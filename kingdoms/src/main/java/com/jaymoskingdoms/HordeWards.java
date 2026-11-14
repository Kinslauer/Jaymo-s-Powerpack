package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bell wards (Andy's pick from Harder Hordes, 2026-11-14: "build it into temples yes, that will be how players learn of
 * its function and from villagers"). A bell standing on a three-by-three of metal blocks keeps hordes
 * ({@link ZombieHordes}) from coming up anywhere near it. The metal sets how far: iron {@link #RADII}[1], gold, diamond,
 * netherite the furthest; a mixed platform counts as its weakest block, except that a netherite block in the middle of an
 * otherwise diamond one is enough for the netherite's reach. Nothing is crafted and nothing is told: The Truth's great
 * temples ({@link GreatTemple}) ring theirs from a square of iron in the bell tower, and villagers near a bell talk of it
 * now and then ({@link #tick}), in speech bubbles, never in chat.
 */
public final class HordeWards {
	/** By the platform's metal (none, iron, gold, diamond, netherite): how far no horde comes up (blocks). */
	static final int[] RADII = {0, 40, 56, 80, 112};
	/** Villagers' talk of the bells: how often players are looked at (ticks), how near a villager and a bell must be, how long before the same player hears it again. */
	static final int TALK_EVERY = 200;
	static final double TALK_NEAR = 10.0;
	static final int BELL_NEAR = 48;
	static final long TALK_AGAIN = 20L * 60 * 20;
	/** Lines a villager has about the bells. */
	static final int LINES = 5;

	/** Player → when they last heard a villager talk of the bells. */
	private static final Map<UUID, Long> HEARD = new HashMap<>();

	private HordeWards() {
	}

	/** The metal's grade under {@code state}'s block: 0 none, 1 iron, 2 gold, 3 diamond, 4 netherite. */
	private static int metal(BlockState state) {
		if (state.is(Blocks.NETHERITE_BLOCK)) {
			return 4;
		}
		if (state.is(Blocks.DIAMOND_BLOCK)) {
			return 3;
		}
		if (state.is(Blocks.GOLD_BLOCK)) {
			return 2;
		}
		return state.is(Blocks.IRON_BLOCK) ? 1 : 0;
	}

	/** How far the bell at {@code bell} wards (0 when it isn't a bell on a full metal platform). */
	static int radius(ServerLevel level, BlockPos bell) {
		if (!level.getBlockState(bell).is(Blocks.BELL)) {
			return 0;
		}
		BlockPos under = bell.below();
		int weakest = 4, rest = 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int m = metal(level.getBlockState(under.offset(dx, 0, dz)));
				weakest = Math.min(weakest, m);
				if (dx != 0 || dz != 0) {
					rest = Math.min(rest, m);
				}
			}
		}
		// a netherite heart in a diamond platform wards as far as netherite
		int grade = rest >= 3 && metal(level.getBlockState(under)) == 4 ? 4 : weakest;
		return RADII[grade];
	}

	/** Whether a warded bell keeps the hordes from {@code at}. */
	static boolean warded(ServerLevel level, BlockPos at) {
		int reach = RADII[RADII.length - 1];
		return level.getPoiManager().findAll(type -> type.is(PoiTypes.MEETING), p -> true, at, reach, PoiManager.Occupancy.ANY)
			.anyMatch(bell -> {
				int r = radius(level, bell);
				return r > 0 && bell.distSqr(at) <= (double) r * r;
			});
	}

	/** Now and then a villager by a bell talks of what the bells do, to a player near. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % TALK_EVERY != 0 || !ZombieHordes.begun(server)) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		HEARD.values().removeIf(t -> now - t > TALK_AGAIN * 4);
		for (ServerPlayer player : level.players()) {
			Long last = HEARD.get(player.getUUID());
			if (player.isSpectator() || last != null && now - last < TALK_AGAIN) {
				continue;
			}
			boolean bell = level.getPoiManager().findClosest(type -> type.is(PoiTypes.MEETING), player.blockPosition(), BELL_NEAR,
				PoiManager.Occupancy.ANY).isPresent();
			if (!bell) {
				continue;
			}
			AbstractVillager speaker = null;
			for (AbstractVillager v : level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(TALK_NEAR))) {
				if (v.isAlive() && !v.isSleeping() && !v.isBaby() && v.hasLineOfSight(player)) {
					speaker = v;
					break;
				}
			}
			if (speaker == null) {
				continue;
			}
			HEARD.put(player.getUUID(), now);
			Component words = Component.translatable("talk.jaymoskingdoms.ward_bell." + level.getRandom().nextInt(LINES));
			Speech.say(level, speaker, words, Speech.WHITE, TALK_NEAR + 2.0);
		}
	}
}
