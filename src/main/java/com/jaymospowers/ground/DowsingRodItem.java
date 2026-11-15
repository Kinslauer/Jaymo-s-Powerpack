package com.jaymospowers.ground;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

/**
 * The dowsing rod (Andy's GT New Horizons picks, patch 7, or-dowse): a forked hazel rod with a pocket bound into its fork
 * for an ore sample. Right-click a raw ore, a crushed ore or a small ore onto it in the inventory (or the rod onto the
 * sample) to put it in; right-click the rod with an empty hand to take it out. Held, it ticks, faster and a little higher
 * the nearer the nearest block of the sample's metal is ({@link Ores.Metal#ores}: a great vein's ore or a lone small ore),
 * up to {@link #REACH} blocks; beyond that it is still. Sound only, heard only by the holder.
 * <p>
 * It never scans every tick: every {@link #SEARCH_EVERY} ticks it lists the loaded chunk sections round the holder that
 * could hold that ore (a section's palette says whether it may have any), then reads them nearest first, at most
 * {@link #SECTIONS_PER_TICK} a tick (so a few thousand block checks a tick at worst, however full the palettes are), and
 * stops once no section left could be nearer than what it found; until then it keeps the last answer. So it hears old
 * veins, Kingdoms' grown ones and small ores alike.
 */
public class DowsingRodItem extends Item {
	/** How far it hears (blocks), and how often (ticks) it looks again. */
	public static final int REACH = 40;
	static final int SEARCH_EVERY = 40;
	/** Chunk sections (4096 blocks each) a search reads in one tick at most, nearest first. */
	static final int SECTIONS_PER_TICK = 2;
	/** Ticks between ticks at the edge of its reach, and right on the ore. */
	static final int SLOWEST = 34, FASTEST = 3;
	/** How sharply the rate climbs with nearness (above 1: slow until close). */
	static final double CURVE = 1.5;
	/** The tick's pitch at the edge and on the ore, and its volume. */
	static final float LOW_PITCH = 0.85F, HIGH_PITCH = 1.5F, VOLUME = 0.45F;

	/** What each holder's rod last found: the metal, when it looked, and how far the ore was (blocks). Not saved. */
	private record Heard(Ores.Metal metal, long at, double distance) {
	}

	private static final Map<UUID, Heard> HEARD = new HashMap<>();
	/** The game time each holder's next tick is due. Not saved. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	/** Each holder's search under way, read a few sections a tick. Not saved. */
	private static final Map<UUID, Search> SEARCHES = new HashMap<>();

	public DowsingRodItem(Properties properties) {
		super(properties);
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player, server.getTickCount());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HEARD.remove(handler.player.getUUID());
			NEXT.remove(handler.player.getUUID());
			SEARCHES.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HEARD.clear();
			NEXT.clear();
			SEARCHES.clear();
		});
	}

	/** The metal of the sample in {@code rod}, or null if it holds none. */
	static Ores.@Nullable Metal sampleOf(ItemStack rod) {
		ItemStack sample = sample(rod);
		return sample.isEmpty() ? null : Ores.Metal.of(sample);
	}

	private static ItemStack sample(ItemStack rod) {
		String id = rod.get(Ores.DOWSING_SAMPLE);
		Identifier key = id == null ? null : Identifier.tryParse(id);
		return key == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	private static void tick(ServerPlayer player, long now) {
		ItemStack rod = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!rod.is(Ores.DOWSING_ROD)) {
			rod = player.getItemInHand(InteractionHand.OFF_HAND);
		}
		Ores.Metal metal = rod.is(Ores.DOWSING_ROD) && player.isAlive() && !player.isSpectator() ? sampleOf(rod) : null;
		UUID id = player.getUUID();
		if (metal == null) {
			HEARD.remove(id);
			SEARCHES.remove(id);
			return;
		}
		Heard heard = HEARD.get(id);
		Search search = SEARCHES.get(id);
		if (search != null && search.metal != metal) {
			search = null;
		}
		if (search == null && (heard == null || heard.metal() != metal || now - heard.at() >= SEARCH_EVERY)) {
			search = new Search(player.level(), player.blockPosition(), metal);
		}
		if (search != null) {
			if (search.step()) {
				heard = new Heard(metal, now, search.distance());
				HEARD.put(id, heard);
				SEARCHES.remove(id);
			} else {
				SEARCHES.put(id, search);
			}
		}
		if (heard == null || heard.metal() != metal || heard.distance() > REACH || now < NEXT.getOrDefault(id, 0L)) {
			return;
		}
		double near = 1.0 - Mth.clamp(heard.distance() / REACH, 0.0, 1.0);
		int gap = (int) Math.round(Mth.lerp(Math.pow(near, 1.0 / CURVE), SLOWEST, FASTEST));
		NEXT.put(id, now + Math.max(FASTEST, gap));
		float pitch = Mth.lerp((float) near, LOW_PITCH, HIGH_PITCH);
		player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, player.getX(), player.getY(),
			player.getZ(), VOLUME, pitch, player.getRandom().nextLong()));
	}

	/** A section that may hold the ore: its chunk, its index, and the least distance (squared) from the holder to it. */
	private record Candidate(LevelChunk chunk, int index, double least) {
	}

	/**
	 * A search for the nearest block of {@code metal}'s ore within {@link #REACH} of {@code from}: the sections that may
	 * hold it, listed once (palette checks only), then read a few a tick ({@link #step}), nearest first.
	 */
	private static final class Search {
		final Ores.Metal metal;
		private final BlockPos from;
		private final List<Candidate> candidates = new ArrayList<>();
		private int next;
		private double best = (double) (REACH + 1) * (REACH + 1);

		Search(ServerLevel level, BlockPos from, Ores.Metal metal) {
			this.metal = metal;
			this.from = from;
			TagKey<Block> ores = metal.ores;
			int reach = Mth.ceil(REACH / 16.0);
			int fx = SectionPos.blockToSectionCoord(from.getX()), fy = SectionPos.blockToSectionCoord(from.getY()),
				fz = SectionPos.blockToSectionCoord(from.getZ());
			for (int cx = fx - reach; cx <= fx + reach; cx++) {
				for (int cz = fz - reach; cz <= fz + reach; cz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
					if (chunk == null) {
						continue;
					}
					LevelChunkSection[] sections = chunk.getSections();
					for (int sy = fy - reach; sy <= fy + reach; sy++) {
						int index = chunk.getSectionIndexFromSectionY(sy);
						if (index < 0 || index >= sections.length) {
							continue;
						}
						LevelChunkSection section = sections[index];
						if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ores))) {
							continue;
						}
						double least = gap(from.getX(), cx << 4) + gap(from.getY(), sy << 4) + gap(from.getZ(), cz << 4);
						if (least < best) {
							candidates.add(new Candidate(chunk, index, least));
						}
					}
				}
			}
			candidates.sort((a, b) -> Double.compare(a.least(), b.least()));
		}

		/** Reads up to {@link #SECTIONS_PER_TICK} more sections; true once the answer is sure. */
		boolean step() {
			for (int read = 0; read < SECTIONS_PER_TICK; read++) {
				if (next >= candidates.size() || candidates.get(next).least() >= best) {
					return true;
				}
				Candidate c = candidates.get(next++);
				LevelChunkSection section = c.chunk().getSections()[c.index()];
				int x0 = c.chunk().getPos().getMinBlockX(), y0 = SectionPos.sectionToBlockCoord(c.chunk().getSectionYFromSectionIndex(c.index())),
					z0 = c.chunk().getPos().getMinBlockZ();
				for (int x = 0; x < 16; x++) {
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							if (section.getBlockState(x, y, z).is(metal.ores)) {
								double dx = x0 + x - from.getX(), dy = y0 + y - from.getY(), dz = z0 + z - from.getZ();
								best = Math.min(best, dx * dx + dy * dy + dz * dz);
							}
						}
					}
				}
			}
			return next >= candidates.size() || candidates.get(next).least() >= best;
		}

		/** How far (blocks) the nearest block found is, or more than {@link #REACH} if none. */
		double distance() {
			return Math.sqrt(best);
		}
	}

	/** The squared distance along one axis from {@code at} to the 16 blocks starting at {@code start} (0 inside). */
	private static double gap(int at, int start) {
		int d = at < start ? start - at : at > start + 15 ? at - (start + 15) : 0;
		return (double) d * d;
	}

	// ---- putting a sample in and taking it out (in the inventory, like a bundle) ----

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
			return false;
		}
		if (!other.isEmpty() && Ores.Metal.of(other) != null && !self.has(Ores.DOWSING_SAMPLE)) {
			self.set(Ores.DOWSING_SAMPLE, BuiltInRegistries.ITEM.getKey(other.getItem()).toString());
			other.shrink(1);
			player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
			return true;
		}
		if (other.isEmpty() && self.has(Ores.DOWSING_SAMPLE)) {
			carried.set(sample(self));
			self.remove(Ores.DOWSING_SAMPLE);
			player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.0F);
			return true;
		}
		return false;
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY) {
			return false;
		}
		ItemStack other = slot.getItem();
		if (!other.isEmpty() && Ores.Metal.of(other) != null && !self.has(Ores.DOWSING_SAMPLE)) {
			ItemStack taken = slot.safeTake(1, 1, player);
			if (!taken.isEmpty()) {
				self.set(Ores.DOWSING_SAMPLE, BuiltInRegistries.ITEM.getKey(taken.getItem()).toString());
				player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1.0F);
			}
			return true;
		}
		if (other.isEmpty() && self.has(Ores.DOWSING_SAMPLE)) {
			ItemStack left = slot.safeInsert(sample(self));
			if (left.isEmpty()) {
				self.remove(Ores.DOWSING_SAMPLE);
				player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.0F);
			}
			return true;
		}
		return false;
	}
}
