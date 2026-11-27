package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.ui.FlatButton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Andy: "can you make a button in the story to squelch the narrator?" Whether the story's narrator is squelched: the voice
 * reading the life story and its loading screen ({@link LifeStoryScreen}, {@link StoryHoldScreen}). The button stops the
 * line being read at once, and the choice is kept for every later story (a one-line file in the config folder), until
 * the button is pressed again. Spirits' voices aren't the narrator, and aren't touched.
 */
final class NarratorSetting {
	/** The button's size: a square with the note on it. */
	static final int SIZE = 16;

	private static boolean loaded, squelched;

	private NarratorSetting() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("jaymoskingdoms-narrator.txt");
	}

	/** Whether the narrator keeps quiet. */
	static boolean squelched() {
		if (!loaded) {
			loaded = true;
			try {
				Path file = file();
				squelched = Files.exists(file) && Files.readString(file).trim().equals("squelched");
			} catch (IOException | RuntimeException e) {
				JaymosKingdoms.LOGGER.warn("Couldn't read the narrator setting", e);
			}
		}
		return squelched;
	}

	/** The narrator squelched, or let speak again, and kept so. */
	static void flip() {
		squelched = !squelched();
		try {
			Files.writeString(file(), squelched ? "squelched\n" : "speaking\n");
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't save the narrator setting", e);
		}
		if (squelched) {
			LifeStoryScreen.hush();
			StoryHoldScreen.hush(net.minecraft.client.Minecraft.getInstance());
		}
	}

	/** The squelch button at {@code x, y}: a note, struck through while the narrator is quiet. */
	static Button button(int x, int y) {
		Button b = FlatButton.builder(label(), button -> {
			flip();
			refresh(button);
		}).bounds(x, y, SIZE, SIZE).build();
		refresh(b);
		return b;
	}

	/** The button's note and tooltip, as the setting stands. */
	static void refresh(Button b) {
		b.setMessage(label());
		b.setTooltip(Ui.tipOf(Component.translatable(squelched() ? "gui.jaymoskingdoms.story.narrator.unsquelch" : "gui.jaymoskingdoms.story.narrator.squelch")));
	}

	private static Component label() {
		return squelched() ? Component.literal("♪").withStyle(ChatFormatting.STRIKETHROUGH).withColor(Ui.DIM) : Component.literal("♪");
	}
}
