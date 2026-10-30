package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.383, Andy's SevTech picks: "Off-World Orders") The goods the far worlds give and the towns ask for: the first metals,
 * the world parts (Rustfall plate, Glacia ice glass, Cinder bell alloy), Cinder Ember and Glacia salt. Each names its world.
 * The merchants' sheet ({@link MerchantOrders}) and the board's notices ({@link TownNotices}) draw on this list. A good whose
 * item isn't in this pack is never asked for (the first metals are Jaymo's Powers' to add: each is looked for under
 * a few ids).
 */
final class OffWorldGoods {
	/** The tier a player needs open before towns ask them for far-world goods (Dread: a pod flown and landed home). */
	static final WorldTiers.Tier MIN_TIER = WorldTiers.Tier.DREAD;
	/** How many goods a merchants' sheet wants on a day. */
	static final int WANTS_A_DAY = 2;

	/** A good: its key, the world it comes from (a lang key suffix), the item ids to try, how many a notice or sheet asks, and what it pays. */
	record Good(String key, String world, List<String> ids, int min, int max, int emeralds) {
		@Nullable Item item() {
			for (String id : this.ids) {
				Identifier key = Identifier.tryParse(id);
				if (key != null) {
					Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
					if (item != Items.AIR) {
						return item;
					}
				}
			}
			return null;
		}

		String itemId() {
			Item item = item();
			return item == null ? this.ids.getFirst() : BuiltInRegistries.ITEM.getKey(item).toString();
		}
	}

	private static Good metal(String key, String world) {
		return new Good(key, world, List.of("jaymospowers:" + key, "jaymospowers:" + key + "_ingot", "jaymostinkering:" + key), 2, 6, 14);
	}

	static final List<Good> ALL = List.of(
		metal("pale_alloy", "moon"),
		metal("rust_iron", "rustfall"),
		metal("ice_iron", "glacia"),
		metal("cinder_bronze", "cinder"),
		metal("void_shard", "maw"),
		metal("amber_bronze", "verdant"),
		new Good("rustfall_plate", "rustfall", List.of("jaymospowers:rustfall_plate"), 1, 3, 20),
		new Good("glacia_ice_glass", "glacia", List.of("jaymospowers:glacia_ice_glass"), 1, 3, 20),
		new Good("cinder_bell_alloy", "cinder", List.of("jaymospowers:cinder_bell_alloy"), 1, 3, 20),
		new Good("cinder_ember", "cinder", List.of("jaymospowers:cinder_ember"), 2, 5, 12),
		new Good("glacia_salt", "glacia", List.of("jaymoskitchen:salt"), 8, 16, 10));

	private OffWorldGoods() {
	}

	/** The good with this key whose item exists, or null. */
	static @Nullable Good byKey(String key) {
		for (Good g : ALL) {
			if (g.key().equals(key) && g.item() != null) {
				return g;
			}
		}
		return null;
	}

	/** Every good that can be asked for here. */
	static List<Good> available() {
		List<Good> out = new ArrayList<>();
		for (Good g : ALL) {
			if (g.item() != null) {
				out.add(g);
			}
		}
		return out;
	}

	/** The goods the board at {@code anchor} wants on day {@code day} (the same all day, the same for everyone). */
	static List<Good> today(BlockPos anchor, long day) {
		List<Good> pool = available();
		List<Good> out = new ArrayList<>();
		Random random = new Random(anchor.asLong() * 31L + day * 0x9E3779B97F4A7C15L);
		while (out.size() < WANTS_A_DAY && !pool.isEmpty()) {
			out.add(pool.remove(random.nextInt(pool.size())));
		}
		return out;
	}

	/** Whether {@code player} has the tier towns ask this of. */
	static boolean asks(ServerPlayer player) {
		return WorldTiers.open(player, MIN_TIER);
	}

	/** Whether any player on is far enough along for a board to carry such a notice. */
	static boolean anyoneAsked(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (asks(p)) {
				return true;
			}
		}
		return false;
	}
}
