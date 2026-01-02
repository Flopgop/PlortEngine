package net.flamgop.borked;

import java.util.ArrayList;
import java.util.List;

public class World implements AutoCloseable {
    protected final List<Entity> entities = new ArrayList<>();

    public void update(float dt) {
        entities.forEach(e -> e.update(dt));
    }

    @Override
    public void close() {
        entities.forEach(Entity::close);
    }
}
