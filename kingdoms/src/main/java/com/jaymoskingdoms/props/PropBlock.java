package com.jaymoskingdoms.props;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.440, Motion patch 9) A placed prop that faces the way it was set down, with its own shape: scarecrow, gravestone,
 * lamp post, the plushies (a squeak when you press one), and the base for the candle stand and planters. A scarecrow
 * sends birds and rabbits off the crops round it.
 */
public class PropBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	/** What the prop is (its shape, and what it does). */
	public enum Kind {
		SCARECROW(6, 0, 6, 10, 24, 10),
		GRAVESTONE(2, 0, 5, 14, 14, 11),
		LAMP_POST(6, 0, 6, 10, 24, 10),
		CANDLE_STAND(4, 0, 6, 12, 15, 10),
		PLUSH(4, 0, 4, 12, 11, 12),
		WINDOW_BOX(0, 0, 9, 16, 7, 16),
		POT_PLANTER(3, 0, 3, 13, 8, 13);

		/** Its box facing north (pixels). */
		final double[] box;

		Kind(double... box) {
			this.box = box;
		}
	}

	/** A scarecrow: how often it looks about (ticks), how far it scares, and how far off it sends them. */
	static final int SCARE_EVERY = 40;
	static final double SCARE_RANGE = 10.0, SCARE_OFF = 8.0, SCARE_SPEED = 1.3;
	/** A plush's squeak: its pitch, give or take. */
	static final float SQUEAK_PITCH = 1.7F, SQUEAK_SPREAD = 0.25F;

	final Kind kind;
	private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

	public PropBlock(BlockBehaviour.Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			this.shapes.put(d, turned(kind.box, d));
		}
		this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	/** A box drawn facing north, turned to face {@code d}. */
	static VoxelShape turned(double[] b, Direction d) {
		double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
		return switch (d) {
			case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
			case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.box(x0, y0, z0, x1, y1, z1);
		};
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.get(state.getValue(FACING));
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
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (this.kind == Kind.PLUSH) {
			squeak(level, Vec3.atCenterOf(pos), player);
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	/** A plush squeaks. */
	static void squeak(Level level, Vec3 at, Player player) {
		level.playSound(player, at.x, at.y, at.z, SoundEvents.BAT_AMBIENT, SoundSource.BLOCKS, 0.6F,
			SQUEAK_PITCH + (level.getRandom().nextFloat() - 0.5F) * SQUEAK_SPREAD);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (this.kind == Kind.SCARECROW && !old.is(this)) {
			level.scheduleTick(pos, this, SCARE_EVERY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (this.kind != Kind.SCARECROW) {
			return;
		}
		Vec3 here = Vec3.atCenterOf(pos);
		for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, new AABB(pos).inflate(SCARE_RANGE), PropBlock::scared)) {
			Vec3 away = mob.position().subtract(here).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = here.add(away.scale(SCARE_RANGE + SCARE_OFF));
			mob.getNavigation().moveTo(to.x, mob.getY(), to.z, SCARE_SPEED);
		}
		level.scheduleTick(pos, this, SCARE_EVERY);
	}

	/** What a scarecrow sends off: birds that peck at crops, and rabbits. */
	private static boolean scared(PathfinderMob mob) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		return mob.isAlive() && !mob.isLeashed() && !mob.isPersistenceRequired()
			&& (id.equals("minecraft:chicken") || id.equals("minecraft:rabbit") || id.equals("minecraft:parrot") || id.equals("jaymoskingdoms:pigeon"));
	}
}
