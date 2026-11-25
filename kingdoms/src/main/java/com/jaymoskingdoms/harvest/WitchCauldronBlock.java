package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.SoulJars;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A witch's cauldron. Set on soul fire (or a lit soul campfire), it brews only at night: put in two rotten pumpkins (Kitchen's,
 * which a field gives when a pumpkin comes up bad) and the soul of one of the dead (a full soul jar whose soul is of an
 * undead kind; the jar comes back empty), and it boils them down over {@link #STAGES} stages of {@link #STAGE_TICKS} ticks into
 * {@link #RESIN} wicked resin, which you take with an empty hand. By day, or with the fire out, the pot just keeps what is in it.
 * No ghost is made or shown: the soul only comes out of a jar, and the jar is a Death Sorcerer's craft ({@code SoulJars}).
 */
public final class WitchCauldronBlock extends Block {
	/** Rotten pumpkins in the pot (0 to {@link #PUMPKINS_NEEDED}), whether a soul is in it, and the stage of the brew (0 not begun, {@link #STAGES} done). */
	public static final IntegerProperty PUMPKINS = IntegerProperty.create("pumpkins", 0, 2);
	public static final BooleanProperty SOUL = BooleanProperty.create("soul");
	public static final IntegerProperty BREW = IntegerProperty.create("brew", 0, 3);
	// ---- tuning ----
	public static final int PUMPKINS_NEEDED = 2, STAGES = 3, STAGE_TICKS = 200, RESIN = 2;
	/** Night, in ticks of the day. */
	static final long DUSK = 13000L, DAWN = 23000L;
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

	public WitchCauldronBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PUMPKINS, 0).setValue(SOUL, false).setValue(BREW, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PUMPKINS, SOUL, BREW);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Night on this level's clock. */
	static boolean night(Level level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= DUSK && time <= DAWN;
	}

	private static boolean onSoulFire(Level level, BlockPos pos) {
		BlockState under = level.getBlockState(pos.below());
		return under.is(Blocks.SOUL_FIRE) || under.is(Blocks.SOUL_CAMPFIRE) && under.getValue(CampfireBlock.LIT);
	}

	private static boolean ready(BlockState state) {
		return state.getValue(PUMPKINS) >= PUMPKINS_NEEDED && state.getValue(SOUL) && state.getValue(BREW) < STAGES;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		Item rotten = KitchenCompat.item("rotten_pumpkin");
		boolean pumpkin = rotten != null && held.is(rotten) && state.getValue(PUMPKINS) < PUMPKINS_NEEDED;
		EntityType<?> soul = held.is(SoulJars.FULL_SOUL_JAR) ? SoulJars.soulIn(held) : null;
		boolean jar = soul != null && soul.builtInRegistryHolder().is(EntityTypeTags.UNDEAD) && !state.getValue(SOUL);
		if (!pumpkin && !jar) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			BlockState next = pumpkin ? state.setValue(PUMPKINS, state.getValue(PUMPKINS) + 1) : state.setValue(SOUL, true);
			server.setBlockAndUpdate(pos, next);
			if (pumpkin) {
				held.consume(1, player);
				server.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 0.7F);
			} else {
				player.setItemInHand(hand, new ItemStack(SoulJars.SOUL_JAR));
				server.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 0.8F);
				Fx.ring(server, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0), 0.7F, WickedHarvest.SOUL);
			}
			if (ready(next)) {
				server.scheduleTick(pos, this, STAGE_TICKS);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(BREW) < STAGES) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			server.setBlockAndUpdate(pos, defaultBlockState());
			Block.popResource(server, pos.above(), new ItemStack(WickedHarvest.WICKED_RESIN, RESIN));
			server.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 0.6F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && ready(state)) {
			server.scheduleTick(pos, this, STAGE_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!ready(state)) {
			return;
		}
		if (night(level) && onSoulFire(level, pos)) {
			BlockState next = state.setValue(BREW, state.getValue(BREW) + 1);
			level.setBlockAndUpdate(pos, next);
			Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
			Fx.pool(level, top, 0.5F, WickedHarvest.SOUL, STAGE_TICKS);
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 0.5F + 0.2F * next.getValue(BREW));
			if (next.getValue(BREW) >= STAGES) {
				Fx.ring(level, top, 0.9F, WickedHarvest.FIRE);
				return;
			}
		}
		// (by day, or with the fire out, it waits and looks again)
		level.scheduleTick(pos, this, STAGE_TICKS);
	}
}
