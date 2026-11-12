package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Track D: the fishermen of a village (Reel to Fish and True Fishing's ideas, our own code). Talk about fishing with one
 * (Kingdoms' "Talk about fishing", or hand a fisherman a fish), and their screen has:
 * <ul>
 * <li>the market: they buy fish by weight ({@link Species#pricePerKg}), a live one for more ({@link #ALIVE_PRICE}), a
 * legend for far more; but a village only eats so much, and the more fish sold there in a day the less each fetches
 * ({@link #GLUT_KG}). Paid in the coin of the place with Kingdoms, else emeralds (the change kept toward the next).</li>
 * <li>the contest: every {@link #CONTEST_EVERY} days the village holds one from sunrise to noon. Enter before it ends,
 * fish within {@link #CONTEST_RANGE} blocks of the village, and your heaviest fish is your weight; the village's own
 * anglers fish too ({@link #RIVALS}). Come back after noon for the results: the heaviest wins
 * {@link #PRIZE_MILLI} and an {@link #RIBBON}. Caught and released counts: the fish are weighed on the bank.</li>
 * <li>what they say keeps the water here: its legend ({@link FishLegends#rumour}).</li>
 * </ul>
 */
public final class Fishermen {
	static final int CONTEST_EVERY = 4;
	static final long CONTEST_END = 6000L;
	static final double CONTEST_RANGE = 128.0;
	static final int RIVALS = 3;
	static final long PRIZE_MILLI = 8000L;
	static final float ALIVE_PRICE = 1.25F, LEGEND_PRICE = 3.0F;
	static final float GLUT_KG = 40.0F;
	/** A village (its fishermen's market and contest) is a square this many blocks across. */
	static final int VILLAGE_SHIFT = 6;

	public static Item RIBBON;

	/** One angler's place in a contest: name, heaviest fish (kg, species id), and whether they've seen the results. */
	public record Entry(String name, float kg, String species, boolean seen) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Entry::name),
			Codec.FLOAT.optionalFieldOf("kg", 0.0F).forGetter(Entry::kg),
			Codec.STRING.optionalFieldOf("species", "").forGetter(Entry::species),
			Codec.BOOL.optionalFieldOf("seen", false).forGetter(Entry::seen)).apply(i, Entry::new));
	}

	/** A village's contest on {@code day}, at {@code at}, by entrant uuid. */
	public record Contest(int day, BlockPos at, Map<String, Entry> entries) {
		public static final Codec<Contest> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("day").forGetter(Contest::day),
			BlockPos.CODEC.fieldOf("at").forGetter(Contest::at),
			Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("entries", Map.of()).forGetter(Contest::entries)).apply(i, Contest::new));
	}

	/** Every village's contests, by "village:day"; and the kg of fish sold in each village today. Kept on the overworld. */
	public static final AttachmentType<Map<String, Contest>> CONTESTS = AttachmentRegistry.create(JaymosPowers.id("fish_contests"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Contest.CODEC)));
	public static final AttachmentType<Map<String, Float>> SOLD = AttachmentRegistry.create(JaymosPowers.id("fish_sold"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.FLOAT)));
	/** Without Kingdoms: the change (thousandths of an emerald) owed toward the next emerald. Kept through death. */
	public static final AttachmentType<Long> CHANGE = AttachmentRegistry.create(JaymosPowers.id("fish_change"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** The village's own anglers in its contests. */
	private static final String[] RIVAL_NAMES = {"Old Hal", "Wenna", "Brannoc", "Tamsin", "Jory", "Kitto", "Morwen", "Pascoe", "Elowen",
		"Gryff", "Lowdy", "Silas"};

	private Fishermen() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("anglers_ribbon"));
		RIBBON = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> act(context.player(), payload));
		// without Kingdoms' talk menu: hand a fisherman a fish
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && is(entity) && !player.isShiftKeyDown() && sellable(player.getItemInHand(hand))) {
				if (player instanceof ServerPlayer sp) {
					open(sp, entity);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** {@code e} is a grown fisherman. */
	static boolean is(Entity e) {
		return e instanceof Villager v && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.FISHERMAN);
	}

	static String village(BlockPos pos) {
		return (pos.getX() >> VILLAGE_SHIFT) + "," + (pos.getZ() >> VILLAGE_SHIFT);
	}

	static long clock(ServerLevel level) {
		return level.getServer().overworld().getOverworldClockTime();
	}

	static int today(ServerLevel level) {
		return (int) (clock(level) / 24000L);
	}

	/** The next contest day from {@code day} on (today, if today is one). */
	static int nextContest(int day) {
		return day + Math.floorMod(-day, CONTEST_EVERY);
	}

	// ---- the market ----

	/** What a fisherman pays for {@code stack} (thousandths of an emerald), or 0 if they won't take it. */
	static long price(ServerLevel level, BlockPos at, ItemStack stack) {
		Species sp = speciesOf(stack);
		Float kg = kgOf(stack);
		if (sp == null || kg == null || sp.pricePerKg <= 0) {
			return 0L;
		}
		FishCatch.Info info = FishCatch.info(stack);
		float f = 1.0F;
		if (info != null && info.alive(level.getGameTime())) {
			f *= ALIVE_PRICE;
		}
		if (info != null && !info.legend().isEmpty()) {
			f *= LEGEND_PRICE;
		}
		float sold = level.getServer().overworld().getAttachedOrElse(SOLD, Map.of()).getOrDefault(village(at) + ":" + today(level), 0.0F);
		f /= 1.0F + sold / GLUT_KG;
		return Math.max(1L, Math.round(sp.pricePerKg * kg * stack.getCount() * f));
	}

	static @Nullable Species speciesOf(ItemStack stack) {
		FishCatch.Info info = FishCatch.info(stack);
		if (info != null) {
			return info.kind();
		}
		if (stack.is(Items.COD)) {
			return Species.COD;
		}
		if (stack.is(Items.SALMON)) {
			return Species.SALMON;
		}
		if (stack.is(Items.PUFFERFISH)) {
			return Species.PUFFERFISH;
		}
		if (stack.is(Items.TROPICAL_FISH)) {
			return Species.TROPICAL;
		}
		if (FishCatch.TUNA != null && stack.is(FishCatch.TUNA)) {
			return Species.TUNA;
		}
		return FishSpecies.ofItem(stack.getItem());
	}

	static @Nullable Float kgOf(ItemStack stack) {
		FishCatch.Info info = FishCatch.info(stack);
		return info != null ? Float.valueOf(info.kg()) : FishCatch.weight(stack);
	}

	/** {@code stack} is a fish a fisherman would buy (a whole one, weighed; not a shark's jaw). */
	static boolean sellable(ItemStack stack) {
		return !stack.isEmpty() && (FishSpecies.SHARK_JAW == null || !stack.is(FishSpecies.SHARK_JAW)) && speciesOf(stack) != null
			&& kgOf(stack) != null;
	}

	private static void sell(ServerPlayer player, Entity villager, int slot) {
		if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		ItemStack stack = player.getInventory().getItem(slot);
		if (!sellable(stack)) {
			return;
		}
		long milli = price(level, villager.blockPosition(), stack);
		Float kg = kgOf(stack);
		if (milli <= 0L || kg == null) {
			return;
		}
		float total = kg * stack.getCount();
		player.getInventory().setItem(slot, ItemStack.EMPTY);
		Map<String, Float> sold = new HashMap<>(level.getServer().overworld().getAttachedOrElse(SOLD, Map.of()));
		int day = today(level);
		sold.keySet().removeIf(k -> !k.endsWith(":" + day));
		sold.merge(village(villager.blockPosition()) + ":" + day, total, Float::sum);
		level.getServer().overworld().setAttached(SOLD, sold);
		Component paid = pay(player, villager.blockPosition(), milli);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.sold", paid).withStyle(ChatFormatting.GREEN));
	}

	/** Pay {@code player} {@code milli}: Kingdoms' local coin, else emeralds with the change kept. */
	private static Component pay(ServerPlayer player, BlockPos at, long milli) {
		Component local = com.jaymospowers.compat.KingdomsCompat.payLocal(player, at, milli);
		if (local != null) {
			return local;
		}
		long owed = player.getAttachedOrElse(CHANGE, 0L) + milli;
		int emeralds = (int) (owed / 1000L);
		player.setAttached(CHANGE, owed % 1000L);
		if (emeralds > 0) {
			ItemStack give = new ItemStack(Items.EMERALD, emeralds);
			if (!player.addItem(give)) {
				player.spawnAtLocation((ServerLevel) player.level(), give, 1.0F);
			}
		}
		return Component.translatable("message.jaymospowers.fishing.emeralds", emeralds, owed % 1000L);
	}

	// ---- the contest ----

	private static String contestKey(BlockPos at, int day) {
		return village(at) + ":" + day;
	}

	private static Map<String, Contest> contests(ServerLevel level) {
		return level.getServer().overworld().getAttachedOrElse(CONTESTS, Map.of());
	}

	private static void put(ServerLevel level, String key, Contest contest) {
		Map<String, Contest> all = new HashMap<>(contests(level));
		int today = today(level);
		all.values().removeIf(c -> c.day < today - 2 * CONTEST_EVERY);
		all.put(key, contest);
		level.getServer().overworld().setAttached(CONTESTS, all);
	}

	private static void enter(ServerPlayer player, Entity villager) {
		ServerLevel level = (ServerLevel) player.level();
		int today = today(level);
		int day = nextContest(today);
		if (day == today && clock(level) % 24000L >= CONTEST_END) {
			day += CONTEST_EVERY;
		}
		String key = contestKey(villager.blockPosition(), day);
		Contest c = contests(level).getOrDefault(key, new Contest(day, villager.blockPosition(), Map.of()));
		if (c.entries.containsKey(player.getStringUUID())) {
			return;
		}
		Map<String, Entry> entries = new HashMap<>(c.entries);
		entries.put(player.getStringUUID(), new Entry(player.getName().getString(), 0.0F, "", false));
		put(level, key, new Contest(c.day, c.at, entries));
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.2F);
	}

	/** {@code player} landed {@code sp} at {@code kg}: if it's contest time and they entered one round here, it's weighed in. */
	static void landed(ServerPlayer player, Species sp, float kg) {
		ServerLevel level = (ServerLevel) player.level();
		int today = today(level);
		if (today % CONTEST_EVERY != 0 || clock(level) % 24000L >= CONTEST_END) {
			return;
		}
		for (Map.Entry<String, Contest> e : contests(level).entrySet()) {
			Contest c = e.getValue();
			Entry mine = c.entries.get(player.getStringUUID());
			if (c.day == today && mine != null && kg > mine.kg && c.at.closerToCenterThan(player.position(), CONTEST_RANGE)) {
				Map<String, Entry> entries = new HashMap<>(c.entries);
				entries.put(player.getStringUUID(), new Entry(mine.name, kg, sp.id, false));
				put(level, e.getKey(), new Contest(c.day, c.at, entries));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.contest_weighed",
					String.format(java.util.Locale.ROOT, "%.1f", kg)).withStyle(ChatFormatting.GOLD));
				return;
			}
		}
	}

	/** The village's own anglers' catches in contest {@code c}: drawn from the fish of the water, the same for everyone. */
	private static List<Standing> rivals(ServerLevel level, String key, Contest c) {
		RandomSource random = RandomSource.create(level.getSeed() ^ key.hashCode() * 131L);
		List<Species> kinds = new ArrayList<>();
		for (Species sp : Species.values()) {
			if (sp.ours() && sp.hookable() && !sp.big() && sp.livesIn(level.getBiome(c.at))) {
				kinds.add(sp);
			}
		}
		if (kinds.isEmpty()) {
			kinds.add(Species.COD);
		}
		List<Standing> out = new ArrayList<>();
		for (int i = 0; i < RIVALS; i++) {
			Species sp = kinds.get(random.nextInt(kinds.size()));
			out.add(new Standing(RIVAL_NAMES[random.nextInt(RIVAL_NAMES.length)], sp.rollKg(random, false), sp.nameKey(), false));
		}
		return out;
	}

	/** Everyone in contest {@code c}, heaviest first. */
	private static List<Standing> standings(ServerLevel level, String key, Contest c, ServerPlayer viewer) {
		List<Standing> all = new ArrayList<>(rivals(level, key, c));
		for (Map.Entry<String, Entry> e : c.entries.entrySet()) {
			Species sp = Species.byId(e.getValue().species);
			all.add(new Standing(e.getValue().name, e.getValue().kg, sp == null ? "" : sp.nameKey(), e.getKey().equals(viewer.getStringUUID())));
		}
		all.sort(Comparator.comparingDouble(Standing::kg).reversed());
		return all;
	}

	/** The contest this fisherman's village holds next (or is holding, or held last, results still to see), or null. */
	private static Map.@Nullable Entry<String, Contest> contestFor(ServerLevel level, Entity villager, ServerPlayer player) {
		String village = village(villager.blockPosition());
		Map.Entry<String, Contest> best = null;
		for (Map.Entry<String, Contest> e : contests(level).entrySet()) {
			if (!e.getKey().startsWith(village + ":")) {
				continue;
			}
			Entry mine = e.getValue().entries.get(player.getStringUUID());
			boolean unseen = mine != null && !mine.seen && over(level, e.getValue());
			if (unseen || !over(level, e.getValue())) {
				if (best == null || unseen) {
					best = e;
				}
			}
		}
		return best;
	}

	private static boolean over(ServerLevel level, Contest c) {
		int today = today(level);
		return today > c.day || today == c.day && clock(level) % 24000L >= CONTEST_END;
	}

	// ---- the screen ----

	/** Open the fisherman screen for {@code player} with {@code villager} (also called by Kingdoms' "Talk about fishing"). */
	public static void open(ServerPlayer player, Entity villager) {
		ServerLevel level = (ServerLevel) player.level();
		List<Offer> offers = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (sellable(stack)) {
				offers.add(new Offer(i, stack.copy(), price(level, villager.blockPosition(), stack)));
			}
		}
		int today = today(level);
		int state;
		int day = nextContest(today);
		if (day == today && clock(level) % 24000L >= CONTEST_END) {
			day += CONTEST_EVERY;
		}
		List<Standing> standings = List.of();
		Map.Entry<String, Contest> c = contestFor(level, villager, player);
		boolean won = false;
		if (c != null && over(level, c.getValue()) && c.getValue().entries.containsKey(player.getStringUUID())) {
			// the results: seen once, and the winner paid
			state = 3;
			day = c.getValue().day;
			standings = standings(level, c.getKey(), c.getValue(), player);
			won = !standings.isEmpty() && standings.getFirst().you() && standings.getFirst().kg() > 0.0F;
			Map<String, Entry> entries = new HashMap<>(c.getValue().entries);
			Entry mine = entries.get(player.getStringUUID());
			entries.put(player.getStringUUID(), new Entry(mine.name, mine.kg, mine.species, true));
			put(level, c.getKey(), new Contest(c.getValue().day, c.getValue().at, entries));
			FishLog.contest(player, won);
			if (won) {
				Component paid = pay(player, villager.blockPosition(), PRIZE_MILLI);
				ItemStack ribbon = new ItemStack(RIBBON);
				if (!player.addItem(ribbon)) {
					player.spawnAtLocation(level, ribbon, 1.0F);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.contest_won", paid).withStyle(ChatFormatting.GOLD));
			}
		} else if (c != null && c.getValue().entries.containsKey(player.getStringUUID())) {
			state = c.getValue().day == today ? 2 : 1;
			day = c.getValue().day;
			standings = standings(level, c.getKey(), c.getValue(), player).stream().filter(Standing::you).toList();
		} else {
			state = 0;
		}
		FishLegends.Legend legend = FishLegends.rumour(level, villager.blockPosition());
		Species ls = legend == null ? null : legend.kind();
		ServerPlayNetworking.send(player, new View(villager.getId(), offers, state, day, today, standings, won,
			legend == null ? "" : legend.name(), ls == null ? "" : ls.nameKey(), legend == null ? 0.0F : legend.kg()));
	}

	private static void act(ServerPlayer player, Action action) {
		Entity villager = player.level().getEntity(action.villager);
		if (villager == null || !is(villager) || villager.distanceToSqr(player) > 64.0) {
			return;
		}
		switch (action.what) {
			case Action.SELL -> sell(player, villager, action.slot);
			case Action.SELL_ALL -> {
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (sellable(player.getInventory().getItem(i))) {
						sell(player, villager, i);
					}
				}
			}
			case Action.ENTER -> enter(player, villager);
			default -> {
			}
		}
		open(player, villager);
	}

	// ---- payloads ----

	/** A fish {@code player} could sell: inventory slot, the fish, its price (thousandths of an emerald). */
	public record Offer(int slot, ItemStack stack, long milli) {
		static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.of(
			(buf, o) -> {
				buf.writeVarInt(o.slot);
				ItemStack.STREAM_CODEC.encode(buf, o.stack);
				buf.writeVarLong(o.milli);
			},
			buf -> new Offer(buf.readVarInt(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarLong()));
	}

	/** A place in a contest: angler, kg, the fish's name key, and whether it's the viewer. */
	public record Standing(String name, float kg, String species, boolean you) {
		static final StreamCodec<RegistryFriendlyByteBuf, Standing> CODEC = StreamCodec.of(
			(buf, s) -> {
				buf.writeUtf(s.name);
				buf.writeFloat(s.kg);
				buf.writeUtf(s.species);
				buf.writeBoolean(s.you);
			},
			buf -> new Standing(buf.readUtf(), buf.readFloat(), buf.readUtf(), buf.readBoolean()));
	}

	/**
	 * Server to client: the fisherman screen. {@code contest}: 0 not entered (next on {@code day}), 1 entered (on
	 * {@code day}), 2 entered and running today, 3 results. Then the water's legend ("" if none).
	 */
	public record View(int villager, List<Offer> offers, int contest, int day, int today, List<Standing> standings, boolean won,
		String legend, String legendSpecies, float legendKg) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(JaymosPowers.id("fisherman_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = CustomPacketPayload.codec(
			(v, buf) -> {
				buf.writeVarInt(v.villager);
				buf.writeVarInt(v.offers.size());
				v.offers.forEach(o -> Offer.CODEC.encode(buf, o));
				buf.writeVarInt(v.contest);
				buf.writeVarInt(v.day);
				buf.writeVarInt(v.today);
				buf.writeVarInt(v.standings.size());
				v.standings.forEach(s -> Standing.CODEC.encode(buf, s));
				buf.writeBoolean(v.won);
				buf.writeUtf(v.legend);
				buf.writeUtf(v.legendSpecies);
				buf.writeFloat(v.legendKg);
			},
			buf -> {
				int villager = buf.readVarInt();
				int n = buf.readVarInt();
				List<Offer> offers = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					offers.add(Offer.CODEC.decode(buf));
				}
				int contest = buf.readVarInt(), day = buf.readVarInt(), today = buf.readVarInt();
				int m = buf.readVarInt();
				List<Standing> standings = new ArrayList<>();
				for (int i = 0; i < m; i++) {
					standings.add(Standing.CODEC.decode(buf));
				}
				return new View(villager, offers, contest, day, today, standings, buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readFloat());
			});

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** Client to server: sell a slot, sell every fish, or enter the contest. */
	public record Action(int villager, int what, int slot) implements CustomPacketPayload {
		public static final int SELL = 0, SELL_ALL = 1, ENTER = 2;
		public static final Type<Action> TYPE = new Type<>(JaymosPowers.id("fisherman_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = CustomPacketPayload.codec(
			(a, buf) -> {
				buf.writeVarInt(a.villager);
				buf.writeVarInt(a.what);
				buf.writeVarInt(a.slot);
			},
			buf -> new Action(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}
}
