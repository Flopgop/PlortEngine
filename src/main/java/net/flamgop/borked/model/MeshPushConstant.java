package net.flamgop.borked.model;

import net.flamgop.borked.renderer.memory.PlortBuffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public value record MeshPushConstant(long vertexBuffer, long meshBuffer, long boundsBuffer, long instanceBuffer) {

    public MeshPushConstant(
            PlortBuffer vertexBuffer,
            PlortBuffer meshBuffer,
            PlortBuffer boundsBuffer,
            PlortBuffer instanceBuffer
    ) {
        this(vertexBuffer.deviceAddress(), meshBuffer.deviceAddress(), boundsBuffer.deviceAddress(), instanceBuffer.deviceAddress());
    }

    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("vertexBuffer"),
            ValueLayout.JAVA_LONG.withName("meshBuffer"),
            ValueLayout.JAVA_LONG.withName("boundsBuffer"),
            ValueLayout.JAVA_LONG.withName("instanceBuffer")
    );

    private static final VarHandle VERTEX_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("vertexBuffer"));
    private static final VarHandle MESH_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("meshBuffer"));
    private static final VarHandle BOUNDS_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("boundsBuffer"));
    private static final VarHandle INSTANCE_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("instanceBuffer"));

    public MemorySegment toMemorySegment(Arena arena) {
        MemorySegment segment = arena.allocate(LAYOUT);
        VERTEX_HANDLE.set(segment, 0L, vertexBuffer);
        MESH_HANDLE.set(segment, 0L, meshBuffer);
        BOUNDS_HANDLE.set(segment, 0L, boundsBuffer);
        INSTANCE_HANDLE.set(segment, 0L, instanceBuffer);
        return segment;
    }
}
