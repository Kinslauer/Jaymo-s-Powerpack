package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.519) Who rules next. With MCA ({@link Rulers#inPlace}) a realm's custom decides: Coin realms elect (the guilds
 * choose the richest and most respected), the Warriorborn's and Frostborn's Old Blood and Conquest realms acclaim one at a
 * moot, and everywhere else blood inherits. When the only heir of the old ruler's family is a child, the best councillor
 * governs as regent until the child comes of age and then steps down. Notables are the realm's grown villagers and its
 * sworn players of Knight rank or higher. Without MCA the realm's folk rulers are spawned as before and none of this applies.
 */
final class Elections {
	/** How a realm chooses its rulers. */
	enum Custom {
		HEREDITARY, ELECTIVE, MOOT;

		String key() {
			return switch (this) {
				case HEREDITARY -> "heir";
				case ELECTIVE -> "elective";
				case MOOT -> "moot";
			};
		}
	}

	/**
	 * What the realm decided: {@code way} ("heir", "elected", "acclaimed", "regent", "of_age", or "" to leave it to the usual
	 * heir), the villager or the sworn player chosen, the ward a regent holds the throne for, and the old ruler's house.
	 */
	record Choice(String way, @Nullable UUID villager, @Nullable UUID player, @Nullable UUID ward, @Nullable UUID house) {
		static final Choice NONE = new Choice("", null, null, null, null);
	}

	/** How the sitting ruler came to the throne ({@code ruler}), the ward a regent rules for, and the old house; the day (since). */
	record Seat(String way, Optional<UUID> ward, String wardName, Optional<UUID> house, long since, Optional<UUID> ruler) {
		static final Codec<Seat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("way").forGetter(Seat::way),
			UUIDUtil.CODEC.optionalFieldOf("ward").forGetter(Seat::ward),
			Codec.STRING.optionalFieldOf("ward_name", "").forGetter(Seat::wardName),
			UUIDUtil.CODEC.optionalFieldOf("house").forGetter(Seat::house),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Seat::since),
			UUIDUtil.CODEC.optionalFieldOf("ruler").forGetter(Seat::ruler)).apply(i, Seat::new));
	}

	record Book(Map<String, Seat> seats) {
		static final Book EMPTY = new Book(Map.of());
		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Seat.CODEC).optionalFieldOf("seats", Map.of()).forGetter(Book::seats)).apply(i, Book::new));
	}

	/** On the Overworld: each realm's seat by id. Saved. */
	static final AttachmentType<Book> SEATS = AttachmentRegistry.create(JaymosKingdoms.id("succession_seats"), b -> b.persistent(Book.CODEC));

	/** The bloods whose Old Blood and Conquest realms hold a moot. */
	static final EnumSet<Heritage.Kind> MOOT_BLOODS = EnumSet.of(Heritage.Kind.WARRIORBORN, Heritage.Kind.FROSTBORN);
	/** A notable's score: per trade level 1 to 5 (twice that where the guilds elect), a guard's service (twice at a moot), kin and spouse of the late ruler (halved at a moot), a gift, mood and luck. */
	static final int PROFESSION_W = 8, GUARD_W = 10, KIN_W = 12, PARTNER_W = 8, GIFTED_W = 10, MOOD_W = 3, LUCK = 10;
	/** Sworn players stand too; the least standing they need; a player's score is standing over this plus their reputation over 100 (at most 10) plus luck. */
	static final boolean ELECT_PLAYERS = true;
	static final int PLAYER_LEAST = Rank.KNIGHT.standing, PLAYER_STANDING_DIV = 25, PLAYER_REP_DIV = 100, PLAYER_REP_MOST = 10;
	/** After this many days a teen ward is crowned even if not yet grown. */
	static final int REGENCY_TEEN_DAYS = 30;
	/** How often regencies are looked at (ticks). */
	static final int CHECK_TICKS = 200;

	/** Who killed each realm's last ruler (they don't stand), this session. */
	private static final Map<Integer, UUID> KILLERS = new HashMap<>();

	private Elections() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Elections::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Elections::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> KILLERS.clear());
	}

	/** {@code killer} slew {@code faction}'s ruler: they don't stand at the election after. */
	static void killed(int faction, UUID killer) {
		KILLERS.put(faction, killer);
	}

	// ---- the record ---------------------------------------------------------------------------------------------------

	private static Book book(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(SEATS, Book.EMPTY);
	}

	private static @Nullable Seat seat(MinecraftServer server, int faction) {
		return book(server).seats().get(Integer.toString(faction));
	}

	private static void put(MinecraftServer server, int faction, Seat seat) {
		Map<String, Seat> map = new HashMap<>(book(server).seats());
		map.put(Integer.toString(faction), seat);
		Factions.level(server).setAttached(SEATS, new Book(Map.copyOf(map)));
	}

	// ---- the custom --------------------------------------------------------------------------------------------------

	/** How {@code f} chooses its rulers. */
	static Custom custom(MinecraftServer server, Faction f) {
		GreatWars.Creed creed = GreatWars.creed(server, f);
		if (creed == GreatWars.Creed.COIN) {
			return Custom.ELECTIVE;
		}
		if (creed == GreatWars.Creed.BLOOD || creed == GreatWars.Creed.CONQUEST) {
			Heritage.Kind blood = Purists.blood(Wars.top(server, f));
			if (blood == null) {
				blood = Rulers.lastBlood(server, f);
			}
			if (blood != null && MOOT_BLOODS.contains(blood)) {
				return Custom.MOOT;
			}
		}
		return Custom.HEREDITARY;
	}

	/** {@code key}, or its elected-successor wording where {@code f}'s rulers are chosen (old age and fate news). */
	static String deathKey(MinecraftServer server, Faction f, String key) {
		return Rulers.inPlace(f) && custom(server, f) != Custom.HEREDITARY ? key + ".elect" : key;
	}

	// ---- choosing ----------------------------------------------------------------------------------------------------

	/** Who takes {@code f}'s throne now that its succession day has come (before the crowning, from {@link KingdomEvents}). */
	static Choice choose(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		if (!Rulers.inPlace(f) || Crowns.sovereign(server, f) != null) {
			// (a player wears the crown: only the steward is replaced)
			return Choice.NONE;
		}
		Custom custom = custom(server, f);
		Seat seat = seat(server, f.id());
		boolean regency = seat != null && "regent".equals(seat.way());
		UUID house = regency ? seat.house().orElse(null) : Rulers.house(server, f.id());
		UUID ward = seat != null ? seat.ward().orElse(null) : null;
		long now = Production.day(server);
		if (ward != null && level.getEntity(ward) instanceof Villager w && w.isAlive()
			&& (McaCompat.adult(w) || McaCompat.teenOrOlder(w) && seat != null && now - seat.since() >= REGENCY_TEEN_DAYS)) {
			// (the ward is of age, and the regent is gone)
			return new Choice("of_age", ward, null, null, null);
		}
		List<Villager> cands = McaCompat.notables(level, f, house);
		RandomSource random = level.getRandom();
		if (custom == Custom.HEREDITARY) {
			for (Villager v : cands) {
				if (house != null && McaCompat.kinOf(v, house) != McaCompat.STRANGERS) {
					// (0.7.519) grown kin or the widow: the usual heir; while a regent sits, name the old house's kin so the regent's family is not crowned
					return regency ? new Choice("heir", v.getUUID(), null, null, null) : Choice.NONE;
				}
			}
			List<Villager> young = McaCompat.youngKin(level, f, house);
			UUID heirChild = !young.isEmpty() ? young.get(0).getUUID() : ward;
			if (heirChild == null || cands.isEmpty()) {
				return Choice.NONE;
			}
			Villager best = null;
			int bestScore = Integer.MIN_VALUE;
			for (Villager v : cands) {
				int s = score(v, custom, house, random);
				if (s > bestScore) {
					best = v;
					bestScore = s;
				}
			}
			return new Choice("regent", best.getUUID(), null, heirChild, house);
		}
		Villager bestVillager = null;
		int bestScore = Integer.MIN_VALUE;
		for (Villager v : cands) {
			int s = score(v, custom, house, random);
			if (s > bestScore) {
				bestVillager = v;
				bestScore = s;
			}
		}
		ServerPlayer bestPlayer = null;
		if (ELECT_PLAYERS) {
			UUID killer = KILLERS.remove(f.id());
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Faction.Member m = f.member(p.getUUID());
				if (m == null || m.standing() < PLAYER_LEAST || Crowns.crownOf(server, p.getUUID()) != null || p.getUUID().equals(killer)) {
					continue;
				}
				int s = score(p, f, random);
				if (s > bestScore) {
					bestPlayer = p;
					bestVillager = null;
					bestScore = s;
				}
			}
		}
		String way = custom == Custom.ELECTIVE ? "elected" : "acclaimed";
		if (bestPlayer != null) {
			return new Choice(way, null, bestPlayer.getUUID(), null, house);
		}
		return bestVillager == null ? Choice.NONE : new Choice(way, bestVillager.getUUID(), null, null, house);
	}

	/** {@code v}'s standing among {@code custom}'s notables. */
	static int score(Villager v, Custom custom, @Nullable UUID house, RandomSource random) {
		int s = McaCompat.professionLevel(v) * PROFESSION_W * (custom == Custom.ELECTIVE ? 2 : 1);
		if (Rulers.role(v) == Folk.GUARD) {
			s += GUARD_W * (custom == Custom.MOOT ? 2 : 1);
		}
		int kin = house == null ? McaCompat.STRANGERS : McaCompat.kinOf(v, house);
		int shared = custom == Custom.MOOT ? 2 : 1;
		if (kin == McaCompat.KIN) {
			s += KIN_W / shared;
		} else if (kin == McaCompat.PARTNER) {
			s += PARTNER_W / shared;
		}
		if (PowersCompat.gift(v) != null) {
			s += GIFTED_W;
		}
		s += MOOD_W * McaCompat.temper(v);
		return s + random.nextInt(LUCK + 1);
	}

	/** A sworn player's standing among the notables. */
	static int score(ServerPlayer p, Faction f, RandomSource random) {
		Faction.Member m = f.member(p.getUUID());
		int standing = m == null ? 0 : m.standing();
		return standing / PLAYER_STANDING_DIV + Math.max(0, Math.min(PLAYER_REP_MOST, Reputation.get(p, f.id()) / PLAYER_REP_DIV)) + random.nextInt(LUCK + 1);
	}

	// ---- crowned -----------------------------------------------------------------------------------------------------

	/**
	 * {@code crowned}'s new ruler is on the throne by {@code choice}: how they came to it is written down, and the realm told.
	 * True if the news is told (so the usual succession lines are not).
	 */
	static boolean crowned(ServerLevel level, @Nullable Faction crowned, Choice choice) {
		if (crowned == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		PathfinderMob ruler = Rulers.of(level, crowned);
		String way = choice.way().isEmpty() ? "heir" : choice.way();
		String wardName = "";
		if ("regent".equals(way) && choice.ward() != null && level.getEntity(choice.ward()) instanceof Villager w) {
			wardName = w.getDisplayName().getString();
		}
		boolean regent = "regent".equals(way);
		put(server, crowned.id(), new Seat(way, regent ? Optional.ofNullable(choice.ward()) : Optional.empty(), wardName,
			regent ? Optional.ofNullable(choice.house()) : Optional.empty(), Production.day(server), ruler == null ? Optional.empty() : Optional.of(ruler.getUUID())));
		Component name = ruler == null ? Component.literal("?") : ruler.getDisplayName();
		if (choice.player() != null && ("elected".equals(way) || "acclaimed".equals(way))) {
			Faction now = Factions.get(server, crowned.id());
			ServerPlayer p = server.getPlayerList().getPlayer(choice.player());
			if (now == null || p == null) {
				return false;
			}
			Crowns.crown(server, now, p.getUUID());
			Post.report(p.getUUID(), Component.translatable("message.jaymoskingdoms.election.you", crowned.name(), name));
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.election.player", p.getDisplayName(), crowned.name())
				.withStyle(ChatFormatting.GOLD));
			return true;
		}
		switch (way) {
			case "elected", "acclaimed" -> KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.election." + way, name, crowned.name())
				.withStyle(ChatFormatting.GOLD));
			case "regent" -> KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.regency.begun", wardName.isEmpty() ? "?" : wardName,
				crowned.name(), name).withStyle(ChatFormatting.GOLD));
			case "of_age" -> KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.regency.of_age_late", name, crowned.name())
				.withStyle(ChatFormatting.GOLD));
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---- regencies ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0 || book(server).seats().isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long today = Production.day(server);
		for (Map.Entry<String, Seat> e : List.copyOf(book(server).seats().entrySet())) {
			Seat seat = e.getValue();
			if (!"regent".equals(seat.way()) || seat.ward().isEmpty()) {
				continue;
			}
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (f == null || f.fallen() || !Rulers.inPlace(f) || f.successionAt() >= 0 || Crowns.sovereign(server, f) != null) {
				continue;
			}
			PathfinderMob regent = Rulers.of(level, f);
			Entity ward = level.getEntity(seat.ward().get());
			// (both must be about: it waits while either is away)
			if (regent == null || !seat.ruler().map(regent.getUUID()::equals).orElse(false) || !(ward instanceof Villager w) || !w.isAlive()) {
				continue;
			}
			if (!(McaCompat.adult(w) || McaCompat.teenOrOlder(w) && today - seat.since() >= REGENCY_TEEN_DAYS)) {
				continue;
			}
			Component regentName = regent.getDisplayName();
			Rulers.stepDown(level, f, regent);
			Faction after = Factions.get(server, f.id());
			Faction crowned = after == null ? null : KingdomEvents.crown(level, after, w.getUUID());
			PathfinderMob ruler = crowned == null ? null : Rulers.of(level, crowned);
			if (crowned == null) {
				continue;
			}
			put(server, f.id(), new Seat("of_age", Optional.empty(), "", Optional.empty(), today, ruler == null ? Optional.empty() : Optional.of(ruler.getUUID())));
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.regency.of_age", ruler == null ? w.getDisplayName() : ruler.getDisplayName(),
				crowned.name(), regentName).withStyle(ChatFormatting.GOLD));
		}
	}

	/** A ward died: the regent rules in their own right. */
	private static void died(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity instanceof Villager v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		for (Map.Entry<String, Seat> e : List.copyOf(book(server).seats().entrySet())) {
			Seat seat = e.getValue();
			if (!"regent".equals(seat.way()) || !seat.ward().map(v.getUUID()::equals).orElse(false)) {
				continue;
			}
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			put(server, Integer.parseInt(e.getKey()), new Seat("heir", Optional.empty(), "", Optional.empty(), seat.since(), seat.ruler()));
			if (f != null) {
				PathfinderMob regent = Rulers.of(level, f);
				KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.regency.ward_died", v.getDisplayName(), f.name(),
					regent == null ? Component.literal("?") : regent.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	// ---- the ruler's info --------------------------------------------------------------------------------------------

	/** The Standing lines for {@code f}'s ruler {@code mob}: how the realm chooses, how they came to the throne, a regent's ward. */
	static Standing.Lines lines(MinecraftServer server, Faction f, PathfinderMob mob) {
		List<Component> lines = new ArrayList<>(), hovers = new ArrayList<>();
		Custom custom = custom(server, f);
		lines.add(Component.translatable("standing.jaymoskingdoms.succession." + custom.key()).withStyle(ChatFormatting.WHITE));
		hovers.add(Component.translatable("standing.jaymoskingdoms.succession." + custom.key() + ".hover"));
		Seat seat = seat(server, f.id());
		if (seat != null && seat.ruler().map(mob.getUUID()::equals).orElse(false)) {
			switch (seat.way()) {
				case "elected", "acclaimed", "of_age" -> {
					lines.add(Component.translatable("standing.jaymoskingdoms.came_by." + seat.way()).withStyle(ChatFormatting.WHITE));
					hovers.add(Component.empty());
				}
				case "regent" -> {
					String ward = seat.wardName().isEmpty() ? "?" : seat.wardName();
					lines.add(Component.translatable("standing.jaymoskingdoms.regent", ward).withStyle(ChatFormatting.GOLD));
					hovers.add(Component.translatable("standing.jaymoskingdoms.regent.hover", ward));
				}
				default -> {
				}
			}
		}
		return new Standing.Lines(List.copyOf(lines), List.copyOf(hovers));
	}
}
