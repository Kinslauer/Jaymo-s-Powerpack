package com.jaymospowers.tinkering.dangers;

import com.jaymospowers.tinkering.smithing.Smithing;
import java.util.Set;
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
 * (GTNH picks, the forge's dangers) Jaymo's Kitchen's Cooking Pot and Frying Pan, taken straight off the fire: one
 * broken while it sits heated ({@code jaymoskitchen:cooking_pot} or {@code frying_pan} with {@code heated=true}) drops hot, carrying the hot metal mark
 * ({@link Smithing#HEATED_AT}), so it burns bare hands until it cools as a hot ingot does ({@code HotMetal}). Only with
 * Kitchen loaded; it's found by id, so nothing here touches Kitchen's classes.
 */
final class HotPots {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");
	/** The heated cookware (block and item share the id). */
	private static final Set<Identifier> POTS = Set.of(Identifier.fromNamespaceAndPath("jaymoskitchen", "cooking_pot"),
		Identifier.fromNamespaceAndPath("jaymoskitchen", "frying_pan"));
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
				Identifier pot = BuiltInRegistries.BLOCK.getKey(state.getBlock());
				for (ItemEntity drop : server.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(DROP_REACH))) {
					if (BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).equals(pot) && !drop.getItem().has(Smithing.HEATED_AT)) {
						ItemStack hot = drop.getItem().copy();
						hot.set(Smithing.HEATED_AT, now);
						drop.setItem(hot);
					}
				}
			}
		});
	}

	private static boolean heated(BlockState state) {
		if (!POTS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
			return false;
		}
		Property<?> heated = state.getBlock().getStateDefinition().getProperty("heated");
		return heated instanceof BooleanProperty flag && state.getValue(flag);
	}
}
