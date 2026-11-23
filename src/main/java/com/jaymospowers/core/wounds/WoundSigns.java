package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Wounds shown like potions (Andy, 2026-11-23: "there should be potion like indicators for when you are bleeding or have
 * breaks / lost limbs"): a player bleeding, with a broken bone or with a lost limb carries a matching effect, its level
 * the number of parts (two broken limbs: Broken Bone II). They only show the state ({@link Bleeding}, {@link Breaks},
 * {@link Dismember#MISSING}) and do nothing themselves; they're put right every {@link #CHECK_TICKS}, so milk or a cure
 * can't hide a wound for long.
 */
public final class WoundSigns {
	/** How often the effects are put right (ticks). */
	static final int CHECK_TICKS = 20;

	public static Holder<MobEffect> BLEEDING, BROKEN_BONE, LOST_LIMB;

	private WoundSigns() {
	}

	/** The effect shows the state and does nothing on its own. */
	static final class Sign extends MobEffect {
		Sign(int colour) {
			super(MobEffectCategory.HARMFUL, colour);
		}
	}

	static void register() {
		BLEEDING = effect("bleeding", 0x8A0303);
		BROKEN_BONE = effect("broken_bone", 0xE3D9BF);
		LOST_LIMB = effect("lost_limb", 0x4A1010);
		ServerTickEvents.END_SERVER_TICK.register(WoundSigns::tick);
	}

	private static Holder<MobEffect> effect(String id, int colour) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id(id)),
			new Sign(colour));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean alive = player.isAlive() && !player.isCreative() && !player.isSpectator();
			show(player, BLEEDING, alive ? Integer.bitCount(Bleeding.bleeding(player)) : 0);
			show(player, BROKEN_BONE, alive ? Integer.bitCount(Breaks.broken(player)) : 0);
			show(player, LOST_LIMB, alive ? Integer.bitCount(Dismember.missing(player)) : 0);
		}
	}

	/** {@code count} parts: the effect at that level (count 1 is level I), or none. */
	private static void show(ServerPlayer player, Holder<MobEffect> sign, int count) {
		MobEffectInstance shown = player.getEffect(sign);
		if (count <= 0) {
			if (shown != null) {
				player.removeEffect(sign);
			}
			return;
		}
		if (shown == null || shown.getAmplifier() != count - 1 || !shown.isInfiniteDuration()) {
			player.forceAddEffect(new MobEffectInstance(sign, MobEffectInstance.INFINITE_DURATION, count - 1, false, false, true), null);
		}
	}
}
