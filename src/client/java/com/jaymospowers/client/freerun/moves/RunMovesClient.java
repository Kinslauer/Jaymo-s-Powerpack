package com.jaymospowers.client.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.freerun.AirFlipClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.freerun.LandingRoll;
import com.jaymospowers.freerun.moves.ChargeJump;
import com.jaymospowers.freerun.moves.FastSwim;
import com.jaymospowers.freerun.moves.Hiding;
import com.jaymospowers.freerun.moves.LongJump;
import com.jaymospowers.freerun.gear.TraceurBoots;
import com.jaymospowers.freerun.moves.RunMoves;
import com.jaymospowers.freerun.moves.SkyDive;
import com.jaymospowers.freerun.moves.TrickJump;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * The running, falling, swimming and hiding moves on this side ({@link RunMoves}): the body is the client's, so it's
 * thrown here and the server told. Every gesture is kept clear of the ones already on Sneak and Jump:
 * <ul>
 * <li>Long jump: a Sneak <i>tap</i> (at most {@link #TAP_HOLD_TICKS}) right around a jump out of a fast run. A held Sneak
 * stays the slide's, two taps the zoom's, and the air flip's wait for its tap is called off.</li>
 * <li>Charge jump: Sneak held still on the ground for {@link ChargeJump#START_TICKS} first, and never when that press
 * came right after a tap (the zoom's double tap); Web keeps its own.</li>
 * <li>Sky dive: Jump in a long fall, and only if a moment later you're still plainly falling (nothing else took it).</li>
 * <li>Safety tap: {@code DodgeClient} asks {@link #safetyTap} before rolling.</li>
 * </ul>
 */
public final class RunMovesClient {
	/** The same tap length and double-tap window as {@code SneakZoom}'s and {@code AirFlipClient}'s. */
	static final int TAP_HOLD_TICKS = 3, DOUBLE_TAP_TICKS = 6;
	/** A fall this long (blocks) counts as a landing the safety tap is for. */
	static final double SAFETY_FALL = 2.5;
	/** The charge bar over the Feathers row: its colour (ARGB). */
	static final int CHARGE_COLOUR = 0x70FFFFFF;

	private static boolean sneakWas, jumpWas, wasOnGround;
	/** Ticks the current Sneak press has been held; client tick of the last press and of the last release. */
	private static int sneakHeld;
	private static long pressedAt = Long.MIN_VALUE / 2, releasedAt = Long.MIN_VALUE / 2;
	/** This press began right after a tap (a double tap: the zoom's), so it never charges. */
	private static boolean doubleTap;
	/** Client tick of the last jump; the long jump in this run of air is spent; ticks left keeping the sprint. */
	private static long jumpedAt = Long.MIN_VALUE / 2;
	private static boolean longJumped;
	private static int keepSprint;
	/** The charge so far (ticks), shown on the Feathers row. */
	private static int charge;
	/** A trick flip waiting to be asked for at this tick, and which. */
	private static long trickAt = -1L;
	private static int trickKind;
	/** The fall of this run of air (blocks), and the tick a real fall last landed. */
	private static double fall;
	private static long landedAt = Long.MIN_VALUE / 2;
	/** Ticks left before checking a sky dive's Jump wasn't someone else's (0: none waiting); the dive is on here. */
	private static int skyCheck;
	private static boolean skyDiving;
	/** Client tick this sky dive began here (the server's flag should follow soon after). */
	private static long skySince;
	/** Ticks pressing into a hideable block (negative: resting after a try). */
	private static int hideTicks;

	private RunMovesClient() {
	}

	/** From {@code JaymosPowersClient}. */
	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			if (minecraft.player != null) {
				pin(minecraft.player);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(RunMovesClient::tick);
		HudElementRegistry.attachElementAfter(JaymosPowers.id("feathers"), JaymosPowers.id("charge_jump"), RunMovesClient::renderCharge);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			reset();
			return;
		}
		long now = player.tickCount;
		boolean screen = minecraft.gui.screen() != null;
		boolean sneak = !screen && minecraft.options.keyShift.isDown();
		boolean jump = !screen && minecraft.options.keyJump.isDown();
		boolean sneakPressed = sneak && !sneakWas, jumpPressed = jump && !jumpWas;
		if (sneakPressed) {
			doubleTap = now - releasedAt <= DOUBLE_TAP_TICKS && releasedAt - pressedAt <= TAP_HOLD_TICKS;
			pressedAt = now;
		} else if (!sneak && sneakWas) {
			releasedAt = now;
		}
		sneakHeld = sneak ? sneakHeld + 1 : 0;
		boolean onGround = player.onGround();

		if (pin(player)) {
			if (jumpPressed) {
				send(RunMoves.UNHIDE, 0L);
			}
		} else {
			boolean jumped = wasOnGround && !onGround && jump && player.getDeltaMovement().y > 0.2;
			if (jumped) {
				jumpedAt = now;
				longJumped = false;
				tickJumped(player, now);
			}
			if (!onGround && !longJumped && sneakPressed && now - jumpedAt <= LongJump.TAP_AFTER) {
				longJump(player);
			}
			if (trickAt >= 0 && now >= trickAt) {
				if (!longJumped && !onGround) {
					send(RunMoves.TRICK_JUMP, trickKind);
				}
				trickAt = -1L;
			}
			if (keepSprint > 0) {
				keepSprint--;
				if (player.input.keyPresses.forward()) {
					player.setSprinting(true);
				}
			}
			tickCharge(player, sneak, onGround);
			tickSkyDive(player, jumpPressed);
			tickFastSwim(player);
			tickHide(player, sneak);
		}
		// the fall of this run of air, for the safety tap
		if (!onGround) {
			fall = Math.max(fall, player.fallDistance);
		} else if (!wasOnGround) {
			if (fall >= SAFETY_FALL) {
				landedAt = now;
			}
			fall = 0.0;
		}
		sneakWas = sneak;
		jumpWas = jump;
		wasOnGround = onGround;
	}

	/** Just left the ground on a jump: a charged leap, a long jump (tap just before), or a chance at a trick flip. */
	private static void tickJumped(LocalPlayer player, long now) {
		if (charge >= ChargeJump.MIN_TICKS && ChargeJump.allowed(player) && Feathers.canSpend(player, ChargeJump.COST_MIN)) {
			Vec3 v = player.getDeltaMovement();
			double up = ChargeJump.up(charge, v.y);
			if (TraceurBoots.wears(player)) up += TraceurBoots.CHARGE_JUMP_BONUS;
			player.setDeltaMovement(v.x, up, v.z);
			send(RunMoves.CHARGE_JUMP, charge);
			charge = 0;
			return;
		}
		charge = 0;
		boolean tapped = now - pressedAt <= LongJump.TAP_BEFORE && (sneakHeld == 0 ? releasedAt - pressedAt <= TAP_HOLD_TICKS : sneakHeld <= TAP_HOLD_TICKS);
		if (tapped && longJump(player)) {
			return;
		}
		if (FastRun.active(player) && player.getRandom().nextFloat() < TrickJump.CHANCE) {
			trickKind = player.getRandom().nextFloat() < TrickJump.BACK_SHARE ? WebPose.BACK_FLIP : WebPose.FRONT_FLIP;
			trickAt = now + Math.max(TrickJump.DELAY_TICKS, LongJump.TAP_AFTER + 1);
		}
	}

	/** Out of a fast run: sail out long and flat the way you face. */
	private static boolean longJump(LocalPlayer player) {
		if (!FastRun.active(player) || !Feathers.canSpend(player, LongJump.COST) || FreerunClient.active()) {
			return false;
		}
		Vec3 v = player.getDeltaMovement();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double speed = Mth.clamp(v.horizontalDistance() * LongJump.BOOST, LongJump.MIN_SPEED, LongJump.MAX_SPEED);
		if (TraceurBoots.wears(player)) speed *= 1.0 + TraceurBoots.LONG_JUMP_BONUS;
		player.setDeltaMovement(-Mth.sin(yaw) * speed, LongJump.UP, Mth.cos(yaw) * speed);
		longJumped = true;
		keepSprint = LongJump.POSE_TICKS + 6;
		trickAt = -1L;
		AirFlipClient.cancelTap();
		send(RunMoves.LONG_JUMP, 0L);
		return true;
	}

	/** Standing still with Sneak held: wind up. Moving, a power's body, water, or letting go loses it. */
	private static void tickCharge(LocalPlayer player, boolean sneak, boolean onGround) {
		Input input = player.input.keyPresses;
		boolean still = onGround && sneak && !doubleTap && !input.forward() && !input.backward() && !input.left() && !input.right()
			&& player.getDeltaMovement().horizontalDistance() < ChargeJump.MOVE_CANCEL && !player.isInWater() && ChargeJump.allowed(player);
		if (!still) {
			if (onGround) {
				charge = 0;
			}
			return;
		}
		if (sneakHeld > ChargeJump.START_TICKS) {
			charge = Math.min(ChargeJump.FULL_TICKS, charge + 1);
		}
	}

	private static void tickSkyDive(LocalPlayer player, boolean jumpPressed) {
		if (skyDiving && !SkyDive.active(player) && player.tickCount - skySince > 10) {
			// the server didn't take it (out of feathers)
			skyDiving = false;
		}
		boolean on = skyDiving || SkyDive.active(player);
		if (on && (player.onGround() || !SkyDive.free(player))) {
			skyDiving = false;
			return;
		}
		if (on) {
			if (jumpPressed) {
				skyDiving = false;
				send(RunMoves.SKY_DIVE_END, 0L);
				return;
			}
			glide(player);
			return;
		}
		if (jumpPressed && player.fallDistance >= SkyDive.MIN_FALL && -player.getDeltaMovement().y >= SkyDive.MIN_SPEED && SkyDive.free(player)
			&& Feathers.canSpend(player, SkyDive.COST)) {
			skyCheck = SkyDive.CHECK_TICKS;
		} else if (skyCheck > 0 && --skyCheck == 0) {
			// still plainly falling: nothing else (a second jump, wings, a flight) took that Jump
			if (-player.getDeltaMovement().y >= SkyDive.MIN_SPEED * 0.8 && SkyDive.free(player)) {
				skyDiving = true;
				skySince = player.tickCount;
				send(RunMoves.SKY_DIVE, 0L);
			}
		}
	}

	/** Spread out: a slower fall, and the keys steer. */
	private static void glide(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		Input input = player.input.keyPresses;
		float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float strafe = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		double x = v.x, z = v.z;
		if (forward != 0.0F || strafe != 0.0F) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
			Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
			x += dir.x * SkyDive.STEER;
			z += dir.z * SkyDive.STEER;
		}
		double drift = Math.sqrt(x * x + z * z);
		if (drift > SkyDive.MAX_DRIFT) {
			x *= SkyDive.MAX_DRIFT / drift;
			z *= SkyDive.MAX_DRIFT / drift;
		}
		player.setDeltaMovement(x, Math.max(v.y, -SkyDive.MAX_FALL_SPEED), z);
	}

	private static void tickFastSwim(LocalPlayer player) {
		if (!FastSwim.active(player) || !player.isSwimming() || !player.isInWater()) {
			return;
		}
		Vec3 v = player.getDeltaMovement().add(player.getLookAngle().scale(FastSwim.PUSH));
		double speed = v.length();
		if (speed > FastSwim.MAX_SPEED) {
			v = v.scale(FastSwim.MAX_SPEED / speed);
		}
		player.setDeltaMovement(v);
	}

	/** Sneak held, pressing forward into a leaves block or hay bale at the feet: squeeze in. */
	private static void tickHide(LocalPlayer player, boolean sneak) {
		if (hideTicks < 0) {
			hideTicks++;
			return;
		}
		if (!sneak || !player.onGround() || !player.horizontalCollision || !player.input.keyPresses.forward()) {
			hideTicks = 0;
			return;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double reach = player.getBbWidth() * 0.5 + 0.3;
		BlockPos pos = BlockPos.containing(player.getX() - Mth.sin(yaw) * reach, player.getY() + 0.1, player.getZ() + Mth.cos(yaw) * reach);
		if (!Hiding.hideable(player.level().getBlockState(pos))) {
			hideTicks = 0;
			return;
		}
		if (++hideTicks >= Hiding.ENTER_TICKS) {
			hideTicks = -20;
			send(RunMoves.HIDE, pos.asLong());
		}
	}

	/** Hidden: the body is kept where the server put it. True if hidden. */
	private static boolean pin(LocalPlayer player) {
		BlockPos spot = Hiding.spot(player);
		if (spot == null) {
			return false;
		}
		Vec3 at = Hiding.anchor(spot);
		if (player.position().distanceToSqr(at) > 1.0E-4) {
			player.setPos(at);
		}
		player.setDeltaMovement(Vec3.ZERO);
		return true;
	}

	/**
	 * From {@code DodgeClient}, before it rolls: the dodge at touchdown (held from the air, {@code landing}, or pressed
	 * just after a real fall) with no direction held is the safety tap instead ({@link LandingRoll#safetyTap}). True if
	 * it was (the press is spent).
	 */
	public static boolean safetyTap(LocalPlayer player, boolean landing) {
		Input input = player.input.keyPresses;
		if (input.forward() || input.backward() || input.left() || input.right()) {
			return false;
		}
		if (!landing && player.tickCount - landedAt > LandingRoll.LATE_TICKS) {
			return false;
		}
		if (!Feathers.canSpend(player, LandingRoll.SAFETY_COST)) {
			return true;
		}
		landedAt = Long.MIN_VALUE / 2;
		send(RunMoves.SAFETY_TAP, 0L);
		return true;
	}

	private static void send(int move, long arg) {
		if (ClientPlayNetworking.canSend(RunMoves.Ask.TYPE)) {
			ClientPlayNetworking.send(new RunMoves.Ask(move, arg));
		}
	}

	private static void reset() {
		sneakWas = jumpWas = wasOnGround = false;
		sneakHeld = charge = skyCheck = hideTicks = keepSprint = 0;
		skyDiving = longJumped = doubleTap = false;
		trickAt = -1L;
		fall = 0.0;
	}

	/** The charge, as a pale veil filling the Feathers row from the right. */
	private static void renderCharge(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || charge <= 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		int right = graphics.guiWidth() / 2 + 91;
		int y = graphics.guiHeight() - 49;
		int width = Math.round(80 * Math.min(1.0F, charge / (float)ChargeJump.FULL_TICKS));
		graphics.fill(right - width, y, right, y + 9, CHARGE_COLOUR);
		if (charge >= ChargeJump.FULL_TICKS) {
			graphics.fill(right - 80, y + 9, right, y + 10, 0xC0FFFFFF);
		}
	}
}
