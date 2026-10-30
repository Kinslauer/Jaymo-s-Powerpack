package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Valor;
import com.jaymoskingdoms.ValorKings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.361) The Valor page, from Strength in the K screen: your Valor as a card. Its name, rank and the way to the next;
 * its origin and second quirk, its look and (once King's Valor shows it) its soul colour; five bars (Hardness, Edge, Reach,
 * Sight, Endurance); and the feats that grew it. Before it wakes: what it takes.
 */
final class ValorScreen extends Screen {
	static final int PANEL_W = 340, PANEL_H = 230, BAR_W = 90;
	static final int ACCENT = 0xFFC8A060;
	private final @Nullable Screen back;

	ValorScreen(@Nullable Screen back) {
		super(Component.translatable("gui.jaymoskingdoms.valor.title"));
		this.back = back;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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

	@Override
	protected void init() {
		int fy = top() + PANEL_H - 19, right = left() + panelW() - 6;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(right - 60, fy, 60, 16).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.back);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		Player player = this.minecraft.player;
		Valor.Data data = player == null ? null : Valor.of(player);
		int accent = data == null ? ACCENT : ARGB.opaque(data.lookKind().color == 0x0D0D12 ? 0x7A7A90 : brighten(data.lookKind().color));
		Ui.panel(graphics, x, y, w, PANEL_H, accent);
		if (data == null) {
			Ui.line(graphics, this.font, this.title, x + 9, y + 8, w - 18, ACCENT);
			int ly = y + 26;
			for (String k : new String[] {"asleep", "needs", "feats", "class"}) {
				for (var line : this.font.split(Component.translatable("gui.jaymoskingdoms.valor." + k, 5), w - 18)) {
					graphics.text(this.font, line, x + 9, ly, Ui.TEXT);
					ly += Ui.LINE + 1;
				}
				ly += 4;
			}
			super.extractRenderState(graphics, mouseX, mouseY, a);
			return;
		}
		// the header: its name, its rank and the way to the next
		int rank = data.rank();
		Ui.line(graphics, this.font, Component.literal(capital(data.name())), x + 9, y + 8, w - 150, accent);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.rank", Component.translatable("valor.jaymoskingdoms.rank." + rank)),
			x + 9, y + 19, w - 150, Ui.TEXT);
		int bx = x + w - 129;
		if (rank >= Valor.RANKS.length - 1) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.top"), bx, y + 8, 120, accent);
			Ui.bar(graphics, bx, y + 20, 120, 3, 1.0F, accent);
		} else {
			float from = Valor.RANKS[rank], to = Valor.RANKS[rank + 1];
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.exp", Math.round(data.exp() - from), Math.round(to - from)), bx, y + 8, 120,
				Ui.TEXT);
			Ui.bar(graphics, bx, y + 20, 120, 3, (data.exp() - from) / (to - from), accent);
		}
		Ui.rule(graphics, x + 6, x + w - 6, y + 30);
		// left: what it is
		int lx = x + 9, ly = y + 36, lw = w / 2 - 14;
		List<Component> left = new ArrayList<>();
		left.add(Component.translatable("gui.jaymoskingdoms.valor.origin", Component.translatable("valor.jaymoskingdoms.origin." + data.originKind().key()))
			.withColor(accent));
		left.add(Component.translatable("valor.jaymoskingdoms.origin." + data.originKind().key() + ".desc").withColor(Ui.DIM));
		Valor.Bent bent = data.bentKind();
		left.add(bent == null ? Component.translatable("gui.jaymoskingdoms.valor.bent.none").withColor(Ui.DIM)
			: Component.translatable("gui.jaymoskingdoms.valor.bent", Component.translatable("valor.jaymoskingdoms.bent." + bent.key())).withColor(accent));
		if (bent != null) {
			left.add(Component.translatable("valor.jaymoskingdoms.bent." + bent.key() + ".desc").withColor(Ui.DIM));
		}
		left.add(Component.translatable("gui.jaymoskingdoms.valor.look", Component.translatable("valor.jaymoskingdoms.look." + data.lookKind().key())));
		boolean kings = ValorKings.open(data);
		left.add(kings ? Component.translatable("gui.jaymoskingdoms.valor.soul", Component.translatable("valor.jaymoskingdoms.soul." + Valor.SOUL_KEYS[data.soul()]))
			.withColor(ARGB.opaque(data.soulColor())) : Component.translatable("gui.jaymoskingdoms.valor.soul.hidden").withColor(Ui.DIM));
		left.add(Component.translatable(kings ? ValorKings.coatOpen(data) ? "gui.jaymoskingdoms.valor.kings.coat" : "gui.jaymoskingdoms.valor.kings.open"
			: rank >= Valor.TEMPERED ? "gui.jaymoskingdoms.valor.kings.ready" : "gui.jaymoskingdoms.valor.kings.closed", data.greatFoes(), ValorKings.COAT_FOES)
			.withColor(kings ? Ui.GOLD : Ui.DIM));
		for (Component c : left) {
			for (var line : this.font.split(c, lw)) {
				graphics.text(this.font, line, lx, ly, c.getStyle().getColor() == null ? Ui.TEXT : ARGB.opaque(c.getStyle().getColor().getValue()));
				ly += Ui.LINE + 1;
			}
			ly += 2;
		}
		// right: five bars
		Classes.PlayerClass cls = Classes.classOf(player);
		int rx = x + w / 2 + 4, ry = y + 38;
		float[] bars = {
			art(data, rank, cls == Classes.PlayerClass.BRUTE || cls == Classes.PlayerClass.WARLORD),
			rank < Valor.KINDLED ? 0.0F : art(data, rank, cls == Classes.PlayerClass.BRUTE || cls == Classes.PlayerClass.WARLORD || cls == Classes.PlayerClass.THIEF),
			(float) Math.min(1.0, Valor.sensesReach(player) / 36.0),
			(Valor.sensesRank(player) + 1) / 5.0F,
			(rank + 1) / 5.0F};
		String[] names = {"hardness", "edge", "reach", "sight", "endurance"};
		for (int i = 0; i < bars.length; i++) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.stat." + names[i]), rx, ry, 60, Ui.TEXT);
			Ui.bar(graphics, rx + 64, ry + 3, BAR_W, 3, bars[i], accent);
			graphics.text(this.font, grade(bars[i]), rx + 64 + BAR_W + 6, ry, accent);
			ry += 14;
		}
		// the feats that grew it
		ry += 4;
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.feats"), rx, ry, w / 2 - 14, accent);
		ry += Ui.LINE + 2;
		List<Map.Entry<Valor.Feat, Integer>> feats = new ArrayList<>(data.featCounts().entrySet());
		feats.sort((p, q) -> Integer.compare(q.getValue(), p.getValue()));
		if (feats.isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.feats.none"), rx, ry, w / 2 - 14, Ui.DIM);
		}
		for (int i = 0; i < Math.min(5, feats.size()); i++) {
			Map.Entry<Valor.Feat, Integer> e = feats.get(i);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.feat", Component.translatable("valor.jaymoskingdoms.feat." + e.getKey().key()),
				e.getValue()), rx, ry, w / 2 - 14, Ui.TEXT);
			ry += Ui.LINE + 1;
		}
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.valor.hint"), x + 9, y + PANEL_H - 15, w - 90, Ui.HINT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** An art's share: its rank over Unbroken, capped at Steeled without the class for it. */
	private static float art(Valor.Data data, int rank, boolean proficient) {
		return ((proficient ? rank : Math.min(rank, Valor.STEELED)) + 1) / 5.0F;
	}

	private static String grade(float share) {
		return share >= 0.99F ? "A" : share >= 0.79F ? "B" : share >= 0.59F ? "C" : share >= 0.39F ? "D" : share > 0.0F ? "E" : "-";
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static int brighten(int c) {
		return ARGB.srgbLerp(0.55F, c, 0xFFFFFF);
	}
}
