package net.flamgop.plort.engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

@SuppressWarnings("UnusedReturnValue")
public class GameState {
    public static final Codec<GameState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("render_debug", true).forGetter(GameState::renderDebug)
            ).apply(instance, GameState::new)
    );

    private boolean renderDebug = true;
    public GameState renderDebug(boolean renderDebug) { this.renderDebug = renderDebug; return this; }
    public boolean renderDebug() { return renderDebug; }

    public GameState() {}

    public GameState(boolean renderDebug) {
        this.renderDebug = renderDebug;
    }
}
