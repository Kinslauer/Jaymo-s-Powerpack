package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.HookBaitState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** (0.21.62) Gives a fishing hook's render state room for the bait on the hook. */
@Mixin(FishingHookRenderState.class)
public abstract class FishingHookRenderStateMixin implements HookBaitState {
    @Unique
    private final ItemStackRenderState jaymospowers$bait = new ItemStackRenderState();

    @Unique
    private final ItemStackRenderState jaymospowers$bead = new ItemStackRenderState();

    @Unique
    private float[] jaymospowers$line;

    @Override
    public float[] jaymospowers$line() {
        return this.jaymospowers$line;
    }

    @Override
    public void jaymospowers$setLine(float[] line) {
        this.jaymospowers$line = line;
    }

    @Override
    public ItemStackRenderState jaymospowers$bead() {
        return this.jaymospowers$bead;
    }

    @Override
    public ItemStackRenderState jaymospowers$bait() {
        return this.jaymospowers$bait;
    }
}
