package net.flamgop.plort.engine.util;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.QuatArg;
import net.flamgop.plort.engine.entity.ComponentStore;
import net.flamgop.plort.engine.entity.components.Transform;
import net.flamgop.plort.engine.math.val.Quaternionf;
import net.flamgop.plort.engine.math.val.Vector3f;
import net.flamgop.plort.engine.model.PlortMesh;
import net.flamgop.plort.engine.model.PlortModel;
import net.flamgop.plort.engine.physics.PhysicsContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ECSUtil {
    public static ComponentStore<?> smallest(ComponentStore<?> a, ComponentStore<?> b) {
        return a.size() < b.size() ? a : b;
    }

    public static ComponentStore<?> smallest(ComponentStore<?> ...stores) {
        return Arrays.stream(stores).min(Comparator.comparingInt(ComponentStore::size)).orElseThrow();
    }

    @SuppressWarnings("resource")
    public static List<Body> createBodiesFromModel(PhysicsContext physics, PlortModel model, Transform initialTransform) {
        if (model.childMeshes().isEmpty()) throw new IllegalStateException("Cannot create physics bodies for an empty mesh.");
        List<Body> bodies = new ArrayList<>();

        boolean dynamic = model.childMeshes().size() == 1;
        Quaternionf identity = new Quaternionf();
        for (PlortMesh child : model.childMeshes()) {
            ConvexShapeSettings shapeSettings;
            if (dynamic) {
                Vector3f halfExtents = child.aabb().size().scale(0.5f);
                shapeSettings = new BoxShapeSettings(halfExtents.x(), halfExtents.y(), halfExtents.z());
            } else {
                shapeSettings = new ConvexHullShapeSettings(child.vertices().stream().map(Vector3f::toJoltVec3).toList());
            }
            shapeSettings.setDensity(150);
            ConstShape shape = shapeSettings.create().get();

            BodyCreationSettings settings = new BodyCreationSettings(shape, dynamic ? child.aabb().center().toJoltVec3().toRVec3() : new RVec3(), identity.toJoltQuat(), dynamic ? EMotionType.Dynamic : EMotionType.Static, 0);
            settings.setFriction(0.5f);
            Body body = physics.addBody(settings, EActivation.Activate);
            bodies.add(body);
        }

        Vector3f pos = initialTransform.transform().position();
        Quaternionf rot = initialTransform.transform().rotation();

        RVec3 joltPos = pos.toJoltVec3().toRVec3();
        QuatArg joltRot = rot.toJoltQuat();

        BodyInterface bodyInterface = physics.system().getBodyInterface();

        for (Body body : bodies) {
            bodyInterface.setPositionAndRotation(body.getId(), joltPos, joltRot, EActivation.Activate);
        }

        return bodies;
    }
}
