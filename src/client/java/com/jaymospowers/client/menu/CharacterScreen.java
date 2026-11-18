package com.jaymospowers.client.menu;

import com.jaymospowers.api.Power;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.client.PowerScreen;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.stamina.Feathers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * The character menu's first page ({@link MenuTabs}), in the Codex's shape: a status header (level, who you are, the
 * experience bar), you in a tall box with what you wear down both sides, a soft glow behind you and a shadow under your
 * feet, your name and heritage under, and beside it panels of power, vitals, attributes, resistances and a record of what
 * is already tracked. Jaymo's Kingdoms adds to the header, the attributes and the record when it is loaded. The body
 * scrolls with the wheel when the window is too small for all of it.
 */
public class CharacterScreen extends Screen {
	/** The most the page is wide, the figure box's width and least height, the header's height, a gap, a list row's height. */
	static final int MAX_W = 460, FIG_W = 132, FIG_MIN_H = 196, HEADER_H = 26, GAP = 4, ROW = 11;
	/** A worn-gear well's size and step down the box's sides, and the room the name lines take under the figure. */
	static final int WELL = 18, WELL_STEP = 22, NAMES_H = 46;
	static final int GOLD_EDGE = 0xFFB89B3F, EDGE_DIM = 0x90B89B3F, POWER = 0xFF4FC3A8;
	static final int HEALTH = 0xFFC84040, HUNGER = 0xFFB8863C, STAMINA = 0xFFE08A30, ARMOUR = 0xFF8FA3B8, XP = 0xFF80D060, MASTERY = 0xFF6AA8FF,
		BLOWS = 0xFF6AA8FF;

	/** A line of a list: a label and a value, a heading (label only), or one with a bar (the value its words, share 0-1). */
	private record Row(Component label, Component value, float share, int color) {
		static Row of(Component label, Component value) {
			return new Row(label, value, -1.0F, PowerScreen.COLOR_TEXT);
		}

		static Row heading(Component label) {
			return new Row(label, null, -1.0F, PowerScreen.COLOR_GOLD);
		}
	}

	private int scroll;

	/** The H menu's raised window (the Map page's too). */
	static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		UiStyle.window(graphics, x, y, w, h);
	}

	/** The H menu's sunken area. */
	static void recess(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		UiStyle.recess(graphics, x, y, w, h);
	}

	public CharacterScreen() {
		super(Component.translatable("menu.jaymospowers.tab.character"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		// the record reads the game's stats, which the server only sends when asked
		if (this.minecraft.getConnection() != null) {
			this.minecraft.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
		}
	}

	private int pageW() {
		return Math.min(MAX_W, this.width - MenuTabs.SIDE - 20);
	}

	private int left() {
		return MenuTabs.centreX(this.width, pageW() + 12) + 6;
	}

	private int top() {
		return Math.max(MenuTabs.ROOM, 6) + 6;
	}

	/** The page's top-left corner, where the menu's tabs hang. */
	int[] corner() {
		return new int[] {left(), top()};
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("menu.jaymospowers.character." + key, args);
	}

	private static Component num(int value) {
		return Component.literal(Integer.toString(value));
	}

	private static Component of(int value, int max) {
		return t("of", value, max);
	}

	private static float share(float value, float max) {
		return max <= 0.0F ? 0.0F : Mth.clamp(value / max, 0.0F, 1.0F);
	}

	/** The values of the header's rows: who you are, one line each (heritage, class, title). The level is the {@code level} panel's. */
	private static List<Component> identity() {
		List<Component> out = new ArrayList<>();
		for (Component[] r : MenuTabs.characterRows("header")) {
			if (r.length >= 2) {
				out.add(r[1]);
			}
		}
		return out;
	}

	private static List<Row> rows(String panel) {
		List<Row> rows = new ArrayList<>();
		for (Component[] r : MenuTabs.characterRows(panel)) {
			if (r.length == 1) {
				rows.add(Row.heading(r[0]));
			} else if (r.length >= 2) {
				rows.add(Row.of(r[0], r[1]));
			}
		}
		return rows;
	}

	private List<Row> attributes(LocalPlayer player) {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.of(t("max_health"), num(Mth.ceil(player.getMaxHealth()))));
		rows.add(Row.of(t("damage"), Component.literal(String.format("%.1f", player.getAttributeValue(Attributes.ATTACK_DAMAGE)))));
		rows.add(Row.of(t("speed"), Component.literal(String.format("%.0f%%", player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1 * 100.0))));
		rows.add(Row.of(t("luck"), Component.literal(String.format("%.0f", player.getAttributeValue(Attributes.LUCK)))));
		rows.addAll(rows("attributes"));
		return rows;
	}

	private List<Row> resistances(LocalPlayer player) {
		List<Row> rows = new ArrayList<>();
		// vanilla's armour rule: what the armour and its toughness take off a blow
		double armour = player.getArmorValue(), toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double blows = Math.min(20.0, Math.max(armour / 5.0, armour - 4.0 / (2.0 + toughness / 4.0))) / 25.0;
		rows.add(new Row(t("blows"), Component.literal(Math.round(blows * 100.0) + "%"), (float)blows * 4.0F / 3.0F, BLOWS));
		double knock = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		rows.add(new Row(t("knockback"), Component.literal(Math.round(knock * 100.0) + "%"), (float)knock, BLOWS));
		rows.addAll(rows("resistances"));
		return rows;
	}

	private List<Row> record(LocalPlayer player) {
		List<Row> rows = new ArrayList<>();
		var stats = player.getStats();
		rows.add(Row.of(t("felled"), num(stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)))));
		rows.add(Row.of(t("fallen"), num(stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)))));
		rows.add(Row.of(t("days"), num(stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 24000)));
		rows.addAll(rows("record"));
		return rows;
	}

	private static int listHeight(List<Row> rows) {
		return 20 + rows.size() * ROW + 4;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll = Math.max(0, this.scroll - (int)Math.signum(scrollY) * ROW * 2);
		return true;
	}

	/** A panel's box: a thin gold edge on a dark well, and a short accent tick over its top. */
	private static void box(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
		UiStyle.recess(graphics, x, y, w, h);
		graphics.outline(x, y, w, h, EDGE_DIM);
		graphics.fill(x + 6, y, x + 30, y + 1, accent);
	}

	/** A titled list in a box; returns its height. */
	private int list(GuiGraphicsExtractor graphics, int x, int y, int w, Component title, int accent, List<Row> rows) {
		int h = listHeight(rows);
		box(graphics, x, y, w, h, accent);
		int inner = w - 16;
		UiStyle.line(graphics, this.font, title, x + 8, y + 6, inner, PowerScreen.COLOR_TEXT);
		int ry = y + 20;
		int w8 = w - 16;
		for (Row r : rows) {
			if (r.value() == null) {
				UiStyle.line(graphics, this.font, r.label(), x + 8, ry + 1, w8, r.color());
			} else if (r.share() >= 0.0F) {
				int labelW = Math.min(this.font.width(r.label()), w8 * 2 / 5);
				UiStyle.line(graphics, this.font, r.label(), x + 8, ry + 1, labelW, PowerScreen.COLOR_DIM);
				int valueW = this.font.width("100%");
				UiStyle.lineRight(graphics, this.font, r.value(), x + 8 + w8 * 3 / 5, ry + 1, valueW, PowerScreen.COLOR_DIM);
				UiStyle.bar(graphics, x + 8 + w8 * 3 / 5 + 4, ry + 4, w8 * 2 / 5 - 4, 3, Mth.clamp(r.share(), 0.0F, 1.0F), r.color());
			} else {
				int labelW = Math.min(this.font.width(r.label()), w8 / 2);
				UiStyle.line(graphics, this.font, r.label(), x + 8, ry + 1, labelW, PowerScreen.COLOR_DIM);
				UiStyle.lineRight(graphics, this.font, r.value(), x + 8 + w8, ry + 1, w8 - labelW - 6, r.color());
			}
			ry += ROW;
		}
		return h;
	}

	/** A bar with its label and numbers over it. */
	private void meter(GuiGraphicsExtractor graphics, int x, int y, int w, Component label, Component value, float share, int color) {
		int valueW = Math.min(this.font.width(value), w / 2);
		UiStyle.line(graphics, this.font, label, x, y, w - valueW - 4, PowerScreen.COLOR_DIM);
		UiStyle.lineRight(graphics, this.font, value, x + w, y, valueW, PowerScreen.COLOR_TEXT);
		UiStyle.bar(graphics, x, y + 11, w, 3, Mth.clamp(share, 0.0F, 1.0F), color);
	}

	/** A round soft patch of {@code color} ({@code 0xRRGGBB}) made of rings that fade out, centred on {@code cx, cy}. */
	private static void glow(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
		int rings = 6;
		for (int i = 0; i < rings; i++) {
			int r = radius - radius * i / rings;
			int argb = (0x0C << 24) | color;
			for (int dy = -r; dy < r; dy++) {
				int half = (int)Math.sqrt((double)r * r - (double)dy * dy);
				graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, argb);
			}
		}
	}

	/** A flat dark oval for the shadow under the feet. */
	private static void shadow(GuiGraphicsExtractor graphics, int cx, int cy, int rx, int ry) {
		for (int dy = -ry; dy <= ry; dy++) {
			int half = (int)(rx * Math.sqrt(1.0 - (double)dy * dy / ((double)ry * ry)));
			graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0x58000000);
		}
	}

	private void well(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack) {
		UiStyle.well(graphics, x, y, WELL, WELL);
		if (!stack.isEmpty()) {
			graphics.item(stack, x + 1, y + 1);
			graphics.itemDecorations(this.font, stack, x + 1, y + 1);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		int pw = pageW(), x = left(), y = top(), bottom = this.height - 6 - 6;
		UiStyle.window(graphics, x - 6, y - 6, pw + 12, bottom - y + 12);
		if (player == null) {
			return;
		}
		// the header: who you are, and how far to the next level
		List<Component> who = identity();
		// the level is the experience level, unless Jaymo's Kingdoms gives the character level
		List<Component[]> given = MenuTabs.characterRows("level");
		Component level = t("level", given.isEmpty() || given.getFirst().length < 2 ? num(player.experienceLevel) : given.getFirst()[1]);
		int xpNeeded = player.getXpNeededForNextLevel();
		Component xp = t("xp", Mth.floor(player.experienceProgress * xpNeeded), xpNeeded);
		int xpW = this.font.width(xp);
		StringBuilder line = new StringBuilder(level.getString());
		for (Component c : who) {
			line.append("  ·  ").append(c.getString());
		}
		UiStyle.line(graphics, this.font, Component.literal(line.toString()), x, y, pw - xpW - 8, PowerScreen.COLOR_GOLD);
		UiStyle.lineRight(graphics, this.font, xp, x + pw, y, xpW, PowerScreen.COLOR_DIM);
		UiStyle.bar(graphics, x, y + 12, pw, 3, share(player.experienceProgress, 1.0F), PowerScreen.COLOR_GOLD);
		// the body scrolls under the header
		int bodyTop = y + HEADER_H + GAP, view = bottom - bodyTop;
		int figW = Math.min(FIG_W, pw / 2), figH = Math.max(FIG_MIN_H, view);
		int rx = x + figW + GAP * 2, rw = pw - figW - GAP * 2;
		boolean pair = rw >= 250;
		List<Row> attributes = attributes(player), resistances = resistances(player), record = record(player);
		int powerH = 48, vitalsH = 62;
		int pairH = pair ? Math.max(listHeight(attributes), listHeight(resistances)) : listHeight(attributes) + GAP + listHeight(resistances);
		int rightH = powerH + GAP + vitalsH + GAP + pairH + GAP + listHeight(record);
		int total = Math.max(figH, rightH);
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - view));
		graphics.enableScissor(x - 4, bodyTop, x + pw + 4, bottom);
		int by = bodyTop - this.scroll;
		// you
		box(graphics, x, by, figW, figH, PowerScreen.COLOR_GOLD);
		int cx = x + figW / 2, areaTop = by + 8, areaBottom = by + figH - NAMES_H - 6;
		int figTop = areaTop, figBottom = areaBottom;
		for (int i = 0; i < 4; i++) {
			EquipmentSlot slot = new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}[i];
			well(graphics, x + 4, areaTop + i * WELL_STEP, player.getItemBySlot(slot));
		}
		List<ItemStack> worn = Baubles.worn(player);
		ItemStack[] right = {player.getMainHandItem(), player.getOffhandItem(), worn.isEmpty() ? ItemStack.EMPTY : worn.get(0),
			worn.size() > Baubles.BELT_SLOT ? worn.get(Baubles.BELT_SLOT) : ItemStack.EMPTY};
		for (int i = 0; i < 4; i++) {
			well(graphics, x + figW - 4 - WELL, areaTop + i * WELL_STEP, right[i]);
		}
		int innerL = x + 4 + WELL + 2, innerR = x + figW - 4 - WELL - 2;
		int size = Mth.clamp((figBottom - figTop) / 3, 18, 64);
		int footY = figBottom - 6;
		glow(graphics, cx, (figTop + figBottom) / 2, Math.min((innerR - innerL) / 2 + 8, (figBottom - figTop) / 2), 0x6A8CFF);
		shadow(graphics, cx, footY, Math.max(8, size * 2 / 3), 3);
		if (innerR - innerL >= 24) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, innerL, figTop, innerR, figBottom, size, 0.0625F, mouseX, mouseY, player);
		}
		int ny = by + figH - NAMES_H;
		UiStyle.lineCentred(graphics, this.font, player.getDisplayName(), cx, ny, figW - 8, PowerScreen.COLOR_TEXT);
		for (int i = 0; i < Math.min(3, who.size()); i++) {
			UiStyle.lineCentred(graphics, this.font, who.get(i), cx, ny + 12 + i * 11, figW - 8, i == 0 ? PowerScreen.COLOR_DIM : PowerScreen.COLOR_GOLD);
		}
		// power
		int ry = by;
		box(graphics, rx, ry, rw, powerH, POWER);
		Power power = Powers.of(player);
		Component powerName = power == null ? t("no_power") : power.name();
		UiStyle.line(graphics, this.font, t("power", powerName), rx + 8, ry + 6, rw - 16, power == null ? PowerScreen.COLOR_DIM : POWER);
		if (power != null) {
			Component fate = Component.translatable(Fate.isAscended(player)
				? Fate.isCorrupted(player) ? "screen.jaymospowers.badge.ascended_corrupted" : "screen.jaymospowers.badge.ascended"
				: Fate.isCorrupted(player) ? "screen.jaymospowers.badge.corrupted" : "screen.jaymospowers.badge.mortal");
			if (!player.isCreative()) {
				Component mastery = Component.translatable("screen.jaymospowers.mastery.count", Mastery.unlocked(player), Mastery.ALL);
				int mw = Math.min(this.font.width(mastery), rw / 2);
				UiStyle.lineRight(graphics, this.font, mastery, rx + rw - 8, ry + 6, mw, PowerScreen.COLOR_DIM);
				float progress = Mastery.progress(player);
				UiStyle.bar(graphics, rx + 8, ry + 20, rw - 16, 4, progress >= 0.0F ? progress : share(Mastery.unlocked(player), Mastery.ALL), MASTERY);
			}
			UiStyle.line(graphics, this.font, t("fate", fate), rx + 8, ry + 30, rw - 16, PowerScreen.COLOR_DIM);
		}
		ry += powerH + GAP;
		// vitals
		box(graphics, rx, ry, rw, vitalsH, HEALTH);
		int colW = (rw - 16 - 2 * 8) / 3;
		Feathers.View stamina = Feathers.view(player);
		meter(graphics, rx + 8, ry + 6, colW, t("health"), of(Mth.ceil(player.getHealth()), Mth.ceil(player.getMaxHealth())),
			share(player.getHealth(), player.getMaxHealth()), HEALTH);
		meter(graphics, rx + 8 + colW + 8, ry + 6, colW, t("hunger"), of(player.getFoodData().getFoodLevel(), 20),
			share(player.getFoodData().getFoodLevel(), 20.0F), HUNGER);
		meter(graphics, rx + 8 + 2 * (colW + 8), ry + 6, colW, t("stamina"), of(stamina.feathers(), stamina.cap()),
			share(stamina.feathers(), stamina.cap()), STAMINA);
		meter(graphics, rx + 8, ry + 34, colW, t("armour"), num(player.getArmorValue()), share(player.getArmorValue(), 20.0F), ARMOUR);
		meter(graphics, rx + 8 + colW + 8, ry + 34, colW, t("experience"), t("level", player.experienceLevel), player.experienceProgress, XP);
		ry += vitalsH + GAP;
		// attributes and resistances, side by side when there is room
		if (pair) {
			int half = (rw - GAP) / 2;
			list(graphics, rx, ry, half, t("attributes"), POWER, attributes);
			list(graphics, rx + half + GAP, ry, rw - half - GAP, t("resistances"), BLOWS, resistances);
		} else {
			ry += list(graphics, rx, ry, rw, t("attributes"), POWER, attributes) + GAP;
			list(graphics, rx, ry, rw, t("resistances"), BLOWS, resistances);
			ry -= listHeight(attributes) + GAP;
		}
		ry += pairH + GAP;
		list(graphics, rx, ry, rw, t("record"), PowerScreen.COLOR_GOLD, record);
		graphics.disableScissor();
		if (total > view) {
			UiStyle.scrollbar(graphics, x + pw + 1, bodyTop, view, view, total, this.scroll);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
