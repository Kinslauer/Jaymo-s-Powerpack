package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.82) The Rig, stage 1: an AI armour suit anyone can wear, salvaged from wrecks and built at the Rig Forge.
 * <ul>
 * <li>Four pieces, one per armour slot ({@link Piece}): the Helm (HUD), the Core (charge, the Mind, palm blasts), the
 * Arms (worn in the legs slot: the frame that powers the arms; shoulder launcher, punch boost, lifting heavy corpses)
 * and the Thrusters (boots: jump boost, hover). Each piece gives its passive alone; the actives ({@link RigActives})
 * need the whole set and charge.</li>
 * <li>Charge lives on the Core ({@link #CHARGE}); see {@link RigCharge} for how it fills.</li>
 * <li>The Mind ({@link RigMind}): the Core's AI, a personality picked once ({@link #MIND}), speaking short warnings.</li>
 * <li>Wrecks ({@link RigWrecks}): Dead Rigs in the ruined future, Fallen Rigs in the Nether and the End.</li>
 * <li>Keys: with no power, R hover, G palm blast, V shoulder launcher, B the Mind's report. With a power those stay the
 * power's; J uses the selected Rig active and Sneak+J picks the next (client: {@code RigClient}).</li>
 * <li>Each power adds one small augment ({@link Augment}).</li>
 * </ul>
 * (0.11.87) Stage 2, the Mind grows up: it levels 1 to {@link #MAX_LEVEL} on the Core ({@link #XP}; see {@link RigLevels}),
 * and each level unlocks one more thing: 2 lock-on, 3 scans ({@link RigScan}) and suit calling ({@link RigCall}), 4 the
 * takeover save ({@link RigTakeover}), 5 full flight (hover, then double-tap Jump). Creative counts as level 5.
 */
public final class Rig {
	/** The Core's charge: full, and what a newly forged Core holds. */
	public static final int MAX_CHARGE = 1000, NEW_CORE_CHARGE = 250;
	/** Armour points per piece (iron-like), the material's durability multiplier, toughness. */
	static final int HELM_ARMOR = 2, CORE_ARMOR = 6, ARMS_ARMOR = 5, THRUSTERS_ARMOR = 2, DURABILITY = 25;
	static final float TOUGHNESS = 1.0F;

	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("rig"));
	/** Rig Salvage and Fallen Rig Scrap: what the Forge builds from, and repairs the pieces at an anvil. */
	public static final TagKey<Item> SALVAGE_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("rig_salvage"));

	/** On a Rig Core: its charge (0..{@link #MAX_CHARGE}). */
	public static DataComponentType<Integer> CHARGE;
	/** On a Rig Core: the Mind's personality, picked when it's first worn ({@link RigMind.Personality}). */
	public static DataComponentType<String> MIND;
	/** (0.11.87) On a Rig Core: the Mind's experience ({@link RigLevels}). */
	public static DataComponentType<Integer> XP;
	/** (0.11.87) On a Rig Core: the game time its takeover save is ready again ({@link RigTakeover}). */
	public static DataComponentType<Long> TAKEOVER_READY;

	/** (0.11.87) The Mind's levels: the experience each one starts at (level 1 at 0). */
	public static final int MAX_LEVEL = 5;
	public static final int[] LEVEL_XP = {0, 100, 300, 700, 1400};
	/** (0.11.87) The level each stage-2 feature needs. */
	public static final int LOCK_LEVEL = 2, SCAN_LEVEL = 3, CALL_LEVEL = 3, TAKEOVER_LEVEL = 4, FLIGHT_LEVEL = 5;

	public static Item HELM, CORE, ARMS, THRUSTERS, SALVAGE, SCRAP;
	public static Block FORGE, DEAD_RIG, FALLEN_RIG;

	/** The four pieces and the slot each is worn in. */
	public enum Piece {
		HELM(EquipmentSlot.HEAD), CORE(EquipmentSlot.CHEST), ARMS(EquipmentSlot.LEGS), THRUSTERS(EquipmentSlot.FEET);

		public final EquipmentSlot slot;

		Piece(EquipmentSlot slot) {
			this.slot = slot;
		}

		public Item item() {
			return switch (this) {
				case HELM -> Rig.HELM;
				case CORE -> Rig.CORE;
				case ARMS -> Rig.ARMS;
				case THRUSTERS -> Rig.THRUSTERS;
			};
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * One small power-themed twist on the Rig for each power (lang {@code rig.jaymospowers.augment.<power>}). The numbers
	 * are read where they apply.
	 */
	public enum Augment {
		/** Wind: the thrusters' gusts last longer (hover drains less). */
		WIND,
		/** Gravity: a heavier punch boost. */
		GRAVITY,
		/** Chrono: faster recharge. */
		CHRONO,
		/** Web: palm blasts web the target (slowed). */
		WEB,
		/** Arsenal: the launcher fires one more missile. */
		ARSENAL,
		/** Resonance: palm blasts knock back harder. */
		RESONANCE,
		/** Darktide: the Mind feels mobs behind you from farther off. */
		DARKTIDE,
		/** Smoke: palm blasts leave the target blinded for a moment. */
		SMOKE,
		/** Xenoform: palm blasts reach farther. */
		XENOFORM,
		/** Z Fighter: the punch boost knocks back harder. */
		ZFIGHTER;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static @Nullable Augment byPower(@Nullable Power power) {
			if (power == null) {
				return null;
			}
			for (Augment augment : values()) {
				if (augment.id().equals(power.id().getPath())) {
					return augment;
				}
			}
			return null;
		}
	}

	private Rig() {
	}

	public static void init() {
		CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rig_charge"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		MIND = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rig_mind"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		XP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rig_xp"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		TAKEOVER_READY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rig_takeover"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		defense.put(ArmorType.HELMET, HELM_ARMOR);
		defense.put(ArmorType.CHESTPLATE, CORE_ARMOR);
		defense.put(ArmorType.LEGGINGS, ARMS_ARMOR);
		defense.put(ArmorType.BOOTS, THRUSTERS_ARMOR);
		defense.put(ArmorType.BODY, CORE_ARMOR);
		ArmorMaterial material = new ArmorMaterial(DURABILITY, defense, 0, SoundEvents.ARMOR_EQUIP_NETHERITE, TOUGHNESS, 0.0F, SALVAGE_TAG, ASSET);
		HELM = item("rig_helm", p -> new RigItem(Piece.HELM, p.humanoidArmor(material, ArmorType.HELMET).rarity(Rarity.UNCOMMON)));
		CORE = item("rig_core", p -> new RigItem(Piece.CORE, p.humanoidArmor(material, ArmorType.CHESTPLATE).rarity(Rarity.UNCOMMON)
			.component(CHARGE, NEW_CORE_CHARGE)));
		ARMS = item("rig_arms", p -> new RigItem(Piece.ARMS, p.humanoidArmor(material, ArmorType.LEGGINGS).rarity(Rarity.UNCOMMON)));
		THRUSTERS = item("rig_thrusters", p -> new RigItem(Piece.THRUSTERS, p.humanoidArmor(material, ArmorType.BOOTS).rarity(Rarity.UNCOMMON)));
		SALVAGE = item("rig_salvage", p -> new SalvageItem(false, p));
		SCRAP = item("fallen_rig_scrap", p -> new SalvageItem(true, p));

		FORGE = block("rig_forge", p -> new RigForgeBlock(p.mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.NETHERITE_BLOCK)));
		DEAD_RIG = block("dead_rig", p -> new RigWreckBlock(false, p.mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F)
			.sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
		FALLEN_RIG = block("fallen_rig", p -> new RigWreckBlock(true, p.mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F)
			.sound(SoundType.NETHERITE_BLOCK).noOcclusion()));
		item("rig_forge", p -> new BlockItem(FORGE, p.useBlockDescriptionPrefix()));
		item("dead_rig", p -> new BlockItem(DEAD_RIG, p.useBlockDescriptionPrefix()));
		item("fallen_rig", p -> new BlockItem(FALLEN_RIG, p.useBlockDescriptionPrefix()));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(HELM);
			output.accept(CORE);
			output.accept(ARMS);
			output.accept(THRUSTERS);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(SALVAGE);
			output.accept(SCRAP);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(FORGE);
			output.accept(DEAD_RIG);
			output.accept(FALLEN_RIG);
		});

		RigNet.init();
		RigCharge.init();
		RigActives.init();
		RigPassives.init();
		RigWrecks.init();
		RigLevels.init();
		RigCall.init();
		ServerTickEvents.END_SERVER_TICK.register(Rig::tick);
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of().setId(key)));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean any = wearsAny(player);
			if (any && !player.isSpectator()) {
				RigCharge.tick(player);
				RigMind.tick(player);
				RigLevels.tick(player);
				RigTakeover.tick(player);
			}
			RigCall.tick(player);
			RigPassives.tick(player, any && !player.isSpectator());
			RigActives.tick(player, any && !player.isSpectator());
		}
		RigActives.tickMissiles(server);
		RigWrecks.tick(server);
	}

	// ---- what's worn --------------------------------------------------------------------------------------------------

	/** Whether this piece is worn (either side). */
	public static boolean wears(Player player, Piece piece) {
		return piece.item() != null && player.getItemBySlot(piece.slot).is(piece.item());
	}

	/** Whether any Rig piece is worn. */
	public static boolean wearsAny(Player player) {
		for (Piece piece : Piece.values()) {
			if (wears(player, piece)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the whole set is worn: what the actives need. */
	public static boolean fullSet(Player player) {
		for (Piece piece : Piece.values()) {
			if (!wears(player, piece)) {
				return false;
			}
		}
		return true;
	}

	/** The worn Core, or an empty stack. */
	public static ItemStack core(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		return CORE != null && chest.is(CORE) ? chest : ItemStack.EMPTY;
	}

	/** (0.11.87) A Core's Mind level (1..{@link #MAX_LEVEL}; 0 for an empty stack). */
	public static int level(ItemStack core) {
		if (core.isEmpty()) {
			return 0;
		}
		int xp = core.getOrDefault(XP, 0);
		int level = 1;
		for (int i = 1; i < LEVEL_XP.length; i++) {
			if (xp >= LEVEL_XP[i]) {
				level = i + 1;
			}
		}
		return level;
	}

	/** (0.11.87) The worn Core's Mind level (0 with no Core; creative: {@link #MAX_LEVEL}). Both sides. */
	public static int level(Player player) {
		ItemStack core = core(player);
		return core.isEmpty() ? 0 : player.isCreative() ? MAX_LEVEL : level(core);
	}

	/** (0.11.87) How far the worn Core is through its level (0..1; 1 at the top level). */
	public static float levelProgress(Player player) {
		ItemStack core = core(player);
		int level = level(core);
		if (level <= 0 || level >= MAX_LEVEL) {
			return level >= MAX_LEVEL ? 1.0F : 0.0F;
		}
		int xp = core.getOrDefault(XP, 0);
		return Mth.clamp((xp - LEVEL_XP[level - 1]) / (float)(LEVEL_XP[level] - LEVEL_XP[level - 1]), 0.0F, 1.0F);
	}

	/** The worn Core's charge (0 with no Core). */
	public static int charge(Player player) {
		ItemStack core = core(player);
		return core.isEmpty() ? 0 : core.getOrDefault(CHARGE, 0);
	}

	/** The worn Core's charge as a share of full (0..1). */
	public static float chargeShare(Player player) {
		return charge(player) / (float)MAX_CHARGE;
	}

	/** Spends {@code amount} of the worn Core's charge (server); false, spending nothing, if there isn't that much. */
	static boolean spend(Player player, int amount) {
		ItemStack core = core(player);
		if (core.isEmpty()) {
			return false;
		}
		if (player.isCreative()) {
			return true;
		}
		int have = core.getOrDefault(CHARGE, 0);
		if (have < amount) {
			return false;
		}
		core.set(CHARGE, have - amount);
		return true;
	}

	/** Adds charge to the worn Core (server), up to full; returns how much went in. */
	static int addCharge(Player player, int amount) {
		ItemStack core = core(player);
		if (core.isEmpty() || amount <= 0) {
			return 0;
		}
		int have = core.getOrDefault(CHARGE, 0);
		int now = Mth.clamp(have + amount, 0, MAX_CHARGE);
		if (now != have) {
			core.set(CHARGE, now);
		}
		return now - have;
	}

	/** This wearer's power's augment, or null without a power. */
	public static @Nullable Augment augment(Player player) {
		return Augment.byPower(Powers.of(player));
	}

	/**
	 * (Costumes) The worn Helm hides the face: the name to show for its wearer (the Helm's own name if renamed, else
	 * "a Rig pilot"), or null without a Helm.
	 */
	public static @Nullable Component helmIdentity(Player player) {
		if (!wears(player, Piece.HELM)) {
			return null;
		}
		ItemStack helm = player.getItemBySlot(EquipmentSlot.HEAD);
		Component custom = helm.getCustomName();
		return custom != null ? custom : Component.translatable("rig.jaymospowers.identity");
	}

	/** Whether this player is hovering on their thrusters (server): they mustn't be kicked for floating. */
	public static boolean hovering(Player player) {
		return RigActives.hovering(player);
	}

	/** (0.11.87) Whether this player is in the Rig's full flight (server). */
	public static boolean flying(Player player) {
		return RigActives.flying(player);
	}
}
