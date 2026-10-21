package com.jaymospowers.client.wounds;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Treatment;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * (0.14.4) The body screen (Wounds phase 4): the creature being treated, its parts each a button (greyed where the
 * medicine in hand can't help), a person's drawn large as the HUD's silhouette beside them. Hovering a part or its
 * button shows its state in the box at the bottom. Picking one sends only the part ({@link Treatment.Treat}); the server
 * checks it again and runs the treatment (hold still).
 */
public class BodyScreen extends Screen {
	private static final int MAX_W = 320;
	private static final int ROW = 18;
	private static final int BUTTON_W = 130;
	private static final int DETAIL_LINES = 4;
	/** The silhouette's unit (pixels), and its size in units (as {@link WoundsHud}'s). */
	private static final int UNIT = 5, SIL_W = 8, SIL_H = 17;
	private static final int ACCENT = 0xFFC83232;
	/** A person's parts in plan order (head, body, left arm, right arm, left leg, right leg, left foot, right foot): x, y, w, h in units. */
	private static final int[][] BOXES = {
		{2, 0, 4, 4}, {2, 4, 4, 6}, {0, 4, 2, 6}, {6, 4, 2, 6}, {2, 10, 2, 5}, {4, 10, 2, 5}, {2, 15, 2, 2}, {4, 15, 2, 2},
	};

	private final Treatment.OpenBody body;

	public BodyScreen(Treatment.OpenBody body) {
		super(Component.translatable("screen.jaymospowers.wounds.body", body.name()));
		this.body = body;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Treatment.OpenBody.TYPE, (payload, context) -> context.client().gui.setScreen(new BodyScreen(payload)));
	}

	private boolean person() {
		return this.body.plan() == BodyPlan.HUMANOID.ordinal() && this.body.parts().size() == BOXES.length;
	}

	private int count() {
		return Math.min(this.body.parts().size(), Math.min(this.body.fractions().size(), this.body.states().size()));
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int bodyH() {
		return Math.max(person() ? SIL_H * UNIT + 8 : 0, count() * ROW + 6);
	}

	private int panelH() {
		return UiStyle.HEADER + bodyH() + UiStyle.detailHeight(DETAIL_LINES);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rowY(int i) {
		return top() + UiStyle.HEADER + i * ROW;
	}

	private int buttonX() {
		return left() + panelW() - BUTTON_W - 8;
	}

	private int silX() {
		return person() ? left() + Math.max(8, (panelW() - BUTTON_W - 16 - SIL_W * UNIT) / 2) : left() + 8;
	}

	private BodyPart part(int i) {
		int ordinal = this.body.parts().get(i);
		return ordinal >= 0 && ordinal < BodyPart.values().length ? BodyPart.values()[ordinal] : BodyPart.BODY;
	}

	private int state(int i) {
		return this.body.states().get(i);
	}

	private Component label(int i) {
		int percent = Math.round(Mth.clamp(this.body.fractions().get(i), 0.0F, 1.0F) * 100.0F);
		return Component.literal(part(i).displayName().getString() + "  " + percent + "%");
	}

	@Override
	protected void init() {
		for (int i = 0; i < count(); i++) {
			int index = i;
			Button button = addRenderableWidget(Button.builder(label(i), b -> {
				ClientPlayNetworking.send(new Treatment.Treat(this.body.entity(), this.body.parts().get(index)));
				this.onClose();
			}).bounds(buttonX(), rowY(i) + 1, BUTTON_W, 16).build());
			button.active = (state(i) & Treatment.HELPS) != 0;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left();
		int y = top();
		int w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		UiStyle.header(graphics, this.font, x, y, w, null, this.title, Component.translatable("screen.jaymospowers.wounds.body_sub"), ACCENT, 0);
		int hovered = -1;
		for (int i = 0; i < count(); i++) {
			if (UiStyle.over(mouseX, mouseY, buttonX(), rowY(i), BUTTON_W, ROW - 2)) {
				hovered = i;
			}
		}
		if (person()) {
			int sx = silX();
			int sy = y + UiStyle.HEADER + 4;
			for (int i = 0; i < BOXES.length; i++) {
				int[] box = BOXES[i];
				if (UiStyle.over(mouseX, mouseY, sx + box[0] * UNIT, sy + box[1] * UNIT, box[2] * UNIT, box[3] * UNIT)) {
					hovered = i;
				}
			}
			// the outline first, so shared edges stay dark
			for (int[] box : BOXES) {
				graphics.fill(sx + box[0] * UNIT - 1, sy + box[1] * UNIT - 1, sx + (box[0] + box[2]) * UNIT + 1, sy + (box[1] + box[3]) * UNIT + 1,
					0xFF000000 | WoundsHud.OUTLINE);
			}
			for (int i = 0; i < BOXES.length; i++) {
				int[] box = BOXES[i];
				int colour = (state(i) & Treatment.MISSING) != 0 ? WoundsHud.GONE : colour(this.body.fractions().get(i));
				if (i == hovered) {
					colour = WoundsHud.FLASH;
				}
				int x0 = sx + box[0] * UNIT, y0 = sy + box[1] * UNIT, x1 = sx + (box[0] + box[2]) * UNIT, y1 = sy + (box[1] + box[3]) * UNIT;
				graphics.fill(x0, y0, x1, y1, 0xFF000000 | colour);
				// a red mark on a cut, green on festering, a pale bar on a splint
				if ((state(i) & Treatment.BLEEDING) != 0) {
					graphics.fill(x0 + 1, y0 + 1, x0 + 3, y0 + 3, 0xFF8A0303);
				}
				if ((state(i) & Treatment.FESTERING) != 0) {
					graphics.fill(x1 - 3, y0 + 1, x1 - 1, y0 + 3, 0xFF5A8A20);
				}
				if ((state(i) & Treatment.SPLINTED) != 0) {
					graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0xFFD8C8A0);
				}
			}
		} else {
			for (int i = 0; i < count(); i++) {
				int ry = rowY(i);
				UiStyle.row(graphics, x + 6, ry, buttonX() - 4, ry + ROW - 2, i, i == hovered);
				UiStyle.line(graphics, this.font, states(i), x + 12, ry + 4, buttonX() - x - 20, UiStyle.DIM);
			}
		}
		int dy = y + panelH() - UiStyle.detailHeight(DETAIL_LINES);
		if (hovered >= 0) {
			List<Component> lines = new ArrayList<>();
			int percent = Math.round(Mth.clamp(this.body.fractions().get(hovered), 0.0F, 1.0F) * 100.0F);
			lines.add(Component.translatable("screen.jaymospowers.wounds.health", percent));
			lines.add(states(hovered));
			if ((state(hovered) & Treatment.HELPS) == 0) {
				lines.add(Component.translatable("screen.jaymospowers.wounds.state.cannot"));
			}
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, part(hovered).displayName(), ACCENT, lines);
		} else {
			UiStyle.detail(graphics, this.font, x, dy, w, DETAIL_LINES, null, ACCENT, List.of(Component.translatable("screen.jaymospowers.wounds.help")));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The part's states, joined: broken, bleeding, festering, splinted, bandaged, gone. */
	private Component states(int i) {
		int state = state(i);
		List<String> words = new ArrayList<>();
		String[] keys = {"broken", "bleeding", "festering", "splinted", "bandaged", "missing"};
		int[] bits = {Treatment.BROKEN, Treatment.BLEEDING, Treatment.FESTERING, Treatment.SPLINTED, Treatment.BANDAGED, Treatment.MISSING};
		for (int k = 0; k < keys.length; k++) {
			if ((state & bits[k]) != 0) {
				words.add(Component.translatable("screen.jaymospowers.wounds.state." + keys[k]).getString());
			}
		}
		return Component.literal(String.join(", ", words));
	}

	private static int colour(float fraction) {
		if (fraction > 0.75F) {
			return WoundsHud.GOOD;
		}
		if (fraction > 0.5F) {
			return WoundsHud.FAIR;
		}
		if (fraction > 0.25F) {
			return WoundsHud.POOR;
		}
		return fraction > 0.0F ? WoundsHud.BAD : WoundsHud.GONE;
	}
}
