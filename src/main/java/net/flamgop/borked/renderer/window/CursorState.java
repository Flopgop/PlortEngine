package net.flamgop.borked.renderer.window;

import static org.lwjgl.glfw.GLFW.*;

public enum CursorState {
    NORMAL(GLFW_CURSOR_NORMAL),
    HIDDEN(GLFW_CURSOR_HIDDEN),
    DISABLED(GLFW_CURSOR_DISABLED),
    CAPTURED(GLFW_CURSOR_CAPTURED),

    ;
    private final int glfwQualifier;
    CursorState(int glfwQualifier) {
        this.glfwQualifier = glfwQualifier;
    }
    public int qualifier() {
        return glfwQualifier;
    }
}
