package com.jaymoskingdoms;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.316) A settlement clears a structure before its people go near it. Any mod's surface structure (not a village)
 * within {@link #WATCH} of a standing settlement's seat is looked over every {@link #EVERY} ticks; while monsters are
 * in it (a dojo's skeletons, a steam house's pillagers, a ruin's spiders):
 * <ul>
 * <li>a party of the settlement's fighters (guards and armed folk, never its ruler or the archers on their posts), up
 * to {@link #PARTY_PER_FOE} for each monster and {@link #PARTY_MAX} in all, goes in after them;</li>
 * <li>everyone else (women, children, builders, farmers...) keeps {@link #KEEP_OUT} blocks clear of it: one who
 * wanders in or near is walked back out toward the seat, so nobody who can't fight is caught in it.</li>
 * </ul>
 * Once it's empty and the party has been inside, its spawners are smashed so it stays clear. A Grim ruin a kingdom
 * takes ({@link GrimSites}) is cleared the same way: its fighters arrive first, its builders wait outside.
 */
final class Clearing {
	/** How often structures near settlements are looked over (ticks); how far round a seat (blocks). */
	static final int EVERY = 60, WATCH = 112;
	/** Fighters sent for each monster inside, and in all; how far they're fetched from. */
	static final int PARTY_PER_FOE = 2, PARTY_MAX = 8, PARTY_REACH = 96;
	/** How far round a structure's pieces a monster counts as in it (blocks). */
	static final int INSIDE = 4;
	/** How far round a structure being cleared everyone but its party is kept (blocks); how far out they're walked. */
	static final int KEEP_OUT = 12, WALK_OUT = 10;
	/** Most blocks read when looking for spawners in one structure. */
	static final int MAX_SPAWNER_SCAN = 400_000;
	static final float SPEED = 0.7F;

	/** A structure being cleared (or clear) near a settlement: its pieces, as last read. */
	private record Site(List<BoundingBox> pieces, BoundingBox area) {
	}

	/** Structures whose spawners have been smashed (start chunk, packed, and the structure's id). */
	private static final Set<String> SMASHED = new HashSet<>();
	/** Monsters found in each structure at the last look (by its key). */
	private static final Map<String, Integer> FOES = new HashMap<>();

	private Clearing() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Clearing::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SMASHED.clear();
			FOES.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		Set<String> seen = new HashSet<>();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Map.Entry<String, Site> e : sites(level, f.seat()).entrySet()) {
				if (seen.add(e.getKey())) {
					look(level, f, e.getKey(), e.getValue());
				}
			}
		}
		FOES.keySet().retainAll(seen);
	}

	/** The non-village surface structures with pieces within {@link #WATCH} of {@code seat}, in loaded chunks (by key). */
	private static Map<String, Site> sites(ServerLevel level, BlockPos seat) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Map<String, Site> out = new HashMap<>();
		int reach = (WATCH >> 4) + 1;
		ChunkPos middle = ChunkPos.containing(seat);
		BoundingBox watched = new BoundingBox(seat.getX() - WATCH, level.getMinY(), seat.getZ() - WATCH, seat.getX() + WATCH, level.getMaxY(),
			seat.getZ() + WATCH);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int cx = middle.x() + dx, cz = middle.z() + dz;
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				ChunkAccess chunk = level.getChunk(cx, cz);
				for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
					Structure structure = e.getKey();
					Holder<Structure> holder = registry.wrapAsHolder(structure);
					if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES || Seats.village(holder) || SharedPools.village(holder)) {
						continue;
					}
					for (long ref : e.getValue()) {
						String key = ref + "|" + registry.getKey(structure);
						if (out.containsKey(key)) {
							continue;
						}
						ChunkPos from = ChunkPos.unpack(ref);
						if (!level.hasChunk(from.x(), from.z())) {
							continue;
						}
						StructureStart start = level.getChunk(from.x(), from.z()).getStartForStructure(structure);
						if (start == null || !start.isValid() || !start.getBoundingBox().intersects(watched)) {
							continue;
						}
						List<BoundingBox> pieces = new ArrayList<>();
						for (StructurePiece piece : start.getPieces()) {
							if (!Seats.name(piece).contains("/terrain")) {
								pieces.add(piece.getBoundingBox());
							}
						}
						if (!pieces.isEmpty()) {
							out.put(key, new Site(pieces, start.getBoundingBox()));
						}
					}
				}
			}
		}
		return out;
	}

	/** One structure near {@code f}: its monsters hunted down and its people kept out, or (clear) its spawners smashed. */
	private static void look(ServerLevel level, Faction f, String key, Site site) {
		// (another settlement's: an illager camp's outpost, a fiefdom's hold... its own folk see to it)
		Faction holder = Factions.at(level.getServer(), level, site.area().getCenter());
		if (holder != null && holder.id() != f.id() && !holder.fallen()) {
			return;
		}
		List<Mob> foes = new ArrayList<>();
		for (BoundingBox piece : site.pieces()) {
			AABB box = AABB.of(piece).inflate(INSIDE);
			for (Mob m : level.getEntitiesOfClass(Mob.class, box, Clearing::foe)) {
				if (!foes.contains(m)) {
					foes.add(m);
				}
			}
		}
		AABB around = AABB.of(site.area()).inflate(PARTY_REACH);
		List<Worker> fighters = Workers.near(level, around, w -> fighter(w, f));
		if (foes.isEmpty()) {
			Integer had = FOES.put(key, 0);
			if (!SMASHED.contains(key) && (had != null && had > 0 || partyInside(fighters, site))) {
				smashSpawners(level, key, site);
			}
			return;
		}
		FOES.put(key, foes.size());
		send(level, foes, fighters);
		// (a monster got into the settlement's own seat: its guards see to it and Frights sends the rest running,
		// nobody is put out of their own home)
		if (!near(site, Vec3.atCenterOf(f.seat()), INSIDE)) {
			keepOut(level, site, f, fighters);
		}
	}

	/** A monster worth clearing out: hostile, nobody's thrall, captive, pet or folk, and no illager camp's own. */
	private static boolean foe(Mob m) {
		return m.isAlive() && m instanceof Enemy && !(m instanceof Worker) && !(m instanceof TamableAnimal t && t.isTame())
			&& !Thralls.isThrall(m) && !m.hasAttached(Captives.CAPTIVE) && !Downed.isDowned(m) && !Illagers.isCampIllager(m);
	}

	/** One of {@code f}'s fighters, fit to go in: a guard or armed folk, not its ruler, not an archer on his post, standing. */
	private static boolean fighter(Worker w, Faction f) {
		PathfinderMob mob = w.mob();
		if (!mob.isAlive() || mob.isBaby() || w.factionId() != f.id() || Downed.isDowned(mob) || w.role() == Folk.ARCHER) {
			return false;
		}
		return w instanceof Folk folk ? folk.combatant() && !folk.leader() : w.role() == Folk.GUARD;
	}

	/** Sends the party: the nearest free fighters after each monster, the nearest first. */
	private static void send(ServerLevel level, List<Mob> foes, List<Worker> fighters) {
		List<PathfinderMob> free = new ArrayList<>();
		int busy = 0;
		for (Worker w : fighters) {
			LivingEntity target = Rulers.target(w.mob());
			if (target != null && target.isAlive()) {
				busy++;
			} else {
				free.add(w.mob());
			}
		}
		int room = Math.min(PARTY_MAX, foes.size() * PARTY_PER_FOE) - busy;
		for (Mob foe : foes) {
			for (int i = 0; i < PARTY_PER_FOE && room > 0 && !free.isEmpty(); i++, room--) {
				PathfinderMob guard = free.stream().min(Comparator.comparingDouble(g -> g.distanceToSqr(foe))).orElseThrow();
				free.remove(guard);
				Rulers.charge(guard, foe);
			}
		}
	}

	/** Everyone near it who isn't in the party (any settlement's) is walked out of it, back toward their seat. */
	private static void keepOut(ServerLevel level, Site site, Faction f, List<Worker> fighters) {
		Set<UUID> party = new HashSet<>();
		for (Worker w : fighters) {
			party.add(w.mob().getUUID());
		}
		AABB zone = AABB.of(site.area()).inflate(KEEP_OUT);
		for (PathfinderMob p : level.getEntitiesOfClass(PathfinderMob.class, zone,
			p -> p.isAlive() && !party.contains(p.getUUID()) && (p instanceof Villager || p instanceof Worker) && !Downed.isDowned(p))) {
			Faction own = p instanceof Worker w && w.factionId() != f.id() ? Factions.get(level.getServer(), w.factionId()) : null;
			if (own != null && fighter((Worker)p, own)) {
				// (another settlement's fighter: his own settlement sends him if it wants)
				continue;
			}
			if (!near(site, p.position(), KEEP_OUT)) {
				continue;
			}
			BlockPos to = out(level, site, p, f.seat());
			if (p instanceof Villager v) {
				Frights.shelter(level, v, to, level.getGameTime() + EVERY * 2L);
				BehaviorUtils.setWalkAndLookTargetMemories(v, to, SPEED, 1);
			} else {
				p.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, SPEED + 0.3);
			}
		}
	}

	/** {@code at} is within {@code margin} of one of its pieces. */
	private static boolean near(Site site, Vec3 at, int margin) {
		for (BoundingBox piece : site.pieces()) {
			if (AABB.of(piece).inflate(margin).contains(at)) {
				return true;
			}
		}
		return false;
	}

	/** A spot outside the structure for {@code p} to go to: out past its edge the way of the seat (else away from its middle). */
	private static BlockPos out(ServerLevel level, Site site, PathfinderMob p, BlockPos seat) {
		BoundingBox area = site.area();
		Vec3 middle = Vec3.atCenterOf(area.getCenter());
		Vec3 way = Vec3.atCenterOf(seat).subtract(middle).multiply(1.0, 0.0, 1.0);
		if (way.lengthSqr() < 4.0) {
			way = p.position().subtract(middle).multiply(1.0, 0.0, 1.0);
		}
		if (way.lengthSqr() < 1.0E-3) {
			way = new Vec3(1.0, 0.0, 0.0);
		}
		double half = Math.max(area.getXSpan(), area.getZSpan()) / 2.0;
		Vec3 to = middle.add(way.normalize().scale(half + KEEP_OUT + WALK_OUT));
		int x = (int)Math.floor(to.x), z = (int)Math.floor(to.z);
		return Construction.standable(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z), 4);
	}

	/** Some of the party stand in it. */
	private static boolean partyInside(List<Worker> fighters, Site site) {
		for (Worker w : fighters) {
			if (near(site, w.mob().position(), 0)) {
				return true;
			}
		}
		return false;
	}

	/** Its monster spawners are broken (once), so what the party cleared stays clear. */
	private static void smashSpawners(ServerLevel level, String key, Site site) {
		int read = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		List<BlockPos> spawners = new ArrayList<>();
		for (BoundingBox piece : site.pieces()) {
			if (!level.hasChunksAt(piece.minX(), piece.minZ(), piece.maxX(), piece.maxZ())) {
				return;
			}
			for (int x = piece.minX(); x <= piece.maxX(); x++) {
				for (int z = piece.minZ(); z <= piece.maxZ(); z++) {
					for (int y = piece.minY(); y <= piece.maxY() && read < MAX_SPAWNER_SCAN; y++, read++) {
						if (level.getBlockState(at.set(x, y, z)).is(Blocks.SPAWNER)) {
							spawners.add(at.immutable());
						}
					}
				}
			}
		}
		for (BlockPos s : spawners) {
			level.destroyBlock(s, false);
		}
		SMASHED.add(key);
		if (!spawners.isEmpty()) {
			JaymosKingdoms.LOGGER.info("A clearing party smashed {} spawner(s) in the structure at {}", spawners.size(), site.area().getCenter());
		}
	}
}
