package com.jaymospowers.alchemy.hunter;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The testing needle (Hunter's Road, patch 6, "T-A3"): held in one hand with a reagent (a poison ingredient, a paste, a
 * reagent from the crucible or the alembic) in the other, used on a strong creature brought near death, it draws the reagent
 * into the creature and shows how it leans ({@link Leans}): the lean is learned, and the Scrying Lens shows it from then on.
 * The reagent is used up; the creature takes a prick. It must be a creature of at least {@link Leans#STRONG_HEALTH} health
 * below {@link Leans#NEAR_DEATH} of it: a weak one or a well one tells nothing.
 */
public class NeedleItem extends Item {
	public NeedleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(player instanceof ServerPlayer server) || !(player.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack reagent = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		Leans.Lean lean = reagent.isEmpty() ? null : Leans.of(reagent);
		if (lean == null) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.needle.no_reagent"));
			return InteractionResult.FAIL;
		}
		if (target.getMaxHealth() < Leans.STRONG_HEALTH || target.getHealth() > target.getMaxHealth() * Leans.NEAR_DEATH) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.needle.not_ready"));
			return InteractionResult.FAIL;
		}
		reagent.consume(1, player);
		target.hurtServer(level, level.damageSources().playerAttack(player), 0.5F);
		if (Leans.learn(server, lean)) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
		} else {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
		level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.testing_needle").withStyle(ChatFormatting.GRAY));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
