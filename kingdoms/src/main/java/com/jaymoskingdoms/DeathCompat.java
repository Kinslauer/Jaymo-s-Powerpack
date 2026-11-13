package com.jaymoskingdoms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Powers' death rules, for the death and land rites (Andy's picks E3 to E5): who is dying and could be called back
 * ({@code Revival}), and who waits in Limbo to be born again ({@code Limbo}). Reached by name, so Kingdoms needs no Powers jar;
 * without it every question answers no and nothing is added.
 */
public final class DeathCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymospowers");
	private static final String REVIVAL = "com.jaymospowers.core.wounds.Revival", LIMBO = "com.jaymospowers.core.wounds.Limbo";
	private static @Nullable MethodHandle dyingNear, revive, inLimbo, diedOn, waitingIn, reborn;
	private static boolean looked;

	private DeathCompat() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!LOADED) {
			return;
		}
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		try {
			Class<?> revival = Class.forName(REVIVAL), limbo = Class.forName(LIMBO);
			dyingNear = lookup.findStatic(revival, "dyingNear", MethodType.methodType(List.class, ServerLevel.class, Vec3.class, double.class));
			revive = lookup.findStatic(revival, "revive", MethodType.methodType(boolean.class, ServerPlayer.class, Entity.class));
			inLimbo = lookup.findStatic(limbo, "in", MethodType.methodType(boolean.class, Player.class));
			diedOn = lookup.findStatic(limbo, "diedOn", MethodType.methodType(long.class, Player.class));
			waitingIn = lookup.findStatic(limbo, "waitingIn", MethodType.methodType(List.class, ServerLevel.class));
			reborn = lookup.findStatic(limbo, "reborn", MethodType.methodType(boolean.class, ServerPlayer.class, ServerLevel.class, Vec3.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Powers is too old for calling the dead back or for Limbo: {}", e.toString());
			dyingNear = null;
		}
	}

	/** Tells Powers who can call a final death back (it asks as someone dies, and each second of the wait). */
	@SuppressWarnings("unchecked")
	static void addReviver(Predicate<ServerPlayer> reviver) {
		if (!LOADED) {
			return;
		}
		try {
			((List<Predicate<ServerPlayer>>) Class.forName(REVIVAL).getField("REVIVERS").get(null)).add(reviver);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' revivers (older Powers?)", e);
		}
	}

	/** Players whose final death is being held for a reviver, near {@code at}. */
	@SuppressWarnings("unchecked")
	static List<ServerPlayer> dyingNear(ServerLevel level, Vec3 at, double range) {
		look();
		try {
			return dyingNear == null ? List.of() : (List<ServerPlayer>) dyingNear.invoke(level, at, range);
		} catch (Throwable e) {
			return List.of();
		}
	}

	/** {@code by} brings {@code dead} back; false if they were not waiting. */
	static boolean revive(ServerPlayer dead, @Nullable Entity by) {
		look();
		try {
			return revive != null && (boolean) revive.invoke(dead, by);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Whether {@code player} waits in Limbo. */
	static boolean inLimbo(Player player) {
		look();
		try {
			return inLimbo != null && (boolean) inLimbo.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}

	/** The game day {@code player} fell, or -1. */
	static long diedOn(Player player) {
		look();
		try {
			return diedOn == null ? -1L : (long) diedOn.invoke(player);
		} catch (Throwable e) {
			return -1L;
		}
	}

	/** Everyone waiting in Limbo in {@code level}. */
	@SuppressWarnings("unchecked")
	static List<ServerPlayer> waitingIn(ServerLevel level) {
		look();
		try {
			return waitingIn == null ? List.of() : (List<ServerPlayer>) waitingIn.invoke(level);
		} catch (Throwable e) {
			return List.of();
		}
	}

	/** Powers' part of being born again in another body (out of Limbo, the old body's gifts gone). */
	static boolean reborn(ServerPlayer player, ServerLevel level, Vec3 at) {
		look();
		try {
			return reborn != null && (boolean) reborn.invoke(player, level, at);
		} catch (Throwable e) {
			return false;
		}
	}
}
