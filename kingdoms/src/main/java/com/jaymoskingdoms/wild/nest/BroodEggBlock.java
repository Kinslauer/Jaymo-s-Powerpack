package com.jaymoskingdoms.wild.nest;

import com.jaymoskingdoms.Hearing;
import com.jaymoskingdoms.Knowledge;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
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
 * the knowledge for it ({@link #WAKE_KNOWLEDGE}, Kingdoms' boss ladder) can crack it: for anyone else it only rings dull and
 * puffs dust.
 * <p>
 * Split ({@link #CRACKS} 3), it grows back {@link #REGROW_TICKS} later (an hour): a queen still alive and idle at home settles
 * back onto it to sleep, and if she was killed a new one is there asleep on it. The egg knows its queen by her id, so a
 * queen whose chunk hasn't loaded yet is never doubled: a new one is laid only once her death is recorded here, or once the
 * lair's entities have been loaded and ticking for {@link #LOAD_WAIT} with no sign of her. A second queen of this egg is
 * sent away. If any of the lair burned ({@link #burn}), nothing grows back and no queen comes again.
 */
public class BroodEggBlock extends BaseEntityBlock {
	// ---- tuning ----
	/** The knowledge level that may wake her (1: the Goblin King slain). */
	static final int WAKE_KNOWLEDGE = 1;
	/** Blows closer together than this (ticks) count as one. */
	static final int STRIKE_GAP = 8;
	/** It grows back this long (ticks) after it split: an hour. */
	static final int REGROW_TICKS = 72000;
	/** How often the egg looks after its queen (ticks), and how far round it (blocks) a queen of its own is looked for. */
	static final int LOOK_EVERY = 100;
	static final double QUEEN_NEAR = 48.0;
	/** With no word of its queen, the lair's chunks (this many round the egg's) must have had their entities loaded and
	 * ticking this long (ticks) before a new queen is laid. */
	static final int LOAD_CHUNKS = 2, LOAD_WAIT = 300;
	/** How far (blocks, across and up or down) from the egg a fire counts as burning its lair. */
	static final int LAIR_REACH = 20, LAIR_RISE = 12;

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

	/** The egg whose lair {@code pos} is in (loaded, the nearest), or null. */
	static @Nullable Entity lairOf(ServerLevel level, BlockPos pos) {
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, reach = (LAIR_REACH >> 4) + 1;
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos at = be.getBlockPos();
					if (be instanceof Entity egg && Math.abs(at.getX() - pos.getX()) <= LAIR_REACH && Math.abs(at.getZ() - pos.getZ()) <= LAIR_REACH
						&& Math.abs(at.getY() - pos.getY()) <= LAIR_RISE && at.distSqr(pos) < bestD) {
						bestD = at.distSqr(pos);
						best = egg;
					}
				}
			}
		}
		return best;
	}

	/**
	 * The egg's own: when it split, the time of the last blow, its queen (by id, and whether her death is recorded here),
	 * and whether its lair burned.
	 */
	public static final class Entity extends BlockEntity {
		private long split = -1L, lastBlow = -1L;
		private @Nullable UUID queenId;
		private boolean queenDead, burned;
		/** How long (ticks) the lair's entities have been loaded and ticking without a break. Not saved. */
		private int loadedFor;

		public Entity(BlockPos pos, BlockState state) {
			super(SpiderNests.BROOD_EGG_ENTITY, pos, state);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			this.split = input.getLongOr("split", -1L);
			this.queenId = input.read("queen", UUIDUtil.CODEC).orElse(null);
			this.queenDead = input.getBooleanOr("queen_dead", false);
			this.burned = input.getBooleanOr("burned", false);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			output.putLong("split", this.split);
			if (this.queenId != null) {
				output.store("queen", UUIDUtil.CODEC, this.queenId);
			}
			output.putBoolean("queen_dead", this.queenDead);
			output.putBoolean("burned", this.burned);
		}

		/** Her lair burned: it stays dead, and so do the chunks of her chamber (the nest there spreads no more). */
		void burn(ServerLevel level) {
			if (this.burned) {
				return;
			}
			this.burned = true;
			this.setChanged();
			ChunkPos home = ChunkPos.containing(this.getBlockPos());
			for (int x = home.x() - 1; x <= home.x() + 1; x++) {
				for (int z = home.z() - 1; z <= home.z() + 1; z++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
					if (chunk != null) {
						chunk.setAttached(SpiderNests.SCARRED, true);
					}
				}
			}
		}

		/** Her queen {@code id} died (from {@link BroodQueen#die}). */
		void died(UUID id) {
			if (id.equals(this.queenId)) {
				this.queenDead = true;
				this.setChanged();
			}
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
			Vec3 c = Vec3.atCenterOf(pos);
			if (!Knowledge.has(striker, WAKE_KNOWLEDGE)) {
				// it only rings dull and puffs dust
				level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.8F, 0.5F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()), c.x, c.y + 0.4, c.z, 6, 0.3, 0.2,
					0.3, 0.02);
				return;
			}
			cracks++;
			level.setBlock(pos, state.setValue(CRACKS, cracks), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 1.4F, 0.55F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y + 0.3, c.z, 12, 0.4, 0.3, 0.4, 0.05);
			Hearing.noise(level, c, 20.0, striker.getUUID(), Hearing.Kind.IMPACT);
			BroodQueen queen = this.queen(level);
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
			if (queen == null && !this.burned && this.mayLay()) {
				queen = this.lay(level);
			}
			if (queen != null) {
				queen.wake(striker);
			}
		}

		void serverTick(ServerLevel level, BlockState state) {
			if ((level.getGameTime() + this.getBlockPos().asLong()) % LOOK_EVERY != 0) {
				return;
			}
			this.loadedFor = this.lairLoaded(level) ? this.loadedFor + LOOK_EVERY : 0;
			if (this.burned || SpiderNests.scarred(level, this.getBlockPos())) {
				// a burned-out lair stays dead: nothing grows back, no new queen
				return;
			}
			BroodQueen queen = this.queen(level);
			if (state.getValue(CRACKS) == 3) {
				if (this.split >= 0 && level.getGameTime() - this.split < REGROW_TICKS) {
					return;
				}
				if (queen != null && !queen.settles() || queen == null && !this.mayLay()) {
					// she's still out hunting (or not yet known to be gone): it waits for her
					return;
				}
				level.setBlock(this.getBlockPos(), state.setValue(CRACKS, 0), Block.UPDATE_ALL);
				level.playSound(null, this.getBlockPos(), SoundEvents.SNIFFER_EGG_PLOP, SoundSource.BLOCKS, 1.0F, 0.5F);
				this.split = -1L;
				this.setChanged();
				if (queen != null) {
					queen.settle();
				} else {
					this.lay(level);
				}
			} else if (queen == null && this.mayLay()) {
				this.lay(level);
			}
		}

		/** A new queen may be laid: the old one's death is recorded, or the lair has been loaded long enough to know she's gone. */
		private boolean mayLay() {
			return this.queenDead || this.loadedFor >= LOAD_WAIT;
		}

		/** Whether every chunk of the lair (round the egg) has its entities loaded and ticking. */
		private boolean lairLoaded(ServerLevel level) {
			ChunkPos home = ChunkPos.containing(this.getBlockPos());
			for (int x = home.x() - LOAD_CHUNKS; x <= home.x() + LOAD_CHUNKS; x++) {
				for (int z = home.z() - LOAD_CHUNKS; z <= home.z() + LOAD_CHUNKS; z++) {
					if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z))) {
						return false;
					}
				}
			}
			return true;
		}

		/**
		 * The queen of this egg, or null: the one it knows by id, if she's loaded and alive; else one near whose home this egg
		 * is, taken as its own if it has none living. Any other queen of this egg near is a double, and is sent away.
		 */
		private @Nullable BroodQueen queen(ServerLevel level) {
			BroodQueen own = null;
			if (this.queenId != null && !this.queenDead && level.getEntity(this.queenId) instanceof BroodQueen q && q.isAlive()) {
				own = q;
			}
			List<BroodQueen> near = level.getEntitiesOfClass(BroodQueen.class, new AABB(this.getBlockPos()).inflate(QUEEN_NEAR), BroodQueen::isAlive);
			for (BroodQueen q : near) {
				if (q == own || !this.getBlockPos().equals(q.home())) {
					continue;
				}
				if (own == null && (this.queenId == null || this.queenDead || this.loadedFor >= LOAD_WAIT)) {
					own = q;
					this.queenId = q.getUUID();
					this.queenDead = false;
					this.setChanged();
				} else {
					q.discard();
				}
			}
			return own;
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
							this.queenId = queen.getUUID();
							this.queenDead = false;
							this.loadedFor = 0;
							this.setChanged();
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
