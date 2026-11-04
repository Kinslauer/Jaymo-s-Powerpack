package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes, "Living together" (Andy, 2026-11-03): now and then a lone orc or goblin (a trader, a tinker, a bruiser)
 * settles in a village, as an MCA villager like any other. How readily follows {@link Peoples}' table: the village's own
 * people's distaste for the newcomer's decides the odds ({@link #WELCOME}), so the other heritages and the havens take them
 * more easily than humans do; a human village only now and then ({@link #HUMAN_SHARE}), more where the ruler has decreed
 * tolerance, never a purist's. They turn up on open ground by the village's edge, out of sight of anyone.
 */
public final class Settlers {
	// ================================================================================================================
	// Tuning
	/** How often each player's surroundings are looked at for a settler (ticks), and the chance each time. */
	static final int CHECK_EVERY = 20 * 60 * 4;
	static final float CHANCE = 0.3F;
	/** Villages this far from the player are chosen from (blocks); a settler is never made nearer than {@link #UNSEEN} to anyone. */
	static final double NEAR = 40.0, FAR = 110.0, UNSEEN = 32.0;
	/** The odds of a village taking the newcomer by the distaste its people hold them in (0 to 3). */
	static final float[] WELCOME = {1.0F, 0.6F, 0.25F, 0.08F};
	/** A human village's share of those odds, plus the same again per edict of tolerance its ruler has made. */
	static final float HUMAN_SHARE = 0.35F;
	/** (0.7.490) A human village with no tolerance edict takes none while a greenskin camp at war with it stands this near (or a raid is on it). */
	static final double HOSTILE_CAMP_RANGE = 192.0;
	/** A village holds at most this many of them (within {@link #VILLAGE_RANGE}), counted by blood. */
	static final int CAP = 3;
	static final double VILLAGE_RANGE = 60.0;
	/** The odds a settler is an orc rather than a goblin (always an orc where a haven sits under an orc king). */
	static final float ORC_SHARE = 0.45F;
	/** The odds of each trade: trader, tinker, bruiser (an orc favours the last). */
	static final int[] ORC_TRADES = {1, 2, 6}, GOBLIN_TRADES = {4, 5, 1};
	// ================================================================================================================

	private record Spot(BlockPos center, @Nullable Faction faction) {
	}

	private Settlers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Settlers::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (!McaCompat.LOADED || level.getGameTime() % CHECK_EVERY != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && random.nextFloat() < CHANCE) {
				settle(level, p);
			}
		}
	}

	/** A village within sight of the world's loaded edge about {@code p} takes in a lone orc or goblin, maybe. */
	private static void settle(ServerLevel level, ServerPlayer p) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		List<Spot> spots = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && (f.type().isRealm() || Havens.is(server, f)) && within(p, f.seat())) {
				spots.add(new Spot(f.seat(), f));
			}
		}
		for (McaCompat.Town t : McaCompat.towns(level)) {
			if (within(p, t.center()) && Factions.around(server, level, t.center(), Emigrants.PLACE_RANGE) == null) {
				spots.add(new Spot(t.center(), null));
			}
		}
		if (spots.isEmpty()) {
			return;
		}
		Spot spot = spots.get(random.nextInt(spots.size()));
		Heritage.Kind kind = kindFor(server, spot.faction(), random);
		float odds = odds(server, spot.faction(), spot.center(), kind, random);
		if (odds <= 0.0F || random.nextFloat() >= odds || !level.isLoaded(spot.center()) || crowded(level, spot.center())) {
			return;
		}
		// ((0.7.490) never load or make chunks to look for ground: the whole search square must be in already)
		int seek = ShutIn.SEEK;
		BlockPos c = spot.center();
		if (!level.hasChunksAt(c.getX() - seek, c.getZ() - seek, c.getX() + seek, c.getZ() + seek)) {
			return;
		}
		BlockPos at = ShutIn.openGround(level, spot.center());
		if (at == null || !level.isLoaded(at) || !level.isPositionEntityTicking(at)) {
			return;
		}
		for (ServerPlayer q : level.players()) {
			if (q.distanceToSqr(at.getX(), at.getY(), at.getZ()) < UNSEEN * UNSEEN) {
				return;
			}
		}
		int[] weights = kind == Heritage.Kind.ORC ? ORC_TRADES : GOBLIN_TRADES;
		int pick = random.nextInt(weights[0] + weights[1] + weights[2]);
		int trade = pick < weights[0] ? McaBridge.TRADER : pick < weights[0] + weights[1] ? McaBridge.TINKER : McaBridge.BRUISER;
		PathfinderMob settler = McaCompat.settler(level, kind, Greenskins.name(kind, random.nextLong()), Vec3.atBottomCenterOf(at), trade);
		if (settler != null) {
			settler.setPersistenceRequired();
		}
	}

	private static boolean within(ServerPlayer p, BlockPos pos) {
		double d = Math.sqrt(p.distanceToSqr(pos.getX(), p.getY(), pos.getZ()));
		return d >= NEAR && d <= FAR;
	}

	/** The brood of the newcomer: an orc or a goblin ({@code f}, if a haven under an orc king, only takes orcs). */
	static Heritage.Kind kindFor(MinecraftServer server, @Nullable Faction f, RandomSource random) {
		if (f != null && Havens.orcRuled(server, f)) {
			return Heritage.Kind.ORC;
		}
		return random.nextFloat() < ORC_SHARE ? Heritage.Kind.ORC : Heritage.Kind.GOBLIN;
	}

	/**
	 * The odds {@code f}'s village (null: an MCA village under no banner, a human one) takes in a settler of blood {@code kind}:
	 * by how much its own people mind that blood ({@link Peoples#distaste}), a human village's only a share of that (and (0.7.490) only when
	 * relations allow: an edict of tolerance, or {@link #atPeace}), and
	 * none at all a purist's.
	 */
	static float odds(MinecraftServer server, @Nullable Faction f, BlockPos center, Heritage.Kind kind, RandomSource random) {
		if (f != null && Purists.is(f)) {
			return 0.0F;
		}
		if (f != null && Havens.is(server, f)) {
			if (Havens.greenskin(server, f)) {
				return WELCOME[0];
			}
			String haven = Havens.of(server, f);
			Heritage.Kind blood = Havens.MIXED.equals(haven) ? Heritage.Kind.SYLVAN : Heritage.Kind.byKey(haven);
			return WELCOME[Math.min(3, Peoples.distaste(blood, kind))];
		}
		float edict = f == null ? 0.0F : Reforms.tolerance(server, Emigrants.ruler(server, f));
		if (edict <= 0.0F && !atPeace(server, f, center)) {
			// ((0.7.490) a human village takes them only when relations allow: an edict of tolerance, or no tribe at war with it near)
			return 0.0F;
		}
		return WELCOME[Math.min(3, Peoples.distaste((Heritage.Kind)null, kind))] * Math.min(1.0F, HUMAN_SHARE * (1.0F + edict));
	}

	/**
	 * (0.7.490) Village {@code f} about {@code center} (null: an MCA village under no banner) is at peace with the tribes: no raid
	 * on it now, and no standing goblin or orc camp at war with it within {@link #HOSTILE_CAMP_RANGE}.
	 */
	private static boolean atPeace(MinecraftServer server, @Nullable Faction f, BlockPos center) {
		if (f != null && Raids.raided(f.id())) {
			return false;
		}
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			if (camp.fallen() || Tribes.of(server, camp.id()) == null || f != null && !Relations.atWar(camp, f)) {
				continue;
			}
			double dx = camp.seat().getX() - center.getX(), dz = camp.seat().getZ() - center.getZ();
			if (dx * dx + dz * dz <= HOSTILE_CAMP_RANGE * HOSTILE_CAMP_RANGE) {
				return false;
			}
		}
		return true;
	}

	/** The village about {@code center} already holds all it will of them. */
	private static boolean crowded(ServerLevel level, BlockPos center) {
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center).inflate(VILLAGE_RANGE),
			x -> x.isAlive() && McaCompat.isMca(x) && Greenskins.of(x) != null)) {
			n++;
		}
		return n >= CAP;
	}
}
