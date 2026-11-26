package com.jaymoskingdoms.client.mixin.mca;

import com.mojang.blaze3d.platform.InputConstants;
import com.jaymoskingdoms.client.DialogueCinematic;
import com.jaymoskingdoms.client.TalkFirst;
import com.jaymoskingdoms.client.TalkWheel;
import com.jaymoskingdoms.client.Ui;
import java.util.List;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy (2026-11-21): the villager's words in a box on the left, the answers on the right once they've finished speaking
 * ({@link DialogueCinematic}). MCA drew a dialogue question and its answers in the middle of the screen, over the
 * villager. Here the question goes to the box on the left, and the answers come up on the right only once the question
 * is all out.
 *
 * <p>Andy (2026-11-21): the answers as a "fallout 4 styled wheel extending in each direction with a response based on
 * your origin choices". The answers sit round a wheel below the button column (Introduce, Gift, Trade and the rest stay
 * where they are): up to four on the four arms, more spaced evenly, {@link #PER_PAGE} at most, with the scroll wheel
 * turning to the next ones. Each arm shows the answer's short label; pointing at it (its label, or its direction from
 * the middle) shows under the wheel the line your character says ({@link TalkWheel#line}), and picking it says that line
 * on camera. Clicking works as before: MCA sends the answer under the mouse.
 *
 * <p>Andy (2026-11-25): "I want all interactions accessible through talk". The wheel also carries a spoke for each of MCA's
 * buttons on the page laid out ({@link TalkFirst}): Family Tree and Ask of them... ahead of the talk's answers, and on Ask
 * of them... and the pages under it their buttons and Back. The column above holds Gift and Trade only.
 */
@Mixin(InteractScreen.class)
abstract class McaDialogueBoxMixin extends Screen {
	/** Most answers on the wheel at once; the scroll wheel turns to the rest. */
	@Unique
	private static final int PER_PAGE = 8;
	/** The wheel's right margin, its gap to the column above and the line box below, the label's padding and height, and widest label (GUI pixels). */
	@Unique
	private static final int RIGHT_GAP = 10, GAP = 6, LABEL_PAD = 4, LABEL_HIGH = 12, LABEL_MAX = 110;
	/** The wheel's radius at least and at most, and the dead middle where pointing picks nothing (GUI pixels). */
	@Unique
	private static final int RADIUS_MIN = 22, RADIUS_MAX = 46, DEAD = 7;
	/** The line under the wheel: its wrap width, most rows, and the room kept for it so the wheel never jumps (GUI pixels). */
	@Unique
	private static final int LINE_WIDTH = 190, LINE_ROWS = 3, LINE_ROOM = DialogueCinematic.BOX_PAD * 2 + (LINE_ROWS + 1) * 10 + 2;
	/** An answer's colour, and pointed at (as MCA's); the wheel's disc and its arms. */
	@Unique
	private static final int ANSWER = 0xDDFFFFFF, HOVER = 0xFFD7D7C4, DISC = 0x90100010, ARM = 0x905000FF, ARM_LIT = 0xFFB98CFF;

	@Shadow
	@Final
	private VillagerLike<?> villager;
	@Shadow
	private List<String> dialogAnswers;
	@Shadow
	private String dialogAnswerHover;
	@Shadow
	private List<FormattedCharSequence> dialogQuestionText;
	@Shadow
	private String dialogQuestionId;

	/** Which page of answers the wheel is on, how many there are, and where its middle and rim were last drawn. */
	@Unique
	private int jaymoskingdoms$page, jaymoskingdoms$pages, jaymoskingdoms$cx, jaymoskingdoms$cy, jaymoskingdoms$radius;
	/** The ring (MCA's page) the wheel last showed: a new one starts on its first spokes. */
	@Unique
	private String jaymoskingdoms$ring = "";

	protected McaDialogueBoxMixin(Component title) {
		super(title);
	}

	/** MCA's question and answers in the middle are skipped: the box and the wheel draw them. */
	@Redirect(method = "drawTextPopups", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
		target = "Lnet/conczin/mca/client/gui/InteractScreen;dialogQuestionText:Ljava/util/List;"))
	private @Nullable List<FormattedCharSequence> jaymoskingdoms$noMiddle(InteractScreen self) {
		return null;
	}

	/** The question's words go to the box on the left. */
	@ModifyArg(method = "setLastPhrase", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"), index = 0)
	private FormattedText jaymoskingdoms$toBox(FormattedText text) {
		if (text instanceof Component said) {
			DialogueCinematic.said(this.villager.asEntity().getId(), said);
		}
		return text;
	}

	/** A new question starts the wheel on its first answers. */
	@Inject(method = "setDialogue", at = @At("TAIL"))
	private void jaymoskingdoms$newQuestion(String question, List<String> answers, CallbackInfo ci) {
		this.jaymoskingdoms$page = 0;
	}

	/**
	 * (Andy, 2026-11-25) A spoke that stands for one of MCA's buttons (Family Tree, Ask of them..., Back...) is done here,
	 * as the button would be: nothing goes to the villager's talk for it, and nothing is said on camera.
	 */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$spoke(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && TalkFirst.local(this.dialogAnswerHover)) {
			String spoke = this.dialogAnswerHover;
			this.dialogAnswerHover = null;
			TalkFirst.pick((InteractScreen)(Object)this, spoke);
			cir.setReturnValue(true);
		}
	}

	/** (Andy, 2026-11-25) Putting a gift away goes back to the talk, not to MCA's Interact page (a ring of the wheel now). */
	@ModifyArg(method = "keyPressed", at = @At(value = "INVOKE",
		target = "Lnet/conczin/mca/client/gui/InteractScreen;setLayout(Ljava/lang/String;)V"), index = 0)
	private String jaymoskingdoms$giftAway(String layout) {
		return "interact".equals(layout) ? "main" : layout;
	}

	/** The answer picked: your line for it is what you say on camera. */
	@Inject(method = "mouseClicked", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/conczin/mca/network/Network;sendToServer(Lnet/conczin/mca/network/HandleablePayload;)V"))
	private void jaymoskingdoms$picked(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		Component line = TalkWheel.line(this.dialogAnswerHover);
		if (line != null) {
			DialogueCinematic.mine(line);
		}
	}

	/** Scrolling over the wheel turns it to the next (or last) answers. */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$turn(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (this.jaymoskingdoms$pages <= 1 || scrollY == 0.0) {
			return;
		}
		double dx = mouseX - this.jaymoskingdoms$cx, dy = mouseY - this.jaymoskingdoms$cy;
		double reach = this.jaymoskingdoms$radius + LABEL_MAX / 2.0;
		if (dx * dx + dy * dy <= reach * reach) {
			this.jaymoskingdoms$page = Math.floorMod(this.jaymoskingdoms$page + (scrollY < 0.0 ? 1 : -1), this.jaymoskingdoms$pages);
			cir.setReturnValue(true);
		}
	}

	/** The wheel, once the question is all out; the answer pointed at is what a click sends. */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void jaymoskingdoms$answers(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		this.dialogAnswerHover = null;
		this.jaymoskingdoms$pages = 0;
		InteractScreen self = (InteractScreen)(Object)this;
		boolean talking = this.dialogQuestionText != null;
		// (Andy, 2026-11-25: every interaction through talk) MCA's answers, and the spokes that stand for its buttons
		List<String> spokes = TalkFirst.spokes(self, talking ? this.dialogQuestionId : null, talking ? this.dialogAnswers : null);
		if (!self.getActiveScreen().equals(this.jaymoskingdoms$ring)) {
			this.jaymoskingdoms$ring = self.getActiveScreen();
			this.jaymoskingdoms$page = 0;
		}
		if (spokes.isEmpty() || !DialogueCinematic.answersUp()) {
			return;
		}
		int pages = (spokes.size() + PER_PAGE - 1) / PER_PAGE;
		int page = Math.floorMod(this.jaymoskingdoms$page, pages);
		List<String> shown = spokes.subList(page * PER_PAGE, Math.min(spokes.size(), (page + 1) * PER_PAGE));
		int n = shown.size();
		// up to four on the four arms (up, right, down, left), more spaced evenly from the top
		int slots = Math.max(n, 4);
		double step = Math.PI * 2.0 / slots;
		String[] labels = new String[n];
		int rightRoom = 0;
		for (int i = 0; i < n; i++) {
			labels[i] = Ui.cut(this.font, jaymoskingdoms$text(shown.get(i)).getString(), LABEL_MAX);
			if (Math.sin(i * step) > 0.3) {
				rightRoom = Math.max(rightRoom, this.font.width(labels[i]) + LABEL_PAD * 2);
			}
		}
		// below the button column, above the line's room over the bottom bar
		int bar = Math.round(this.height * 0.11F);
		int top = bar + GAP;
		for (GuiEventListener l : this.children()) {
			if (l instanceof AbstractWidget w && w.visible && w.getX() > this.width / 2 && w.getRight() >= this.width - RIGHT_GAP - 1) {
				top = Math.max(top, w.getBottom() + GAP);
			}
		}
		int floor = this.height - bar - GAP - LINE_ROOM - GAP;
		int radius = Mth.clamp((floor - top - LABEL_HIGH * 2 - 4) / 2, RADIUS_MIN, RADIUS_MAX);
		int cy = Math.max(top + LABEL_HIGH + radius + 2, (top + floor) / 2);
		int cx = this.width - RIGHT_GAP - rightRoom - 2 - radius;
		this.jaymoskingdoms$page = page;
		this.jaymoskingdoms$pages = pages;
		this.jaymoskingdoms$cx = cx;
		this.jaymoskingdoms$cy = cy;
		this.jaymoskingdoms$radius = radius;

		// where each label sits, at the end of its arm
		int[][] boxes = new int[n][4];
		for (int i = 0; i < n; i++) {
			double s = Math.sin(i * step), c = Math.cos(i * step);
			int tx = cx + (int) Math.round((radius + 2) * s), ty = cy - (int) Math.round((radius + 2) * c);
			int lw = this.font.width(labels[i]) + LABEL_PAD * 2;
			int x0 = s > 0.3 ? tx + 2 : s < -0.3 ? tx - 2 - lw : tx - lw / 2;
			int y0 = Math.abs(s) > 0.3 ? ty - LABEL_HIGH / 2 : c > 0.0 ? ty - LABEL_HIGH - 1 : ty + 1;
			boxes[i] = new int[] { x0, y0, x0 + lw, y0 + LABEL_HIGH };
		}
		// pointed at: a label under the mouse, else the arm in the mouse's direction from the middle
		int lit = -1;
		for (int i = 0; i < n && lit < 0; i++) {
			if (mouseX >= boxes[i][0] && mouseX < boxes[i][2] && mouseY >= boxes[i][1] && mouseY < boxes[i][3]) {
				lit = i;
			}
		}
		double dx = mouseX - cx, dy = mouseY - cy, far = Math.sqrt(dx * dx + dy * dy);
		if (lit < 0 && far >= DEAD && far <= radius + 6) {
			int arm = Math.floorMod((int) Math.round(Math.atan2(dx, -dy) / step), slots);
			lit = arm < n ? arm : -1;
		}
		if (lit >= 0) {
			this.dialogAnswerHover = shown.get(lit);
		}

		// the disc, its rim, and an arm to each answer
		for (int y = -radius; y <= radius; y++) {
			int half = (int) Math.sqrt((double) radius * radius - y * y);
			g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, DISC);
		}
		int rim = (int) Math.ceil(Math.PI * 2.0 * radius);
		for (int k = 0; k < rim; k++) {
			double a = Math.PI * 2.0 * k / rim;
			int px = cx + (int) Math.round(radius * Math.sin(a)), py = cy - (int) Math.round(radius * Math.cos(a));
			g.fill(px, py, px + 1, py + 1, DialogueCinematic.BORDER_TOP);
		}
		for (int i = 0; i < n; i++) {
			double s = Math.sin(i * step), c = Math.cos(i * step);
			int thick = i == lit ? 2 : 1, color = i == lit ? ARM_LIT : ARM;
			for (int r = DEAD; r <= radius; r++) {
				int px = cx + (int) Math.round(r * s), py = cy - (int) Math.round(r * c);
				g.fill(px, py, px + thick, py + thick, color);
			}
		}
		// the labels
		for (int i = 0; i < n; i++) {
			int[] b = boxes[i];
			jaymoskingdoms$frame(g, b[0], b[1], b[2], b[3]);
			if (i == lit) {
				g.fill(b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, 0x30FFFFFF);
			}
			g.text(this.font, labels[i], b[0] + LABEL_PAD, b[1] + (LABEL_HIGH - this.font.lineHeight) / 2 + 1, i == lit ? HOVER : ANSWER);
		}
		// the middle: which page of how many, when there's more than one
		if (pages > 1) {
			String pageText = (page + 1) + "/" + pages;
			g.text(this.font, pageText, cx - this.font.width(pageText) / 2 + 1, cy - this.font.lineHeight / 2 + 1, ANSWER);
		} else {
			g.fill(cx - 1, cy - 1, cx + 2, cy + 2, ARM);
		}

		// the line you'd say, under the wheel
		Component line = lit < 0 ? null : TalkWheel.line(shown.get(lit));
		if (line != null && Minecraft.getInstance().player != null) {
			List<FormattedCharSequence> rows = this.font.split(line, LINE_WIDTH - DialogueCinematic.BOX_PAD * 2);
			rows = rows.subList(0, Math.min(LINE_ROWS, rows.size()));
			Component name = Minecraft.getInstance().player.getDisplayName();
			int wide = this.font.width(name);
			for (FormattedCharSequence row : rows) {
				wide = Math.max(wide, this.font.width(row));
			}
			int rowHigh = this.font.lineHeight + 1;
			int x1 = this.width - RIGHT_GAP, x0 = x1 - wide - DialogueCinematic.BOX_PAD * 2;
			int y1 = this.height - bar - GAP, y0 = y1 - DialogueCinematic.BOX_PAD * 2 - (rows.size() + 1) * rowHigh - 2;
			jaymoskingdoms$frame(g, x0, y0, x1, y1);
			int y = y0 + DialogueCinematic.BOX_PAD;
			g.text(this.font, name.getVisualOrderText(), x0 + DialogueCinematic.BOX_PAD, y, DialogueCinematic.NAME_COLOR);
			y += rowHigh + 2;
			for (FormattedCharSequence row : rows) {
				g.text(this.font, row, x0 + DialogueCinematic.BOX_PAD, y, 0xFFFFFFFF);
				y += rowHigh;
			}
		}
	}

	/** A box in the talk's dark purple, as the villager's on the left. */
	@Unique
	private static void jaymoskingdoms$frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, DialogueCinematic.BOX_FILL);
		g.fillGradient(x0, y0, x0 + 1, y1, DialogueCinematic.BORDER_TOP, DialogueCinematic.BORDER_BOTTOM);
		g.fillGradient(x1 - 1, y0, x1, y1, DialogueCinematic.BORDER_TOP, DialogueCinematic.BORDER_BOTTOM);
		g.fill(x0, y0, x1, y0 + 1, DialogueCinematic.BORDER_TOP);
		g.fill(x0, y1 - 1, x1, y1, DialogueCinematic.BORDER_BOTTOM);
	}

	@Unique
	private Component jaymoskingdoms$text(String answer) {
		if (TalkFirst.local(answer)) {
			return TalkFirst.label(answer);
		}
		return Component.translatable(Question.getTranslationKey(this.dialogQuestionId, answer));
	}
}
