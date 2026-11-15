package com.jaymoskingdoms;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * The sky lord's court: who holds the sky castle Jaymo's Powers builds in the Aether ({@code jaymospowers:sky_castle}).
 * Every so often the castles and sky villages in the chunks round each player in the Aether are looked over
 * ({@link #look}); the villages go to {@link SkyFerries}.
 * <ul>
 * <li><b>The lord.</b> One grown villager of the castle is its lord ({@link #LORD_TAG}): the one Powers sat on the
 * throne if they can still be told, else whoever stands nearest the keep. He keeps within {@link #LORD_ROAM} of where he
 * was found, wears the mantle ({@link SkyFolk#outfits}) and talks as a lord does. When he dies the next look names
 * another from the castle's people; with none left the seat stands empty.</li>
 * <li><b>His wardens.</b> Up to {@link #WARDENS} more take up a guard's trade ({@link #WARDEN_TAG}): MCA arms them and
 * has them fight what comes over the wall, and anyone who strikes the castle's people. They are the well-wardens, in
 * grey plate where Fantasy Armor is loaded ({@link FantasyArmorCompat#WELL_WARDEN}).</li>
 * <li><b>The villages' elders.</b> Where Fantasy Armor is loaded, each sky village and town has an elder ({@link #ELDER_TAG}):
 * the grown villager nearest its middle who holds no other post, in the wind-faith robes ({@link FantasyArmorCompat#SKY_ROBES}),
 * which they keep on. When the elder dies the next look names another.</li>
 * </ul>
 * The court is nobody's kingdom: it holds no land, pays and takes no tribute and goes to no war. The realms below settle
 * their affairs on foot, and there is no road up. But the seat can be taken by force: a power-hungry traveller now and then
 * comes for it ({@link SkyUsurpers}).
 */
public final class SkyCourt {
	/** On the castle's lord, and on each of his wardens. */
	public static final String LORD_TAG = "jaymoskingdoms.sky_lord", WARDEN_TAG = "jaymoskingdoms.sky_warden";
	/** On a sky village's elder. */
	public static final String ELDER_TAG = "jaymoskingdoms.sky_elder";
	/** What Powers puts on the villager it seats on the throne (it may not outlast MCA making him its own). */
	static final String BUILT_LORD_TAG = "jaymospowers_sky_lord";
	/** How often the chunks round players in the Aether are looked over (ticks), and how far round (chunks). */
	static final int LOOK_EVERY = 600, LOOK_CHUNKS = 5;
	/** How many wardens a castle keeps. */
	static final int WARDENS = 3;
	/** How far the lord strays from where he was named (blocks). */
	static final int LORD_ROAM = 6;
	/** The castle's people are looked for this far past its walls (blocks). */
	static final double COURT_REACH = 4.0;
	/** A village's elder still counts as its own this far past its walls (out at the farms or the mast). */
	static final double ELDER_REACH = 48.0;

	private static final String BUILDER = "jaymospowers", CASTLE = "sky_castle", VILLAGE = "sky_village_", TOWN = "sky_town_", RUIN = "ruin";

	private SkyCourt() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % LOOK_EVERY == 0 && McaCompat.LOADED) {
				look(level);
			}
		});
	}

	/** Looks over the sky castles and sky villages in the loaded chunks round every player in the Aether. */
	private static void look(ServerLevel level) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		LongSet done = new LongOpenHashSet();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !SkyFolk.in(player)) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -LOOK_CHUNKS; dx <= LOOK_CHUNKS; dx++) {
				for (int dz = -LOOK_CHUNKS; dz <= LOOK_CHUNKS; dz++) {
					ChunkAccess chunk = level.getChunkSource().getChunkNow(middle.x() + dx, middle.z() + dz);
					if (chunk == null) {
						continue;
					}
					for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
						Identifier id = registry.getKey(e.getKey());
						if (id == null || !id.getNamespace().equals(BUILDER)) {
							continue;
						}
						boolean castle = id.getPath().equals(CASTLE);
						if (!castle && !(id.getPath().startsWith(VILLAGE) && !id.getPath().contains(RUIN)) && !id.getPath().startsWith(TOWN)) {
							continue;
						}
						for (long ref : e.getValue()) {
							if (!done.add(ref)) {
								continue;
							}
							ChunkPos from = ChunkPos.unpack(ref);
							ChunkAccess startChunk = level.getChunkSource().getChunkNow(from.x(), from.z());
							StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(e.getKey());
							if (start == null || !start.isValid()) {
								continue;
							}
							BoundingBox box = start.getBoundingBox();
							if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
								continue;
							}
							if (castle) {
								hold(level, box);
							} else {
								SkyFerries.moored(level, box, ref);
								elder(level, box);
								if (id.getPath().startsWith(VILLAGE)) {
									// a sky village is a haven of the Divine-born (SkyHavens)
									SkyHavens.found(level, box);
								}
							}
						}
					}
				}
			}
		}
	}

	/** The grown MCA villagers in and about {@code box}. */
	static List<Villager> people(ServerLevel level, BoundingBox box) {
		return level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(COURT_REACH), v -> v.isAlive() && McaBridge.adult(v));
	}

	/** A sky village's elder named, if Fantasy Armor is loaded and it has none. */
	private static void elder(ServerLevel level, BoundingBox box) {
		if (!FantasyArmorCompat.LOADED) {
			return;
		}
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(box.getCenter());
		if (!level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(ELDER_REACH),
				v -> v.isAlive() && v.entityTags().contains(ELDER_TAG)).isEmpty()) {
			return;
		}
		Villager elder = null;
		for (Villager v : people(level, box)) {
			// (one who holds no other post: no trade, no guard's, nobody's family; SkyTrades#hirable)
			if (SkyUsurpers.party(v) || !SkyTrades.hirable(v) || McaBridge.isGuard(v)) {
				continue;
			}
			if (elder == null || v.distanceToSqr(at) < elder.distanceToSqr(at)) {
				elder = v;
			}
		}
		if (elder != null) {
			elder.addTag(ELDER_TAG);
			elder.setPersistenceRequired();
			FantasyArmorCompat.dress(elder, FantasyArmorCompat.SKY_ROBES);
		}
	}

	/** One castle: a lord named if it has none, and its wardens made up to their number. */
	private static void hold(ServerLevel level, BoundingBox box) {
		List<Villager> people = people(level, box);
		BlockPos keep = box.getCenter();
		Villager lord = null;
		int wardens = 0;
		List<Villager> free = new ArrayList<>();
		for (Villager v : people) {
			if (SkyUsurpers.party(v)) {
				continue;
			}
			if (v.entityTags().contains(LORD_TAG)) {
				lord = v;
			} else if (v.entityTags().contains(WARDEN_TAG)) {
				wardens++;
			} else if (!v.entityTags().contains(ELDER_TAG)) {
				free.add(v);
			}
		}
		if (SkyUsurpers.underway(box)) {
			// a usurper is at the gate: the seat isn't filled again until that is settled
			SkyUsurpers.watch(level, box, lord, people);
			return;
		}
		if (lord == null && !free.isEmpty()) {
			// the one sat on the throne when it was built, else whoever keeps nearest the keep
			free.sort(Comparator.comparingDouble(v -> (v.entityTags().contains(BUILT_LORD_TAG) ? -1.0e9 : 0.0) + v.distanceToSqr(keep.getX() + 0.5, v.getY(), keep.getZ() + 0.5)));
			lord = free.remove(0);
			lord.addTag(LORD_TAG);
			lord.setPersistenceRequired();
			lord.setHomeTo(lord.blockPosition(), LORD_ROAM);
			McaBridge.dress(lord);
			JaymosKingdoms.LOGGER.info("The sky castle at {} has a lord: {}", keep, lord.getName().getString());
		}
		// the wardens come from those who keep nearest the wall (leaving the hall's people to the hall)
		free.sort(Comparator.comparingDouble(v -> -v.distanceToSqr(keep.getX() + 0.5, v.getY(), keep.getZ() + 0.5)));
		while (wardens < WARDENS && free.size() > 1) {
			Villager warden = free.remove(0);
			warden.addTag(WARDEN_TAG);
			McaBridge.toGuard(warden);
			// (the well-wardens' grey plate, where Fantasy Armor is loaded; MCA puts it back on them from then on)
			FantasyArmorCompat.dress(warden, FantasyArmorCompat.WELL_WARDEN);
			wardens++;
		}
		// now and then a power-hungry traveller comes for the seat (SkyUsurpers)
		SkyUsurpers.watch(level, box, lord, people);
	}
}
