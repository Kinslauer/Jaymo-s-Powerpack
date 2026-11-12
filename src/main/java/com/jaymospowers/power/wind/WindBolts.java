package com.jaymospowers.power.wind;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The storm side's thrown lightning, for a Wind user with the Stormbound or Thunderhead mode on. Every move is a variant of
 * a key (the keys themselves stay as they were):
 * <ul>
 * <li>sneak + G: <b>Bolt Rush</b>, a rush forward that throws a bolt at what you face.</li>
 * <li>sneak + V: <b>Bolt Clap</b>, a clap that sends a cone of lightning ahead.</li>
 * <li>sneak + B: <b>Bolt Slam</b>, a leap and a dive that stuns everything round where you land.</li>
 * <li>sprint + G (Thunderhead): <b>Bolt Dome</b>, a dome of lightning that zaps what touches it and stops shots.</li>
 * <li>sprint + V (Thunderhead): <b>Bolt Step</b>, a blink along a bolt of your own.</li>
 * <li>sprint + B (Thunderhead): <b>Tornado Ring</b>, tornadoes round you that fling enemies skyward.</li>
 * <li>sneak + Z (in a storm mode): <b>Turn the Sky</b>, the weather cycles clear, rain, thunder.</li>
 * <li>passive: melee hits change with the weather (clear: faster; rain: chains to wet targets; thunder: stuns).</li>
 * </ul>
 */
final class WindBolts {
	private static final String TABLE = "wind_bolts";
	private static final int RUSH = 0, CLAP = 1, SLAM = 2, DOME = 3, STEP = 4, RING = 5, SKY = 6, SLOTS = 7;

	/** Bolt Rush: the rush's speed (blocks/tick), how far a bolt is thrown, its damage, the cooldown. */
	static final double RUSH_SPEED = 1.5, THROW_RANGE = 28.0;
	static final float BOLT_DAMAGE = 6.0F;
	static final int RUSH_COOLDOWN = 140;
	/** Bolt Clap: reach, half-width at the far end, damage, knockback, cooldown. */
	static final double CLAP_REACH = 9.0, CLAP_WIDTH = 3.5, CLAP_KNOCK = 1.3;
	static final float CLAP_DAMAGE = 5.0F;
	static final int CLAP_COOLDOWN = 120;
	/** Bolt Slam: the leap, the dive speed, the reach of the stun, damage, stun ticks, ticks it may take, cooldown. */
	static final double SLAM_LEAP = 0.85, SLAM_DIVE = 2.2, SLAM_RADIUS = 6.5;
	static final float SLAM_DAMAGE = 4.0F;
	static final int SLAM_STUN = 50, SLAM_RISE = 7, SLAM_MAX = 80, SLAM_COOLDOWN = 200;
	/** Bolt Dome: radius, ticks, the zap and its damage, cooldown. */
	static final double DOME_RADIUS = 5.0;
	static final int DOME_TICKS = 140, DOME_ZAP_EVERY = 10, DOME_COOLDOWN = 420;
	static final float DOME_DAMAGE = 2.5F;
	/** Bolt Step: how far, damage along the line, cooldown. */
	static final double STEP_RANGE = 20.0;
	static final float STEP_DAMAGE = 4.0F;
	static final int STEP_COOLDOWN = 160;
	/** Tornado Ring: how many, their radius from you, their reach, ticks, the fling, damage, cooldown. */
	static final int RING_COUNT = 4, RING_TICKS = 120, RING_COOLDOWN = 520;
	static final double RING_RADIUS = 4.0, RING_REACH = 1.8, RING_HEIGHT = 7.0, RING_FLING = 1.0;
	static final float RING_DAMAGE = 2.0F;
	/** Turn the Sky: ticks the new weather lasts, the cooldown (long: it changes the world). */
	static final int SKY_TICKS = 6000, SKY_COOLDOWN = 6000;
	/** Hunger each move costs (exhaustion), and the food needed. */
	static final float EXHAUSTION = 2.0F;
	static final int MIN_FOOD = 6;
	/** Weather hits: clear gives Speed, rain chains, thunder stuns. */
	static final int CLEAR_SPEED_TICKS = 60;
	static final double CHAIN_RANGE = 6.0;
	static final int CHAIN_MAX = 2, STUN_TICKS = 24, HIT_COOLDOWN = 40;
	static final float CHAIN_SHARE = 0.6F;
	static final int BOLT = 0xCFE0FF, GLOW = 0x8FB4FF;

	private record Dome(ResourceKey<Level> dimension, Vec3 centre, long until) {
	}

	private record Slam(long armedAt, boolean dived) {
	}

	private static final Map<UUID, Dome> DOMES = new HashMap<>();
	private static final Map<UUID, Long> RINGS = new HashMap<>();
	private static final Map<UUID, Slam> SLAMS = new HashMap<>();
	private static final Map<UUID, Long> LAST_WEATHER_HIT = new HashMap<>();

	private WindBolts() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && damageTaken > 0.0F && source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
				&& source.is(DamageTypes.PLAYER_ATTACK) && WindModes.storm(attacker) && attacker.isAlive()) {
				weatherHit(attacker, entity, damageTaken);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
	}

	static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		DOMES.remove(id);
		RINGS.remove(id);
		SLAMS.remove(id);
		LAST_WEATHER_HIT.remove(id);
		PickKit.forget(player);
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.wind_picks." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** A key press while the player sneaks or sprints; true if it was one of the bolt moves (and so the key does nothing else). */
	static boolean key(ServerPlayer player, WindAbility ability) {
		if (!WindModes.storm(player) || player.isSpectator()) {
			return false;
		}
		boolean thunderhead = WindModes.mode(player) == WindModes.BOTH;
		int slot;
		if (player.isShiftKeyDown()) {
			slot = switch (ability) {
				case TAILWIND -> RUSH;
				case GALE_BARRIER -> CLAP;
				case ZEPHYR -> SLAM;
			};
		} else if (player.isSprinting() && thunderhead) {
			slot = switch (ability) {
				case TAILWIND -> DOME;
				case GALE_BARRIER -> STEP;
				case ZEPHYR -> RING;
			};
		} else {
			return false;
		}
		if (!PickKit.ready(TABLE, player, slot)) {
			say(player, "cooling");
			return true;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			say(player, "hungry");
			return true;
		}
		boolean went = switch (slot) {
			case RUSH -> rush(player);
			case CLAP -> clap(player);
			case SLAM -> slam(player);
			case DOME -> dome(player);
			case STEP -> step(player);
			default -> ring(player);
		};
		if (went) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		return true;
	}

	// ---------------------------------------------------------------- shared

	/** How hard a bolt hits under today's sky. */
	private static float sky(ServerLevel level) {
		return level.isThundering() ? 1.3F : level.isRaining() ? 1.0F : 0.8F;
	}

	/** One bolt at {@code at}: a visual-only strike and lightning damage to the creatures at its foot. */
	private static void strike(ServerPlayer caster, Vec3 at, float damage, double reach) {
		ServerLevel level = caster.level();
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at);
			bolt.setVisualOnly(true);
			bolt.setCause(caster);
			level.addFreshEntity(bolt);
		}
		DamageSource source = caster.damageSources().source(DamageTypes.LIGHTNING_BOLT, caster);
		AABB area = new AABB(at.x - reach, at.y - 1.0, at.z - reach, at.x + reach, at.y + 4.0, at.z + reach);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> PickKit.foe(caster, e))) {
			target.hurtServer(level, source, damage * sky(level));
			WindModes.chargeCreeper(target);
		}
		MagicFx.blast(level, at.add(0.0, 0.4, 0.0), (float)reach, BOLT);
		Shakes.around(level, at, 16.0, 0.8F);
	}

	private static void zap(ServerLevel level, Vec3 from, Vec3 to) {
		MagicFx.beam(level, from, to, 0.12F, BOLT, 5);
		Vec3 mid = from.add(to).scale(0.5);
		level.sendParticles(MagicFx.spark(BOLT, 0.9F), mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.0);
	}

	/** The spot the player's gaze meets, a creature or a block, within {@code range}. */
	private static Vec3 gaze(ServerPlayer player, double range) {
		LivingEntity aimed = PickKit.aimed(player, range);
		if (aimed != null) {
			return aimed.position();
		}
		HitResult hit = player.pick(range, 1.0F, false);
		return hit.getType() == HitResult.Type.MISS ? player.getEyePosition().add(player.getLookAngle().scale(range)) : hit.getLocation();
	}

	// ---------------------------------------------------------------- sneak moves

	private static boolean rush(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 dash = new Vec3(look.x, 0.0, look.z);
		dash = dash.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dash.normalize().scale(RUSH_SPEED);
		player.setDeltaMovement(dash.x, 0.2, dash.z);
		player.syncVelocity = true;
		player.resetFallDistance();
		Vec3 target = gaze(player, THROW_RANGE);
		Vec3 hand = player.getEyePosition().add(look.scale(0.6)).add(0.0, -0.3, 0.0);
		zap(level, hand, target);
		strike(player, target, BOLT_DAMAGE, 2.5);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.5F);
		PickKit.spend(TABLE, player, RUSH, RUSH_COOLDOWN);
		return true;
	}

	private static boolean clap(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		MagicFx.wave(level, player.position().add(0.0, 0.1, 0.0), new Vec3(look.x, 0.0, look.z), CLAP_REACH, 2.5F, BOLT, 14);
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(eye.add(look.scale(CLAP_REACH * 0.5)), CLAP_REACH * 0.6), c -> PickKit.foe(player, c))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double along = to.dot(look);
			if (along < 0.0 || along > CLAP_REACH) {
				continue;
			}
			double off = to.subtract(look.scale(along)).length();
			if (off <= 0.8 + CLAP_WIDTH * (along / CLAP_REACH)) {
				hit.add(e);
			}
		}
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
		for (LivingEntity e : hit) {
			e.hurtServer(level, source, CLAP_DAMAGE * sky(level));
			Vec3 push = new Vec3(look.x, 0.0, look.z).normalize().scale(CLAP_KNOCK).add(0.0, 0.3, 0.0);
			e.setDeltaMovement(e.getDeltaMovement().add(push));
			e.syncVelocity = true;
			if (level.isThundering()) {
				DarktideStatus.stun(e, STUN_TICKS);
			}
			zap(level, eye.add(look.scale(0.8)), e.getBoundingBox().getCenter());
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 1.6F);
		Shakes.around(level, player.position(), 14.0, 0.6F);
		PickKit.spend(TABLE, player, CLAP, CLAP_COOLDOWN);
		return true;
	}

	private static boolean slam(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (player.onGround()) {
			player.setDeltaMovement(player.getDeltaMovement().x, SLAM_LEAP, player.getDeltaMovement().z);
			player.syncVelocity = true;
			SLAMS.put(player.getUUID(), new Slam(now, false));
		} else {
			player.setDeltaMovement(0.0, -SLAM_DIVE, 0.0);
			player.syncVelocity = true;
			SLAMS.put(player.getUUID(), new Slam(now - SLAM_RISE, true));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.9F, 0.8F);
		PickKit.spend(TABLE, player, SLAM, SLAM_COOLDOWN);
		return true;
	}

	private static void slamTick(ServerPlayer player, ServerLevel level, long now) {
		Slam slam = SLAMS.get(player.getUUID());
		if (slam == null) {
			return;
		}
		long age = now - slam.armedAt;
		if (age > SLAM_MAX) {
			SLAMS.remove(player.getUUID());
			return;
		}
		if (!slam.dived && age >= SLAM_RISE) {
			player.setDeltaMovement(0.0, -SLAM_DIVE, 0.0);
			player.syncVelocity = true;
			SLAMS.put(player.getUUID(), new Slam(slam.armedAt, true));
			return;
		}
		if (slam.dived) {
			level.sendParticles(MagicFx.spark(BOLT, 0.9F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.6, 0.3, 0.0);
		}
		if (slam.dived && (player.onGround() || player.isInWater())) {
			SLAMS.remove(player.getUUID());
			Vec3 at = player.position();
			MagicFx.ring(level, at, (float)SLAM_RADIUS, BOLT);
			MagicFx.blast(level, at.add(0.0, 0.3, 0.0), 2.5F, GLOW);
			DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, SLAM_RADIUS), c -> PickKit.foe(player, c))) {
				if (e.distanceToSqr(at) <= SLAM_RADIUS * SLAM_RADIUS) {
					e.hurtServer(level, source, SLAM_DAMAGE * sky(level));
					DarktideStatus.stun(e, SLAM_STUN);
					zap(level, at.add(0.0, 0.2, 0.0), e.getBoundingBox().getCenter());
				}
			}
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4F, 0.8F);
			Shakes.around(level, at, 24.0, 1.4F);
		}
	}

	// ---------------------------------------------------------------- thunderhead moves

	private static boolean dome(ServerPlayer player) {
		ServerLevel level = player.level();
		DOMES.put(player.getUUID(), new Dome(level.dimension(), player.position(), level.getGameTime() + DOME_TICKS));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.2F);
		PickKit.spend(TABLE, player, DOME, DOME_COOLDOWN);
		return true;
	}

	private static void domeTick(ServerPlayer player, ServerLevel level, long now) {
		Dome d = DOMES.get(player.getUUID());
		if (d == null) {
			return;
		}
		if (now >= d.until || !d.dimension.equals(level.dimension())) {
			DOMES.remove(player.getUUID());
			MagicFx.blast(level, d.centre.add(0.0, 1.0, 0.0), (float)DOME_RADIUS * 0.6F, BOLT);
			return;
		}
		Vec3 c = d.centre.add(0.0, 0.5, 0.0);
		if (now % 4 == 0) {
			MagicFx.orb(level, c, (float)DOME_RADIUS, GLOW, 6);
		}
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, PickKit.around(c, DOME_RADIUS + 1.0))) {
			Entity owner = p.getOwner();
			if (owner == player || owner != null && player.isAlliedTo(owner)) {
				continue;
			}
			Vec3 to = c.subtract(p.position());
			if (to.length() <= DOME_RADIUS && p.getDeltaMovement().dot(to) > 0.0) {
				zap(level, p.position(), c.add(to.normalize().scale(-DOME_RADIUS)));
				p.discard();
			}
		}
		if (now % DOME_ZAP_EVERY == 0) {
			DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(c, DOME_RADIUS + 1.5), x -> PickKit.foe(player, x))) {
				double dist = e.position().add(0.0, 1.0, 0.0).distanceTo(c);
				if (Math.abs(dist - DOME_RADIUS) <= 1.3 || dist < DOME_RADIUS && e.distanceTo(player) > DOME_RADIUS * 1.5) {
					e.hurtServer(level, source, DOME_DAMAGE * sky(level));
					Vec3 out = e.position().subtract(c).multiply(1.0, 0.0, 1.0);
					if (out.lengthSqr() > 1.0E-4) {
						e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(0.5)));
						e.syncVelocity = true;
					}
					zap(level, c.add(e.position().subtract(c).normalize().scale(DOME_RADIUS)), e.getBoundingBox().getCenter());
				}
			}
		}
	}

	private static boolean step(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(STEP_RANGE));
		var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		Vec3 start = player.position();
		Vec3 dest = null;
		double total = stop.subtract(eye).length();
		for (double d = total; d >= 1.0 && dest == null; d -= 0.5) {
			Vec3 feet = eye.add(look.scale(d)).add(0.0, -player.getEyeHeight(), 0.0);
			if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(start)))) {
				dest = feet;
			}
		}
		if (dest == null) {
			say(player, "no_room");
			return false;
		}
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
		Vec3 from = start.add(0.0, 1.0, 0.0), to = dest.add(0.0, 1.0, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5), c -> PickKit.foe(player, c))) {
			Vec3 p = e.getBoundingBox().getCenter().subtract(from);
			Vec3 line = to.subtract(from);
			double t = Mth.clamp(p.dot(line) / Math.max(1.0E-4, line.lengthSqr()), 0.0, 1.0);
			if (p.subtract(line.scale(t)).length() <= 1.6) {
				e.hurtServer(level, source, STEP_DAMAGE * sky(level));
			}
		}
		MagicFx.beam(level, from, to, 0.25F, BOLT, 8);
		level.sendParticles(MagicFx.spark(BOLT, 1.2F), from.x, from.y, from.z, 8, 0.4, 0.6, 0.4, 0.0);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		MagicFx.blast(level, to, 1.6F, GLOW);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.8F);
		PickKit.spend(TABLE, player, STEP, STEP_COOLDOWN);
		return true;
	}

	private static boolean ring(ServerPlayer player) {
		ServerLevel level = player.level();
		RINGS.put(player.getUUID(), level.getGameTime() + RING_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.4F, 0.7F);
		PickKit.spend(TABLE, player, RING, RING_COOLDOWN);
		return true;
	}

	private static void ringTick(ServerPlayer player, ServerLevel level, long now) {
		Long until = RINGS.get(player.getUUID());
		if (until == null) {
			return;
		}
		if (now >= until) {
			RINGS.remove(player.getUUID());
			return;
		}
		double spin = now * 0.12;
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
		for (int i = 0; i < RING_COUNT; i++) {
			double a = spin + i * Math.PI * 2.0 / RING_COUNT;
			Vec3 base = player.position().add(Math.cos(a) * RING_RADIUS, 0.0, Math.sin(a) * RING_RADIUS);
			if (now % 6 == 0) {
				MagicFx.ring(level, base, null, 1.3F, GLOW, 8);
			}
			for (double h = 0.5; h < RING_HEIGHT; h += 1.2) {
				double r = 0.4 + h * 0.18, t = now * 0.5 + h;
				level.sendParticles(MagicFx.wisp(BOLT, 0.9F), base.x + Math.cos(t) * r, base.y + h, base.z + Math.sin(t) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (now % 3 == 0) {
				level.sendParticles(MagicFx.spark(BOLT, 0.8F), base.x, base.y + RING_HEIGHT * 0.5, base.z, 1, 0.5, 2.0, 0.5, 0.0);
			}
			AABB column = new AABB(base.x - RING_REACH, base.y - 0.5, base.z - RING_REACH, base.x + RING_REACH, base.y + RING_HEIGHT, base.z + RING_REACH);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, column, c -> PickKit.foe(player, c))) {
				e.setDeltaMovement(e.getDeltaMovement().x * 0.8, Math.max(e.getDeltaMovement().y, RING_FLING), e.getDeltaMovement().z * 0.8);
				e.syncVelocity = true;
				e.resetFallDistance();
				if (now % 20 == 0) {
					e.hurtServer(level, source, RING_DAMAGE * sky(level));
				}
			}
		}
		if (now % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	// ---------------------------------------------------------------- weather

	/** Z while sneaking in a storm mode: the weather turns one step (clear, rain, thunder). True if it handled the key. */
	static boolean turnSky(ServerPlayer player) {
		if (!WindModes.storm(player)) {
			return false;
		}
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			say(player, "sky_overworld");
			return true;
		}
		if (!level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
			say(player, "sky_locked");
			return true;
		}
		if (!PickKit.ready(TABLE, player, SKY)) {
			say(player, "cooling");
			return true;
		}
		WeatherData weather = level.getWeatherData();
		String to;
		if (level.isThundering()) {
			weather.setClearWeatherTime(SKY_TICKS);
			weather.setRainTime(0);
			weather.setThunderTime(0);
			weather.setRaining(false);
			weather.setThundering(false);
			to = "clear";
		} else if (level.isRaining()) {
			weather.setClearWeatherTime(0);
			weather.setRainTime(SKY_TICKS);
			weather.setThunderTime(SKY_TICKS);
			weather.setRaining(true);
			weather.setThundering(true);
			to = "thunder";
		} else {
			weather.setClearWeatherTime(0);
			weather.setRainTime(SKY_TICKS);
			weather.setThunderTime(SKY_TICKS);
			weather.setRaining(true);
			weather.setThundering(false);
			to = "rain";
		}
		PickKit.spend(TABLE, player, SKY, SKY_COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION * 3.0F);
		MagicFx.sigil(level, player.position(), 4.0F, GLOW, 40);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.wind_picks.sky_" + to).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		return true;
	}

	/** A melee hit under the sky as it is: clear is quicker, rain chains to the wet, thunder stuns. */
	private static void weatherHit(ServerPlayer attacker, LivingEntity victim, float dealt) {
		ServerLevel level = attacker.level();
		long now = level.getGameTime();
		if (level.isThundering()) {
			DarktideStatus.stun(victim, STUN_TICKS);
			MagicFx.blast(level, victim.getBoundingBox().getCenter(), 1.2F, BOLT);
		} else if (level.isRaining()) {
			Long last = LAST_WEATHER_HIT.get(attacker.getUUID());
			if (last != null && now - last < HIT_COOLDOWN || !victim.isInWaterOrRain()) {
				return;
			}
			LAST_WEATHER_HIT.put(attacker.getUUID(), now);
			DamageSource source = attacker.damageSources().source(DamageTypes.LIGHTNING_BOLT, attacker);
			int chained = 0;
			for (LivingEntity next : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(victim.position(), CHAIN_RANGE),
				c -> c != victim && PickKit.foe(attacker, c) && c.isInWaterOrRain())) {
				if (chained++ >= CHAIN_MAX) {
					break;
				}
				next.hurtServer(level, source, dealt * CHAIN_SHARE);
				zap(level, victim.getBoundingBox().getCenter(), next.getBoundingBox().getCenter());
			}
		} else {
			attacker.addEffect(new MobEffectInstance(MobEffects.SPEED, CLEAR_SPEED_TICKS, 1, true, false));
		}
	}

	// ---------------------------------------------------------------- every tick

	static void tick(ServerPlayer player) {
		if (DOMES.isEmpty() && RINGS.isEmpty() && SLAMS.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive()) {
			forget(player);
			return;
		}
		slamTick(player, level, now);
		domeTick(player, level, now);
		ringTick(player, level, now);
	}
}
