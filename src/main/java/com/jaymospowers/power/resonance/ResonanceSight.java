package com.jaymospowers.power.resonance;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.blindness.Blind;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Sneak + V, <b>Sky Sight</b> (ascended, and only in the dark: both eyes gone, or a blindfold on): the view rises high
 * over you and looks down, and the living burn through the dark twice as far ({@code BlindnessDevoutSight} draws it
 * from {@link #ACTIVE}). Again to come back down. It ends by itself when you can see again.
 */
public final class ResonanceSight {
	/** Up in Sky Sight. Synced to its owner only. Not saved. */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("resonance_sky_sight"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	private ResonanceSight() {
	}

	/** Sneak + V. True when the press was Sky Sight's (so it isn't also an Echolocation). */
	static boolean key(ServerPlayer player) {
		if (!player.isShiftKeyDown() || !Fate.isAscended(player)) {
			return false;
		}
		if (player.hasAttached(ACTIVE)) {
			end(player);
			return true;
		}
		if (!Blind.sightless(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_sky_sight_eyes").withStyle(ChatFormatting.GRAY));
			return true;
		}
		player.setAttached(ACTIVE, true);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.blindness.sky_sight.on").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	static void tick(ServerPlayer player) {
		if (player.hasAttached(ACTIVE) && (!Blind.sightless(player) || !Fate.isAscended(player))) {
			end(player);
		}
	}

	static void end(ServerPlayer player) {
		if (player.hasAttached(ACTIVE)) {
			player.removeAttached(ACTIVE);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blindness.sky_sight.off").withStyle(ChatFormatting.GRAY));
		}
	}
}
