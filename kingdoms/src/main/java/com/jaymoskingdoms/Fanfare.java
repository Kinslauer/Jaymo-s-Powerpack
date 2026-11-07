package com.jaymoskingdoms;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.464) The one fanfare every progression milestone in the pack plays, so reaching one always feels the same way: a
 * big title with what you earned under it, a layered chord (the level-up chime, and for bigger steps the challenge
 * fanfare and a beacon's hum, then a totem's flourish for the grandest), a burst of sparks in the track's colour that
 * people nearby see and hear too, and a gold line in chat saying what it gives.
 *
 * <p>Three sizes: {@link #MINOR} (a skill level), {@link #MAJOR} (every fifth level, with its bonus point), {@link #GRAND}
 * (the top of a pillar). A copy of Jaymo's Powers' {@code core.Milestones} (Kingdoms doesn't depend on it), without its
 * screen kick.
 */
public final class Fanfare {
	public static final int MINOR = 1, MAJOR = 2, GRAND = 3;
	/** Title timing (fade in, stay, fade out), ticks, by size. */
	static final int[] TITLE_STAY = {0, 40, 60, 90};
	/** Sparks in the burst, by size, and how far the ring reaches (blocks). */
	static final int[] SPARKS = {0, 16, 36, 70};
	static final double RING = 1.2;
	/** How loud the chord is (others nearby hear it too). */
	static final float VOLUME = 0.9F;

	private Fanfare() {
	}

	/**
	 * Plays the fanfare for {@code player}: {@code title} big, {@code subtitle} under it, {@code chat} (may be null) as a
	 * gold line, sparks in {@code color} (0xRRGGBB), at {@code size} ({@link #MINOR}-{@link #GRAND}).
	 */
	public static void fanfare(ServerPlayer player, Component title, @Nullable Component subtitle, @Nullable Component chat, int color, int size) {
		int s = Math.max(MINOR, Math.min(GRAND, size));
		if (s >= MAJOR) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(8, TITLE_STAY[s], 20));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
		} else {
			// a small step only flashes over the hotbar
			player.sendOverlayMessage(subtitle == null ? title : title.copy().append(" ").append(subtitle));
		}
		if (chat != null) {
			player.sendSystemMessage(chat.copy().withStyle(ChatFormatting.GOLD));
		}
		chord(player, s);
		burst(player, color, s);
	}

	/** The chord alone (layered, so a bigger step sounds bigger). */
	public static void chord(ServerPlayer player, int size) {
		ServerLevel level = player.level();
		double x = player.getX(), y = player.getY(), z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, VOLUME * 0.8F, size >= MAJOR ? 1.0F : 1.3F);
		if (size >= MAJOR) {
			level.playSound(null, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, VOLUME * 0.7F, 1.0F);
			level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, VOLUME, 0.8F);
		}
		if (size >= GRAND) {
			level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, VOLUME, 1.2F);
			level.playSound(null, x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, VOLUME * 0.6F, 1.1F);
		}
	}

	/** A rising ring of sparks in the track's colour, end-rod motes, and for the grandest a totem's swirl. */
	public static void burst(ServerPlayer player, int color, int size) {
		ServerLevel level = player.level();
		DustParticleOptions dust = new DustParticleOptions(color & 0xFFFFFF, 1.4F);
		int sparks = SPARKS[size];
		for (int i = 0; i < sparks; i++) {
			double a = Math.PI * 2.0 * i / sparks;
			double up = 0.2 + (i % 4) * 0.45;
			level.sendParticles(dust, player.getX() + Math.cos(a) * RING, player.getY() + up, player.getZ() + Math.sin(a) * RING, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), sparks / 2, 0.5, 0.8, 0.5, 0.05);
		if (size >= GRAND) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.5);
		}
	}
}
