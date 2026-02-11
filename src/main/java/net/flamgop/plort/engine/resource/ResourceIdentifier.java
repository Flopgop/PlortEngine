package net.flamgop.plort.engine.resource;

// namespace:path
public value record ResourceIdentifier(String namespace, String path) {
    public static final String DEFAULT_NAMESPACE = "borked";

    public static ResourceIdentifier withDefaultNamespace(String path) {
        return new ResourceIdentifier(DEFAULT_NAMESPACE, path);
    }
}
