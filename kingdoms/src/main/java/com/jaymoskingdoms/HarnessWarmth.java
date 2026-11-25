package com.jaymoskingdoms;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What the harness worn over the armour does for body heat (Kitchen's {@code BodyHeat.addWorn}): furs, cloaks, hoods
 * and padding keep the cold off, turbans and wide hats keep the sun off. Before this, a fur hood on a guard of the
 * north or a turban in the desert did nothing for the one wearing it. Counted in Kitchen's warm and cool pieces (one
 * piece is a wool shirt's worth).
 */
public final class HarnessWarmth {
	/** Warm pieces each harness piece counts as, by item id. */
	static final Map<String, Float> WARM = Map.ofEntries(
		Map.entry("fur_hat", 1.0F), Map.entry("fur_hood", 1.0F), Map.entry("fur_jerkin", 1.0F),
		Map.entry("fur_lined_spaulders", 0.5F), Map.entry("cloak", 0.5F), Map.entry("torn_cloak", 0.25F),
		Map.entry("hood", 0.5F), Map.entry("torn_hood", 0.25F), Map.entry("monks_cowl", 0.5F),
		Map.entry("aketon", 0.5F), Map.entry("arming_doublet", 0.5F), Map.entry("arming_cap", 0.25F));
	/** Cool pieces each counts as. */
	static final Map<String, Float> COOL = Map.ofEntries(
		Map.entry("turban", 1.0F), Map.entry("veiled_turban", 1.0F), Map.entry("field_hat", 0.5F));

	private HarnessWarmth() {
	}

	public static double warm(Player player) {
		return count(player, WARM);
	}

	public static double cool(Player player) {
		return count(player, COOL);
	}

	private static double count(Player player, Map<String, Float> table) {
		double n = 0.0;
		for (ItemStack piece : Harness.worn(player)) {
			if (!piece.isEmpty()) {
				var id = BuiltInRegistries.ITEM.getKey(piece.getItem());
				if (JaymosKingdoms.MOD_ID.equals(id.getNamespace())) {
					n += table.getOrDefault(id.getPath(), 0.0F);
				}
			}
		}
		return n;
	}
}
