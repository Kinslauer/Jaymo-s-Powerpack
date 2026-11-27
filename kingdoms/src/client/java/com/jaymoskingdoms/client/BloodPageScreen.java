package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BloodPage;
import com.jaymoskingdoms.NightGifts;
import com.jaymoskingdoms.Skills;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Blood page (Night's Blood, patch 2), a page of the H menu for a vampire or a dhampir: the gifts they have and can learn,
 * the slots (three free, a pact for each one past them, six at most), blood age and the page's points, and the five pacts.
 * Andy: "these options only appear for vampires". Learning, slotting and the pacts work only at a shrine (the server checks
 * every press); anywhere else the page only shows what you have. A dhampir sees only the four nodes a dhampir can take, paid
 * with skill points as they always were, with no slots, no age and no pacts.
 */
final class BloodPageScreen extends Screen {
	private static final int W = 290, H = 214, PAD = 10, ROW = 22, HEAD = 56, FOOT = 18;
	/** Whether the last opening said they stand at a shrine (the server asks again of every press). */
	private static boolean shrine;
	private int scroll;

	BloodPageScreen() {
		super(Component.translatable("menu.jaymoskingdoms.tab.blood"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BloodPage.Open.TYPE, (open, context) -> {
			shrine = open.shrine();
			Minecraft.getInstance().gui.setScreen(new BloodPageScreen());
		});
	}

	/** Whether the page is open to the local player: a vampire or a dhampir. */
	static boolean available() {
		LocalPlayer player = Minecraft.getInstance().player;
		NightGifts.Gift gift = player == null ? null : NightGifts.gift(player);
		return gift != null && gift.blood();
	}

	/** From the H menu's tab: the page, not at a shrine. */
	static void open() {
		shrine = false;
		Minecraft.getInstance().gui.setScreen(new BloodPageScreen());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return MenuCompat.centre(this.width, W);
	}

	private int top() {
		return Math.max(MenuCompat.STRIP + 4, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	private enum Kind {
		HEADING, GIFT, PACT, NODE
	}

	/** One line: a heading, a gift (full vampire), a pact, or a dhampir's node. */
	private record Entry(Kind kind, String key, Component name, Component text, Component right, int color) {
	}

	private BloodPage.@Nullable Blood blood() {
		return this.minecraft.player == null ? null : BloodPage.of(this.minecraft.player);
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return list;
		}
		BloodPage.Blood b = blood();
		if (b == null) {
			// a dhampir's nodes: skill points, as ever
			Skills.Progress progress = Skills.of(player);
			list.add(new Entry(Kind.HEADING, "", Component.translatable("gui.jaymoskingdoms.blood.gifts"), Component.translatable("gui.jaymoskingdoms.blood.dhampir"),
				Component.empty(), Ui.GOLD));
			for (Skills.Perk perk : new Skills.Perk[] {Skills.Perk.LIFELEECH, Skills.Perk.NIGHT_SENSE, Skills.Perk.NIGHT_POUND, Skills.Perk.BAT_WINGS}) {
				boolean have = progress.rank(perk) >= 1;
				list.add(new Entry(Kind.NODE, Integer.toString(perk.ordinal()), Component.translatable("skill.jaymoskingdoms.perk." + perk.key()),
					Component.translatable("skill.jaymoskingdoms.perk." + perk.key() + ".desc"),
					Component.translatable(have ? "gui.jaymoskingdoms.blood.learned" : "gui.jaymoskingdoms.blood.cost", perk.cost),
					have ? Ui.GOOD : progress.blocked(perk) == null ? Ui.TEXT : Ui.HINT));
			}
			return list;
		}
		list.add(new Entry(Kind.HEADING, "", Component.translatable("gui.jaymoskingdoms.blood.gifts"), Component.translatable("gui.jaymoskingdoms.blood.gifts.note"),
			Component.empty(), Ui.GOLD));
		for (BloodPage.Gift g : BloodPage.Gift.values()) {
			boolean known = b.knows(g), slotted = b.slotted(g);
			Component name = g.move >= 0 ? Component.translatable("spell.jaymoskingdoms.night_" + g.key() + ".name") : Component.translatable("skill.jaymoskingdoms.perk." + g.key());
			Component text = g.move >= 0 ? Component.translatable("gui.jaymoskingdoms.blood.gift." + g.key()) : Component.translatable("skill.jaymoskingdoms.perk." + g.key() + ".desc");
			// a gift that needs another learned first, or an older blood, says so and is dim
			String blocked = known ? null : BloodPage.blocked(b, g);
			Component right = blocked != null ? Component.translatable(blocked, g.needs == null ? Component.empty() : Component.translatable(g.needs.move >= 0
					? "spell.jaymoskingdoms.night_" + g.needs.key() + ".name" : "skill.jaymoskingdoms.perk." + g.needs.key()))
				: Component.translatable(slotted ? "gui.jaymoskingdoms.blood.slotted" : known ? "gui.jaymoskingdoms.blood.learned" : "gui.jaymoskingdoms.blood.cost", g.cost);
			list.add(new Entry(Kind.GIFT, g.key(), name, text, right, slotted ? Ui.GOOD : known ? Ui.TEXT : blocked == null && b.points() >= g.cost ? Ui.TEXT : Ui.HINT));
		}
		list.add(new Entry(Kind.HEADING, "", Component.translatable("gui.jaymoskingdoms.blood.pacts"), Component.translatable("gui.jaymoskingdoms.blood.pacts.note"),
			Component.empty(), Ui.GOLD));
		for (BloodPage.Pact p : BloodPage.Pact.values()) {
			boolean taken = b.has(p);
			list.add(new Entry(Kind.PACT, p.key(), Component.translatable("gui.jaymoskingdoms.blood.pact." + p.key()),
				Component.translatable("gui.jaymoskingdoms.blood.pact." + p.key() + ".text"),
				Component.translatable(taken ? "gui.jaymoskingdoms.blood.taken" : "gui.jaymoskingdoms.blood.free"), taken ? Ui.BAD : Ui.TEXT));
		}
		return list;
	}

	private int listTop() {
		return top() + HEAD;
	}

	private int listHeight() {
		return H - HEAD - FOOT;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll -= (int)Math.signum(scrollY) * ROW;
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int x = left(), mx = (int)event.x(), my = (int)event.y();
		if (mx < x + PAD || mx > x + W - PAD || my < listTop() || my >= listTop() + listHeight()) {
			return false;
		}
		int index = (my - listTop() + this.scroll) / ROW;
		List<Entry> entries = entries();
		if (index < 0 || index >= entries.size()) {
			return false;
		}
		Entry e = entries.get(index);
		BloodPage.Blood b = blood();
		if (e.kind() == Kind.NODE) {
			// a dhampir's node: the skill tree's own spend
			ClientPlayNetworking.send(new Skills.Spend(Integer.parseInt(e.key())));
			click();
			return true;
		}
		if (b == null || !shrine) {
			return true;
		}
		if (e.kind() == Kind.GIFT) {
			BloodPage.Gift g = BloodPage.Gift.valueOf(e.key().toUpperCase(java.util.Locale.ROOT));
			BloodPage.Op op = !b.knows(g) ? BloodPage.Op.LEARN : b.slotted(g) ? BloodPage.Op.UNSLOT : BloodPage.Op.SLOT;
			ClientPlayNetworking.send(new BloodPage.Act(op.ordinal(), g.key()));
			click();
		} else if (e.kind() == Kind.PACT) {
			BloodPage.Pact p = BloodPage.Pact.valueOf(e.key().toUpperCase(java.util.Locale.ROOT));
			ClientPlayNetworking.send(new BloodPage.Act((b.has(p) ? BloodPage.Op.GIVE : BloodPage.Op.TAKE).ordinal(), p.key()));
			click();
		}
		return true;
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = W - 2 * PAD;
		Ui.panel(graphics, x, y, W, H, Ui.GOLD);
		BloodPage.Blood b = blood();
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.blood.title"), x + PAD, y + 8, w, Ui.GOLD);
		if (b != null) {
			Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.blood.age." + b.age()), x + W - PAD, y + 8, w / 2, Ui.TEXT);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.blood.points", b.points()), x + PAD, y + 20, w / 2, Ui.DIM);
			// the slots: a box for each of the six, the ones there are lit, the filled ones named by their gift
			int box = (w - 5 * 4) / BloodPage.MAX_SLOTS;
			for (int i = 0; i < BloodPage.MAX_SLOTS; i++) {
				int bx = x + PAD + i * (box + 4), by = y + 32;
				boolean open = i < b.slotCount(), filled = i < b.slots().size();
				graphics.fill(bx, by, bx + box, by + 14, open ? Ui.WELL : Ui.ROW_ALT);
				graphics.fill(bx, by, bx + box, by + 1, open ? Ui.GOLD : Ui.BUTTON_RIM_OFF);
				if (filled) {
					BloodPage.Gift g = BloodPage.Gift.valueOf(b.slots().get(i).toUpperCase(java.util.Locale.ROOT));
					Component name = g.move >= 0 ? Component.translatable("spell.jaymoskingdoms.night_" + g.key() + ".name") : Component.translatable("skill.jaymoskingdoms.perk." + g.key());
					Ui.lineCentred(graphics, this.font, name, bx + box / 2, by + 3, box - 2, Ui.GOOD);
				}
			}
		} else {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.blood.dhampir"), x + PAD, y + 20, w, Ui.DIM);
		}
		Ui.rule(graphics, x + 6, x + W - 6, y + HEAD - 5);
		List<Entry> entries = entries();
		int total = entries.size() * ROW;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - listHeight()));
		graphics.enableScissor(x + PAD - 2, listTop(), x + W - PAD + 2, listTop() + listHeight());
		for (int i = 0; i < entries.size(); i++) {
			int ry = listTop() + i * ROW - this.scroll;
			if (ry + ROW <= listTop() || ry >= listTop() + listHeight()) {
				continue;
			}
			Entry e = entries.get(i);
			boolean over = e.kind() != Kind.HEADING && Ui.over(mouseX, mouseY, x + PAD, ry, w - 6, ROW - 2);
			Ui.row(graphics, x + PAD, ry, x + W - PAD - 6, ry + ROW - 2, i, over);
			Ui.line(graphics, this.font, e.name(), x + PAD + 4, ry + 2, w - 90, e.color());
			if (!e.right().getString().isEmpty()) {
				Ui.lineRight(graphics, this.font, e.right(), x + W - PAD - 10, ry + 2, 80, e.color());
			}
			if (!e.text().getString().isEmpty()) {
				Ui.line(graphics, this.font, e.text(), x + PAD + 4, ry + 12, w - 16, Ui.DIM);
			}
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + W - PAD - 2, listTop(), listHeight(), listHeight(), total, this.scroll);
		if (b != null && !shrine) {
			Ui.lineCentred(graphics, this.font, Component.translatable("gui.jaymoskingdoms.blood.shrine_only"), x + W / 2, y + H - 13, w, Ui.HINT);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
