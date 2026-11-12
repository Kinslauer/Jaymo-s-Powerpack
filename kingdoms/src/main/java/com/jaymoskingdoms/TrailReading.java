package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Read the Ground, a Survival perk for anyone ({@link Skills.Perk#READ_THE_GROUND}; Andy: "this should instead be added to
 * the survivalist tree. available for everyone").
 * <p>
 * Everything that walks leaves prints: once a second every creature on the ground near any player that has gone at least
 * {@link #STRIDE} blocks since its last print leaves one where it stands, with the way it was heading ({@link #record}).
 * They last {@link #KEEP_TICKS} and are kept in memory only (a restart wipes the ground clean). Nobody sees them as they
 * are made.
 * <p>
 * Someone with the perk who crouches and holds still for {@link #READ_AFTER} ticks reads the ground: every print within
 * reach that is young enough shows to them alone as a faint mote that drifts the way its maker went, larger for a larger
 * creature, fainter with age, pale for people, rust for monsters and moss for beasts. Rank 2 reads further and older.
 * A player who sneaks with Soft Step leaves no prints, nor does anything in water or in the air.
 * <p>
 * Blood and drag marks need no perk: they are already on the ground for all to see (Jaymo's Powers' blood trail).
 */
public final class TrailReading {
	// ---- tuning ----
	/** Prints are laid this often (ticks), by creatures within this many blocks of a player, a stride apart. */
	static final int RECORD_EVERY = 20;
	static final double RECORD_RANGE = 40.0, STRIDE = 1.6;
	/** How long a print lasts, and how many a world keeps (the oldest go first). */
	static final int KEEP_TICKS = 20 * 60 * 8, KEEP_MOST = 6000;
	/** Crouched and still this long (ticks) before the ground gives anything up; then it is read this often. */
	static final int READ_AFTER = 20, READ_EVERY = 10;
	/** Per rank: how far the reading reaches (blocks) and how old a print it finds (ticks). */
	static final double[] READ_RANGE = {18.0, 32.0};
	static final int[] READ_AGE = {20 * 60 * 3, KEEP_TICKS};
	/** The most prints shown in one reading. */
	static final int READ_MOST = 160;
	/** Colours: people, monsters, beasts. */
	static final int PEOPLE = 0xE8DFC4, MONSTER = 0xC0644E, BEAST = 0xA9BD86;
	/** A print's mote: size for a creature a block wide, when fresh; an old one is this share of it. Its drift along the heading. */
	static final float SIZE = 0.9F, OLD_SIZE = 0.45F;
	static final double DRIFT = 0.035;

	private record Print(double x, double y, double z, float hx, float hz, int color, float size, int maker, long at) {
	}

	private static final Map<ResourceKey<Level>, ArrayDeque<Print>> PRINTS = new HashMap<>();
	private static final Map<LivingEntity, Vec3> LAST = new WeakHashMap<>();
	/** Reader → ticks crouched and still. */
	private static final Map<ServerPlayer, Integer> STILL = new WeakHashMap<>();

	private TrailReading() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PRINTS.clear();
			LAST.clear();
			STILL.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TrailReading::tick);
	}

	private static void tick(MinecraftServer server) {
		boolean laying = server.getTickCount() % RECORD_EVERY == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			if (laying) {
				record(level, player);
			}
			int rank = Skills.rank(player, Skills.Perk.READ_THE_GROUND);
			if (rank <= 0) {
				continue;
			}
			boolean still = player.isShiftKeyDown() && player.onGround() && player.getKnownMovement().horizontalDistanceSqr() < 1.0E-4;
			int ticks = still ? STILL.merge(player, 1, Integer::sum) : 0;
			if (!still) {
				STILL.remove(player);
			} else if (ticks >= READ_AFTER && (ticks - READ_AFTER) % READ_EVERY == 0) {
				read(level, player, Math.min(rank, READ_RANGE.length) - 1);
			}
		}
	}

	/** Every walker near {@code player} that has gone a stride since its last print leaves one. */
	private static void record(ServerLevel level, ServerPlayer player) {
		ArrayDeque<Print> prints = PRINTS.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
		long now = level.getGameTime();
		while (!prints.isEmpty() && (prints.size() > KEEP_MOST || now - prints.peekFirst().at > KEEP_TICKS)) {
			prints.pollFirst();
		}
		for (LivingEntity walker : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RECORD_RANGE),
			e -> e.isAlive() && e.onGround() && !e.isInWater() && !e.isSpectator() && !e.isPassenger())) {
			Vec3 at = walker.position(), last = LAST.get(walker);
			if (last == null) {
				LAST.put(walker, at);
				continue;
			}
			double far = at.distanceToSqr(last);
			if (far < STRIDE * STRIDE) {
				continue;
			}
			LAST.put(walker, at);
			if (far > 64.0 * 64.0 || walker instanceof Player sneaker && sneaker.isShiftKeyDown() && Skills.rank(sneaker, Skills.Perk.SOFT_STEP) > 0) {
				// (a teleport leaves no heading; a soft step leaves nothing)
				continue;
			}
			double dx = at.x - last.x, dz = at.z - last.z, flat = Math.sqrt(dx * dx + dz * dz);
			if (flat < 0.3) {
				continue;
			}
			int color = walker instanceof Player || walker instanceof net.minecraft.world.entity.npc.Npc ? PEOPLE : walker instanceof Enemy ? MONSTER : BEAST;
			float size = SIZE * Mth.clamp(0.6F + walker.getBbWidth() * 0.5F, 0.6F, 2.2F);
			prints.addLast(new Print(at.x, at.y + 0.06, at.z, (float)(dx / flat), (float)(dz / flat), color, size, walker.getId(), now));
		}
	}

	/** Shows {@code player} the prints round them (not their own), nearest in time last so the freshest stand out. */
	private static void read(ServerLevel level, ServerPlayer player, int rank) {
		ArrayDeque<Print> prints = PRINTS.get(level.dimension());
		if (prints == null) {
			return;
		}
		long now = level.getGameTime();
		double reach = READ_RANGE[rank] * READ_RANGE[rank];
		int shown = 0;
		// newest first, so a crowded patch of ground shows what is fresh
		for (Iterator<Print> it = prints.descendingIterator(); it.hasNext() && shown < READ_MOST;) {
			Print print = it.next();
			long age = now - print.at;
			if (age > READ_AGE[rank]) {
				break;
			}
			if (print.maker == player.getId() || player.distanceToSqr(print.x, print.y, print.z) > reach) {
				continue;
			}
			float size = print.size * Mth.lerp(age / (float)KEEP_TICKS, 1.0F, OLD_SIZE);
			// (count 0: the offsets are the mote's own drift, the way the maker went)
			level.sendParticles(player, Fx.mote(print.color, size), true, false, print.x, print.y, print.z, 0, print.hx, 0.0, print.hz, DRIFT);
			shown++;
		}
	}
}
