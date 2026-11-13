package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Consumer;

/**
 * The immovable rod: a short iron bar that, set down, locks where it is, even in the open air, and stays: no piston moves it,
 * no blast breaks it, nothing carries it off, and it will bear any weight laid on it. Point it at a spot (or a face) and use it
 * to set it, level or upright after the way you look; sneak and use the rod itself, bare-handed, to take it up again.
 */
public final class ImmovableRod {
	/** How far a rod is set from you in open air (blocks) and how far you can aim it at a face. */
	static final double AIR = 3.0, REACH = 4.5;
	public static Item ITEM;
	public static Block BLOCK;
	private static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
	private static final VoxelShape X = Block.box(0, 6, 6, 16, 10, 10), Y = Block.box(6, 0, 6, 10, 16, 10), Z = Block.box(6, 6, 0, 10, 10, 16);

	private ImmovableRod() {
	}

	static void register() {
		BLOCK = Gadgets.block("immovable_rod", RodBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.CHAIN)
			.strength(-1.0F, 3600000.0F).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
		ITEM = Gadgets.item("immovable_rod", Rarity.RARE, RodItem::new);
	}

	/** The rod in a hand. */
	static final class RodItem extends Item {
		RodItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			HitResult hit = player.pick(REACH, 0.0F, false);
			BlockPos at = hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b.getBlockPos().relative(b.getDirection())
				: BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(AIR)));
			if (!server.getBlockState(at).canBeReplaced() || !server.isInWorldBounds(at)) {
				return InteractionResult.FAIL;
			}
			Vec3 look = player.getLookAngle();
			Direction.Axis axis = Direction.getApproximateNearest(look.x, look.y, look.z).getAxis();
			server.setBlock(at, BLOCK.defaultBlockState().setValue(AXIS, axis), 3);
			server.playSound(null, at, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.6F);
			Fx.ring(server, Vec3.atCenterOf(at), 1.2F, 0xB0B8C8);
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.immovable_rod").withStyle(net.minecraft.ChatFormatting.GRAY));
		}
	}

	/** The rod, set. */
	static final class RodBlock extends Block {
		RodBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(AXIS)) {
				case X -> X;
				case Z -> Z;
				default -> Y;
			};
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				server.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				if (!player.getInventory().add(new ItemStack(ITEM))) {
					Block.popResource(server, pos, new ItemStack(ITEM));
				}
				server.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1.9F);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
