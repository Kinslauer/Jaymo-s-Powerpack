package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * How bad you smell (Andy's TFC Hardrock picks: "if you stink you'll be caught, especially among non humans with better
 * noses. Villagers will comment on this"). Unwashed days, sweat (sprinting) and grime ({@link ArmourGrime}) build it
 * up, 0 to {@link #MAX} ({@link #smell}); water washes it off: under water at once, wading or in the rain slowly, and a
 * scrub at a water cauldron (sneak, empty hand: a wash tub, a bath house) takes most of it off for one level of water.
 * Jaymo's Kingdoms reads it: monsters and beasts smell you from further away, people a little, and villagers remark on it.
 */
public final class Smell {
	public static final float MAX = 100.0F;
	/** Stink each check (every {@link #CHECK_TICKS}): just living, sprinting on top; washing off under water, wading, rain. */
	static final float LIVING = 0.05F, SPRINTING = 0.4F, WADING = 8.0F, RAIN = 1.5F;
	static final int CHECK_TICKS = 40;
	/** How much the worst grime on your armour adds (at its filthiest), and a scrub at a cauldron takes off. */
	static final float GRIME_SHARE = 40.0F, SCRUB = 70.0F;
	/** How much old blood on you adds at its worst (soaked in rotten blood, {@code Bloodied.reek}). */
	static final float BLOOD_SHARE = 75.0F;

	/** Your own stink, before grime. Saved on the player. */
	public static final AttachmentType<Float> STINK = AttachmentRegistry.create(JaymosPowers.id("stink"), b -> b.persistent(Codec.FLOAT).copyOnDeath());

	private Smell() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Smell::tick);
	}

	/** A scrub at a water cauldron ({@link ArmourGrime}'s, sneaking with an empty hand). */
	static void scrub(Player player) {
		set(player, stink(player) - SCRUB);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.smell.washed"));
	}

	public static float stink(Player player) {
		return player.getAttachedOrElse(STINK, 0.0F);
	}

	static void set(Player player, float stink) {
		float s = Mth.clamp(stink, 0.0F, MAX);
		if (s <= 0.0F) {
			player.removeAttached(STINK);
		} else {
			player.setAttached(STINK, s);
		}
	}

	/**
	 * How bad {@code player} smells, 0 (clean) to {@link #MAX}: their stink, the worst grime on their armour, and old blood
	 * on them (rotten blood stinks the worst).
	 */
	public static float smell(Player player) {
		float grime = 0.0F;
		for (net.minecraft.world.entity.EquipmentSlot slot : ArmourGrime.SLOTS) {
			ArmourGrime.Grime g = ArmourGrime.grime(player.getItemBySlot(slot));
			if (g != null) {
				grime = Math.max(grime, g.level());
			}
		}
		return Math.min(MAX, stink(player) + grime * GRIME_SHARE + com.jaymospowers.core.blood.Bloodied.reek(player) * BLOOD_SHARE);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			float s = stink(player);
			if (player.isUnderWater()) {
				s = 0.0F;
			} else if (player.isInWater()) {
				s -= WADING;
			} else if (player.isInWaterOrRain()) {
				s -= RAIN;
			} else {
				s += LIVING + (player.isSprinting() ? SPRINTING : 0.0F);
			}
			set(player, s);
		}
	}
}
