package com.jaymospowers.tinkering;

import com.jaymospowers.tinkering.ranged.Ranged;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A tool put together at the Tool Station. Its stats all come from its parts' materials ({@link ToolStats}). */
public class TinkerToolItem extends Item implements TinkerTool {
	public final ToolType type;

	public TinkerToolItem(Item.Properties properties, ToolType type) {
		super(properties);
		this.type = type;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		TinkerTool.tick(stack, level);
	}

	@Override
	public ToolType type() {
		return type;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		TraitEffects.afterHit(stack, mob, attacker);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		boolean mined = super.mineBlock(stack, level, state, pos, owner);
		TraitEffects.afterMine(stack, level, owner);
		return mined;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (ToolStats.isBroken(context.getItemInHand())) {
			return InteractionResult.PASS;
		}
		if (type.traits.containsKey(Trait.HARVEST)) {
			InteractionResult harvested = ToolActions.useOn(context, type);
			if (harvested != InteractionResult.PASS) {
				return harvested;
			}
		}
		return super.useOn(context);
	}

	/** (0.15.x) A dagger in the off hand swings from right mouse (Dual Wield); a battlesign is held up to block (Blocking). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand == InteractionHand.OFF_HAND && type.traits.containsKey(Trait.OFFHAND)) {
			InteractionResult swung = ToolActions.offhandSwing(level, player, player.getItemInHand(hand));
			if (swung != InteractionResult.PASS) {
				return swung;
			}
		}
		if (type.style.thrown() && !ToolStats.isBroken(player.getItemInHand(hand))) {
			InteractionResult thrown = Ranged.use(level, player, hand, type);
			if (thrown != InteractionResult.PASS) {
				return thrown;
			}
		}
		return super.use(level, player, hand);
	}

	/** (0.15.x) The javelin is drawn back like a trident. */
	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return type.style == ToolType.Style.JAVELIN ? ItemUseAnimation.TRIDENT : super.getUseAnimation(stack);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return type.style == ToolType.Style.JAVELIN ? Ranged.HOLD_DURATION : super.getUseDuration(stack, user);
	}

	/** (0.15.x) Shears: right mouse on a sheep that's ready. */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		InteractionResult sheared = type.traits.containsKey(Trait.SHEARS) ? ToolActions.shear(stack, player, target, hand) : InteractionResult.PASS;
		return sheared != InteractionResult.PASS ? sheared : super.interactLivingEntity(stack, player, target, hand);
	}

	/** (0.15.x) Bonking: let go of a held-up battlesign after a moment's charge to bonk what you're looking at. */
	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		if (type.traits.containsKey(Trait.BONKING) && ToolActions.bonk(stack, level, user, getUseDuration(stack, user) - remainingUseTicks)) {
			return true;
		}
		if (type.style == ToolType.Style.JAVELIN && Ranged.releaseJavelin(stack, level, user, getUseDuration(stack, user) - remainingUseTicks)) {
			return true;
		}
		return super.releaseUsing(stack, level, user, remainingUseTicks);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		TinkerTool.tooltip(stack, type, builder);
	}
}
