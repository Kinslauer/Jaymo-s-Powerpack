package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;

/**
 * Jaymo's Armory: weapons from the Stone Age to the pirates, rebuilt for 26.3 after Age of Weapons - Reforged
 * (by XxRexRaptorxX, MIT; its textures are reused, see textures/item/armory/LICENSE-AgeOfWeapons.txt). Nothing past the
 * age of sail. Better Combat gives each its swings (data/jaymospowers/weapon_attributes, by type); the assets, recipes
 * and tags are written by tools/armory_assets.py, which keeps the same ids as {@link #WEAPONS} and the singles below.
 * <ul>
 * <li>Daggers, hatchets, primitive spears, throwing sticks and the harpoon are held back and thrown like a trident
 * ({@link ThrowingWeaponItem}); kunai and throwing stars fly at a flick and stack. (0.18.64) Knives parry rather than
 * throw, and Balkon's knife and our dagger swap held damage ({@link KnifeGuard}).</li>
 * <li>The lance, couched on a moving mount, hits harder the faster you ride ({@link #LANCE_CHARGE}).</li>
 * <li>The flanged mace and the war axe knock a raised shield aside, like an axe.</li>
 * <li>The machete cuts through leaves, vines and crops fast.</li>
 * <li>The round shield blocks like a shield and comes up a little quicker.</li>
 * <li>The recurve bow shoots harder than a plain bow ({@link RecurveBowItem#POWER}).</li>
 * <li>(0.15.2) The rapier lunges on right-click and runs through the first foe ahead ({@link RapierItem}).</li>
 * <li>(0.15.148) The leather whip cracks, wraps and hauls; Balkon's flail whirls and comes down overhead ({@link Lash}).</li>
 * </ul>
 */
public final class Armory {
	/** Lance: the most it adds on a full gallop; the mount speed (blocks/tick) for the full bonus; below this, none. */
	static final float LANCE_CHARGE = 8.0F;
	static final double LANCE_FULL_SPEED = 0.4, LANCE_MIN_SPEED = 0.08;
	/** Lance: how hard a charge throws its target back, at full speed. */
	static final double LANCE_KNOCKBACK = 1.2;
	/** Flanged mace: seconds a shield it strikes stays down (axes: 5). */
	static final float MACE_SHIELD_BREAK = 4.0F;
	/** Machete: how fast it cuts leaves, vines and plants. */
	static final float MACHETE_CUT_SPEED = 10.0F;
	/** Round shield: seconds to raise it (vanilla shield 0.25), and how wide it covers (degrees). */
	static final float SHIELD_RAISE = 0.15F, SHIELD_ARC = 90.0F;

	/** How a weapon is built: a blade or club ({@code sword}), or an axe ({@code axe}: chops wood too). */
	enum Style { SWORD, AXE, SHIELD }

	/**
	 * A tiered weapon: damage and speed over the material's (as {@code Item.Properties.sword/axe}), and what a throw does
	 * ({@code thrown} > 0: held back and thrown, for that much plus the material's bonus).
	 */
	record Weapon(String id, Style style, float damage, float speed, float thrown) {
	}

	/** Each tier: our id prefix and its material. */
	enum Tier {
		WOODEN("wooden", ToolMaterial.WOOD, 200), STONE("stone", ToolMaterial.STONE, 250), IRON("iron", ToolMaterial.IRON, 336),
		GOLDEN("golden", ToolMaterial.GOLD, 150), DIAMOND("diamond", ToolMaterial.DIAMOND, 600),
		NETHERITE("netherite", ToolMaterial.NETHERITE, 800);

		final String prefix;
		final ToolMaterial material;
		/** A round shield of this tier lasts this long. */
		final int shieldDurability;

		Tier(String prefix, ToolMaterial material, int shieldDurability) {
			this.prefix = prefix;
			this.material = material;
			this.shieldDurability = shieldDurability;
		}
	}

	static final List<Weapon> WEAPONS = List.of(
		// antiquity
		new Weapon("dagger", Style.SWORD, 1.5F, -1.8F, 4.0F),
		new Weapon("hatchet", Style.AXE, 4.0F, -2.8F, 6.0F),
		new Weapon("skewer", Style.SWORD, 2.5F, -2.2F, 0.0F),
		new Weapon("round_shield", Style.SHIELD, 0.0F, 0.0F, 0.0F),
		// middle ages
		new Weapon("longsword", Style.SWORD, 4.0F, -2.7F, 0.0F),
		new Weapon("glaive", Style.SWORD, 4.5F, -3.0F, 0.0F),
		new Weapon("lance", Style.SWORD, 3.5F, -3.2F, 0.0F),
		new Weapon("war_axe", Style.AXE, 7.0F, -3.2F, 0.0F),
		new Weapon("war_scythe", Style.SWORD, 5.0F, -3.0F, 0.0F),
		new Weapon("flanged_mace", Style.SWORD, 4.5F, -2.9F, 0.0F),
		new Weapon("broadsword", Style.SWORD, 3.5F, -2.5F, 0.0F),
		new Weapon("claymore", Style.SWORD, 5.5F, -3.0F, 0.0F),
		new Weapon("cleaver", Style.SWORD, 4.5F, -2.8F, 0.0F),
		// Japan and the sea
		new Weapon("ninjato", Style.SWORD, 2.5F, -2.1F, 0.0F),
		new Weapon("saber", Style.SWORD, 3.0F, -2.3F, 0.0F),
		new Weapon("machete", Style.SWORD, 2.5F, -2.2F, 0.0F),
		// (0.15.2) the duellist's rapier: lunges on right-click (RapierItem)
		new Weapon("rapier", Style.SWORD, 2.0F, -2.0F, 0.0F));

	/** Every Armory item, by id, in the order the creative tab lists them. */
	static final Map<String, Item> ITEMS = new java.util.LinkedHashMap<>();
	static final Map<Tier, Item> LANCES = new EnumMap<>(Tier.class);
	/** (Strains C) The bomb glider's item: placed like a boat, it's the {@link BombGlider}. */
	public static Item BOMB_GLIDER;
	/**
	 * A weighted string net. Rogues &amp; Warriors' Throw Net spends one per cast (compat/SpellThrows); Andy: "make a net
	 * item crafted from string".
	 */
	public static Item THROWING_NET;
	/** (0.18.75) The Fang Gauntlets: worn on the hands when held (client/armory/FangGauntletsClient). */
	public static Item FANG_GAUNTLETS;
	/** (BWM port) The mining charge ({@link MiningChargeBlock}). */
	public static Block MINING_CHARGE;
	public static final ResourceKey<EquipmentAsset> CAVEMAN = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("caveman"));

	/** (0.10.13) A quarterstaff in a wind sorcerer's hand rides the gale (WindStaff). */
	private static final ItemLore STAFF_LORE = new ItemLore(List.of(
		Component.translatable("item.jaymospowers.quarterstaff.wind").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));

	private Armory() {
	}

	public static void init() {
		ArmoryEntities.init();
		KnifeGuard.init();
		DaggerHarvest.init();
		for (Weapon weapon : WEAPONS) {
			for (Tier tier : Tier.values()) {
				Item item = tiered(weapon, tier);
				if (weapon.id().equals("lance")) {
					LANCES.put(tier, item);
				}
			}
		}
		// the Stone Age
		throwing("primitive_spear", p -> p.sword(ToolMaterial.STONE, 2.5F, -2.8F).repairable(net.minecraft.world.item.Items.FLINT), 6.0F, false);
		item("primitive_knife", Item::new, p -> p.sword(ToolMaterial.STONE, 1.0F, -1.8F).repairable(net.minecraft.world.item.Items.FLINT));
		item("bone_knife", Item::new, p -> p.sword(ToolMaterial.STONE, 1.0F, -1.6F).repairable(net.minecraft.world.item.Items.BONE));
		THROWING_NET = item("throwing_net", Item::new, p -> p.stacksTo(16));
		item("hand_axe", Item::new, p -> p.axe(ToolMaterial.STONE, 5.0F, -3.0F).repairable(net.minecraft.world.item.Items.FLINT));
		item("wooden_cudgel", Item::new, p -> p.sword(ToolMaterial.WOOD, 3.0F, -2.8F));
		item("stone_cudgel", Item::new, p -> p.sword(ToolMaterial.STONE, 3.0F, -2.8F));
		throwing("throwing_stick", p -> p.sword(ToolMaterial.WOOD, 0.5F, -2.0F), 3.0F, true);
		item("wooden_quarterstaff", Item::new, p -> staffBlock(p.sword(ToolMaterial.WOOD, 2.0F, -2.0F).component(DataComponents.LORE, STAFF_LORE)));
		item("iron_quarterstaff", Item::new, p -> staffBlock(p.sword(ToolMaterial.IRON, 2.0F, -2.0F).component(DataComponents.LORE, STAFF_LORE)));
		// the Twinblade: two swords joined at the hilt (Twinblade), and the sheet that teaches the joining
		Twinblade.item = item("twinblade", Item::new, p -> staffBlock(p.sword(ToolMaterial.IRON, 4.0F, Twinblade.ATTACK_SPEED)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.twinblade.hint").withStyle(ChatFormatting.GRAY),
				Component.translatable("item.jaymospowers.twinblade.old").withStyle(ChatFormatting.DARK_GRAY))))));
		Twinblade.schematic = item("twinblade_schematic", Item::new, p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.twinblade_schematic.hint").withStyle(ChatFormatting.GRAY),
				Component.translatable("item.jaymospowers.twinblade_schematic.old").withStyle(ChatFormatting.DARK_GRAY)))));
		item("caveman_helmet", Item::new, p -> p.humanoidArmor(cavemanHide(), ArmorType.HELMET));
		item("caveman_chestplate", Item::new, p -> p.humanoidArmor(cavemanHide(), ArmorType.CHESTPLATE));
		item("caveman_leggings", Item::new, p -> p.humanoidArmor(cavemanHide(), ArmorType.LEGGINGS));
		item("caveman_boots", Item::new, p -> p.humanoidArmor(cavemanHide(), ArmorType.BOOTS));
		// antiquity's bow, Japan's throwing irons, the whalers' harpoon
		item("recurve_bow", RecurveBowItem::new, p -> p.durability(RecurveBowItem.DURABILITY).enchantable(1));
		// (Smithing) its steel broadhead
		item("broadhead_arrow", BroadheadArrowItem::new, p -> p.component(DataComponents.LORE, new ItemLore(
			List.of(Component.translatable("item.jaymospowers.broadhead_arrow.hint").withStyle(ChatFormatting.GRAY)))));
		BroadheadArrowItem.init();
		item("kunai", props -> new ThrowingWeaponItem(props, 4.0F, false, false),
			p -> p.stacksTo(16).attributes(net.minecraft.world.item.component.ItemAttributeModifiers.builder()
				.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					Item.BASE_ATTACK_DAMAGE_ID, 2.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
					net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
				.build()));
		item("throwing_star", props -> new ThrowingWeaponItem(props, 3.0F, true, false), p -> p.stacksTo(16));
		throwing("harpoon", p -> p.sword(ToolMaterial.IRON, 3.5F, -2.9F), 8.0F, false);
		// (0.18.47) the Fang Gauntlets: claws for Weapon Forms' Claw family (Kingdoms' form_claws tag); forged only (tinkering/WeaponParts)
		FANG_GAUNTLETS = item("fang_gauntlets", Item::new, p -> p.sword(ToolMaterial.IRON, 2.5F, -1.6F));
		// (Havens patch 5) the kings' weapons: from the iron heads Kingdoms drops off fallen kings, at the Tool Station only
		KingWeapons.GUTTING_HOOK = item("gutting_hook", p -> new KingWeapons.KingWeapon(p, KingWeapons.Kind.HOOK), KingWeapons::hook);
		KingWeapons.WARCHIEF_CLEAVER = item("warchief_cleaver", p -> new KingWeapons.KingWeapon(p, KingWeapons.Kind.CLEAVER), KingWeapons::cleaver);
		KingWeapons.OGRE_KINGS_MAUL = item("ogre_kings_maul", p -> new KingWeapons.KingWeapon(p, KingWeapons.Kind.MAUL), KingWeapons::maul);
		// (0.20.18) the patrons' weapons: iron heads Kingdoms drops off the champions, at the Tool Station only
		PatronWeapons.HEARTHGUARD = item("hearthguard", p -> new PatronWeaponItem(p, PatronWeapons.Kind.HEARTHGUARD), p -> PatronWeapons.props(PatronWeapons.Kind.HEARTHGUARD, p));
		PatronWeapons.ANVIL_FATHERS_HAMMER = item("anvil_fathers_hammer", p -> new PatronWeaponItem(p, PatronWeapons.Kind.ANVIL_HAMMER), p -> PatronWeapons.props(PatronWeapons.Kind.ANVIL_HAMMER, p));
		PatronWeapons.TIDECALLER = item("tidecaller", p -> new PatronWeaponItem(p, PatronWeapons.Kind.TIDECALLER), p -> PatronWeapons.props(PatronWeapons.Kind.TIDECALLER, p));
		PatronWeapons.HUNTSMANS_SPEAR = item("huntsmans_spear", p -> new PatronWeaponItem(p, PatronWeapons.Kind.HUNTSMANS_SPEAR), p -> PatronWeapons.props(PatronWeapons.Kind.HUNTSMANS_SPEAR, p));
		PatronWeapons.GRAVELIGHT = item("gravelight", p -> new PatronWeaponItem(p, PatronWeapons.Kind.GRAVELIGHT), p -> PatronWeapons.props(PatronWeapons.Kind.GRAVELIGHT, p));
		PatronWeapons.SUNBRAND = item("sunbrand", p -> new PatronWeaponItem(p, PatronWeapons.Kind.SUNBRAND), p -> PatronWeapons.props(PatronWeapons.Kind.SUNBRAND, p));
		PatronWeapons.ASHFANG = item("ashfang", p -> new PatronWeaponItem(p, PatronWeapons.Kind.ASHFANG), p -> PatronWeapons.props(PatronWeapons.Kind.ASHFANG, p));
		PatronWeapons.PLAIN_WORD = item("plain_word", p -> new PatronWeaponItem(p, PatronWeapons.Kind.PLAIN_WORD), p -> PatronWeapons.props(PatronWeapons.Kind.PLAIN_WORD, p));
		// (0.21.19) the Banes' weapons (Kingdoms Rivals patch 6b): heads taken from an ended Bane, at the Tool Station only
		BaneWeapons.registerComponents();
		BaneWeapons.GRAVESWORN = item("gravesworn", p -> new BaneWeapons.BaneWeaponItem(p, BaneWeapons.Kind.GRAVESWORN), p -> BaneWeapons.props(BaneWeapons.Kind.GRAVESWORN, p));
		BaneWeapons.GRUDGE = item("grudge", p -> new BaneWeapons.BaneWeaponItem(p, BaneWeapons.Kind.GRUDGE), p -> BaneWeapons.props(BaneWeapons.Kind.GRUDGE, p));
		BaneWeapons.HORNSCYTHE = item("hornscythe", p -> new BaneWeapons.BaneWeaponItem(p, BaneWeapons.Kind.HORNSCYTHE), p -> BaneWeapons.props(BaneWeapons.Kind.HORNSCYTHE, p));
		// (0.15.148) the single-tail leather whip: cracks, wraps and hauls (Lash)
		item("leather_whip", LeatherWhipItem::new, p -> p.durability(LeatherWhipItem.DURABILITY).repairable(net.minecraft.world.item.Items.LEATHER)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.leather_whip.hint").withStyle(ChatFormatting.GRAY)))));
		// (Strains C) the bomb glider: a bat-winged board that drops dynamite
		BOMB_GLIDER = item("bomb_glider", BombGliderItem::new, p -> p.stacksTo(1));
		BombGliderNet.init();
		// (BWM port) the mining charge: stuck to a block, it digs a 3x3x3 hole into it
		ResourceKey<Block> chargeKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("mining_charge"));
		MINING_CHARGE = Registry.register(BuiltInRegistries.BLOCK, chargeKey, new MiningChargeBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.FIRE).instabreak().sound(SoundType.GRASS).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)
			.setId(chargeKey)));
		item("mining_charge", p -> new BlockItem(MINING_CHARGE, p), p -> p.useBlockDescriptionPrefix().component(DataComponents.LORE, new ItemLore(
			List.of(Component.translatable("block.jaymospowers.mining_charge.hint").withStyle(ChatFormatting.GRAY)))));

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("armory"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.armory"))
			.icon(() -> new ItemStack(ITEMS.get("iron_longsword")))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> ITEMS.values().forEach(output::accept));

		ArmoryHits.init();
		RapierItem.init();
		PatronWeapons.init();
		Lash.init();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
				&& entity.level() instanceof ServerLevel level && isLance(attacker.getMainHandItem())) {
				charge(level, attacker, entity);
			}
		});
	}

	/**
	 * (0.8.76) One of our melee weapons, which Jaymo's Tinkering's Tool Station gives modifiers: damageable, held alone,
	 * not armour or the bow.
	 */
	public static boolean isWeapon(ItemStack stack) {
		return ITEMS.containsValue(stack.getItem()) && stack.isDamageableItem() && stack.getMaxStackSize() == 1
			&& !stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE) && !(stack.getItem() instanceof RecurveBowItem);
	}

	static boolean isLance(ItemStack stack) {
		return LANCES.containsValue(stack.getItem());
	}

	/** A couched lance: the faster the mount, the harder it lands, and the further it throws the target back. */
	private static void charge(ServerLevel level, LivingEntity rider, LivingEntity target) {
		Entity mount = rider.getVehicle();
		if (mount == null || !target.isAlive()) {
			return;
		}
		Vec3 move = mount.getKnownMovement();
		double speed = Math.max(move.horizontalDistance(), mount.position().subtract(mount.xo, mount.yo, mount.zo).horizontalDistance());
		if (speed < LANCE_MIN_SPEED) {
			return;
		}
		double share = Math.min(1.0, (speed - LANCE_MIN_SPEED) / (LANCE_FULL_SPEED - LANCE_MIN_SPEED));
		target.setInvulnerableTime(0);
		target.hurtServer(level, rider instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(rider),
			(float) (LANCE_CHARGE * share));
		Vec3 push = move.horizontalDistanceSqr() > 1.0E-4 ? move.normalize() : rider.getLookAngle();
		target.push(push.x * LANCE_KNOCKBACK * share, 0.25 * share, push.z * LANCE_KNOCKBACK * share);
		target.syncVelocity = true;
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), (int) (6 + 10 * share),
			0.3, 0.3, 0.3, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** (0.13.32) A quarterstaff held up blocks like a shield, from the front only ({@link ArmoryHits}). */
	private static Item.Properties staffBlock(Item.Properties p) {
		return p.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(ArmoryHits.STAFF_RAISE, 1.0F,
			List.of(new BlocksAttacks.DamageReduction(ArmoryHits.STAFF_ARC, Optional.empty(), 0.0F, 1.0F)),
			new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
			Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
			Optional.of(SoundEvents.SHIELD_BLOCK), Optional.empty()));
	}

	private static Item tiered(Weapon weapon, Tier tier) {
		String id = tier.prefix + "_" + weapon.id();
		ToolMaterial m = tier.material;
		Function<Item.Properties, Item.Properties> props = switch (weapon.style()) {
			case AXE -> p -> p.axe(m, weapon.damage(), weapon.speed());
			case SWORD -> p -> p.sword(m, weapon.damage(), weapon.speed());
			case SHIELD -> p -> p.durability(tier.shieldDurability)
				.repairable(m.repairItems())
				.enchantable(m.enchantmentValue())
				.equippableUnswappable(EquipmentSlot.OFFHAND)
				.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(SHIELD_RAISE, 1.0F,
					List.of(new BlocksAttacks.DamageReduction(SHIELD_ARC, Optional.empty(), 0.0F, 1.0F)),
					new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
					Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
					Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
				.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
		};
		if (weapon.id().equals("flanged_mace")) {
			props = props.andThen(p -> p.component(DataComponents.WEAPON, new net.minecraft.world.item.component.Weapon(1, MACE_SHIELD_BREAK)));
		} else if (weapon.id().equals("lance")) {
			props = props.andThen(p -> p.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.lance.charge").withStyle(ChatFormatting.GRAY)))));
		} else if (weapon.id().equals("machete")) {
			props = props.andThen(p -> p.component(DataComponents.TOOL, macheteTool()));
		} else if (weapon.id().equals("rapier")) {
			props = props.andThen(p -> p.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.rapier.lunge").withStyle(ChatFormatting.GRAY)))));
		}
		if (tier == Tier.NETHERITE) {
			props = props.andThen(Item.Properties::fireResistant);
		}
		if (weapon.thrown() > 0.0F) {
			float thrown = weapon.thrown() + m.attackDamageBonus();
			boolean spins = weapon.id().equals("hatchet");
			return item(id, p -> new ThrowingWeaponItem(p, thrown, spins, true), props);
		}
		return item(id, weapon.id().equals("rapier") ? RapierItem::new : Item::new, props);
	}

	private static Item throwing(String id, Function<Item.Properties, Item.Properties> props, float thrown, boolean spins) {
		return item(id, p -> new ThrowingWeaponItem(p, thrown, spins, true), props);
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
		ITEMS.put(id, item);
		return item;
	}

	/** A sword's cuts, but leaves, vines and crops go down fast. */
	private static Tool macheteTool() {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return new Tool(List.of(
			Tool.Rule.minesAndDrops(HolderSet.direct(new Holder[] {Blocks.COBWEB.builtInRegistryHolder()}), 15.0F),
			Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
			Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.LEAVES), MACHETE_CUT_SPEED),
			Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_EFFICIENT), MACHETE_CUT_SPEED)), 1.0F, 2, false);
	}

	private static ArmorMaterial caveman;

	/** Hide and bone: leather's protection, a little tougher-wearing. */
	private static ArmorMaterial cavemanHide() {
		if (caveman == null) {
			Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
			defense.put(ArmorType.BOOTS, 1);
			defense.put(ArmorType.LEGGINGS, 2);
			defense.put(ArmorType.CHESTPLATE, 3);
			defense.put(ArmorType.HELMET, 1);
			defense.put(ArmorType.BODY, 3);
			caveman = new ArmorMaterial(7, defense, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR, CAVEMAN);
		}
		return caveman;
	}
}
