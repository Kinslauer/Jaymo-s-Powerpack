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
		int sources = 0;
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			BlockState state = be.getBlockState();
			if (state.getValueOrElse(BlockStateProperties.LIT, false) && ++sources >= MAX_SOURCES) {
				break;
			}
		}
		float was = chunk.getAttachedOrElse(SOOT, 0.0F);
		if (sources == 0 && was <= 0.0F) {
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

	/** A few open columns: a crop on top wilts back a stage. */
	private static void wilt(ServerLevel level, LevelChunk chunk, RandomSource random) {
		ChunkPos pos = chunk.getPos();
		for (int i = 0; i < WILT_TRIES; i++) {
			int x = pos.getMinBlockX() + random.nextInt(16), z = pos.getMinBlockZ() + random.nextInt(16);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
			BlockState state = level.getBlockState(top);
			if (state.getBlock() instanceof CropBlock crop && crop.getAge(state) > 0) {
				level.setBlockAndUpdate(top, crop.getStateForAge(crop.getAge(state) - 1));
				level.sendParticles(ParticleTypes.ASH, x + 0.5, top.getY() + 0.6, z + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
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
