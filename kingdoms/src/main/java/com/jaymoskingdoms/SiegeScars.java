package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * (am-sieges, am-warchest, am-sappers) What a siege leaves in the world, kept in the world's books (war parties aren't
 * saved): the barricade across a keep's door ({@link WarChest#shutGate}), what shots and kegs broke about the seat
 * ({@link Sieges#broken}), and the silver in kegs its sappers still carry ({@link Sappers}). After a restart the siege
 * is gone: its kegs' worth goes back to their realm's treasury, and its barricade comes down and its breaches are
 * mended as each spot's land is next loaded. A barricade still up where the land wasn't loaded when a siege ended
 * comes down the same way.
 */
final class SiegeScars {
	/** How often (ticks) the spots left over are looked at, and the books written. */
	static final int EVERY = 100;

	/** The books: the barricade and breaches of sieges under way, those left over to put right, and kegs' worth carried by realm. */
	record Book(Map<String, BlockState> gates, Map<String, BlockState> breaches, Map<String, BlockState> leftGates,
		Map<String, BlockState> leftBreaches, Map<String, Long> kegs) {
		static final Codec<Map<String, BlockState>> SPOTS = Codec.unboundedMap(Codec.STRING, BlockState.CODEC);
		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
				SPOTS.optionalFieldOf("gates", Map.of()).forGetter(Book::gates),
				SPOTS.optionalFieldOf("breaches", Map.of()).forGetter(Book::breaches),
				SPOTS.optionalFieldOf("left_gates", Map.of()).forGetter(Book::leftGates),
				SPOTS.optionalFieldOf("left_breaches", Map.of()).forGetter(Book::leftBreaches),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("kegs", Map.of()).forGetter(Book::kegs))
			.apply(i, Book::new));
	}

	/** On the Overworld (Factions' level). */
	static final AttachmentType<Book> BOOK = AttachmentRegistry.create(JaymosKingdoms.id("siege_scars"),
		builder -> builder.persistent(Book.CODEC));

	private static final Map<Long, BlockState> GATES = new HashMap<>(), BREACHES = new HashMap<>(), LEFT_GATES = new HashMap<>(),
		LEFT_BREACHES = new HashMap<>();
	private static final Map<Integer, Long> KEGS = new HashMap<>();
	private static boolean dirty;

	private SiegeScars() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(SiegeScars::load);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % EVERY == 0) {
				putRight(level);
				flush(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(SiegeScars::flush);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GATES.clear();
			BREACHES.clear();
			LEFT_GATES.clear();
			LEFT_BREACHES.clear();
			KEGS.clear();
			dirty = false;
		});
	}

	/** The world's up: whatever sieges it remembers are over; their kegs' worth is refunded and their scars are to be put right. */
	private static void load(MinecraftServer server) {
		Book book = Factions.level(server).getAttached(BOOK);
		if (book == null) {
			return;
		}
		read(book.leftGates(), LEFT_GATES);
		read(book.leftBreaches(), LEFT_BREACHES);
		read(book.gates(), LEFT_GATES);
		read(book.breaches(), LEFT_BREACHES);
		for (Map.Entry<String, Long> e : book.kegs().entrySet()) {
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			long silver = e.getValue();
			if (silver > 0 && Factions.get(server, id) != null) {
				Factions.update(server, id, f -> f.withTreasury(f.treasury() + silver));
			}
		}
		dirty = true;
		flush(server);
	}

	private static void read(Map<String, BlockState> from, Map<Long, BlockState> into) {
		for (Map.Entry<String, BlockState> e : from.entrySet()) {
			try {
				into.putIfAbsent(Long.parseLong(e.getKey()), e.getValue());
			} catch (NumberFormatException ex) {
				// (a bad line in the books: skipped)
			}
		}
	}

	private static Map<String, BlockState> write(Map<Long, BlockState> from) {
		Map<String, BlockState> out = new HashMap<>();
		from.forEach((k, v) -> out.put(Long.toString(k), v));
		return out;
	}

	private static void flush(MinecraftServer server) {
		if (!dirty) {
			return;
		}
		dirty = false;
		Map<String, Long> kegs = new HashMap<>();
		KEGS.forEach((k, v) -> {
			if (v > 0) {
				kegs.put(Integer.toString(k), v);
			}
		});
		Factions.level(server).setAttached(BOOK, new Book(write(GATES), write(BREACHES), write(LEFT_GATES), write(LEFT_BREACHES), Map.copyOf(kegs)));
	}

	/** The spots left over whose land is loaded now: the barricade down, the breach mended (where nothing's in the way). */
	private static void putRight(ServerLevel level) {
		for (Iterator<Map.Entry<Long, BlockState>> it = LEFT_GATES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Long, BlockState> e = it.next();
			BlockPos pos = BlockPos.of(e.getKey());
			if (!Loaded.made(level, pos)) {
				continue;
			}
			it.remove();
			dirty = true;
			if (barricade(level.getBlockState(pos))) {
				level.setBlock(pos, e.getValue(), Block.UPDATE_ALL);
			}
		}
		for (Iterator<Map.Entry<Long, BlockState>> it = LEFT_BREACHES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Long, BlockState> e = it.next();
			BlockPos pos = BlockPos.of(e.getKey());
			if (!Loaded.made(level, pos)) {
				continue;
			}
			it.remove();
			dirty = true;
			mend(level, pos, e.getValue());
		}
	}

	/** A barricade's block (timber, or the iron gate). */
	static boolean barricade(BlockState state) {
		return state.is(Blocks.SPRUCE_FENCE) || state.is(Blocks.IRON_BARS);
	}

	/** {@code state} put back at {@code pos} if the spot's open and nobody stands in it. True if it was. */
	static boolean mend(ServerLevel level, BlockPos pos, BlockState state) {
		if (!level.getBlockState(pos).canBeReplaced() || !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		return true;
	}

	// ---- the books kept --------------------------------------------------------------------------------------------

	/** A barricade block set at {@code pos} over {@code was}. */
	static void gate(BlockPos pos, BlockState was) {
		GATES.put(pos.asLong(), was);
		dirty = true;
	}

	/** The barricade at {@code pos} taken down (or to be, when its land loads: {@code later}). */
	static void gateDown(BlockPos pos, BlockState was, boolean later) {
		GATES.remove(pos.asLong());
		if (later) {
			LEFT_GATES.put(pos.asLong(), was);
		}
		dirty = true;
	}

	/** {@code was} broken at {@code pos} by a siege. */
	static void breach(BlockPos pos, BlockState was) {
		BREACHES.put(pos.asLong(), was);
		dirty = true;
	}

	/** The breach at {@code pos} mended or let be (or to be mended, when its land loads: {@code later}). */
	static void breachDone(BlockPos pos, BlockState was, boolean later) {
		BREACHES.remove(pos.asLong());
		if (later) {
			LEFT_BREACHES.put(pos.asLong(), was);
		}
		dirty = true;
	}

	/** {@code faction}'s sappers carry {@code silver} more (less, if negative) in kegs. */
	static void kegs(int faction, long silver) {
		KEGS.put(faction, Math.max(0L, KEGS.getOrDefault(faction, 0L) + silver));
		dirty = true;
	}
}
