package com.jaymospowers.core;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;

/**
 * (0.11.26) A lantern in the offhand is for light (LambDynamicLights lights the way around you), not for building: a
 * right click that the main hand had no use for fell through to the offhand and set the lantern down. Lanterns
 * (vanilla's {@code minecraft:lanterns} tag: plain, soul and every copper one) are now only placed from the main hand.
 * The main hand's own clicks, opening a door or a chest included, are untouched; they come first.
 */
public final class OffhandLanterns {
	static final TagKey<Item> LANTERNS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", "lanterns"));

	private OffhandLanterns() {
	}

	public static void init() {
		// both sides run it, so the client doesn't place a ghost lantern either
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
			hand == InteractionHand.OFF_HAND && player.getOffhandItem().is(LANTERNS) ? InteractionResult.FAIL : InteractionResult.PASS);
	}
}
