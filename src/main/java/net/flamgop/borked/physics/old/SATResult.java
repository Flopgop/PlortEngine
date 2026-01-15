package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.Vector3f;

import java.util.List;

public record SATResult(boolean collided, Vector3f normal, float penetrationDepth, List<Vector3f> collisionManifold) {
}
