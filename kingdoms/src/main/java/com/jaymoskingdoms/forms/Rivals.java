package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Relation;
import com.jaymoskingdoms.Relations;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.forms.Schools.School;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.385) Rival schools (Weapon Forms, patch 5). Each realm has a local school ({@link Schools#localSchool} at its seat). Two
 * schools are <b>rivals</b> when a realm of one and a realm of the other are at odds ({@link Relation#RIVAL} or
 * {@link Relation#WAR}). People of a school look down on a player who has joined its rival:
 * <ul>
 * <li>{@link #greeting}: the line a schooled guard opens a work talk with ({@code WorkTalk}) instead of the plain one;</li>
 * <li>a sneer in a speech bubble when such a player walks near a schooled guard ({@link #SNEER_CHANCE}, and no more than one
 * every {@link #SNEER_GAP} ticks for a player);</li>
 * <li>a line in the player's Tidings on first entering a rival school's realm each day;</li>
 * <li>honour duels are offered {@link #CHALLENGE_FACTOR} times as often ({@link Duels}).</li>
 * </ul>
 * The rival table is rebuilt every {@link #REFRESH} ticks; nothing here is saved.
 */
public final class Rivals {
	// ---- tuning ----
	/** How often (ticks) the table of rival schools is rebuilt, and how often players are looked at. */
	static final long REFRESH = 1200L;
	static final int LOOK_EVERY = 100;
	/** How much likelier a rival school's student is to challenge you. */
	public static final float CHALLENGE_FACTOR = 3.0F;
	/** The chance, at each look, that a schooled guard near a rival's student sneers; how near (blocks); the wait before the next for that player (ticks). */
	static final float SNEER_CHANCE = 0.2F;
	static final double SNEER_RANGE = 8.0;
	static final long SNEER_GAP = 1800L;
	/** How many lines of each kind there are. */
	static final int LINES = 3;
	static final long DAY_TICKS = 24000L;

	private static final Map<School, EnumSet<School>> TABLE = new EnumMap<>(School.class);
	private static long builtAt = Long.MIN_VALUE;
	private static final Map<UUID, Long> SNEERED = new HashMap<>();
	/** player → "realm:day" of the Tidings line already sent. */
	private static final Map<UUID, String> TOLD = new HashMap<>();

	private Rivals() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Rivals::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TABLE.clear();
			SNEERED.clear();
			TOLD.clear();
			builtAt = Long.MIN_VALUE;
		});
	}

	// ---- the table ---------------------------------------------------------------------------------------------------

	/** Two realms at odds. */
	static boolean atOdds(Faction a, Faction b) {
		Relation r = Relations.between(a, b);
		return r == Relation.RIVAL || r == Relation.WAR;
	}

	private static void rebuild(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now - builtAt < REFRESH && builtAt != Long.MIN_VALUE) {
			return;
		}
		builtAt = now;
		TABLE.clear();
		List<Faction> realms = new ArrayList<>();
		List<School> schools = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm()) {
				continue;
			}
			School s = Schools.localSchool(level, f.seat());
			if (s != null) {
				realms.add(f);
				schools.add(s);
			}
		}
		for (int i = 0; i < realms.size(); i++) {
			for (int j = i + 1; j < realms.size(); j++) {
				if (schools.get(i) != schools.get(j) && atOdds(realms.get(i), realms.get(j))) {
					TABLE.computeIfAbsent(schools.get(i), k -> EnumSet.noneOf(School.class)).add(schools.get(j));
					TABLE.computeIfAbsent(schools.get(j), k -> EnumSet.noneOf(School.class)).add(schools.get(i));
				}
			}
		}
	}

	/** Whether {@code a} and {@code b} are rival schools just now (a realm of each at odds). */
	public static boolean rival(MinecraftServer server, @Nullable School a, @Nullable School b) {
		if (a == null || b == null || a == b) {
			return false;
		}
		rebuild(server);
		EnumSet<School> set = TABLE.get(a);
		return set != null && set.contains(b);
	}

	/** The school {@code player} has joined that is a rival of {@code local}, or null. */
	public static @Nullable School rivalOf(MinecraftServer server, ServerPlayer player, @Nullable School local) {
		if (local == null) {
			return null;
		}
		for (School mine : Schools.joined(player)) {
			if (rival(server, mine, local)) {
				return mine;
			}
		}
		return null;
	}

	/** A school's name in lines ("the blade school"). */
	public static Component name(School school) {
		return Component.translatable("duel.jaymoskingdoms.school." + school.key());
	}

	// ---- lines -------------------------------------------------------------------------------------------------------

	/**
	 * The line a schooled guard opens a talk about work with (from {@code WorkTalk}) when {@code player} has joined a rival of
	 * its school, else null.
	 */
	public static @Nullable Component greeting(ServerPlayer player, LivingEntity speaker) {
		if (!(speaker instanceof PathfinderMob mob) || !(mob.level() instanceof ServerLevel level) || !com.jaymoskingdoms.Downed.isGuard(mob)) {
			return null;
		}
		School theirs = SchooledGuards.schoolOf(level, mob);
		School mine = rivalOf(level.getServer(), player, theirs);
		if (theirs == null || mine == null) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.rival.greet." + level.getRandom().nextInt(LINES), player.getDisplayName(), name(mine), name(theirs));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level) || Schools.joined(player).isEmpty()) {
				continue;
			}
			realmLine(server, level, player);
			sneer(server, level, player);
		}
	}

	/** First time today in the land of a rival school's realm: a line in the Tidings. */
	private static void realmLine(MinecraftServer server, ServerLevel level, ServerPlayer player) {
		Faction f = Factions.at(server, level, player.blockPosition());
		if (f == null || f.fallen() || !f.type().isRealm()) {
			return;
		}
		School local = Schools.localSchool(level, f.seat());
		School mine = rivalOf(server, player, local);
		if (local == null || mine == null) {
			return;
		}
		String mark = f.id() + ":" + level.getGameTime() / DAY_TICKS;
		if (mark.equals(TOLD.put(player.getUUID(), mark))) {
			return;
		}
		Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.rival.land." + level.getRandom().nextInt(LINES),
			f.name(), name(local), name(mine)));
	}

	/** A schooled guard near a rival's student looks down on them, in a bubble. */
	private static void sneer(MinecraftServer server, ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		Long last = SNEERED.get(player.getUUID());
		if (last != null && now - last < SNEER_GAP || level.getRandom().nextFloat() >= SNEER_CHANCE) {
			return;
		}
		for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(SNEER_RANGE),
			m -> m.isAlive() && com.jaymoskingdoms.Downed.isGuard(m) && !com.jaymoskingdoms.Downed.isDowned(m) && m.hasLineOfSight(player))) {
			School theirs = SchooledGuards.schoolOf(level, mob);
			School mine = rivalOf(server, player, theirs);
			if (theirs != null && mine != null) {
				SNEERED.put(player.getUUID(), now);
				Speech.tell(level, mob, player, Component.translatable("talk.jaymoskingdoms.rival.sneer." + level.getRandom().nextInt(LINES),
					player.getDisplayName(), name(mine), name(theirs)), Speech.YELLOW);
				return;
			}
		}
	}
}
