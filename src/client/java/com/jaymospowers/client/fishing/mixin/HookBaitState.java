package com.jaymospowers.client.fishing.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** (0.21.61) The bait drawn under a fishing hook, carried on its render state ({@link FishingHookRenderStateMixin}). */
public interface HookBaitState {
    ItemStackRenderState jaymospowers$bait();
}
