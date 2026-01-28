package net.flamgop.borked.entity.system;

import com.github.stephengold.joltjni.Body;
import net.flamgop.borked.entity.ComponentStore;
import net.flamgop.borked.entity.EntityManager;
import net.flamgop.borked.entity.components.PhysicsBody;
import net.flamgop.borked.entity.components.Transform;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.physics.PhysicsContext;
import net.flamgop.borked.util.ECSUtil;

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

        Vector3f workVector = new Vector3f();
        Quaternionf workQuat = new Quaternionf();
        ComponentStore<?> smaller = ECSUtil.smallest(transforms, bodies);
        for (int e : smaller.entities()) {
            if (!bodies.has(e) || !transforms.has(e)) continue;

            Transform transform = transforms.get(e);
            PhysicsBody body = bodies.get(e);

            if (body.dynamic() && body.bodies().getFirst().isActive()) {
                Body b = body.bodies().getFirst();
                transform.transform().identity()
                        .translate(workVector.setFrom(b.getPosition()))
                        .rotate(workQuat.setFrom(b.getRotation().normalized()));
            }
        }
    }
}
