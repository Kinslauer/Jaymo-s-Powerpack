package com.jaymospowers.client.wounds;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Mortality;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Andy: "When everything goes dark as you go down your audio should quiet down and blank out too". The game's sound
 * follows the screen: quieter while down and greyed, quieter still once passed out, and gone while the screen is black
 * (out cold, a death turned away, a fainting's letterbox shut, the death screen), coming back as the view does.
 * <p>
 * Every sound is multiplied by {@link #volume} as it starts and as its volume is worked out again
 * ({@code BlackoutHushSoundMixin}); {@link #tick} has the playing ones worked out again whenever it changes. Left alone:
 * menu clicks (the death screen's buttons), the master category, and the black's own cues ({@link #CUES}): the heartbeat
 * while down or swooning, the breath as they wake, and a turning's groan.
 * <p>
 * A death that ends the character ({@link Mortality#FINAL}) is the exception (Andy, 2026-11-19: "only hearing nothing and
 * the long wait for the respawn screen", "the cut to black is only for a final death"): the moment you die it's silent at once, not eased into, and the black's own cues are hushed with the rest; only the menu clicks
 * (the death screen's buttons) and the master category are left.
 * <p>
 * Works beside Sound Physics Remastered: it only scales each source's gain, so its reverb and muffling fade with it.
 */
public final class BlackoutHush {
	/** How quiet the game gets (0-1 of the way to silent) while down and greyed, and once passed out. Black is all the way. */
	static final float DOWN_HUSH = 0.3F, OUT_HUSH = 0.6F;
	/** The most the hush moves in a tick going quiet, and coming back (the view sets the pace; these only smooth jumps). */
	static final float QUIET_STEP = 1.0F / 10.0F, RETURN_STEP = 1.0F / 20.0F;
	/** Silent is this, not 0: the engine refuses to start a sound at 0, and a loop refused there wouldn't come back. */
	static final float SILENT = 0.001F;
	/** The black's own sounds (on the players' category), heard over it at their own volume. */
	static final Set<Identifier> CUES = Set.of(Identifier.withDefaultNamespace("entity.warden.heartbeat"),
		Identifier.withDefaultNamespace("entity.player.breath"), Identifier.withDefaultNamespace("entity.zombie_villager.converted"),
		Identifier.withDefaultNamespace("entity.zombie_villager.cure"));

	/** How far to silent the game is now (0-1), and what the playing sounds were last worked out for. */
	private static float hush, refreshed;
	/** Whether you have died for good just now (the death screen up, or dying): silent at once, cues and all. */
	private static boolean dead;

	private BlackoutHush() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BlackoutHush::tick);
	}

	/** (BlackoutHushSoundMixin) What {@code sound}'s volume is multiplied by now. */
	public static float volume(SoundInstance sound) {
		if (hush <= 0.0F) {
			return 1.0F;
		}
		SoundSource source = sound.getSource();
		if (source == SoundSource.UI || source == SoundSource.MASTER
			|| !dead && source == SoundSource.PLAYERS && CUES.contains(sound.getIdentifier())) {
			return 1.0F;
		}
		return Math.max(SILENT, 1.0F - hush);
	}

	private static void tick(Minecraft minecraft) {
		boolean wasDead = dead;
		dead = minecraft.player != null && minecraft.level != null && Mortality.isFinal(minecraft.player)
			&& (minecraft.gui.screen() instanceof DeathScreen || minecraft.player.isDeadOrDying());
		float target = target(minecraft);
		hush = dead ? 1.0F : target > hush ? Math.min(target, hush + QUIET_STEP) : Math.max(target, hush - RETURN_STEP);
		if (hush == refreshed && dead == wasDead || minecraft.getSoundManager() == null) {
			return;
		}
		refreshed = hush;
		for (SoundSource source : SoundSource.values()) {
			if (source != SoundSource.MASTER && source != SoundSource.UI) {
				minecraft.getSoundManager().refreshCategoryVolume(source);
			}
		}
	}

	/** How dark the screen is, as a hush (0-1). */
	private static float target(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return 0.0F;
		}
		if (minecraft.gui.screen() instanceof DeathScreen) {
			return 1.0F;
		}
		float dark = DownedHud.darkness();
		if (Downed.isDowned(player) && !OutOfBodyClient.drifting()) {
			dark = Math.max(dark, Downed.passedOut(player) ? OUT_HUSH : DOWN_HUSH);
		}
		if (player.isAlive()) {
			dark = Math.max(dark, FaintHud.shut(player, 0.0F));
		}
		return Math.min(1.0F, dark);
	}
}
