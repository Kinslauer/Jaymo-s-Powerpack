package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy and light (Andy's Awakening pick: "Gravity, on its mode key", "tie into weightlessness"). A Gravity user sets their own
 * weight on a scale that runs heavy, normal, light and, at its far end, Weightless itself:
 * <ul>
 * <li>hold Z (past {@link #HOLD_TICKS}): one step lighter. From light, the next step is Weightless (when V is yours);</li>
 * <li>sneak and hold Z: one step heavier. From Weightless, the step down is light.</li>
 * </ul>
 * A tap of Z still raises the Immense Field (sneak + tap: Trading Places); since a hold is now told from a tap, both answer as the
 * key comes up rather than as it goes down.
 * <p><b>Light</b>: less gravity, higher jumps, and falls land softer. <b>Heavy</b>: knockback hardly moves you, you come down
 * harder through the air, and in water you sink instead of floating.
 */
final class GravityHeft {
	// ---- tuning ----
	/** Ticks Z must be held to step the weight instead of tapping. */
	static final int HOLD_TICKS = 8;
	/** Light: gravity, jump and fall damage, as a share added (negative: less). */
	static final double LIGHT_GRAVITY = -0.4, LIGHT_JUMP = 0.35, LIGHT_FALL = -0.5;
	/** Heavy: knockback resistance added, gravity added, the pull down in water (blocks a tick, a tick). */
	static final double HEAVY_KNOCKBACK = 0.8, HEAVY_GRAVITY = 0.5, HEAVY_SINK = 0.035;

	static final int HEAVY = -1, NORMAL = 0, LIGHT = 1;

	private static final Identifier GRAVITY_ID = JaymosPowers.id("gravity_heft_gravity"), JUMP_ID = JaymosPowers.id("gravity_heft_jump"),
		FALL_ID = JaymosPowers.id("gravity_heft_fall"), KNOCK_ID = JaymosPowers.id("gravity_heft_knockback");

	/** Each player's weight (not saved: a relog sets it back to normal) and when Z went down. */
	private static final Map<UUID, Integer> WEIGHT = new HashMap<>();
	private static final Map<UUID, Long> DOWN_AT = new HashMap<>();

	private GravityHeft() {
	}

	/** Z went down or up. True if this was a hold (the weight stepped), false if it was a tap the mode should answer. */
	static boolean key(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		if (down) {
			DOWN_AT.put(player.getUUID(), now);
			return true;
		}
		Long at = DOWN_AT.remove(player.getUUID());
		if (at == null || now - at < HOLD_TICKS) {
			return false;
		}
		step(player, player.isShiftKeyDown() ? -1 : 1);
		return true;
	}

	/** One step along the scale ({@code by}: +1 lighter, -1 heavier). */
	static void step(ServerPlayer player, int by) {
		boolean weightless = player.getAttached(GravityPower.WEIGHTLESS_ON) != null;
		int weight = WEIGHT.getOrDefault(player.getUUID(), NORMAL);
		if (weightless) {
			if (by < 0) {
				// out of Weightless, down to light
				GravityPower.heftWeightless(player, false);
				set(player, LIGHT);
			}
			return;
		}
		int next = weight + by;
		if (next > LIGHT) {
			if (Mastery.isUnlocked(player, PowerKey.ABILITY_3) && GravityPower.heftWeightless(player, true)) {
				set(player, NORMAL);
			}
			return;
		}
		if (next < HEAVY) {
			return;
		}
		set(player, next);
	}

	private static void set(ServerPlayer player, int weight) {
		if (weight == NORMAL) {
			WEIGHT.remove(player.getUUID());
		} else {
			WEIGHT.put(player.getUUID(), weight);
		}
		apply(player, weight);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_heft." + (weight == LIGHT ? "light" : weight == HEAVY ? "heavy" : "normal"))
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), weight == HEAVY ? SoundEvents.ANVIL_PLACE : SoundEvents.AMETHYST_BLOCK_CHIME,
			SoundSource.PLAYERS, 0.6F, weight == HEAVY ? 0.5F : weight == LIGHT ? 1.6F : 1.0F);
		MagicFx.ring(player.level(), player.position().add(0.0, 0.1, 0.0), weight == HEAVY ? 1.2F : 2.0F, weight == HEAVY ? 0x3D2E6B : 0xB9A8FF);
	}

	private static void apply(ServerPlayer player, int weight) {
		GravityPower.modifier(player, Attributes.GRAVITY, GRAVITY_ID, weight == LIGHT ? LIGHT_GRAVITY : HEAVY_GRAVITY, weight != NORMAL);
		GravityPower.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, LIGHT_JUMP, weight == LIGHT);
		GravityPower.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, LIGHT_FALL, weight == LIGHT);
		GravityPower.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, HEAVY_KNOCKBACK, weight == HEAVY);
	}

	static int weight(ServerPlayer player) {
		return WEIGHT.getOrDefault(player.getUUID(), NORMAL);
	}

	/** Every tick: the heavy sink in water. */
	static void tick(ServerPlayer player) {
		if (weight(player) != NORMAL && player.getAttribute(Attributes.GRAVITY) != null && !player.getAttribute(Attributes.GRAVITY).hasModifier(GRAVITY_ID)) {
			// died and came back: the weight went with the old body
			WEIGHT.remove(player.getUUID());
		}
		if (weight(player) == HEAVY && player.isInWater() && !player.getAbilities().flying) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.min(v.y, 0.0) - HEAVY_SINK, v.z);
			player.syncVelocity = true;
		}
	}

	/** The power is lost, or the player leaves: back to normal. */
	static void clear(ServerPlayer player) {
		WEIGHT.remove(player.getUUID());
		DOWN_AT.remove(player.getUUID());
		apply(player, NORMAL);
	}
}
