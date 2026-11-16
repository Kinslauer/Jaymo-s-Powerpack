package com.jaymoskingdoms.client;

import com.jaymoskingdoms.SignIn;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The sign-in screen for an offline account on a server running EasyAuth ({@link SignIn}): the password kept with this
 * name, or a new one chosen (typed twice). It sends EasyAuth's own {@code login} or {@code register} command, so nobody
 * types a slash command, and it comes down when the server says they're in. Typed passwords show as dots. It can't be
 * shut, only left (which leaves the server), since nothing can be played before signing in.
 */
final class SignInScreen extends Screen {
	static final int W = 240, PAD = 10, BOX_H = 18, GAP = 6, BUTTON_H = 16;
	/** How long an answer may take before the password counts as refused (ms). */
	static final long ANSWER_MS = 3000L;

	private final boolean choosing;
	private @Nullable EditBox password, again;
	/** When the password was last sent (ms), or -1; and the line under the boxes (a refusal, a mismatch), or null. */
	private long sentAt = -1L;
	private @Nullable Component note;

	private SignInScreen(boolean choosing) {
		super(Component.translatable(choosing ? "gui.jaymoskingdoms.sign_in.choose" : "gui.jaymoskingdoms.sign_in.title"));
		this.choosing = choosing;
	}

	/** The screen the server asked for and not yet shown ({@link SignIn#DONE} for none): it waits for the world to load in. */
	private static int pending = SignIn.DONE;

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SignIn.Prompt.TYPE, (payload, context) -> {
			pending = payload.state();
			if (pending == SignIn.DONE && context.client().gui.screen() instanceof SignInScreen) {
				context.client().gui.setScreen(null);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (pending != SignIn.DONE && minecraft.player != null && minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new SignInScreen(pending == SignIn.REGISTER));
				pending = SignIn.DONE;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> pending = SignIn.DONE);
	}

	private int height0() {
		return 34 + 12 + (this.choosing ? 2 : 1) * (BOX_H + GAP) + 14 + BUTTON_H + PAD;
	}

	@Override
	protected void init() {
		int left = (this.width - W) / 2, top = (this.height - height0()) / 2;
		int y = top + 34 + 12;
		this.password = box(left, y, "gui.jaymoskingdoms.sign_in.password");
		y += BOX_H + GAP;
		if (this.choosing) {
			this.again = box(left, y, "gui.jaymoskingdoms.sign_in.again");
			y += BOX_H + GAP;
		}
		y += 14;
		int half = (W - 2 * PAD - GAP) / 2;
		addRenderableWidget(FlatButton.builder(Component.translatable(this.choosing ? "gui.jaymoskingdoms.sign_in.keep" : "gui.jaymoskingdoms.sign_in.enter"),
			b -> send()).bounds(left + PAD, y, half, BUTTON_H).build());
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.sign_in.leave"),
			b -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE)).bounds(left + PAD + half + GAP, y, half, BUTTON_H).build());
		setInitialFocus(this.password);
	}

	private EditBox box(int left, int y, String hint) {
		EditBox box = new EditBox(this.font, left + PAD, y, W - 2 * PAD, BOX_H, Component.translatable(hint));
		box.setMaxLength(64);
		box.setHint(Component.translatable(hint));
		// dots, not the letters
		box.addFormatter((text, first) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
		return addRenderableWidget(box);
	}

	private void send() {
		if (this.password == null || this.minecraft.player == null || this.sentAt >= 0L) {
			return;
		}
		String typed = this.password.getValue();
		if (typed.isEmpty()) {
			return;
		}
		if (this.choosing && (this.again == null || !typed.equals(this.again.getValue()))) {
			this.note = Component.translatable("gui.jaymoskingdoms.sign_in.mismatch");
			return;
		}
		String quoted = quote(typed);
		this.minecraft.player.connection.sendCommand(this.choosing ? "register " + quoted + " " + quoted : "login " + quoted);
		this.sentAt = System.currentTimeMillis();
		this.note = Component.translatable("gui.jaymoskingdoms.sign_in.checking");
	}

	/** {@code s} as one quoted command argument (EasyAuth reads a password as a string). */
	private static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	@Override
	public void tick() {
		// no word back in time: refused (EasyAuth says why in chat, and lets only a few tries go before it sends them off)
		if (this.sentAt >= 0L && System.currentTimeMillis() - this.sentAt > ANSWER_MS) {
			this.sentAt = -1L;
			this.note = Component.translatable(this.choosing ? "gui.jaymoskingdoms.sign_in.not_kept" : "gui.jaymoskingdoms.sign_in.refused");
			if (this.password != null) {
				this.password.setValue("");
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int left = (this.width - W) / 2, top = (this.height - height0()) / 2;
		Ui.panel(graphics, left, top, W, height0(), Ui.GOLD);
		Ui.line(graphics, this.font, this.title, left + PAD, top + 8, W - 2 * PAD, Ui.GOLD);
		Ui.wrapped(graphics, this.font, Component.translatable(this.choosing ? "gui.jaymoskingdoms.sign_in.choose_why" : "gui.jaymoskingdoms.sign_in.why"),
			left + PAD, top + 22, W - 2 * PAD, 2, 10, Ui.DIM, false);
		if (this.note != null) {
			int y = top + 34 + 12 + (this.choosing ? 2 : 1) * (BOX_H + GAP);
			Ui.line(graphics, this.font, this.note, left + PAD, y, W - 2 * PAD, this.sentAt >= 0L ? Ui.DIM : Ui.BAD);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
