package com.jaymospowers.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.ThrownWeapon;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A seeking blade: a throwing weapon (a dagger, hatchet, kunai, star) with a lodestone chip set in it bends gently toward the
 * nearest hostile thing ahead of it once thrown. The chip is made from lodestone (crafting table) and set in by clicking it
 * onto the weapon in the inventory ({@link com.jaymospowers.armory.ThrowingWeaponItem}); the weapon's tooltip shows it.
 */
public final class SeekingChip {
	// ---- tuning ----
	/** How far ahead (blocks) it looks, how wide a cone it looks in (cosine of the half-angle), and how much of the turn it takes each tick (0 to 1). */
	public static final double REACH = 18.0, CONE = 0.55, TURN = 0.12;
	/** Chips one lodestone makes. */
	public static final int CHIPS_PER_LODESTONE = 4;
	/** Custom-data key of a chipped weapon. */
	public static final String KEY = "jaymospowers_seeking";

	public static Item ITEM;

	private SeekingChip() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("lodestone_chip"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new ChipItem(new Item.Properties().setId(key)));
	}

	/** The loose chip. */
	public static final class ChipItem extends Item {
		ChipItem(Properties properties) {
			super(properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.lodestone_chip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.lodestone_chip.how").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public static boolean chip(ItemStack stack) {
		return stack.is(ITEM);
	}

	/** Whether a chip is set in this weapon. */
	public static boolean seeking(ItemStack weapon) {
		CustomData data = weapon.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(KEY, false);
	}

	/** Sets a chip in every piece of {@code weapon} (one chip each), taking them from {@code chips}. False when there aren't enough or it is already chipped. */
	public static boolean set(ItemStack weapon, ItemStack chips) {
		if (!chip(chips) || seeking(weapon) || chips.getCount() < weapon.getCount()) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.putBoolean(KEY, true));
		chips.shrink(weapon.getCount());
		return true;
	}

	/** One tick of a chipped weapon's flight (server): it turns a little toward the nearest hostile thing in the cone ahead of it. */
	public static void steer(ThrownWeapon weapon) {
		if (weapon.struck() || weapon.lying() || weapon.tickCount % 2 != 0 || !seeking(weapon.getItem())) {
			return;
		}
		Vec3 velocity = weapon.getDeltaMovement();
		double speed = velocity.length();
		if (speed < 0.3) {
			return;
		}
		Vec3 ahead = velocity.scale(1.0 / speed);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : weapon.level().getEntitiesOfClass(LivingEntity.class, weapon.getBoundingBox().inflate(REACH),
			e -> e.isAlive() && !e.isSpectator() && e != weapon.getOwner() && hostile(e, weapon.getOwner()))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(weapon.position());
			double distance = to.length();
			if (distance > REACH || distance < 0.5 || to.scale(1.0 / distance).dot(ahead) < CONE || distance >= bestDistance || !e.hasLineOfSight(weapon)) {
				continue;
			}
			best = e;
			bestDistance = distance;
		}
		if (best != null) {
			Vec3 want = best.getBoundingBox().getCenter().subtract(weapon.position()).normalize();
			// (twice the turn: this runs every other tick)
			weapon.setDeltaMovement(ahead.scale(1.0 - 2.0 * TURN).add(want.scale(2.0 * TURN)).normalize().scale(speed));
			weapon.needsSync = true;
		}
	}

	/** Whether {@code e} is something to seek: a hostile mob, or whatever is after the thrower. */
	private static boolean hostile(LivingEntity e, @Nullable Entity owner) {
		if (e instanceof Player || e instanceof TamableAnimal tame && tame.isTame()) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == owner || owner instanceof LivingEntity thrower && thrower.getLastHurtMob() == e;
	}
}
