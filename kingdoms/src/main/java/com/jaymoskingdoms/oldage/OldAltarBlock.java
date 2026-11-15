package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.Patron;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (medieval picks, cv-avatars) An old-age temple's altar: raised to one patron ({@link #PATRON}, a {@link Patron}'s
 * ordinal), burning while it waits for an offering ({@link #LIT}). An offering of that patron's laid on it calls the
 * patron's champion down ({@link OldTemples#offer}); the fire goes out, and comes back on its own in a few days
 * ({@link OldTemples#RELIGHT_CHANCE}).
 */
public class OldAltarBlock extends Block {
	public static final IntegerProperty PATRON = IntegerProperty.create("patron", 0, 7);
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	public OldAltarBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PATRON, 0).setValue(LIT, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PATRON, LIT);
	}

	public static Patron patron(BlockState state) {
		return Patron.values()[Math.min(state.getValue(PATRON), Patron.values().length - 1)];
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			return OldTemples.offer(server, pos, state, sp, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return !state.getValue(LIT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextFloat() < OldTemples.RELIGHT_CHANCE) {
			level.setBlockAndUpdate(pos, state.setValue(LIT, true));
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
		}
	}
}
