package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The web lines the attacks of {@link WebAttacks} keep out for a few ticks: one from each hand, to a point or to a creature,
 * drawn for everyone like a Yank line ({@link WebPower#LINE}, {@link WebPower#LINE_OFF}). Held again every tick the attack
 * wants them shown, and taken down a few ticks after it stops. Never over a swing web that is out.
 */
final class WebStrikeLines {
	/** Both lines go this many ticks after the last time they were asked for. */
	static final int LINGER = 4;

	private record Out(WebLine main, WebLine off, long until) {
	}

	private static final Map<UUID, Out> OUT = new HashMap<>();

	private WebStrikeLines() {
	}

	/** Lines from both hands to {@code main} and {@code off} (each a point, or the creature when an id is given). */
	static void show(ServerPlayer player, Vec3 mainAnchor, int mainEntity, Vec3 offAnchor, int offEntity) {
		if (WebPower.swingingNow(player)) {
			return;
		}
		WebLine main = new WebLine(WebLine.YANK, mainAnchor, mainEntity);
		WebLine off = new WebLine(WebLine.YANK, offAnchor, offEntity);
		player.setAttached(WebPower.LINE, main);
		player.setAttached(WebPower.LINE_OFF, off);
		OUT.put(player.getUUID(), new Out(main, off, player.level().getGameTime() + LINGER));
	}

	/** Both hands' lines to one point. */
	static void toPoint(ServerPlayer player, Vec3 point) {
		show(player, point, -1, point, -1);
	}

	/** Both hands' lines to one creature. */
	static void toEntity(ServerPlayer player, Entity target) {
		Vec3 centre = target.getBoundingBox().getCenter();
		show(player, centre, target.getId(), centre, target.getId());
	}

	/** Every tick, per player: lines nobody has asked for lately come down. */
	static void tick(ServerPlayer player) {
		Out out = OUT.get(player.getUUID());
		if (out == null || player.level().getGameTime() < out.until()) {
			return;
		}
		clear(player);
	}

	/** Takes down what this class put out (only those lines, if they are still the ones attached). */
	static void clear(ServerPlayer player) {
		Out out = OUT.remove(player.getUUID());
		if (out == null) {
			return;
		}
		if (out.main().equals(player.getAttached(WebPower.LINE))) {
			player.removeAttached(WebPower.LINE);
		}
		if (out.off().equals(player.getAttached(WebPower.LINE_OFF))) {
			player.removeAttached(WebPower.LINE_OFF);
		}
	}

	static void forget(ServerPlayer player) {
		OUT.remove(player.getUUID());
	}

	static void clearAll() {
		OUT.clear();
	}
}
