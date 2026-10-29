package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.332) Rumours with names. A villager (or a realm's folk) who sees a vampire player feed, show themselves hunting
 * ({@link Vampires#exposed}), or a werewolf player change or show the tells of the change ({@link HiddenChange#TELLS})
 * remembers a suspicion against that player, by the name they know them by ({@link Recollections#nameTo}: their MCA
 * name, or the costume's). It is kept with the villager and fades if the player lies low: each {@link #DECAY_TICKS}
 * since they last saw anything takes a point off, so the strongest is forgotten in about three days. A villager with a
 * suspicion gossips about it by name ({@link #gossip}, {@link #LINES} vague lines for each kind), and a guard with a
 * strong one ({@link #STRONG}) turns to watch the player when they come close, with a wary word now and then.
 */
public final class Suspicion {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	public static final String VAMPIRE = "vampire", WOLF = "wolf";
	/** The most a suspicion can reach, and the share from which it is strong. */
	static final int MAX = 4, STRONG = 3;
	/** What one sighting adds: a feeding, a change, a tell or a hunt shown. */
	static final int ADD_FEEDING = 2, ADD_CHANGE = 2, ADD_TELL = 1, ADD_HUNT = 1;
	/** A witness adds a point at most this often (ticks); between times a sighting only keeps the old suspicion fresh. */
	static final long ADD_GAP = 600L;
	/** A point fades after this long without a sighting (ticks): four points, about three days. */
	static final long DECAY_TICKS = 18000L;
	/** How far a witness sees a feeding, a change, a tell or a hunt (blocks); sneaking, this share of it for a feeding. */
	static final double FEED_RANGE = 16.0, CHANGE_RANGE = 24.0, TELL_RANGE = 10.0, HUNT_RANGE = 16.0, SNEAK_SHARE = 0.5;
	/** The chance a villager's gossip is one of their suspicions. */
	static final float GOSSIP_CHANCE = 0.6F;
	/** How many gossip lines there are for each kind; and the guards' wary words. */
	static final int LINES = 8, WARY_LINES = 3;
	/** A guard watches a suspect this close (blocks), and speaks up at most this often (ticks). */
	static final double WARY_RANGE = 10.0;
	static final long WARY_GAP = 600L;

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** What a villager suspects of one player: the name they know them by, what of ("vampire" or "wolf"), how strongly, when they last saw it and last added a point. */
	record Mark(String name, String kind, int strength, long seen, long added) {
		static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Mark::name),
			Codec.STRING.fieldOf("kind").forGetter(Mark::kind),
			Codec.INT.fieldOf("strength").forGetter(Mark::strength),
			Codec.LONG.fieldOf("seen").forGetter(Mark::seen),
			Codec.LONG.fieldOf("added").forGetter(Mark::added)).apply(i, Mark::new));

		/** The strength left now. */
		int now(long time) {
			return strength - (int) Math.max(0L, (time - seen) / DECAY_TICKS);
		}
	}

	/** On a villager or folk: who they suspect (by the id they know them by). Kept. */
	static final AttachmentType<Map<UUID, Mark>> MARKS = AttachmentRegistry.create(JaymosKingdoms.id("suspicions"),
		builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Mark.CODEC)));

	/** Not kept: when each guard may speak up again. */
	private static final Map<UUID, Long> WARNED = new HashMap<>();

	private Suspicion() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Suspicion::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WARNED.clear());
	}

	// ---- seeing --------------------------------------------------------------------------------------------------------

	/**
	 * {@code player} was seen by the villagers and folk near them (within {@code range} blocks, in line of sight; or all of
	 * them if {@code anyone}): each adds {@code add} points to what they suspect of them ({@link #VAMPIRE} or {@link #WOLF}).
	 */
	static void witnessed(ServerLevel level, ServerPlayer player, String kind, int add, double range, boolean anyone) {
		if (player.isSpectator() || player.isCreative()) {
			return;
		}
		long now = level.getGameTime();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			x -> x.isAlive() && !x.isSleeping() && Recollections.rememberer(x) && x.distanceTo(player) <= range)) {
			if (anyone || e.hasLineOfSight(player)) {
				mark(e, player, kind, add, now);
			}
		}
	}

	private static void mark(LivingEntity who, ServerPlayer player, String kind, int add, long now) {
		Map<UUID, Mark> marks = new HashMap<>(who.getAttachedOrElse(MARKS, Map.of()));
		UUID key = Recollections.key(player, who);
		Mark old = marks.get(key);
		String name = Recollections.nameTo(player, who).getString();
		if (old == null || old.now(now) <= 0 || !old.kind().equals(kind)) {
			marks.put(key, new Mark(name, kind, Math.min(MAX, add), now, now));
		} else if (now - old.added() >= ADD_GAP) {
			marks.put(key, new Mark(name, kind, Math.min(MAX, old.now(now) + add), now, now));
		} else {
			marks.put(key, new Mark(name, kind, old.strength(), now, old.added()));
		}
		who.setAttached(MARKS, marks);
	}

	/** How strongly {@code who} suspects {@code player} now (0 for none). */
	static int strength(LivingEntity who, ServerPlayer player) {
		Mark m = who.getAttachedOrElse(MARKS, Map.<UUID, Mark>of()).get(Recollections.key(player, who));
		return m == null ? 0 : Math.max(0, m.now(who.level().getGameTime()));
	}

	// ---- every two seconds ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 40 != 7 || !player.isAlive() || player.isSpectator() || player.isCreative()) {
				continue;
			}
			ServerLevel level = player.level();
			String kind = null;
			if (Vampires.isVampire(player) && !VampirePlayers.dhampir(player) && Vampires.exposed(player)) {
				kind = VAMPIRE;
				witnessed(level, player, VAMPIRE, ADD_HUNT, HUNT_RANGE, false);
			} else if (HiddenChange.tells(player) != 0 && Lycans.cursed(player)) {
				kind = WOLF;
				witnessed(level, player, WOLF, ADD_TELL, TELL_RANGE, false);
			}
			if (kind != null || Lycans.cursed(player) || Vampires.isVampire(player)) {
				wary(level, player, now);
			}
		}
	}

	/** A guard with a strong suspicion of {@code player} turns to watch them, and now and then says so. */
	private static void wary(ServerLevel level, ServerPlayer player, long now) {
		for (Folk guard : level.getEntitiesOfClass(Folk.class, player.getBoundingBox().inflate(WARY_RANGE), g -> g.isAlive() && g.combatant())) {
			if (strength(guard, player) < STRONG || !guard.hasLineOfSight(player)) {
				continue;
			}
			guard.getLookControl().setLookAt(player);
			if (now >= WARNED.getOrDefault(guard.getUUID(), 0L)) {
				WARNED.put(guard.getUUID(), now + WARY_GAP);
				Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.suspect.wary." + level.getRandom().nextInt(WARY_LINES),
					Recollections.nameTo(player, guard)), Speech.YELLOW, WARY_RANGE);
			}
		}
	}

	// ---- gossip --------------------------------------------------------------------------------------------------------

	/** A villager's gossip about a player they suspect (not the one they are talking to), or null. */
	static @Nullable Component gossip(LivingEntity who, ServerPlayer listener, RandomSource random) {
		if (random.nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		long now = who.level().getGameTime();
		UUID self = Recollections.key(listener, who);
		List<Mark> said = new ArrayList<>();
		for (Map.Entry<UUID, Mark> e : who.getAttachedOrElse(MARKS, Map.<UUID, Mark>of()).entrySet()) {
			if (!e.getKey().equals(self) && e.getValue().now(now) > 0 && !e.getValue().name().isEmpty()) {
				said.add(e.getValue());
			}
		}
		if (said.isEmpty()) {
			return null;
		}
		Mark m = said.get(random.nextInt(said.size()));
		return Component.translatable("talk.jaymoskingdoms.gossip.suspect." + m.kind() + "." + random.nextInt(LINES), m.name());
	}
}
