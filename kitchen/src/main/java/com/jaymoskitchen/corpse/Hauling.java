package com.jaymoskitchen.corpse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.36) Andy: "make it slowness IV and obviously the stronger characters can lift with less encumberment". How much a
 * body slows whoever hauls it: a dragged carcass is Slowness IV ({@link #DRAG_SLOWNESS}), and each point of brawn takes a
 * level off. Brawn is the Strength effect (its level), being bigger than a person ({@link #SCALE_PER_BRAWN}), and
 * whatever other mods add through {@link #addBrawn} (Jaymo's Kingdoms: Might and Packhorse; Jaymo's Powers: Z Fighters).
 * With {@link #LIFT_BIG_BRAWN} you're strong enough to lift a carcass too big for anyone else's arms.
 */
public final class Hauling {
	/** Slowness amplifiers before brawn: dragging (3 = IV), carrying a big carcass (III), carrying anything else (I). */
	public static final int DRAG_SLOWNESS = 3, CARRY_BIG_SLOWNESS = 2, CARRY_SLOWNESS = 0;
	/** Brawn that lifts a cow-sized (or horse-sized) carcass into your arms instead of dragging it. */
	public static final int LIFT_BIG_BRAWN = 4;
	/** A point of brawn for each this much bigger than normal size (a giant form at 2x is two). */
	public static final float SCALE_PER_BRAWN = 0.5F;

	private static final List<ToIntFunction<Player>> SOURCES = new CopyOnWriteArrayList<>();

	private Hauling() {
	}

	/** Other mods (by reflection, so they need no Kitchen jar): {@code source} adds its points to every player's brawn. */
	public static void addBrawn(ToIntFunction<Player> source) {
		SOURCES.add(source);
	}

	/** How strong {@code player} is at hauling bodies: 0 for most. */
	public static int brawn(Player player) {
		int brawn = 0;
		@Nullable MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
		if (strength != null) {
			brawn += strength.getAmplifier() + 1;
		}
		brawn += Math.max(0, (int) ((player.getScale() - 1.0F) / SCALE_PER_BRAWN));
		for (ToIntFunction<Player> source : SOURCES) {
			try {
				brawn += Math.max(0, source.applyAsInt(player));
			} catch (RuntimeException e) {
				// a broken source counts for nothing
			}
		}
		return brawn;
	}

	/** Whether {@code player} can lift a carcass too big for anyone else's arms. */
	public static boolean canLiftBig(Player player) {
		return brawn(player) >= LIFT_BIG_BRAWN;
	}

	/** Slows {@code player} under a load of {@code base} (a Slowness amplifier), less by their brawn; for 40 ticks. */
	static void encumber(Player player, int base) {
		int amplifier = base - brawn(player);
		if (amplifier >= 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, amplifier, false, false, true));
		}
	}
}
