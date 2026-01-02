package net.flamgop.borked.renderer.window;

import net.flamgop.borked.math.Vector2f;
import net.flamgop.borked.renderer.util.Util;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.glfw.GLFW.*;

public class PlortInput {
    private final Vector2f mousePosition = new Vector2f(0);

    private final PlortWindow window;

    private final boolean[] keysPressed = new boolean[GLFW_KEY_LAST];
    private final boolean[] keysPressedOld = new boolean[GLFW_KEY_LAST];
    private final boolean[] buttonsPressed = new boolean[GLFW_MOUSE_BUTTON_LAST];
    private final boolean[] buttonsPressedOld = new boolean[GLFW_MOUSE_BUTTON_LAST];

    public PlortInput(PlortWindow window) {
        this.window = window;
        Util.closeIfNotNull(glfwSetCursorPosCallback(window.handle(), (_, x, y) -> {
            this.mousePosition.x((float) x);
            this.mousePosition.y((float) y);
        }));
        Util.closeIfNotNull(glfwSetKeyCallback(window.handle(), (_, key, scancode, action, mods) -> {
            keysPressed[key] = action == GLFW_PRESS || action == GLFW_REPEAT;
        }));
        Util.closeIfNotNull(glfwSetMouseButtonCallback(window.handle(), (_, button, action, mods) -> {
            buttonsPressed[button] = action == GLFW_PRESS || action == GLFW_REPEAT;
        }));
    }

    public void update() {
        System.arraycopy(keysPressed, 0, keysPressedOld, 0, keysPressed.length);
        System.arraycopy(buttonsPressed, 0, buttonsPressedOld, 0, buttonsPressed.length);
    }

    public void setCursorState(CursorState state) {
        GLFW.glfwSetInputMode(window.handle(), GLFW_CURSOR, state.qualifier());
    }

    public Vector2f mousePosition() {
        return new Vector2f(mousePosition);
    }

    public boolean keyDown(int key) {
        return keysPressed[key];
    }

    public boolean keyPressed(int key) {
        return keysPressed[key] && !keysPressedOld[key];
    }

    public boolean buttonDown(int button) {
        return buttonsPressed[button];
    }

    public boolean buttonPressed(int button) {
        return buttonsPressed[button] && !buttonsPressedOld[button];
    }
}
