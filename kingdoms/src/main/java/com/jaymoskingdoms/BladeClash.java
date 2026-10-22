package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.105) Blade clash, after Just-In COMBAT's block on simultaneous attacks (its idea only, our own code). When two armed
 * fighters face each other and swing within a breath of each other, the blades meet instead of both landing: neither blow
 * hurts, sparks fly with a ring of steel, and both are thrown back a step. "Within a breath": the one struck swung (a
 * full-strength swing, for a player) at most {@link #CLASH_TICKS} before without landing it, or (a mob fighting Better
 * Combat's way, {@link MobCombat}) its own blow was about to land. A swing that landed is spent: hitting straight back is
 * a trade of blows, not a clash. So a player who times a full swing to a foe's blow turns it aside;
 * one who spam-clicks doesn't (a half-charged swing doesn't count), and after a clash neither can clash again for
 * {@link #COOLDOWN_TICKS}.
 * <p>
 * Swings are noted in {@code LivingEntitySwingMixin} (a swing at the air too) and {@code PlayerAttackMixin} (a blow,
 * Better Combat's included).
 */
public final class BladeClash {
	/** Swings this close together (ticks) clash. */
	static final int CLASH_TICKS = 3;
	/** A player's swing counts only charged at least this much (vanilla's attack strength, 0..1). */
	static final float FULL_SWING = 0.9F;
	/** Both must face each other at least this squarely (the dot of facing and the way to the other, -1..1). */
	static final double FACING = 0.25;
	/** After a clash, neither fighter clashes again for this long (ticks). */
	static final int COOLDOWN_TICKS = 20;
	/** How hard each is pushed back (blocks/tick) and up. */
	static final double PUSH = 0.45, PUSH_UP = 0.15;

	/** When each fighter last swung (game time). */
	private static final Map<LivingEntity, Long> SWUNG = new WeakHashMap<>();
	/** When each fighter last clashed (game time). */
	private static final Map<LivingEntity, Long> CLASHED = new WeakHashMap<>();

	private BladeClash() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BladeClash::allowDamage);
	}

	/** A swing (or a blow) begun: kept if it's armed and, for a player, fully charged. */
	public static void swung(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !armed(entity)) {
			return;
		}
		if (entity instanceof Player player && player.getAttackStrengthScale(0.5F) < FULL_SWING) {
			return;
		}
		SWUNG.put(entity, level.getGameTime());
	}

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
			|| source.getDirectEntity() != attacker || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))
			|| CombatArts.striking() || source.isCreativePlayer()) {
			return true;
		}
		long now = level.getGameTime();
		if (armed(attacker) && armed(victim) && !recently(CLASHED, victim, now, COOLDOWN_TICKS) && !recently(CLASHED, attacker, now, COOLDOWN_TICKS)
			&& facing(victim, attacker) && facing(attacker, victim)
			&& (recently(SWUNG, victim, now, CLASH_TICKS) || victim instanceof Mob mob && MobCombat.blowWithin(mob, CLASH_TICKS))) {
			clash(level, attacker, victim, now);
			return false;
		}
		// this blow lands, so the attacker's swing was spent on it: a blow straight back is a trade of blows, not a clash
		SWUNG.remove(attacker);
		return true;
	}

	/** The blades meet: sparks, the ring of steel, both thrown back; a mob's wound-up blow is spent. */
	private static void clash(ServerLevel level, LivingEntity a, LivingEntity b, long now) {
		CLASHED.put(a, now);
		CLASHED.put(b, now);
		SWUNG.remove(a);
		SWUNG.remove(b);
		for (LivingEntity fighter : new LivingEntity[] {a, b}) {
			if (fighter instanceof Mob mob) {
				MobCombat.checkBlow(mob);
			}
		}
		Vec3 mid = a.getEyePosition().add(b.getEyePosition()).scale(0.5).add(0.0, -0.3, 0.0);
		level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 14, 0.15, 0.15, 0.15, 0.4);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 8, 0.1, 0.1, 0.1, 0.3);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45F, 1.8F + level.getRandom().nextFloat() * 0.2F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
		push(a, b);
		push(b, a);
	}

	private static void push(LivingEntity who, LivingEntity from) {
		Vec3 away = new Vec3(who.getX() - from.getX(), 0.0, who.getZ() - from.getZ());
		away = away.lengthSqr() > 1.0E-4 ? away.normalize() : Vec3.directionFromRotation(0.0F, who.getYRot() + 180.0F);
		double resist = 1.0 - who.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		who.push(away.x * PUSH * resist, PUSH_UP * resist, away.z * PUSH * resist);
		who.syncVelocity = true;
	}

	private static boolean recently(Map<LivingEntity, Long> map, LivingEntity entity, long now, int ticks) {
		Long at = map.get(entity);
		return at != null && now - at <= ticks;
	}

	/** A weapon in hand (anything with a weapon's component: swords, axes, maces, spears, our Armory's). */
	static boolean armed(LivingEntity entity) {
		return entity.getMainHandItem().has(DataComponents.WEAPON);
	}

	/** Whether {@code who} faces {@code other}. */
	static boolean facing(LivingEntity who, LivingEntity other) {
		Vec3 to = new Vec3(other.getX() - who.getX(), 0.0, other.getZ() - who.getZ());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		return Vec3.directionFromRotation(0.0F, who.getYHeadRot()).dot(to.normalize()) >= FACING;
	}
}
