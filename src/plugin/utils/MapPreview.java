package plugin.utils;

import arc.files.Fi;
import arc.struct.StringMap;
import arc.util.Log;
import arc.util.io.CounterInputStream;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.maps.Map;
import mindustry.type.Item;
import mindustry.world.*;
import mindustry.world.blocks.environment.OreBlock;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import static mindustry.io.MapIO.colorFor;

public class MapPreview {
    public static void loadColors() {
        try {
            Fi colors = UtilsKt.getResource("block_colors.png");
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
                    if (tile.build.config() instanceof Item item) {
                        //pixmap.set(x, pixmap.height - 1 - y, item.color.rgba());
                        image.setRGB(x, tiles.height - 1 - y, convert(item.color.rgba()));
                        item = null;
                    } else {
                        // pixmap.set(x, pixmap.height - 1 - y, colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                        image.setRGB(x, tiles.height - 1 - y, convert(colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team())));
                    }
                } else {
                    //pixmap.set(x, pixmap.height - 1 - y, colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()));
                    image.setRGB(x, tiles.height - 1 - y, convert(colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team())));
                }
            }
        }
        return image;
    }

    public static byte[] parseMap(Map map) {
        try {
            return parseImage(generatePreview(map));
        } catch (IOException var2) {
            Log.debug(var2);
            return new byte[0];
        }
    }

    public static BufferedImage generatePreview(Map map) throws IOException {
        return generatePreview(map.file.read(8192));
    }

    public static BufferedImage generatePreview(InputStream input) throws IOException {
        BufferedImage var12;
        try {
            CounterInputStream counter = new CounterInputStream(new InflaterInputStream(input));

            try (DataInputStream stream = new DataInputStream(counter)) {
                SaveIO.readHeader(stream);
                SaveVersion version = SaveIO.getSaveWriter(stream.readInt());
                StringMap meta = new StringMap();
                version.readRegion("meta", stream, counter, data -> meta.putAll(version.readStringMap(data)));
                int width = meta.getInt("width");
                int height = meta.getInt("height");
                final BufferedImage floors = new BufferedImage(width, height, 2);
                final BufferedImage walls = new BufferedImage(width, height, 2);
                final Graphics2D fgraphics = floors.createGraphics();
                final java.awt.Color shade = new java.awt.Color(0, 0, 0, 64);
                CachedTile tile = new CachedTile() {
                    public void setBlock(Block type) {
                        super.setBlock(type);
                        int color = MapIO.colorFor(this.block(), Blocks.air, Blocks.air, this.team());
                        if (color != 255 && color != 0) {
                            walls.setRGB(this.x, floors.getHeight() - 1 - this.y, convert(color));
                            fgraphics.setColor(shade);
                            fgraphics.drawRect(this.x, floors.getHeight() - 1 - this.y + 1, 1, 1);
                        }
                    }
                };
                version.readRegion("content", stream, counter, version::readContentHeader);
                if (version.version >= 11) version.readRegion("content", stream, counter, version::skipContentPatches);
                version.readRegion("preview_map", stream, counter, in -> version.readMap(in, new WorldContext() {
                                    public void resize(int widthx, int heightx) {
                                    }

                                    public boolean isGenerating() {
                                        return false;
                                    }

                                    public void begin() {
                                        Vars.world.setGenerating(true);
                                    }

                                    public void end() {
                                        Vars.world.setGenerating(false);
                                    }

                                    public void onReadBuilding() {
                                        if (tile.build != null) {
                                            int size = tile.block().size;
                                            int offsetX = -(size - 1) / 2;
                                            int offsetY = -(size - 1) / 2;

                                            for (int dx = 0; dx < size; dx++) {
                                                for (int dy = 0; dy < size; dy++) {
                                                    int drawx = tile.x + dx + offsetX;
                                                    int drawy = tile.y + dy + offsetY;
                                                    walls.setRGB(drawx, floors.getHeight() - 1 - drawy, tile.team().color.argb8888());
                                                }
                                            }
                                        }
                                    }

                                    public Tile tile(int index) {
                                        tile.x = (short) (index % width);
                                        tile.y = (short) (index / width);
                                        return tile;
                                    }

                                    public Tile create(int x, int y, int floorID, int overlayID, int wallID) {
                                        if (overlayID != 0) {
                                            floors.setRGB(
                                                    x,
                                                    floors.getHeight() - 1 - y,
                                                    convert(MapIO.colorFor(Blocks.air, Blocks.air, Vars.content.block(overlayID), Team.derelict))
                                            );
                                        } else {
                                            floors.setRGB(
                                                    x,
                                                    floors.getHeight() - 1 - y,
                                                    convert(MapIO.colorFor(Blocks.air, Vars.content.block(floorID), Blocks.air, Team.derelict))
                                            );
                                        }

                                        return tile;
                                    }
                                }
                        )
                );
                fgraphics.drawImage(walls, 0, 0, null);
                fgraphics.dispose();
                var12 = floors;
            } catch (Throwable var22) {
                try {
                    counter.close();
                } catch (Throwable var19) {
                    var22.addSuppressed(var19);
                }

                throw var22;
            }

            counter.close();
        } finally {
            Vars.content.setTemporaryMapper(null);
        }

        return var12;
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
