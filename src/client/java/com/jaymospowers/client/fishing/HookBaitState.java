package com.jaymospowers.client.fishing;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** (0.21.62) The bait drawn under a fishing hook, carried on its render state ({@link com.jaymospowers.client.fishing.mixin.FishingHookRenderStateMixin}). */
public interface HookBaitState {
    ItemStackRenderState jaymospowers$bait();

    /** The dye bead fitted as a float, drawn over the bobber. */
    ItemStackRenderState jaymospowers$bead();

    /** (F6) The hanging line's points ({@link HangingLine#points}), or null to draw vanilla's. */
    float @org.jspecify.annotations.Nullable [] jaymospowers$line();

    void jaymospowers$setLine(float @org.jspecify.annotations.Nullable [] line);
}
