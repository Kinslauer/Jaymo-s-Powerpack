package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tight gaps for a web slinger. Climbing a wall or a ceiling (Wall Crawl's latch, or a wall cling) the body lies low, as
 * in the prone stance (the swimming pose, a 0.6 block box), whenever the way ahead only fits that low, so a one-block
 * gap can be crawled through. And a web slinger walks through cobwebs unslowed and tears them down by hand at once
 * ({@code WebCobwebMixin}).
 */
public final class WebGaps {
	/** How far ahead (blocks) the way is tried for a gap. */
	static final double LOOK_AHEAD = 0.6;
	/** A cobweb is torn at this destroy speed by hand (it is hardness 4: this takes about two ticks). */
	public static final float WEB_TEAR_SPEED = 100.0F;

	private WebGaps() {
	}

	/** A web slinger, whose body shrugs off cobwebs. */
	public static boolean passesWebs(Player player) {
		return Powers.has(player, WebPower.INSTANCE) && !player.isSpectator();
	}

	/** Climbing a wall or ceiling on the tendrils' latch, or clinging to a wall (both synced, so both sides agree). */
	static boolean climbing(Player player) {
		if (!Powers.has(player, WebPower.INSTANCE)) {
			return false;
		}
		if (player.getAttached(WebTendrils.CLIMB) != null) {
			return true;
		}
		Integer move = player.getAttached(WebParkour.MOVE);
		return move != null && WebParkour.kind(move) == WebParkour.CLING;
	}

	/** Climbing, and the way ahead fits only lying low (or the standing body wouldn't fit where it is): the low pose. */
	public static boolean needsLow(Player player) {
		if (!climbing(player) || player.isPassenger() || player.isSleeping() || player.isFallFlying()) {
			return false;
		}
		Vec3 here = player.position();
		AABB stand = player.getDimensions(Pose.STANDING).makeBoundingBox(here);
		if (player.getPose() == Pose.SWIMMING && !player.level().noCollision(player, stand.deflate(1.0E-7))) {
			return true;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 ahead = here.add(flat.normalize().scale(LOOK_AHEAD));
		return !player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(ahead).deflate(1.0E-7))
			&& player.level().noCollision(player, player.getDimensions(Pose.SWIMMING).makeBoundingBox(ahead).deflate(1.0E-7));
	}
}
