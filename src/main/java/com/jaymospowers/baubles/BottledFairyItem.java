package com.jaymospowers.baubles;

import com.jaymospowers.core.fx.MagicFx;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.12) A pink fairy in a corked bottle: worn as an amulet or a charm, she brings luck and, once, turns death
 * aside ({@link Fairies#saveFromDeath}). Right-click puts her on; sneak and right-click lets her go (for good). Worn,
 * she glows faintly about you, for all to see.
 */
public class BottledFairyItem extends BaubleItem {
	/** How often (ticks) a worn fairy's glow shows about the wearer. */
	static final int GLOW_EVERY = 15;

	public BottledFairyItem(Properties properties, List<Bonus> bonuses) {
		super(properties, Baubles.Kind.CHARM, bonuses);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return super.use(level, player, hand);
		}
		if (player instanceof ServerPlayer server && server.level() instanceof ServerLevel serverLevel) {
			player.getItemInHand(hand).shrink(1);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			Fairies.loose(serverLevel, player.getX(), player.getEyeY(), player.getZ());
		}
		return InteractionResult.SUCCESS;
	}

	/** Worn (or carried), a faint rose glow drifts about the wearer now and then. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner.tickCount % GLOW_EVERY == 0 && !owner.isInvisible()) {
			level.sendParticles(MagicFx.wisp(PinkFairy.GLOW, 0.5F), owner.getX(), owner.getY() + owner.getBbHeight() * 0.55, owner.getZ(),
				1, 0.35, 0.35, 0.35, 0.0);
		}
	}

	/** She fits the amulet slot (on a cord), the charm slot, or (0.20.12) the belt slot, hung at the hip. */
	@Override
	protected Component slotName() {
		return Component.translatable("item.jaymospowers.bottled_fairy.slots");
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		builder.accept(Component.translatable("tooltip.jaymospowers.bottled_fairy.save").withStyle(ChatFormatting.LIGHT_PURPLE));
		builder.accept(Component.translatable("tooltip.jaymospowers.bottled_fairy.free").withStyle(ChatFormatting.DARK_GRAY));
	}
}
