package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

// TODO: this entire package sucks and I want to kill it.
public class PhysicsWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicsWorld.class);

    private final List<Collider> dynamicColliders = new ArrayList<>();

    private final SpatialHashMap staticObjects;
    private final SpatialHashMap dynamicObjects;

    public PhysicsWorld(float cellSize) {
        staticObjects = new SpatialHashMap(256, cellSize);
        dynamicObjects = new SpatialHashMap(256, cellSize);
    }

    public void add(Collider collider) {
        if (collider.dynamic()) dynamicColliders.add(collider);
        else staticObjects.insert(collider);
    }

    public void update(float dt) {
        dynamicObjects.clear();
        for (Collider collider : dynamicColliders) {
            collider.linearVelocity().add(0, -9.81f * dt, 0);

            collider.position().add(new Vector3f(collider.linearVelocity()).scale(dt));
            collider.rotation().setFrom(collider.rotation().multiply(Quaternionf.fromAngularVelocity(new Vector3f(collider.angularVelocity()), dt)));
            collider.rotation().normalize();
            collider.updateAABBtoMatchPosition();

            dynamicObjects.insert(collider);
        }

        for (int i = 0; i < 5; i++) {
            broadPhase();
        }
    }

    private void broadPhase() {
        Arena staticArena = Arena.ofConfined();
        Arena dynamicArena = Arena.ofConfined();
        for (Collider c : dynamicColliders) {
            staticObjects.query(c, (Collider obj) -> {
                narrowPhaseStatic(staticArena, c, obj);
            });

            dynamicObjects.query(c, (Collider obj) -> {
                if (c == obj) return;
                narrowPhaseDynamic(dynamicArena, c, obj);
            });
        }
        staticArena.close();
        dynamicArena.close();
    }

    // obj is always static
    private void narrowPhaseStatic(Arena arena, Collider c, Collider obj) {
        if (c.resolveStatic(arena, obj)) {
        }
        c.updateAABBtoMatchPosition();
    }

    // obj is always dynamic
    private void narrowPhaseDynamic(Arena arena, Collider c, Collider obj) {
        if (c.resolveDynamic(arena, obj)) {
        }
        c.updateAABBtoMatchPosition();
    }
}
