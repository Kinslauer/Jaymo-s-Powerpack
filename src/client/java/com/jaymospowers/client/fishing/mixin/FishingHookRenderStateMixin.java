package com.jaymospowers.client.fishing.mixin;

import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** (0.21.62) Gives a fishing hook's render state room for the bait on the hook. */
@Mixin(FishingHookRenderState.class)
public abstract class FishingHookRenderStateMixin implements HookBaitState {
    @Unique
    private final ItemStackRenderState jaymospowers$bait = new ItemStackRenderState();

    @Override
    public ItemStackRenderState jaymospowers$bait() {
        return this.jaymospowers$bait;
    }
}
