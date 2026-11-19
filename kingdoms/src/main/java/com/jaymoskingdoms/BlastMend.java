package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Blast mending (village mods patch 2, ideas from Guard Villagers' mending; our own code). A blast that breaks blocks on a
 * settlement's land ({@code ServerExplosionMixin} calls {@link #noted}) leaves what it broke noted for that settlement's builders:
 * one builder of each crew, by day ({@link Upkeep}'s hand), puts each block back from the storehouse.
 * <ul>
 * <li>A <b>monster's</b> blast (a creeper's, a ghast's, anything not a player's or a siege's) is mended for the price of the
 * block out of the storehouse.</li>
 * <li>A blast a <b>player</b> caused stays, unless the town pays: {@link #PAID_COST} out of its treasury for every block, and only
 * while it has that.</li>
 * <li>A blast a <b>siege</b> caused is not noted here at all: {@link SiegeScars} keeps sieges (its own breaches, mended by the war
 * chest's masons).</li>
 * </ul>
 * Blocks that hold things (chests, signs, beds) and anything with no item to put back are never noted; a hole someone else filled
 * is forgotten.
 */
public final class BlastMend {
	/** What the town pays out of its treasury for each block of a player's blast it mends, and the most holes kept for a settlement. */
	static final int PAID_COST = 2, HOLES_MAX = 600;
	/** A siege this near a blast (blocks) is the blast's cause. */
	static final double SIEGE_NEAR = 80.0;
	/** The most blocks noted from one blast. */
	static final int BLAST_MOST = 400;
	/** How often (ticks) the holes still waiting are put back in the builders' queue, and how many at a time for each settlement. */
	static final int RENOTE_TICKS = 600, RENOTE_MOST = 48;

	/** A hole: where, what stood there, and whether mending it is the town's to pay for (a player's blast). */
	record Hole(long pos, BlockState state, boolean paid) {
		static final Codec<Hole> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Hole::pos),
			BlockState.CODEC.fieldOf("state").forGetter(Hole::state),
			Codec.BOOL.fieldOf("paid").forGetter(Hole::paid)).apply(i, Hole::new));
	}

	/** On the Overworld (Factions' level): settlement id → the holes blasts left on its land. Saved. */
	static final AttachmentType<Map<String, List<Hole>>> HOLES = AttachmentRegistry.create(JaymosKingdoms.id("blast_holes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Hole.CODEC.listOf())));

	private BlastMend() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % RENOTE_TICKS == 0) {
				renote(level);
			}
		});
	}

	/**
	 * A blast at {@code center} is about to break {@code targets}: on a settlement's land its monster-made or player-made holes are
	 * noted for its builders; elsewhere the soft ground it takes is marked to heal ({@link Craters}).
	 */
	public static void noted(ServerLevel level, net.minecraft.world.phys.Vec3 center, List<BlockPos> targets, boolean player) {
		MinecraftServer server = level.getServer();
		BlockPos at = BlockPos.containing(center);
		Faction f = level == Factions.level(server) ? Factions.around(server, level, at, Upkeep.NOTE_RADIUS) : null;
		if (f == null || f.fallen()) {
			Craters.mark(level, targets);
			return;
		}
		if (siege(level, center)) {
			return;
		}
		Map<String, List<Hole>> all = new HashMap<>(level.getAttachedOrElse(HOLES, Map.of()));
		List<Hole> holes = new ArrayList<>(all.getOrDefault(Integer.toString(f.id()), List.of()));
		int added = 0;
		for (BlockPos pos : targets) {
			if (added >= BLAST_MOST || holes.size() >= HOLES_MAX) {
				break;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getBlock().asItem() == Items.AIR) {
				continue;
			}
			long key = pos.asLong();
			holes.removeIf(h -> h.pos() == key);
			holes.add(new Hole(key, state, player));
			added++;
		}
		if (added == 0) {
			return;
		}
		all.put(Integer.toString(f.id()), List.copyOf(holes));
		level.setAttached(HOLES, Map.copyOf(all));
		for (BlockPos pos : targets) {
			Upkeep.note(level, pos);
		}
	}

	/** Whether a siege's party is besieging a seat near {@code center}: the blast is the siege's, and {@link SiegeScars} keeps it. */
	private static boolean siege(ServerLevel level, net.minecraft.world.phys.Vec3 center) {
		for (WarParties.Party party : WarParties.active()) {
			if (party.phase != WarParties.Phase.SIEGE || party.target < 0) {
				continue;
			}
			BlockPos goal = WarParties.goal(level.getServer(), party);
			if (goal != null && goal.distToCenterSqr(center) <= SIEGE_NEAR * SIEGE_NEAR) {
				return true;
			}
		}
		return false;
	}

	/** The hole of settlement {@code id} at {@code pos}, or null. */
	private static @Nullable Hole hole(ServerLevel level, int id, BlockPos pos) {
		long key = pos.asLong();
		for (Hole h : level.getAttachedOrElse(HOLES, Map.<String, List<Hole>>of()).getOrDefault(Integer.toString(id), List.of())) {
			if (h.pos() == key) {
				return h;
			}
		}
		return null;
	}

	private static void forget(ServerLevel level, int id, BlockPos pos) {
		Map<String, List<Hole>> all = new HashMap<>(level.getAttachedOrElse(HOLES, Map.of()));
		List<Hole> holes = new ArrayList<>(all.getOrDefault(Integer.toString(id), List.of()));
		long key = pos.asLong();
		if (holes.removeIf(h -> h.pos() == key)) {
			if (holes.isEmpty()) {
				all.remove(Integer.toString(id));
			} else {
				all.put(Integer.toString(id), List.copyOf(holes));
			}
			level.setAttached(HOLES, Map.copyOf(all));
		}
	}

	/**
	 * (for {@link Upkeep}) The block a builder of settlement {@code id} puts back at {@code pos}, or null: there is no hole, someone
	 * filled it (it's forgotten), something stands in it, or the storehouse (or, for a player's blast, the treasury) can't pay.
	 */
	static @Nullable BlockState fix(ServerLevel level, int id, BlockPos pos) {
		Hole hole = hole(level, id, pos);
		if (hole == null) {
			return null;
		}
		if (!level.getBlockState(pos).canBeReplaced()) {
			forget(level, id, pos);
			return null;
		}
		Faction f = Factions.get(level.getServer(), id);
		Item item = hole.state().getBlock().asItem();
		if (f == null || item == Items.AIR || !Storage.loaded(level, f) || !Storage.has(level, f, Map.of(item, 1)) || hole.paid() && f.treasury() < PAID_COST
			|| !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) {
			return null;
		}
		return hole.state();
	}

	/** (for {@link Upkeep}) The builder put the block back: the hole is closed, the storehouse gives the block, a player's blast is paid for. */
	static void mended(ServerLevel level, int id, BlockPos pos) {
		Hole hole = hole(level, id, pos);
		if (hole == null) {
			return;
		}
		forget(level, id, pos);
		Faction f = Factions.get(level.getServer(), id);
		if (f == null) {
			return;
		}
		Storage.take(level, f, hole.state().getBlock().asItem(), 1);
		if (hole.paid()) {
			Factions.update(level.getServer(), id, g -> g.withTreasury(Math.max(0L, g.treasury() - PAID_COST)));
		}
	}

	/** Holes left waiting (no materials, no silver, a builder busy) are put back in the builders' queue now and then. */
	private static void renote(ServerLevel level) {
		for (Map.Entry<String, List<Hole>> e : level.getAttachedOrElse(HOLES, Map.<String, List<Hole>>of()).entrySet()) {
			int n = 0;
			for (Hole h : e.getValue()) {
				if (n++ >= RENOTE_MOST) {
					break;
				}
				Upkeep.note(level, BlockPos.of(h.pos()));
			}
		}
	}
}
