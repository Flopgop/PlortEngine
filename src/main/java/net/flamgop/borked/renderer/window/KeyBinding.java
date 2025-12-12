package net.flamgop.borked.renderer.window;

public record KeyBinding(int key, int mods, InputAction action, Runnable onAction) {
}
