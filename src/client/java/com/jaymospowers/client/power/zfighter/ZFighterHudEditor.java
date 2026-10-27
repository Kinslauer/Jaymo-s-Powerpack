package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.power.zfighter.ZFighterHudLayout.Piece;
import com.jaymospowers.client.power.zfighter.ZFighterHudLayout.Place;
import com.jaymospowers.client.power.zfighter.ZFighterHudLayout.Rect;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.58, Andy's NOEA picks) The HUD editor: drag the Z Fighter HUD's pieces to new places and size them. Opened by the
 * Training screen's "HUD" button and by the client command {@code /zfighterhud}. Every piece is shown as a box where it
 * is now (all of them, even those not on screen at the moment), named, with its size; drag one to move it, turn the wheel
 * over it (or press + and -) to scale it, right-click it (or press Delete) to put it back, Tab to pick the next one and
 * the arrow keys to nudge the picked one a pixel (Shift: five). The layout is saved when a piece is let go
 * ({@link ZFighterHudLayout}); "Reset all" puts every piece back, "Done" (or Escape) closes the editor.
 */
public class ZFighterHudEditor extends Screen {
	/** What a nudge by the arrow keys moves a piece by (pixels), and with Shift held. */
	private static final int NUDGE = 1, NUDGE_FAR = 5;
	private static final int ACCENT = 0xFF000000 | com.jaymospowers.power.zfighter.ZFighterPower.COLOR;
	private static final int DIM = 0x70000000;
	/** A box's fill, picked or not, and its outline. */
	private static final int BOX = 0x40FFFFFF, BOX_PICKED = 0x70FFC840, BOX_HOVER = 0x55FFFFFF;
	private static final String COMMAND = "zfighterhud";

	private static boolean pendingOpen;

	private final @Nullable Screen previous;
	private @Nullable Piece picked;
	/** While dragging: where the mouse went down, and the picked piece's place then. */
	private boolean dragging;
	private int pressX, pressY;
	private Place pressPlace = Place.DEFAULT;

	public ZFighterHudEditor(@Nullable Screen previous) {
		super(Component.translatable("screen.jaymospowers.zfighter_hud"));
		this.previous = previous;
	}

	/** Called once from {@link ZFighterSensesClient#init}: the client command that opens the editor. */
	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal(COMMAND).executes(c -> {
			// the chat screen is still closing: open the editor on the next tick
			pendingOpen = true;
			return 1;
		})));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (pendingOpen) {
				pendingOpen = false;
				if (minecraft.player != null) {
					minecraft.gui.setScreen(new ZFighterHudEditor(null));
				}
			}
		});
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
		int by = this.height - 24;
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_hud.reset"), b -> {
			ZFighterHudLayout.resetAll();
			this.picked = null;
		}).bounds(this.width / 2 - 68, by, 64, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_hud.done"), b -> onClose())
			.bounds(this.width / 2 + 4, by, 64, 16).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.previous);
	}

	private Rect placed(Piece piece) {
		LocalPlayer player = this.minecraft.player;
		return ZFighterHudLayout.placed(ZFighterHudLayout.rect(piece, player, this.width, this.height), ZFighterHudLayout.place(piece));
	}

	/** The piece under the mouse: the smallest box wins where boxes overlap. */
	private @Nullable Piece pieceAt(int mouseX, int mouseY) {
		List<Piece> hits = new ArrayList<>();
		for (Piece piece : Piece.values()) {
			Rect r = placed(piece);
			if (UiStyle.over(mouseX, mouseY, r.x(), r.y(), r.w(), r.h())) {
				hits.add(piece);
			}
		}
		hits.sort(Comparator.comparingInt(piece -> placed(piece).w() * placed(piece).h()));
		return hits.isEmpty() ? null : hits.get(0);
	}

	// ---- input --------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Piece hit = pieceAt((int)event.x(), (int)event.y());
		if (hit == null) {
			this.picked = null;
			return false;
		}
		this.picked = hit;
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			ZFighterHudLayout.put(hit, Place.DEFAULT);
			return true;
		}
		this.dragging = true;
		this.pressX = (int)event.x();
		this.pressY = (int)event.y();
		this.pressPlace = ZFighterHudLayout.place(hit);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging && this.picked != null) {
			Piece piece = this.picked;
			Place now = ZFighterHudLayout.place(piece);
			Place target = new Place(this.pressPlace.dx() + (int)event.x() - this.pressX, this.pressPlace.dy() + (int)event.y() - this.pressY, now.scale());
			ZFighterHudLayout.move(piece, keepOnScreen(piece, target));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging && this.picked != null) {
			ZFighterHudLayout.save(this.picked);
		}
		this.dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Piece piece = pieceAt((int)mouseX, (int)mouseY);
		if (piece == null) {
			piece = this.picked;
		}
		if (piece != null && scrollY != 0.0) {
			this.picked = piece;
			scale(piece, scrollY > 0.0 ? ZFighterHudLayout.SCALE_STEP : -ZFighterHudLayout.SCALE_STEP);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_TAB) {
			Piece[] pieces = Piece.values();
			this.picked = pieces[this.picked == null ? 0 : (this.picked.ordinal() + 1) % pieces.length];
			return true;
		}
		if (this.picked != null) {
			int step = event.hasShiftDown() ? NUDGE_FAR : NUDGE;
			Place now = ZFighterHudLayout.place(this.picked);
			Place next = null;
			if (key == InputConstants.KEY_LEFT) {
				next = new Place(now.dx() - step, now.dy(), now.scale());
			} else if (key == InputConstants.KEY_RIGHT) {
				next = new Place(now.dx() + step, now.dy(), now.scale());
			} else if (key == InputConstants.KEY_UP) {
				next = new Place(now.dx(), now.dy() - step, now.scale());
			} else if (key == InputConstants.KEY_DOWN) {
				next = new Place(now.dx(), now.dy() + step, now.scale());
			}
			if (next != null) {
				ZFighterHudLayout.put(this.picked, keepOnScreen(this.picked, next));
				return true;
			}
			if (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD) {
				scale(this.picked, ZFighterHudLayout.SCALE_STEP);
				return true;
			}
			if (key == InputConstants.KEY_MINUS) {
				scale(this.picked, -ZFighterHudLayout.SCALE_STEP);
				return true;
			}
			if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
				ZFighterHudLayout.put(this.picked, Place.DEFAULT);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void scale(Piece piece, float by) {
		Place now = ZFighterHudLayout.place(piece);
		float scale = Mth.clamp(Math.round((now.scale() + by) * 10.0F) / 10.0F, ZFighterHudLayout.MIN_SCALE, ZFighterHudLayout.MAX_SCALE);
		ZFighterHudLayout.put(piece, keepOnScreen(piece, new Place(now.dx(), now.dy(), scale)));
	}

	/** {@code place} nudged so the piece stays on the screen. */
	private Place keepOnScreen(Piece piece, Place place) {
		Rect usual = ZFighterHudLayout.rect(piece, this.minecraft.player, this.width, this.height);
		Rect at = ZFighterHudLayout.placed(usual, place);
		int dx = place.dx(), dy = place.dy();
		if (at.x() < 0) {
			dx -= at.x();
		} else if (at.x() + at.w() > this.width) {
			dx -= at.x() + at.w() - this.width;
		}
		if (at.y() < 0) {
			dy -= at.y();
		} else if (at.y() + at.h() > this.height) {
			dy -= at.y() + at.h() - this.height;
		}
		return new Place(dx, dy, place.scale());
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (this.minecraft.player == null) {
			return;
		}
		Piece hover = this.dragging ? this.picked : pieceAt(mouseX, mouseY);
		for (Piece piece : Piece.values()) {
			Rect r = placed(piece);
			boolean isPicked = piece == this.picked;
			graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), isPicked ? BOX_PICKED : piece == hover ? BOX_HOVER : BOX);
			graphics.outline(r.x(), r.y(), r.w(), r.h(), isPicked ? ACCENT : 0xA0FFFFFF);
			sample(graphics, piece, r);
			Component name = Component.translatable("hud.jaymospowers.zfighter_hud." + piece.key);
			int ty = r.y() > 10 ? r.y() - 9 : r.y() + r.h() + 1;
			graphics.text(this.font, name, r.x(), ty, isPicked ? ACCENT : UiStyle.TEXT);
		}
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_hud.help"), this.width / 2, 8, this.width - 16, UiStyle.TEXT);
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_hud.help2"), this.width / 2, 19, this.width - 16, UiStyle.DIM);
		if (this.picked != null) {
			Place place = ZFighterHudLayout.place(this.picked);
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_hud.picked",
				Component.translatable("hud.jaymospowers.zfighter_hud." + this.picked.key), place.dx(), place.dy(), Math.round(place.scale() * 100.0F)),
				this.width / 2, this.height - 38, this.width - 16, ACCENT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** A hint of what the piece looks like inside its box. */
	private void sample(GuiGraphicsExtractor graphics, Piece piece, Rect r) {
		switch (piece) {
			case KI_BAR, CHARGE, SPIRIT, APE_RAGE -> {
				int bx = r.x() + 2, by = r.y() + r.h() - Math.max(3, r.h() / 4), bw = r.w() - 4;
				graphics.fill(bx, by, bx + bw, by + Math.max(2, r.h() / 6), 0x90000000);
				graphics.fill(bx, by, bx + bw * 6 / 10, by + Math.max(2, r.h() / 6), ACCENT);
			}
			case CHIPS -> {
				int chip = Math.max(4, r.h());
				for (int x = r.x(); x + chip <= r.x() + r.w(); x += chip + Math.max(1, chip / 10)) {
					graphics.outline(x, r.y(), chip, r.h(), 0x90FFFFFF);
				}
			}
			default -> {
			}
		}
	}
}
