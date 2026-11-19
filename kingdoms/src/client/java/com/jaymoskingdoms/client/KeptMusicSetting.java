package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;

/**
 * Andy: "If players have music off, turn it back off after they spawn from the creator." The Music slider is read as the
 * world is joined; if it was off then and is on once the newcomer is set down after the life story and MCA's creator
 * (nothing of theirs on screen for {@link #SETTLED_TICKS}), it is put back to off and saved. A slider that was on is left
 * as it is, and so is one that never went through the creator.
 */
final class KeptMusicSetting {
	/** How long after the last story or creator screen the player counts as spawned (ticks). */
	static final int SETTLED_TICKS = 60;

	/** Whether the Music slider was off as this world was joined, whether the story or creator was seen, and the ticks since. */
	private static boolean offAtJoin, created;
	private static int settled;

	private KeptMusicSetting() {
	}

	static void register() {
		ClientPlayConnectionEvents.INIT.register((handler, minecraft) -> {
			offAtJoin = minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F;
			created = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(KeptMusicSetting::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		Screen screen = minecraft.gui.screen();
		if (StoryCurtain.up() || StoryCurtain.isCreator(screen) || screen instanceof LifeStoryScreen || screen instanceof StoryHoldScreen) {
			created = true;
			settled = 0;
			return;
		}
		if (!created || ++settled < SETTLED_TICKS) {
			return;
		}
		created = false;
		if (offAtJoin && minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0.0F) {
			minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
			minecraft.options.save();
			JaymosKingdoms.LOGGER.info("Music was off before character creation: switched it back off");
		}
	}
}
