package com.jaymospowers.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * A held beam of fire that is not a power's: Jaymo's Kingdoms' Fire Lance spell runs its own stream and tells this
 * class while it burns, so the stream joins the beam clashes ({@link BeamClash}, kind {@link BeamClash#KIND_FIRE})
 * like Wind Whistle or a ki wave. Kingdoms asks by name (it never needs Powers): {@link #start} when the spell is
 * cast, {@link #stop} when it ends, {@link #active} each tick to learn that a lost clash put it out, {@link #clashing}
 * to hold its own burning while the streams are locked, and {@link #tap} for each press of the caster's key.
 */
public final class SpellBeam {
	/** How far the stream reaches to meet another (blocks): the spell's own length. */
	public static final double RANGE = 14.0;

	private static final Set<UUID> ACTIVE = new HashSet<>();

	private SpellBeam() {
	}

	public static void init() {
		BeamClash.register(new BeamClash.Beam(p -> BeamClash.KIND_FIRE, p -> ACTIVE.contains(p.getUUID()), RANGE, SpellBeam::stop));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
	}

	/** The stream begins. False while a lost clash still rests this player's streams. */
	public static boolean start(ServerPlayer player) {
		if (BeamClash.resting(player)) {
			return false;
		}
		ACTIVE.add(player.getUUID());
		return true;
	}

	public static void stop(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
	}

	public static boolean active(ServerPlayer player) {
		return ACTIVE.contains(player.getUUID());
	}

	public static boolean clashing(ServerPlayer player) {
		return BeamClash.isClashing(player);
	}

	/** A press of the caster's key while the streams are locked: one shove of the knot. True if they were in a clash. */
	public static boolean tap(ServerPlayer player) {
		if (!BeamClash.isClashing(player)) {
			return false;
		}
		BeamClash.input(player, false);
		return BeamClash.input(player, true);
	}
}
