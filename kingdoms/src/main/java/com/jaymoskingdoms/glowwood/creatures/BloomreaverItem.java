package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * The Myceliarch's blade. Hits infect (Mycosis II, 6 s). Use: Spore Burst, a ring of spores that infects, slows and
 * shoves back everything around you (10 s cooldown, 3 durability).
 */
public class BloomreaverItem extends Item {
	/** 1800 uses, mining speed 8, +3 damage, enchantability 15, repaired with Lumen Spores. */
	public static final ToolMaterial TIER = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.0F, 3.0F, 15,
			GlowCreatures.BLOOM_REPAIRS);
	public static final int COOLDOWN = 200;
	public static final double RADIUS = 4.0;

	public BloomreaverItem(Properties properties) {
		// Built with Properties#sword(TIER, 4, -2.4): 1 base + 3 tier bonus + 4 = 8 attack damage.
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		Mycosis.infect(target, 120, 1, attacker);
		super.hurtEnemy(stack, target, attacker);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			sporeBurst(server, player);
			stack.hurtAndBreak(3, player, hand);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	public static void sporeBurst(ServerLevel level, LivingEntity source) {
		level.playSound(null, source.getX(), source.getY(), source.getZ(), GlowSounds.BLOOMREAVER_BURST,
				SoundSource.PLAYERS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		for (int i = 0; i < 48; i++) {
			double angle = i * Math.PI * 2 / 48;
			double x = source.getX() + Math.cos(angle) * 1.2;
			double z = source.getZ() + Math.sin(angle) * 1.2;
			level.sendParticles(GlowFx.spore(), x, source.getY() + 0.6, z, 0, Math.cos(angle), 0.05,
					Math.sin(angle), 0.35);
		}
		level.sendParticles(GlowFx.glowSpore(), source.getX(), source.getY() + 1.0, source.getZ(), 30, 1.5, 0.6, 1.5, 0.02);
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(RADIUS),
				e -> e != source && e.isAlive() && e.distanceToSqr(source) <= RADIUS * RADIUS && !source.isAlliedTo(e))) {
			Mycosis.infect(other, 160, 1, source);
			other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1), source);
			Vec3 push = other.position().subtract(source.position()).multiply(1, 0, 1).normalize().scale(0.9);
			other.push(push.x, 0.35, push.z);
			other.syncVelocity = true;
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
								TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.jaymoskingdoms.bloomreaver.desc").withStyle(ChatFormatting.GRAY));
	}
}
