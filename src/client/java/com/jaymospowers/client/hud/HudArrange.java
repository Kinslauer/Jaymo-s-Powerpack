package com.jaymospowers.client.hud;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.jaymospowers.client.ui.FlatButton;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Arranging the HUD (Andy's pick from The Awakening's suit; ideas only): the client command {@code /hudmove} opens a
 * screen with a box for every zoned HUD piece ({@link HudZones}) that drew in the last {@link #SEEN_MS} ms, or that has been
 * moved before. Drag a box to move its piece, right-click it to put it back; "Reset all" puts everything back. The shifts
 * are kept in the client's settings ({@link #KEY}) and go into {@link HudZones#offsets}, which Kingdoms' and Kitchen's
 * pieces read too. A piece only shows here once it has been on screen, so open this with the thing to move in view.
 */
public final class HudArrange extends Screen {
	/** Pieces seen this lately get a box (ms). */
	static final long SEEN_MS = 5000L;
	/** Where the shifts are kept: {@code zone/id=dx,dy;...}. */
	static final String KEY = "hud.offsets";
	private static final String COMMAND = "hudmove";
	private static final int DIM = 0x70000000, BOX = 0x40FFFFFF, BOX_PICKED = 0x70FFC840, EDGE = 0xFFE0F4FF, MIN_BOX = 8;

	private static boolean pendingOpen;

	/** The pieces shown: {zone, id}. */
	private final List<String[]> pieces = new ArrayList<>();
	private String @Nullable [] picked;
	private int pressX, pressY, pressDx, pressDy;

	private HudArrange() {
		super(Component.translatable("screen.jaymospowers.hud_arrange"));
	}

	/** Called once at client start: the saved shifts go in, and the command that opens the screen. */
	public static void register() {
		for (String entry : ZFighterClientConfig.get(KEY, "").split(";")) {
			String[] parts = entry.split("[=,]");
			if (parts.length == 3) {
				try {
					HudZones.offsets().put(parts[0], new int[] {Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
				} catch (NumberFormatException e) {
					// a line someone edited by hand: skipped
				}
			}
		}
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal(COMMAND).executes(c -> {
			// the chat screen is still closing: open on the next tick
			pendingOpen = true;
			return 1;
		})));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (pendingOpen) {
				pendingOpen = false;
				if (minecraft.player != null) {
					minecraft.gui.setScreen(new HudArrange());
				}
			}
		});
	}

	private static void save() {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, int[]> entry : new TreeMap<>(HudZones.offsets()).entrySet()) {
			text.append(entry.getKey()).append('=').append(entry.getValue()[0]).append(',').append(entry.getValue()[1]).append(';');
		}
		ZFighterClientConfig.set(KEY, text.toString());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// no blur: the world stays in view, a little darker
		graphics.fill(0, 0, this.width, this.height, DIM);
	}

	@Override
	protected void init() {
		this.pieces.clear();
		this.pieces.addAll(HudZones.seen(SEEN_MS));
		for (String key : HudZones.offsets().keySet()) {
			String[] piece = key.split("/", 2);
			if (piece.length == 2 && this.pieces.stream().noneMatch(p -> p[0].equals(piece[0]) && p[1].equals(piece[1]))) {
				this.pieces.add(piece);
			}
		}
		int by = this.height - 24;
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.hud_arrange.reset"), b -> {
			HudZones.offsets().clear();
			save();
		}).bounds(this.width / 2 - 68, by, 64, 16).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("screen.jaymospowers.hud_arrange.done"), b -> onClose())
			.bounds(this.width / 2 + 4, by, 64, 16).build());
	}

	/** A piece's box, never smaller than can be grabbed. */
	private static int @Nullable [] box(String[] piece) {
		int[] box = HudZones.hold(piece[0], piece[1]);
		if (box != null) {
			box[2] = Math.max(MIN_BOX, box[2]);
			box[3] = Math.max(MIN_BOX, box[3]);
		}
		return box;
	}

	/** The piece under the mouse: the smallest box wins where boxes overlap. */
	private String @Nullable [] pieceAt(int mouseX, int mouseY) {
		String[] best = null;
		int bestArea = Integer.MAX_VALUE;
		for (String[] piece : this.pieces) {
			int[] box = box(piece);
			if (box != null && UiStyle.over(mouseX, mouseY, box[0], box[1], box[2], box[3]) && box[2] * box[3] < bestArea) {
				best = piece;
				bestArea = box[2] * box[3];
			}
		}
		return best;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		String[] hit = pieceAt((int)event.x(), (int)event.y());
		this.picked = null;
		if (hit == null) {
			return false;
		}
		String key = hit[0] + "/" + hit[1];
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			HudZones.offsets().remove(key);
			save();
			return true;
		}
		int[] by = HudZones.offsets().getOrDefault(key, new int[2]);
		this.picked = hit;
		this.pressX = (int)event.x();
		this.pressY = (int)event.y();
		this.pressDx = by[0];
		this.pressDy = by[1];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.picked == null) {
			return super.mouseDragged(event, dx, dy);
		}
		HudZones.offsets().put(this.picked[0] + "/" + this.picked[1],
			new int[] {this.pressDx + (int)event.x() - this.pressX, this.pressDy + (int)event.y() - this.pressY});
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.picked != null) {
			this.picked = null;
			save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		String[] hovered = this.picked != null ? this.picked : pieceAt(mouseX, mouseY);
		for (String[] piece : this.pieces) {
			int[] box = box(piece);
			if (box == null) {
				continue;
			}
			graphics.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], piece == hovered ? BOX_PICKED : BOX);
			graphics.outline(box[0], box[1], box[2], box[3], EDGE);
		}
		UiStyle.lineCentred(graphics, this.font, Component.translatable(this.pieces.isEmpty() ? "screen.jaymospowers.hud_arrange.empty"
			: "screen.jaymospowers.hud_arrange.hint"), this.width / 2, this.height - 38, this.width - 20, UiStyle.TEXT);
		if (hovered != null) {
			UiStyle.tip(graphics, this.font, Component.literal(hovered[1].replace('_', ' ')), mouseX, mouseY);
		}
	}
}
