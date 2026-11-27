package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What a settlement's working folk hold to work with (Andy: "all item materials should change dependent on the
 * availability and realm wealth. only the richer kingdoms spend materials on iron shovels, otherwise they are wooden or
 * stone", then "they should only gain access once a blacksmith is built and actually forges the items").
 * <p>
 * Stone and iron tools are forged ({@link #forge}): a smith (toolsmith, armourer or weaponsmith) at their workstation
 * in working hours ({@link Jobs}) makes them out of the storehouse into the storehouse, {@link #KEEP} of each kind.
 * Iron only in a wealthy realm ({@link Wealth.Tier#WEALTHY}, a fiefdom by its overlord's) with the ingots to hand, stone
 * out of cobblestone otherwise; the handle is two sticks, or a plank cut for them. Folk then work with the best of each
 * kind the storehouse holds ({@link #of}); with none, a wooden one they whittled themselves. A water bucket is iron
 * only if there's a bucket in the stores (a smith forges one in a wealthy realm), else Jaymo's Powers' fired clay one.
 * <p>
 * Tools wear out: once a day ({@link #WEAR_EVERY}) one of each kind in use is gone from the stores, so a town keeps
 * spending iron or stone on them, and a town that runs out of iron goes back to stone as the last iron ones wear out.
 * Each settlement's tools are looked over once every {@link #FRESH} ticks, or as soon as its smith forges one.
 */
public final class ToolGrades {
	public enum Grade { WOOD, STONE, IRON }

	/** What a settlement's folk have of each kind of tool, and whether their bucket is iron. */
	public record Kit(Grade shovel, Grade pickaxe, Grade axe, Grade hoe, boolean ironBucket) {
		/** A camp's, or folk with no settlement: stone, and a clay bucket. */
		public static final Kit STONE = new Kit(Grade.STONE, Grade.STONE, Grade.STONE, Grade.STONE, false);
	}

	private enum Kind { PICKAXE, AXE, SHOVEL, HOE, BUCKET }

	/** How long a settlement's kit holds before it's looked at again (ticks). */
	static final long FRESH = 1200L;
	/** A smith keeps this many of each tool in the stores (a bucket: {@link #KEEP_BUCKETS}). */
	static final int KEEP = 2, KEEP_BUCKETS = 1;
	/** How often a tool of each kind in use wears out (ticks: a day). */
	static final long WEAR_EVERY = 24000L;
	/** Iron ingots or cobblestone a tool takes: pickaxe, axe, shovel, hoe, bucket (a bucket has no handle). */
	private static final int[] HEADS = {3, 3, 1, 2, 3};
	/** Sticks a handle takes; sticks one plank is cut into. */
	static final int HANDLE = 2, STICKS_PER_PLANK = 4;

	private record Seen(Kit kit, long at) {
	}

	private static final Map<Integer, Seen> SEEN = new HashMap<>();

	private ToolGrades() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ToolGrades::wear);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEEN.clear());
	}

	/** The tools {@code mob} works with, by its settlement {@code factionId}; stone off the server or with no settlement. */
	public static Kit of(Entity mob, int factionId) {
		if (!(mob.level() instanceof ServerLevel level) || factionId < 0) {
			return Kit.STONE;
		}
		Faction f = Factions.get(level.getServer(), factionId);
		return f == null ? Kit.STONE : of(level, f);
	}

	/** The tools {@code f}'s working folk have (see the class note). */
	public static Kit of(ServerLevel level, @Nullable Faction f) {
		if (f == null || f.fallen()) {
			return Kit.STONE;
		}
		long now = level.getGameTime();
		Seen seen = SEEN.get(f.id());
		if (seen != null && now - seen.at() < FRESH && now >= seen.at()) {
			return seen.kit();
		}
		ServerLevel home = Factions.level(level.getServer());
		Kit kit = new Kit(stocked(home, f, Kind.SHOVEL), stocked(home, f, Kind.PICKAXE), stocked(home, f, Kind.AXE), stocked(home, f, Kind.HOE),
			Storage.count(home, f, s -> s.is(Items.BUCKET) || s.is(Items.WATER_BUCKET)) > 0);
		SEEN.put(f.id(), new Seen(kit, now));
		return kit;
	}

	/** The best of {@code kind} in {@code f}'s stores, or wood. */
	private static Grade stocked(ServerLevel level, Faction f, Kind kind) {
		if (Storage.count(level, f, item(kind, Grade.IRON)) > 0) {
			return Grade.IRON;
		}
		return Storage.count(level, f, item(kind, Grade.STONE)) > 0 ? Grade.STONE : Grade.WOOD;
	}

	private static Item item(Kind kind, Grade g) {
		return switch (kind) {
			case PICKAXE -> pickaxe(g);
			case AXE -> axe(g);
			case SHOVEL -> shovel(g);
			case HOE -> hoe(g);
			case BUCKET -> Items.BUCKET;
		};
	}

	/**
	 * A smith's batch ({@link Jobs}): the first tool the stores are short of, forged out of them into them, the best
	 * the realm spends on (see the class note). True if one was made.
	 */
	static boolean forge(ServerLevel level, Faction f, Villager smith) {
		boolean rich = WalledTowns.wealth(level.getServer(), f) == Wealth.Tier.WEALTHY;
		for (Kind kind : Kind.values()) {
			int head = HEADS[kind.ordinal()];
			boolean iron = rich && Storage.count(level, f, Items.IRON_INGOT) >= head;
			if (kind == Kind.BUCKET) {
				if (!iron || Storage.count(level, f, s -> s.is(Items.BUCKET) || s.is(Items.WATER_BUCKET)) >= KEEP_BUCKETS) {
					continue;
				}
			} else {
				Grade g = iron ? Grade.IRON : Grade.STONE;
				// (no stone ones made while iron ones are kept)
				if (Storage.count(level, f, item(kind, g)) >= KEEP || g == Grade.STONE && Storage.count(level, f, item(kind, Grade.IRON)) > 0
					|| !iron && Storage.count(level, f, Items.COBBLESTONE) < head || !handle(level, f)) {
					continue;
				}
			}
			Storage.take(level, f, iron ? Items.IRON_INGOT : Items.COBBLESTONE, head);
			if (kind != Kind.BUCKET) {
				Storage.take(level, f, Items.STICK, HANDLE);
			}
			Item made = item(kind, iron ? Grade.IRON : Grade.STONE);
			if (Storage.put(level, f, made, 1) > 0) {
				// (no room for it: the stuff goes back)
				Storage.put(level, f, iron ? Items.IRON_INGOT : Items.COBBLESTONE, head);
				if (kind != Kind.BUCKET) {
					Storage.put(level, f, Items.STICK, HANDLE);
				}
				return false;
			}
			level.playSound(null, smith.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.5F, 1.1F + level.getRandom().nextFloat() * 0.2F);
			level.sendParticles(ParticleTypes.LAVA, smith.getX(), smith.getY() + 1.0, smith.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
			SEEN.remove(f.id());
			return true;
		}
		return false;
	}

	/** Sticks for a handle in the stores, cutting a plank for them if there aren't. */
	private static boolean handle(ServerLevel level, Faction f) {
		if (Storage.count(level, f, Items.STICK) < HANDLE) {
			ItemStack plank = Storage.take(level, f, s -> s.is(ItemTags.PLANKS), 1);
			if (!plank.isEmpty() && Storage.put(level, f, Items.STICK, STICKS_PER_PLANK) == STICKS_PER_PLANK) {
				Storage.put(level, f, plank);
			}
		}
		return Storage.count(level, f, Items.STICK) >= HANDLE;
	}

	/** Once a day, one of each kind of tool the settlement works with wears out of its stores. */
	private static void wear(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % WEAR_EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat()) || !Storage.loaded(level, f)) {
				continue;
			}
			for (Kind kind : Kind.values()) {
				if (kind == Kind.BUCKET) {
					continue;
				}
				Grade g = stocked(level, f, kind);
				if (g != Grade.WOOD) {
					Storage.take(level, f, item(kind, g), 1);
				}
			}
			SEEN.remove(f.id());
		}
	}

	public static Item shovel(Grade g) {
		return g == Grade.IRON ? Items.IRON_SHOVEL : g == Grade.STONE ? Items.STONE_SHOVEL : Items.WOODEN_SHOVEL;
	}

	public static Item pickaxe(Grade g) {
		return g == Grade.IRON ? Items.IRON_PICKAXE : g == Grade.STONE ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE;
	}

	public static Item axe(Grade g) {
		return g == Grade.IRON ? Items.IRON_AXE : g == Grade.STONE ? Items.STONE_AXE : Items.WOODEN_AXE;
	}

	public static Item hoe(Grade g) {
		return g == Grade.IRON ? Items.IRON_HOE : g == Grade.STONE ? Items.STONE_HOE : Items.WOODEN_HOE;
	}

	public static Item shovel(Kit k) {
		return shovel(k.shovel());
	}

	public static Item pickaxe(Kit k) {
		return pickaxe(k.pickaxe());
	}

	public static Item axe(Kit k) {
		return axe(k.axe());
	}

	public static Item hoe(Kit k) {
		return hoe(k.hoe());
	}

	/** A bucket of water: an iron one if the town has a bucket, else Jaymo's Powers' fired clay one (iron without Powers). */
	public static Item waterBucket(Kit k) {
		if (!k.ironBucket()) {
			Item clay = net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "clay_water_bucket")).orElse(null);
			if (clay != null) {
				return clay;
			}
		}
		return Items.WATER_BUCKET;
	}

	/** {@code a} and {@code b} are the same tool whatever they're made of (a stone shovel and an iron one), or the same item. */
	public static boolean sameKind(ItemStack a, ItemStack b) {
		if (a.is(b.getItem())) {
			return true;
		}
		for (Grade g : Grade.values()) {
			for (Grade h : Grade.values()) {
				if (a.is(shovel(g)) && b.is(shovel(h)) || a.is(pickaxe(g)) && b.is(pickaxe(h)) || a.is(axe(g)) && b.is(axe(h)) || a.is(hoe(g)) && b.is(hoe(h))) {
					return true;
				}
			}
		}
		return false;
	}
}
