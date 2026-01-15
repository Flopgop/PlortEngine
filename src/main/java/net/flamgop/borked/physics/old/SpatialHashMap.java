package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.AABB;

import java.util.Arrays;
import java.util.function.Consumer;

public class SpatialHashMap {
    private final int numBuckets;
    private final float cellSize;

    private final int[] heads;
    private int[] next;
    private AABBHolder[] contents;

    private int entryCount;

    public SpatialHashMap(int maxColliders, float cellSize) {
        this.numBuckets = maxColliders * 2;
        this.cellSize = cellSize;
        this.heads = new int[numBuckets];
        this.next = new int[maxColliders];
        this.contents = new AABBHolder[maxColliders];
        clear();
    }

    // blegh https://www.beosil.com/download/CollisionDetectionHashing_VMV03.pdf
    private int getBucketFromWorldCoords(float x, float y, float z) {
        return getBucketFromCellCoords((int) Math.floor(x / cellSize), (int) Math.floor(y / cellSize), (int) Math.floor(z / cellSize));
    }

    private int getBucketFromCellCoords(int x, int y, int z) {
        long hash = ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);

        int bucket = (int)(hash % numBuckets);
        return (bucket < 0) ? bucket + numBuckets : bucket;
    }

    public void insert(AABBHolder t) {
        AABB box = t.aabb();

        int minX = (int) Math.floor(box.min().x() / cellSize);
        int maxX = (int) Math.floor(box.max().x() / cellSize);
        int minY = (int) Math.floor(box.min().y() / cellSize);
        int maxY = (int) Math.floor(box.max().y() / cellSize);
        int minZ = (int) Math.floor(box.min().z() / cellSize);
        int maxZ = (int) Math.floor(box.max().z() / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (entryCount >= next.length) {
                        grow();
                    }

                    int bucket = getBucketFromCellCoords(x, y, z);

                    contents[entryCount] = t;
                    next[entryCount] = heads[bucket];
                    heads[bucket] = entryCount;
                    entryCount++;
                }
            }
        }
    }

    public <T extends AABBHolder> void query(AABBHolder t, Consumer<T> action) {
        AABB box = t.aabb();

        int minX = (int) Math.floor(box.min().x() / cellSize);
        int maxX = (int) Math.floor(box.max().x() / cellSize);
        int minY = (int) Math.floor(box.min().y() / cellSize);
        int maxY = (int) Math.floor(box.max().y() / cellSize);
        int minZ = (int) Math.floor(box.min().z() / cellSize);
        int maxZ = (int) Math.floor(box.max().z() / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int bucket = getBucketFromCellCoords(x, y, z);
                    for (int i = heads[bucket]; i != -1; i = next[i]) {
                        //noinspection unchecked
                        action.accept((T) contents[i]);
                    }
                }
            }
        }
    }

    private void grow() {
        int newSize = next.length * 2;
        int[] newNext = new int[newSize];
        AABBHolder[] newContents = new AABBHolder[newSize];

        System.arraycopy(next, 0, newNext, 0, next.length);
        System.arraycopy(contents, 0, newContents, 0, contents.length);

        this.next = newNext;
        this.contents = newContents;
    }

    public void clear() {
        Arrays.fill(heads, -1);
        entryCount = 0;
    }
}
