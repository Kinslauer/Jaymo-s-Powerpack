package com.jaymospowers.alchemy.gear;

import com.jaymospowers.core.CarryBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * A bound block: what the Lift focus turns a block into. The block and everything inside it (a chest's items, a furnace's
 * fire) are bound into this one item, kept in the {@link AlchemyGear#BOUND} component; using the item on a face sets the block back down
 * where it fits, exactly as it was. Unlike {@link CarryBlock}, which only lifts a few kinds of block and carries them in the
 * arms, this takes any block with a body, and the binding is an item that can sit in a pocket or a chest.
 * If the item is lost the block is lost with it, so it is never destroyed by anything but the usual ways items go.
 */
public class BoundBlockItem extends Item {
	/** The bound block, and its block entity's saved contents. */
	public record Bound(BlockState state, Optional<CompoundTag> contents) {
		public static final Codec<Bound> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockState.CODEC.fieldOf("state").forGetter(Bound::state),
			CompoundTag.CODEC.optionalFieldOf("contents").forGetter(Bound::contents)).apply(i, Bound::new));
	}

	public BoundBlockItem(Item.Properties properties) {
		super(properties);
	}

	/** Whether the block at {@code pos} can be bound (any solid block with a body that isn't a half of a larger whole or unbreakable). */
	public static boolean bindable(Level level, BlockPos pos, BlockState state) {
		return CarryBlock.liftableAny(level, pos, state);
	}

	/** Binds the block at {@code pos} into an item and gives it to {@code player}. The caller has checked {@link #bindable}. */
	public static void bind(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		BlockState held = state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
		CompoundTag contents = null;
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity != null) {
			contents = entity.saveWithFullMetadata(level.registryAccess());
			// emptied first so nothing spills as the block goes
			if (entity instanceof Clearable clearable) {
				clearable.clearContent();
			}
		}
		level.setBlock(pos, state.getFluidState().createLegacyBlock(),
			entity == null ? Block.UPDATE_ALL : Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
		ItemStack item = new ItemStack(AlchemyGear.BOUND_BLOCK);
		item.set(AlchemyGear.BOUND, new Bound(held, Optional.ofNullable(contents)));
		player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
		level.playSound(null, pos, held.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 0.9F);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		Bound bound = stack.get(AlchemyGear.BOUND);
		Player player = context.getPlayer();
		if (bound == null || player == null) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.getBlockState(pos).canBeReplaced()) {
			pos = pos.relative(context.getClickedFace());
		}
		BlockState state = bound.state();
		if (!level.getBlockState(pos).canBeReplaced() || !level.isUnobstructed(state, pos, CollisionContext.placementContext(player))
			|| !state.canSurvive(level, pos)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER));
			}
			server.setBlock(pos, state, Block.UPDATE_ALL);
			if (bound.contents().isPresent()) {
				BlockEntity loaded = BlockEntity.loadStatic(pos, state, bound.contents().get(), server.registryAccess());
				if (loaded != null) {
					server.setBlockEntity(loaded);
				}
			}
			CarryBlock.markPlaced(server, pos);
			server.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
			if (!player.isCreative()) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getName(ItemStack stack) {
		Bound bound = stack.get(AlchemyGear.BOUND);
		return bound == null ? super.getName(stack) : Component.translatable("item.jaymospowers.bound_block.of", bound.state().getBlock().getName());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return stack.has(AlchemyGear.BOUND);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.bound_block").withStyle(ChatFormatting.GRAY));
	}
}
