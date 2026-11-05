package com.jaymospowers.baubles.artifacts;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BaubleItem.Bonus;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.baubles.Baubles.Kind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * (Artifacts) Andy: "very necessary bauble additions and acquisition" from the Artifacts mod (Wouter Pauwels, MIT). Its
 * 26.3 branch is unreleased and leans on Trinkets for slots, so instead its 45 wearables are ported here as our own
 * baubles: its item art is used under the MIT licence (notice in {@code textures/item/bauble/artifacts/LICENSE.txt}),
 * the effects are rewritten in {@link ArtifactEffects}, and where they turn up is ours ({@link ArtifactLoot}). They are
 * found only, never crafted, as in Artifacts. The handheld umbrella and the everlasting foods aren't baubles and are left
 * out, as are mimics.
 * <p>Slots: hats on the head, scarves and pendants on the amulet, charms on the charm, bottles and gadgets on the belt
 * (the bottles and the skull hang at the hip), gloves and claws on the wrist, the glutton's ring on a ring, and the shoes on
 * the new feet slot.
 */
public final class ArtifactBaubles {
	// head
	public static Item ALEWIFE_CAP, JESTER_CAP, REED_BREATHER, OWL_MASK, VILLAGER_HAT, SOOTHSAYER_HOOD, RIDERS_HAT,
		FISHERS_HAT;
	// amulet
	public static Item LUCKY_SCARF, SCARF_OF_INVISIBILITY, CROSS_NECKLACE, PANIC_NECKLACE, SHOCK_PENDANT, FLAME_PENDANT, THORN_PENDANT;
	// charm
	public static Item CHARM_OF_SINKING, CHARM_OF_SHRINKING, CHORUS_TOTEM;
	// belt
	public static Item CLOUD_IN_A_BOTTLE, OBSIDIAN_SKULL, ANTIDOTE_VESSEL, LODESTONE_CHARM, CRYSTAL_HEART, WIND_BLADDER, ENDER_POUCH,
		JESTERS_CUSHION;
	// wrist and ring
	public static Item DIGGING_CLAWS, FERAL_CLAWS, IRON_KNUCKLES, FIRE_GAUNTLET, RAM_BRACER, VAMPIRIC_GLOVE, GOLDEN_HOOK, SMELTERS_BRACER,
		WITHERED_BRACELET, GLUTTONS_RING;
	// feet
	public static Item WATERSTRIDER_BOOTS, HARE_BOOTS, KITTY_SLIPPERS, COURIERS_BOOTS, SNOWSHOES, STEADFAST_SPIKES, FROGSKIN_FLIPPERS, ROOTED_BOOTS,
		STRIDER_SHOES;

	/** The jester's cushion's two sounds (Artifacts' own recordings, MIT). */
	public static Holder<SoundEvent> FART;
	static final List<Item> ALL = new ArrayList<>();
	private static final Kind[] NONE = {};

	private ArtifactBaubles() {
	}

	public static void init() {
		ALEWIFE_CAP = item("alewife_cap", Kind.HEAD, Rarity.COMMON);
		JESTER_CAP = item("jester_cap", Kind.HEAD, Rarity.UNCOMMON);
		REED_BREATHER = item("reed_breather", Kind.HEAD, Rarity.COMMON);
		OWL_MASK = item("owl_mask", Kind.HEAD, Rarity.UNCOMMON);
		VILLAGER_HAT = item("villager_hat", Kind.HEAD, Rarity.COMMON);
		SOOTHSAYER_HOOD = item("soothsayer_hood", Kind.HEAD, Rarity.RARE);
		RIDERS_HAT = item("riders_hat", Kind.HEAD, Rarity.COMMON);
		FISHERS_HAT = item("fishers_hat", Kind.HEAD, Rarity.COMMON);

		LUCKY_SCARF = item("lucky_scarf", Kind.AMULET, Rarity.RARE);
		SCARF_OF_INVISIBILITY = item("scarf_of_invisibility", Kind.AMULET, Rarity.RARE);
		CROSS_NECKLACE = item("cross_necklace", Kind.AMULET, Rarity.UNCOMMON);
		PANIC_NECKLACE = item("panic_necklace", Kind.AMULET, Rarity.COMMON);
		SHOCK_PENDANT = item("shock_pendant", Kind.AMULET, Rarity.UNCOMMON);
		FLAME_PENDANT = item("flame_pendant", Kind.AMULET, Rarity.UNCOMMON);
		THORN_PENDANT = item("thorn_pendant", Kind.AMULET, Rarity.UNCOMMON);

		CHARM_OF_SINKING = item("charm_of_sinking", Kind.CHARM, Rarity.UNCOMMON,
			new Bonus(Attributes.OXYGEN_BONUS, 1.5, Operation.ADD_VALUE));
		CHARM_OF_SHRINKING = item("charm_of_shrinking", Kind.CHARM, Rarity.RARE,
			new Bonus(Attributes.SCALE, -0.5, Operation.ADD_MULTIPLIED_TOTAL));
		CHORUS_TOTEM = item("chorus_totem", Kind.CHARM, Rarity.RARE);

		CLOUD_IN_A_BOTTLE = item("cloud_in_a_bottle", Kind.BELT, Rarity.RARE,
			new Bonus(Attributes.SAFE_FALL_DISTANCE, 3.0, Operation.ADD_VALUE));
		OBSIDIAN_SKULL = item("obsidian_skull", Kind.BELT, Rarity.UNCOMMON);
		ANTIDOTE_VESSEL = item("antidote_vessel", Kind.BELT, Rarity.UNCOMMON);
		LODESTONE_CHARM = item("lodestone_charm", Kind.BELT, Rarity.UNCOMMON);
		CRYSTAL_HEART = item("crystal_heart", Kind.BELT, new Kind[] {Kind.CHARM}, Rarity.RARE,
			new Bonus(Attributes.MAX_HEALTH, 10.0, Operation.ADD_VALUE));
		WIND_BLADDER = item("wind_bladder", Kind.BELT, Rarity.RARE);
		ENDER_POUCH = item("ender_pouch", Kind.BELT, Rarity.RARE);
		JESTERS_CUSHION = item("jesters_cushion", Kind.BELT, Rarity.COMMON);

		DIGGING_CLAWS = item("digging_claws", Kind.WRIST, Rarity.UNCOMMON,
			new Bonus(Attributes.BLOCK_BREAK_SPEED, 0.3, Operation.ADD_MULTIPLIED_BASE));
		FERAL_CLAWS = item("feral_claws", Kind.WRIST, Rarity.UNCOMMON,
			new Bonus(Attributes.ATTACK_SPEED, 0.3, Operation.ADD_MULTIPLIED_BASE));
		IRON_KNUCKLES = item("iron_knuckles", Kind.WRIST, Rarity.RARE,
			new Bonus(Attributes.ATTACK_DAMAGE, 4.0, Operation.ADD_VALUE));
		FIRE_GAUNTLET = item("fire_gauntlet", Kind.WRIST, Rarity.UNCOMMON);
		RAM_BRACER = item("ram_bracer", Kind.WRIST, Rarity.COMMON,
			new Bonus(Attributes.ATTACK_KNOCKBACK, 0.75, Operation.ADD_VALUE));
		VAMPIRIC_GLOVE = item("vampiric_glove", Kind.WRIST, Rarity.RARE);
		GOLDEN_HOOK = item("golden_hook", Kind.WRIST, Rarity.UNCOMMON);
		SMELTERS_BRACER = item("smelters_bracer", Kind.WRIST, Rarity.UNCOMMON);
		WITHERED_BRACELET = item("withered_bracelet", Kind.WRIST, Rarity.UNCOMMON);
		GLUTTONS_RING = item("gluttons_ring", Kind.RING, Rarity.COMMON);

		WATERSTRIDER_BOOTS = item("waterstrider_boots", Kind.FEET, Rarity.UNCOMMON);
		HARE_BOOTS = item("hare_boots", Kind.FEET, Rarity.UNCOMMON,
			new Bonus(Attributes.JUMP_STRENGTH, 0.4, Operation.ADD_MULTIPLIED_BASE),
			new Bonus(Attributes.SAFE_FALL_DISTANCE, 10.0, Operation.ADD_VALUE));
		KITTY_SLIPPERS = item("kitty_slippers", Kind.FEET, Rarity.UNCOMMON);
		COURIERS_BOOTS = item("couriers_boots", Kind.FEET, Rarity.COMMON);
		SNOWSHOES = item("snowshoes", Kind.FEET, Rarity.COMMON);
		STEADFAST_SPIKES = item("steadfast_spikes", Kind.FEET, Rarity.UNCOMMON,
			new Bonus(Attributes.KNOCKBACK_RESISTANCE, 1.0, Operation.ADD_VALUE));
		FROGSKIN_FLIPPERS = item("frogskin_flippers", Kind.FEET, Rarity.COMMON,
			new Bonus(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0, Operation.ADD_VALUE));
		ROOTED_BOOTS = item("rooted_boots", Kind.FEET, Rarity.UNCOMMON);
		STRIDER_SHOES = item("strider_shoes", Kind.FEET, Rarity.UNCOMMON);

		Baubles.picture(Kind.FEET, COURIERS_BOOTS);
		FART = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, JaymosPowers.id("artifacts.fart"),
			SoundEvent.createVariableRangeEvent(JaymosPowers.id("artifacts.fart")));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> ALL.forEach(output::accept));
		ArtifactEffects.init();
		ArtifactLoot.init();
	}

	private static Item item(String id, Kind kind, Rarity rarity, Bonus... bonuses) {
		return item(id, kind, NONE, rarity, bonuses);
	}

	private static Item item(String id, Kind kind, Kind[] also, Rarity rarity, Bonus... bonuses) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key,
			new ArtifactItem(new Item.Properties().setId(key).stacksTo(1).rarity(rarity), id, kind, also, List.of(bonuses)));
		for (Bonus bonus : bonuses) {
			Baubles.touch(bonus.attribute());
		}
		ALL.add(item);
		return item;
	}

	/** Every Artifacts bauble, for the loot pools. */
	public static List<Item> all() {
		return ALL;
	}
}
