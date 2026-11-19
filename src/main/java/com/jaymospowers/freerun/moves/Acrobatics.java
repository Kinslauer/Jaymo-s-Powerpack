package com.jaymospowers.freerun.moves;

import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.Powers;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Dodge;
import com.jaymospowers.stamina.Feathers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Acrobatics (Andy 2026-11-19: "add a backflip to the skill tree, and also the ability to use alt in mid air to do
 * different tricks and flips"). Players only: both are asked for by the player's own client ({@code AcrobaticsClient},
 * from the dodge key in {@code DodgeClient}), checked and charged here, and drawn for everyone by the web slinger's
 * whole-body pose ({@link WebPower#POSE}, {@code WebBodyPose}), which draws for anyone it's set on.
 * <ul>
 * <li>The standing backflip: on the ground, Back held and nothing else, the dodge key throws you up and over backwards
 * instead of the back roll, landing on your feet facing the way you were. It is a dodge (the roll's evasion window and
 * cooldown, {@link Dodge#dodgeBy}) and needs the Backflip perk in Kingdoms' Freerun tree ({@link Freerun.Perk#BACKFLIP}).</li>
 * <li>Mid-air tricks: in the air, the dodge key throws a trick, for show like the Sneak-tap air flip (no push, no damage).
 * The movement keys pick the family ({@link Trick#dir}), a random one of it is thrown, never the one just thrown, and only one
 * that has time to finish before you land (the client works out the fall). Freerunner rank 1 (or the web power) opens the
 * plain ones and two a jump; rank 2 the harder ones ({@link Trick#rank}) and a third. Each next trick can start once the last
 * is {@link #CHAIN_AT} through.</li>
 * </ul>
 */
public final class Acrobatics {
	// ===== the knobs ================================================================================================
	/** Feathers a standing backflip costs. */
	public static final int BACKFLIP_COST = 5;
	/** The backflip's hop: up (blocks a tick; a jump is 0.42) and back along the way you face (blocks a tick). */
	public static final double BACKFLIP_UP = 0.52, BACKFLIP_BACK = 0.24;
	/** Feathers a plain trick and a rank-2 trick cost. */
	public static final int TRICK_COST = 2, HARD_TRICK_COST = 3;
	/** Tricks one stretch in the air may hold, by trick rank (0, 1, 2). */
	public static final int[] TRICKS_PER_AIR = {0, 2, 3};
	/** A next trick may start once the one playing is this share through (0-1). */
	public static final float CHAIN_AT = 0.75F;
	/** Client: a trick is thrown only if the ground is at least this many ticks of falling away past the trick's own length. */
	public static final int LAND_SPARE_TICKS = 0;
	// ================================================================================================================

	/** The movement-key families. */
	public static final int FORWARD = 0, BACK = 1, SIDE = 2;

	/** The mid-air tricks: the pose kind, the family its keys pick, and the trick rank it needs. */
	public enum Trick {
		FRONT_FLIP(WebPose.FRONT_FLIP, FORWARD, 1), AIR_SPIN(WebPose.AIR_SPIN, FORWARD, 1), TOE_TOUCH(WebPose.TOE_TOUCH, FORWARD, 1),
		DOUBLE_FRONT(WebPose.DOUBLE_FRONT, FORWARD, 2), CORKSCREW(WebPose.CORKSCREW, FORWARD, 2),
		BACK_FLIP(WebPose.BACK_FLIP, BACK, 1), SWAN(WebPose.SWAN, BACK, 1),
		LAYOUT_TWIST(WebPose.LAYOUT_TWIST, BACK, 2), DOUBLE_BACK(WebPose.DOUBLE_BACK, BACK, 2),
		CARTWHEEL(WebPose.CARTWHEEL, SIDE, 1), SIDE_FLIP(WebPose.SIDE_FLIP, SIDE, 1), BUTTERFLY(WebPose.BUTTERFLY, SIDE, 2);

		public final int kind, dir, rank;

		Trick(int kind, int dir, int rank) {
			this.kind = kind;
			this.dir = dir;
			this.rank = rank;
		}

		/** How long it lasts (ticks). */
		public int ticks() {
			return WebPose.ticks(kind);
		}

		public static Trick of(int kind) {
			for (Trick t : values()) {
				if (t.kind == kind) {
					return t;
				}
			}
			return null;
		}

		/** The tricks of family {@code dir} a trick rank of {@code rank} may throw. */
		public static List<Trick> family(int dir, int rank) {
			List<Trick> out = new ArrayList<>();
			for (Trick t : values()) {
				if (t.dir == dir && t.rank <= rank) {
					out.add(t);
				}
			}
			return out;
		}
	}

	/** Server: tricks thrown since each player last stood on the ground. */
	private static final Map<UUID, Integer> THROWN = new HashMap<>();

	private Acrobatics() {
	}

	/** From {@link RunMoves#init}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!THROWN.isEmpty()) {
				THROWN.keySet().removeIf(id -> {
					ServerPlayer player = server.getPlayerList().getPlayer(id);
					return player == null || player.onGround() || player.isInWater() || player.isPassenger();
				});
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> THROWN.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> THROWN.clear());
	}

	/** Whether {@code player} has learnt the standing backflip (both sides: the client reads the synced ranks). */
	public static boolean backflipLearnt(Player player) {
		return Freerun.perk(player, Freerun.Perk.BACKFLIP) > 0;
	}

	/** {@code player}'s trick rank as last synced (both sides): Freerunner's rank, at least 1 for a web slinger, at most 2. */
	public static int trickRank(Player player) {
		int rank = Freerun.perk(player, Freerun.Perk.FREERUNNER);
		if (Powers.has(player, WebPower.INSTANCE)) {
			rank = Math.max(rank, 1);
		}
		return Math.min(2, rank);
	}

	/** Whether {@code player}'s body may turn a trick in the air now (both sides). */
	public static boolean canTrick(Player player) {
		return !player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.getAbilities().flying && !player.isPassenger()
			&& !player.isUsingItem() && !player.isSpectator() && Freerun.canMove(player) && !WebPower.ragdolled(player);
	}

	/** Whether the pose {@code player} shows now leaves room for a trick at {@code now}: none, over, or a trick {@link #CHAIN_AT} through. */
	public static boolean free(Player player, long now) {
		WebPose current = player.getAttached(WebPower.POSE);
		if (current == null || now - current.start() >= current.ticks()) {
			return true;
		}
		return WebPose.trick(current.kind()) && now - current.start() >= Math.ceil(current.ticks() * CHAIN_AT) - 1;
	}

	/** The cost of {@code trick}. */
	public static int cost(Trick trick) {
		return trick.rank >= 2 ? HARD_TRICK_COST : TRICK_COST;
	}

	/** Packs a trick ask's argument: the pose kind, and the side (±1) a cartwheel or twist turns to. */
	public static long arg(Trick trick, int side) {
		return trick.kind | (side < 0 ? 1L : 0L) << 8;
	}

	static void trick(ServerPlayer player, long arg) {
		Trick trick = Trick.of((int)(arg & 0xFF));
		int side = (arg >> 8 & 1) != 0 ? -1 : 1;
		int rank = Math.min(2, Math.max(Powers.has(player, WebPower.INSTANCE) ? 1 : 0, Freerun.rank(player, Freerun.Perk.FREERUNNER)));
		// (the client checked it was in the air as it threw; by now the server may already have it landed)
		if (trick == null || rank < trick.rank || !(canTrick(player) || player.onGround()) || !Ledge.canHang(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int thrown = THROWN.getOrDefault(player.getUUID(), 0);
		if (thrown >= TRICKS_PER_AIR[rank] || !free(player, now) || !Feathers.spend(player, cost(trick))) {
			return;
		}
		THROWN.put(player.getUUID(), thrown + 1);
		WebPower.pose(player, trick.kind, side);
		float pitch = 1.6F + 0.1F * thrown + player.getRandom().nextFloat() * 0.2F;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F, pitch);
		if (trick.ticks() > 18) {
			// a double goes round twice: a second whoosh halfway
			level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.25F, pitch + 0.15F);
		}
		player.addTag(Freerun.XP_TAG);
	}

	static void backflip(ServerPlayer player) {
		if (Freerun.rank(player, Freerun.Perk.BACKFLIP) < 1 || !Freerun.canMove(player) || player.isPassenger() || player.isInWater()
			|| !Feathers.canSpend(player, BACKFLIP_COST)) {
			return;
		}
		Vec3 back = Vec3.directionFromRotation(0.0F, player.getYRot()).reverse();
		if (!Dodge.dodgeBy(player, back)) {
			return;
		}
		Feathers.spend(player, BACKFLIP_COST);
		WebPower.pose(player, WebPose.STAND_BACKFLIP, 1);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 0.9F);
		player.addTag(Freerun.XP_TAG);
	}
}
