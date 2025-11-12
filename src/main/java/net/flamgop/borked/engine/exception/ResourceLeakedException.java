package net.flamgop.borked.engine.exception;

public class ResourceLeakedException extends Exception {
    public ResourceLeakedException(Class<?> resource) {
        super(resource.getName() + " Leaked: ");
    }
}
