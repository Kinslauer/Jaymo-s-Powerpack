package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "all item materials should change dependent on the availability and realm wealth. only the richer kingdoms spend
 * materials on iron shovels, otherwise they are wooden or stone". What a settlement's working folk hold to work with is
 * made of what it can afford and has to hand ({@link #of}):
 * <ul>
 * <li><b>iron</b> only in a wealthy realm ({@link Wealth.Tier#WEALTHY}, a fiefdom by its overlord's) whose storehouse
 * holds iron ingots;</li>
 * <li><b>stone</b> where there is stone to hand (cobblestone in the storehouse) or the realm is at least prosperous;</li>
 * <li><b>wood</b> otherwise.</li>
 * </ul>
 * Each settlement's grade is worked out once every {@link #FRESH} ticks, so a town that grows rich or runs out of iron
 * changes its tools as its folk next take them up.
 */
public final class ToolGrades {
	public enum Grade { WOOD, STONE, IRON }

	/** How long a settlement's grade holds before it's worked out again (ticks). */
	static final long FRESH = 1200L;

	private record Seen(Grade grade, long at) {
	}

	private static final Map<Integer, Seen> SEEN = new HashMap<>();

	private ToolGrades() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEEN.clear());
	}

	/** The grade of tools {@code mob} works with, by its settlement {@code factionId}; stone off the server or with no settlement. */
	public static Grade of(Entity mob, int factionId) {
		if (!(mob.level() instanceof ServerLevel level) || factionId < 0) {
			return Grade.STONE;
		}
		Faction f = Factions.get(level.getServer(), factionId);
		return f == null ? Grade.STONE : of(level, f);
	}

	/** The grade of tools {@code f}'s working folk have (see the class note). */
	public static Grade of(ServerLevel level, @Nullable Faction f) {
		if (f == null || f.fallen()) {
			return Grade.STONE;
		}
		long now = level.getGameTime();
		Seen seen = SEEN.get(f.id());
		if (seen != null && now - seen.at() < FRESH && now >= seen.at()) {
			return seen.grade();
		}
		ServerLevel home = Factions.level(level.getServer());
		Wealth.Tier tier = WalledTowns.wealth(level.getServer(), f);
		Grade grade;
		if (tier == Wealth.Tier.WEALTHY && Storage.count(home, f, Items.IRON_INGOT) > 0) {
			grade = Grade.IRON;
		} else if (tier != Wealth.Tier.POOR || Storage.count(home, f, Items.COBBLESTONE) > 0) {
			grade = Grade.STONE;
		} else {
			grade = Grade.WOOD;
		}
		SEEN.put(f.id(), new Seen(grade, now));
		return grade;
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

	/** A bucket of water: an iron one in a rich town, else Jaymo's Powers' fired clay one (iron without Powers). */
	public static Item waterBucket(Grade g) {
		if (g != Grade.IRON) {
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
