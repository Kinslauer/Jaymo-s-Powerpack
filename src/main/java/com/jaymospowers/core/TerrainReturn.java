package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Terrain return (God Wand's idea, hyisnoob, MIT: a terrain engine of recorded blocks that come back; none of its art or
 * sound is used, and the code here is our own, see {@code licenses/god-wand/LICENSE}): ground that a power tears out comes
 * back, a little at a time. The hooks ({@link #record}) are called just before a Gravity move or a meteor breaks a block, and
 * by {@code ServerExplosionReturnMixin} for a big blast a player throws. Each block recorded is the state it stood in. It is
 * put back after {@link #DELAY} ticks (the lowest first, so a wall is built up from the floor), never more than
 * {@link #PER_TICK} a tick over the server, only into air or something a block can replace, and only when nothing living
 * stands in the spot. A spot someone has built on since is left alone. Records are saved with the level, so a restart doesn't
 * lose them.
 * <ul>
 * <li>Only whole blocks are recorded (the ground, stone, logs, leaves): plants and doors grow back or are made again by hand.</li>
 * <li>Ores, anything with a block entity (chests, spawners) and anything unbreakable are never recorded: they drop what they
 * are, and coming back they would be a gift.</li>
 * <li>{@link #lend} is the same engine for a block taken for a short while (the Sylvan's wood path,
 * {@code HeritageMoves}); it is lent whether or not the server switch is on, and comes back once nothing stands in it.</li>
 * </ul>
 * The server switch is {@code terrain_return=true} (the default) in {@code config/jaymospowers-terrain.properties}, read at
 * server start: off, blasts and moves leave their craters for good, as before.
 */
public final class TerrainReturn {
	// ---- tuning ----
	/** Ticks before a broken block starts to come back, the spread added per block of height above the level's floor, and a random extra. */
	static final int DELAY = 20 * 90, PER_HEIGHT = 3, JITTER = 20 * 40;
	/** The most blocks put back in a tick, over the whole server. */
	static final int PER_TICK = 24;
	/** The wait before a block that couldn't come back (a creature in the way, the chunk away) is tried again. */
	static final int RECHECK = 40;
	/** The most blocks kept waiting in one level; a blast past this leaves the rest for good. */
	static final int MAX_RECORDS = 120_000;
	/** How often (ticks) the records are written to the level's saved data when they have changed. */
	static final int SAVE_EVERY = 20 * 30;
	/** A restored block makes a quiet sound and a glint once for this many, the glint's colour. */
	static final int SOUND_EVERY = 40;
	static final int GLINT = 0xB8E0A0;
	/** The file the switch is in, and its key. */
	static final String FILE = "jaymospowers-terrain.properties", SWITCH = "terrain_return";

	/** A block waiting to come back. */
	private record Held(BlockPos pos, BlockState state, long at) {
		static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Held::pos),
			BlockState.CODEC.fieldOf("state").forGetter(Held::state),
			Codec.LONG.fieldOf("at").forGetter(Held::at)).apply(i, Held::new));
	}

	/** On each level: the blocks waiting to come back. Saved. */
	private static final AttachmentType<List<Held>> SAVED = AttachmentRegistry.create(JaymosPowers.id("terrain_return"),
		builder -> builder.persistent(Held.CODEC.listOf()));

	/** One level's records, read from its saved data the first time they are needed. */
	private static final class Store {
		final PriorityQueue<Held> queue = new PriorityQueue<>((a, b) -> Long.compare(a.at(), b.at()));
		final Set<Long> spots = new HashSet<>();
		boolean dirty;
	}

	private static final Map<ResourceKey<Level>, Store> STORES = new HashMap<>();
	private static boolean enabled = true;
	private static int sounded;

	private TerrainReturn() {
	}

	public static void init() {
		load();
		ServerTickEvents.END_SERVER_TICK.register(TerrainReturn::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			STORES.clear();
		});
	}

	/** Whether blasts and powers record what they break (the server switch). */
	public static boolean enabled() {
		return enabled;
	}

	/**
	 * Records the block at {@code pos}, about to be broken by a power, so it comes back; nothing if the switch is off or it isn't
	 * ground. True when it was recorded: the caller then breaks it without drops, since the block grows back (Andy, 2026-11-25:
	 * ground that returns drops nothing, or it could be farmed).
	 */
	public static boolean record(ServerLevel level, BlockPos pos) {
		if (!enabled) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!worth(level, pos, state)) {
			return false;
		}
		return hold(level, pos, state, level.getGameTime() + DELAY + (long)(pos.getY() - level.getMinY()) * PER_HEIGHT + level.getRandom().nextInt(JITTER));
	}

	/**
	 * Takes the block at {@code pos} away for {@code ticks} ticks (and until nothing stands in its place): air now, put back as it
	 * was. No drops. False if there was nothing to lend.
	 */
	public static boolean lend(ServerLevel level, BlockPos pos, int ticks) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F || store(level).spots.contains(pos.asLong())) {
			return false;
		}
		hold(level, pos, state, level.getGameTime() + ticks);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return true;
	}

	private static boolean worth(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !state.is(BlockTags.ORES)
			&& state.getDestroySpeed(level, pos) >= 0.0F && state.isCollisionShapeFullBlock(level, pos);
	}

	/** False when it couldn't be kept (the level's records are full, or the spot is already waiting for a block). */
	private static boolean hold(ServerLevel level, BlockPos pos, BlockState state, long at) {
		Store store = store(level);
		if (store.queue.size() >= MAX_RECORDS || !store.spots.add(pos.asLong())) {
			return false;
		}
		store.queue.add(new Held(pos.immutable(), state, at));
		store.dirty = true;
		return true;
	}

	private static Store store(ServerLevel level) {
		return STORES.computeIfAbsent(level.dimension(), key -> {
			Store store = new Store();
			List<Held> saved = level.getAttached(SAVED);
			if (saved != null) {
				for (Held held : saved) {
					if (store.spots.add(held.pos().asLong())) {
						store.queue.add(held);
					}
				}
			}
			return store;
		});
	}

	private static void tick(MinecraftServer server) {
		int budget = PER_TICK;
		for (ServerLevel level : server.getAllLevels()) {
			if (!STORES.containsKey(level.dimension()) && level.getAttached(SAVED) == null) {
				continue;
			}
			Store store = store(level);
			if (store.queue.isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			List<Held> later = new ArrayList<>();
			while (budget > 0 && !store.queue.isEmpty() && store.queue.peek().at() <= now) {
				Held held = store.queue.poll();
				BlockPos pos = held.pos();
				if (!Loaded.made(level, pos)) {
					later.add(new Held(pos, held.state(), now + RECHECK));
					continue;
				}
				BlockState there = level.getBlockState(pos);
				if (!there.isAir() && !there.canBeReplaced()) {
					// built on since: it stays as it is
					store.spots.remove(pos.asLong());
					store.dirty = true;
					continue;
				}
				if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).deflate(0.05)).isEmpty()) {
					later.add(new Held(pos, held.state(), now + RECHECK));
					continue;
				}
				level.setBlock(pos, held.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				store.spots.remove(pos.asLong());
				store.dirty = true;
				budget--;
				if (sounded++ % SOUND_EVERY == 0) {
					level.playSound(null, pos, held.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 0.8F);
					MagicFx.burst(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, GLINT, 1.0F, 3, 0.3, 0.3, 0.3);
				}
			}
			store.queue.addAll(later);
		}
		if (server.getTickCount() % SAVE_EVERY == 0) {
			save(server);
		}
	}

	private static void save(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			Store store = STORES.get(level.dimension());
			if (store != null && store.dirty) {
				store.dirty = false;
				if (store.queue.isEmpty()) {
					level.removeAttached(SAVED);
				} else {
					level.setAttached(SAVED, List.copyOf(store.queue));
				}
			}
		}
	}

	// ---------------------------------------------------------------- the switch

	private static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		try {
			if (Files.exists(file)) {
				Properties properties = new Properties();
				try (Reader reader = Files.newBufferedReader(file)) {
					properties.load(reader);
				}
				enabled = !"false".equalsIgnoreCase(properties.getProperty(SWITCH, "true").trim());
			} else {
				enabled = true;
				Properties properties = new Properties();
				properties.setProperty(SWITCH, "true");
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file)) {
					properties.store(writer, "Jaymo's Powers, terrain return. terrain_return=false leaves the ground that Gravity, meteors and big power blasts tear out "
						+ "torn for good; true (the default) puts it back, a little at a time, after " + (DELAY / 20) + " seconds or more. "
						+ "Ores, chests and anything with an inventory are never put back.");
				}
			}
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't read {}", file, e);
		}
	}
}
