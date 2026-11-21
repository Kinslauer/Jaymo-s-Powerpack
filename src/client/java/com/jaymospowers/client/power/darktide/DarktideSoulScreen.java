package com.jaymospowers.client.power.darktide;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.ui.FlatButton;
import com.jaymospowers.power.darktide.DarktideNet;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Darktide's soul menu (sprint + Z): the shadows you hold, one button each. Click one to put it in your wave (or take it
 * out), up to as many as can stand at once; a tap of C then raises the wave. Closing the menu sends the wave.
 */
public class DarktideSoulScreen extends Screen {
	private static final int ROW = 18, COLUMN_W = 150, PER_COLUMN = 8;
	private static final int ACCENT = 0xFF2E6FB0;

	private final List<String> names;
	private final TreeSet<Integer> picked;
	private final int max;
	private final List<FlatButton> buttons = new ArrayList<>();

	public DarktideSoulScreen(DarktideNet.Souls souls) {
		super(Component.translatable("screen.jaymospowers.darktide_souls"));
		this.names = souls.names();
		this.picked = new TreeSet<>(souls.picked());
		this.max = souls.max();
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DarktideNet.Souls.TYPE, (payload, ctx) -> ctx.client().gui.setScreen(new DarktideSoulScreen(payload)));
	}

	private int columns() {
		return Math.max(1, (this.names.size() + PER_COLUMN - 1) / PER_COLUMN);
	}

	private int panelW() {
		return columns() * COLUMN_W + 12;
	}

	private int panelH() {
		return UiStyle.HEADER + Math.max(1, Math.min(PER_COLUMN, this.names.size())) * ROW + 10;
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private Component label(int i) {
		Identifier id = Identifier.tryParse(this.names.get(i));
		Component name = id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription()
			: Component.literal(this.names.get(i));
		return this.picked.contains(i) ? Component.translatable("screen.jaymospowers.darktide_souls.in_wave", name) : name;
	}

	@Override
	protected void init() {
		this.buttons.clear();
		for (int i = 0; i < this.names.size(); i++) {
			int index = i;
			FlatButton button = FlatButton.builder(label(i), b -> {
				if (!this.picked.remove(index) && this.picked.size() < this.max) {
					this.picked.add(index);
				}
				b.setMessage(label(index));
			}).bounds(left() + 6 + (i / PER_COLUMN) * COLUMN_W, top() + UiStyle.HEADER + (i % PER_COLUMN) * ROW, COLUMN_W - 4, ROW - 2).build();
			this.buttons.add(button);
			addRenderableWidget(button);
		}
	}

	@Override
	public void removed() {
		if (ClientPlayNetworking.canSend(DarktideNet.SetWave.TYPE)) {
			ClientPlayNetworking.send(new DarktideNet.SetWave(new ArrayList<>(this.picked)));
		}
		super.removed();
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
		UiStyle.header(graphics, this.font, x, y, panelW(), null, this.title,
			Component.translatable(this.names.isEmpty() ? "screen.jaymospowers.darktide_souls.none" : "screen.jaymospowers.darktide_souls.sub", this.max), ACCENT, 0);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
