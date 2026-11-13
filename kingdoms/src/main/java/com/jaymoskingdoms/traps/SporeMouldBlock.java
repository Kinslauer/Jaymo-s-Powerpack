package com.jaymoskingdoms.traps;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

/**
 * Spore mould (Andy's pick F3: yellow mould spores), a mustard-yellow bloom in damp caves. It puffs a cloud of spores whenever
 * something walks near it, and spends some of its store ({@link #CHARGE}, 0 to {@link #FULL}) each time.
 * <ul>
 * <li><b>The cloud</b> reaches {@link #CLOUD} blocks. Anyone breathing it takes poison, nausea and a slowed gait, and
 * sneezes.</li>
 * <li><b>Hold your breath: sneak.</b> A player crouched low and moving slowly holds their breath (a held breath draws on the
 * air bar, {@link #HOLD_COST} of it for each puff, as under water), and passes through with nothing worse than a loss of air. When the bar is spent
 * mid-cloud they gasp and the spores take them after all. Mobs have no breath to hold.</li>
 * <li><b>Feeds on damp.</b> It recharges only with water within {@link #DAMP} blocks (or mud and moss); a spent mould in dry
 * rock dies, and a full one in damp ground spreads. So a cave's spores follow its water, and a dry cave loses them.</li>
 * <li><b>The answer:</b> sneak past, or wash it off: water poured beside it, or fire, ends the patch.</li>
 * </ul>
 */
public class SporeMouldBlock extends Block {
	public static final int FULL = 3;
	public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, FULL);
	// ---- tuning ----
	static final double TRIGGER = 3.0, CLOUD = 3.5;
	static final int DAMP = 5, TICK = 10, RELOAD = 120;
	/** The air a held breath costs for each puff (of 300), poison and nausea (ticks) of a breath taken in. */
	static final int HOLD_COST = 90, POISON = 20 * 6, NAUSEA = 20 * 8, SLOW = 20 * 5;
	/** Chances each random tick: to recharge when damp, to spread when full and damp, to die when spent and dry. */
	static final float RECHARGE = 0.4F, SPREAD = 0.2F, DIE = 0.25F;
	static final int YELLOW = 0xD6C23A;

	public SporeMouldBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CHARGE, FULL));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CHARGE);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		level.scheduleTick(pos, this, TICK);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.scheduleTick(pos, this, TICK);
		int charge = state.getValue(CHARGE);
		if (charge <= 0 || state.getValue(CHARGE) < 0) {
			return;
		}
		boolean disturbed = false;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(TRIGGER),
			l -> l.isAlive() && !l.isSpectator() && l.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4)) {
			// a creature creeping on tiptoe doesn't stir it
			if (!(e instanceof Player p && p.isShiftKeyDown() && p.getDeltaMovement().horizontalDistanceSqr() < 0.004)) {
				disturbed = true;
				break;
			}
		}
		if (!disturbed) {
			return;
		}
		puff(level, pos, state, random);
	}

	/** The cloud: spent a charge, spores on everyone in reach. */
	private void puff(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		level.setBlockAndUpdate(pos, state.setValue(CHARGE, state.getValue(CHARGE) - 1));
		level.playSound(null, pos, SoundEvents.PANDA_SNEEZE, SoundSource.BLOCKS, 0.7F, 1.4F);
		Fx.pool(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), (float) CLOUD, YELLOW, 40);
		level.sendParticles(Fx.miasma(YELLOW, 1.4F), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 24, CLOUD / 2, 0.8, CLOUD / 2, 0.01);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(CLOUD), l -> l.isAlive() && !l.isSpectator())) {
			if (e instanceof Player player && player.isShiftKeyDown()) {
				// a held breath: it draws on the air, and the spores wait for the last of it
				int air = player.getAirSupply() - HOLD_COST;
				if (air > 0) {
					player.setAirSupply(air);
					continue;
				}
				player.setAirSupply(0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			e.addEffect(new MobEffectInstance(MobEffects.POISON, POISON, 0));
			e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW, 0));
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean damp = damp(level, pos);
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.is(Blocks.WATER) || n.is(net.minecraft.tags.BlockTags.FIRE)) {
				level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				level.sendParticles(Fx.miasma(YELLOW, 1.0F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0.01);
				return;
			}
		}
		int charge = state.getValue(CHARGE);
		if (!damp) {
			if (charge == 0 && random.nextFloat() < DIE) {
				level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			}
			return;
		}
		if (charge < FULL) {
			if (random.nextFloat() < RECHARGE) {
				level.setBlockAndUpdate(pos, state.setValue(CHARGE, charge + 1));
			}
		} else if (random.nextFloat() < SPREAD) {
			BlockPos next = Surfaces.neighbour(level, pos, null, random, false);
			if (next != null) {
				level.setBlockAndUpdate(next, state.setValue(CHARGE, 1));
			}
		}
	}

	/** Water, mud or moss within {@link #DAMP} blocks. */
	static boolean damp(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-DAMP, -DAMP, -DAMP), pos.offset(DAMP, DAMP, DAMP))) {
			BlockState s = level.getBlockState(p);
			if (s.getFluidState().is(FluidTags.WATER) || s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.CLAY)) {
				return true;
			}
		}
		return false;
	}
}
