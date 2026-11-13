package com.jaymospowers.mortal;

import com.jaymospowers.core.FlightRoll;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Tripped;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A falling attack for every kind of weapon. A player's own blow landed while they have fallen {@link #MIN_FALL} blocks
 * or more (not flying, not gliding) hits harder by the fall, {@link #PER_BLOCK} a block up to {@link #MOST}, does its
 * weapon's own thing besides, and breaks the fall. By the weapon's kind ({@link Finishers#kind}):
 * <ul>
 * <li>sword: the plunge, the plain bonus;</li>
 * <li>greatsword: the bonus and a half more, and half of it to everyone within {@link #WIDE} blocks;</li>
 * <li>dagger: the bonus goes through armour;</li>
 * <li>axe: the bonus and a half more, and the struck staggers (slowed);</li>
 * <li>blunt and flail: the bonus, and a shock through the ground that hurts and lifts everyone within {@link #WIDE};</li>
 * <li>spear and polearm: the bonus, and the struck is pinned where it stands for a moment;</li>
 * <li>staff: half the bonus and a long shove;</li>
 * <li>bare hands: half the bonus, and the struck goes down on its back (the sweep).</li>
 * </ul>
 * The vanilla mace keeps its own smash and gets nothing here; bows and guns have no falling attack.
 */
public final class FallingAttacks {
	static final double MIN_FALL = 2.5;
	static final float PER_BLOCK = 1.0F, MOST = 8.0F;
	static final double WIDE = 2.8;
	static final float SHOCK_DAMAGE = 3.0F;
	static final int STAGGER = 40, PIN = 30, FLOOR = 40;

	static boolean landing;

	private FallingAttacks() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!landing && taken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer player
				&& entity != player && entity.level() instanceof ServerLevel level) {
				landing = true;
				try {
					land(level, player, entity, source);
				} finally {
					landing = false;
				}
			}
		});
	}

	private static void land(ServerLevel level, ServerPlayer player, LivingEntity target, DamageSource source) {
		ItemStack held = player.getMainHandItem();
		if (player.fallDistance < MIN_FALL || player.onGround() || player.isFallFlying() || player.isPassenger() || FlightRoll.flying(player)
			|| held.is(Items.MACE)) {
			return;
		}
		Finishers.Kind kind = Finishers.kind(held);
		float bonus = Math.min(MOST, (float) player.fallDistance * PER_BLOCK);
		switch (kind) {
			case SWORD -> extra(level, player, target, bonus, false);
			case GREATSWORD -> {
				extra(level, player, target, bonus * 1.5F, false);
				for (LivingEntity near : around(level, player, target)) {
					near.hurtServer(level, player.damageSources().playerAttack(player), bonus * 0.75F);
				}
			}
			case DAGGER -> extra(level, player, target, bonus, true);
			case AXE -> {
				extra(level, player, target, bonus * 1.5F, false);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER, 2), player);
			}
			case BLUNT, FLAIL -> {
				extra(level, player, target, bonus, false);
				for (LivingEntity near : around(level, player, target)) {
					if (near.hurtServer(level, player.damageSources().playerAttack(player), SHOCK_DAMAGE)) {
						near.setDeltaMovement(near.getDeltaMovement().add(0.0, 0.45, 0.0));
						near.needsSync = true;
					}
				}
				level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.2, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			case SPEAR, POLEARM -> {
				extra(level, player, target, bonus, false);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN, 6), player);
			}
			case STAFF -> {
				extra(level, player, target, bonus * 0.5F, false);
				target.knockback(1.5, player.getX() - target.getX(), player.getZ() - target.getZ(), source, bonus);
			}
			case FIST -> {
				extra(level, player, target, bonus * 0.5F, false);
				if (target.isAlive()) {
					Tripped.sweep(target, FLOOR);
				}
			}
			default -> {
				return;
			}
		}
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 24, 0.4, 0.4, 0.4, 0.4);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.3F, 0.6F);
	}

	private static void extra(ServerLevel level, ServerPlayer player, LivingEntity target, float amount, boolean throughArmour) {
		if (target.isAlive() && amount > 0.0F) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, throughArmour ? level.damageSources().indirectMagic(player, player) : player.damageSources().playerAttack(player), amount);
		}
	}

	private static java.util.List<LivingEntity> around(ServerLevel level, ServerPlayer player, LivingEntity target) {
		return level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(WIDE, 1.0, WIDE),
			e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player)));
	}
}
