package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Vector2i {
    public static final long BYTES = 2 * Integer.BYTES;
    private static final ValueLayout.OfInt I32 = ValueLayout.JAVA_INT;

    private final MemorySegment memory;

    public Vector2i(Arena arena) {
        this.memory = arena.allocate(BYTES);
    }

    public Vector2i(Arena arena, int x, int y) {
        this(arena);
        this.x(x);
        this.y(y);
    }

    public Vector2i(int x, int y) {
        this(Arena.ofAuto(), x, y);
    }

    @ApiStatus.Internal // note: not bounds checked
    public int getUnsafe(int index) {
        return memory.get(I32, (long) index * Integer.BYTES);
    }

    public int get(int index) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal
    public void setUnsafe(int index, int value) {
        memory.set(I32, (long) index * Integer.BYTES, value);
    }

    public void set(int index, int value) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
    }

    public void x(int value) {
        setUnsafe(0, value);
    }

    public void y(int value) {
        setUnsafe(1, value);
    }

    public int x() {
        return getUnsafe(0);
    }

    public int y() {
        return getUnsafe(1);
    }
}
