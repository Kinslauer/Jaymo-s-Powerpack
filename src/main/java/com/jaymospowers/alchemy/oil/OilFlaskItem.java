package com.jaymospowers.alchemy.oil;

import com.jaymospowers.alchemy.bomb.Bombs;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.alchemy.hunter.Refills;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A flask of oil ({@link OilKind}). Held in one hand with a blade (a mending oil: a tool) in the other, use it and the oil goes
 * on the blade ({@link Oils#coat}); the flask is spent and leaves a spent phial that strong spirit refills ({@link Refills}).
 * Sneak and use it on the ground and it is poured out as a slick ({@link OilSlickBlock}). The two splashes are thrown instead
 * ({@link Bombs}).
 */
public final class OilFlaskItem extends Item {
	public final OilKind kind;

	public OilFlaskItem(Item.Properties properties, OilKind kind) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (this.kind.group == OilKind.Group.SPLASH) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
			if (level instanceof ServerLevel server) {
				Bombs.throwFrom(server, player, held.copyWithCount(1));
				spend(player, held, false);
			}
			return InteractionResult.SUCCESS;
		}
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack target = player.getItemInHand(otherHand);
		if (!Oils.fits(this.kind, target)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel && player instanceof ServerPlayer sp && Oils.coat(sp, target, this.kind, Oils.tier(held))) {
			spend(player, held, true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isShiftKeyDown() || this.kind.group == OilKind.Group.SPLASH) {
			return InteractionResult.PASS;
		}
		if (context.getLevel() instanceof ServerLevel level) {
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			if (OilSlickBlock.spill(level, at, 1)) {
				spend(player, context.getItemInHand(), true);
				level.playSound(null, at, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** One flask used up; its spent phial comes back to the pack (a thrown flask leaves none). */
	private static void spend(Player player, ItemStack held, boolean phial) {
		ItemStack empty = phial ? Refills.spentPhial(held) : ItemStack.EMPTY;
		held.consume(1, player);
		if (!empty.isEmpty() && !player.hasInfiniteMaterials()) {
			player.getInventory().placeItemBackInInventory(empty, Prediction.SERVER_ONLY);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(BrewGrades.orPlain(stack).displayName());
		builder.accept(Component.translatable("tooltip.jaymospowers.oil." + this.kind.id).withStyle(ChatFormatting.GRAY));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
