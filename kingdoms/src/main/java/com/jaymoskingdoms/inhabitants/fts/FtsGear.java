package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The gear of From the Shadows Reborn 3.0 (Sonmok14 and L_Ender; AGPL v3; credit and file list in
 * licenses/from-the-shadows/CREDITS-gear.md; the code is our own): diabolium, the red metal of the demons' dens, and the
 * armour of it, and the crust armour made of a Bulldrogioth's crimson shell. Both are worn as the original's 3D models
 * ({@link GearArmour}). Tiers and families: both sets are heavy armour (tag {@code heavy_armor}) and belong to the dread tier.
 * <p>The sets' bonuses, as the original has them: the full diabolium set (all four pieces) makes the wearer's blows tear
 * (Powers' wounds open a cut for a "tearing" attacker, see {@code LivingEntityGearMixin}) and gives back a sixth of the damage
 * they do as health; the full crust set (helmet, chestplate, leggings) while crouching halves the damage taken, at the price
 * of ten durability on each piece per blow ({@link #crouched}).
 */
public final class FtsGear {
	public static Block DIABOLIUM_BLOCK;
	public static Item DIABOLIUM_BLOCK_ITEM, DIABOLIUM_NUGGET, DIABOLIUM_INGOT;
	public static GearArmour DIABOLIUM_HELMET, DIABOLIUM_CHEST, DIABOLIUM_LEGGINGS, DIABOLIUM_BOOTS;
	public static GearArmour CRUST_HELMET, CRUST_CHEST, CRUST_LEGGINGS;
	/** What the Nehemoth drops and what the dagger and the bottle are made of. */
	public static Item CRYSTALLIZED_BLOOD, CRYSTALLIZED_FRAGMENT, BOTTLE_OF_BLOOD, CORRUPTED_HEART;

	/**
	 * The original's numbers: a piece's durability is the slot's vanilla factor times 24 (diabolium; iron is 15, netherite 37)
	 * or 30 (crust). Armour points helmet/chest/leggings/boots: diabolium 3/8/6/3, crust 4/9/7. The original's toughness
	 * (diabolium 3, crust 6 a piece) and knockback resistance (0.2 and 0.4 a piece) are brought down to netherite's
	 * (3 and 0.1 a piece), 2 and 3 toughness here, since the crust's 6 on three pieces and its 120% knockback resistance
	 * would make the set untouchable.
	 */
	static final int DIABOLIUM_DURABILITY = 24, CRUST_DURABILITY = 30;
	static final int[] DIABOLIUM_ARMOR = {3, 8, 6, 3};
	static final int[] CRUST_ARMOR = {4, 9, 7};
	static final float DIABOLIUM_TOUGHNESS = 2.0F, CRUST_TOUGHNESS = 3.0F, KNOCKBACK_RESISTANCE = 0.1F;
	static final int DIABOLIUM_ENCHANTABILITY = 18, CRUST_ENCHANTABILITY = 12;
	/** The vanilla durability factors: helmet, chestplate, leggings, boots. */
	static final int[] SLOT_FACTOR = {11, 16, 15, 13};

	/** The full diabolium set gives back this share of the damage its wearer's blows do. */
	static final float DIABOLIUM_LEECH = 1.0F / 6.0F;
	/** The full crust set, crouching, takes this share of the damage, and each piece loses this much durability for the blow. */
	static final float CRUST_SHARE = 0.5F;
	static final int CRUST_WEAR = 10;

	private FtsGear() {
	}

	public static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("diabolium_block"));
		DIABOLIUM_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(BlockBehaviour.Properties.of().setId(blockKey)
			.mapColor(MapColor.COLOR_RED).strength(4.0F, 30.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
		ResourceKey<Item> blockItemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("diabolium_block"));
		DIABOLIUM_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, blockItemKey, new BlockItem(DIABOLIUM_BLOCK,
			new Item.Properties().setId(blockItemKey).useBlockDescriptionPrefix().fireResistant()));
		DIABOLIUM_NUGGET = item("diabolium_nugget", p -> new Item(p.fireResistant()));
		DIABOLIUM_INGOT = item("diabolium_ingot", p -> new Item(p.fireResistant()));
		CRYSTALLIZED_BLOOD = item("crystallized_blood", Item::new);
		CRYSTALLIZED_FRAGMENT = item("crystallized_fragment", Item::new);
		BOTTLE_OF_BLOOD = item("bottle_of_blood", Item::new);
		CORRUPTED_HEART = item("corrupted_heart", p -> new Item(p.rarity(Rarity.EPIC)));

		DIABOLIUM_HELMET = armour("diabolium_helmet", "diabolium", EquipmentSlot.HEAD, 0, DIABOLIUM_ARMOR[0], DIABOLIUM_DURABILITY, DIABOLIUM_TOUGHNESS,
			DIABOLIUM_ENCHANTABILITY, true, DIABOLIUM_INGOT);
		DIABOLIUM_CHEST = armour("diabolium_chest", "diabolium", EquipmentSlot.CHEST, 1, DIABOLIUM_ARMOR[1], DIABOLIUM_DURABILITY, DIABOLIUM_TOUGHNESS,
			DIABOLIUM_ENCHANTABILITY, true, DIABOLIUM_INGOT);
		DIABOLIUM_LEGGINGS = armour("diabolium_leggings", "diabolium", EquipmentSlot.LEGS, 2, DIABOLIUM_ARMOR[2], DIABOLIUM_DURABILITY, DIABOLIUM_TOUGHNESS,
			DIABOLIUM_ENCHANTABILITY, true, DIABOLIUM_INGOT);
		DIABOLIUM_BOOTS = armour("diabolium_boots", "diabolium", EquipmentSlot.FEET, 3, DIABOLIUM_ARMOR[3], DIABOLIUM_DURABILITY, DIABOLIUM_TOUGHNESS,
			DIABOLIUM_ENCHANTABILITY, true, DIABOLIUM_INGOT);
		CRUST_HELMET = armour("crust_helmet", "crust", EquipmentSlot.HEAD, 0, CRUST_ARMOR[0], CRUST_DURABILITY, CRUST_TOUGHNESS, CRUST_ENCHANTABILITY,
			false, NetherBeasts.CRIMSON_SHELL);
		CRUST_CHEST = armour("crust_chest", "crust", EquipmentSlot.CHEST, 1, CRUST_ARMOR[1], CRUST_DURABILITY, CRUST_TOUGHNESS, CRUST_ENCHANTABILITY,
			false, NetherBeasts.CRIMSON_SHELL);
		CRUST_LEGGINGS = armour("crust_leggings", "crust", EquipmentSlot.LEGS, 2, CRUST_ARMOR[2], CRUST_DURABILITY, CRUST_TOUGHNESS, CRUST_ENCHANTABILITY,
			false, NetherBeasts.CRIMSON_SHELL);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(DIABOLIUM_NUGGET);
			output.accept(DIABOLIUM_INGOT);
			output.accept(CRYSTALLIZED_FRAGMENT);
			output.accept(CRYSTALLIZED_BLOOD);
			output.accept(BOTTLE_OF_BLOOD);
			output.accept(CORRUPTED_HEART);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.accept(DIABOLIUM_BLOCK_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_BOOTS, DIABOLIUM_HELMET);
			output.insertAfter(DIABOLIUM_HELMET, DIABOLIUM_CHEST);
			output.insertAfter(DIABOLIUM_CHEST, DIABOLIUM_LEGGINGS);
			output.insertAfter(DIABOLIUM_LEGGINGS, DIABOLIUM_BOOTS);
			output.insertAfter(DIABOLIUM_BOOTS, CRUST_HELMET);
			output.insertAfter(CRUST_HELMET, CRUST_CHEST);
			output.insertAfter(CRUST_CHEST, CRUST_LEGGINGS);
		});

		FtsWeapons.register();

		// the diabolium set gives back a sixth of the damage its wearer's blows do
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && !blocked && source.getEntity() instanceof ServerPlayer player && player != entity && diaboliumSet(player)) {
				player.heal(taken * DIABOLIUM_LEECH);
			}
		});
	}

	private static GearArmour armour(String name, String set, EquipmentSlot slot, int index, int armor, int durability, float toughness, int enchantability,
		boolean fireproof, Item repair) {
		Identifier id = JaymosKingdoms.id(name);
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE), group)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE), group)
			.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE), group).build();
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item.Properties props = new Item.Properties().setId(key).rarity(Rarity.UNCOMMON).durability(durability * SLOT_FACTOR[index])
			.enchantable(enchantability).attributes(attributes)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(set))).build())
			.repairable(repair);
		if (fireproof) {
			props.fireResistant();
		}
		return Registry.register(BuiltInRegistries.ITEM, key, new GearArmour(props, "tooltip.jaymoskingdoms." + set + "_armor"));
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}

	/** Whether {@code wearer} has the whole diabolium set on. */
	public static boolean diaboliumSet(LivingEntity wearer) {
		return wearer.getItemBySlot(EquipmentSlot.HEAD).is(DIABOLIUM_HELMET) && wearer.getItemBySlot(EquipmentSlot.CHEST).is(DIABOLIUM_CHEST)
			&& wearer.getItemBySlot(EquipmentSlot.LEGS).is(DIABOLIUM_LEGGINGS) && wearer.getItemBySlot(EquipmentSlot.FEET).is(DIABOLIUM_BOOTS);
	}

	/** Whether {@code wearer} has the whole crust set on (it has no boots). */
	public static boolean crustSet(LivingEntity wearer) {
		return wearer.getItemBySlot(EquipmentSlot.HEAD).is(CRUST_HELMET) && wearer.getItemBySlot(EquipmentSlot.CHEST).is(CRUST_CHEST)
			&& wearer.getItemBySlot(EquipmentSlot.LEGS).is(CRUST_LEGGINGS);
	}

	/**
	 * What a blow does to {@code victim} when the crust set is worn crouching: half, and the set wears for it. (Not for what
	 * nothing guards against: the void and the commands.)
	 */
	public static float crouched(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || !player.isCrouching() || !crustSet(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
			player.getItemBySlot(slot).hurtAndBreak(CRUST_WEAR, player, slot);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		return amount * CRUST_SHARE;
	}
}
