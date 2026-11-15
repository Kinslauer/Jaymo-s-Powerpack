package com.jaymospowers.tinkering.dangers;

import com.jaymospowers.tinkering.smithing.Smithing;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

/**
 * (GTNH picks, the forge's dangers) Jaymo's Kitchen's Cooking Pot, taken straight off the fire: a pot broken while it
 * sits heated ({@code jaymoskitchen:cooking_pot} with {@code heated=true}) drops hot, carrying the hot metal mark
 * ({@link Smithing#HEATED_AT}), so it burns bare hands until it cools as a hot ingot does ({@code HotMetal}). Only with
 * Kitchen loaded; it's found by id, so nothing here touches Kitchen's classes.
 */
final class HotPots {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");
	private static final Identifier POT = Identifier.fromNamespaceAndPath("jaymoskitchen", "cooking_pot");
	/** How far from the pot's block its drop is looked for (blocks). */
	static final double DROP_REACH = 0.5;

	private HotPots() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && heated(state)) {
				long now = server.getGameTime();
				for (ItemEntity drop : server.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(DROP_REACH))) {
					if (BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).equals(POT) && !drop.getItem().has(Smithing.HEATED_AT)) {
						ItemStack hot = drop.getItem().copy();
						hot.set(Smithing.HEATED_AT, now);
						drop.setItem(hot);
					}
				}
			}
		});
	}

	private static boolean heated(BlockState state) {
		if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(POT)) {
			return false;
		}
		Property<?> heated = state.getBlock().getStateDefinition().getProperty("heated");
		return heated instanceof BooleanProperty flag && state.getValue(flag);
	}
}
