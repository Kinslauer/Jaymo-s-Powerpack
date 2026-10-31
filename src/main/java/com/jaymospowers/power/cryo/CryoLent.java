package com.jaymospowers.power.cryo;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * (0.18.107) Cryokinesis's moves lent to someone without the power: the Xeno-Band's Rime (Scan B of
 * {@code docs/ScanGaps-Plan.md}, Andy: "it also gets all the cryo powers worked in"). The moves run as they do for a
 * Cryokinesis user (the same breath, shards, Flash Freeze, Ice Wall, Ice Slide and Blizzard, and their poses), but the
 * lender keeps its own cooldowns and calls {@link #tick} itself.
 */
public final class CryoLent {
	/** Who has the moves lent right now (both sides; set by Xenoform: a player who is Rime). */
	public static Predicate<Player> lender = player -> false;

	private CryoLent() {
	}

	public static boolean lent(Player player) {
		return lender.test(player);
	}

	/** Frost Breath: held while {@code down}. */
	public static void breath(ServerPlayer player, boolean down) {
		if (down) {
			CryoBreath.start(player);
		} else {
			CryoBreath.stop(player, false);
		}
	}

	public static boolean breathing(ServerPlayer player) {
		return CryoBreath.breathing(player);
	}

	public static void shards(ServerPlayer player) {
		CryoShards.fire(player);
	}

	/** Flash Freeze on the creature looked at (a message if none, or too big). */
	public static void freeze(ServerPlayer player) {
		CryoFlashFreeze.cast(player);
	}

	public static void wall(ServerPlayer player) {
		CryoWall.raise(player);
	}

	/** Ice Slide on; false if it can't start here. */
	public static boolean slideOn(ServerPlayer player) {
		if (CryoSlide.active(player)) {
			return true;
		}
		CryoSlide.toggle(player);
		return CryoSlide.active(player);
	}

	public static void slideOff(ServerPlayer player) {
		CryoSlide.stop(player, false);
	}

	public static boolean sliding(Player player) {
		return CryoSlide.active(player);
	}

	public static void blizzard(ServerPlayer player) {
		CryoBlizzard.start(player);
	}

	/** Every tick the moves are lent. */
	public static void tick(ServerPlayer player) {
		CryoSlide.tick(player);
		CryoBreath.tick(player);
		CryoBlizzard.tick(player);
		CryoMoves.tick(player);
	}

	/** The lend ends: everything running stops, with no cooldown of Cryokinesis's own. */
	public static void end(ServerPlayer player) {
		CryoSlide.stop(player, false);
		CryoBreath.stop(player, false);
		CryoBlizzard.end(player, false);
		CryoMoves.forget(player);
	}
}
