import http from "http";
import fs from "fs";
import path from "path";
import url from "url";

const PORT = 8080;
const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const BUNDLES_DIR = path.join(__dirname, "bundles");

http.createServer((req, res) => {
    const match = req.url.match(/^\/bundles\/([a-zA-Z0-9._-]+)$/);

    if (!match) {
        res.writeHead(404);
        return res.end("Not found");
    }

    const locale = match[1];
    const requestedPath = path.join(BUNDLES_DIR, locale);

    let realPath;
    try {
        // 🔑 РАЗРЕШАЕМ СИМЛИНКИ
        realPath = fs.realpathSync(requestedPath);
    } catch {
        res.writeHead(404);
        return res.end("Locale not found");
    }

    // 🔒 защита: файл должен оставаться внутри bundles
    if (!realPath.startsWith(BUNDLES_DIR)) {
        res.writeHead(403);
        return res.end("Forbidden");
    }

    let content;
    try {
        content = fs.readFileSync(realPath, "utf8");
    } catch {
        res.writeHead(500);
        return res.end("Read error");
    }

    res.writeHead(200, {
        "Content-Type": "text/plain; charset=utf-8",
        "Content-Length": Buffer.byteLength(content)
    });

    res.end(content);
}).listen(PORT, () => {
    console.log(`Locale server with symlinks: http://127.0.0.1:${PORT}`);
});
