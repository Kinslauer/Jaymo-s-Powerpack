package com.jaymoskingdoms.client;

import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Parties;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * (0.7.264, Andy's NOEA picks) The client half of {@link Parties}: a compact list of the other members of your party at
 * the top left, under the kin-sense compass ({@code NightGiftsClient}), each with their name, a health bar with a thin
 * hunger bar under it, and how far away they are (or which dimension they are in). Sent about once a second, so it
 * shows them at any distance.
 */
public final class PartyClient {
	/** Where the list starts (pixels from the top, under the compass's three lines) and how wide a row is. */
	static final int TOP = 48, LEFT = 4, WIDTH = 96;
	/** A row's height, and the bar heights (health, hunger). */
	static final int ROW = 18, BAR = 3, THIN = 1;
	static final int LEADER = 0xFFFFC21A, NAME = 0xFFFFFFFF, FAR = 0xFFB0B0B0, HEALTH = 0xFFD84040, LOW = 0xFFFF9020, HUNGER = 0xFFC8872A;
	static final int BACK = 0x60000000, EMPTY = 0xA0202020;

	/** The party as last sent (empty without one). */
	private static List<Parties.Entry> party = List.of();

	private PartyClient() {
	}

	/** (0.7.649) The party as last sent, you included (empty without one), for the character menu's Party tab ({@link PartyScreen}). */
	static List<Parties.Entry> party() {
		return party;
	}

	/** How far {@code e} is, or the dimension they are in (the HUD's words). */
	static Component whereIs(LocalPlayer player, Parties.Entry e) {
		return where(player, e, player.level().dimension().identifier().toString());
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Parties.Sync.TYPE, (payload, context) -> party = List.copyOf(payload.members()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> party = List.of());
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, JaymosKingdoms.id("party"), PartyClient::hud);
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || party.size() < 2 || minecraft.debugEntries.isOverlayVisible()) {
			return;
		}
		Font font = minecraft.font;
		String here = player.level().dimension().identifier().toString();
		int others = 0;
		for (Parties.Entry e : party) {
			if (!e.id().equals(player.getUUID())) {
				others++;
			}
		}
		if (others == 0) {
			return;
		}
		// (HUD rework) top-left rail, row 30 (under the kin-sense compass); the plates run 2 px out of the text box on each side
		int[] at = HudZones.place(HudZones.RAIL, "jaymoskingdoms:party", 30, WIDTH + 4, others * (ROW + 2) - 2);
		if (at == null) {
			return;
		}
		int left = at[0] + 2;
		int y = at[1] + 2;
		for (Parties.Entry e : party) {
			if (e.id().equals(player.getUUID())) {
				continue;
			}
			graphics.fill(left - 2, y - 2, left + WIDTH + 2, y + ROW - 2, BACK);
			graphics.text(font, e.name(), left, y, e.leader() ? LEADER : NAME, true);
			Component where = where(player, e, here);
			graphics.text(font, where, left + WIDTH - font.width(where), y, FAR, true);
			int bar = y + font.lineHeight + 1;
			float share = e.maxHealth() <= 0.0F ? 0.0F : Mth.clamp(e.health() / e.maxHealth(), 0.0F, 1.0F);
			graphics.fill(left, bar, left + WIDTH, bar + BAR, EMPTY);
			graphics.fill(left, bar, left + Math.round(WIDTH * share), bar + BAR, share < 0.3F ? LOW : HEALTH);
			graphics.fill(left, bar + BAR, left + WIDTH, bar + BAR + THIN, EMPTY);
			graphics.fill(left, bar + BAR, left + Math.round(WIDTH * Mth.clamp(e.food() / 20.0F, 0.0F, 1.0F)), bar + BAR + THIN, HUNGER);
			y += ROW + 2;
		}
	}

	/** How far {@code e} is (same dimension), or the dimension they are in. */
	private static Component where(LocalPlayer player, Parties.Entry e, String here) {
		if (e.dimension().equals(here)) {
			double dx = e.x() + 0.5 - player.getX(), dy = e.y() - player.getY(), dz = e.z() + 0.5 - player.getZ();
			return Component.translatable("hud.jaymoskingdoms.party.distance", Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
		}
		String path = e.dimension().substring(e.dimension().indexOf(':') + 1);
		return switch (path) {
			case "overworld", "the_nether", "the_end" -> Component.translatable("hud.jaymoskingdoms.party.dim." + path);
			default -> Component.literal(path.replace('_', ' '));
		};
	}
}
