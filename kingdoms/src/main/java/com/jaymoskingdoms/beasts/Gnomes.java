package com.jaymoskingdoms.beasts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The gnomes ({@link Gnome}): their entity, and the gnomes of the other mods' houses made ours. ATi Structures' gnome huts
 * and Grim Kingdoms' gnome towers (both All Rights Reserved; nothing of theirs is used) place zombies named "Gnome",
 * "Angry Gnome" or "the gnome". Each one that comes into the world, from a structure, its spawner or a saved chunk, is
 * turned into one of ours where it stood, keeping its shield, and that spot is its house (up to {@link #HOUSE_MAX} to a
 * house; past that the zombie is only taken away).
 * <p>
 * Here too is what wrongs a house besides a blow: breaking a block inside it, or opening one of its chests, barrels or
 * pots.
 */
public final class Gnomes {
	public static EntityType<Gnome> GNOME;
	/** The most gnomes a house holds: a gnome tower's spawner counts only zombies, so it would never stop making ours. */
	static final int HOUSE_MAX = 6;

	/** Structure gnomes taken out as they loaded, to be put back as ours at the end of the tick. */
	private record Swap(ServerLevel level, Vec3 pos, float yRot, boolean shield) {
	}

	private static final List<Swap> SWAPS = new ArrayList<>();

	private Gnomes() {
	}

	static void register() {
		GNOME = Beasts.type("gnome", EntityType.Builder.of(Gnome::new, MobCategory.CREATURE).sized(0.5F, 0.95F).eyeHeight(0.75F)
			.clientTrackingRange(8), Gnome.createAttributes());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getType() == EntityTypes.ZOMBIE && entity instanceof Zombie z && named(z)) {
				SWAPS.add(new Swap(level, z.position(), z.getYRot(), z.getMainHandItem().is(Items.SHIELD) || z.getOffhandItem().is(Items.SHIELD)));
				z.discard();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SWAPS.isEmpty()) {
				return;
			}
			for (Swap s : List.copyOf(SWAPS)) {
				if (Gnome.house(s.level(), s.pos()).size() >= HOUSE_MAX) {
					continue;
				}
				Gnome g = GNOME.create(s.level(), EntitySpawnReason.CONVERSION);
				if (g != null) {
					g.snapTo(s.pos().x, s.pos().y, s.pos().z, s.yRot(), 0.0F);
					g.settle(BlockPos.containing(s.pos()), s.shield());
					s.level().addFreshEntity(g);
				}
			}
			SWAPS.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SWAPS.clear());
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server) {
				trespass(server, player, pos);
			}
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && level.getBlockEntity(hit.getBlockPos()) instanceof Container) {
				trespass(level, player, hit.getBlockPos());
			}
			// (the chest opens as it always did)
			return InteractionResult.PASS;
		});
	}

	/** One of the structure mods' gnomes: a zombie whose name says so. */
	private static boolean named(Zombie z) {
		return z.getCustomName() != null && z.getCustomName().getString().toLowerCase(Locale.ROOT).contains("gnome");
	}

	/** {@code player} broke into or dug up {@code pos}: if it is some gnome's house, the house comes for them. */
	private static void trespass(ServerLevel level, Player player, BlockPos pos) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		for (Gnome g : Gnome.house(level, Vec3.atCenterOf(pos))) {
			if (g.keeps(pos)) {
				g.rouse(level, player);
				return;
			}
		}
	}
}
