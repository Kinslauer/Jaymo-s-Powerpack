package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.WorldTiers;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.82) The world tier screen ({@link WorldTiers}), opened from the skill screen (K) or its own key (unbound at
 * first): every tier down the left, yours marked, the locked ones dim; on the right the one you point at or picked:
 * what it does to foes and gives you, what opens it and how far along you are, and a box with its story. Enter puts
 * you in it.
 */
final class TierScreen extends Screen {
	static final int MAX_W = 400, MAX_H = 236, LIST_W = 112, LIST_ROW = 19, HEADER = 34, FOOTER = 24;
	/** The screen's stripe: the Kingdoms gold. */
	static final int ACCENT = Ui.GOLD;

	private static @Nullable KeyMapping key;

	private final @Nullable Screen parent;
	private WorldTiers.@Nullable Tier picked;
	private @Nullable Button enter;

	TierScreen(@Nullable Screen parent) {
		super(Component.translatable("gui.jaymoskingdoms.tier.title"));
		this.parent = parent;
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("world_tier"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.world_tier", InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new TierScreen(null));
				}
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return Math.min(MAX_H, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int paneX() {
		return left() + LIST_W;
	}

	private int paneW() {
		return panelW() - LIST_W;
	}

	private int rowY(int index) {
		return top() + HEADER + index * LIST_ROW;
	}

	private WorldTiers.Tier current() {
		return this.minecraft.player == null ? WorldTiers.Tier.HEARTH : WorldTiers.of(this.minecraft.player);
	}

	private boolean open(WorldTiers.Tier tier) {
		return this.minecraft.player != null && WorldTiers.open(this.minecraft.player, tier);
	}

	@Override
	protected void init() {
		if (this.picked == null) {
			this.picked = current();
		}
		int y = top() + panelH() - FOOTER + 4;
		int right = paneX() + paneW() - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 56, y, 56, 16).build());
		this.enter = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.tier.enter_plain"), b -> {
			if (this.picked != null && this.picked != current() && open(this.picked)) {
				ClientPlayNetworking.send(new WorldTiers.Choose(this.picked.ordinal()));
			}
		}).bounds(right - 60 - 130, y, 130, 16).build());
	}

	private WorldTiers.@Nullable Tier rowAt(int mouseX, int mouseY) {
		WorldTiers.Tier[] all = WorldTiers.Tier.values();
		for (int i = 0; i < all.length; i++) {
			if (Ui.over(mouseX, mouseY, left() + 4, rowY(i), LIST_W - 8, LIST_ROW - 2)) {
				return all[i];
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		WorldTiers.Tier row = rowAt((int)event.x(), (int)event.y());
		if (row != null) {
			this.picked = row;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW(), h = panelH();
		WorldTiers.Tier current = current();
		Ui.panel(graphics, x, y, w, h, current.color);
		list(graphics, mouseX, mouseY, current);
		WorldTiers.Tier hover = rowAt(mouseX, mouseY);
		WorldTiers.Tier tier = hover != null ? hover : this.picked != null ? this.picked : current;
		boolean open = open(tier);
		int px = paneX(), pw = paneW();

		// the header: its icon, its name, and whether it's yours, open or locked
		graphics.item(new ItemStack(tier.icon), px + 8, y + 8);
		Ui.line(graphics, this.font, tier.title(), px + 28, y + 7, pw - 36, open ? tier.color : Ui.DIM);
		Ui.line(graphics, this.font, Component.translatable(tier == current ? "gui.jaymoskingdoms.tier.yours"
			: open ? "gui.jaymoskingdoms.tier.open" : "gui.jaymoskingdoms.tier.locked"), px + 28, y + 18, pw - 36, tier == current ? Ui.GOOD : open ? Ui.DIM : Ui.BAD);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);

		// what it does (foes, then you), what opens it, how far along you are
		List<Component> rows = new ArrayList<>(tier.effects());
		int needRow = rows.size();
		rows.add(Component.translatable("gui.jaymoskingdoms.tier.need", tier.requirement()));
		if (this.minecraft.player != null && !open) {
			WorldTiers.Progress p = WorldTiers.progress(this.minecraft.player);
			rows.add(Component.translatable("gui.jaymoskingdoms.tier.progress", p.foes()));
		}
		int ry = y + HEADER, bottom = y + h - FOOTER - 2;
		int textW = pw - 20;
		List<Component> story = List.of(Component.translatable("tier.jaymoskingdoms." + tier.key() + ".desc"));
		int storyLines = Math.min(3, Ui.detailLines(this.font, pw, null, story));
		int rowsBottom = bottom - 6 - storyLines * Ui.LINE;
		// (UI text pass) rows past the room scroll slowly through it and back instead of being left off
		int rowsTop = ry, rowsH = 0;
		for (Component row : rows) {
			rowsH += this.font.split(row, textW).size() * Ui.LINE + 4;
		}
		int view = Math.max(0, rowsBottom - rowsTop), over = Math.max(0, rowsH - view);
		int shift = over > 0 ? Ui.slide(over, Ui.SCROLL_MS_PER_LINE / Ui.LINE, Ui.SCROLL_PAUSE_MS) : 0;
		if (over > 0) {
			graphics.enableScissor(px, rowsTop, px + pw, rowsBottom);
		}
		ry -= shift;
		for (int i = 0; i < rows.size(); i++) {
			List<FormattedCharSequence> split = this.font.split(rows.get(i), textW);
			int rh = split.size() * Ui.LINE + 4;
			Ui.row(graphics, px + 6, ry, px + pw - 6 - (over > 0 ? 4 : 0), ry + rh - 1, i, false);
			int color = i >= needRow ? (open ? Ui.GOOD : Ui.DIM) : Ui.TEXT;
			for (int l = 0; l < split.size(); l++) {
				graphics.text(this.font, split.get(l), px + 10, ry + 2 + l * Ui.LINE, color);
			}
			ry += rh;
		}
		if (over > 0) {
			graphics.disableScissor();
			Ui.scrollbar(graphics, px + pw - 7, rowsTop, view - 1, view, rowsH, shift);
		}
		Ui.detail(graphics, this.font, px, rowsBottom, pw, storyLines, null, tier.color, story);

		if (this.enter != null) {
			WorldTiers.Tier pick = this.picked != null ? this.picked : current;
			this.enter.setMessage(Component.translatable("gui.jaymoskingdoms.tier.enter", pick.title()));
			this.enter.active = pick != current && open(pick);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Every tier down the left: yours marked, the picked one lit, the locked ones dim. */
	private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY, WorldTiers.Tier current) {
		int x = left(), y = top();
		Ui.line(graphics, this.font, this.title, x + 8, y + 8, LIST_W - 12, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.tier.sub"), x + 8, y + 19, LIST_W - 12, Ui.DIM);
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + panelH() - 4, Ui.ROW);
		WorldTiers.Tier[] all = WorldTiers.Tier.values();
		WorldTiers.Tier hover = rowAt(mouseX, mouseY);
		for (int i = 0; i < all.length; i++) {
			WorldTiers.Tier tier = all[i];
			int ry = rowY(i);
			boolean picked = tier == this.picked;
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + LIST_ROW - 2,
				picked ? Ui.ROW_PICKED : tier == hover ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			if (tier == current) {
				graphics.fill(x + 4, ry, x + 6, ry + LIST_ROW - 2, tier.color);
			}
			graphics.item(new ItemStack(tier.icon), x + 8, ry + 1);
			boolean open = open(tier);
			Ui.line(graphics, this.font, tier.title(), x + 27, ry + 5, LIST_W - 34,
				!open ? Ui.HINT : tier == current || picked ? tier.color : tier == hover ? Ui.TEXT : Ui.DIM);
		}
	}
}
