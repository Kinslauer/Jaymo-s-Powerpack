package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.LifeStory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * (0.7.238) Andy: "it should stay black until the character creator. i just dont want the player seeing anything in
 * world while they are spawning." From the life story (or a {@link StoryHoldScreen}) until MCA's creator opens, the
 * world is drawn over in black: behind those screens and in the gaps between them, while the server moves the newcomer.
 */
final class StoryCurtain {
	/** MCA's creator, by name (MCA is optional): the curtain lifts when it opens. */
	private static final String MCA_CREATOR = "net.conczin.mca.client.gui.DestinyScreen";
	/** Once nothing holds the newcomer any more, how long the curtain waits for MCA's creator before lifting anyway (ms). */
	static final long LIFT_AFTER_MS = 2500L;
	static final int BLACK = 0xFF000000;

	private static boolean up;
	private static long lastHeld;

	private StoryCurtain() {
	}

	static void register() {
		HudElementRegistry.addLast(JaymosKingdoms.id("story_curtain"), StoryCurtain::draw);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.player == null) {
				return;
			}
			long now = System.currentTimeMillis();
			Screen screen = minecraft.gui.screen();
			if (LifeStoryScreen.owed() || StoryHoldScreen.holding() || screen instanceof LifeStoryScreen
				|| screen instanceof StoryHoldScreen
				// (0.7.304) told, still in stasis till they're set down at their start: black, but for MCA's creator
				|| minecraft.player.hasAttached(LifeStory.ROAD) && !isCreator(screen)
				// (0.7.356) the road still to tell after MCA's creator: black till its screen is up
				|| LifeStory.roadOwed(LifeStory.of(minecraft.player)) && !isCreator(screen)) {
				up = true;
				lastHeld = now;
			} else if (up && (isCreator(screen) || now - lastHeld > LIFT_AFTER_MS)) {
				up = false;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> up = false);
	}

	/** Raised by the story or a hold arriving, before the first tick sees them. */
	static void raise() {
		up = true;
		lastHeld = System.currentTimeMillis();
	}

	static boolean up() {
		return up;
	}

	private static boolean isCreator(Screen screen) {
		return screen != null && screen.getClass().getName().equals(MCA_CREATOR);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (up) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), BLACK);
		}
	}

	/** A story screen's background: black, never the world. */
	static void background(GuiGraphicsExtractor graphics, Screen screen) {
		graphics.fill(0, 0, screen.width, screen.height, BLACK);
	}
}
