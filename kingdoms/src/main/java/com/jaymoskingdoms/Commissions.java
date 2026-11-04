package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) Plans offered to a settlement for its growth: a member (or a well-liked friend) standing in its land gives
 * its steward one of their designs (from a {@link MasonTable}'s screen), and its builders put it up on open ground
 * of their own choosing, before anything of their own catalogue, once they're free and its storage holds the
 * materials ({@link Planner#eligible}). Built like any of their buildings: block by block from raw materials, the
 * crew walking to and from the storage ({@link Construction}). When it's finished the one who gave it is thanked,
 * and the settlement thinks better of them. A lord who wants it on a particular spot marks a plot instead.
 */
public final class Commissions {
	/** Reputation (with a settlement you're not sworn to) needed to offer it plans. */
	static final int SUBMIT_REPUTATION = 100;
	/** How far round a settlement's seat counts as standing in it (villages have no land of their own). */
	static final double NEAR_SEAT = 96.0;
	/** The most plans one settlement keeps waiting at once; the most of them from one player. */
	static final int MAX_WAITING = 4, MAX_PER_PLAYER = 1;
	/** Reputation earned when one is finished. */
	static final int REWARD = 40;
	/** How often finished ones are looked for (ticks). */
	static final int CHECK_TICKS = 200;

	/**
	 * One plan given: the design's building id, who gave it (and their name, for the list), and how many of that
	 * building the settlement had (built or under way) when it was given: it's done once more than that are finished.
	 */
	public record Commission(String design, UUID by, String byName, int baseline) {
		public static final Codec<Commission> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("design").forGetter(Commission::design),
				UUIDUtil.CODEC.fieldOf("by").forGetter(Commission::by),
				Codec.STRING.optionalFieldOf("by_name", "").forGetter(Commission::byName),
				Codec.INT.optionalFieldOf("baseline", 0).forGetter(Commission::baseline))
			.apply(i, Commission::new));
	}

	/** Faction id → the plans it was given, oldest first. Kept on the Overworld. */
	static final AttachmentType<Map<String, List<Commission>>> COMMISSIONS = AttachmentRegistry.create(JaymosKingdoms.id("commissions"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Commission.CODEC.listOf())));

	private Commissions() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				finish(server);
			}
		});
	}

	static List<Commission> of(MinecraftServer server, int faction) {
		return server.overworld().getAttachedOrElse(COMMISSIONS, Map.<String, List<Commission>>of()).getOrDefault(Integer.toString(faction), List.of());
	}

	private static void put(MinecraftServer server, int faction, List<Commission> list) {
		Map<String, List<Commission>> all = new HashMap<>(server.overworld().getAttachedOrElse(COMMISSIONS, Map.of()));
		if (list.isEmpty()) {
			all.remove(Integer.toString(faction));
		} else {
			all.put(Integer.toString(faction), List.copyOf(list));
		}
		server.overworld().setAttached(COMMISSIONS, Map.copyOf(all));
	}

	/** Some settlement still waits to build design building {@code id} (so its file may not change). */
	static boolean wanted(MinecraftServer server, String id) {
		for (List<Commission> list : server.overworld().getAttachedOrElse(COMMISSIONS, Map.<String, List<Commission>>of()).values()) {
			if (list.stream().anyMatch(c -> c.design().equals(id))) {
				return true;
			}
		}
		return false;
	}

	/** How many of {@code entry} {@code works} has finished. */
	private static int finished(Works.Site works, String entry) {
		int n = 0;
		for (Works.Built b : works.built()) {
			if (b.entry().equals(entry)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The plans {@code f} was given that its builders may start now (none of it under way yet), as catalogue entries, oldest
	 * first: put ahead of its own growth order by {@link Planner#eligible}.
	 */
	static List<Blueprint.Entry> next(MinecraftServer server, Faction f) {
		List<Blueprint.Entry> out = new ArrayList<>();
		Works.Site works = Works.of(server, f.id());
		Set<String> seen = new HashSet<>();
		for (Commission c : of(server, f.id())) {
			if (works.count(c.design()) > c.baseline() || !seen.add(c.design())) {
				// (under way already, or done and not yet thanked for)
				continue;
			}
			Blueprint.Entry e = Designs.isDesign(c.design()) ? Designs.entry(c.design()) : catalogueEntry(f, c.design());
			if (e != null) {
				out.add(e);
			}
		}
		return out;
	}

	/** (0.7.530) {@code f}'s own catalogue entry {@code id}, or null if its builders have none. */
	private static Blueprint.@Nullable Entry catalogueEntry(Faction f, String id) {
		for (Blueprint.Entry e : Blueprint.catalogue(f.type())) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	/**
	 * (0.7.530) A town's own shared project ({@link TownProjects}) asks its builders for catalogue building {@code entryId}: queued
	 * like a plan given, thanked by nobody. False if its list is full or it has no such building.
	 */
	static boolean give(MinecraftServer server, Faction f, String entryId) {
		if (catalogueEntry(f, entryId) == null) {
			return false;
		}
		List<Commission> list = new ArrayList<>(of(server, f.id()));
		if (list.size() >= MAX_WAITING) {
			return false;
		}
		int baseline = Works.of(server, f.id()).count(entryId) + (int)list.stream().filter(c -> c.design().equals(entryId)).count();
		list.add(new Commission(entryId, net.minecraft.util.Util.NIL_UUID, "", baseline));
		put(server, f.id(), list);
		return true;
	}

	/** Thanks whoever gave a plan that's now finished, and forgets it. */
	private static void finish(MinecraftServer server) {
		Map<String, List<Commission>> all = server.overworld().getAttached(COMMISSIONS);
		if (all == null || all.isEmpty()) {
			return;
		}
		for (Map.Entry<String, List<Commission>> e : all.entrySet()) {
			int id = Integer.parseInt(e.getKey());
			Faction f = Factions.get(server, id);
			if (f == null || f.fallen()) {
				put(server, id, List.of());
				continue;
			}
			Works.Site works = Works.of(server, id);
			List<Commission> left = new ArrayList<>();
			boolean changed = false;
			for (Commission c : e.getValue()) {
				if (finished(works, c.design()) <= c.baseline()) {
					left.add(c);
					continue;
				}
				changed = true;
				ServerPlayer by = server.getPlayerList().getPlayer(c.by());
				if (by != null) {
					Reputation.change(by, f, REWARD);
					by.sendSystemMessage(Component.translatable("message.jaymoskingdoms.commission.built", label(c.design()), f.name()).withStyle(ChatFormatting.GOLD));
					by.level().playSound(null, by.blockPosition(), SoundEvents.VILLAGER_WORK_MASON, SoundSource.PLAYERS, 0.8F, 1.0F);
				}
			}
			if (changed) {
				put(server, id, left);
			}
		}
	}

	private static String label(String id) {
		return Designs.isDesign(id) ? id.substring(Designs.PREFIX.length()) : id;
	}

	/** The settlement {@code player} stands in, if any. */
	private static @Nullable Faction here(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Faction f = Factions.at(server, player.level(), player.blockPosition());
		return f != null ? f : Factions.around(server, player.level(), player.blockPosition(), NEAR_SEAT);
	}

	/** Offers design {@code name} to the settlement {@code player} stands in. */
	static boolean submit(ServerPlayer player, String name) {
		MinecraftServer server = player.level().getServer();
		name = name.toLowerCase(java.util.Locale.ROOT);
		Faction f = here(player);
		if (f == null || f.fallen()) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.commission.nowhere").withStyle(ChatFormatting.RED));
			return false;
		}
		boolean member = f.member(player.getUUID()) != null;
		if (!member && !player.isCreative() && Reputation.get(player, f.id()) < SUBMIT_REPUTATION) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.commission.stranger", f.name()).withStyle(ChatFormatting.RED));
			return false;
		}
		Blueprint bp = Designs.blueprint(server, name);
		if (bp == null) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.unknown", name).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!Designs.forCrews(bp)) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.table_only", Designs.MAX_SIDE, Designs.MAX_HEIGHT, Designs.MAX_BLOCKS)
				.withStyle(ChatFormatting.RED));
			return false;
		}
		if (Blueprint.catalogue(f.type()).isEmpty()) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.commission.no_builders", f.name()).withStyle(ChatFormatting.RED));
			return false;
		}
		List<Commission> list = new ArrayList<>(of(server, f.id()));
		String id = Designs.PREFIX + name;
		if (!player.isCreative() && list.stream().filter(c -> c.by().equals(player.getUUID())).count() >= MAX_PER_PLAYER) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.commission.yours_waiting", f.name()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (list.size() >= MAX_WAITING) {
			MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.commission.full", f.name()).withStyle(ChatFormatting.RED));
			return false;
		}
		// (a second plan for the same design waits for the first to be finished)
		int baseline = Works.of(server, f.id()).count(id) + (int)list.stream().filter(c -> c.design().equals(id)).count();
		list.add(new Commission(id, player.getUUID(), player.getGameProfile().name(), baseline));
		put(server, f.id(), list);
		MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.commission.taken", name, f.name(), bp.width(), bp.depth(), bp.height())
			.withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		JaymosKingdoms.LOGGER.info("{} gave {} the plans for {}", player.getGameProfile().name(), f.name(), id);
		return true;
	}
}
