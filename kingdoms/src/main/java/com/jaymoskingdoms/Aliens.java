package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.4) Jaymo's Powers' Xenoform: a player wearing the Xeno-Band can become an alien. To a kingdom's people that is
 * a monster: villagers run from one ({@link Frights}) and guards hunt it ({@link Folk}'s target goals,
 * {@link MobCombat#foe}), unless they know better. A kingdom's folk who <i>see</i> you change (within {@link #WITNESS}
 * blocks, in line of sight) know you for who you are from then on, and that kingdom treats the alien as it treats you.
 *
 * <p>Read by entity tag ({@link #TAG}, set by Powers while a player is changed), so Kingdoms needs no Powers classes.
 *
 * <p>(0.6.11) The same goes for the other shapes a player can take ({@link #KINDS}): the symbiote's black suit or its
 * big body, and a Darktide shade. Each is learned on its own: a kingdom that saw you change into the alien still
 * runs from the suit until it sees you put that on.
 *
 * <p>(0.7.446, Xeno-Band patch 8) A realm whose people have seen anyone change into the alien knows what the band is
 * ({@link #AWARE}), and the other band, Powers' dark-red rival band ({@link #RIVAL_TAG} on its wearer), is a crime to
 * wear where such a realm's people see it ({@link #rivalBand}): heavy, once each {@link #RIVAL_GAP} ticks per realm.
 */
final class Aliens {
	/** The tag Powers puts on a changed player; who has seen a player change (faction ids), kept across deaths. */
	static final String TAG = "jaymospowers_xenoform";
	static final AttachmentType<List<String>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("alien_known"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** How close (blocks) a kingdom's people must be, and seeing you, to catch you changing; how often we look. */
	static final double WITNESS = 16.0;
	static final int EVERY = 10;

	/** What a player can be seen to become: the alien, the symbiote, the shade ({@link Oddities}' tags). */
	static final String ALIEN = "alien";
	static final List<String> KINDS = List.of(ALIEN, Oddities.SYMBIOTE, Oddities.SHADE);

	/** (0.7.446) Realms (faction ids) whose people have seen the band's alien: they know the bands. On the overworld. */
	static final AttachmentType<List<Integer>> AWARE = AttachmentRegistry.create(JaymosKingdoms.id("alien_aware_realms"),
		builder -> builder.persistent(Codec.INT.listOf()));
	/** Powers' tag on the rival band's wearer; how often we look (ticks), and the least gap between charges by one realm. */
	static final String RIVAL_TAG = "jaymospowers_rival_band";
	static final int RIVAL_EVERY = 40, RIVAL_GAP = 2400;

	private static final Map<UUID, Set<String>> WAS = new HashMap<>();
	/** Rival band wearers: realm id → when that realm may next charge them. */
	private static final Map<UUID, Map<Integer, Long>> RIVAL_NEXT = new HashMap<>();

	private Aliens() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Aliens::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAS.clear();
			RIVAL_NEXT.clear();
		});
	}

	/** Whether {@code e} is a player changed into one of the band's aliens right now. */
	static boolean alien(Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** Whether {@code e} shows {@code kind} right now. */
	static boolean is(Entity e, String kind) {
		return ALIEN.equals(kind) ? alien(e) : Oddities.has(e, kind);
	}

	/** What a {@code faction} that saw the change remembers (the alien's entry is the bare id, as before 0.6.9). */
	private static String entry(int faction, String kind) {
		return ALIEN.equals(kind) ? Integer.toString(faction) : faction + ":" + kind;
	}

	/** Whether {@code faction}'s people have seen {@code player} change, and so know the alien is them. */
	static boolean known(ServerPlayer player, int faction) {
		return known(player, faction, ALIEN);
	}

	static boolean known(ServerPlayer player, int faction, String kind) {
		List<String> known = player.getAttached(KNOWN);
		return known != null && known.contains(entry(faction, kind));
	}

	/** What {@code faction}'s people take {@code player} for a monster as, if anything: a shape they haven't seen them take. */
	static @Nullable String fearedAs(ServerPlayer player, int faction) {
		if (player.isCreative() || player.isSpectator()) {
			return null;
		}
		for (String kind : KINDS) {
			if (is(player, kind) && !known(player, faction, kind)) {
				return kind;
			}
		}
		return null;
	}

	/** Whether {@code faction}'s people take {@code player} for a monster: a shape they haven't seen them take. */
	static boolean feared(ServerPlayer player, int faction) {
		return fearedAs(player, faction) != null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % RIVAL_EVERY == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.entityTags().contains(RIVAL_TAG)) {
					rivalBand(server, player);
				}
			}
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Set<String> was = WAS.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
			for (String kind : KINDS) {
				boolean now = is(player, kind);
				if (now && was.add(kind)) {
					witness(server, player, kind);
				} else if (!now) {
					was.remove(kind);
				}
			}
		}
	}

	/** They changed just now: every kingdom with someone near enough, and looking, learns the alien is them. */
	private static void witness(MinecraftServer server, ServerPlayer player, String kind) {
		ServerLevel level = player.level();
		Set<Integer> factions = new LinkedHashSet<>();
		List<LivingEntity> witnesses = new ArrayList<>();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WITNESS),
			e -> e.isAlive() && (e instanceof Folk || e instanceof Villager) && e.hasLineOfSight(player))) {
			Faction f = factionOf(server, near);
			if (f != null && !f.fallen() && f.type().isRealm()) {
				if (factions.add(f.id())) {
					witnesses.add(near);
				}
			}
		}
		if (factions.isEmpty()) {
			return;
		}
		if (ALIEN.equals(kind)) {
			aware(server, factions);
		}
		List<String> known = new ArrayList<>(player.getAttachedOrElse(KNOWN, List.of()));
		boolean news = false;
		for (int id : factions) {
			if (!known.contains(entry(id, kind))) {
				known.add(entry(id, kind));
				news = true;
				Faction f = Factions.get(server, id);
				if (f != null) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms." + kind + ".seen", f.name()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
		}
		if (news) {
			player.setAttached(KNOWN, known);
			LivingEntity speaker = witnesses.get(0);
			Speech.say(level, speaker, Component.translatable("talk.jaymoskingdoms." + kind + ".seen"), Speech.YELLOW, 24.0);
		}
	}

	/** (0.7.446) These realms have seen the band's alien: they know the bands from now on. */
	private static void aware(MinecraftServer server, Set<Integer> factions) {
		List<Integer> aware = new ArrayList<>(server.overworld().getAttachedOrElse(AWARE, List.of()));
		boolean news = false;
		for (int id : factions) {
			if (!aware.contains(id)) {
				aware.add(id);
				news = true;
			}
		}
		if (news) {
			server.overworld().setAttached(AWARE, List.copyOf(aware));
		}
	}

	/** (0.7.446) Whether {@code faction}'s people know the bands (they've seen the alien). */
	static boolean aware(MinecraftServer server, int faction) {
		return server.overworld().getAttachedOrElse(AWARE, List.of()).contains(faction);
	}

	/**
	 * (0.7.446) {@code player} wears the rival band: where a realm that knows the bands holds the law, and its people see
	 * them, that's a heavy crime (seen only: nobody notices a band later).
	 */
	private static void rivalBand(MinecraftServer server, ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		Faction f = Theft.lawAt(level, player.blockPosition());
		if (f == null || f.fallen() || !aware(server, f.id())) {
			return;
		}
		Map<Integer, Long> next = RIVAL_NEXT.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		long now = level.getGameTime();
		if (now < next.getOrDefault(f.id(), 0L)) {
			return;
		}
		List<LivingEntity> seen = Theft.witnesses(level, f, player);
		if (seen.isEmpty()) {
			return;
		}
		next.put(f.id(), now + RIVAL_GAP);
		Theft.commit(player, level, f, Theft.Crime.RIVAL_BAND, null, false, seen);
	}

	/** The kingdom {@code e} belongs to: a folk's own, a villager's by the land they stand on. */
	static @Nullable Faction factionOf(MinecraftServer server, LivingEntity e) {
		if (e instanceof Folk folk) {
			return Factions.get(server, folk.factionId());
		}
		Faction at = Factions.at(server, e.level(), e.blockPosition());
		return at != null ? at : Factions.around(server, e.level(), e.blockPosition(), 64.0);
	}
}
