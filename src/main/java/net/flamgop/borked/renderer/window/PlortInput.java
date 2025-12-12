package net.flamgop.borked.renderer.window;

import net.flamgop.borked.renderer.util.Util;
import org.joml.Vector2d;

import static org.lwjgl.glfw.GLFW.*;

public class PlortInput {
    private final Vector2d mousePosition = new Vector2d();

    private final boolean[] keysPressed = new boolean[GLFW_KEY_LAST];
    private final boolean[] buttonsPressed = new boolean[GLFW_MOUSE_BUTTON_LAST];

    public PlortInput(PlortWindow window) {
        Util.closeIfNotNull(glfwSetCursorPosCallback(window.handle(), (_, x, y) -> {
            this.mousePosition.x = x;
            this.mousePosition.y = y;
        }));
        Util.closeIfNotNull(glfwSetKeyCallback(window.handle(), (_, key, scancode, action, mods) -> {
            keysPressed[key] = action == GLFW_PRESS || action == GLFW_REPEAT;
        }));
        Util.closeIfNotNull(glfwSetMouseButtonCallback(window.handle(), (_, button, action, mods) -> {
            buttonsPressed[button] = action == GLFW_PRESS || action == GLFW_REPEAT;
        }));
    }

    public Vector2d mousePosition() {
        return new Vector2d(mousePosition);
    }

    public boolean keyDown(int key) {
        return keysPressed[key];
    }

    public boolean buttonDown(int button) {
        return buttonsPressed[button];
    }
}
