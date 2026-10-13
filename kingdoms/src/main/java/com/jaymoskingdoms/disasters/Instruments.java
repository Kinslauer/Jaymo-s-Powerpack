package com.jaymoskingdoms.disasters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.6.10) Instruments for the weather: the storm bell (rings and gives a redstone signal while a tornado is near), the
 * weathervane (right click: the wind, and the nearest storm), and the barometer (held: what the sky is doing).
 */
public final class Instruments {
	/** The storm bell rings while a tornado (not a dust devil) is this near, checking this often (ticks). */
	static final double BELL_RANGE = 180.0;
	static final int BELL_EVERY = 40;
	/** The barometer and weathervane tell of storms this near. */
	static final double READ_RANGE = 600.0;

	private Instruments() {
	}

	/** Every player holding a barometer reads it (the action bar). */
	static void readBarometers(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (p.getMainHandItem().is(Disasters.BAROMETER) || p.getOffhandItem().is(Disasters.BAROMETER)) {
				p.sendOverlayMessage(reading(level, p.getX(), p.getZ()));
			}
		}
	}

	/** What the sky is doing near (x, z): a tornado first, then the nearest storm, else fair. */
	static Component reading(ServerLevel level, double x, double z) {
		Tornado nearT = null;
		double bestT = READ_RANGE * READ_RANGE;
		for (Tornado t : Tornadoes.SERVER) {
			double d = (t.x - x) * (t.x - x) + (t.z - z) * (t.z - z);
			if (t.size > Tornado.DUST_DEVIL && d < bestT) {
				bestT = d;
				nearT = t;
			}
		}
		if (nearT != null) {
			return Component.translatable("disaster.jaymoskingdoms.barometer.tornado", Storms.direction(x, z, nearT.x, nearT.z),
				Mth.floor(Math.sqrt(bestT)));
		}
		Storm nearS = null;
		double bestS = READ_RANGE * READ_RANGE;
		for (Storm s : Storms.SERVER) {
			double d = Math.max(0.0, Math.sqrt((s.x - x) * (s.x - x) + (s.z - z) * (s.z - z)) - s.radius);
			if (d * d < bestS) {
				bestS = d * d;
				nearS = s;
			}
		}
		if (nearS == null) {
			return Component.translatable("disaster.jaymoskingdoms.barometer.fair");
		}
		int d = Mth.floor(Math.sqrt(bestS));
		String kind = nearS.kind == Storm.SUPERCELL ? "supercell" : nearS.kind == Storm.THUNDER ? "thunder" : "rain";
		if (d == 0) {
			return Component.translatable("disaster.jaymoskingdoms.barometer.over." + kind);
		}
		return Component.translatable("disaster.jaymoskingdoms.barometer." + kind, Storms.direction(x, z, nearS.x, nearS.z), d);
	}

	/** The wind, as a weathervane shows it. */
	static Component wind(double x, double z) {
		double tx = x + Math.cos(Storms.windAngle) * 100.0, tz = z + Math.sin(Storms.windAngle) * 100.0;
		String strength = Storms.windSpeed > 0.07F ? "strong" : Storms.windSpeed > 0.055F ? "fresh" : "light";
		return Component.translatable("disaster.jaymoskingdoms.weathervane", Component.translatable("disaster.jaymoskingdoms.wind." + strength),
			Storms.direction(x, z, tx, tz));
	}

	/** The storm bell: rings while a tornado is near, and gives a full redstone signal. */
	public static final class StormBell extends Block {
		public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
		private static final VoxelShape SHAPE = Block.box(1, 0, 4, 15, 16, 12);

		public StormBell(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(POWERED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(POWERED);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
			if (!oldState.is(this)) {
				level.scheduleTick(pos, this, BELL_EVERY);
			}
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			boolean near = near(pos);
			if (near != state.getValue(POWERED)) {
				level.setBlock(pos, state.setValue(POWERED, near), Block.UPDATE_ALL);
			}
			if (near) {
				level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.8F + random.nextFloat() * 0.2F);
				level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5F, 1.0F);
			}
			level.scheduleTick(pos, this, BELL_EVERY);
		}

		private static boolean near(BlockPos pos) {
			for (Tornado t : Tornadoes.SERVER) {
				double dx = t.x - pos.getX(), dz = t.z - pos.getZ();
				if (t.size > Tornado.DUST_DEVIL && dx * dx + dz * dz < BELL_RANGE * BELL_RANGE) {
					return true;
				}
			}
			return false;
		}

		@Override
		protected boolean isSignalSource(BlockState state) {
			return true;
		}

		@Override
		protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
			return state.getValue(POWERED) ? 15 : 0;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
				p.sendOverlayMessage(reading(server, pos.getX(), pos.getZ()));
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The weathervane: right click tells the wind and the nearest storm. */
	public static final class Weathervane extends Block {
		private static final VoxelShape SHAPE = Shapes.or(Block.box(6, 0, 6, 10, 2, 10), Block.box(7.5, 2, 7.5, 8.5, 16, 8.5));

		public Weathervane(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
				p.sendSystemMessage(wind(pos.getX(), pos.getZ()));
				p.sendSystemMessage(reading(server, pos.getX(), pos.getZ()));
				level.playSound(null, pos, SoundEvents.BREEZE_IDLE_AIR, SoundSource.BLOCKS, 0.4F, 1.4F);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
