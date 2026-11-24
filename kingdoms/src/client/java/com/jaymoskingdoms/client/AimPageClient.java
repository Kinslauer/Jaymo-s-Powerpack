package com.jaymoskingdoms.client;

import com.jaymoskingdoms.LifeAims;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/**
 * The life aim's page ({@link LifeAims.Page}), opened as the player lands, in place of the one-page book that used to go
 * in their pack: on a torn scrap ({@link ScrapScreen}) for a rough start, on Minecraft's own book page for a town one.
 * Either way it's headed by who they are, then the aim. It waits until no screen has been open for {@link #CLEAR_TICKS},
 * so the landing's loading screen (StoryHoldScreen) and the creator are gone and nothing opens straight over it.
 */
public final class AimPageClient {
	/** Ticks with no screen open before the page shows. */
	static final int CLEAR_TICKS = 20;

	private static LifeAims.Page pending;
	private static int clear;

	private AimPageClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LifeAims.Page.TYPE, (payload, context) -> {
			pending = payload;
			clear = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(AimPageClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (pending == null) {
			return;
		}
		if (minecraft.player == null) {
			pending = null;
			return;
		}
		if (minecraft.gui.screen() != null) {
			clear = 0;
			return;
		}
		if (++clear < CLEAR_TICKS) {
			return;
		}
		Component heading = Component.translatable("aim.jaymoskingdoms.sheet.heading").withStyle(ChatFormatting.UNDERLINE);
		Component who = who(pending);
		Component aim = Component.translatable(pending.key());
		minecraft.gui.setScreen(pending.scrap() ? new ScrapScreen(heading, who, aim)
			: new BookViewScreen(new BookViewScreen.BookAccess(List.of(Component.empty().append(heading.copy().withStyle(ChatFormatting.BOLD))
				.append("\n\n").append(who).append("\n\n").append(aim)))));
		pending = null;
	}

	/** "You are <name>, a <class> of <heritage> heritage.", one lang key a shape, for whatever of class and heritage they have. */
	static Component who(LifeAims.Page page) {
		Component name = Component.literal(page.name()).withStyle(ChatFormatting.ITALIC);
		String k = "aim.jaymoskingdoms.sheet.";
		Component cls = page.cls().isEmpty() ? null : Component.translatable(k + "class." + page.cls());
		Component heritage = page.heritage().isEmpty() ? null : Component.translatable("heritage.jaymoskingdoms." + page.heritage());
		if (cls != null && heritage != null) {
			return Component.translatable(k + "full", name, cls, heritage);
		}
		if (heritage != null) {
			return Component.translatable(k + "classless", name, heritage);
		}
		return cls != null ? Component.translatable(k + "no_heritage", name, cls) : Component.translatable(k + "bare", name);
	}
}
