package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jspecify.annotations.Nullable;

/**
 * The late-medieval harness (the idea from Knights &amp; Heraldry; our own models, textures and names, drawn by
 * kingdoms/tools/gen_heraldry_armour.py): armour worn in layers and in parts.
 * <ul>
 * <li>Eight helms, a cuirass and sabatons go in the helmet, chestplate and boot slots as any armour does.</li>
 * <li>Everything else goes in the {@link Harness} slots: the arming doublet (padding) and the mail haubergeon under the
 * cuirass; spaulders in plate, mail or brigandine; the arm harness, gauntlets, cuisses and greaves; a giornea or a cloak
 * over it all, a hood, and a crest on the helm.</li>
 * <li>Every plate piece comes bright, blackened or gilded ({@link #FINISH}, chosen at the smith's anvil; gilded is for
 * royals and lords, {@link HeraldryWear}).</li>
 * <li>A helm with a visor can have it raised ({@link Visors}).</li>
 * <li>The wider makes: the fluted cuirass and the rounded breastplate beside the plain one, the coat of plates (cloth in
 * the realm's colour over riveted plates), the gorget at the neck, faulds and tassets at the waist, spaulders with
 * rondels, and the tabard in a realm's arms ({@link #ARMS}).</li>
 * </ul>
 * All of it is drawn from baked models by the client's {@code HeraldryLayer}.
 */
public final class HeraldryArmour {
	/** The plate's numbers as a vanilla armour material (helm, cuirass, sabatons): durability multiplier, defence, toughness, knockback resistance. */
	static final int PLATE_DURABILITY = 30, HELM_ARMOUR = 2, CUIRASS_ARMOUR = 5, SABATON_ARMOUR = 1, PLATE_ENCHANT = 10;
	static final float PLATE_TOUGHNESS = 1.0F, PLATE_KNOCKBACK = 0.05F;
	/** The finishes a plate piece can have besides bright (no component). */
		public static final String BLACKENED = "blackened", GILDED = "gilded", RUSSETED = "russeted", BLUED = "blued", PAINTED = "painted", ETCHED = "etched", RUSTED = "rusted",
			SPIKED = "spiked", GILT_BANDED = "gilt_banded", FUR_LINED = "fur_lined", QUILTED = "quilted", BRINE_SOAKED = "brine_soaked",
			HIDE = "hide", BRONZE = "bronze", LAMELLAR = "lamellar";
	/** A plate piece's finish ({@link #BLACKENED} or {@link #GILDED}; bright has none). */
	public static final DataComponentType<String> FINISH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("finish"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
	/** On a visored helm: its visor is raised. */
	public static final DataComponentType<Boolean> VISOR_UP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("visor_up"),
		DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	/**
	 * A realm's arms on a heraldic tabard, front and back: which of the realm ordinaries ({@code Outfits.ORDINARIES}, by
	 * index) laid in the {@code charge} colour (rgb) over the tabard's own dye, the field.
	 */
	public record Arms(int ordinary, int charge) {
		static final Codec<Arms> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("ordinary").forGetter(Arms::ordinary), Codec.INT.fieldOf("charge").forGetter(Arms::charge)).apply(i, Arms::new));
		static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Arms> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Arms::ordinary, ByteBufCodecs.INT, Arms::charge, Arms::new);
	}

	public static final DataComponentType<Arms> ARMS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("arms"),
		DataComponentType.<Arms>builder().persistent(Arms.CODEC).networkSynchronized(Arms.STREAM_CODEC).build());

	/** How a helm covers the face: not at all, with a visor that lifts, or with a plate that doesn't. */
	public enum Face {
		OPEN, VISORED, FIXED
	}

	/**
	 * One piece: its item, the harness slot it goes in (null: a vanilla armour slot), the armour and toughness it gives
	 * there, whether it is plate (and so has a finish), its baked model and, for a helm, its face, how much of the view
	 * its sight leaves (a share of the screen's height) and how far a crest is lifted to stand on it.
	 */
	public record Piece(String name, Item item, Harness.@Nullable Slot slot, int armour, float toughness, boolean plate, String model,
		@Nullable Face face, float sight, float crestRise) {
	}

	static final ArmorMaterial PLATE = new ArmorMaterial(PLATE_DURABILITY,
		ArmorMaterials.makeDefense(SABATON_ARMOUR, CUIRASS_ARMOUR, CUIRASS_ARMOUR, HELM_ARMOUR, CUIRASS_ARMOUR), PLATE_ENCHANT, SoundEvents.ARMOR_EQUIP_IRON,
		PLATE_TOUGHNESS, PLATE_KNOCKBACK, Armory.STEEL_INGOTS, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("heraldry_plate")));
	/** The coat of plates: plates riveted inside a cloth coat, a little weaker than a cuirass, mended with iron. */
	static final int COAT_DURABILITY = 22, COAT_ARMOUR = 4;
	static final float COAT_TOUGHNESS = 0.5F;
	static final ArmorMaterial COAT = new ArmorMaterial(COAT_DURABILITY,
		ArmorMaterials.makeDefense(SABATON_ARMOUR, COAT_ARMOUR, COAT_ARMOUR, HELM_ARMOUR, COAT_ARMOUR), PLATE_ENCHANT, SoundEvents.ARMOR_EQUIP_IRON,
		COAT_TOUGHNESS, 0.0F, net.minecraft.tags.ItemTags.REPAIRS_IRON_ARMOR, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("heraldry_plate")));

	/** Every piece by name (its item id), in the order they were registered. */
	public static final Map<String, Piece> PIECES = new LinkedHashMap<>();
	private static final Map<Item, Piece> BY_ITEM = new java.util.HashMap<>();
	/** The eight helms, the eight crest beasts and the three makes of spaulders, by name. */
		public static final List<String> HELMS = List.of("frogmouth_helm", "houndskull_bascinet", "armet", "close_helm", "savoyard_helm", "sallet",
			"kettle_hat", "morion", "great_helm", "barbute", "burgonet", "spangenhelm", "lobster_tail", "grotesque_visor", "veiled_helm");
		public static final List<String> BEASTS = List.of("crest_dragon", "crest_lion", "crest_stag", "crest_boar", "crest_eagle", "crest_pegasus",
			"crest_unicorn", "crest_snake", "crest_griffin", "crest_bear", "crest_wolf", "crest_raven", "crest_hound");
		public static final List<String> SPAULDERS = List.of("plate_spaulders", "mail_spaulders", "brigandine_spaulders", "spiked_spaulders", "gilt_banded_spaulders", "fur_lined_spaulders");

	private HeraldryArmour() {
	}

	static void register() {
			// the helms: face, sight (the share of the screen's height left to see through), and how far (model pixels) a crest
			// is lifted over its place on a plain helmet to stand on this one's crown
			helm("frogmouth_helm", Face.FIXED, 0.2F, 0.0F);
			helm("houndskull_bascinet", Face.VISORED, 0.24F, 2.0F);
			helm("armet", Face.VISORED, 0.26F, 0.9F);
			helm("close_helm", Face.VISORED, 0.26F, 1.0F);
			helm("savoyard_helm", Face.VISORED, 0.3F, 1.0F);
			helm("sallet", Face.VISORED, 0.34F, 0.5F);
			helm("kettle_hat", Face.OPEN, 1.0F, 0.6F);
			helm("morion", Face.OPEN, 1.0F, 2.5F);
			helm("great_helm", Face.FIXED, 0.15F, 0.0F);
			helm("barbute", Face.OPEN, 0.85F, 0.5F);
			helm("burgonet", Face.VISORED, 0.3F, 1.2F);
			helm("spangenhelm", Face.FIXED, 0.4F, 0.3F);
			helm("lobster_tail", Face.VISORED, 0.25F, 1.5F);
			helm("grotesque_visor", Face.VISORED, 0.2F, 1.8F);
			helm("veiled_helm", Face.VISORED, 0.3F, 1.5F);
			worn("plate_cuirass", ArmorType.CHESTPLATE);
		worn("plate_sabatons", ArmorType.BOOTS);
		// the harness: slot, armour, toughness, durability, what mends it
		part("arming_doublet", Harness.Slot.PADDING, 1, 0.0F, 160, false, Items.LEATHER, null);
		part("mail_haubergeon", Harness.Slot.MAIL, 2, 0.0F, 300, false, Items.IRON_INGOT, null);
		part("plate_spaulders", Harness.Slot.SHOULDERS, 1, 0.5F, 260, true, null, "plate_spaulders");
		part("mail_spaulders", Harness.Slot.SHOULDERS, 1, 0.0F, 220, false, Items.IRON_INGOT, "plate_spaulders");
		part("brigandine_spaulders", Harness.Slot.SHOULDERS, 1, 0.0F, 240, false, Items.IRON_INGOT, "plate_spaulders");
		part("plate_arm_harness", Harness.Slot.ARMS, 1, 0.5F, 300, true, null, "plate_arm_harness");
		part("plate_gauntlets", Harness.Slot.HANDS, 1, 0.0F, 240, true, null, "plate_gauntlets");
		part("plate_cuisses", Harness.Slot.THIGHS, 2, 1.0F, 340, true, null, "plate_cuisses");
		part("plate_greaves", Harness.Slot.SHINS, 1, 0.5F, 300, true, null, "plate_greaves");
		// cloth, and what stands on a helm: no armour in them, and nothing to wear out
		part("giornea", Harness.Slot.OVER, 0, 0.0F, 0, false, null, "giornea");
		part("cloak", Harness.Slot.OVER, 0, 0.0F, 0, false, null, "cloak");
		part("torn_cloak", Harness.Slot.OVER, 0, 0.0F, 0, false, null, "cloak");
		part("hood", Harness.Slot.HOOD, 0, 0.0F, 0, false, null, "hood");
		part("torn_hood", Harness.Slot.HOOD, 0, 0.0F, 0, false, null, "hood");
		part("torse", Harness.Slot.CREST, 0, 0.0F, 0, false, null, "torse");
		for (String beast : BEASTS) {
			part(beast, Harness.Slot.CREST, 0, 0.0F, 0, false, null, beast);
		}
		part("crest_horns", Harness.Slot.CREST, 0, 0.0F, 0, false, null, "crest_horns");
				part("crest_wings", Harness.Slot.CREST, 0, 0.0F, 0, false, null, "crest_wings");
				// (the plume came with its art, name and dye recipe but was never registered, and the recipe stopped the server loading)
				part("crest_plume", Harness.Slot.CREST, 0, 0.0F, 0, false, null, "crest_plume");
				// creed variants
				worn("faith_cuirass", ArmorType.CHESTPLATE);
				part("oath_sleeves", Harness.Slot.ARMS, 1, 0.0F, 240, true, null, "oath_sleeves");
				part("spiked_spaulders", Harness.Slot.SHOULDERS, 1, 0.5F, 260, true, null, "spiked_spaulders");
				worn("etched_cuirass", ArmorType.CHESTPLATE);
				worn("gilt_banded_cuirass", ArmorType.CHESTPLATE);
				part("gilt_banded_spaulders", Harness.Slot.SHOULDERS, 1, 0.5F, 260, true, null, "gilt_banded_spaulders");
				part("tabard", Harness.Slot.OVER, 0, 0.0F, 0, false, null, "tabard");
				// land variants
				worn("fur_lined_cuirass", ArmorType.CHESTPLATE);
				part("fur_lined_spaulders", Harness.Slot.SHOULDERS, 1, 0.5F, 260, true, null, "fur_lined_spaulders");
				part("fur_lined_arm_harness", Harness.Slot.ARMS, 1, 0.5F, 300, true, null, "fur_lined_arm_harness");
				part("fur_lined_gauntlets", Harness.Slot.HANDS, 1, 0.0F, 240, true, null, "fur_lined_gauntlets");
				part("fur_lined_cuisses", Harness.Slot.THIGHS, 2, 1.0F, 340, true, null, "fur_lined_cuisses");
				part("fur_lined_greaves", Harness.Slot.SHINS, 1, 0.5F, 300, true, null, "fur_lined_greaves");
				part("quilted_coat", Harness.Slot.MAIL, 2, 0.0F, 220, false, Items.IRON_INGOT, "quilted_coat");
				worn("quilted_cotton_armor", ArmorType.CHESTPLATE);
				worn("hide_armor", ArmorType.CHESTPLATE);
				worn("bronze_armor", ArmorType.CHESTPLATE);
				worn("laced_lamellar", ArmorType.CHESTPLATE);
		// the wider makes (Harness 2): two more cuirasses, the coat of plates, the gorget, faulds and tassets, rondels
		worn("fluted_cuirass", ArmorType.CHESTPLATE);
		worn("rounded_breastplate", ArmorType.CHESTPLATE);
		coat("coat_of_plates");
		part("gorget", Harness.Slot.NECK, 1, 0.5F, 240, true, null, "gorget");
		part("faulds_and_tassets", Harness.Slot.WAIST, 1, 0.5F, 280, true, null, "faulds_and_tassets");
		part("rondel_spaulders", Harness.Slot.SHOULDERS, 1, 1.0F, 280, true, null, "rondel_spaulders");
		List<Item> all = new ArrayList<>();
		PIECES.values().forEach(p -> all.add(p.item()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.NETHERITE_BOOTS;
			for (Item item : all) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static void helm(String name, Face face, float sight, float crestRise) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(PLATE, ArmorType.HELMET)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.heraldry.face." + face.name().toLowerCase(java.util.Locale.ROOT)))))));
		put(new Piece(name, item, null, 0, 0.0F, true, "heraldry_" + name, face, sight, crestRise));
	}

	private static void worn(String name, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(PLATE, type)));
		put(new Piece(name, item, null, 0, 0.0F, true, "heraldry_" + name, null, 1.0F, 0.0F));
	}

	/** The coat of plates: worn in the chestplate slot, cloth outside (dyed like the doublet, so no finish), plates within. */
	private static void coat(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(COAT, ArmorType.CHESTPLATE)));
		put(new Piece(name, item, null, 0, 0.0F, false, "heraldry_" + name, null, 1.0F, 0.0F));
	}

	/** A piece for a {@link Harness} slot; {@code mend} is what repairs it (null: steel for plate, nothing for cloth). */
	private static void part(String name, Harness.Slot slot, int armour, float toughness, int durability, boolean plate, @Nullable Item mend, @Nullable String model) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(1)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.harness." + slot.key()))));
		if (durability > 0) {
			properties = properties.durability(durability);
			properties = mend != null ? properties.repairable(mend) : properties.repairable(Armory.STEEL_INGOTS);
		}
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Harness.Part(properties));
		put(new Piece(name, item, slot, armour, toughness, plate, model == null ? "" : "heraldry_" + model, null, 1.0F, 0.0F));
	}

	private static void put(Piece piece) {
		PIECES.put(piece.name(), piece);
		BY_ITEM.put(piece.item(), piece);
	}

	/** The piece {@code stack} is, or null if it isn't one of these. */
	public static @Nullable Piece of(ItemStack stack) {
		return stack.isEmpty() ? null : BY_ITEM.get(stack.getItem());
	}

	/** A piece as a stack (empty for a name that isn't one). */
	public static ItemStack stack(String name) {
		Piece piece = PIECES.get(name);
		return piece == null ? ItemStack.EMPTY : new ItemStack(piece.item());
	}

	/** A plate piece in {@code finish} ("" for bright), named for it ("Gilded Armet"). */
	public static ItemStack stack(String name, String finish) {
		return finished(stack(name), finish);
	}

	/** {@code stack} given {@code finish} ("" leaves it bright); only plate takes one. */
	public static ItemStack finished(ItemStack stack, String finish) {
		Piece piece = of(stack);
		if (piece == null || !piece.plate() || finish.isEmpty()) {
			return stack;
		}
		stack.set(FINISH, finish);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.finish." + finish, Component.translatable(piece.item().getDescriptionId())));
		return stack;
	}

	/** {@code stack}'s finish: "" (bright), {@link #BLACKENED} or {@link #GILDED}. */
	public static String finish(ItemStack stack) {
		return stack.getOrDefault(FINISH, "");
	}

	public static boolean gilded(ItemStack stack) {
		return GILDED.equals(stack.get(FINISH));
	}

	/** How {@code wearer}'s helm covers the face now: {@link Face#OPEN} for none, an open helm, or a visor raised. */
	public static Face face(LivingEntity wearer) {
		ItemStack helm = wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
		Piece piece = of(helm);
		if (piece == null || piece.face() == null || piece.face() == Face.VISORED && helm.getOrDefault(VISOR_UP, false)) {
			return Face.OPEN;
		}
		return piece.face();
	}
}
