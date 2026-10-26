package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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

	/** (0.9.26) Tags Jaymo's Kingdoms' tornadoes read: a Wind user, and their last Cyclone Lance (+ the game time). */
	public static final String KINGDOMS_TAG = "jaymospowers_wind", LANCE_TAG = "jaymospowers_wind_lance:";

	/** The air brake as others see it: clouds ahead of the palms each tick, and a puff of wind every few ticks. */
	private static final int BRAKE_CLOUDS = 5, BRAKE_GUST_EVERY = 4;
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
	/** Fire the Tailwind's take-off snuffs around you, and the Gale Barrier around you while it's up (blocks). */
	static final double TAILWIND_DOUSE_RADIUS = 3.0, BARRIER_DOUSE_RADIUS = 2.5;
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
	/**
	 * Tailwind on the ground: a gust blast along the look direction instead of the buff, growing with the charge (up
	 * to 5 s). A full charge hits like TNT: point-blank damage {@code GUST_DAMAGE_MAX} (a TNT blast's 57), falling off
	 * along TNT's curve to the blast's edge, with TNT-strength knockback; it costs a 3-minute cooldown.
	 */
	static final double GUST_RANGE_MIN = 6.0, GUST_RANGE_MAX = 12.0;
	static final double GUST_HALF_ANGLE = 35.0;
	static final float GUST_DAMAGE_MIN = 4.0F, GUST_DAMAGE_MAX = 57.0F;
	static final double GUST_KNOCKBACK_MIN = 0.6, GUST_KNOCKBACK_MAX = 1.8;
	static final int GUST_COOLDOWN_MIN = 400, GUST_COOLDOWN_MAX = 3600;
	/** A charge at least this full is a full blast (explosion and all). */
	static final float GUST_FULL = 0.95F;
	static final int ZEPHYR_FALL_MIN = 200, ZEPHYR_FALL_MAX = 600, ZEPHYR_REGEN_MIN = 100, ZEPHYR_REGEN_MAX = 240;
	/** Stormbound Zephyr's Breath: the lightning seeks a hostile mob this far away, else strikes this far ahead. */
	static final double ZEPHYR_BOLT_SEEK = 12.0, ZEPHYR_BOLT_AHEAD = 6.0;
	/** Other players further away than this don't get the flight trail. */
	private static final double TRAIL_VIEW_DISTANCE = 128.0;

	private final Map<UUID, State> states = new HashMap<>();

	private WindPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Powers.register(INSTANCE);
		WindNet.register();
		WindModes.register();
		WindTempest.init();
		WindStaff.register();
		WindStaffSurge.register();
		WindDagger.register();
		WindKick.init();
		WindCyclone.init();
		WindJet.init();
		com.jaymospowers.power.wind.aether.WindAether.init();
		WindDebug.register();
		// (0.17.2) what a gifted villager does with Wind
		WindGift.init();
		ServerPlayNetworking.registerGlobalReceiver(WindNet.Flight.TYPE, (payload, ctx) -> INSTANCE.onFlightPacket(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WindNet.Impact.TYPE, (payload, ctx) -> INSTANCE.onImpact(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WindNet.Ram.TYPE, (payload, ctx) -> INSTANCE.onRam(ctx.player(), payload));
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
			WindStorm.forget(handler.player);
			WindWeather.forget(handler.player);
			WindFall.forget(handler.player);
		});
	}

	@Override
	public Identifier id() {
		return ID;
	}

	/** Flight first; the channelled abilities after it, with the charged Tailwind (a TNT blast at full) last. */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_3, PowerKey.ABILITY_4, PowerKey.ABILITY_2};
	}

	@Override
	public int color() {
		return 0xBFF4EA;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.FEATHER);
	}

	/** Stormbound's Call Lightning (B) cooldown. */
	static final int CALL_LIGHTNING_COOLDOWN = 120;
	/** A flyer using the air brake (S at speed): others see the palms-out gust. */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> BRAKING = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(
		JaymosPowers.id("wind_braking"), builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL,
			net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	/** Per-player server state. */
	private static final class State {
		WindFlight flight = WindFlight.NONE;
		boolean locked;
		/** Set while flying and until the player next lands: the server's floating/speed checks are skipped. */
		boolean fallProtected;
		int lastImpactTick = -1000;
		int lastSmashTick = -1000;
		WindAbility channel;
		int channelTicks;
		/** Ticks to a full charge of this channel (longer in the cold, see {@link WindCold}). */
		int channelMax = 1;
		final int[] cooldowns = new int[WindAbility.values().length];
		int barrierTicks;
		/** Ticks left of the post-dash movement-check grace. */
		int dashGrace;
		int lastBoomTick = -1000;
		Vec3 lastPos;
		/** False until this connection has been sent the cooldowns. */
		boolean synced;
		/** player.tickCount when last seen in flight (breeze kills count for a moment after). */
		int lastFlightTick = -100000;
		/** Cyclone Lance's cooldown. */
		int lanceCooldown;
		/** The ground Gust's own cooldown (G on the ground); Tailwind (G in the air) keeps the ability's slot. */
		int gustCooldown;
		/** The eased weather factor, this tick. */
		float weather = 1.0F;
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

	/** (0.15.31) Downed: what was running ends. */
	@Override
	public void onDowned(ServerPlayer player) {
		// the cyclone and the jet stop (flight ends by WindFlight.canFly, next tick)
		WindCyclone.stop(player);
		WindJet.stop(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		WindCyclone.stop(player);
		WindJet.stop(player);
		player.removeTag(KINGDOMS_TAG);
		forgetLance(player);
		State s = this.states.remove(player.getUUID());
		player.removeAttached(FLIGHT);
		player.removeAttached(BRAKING);
		WindModes.clear(player);
		removeBarrierModifier(player);
		WindStorm.clear(player);
		WindStorm.forget(player);
		player.removeAttached(WindStorm.STORMBOUND_SINCE);
		WindTempest.setBreezeKills(player, 0);
		if (s != null && s.flight.flying()) {
			// the client also drops its flight when it sees the power go; this covers a slow attachment sync
			ServerPlayNetworking.send(player, new WindNet.Status(-1, 0, 1, new int[WindAbility.values().length], 0.0F, true, 0, 0));
		}
	}

	@Override
	public void onAscended(ServerPlayer player) {
		this.sendStatus(player, this.state(player), 0.0F, false);
	}

	@Override
	public void onCorrupted(ServerPlayer player) {
		// the three days start now
		player.setAttached(WindStorm.STORMBOUND_SINCE, player.level().getServer().overworld().getGameTime());
		// the sky answers
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.6F);
	}

	@Override
	public void onPurified(ServerPlayer player) {
		WindStorm.clear(player);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.5F);
	}

	/** Cyclone Lance (C, ascended only): an instant spear of wind along the look direction. */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (!down || !Fate.isAscended(player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		State s = this.state(player);
		if (s.lanceCooldown > 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.25F, 0.6F);
			return;
		}
		s.lanceCooldown = WindCold.cooldown(player, WindTempest.LANCE_COOLDOWN);
		WindFumble.onCast(player);
		WindStorm.onCast(player);
		WindModes.onCast(player);
		WindTempest.lance(player, s.weather);
		// (0.9.26) Jaymo's Kingdoms reads this: a lance fired into a tornado shoves it off course
		forgetLance(player);
		player.addTag(LANCE_TAG + player.level().getGameTime());
		this.sendStatus(player, s, 0.0F, false);
	}

	private static void forgetLance(ServerPlayer player) {
		player.entityTags().stream().filter(t -> t.startsWith(LANCE_TAG)).toList().forEach(player::removeTag);
	}

	/** The Power Mode key (Z): the Stormbound / Tempest mode (or both), once the power has either form. */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (down) {
			WindModes.toggle(player);
		}
	}

	/** In wind flight now, or within the last {@code ticks}. */
	boolean flewRecently(ServerPlayer player, int ticks) {
		State s = this.states.get(player.getUUID());
		return s != null && (s.flight.flying() || player.tickCount - s.lastFlightTick <= ticks);
	}

	/** (0.13.35) In wind flight right now (for {@code WindAether}'s rise into the Aether). */
	public static boolean flying(ServerPlayer player) {
		State s = INSTANCE.states.get(player.getUUID());
		return s != null && s.flight.flying();
	}

	/** Debug: every cooldown back to zero. */
	void clearCooldowns(ServerPlayer player) {
		State s = this.state(player);
		java.util.Arrays.fill(s.cooldowns, 0);
		s.lanceCooldown = 0;
		s.gustCooldown = 0;
		this.sendStatus(player, s, 0.0F, false);
	}

	/** X: the Cyclone comes with the first ability (the Tempest Kick waits for everything mastered). */
	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return !"special".equals(slot) || com.jaymospowers.core.Mastery.unlocked(player) >= 1;
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
		if (s != null && s.flight.flying() && Powers.has(player, this) && player.isAlive() && !player.isSpectator()
			&& player.tickCount - s.lastSmashTick >= IMPACT_SPACING) {
			// a crash at speed breaks what you hit, like any hard impact (see Debris.smash)
			float hit = Mth.clamp(payload.lost(), 0.0F, IMPACT_MAX_LOST);
			if (hit >= com.jaymospowers.power.web.WebImpacts.SMASH_SPEED) {
				s.lastSmashTick = player.tickCount;
				// where you hit: ahead of you along the way you were going (below you, into the ground)
				net.minecraft.world.phys.Vec3 dir = payload.dir().lengthSqr() > 1.0E-6 ? payload.dir().normalize() : player.getLookAngle();
				net.minecraft.world.phys.Vec3 at = payload.wall()
					? player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).add(new net.minecraft.world.phys.Vec3(dir.x, 0.0, dir.z).normalize().scale(0.9))
					: player.position().add(dir.x * 0.6, -0.5, dir.z * 0.6);
				com.jaymospowers.core.Debris.smash(player.level(), at, hit, player);
			}
		}
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

	/**
	 * Ploughing through a creature at speed: {@code RAM_DAMAGE_K} × speed² (at most {@code RAM_DAMAGE_MAX}), and it's
	 * thrown along your path. Each creature only once every {@code RAM_SPACING} ticks.
	 */
	static final float RAM_DAMAGE_K = 6.0F, RAM_DAMAGE_MAX = 36.0F;
	static final int RAM_SPACING = 10;
	private final Map<Integer, Long> rammed = new HashMap<>();

	private void onRam(ServerPlayer player, WindNet.Ram payload) {
		State s = this.states.get(player.getUUID());
		ServerLevel level = player.level();
		if (s == null || !s.flight.fast() || !Powers.has(player, this) || !player.isAlive() || player.isSpectator()
			|| !(level.getEntity(payload.entity()) instanceof net.minecraft.world.entity.LivingEntity target) || !target.isAlive()) {
			return;
		}
		float speed = Mth.clamp(payload.speed(), 0.0F, IMPACT_MAX_LOST);
		if (target.distanceTo(player) > 4.0F + speed * 2.0F) {
			return;
		}
		long now = level.getGameTime();
		Long last = this.rammed.get(target.getId());
		if (last != null && now - last < RAM_SPACING) {
			return;
		}
		this.rammed.put(target.getId(), now);
		if (this.rammed.size() > 64) {
			this.rammed.values().removeIf(t -> now - t > RAM_SPACING);
		}
		float damage = Math.min(RAM_DAMAGE_MAX, RAM_DAMAGE_K * speed * speed);
		net.minecraft.world.phys.Vec3 dir = payload.dir().lengthSqr() > 1.0E-6 ? payload.dir().normalize() : player.getLookAngle();
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(speed * 0.9)).add(0.0, 0.35, 0.0));
		target.syncVelocity = true;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.GUST, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		if (speed >= com.jaymospowers.power.web.WebImpacts.SMASH_SPEED) {
			level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
		}
	}

	/** A wind can't keep hold of a shield: its own wind tears it out of the hand, into the pack (or onto the ground). */
	private static void blowShieldAway(ServerPlayer player, ServerLevel level) {
		for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
			net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
			// (0.13.32) a quarterstaff blocks too now, but it's a staff, not a shield: the wind leaves it be
			if (held.isEmpty() || !held.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) || WindStaff.isStaff(held)) {
				continue;
			}
			net.minecraft.world.item.ItemStack shield = held.copy();
			player.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY);
			boolean stowed = false;
			for (int i = 9; i < 36 && !stowed; i++) {
				if (player.getInventory().getItem(i).isEmpty()) {
					player.getInventory().setItem(i, shield);
					stowed = true;
				}
			}
			if (!stowed) {
				player.drop(shield, true, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.wind.shield_blown")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		State s = this.state(player);
		// (0.9.26) Jaymo's Kingdoms reads this: a Wind user rides a tornado's winds instead of being thrown
		player.addTag(KINGDOMS_TAG);
		ServerLevel level = player.level();
		blowShieldAway(player, level);
		WindJet.tick(player);
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
		if (s.lanceCooldown > 0) {
			s.lanceCooldown--;
		}
		if (s.gustCooldown > 0) {
			s.gustCooldown--;
		}
		s.weather = WindWeather.tick(player);
		WindCyclone.tick(player);

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
		WindStorm.tick(player, s.flight, s.channel != null, s.barrierTicks > 0);
		WindModes.tick(player);
		// the air brake: a gust pushed out ahead from the palms, for everyone else to see
		if (player.getAttached(BRAKING) != null) {
			if (!s.flight.flying()) {
				player.removeAttached(BRAKING);
			} else {
				Vec3 ahead = player.getEyePosition().add(player.getLookAngle().scale(1.4));
				this.particlesForOthers(player, level, ParticleTypes.CLOUD, ahead.x, ahead.y - 0.3, ahead.z, BRAKE_CLOUDS, 0.3, 0.3, 0.3, 0.08);
				if (player.tickCount % BRAKE_GUST_EVERY == 0) {
					this.particlesForOthers(player, level, ParticleTypes.SMALL_GUST, ahead.x, ahead.y - 0.3, ahead.z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			}
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
				s.lastFlightTick = player.tickCount;
				// fall damage always applies; like an elytra, a controlled descent (slower than 0.5 b/t) isn't a fall
				if (player.getY() - player.yo > -GLIDE_SAFE_SINK) {
					player.fallDistance = Math.min(player.fallDistance, 1.0);
				}
				if (s.flight.fast() && s.lastPos != null && moved.lengthSqr() < 64.0) {
					this.trail(player, level, s, moved);
				}
				// flying is hungry work: hovering already burns a little more than sprinting, and it climbs with speed
				// (the Tempest rides the wind for free)
				if (!Fate.isAscended(player)) {
					double speed = s.lastPos == null ? 0.0 : Math.min(moved.length(), 3.0);
					player.causeFoodExhaustion((float)(FLIGHT_EXHAUSTION_BASE + FLIGHT_EXHAUSTION_PER_SPEED * speed)
						* (com.jaymospowers.core.Carry.isCarrying(player) ? com.jaymospowers.core.Carry.FLIGHT_HUNGER : 1.0F));
				}
				WindFumble.flightTick(player);
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
			if (s.channelTicks >= s.channelMax || !PowerInput.isDown(player, s.channel.key)) {
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
			if (this.coolingDown(player, s, ability)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.25F, 0.6F);
				return;
			}
			s.channel = ability;
			s.channelTicks = 0;
			// the cold makes the wind slow to gather
			s.channelMax = WindCold.windup(player, ability.maxChannel);
			WindStorm.onChannel(player);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.8F, 1.0F);
			this.sendStatus(player, s, 0.0F, false);
		} else if (s.channel == ability) {
			this.release(player, s);
		}
	}

	/** Whether G (or V, B) can't be channelled yet. G is refused only if neither of its halves could go off. */
	private boolean coolingDown(ServerPlayer player, State s, WindAbility ability) {
		if (ability != WindAbility.TAILWIND) {
			return s.cooldowns[ability.ordinal()] > 0;
		}
		boolean grounded = player.onGround() && !s.flight.flying();
		return s.cooldowns[ability.ordinal()] > 0 && (!grounded || s.gustCooldown > 0);
	}

	// ---- flight -----------------------------------------------------------------------------------------------

	private void onFlightPacket(ServerPlayer player, WindNet.Flight payload) {
		WindFlight next = WindFlight.byId(payload.state());
		if (next == null || !Powers.has(player, this)) {
			return;
		}
		State s = this.state(player);
		// (0.15.54) lying limp, only a Wind user who has mastered enough gets back into the air (see WindFall)
		if (next.flying() && (!WindFlight.canFly(player) || !s.flight.flying() && !WindFall.canTakeOff(player))) {
			this.setFlight(player, s, WindFlight.NONE, false);
			this.sendStatus(player, s, 0.0F, true);
			return;
		}
		WindFlight previous = s.flight;
		this.setFlight(player, s, next, next.fast() && payload.locked());
		if (payload.braking() && next.flying()) {
			if (player.getAttached(BRAKING) == null) {
				player.setAttached(BRAKING, true);
				player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.8F, 1.3F);
			}
		} else if (player.getAttached(BRAKING) != null) {
			player.removeAttached(BRAKING);
		}
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
			com.jaymospowers.core.Shakes.around(level, new net.minecraft.world.phys.Vec3(x, y, z), 40.0, 1.4F, com.jaymospowers.core.Shakes.ticksFor(1.4F), player);
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
		float f = Math.min(1.0F, s.channelTicks / (float)Math.max(1, s.channelMax));
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
				case ZEPHYR -> Fate.isCorrupted(player) ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.HAPPY_VILLAGER;
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
		float f = Mth.clamp(s.channelTicks / (float)Math.max(1, s.channelMax), 0.1F, 1.0F);
		// G's two halves cool down apart: on the ground the Gust (if it's gathered again), else the Tailwind
		boolean gust = ability == WindAbility.TAILWIND && player.onGround() && !s.flight.flying() && s.gustCooldown <= 0;
		if (ability == WindAbility.TAILWIND && !gust && s.cooldowns[ability.ordinal()] > 0) {
			// (took off mid-channel with only the Gust ready, or the like): nothing to release
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.25F, 0.6F);
			this.sendStatus(player, s, 0.0F, false);
			return;
		}
		if (!gust) {
			s.cooldowns[ability.ordinal()] = WindCold.cooldown(player, ability.cooldown);
		}
		ServerLevel level = player.level();
		WindFumble.onCast(player);
		// the weather governs the wind's strength (and the Tempest mode adds to it)
		float w = s.weather * WindModes.abilityScale(player);
		boolean stormbound = Fate.isCorrupted(player);
		WindStorm.onCast(player);
		WindModes.onCast(player);
		float dash = 0.0F;
		switch (ability) {
			case TAILWIND -> {
				if (gust) {
					this.douseGust(player, level, f);
					if (stormbound) {
						WindStorm.strike(player, this.gustImpact(player, f));
					}
					this.gust(player, level, f, w);
					// the bigger the blast, the longer the wind takes to gather again: up to 3 minutes for a full charge
					s.gustCooldown = WindCold.cooldown(player, Mth.lerpInt(f * f, GUST_COOLDOWN_MIN, GUST_COOLDOWN_MAX));
					break;
				}
				WindDouse.douse(level, player.position().add(0.0, 1.0, 0.0), TAILWIND_DOUSE_RADIUS, player);
				if (stormbound) {
					// the bolt falls where you launched from
					WindStorm.strike(player, player.position());
				}
				int ticks = (int)(Mth.lerpInt(f, TAILWIND_MIN_TICKS, TAILWIND_MAX_TICKS) * w);
				int amp = f >= 0.5F ? 1 : 0;
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, amp), player);
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks, amp), player);
				dash = Mth.lerp(f, TAILWIND_DASH_MIN, TAILWIND_DASH_MAX) * w;
				s.dashGrace = 40;
				s.fallProtected = true;
				level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
			case GALE_BARRIER -> {
				WindDouse.douse(level, player.position().add(0.0, 1.0, 0.0), BARRIER_PUSH_RADIUS, player);
				if (stormbound) {
					LivingEntity nearest = this.nearestEnemy(player, level, BARRIER_PUSH_RADIUS);
					WindStorm.strike(player, nearest != null ? nearest.position() : player.position());
				}
				s.barrierTicks = (int)(Mth.lerpInt(f, BARRIER_MIN_TICKS, BARRIER_MAX_TICKS) * w);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, s.barrierTicks, f >= 0.75F ? 1 : 0), player);
				AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (knockback != null) {
					knockback.addOrUpdateTransientModifier(new AttributeModifier(BARRIER_MODIFIER, 0.5 + 0.5 * f, AttributeModifier.Operation.ADD_VALUE));
				}
				this.pushMobs(player, level, f, w * WindStorm.galeMultiplier(player));
				level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.8F);
				com.jaymospowers.core.Shakes.heavy(player, player.position(), 10.0, 0.7F);
			}
			case ZEPHYR -> {
				if (stormbound && WindModes.thunderPower(level) > 0.0F) {
					// Stormbound in the rain has no breath to give: B calls the lightning down instead (under a clear sky
					// it's the Tempest's breath again)
					s.cooldowns[ability.ordinal()] = WindModes.callLightning(player, f, WindModes.abilityScale(player))
						? WindCold.cooldown(player, CALL_LIGHTNING_COOLDOWN) : 20;
					break;
				}
				WindDouse.douse(level, player.position().add(0.0, 1.0, 0.0), ZEPHYR_RADIUS, player);
				int fall = (int)(Mth.lerpInt(f, ZEPHYR_FALL_MIN, ZEPHYR_FALL_MAX) * w);
				int regen = (int)(Mth.lerpInt(f, ZEPHYR_REGEN_MIN, ZEPHYR_REGEN_MAX) * w);
				int regenAmp = f >= 1.0F ? 1 : 0;
				AABB area = player.getBoundingBox().inflate(ZEPHYR_RADIUS);
				for (Player target : level.getEntitiesOfClass(Player.class, area,
					p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(player) <= ZEPHYR_RADIUS * ZEPHYR_RADIUS)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, fall, 0), player);
					target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, regenAmp), player);
					com.jaymospowers.core.Fractures.startMend(target, com.jaymospowers.core.Fractures.ZEPHYR_MEND);
					// a symbiote mid-takeover feels the breath (WebSymbioteStrains: purifying)
					com.jaymospowers.power.web.WebSymbioteStrains.purifyHeal(player, target, com.jaymospowers.power.web.WebSymbioteStrains.Half.WIND);
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
	private void gust(ServerPlayer player, ServerLevel level, float f, float weather) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		boolean full = f >= GUST_FULL;
		double range = Mth.lerp(f, GUST_RANGE_MIN, GUST_RANGE_MAX);
		// the weather (and a Stormbound wind) scale the blow
		float strength = weather * WindStorm.galeMultiplier(player);
		// damage grows faster than the charge: most of a full blast's power is in its last second or two
		float maxDamage = Mth.lerp(f * f, GUST_DAMAGE_MIN, GUST_DAMAGE_MAX) * strength;
		double knockback = Mth.lerp(f, GUST_KNOCKBACK_MIN, GUST_KNOCKBACK_MAX) * strength;
		double minDot = Math.cos(Math.toRadians(GUST_HALF_ANGLE));
		AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.6);
		for (Entity target : level.getEntities(player, area, e -> e.isAlive() && !e.isSpectator() && e != player.getVehicle())) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > range || distance < 1.0E-3 || to.scale(1.0 / distance).dot(look) < minDot) {
				continue;
			}
			if (!player.hasLineOfSight(target)) {
				// walls take the blast
				continue;
			}
			// TNT's falloff: impact 1 point-blank, 0 at the edge; damage follows (impact² + impact) / 2
			double impact = 1.0 - distance / range;
			if (target instanceof LivingEntity living) {
				float damage = (float)(maxDamage * (impact * impact + impact) / 2.0);
				living.hurtServer(level, full ? player.damageSources().explosion(player, player) : player.damageSources().windCharge(player, player),
					Math.max(1.0F, damage));
				double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				if (resist <= 0.0) {
					continue;
				}
				Vec3 dir = to.normalize();
				Vec3 push = dir.scale(knockback * impact * resist);
				living.push(push.x, Math.max(push.y, 0.0) + (0.25 + 0.35 * f) * impact * resist, push.z);
				living.syncVelocity = true;
			} else if (target instanceof Projectile projectile) {
				projectile.setDeltaMovement(look.scale(1.0 + f));
				projectile.syncVelocity = true;
			}
		}
		// the blast itself: gusts rolling out along the cone
		for (int i = 1; i <= 5; i++) {
			Vec3 at = eye.add(look.scale(range * i / 5.0));
			level.sendParticles(f > 0.5F ? ParticleTypes.GUST_EMITTER_SMALL : ParticleTypes.GUST, at.x, at.y - 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y - 0.3, at.z, (int)(6 + 14 * f), 0.4 * i / 5.0 * (1 + f), 0.3 * i / 5.0 * (1 + f),
				0.4 * i / 5.0 * (1 + f), 0.05 + 0.1 * f);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F + f, 0.9F - 0.3F * f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (full) {
			// a full charge goes off like TNT (without breaking anything)
			Vec3 at = eye.add(look.scale(3.0));
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 0.8F);
			com.jaymospowers.core.Shakes.heavy(player, at, 24.0, 1.8F);
		}
	}

	/** The ground gust blows out the fire along its cone (before a Stormbound bolt lands at its tip). */
	private void douseGust(ServerPlayer player, ServerLevel level, float f) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double range = Mth.lerp(f, GUST_RANGE_MIN, GUST_RANGE_MAX);
		double spread = Math.tan(Math.toRadians(GUST_HALF_ANGLE));
		for (int i = 0; i <= 6; i++) {
			double d = range * i / 6.0;
			WindDouse.douse(level, eye.add(look.scale(d)), 1.0 + d * spread, player);
		}
	}

	/** Where a ground gust of this charge lands: the first block along the look, or the cone's tip. */
	private Vec3 gustImpact(ServerPlayer player, float f) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(Mth.lerp(f, GUST_RANGE_MIN, GUST_RANGE_MAX)));
		HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	private LivingEntity nearestEnemy(ServerPlayer player, ServerLevel level, double radius) {
		LivingEntity nearest = null;
		double best = radius * radius;
		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> e instanceof Enemy && e.isAlive())) {
			double d = mob.distanceToSqr(player);
			if (d <= best) {
				best = d;
				nearest = mob;
			}
		}
		return nearest;
	}

	private void pushMobs(ServerPlayer player, ServerLevel level, float f, float scale) {
		double strength = Mth.lerp(f, BARRIER_PUSH_MIN, BARRIER_PUSH_MAX) * scale;
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
		if (s.barrierTicks % 10 == 0) {
			WindDouse.douse(level, center, BARRIER_DOUSE_RADIUS, player);
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
		ServerPlayNetworking.send(player, new WindNet.Status(s.channel == null ? -1 : s.channel.ordinal(), s.channelTicks, s.channelMax, s.cooldowns.clone(), dash,
			stopFlight, s.lanceCooldown, s.gustCooldown));
	}
}
