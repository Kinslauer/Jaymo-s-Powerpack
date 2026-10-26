package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Flux;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * What flux does to a person, once a second for every player on the server:
 * <ul>
 * <li>Standing in a chunk with {@link FluxEffects#CORRUPT_AT} flux or more gives fleeting corruption, more the more flux
 * ({@link #SOAK_STEP}); it stops at {@link #FLEETING_CAP}.</li>
 * <li>Fleeting fades one point every {@link #FLEETING_FADE} seconds, lingering one every {@link #LINGERING_FADE} seconds;
 * deep never fades (see {@link Cures}). Neither fades while the player stands in flux.</li>
 * <li>By total corruption there are four stages ({@link #STAGES}). Each time a stage is first reached a vague line is shown.
 * Stages 1 and 2 (whispers, sights) are drawn on the client alone (see CorruptionClient); from stage 3 there are now and
 * then short Nausea or Darkness pulses; at stage 4 a Flux Wisp hunts the player at night.</li>
 * </ul>
 */
public final class FluxCorruption {
	// ---- tuning ----
	/** Total corruption at which stage 1 (whispers), 2 (sights), 3 (unease) and 4 (the hunt) begin. */
	public static final int[] STAGES = {15, 35, 60, 100};
	/** Fleeting corruption per second is 1 + flux / this. */
	public static final int SOAK_STEP = 400;
	/** Fleeting corruption stops growing here. */
	public static final int FLEETING_CAP = 120;
	/** Seconds per point faded. */
	public static final int FLEETING_FADE = 4, LINGERING_FADE = 120;
	/** Per second at stage 3+: the chance (1 in this) of a Nausea or Darkness pulse, and its length in ticks. */
	public static final int UNEASE_ODDS = 40, UNEASE_TICKS = 100;
	/** Per second at stage 4, at night: the chance (1 in this) that a wisp is sent (halved on a Blood Moon); the ring it appears on. */
	public static final int HUNT_ODDS = 40;
	public static final double HUNT_MIN = 9.0, HUNT_MAX = 14.0, HUNT_SPACING = 48.0;

	private static final Map<UUID, Integer> STAGE = new HashMap<>();

	private FluxCorruption() {
	}

	static void forget(ServerPlayer player) {
		STAGE.remove(player.getUUID());
	}

	/** 0 for none, 1..4 by {@link #STAGES}. */
	public static int stage(int total) {
		int stage = 0;
		for (int threshold : STAGES) {
			if (total >= threshold) {
				stage++;
			}
		}
		return stage;
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long seconds = server.getTickCount() / 20;
		boolean blood = false, bloodKnown = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			boolean soaked = false;
			int flux = Flux.get(level, player.blockPosition());
			Corruption.State state = Corruption.of(player);
			if (flux >= FluxEffects.CORRUPT_AT && !player.isCreative()) {
				soaked = true;
				if (state.fleeting() < FLEETING_CAP) {
					Corruption.add(player, Corruption.Kind.FLEETING, 1 + flux / SOAK_STEP);
				}
			}
			if (!soaked) {
				if (state.fleeting() > 0 && seconds % FLEETING_FADE == 0) {
					Corruption.add(player, Corruption.Kind.FLEETING, -1);
				}
				if (state.lingering() > 0 && seconds % LINGERING_FADE == 0) {
					Corruption.add(player, Corruption.Kind.LINGERING, -1);
				}
			}
			int total = Corruption.total(player);
			int stage = stage(total);
			Integer before = STAGE.put(player.getUUID(), stage);
			if (before != null && stage > before) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.corruption.stage" + stage), true);
			}
			RandomSource random = player.getRandom();
			if (stage >= 3 && random.nextInt(UNEASE_ODDS) == 0) {
				player.addEffect(new MobEffectInstance(random.nextBoolean() ? MobEffects.NAUSEA : MobEffects.DARKNESS, UNEASE_TICKS, 0, true, false));
			}
			if (stage >= 4 && level.isDarkOutside()) {
				if (!bloodKnown) {
					blood = BloodMoon.active(server);
					bloodKnown = true;
				}
				if (random.nextInt(blood ? HUNT_ODDS / 2 : HUNT_ODDS) == 0) {
					hunt(level, player, random);
				}
			}
		}
	}

	/** Sends a wisp at a player, from a ring around them, unless one already hunts nearby. */
	private static void hunt(ServerLevel level, ServerPlayer player, RandomSource random) {
		AABB near = player.getBoundingBox().inflate(HUNT_SPACING);
		if (!level.getEntitiesOfClass(FluxWisp.class, near).isEmpty()) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = HUNT_MIN + random.nextDouble() * (HUNT_MAX - HUNT_MIN);
		FluxWisp wisp = FluxEffects.WISP.create(level, EntitySpawnReason.EVENT);
		if (wisp != null) {
			wisp.snapTo(player.getX() + Math.cos(angle) * distance, player.getY() + 1.0 + random.nextDouble() * 2.0,
				player.getZ() + Math.sin(angle) * distance, random.nextFloat() * 360.0F, 0.0F);
			wisp.setTarget(player);
			level.addFreshEntity(wisp);
		}
	}
}
