package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.509) The boon menu (docs/Patrons-Plan.md section 8), opened by the boon key (left square bracket) while sworn: your patron, the
 * boon's name and what it does, whether it is ready, a Call button (tier 4 and ready), and a button to the patrons screen.
 */
final class BoonScreen extends Screen {
	private static final int W = 230, H = 118;
	private @Nullable Button call;

	BoonScreen() {
		super(Component.translatable("gui.jaymoskingdoms.patrons.boon"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	/** "Ready", "Ready in 2 days 5 hours" or "Reach the fourth tier". */
	static Component stateLine(Patrons.State s, Minecraft minecraft) {
		if (s.tier() < 4) {
			return Component.translatable("gui.jaymoskingdoms.patrons.boon.locked");
		}
		long left = s.boonReady() - (minecraft.level == null ? 0L : minecraft.level.getGameTime());
		if (left <= 0L) {
			return Component.translatable("gui.jaymoskingdoms.patrons.boon.ready");
		}
		return Component.translatable("gui.jaymoskingdoms.patrons.boon.wait", left / 24000L, (left % 24000L) / 1000L);
	}

	@Override
	protected void init() {
		int x = left(), y = top() + H - 24;
		this.call = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.patrons.boon.call"), b -> {
			ClientPlayNetworking.send(Patrons.CallBoon.INSTANCE);
			onClose();
		}).bounds(x + 10, y, 80, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.patrons.button").append("..."),
			b -> this.minecraft.gui.setScreen(new PatronScreen(this, null))).bounds(x + W - 100, y, 90, 16).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Patrons.State s = this.minecraft.player == null ? Patrons.State.EMPTY : Patrons.state(this.minecraft.player);
		Patron p = s.patronOf();
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, H, p != null ? p.colour : Ui.GOLD);
		if (p == null) {
			onClose();
			return;
		}
		Ui.line(graphics, this.font, p.named(), x + 10, y + 8, W - 20, p.colour);
		Ui.line(graphics, this.font, Component.translatable("blessing.jaymoskingdoms." + p.key + ".4"), x + 10, y + 21, W - 20, Ui.TEXT);
		int ty = y + 34;
		for (var line : this.font.split(Component.translatable("blessing.jaymoskingdoms." + p.key + ".4.desc"), W - 20)) {
			graphics.text(this.font, line, x + 10, ty, Ui.DIM);
			ty += Ui.LINE;
		}
		boolean ready = s.tier() >= 4 && s.boonReady() <= (this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime());
		Ui.line(graphics, this.font, stateLine(s, this.minecraft), x + 10, y + H - 40, W - 20, ready ? Ui.GOOD : Ui.HINT);
		if (this.call != null) {
			this.call.active = ready;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
