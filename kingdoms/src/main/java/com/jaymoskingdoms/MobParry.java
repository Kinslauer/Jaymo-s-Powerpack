package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.267) Fighters parry you back, the other half of Jaymo's Powers' guard (Andy: "Build those ideas too": enemies that
 * guard and parry). A mob fighting Better Combat's way ({@link MobCombat}) with a weapon or a shield:
 * <ul>
 * <li>A player's blow in the first {@link #GUARD_WINDOW} ticks of its raised guard ({@link MobShields}) is always parried,
 * as a player's own early guard is.</li>
 * <li>Caught with its guard down (and not mid-swing), it may still read the blow and snap the parry: {@link #CHANCE},
 * {@link #VETERAN_CHANCE} for {@link WeaponForms#experienced} fighters.</li>
 * <li>A parry: nothing taken, sparks and a ring of steel, your swing knocked out of rhythm (the attack timer starts over)
 * and you slowed {@link #STAGGER_TICKS}, and it answers at once ({@link MobCombat#hasten}). Then it can't parry again for
 * {@link #COOLDOWN_TICKS}.</li>
 * <li>No parry from behind, nor against an axe (it knocks the guard aside), a two-handed weapon's blow, or the Combat
 * Arts' area blows.</li>
 * </ul>
 */
public final class MobParry {
	/** A blow this soon into a raised guard (ticks) is parried. */
	static final int GUARD_WINDOW = 8;
	/** With the guard down: the chance it snaps a parry anyway, plain and veteran. */
	static final float CHANCE = 0.12F, VETERAN_CHANCE = 0.3F;
	/** After a parry, none again for this long (ticks). */
	static final int COOLDOWN_TICKS = 60;
	/** The player parried: slowed this long (ticks), and pushed back this hard. */
	static final int STAGGER_TICKS = 14;
	static final double PUSH = 0.35;
	/** Only blows from this far round its front (degrees each side). */
	static final double FRONT_DEGREES = 70.0;

	private static final Map<Mob, Long> LAST = new WeakHashMap<>();

	private MobParry() {
	}

	public static void register() {
		if (MobCombat.ENABLED) {
			ServerLivingEntityEvents.ALLOW_DAMAGE.register(MobParry::allowDamage);
		}
	}

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof Mob mob) || !(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player player)
			|| source.getDirectEntity() != player || source.isCreativePlayer() || amount <= 0.0F || CombatArts.striking()) {
			return true;
		}
		if (!mob.isAlive() || mob.isPassenger() || Downed.isDowned(mob) || MobCombat.swinging(mob) || MobShields.guardHand(mob) == null
			|| !MobCombat.handles(mob)) {
			return true;
		}
		long now = level.getGameTime();
		Long last = LAST.get(mob);
		if (last != null && now - last < COOLDOWN_TICKS) {
			return true;
		}
		if (player.getSecondsToDisableBlocking() > 0.0F || heavy(player) || !inFront(mob, player)) {
			return true;
		}
		long raised = MobShields.raisedFor(mob);
		boolean parry = raised >= 0 && raised <= GUARD_WINDOW
			|| raised < 0 && mob.getRandom().nextFloat() < (WeaponForms.experienced(mob) ? VETERAN_CHANCE : CHANCE);
		if (!parry) {
			return true;
		}
		LAST.put(mob, now);
		parried(level, mob, player);
		return false;
	}

	/** A two-handed weapon's blow is too heavy to turn aside with a blade or shield. */
	private static boolean heavy(Player player) {
		MobCombat.Moves moves = BetterCombatBridge.moves(player.getMainHandItem());
		return moves != null && moves.twoHanded();
	}

	private static boolean inFront(Mob mob, Player player) {
		Vec3 to = new Vec3(player.getX() - mob.getX(), 0.0, player.getZ() - mob.getZ());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		return to.normalize().dot(facing) >= Math.cos(Math.toRadians(FRONT_DEGREES));
	}

	private static void parried(ServerLevel level, Mob mob, Player player) {
		Vec3 at = mob.getEyePosition().add(player.getEyePosition().subtract(mob.getEyePosition()).normalize().scale(0.6)).add(0.0, -0.3, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.15, 0.15, 0.15, 0.45);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.25);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.3F, 1.8F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.3F);
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		player.resetAttackStrengthTicker();
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 1, false, false));
		Vec3 back = new Vec3(player.getX() - mob.getX(), 0.0, player.getZ() - mob.getZ());
		if (back.lengthSqr() > 1.0E-4) {
			back = back.normalize().scale(PUSH);
			player.push(back.x, 0.1, back.z);
			player.needsSync = true;
		}
		MobShields.drop(mob);
		MobCombat.hasten(mob);
	}
}
