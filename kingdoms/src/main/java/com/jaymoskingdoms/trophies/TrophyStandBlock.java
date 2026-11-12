package com.jaymoskingdoms.trophies;

import com.jaymoskingdoms.Speech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A trophy stand, in wood or in stone: a low plinth with a dish on a short post, a brass plate on its front. Right-click
 * with something (a great foe's head or trophy, a relic: anything) to set it on the dish; right-click empty-handed to read
 * the plate (a foe's trophy shows its foe's name to someone who has learned it, {@link Trophies#knowsName}) and, crouching,
 * to take it back. A villager nearby remarks on a trophy newly set there.
 */
public class TrophyStandBlock extends Block implements EntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** The plinth, the post and the dish, as one shape for every facing (the plate is too small to matter). */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(6, 2, 6, 10, 8, 10), Block.box(3, 8, 3, 13, 10, 13));

	public TrophyStandBlock(Block.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrophyStandEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof TrophyStandEntity stand && stand.stack().isEmpty() && !stack.isEmpty()) {
			if (level instanceof ServerLevel server) {
				stand.setStack(stack.copyWithCount(1));
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
				}
				level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8F, 0.9F);
				remark(server, pos);
				plate(player, stand);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof TrophyStandEntity stand && !stand.stack().isEmpty()) {
			if (!level.isClientSide()) {
				if (player.isShiftKeyDown()) {
					player.getInventory().placeItemBackInInventory(stand.take(), Prediction.SERVER_ONLY);
					level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
				} else {
					plate(player, stand);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** The plate, said to {@code player}: a foe's trophy gives its foe's name only to someone who has learned it. */
	static void plate(Player player, TrophyStandEntity stand) {
		String kind = Trophies.foeOf(stand.stack());
		if (kind == null) {
			return;
		}
		Component text = Trophies.knowsName(player, kind, stand.stack()) ? Trophies.foeName(kind)
			: Component.translatable("block.jaymoskingdoms.trophy_stand.unnamed");
		player.sendOverlayMessage(text);
	}

	/** A villager within reach of the stand remarks on the trophy newly set on it, once. */
	private static void remark(ServerLevel level, BlockPos pos) {
		Villager villager = null;
		double best = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(pos).inflate(Trophies.REMARK_RANGE), LivingEntity::isAlive)) {
			double d = v.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (d < best) {
				best = d;
				villager = v;
			}
		}
		if (villager != null) {
			Speech.say(level, villager, Component.translatable("speech.jaymoskingdoms.trophy." + level.getRandom().nextInt(Trophies.REMARK_LINES)), Speech.GOLD, 16.0);
		}
	}
}
