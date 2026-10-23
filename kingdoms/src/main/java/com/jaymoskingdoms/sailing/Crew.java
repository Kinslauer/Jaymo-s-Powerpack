package com.jaymoskingdoms.sailing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.42) Sailing, stage 3: a ship with a crew of her own, sailed without a player. A pirate (a cove's ship, or a crew
 * roaming free), a coastal kingdom's warship, or a merchantman on her way to another realm's harbour. Who she is, who
 * her captain is, where she calls home, where she's bound, which gunner serves which gun, and her hold (each player's
 * own share, once it's theirs to take). Her crew ride her at their stations ({@link Ship#SEAT}); Jaymo's Kingdoms
 * sails her ({@link Ship#captaincy}).
 */
public final class Crew {
	public enum Side {
		PIRATE, NAVY, MERCHANT;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static Side parse(String id) {
			for (Side side : values()) {
				if (side.id().equals(id)) {
					return side;
				}
			}
			return PIRATE;
		}
	}

	public Side side;
	/** The faction she sails for (a pirate cove, a free crew, a realm). */
	public int faction;
	/** Her name ("the Black Gull"). */
	public String name;
	public @Nullable UUID captain;
	/** Where she lies when she's at home (offshore of a cove, a harbour's pier). */
	public BlockPos home;
	/** Where she's bound (a merchantman's harbour, a pirate's raid), or null. */
	public @Nullable BlockPos bound;
	/** The faction she's bound for (a merchantman's), or -1. */
	public int boundFor = -1;
	/** An escort quest she's on ("" none). */
	public String quest = "";
	/** Her hold's chest (its place in her frame, packed; 0 with none is never a chest: the keel's middle). */
	public long hold;
	/** Gun (packed place) → its gunner. */
	public final Map<Long, UUID> gunners = new HashMap<>();
	/** Player → their share of her hold, once they've opened it. */
	public final Map<UUID, List<ItemStack>> plunder = new HashMap<>();
	/** When she was launched (game time). */
	public long born;

	/** Not saved: what she's doing now, its target, the next waypoint, how long she's been stuck, when each gun's loaded. */
	public String mode = "patrol";
	public int target = -1;
	public @Nullable BlockPos waypoint;
	public int stuck, lastFired, backing;
	public final Map<Long, Long> loading = new HashMap<>();
	/** Not saved: the helm her captain has set (a heading, a share of sail, rowing, backing water). */
	public float wantYaw;
	public double wantSail;
	public boolean row, astern;
	/** (0.7.179) Not saved: her deck, for her crew to walk ({@link Deck}), and each crewman's walk about it. */
	public @Nullable Deck deck;
	public final Map<UUID, Walk> walks = new HashMap<>();

	/** (0.7.179) A crewman's walk: the places still to walk through (in her frame), how fast, and when they set off again. */
	public static final class Walk {
		public List<Vec3> way = new ArrayList<>();
		public double pace;
		public long rest;
	}

	public Crew(Side side, int faction, String name, BlockPos home) {
		this.side = side;
		this.faction = faction;
		this.name = name;
		this.home = home;
	}

	/** Whether her captain is aboard and alive (he has her wheel, and nobody else may take it). */
	public boolean captained(Ship ship) {
		if (this.captain == null) {
			return false;
		}
		for (Entity e : ship.getPassengers()) {
			if (e.getUUID().equals(this.captain)) {
				return e.isAlive();
			}
		}
		return false;
	}

	private record Saved(String side, int faction, String name, Optional<UUID> captain, BlockPos home, Optional<BlockPos> bound, int boundFor,
		String quest, long hold, Map<String, String> gunners, Map<String, List<ItemStack>> plunder, long born, String mode) {
	}

	public static final Codec<Crew> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
			Codec.STRING.fieldOf("side").forGetter(Saved::side),
			Codec.INT.fieldOf("faction").forGetter(Saved::faction),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Saved::name),
			UUIDUtil.CODEC.optionalFieldOf("captain").forGetter(Saved::captain),
			BlockPos.CODEC.fieldOf("home").forGetter(Saved::home),
			BlockPos.CODEC.optionalFieldOf("bound").forGetter(Saved::bound),
			Codec.INT.optionalFieldOf("bound_for", -1).forGetter(Saved::boundFor),
			Codec.STRING.optionalFieldOf("quest", "").forGetter(Saved::quest),
			Codec.LONG.optionalFieldOf("hold", 0L).forGetter(Saved::hold),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("gunners", Map.of()).forGetter(Saved::gunners),
			Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf()).optionalFieldOf("plunder", Map.of()).forGetter(Saved::plunder),
			Codec.LONG.optionalFieldOf("born", 0L).forGetter(Saved::born),
			Codec.STRING.optionalFieldOf("mode", "patrol").forGetter(Saved::mode))
		.apply(i, Saved::new)).xmap(Crew::of, Crew::saved);

	private static Crew of(Saved s) {
		Crew c = new Crew(Side.parse(s.side()), s.faction(), s.name(), s.home());
		c.captain = s.captain().orElse(null);
		c.bound = s.bound().orElse(null);
		c.boundFor = s.boundFor();
		c.quest = s.quest();
		c.hold = s.hold();
		for (Map.Entry<String, String> e : s.gunners().entrySet()) {
			try {
				c.gunners.put(Long.parseLong(e.getKey()), UUID.fromString(e.getValue()));
			} catch (IllegalArgumentException ignored) {
			}
		}
		for (Map.Entry<String, List<ItemStack>> e : s.plunder().entrySet()) {
			try {
				c.plunder.put(UUID.fromString(e.getKey()), new ArrayList<>(e.getValue()));
			} catch (IllegalArgumentException ignored) {
			}
		}
		c.born = s.born();
		c.mode = s.mode();
		return c;
	}

	private Saved saved() {
		Map<String, String> guns = new HashMap<>();
		this.gunners.forEach((k, v) -> guns.put(Long.toString(k), v.toString()));
		Map<String, List<ItemStack>> loot = new HashMap<>();
		this.plunder.forEach((k, v) -> loot.put(k.toString(), List.copyOf(v)));
		return new Saved(this.side.id(), this.faction, this.name, Optional.ofNullable(this.captain), this.home, Optional.ofNullable(this.bound),
			this.boundFor, this.quest, this.hold, guns, loot, this.born, this.mode);
	}
}
