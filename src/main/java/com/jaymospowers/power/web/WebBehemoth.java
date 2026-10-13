package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A risen body the symbiote has taken (Kitchen's zombie player): a feral undead behemoth in the big body. It hunts
 * every living thing it can see, and its tendrils never stop: each one thrashes on its own and lashes at whatever
 * living thing is in reach, anything at all, several at once, the symbiote fighting for its life. It never lets go of
 * this body, and it has everything a symbiote has near death in a player: it roars (everything near hurt and hurled,
 * harm shrugged off while it does), and once it's badly hurt it goes into a frenzy (lashes twice as often, harder,
 * snatching prey and hurling it), and when nearly dead it vanishes into the Shroud and leaps clear to heal before it
 * comes back. Until something kills it; then the symbiote crawls
 * out again (as {@link WebSymbioteBlob}) and goes on looking for a host. The sun doesn't burn it and water doesn't
 * drown it (see {@code mixin.WebHostZombieMixin}).
 */
public final class WebBehemoth {
	// ---- tuning ----
	/** On top of the zombie's own: health, melee damage, size (+70%: the big body), speed (x1.3), knockback resistance. */
	static final double HEALTH_BONUS = 160.0, DAMAGE_BONUS = 10.0, SCALE_BONUS = 0.7, SPEED_BONUS = 0.3, KNOCKBACK_RESIST = 0.8;
	/**
	 * Its takeover moves. The roar: every {@code ROAR_EVERY} ticks with prey within {@code WebSymbiote.ROAR_RADIUS}, and
	 * at once when its health first falls under half and under a quarter; it shrugs off harm ({@code ROAR_RESISTANCE})
	 * through it. The frenzy: under {@code FRENZY_BELOW} of its health, lashes come twice as often, hit
	 * {@code FRENZY_DAMAGE} times as hard, and {@code SNATCH_CHANCE} of them snatch the prey and hurl it. The Shroud:
	 * under {@code SHROUD_BELOW}, unseen for {@code SHROUD_TICKS}, a leap clear and a quick heal, every {@code SHROUD_EVERY}.
	 */
	static final int ROAR_EVERY = 300, ROAR_RESIST_TICKS = 40, ROAR_RESISTANCE = 2;
	static final float ROAR_DAMAGE = 10.0F;
	static final float FRENZY_BELOW = 0.5F, FRENZY_DAMAGE = 1.4F, SNATCH_CHANCE = 0.18F;
	static final double HURL = 1.8, HURL_LIFT = 0.9;
	static final float SHROUD_BELOW = 0.25F;
	static final int SHROUD_TICKS = 100, SHROUD_EVERY = 1200, SHROUD_REGEN = 2;
	static final double SHROUD_LEAP = 1.4;
	/** It sees prey this far off (in line of sight), and picks new prey this often (ticks). */
	static final double SIGHT = 24.0;
	static final int RETARGET_EVERY = 10;
	/**
	 * Its back tendrils, each lashing on its own: {@code STRIKE_TICKS} long, landing at {@code STRIKE_HIT}, then
	 * {@code STRIKE_GAP_MIN} to {@code STRIKE_GAP_MAX} ticks before that tendril goes again, at a random living thing up
	 * to {@code STRIKE_REACH} away.
	 */
	public static final int TENDRILS = 6;
	public static final int STRIKE_TICKS = 10, STRIKE_HIT = 5;
	static final int STRIKE_GAP_MIN = 1, STRIKE_GAP_MAX = 5;
	public static final double STRIKE_REACH = 7.5;
	static final float STRIKE_DAMAGE = 10.0F;
	static final double STRIKE_KNOCK = 1.1;

	/** When the symbiote took this body (saved, synced: clients draw the big body and its tendrils). */
	public static final AttachmentType<Long> HOST = AttachmentRegistry.create(JaymosPowers.id("web_behemoth"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));

	/** A lash under way: which tendril, at whom, and since when (synced, for the tendrils' swing). */
	public record Strike(int tendril, int target, long start) {
		public static final StreamCodec<ByteBuf, Strike> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Strike::tendril, ByteBufCodecs.VAR_INT, Strike::target, ByteBufCodecs.VAR_LONG, Strike::start, Strike::new);
	}

	/** Every lash under way (one per tendril at most). */
	public static final AttachmentType<java.util.List<Strike>> STRIKE = AttachmentRegistry.create(JaymosPowers.id("web_behemoth_strike"),
		builder -> builder.syncWith(Strike.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

	private static final java.util.Set<Mob> HOSTS = java.util.Collections.newSetFromMap(new WeakHashMap<>());
	/** When each tendril may lash again. */
	private static final Map<Mob, long[]> NEXT_STRIKE = new WeakHashMap<>();
	/** Per behemoth: next roar, whether it has roared at half and at a quarter, when it may Shroud again. */
	private static final Map<Mob, long[]> FURY = new WeakHashMap<>();

	private WebBehemoth() {
	}

	public static boolean isHost(Entity entity) {
		return entity.hasAttached(HOST);
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isHost(mob)) {
				HOSTS.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				HOSTS.remove(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Mob host : new ArrayList<>(HOSTS)) {
				if (host.isRemoved() || !host.isAlive() || !(host.level() instanceof ServerLevel)) {
					HOSTS.remove(host);
				} else {
					tick(host);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(WebBehemoth::afterDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOSTS.clear();
			NEXT_STRIKE.clear();
			FURY.clear();
		});
	}

	/** The symbiote pours itself into a risen body: it swells into the big body and goes hunting. */
	static void possess(ServerLevel level, Mob host) {
		host.setAttached(HOST, level.getGameTime());
		com.jaymospowers.core.Oddities.mark(host, com.jaymospowers.core.Oddities.SYMBIOTE);
		boost(host, Attributes.MAX_HEALTH, "behemoth_health", HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		boost(host, Attributes.ATTACK_DAMAGE, "behemoth_damage", DAMAGE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		boost(host, Attributes.SCALE, "behemoth_scale", SCALE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		boost(host, Attributes.MOVEMENT_SPEED, "behemoth_speed", SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		boost(host, Attributes.KNOCKBACK_RESISTANCE, "behemoth_knockback", KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
		boost(host, Attributes.FOLLOW_RANGE, "behemoth_sight", SIGHT, AttributeModifier.Operation.ADD_VALUE);
		host.setHealth(host.getMaxHealth());
		host.setPersistenceRequired();
		HOSTS.add(host);
		Vec3 at = host.position();
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0, at.z, 80, 0.6, 1.0, 0.6, 0.12);
		level.sendParticles(WebSymbiote.dust(2.0F), at.x, at.y + 1.0, at.z, 60, 0.6, 1.0, 0.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.2F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.4F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5F, 0.6F);
	}

	private static void boost(Mob host, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = host.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(JaymosPowers.id(id), amount, operation));
		}
	}

	private static void tick(Mob host) {
		ServerLevel level = (ServerLevel) host.level();
		long now = level.getGameTime();
		LivingEntity target = host.getTarget();
		if (host.tickCount % RETARGET_EVERY == 0 && (target == null || !target.isAlive() || !host.hasLineOfSight(target)
			|| target.distanceTo(host) > SIGHT || !prey(host, target))) {
			// anything it can see
			target = level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(SIGHT),
					e -> prey(host, e) && e.distanceTo(host) <= SIGHT && host.hasLineOfSight(e)).stream()
				.min(Comparator.comparingDouble(e -> e.distanceToSqr(host))).orElse(null);
			if (target != null) {
				host.setTarget(target);
			}
		}
		lash(level, host, now);
		fury(level, host, target, now);
		if (host.tickCount % 80 == 0) {
			level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 0.7F, 0.6F);
		}
		if (host.getRandom().nextInt(4) == 0) {
			level.sendParticles(WebSymbiote.dust(1.0F), host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(), 1,
				host.getBbWidth() * 0.4, host.getBbHeight() * 0.3, host.getBbWidth() * 0.4, 0.0);
		}
	}

	/** Under half its health a behemoth is in a frenzy. */
	static boolean frenzied(Mob host) {
		return isHost(host) && host.getHealth() < host.getMaxHealth() * FRENZY_BELOW;
	}

	/** The roar and the Shroud (the frenzy is in its lashes). */
	private static void fury(ServerLevel level, Mob host, LivingEntity target, long now) {
		long[] fury = FURY.computeIfAbsent(host, h -> new long[] {now + ROAR_EVERY, 0L, 0L, 0L});
		float health = host.getHealth() / host.getMaxHealth();
		boolean near = !level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(WebSymbiote.ROAR_RADIUS), e -> prey(host, e)).isEmpty();
		boolean roar = near && now >= fury[0];
		if (health < 0.5F && fury[1] == 0L) {
			fury[1] = 1L;
			roar = true;
		}
		if (health < 0.25F && fury[2] == 0L) {
			fury[2] = 1L;
			roar = true;
		}
		if (roar) {
			fury[0] = now + ROAR_EVERY;
			roar(level, host);
		}
		if (health < SHROUD_BELOW && now >= fury[3]) {
			fury[3] = now + SHROUD_EVERY;
			shroud(level, host, target);
		}
	}

	/** It throws its head back and roars: everything near hurt and hurled, and harm shrugged off while it does. */
	private static void roar(ServerLevel level, Mob host) {
		Vec3 center = host.position().add(0.0, host.getBbHeight() * 0.55, 0.0);
		double radius = WebSymbiote.ROAR_RADIUS * Math.max(1.0, host.getScale() * 0.8);
		host.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, ROAR_RESIST_TICKS, ROAR_RESISTANCE,
			false, false));
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(radius), e -> prey(host, e))) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(center);
			if (to.length() > radius + living.getBbWidth() * 0.5) {
				continue;
			}
			living.hurtServer(level, level.damageSources().mobAttack(host), ROAR_DAMAGE);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			double force = WebSymbiote.ROAR_KNOCKBACK * (1.2 - 0.5 * Math.min(1.0, to.length() / radius));
			fling(living, flat.scale(force).add(0.0, 0.5, 0.0));
			if (living instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new WebNet.Shake(WebSymbiote.ROAR_SHAKE));
			}
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 0.4, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * radius / 3.0;
			int count = 10 + ring * 6;
			for (int k = 0; k < count; k++) {
				double a = k * Math.PI * 2.0 / count;
				level.sendParticles(WebSymbiote.dust(1.7F - ring * 0.25F), center.x + Math.cos(a) * r, center.y - 0.4, center.z + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 0.4, center.z, 40, 0.5, 0.5, 0.5, 0.3);
		level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 0.7F);
		level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.5F);
	}

	/** Nearly dead: it vanishes into the Shroud and leaps clear, away from what hurt it, healing as it goes. */
	private static void shroud(ServerLevel level, Mob host, LivingEntity threat) {
		host.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, SHROUD_TICKS, 0, false, false));
		host.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, SHROUD_TICKS, SHROUD_REGEN, false, false));
		host.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, SHROUD_TICKS, 1, false, false));
		Vec3 away = threat == null ? Vec3.directionFromRotation(0.0F, host.getRandom().nextFloat() * 360.0F)
			: host.position().subtract(threat.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		host.setDeltaMovement(away.scale(SHROUD_LEAP).add(0.0, 0.7, 0.0));
		host.needsSync = true;
		host.getNavigation().stop();
		Vec3 at = host.position();
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0, at.z, 60, 0.6, 1.0, 0.6, 0.15);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 20, 0.6, 0.8, 0.6, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.2F, 0.8F);
	}

	private static void fling(LivingEntity living, Vec3 push) {
		living.setDeltaMovement(living.getDeltaMovement().add(push));
		living.needsSync = true;
		living.syncVelocity = true;
		if (living instanceof ServerPlayer player) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new WebNet.Nudge(player.getDeltaMovement()));
		}
	}

	/**
	 * Its tendrils, every tick: each lash lands halfway and ends; every free tendril whose rest is up lashes at a random
	 * living thing in reach (it doesn't pick: anything will do).
	 */
	static void lash(ServerLevel level, Mob host, long now) {
		java.util.List<Strike> old = host.getAttachedOrElse(STRIKE, java.util.List.of());
		java.util.List<Strike> lashes = new ArrayList<>();
		long[] next = NEXT_STRIKE.computeIfAbsent(host, h -> new long[TENDRILS]);
		boolean[] busy = new boolean[TENDRILS];
		for (Strike strike : old) {
			long t = now - strike.start();
			if (t == STRIKE_HIT) {
				land(level, host, level.getEntity(strike.target()));
			}
			if (t >= STRIKE_TICKS || strike.tendril() < 0 || strike.tendril() >= TENDRILS) {
				if (strike.tendril() >= 0 && strike.tendril() < TENDRILS) {
					int gap = STRIKE_GAP_MIN + host.getRandom().nextInt(STRIKE_GAP_MAX - STRIKE_GAP_MIN + 1);
					next[strike.tendril()] = now + (frenzied(host) ? gap / 2 : gap);
				}
				continue;
			}
			busy[strike.tendril()] = true;
			lashes.add(strike);
		}
		java.util.List<LivingEntity> near = null;
		for (int t = 0; t < TENDRILS; t++) {
			if (busy[t] || now < next[t]) {
				continue;
			}
			if (near == null) {
				near = level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(STRIKE_REACH),
					e -> prey(host, e) && e.distanceTo(host) <= STRIKE_REACH && host.hasLineOfSight(e));
			}
			if (near.isEmpty()) {
				break;
			}
			LivingEntity victim = near.get(host.getRandom().nextInt(near.size()));
			lashes.add(new Strike(t, victim.getId(), now));
			if (host.getRandom().nextInt(3) == 0) {
				level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.0F,
					0.4F + host.getRandom().nextFloat() * 0.3F);
			}
		}
		if (!lashes.equals(old)) {
			if (lashes.isEmpty()) {
				host.removeAttached(STRIKE);
			} else {
				host.setAttached(STRIKE, java.util.List.copyOf(lashes));
			}
		}
	}

	/** What it hunts: every living thing but other behemoths, roaming symbiotes, stands, and players who can't be hurt. */
	private static boolean prey(Mob host, LivingEntity e) {
		return e != host && e.isAlive() && !isHost(e) && !(e instanceof WebSymbioteBlob) && !(e instanceof ArmorStand)
			&& !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	private static void land(ServerLevel level, Mob host, Entity hit) {
		if (!(hit instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(host) > STRIKE_REACH + 1.5) {
			return;
		}
		DamageSource source = level.damageSources().mobAttack(host);
		boolean frenzy = frenzied(host);
		if (frenzy && host.getRandom().nextFloat() < SNATCH_CHANCE && living.hurtServer(level, source, STRIKE_DAMAGE * FRENZY_DAMAGE)) {
			// snatched up and hurled away
			Vec3 away = living.position().subtract(host.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			fling(living, away.scale(HURL).add(0.0, HURL_LIFT, 0.0));
			level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.5F);
		} else if (living.hurtServer(level, source, STRIKE_DAMAGE * (frenzy ? FRENZY_DAMAGE : 1.0F))) {
			Vec3 push = living.position().subtract(host.position()).horizontal();
			push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(STRIKE_KNOCK);
			living.setDeltaMovement(living.getDeltaMovement().add(push.x, 0.35, push.z));
			living.needsSync = true;
			if (living instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new WebNet.Nudge(push.add(0.0, 0.35, 0.0)));
			}
		}
		Vec3 at = living.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** Its body is done for: the symbiote crawls out and goes looking again. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (isHost(entity) && entity.level() instanceof ServerLevel level) {
			entity.removeAttached(HOST);
			com.jaymospowers.core.Oddities.unmark(entity, com.jaymospowers.core.Oddities.SYMBIOTE);
			WebSymbioteBlob.release(level, entity.position());
		}
	}
}
