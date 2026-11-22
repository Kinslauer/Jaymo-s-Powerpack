package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-21): the talk answers as a wheel on the right, "fallout 4 styled ... with a response based on your origin
 * choices". Each spoke ({@code McaDialogueBoxMixin}) shows the answer's short label; pointing at it shows the line your
 * character says, worded by your blood and then your class, and that same line is what you say on camera when you pick
 * it ({@link DialogueCinematic#mine}).
 */
public final class TalkWheel {
	private static final String PREFIX = "talkwheel.jaymoskingdoms.";

	private TalkWheel() {
	}

	/**
	 * What you say for {@code answer} (an MCA answer's name): your heritage's line, else your class's, else the plain one,
	 * else null (no line written for it).
	 */
	public static @Nullable Component line(String answer) {
		LocalPlayer self = Minecraft.getInstance().player;
		String base = PREFIX + answer;
		Language language = Language.getInstance();
		if (self != null) {
			Heritage.Kind heritage = Heritage.of(self);
			if (heritage != null && language.has(base + "." + heritage.key())) {
				return Component.translatable(base + "." + heritage.key());
			}
			Classes.PlayerClass cls = Classes.classOf(self);
			if (cls != null && language.has(base + "." + cls.key())) {
				return Component.translatable(base + "." + cls.key());
			}
		}
		return language.has(base) ? Component.translatable(base) : null;
	}
}
