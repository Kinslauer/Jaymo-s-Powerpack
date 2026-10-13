package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Skills;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * (0.5.50) The skill tree ({@link Skills}), on the {@code K} key: a tab per pillar, its level and experience, and its
 * perks as a tree; press a perk to spend points on it. The server checks every spend and syncs the result back.
 */
final class SkillScreen extends Screen {
	static final int PANEL_W = 320, PANEL_H = 250, PAD = 8, TAB_W = 98, PERK_W = 96, PERK_H = 20, TIER_H = 34, COLUMN_GAP = 104;
	static final int COLOR_PANEL = 0xE0201812, COLOR_EDGE = 0xFFB08A3C, COLOR_TEXT = 0xFFF2E6CC, COLOR_DIM = 0xFFB8AA90,
		COLOR_GOLD = 0xFFFFC21A, COLOR_BAR = 0xFF3A3024, COLOR_FILL = 0xFF66C266, COLOR_LINE_ON = 0xFFB08A3C, COLOR_LINE_OFF = 0xFF54483A;

	private static KeyMapping key;
	/** The tab last looked at (Stealth first: it's the one built). */
	private static Skills.Pillar pillar = Skills.Pillar.STEALTH;

	/** What the widgets were built for; when the server's answer changes it, they're built again. */
	private Skills.Progress shown = Skills.Progress.EMPTY;

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

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	/** Where perk {@code perk}'s button sits. */
	private int perkX(Skills.Perk perk) {
		return this.width / 2 + (perk.column - 1) * COLUMN_GAP - PERK_W / 2;
	}

	private int perkY(Skills.Perk perk) {
		return top() + 76 + perk.tier * TIER_H;
	}

	@Override
	protected void init() {
		this.shown = progress();
		int x = left() + (PANEL_W - TAB_W * 3 - 8) / 2, y = top() + 6;
		for (Skills.Pillar p : Skills.Pillar.values()) {
			Button tab = Button.builder(Component.translatable("skill.jaymoskingdoms." + p.key()), b -> {
				pillar = p;
				rebuildWidgets();
			}).bounds(x, y, TAB_W, 18).build();
			tab.active = p != pillar;
			addRenderableWidget(tab);
			x += TAB_W + 4;
		}
		if (pillar.open) {
			for (Skills.Perk perk : Skills.Perk.values()) {
				if (perk.pillar != pillar) {
					continue;
				}
				String blocked = shown.blocked(perk);
				Button button = Button.builder(perkLabel(perk), b -> ClientPlayNetworking.send(new Skills.Spend(perk.ordinal())))
					.bounds(perkX(perk), perkY(perk), PERK_W, PERK_H).build();
				button.active = blocked == null;
				button.setTooltip(Tooltip.create(tooltip(perk, blocked)));
				addRenderableWidget(button);
			}
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(this.width / 2 - 40, top() + PANEL_H - 24, 80, 18).build());
	}

	private Component perkLabel(Skills.Perk perk) {
		int rank = shown.rank(perk);
		MutableComponent name = Component.translatable("skill.jaymoskingdoms.perk." + perk.key());
		return perk.maxRank == 1 ? name.withStyle(rank > 0 ? ChatFormatting.GOLD : ChatFormatting.RESET)
			: name.append(" " + rank + "/" + perk.maxRank).withStyle(rank >= perk.maxRank ? ChatFormatting.GOLD : ChatFormatting.RESET);
	}

	private Component tooltip(Skills.Perk perk, String blocked) {
		MutableComponent text = Component.translatable("skill.jaymoskingdoms.perk." + perk.key()).withStyle(ChatFormatting.GOLD)
			.append(Component.literal("\n"))
			.append(Component.translatable("skill.jaymoskingdoms.perk." + perk.key() + ".desc").withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("\n"));
		text.append(Component.translatable("gui.jaymoskingdoms.skills.cost", perk.cost).withStyle(ChatFormatting.GRAY));
		if (perk.minLevel > 0) {
			text.append(Component.literal("  "));
			text.append(Component.translatable("gui.jaymoskingdoms.skills.needs_level", perk.minLevel).withStyle(ChatFormatting.GRAY));
		}
		if (blocked != null && !blocked.endsWith(".max")) {
			text.append(Component.literal("\n"));
			text.append(Component.translatable(blocked).withStyle(ChatFormatting.RED));
		}
		return text;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Skills.Progress now = progress();
		if (!now.equals(this.shown)) {
			rebuildWidgets();
		}
		int x = left(), y = top();
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_PANEL);
		edge(graphics, x, y, PANEL_W, PANEL_H, COLOR_EDGE);
		int cy = y + 30;
		Component name = Component.translatable("skill.jaymoskingdoms." + pillar.key());
		if (!pillar.open) {
			graphics.centeredText(this.font, name, this.width / 2, cy, COLOR_GOLD);
			cy += 16;
			for (FormattedCharSequence line : this.font.split(Component.translatable("gui.jaymoskingdoms.skills.plan." + pillar.key()), PANEL_W - PAD * 4)) {
				graphics.centeredText(this.font, line, this.width / 2, cy, COLOR_DIM);
				cy += 10;
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		int level = shown.level(pillar), xp = shown.xp(pillar);
		graphics.centeredText(this.font, Component.translatable("gui.jaymoskingdoms.skills.level", name, level), this.width / 2, cy, COLOR_GOLD);
		cy += 12;
		int barX = x + PAD * 4, barW = PANEL_W - PAD * 8;
		graphics.fill(barX, cy, barX + barW, cy + 5, COLOR_BAR);
		Component under;
		if (level >= Skills.MAX_LEVEL) {
			graphics.fill(barX, cy, barX + barW, cy + 5, COLOR_GOLD);
			under = Component.translatable("gui.jaymoskingdoms.skills.mastered");
		} else {
			int into = xp - Skills.total(level), need = Skills.toNext(level);
			graphics.fill(barX, cy, barX + barW * into / need, cy + 5, COLOR_FILL);
			under = Component.translatable("gui.jaymoskingdoms.skills.xp", into, need);
		}
		cy += 8;
		int points = shown.points(pillar);
		Component pts = Component.translatable("gui.jaymoskingdoms.skills.points", points);
		graphics.text(this.font, under, barX, cy, COLOR_DIM);
		graphics.text(this.font, pts, barX + barW - this.font.width(pts), cy, points > 0 ? COLOR_FILL : COLOR_DIM);
		// (the lines from each perk to the ones it opens)
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (perk.pillar != pillar) {
				continue;
			}
			for (Skills.Perk need : perk.requires) {
				int color = shown.rank(need) > 0 ? COLOR_LINE_ON : COLOR_LINE_OFF;
				int fromX = perkX(need) + PERK_W / 2, fromY = perkY(need) + PERK_H, toX = perkX(perk) + PERK_W / 2, toY = perkY(perk);
				int midY = (fromY + toY) / 2;
				graphics.fill(fromX - 1, fromY, fromX + 1, midY + 1, color);
				graphics.fill(Math.min(fromX, toX) - 1, midY - 1, Math.max(fromX, toX) + 1, midY + 1, color);
				graphics.fill(toX - 1, midY, toX + 1, toY, color);
			}
		}
		graphics.centeredText(this.font, Component.translatable("gui.jaymoskingdoms.skills.hint." + pillar.key()), this.width / 2, y + PANEL_H - 38, COLOR_DIM);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private static void edge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}
}
