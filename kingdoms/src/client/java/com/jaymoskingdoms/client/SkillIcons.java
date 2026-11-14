package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Skills;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The picture on each perk's node of the skill web: one item that says what the perk is about. */
final class SkillIcons {
	private static final Map<Skills.Perk, ItemStack> ICONS = new EnumMap<>(Skills.Perk.class);

	private SkillIcons() {
	}

	private static void put(Skills.Perk perk, Item item) {
		ICONS.put(perk, new ItemStack(item));
	}

	/** The perk's picture (a book for one that has none of its own). */
	static ItemStack of(Skills.Perk perk) {
		return ICONS.computeIfAbsent(perk, p -> new ItemStack(Items.BOOK));
	}

	/** A pillar's picture, on its crest. */
	static ItemStack of(Skills.Pillar pillar) {
		return new ItemStack(switch (pillar) {
			case STRENGTH -> Items.IRON_SWORD;
			case SURVIVAL -> Items.CAMPFIRE;
			case STEALTH -> Items.ENDER_EYE;
			case SCHOLAR -> Items.SPYGLASS;
		});
	}

	static {
		put(Skills.Perk.LIGHT_FINGERS, Items.EMERALD);
		put(Skills.Perk.DEEP_POCKETS, Items.BUNDLE);
		put(Skills.Perk.SOFT_STEP, Items.LEATHER_BOOTS);
		put(Skills.Perk.QUICK_HANDS, Items.RABBIT_FOOT);
		put(Skills.Perk.LOCKPICKING, Items.TRIPWIRE_HOOK);
		put(Skills.Perk.GHOST, Items.PHANTOM_MEMBRANE);
		put(Skills.Perk.FORAGER, Items.SWEET_BERRIES);
		put(Skills.Perk.SECOND_WIND, Items.FEATHER);
		put(Skills.Perk.WEATHERED, Items.LEATHER_CHESTPLATE);
		put(Skills.Perk.IRON_STOMACH, Items.ROTTEN_FLESH);
		put(Skills.Perk.HARDY_LANDING, Items.HAY_BLOCK);
		put(Skills.Perk.LAST_STAND, Items.TOTEM_OF_UNDYING);
		put(Skills.Perk.HEAVY_BLOWS, Items.IRON_AXE);
		put(Skills.Perk.BRACE, Items.SHIELD);
		put(Skills.Perk.BULLDOZER, Items.PISTON);
		put(Skills.Perk.QUARRYMAN, Items.IRON_PICKAXE);
		put(Skills.Perk.PACKHORSE, Items.CHEST);
		put(Skills.Perk.TITANS_GRIP, Items.NETHERITE_AXE);
		put(Skills.Perk.MOMENTUM, Items.SUGAR);
		put(Skills.Perk.CLEAVE, Items.DIAMOND_AXE);
		put(Skills.Perk.EARTHSPLITTER, Items.COBBLED_DEEPSLATE);
		put(Skills.Perk.EXECUTIONER, Items.GOLDEN_AXE);
		put(Skills.Perk.REND, Items.REDSTONE);
		put(Skills.Perk.CHARGE, Items.IRON_HORSE_ARMOR);
		put(Skills.Perk.WAR_CRY, Items.GOAT_HORN);
		put(Skills.Perk.BLADESTORM, Items.NETHERITE_SWORD);
		put(Skills.Perk.SHIELD_THROW, Items.HEAVY_CORE);
		put(Skills.Perk.MEDITATION, Items.AMETHYST_SHARD);
		put(Skills.Perk.WEAPON_FORMS, Items.NETHERITE_SWORD);
		put(Skills.Perk.CLIMBER, Items.LADDER);
		put(Skills.Perk.FREERUNNER, Items.CHAINMAIL_BOOTS);
		put(Skills.Perk.LEAP_OF_FAITH, Items.ELYTRA);
		put(Skills.Perk.BAR_SWING, Items.IRON_BARS);
		put(Skills.Perk.WALL_RUNNER, Items.STONE_BRICK_WALL);
		put(Skills.Perk.AIR_ROLL, Items.RABBIT_FOOT);
		put(Skills.Perk.SHADOWED, Items.INK_SAC);
		put(Skills.Perk.QUIET_STEP, Items.FEATHER);
		put(Skills.Perk.SLIP_THE_NET, Items.COBWEB);
		put(Skills.Perk.SNEAK_ATTACK, Items.IRON_SWORD);
		put(Skills.Perk.ASSASSINATE, Items.WITHER_SKELETON_SKULL);
		put(Skills.Perk.STEADY_HANDS, Items.SHEARS);
		put(Skills.Perk.FIELD_DRESSER, Items.PAPER);
		put(Skills.Perk.BONESETTER, Items.BONE);
		put(Skills.Perk.PLAGUE_DOCTOR, Items.FERMENTED_SPIDER_EYE);
		put(Skills.Perk.LIFESAVER, Items.GOLDEN_APPLE);
		put(Skills.Perk.SELF_REVIVE, Items.GOLDEN_APPLE);
		put(Skills.Perk.MAKE_DO, Items.CRAFTING_TABLE);
		put(Skills.Perk.DREAD_PRESENCE, Items.SKELETON_SKULL);
		put(Skills.Perk.GROUNDED, Items.ANVIL);
		put(Skills.Perk.STARE_DOWN, Items.ENDER_EYE);
		put(Skills.Perk.NO_GETTING_UP, Items.CHAINMAIL_BOOTS);
		put(Skills.Perk.SPELLBREAKER, Items.ENCHANTED_BOOK);
		put(Skills.Perk.BROKEN_MORALE, Items.PAPER);
		put(Skills.Perk.SNAP_THE_THREAD, Items.STRING);
		put(Skills.Perk.SHRUG_IT_OFF, Items.IRON_CHESTPLATE);
		put(Skills.Perk.NAME_THAT_CARRIES, Items.NAME_TAG);
		put(Skills.Perk.WITCH_HUNTER, Items.GLASS_BOTTLE);
		put(Skills.Perk.WILL_OVER_FLESH, Items.NETHER_STAR);
		put(Skills.Perk.GRIM_HAND, Items.BONE_MEAL);
		put(Skills.Perk.NO_MERCY, Items.STONE_SWORD);
		put(Skills.Perk.LAST_LOOK, Items.SPYGLASS);
		put(Skills.Perk.REFUSE_TO_FALL, Items.GOLDEN_CHESTPLATE);
		put(Skills.Perk.SECOND_BREATH, Items.GLISTERING_MELON_SLICE);
		put(Skills.Perk.UNBROKEN_ROAR, Items.DRAGON_HEAD);
		put(Skills.Perk.UNFLINCHING, Items.OBSIDIAN);
		put(Skills.Perk.INTERROGATION, Items.WRITABLE_BOOK);
		put(Skills.Perk.ROUGH_QUESTIONS, Items.STICK);
		put(Skills.Perk.QUIET_WORD, Items.BOOK);
		put(Skills.Perk.DEVOTION, Items.CANDLE);
		put(Skills.Perk.AMBUSHER, Items.SPIDER_EYE);
		put(Skills.Perk.CUTTHROAT, Items.FLINT);
		put(Skills.Perk.IRON_HIDE, Items.IRON_INGOT);
		put(Skills.Perk.BEAST_OF_BURDEN, Items.SADDLE);
		put(Skills.Perk.TRACKER, Items.COMPASS);
		put(Skills.Perk.LIGHT_PACK, Items.LEATHER);
		put(Skills.Perk.POWER_SHOT, Items.BOW);
		put(Skills.Perk.ENTANGLING_ROOTS, Items.VINE);
		put(Skills.Perk.BARRAGE, Items.ARROW);
		put(Skills.Perk.SPIRIT_WOLF, Items.WOLF_ARMOR);
		put(Skills.Perk.MAGIC_ARROW, Items.SPECTRAL_ARROW);
		put(Skills.Perk.RAIN_OF_ARROWS, Items.TIPPED_ARROW);
		put(Skills.Perk.ARCANE_MISSILE, Items.FIREWORK_ROCKET);
		put(Skills.Perk.ARCANE_EXPLOSION, Items.END_CRYSTAL);
		put(Skills.Perk.ARCANE_BEAM, Items.END_ROD);
		put(Skills.Perk.ARCANE_BARRAGE, Items.AMETHYST_CLUSTER);
		put(Skills.Perk.ARCANE_BLINK, Items.ENDER_PEARL);
		put(Skills.Perk.ARCANE_EVOCATION, Items.ENCHANTING_TABLE);
		put(Skills.Perk.FIRE_BREATH, Items.BLAZE_POWDER);
		put(Skills.Perk.FIRE_SLASH, Items.BLAZE_ROD);
		put(Skills.Perk.FIRE_METEOR, Items.FIRE_CHARGE);
		put(Skills.Perk.FIRE_STORM, Items.MAGMA_CREAM);
		put(Skills.Perk.FIRE_WALL, Items.MAGMA_BLOCK);
		put(Skills.Perk.FIRE_HYDRA, Items.DRAGON_BREATH);
		put(Skills.Perk.FROST_NOVA, Items.SNOWBALL);
		put(Skills.Perk.FROST_SPIKES, Items.POINTED_DRIPSTONE);
		put(Skills.Perk.FROST_SHIELD, Items.ICE);
		put(Skills.Perk.FROST_LANCE, Items.PACKED_ICE);
		put(Skills.Perk.FROST_BLIZZARD, Items.POWDER_SNOW_BUCKET);
		put(Skills.Perk.FROST_ELEMENTAL, Items.BLUE_ICE);
		put(Skills.Perk.SLICE_AND_DICE, Items.GOLDEN_SWORD);
		put(Skills.Perk.SHOCK_POWDER, Items.GUNPOWDER);
		put(Skills.Perk.MUTILATE, Items.BONE_MEAL);
		put(Skills.Perk.BEAR_TRAP, Items.IRON_TRAPDOOR);
		put(Skills.Perk.SHADOW_STEP, Items.ECHO_SHARD);
		put(Skills.Perk.VANISH, Items.GLASS);
		put(Skills.Perk.MARKED_QUARRY, Items.TARGET);
		put(Skills.Perk.TANGLE_CORD, Items.LEAD);
		put(Skills.Perk.CAST_OFF_CLOAK, Items.LEATHER_LEGGINGS);
		put(Skills.Perk.THROW, Items.STONE_AXE);
		put(Skills.Perk.SHOUT, Items.BELL);
		put(Skills.Perk.THROW_NET, Items.FISHING_ROD);
		put(Skills.Perk.WARRIOR_CHARGE, Items.IRON_BOOTS);
		put(Skills.Perk.WARRIOR_LAST_STAND, Items.IRON_HELMET);
		put(Skills.Perk.MORTAL_STRIKE, Items.DIAMOND_SWORD);
		put(Skills.Perk.SHEATH_DRAW, Items.STONE_SWORD);
		put(Skills.Perk.CRESCENT_SWEEP, Items.GOLDEN_SWORD);
		put(Skills.Perk.RAIN_OF_CUTS, Items.NETHERITE_AXE);
		put(Skills.Perk.GROUND_STOMP, Items.NETHERITE_BOOTS);
		put(Skills.Perk.FALLING_FIST, Items.MACE);
		put(Skills.Perk.RENDING_CLAW, Items.ARMADILLO_SCUTE);
		put(Skills.Perk.FERAL_POUNCE, Items.RABBIT_HIDE);
		put(Skills.Perk.SPEAR_LUNGE, Items.TRIDENT);
		put(Skills.Perk.COUCHED_LANCE, Items.IRON_SPEAR);
		put(Skills.Perk.PINNING_THROW, Items.SPECTRAL_ARROW);
		put(Skills.Perk.ENCHANTED_STORAGE, Items.ENDER_CHEST);
		put(Skills.Perk.READ_THE_GROUND, Items.MAP);
		put(Skills.Perk.WATCHED, Items.CARVED_PUMPKIN);
		put(Skills.Perk.THE_STARE, Items.WITHER_SKELETON_SKULL);
		put(Skills.Perk.SLIP_BEHIND, Items.PHANTOM_MEMBRANE);
		put(Skills.Perk.BODY_COUNT, Items.BONE_BLOCK);
		put(Skills.Perk.LIFT_WEAPON, Items.WOODEN_SWORD);
		put(Skills.Perk.DRY_REMARK, Items.WRITABLE_BOOK);
		put(Skills.Perk.DISARM_RUSH, Items.CHAINMAIL_CHESTPLATE);
	}
}
