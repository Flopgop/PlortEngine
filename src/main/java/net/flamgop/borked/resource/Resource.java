package net.flamgop.borked.resource;

import java.io.IOException;
import java.io.InputStream;

public class Resource {
    private final ResourceIdentifier identifier;
    private final IOSupplier stream;

    Resource(ResourceIdentifier identifier, IOSupplier stream) {
        this.identifier = identifier;
        this.stream = stream;
    }

    public ResourceIdentifier identifier() {
        return identifier;
    }

    public InputStream open() throws IOException {
        return stream.get();
    }
}
