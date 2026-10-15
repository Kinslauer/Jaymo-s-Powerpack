package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Z, Smoke Sight (a mode): while it's on, every creature standing in smoke (anyone's) within {@link #RANGE} blocks is
 * outlined through walls on your screen, darker for the hostile, and your own smoke is drawn fainter still. The client
 * decides who's outlined from the smoke it was sent ({@code SmokeSightClient}); the server only keeps the switch
 * ({@link #ON}, synced to you) and takes a little hunger while it's on. Not for a fresh awakening (only Vapour Form).
 */
public final class SmokeSight {
	/** How far the outlines reach (blocks), and how thick the smoke round a creature must be (at its feet or eyes). */
	public static final double RANGE = 32.0;
	public static final float THICK = 0.5F;
	/** Hunger a tick while it's on. */
	static final float EXHAUSTION = 0.004F;

	/** On a Smoke user with Smoke Sight on; synced to them only. */
	public static final AttachmentType<Boolean> ON = AttachmentRegistry.create(JaymosPowers.id("smoke_sight"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	private SmokeSight() {
	}

	/** Smoke Sight is on (both sides). */
	public static boolean on(Player player) {
		return player.getAttached(ON) != null && Powers.has(player, SmokePower.INSTANCE);
	}

	/** Whether Z does anything yet: everything but a fresh awakening. */
	static boolean unlocked(Player player) {
		return !Mastery.starterOnly(player);
	}

	/** Z: on, or off. */
	static void toggle(ServerPlayer player) {
		if (player.getAttached(ON) != null) {
			off(player);
			return;
		}
		if (!unlocked(player)) {
			return;
		}
		player.setAttached(ON, true);
		SmokeMoves.begin(player, SmokeMoves.SIGHT);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	static void off(ServerPlayer player) {
		if (player.getAttached(ON) != null) {
			player.removeAttached(ON);
		}
	}

	static void tick(ServerPlayer player) {
		if (player.getAttached(ON) != null && player.isAlive() && !player.getAbilities().instabuild) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
	}
}
