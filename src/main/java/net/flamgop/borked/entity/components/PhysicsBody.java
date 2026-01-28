package net.flamgop.borked.entity.components;

import com.github.stephengold.joltjni.Body;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhysicsBody {
    private final boolean dynamic;
    private final List<Body> bodies = new ArrayList<>();

    public PhysicsBody(List<Body> bodies) {
        if (bodies.isEmpty()) throw new IllegalStateException("Cannot create a physics body with no bodies.");
        this.bodies.addAll(bodies);
        dynamic = bodies.size() == 1;
    }

    public boolean dynamic() {
        return dynamic;
    }

    public @Unmodifiable List<Body> bodies() {
        return Collections.unmodifiableList(bodies);
    }
}
