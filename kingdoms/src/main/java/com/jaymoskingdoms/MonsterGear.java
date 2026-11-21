package com.jaymoskingdoms;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * (0.7.105) Tiered monster gear, after Just-In COMBAT's (its idea only, our own code): the {@link WorldTiers} a monster
 * takes now shows on it. When a zombie or skeleton first takes a tier above Hearth, each empty armour slot may be filled,
 * and its hands too: a weapon for a zombie, now and then a blade for a skeleton, and a shield ({@link MobShields}) for a
 * zombie with a weapon. Each piece is one of five grades, higher ones rarer, and the world tier shifts the odds up:
 * <ol>
 * <li>Worn: leather, a stone sword</li>
 * <li>Common: copper</li>
 * <li>Fine: chainmail, iron blades</li>
 * <li>Rare: iron, a rapier or longsword when Jaymo's Powers is there</li>
 * <li>Legendary: steel plate and heavy iron blades, always enchanted (no diamond gear: Andy, 2026-11-15)</li>
 * </ol>
 * They drop it as vanilla's mobs do (now and then, worn). Gear a monster already had is kept.
 * <p>
 * Deeper monsters, older gear: a zombie, skeleton or vindicator that spawns naturally below y 0 ({@link #armDeep}) is
 * dressed as if its world tier were higher by the band it spawned in, and the band is kept on it, so the world tier it
 * takes later stacks on top ({@link #arm} reads it) instead of being replaced.
 */
public final class MonsterGear {
	/** Per world tier (Hearth to Legend): the chance each empty armour slot is filled. */
	static final float[] ARMOR_CHANCE = {0.0F, 0.25F, 0.40F, 0.55F, 0.70F};
	/** Per world tier: the chance an empty-handed zombie takes up a weapon, and a skeleton trades its bow for a blade. */
	static final float[] WEAPON_CHANCE = {0.0F, 0.15F, 0.25F, 0.35F, 0.45F};
	static final float[] SKELETON_BLADE_CHANCE = {0.0F, 0.03F, 0.06F, 0.10F, 0.15F};
	/** Per world tier: the chance an armed zombie, or a skeleton with a blade, carries a shield. */
	static final float[] SHIELD_CHANCE = {0.0F, 0.12F, 0.22F, 0.32F, 0.42F};
	/** Per world tier, the weights of the five grades (worn, common, fine, rare, legendary). */
	static final int[][] GRADE_WEIGHTS = {
		{100, 0, 0, 0, 0},
		{60, 30, 10, 0, 0},
		{35, 35, 22, 8, 0},
		{15, 30, 30, 20, 5},
		{5, 20, 30, 30, 15}};
	/** Rare gear is enchanted this often; legendary always. The enchanting's level for each. */
	static final float RARE_ENCHANT_CHANCE = 0.3F;
	static final int RARE_ENCHANT_LEVEL = 12, LEGENDARY_ENCHANT_LEVEL = 25;

	/**
	 * Per world tier: the chance one of the risen dead comes up in a whole dark set of Fantasy Armor's instead
	 * ({@link FantasyArmorCompat}, only where that mod is loaded), always enchanted as legendary gear is.
	 */
	static final float[] FANTASY_SET_CHANCE = {0.0F, 0.0F, 0.0F, 0.04F, 0.10F};

	/** Depth gear: the first y of each band below y 0 (shallow, deep, deepest), each worth one more step up the grade tables. */
	static final int[] DEPTH_BAND_Y = {-1, -25, -49};
	/** The tag a deep-spawned mob keeps, followed by its band. */
	static final String DEPTH_TAG = "jaymoskingdoms_deep_";

	/** (0.7.386) How often a star-touched mob that wears armour takes each empty slot's dyed piece. */
	static final float STAR_PIECE_CHANCE = 0.75F;

	/** Per grade: helmet, chestplate, leggings, boots. */
	private static final Item[][] ARMOR = {
		{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
		{Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS},
		{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
		{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS}};
	/** Legendary armour: the Armory's steel plate (iron if it's missing), helmet to boots. */
	private static final String[] LEGENDARY_ARMOR = {"jaymoskingdoms:steel_plate_helmet", "jaymoskingdoms:steel_plate_chestplate",
		"jaymoskingdoms:steel_plate_leggings", "jaymoskingdoms:steel_plate_boots"};
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Per grade, the weapons (Jaymo's Powers' ids are used when it's there, the vanilla one after each otherwise). */
	private static final String[][] WEAPONS = {
		{"minecraft:stone_sword", "minecraft:stone_axe", "jaymospowers:stone_rapier"},
		{"minecraft:copper_sword", "minecraft:copper_axe", "jaymospowers:iron_dagger"},
		{"minecraft:iron_sword", "jaymospowers:iron_saber", "jaymospowers:iron_rapier"},
		{"jaymospowers:iron_rapier", "jaymospowers:iron_longsword", "minecraft:iron_axe"},
		{"jaymospowers:iron_claymore", "jaymospowers:iron_broadsword", "jaymospowers:iron_war_axe"}};
	private static final Item[] FALLBACK = {Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_SWORD};
	/** Per grade, the shield (a round shield from Jaymo's Powers above common). */
	private static final String[] SHIELDS = {"minecraft:shield", "minecraft:shield", "jaymospowers:iron_round_shield",
		"jaymospowers:iron_round_shield", "jaymospowers:iron_round_shield"};

	private MonsterGear() {
	}

	/** Who wears it: the zombies (husks, drowned, zombified piglins, and the like) and the skeletons, whose bodies show armour. */
	static boolean wears(Mob mob) {
		return mob instanceof Zombie || mob instanceof AbstractSkeleton;
	}

	/** {@code mob} takes world tier {@code tier} for the first time: its gear for that tier (and for the depth it spawned at). */
	static void arm(ServerLevel level, Mob mob, int tier) {
		dress(level, mob, Math.min(GRADE_WEIGHTS.length - 1, tier + deepBand(mob)));
	}

	/** Which depth band {@code y} lies in: 0 at or above y 0, then 1 to {@link #DEPTH_BAND_Y}'s length. */
	public static int depthBand(double y) {
		int band = 0;
		for (int i = 0; i < DEPTH_BAND_Y.length; i++) {
			if (y <= DEPTH_BAND_Y[i]) {
				band = i + 1;
			}
		}
		return band;
	}

	/** The depth band {@code mob} spawned in (0 when it wasn't a deep spawn). */
	static int deepBand(Mob mob) {
		for (int band = DEPTH_BAND_Y.length; band > 0; band--) {
			if (mob.entityTags().contains(DEPTH_TAG + band)) {
				return band;
			}
		}
		return 0;
	}

	/** A hostile humanoid {@code mob} that spawned naturally deep underground takes its gear for that depth. */
	public static void armDeep(ServerLevel level, Mob mob) {
		int band = depthBand(mob.getY());
		if (band <= 0 || !(wears(mob) || mob instanceof Vindicator)) {
			return;
		}
		mob.addTag(DEPTH_TAG + band);
		dress(level, mob, band);
	}

	/** Fills {@code mob}'s empty slots from the grade tables' row {@code tier} (1 to 4). */
	private static void dress(ServerLevel level, Mob mob, int tier) {
		if (tier <= 0 || tier >= GRADE_WEIGHTS.length) {
			return;
		}
		if (mob instanceof Vindicator) {
			// an illager's model shows no armour: only the axe it carries is swapped for a better blade
			if (mob.getRandom().nextFloat() < WEAPON_CHANCE[tier]) {
				int grade = grade(mob.getRandom(), tier);
				mob.setItemSlot(EquipmentSlot.MAINHAND, finish(level, mob.getRandom(), weapon(mob.getRandom(), grade), grade));
			}
			return;
		}
		if (!wears(mob)) {
			return;
		}
		RandomSource random = mob.getRandom();
		String set = random.nextFloat() < FANTASY_SET_CHANCE[tier] ? FantasyArmorCompat.pick(FantasyArmorCompat.UNDEAD_SETS, random) : null;
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			EquipmentSlot slot = ARMOR_SLOTS[i];
			Optional<Item> dark = FantasyArmorCompat.piece(set, i);
			if (mob.getItemBySlot(slot).isEmpty() && dark.isPresent()) {
				mob.setItemSlot(slot, finish(level, random, new ItemStack(dark.get()), 4));
			} else if (mob.getItemBySlot(slot).isEmpty() && random.nextFloat() < ARMOR_CHANCE[tier]) {
				int grade = grade(random, tier);
				Item piece = grade < ARMOR.length ? ARMOR[grade][i] : item(LEGENDARY_ARMOR[i]).orElse(ARMOR[ARMOR.length - 1][i]);
				mob.setItemSlot(slot, finish(level, random, new ItemStack(piece), grade));
			}
		}
		boolean zombie = mob instanceof Zombie;
		if (zombie && mob.getMainHandItem().isEmpty() && random.nextFloat() < WEAPON_CHANCE[tier]
			|| !zombie && random.nextFloat() < SKELETON_BLADE_CHANCE[tier]) {
			int grade = grade(random, tier);
			mob.setItemSlot(EquipmentSlot.MAINHAND, finish(level, random, weapon(random, grade), grade));
		}
		if (ShieldBearers.canBear(mob) && random.nextFloat() < SHIELD_CHANCE[tier]) {
			int grade = grade(random, tier);
			mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(item(SHIELDS[grade]).orElse(Items.SHIELD)));
		}
	}

	/** A weapon from world tier {@code tier}'s grades (a zombie horde's armed few, {@link ZombieHordes}). */
	static ItemStack weaponFor(ServerLevel level, RandomSource random, int tier) {
		int grade = grade(random, Mth.clamp(tier, 0, GRADE_WEIGHTS.length - 1));
		return finish(level, random, weapon(random, grade), grade);
	}

	/** A grade, 0 (worn) to 4 (legendary), by the world tier's weights. */
	static int grade(RandomSource random, int tier) {
		int[] weights = GRADE_WEIGHTS[tier];
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int roll = random.nextInt(total);
		for (int grade = 0; grade < weights.length; grade++) {
			roll -= weights[grade];
			if (roll < 0) {
				return grade;
			}
		}
		return 0;
	}

	private static ItemStack weapon(RandomSource random, int grade) {
		String[] choices = WEAPONS[grade];
		return new ItemStack(item(choices[random.nextInt(choices.length)]).orElse(FALLBACK[grade]));
	}

	/** Rare pieces are now and then enchanted, legendary ones always (as a monster's spawn gear can be). */
	private static ItemStack finish(ServerLevel level, RandomSource random, ItemStack stack, int grade) {
		boolean enchant = grade == 4 || grade == 3 && random.nextFloat() < RARE_ENCHANT_CHANCE;
		if (!enchant) {
			return stack;
		}
		return EnchantmentHelper.enchantItem(random, stack, grade == 4 ? LEGENDARY_ENCHANT_LEVEL : RARE_ENCHANT_LEVEL, level.registryAccess(),
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT));
	}

	/**
	 * (0.7.386) A star-touched mob's look ({@link StarTouched}): where it wears armour, each empty slot takes a leather piece dyed
	 * {@code colour} (never dropped); the rest show it by their motes of light.
	 */
	static void starLook(Mob mob, int colour) {
		if (!wears(mob)) {
			return;
		}
		Item[] leather = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			EquipmentSlot slot = ARMOR_SLOTS[i];
			if (mob.getItemBySlot(slot).isEmpty() && mob.getRandom().nextFloat() < STAR_PIECE_CHANCE) {
				ItemStack piece = new ItemStack(leather[i]);
				piece.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(colour));
				mob.setItemSlot(slot, piece);
				mob.setDropChance(slot, 0.0F);
			}
		}
	}

	private static Optional<Item> item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
	}
}
