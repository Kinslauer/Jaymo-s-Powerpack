package com.jaymoskingdoms;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.492) A great foe's posture, the one piece of Gemini's "DawnCraft" boss handoff that the pack didn't have yet (its
 * idea only, written our own way on the pack's bosses; nothing of DawnCraft or Epic Fight is copied). Every boss with a
 * bar ({@link #applies}: kings, lords, chiefs, warlords, champions, Aether Giants, the Ogre King, the Skeleton Lord, the
 * Piglin King) carries a posture beside its health:
 * <ul>
 * <li>Blows wear it: a melee blow by what it took ({@link #MELEE_SHARE}), a sprinting or falling (critical) blow more
 * ({@link #HEAVY_FACTOR}), an arrow or thrown weapon less ({@link #RANGED_SHARE}). A blow of its own caught on a shield or
 * a raised guard wears it too ({@link #BLOCKED_SHARE}).</li>
 * <li>Left alone for {@link #REFILL_DELAY} ticks it comes back, full again in about {@link #REFILL_TICKS}.</li>
 * <li>Broken, the foe sinks to a knee for {@link #KNEEL_TICKS}: it stops, bows its head, can't strike, a gold flash and a
 * ring of iron. The first blow struck on it then is a riposte, {@link #RIPOSTE}x, and brings it back up. Either way its
 * posture is full again and can't be worn for {@link #IMMUNE_TICKS}, so a fight is never a stun lock.</li>
 * <li>The players who have its health bar ({@link BossBars}) also see a thin posture bar under it while it's worn.</li>
 * </ul>
 * No chat text: the bar, the sounds and the sparks say it all. Powers' Z Fighter poise ({@code ZFighterPoise}) is its own
 * thing in a Z Fighter's fight and isn't touched.
 */
public final class BossPosture {
	/** A posture of {@code BASE + maxHealth * PER_HEALTH}. */
	static final float BASE = 20.0F;
	static final float PER_HEALTH = 0.35F;
	/** How much of a melee blow's damage comes off the posture. */
	static final float MELEE_SHARE = 1.0F;
	/** A sprinting or falling blow's extra. */
	static final float HEAVY_FACTOR = 1.6F;
	/** An arrow's, javelin's or thrown blade's share. */
	static final float RANGED_SHARE = 0.4F;
	/** Of the foe's own blow caught on a shield or guard. */
	static final float BLOCKED_SHARE = 0.75F;
	/** Quiet ticks before it starts coming back. */
	static final int REFILL_DELAY = 60;
	/** Ticks from empty to full once it's coming back. */
	static final int REFILL_TICKS = 100;
	/** How long it kneels. */
	static final int KNEEL_TICKS = 60;
	/** The riposte's multiplier on the first blow while it kneels. */
	static final float RIPOSTE = 2.25F;
	/** After a kneel, how long its posture can't be worn. */
	static final int IMMUNE_TICKS = 160;
	/** What an armoured foe ({@link #armoured}) takes of a blow while it is on its feet. */
	static final float ARMOURED_SHARE = 0.5F;
	/** How the head bows while it kneels (degrees). */
	static final float BOW_PITCH = 55.0F;

	private static final class Posture {
		float left;
		long lastHit;
		long kneelFrom;
		long kneelUntil;
		long immuneUntil;
		final ServerBossEvent bar;

		Posture(LivingEntity boss) {
			this.left = max(boss);
			this.bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("bar.jaymoskingdoms.posture"),
				BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
		}

		boolean kneeling(long now) {
			return now < this.kneelUntil;
		}
	}

	private static final Map<LivingEntity, Posture> POSTURES = new IdentityHashMap<>();

	private BossPosture() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BossPosture::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POSTURES.values().forEach(p -> p.bar.removeAllPlayers());
			POSTURES.clear();
		});
	}

	/** The pack's bosses: whatever has a Kingdoms boss bar. Vanilla's bosses (the Wither, the dragon) keep their own fight. */
	public static boolean applies(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive()
			&& (BossBars.boss(entity) || OgreHolds.king(entity) || entity instanceof SkeletonLord
				|| entity instanceof com.jaymoskingdoms.beasts.PiglinKing);
	}

	static float max(LivingEntity boss) {
		return BASE + boss.getMaxHealth() * PER_HEALTH;
	}

	/** Whether {@code entity} is down on one knee just now, open to a riposte. */
	public static boolean kneeling(LivingEntity entity) {
		Posture p = POSTURES.get(entity);
		return p != null && p.kneeling(entity.level().getGameTime());
	}

	/** From {@code LivingEntityHurtMixin}: a melee blow on a kneeling foe is a riposte. */
	public static float dealt(LivingEntity self, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		Posture p = POSTURES.get(self);
		long now = self.level().getGameTime();
		if (p != null && p.kneeling(now) && now >= p.kneelFrom) {
			return riposteBlow(source) ? amount * RIPOSTE : amount;
		}
		// hide and plate: only a share gets through until its posture breaks
		return source.getEntity() instanceof LivingEntity && armoured(self) ? amount * ARMOURED_SHARE : amount;
	}

	/**
	 * Great foes in stone hide or heavy plate: the Aether Giants and the Ogre King. On their feet they take
	 * {@link #ARMOURED_SHARE} of a blow; kneeling, all of it (and the riposte). Their posture wears as if the whole
	 * blow had landed, so breaking it is the way through.
	 */
	public static boolean armoured(Entity entity) {
		return entity instanceof Mob && (com.jaymoskingdoms.remnants.Monoliths.giant(entity) || OgreHolds.king(entity));
	}

	private static boolean riposteBlow(DamageSource source) {
		Entity attacker = source.getEntity();
		return attacker instanceof LivingEntity && source.getDirectEntity() == attacker;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		// the foe's own blow caught on a shield or a guard
		if (blocked && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && applies(attacker)) {
			wear(level, attacker, baseDamage * BLOCKED_SHARE);
		}
		if (damageTaken <= 0.0F || !applies(entity) || Downed.isDowned(entity)) {
			return;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity hitter) || attacker == entity) {
			return;
		}
		long now = level.getGameTime();
		Posture p = POSTURES.get(entity);
		if (p != null && p.kneeling(now)) {
			if (now >= p.kneelFrom && riposteBlow(source)) {
				riposte(level, entity, p, now);
			}
			return;
		}
		float wear;
		if (source.getDirectEntity() == attacker) {
			boolean heavy = hitter.isSprinting() || !hitter.onGround() && hitter.fallDistance > 0.0 && !hitter.isInWater();
			wear = damageTaken * MELEE_SHARE * (heavy ? HEAVY_FACTOR : 1.0F);
		} else {
			wear = damageTaken * RANGED_SHARE;
		}
		if (armoured(entity)) {
			// the share its hide turned still counts against its posture, and is heard
			wear /= ARMOURED_SHARE;
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.5F, 0.6F);
		}
		wear(level, entity, wear);
	}

	static void wear(ServerLevel level, LivingEntity boss, float amount) {
		if (amount <= 0.0F || Downed.isDowned(boss)) {
			return;
		}
		long now = level.getGameTime();
		Posture p = POSTURES.computeIfAbsent(boss, Posture::new);
		if (now < p.immuneUntil || p.kneeling(now)) {
			return;
		}
		p.lastHit = now;
		p.left -= amount;
		if (p.left <= 0.0F) {
			kneel(level, boss, p, now);
		}
	}

	private static void kneel(ServerLevel level, LivingEntity boss, Posture p, long now) {
		p.left = 0.0F;
		// the blow that broke it isn't the riposte: the next one is
		p.kneelFrom = now + 1;
		BossBars.show(level, boss, p.bar);
		p.kneelUntil = now + KNEEL_TICKS;
		p.bar.setColor(BossEvent.BossBarColor.YELLOW);
		boss.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, KNEEL_TICKS, 9, false, false));
		boss.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, KNEEL_TICKS, 9, false, false));
		hold(boss);
		Vec3 chest = boss.position().add(0.0, boss.getBbHeight() * 0.6, 0.0);
		double spread = boss.getBbWidth() * 0.4;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 30, spread, 0.4, spread, 0.15);
		level.sendParticles(ParticleTypes.WAX_OFF, chest.x, chest.y, chest.z, 16, spread, 0.4, spread, 0.05);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
	}

	private static void riposte(ServerLevel level, LivingEntity boss, Posture p, long now) {
		Vec3 chest = boss.position().add(0.0, boss.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.CRIT, chest.x, chest.y, chest.z, 30, 0.5, 0.5, 0.5, 0.25);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, chest.x, chest.y, chest.z, 16, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.4F, 0.7F);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		PowersCompat.impactFrame(level, chest);
		rise(boss, p, now);
	}

	/** Back on its feet, posture full, unwearable for a while. */
	private static void rise(LivingEntity boss, Posture p, long now) {
		p.kneelUntil = 0L;
		p.immuneUntil = now + IMMUNE_TICKS;
		p.left = max(boss);
		p.bar.setColor(BossEvent.BossBarColor.WHITE);
		boss.removeEffect(MobEffects.SLOWNESS);
		boss.removeEffect(MobEffects.WEAKNESS);
	}

	/** Stopped where it kneels, off its quarry, head bowed. */
	private static void hold(LivingEntity boss) {
		if (boss instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		boss.setDeltaMovement(0.0, Math.min(0.0, boss.getDeltaMovement().y), 0.0);
		boss.setXRot(BOW_PITCH);
	}

	private static void tick() {
		Iterator<Map.Entry<LivingEntity, Posture>> it = POSTURES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Posture> entry = it.next();
			LivingEntity boss = entry.getKey();
			Posture p = entry.getValue();
			if (boss.isRemoved() || !boss.isAlive() || Downed.isDowned(boss) || !(boss.level() instanceof ServerLevel level)) {
				p.bar.removeAllPlayers();
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (p.kneelUntil > 0 && now >= p.kneelUntil) {
				// up again without a riposte
				rise(boss, p, now);
			} else if (p.kneeling(now)) {
				hold(boss);
			} else if (now - p.lastHit >= REFILL_DELAY) {
				p.left = Math.min(max(boss), p.left + max(boss) / REFILL_TICKS);
				if (p.left >= max(boss) && now >= p.immuneUntil) {
					// a full posture is just no entry
					p.bar.removeAllPlayers();
					it.remove();
					continue;
				}
			}
			p.bar.setProgress(Math.clamp(p.left / max(boss), 0.0F, 1.0F));
			if (p.left >= max(boss)) {
				// full (risen and still unwearable): no bar
				p.bar.removeAllPlayers();
			} else if (boss.tickCount % 10 == 0 || p.kneelFrom == now) {
				BossBars.show(level, boss, p.bar);
			}
		}
	}
}
