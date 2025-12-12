package net.flamgop.borked.renderer.exception;

public class ResourceLeakedException extends Exception {
    public ResourceLeakedException(Class<?> resource) {
        super(resource.getName() + " Leaked: ");
    }
}
