package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * The bond (docs/Pawn-Plan.md row G1; after the pawns who grow fond of the master who keeps them close in Dragon's
 * Dogma, a commercial game: ideas only). Each companion keeps a bond with its player, {@code 0} to {@link #MAX}:
 * <ul>
 * <li><b>Up</b>: whatever warms them in MCA (talk, gifts, a joke; read off their hearts, {@link #HEARTS_PER_POINT}),
 * sitting down together ({@link TableTalk}), and being got back on their feet with you beside them ({@link #SAVED}).</li>
 * <li><b>Down</b>: left lying downed while you're far off ({@link #LEFT_FAR}, {@link #LEFT_TICKS}): {@link #LEFT}.</li>
 * <li><b>Close</b> ({@link #CLOSE} or more): new lines (a greeting when you come back, "bond.close"), they keep nearer
 * you out of a fight ({@link #NEAR}), and they hold out against being turned ({@link #resists}).</li>
 * <li><b>A sworn captain</b> ({@link SwornCaptains}) keeps one too, by his id; the closer it is, the likelier he keeps
 * faith at a dawn he'd otherwise turn on you ({@link #keepsFaith}).</li>
 * </ul>
 */
public final class Bonds {
	/** The top of the bond; close from here. */
	static final int MAX = 100, CLOSE = 60;
	/** MCA hearts gained for each point; points a sit-down gives; points for being got up with you by. */
	static final int HEARTS_PER_POINT = 5, TABLE = 4, SAVED = 8;
	/** Lost for being left down this long (ticks) with you this far off (blocks). */
	static final int LEFT = 10, LEFT_TICKS = 600;
	static final double LEFT_FAR = 40.0, SAVED_NEAR = 6.0;
	/** A close companion out of a fight walks back to you past this far (blocks). */
	static final double NEAR = 6.0;
	/** Looked over this often (ticks). */
	static final int EVERY = 40;

	/** On a companion: player id → bond. Saved. */
	public static final AttachmentType<Map<String, Integer>> BOND = AttachmentRegistry.create(JaymosKingdoms.id("comrade_bond"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** On a companion: MCA hearts last read, per player. Saved. */
	static final AttachmentType<Map<String, Integer>> HEARTS_SEEN = AttachmentRegistry.create(JaymosKingdoms.id("comrade_bond_hearts"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** On the Overworld: sworn captain id → his bond with his lord. Saved. */
	static final AttachmentType<Map<String, Integer>> CAPTAINS = AttachmentRegistry.create(JaymosKingdoms.id("captain_bonds"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	/** Companions lying downed: since when (game time). */
	private static final Map<UUID, Long> DOWN_SINCE = new HashMap<>();
	/** Downed long enough with their player far off: already docked for it. */
	private static final Set<UUID> DOCKED = new HashSet<>();

	private Bonds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Bonds::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DOWN_SINCE.clear();
			DOCKED.clear();
		});
	}

	// ---- whose ---------------------------------------------------------------------------------------------------------

	/** The player {@code m} follows (a companion's, or a sworn captain's lord), or null. */
	static @Nullable UUID lordOf(LivingEntity m) {
		if (SwornCaptains.free(m)) {
			return SwornCaptains.lordOf(m);
		}
		String side = Comrades.side(m);
		if (!side.startsWith("p:") || !(m instanceof Mob)) {
			return null;
		}
		try {
			return UUID.fromString(side.substring(2));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** {@code player}'s companions (and sworn captains) within {@code range}: up, grown and humanoid. */
	static List<Mob> companions(ServerPlayer player, double range) {
		UUID id = player.getUUID();
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
			m -> m.isAlive() && !m.isBaby() && !Downed.isDowned(m) && Temperaments.humanoid(m) && id.equals(lordOf(m)));
	}

	// ---- the bond ------------------------------------------------------------------------------------------------------

	/** {@code m}'s bond with {@code player}. */
	static int of(Mob m, UUID player) {
		String captain = captainId(m);
		if (captain != null && m.level() instanceof ServerLevel level) {
			return level.getServer().overworld().getAttachedOrElse(CAPTAINS, Map.of()).getOrDefault(captain, 0);
		}
		return m.getAttachedOrElse(BOND, Map.of()).getOrDefault(player.toString(), 0);
	}

	/** Whether {@code m}'s bond with its own player is close. */
	static boolean close(Mob m) {
		UUID lord = lordOf(m);
		return lord != null && of(m, lord) >= CLOSE;
	}

	/** {@code m}'s bond with {@code player} moves by {@code by}; crossing into close (or out of it) is said aloud. */
	static void add(Mob m, UUID player, int by) {
		if (by == 0) {
			return;
		}
		int was = of(m, player);
		int now = Math.max(0, Math.min(MAX, was + by));
		String captain = captainId(m);
		if (captain != null && m.level() instanceof ServerLevel level) {
			ServerLevel overworld = level.getServer().overworld();
			Map<String, Integer> all = new HashMap<>(overworld.getAttachedOrElse(CAPTAINS, Map.of()));
			all.put(captain, now);
			overworld.setAttached(CAPTAINS, all);
		} else {
			Map<String, Integer> all = new HashMap<>(m.getAttachedOrElse(BOND, Map.of()));
			all.put(player.toString(), now);
			m.setAttached(BOND, all);
		}
		if (was < CLOSE && now >= CLOSE) {
			Callouts.say(m, "bond.close", null, true);
			if (m.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
			}
		} else if (was >= CLOSE && now < CLOSE) {
			Callouts.say(m, "bond.cool", null, true);
		}
	}

	/** {@code m} holds out against being turned (a thrall-bite, a sorcerer's hold): likelier the closer it is. */
	public static boolean resists(Mob m, RandomSource random) {
		UUID lord = lordOf(m);
		return lord != null && random.nextInt(MAX + 40) < of(m, lord);
	}

	/** (SwornCaptains, at a dawn he'd turn) a sworn captain keeps faith instead: as likely as his bond is close. */
	static boolean keepsFaith(MinecraftServer server, String captainId, RandomSource random) {
		int bond = server.overworld().getAttachedOrElse(CAPTAINS, Map.of()).getOrDefault(captainId, 0);
		return random.nextInt(MAX) < bond;
	}

	private static @Nullable String captainId(Mob m) {
		return SwornCaptains.free(m) && Captains.is(m) ? Captains.idOf(m) : null;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			UUID id = player.getUUID();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0),
				m -> m.isAlive() && Temperaments.humanoid(m) && id.equals(lordOf(m)))) {
				seen.add(m.getUUID());
				if (Downed.isDowned(m)) {
					long since = DOWN_SINCE.computeIfAbsent(m.getUUID(), u -> now);
					if (now - since >= LEFT_TICKS && m.distanceTo(player) > LEFT_FAR && DOCKED.add(m.getUUID())) {
						add(m, id, -LEFT);
					}
					continue;
				}
				if (DOWN_SINCE.remove(m.getUUID()) != null) {
					// up again: with you by, they know who got them up
					DOCKED.remove(m.getUUID());
					if (m.distanceTo(player) <= SAVED_NEAR) {
						add(m, id, SAVED);
						Callouts.say(m, "thanks", player, true, player.getDisplayName());
					}
				}
				if (m instanceof Villager v) {
					hearts(v, player);
				}
				stayNear(m, player);
			}
		}
		// anyone downed out of every player's reach keeps counting; anyone gone is forgotten
		DOWN_SINCE.keySet().removeIf(u -> !seen.contains(u) && now - DOWN_SINCE.get(u) > LEFT_TICKS * 20L);
		DOCKED.retainAll(DOWN_SINCE.keySet());
	}

	/** Whatever warmed them in MCA since last looked warms the bond too. */
	private static void hearts(Villager v, ServerPlayer player) {
		int hearts = McaCompat.heartsOf(v, player);
		String key = player.getUUID().toString();
		Map<String, Integer> seen = v.getAttachedOrElse(HEARTS_SEEN, Map.of());
		Integer last = seen.get(key);
		if (last == null || hearts != last) {
			Map<String, Integer> next = new HashMap<>(seen);
			next.put(key, hearts);
			v.setAttached(HEARTS_SEEN, next);
		}
		if (last != null && hearts - last >= HEARTS_PER_POINT) {
			add(v, player.getUUID(), (hearts - last) / HEARTS_PER_POINT);
		}
	}

	/** A close companion, out of a fight and not told to wait, keeps by you. */
	private static void stayNear(Mob m, ServerPlayer player) {
		if (!(m instanceof PathfinderMob) || Comrades.target(m) != null || Comrades.busy(m) || m.isPassenger() || m.isSleeping()
			|| m.distanceTo(player) <= NEAR || m.distanceTo(player) > 24.0 || !close(m)) {
			return;
		}
		QuickOrders.Stance stance = QuickOrders.stance(m);
		Followers.Order order = m.getAttached(Followers.ORDER);
		if (stance != null && stance.kind() == QuickOrders.WAIT
			|| order != null && order.kind() != Followers.FOLLOW && order.kind() != Followers.FIGHT) {
			return;
		}
		if (m instanceof Villager v && McaCompat.following(v) == null && order == null) {
			// (going about their day: not following you now)
			return;
		}
		m.getNavigation().moveTo(player, 0.6);
	}

	/** Every list a companion's bond touches (for {@link Remarks}): the close ones near {@code player}. */
	static List<Mob> closeNear(ServerPlayer player, double range) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : companions(player, range)) {
			if (of(m, player.getUUID()) >= CLOSE) {
				out.add(m);
			}
		}
		return out;
	}
}
