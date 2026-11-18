package com.jaymoskingdoms.beasts;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jspecify.annotations.Nullable;

/**
 * The labyrinth-builders' compass, built from the geared plates of one of their instruments (found in ocean ruin and
 * shipwreck chests, {@link LabyrinthCraft#GEARED_PLATE}): a bronze compass that turns toward the nearest labyrinth
 * ({@link Labyrinths#ALWAYS}, {@link Labyrinths#SOME}) of the world it is in. It is the game's own lodestone compass
 * (the tracker names a place and the compass needle follows it), looked up again {@link #EVERY} ticks while held in a
 * hand; where there is no labyrinth within {@link #REACH} chunks the needle spins.
 */
public class LabyrinthCompass extends Item {
	/** How often a held compass looks again (ticks), and how far out it looks (chunks). */
	static final int EVERY = 100, REACH = 64;

	public LabyrinthCompass(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		if ((slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) || (owner.tickCount + owner.getId()) % EVERY != 0) {
			return;
		}
		BlockPos near = nearest(level, owner.blockPosition());
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(near == null ? Optional.empty() : Optional.of(new GlobalPos(level.dimension(), near)),
			false));
	}

	/** The nearer of the nearest great labyrinth and the nearest middling one, or null. */
	private static @Nullable BlockPos nearest(ServerLevel level, BlockPos from) {
		BlockPos great = level.findNearestMapStructure(Labyrinths.ALWAYS, from, REACH, false);
		BlockPos some = level.findNearestMapStructure(Labyrinths.SOME, from, REACH, false);
		if (great == null || some == null) {
			return great != null ? great : some;
		}
		return great.distSqr(from) <= some.distSqr(from) ? great : some;
	}
}
