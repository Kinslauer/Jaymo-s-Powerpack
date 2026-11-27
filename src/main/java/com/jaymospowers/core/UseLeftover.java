package com.jaymospowers.core;

import net.minecraft.world.item.ItemStack;

/**
 * What finishing an item's use left (the bowl of the last stew or tea, the bottle of the last potion), remembered on the
 * entity so it reaches the hand. CreativeCore (2.14.22, in the pack) wraps the {@code finishUsingItem} call in
 * {@code LivingEntity.completeUsingItem} and throws its result away, so the last bowl or bottle of a stack vanished
 * (Andy, 2026-11-24: Chamomile Tea). See {@code UseLeftoverHandMixin}.
 */
public interface UseLeftover {
	void jaymospowers$useLeftover(ItemStack left);
}
