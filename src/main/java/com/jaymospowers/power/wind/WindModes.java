package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 Wind: the modes on the Power Mode key (Z), Stormbound's lightning call on B, and the starter's cloak.
 * <ul>
 * <li><b>Stormbound mode</b> (corrupted): lightning wraps you. Your melee hits stun (brief heavy Slowness and a jolt) and
 * charge the target with static; at {@link #STATIC_MAX} stacks it's <b>shocked</b>: rooted completely for
 * {@link #SHOCK_TICKS}. The storm is unstable: every second there's a chance it arcs into you, and a far bigger one
 * whenever you use an ability.</li>
 * <li><b>Tempest mode</b> (ascended): a circling wind deflects projectiles, and your other abilities are
 * {@link #TEMPEST_ABILITY_SCALE} times as strong.</li>
 * <li>Both (ascended and corrupted): one mode with all of it.</li>
 * <li><b>Call Lightning</b> (B while Stormbound, replacing Zephyr's Breath): a bolt where you aim, for moderate damage, a
 * good bite of hunger, and a little static that arcs back into you.</li>
 * <li><b>The starter</b>: a faint cloak of wind, kept for good: slightly quicker on foot, and holding Jump in mid-air
 * slows your fall (client side; here, a slow descent isn't counted as a fall).</li>
 * </ul>
 */
public final class WindModes {
	public static final int NONE = 0, STORM = 1, TEMPEST = 2, BOTH = 3;

	// ---- Stormbound mode ----
	/** Melee stun: Slowness at this amplifier for this long, and a little extra knockback. */
	static final int STUN_TICKS = 16;
	static final int STUN_AMPLIFIER = 3;
	static final double STUN_KNOCKBACK = 0.35;
	/** Static: stacks to a shock, how long a stack lasts without another hit, how long a shock roots. */
	public static final int STATIC_MAX = 5;
	static final int STATIC_DECAY_TICKS = 100;
	public static final int SHOCK_TICKS = 50;
	static final float SHOCK_DAMAGE = 3.0F;
	/** Instability: chance each second of arcing into you, and per ability use; how much each arc hurts. */
	static final float IDLE_ZAP_CHANCE = 0.06F;
	static final float CAST_ZAP_CHANCE = 0.35F;
	static final float IDLE_ZAP_DAMAGE = 1.5F;
	static final float CAST_ZAP_DAMAGE = 3.0F;
	/** The mode's hunger, per tick. */
	static final float MODE_EXHAUSTION = 0.01F;

	// ---- Tempest mode ----
	public static final float TEMPEST_ABILITY_SCALE = 1.25F;
	static final double TEMPEST_DEFLECT_RADIUS = 3.5;

	// ---- Call Lightning (Stormbound B) ----
	static final double CALL_RANGE = 40.0;
	static final double CALL_RADIUS = 2.5;
	static final float CALL_DAMAGE_MIN = 6.0F, CALL_DAMAGE_MAX = 9.0F;
	/** Exhaustion (4 = one hunger point) and the static that bites back. */
	static final float CALL_EXHAUSTION_MIN = 6.0F, CALL_EXHAUSTION_MAX = 12.0F;
	static final float CALL_SELF_DAMAGE_MIN = 1.0F, CALL_SELF_DAMAGE_MAX = 2.0F;

	// ---- the starter's cloak ----
	static final double CLOAK_SPEED = 0.10;
	/** The cloak's slowest fall (b/t) while Jump is held, and the sink the server lets pass as "not a fall". */
	public static final double CLOAK_FALL_SPEED = 0.12;
	static final double CLOAK_SAFE_SINK = 0.2;

	private static final Identifier CLOAK_ID = JaymosPowers.id("wind_cloak");
	private static final Identifier SHOCK_SPEED_ID = JaymosPowers.id("wind_shock_speed");
	private static final Identifier SHOCK_JUMP_ID = JaymosPowers.id("wind_shock_jump");

	/** The mode a player has on (0 none, else STORM | TEMPEST bits). Synced to everyone, who see it. */
	public static final AttachmentType<Integer> MODE = AttachmentRegistry.create(JaymosPowers.id("wind_mode"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a creature: rooted by a shock (clients draw it crackling). */
	public static final AttachmentType<Boolean> SHOCKED = AttachmentRegistry.create(JaymosPowers.id("wind_shocked"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private record Charge(int stacks, long at) {
	}

	private record Shock(ResourceKey<Level> dimension, UUID entity, long until) {
	}

	private static final Map<UUID, Charge> STATIC = new HashMap<>();
	private static final List<Shock> SHOCKS = new ArrayList<>();

	private WindModes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WindModes::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATIC.clear();
			SHOCKS.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			// melee only: the attacker struck it themselves
			if (damageTaken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer attacker
				&& entity != attacker && storm(attacker) && Powers.has(attacker, WindPower.INSTANCE)) {
				charge(attacker, entity);
			}
		});
	}

	public static int mode(Player player) {
		Integer mode = player.getAttached(MODE);
		return mode == null ? NONE : mode;
	}

	public static boolean storm(Player player) {
		return (mode(player) & STORM) != 0;
	}

	public static boolean tempest(Player player) {
		return (mode(player) & TEMPEST) != 0;
	}

	/** What Z would turn on now: the storm if corrupted, the tempest if ascended, both if both. */
	static int available(Player player) {
		return (Fate.isCorrupted(player) ? STORM : 0) | (Fate.isAscended(player) ? TEMPEST : 0);
	}

	/** Other abilities' strength in the Tempest mode. */
	static float abilityScale(Player player) {
		return tempest(player) ? TEMPEST_ABILITY_SCALE : 1.0F;
	}

	/** Z: on or off. Nothing at all if the power has neither form yet. */
	static void toggle(ServerPlayer player) {
		ServerLevel level = player.level();
		if (mode(player) != NONE) {
			player.removeAttached(MODE);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_DEATH, SoundSource.PLAYERS, 0.6F, 1.4F);
			return;
		}
		int mode = available(player);
		if (mode == NONE) {
			return;
		}
		player.setAttached(MODE, mode);
		if ((mode & STORM) != 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.6F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.3);
		}
		if ((mode & TEMPEST) != 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.7F);
			level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Every tick, per wind player: the mode's upkeep, and the starter's cloak. */
	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		cloak(player, true);
		// a slow, held descent (the cloak) isn't a fall; the rest still is
		if (!player.onGround() && player.getY() < player.yo && player.getY() - player.yo > -CLOAK_SAFE_SINK) {
			player.fallDistance = Math.min(player.fallDistance, 1.0);
			if (player.tickCount % 40 == 0) {
				Mastery.used(player);
			}
		}
		int mode = mode(player);
		if (mode == NONE) {
			return;
		}
		// a form that's gone (purified, say) takes its half of the mode with it
		int still = mode & available(player);
		if (still != mode) {
			if (still == NONE) {
				player.removeAttached(MODE);
				return;
			}
			player.setAttached(MODE, still);
			mode = still;
		}
		player.causeFoodExhaustion(MODE_EXHAUSTION);
		if ((mode & STORM) != 0 && player.tickCount % 20 == 0 && player.getRandom().nextFloat() < IDLE_ZAP_CHANCE) {
			zap(player, IDLE_ZAP_DAMAGE);
		}
		if ((mode & TEMPEST) != 0) {
			deflect(player, level);
		}
	}

	/** An ability was used: a Stormbound mode may arc into you. */
	static void onCast(ServerPlayer player) {
		if (storm(player) && player.getRandom().nextFloat() < CAST_ZAP_CHANCE) {
			zap(player, CAST_ZAP_DAMAGE);
		}
	}

	/** The storm turns on its master: a crackle and a jolt. */
	private static void zap(ServerPlayer player, float damage) {
		ServerLevel level = player.level();
		if (player.isCreative()) {
			return;
		}
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT);
		player.hurtServer(level, source, damage);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.3, 0.6, 0.3, 0.4);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
	}

	/** Projectiles near a Tempest are blown back the way they came. */
	private static void deflect(ServerPlayer player, ServerLevel level) {
		Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		AABB area = player.getBoundingBox().inflate(TEMPEST_DEFLECT_RADIUS);
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area, p -> p.isAlive() && p.getOwner() != player)) {
			Vec3 velocity = projectile.getDeltaMovement();
			Vec3 toPlayer = center.subtract(projectile.position());
			if (velocity.lengthSqr() < 0.01 || velocity.dot(toPlayer) <= 0.0) {
				continue;
			}
			projectile.deflect((p, deflector, random, power) -> {
				Vec3 away = p.position().subtract(center).normalize();
				double speed = Math.max(0.5, p.getDeltaMovement().length() * 0.8);
				p.setDeltaMovement(away.scale(speed).add(0.0, 0.15, 0.0));
				p.needsSync = true;
			}, player, EntityReference.of((Entity)player), false, 1.0);
			level.sendParticles(ParticleTypes.SMALL_GUST, projectile.getX(), projectile.getY(), projectile.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	// ---------------------------------------------------------------- static and shock

	private static void charge(ServerPlayer attacker, LivingEntity target) {
		ServerLevel level = attacker.level();
		// the stun: heavy, brief, and a jolt backward
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, STUN_AMPLIFIER), attacker);
		Vec3 away = target.position().subtract(attacker.position()).horizontal();
		if (away.lengthSqr() > 1.0E-4) {
			Vec3 jolt = away.normalize().scale(STUN_KNOCKBACK);
			target.push(jolt.x, 0.1, jolt.z);
			target.syncVelocity = true;
		}
		if (target.getAttached(SHOCKED) != null) {
			return;
		}
		long now = level.getGameTime();
		Charge previous = STATIC.get(target.getUUID());
		int stacks = (previous == null || now - previous.at() > STATIC_DECAY_TICKS ? 0 : previous.stacks()) + 1;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
			4 + stacks * 3, 0.3, 0.4, 0.3, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 0.6F, 0.8F + stacks * 0.2F);
		if (stacks < STATIC_MAX) {
			STATIC.put(target.getUUID(), new Charge(stacks, now));
			return;
		}
		STATIC.remove(target.getUUID());
		shock(attacker, level, target);
	}

	/** Enough static: it discharges through them, and they can't move at all for a while. */
	private static void shock(ServerPlayer attacker, ServerLevel level, LivingEntity target) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(target.position());
			bolt.setVisualOnly(true);
			bolt.setCause(attacker);
			level.addFreshEntity(bolt);
		}
		target.setInvulnerableTime(0);
		target.hurtServer(level, attacker.damageSources().source(DamageTypes.LIGHTNING_BOLT, attacker), SHOCK_DAMAGE);
		root(target, true);
		target.setAttached(SHOCKED, true);
		SHOCKS.removeIf(s -> s.entity().equals(target.getUUID()));
		SHOCKS.add(new Shock(level.dimension(), target.getUUID(), level.getGameTime() + SHOCK_TICKS));
	}

	private static void root(LivingEntity target, boolean on) {
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (on) {
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SHOCK_SPEED_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (jump != null) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(SHOCK_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
			target.syncVelocity = true;
		} else {
			if (speed != null) {
				speed.removeModifier(SHOCK_SPEED_ID);
			}
			if (jump != null) {
				jump.removeModifier(SHOCK_JUMP_ID);
			}
		}
	}

	private static void serverTick(MinecraftServer server) {
		if (!SHOCKS.isEmpty()) {
			for (Iterator<Shock> it = SHOCKS.iterator(); it.hasNext(); ) {
				Shock shock = it.next();
				ServerLevel level = server.getLevel(shock.dimension());
				Entity entity = level == null ? null : level.getEntity(shock.entity());
				if (!(entity instanceof LivingEntity living) || level.getGameTime() >= shock.until() || !living.isAlive()) {
					it.remove();
					if (entity instanceof LivingEntity living) {
						root(living, false);
						living.removeAttached(SHOCKED);
					}
					continue;
				}
				if (living instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				Vec3 v = living.getDeltaMovement();
				if (v.x != 0.0 || v.z != 0.0) {
					living.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
					living.syncVelocity = true;
				}
				if (level.getGameTime() % 4 == 0) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
						3, 0.3, 0.5, 0.3, 0.15);
				}
			}
		}
		if (server.getTickCount() % 200 == 0 && !STATIC.isEmpty()) {
			long now = server.overworld().getGameTime();
			STATIC.values().removeIf(c -> now - c.at() > STATIC_DECAY_TICKS);
		}
	}

	// ---------------------------------------------------------------- Call Lightning (Stormbound B)

	static void callLightning(ServerPlayer player, float f, float scale) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(CALL_RANGE));
		HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at);
			bolt.setVisualOnly(true);
			bolt.setCause(player);
			level.addFreshEntity(bolt);
		}
		float damage = Mth.lerp(f, CALL_DAMAGE_MIN, CALL_DAMAGE_MAX) * scale;
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
		AABB area = new AABB(at, at).inflate(CALL_RADIUS, CALL_RADIUS + 3.0, CALL_RADIUS);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != player && !e.isSpectator())) {
			target.hurtServer(level, source, damage);
			if (!target.fireImmune()) {
				target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 60));
			}
			if (storm(player)) {
				charge(player, target);
			}
		}
		player.causeFoodExhaustion(Mth.lerp(f, CALL_EXHAUSTION_MIN, CALL_EXHAUSTION_MAX));
		// the air was thick with static: some of it finds you
		if (!player.isCreative()) {
			player.hurtServer(level, player.damageSources().source(DamageTypes.LIGHTNING_BOLT), Mth.lerp(f, CALL_SELF_DAMAGE_MIN, CALL_SELF_DAMAGE_MAX));
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
	}

	// ---------------------------------------------------------------- the starter's cloak

	/** The faint cloak of wind: a little speed, for as long as the wind is yours. */
	static void cloak(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (on) {
			if (!speed.hasModifier(CLOAK_ID)) {
				speed.addTransientModifier(new AttributeModifier(CLOAK_ID, CLOAK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		} else {
			speed.removeModifier(CLOAK_ID);
		}
	}

	/** The power went: everything off. */
	static void clear(ServerPlayer player) {
		player.removeAttached(MODE);
		cloak(player, false);
	}
}
