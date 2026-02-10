package net.flamgop.plort.engine.renderer.util;

public class Formats {
    public static String address(long address) {
        return String.format("0x%016x", address);
    }
}
