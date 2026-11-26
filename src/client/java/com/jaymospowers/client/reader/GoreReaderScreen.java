package com.jaymospowers.client.reader;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.blood.BloodParticles;
import com.jaymospowers.core.reader.GoreOverrides;
import com.jaymospowers.core.reader.GoreReader;
import com.jaymospowers.core.wounds.BodyPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The gore reader (ops only, {@code /gore reader}): one striped row per creature kind (its id, body plan, weight as times
 * a man, blood colour, metal body) or per weapon (its id, kind and blows, cut depth), found by the box above, scrolled by
 * the wheel. Click a row and the strip under the list changes what is set for it: a creature's blood, weight and body
 * plan, a weapon's kind. A value an op has set shows in gold; "Auto" puts the mod's own guess back. Every change goes to
 * the server, which saves it and sends the row back.
 */
public class GoreReaderScreen extends Screen {
	private static final int MAX_W = 440, ROW_H = 14, DETAIL_H = 58, ACCENT = 0xFFD8504A, SET = UiStyle.GOLD;
	/** How much a weight button moves the weight (times a man's bulk). */
	private static final float WEIGHT_STEP = 0.25F;
	/** The blood a button can pick: its name's key and its colour; no blood is {@link GoreOverrides#NO_BLOOD}. */
	private static final String[] BLOOD_KEYS = {"red", "ender", "dark", "slime", "bug", "creeper", "rotten", "magma", "gold", "sap", "none"};
	private static final int[] BLOOD_COLORS = {BladeBlood.RED, BladeBlood.ENDER, BladeBlood.DARK, BladeBlood.SLIME, BladeBlood.BUG,
		BladeBlood.CREEPER, BladeBlood.ROTTEN, BloodParticles.MAGMA, BladeBlood.GOLD, BladeBlood.SAP, GoreOverrides.NO_BLOOD};

	private final List<GoreReader.MobRow> mobs;
	private final List<GoreReader.WeaponRow> weapons;
	private boolean weaponTab;
	private EditBox search;
	private List<Integer> shown = new ArrayList<>();
	private int scroll;
	private @Nullable String selected;

	/** One button in the strip under the list. */
	private record Btn(int x, int y, int w, Component label, Runnable press) {
	}

	private GoreReaderScreen(GoreReader.Data data) {
		super(Component.translatable("screen.jaymospowers.gore_reader.title"));
		this.mobs = new ArrayList<>(data.mobs());
		this.weapons = new ArrayList<>(data.weapons());
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(GoreReader.Data.TYPE, (payload, context) -> context.client().execute(() -> {
			if (payload.open()) {
				Minecraft.getInstance().gui.setScreen(new GoreReaderScreen(payload));
			} else if (Minecraft.getInstance().gui.screen() instanceof GoreReaderScreen screen) {
				screen.merge(payload);
			}
		}));
	}

	private void merge(GoreReader.Data data) {
		for (GoreReader.MobRow row : data.mobs()) {
			for (int i = 0; i < mobs.size(); i++) {
				if (mobs.get(i).id().equals(row.id())) {
					mobs.set(i, row);
				}
			}
		}
		for (GoreReader.WeaponRow row : data.weapons()) {
			boolean found = false;
			for (int i = 0; i < weapons.size(); i++) {
				if (weapons.get(i).id().equals(row.id())) {
					weapons.set(i, row);
					found = true;
				}
			}
			if (!found) {
				weapons.add(row);
				weapons.sort(java.util.Comparator.comparing(GoreReader.WeaponRow::id));
			}
		}
		rebuild();
	}

	// ---------------------------------------------------------------- layout

	private int panelW() {
		return Math.min(MAX_W, width - 12);
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return 6;
	}

	private int panelH() {
		return height - 12;
	}

	private int toolsY() {
		return top() + UiStyle.HEADER;
	}

	private int listTop() {
		return toolsY() + 22;
	}

	private int listBottom() {
		return top() + panelH() - DETAIL_H - 4;
	}

	private int visible() {
		return Math.max(1, (listBottom() - listTop()) / ROW_H);
	}

	private int rowsTotal() {
		return weaponTab ? weapons.size() : mobs.size();
	}

	private String idAt(int index) {
		return weaponTab ? weapons.get(index).id() : mobs.get(index).id();
	}

	@Override
	protected void init() {
		int x = left(), w = panelW();
		String text = search == null ? "" : search.getValue();
		int box = x + 148;
		search = addRenderableWidget(new EditBox(font, box, toolsY() + 1, x + w - 8 - box, 16, Component.translatable("screen.jaymospowers.gore_reader.search")));
		search.setHint(Component.translatable("screen.jaymospowers.gore_reader.search"));
		search.setValue(text);
		search.setResponder(s -> {
			scroll = 0;
			rebuild();
		});
		rebuild();
	}

	private void rebuild() {
		String text = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		shown = new ArrayList<>();
		for (int i = 0; i < rowsTotal(); i++) {
			if (text.isEmpty() || idAt(i).contains(text)) {
				shown.add(i);
			}
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - visible()));
	}

	/** The row (an index into the shown list) under the mouse, or -1. */
	private int rowAt(double mouseX, double mouseY) {
		int x = left() + 6, w = panelW() - 14;
		for (int r = 0; r < visible(); r++) {
			if (scroll + r < shown.size() && UiStyle.over((int) mouseX, (int) mouseY, x, listTop() + r * ROW_H, w, ROW_H - 1)) {
				return scroll + r;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- the rows' values, as words

	private GoreReader.@Nullable MobRow mob() {
		for (GoreReader.MobRow row : mobs) {
			if (row.id().equals(selected)) {
				return row;
			}
		}
		return null;
	}

	private GoreReader.@Nullable WeaponRow weapon() {
		for (GoreReader.WeaponRow row : weapons) {
			if (row.id().equals(selected)) {
				return row;
			}
		}
		return null;
	}

	private static Component planName(int plan) {
		return Component.translatable("screen.jaymospowers.gore_reader.plan." + BodyPlan.values()[plan].name().toLowerCase(Locale.ROOT));
	}

	private static Component kindName(int kind) {
		return kind < 0 ? Component.translatable("screen.jaymospowers.gore_reader.none")
			: Component.translatable("screen.jaymospowers.gore_reader.kind." + WeaponKind.values()[kind].name().toLowerCase(Locale.ROOT));
	}

	private static Component bloodName(int blood) {
		for (int i = 0; i < BLOOD_COLORS.length; i++) {
			if (BLOOD_COLORS[i] == blood) {
				return Component.translatable("screen.jaymospowers.gore_reader.blood." + BLOOD_KEYS[i]);
			}
		}
		return Component.literal(String.format("#%06X", blood));
	}

	private static String bulkText(float bulk) {
		return String.format(Locale.ROOT, "x%.2f", bulk);
	}

	/** Which blows a kind makes, as words ("Cuts, thrusts"). */
	private static Component blowsOf(int kind) {
		if (kind < 0) {
			return Component.translatable("screen.jaymospowers.gore_reader.none");
		}
		List<String> words = new ArrayList<>();
		for (WeaponKind.Blow blow : WeaponKind.values()[kind].blows()) {
			words.add(Component.translatable("screen.jaymospowers.gore_reader.blow." + blow.name().toLowerCase(Locale.ROOT)).getString());
		}
		return Component.literal(String.join(", ", words));
	}

	private static Component depthName(int depth) {
		return Component.translatable("screen.jaymospowers.gore_reader.depth." + depth);
	}

	// ---------------------------------------------------------------- the strip's buttons

	private List<Btn> buttons() {
		List<Btn> out = new ArrayList<>();
		int x = left() + 8, y = listBottom() + 4 + 30;
		GoreReader.MobRow mob = weaponTab ? null : mob();
		GoreReader.WeaponRow weapon = weaponTab ? weapon() : null;
		if (mob != null) {
			out.add(new Btn(x, y, 110, Component.translatable("screen.jaymospowers.gore_reader.set_blood", bloodName(mob.blood())),
				() -> send(GoreReader.BLOOD, nextBlood(mob))));
			out.add(new Btn(x + 114, y, 20, Component.literal("-"), () -> send(GoreReader.WEIGHT, String.valueOf(Math.max(GoreOverrides.MIN_WEIGHT, mob.bulk() - WEIGHT_STEP)))));
			out.add(new Btn(x + 136, y, 20, Component.literal("+"), () -> send(GoreReader.WEIGHT, String.valueOf(mob.bulk() + WEIGHT_STEP))));
			out.add(new Btn(x + 158, y, 40, Component.translatable("screen.jaymospowers.gore_reader.auto_weight"), () -> send(GoreReader.WEIGHT, "")));
			out.add(new Btn(x + 202, y, 110, Component.translatable("screen.jaymospowers.gore_reader.set_body", planName(mob.plan())),
				() -> send(GoreReader.BODY, nextPlan(mob))));
		} else if (weapon != null) {
			out.add(new Btn(x, y, 130, Component.translatable("screen.jaymospowers.gore_reader.set_kind", kindName(weapon.kind())),
				() -> send(GoreReader.WEAPON, nextKind(weapon))));
		}
		return out;
	}

	/** The value after this creature's blood in the cycle: Auto, then each blood, then none, then Auto again. */
	private static String nextBlood(GoreReader.MobRow row) {
		int at = -1;
		if (row.bloodSet()) {
			for (int i = 0; i < BLOOD_COLORS.length; i++) {
				at = BLOOD_COLORS[i] == row.blood() ? i : at;
			}
			at = at < 0 ? BLOOD_COLORS.length - 1 : at;
		}
		int next = row.bloodSet() ? at + 1 : 0;
		if (next >= BLOOD_COLORS.length) {
			return "";
		}
		return BLOOD_COLORS[next] == GoreOverrides.NO_BLOOD ? "none" : String.format("#%06X", BLOOD_COLORS[next]);
	}

	private static String nextPlan(GoreReader.MobRow row) {
		int next = row.planSet() ? row.plan() + 1 : 0;
		return next >= BodyPlan.values().length ? "" : BodyPlan.values()[next].name();
	}

	private static String nextKind(GoreReader.WeaponRow row) {
		if (!row.kindSet()) {
			return WeaponKind.values()[0].name();
		}
		if (row.kind() < 0) {
			return "";
		}
		return row.kind() + 1 >= WeaponKind.values().length ? "none" : WeaponKind.values()[row.kind() + 1].name();
	}

	private void send(int what, String value) {
		if (selected != null) {
			ClientPlayNetworking.send(new GoreReader.Edit(what, selected, value));
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = left() + 8, y = toolsY();
		if (UiStyle.over((int) event.x(), (int) event.y(), x, y, 60, UiStyle.BUTTON_H) || UiStyle.over((int) event.x(), (int) event.y(), x + 64, y, 72, UiStyle.BUTTON_H)) {
			weaponTab = event.x() >= x + 64;
			scroll = 0;
			selected = null;
			rebuild();
			return true;
		}
		int r = rowAt(event.x(), event.y());
		if (r >= 0) {
			selected = idAt(shown.get(r));
			return true;
		}
		for (Btn btn : buttons()) {
			if (UiStyle.over((int) event.x(), (int) event.y(), btn.x(), btn.y(), btn.w(), UiStyle.BUTTON_H)) {
				btn.press().run();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, Math.max(0, shown.size() - visible()));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - visible()));
		int x = left(), y = top(), w = panelW();
		UiStyle.panel(graphics, x, y, w, panelH(), ACCENT);
		UiStyle.header(graphics, font, x, y, w, null, title, Component.translatable("screen.jaymospowers.gore_reader.sub"), ACCENT, 0);
		UiStyle.button(graphics, font, x + 8, toolsY(), 60, UiStyle.BUTTON_H, Component.translatable("screen.jaymospowers.gore_reader.tab_mobs"), ACCENT,
			UiStyle.over(mouseX, mouseY, x + 8, toolsY(), 60, UiStyle.BUTTON_H), weaponTab);
		UiStyle.button(graphics, font, x + 72, toolsY(), 72, UiStyle.BUTTON_H, Component.translatable("screen.jaymospowers.gore_reader.tab_weapons"), ACCENT,
			UiStyle.over(mouseX, mouseY, x + 72, toolsY(), 72, UiStyle.BUTTON_H), !weaponTab);
		int hovered = rowAt(mouseX, mouseY);
		int rx = x + 6, rw = w - 14;
		for (int r = 0; r < visible() && scroll + r < shown.size(); r++) {
			int index = shown.get(scroll + r), ry = listTop() + r * ROW_H;
			boolean picked = idAt(index).equals(selected);
			UiStyle.row(graphics, rx, ry, rx + rw, ry + ROW_H - 1, scroll + r, hovered == scroll + r || picked);
			if (picked) {
				graphics.fill(rx, ry, rx + 2, ry + ROW_H - 1, ACCENT);
			}
			if (weaponTab) {
				weaponRow(graphics, weapons.get(index), rx + 4, ry + 3, rw - 8);
			} else {
				mobRow(graphics, mobs.get(index), rx + 4, ry + 3, rw - 8);
			}
		}
		UiStyle.scrollbar(graphics, x + w - 5, listTop(), visible() * ROW_H, visible(), shown.size(), scroll);
		detail(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** A creature row: its id, then plan, weight, blood colour and name, and "metal". */
	private void mobRow(GuiGraphicsExtractor graphics, GoreReader.MobRow row, int x, int y, int w) {
		int planW = 66, bulkW = 40, bloodW = 70, metalW = 30, idW = w - planW - bulkW - bloodW - metalW - 8;
		UiStyle.line(graphics, font, Component.literal(row.id()), x, y, idW, UiStyle.TEXT);
		int cx = x + idW + 4;
		UiStyle.line(graphics, font, planName(row.plan()), cx, y, planW, row.planSet() ? SET : UiStyle.DIM);
		cx += planW;
		UiStyle.line(graphics, font, Component.literal(bulkText(row.bulk())), cx, y, bulkW, row.bulkSet() ? SET : UiStyle.DIM);
		cx += bulkW;
		if (row.blood() != GoreOverrides.NO_BLOOD) {
			graphics.fill(cx, y, cx + 7, y + 7, 0xFF000000 | row.blood());
		}
		UiStyle.line(graphics, font, bloodName(row.blood()), cx + 10, y, bloodW - 10, row.bloodSet() ? SET : UiStyle.DIM);
		cx += bloodW;
		if (row.metal()) {
			UiStyle.line(graphics, font, Component.translatable("screen.jaymospowers.gore_reader.metal"), cx, y, metalW, UiStyle.DIM);
		}
	}

	/** A weapon row: its id, then its kind, its blows and how deep it cuts. */
	private void weaponRow(GuiGraphicsExtractor graphics, GoreReader.WeaponRow row, int x, int y, int w) {
		int kindW = 70, blowW = 90, depthW = 50, idW = w - kindW - blowW - depthW - 8;
		UiStyle.line(graphics, font, Component.literal(row.id()), x, y, idW, UiStyle.TEXT);
		int cx = x + idW + 4;
		UiStyle.line(graphics, font, kindName(row.kind()), cx, y, kindW, row.kindSet() ? SET : UiStyle.DIM);
		cx += kindW;
		UiStyle.line(graphics, font, blowsOf(row.kind()), cx, y, blowW, UiStyle.DIM);
		cx += blowW;
		UiStyle.line(graphics, font, row.kind() < 0 ? Component.empty() : depthName(row.depth()), cx, y, depthW, UiStyle.DIM);
	}

	/** The strip under the list: what the picked row is, and the buttons that change it. */
	private void detail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left(), y = listBottom() + 4, w = panelW();
		UiStyle.rule(graphics, x + 6, x + w - 6, y);
		GoreReader.MobRow mob = weaponTab ? null : mob();
		GoreReader.WeaponRow weapon = weaponTab ? weapon() : null;
		if (mob == null && weapon == null) {
			UiStyle.line(graphics, font, Component.translatable("screen.jaymospowers.gore_reader.pick"), x + 8, y + 6, w - 16, UiStyle.DIM);
			return;
		}
		UiStyle.line(graphics, font, Component.literal(mob != null ? mob.id() : weapon.id()), x + 8, y + 4, w - 16, ACCENT);
		Component second;
		if (mob != null) {
			List<String> parts = new ArrayList<>();
			BodyPlan plan = BodyPlan.values()[mob.plan()];
			for (int i = 0; i < plan.size(); i++) {
				parts.add(plan.part(i).displayName().getString());
			}
			second = Component.translatable("screen.jaymospowers.gore_reader.parts", String.join(", ", parts));
		} else {
			second = Component.translatable("screen.jaymospowers.gore_reader.blows", blowsOf(weapon.kind()),
				weapon.kind() < 0 ? Component.translatable("screen.jaymospowers.gore_reader.none") : depthName(weapon.depth()));
		}
		UiStyle.line(graphics, font, second, x + 8, y + 15, w - 16, UiStyle.DIM);
		for (Btn btn : buttons()) {
			UiStyle.button(graphics, font, btn.x(), btn.y(), btn.w(), UiStyle.BUTTON_H, btn.label(), ACCENT,
				UiStyle.over(mouseX, mouseY, btn.x(), btn.y(), btn.w(), UiStyle.BUTTON_H), true);
		}
	}
}
