package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * A band's own parting word (docs/Pawn-Plan.md row A6, the foe side; after the word and gift a Dragon's Dogma pawn
 * takes home, a commercial game: ideas only). Every hit a band member lands on a player or a player's companion counts
 * to its tally. When the fight's over, its leader has a word for them: the best of them is praised and comes out of it
 * blooded ({@link #BLOODED}: a little more health, up to {@link #MAX_BLOODED} times, so the ones who did well are
 * harder next time), and one who landed nothing is told off.
 */
public final class BandTallies {
	/** The least tally that earns praise; health per blooding; how many times. */
	static final float PRAISE_AT = 6.0F;
	static final double HEALTH_PER = 2.0;
	static final int MAX_BLOODED = 3;
	/** Mates this near (blocks) the leader are given a word. */
	static final double NEAR = 24.0;

	/** Times a band member was praised. Saved. */
	public static final AttachmentType<Integer> BLOODED = AttachmentRegistry.create(JaymosKingdoms.id("band_blooded"),
		builder -> builder.persistent(Codec.INT));

	/** On one told off: kept back at camp, minding the pens, until then (game time). Saved. */
	public static final AttachmentType<Long> PENS = AttachmentRegistry.create(JaymosKingdoms.id("band_pens"),
		builder -> builder.persistent(Codec.LONG));
	/** The praised ones a camp sends with a raid, at most; how near the camp's fire one minds the pens (blocks). */
	static final int SEND_BEST = 2;
	static final double PENS_NEAR = 6.0, MUSTER_CLEAR = 48.0;

	/** Damage each band member dealt in its current fight (0 for one that was in it and landed nothing). */
	private static final Map<UUID, Float> TALLY = new HashMap<>();

	/** Band members sent along with a raid (the praised, and sold swords): the raiding camp and the seat they go for. */
	private record Joined(int camp, BlockPos seat) {
	}

	private static final Map<Mob, Joined> JOINED = new java.util.IdentityHashMap<>();
	private static final Set<UUID> TOLD = new HashSet<>();

	private BandTallies() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> dealt(entity, source, taken));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TALLY.clear();
			JOINED.clear();
			TOLD.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(BandTallies::tick);
	}

	private static boolean band(LivingEntity e) {
		return FoeLore.keeps(Comrades.side(e));
	}

	private static void dealt(LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0.0F || !(source.getEntity() instanceof Mob m)) {
			if (source.getEntity() instanceof Player && victim instanceof Mob hurt && band(hurt)) {
				// in it, at least
				TALLY.putIfAbsent(hurt.getUUID(), 0.0F);
			}
			return;
		}
		if (band(m) && (victim instanceof Player || Bonds.lordOf(victim) != null)) {
			TALLY.merge(m.getUUID(), taken, Float::sum);
		}
	}

	/** (Comrades) {@code m}'s fight is over: if it leads a band, it has a word for the best and the worst of them. */
	static void ended(Mob m) {
		if (!(m.level() instanceof ServerLevel level) || !BandLeaders.isLeader(m) || !band(m)) {
			TALLY.remove(m.getUUID());
			return;
		}
		String side = Comrades.side(m);
		Mob best = null, worst = null;
		float bestTally = PRAISE_AT;
		for (Mob mate : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(NEAR), e -> e.isAlive() && !Downed.isDowned(e)
			&& TALLY.containsKey(e.getUUID()) && Comrades.side(e).equals(side))) {
			float t = TALLY.get(mate.getUUID());
			if (t >= bestTally) {
				bestTally = t;
				best = mate;
			}
			if (t <= 0.0F && mate != m && worst == null) {
				worst = mate;
			}
		}
		if (best != null) {
			if (best == m) {
				Callouts.say(m, "band.boast", null, true);
			} else {
				Callouts.say(m, "band.praise", best, true, best.getDisplayName());
			}
			blood(best);
		} else if (worst != null) {
			Callouts.say(m, "band.scold", worst, true, worst.getDisplayName());
			worst.setAttached(PENS, level.getGameTime() + 24000L);
		}
		for (Mob mate : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(NEAR), e -> Comrades.side(e).equals(side))) {
			TALLY.remove(mate.getUUID());
		}
		TALLY.remove(m.getUUID());
	}

	/** {@code m} comes out of it blooded: a little harder next time. */
	private static void blood(Mob m) {
		int times = Math.min(MAX_BLOODED, m.getAttachedOrElse(BLOODED, 0) + 1);
		m.setAttached(BLOODED, times);
		AttributeInstance health = m.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("band_blooded"), times * HEALTH_PER,
				AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** (Veterans) {@code m} went with a raid and it's still on. */
	static boolean raiding(LivingEntity m) {
		return m instanceof Mob mob && JOINED.containsKey(mob);
	}

	/**
	 * (Raids.start) a camp's raid sets out: its praised fighters (not those minding the pens) and the swords it bought go
	 * with it, coming over the land from the muster like the rest of the party (row A6, the foe side).
	 */
	static void raidStarted(ServerLevel level, Faction camp, Faction target, BlockPos muster) {
		Camps.CampData data = Camps.data(level.getServer(), camp.id());
		if (data == null) {
			return;
		}
		String side = "f:" + camp.id();
		long now = level.getGameTime();
		int sent = 0;
		List<Mob> band = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(data.center()).inflate(96.0), m -> m.isAlive()
			&& !Downed.isDowned(m) && Comrades.side(m).equals(side) && (m.getAttachedOrElse(BLOODED, 0) > 0 || m.hasAttached(Veterans.SOLD))
			&& m.getAttachedOrElse(PENS, 0L) < now && level.getNearestPlayer(m, MUSTER_CLEAR) == null));
		band.sort((a, b) -> Integer.compare(b.getAttachedOrElse(BLOODED, 0), a.getAttachedOrElse(BLOODED, 0)));
		for (Mob m : band) {
			boolean sword = m.hasAttached(Veterans.SOLD);
			if (!sword && sent >= SEND_BEST) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(m.getRandom().nextInt(7) - 3, 0,
				m.getRandom().nextInt(7) - 3));
			if (!level.isLoaded(at) || level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), 24.0, false) != null) {
				continue;
			}
			// (they come over the land with the party: the muster is out of anyone's sight, as the party's own arrival is)
			m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, m.getYRot(), 0.0F);
			JOINED.put(m, new Joined(camp.id(), target.seat()));
			if (!sword) {
				sent++;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (var it = JOINED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Joined> e = it.next();
			Mob m = e.getKey();
			Joined j = e.getValue();
			if (!m.isAlive() || m.isRemoved() || !Raids.raiding(j.camp())) {
				it.remove();
				TOLD.remove(m.getUUID());
				continue;
			}
			if (Downed.isDowned(m) || Comrades.target(m) != null) {
				continue;
			}
			if (m.blockPosition().distSqr(j.seat()) > 14.0 * 14.0 && m.getNavigation().isDone()) {
				m.getNavigation().moveTo(j.seat().getX() + 0.5, j.seat().getY(), j.seat().getZ() + 0.5, 1.0);
			}
			// a sold sword finds the one who turned it down
			List<String> spurned = m.getAttachedOrElse(Veterans.SPURNED, List.of());
			if (!spurned.isEmpty() && m.level() instanceof ServerLevel level && !TOLD.contains(m.getUUID())) {
				for (ServerPlayer p : level.getPlayers(p -> p.distanceTo(m) < 24.0 && spurned.contains(p.getUUID().toString()))) {
					TOLD.add(m.getUUID());
					Callouts.say(m, "sell.raid", p, true, p.getDisplayName());
					Comrades.aim(m, p);
					break;
				}
			}
		}
		// the ones told off mind the pens: they keep by the camp's fire for the day
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(96.0), m -> m.getAttachedOrElse(PENS, 0L) > now
				&& Comrades.target(m) == null && !Downed.isDowned(m))) {
				String side = Comrades.side(m);
				if (!side.startsWith("f:")) {
					continue;
				}
				try {
					Camps.CampData data = Camps.data(server, Integer.parseInt(side.substring(2)));
					if (data != null && m.blockPosition().distSqr(data.center()) > PENS_NEAR * PENS_NEAR && m.getNavigation().isDone()) {
						m.getNavigation().moveTo(data.center().getX() + 0.5, data.center().getY(), data.center().getZ() + 0.5, 0.6);
					}
				} catch (NumberFormatException e) {
					// not a camp's
				}
			}
		}
	}
}
