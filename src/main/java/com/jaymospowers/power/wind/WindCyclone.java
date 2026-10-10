package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cyclone (hold X, the power skill key; a tap is still the Tempest Kick): you drop into a superhero landing and a
 * column of wind rises round you for as long as you hold. Creatures inside are caught, spun and lifted a little, and
 * battered: {@link #DAMAGE} every {@link #DAMAGE_INTERVAL} ticks ({@link #DAMAGE_INTERVAL_ASCENDED} ascended or
 * corrupted; corrupted, as shock). The vortex steals your breath too: {@link #SELF_DAMAGE} suffocation every
 * {@link #SELF_INTERVAL} ticks ({@link #SELF_INTERVAL_ASCENDED}), which is what limits how long you can hold it.
 * Available from the first unlocked ability; its radius and height grow with mastery.
 */
public final class WindCyclone {
	/** How long X must be held before it's a Cyclone rather than a kick (ticks, ~0.4 s). The client decides it. */
	public static final int HOLD_TICKS = 8;
	/** The fewest unlocked abilities that give the Cyclone (a fresh awakening has only its starter). */
	public static final int MIN_MASTERY = 1;
	/** After you let go (ticks). */
	public static final int COOLDOWN = 60;
	/** Corrupted: how long the shock sets a creature alight (seconds). */
	static final int SHOCK_BURN_SECONDS = 2;
	/** The funnel at the least and the most mastery: radius and height (blocks). */
	static final double RADIUS_MIN = 3.0, RADIUS_MAX = 6.0;
	static final double HEIGHT_MIN = 6.0, HEIGHT_MAX = 12.0;
	/** The funnel's foot, as a share of its top's radius. */
	static final double FOOT_SHARE = 0.35;
	/** How long the funnel takes to rise to full size, while you drop into the stance (ticks). */
	public static final int FORM_TICKS = 8;
	/** Damage to everything else inside, and how often (ticks). */
	static final float DAMAGE = 4.0F;
	static final int DAMAGE_INTERVAL = 20, DAMAGE_INTERVAL_ASCENDED = 10;
	/** The vortex's toll on you (suffocation), and how often (ticks). */
	static final float SELF_DAMAGE = 1.0F;
	static final int SELF_INTERVAL = 40, SELF_INTERVAL_ASCENDED = 80;
	/** Hunger per tick while held (full sonic flight is about 0.105). */
	static final float EXHAUSTION_PER_TICK = 0.06F;
	/** Caught creatures: spin speed (blocks/tick along the circle), pull toward the eye, lift and how high (blocks). */
	static final double SPIN_SPEED = 0.38, PULL = 0.06, LIFT_SPEED = 0.12, LIFT_HEIGHT = 1.4;
	/** Letting go: what was caught is flung outward this hard. */
	static final double RELEASE_FLING = 0.7, RELEASE_FLING_UP = 0.3;
	/** The funnel's particles: rings up its height, points on each ring per tick, and how fast it turns (radians/tick). */
	static final int RINGS = 6, RING_POINTS = 3;
	static final double TURN_RATE = 0.55;
	/** The roar layered over the client's loop (ticks between). */
	static final int ROAR_INTERVAL = 16;
	private static final DustParticleOptions STORM_DUST = new DustParticleOptions(0xB070FF, 1.2F);
	private static final Identifier ROOT = JaymosPowers.id("wind_cyclone_root");

	/** A Cyclone under way: when it began (game time) and which side kneels ({@code left}: the stance mirrored). */
	public record Stance(long start, boolean left) {
		public static final Codec<Stance> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("start").forGetter(Stance::start),
			Codec.BOOL.fieldOf("left").forGetter(Stance::left)
		).apply(i, Stance::new));
		public static final StreamCodec<ByteBuf, Stance> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Stance::start,
			ByteBufCodecs.BOOL, Stance::left,
			Stance::new);
	}

	/** On a player holding a Cyclone: the stance. Synced to everyone, who see the landing pose and hear the roar. */
	public static final AttachmentType<Stance> CYCLONE = AttachmentRegistry.create(JaymosPowers.id("wind_cyclone"),
		builder -> builder.syncWith(Stance.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: X held long enough ({@code down}), or let go. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("wind_cyclone"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::down);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	private static final class Active {
		final ResourceKey<Level> dimension;
		final long start;
		long nextHit;
		long nextSelf;

		Active(ResourceKey<Level> dimension, long start, long nextHit, long nextSelf) {
			this.dimension = dimension;
			this.start = start;
			this.nextHit = nextHit;
			this.nextSelf = nextSelf;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private WindCyclone() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> {
			if (payload.down()) {
				start(ctx.player());
			} else {
				stop(ctx.player());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ACTIVE.remove(handler.player.getUUID());
			READY_AT.remove(handler.player.getUUID());
			unroot(handler.player);
			handler.player.removeAttached(CYCLONE);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		ACTIVE.clear();
		READY_AT.clear();
	}

	/** Holding a Cyclone now (either side: the client reads the synced stance). */
	public static boolean isActive(Player player) {
		return player.getAttached(CYCLONE) != null;
	}

	/** The funnel's top radius at this player's mastery (blocks). */
	public static double radius(Player player) {
		return Mth.lerp(masteryShare(player), RADIUS_MIN, RADIUS_MAX);
	}

	/** The funnel's height at this player's mastery (blocks). */
	public static double height(Player player) {
		return Mth.lerp(masteryShare(player), HEIGHT_MIN, HEIGHT_MAX);
	}

	/** Whether this player has grown into the Cyclone (both sides). */
	public static boolean isAvailable(Player player) {
		return Mastery.unlocked(player) >= MIN_MASTERY;
	}

	private static double masteryShare(Player player) {
		return Mth.clamp((Mastery.unlocked(player) - MIN_MASTERY) / (double)(Mastery.ALL - MIN_MASTERY), 0.0, 1.0);
	}

	private static void start(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!Powers.has(player, WindPower.INSTANCE) || !player.isAlive() || player.isSpectator() || ACTIVE.containsKey(player.getUUID())) {
			return;
		}
		if (!isAvailable(player) || !player.onGround() || WindPower.INSTANCE.flewRecently(player, 0) || Carry.isCarried(player)
			|| Carry.isCarrying(player) || player.isPassenger() || now < READY_AT.getOrDefault(player.getUUID(), 0L)) {
			// the wind won't gather: a sigh, nothing more
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.25F, 0.6F);
			return;
		}
		boolean fast = Fate.isAscended(player) || Fate.isCorrupted(player);
		ACTIVE.put(player.getUUID(), new Active(level.dimension(), now, now + (fast ? DAMAGE_INTERVAL_ASCENDED : DAMAGE_INTERVAL),
			now + (fast ? SELF_INTERVAL_ASCENDED : SELF_INTERVAL)));
		player.setAttached(CYCLONE, new Stance(now, player.getRandom().nextBoolean()));
		root(player);
		Mastery.used(player);
		WindStorm.onCast(player);
		WindModes.onCast(player);
		// the landing: a thump into the ground and a ring of dust kicked out
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.8F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI * 2.0 / 16.0;
			level.sendParticles(ParticleTypes.POOF, player.getX() + Math.cos(a) * 0.5, player.getY() + 0.1, player.getZ() + Math.sin(a) * 0.5,
				0, Math.cos(a) * 0.3, 0.02, Math.sin(a) * 0.3, 1.0);
		}
	}

	/** Let go (or forced to): the funnel bursts outward, flinging what it held, and the cooldown starts. */
	static void stop(ServerPlayer player) {
		Active active = ACTIVE.remove(player.getUUID());
		unroot(player);
		player.removeAttached(CYCLONE);
		if (active == null) {
			return;
		}
		ServerLevel level = player.level();
		READY_AT.put(player.getUUID(), level.getGameTime() + COOLDOWN);
		if (level.dimension() != active.dimension) {
			return;
		}
		double radius = radius(player);
		for (LivingEntity target : caught(player, level, radius, height(player))) {
			Vec3 away = new Vec3(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ());
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away.normalize();
			double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			if (resist > 0.0) {
				target.push(away.x * RELEASE_FLING * resist, RELEASE_FLING_UP * resist, away.z * RELEASE_FLING * resist);
				target.syncVelocity = true;
			}
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_DEATH, SoundSource.PLAYERS, 0.5F, 0.6F);
	}

	/** From the power's server tick (alive or not), while the player has Wind. */
	static void tick(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			if (player.getAttached(CYCLONE) != null) {
				// (a stale stance, e.g. after a reload)
				unroot(player);
				player.removeAttached(CYCLONE);
			}
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive() || player.isSpectator() || level.dimension() != active.dimension || Carry.isCarried(player)
			|| player.isPassenger() || WindPower.INSTANCE.flewRecently(player, 0)) {
			stop(player);
			return;
		}
		long now = level.getGameTime();
		long age = now - active.start;
		boolean corrupted = Fate.isCorrupted(player);
		boolean fast = Fate.isAscended(player) || corrupted;
		// the funnel rises out of the landing
		double grow = Mth.clamp((age + 1) / (double)FORM_TICKS, 0.0, 1.0);
		grow = grow * grow * (3.0 - 2.0 * grow);
		double radius = Math.max(1.0, radius(player) * grow);
		double height = Math.max(1.5, height(player) * grow);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION_PER_TICK);
		}
		funnel(player, level, age, radius, height, corrupted);
		if (age % ROAR_INTERVAL == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.2F, 0.5F + player.getRandom().nextFloat() * 0.2F);
		}

		boolean hit = now >= active.nextHit;
		if (hit) {
			active.nextHit = now + (fast ? DAMAGE_INTERVAL_ASCENDED : DAMAGE_INTERVAL);
		}
		for (LivingEntity target : caught(player, level, radius, height)) {
			spin(player, target);
			if (hit) {
				batter(player, level, target, corrupted);
			}
		}

		if (now >= active.nextSelf) {
			if (player.isCreative()) {
				active.nextSelf = now + (fast ? SELF_INTERVAL_ASCENDED : SELF_INTERVAL);
			} else if (suffocate(player, level)) {
				active.nextSelf = now + (fast ? SELF_INTERVAL_ASCENDED : SELF_INTERVAL);
			}
			// (blocked by a fresh hit's invulnerability: it lands next tick instead)
		}
	}

	/** Everything else alive inside the funnel (wider toward the top). */
	private static java.util.List<LivingEntity> caught(ServerPlayer player, ServerLevel level, double radius, double height) {
		double baseY = player.getY();
		AABB box = new AABB(player.getX() - radius, baseY - 0.5, player.getZ() - radius, player.getX() + radius, baseY + height, player.getZ() + radius);
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> {
			if (e == player || !e.isAlive() || e.isSpectator() || Carry.isCarried(e) || e == player.getVehicle()) {
				return false;
			}
			double up = Mth.clamp((e.getY() - baseY) / height, 0.0, 1.0);
			double reach = Mth.lerp(up, radius * FOOT_SHARE, radius) + e.getBbWidth() * 0.5;
			// (the foot is narrow, but anything within the full radius at ground level is still dragged in)
			reach = Math.max(reach, radius * 0.8);
			double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
			return dx * dx + dz * dz <= reach * reach;
		});
	}

	/** Round and round, drawn in toward the eye, lifted off its feet a little. */
	private static void spin(ServerPlayer player, LivingEntity target) {
		double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		if (resist <= 0.0) {
			return;
		}
		Vec3 out = new Vec3(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ());
		double distance = out.length();
		Vec3 tangent;
		Vec3 inward;
		if (distance < 1.0E-3) {
			tangent = player.getLookAngle().horizontal();
			inward = Vec3.ZERO;
		} else {
			out = out.scale(1.0 / distance);
			// counter-clockwise seen from above
			tangent = new Vec3(-out.z, 0.0, out.x);
			// drawn in toward a ring just outside you, never into your arms
			inward = out.scale(distance > 1.6 ? -PULL : PULL);
		}
		double lift = Mth.clamp((player.getY() + LIFT_HEIGHT - target.getY()) * 0.2, -0.1, LIFT_SPEED);
		Vec3 v = target.getDeltaMovement();
		Vec3 swirl = tangent.scale(SPIN_SPEED).add(inward);
		target.setDeltaMovement(Mth.lerp(resist, v.x, swirl.x), Mth.lerp(resist, v.y, lift), Mth.lerp(resist, v.z, swirl.z));
		target.syncVelocity = true;
		target.fallDistance = 0.0;
	}

	private static void batter(ServerPlayer player, ServerLevel level, LivingEntity target, boolean corrupted) {
		double y = target.getY() + target.getBbHeight() * 0.5;
		if (corrupted) {
			DamageSource shock = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
			target.hurtServer(level, shock, DAMAGE);
			com.jaymospowers.core.Flames.touch(level, target, SHOCK_BURN_SECONDS, player);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), y, target.getZ(), 12, target.getBbWidth() * 0.6, target.getBbHeight() * 0.4,
				target.getBbWidth() * 0.6, 0.3);
			level.playSound(null, target.getX(), y, target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.6F);
		} else {
			target.hurtServer(level, player.damageSources().windCharge(player, player), DAMAGE);
			level.sendParticles(ParticleTypes.SMALL_GUST, target.getX(), y, target.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/**
	 * The vortex steals your breath: suffocation, which ignores armour. Resistance (the Gale Barrier's, or any other) is
	 * made up for, so Wind's own protections never shield you from your own storm. False if the hit didn't land.
	 */
	private static boolean suffocate(ServerPlayer player, ServerLevel level) {
		float amount = SELF_DAMAGE;
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			// vanilla takes a fifth off per level
			float kept = 1.0F - 0.2F * (resistance.getAmplifier() + 1);
			if (kept > 0.0F) {
				amount /= kept;
			}
		}
		boolean landed = player.hurtServer(level, player.damageSources().inWall(), amount);
		if (landed) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.7F, 0.8F);
		}
		return landed;
	}

	/** The column: rings of cloud turning round you, wider toward the top, with gusts and (corrupted) sparks. */
	private static void funnel(ServerPlayer player, ServerLevel level, long age, double radius, double height, boolean corrupted) {
		double x0 = player.getX(), y0 = player.getY(), z0 = player.getZ();
		for (int ring = 0; ring < RINGS; ring++) {
			double up = (ring + 0.5) / RINGS;
			double r = Mth.lerp(up, radius * FOOT_SHARE, radius);
			// each ring a little ahead of the one below, so the column twists
			double turn = age * TURN_RATE + ring * 0.7;
			for (int i = 0; i < RING_POINTS; i++) {
				double a = turn + i * Math.PI * 2.0 / RING_POINTS;
				double x = x0 + Math.cos(a) * r, z = z0 + Math.sin(a) * r;
				double y = y0 + up * height + (player.getRandom().nextDouble() - 0.5) * height / RINGS;
				// count 0: (dx, dy, dz) is the velocity, along the circle and rising
				double speed = 0.25 + 0.25 * up;
				ParticleOptions particle = corrupted && i == 0 ? STORM_DUST : ParticleTypes.CLOUD;
				level.sendParticles(particle, x, y, z, 0, -Math.sin(a) * speed, 0.06, Math.cos(a) * speed, 1.0);
			}
		}
		// dust and leaves dragged round at the foot
		double a = age * TURN_RATE * 1.3;
		level.sendParticles(ParticleTypes.POOF, x0 + Math.cos(a) * radius * 0.5, y0 + 0.1, z0 + Math.sin(a) * radius * 0.5,
			0, -Math.sin(a) * 0.3, 0.05, Math.cos(a) * 0.3, 1.0);
		if (age % 4 == 0) {
			double h = player.getRandom().nextDouble() * height;
			double b = player.getRandom().nextDouble() * Math.PI * 2.0;
			double r = Mth.lerp(h / height, radius * FOOT_SHARE, radius);
			level.sendParticles(ParticleTypes.SMALL_GUST, x0 + Math.cos(b) * r, y0 + h, z0 + Math.sin(b) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (corrupted) {
			// static crawling up the column
			for (int i = 0; i < 3; i++) {
				double h = player.getRandom().nextDouble() * height;
				double b = player.getRandom().nextDouble() * Math.PI * 2.0;
				double r = Mth.lerp(h / height, radius * FOOT_SHARE, radius);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x0 + Math.cos(b) * r, y0 + h, z0 + Math.sin(b) * r, 2, 0.2, 0.2, 0.2, 0.2);
			}
			if (age % 23 == 0) {
				level.playSound(null, x0, y0 + height * 0.5, z0, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.5F, 1.6F);
			}
		}
	}

	/** Rooted in the stance: no walking, no jumping (attribute changes reach the client, which moves the player). */
	private static void root(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.addOrUpdateTransientModifier(new AttributeModifier(ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void unroot(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(ROOT);
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(ROOT);
		}
	}
}
