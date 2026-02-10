package net.flamgop.plort.engine.resource;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface IOSupplier {
    InputStream get() throws IOException;
}
