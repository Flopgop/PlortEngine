package net.flamgop.borked.renderer.model;

import net.flamgop.borked.math.AABB;
import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.memory.*;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.meshoptimizer.MeshOptimizer;
import org.lwjgl.util.meshoptimizer.MeshoptBounds;
import org.lwjgl.util.meshoptimizer.MeshoptMeshlet;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK14.*;

public class PlortMesh extends TrackedCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlortMesh.class);

    public static final int MAX_VERTICES_PER_MESHLET = 64, MAX_TRIANGLES_PER_MESHLET = 96;
    public static final int MESHLET_SIZE = Integer.BYTES * MAX_VERTICES_PER_MESHLET + Integer.BYTES * 3 * MAX_TRIANGLES_PER_MESHLET + Integer.BYTES + Integer.BYTES + Long.BYTES;
    public static final int VERTEX_SIZE = 3 * Float.BYTES + 3 * Float.BYTES + 4 * Float.BYTES + 2 * Float.BYTES;
    public static final int BOUNDS_SIZE = 4 * Float.BYTES + 4 * Float.BYTES + 4 * Float.BYTES;

    private final AABB aabb;
    private final PlortBuffer vertexBuffer, meshBuffer, boundsBuffer;
    private final int meshletCount;

    public PlortMesh(PlortAllocator allocator, AIMesh mesh, boolean hasCollision, Matrix4f transform) {
        super();
        Matrix4f normalMatrix = new Matrix4f(transform).invert().transpose();

        int numVertices = mesh.mNumVertices();
        int numFaces = mesh.mNumFaces();
        int numIndices = numFaces * 3;

        AIVector3D.Buffer positions = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        AIVector3D.Buffer tangents = mesh.mTangents();
        AIVector3D.Buffer bitangents = mesh.mBitangents();
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
            Vector3f position = transform.transform(new Vector3f(v.x(), v.y(), v.z()));
            vertices.put(position.x()).put(position.y()).put(position.z());
        }
        vertices.flip();

        meshletCount = Math.toIntExact(MeshOptimizer.meshopt_buildMeshlets(meshlets, meshletVertices, meshletTriangles, indices, vertices, numVertices, 12, MAX_VERTICES_PER_MESHLET, MAX_TRIANGLES_PER_MESHLET, 0.0f));

        LOGGER.debug("Mesh has {} vertices, {} triangles, and {} meshlets", numVertices, numFaces, meshletCount);

        this.vertexBuffer = new PlortBuffer((long) numVertices * VERTEX_SIZE, BufferUsage.STORAGE_BUFFER_BIT , allocator);
        this.meshBuffer = new PlortBuffer((long) meshletCount * MESHLET_SIZE, BufferUsage.STORAGE_BUFFER_BIT , allocator);
        this.boundsBuffer = new PlortBuffer((long) meshletCount * BOUNDS_SIZE, BufferUsage.STORAGE_BUFFER_BIT , allocator);

        vertexBuffer.label(mesh.mName().dataString() + " Vertex");
        meshBuffer.label(mesh.mName().dataString() + " Mesh");
        boundsBuffer.label(mesh.mName().dataString() + " Bounds");

        try (MappedMemory mem = vertexBuffer.map()) {
            for (int i = 0; i < numVertices; i++) {
                mem.putFloat(vertices.get(i*3));
                mem.putFloat(vertices.get(i*3+1));
                mem.putFloat(vertices.get(i*3+2));
                if (normals != null) {
                    AIVector3D n = normals.get(i);
                    Vector3f normal = normalMatrix.transformDirection(new Vector3f(n.x(), n.y(), n.z()));
                    mem.putVector3f(normal);

                    if (tangents != null && bitangents != null) {
                        AIVector3D tan = tangents.get(i);
                        Vector3f tangent = normalMatrix.transformDirection(new Vector3f(tan.x(), tan.y(), tan.z()));

                        mem.putVector3f(tangent);

                        AIVector3D bitangent = bitangents.get(i);

                        Vector3f b = normalMatrix.transformDirection(new Vector3f(bitangent.x(), bitangent.y(), bitangent.z()));

                        float sign = normal.cross(tangent).dot(b) < 0f ? -1f : 1f;
                        mem.putFloat(sign);
                    } else {
                        LOGGER.warn("Null tangents!");
                        mem.putFloat(0);
                        mem.putFloat(0);
                        mem.putFloat(0);
                        mem.putFloat(1);
                    }
                } else {
                    LOGGER.warn("Null normals!");
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(0);
                    mem.putFloat(1);
                }
                if (texcoords != null) {
                    AIVector3D texcoord = texcoords.get(i);
                    mem.putFloat(texcoord.x());
                    mem.putFloat(texcoord.y());
                } else {
                    mem.putFloat(0);
                    mem.putFloat(0);
                }
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

        AIVector3D amin = mesh.mAABB().mMin();
        AIVector3D amax = mesh.mAABB().mMax();
        Vector3f min = new Vector3f(amin.x(), amin.y(), amin.z());
        Vector3f max = new Vector3f(amax.x(), amax.y(), amax.z());

        Vector3f[] corners = {
                new Vector3f(min.x(), min.y(), min.z()),
                new Vector3f(min.x(), min.y(), max.z()),
                new Vector3f(min.x(), max.y(), min.z()),
                new Vector3f(min.x(), max.y(), max.z()),
                new Vector3f(max.x(), min.y(), min.z()),
                new Vector3f(max.x(), min.y(), max.z()),
                new Vector3f(max.x(), max.y(), min.z()),
                new Vector3f(max.x(), max.y(), max.z()),
        };

        Vector3f newMin = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f newMax = new Vector3f(Float.NEGATIVE_INFINITY);

        for (Vector3f c : corners) {
            Vector3f t = transform.transform(c);
            newMin = newMin.min(t);
            newMax = newMax.max(t);
        }

        this.aabb = new AABB(newMin, newMax, hasCollision);

        meshlets.close();
        MemoryUtil.memFree(meshletTriangles);
        MemoryUtil.memFree(meshletVertices);
        MemoryUtil.memFree(vertices);
        MemoryUtil.memFree(indices);
    }

    public AABB aabb() {
        return new AABB(aabb);
    }

    public PlortBuffer vertexBuffer() {
        return vertexBuffer;
    }

    public PlortBuffer meshBuffer() {
        return meshBuffer;
    }

    public PlortBuffer boundsBuffer() {
        return boundsBuffer;
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

    public void recordDrawCommand(PlortCommandBuffer commandBuffer) {
        commandBuffer.drawMeshTasksEXT(meshletCount, 1, 1);
    }

    public void recordDrawCommandInstanced(PlortCommandBuffer commandBuffer, int numInstances) {
        commandBuffer.drawMeshTasksEXT(meshletCount, numInstances, 1);
    }

    @Override
    public void close() {
        vertexBuffer.close();
        meshBuffer.close();
        boundsBuffer.close();
        super.close();
    }
}
