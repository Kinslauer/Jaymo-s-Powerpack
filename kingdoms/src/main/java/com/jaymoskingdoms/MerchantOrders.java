package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.256) Merchant orders on the town board, after Merchant Orders by ouheaddd (its idea, remade: that mod is MIT, but
 * it's NeoForge/Forge and keeps its own board block, sack and catalog, none of which we need). Andy: "can we add this
 * functionality to our boards? it should not be craftable, and the deliveries should be handled through couriers and
 * tidings".
 * <ul>
 * <li>A villager of the town pins a <b>Merchant orders</b> sheet on its bounty board ({@link Bounties} errands). It stays
 * up: use or punch it to read what the town's merchants sell ({@link #open}). Nothing to craft: no board, no sack.</li>
 * <li>The catalog is the town's own trades: every grown villager with a trade within {@link #CATALOG_RANGE} of the
 * board, each offer bought with emeralds alone. One shared catalog; each player may order each ware as often a day as
 * the merchant would trade it ({@link MerchantOffer#getMaxUses}), and it restocks at dawn.</li>
 * <li>Five <b>standings</b> (Novice to Master), earned by the trade experience of what you order
 * ({@link #STANDING_XP}): a merchant's higher-level trades (offers 3-4 are Apprentice, 5-6 Journeyman...) show once
 * your standing reaches them.</li>
 * <li>Ordering pays at once, into the town's storehouse; the merchant makes it up ({@link #PREP}). Then a free
 * townsman near the board carries everything you have waiting from that town, wrapped in one {@link #PARCEL}, to you
 * wherever you are ({@link Couriers}: on foot, or riding if you're far). If the town is out of everyone's reach for
 * {@link #POST_AFTER}, the post takes it ({@link Post#later}). Either way a line goes in your next Tidings
 * ({@link Post#tidings}): who's bringing it, and if the courier is cut down on the road, where the goods lie.</li>
 * <li>An order not yet sent can be cancelled for the emeralds back.</li>
 * </ul>
 */
final class MerchantOrders {
	// ---- tuning ----
	/** Merchants this near the board (blocks) are in its catalog; the most wares shown. */
	static final double CATALOG_RANGE = 64.0;
	static final int MOST_WARES = 60;
	/** How long (ticks) a merchant takes to make an order up; how long it waits on an unloaded town before the post takes it. */
	static final long PREP = 1200L, POST_AFTER = 6000L;
	/** A courier is found this near the board (blocks). */
	static final double COURIER_RANGE = 48.0;
	/** Standing experience for Novice, Apprentice, Journeyman, Expert, Master (vanilla's villager levels). */
	static final int[] STANDING_XP = {0, 10, 70, 150, 250};
	/** A merchant's offers come two a level (vanilla), so offer {@code i} needs standing {@code i / OFFERS_A_LEVEL + 1}. */
	static final int OFFERS_A_LEVEL = 2;
	/** The least reputation with the town its merchants sell at. */
	static final int LEAST_REP = 0;
	/** At most this many stacks in one parcel; at most this many orders waiting from one town. */
	static final int PARCEL_SLOTS = 27, MOST_WAITING = 12;
	/** How often (ticks) orders are seen to. */
	static final int EVERY = 100;
	/** The chance, each board check, that a board without a sheet gets one. */
	static final float SHEET_CHANCE = 0.5F;
	/** (QuestNet) The "giver" of the orders screen. */
	static final int ORDERS = QuestNet.ORDERS;
	/** A board's sheet: {@code orders|<the board's anchor as a long>}. */
	static final String SHEET = "orders|";
	/**
	 * (0.7.369, Off-World Orders) The sheet also lists what the town's merchants want from the far worlds
	 * ({@link OffWorldGoods}): hand in a good, get its listed price in emeralds at once, and
	 * {@link #FAR_STANDING_XP} standing, which is faster than ordering wares earns it (one ware earns its trade's 1 to 30,
	 * mostly under 10). Each want can be answered once a day by each player.
	 */
	static final int FAR_STANDING_XP = 25;
	/** The prefix of a want's id on the screen. */
	static final String WANT = "want:";
	/** The custom data key on a parcel (the town it came from). */
	static final String PARCEL_KEY = "jaymoskingdoms_parcel";

	static Item PARCEL;

	/**
	 * An order: {@code buyer} paid {@code paid} emeralds for {@code goods} from {@code merchant} (their name) of the town
	 * {@code town} (its name), ordered at the board {@code board}; it can go from {@code ready}. {@code ware} and {@code xp}:
	 * what it counted against the buyer's day and earned their standing (taken back if it's cancelled).
	 */
	record Order(String id, UUID buyer, ItemStack goods, int paid, String merchant, String town, int townId, BlockPos board, long ready,
		String ware, int xp) {
		static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Order::id),
			UUIDUtil.CODEC.fieldOf("buyer").forGetter(Order::buyer),
			ItemStack.OPTIONAL_CODEC.fieldOf("goods").forGetter(Order::goods),
			Codec.INT.fieldOf("paid").forGetter(Order::paid),
			Codec.STRING.fieldOf("merchant").forGetter(Order::merchant),
			Codec.STRING.fieldOf("town").forGetter(Order::town),
			Codec.INT.fieldOf("town_id").forGetter(Order::townId),
			BlockPos.CODEC.fieldOf("board").forGetter(Order::board),
			Codec.LONG.fieldOf("ready").forGetter(Order::ready),
			Codec.STRING.optionalFieldOf("ware", "").forGetter(Order::ware),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(Order::xp)).apply(i, Order::new));
	}

	/** A player's standing with the merchants ({@code xp}), and what they've ordered today ({@code day}: ware → how many). */
	record Ledger(int xp, long day, Map<String, Integer> bought) {
		static final Ledger NONE = new Ledger(0, -1L, Map.of());
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("xp").forGetter(Ledger::xp),
			Codec.LONG.fieldOf("day").forGetter(Ledger::day),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("bought").forGetter(Ledger::bought)).apply(i, Ledger::new));

		int boughtToday(String ware, long today) {
			return this.day == today ? this.bought.getOrDefault(ware, 0) : 0;
		}

		Ledger bought(String ware, long today, int delta, int xp) {
			Map<String, Integer> b = new HashMap<>(this.day == today ? this.bought : Map.of());
			int n = Math.max(0, b.getOrDefault(ware, 0) + delta);
			if (n == 0) {
				b.remove(ware);
			} else {
				b.put(ware, n);
			}
			return new Ledger(Math.max(0, this.xp + xp), today, Map.copyOf(b));
		}
	}

	/** On the Overworld: every order not yet sent, by its id. Saved. */
	static final AttachmentType<Map<String, Order>> WAITING = AttachmentRegistry.create(JaymosKingdoms.id("merchant_orders"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Order.CODEC)));
	/** On a player: their {@link Ledger}. Saved, kept through death. */
	static final AttachmentType<Ledger> LEDGER = AttachmentRegistry.create(JaymosKingdoms.id("merchant_ledger"),
		builder -> builder.persistent(Ledger.CODEC).copyOnDeath());

	/** Player → the board whose orders they're reading. */
	private static final Map<UUID, BlockPos> READING = new HashMap<>();

	private MerchantOrders() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("parcel"));
		PARCEL = Registry.register(BuiltInRegistries.ITEM, key, new Parcel(new Item.Properties().setId(key).stacksTo(1)));
		ServerTickEvents.END_SERVER_TICK.register(MerchantOrders::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READING.clear());
	}

	// ---- the sheet on the board ------------------------------------------------------------------------------------------

	static boolean is(String key) {
		return key.startsWith(SHEET);
	}

	static String sheetKey(BlockPos anchor) {
		return SHEET + anchor.asLong();
	}

	/** The board a sheet belongs to. */
	static @Nullable BlockPos boardOf(String key) {
		try {
			return BlockPos.of(Long.parseLong(key.substring(SHEET.length())));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The sheet as the paper a villager carries to the board. */
	static ItemStack sheet(String key) {
		ItemStack stack = new ItemStack(TownNotices.ITEM);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("orders.jaymoskingdoms.sheet")
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE)));
		CompoundTag tag = new CompoundTag();
		tag.putString(Bounties.NOTICE_KEY, key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** What the villager calls out pinning the sheet up. */
	static Component callOut(RandomSource random) {
		return Component.translatable("talk.jaymoskingdoms.orders.posting." + random.nextInt(3));
	}

	// ---- the catalog -------------------------------------------------------------------------------------------------------

	/** One ware: {@code merchant}'s offer number {@code index}, for this many emeralds; the standing it needs. */
	private record Ware(String id, Villager merchant, int index, MerchantOffer offer, int price, int tier) {
	}

	/** The standing (1 Novice .. 5 Master) {@code xp} makes. */
	static int standing(int xp) {
		int s = 1;
		for (int i = 1; i < STANDING_XP.length; i++) {
			if (xp >= STANDING_XP[i]) {
				s = i + 1;
			}
		}
		return s;
	}

	static Ledger ledger(ServerPlayer player) {
		return player.getAttachedOrElse(LEDGER, Ledger.NONE);
	}

	/** What an offer costs on the board: its price with the merchant's demand, but no one player's discount or mark-up. */
	private static int price(MerchantOffer offer, float factor) {
		return Math.max(1, Math.round(price(offer) * factor));
	}

	private static int price(MerchantOffer offer) {
		int base = offer.getBaseCostA().getCount();
		int demand = Math.max(0, Mth.floor(base * offer.getDemand() * offer.getPriceMultiplier()));
		return Mth.clamp(base + demand, 1, Items.EMERALD.getDefaultMaxStackSize());
	}

	/** The wares of the merchants about the board at {@code anchor}, cheapest standing first. */
	private static List<Ware> wares(ServerLevel level, BlockPos anchor) {
		List<Ware> out = new ArrayList<>();
		// (0.7.369) lower while a pod stands in the town's landing yard
		float factor = LandingYard.tradeFactor(level, town(level, anchor));
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(CATALOG_RANGE),
			v -> v.isAlive() && !v.isBaby() && !Downed.isDowned(v))) {
			if (v.getVillagerData().profession().is(VillagerProfession.NONE) || v.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
				continue;
			}
			var offers = v.getOffers();
			for (int i = 0; i < offers.size(); i++) {
				MerchantOffer o = offers.get(i);
				ItemStack result = o.getResult();
				if (result.isEmpty() || result.is(Items.EMERALD) || !o.getBaseCostA().is(Items.EMERALD) || o.getItemCostB().isPresent()) {
					continue;
				}
				out.add(new Ware(v.getStringUUID() + "#" + i, v, i, o, price(o, factor), Math.min(STANDING_XP.length, i / OFFERS_A_LEVEL + 1)));
			}
		}
		out.sort(Comparator.comparingInt(Ware::tier).thenComparing(w -> w.merchant().getStringUUID()).thenComparingInt(Ware::index));
		return out;
	}

	private static @Nullable Ware ware(ServerLevel level, BlockPos anchor, String id) {
		for (Ware w : wares(level, anchor)) {
			if (w.id().equals(id)) {
				return w;
			}
		}
		return null;
	}

	/** The town the board at {@code anchor} serves. */
	private static @Nullable Faction town(ServerLevel level, BlockPos anchor) {
		return Factions.around(level.getServer(), level, anchor, Couriers.REALM_RANGE);
	}

	// ---- the screen --------------------------------------------------------------------------------------------------------

	/** {@code player} reads the merchant orders of the board at {@code anchor}. */
	static void open(ServerLevel level, ServerPlayer player, BlockPos anchor) {
		READING.put(player.getUUID(), anchor.immutable());
		Faction town = town(level, anchor);
		Component title = Component.translatable("orders.jaymoskingdoms.screen", town == null ? Component.translatable("bounty.jaymoskingdoms.nowhere") : town.name());
		if (town == null) {
			send(player, new QuestNet.Open(ORDERS, title, List.of(), Component.translatable("orders.jaymoskingdoms.no_town")));
			return;
		}
		if (Reputation.get(player, town.id()) < LEAST_REP) {
			send(player, new QuestNet.Open(ORDERS, title, List.of(), Component.translatable("orders.jaymoskingdoms.refused", town.name())));
			return;
		}
		Ledger ledger = ledger(player);
		int standing = standing(ledger.xp());
		long today = Quests.day(level);
		List<QuestNet.View> views = new ArrayList<>();
		// (what you have waiting from here first)
		for (Order o : waiting(level.getServer()).values()) {
			if (o.buyer().equals(player.getUUID()) && o.townId() == town.id()) {
				views.add(new QuestNet.View("order:" + o.id(), QuestNet.ACTIVE, false,
					Component.translatable("orders.jaymoskingdoms.title.ordered", o.goods().getHoverName(), o.goods().getCount()),
					Component.translatable("orders.jaymoskingdoms.text.ordered", o.merchant(), o.paid()),
					Component.translatable(level.getGameTime() < o.ready() ? "orders.jaymoskingdoms.objective.making" : "orders.jaymoskingdoms.objective.courier"),
					List.of(o.goods().copy()), List.of()));
			}
		}
		if (OffWorldGoods.asks(player)) {
			for (OffWorldGoods.Good g : OffWorldGoods.today(anchor, today)) {
				views.add(wantView(player, g, ledger, today));
			}
		}
		int locked = 0;
		for (Ware w : wares(level, anchor)) {
			if (w.tier() > standing) {
				locked++;
				continue;
			}
			if (views.size() >= MOST_WARES) {
				break;
			}
			int left = Math.max(0, w.offer().getMaxUses() - ledger.boughtToday(w.id(), today));
			ItemStack result = w.offer().getResult();
			Component job = Component.translatable("orders.jaymoskingdoms.job." + Mth.clamp(w.tier(), 1, 5));
			views.add(new QuestNet.View(w.id(), QuestNet.OFFER, left == 0,
				Component.translatable("orders.jaymoskingdoms.title", result.getHoverName(), result.getCount(), w.price()),
				Component.translatable("orders.jaymoskingdoms.text", w.merchant().getDisplayName(), professionName(w.merchant()), town.name(), w.price()),
				left == 0 ? Component.translatable("orders.jaymoskingdoms.objective.sold_out")
					: Component.translatable("orders.jaymoskingdoms.objective.left", left),
				List.of(result.copy(), new ItemStack(Items.EMERALD, w.price())),
				List.of(Component.translatable("orders.jaymoskingdoms.extra.standing", job, w.offer().getXp()))));
		}
		Component note = Component.translatable("orders.jaymoskingdoms.standing", Component.translatable("orders.jaymoskingdoms.job." + standing), ledger.xp(),
			standing < STANDING_XP.length ? Component.literal(" / " + STANDING_XP[standing]) : Component.empty());
		if (locked > 0) {
			note = note.copy().append(" ").append(Component.translatable("orders.jaymoskingdoms.locked", locked));
		}
		send(player, new QuestNet.Open(ORDERS, title, views, note));
	}

	private static Component professionName(Villager v) {
		return v.getVillagerData().profession().unwrapKey()
			.map(k -> (Component)Component.translatable("entity." + k.identifier().getNamespace() + ".villager." + k.identifier().getPath()))
			.orElse(Component.empty());
	}

	private static void send(ServerPlayer player, QuestNet.Open open) {
		if (ServerPlayNetworking.canSend(player, QuestNet.Open.TYPE)) {
			ServerPlayNetworking.send(player, open);
		}
	}

	/** (Quests) A choice on the orders screen: order a ware, or cancel an order not yet sent. */
	static void act(ServerPlayer player, QuestNet.Act act) {
		BlockPos at = READING.get(player.getUUID());
		if (act.action() == QuestNet.CLOSE) {
			READING.remove(player.getUUID());
			return;
		}
		if (at == null || !(player.level() instanceof ServerLevel level) || level != Factions.level(level.getServer())
			|| player.distanceToSqr(Vec3.atCenterOf(at)) > 64.0) {
			return;
		}
		if (act.action() == QuestNet.ACCEPT && act.questId().startsWith(WANT)) {
			want(level, player, at, act.questId().substring(WANT.length()));
		} else if (act.action() == QuestNet.ACCEPT) {
			order(level, player, at, act.questId());
		} else if (act.action() == QuestNet.ABANDON && act.questId().startsWith("order:")) {
			cancel(level, player, act.questId().substring(6));
		}
		open(level, player, at);
	}

	private static void order(ServerLevel level, ServerPlayer player, BlockPos at, String id) {
		Faction town = town(level, at);
		Ware w = town == null || Reputation.get(player, town.id()) < LEAST_REP ? null : ware(level, at, id);
		if (w == null) {
			return;
		}
		Ledger ledger = ledger(player);
		long today = Quests.day(level);
		if (w.tier() > standing(ledger.xp()) || ledger.boughtToday(w.id(), today) >= w.offer().getMaxUses()) {
			return;
		}
		long mine = waiting(level.getServer()).values().stream().filter(o -> o.buyer().equals(player.getUUID()) && o.townId() == town.id()).count();
		if (mine >= MOST_WAITING) {
			player.sendSystemMessage(Component.translatable("orders.jaymoskingdoms.too_many").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!pay(player, w.price())) {
			player.sendSystemMessage(Component.translatable("orders.jaymoskingdoms.poor", w.price()).withStyle(ChatFormatting.GRAY));
			return;
		}
		// (the emeralds go into the town's storehouse, where its wages come from)
		Storage.put(Factions.level(level.getServer()), town, new ItemStack(Items.EMERALD, w.price()));
		Order o = new Order(UUID.randomUUID().toString(), player.getUUID(), w.offer().getResult().copy(), w.price(), w.merchant().getName().getString(),
			town.name(), town.id(), at.immutable(), level.getGameTime() + PREP, w.id(), w.offer().getXp());
		put(level.getServer(), o);
		player.setAttached(LEDGER, ledger.bought(w.id(), today, 1, w.offer().getXp()));
		level.playSound(null, at, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.0F);
		level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.1F);
	}

	// ---- off-world wants (0.7.369) ---------------------------------------------------------------------------------------

	/** How many of {@code good} the merchants ask for today (the same for everyone). */
	private static int wanted(OffWorldGoods.Good g, BlockPos anchor, long day) {
		return g.min() + new java.util.Random(anchor.asLong() ^ day ^ g.key().hashCode()).nextInt(g.max() - g.min() + 1);
	}

	private static Component worldName(OffWorldGoods.Good g) {
		return Component.translatable("order.jaymoskingdoms.world." + g.world());
	}

	private static QuestNet.View wantView(ServerPlayer player, OffWorldGoods.Good g, Ledger ledger, long today) {
		BlockPos anchor = READING.getOrDefault(player.getUUID(), BlockPos.ZERO);
		int count = wanted(g, anchor, today);
		ItemStack goods = new ItemStack(g.item(), count);
		boolean done = ledger.boughtToday(WANT + g.key(), today) > 0;
		return new QuestNet.View(WANT + g.key(), QuestNet.OFFER, done,
			Component.translatable("orders.jaymoskingdoms.want.title", goods.getHoverName(), count, worldName(g)),
			Component.translatable("orders.jaymoskingdoms.want.text", worldName(g), g.emeralds()),
			done ? Component.translatable("orders.jaymoskingdoms.want.done") : Component.translatable("orders.jaymoskingdoms.want.objective", count),
			List.of(goods.copy(), new ItemStack(Items.EMERALD, g.emeralds())),
			List.of(Component.translatable("orders.jaymoskingdoms.want.standing", FAR_STANDING_XP)));
	}

	/** {@code player} hands in the far-world good {@code key} the board wants today: it goes to the town's storehouse, and they get paid. */
	private static void want(ServerLevel level, ServerPlayer player, BlockPos at, String key) {
		Faction town = town(level, at);
		OffWorldGoods.Good g = town == null || !OffWorldGoods.asks(player) ? null : OffWorldGoods.byKey(key);
		long today = Quests.day(level);
		if (g == null || OffWorldGoods.today(at, today).stream().noneMatch(w -> w.key().equals(key)) || Reputation.get(player, town.id()) < LEAST_REP) {
			return;
		}
		Ledger ledger = ledger(player);
		if (ledger.boughtToday(WANT + key, today) > 0) {
			return;
		}
		if (!Storage.loaded(Factions.level(level.getServer()), town)) {
			return;
		}
		int count = wanted(g, at, today);
		var inv = player.getInventory();
		int have = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(g.item())) {
				have += inv.getItem(i).getCount();
			}
		}
		if (have < count) {
			player.sendSystemMessage(Component.translatable("orders.jaymoskingdoms.want.short", count, new ItemStack(g.item()).getHoverName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		for (int i = 0, left = count; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(g.item())) {
				int n = Math.min(left, s.getCount());
				s.shrink(n);
				left -= n;
			}
		}
		inv.setChanged();
		ItemStack left = Storage.put(Factions.level(level.getServer()), town, new ItemStack(g.item(), count));
		if (!left.isEmpty() && !player.getInventory().add(left) && !left.isEmpty()) {
			// a full storehouse hands the rest back rather than losing it
			player.drop(left, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		ItemStack pay = new ItemStack(Items.EMERALD, g.emeralds());
		if (!player.getInventory().add(pay) && !pay.isEmpty()) {
			player.drop(pay, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.setAttached(LEDGER, ledger.bought(WANT + key, today, 1, FAR_STANDING_XP));
		Post.tidings(player.getUUID(), Component.translatable("orders.jaymoskingdoms.tidings.want", town.name(), worldName(g)));
		level.playSound(null, at, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.1F);
	}

	/** (0.7.369) {@code player} carries a parcel. */
	static boolean carriesParcel(ServerPlayer player) {
		return player.getInventory().contains(MerchantOrders::isParcel);
	}

	/** (0.7.369) Takes one parcel out of {@code player}'s pack (empty if none). */
	static ItemStack takeParcel(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isParcel(inv.getItem(i))) {
				ItemStack taken = inv.getItem(i).split(1);
				inv.setChanged();
				return taken;
			}
		}
		return ItemStack.EMPTY;
	}

	/** (0.7.369) What a parcel holds. */
	static List<ItemStack> contents(ItemStack parcel) {
		return parcel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
	}

	/** Takes {@code price} emeralds out of {@code player}'s pack, if they have that many. */
	private static boolean pay(ServerPlayer player, int price) {
		var inv = player.getInventory();
		int have = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(Items.EMERALD)) {
				have += inv.getItem(i).getCount();
			}
		}
		if (have < price) {
			return false;
		}
		for (int i = 0, left = price; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.EMERALD)) {
				int n = Math.min(left, s.getCount());
				s.shrink(n);
				left -= n;
			}
		}
		inv.setChanged();
		return true;
	}

	private static void cancel(ServerLevel level, ServerPlayer player, String id) {
		Order o = waiting(level.getServer()).get(id);
		if (o == null || !o.buyer().equals(player.getUUID())) {
			return;
		}
		remove(level.getServer(), id);
		player.setAttached(LEDGER, ledger(player).bought(o.ware(), Quests.day(level), -1, -o.xp()));
		ItemStack refund = new ItemStack(Items.EMERALD, o.paid());
		if (!player.getInventory().add(refund) && !refund.isEmpty()) {
			player.drop(refund, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
	}

	// ---- the record ----------------------------------------------------------------------------------------------------------

	private static Map<String, Order> waiting(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(WAITING, Map.of());
	}

	private static void put(MinecraftServer server, Order o) {
		Map<String, Order> all = new HashMap<>(waiting(server));
		all.put(o.id(), o);
		Factions.level(server).setAttached(WAITING, Map.copyOf(all));
	}

	private static void remove(MinecraftServer server, String id) {
		Map<String, Order> all = new HashMap<>(waiting(server));
		if (all.remove(id) != null) {
			Factions.level(server).setAttached(WAITING, Map.copyOf(all));
		}
	}

	// ---- sending them out ------------------------------------------------------------------------------------------------------

	/** Each buyer's ready orders from each town go out together: with a townsman from the board, or by the post if the town's out of reach. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Map<String, Order> all = waiting(server);
		if (all.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		Map<String, List<Order>> lots = new HashMap<>();
		for (Order o : all.values()) {
			if (o.ready() <= now) {
				lots.computeIfAbsent(o.buyer() + "|" + o.townId(), k -> new ArrayList<>()).add(o);
			}
		}
		for (List<Order> lot : lots.values()) {
			Order first = lot.getFirst();
			if (level.isPositionEntityTicking(first.board())) {
				Villager courier = Couriers.nearestFree(level, Vec3.atCenterOf(first.board()), COURIER_RANGE);
				if (courier == null) {
					continue;
				}
				for (Order o : lot) {
					remove(server, o.id());
				}
				Couriers.start(level, courier, parcel(lot), null, first.buyer());
				Post.tidings(first.buyer(), Component.translatable("orders.jaymoskingdoms.tidings.sent", first.town(), courier.getName()));
			} else if (now - first.ready() > POST_AFTER) {
				for (Order o : lot) {
					remove(server, o.id());
				}
				Post.later(first.buyer(), parcel(lot));
				Post.tidings(first.buyer(), Component.translatable("orders.jaymoskingdoms.tidings.post", first.town()));
			}
		}
	}

	/** The orders of {@code lot} wrapped in one parcel, named for their town, the merchants in its lore. */
	private static ItemStack parcel(List<Order> lot) {
		List<ItemStack> goods = new ArrayList<>();
		List<String> from = new ArrayList<>();
		for (Order o : lot) {
			ItemStack left = o.goods().copy();
			// (alike goods packed together)
			for (ItemStack g : goods) {
				if (!left.isEmpty() && ItemStack.isSameItemSameComponents(g, left) && g.getCount() < g.getMaxStackSize()) {
					int n = Math.min(left.getCount(), g.getMaxStackSize() - g.getCount());
					g.grow(n);
					left.shrink(n);
				}
			}
			if (!left.isEmpty()) {
				goods.add(left);
			}
			if (!from.contains(o.merchant())) {
				from.add(o.merchant());
			}
		}
		while (goods.size() > PARCEL_SLOTS) {
			// (can't happen with MOST_WAITING orders, but never lose the rest)
			goods.removeLast();
		}
		ItemStack stack = new ItemStack(PARCEL);
		stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(goods));
		String town = lot.getFirst().town();
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.parcel.from", town)
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.parcel.merchants", String.join(", ", from))
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
		CompoundTag tag = new CompoundTag();
		tag.putString(PARCEL_KEY, town);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	static boolean isParcel(ItemStack stack) {
		return stack.is(PARCEL);
	}

	/** (Couriers) The courier bringing {@code to} a parcel was killed at {@code where}: a line in their Tidings. */
	static void waylaid(UUID to, ItemStack parcel, Villager courier, BlockPos where) {
		CustomData data = parcel.get(DataComponents.CUSTOM_DATA);
		String town = data == null ? "" : data.copyTag().getStringOr(PARCEL_KEY, "");
		Post.tidings(to, Component.translatable("orders.jaymoskingdoms.tidings.waylaid", courier.getName(), town, where.getX(), where.getZ()));
	}

	/** A parcel: use it to unwrap it. */
	static final class Parcel extends Item {
		Parcel(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			List<ItemStack> goods = contents.nonEmptyItemCopyStream().toList();
			stack.shrink(1);
			for (ItemStack g : goods) {
				if (!player.getInventory().add(g) && !g.isEmpty()) {
					player.drop(g, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 0.9F);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymoskingdoms.parcel.use").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
