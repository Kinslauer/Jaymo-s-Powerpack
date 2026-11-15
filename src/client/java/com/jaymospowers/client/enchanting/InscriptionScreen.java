package com.jaymospowers.client.enchanting;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.enchanting.Inscription;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Medieval picks (me-inscribe): the words of power open on an Inscription Table, one striped row each (in the gem's
 * colour), with the level it would carve. Click one to carve it.
 */
public class InscriptionScreen extends Screen {
	private static final int MAX_W = 260, ROW_H = 18;

	private final BlockPos pos;
	private final ItemStack gear;
	private final int accent;
	private final List<Inscription.Word> words;
	private int scroll;

	InscriptionScreen(Inscription.Open open) {
		super(Component.translatable("screen.jaymospowers.inscription.title"));
		this.pos = open.pos();
		this.gear = open.gear();
		this.accent = 0xFF000000 | open.gemColour();
		this.words = open.words();
	}

	/** Registers the screen's packet and the table's renderer. */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Inscription.Open.TYPE, (payload, context) ->
			Minecraft.getInstance().gui.setScreen(new InscriptionScreen(payload)));
		BlockEntityRenderers.register(Inscription.ENTITY, InscriptionTableRenderer::new);
	}

	private int panelW() {
		return Math.min(MAX_W, width - 12);
	}

	private int visibleRows() {
		return Math.max(1, Math.min(words.size(), (height - 16 - UiStyle.HEADER - 8) / ROW_H));
	}

	private int panelH() {
		return UiStyle.HEADER + visibleRows() * ROW_H + 8;
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (height - panelH()) / 2);
	}

	private int maxScroll() {
		return Math.max(0, words.size() - visibleRows());
	}

	private int at(double mouseX, double mouseY) {
		int x = left() + 6, y = top() + UiStyle.HEADER;
		for (int row = 0; row < visibleRows(); row++) {
			int i = row + scroll;
			if (i < words.size() && UiStyle.over((int) mouseX, (int) mouseY, x, y + row * ROW_H, panelW() - 14, ROW_H - 2)) {
				return i;
			}
		}
		return -1;
	}

	private Component name(Inscription.Word word) {
		var level = Minecraft.getInstance().level;
		Identifier id = Identifier.tryParse(word.id());
		Holder<Enchantment> holder = level == null || id == null ? null
			: level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(id).orElse(null);
		return holder == null ? Component.literal(word.id()) : Enchantment.getFullname(holder, word.level());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = at(event.x(), event.y());
		if (i >= 0) {
			ClientPlayNetworking.send(new Inscription.Carve(pos, words.get(i).id()));
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		scroll = Mth.clamp(scroll, 0, maxScroll());
		int x = left(), y = top(), w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), accent);
		UiStyle.header(graphics, font, x, y, w, gear, title, gear.getHoverName(), accent, 0);
		int hovered = at(mouseX, mouseY);
		int rowsY = y + UiStyle.HEADER;
		for (int row = 0; row < visibleRows(); row++) {
			int i = row + scroll;
			if (i >= words.size()) {
				break;
			}
			int ry = rowsY + row * ROW_H;
			UiStyle.row(graphics, x + 6, ry, x + w - 8, ry + ROW_H - 2, i, i == hovered);
			UiStyle.line(graphics, font, name(words.get(i)), x + 10, ry + 4, w - 24, UiStyle.TEXT);
		}
		UiStyle.scrollbar(graphics, x + w - 4, rowsY, visibleRows() * ROW_H, visibleRows(), words.size(), scroll);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
