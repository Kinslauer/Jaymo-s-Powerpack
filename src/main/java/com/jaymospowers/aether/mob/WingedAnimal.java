package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherFlora;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.Level;

/**
 * A rideable animal with wings (the phyg, the flying cow): a high jump from the ground, one flap more in mid-air, and
 * a glide while jump is held. The wings are drawn open when it is airborne and folded when it stands.
 */
public abstract class WingedAnimal extends RideableAnimal {
	// ---- tuning ----
	/** Upward speed of the jump from the ground and of the mid-air flap (blocks a tick; 0.42 is a vanilla jump). */
	public static final double GROUND_JUMP = 0.62, AIR_JUMP = 0.55;
	/** Flaps in mid-air after leaving the ground. */
	public static final int AIR_JUMPS = 1;

	protected WingedAnimal(EntityType<? extends WingedAnimal> type, Level level) {
		super(type, level);
	}

	/** Valkyrie wings calm it: whoever holds them can climb on (and keeps it calm only while held). */
	@Override
	protected @Nullable Item calmItem() {
		return AetherFlora.VALKYRIE_WINGS;
	}

	@Override
	protected int maxAirJumps() {
		return AIR_JUMPS;
	}

	@Override
	protected double groundJump() {
		return GROUND_JUMP;
	}

	@Override
	protected double airJump() {
		return AIR_JUMP;
	}
}
