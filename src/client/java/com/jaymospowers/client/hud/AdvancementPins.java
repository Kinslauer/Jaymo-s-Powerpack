package com.jaymospowers.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.jspecify.annotations.Nullable;

/**
 * Pinned advancements (Andy's pick from Advancements Tracker, MIT, ideas only): hover an advancement on the advancements
 * screen (vanilla's or Better Advancements') and shift-click it to pin it, again to unpin. Up to {@link #MOST} show in
 * the top-left rail under Xaero's minimap ({@link HudZones#RAIL}, row {@link #ROW}), each with its icon, its title, the
 * criteria done out of all, and the next few still to do. A finished one drops off by itself. Pins are kept per world or
 * server in {@code config/jaymospowers/advancement_pins.properties}.
 * <p>
 * Which advancement is under the mouse comes from the two screens' hover tooltips
 * ({@code client/mixin/pins}): each frame the hovered one draws its tooltip, it says so here.
 */
public final class AdvancementPins {
	// ===== the knobs ================================================================================================
	/** The most pins at once. */
	public static final int MOST = 3;
	/** The rail row (under the biome title, over the move hints). */
	public static final int ROW = 30;
	/** The most criteria still to do listed under each pin. */
	static final int LEFT_SHOWN = 3;
	/** A hover older than this (ns) no longer counts: the mouse has left. */
	static final long HOVER_FRESH = 150_000_000L;
	/** Its look: line height, padding, icon size, gap between pins, the widest line (gui pixels). */
	static final int LINE = 10, PAD = 3, ICON = 16, PIN_GAP = 4, MAX_WIDTH = 170;
	// ================================================================================================================

	private static final List<Identifier> pins = new ArrayList<>();
	private static @Nullable String world;
	private static @Nullable Identifier hovered;
	private static long hoveredAt;

	private AdvancementPins() {
	}

	/** From {@code JaymosPowersClient}. */
	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (!advancementScreen(screen)) {
				return;
			}
			// a shift-click on the advancement: no key of its own to clash with the many already bound
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
				event.button() != InputConstants.MOUSE_BUTTON_LEFT || !event.hasShiftDown() || !toggleHovered(Minecraft.getInstance()));
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> hint(graphics, s));
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("advancement_pins"), AdvancementPins::draw);
	}

	/** Vanilla's advancements screen, or Better Advancements' (looked up by name: it's optional). */
	private static boolean advancementScreen(Screen screen) {
		return screen instanceof AdvancementsScreen || screen.getClass().getName().startsWith("betteradvancements.");
	}

	/** From the hover mixins: this advancement's tooltip is drawing now. */
	public static void hovering(AdvancementHolder holder) {
		hovered = holder.id();
		hoveredAt = System.nanoTime();
	}

	/** Pins or unpins the hovered advancement; false when none is hovered (the click goes on to the screen). */
	private static boolean toggleHovered(Minecraft minecraft) {
		if (hovered == null || System.nanoTime() - hoveredAt > HOVER_FRESH || minecraft.player == null) {
			return false;
		}
		load(minecraft);
		if (pins.remove(hovered)) {
			minecraft.player.sendOverlayMessage(Component.translatable("message.jaymospowers.pins.unpinned"));
		} else if (pins.size() >= MOST) {
			minecraft.player.sendOverlayMessage(Component.translatable("message.jaymospowers.pins.full", MOST));
			return true;
		} else {
			pins.add(hovered);
			minecraft.player.sendOverlayMessage(Component.translatable("message.jaymospowers.pins.pinned"));
		}
		save();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		return true;
	}

	/** A line at the screen's foot saying how to pin, so the pins can be found. */
	private static void hint(GuiGraphicsExtractor graphics, Screen screen) {
		Minecraft minecraft = Minecraft.getInstance();
		Component line = Component.translatable("message.jaymospowers.pins.hint", MOST);
		graphics.centeredText(minecraft.font, line, screen.width / 2, screen.height - 12, HudStyle.DIM);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null) {
			return;
		}
		load(minecraft);
		if (pins.isEmpty()) {
			return;
		}
		ClientAdvancements advancements = minecraft.getConnection().getAdvancements();
		List<Card> cards = new ArrayList<>();
		boolean finished = false;
		for (Identifier id : pins) {
			AdvancementHolder holder = advancements.get(id);
			if (holder == null || holder.value().display().isEmpty()) {
				continue;
			}
			AdvancementProgress progress = advancements.progress().get(holder);
			if (progress != null && progress.isDone()) {
				finished = true;
				continue;
			}
			cards.add(card(minecraft, holder.value().display().get(), progress));
		}
		if (finished) {
			// done: unpinned by itself
			pins.removeIf(id -> {
				AdvancementHolder holder = advancements.get(id);
				AdvancementProgress progress = holder == null ? null : advancements.progress().get(holder);
				return progress != null && progress.isDone();
			});
			save();
		}
		if (cards.isEmpty()) {
			return;
		}
		int width = 0, height = 0;
		for (Card card : cards) {
			width = Math.max(width, card.width);
			height += card.lines.size() * LINE;
		}
		int w = Math.min(width, MAX_WIDTH) + ICON + PAD * 3, h = height + PIN_GAP * (cards.size() - 1) + PAD * 2 - 1;
		int[] at = HudZones.place(HudZones.RAIL, "jaymospowers:advancement_pins", ROW, w, h);
		if (at == null) {
			return;
		}
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, HudStyle.PLATE_SOFT);
		int y = at[1] + PAD;
		for (Card card : cards) {
			graphics.item(card.icon, at[0] + PAD, y - 1);
			int x = at[0] + PAD * 2 + ICON;
			for (Component line : card.lines) {
				graphics.text(minecraft.font, Language.getInstance().getVisualOrder(minecraft.font.substrByWidth(line, MAX_WIDTH)), x, y, HudStyle.TEXT, true);
				y += LINE;
			}
			y += PIN_GAP;
		}
	}

	private record Card(net.minecraft.world.item.ItemStack icon, List<Component> lines, int width) {
	}

	private static Card card(Minecraft minecraft, DisplayInfo display, @Nullable AdvancementProgress progress) {
		List<Component> lines = new ArrayList<>();
		Component title = display.title().copy().withColor(HudStyle.GOLD & 0xFFFFFF);
		Component count = progress == null ? null : progress.getProgressText();
		lines.add(count == null ? title : title.copy().append(Component.literal("  ").append(count.copy().withColor(HudStyle.DIM & 0xFFFFFF))));
		if (progress != null) {
			int listed = 0, left = 0;
			for (String criterion : progress.getRemainingCriteria()) {
				if (listed < LEFT_SHOWN) {
					lines.add(Component.literal("- " + readable(criterion)).withColor(HudStyle.DIM & 0xFFFFFF));
					listed++;
				}
				left++;
			}
			if (left > listed) {
				lines.add(Component.translatable("message.jaymospowers.pins.more", left - listed).withColor(HudStyle.HINT & 0xFFFFFF));
			}
		}
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, minecraft.font.width(line));
		}
		return new Card(display.icon().create(), lines, width);
	}

	/** A criterion's name as words: "minecraft:snowy_slopes" reads "snowy slopes". */
	static String readable(String criterion) {
		String name = criterion.substring(criterion.indexOf(':') + 1);
		name = name.substring(name.lastIndexOf('/') + 1).replace('_', ' ');
		return name.isEmpty() ? criterion : name;
	}

	// ----- kept per world or server -----

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("jaymospowers").resolve("advancement_pins.properties");
	}

	private static String worldKey(Minecraft minecraft) {
		if (minecraft.getSingleplayerServer() != null) {
			return "world." + minecraft.getSingleplayerServer().getWorldData().getLevelName();
		}
		ServerData server = minecraft.getCurrentServer();
		return "server." + (server == null ? "unknown" : server.ip);
	}

	private static void load(Minecraft minecraft) {
		String now = worldKey(minecraft);
		if (now.equals(world)) {
			return;
		}
		world = now;
		pins.clear();
		Properties all = read();
		for (String id : all.getProperty(now, "").split(",")) {
			Identifier parsed = id.isBlank() ? null : Identifier.tryParse(id.trim());
			if (parsed != null && pins.size() < MOST) {
				pins.add(parsed);
			}
		}
	}

	private static Properties read() {
		Properties all = new Properties();
		if (Files.exists(file())) {
			try (Reader in = Files.newBufferedReader(file())) {
				all.load(in);
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't read the advancement pins", e);
			}
		}
		return all;
	}

	private static void save() {
		if (world == null) {
			return;
		}
		Properties all = read();
		List<String> ids = new ArrayList<>();
		for (Identifier id : pins) {
			ids.add(id.toString());
		}
		if (ids.isEmpty()) {
			all.remove(world);
		} else {
			all.setProperty(world, String.join(",", ids));
		}
		try {
			Files.createDirectories(file().getParent());
			try (Writer out = Files.newBufferedWriter(file())) {
				all.store(out, "Pinned advancements, per world or server");
			}
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't save the advancement pins", e);
		}
	}
}
