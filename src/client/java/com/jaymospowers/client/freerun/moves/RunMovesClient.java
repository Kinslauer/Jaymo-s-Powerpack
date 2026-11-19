package com.jaymospowers.client.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.freerun.AirFlipClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.LandingRoll;
import com.jaymospowers.freerun.moves.AirRoll;
import com.jaymospowers.freerun.moves.Breach;
import com.jaymospowers.freerun.moves.ChargeJump;
import com.jaymospowers.freerun.moves.FastSwim;
import com.jaymospowers.freerun.moves.Hiding;
import com.jaymospowers.freerun.moves.Leap;
import com.jaymospowers.freerun.moves.LongJump;
import com.jaymospowers.freerun.gear.TraceurBoots;
import com.jaymospowers.freerun.moves.RunMoves;
import com.jaymospowers.freerun.moves.SkyDive;
import com.jaymospowers.freerun.moves.SwimBurst;
import com.jaymospowers.freerun.moves.TrickJump;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
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
 * <li>Charge jump: Sneak held still on the ground for {@link ChargeJump#START_TICKS} first (and never when that press
 * came right after a tap, the zoom's double tap) puts a horse's jump bar in place of the XP bar; then Jump is held to fill
 * it and let go to leap ({@code ChargeJumpMixin} keeps the plain jump off meanwhile). Web keeps its own.</li>
 * <li>Sky dive: Jump in a long fall, and only if a moment later you're still plainly falling (nothing else took it).</li>
 * <li>Safety tap: {@code DodgeClient} asks {@link #safetyTap} before rolling.</li>
 * <li>Swim burst: a fresh press of Sprint while swimming surges you along your look ({@link SwimBurst}).</li>
 * <li>Breach: a fresh Jump while sprint-swimming at the surface throws you out of the water along your look ({@link Breach}); you
 * come down into a slide on the ground, or skip off the water if you land on it shallow and fast.</li>
 * <li>Leap into prone: the Crawl key, or Sneak + a scroll down, in the air within {@link Leap#WINDOW_TICKS} of a sprint jump ({@code CrawlTaps} and {@code StanceScroll} ask {@link #leap} first).</li>
 * <li>Air roll: a fresh Sprint press within {@link AirRoll#WINDOW_TICKS} of a sprint jump, with the Air Roll perk.</li>
 * </ul>
 */
public final class RunMovesClient {
	/** The same tap length and double-tap window as {@code SneakZoom}'s and {@code AirFlipClient}'s. */
	static final int TAP_HOLD_TICKS = 3, DOUBLE_TAP_TICKS = 6;
	/** A fall this long (blocks) counts as a landing the safety tap is for. */
	static final double SAFETY_FALL = 2.5;
	/** The charge bar's orange peak (ARGB): still to fill, and filled. */
	static final int PEAK_EMPTY = 0xFFB85A12, PEAK_FILLED = 0xFFFFA530;
	/** Vanilla's horse jump bar. */
	private static final Identifier JUMP_BAR = Identifier.withDefaultNamespace("hud/jump_bar_background"),
		JUMP_BAR_FILL = Identifier.withDefaultNamespace("hud/jump_bar_progress");

	private static boolean sneakWas, jumpWas, sprintWas, wasOnGround;
	/** Client tick of the last jump out of a sprint, and whether this run of air's leap and air roll are spent. */
	private static long sprintJumpAt = Long.MIN_VALUE / 2;
	private static boolean leapt, airRolled;
	/** Ticks the current Sneak press has been held; client tick of the last press and of the last release. */
	private static int sneakHeld;
	private static long pressedAt = Long.MIN_VALUE / 2, releasedAt = Long.MIN_VALUE / 2;
	/** This press began right after a tap (a double tap: the zoom's), so it never charges. */
	private static boolean doubleTap;
	/** Client tick of the last jump; the long jump in this run of air is spent; ticks left keeping the sprint. */
	private static long jumpedAt = Long.MIN_VALUE / 2;
	private static boolean longJumped;
	private static int keepSprint;
	/** Sneak is held still on the ground: the jump bar is up and Jump charges it. The charge so far (ticks), and where its orange peak starts. */
	private static boolean armed;
	private static int charge;
	private static float peak;
	/** A trick flip waiting to be asked for at this tick, and which. */
	private static long trickAt = -1L;
	private static int trickKind;
	/** The fall of this run of air (blocks), and the tick a real fall last landed. */
	private static double fall;
	private static long landedAt = Long.MIN_VALUE / 2;
	/** Client ticks of the last swim burst (it lifts FastSwim's speed cap for {@link SwimBurst#SURGE_TICKS}) and of the last breach (or skip). */
	private static long burstAt = Long.MIN_VALUE / 2, breachAt = Long.MIN_VALUE / 2;
	/** A breach leap is in the air, and the skips it has made. */
	private static boolean breaching;
	private static int skips;
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
		// the charge jump's bar takes the XP bar's place, as a horse's does
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, old -> (graphics, delta) -> {
			if (!renderCharge(graphics)) {
				old.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, old -> (graphics, delta) -> {
			if (!showing()) {
				old.extractRenderState(graphics, delta);
			}
		});
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
		boolean sprint = !screen && minecraft.options.keySprint.isDown();
		boolean sneakPressed = sneak && !sneakWas, jumpPressed = jump && !jumpWas, sprintPressed = sprint && !sprintWas;
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
				sprintJumpAt = player.isSprinting() || FastRun.active(player) ? now : Long.MIN_VALUE / 2;
				leapt = airRolled = false;
				tickJumped(player, now);
			}
			if (sprintPressed && !onGround && !airRolled && now - sprintJumpAt <= AirRoll.WINDOW_TICKS) {
				airRoll(player);
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
			tickCharge(player, now, sneak, jump, onGround);
			tickSkyDive(player, jumpPressed);
			tickSwim(player, now, sprintPressed, jumpPressed);
			tickFastSwim(player, now);
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
		sprintWas = sprint;
		wasOnGround = onGround;
	}

	/** Whether a swim burst is ready now ({@link SwimBurst#COOLDOWN} since the last), for the move hints. */
	public static boolean burstReady(LocalPlayer player) {
		return player.tickCount - burstAt >= SwimBurst.COOLDOWN && SwimBurst.hasAir(player) && Feathers.canSpend(player, SwimBurst.COST);
	}

	/** Whether a breach is ready now ({@link Breach#COOLDOWN} since the last), for the move hints. */
	public static boolean breachReady(LocalPlayer player) {
		return player.tickCount - breachAt >= Breach.COOLDOWN && !player.onGround() && Feathers.canSpend(player, Breach.COST);
	}

	/** Just left the ground on a jump: a charged leap, a long jump (tap just before), or a chance at a trick flip. */
	private static void tickJumped(LocalPlayer player, long now) {
		boolean tapped = now - pressedAt <= LongJump.TAP_BEFORE && (sneakHeld == 0 ? releasedAt - pressedAt <= TAP_HOLD_TICKS : sneakHeld <= TAP_HOLD_TICKS);
		if (tapped && longJump(player)) {
			return;
		}
		// (Andy's skill-tree locks) the flips are Freerunner's: without it, no flip (they're only for show, so nothing is said)
		if (FastRun.active(player) && com.jaymospowers.core.TreeLocks.has(player, com.jaymospowers.core.TreeLocks.TRICK_JUMP) && player.getRandom().nextFloat() < TrickJump.CHANCE) {
			trickKind = player.getRandom().nextFloat() < TrickJump.BACK_SHARE ? WebPose.BACK_FLIP : WebPose.FRONT_FLIP;
			trickAt = now + Math.max(TrickJump.DELAY_TICKS, LongJump.TAP_AFTER + 1);
		}
	}

	/** A fresh Sprint press just after a sprint jump, perk learnt, feathers to spare: a roll forward through the air with a real push the way you face. */
	private static void airRoll(LocalPlayer player) {
		if (!AirRoll.allowed(player) || !Feathers.canSpend(player, AirRoll.COST) || FreerunClient.active() || !Freerun.canMove(player)
			|| player.isSpectator() || player.isInWater()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double speed = Mth.clamp(v.horizontalDistance() * AirRoll.BOOST + AirRoll.ADD, AirRoll.MIN_SPEED, AirRoll.MAX_SPEED);
		player.setDeltaMovement(-Mth.sin(yaw) * speed, v.y + AirRoll.LIFT, Mth.cos(yaw) * speed);
		airRolled = true;
		keepSprint = 10;
		AirFlipClient.cancelTap();
		send(RunMoves.AIR_ROLL, 0L);
	}

	/**
	 * From {@code CrawlTaps}, before the Crawl key toggles prone, and {@code StanceScroll} (Sneak + a scroll down): pressed in the air within {@link Leap#WINDOW_TICKS} of a sprint
	 * jump, it dives you forward onto your belly with a burst, flat enough for a gap a block and a half high, and you land
	 * prone. True if it was (the press is spent).
	 */
	public static boolean leap(LocalPlayer player) {
		long now = player.tickCount;
		if (player.onGround() || leapt || longJumped || now - sprintJumpAt > Leap.WINDOW_TICKS || FreerunClient.active() || !Freerun.canMove(player)
			|| !Crawl.canCrawl(player) || !Feathers.canSpend(player, Leap.COST)) {
			return false;
		}
		Vec3 v = player.getDeltaMovement();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double speed = Mth.clamp(v.horizontalDistance() * Leap.BOOST, Leap.MIN_SPEED, Leap.MAX_SPEED);
		player.setDeltaMovement(-Mth.sin(yaw) * speed, Math.min(v.y, Leap.UP_CAP), Mth.cos(yaw) * speed);
		leapt = true;
		longJumped = true;
		trickAt = -1L;
		Leap.localId = player.getId();
		Leap.localAt = player.level().getGameTime();
		com.jaymospowers.client.CrawlTaps.goProne(player, false);
		send(RunMoves.LEAP, 0L);
		return true;
	}

	/** Out of a fast run: sail out long and flat the way you face. */
	private static boolean longJump(LocalPlayer player) {
		if (!FastRun.active(player) || !Feathers.canSpend(player, LongJump.COST) || FreerunClient.active()) {
			return false;
		}
		if (!com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.LONG_JUMP)) {
			// (Andy's skill-tree locks) learned: Long Jump
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

	/**
	 * Standing still with Sneak held: the jump bar is up. Holding Jump fills it, letting go leaps at what it shows. Moving,
	 * a power's body, water, or letting go of Sneak loses the charge (and a Jump still held then is a plain jump).
	 */
	private static void tickCharge(LocalPlayer player, long now, boolean sneak, boolean jump, boolean onGround) {
		Input input = player.input.keyPresses;
		armed = onGround && sneak && !doubleTap && sneakHeld > ChargeJump.START_TICKS && !input.forward() && !input.backward()
			&& !input.left() && !input.right() && player.getDeltaMovement().horizontalDistance() < ChargeJump.MOVE_CANCEL
			&& !player.isInWater() && ChargeJump.allowed(player);
		if (!armed) {
			charge = 0;
			return;
		}
		if (jump) {
			if (charge == 0) {
				peak = ChargeJump.peak(player, player.getRandom());
			}
			charge = Math.min(ChargeJump.FULL_TICKS, charge + 1);
			return;
		}
		if (charge == 0) {
			return;
		}
		// let go of Jump: leap at the charge the bar shows (a short one is a plain jump)
		int ticks = charge;
		charge = 0;
		player.jumpFromGround();
		jumpedAt = now;
		sprintJumpAt = Long.MIN_VALUE / 2;
		if (ticks >= ChargeJump.MIN_TICKS && Feathers.canSpend(player, ChargeJump.COST_MIN)) {
			Vec3 v = player.getDeltaMovement();
			double up = ChargeJump.up(ChargeJump.share(fill(ticks), peak), v.y);
			if (TraceurBoots.wears(player)) up += TraceurBoots.CHARGE_JUMP_BONUS;
			player.setDeltaMovement(v.x, up, v.z);
			send(RunMoves.CHARGE_JUMP, ticks);
		}
	}

	/** Whether Jump is filling the charge bar now, so it mustn't jump yet ({@code ChargeJumpMixin}). */
	public static boolean holdingCharge(LocalPlayer player) {
		return armed && player.onGround() && Minecraft.getInstance().options.keyJump.isDown();
	}

	private static float fill(int ticks) {
		return Math.min(1.0F, ticks / (float)ChargeJump.FULL_TICKS);
	}

	private static boolean showing() {
		LocalPlayer player = Minecraft.getInstance().player;
		return armed && player != null && !player.isCreative() && !player.isSpectator();
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

	/** A fresh Sprint while swimming surges; a fresh Jump while sprint-swimming at the surface breaches; a breach in the air is watched over. */
	private static void tickSwim(LocalPlayer player, long now, boolean sprintPressed, boolean jumpPressed) {
		if (breaching) {
			tickBreach(player, now);
		}
		boolean swimming = player.isSwimming() && player.isInWater() && !player.isPassenger() && !player.isSpectator() && Freerun.canMove(player);
		if (!swimming) {
			return;
		}
		if (sprintPressed && now - burstAt >= SwimBurst.COOLDOWN && SwimBurst.hasAir(player) && Feathers.canSpend(player, SwimBurst.COST)
			&& com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.SWIM_BURST)) {
			Vec3 v = player.getDeltaMovement().add(player.getLookAngle().scale(SwimBurst.PUSH));
			double speed = v.length();
			player.setDeltaMovement(speed > SwimBurst.MAX_SPEED ? v.scale(SwimBurst.MAX_SPEED / speed) : v);
			burstAt = now;
			send(RunMoves.SWIM_BURST, 0L);
		} else if (jumpPressed && !player.onGround() && !player.isUnderWater() && now - breachAt >= Breach.COOLDOWN && Feathers.canSpend(player, Breach.COST)
			&& com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.BREACH)) {
			// out of the water the way you look, at least a little up so a level look still clears it
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
			double up = Math.max(look.y, Breach.MIN_LOOK_Y);
			player.setDeltaMovement(flat.scale(Math.sqrt(1.0 - up * up)).add(0.0, up, 0.0).scale(Breach.SPEED));
			breaching = true;
			skips = 0;
			breachAt = now;
			send(RunMoves.BREACH, 0L);
		}
	}

	/** In the air from a breach: a landing on the ground slides (still sprinting), one on the water skips if shallow and fast enough, else swims on. */
	private static void tickBreach(LocalPlayer player, long now) {
		if (now - breachAt > Breach.FLIGHT_TICKS || player.isPassenger() || player.isSpectator()) {
			breaching = false;
			return;
		}
		if (now - breachAt < 3) {
			// still leaving the water
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (player.onGround()) {
			breaching = false;
			double flat = v.horizontalDistance();
			if (player.isSprinting() && player.input.keyPresses.forward() && flat >= Breach.SLIDE_MIN_SPEED) {
				FreerunClient.slideOutOf(player, new Vec3(v.x, 0.0, v.z), flat);
			}
		} else if (player.isInWater()) {
			if (skips < Breach.SKIPS && v.y < 0.0 && -v.y <= Breach.SKIP_MAX_DOWN && v.horizontalDistance() >= Breach.SKIP_MIN_SPEED && !player.isUnderWater()) {
				player.setDeltaMovement(v.x * Breach.SKIP_KEEP, Math.max(-v.y * Breach.SKIP_BOUNCE, Breach.SKIP_MIN_UP), v.z * Breach.SKIP_KEEP);
				skips++;
				breachAt = now;
				send(RunMoves.BREACH_SKIP, 0L);
			} else {
				breaching = false;
			}
		}
	}

	private static void tickFastSwim(LocalPlayer player, long now) {
		if (!FastSwim.active(player) || !player.isSwimming() || !player.isInWater()) {
			return;
		}
		Vec3 v = player.getDeltaMovement().add(player.getLookAngle().scale(FastSwim.PUSH));
		double speed = v.length();
		// a burst's surge goes over FastSwim's own top speed while it lasts
		double cap = now - burstAt <= SwimBurst.SURGE_TICKS ? Math.max(FastSwim.MAX_SPEED, SwimBurst.MAX_SPEED) : FastSwim.MAX_SPEED;
		if (speed > cap) {
			v = v.scale(cap / speed);
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
		sneakWas = jumpWas = sprintWas = wasOnGround = breaching = leapt = airRolled = false;
		sprintJumpAt = Long.MIN_VALUE / 2;
		sneakHeld = charge = skyCheck = hideTicks = keepSprint = 0;
		skyDiving = longJumped = doubleTap = armed = false;
		trickAt = -1L;
		fall = 0.0;
	}

	/** The charge, on a horse's jump bar in the XP bar's place, its orange peak drawn over it. False when there's none to draw. */
	private static boolean renderCharge(GuiGraphicsExtractor graphics) {
		if (!showing()) {
			return false;
		}
		int left = (graphics.guiWidth() - 182) / 2;
		int top = graphics.guiHeight() - 29;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, JUMP_BAR, left, top, 182, 5);
		int filled = Math.round(182 * fill(charge));
		if (filled > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, JUMP_BAR_FILL, 182, 5, 0, 0, left, top, filled, 5);
		}
		if (charge > 0) {
			// the peak: where the full leap is (the perk keeps it in one spot; without it, it moves every charge)
			int from = left + Math.round(182 * peak), to = Math.min(left + 181, from + Math.round(182 * ChargeJump.PEAK_WIDTH));
			int edge = Mth.clamp(left + filled, from, to);
			graphics.fill(from, top + 1, edge, top + 4, PEAK_FILLED);
			graphics.fill(edge, top + 1, to, top + 4, PEAK_EMPTY);
		}
		return true;
	}
}
