package com.jaymoskingdoms.sailing;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) The kinds of shot a ship's gun (or a shore gun) fires, each its own item: round shot (the old reliable),
 * heavy shot (slower, punches through planking and strips the shore deeper), an explosive shell (bursts on contact,
 * blasts a wide crater), chain shot (tears sails and rigging, not planks) and grape (a spray against crews and
 * foliage). The numbers are all here; {@link Bombardment} reads the crater.
 */
public enum ShotKind implements StringRepresentable {
	ROUND(1.0, 1.0, 160, 16.0F, 2.0, 0.8, 0, false, new Bombardment.Crater(3.2, 2, 4, 7.5F, Bombardment.Strip.STRUCTURES, 14, false)),
	HEAVY(0.85, 1.3, 170, 24.0F, 2.6, 1.1, 2, false, new Bombardment.Crater(4.8, 3, 6, 10.0F, Bombardment.Strip.MASONRY, 16, true)),
	SHELL(0.9, 1.2, 170, 20.0F, 4.6, 1.5, 0, true, new Bombardment.Crater(7.5, 4, 9, 14.0F, Bombardment.Strip.MASONRY, 18, true)),
	CHAIN(1.05, 0.9, 120, 12.0F, 2.8, 0.6, 0, false, new Bombardment.Crater(1.6, 0, 3, 1.0F, Bombardment.Strip.SOFT, 30, false)),
	GRAPE(1.0, 0.8, 14, 9.0F, 5.0, 0.5, 0, false, new Bombardment.Crater(2.6, 0, 3, 1.0F, Bombardment.Strip.SOFT, 30, false));

	/** Muzzle speed and fall as shares of {@link ShipShot}'s; how long it flies (ticks). */
	public final double speed, gravity;
	public final int life;
	/** Its blow, the radius of its burst (blocks) and its shove. */
	public final float damage;
	public final double burst, knockback;
	/** How many more ship blocks it punches through after the first; whether it tears a wide hole where it strikes. */
	public final int pierce;
	public final boolean splash;
	/** What it does to the ground it lands on. */
	public final Bombardment.Crater crater;

	ShotKind(double speed, double gravity, int life, float damage, double burst, double knockback, int pierce, boolean splash, Bombardment.Crater crater) {
		this.speed = speed;
		this.gravity = gravity;
		this.life = life;
		this.damage = damage;
		this.burst = burst;
		this.knockback = knockback;
		this.pierce = pierce;
		this.splash = splash;
		this.crater = crater;
	}

	@Override
	public String getSerializedName() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** The item this is loaded from. */
	public Item item() {
		return switch (this) {
			case ROUND -> Sailing.ROUND_SHOT;
			case HEAVY -> Sailing.HEAVY_SHOT;
			case SHELL -> Sailing.SHELL;
			case CHAIN -> Sailing.CHAIN_SHOT;
			case GRAPE -> Sailing.GRAPE_SHOT;
		};
	}

	/** What {@code stack} loads as (WeaponMod's cannonball is round shot), or null if it isn't shot. */
	public static @Nullable ShotKind of(ItemStack stack) {
		for (ShotKind kind : values()) {
			if (stack.is(kind.item())) {
				return kind;
			}
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.toString().equals(ShipCannonBlock.WEAPONMOD_BALL) ? ROUND : null;
	}

	/** The shot a gun in {@code load} holds, or null if it isn't loaded with one. */
	public static @Nullable ShotKind of(ShipCannonBlock.Load load) {
		return switch (load) {
			case SHOT -> ROUND;
			case HEAVY -> HEAVY;
			case SHELL -> SHELL;
			case CHAIN -> CHAIN;
			case GRAPE -> GRAPE;
			default -> null;
		};
	}

	/** The load state of a gun holding this. */
	public ShipCannonBlock.Load load() {
		return switch (this) {
			case ROUND -> ShipCannonBlock.Load.SHOT;
			case HEAVY -> ShipCannonBlock.Load.HEAVY;
			case SHELL -> ShipCannonBlock.Load.SHELL;
			case CHAIN -> ShipCannonBlock.Load.CHAIN;
			case GRAPE -> ShipCannonBlock.Load.GRAPE;
		};
	}
}
