package com.jaymospowers.core.gifted;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.costume.CostumeKit;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Costumed gifted folk (Andy, 2026-11-11: "You can find some npc costumed power users in the world, either wreaking havoc
 * or trying to help their people"). A gifted villager ({@link Gifted}) can own a costume of their own ({@link #DESIGN}),
 * made here for their power and their side ({@link #dress}): a vigilante's in bright colours, a villain's in dark ones,
 * always with a mask that hides the face, and a name as the folk would call them. They wear it only while
 * {@link #SUITED}; Jaymo's Kingdoms decides who gets one and when it goes on (after dark), by reflection, and the client
 * draws a suited villager as a player body in that costume ({@code client.costume.GiftedCostumeClient}).
 */
public final class GiftedCostume {
	/** On a gifted villager: the costume they own (synced to everyone, so the client can draw it). */
	public static final AttachmentType<CostumeDesign> DESIGN = AttachmentRegistry.create(JaymosPowers.id("gifted_costume"),
		builder -> builder.persistent(CostumeDesign.CODEC).syncWith(CostumeDesign.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On a gifted villager: they have it on just now. */
	public static final AttachmentType<Boolean> SUITED = AttachmentRegistry.create(JaymosPowers.id("gifted_suited"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** A cape on this share of vigilantes' costumes, a tattered one on this share of villains'. */
	static final float HERO_CAPE = 0.5F, VILLAIN_CAPE = 0.4F;
	/** Each kit piece (gloves, boots, belt) on this share of costumes. */
	static final float KIT_PIECE = 0.5F;

	/** The names the folk give them, by power: a vigilante's, then a villain's. */
	private static final Map<String, String[][]> NAMES = Map.of(
		"wind", new String[][] {{"the Gale", "Zephyr", "Skylark", "the Updraft"}, {"the Squall", "Stormcrow", "the Howl"}},
		"cryo", new String[][] {{"Hoarfrost", "Rime", "the Thaw"}, {"Black Ice", "Frostbite", "Winterheart"}},
		"smoke", new String[][] {{"the Haze", "Ashfall", "Hearthsmoke"}, {"the Smother", "Soot", "Cinder Jack"}},
		"darktide", new String[][] {{"the Undertow", "Deepwatch", "Tidewarden"}, {"the Drowner", "Black Tide", "the Riptide"}},
		"gravity", new String[][] {{"the Anchor", "Lodestone", "Featherfall"}, {"the Crusher", "Deadweight", "the Sinkhole"}},
		"web", new String[][] {{"the Weaver", "Silkline", "the Spinner"}, {"the Widow", "the Snare", "the Lurker"}},
		"resonance", new String[][] {{"the Bell", "Echo", "the Chime"}, {"the Shriek", "Shatter", "the Wail"}},
		"zfighter", new String[][] {{"the Fist", "Ironpalm", "the Ward"}, {"the Brawler", "Knuckles", "the Ravager"}},
		"arsenal", new String[][] {{"the Quartermaster", "Steelreach", "the Armourer"}, {"the Magpie", "Grabhand", "the Pilferer"}});
	private static final String[][] ANY_NAMES = {{"the Masked One", "the Watchman", "the Lantern"}, {"the Menace", "the Masked Thief", "Nightjack"}};

	/** A vigilante's colours, by power (first, second); a villain's are dark. */
	private static final Map<String, DyeColor[]> HERO_COLOURS = Map.of(
		"wind", new DyeColor[] {DyeColor.WHITE, DyeColor.LIGHT_BLUE},
		"cryo", new DyeColor[] {DyeColor.LIGHT_BLUE, DyeColor.WHITE},
		"smoke", new DyeColor[] {DyeColor.GRAY, DyeColor.ORANGE},
		"darktide", new DyeColor[] {DyeColor.BLUE, DyeColor.CYAN},
		"gravity", new DyeColor[] {DyeColor.PURPLE, DyeColor.LIGHT_GRAY},
		"web", new DyeColor[] {DyeColor.RED, DyeColor.BLUE},
		"resonance", new DyeColor[] {DyeColor.CYAN, DyeColor.WHITE},
		"zfighter", new DyeColor[] {DyeColor.ORANGE, DyeColor.BLUE},
		"arsenal", new DyeColor[] {DyeColor.YELLOW, DyeColor.BROWN});
	private static final DyeColor[] VILLAIN_SECOND = {DyeColor.RED, DyeColor.PURPLE, DyeColor.GREEN, DyeColor.GRAY};

	private GiftedCostume() {
	}

	/**
	 * Makes {@code entity} a costume of their own for their power, a {@code villain}'s or a vigilante's (called by Kingdoms,
	 * by reflection). False for one with no gift; true if they already have one.
	 */
	public static boolean dress(Entity entity, boolean villain) {
		if (!(entity instanceof PathfinderMob mob) || Gifted.of(mob) == null) {
			return false;
		}
		if (mob.getAttached(DESIGN) == null) {
			mob.setAttached(DESIGN, design(Gifted.power(mob), villain, mob.getRandom()).sanitized());
		}
		return true;
	}

	/** Puts their costume on or takes it off (Kingdoms, by reflection); false without one, or if it already was. */
	public static boolean suit(Entity entity, boolean on) {
		if (entity.getAttached(DESIGN) == null || suited(entity) == on) {
			return false;
		}
		if (on) {
			entity.setAttached(SUITED, true);
		} else {
			entity.removeAttached(SUITED);
		}
		entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.8F, 1.0F);
		return true;
	}

	/** Whether {@code entity} has their costume on. Either side. */
	public static boolean suited(Entity entity) {
		return Boolean.TRUE.equals(entity.getAttached(SUITED));
	}

	/** The costume {@code entity} has on just now, or null. Either side. */
	public static @Nullable CostumeDesign shown(Entity entity) {
		return suited(entity) ? entity.getAttached(DESIGN) : null;
	}

	/** The costume's name (what the folk call them while it's on), or null without one. Either side. */
	public static @Nullable String name(Entity entity) {
		CostumeDesign design = entity.getAttached(DESIGN);
		return design == null ? null : design.name();
	}

	/** Takes their costume away (a gift taken, a test). */
	public static void strip(Entity entity) {
		entity.removeAttached(DESIGN);
		entity.removeAttached(SUITED);
	}

	/** A costume for {@code power} ("wind"...): the power's own cut where it has one, and a mask that hides the face. */
	static CostumeDesign design(@Nullable String power, boolean villain, RandomSource random) {
		String key = power == null ? "" : power;
		String[][] names = NAMES.getOrDefault(key, ANY_NAMES);
		String name = pick(names[villain ? 1 : 0], random);

		List<CostumeDesign.Cut> cuts = new ArrayList<>();
		for (CostumeDesign.Cut cut : CostumeDesign.Cut.values()) {
			if (key.equals(cut.power)) {
				cuts.add(cut);
			}
		}
		if (cuts.isEmpty() || random.nextBoolean()) {
			cuts.addAll(villain ? List.of(CostumeDesign.Cut.RAGGED, CostumeDesign.Cut.COAT, CostumeDesign.Cut.ASSASSIN)
				: List.of(CostumeDesign.Cut.BODYSUIT, CostumeDesign.Cut.ARMOURED, CostumeDesign.Cut.COAT));
		}
		CostumeDesign.Cut cut = cuts.get(random.nextInt(cuts.size()));

		List<CostumeDesign.Mask> masks = new ArrayList<>();
		for (CostumeDesign.Mask mask : CostumeDesign.Mask.values()) {
			if (mask.hidesFace && (mask.power == null || key.equals(mask.power))) {
				masks.add(mask);
			}
		}
		CostumeDesign.Mask mask = masks.get(random.nextInt(masks.size()));

		DyeColor primary, secondary;
		if (villain) {
			primary = DyeColor.BLACK;
			secondary = VILLAIN_SECOND[random.nextInt(VILLAIN_SECOND.length)];
		} else {
			DyeColor[] colours = HERO_COLOURS.getOrDefault(key, new DyeColor[] {DyeColor.BLUE, DyeColor.WHITE});
			boolean swap = random.nextBoolean();
			primary = colours[swap ? 1 : 0];
			secondary = colours[swap ? 0 : 1];
		}

		CostumeDesign.Cape cape = CostumeDesign.Cape.NONE;
		if (villain ? random.nextFloat() < VILLAIN_CAPE : random.nextFloat() < HERO_CAPE) {
			if (villain) {
				cape = CostumeDesign.Cape.TATTERED;
			} else {
				List<CostumeDesign.Cape> capes = new ArrayList<>(List.of(CostumeDesign.Cape.SHORT, CostumeDesign.Cape.LONG));
				for (CostumeDesign.Cape c : CostumeDesign.Cape.values()) {
					if (key.equals(c.power)) {
						capes.add(c);
					}
				}
				cape = capes.get(random.nextInt(capes.size()));
			}
		}

		CostumeKit kit = CostumeKit.NONE;
		if (random.nextFloat() < KIT_PIECE) {
			kit = kit.withGloves(random.nextBoolean() ? CostumeKit.Gloves.GLOVES : CostumeKit.Gloves.GAUNTLETS);
		}
		if (random.nextFloat() < KIT_PIECE) {
			kit = kit.withBoots(CostumeKit.Boots.BOOTS);
		}
		if (random.nextFloat() < KIT_PIECE) {
			kit = kit.withBelt(CostumeKit.Belt.BELT);
		}
		return new CostumeDesign(name, cut, primary, secondary, mask, cape, "", kit);
	}

	private static String pick(String[] from, RandomSource random) {
		return from[random.nextInt(from.length)];
	}
}
