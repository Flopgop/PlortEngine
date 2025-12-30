package net.flamgop.borked.renderer.text;

import net.flamgop.borked.math.Vector2d;
import net.flamgop.borked.math.Vector3i;
import net.flamgop.borked.math.Vector4d;
import net.flamgop.borked.renderer.PlortCommandPool;
import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.image.ImageFormat;
import net.flamgop.borked.renderer.image.PlortFilter;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.image.PlortSampler;
import net.flamgop.borked.renderer.memory.*;
import net.flamgop.borked.renderer.text.json.JsonAtlasFile;
import net.flamgop.borked.renderer.text.json.JsonGlyph;
import net.flamgop.borked.renderer.util.ResourceHelper;
import net.flamgop.borked.renderer.util.VkUtil;
import org.graalvm.collections.Pair;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

public class Atlas extends TrackedCloseable {

    private static final int BAKED_GLYPH_SIZE = 8 * Float.BYTES;
    protected static final int GLYPH_MESHLET_SIZE = 4 * Float.BYTES + 3 * Float.BYTES + Integer.BYTES + 4 * Float.BYTES;

    private final PlortAllocator allocator;

    private final List<Pair<Character, Glyph>> glyphs = new ArrayList<>();
    private final float spaceWidth, lineHeight;

    private final PlortImage atlas;
    private final PlortSampler sampler;
    private final PlortBuffer bakedGlyphData;

    public Atlas(PlortDevice device, PlortAllocator allocator, PlortCommandPool commandPool, String atlasPath) {
        super();
        this.allocator = allocator;

        int numGlyphs = 0xFF - 0x20;
        this.bakedGlyphData = new PlortBuffer(BAKED_GLYPH_SIZE * numGlyphs, BufferUsage.STORAGE_BUFFER_BIT , allocator);

        JsonAtlasFile atlasFile = JsonAtlasFile.loadFromResources(atlasPath + ".json");
        this.spaceWidth = (float) (atlasFile.glyphs.stream().filter(g -> g.unicode == 0x20).findFirst().orElseThrow().advance * atlasFile.atlas.size);
        this.lineHeight = (float) (atlasFile.metrics.lineHeight * atlasFile.atlas.size);

        try (MappedMemory memory = bakedGlyphData.map()) {
            for (int c = 0x20; c < 0xFF; c++) {
                final int ch = c;
                Optional<JsonGlyph> maybeGlyph = atlasFile.glyphs.stream().filter(g -> g.unicode == ch).findFirst();
                if (maybeGlyph.isEmpty()) {
                    glyphs.add(Pair.create((char) c, new Glyph(new Vector4d(0), new Vector2d(0), new Vector2d(0), 0.261, true))); // let's just say this char is a space :3
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    continue;
                }
                JsonGlyph glyph = maybeGlyph.get();

                if (glyph.atlasBounds == null) {
                    glyphs.add(Pair.create((char) c, new Glyph(new Vector4d(0), new Vector2d(0), new Vector2d(0), glyph.advance, true)));
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    memory.putFloat(0);
                    continue;
                }

                double atlasW = atlasFile.atlas.width;
                double atlasH = atlasFile.atlas.height;
                boolean yOriginBottom = "bottom".equalsIgnoreCase(atlasFile.atlas.yOrigin);

                double leftPx   = glyph.atlasBounds.left;
                double bottomPx = glyph.atlasBounds.bottom;
                double rightPx  = glyph.atlasBounds.right;
                double topPx    = glyph.atlasBounds.top;

                double uSize = (rightPx - leftPx) / atlasW;
                double vSize = (topPx - bottomPx) / atlasH;

                double u0 = leftPx / atlasW;
                double v0;
                if (yOriginBottom) {
                    v0 = bottomPx / atlasH;
                } else {
                    v0 = 1.0 - (topPx / atlasH);
                }

                glyphs.add(Pair.create((char) c, new Glyph(
                        new Vector4d(u0, v0, uSize, vSize),
                        new Vector2d((glyph.planeBounds.right - glyph.planeBounds.left) * atlasFile.atlas.size, (glyph.planeBounds.top - glyph.planeBounds.bottom) * atlasFile.atlas.size),
                        new Vector2d(glyph.planeBounds.left * atlasFile.atlas.size, glyph.planeBounds.top * atlasFile.atlas.size),
                        glyph.advance * atlasFile.atlas.size,
                        false
                )));

                memory.putFloat((float) u0);
                memory.putFloat((float) v0);
                memory.putFloat((float) uSize);
                memory.putFloat((float) vSize);
                memory.putFloat((float) atlasFile.atlas.distanceRange);
                memory.putFloat(0);
                memory.putFloat(0);
                memory.putFloat(0);
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer msdf = ResourceHelper.loadFromResource(atlasPath + ".png");
            IntBuffer x = stack.callocInt(1), y = stack.callocInt(1), channels = stack.callocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer imageData = STBImage.stbi_load_from_memory(msdf, x, y, channels, 4);
            if (imageData == null) throw new RuntimeException("font");
            STBImage.stbi_set_flip_vertically_on_load(false);
            MemoryUtil.memFree(msdf);

            PlortBuffer stagingBuffer = new PlortBuffer(imageData.capacity(), BufferUsage.TRANSFER_SRC_BIT , allocator);
            try (MappedMemory mem = stagingBuffer.map()) {
                mem.copyEntireBuffer(imageData);
            }

            STBImage.stbi_image_free(imageData);

            this.atlas = new PlortImage(
                    device, allocator,
                    PlortImage.Type.TYPE_2D,
                    new Vector3i(x.get(0), y.get(0), 1),
                    1, 1,
                    ImageFormat.R8G8B8A8_UNORM,
                    PlortImage.Layout.PREINITIALIZED,
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                    1, SharingMode.EXCLUSIVE,
                    MemoryUsage.GPU_ONLY,
                    PlortImage.ViewType.TYPE_2D,
                    VK_IMAGE_ASPECT_COLOR_BIT
            );

            commandPool.transientSubmit(device.graphicsQueue(), 0, (cmd) -> {
                VkUtil.copyBufferToImage(cmd, stagingBuffer, atlas, x.get(0), y.get(0));
            });

            stagingBuffer.close();

            this.sampler = new PlortSampler(device, PlortFilter.LINEAR, PlortFilter.LINEAR, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT);
        }
    }

    public float lineHeight() {
        return lineHeight;
    }

    public float stringWidth(String text, float scale) {
        int numChars = text.length();
        float penX = 0;
        float maxWidth = 0;

        for (int i = 0; i < numChars; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                // penX resets to zero, but we don't want to return zero if a string ends with \n, we want to return the length of the longest line in the string.
                if (penX > maxWidth) maxWidth = penX;
                penX = 0f;
                continue;
            }
            Pair<Character, Glyph> glyphPair = glyphs.stream()
                    .filter(p -> p.getLeft() == c)
                    .findFirst()
                    .orElse(glyphs.getFirst());
            Glyph glyph = glyphPair.getRight();

            if (glyph.isEmpty())
                penX += spaceWidth * scale;
            else
                penX += (float) glyph.advance() * scale;
        }

        if (penX > maxWidth) {
            maxWidth = penX;
        }

        return maxWidth;
    }

    public float stringHeight(String text, float scale) {
        return text.chars().filter(ch -> ch == '\n' || ch == '\r').count() * lineHeight * scale;
    }

    public PlortBuffer buildTextBuffer(List<Text> text) {
        int totalSize = text.stream().mapToInt(t -> t.text().replaceAll("[\\r\\n]", "").length()).sum() * GLYPH_MESHLET_SIZE;
        if (totalSize == 0) return null; // nothing to build

        PlortBuffer buffer = new PlortBuffer(totalSize, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);

        try (MappedMemory mem = buffer.map()) {
            for (Text t : text) {
                List<String> lines = Arrays.asList(t.text().split("\\r?\\n"));
                List<Float> lineWidths = new ArrayList<>();
                lines.forEach(s -> lineWidths.add(stringWidth(s, t.scale())));

                float penY = t.offset().y();

                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    String line = lines.get(lineIndex);
                    float lineWidth = lineWidths.get(lineIndex);

                    float penX = switch (t.align()) {
                        case CENTER -> t.offset().x() - lineWidth / 2f;
                        case RIGHT -> t.offset().x() - lineWidth;
                        default -> t.offset().x();
                    };

                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);
                        Pair<Character, Glyph> glyphPair = glyphs.stream()
                                .filter(p -> p.getLeft() == c)
                                .findFirst()
                                .orElse(glyphs.getFirst());
                        int index = glyphs.indexOf(glyphPair);
                        Glyph glyph = glyphPair.getRight();

                        mem.putFloat(penX + (float) glyph.bearing().x() * t.scale());
                        mem.putFloat(penY - (float) glyph.bearing().y() * t.scale());
                        mem.putFloat((float) glyph.size().x() * t.scale());
                        mem.putFloat((float) glyph.size().y() * t.scale());
                        mem.putFloat(t.color().x());
                        mem.putFloat(t.color().y());
                        mem.putFloat(t.color().z());
                        mem.putInt(index);
                        mem.putFloat(t.depth());
                        mem.putFloat(0);
                        mem.putFloat(0);
                        mem.putFloat(0);

                        if (glyph.isEmpty())
                            penX += spaceWidth * t.scale();
                        else
                            penX += (float) glyph.advance() * t.scale();
                    }

                    penY += lineHeight * t.scale();
                }
            }
        }

        buffer.label("Autogenerated Text");
        return buffer;
    }

    public PlortTexture texture() {
        return new PlortTexture(this.atlas, this.sampler);
    }

    public PlortBuffer bakedGlyphData() {
        return bakedGlyphData;
    }

    @Override
    public void close() {
        bakedGlyphData.close();
        sampler.close();
        atlas.close();
        super.close();
    }
}
