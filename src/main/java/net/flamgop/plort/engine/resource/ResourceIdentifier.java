package net.flamgop.plort.engine.resource;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

// namespace:path
public record ResourceIdentifier(String namespace, String path) {
    public static final String DEFAULT_NAMESPACE = "borked";

    public static ResourceIdentifier withDefaultNamespace(String path) {
        return new ResourceIdentifier(DEFAULT_NAMESPACE, path);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ResourceIdentifier(String other_namespace, String other_path))) return false;
        return other_namespace.equals(namespace) && other_path.equals(path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public @NonNull String toString() {
        return "ResourceIdentifier{" + namespace + ":" + path + "}";
    }
}
