package com.jaymoskingdoms;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.jspecify.annotations.Nullable;

/**
 * How each faction's people look in vanilla gear, so two realms side by side are told apart at a glance and the king is
 * unmistakable. Applied by {@link Folk#setup} after the role's outfit and before {@link KnightsCompat} (whose pieces,
 * when Epic Knights is loaded, replace these slot by slot).
 * <ul>
 * <li><b>Arms</b> ({@link Heraldry}): every realm has a coat of arms worked out from its id: its colour for the field,
 * a contrasting tincture, one of {@link #ORDINARIES} (a cross, a saltire, a chevron, a bend...), an armour trim
 * pattern, and a way of arming its guards (plate, mail under a tabard, or half-plate). The trim's material follows the
 * colour (red: redstone, blue: lapis, green: emerald...). A fiefdom bears its overlord's arms within a border.</li>
 * <li><b>Rank</b>: guards in iron or mail with the realm's trim and a shield of its arms (some with spears); archers in
 * leather of its colours; a lord in trimmed diamond, bareheaded but for his circlet, with his shield; the king in a
 * gold crown set with the realm's gem (glinting), netherite trimmed in gold with the realm's pattern, gold boots, a
 * glinting sword and the arms on his shield. Builders wear the land's headgear.</li>
 * <li><b>Land</b> ({@link Climate}): linen turbans, sand-coloured trousers and gold trim in the desert and the badlands;
 * white hoods and furs in the snow; dark leather boots in the taiga; green leather and spears in the jungle; murky
 * leather in the swamp; straw-coloured caps on the savanna; (0.7.250) the ronin harness among the cherry trees.</li>
 * <li><b>Camps</b>: barbarians' hides and their chief's mail get rough trims of their camp.</li>
 * </ul>
 * Folk dressed before this ({@link #VERSION}) are dressed again the first time they're ticked ({@link Folk}).
 */
public final class Outfits {
	/** Bumped when the looks change enough that folk already in the world should be dressed again. */
	public static final int VERSION = 3;

	/** A guard's chance of a spear instead of a sword (jungle realms: {@link #JUNGLE_SPEAR_CHANCE}). */
	static final float GUARD_SPEAR_CHANCE = 0.3F, JUNGLE_SPEAR_CHANCE = 0.7F;
	/** A guard's chance of the other helmet of his realm's harness (some variety within a rank). */
	static final float GUARD_HELM_SWAP_CHANCE = 0.2F;
	/** An archer's chance of a hood of his realm's colour rather than a mail coif (where the land doesn't decide). */
	static final float ARCHER_HOOD_CHANCE = 0.6F;
	/** A barbarian's chance his hides are marked with his camp's trim. */
	static final float BARBARIAN_TRIM_CHANCE = 0.5F;
	/** (0.5.30) One realm in this many arms its guards from {@link Armory} (its land's set, in its colour). */
	static final int ARMOURY_REALMS = 2;
	/** (0.7.105) A guard's chance of fencing with a rapier from Jaymo's Powers (a lord always does), when it's there. */
	static final float GUARD_RAPIER_CHANCE = 0.2F;
	/** (0.5.30) A raider's chance of the warlord's horned cap (the chief always wears the whole set). */
	static final float RAIDER_HORNS_CHANCE = 0.3F;

	/** Cloth colours (RGB) for the land's gear. */
	static final int LINEN = 0xEDE4CC, SAND = 0xD8C08A, DUST = 0xB0884F, SNOW = 0xF4F6F8, FROST_GREY = 0xB9C2C9, FUR_BROWN = 0x5A3F2A,
		JUNGLE_GREEN = 0x4F6B2A, SWAMP_MURK = 0x4A5530, STRAW = 0xD9B65A, CLAY = 0xB5651D, BOOT_BROWN = 0x4B3621;

	/** The ways of arming a realm's guards. */
	static final int PLATE = 0, TABARD = 1, HALF_PLATE = 2, HARNESS_STYLES = 3;

	/** One charge on a realm's arms: a pattern laid in the contrasting tincture. */
	private static final List<List<ResourceKey<BannerPattern>>> ORDINARIES = List.of(
		List.of(BannerPatterns.STRAIGHT_CROSS),
		List.of(BannerPatterns.CROSS),
		List.of(BannerPatterns.TRIANGLE_BOTTOM),
		List.of(BannerPatterns.STRIPE_DOWNRIGHT),
		List.of(BannerPatterns.STRIPE_CENTER),
		List.of(BannerPatterns.STRIPE_MIDDLE),
		List.of(BannerPatterns.HALF_VERTICAL),
		List.of(BannerPatterns.HALF_HORIZONTAL),
		List.of(BannerPatterns.STRIPE_TOP),
		List.of(BannerPatterns.RHOMBUS_MIDDLE),
		List.of(BannerPatterns.CIRCLE_MIDDLE),
		List.of(BannerPatterns.SQUARE_TOP_LEFT),
		List.of(BannerPatterns.STRIPE_SMALL),
		List.of(BannerPatterns.CURLY_BORDER),
		List.of(BannerPatterns.FLOWER),
		List.of(BannerPatterns.TRIANGLES_TOP, BannerPatterns.TRIANGLES_BOTTOM),
		List.of(BannerPatterns.STRIPE_LEFT, BannerPatterns.STRIPE_RIGHT),
		List.of(BannerPatterns.DIAGONAL_LEFT));

	/** Trim patterns a realm may wear (the ones that read well on a mob). */
	private static final List<ResourceKey<TrimPattern>> PATTERNS = List.of(TrimPatterns.SENTRY, TrimPatterns.DUNE, TrimPatterns.COAST,
		TrimPatterns.WILD, TrimPatterns.WARD, TrimPatterns.EYE, TrimPatterns.VEX, TrimPatterns.TIDE, TrimPatterns.SNOUT, TrimPatterns.RIB,
		TrimPatterns.SPIRE, TrimPatterns.WAYFINDER, TrimPatterns.SHAPER, TrimPatterns.SILENCE, TrimPatterns.RAISER, TrimPatterns.HOST,
		TrimPatterns.FLOW, TrimPatterns.BOLT);

	/** Tinctures that stand out against a field (a field never gets its own colour back). */
	private static final List<DyeColor> TINCTURES = List.of(DyeColor.WHITE, DyeColor.YELLOW, DyeColor.BLACK, DyeColor.RED, DyeColor.BLUE,
		DyeColor.GREEN, DyeColor.LIGHT_GRAY, DyeColor.ORANGE);

	/**
	 * A realm's arms: its {@code field} colour, the {@code charge}'s tincture, which of {@link #ORDINARIES}, whether it's
	 * a vassal's ({@code bordered}), its trim pattern and material, and how its guards are armed ({@link #PLATE}...).
	 */
	record Heraldry(DyeColor field, DyeColor charge, int ordinary, boolean bordered, ResourceKey<TrimPattern> pattern,
		ResourceKey<TrimMaterial> material, int harness, boolean armoury) {

		/** The arms of the realm with id {@code id} (an overlord's, for its fiefdoms) in {@code field}. */
		static Heraldry of(int id, DyeColor field, boolean vassal) {
			long h = mix(id * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
			List<DyeColor> tinctures = TINCTURES.stream().filter(c -> c != field && contrast(c, field)).toList();
			DyeColor charge = tinctures.get((int)Math.floorMod(h, (long)tinctures.size()));
			int ordinary = (int)Math.floorMod(h >>> 8, (long)ORDINARIES.size());
			ResourceKey<TrimPattern> pattern = PATTERNS.get((int)Math.floorMod(h >>> 20, (long)PATTERNS.size()));
			int harness = (int)Math.floorMod(h >>> 32, (long)HARNESS_STYLES);
			boolean armoury = Math.floorMod(h >>> 44, (long)ARMOURY_REALMS) == 0;
			return new Heraldry(field, charge, ordinary, vassal, pattern, Outfits.material(field), harness, armoury);
		}
	}

	private Outfits() {
	}

	/** The arms {@code folk} of {@code faction} bear (a fiefdom: its overlord's, bordered). */
	static Heraldry heraldry(Faction faction, @Nullable MinecraftServer server) {
		DyeColor color = server == null ? faction.color() : faction.styleColor(server);
		boolean vassal = faction.type() == FactionType.FIEFDOM && faction.overlord() >= 0;
		return Heraldry.of(vassal ? faction.overlord() : faction.id(), color, vassal);
	}

	/** Dresses {@code folk} for its role in {@code faction}'s arms and {@code climate}'s gear (its role's outfit already on). */
	public static void dress(Folk folk, Faction faction, Climate climate) {
		Heraldry arms = heraldry(faction, folk.level().getServer());
		RandomSource random = folk.getRandom();
		try {
			switch (folk.role()) {
				case Folk.GUARD -> guard(folk, arms, climate, random);
				case Folk.ARCHER -> archer(folk, arms, climate, random);
				case Folk.KING -> king(folk, arms, climate);
				case Folk.LORD -> lord(folk, arms, climate);
				case Folk.FOREMAN, Folk.WORKER -> {
					if (faction.type().isRealm()) {
						builder(folk, arms, climate);
					}
				}
				case Folk.BARBARIAN, Folk.CHIEF -> camp(folk, arms, random);
				default -> {
				}
			}
			fencer(folk, random);
		} catch (RuntimeException e) {
			// a registry without one of the vanilla trims or patterns (a data pack took it out): the role's plain gear stays
			JaymosKingdoms.LOGGER.debug("Folk outfit skipped", e);
		}
	}

	/**
	 * (0.7.105) A realm's duellists: a lord, and now and then a guard, fence with a rapier of their sword's metal (Jaymo's
	 * Powers' {@code RapierItem}: it lunges), when Powers is there.
	 */
	private static void fencer(Folk folk, RandomSource random) {
		if (!(folk.role() == Folk.LORD || folk.role() == Folk.GUARD && random.nextFloat() < GUARD_RAPIER_CHANCE)) {
			return;
		}
		ItemStack sword = folk.getMainHandItem();
		String metal = sword.is(Items.DIAMOND_SWORD) ? "diamond" : sword.is(Items.NETHERITE_SWORD) ? "netherite" : sword.is(Items.IRON_SWORD) ? "iron" : null;
		if (metal == null) {
			return;
		}
		net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", metal + "_rapier"))
			.ifPresent(rapier -> folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(rapier)));
	}

	private static void guard(Folk folk, Heraldry arms, Climate climate, RandomSource random) {
		boolean swap = random.nextFloat() < GUARD_HELM_SWAP_CHANCE;
		Item helm = switch (arms.harness()) {
			case TABARD -> swap ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET;
			default -> swap ? Items.CHAINMAIL_HELMET : Items.IRON_HELMET;
		};
		ItemStack head = trimmed(folk, helm, arms.pattern(), arms.material());
		ItemStack chest = switch (arms.harness()) {
			// a tabard of the realm's colour over mail: the leather tunic dyed and trimmed
			case TABARD -> trimmed(folk, dyed(Items.LEATHER_CHESTPLATE, rgb(arms.field())), arms.pattern(), arms.material());
			case HALF_PLATE -> trimmed(folk, Items.CHAINMAIL_CHESTPLATE, arms.pattern(), arms.material());
			default -> trimmed(folk, Items.IRON_CHESTPLATE, arms.pattern(), arms.material());
		};
		ItemStack legs = trimmed(folk, arms.harness() == HALF_PLATE ? Items.IRON_LEGGINGS : Items.CHAINMAIL_LEGGINGS, arms.pattern(), arms.material());
		ItemStack feet = new ItemStack(Items.IRON_BOOTS);
		switch (climate) {
			case DESERT, BADLANDS -> {
				head = trimmed(folk, dyed(Items.LEATHER_HELMET, LINEN), arms.pattern(), TrimMaterials.GOLD);
				legs = dyed(Items.LEATHER_LEGGINGS, climate == Climate.DESERT ? SAND : DUST);
				feet = dyed(Items.LEATHER_BOOTS, DUST);
			}
			case SNOWY -> {
				head = dyed(Items.LEATHER_HELMET, SNOW);
				feet = dyed(Items.LEATHER_BOOTS, FROST_GREY);
			}
			case TAIGA -> feet = dyed(Items.LEATHER_BOOTS, FUR_BROWN);
			case JUNGLE -> {
				head = trimmed(folk, dyed(Items.LEATHER_HELMET, JUNGLE_GREEN), arms.pattern(), arms.material());
				legs = dyed(Items.LEATHER_LEGGINGS, JUNGLE_GREEN);
			}
			case SWAMP -> legs = dyed(Items.LEATHER_LEGGINGS, SWAMP_MURK);
			case SAVANNA -> {
				head = dyed(Items.LEATHER_HELMET, STRAW);
				feet = new ItemStack(Items.COPPER_BOOTS);
			}
			default -> {
			}
		}
		if (arms.armoury() || climate == Climate.CHERRY) {
			// (0.5.30) the realm's armoury: its land's set, the cloth in the realm's colour and the trim on the chest;
			// (0.7.250) the Blossom land's guards always go in the ronin harness
			String set = switch (climate) {
				case DESERT, BADLANDS, SAVANNA -> "gladiator";
				case JUNGLE, CHERRY -> "ronin";
				default -> "paladin";
			};
			head = armoury(set, ArmorType.HELMET, rgb(arms.field()), head);
			chest = trimmed(folk, armoury(set, ArmorType.CHESTPLATE, rgb(arms.field()), chest), arms.pattern(), arms.material());
			legs = armoury(set, ArmorType.LEGGINGS, rgb(arms.field()), legs);
			feet = armoury(set, ArmorType.BOOTS, rgb(arms.field()), feet);
		}
		folk.setItemSlot(EquipmentSlot.HEAD, head);
		folk.setItemSlot(EquipmentSlot.CHEST, chest);
		folk.setItemSlot(EquipmentSlot.LEGS, legs);
		folk.setItemSlot(EquipmentSlot.FEET, feet);
		float spear = climate == Climate.JUNGLE ? JUNGLE_SPEAR_CHANCE : GUARD_SPEAR_CHANCE;
		folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextFloat() < spear ? Items.IRON_SPEAR : Items.IRON_SWORD));
		folk.setItemSlot(EquipmentSlot.OFFHAND, shield(folk, arms, false));
	}

	private static void archer(Folk folk, Heraldry arms, Climate climate, RandomSource random) {
		ItemStack head = random.nextFloat() < ARCHER_HOOD_CHANCE ? dyed(Items.LEATHER_HELMET, rgb(arms.field())) : new ItemStack(Items.CHAINMAIL_HELMET);
		int boots = BOOT_BROWN;
		switch (climate) {
			case DESERT, BADLANDS -> {
				head = dyed(Items.LEATHER_HELMET, LINEN);
				boots = DUST;
			}
			case SNOWY -> {
				head = dyed(Items.LEATHER_HELMET, SNOW);
				boots = FROST_GREY;
			}
			case JUNGLE -> head = dyed(Items.LEATHER_HELMET, JUNGLE_GREEN);
			case SAVANNA -> head = dyed(Items.LEATHER_HELMET, STRAW);
			default -> {
			}
		}
		folk.setItemSlot(EquipmentSlot.HEAD, head);
		folk.setItemSlot(EquipmentSlot.CHEST, trimmed(folk, dyed(Items.LEATHER_CHESTPLATE, rgb(arms.field())), arms.pattern(), arms.material()));
		folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, rgb(arms.charge())));
		folk.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, boots));
	}

	private static void king(Folk folk, Heraldry arms, Climate climate) {
		// the crown: gold, set with the realm's gem (diamond if its own metal is gold), glinting
		ResourceKey<TrimMaterial> gem = arms.material() == TrimMaterials.GOLD ? TrimMaterials.DIAMOND : arms.material();
		folk.setItemSlot(EquipmentSlot.HEAD, glint(trimmed(folk, Items.GOLDEN_HELMET, arms.pattern(), gem)));
		folk.setItemSlot(EquipmentSlot.CHEST, trimmed(folk, Items.NETHERITE_CHESTPLATE, arms.pattern(), TrimMaterials.GOLD));
		folk.setItemSlot(EquipmentSlot.LEGS, trimmed(folk, Items.NETHERITE_LEGGINGS, arms.pattern(), TrimMaterials.GOLD));
		folk.setItemSlot(EquipmentSlot.FEET, switch (climate) {
			case SNOWY -> dyed(Items.LEATHER_BOOTS, SNOW);
			case DESERT, BADLANDS, SAVANNA -> trimmed(folk, Items.GOLDEN_BOOTS, arms.pattern(), gem);
			default -> trimmed(folk, Items.NETHERITE_BOOTS, arms.pattern(), TrimMaterials.GOLD);
		});
		folk.setItemSlot(EquipmentSlot.MAINHAND, glint(new ItemStack(Items.NETHERITE_SWORD)));
		folk.setItemSlot(EquipmentSlot.OFFHAND, shield(folk, arms, true));
	}

	private static void lord(Folk folk, Heraldry arms, Climate climate) {
		// bareheaded (his circlet shows) unless the land calls for a hood
		folk.setItemSlot(EquipmentSlot.HEAD, switch (climate) {
			case DESERT, BADLANDS -> trimmed(folk, dyed(Items.LEATHER_HELMET, LINEN), arms.pattern(), TrimMaterials.GOLD);
			case SNOWY -> dyed(Items.LEATHER_HELMET, SNOW);
			default -> ItemStack.EMPTY;
		});
		folk.setItemSlot(EquipmentSlot.CHEST, trimmed(folk, Items.DIAMOND_CHESTPLATE, arms.pattern(), arms.material()));
		folk.setItemSlot(EquipmentSlot.LEGS, trimmed(folk, Items.DIAMOND_LEGGINGS, arms.pattern(), arms.material()));
		folk.setItemSlot(EquipmentSlot.FEET, trimmed(folk, Items.IRON_BOOTS, arms.pattern(), arms.material()));
		folk.setItemSlot(EquipmentSlot.OFFHAND, shield(folk, arms, false));
	}

	private static void builder(Folk folk, Heraldry arms, Climate climate) {
		// their tabards are in their skins; the land gives them a hat
		ItemStack head = switch (climate) {
			case DESERT, BADLANDS -> dyed(Items.LEATHER_HELMET, LINEN);
			case SNOWY -> dyed(Items.LEATHER_HELMET, SNOW);
			case SAVANNA, JUNGLE -> dyed(Items.LEATHER_HELMET, STRAW);
			default -> ItemStack.EMPTY;
		};
		if (!head.isEmpty()) {
			folk.setItemSlot(EquipmentSlot.HEAD, head);
		}
		if (climate == Climate.SNOWY) {
			folk.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, FUR_BROWN));
		}
	}

	private static void camp(Folk folk, Heraldry arms, RandomSource random) {
		// (0.5.30) the chief in the warlord's furs and horns, his kilt in the camp's colour; some raiders in the horned cap
		if (folk.role() == Folk.CHIEF) {
			for (ArmorType type : new ArmorType[]{ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
				EquipmentSlot slot = type.getSlot();
				folk.setItemSlot(slot, armoury("warlord", type, rgb(arms.field()), folk.getItemBySlot(slot)));
			}
		} else if (random.nextFloat() < RAIDER_HORNS_CHANCE) {
			folk.setItemSlot(EquipmentSlot.HEAD, armoury("warlord", ArmorType.HELMET, -1, folk.getItemBySlot(EquipmentSlot.HEAD)));
		}
		ItemStack chest = folk.getItemBySlot(EquipmentSlot.CHEST);
		if (chest.isEmpty() || folk.role() == Folk.BARBARIAN && random.nextFloat() >= BARBARIAN_TRIM_CHANCE) {
			return;
		}
		ResourceKey<TrimPattern> pattern = folk.role() == Folk.CHIEF ? TrimPatterns.RIB
			: List.of(TrimPatterns.WILD, TrimPatterns.SNOUT, TrimPatterns.DUNE).get(arms.ordinary() % 3);
		ResourceKey<TrimMaterial> material = folk.role() == Folk.CHIEF ? TrimMaterials.GOLD : arms.ordinary() % 2 == 0 ? TrimMaterials.COPPER : TrimMaterials.IRON;
		folk.setItemSlot(EquipmentSlot.CHEST, trimmed(folk, chest.copy(), pattern, material));
	}

	/** A shield bearing the realm's arms (a king's glints). */
	static ItemStack shield(Folk folk, Heraldry arms, boolean royal) {
		ItemStack stack = new ItemStack(Items.SHIELD);
		stack.set(DataComponents.BASE_COLOR, arms.field());
		stack.set(DataComponents.BANNER_PATTERNS, layers(folk, arms));
		return royal ? glint(stack) : stack;
	}

	/** The charges of the realm's arms, as banner layers (on a shield, or a banner). */
	static BannerPatternLayers layers(Folk folk, Heraldry arms) {
		Registry<BannerPattern> patterns = folk.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
		for (ResourceKey<BannerPattern> key : ORDINARIES.get(arms.ordinary())) {
			patterns.get(key).ifPresent(p -> builder.add(p, arms.charge()));
		}
		if (arms.bordered()) {
			patterns.get(BannerPatterns.BORDER).ifPresent(p -> builder.add(p, arms.charge()));
		}
		return builder.build();
	}

	/**
	 * (0.7.250) The Blossom land's guards (MCA's own too: {@link BlossomVillages}) in the whole ronin harness, its cloth
	 * in {@code color}; a piece the set lacks keeps what's there.
	 */
	static void ronin(LivingEntity mob, DyeColor color) {
		for (ArmorType type : new ArmorType[] {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
			EquipmentSlot slot = type.getSlot();
			mob.setItemSlot(slot, armoury("ronin", type, rgb(color), mob.getItemBySlot(slot)));
		}
	}

	/** (0.7.250) {@code mob} wears the ronin harness's cuirass. */
	static boolean wearsRonin(LivingEntity mob) {
		Item chest = Armory.piece("ronin", ArmorType.CHESTPLATE);
		return chest != null && mob.getItemBySlot(EquipmentSlot.CHEST).is(chest);
	}

	/** A piece of an {@link Armory} set, its cloth dyed {@code rgb} (-1: the set's own colour); {@code fallback} if there's no such piece. */
	private static ItemStack armoury(String set, ArmorType type, int rgb, ItemStack fallback) {
		Item item = Armory.piece(set, type);
		if (item == null) {
			return fallback;
		}
		ItemStack stack = new ItemStack(item);
		if (rgb != -1) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		}
		return stack;
	}

	private static ItemStack trimmed(Folk folk, Item item, ResourceKey<TrimPattern> pattern, ResourceKey<TrimMaterial> material) {
		return trimmed(folk, new ItemStack(item), pattern, material);
	}

	/** {@code stack} with a trim (unchanged if either part isn't registered). */
	private static ItemStack trimmed(Folk folk, ItemStack stack, ResourceKey<TrimPattern> pattern, ResourceKey<TrimMaterial> material) {
		Optional<? extends Holder<TrimPattern>> p = folk.level().registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(pattern);
		Optional<? extends Holder<TrimMaterial>> m = folk.level().registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(material);
		if (p.isPresent() && m.isPresent()) {
			stack.set(DataComponents.TRIM, new ArmorTrim(m.get(), p.get()));
		}
		return stack;
	}

	static ItemStack dyed(Item item, int rgb) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	private static ItemStack glint(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	static int rgb(DyeColor color) {
		return color.getTextureDiffuseColor() & 0xFFFFFF;
	}

	/** The trim material nearest a dye's colour. */
	static ResourceKey<TrimMaterial> material(DyeColor color) {
		return switch (color) {
			case WHITE -> TrimMaterials.QUARTZ;
			case ORANGE, BROWN -> TrimMaterials.COPPER;
			case MAGENTA, PURPLE -> TrimMaterials.AMETHYST;
			case LIGHT_BLUE, CYAN -> TrimMaterials.DIAMOND;
			case YELLOW -> TrimMaterials.GOLD;
			case LIME, GREEN -> TrimMaterials.EMERALD;
			case PINK, RED -> TrimMaterials.REDSTONE;
			case GRAY, BLACK -> TrimMaterials.NETHERITE;
			case LIGHT_GRAY -> TrimMaterials.IRON;
			case BLUE -> TrimMaterials.LAPIS;
		};
	}

	/** Whether {@code a} reads against {@code b} (different enough in brightness or hue family). */
	private static boolean contrast(DyeColor a, DyeColor b) {
		int x = a.getTextureDiffuseColor(), y = b.getTextureDiffuseColor();
		int dr = ((x >> 16) & 255) - ((y >> 16) & 255), dg = ((x >> 8) & 255) - ((y >> 8) & 255), db = (x & 255) - (y & 255);
		return dr * dr + dg * dg + db * db > 90 * 90;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
