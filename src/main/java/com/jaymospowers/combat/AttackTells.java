package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.67) Attack tells for every mob. Andy: "Can you make every mob use this glint mechanic?" Kingdoms' fighters already
 * wind up their Better Combat swings with a glint; every other melee mob (vanilla's goal-driven ones, the brain-driven
 * piglins and hoglins, and every modded mob that attacks through either) now does too. Its blow no longer lands the
 * instant it's thrown: a glint shows at its weapon or head, and the blow lands {@link #WINDUP} ticks later if its target
 * is still in reach and in sight (step back or dodge and it whiffs, raise a guard and you can parry it). Its attack
 * cooldown runs as before. White: parryable. Red: heavy ({@link #HEAVY_DAMAGE} attack damage or more, or a body
 * {@link #HEAVY_WIDTH} wide), which carries {@link Guard#UNPARRYABLE_TAG} as it lands, so a guard only blocks it.
 * <p>
 * The blows are taken in {@code mixin/AttackTellGoalMixin} (MeleeAttackGoal) and {@code mixin/AttackTellBrainMixin}
 * (the MeleeAttack behaviour).
 */
public final class AttackTells {
	/** Ticks from the glint to the blow. */
	public static final int WINDUP = 6;
	/** A blow this heavy (attack damage), or from a body this wide (blocks), can't be parried. */
	public static final double HEAVY_DAMAGE = 10.0, HEAVY_WIDTH = 1.35;
	public static final int TELL_COLOR = 0xFFFFFF, HEAVY_TELL_COLOR = 0xE02020;
	public static final float TELL_SIZE = 1.2F;
	/** Reach slack (blocks) for a target that stepped a little since the wind-up began. */
	static final double REACH_SLACK = 0.25;

	private record Pending(LivingEntity target, long hitAt) {
	}

	private static final Map<Mob, Pending> PENDING = new IdentityHashMap<>();

	private AttackTells() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AttackTells::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	/**
	 * A melee blow thrown by {@code mob} at {@code target}: the glint now and the blow after the wind-up. Always false
	 * (nothing landed yet); the callers drop the result, as vanilla does.
	 */
	public static boolean windUp(Mob mob, ServerLevel level, Entity target) {
		if (!(target instanceof LivingEntity living)) {
			return mob.doHurtTarget(level, target);
		}
		if (PENDING.containsKey(mob)) {
			return false;
		}
		tell(level, mob, heavy(mob));
		PENDING.put(mob, new Pending(living, level.getGameTime() + WINDUP));
		return false;
	}

	/** Whether {@code mob}'s blows are too heavy to parry. */
	public static boolean heavy(Mob mob) {
		return mob.getBbWidth() >= HEAVY_WIDTH || mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
			&& mob.getAttributeValue(Attributes.ATTACK_DAMAGE) >= HEAVY_DAMAGE;
	}

	/** The glint: at the weapon hand for something with arms, at the head for anything else. */
	public static void tell(ServerLevel level, LivingEntity mob, boolean heavy) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		Vec3 at;
		if (!mob.getMainHandItem().isEmpty()) {
			Vec3 side = new Vec3(-facing.z, 0.0, facing.x).scale(mob.getMainArm() == HumanoidArm.RIGHT ? -0.35 : 0.35);
			at = mob.getEyePosition().add(facing.scale(0.4)).add(side).add(0.0, -0.2, 0.0);
		} else {
			at = mob.getEyePosition().add(facing.scale(mob.getBbWidth() * 0.5 + 0.15));
		}
		level.sendParticles(new DustParticleOptions(heavy ? HEAVY_TELL_COLOR : TELL_COLOR, TELL_SIZE), at.x, at.y, at.z, heavy ? 8 : 5,
			0.08, 0.08, 0.08, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.05);
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Mob, Pending>> it = new ArrayList<>(PENDING.entrySet()).iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Pending> entry = it.next();
			Mob mob = entry.getKey();
			Pending pending = entry.getValue();
			if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved() || !mob.isAlive()) {
				PENDING.remove(mob);
				continue;
			}
			if (level.getGameTime() < pending.hitAt()) {
				continue;
			}
			PENDING.remove(mob);
			LivingEntity target = pending.target();
			if (!target.isAlive() || target.level() != level || !inReach(mob, target) || !mob.getSensing().hasLineOfSight(target)) {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, mob.getSoundSource(), 0.6F, 0.8F);
				continue;
			}
			boolean heavy = heavy(mob);
			if (heavy) {
				mob.addTag(Guard.UNPARRYABLE_TAG);
			}
			try {
				mob.doHurtTarget(level, target);
			} finally {
				if (heavy) {
					mob.removeTag(Guard.UNPARRYABLE_TAG);
				}
			}
		}
	}

	private static boolean inReach(Mob mob, LivingEntity target) {
		return mob.isWithinMeleeAttackRange(target)
			|| mob.getBoundingBox().inflate(REACH_SLACK).intersects(target.getBoundingBox());
	}
}
