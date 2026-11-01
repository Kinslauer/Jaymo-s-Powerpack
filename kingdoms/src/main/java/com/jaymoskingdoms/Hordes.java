package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.475) Hordes patch 2: greenskin camps grow in tiers ({@code docs/Hordes-Plan.md}). An orc camp goes war camp,
 * warhold, horde town, horde kingdom; a goblin village goes village, warren, goblin town, goblin kingdom. Each tier opens
 * when the last is filled (its buildings up, its folk at their cap, its wall closed) and the camp is old enough; the last
 * only for the tribe's king seat ({@link Tribes#seat}).
 *
 * <p>Moving up, a camp raises a new outer palisade in its own style, one arc a day (two with ogres in the camp), never
 * over anything a player placed or anything built; once it closes, gates are cut in the old ring and coarse-dirt roads
 * run gate to gate. New buildings go into the band between the walls, laid out by an angle-slot planner seeded from the
 * camp (so each camp lays out differently, and the same after a restart), from per-tier lists of the old designs and
 * {@link HordeBuildings}' new ones. At tier 4 the great hall replaces the longhouse or great hut, the camp claims land as
 * a kingdom does, its chief takes a title (Warlord, Goblin King) on his bar and in the news, and its tribe's smaller
 * camps nearby become its vassals ({@link Relations#vassalize}: they stay camps, with the realm as their overlord).
 *
 * <p>Saved in {@link #HORDES} (camp id → {@link Horde}); a camp with no entry is tier 1 with its first stockade closed, so
 * camps from before start at tier 1. Nothing here runs unless the camp is loaded ({@link Camps}' tick), and folk still
 * spawn and act only near players.
 */
final class Hordes {
	// ================================================================================================================
	// Tuning
	/** Each tier's wall radius round the fire (tier 1's is the camp's first stockade, {@link OrcBuildings#RING_RADIUS}). */
	static final int[] WALL_RADIUS = {OrcBuildings.RING_RADIUS, 36, 52, 72};
	/** Each tier's folk cap (grunts and rangers; goblins and slingers): a big camp looks big by its walls, not its crowds. */
	static final int[] ORC_CAP = {OrcCamps.MAX_ORCS, 20, 25, 30}, GOBLIN_CAP = {GoblinVillages.MAX_GOBLINS, 20, 25, 30};
	/** How old (days) a camp must be to reach each tier. */
	static final int[] TIER_DAYS = {0, 10, 25, 50};
	/** Its land, by tier: chunks out each way from the fire. A tier 4 realm claims as a kingdom does. */
	static final int[] CLAIM_RADIUS = {Camps.CLAIM_RADIUS, 3, 4, KingdomEvents.CLAIM_RADIUS};
	/** What it takes in each day, in percent of a war camp's (more hands; Patch 3's jobs replace it). */
	static final int[] YIELD_PERCENT = {100, 150, 200, 250};
	/** A new palisade goes up in this many arcs, one a day (ogres in the camp: this many a day), each this many logs. */
	static final int ARCS = 8, OGRE_ARCS = 2, ARC_LOGS = 6;
	/** A band building's cost: logs, and hides (orcs) or mud bricks (goblins). */
	static final int BUILD_LOGS = 4, BUILD_HIDES = 4, BUILD_MUD = 6;
	/** The band: its buildings stay this far inside the new wall and outside the old, and this far from each other. */
	static final int BAND_INNER = 3, BAND_OUTER = 2, GAP = 2;
	/** The four ways out (gates and roads) keep this half-width clear of buildings; a road is this half-width. */
	static final int WAY_CLEAR = 4, ROAD_HALF = 1;
	/** The planner tries angles this many degrees apart, and rows (from the fire) this many blocks apart. */
	static final double ANGLE_STEP = 3.0;
	static final int ROW_STEP = 1;
	/** The most the ground may rise and fall under a band building, and how far a wall or a building may sit above or below the fire. */
	static final int PLOT_SPREAD = 4, MAX_DROP = 12;
	/** New folk of a bigger camp wander this far inside its outer wall. */
	static final int HOME_INSIDE = 6;
	/** A tier 4 realm's vassals: its tribe's smaller standing camps this near. */
	static final double VASSAL_RANGE = 640.0;
	/** Nothing goes up with a player this near (a wall doesn't rise before your eyes). */
	static final double BUILD_HIDE_RANGE = 24.0;
	// ================================================================================================================

	/**
	 * A camp's tier and its progress in it: {@code since} (its {@link Camps.CampData#grown} days when it reached the tier),
	 * the arcs of the tier's palisade raised, the band buildings raised (or skipped), whether the old ring has its gates and
	 * roads, whether the great hall stands (tier 4). {@code hoard}: Patch 3's chief's hoard, kept but not used yet.
	 */
	record Horde(int tier, int since, int arcs, int built, boolean opened, boolean hall, long hoard) {
		static final Horde START = new Horde(1, 0, ARCS, 0, true, false, 0L);
		static final Codec<Horde> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("tier", 1).forGetter(Horde::tier),
				Codec.INT.optionalFieldOf("since", 0).forGetter(Horde::since),
				Codec.INT.optionalFieldOf("arcs", ARCS).forGetter(Horde::arcs),
				Codec.INT.optionalFieldOf("built", 0).forGetter(Horde::built),
				Codec.BOOL.optionalFieldOf("opened", true).forGetter(Horde::opened),
				Codec.BOOL.optionalFieldOf("hall", false).forGetter(Horde::hall),
				Codec.LONG.optionalFieldOf("hoard", 0L).forGetter(Horde::hoard))
			.apply(i, Horde::new));

		Horde withArcs(int arcs) {
			return new Horde(this.tier, this.since, arcs, this.built, this.opened, this.hall, this.hoard);
		}

		Horde withBuilt(int built) {
			return new Horde(this.tier, this.since, this.arcs, built, this.opened, this.hall, this.hoard);
		}

		Horde withOpened() {
			return new Horde(this.tier, this.since, this.arcs, this.built, true, this.hall, this.hoard);
		}

		Horde withHall() {
			return new Horde(this.tier, this.since, this.arcs, this.built, this.opened, true, this.hoard);
		}
	}

	/** On the Overworld: every greenskin camp's tier (camp id → it). Saved. */
	static final AttachmentType<Map<String, Horde>> HORDES = AttachmentRegistry.create(JaymosKingdoms.id("hordes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Horde.CODEC)));

	/** A planned band: the tier it's for, and its buildings in the order they go up (camp frame, as {@link IllagerBuildings.Spot}s). */
	private record Plan(int tier, List<IllagerBuildings.Spot> spots) {
	}

	/** One entry of a tier's build list. */
	private record Piece(String id, Supplier<Blueprint> design) {
	}

	/** Camp id → its band's plan (worked out again after a restart: it's seeded from the camp). */
	private static final Map<Integer, Plan> PLANS = new HashMap<>();
	/** Building id → its footprint in its own frame (min x, min z, max x, max z, height). */
	private static final Map<String, int[]> BOUNDS = new HashMap<>();

	/** The stockades go up without reshaping their neighbours, so their stakes stay lone posts. */
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private Hordes() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLANS.clear());
	}

	// ---- state ---------------------------------------------------------------------------------------------------------

	static boolean greenskin(MinecraftServer server, int id) {
		return Camps.orc(server, id) || Camps.goblin(server, id);
	}

	static Horde horde(MinecraftServer server, int id) {
		return Factions.level(server).getAttachedOrElse(HORDES, Map.of()).getOrDefault(Integer.toString(id), Horde.START);
	}

	private static void put(MinecraftServer server, int id, Horde horde) {
		Map<String, Horde> map = new HashMap<>(Factions.level(server).getAttachedOrElse(HORDES, Map.of()));
		map.put(Integer.toString(id), horde);
		Factions.level(server).setAttached(HORDES, Map.copyOf(map));
	}

	/** Camp {@code id}'s tier (1 for any camp that isn't a greenskin camp, or hasn't grown). */
	static int tier(MinecraftServer server, int id) {
		return horde(server, id).tier();
	}

	/** Its folk cap at its tier. */
	static int folkCap(MinecraftServer server, int id, boolean orc) {
		return (orc ? ORC_CAP : GOBLIN_CAP)[tier(server, id) - 1];
	}

	/** {@code amount} of a war camp's daily takings, at its tier. */
	static int yield(MinecraftServer server, int id, int amount) {
		return amount * YIELD_PERCENT[tier(server, id) - 1] / 100;
	}

	/** How far its new folk wander from its fire ({@code base} at tier 1). */
	static int homeRadius(MinecraftServer server, int id, int base) {
		int tier = tier(server, id);
		return tier <= 1 ? base : Math.max(base, WALL_RADIUS[tier - 1] - HOME_INSIDE);
	}

	/** Its outer wall's radius (what it has built round itself so far, or is building). */
	static int radius(MinecraftServer server, int id) {
		return WALL_RADIUS[tier(server, id) - 1];
	}

	/** A tier 4 realm. */
	static boolean realm(MinecraftServer server, int id) {
		return greenskin(server, id) && tier(server, id) >= 4;
	}

	// ---- names -----------------------------------------------------------------------------------------------------------

	/** The lang key of camp {@code f}'s tier name ("War camp", "Goblin town"...), or null for any other faction. */
	static @Nullable String tierKey(MinecraftServer server, Faction f) {
		if (f.type() != FactionType.BARBARIAN || !greenskin(server, f.id())) {
			return null;
		}
		return "camp.jaymoskingdoms.tier." + (Camps.orc(server, f.id()) ? "orc." : "goblin.") + tier(server, f.id());
	}

	/** What {@code f} is, as shown (a greenskin camp by its tier name, anything else by its type). */
	static Component kindName(MinecraftServer server, Faction f) {
		String key = tierKey(server, f);
		return Component.translatable(key != null ? key : f.type().key());
	}

	/** The journal's key for what {@code f} is. */
	static String journalKind(MinecraftServer server, Faction f) {
		String key = tierKey(server, f);
		return key != null ? key : "journal.jaymoskingdoms.kind." + f.type().id();
	}

	/** A tier 4 realm's chief's title (Warlord, Goblin King), or null. */
	static @Nullable Component title(MinecraftServer server, int id) {
		if (!realm(server, id)) {
			return null;
		}
		return Component.translatable(Camps.orc(server, id) ? "title.jaymoskingdoms.warlord" : "title.jaymoskingdoms.goblin_king");
	}

	/** {@code name} with its camp's chief's title before it, if he has one. */
	static Component titled(MinecraftServer server, int id, Component name) {
		Component title = title(server, id);
		return title == null ? name : Component.translatable("title.jaymoskingdoms.horde_titled", title, name);
	}

	// ---- each day --------------------------------------------------------------------------------------------------------

	/** A greenskin camp's day ({@link OrcCamps#grow}, {@link GoblinVillages#grow}): it moves up a tier if it's ready, and a realm takes its vassals. */
	static void day(ServerLevel level, int id) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, id);
		Camps.CampData data = Camps.data(server, id);
		if (camp == null || data == null || camp.fallen() || data.clearedAt() >= 0) {
			return;
		}
		Horde h = horde(server, id);
		if (h.tier() < WALL_RADIUS.length && missing(level, camp, data, h).isEmpty()) {
			promote(level, camp, data, true);
		}
		Faction now = Factions.get(server, id);
		if (now != null && realm(server, id)) {
			vassals(server, now);
		}
	}

	/** What camp {@code camp} still lacks for its next tier (debug text: the command shows it); empty when it's ready. */
	static List<String> missing(ServerLevel level, Faction camp, Camps.CampData data, Horde h) {
		MinecraftServer server = level.getServer();
		List<String> out = new ArrayList<>();
		int t = h.tier();
		if (t >= WALL_RADIUS.length) {
			out.add("already at the top tier");
			return out;
		}
		boolean orc = data.orc();
		if (t == 1) {
			int inner = orc ? OrcBuildings.GROWTH.size() : GoblinBuildings.GROWTH.size();
			if (data.built() < inner) {
				out.add("inner buildings " + data.built() + "/" + inner);
			}
		} else {
			if (h.arcs() < ARCS) {
				out.add("palisade " + h.arcs() + "/" + ARCS + " arcs");
			} else if (!h.opened()) {
				out.add("gates and roads not yet cut");
			}
			int planned = plan(server, camp.id(), data, t).size();
			if (h.built() < planned) {
				out.add("band buildings " + h.built() + "/" + planned);
			}
		}
		int folk = folk(camp, orc), cap = (orc ? ORC_CAP : GOBLIN_CAP)[t - 1];
		if (folk < cap) {
			out.add("folk " + folk + "/" + cap);
		}
		long age = (level.getGameTime() - data.founded()) / Camps.DAY_TICKS;
		if (age < TIER_DAYS[t]) {
			out.add("age " + age + "/" + TIER_DAYS[t] + " days");
		}
		if (t + 1 == WALL_RADIUS.length) {
			String tribe = Tribes.of(server, camp.id());
			Faction seat = tribe == null ? null : Tribes.seat(server, tribe);
			if (seat == null || seat.id() != camp.id()) {
				out.add("not the tribe's king seat" + (seat == null ? "" : " (that's " + seat.name() + ")"));
			}
		}
		return out;
	}

	/** Its fighting folk (grunts and rangers; goblins and slingers), on the roster or on their way back. */
	static int folk(Faction camp, boolean orc) {
		return orc ? OrcCamps.fighters(camp) : GoblinVillages.goblins(camp);
	}

	/** Moves the camp up a tier: its new land claimed, its new wall and band begun, the news told. */
	static void promote(ServerLevel level, Faction camp, Camps.CampData data, boolean tell) {
		MinecraftServer server = level.getServer();
		Horde h = horde(server, camp.id());
		int next = h.tier() + 1;
		if (next > WALL_RADIUS.length) {
			return;
		}
		put(server, camp.id(), new Horde(next, data.grown(), 0, 0, false, false, h.hoard()));
		PLANS.remove(camp.id());
		List<Long> more = KingdomEvents.claim(server, data.center(), CLAIM_RADIUS[next - 1]);
		Factions.update(server, camp.id(), f -> {
			Set<Long> claims = new LinkedHashSet<>(f.claims());
			claims.addAll(more);
			return f.withClaims(List.copyOf(claims));
		});
		boolean orc = data.orc();
		Faction now = Factions.get(server, camp.id());
		if (next == WALL_RADIUS.length && now != null) {
			vassals(server, now);
		}
		if (tell && now != null) {
			String brood = orc ? "orc" : "goblin";
			if (next == WALL_RADIUS.length) {
				Entity chief = now.leader().isPresent() ? level.getEntity(now.leader().get()) : null;
				Component who = chief != null ? chief.getDisplayName() : Component.translatable("message.jaymoskingdoms.horde.unnamed." + brood);
				long vassals = Factions.all(server, FactionType.BARBARIAN).stream().filter(f -> f.overlord() == now.id() && !f.fallen()).count();
				KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.horde." + brood + ".4", now.name(), who, vassals)
					.withStyle(ChatFormatting.GOLD));
			} else {
				KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.horde." + brood + "." + next, now.name())
					.withStyle(ChatFormatting.GOLD));
			}
		}
		JaymosKingdoms.LOGGER.info("{} grew to tier {}", camp.name(), next);
	}

	// ---- the realm -------------------------------------------------------------------------------------------------------

	/** A tier 4 realm takes its tribe's smaller standing camps near it as vassals, and lets go of any no longer its tribe's or near. */
	static void vassals(MinecraftServer server, Faction realm) {
		String tribe = Tribes.of(server, realm.id());
		if (tribe == null) {
			return;
		}
		List<Faction> camps = Tribes.camps(server, tribe);
		for (Faction f : camps) {
			if (f.id() != realm.id() && f.overlord() != realm.id() && tier(server, f.id()) < WALL_RADIUS.length
				&& Raids.horizontal(f.seat(), realm.seat()) <= VASSAL_RANGE * VASSAL_RANGE) {
				Relations.vassalize(server, f.id(), realm.id());
				JaymosKingdoms.LOGGER.info("{} bows to {}", f.name(), realm.name());
			}
		}
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (f.overlord() == realm.id() && (f.fallen() || camps.stream().noneMatch(c -> c.id() == f.id())
				|| Raids.horizontal(f.seat(), realm.seat()) > VASSAL_RANGE * VASSAL_RANGE)) {
				Factions.update(server, f.id(), x -> x.withOverlord(-1));
			}
		}
	}

	/** Camp {@code id} fell (its chief slain): its vassals are free. */
	static void fell(MinecraftServer server, int id) {
		for (Faction f : Factions.all(server)) {
			if (f.overlord() == id && f.type() == FactionType.BARBARIAN) {
				Factions.update(server, f.id(), x -> x.withOverlord(-1));
			}
		}
		PLANS.remove(id);
	}

	// ---- building, each look at a loaded camp ------------------------------------------------------------------------------

	/**
	 * Every {@link Camps#CHECK_TICKS} while a standing greenskin camp is loaded ({@link OrcCamps#tick},
	 * {@link GoblinVillages#tick}): at most one thing goes up, the great hall, the next arc of the palisade, the gates and
	 * roads once it closes, or the next band building, as the days since it reached its tier (and its ogres) allow.
	 * {@code force}: even with a player near.
	 */
	static boolean tick(ServerLevel level, Faction camp, Camps.CampData data, boolean force) {
		return step(level, camp, data, force, false);
	}

	/** One step of its tier's building; {@code all}: whatever the days, the cost or who's near (the operator's command). */
	private static boolean step(ServerLevel level, Faction camp, Camps.CampData data, boolean force, boolean all) {
		MinecraftServer server = level.getServer();
		Horde h = horde(server, camp.id());
		if (h.tier() < 2 || !all && !Storage.loaded(level, camp)) {
			return false;
		}
		boolean orc = data.orc();
		int ogres = orc ? OgreHolds.haulers(level, camp.id(), data.center()).size() : 0;
		int days = Math.max(0, data.grown() - h.since());
		boolean unseen = force || all;
		if (h.tier() == WALL_RADIUS.length && !h.hall() && (all || days >= 1)) {
			if (greatHall(level, data, orc, unseen)) {
				put(server, camp.id(), h.withHall());
				return true;
			}
			return false;
		}
		int arcs = all ? ARCS : Math.min(ARCS, days * (ogres > 0 ? OGRE_ARCS : 1));
		if (h.arcs() < arcs) {
			if (arc(level, camp, data, h, orc, unseen, all)) {
				put(server, camp.id(), h.withArcs(h.arcs() + 1));
				return true;
			}
			return false;
		}
		if (h.arcs() >= ARCS && !h.opened()) {
			if (open(level, camp, data, h.tier(), orc)) {
				put(server, camp.id(), h.withOpened());
				return true;
			}
			return false;
		}
		List<IllagerBuildings.Spot> plan = plan(server, camp.id(), data, h.tier());
		int allowed = all ? plan.size() : Math.min(plan.size(), days * (1 + ogres));
		if (h.built() < allowed) {
			Raise r = raise(level, camp, data, plan.get(h.built()), orc, unseen, all);
			if (r != Raise.WAIT) {
				put(server, camp.id(), h.withBuilt(h.built() + 1));
				if (r == Raise.PLACED) {
					OgreHolds.haul(level, camp.id(), data.center(), plan.get(h.built()).placer(data.center(), data.gate()).at(0, 0, 0));
				}
				return true;
			}
		}
		return false;
	}

	/** Builds the rest of the camp's tier at once (the operator's command). */
	private static void complete(ServerLevel level, int id) {
		MinecraftServer server = level.getServer();
		for (int i = 0; i < 400; i++) {
			Faction camp = Factions.get(server, id);
			Camps.CampData data = Camps.data(server, id);
			if (camp == null || data == null || !step(level, camp, data, true, true)) {
				return;
			}
		}
	}

	// ---- the palisade ----------------------------------------------------------------------------------------------------

	/** The arc the column at camp-frame {@code (x, z)} belongs to (arc 0 is centred on the front gate, going round clockwise). */
	private static int arcOf(int x, int z) {
		double angle = Math.toDegrees(Math.atan2(x, -z));
		double span = 360.0 / ARCS;
		return Math.floorMod((int)Math.floor((angle + span / 2.0) / span), ARCS);
	}

	/** The {@code n}th arc to go up: from the front gate, out to either side, the back last. */
	private static int arcNumber(int n) {
		return n == 0 ? 0 : n % 2 == 1 ? (n + 1) / 2 : ARCS - n / 2;
	}

	/** A cell of a ring at camp-frame {@code (x, z)} is in one of its four gates (front, right, back, left). */
	private static boolean gate(int x, int z, int half) {
		return Math.abs(x) <= half && z != 0 || Math.abs(z) <= half && x != 0;
	}

	/** The ground at {@code column}: the first air above the land itself, through trees and plants. */
	static int ground(ServerLevel level, BlockPos column) {
		return ground(level, column, false);
	}

	/** The same, and with {@code throughWall} through a palisade's parts too (where a ladder or a gate tower goes). */
	private static int ground(ServerLevel level, BlockPos column, boolean throughWall) {
		int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
		int min = level.getMinY();
		while (y > min + 1) {
			BlockPos below = column.atY(y - 1);
			BlockState s = level.getBlockState(below);
			boolean mine = !Placed.any(level, below);
			boolean tree = (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) && mine;
			boolean wall = throughWall && mine && (s.is(BlockTags.WOODEN_SLABS) || s.is(Blocks.LADDER) || s.is(BlockTags.FENCES)
				|| s.is(Blocks.SKELETON_SKULL) || s.is(Blocks.BONE_BLOCK) || s.is(Blocks.BLACKSTONE));
			if (tree || wall || s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	/** {@code at} may be built over: nothing a player placed, nothing built; only air, plants, trees and the land. */
	private static boolean clearable(ServerLevel level, BlockPos at) {
		BlockState s = level.getBlockState(at);
		if (s.isAir()) {
			return true;
		}
		if (Placed.any(level, at) || !s.getFluidState().isEmpty()) {
			return false;
		}
		return s.canBeReplaced() || Castle.natural(s) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.MUSHROOM_STEM)
			|| s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK);
	}

	/** {@code column} (camp frame {@code (x, z)}) is the camp's to build on: loaded, its own land or nobody's, and on dry land. */
	private static boolean ours(ServerLevel level, Faction camp, BlockPos column) {
		if (!level.isLoaded(column)) {
			return false;
		}
		Faction owner = Factions.at(level.getServer(), level, column);
		return owner == null || owner.id() == camp.id();
	}

	/** Raises the next arc of the tier's palisade (false: not now, it's waiting on logs, loaded ground, or a player to leave). */
	private static boolean arc(ServerLevel level, Faction camp, Camps.CampData data, Horde h, boolean orc, boolean unseen, boolean all) {
		int r = WALL_RADIUS[h.tier() - 1];
		int arc = arcNumber(h.arcs());
		CampBuildings.Placer frame = new CampBuildings.Placer(data.center(), data.gate(), 0);
		double mid = Math.toRadians(arc * 360.0 / ARCS);
		BlockPos middle = frame.at((int)Math.round(Math.sin(mid) * r), 0, (int)Math.round(-Math.cos(mid) * r));
		// (the whole arc loaded, or it waits: no gaps left for want of a chunk)
		for (double edge : new double[] {-0.5, 0.5}) {
			double a = Math.toRadians((arc + edge) * 360.0 / ARCS);
			if (!level.isLoaded(frame.at((int)Math.round(Math.sin(a) * (r + 1)), 0, (int)Math.round(-Math.cos(a) * (r + 1))))) {
				return false;
			}
		}
		if (!level.isLoaded(middle)) {
			return false;
		}
		if (!unseen && Raids.playerNear(level, middle, BUILD_HIDE_RANGE)) {
			return false;
		}
		Item log = (orc ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).asItem();
		if (!all) {
			if (!Storage.has(level, camp, Map.of(log, ARC_LOGS))) {
				return false;
			}
			Storage.take(level, camp, log, ARC_LOGS);
		}
		int wallHigh = orc ? OrcBuildings.WALL_HIGH : GoblinBuildings.WALL_HIGH, walk = orc ? OrcBuildings.WALK_HIGH : GoblinBuildings.WALK_HIGH;
		int gateHalf = orc ? OrcBuildings.GATE_HALF : GoblinBuildings.GATE_HALF;
		int floor = data.center().getY();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < r - 1.5 || d >= r + 0.5 || arcOf(x, z) != arc) {
					continue;
				}
				BlockPos column = frame.at(x, 0, z);
				if (!ours(level, camp, column)) {
					continue;
				}
				int base = ground(level, column);
				if (Math.abs(base - floor) > MAX_DROP || !level.getFluidState(column.atY(base - 1)).isEmpty()) {
					continue;
				}
				boolean inGate = gate(x, z, gateHalf);
				if (d < r - 0.5) {
					// the walkway, wherever nothing stands
					BlockPos at = column.atY(base + walk);
					if (!inGate && level.getBlockState(at).isAir()) {
						level.setBlock(at, slabTop(orc), QUIET);
					}
					continue;
				}
				wallColumn(level, frame, column, x, z, base, r, orc, inGate, wallHigh);
			}
		}
		// ladders up beside the side and back gates, and the front gate's towers
		for (int[] l : ladders(r, gateHalf)) {
			if (arcOf(l[0], l[1]) == arc) {
				ladder(level, camp, frame, l[0], l[1], Direction.values()[l[2]], walk);
			}
		}
		if (arc == 0) {
			tower(level, camp, frame, -gateHalf - 2, -r + 1, orc, wallHigh);
			tower(level, camp, frame, gateHalf + 2, -r + 1, orc, wallHigh);
		}
		return true;
	}

	/** Where ladders go up to a ring's walkway (camp frame x, z, and the ladder's facing as a {@link Direction} ordinal). */
	private static List<int[]> ladders(int r, int gateHalf) {
		int off = gateHalf + 1;
		return List.of(new int[] {off, r - 1, Direction.NORTH.ordinal()}, new int[] {r - 1, off, Direction.WEST.ordinal()},
			new int[] {-r + 1, off, Direction.EAST.ordinal()});
	}

	private static BlockState slabTop(boolean orc) {
		return (orc ? Blocks.DARK_OAK_SLAB : Blocks.SPRUCE_SLAB).defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	private static BlockState wallLog(boolean orc) {
		return (orc ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).defaultBlockState();
	}

	private static BlockState stake(boolean orc) {
		return (orc ? Blocks.DARK_OAK_FENCE : Blocks.SPRUCE_FENCE).defaultBlockState();
	}

	/**
	 * One column of a palisade on the land at {@code base}, in its camp's style: an orc stockade (a blackstone foot, dark
	 * oak logs, sharpened stakes, bone posts crowned with skulls) or a goblin palisade (spruce logs, stakes, skull posts);
	 * in a gate, a lintel with the way clear under it. Anything a player placed or anything built in the way, and it's
	 * left as a gap.
	 */
	private static void wallColumn(ServerLevel level, CampBuildings.Placer frame, BlockPos column, int x, int z, int base, int r, boolean orc,
		boolean inGate, int wallHigh) {
		int postHigh = orc ? OrcBuildings.POST_HIGH : GoblinBuildings.POST_HIGH;
		for (int y = 0; y <= postHigh; y++) {
			if (!clearable(level, column.atY(base + y))) {
				return;
			}
		}
		if (inGate) {
			for (int y = 0; y < wallHigh - 1; y++) {
				level.setBlock(column.atY(base + y), Blocks.AIR.defaultBlockState(), QUIET);
			}
			Direction.Axis along = Math.abs(x) > Math.abs(z) ? Direction.Axis.Z : Direction.Axis.X;
			level.setBlock(column.atY(base + wallHigh - 1), frame.turn(wallLog(orc).setValue(RotatedPillarBlock.AXIS, along)), QUIET);
			level.setBlock(column.atY(base + wallHigh), orc && (x == 0 || z == 0) ? Blocks.SKELETON_SKULL.defaultBlockState() : stake(orc), QUIET);
			return;
		}
		double every = orc ? OrcBuildings.POST_EVERY : GoblinBuildings.POST_EVERY;
		double angle = Math.toDegrees(Math.atan2(z, x)) + 360.0;
		boolean post = angle % every < Math.toDegrees(1.0 / r);
		int high = post ? postHigh : wallHigh;
		for (int y = 0; y < high; y++) {
			BlockState s = orc ? (y == 0 ? Blocks.BLACKSTONE.defaultBlockState() : post && y >= wallHigh - 1 ? Blocks.BONE_BLOCK.defaultBlockState() : wallLog(true))
				: wallLog(false);
			level.setBlock(column.atY(base + y), s, QUIET);
		}
		if (post) {
			level.setBlock(column.atY(base + postHigh), Blocks.SKELETON_SKULL.defaultBlockState(), QUIET);
		} else {
			int stakes = (x + z & 1) == 0 ? 2 : 1;
			for (int y = 0; y < stakes; y++) {
				level.setBlock(column.atY(base + wallHigh + y), stake(orc), QUIET);
			}
		}
		// (land falling away under the wall: its foot is filled in)
		for (int y = 1; y <= 3; y++) {
			BlockPos under = column.atY(base - y);
			if (!level.getBlockState(under).isAir() || Placed.any(level, under)) {
				break;
			}
			level.setBlock(under, orc ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), QUIET);
		}
	}

	/** A ladder up a ring's inside at camp-frame {@code (x, z)}, on the land there, to its walkway. */
	private static void ladder(ServerLevel level, Faction camp, CampBuildings.Placer frame, int x, int z, Direction facing, int walk) {
		BlockPos column = frame.at(x, 0, z);
		if (!ours(level, camp, column)) {
			return;
		}
		int base = ground(level, column, true);
		BlockState ladder = frame.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
		for (int y = 0; y <= walk; y++) {
			BlockPos at = column.atY(base + y);
			BlockState there = level.getBlockState(at);
			if (!(there.isAir() || there.is(BlockTags.WOODEN_SLABS) && !Placed.any(level, at))) {
				return;
			}
			if (ladder.canSurvive(level, at)) {
				level.setBlock(at, ladder, QUIET);
			}
		}
	}

	/** A gate tower (3 x 3, its camp's {@code TOWER_HIGH}) at camp-frame {@code (x, z)}, on the land there, as the first stockade's. */
	private static void tower(ServerLevel level, Faction camp, CampBuildings.Placer frame, int x, int z, boolean orc, int wallHigh) {
		int high = orc ? OrcBuildings.TOWER_HIGH : GoblinBuildings.TOWER_HIGH;
		BlockPos centre = frame.at(x, 0, z);
		if (!ours(level, camp, centre)) {
			return;
		}
		int base = ground(level, centre, true);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos column = frame.at(x + dx, 0, z + dz);
				for (int y = 0; y <= high + 1; y++) {
					BlockState s = level.getBlockState(column.atY(base + y));
					// (its own wall may already stand there: that's taken into the tower)
					if (!clearable(level, column.atY(base + y)) && !(s.is(BlockTags.LOGS) || s.is(BlockTags.FENCES) || s.is(BlockTags.WOODEN_SLABS)
						|| s.is(Blocks.SKELETON_SKULL) || s.is(Blocks.BONE_BLOCK) || s.is(Blocks.BLACKSTONE))) {
						return;
					}
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y < high; y++) {
					level.setBlock(frame.at(x + dx, base - frame.anchor().getY() + y, z + dz),
						orc && y < 2 ? Blocks.BLACKSTONE.defaultBlockState() : wallLog(orc), QUIET);
				}
				level.setBlock(frame.at(x + dx, base - frame.anchor().getY() + high, z + dz),
					(orc ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS).defaultBlockState(), QUIET);
				if (dx != 0 && dz != 0) {
					level.setBlock(frame.at(x + dx, base - frame.anchor().getY() + high + 1, z + dz), stake(orc), QUIET);
				}
			}
		}
		int lift = base - frame.anchor().getY();
		level.setBlock(frame.at(x, lift + high + 1, z), Blocks.SKELETON_SKULL.defaultBlockState(), QUIET);
		if (orc) {
			BlockPos banner = frame.at(x, lift + wallHigh, z - 2);
			if (level.getBlockState(banner).isAir()) {
				level.setBlock(banner, frame.turn(Blocks.WALL_BANNER.pick(DyeColor.RED).defaultBlockState()
					.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)), QUIET);
			}
		} else {
			BlockPos lamp = frame.at(x + (x < 0 ? 2 : -2), lift + wallHigh - 2, z - 1);
			if (level.getBlockState(lamp).isAir()) {
				level.setBlock(lamp, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), QUIET);
			}
		}
	}

	/** {@code s} is part of a palisade of the camp's style (what a cut gate takes out). */
	private static boolean wallPart(BlockState s, boolean orc) {
		return s.is(orc ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG) || s.is(orc ? Blocks.DARK_OAK_FENCE : Blocks.SPRUCE_FENCE)
			|| s.is(orc ? Blocks.DARK_OAK_SLAB : Blocks.SPRUCE_SLAB) || s.is(Blocks.LADDER) || s.is(Blocks.SKELETON_SKULL)
			|| orc && (s.is(Blocks.BLACKSTONE) || s.is(Blocks.BONE_BLOCK));
	}

	/**
	 * The tier's palisade is closed: gates are cut in the old ring (right, back and left: the front had one), with ladders
	 * beside them, and coarse-dirt roads run out from each old gate to the new ring's.
	 */
	private static boolean open(ServerLevel level, Faction camp, Camps.CampData data, int tier, boolean orc) {
		int old = WALL_RADIUS[tier - 2], now = WALL_RADIUS[tier - 1];
		int gateHalf = orc ? OrcBuildings.GATE_HALF : GoblinBuildings.GATE_HALF;
		int wallHigh = orc ? OrcBuildings.WALL_HIGH : GoblinBuildings.WALL_HIGH, walk = orc ? OrcBuildings.WALK_HIGH : GoblinBuildings.WALK_HIGH;
		CampBuildings.Placer frame = new CampBuildings.Placer(data.center(), data.gate(), 0);
		// (the four ways out loaded from the fire to past the new wall, or it waits)
		for (int[] end : new int[][] {{0, -now - 2}, {now + 2, 0}, {0, now + 2}, {-now - 2, 0}}) {
			if (!level.isLoaded(frame.at(end[0], 0, end[1]))) {
				return false;
			}
		}
		int floor = data.center().getY();
		for (int x = -old - 1; x <= old + 1; x++) {
			for (int z = -old - 1; z <= old + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				// (the front gate is there already)
				if (d < old - 1.5 || d >= old + 0.5 || !gate(x, z, gateHalf) || z < 0 && Math.abs(x) <= gateHalf) {
					continue;
				}
				BlockPos column = frame.at(x, 0, z);
				if (!ours(level, camp, column)) {
					continue;
				}
				// the column's own wall: from its lowest part, up to under a lintel
				int bottom = Integer.MIN_VALUE;
				for (int y = floor - MAX_DROP - 1; y <= floor + MAX_DROP + wallHigh; y++) {
					BlockPos at = column.atY(y);
					if (wallPart(level.getBlockState(at), orc) && !Placed.any(level, at)) {
						bottom = y;
						break;
					}
				}
				if (bottom == Integer.MIN_VALUE) {
					continue;
				}
				boolean walkway = d < old - 0.5;
				for (int y = bottom; y <= bottom + (walkway ? walk : wallHigh - 2); y++) {
					BlockPos at = column.atY(y);
					if (wallPart(level.getBlockState(at), orc) && !Placed.any(level, at)) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
					}
				}
				BlockPos lintel = column.atY(bottom + wallHigh - 1);
				if (!walkway && wallPart(level.getBlockState(lintel), orc) && !Placed.any(level, lintel)) {
					Direction.Axis along = Math.abs(x) > Math.abs(z) ? Direction.Axis.Z : Direction.Axis.X;
					level.setBlock(lintel, frame.turn(wallLog(orc).setValue(RotatedPillarBlock.AXIS, along)), QUIET);
				}
			}
		}
		for (int[] l : ladders(old, gateHalf)) {
			ladder(level, camp, frame, l[0], l[1], Direction.values()[l[2]], walk);
		}
		// the roads: from just inside each old gate, out through the band, to just outside the new ring
		for (int way = 0; way < 4; way++) {
			for (int along = old - 2; along <= now + 2; along++) {
				for (int across = -ROAD_HALF; across <= ROAD_HALF; across++) {
					int x = switch (way) {
						case 0 -> across;
						case 1 -> along;
						case 2 -> -across;
						default -> -along;
					};
					int z = switch (way) {
						case 0 -> -along;
						case 1 -> across;
						case 2 -> along;
						default -> -across;
					};
					BlockPos column = frame.at(x, 0, z);
					if (!ours(level, camp, column)) {
						continue;
					}
					BlockPos surface = column.atY(ground(level, column) - 1);
					BlockState s = level.getBlockState(surface);
					if ((s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK)) && !s.is(Blocks.COARSE_DIRT) && !Placed.any(level, surface)) {
						level.setBlock(surface, orc && (x + z & 1) == 1 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(),
							Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		return true;
	}

	// ---- the band: the planner -------------------------------------------------------------------------------------------

	/** What goes up in tier {@code tier}'s band, before the planner shuffles and places it. */
	private static List<Piece> list(boolean orc, int tier) {
		List<Piece> out = new ArrayList<>();
		if (orc) {
			Piece tentBlack = new Piece("orc_tent_black", () -> OrcBuildings.tent(true)), tentBrown = new Piece("orc_tent_brown", () -> OrcBuildings.tent(false));
			Piece totem = new Piece("orc_totem", OrcBuildings::totem), rack = new Piece("orc_trophy_rack", OrcBuildings::trophyRack);
			Piece tower = new Piece("orc_watchtower", OrcBuildings::watchtower), pit = new Piece("orc_pit", OrcBuildings::pit);
			Piece forge = new Piece("orc_forge", OrcBuildings::forge), spit = new Piece("orc_spit", OrcBuildings::spit);
			Piece market = new Piece("orc_market_square", () -> HordeBuildings.marketSquare(true)), den = new Piece("orc_ogre_den", HordeBuildings::ogreDen);
			Piece smelter = new Piece("orc_smelter", () -> HordeBuildings.smelter(true)), pens = new Piece("orc_war_beast_pens", () -> HordeBuildings.warBeastPens(true));
			switch (tier) {
				case 2 -> add(out, pens, 1, forge, 1, tentBlack, 2, tentBrown, 2, tower, 1, pit, 1, rack, 1, spit, 1, totem, 2);
				case 3 -> add(out, market, 1, den, 1, smelter, 1, pens, 1, tentBlack, 3, tentBrown, 2, tower, 2, rack, 2, totem, 3);
				default -> add(out, market, 1, smelter, 1, den, 2, pens, 2, forge, 1, tentBlack, 3, tentBrown, 3, tower, 3, pit, 2, rack, 2, totem, 4);
			}
		} else {
			Piece hutRed = new Piece("goblin_hut_red", () -> GoblinBuildings.hut(true)), hutBrown = new Piece("goblin_hut_brown", () -> GoblinBuildings.hut(false));
			Piece patch = new Piece("goblin_mushroom_patch", GoblinBuildings::mushroomPatch), totem = new Piece("goblin_totem", GoblinBuildings::totem);
			Piece lookout = new Piece("goblin_lookout", GoblinBuildings::lookout), tavern = new Piece("goblin_tavern", GoblinBuildings::tavern);
			Piece stewpot = new Piece("goblin_stewpot", GoblinBuildings::stewpot);
			Piece market = new Piece("goblin_market_square", () -> HordeBuildings.marketSquare(false)), brewery = new Piece("goblin_brewery", HordeBuildings::brewery);
			Piece smelter = new Piece("goblin_smelter", () -> HordeBuildings.smelter(false)), pens = new Piece("goblin_war_beast_pens", () -> HordeBuildings.warBeastPens(false));
			switch (tier) {
				case 2 -> add(out, pens, 1, tavern, 1, hutRed, 2, hutBrown, 2, lookout, 1, patch, 2, stewpot, 1, totem, 2);
				case 3 -> add(out, market, 1, brewery, 1, smelter, 1, pens, 1, hutRed, 3, hutBrown, 2, lookout, 2, patch, 3, totem, 3);
				default -> add(out, market, 1, smelter, 1, brewery, 2, tavern, 1, pens, 2, hutRed, 4, hutBrown, 3, lookout, 3, patch, 4, totem, 4);
			}
		}
		return out;
	}

	private static void add(List<Piece> out, Object... itemsAndCounts) {
		for (int i = 0; i < itemsAndCounts.length; i += 2) {
			for (int n = 0; n < (Integer)itemsAndCounts[i + 1]; n++) {
				out.add((Piece)itemsAndCounts[i]);
			}
		}
	}

	/** Camp {@code id}'s band plan for {@code tier} (worked out once, and the same every time for the same camp). */
	static List<IllagerBuildings.Spot> plan(MinecraftServer server, int id, Camps.CampData data, int tier) {
		Plan cached = PLANS.get(id);
		if (cached != null && cached.tier() == tier) {
			return cached.spots();
		}
		List<IllagerBuildings.Spot> spots = tier < 2 ? List.of() : planBand(data.orc(), tier,
			id * 0x9E3779B97F4A7C15L ^ data.center().asLong() ^ tier * 0x632BE59BD9B4E019L);
		PLANS.put(id, new Plan(tier, spots));
		return spots;
	}

	/**
	 * The angle-slot planner: the tier's list, shuffled by {@code seed} (biggest first, so they find room), each building
	 * tried round the band from its own seeded angle, at rows from the old wall out, facing the fire; the first slot where
	 * its whole footprint lies in the band, off the four ways out and clear of the others by {@link #GAP} is its. What finds
	 * no room is left out.
	 */
	private static List<IllagerBuildings.Spot> planBand(boolean orc, int tier, long seed) {
		Random random = new Random(seed);
		int inner = WALL_RADIUS[tier - 2] + BAND_INNER, outer = WALL_RADIUS[tier - 1] - BAND_OUTER;
		List<Piece> items = new ArrayList<>(list(orc, tier));
		java.util.Collections.shuffle(items, random);
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			order.add(i);
		}
		order.sort(java.util.Comparator.comparingInt(i -> -area(items.get(i))));
		List<int[]> taken = new ArrayList<>();
		IllagerBuildings.Spot[] placed = new IllagerBuildings.Spot[items.size()];
		double start = random.nextDouble() * 360.0;
		for (int i = 0; i < order.size(); i++) {
			Piece item = items.get(order.get(i));
			int[] b = bounds(item);
			Blueprint design = item.design().get();
			double from = start + i * 137.5 + random.nextDouble() * 20.0;
			List<Integer> rows = new ArrayList<>();
			for (int row = inner; row < outer; row += ROW_STEP) {
				rows.add(row);
			}
			java.util.Collections.shuffle(rows, random);
			IllagerBuildings.Spot found = null;
			for (int k = 0; k < 360.0 / ANGLE_STEP && found == null; k++) {
				double a = Math.toRadians(from + k * ANGLE_STEP);
				for (int row : rows) {
					int x = (int)Math.round(Math.sin(a) * row), z = (int)Math.round(-Math.cos(a) * row);
					int turns = Math.abs(x) > Math.abs(z) ? (x > 0 ? 3 : 1) : (z > 0 ? 0 : 2);
					IllagerBuildings.Spot spot = new IllagerBuildings.Spot(item.id(), item.design(), x, z, turns, design.width());
					int[] box = box(spot, b);
					if (fits(box, inner, outer, taken)) {
						found = spot;
						taken.add(box);
						break;
					}
				}
			}
			placed[order.get(i)] = found;
		}
		// (they go up in the order they were drawn, not biggest first)
		List<IllagerBuildings.Spot> out = new ArrayList<>();
		for (IllagerBuildings.Spot spot : placed) {
			if (spot != null) {
				out.add(spot);
			}
		}
		return out;
	}

	private static int area(Piece item) {
		int[] b = bounds(item);
		return (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
	}

	/** A design's footprint in its own frame (min x, min z, max x, max z, height), worked out once. */
	private static int[] bounds(Piece item) {
		return BOUNDS.computeIfAbsent(item.id(), id -> {
			Blueprint design = item.design().get();
			int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, design.height()};
			for (Blueprint.Placement p : design.blocks()) {
				b[0] = Math.min(b[0], p.offset().getX());
				b[1] = Math.min(b[1], p.offset().getZ());
				b[2] = Math.max(b[2], p.offset().getX());
				b[3] = Math.max(b[3], p.offset().getZ());
			}
			return b;
		});
	}

	/** {@code spot}'s footprint in the camp frame (min x, min z, max x, max z). */
	private static int[] box(IllagerBuildings.Spot spot, int[] b) {
		CampBuildings.Placer p = spot.placer(BlockPos.ZERO, Direction.NORTH);
		BlockPos c0 = p.at(b[0], 0, b[1]), c1 = p.at(b[2], 0, b[3]);
		return new int[] {Math.min(c0.getX(), c1.getX()), Math.min(c0.getZ(), c1.getZ()), Math.max(c0.getX(), c1.getX()), Math.max(c0.getZ(), c1.getZ())};
	}

	/** {@code box} lies wholly in the band, off the four ways out, and {@link #GAP} clear of every box {@code taken}. */
	private static boolean fits(int[] box, int inner, int outer, List<int[]> taken) {
		int nx = Math.clamp(0, box[0], box[2]), nz = Math.clamp(0, box[1], box[3]);
		if (nx * nx + nz * nz < inner * inner) {
			return false;
		}
		for (int x : new int[] {box[0], box[2]}) {
			for (int z : new int[] {box[1], box[3]}) {
				if (x * x + z * z > outer * outer) {
					return false;
				}
			}
		}
		if (box[0] <= WAY_CLEAR && box[2] >= -WAY_CLEAR || box[1] <= WAY_CLEAR && box[3] >= -WAY_CLEAR) {
			return false;
		}
		for (int[] o : taken) {
			if (box[0] <= o[2] + GAP && box[2] >= o[0] - GAP && box[1] <= o[3] + GAP && box[3] >= o[1] - GAP) {
				return false;
			}
		}
		return true;
	}

	// ---- the band: raising a building ------------------------------------------------------------------------------------

	private enum Raise {
		/** It stands. */
		PLACED,
		/** It can't stand there (the land's too rough, or something's in the way): passed over. */
		SKIPPED,
		/** Not now (a player near, the ground not loaded, or not enough in the pile). */
		WAIT
	}

	/**
	 * Raises a band building at once, on its own patch of levelled ground (the median height of its footprint): never over
	 * anything a player placed or anything built, nor on land too rough or far above or below the camp.
	 */
	private static Raise raise(ServerLevel level, Faction camp, Camps.CampData data, IllagerBuildings.Spot spot, boolean orc, boolean unseen,
		boolean all) {
		CampBuildings.Placer flat = spot.placer(data.center(), data.gate());
		Blueprint design = spot.design().get();
		if (!unseen && Raids.playerNear(level, flat.anchor(), BUILD_HIDE_RANGE)) {
			return Raise.WAIT;
		}
		int[] b = bounds(new Piece(spot.id(), spot.design()));
		List<BlockPos> columns = new ArrayList<>();
		List<Integer> heights = new ArrayList<>();
		for (int x = b[0]; x <= b[2]; x++) {
			for (int z = b[1]; z <= b[3]; z++) {
				BlockPos column = flat.at(x, 0, z);
				if (!level.isLoaded(column)) {
					return Raise.WAIT;
				}
				if (!ours(level, camp, column)) {
					return Raise.SKIPPED;
				}
				int g = ground(level, column);
				if (!level.getFluidState(column.atY(g - 1)).isEmpty()) {
					return Raise.SKIPPED;
				}
				columns.add(column);
				heights.add(g);
			}
		}
		List<Integer> sorted = new ArrayList<>(heights);
		sorted.sort(null);
		int floor = sorted.get(sorted.size() / 2);
		if (sorted.getLast() - sorted.getFirst() > PLOT_SPREAD || Math.abs(floor - data.center().getY()) > MAX_DROP) {
			return Raise.SKIPPED;
		}
		for (int i = 0; i < columns.size(); i++) {
			for (int y = Math.min(heights.get(i), floor); y <= floor + b[4] + 1; y++) {
				if (!clearable(level, columns.get(i).atY(y))) {
					return Raise.SKIPPED;
				}
			}
		}
		if (!all) {
			Item log = (orc ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).asItem();
			Item other = orc ? Items.WOOL.pick(DyeColor.BLACK) : Items.MUD_BRICKS;
			Map<Item, Integer> cost = Map.of(log, BUILD_LOGS, other, orc ? BUILD_HIDES : BUILD_MUD);
			if (!Storage.has(level, camp, cost)) {
				return Raise.WAIT;
			}
			for (var e : cost.entrySet()) {
				Storage.take(level, camp, e.getKey(), e.getValue());
			}
		}
		// the ground levelled: raised with dirt, cut down, the air above cleared
		for (int i = 0; i < columns.size(); i++) {
			BlockPos column = columns.get(i);
			int g = heights.get(i);
			BlockState surface = level.getBlockState(column.atY(g - 1));
			if (!Storehouse.ground(surface)) {
				surface = Blocks.COARSE_DIRT.defaultBlockState();
			}
			for (int y = g; y < floor - 1; y++) {
				level.setBlock(column.atY(y), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			for (int y = floor; y <= floor + b[4] + 1; y++) {
				if (!level.getBlockState(column.atY(y)).isAir()) {
					level.setBlock(column.atY(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			if (g != floor) {
				level.setBlock(column.atY(floor - 1), surface, Block.UPDATE_CLIENTS);
			}
		}
		CampBuildings.Placer placer = new CampBuildings.Placer(flat.anchor().atY(floor), flat.facing(), flat.width());
		CampBuildings.place(level, design, placer);
		for (Blueprint.Placement p : design.blocks()) {
			joinFence(level, placer.at(p.offset()));
		}
		JaymosKingdoms.LOGGER.debug("{} raised a {} in its band", camp.name(), spot.id());
		return Raise.PLACED;
	}

	private static void joinFence(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (state.is(BlockTags.FENCES)) {
			level.setBlock(at, Block.updateFromNeighbourShapes(state, level, at), Block.UPDATE_CLIENTS);
		}
	}

	// ---- the great hall --------------------------------------------------------------------------------------------------

	/**
	 * Tier 4: the great hall goes up over the longhouse (or great hut), in its footprint: what of the old building the new
	 * doesn't need comes down, the rest is laid over it; the throne stays where it was, and the hoard's chest is left as it
	 * stands (its loot in it). Nothing a player placed is touched.
	 */
	private static boolean greatHall(ServerLevel level, Camps.CampData data, boolean orc, boolean unseen) {
		IllagerBuildings.Spot spot = orc ? OrcBuildings.LONGHOUSE : GoblinBuildings.GREAT_HUT;
		CampBuildings.Placer placer = spot.placer(data.center(), data.gate());
		if (!level.isLoaded(placer.at(0, 0, 0)) || !unseen && Raids.playerNear(level, placer.at(0, 0, 0), BUILD_HIDE_RANGE)) {
			return false;
		}
		Blueprint old = spot.design().get(), hall = orc ? HordeBuildings.orcGreatHall() : HordeBuildings.goblinGreatHall();
		Set<BlockPos> fresh = new java.util.HashSet<>(), was = new java.util.HashSet<>();
		for (Blueprint.Placement p : hall.blocks()) {
			fresh.add(placer.at(p.offset()));
		}
		for (Blueprint.Placement p : old.blocks()) {
			was.add(placer.at(p.offset()));
		}
		for (Blueprint.Placement p : old.blocks()) {
			BlockPos at = placer.at(p.offset());
			if (!fresh.contains(at) && level.getBlockState(at).is(p.state().getBlock()) && !Placed.any(level, at)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (Blueprint.Placement p : hall.blocks()) {
			BlockPos at = placer.at(p.offset());
			BlockState there = level.getBlockState(at);
			if (Placed.any(level, at) || there.is(p.state().getBlock()) && (there.getBlock() instanceof ChestBlock || there.is(Blocks.BARREL))) {
				continue;
			}
			if (!there.isAir() && !clearable(level, at) && !was.contains(at)) {
				// (something not of the old building stands there: left be)
				continue;
			}
			level.setBlock(at, placer.turn(p.state()), Block.UPDATE_CLIENTS);
		}
		for (Blueprint.Placement p : hall.blocks()) {
			joinFence(level, placer.at(p.offset()));
		}
		JaymosKingdoms.LOGGER.info("A great hall rose at {}", placer.at(0, 0, 0));
		return true;
	}

	// ---- the command (operators; debug: plain text) ---------------------------------------------------------------------

	/** {@code /kingdom camp tier [1-4] [now]}: the nearest greenskin camp's tier and progress; or move it to a tier. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("tier").executes(Hordes::show)
			.then(Commands.argument("tier", IntegerArgumentType.integer(1, WALL_RADIUS.length))
				.executes(c -> set(c, IntegerArgumentType.getInteger(c, "tier"), false))
				.then(Commands.literal("now").executes(c -> set(c, IntegerArgumentType.getInteger(c, "tier"), true))));
	}

	private static @Nullable Faction nearest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction camp = Camps.nearestCamp(c);
		if (camp != null && !greenskin(c.getSource().getServer(), camp.id())) {
			c.getSource().sendFailure(Component.literal(camp.name() + " is no orc camp or goblin village"));
			return null;
		}
		return camp;
	}

	private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction camp = nearest(c);
		if (camp == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		Camps.CampData data = Camps.data(server, camp.id());
		if (data == null) {
			return 0;
		}
		c.getSource().sendSuccess(() -> describe(level, camp, data), false);
		return horde(server, camp.id()).tier();
	}

	/** The camp's tier and its progress (debug text). */
	static Component describe(ServerLevel level, Faction camp, Camps.CampData data) {
		MinecraftServer server = level.getServer();
		Horde h = horde(server, camp.id());
		StringBuilder out = new StringBuilder();
		out.append(camp.name()).append(": ").append(kindName(server, camp).getString()).append(" (tier ").append(h.tier()).append(", wall radius ")
			.append(WALL_RADIUS[h.tier() - 1]).append(", folk ").append(folk(camp, data.orc())).append('/').append(folkCap(server, camp.id(), data.orc()))
			.append(")");
		if (h.tier() >= 2) {
			out.append("\nthis tier: palisade ").append(h.arcs()).append('/').append(ARCS).append(" arcs").append(h.opened() ? ", gates and roads cut" : "")
				.append(", band ").append(Math.min(h.built(), plan(server, camp.id(), data, h.tier()).size())).append('/')
				.append(plan(server, camp.id(), data, h.tier()).size()).append(" buildings, ")
				.append(Math.max(0, data.grown() - h.since())).append(" days in it");
			if (h.tier() == WALL_RADIUS.length) {
				out.append(h.hall() ? ", the great hall stands" : ", the great hall to raise");
			}
		}
		if (h.tier() == WALL_RADIUS.length) {
			Component title = title(server, camp.id());
			List<String> vassals = Factions.all(server, FactionType.BARBARIAN).stream().filter(f -> f.overlord() == camp.id() && !f.fallen())
				.map(Faction::name).toList();
			out.append("\nrealm: its chief is ").append(title == null ? "?" : title.getString()).append(", ").append(camp.claims().size())
				.append(" chunks, vassals: ").append(vassals.isEmpty() ? "none" : String.join(", ", vassals));
		} else {
			List<String> missing = missing(level, camp, data, h);
			out.append("\nnext tier: ").append(missing.isEmpty() ? "ready (it moves up at its next day)" : "needs " + String.join(", ", missing));
		}
		if (camp.overlord() >= 0) {
			Faction lord = Factions.get(server, camp.overlord());
			out.append("\nvassal of ").append(lord == null ? "?" : lord.name());
		}
		return Component.literal(out.toString());
	}

	/**
	 * Moves the nearest camp to {@code target}: up step by step (each tier between built at once, so it looks as it would
	 * have; the last one goes up over the coming days, or at once with {@code now}), or down (its walls stay).
	 */
	private static int set(CommandContext<CommandSourceStack> c, int target, boolean now) throws CommandSyntaxException {
		Faction camp = nearest(c);
		if (camp == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		int id = camp.id();
		Horde h = horde(server, id);
		if (target < h.tier()) {
			put(server, id, new Horde(target, 0, ARCS, Integer.MAX_VALUE / 2, true, true, h.hoard()));
			PLANS.remove(id);
			if (h.tier() == WALL_RADIUS.length) {
				fell(server, id);
			}
		} else {
			complete(level, id);
			while (horde(server, id).tier() < target) {
				Faction f = Factions.get(server, id);
				Camps.CampData data = Camps.data(server, id);
				if (f == null || data == null) {
					return 0;
				}
				promote(level, f, data, true);
				if (horde(server, id).tier() < target || now) {
					complete(level, id);
				}
			}
		}
		Faction after = Factions.get(server, id);
		Camps.CampData data = Camps.data(server, id);
		if (after != null && data != null) {
			c.getSource().sendSuccess(() -> describe(level, after, data), true);
		}
		return 1;
	}
}
