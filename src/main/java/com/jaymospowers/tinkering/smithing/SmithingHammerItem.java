package com.jaymospowers.tinkering.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) The smithing hammer (copper and steel, after Overgeared's). Right-click a Smithing Anvil holding hot
 * ingots to choose what to forge, then again in time with the bar for each blow. Sneak-use it on a vanilla anvil to
 * turn it into a Smithing Anvil.
 */
public class SmithingHammerItem extends Item {
	/** Blocks a sneaking hammer turns into anvils: the first non-null answer wins. */
	public static final List<Function<BlockState, @Nullable BlockState>> CONVERSIONS = new ArrayList<>();

	static {
		CONVERSIONS.add(state -> state.is(Blocks.ANVIL)
			? Smithing.SMITHING_ANVIL.defaultBlockState().setValue(SmithingAnvilBlock.FACING, state.getValue(AnvilBlock.FACING)) : null);
	}

	public SmithingHammerItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		for (Function<BlockState, @Nullable BlockState> conversion : CONVERSIONS) {
			BlockState anvil = conversion.apply(state);
			if (anvil != null) {
				if (level instanceof ServerLevel server) {
					server.setBlock(pos, anvil, Block.UPDATE_ALL);
					server.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
					server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
						12, 0.3, 0.1, 0.3, 0.1);
					context.getItemInHand().hurtAndBreak(1, player, context.getHand());
				}
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.smithing_hammer").withStyle(ChatFormatting.GRAY));
	}
}
