package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MenuNet;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.649) The character menu's Faction tab (Jaymo's Powers' menu, {@link MenuCompat}): the realm you are sworn to, its
 * kind and overlord, your rank in it and how far your standing is from the next, its people and treasury, your patron,
 * and on the right how it stands with the realms it keeps relations with. Asks the server when it opens
 * ({@link MenuNet}); until the answer comes it says so.
 */
final class FactionScreen extends Screen {
	static final int PANEL_W = 320, PANEL_H = 200, HEADER = 34, ROW = 12, RELATION_ROW = 13;
	/** The left column's width (the rest is the relations). */
	static final int LEFT_W = 170;
	static final int ALLIED = Ui.GOOD, RIVAL = 0xFFE0C04A, WAR = Ui.BAD;

	/** The last answer (kept for the Map tab's pins too). */
	static MenuNet.@Nullable Info info;

	FactionScreen() {
		super(Component.translatable("menu.jaymoskingdoms.faction.title"));
	}

	/** Asks the server for the faction and the settlements you know. */
	static void ask() {
		if (ClientPlayNetworking.canSend(MenuNet.Ask.TYPE)) {
			ClientPlayNetworking.send(new MenuNet.Ask());
		}
	}

	@Override
	protected void init() {
		ask();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 40);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	private int row(GuiGraphicsExtractor graphics, int x, int y, int w, Component label, Component value, int color) {
		int labelW = Math.min(this.font.width(label), w / 2);
		Ui.line(graphics, this.font, label, x, y, labelW, Ui.DIM);
		Ui.lineRight(graphics, this.font, value, x + w, y, w - labelW - 6, color);
		return y + ROW;
	}

	private static int relationColor(String relation) {
		return switch (relation) {
			case "allied" -> ALLIED;
			case "rival" -> RIVAL;
			case "war" -> WAR;
			default -> Ui.DIM;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		int x = left(), y = top(), w = panelW();
		MenuNet.Info i = info;
		int accent = i != null && i.sworn() ? 0xFF000000 | i.color() : Ui.GOLD;
		Ui.panel(graphics, x, y, w, PANEL_H, accent);
		graphics.item(new ItemStack(Items.BELL), x + 8, y + 8);
		Component name = i == null ? this.title : i.sworn() ? Component.literal(i.name()) : Component.translatable("menu.jaymoskingdoms.faction.unsworn");
		Ui.line(graphics, this.font, name, x + 28, y + 7, w - 36, accent);
		Component sub = i == null ? Component.translatable("menu.jaymoskingdoms.faction.asking")
			: !i.sworn() ? Component.translatable("menu.jaymoskingdoms.faction.unsworn_sub")
			: i.overlord().isEmpty() ? Component.translatable(i.kind())
			: Component.translatable("menu.jaymoskingdoms.faction.vassal", Component.translatable(i.kind()), i.overlord());
		Ui.line(graphics, this.font, sub, x + 28, y + 18, w - 36, Ui.DIM);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
		int lw = Math.min(LEFT_W, w / 2 + 10) - 16;
		int lx = x + 10, ly = y + HEADER + 4;
		if (i != null && i.sworn()) {
			ly = row(graphics, lx, ly, lw, Component.translatable("menu.jaymoskingdoms.faction.rank"), Component.translatable(i.rank()), Ui.GOLD);
			if (i.next() > 0) {
				ly = row(graphics, lx, ly, lw, Component.translatable("menu.jaymoskingdoms.faction.standing"),
					Component.translatable("menu.jaymoskingdoms.faction.of", i.standing(), i.next()), Ui.TEXT);
				Ui.bar(graphics, lx, ly - 2, lw, 3, Math.min(1.0F, i.standing() / (float)i.next()), Ui.GOLD);
				ly += 4;
			} else {
				ly = row(graphics, lx, ly, lw, Component.translatable("menu.jaymoskingdoms.faction.standing"), Component.literal(Integer.toString(i.standing())),
					Ui.TEXT);
			}
			ly = row(graphics, lx, ly, lw, Component.translatable("menu.jaymoskingdoms.faction.members"), Component.literal(Integer.toString(i.members())), Ui.TEXT);
			ly = row(graphics, lx, ly, lw, Component.translatable("menu.jaymoskingdoms.faction.people"), Component.literal(Integer.toString(i.population())),
				Ui.TEXT);
			ly = row(graphics, lx, ly, lw, Component.translatable("menu.jaymoskingdoms.faction.treasury"),
				Component.translatable("menu.jaymoskingdoms.faction.emeralds", i.treasury()), Ui.TEXT);
		} else if (i != null) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("menu.jaymoskingdoms.faction.how"), lw)) {
				graphics.text(this.font, line, lx, ly, Ui.DIM, false);
				ly += Ui.LINE + 1;
			}
			ly += 4;
		}
		if (player != null) {
			Patron patron = Patrons.of(player);
			row(graphics, lx, ly + 4, lw, Component.translatable("menu.jaymoskingdoms.faction.patron"),
				patron == null ? Component.translatable("menu.jaymoskingdoms.faction.no_patron") : Component.translatable("patron.jaymoskingdoms." + patron.key),
				patron == null ? Ui.DIM : Ui.TEXT);
		}
		// relations
		int rx = x + lw + 26, rw = x + w - 10 - rx, ry = y + HEADER + 4;
		graphics.fill(rx - 8, y + HEADER, rx - 7, y + PANEL_H - 8, Ui.ROW);
		Ui.line(graphics, this.font, Component.translatable("menu.jaymoskingdoms.faction.relations"), rx, ry, rw, Ui.GOLD);
		ry += 12;
		if (i == null || !i.sworn() || i.ties().isEmpty()) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("menu.jaymoskingdoms.faction.no_relations"), rw)) {
				graphics.text(this.font, line, rx, ry, Ui.DIM, false);
				ry += Ui.LINE + 1;
			}
		} else {
			int n = 0;
			for (MenuNet.Tie t : i.ties()) {
				if (ry + RELATION_ROW > y + PANEL_H - 6) {
					break;
				}
				Ui.row(graphics, rx - 2, ry - 2, rx + rw, ry + RELATION_ROW - 3, n++, false);
				graphics.fill(rx, ry + 1, rx + 5, ry + 6, 0xFF000000 | t.color());
				Component rel = Component.translatable("relation.jaymoskingdoms." + t.relation());
				int relW = this.font.width(rel);
				Ui.line(graphics, this.font, Component.literal(t.name()), rx + 8, ry, rw - relW - 12, Ui.TEXT);
				graphics.text(this.font, rel, rx + rw - relW - 1, ry, relationColor(t.relation()), false);
				ry += RELATION_ROW;
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
