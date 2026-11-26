package com.jaymoskingdoms;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Clothes for the sun (docs/Climate-Plan.md, track D: cl-desert, cl-hats). Nothing here is armour to speak of: it keeps the heat off.
 * <ul>
 * <li><b>Desert garb</b>: a light robe and a headscarf, each of linen or of rabbit hide. A robe turns a blow by one point and a
 * scarf by none. All count as cool clothing for Kitchen's body heat (the tag {@code jaymoskitchen:cool_clothing}); a robe
 * <i>and</i> a scarf worn together also keep the sun off as shade does ({@link #shaded}, registered with Kitchen's
 * {@code BodyHeat.addShade}): no sun on the body and no metal baking in it, whatever the sky. Tailors (shepherds) sell
 * them in a hot land's villages: a settlement whose land ({@link Climate}) is desert, badlands or savanna.</li>
 * <li><b>Hats</b>: the bamboo hat (a steep cone of split bamboo), the reed hat (plaited sugar cane, a round crown) and the
 * palm-leaf hat (a flat brim of jungle fronds), beside the straw hat of the blossom folk. The game has no reed or palm
 * to make them of, so the recipes use the nearest thing: bamboo, sugar cane (the reed of the game) and jungle leaves (the
 * only palm-like leaf). Farmers and tailors sell all four, in any land. Each is cool clothing.</li>
 * </ul>
 * The hats are worn on the head and drawn there from their own models, as the straw hat is ({@link
 * com.jaymoskingdoms.blossomland.BlossomDress}); the robes and scarves are armour with their own worn textures.
 */
public final class ClimateWear {
	/** Emeralds' worth each sells for (at the villager's counter: see {@link #offers}). */
	static final int HAT_PRICE = 3, SCARF_PRICE = 4, LINEN_ROBE_PRICE = 7, HIDE_ROBE_PRICE = 8, HIDE_SCARF_PRICE = 5;
	/** How many sales an offer holds, the experience it gives, and its price rise per sale. */
	static final int USES = 3, XP = 5;
	static final float PRICE_MULT = 0.05F;
	/** Durability multiplier, defence (boots, leggings, chest, helmet, body): a robe turns a blow by one point, a scarf by none. */
	private static final int DURABILITY = 5, ENCHANT = 12;
	private static final int ROBE_DEFENCE = 1;

	/** What mends the garb at an anvil: linen is repaired with string or wool, hide with rabbit hide. */
	public static final TagKey<Item> LINEN_CLOTH = TagKey.create(Registries.ITEM, JaymosKingdoms.id("linen_cloth"));
	public static final TagKey<Item> HIDE_CLOTH = TagKey.create(Registries.ITEM, JaymosKingdoms.id("hide_cloth"));

	static final ArmorMaterial LINEN = new ArmorMaterial(DURABILITY, ArmorMaterials.makeDefense(0, 0, ROBE_DEFENCE, 0, ROBE_DEFENCE), ENCHANT,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, LINEN_CLOTH, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("desert_linen")));
	static final ArmorMaterial HIDE = new ArmorMaterial(DURABILITY + 2, ArmorMaterials.makeDefense(0, 0, ROBE_DEFENCE, 0, ROBE_DEFENCE), ENCHANT,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, HIDE_CLOTH, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("desert_hide")));

	public static Item LINEN_ROBE, LINEN_HEADSCARF, HIDE_ROBE, HIDE_HEADSCARF, BAMBOO_HAT, REED_HAT, PALM_HAT;

	private ClimateWear() {
	}

	static void register() {
		LINEN_ROBE = piece("linen_robe", LINEN, ArmorType.CHESTPLATE);
		LINEN_HEADSCARF = piece("linen_headscarf", LINEN, ArmorType.HELMET);
		HIDE_ROBE = piece("rabbit_hide_robe", HIDE, ArmorType.CHESTPLATE);
		HIDE_HEADSCARF = piece("rabbit_hide_headscarf", HIDE, ArmorType.HELMET);
		BAMBOO_HAT = hat("bamboo_hat");
		REED_HAT = hat("reed_hat");
		PALM_HAT = hat("palm_leaf_hat");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			for (Item item : List.of(LINEN_ROBE, LINEN_HEADSCARF, HIDE_ROBE, HIDE_HEADSCARF, BAMBOO_HAT, REED_HAT, PALM_HAT, LeafCloak.CLOAK)) {
				output.accept(item);
			}
		});
	}

	private static Item piece(String id, ArmorMaterial material, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(material, type)));
	}

	/** A hat worn on the head and drawn from its own item model (no worn texture), as the straw hat is. */
	private static Item hat(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).component(DataComponents.EQUIPPABLE,
			Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build())));
	}

	// ---- keeping the sun off ---------------------------------------------------------------------------------------------

	/** Wearing a desert robe and a desert headscarf (of either cloth): the sun doesn't reach the body. */
	public static boolean shaded(Player player) {
		return LINEN_ROBE != null && isRobe(player.getItemBySlot(EquipmentSlot.CHEST)) && isScarf(player.getItemBySlot(EquipmentSlot.HEAD));
	}

	private static boolean isRobe(ItemStack stack) {
		return stack.is(LINEN_ROBE) || stack.is(HIDE_ROBE);
	}

	private static boolean isScarf(ItemStack stack) {
		return stack.is(LINEN_HEADSCARF) || stack.is(HIDE_HEADSCARF);
	}

	/** With Kitchen: a robe and a scarf together count as shade for its body heat (found by reflection, as the rest are). */
	static void kitchenHooks() {
		try {
			Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addShade", Predicate.class)
				.invoke(null, (Predicate<Player>)ClimateWear::shaded);
		} catch (ReflectiveOperationException e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for desert garb to keep the sun off: {}", e.toString());
		}
	}

	// ---- shops -----------------------------------------------------------------------------------------------------------

	/**
	 * (from {@code VillagerPriceMixin}, when a player opens a trade with {@code v}) farmers and tailors lay out the hats; a tailor in
	 * a hot land's village the robes and headscarves as well.
	 */
	public static void offers(Villager v, Player player) {
		if (!(v.level() instanceof ServerLevel level) || v.isBaby() || LINEN_ROBE == null) {
			return;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		boolean tailor = job.equals("shepherd");
		if (!tailor && !job.equals("farmer")) {
			return;
		}
		MerchantOffers offers = v.getOffers();
		offer(offers, new ItemStack(com.jaymoskingdoms.blossomland.BlossomDress.STRAW_HAT), HAT_PRICE);
		offer(offers, new ItemStack(BAMBOO_HAT), HAT_PRICE);
		offer(offers, new ItemStack(REED_HAT), HAT_PRICE);
		offer(offers, new ItemStack(PALM_HAT), HAT_PRICE);
		if (tailor) {
			Faction f = Quests.settlement(level, v);
			if (hot(level, f != null ? f.seat() : v.blockPosition())) {
				offer(offers, new ItemStack(LINEN_ROBE), LINEN_ROBE_PRICE);
				offer(offers, new ItemStack(LINEN_HEADSCARF), SCARF_PRICE);
				offer(offers, new ItemStack(HIDE_ROBE), HIDE_ROBE_PRICE);
				offer(offers, new ItemStack(HIDE_HEADSCARF), HIDE_SCARF_PRICE);
			}
		}
	}

	/** A hot land: the village's land is desert, badlands or savanna ({@link Climate}, the reading its buildings and people dress by). */
	static boolean hot(ServerLevel level, BlockPos at) {
		Climate land = Climate.at(level, at);
		return land.arid() || land == Climate.SAVANNA;
	}

	private static void offer(MerchantOffers offers, ItemStack stack, int price) {
		Item item = stack.getItem();
		if (!stack.isEmpty() && offers.stream().noneMatch(o -> o.getResult().is(item))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), stack, USES, XP, PRICE_MULT));
		}
	}
}
