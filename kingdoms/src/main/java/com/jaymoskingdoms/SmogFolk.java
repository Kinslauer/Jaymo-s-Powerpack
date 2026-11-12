package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Folk and a Smog user (Jaymo's Powers' smoke corruption, which tags the player {@link #SMOG_TAG} while it holds them).
 * Andy (2026-11-12): "they should react, and repeat interactions should drive you out of the town. most people will come
 * to realize the effects from corruption and the bad karma you must harbor to have gotten there."
 * <p>
 * A villager in sight within {@link #RANGE} remarks on the reek, once every {@link #COOLDOWN} ticks a town, and thinks
 * less of you (an MCA heart). Each remark in a town counts: the first few wonder at the ash and the cough
 * ({@code curious}), then they know it for corruption and say what it says of you ({@code knowing}), and at
 * {@link #DRIVE_OUT} the town drives you out for {@link #BAN_TICKS}: a realm with a ruler sends its guards to see you
 * out ({@link Retinue#exile}); a town without one shouts you off, and if you're still there after {@link #GRACE} ticks
 * its nearest guard comes at you. The count fades by one each {@link #FORGET_TICKS}, so a cured user is let back in.
 */
public final class SmogFolk {
	/** Powers' tag on a player whom the smog holds. */
	static final String SMOG_TAG = "jaymospowers.smog";
	static final double RANGE = 8.0;
	static final int CHECK_TICKS = 100, COOLDOWN = 1200;
	/** Remarks in one town before it knows the corruption for what it is, and before it drives you out. */
	static final int KNOWING = 2, DRIVE_OUT = 5;
	static final long BAN_TICKS = 12000, GRACE = 600, FORGET_TICKS = 24000;
	static final int LINES = 4;
	/** How far a rulerless town's guard is called from to come at a user who won't leave. */
	static final double GUARD_RANGE = 32.0;

	private static final class Seen {
		int count;
		long last = Long.MIN_VALUE / 2, bannedUntil, shouted;
	}

	/** Per town and player (key: faction|player). */
	private static final Map<String, Seen> SEEN = new HashMap<>();

	private SmogFolk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SmogFolk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEEN.clear());
	}

	private static String key(int faction, UUID player) {
		return faction + "|" + player;
	}

	/** Whether {@code player} is barred from {@code f}'s town for the smog on them. */
	static boolean banned(Faction f, ServerPlayer player, long now) {
		Seen s = SEEN.get(key(f.id(), player.getUUID()));
		return s != null && s.bannedUntil > now;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			ServerLevel level = player.level();
			Faction here = Theft.lawAt(level, player.blockPosition());
			if (here == null) {
				continue;
			}
			Seen s = SEEN.get(key(here.id(), player.getUUID()));
			if (s != null && s.count > 0 && now - s.last >= FORGET_TICKS) {
				// time (and a cure) lets a town forget
				s.count = Math.max(0, s.count - (int)((now - s.last) / FORGET_TICKS));
				s.last = now;
			}
			if (s != null && s.bannedUntil > now) {
				// back while barred: smog or no smog, they said what they'd do
				enforce(level, here, player, s, now);
				continue;
			}
			if (!player.entityTags().contains(SMOG_TAG) || player.isInvisible()) {
				continue;
			}
			if (s != null && now - s.last < COOLDOWN) {
				continue;
			}
			Villager speaker = nearest(level, player);
			if (speaker == null) {
				continue;
			}
			if (s == null) {
				s = new Seen();
				SEEN.put(key(here.id(), player.getUUID()), s);
			}
			s.count++;
			s.last = now;
			McaCompat.hearts(speaker, player, -1);
			if (s.count >= DRIVE_OUT) {
				driveOut(level, here, player, speaker, s, now);
			} else {
				String stage = s.count > KNOWING ? "knowing" : "curious";
				Speech.sayTo(level, speaker, player, Component.translatable("speech.jaymoskingdoms.smog." + stage + "." + player.getRandom().nextInt(LINES)),
					s.count > KNOWING ? Speech.GOLD : Speech.WHITE);
				if (s.count == DRIVE_OUT - 1) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.smog.last_warning", here.name()).withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}

	private static @Nullable Villager nearest(ServerLevel level, ServerPlayer player) {
		List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RANGE),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && v.hasLineOfSight(player));
		return near.stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElse(null);
	}

	private static void driveOut(ServerLevel level, Faction f, ServerPlayer player, Villager speaker, Seen s, long now) {
		s.bannedUntil = now + BAN_TICKS;
		s.shouted = now;
		Speech.say(level, speaker, Component.translatable("speech.jaymoskingdoms.smog.out." + player.getRandom().nextInt(LINES)), Speech.RED, 24.0);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.smog.driven_out", f.name(), BAN_TICKS / 1200)
			.withStyle(ChatFormatting.DARK_RED));
		if (Tempers.of(f) != null) {
			Retinue.exile(level, f, player, BAN_TICKS);
		}
	}

	/** A barred user on the town's land: a ruler's guards are already seeing to it; elsewhere folk shout, then a guard comes. */
	private static void enforce(ServerLevel level, Faction f, ServerPlayer player, Seen s, long now) {
		if (Tempers.of(f) != null) {
			return;
		}
		Villager speaker = nearest(level, player);
		if (speaker != null && now - s.shouted >= COOLDOWN / 2) {
			s.shouted = now;
			Speech.sayTo(level, speaker, player, Component.translatable("speech.jaymoskingdoms.smog.out." + player.getRandom().nextInt(LINES)), Speech.RED);
		}
		if (now - (s.bannedUntil - BAN_TICKS) < GRACE) {
			return;
		}
		PathfinderMob guard = null;
		double best = Double.MAX_VALUE;
		for (Worker w : Workers.near(level, player.getBoundingBox().inflate(GUARD_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD && Rulers.target(g.mob()) == null && !Arrests.busy(g.mob()))) {
			double d = w.mob().distanceToSqr(player);
			if (d < best) {
				best = d;
				guard = w.mob();
			}
		}
		if (guard != null) {
			Rulers.charge(guard, player);
		}
	}
}
