package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.PowerInput;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wind Control, server side. The flight itself runs on the client (player motion is client-authoritative); the
 * server follows the reported state, validates it, cancels fall damage, keeps the anti-cheat quiet and shows the
 * flight to everyone else (trail, sonic boom, take-off gust). The channelled abilities (G, V, B) are fully server-owned.
 */
public final class WindPower implements Power {
	public static final WindPower INSTANCE = new WindPower();
	public static final Identifier ID = JaymosPowers.id("wind");

	/** The flight state ({@link WindFlight} ordinal), synced to every client so others see the flying pose. Not saved. */
	public static final AttachmentType<Integer> FLIGHT = AttachmentRegistry.create(JaymosPowers.id("wind_flight_state"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final Identifier BARRIER_MODIFIER = JaymosPowers.id("wind_gale_barrier");

	// ---- ability tuning ---------------------------------------------------------------------------------------
	/** Tailwind: buff duration (ticks) at the weakest and full channel; dash speed (blocks/tick). */
	static final int TAILWIND_MIN_TICKS = 100, TAILWIND_MAX_TICKS = 400;
	static final float TAILWIND_DASH_MIN = 0.7F, TAILWIND_DASH_MAX = 1.5F;
	/** Gale Barrier: duration, projectile deflection radius, mob push radius and strength. */
	static final int BARRIER_MIN_TICKS = 120, BARRIER_MAX_TICKS = 360;
	static final double BARRIER_DEFLECT_RADIUS = 4.5;
	static final double BARRIER_PUSH_RADIUS = 6.0, BARRIER_PUSH_MIN = 0.9, BARRIER_PUSH_MAX = 1.8;
	/** Zephyr's Breath: radius; Slow Falling and Regeneration durations. */
	static final double ZEPHYR_RADIUS = 6.0;
	/** Descending slower than this (blocks/tick) while flying doesn't build up fall distance (vanilla's elytra rule). */
	static final double GLIDE_SAFE_SINK = 0.5;
	/**
	 * Hunger while flying, per tick: a base plus more per block/tick of speed. Sprinting costs about 0.028/tick, so a
	 * hover (0.03) is a bit hungrier and full sonic (0.105) burns through a full bar in under a minute.
	 */
	static final double FLIGHT_EXHAUSTION_BASE = 0.03;
	static final double FLIGHT_EXHAUSTION_PER_SPEED = 0.03;
	/** At or below this food level you're too weak to cruise (like sprinting); you can still hover. */
	public static final int CRUISE_MIN_FOOD = 6;
	/** Tailwind on the ground: a gust blast along the look direction instead of the buff. */
	static final double GUST_RANGE_MIN = 6.0, GUST_RANGE_MAX = 11.0;
	static final double GUST_HALF_ANGLE = 35.0;
	static final float GUST_DAMAGE_MIN = 4.0F, GUST_DAMAGE_MAX = 10.0F;
	static final double GUST_KNOCKBACK_MIN = 1.4, GUST_KNOCKBACK_MAX = 3.0;
	static final int ZEPHYR_FALL_MIN = 200, ZEPHYR_FALL_MAX = 600, ZEPHYR_REGEN_MIN = 100, ZEPHYR_REGEN_MAX = 240;
	/** Other players further away than this don't get the flight trail. */
	private static final double TRAIL_VIEW_DISTANCE = 128.0;

	private final Map<UUID, State> states = new HashMap<>();

	private WindPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Powers.register(INSTANCE);
		WindNet.register();
		ServerPlayNetworking.registerGlobalReceiver(WindNet.Flight.TYPE, (payload, ctx) -> INSTANCE.onFlightPacket(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WindNet.Impact.TYPE, (payload, ctx) -> INSTANCE.onImpact(ctx.player(), payload));
		// cooldowns outlive a relog (no reconnect-to-reset); everything else starts over
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			State s = INSTANCE.states.get(handler.player.getUUID());
			if (s != null) {
				s.flight = WindFlight.NONE;
				s.locked = false;
				s.fallProtected = false;
				s.channel = null;
				s.barrierTicks = 0;
				s.dashGrace = 0;
				s.lastPos = null;
				s.synced = false;
			}
		});
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0xBFF4EA;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.FEATHER);
	}

	/** Per-player server state. */
	private static final class State {
		WindFlight flight = WindFlight.NONE;
		boolean locked;
		/** Set while flying and until the player next lands: the server's floating/speed checks are skipped. */
		boolean fallProtected;
		int lastImpactTick = -1000;
		WindAbility channel;
		int channelTicks;
		final int[] cooldowns = new int[WindAbility.values().length];
		int barrierTicks;
		/** Ticks left of the post-dash movement-check grace. */
		int dashGrace;
		int lastBoomTick = -1000;
		Vec3 lastPos;
		/** False until this connection has been sent the cooldowns. */
		boolean synced;
	}

	private State state(ServerPlayer player) {
		return this.states.computeIfAbsent(player.getUUID(), uuid -> new State());
	}

	// ---- Power hooks ------------------------------------------------------------------------------------------

	@Override
	public void onGained(ServerPlayer player) {
		this.states.remove(player.getUUID());
		this.sendStatus(player, this.state(player), 0.0F, false);
	}

	@Override
	public void onLost(ServerPlayer player) {
		State s = this.states.remove(player.getUUID());
		player.removeAttached(FLIGHT);
		removeBarrierModifier(player);
		if (s != null && s.flight.flying()) {
			// the client also drops its flight when it sees the power go; this covers a slow attachment sync
			ServerPlayNetworking.send(player, new WindNet.Status(-1, 0, new int[WindAbility.values().length], 0.0F, true));
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		State s = this.states.get(player.getUUID());
		return s != null && (s.flight.flying() || s.fallProtected);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		State s = this.states.get(player.getUUID());
		return s != null && (s.flight.flying() || s.fallProtected || s.dashGrace > 0);
	}

	/**
	 * Crash damage grows with the square of the speed lost, like kinetic energy: {@code K × (lost² − safe²)}. Walls
	 * are less forgiving than the ground. Cruising (1 b/t) into a wall ≈ 6; 1.5 b/t ≈ 16; sonic (2.5 b/t) ≈ 48.
	 */
	static final float IMPACT_DAMAGE_K = 8.0F;
	static final float IMPACT_SAFE_WALL = 0.5F, IMPACT_SAFE_GROUND = 0.8F;
	/** The most speed an impact can report (a little over top speed), and the minimum ticks between two impacts. */
	static final float IMPACT_MAX_LOST = 3.0F;
	static final int IMPACT_SPACING = 6;

	/** A crash reported by the flyer's client (motion is client-side). Survival and adventure only. */
	private void onImpact(ServerPlayer player, WindNet.Impact payload) {
		State s = this.states.get(player.getUUID());
		// the ground is judged by ordinary fall damage; this is for walls
		if (!payload.wall() || s == null || !s.flight.flying() || !Powers.has(player, this) || !player.isAlive()
			|| player.isCreative() || player.isSpectator() || player.tickCount - s.lastImpactTick < IMPACT_SPACING) {
			return;
		}
		float lost = Mth.clamp(payload.lost(), 0.0F, IMPACT_MAX_LOST);
		float safe = payload.wall() ? IMPACT_SAFE_WALL : IMPACT_SAFE_GROUND;
		float damage = (lost * lost - safe * safe) * IMPACT_DAMAGE_K;
		if (damage < 1.0F) {
			return;
		}
		s.lastImpactTick = player.tickCount;
		ServerLevel level = player.level();
		player.hurtServer(level, payload.wall() ? player.damageSources().flyIntoWall() : player.damageSources().fall(), damage);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			damage >= 6.0F ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	@Override
	public void tick(ServerPlayer player) {
		State s = this.state(player);
		ServerLevel level = player.level();
		boolean statusDirty = !s.synced;
		s.synced = true;
		for (int i = 0; i < s.cooldowns.length; i++) {
			if (s.cooldowns[i] > 0) {
				s.cooldowns[i]--;
			}
		}
		if (s.dashGrace > 0) {
			s.dashGrace--;
		}

		if (!player.isAlive() || player.isSpectator()) {
			if (s.flight.flying()) {
				this.setFlight(player, s, WindFlight.NONE, false);
			}
			if (s.channel != null) {
				s.channel = null;
				statusDirty = true;
			}
			if (s.barrierTicks > 0) {
				s.barrierTicks = 0;
				removeBarrierModifier(player);
			}
			s.fallProtected = false;
			s.lastPos = null;
			if (statusDirty) {
				this.sendStatus(player, s, 0.0F, false);
			}
			return;
		}
		if (statusDirty) {
			this.sendStatus(player, s, 0.0F, false);
		}

		// ---- flight
		Vec3 pos = player.position();
		Vec3 moved = s.lastPos == null ? Vec3.ZERO : pos.subtract(s.lastPos);
		if (s.flight.flying()) {
			if (!WindFlight.canFly(player)) {
				this.setFlight(player, s, WindFlight.NONE, false);
				this.sendStatus(player, s, 0.0F, true);
			} else {
				s.fallProtected = true;
				// fall damage always applies; like an elytra, a controlled descent (slower than 0.5 b/t) isn't a fall
				if (player.getY() - player.yo > -GLIDE_SAFE_SINK) {
					player.fallDistance = Math.min(player.fallDistance, 1.0);
				}
				if (s.flight.fast() && s.lastPos != null && moved.lengthSqr() < 64.0) {
					this.trail(player, level, s, moved);
				}
				// flying is hungry work: hovering already burns a little more than sprinting, and it climbs with speed
				double speed = s.lastPos == null ? 0.0 : Math.min(moved.length(), 3.0);
				player.causeFoodExhaustion((float)(FLIGHT_EXHAUSTION_BASE + FLIGHT_EXHAUSTION_PER_SPEED * speed));
			}
		}
		if (s.fallProtected) {
			// (fall distance is only waived while actually flying, above; after that a fall counts as normal)
			if (!s.flight.flying() && (player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger())) {
				s.fallProtected = false;
			}
		}
		s.lastPos = pos;

		// ---- channelling
		if (s.channel != null) {
			s.channelTicks++;
			this.channelEffects(player, level, s);
			if (s.channelTicks >= WindAbility.MAX_CHANNEL || !PowerInput.isDown(player, s.channel.key)) {
				this.release(player, s);
			}
		}

		// ---- Gale Barrier
		if (s.barrierTicks > 0) {
			s.barrierTicks--;
			this.barrierTick(player, level, s);
			if (s.barrierTicks == 0) {
				removeBarrierModifier(player);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_DEATH, SoundSource.PLAYERS, 0.35F, 1.6F);
			}
		}
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		WindAbility ability = WindAbility.byKey(key);
		if (ability == null) {
			// R toggles the flight on the client, which reports it via wind_flight
			return;
		}
		State s = this.state(player);
		if (down) {
			if (s.channel != null) {
				return;
			}
			if (s.cooldowns[ability.ordinal()] > 0) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.25F, 0.6F);
				return;
			}
			s.channel = ability;
			s.channelTicks = 0;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.8F, 1.0F);
			this.sendStatus(player, s, 0.0F, false);
		} else if (s.channel == ability) {
			this.release(player, s);
		}
	}

	// ---- flight -----------------------------------------------------------------------------------------------

	private void onFlightPacket(ServerPlayer player, WindNet.Flight payload) {
		WindFlight next = WindFlight.byId(payload.state());
		if (next == null || !Powers.has(player, this)) {
			return;
		}
		State s = this.state(player);
		if (next.flying() && !WindFlight.canFly(player)) {
			this.setFlight(player, s, WindFlight.NONE, false);
			this.sendStatus(player, s, 0.0F, true);
			return;
		}
		WindFlight previous = s.flight;
		this.setFlight(player, s, next, next.fast() && payload.locked());
		ServerLevel level = player.level();
		if (previous == WindFlight.NONE && next.flying()) {
			// take-off gust, for everyone else (the flyer makes their own)
			this.particlesForOthers(player, level, ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9F, 1.1F);
		}
		if (next == WindFlight.SONIC && previous != WindFlight.SONIC && player.tickCount - s.lastBoomTick > 30) {
			s.lastBoomTick = player.tickCount;
			double x = player.getX(), y = player.getY() + 0.9, z = player.getZ();
			this.particlesForOthers(player, level, ParticleTypes.SONIC_BOOM, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			this.particlesForOthers(player, level, ParticleTypes.GUST_EMITTER_SMALL, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			this.particlesForOthers(player, level, ParticleTypes.CLOUD, x, y, z, 40, 0.6, 0.6, 0.6, 0.35);
			this.particlesForOthers(player, level, ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(player, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.7F);
			level.playSound(player, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.55F);
		}
	}

	private void setFlight(ServerPlayer player, State s, WindFlight flight, boolean locked) {
		s.flight = flight;
		s.locked = locked;
		if (flight.flying()) {
			s.fallProtected = true;
			Integer current = player.getAttached(FLIGHT);
			if (current == null || current != flight.ordinal()) {
				player.setAttached(FLIGHT, flight.ordinal());
			}
		} else if (flight == WindFlight.TUMBLE) {
			// everyone draws the tumble
			player.setAttached(FLIGHT, flight.ordinal());
		} else if (player.getAttached(FLIGHT) != null) {
			player.removeAttached(FLIGHT);
		}
	}

	/** The cloud trail and the whoosh, for everyone but the flyer (whose client draws its own). */
	private void trail(ServerPlayer player, ServerLevel level, State s, Vec3 moved) {
		boolean sonic = s.flight == WindFlight.SONIC;
		int steps = sonic ? 4 : 2;
		Vec3 back = moved.lengthSqr() > 1.0E-4 ? moved.normalize().scale(-0.8) : Vec3.ZERO;
		for (int i = 0; i < steps; i++) {
			double t = i / (double)steps;
			double x = player.getX() - moved.x * t + back.x;
			double y = player.getY() + 0.9 - moved.y * t + back.y;
			double z = player.getZ() - moved.z * t + back.z;
			this.particlesForOthers(player, level, ParticleTypes.CLOUD, x, y, z, 1, 0.12, 0.12, 0.12, 0.01);
		}
		if (sonic && player.tickCount % 3 == 0) {
			this.particlesForOthers(player, level, ParticleTypes.SMALL_GUST, player.getX() + back.x * 2, player.getY() + 0.9 + back.y * 2,
				player.getZ() + back.z * 2, 1, 0.2, 0.2, 0.2, 0.0);
		}
		if (player.tickCount % (sonic ? 12 : 20) == 0) {
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS,
				sonic ? 1.2F : 0.8F, sonic ? 1.3F : 1.0F);
		}
	}

	private <T extends ParticleOptions> void particlesForOthers(ServerPlayer flyer, ServerLevel level, T particle, double x, double y, double z,
		int count, double dx, double dy, double dz, double speed) {
		for (ServerPlayer other : level.players()) {
			if (other != flyer && other.distanceToSqr(x, y, z) < TRAIL_VIEW_DISTANCE * TRAIL_VIEW_DISTANCE) {
				level.sendParticles(other, particle, true, false, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	// ---- channelled abilities ---------------------------------------------------------------------------------

	private void channelEffects(ServerPlayer player, ServerLevel level, State s) {
		float f = s.channelTicks / (float)WindAbility.MAX_CHANNEL;
		// two wisps spiralling up and in around the body
		for (int i = 0; i < 2; i++) {
			double angle = s.channelTicks * 0.45 + i * Math.PI;
			double radius = 1.2 - 0.5 * f;
			double height = 0.15 + (s.channelTicks * 0.06 + i * 0.9) % 1.9;
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			// count 0: (dx, dy, dz) is the velocity — along the circle, a little upward
			level.sendParticles(ParticleTypes.CLOUD, x, player.getY() + height, z, 0, -Math.sin(angle) * 0.18, 0.02, Math.cos(angle) * 0.18, 1.0);
		}
		if (s.channelTicks % 4 == 0) {
			ParticleOptions accent = switch (s.channel) {
				case TAILWIND -> ParticleTypes.SMALL_GUST;
				case GALE_BARRIER -> ParticleTypes.WHITE_SMOKE;
				case ZEPHYR -> ParticleTypes.HAPPY_VILLAGER;
			};
			level.sendParticles(accent, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.5, 0.6, 0.5, 0.0);
			// a chime that climbs as the wind gathers
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.5F, 0.5F + 1.5F * f);
		}
		if (s.channelTicks % 10 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS,
				0.35F, 0.7F + 0.8F * f);
		}
	}

	private void release(ServerPlayer player, State s) {
		WindAbility ability = s.channel;
		if (ability == null) {
			return;
		}
		s.channel = null;
		float f = Mth.clamp(s.channelTicks / (float)WindAbility.MAX_CHANNEL, 0.1F, 1.0F);
		s.cooldowns[ability.ordinal()] = ability.cooldown;
		ServerLevel level = player.level();
		float dash = 0.0F;
		switch (ability) {
			case TAILWIND -> {
				if (player.onGround() && !s.flight.flying()) {
					this.gust(player, level, f);
					break;
				}
				int ticks = Mth.lerpInt(f, TAILWIND_MIN_TICKS, TAILWIND_MAX_TICKS);
				int amp = f >= 0.5F ? 1 : 0;
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, amp), player);
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks, amp), player);
				dash = Mth.lerp(f, TAILWIND_DASH_MIN, TAILWIND_DASH_MAX);
				s.dashGrace = 40;
				s.fallProtected = true;
				level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
			case GALE_BARRIER -> {
				s.barrierTicks = Mth.lerpInt(f, BARRIER_MIN_TICKS, BARRIER_MAX_TICKS);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, s.barrierTicks, f >= 0.75F ? 1 : 0), player);
				AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (knockback != null) {
					knockback.addOrUpdateTransientModifier(new AttributeModifier(BARRIER_MODIFIER, 0.5 + 0.5 * f, AttributeModifier.Operation.ADD_VALUE));
				}
				this.pushMobs(player, level, f);
				level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.8F);
			}
			case ZEPHYR -> {
				int fall = Mth.lerpInt(f, ZEPHYR_FALL_MIN, ZEPHYR_FALL_MAX);
				int regen = Mth.lerpInt(f, ZEPHYR_REGEN_MIN, ZEPHYR_REGEN_MAX);
				int regenAmp = f >= 1.0F ? 1 : 0;
				AABB area = player.getBoundingBox().inflate(ZEPHYR_RADIUS);
				for (Player target : level.getEntitiesOfClass(Player.class, area,
					p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(player) <= ZEPHYR_RADIUS * ZEPHYR_RADIUS)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, fall, 0), player);
					target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, regenAmp), player);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.4, 0.6, 0.4, 0.0);
					level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.2, target.getZ(), 8, 0.4, 0.1, 0.4, 0.03);
				}
				// a soft ring of breath rolling outward
				for (int i = 0; i < 24; i++) {
					double angle = i * Math.PI * 2.0 / 24.0;
					level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(angle) * 0.6, player.getY() + 0.3, player.getZ() + Math.sin(angle) * 0.6,
						0, Math.cos(angle) * 0.35, 0.01, Math.sin(angle) * 0.35, 1.0);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.8F, 1.6F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
			}
		}
		this.sendStatus(player, s, dash, false);
	}

	/** Tailwind released on the ground: a cone of wind blasts everything in front away, hurting it. */
	private void gust(ServerPlayer player, ServerLevel level, float f) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double range = Mth.lerp(f, GUST_RANGE_MIN, GUST_RANGE_MAX);
		float damage = Mth.lerp(f, GUST_DAMAGE_MIN, GUST_DAMAGE_MAX);
		double knockback = Mth.lerp(f, GUST_KNOCKBACK_MIN, GUST_KNOCKBACK_MAX);
		double minDot = Math.cos(Math.toRadians(GUST_HALF_ANGLE));
		AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.6);
		for (Entity target : level.getEntities(player, area, e -> e.isAlive() && !e.isSpectator() && e != player.getVehicle())) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > range || distance < 1.0E-3 || to.scale(1.0 / distance).dot(look) < minDot) {
				continue;
			}
			// full strength up close, half at the edge of the blast
			double falloff = 1.0 - 0.5 * distance / range;
			if (target instanceof LivingEntity living) {
				living.hurtServer(level, player.damageSources().windCharge(player, player), (float)(damage * falloff));
				double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				if (resist <= 0.0) {
					continue;
				}
				Vec3 push = look.horizontal().normalize().scale(knockback * falloff * resist);
				living.push(push.x, 0.45 * resist + Math.max(0.0, look.y) * knockback * 0.5, push.z);
				living.syncVelocity = true;
			} else if (target instanceof Projectile projectile) {
				projectile.setDeltaMovement(look.scale(1.5));
				projectile.syncVelocity = true;
			}
		}
		// the blast itself: gusts rolling out along the cone
		for (int i = 1; i <= 5; i++) {
			Vec3 at = eye.add(look.scale(range * i / 5.0));
			level.sendParticles(ParticleTypes.GUST, at.x, at.y - 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y - 0.3, at.z, 6, 0.4 * i / 5.0, 0.3 * i / 5.0, 0.4 * i / 5.0, 0.05);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.4F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	private void pushMobs(ServerPlayer player, ServerLevel level, float f) {
		double strength = Mth.lerp(f, BARRIER_PUSH_MIN, BARRIER_PUSH_MAX);
		AABB area = player.getBoundingBox().inflate(BARRIER_PUSH_RADIUS);
		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) <= BARRIER_PUSH_RADIUS * BARRIER_PUSH_RADIUS)) {
			Vec3 away = new Vec3(mob.getX() - player.getX(), 0.0, mob.getZ() - player.getZ());
			if (away.lengthSqr() < 1.0E-4) {
				away = player.getLookAngle().horizontal();
			}
			double resist = 1.0 - mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			if (resist <= 0.0) {
				continue;
			}
			// weaker further out
			double falloff = 1.0 - 0.5 * Math.sqrt(mob.distanceToSqr(player)) / BARRIER_PUSH_RADIUS;
			Vec3 push = away.normalize().scale(strength * falloff * resist);
			mob.push(push.x, 0.35 * resist, push.z);
			level.sendParticles(ParticleTypes.SMALL_GUST, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	private void barrierTick(ServerPlayer player, ServerLevel level, State s) {
		Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		AABB area = player.getBoundingBox().inflate(BARRIER_DEFLECT_RADIUS);
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area, p -> p.isAlive() && p.getOwner() != player)) {
			Vec3 velocity = projectile.getDeltaMovement();
			Vec3 toPlayer = center.subtract(projectile.position());
			if (velocity.lengthSqr() < 0.01 || velocity.dot(toPlayer) <= 0.0 || toPlayer.lengthSqr() > BARRIER_DEFLECT_RADIUS * BARRIER_DEFLECT_RADIUS) {
				continue;
			}
			projectile.deflect((p, deflector, random, power) -> {
				// blown back out the way it came, spread a little and lifted
				Vec3 away = p.position().subtract(center).normalize();
				double speed = Math.max(0.5, p.getDeltaMovement().length() * 0.8);
				p.setDeltaMovement(away.scale(speed).add((random.nextDouble() - 0.5) * 0.2, 0.15, (random.nextDouble() - 0.5) * 0.2));
				p.needsSync = true;
			}, player, EntityReference.of((Entity)player), false, 1.0);
			level.sendParticles(ParticleTypes.SMALL_GUST, projectile.getX(), projectile.getY(), projectile.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		// a faint swirl so everyone can see the barrier is up
		if (s.barrierTicks % 3 == 0) {
			double angle = player.tickCount * 0.6;
			for (int i = 0; i < 3; i++) {
				double a = angle + i * Math.PI * 2.0 / 3.0;
				level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(a) * 1.0, player.getY() + 0.3 + i * 0.6, player.getZ() + Math.sin(a) * 1.0,
					0, -Math.sin(a) * 0.12, 0.0, Math.cos(a) * 0.12, 1.0);
			}
		}
	}

	private static void removeBarrierModifier(ServerPlayer player) {
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.removeModifier(BARRIER_MODIFIER);
		}
	}

	private void sendStatus(ServerPlayer player, State s, float dash, boolean stopFlight) {
		ServerPlayNetworking.send(player, new WindNet.Status(s.channel == null ? -1 : s.channel.ordinal(), s.channelTicks, s.cooldowns.clone(), dash, stopFlight));
	}
}
