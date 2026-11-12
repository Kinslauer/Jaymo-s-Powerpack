package com.jaymoskingdoms.client;

import com.jaymoskingdoms.client.hud.HudStyle;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.NightGifts;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.66) The client half of {@link NightGifts}: the two keys ({@code O}: Blood Dash or Leap; {@code P}: Groundpound or
 * Howl), the kin-sense compass at the top of the screen, people a groundpound or howl showed glowing through walls,
 * and a vampire's bat wings, spread when it glides without an elytra.
 */
public final class NightGiftsClient {
	/** Outline of someone a groundpound or howl showed: a vampire's (blood) and a werewolf's (amber). */
	static final int REVEAL_BLOOD = 0xFFFF3B30, REVEAL_WOLF = 0xFFFFB020;
	/** The compass's colours and how long it holds without word from the server (ticks). */
	static final int TEXT_BLOOD = 0xFFD43A3A, TEXT_WOLF = 0xFFE8A33A, TEXT_DIM = 0xFF9A9A9A;
	/** (0.7.88) What's felt only faintly (a vampire's unturned Nightborn, a cured Nightborn's nearest vampire). */
	static final int TEXT_FAINT = 0xFF8C6670;
	static final int SENSE_STALE = 60;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private static KeyMapping dash, pound;
	/** Shown people: entity id → game time the glow ends. */
	private static final Map<Integer, Long> REVEALED = new HashMap<>();
	private static List<Integer> bearings = List.of(), distances = List.of(), faintBearings = List.of(), faintDistances = List.of();
	private static int kinCount;
	private static long senseAt = Long.MIN_VALUE;

	private NightGiftsClient() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("night_gifts"));
		dash = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.night_dash", InputConstants.KEY_O, category));
		pound = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.night_pound", InputConstants.KEY_P, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (dash.consumeClick()) {
				// sneak and O: a full vampire's shapes of the night (NightForms), else the Blood Dash or Leap
				send(minecraft, minecraft.player != null && minecraft.player.isShiftKeyDown() && com.jaymoskingdoms.nightforms.NightForms.vampire(minecraft.player) ? NightGifts.SHAPES : 0);
			}
			while (pound.consumeClick()) {
				send(minecraft, 1);
			}
			if (minecraft.level != null) {
				long now = minecraft.level.getGameTime();
				REVEALED.values().removeIf(end -> end < now);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(NightGifts.Sense.TYPE, (payload, context) -> {
			bearings = payload.bearings();
			distances = payload.distances();
			kinCount = payload.count();
			faintBearings = payload.faintBearings();
			faintDistances = payload.faintDistances();
			senseAt = context.client().level == null ? 0L : context.client().level.getGameTime();
		});
		ClientPlayNetworking.registerGlobalReceiver(NightGifts.Reveal.TYPE, (payload, context) -> {
			if (context.client().level == null) {
				return;
			}
			long end = context.client().level.getGameTime() + payload.ticks();
			for (int id : payload.ids()) {
				REVEALED.put(id, end);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			REVEALED.clear();
			bearings = List.of();
			faintBearings = List.of();
			kinCount = 0;
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("kin_sense"), NightGiftsClient::compass);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new BatWings(avatar));
			}
		});
	}

	private static void send(Minecraft minecraft, int which) {
		if (minecraft.player != null && minecraft.gui.screen() == null && NightGifts.gift(minecraft.player) != null
			&& ClientPlayNetworking.canSend(NightGifts.Move.TYPE)) {
			ClientPlayNetworking.send(new NightGifts.Move(which));
		}
	}

	/** On a player's render state: their bat wings are spread. */
	public static final RenderStateDataKey<Boolean> WINGS = BatWings.SPREAD;

	/** Called from the render-state mixin. */
	public static Boolean wings(LivingEntity entity) {
		return BatWings.spread(entity);
	}

	/** Whether a groundpound or howl is showing {@code entity} to this player now. */
	public static boolean revealed(Entity entity) {
		return !REVEALED.isEmpty() && REVEALED.containsKey(entity.getId());
	}

	/** The outline colour for someone shown. */
	public static int revealColor() {
		LocalPlayer player = Minecraft.getInstance().player;
		NightGifts.Gift g = player == null ? null : NightGifts.gift(player);
		return g != null && g.wolf() ? REVEAL_WOLF : REVEAL_BLOOD;
	}

	/** The kin-sense compass: top left, an arrow and distance to each of the nearest kin. */
	private static void compass(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		NightGifts.Gift g = NightGifts.gift(player);
		if (minecraft.level.getGameTime() - senseAt > SENSE_STALE) {
			return;
		}
		Font font = minecraft.font;
		if (minecraft.debugEntries.isOverlayVisible()) {
			// (HUD rework) hidden while F3 is open, like the party list
			return;
		}
		java.util.List<Component> lines = new java.util.ArrayList<>();
		java.util.List<Integer> colors = new java.util.ArrayList<>();
		if (g == null) {
			// (0.7.88) a cured Nightborn's trace: only the faint line
			if (faintBearings.isEmpty()) {
				return;
			}
			lines.add(Component.translatable("hud.jaymoskingdoms.night.faint", line(player, faintBearings, faintDistances, "~")));
			colors.add(TEXT_FAINT);
		} else {
			int color = g.wolf() ? TEXT_WOLF : TEXT_BLOOD;
			String line = line(player, bearings, distances, "");
			Component label = Component.translatable(g.wolf() ? "hud.jaymoskingdoms.night.pack" : "hud.jaymoskingdoms.night.kin");
			lines.add(kinCount == 0 ? Component.translatable("hud.jaymoskingdoms.night.none", label)
				: Component.translatable("hud.jaymoskingdoms.night.sense", label, line,
					kinCount > bearings.size() ? Component.translatable("hud.jaymoskingdoms.night.more", kinCount - bearings.size()) : Component.empty()));
			colors.add(kinCount == 0 ? TEXT_DIM : color);
			if (!faintBearings.isEmpty()) {
				lines.add(Component.translatable("hud.jaymoskingdoms.night.faint", line(player, faintBearings, faintDistances, "~")));
				colors.add(TEXT_FAINT);
			}
			if (g.blood() && g.wings() < NightGifts.WINGS_MAX) {
				// (the wings' strength, under the compass, while they're worn down)
				lines.add(Component.translatable("hud.jaymoskingdoms.night.wings", Math.round(100.0F * g.wings() / NightGifts.WINGS_MAX)));
				colors.add(TEXT_DIM);
			}
		}
		// (HUD rework) top-left rail, row 20, on a soft plate like the party list's; lines 11 px apart
		int textW = 0;
		for (Component c : lines) {
			textW = Math.max(textW, font.width(c));
		}
		int boxW = textW + 4, boxH = lines.size() * 11 - 2 + 4;
		int[] at = HudZones.place(HudZones.RAIL, "jaymoskingdoms:kin_sense", 20, boxW, boxH);
		if (at == null) {
			return;
		}
		graphics.fill(at[0], at[1], at[0] + boxW, at[1] + boxH, HudStyle.PLATE_SOFT);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), at[0] + 2, at[1] + 2 + i * 11, colors.get(i), true);
		}
	}

	/** An arrow and distance for each one sensed ({@code about} before rough distances). */
	private static String line(LocalPlayer player, List<Integer> bearings, List<Integer> distances, String about) {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < bearings.size() && i < distances.size(); i++) {
			float rel = Mth.wrapDegrees(bearings.get(i) - player.getYRot());
			String arrow = ARROWS[Math.floorMod(Math.round(rel / 45.0F), 8)];
			if (!line.isEmpty()) {
				line.append("   ");
			}
			line.append(arrow).append(' ').append(about).append(distances.get(i)).append(" m");
		}
		return line.toString();
	}
}
