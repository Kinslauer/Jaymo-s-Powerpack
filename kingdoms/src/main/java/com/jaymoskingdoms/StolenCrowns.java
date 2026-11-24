package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.Creatures;
import com.jaymoskingdoms.creatures.Shapeshifter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The stolen crown (Andy, 2026-11-23: "you can have captive royalty, it just needs to really hide them, and the kidnapper
 * would have to take their place and probably be easily found out. if that happens the whole kingdom is on alert till they
 * are found, you finding a king or queen this way should be extremely handsomely rewarded"; plan:
 * {@code docs/Imposter-Ruler-Plan.md}, tracks 1 and 4).
 * <ul>
 * <li><b>Taken</b>: rarely, while a player is near a realm's seat, its villager ruler is carried off: saved and taken out of
 * the world, and a copy of them ({@link Doubles#COPY}, {@link #COPY_OF}) stands where they stood and takes the throne, so
 * every part of the realm treats it as the ruler.</li>
 * <li><b>The vault</b>: the real one waits, saved, for a spot {@link #VAULT_FROM} to {@link #VAULT_TO} blocks from the seat,
 * clear of every settlement. When a player first comes near it a sealed vault is dug there (a buried hatch under a cairn of
 * stones, a ladder down, a stone room), the ruler is put in it in chains as their own realm's {@link Captives#ROYAL}
 * captive, and shapeshifters keep it.</li>
 * <li><b>Found out</b> ({@link Doubles#expose}, however it happens): the copy is off the throne, the late ruler's blood (their
 * spouse, their kin, else the best of the town) keeps it ({@link Rulers#crown}: the realm's house is still the real one's),
 * and the realm is told which way the thing that wore their face fled. Each dawn after, its news narrows where to look.</li>
 * <li><b>Brought home</b> ({@link Captives}' walk home, then {@link #home}): the keeper steps down, the ruler is crowned
 * again, and whoever brought them is paid a royal bounty from the treasury, given an estate charter (Captives), and
 * titled: a sworn member of the realm rises to Knight (or Lord, if already a Knight); anyone else earns the title
 * "Saviour of" the realm ({@link #HONOURED}, {@link TitleChoice}).</li>
 * </ul>
 */
public final class StolenCrowns {
	// ---- tuning ----
	/** How often realms are looked at (ticks), and the chance each check that a ruled seat with a player near loses its ruler. */
	static final int CHECK = 1200;
	static final float CHANCE = 0.004F;
	/** A realm's crown can't be taken again for this long after it was (ticks: 20 days). */
	static final long GAP = 20L * 24000L;
	/** A player must be this near the seat (blocks), and the ruler this near it too. */
	static final double SEAT_RANGE = 64.0;
	/** Where the vault goes: this far from the seat (blocks), at least this far from any settlement's seat. */
	static final int VAULT_FROM = 220, VAULT_TO = 400;
	static final double CLEAR_OF = 120.0;
	/** The vault is dug when a player first comes this near its spot (blocks). */
	static final double VAULT_PLACE = 56.0;
	/** How deep its floor is under the ground, its room's half width, and how many shapeshifters keep it. */
	static final int VAULT_DEPTH = 12, ROOM_HALF = 2, KEEPERS_LEAST = 2, KEEPERS_MOST = 3;
	// ---- the tells (track 2) ----
	/** The copy slips on its own court this near a player (blocks), with this chance a look, at most this often (ticks). */
	static final double SLIP_RANGE = 5.0;
	static final float SLIP_CHANCE = 0.4F;
	static final long SLIP_GAP = 300L;
	/** The chance each dawn that the copy hands down an odd decree; how near a player its people grumble about it (blocks), how often (ticks). */
	static final float DECREE_CHANCE = 0.5F;
	static final double GRUMBLE_RANGE = 8.0;
	static final long GRUMBLE_GAP = 2400L;
	/** The real one's spouse, kin or court wizard tells a player this near (blocks) that the ruler isn't themself, once a day to each. */
	static final double NOTICE_RANGE = 6.0;
	static final long NOTICE_GAP = 24000L;
	/** A guard this near the copy (blocks), a player within this of it, challenges it with this chance a look; this many challenges and its face slips. */
	static final double CHALLENGE_RANGE = 6.0, CHALLENGE_SEEN = 16.0;
	static final float CHALLENGE_CHANCE = 0.03F;
	static final int CHALLENGES = 2;
	/** Crest beasts a copy may name for its own, wrongly. */
	static final java.util.List<String> BEASTS = java.util.List.of("dragon", "lion", "stag", "boar", "eagle", "pegasus", "unicorn", "serpent");
	// ---- the realm on alert (track 3) ----
	/** A stranger this near the seat (blocks) is warned by a guard that the gates are shut, at most this often (ticks). */
	static final double WARN_RANGE = 48.0;
	static final long WARN_GAP = 1200L;
	/** By day, this many guards set out searching this far toward the trail (blocks), this often (ticks). */
	static final int SEARCHERS = 3, SEARCH_OUT = 70;
	static final long SEARCH_GAP = 2400L;

	/** A taken ruler: who, the copy on the throne, their saved body until the vault is dug, its spot, since when, found out yet, the last day a hint went out, the day it was found out. */
	record Taken(UUID real, UUID copy, CompoundTag body, int x, int z, boolean placed, long since, boolean unmasked, long hinted, long found) {
		static final Codec<Taken> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("real").forGetter(Taken::real),
				UUIDUtil.CODEC.fieldOf("copy").forGetter(Taken::copy),
				CompoundTag.CODEC.fieldOf("body").forGetter(Taken::body),
				Codec.INT.fieldOf("x").forGetter(Taken::x),
				Codec.INT.fieldOf("z").forGetter(Taken::z),
				Codec.BOOL.optionalFieldOf("placed", false).forGetter(Taken::placed),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Taken::since),
				Codec.BOOL.optionalFieldOf("unmasked", false).forGetter(Taken::unmasked),
				Codec.LONG.optionalFieldOf("hinted", -1L).forGetter(Taken::hinted),
				Codec.LONG.optionalFieldOf("found", -1L).forGetter(Taken::found))
			.apply(i, Taken::new));

		Taken placedNow() {
			return new Taken(real, copy, new CompoundTag(), x, z, true, since, unmasked, hinted, found);
		}

		Taken unmaskedOn(long day) {
			return new Taken(real, copy, body, x, z, placed, since, true, day, day);
		}

		Taken hintedOn(long day) {
			return new Taken(real, copy, body, x, z, placed, since, unmasked, day, found);
		}
	}

	/** On the overworld: each realm's taken ruler, by realm id. Saved. */
	static final AttachmentType<Map<String, Taken>> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("stolen_crowns"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Taken.CODEC)));
	/** On the overworld: when each realm's crown was last taken. Saved. */
	static final AttachmentType<Map<String, Long>> LAST = AttachmentRegistry.create(JaymosKingdoms.id("stolen_crowns_last"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** On the copy on a throne: the realm it sits on. Saved. */
	static final AttachmentType<Integer> COPY_OF = AttachmentRegistry.create(JaymosKingdoms.id("crown_copy"), b -> b.persistent(Codec.INT));
	/** On a player who brought a ruler home without being sworn to the realm: its name, for the title. Saved, kept through death. */
	static final AttachmentType<String> HONOURED = AttachmentRegistry.create(JaymosKingdoms.id("crown_saviour"),
		b -> b.persistent(Codec.STRING).copyOnDeath());

	/** This session: when each copy last slipped, when each player was last told, grumbled at or warned, each copy's challenges, each realm's last search, the day's decree. */
	private static final Map<UUID, Long> SLIPPED = new HashMap<>(), NOTICED = new HashMap<>(), GRUMBLED = new HashMap<>(), WARNED = new HashMap<>();
	private static final Map<UUID, Integer> CHALLENGED = new HashMap<>();
	private static final Map<Integer, Long> SEARCHED = new HashMap<>();
	private static final Map<Integer, long[]> DECREED = new HashMap<>();

	private StolenCrowns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StolenCrowns::tick);
	}

	private static Map<String, Taken> taken(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(TAKEN, Map.of());
	}

	private static void put(MinecraftServer server, int faction, @Nullable Taken t) {
		Map<String, Taken> all = new HashMap<>(taken(server));
		if (t == null) {
			all.remove(Integer.toString(faction));
		} else {
			all.put(Integer.toString(faction), t);
		}
		server.overworld().setAttached(TAKEN, Map.copyOf(all));
	}

	/** Whether {@code f}'s ruler is taken just now (the one on its throne is a copy, or the throne is kept for them). */
	static boolean taken(MinecraftServer server, Faction f) {
		return taken(server).containsKey(Integer.toString(f.id()));
	}

	// ---- the taking -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (!taken(server).isEmpty()) {
			vaults(level);
			hints(level);
			tells(level);
			onAlert(level);
		}
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (level.getRandom().nextFloat() < CHANCE && takeable(level, f)) {
				take(level, f);
			}
		}
	}

	/** Whether {@code f}'s ruler could be taken now: a realm's villager ruler on its throne, a player near, not lately, not a haven's, not at war. */
	private static boolean takeable(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		if (f.fallen() || f.type() != FactionType.KINGDOM && f.type() != FactionType.FIEFDOM || !Rulers.inPlace(f) || taken(server, f)
			|| Havens.is(server, f) || !Wars.warsOf(server, f.id()).isEmpty()) {
			return false;
		}
		Long last = server.overworld().getAttachedOrElse(LAST, Map.of()).get(Integer.toString(f.id()));
		if (last != null && server.overworld().getGameTime() - last < GAP) {
			return false;
		}
		PathfinderMob ruler = Rulers.of(level, f);
		if (!(ruler instanceof Villager v) || !Doubles.stealable(v) || Doubles.isCopy(v) || Captives.bound(v) || v.isSleeping()
			|| com.jaymoskingdoms.Downed.isDowned(v) || v.blockPosition().distSqr(f.seat()) > SEAT_RANGE * SEAT_RANGE) {
			return false;
		}
		return level.getNearestPlayer(f.seat().getX() + 0.5, f.seat().getY(), f.seat().getZ() + 0.5, SEAT_RANGE, p -> !p.isSpectator()) != null;
	}

	/** {@code f}'s ruler is carried off, and a copy takes their place on the throne. */
	private static void take(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		if (!(Rulers.of(level, f) instanceof Villager real)) {
			return;
		}
		BlockPos vault = vaultSpot(level, f);
		if (vault == null) {
			return;
		}
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!real.saveAsPassenger(out)) {
			return;
		}
		CompoundTag body = out.buildResult();
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), body), level,
			EntitySpawnReason.LOAD, e -> {
				e.setUUID(UUID.randomUUID());
				e.snapTo(real.getX(), real.getY(), real.getZ(), real.getYRot(), real.getXRot());
				return e;
			});
		if (!(loaded instanceof Villager copy)) {
			return;
		}
		UUID realId = real.getUUID();
		real.discard();
		copy.setAttached(Doubles.COPY, realId);
		copy.setAttached(COPY_OF, f.id());
		copy.setPersistenceRequired();
		level.addFreshEntity(copy);
		// (the realm's house stays the real one's: their family keep the throne if the copy is found out)
		Factions.update(server, f.id(), x -> x.withLeader(java.util.Optional.of(copy.getUUID())));
		long now = server.overworld().getGameTime();
		put(server, f.id(), new Taken(realId, copy.getUUID(), body, vault.getX(), vault.getZ(), false, now, false, -1L, -1L));
		Map<String, Long> last = new HashMap<>(server.overworld().getAttachedOrElse(LAST, Map.of()));
		last.put(Integer.toString(f.id()), now);
		server.overworld().setAttached(LAST, Map.copyOf(last));
		JaymosKingdoms.LOGGER.info("{}'s ruler {} was taken; a copy sits the throne; the vault will be at {}, {}", f.name(), copy.getName().getString(),
			vault.getX(), vault.getZ());
	}

	/** A spot for the vault: {@link #VAULT_FROM} to {@link #VAULT_TO} from {@code f}'s seat, {@link #CLEAR_OF} from every settlement's. */
	private static @Nullable BlockPos vaultSpot(ServerLevel level, Faction f) {
		for (int tries = 0; tries < 12; tries++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			int dist = VAULT_FROM + level.getRandom().nextInt(VAULT_TO - VAULT_FROM + 1);
			BlockPos at = new BlockPos(f.seat().getX() + (int) (Math.cos(angle) * dist), f.seat().getY(), f.seat().getZ() + (int) (Math.sin(angle) * dist));
			boolean clear = true;
			for (Faction o : Factions.all(level.getServer())) {
				if (!o.fallen() && o.seat().distSqr(at) < CLEAR_OF * CLEAR_OF) {
					clear = false;
					break;
				}
			}
			if (clear) {
				return at;
			}
		}
		return null;
	}

	// ---- the vault --------------------------------------------------------------------------------------------------

	/** Digs the vaults a player has come near, and puts their rulers in them. */
	private static void vaults(ServerLevel level) {
		MinecraftServer server = level.getServer();
		for (Map.Entry<String, Taken> e : Map.copyOf(taken(server)).entrySet()) {
			Taken t = e.getValue();
			if (t.placed()) {
				continue;
			}
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (f == null || f.fallen()) {
				put(server, Integer.parseInt(e.getKey()), null);
				continue;
			}
			BlockPos spot = new BlockPos(t.x(), 0, t.z());
			Player near = level.getNearestPlayer(t.x() + 0.5, level.getHeight(Heightmap.Types.WORLD_SURFACE, t.x(), t.z()), t.z() + 0.5, VAULT_PLACE,
				p -> !p.isSpectator());
			if (near == null || !Loaded.made(level, spot.offset(-ROOM_HALF - 2, 0, -ROOM_HALF - 2), spot.offset(ROOM_HALF + 2, 0, ROOM_HALF + 4))) {
				continue;
			}
			Villager real = dig(level, f, t);
			if (real != null) {
				put(server, f.id(), t.placedNow());
			}
		}
	}

	/** The sealed vault at {@code t}'s spot, with the ruler chained in it and its keepers; the ruler, or null if they couldn't be put there. */
	private static @Nullable Villager dig(ServerLevel level, Faction f, Taken t) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, new BlockPos(t.x(), 0, t.z())).below();
		BlockPos floor = top.below(VAULT_DEPTH);
		int h = ROOM_HALF;
		// the room: stone brick walls, a floor, a roof, air inside
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int dz = -h - 1; dz <= h + 1; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					boolean wall = dx == -h - 1 || dx == h + 1 || dz == -h - 1 || dz == h + 1 || dy == -1 || dy == 3;
					level.setBlock(floor.offset(dx, dy, dz), (wall ? Blocks.MOSSY_STONE_BRICKS : Blocks.AIR).defaultBlockState(), 3);
				}
			}
		}
		// the shaft up the south side, a ladder in it, to a hatch a block under the turf, and a cairn over that
		int sx = floor.getX(), sz = floor.getZ() + h + 2;
		level.setBlock(new BlockPos(sx, floor.getY(), floor.getZ() + h + 1), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(new BlockPos(sx, floor.getY() + 1, floor.getZ() + h + 1), Blocks.AIR.defaultBlockState(), 3);
		for (int y = floor.getY(); y < top.getY() - 1; y++) {
			BlockPos behind = new BlockPos(sx, y, sz + 1);
			if (!level.getBlockState(behind).isSolid()) {
				level.setBlock(behind, Blocks.STONE_BRICKS.defaultBlockState(), 3);
			}
			level.setBlock(new BlockPos(sx, y, sz), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
		}
		level.setBlock(new BlockPos(sx, top.getY() - 1, sz), Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), 3);
		level.setBlock(new BlockPos(sx, top.getY(), sz), Blocks.COARSE_DIRT.defaultBlockState(), 3);
		for (BlockPos stone : new BlockPos[] {new BlockPos(sx, top.getY() + 1, sz), new BlockPos(sx + 1, top.getY() + 1, sz),
			new BlockPos(sx, top.getY() + 1, sz + 1), new BlockPos(sx, top.getY() + 2, sz)}) {
			level.setBlock(stone, Blocks.COBBLESTONE.defaultBlockState(), 3);
		}
		level.setBlock(floor.offset(0, 2, 0), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 3);
		// the ruler, in chains
		BlockPos in = floor.offset(0, 0, -1);
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), t.body()), level,
			EntitySpawnReason.LOAD, e -> {
				e.snapTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, e.getYRot(), 0.0F);
				return e;
			});
		if (!(loaded instanceof Villager real)) {
			return null;
		}
		// (off the throne while they're held: not a ruler till they're crowned again at home)
		McaCompat.discharge(real);
		Rulers.unfit(real);
		real.setPersistenceRequired();
		level.addFreshEntity(real);
		Captives.hold(level, real, f, Captives.ROYAL);
		// its keepers
		int keepers = KEEPERS_LEAST + level.getRandom().nextInt(KEEPERS_MOST - KEEPERS_LEAST + 1);
		for (int i = 0; i < keepers; i++) {
			Shapeshifter s = Creatures.SHAPESHIFTER.create(level, EntitySpawnReason.EVENT);
			if (s == null) {
				continue;
			}
			s.snapTo(floor.getX() + 0.5 + (i - 1), floor.getY(), floor.getZ() + 1.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			s.setPersistenceRequired();
			level.addFreshEntity(s);
		}
		JaymosKingdoms.LOGGER.info("{}'s taken ruler {} is held in a vault at {}", f.name(), real.getName().getString(), floor);
		return real;
	}

	// ---- found out ------------------------------------------------------------------------------------------------

	/**
	 * ({@link Doubles#expose}, before the face comes off) {@code copy} is found out: if it sat on a throne, it's off it, the
	 * ruler's blood keeps the throne, and the realm hears which way the thing fled.
	 */
	static void unmasked(ServerLevel level, Villager copy, @Nullable LivingEntity by) {
		Integer id = copy.getAttached(COPY_OF);
		if (id == null) {
			return;
		}
		copy.removeAttached(COPY_OF);
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, id);
		Taken t = taken(server).get(Integer.toString(id));
		if (f == null || t == null || f.fallen()) {
			return;
		}
		Component wore = copy.getDisplayName();
		if (f.leader().map(copy.getUUID()::equals).orElse(false)) {
			Rulers.stepDown(level, f, copy);
		}
		Faction after = Factions.get(server, f.id());
		Faction kept = after == null ? null : KingdomEvents.crown(level, after);
		PathfinderMob keeper = kept == null ? null : Rulers.of(level, kept);
		long day = Production.day(server);
		put(server, f.id(), t.unmaskedOn(day));
		Vec3 seat = Vec3.atCenterOf(f.seat());
		Component way = Directions.heading(seat, new Vec3(t.x() + 0.5, seat.y, t.z() + 0.5));
		Post.news(server, Component.translatable("news.jaymoskingdoms.stolen_crown.found_out", wore, f.name(), way).withStyle(ChatFormatting.GOLD));
		if (keeper != null) {
			Post.news(server, Component.translatable("news.jaymoskingdoms.stolen_crown.keeper", keeper.getDisplayName(), f.name()));
		}
		JaymosKingdoms.LOGGER.info("The copy on {}'s throne was found out; {} keeps it", f.name(), keeper == null ? "nobody" : keeper.getName().getString());
	}

	/** Each dawn while a found-out realm's ruler is still away, its news says a little more about where to look. */
	private static void hints(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long day = Production.day(server);
		for (Map.Entry<String, Taken> e : Map.copyOf(taken(server)).entrySet()) {
			Taken t = e.getValue();
			if (!t.unmasked() || t.hinted() >= day) {
				continue;
			}
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (f == null || f.fallen()) {
				continue;
			}
			long days = day - t.found();
			Vec3 seat = Vec3.atCenterOf(f.seat());
			Vec3 vault = new Vec3(t.x() + 0.5, seat.y, t.z() + 0.5);
			Component way = Directions.heading(seat, vault);
			double dist = seat.distanceTo(vault);
			// (the first dawn after: how far; after that, the cairn itself)
			Component hint = days <= 1
				? Component.translatable("news.jaymoskingdoms.stolen_crown.hint.far", f.name(), way, Directions.distance(dist))
				: Component.translatable("news.jaymoskingdoms.stolen_crown.hint.cairn", f.name(), way, Directions.distance(dist));
			Post.news(server, hint);
			put(server, f.id(), t.hintedOn(day));
		}
	}

	// ---- home -------------------------------------------------------------------------------------------------------

	/**
	 * ({@link Captives}' homecoming, before the bounty) {@code real}, a taken ruler, is home with {@code player}: whoever keeps
	 * the throne steps down, they're crowned again, and {@code player} is titled.
	 */
	static void home(ServerLevel level, ServerPlayer player, Villager real, Faction home) {
		MinecraftServer server = level.getServer();
		Taken t = taken(server).get(Integer.toString(home.id()));
		if (t == null || !t.real().equals(real.getUUID())) {
			return;
		}
		put(server, home.id(), null);
		Faction f = Factions.get(server, home.id());
		if (f == null || f.fallen()) {
			return;
		}
		PathfinderMob sitting = Rulers.of(level, f);
		if (sitting != null) {
			if (sitting.hasAttached(COPY_OF)) {
				// (the copy still there and not yet seen to: its face comes off now, in front of them both)
				Doubles.expose(level, (Villager) sitting, player);
			}
			Faction now = Factions.get(server, f.id());
			PathfinderMob still = now == null ? null : Rulers.of(level, now);
			if (still != null) {
				Rulers.stepDown(level, now, still);
			}
		}
		Faction ready = Factions.get(server, f.id());
		if (ready != null) {
			KingdomEvents.crown(level, ready, real.getUUID());
		}
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.stolen_crown.home", real.getDisplayName(), f.name(),
			player.getDisplayName()).withStyle(ChatFormatting.GOLD));
		title(server, player, f);
	}

	/** {@code player} brought {@code f}'s ruler home: a sworn member rises to Knight (a Knight to Lord); anyone else is its Saviour. */
	private static void title(MinecraftServer server, ServerPlayer player, Faction f) {
		Faction.Member member = f.member(player.getUUID());
		if (member != null) {
			int to = member.standing() >= Rank.KNIGHT.standing ? Math.max(member.standing(), Rank.LORD.standing) : Rank.KNIGHT.standing;
			KingdomEvents.standing(server, player, f, to);
			return;
		}
		player.setAttached(HONOURED, f.name());
	}

	/** The title {@code player} earned by bringing a ruler home unsworn ("Saviour of" the realm), or null. */
	static @Nullable Component saviour(Player player) {
		String realm = player.getAttached(HONOURED);
		return realm == null ? null : Component.translatable("title.jaymoskingdoms.crown_saviour", realm);
	}

	// ---- the tells (track 2) --------------------------------------------------------------------------------------

	/** The copies on thrones give themselves away: court slips, odd decrees, the ruler's own people, the guards. */
	private static void tells(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long now = server.overworld().getGameTime(), day = Production.day(server);
		for (Map.Entry<String, Taken> e : Map.copyOf(taken(server)).entrySet()) {
			Taken t = e.getValue();
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (t.unmasked() || f == null || f.fallen() || !(level.getEntity(t.copy()) instanceof Villager copy) || !copy.isAlive()) {
				continue;
			}
			decree(level, f, copy, day);
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.distanceToSqr(copy) > 48.0 * 48.0) {
					continue;
				}
				if (player.distanceTo(copy) <= SLIP_RANGE) {
					slip(level, copy, player, f, t, now);
				}
				notice(level, player, t, f, now);
				grumble(level, player, f, day, now);
			}
			challenge(level, copy);
		}
	}

	/** The copy gets its own court wrong in {@code player}'s hearing: its heir, its crest, the name of its own seat. Heard, sneak and speak to it. */
	private static void slip(ServerLevel level, Villager copy, ServerPlayer player, Faction f, Taken t, long now) {
		Long last = SLIPPED.get(copy.getUUID());
		if (last != null && now - last < SLIP_GAP || level.getRandom().nextFloat() > SLIP_CHANCE) {
			return;
		}
		SLIPPED.put(copy.getUUID(), now);
		Component line = switch (level.getRandom().nextInt(3)) {
			case 0 -> Component.translatable("talk.jaymoskingdoms.stolen_crown.slip.heir", Doubles.wrongWife(t.real()));
			case 1 -> Component.translatable("talk.jaymoskingdoms.stolen_crown.slip.crest",
				Component.translatable("crest.jaymoskingdoms.stolen_crown." + BEASTS.get(level.getRandom().nextInt(BEASTS.size()))));
			default -> Component.translatable("talk.jaymoskingdoms.stolen_crown.slip.seat", wrongRealm(level, f));
		};
		Speech.say(level, copy, line, Speech.WHITE, SLIP_RANGE + 6.0);
		Doubles.heard(copy, player);
	}

	/** Another realm's name (or a made-up one), for the copy to call its own seat. */
	private static String wrongRealm(ServerLevel level, Faction f) {
		java.util.List<Faction> others = Factions.all(level.getServer()).stream().filter(o -> o.id() != f.id() && !o.fallen()
			&& o.type() != FactionType.BARBARIAN).toList();
		return others.isEmpty() ? f.name() + "shire" : others.get(level.getRandom().nextInt(others.size())).name();
	}

	/** Once a day the copy may hand down a decree no ruler of the house would; the realm's news carries it. */
	private static void decree(ServerLevel level, Faction f, Villager copy, long day) {
		long[] d = DECREED.get(f.id());
		if (d != null && d[0] == day) {
			return;
		}
		int kind = level.getRandom().nextFloat() < DECREE_CHANCE ? level.getRandom().nextInt(3) : -1;
		DECREED.put(f.id(), new long[] {day, kind});
		if (kind >= 0) {
			Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.stolen_crown.decree." + kind, copy.getDisplayName(), f.name()));
		}
	}

	/** On a day the copy decreed something, its people near {@code player} grumble about it. */
	private static void grumble(ServerLevel level, ServerPlayer player, Faction f, long day, long now) {
		long[] d = DECREED.get(f.id());
		Long last = GRUMBLED.get(player.getUUID());
		if (d == null || d[0] != day || d[1] < 0 || last != null && now - last < GRUMBLE_GAP) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(GRUMBLE_RANGE), v -> v.isAlive() && !v.isBaby()
			&& !v.isSleeping() && !Doubles.isCopy(v) && !Rulers.is(v) && !Captives.bound(v))) {
			GRUMBLED.put(player.getUUID(), now);
			Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.stolen_crown.grumble." + d[1]), Speech.YELLOW, GRUMBLE_RANGE + 4.0);
			return;
		}
	}

	/** The real one's spouse, kin or court wizard, near {@code player}, says the ruler isn't themself. */
	private static void notice(ServerLevel level, ServerPlayer player, Taken t, Faction f, long now) {
		Long last = NOTICED.get(player.getUUID());
		if (last != null && now - last < NOTICE_GAP) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NOTICE_RANGE), v -> v.isAlive() && !v.isBaby()
			&& !v.isSleeping() && !Doubles.isCopy(v))) {
			int kin = McaCompat.kinOf(v, t.real());
			boolean wizard = CourtWizards.is(v);
			if (kin == McaCompat.STRANGERS && !wizard) {
				continue;
			}
			NOTICED.put(player.getUUID(), now);
			String who = kin == McaCompat.PARTNER ? "spouse" : kin == McaCompat.KIN ? "kin" : "wizard";
			Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.stolen_crown.notice." + who, f.name()), Speech.YELLOW, NOTICE_RANGE + 6.0);
			return;
		}
	}

	/** Now and then a sharp-eyed guard near the copy, a player watching, challenges it; {@link #CHALLENGES} of them and its face slips. */
	private static void challenge(ServerLevel level, Villager copy) {
		if (level.getRandom().nextFloat() > CHALLENGE_CHANCE || level.getNearestPlayer(copy, CHALLENGE_SEEN) == null) {
			return;
		}
		for (PathfinderMob guard : level.getEntitiesOfClass(PathfinderMob.class, copy.getBoundingBox().inflate(CHALLENGE_RANGE),
			g -> g.isAlive() && g != copy && McaCompat.isGuard(g))) {
			Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.stolen_crown.challenge"), Speech.YELLOW, CHALLENGE_SEEN);
			int times = CHALLENGED.merge(copy.getUUID(), 1, Integer::sum);
			if (times >= CHALLENGES) {
				CHALLENGED.remove(copy.getUUID());
				Doubles.expose(level, copy, null);
			} else {
				Speech.say(level, copy, Component.translatable("talk.jaymoskingdoms.stolen_crown.stammer"), Speech.WHITE, CHALLENGE_SEEN);
			}
			return;
		}
	}

	// ---- the realm on alert (track 3) -------------------------------------------------------------------------------

	/**
	 * Whether {@code f} (or the realm above it) is on alert: its ruler's face came off on the throne and they aren't home yet.
	 * Its people doubt every face ({@link Doubts}), its caravans stay in ({@link Caravans}, {@link TradeRoutes}), it doesn't
	 * trade with strangers ({@link #refusesTrade}), and its guards warn strangers off and ride out searching.
	 */
	public static boolean alert(MinecraftServer server, @Nullable Faction f) {
		if (f == null) {
			return false;
		}
		Map<String, Taken> all = taken(server);
		if (all.isEmpty()) {
			return false;
		}
		Taken t = all.get(Integer.toString(f.id()));
		if (t == null) {
			Faction top = Wars.top(server, f);
			t = top == null ? null : all.get(Integer.toString(top.id()));
		}
		return t != null && t.unmasked();
	}

	/** Whether {@code player} is one of {@code f}'s own: sworn to it (or the realm above it), or a householder there. */
	private static boolean ofTheRealm(MinecraftServer server, ServerPlayer player, Faction f) {
		Faction top = Wars.top(server, f);
		return f.member(player.getUUID()) != null || top != null && top.member(player.getUUID()) != null || ForgedPapers.householder(player, f);
	}

	/** The realms on alert: their guards warn strangers off, and by day ride out searching toward the trail. */
	private static void onAlert(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long now = server.overworld().getGameTime();
		for (Map.Entry<String, Taken> e : Map.copyOf(taken(server)).entrySet()) {
			Taken t = e.getValue();
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (!t.unmasked() || f == null || f.fallen()) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				Long last = WARNED.get(player.getUUID());
				if (player.isSpectator() || player.blockPosition().distSqr(f.seat()) > WARN_RANGE * WARN_RANGE || last != null && now - last < WARN_GAP
					|| ofTheRealm(server, player, f)) {
					continue;
				}
				for (PathfinderMob guard : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(16.0), g -> g.isAlive() && McaCompat.isGuard(g))) {
					WARNED.put(player.getUUID(), now);
					Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.stolen_crown.gates_shut", f.name()), Speech.YELLOW, 20.0);
					break;
				}
			}
			search(level, f, t, now);
		}
	}

	/** By day, a few of {@code f}'s guards set out toward where the trail points (a player may follow them). */
	private static void search(ServerLevel level, Faction f, Taken t, long now) {
		Long last = SEARCHED.get(f.id());
		if (!Royals.daytime(level) || last != null && now - last < SEARCH_GAP
			|| level.getNearestPlayer(f.seat().getX() + 0.5, f.seat().getY(), f.seat().getZ() + 0.5, Economy.POPULATION_RANGE + 32.0, p -> !p.isSpectator()) == null) {
			return;
		}
		SEARCHED.put(f.id(), now);
		Vec3 seat = Vec3.atCenterOf(f.seat());
		Vec3 toward = new Vec3(t.x() + 0.5 - seat.x, 0.0, t.z() + 0.5 - seat.z).normalize();
		BlockPos out = BlockPos.containing(seat.add(toward.scale(SEARCH_OUT)));
		if (!level.isLoaded(out)) {
			return;
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, out);
		int sent = 0;
		for (PathfinderMob guard : level.getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			g -> g.isAlive() && McaCompat.isGuard(g) && g.getTarget() == null)) {
			if (sent == 0) {
				Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.stolen_crown.search", Directions.heading(seat, Vec3.atCenterOf(out))),
					Speech.YELLOW, 24.0);
			}
			net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(guard, ground, 0.6F, 4);
			if (++sent >= SEARCHERS) {
				break;
			}
		}
	}

	/**
	 * ({@code VillagerCoinMixin}, a trade opening) Whether {@code villager}, one of a realm on alert, won't trade with
	 * {@code player}, a stranger to it: they say so, and the trade is closed again as it opens.
	 */
	public static boolean refusesTrade(Villager villager, ServerPlayer player) {
		if (!(villager.level() instanceof ServerLevel level) || taken(level.getServer()).isEmpty()) {
			return false;
		}
		MinecraftServer server = level.getServer();
		Faction f = Captives.nearestSettlement(server, villager.blockPosition());
		if (f == null || f.seat().distSqr(villager.blockPosition()) > Economy.POPULATION_RANGE * Economy.POPULATION_RANGE || !alert(server, f)
			|| ofTheRealm(server, player, f) || Captives.bound(villager)) {
			return false;
		}
		Speech.say(level, villager, Component.translatable("talk.jaymoskingdoms.stolen_crown.no_trade"), Speech.YELLOW, 10.0);
		server.execute(player::closeContainer);
		return true;
	}

// ---- testing -----------------------------------------------------------------------------------------------------

	/** {@code /kingdom stolencrown} (ops): the ruler of the realm whose seat is nearest is taken now, and where the vault will be is said. */
	static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command() {
		return net.minecraft.commands.Commands.literal("stolencrown")
			.requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
			.executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ServerLevel level = Factions.level(player.level().getServer());
				Faction nearest = null;
				for (Faction f : Factions.all(level.getServer())) {
					if (!f.fallen() && (f.type() == FactionType.KINGDOM || f.type() == FactionType.FIEFDOM)
						&& (nearest == null || f.seat().distSqr(player.blockPosition()) < nearest.seat().distSqr(player.blockPosition()))) {
						nearest = f;
					}
				}
				if (nearest == null || taken(level.getServer(), nearest) || !(Rulers.of(level, nearest) instanceof Villager)) {
					c.getSource().sendFailure(Component.literal("No realm near with its villager ruler about (or it's already taken)"));
					return 0;
				}
				take(level, nearest);
				Taken t = taken(level.getServer()).get(Integer.toString(nearest.id()));
				if (t == null) {
					c.getSource().sendFailure(Component.literal("Couldn't take " + nearest.name()));
					return 0;
				}
				String name = nearest.name();
				c.getSource().sendSuccess(() -> Component.literal(name + "'s ruler is taken; the vault will be at " + t.x() + ", " + t.z()), false);
				return 1;
			});
	}
}
