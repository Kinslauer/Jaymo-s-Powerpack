package com.jaymospowers.alchemy.gear;

import com.jaymospowers.alchemy.Corruption;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An ichorium sword, pickaxe or axe. Better than netherite, but it feeds on its wielder: every block broken and every blow that
 * lands adds {@link #HUNGER} fleeting corruption. The tooltip says only "It hungers."
 */
public class IchoriumToolItem extends Item {
	/** Fleeting corruption per block broken or blow landed. */
	public static final int HUNGER = 1;

	public IchoriumToolItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		feed(miner);
		return super.mineBlock(stack, level, state, pos, miner);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		feed(attacker);
		super.hurtEnemy(stack, target, attacker);
	}

	private static void feed(LivingEntity wielder) {
		if (wielder instanceof ServerPlayer player) {
			Corruption.add(player, Corruption.Kind.FLEETING, HUNGER);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.ichorium").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}
}
