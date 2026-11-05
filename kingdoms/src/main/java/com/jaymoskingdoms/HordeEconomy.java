package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes patch 3, a greenskin camp's own economy ({@code docs/Hordes-Plan.md}), replacing the flat daily takings and
 * {@code Hordes}' yield percent.
 * <ul>
 * <li><b>Jobs.</b> The camp's working folk (its grunts, or its goblins; the rangers and slingers keep watch) take jobs from a
 * fixed rota ({@link #ORC_ROTA}, {@link #GOBLIN_ROTA}): hunter, forager (a goblin's a mushroom farmer), woodcutter, miner,
 * smith, tanner, brewer (goblins only) and trader. Each job brings in its goods a day ({@link #goods}) and the smiths,
 * tanners, brewers and traders earn bent ingots ({@link #wage}) for the chief's hoard. Goblins work better than orcs
 * ({@link #GOBLIN_PERCENT}), and a bigger camp's shops and forges better than a small one's ({@link #TIER_PERCENT}).</li>
 * <li><b>Food.</b> Everyone eats {@link #MEAL} each day, an ogre {@link #OGRE_MEALS}. A camp that can't feed all of them is
 * <i>hungry</i>: it grows no folk, raises no building and climbs no tier ({@link #hungry}), and its war bands raid more
 * often ({@link Raids}); a camp with more than {@link #PLENTY_OVER} to spare is in <i>plenty</i> and grows faster
 * ({@link #age}).</li>
 * <li><b>The hoard.</b> The chief's hoard ({@link Hordes.Horde#hoard}) is counted in bent worth ({@link BentIngots}): wages,
 * trade ({@link GreenMarket}), caravans ({@link Caravans}), tribute and gifts ({@link Tribes}) go in; walls, band buildings
 * and tiers are paid out of it ({@link Hordes}).</li>
 * </ul>
 * Nothing here runs unless the camp's store is loaded, as before.
 */
final class HordeEconomy {
	// ================================================================================================================
	// Tuning
	/** The jobs. */
	enum Job {
		HUNTER, FORAGER, WOODCUTTER, MINER, SMITH, TANNER, BREWER, TRADER
	}

	/** Who does what: the working folk take these jobs in turn, first to last, round again. */
	static final List<Job> ORC_ROTA = List.of(Job.HUNTER, Job.WOODCUTTER, Job.FORAGER, Job.MINER, Job.SMITH, Job.HUNTER, Job.TANNER, Job.WOODCUTTER,
		Job.MINER, Job.TRADER, Job.FORAGER, Job.HUNTER);
	static final List<Job> GOBLIN_ROTA = List.of(Job.FORAGER, Job.WOODCUTTER, Job.HUNTER, Job.BREWER, Job.MINER, Job.SMITH, Job.FORAGER, Job.TRADER,
		Job.TANNER, Job.WOODCUTTER, Job.MINER, Job.BREWER);
	/** How well they work, in percent of an orc's (goblins are quicker and cleverer), and by the camp's tier (shops, forges, pens). */
	static final int ORC_PERCENT = 100, GOBLIN_PERCENT = 140;
	static final int[] TIER_PERCENT = {100, 110, 120, 130};
	/** What a job earns for the hoard a day, in bent worth, at 100%: smith, tanner, brewer, trader. */
	static final int SMITH_WAGE = 3, TANNER_WAGE = 2, BREWER_WAGE = 4, TRADER_WAGE = 4;
	/** Food eaten a day by each of the camp's folk, and by each ogre in it. */
	static final int MEAL = 1, OGRE_MEALS = 2;
	/** Food a day at 100%: a hunter's meat, a forager's berries, a mushroom farmer's mushrooms (brown and red). */
	static final int HUNTER_MEAT = 5, FORAGER_BERRIES = 5, FARMER_BROWN = 3, FARMER_RED = 2;
	/** A camp with this much more food than the day's meals is in plenty; each such day it ages this many days sooner (in half-days). */
	static final int PLENTY_OVER = 24, PLENTY_HALF_DAYS = 1, PLENTY_BIRTHS = 2;
	/** A new camp's hoard, in bent worth. */
	static final int START_HOARD = 12;
	/** The most a hoard holds (so a long-running camp's number stays sane). */
	static final long HOARD_MOST = 100_000L;
	// ================================================================================================================

	/** One camp's books: days it has gone hungry running, days it has been in plenty, what its jobs earned the last day. */
	record Book(int hunger, int plenty, int income, int eaten) {
		static final Book EMPTY = new Book(0, 0, 0, 0);
		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("hunger", 0).forGetter(Book::hunger),
				Codec.INT.optionalFieldOf("plenty", 0).forGetter(Book::plenty),
				Codec.INT.optionalFieldOf("income", 0).forGetter(Book::income),
				Codec.INT.optionalFieldOf("eaten", 0).forGetter(Book::eaten))
			.apply(i, Book::new));
	}

	/** On the Overworld: every greenskin camp's books (camp id → it). Saved. */
	static final AttachmentType<Map<String, Book>> BOOKS = AttachmentRegistry.create(JaymosKingdoms.id("horde_books"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Book.CODEC)));

	private HordeEconomy() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("horde books attachment: {}", BOOKS);
	}

	// ---- the books -------------------------------------------------------------------------------------------------------

	static Book book(MinecraftServer server, int id) {
		return Factions.level(server).getAttachedOrElse(BOOKS, Map.of()).getOrDefault(Integer.toString(id), Book.EMPTY);
	}

	private static void put(MinecraftServer server, int id, Book book) {
		Map<String, Book> map = new HashMap<>(Factions.level(server).getAttachedOrElse(BOOKS, Map.of()));
		map.put(Integer.toString(id), book);
		Factions.level(server).setAttached(BOOKS, Map.copyOf(map));
	}

	/** The camp couldn't feed all its folk at its last meal. */
	static boolean hungry(MinecraftServer server, int id) {
		return book(server, id).hunger() > 0;
	}

	/** How many folk a camp may add in a day: one, or {@link #PLENTY_BIRTHS} while it has more than {@link #PLENTY_OVER} food in store. */
	static int births(ServerLevel level, Faction camp) {
		return Storage.loaded(level, camp) && Storage.count(level, camp, HordeEconomy::eats) >= PLENTY_OVER ? PLENTY_BIRTHS : 1;
	}

	/** The camp's age in days as its growth counts it: {@code grown} real days and a share of its days of plenty. */
	static int age(MinecraftServer server, int id, int grown) {
		return grown + book(server, id).plenty() * PLENTY_HALF_DAYS / 2;
	}

	// ---- the hoard -------------------------------------------------------------------------------------------------------

	/** Camp {@code id}'s hoard, in bent worth. */
	static long hoard(MinecraftServer server, int id) {
		return Hordes.horde(server, id).hoard();
	}

	/** {@code worth} into camp {@code id}'s hoard (nothing if it isn't a greenskin camp). */
	static void deposit(MinecraftServer server, int id, long worth) {
		if (worth > 0L && Hordes.greenskin(server, id)) {
			Hordes.setHoard(server, id, Math.min(HOARD_MOST, hoard(server, id) + worth));
		}
	}

	/** Takes {@code worth} from camp {@code id}'s hoard: false (and nothing taken) if it holds less. */
	static boolean spend(MinecraftServer server, int id, long worth) {
		long have = hoard(server, id);
		if (have < worth) {
			return false;
		}
		Hordes.setHoard(server, id, have - worth);
		return true;
	}

	/** The camp where {@code tribe}'s tribute and trade are kept: its king's seat, else any camp of it. */
	static @Nullable Faction treasury(MinecraftServer server, String tribe) {
		Faction seat = Tribes.seat(server, tribe);
		if (seat != null) {
			return seat;
		}
		List<Faction> camps = Tribes.camps(server, tribe);
		return camps.isEmpty() ? null : camps.getFirst();
	}

	// ---- jobs ------------------------------------------------------------------------------------------------------------

	/** The camp's working folk: its grunts (an orc camp) or goblins; the rangers and slingers keep watch and have no job. */
	static int workers(ServerLevel level, Faction camp, Camps.CampData data, boolean orc) {
		// ((0.7.490) an orc camp's goblin slaves do its work too, but are no folk of the camp: they are counted from the
		// world, loaded ones only, and never in its holdings or its cap)
		return camp.holdings().count(orc ? Orcs.GRUNT : Goblins.GOBLIN) + (orc ? Slaves.count(level, camp.id(), data.center()) : 0);
	}

	/** {@code workers} folk (and slaves) shared out among the jobs by the rota. */
	static Map<Job, Integer> assign(int workers, boolean orc) {
		List<Job> rota = orc ? ORC_ROTA : GOBLIN_ROTA;
		Map<Job, Integer> out = new EnumMap<>(Job.class);
		for (int i = 0; i < workers; i++) {
			out.merge(rota.get(i % rota.size()), 1, Integer::sum);
		}
		return out;
	}

	/** How well the camp's folk work, in percent: their kind's, and their tier's. */
	static int percent(MinecraftServer server, int id, boolean orc) {
		return (orc ? ORC_PERCENT : GOBLIN_PERCENT) * TIER_PERCENT[Hordes.tier(server, id) - 1] / 100;
	}

	/** What one working folk of {@code job} brings in a day at 100%, in goods. */
	static Map<Item, Integer> goods(Job job, boolean orc) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		switch (job) {
			case HUNTER -> {
				out.put(orc ? Items.COOKED_BEEF : Items.COOKED_CHICKEN, HUNTER_MEAT);
				out.put(orc ? Items.WOOL.pick(DyeColor.BLACK) : Items.BONE, 1);
				if (orc) {
					out.put(Items.BONE, 1);
				}
			}
			case FORAGER -> {
				if (orc) {
					out.put(Items.SWEET_BERRIES, FORAGER_BERRIES);
				} else {
					out.put(Items.BROWN_MUSHROOM, FARMER_BROWN);
					out.put(Items.RED_MUSHROOM, FARMER_RED);
				}
			}
			case WOODCUTTER -> out.put(orc ? Items.DARK_OAK_LOG : Items.SPRUCE_LOG, 4);
			case MINER -> {
				if (orc) {
					out.put(Items.IRON_NUGGET, 4);
				} else {
					out.put(Items.GOLD_NUGGET, 2);
					out.put(Items.MUD_BRICKS, 4);
				}
			}
			case SMITH -> out.put(Items.ARROW, orc ? 6 : 5);
			case TANNER -> {
				out.put(Items.LEATHER, 2);
				if (orc) {
					out.put(Items.WOOL.pick(DyeColor.BLACK), 2);
				}
			}
			default -> {
			}
		}
		return out;
	}

	/** What one working folk of {@code job} earns the hoard a day at 100%, in bent worth. */
	static int wage(Job job) {
		return switch (job) {
			case SMITH -> SMITH_WAGE;
			case TANNER -> TANNER_WAGE;
			case BREWER -> BREWER_WAGE;
			case TRADER -> TRADER_WAGE;
			default -> 0;
		};
	}

	/** {@code amount} at {@code percent}, rounded to the nearest (a lone folk at 100% brings in exactly {@code amount}). */
	private static int scaled(int amount, int percent) {
		return (amount * percent + 50) / 100;
	}

	/** Whether the camp's folk eat {@code stack}: any food, and a goblin's mushrooms. */
	static boolean eats(ItemStack stack) {
		return Storage.isFood(stack) || stack.is(Items.BROWN_MUSHROOM) || stack.is(Items.RED_MUSHROOM);
	}

	/** How many meals the camp's folk need a day ((0.7.490) only the living eat: a dead one's place waiting to be filled doesn't). */
	static int meals(ServerLevel level, Faction camp, Camps.CampData data, boolean orc) {
		int folk = orc ? camp.holdings().count(Orcs.GRUNT) + camp.holdings().count(Orcs.RANGER) + 2 + Slaves.count(level, camp.id(), data.center())
			: camp.holdings().count(Goblins.GOBLIN) + camp.holdings().count(Goblins.SLINGER) + 1;
		int ogres = orc ? OgreHolds.haulers(level, camp.id(), data.center()).size() : 0;
		return folk * MEAL + ogres * OGRE_MEALS;
	}

	// ---- each day --------------------------------------------------------------------------------------------------------

	/** {@code days} of the camp's work and meals (in place of its old flat takings): goods in its store, wages in its hoard, hunger or plenty in its books. */
	static void day(ServerLevel level, Faction camp, boolean orc, int days) {
		MinecraftServer server = level.getServer();
		if (!Storage.loaded(level, camp)) {
			return;
		}
		// ((0.7.490) now and then it leaves a war journal in the store)
		LoreBooks.camp(level, camp, orc, days);
		for (int d = 0; d < days; d++) {
			Faction now = Factions.get(server, camp.id());
			Camps.CampData data = Camps.data(server, camp.id());
			if (now == null || data == null) {
				return;
			}
			int percent = percent(server, camp.id(), orc);
			Map<Item, Integer> bring = new LinkedHashMap<>();
			long wages = 0L;
			for (var e : assign(workers(level, now, data, orc), orc).entrySet()) {
				for (var g : goods(e.getKey(), orc).entrySet()) {
					bring.merge(g.getKey(), scaled(g.getValue() * e.getValue(), percent), Integer::sum);
				}
				wages += scaled(wage(e.getKey()) * e.getValue(), percent);
			}
			for (var e : bring.entrySet()) {
				Storage.put(level, now, e.getKey(), e.getValue());
			}
			deposit(server, camp.id(), wages);
			// everyone eats
			int need = meals(level, now, data, orc);
			int eaten = 0;
			while (eaten < need) {
				ItemStack bite = Storage.take(level, now, HordeEconomy::eats, need - eaten);
				if (bite.isEmpty()) {
					break;
				}
				eaten += bite.getCount();
			}
			int left = Storage.count(level, now, HordeEconomy::eats);
			Book old = book(server, camp.id());
			boolean fed = eaten >= need;
			put(server, camp.id(), new Book(fed ? 0 : old.hunger() + 1, fed && left >= PLENTY_OVER ? old.plenty() + 1 : old.plenty(), (int)wages, eaten));
			if (!fed && old.hunger() == 0) {
				JaymosKingdoms.LOGGER.info("{} has gone hungry ({} of {} meals)", camp.name(), eaten, need);
			}
		}
	}

	/** Plain text for the operator's command: the camp's jobs, food and hoard. */
	static String describe(ServerLevel level, Faction camp, Camps.CampData data) {
		MinecraftServer server = level.getServer();
		boolean orc = data.orc();
		Book book = book(server, camp.id());
		StringBuilder jobs = new StringBuilder();
		for (var e : assign(workers(level, camp, data, orc), orc).entrySet()) {
			jobs.append(jobs.isEmpty() ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey().name().toLowerCase(java.util.Locale.ROOT));
		}
		int food = Storage.loaded(level, camp) ? Storage.count(level, camp, HordeEconomy::eats) : -1;
		return "jobs: " + (jobs.isEmpty() ? "none" : jobs) + " (" + percent(server, camp.id(), orc) + "%)\nfood: " + (food < 0 ? "?" : Integer.toString(food))
			+ " (" + meals(level, camp, data, orc) + " meals a day)" + (book.hunger() > 0 ? ", hungry " + book.hunger() + " days" : book.plenty() > 0 ? ", plenty "
			+ book.plenty() + " days" : "") + "\nhoard: " + hoard(server, camp.id()) + " bent worth (+" + book.income() + " last day)";
	}
}
