package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.DialogueCinematic;
import java.util.List;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
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

/**
 * Andy (2026-11-21): the villager's words in a box on the left, the answers in a list on the right once they've finished
 * speaking ({@link DialogueCinematic}). MCA drew a dialogue question and its answers in the middle of the screen, over
 * the villager. Here the question goes to the box on the left, and the answers are drawn on the right, low, over the
 * bottom bar, only once the question is all out. Clicking one works as before (MCA sends the answer under the mouse).
 */
@Mixin(InteractScreen.class)
abstract class McaDialogueBoxMixin extends Screen {
	/** The answers' right margin and gap over the bottom bar, their padding, row height, and widest (GUI pixels). */
	@Unique
	private static final int RIGHT_GAP = 10, PAD = 4, ROW = 12, MAX_WIDTH = 200;
	/** An answer's colour, and under the mouse (as MCA's). */
	@Unique
	private static final int ANSWER = 0xAAFFFFFF, HOVER = 0xFFD7D7C4;

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

	protected McaDialogueBoxMixin(Component title) {
		super(title);
	}

	/** MCA's question and answers in the middle are skipped: the box and the list below draw them. */
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

	/** The answers, on the right, once the question is all out; the one under the mouse is what a click sends. */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void jaymoskingdoms$answers(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		this.dialogAnswerHover = null;
		if (this.dialogQuestionText == null || this.dialogAnswers == null || this.dialogAnswers.isEmpty() || !DialogueCinematic.answersUp()) {
			return;
		}
		int wide = 0;
		for (String answer : this.dialogAnswers) {
			wide = Math.max(wide, this.font.width(jaymoskingdoms$text(answer)));
		}
		wide = Math.min(MAX_WIDTH, wide) + PAD * 2;
		int x1 = this.width - RIGHT_GAP, x0 = x1 - wide;
		int bar = Math.round(this.height * 0.11F);
		int y1 = this.height - bar - DialogueCinematic.BOX_GAP, y0 = y1 - PAD * 2 - this.dialogAnswers.size() * ROW;
		g.fill(x0, y0, x1, y1, DialogueCinematic.BOX_FILL);
		g.fillGradient(x0, y0, x0 + 1, y1, DialogueCinematic.BORDER_TOP, DialogueCinematic.BORDER_BOTTOM);
		g.fillGradient(x1 - 1, y0, x1, y1, DialogueCinematic.BORDER_TOP, DialogueCinematic.BORDER_BOTTOM);
		g.fill(x0, y0, x1, y0 + 1, DialogueCinematic.BORDER_TOP);
		g.fill(x0, y1 - 1, x1, y1, DialogueCinematic.BORDER_BOTTOM);
		int y = y0 + PAD;
		for (String answer : this.dialogAnswers) {
			boolean over = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
			if (over) {
				this.dialogAnswerHover = answer;
				g.fill(x0 + 1, y, x1 - 1, y + ROW, 0x30FFFFFF);
			}
			g.text(this.font, jaymoskingdoms$text(answer).getVisualOrderText(), x0 + PAD, y + (ROW - this.font.lineHeight) / 2 + 1, over ? HOVER : ANSWER);
			y += ROW;
		}
	}

	@Unique
	private Component jaymoskingdoms$text(String answer) {
		return Component.translatable(Question.getTranslationKey(this.dialogQuestionId, answer));
	}
}
