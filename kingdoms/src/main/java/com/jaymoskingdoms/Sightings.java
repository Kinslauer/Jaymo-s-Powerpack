package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.56) Word of the monsters of legend ({@link Monsters}): where one was last seen. A sighting comes of an encounter
 * with one ({@link Encounters}), or of the talk that goes round (now and then one is "seen" somewhere out in the wild a
 * few hundred blocks from a settlement, where its kind roams). Villagers gossip about the nearest
 * ({@link Approaches}), and now and then one sends you after it ({@link QuestBoard}): to slay it ({@link Quest#HUNT}), or
 * to last a night out where it was seen ({@link Quest#SURVIVE}). Kept while the server runs.
 */
public final class Sightings {
	/** A beast's kind and where and when it was seen (game time). */
	record Sighting(String mob, BlockPos pos, long time) {
		String name() {
			return this.mob.substring(this.mob.indexOf(':') + 1);
		}
	}

	/** How long a sighting is talked about (ticks; three days), and how many are kept. */
	static final long SIGHTING_TICKS = 72000;
	static final int MAX_SIGHTINGS = 24;
	/** How often the talk turns up a new one (ticks), quicker while there are fewer than {@link #FEW}. */
	static final int RUMOUR_TICKS = 6000, FEW_RUMOUR_TICKS = 1200, FEW = 6;
	/** How far from a settlement's seat the talk puts them (blocks). */
	static final int RUMOUR_MIN = 300, RUMOUR_MAX = 800;
	/** A villager gossips about the nearest within this (blocks), this often when gossiping. */
	static final double GOSSIP_RANGE = 1200.0;
	static final float GOSSIP_CHANCE = 0.4F;
	/** How many lines there are for each beast. */
	static final int GOSSIP_LINES = 3;
	/** How often a villager with work for you sends you after a beast instead, and how far they'd send you (blocks). */
	static final float QUEST_CHANCE = 0.25F;
	static final double QUEST_RANGE = 1200.0;
	/** Of those, how many are to last a night out there rather than slay it. */
	static final float SURVIVE_SHARE = 0.35F;
	/** A slay quest's beast comes for you this near where it was seen (blocks); so does a survive quest's pack, at night. */
	static final double HUNT_RANGE = 64.0, SURVIVE_RANGE = 48.0;
	/** How long a slay quest's beast keeps after you (ticks). */
	static final int QUEST_HUNT_TICKS = 6000;

	private static final List<Sighting> SEEN = new ArrayList<>();

	private Sightings() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Sightings::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEEN.clear());
	}

	/** A monster of legend was seen at {@code pos}. */
	static void seen(ServerLevel level, EntityType<?> type, BlockPos pos) {
		if (!Monsters.legend(type)) {
			return;
		}
		SEEN.add(new Sighting(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), pos.immutable(), level.getGameTime()));
		while (SEEN.size() > MAX_SIGHTINGS) {
			SEEN.removeFirst();
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % FEW_RUMOUR_TICKS != 0) {
			return;
		}
		SEEN.removeIf(s -> now - s.time() > SIGHTING_TICKS);
		if (SEEN.size() < FEW || now % RUMOUR_TICKS == 0) {
			rumour(server, level, level.getRandom());
		}
	}

	/** The talk puts one of them somewhere out from a settlement, where its kind roams. */
	private static void rumour(MinecraftServer server, ServerLevel level, RandomSource random) {
		List<Faction> all = new ArrayList<>(Factions.all(server));
		all.removeIf(f -> f.fallen() || f.type() == FactionType.BARBARIAN);
		if (all.isEmpty()) {
			return;
		}
		Faction f = all.get(random.nextInt(all.size()));
		double angle = random.nextDouble() * Math.PI * 2.0;
		int distance = Mth.nextInt(random, RUMOUR_MIN, RUMOUR_MAX);
		BlockPos at = BlockPos.containing(f.seat().getX() + Math.cos(angle) * distance, level.getSeaLevel(), f.seat().getZ() + Math.sin(angle) * distance);
		if (Factions.around(server, level, at, Encounters.SETTLEMENT_CLEARANCE) != null) {
			return;
		}
		Encounters.Encounter e = Encounters.pick(level.getBiome(at), true, random, true);
		EntityType<?> type = e == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(e.mob())).orElse(null);
		if (type != null) {
			seen(level, type, at);
		}
	}

	/** The latest sighting within {@code range} of {@code pos}, if any. */
	static @Nullable Sighting near(BlockPos pos, double range) {
		Sighting best = null;
		for (Sighting s : SEEN) {
			double dx = s.pos().getX() - pos.getX(), dz = s.pos().getZ() - pos.getZ();
			if (dx * dx + dz * dz <= range * range && (best == null || s.time() > best.time())) {
				best = s;
			}
		}
		return best;
	}

	/** Which way {@code to} lies from {@code from} (the quest tracker's words: north, north-east...). */
	static Component direction(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
		String[] eight = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		return Component.translatable("quest.jaymoskingdoms.tracker." + eight[Math.floorMod(Math.round(angle / 45.0), 8)]);
	}

	/** A line of gossip about the nearest sighting to {@code villager}, or null if none is near. */
	static @Nullable Component gossip(ServerLevel level, LivingEntity villager) {
		Sighting s = near(villager.blockPosition(), GOSSIP_RANGE);
		if (s == null) {
			return null;
		}
		int blocks = Math.max(50, Math.round((float) Math.sqrt(s.pos().distSqr(villager.blockPosition())) / 50.0F) * 50);
		return Component.translatable("talk.jaymoskingdoms.gossip.beast." + s.name() + "." + level.getRandom().nextInt(GOSSIP_LINES), blocks,
			direction(villager.blockPosition(), s.pos()));
	}

	/** A villager's quest after the nearest sighting: slay it, or last a night out there; null if none is near. */
	static @Nullable Quest quest(LivingEntity giver, Quest.Giver who, Quest.Reward reward, RandomSource random) {
		Sighting s = near(giver.blockPosition(), QUEST_RANGE);
		if (s == null) {
			return null;
		}
		boolean survive = random.nextFloat() < SURVIVE_SHARE;
		Quest.Objective o = new Quest.Objective(s.mob(), 1, 0, Optional.of(s.pos()), Optional.empty(), s.name(), "", false);
		return new Quest(UUID.randomUUID().toString(), survive ? Quest.SURVIVE : Quest.HUNT, (survive ? "beast.survive." : "beast.hunt.") + s.name(),
			who, o, reward, 0L);
	}
}
