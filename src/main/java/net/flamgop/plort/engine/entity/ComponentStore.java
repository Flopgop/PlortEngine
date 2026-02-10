package net.flamgop.plort.engine.entity;

import java.util.Collection;

public interface ComponentStore<T> {
    void add(int entity, T component);
    T get(int entity);
    void remove(int entity);
    boolean has(int entity);
    int size();
    Collection<Integer> entities();
    Collection<T> components();
}
