package com.jaymoskingdoms;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.513) The clerics preach (docs/Patrons-B-Plan.md section 6). Every cleric holds The Truth. In the free evening hours the
 * cleric of a Truth realm or village preaches on the square after a bell; near a shrine given to one of the seven a cleric
 * speaks against it; and a cleric near a player says a word to an apostate, to one just come home to the Truth, or to one
 * sworn to the seven. The Truth's townsfolk shun an apostate. Speech bubbles only, no chat.
 */
public final class Sermons {
	static final int SCAN_EVERY = 100, LINE_GAP = 200, SERMON_LINES = 6;
	static final double HEAR_RANGE = 32.0, SQUARE_RANGE = 8.0, SQUARE_DRAW = 48.0, SQUARE_WALK_SPEED = 0.6, SPEECH_RANGE = 24.0;
	/** The free evening hours (Routines.EVENING..). */
	static final long SQUARE_FROM = 9000L, SQUARE_TILL = 11000L;
	static final int SHRINE_SCAN = 8, SHRINE_SCAN_Y = 3, SHRINE_GAP = 2400, SHRINE_LINES = 3;
	static final double DENOUNCE_RANGE = 8.0, SHUN_RANGE = 5.0;
	static final int DENOUNCE_GAP = 1200, SHUN_GAP = 1200, APOSTATE_LINES = 3, HOME_LINES = 3, HEATHEN_LINES = 3, SHUN_LINES = 3;
	static final float HEATHEN_CHANCE = 0.35F, SHUN_CHANCE = 0.15F;

	/** A realm's preacher for a day. */
	private record Preacher(long day, UUID id) {
	}

	private static final Map<Integer, Preacher> PREACHER = new HashMap<>();
	private static final Map<Integer, Long> BELLED = new HashMap<>(), NEXT_LINE = new HashMap<>(), LINES = new HashMap<>();
	private static final Map<UUID, Long> SHRINE_NEXT = new HashMap<>(), SHUNNED = new HashMap<>();
	private static final Map<String, Long> SPOKE = new HashMap<>();

	private Sermons() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Sermons::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PREACHER.clear();
			BELLED.clear();
			NEXT_LINE.clear();
			LINES.clear();
			SHRINE_NEXT.clear();
			SHUNNED.clear();
			SPOKE.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (level.players().isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		square(server, level, now);
		for (ServerPlayer sp : level.players()) {
			if (!sp.isAlive() || sp.isSpectator()) {
				continue;
			}
			shrines(level, sp, now);
			words(level, sp, now);
			shun(level, sp, now);
		}
	}

	private static boolean preaches(Villager v) {
		return Accusations.priest(v) && v.isAlive() && !v.isSleeping() && !Downed.isDowned(v);
	}

	// ---- the square ------------------------------------------------------------------------------------------------------------

	private static void square(MinecraftServer server, ServerLevel level, long now) {
		long time = Royals.timeOfDay(level);
		if (time < SQUARE_FROM || time >= SQUARE_TILL) {
			return;
		}
		long day = Math.floorDiv(level.getOverworldClockTime(), Production.DAY_TICKS);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || RealmFaiths.patron(server, f) != Patron.TRUTH || !level.isPositionEntityTicking(f.seat())
				|| Festivals.festivalDay(level, f, day)) {
				continue;
			}
			BlockPos seat = f.seat();
			double cx = seat.getX() + 0.5, cy = seat.getY(), cz = seat.getZ() + 0.5;
			if (level.players().stream().noneMatch(p -> !p.isSpectator() && p.distanceToSqr(cx, cy, cz) <= HEAR_RANGE * HEAR_RANGE)) {
				continue;
			}
			Villager priest = preacher(level, f, seat, day);
			if (priest == null) {
				continue;
			}
			Long belled = BELLED.get(f.id());
			if (belled == null || belled != day) {
				BELLED.put(f.id(), day);
				level.playSound(null, seat, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.9F);
			}
			if (priest.distanceToSqr(cx, cy, cz) > SQUARE_RANGE * SQUARE_RANGE) {
				priest.getNavigation().moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, SQUARE_WALK_SPEED);
				continue;
			}
			if (now < NEXT_LINE.getOrDefault(f.id(), 0L)) {
				continue;
			}
			NEXT_LINE.put(f.id(), now + LINE_GAP);
			long n = LINES.merge(f.id(), 1L, Long::sum);
			int i = (int)Math.floorMod(day + n, (long)SERMON_LINES);
			Speech.say(level, priest, Component.translatable("speech.jaymoskingdoms.sermon." + i), Speech.WHITE, SPEECH_RANGE);
		}
	}

	/** The cleric who preaches in {@code f} today: the one kept, else the nearest awake cleric to the seat. */
	private static @Nullable Villager preacher(ServerLevel level, Faction f, BlockPos seat, long day) {
		Preacher kept = PREACHER.get(f.id());
		if (kept != null && kept.day() == day && level.getEntity(kept.id()) instanceof Villager v && preaches(v) && !v.isBaby()) {
			return v;
		}
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(seat).inflate(SQUARE_DRAW), v -> preaches(v) && !v.isBaby());
		if (near.isEmpty()) {
			return null;
		}
		Villager best = near.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5))).orElse(null);
		if (best != null) {
			PREACHER.put(f.id(), new Preacher(day, best.getUUID()));
		}
		return best;
	}

	// ---- shrines of the seven ------------------------------------------------------------------------------------------------------

	private static void shrines(ServerLevel level, ServerPlayer sp, long now) {
		if (now < SHRINE_NEXT.getOrDefault(sp.getUUID(), 0L)) {
			return;
		}
		Villager priest = level.getEntitiesOfClass(Villager.class, sp.getBoundingBox().inflate(HEAR_RANGE), Sermons::preaches).stream()
			.min(Comparator.comparingDouble(v -> v.distanceToSqr(sp))).orElse(null);
		if (priest == null) {
			return;
		}
		SHRINE_NEXT.put(sp.getUUID(), now + SHRINE_GAP);
		BlockPos pos = priest.blockPosition();
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-SHRINE_SCAN, -SHRINE_SCAN_Y, -SHRINE_SCAN), pos.offset(SHRINE_SCAN, SHRINE_SCAN_Y, SHRINE_SCAN))) {
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof Shrine) {
				Patron p = state.getValue(Shrine.DEDICATION).patron();
				if (p != null && p.seven) {
					Speech.say(level, priest, Component.translatable("speech.jaymoskingdoms.sermon.shrine." + priest.getRandom().nextInt(SHRINE_LINES), Patrons.coloured(p)),
						Speech.YELLOW, SPEECH_RANGE);
					return;
				}
			}
		}
	}

	// ---- words for a player near a cleric ----------------------------------------------------------------------------------------------

	private static void words(ServerLevel level, ServerPlayer sp, long now) {
		if (Prejudice.masked(sp)) {
			return;
		}
		Patron mine = Patrons.of(sp);
		boolean apostate = Turning.trial(sp, Patron.TRUTH) > 0.0F;
		Patron cameFrom = null;
		if (mine == Patron.TRUTH) {
			for (Patron p : Patron.values()) {
				if (p != Patron.TRUTH && Turning.trial(sp, p) > 0.0F) {
					cameFrom = p;
				}
			}
		}
		if (!apostate && cameFrom == null && (mine == null || !mine.seven)) {
			return;
		}
		for (Villager priest : level.getEntitiesOfClass(Villager.class, sp.getBoundingBox().inflate(DENOUNCE_RANGE), Sermons::preaches)) {
			String key = priest.getUUID() + "/" + sp.getUUID();
			if (now < SPOKE.getOrDefault(key, 0L) || !priest.hasLineOfSight(sp)) {
				continue;
			}
			if (apostate) {
				Speech.say(level, priest, Component.translatable("speech.jaymoskingdoms.sermon.apostate." + priest.getRandom().nextInt(APOSTATE_LINES)), Speech.RED, SPEECH_RANGE);
			} else if (cameFrom != null) {
				Speech.sayTo(level, priest, sp, Component.translatable("speech.jaymoskingdoms.sermon.home." + priest.getRandom().nextInt(HOME_LINES)), Speech.GOLD);
			} else if (priest.getRandom().nextFloat() < HEATHEN_CHANCE) {
				Speech.sayTo(level, priest, sp, Component.translatable("speech.jaymoskingdoms.sermon.heathen." + priest.getRandom().nextInt(HEATHEN_LINES), Patrons.coloured(mine)),
					Speech.YELLOW);
			} else {
				continue;
			}
			SPOKE.put(key, now + DENOUNCE_GAP);
			return;
		}
	}

	// ---- shunning ------------------------------------------------------------------------------------------------------------------------

	private static void shun(ServerLevel level, ServerPlayer sp, long now) {
		if (Turning.trial(sp, Patron.TRUTH) <= 0.0F || Prejudice.masked(sp) || now < SHUNNED.getOrDefault(sp.getUUID(), 0L)) {
			return;
		}
		List<LivingEntity> folk = level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(SHUN_RANGE),
			x -> x.isAlive() && Faiths.holds(x) && !Accusations.priest(x) && Faiths.of(x) == Patron.TRUTH);
		if (folk.isEmpty() || sp.getRandom().nextFloat() >= SHUN_CHANCE) {
			return;
		}
		LivingEntity who = folk.get(sp.getRandom().nextInt(folk.size()));
		SHUNNED.put(sp.getUUID(), now + SHUN_GAP);
		Speech.say(level, who, Component.translatable("speech.jaymoskingdoms.shun." + who.getRandom().nextInt(SHUN_LINES)), Speech.RED, SPEECH_RANGE);
	}
}
