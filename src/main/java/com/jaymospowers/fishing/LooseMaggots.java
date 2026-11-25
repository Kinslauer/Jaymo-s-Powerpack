package com.jaymospowers.fishing;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andy: "you should be able to pickup maggots as an item for bait". Maggots for the hook keep in your pack as long as you
 * like (an angler's bait tin), but a handful left lying on the ground doesn't wait to be picked up again: after
 * {@link #BURROW_TICKS} the maggots wriggle off into the earth (or drown, in water) and are gone, as real ones crawl away
 * from the light to bury themselves. Picked off a body (Jaymo's Kingdoms' maggot, a tap of use) or bought, crafted, or
 * dropped from a crate, they're all the same {@link Tackle#MAGGOTS}.
 */
public final class LooseMaggots {
	/** How long (ticks) dropped maggots lie before they burrow away; how often (ticks) the loose ones are looked at. */
	static final int BURROW_TICKS = 1200, EVERY = 20;

	private static final Set<ItemEntity> LOOSE = Collections.newSetFromMap(new WeakHashMap<>());

	private LooseMaggots() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getItem().is(Tackle.MAGGOTS)) {
				LOOSE.add(item);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(LooseMaggots::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LOOSE.clear());
	}

	private static void tick(MinecraftServer server) {
		if (LOOSE.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (var it = LOOSE.iterator(); it.hasNext();) {
			ItemEntity item = it.next();
			if (item.isRemoved() || !item.getItem().is(Tackle.MAGGOTS)) {
				it.remove();
			} else if (item.getAge() >= BURROW_TICKS && (item.onGround() || item.isInWater()) && item.level() instanceof ServerLevel level) {
				it.remove();
				burrow(level, item);
			}
		}
	}

	/** They're gone: into the ground below (a little of it kicked up), or drowned. */
	private static void burrow(ServerLevel level, ItemEntity item) {
		if (item.isInWater()) {
			level.sendParticles(ParticleTypes.BUBBLE, item.getX(), item.getY() + 0.1, item.getZ(), 3, 0.08, 0.05, 0.08, 0.02);
		} else {
			BlockPos below = BlockPos.containing(item.getX(), item.getY() - 0.2, item.getZ());
			BlockState ground = level.getBlockState(below);
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), item.getX(), item.getY() + 0.05, item.getZ(), 4, 0.1, 0.02, 0.1, 0.05);
				level.playSound(null, item.getX(), item.getY(), item.getZ(), ground.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.3F, 1.5F);
			}
		}
		item.discard();
	}
}
