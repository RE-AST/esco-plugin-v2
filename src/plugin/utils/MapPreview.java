package plugin.utils;

import arc.files.Fi;
import arc.util.Log;
import mindustry.Vars;
import mindustry.io.MapIO;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.environment.OreBlock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MapPreview {
    public static void loadColors() {
        try {
            Fi colors = Utils.getResource("block_colors.png");
            BufferedImage image = ImageIO.read(colors.read());
            Vars.content
                    .blocks()
                    .each(block -> block.mapColor.argb8888(block instanceof OreBlock ? block.itemDrop.color.argb8888() : image.getRGB(block.id, 0)).a(1.0F));
        } catch (Exception e) {
            Log.err(e);
        }
    }

    public static BufferedImage generatePreview(Tiles tiles) {
        BufferedImage image = new BufferedImage(tiles.width, tiles.height, 2);
        // Pixmap pixmap = new Pixmap(tiles.width, tiles.height);
        for (int x = 0; x < tiles.width; x++) {
            for (int y = 0; y < tiles.height; y++) {
                Tile tile = tiles.getn(x, y);
                if (tile.build != null) {
                    if (tile.build.config() instanceof Item) {
                        Item item = (Item) tile.build.config();
                        //pixmap.set(x, pixmap.height - 1 - y, item.color.rgba());
                        image.setRGB(x, tiles.height - 1 -y, convert(item.color.rgba()));
                        item = null;
                    } else {
                        // pixmap.set(x, pixmap.height - 1 - y, colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                        image.setRGB(x, tiles.height - 1 - y, convert(MapIO.colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team())));
                    }
                } else {
                    //pixmap.set(x, pixmap.height - 1 - y, colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                    image.setRGB(x, tiles.height - 1 - y, convert(MapIO.colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team())));
                }
            }
        }
        return image;
    }

    public static int convert(int color) {
        return new arc.graphics.Color(color).argb8888();
    }

    public static byte[] parseImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", stream);
        return stream.toByteArray();
    }

    public static byte[] parseTiles(Tiles tiles) {
        try {
            return parseImage(generatePreview(tiles));
        } catch (IOException var2) {
            return new byte[0];
        }
    }
}
