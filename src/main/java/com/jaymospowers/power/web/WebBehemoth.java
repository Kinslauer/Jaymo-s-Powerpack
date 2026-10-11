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
 * every living thing it can see, lashing out with its tendrils, until something kills it; then the symbiote crawls
 * out again (as {@link WebSymbioteBlob}) and goes on looking for a host. The sun doesn't burn it and water doesn't
 * drown it (see {@code mixin.WebHostZombieMixin}).
 */
public final class WebBehemoth {
	// ---- tuning ----
	/** On top of the zombie's own: health, melee damage, size (+70%: the big body), speed (x1.3), knockback resistance. */
	static final double HEALTH_BONUS = 80.0, DAMAGE_BONUS = 6.0, SCALE_BONUS = 0.7, SPEED_BONUS = 0.3, KNOCKBACK_RESIST = 0.8;
	/** It sees prey this far off (in line of sight), and picks new prey this often (ticks). */
	static final double SIGHT = 24.0;
	static final int RETARGET_EVERY = 10;
	/** A tendril lash: {@code STRIKE_TICKS} long, landing at {@code STRIKE_HIT}, one every {@code STRIKE_EVERY}, up to {@code STRIKE_REACH} away. */
	public static final int STRIKE_TICKS = 16, STRIKE_HIT = 8;
	static final int STRIKE_EVERY = 12;
	public static final double STRIKE_REACH = 6.5;
	static final float STRIKE_DAMAGE = 12.0F;
	static final double STRIKE_KNOCK = 1.1;

	/** When the symbiote took this body (saved, synced: clients draw the big body and its tendrils). */
	public static final AttachmentType<Long> HOST = AttachmentRegistry.create(JaymosPowers.id("web_behemoth"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));

	/** A lash under way: at whom, and since when (synced, for the tendrils' swing). */
	public record Strike(int target, long start) {
		public static final StreamCodec<ByteBuf, Strike> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Strike::target, ByteBufCodecs.VAR_LONG, Strike::start, Strike::new);
	}

	public static final AttachmentType<Strike> STRIKE = AttachmentRegistry.create(JaymosPowers.id("web_behemoth_strike"),
		builder -> builder.syncWith(Strike.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final java.util.Set<Mob> HOSTS = java.util.Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Mob, Long> NEXT_STRIKE = new WeakHashMap<>();

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
		});
	}

	/** The symbiote pours itself into a risen body: it swells into the big body and goes hunting. */
	static void possess(ServerLevel level, Mob host) {
		host.setAttached(HOST, level.getGameTime());
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
		Strike strike = host.getAttached(STRIKE);
		if (strike != null) {
			long t = now - strike.start();
			if (t == STRIKE_HIT) {
				land(level, host, level.getEntity(strike.target()));
			}
			if (t >= STRIKE_TICKS) {
				host.removeAttached(STRIKE);
				NEXT_STRIKE.put(host, now + STRIKE_EVERY - STRIKE_TICKS);
			}
		} else if (target != null && now >= NEXT_STRIKE.getOrDefault(host, 0L) && prey(host, target)
			&& target.distanceTo(host) <= STRIKE_REACH && host.hasLineOfSight(target)) {
			host.setAttached(STRIKE, new Strike(target.getId(), now));
			level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.0F, 0.5F);
		}
		if (host.tickCount % 80 == 0) {
			level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 0.7F, 0.6F);
		}
		if (host.getRandom().nextInt(4) == 0) {
			level.sendParticles(WebSymbiote.dust(1.0F), host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(), 1,
				host.getBbWidth() * 0.4, host.getBbHeight() * 0.3, host.getBbWidth() * 0.4, 0.0);
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
		if (living.hurtServer(level, source, STRIKE_DAMAGE)) {
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
			WebSymbioteBlob.release(level, entity.position());
		}
	}
}
