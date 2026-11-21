package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Skills;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The K screen's class tab ({@link ClassTrees}): your class's level, its experience and its points on the left, its tree
 * as rows of tiles on the right (a perk's ties run up to what it needs; a crossroads' perks are joined by a dashed line).
 * Click a tile to see it, click it again to learn a rank. With a second class, the foot's button shows its tree instead.
 */
final class ClassTreeTab {
	static final int TILE = 24, GAP_X = 28, GAP_Y = 14, SIDE = 150;
	/** Which of the player's classes the tab shows: 0 the first, 1 the second. */
	static int which;
	private static @Nullable String selected;
	private static int scroll;

	private ClassTreeTab() {
	}

	/** The class shown, or null with none (no class yet). */
	static @Nullable PlayerClass shown(@Nullable Player player) {
		if (player == null) {
			return null;
		}
		List<PlayerClass> both = Classes.both(player);
		if (both.isEmpty()) {
			return null;
		}
		return both.get(Math.min(which, both.size() - 1));
	}

	private static int colour(PlayerClass cls) {
		return 0xFF000000 | ClassTrees.colour(cls);
	}

	/** Where a node's tile sits, in the tree's area that starts at {@code left},{@code top}. */
	private static int tileX(ClassTrees.Node n, int left, int width) {
		int span = (ClassTrees.COLUMNS - 1) * (TILE + GAP_X);
		int x0 = left + Math.max(8, (width - span - TILE) / 2);
		return x0 + n.col() * (TILE + GAP_X);
	}

	private static int tileY(ClassTrees.Node n, int top) {
		return top + 12 + n.row() * (TILE + GAP_Y) - scroll;
	}

	static void render(GuiGraphicsExtractor g, Font font, Player player, int left, int top, int right, int bottom, int mouseX, int mouseY) {
		PlayerClass cls = shown(player);
		Skills.Progress p = Skills.of(player);
		int sideR = left + SIDE;
		Ui.panel(g, left + 4, top + 4, SIDE - 8, bottom - top - 8, cls == null ? Ui.GOLD : colour(cls));
		if (cls == null) {
			Ui.line(g, font, Component.translatable("gui.jaymoskingdoms.classtree.none"), left + 10, top + 10, SIDE - 20, Ui.GOLD);
			Ui.wrapped(g, font, Component.translatable("gui.jaymoskingdoms.classtree.none.help"), sideR + 12, top + 12, right - sideR - 24, 30, 10, Ui.TEXT, false);
			return;
		}
		int level = ClassTrees.level(p, cls), xp = ClassTrees.xp(p, cls), points = ClassTrees.points(p, cls);
		Component name = Component.translatable("class.jaymoskingdoms." + cls.key());
		Ui.line(g, font, Component.translatable("gui.jaymoskingdoms.classtree.level", name, level), left + 10, top + 10, SIDE - 20, colour(cls));
		if (level < ClassTrees.MAX_LEVEL) {
			int from = ClassTrees.total(level), to = ClassTrees.total(level + 1);
			Ui.bar(g, left + 10, top + 22, SIDE - 20, 4, (xp - from) / (float) Math.max(1, to - from), colour(cls));
			Ui.line(g, font, Component.translatable("gui.jaymoskingdoms.classtree.xp", xp - from, to - from), left + 10, top + 29, SIDE - 20, Ui.DIM);
		} else {
			Ui.line(g, font, Component.translatable("gui.jaymoskingdoms.classtree.top"), left + 10, top + 29, SIDE - 20, Ui.GOLD);
		}
		Ui.line(g, font, Component.translatable("gui.jaymoskingdoms.classtree.points", points), left + 10, top + 41, SIDE - 20, points > 0 ? Ui.GOOD : Ui.DIM);
		if (Classes.second(player) == cls) {
			Ui.wrapped(g, font, Component.translatable("gui.jaymoskingdoms.classtree.second"), left + 10, top + 54, SIDE - 20, 3, 10, Ui.DIM, false);
		}
		List<ClassTrees.Node> tree = ClassTrees.tree(cls);
		int areaL = sideR, areaW = right - sideR;
		ClassTrees.Node hovered = null;
		if (tree.isEmpty()) {
			Ui.wrapped(g, font, Component.translatable("gui.jaymoskingdoms.classtree.empty"), areaL + 12, top + 12, areaW - 24, 4, 10, Ui.DIM, false);
		}
		g.enableScissor(areaL, top, right, bottom);
		// the ties: up to what each needs, and the crossroads, dashed across
		for (ClassTrees.Node n : tree) {
			int x = tileX(n, areaL, areaW) + TILE / 2, y = tileY(n, top) + TILE / 2;
			for (String need : n.requires()) {
				ClassTrees.Node m = ClassTrees.node(need);
				if (m != null && m.of(cls)) {
					int mx = tileX(m, areaL, areaW) + TILE / 2, my = tileY(m, top) + TILE / 2;
					tie(g, mx, my, x, y, ClassTrees.rank(p, m) > 0 ? Ui.GOLD : 0xFF505058, false);
				}
			}
			for (String f : n.forks()) {
				ClassTrees.Node m = ClassTrees.node(f);
				if (m != null && m.of(cls) && f.compareTo(n.key()) > 0) {
					tie(g, tileX(m, areaL, areaW) + TILE / 2, tileY(m, top) + TILE / 2, x, y, 0xFFB0A070, true);
				}
			}
		}
		for (ClassTrees.Node n : tree) {
			int x = tileX(n, areaL, areaW), y = tileY(n, top);
			int rank = ClassTrees.rank(p, n);
			String why = ClassTrees.blocked(player, n);
			boolean over = Ui.over(mouseX, mouseY, x, y, TILE, TILE) && mouseY >= top && mouseY < bottom;
			if (over) {
				hovered = n;
			}
			int rim = n.key().equals(selected) ? Ui.GOLD : rank > 0 ? colour(cls) : why == null ? Ui.GOOD : 0xFF505058;
			g.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, 0xFF000000);
			g.fill(x, y, x + TILE, y + TILE, rim);
			g.fill(x + 2, y + 2, x + TILE - 2, y + TILE - 2, rank > 0 ? darker(colour(cls)) : over ? 0xFF2A2A34 : 0xFF16161C);
			String label = rank > 0 && n.maxRank() > 1 ? rank + "/" + n.maxRank() : n.maxRank() > 1 ? "0/" + n.maxRank() : rank > 0 ? "✓" : "";
			if (!label.isEmpty()) {
				g.text(font, label, x + (TILE - font.width(label)) / 2, y + 8, rank > 0 ? Ui.GOLD : Ui.DIM);
			}
			Component nm = Component.translatable(n.nameKey());
			String cut = Ui.cut(font, nm.getString(), TILE + GAP_X - 2);
			g.text(font, cut, x + TILE / 2 - font.width(cut) / 2, y + TILE + 2, rank > 0 ? Ui.TEXT : Ui.DIM);
		}
		g.disableScissor();
		ClassTrees.Node show = hovered != null ? hovered : selected == null ? null : ClassTrees.node(selected);
		if (show != null) {
			detail(g, font, player, show, cls, left + 10, top + 90, SIDE - 20, bottom - top - 100);
		}
	}

	/** The perk shown on the left: its name, what it does, its cost and level, and why it can't be learned yet. */
	private static void detail(GuiGraphicsExtractor g, Font font, Player player, ClassTrees.Node n, PlayerClass cls, int x, int y, int w, int h) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(n.descKey()).withColor(Ui.TEXT));
		lines.add(Component.translatable("gui.jaymoskingdoms.classtree.rank", ClassTrees.rank(Skills.of(player), n), n.maxRank()).withColor(Ui.DIM));
		MutableComponent cost = Component.translatable("gui.jaymoskingdoms.skills.cost", n.cost());
		if (n.minLevel() > 0) {
			cost = Component.empty().append(cost).append("  ·  ").append(Component.translatable("gui.jaymoskingdoms.skills.needs_level", n.minLevel()));
		}
		lines.add(cost.withColor(Ui.DIM));
		if (!n.forks().isEmpty()) {
			lines.add(Component.translatable("gui.jaymoskingdoms.classtree.fork", names(n.forks())).withColor(0xFFF0D28C));
		}
		if (n.classes().size() > 1) {
			MutableComponent others = Component.empty();
			boolean first = true;
			for (PlayerClass c : n.classes()) {
				if (c != cls) {
					others.append(first ? Component.empty() : Component.literal(", ")).append(Component.translatable("class.jaymoskingdoms." + c.key()));
					first = false;
				}
			}
			lines.add(Component.translatable("gui.jaymoskingdoms.classtree.shared", others).withColor(Ui.DIM));
		}
		String why = ClassTrees.blocked(player, n);
		lines.add(why == null ? Component.translatable("gui.jaymoskingdoms.skills.learn").withColor(Ui.GOOD)
			: Component.translatable(why).withColor(why.endsWith(".max") ? Ui.GOLD : Ui.BAD));
		Ui.detail(g, font, x, y, w, Math.max(1, h / Ui.LINE), Component.translatable(n.nameKey()), colour(cls), lines);
	}

	private static Component names(List<String> keys) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < keys.size(); i++) {
			ClassTrees.Node n = ClassTrees.node(keys.get(i));
			if (i > 0) {
				out.append(", ");
			}
			out.append(n == null ? Component.literal(keys.get(i)) : Component.translatable(n.nameKey()));
		}
		return out;
	}

	/** A click on the tab: a tile is picked, a picked one learned. True if it was used. */
	static boolean click(Player player, double mouseX, double mouseY, int left, int top, int right, int bottom) {
		PlayerClass cls = shown(player);
		if (cls == null || mouseX < left + SIDE || mouseY < top || mouseY >= bottom) {
			return false;
		}
		int areaL = left + SIDE, areaW = right - areaL;
		for (ClassTrees.Node n : ClassTrees.tree(cls)) {
			if (Ui.over((int) mouseX, (int) mouseY, tileX(n, areaL, areaW), tileY(n, top), TILE, TILE)) {
				if (n.key().equals(selected)) {
					if (ClassTrees.blocked(player, n) == null) {
						ClientPlayNetworking.send(new ClassTrees.Spend(n.key()));
						Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					}
				} else {
					selected = n.key();
				}
				return true;
			}
		}
		selected = null;
		return false;
	}

	/** Learns the picked perk (Enter). */
	static void learnSelected() {
		Player player = Minecraft.getInstance().player;
		ClassTrees.Node n = selected == null ? null : ClassTrees.node(selected);
		if (player != null && n != null && ClassTrees.blocked(player, n) == null) {
			ClientPlayNetworking.send(new ClassTrees.Spend(n.key()));
		}
	}

	static void scroll(Player player, double by, int height) {
		PlayerClass cls = shown(player);
		int rows = 0;
		if (cls != null) {
			for (ClassTrees.Node n : ClassTrees.tree(cls)) {
				rows = Math.max(rows, n.row() + 1);
			}
		}
		int most = Math.max(0, 24 + rows * (TILE + GAP_Y) - height);
		scroll = Math.clamp(scroll - (int) Math.signum(by) * (TILE + GAP_Y), 0, most);
	}

	static void flip() {
		which = 1 - which;
		selected = null;
		scroll = 0;
	}

	private static int darker(int colour) {
		int r = (colour >> 16 & 0xFF) * 2 / 5, gr = (colour >> 8 & 0xFF) * 2 / 5, b = (colour & 0xFF) * 2 / 5;
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	/** A tie between two tiles' middles: straight down then across, as in an outline; dashed for a crossroads. */
	private static void tie(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int colour, boolean dashed) {
		if (dashed) {
			int a = Math.min(x0, x1), b = Math.max(x0, x1), y = (y0 + y1) / 2;
			for (int x = a; x < b; x += 6) {
				g.fill(x, y, Math.min(b, x + 3), y + 1, colour);
			}
			return;
		}
		int midY = (y0 + y1) / 2;
		g.fill(x0, Math.min(y0, midY), x0 + 1, Math.max(y0, midY) + 1, colour);
		g.fill(Math.min(x0, x1), midY, Math.max(x0, x1) + 1, midY + 1, colour);
		g.fill(x1, Math.min(midY, y1), x1 + 1, Math.max(midY, y1) + 1, colour);
	}
}
