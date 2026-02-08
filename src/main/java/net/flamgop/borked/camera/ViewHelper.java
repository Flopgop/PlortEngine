package net.flamgop.borked.camera;

import net.flamgop.borked.math.val.Matrix4f;
import net.flamgop.borked.math.val.Vector3f;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortBuffer;

public class ViewHelper {
    public static void uploadViewBuffer(PlortBuffer buffer, Matrix4f view, Matrix4f projection, Vector3f cameraPos) {
        try (MappedMemory mem = buffer.map()) {
            mem.putMatrix4f(projection.multiply(view));
            mem.putMatrix4f(view);
            mem.putMatrix4f(projection);
            mem.putMatrix4f(view.invert());
            mem.putMatrix4f(projection.invert());
            mem.putVector3f(cameraPos);
            mem.putFloat(0f);
        }
    }
}
