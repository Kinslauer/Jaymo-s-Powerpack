package com.jaymospowers.alchemy.gear;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Phials;
import com.jaymospowers.alchemy.crucible.Crucibles;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The Lantern of Everflame. Used on the face of a block it sets an Everflame ({@link Crucibles#EVERFLAME}) in the space beside
 * that face, drawing {@link #COST} of Light from the phials you carry; it needs {@link #COOLDOWN} ticks between flames.
 * Needs the {@code everflame_lantern} research.
 */
public class LanternItem extends Item {
	/** What one flame draws from carried phials. */
	public static final EssenceList COST = EssenceList.of(Essence.LIGHT, 1);
	/** Ticks before the next flame can be set (1 s). */
	public static final int COOLDOWN = 20;

	public LanternItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		var level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.getBlockState(pos).canBeReplaced()) {
			pos = pos.relative(context.getClickedFace());
		}
		BlockState flame = Crucibles.EVERFLAME.defaultBlockState();
		if (!level.getBlockState(pos).canBeReplaced() || !flame.canSurvive(level, pos) || !level.isUnobstructed(flame, pos, CollisionContext.empty())) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!Knowledge.knows(sp, AlchemyGear.EVERFLAME_LANTERN)) {
			sp.sendOverlayMessage(RodItem.text("not_understood"));
			return InteractionResult.FAIL;
		}
		if (!Phials.pay(sp, COST)) {
			sp.sendOverlayMessage(RodItem.text("no_essence"));
			return InteractionResult.FAIL;
		}
		server.setBlockAndUpdate(pos, flame);
		server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.6F);
		sp.getCooldowns().addCooldown(context.getItemInHand(), COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.everflame_lantern").withStyle(ChatFormatting.GRAY));
	}
}
