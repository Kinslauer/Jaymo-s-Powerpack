package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Pollution (Andy's TFC Hardrock picks): soot in the air. Every lit furnace, smoker, blast furnace, campfire or other lit
 * block with a block entity (a smeltery, a forge) adds {@link #PER_SOURCE} soot to its chunk each check; soot thins by
 * {@link #CLEAR} (three times as fast in rain) and drifts into the neighbouring chunks ({@link #SPREAD} of the
 * difference). Kept per chunk ({@link #SOOT}, 0 to {@link #MAX}), checked every {@link #CHECK_TICKS} round each player.
 * <ul>
 * <li>From {@link #ASHY}: ash drifts in the air, and rain there is sooty, no longer clean to drink ({@link #dirtyRain}).</li>
 * <li>From {@link #COUGH}: you cough now and then (a little hunger, a line on screen), as a smoky town would make you.</li>
 * <li>From {@link #WILT}: crops in the open wilt back a stage, a few columns a check.</li>
 * </ul>
 * <p>
 * Chimneys carry it further (our own take on the idea that a tall flue sends smoke far; none of Advanced Chimneys is used): a
 * lit source with a flue, a shaft of air walled in on all four sides above it ({@link #flue}), puts only part of its soot
 * into its own chunk ({@link #FLUE_LOCAL}), and sends the rest {@link #FLUE_REACH} chunks downwind plus one for every
 * {@link #FLUE_PER_CHUNK} blocks of flue, since the higher the smoke leaves, the further the wind takes it. The wind is the
 * day's: it turns a quarter each day and swings to the rain's side when it rains. A town of short chimneys keeps its
 * smog at home; a tall stack gives it to the next valley.
 * <p>
 * It starts gentle: one furnace never reaches the ashy line; a town of forges does.
 */
public final class Soot {
	public static final float MAX = 100.0F;
	/** Soot each lit source adds a check, and the most sources a chunk counts. */
	static final float PER_SOURCE = 0.6F;
	static final int MAX_SOURCES = 16;
	/** Soot that clears each check (times {@link #RAIN_CLEAR} in rain) and the share of a difference that drifts next door. */
	static final float CLEAR = 1.0F, RAIN_CLEAR = 3.0F, SPREAD = 0.05F;
	/** Where ash shows and rain turns dirty, where you cough, where crops wilt. */
	static final float ASHY = 25.0F, COUGH = 40.0F, WILT = 60.0F;
	/** How often (ticks), how many chunks round each player, the cough chance at the most soot, columns tried for wilting. */
	static final int CHECK_TICKS = 200, RADIUS = 2, WILT_TRIES = 4;
	static final float COUGH_CHANCE = 0.6F, COUGH_EXHAUSTION = 0.6F;
	/** A flue: the share of its soot a chimneyed source keeps at home at the most, the chunks it carries the rest downwind to start with, and the blocks of flue for each chunk more; the tallest flue counted. */
	static final float FLUE_LOCAL = 0.35F;
	static final int FLUE_REACH = 1, FLUE_PER_CHUNK = 6, FLUE_MOST = 24;

	/** The soot in a chunk. Saved with the chunk. */
	public static final AttachmentType<Float> SOOT = AttachmentRegistry.create(JaymosKitchen.id("soot"), b -> b.persistent(Codec.FLOAT));

	private Soot() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Soot::tick);
	}

	/** The soot where {@code player} stands, 0 to {@link #MAX}. */
	public static float at(Player player) {
		LevelChunk chunk = player.level().getChunkAt(player.blockPosition());
		return chunk.getAttachedOrElse(SOOT, 0.0F);
	}

	/** Rain here is sooty: not clean to drink. */
	public static boolean dirtyRain(Player player) {
		return at(player) >= ASHY;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<Long> done = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				ChunkPos centre = ChunkPos.containing(player.blockPosition());
				for (int dx = -RADIUS; dx <= RADIUS; dx++) {
					for (int dz = -RADIUS; dz <= RADIUS; dz++) {
						int cx = centre.x() + dx, cz = centre.z() + dz;
						if (done.add(ChunkPos.pack(cx, cz))) {
							LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
							if (chunk != null) {
								update(level, chunk);
							}
						}
					}
				}
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator()) {
					breathe(level, player);
				}
			}
		}
	}

	private static void update(ServerLevel level, LevelChunk chunk) {
		float sources = 0.0F;
		int counted = 0, tallest = 0;
		float carried = 0.0F;
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			BlockState state = be.getBlockState();
			if (state.getValueOrElse(BlockStateProperties.LIT, false)) {
				int flue = flue(level, be.getBlockPos());
				if (flue > 0) {
					// the chimney keeps some of it at home and gives the rest to the wind
					sources += FLUE_LOCAL;
					carried += 1.0F - FLUE_LOCAL;
					tallest = Math.max(tallest, flue);
				} else {
					sources += 1.0F;
				}
				if (++counted >= MAX_SOURCES) {
					break;
				}
			}
		}
		if (carried > 0.0F) {
			carry(level, chunk.getPos(), carried * PER_SOURCE, tallest);
		}
		float was = chunk.getAttachedOrElse(SOOT, 0.0F);
		if (sources <= 0.0F && was <= 0.0F) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		BlockPos middle = new BlockPos(pos.getMiddleBlockX(), level.getSeaLevel() + 8, pos.getMiddleBlockZ());
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, middle);
		float soot = was + sources * PER_SOURCE - CLEAR * (level.isRainingAt(top) ? RAIN_CLEAR : 1.0F);
		// drift into the neighbours that have less
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] side : sides) {
			LevelChunk next = level.getChunkSource().getChunkNow(pos.x() + side[0], pos.z() + side[1]);
			if (next == null) {
				continue;
			}
			float there = next.getAttachedOrElse(SOOT, 0.0F);
			if (soot > there) {
				float move = (soot - there) * SPREAD;
				soot -= move;
				set(next, there + move);
			}
		}
		set(chunk, soot);
		if (soot >= WILT) {
			wilt(level, chunk, level.getRandom());
		}
	}

	/**
	 * How tall the flue over a lit source at {@code pos} is: the blocks above it that are air with a wall on all four sides
	 * (a shaft), up to {@link #FLUE_MOST}; 0 if the air over it is open, or too short a shaft (under 2) to count.
	 */
	static int flue(ServerLevel level, BlockPos pos) {
		int height = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = 1; y <= FLUE_MOST; y++) {
			at.set(pos.getX(), pos.getY() + y, pos.getZ());
			if (!level.getBlockState(at).isAir() && !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				break;
			}
			boolean walled = true;
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos side = at.relative(d);
				walled &= level.getBlockState(side).isFaceSturdy(level, side, d.getOpposite());
			}
			if (!walled) {
				break;
			}
			height++;
		}
		return height >= 2 ? height : 0;
	}

	/** The day's wind: a way along the ground, turning a quarter a day, and to the side the rain comes from when it rains. */
	static net.minecraft.core.Direction wind(ServerLevel level) {
		long day = level.getOverworldClockTime() / 24000L;
		net.minecraft.core.Direction d = net.minecraft.core.Direction.from2DDataValue((int) Math.floorMod(day, 4L));
		return level.isRaining() ? d.getClockWise() : d;
	}

	/** {@code amount} of soot sent downwind of {@code from}, as far as a flue {@code flue} blocks tall reaches. */
	private static void carry(ServerLevel level, ChunkPos from, float amount, int flue) {
		net.minecraft.core.Direction wind = wind(level);
		int far = FLUE_REACH + flue / FLUE_PER_CHUNK;
		LevelChunk target = level.getChunkSource().getChunkNow(from.x() + wind.getStepX() * far, from.z() + wind.getStepZ() * far);
		if (target != null) {
			set(target, target.getAttachedOrElse(SOOT, 0.0F) + amount);
		}
	}

	private static void set(LevelChunk chunk, float soot) {
		float s = Math.min(MAX, soot);
		if (s <= 0.05F) {
			if (chunk.getAttached(SOOT) == null) {
				return;
			}
			chunk.removeAttached(SOOT);
		} else {
			chunk.setAttached(SOOT, s);
		}
		chunk.markUnsaved();
	}

	/** A few open columns: a crop on top wilts back a stage, and a leaf at the top of a tree starts to wilt and fall. */
	private static void wilt(ServerLevel level, LevelChunk chunk, RandomSource random) {
		ChunkPos pos = chunk.getPos();
		for (int i = 0; i < WILT_TRIES; i++) {
			int x = pos.getMinBlockX() + random.nextInt(16), z = pos.getMinBlockZ() + random.nextInt(16);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
			BlockState state = level.getBlockState(top);
			if (state.getBlock() instanceof CropBlock crop && crop.getAge(state) > 0) {
				level.setBlockAndUpdate(top, crop.getStateForAge(crop.getAge(state) - 1));
				level.sendParticles(ParticleTypes.ASH, x + 0.5, top.getY() + 0.6, z + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
			} else if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock && state.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
				// a wilting leaf: let go of the tree, and the game's own leaf decay takes it, dropping what it would
				level.setBlockAndUpdate(top, state.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)
					.setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 7));
				level.sendParticles(ParticleTypes.ASH, x + 0.5, top.getY() + 0.6, z + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
			}
		}
	}

	/** Ash in sooty air, and a cough where it's thick. */
	private static void breathe(ServerLevel level, ServerPlayer player) {
		float soot = at(player);
		if (soot < ASHY) {
			return;
		}
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(ParticleTypes.ASH, false, false,
			player.getX(), player.getEyeY(), player.getZ(), 4.0F, 2.0F, 4.0F, 0.0F, (int) (soot / 2)));
		if (soot < COUGH || player.isUnderWater() || player.isCreative()
			|| player.getRandom().nextFloat() >= COUGH_CHANCE * (soot - COUGH) / (MAX - COUGH) + 0.15F) {
			return;
		}
		player.causeFoodExhaustion(COUGH_EXHAUSTION);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.soot.cough"));
	}
}
