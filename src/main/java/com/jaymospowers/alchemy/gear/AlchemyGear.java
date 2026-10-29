package com.jaymospowers.alchemy.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Essence enchanting and the artificer's gear (docs/Alchemy-Plan.md section 5):
 * <ul>
 * <li>{@link EssenceEnchanting}: the enchanting table can take essence from carried phials instead of items.</li>
 * <li>The {@link SpellbindingClothItem Spellbinding Cloth}, which strips an enchantment back into essence.</li>
 * <li>The {@link RodItem Artificer's Rod} and its five {@link Focus foci}, and the {@link BoundBlockItem bound block} the Lift focus makes.</li>
 * <li>Ichorium (from Ichor): an ingot, a sword, a pickaxe and an axe, stronger than netherite and hungry
 * ({@link IchoriumToolItem}); and the Ichorcloth hood, robe, leggings and boots, which turn aside magic and slow flux's hold on the wearer.</li>
 * <li>The {@link LanternItem Lantern of Everflame}.</li>
 * </ul>
 * Everything is gated by research, registered here (Artifice and Enchanting tabs of the Codex).
 */
public final class AlchemyGear {
	// ---- research ids (Kingdoms' hermits hint by these; don't rename) ----
	/** Clockwork golems (Kingdoms' {@code ClockworkGolem}): only with Kingdoms, whose infusion recipe makes them. */
	public static final String GOLEMANCY = "golemancy";
	private static final Identifier CLOCKWORK_GOLEM = Identifier.fromNamespaceAndPath("jaymoskingdoms", "clockwork_golem");
	/** The elemental spell tomes (Kingdoms' {@code Tomes}): only with Kingdoms, whose infusion recipes make them. */
	public static final String TOMECRAFT = "tomecraft";
	private static final Identifier WATER_TOME = Identifier.fromNamespaceAndPath("jaymoskingdoms", "water_tome");
	public static final String ARTIFICE = "artifice", ESSENCE_ENCHANTING = "essence_enchanting", SPELLBINDING = "spellbinding",
		ICHORIUM = "ichorium", ICHORCLOTH = "ichorcloth", EVERFLAME_LANTERN = "everflame_lantern";

	// ---- tuning ----
	/** Ichorium tools against netherite (2031 uses, speed 9, +4 damage, enchantability 15). */
	public static final int ICHORIUM_DURABILITY = 3200, ICHORIUM_ENCHANTABILITY = 18;
	public static final float ICHORIUM_SPEED = 10.5F, ICHORIUM_DAMAGE = 5.0F;
	/** Ichorcloth: durability multiplier, defence (hood, robe, leggings, boots), enchantability, toughness. */
	public static final int CLOTH_DURABILITY = 22, CLOTH_HOOD = 1, CLOTH_ROBE = 4, CLOTH_LEGGINGS = 3, CLOTH_BOOTS = 1, CLOTH_ENCHANTABILITY = 22;
	public static final float CLOTH_TOUGHNESS = 1.0F;
	/** Share of magic damage each worn ichorcloth piece turns aside (four pieces: most of it). */
	public static final float MAGIC_RESIST_PER_PIECE = 0.2F;
	/** What each worn piece takes off the rate flux corrupts the wearer at (four pieces: half as fast). */
	public static final float FLUX_EASE_PER_PIECE = 0.125F;

	/** The focus a rod holds: the focus item's id. */
	public static DataComponentType<Identifier> FOCUS;
	/** What a bound block holds. */
	public static DataComponentType<BoundBlockItem.Bound> BOUND;

	public static Item ROD, SPELLBINDING_CLOTH, BOUND_BLOCK, ICHORIUM_INGOT, ICHORIUM_SWORD, ICHORIUM_PICKAXE, ICHORIUM_AXE, EVERFLAME_LANTERN_ITEM;
	public static final Map<Focus, Item> FOCI = new EnumMap<>(Focus.class);
	/** Hood, robe, leggings, boots. */
	public static final Item[] CLOTH_PIECES = new Item[4];
	public static final EquipmentSlot[] CLOTH_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static final TagKey<Item> ICHORIUM_MATERIALS = TagKey.create(Registries.ITEM, JaymosPowers.id("ichorium_tool_materials"));
	public static final TagKey<Item> CLOTH_REPAIRS = TagKey.create(Registries.ITEM, JaymosPowers.id("repairs_ichorcloth_armor"));

	/** Set while the magic reduction re-deals a blow, so it isn't reduced twice. */

	private AlchemyGear() {
	}

	public static void init() {
		FOCUS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("focus"),
			DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());
		BOUND = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("bound_block"),
			DataComponentType.<BoundBlockItem.Bound>builder().persistent(BoundBlockItem.Bound.CODEC)
				.networkSynchronized(ByteBufCodecs.fromCodec(BoundBlockItem.Bound.CODEC)).build());

		ROD = Alchemy.item("artificers_rod", p -> new RodItem(p.stacksTo(1).rarity(Rarity.RARE)));
		for (Focus focus : Focus.values()) {
			FOCI.put(focus, Alchemy.item(focus.id + "_focus", p -> new FocusItem(p.stacksTo(16).rarity(Rarity.UNCOMMON), focus)));
		}
		BOUND_BLOCK = Alchemy.item("bound_block", p -> new BoundBlockItem(p.stacksTo(1).rarity(Rarity.UNCOMMON)));
		SPELLBINDING_CLOTH = Alchemy.item("spellbinding_cloth", p -> new SpellbindingClothItem(p.durability(8).rarity(Rarity.UNCOMMON)));
		EVERFLAME_LANTERN_ITEM = Alchemy.item("everflame_lantern", p -> new LanternItem(p.stacksTo(1).rarity(Rarity.RARE)));

		ToolMaterial ichorium = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, ICHORIUM_DURABILITY, ICHORIUM_SPEED, ICHORIUM_DAMAGE,
			ICHORIUM_ENCHANTABILITY, ICHORIUM_MATERIALS);
		ICHORIUM_INGOT = Alchemy.item("ichorium_ingot", p -> new Item(p.rarity(Rarity.UNCOMMON)));
		ICHORIUM_SWORD = Alchemy.item("ichorium_sword", p -> new IchoriumToolItem(p.sword(ichorium, 3.0F, -2.4F).rarity(Rarity.RARE)));
		ICHORIUM_PICKAXE = Alchemy.item("ichorium_pickaxe", p -> new IchoriumToolItem(p.pickaxe(ichorium, 1.0F, -2.8F).rarity(Rarity.RARE)));
		ICHORIUM_AXE = Alchemy.item("ichorium_axe", p -> new IchoriumToolItem(p.axe(ichorium, 5.0F, -3.0F).rarity(Rarity.RARE)));

		ArmorType[] types = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
		String[] names = {"hood", "robe", "leggings", "boots"};
		ArmorMaterial cloth = clothMaterial();
		for (int i = 0; i < 4; i++) {
			ArmorType type = types[i];
			CLOTH_PIECES[i] = Alchemy.item("ichorcloth_" + names[i], p -> new Item(p.humanoidArmor(cloth, type).rarity(Rarity.UNCOMMON)));
		}

		registerResearch();
	}

	private static ArmorMaterial clothMaterial() {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		defense.put(ArmorType.HELMET, CLOTH_HOOD);
		defense.put(ArmorType.CHESTPLATE, CLOTH_ROBE);
		defense.put(ArmorType.LEGGINGS, CLOTH_LEGGINGS);
		defense.put(ArmorType.BOOTS, CLOTH_BOOTS);
		defense.put(ArmorType.BODY, CLOTH_ROBE);
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("ichorcloth"));
		return new ArmorMaterial(CLOTH_DURABILITY, defense, CLOTH_ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_LEATHER, CLOTH_TOUGHNESS, 0.0F, CLOTH_REPAIRS, asset);
	}

	/**
	 * What's left of a magic blow on {@code entity} after its ichorcloth (from {@code IchorclothMagicMixin}, as vanilla
	 * takes off what the Protection enchantment does, so every other damage hook sees the blow once, already smaller).
	 */
	public static float magicTaken(net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof Player player) || !magic(source)) {
			return amount;
		}
		return amount * (1.0F - Math.min(1.0F, MAGIC_RESIST_PER_PIECE * worn(player)));
	}

	/** Whether this blow is magic: the potion and spell kinds, the Wither's rot and the dragon's breath. */
	private static boolean magic(DamageSource source) {
		return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH);
	}

	/** How many ichorcloth pieces {@code player} wears (0 to 4). */
	public static int worn(Player player) {
		int n = 0;
		for (int i = 0; i < 4; i++) {
			if (player.getItemBySlot(CLOTH_SLOTS[i]).is(CLOTH_PIECES[i])) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The share of the usual rate at which flux in the chunk corrupts {@code player} (1.0 bare, 0.5 in the whole
	 * ichorcloth set). Flux's effects ({@link com.jaymospowers.alchemy.flux.FluxEffects}) multiply what they would add by this.
	 */
	public static float fluxFactor(Player player) {
		return 1.0F - FLUX_EASE_PER_PIECE * worn(player);
	}

	private static void registerResearch() {
		Research.Category art = Research.Category.ARTIFICE, ench = Research.Category.ENCHANTING;
		Research.register(new Research.Entry(ESSENCE_ENCHANTING, ench, () -> Items.ENCHANTING_TABLE,
			EssenceList.of(Essence.ARCANA, 15, Essence.MIND, 10).plus(Essence.CRAFT, 8), List.of("essences"), 0, 0, 0));
		Research.register(new Research.Entry(SPELLBINDING, ench, () -> SPELLBINDING_CLOTH,
			EssenceList.of(Essence.ARCANA, 15, Essence.SALT, 10).plus(Essence.MERCURY, 10), List.of(ESSENCE_ENCHANTING), 0, 1, 0));

		Research.register(new Research.Entry(ARTIFICE, art, () -> ROD,
			EssenceList.of(Essence.ARCANA, 14, Essence.CRAFT, 10).plus(Essence.SPIRIT, 8), List.of("infusion"), 0, 0, 2));
		Research.register(new Research.Entry("focus_pull", art, () -> FOCI.get(Focus.PULL),
			EssenceList.of(Essence.MOTION, 12, Essence.AIR, 8), List.of(ARTIFICE), 0, 1, 0));
		Research.register(new Research.Entry("focus_push", art, () -> FOCI.get(Focus.PUSH),
			EssenceList.of(Essence.MOTION, 12, Essence.EARTH, 8), List.of(ARTIFICE), 0, 1, 1));
		Research.register(new Research.Entry("focus_lift", art, () -> FOCI.get(Focus.LIFT),
			EssenceList.of(Essence.EARTH, 14, Essence.SPIRIT, 10).plus(Essence.MOTION, 8), List.of(ARTIFICE), 0, 1, 2));
		Research.register(new Research.Entry("focus_ember", art, () -> FOCI.get(Focus.EMBER),
			EssenceList.of(Essence.FIRE, 12, Essence.SPARK, 8), List.of(ARTIFICE), 0, 1, 3));
		Research.register(new Research.Entry("focus_frost", art, () -> FOCI.get(Focus.FROST),
			EssenceList.of(Essence.FROST, 12, Essence.WATER, 8), List.of(ARTIFICE), 0, 1, 4));

		Research.register(new Research.Entry(ICHORIUM, art, () -> ICHORIUM_INGOT,
			EssenceList.of(Essence.METAL, 16, Essence.ARCANA, 12).plus(Essence.WEALTH, 6), List.of("ichor"), 0, 3, 0));
		Research.register(new Research.Entry(ICHORCLOTH, art, () -> CLOTH_PIECES[1],
			EssenceList.of(Essence.SPIRIT, 14, Essence.SALT, 10).plus(Essence.LIGHT, 8), List.of(ICHORIUM), 0, 4, 0));
		Research.register(new Research.Entry(EVERFLAME_LANTERN, art, () -> EVERFLAME_LANTERN_ITEM,
			EssenceList.of(Essence.LIGHT, 14, Essence.FIRE, 8).plus(Essence.CRAFT, 6), List.of("everflame", ARTIFICE), 0, 3, 2));
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			// the Enchanting tab's third column (spellbinding is the second); the icon is a Kingdoms tome by lookup, the book without it
			Research.register(new Research.Entry(TOMECRAFT, ench, () -> BuiltInRegistries.ITEM.getOptional(WATER_TOME).orElse(Items.BOOK),
				EssenceList.of(Essence.ARCANA, 16, Essence.MIND, 10).plus(Essence.CRYSTAL, 8), List.of(SPELLBINDING), 0, 2, 0));
			Research.register(new Research.Entry(GOLEMANCY, art, () -> BuiltInRegistries.ITEM.getOptional(CLOCKWORK_GOLEM).orElse(Items.CLOCK),
				EssenceList.of(Essence.METAL, 16, Essence.MOTION, 14).plus(Essence.SPIRIT, 10), List.of(ARTIFICE, "ichor"), 0, 0, 4));
		}
	}
}
