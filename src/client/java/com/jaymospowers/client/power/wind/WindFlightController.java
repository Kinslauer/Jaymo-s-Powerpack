package com.jaymospowers.client.power.wind;

import com.jaymospowers.core.Fate;
import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindNet;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.wind.WindStorm;
import com.jaymospowers.power.wind.WindWeather;
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
	/** Top cruise speed in a plain rainy sky (weather factor 1.0); the weather scales it (see {@link #topSpeed()}). */
	public static final double TOP_SPEED = 2.5;
	/** The Tempest (ascended) turns this much harder before inertia or a tumble wins. */
	static final double TEMPEST_TURN_BONUS = 1.4;
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
	static final double CRUISE_DECEL = 0.012;
	static final double CRUISE_BRAKE = 0.15;
	/**
	 * The air brake: S at speed pushes a gust out ahead from the palms. Momentum is shed smoothly (this share kept each
	 * tick, plus the plain brake) down to a hover; it's only an air brake from {@code AIR_BRAKE_MIN_SPEED} up.
	 */
	static final double AIR_BRAKE_KEEP = 0.93, AIR_BRAKE_PLAIN = 0.25;
	static final double AIR_BRAKE_MIN_SPEED = 0.7;
	/** The gust's clouds out of the palms each tick, and a puff of wind every {@code AIR_BRAKE_GUST_EVERY} ticks. */
	static final int AIR_BRAKE_CLOUDS = 6, AIR_BRAKE_GUST_EVERY = 3;
	/**
	 * Turning is rate-limited by speed, with real inertia: the flight path swings toward where you look by at most this
	 * many degrees per tick, from just-cruising to top speed (so at sonic a U-turn takes a long, wide arc).
	 */
	static final double CRUISE_TURN_RATE_SLOW = 7.0;
	static final double CRUISE_TURN_RATE_FAST = 0.7;
	/**
	 * Losing control: at or above this fraction of top speed, looking further off the flight path than the limit
	 * (from {@code TUMBLE_ANGLE_SLOW} degrees at that speed down to {@code TUMBLE_ANGLE_FAST} at top speed) throws
	 * you into a tumble for {@code TUMBLE_TICKS}, after which you recover into a hover.
	 */
	static final double TUMBLE_MIN_FRACTION = 0.35;
	static final double TUMBLE_ANGLE_SLOW = 110.0;
	static final double TUMBLE_ANGLE_FAST = 60.0;
	/**
	 * Once the power is mastered (every ability unlocked), only a real reversal tumbles you: looking this far off the
	 * flight path (degrees) at speed, like snapping round 180°. Dips and climbs are yours to make.
	 */
	static final double MASTERED_TUMBLE_ANGLE = 150.0;
	public static final int TUMBLE_TICKS = 40;
	/** Momentum kept per tick while tumbling (vanilla air is 0.91; a tumbling body carries further). */
	static final double TUMBLE_DRAG = 0.975;
	/** How long the recovery pose is held after regaining control. */
	public static final int RECOVER_TICKS = 30;
	/** Speed bled per radian turned (hard turns cost momentum). */
	static final double CRUISE_TURN_DRAG = 0.35;
	/**
	 * Gravity along the flight path: point down and you pick up speed ({@code DIVE_GRAVITY}, blocks/tick per tick
	 * straight down); climbing costs a little ({@code CLIMB_GRAVITY}), never enough to stop you gaining height. A dive
	 * can carry you past the top speed the wind alone gives, up to {@code DIVE_OVERSPEED} of it.
	 */
	static final double DIVE_GRAVITY = 0.085, CLIMB_GRAVITY = 0.02;
	static final double DIVE_OVERSPEED = 1.3;
	/**
	 * Momentum fights pulling up: the nose can rise at most this many degrees per tick, from slow (free) to top speed
	 * (barely), so at speed you can't wrench yourself up and out of a dive or a blast; you have to arc out of it.
	 */
	static final double PULL_UP_RATE_SLOW = 7.0, PULL_UP_RATE_FAST = 0.8;
	/** Upward kick on take-off from the ground / in mid-air. */
	static final double TAKEOFF_BOOST = 0.9;
	static final double AIR_TAKEOFF_BOOST = 0.5;
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
	/** Sneak only sinks once it has been let go after take-off (take-off is done while sneaking). */
	private boolean sinkArmed = true;
	private int lockFlash;
	private @Nullable WindLoopSound loop;
	private int tumbleTicks;
	private int recoverTicks;
	private WindFlight sentState = WindFlight.NONE;
	private boolean sentLocked;
	private int sinceSent;
	/** This tick's top speed: TOP_SPEED × weather (× 0.75 Stormbound). */
	private double top = TOP_SPEED;
	/** This tick's turn-rate and tumble-angle multiplier. */
	private double turnBonus = 1.0;
	/** The air brake is on (S at speed): the palms-out pose and the gust ahead. */
	private boolean braking;
	private boolean sentBraking;

	public WindFlight state() {
		return this.state;
	}

	public boolean locked() {
		return this.locked;
	}

	/** Using the air brake right now. */
	public boolean braking() {
		return this.braking;
	}

	/** Ticks of tumble left (0 when not tumbling). */
	public int tumbleTicks() {
		return this.state == WindFlight.TUMBLE ? this.tumbleTicks : 0;
	}

	/** Ticks of the recovery pose left (0 when not recovering). */
	public int recoverTicks() {
		return this.recoverTicks;
	}

	/** The top cruise speed right now (the weather and the power's fate decide it). */
	public double topSpeed() {
		return this.top;
	}

	/** 0..1 of the top speed (cruise), or of the hover speed while hovering. */
	public double speedFraction() {
		if (this.state.fast()) {
			return Mth.clamp(this.speed / this.top, 0.0, 1.0);
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
		int sneakTaps = drain(minecraft.options.keyShift);
		Input input = player.input.keyPresses;
		// the weather governs the wind: a still clear day flies slower, a storm faster; Stormbound drags at you
		this.top = TOP_SPEED * WindWeather.factor(player) * (Fate.isCorrupted(player) ? WindStorm.STORMBOUND_CRUISE_MULTIPLIER : 1.0)
			* (com.jaymospowers.power.wind.WindModes.tempest(player) ? com.jaymospowers.power.wind.WindModes.TEMPEST_ABILITY_SCALE : 1.0)
			// heavy armour weighs the flight down (anything above chainmail)
			* com.jaymospowers.power.wind.WindArmor.speedFactor(player)
			// someone in your arms slows you a little
			* (com.jaymospowers.core.Carry.isCarrying(player) ? com.jaymospowers.core.Carry.FLIGHT_SPEED : 1.0);
		if (!this.state.fast()) {
			this.braking = false;
		}
		this.turnBonus = Fate.isAscended(player) ? TEMPEST_TURN_BONUS : 1.0;
		if (this.recoverTicks > 0) {
			this.recoverTicks--;
		}

		if (this.state == WindFlight.TUMBLE) {
			this.tumble(minecraft, player);
		} else if (!this.state.flying()) {
			// grounded: take-off is R only (Sneak + double Jump is the crawl's)
		} else if (!WindFlight.canFly(player) || player.isFallFlying()) {
			this.land(player, false);
		} else {
			this.collide(player);
			if (this.state == WindFlight.HOVER) {
				this.hover(minecraft, player, input);
			} else {
				this.cruise(minecraft, player, input, sneakTaps);
			}
			if (this.state.fast()) {
				this.ram(minecraft, player);
			}
			if (this.state.flying()) {
				player.setDeltaMovement(this.velocity);
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
		if (boosting && this.speed < this.top) {
			this.speed += CRUISE_ACCEL * (this.top / TOP_SPEED) * (1.0 - CRUISE_ACCEL_TAPER * this.speed / this.top);
		} else if (!this.locked) {
			this.speed -= CRUISE_DECEL;
		}
		if (input.backward()) {
			if (this.speed >= AIR_BRAKE_MIN_SPEED || this.braking && this.speed > HOVER_SPEED * 0.5) {
				// the air brake: palms out, a gust ahead, and the momentum bled away to a halt
				if (!this.braking) {
					player.level().playLocalSound(player, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.7F, 1.3F);
					WindCamera.shake(0.4F, 5);
				}
				this.braking = true;
				this.speed = this.speed * AIR_BRAKE_KEEP - CRUISE_BRAKE * AIR_BRAKE_PLAIN;
				this.brakeGust(player);
			} else {
				this.braking = false;
				this.speed -= CRUISE_BRAKE;
			}
			this.locked = false;
		} else {
			this.braking = false;
		}
		// inertia: the path only swings round toward the look direction so fast, and turning hard costs speed
		Vec3 look = player.getLookAngle();
		Vec3 dir = this.velocity.lengthSqr() > 1.0E-6 ? this.velocity.normalize() : look;
		double fractionNow = Mth.clamp(this.speed / this.top, 0.0, 1.0);
		// the turn rate falls away quickly with speed: half speed already turns at a third of the slow rate
		double maxTurn = Math.toRadians(Mth.lerp(Math.sqrt(Math.min(1.0, fractionNow)), CRUISE_TURN_RATE_SLOW, CRUISE_TURN_RATE_FAST) * this.turnBonus);
		double angle = Math.acos(Mth.clamp(dir.dot(look), -1.0, 1.0));
		if (fractionNow >= TUMBLE_MIN_FRACTION) {
			double t = Math.min(1.0, (fractionNow - TUMBLE_MIN_FRACTION) / (1.0 - TUMBLE_MIN_FRACTION));
			double limit = com.jaymospowers.core.Mastery.unlocked(player) >= com.jaymospowers.core.Mastery.ALL
				? Math.toRadians(MASTERED_TUMBLE_ANGLE)
				: Math.toRadians(Math.min(179.0, Mth.lerp(t, TUMBLE_ANGLE_SLOW, TUMBLE_ANGLE_FAST) * this.turnBonus));
			// pulling up too hard isn't a tumble, the nose just won't come up (see PULL_UP_RATE); only a turn sideways or down
			double tumbleAngle = angle;
			if (look.y > dir.y) {
				Vec3 flatDir = new Vec3(dir.x, 0.0, dir.z), flatLook = new Vec3(look.x, 0.0, look.z);
				tumbleAngle = flatDir.lengthSqr() < 1.0E-6 || flatLook.lengthSqr() < 1.0E-6 ? 0.0
					: Math.acos(Mth.clamp(flatDir.normalize().dot(flatLook.normalize()), -1.0, 1.0));
			}
			if (tumbleAngle > limit) {
				// wrenched round far harder than this speed allows: control is lost
				this.velocity = dir.scale(this.speed);
				this.startTumble(minecraft, player);
				return;
			}
		}
		if (angle > 1.0E-4) {
			double turned = Math.min(angle, maxTurn);
			Vec3 was = dir;
			dir = rotateToward(dir, look, angle, turned);
			// the nose only comes up so fast at speed: the momentum carries you on
			double pitchWas = Math.asin(Mth.clamp(was.y, -1.0, 1.0));
			double pitchNow = Math.asin(Mth.clamp(dir.y, -1.0, 1.0));
			double maxUp = Math.toRadians(Mth.lerp(Math.min(1.0, fractionNow), PULL_UP_RATE_SLOW, PULL_UP_RATE_FAST) * this.turnBonus);
			if (pitchNow - pitchWas > maxUp) {
				double pitch = pitchWas + maxUp;
				double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
				Vec3 horizontal = flat < 1.0E-4 ? new Vec3(was.x, 0.0, was.z).normalize() : new Vec3(dir.x / flat, 0.0, dir.z / flat);
				dir = horizontal.scale(Math.cos(pitch)).add(0.0, Math.sin(pitch), 0.0);
			}
			this.speed -= this.speed * CRUISE_TURN_DRAG * turned;
		}
		// and gravity along the path: diving gains speed (past the wind's own top, a little), climbing costs a little
		double before = this.speed;
		this.speed -= dir.y * (dir.y < 0.0 ? DIVE_GRAVITY : CLIMB_GRAVITY);
		// the wind's push only reaches the top speed; a dive may carry you beyond it (and any extra bleeds off slowly)
		double cap = this.top * DIVE_OVERSPEED;
		if (this.speed > this.top && dir.y > -0.05) {
			this.speed = Math.max(this.top, Math.min(this.speed, before) - CRUISE_DECEL * 2.0);
		}
		this.speed = Mth.clamp(this.speed, 0.0, cap);
		double fraction = this.speed / this.top;
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

	/** The air brake's gust, pushed out ahead from the hands (our own view; others get it from the server). */
	private void brakeGust(LocalPlayer player) {
		RandomSource random = player.getRandom();
		Vec3 look = player.getLookAngle();
		Vec3 hands = player.getEyePosition().add(look.scale(0.9)).add(0.0, -0.35, 0.0);
		for (int i = 0; i < AIR_BRAKE_CLOUDS; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, hands.x + random.nextGaussian() * 0.2, hands.y + random.nextGaussian() * 0.2,
				hands.z + random.nextGaussian() * 0.2, look.x * 0.5 + random.nextGaussian() * 0.08, look.y * 0.5 + random.nextGaussian() * 0.04,
				look.z * 0.5 + random.nextGaussian() * 0.08);
		}
		if (this.ticks % AIR_BRAKE_GUST_EVERY == 0) {
			player.level().addParticle(ParticleTypes.SMALL_GUST, hands.x + look.x, hands.y + look.y, hands.z + look.z, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Walls and floors stop you: drop the blocked part of the velocity (vanilla zeroed it on the player). The crash is
	 * the whole change in velocity, whichever way you hit (a dive into the ground at an angle counts in full).
	 */
	private void collide(LocalPlayer player) {
		Vec3 actual = player.getDeltaMovement();
		Vec3 was = this.velocity;
		boolean wall = false, floor = false;
		if (player.horizontalCollision) {
			double x = Math.abs(actual.x) < 1.0E-7 ? 0.0 : this.velocity.x;
			double z = Math.abs(actual.z) < 1.0E-7 ? 0.0 : this.velocity.z;
			wall = x != this.velocity.x || z != this.velocity.z;
			this.velocity = new Vec3(x, this.velocity.y, z);
		}
		if (player.verticalCollision && (this.velocity.y < 0.0 && player.onGround() || this.velocity.y > 0.0 && !player.onGround())) {
			floor = true;
			this.velocity = new Vec3(this.velocity.x, 0.0, this.velocity.z);
		}
		if (!wall && !floor) {
			return;
		}
		// a glancing blow sheds speed too: a dive's forward rush is lost on the ground as well
		if (floor && !wall && this.state.fast()) {
			this.velocity = this.velocity.scale(Math.max(0.0, 1.0 - Math.abs(was.y) / Math.max(1.0E-3, was.length())));
		}
		double lost = was.subtract(this.velocity).length();
		if (lost > (wall ? IMPACT_SPEED : IMPACT_SPEED * 1.5)) {
			this.impact(player, lost, wall, was);
		}
		if (this.state.fast()) {
			this.speed = Math.min(this.speed, this.velocity.length());
		}
	}

	/** Creatures you ploughed through lately (entity id → tick), so each counts once a pass. */
	private final java.util.Map<Integer, Integer> rammed = new java.util.HashMap<>();
	/** Flying faster than this (blocks/tick) through a creature rams it. */
	static final double RAM_MIN_SPEED = 0.7;
	/** How much of your speed a ram keeps. */
	static final double RAM_KEEP = 0.75;

	/** At speed, anything in your path is rammed: the server hurts and throws it, and it slows you. */
	private void ram(Minecraft minecraft, LocalPlayer player) {
		double speed = this.velocity.length();
		if (speed < RAM_MIN_SPEED) {
			return;
		}
		this.rammed.values().removeIf(t -> this.ticks - t > 10);
		net.minecraft.world.phys.AABB path = player.getBoundingBox().expandTowards(this.velocity).inflate(0.3);
		for (net.minecraft.world.entity.Entity e : player.level().getEntities(player, path,
			e -> e instanceof net.minecraft.world.entity.LivingEntity && e.isAlive() && !e.isSpectator() && !player.isPassengerOfSameVehicle(e))) {
			if (this.rammed.containsKey(e.getId())) {
				continue;
			}
			this.rammed.put(e.getId(), this.ticks);
			ClientPlayNetworking.send(new WindNet.Ram(e.getId(), (float)speed, this.velocity));
			this.speed *= RAM_KEEP;
			this.velocity = this.velocity.scale(RAM_KEEP);
			player.level().playLocalSound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
			WindCamera.shake((float)Math.min(2.0, speed), 8);
		}
	}

	// ---- tumbling ---------------------------------------------------------------------------------------------

	/** Caught in a Gravity user's Lift: a flyer is knocked into a tumble (no control) for at least {@code ticks}. */
	public void heldStun(Minecraft minecraft, LocalPlayer player, int ticks) {
		if (player != this.player || this.state == WindFlight.NONE) {
			return;
		}
		if (this.state != WindFlight.TUMBLE) {
			this.velocity = player.getDeltaMovement();
			this.startTumble(minecraft, player);
		}
		this.tumbleTicks = Math.max(this.tumbleTicks, ticks);
	}

	private void startTumble(Minecraft minecraft, LocalPlayer player) {
		this.state = WindFlight.TUMBLE;
		this.locked = false;
		this.tumbleTicks = TUMBLE_TICKS;
		this.speed = 0.0;
		player.setDeltaMovement(this.velocity);
		ClientLevel level = minecraft.level;
		if (level != null) {
			level.playLocalSound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playLocalSound(player, SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0F, 0.7F);
			for (int i = 0; i < 16; i++) {
				level.addParticle(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(),
					(level.getRandom().nextDouble() - 0.5) * 0.5, (level.getRandom().nextDouble() - 0.5) * 0.5, (level.getRandom().nextDouble() - 0.5) * 0.5);
			}
		}
		WindCamera.shake(3.0F, 20);
	}

	/** Out of control: momentum and gravity carry you, nothing you press matters, until you recover (or hit something). */
	private void tumble(Minecraft minecraft, LocalPlayer player) {
		if (!WindFlight.canFly(player) || player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			// came down still tumbling: that's a crash, and the fall counts as a fall
			this.state = WindFlight.NONE;
			this.tumbleTicks = 0;
			return;
		}
		Vec3 v = player.getDeltaMovement();
		// a tumbling body keeps more of its speed than vanilla air drag allows
		player.setDeltaMovement(v.x / 0.91 * TUMBLE_DRAG, v.y, v.z / 0.91 * TUMBLE_DRAG);
		if (this.tumbleTicks % 6 == 0) {
			WindCamera.shake(1.4F, 6);
			player.level().playLocalSound(player, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
		if (--this.tumbleTicks <= 0) {
			// control back: catch yourself into a hover, in the recovery pose
			this.state = WindFlight.HOVER;
			this.sinkArmed = false;
			this.velocity = player.getDeltaMovement().scale(0.5);
			player.setDeltaMovement(this.velocity);
			this.recoverTicks = RECOVER_TICKS;
			player.level().playLocalSound(player, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9F, 1.2F);
			ClientLevel level = minecraft.level;
			if (level != null) {
				level.addParticle(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 0.0, 0.0, 0.0);
			}
			WindCamera.shake(0.8F, 8);
		}
	}

	// ---- transitions ------------------------------------------------------------------------------------------

	/** R, or a double-tapped Jump while sneaking. */
	void toggle(Minecraft minecraft, LocalPlayer player) {
		if (player != this.player) {
			this.player = player;
			this.reset();
		}
		if (this.state == WindFlight.TUMBLE) {
			// no control while tumbling
			return;
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
				this.speed = Math.min(this.top, this.speed + strength);
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

	private void impact(LocalPlayer player, double lost, boolean wall, Vec3 heading) {
		// the server decides wall-crash damage (none in creative; the ground is ordinary fall damage) and, fast
		// enough, smashes what you hit
		ClientPlayNetworking.send(new WindNet.Impact((float)lost, wall, heading));
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
		if (this.state != this.sentState || this.locked != this.sentLocked || this.braking != this.sentBraking
			|| this.state.flying() && this.sinceSent >= RESYNC_TICKS) {
			this.sentState = this.state;
			this.sentLocked = this.locked;
			this.sentBraking = this.braking;
			this.sinceSent = 0;
			if (ClientPlayNetworking.canSend(WindNet.Flight.TYPE)) {
				ClientPlayNetworking.send(new WindNet.Flight(this.state.ordinal(), this.locked, this.braking));
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
