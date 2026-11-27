package com.jaymospowers.power.web;

import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The suit-up pose (Legends patch 4, {@code ot_suitup}): a short pose while a suit forms over you, arms crossed and then
 * thrown out as it finishes ({@link WebPose#SUIT_UP}). The symbiote's suit forming plays it (from
 * {@link WebSymbiote#set}, by any path that puts the suit on, except the roar and the big body, which have their own
 * forming), and so does putting on a costume ({@code costume.Costume#wear}), for anyone, web slinger or not. The Rig's
 * own suit-ups are {@code rig.RigSuitUp}.
 */
public final class WebSuitUp {
	private static final int COLOR = 0x1B1B26;

	private WebSuitUp() {
	}

	/** Starts the pose on this player, unless a longer move of the suit's own is under way. */
	public static void play(ServerPlayer player, int color) {
		WebPose pose = player.getAttached(WebPower.POSE);
		long now = player.level().getGameTime();
		if (pose != null && now - pose.start() < pose.ticks() && (pose.kind() == WebPose.ROAR || pose.kind() == WebPose.SPIKES)) {
			return;
		}
		player.setAttached(WebPower.POSE, new WebPose(WebPose.SUIT_UP, now, 1));
		ServerLevel level = player.level();
		Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		// the flash comes as the arms are thrown out
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.8F, color);
		MagicFx.blast(level, chest, 0.9F, color);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
	}

	/** The symbiote's suit has formed. */
	static void suit(ServerPlayer player) {
		play(player, COLOR);
	}

	/** A costume has gone on (or changed). */
	public static void costume(ServerPlayer player) {
		play(player, 0xE6E0C8);
	}
}
