package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

/**
 * Andy's pick "Guards keep the sick out" (2026-11-15): a town's guards who hear a blighted player's cough
 * ({@link BlightSigns#cough}) deal with it by their temper ({@link Temperaments}). A righteous one orders them out of the
 * town; one still there coughing after {@link #GRACE} is taken by force (the guards come for them as for any who defy
 * them, {@link Theft#arrest}), and once they're downed by a guard or yield to one (sneaking empty-handed by him), they're
 * shut in the realm's cells ({@link Busts#JAILED}) until the blight is cured or has turned them: a cure lets them out
 * at once. A hotheaded or spiteful guard doesn't bother with words, and goes for them. Speech bubbles, never chat, but
 * for the cells' own word on how long.
 */
public final class BlightQuarantine {
	// ---- tuning ----
	/** Guards this near a cough hear it (blocks), if they can see who coughed. */
	static final double HEAR_RANGE = 16.0;
	/** One ordered out has this long to be gone before a cough there gets them taken (ticks); a warning holds this long. */
	static final int GRACE = 20 * 60, WARNING_KEEPS = 20 * 60 * 10;
	/** How long the guards hunt one who stayed (ticks). */
	static final long HUNT_TICKS = 20L * 120;
	/** A guard this near a downed or yielding carrier takes them; yielding takes this long (ticks); checked this often. */
	static final double TAKE_RANGE = 6.0, YIELD_RANGE = 3.0;
	static final int YIELD_TICKS = 40, CHECK = 10;
	/** The cells hold them at least this long (ticks), whatever is left of the blight. */
	static final long LEAST_HELD = 20L * 60;
	/** Lines each ({@code blight_quarantine.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;
	static final int GUARD_COLOUR = 0xFFE0C890;

	/** On a player held in the cells for the blight: which realm. Saved (the cells are). */
	static final AttachmentType<Integer> HELD = AttachmentRegistry.create(JaymosKingdoms.id("blight_quarantine"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath());

	/** Who was ordered out of which realm, and when (game time). Not kept. */
	private record Warning(int realm, long at) {
	}

	private static final Map<UUID, Warning> WARNED = new HashMap<>();
	/** Who the guards are taking for the blight: player → realm. Not kept (the hunt lapses). */
	private static final Map<UUID, Integer> TAKING = new HashMap<>();
	private static final Map<UUID, Integer> YIELDING = new HashMap<>();

	private BlightQuarantine() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BlightQuarantine::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WARNED.clear();
			TAKING.clear();
			YIELDING.clear();
		});
	}

	/** {@code player} coughed the blight's cough: the town's guards who hear it act on it. */
	static void coughed(ServerLevel level, ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || player.hasAttached(HELD) || Busts.jailed(player) || Downed.isDowned(player)) {
			return;
		}
		Faction f = Theft.lawAt(level, player.blockPosition());
		if (f == null || Theft.exempt(player, f)) {
			return;
		}
		List<Worker> guards = Underworld.guards(level, f, player.getBoundingBox().inflate(HEAR_RANGE));
		PathfinderMob guard = null;
		for (Worker w : guards) {
			if (w.mob().hasLineOfSight(player) && (guard == null || w.mob().distanceToSqr(player) < guard.distanceToSqr(player))) {
				guard = w.mob();
			}
		}
		if (guard == null) {
			return;
		}
		long now = level.getGameTime();
		Temperaments.Temperament t = Temperaments.of(guard);
		guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (t == Temperaments.Temperament.HOTHEADED || t == Temperaments.Temperament.SPITEFUL) {
			// no words: they go for the sick one
			say(level, guard, player, "lawless");
			guard.setTarget(player);
			return;
		}
		if (TAKING.containsKey(player.getUUID())) {
			return;
		}
		Warning w = WARNED.get(player.getUUID());
		if (w == null || w.realm() != f.id() || now - w.at() > WARNING_KEEPS || now < w.at()) {
			WARNED.put(player.getUUID(), new Warning(f.id(), now));
			say(level, guard, player, "out");
			return;
		}
		if (now - w.at() < GRACE) {
			return;
		}
		// told, and still here: taken by force
		WARNED.remove(player.getUUID());
		TAKING.put(player.getUUID(), f.id());
		say(level, guard, player, "take");
		Theft.arrest(player, level, f, HUNT_TICKS);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % CHECK != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			Integer held = player.getAttached(HELD);
			if (held != null && !Busts.jailed(player)) {
				// out of the cells some other way (dug out, let out): no longer held for it
				player.removeAttached(HELD);
			}
			Integer realm = TAKING.get(player.getUUID());
			if (realm == null) {
				continue;
			}
			Faction f = Factions.get(server, realm);
			if (f == null || !HollowBlight.blighted(player) || !Theft.wanted(player, realm)) {
				// cured, or got away: the guards let it go
				TAKING.remove(player.getUUID());
				YIELDING.remove(player.getUUID());
				continue;
			}
			taken(level, player, f);
		}
	}

	/** Downed by one of {@code f}'s guards or yielding to one: shut in the cells. */
	private static void taken(ServerLevel level, ServerPlayer player, Faction f) {
		boolean downed = Downed.isDowned(player);
		List<Worker> near = Underworld.guards(level, f, player.getBoundingBox().inflate(downed ? TAKE_RANGE : YIELD_RANGE));
		if (near.isEmpty() || downed && Unmasking.awaits(player)) {
			YIELDING.remove(player.getUUID());
			return;
		}
		if (!downed) {
			if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
				YIELDING.remove(player.getUUID());
				return;
			}
			int held = YIELDING.merge(player.getUUID(), CHECK, Integer::sum);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bust.yielding").withStyle(ChatFormatting.GRAY));
			if (held < YIELD_TICKS) {
				return;
			}
		}
		YIELDING.remove(player.getUUID());
		TAKING.remove(player.getUUID());
		shutAway(level, player, f, near.getFirst().mob());
	}

	/** Into the cells until the blight is out of them, one way or the other. */
	private static void shutAway(ServerLevel level, ServerPlayer player, Faction f, PathfinderMob guard) {
		HollowBlight.Blight blight = player.getAttached(HollowBlight.BLIGHT);
		long now = level.getGameTime();
		long until = Math.max(now + LEAST_HELD, (blight == null ? now : blight.since() + HollowBlight.TURN_TICKS) + HollowBlight.TURN_DARK);
		say(level, guard, player, "cells");
		Theft.clearWanted(player, f.id());
		Underworld.Den d = Underworld.den(level.getServer(), f.id());
		BlockPos cell = Speakeasy.cell(level, f, d == null ? null : d.cell().orElse(null));
		if (d != null) {
			Underworld.putCell(level.getServer(), d, cell);
		}
		Speakeasy.cellDoor(level, cell);
		player.setAttached(Busts.JAILED, new Busts.Jail(f.id(), cell, until));
		player.setAttached(HELD, f.id());
		Vec3 c = Vec3.atBottomCenterOf(cell);
		player.teleportTo(level, c.x, c.y, c.z, Set.of(), player.getYRot(), 0.0F, false);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0F, 0.7F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.blight_quarantine.cells").withStyle(ChatFormatting.RED));
		JaymosKingdoms.LOGGER.info("{} was shut in {}'s cells with the Hollow's blight", player.getScoreboardName(), f.name());
	}

	/** The blight is out of {@code player} (cured or turned): held in the cells for it, they're let out. */
	static void cleared(ServerPlayer player) {
		WARNED.remove(player.getUUID());
		TAKING.remove(player.getUUID());
		YIELDING.remove(player.getUUID());
		if (player.hasAttached(HELD)) {
			player.removeAttached(HELD);
			if (player.level() instanceof ServerLevel level) {
				Busts.letOut(level, player);
			}
		}
	}

	private static void say(ServerLevel level, PathfinderMob guard, ServerPlayer player, String key) {
		Speech.tell(level, guard, player, Component.translatable("blight_quarantine.jaymoskingdoms." + key + "." + player.getRandom().nextInt(LINES)), GUARD_COLOUR);
	}
}
