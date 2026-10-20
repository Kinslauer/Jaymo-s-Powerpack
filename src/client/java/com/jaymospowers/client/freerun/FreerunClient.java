package com.jaymospowers.client.freerun;

import com.jaymospowers.client.LedgeClient;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.web.WebParkour;
import com.jaymospowers.power.web.WebWallRun;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.14.3) The local player's freerunning (see {@link Freerun} for the moves, the rules and the numbers), from the
 * ranks Kingdoms gives them ({@link Freerun#PERKS}). Ticks after the powers (so a web slinger's own parkour and wall run
 * go first and this stands aside while one is on) and after the ledge grab (a ledge in reach is the ledge's).
 * <ul>
 * <li>A sprinting jump at something waist-high: vault it (Freerunner 1).</li>
 * <li>In the air: Jump held under a bar catches it (Bar Swing); sprinting alongside a wall runs along it (Wall
 * Runner); forward and Jump held at a tall wall clings to it (Climber); a fresh Jump beside or facing a wall kicks off
 * it (Freerunner 2), each face once till you land.</li>
 * <li>Clinging: forward climbs, strafe shimmies, Sneak lets go, Jump kicks off (Freerunner 2); the grip gives out, then
 * you slip. Climbed to the top, the ledge grab takes you and pulls you up and over.</li>
 * <li>Hanging: the body is held under the bar (the vanilla travel skipped, {@code LedgePlayerMixin}); Jump swings off it
 * forward, Sneak drops.</li>
 * </ul>
 * A held move (cling, hang, wall run) the server doesn't confirm within {@link #CONFIRM_TICKS} drops.
 */
public final class FreerunClient {
	/** How far from the body (blocks) a wall is felt for a cling or a kick; to step onto it for a run, and to stay on it. */
	private static final double REACH = 0.35, RUN_REACH_ENTRY = 0.35, RUN_REACH = 0.3;
	/** Pressed into the wall this hard each tick while clinging, and while running along it. */
	private static final double HUG = 0.02, RUN_HUG = 0.06;
	/** Ticks a refused (unconfirmed) held move lasts, ticks to settle under a bar, and before another move after letting go. */
	private static final int CONFIRM_TICKS = 20, BAR_SETTLE_TICKS = 4, LET_GO_COOLDOWN = 8;

	private static int kind = Freerun.NONE;
	/** The wall's side for a cling or a run (null otherwise). */
	private static @Nullable Direction wall;
	/** The bar hung from, where the hang began and where it settles. */
	private static @Nullable BlockPos bar;
	private static Vec3 caughtAt = Vec3.ZERO, hangAt = Vec3.ZERO;
	/** A wall run's heading along the wall, speed and rise. */
	private static Vec3 along = Vec3.ZERO;
	private static double speed, up;
	private static int ticks, maxTicks, cooldown, airTicks;
	private static boolean confirmed, jumpWasDown;
	/** Wall faces kicked off and run along since last on the ground (bits by 3D index). */
	private static int kicked, ran;
	/** (review) Ticks of grip spent clinging since last on the ground, and how many were spent when this cling began: letting go and catching the wall again doesn't renew the grip. */
	private static int gripped, clingBase;

	private FreerunClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(FreerunClient::tick);
	}

	/** In a held move: clinging, hanging from a bar or running along a wall. */
	public static boolean active() {
		return kind == Freerun.CLING || kind == Freerun.BAR || kind == Freerun.WALL_RUN;
	}

	/** Clinging to a wall or hanging from a bar (Sneak lets go: no Combat Roll from it). */
	public static boolean holding() {
		return kind == Freerun.CLING || kind == Freerun.BAR;
	}

	/** Hanging from a bar: {@link #travel} moves the body instead of the vanilla travel. */
	public static boolean hanging() {
		return kind == Freerun.BAR;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			reset();
			return;
		}
		Input input = player.input.keyPresses;
		boolean jumpPressed = input.jump() && !jumpWasDown;
		jumpWasDown = input.jump();
		if (cooldown > 0) {
			cooldown--;
		}
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			kicked = 0;
			ran = 0;
			gripped = 0;
			airTicks = 0;
		} else {
			airTicks++;
		}
		if (Freerun.unsteady(player) && player.isSprinting()) {
			// no Sure Footing: a narrow top is walked, not run
			player.setSprinting(false);
		}
		boolean free = !player.isSpectator() && Freerun.canMove(player) && !LedgeClient.active()
			&& !com.jaymospowers.client.power.web.WebPowerClient.parkourBusy();
		if (!free) {
			if (active()) {
				end(player);
			}
			kind = Freerun.NONE;
			return;
		}
		switch (kind) {
			case Freerun.CLING -> tickCling(player, input, jumpPressed);
			case Freerun.WALL_RUN -> tickRun(player, input, jumpPressed);
			case Freerun.BAR -> tickBar(player, input, jumpPressed);
			case Freerun.VAULT, Freerun.WALL_JUMP -> {
				// the pose plays out on the server's clock; here the move is done once it has its push
				if (++ticks > 2) {
					kind = Freerun.NONE;
				}
			}
			default -> tryStart(player, input, jumpPressed);
		}
	}

	private static void tryStart(LocalPlayer player, Input input, boolean jumpPressed) {
		if (cooldown > 0) {
			return;
		}
		// (the jump off the ground has already happened this tick: airborne one tick)
		if (jumpPressed && airTicks <= 1 && player.isSprinting() && input.forward() && Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 1
			&& tryVault(player)) {
			return;
		}
		if (player.onGround()) {
			return;
		}
		double vy = player.getDeltaMovement().y;
		if (input.jump() && vy > -0.4 && Freerun.perk(player, Freerun.Perk.BAR_SWING) >= 1 && tryBar(player)) {
			return;
		}
		if (input.forward() && !input.shift() && player.isSprinting() && Freerun.perk(player, Freerun.Perk.WALL_RUNNER) >= 1 && tryRun(player)) {
			return;
		}
		if (input.forward() && input.jump() && !input.shift() && Freerun.perk(player, Freerun.Perk.CLIMBER) >= 1 && tryCling(player)) {
			return;
		}
		if (jumpPressed && airTicks > 2 && Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 2) {
			tryKick(player, null);
		}
	}

	// ---- vault --------------------------------------------------------------------------------------------------------

	/** A sprinting jump at something waist-high: up and over it, keeping every bit of the run's speed. */
	private static boolean tryVault(LocalPlayer player) {
		Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		double height = Freerun.vaultHeight(player.level(), player, dir, Freerun.VAULT_REACH);
		if (height <= 0.0) {
			return false;
		}
		double forward = Math.max(Freerun.VAULT_FORWARD, player.getDeltaMovement().horizontal().length());
		player.setDeltaMovement(dir.scale(forward).add(0.0, height > 1.0 ? Freerun.VAULT_UP_HIGH : Freerun.VAULT_UP_LOW, 0.0));
		start(player, Freerun.VAULT, null, BlockPos.ZERO);
		cooldown = Freerun.VAULT_COOLDOWN;
		return true;
	}

	// ---- cling, climb, kick -------------------------------------------------------------------------------------------

	private static boolean tryCling(LocalPlayer player) {
		Direction facing = player.getDirection();
		int grip = Freerun.GRIP_TICKS[Mth.clamp(Freerun.perk(player, Freerun.Perk.CLIMBER), 1, Freerun.GRIP_TICKS.length) - 1];
		if (gripped >= grip || player.getDeltaMovement().y < -Freerun.CLING_CATCH_FALL || !Freerun.climbable(player.level(), player, facing, REACH)) {
			return false;
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		start(player, Freerun.CLING, facing, BlockPos.ZERO);
		clingBase = gripped;
		return true;
	}

	private static void tickCling(LocalPlayer player, Input input, boolean jumpPressed) {
		Direction face = wall;
		ticks++;
		gripped = clingBase + ticks;
		Integer move = player.getAttached(Freerun.MOVE);
		if (face == null || !confirm(move != null && WebParkour.kind(move) == Freerun.CLING) || player.onGround() || input.shift()) {
			letGo(player);
			return;
		}
		if (jumpPressed && Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 2) {
			tryKick(player, face);
			return;
		}
		int rank = Mth.clamp(Freerun.perk(player, Freerun.Perk.CLIMBER), 1, Freerun.CLIMB_SPEED.length);
		int grip = Freerun.GRIP_TICKS[rank - 1];
		if (gripped > grip + Freerun.SLIP_TICKS) {
			letGo(player);
			return;
		}
		if (input.forward()) {
			// at the top: the ledge grab takes over and pulls you up and over
			Ledge.Spot spot = Ledge.find(player);
			if (spot != null) {
				end(player);
				LedgeClient.catchFromClimb(player, spot);
				return;
			}
		}
		if (!WebWallRun.wallAt(player.level(), player, face, REACH)) {
			if (input.forward() && player.level().noCollision(player, player.getBoundingBox().move(0.0, 0.6, 0.0))) {
				// the wall ran out under the hands with no ledge to catch: a scramble up and over it
				player.setDeltaMovement(new Vec3(face.getStepX(), 0.0, face.getStepZ()).scale(WebParkour.MANTLE_IN).add(0.0, WebParkour.MANTLE_UP, 0.0));
				player.resetFallDistance();
				end(player);
				return;
			}
			letGo(player);
			return;
		}
		boolean slipping = gripped > grip;
		double vertical = slipping ? -Freerun.SLIP_SPEED : input.forward() ? Freerun.CLIMB_SPEED[rank - 1] : 0.0;
		Direction left = face.getCounterClockWise();
		double strafe = (input.left() ? 1.0 : 0.0) - (input.right() ? 1.0 : 0.0);
		Vec3 into = new Vec3(face.getStepX(), 0.0, face.getStepZ()).scale(HUG);
		Vec3 side = new Vec3(left.getStepX(), 0.0, left.getStepZ()).scale(strafe * Freerun.SHIMMY_SPEED);
		player.setDeltaMovement(side.add(into).add(0.0, vertical, 0.0));
		player.resetFallDistance();
		if (slipping && ticks % 4 == 0) {
			Vec3 at = player.position().add(face.getStepX() * 0.35, 1.2, face.getStepZ() * 0.35);
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0.0, 0.02, 0.0);
		}
	}

	/** Let go of the wall: a little push off it, the fall from here on yours. */
	private static void letGo(LocalPlayer player) {
		Direction face = wall;
		end(player);
		cooldown = LET_GO_COOLDOWN;
		if (face != null) {
			player.setDeltaMovement(-face.getStepX() * Freerun.LET_GO_PUSH, Math.min(0.0, player.getDeltaMovement().y), -face.getStepZ() * Freerun.LET_GO_PUSH);
		}
	}

	/** Kick off a wall: the one held ({@code from}), else one in front or beside you not kicked off since you landed. */
	private static void tryKick(LocalPlayer player, @Nullable Direction from) {
		Direction face = from;
		if (face == null) {
			Direction facing = player.getDirection();
			if ((kicked & 1 << facing.get3DDataValue()) == 0 && WebWallRun.wallAt(player.level(), player, facing, REACH)) {
				face = facing;
			} else {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					if ((kicked & 1 << side.get3DDataValue()) == 0 && WebWallRun.wallAt(player.level(), player, side, REACH)) {
						face = side;
						break;
					}
				}
			}
		}
		if (face == null) {
			return;
		}
		Vec3 normal = new Vec3(face.getStepX(), 0.0, face.getStepZ());
		Vec3 out = normal.scale(-Freerun.KICK_OUT);
		Vec3 flat = kind == Freerun.WALL_RUN ? along.scale(speed) : player.getDeltaMovement().horizontal();
		Vec3 keep = flat.subtract(normal.scale(flat.dot(normal))).scale(Freerun.KICK_KEEP);
		player.setDeltaMovement(keep.add(out).add(0.0, Freerun.KICK_UP, 0.0));
		kicked |= 1 << face.get3DDataValue();
		// (from a cling or a run the server swaps that move for the kick)
		kind = Freerun.NONE;
		wall = null;
		start(player, Freerun.WALL_JUMP, face, BlockPos.ZERO);
		Vec3 at = player.position().add(face.getStepX() * 0.4, 0.6, face.getStepZ() * 0.4);
		for (int i = 0; i < 5; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, out.x * 0.1, 0.02, out.z * 0.1);
		}
	}

	// ---- wall run -----------------------------------------------------------------------------------------------------

	/** Step onto a wall at your side, if you're moving along it fast enough (as {@code WebWallRunClient} does). */
	private static boolean tryRun(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		Vec3 flat = v.horizontal();
		if (flat.length() < WebWallRun.ENTRY_SPEED || v.y < -WebWallRun.ENTRY_FALL_MAX) {
			return false;
		}
		Vec3 heading = flat.normalize();
		Direction best = null;
		double bestInto = WebWallRun.ENTRY_INTO;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if ((ran & 1 << side.get3DDataValue()) != 0) {
				continue;
			}
			double into = heading.dot(new Vec3(side.getStepX(), 0.0, side.getStepZ()));
			if (Math.abs(into) <= bestInto && into > -0.3 && WebWallRun.wallAt(player.level(), player, side, RUN_REACH_ENTRY)) {
				best = side;
				bestInto = Math.abs(into);
			}
		}
		if (best == null) {
			return false;
		}
		Vec3 tangent = new Vec3(-best.getStepZ(), 0.0, best.getStepX());
		along = heading.dot(tangent) >= 0.0 ? tangent : tangent.scale(-1.0);
		maxTicks = Freerun.RUN_TICKS[Mth.clamp(Freerun.perk(player, Freerun.Perk.WALL_RUNNER), 1, Freerun.RUN_TICKS.length) - 1];
		speed = Math.min(WebWallRun.RUN_SPEED_MAX, Math.max(WebWallRun.RUN_SPEED_MIN, flat.length()));
		up = Math.min(WebWallRun.RUN_LIFT_MAX, Math.max(v.y, WebWallRun.RUN_LIFT));
		ran |= 1 << best.get3DDataValue();
		player.setDeltaMovement(along.scale(speed).add(0.0, up, 0.0));
		player.resetFallDistance();
		start(player, Freerun.WALL_RUN, best, BlockPos.ZERO);
		return true;
	}

	private static void tickRun(LocalPlayer player, Input input, boolean jumpPressed) {
		Direction face = wall;
		ticks++;
		if (face == null || !confirm(player.getAttached(Freerun.WALL) != null) || !input.forward() || input.shift() || ticks > maxTicks
			|| player.onGround() || !WebWallRun.wallAt(player.level(), player, face, RUN_REACH)) {
			// off the wall: the run's speed carries on into the air
			end(player);
			return;
		}
		if (jumpPressed) {
			if (Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 2) {
				tryKick(player, face);
			}
			return;
		}
		Vec3 moved = new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
		if (ticks > 1 && moved.dot(along) < speed * 0.3) {
			// ran into something ahead
			end(player);
			return;
		}
		up = Math.max(-WebWallRun.RUN_FALL_MAX, up - WebWallRun.RUN_GRAVITY);
		Vec3 into = new Vec3(face.getStepX(), 0.0, face.getStepZ()).scale(RUN_HUG);
		player.setDeltaMovement(along.scale(speed).add(into).add(0.0, up, 0.0));
		player.resetFallDistance();
		if (ticks % 3 == 0) {
			Vec3 at = player.position().add(face.getStepX() * 0.35, 0.2, face.getStepZ() * 0.35);
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, -along.x * 0.05, 0.01, -along.z * 0.05);
		}
	}

	// ---- bar swing ----------------------------------------------------------------------------------------------------

	private static boolean tryBar(LocalPlayer player) {
		BlockPos pos = Freerun.barAbove(player.level(), player);
		if (pos == null) {
			return false;
		}
		bar = pos;
		caughtAt = player.position();
		hangAt = Freerun.hangSpot(pos, Freerun.barBottom(player.level(), pos));
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		start(player, Freerun.BAR, null, pos);
		return true;
	}

	private static void tickBar(LocalPlayer player, Input input, boolean jumpPressed) {
		ticks++;
		BlockPos at = bar;
		if (at == null || !confirm(player.getAttached(Freerun.HANG) != null) || Double.isNaN(Freerun.barBottom(player.level(), at)) || input.shift()
			|| ticks > Freerun.BAR_MAX_TICKS) {
			end(player);
			cooldown = LET_GO_COOLDOWN;
			return;
		}
		if (jumpPressed && ticks >= BAR_SETTLE_TICKS) {
			// swing off it, forward the way you look
			Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot());
			kind = Freerun.NONE;
			bar = null;
			cooldown = LET_GO_COOLDOWN;
			player.setDeltaMovement(dir.scale(Freerun.RELEASE_FORWARD).add(0.0, Freerun.RELEASE_UP, 0.0));
			ClientPlayNetworking.send(new Freerun.Move(Freerun.RELEASE, -1, BlockPos.ZERO));
		}
	}

	/** In place of the player's travel while hanging from a bar: settle under it and stay there. */
	public static void travel(LocalPlayer player) {
		if (bar == null) {
			return;
		}
		float t = Math.min(1.0F, (ticks + 1.0F) / BAR_SETTLE_TICKS);
		float s = t * t * (3.0F - 2.0F * t);
		Vec3 target = caughtAt.lerp(hangAt, s);
		player.setDeltaMovement(Vec3.ZERO);
		player.move(MoverType.SELF, target.subtract(player.position()));
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}

	// ------------------------------------------------------------------------------------------------------------------

	/** Whether a held move goes on: once the server's copy has come it must stay; until then, for {@link #CONFIRM_TICKS}. */
	private static boolean confirm(boolean present) {
		confirmed |= present;
		return confirmed ? present : ticks <= CONFIRM_TICKS;
	}

	private static void start(LocalPlayer player, int move, @Nullable Direction face, BlockPos pos) {
		kind = move;
		wall = face;
		ticks = 0;
		confirmed = false;
		ClientPlayNetworking.send(new Freerun.Move(move, face == null ? -1 : face.get3DDataValue(), pos));
	}

	/** Ends a held move here and on the server. */
	private static void end(LocalPlayer player) {
		boolean held = active();
		kind = Freerun.NONE;
		wall = null;
		bar = null;
		if (held && ClientPlayNetworking.canSend(Freerun.Move.TYPE)) {
			ClientPlayNetworking.send(new Freerun.Move(Freerun.NONE, -1, BlockPos.ZERO));
		}
	}

	private static void reset() {
		kind = Freerun.NONE;
		wall = null;
		bar = null;
		kicked = 0;
		ran = 0;
		gripped = 0;
		cooldown = 0;
		airTicks = 0;
		jumpWasDown = false;
	}
}
