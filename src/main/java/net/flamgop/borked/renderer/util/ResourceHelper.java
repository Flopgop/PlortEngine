package net.flamgop.borked.renderer.util;

import net.flamgop.borked.Main;
import net.flamgop.borked.math.Vector3i;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.image.ImageFormat;
import net.flamgop.borked.renderer.image.PlortFilter;
import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.image.PlortSampler;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class ResourceHelper {
    public static ByteBuffer loadFromResource(String resourcePath) {
        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) throw new RuntimeException("Couldn't open resource stream for " + resourcePath);
            byte[] bytes = stream.readAllBytes();
            ByteBuffer mem = MemoryUtil.memAlloc(bytes.length);
            mem.put(bytes);
            mem.flip();
            return mem;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static PlortTexture loadTextureFromResources(PlortRenderContext engine, String path) {
        ByteBuffer bytes = ResourceHelper.loadFromResource(path);
        PlortTexture texture = loadTextureFromMemory(engine, bytes);
        MemoryUtil.memFree(bytes);
        return texture;
    }

    public static PlortTexture loadTextureFromMemory(PlortRenderContext engine, ByteBuffer bytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.callocInt(1), y = stack.callocInt(1), channels = stack.callocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer imageData = STBImage.stbi_load_from_memory(bytes, x, y, channels, 4);
            if (imageData == null) throw new RuntimeException("font");
            STBImage.stbi_set_flip_vertically_on_load(false);

            PlortBuffer stagingBuffer = new PlortBuffer(imageData.capacity(), BufferUsage.TRANSFER_SRC_BIT, engine.allocator());
            try (MappedMemory mem = stagingBuffer.map()) {
                mem.copyEntireBuffer(imageData);
            }

            STBImage.stbi_image_free(imageData);

            PlortImage image = new PlortImage(
                    engine.device(), engine.allocator(),
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

            engine.commandPool().transientSubmit(engine.device().graphicsQueue(), 0, (cmd) -> {
                VkUtil.copyBufferToImage(cmd, stagingBuffer, image, x.get(0), y.get(0));
            });

            stagingBuffer.close();

            PlortSampler sampler = new PlortSampler(engine.device(), PlortFilter.LINEAR, PlortFilter.LINEAR, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT);

            return new PlortTexture(image, sampler);
        }
    }

    public static PlortTexture loadRawTextureFromMemory(PlortRenderContext engine, ByteBuffer rgba, int width, int height) {
        PlortBuffer stagingBuffer = new PlortBuffer(rgba.capacity(), BufferUsage.TRANSFER_SRC_BIT, engine.allocator());
        try (MappedMemory mem = stagingBuffer.map()) {
            mem.copyEntireBuffer(rgba);
        }

        PlortImage image = new PlortImage(
                engine.device(), engine.allocator(),
                PlortImage.Type.TYPE_2D,
                new Vector3i(width, height, 1),
                1, 1,
                ImageFormat.R8G8B8A8_UNORM,
                PlortImage.Layout.PREINITIALIZED,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                1, SharingMode.EXCLUSIVE,
                MemoryUsage.GPU_ONLY,
                PlortImage.ViewType.TYPE_2D,
                VK_IMAGE_ASPECT_COLOR_BIT
        );

        engine.commandPool().transientSubmit(engine.device().graphicsQueue(), 0, (cmd) -> {
            VkUtil.copyBufferToImage(cmd, stagingBuffer, image, width, height);
        });

        stagingBuffer.close();

        PlortSampler sampler = new PlortSampler(engine.device(), PlortFilter.LINEAR, PlortFilter.LINEAR, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT);

        return new PlortTexture(image, sampler);
    }
}
