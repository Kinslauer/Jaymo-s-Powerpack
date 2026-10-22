package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (BWM batch 5) A beacon's watch-light, after Better With Mods' beacon effects (MIT; its idea, our own code). A vanilla
 * beacon keeps its screen and its powers; on top of them, a pyramid built all of one block keeps watch over the
 * beacon's own range (the game's: 10 blocks a level plus 10, up to the sky):
 * <ul>
 * <li>all iron blocks: hostile mobs in range glow, seen through walls, for as long as they stay;</li>
 * <li>all emerald blocks: hostile mobs killed in range drop loot as if their World Tier were a step higher
 * ({@link WorldTiers}' doubled stacks, {@link #lootStep}).</li>
 * </ul>
 * Gold, diamond and netherite pyramids (and mixed ones) do nothing more. The beacon calls {@link #pulse} itself every
 * 80 ticks (mixin.beacons.BeaconWatchMixin), so nothing scans for beacons.
 */
public final class BeaconWatch {
	/** How long a pulse's Glowing lasts (ticks): longer than the beacon's 80-tick beat, so it doesn't flicker. */
	static final int GLOW_TICKS = 200;
	/** How long after a pulse an emerald beacon's mark on a mob holds (ticks). */
	static final int MARK_TICKS = 100;
	/** How many World Tier steps an emerald beacon's mark adds to a kill's loot. */
	static final int LOOT_STEPS = 1;
	/** Each level's range (blocks) and the base, as the game's own beacon. */
	static final int RANGE_PER_LEVEL = 10, RANGE_BASE = 10;

	/** Till when (game time) a mob is in an emerald beacon's watch. Not saved. */
	static final AttachmentType<Long> EMERALD_WATCH = AttachmentRegistry.create(JaymosKingdoms.id("beacon_emerald_watch"));

	private BeaconWatch() {
	}

	/** Loads the attachment. */
	static void register() {
		JaymosKingdoms.LOGGER.debug("beacon watch attachment: {}", EMERALD_WATCH);
	}

	/** A beacon at {@code pos} with a pyramid {@code levels} high pulses. */
	public static void pulse(ServerLevel level, BlockPos pos, int levels) {
		Block base = pyramid(level, pos, levels);
		if (base != Blocks.IRON_BLOCK && base != Blocks.EMERALD_BLOCK) {
			return;
		}
		double range = levels * RANGE_PER_LEVEL + RANGE_BASE;
		AABB box = new AABB(pos).inflate(range).expandTowards(0.0, level.getHeight(), 0.0);
		long until = level.getGameTime() + MARK_TICKS;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && WorldTiers.hostile(m))) {
			if (base == Blocks.IRON_BLOCK) {
				mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false));
			} else {
				mob.setAttached(EMERALD_WATCH, until);
			}
		}
	}

	/** The one block every layer of the pyramid under {@code pos} is made of, or null if it's mixed. */
	static @Nullable Block pyramid(ServerLevel level, BlockPos pos, int levels) {
		Block base = null;
		for (int i = 1; i <= levels; i++) {
			int y = pos.getY() - i;
			for (int x = pos.getX() - i; x <= pos.getX() + i; x++) {
				for (int z = pos.getZ() - i; z <= pos.getZ() + i; z++) {
					Block block = level.getBlockState(new BlockPos(x, y, z)).getBlock();
					if (base == null) {
						base = block;
					} else if (block != base) {
						return null;
					}
				}
			}
		}
		return base;
	}

	/** World Tier steps {@code entity}'s loot rises by: an emerald beacon's watch. */
	static int lootStep(Entity entity) {
		Long until = entity.getAttached(EMERALD_WATCH);
		return until != null && until >= entity.level().getGameTime() ? LOOT_STEPS : 0;
	}
}
