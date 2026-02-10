package net.flamgop.plort.engine.renderer.exception;

public class ResourceLeakedException extends Exception {
    public ResourceLeakedException(Class<?> resource) {
        super(resource.getName() + " Leaked: ");
    }
}
