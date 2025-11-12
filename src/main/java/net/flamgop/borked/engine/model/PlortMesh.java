package net.flamgop.borked.engine.model;

import net.flamgop.borked.engine.memory.MappedMemory;
import net.flamgop.borked.engine.memory.PlortAllocator;
import net.flamgop.borked.engine.memory.PlortBuffer;
import net.flamgop.borked.engine.memory.TrackedCloseable;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;
import org.lwjgl.util.meshoptimizer.MeshOptimizer;
import org.lwjgl.util.meshoptimizer.MeshoptBounds;
import org.lwjgl.util.meshoptimizer.MeshoptMeshlet;
import org.lwjgl.util.par.ParShapes;
import org.lwjgl.util.par.ParShapesMesh;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.vulkan.EXTMeshShader.*;
import static org.lwjgl.vulkan.VK14.*;

public class PlortMesh extends TrackedCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlortMesh.class);

    private static final int MAX_VERTICES_PER_MESHLET = 64, MAX_TRIANGLES_PER_MESHLET = 96;
    private static final int MESHLET_SIZE = Integer.BYTES * MAX_VERTICES_PER_MESHLET + Integer.BYTES * 3 * MAX_TRIANGLES_PER_MESHLET + Integer.BYTES + Integer.BYTES + Long.BYTES;
    private static final int VERTEX_SIZE = 4 * Float.BYTES + 4 * Float.BYTES + 4 * Float.BYTES;
    private static final int BOUNDS_SIZE = 4 * Float.BYTES + 4 * Float.BYTES + 4 * Float.BYTES;

    private final PlortBuffer vertexBuffer, meshBuffer, boundsBuffer;
    private final int meshletCount;

    public PlortMesh(PlortAllocator allocator, AIMesh mesh) {
        super();
        int numVertices = mesh.mNumVertices();
        int numFaces = mesh.mNumFaces();
        int numIndices = numFaces * 3;

        AIVector3D.Buffer positions = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        AIVector3D.Buffer texcoords = mesh.mTextureCoords(0);

        IntBuffer indices = MemoryUtil.memAllocInt(numIndices);
        AIFace.Buffer faces = mesh.mFaces();
        for (int i = 0; i < numFaces; i++) {
            AIFace face = faces.get(i);
            if (face.mNumIndices() != 3) continue;
            indices.put(face.mIndices());
        }
        indices.flip();

        int worstCaseMeshletCount = Math.toIntExact(MeshOptimizer.meshopt_buildMeshletsBound(numIndices, MAX_VERTICES_PER_MESHLET, MAX_TRIANGLES_PER_MESHLET));

        MeshoptMeshlet.Buffer meshlets = MeshoptMeshlet.calloc(worstCaseMeshletCount);
        IntBuffer meshletVertices = MemoryUtil.memAllocInt(MAX_VERTICES_PER_MESHLET * worstCaseMeshletCount);
        ByteBuffer meshletTriangles = MemoryUtil.memAlloc(MAX_TRIANGLES_PER_MESHLET * 3 * worstCaseMeshletCount);

        FloatBuffer vertices = MemoryUtil.memAllocFloat(numVertices * 3);
        for (int i = 0; i < numVertices; i++) {
            AIVector3D v = positions.get(i);
            vertices.put(v.x()).put(v.y()).put(v.z());
        }
        vertices.flip();

        meshletCount = Math.toIntExact(MeshOptimizer.meshopt_buildMeshlets(meshlets, meshletVertices, meshletTriangles, indices, vertices, numVertices, 12, MAX_VERTICES_PER_MESHLET, MAX_TRIANGLES_PER_MESHLET, 0.0f));

        LOGGER.debug("Mesh has {} vertices, {} triangles, and {} meshlets", numVertices, numFaces, meshletCount);

        this.vertexBuffer = new PlortBuffer((long) numVertices * VERTEX_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);
        this.meshBuffer = new PlortBuffer((long) meshletCount * MESHLET_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);
        this.boundsBuffer = new PlortBuffer((long) meshletCount * BOUNDS_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);

        vertexBuffer.label(mesh.mName().dataString() + " Vertex Buffer");
        meshBuffer.label(mesh.mName().dataString() + " Mesh Buffer");
        boundsBuffer.label(mesh.mName().dataString() + " Bounds Buffer");

        try (MappedMemory mem = vertexBuffer.map()) {
            for (int i = 0; i < numVertices; i++) {
                mem.putFloat(vertices.get(i * 3));
                mem.putFloat(vertices.get(i * 3 + 1));
                mem.putFloat(vertices.get(i * 3 + 2));
                mem.putFloat(0);
                if (normals != null) {
                    AIVector3D normal = normals.get(i);
                    mem.putFloat(normal.x());
                    mem.putFloat(normal.y());
                    mem.putFloat(normal.z());
                } else {
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(0);
                }
                mem.putFloat(0);
                if (texcoords != null) {
                    AIVector3D texcoord = texcoords.get(i);
                    mem.putFloat(texcoord.x());
                    mem.putFloat(texcoord.y());
                } else {
                    mem.putFloat(0);
                    mem.putFloat(0);
                }
                mem.putFloat(0);
                mem.putFloat(0);
            }
        }

        try (MappedMemory mem = meshBuffer.map()) {
            for (int i = 0; i < meshletCount; i++) {
                MeshoptMeshlet meshlet = meshlets.get(i);

                for (int v = 0; v < meshlet.vertex_count(); v++) {
                    int vertexIndex = meshletVertices.get(meshlet.vertex_offset() + v);
                    mem.putInt(vertexIndex);
                }

                for (int v = meshlet.vertex_count(); v < MAX_VERTICES_PER_MESHLET; v++) {
                    mem.putInt(0);
                }

                for (int t = 0; t < meshlet.triangle_count() * 3; t++) {
                    byte idx = meshletTriangles.get(meshlet.triangle_offset() + t);
                    mem.putInt(idx & 0xFF);
                }

                for (int t = meshlet.triangle_count() * 3; t < 3 * MAX_TRIANGLES_PER_MESHLET; t++) {
                    mem.putInt(0);
                }

                mem.putInt(meshlet.vertex_count());
                mem.putInt(meshlet.triangle_count() * 3);

                mem.putLong(0);
            }
        }

        try (MappedMemory mem = boundsBuffer.map()) {
            for (int i = 0; i < meshletCount; i++) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    MeshoptMeshlet meshlet = meshlets.get(i);

                    MeshoptBounds bounds = MeshoptBounds.calloc(stack);
                    MeshOptimizer.meshopt_computeMeshletBounds(meshletVertices.slice(meshlet.vertex_offset(), meshlet.vertex_count()), meshletTriangles.slice(meshlet.triangle_offset(), meshlet.triangle_count()), vertices, numVertices, 12, bounds);

                    mem.putFloat(bounds.center(0));
                    mem.putFloat(bounds.center(1));
                    mem.putFloat(bounds.center(2));
                    mem.putFloat(bounds.radius());

                    mem.putFloat(bounds.cone_apex(0));
                    mem.putFloat(bounds.cone_apex(1));
                    mem.putFloat(bounds.cone_apex(2));
                    mem.putFloat(bounds.cone_cutoff());

                    mem.putFloat(bounds.cone_axis(0));
                    mem.putFloat(bounds.cone_axis(1));
                    mem.putFloat(bounds.cone_axis(2));
                    mem.putFloat(0); // pad
                }
            }
        }

        meshlets.close();
        MemoryUtil.memFree(meshletTriangles);
        MemoryUtil.memFree(meshletVertices);
        MemoryUtil.memFree(vertices);
        MemoryUtil.memFree(indices);
    }

    public PlortMesh(PlortAllocator allocator, String path) {
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

        AIMesh mesh = AIMesh.create(scene.mMeshes().get(0));
        this(allocator, mesh);
        Assimp.aiFreeScene(scene);
    }

    public PlortMesh(PlortAllocator allocator, ParShapesMesh mesh) {
        super();
        int nIndices = mesh.ntriangles() * 3;
        int worstCaseMeshletCount = Math.toIntExact(MeshOptimizer.meshopt_buildMeshletsBound(nIndices, MAX_VERTICES_PER_MESHLET, MAX_TRIANGLES_PER_MESHLET));

        ParShapes.par_shapes_compute_normals(mesh);

        // note: these are heap allocated because even for *small* meshes they are much too big for a stack
        MeshoptMeshlet.Buffer meshlets = MeshoptMeshlet.calloc(worstCaseMeshletCount);
        IntBuffer meshletVertices = MemoryUtil.memAllocInt(MAX_VERTICES_PER_MESHLET * worstCaseMeshletCount);
        ByteBuffer meshletTriangles = MemoryUtil.memAlloc(MAX_TRIANGLES_PER_MESHLET * 3 * worstCaseMeshletCount);
        IntBuffer indices = mesh.triangles(3 * mesh.ntriangles());
        FloatBuffer vertices = mesh.points(3 * mesh.npoints());
        FloatBuffer normals = mesh.normals(3 * mesh.npoints());
        FloatBuffer tcoords = mesh.tcoords(2 * mesh.npoints());

        int meshletCount = Math.toIntExact(MeshOptimizer.meshopt_buildMeshlets(meshlets, meshletVertices, meshletTriangles, indices, vertices, mesh.npoints(), 12, MAX_VERTICES_PER_MESHLET, MAX_TRIANGLES_PER_MESHLET, 0.0f));

        LOGGER.debug("Mesh has {} vertices, {} triangles, and {} meshlets", mesh.npoints(), mesh.ntriangles(), meshletCount);

        this.vertexBuffer = new PlortBuffer((long) mesh.npoints() * VERTEX_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);
        this.meshBuffer = new PlortBuffer((long) meshletCount * MESHLET_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);
        this.boundsBuffer = new PlortBuffer((long) meshletCount * BOUNDS_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, allocator);

        vertexBuffer.label(mesh + " Vertex Buffer");
        meshBuffer.label(mesh + " Mesh Buffer");
        boundsBuffer.label(mesh + " Bounds Buffer");

        try (MappedMemory mem = vertexBuffer.map()) {
            for (int i = 0; i < mesh.npoints(); i++) {
                mem.putFloat(vertices.get(i * 3));
                mem.putFloat(vertices.get(i * 3 + 1));
                mem.putFloat(vertices.get(i * 3 + 2));
                mem.putFloat(0);
                if (normals != null) {
                    mem.putFloat(normals.get(i * 3));
                    mem.putFloat(normals.get(i * 3 + 1));
                    mem.putFloat(normals.get(i * 3 + 2));
                } else {
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(0);
                }
                mem.putFloat(0);
                if (tcoords != null) {
                    mem.putFloat(tcoords.get(i * 2));
                    mem.putFloat(tcoords.get(i * 2 + 1));
                } else {
                    mem.putFloat(0);
                    mem.putFloat(0);
                }
                mem.putFloat(0);
                mem.putFloat(0);
            }
        }

        try (MappedMemory mem = meshBuffer.map()) {
            for (int i = 0; i < meshletCount; i++) {
                MeshoptMeshlet meshlet = meshlets.get(i);

                for (int v = 0; v < meshlet.vertex_count(); v++) {
                    int vertexIndex = meshletVertices.get(meshlet.vertex_offset() + v);
                    mem.putInt(vertexIndex);
                }

                for (int v = meshlet.vertex_count(); v < MAX_VERTICES_PER_MESHLET; v++) {
                    mem.putInt(0);
                }

                for (int t = 0; t < meshlet.triangle_count() * 3; t++) {
                    byte idx = meshletTriangles.get(meshlet.triangle_offset() + t);
                    mem.putInt(idx & 0xFF);
                }

                for (int t = meshlet.triangle_count() * 3; t < 3 * MAX_TRIANGLES_PER_MESHLET; t++) {
                    mem.putInt(0);
                }

                mem.putInt(meshlet.vertex_count());
                mem.putInt(meshlet.triangle_count() * 3);

                mem.putLong(0);
            }
        }

        try (MappedMemory mem = boundsBuffer.map()) {
            for (int i = 0; i < meshletCount; i++) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    MeshoptMeshlet meshlet = meshlets.get(i);

                    MeshoptBounds bounds = MeshoptBounds.calloc(stack);
                    MeshOptimizer.meshopt_computeMeshletBounds(meshletVertices.slice(meshlet.vertex_offset(), meshlet.vertex_count()), meshletTriangles.slice(meshlet.triangle_offset(), meshlet.triangle_count()), vertices, mesh.npoints(), 12, bounds);

                    mem.putFloat(bounds.center(0));
                    mem.putFloat(bounds.center(1));
                    mem.putFloat(bounds.center(2));
                    mem.putFloat(bounds.radius());

                    mem.putFloat(bounds.cone_apex(0));
                    mem.putFloat(bounds.cone_apex(1));
                    mem.putFloat(bounds.cone_apex(2));
                    mem.putFloat(bounds.cone_cutoff());

                    mem.putFloat(bounds.cone_axis(0));
                    mem.putFloat(bounds.cone_axis(1));
                    mem.putFloat(bounds.cone_axis(2));
                    mem.putFloat(0); // pad
                }
            }
        }

        this.meshletCount = meshletCount;

        MemoryUtil.memFree(meshletTriangles);
        MemoryUtil.memFree(meshletVertices);
        meshlets.close();
    }

    public int updateDescriptors(List<NativeResource> freeMeWhenDone, long descriptorSet, VkWriteDescriptorSet.Buffer writes, int baseIndex) {
        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(3);
        freeMeWhenDone.add(bufferInfos);

        return writeDescriptors(descriptorSet, writes, baseIndex, bufferInfos);
    }

    public int updateDescriptors(MemoryStack stack, long descriptorSet, VkWriteDescriptorSet.Buffer writes, int baseIndex) {
        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(3, stack);

        return writeDescriptors(descriptorSet, writes, baseIndex, bufferInfos);
    }

    @SuppressWarnings("resource")
    private int writeDescriptors(long descriptorSet, VkWriteDescriptorSet.Buffer writes, int baseIndex, VkDescriptorBufferInfo.Buffer bufferInfos) {
        bufferInfos.get(0)
                .offset(0)
                .buffer(vertexBuffer.handle())
                .range(vertexBuffer.size());
        bufferInfos.get(1)
                .offset(0)
                .buffer(meshBuffer.handle())
                .range(meshBuffer.size());
        bufferInfos.get(2)
                .offset(0)
                .buffer(boundsBuffer.handle())
                .range(boundsBuffer.size());

        writes.get(baseIndex)
                .sType$Default()
                .dstSet(descriptorSet)
                .dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(bufferInfos.position(0));

        writes.get(baseIndex + 1)
                .sType$Default()
                .dstSet(descriptorSet)
                .dstBinding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(bufferInfos.position(1));

        writes.get(baseIndex + 2)
                .sType$Default()
                .dstSet(descriptorSet)
                .dstBinding(2)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(bufferInfos.position(2));
        return 3;
    }

    public void recordDrawCommand(VkCommandBuffer commandBuffer) {
        vkCmdDrawMeshTasksEXT(commandBuffer, meshletCount, 1, 1);
    }

    public void recordDrawCommandInstanced(VkCommandBuffer commandBuffer, int numInstances) {
        vkCmdDrawMeshTasksEXT(commandBuffer, meshletCount, numInstances, 1);
    }

    @Override
    public void close() {
        vertexBuffer.close();
        meshBuffer.close();
        boundsBuffer.close();
        super.close();
    }
}
