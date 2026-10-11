package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

/**
 * Epic Knights (mod id {@value #MOD}: "Epic Knights: Shields, Armor and Weapons", all rights reserved; nothing of it is
 * copied here) dresses each faction's people when it's loaded: kingdoms' guards in plate with their realm's colours on
 * the surcoat and shield, archers in quilted gambesons of that colour, kings in gothic plate under a crowned armet with a
 * mantle, fiefdoms in Norman mail and crusader harness, barbarians in rusted mail and lamellar with clubs and maces, the
 * chief under a horned helm. Everything is found by registry id when a folk is outfitted, so there's no dependency; a
 * piece that's missing (the mod absent, or an id renamed in a later build) is skipped and the role's vanilla gear stays.
 * <p>
 * How Epic Knights decorates armour (read from its 26.1.2 build): a surcoat is a banner's {@code base_color} and
 * {@code banner_patterns} components on the chestplate (its heraldry recipe), as on a shield; dyeable cloth (gambeson,
 * coif, crusader tabard) uses {@code dyed_color}; plumes, crowns, mantles and horns are entries
 * ({@code {name: "magistuarmory:<decoration>", dyeable, color}}) in the {@code Items} list of its
 * {@code magistuarmory:armor_decoration} component (custom data) on the helmet.
 */
public final class KnightsCompat {
	/** Epic Knights' mod id (and item namespace). */
	public static final String MOD = "magistuarmory";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);

	/** Barbarians: chance of each piece of rough gear (else their furs stay), and of a round shield. */
	static final float BARBARIAN_HELM_CHANCE = 0.6F, BARBARIAN_ARMOUR_CHANCE = 0.7F, BARBARIAN_SHIELD_CHANCE = 0.3F;

	private static final Identifier DECORATION_COMPONENT = Identifier.fromNamespaceAndPath(MOD, "armor_decoration");

	/**
	 * One slot of a loadout: the first id that exists (or, {@code random}, any of them); whether it's dyed in the
	 * faction's colour ({@code dyed}) or wears its heraldry ({@code heraldry}, chests and shields); its decorations
	 * (helmets); and the chance it's worn at all.
	 */
	private record Piece(EquipmentSlot slot, List<String> ids, boolean random, boolean dyed, boolean heraldry, List<Decoration> decorations,
		float chance) {
	}

	/**
	 * A helmet decoration: its Epic Knights name, and its colour ({@link #FACTION_COLOUR} for the faction's; every
	 * decoration used here is a dyeable one).
	 */
	private record Decoration(String name, int color) {
	}

	/** A decoration in the faction's colour. */
	private static final int FACTION_COLOUR = -1;
	/** The chief's horns: old bone. */
	static final int HORN_COLOUR = 0xFFE3D8BC;

	/** Loadouts by faction type and role. */
	private static final Map<FactionType, Map<Integer, List<Piece>>> LOADOUTS = new HashMap<>();

	static {
		// a kingdom: knights in plate under their realm's colours
		loadout(FactionType.KINGDOM, Folk.GUARD,
			helm("bascinet", "sallet"),
			chest(true, "platemail_chestplate", "chainmail_chestplate"),
			piece(EquipmentSlot.LEGS, "platemail_leggings", "chainmail_leggings"),
			piece(EquipmentSlot.FEET, "platemail_boots", "chainmail_boots"),
			piece(EquipmentSlot.MAINHAND, "steel_bastardsword", "iron_bastardsword"),
			shield("steel_heatershield", "iron_heatershield", "heatershield"));
		loadout(FactionType.KINGDOM, Folk.ARCHER,
			helm("kettlehat"),
			dyed(EquipmentSlot.CHEST, "gambeson_chestplate"),
			dyed(EquipmentSlot.LEGS, "pantyhose"),
			dyed(EquipmentSlot.FEET, "gambeson_boots"),
			piece(EquipmentSlot.MAINHAND, "longbow"));
		List<Piece> king = List.of(
			helm("armet", List.of(new Decoration("torse_and_mantle", FACTION_COLOUR), new Decoration("crown", FACTION_COLOUR))),
			chest(true, "gothic_chestplate", "knight_chestplate"),
			piece(EquipmentSlot.LEGS, "gothic_leggings", "knight_leggings"),
			piece(EquipmentSlot.FEET, "gothic_boots", "knight_boots"),
			piece(EquipmentSlot.MAINHAND, "netherite_claymore", "noble_sword"));
		loadout(FactionType.KINGDOM, Folk.KING, king);
		loadout(FactionType.FIEFDOM, Folk.KING, king);
		loadout(FactionType.KINGDOM, Folk.LORD,
			helm("grand_bascinet", List.of(new Decoration("big_plume", FACTION_COLOUR))),
			chest(true, "maximilian_chestplate", "knight_chestplate"),
			piece(EquipmentSlot.LEGS, "maximilian_leggings", "knight_leggings"),
			piece(EquipmentSlot.FEET, "maximilian_boots", "knight_boots"),
			piece(EquipmentSlot.MAINHAND, "diamond_bastardsword"),
			shield("diamond_kiteshield", "kiteshield"));
		// a fiefdom: older harness, Norman mail and kite shields, in its overlord's colours
		loadout(FactionType.FIEFDOM, Folk.GUARD,
			helm("norman_helmet", "chainmail_helmet"),
			chest(true, "chainmail_chestplate"),
			piece(EquipmentSlot.LEGS, "chainmail_leggings"),
			piece(EquipmentSlot.FEET, "chainmail_boots"),
			piece(EquipmentSlot.MAINHAND, "iron_morgenstern", "iron_shortsword"),
			shield("iron_kiteshield", "kiteshield"));
		loadout(FactionType.FIEFDOM, Folk.ARCHER,
			new Piece(EquipmentSlot.HEAD, List.of("coif"), false, true, false, List.of(), 1.0F),
			dyed(EquipmentSlot.CHEST, "gambeson_chestplate"),
			dyed(EquipmentSlot.LEGS, "pantyhose"),
			dyed(EquipmentSlot.FEET, "gambeson_boots"),
			piece(EquipmentSlot.MAINHAND, "longbow"));
		loadout(FactionType.FIEFDOM, Folk.LORD,
			helm("greathelm", List.of(new Decoration("two_plumes", FACTION_COLOUR))),
			dyed(EquipmentSlot.CHEST, "crusader_chestplate"),
			piece(EquipmentSlot.LEGS, "crusader_leggings"),
			dyed(EquipmentSlot.FEET, "crusader_boots"),
			piece(EquipmentSlot.MAINHAND, "steel_bastardsword"),
			shield("steel_heatershield", "heatershield"));
		// barbarians: whatever they could scavenge, rusted and rough
		loadout(FactionType.BARBARIAN, Folk.BARBARIAN,
			new Piece(EquipmentSlot.HEAD, List.of("rustedkettlehat", "rustednorman_helmet", "rustedbarbute", "rustedchainmail_helmet"), true, false, false,
				List.of(), BARBARIAN_HELM_CHANCE),
			new Piece(EquipmentSlot.CHEST, List.of("rustedchainmail_chestplate", "rustedhalfarmor_chestplate", "lamellar_chestplate"), true, false, false,
				List.of(), BARBARIAN_ARMOUR_CHANCE),
			new Piece(EquipmentSlot.LEGS, List.of("rustedchainmail_leggings"), false, false, false, List.of(), BARBARIAN_ARMOUR_CHANCE),
			new Piece(EquipmentSlot.FEET, List.of("rustedchainmail_boots", "lamellar_boots"), true, false, false, List.of(), BARBARIAN_ARMOUR_CHANCE),
			new Piece(EquipmentSlot.MAINHAND, List.of("club", "barbedclub", "rusted_heavymace", "rusted_bastardsword", "stone_morgenstern",
				"iron_chainmorgenstern"), true, false, false, List.of(), 1.0F),
			new Piece(EquipmentSlot.OFFHAND, List.of("wood_roundshield", "roundshield"), false, false, false, List.of(), BARBARIAN_SHIELD_CHANCE));
		// the chief: a horned helm, lamellar, a great axe (his war-painted leggings stay)
		loadout(FactionType.BARBARIAN, Folk.CHIEF,
			helm("rustedgreathelm", List.of(new Decoration("viking_horns", HORN_COLOUR))),
			piece(EquipmentSlot.CHEST, "lamellar_chestplate"),
			piece(EquipmentSlot.FEET, "lamellar_boots"),
			piece(EquipmentSlot.MAINHAND, "diamond_lochaberaxe", "iron_lochaberaxe"));
	}

	private KnightsCompat() {
	}

	/** Whether Epic Knights is loaded. */
	public static boolean loaded() {
		return LOADED;
	}

	/**
	 * Dresses {@code folk} (already in its role's vanilla gear) from Epic Knights, if it's loaded and has a loadout for
	 * the faction's type and the folk's role. Called by {@link Folk#setup} after the role's outfit.
	 */
	public static void dress(Folk folk, Faction faction) {
		if (!LOADED) {
			return;
		}
		List<Piece> loadout = LOADOUTS.getOrDefault(faction.type(), Map.of()).get(folk.role());
		if (loadout == null) {
			return;
		}
		MinecraftServer server = folk.level().getServer();
		DyeColor color = server == null ? faction.color() : faction.styleColor(server);
		RandomSource random = folk.getRandom();
		for (Piece piece : loadout) {
			try {
				if (piece.chance() < 1.0F && random.nextFloat() >= piece.chance()) {
					continue;
				}
				ItemStack stack = make(piece, color, random);
				if (stack != null) {
					folk.setItemSlot(piece.slot(), stack);
				}
			} catch (RuntimeException e) {
				// a renamed or reworked item in a later build: keep the vanilla piece
				JaymosKingdoms.LOGGER.debug("Epic Knights piece {} skipped", piece.ids(), e);
			}
		}
	}

	/** The piece's item, dyed and decorated, or null if none of its ids exists. */
	private static @Nullable ItemStack make(Piece piece, DyeColor color, RandomSource random) {
		List<Item> found = new ArrayList<>();
		for (String id : piece.ids()) {
			Item item = item(id);
			if (item != null) {
				found.add(item);
				if (!piece.random()) {
					break;
				}
			}
		}
		if (found.isEmpty()) {
			return null;
		}
		ItemStack stack = new ItemStack(found.get(piece.random() ? random.nextInt(found.size()) : 0));
		int rgb = color.getTextureDiffuseColor() & 0xFFFFFF;
		if (piece.dyed()) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		}
		if (piece.heraldry()) {
			// the realm's banner (a plain field of its colour), as Epic Knights' heraldry recipe puts it on a surcoat or shield
			stack.set(DataComponents.BASE_COLOR, color);
			stack.set(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		}
		if (!piece.decorations().isEmpty()) {
			decorate(stack, piece.decorations(), ARGB.opaque(rgb));
		}
		return stack;
	}

	/** An Epic Knights item by id, or null if it isn't registered. */
	static @Nullable Item item(String id) {
		Identifier key = Identifier.fromNamespaceAndPath(MOD, id);
		if (!BuiltInRegistries.ITEM.containsKey(key)) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(key);
		return item == null || item == Items.AIR ? null : item;
	}

	/** Puts decorations on a helmet through Epic Knights' decoration component; skipped if it isn't (or no longer is) custom data. */
	@SuppressWarnings("unchecked")
	private static void decorate(ItemStack helmet, List<Decoration> decorations, int argb) {
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(DECORATION_COMPONENT);
		if (type == null) {
			return;
		}
		ListTag items = new ListTag();
		for (Decoration decoration : decorations) {
			CompoundTag entry = new CompoundTag();
			entry.putString("name", MOD + ":" + decoration.name());
			entry.putBoolean("dyeable", true);
			entry.putInt("color", decoration.color() == FACTION_COLOUR ? argb : decoration.color());
			items.add(entry);
		}
		CompoundTag tag = new CompoundTag();
		tag.put("Items", items);
		CustomData value = CustomData.of(tag);
		Codec<Object> codec = (Codec<Object>) type.codec();
		try {
			if (codec == null || codec.encodeStart(NbtOps.INSTANCE, value).isError()) {
				return;
			}
		} catch (ClassCastException e) {
			return;
		}
		helmet.set((DataComponentType<Object>) type, value);
	}

	private static void loadout(FactionType type, int role, Piece... pieces) {
		loadout(type, role, List.of(pieces));
	}

	private static void loadout(FactionType type, int role, List<Piece> pieces) {
		LOADOUTS.computeIfAbsent(type, t -> new HashMap<>()).put(role, pieces);
	}

	private static Piece piece(EquipmentSlot slot, String... ids) {
		return new Piece(slot, List.of(ids), false, false, false, List.of(), 1.0F);
	}

	private static Piece dyed(EquipmentSlot slot, String... ids) {
		return new Piece(slot, List.of(ids), false, true, false, List.of(), 1.0F);
	}

	private static Piece chest(boolean heraldry, String... ids) {
		return new Piece(EquipmentSlot.CHEST, List.of(ids), false, false, heraldry, List.of(), 1.0F);
	}

	private static Piece shield(String... ids) {
		return new Piece(EquipmentSlot.OFFHAND, List.of(ids), false, false, true, List.of(), 1.0F);
	}

	private static Piece helm(String... ids) {
		return new Piece(EquipmentSlot.HEAD, List.of(ids), false, false, false, List.of(), 1.0F);
	}

	private static Piece helm(String id, List<Decoration> decorations) {
		return new Piece(EquipmentSlot.HEAD, List.of(id), false, false, false, decorations, 1.0F);
	}
}
