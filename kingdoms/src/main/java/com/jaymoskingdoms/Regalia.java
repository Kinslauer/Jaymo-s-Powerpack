package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/**
 * (0.7.171) Andy (2026-10-22): rulers "should all have some random variable of different crowns, and or some kind of
 * royal attire or armor": he found a villager queen who looked like anyone else.
 * <p>
 * A ruler wears one of several crowns ({@link #CROWNS}, drawn by {@code tools/gen_regalia.py}), picked by who they are
 * so it stays theirs: a king or queen a crown (a queen may wear a tiara), a lord or lady a circlet or coronet. With
 * royal clothes from MCA's clothing library ({@link ClothingLibrary}) the crown is all they add; without, they wear one
 * of {@link #ATTIRE} in their realm's colours. Each piece is marked as regalia ({@link #MARK}), never drops, and comes off
 * with the throne. Crowns are cosmetic headwear (no armour), in the creative Combat tab.
 */
public final class Regalia {
	/** The crowns, by name: what each rank may wear (K king, Q queen, L lord, A lady). */
	static final Map<String, String> CROWNS = new LinkedHashMap<>();
	static {
		CROWNS.put("royal_crown", "KQ");
		CROWNS.put("jeweled_crown", "KQ");
		CROWNS.put("iron_crown", "K");
		CROWNS.put("laurel_crown", "KQL");
		CROWNS.put("tiara", "QA");
		CROWNS.put("circlet", "LA");
		CROWNS.put("coronet", "LA");
	}
	/** The attire a ruler without royal clothes wears: gilded plate, a robe of their colour, trimmed diamond. */
	static final int GILDED = 0, ROBED = 1, DIAMOND = 2, ATTIRE = 3;
	/** Royal colours for robes (one realm in two wears its own instead). */
	static final List<DyeColor> ROYAL_COLOURS = List.of(DyeColor.PURPLE, DyeColor.RED, DyeColor.BLUE, DyeColor.MAGENTA);
	/** On every piece of a ruler's regalia (custom data). */
	static final String MARK = "jaymoskingdoms_regalia";

	static final Map<String, Item> ITEMS = new LinkedHashMap<>();

	private Regalia() {
	}

	static void register() {
		List<Item> all = new ArrayList<>();
		for (String name : CROWNS.keySet()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
			Equippable worn = Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_GOLD)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(name))).build();
			Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)
				.component(DataComponents.EQUIPPABLE, worn)));
			ITEMS.put(name, item);
			all.add(item);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.GOLDEN_HELMET;
			for (Item item : all) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	/** The rank letter for {@link #CROWNS}. */
	private static char rank(int role, boolean female) {
		return role == Folk.LORD ? (female ? 'A' : 'L') : (female ? 'Q' : 'K');
	}

	/**
	 * {@code mob} is dressed as {@code role} (a king, a lord) of {@code faction}: a crown, and attire unless MCA's library
	 * dresses them ({@code robed}). The same pieces each time for the same ruler and rank.
	 */
	static void dress(PathfinderMob mob, int role, Faction faction, boolean female, boolean robed) {
		RandomSource random = RandomSource.create(mob.getUUID().getLeastSignificantBits() ^ role * 0x9E3779B97F4A7C15L);
		char rank = rank(role, female);
		List<Item> crowns = CROWNS.entrySet().stream().filter(e -> e.getValue().indexOf(rank) >= 0).map(e -> ITEMS.get(e.getKey())).toList();
		Outfits.Heraldry arms = Outfits.heraldry(faction, mob.level().getServer());
		ResourceKey<TrimMaterial> gem = arms.material() == TrimMaterials.GOLD ? TrimMaterials.DIAMOND : arms.material();
		put(mob, EquipmentSlot.HEAD, glint(new ItemStack(crowns.get(random.nextInt(crowns.size())))));
		int attire = random.nextInt(ATTIRE);
		DyeColor robe = random.nextBoolean() ? arms.field() : ROYAL_COLOURS.get(random.nextInt(ROYAL_COLOURS.size()));
		if (robed) {
			// (their clothes are royal already: just the boots, so the hem isn't hidden)
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
				if (marked(mob.getItemBySlot(slot))) {
					mob.setItemSlot(slot, ItemStack.EMPTY);
				}
			}
			put(mob, EquipmentSlot.FEET, trimmed(mob, new ItemStack(Items.GOLDEN_BOOTS), arms.pattern(), gem));
			return;
		}
		switch (attire) {
			case GILDED -> {
				put(mob, EquipmentSlot.CHEST, trimmed(mob, new ItemStack(Items.NETHERITE_CHESTPLATE), arms.pattern(), TrimMaterials.GOLD));
				put(mob, EquipmentSlot.LEGS, trimmed(mob, new ItemStack(Items.NETHERITE_LEGGINGS), arms.pattern(), TrimMaterials.GOLD));
				put(mob, EquipmentSlot.FEET, trimmed(mob, new ItemStack(Items.GOLDEN_BOOTS), arms.pattern(), gem));
			}
			case ROBED -> {
				put(mob, EquipmentSlot.CHEST, trimmed(mob, Outfits.dyed(Items.LEATHER_CHESTPLATE, Outfits.rgb(robe)), arms.pattern(), TrimMaterials.GOLD));
				put(mob, EquipmentSlot.LEGS, trimmed(mob, Outfits.dyed(Items.LEATHER_LEGGINGS, Outfits.rgb(robe)), arms.pattern(), TrimMaterials.GOLD));
				put(mob, EquipmentSlot.FEET, trimmed(mob, new ItemStack(Items.GOLDEN_BOOTS), arms.pattern(), gem));
			}
			default -> {
				put(mob, EquipmentSlot.CHEST, trimmed(mob, new ItemStack(Items.DIAMOND_CHESTPLATE), arms.pattern(), TrimMaterials.GOLD));
				put(mob, EquipmentSlot.LEGS, trimmed(mob, Outfits.dyed(Items.LEATHER_LEGGINGS, Outfits.rgb(robe)), arms.pattern(), TrimMaterials.GOLD));
				put(mob, EquipmentSlot.FEET, trimmed(mob, new ItemStack(Items.IRON_BOOTS), arms.pattern(), gem));
			}
		}
	}

	/** {@code mob} has their crown on. */
	static boolean crowned(PathfinderMob mob) {
		return marked(mob.getItemBySlot(EquipmentSlot.HEAD));
	}

	/** Off the throne: every piece of regalia comes off (and the gold helmet older crowns were). */
	static void undress(PathfinderMob mob) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack worn = mob.getItemBySlot(slot);
			if (marked(worn) || slot == EquipmentSlot.HEAD && worn.is(Items.GOLDEN_HELMET)) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	static boolean marked(ItemStack stack) {
		return !stack.isEmpty() && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(MARK, false);
	}

	private static void put(PathfinderMob mob, EquipmentSlot slot, ItemStack stack) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0.0F);
	}

	private static ItemStack glint(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static ItemStack trimmed(PathfinderMob mob, ItemStack stack, ResourceKey<TrimPattern> pattern, ResourceKey<TrimMaterial> material) {
		Optional<? extends Holder<TrimPattern>> p = mob.level().registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(pattern);
		Optional<? extends Holder<TrimMaterial>> m = mob.level().registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(material);
		if (p.isPresent() && m.isPresent()) {
			stack.set(DataComponents.TRIM, new ArmorTrim(m.get(), p.get()));
		}
		return stack;
	}
}
