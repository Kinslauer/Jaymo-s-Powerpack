package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.11) Buildings lost are put up again. Now and then each settlement looks over one of its finished buildings
 * (round them all in turn): if a good share of what it's made of is gone (burnt or broken: {@link #RUINED_SHARE} of
 * its paid-for blocks, not counting crops, plants, water or soil), it's in ruins. Left in ruins for
 * {@link #REBUILD_AFTER}, it joins its builders' work (whatever else they're doing) to be put up again where it stood, the same as it was (clearing what's left of it
 * first). Mended in the meantime, it's forgotten. Only buildings its own builders put up can be (not the village it
 * grew from, nor a building finished before 0.5.11).
 */
final class Rebuilds {
	/** How often each settlement looks over one of its buildings (ticks). */
	static final int CHECK_TICKS = 300;
	/** The share of a building's blocks that must be gone for it to be in ruins. */
	static final double RUINED_SHARE = 0.35;
	/** How long a building lies in ruins before it's put up again (ticks: half a day). */
	static final long REBUILD_AFTER = 12_000L;
	/** Announced to players this close to the seat. */
	static final double NEWS_RANGE = 160.0;

	private static final Map<Integer, Integer> NEXT = new HashMap<>();

	private Rebuilds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Rebuilds::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && level.isPositionEntityTicking(f.seat())) {
				look(level, f, now);
			}
		}
	}

	/** One of {@code f}'s buildings looked over. */
	private static void look(ServerLevel level, Faction f, long now) {
		List<Works.Built> built = Works.of(level.getServer(), f.id()).built();
		if (built.isEmpty()) {
			return;
		}
		Works.Built b = built.get(Math.floorMod(NEXT.merge(f.id(), 1, Integer::sum), built.size()));
		if (b.rotation().isEmpty() || !level.isLoaded(b.min()) || !level.isLoaded(b.max())) {
			return;
		}
		Blueprint.Entry entry = Planner.entry(f, b.entry());
		if (entry == null) {
			return;
		}
		Planner.Layout layout = Planner.layout(level, f, b.entry(), entry.maker().make(level, f, b.variant()), b.min(), b.rotation().get(), false, false);
		int made = 0, gone = 0;
		for (Planner.Op op : layout.ops()) {
			if (op.cost() == null) {
				continue;
			}
			made++;
			BlockState there = level.getBlockState(op.pos());
			if (there.isAir() || there.is(BlockTags.FIRE)) {
				gone++;
			}
		}
		boolean ruined = made > 0 && gone >= made * RUINED_SHARE;
		if (ruined && b.ruined() == 0L) {
			replace(level, f, b, b.withRuined(now));
			news(level, f, Component.translatable("message.jaymoskingdoms.build.ruined", f.name(), name(b.entry())));
			JaymosKingdoms.LOGGER.info("{}'s {} at {} lies in ruins ({} of {} blocks gone)", f.name(), b.entry(), b.min(), gone, made);
		} else if (!ruined && b.ruined() != 0L) {
			replace(level, f, b, b.withRuined(0L));
		} else if (ruined && now - b.ruined() >= REBUILD_AFTER) {
			Works.Project again = new Works.Project(b.entry(), b.min(), b.rotation().get(), Works.STAKE, 0, Map.of(), List.of(), -1, now, true,
				b.variant());
			Works.update(level.getServer(), f.id(), s -> {
				List<Works.Built> left = new ArrayList<>(s.built());
				left.removeIf(x -> x.entry().equals(b.entry()) && x.min().equals(b.min()));
				List<Works.Project> projects = new ArrayList<>(s.projects());
				projects.add(again);
				return s.withBuilt(left).withProjects(projects);
			});
			Planner.forget(f.id());
			news(level, f, Component.translatable("message.jaymoskingdoms.build.rebuilding", f.name(), name(b.entry())));
			JaymosKingdoms.LOGGER.info("{} puts up its {} at {} again", f.name(), b.entry(), b.min());
		}
	}

	private static void replace(ServerLevel level, Faction f, Works.Built was, Works.Built now) {
		Works.update(level.getServer(), f.id(), s -> {
			List<Works.Built> list = new ArrayList<>(s.built());
			list.replaceAll(x -> x.entry().equals(was.entry()) && x.min().equals(was.min()) ? now : x);
			return s.withBuilt(list);
		});
	}

	private static Component name(String entry) {
		return Component.translatableWithFallback("building.jaymoskingdoms." + entry, entry);
	}

	private static void news(ServerLevel level, Faction f, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(f.seat().getX(), f.seat().getY(), f.seat().getZ()) <= NEWS_RANGE * NEWS_RANGE) {
				player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GRAY));
			}
		}
	}

	static void clear() {
		NEXT.clear();
	}
}
