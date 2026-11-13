package com.jaymospowers.baubles;

import static net.minecraft.world.level.storage.loot.BuiltInLootTables.*;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.artifacts.ArtifactItem;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Two of the wondrous things (the others are in Jaymo's Kingdoms) that are worn: the boots of long strides (the feet slot:
 * a quicker stride and a step up a block and a half without a jump) and the cloak of the depths (the body slot: more
 * breath, and while it is on you can breathe under water). Like every strong bauble they work only once bonded, and show
 * their powers as unknown until used. They are found, never crafted: the boots in dry old ruins and tombs, the cloak in
 * wrecks and the sea's treasure. Any found bauble may carry a curse that shows only when bonded ({@link #dress}).
 */
public final class Wondrous {
	/** The boots' quickness (a share of walking speed) and the extra step height (blocks). */
	static final double STRIDE_SPEED = 0.14, STRIDE_STEP = 0.6;
	/** The cloak's extra breath (a multiple of the usual bonus), and how long the water-breathing it gives is renewed (ticks). */
	static final double DEPTHS_BREATH = 6.0;
	static final int DEPTHS_RENEW = 60;
	/** The chance a found bauble carries a curse, with Jaymo's Kingdoms there to act on it. */
	static final float CURSE_CHANCE = 0.08F;
	/** The curses Jaymo's Kingdoms knows how to act on. */
	static final List<String> CURSES = List.of("clinging", "thirst", "whispers", "misfortune");

	public static Item BOOTS_OF_LONG_STRIDES, CLOAK_OF_THE_DEPTHS;

	private static final Map<ResourceKey<LootTable>, Float> BOOT_HOMES = Map.of(DESERT_PYRAMID, 0.10F, JUNGLE_TEMPLE, 0.12F, ABANDONED_MINESHAFT, 0.04F,
		STRONGHOLD_CORRIDOR, 0.06F, STRONGHOLD_CROSSING, 0.06F, SIMPLE_DUNGEON, 0.07F, RUINED_PORTAL, 0.05F, PILLAGER_OUTPOST, 0.08F);
	private static final Map<ResourceKey<LootTable>, Float> CLOAK_HOMES = Map.of(SHIPWRECK_TREASURE, 0.12F, BURIED_TREASURE, 0.16F, UNDERWATER_RUIN_BIG, 0.08F,
		UNDERWATER_RUIN_SMALL, 0.04F, OCEAN_RUIN_COLD_ARCHAEOLOGY, 0.04F, OCEAN_RUIN_WARM_ARCHAEOLOGY, 0.04F, TRIAL_CHAMBERS_REWARD_RARE, 0.06F);

	private Wondrous() {
	}

	static void init() {
		BOOTS_OF_LONG_STRIDES = make("boots_of_long_strides", Baubles.Kind.FEET, Rarity.RARE,
			new BaubleItem.Bonus(Attributes.MOVEMENT_SPEED, STRIDE_SPEED, Operation.ADD_MULTIPLIED_BASE),
			new BaubleItem.Bonus(Attributes.STEP_HEIGHT, STRIDE_STEP, Operation.ADD_VALUE));
		CLOAK_OF_THE_DEPTHS = make("cloak_of_the_depths", Baubles.Kind.BODY, Rarity.RARE,
			new BaubleItem.Bonus(Attributes.OXYGEN_BONUS, DEPTHS_BREATH, Operation.ADD_VALUE));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isUnderWater() && Baubles.wears(player, CLOAK_OF_THE_DEPTHS)) {
					player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, DEPTHS_RENEW, 0, true, false, false));
				}
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null) {
				return;
			}
			RandomSource random = context.getRandom();
			Float boots = BOOT_HOMES.get(key), cloak = CLOAK_HOMES.get(key);
			if (boots != null && random.nextFloat() < boots) {
				drops.add(dress(new ItemStack(BOOTS_OF_LONG_STRIDES), random));
			}
			if (cloak != null && random.nextFloat() < cloak) {
				drops.add(dress(new ItemStack(CLOAK_OF_THE_DEPTHS), random));
			}
		});
	}

	private static Item make(String id, Baubles.Kind kind, Rarity rarity, BaubleItem.Bonus... bonuses) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key,
			new ArtifactItem(new Item.Properties().setId(key).stacksTo(1).rarity(rarity), id, kind, new Baubles.Kind[0], List.of(bonuses)));
		for (BaubleItem.Bonus bonus : bonuses) {
			Baubles.touch(bonus.attribute());
		}
		Baubles.add((BaubleItem) item);
		return item;
	}

	/** A found bauble, maybe with a curse that shows only when it is bonded (only when Jaymo's Kingdoms can act on it). */
	public static ItemStack dress(ItemStack stack, RandomSource random) {
		if (random.nextFloat() < CURSE_CHANCE && FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			String curse = CURSES.get(random.nextInt(CURSES.size()));
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(Bonds.CURSE, curse));
		}
		return stack;
	}
}
