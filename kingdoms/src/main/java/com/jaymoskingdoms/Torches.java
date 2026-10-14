package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * (0.7.12) Torches burn down, the idea of Realistic Torches (remade here; none of its code or art). A torch a player
 * places burns for {@link #BURN_TICKS}, then goes out and is left a {@link #BURNT_TORCH}; rain on a torch open to the
 * sky puts it out sooner. A burnt torch gives no light and drops a stick; flint and steel, a fire charge or a lit
 * torch in hand lights it again for another full burn. Torches a settlement or world generation put there aren't
 * counted (only placed ones are, through mixin.BlockItemMixin), so villages and miners' tunnels stay lit: their folk
 * tend them.
 */
public final class Torches {
	/** How long a placed torch burns (ticks): two days. */
	static final long BURN_TICKS = 48000L;
	/** How often (ticks) the torches round each player are looked at. */
	static final int CHECK_EVERY = 100;
	/** A torch in the rain under open sky goes out with 1 chance in this many at each look (about 2 minutes). */
	static final int RAIN_DOUSE_CHANCE = 24;
	/** Chunks this far round a player are looked at; torches further off go out when someone comes near. */
	static final int CHUNK_RADIUS = 6;

	public static Block BURNT_TORCH, BURNT_WALL_TORCH;

	/** Per chunk: packed block position → the game time its torch goes out. Saved. */
	static final AttachmentType<Map<String, Long>> BURNING = AttachmentRegistry.create(JaymosKingdoms.id("burning_torches"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

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
				server.setBlockAndUpdate(pos, lit);
				lit(server, pos);
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

	/** A player just put {@code state} at {@code pos} (from the block item mixin): a torch starts to burn down. */
	public static void placed(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
			lit(level, pos);
		}
	}

	private static void lit(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Map<String, Long> burning = new HashMap<>(chunk.getAttachedOrElse(BURNING, Map.of()));
		burning.put(Long.toString(pos.asLong()), level.getGameTime() + BURN_TICKS);
		chunk.setAttached(BURNING, burning);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<ChunkPos> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				ChunkPos at = player.chunkPosition();
				for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
					for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
						ChunkPos pos = new ChunkPos(at.x() + x, at.z() + z);
						if (seen.add(pos)) {
							LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
							if (chunk != null && chunk.hasAttached(BURNING)) {
								look(level, chunk);
							}
						}
					}
				}
			}
		}
	}

	/** Torches in {@code chunk} that are gone are forgotten; burnt down or rained out, they go out. */
	private static void look(ServerLevel level, LevelChunk chunk) {
		Map<String, Long> burning = new HashMap<>(chunk.getAttachedOrElse(BURNING, Map.of()));
		long now = level.getGameTime();
		boolean changed = burning.entrySet().removeIf(entry -> {
			BlockPos pos = BlockPos.of(Long.parseLong(entry.getKey()));
			BlockState state = level.getBlockState(pos);
			if (!state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH)) {
				return true;
			}
			if (now >= entry.getValue() || level.isRainingAt(pos) && level.getRandom().nextInt(RAIN_DOUSE_CHANCE) == 0) {
				goOut(level, pos, state);
				return true;
			}
			return false;
		});
		if (changed) {
			if (burning.isEmpty()) {
				chunk.removeAttached(BURNING);
			} else {
				chunk.setAttached(BURNING, burning);
			}
		}
	}

	private static void goOut(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState burnt = state.is(Blocks.WALL_TORCH)
			? BURNT_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING))
			: BURNT_TORCH.defaultBlockState();
		level.setBlockAndUpdate(pos, burnt);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 6, 0.05, 0.05, 0.05, 0.01);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key).noCollision().instabreak()
			.sound(SoundType.WOOD).pushReaction(PushReaction.POPPED)));
	}
}
