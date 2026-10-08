package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * What Reach Through can pull out: every weapon of Balkon's WeaponMod: Legacy (mod id {@code weaponmod}, found in the
 * item registry at runtime, no compile dependency) plus a handful of vanilla weapons. Built lazily on first use, after
 * the registries are frozen. Without the weapon mod installed the pool is simply vanilla-only.
 */
public final class ArsenalPool {
	public static final String WEAPON_MOD = "weaponmod";

	/** Ids containing any of these are ammo, parts or props, not weapons. */
	private static final String[] NOT_WEAPONS = {"bullet", "bolt", "dart", "shot", "shell", "cannonball", "ironpart", "gun-stock",
		"gun_stock", "gunstock", "dummy", "cannon", "mortar", "firerod"};
	/** Ids containing these make good Barrage projectiles. */
	private static final String[] BLADES = {"spear", "javelin", "knife", "katana", "halberd", "battleaxe", "warhammer", "boomerang", "flail"};

	/** Ammo handed out with a pulled launcher: which launcher (id keywords), which ammo (id keyword), how many. */
	private record AmmoRule(String[] launchers, String ammo, int count) {
		boolean matches(String path) {
			for (String l : launchers) {
				if (path.contains(l)) {
					return true;
				}
			}
			return false;
		}
	}

	private static final List<AmmoRule> AMMO_RULES = List.of(
		new AmmoRule(new String[] {"musket", "flintlock"}, "bullet", 8),
		new AmmoRule(new String[] {"blunderbuss"}, "shot", 8),
		new AmmoRule(new String[] {"blowgun", "blow_gun", "blowpipe"}, "dart", 16),
		new AmmoRule(new String[] {"crossbow"}, "bolt", 16));

	private enum Kind { PLAIN, BOW, CROSSBOW, TRIDENT }

	/** One thing that can be pulled: the weapon, how often (weight), how many (stackables like dynamite), its ammo. */
	private record Entry(Item weapon, int weight, int count, List<Item> ammo, int ammoCount, Kind kind) {
	}

	private static @Nullable List<Entry> pool;
	private static int totalWeight;
	private static @Nullable List<Item> blades;

	private ArsenalPool() {
	}

	private static synchronized void build() {
		if (pool != null) {
			return;
		}
		List<Entry> entries = new ArrayList<>();
		List<Item> bladeItems = new ArrayList<>();

		// vanilla weapons (weights are out of ~10 for a common pick)
		vanilla(entries, Items.IRON_SWORD, 80, Kind.PLAIN);
		vanilla(entries, Items.IRON_AXE, 60, Kind.PLAIN);
		vanilla(entries, Items.IRON_SPEAR, 40, Kind.PLAIN);
		vanilla(entries, Items.DIAMOND_SWORD, 20, Kind.PLAIN);
		vanilla(entries, Items.DIAMOND_AXE, 15, Kind.PLAIN);
		vanilla(entries, Items.DIAMOND_SPEAR, 12, Kind.PLAIN);
		vanilla(entries, Items.NETHERITE_SWORD, 5, Kind.PLAIN);
		vanilla(entries, Items.NETHERITE_AXE, 4, Kind.PLAIN);
		vanilla(entries, Items.NETHERITE_SPEAR, 3, Kind.PLAIN);
		vanilla(entries, Items.MACE, 8, Kind.PLAIN);
		vanilla(entries, Items.TRIDENT, 20, Kind.TRIDENT);
		vanilla(entries, Items.BOW, 45, Kind.BOW);
		vanilla(entries, Items.CROSSBOW, 35, Kind.CROSSBOW);
		bladeItems.addAll(List.of(Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.IRON_AXE, Items.IRON_SPEAR, Items.DIAMOND_SPEAR, Items.TRIDENT));

		// Balkon's WeaponMod: Legacy, whatever its ids look like
		List<Identifier> ammoIds = new ArrayList<>();
		List<Identifier> weaponIds = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!WEAPON_MOD.equals(id.getNamespace())) {
				continue;
			}
			String path = id.getPath().toLowerCase(Locale.ROOT);
			if (containsAny(path, NOT_WEAPONS)) {
				ammoIds.add(id);
			} else {
				weaponIds.add(id);
			}
		}
		weaponIds.sort(null);
		for (Identifier id : weaponIds) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item == null || item == Items.AIR) {
				continue;
			}
			String path = id.getPath().toLowerCase(Locale.ROOT);
			List<Item> ammo = List.of();
			int ammoCount = 0;
			for (AmmoRule rule : AMMO_RULES) {
				if (rule.matches(path)) {
					ammo = ammoIds.stream()
						.filter(a -> a.getPath().toLowerCase(Locale.ROOT).contains(rule.ammo))
						.map(BuiltInRegistries.ITEM::getValue)
						.filter(a -> a != null && a != Items.AIR)
						.toList();
					ammoCount = rule.count;
					break;
				}
			}
			int maxStack = Math.max(1, item.getDefaultMaxStackSize());
			int count = maxStack > 1 ? Math.min(4, maxStack) : 1; // dynamite, javelins...
			entries.add(new Entry(item, tierWeight(path), count, ammo, ammoCount, Kind.PLAIN));
			if (containsAny(path, BLADES)) {
				bladeItems.add(item);
			}
		}
		JaymosPowers.LOGGER.info("Pocket Arsenal: {} pullable weapons ({} from {}), {} {} ammo items",
			entries.size(), weaponIds.size(), WEAPON_MOD, ammoIds.size(), WEAPON_MOD);

		int total = 0;
		for (Entry e : entries) {
			total += e.weight;
		}
		totalWeight = total;
		blades = List.copyOf(bladeItems);
		pool = List.copyOf(entries);
	}

	private static void vanilla(List<Entry> entries, Item item, int weight, Kind kind) {
		entries.add(new Entry(item, weight, 1, List.of(), 0, kind));
	}

	private static boolean containsAny(String path, String[] words) {
		for (String w : words) {
			if (path.contains(w)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Balkon's ids are {@code <weapon>.<tier>} (e.g. {@code katana.iron}) or untiered ({@code musket}); matched by keyword
	 * so renames survive. Iron and copper are common, wood/stone/gold uncommon, diamond rare, netherite very rare; the
	 * untiered specials are common, so guns turn up often.
	 */
	private static int tierWeight(String path) {
		if (path.contains("netherite")) {
			return 3;
		}
		if (path.contains("diamond")) {
			return 12;
		}
		if (path.contains("gold")) {
			return 30;
		}
		if (path.contains("iron") || path.contains("copper")) {
			return 70;
		}
		if (path.contains("wood") || path.contains("stone")) {
			return 35;
		}
		if (path.contains("musket") && !path.contains("bayonet")) {
			return 60; // the big gun is a lucky pull
		}
		return 90; // the untiered specials: guns, blowgun, crossbow, javelins, dynamite
	}

	/**
	 * Rolls a pull: the weapon first, then any ammo. Every stack is stamped with {@code summoned} by the caller.
	 * Vanilla bows get Infinity and one arrow, crossbows a few arrows, tridents Loyalty (so they always come back).
	 */
	public static List<ItemStack> roll(ServerLevel level, RandomSource random) {
		build();
		List<ItemStack> out = new ArrayList<>();
		if (pool.isEmpty() || totalWeight <= 0) {
			out.add(new ItemStack(Items.IRON_SWORD));
			return out;
		}
		int r = random.nextInt(totalWeight);
		Entry pick = pool.getLast();
		for (Entry e : pool) {
			r -= e.weight;
			if (r < 0) {
				pick = e;
				break;
			}
		}
		ItemStack weapon = new ItemStack(pick.weapon, pick.count);
		switch (pick.kind) {
			case BOW -> {
				enchant(level, weapon, Enchantments.INFINITY);
				out.add(weapon);
				out.add(new ItemStack(Items.ARROW, 1));
				return out;
			}
			case CROSSBOW -> {
				out.add(weapon);
				out.add(new ItemStack(Items.ARROW, 12));
				return out;
			}
			case TRIDENT -> enchant(level, weapon, Enchantments.LOYALTY);
			default -> {
			}
		}
		out.add(weapon);
		if (!pick.ammo.isEmpty() && pick.ammoCount > 0) {
			Item ammo = pick.ammo.get(random.nextInt(pick.ammo.size()));
			int count = Math.min(pick.ammoCount, Math.max(1, ammo.getDefaultMaxStackSize()));
			out.add(new ItemStack(ammo, count));
		}
		return out;
	}

	/** A random weapon to dress a Barrage projectile with. */
	public static ItemStack randomBlade(RandomSource random) {
		build();
		List<Item> list = blades;
		return list == null || list.isEmpty() ? new ItemStack(Items.IRON_SWORD) : new ItemStack(list.get(random.nextInt(list.size())));
	}

	private static void enchant(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).ifPresent(holder -> stack.enchant(holder, key == Enchantments.LOYALTY ? 3 : 1));
	}
}
