package net.flamgop.borked.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ResourceManager {
    private final Path resourceRoot;

    public ResourceManager(Path resourceRoot) throws IOException {
        this.resourceRoot = resourceRoot;
        if (!Files.exists(resourceRoot)) {
            Files.createDirectory(resourceRoot);
        }
    }

    public Resource get(ResourceIdentifier identifier) {
        Path resourcePath = resourceRoot.resolve(identifier.namespace(), identifier.path());
        if (!Files.exists(resourcePath)) {
            String path = Path.of("assets", identifier.namespace(), identifier.path()).toString();
            return new Resource(identifier, () -> ResourceManager.class.getClassLoader().getResourceAsStream(path));
        } else {
            return new Resource(identifier, () -> Files.newInputStream(resourcePath, StandardOpenOption.READ));
        }
    }

    public InputStream open(ResourceIdentifier identifier) throws IOException {
        Path resourcePath = resourceRoot.resolve(identifier.namespace(), identifier.path());
        if (!Files.exists(resourcePath)) {
            return ResourceManager.class.getClassLoader().getResourceAsStream(Path.of("assets", identifier.namespace(), identifier.path()).toString());
        } else {
            return Files.newInputStream(resourcePath, StandardOpenOption.READ);
        }
    }
}
