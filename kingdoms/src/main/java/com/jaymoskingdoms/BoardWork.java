package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Work on the town board (docs/StoryQuests-Plan.md): besides errands and news, a town pins up paid work of the new
 * kinds ({@link QuestJobs}), each rated one to five stars. A notice is {@code town|work.<kind>.<stars>|poster|posted at}
 * ({@link TownNotices}); pulled off, it's a quest from whoever posted it, brought back to them for the pay.
 *
 * <p>Each town's board has a <b>level</b>, one to five: it goes up as work off it is finished ({@link #LEVEL_JOBS} jobs
 * a level, by anyone). A higher board posts work more often, holds one more notice, posts harder work (more stars, and
 * the kinds that need a name for themselves: cave-ins and incursions, then elites and night watches) and pays more.
 */
final class BoardWork {
	// ---- tuning ----
	/** The share of a board's town notices that are paid work. */
	static final float CHANCE = 0.45F;
	/** At most this many work notices on all boards of a kind at once. */
	static final int SAME_KIND = 1;
	/** A board goes up a level for every this many jobs finished; the highest level. */
	static final int LEVEL_JOBS = 3, MAX_LEVEL = 5;
	/** From this board level it holds one more notice; each level adds this to the chance a bare board gets one. */
	static final int ROOM_LEVEL = 3;
	static final float LEVEL_CHANCE = 0.05F;
	/** What a job pays for each star, by kind (an emerald's worth each); hearts with the poster and the town's good name, for each star. */
	private static final Map<String, Integer> PAY = Map.of("pack", 4, "elite", 8, "defend", 7, "trek", 5, "incursion", 6, "lamps", 3, "build", 4, "blast", 5, "stash", 3);
	static final int HEARTS = 2, REPUTATION = 3;
	/** The board level a kind is first posted at. */
	private static final Map<String, Integer> FROM = Map.of("pack", 1, "lamps", 1, "build", 1, "stash", 1, "trek", 1, "blast", 2, "incursion", 2, "elite", 3, "defend", 3);
	/** How far from the town its work lies: hunts and dens, cave-ins, stashes, building. */
	static final int HUNT_NEAR = 140, HUNT_FAR = 320, BUILD_NEAR = 44, BUILD_FAR = 70;
	/** An escort goes to a town at least this far off. */
	static final double TREK_LEAST = 120.0;

	private static final List<String> KINDS = List.of("pack", "elite", "defend", "trek", "incursion", "lamps", "build", "blast", "stash");
	/** What packs are made of; what an elite is; who comes at a town in the night. */
	private static final List<String> PACKS = List.of("minecraft:wolf", "minecraft:spider", "minecraft:zombie", "minecraft:skeleton", "minecraft:pillager", "minecraft:husk", "minecraft:stray");
	private static final List<String> ELITES = List.of("minecraft:vindicator", "minecraft:pillager", "minecraft:skeleton", "minecraft:zombie", "minecraft:spider", "minecraft:wolf", "minecraft:stray");
	private static final List<String> RAIDERS = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:pillager");
	/** An elite's name: the first part, the second. */
	private static final List<String> NAME_A = List.of("Grey", "Split", "Red", "Black", "Long", "Crook", "Pale", "Burnt", "One", "Hollow", "Iron", "Ash");
	private static final List<String> NAME_B = List.of("jaw", "ear", "tooth", "hand", "shank", "back", "eye", "rib", "heel", "mane", "tongue", "hide");
	/** What's built: the block (or #tag) and how many for one star. */
	private static final List<String> BUILDS = List.of("#minecraft:fences:8", "#minecraft:walls:8", "#minecraft:planks:12", "minecraft:hay_block:4");
	/** What's stashed, by where: a hermit's food, a drop of iron, a shrine's candles. */
	private static final Map<String, String> STASHES = Map.of("hermit", "minecraft:bread:4", "drop", "minecraft:iron_ingot:3", "shrine", "minecraft:candle:2");
	private static final List<String> DENS = List.of("nest", "camp", "pit");

	/** How many jobs have been finished off each town's board (faction id → jobs); on the overworld. */
	static final AttachmentType<Map<String, Integer>> DONE = AttachmentRegistry.create(JaymosKingdoms.id("board_work_done"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	private BoardWork() {
	}

	// ---- stars and levels ------------------------------------------------------------------------------------------------

	/** {@code work.<kind>.<stars>}: its kind, or null if it isn't one. */
	static @Nullable String kind(String template) {
		String[] p = template.split("\\.");
		return p.length == 3 && p[0].equals("work") && KINDS.contains(p[1]) ? p[1] : null;
	}

	/** How hard a piece of work is, one to five stars (one for anything that isn't board work). */
	static int stars(String template) {
		String[] p = template.split("\\.");
		try {
			return p.length == 3 && p[0].equals("work") ? Math.clamp(Integer.parseInt(p[2]), 1, 5) : 1;
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/** {@code f}'s board level, one to five. */
	static int level(ServerLevel level, @Nullable Faction f) {
		if (f == null) {
			return 1;
		}
		int done = Factions.level(level.getServer()).getAttachedOrElse(DONE, Map.of()).getOrDefault(Integer.toString(f.id()), 0);
		return Math.min(MAX_LEVEL, 1 + done / LEVEL_JOBS);
	}

	/** {@code q} was brought back and paid: board work counts toward its town's board level. */
	static void done(ServerLevel level, Faction f, Quest q) {
		if (kind(q.template()) == null) {
			return;
		}
		ServerLevel world = Factions.level(level.getServer());
		Map<String, Integer> done = new HashMap<>(world.getAttachedOrElse(DONE, Map.of()));
		done.merge(Integer.toString(f.id()), 1, Integer::sum);
		world.setAttached(DONE, Map.copyOf(done));
	}

	/** How many notices the board of {@code f} holds, and how often a bare one gets one: more for a board with a name. */
	static int room(ServerLevel level, @Nullable Faction f) {
		return TownNotices.MOST + (level(level, f) >= ROOM_LEVEL ? 1 : 0);
	}

	static float chance(ServerLevel level, @Nullable Faction f) {
		return TownNotices.CHANCE + LEVEL_CHANCE * (level(level, f) - 1);
	}

	// ---- posting -----------------------------------------------------------------------------------------------------------

	/** A work notice for {@code poster}'s town that isn't up already, or null: a kind its board has the level for, as hard as the level lets it be. */
	static @Nullable String pick(ServerLevel level, Villager poster, Set<String> up, long now, RandomSource random) {
		Faction f = Quests.settlement(level, poster);
		if (f == null) {
			return null;
		}
		int board = level(level, f);
		List<String> free = new ArrayList<>();
		for (String kind : KINDS) {
			long same = up.stream().filter(k -> TownNotices.is(k) && k.startsWith("town|work." + kind + ".")).count();
			if (FROM.get(kind) <= board && same < SAME_KIND
				&& (!kind.equals("trek") || Tales.nearest(level.getServer(), f.seat(), f, false) != null
					&& Tales.nearest(level.getServer(), f.seat(), f, false).seat().distSqr(f.seat()) >= TREK_LEAST * TREK_LEAST)) {
				free.add(kind);
			}
		}
		if (free.isEmpty()) {
			return null;
		}
		String kind = free.get(random.nextInt(free.size()));
		// (the lower of two throws, so the hardest work a board can post is its rarest; a kind is never easier than the level it comes at)
		int most = Math.min(5, board + 1);
		int stars = Math.max(Math.min(1 + random.nextInt(most), 1 + random.nextInt(most)), Math.min(most, FROM.get(kind)));
		return "town|work." + kind + "." + stars + "|" + poster.getStringUUID() + "|" + now;
	}

	static Component title(String template) {
		String kind = kind(template);
		return Component.translatable("work.jaymoskingdoms." + (kind == null ? "pack" : kind) + ".title", "★".repeat(stars(template)));
	}

	/** What the notice says of the work, before anyone has taken it. */
	static Component lore(String template) {
		String kind = kind(template);
		return Component.translatable("work.jaymoskingdoms." + (kind == null ? "pack" : kind) + ".notice");
	}

	// ---- pulled off ----------------------------------------------------------------------------------------------------------

	private static <T> T any(List<T> list, RandomSource random) {
		return list.get(random.nextInt(list.size()));
	}

	/** {@code what:n} (the id may have colons of its own): the id, and n for each star. */
	private static String what(String spec) {
		return spec.substring(0, spec.lastIndexOf(':'));
	}

	private static int each(String spec) {
		return Integer.parseInt(spec.substring(spec.lastIndexOf(':') + 1));
	}

	/**
	 * {@code player} pulls the work notice {@code template} off {@code f}'s board: it's a quest from {@code giver}, who
	 * posted it. False if it stays up (their log is full, or the work can't be set out).
	 */
	static boolean take(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, String template) {
		String kind = kind(template);
		if (kind == null) {
			return true;
		}
		int stars = stars(template);
		RandomSource random = level.getRandom();
		BlockPos seat = f.seat();
		String id = UUID.randomUUID().toString();
		int questKind;
		String target = "", otherName = "", place = f.name();
		int count = 1;
		BlockPos pos = seat;
		Optional<UUID> other = Optional.empty();
		switch (kind) {
			case "pack" -> {
				questKind = Quest.PACK;
				target = any(PACKS, random);
				count = 3 + stars;
				pos = Tales.wild(level, seat, HUNT_NEAR + random.nextInt(HUNT_FAR - HUNT_NEAR), random);
			}
			case "elite" -> {
				questKind = Quest.ELITE;
				target = any(ELITES, random);
				otherName = any(NAME_A, random) + any(NAME_B, random);
				pos = Tales.wild(level, seat, HUNT_NEAR + random.nextInt(HUNT_FAR - HUNT_NEAR), random);
			}
			case "defend" -> {
				questKind = Quest.DEFEND;
				target = any(RAIDERS, random);
				count = 2 + stars / 2;
			}
			case "trek" -> {
				questKind = Quest.TREK;
				Faction to = Tales.nearest(level.getServer(), seat, f, false);
				if (to == null) {
					return true;
				}
				// (the traveller is waiting by whoever posted it)
				BlockPos by = Construction.standable(level, giver.blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), 2);
				Villager charge = McaCompat.talePerson(level, Vec3.atBottomCenterOf(by), null, null, "", false);
				if (charge == null) {
					return true;
				}
				charge.setPersistenceRequired();
				charge.setAttached(TaleCast.CAST, new TaleCast.Cast("work:" + id, "charge", by.asLong(), false, false, false, level.getGameTime()));
				otherName = charge.getName().getString();
				other = Optional.of(charge.getUUID());
				pos = to.seat();
				place = to.name();
			}
			case "incursion" -> {
				questKind = Quest.INCURSION;
				otherName = any(DENS, random);
				target = otherName.equals("nest") ? "minecraft:spider" : otherName.equals("camp") ? "minecraft:pillager" : random.nextBoolean() ? "minecraft:zombie" : "minecraft:skeleton";
				count = 4 + stars + (otherName.equals("nest") ? 1 : 0);
				pos = Tales.wild(level, seat, HUNT_NEAR + random.nextInt(HUNT_FAR - HUNT_NEAR), random);
			}
			case "lamps" -> {
				questKind = Quest.LAMPS;
				target = "minecraft:torch";
				count = 3 + stars;
			}
			case "build" -> {
				questKind = Quest.BUILD;
				String spec = any(BUILDS, random);
				target = what(spec);
				count = each(spec) * stars;
				pos = Tales.wild(level, seat, BUILD_NEAR + random.nextInt(BUILD_FAR - BUILD_NEAR), random);
			}
			case "blast" -> {
				questKind = Quest.BLAST;
				target = "minecraft:tnt";
				pos = Tales.wild(level, seat, HUNT_NEAR + random.nextInt(HUNT_FAR - HUNT_NEAR), random);
			}
			default -> {
				questKind = Quest.STASH;
				otherName = any(List.copyOf(STASHES.keySet()), random);
				String spec = STASHES.get(otherName);
				target = what(spec);
				count = each(spec) * stars;
				pos = Tales.wild(level, seat, HUNT_NEAR + random.nextInt(HUNT_FAR - HUNT_NEAR), random);
			}
		}
		String job = giver instanceof Villager v ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath() : "";
		// (a board with a name for itself pays a little over the rate)
		int pay = PAY.get(kind) * stars + level(level, f) - 1 + random.nextInt(3);
		Quest q = new Quest(id, questKind, template,
			new Quest.Giver(giver.getUUID(), giver.getName().getString(), f.id(), false, job),
			new Quest.Objective(target, count, 0, Optional.of(pos), other, otherName, place, false),
			new Quest.Reward(pay, List.of(), Optional.empty(), Optional.empty(), HEARTS * stars, REPUTATION * stars, 0), 0L);
		if (!Quests.takeFromBoard(level, player, q)) {
			if (other.isPresent() && level.getEntity(other.get()) instanceof Villager charge) {
				charge.discard();
			}
			return false;
		}
		return true;
	}
}
