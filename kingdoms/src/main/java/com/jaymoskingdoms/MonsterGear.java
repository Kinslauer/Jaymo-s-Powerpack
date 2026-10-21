package com.jaymoskingdoms;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
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
 * <li>Legendary: diamond, always enchanted</li>
 * </ol>
 * They drop it as vanilla's mobs do (now and then, worn). Gear a monster already had is kept.
 */
public final class MonsterGear {
	/** Per world tier (Hearth to Legend): the chance each empty armour slot is filled. */
	static final float[] ARMOR_CHANCE = {0.0F, 0.25F, 0.40F, 0.55F, 0.70F};
	/** Per world tier: the chance an empty-handed zombie takes up a weapon, and a skeleton trades its bow for a blade. */
	static final float[] WEAPON_CHANCE = {0.0F, 0.15F, 0.25F, 0.35F, 0.45F};
	static final float[] SKELETON_BLADE_CHANCE = {0.0F, 0.03F, 0.06F, 0.10F, 0.15F};
	/** Per world tier: the chance an armed zombie carries a shield. */
	static final float[] SHIELD_CHANCE = {0.0F, 0.05F, 0.12F, 0.20F, 0.30F};
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

	/** Per grade: helmet, chestplate, leggings, boots. */
	private static final Item[][] ARMOR = {
		{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
		{Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS},
		{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
		{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}};
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Per grade, the weapons (Jaymo's Powers' ids are used when it's there, the vanilla one after each otherwise). */
	private static final String[][] WEAPONS = {
		{"minecraft:stone_sword", "minecraft:stone_axe", "jaymospowers:stone_rapier"},
		{"minecraft:copper_sword", "minecraft:copper_axe", "jaymospowers:iron_dagger"},
		{"minecraft:iron_sword", "jaymospowers:iron_saber", "jaymospowers:iron_rapier"},
		{"jaymospowers:iron_rapier", "jaymospowers:iron_longsword", "minecraft:iron_axe"},
		{"minecraft:diamond_sword", "jaymospowers:diamond_rapier", "minecraft:diamond_axe"}};
	private static final Item[] FALLBACK = {Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.IRON_AXE, Items.DIAMOND_SWORD};
	/** Per grade, the shield (a round shield from Jaymo's Powers above common). */
	private static final String[] SHIELDS = {"minecraft:shield", "minecraft:shield", "jaymospowers:iron_round_shield",
		"jaymospowers:iron_round_shield", "jaymospowers:diamond_round_shield"};

	private MonsterGear() {
	}

	/** Who wears it: the zombies (husks, drowned, zombified piglins, and the like) and the skeletons, whose bodies show armour. */
	static boolean wears(Mob mob) {
		return mob instanceof Zombie || mob instanceof AbstractSkeleton;
	}

	/** {@code mob} takes world tier {@code tier} for the first time: its gear for that tier. */
	static void arm(ServerLevel level, Mob mob, int tier) {
		if (tier <= 0 || tier >= GRADE_WEIGHTS.length || !wears(mob)) {
			return;
		}
		RandomSource random = mob.getRandom();
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			EquipmentSlot slot = ARMOR_SLOTS[i];
			if (mob.getItemBySlot(slot).isEmpty() && random.nextFloat() < ARMOR_CHANCE[tier]) {
				int grade = grade(random, tier);
				mob.setItemSlot(slot, finish(level, random, new ItemStack(ARMOR[grade][i]), grade));
			}
		}
		boolean zombie = mob instanceof Zombie;
		if (zombie && mob.getMainHandItem().isEmpty() && random.nextFloat() < WEAPON_CHANCE[tier]
			|| !zombie && random.nextFloat() < SKELETON_BLADE_CHANCE[tier]) {
			int grade = grade(random, tier);
			mob.setItemSlot(EquipmentSlot.MAINHAND, finish(level, random, weapon(random, grade), grade));
		}
		if (zombie && !mob.getMainHandItem().isEmpty() && mob.getOffhandItem().isEmpty() && random.nextFloat() < SHIELD_CHANCE[tier]) {
			int grade = grade(random, tier);
			mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(item(SHIELDS[grade]).orElse(Items.SHIELD)));
		}
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

	private static Optional<Item> item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
	}
}
