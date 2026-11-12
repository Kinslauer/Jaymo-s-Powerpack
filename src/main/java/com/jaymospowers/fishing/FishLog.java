package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Track D: personal bests and the fisher's journal (Reel to Fish and Legendary Fishing's ideas, our own code). Every fish
 * an angler lands is written down: how many of each kind, how many went back, the heaviest (the personal best: beating it
 * plays a fanfare), the bait it took, where and on which day, and the hours each kind bites for them. The record is kept
 * through death (a Reincarnation wipes it with everything else). Read it in the {@link #JOURNAL} (no command: Andy's rule).
 */
public final class FishLog {
	/** One kind of fish in the journal: counts, the best's weight, day, bait and water, and catches by time of day. */
	public record Entry(int caught, int released, float best, int bestDay, String bestBait, String bestWater, List<Integer> hours) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("caught").forGetter(Entry::caught),
			Codec.INT.optionalFieldOf("released", 0).forGetter(Entry::released),
			Codec.FLOAT.optionalFieldOf("best", 0.0F).forGetter(Entry::best),
			Codec.INT.optionalFieldOf("best_day", 0).forGetter(Entry::bestDay),
			Codec.STRING.optionalFieldOf("best_bait", "").forGetter(Entry::bestBait),
			Codec.STRING.optionalFieldOf("best_water", "").forGetter(Entry::bestWater),
			Codec.INT.listOf().optionalFieldOf("hours", List.of(0, 0, 0, 0)).forGetter(Entry::hours)).apply(i, Entry::new));

		static final Entry NONE = new Entry(0, 0, 0.0F, 0, "", "", List.of(0, 0, 0, 0));
	}

	/** An angler's whole journal: every kind, the legends they've landed, and their contests. */
	public record Log(Map<String, Entry> kinds, List<String> legends, int contests, int won) {
		public static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("kinds", Map.of()).forGetter(Log::kinds),
			Codec.STRING.listOf().optionalFieldOf("legends", List.of()).forGetter(Log::legends),
			Codec.INT.optionalFieldOf("contests", 0).forGetter(Log::contests),
			Codec.INT.optionalFieldOf("won", 0).forGetter(Log::won)).apply(i, Log::new));

		public static final Log EMPTY = new Log(Map.of(), List.of(), 0, 0);

		public Entry entry(Species sp) {
			return kinds.getOrDefault(sp.id, Entry.NONE);
		}
	}

	public static final AttachmentType<Log> LOG = AttachmentRegistry.create(JaymosPowers.id("fish_log"),
		builder -> builder.persistent(Log.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(Log.CODEC), AttachmentSyncPredicate.targetOnly()));

	public static Item JOURNAL;

	private FishLog() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("fishers_journal"));
		JOURNAL = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(JOURNAL));
	}

	public static Log of(Player player) {
		Log log = player.getAttached(LOG);
		return log == null ? Log.EMPTY : log;
	}

	/** Which quarter of the day it is: 0 dawn, 1 day, 2 dusk, 3 night. */
	static int quarter(ServerLevel level) {
		long t = level.getOverworldClockTime() % 24000L;
		return t >= 23000L || t < 1000L ? 0 : t < 11500L ? 1 : t < 13500L ? 2 : 3;
	}

	/**
	 * Write {@code sp} landed at {@code kg} into {@code player}'s journal. True if it beat their best of that kind (the
	 * first of a kind is written down quietly).
	 */
	static boolean landed(ServerPlayer player, Species sp, float kg, @Nullable Item bait, ServerLevel level, BlockPos pos,
		String legend) {
		Log log = of(player);
		Entry was = log.entry(sp);
		List<Integer> hours = new ArrayList<>(was.hours.size() == 4 ? was.hours : List.of(0, 0, 0, 0));
		int q = quarter(level);
		hours.set(q, hours.get(q) + 1);
		boolean best = kg > was.best;
		int day = (int) (level.getOverworldClockTime() / 24000L);
		String baitId = bait == null ? "" : BuiltInRegistries.ITEM.getKey(bait).toString();
		String water = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
		Entry now = best ? new Entry(was.caught + 1, was.released, kg, day, baitId, water, hours)
			: new Entry(was.caught + 1, was.released, was.best, was.bestDay, was.bestBait, was.bestWater, hours);
		Map<String, Entry> kinds = new HashMap<>(log.kinds);
		kinds.put(sp.id, now);
		List<String> legends = log.legends;
		if (!legend.isEmpty() && !legends.contains(legend)) {
			legends = new ArrayList<>(legends);
			legends.add(legend);
		}
		player.setAttached(LOG, new Log(kinds, legends, log.contests, log.won));
		if (was.caught == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.fishing.journal_new", Component.translatable(sp.nameKey()))
				.withStyle(ChatFormatting.DARK_AQUA), true);
			return false;
		}
		return best;
	}

	/** {@code player} put a fish of {@code sp} back. */
	static void released(ServerPlayer player, Species sp) {
		Log log = of(player);
		Entry was = log.entry(sp);
		Map<String, Entry> kinds = new HashMap<>(log.kinds);
		kinds.put(sp.id, new Entry(was.caught, was.released + 1, was.best, was.bestDay, was.bestBait, was.bestWater, was.hours));
		player.setAttached(LOG, new Log(kinds, log.legends, log.contests, log.won));
	}

	/** {@code player} fished a contest, and maybe won it. */
	static void contest(ServerPlayer player, boolean won) {
		Log log = of(player);
		player.setAttached(LOG, new Log(log.kinds, log.legends, log.contests + 1, log.won + (won ? 1 : 0)));
	}
}
