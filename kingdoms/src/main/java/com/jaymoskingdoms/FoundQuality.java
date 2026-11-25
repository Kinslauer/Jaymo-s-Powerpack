package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Found gear rolls a quality from where it was found (Apotheosis's affixes, ARR: the idea only, our own code). A weapon or a piece of
 * armour that turns up in loot (a chest, a mob's drop) in the Aether is sometimes <b>airy</b> and in the Nether <b>fiery</b>; the
 * quality has a grade of 1 to 3 ({@link #GRADES}, mostly 1). Worn or carried it works:
 * <ul>
 * <li><b>airy</b>: a blow from it throws its foe up off their feet, and each airy piece worn softens a fall;</li>
 * <li><b>fiery</b>: a blow from it sets its foe alight, and each fiery piece worn takes the edge off fire.</li>
 * </ul>
 * Kept on the item ({@link #KEY}, grade after a colon: {@code "fire:2"}) and named in its lore. The Artificer's reforge
 * ({@link Artificers}) can roll one on gear of any origin.
 */
public final class FoundQuality {
	/** The chance a piece of gear found in a region with a quality of its own has it. */
	static final float CHANCE = 0.3F;
	/** Weights of grade 1, 2 and 3. */
	static final int[] GRADES = {70, 25, 5};
	/** A blow from airy gear lifts its foe this much a grade; a blow from fiery gear sets it alight this many seconds a grade, and two over. */
	static final double LIFT = 0.18;
	static final int BURN = 2;
	/** Each worn piece takes this share of fire's or a fall's harm off per grade. */
	static final float EASE = 0.08F;
	static final String KEY = "jaymoskingdoms_quality";
	public static final String AIR = "air", FIRE = "fire";

	private FoundQuality() {
	}

	static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ServerLevel level = context.getLevel();
			Vec3 at = context.getOptional(LootContextParams.ORIGIN);
			String kind = at == null ? null : region(level, BlockPos.containing(at));
			if (kind == null) {
				return;
			}
			RandomSource random = context.getRandom();
			for (ItemStack drop : drops) {
				if (gear(drop) && kind(drop) == null && random.nextFloat() < CHANCE) {
					put(drop, kind, grade(random));
				}
			}
		});
	}

	/** The quality gear found at {@code pos} may have, or null. */
	static @Nullable String region(ServerLevel level, BlockPos pos) {
		if (SkyFolk.at(level, pos)) {
			return AIR;
		}
		return level.dimension() == Level.NETHER ? FIRE : null;
	}

	/** Weapons and armour. */
	static boolean gear(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return false;
		}
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			return equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
		}
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ClassPerks.TWO_HANDED) || stack.is(ClassPerks.LIGHT_BLADES) || stack.is(ClassPerks.BLUDGEONS);
	}

	static int grade(RandomSource random) {
		int roll = random.nextInt(GRADES[0] + GRADES[1] + GRADES[2]);
		return roll < GRADES[0] ? 1 : roll < GRADES[0] + GRADES[1] ? 2 : 3;
	}

	/** The quality kind on {@code stack}, or null. */
	public static @Nullable String kind(ItemStack stack) {
		String v = raw(stack);
		return v.isEmpty() ? null : v.substring(0, v.indexOf(':'));
	}

	/** The grade of the quality on {@code stack} (0: none). */
	public static int gradeOf(ItemStack stack) {
		String v = raw(stack);
		return v.isEmpty() ? 0 : Integer.parseInt(v.substring(v.indexOf(':') + 1));
	}

	private static String raw(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		String v = data == null ? "" : data.copyTag().getStringOr(KEY, "");
		return v.indexOf(':') < 0 ? "" : v;
	}

	/** Gives {@code stack} the quality {@code kind} at {@code grade} (replacing any), and names it in its lore. */
	public static void put(ItemStack stack, String kind, int grade) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, kind + ":" + grade));
		ItemLore old = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> lines = new ArrayList<>();
		for (Component line : old.lines()) {
			if (!(line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) || !t.getKey().startsWith("quality.jaymoskingdoms.")) {
				lines.add(line);
			}
		}
		lines.add(Component.translatable("quality.jaymoskingdoms." + kind, Component.translatable("quality.jaymoskingdoms.grade." + grade))
			.withStyle(kind.equals(FIRE) ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.AQUA));
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	/** A blow's say: what the attacker's weapon does to the target, and what the target's worn pieces take off fire and falls. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity attacker && attacker != target) {
			ItemStack weapon = source.getDirectEntity() instanceof AbstractArrow arrow ? arrow.getWeaponItem() : source.getDirectEntity() == attacker ? attacker.getMainHandItem() : null;
			String kind = weapon == null ? null : kind(weapon);
			if (kind != null) {
				int grade = gradeOf(weapon);
				if (kind.equals(FIRE)) {
					target.igniteForSeconds(BURN * grade + 2);
				} else {
					target.setDeltaMovement(target.getDeltaMovement().add(0.0, LIFT * grade, 0.0));
					target.syncVelocity = true;
				}
			}
		}
		boolean fire = source.is(DamageTypeTags.IS_FIRE), fall = source.is(DamageTypeTags.IS_FALL);
		if (fire || fall) {
			String want = fire ? FIRE : AIR;
			float eased = 0.0F;
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack worn = target.getItemBySlot(slot);
				if (want.equals(kind(worn))) {
					eased += EASE * gradeOf(worn);
				}
			}
			amount *= Math.max(0.25F, 1.0F - eased);
		}
		return amount;
	}
}
