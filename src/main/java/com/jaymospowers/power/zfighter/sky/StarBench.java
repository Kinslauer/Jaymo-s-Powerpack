package com.jaymospowers.power.zfighter.sky;

import com.jaymospowers.power.zfighter.ZFighterLookout;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.18.18, Andy's SevTech picks: "Star-Sight Bench") A stone bench with a lens on a post. Set on the Lookout's platform
 * ({@link ZFighterLookout#onPlatform}) under open sky on a clear night it wakes: the lens glows ({@link #AWAKE}), it hums and
 * lets off a mote now and then. Used awake it gives its user the chart of the Pale Moon, once for each player. It makes no pod
 * and does nothing about pods. Asleep it is only a bench. It looks at the sky again every {@link #LOOK_TICKS} ticks.
 */
public class StarBench extends Block {
	// ---- tuning ----
	/** How often (ticks) a bench looks at the sky, and how often an awake one hums. */
	public static final int LOOK_TICKS = 100;
	/** An awake bench: its light, the hum's volume and pitch, the motes it lets off each look. */
	public static final int AWAKE_LIGHT = 9;
	static final float HUM_VOLUME = 0.5F, HUM_PITCH = 0.5F;
	static final int MOTES = 3;

	public static final BooleanProperty AWAKE = BooleanProperty.create("awake");
	/** The bench's body (pixels of 16); tools/gen_star_sky.py draws the same boxes. */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(6, 4, 6, 10, 9, 10), Block.box(5, 9, 5, 11, 12, 11));

	public StarBench(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AWAKE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AWAKE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Whether {@code pos} is a place the bench wakes: on the Lookout, under open sky, on a clear night. */
	public static boolean wakes(Level level, BlockPos pos) {
		return level instanceof ServerLevel server && ZFighterLookout.onPlatform(server, pos) && level.canSeeSky(pos.above())
			&& level.isDarkOutside() && !level.isRaining() && !level.isThundering();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!level.isClientSide() && !old.is(this)) {
			level.scheduleTick(pos, this, 20);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		look(state, level, pos);
		level.scheduleTick(pos, this, LOOK_TICKS);
	}

	/** Looks at the sky: wakes or sleeps, and while awake hums and lets off a few motes. */
	private void look(BlockState state, ServerLevel level, BlockPos pos) {
		boolean awake = wakes(level, pos);
		if (awake != state.getValue(AWAKE)) {
			level.setBlock(pos, state.setValue(AWAKE, awake), Block.UPDATE_ALL);
		}
		if (awake) {
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, HUM_VOLUME, HUM_PITCH);
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, MOTES, 0.15, 0.2, 0.15, 0.01);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)) {
			return InteractionResult.SUCCESS;
		}
		look(state, server, pos);
		if (!wakes(server, pos)) {
			user.sendOverlayMessage(Component.translatable("message.jaymospowers.star_bench.asleep").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (Charts.benchGiven(user)) {
			user.sendOverlayMessage(Component.translatable("message.jaymospowers.star_bench.hum").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		Charts.benchDone(user);
		if (Charts.read(user, ZFighterSpace.MOON)) {
			user.sendOverlayMessage(Component.translatable("message.jaymospowers.star_bench.known").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		ItemStack chart = Charts.stack(ZFighterSpace.MOON);
		if (!user.getInventory().add(chart)) {
			user.drop(chart, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		server.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 1.4F);
		user.sendOverlayMessage(Component.translatable("message.jaymospowers.star_bench.chart").withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS;
	}
}
