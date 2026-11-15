package com.jaymoskingdoms.wild.nest;

import com.jaymoskingdoms.Hearing;
import com.jaymoskingdoms.Knowledge;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, de-broodqueen) The brood queen's egg, on its silk mound in the deepest chamber of a spider nest
 * ({@link NestStructures}), with the queen asleep over it ({@link BroodQueen}). Strike it three times (it can't be broken
 * any other way) and it splits and she wakes; each blow cracks it ({@link #CRACKS} 1 and 2) and she stirs. Only someone with
 * the knowledge for it ({@link #WAKE_KNOWLEDGE}, Kingdoms' boss ladder) can crack it: for anyone else it rings dull and a
 * line on the action bar says why, as every knowledge gate does.
 * <p>
 * Split ({@link #CRACKS} 3), it grows back {@link #REGROW_TICKS} later (an hour), unless its nest was burned out: a queen
 * still alive and idle at home settles back onto it to sleep, and if she was killed a new one is there asleep on it.
 */
public class BroodEggBlock extends BaseEntityBlock {
	// ---- tuning ----
	/** The knowledge level that may wake her (1: the Goblin King slain). */
	static final int WAKE_KNOWLEDGE = 1;
	/** Blows closer together than this (ticks) count as one. */
	static final int STRIKE_GAP = 8;
	/** It grows back this long (ticks) after it split: an hour. */
	static final int REGROW_TICKS = 72000;
	/** How often the egg looks after its queen (ticks), and how far round it (blocks) a queen counts as its own. */
	static final int LOOK_EVERY = 100;
	static final double QUEEN_NEAR = 48.0;

	/** 0 whole, 1 and 2 cracked, 3 split open (spent, growing back). */
	public static final IntegerProperty CRACKS = IntegerProperty.create("cracks", 0, 3);
	private static final VoxelShape WHOLE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0), SPLIT = Block.box(1.0, 0.0, 1.0, 15.0, 7.0, 15.0);

	public BroodEggBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CRACKS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CRACKS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CRACKS) == 3 ? SPLIT : WHOLE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Entity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, SpiderNests.BROOD_EGG_ENTITY, (l, p, s, egg) -> egg.serverTick((ServerLevel) l, s));
	}

	/** A blow on the egg (a player starting to break it). */
	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer striker && level.getBlockEntity(pos) instanceof Entity egg) {
			egg.struck(server, state, striker);
		}
	}

	/** The egg's own: when it split, and the time of the last blow. */
	public static final class Entity extends BlockEntity {
		private long split = -1L, lastBlow = -1L;

		public Entity(BlockPos pos, BlockState state) {
			super(SpiderNests.BROOD_EGG_ENTITY, pos, state);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			this.split = input.getLongOr("split", -1L);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			output.putLong("split", this.split);
		}

		void struck(ServerLevel level, BlockState state, ServerPlayer striker) {
			long now = level.getGameTime();
			if (now - this.lastBlow < STRIKE_GAP) {
				return;
			}
			this.lastBlow = now;
			BlockPos pos = this.getBlockPos();
			int cracks = state.getValue(CRACKS);
			if (cracks == 3) {
				level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.6F, 0.6F);
				return;
			}
			if (!Knowledge.has(striker, WAKE_KNOWLEDGE)) {
				// as every knowledge gate does: a dull sound and a line on the action bar
				striker.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.knowledge.locked.brood_egg"));
				level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.8F, 0.5F);
				return;
			}
			cracks++;
			level.setBlock(pos, state.setValue(CRACKS, cracks), Block.UPDATE_ALL);
			Vec3 c = Vec3.atCenterOf(pos);
			level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 1.4F, 0.55F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y + 0.3, c.z, 12, 0.4, 0.3, 0.4, 0.05);
			Hearing.noise(level, c, 20.0, striker.getUUID(), Hearing.Kind.IMPACT);
			BroodQueen queen = queen(level);
			if (cracks < 3) {
				if (queen != null) {
					queen.stir();
				}
				return;
			}
			// split: she wakes
			this.split = now;
			this.setChanged();
			level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.HOSTILE, 2.0F, 0.4F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y + 0.5, c.z, 50, 0.8, 0.6, 0.8, 0.1);
			if (queen == null) {
				queen = lay(level);
			}
			if (queen != null) {
				queen.wake(striker);
			}
		}

		void serverTick(ServerLevel level, BlockState state) {
			if ((level.getGameTime() + this.getBlockPos().asLong()) % LOOK_EVERY != 0) {
				return;
			}
			if (SpiderNests.scarred(level, this.getBlockPos())) {
				// a burned-out nest stays dead: nothing grows back, no new queen
				return;
			}
			BroodQueen queen = queen(level);
			if (state.getValue(CRACKS) == 3) {
				if (this.split >= 0 && level.getGameTime() - this.split < REGROW_TICKS) {
					return;
				}
				if (queen != null && !queen.settles()) {
					// she's still out hunting: it waits for her
					return;
				}
				level.setBlock(this.getBlockPos(), state.setValue(CRACKS, 0), Block.UPDATE_ALL);
				level.playSound(null, this.getBlockPos(), SoundEvents.SNIFFER_EGG_PLOP, SoundSource.BLOCKS, 1.0F, 0.5F);
				this.split = -1L;
				this.setChanged();
				if (queen != null) {
					queen.settle();
				} else {
					lay(level);
				}
			} else if (queen == null) {
				lay(level);
			}
		}

		/** The queen of this egg (any brood queen near, whose home it is), or null. */
		private @Nullable BroodQueen queen(ServerLevel level) {
			List<BroodQueen> near = level.getEntitiesOfClass(BroodQueen.class, new AABB(this.getBlockPos()).inflate(QUEEN_NEAR), BroodQueen::isAlive);
			for (BroodQueen q : near) {
				if (this.getBlockPos().equals(q.home())) {
					return q;
				}
			}
			return near.isEmpty() ? null : near.get(0);
		}

		/** A new queen asleep beside the egg, curled round it on open floor; null if there's no room. */
		private @Nullable BroodQueen lay(ServerLevel level) {
			BroodQueen queen = SpiderNests.BROOD_QUEEN.create(level, EntitySpawnReason.STRUCTURE);
			if (queen == null) {
				return null;
			}
			BlockPos egg = this.getBlockPos();
			for (int r = 3; r <= 6; r++) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					for (int dy = 1; dy >= -2; dy--) {
						BlockPos at = egg.relative(d, r).above(dy);
						if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
							continue;
						}
						queen.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, d.getOpposite().toYRot(), 0.0F);
						if (level.noCollision(queen)) {
							queen.sleepOn(egg);
							level.addFreshEntity(queen);
							return queen;
						}
					}
				}
			}
			queen.discard();
			return null;
		}
	}
}
