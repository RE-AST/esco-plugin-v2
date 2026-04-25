// tools/generateModels.js

const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const MIGRATIONS = path.join(ROOT, "migrations.sql");
const OUT_DIR = path.join(__dirname, "generatedClasses");

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

const sql = fs.readFileSync(MIGRATIONS, "utf8");

const tables = [...sql.matchAll(/CREATE TABLE\s+(\w+)\s*\(([\s\S]*?)\);/gi)];

function toPascal(str) {
    return str
        .split("_")
        .map(s => s.charAt(0).toUpperCase() + s.slice(1))
        .join("");
}

function toCamel(str) {
    const p = toPascal(str);
    return p.charAt(0).toLowerCase() + p.slice(1);
}

function kotlinType(column, raw) {
    raw = raw.toUpperCase();

    if (raw.includes("SERIAL")) return "Int";
    if (raw.includes("INTEGER")) return "Int";
    if (raw.includes("BIGINT")) return "Long?";
    if (raw.includes("BOOLEAN")) return "Boolean";
    if (raw.includes("TIMESTAMP")) return "Instant?";
    if (raw.includes("JSONB")) return "String";
    if (raw.includes("TEXT[]")) return "List<String>";
    if (raw.includes("VARCHAR")) return "String";
    if (raw.includes("TEXT")) return "String";

    return "String";
}

function rsGetter(column, raw) {
    raw = raw.toUpperCase();

    if (raw.includes("SERIAL")) return `rs.getInt("${column}")`;
    if (raw.includes("INTEGER")) return `rs.getInt("${column}")`;
    if (raw.includes("BIGINT")) return `rs.getObject("${column}") as Long?`;
    if (raw.includes("BOOLEAN")) return `rs.getBoolean("${column}")`;
    if (raw.includes("TIMESTAMP")) return `rs.getTimestamp("${column}")?.toInstant()`;
    if (raw.includes("JSONB")) return `rs.getString("${column}")`;
    if (raw.includes("TEXT[]")) return `rs.getArray("${column}")?.array as List<String>`;
    if (raw.includes("VARCHAR")) return `rs.getString("${column}")`;
    if (raw.includes("TEXT")) return `rs.getString("${column}")`;

    return `rs.getString("${column}")`;
}

for (const match of tables) {
    const tableName = match[1];
    const body = match[2];

    const className = toPascal(tableName.replace(/s$/, ""));
    const mapperName = `get${className}`;

    const lines = body
        .split("\n")
        .map(x => x.trim())
        .filter(Boolean)
        .filter(x => !x.startsWith("UNIQUE"))
        .filter(x => !x.startsWith("PRIMARY"))
        .filter(x => !x.startsWith("FOREIGN"));

    const fields = [];

    for (let line of lines) {
        line = line.replace(/--.*$/g, "").trim();
        line = line.replace(/,$/, "");

        const parts = line.split(/\s+/);
        const column = parts[0];

        if (!column) continue;

        const rawType = parts.slice(1).join(" ");

        fields.push({
            column,
            name: toCamel(column),
            type: kotlinType(column, rawType),
            getter: rsGetter(column, rawType)
        });
    }

    const classCode = `
import java.time.Instant
import java.sql.ResultSet
import java.sql.SQLException

data class ${className}(
${fields.map(f => `    val ${f.name}: ${f.type}`).join(",\n")}
)
`.trim();

    const mapperCode = `
@Throws(SQLException::class)
fun ${mapperName}(rs: ResultSet): ${className} {
    return ${className}(
${fields.map(f => `        ${f.getter}`).join(",\n")}
    )
}
`.trim();

    const generatedAt = new Date().toISOString();

    const full =
        `// Auto-generated on ${generatedAt}\n` +
        classCode +
        "\n\n" +
        mapperCode +
        "\n";

    fs.writeFileSync(
        path.join(OUT_DIR, `${className}.kt`),
        full
    );

    console.log(`Generated ${className}.kt`);
}

console.log("Done.");