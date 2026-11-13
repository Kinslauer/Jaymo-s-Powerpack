package com.jaymoskingdoms;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * The climbing rope: a coil that knots itself up a wall. Use it on the side of a wall: a knot ties at the spot and the rope runs
 * up from it, one knot after another, as far as {@link #LENGTH} (it stops at a ledge, a roof, or a wall that will not hold it).
 * Climb it as a ladder. After {@link #HOLDS} ticks it unknots and falls away. The coil is never used up, only rested
 * ({@link #COOLDOWN}).
 */
public final class ClimbingRope {
	/** How many knots a rope ties, how fast (ticks apart), how long it holds (ticks), and the coil's rest (ticks). */
	static final int LENGTH = 16, STEP = 2, HOLDS = 20 * 60 * 3, COOLDOWN = 20 * 20;
	/** The knots still to tie above a knot (0: tied all it will). */
	static final IntegerProperty LEFT = IntegerProperty.create("left", 0, LENGTH);
	public static Item ITEM;
	public static Block KNOT;

	private ClimbingRope() {
	}

	static void register() {
		KNOT = Gadgets.block("climbing_knot", Knot::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.4F).sound(SoundType.WOOL)
			.noOcclusion().pushReaction(PushReaction.POPPED).ignitedByLava());
		ITEM = Gadgets.item("climbing_rope", Rarity.UNCOMMON, Coil::new);
	}

	/** The coil in a hand. */
	static final class Coil extends Item {
		Coil(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Direction face = context.getClickedFace();
			if (!face.getAxis().isHorizontal()) {
				return InteractionResult.PASS;
			}
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(face);
			BlockState first = KNOT.defaultBlockState().setValue(LadderBlock.FACING, face).setValue(LEFT, LENGTH - 1);
			if (!level.getBlockState(at).canBeReplaced() || !first.canSurvive(level, at)) {
				return InteractionResult.FAIL;
			}
			level.setBlock(at, first, 3);
			level.scheduleTick(at, KNOT, STEP);
			level.playSound(null, at, SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 0.9F, 1.1F);
			if (context.getPlayer() != null) {
				context.getPlayer().getCooldowns().addCooldown(context.getItemInHand(), COOLDOWN);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.climbing_rope").withStyle(ChatFormatting.GRAY));
		}
	}

	/** One knot of the rope, a ladder in all but its look; it ties the next one above, and in time unties itself. */
	static final class Knot extends LadderBlock {
		Knot(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(defaultBlockState().setValue(LEFT, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(LEFT);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			int left = state.getValue(LEFT);
			if (left <= 0) {
				// its time is up: it unties
				level.removeBlock(pos, false);
				level.playSound(null, pos, SoundEvents.LEAD_BREAK, SoundSource.BLOCKS, 0.5F, 1.2F);
				return;
			}
			BlockPos up = pos.above();
			BlockState next = state.setValue(LEFT, left - 1);
			if (level.getBlockState(up).canBeReplaced() && next.canSurvive(level, up)) {
				level.setBlock(up, next, 3);
				level.scheduleTick(up, this, STEP);
				level.sendParticles(Fx.PALE, up.getX() + 0.5, up.getY() + 0.5, up.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.0);
				level.playSound(null, up, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
			}
			level.setBlock(pos, state.setValue(LEFT, 0), 3);
			level.scheduleTick(pos, this, HOLDS);
		}
	}
}
