package com.jaymospowers.tinkering.ranged;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.ground.ThrownRocks;
import com.jaymospowers.tinkering.TinkerMaterial;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.89) Andy (seven-mods plan, patch 4): "no new arrow items: our arrows already have a choice of heads", so the
 * head of a Tinkers arrow gives the effect (Caverns &amp; Chasms' blunt and large arrows, our own take).
 * <ul>
 * <li><b>Blunt</b>, a wood or rock head: it doesn't wound. It knocks back, dazes (Slowness and Nausea) and deals
 * {@link #BLUNT_SHARE} of its damage, never the last of someone's health; and like a thrown rock it can knock out
 * someone who didn't see it coming ({@link ThrownRocks#unsuspecting}). It bounces off.</li>
 * <li><b>Large</b>, a heavy metal head (pig iron, steel, star metal) on a bone or blaze rod shaft: it hits
 * {@link #LARGE_DAMAGE} times as hard and flies {@link #LARGE_SPEED} as fast, so it drops sooner.</li>
 * </ul>
 */
public final class ArrowHeads {
	public static final String BLUNT = "jaymospowers.arrow_blunt", LARGE = "jaymospowers.arrow_large";
	private static final String SLOWED = "jaymospowers.arrow_slowed";
	static final double LARGE_DAMAGE = 1.4, LARGE_SPEED = 0.75;
	static final float BLUNT_SHARE = 0.5F;
	static final double BLUNT_KNOCKBACK = 0.8;
	static final int DAZE_TICKS = 60;
	static final float HEAD_CHANCE = 0.4F, BODY_CHANCE = 0.12F;
	static final int OUT_MIN = 200, OUT_MAX = 600;
	static final double HEAD_REACH = 0.35;

	static final Set<TinkerMaterial> HEAVY = Set.of(TinkerMaterial.PIG_IRON, TinkerMaterial.STEEL, TinkerMaterial.STAR_METAL);
	static final Set<TinkerMaterial> STOUT_SHAFTS = Set.of(TinkerMaterial.BONE, TinkerMaterial.BLAZE_ROD);

	private static boolean redealing;

	private ArrowHeads() {
	}

	public static boolean blunt(TinkerMaterial head) {
		return head.name().startsWith("WOOD") || head.name().startsWith("ROCK_");
	}

	public static boolean large(TinkerMaterial head, TinkerMaterial shaft) {
		return HEAVY.contains(head) && STOUT_SHAFTS.contains(shaft);
	}

	/** Marks a fresh arrow by its head and shaft (head first, then shaft, as the parts go). */
	static void mark(AbstractArrow arrow, List<TinkerMaterial> materials, double damage) {
		if (materials.isEmpty()) {
			return;
		}
		TinkerMaterial head = materials.get(0);
		if (blunt(head)) {
			arrow.addTag(BLUNT);
		} else if (materials.size() > 1 && large(head, materials.get(1))) {
			arrow.addTag(LARGE);
			arrow.setBaseDamage(damage * LARGE_DAMAGE);
		}
	}

	public static void init() {
		// a large arrow's launch is slowed once, when it first goes into the world (already flying by then)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.entityTags().contains(LARGE) && !arrow.entityTags().contains(SLOWED)) {
				arrow.addTag(SLOWED);
				arrow.setDeltaMovement(arrow.getDeltaMovement().scale(LARGE_SPEED));
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArrowHeads::allowDamage);
	}

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (redealing || !(source.getDirectEntity() instanceof AbstractArrow arrow) || !arrow.entityTags().contains(BLUNT)
			|| !(victim.level() instanceof ServerLevel level)) {
			return true;
		}
		boolean unsuspecting = ThrownRocks.unsuspecting(victim, source.getEntity());
		boolean head = arrow.getY() >= victim.getEyeY() - HEAD_REACH;
		float dealt = Math.min(amount * BLUNT_SHARE, Math.max(0.0F, victim.getHealth() - 1.0F));
		boolean hurt = false;
		if (dealt > 0.0F) {
			redealing = true;
			try {
				hurt = victim.hurtServer(level, level.damageSources().generic(), dealt);
			} finally {
				redealing = false;
			}
		}
		Vec3 push = arrow.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		if (push.lengthSqr() > 1.0E-6) {
			push = push.normalize();
			victim.knockback(BLUNT_KNOCKBACK, -push.x, -push.z, source, amount);
		}
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DAZE_TICKS, 1), arrow.getOwner());
		victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DAZE_TICKS + 40, 0), arrow.getOwner());
		level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.8F, 0.9F);
		if (unsuspecting && victim.isAlive() && !Downed.passedOut(victim) && level.getRandom().nextFloat() < (head ? HEAD_CHANCE : BODY_CHANCE)) {
			int ticks = OUT_MIN + level.getRandom().nextInt(OUT_MAX - OUT_MIN + 1);
			Downed.knockOut(level, victim, ticks, true, "message.jaymospowers.downed.knocked_rock");
		}
		return false;
	}
}
