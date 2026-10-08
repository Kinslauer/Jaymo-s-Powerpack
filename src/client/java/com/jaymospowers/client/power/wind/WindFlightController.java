package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindNet;
import com.jaymospowers.power.wind.WindPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The flight, run on the client (player motion is client-authoritative). Each tick it keeps its own velocity,
 * steers it toward what the controls ask for (smoothly, so there's momentum) and hands it to the player with
 * {@code setDeltaMovement}; vanilla gravity and drag are discarded because the velocity is set fresh every tick.
 * The server is told the state via {@code wind_flight}.
 */
public final class WindFlightController {
	// ---- tuning (blocks per tick; 1 b/t = 20 m/s) -------------------------------------------------------------
	/** Top cruise speed. */
	public static final double TOP_SPEED = 2.5;
	/** Sonic starts at this share of the top speed, and drops back to cruise below the exit share. */
	public static final double SONIC_ENTER = 0.80;
	public static final double SONIC_EXIT = 0.74;
	/** Hover: WASD speed (creative flight is ~0.55), rise/sink speed, how fast velocity follows input (accel/drag). */
	static final double HOVER_SPEED = 0.55;
	static final double HOVER_VERTICAL = 0.42;
	static final double HOVER_RESPONSE = 0.16;
	/** Idle bob: peak vertical speed (the float is about this / 0.1 blocks up and down). */
	static final double HOVER_BOB = 0.012;
	/** Cruise: speed on entering, acceleration while Sprint is held (tapering by 60% toward the top), coast-down and brake. */
	static final double CRUISE_ENTER_SPEED = 0.8;
	static final double CRUISE_ACCEL = 0.05;
	static final double CRUISE_ACCEL_TAPER = 0.6;
	static final double CRUISE_DECEL = 0.06;
	static final double CRUISE_BRAKE = 0.15;
	/**
	 * Turning is rate-limited by speed, with real inertia: the flight path swings toward where you look by at most this
	 * many degrees per tick, from just-cruising to top speed (so at sonic a U-turn takes a long, wide arc).
	 */
	static final double CRUISE_TURN_RATE_SLOW = 7.0;
	static final double CRUISE_TURN_RATE_FAST = 1.2;
	/** Speed bled per radian turned (hard turns cost momentum). */
	static final double CRUISE_TURN_DRAG = 0.5;
	/** Gravity along the flight path: diving gains speed, climbing loses it (blocks/tick per tick at vertical). */
	static final double CRUISE_GRAVITY = 0.035;
	/** Upward kick on take-off from the ground / in mid-air. */
	static final double TAKEOFF_BOOST = 0.9;
	static final double AIR_TAKEOFF_BOOST = 0.5;
	/** Double-tap window for Jump (while sneaking) to take off. */
	static final int DOUBLE_TAP_TICKS = 7;
	/** Horizontal speed lost in one bump that counts as a crash (shake + thud). */
	static final double IMPACT_SPEED = 0.6;
	/** Re-send the state this often while flying, in case the server missed something (respawn, lag). */
	private static final int RESYNC_TICKS = 40;

	private @Nullable LocalPlayer player;
	private WindFlight state = WindFlight.NONE;
	private boolean locked;
	private double speed;
	private Vec3 velocity = Vec3.ZERO;
	private int ticks;
	private int lastJumpTap = -100;
	/** Sneak only sinks once it has been let go after take-off (take-off is done while sneaking). */
	private boolean sinkArmed = true;
	private int lockFlash;
	private @Nullable WindLoopSound loop;
	private WindFlight sentState = WindFlight.NONE;
	private boolean sentLocked;
	private int sinceSent;

	public WindFlight state() {
		return this.state;
	}

	public boolean locked() {
		return this.locked;
	}

	/** 0..1 of the top speed (cruise), or of the hover speed while hovering. */
	public double speedFraction() {
		if (this.state.fast()) {
			return Mth.clamp(this.speed / TOP_SPEED, 0.0, 1.0);
		}
		return 0.0;
	}

	/** Current speed in blocks/tick, for the HUD. */
	public double currentSpeed() {
		return this.state.fast() ? this.speed : this.velocity.length();
	}

	public Vec3 velocity() {
		return this.velocity;
	}

	@Nullable LocalPlayer player() {
		return this.player;
	}

	int lockFlash() {
		return this.lockFlash;
	}

	// ---- per tick ---------------------------------------------------------------------------------------------

	void tick(Minecraft minecraft, LocalPlayer player) {
		this.ticks++;
		if (this.lockFlash > 0) {
			this.lockFlash--;
		}
		if (player != this.player) {
			// first tick, respawn or a dimension change: a new player entity starts on the ground
			this.player = player;
			this.reset();
		}
		int jumpTaps = drain(minecraft.options.keyJump);
		int sneakTaps = drain(minecraft.options.keyShift);
		Input input = player.input.keyPresses;

		if (!this.state.flying()) {
			if (jumpTaps > 0 && input.shift()) {
				if (jumpTaps >= 2 || this.ticks - this.lastJumpTap <= DOUBLE_TAP_TICKS) {
					this.lastJumpTap = -100;
					this.takeOff(minecraft, player);
				} else {
					this.lastJumpTap = this.ticks;
				}
			}
		} else if (!WindFlight.canFly(player) || player.isFallFlying()) {
			this.land(player, false);
		} else {
			this.collide(player);
			if (this.state == WindFlight.HOVER) {
				this.hover(minecraft, player, input);
			} else {
				this.cruise(minecraft, player, input, sneakTaps);
			}
			if (this.state.flying()) {
				player.setDeltaMovement(this.velocity);
				player.resetFallDistance();
				this.effects(minecraft, player);
			}
		}

		WindCamera.setTremble(this.state == WindFlight.SONIC ? 0.1F + 0.1F * (float)this.speedFraction() : 0.0F);
		WindCamera.setFovTarget(this.state.fast() ? (float)Math.pow(this.speedFraction(), 1.2) : 0.0F);
		WindCamera.tick();
		this.sync();
	}

	private void hover(Minecraft minecraft, LocalPlayer player, Input input) {
		if (!input.shift()) {
			this.sinkArmed = true;
		}
		Vec2 move = player.input.getMoveVector();
		float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
		float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
		double wishX = (move.x * cos - move.y * sin) * HOVER_SPEED;
		double wishZ = (move.y * cos + move.x * sin) * HOVER_SPEED;
		double wishY = 0.0;
		if (input.jump()) {
			wishY += HOVER_VERTICAL;
		}
		boolean sinking = input.shift() && this.sinkArmed;
		if (sinking) {
			wishY -= HOVER_VERTICAL;
		}
		if (wishY == 0.0 && wishX * wishX + wishZ * wishZ < 1.0E-4) {
			wishY = Math.cos(this.ticks * 0.1) * HOVER_BOB;
		}
		Vec3 wish = new Vec3(wishX, wishY, wishZ);
		this.velocity = this.velocity.add(wish.subtract(this.velocity).scale(HOVER_RESPONSE));

		if (input.sprint() && (player.getAbilities().instabuild || player.getFoodData().getFoodLevel() > WindPower.CRUISE_MIN_FOOD)) {
			// punch it
			this.state = WindFlight.CRUISE;
			this.locked = false;
			this.speed = Math.max(CRUISE_ENTER_SPEED, this.velocity.length());
			this.velocity = this.velocity.lerp(player.getLookAngle().scale(this.speed), 0.5);
			player.level().playLocalSound(player, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.3F);
			WindCamera.shake(0.5F, 6);
		} else if (sinking && player.onGround()) {
			this.land(player, true);
		}
	}

	/** {@code from} turned toward {@code to} (unit vectors {@code angle} apart) by {@code by} radians, on the great circle. */
	private static Vec3 rotateToward(Vec3 from, Vec3 to, double angle, double by) {
		if (by >= angle) {
			return to;
		}
		double sin = Math.sin(angle);
		if (sin < 1.0E-4) {
			// facing straight back: turn about any axis across the path (prefer the vertical one, a flat U-turn)
			Vec3 axis = Math.abs(from.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
			Vec3 side = from.cross(axis).normalize();
			return from.scale(Math.cos(by)).add(side.scale(Math.sin(by))).normalize();
		}
		return from.scale(Math.sin(angle - by) / sin).add(to.scale(Math.sin(by) / sin)).normalize();
	}

	private void cruise(Minecraft minecraft, LocalPlayer player, Input input, int sneakTaps) {
		if (sneakTaps % 2 == 1) {
			this.locked = !this.locked;
			this.lockFlash = 20;
			player.level().playLocalSound(player, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.35F, this.locked ? 1.6F : 1.1F);
		}
		// too hungry to push hard (like sprinting): no acceleration, and the lock lets go
		boolean starving = !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= WindPower.CRUISE_MIN_FOOD;
		if (starving) {
			this.locked = false;
		}
		boolean boosting = input.sprint() && !starving;
		if (boosting) {
			this.speed += CRUISE_ACCEL * (1.0 - CRUISE_ACCEL_TAPER * this.speed / TOP_SPEED);
		} else if (!this.locked) {
			this.speed -= CRUISE_DECEL;
		}
		if (input.backward()) {
			this.speed -= CRUISE_BRAKE;
			this.locked = false;
		}
		// inertia: the path only swings round toward the look direction so fast, and turning hard costs speed
		Vec3 look = player.getLookAngle();
		Vec3 dir = this.velocity.lengthSqr() > 1.0E-6 ? this.velocity.normalize() : look;
		double fractionNow = Mth.clamp(this.speed / TOP_SPEED, 0.0, 1.0);
		double maxTurn = Math.toRadians(Mth.lerp(fractionNow, CRUISE_TURN_RATE_SLOW, CRUISE_TURN_RATE_FAST));
		double angle = Math.acos(Mth.clamp(dir.dot(look), -1.0, 1.0));
		if (angle > 1.0E-4) {
			double turned = Math.min(angle, maxTurn);
			dir = rotateToward(dir, look, angle, turned);
			this.speed -= this.speed * CRUISE_TURN_DRAG * turned;
		}
		// and gravity along the path
		this.speed -= dir.y * CRUISE_GRAVITY;
		this.speed = Mth.clamp(this.speed, 0.0, TOP_SPEED);
		double fraction = this.speed / TOP_SPEED;
		this.velocity = dir.scale(this.speed);

		if (!boosting && !this.locked && this.speed <= HOVER_SPEED) {
			this.state = WindFlight.HOVER;
			this.sinkArmed = !player.input.keyPresses.shift();
		} else if (this.state == WindFlight.CRUISE && fraction >= SONIC_ENTER) {
			this.state = WindFlight.SONIC;
			this.boom(minecraft, player);
		} else if (this.state == WindFlight.SONIC && fraction < SONIC_EXIT) {
			this.state = WindFlight.CRUISE;
		}
	}

	/** Walls and floors stop you: drop the blocked part of the velocity (vanilla zeroed it on the player). */
	private void collide(LocalPlayer player) {
		Vec3 actual = player.getDeltaMovement();
		if (player.horizontalCollision) {
			double before = this.velocity.horizontalDistance();
			double x = Math.abs(actual.x) < 1.0E-7 ? 0.0 : this.velocity.x;
			double z = Math.abs(actual.z) < 1.0E-7 ? 0.0 : this.velocity.z;
			this.velocity = new Vec3(x, this.velocity.y, z);
			double lost = before - this.velocity.horizontalDistance();
			if (lost > IMPACT_SPEED) {
				this.impact(player, lost, true);
			}
			if (this.state.fast()) {
				this.speed = Math.min(this.speed, this.velocity.length());
			}
		}
		if (player.verticalCollision && (this.velocity.y < 0.0 && player.onGround() || this.velocity.y > 0.0 && !player.onGround())) {
			double lost = Math.abs(this.velocity.y);
			this.velocity = new Vec3(this.velocity.x, 0.0, this.velocity.z);
			if (lost > IMPACT_SPEED * 1.5) {
				this.impact(player, lost, false);
			}
			if (this.state.fast()) {
				this.speed = Math.min(this.speed, this.velocity.length());
			}
		}
	}

	// ---- transitions ------------------------------------------------------------------------------------------

	/** R, or a double-tapped Jump while sneaking. */
	void toggle(Minecraft minecraft, LocalPlayer player) {
		if (player != this.player) {
			this.player = player;
			this.reset();
		}
		if (this.state.flying()) {
			this.land(player, true);
		} else {
			this.takeOff(minecraft, player);
		}
		this.sync();
	}

	private void takeOff(Minecraft minecraft, LocalPlayer player) {
		if (!WindFlight.canFly(player) || player.isFallFlying()) {
			return;
		}
		boolean grounded = player.onGround();
		Vec3 motion = player.getDeltaMovement();
		this.state = WindFlight.HOVER;
		this.locked = false;
		this.speed = 0.0;
		this.sinkArmed = false;
		this.velocity = new Vec3(motion.x, Math.max(motion.y, 0.0) + (grounded ? TAKEOFF_BOOST : AIR_TAKEOFF_BOOST), motion.z);
		player.setDeltaMovement(this.velocity);
		player.resetFallDistance();

		ClientLevel level = minecraft.level;
		if (level != null) {
			level.addParticle(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.1, player.getZ(), 0.0, 0.0, 0.0);
			ring(level, player.position().add(0.0, 0.1, 0.0), new Vec3(0.0, 1.0, 0.0), 0.5, 0.3, 16);
			level.playLocalSound(player, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 1.1F);
		}
		WindCamera.shake(0.8F, 8);
		this.ensureLoop(minecraft, player);
	}

	/** Flight off: gravity takes over, momentum is kept (the fall is harmless). */
	void land(LocalPlayer player, boolean effects) {
		if (!this.state.flying()) {
			return;
		}
		this.state = WindFlight.NONE;
		this.locked = false;
		this.speed = 0.0;
		if (effects) {
			player.level().playLocalSound(player, SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.6F, 1.2F);
		}
	}

	/** The server ended the flight (or refused it): no packet back. */
	void forceLand(LocalPlayer player) {
		this.land(player, false);
		this.sentState = WindFlight.NONE;
		this.sentLocked = false;
	}

	/** Tailwind's release: a burst along the look direction. */
	void dash(LocalPlayer player, float strength) {
		Vec3 look = player.getLookAngle();
		if (this.state.flying()) {
			this.velocity = this.velocity.add(look.scale(strength));
			if (this.state.fast()) {
				this.speed = Math.min(TOP_SPEED, this.speed + strength);
			}
			player.setDeltaMovement(this.velocity);
		} else {
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(strength);
			player.setDeltaMovement(player.getDeltaMovement().add(flat.x, player.onGround() ? 0.35 : 0.1, flat.z));
		}
		WindCamera.shake(0.6F, 6);
	}

	void reset() {
		this.state = WindFlight.NONE;
		this.locked = false;
		this.speed = 0.0;
		this.velocity = Vec3.ZERO;
		this.lastJumpTap = -100;
		this.sinkArmed = true;
		this.lockFlash = 0;
		WindCamera.reset();
	}

	// ---- effects ----------------------------------------------------------------------------------------------

	private void boom(Minecraft minecraft, LocalPlayer player) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		// a vapour ring just ahead to punch through, the shockwave behind, a flash on the horizon
		ring(level, eye.add(look.scale(3.0)), look, 1.0, 0.45, 28);
		level.addParticle(ParticleTypes.SONIC_BOOM, eye.x - look.x * 2.5, eye.y - look.y * 2.5, eye.z - look.z * 2.5, 0.0, 0.0, 0.0);
		Vec3 flash = eye.add(look.scale(8.0));
		level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), flash.x, flash.y, flash.z, 0.0, 0.0, 0.0);
		level.playLocalSound(player, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playLocalSound(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.45F, 0.55F);
		WindCamera.shake(2.2F, 14);
	}

	private void impact(LocalPlayer player, double lost, boolean wall) {
		// the server decides the damage (none in creative)
		ClientPlayNetworking.send(new WindNet.Impact((float)lost, wall));
		ClientLevel level = (ClientLevel)player.level();
		level.playLocalSound(player, SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.addParticle(ParticleTypes.POOF, player.getX(), player.getY() + 0.9, player.getZ(), 0.0, 0.05, 0.0);
		WindCamera.shake((float)Math.min(2.5, lost * 1.2), 10);
	}

	private void effects(Minecraft minecraft, LocalPlayer player) {
		this.ensureLoop(minecraft, player);
		ClientLevel level = minecraft.level;
		if (level == null || !this.state.fast()) {
			return;
		}
		RandomSource random = player.getRandom();
		boolean sonic = this.state == WindFlight.SONIC;
		// the cloud trail behind (seen in third person), along the whole stretch flown this tick
		Vec3 dir = this.velocity.lengthSqr() > 1.0E-4 ? this.velocity.normalize() : player.getLookAngle();
		int steps = sonic ? 4 : 2;
		for (int i = 0; i < steps; i++) {
			double t = i / (double)steps;
			double x = player.getX() - this.velocity.x * t - dir.x * 0.8;
			double y = player.getY() + 0.9 - this.velocity.y * t - dir.y * 0.8;
			double z = player.getZ() - this.velocity.z * t - dir.z * 0.8;
			level.addParticle(ParticleTypes.CLOUD, x + random.nextGaussian() * 0.1, y + random.nextGaussian() * 0.1, z + random.nextGaussian() * 0.1, 0.0, 0.0, 0.0);
		}
		// wind streaks rushing past the camera
		double fraction = this.speedFraction();
		int streaks = sonic ? 3 : fraction > 0.35 ? 1 : 0;
		Vec3 eye = player.getEyePosition();
		Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		for (int i = 0; i < streaks; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = 1.8 + random.nextDouble() * 2.2;
			Vec3 at = eye.add(dir.scale(5.0 + random.nextDouble() * 4.0))
				.add(side.scale(Math.cos(angle) * radius))
				.add(up.scale(Math.sin(angle) * radius));
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0.0, 0.0, 0.0);
		}
		if (sonic && this.ticks % 3 == 0) {
			level.addParticle(ParticleTypes.SMALL_GUST, player.getX() - dir.x * 2.0, player.getY() + 0.9 - dir.y * 2.0, player.getZ() - dir.z * 2.0, 0.0, 0.0, 0.0);
		}
	}

	private void ensureLoop(Minecraft minecraft, LocalPlayer player) {
		// (the engine can drop a sound, e.g. when too many play at once: check now and then that it's still going)
		if (this.loop == null || this.loop.isStopped() || this.ticks % 20 == 0 && !minecraft.getSoundManager().isActive(this.loop)) {
			this.loop = new WindLoopSound(player, this);
			minecraft.getSoundManager().play(this.loop);
		}
	}

	/** A ring of cloud puffs around {@code center}, square to {@code axis}, blowing outward. */
	private static void ring(ClientLevel level, Vec3 center, Vec3 axis, double radius, double speed, int count) {
		Vec3 a = axis.normalize();
		Vec3 side = a.cross(Math.abs(a.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)).normalize();
		Vec3 up = side.cross(a).normalize();
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI * 2.0 / count;
			Vec3 out = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
			Vec3 at = center.add(out.scale(radius));
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, out.x * speed, out.y * speed, out.z * speed);
		}
	}

	private void sync() {
		this.sinceSent++;
		if (this.state != this.sentState || this.locked != this.sentLocked || this.state.flying() && this.sinceSent >= RESYNC_TICKS) {
			this.sentState = this.state;
			this.sentLocked = this.locked;
			this.sinceSent = 0;
			if (ClientPlayNetworking.canSend(WindNet.Flight.TYPE)) {
				ClientPlayNetworking.send(new WindNet.Flight(this.state.ordinal(), this.locked));
			}
		}
	}

	private static int drain(KeyMapping key) {
		int count = 0;
		while (key.consumeClick() && count < 16) {
			count++;
		}
		return count;
	}
}
