package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.491) Hordes patch 4: the greenskin king's city ({@code docs/Hordes-Plan.md}). A tribe's king seat that has grown to
 * tier 4 ({@link Hordes#realm}) already has its great hall, its land and its vassal camps; its band now also raises an
 * arena and a shaman's (goblins: brewer-witch's) tower ({@link HordeBuildings#arena}, {@link HordeBuildings#shamanTower}).
 * On top of that its king acts as a realm does, in the horde's own way:
 * <ul>
 * <li><b>The warpath.</b> Every {@link #WARPATH_EVERY_DAYS} days the Warlord (or Goblin King) takes his whole host on the
 * warpath against the nearest settlement within {@link #WARPATH_REACH}: a human realm or village, or a camp of the rival
 * brood. It's told to everyone. For {@link #WARPATH_DAYS} days the realm and all its vassal camps raid that one target,
 * far more often ({@link #RAID_CHANCE}, a quarter of the usual wait) and in bigger bands ({@link #PARTY_MAX}), and the
 * goblins march too, hungry or not ({@link Raids}).</li>
 * <li><b>The wanted notice.</b> The settlement it marches on posts the king as wanted on its bounty board
 * ({@link Bounties#warlord}), {@link #WANTED_PRICE} for his head.</li>
 * <li><b>Tribute pacts.</b> Each day one realm within {@link #TRIBUTE_RANGE} whose army is smaller than
 * {@link #COWED_SHARE} of the horde's fighters buys its peace (at most {@link #MOST_COWED} at once): it pays {@link #TRIBUTE_EMERALDS} a day out of its
 * treasury into the king's hoard (as bent worth, {@link TribePacts#BENT_PER_EMERALD} to the emerald) and the horde's camps
 * leave it be ({@link #spared}). A realm that can't pay is struck from the pact, and the horde goes on the warpath
 * soon after.</li>
 * <li><b>The horde oath.</b> A half-orc may swear to an orc realm, and one raised among goblins to a goblin realm, with
 * {@code /kingdom pledge <its name>}; anyone else once the tribe's favour reaches {@link TribePacts#ALLY_AT}. The tribe's
 * folk spare the sworn, they're told where each warpath goes, and they get {@link #OATH_CUT} bent copper of every day's
 * tribute. {@code /kingdom leave} forswears it.</li>
 * </ul>
 * Saved in {@link #CITIES} (realm id → {@link City}); nothing here runs unless the realm stands.
 */
final class HordeCity {
	// ================================================================================================================
	// Tuning
	/** How often a realm goes on the warpath (days), and how long it lasts (days). */
	static final int WARPATH_EVERY_DAYS = 6, WARPATH_DAYS = 2;
	/** How far (blocks) a warpath reaches for its target. */
	static final double WARPATH_REACH = 900.0;
	/** On the warpath: the chance a camp of the host raids at each look ({@link Raids#CHECK_TICKS}), and its band's largest size and extra raiders. */
	static final float RAID_CHANCE = 0.5F;
	static final int PARTY_MAX = 14, PARTY_EXTRA = 3;
	/** The price on the king's head that the warpath's target posts. */
	static final int WANTED_PRICE = 80;
	/** How near (blocks) a realm must be to be cowed into tribute, and how weak: its army under this share of the horde's fighters. */
	static final double TRIBUTE_RANGE = 640.0;
	static final float COWED_SHARE = 0.25F;
	/** At most this many realms pay one horde at once (the rest it keeps warring on). */
	static final int MOST_COWED = 3;
	/** Emeralds a day a cowed realm pays. */
	static final int TRIBUTE_EMERALDS = 3;
	/** Bent copper each sworn player online gets on a day tribute comes in. */
	static final int OATH_CUT = 2;
	// ================================================================================================================

	/**
	 * A realm's city state: the warpath's target (-1 none) and when it ends, when the next one may start (game time), the
	 * realms paying it tribute, and the players sworn to it.
	 */
	record City(int target, long until, long next, List<Integer> cowed, List<String> sworn) {
		static final City NONE = new City(-1, 0L, 0L, List.of(), List.of());
		static final Codec<City> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("target", -1).forGetter(City::target),
				Codec.LONG.optionalFieldOf("until", 0L).forGetter(City::until),
				Codec.LONG.optionalFieldOf("next", 0L).forGetter(City::next),
				Codec.INT.listOf().optionalFieldOf("cowed", List.of()).forGetter(City::cowed),
				Codec.STRING.listOf().optionalFieldOf("sworn", List.of()).forGetter(City::sworn))
			.apply(i, City::new));
	}

	/** On the Overworld: every greenskin realm's city state (realm id → it). Saved. */
	static final AttachmentType<Map<String, City>> CITIES = AttachmentRegistry.create(JaymosKingdoms.id("horde_cities"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, City.CODEC)));

	private HordeCity() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("horde cities attachment: {}", CITIES);
	}

	// ---- state ---------------------------------------------------------------------------------------------------------

	static City city(MinecraftServer server, int id) {
		return Factions.level(server).getAttachedOrElse(CITIES, Map.of()).getOrDefault(Integer.toString(id), City.NONE);
	}

	private static Map<String, City> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CITIES, Map.of());
	}

	private static void put(MinecraftServer server, int id, @Nullable City city) {
		Map<String, City> map = new HashMap<>(all(server));
		if (city == null) {
			map.remove(Integer.toString(id));
		} else {
			map.put(Integer.toString(id), city);
		}
		Factions.level(server).setAttached(CITIES, Map.copyOf(map));
	}

	/** The greenskin realm {@code camp} answers to: itself if it's one, its overlord if that's one, or null. */
	static @Nullable Faction realmOf(MinecraftServer server, Faction camp) {
		if (Hordes.realm(server, camp.id())) {
			return camp;
		}
		if (camp.overlord() >= 0 && Hordes.realm(server, camp.overlord())) {
			Faction lord = Factions.get(server, camp.overlord());
			return lord == null || lord.fallen() ? null : lord;
		}
		return null;
	}

	/** Realm {@code id} fell, or dropped below tier 4: its warpath, pacts and oaths are gone. */
	static void fell(MinecraftServer server, int id) {
		if (all(server).containsKey(Integer.toString(id))) {
			put(server, id, null);
		}
	}

	// ---- each day ------------------------------------------------------------------------------------------------------

	/** A greenskin realm's day ({@link Hordes#day}): tribute comes in, a new realm may be cowed, and the warpath ends or begins. */
	static void day(ServerLevel level, Faction realm) {
		MinecraftServer server = level.getServer();
		City c = city(server, realm.id());
		long now = level.getGameTime();
		c = tribute(level, realm, c, now);
		c = cow(level, realm, c);
		if (c.target() >= 0 && now >= c.until()) {
			c = new City(-1, 0L, c.next(), c.cowed(), c.sworn());
		}
		if (c.target() < 0 && now >= c.next()) {
			Faction target = pick(level, realm, c);
			if (target != null) {
				c = new City(target.id(), now + WARPATH_DAYS * Camps.DAY_TICKS, now + WARPATH_EVERY_DAYS * Camps.DAY_TICKS, c.cowed(), c.sworn());
				march(level, realm, target, c);
			}
		}
		put(server, realm.id(), c);
	}

	/** Each cowed realm pays its tribute (or is struck from the pact); the sworn get their cut. */
	private static City tribute(ServerLevel level, Faction realm, City c, long now) {
		MinecraftServer server = level.getServer();
		List<Integer> still = new ArrayList<>();
		int paid = 0;
		long next = c.next();
		for (int id : c.cowed()) {
			Faction f = Factions.get(server, id);
			if (f == null || f.fallen()) {
				continue;
			}
			if (f.treasury() >= TRIBUTE_EMERALDS) {
				Factions.update(server, id, x -> x.withTreasury(Math.max(0L, x.treasury() - TRIBUTE_EMERALDS)));
				HordeEconomy.deposit(server, realm.id(), (long)TRIBUTE_EMERALDS * TribePacts.BENT_PER_EMERALD);
				still.add(id);
				paid++;
			} else {
				KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.horde_tribute.stopped", f.name(), realm.name())
					.withStyle(ChatFormatting.RED));
				// (a realm that stops paying is the next to be marched on)
				next = Math.min(next, now);
			}
		}
		if (paid > 0) {
			for (String s : c.sworn()) {
				ServerPlayer p = player(server, s);
				if (p != null) {
					ItemStack cut = new ItemStack(BentIngots.BENT_COPPER, OATH_CUT * paid);
					if (!p.getInventory().add(cut)) {
						p.drop(cut, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
				}
			}
		}
		return new City(c.target(), c.until(), next, List.copyOf(still), c.sworn());
	}

	/** One realm near enough and weak enough buys its peace with tribute. */
	private static City cow(ServerLevel level, Faction realm, City c) {
		MinecraftServer server = level.getServer();
		if (c.cowed().size() >= MOST_COWED) {
			return c;
		}
		int host = host(server, realm);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || c.cowed().contains(f.id()) || f.id() == c.target()
				|| Raids.horizontal(f.seat(), realm.seat()) > TRIBUTE_RANGE * TRIBUTE_RANGE) {
				continue;
			}
			int principal = f.overlord() >= 0 ? f.overlord() : f.id();
			if (Wars.army(server, principal) >= host * COWED_SHARE || f.treasury() < TRIBUTE_EMERALDS) {
				continue;
			}
			List<Integer> cowed = new ArrayList<>(c.cowed());
			cowed.add(f.id());
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.horde_tribute", f.name(), realm.name())
				.withStyle(ChatFormatting.GOLD));
			JaymosKingdoms.LOGGER.info("{} pays tribute to {}", f.name(), realm.name());
			return new City(c.target(), c.until(), c.next(), List.copyOf(cowed), c.sworn());
		}
		return c;
	}

	/** The horde's fighters: the realm's and its standing vassal camps'. */
	private static int host(MinecraftServer server, Faction realm) {
		int host = Hordes.folk(realm, Camps.orc(server, realm.id()));
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (f.overlord() == realm.id() && !f.fallen()) {
				host += Hordes.folk(f, Camps.orc(server, f.id()));
			}
		}
		return host;
	}

	/** The warpath's target: the nearest standing settlement in reach that isn't a pact's, a spared haven, or another greenskin camp (bar the rival brood's). */
	private static @Nullable Faction pick(ServerLevel level, Faction realm, City c) {
		MinecraftServer server = level.getServer();
		Faction best = null;
		double bestDist = WARPATH_REACH * WARPATH_REACH;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.id() == realm.id() || c.cowed().contains(f.id()) || f.overlord() == realm.id()
				|| f.type() == FactionType.BARBARIAN && !Brutality.rival(server, realm, f) || TribePacts.spared(server, realm, f)) {
				continue;
			}
			double d = Raids.horizontal(f.seat(), realm.seat());
			if (d < bestDist) {
				best = f;
				bestDist = d;
			}
		}
		return best;
	}

	/** The warpath begins: told to everyone (the sworn are told to join), and its target posts the king as wanted. */
	private static void march(ServerLevel level, Faction realm, Faction target, City c) {
		MinecraftServer server = level.getServer();
		boolean orc = Camps.orc(server, realm.id());
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.warpath." + (orc ? "orc" : "goblin"), realm.name(), target.name())
			.withStyle(ChatFormatting.RED));
		for (String s : c.sworn()) {
			ServerPlayer p = player(server, s);
			if (p != null) {
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warpath.sworn", target.name(), target.seat().getX(), target.seat().getZ())
					.withStyle(ChatFormatting.GOLD));
			}
		}
		Bounties.warlord(server, realm, target, realm.name() + (orc ? "'s Warlord" : "'s Goblin King"), WANTED_PRICE);
		JaymosKingdoms.LOGGER.info("{} goes on the warpath against {}", realm.name(), target.name());
	}

	// ---- the raids ({@link Raids}) ---------------------------------------------------------------------------------------

	/** The settlement {@code camp}'s realm is on the warpath against, if it's still standing; else null. */
	static @Nullable Faction warpath(MinecraftServer server, Faction camp) {
		Faction realm = realmOf(server, camp);
		if (realm == null) {
			return null;
		}
		City c = city(server, realm.id());
		if (c.target() < 0 || Factions.level(server).getGameTime() >= c.until()) {
			return null;
		}
		Faction target = Factions.get(server, c.target());
		return target == null || target.fallen() ? null : target;
	}

	/** {@code camp} leaves {@code f} be: a realm paying its realm tribute. */
	static boolean spared(MinecraftServer server, Faction camp, Faction f) {
		Faction realm = realmOf(server, camp);
		return realm != null && city(server, realm.id()).cowed().contains(f.id());
	}

	// ---- the horde oath --------------------------------------------------------------------------------------------------

	/** The greenskin realm {@code player} is sworn to, or null. */
	static @Nullable Faction oathOf(MinecraftServer server, UUID player) {
		String id = player.toString();
		for (Map.Entry<String, City> e : all(server).entrySet()) {
			if (e.getValue().sworn().contains(id)) {
				Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
				if (f != null && !f.fallen()) {
					return f;
				}
			}
		}
		return null;
	}

	/** {@code player} is sworn to a realm of {@code tribe}. */
	static boolean sworn(MinecraftServer server, String tribe, UUID player) {
		Faction realm = oathOf(server, player);
		return realm != null && tribe.equals(Tribes.of(server, realm.id()));
	}

	/** {@code player} swears to the greenskin realm {@code realm}: null if done, or the lang key of why not. */
	static @Nullable String swear(ServerPlayer player, Faction realm) {
		MinecraftServer server = player.level().getServer();
		String tribe = Tribes.of(server, realm.id());
		if (tribe == null || !Hordes.realm(server, realm.id())) {
			return "message.jaymoskingdoms.no_pledge";
		}
		if (Factions.of(server, player.getUUID()) != null || oathOf(server, player.getUUID()) != null) {
			return "message.jaymoskingdoms.already_sworn";
		}
		boolean orc = Camps.orc(server, realm.id());
		boolean kin = orc ? GreenskinLife.halfOrc(player) : GreenskinLife.raised(player) && !GreenskinLife.halfOrc(player);
		if (!kin && Tribes.grudge(server, player.getUUID(), tribe) > TribePacts.ALLY_AT) {
			return "message.jaymoskingdoms.horde_oath.unproven";
		}
		City c = city(server, realm.id());
		List<String> sworn = new ArrayList<>(c.sworn());
		sworn.add(player.getStringUUID());
		put(server, realm.id(), new City(c.target(), c.until(), c.next(), c.cowed(), List.copyOf(sworn)));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.horde_oath.sworn", player.getDisplayName(), realm.name())
			.withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** {@code player} forswears their horde oath: the realm they were sworn to, or null if they had none. */
	static @Nullable Faction forswear(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Faction realm = oathOf(server, player.getUUID());
		if (realm == null) {
			return null;
		}
		City c = city(server, realm.id());
		List<String> sworn = new ArrayList<>(c.sworn());
		sworn.remove(player.getStringUUID());
		put(server, realm.id(), new City(c.target(), c.until(), c.next(), c.cowed(), List.copyOf(sworn)));
		String tribe = Tribes.of(server, realm.id());
		if (tribe != null) {
			// (turning your back on the horde stings)
			Tribes.add(server, player.getUUID(), tribe, 20);
		}
		return realm;
	}

	private static @Nullable ServerPlayer player(MinecraftServer server, String uuid) {
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(uuid));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
