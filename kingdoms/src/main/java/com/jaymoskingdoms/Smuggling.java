package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * The underworld's second stage, its roads (Andy's pick from the PR #525 plan: "smuggling runs between hotbeds, and the trade
 * spreading from city to city"). Each den makes one good, Pmeh or Shash, and the other is dear there ({@link Underworld}); what
 * carries it across the land:
 * <ul>
 * <li><b>Smugglers.</b> A known hotbed's back room sends a man out about once a day ({@link #EVERY}) with a handcart of
 * potatoes and wheat and {@link #LOAD} of the den's own good under the sacks: one of {@link Caravans}' carts ({@code smuggle}),
 * so he is seen on the road where a player is about, and goes on in the record where nobody is. He makes for the nearest
 * other city with a den (the one dealing in the other good first) and, as often as not ({@link #SPREAD_SHARE}), for a city
 * that has none yet. At a den he hands over his load: that fence has more of the other good to sell for a day or two, cheaper
 * ({@code supply}, {@link #SUPPLY_PRICE}), both cities run warmer, and he comes home with the far den's good for his own. Looked
 * at, his cart says only potatoes and wheat; anyone the den knows (who has found either end's back room) can buy what's under
 * them from him with coin in hand ({@link #deal}). Nobody stands up for a smuggler's cart: broken open, it spills everything
 * and no realm thinks the worse of whoever did it ({@link #robbed}).</li>
 * <li><b>The watch.</b> A guard of the land he passes through who sees him close by may search the cart ({@link #SEARCH_CHANCE},
 * far likelier where the watch is cracking down): the goods are seized and he goes on with only his sacks, and if that city
 * has a den the watch cracks down on it. Out of everyone's sight, the same can happen at the far gate ({@link #SEIZE_CHANCE}).</li>
 * <li><b>The trade spreads.</b> A city with no den that smugglers keep visiting ({@link #SEEDS_PER_RUN} a visit), or where a
 * player keeps selling contraband to the townsfolk (one a sale), takes it up when it reaches {@link #SEEDS_TO_OPEN}: it gets a
 * back room of its own, dealing in what came in, rich city or not, and the Tidings carry the word.</li>
 * <li><b>A player's runs.</b> The fence also hands out a <b>sealed bundle</b> for each of the nearest other dens (one a day
 * each): pay him {@link #BUNDLE_PRICE} for it, carry it there, and that fence pays {@link #BUNDLE_PAY} for it. It's
 * contraband the whole way (a guard who sees it in your hand calls it, and a bust takes it), and it adds to the far den's
 * supply like a smuggler's load.</li>
 * </ul>
 */
final class Smuggling {
	/** On a smuggler: his scoreboard tag. */
	static final String TAG = "jaymoskingdoms.smuggler";
	/** A hotbed sends a smuggler this often (ticks), give or take this much; it looks again after this when there was nowhere to go. */
	static final long EVERY = 24000L, EVERY_SPREAD = 8000L, RETRY = 6000L;
	/** How far a smuggler goes (blocks, seat to seat). */
	static final double REACH = 1500.0;
	/** The chance a run goes to a city without a den when there's a den to go to as well. */
	static final float SPREAD_SHARE = 0.4F;
	/** What he carries: this much of his den's good, under this many potatoes and this much wheat. */
	static final int LOAD = 12, COVER_POTATOES = 32, COVER_WHEAT = 16;
	/** A run warms the far city this much, and home this much. */
	static final int HEAT_AWAY = 2, HEAT_HOME = 1;
	/** A fence's supply of the other good: at most this much on top of his usual; sold at this share of the usual price while it lasts. */
	static final int SUPPLY_MOST = 12;
	static final float SUPPLY_PRICE = 0.75F;
	/** Unseen, the chance the far city's watch finds the goods (and this much more while it's cracking down). */
	static final float SEIZE_CHANCE = 0.1F, SEIZE_CRACKDOWN = 0.25F;
	/** Seen: a guard this close with sight of him may search the cart, this likely each look (and this likely while the watch is cracking down). */
	static final double SEARCH_SIGHT = 8.0;
	static final float SEARCH_CHANCE = 0.08F, SEARCH_CRACKDOWN = 0.5F;
	/** How often the smugglers on the road are looked at (ticks), and how far round each player. */
	static final int LOOK_TICKS = 40;
	static final double LOOK_RANGE = 64.0;
	/** The trade spreading: a smuggler's visit counts this much, a player's street sale one; the city takes it up at this. */
	static final int SEEDS_PER_RUN = 2, SEEDS_PER_SALE = 1, SEEDS_TO_OPEN = 6;
	/** What a smuggler asks for one of what's under his sacks (emeralds' worth, in the cart owner's coin). */
	static final int PMEH_PRICE = 6, SHASH_PRICE = 14;
	/** A sealed bundle: what the fence asks for one, what the far fence pays, how many of the nearest other dens he has one for, how much supply it brings. */
	static final int BUNDLE_PRICE = 4, BUNDLE_PAY = 14, BUNDLE_DENS = 2, BUNDLE_SUPPLY = 4;
	/** Lines each (lang {@code smuggle.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;
	static final double SPEECH = 12.0;

	/** On a sealed bundle: the realm whose den it's for. */
	public static DataComponentType<Integer> BUNDLE_TO;
	public static Item SEALED_BUNDLE;

	/** Smuggler caravans whose cart a guard has already gone through (not saved: at worst a second search after a restart). */
	private static final Set<Integer> SEARCHED = new HashSet<>();

	private Smuggling() {
	}

	static void register() {
		BUNDLE_TO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("smuggle_to"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		SEALED_BUNDLE = Underworld.item("sealed_bundle", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % LOOK_TICKS == 0) {
				watch(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEARCHED.clear());
	}

	// ---- the goods ----------------------------------------------------------------------------------------------------

	/** The item a den of this make deals in (null: Pmeh without Jaymo's Kitchen). */
	static @Nullable Item good(int makes) {
		return makes == Underworld.PMEH ? Underworld.pmeh() : Underworld.SHASH_BRICK;
	}

	private static boolean isGood(String key) {
		Item item = Caravans.item(key);
		return item != null && Underworld.contraband(new ItemStack(item));
	}

	/** How much contraband is in {@code cart}. */
	private static int hidden(Map<String, Integer> cart) {
		int n = 0;
		for (Map.Entry<String, Integer> e : cart.entrySet()) {
			if (isGood(e.getKey())) {
				n += e.getValue();
			}
		}
		return n;
	}

	/** {@code cart} with the contraband taken out. */
	private static Map<String, Integer> stripped(Map<String, Integer> cart) {
		Map<String, Integer> out = new HashMap<>(cart);
		out.keySet().removeIf(Smuggling::isGood);
		return Map.copyOf(out);
	}

	/** A cart of sacks with {@code n} of {@code good} under them. */
	private static Map<String, Integer> cart(@Nullable Item good, int n) {
		Map<String, Integer> cart = new HashMap<>();
		cart.put(Caravans.key(Items.POTATO), COVER_POTATOES);
		cart.put(Caravans.key(Items.WHEAT), COVER_WHEAT);
		if (good != null && n > 0) {
			cart.put(Caravans.key(good), n);
		}
		return Map.copyOf(cart);
	}

	/** Which good (make) is hidden in {@code cart}: {@link Underworld#PMEH}, {@link Underworld#SHASH}, or -1 for none. */
	private static int makeIn(Map<String, Integer> cart) {
		Item pmeh = Underworld.pmeh();
		if (pmeh != null && cart.getOrDefault(Caravans.key(pmeh), 0) > 0) {
			return Underworld.PMEH;
		}
		return cart.getOrDefault(Caravans.key(Underworld.SHASH_BRICK), 0) > 0 ? Underworld.SHASH : -1;
	}

	// ---- sending ------------------------------------------------------------------------------------------------------

	/** (From {@link Underworld}'s tick) {@code f}'s den, if it's a known hotbed and its time has come, sends a smuggler out. */
	static void maybeSend(ServerLevel level, Faction f, Underworld.Den d, long now) {
		MinecraftServer server = level.getServer();
		if (!d.known() || d.shutAt(now) || now < d.nextRun() || DenRaids.live(f.id())) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Caravans.Caravan c : Caravans.all(server)) {
			if (c.smuggle() && c.home() == f.id()) {
				// (his man's still out)
				Underworld.put(server, d.withNextRun(now + RETRY));
				return;
			}
		}
		Faction to = target(server, f, d, random);
		if (to == null) {
			Underworld.put(server, d.withNextRun(now + RETRY));
			return;
		}
		Item good = good(d.makes());
		Caravans.launchSmuggler(level, f, to, cart(good, LOAD));
		Underworld.put(server, d.withNextRun(now + EVERY + random.nextInt((int)EVERY_SPREAD)));
		Underworld.warm(server, f, HEAT_HOME);
		JaymosKingdoms.LOGGER.info("A smuggler sets out from {}'s back room for {}", f.name(), to.name());
	}

	/** Where {@code f}'s smuggler goes: the nearest den (dealing in the other good first), or a city without one. */
	private static @Nullable Faction target(MinecraftServer server, Faction f, Underworld.Den d, RandomSource random) {
		long now = Factions.level(server).getGameTime();
		List<Faction> dens = new ArrayList<>(), bare = new ArrayList<>();
		for (Faction o : Factions.all(server)) {
			if (o.id() == f.id() || !o.type().isRealm() || o.fallen() || o.seat().distSqr(f.seat()) > REACH * REACH) {
				continue;
			}
			Underworld.Den od = Underworld.den(server, o.id());
			if (od == null || !od.has()) {
				bare.add(o);
			} else if (od.built() && !od.shutAt(now)) {
				dens.add(o);
			}
		}
		Comparator<Faction> nearest = Comparator.comparingDouble(o -> o.seat().distSqr(f.seat()));
		if (!bare.isEmpty() && (dens.isEmpty() || random.nextFloat() < SPREAD_SHARE)) {
			bare.sort(nearest);
			// (one of the nearest few, so the trade doesn't only ever go one way)
			return bare.get(random.nextInt(Math.min(3, bare.size())));
		}
		if (dens.isEmpty()) {
			return null;
		}
		dens.sort(Comparator.<Faction>comparingInt(o -> {
			Underworld.Den od = Underworld.den(server, o.id());
			return od != null && od.makes() != d.makes() ? 0 : 1;
		}).thenComparing(nearest));
		return dens.getFirst();
	}

	// ---- arriving -----------------------------------------------------------------------------------------------------

	/** (From {@link Caravans}) smuggler {@code c} has got to {@code lord}'s gate, or home to {@code home}'s: the caravan turned for home, or null when he's done. */
	static Caravans.@Nullable Caravan arrive(ServerLevel level, Caravans.Caravan c, Faction home, Faction lord, long now) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Map<String, Integer> load = c.carts().isEmpty() ? Map.of() : c.carts().getFirst();
		int n = hidden(load), make = makeIn(load);
		if (c.back()) {
			// home: what he brought back goes down to his fence
			Underworld.Den hd = Underworld.den(server, home.id());
			if (hd != null && hd.has() && hd.built() && !hd.shutAt(now) && n > 0 && make != hd.makes()) {
				Underworld.put(server, hd.withSupply(hd.supply() + n));
			}
			return null;
		}
		Underworld.Den ld = Underworld.den(server, lord.id());
		Underworld.Den hd = Underworld.den(server, home.id());
		int homeMake = hd == null ? Underworld.SHASH : hd.makes();
		if (n > 0 && random.nextFloat() < SEIZE_CHANCE + (Underworld.crackdown(server, lord.id()) ? SEIZE_CRACKDOWN : 0.0F)) {
			// the watch at the gate went through the sacks
			JaymosKingdoms.LOGGER.info("The watch of {} found what {}'s smuggler had under his sacks", lord.name(), home.name());
			return c.turned(List.of(cart(null, 0)), now + Caravans.STOP_TICKS);
		}
		if (ld == null || !ld.has()) {
			// no back room here yet: he sells what he can on the quiet, and the taste for it takes hold
			if (n > 0) {
				seed(server, lord, SEEDS_PER_RUN, make < 0 ? homeMake : make);
			}
			return c.turned(List.of(cart(null, 0)), now + Caravans.STOP_TICKS);
		}
		if (!ld.built() || ld.shutAt(now)) {
			// (nobody to take it: the den's not dug, or the watch has it shut; he takes it home again)
			return c.turned(List.of(load), now + Caravans.STOP_TICKS);
		}
		if (n > 0 && make != ld.makes()) {
			ld = ld.withSupply(ld.supply() + n);
			Underworld.put(server, ld);
		}
		Underworld.warm(server, lord, HEAT_AWAY);
		Underworld.warm(server, home, HEAT_HOME);
		// he goes home with the far den's good for his own (if it deals in the other one)
		Item back = ld.makes() != homeMake ? good(ld.makes()) : null;
		return c.turned(List.of(cart(back, back == null ? 0 : LOAD)), now + Caravans.STOP_TICKS);
	}

	/**
	 * {@code f}, which has no den, has had another taste of the trade ({@code by}); enough, and it takes it up: a den of its own,
	 * dealing in {@code make}, dug when its seat is next about.
	 */
	static void seed(MinecraftServer server, Faction f, int by, int make) {
		if (!f.type().isRealm() || f.fallen()) {
			return;
		}
		Underworld.Den d = Underworld.den(server, f.id());
		if (d == null) {
			d = Underworld.Den.fresh(f.id(), false, make, f.seat(), Factions.level(server).getGameTime() / 24000L);
		}
		if (d.has()) {
			return;
		}
		int seeds = d.seeds() + by;
		if (seeds < SEEDS_TO_OPEN) {
			Underworld.put(server, d.withSeeds(seeds));
			return;
		}
		int good = make == Underworld.PMEH && Underworld.pmeh() == null ? Underworld.SHASH : make;
		Underworld.put(server, d.withSeeds(seeds).opened(good));
		Post.news(server, Component.translatable("news.jaymoskingdoms.underworld.spread", f.name(),
			Component.translatable(good == Underworld.PMEH ? "item.jaymoskitchen.pmeh" : "item.jaymoskingdoms.shash_brick")));
		JaymosKingdoms.LOGGER.info("The trade has spread to {}: it will keep a back room now", f.name());
	}

	// ---- on the road --------------------------------------------------------------------------------------------------

	/** The smuggler caravan {@code e} drives (by the caravan's stamp on him), or null. */
	private static Caravans.@Nullable Caravan runOf(MinecraftServer server, Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(Caravans.TAG_OF)) {
				String rest = tag.substring(Caravans.TAG_OF.length());
				int dot = rest.indexOf('.');
				try {
					Caravans.Caravan c = Caravans.get(server, Integer.parseInt(dot < 0 ? rest : rest.substring(0, dot)));
					return c != null && c.smuggle() ? c : null;
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	/** Smugglers seen on the road: a guard of the land close by may go through the cart. */
	private static void watch(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Set<Entity> seen = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				seen.addAll(level.getEntitiesOfClass(PathfinderMob.class, p.getBoundingBox().inflate(LOOK_RANGE),
					m -> m.isAlive() && m.entityTags().contains(TAG)));
			}
		}
		RandomSource random = level.getRandom();
		for (Entity s : seen) {
			Caravans.Caravan c = runOf(server, s);
			if (c == null || SEARCHED.contains(c.id()) || c.carts().isEmpty() || hidden(c.carts().getFirst()) == 0) {
				continue;
			}
			Faction f = Theft.lawAt(level, s.blockPosition());
			if (f == null || !f.type().isRealm() || f.fallen()) {
				continue;
			}
			PathfinderMob guard = null;
			for (Worker w : Underworld.guards(level, f, s.getBoundingBox().inflate(SEARCH_SIGHT))) {
				if (w.mob().hasLineOfSight(s) && !Arrests.busy(w.mob())) {
					guard = w.mob();
					break;
				}
			}
			if (guard == null) {
				continue;
			}
			SEARCHED.add(c.id());
			boolean cracking = Underworld.crackdown(server, f.id());
			if (random.nextFloat() >= (cracking ? SEARCH_CRACKDOWN : SEARCH_CHANCE)) {
				// (waved through)
				continue;
			}
			List<Map<String, Integer>> carts = new ArrayList<>();
			for (Map<String, Integer> cart : c.carts()) {
				carts.add(stripped(cart));
			}
			Caravans.put(server, c.carts(carts));
			guard.getLookControl().setLookAt(s, 30.0F, 30.0F);
			Speech.say(level, guard, Component.translatable("smuggle.jaymoskingdoms.search." + random.nextInt(LINES)), Speech.RED, SPEECH);
			Speech.say(level, s, Component.translatable("smuggle.jaymoskingdoms.caught." + random.nextInt(LINES)), Speech.WHITE, SPEECH);
			level.playSound(null, s.blockPosition(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.NEUTRAL, 1.0F, 0.8F);
			if (Underworld.builtDen(server, f) != null) {
				// smuggling under their noses: the watch cracks down on the city's den
				Underworld.crackDown(level, f, guard, null);
			}
		}
	}

	/** (From {@link TradeRoutes#deal}) {@code player}, coin in hand, at smuggler {@code c}'s cart: he sells what's under the sacks to someone the den knows. True: handled. */
	static boolean deal(ServerLevel level, Wagon wagon, ServerPlayer player, Caravans.Caravan c, Faction owner) {
		MinecraftServer server = level.getServer();
		Entity seller = wagon.horse();
		List<Integer> found = player.getAttachedOrElse(Underworld.FOUND, List.of());
		Map<String, Integer> cart = new HashMap<>(c.carts().get(wagon.slot()));
		int make = makeIn(cart);
		RandomSource random = level.getRandom();
		if (!found.contains(c.home()) && !found.contains(c.lord()) || make < 0) {
			if (seller != null) {
				Speech.tell(level, seller, player, Component.translatable("smuggle.jaymoskingdoms.cover." + random.nextInt(LINES)), Speech.WHITE);
			}
			return true;
		}
		Item good = good(make);
		int price = make == Underworld.PMEH ? PMEH_PRICE : SHASH_PRICE;
		if (good == null || !Money.charge(player, owner, Money.worth(price))) {
			if (seller != null) {
				Speech.tell(level, seller, player, Component.translatable("smuggle.jaymoskingdoms.short"), Speech.WHITE);
			}
			return true;
		}
		String key = Caravans.key(good);
		int left = cart.getOrDefault(key, 0) - 1;
		if (left > 0) {
			cart.put(key, left);
		} else {
			cart.remove(key);
		}
		List<Map<String, Integer>> carts = new ArrayList<>(c.carts());
		carts.set(wagon.slot(), Map.copyOf(cart));
		Caravans.put(server, c.carts(carts));
		ItemStack got = new ItemStack(good);
		if (!player.getInventory().add(got)) {
			net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), got);
		}
		if (seller != null) {
			Speech.tell(level, seller, player, Component.translatable("smuggle.jaymoskingdoms.sold." + random.nextInt(LINES)), Speech.GOLD);
		}
		Deeds.done(player, "market.black");
		// dealt out in the open: a crime for any of the land's people who saw it
		Faction law = Theft.lawAt(level, wagon.blockPosition());
		if (law != null && law.type().isRealm()) {
			List<LivingEntity> saw = new ArrayList<>(Theft.crimeWitnesses(level, law, player));
			saw.removeIf(e -> e == seller || Crooks.isCrook(e) || e.entityTags().contains(TAG));
			if (!saw.isEmpty()) {
				Theft.commit(player, level, law, Theft.Crime.DEALING, null, false, saw);
				Busts.charge(player, law, "dealing");
				Underworld.overheard(level, law, saw, player);
			}
		}
		return true;
	}

	/** (From {@link Caravans#robbed}) {@code player} broke a smuggler's cart open: no realm will hear of it. */
	static void robbed(ServerLevel level, Wagon wagon, ServerPlayer player, Caravans.Caravan c) {
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.smuggle.robbed").withStyle(ChatFormatting.GRAY));
	}

	// ---- sealed bundles -----------------------------------------------------------------------------------------------

	/** A sealed bundle for {@code to}'s den. */
	static ItemStack bundle(Faction to) {
		ItemStack stack = new ItemStack(SEALED_BUNDLE);
		stack.set(BUNDLE_TO, to.id());
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.sealed_bundle.for", to.name()));
		return stack;
	}

	static boolean isBundle(ItemStack stack) {
		return SEALED_BUNDLE != null && stack.is(SEALED_BUNDLE);
	}

	/**
	 * (From {@link Underworld}'s stocking) {@code f}'s fence lays out his sealed bundles: one for each of the nearest
	 * {@link #BUNDLE_DENS} other open dens, and he pays for any sealed for his own.
	 */
	static void bundles(ServerLevel level, Faction f, MerchantOffers offers) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		List<Faction> dens = new ArrayList<>();
		for (Faction o : Factions.all(server)) {
			Underworld.Den od = o.id() == f.id() || !o.type().isRealm() || o.fallen() ? null : Underworld.builtDen(server, o);
			if (od != null && !od.shutAt(now) && o.seat().distSqr(f.seat()) <= REACH * REACH) {
				dens.add(o);
			}
		}
		dens.sort(Comparator.comparingDouble(o -> o.seat().distSqr(f.seat())));
		for (int i = 0; i < Math.min(BUNDLE_DENS, dens.size()); i++) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, BUNDLE_PRICE), bundle(dens.get(i)), 1, Underworld.XP, Underworld.PRICE_MULTIPLIER));
		}
		ItemCost mine = new ItemCost(SEALED_BUNDLE.builtInRegistryHolder(), 1, DataComponentExactPredicate.expect(BUNDLE_TO, f.id()));
		offers.add(new MerchantOffer(mine, new ItemStack(Items.EMERALD, BUNDLE_PAY), 4, Underworld.XP, Underworld.PRICE_MULTIPLIER));
	}

	/** (From {@link Underworld#traded}) a sealed bundle was handed in at {@code f}'s fence: it's stock for him. */
	static void delivered(MinecraftServer server, Faction f) {
		Underworld.Den d = Underworld.den(server, f.id());
		if (d != null && d.has()) {
			Underworld.put(server, d.withSupply(d.supply() + BUNDLE_SUPPLY));
		}
	}
}
