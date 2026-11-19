package com.jaymoskingdoms;

import com.jaymoskingdoms.trophies.Trophies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Firsts (medieval deco picks, the journey book and the song stones): the first time somebody slays a great foe
 * ({@link Trophies#foeKind}), founds a realm ({@link Crowns}) or reaches a far world ({@link StarTouched#world}).
 * Each player keeps their own firsts and the day each happened ({@link #MINE}, read in the {@link JourneyBook}, a private
 * book); the world keeps who was first of all ({@link #CUT}, read at a capital's {@link SongStoneBlock}). Nothing is said
 * when a first happens: no toast, no chat, no reward.
 */
public final class Firsts {
	/** The themes a book sorts firsts under. */
	public enum Theme {
		FOES, REALMS, WORLDS;

		String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** Who was first of all, and when (the day, counting from 0). */
	record Cut(String who, long day) {
		static final Codec<Cut> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("who").forGetter(Cut::who),
			Codec.LONG.fieldOf("day").forGetter(Cut::day)).apply(i, Cut::new));
	}

	// ---- tuning ----
	/** Ticks in a day. */
	static final long DAY = 24000L;
	/** How often (ticks) a player's world is looked at for a far world's first. */
	static final int WORLD_CHECK = 100;
	/** A villager within this many blocks of a song stone speaks of what is cut on it, and how many lines it has. */
	static final int REMARK_RANGE = 8, REMARK_LINES = 3;
	/** Lines to a page. */
	static final int PER_PAGE = 8;

	/** On a player: theme/key → the day it first happened to them. Saved, kept through death. */
	static final AttachmentType<Map<String, Long>> MINE = AttachmentRegistry.create(JaymosKingdoms.id("firsts_mine"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());
	/** On the Overworld: theme/key → who was first of all. Saved. */
	static final AttachmentType<Map<String, Cut>> CUT = AttachmentRegistry.create(JaymosKingdoms.id("firsts_cut"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Cut.CODEC)));

	private Firsts() {
	}

	static void register() {
		JourneyBook.register();
		SongStoneBlock.register();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer slayer) {
				String kind = Trophies.foeKind(entity);
				if (kind != null) {
					note(slayer, Theme.FOES, kind);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % WORLD_CHECK == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (StarTouched.world(player.level()) >= 0) {
						note(player, Theme.WORLDS, player.level().dimension().identifier().getPath());
					}
				}
			}
		});
	}

	/** {@code player} founded a realm ({@link Crowns#found}). */
	static void founded(ServerPlayer player) {
		note(player, Theme.REALMS, "founded");
	}

	/** Records that {@code player} did {@code key} under {@code theme}: for them if it is their first time, for the world if it is the first of all. */
	static void note(ServerPlayer player, Theme theme, String key) {
		String id = theme.id() + "/" + key;
		MinecraftServer server = player.level().getServer();
		long day = Factions.level(server).getGameTime() / DAY;
		Map<String, Long> mine = player.getAttachedOrElse(MINE, Map.of());
		if (!mine.containsKey(id)) {
			Map<String, Long> now = new HashMap<>(mine);
			now.put(id, day);
			player.setAttached(MINE, Map.copyOf(now));
		}
		ServerLevel world = Factions.level(server);
		Map<String, Cut> cut = world.getAttachedOrElse(CUT, Map.of());
		if (!cut.containsKey(id)) {
			Map<String, Cut> now = new HashMap<>(cut);
			now.put(id, new Cut(player.getName().getString(), day));
			world.setAttached(CUT, Map.copyOf(now));
		}
	}

	/** What a first is of, as a line: a great foe by name, "founded a realm", a far world by name. */
	private static Component what(String id) {
		String key = id.substring(id.indexOf('/') + 1);
		return id.startsWith(Theme.FOES.id() + "/") ? Trophies.foeName(key) : Component.translatable("firsts.jaymoskingdoms." + id.replace('/', '.'));
	}

	/** The books for {@code theme}s: a book for each that has something in it, its lines {@code line(id)} in pages. */
	private static List<LoreCodex.Entry> books(java.util.Set<String> firsts, java.util.function.Function<String, Component> line) {
		List<LoreCodex.Entry> out = new ArrayList<>();
		for (Theme theme : Theme.values()) {
			List<Component> lines = new ArrayList<>();
			for (String id : new TreeSet<>(firsts)) {
				if (id.startsWith(theme.id() + "/")) {
					lines.add(line.apply(id));
				}
			}
			if (lines.isEmpty()) {
				continue;
			}
			List<Component> pages = new ArrayList<>();
			for (int i = 0; i < lines.size(); i += PER_PAGE) {
				MutableComponent page = Component.empty();
				for (int j = i; j < Math.min(lines.size(), i + PER_PAGE); j++) {
					page.append(lines.get(j)).append("\n\n");
				}
				pages.add(page);
			}
			out.add(new LoreCodex.Entry("firsts_" + theme.id(), Component.translatable("firsts.jaymoskingdoms.theme." + theme.id()), pages));
		}
		return out;
	}

	/** The journey book, open to {@code player}: their firsts, a book for each theme, each with the day it happened. */
	static void openJourney(ServerPlayer player) {
		Map<String, Long> mine = player.getAttachedOrElse(MINE, Map.of());
		send(player, books(mine.keySet(), id -> Component.translatable("firsts.jaymoskingdoms.mine", what(id), mine.get(id) + 1)), "firsts.jaymoskingdoms.journey");
	}

	/** The song stone at {@code pos} read by {@code player}: who was first, a book for each theme; a villager nearby names one of them. */
	static void readStone(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		Map<String, Cut> cut = Factions.level(level.getServer()).getAttachedOrElse(CUT, Map.of());
		send(player, books(cut.keySet(), id -> Component.translatable("firsts.jaymoskingdoms.cut", what(id), cut.get(id).who(), cut.get(id).day() + 1)), "firsts.jaymoskingdoms.stone");
		if (cut.isEmpty()) {
			return;
		}
		Villager speaker = null;
		double best = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(REMARK_RANGE), LivingEntity::isAlive)) {
			double d = v.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (d < best) {
				best = d;
				speaker = v;
			}
		}
		if (speaker != null) {
			List<Cut> all = new ArrayList<>(cut.values());
			Speech.say(level, speaker, Component.translatable("speech.jaymoskingdoms.firsts." + level.getRandom().nextInt(REMARK_LINES),
				all.get(level.getRandom().nextInt(all.size())).who()), Speech.GOLD, 16.0);
		}
	}

	/** Opens {@code books} for {@code player}; with none yet, one book whose single page says so. */
	private static void send(ServerPlayer player, List<LoreCodex.Entry> books, String title) {
		if (!ServerPlayNetworking.canSend(player, LoreCodex.Open.TYPE)) {
			return;
		}
		if (books.isEmpty()) {
			books = List.of(new LoreCodex.Entry("firsts_none", Component.translatable(title), List.of(Component.translatable("firsts.jaymoskingdoms.none"))));
		}
		ServerPlayNetworking.send(player, new LoreCodex.Open(Component.translatable(title), books));
	}
}
