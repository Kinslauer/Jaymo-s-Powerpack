package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.7.17) Torches burn down, the idea of Realistic Torches (remade here; none of its code or art). Every torch, floor
 * or wall, placed by a player, a settlement or world generation, burns about {@link #BURN_TICKS} on average (a chance
 * on each random tick, mixin.TorchBlockMixin), then goes out and is left a {@link #BURNT_TORCH}; rain on a torch open
 * to the sky puts it out much sooner. A burnt torch gives no light and drops a stick. Flint and steel, a fire charge or
 * a lit torch in hand lights it again, and villagers (MCA's too) walk over to burnt torches near them and relight them,
 * so a village stays lit as long as its folk are up and about (Andy, 2026-10-14).
 */
public final class Torches {
	/** How long a torch burns on average (ticks): two days. */
	static final long BURN_TICKS = 48000L;
	/** Ticks between a block's random ticks at the default random tick speed (3 per 16x16x16 section a tick). */
	static final int RANDOM_TICK_EVERY = 4096 / 3;
	/** A torch in the rain under open sky goes out with 1 chance in this many at each random tick (about 2 minutes). */
	static final int RAIN_DOUSE_CHANCE = 2;
	/** How often (ticks) villagers look for burnt torches to relight. */
	static final int LIGHTER_EVERY = 40;
	/** Chunks this far round a player are looked through for burnt torches. */
	static final int CHUNK_RADIUS = 6;
	/** A villager this close (blocks) to a burnt torch walks over to relight it; within {@link #LIGHTER_REACH} it does. */
	static final double LIGHTER_RANGE = 24.0, LIGHTER_REACH = 2.5;

	public static Block BURNT_TORCH, BURNT_WALL_TORCH;

	/** Per chunk: packed positions of its burnt torches, for the lamplighters. Saved. */
	static final AttachmentType<List<Long>> BURNT = AttachmentRegistry.create(JaymosKingdoms.id("burnt_torches"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private Torches() {
	}

	/** A burnt-out torch: smoulders now and then, no light. */
	static final class BurntTorch extends TorchBlock {
		BurntTorch(BlockBehaviour.Properties properties) {
			super(ParticleTypes.SMOKE, properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(6) == 0) {
				level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 0.0, 0.01, 0.0);
			}
		}
	}

	/** The same, on a wall. */
	static final class BurntWallTorch extends WallTorchBlock {
		BurntWallTorch(BlockBehaviour.Properties properties) {
			super(ParticleTypes.SMOKE, properties);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(6) == 0) {
				Direction away = state.getValue(FACING).getOpposite();
				level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + 0.27 * away.getStepX(), pos.getY() + 0.92,
					pos.getZ() + 0.5 + 0.27 * away.getStepZ(), 0.0, 0.01, 0.0);
			}
		}
	}

	public static void register() {
		BURNT_TORCH = block("burnt_torch", BurntTorch::new, BlockBehaviour.Properties.of());
		BURNT_WALL_TORCH = block("burnt_wall_torch", BurntWallTorch::new, BlockBehaviour.Properties.of().overrideLootTable(BURNT_TORCH.getLootTable())
			.overrideDescription(BURNT_TORCH.getDescriptionId()));
		ServerTickEvents.END_SERVER_TICK.register(Torches::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!state.is(BURNT_TORCH) && !state.is(BURNT_WALL_TORCH)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(Items.FLINT_AND_STEEL) && !held.is(Items.FIRE_CHARGE) && !held.is(Items.TORCH)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				BlockPos pos = hit.getBlockPos();
				BlockState lit = state.is(BURNT_WALL_TORCH)
					? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING))
					: Blocks.TORCH.defaultBlockState();
				relight(server, pos, state);
				if (held.is(Items.FLINT_AND_STEEL)) {
					held.hurtAndBreak(1, sp, hand);
					server.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				} else if (held.is(Items.FIRE_CHARGE)) {
					held.consume(1, sp);
					server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				} else {
					server.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.2F);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Floor and wall torches burn down (not soul or copper torches, nor our burnt ones). */
	public static boolean burns(BlockState state) {
		return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
	}

	/** A torch's random tick (mixin.TorchBlockMixin): maybe it burns out, sooner in the rain. */
	public static void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean out = level.isRainingAt(pos) ? random.nextInt(RAIN_DOUSE_CHANCE) == 0
			: random.nextInt((int) (BURN_TICKS / RANDOM_TICK_EVERY)) == 0;
		if (out) {
			goOut(level, pos, state);
		}
	}

	private static void goOut(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState burnt = state.is(Blocks.WALL_TORCH)
			? BURNT_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING))
			: BURNT_TORCH.defaultBlockState();
		level.setBlockAndUpdate(pos, burnt);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 6, 0.05, 0.05, 0.05, 0.01);
		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> burntHere = new ArrayList<>(chunk.getAttachedOrElse(BURNT, List.of()));
		if (!burntHere.contains(pos.asLong())) {
			burntHere.add(pos.asLong());
			chunk.setAttached(BURNT, burntHere);
		}
	}

	/** The burnt torch {@code state} at {@code pos} burns again. */
	static void relight(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState lit = state.is(BURNT_WALL_TORCH)
			? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING))
			: Blocks.TORCH.defaultBlockState();
		level.setBlockAndUpdate(pos, lit);
		forget(level, pos);
	}

	private static void forget(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> burntHere = chunk.getAttached(BURNT);
		if (burntHere != null && burntHere.contains(pos.asLong())) {
			List<Long> left = new ArrayList<>(burntHere);
			left.remove(pos.asLong());
			if (left.isEmpty()) {
				chunk.removeAttached(BURNT);
			} else {
				chunk.setAttached(BURNT, left);
			}
		}
	}

	/** Villagers near players walk to the burnt torches round them and light them again. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LIGHTER_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<ChunkPos> seen = new HashSet<>();
			Set<Villager> busy = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				ChunkPos at = player.chunkPosition();
				for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
					for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
						ChunkPos pos = new ChunkPos(at.x() + x, at.z() + z);
						if (seen.add(pos)) {
							LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
							if (chunk != null && chunk.hasAttached(BURNT)) {
								for (long packed : List.copyOf(chunk.getAttachedOrElse(BURNT, List.of()))) {
									tend(level, BlockPos.of(packed), busy);
								}
							}
						}
					}
				}
			}
		}
	}

	/** The nearest free, awake grown villager goes to the burnt torch at {@code pos}, and relights it once there. */
	private static void tend(ServerLevel level, BlockPos pos, Set<Villager> busy) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BURNT_TORCH) && !state.is(BURNT_WALL_TORCH)) {
			forget(level, pos);
			return;
		}
		Vec3 at = Vec3.atCenterOf(pos);
		Villager lighter = null;
		double best = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(LIGHTER_RANGE),
				v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !busy.contains(v))) {
			double d = v.distanceToSqr(at);
			if (d < best) {
				best = d;
				lighter = v;
			}
		}
		if (lighter == null) {
			return;
		}
		busy.add(lighter);
		if (best <= LIGHTER_REACH * LIGHTER_REACH) {
			lighter.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			lighter.getLookControl().setLookAt(at);
			relight(level, pos, state);
			level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		} else {
			lighter.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.5F, 1));
		}
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key).noCollision().instabreak()
			.sound(SoundType.WOOD).pushReaction(PushReaction.POPPED)));
	}
}
