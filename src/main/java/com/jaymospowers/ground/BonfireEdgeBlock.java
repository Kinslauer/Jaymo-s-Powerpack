package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the eight outer blocks of a {@link BonfireBlock bonfire}: its share of the stone ring, ash and burning wood.
 * Uses on it (fuel, food, flint and steel...) go to the centre; it lights and burns with the centre ({@link #LIT}), and
 * breaking it breaks the whole bonfire.
 */
public class BonfireEdgeBlock extends Block {
	/** Where this part sits around the centre. */
	public enum Part implements StringRepresentable {
		NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0), NORTH_WEST(-1, -1), NORTH_EAST(1, -1), SOUTH_WEST(-1, 1), SOUTH_EAST(1, 1);

		/** This part's offset from the centre. */
		public final int dx;
		public final int dz;

		Part(int dx, int dz) {
			this.dx = dx;
			this.dz = dz;
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);

	public BonfireEdgeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PART, Part.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** The bonfire centre this part belongs to. */
	public static BlockPos centre(BlockPos pos, BlockState state) {
		Part part = state.getValue(PART);
		return pos.offset(-part.dx, 0, -part.dz);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		BlockPos centre = centre(pos, state);
		BlockState fire = level.getBlockState(centre);
		if (!fire.is(GroundSticks.BONFIRE)) {
			return InteractionResult.PASS;
		}
		BlockHitResult atCentre = hit.withPosition(centre);
		InteractionResult result = fire.useItemOn(stack, level, player, hand, atCentre);
		if (result.consumesAction()) {
			return result;
		}
		// flint and steel, fire charges: the item's own use, aimed at the centre
		InteractionResult used = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, atCentre));
		return used.consumesAction() ? used : InteractionResult.PASS;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
		if (state.getValue(LIT) && entity instanceof LivingEntity && level instanceof ServerLevel server) {
			entity.hurtServer(server, server.damageSources().campfire(), 1.0F);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(4) == 0) {
			level.addParticle(random.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE, pos.getX() + 0.2 + random.nextDouble() * 0.6,
				pos.getY() + 0.3, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		collapse(level, centre(pos, state));
	}

	/** A bonfire missing a part falls apart: every part left at the centre is broken, dropping its loot. */
	static void collapse(ServerLevel level, BlockPos centre) {
		if (level.getBlockState(centre).is(GroundSticks.BONFIRE)) {
			level.destroyBlock(centre, true);
		}
		for (Part part : Part.values()) {
			BlockPos edge = centre.offset(part.dx, 0, part.dz);
			BlockState state = level.getBlockState(edge);
			if (state.is(GroundSticks.BONFIRE_EDGE) && state.getValue(PART) == part) {
				level.destroyBlock(edge, true);
			}
		}
	}
}
