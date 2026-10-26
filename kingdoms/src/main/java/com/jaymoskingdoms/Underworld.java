package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.211) The criminal underbelly, stage 1. Andy (2026-10-25): "Id like to see illegal goods traded amongst the people.
 * Rare commodities like Pmeh and Shash bricks go for sale around specific cities that eventually become known as hotbeds
 * for trafficking goods and gambling. You should be able to find these speakeasies hidden from the guard as being caught
 * facilitating sale would end up with you flogged, imprisoned, or worse."
 *
 * <p>(Andy: "One in three cities that are wealthier, giving the criminals a pool to siphon from.") The wealthiest
 * third of the realms ({@link #DEN_SHARE}, by {@code Wealth.worth}) keep a <b>speakeasy</b>; a realm that grows rich
 * enough to join them gets one too, and a den once dug stays when its city's fortunes fall. It's a back room dug under the town ({@link Speakeasy}), reached by a ladder under a hatch in
 * the grass. Its <b>fence</b> sells and buys the contraband, <b>Pmeh</b> (Jaymo's Kitchen's herb: roll it into a joint
 * there to smoke it) and <b>Shash bricks</b>; each den makes one of the
 * two ({@link Den#makes}), so it's cheap there and dear in a den that makes the other: buy in one city, carry it across
 * the land, sell in another. Once a den is warm enough ({@link #DICE_HEAT}) a dice-thrower sets up there too (hold
 * emeralds and right-click him: a throw of two dice each, higher takes the pot).
 *
 * <p>Every sale warms the city ({@link Den#heat}); so does each day. Hot enough ({@link #KNOWN_HEAT}) and it's known
 * across the land (the Tidings carry it) as a hotbed of trafficking and gambling: more of its crooks buy off you in the
 * street. Nobody gives a den away to just anyone, but in a hotbed's streets its crooks whisper the way to strangers
 * ({@link #whispers}), out of a guard's hearing.
 *
 * <p>Dealing outside the den is a crime ({@code Theft.Crime#DEALING}), and so is waving the stuff about in front of a
 * guard ({@code Theft.Crime#CONTRABAND}); either, seen, has you hunted, and caught (downed by the guards, or yielding:
 * sneak empty-handed up to one) you're sentenced on the spot ({@link Busts}): flogged, thrown in the cells, or beheaded.
 * Each bust cools the city down.
 *
 * <p>(0.7.224) Every den keeps a <b>bookie</b> ({@link Bookies}), the only one who takes wagers on wars, and on the
 * bouts in the den's <b>fight pit</b> ({@link FightPit}), dug off the den as a backroom (or a makeshift ring where
 * that's all there's room for).
 *
 * <p>Stage 2 (not yet): the guards raiding a den that runs too hot, smuggling runs between hotbeds, the trade spreading
 * from city to city.
 */
public final class Underworld {
	/** One realm in this many keeps a den: the richest. */
	static final int DEN_SHARE = 3;
	/** Heat: what the den starts at, a sale adds, a day adds, a bust takes off; the dice from this; known from this; never above this. */
	static final int START_HEAT = 0, HEAT_PER_SALE = 1, HEAT_PER_DAY = 2, HEAT_PER_BUST = -8, DICE_HEAT = 10, KNOWN_HEAT = 40, MAX_HEAT = 100;
	/** One dice game in this many warms the city a point. */
	static final int GAMES_PER_HEAT = 4;
	/** The fence's prices in emeralds (the den's own make, the other city's), and how many a day: Pmeh. */
	static final int PMEH_SELL_HOME = 3, PMEH_SELL_AWAY = 12, PMEH_BUY_HOME = 2, PMEH_BUY_AWAY = 8;
	/** The same for a Shash brick. */
	static final int SHASH_SELL_HOME = 7, SHASH_SELL_AWAY = 24, SHASH_BUY_HOME = 4, SHASH_BUY_AWAY = 16;
	static final int HOME_STOCK = 8, AWAY_STOCK = 2, BUY_STOCK = 6;
	/** A hotbed's crooks buy in the street: these prices, this many a day. */
	static final int STREET_PMEH = 5, STREET_SHASH = 11, STREET_STOCK = 3;
	/**
	 * (Andy: "you can also sell to any villagers receptive enough to listen") Sneak and right-click any grown villager
	 * with contraband in hand to offer it. They're receptive if hearts + mood x {@link #PITCH_MOOD} + their personality's
	 * lean ({@link #leaning}) + {@link #PITCH_HOTBED} in a known hotbed reaches {@link #PITCH_LISTEN}; then they buy one at
	 * street price, once a day each. Below {@link #PITCH_SNITCH} they tell the guard. Guards and rulers always do.
	 */
	static final int PITCH_LISTEN = 30, PITCH_SNITCH = -10, PITCH_MOOD = 2, PITCH_HOTBED = 15, PITCH_SOURED = -5, STREET_JOINT = 3;
	/** How often dens are looked after (ticks); a missing fence or dice-thrower is replaced after this long. */
	static final int CHECK_TICKS = 200;
	static final long REPLACE_TICKS = 24000L;
	/** Whispers: how often each player may be told (ticks); the chance each check; how close the whisperer; no guard this close. */
	static final long WHISPER_GAP = 2400L;
	static final float WHISPER_CHANCE = 0.35F;
	static final double WHISPER_RANGE = 10.0, GUARD_EARSHOT = 12.0;
	/** Open carry: a guard this close who sees it in your hand calls it (checked this often, ticks; then not again for this long). */
	static final double CARRY_SIGHT = 10.0;
	static final int CARRY_CHECK = 20;
	static final long CARRY_GAP = 1200L;
	/** Dice: the most a throw takes, how long between throws (ticks), how far the thrower's words carry. */
	static final int MOST_WAGER = 8, DICE_COOLDOWN = 50;
	static final double DEN_SPEECH = 12.0;
	/** The trading experience a fence or a street buyer gets a deal, and how prices drift with demand. */
	static final int XP = 2;
	static final float PRICE_MULTIPLIER = 0.05F;
	/** Lines each (lang {@code underworld.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;

	/** The den's people (scoreboard tags). */
	static final String FENCE_TAG = "jaymoskingdoms.fence", DICER_TAG = "jaymoskingdoms.dicer";
	/** What a den makes. */
	static final int PMEH = 0, SHASH = 1;

	public static Item SHASH_BRICK;
	/** (Andy: "Pmeh has to be rolled like it is in kitchen") Pmeh is Jaymo's Kitchen's herb, rolled into its joints there to smoke. */
	static final Identifier KITCHEN_PMEH = Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "pmeh"),
		KITCHEN_JOINT = Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "joint"),
		KITCHEN_FRESH_PMEH = Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "fresh_pmeh"),
		KITCHEN_PMEH_GRADE = Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "pmeh_grade");
	/**
	 * (Andy: "you just have to be good at growing and drying the plant to have quality enough to sell like the dealers
	 * do") Kitchen grades Pmeh 0 scraggly, 1 fair, 2 fine, 3 prime; dealers buy fine and prime only, and sell prime.
	 */
	static final int FINE = 2, PRIME = 3;
	/** What a dealer pays for prime over fine. */
	static final float PRIME_BONUS = 1.5F;

	/** Kitchen's Pmeh (null without Jaymo's Kitchen: then every den deals in Shash). */
	static @Nullable Item pmeh() {
		return BuiltInRegistries.ITEM.getOptional(KITCHEN_PMEH).orElse(null);
	}

	/** Kitchen's Pmeh grade component (null on a Kitchen too old to grade). */
	@SuppressWarnings("unchecked")
	private static @Nullable DataComponentType<Integer> grade() {
		return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(KITCHEN_PMEH_GRADE).orElse(null);
	}

	private static @Nullable Item joint() {
		return BuiltInRegistries.ITEM.getOptional(KITCHEN_JOINT).orElse(null);
	}

	/** What a new den makes: either, or Shash if there's no Pmeh to be had. */
	private static int makes(RandomSource random) {
		return pmeh() == null ? SHASH : random.nextInt(2);
	}

	/** The lang key of what a den makes. */
	private static String good(Den d) {
		return d.makes() == PMEH ? "item.jaymoskitchen.pmeh" : "item.jaymoskingdoms.shash_brick";
	}

	/**
	 * A realm's underworld: whether it has a den at all ({@code has}: among the richest), what it makes, where its hatch is
	 * ({@code built} once dug), how hot it runs, whether it's a known hotbed, the last day counted, and its cells (once
	 * built, for {@link Busts}).
	 */
	record Den(int realm, boolean has, int makes, BlockPos hatch, boolean built, int heat, boolean known, long day, Optional<BlockPos> cell) {
		static final Codec<Den> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("realm").forGetter(Den::realm),
			Codec.BOOL.fieldOf("has").forGetter(Den::has),
			Codec.INT.fieldOf("makes").forGetter(Den::makes),
			BlockPos.CODEC.fieldOf("hatch").forGetter(Den::hatch),
			Codec.BOOL.fieldOf("built").forGetter(Den::built),
			Codec.INT.fieldOf("heat").forGetter(Den::heat),
			Codec.BOOL.fieldOf("known").forGetter(Den::known),
			Codec.LONG.fieldOf("day").forGetter(Den::day),
			BlockPos.CODEC.optionalFieldOf("cell").forGetter(Den::cell)
		).apply(i, Den::new));

		Den withHeat(int heat) {
			return new Den(this.realm, this.has, this.makes, this.hatch, this.built, Math.clamp(heat, 0, MAX_HEAT), this.known, this.day, this.cell);
		}

		Den withHatch(BlockPos hatch) {
			return new Den(this.realm, this.has, this.makes, hatch, true, this.heat, this.known, this.day, this.cell);
		}

		Den withDay(long day, int heat, boolean known) {
			return new Den(this.realm, this.has, this.makes, this.hatch, this.built, Math.clamp(heat, 0, MAX_HEAT), known, day, this.cell);
		}

		Den withCell(BlockPos cell) {
			return new Den(this.realm, this.has, this.makes, this.hatch, this.built, this.heat, this.known, this.day, Optional.of(cell));
		}

		Den opened(int makes) {
			return new Den(this.realm, true, makes, this.hatch, false, this.heat, this.known, this.day, this.cell);
		}
	}

	/** Every realm's den (realm id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, Den>> DENS = AttachmentRegistry.create(JaymosKingdoms.id("dens"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Den.CODEC)));
	/** Per player: the realms whose den they've found (ids). Saved. */
	static final AttachmentType<List<Integer>> FOUND = AttachmentRegistry.create(JaymosKingdoms.id("dens_found"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

	/** Player → when they were last whispered to; when a guard last called their open carry. */
	private static final Map<UUID, Long> WHISPERED = new HashMap<>(), CARRY_CALLED = new HashMap<>();
	/** Realm → when its den last lost its fence (or dice-thrower). */
	private static final Map<String, Long> MISSING_FENCE = new HashMap<>(), MISSING_DICER = new HashMap<>(), MISSING_BOOKIE = new HashMap<>();
	/** Fence or street buyer → the day their stock was last laid out. */
	private static final Map<UUID, Long> STOCKED = new HashMap<>();
	/** Player → when they may throw dice again; games thrown (to warm the city). */
	private static final Map<UUID, Long> DICE_NEXT = new HashMap<>();
	private static int games;
	/** Realm → the day a site for its den was last looked for and none found (looked for once a day after that). */
	private static final Map<String, Long> NO_SITE = new HashMap<>();

	private Underworld() {
	}

	static void register() {
		SHASH_BRICK = item("shash_brick", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(SHASH_BRICK);
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)
				|| !(entity instanceof Villager v) || !v.isAlive() || v.isBaby() || Downed.isDowned(v) || sp.isSpectator()) {
				return InteractionResult.PASS;
			}
			// (0.7.224) the bookie keeps the book on wars and pit fights
			if (v.entityTags().contains(Bookies.TAG)) {
				Bookies.open(level, sp, v);
				return InteractionResult.SUCCESS;
			}
			if (v.entityTags().contains(FightPit.FIGHTER_TAG) && FightPit.onCard(v.getUUID())) {
				// (between bouts the champion will talk)
				return InteractionResult.SUCCESS;
			}
			if (v.entityTags().contains(DICER_TAG) && sp.getMainHandItem().is(Items.EMERALD)) {
				dice(level, sp, v);
				return InteractionResult.SUCCESS;
			}
			if (sp.isShiftKeyDown() && contraband(sp.getMainHandItem()) && !v.entityTags().contains(FENCE_TAG) && !v.entityTags().contains(DICER_TAG)) {
				pitch(level, sp, v);
				return InteractionResult.SUCCESS;
			}
			if (v.getTradingPlayer() == null) {
				stock(level, v);
			}
			return InteractionResult.PASS;
		});
		Busts.register();
		ServerTickEvents.END_SERVER_TICK.register(Underworld::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WHISPERED.clear();
			CARRY_CALLED.clear();
			MISSING_FENCE.clear();
			MISSING_DICER.clear();
			MISSING_BOOKIE.clear();
			STOCKED.clear();
			PITCHED.clear();
			DICE_NEXT.clear();
			NO_SITE.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("underworld")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("den").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				String said = openHere(player);
				c.getSource().sendSuccess(() -> Component.translatable(said), true);
				return 1;
			}))
			.then(Commands.literal("heat").then(Commands.argument("heat", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, MAX_HEAT))
				.executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					Faction f = Theft.lawAt(player.level(), player.blockPosition());
					if (f == null || den(c.getSource().getServer(), f.id()) == null) {
						c.getSource().sendFailure(Component.translatable("commands.jaymoskingdoms.underworld.none"));
						return 0;
					}
					int heat = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "heat");
					warm(c.getSource().getServer(), f, heat - den(c.getSource().getServer(), f.id()).heat());
					c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.underworld.heat", f.name(), heat), true);
					return 1;
				})))));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	// ---- the dens ------------------------------------------------------------------------------------------------------

	static boolean contraband(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Item pmeh = pmeh(), joint = joint(), fresh = BuiltInRegistries.ITEM.getOptional(KITCHEN_FRESH_PMEH).orElse(null);
		return stack.is(SHASH_BRICK) || pmeh != null && stack.is(pmeh) || joint != null && stack.is(joint) || fresh != null && stack.is(fresh);
	}

	static @Nullable Den den(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(DENS, Map.of()).get(Integer.toString(realm));
	}

	private static void put(MinecraftServer server, Den den) {
		ServerLevel level = Factions.level(server);
		Map<String, Den> all = new HashMap<>(level.getAttachedOrElse(DENS, Map.of()));
		all.put(Integer.toString(den.realm()), den);
		level.setAttached(DENS, Map.copyOf(all));
	}

	/** {@code f}'s den, if it has one dug. */
	static @Nullable Den builtDen(MinecraftServer server, Faction f) {
		Den d = den(server, f.id());
		return d != null && d.has() && d.built() ? d : null;
	}

	/** {@code d}'s realm keeps its cells at {@code cell}. */
	static void putCell(MinecraftServer server, Den d, BlockPos cell) {
		Den now = den(server, d.realm());
		put(server, (now == null ? d : now).withCell(cell));
	}

	/** The city warms (or, a bust, cools) by {@code by}; hot enough, and it's known across the land. */
	static void warm(MinecraftServer server, Faction f, int by) {
		Den d = den(server, f.id());
		if (d == null || !d.has()) {
			return;
		}
		int heat = Math.clamp(d.heat() + by, 0, MAX_HEAT);
		boolean known = d.known() || heat >= KNOWN_HEAT;
		if (known && !d.known()) {
			Post.news(server, Component.translatable("news.jaymoskingdoms.underworld.hotbed", f.name(),
				Component.translatable(good(d))));
		}
		put(server, d.withDay(d.day(), heat, known));
	}

	/** (/underworld den) The realm the player stands in keeps a den, dug now if a site can be found. A lang key for the answer. */
	private static String openHere(ServerPlayer player) {
		ServerLevel level = player.level();
		Faction f = Theft.lawAt(level, player.blockPosition());
		if (f == null || !f.type().isRealm()) {
			return "commands.jaymoskingdoms.underworld.none";
		}
		MinecraftServer server = level.getServer();
		Den d = den(server, f.id());
		if (d == null) {
			d = new Den(f.id(), true, makes(level.getRandom()), f.seat(), false, START_HEAT, false, level.getGameTime() / 24000L, Optional.empty());
		} else if (!d.has()) {
			d = d.opened(makes(level.getRandom()));
		}
		put(server, d);
		if (!d.built()) {
			BlockPos hatch = Speakeasy.site(level, f);
			if (hatch == null) {
				return "commands.jaymoskingdoms.underworld.nosite";
			}
			Speakeasy.build(level, hatch);
			put(server, d.withHatch(hatch));
		}
		return "commands.jaymoskingdoms.underworld.made";
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CARRY_CHECK == 0) {
			for (ServerPlayer player : level.players()) {
				openCarry(level, player, now);
			}
		}
		if (now % CHECK_TICKS != 0) {
			return;
		}
		long day = now / 24000L;
		RandomSource random = level.getRandom();
		java.util.Set<Integer> rich = richest(server);
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			Den d = den(server, f.id());
			if (d == null || !d.has() && rich.contains(f.id())) {
				// one of the richest third: the criminals come where the money is
				d = d == null ? new Den(f.id(), rich.contains(f.id()), makes(random), f.seat(), false, START_HEAT, false, day, Optional.empty())
					: d.opened(makes(random));
				put(server, d);
			}
			if (!d.has()) {
				continue;
			}
			if (day > d.day()) {
				put(server, d.withDay(day, d.heat(), d.known()));
				warm(server, f, HEAT_PER_DAY * (int)Math.min(day - d.day(), 5L));
				d = den(server, f.id());
			}
			String key = Integer.toString(f.id());
			if (!d.built()) {
				Long tried = NO_SITE.get(key);
				if ((tried == null || tried < day) && level.isPositionEntityTicking(f.seat())) {
					BlockPos hatch = Speakeasy.site(level, f);
					if (hatch == null) {
						NO_SITE.put(key, day);
					} else {
						Speakeasy.build(level, hatch);
						put(server, d.withHatch(hatch));
						JaymosKingdoms.LOGGER.debug("a speakeasy dug under {} at {}", f.name(), hatch);
					}
				}
				continue;
			}
			if (level.isPositionEntityTicking(d.hatch())) {
				staff(level, f, d, now);
			}
		}
		for (ServerPlayer player : level.players()) {
			visit(level, player, now);
		}
	}

	/** The richest {@link #DEN_SHARE}th of the standing realms (at least one), by worth. */
	private static java.util.Set<Integer> richest(MinecraftServer server) {
		List<Faction> realms = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				realms.add(f);
			}
		}
		realms.sort((a, b) -> Long.compare(Wealth.worth(server, b), Wealth.worth(server, a)));
		java.util.Set<Integer> out = new java.util.HashSet<>();
		int n = (realms.size() + DEN_SHARE - 1) / DEN_SHARE;
		for (int i = 0; i < n; i++) {
			out.add(realms.get(i).id());
		}
		return out;
	}

	/** The den keeps its fence (and, warm enough, its dice-thrower): one gone is replaced after a day. */
	private static void staff(ServerLevel level, Faction f, Den d, long now) {
		AABB box = Speakeasy.room(d.hatch()).inflate(2.0);
		String key = Integer.toString(f.id());
		if (level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && v.entityTags().contains(FENCE_TAG)).isEmpty()) {
			Long since = MISSING_FENCE.get(key);
			if (since == null) {
				// (the first look after a restart, or the first ever: no waiting)
				MISSING_FENCE.put(key, now - REPLACE_TICKS);
			} else if (now - since >= REPLACE_TICKS) {
				hire(level, Speakeasy.fenceSpot(d.hatch()), FENCE_TAG);
				MISSING_FENCE.remove(key);
			}
		} else {
			MISSING_FENCE.remove(key);
		}
		// (0.7.224) the bookie, from the first: the den's the only place a war is bet on; and the pit off the den
		if (level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && v.entityTags().contains(Bookies.TAG)).isEmpty()) {
			Long since = MISSING_BOOKIE.get(key);
			if (since == null) {
				MISSING_BOOKIE.put(key, now - REPLACE_TICKS);
			} else if (now - since >= REPLACE_TICKS) {
				hire(level, Speakeasy.bookieSpot(d.hatch()), Bookies.TAG);
				MISSING_BOOKIE.remove(key);
			}
		} else {
			MISSING_BOOKIE.remove(key);
		}
		FightPit.ensure(level, f, d);
		if (d.heat() < DICE_HEAT) {
			return;
		}
		if (level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && v.entityTags().contains(DICER_TAG)).isEmpty()) {
			Long since = MISSING_DICER.get(key);
			if (since == null) {
				MISSING_DICER.put(key, now - REPLACE_TICKS);
			} else if (now - since >= REPLACE_TICKS) {
				hire(level, Speakeasy.diceSpot(d.hatch()), DICER_TAG);
				MISSING_DICER.remove(key);
			}
		} else {
			MISSING_DICER.remove(key);
		}
	}

	static @Nullable Villager hire(ServerLevel level, Vec3 at, String tag) {
		Villager v = McaCompat.newVillager(level, at);
		if (v == null) {
			return null;
		}
		v.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.EVENT, null);
		v.addTag(tag);
		v.setPersistenceRequired();
		level.addFreshEntity(v);
		return v;
	}

	/** {@code player} in a den finds it (once, told); in a hotbed's streets, a crook may whisper the way to it. */
	private static void visit(ServerLevel level, ServerPlayer player, long now) {
		if (player.isSpectator()) {
			return;
		}
		Faction f = Theft.lawAt(level, player.blockPosition());
		if (f == null || !f.type().isRealm()) {
			return;
		}
		Den d = builtDen(level.getServer(), f);
		if (d == null) {
			return;
		}
		List<Integer> found = player.getAttachedOrElse(FOUND, List.of());
		if (Speakeasy.room(d.hatch()).inflate(0.5).contains(player.position())) {
			if (!found.contains(f.id())) {
				List<Integer> more = new ArrayList<>(found);
				more.add(f.id());
				player.setAttached(FOUND, List.copyOf(more));
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.underworld.found", f.name()).withStyle(ChatFormatting.DARK_PURPLE));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.AMBIENT, 0.6F, 0.7F);
			}
			return;
		}
		if (!found.contains(f.id())) {
			whispers(level, player, f, d, now);
		}
	}

	/** A crook (in a known hotbed, any grown townsman) out of a guard's earshot whispers the way to the den. */
	private static void whispers(ServerLevel level, ServerPlayer player, Faction f, Den d, long now) {
		Long last = WHISPERED.get(player.getUUID());
		if (last != null && now - last < WHISPER_GAP || level.getRandom().nextFloat() >= WHISPER_CHANCE || Theft.wanted(player, f.id())
			|| !guards(level, f, player.getBoundingBox().inflate(GUARD_EARSHOT)).isEmpty()) {
			return;
		}
		Villager speaker = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(WHISPER_RANGE),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && v.hasLineOfSight(player))) {
			if (Crooks.isCrook(v) || d.known() && Workers.near(level, v.getBoundingBox(), w -> w.mob() == v).isEmpty()) {
				speaker = v;
				break;
			}
		}
		if (speaker == null) {
			return;
		}
		WHISPERED.put(player.getUUID(), now);
		BlockPos hatch = d.hatch();
		double dx = hatch.getX() + 0.5 - speaker.getX(), dz = hatch.getZ() + 0.5 - speaker.getZ();
		int paces = Math.max(5, (int)Math.round(Math.sqrt(dx * dx + dz * dz) / 5.0) * 5);
		Component way = Component.translatable("direction.jaymoskingdoms." + compass(dx, dz));
		speaker.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.tell(level, speaker, player, Component.translatable("underworld.jaymoskingdoms.whisper." + level.getRandom().nextInt(LINES), way, paces,
			Component.translatable(good(d))), Speech.GOLD);
	}

	/** The eight winds, from a step of {@code dx, dz} (north is -z). */
	static String compass(double dx, double dz) {
		String[] names = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
		double angle = Math.toDegrees(Math.atan2(dz, dx));
		int i = Math.floorMod((int)Math.round(angle / 45.0), 8);
		return names[i];
	}

	/** {@code f}'s guards (folk and its MCA guards, not its ruler) in {@code box}, standing. */
	static List<Worker> guards(ServerLevel level, Faction f, AABB box) {
		return Workers.near(level, box, w -> w.mob().isAlive() && w.factionId() == f.id() && !Downed.isDowned(w.mob())
			&& (w instanceof Folk folk ? folk.combatant() && !folk.leader() : w.role() == Folk.GUARD));
	}

	/** Contraband in hand in front of a guard: he calls it. */
	private static void openCarry(ServerLevel level, ServerPlayer player, long now) {
		if (player.isSpectator() || player.isCreative() || !contraband(player.getMainHandItem()) && !contraband(player.getOffhandItem())) {
			return;
		}
		Long last = CARRY_CALLED.get(player.getUUID());
		if (last != null && now - last < CARRY_GAP) {
			return;
		}
		Faction f = Theft.lawAt(level, player.blockPosition());
		if (f == null || !f.type().isRealm() || Theft.wanted(player, f.id())) {
			return;
		}
		Den d = builtDen(level.getServer(), f);
		if (d != null && Speakeasy.room(d.hatch()).contains(player.position())) {
			return;
		}
		for (Worker w : guards(level, f, player.getBoundingBox().inflate(CARRY_SIGHT))) {
			LivingEntity guard = w.mob();
			if (guard.hasLineOfSight(player)) {
				CARRY_CALLED.put(player.getUUID(), now);
				Theft.commit(player, level, f, Theft.Crime.CONTRABAND, null, false, List.of(guard));
				Busts.charge(player, f, "contraband");
				return;
			}
		}
	}

	// ---- trade ---------------------------------------------------------------------------------------------------------

	/** Before the trading screen opens: a fence lays out the day's stock; a hotbed's crook in the street, what he'd buy. */
	private static void stock(ServerLevel level, Villager v) {
		Faction f = Theft.lawAt(level, v.blockPosition());
		if (f == null || !f.type().isRealm()) {
			return;
		}
		Den d = builtDen(level.getServer(), f);
		if (d == null) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		Long last = STOCKED.get(v.getUUID());
		if (v.entityTags().contains(FENCE_TAG)) {
			if (last != null && last >= day) {
				return;
			}
			STOCKED.put(v.getUUID(), day);
			MerchantOffers offers = v.getOffers();
			offers.clear();
			boolean home = d.makes() == PMEH;
			Item pmeh = pmeh();
			if (pmeh != null) {
				offers.add(sellPrime(pmeh, home ? PMEH_SELL_HOME : PMEH_SELL_AWAY, home ? HOME_STOCK : AWAY_STOCK));
			}
			offers.add(sell(SHASH_BRICK, home ? SHASH_SELL_AWAY : SHASH_SELL_HOME, home ? AWAY_STOCK : HOME_STOCK));
			if (pmeh != null) {
				buyGraded(offers, pmeh, home ? PMEH_BUY_HOME : PMEH_BUY_AWAY, BUY_STOCK);
			}
			offers.add(buy(SHASH_BRICK, home ? SHASH_BUY_AWAY : SHASH_BUY_HOME, BUY_STOCK));
			return;
		}
		if (!d.known() || !Crooks.isCrook(v) || last != null) {
			return;
		}
		// a hotbed's crook buys in the street (offers kept once made, as with whale goods)
		STOCKED.put(v.getUUID(), day);
		MerchantOffers offers = v.getOffers();
		Item pmeh = pmeh();
		if (pmeh != null) {
			buyGraded(offers, pmeh, STREET_PMEH, STREET_STOCK);
		}
		offers.add(buy(SHASH_BRICK, STREET_SHASH, STREET_STOCK));
	}

	private static MerchantOffer sell(Item item, int emeralds, int uses) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item), uses, XP, PRICE_MULTIPLIER);
	}

	/** Villager → the day they were last offered contraband (once a day each). */
	private static final Map<UUID, Long> PITCHED = new HashMap<>();

	/** How a personality takes to being offered contraband. */
	static int leaning(String personality) {
		return switch (personality) {
			case "relaxed", "odd", "lazy", "playful", "flirty", "extroverted", "witty" -> 15;
			case "greedy", "confident" -> 10;
			case "anxious", "sensitive", "shy", "peaceful" -> -10;
			case "crabby", "grumpy", "stubborn" -> -15;
			default -> 0;
		};
	}

	/**
	 * What a receptive villager pays for {@code stack} in the street (-1: not dried yet). (Andy: "not everyone knows the
	 * difference") Townsfolk can't tell grades apart, so any dried Pmeh fetches the same; only dealers check the grade.
	 */
	private static int streetPrice(ItemStack stack) {
		if (stack.is(SHASH_BRICK)) {
			return STREET_SHASH;
		}
		Item pmeh = pmeh(), joint = joint();
		if (joint != null && stack.is(joint)) {
			return STREET_JOINT;
		}
		return pmeh != null && stack.is(pmeh) ? STREET_PMEH : -1;
	}

	/** {@code player} offers {@code v} the contraband in hand: a sale, a no, or a word to the guard. */
	private static void pitch(ServerLevel level, ServerPlayer player, Villager v) {
		Faction f = Theft.lawAt(level, v.blockPosition());
		boolean realm = f != null && f.type().isRealm();
		boolean law = Downed.isGuard(v) || realm && f.leader().isPresent() && f.leader().get().equals(v.getUUID());
		long day = level.getGameTime() / 24000L;
		Long last = PITCHED.get(v.getUUID());
		if (!law && last != null && last >= day) {
			Speech.tell(level, v, player, Component.translatable("underworld.jaymoskingdoms.pitch.again"), Speech.GOLD);
			return;
		}
		PITCHED.put(v.getUUID(), day);
		Den d = realm ? den(level.getServer(), f.id()) : null;
		int score = law ? Integer.MIN_VALUE : McaCompat.heartsOf(v, player) + McaCompat.mood(v) * PITCH_MOOD + leaning(McaCompat.personality(v))
			+ (d != null && d.known() ? PITCH_HOTBED : 0);
		if (score >= PITCH_LISTEN) {
			ItemStack stack = player.getMainHandItem();
			int price = streetPrice(stack);
			if (price < 0) {
				Speech.tell(level, v, player, Component.translatable("underworld.jaymoskingdoms.pitch.fresh"), Speech.GOLD);
				PITCHED.remove(v.getUUID());
				return;
			}
			stack.consume(1, player);
			player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, price), Prediction.SERVER_ONLY);
			Speech.tell(level, v, player, Component.translatable("underworld.jaymoskingdoms.pitch.yes." + level.getRandom().nextInt(LINES)), Speech.GOLD);
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.0F);
			if (realm) {
				warm(level.getServer(), f, HEAT_PER_SALE);
				List<LivingEntity> seen = new ArrayList<>(Theft.witnesses(level, f, player));
				seen.removeIf(e -> e == v || Crooks.isCrook(e));
				if (!seen.isEmpty()) {
					Theft.commit(player, level, f, Theft.Crime.DEALING, null, false, seen);
					Busts.charge(player, f, "dealing");
				}
			}
			return;
		}
		level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6F, 1.0F);
		McaCompat.hearts(v, player, PITCH_SOURED);
		if (score >= PITCH_SNITCH || !realm) {
			Speech.tell(level, v, player, Component.translatable("underworld.jaymoskingdoms.pitch.no." + level.getRandom().nextInt(LINES)), Speech.WHITE);
			return;
		}
		// they tell the guard: a dealing charge as if a guard had seen it
		Speech.tell(level, v, player, Component.translatable("underworld.jaymoskingdoms.pitch.snitch." + level.getRandom().nextInt(LINES)), Speech.RED);
		Theft.commit(player, level, f, Theft.Crime.DEALING, null, false, List.of(v));
		Busts.charge(player, f, "dealing");
	}

	/** Prime Pmeh for sale (plain, if Kitchen doesn't grade). */
	private static MerchantOffer sellPrime(Item pmeh, int emeralds, int uses) {
		ItemStack stack = new ItemStack(pmeh);
		DataComponentType<Integer> grade = grade();
		if (grade != null) {
			stack.set(grade, PRIME);
		}
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), stack, uses, XP, PRICE_MULTIPLIER);
	}

	/** Buys fine Pmeh at {@code emeralds} and prime for more; nothing worse (any Pmeh if Kitchen doesn't grade). */
	private static void buyGraded(MerchantOffers offers, Item pmeh, int emeralds, int uses) {
		DataComponentType<Integer> grade = grade();
		if (grade == null) {
			offers.add(buy(pmeh, emeralds, uses));
			return;
		}
		for (int g : new int[] {FINE, PRIME}) {
			int pay = g == PRIME ? Math.round(emeralds * PRIME_BONUS) : emeralds;
			ItemCost cost = new ItemCost(pmeh.builtInRegistryHolder(), 1, DataComponentExactPredicate.expect(grade, g));
			offers.add(new MerchantOffer(cost, new ItemStack(Items.EMERALD, pay), uses, XP, PRICE_MULTIPLIER));
		}
	}

	private static MerchantOffer buy(Item item, int emeralds, int uses) {
		return new MerchantOffer(new ItemCost(item, 1), new ItemStack(Items.EMERALD, emeralds), uses, XP, PRICE_MULTIPLIER);
	}

	/** A den's fence takes no toll for the realm (it never sees the coin). */
	public static boolean blackMarket(Entity trader) {
		return trader.entityTags().contains(FENCE_TAG);
	}

	/**
	 * (From the trade mixin) a trade went through. Contraband warms the city; dealt outside a den, it's a crime for whoever
	 * of the realm's people sees it (the buyer himself won't tell).
	 */
	public static void traded(Villager v, MerchantOffer offer) {
		if (!(v.level() instanceof ServerLevel level) || !(v.getTradingPlayer() instanceof ServerPlayer player)) {
			return;
		}
		if (!contraband(offer.getResult()) && !contraband(offer.getCostA()) && !contraband(offer.getCostB())) {
			return;
		}
		Faction f = Theft.lawAt(level, v.blockPosition());
		if (f == null || !f.type().isRealm()) {
			return;
		}
		warm(level.getServer(), f, HEAT_PER_SALE);
		Den d = builtDen(level.getServer(), f);
		if (v.entityTags().contains(FENCE_TAG) && d != null && Speakeasy.room(d.hatch()).inflate(1.0).contains(v.position())) {
			return;
		}
		List<LivingEntity> seen = new ArrayList<>(Theft.witnesses(level, f, player));
		seen.removeIf(e -> e == v || Crooks.isCrook(e));
		if (seen.isEmpty()) {
			return;
		}
		Theft.commit(player, level, f, Theft.Crime.DEALING, null, false, seen);
		Busts.charge(player, f, "dealing");
	}

	// ---- dice ----------------------------------------------------------------------------------------------------------

	/** A throw against the house: two dice each, the higher total takes the pot; a tie, the stakes back. */
	private static void dice(ServerLevel level, ServerPlayer player, Villager dicer) {
		long now = level.getGameTime();
		Long next = DICE_NEXT.get(player.getUUID());
		if (next != null && now < next) {
			return;
		}
		DICE_NEXT.put(player.getUUID(), now + DICE_COOLDOWN);
		ItemStack held = player.getMainHandItem();
		int wager = Math.min(held.getCount(), MOST_WAGER);
		held.shrink(wager);
		RandomSource random = level.getRandom();
		int a = 1 + random.nextInt(6), b = 1 + random.nextInt(6), c = 1 + random.nextInt(6), e = 1 + random.nextInt(6);
		int yours = a + b, theirs = c + e;
		dicer.getLookControl().setLookAt(player, 30.0F, 30.0F);
		level.playSound(null, dicer.getX(), dicer.getY(), dicer.getZ(), SoundEvents.BONE_BLOCK_HIT, SoundSource.NEUTRAL, 0.8F, 1.6F);
		String outcome = yours > theirs ? "win" : yours < theirs ? "lose" : "push";
		Speech.tell(level, dicer, player, Component.translatable("underworld.jaymoskingdoms.dice." + outcome + "." + random.nextInt(LINES), a, b, c, e, wager),
			yours > theirs ? Speech.GOLD : yours < theirs ? Speech.RED : Speech.WHITE);
		int back = yours > theirs ? wager * 2 : yours == theirs ? wager : 0;
		if (back > 0) {
			player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, back), Prediction.SERVER_ONLY);
		}
		if (++games % GAMES_PER_HEAT == 0) {
			Faction f = Theft.lawAt(level, dicer.blockPosition());
			if (f != null) {
				warm(level.getServer(), f, 1);
			}
		}
	}
}
