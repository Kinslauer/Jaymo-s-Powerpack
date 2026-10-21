package com.jaymoskingdoms.nightfall;

import com.jaymoskingdoms.Vampires;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The plague's things ({@link Nightfall}): the Cursed Tusk, and the four the vampires' callings trade
 * ({@link Callings}): the Cursed Card, the Lute, Dried Meat (and the Immunity potion, which is a potion).
 */
final class Relics {
	private Relics() {
	}

	/** A Night Bleeder's tusk: struck with it, a creature catches the plague. Brewed into the Immunity potion. */
	static final class CursedTusk extends Item {
		/** The plague a blow gives (ticks). */
		static final int ATROPHY_TICKS = 20 * 15;

		CursedTusk(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
			super.hurtEnemy(stack, mob, attacker);
			Nightfall.infect(mob, ATROPHY_TICKS, 0);
		}
	}

	/** The Chronicler's card: use it to dash like a vampire; held, now and then it quickens you. */
	static final class CursedCard extends Item {
		/** The dash: speed (blocks a tick), lift, and the wait after (ticks). */
		static final double DASH = 1.5, LIFT = 0.25;
		static final int COOLDOWN = 50;
		/** Held: every this many ticks, this chance of Speed for this long (ticks). */
		static final int QUICKEN_EVERY = 100, QUICKEN_TICKS = 120;
		static final float QUICKEN_CHANCE = 0.25F;

		CursedCard(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (look.lengthSqr() < 1.0E-4) {
				return InteractionResult.PASS;
			}
			look = look.normalize();
			player.setDeltaMovement(look.x * DASH, Math.max(player.getDeltaMovement().y, 0.0) + LIFT, look.z * DASH);
			player.syncVelocity = true;
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			if (level instanceof ServerLevel server) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.5), player.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The Bard's lute: calms the monsters round you a few seconds, and you see in the dark a while after. */
	static final class Lute extends Item {
		/** How far it carries (blocks), how long the calm lasts (ticks), the wait after (ticks). */
		static final double REACH = 15.0;
		static final int CALM_TICKS = 80, COOLDOWN = 100;
		/** Night Vision: a base, more for each calmed, at most (ticks). */
		static final int SIGHT_TICKS = 20 * 20, SIGHT_PER_MOB = 40, SIGHT_MAX = 20 * 60;
		/** The calmed, by UUID: the game time their calm ends. */
		static final Map<UUID, Long> CALMED = new HashMap<>();

		Lute(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			long until = server.getGameTime() + CALM_TICKS;
			int calmed = 0;
			for (Mob mob : server.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(REACH), m -> m instanceof Enemy && m.isAlive())) {
				if (mob.getTarget() == player) {
					mob.setTarget(null);
				}
				CALMED.put(mob.getUUID(), until);
				server.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 1, 0.0, 0.0, 0.0, server.getRandom().nextDouble());
				calmed++;
			}
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Math.min(SIGHT_MAX, SIGHT_TICKS + calmed * SIGHT_PER_MOB), 0, false, false, true));
			for (int i = 0; i < 6; i++) {
				server.sendParticles(ParticleTypes.NOTE, player.getX() + (server.getRandom().nextDouble() - 0.5) * 1.6, player.getEyeY() + 0.4,
					player.getZ() + (server.getRandom().nextDouble() - 0.5) * 1.6, 0, server.getRandom().nextDouble(), 0.0, 0.0, 1.0);
			}
			float[] chord = {0.5F, 0.63F, 0.75F, 1.0F};
			for (float pitch : chord) {
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.PLAYERS, 0.7F, pitch);
			}
			return InteractionResult.SUCCESS;
		}

		/** Whether {@code mob} is still calmed by a lute and so can't hurt anyone with a blow. */
		static boolean calmed(LivingEntity mob, long now) {
			Long until = CALMED.get(mob.getUUID());
			if (until == null) {
				return false;
			}
			if (until < now) {
				CALMED.remove(mob.getUUID());
				return false;
			}
			return true;
		}
	}

	/** The Slaughter's dried meat: mends one to three hearts, more by night, most under a full moon. */
	static final class DriedMeat extends Item {
		/** Health it mends (at least, at most), and how much more by night and under a full moon (times). */
		static final float HEAL_MIN = 2.0F, HEAL_MAX = 6.0F, NIGHT_FACTOR = 1.5F, FULL_MOON_FACTOR = 2.0F;

		DriedMeat(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (level instanceof ServerLevel server) {
				float heal = HEAL_MIN + server.getRandom().nextFloat() * (HEAL_MAX - HEAL_MIN);
				if (Vampires.night(server.getOverworldClockTime())) {
					boolean full = server.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON;
					heal *= full ? FULL_MOON_FACTOR : NIGHT_FACTOR;
				}
				entity.heal(heal);
			}
			return super.finishUsingItem(stack, level, entity);
		}
	}
}
