package net.flamgop.borked;

import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class World implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(World.class);
    private static final boolean DRAW_PLAYER_AABB = false;

    private static final long AABB_SIZE = 2 * Vector3f.BYTES + 2 * Float.BYTES;
    private final PlortAllocator allocator;
    private long aabbCount;
    private PlortBuffer aabbBuffer;

    protected final List<Entity> entities = new ArrayList<>();
    private final CameraController player;

    public World(PlortAllocator allocator, CameraController player) {
        this.allocator = allocator;
        this.player = player;
    }

    public void recreateAABBBuffer() {
        aabbCount = entities.stream().mapToLong(e -> e.model().childAABBs().size()).sum() + (DRAW_PLAYER_AABB ? 1 : 0);
        if (aabbBuffer != null) {
            aabbBuffer.close();
            aabbBuffer = null;
        }
        if (aabbCount <= 0) {
            LOGGER.debug("No AABBs to build in buffer.");
            return;
        }
        aabbBuffer = new PlortBuffer(aabbCount * AABB_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);
        try (MappedMemory mem = aabbBuffer.map()) {
            entities.forEach(e -> e.model().childAABBs().forEach(a -> {
                mem.putVector3f(a.min());
                mem.putFloat(0);
                mem.putVector3f(a.max());
                mem.putFloat(a.hasCollision() ? 1 : 0);
            }));
            if (DRAW_PLAYER_AABB) {
                mem.putVector3f(player.aabb().min());
                mem.putFloat(0);
                mem.putVector3f(player.aabb().max());
                mem.putFloat(0);
            }
        }
    }

    public @Nullable PlortBuffer aabbBuffer() {
        return aabbBuffer;
    }

    public long aabbCount() {
        return aabbCount;
    }

    public void update(float dt) {
        entities.forEach(e -> e.update(dt));
    }

    @Override
    public void close() {
        if (aabbBuffer != null) aabbBuffer.close();
        entities.forEach(Entity::close);
    }
}
