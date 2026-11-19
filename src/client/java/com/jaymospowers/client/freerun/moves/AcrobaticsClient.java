package com.jaymospowers.client.freerun.moves;

import com.jaymospowers.client.freerun.AirFlipClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.moves.Acrobatics;
import com.jaymospowers.freerun.moves.RunMoves;
import com.jaymospowers.stamina.Feathers;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Acrobatics on this side ({@link Acrobatics}), asked for by the dodge key ({@code DodgeClient}): on the ground with only
 * Back held, the standing backflip (moved here at once, as the roll is); in the air, a trick of the family the movement keys
 * pick, if one has time to finish before the ground comes up.
 */
public final class AcrobaticsClient {
	/** How far down (blocks) the ground is looked for; past it the fall counts as long enough for anything. */
	static final double LOOK_DOWN = 24.0;
	/** Ticks of fall counted at most. */
	static final int MAX_FALL_TICKS = 100;

	/** Tricks thrown this stretch in the air, and the last one's pose kind (-1: none). */
	private static int thrown;
	private static int lastKind = -1;

	private AcrobaticsClient() {
	}

	/** Every client tick the player stands on the ground (or swims, or rides): the next stretch in the air starts afresh. */
	public static void grounded() {
		thrown = 0;
		lastKind = -1;
	}

	/** The standing backflip, from the dodge key on the ground: true if it went (the press is spent). */
	public static boolean backflip(LocalPlayer player) {
		Input input = player.input.keyPresses;
		if (!input.backward() || input.forward() || input.left() || input.right() || !Acrobatics.backflipLearnt(player)
			|| FreerunClient.active() || !Freerun.canMove(player) || player.isInWater() || player.isPassenger()
			|| !Feathers.canSpend(player, Acrobatics.BACKFLIP_COST) || !ClientPlayNetworking.canSend(RunMoves.Ask.TYPE)) {
			return false;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(Mth.sin(yaw) * Acrobatics.BACKFLIP_BACK + motion.x * 0.2, Acrobatics.BACKFLIP_UP,
			-Mth.cos(yaw) * Acrobatics.BACKFLIP_BACK + motion.z * 0.2);
		player.setSprinting(false);
		AirFlipClient.cancelTap();
		ClientPlayNetworking.send(new RunMoves.Ask(RunMoves.BACKFLIP, 0L));
		return true;
	}

	/** A mid-air trick, from the dodge key in the air: true if one was thrown (the press is spent; otherwise it is the landing roll's). */
	public static boolean trick(LocalPlayer player) {
		Acrobatics.Trick trick = pick(player, true);
		if (trick == null) {
			return false;
		}
		Input input = player.input.keyPresses;
		int side = input.left() != input.right() ? input.left() ? -1 : 1 : player.getRandom().nextBoolean() ? 1 : -1;
		thrown++;
		lastKind = trick.kind;
		AirFlipClient.cancelTap();
		ClientPlayNetworking.send(new RunMoves.Ask(RunMoves.AIR_TRICK, Acrobatics.arg(trick, side)));
		return true;
	}

	/** Whether the dodge key would throw a trick now (the move hints show it). */
	public static boolean ready(LocalPlayer player) {
		return pick(player, false) != null;
	}

	/** A trick the player may throw now for the keys held, at random ({@code roll}) or the first that fits; null for none. */
	private static Acrobatics.Trick pick(LocalPlayer player, boolean roll) {
		int rank = Acrobatics.trickRank(player);
		if (rank < 1 || thrown >= Acrobatics.TRICKS_PER_AIR[rank] || !Acrobatics.canTrick(player) || FreerunClient.active()
			|| com.jaymospowers.boxing.Boxing.boxing(player) || !Acrobatics.free(player, player.level().getGameTime())
			|| !ClientPlayNetworking.canSend(RunMoves.Ask.TYPE)) {
			return null;
		}
		Input input = player.input.keyPresses;
		int dir = input.left() != input.right() && !input.forward() && !input.backward() ? Acrobatics.SIDE
			: input.backward() && !input.forward() ? Acrobatics.BACK : Acrobatics.FORWARD;
		int air = ticksToGround(player);
		List<Acrobatics.Trick> family = Acrobatics.Trick.family(dir, rank);
		family.removeIf(t -> t.kind == lastKind || t.ticks() + Acrobatics.LAND_SPARE_TICKS > air || !Feathers.canSpend(player, Acrobatics.cost(t)));
		if (family.isEmpty()) {
			return null;
		}
		return roll ? family.get(player.getRandom().nextInt(family.size())) : family.get(0);
	}

	/** Ticks until the player's feet reach the ground below at the speed they fall now (vanilla's gravity and drag), or {@link #MAX_FALL_TICKS}. */
	static int ticksToGround(LocalPlayer player) {
		AABB box = player.getBoundingBox();
		double drop = LOOK_DOWN;
		for (double d = 0.25; d <= LOOK_DOWN; d += 0.25) {
			if (!player.level().noCollision(player, box.move(0.0, -d, 0.0))) {
				drop = d - 0.25;
				break;
			}
		}
		if (drop >= LOOK_DOWN) {
			return MAX_FALL_TICKS;
		}
		double y = 0.0, vy = player.getDeltaMovement().y;
		double gravity = player.getGravity();
		for (int t = 1; t <= MAX_FALL_TICKS; t++) {
			y += vy;
			if (y <= -drop) {
				return t;
			}
			vy = (vy - gravity) * 0.98;
		}
		return MAX_FALL_TICKS;
	}
}
