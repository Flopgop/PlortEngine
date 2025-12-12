package net.flamgop.borked.renderer.model;

import net.flamgop.borked.renderer.PlortBufferedDescriptorSetPool;
import net.flamgop.borked.renderer.PlortEngine;
import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.util.ResourceHelper;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;

public class PlortModel implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlortModel.class);

    private static PlortTexture nullNormal = null;
    private static PlortTexture nullTexture = null;

    private final List<PlortMesh> meshes = new ArrayList<>();
    private final List<PlortTexture> textures = new ArrayList<>();
    private final Map<PlortMesh, Integer> materialMappings = new HashMap<>();

    private final int materialCount;

    private final PlortBufferedDescriptorSetPool descriptorSets;

    public static void closeNulls() {
        if (nullNormal != null) nullNormal.close();
        if (nullTexture != null) nullTexture.close();
        nullNormal = null;
        nullTexture = null;
    }

    @SuppressWarnings("resource")
    public PlortModel(PlortEngine engine, String path) {
        if (nullTexture == null) {
            nullTexture = ResourceHelper.loadTextureFromResources(engine, "assets/textures/null.png");
        }
        if (nullNormal == null) {
            nullNormal = ResourceHelper.loadTextureFromResources(engine, "assets/textures/null_normal.png");
        }

        AIScene scene = Assimp.aiImportFile(
                path,
                Assimp.aiProcess_Triangulate |
                        Assimp.aiProcess_GenSmoothNormals |
                        Assimp.aiProcess_CalcTangentSpace |
                        Assimp.aiProcess_JoinIdenticalVertices |
                        Assimp.aiProcess_ImproveCacheLocality |
                        Assimp.aiProcess_SortByPType
        );

        if (scene == null || scene.mNumMeshes() == 0) throw new RuntimeException("bad model " + path);
        if (scene.mMeshes() == null) throw new NullPointerException("No meshes in scene");
        if (scene.mTextures() == null) throw new NullPointerException("No textures in scene");

        PlortDescriptorSet descriptorSet = new PlortDescriptorSet(
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.ALL.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.descriptorSets = new PlortBufferedDescriptorSetPool(engine.device(), descriptorSet, scene.mNumMaterials(), engine.swapchain().imageCount());
        materialCount = scene.mNumMaterials();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Map<String, PlortTexture> textureMap = new HashMap<>();
            for (int i = 0; i < scene.mNumTextures(); i++) {
                AITexture aiTexture = AITexture.create(scene.mTextures().get(i));

                if (aiTexture.mHeight() == 0) { // compressed
                    ByteBuffer data = aiTexture.pcDataCompressed();
                    textures.add(ResourceHelper.loadTextureFromMemory(engine, data));
                } else {
                    int width = aiTexture.mWidth();
                    int height = aiTexture.mHeight();

                    ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
                    AITexel.Buffer texels = aiTexture.pcData();

                    texels.forEach(t -> {
                        rgba.put(t.r());
                        rgba.put(t.g());
                        rgba.put(t.b());
                        rgba.put(t.a());
                    });
                    rgba.flip();

                    textures.add(ResourceHelper.loadRawTextureFromMemory(engine, rgba, width, height));
                    MemoryUtil.memFree(rgba);
                }
                textureMap.put("*" + i, textures.get(i));
            }

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(engine.swapchain().imageCount() * scene.mNumMaterials() * 2, stack);
            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(scene.mNumMaterials() * 2, stack);
            for (int i = 0; i < scene.mNumMaterials(); i++) {
                AIMaterial aiMaterial = AIMaterial.create(scene.mMaterials().get(i));
                AIString aiPath = AIString.calloc();
                int r = Assimp.aiGetMaterialTexture(aiMaterial, Assimp.aiTextureType_DIFFUSE, 0, aiPath,
                        (IntBuffer)null, null, null, null, null, null);

                PlortTexture albedo = null;
                if (r == Assimp.aiReturn_SUCCESS) {
                    String p = aiPath.dataString();
                    albedo = textureMap.get(p);
                } else if (r != Assimp.aiReturn_FAILURE) LOGGER.error("aiGetMaterialTexture(diffuse) returned {}", r);

                r = Assimp.aiGetMaterialTexture(aiMaterial, Assimp.aiTextureType_NORMALS, 0, aiPath, (IntBuffer) null, null, null, null, null, null);

                PlortTexture normal = null;
                if (r == Assimp.aiReturn_SUCCESS) {
                    String p = aiPath.dataString();
                    normal = textureMap.get(p);
                } else if (r != Assimp.aiReturn_FAILURE) LOGGER.error("aiGetMaterialTexture(normals) returned {}", r);

                aiPath.close();

                if (albedo == null) {
                    LOGGER.warn("Mesh has no albedo texture");
                    albedo = nullTexture;
                }
                if (normal == null) {
                    LOGGER.warn("Mesh has no normal texture");
                    normal = nullNormal;
                }

                albedo.image().info(imageInfos.get(i * 2));
                albedo.sampler().info(imageInfos.get(i * 2));
                imageInfos.get(i * 2).imageLayout(PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL.qualifier());
                normal.image().info(imageInfos.get(i * 2 + 1));
                normal.sampler().info(imageInfos.get(i * 2 + 1));
                imageInfos.get(i * 2 + 1).imageLayout(PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL.qualifier());

                for (int f = 0; f < engine.swapchain().imageCount(); f++) {
                    writes.get(f * scene.mNumMaterials() * 2 + i * 2)
                            .sType$Default()
                            .descriptorCount(1)
                            .descriptorType(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER.qualifier())
                            .dstSet(descriptorSets.descriptorSet(f, i))
                            .dstBinding(1)
                            .pImageInfo(imageInfos.slice(2 * i, 1));

                    writes.get(f * scene.mNumMaterials() * 2 + i * 2 + 1)
                            .sType$Default()
                            .descriptorCount(1)
                            .descriptorType(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER.qualifier())
                            .dstSet(descriptorSets.descriptorSet(f, i))
                            .dstBinding(2)
                            .pImageInfo(imageInfos.slice(2 * i + 1, 1));
                }
            }

            engine.device().updateDescriptorSets(writes, null);
        }

        for (int i = 0; i < scene.mNumMeshes(); i++) {
            AIMesh mesh = AIMesh.create(scene.mMeshes().get(i));
            meshes.add(new PlortMesh(engine.allocator(), mesh));

            materialMappings.put(meshes.get(i), mesh.mMaterialIndex());
        }

        Assimp.aiFreeScene(scene);
    }

    @SuppressWarnings("resource")
    public void setViewBuffer(PlortEngine engine, PlortBuffer viewBuffer, int currentFrameModInFlight) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(materialCount, stack);
            VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(materialCount, stack);
            for (int i = 0; i < materialCount; i++) {
                viewBuffer.info(bufferInfos.get(i));
                writes.get(i)
                        .sType$Default()
                        .descriptorCount(1)
                        .descriptorType(PlortDescriptor.Type.UNIFORM_BUFFER.qualifier())
                        .dstBinding(0)
                        .dstSet(descriptorSets.descriptorSet(currentFrameModInFlight, i))
                        .pBufferInfo(bufferInfos.slice(i, 1));
            }
            engine.device().updateDescriptorSets(writes, null);
        }
    }

    public void submit(VkCommandBuffer cmdBuffer, PlortPipeline pipeline, PlortBuffer instanceBuffer, int instanceCount, int currentFrameModInFlight) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer push = stack.calloc(4 * Long.BYTES);
            for (PlortMesh mesh : meshes) {
                if (materialMappings.containsKey(mesh)) {
                    long descriptor = descriptorSets.descriptorSet(currentFrameModInFlight, materialMappings.get(mesh));

                    vkCmdBindDescriptorSets(cmdBuffer, PipelineBindPoint.GRAPHICS.qualifier(), pipeline.layout(), 0, stack.longs(descriptor), null);
                }

                push.putLong(mesh.vertexBuffer().deviceAddress());
                push.putLong(mesh.meshBuffer().deviceAddress());
                push.putLong(mesh.boundsBuffer().deviceAddress());
                push.putLong(instanceBuffer.deviceAddress());
                push.flip();

                vkCmdPushConstants(cmdBuffer, pipeline.layout(), PlortShaderStage.Stage.ALL.bit(), 0, push);
                mesh.recordDrawCommandInstanced(cmdBuffer, instanceCount);
            }
        }
    }

    @Override
    public void close() {
        descriptorSets.close();
        meshes.forEach(PlortMesh::close);
        textures.forEach(PlortTexture::close);
    }
}
