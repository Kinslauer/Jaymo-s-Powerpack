package com.jaymoskingdoms;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (hs_posture) An ordinary fighter's stance, {@link BossPosture}'s idea scaled down (Hackers 'n Slashers' posture, its idea
 * only, written our own way). Every armed humanoid fighter that isn't a boss ({@link #applies}: the town's guards and
 * knights, raiders, the armed dead and piglins) holds a stance of {@link #BASE} plus {@link #PER_HEALTH} of its health:
 * <ul>
 * <li>A blow it caught on a shield or guard wears it ({@link #BLOCKED_SHARE} of the blow), a blow of its own caught on
 * someone's shield too, a parry it snaps ({@link #PARRY_WEAR}), and a player's plain melee blow a little
 * ({@link #HIT_SHARE}).</li>
 * <li>Left alone for {@link #REFILL_DELAY} ticks it comes back, full again in {@link #REFILL_TICKS}.</li>
 * <li>Broken, it reels for {@link #REEL_TICKS}: it sags (head down), stops, can't strike, its guard is knocked aside and it
 * can't parry, so a blow on it always lands. It then can't be worn again for {@link #IMMUNE_TICKS}.</li>
 * </ul>
 * No bar and no text: the sagging stance, a typed ring and the clang say it. Nothing here changes the blow's damage.
 */
public final class FighterPosture {
	/** A stance of {@code BASE + maxHealth * PER_HEALTH}. */
	static final float BASE = 8.0F;
	static final float PER_HEALTH = 0.2F;
	/** Of a blow caught on a shield or guard (either way), a plain melee blow from a player, and a parry it snaps. */
	static final float BLOCKED_SHARE = 0.75F, HIT_SHARE = 0.25F, PARRY_WEAR = 3.0F;
	/** Quiet ticks before it comes back, and ticks from empty to full. */
	static final int REFILL_DELAY = 80, REFILL_TICKS = 120;
	/** How long it reels, and how long after that its stance can't be worn. */
	static final int REEL_TICKS = 30, IMMUNE_TICKS = 200;
	/** Its raised guard knocked aside for this long (seconds) when the stance breaks. */
	static final float GUARD_DISABLE_SECONDS = 2.0F;
	/** How far the head sags while it reels (degrees of pitch). */
	static final float SAG_PITCH = 40.0F;
	/** The ring when the stance breaks (a pale steel). */
	static final int BREAK_COLOUR = 0xC8CCD2;

	private static final class Stance {
		float left;
		long lastWorn;
		long reelUntil;
		long immuneUntil;

		Stance(Mob mob) {
			this.left = max(mob);
		}

		boolean reeling(long now) {
			return now < this.reelUntil;
		}
	}

	private static final Map<Mob, Stance> STANCES = new IdentityHashMap<>();

	private FighterPosture() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(FighterPosture::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STANCES.clear());
	}

	/** An armed humanoid fighter with no boss posture of its own: not a civilian, not a boss. */
	static boolean applies(Mob mob) {
		return mob.isAlive() && !(mob instanceof Folk folk && !folk.combatant()) && MobCombat.humanoid(mob) && mob.getMainHandItem().has(DataComponents.WEAPON)
			&& !BossPosture.applies(mob) && !Senses.boss(mob) && !Downed.isDowned(mob);
	}

	static float max(Mob mob) {
		return BASE + mob.getMaxHealth() * PER_HEALTH;
	}

	/** Whether {@code mob} is reeling from a broken stance just now: it can't parry, and a blow on it lands. */
	public static boolean reeling(Mob mob) {
		Stance s = STANCES.get(mob);
		return s != null && s.reeling(mob.level().getGameTime());
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Entity attacker = source.getEntity();
		boolean melee = attacker instanceof LivingEntity && source.getDirectEntity() == attacker;
		// a fighter's own blow caught on a shield or guard
		if (blocked && melee && attacker instanceof Mob swinger && applies(swinger)) {
			wear(level, swinger, baseDamage * BLOCKED_SHARE);
		}
		if (!(entity instanceof Mob mob) || !melee || attacker == entity || !applies(mob)) {
			return;
		}
		if (blocked) {
			// a blow it caught on its own guard
			wear(level, mob, baseDamage * BLOCKED_SHARE);
		} else if (damageTaken > 0.0F && attacker instanceof Player) {
			wear(level, mob, damageTaken * HIT_SHARE);
		}
	}

	/** From {@link MobParry}: {@code mob} snapped a parry. */
	static void parried(ServerLevel level, Mob mob) {
		if (applies(mob)) {
			wear(level, mob, PARRY_WEAR);
		}
	}

	private static void wear(ServerLevel level, Mob mob, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		long now = level.getGameTime();
		Stance s = STANCES.computeIfAbsent(mob, Stance::new);
		if (now < s.immuneUntil || s.reeling(now)) {
			return;
		}
		s.lastWorn = now;
		s.left -= amount;
		if (s.left <= 0.0F) {
			reel(level, mob, s, now);
		}
	}

	private static void reel(ServerLevel level, Mob mob, Stance s, long now) {
		s.left = 0.0F;
		s.reelUntil = now + REEL_TICKS;
		s.immuneUntil = now + REEL_TICKS + IMMUNE_TICKS;
		mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REEL_TICKS, 9, false, false));
		mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, REEL_TICKS, 9, false, false));
		// a raised guard is knocked aside, and it can't snap a parry (MobParry)
		MobShields.disable(level, mob, GUARD_DISABLE_SECONDS);
		MobShields.drop(mob);
		sag(mob);
		Vec3 chest = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
		Fx.ring(level, chest, 1.0F, BREAK_COLOUR);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 1.1F);
	}

	/** Stopped where it stands, head sagging. */
	private static void sag(Mob mob) {
		mob.getNavigation().stop();
		mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
		mob.setXRot(SAG_PITCH);
	}

	private static void tick() {
		Iterator<Map.Entry<Mob, Stance>> it = STANCES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Mob, Stance> entry = it.next();
			Mob mob = entry.getKey();
			Stance s = entry.getValue();
			if (mob.isRemoved() || !mob.isAlive()) {
				it.remove();
				continue;
			}
			long now = mob.level().getGameTime();
			if (s.reeling(now)) {
				sag(mob);
			} else if (now - s.lastWorn >= REFILL_DELAY) {
				s.left = Math.min(max(mob), s.left + max(mob) / REFILL_TICKS);
				if (s.left >= max(mob) && now >= s.immuneUntil) {
					it.remove();
				}
			}
		}
	}
}
