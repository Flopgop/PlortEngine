package net.flamgop.plort.engine.entity.system;

import com.github.stephengold.joltjni.Body;
import net.flamgop.plort.engine.entity.ComponentStore;
import net.flamgop.plort.engine.entity.EntityManager;
import net.flamgop.plort.engine.entity.components.PhysicsBody;
import net.flamgop.plort.engine.entity.components.Transform;
import net.flamgop.plort.engine.math.val.Matrix4f;
import net.flamgop.plort.engine.math.val.Quaternionf;
import net.flamgop.plort.engine.math.val.Vector3f;
import net.flamgop.plort.engine.physics.PhysicsContext;
import net.flamgop.plort.engine.util.ECSUtil;

public class EntityPhysicsSystem {

    private final PhysicsContext physicsContext;

    public EntityPhysicsSystem() {
        this.physicsContext = new PhysicsContext();
    }

    public PhysicsContext context() {
        return physicsContext;
    }

    public void update(EntityManager entityManager, float dt) {
        physicsContext.update(dt, 1);

        ComponentStore<Transform> transforms = entityManager.store(Transform.class);
        ComponentStore<PhysicsBody> bodies = entityManager.store(PhysicsBody.class);

        ComponentStore<?> smaller = ECSUtil.smallest(transforms, bodies);
        for (int e : smaller.entities()) {
            if (!bodies.has(e) || !transforms.has(e)) continue;

            Transform transform = transforms.get(e);
            PhysicsBody body = bodies.get(e);

            if (body.dynamic() && body.bodies().getFirst().isActive()) {
                Body b = body.bodies().getFirst();
                transform.transform(new Matrix4f()
                        .translate(new Vector3f(b.getPosition()))
                        .rotate(new Quaternionf(b.getRotation().normalized())));
            }
        }
    }
}
