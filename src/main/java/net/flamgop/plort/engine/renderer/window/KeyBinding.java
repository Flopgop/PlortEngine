package net.flamgop.plort.engine.renderer.window;

public record KeyBinding(int key, int mods, InputAction action, Runnable onAction) {
}
