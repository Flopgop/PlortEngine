package net.flamgop.borked.entity;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SparseSetStore<T> implements ComponentStore<T> {
    private final Int2IntMap sparse = new Int2IntOpenHashMap();
    private final IntArrayList denseEntities = new IntArrayList();
    private final ArrayList<T> denseComponents = new ArrayList<>();

    @Override
    public void add(int entity, T component) {
        int idx = denseEntities.size();
        sparse.put(entity, idx);
        denseEntities.add(entity);
        denseComponents.add(component);
    }

    @Override
    public T get(int entity) {
        return denseComponents.get(sparse.get(entity));
    }

    @Override
    public void remove(int entity) {
        if (!sparse.containsKey(entity)) return;
        int index = sparse.get(entity);
        int lastIndex = denseEntities.size() - 1;

        if (index != lastIndex) {
            int lastEntity = denseEntities.getInt(lastIndex);
            T lastComponent = denseComponents.get(lastIndex);

            denseEntities.set(index, lastEntity);
            denseComponents.set(index, lastComponent);
            sparse.put(lastEntity, index);
        }

        denseComponents.remove(sparse.get(entity));
        denseEntities.removeInt(sparse.get(entity));
        sparse.remove(entity);
    }

    @Override
    public boolean has(int entity) {
        return sparse.containsKey(entity);
    }

    @Override
    public int size() {
        return denseEntities.size();
    }

    @Override
    public Collection<Integer> entities() {
        return Collections.unmodifiableCollection(denseEntities);
    }

    @Override
    public Collection<T> components() {
        return Collections.unmodifiableList(denseComponents);
    }
}
