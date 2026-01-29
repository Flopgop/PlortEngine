package net.flamgop.borked.entity;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityManager {

    private final AtomicInteger idCounter = new AtomicInteger();
    private final IntArrayList freeIds = new IntArrayList();
    private final Map<Class<?>, ComponentStore<?>> stores = new HashMap<>();

    public EntityManager() {}

    @SuppressWarnings("unchecked")
    public <T> ComponentStore<T> store(Class<T> type) {
        return (ComponentStore<T>) stores.computeIfAbsent(
                type, _ -> new SparseSetStore<>()
        );
    }

    public void destroyEntity(int entity) {
        for (ComponentStore<?> store : stores.values()) {
            store.remove(entity);
        }
    }

    public int createEntity() {
        if (!freeIds.isEmpty()) {
            return freeIds.popInt();
        }
        return idCounter.getAndIncrement();
    }
}
