package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterSensesNet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (0.17.45, Andy's NOEA picks) Instant Teleport's list, opened by holding B for a second: the friends (players) and masters
 * whose ki you sensed within the last day, nearest friends first. Pick one and you appear beside them. Escape closes it.
 */
public class ZFighterTeleportScreen extends Screen {
	private static final int ROW = 18;
	private static final int COLUMN_W = 160;
	private static final int PER_COLUMN = 12;
	private static final int ACCENT = 0xFF8FD3FF;

	private final List<ZFighterSensesNet.Dest> dests;

	public ZFighterTeleportScreen(List<ZFighterSensesNet.Dest> dests) {
		super(Component.translatable("screen.jaymospowers.zfighter_senses.teleport"));
		this.dests = dests;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSensesNet.OpenTeleport.TYPE,
			(payload, ctx) -> ctx.client().gui.setScreen(new ZFighterTeleportScreen(payload.dests())));
	}

	private int columns() {
		return Math.max(1, (this.dests.size() + PER_COLUMN - 1) / PER_COLUMN);
	}

	private int panelW() {
		return columns() * COLUMN_W + 12;
	}

	private int rows() {
		return Math.min(PER_COLUMN, this.dests.size());
	}

	private int panelH() {
		return UiStyle.HEADER + rows() * ROW + 10;
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	@Override
	protected void init() {
		for (int i = 0; i < this.dests.size(); i++) {
			ZFighterSensesNet.Dest dest = this.dests.get(i);
			int column = i / PER_COLUMN;
			int row = i % PER_COLUMN;
			Component label = Component.translatable(dest.master() ? "screen.jaymospowers.zfighter_senses.teleport_master" : "screen.jaymospowers.zfighter_senses.teleport_friend",
				dest.name(), dest.distance());
			addRenderableWidget(Button.builder(label, b -> {
				ClientPlayNetworking.send(new ZFighterSensesNet.TeleportTo(dest.id()));
				this.onClose();
			}).bounds(left() + 6 + column * COLUMN_W, top() + UiStyle.HEADER + row * ROW, COLUMN_W - 4, ROW - 2).build());
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
		UiStyle.panel(graphics, x, y, panelW(), panelH(), ACCENT);
		UiStyle.header(graphics, this.font, x, y, panelW(), null, this.title, Component.translatable("screen.jaymospowers.zfighter_senses.teleport_sub"), ACCENT, 0);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
