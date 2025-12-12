package net.flamgop.borked.renderer.memory;

import net.flamgop.borked.renderer.exception.ResourceLeakedException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/// @implNote Any object extending this class cannot be garbage collected, however it MUST call super() in its constructor, and it MUST call super.close() in its overridden close method.
public abstract class TrackedCloseable implements AutoCloseable {

    private static final Set<TrackedCloseable> INSTANCES = Collections.synchronizedSet(new HashSet<>());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (TrackedCloseable t : INSTANCES) {
                if (t != null && !t.closed) {
                    t.creationStack.printStackTrace(System.err);
                }
            }
        }));
    }

    private final Exception creationStack;
    private volatile boolean closed = false;

    public TrackedCloseable() {
        this.creationStack = new ResourceLeakedException(this.getClass());
        INSTANCES.add(this);
    }

    public void close() {
        this.closed = true;
    }
}
