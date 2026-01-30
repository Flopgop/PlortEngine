package net.flamgop.borked.resource;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface IOSupplier {
    InputStream get() throws IOException;
}
