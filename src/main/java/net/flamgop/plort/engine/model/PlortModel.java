package net.flamgop.plort.engine.model;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.flamgop.plort.engine.math.val.AABB;
import net.flamgop.plort.engine.math.val.Frustum;
import net.flamgop.plort.engine.math.val.Matrix4f;
import net.flamgop.plort.engine.math.val.Vector3f;
import net.flamgop.plort.engine.renderer.PlortCommandBuffer;
import net.flamgop.plort.engine.renderer.PlortDevice;
import net.flamgop.plort.engine.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.plort.engine.renderer.PlortRenderContext;
import net.flamgop.plort.engine.renderer.descriptor.PlortDescriptor;
import net.flamgop.plort.engine.renderer.descriptor.PlortDescriptorSetLayout;
import net.flamgop.plort.engine.renderer.image.PlortImage;
import net.flamgop.plort.engine.renderer.material.PlortTexture;
import net.flamgop.plort.engine.renderer.memory.PlortAllocator;
import net.flamgop.plort.engine.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.plort.engine.renderer.pipeline.PipelineBindPoint;
import net.flamgop.plort.engine.renderer.pipeline.PlortPipelineLayout;
import net.flamgop.plort.engine.renderer.pipeline.PlortShaderStage;
import net.flamgop.plort.engine.renderer.util.ResourceHelper;
import net.flamgop.plort.engine.resource.ResourceIdentifier;
import net.flamgop.plort.engine.resource.ResourceManager;
import org.jetbrains.annotations.UnmodifiableView;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.function.IntToLongFunction;

public class PlortModel implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlortModel.class);

    private static PlortTexture nullNormal = null;
    private static PlortTexture nullTexture = null;

    private final PlortDevice device;

    private final List<PlortMesh> meshes = new ArrayList<>();
    private final List<PlortTexture> textures = new ArrayList<>();
    private final Object2IntMap<PlortMesh> materialMappings = new Object2IntOpenHashMap<>();

    private final List<AABB> childAABBs;
    private final AABB aabb;

    private final int materialCount;

    private final PlortDescriptorSetLayout layout;
    private final List<PlortMaterial> materials = new ArrayList<>();
    private final LongBuffer pOut = MemoryUtil.memCallocLong(1);

    private boolean closed = false;

    public static void closeNulls() {
        if (nullNormal != null) nullNormal.close();
        if (nullTexture != null) nullTexture.close();
        nullNormal = null;
        nullTexture = null;
    }

    public PlortModel(PlortRenderContext engine, ResourceManager manager, ResourceIdentifier model) {
        if (nullTexture == null) {
            nullTexture = ResourceHelper.loadTextureFromResources(engine, "assets/textures/null.png");
        }
        if (nullNormal == null) {
            nullNormal = ResourceHelper.loadTextureFromResources(engine, "assets/textures/null_normal.png");
        }

        this.device = engine.device();

        ByteBuffer bytes;
        try (InputStream stream = manager.open(model)) {
            bytes = MemoryUtil.memAlloc(stream.available());
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                bytes.put(chunk, 0, read);
            }
            bytes.flip();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        AIScene scene = Assimp.aiImportFileFromMemory(
                bytes,
                Assimp.aiProcess_Triangulate |
                        Assimp.aiProcess_GenSmoothNormals |
                        Assimp.aiProcess_CalcTangentSpace |
                        Assimp.aiProcess_JoinIdenticalVertices |
                        Assimp.aiProcess_ImproveCacheLocality |
                        Assimp.aiProcess_SortByPType |
                        Assimp.aiProcess_GenBoundingBoxes,
                (CharSequence) null
        );


        if (scene == null || scene.mNumMeshes() == 0) throw new RuntimeException("bad model " + model);
        AINode rootNode = scene.mRootNode();
        if (rootNode == null) throw new NullPointerException("No nodes in scene");
        if (scene.mMeshes() == null) throw new NullPointerException("No meshes in scene");
        if (scene.mTextures() == null) throw new NullPointerException("No textures in scene");

        this.layout = new PlortDescriptorSetLayout(
                engine.device(),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.ALL.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        materialCount = scene.mNumMaterials();

        PointerBuffer pTextures = scene.mTextures();
        Map<String, PlortTexture> textureMap = new HashMap<>();
        if (pTextures == null) LOGGER.warn("Scene has a null texture buffer, this may be problematic.");
        else {
            for (int i = 0; i < scene.mNumTextures(); i++) {
                AITexture aiTexture = AITexture.create(pTextures.get(i));

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
        }

        buildMaterials(engine, scene, textureMap);

        aabb = traverseNode(engine.allocator(), scene, rootNode);
        this.childAABBs = meshes.stream().map(PlortMesh::aabb).toList();

        Assimp.aiFreeScene(scene);
        MemoryUtil.memFree(bytes);
    }

    public PlortDescriptorSetLayout layout() {
        return this.layout;
    }

    public int materialCount() {
        return materialCount;
    }

    private void buildMaterials(PlortRenderContext engine, AIScene scene, Map<String, PlortTexture> textureMap) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIColor4D outColor = AIColor4D.calloc(stack);
            PointerBuffer pMaterials = scene.mMaterials();
            if (pMaterials == null) throw new NullPointerException("Scene has a null material buffer.");
            for (int i = 0; i < scene.mNumMaterials(); i++) {
                AIMaterial aiMaterial = AIMaterial.create(pMaterials.get(i));
                AIString aiPath = AIString.calloc();
                int r = Assimp.aiGetMaterialTexture(aiMaterial, Assimp.aiTextureType_DIFFUSE, 0, aiPath,
                        (IntBuffer) null, null, null, null, null, null);

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
                    r = Assimp.aiGetMaterialColor(aiMaterial, Assimp.AI_MATKEY_COLOR_DIFFUSE, Assimp.aiTextureType_NONE, 0, outColor);
                    if (r == Assimp.aiReturn_SUCCESS) {
                        LOGGER.debug("Mesh uses a flat color for albedo instead of a texture (rgba = {} {} {} {})", outColor.r(), outColor.g(), outColor.b(), outColor.a());
                        albedo = ResourceHelper.loadRawTextureFromMemory(engine, stack.bytes((byte) (outColor.r() * 0xFF), (byte) (outColor.g() * 0xFF), (byte) (outColor.b() * 0xFF), (byte) (outColor.a() * 0xFF)), 1, 1);
                        textures.add(albedo);
                    } else {
                        LOGGER.warn("Mesh has no albedo texture");
                        albedo = nullTexture;
                    }
                }
                if (normal == null) {
                    LOGGER.warn("Mesh has no normal texture");
                    normal = nullNormal;
                }

                materials.add(new PlortMaterial("mat_" + i, albedo, normal));
            }
        }
    }

    @SuppressWarnings("resource")
    public void writeDescriptors(PlortRenderContext engine, PlortBufferedDescriptorSetPool descriptorSets) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(engine.swapchain().imageCount() * materials.size() * PlortMaterial.TEXTURE_COUNT, stack);
            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(materials.size() * PlortMaterial.TEXTURE_COUNT, stack);
            for (int i = 0; i < materials.size(); i++) {
                PlortMaterial material = materials.get(i);
                material.albedo().image().info(imageInfos.get(i * 2));
                material.albedo().sampler().info(imageInfos.get(i * 2));
                imageInfos.get(i * 2).imageLayout(PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL.qualifier());
                material.normal().image().info(imageInfos.get(i * 2 + 1));
                material.normal().sampler().info(imageInfos.get(i * 2 + 1));
                imageInfos.get(i * 2 + 1).imageLayout(PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL.qualifier());

                for (int f = 0; f < engine.swapchain().imageCount(); f++) {
                    writes.get(f * materials.size() * 2 + i * 2)
                            .sType$Default()
                            .descriptorCount(1)
                            .descriptorType(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER.qualifier())
                            .dstSet(descriptorSets.descriptorSet(f, i))
                            .dstBinding(1)
                            .pImageInfo(imageInfos.slice(2 * i, 1));

                    writes.get(f * materials.size() * 2 + i * 2 + 1)
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
    }

    private AABB traverseNode(PlortAllocator allocator, AIScene scene, AINode node) {
        AABB aabb = null;
        boolean noCollision = false;
        AIMetaData meta = node.mMetadata();
        if (meta != null) {
            int count = meta.mNumProperties();
            AIString.Buffer keys = meta.mKeys();
            AIMetaDataEntry.Buffer values = meta.mValues();

            for (int i = 0; i < count; i++) {
                String key = keys.get(i).dataString();
                if (key.equals("no_collision")) {
                    AIMetaDataEntry entry = values.get(i);
                    LOGGER.debug("Found a no_collision tag!");

                    switch (entry.mType()) {
                        case 0 -> // BOOL
                                noCollision = entry.mData(1).get() != 0;
                        case 1 -> // INT32
                                noCollision = entry.mData(4).getInt(0) != 0;
                    }
                    LOGGER.debug("Set noCollision to {}", noCollision);
                    break;
                }
            }
        }

        IntBuffer meshIndices = node.mMeshes();
        if (meshIndices != null) {
            PointerBuffer pMeshes = scene.mMeshes();
            if (pMeshes == null) throw new NullPointerException("Scene has null mesh buffer!");
            for (int i = 0; i < meshIndices.capacity(); i++) {
                int meshIndex = meshIndices.get(i);
                AIMesh mesh = AIMesh.create(pMeshes.get(meshIndex));
                PlortMesh pm = new PlortMesh(allocator, mesh, Matrix4f.fromAssimp(node.mTransformation()));

                meshes.add(pm);
                materialMappings.put(pm, mesh.mMaterialIndex());

                if (aabb == null) aabb = pm.aabb();
                else aabb = aabb.union(pm.aabb());
            }
        }

        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                AINode child = AINode.create(children.get(i));
                AABB childAABB = traverseNode(allocator, scene, child);
                if (childAABB != null) {
                    if (aabb == null) aabb = childAABB;
                    else aabb = aabb.union(childAABB);
                }
            }
        }
        return aabb;
    }

    public @UnmodifiableView List<PlortMesh> childMeshes() {
        return Collections.unmodifiableList(this.meshes);
    }

    public AABB aabb() {
        return new AABB(aabb);
    }

    @SuppressWarnings("resource")
    public void writeViewBuffer(PlortBuffer viewBuffer, IntToLongFunction materialDescriptorSetProvider) {
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
                        .dstSet(materialDescriptorSetProvider.applyAsLong(i))
                        .pBufferInfo(bufferInfos.slice(i, 1));
            }
            device.updateDescriptorSets(writes, null);
        }
    }

    public void submit(PlortCommandBuffer cmdBuffer, PlortPipelineLayout layout, PlortBuffer instanceBuffer, PlortBufferedDescriptorSetPool descriptorSetPool, int instanceCount, int descriptorSetIndex, Frustum frustum, Vector3f aabbOffset) {
        try (Arena arena = Arena.ofConfined()) {
            for (PlortMesh mesh : meshes) {
                if (!frustum.contains(mesh.aabb().translate(aabbOffset))) continue;
                int mapping = materialMappings.getOrDefault(mesh, -1);
                if (mapping != -1) {
                    pOut.put(descriptorSetPool.descriptorSet(descriptorSetIndex, mapping));
                    pOut.flip();

                    cmdBuffer.bindDescriptorSets(PipelineBindPoint.GRAPHICS, layout, 0, pOut, null);
                }

                cmdBuffer.pushConstants(layout, PlortShaderStage.Stage.ALL.bit(), 0, new MeshPushConstant(
                        mesh.vertexBuffer(),
                        mesh.meshBuffer(),
                        mesh.boundsBuffer(),
                        instanceBuffer
                ).toMemorySegment(arena));
                mesh.recordDrawCommandInstanced(cmdBuffer, instanceCount);
            }
        }
    }

    public @UnmodifiableView List<AABB> childAABBs() {
        return childAABBs;
    }

    @SuppressWarnings("unused")
    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        this.closed = true;
        MemoryUtil.memFree(pOut);
        layout.close();
        meshes.forEach(PlortMesh::close);
        textures.forEach(PlortTexture::close);
    }
}
