package net.flamgop.borked.renderer.memory;

import java.lang.reflect.Array;
import java.util.function.Function;
import java.util.function.Supplier;

public class BufferedObject<T extends AutoCloseable> implements AutoCloseable {
    private final T[] objects;
    private int index = 0;

    @SuppressWarnings({"unchecked", "resource"})
    public BufferedObject(Class<T> typeParameterClass, int numObjects, Function<Integer, T> supplier) {
        if (numObjects <= 0) throw new IllegalArgumentException("numObjects must be non-zero and positive.");
        objects = (T[]) Array.newInstance(typeParameterClass, numObjects);
        for (int i = 0; i < numObjects; i++) {
            objects[i] = supplier.apply(i);
        }
    }

    public synchronized T acquire() {
        T obj = objects[index];
        index = nextIndex();
        return obj;
    }

    public T get(int index) {
        return objects[index];
    }

    /// Closes the previous object at this position and replaces it with the new object.
    public synchronized void replace(int position, T newObject) {
        if (position < 0 || position >= objects.length) throw new IndexOutOfBoundsException();
        T oldObject = objects[position];
        objects[position] = newObject;
        if (oldObject != null) try { oldObject.close(); } catch (Exception _) {}
    }

    public int index() {
        return index;
    }

    public int nextIndex() {
        return (index + 1) % objects.length;
    }

    public int size() {
        return objects.length;
    }

    @Override
    public void close() throws Exception {
        for (T obj : objects) {
            obj.close();
        }
    }
}
