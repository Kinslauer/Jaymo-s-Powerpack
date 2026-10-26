package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Careers;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.218) Players' job titles ({@link Careers#refreshTitles}): under the name over their head and after it in the tab
 * list, for those you've been introduced to ({@code IntroNameTagMixin}, {@code IntroTabListMixin}).
 */
public final class JobTitles {
	private JobTitles() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Careers.Titles.TYPE, (payload, context) -> Careers.shownTitles = Map.copyOf(payload.titles()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> Careers.shownTitles = Map.of());
	}

	/** The title of the player with {@code id}, as shown (greyed), or null. */
	public static @Nullable Component of(UUID id) {
		Component title = Careers.shownTitles.get(id);
		return title == null ? null : title.copy().withStyle(ChatFormatting.GRAY);
	}
}
