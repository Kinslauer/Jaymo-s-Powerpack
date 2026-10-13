package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * A roaming symbiote (see {@link WebSymbioteBlob}) with no web slinger or risen body about makes do with a mob near
 * (only some will do: see {@link #takeable}): a cow, a sheep, a zombie, a skeleton. It pours over it, and the mob wears the black suit over its own body for a while.
 * Nothing living can keep it: a peaceful host is spat out as inadequate after {@code CALM_MIN}-{@code CALM_MAX} ticks
 * (the mob reels, but lives), and the symbiote drops off to look again. A hostile mob keeps it longer
 * ({@code HOSTILE_MIN}-{@code HOSTILE_MAX}): stronger, faster, lashing with tendrils at everything near (as a behemoth
 * does), but unfit for it, so it wastes away ({@code DECAY} of its health a second) and dies by the end. Whenever a
 * host dies, the symbiote drops out where it fell.
 */
public final class WebInhabit {
	// ---- tuning ----
	/** How long a peaceful mob holds it before it's rejected, and a hostile one before it dies (ticks). */
	static final int CALM_MIN = 400, CALM_MAX = 800, HOSTILE_MIN = 1200, HOSTILE_MAX = 1800;
	/** A hostile host: extra health, melee damage, speed (share), knockback resistance. */
	static final double HEALTH_BONUS = 20.0, DAMAGE_BONUS = 4.0, SPEED_BONUS = 0.25, KNOCKBACK_RESIST = 0.4;
	/** A hostile host wastes away: this share of its max health every second, more as it nears its end (up to x3). */
	static final float DECAY = 0.012F;
	/** A rejected host reels this long (ticks). */
	static final int REJECT_REEL = 100;

	/** When the symbiote leaves (game time): rejected or dead by then. Saved, synced (clients draw the suit over it). */
	public static final AttachmentType<Long> HOST = AttachmentRegistry.create(JaymosPowers.id("web_inhabited"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));

	private static final Set<Mob> HOSTS = Collections.newSetFromMap(new WeakHashMap<>());

	private WebInhabit() {
	}

	public static boolean isHost(Entity entity) {
		return entity.hasAttached(HOST);
	}

	/** Inhabited by a hostile mob: it lashes with tendrils (clients draw them). */
	public static boolean lashing(Entity entity) {
		return entity instanceof Enemy && isHost(entity);
	}

	/**
	 * A mob it could take: villagers, a few farm animals (cow, chicken, sheep, pig) or a humanoid hostile (zombies of
	 * every kind, skeletons, illagers, witches, piglins). Villagers and animals are always rejected in the end. Not one taken already, and not a risen player (that one is a behemoth's).
	 */
	static boolean takeable(Entity entity) {
		if (!(entity instanceof Mob mob) || !mob.isAlive() || isHost(mob) || WebBehemoth.isHost(mob) || KitchenCompat.isZombiePlayer(mob)) {
			return false;
		}
		return mob instanceof net.minecraft.world.entity.npc.villager.AbstractVillager || mob instanceof net.minecraft.world.entity.animal.cow.Cow || mob instanceof net.minecraft.world.entity.animal.chicken.Chicken
			|| mob instanceof net.minecraft.world.entity.animal.sheep.Sheep || mob instanceof net.minecraft.world.entity.animal.pig.Pig
			|| mob instanceof net.minecraft.world.entity.monster.zombie.Zombie || mob instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
			|| mob instanceof net.minecraft.world.entity.monster.illager.AbstractIllager || mob instanceof net.minecraft.world.entity.monster.Witch
			|| mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin;
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
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (isHost(entity) && entity.level() instanceof ServerLevel level) {
				entity.removeAttached(HOST);
				com.jaymospowers.core.Oddities.unmark(entity, com.jaymospowers.core.Oddities.SYMBIOTE);
				WebSymbioteBlob.release(level, entity.position()).spurn(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOSTS.clear());
	}

	/** It pours itself over {@code mob}. */
	static void inhabit(ServerLevel level, Mob mob) {
		boolean hostile = mob instanceof Enemy;
		long now = level.getGameTime();
		int stay = hostile ? HOSTILE_MIN + mob.getRandom().nextInt(HOSTILE_MAX - HOSTILE_MIN + 1)
			: CALM_MIN + mob.getRandom().nextInt(CALM_MAX - CALM_MIN + 1);
		mob.setAttached(HOST, now + stay);
		com.jaymospowers.core.Oddities.mark(mob, com.jaymospowers.core.Oddities.SYMBIOTE);
		if (hostile) {
			boost(mob, Attributes.MAX_HEALTH, "inhabit_health", HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
			boost(mob, Attributes.ATTACK_DAMAGE, "inhabit_damage", DAMAGE_BONUS, AttributeModifier.Operation.ADD_VALUE);
			boost(mob, Attributes.MOVEMENT_SPEED, "inhabit_speed", SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			boost(mob, Attributes.KNOCKBACK_RESISTANCE, "inhabit_knockback", KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
			mob.setHealth(mob.getMaxHealth());
		}
		mob.setPersistenceRequired();
		HOSTS.add(mob);
		Vec3 at = mob.position();
		double h = mob.getBbHeight();
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + h * 0.5, at.z, 40, mob.getBbWidth() * 0.5, h * 0.4, mob.getBbWidth() * 0.5, 0.08);
		level.sendParticles(WebSymbiote.dust(1.6F), at.x, at.y + h * 0.5, at.z, 30, mob.getBbWidth() * 0.5, h * 0.4, mob.getBbWidth() * 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.HOSTILE, 1.3F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.2F, 0.4F);
		if (hostile) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0F, 1.3F);
		}
	}

	private static void boost(Mob mob, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(JaymosPowers.id(id), amount, operation));
		}
	}

	private static void tick(Mob host) {
		ServerLevel level = (ServerLevel)host.level();
		long now = level.getGameTime();
		long until = host.getAttachedOrElse(HOST, now);
		if (host instanceof Enemy) {
			// lashing at everything near, and wasting away, faster toward the end
			WebBehemoth.lash(level, host, now);
			if (now >= until) {
				host.kill(level);
				return;
			}
			if (host.tickCount % 20 == 0) {
				long left = until - now;
				float urgency = 1.0F + 2.0F * (1.0F - Math.min(1.0F, left / (float)HOSTILE_MIN));
				host.hurtServer(level, level.damageSources().magic(), host.getMaxHealth() * DECAY * urgency);
			}
		} else if (now >= until) {
			reject(level, host);
			return;
		}
		if (host.getRandom().nextInt(5) == 0) {
			level.sendParticles(WebSymbiote.dust(0.9F), host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 1,
				host.getBbWidth() * 0.4, host.getBbHeight() * 0.3, host.getBbWidth() * 0.4, 0.0);
		}
		if (host.tickCount % 100 == 0) {
			level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.6F, 0.8F);
		}
	}

	/** Not good enough: it spits the mob out (reeling, but alive) and drops off to look again. */
	private static void reject(ServerLevel level, Mob host) {
		host.removeAttached(HOST);
		com.jaymospowers.core.Oddities.unmark(host, com.jaymospowers.core.Oddities.SYMBIOTE);
		host.removeAttached(WebBehemoth.STRIKE);
		HOSTS.remove(host);
		host.addEffect(new MobEffectInstance(MobEffects.NAUSEA, REJECT_REEL, 0));
		host.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REJECT_REEL, 1));
		host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, REJECT_REEL, 0));
		WebSymbioteBlob.release(level, host.position()).spurn(host);
	}
}
