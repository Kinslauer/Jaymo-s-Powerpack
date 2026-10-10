package com.jaymoskitchen.herb;

import com.jaymoskitchen.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A rolled Pmeh joint. Hold use to take a puff (held to the mouth, a wisp of smoke); it lasts
 * {@link #PUFFS} puffs. Every puff makes you hungry and gives one random effect, good or bad depending on how
 * well it was rolled ({@link JointQuality}).
 */
public class JointItem extends Item {
	public static final int PUFFS = 4;
	private static final int PUFF_TICKS = 30;
	/** Hunger from each puff, and how long a puff's random effect lasts (random, in this range). */
	private static final int HUNGER_TICKS = 15 * 20;
	private static final int EFFECT_TICKS_MIN = 20 * 20, EFFECT_TICKS_MAX = 45 * 20;

	public static JointQuality quality(ItemStack stack) {
		JointQuality quality = stack.get(ModItems.JOINT_QUALITY);
		return quality == null ? JointQuality.CLEAN : quality;
	}

	/** Stamps how it was rolled onto a freshly rolled joint (sets its effects, name and colour). */
	public static void stamp(ItemStack stack, JointQuality quality) {
		stack.set(ModItems.JOINT_QUALITY, quality);
		stack.set(DataComponents.ITEM_NAME, Component.translatable(quality.translationKey()).withStyle(quality.color()));
	}

	public JointItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return PUFF_TICKS;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		// the tip glows while drawing on it
		if (level instanceof ServerLevel server && ticksRemaining % 6 == 0) {
			Vec3 tip = tip(user);
			server.sendParticles(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (level instanceof ServerLevel server) {
			// breathe out: a puff of smoke drifting forward from the mouth
			Vec3 look = user.getLookAngle();
			Vec3 mouth = user.getEyePosition().add(look.scale(0.4)).add(0.0, -0.15, 0.0);
			for (int i = 0; i < 6; i++) {
				server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, mouth.x, mouth.y, mouth.z, 0, look.x * 0.04, 0.03,
					look.z * 0.04, 1.0);
			}
			server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
				0.15F, 1.6F + server.getRandom().nextFloat() * 0.2F);
			user.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
			int duration = EFFECT_TICKS_MIN + server.getRandom().nextInt(EFFECT_TICKS_MAX - EFFECT_TICKS_MIN + 1);
			user.addEffect(new MobEffectInstance(quality(stack).rollEffect(server.getRandom()), duration, 0));
			EquipmentSlot slot = user.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
			stack.hurtAndBreak(1, user, slot);
		}
		return stack;
	}

	private static Vec3 tip(LivingEntity user) {
		Vec3 look = user.getLookAngle();
		return user.getEyePosition().add(look.scale(0.55)).add(0.0, -0.2, 0.0);
	}
}
