package com.jaymoskingdoms.mineshafts;

import com.jaymoskingdoms.Currencies;
import com.jaymoskingdoms.Currency;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Relics;
import com.jaymoskingdoms.Spoils;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * (medieval picks, mm-loot) Our goods in the shafts' chests and barrels (Moog's Mineshafts Reimagined's miner chests,
 * supply barrels and treasure, and its shared common, uncommon and rare tables): a few of our coins (copper bits, now and
 * then the realm's silver), ore pebbles (Jaymo's Powers' crushed ore, raw ore without it), a smith's tools (Powers'
 * tongs and smithing hammer), a pattern scroll (a Powers blueprint of a common shape) and, rarely, an old relic
 * ({@code Relics}). A treasure chest is likelier to hold the last two.
 */
public final class ShaftLoot {
	// ---- tuning ----
	/** The chance of each, in a miner's chest or supply barrel. */
	static final float COINS = 0.5F, SILVER = 0.12F, PEBBLES = 0.45F, TOOLS = 0.07F, PATTERN = 0.05F, RELIC = 0.02F;
	/** The chance of a pattern and of a relic in a treasure chest. */
	static final float TREASURE_PATTERN = 0.25F, TREASURE_RELIC = 0.12F;
	static final int BITS_MIN = 2, BITS_MAX = 9, SILVER_MAX = 3, PEBBLES_MIN = 1, PEBBLES_MAX = 4;
	/** The ore pebbles (Powers' crushed ores), the smith's tools, and the pattern scrolls' shapes. */
	static final List<String> ORES = List.of("crushed_iron_ore", "crushed_copper_ore", "crushed_tin_ore", "crushed_silver_ore", "crushed_gold_ore");
	static final List<String> TOOLS_IDS = List.of("iron_tongs", "copper_smithing_hammer");
	static final List<String> SHAPES = List.of("weapon/prospector_pick_head", "weapon/spear_tip", "weapon/saw_blade", "weapon/tongs_jaws",
		"weapon/pitchfork_tines", "weapon/kitchen_knife_blade");

	private ShaftLoot() {
	}

	static void register() {
		if (!Mineshafts.LOADED) {
			return;
		}
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null || !key.identifier().getNamespace().equals(Mineshafts.MOD) || !key.identifier().getPath().contains("mineshaft")) {
				return;
			}
			String path = key.identifier().getPath();
			String name = path.substring(path.lastIndexOf('/') + 1);
			boolean treasure = name.startsWith("treasure") || name.equals("rare");
			if (!treasure && !name.startsWith("miner_chest") && !name.startsWith("supply_barrel") && !name.equals("common") && !name.equals("uncommon")) {
				return;
			}
			RandomSource random = context.getRandom();
			if (random.nextFloat() < COINS) {
				drops.add(new ItemStack(Currency.BIT.item(), BITS_MIN + random.nextInt(BITS_MAX - BITS_MIN + 1)));
			}
			if (random.nextFloat() < SILVER && Currencies.REALM_SILVER != null) {
				drops.add(new ItemStack(Currencies.REALM_SILVER, 1 + random.nextInt(SILVER_MAX)));
			}
			if (random.nextFloat() < PEBBLES) {
				ItemStack ore = Spoils.powers(ORES.get(random.nextInt(ORES.size())));
				Item item = ore.isEmpty() ? (random.nextBoolean() ? Items.RAW_IRON : Items.RAW_COPPER) : ore.getItem();
				drops.add(new ItemStack(item, PEBBLES_MIN + random.nextInt(PEBBLES_MAX - PEBBLES_MIN + 1)));
			}
			if (random.nextFloat() < TOOLS) {
				ItemStack tool = Spoils.powers(TOOLS_IDS.get(random.nextInt(TOOLS_IDS.size())));
				if (!tool.isEmpty()) {
					drops.add(tool);
				}
			}
			if (random.nextFloat() < (treasure ? TREASURE_PATTERN : PATTERN)) {
				ItemStack pattern = PowersCompat.blueprint(SHAPES.get(random.nextInt(SHAPES.size())));
				if (!pattern.isEmpty()) {
					drops.add(pattern);
				}
			}
			if (random.nextFloat() < (treasure ? TREASURE_RELIC : RELIC)) {
				ItemStack relic = Relics.found(context.getLevel(), random);
				if (!relic.isEmpty()) {
					drops.add(relic);
				}
			}
		});
	}
}
