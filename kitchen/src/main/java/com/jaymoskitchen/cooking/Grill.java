package com.jaymoskitchen.cooking;

import com.jaymoskitchen.net.Payloads.GrillUpdate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The campfire grill minigame, server side. The side facing the fire cooks; the player flips and serves.
 * Doneness is 1.0 when a side is cooked through; a side gains (heat / {@link Dish#grillTicks()}) per tick.
 * The fire's heat is random: it changes on every flip, a soul campfire runs hotter, and in the furnace oven the
 * heat surges and dips on its own. The client is told the heat so it can show it (flame height) and predict.
 * The furnace pizza oven runs on the same clock with a single side and no flipping.
 */
public final class Grill {
	/** Below this a side is still raw. */
	public static final float COOKED = 0.6F;
	/** Both sides at least this (and not burnt) is Perfect. */
	public static final float GOLDEN = 0.85F;
	/** Above this a side is burnt. */
	public static final float BURNT = 1.0F;
	/** A side this far gone ends the cook: it's charcoal. */
	public static final float ASH = 1.6F;
	/** In the oven there's only one "side", so this far past burnt counts as charred. */
	public static final float OVEN_CHARRED = 1.3F;
	/** Flip animation length; flips faster than this are ignored. */
	public static final int FLIP_TICKS = 6;
	/** Soul fire burns hotter. */
	public static final float SOUL_HEAT = 1.3F;

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/** The dishes whose sessions here are running at {@code pos} in {@code level} (for the oven's cookware locks). */
	static void dishesAt(net.minecraft.server.level.ServerLevel level, BlockPos pos, java.util.List<Dish> out) {
		for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
			if (entry.getValue().pos.equals(pos)) {
				net.minecraft.server.level.ServerPlayer cook = level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (cook != null && cook.level() == level) {
					out.add(entry.getValue().dish);
				}
			}
		}
	}

	private Grill() {
	}

	private static final class Session {
		final Dish dish;
		final BlockPos pos;
		final Station station;
		final float baseHeat;
		/** Doneness of each side, not counting the current stretch. */
		final float[] done = new float[2];
		/** Which side (0 or 1) is on the fire. */
		int down;
		/** Tick the current stretch (constant heat, same side down) began. */
		int since;
		float heat;
		/** Oven only: when the heat next changes by itself. */
		int nextSurge;

		Session(Dish dish, BlockPos pos, Station station, float baseHeat, int now) {
			this.dish = dish;
			this.pos = pos;
			this.station = station;
			this.baseHeat = baseHeat;
			this.since = now;
		}

		float perTick() {
			return heat / dish.grillTicks();
		}

		float doneness(int side, int now) {
			return done[side] + (side == down ? perTick() * (now - since) : 0.0F);
		}

		/** Closes the current stretch so the heat or side can change from {@code now}. */
		void settle(int now) {
			done[down] = doneness(down, now);
			since = now;
		}

		boolean oven() {
			return dish.method() == Dish.Method.OVEN;
		}
	}

	public static boolean isGrilling(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	private static float rollHeat(Session session, RandomSource random) {
		float swing = session.oven() ? 0.7F + random.nextFloat() * 0.7F : 0.8F + random.nextFloat() * 0.5F;
		return session.baseHeat * swing;
	}

	private static int rollSurge(int now, RandomSource random) {
		return now + 30 + random.nextInt(50);
	}

	static void start(ServerPlayer player, Dish dish, BlockPos pos, Station station) {
		int now = player.level().getServer().getTickCount();
		float baseHeat = player.level().getBlockState(pos).is(Blocks.SOUL_CAMPFIRE) ? SOUL_HEAT : 1.0F;
		Session session = new Session(dish, pos.immutable(), station, baseHeat, now);
		session.heat = rollHeat(session, player.getRandom());
		session.nextSurge = rollSurge(now, player.getRandom());
		SESSIONS.put(player.getUUID(), session);
		player.level().playSound(null, pos,
			session.oven() ? SoundEvents.FURNACE_FIRE_CRACKLE : SoundEvents.CAMPFIRE_CRACKLE,
			SoundSource.BLOCKS, 1.0F, 1.0F);
		send(player, session, now, GrillUpdate.STATUS_COOKING, 0);
	}

	public static void flip(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || session.oven()) {
			return;
		}
		int now = player.level().getServer().getTickCount();
		if (now - session.since < FLIP_TICKS) {
			return;
		}
		session.settle(now);
		session.down ^= 1;
		// the coals shift: the new side gets a different heat
		session.heat = rollHeat(session, player.getRandom());
		player.level().playSound(null, session.pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.8F);
		send(player, session, now, GrillUpdate.STATUS_COOKING, 0);
	}

	/** Take it off the fire. Closing the screen does the same. */
	public static void serve(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session != null) {
			finish(player, session);
		}
	}

	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			Session session = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
			} else if (session.doneness(session.down, now) >= ASH
				|| !session.station.cooks(player.level(), session.pos, session.dish)) {
				// burnt to nothing, or the fire went out / the station was broken: it's over
				it.remove();
				finish(player, session);
			} else if (session.oven() && now >= session.nextSurge) {
				// the oven's fire surges and dies back on its own
				session.settle(now);
				session.heat = rollHeat(session, player.getRandom());
				session.nextSurge = rollSurge(now, player.getRandom());
				send(player, session, now, GrillUpdate.STATUS_COOKING, 0);
			}
		}
	}

	private static void finish(ServerPlayer player, Session session) {
		int now = player.level().getServer().getTickCount();
		Quality quality = session.oven()
			? Quality.judgeOven(session.doneness(0, now))
			: Quality.judgeGrill(session.doneness(0, now), session.doneness(1, now));
		Kitchen.award(player, session.dish, session.pos, quality);
		send(player, session, now, GrillUpdate.STATUS_FINISHED, quality.ordinal());
	}

	private static void send(ServerPlayer player, Session session, int now, int status, int quality) {
		ServerPlayNetworking.send(player, new GrillUpdate(session.dish.ordinal(), session.doneness(0, now),
			session.doneness(1, now), session.down, session.perTick(), session.heat, status, quality));
	}
}
