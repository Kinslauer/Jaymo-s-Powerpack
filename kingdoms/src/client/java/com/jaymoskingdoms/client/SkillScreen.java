package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.52) The skill tree ({@link Skills}), on the {@code K} key. (0.7.40) Restyled like the H screen: a header with the
 * pillar's level, its experience bar and the points to spend; a tab per pillar; its perks as a tree of flat tiles (press
 * one to spend points on it); and a box at the bottom that shows the hovered perk in full, or else what the pillar
 * levels on, your class and your alignment ({@link Renown}). The server checks every spend and syncs the result back.
 */
final class SkillScreen extends Screen {
	static final int PANEL_W = 340, PANEL_H = 230, HEADER = 34, TAB_W = 98, TAB_H = 16, PERK_W = 98, PERK_H = 18, TIER_H = 26,
		COLUMN_GAP = 106, TREE_Y = 56, DETAIL_Y = 156, DETAIL_LINES = 5, FOOTER = 22, XP_BAR_W = 120;
	static final int COLOR_LINE_OFF = 0xFF3A3A44;

	private static KeyMapping key;
	/** The tab last looked at (Stealth first: it's the one built). */
	private static Skills.Pillar pillar = Skills.Pillar.STEALTH;
	/** (0.6.5) The page of the pillar's tree shown: 0, or 1 for the tiers past the first page (Strength's Combat Arts). */
	private static int page;

	SkillScreen() {
		super(Component.translatable("gui.jaymoskingdoms.skills.title"));
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("skills"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.skills", InputConstants.KEY_K, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new SkillScreen());
				}
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Skills.Progress progress() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? Skills.Progress.EMPTY : Skills.of(minecraft.player);
	}

	static int accent(Skills.Pillar pillar) {
		return switch (pillar) {
			case SURVIVAL -> Ui.SURVIVAL;
			case STEALTH -> Ui.STEALTH;
			case STRENGTH -> Ui.STRENGTH;
		};
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	/** Where perk {@code perk}'s tile sits. */
	private int perkX(Skills.Perk perk) {
		return this.width / 2 + (perk.column - 1) * COLUMN_GAP - PERK_W / 2;
	}

	private int perkY(Skills.Perk perk) {
		return top() + TREE_Y + (perk.tier % Skills.Perk.PAGE_TIERS) * TIER_H;
	}

	private static boolean onPage(Skills.Perk perk) {
		return perk.pillar == pillar && perk.tier / Skills.Perk.PAGE_TIERS == page;
	}

	/** Whether {@code pillar}'s tree runs onto a second page. */
	private static boolean twoPages() {
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (perk.pillar == pillar && perk.tier >= Skills.Perk.PAGE_TIERS) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void init() {
		int x = left() + (panelW() - TAB_W * 3 - 8) / 2, y = top() + HEADER + 2;
		for (Skills.Pillar p : Skills.Pillar.values()) {
			Button tab = Button.builder(Component.translatable("skill.jaymoskingdoms." + p.key()), b -> {
				pillar = p;
				page = 0;
				rebuildWidgets();
			}).bounds(x, y, TAB_W, TAB_H).build();
			tab.active = p != pillar;
			addRenderableWidget(tab);
			x += TAB_W + 4;
		}
		int fy = top() + PANEL_H - FOOTER + 3;
		int right = left() + panelW() - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
		if (!twoPages()) {
			page = 0;
		} else {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.skills.page." + pillar.key() + "." + (1 - page)), b -> {
				page = 1 - page;
				rebuildWidgets();
			}).bounds(left() + 6, fy, 92, 16).build());
		}
	}

	private Skills.@Nullable Perk perkAt(double mouseX, double mouseY) {
		if (!pillar.open) {
			return null;
		}
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (onPage(perk) && Ui.over((int)mouseX, (int)mouseY, perkX(perk), perkY(perk), PERK_W, PERK_H)) {
				return perk;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Skills.Perk perk = perkAt(event.x(), event.y());
		if (perk != null) {
			if (progress().blocked(perk) == null) {
				ClientPlayNetworking.send(new Skills.Spend(perk.ordinal()));
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Skills.Progress shown = progress();
		int x = left(), y = top(), w = panelW();
		int accent = accent(pillar);
		Ui.panel(graphics, x, y, w, PANEL_H, accent);
		header(graphics, shown, accent);

		Skills.Perk hovered = perkAt(mouseX, mouseY);
		if (pillar.open) {
			tree(graphics, shown, accent, hovered);
		}

		// the detail box: the hovered perk in full, or what the pillar is about, your class and your alignment
		Component name = Component.empty();
		int nameColor = accent;
		List<Component> text = new ArrayList<>();
		if (hovered != null) {
			int rank = shown.rank(hovered);
			name = Component.translatable("skill.jaymoskingdoms.perk." + hovered.key()).append("  " + rank + "/" + hovered.maxRank);
			nameColor = rank > 0 ? accent : Ui.TEXT;
			text.add(Component.translatable("skill.jaymoskingdoms.perk." + hovered.key() + ".desc"));
			Component cost = Component.translatable("gui.jaymoskingdoms.skills.cost", hovered.cost);
			if (hovered.minLevel > 0) {
				cost = Component.empty().append(cost).append("  ·  ").append(Component.translatable("gui.jaymoskingdoms.skills.needs_level", hovered.minLevel));
			}
			text.add(cost.copy().withColor(Ui.DIM));
			String blocked = shown.blocked(hovered);
			if (blocked == null) {
				text.add(Component.translatable("gui.jaymoskingdoms.skills.learn").withColor(Ui.GOOD));
			} else {
				text.add(Component.translatable(blocked).withColor(blocked.endsWith(".max") ? accent : Ui.BAD));
			}
		} else if (!pillar.open) {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.plan." + pillar.key()));
		} else {
			text.add(Component.translatable("gui.jaymoskingdoms.skills.hint." + pillar.key() + (page > 0 ? ".1" : "")).withColor(Ui.DIM));
			Classes.PlayerClass cls = this.minecraft.player == null ? null : Classes.classOf(this.minecraft.player);
			text.add(cls == null ? Component.translatable("gui.jaymoskingdoms.skills.class.none").withColor(Ui.DIM)
				: Component.translatable("gui.jaymoskingdoms.skills.class", Component.translatable("class.jaymoskingdoms." + cls.key())).withColor(Ui.GOLD));
			// (your alignment: the pillar you've made your own, as others see it over your head)
			Skills.Pillar aligned = Renown.alignment(shown);
			text.add(aligned == null ? Component.translatable("gui.jaymoskingdoms.skills.alignment.none", Renown.ALIGN_LEVEL).withColor(Ui.DIM)
				: Component.translatable("gui.jaymoskingdoms.skills.alignment", Component.translatable("title.jaymoskingdoms." + aligned.key()
					+ (shown.level(aligned) >= aligned.maxLevel ? ".master" : ""))).withColor(accent(aligned)));
			text.add(Component.translatable("gui.jaymoskingdoms.skills.hover").withColor(Ui.HINT));
		}
		Ui.detail(graphics, this.font, x, y + DETAIL_Y, w, DETAIL_LINES, name, nameColor, text);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The pillar (and page) and its level on the left; the points to spend, and the experience bar, on the right. */
	private void header(GuiGraphicsExtractor graphics, Skills.Progress shown, int accent) {
		int x = left(), y = top(), w = panelW();
		Component name = Component.translatable("skill.jaymoskingdoms." + pillar.key());
		if (page > 0) {
			name = Component.translatable("gui.jaymoskingdoms.skills.page." + pillar.key() + ".1.title");
		}
		int textW = w - 20 - XP_BAR_W - 12;
		if (!pillar.open) {
			Ui.line(graphics, this.font, name, x + 9, y + 8, textW, accent);
			Ui.line(graphics, this.font, this.title, x + 9, y + 19, textW, Ui.DIM);
			Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
			return;
		}
		int level = shown.level(pillar), xp = shown.xp(pillar);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.level", name, level), x + 9, y + 8, textW, accent);
		int points = shown.points(pillar);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.points", points), x + 9, y + 19, textW,
			points > 0 ? Ui.GOOD : Ui.DIM);
		int bx = x + w - XP_BAR_W - 9;
		if (level >= pillar.maxLevel) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.mastered"), bx, y + 8, XP_BAR_W, accent);
			Ui.bar(graphics, bx, y + 20, XP_BAR_W, 3, 1.0F, accent);
		} else {
			int into = xp - Skills.total(level), need = Skills.toNext(level);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.skills.xp", into, need), bx, y + 8, XP_BAR_W, Ui.TEXT);
			Ui.bar(graphics, bx, y + 20, XP_BAR_W, 3, into / (float)Math.max(1, need), accent);
		}
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
	}

	/** The lines from each perk to the ones it opens, then the perks' tiles, on this page. */
	private void tree(GuiGraphicsExtractor graphics, Skills.Progress shown, int accent, Skills.@Nullable Perk hovered) {
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (!onPage(perk)) {
				continue;
			}
			for (Skills.Perk need : perk.requires) {
				if (!onPage(need)) {
					continue;
				}
				int color = shown.rank(need) > 0 ? accent : COLOR_LINE_OFF;
				int fromX = perkX(need) + PERK_W / 2, fromY = perkY(need) + PERK_H, toX = perkX(perk) + PERK_W / 2, toY = perkY(perk);
				int midY = (fromY + toY) / 2;
				graphics.fill(fromX - 1, fromY, fromX + 1, midY + 1, color);
				graphics.fill(Math.min(fromX, toX) - 1, midY - 1, Math.max(fromX, toX) + 1, midY + 1, color);
				graphics.fill(toX - 1, midY, toX + 1, toY, color);
			}
		}
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (!onPage(perk)) {
				continue;
			}
			int px = perkX(perk), py = perkY(perk);
			int rank = shown.rank(perk);
			String blocked = shown.blocked(perk);
			boolean open = blocked == null, full = rank >= perk.maxRank;
			graphics.fill(px, py, px + PERK_W, py + PERK_H, perk == hovered ? Ui.ROW_HOVER : 0xE0181820);
			graphics.fill(px, py, px + PERK_W, py + PERK_H, rank > 0 ? Ui.ROW : 0x00000000);
			if (rank > 0) {
				// learnt: the pillar's colour down its left
				graphics.fill(px, py, px + 2, py + PERK_H, accent);
			}
			if (open) {
				// can be learnt now: a green line under it
				graphics.fill(px, py + PERK_H - 1, px + PERK_W, py + PERK_H, Ui.GOOD);
			}
			String ranks = rank + "/" + perk.maxRank;
			int ranksW = this.font.width(ranks);
			int color = rank > 0 || open ? Ui.TEXT : Ui.DIM;
			Ui.line(graphics, this.font, Component.translatable("skill.jaymoskingdoms.perk." + perk.key()), px + 6, py + 5, PERK_W - ranksW - 14, color);
			graphics.text(this.font, ranks, px + PERK_W - ranksW - 5, py + 5, full ? accent : rank > 0 ? Ui.TEXT : Ui.HINT);
		}
	}
}
