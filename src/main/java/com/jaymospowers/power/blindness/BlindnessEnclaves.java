package com.jaymospowers.power.blindness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "Villagers who have done this start to get shunned by others, meaning you will find unseeing
 * enclaves in the wild, in random caves or elsewhere, that they can be safe and practice their belief." Down in the dark
 * of a cave, a survival player may come on an enclave of the Unseeing ({@link Unseeing}): {@link #MIN_PEOPLE} to
 * {@link #MAX_PEOPLE} of them, the eldest blindfolded, the rest blindfolded or gouged (some already zealous), round a
 * small fire with candles about it, a few rough beds of wool. Found {@link #NEAR}-{@link #FAR} blocks off, out of sight
 * of the sky; then they live there (they keep within {@link BlindnessService#HOME_RANGE} of it).
 * <p>A check every {@link #CHECK_EVERY} ticks for a player in a cave (no sky, under y {@link #CAVE_BELOW}), at
 * {@link #CHANCE}; at most once every {@link #GAP_DAYS} days each ({@link #LAST}, not saved), and never with another
 * enclave within {@link #APART} blocks.
 */
public final class BlindnessEnclaves {
	static final int CHECK_EVERY = 200;
	static final float CHANCE = 0.01F;
	static final int GAP_DAYS = 5;
	static final int CAVE_BELOW = 50;
	static final int NEAR = 16, FAR = 30, APART = 96;
	static final int MIN_PEOPLE = 4, MAX_PEOPLE = 7;
	/** Of the ones that aren't blindfolded, the share already gone zealous. */
	static final float ZEALOUS = 0.35F;
	static final String TAG = "jaymospowers_unseeing_enclave";

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private BlindnessEnclaves() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BlindnessEnclaves::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition();
			if (player.isCreative() || player.isSpectator() || level.dimension() != Level.OVERWORLD || pos.getY() > CAVE_BELOW
				|| level.canSeeSky(pos) || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			long day = level.getOverworldClockTime() / 24000L;
			Long last = LAST.get(player.getUUID());
			if (last != null && day - last < GAP_DAYS) {
				continue;
			}
			if (!level.getEntitiesOfClass(Unseeing.class, new AABB(pos).inflate(APART), u -> u.entityTags().contains(TAG)).isEmpty()) {
				continue;
			}
			if (found(level, player)) {
				LAST.put(player.getUUID(), day);
			}
		}
	}

	/** Lays out an enclave in the cave near {@code player}. False if there's no room for one. */
	static boolean found(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 24; tries++) {
			int dx = (random.nextBoolean() ? 1 : -1) * (NEAR + random.nextInt(FAR - NEAR + 1));
			int dz = random.nextInt(2 * FAR + 1) - FAR;
			if (random.nextBoolean()) {
				int t = dx;
				dx = dz;
				dz = t;
			}
			BlockPos centre = floor(level, player.blockPosition().offset(dx, 0, dz));
			if (centre == null) {
				continue;
			}
			List<BlockPos> spots = new ArrayList<>();
			for (BlockPos at : BlockPos.betweenClosed(centre.offset(-4, -1, -4), centre.offset(4, 1, 4))) {
				if (at.distManhattan(centre) >= 2 && standable(level, at)) {
					spots.add(at.immutable());
				}
			}
			if (spots.size() < MIN_PEOPLE + 4) {
				continue;
			}
			java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
			level.setBlock(centre, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false), 3);
			int people = MIN_PEOPLE + random.nextInt(MAX_PEOPLE - MIN_PEOPLE + 1);
			int used = 0;
			for (int i = 0; i < people && used < spots.size(); i++, used++) {
				Unseeing.Look look = i == 0 || random.nextBoolean() ? Unseeing.Look.BLINDFOLD
					: random.nextFloat() < ZEALOUS ? Unseeing.Look.ZEALOUS : Unseeing.Look.GOUGED;
				Unseeing one = BlindnessService.place(level, spots.get(used), centre, look);
				if (one != null) {
					one.setElder(i == 0);
					one.addTag(TAG);
				}
			}
			// candles and a few rough beds of wool round the fire
			for (int i = 0; i < 4 && used < spots.size(); i++, used++) {
				level.setBlock(spots.get(used), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3))
					.setValue(CandleBlock.LIT, true), 3);
			}
			for (int i = 0; i < 3 && used < spots.size(); i++, used++) {
				level.setBlock(spots.get(used), Blocks.CARPET.pick(random.nextBoolean() ? net.minecraft.world.item.DyeColor.GRAY : net.minecraft.world.item.DyeColor.BLACK).defaultBlockState(), 3);
			}
			return true;
		}
		return false;
	}

	/** The cave floor in the column at {@code near} (within 6 up or down): a standable spot out of sight of the sky. */
	private static @Nullable BlockPos floor(ServerLevel level, BlockPos near) {
		for (int dy = 0; dy <= 6; dy++) {
			for (int sign : new int[] {-1, 1}) {
				BlockPos at = near.above(dy * sign);
				if (standable(level, at) && !level.canSeeSky(at) && level.getBrightness(LightLayer.SKY, at) == 0) {
					return at;
				}
			}
		}
		return null;
	}

	/** Solid ground under it, two blocks of air (no water) to stand in. */
	private static boolean standable(ServerLevel level, BlockPos at) {
		BlockState below = level.getBlockState(at.below());
		return below.isFaceSturdy(level, at.below(), Direction.UP) && below.getFluidState().isEmpty()
			&& level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir();
	}
}
