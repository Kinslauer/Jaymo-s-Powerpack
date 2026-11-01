package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import com.jaymospowers.power.chrono.ChronoDirectives;
import com.jaymospowers.power.chrono.ChronoPower;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The powers screen (H). (0.15.147) A vanilla container window in dark tones, with tabs down its left edge:
 * <ul>
 * <li><b>Overview:</b> the power, its fate and mastery, and slots that press Z, C and the costume mask for you.</li>
 * <li><b>Abilities:</b> a slot per key; pick one to read it in full, and Use presses it for you.</li>
 * <li><b>Chrono:</b> (Chrono only) the echo's orders and the three loop slots ({@link ChronoDirectives}).</li>
 * <li><b>Mastery:</b> what's mastered and what comes next.</li>
 * <li><b>Powers:</b> (creative only) every power, to look through and choose.</li>
 * </ul>
 * The inventory has a tab that opens it too ({@link PowerInventoryTab}).
 */
public class PowerScreen extends Screen {
	static final int W = 176, H = 186;
	/** The tabs down the left edge: size, spacing, and where the first one starts below the top. */
	static final int TAB_W = 28, TAB_H = 26, TAB_STEP = 28, TAB_TOP = 8, TAB_OUT = 26;
	static final int SLOT = 18;
	// the vanilla window, dark
	static final int COLOR_EDGE = 0xFF0A0A0E;
	static final int COLOR_LIGHT = 0xFF4A4A52;
	static final int COLOR_SHADE = 0xFF141418;
	static final int COLOR_BODY = 0xFF2B2B2F;
	static final int COLOR_CANVAS = 0xFF1E1E22;
	static final int COLOR_WELL = 0xFF242429;
	static final int COLOR_WELL_LIGHT = 0xFF3E3E46;
	static final int COLOR_TAB = 0xFF222226;
	static final int COLOR_TEXT = 0xFFE0E0E8;
	static final int COLOR_DIM = 0xFF8A8A98;
	static final int COLOR_GOLD = 0xFFFFD54A;
	static final int COLOR_BAR_BACK = 0xFF141418;
	/** The inner canvas: inset from the window's edges, below the title. */
	private static final int CANVAS_X = 7, CANVAS_Y = 18, CANVAS_W = W - 14, CANVAS_H = H - 25;
	private static final int ROW = 18;

	enum Tab {
		OVERVIEW, ABILITIES, CHRONO, MASTERY, POWERS;

		String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private final List<Power> powers = new ArrayList<>(Powers.all());
	/** The power being shown (creative can look through any of them). */
	private @Nullable Power selected;
	private Tab tab = Tab.OVERVIEW;
	/** The key line picked on the Abilities tab. */
	private int picked;
	/** How many rows of the creative list are scrolled past. */
	private int scroll;
	private @Nullable Button useButton, chooseButton, clearButton;

	public PowerScreen() {
		super(Component.translatable("screen.jaymospowers.title"));
		// outside creative, the screen is about your own power only
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		Power own = player == null ? null : Powers.of(player);
		if (player != null && !player.isCreative()) {
			this.powers.clear();
			if (own != null) {
				this.powers.add(own);
			}
		} else {
			// a power fate can't give (Xenoform: the band only) isn't offered to pick, unless it's already yours
			this.powers.removeIf(power -> !power.fated() && power != own);
		}
		this.selected = own != null && this.powers.contains(own) ? own : this.powers.isEmpty() ? null : this.powers.getFirst();
		if (own == null && player != null && player.isCreative()) {
			this.tab = Tab.POWERS;
		}
	}

	/** Opens on {@code power}'s overview (the inventory tab). */
	public PowerScreen(Power power) {
		this();
		if (this.powers.contains(power)) {
			this.selected = power;
			this.tab = Tab.OVERVIEW;
		}
	}

	private boolean creative() {
		return this.minecraft.player != null && this.minecraft.player.isCreative();
	}

	private @Nullable Power current() {
		return this.minecraft.player == null ? null : Powers.of(this.minecraft.player);
	}

	/** The player, for their own power (its lines follow their state); null for the others. */
	private @Nullable Player owner(Power power) {
		return this.minecraft.player != null && power == current() ? this.minecraft.player : null;
	}

	/** The tabs there are for the power shown. */
	private List<Tab> tabs() {
		List<Tab> tabs = new ArrayList<>();
		if (this.selected != null) {
			tabs.add(Tab.OVERVIEW);
			tabs.add(Tab.ABILITIES);
			if (this.selected == ChronoPower.INSTANCE) {
				tabs.add(Tab.CHRONO);
			}
			tabs.add(Tab.MASTERY);
		}
		if (creative()) {
			tabs.add(Tab.POWERS);
		}
		return tabs;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2;
	}

	private int cx() {
		return left() + CANVAS_X;
	}

	private int cy() {
		return top() + CANVAS_Y;
	}

	private List<KeyGuide.Line> lines() {
		return this.selected == null ? List.of() : KeyGuide.lines(this.selected, owner(this.selected));
	}

	@Override
	protected void init() {
		if (!tabs().contains(this.tab)) {
			this.tab = tabs().isEmpty() ? Tab.OVERVIEW : tabs().getFirst();
		}
		this.useButton = null;
		this.chooseButton = null;
		this.clearButton = null;
		int bottom = cy() + CANVAS_H - 20;
		if (this.tab == Tab.ABILITIES) {
			this.useButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.use"), b -> use(this.picked))
				.bounds(cx() + CANVAS_W - 64, bottom, 60, 16).build());
		} else if (this.tab == Tab.POWERS) {
			this.chooseButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.choose"), b -> {
				if (this.selected != null) {
					choose(this.selected.id().toString());
				}
			}).bounds(cx() + CANVAS_W - 64, bottom, 60, 16).build());
			this.clearButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.clear"), b -> choose(""))
				.bounds(cx() + 4, bottom, 90, 16).build());
		}
	}

	private void select(Tab tab) {
		if (this.tab != tab) {
			this.tab = tab;
			rebuildWidgets();
		}
	}

	private void choose(String id) {
		ClientPlayNetworking.send(new PowerPayloads.ChoosePower(id));
		onClose();
	}

	/** Presses a key for you (the screen closes so it lands in the world): {@link JaymosPowersClient#tap}. */
	private void press(int key) {
		onClose();
		JaymosPowersClient.tap(key);
	}

	/** The Abilities tab's Use: the picked line's key, if it's one that can be pressed. */
	private void use(int line) {
		int key = tapKey(line);
		if (key >= 0) {
			press(key);
		}
	}

	/** The key {@link JaymosPowersClient#tap} takes for line {@code i}, or -1 if it can't be pressed from here. */
	private int tapKey(int i) {
		List<KeyGuide.Line> lines = lines();
		Player player = this.minecraft.player;
		if (i < 0 || i >= lines.size() || player == null || this.selected != current() || lines.get(i).locked()) {
			return -1;
		}
		String slot = lines.get(i).slot();
		for (PowerKey key : PowerKey.values()) {
			if (key.id().equals(slot)) {
				return Mastery.isUsable(player, key) ? key.ordinal() : -1;
			}
		}
		return switch (slot) {
			case "mode" -> JaymosPowersClient.TAP_MODE;
			case "ultimate" -> this.selected.ultimateAnswers(player) ? JaymosPowersClient.TAP_ULTIMATE : -1;
			default -> -1;
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- clicks

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int)event.x(), my = (int)event.y();
		List<Tab> tabs = tabs();
		for (int i = 0; i < tabs.size(); i++) {
			if (over(mx, my, left() - TAB_OUT, top() + TAB_TOP + i * TAB_STEP, TAB_OUT, TAB_H)) {
				select(tabs.get(i));
				return true;
			}
		}
		switch (this.tab) {
			case OVERVIEW -> {
				for (int i = 0; i < 3; i++) {
					if (over(mx, my, toggleX(i), toggleY(), SLOT, SLOT) && toggleOpen(i)) {
						toggle(i);
						return true;
					}
				}
			}
			case ABILITIES -> {
				List<KeyGuide.Line> lines = lines();
				for (int i = 0; i < lines.size(); i++) {
					if (over(mx, my, wellX(i), wellY(i), SLOT, SLOT)) {
						this.picked = i;
						return true;
					}
				}
			}
			case CHRONO -> {
				for (int b = 0; b <= ChronoDirectives.CLEAR_LOOP; b++) {
					if (over(mx, my, orderX(b), orderY(b), SLOT, SLOT) && orderOpen(b)) {
						ClientPlayNetworking.send(new ChronoDirectives.Order(b));
						return true;
					}
				}
			}
			case POWERS -> {
				for (int i = 0; i < listRows(); i++) {
					int n = i + this.scroll;
					if (n < this.powers.size() && over(mx, my, cx() + 2, cy() + 2 + i * ROW, CANVAS_W - 4, ROW)) {
						this.selected = this.powers.get(n);
						this.picked = 0;
						return true;
					}
				}
			}
			default -> {
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.tab == Tab.POWERS) {
			this.scroll = Mth.clamp(this.scroll - (int)Math.signum(scrollY), 0, Math.max(0, this.powers.size() - listRows()));
		}
		return true;
	}

	static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		List<Tab> tabs = tabs();
		// the tabs not picked sit behind the window
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i) != this.tab) {
				tab(graphics, tabs.get(i), i, false, mouseX, mouseY);
			}
		}
		frame(graphics, x, y, W, H);
		recess(graphics, cx(), cy(), CANVAS_W, CANVAS_H);
		int picked = tabs.indexOf(this.tab);
		if (picked >= 0) {
			tab(graphics, this.tab, picked, true, mouseX, mouseY);
		}
		graphics.text(this.font, Component.translatable("screen.jaymospowers.tab." + this.tab.id()), x + 8, y + 6, COLOR_TEXT, false);
		Power power = this.selected;
		if (power == null && this.tab != Tab.POWERS) {
			wrapped(graphics, Component.translatable("screen.jaymospowers.dormant"), cx() + 4, cy() + 4, CANVAS_W - 8, 6, COLOR_DIM);
		} else {
			switch (this.tab) {
				case OVERVIEW -> overview(graphics, power, mouseX, mouseY);
				case ABILITIES -> abilities(graphics, power, mouseX, mouseY);
				case CHRONO -> chrono(graphics, mouseX, mouseY);
				case MASTERY -> mastery(graphics, power);
				case POWERS -> list(graphics, mouseX, mouseY);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** Vanilla's raised window: a dark edge, a light bevel on top and left, a shadow on bottom and right. */
	static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, COLOR_EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_SHADE);
		graphics.fill(x + 1, y + 1, x + w - 3, y + h - 3, COLOR_LIGHT);
		graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, COLOR_BODY);
	}

	/** A sunken area: shadow on top and left, light on bottom and right. */
	static void recess(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, COLOR_WELL_LIGHT);
		graphics.fill(x, y, x + w - 1, y + h - 1, COLOR_SHADE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_CANVAS);
	}

	/** An 18x18 item slot well. */
	static void well(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_WELL_LIGHT);
		graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, COLOR_SHADE);
		graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, COLOR_WELL);
	}

	/** A tab on the left edge; the picked one runs into the window. */
	private void tab(GuiGraphicsExtractor graphics, Tab tab, int i, boolean picked, int mouseX, int mouseY) {
		int x = left() - TAB_OUT, y = top() + TAB_TOP + i * TAB_STEP;
		graphics.fill(x, y, x + TAB_W, y + TAB_H, COLOR_EDGE);
		graphics.fill(x + 1, y + 1, x + TAB_W, y + TAB_H - 1, picked ? COLOR_LIGHT : COLOR_SHADE);
		graphics.fill(x + 3, y + 3, x + TAB_W, y + TAB_H - 3, picked ? COLOR_BODY : COLOR_TAB);
		if (picked) {
			// merge into the window: over its left edge
			graphics.fill(x + TAB_W - 2, y + 1, left() + 3, y + TAB_H - 1, COLOR_BODY);
		}
		int ix = x + 6, iy = y + 5;
		if (tab == Tab.OVERVIEW && this.selected != null) {
			graphics.item(this.selected.icon(), ix, iy);
		} else {
			icon(graphics, tab.id(), ix, iy);
		}
		if (over(mouseX, mouseY, x, y, TAB_OUT, TAB_H)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.jaymospowers.tab." + tab.id()), mouseX, mouseY);
		}
	}

	/** One of our 16x16 GUI icons ({@code textures/gui/power_screen/<name>.png}). */
	static void icon(GuiGraphicsExtractor graphics, String name, int x, int y) {
		Identifier id = JaymosPowers.id("textures/gui/power_screen/" + name + ".png");
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
	}

	/** One line of text; one too long for {@code width} slides back and forth inside it ({@link UiStyle#slide}). */
	private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		line(graphics, text, x, y, width, color, false);
	}

	private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color, boolean shadow) {
		String s = text.getString();
		int over = this.font.width(s) - width;
		if (over <= 0) {
			graphics.text(this.font, s, x, y, color, shadow);
			return;
		}
		graphics.enableScissor(x, y - 1, x + Math.max(0, width), y + 10);
		graphics.text(this.font, s, x - UiStyle.slide(over, UiStyle.SLIDE_MS_PER_PX, UiStyle.SLIDE_PAUSE_MS), y, color, shadow);
		graphics.disableScissor();
	}

	/** The same, centred on {@code centre}, with a shadow (as {@code centeredText}). */
	private void lineCentred(GuiGraphicsExtractor graphics, Component text, int centre, int y, int width, int color) {
		int textW = this.font.width(text.getString());
		line(graphics, text, textW <= width ? centre - textW / 2 : centre - width / 2, y, width, color, true);
	}

	/** Text wrapped to {@code width} in {@code lines} lines, scrolling through any more ({@link UiStyle#wrapped}). Returns the lines used. */
	private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int lines, int color) {
		return UiStyle.wrapped(graphics, this.font, text, x, y, width, lines, 9, color, false);
	}

	private void bar(GuiGraphicsExtractor graphics, int x, int y, int w, float progress, int color) {
		graphics.fill(x, y, x + w, y + 3, COLOR_BAR_BACK);
		graphics.fill(x, y, x + Math.round(w * Mth.clamp(progress, 0.0F, 1.0F)), y + 3, color);
	}

	private static int accent(Power power) {
		return 0xFF000000 | power.color();
	}

	// ---------------------------------------------------------------- overview

	private void overview(GuiGraphicsExtractor graphics, Power power, int mouseX, int mouseY) {
		int x = cx() + 4, y = cy() + 4, w = CANVAS_W - 8;
		Player player = owner(power);
		well(graphics, x, y);
		graphics.item(power.icon(), x + 1, y + 1);
		Component name = player != null ? PowerHud.title(power, Fate.state(player), player) : power.name();
		line(graphics, name, x + 22, y + 1, w - 22, accent(power));
		// the fate badge
		Component badge;
		int badgeColor;
		if (player == null) {
			badge = Component.translatable(creative() ? "screen.jaymospowers.creative_hint" : "screen.jaymospowers.yours");
			badgeColor = COLOR_DIM;
		} else if (Fate.isAscended(player)) {
			badge = Component.translatable(Fate.isCorrupted(player) ? "screen.jaymospowers.badge.ascended_corrupted" : "screen.jaymospowers.badge.ascended");
			badgeColor = COLOR_GOLD;
		} else if (Fate.isCorrupted(player)) {
			badge = Component.translatable("screen.jaymospowers.badge.corrupted");
			badgeColor = 0xFFD05050;
		} else {
			badge = Component.translatable("screen.jaymospowers.badge.mortal");
			badgeColor = COLOR_DIM;
		}
		line(graphics, badge, x + 22, y + 10, w - 22, badgeColor);
		y += 24;
		float progress = player != null && !player.isCreative() ? Mastery.progress(player) : -2.0F;
		if (progress > -2.0F) {
			Component label = progress < 0.0F ? rankLabel(player)
				: Component.translatable("screen.jaymospowers.mastery_bar", Math.round(progress * 100.0F));
			line(graphics, label, x, y, w, progress < 0.0F ? accent(power) : COLOR_TEXT);
			bar(graphics, x, y + 10, w, progress < 0.0F ? rankBar(player) : progress, accent(power));
			y += 18;
		}
		wrapped(graphics, power.description(), x, y, w, (toggleY() - 6 - y) / 9, COLOR_DIM);
		// the slots that press keys for you
		if (player == null) {
			return;
		}
		Component hover = null;
		for (int i = 0; i < 3; i++) {
			int tx = toggleX(i), ty = toggleY();
			well(graphics, tx, ty);
			ItemStack stack = new ItemStack(i == 0 ? Items.ENDER_EYE : i == 1 ? Items.NETHER_STAR : Items.CARVED_PUMPKIN);
			graphics.item(stack, tx + 1, ty + 1);
			boolean open = toggleOpen(i);
			if (!open) {
				graphics.fill(tx + 1, ty + 1, tx + SLOT - 1, ty + SLOT - 1, 0xA0141418);
			} else if (over(mouseX, mouseY, tx, ty, SLOT, SLOT)) {
				graphics.fill(tx + 1, ty + 1, tx + SLOT - 1, ty + SLOT - 1, 0x60FFFFFF);
			}
			String id = i == 0 ? "mode" : i == 1 ? "ultimate" : "mask";
			Component label = i == 0 ? modeLabel() : Component.translatable("screen.jaymospowers.toggle." + id);
			// (0.16.6) a power's Mode is named for what it does, so cut a long name to the slot's room
			int room = (CANVAS_W - 3 * SLOT) / 4 + SLOT - 2;
			lineCentred(graphics, label, tx + SLOT / 2, ty + SLOT + 2, room, open ? COLOR_TEXT : COLOR_DIM);
			if (over(mouseX, mouseY, tx, ty, SLOT, SLOT)) {
				hover = i == 0 ? modeHover(open) : Component.translatable("screen.jaymospowers.toggle." + id + (open ? ".desc" : ".locked"));
			}
		}
		if (hover != null) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(hover, 160), mouseX, mouseY);
		}
	}

	private int toggleX(int i) {
		int gap = (CANVAS_W - 3 * SLOT) / 4;
		return cx() + gap + i * (SLOT + gap);
	}

	private int toggleY() {
		return cy() + CANVAS_H - SLOT - 14;
	}

	/** (0.16.6) The power's own name for the screen's Mode slot (lang {@code power.<id>.toggle.mode}), or null. */
	private @Nullable String ownModeKey() {
		if (this.selected == null) {
			return null;
		}
		String key = "power." + this.selected.id().getNamespace() + "." + this.selected.id().getPath() + ".toggle.mode";
		return Language.getInstance().has(key) ? key : null;
	}

	/** (0.16.6) The Mode key's line in the key guide (its name and what it does), or null when this power has no mode. */
	private KeyGuide.@Nullable Line modeLine() {
		for (KeyGuide.Line line : lines()) {
			if ("mode".equals(line.slot())) {
				return line;
			}
		}
		return null;
	}

	/**
	 * (0.16.6) Andy: "the mode button should change depending on the power, because not all of them have a mode". Named
	 * for what the power's Mode does (Web Zip, Pocket Dimension...), or the power's own slot (Z Fighter: Fusion Dance).
	 */
	private Component modeLabel() {
		String own = ownModeKey();
		if (own != null) {
			return Component.translatable(own);
		}
		KeyGuide.Line line = modeLine();
		if (line == null) {
			return Component.translatable("screen.jaymospowers.toggle.mode.none");
		}
		return line.name() != null ? line.name() : Component.translatable("screen.jaymospowers.toggle.mode");
	}

	private Component modeHover(boolean open) {
		if (this.selected != current()) {
			return Component.translatable("screen.jaymospowers.toggle.mode.locked");
		}
		String own = ownModeKey();
		if (own != null) {
			return Component.translatable(own + ".desc");
		}
		KeyGuide.Line line = modeLine();
		if (line == null) {
			return Component.translatable("screen.jaymospowers.toggle.mode.none.desc");
		}
		return open ? line.text() : Component.translatable("screen.jaymospowers.toggle.mode.unlearned");
	}

	/** Whether toggle {@code i} does anything: Z for your power, C once it answers, the mask while a costume has one. */
	private boolean toggleOpen(int i) {
		Player player = this.minecraft.player;
		if (player == null || this.selected == null || this.selected != current()) {
			return false;
		}
		return switch (i) {
			case 0 -> {
				KeyGuide.Line line = modeLine();
				yield ownModeKey() != null || line != null && !line.locked();
			}
			case 1 -> this.selected.ultimateAnswers(player);
			default -> {
				com.jaymospowers.costume.CostumeDesign worn = com.jaymospowers.costume.Costume.worn(player);
				yield worn != null && worn.mask() != com.jaymospowers.costume.CostumeDesign.Mask.NONE;
			}
		};
	}

	private void toggle(int i) {
		switch (i) {
			case 0 -> {
				PowerClient client = this.selected == null ? null : PowerClients.get(this.selected.id());
				if (client != null && this.minecraft.player != null && ownModeKey() != null) {
					onClose();
					client.screenMode(this.minecraft.player);
				} else {
					press(JaymosPowersClient.TAP_MODE);
				}
			}
			case 1 -> press(JaymosPowersClient.TAP_ULTIMATE);
			default -> com.jaymospowers.client.costume.CostumeClient.toggleMask();
		}
	}

	// ---------------------------------------------------------------- abilities

	/** The key wells: the four abilities on the first row, the other keys from the second. */
	private int wellX(int i) {
		int col = i < 4 ? i : (i - 4) % 7;
		return cx() + 4 + col * (SLOT + 4);
	}

	private int wellY(int i) {
		int row = i < 4 ? 0 : 1 + (i - 4) / 7;
		return cy() + 4 + row * (SLOT + 6);
	}

	private void abilities(GuiGraphicsExtractor graphics, Power power, int mouseX, int mouseY) {
		List<KeyGuide.Line> lines = lines();
		if (lines.isEmpty()) {
			return;
		}
		this.picked = Mth.clamp(this.picked, 0, lines.size() - 1);
		Player player = owner(power);
		PowerClient client = player instanceof net.minecraft.client.player.LocalPlayer local ? JaymosPowersClient.active(local) : null;
		int shown = this.picked;
		int bottom = 0;
		for (int i = 0; i < lines.size(); i++) {
			KeyGuide.Line line = lines.get(i);
			int x = wellX(i), y = wellY(i);
			bottom = Math.max(bottom, y + SLOT + 4);
			well(graphics, x, y);
			lineCentred(graphics, line.key(), x + SLOT / 2, y + 5, SLOT - 4, line.locked() ? COLOR_DIM : line.color());
			// a cooldown greys the slot from the top, as vanilla's item cooldowns do
			PowerKey ability = abilityKey(line);
			float cooldown = client != null && ability != null ? client.cooldown((net.minecraft.client.player.LocalPlayer)player, ability) : 0.0F;
			if (cooldown > 0.0F) {
				graphics.fill(x + 1, y + 1, x + SLOT - 1, y + 1 + Math.round((SLOT - 2) * cooldown), 0x90FFFFFF);
			}
			if (line.locked()) {
				graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x90141418);
			}
			if (ability != null && player != null && !player.isCreative()) {
				// a star under each ability: gold once mastered
				int sx = x + SLOT / 2 - 1;
				graphics.fill(sx, y + SLOT + 1, sx + 3, y + SLOT + 3, Mastery.isUnlocked(player, ability) ? COLOR_GOLD : COLOR_WELL_LIGHT);
			}
			if (i == this.picked) {
				graphics.outline(x - 1, y - 1, SLOT + 2, SLOT + 2, accent(power));
			} else if (over(mouseX, mouseY, x, y, SLOT, SLOT)) {
				graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x40FFFFFF);
			}
			if (over(mouseX, mouseY, x, y, SLOT, SLOT)) {
				shown = i;
			}
		}
		// the picked (or hovered) key in full
		KeyGuide.Line line = lines.get(shown);
		int x = cx() + 4, y = bottom + 2, w = CANVAS_W - 8;
		graphics.fill(x, y - 3, x + w, y - 2, COLOR_WELL_LIGHT);
		Component name = line.name() != null ? line.name() : line.key();
		line(graphics, Component.literal("[").append(line.key()).append("] ").append(name), x, y, w, line.color());
		int room = (cy() + CANVAS_H - 24 - (y + 11)) / 9;
		wrapped(graphics, line.text(), x, y + 11, w, room, COLOR_TEXT);
		if (this.useButton != null) {
			this.useButton.active = tapKey(this.picked) >= 0;
		}
	}

	private static @Nullable PowerKey abilityKey(KeyGuide.Line line) {
		for (PowerKey key : PowerKey.values()) {
			if (key.id().equals(line.slot())) {
				return key;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- chrono

	/** The order slots: the directives and Record on the first row, the loop slots and Clear on the second. */
	private int orderX(int button) {
		int col = button <= ChronoDirectives.RECORD_BUTTON ? button : button - ChronoDirectives.SLOT_0;
		return cx() + 8 + col * (SLOT + 12);
	}

	private int orderY(int button) {
		return cy() + (button <= ChronoDirectives.RECORD_BUTTON ? 16 : 56);
	}

	private int shownOrders() {
		Integer shown = this.minecraft.player == null ? null : this.minecraft.player.getAttached(ChronoDirectives.SHOWN);
		return shown == null ? 0 : shown;
	}

	private boolean commanding() {
		Player player = this.minecraft.player;
		return player != null && !player.isCreative() && current() == ChronoPower.INSTANCE && ChronoDirectives.commandable(player);
	}

	private int orderTier(int button) {
		return button >= ChronoDirectives.RECORD_BUTTON ? ChronoDirectives.RECORD_TIER : ChronoDirectives.Directive.values()[button].tier();
	}

	/** Whether order button {@code b} can be pressed now: earned, and not the order already standing. */
	private boolean orderOpen(int b) {
		if (!commanding()) {
			return false;
		}
		int value = shownOrders();
		int unlocked = (value >> ChronoDirectives.UNLOCKED_SHIFT) & 0x7;
		if (orderTier(b) > unlocked) {
			return false;
		}
		boolean recording = (value & ChronoDirectives.RECORDING_FLAG) != 0;
		if (b < ChronoDirectives.RECORD_BUTTON) {
			return recording || b != (value & 0xFF);
		}
		return true;
	}

	private void chrono(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = cx() + 4, w = CANVAS_W - 8;
		if (!commanding()) {
			Player player = this.minecraft.player;
			String why = current() != ChronoPower.INSTANCE || player == null || player.isCreative() ? "screen.jaymospowers.chrono.not_yours"
				: "screen.jaymospowers.chrono.not_timeless";
			wrapped(graphics, Component.translatable(why), x, cy() + 4, w, 10, COLOR_DIM);
			return;
		}
		int value = shownOrders();
		boolean recording = (value & ChronoDirectives.RECORDING_FLAG) != 0;
		int directive = value & 0xFF;
		int unlocked = (value >> ChronoDirectives.UNLOCKED_SHIFT) & 0x7;
		int slot = (value >> ChronoDirectives.SLOT_SHIFT) & 0x3;
		int filled = (value >> ChronoDirectives.FILLED_SHIFT) & 0x7;
		graphics.text(this.font, Component.translatable("screen.jaymospowers.chrono.orders"), x, cy() + 4, COLOR_DIM, false);
		graphics.text(this.font, Component.translatable("screen.jaymospowers.chrono.loops"), x, cy() + 44, COLOR_DIM, false);
		Component hover = null;
		for (int b = 0; b <= ChronoDirectives.CLEAR_LOOP; b++) {
			int bx = orderX(b), by = orderY(b);
			well(graphics, bx, by);
			boolean earned = orderTier(b) <= unlocked;
			String name;
			Component desc;
			if (b < ChronoDirectives.RECORD_BUTTON) {
				name = ChronoDirectives.Directive.values()[b].name().toLowerCase(Locale.ROOT);
				icon(graphics, name, bx + 1, by + 1);
				desc = Component.translatable("screen.jaymospowers.order." + name + ".desc");
				if (b == directive && !recording) {
					graphics.outline(bx - 1, by - 1, SLOT + 2, SLOT + 2, 0xFF000000 | ChronoPower.INSTANCE.color());
				}
			} else if (b == ChronoDirectives.RECORD_BUTTON) {
				name = recording ? "stop" : "record";
				icon(graphics, "record", bx + 1, by + 1);
				desc = Component.translatable("screen.jaymospowers.order.record.desc");
				if (recording && (this.minecraft.level == null || this.minecraft.level.getGameTime() / 10 % 2 == 0)) {
					graphics.outline(bx - 1, by - 1, SLOT + 2, SLOT + 2, 0xFFE04040);
				}
			} else if (b < ChronoDirectives.CLEAR_LOOP) {
				int n = b - ChronoDirectives.SLOT_0;
				boolean has = (filled & 1 << n) != 0;
				name = "slot";
				if (has) {
					icon(graphics, "loop", bx + 1, by + 1);
				}
				graphics.text(this.font, String.valueOf(n + 1), bx + SLOT - 6, by + SLOT - 8, COLOR_TEXT, true);
				desc = has ? Component.translatable("screen.jaymospowers.order.slot.filled", n + 1)
					: Component.translatable("screen.jaymospowers.order.slot.empty", n + 1);
				if (n == slot) {
					graphics.outline(bx - 1, by - 1, SLOT + 2, SLOT + 2, 0xFF000000 | ChronoPower.INSTANCE.color());
				}
			} else {
				name = "clear";
				graphics.item(new ItemStack(Items.BARRIER), bx + 1, by + 1);
				desc = Component.translatable("screen.jaymospowers.order.clear.desc");
			}
			if (!earned) {
				graphics.fill(bx + 1, by + 1, bx + SLOT - 1, by + SLOT - 1, 0xA0141418);
				desc = Component.translatable("screen.jaymospowers.order.locked", orderTier(b));
			} else if (over(mouseX, mouseY, bx, by, SLOT, SLOT) && orderOpen(b)) {
				graphics.fill(bx + 1, by + 1, bx + SLOT - 1, by + SLOT - 1, 0x40FFFFFF);
			}
			if (b <= ChronoDirectives.RECORD_BUTTON) {
				lineCentred(graphics, Component.translatable("screen.jaymospowers.order." + name), bx + SLOT / 2, by + SLOT + 2, SLOT + 10,
					earned ? COLOR_TEXT : COLOR_DIM);
			}
			if (over(mouseX, mouseY, bx, by, SLOT, SLOT)) {
				hover = desc;
			}
		}
		// what's going on now, or the hovered order in full
		int y = cy() + 84;
		graphics.fill(x, y - 3, x + w, y - 2, COLOR_WELL_LIGHT);
		Component now = recording ? Component.translatable("screen.jaymospowers.chrono.recording", slot + 1)
			: Component.translatable("screen.jaymospowers.chrono.now",
				Component.translatable("screen.jaymospowers.order." + ChronoDirectives.Directive.values()[Math.min(directive, 3)].name().toLowerCase(Locale.ROOT)));
		wrapped(graphics, hover != null ? hover : now, x, y, w, (cy() + CANVAS_H - 4 - y) / 9, hover != null ? COLOR_TEXT : COLOR_DIM);
	}

	// ---------------------------------------------------------------- mastery

	/** (0.19.29) Past the four abilities: the Mastery rank and the way to the next. */
	private static Component rankLabel(Player player) {
		float next = Mastery.rankProgress(player);
		Component rank = com.jaymospowers.core.Training.rankName(Mastery.rank(player));
		if (Mastery.rank(player) == 0) {
			rank = Component.translatable("screen.jaymospowers.mastered");
		}
		return next < 0.0F ? Component.translatable("training.jaymospowers.rank_max", rank)
			: Component.translatable("training.jaymospowers.rank_bar", rank, Math.round(next * 100.0F));
	}

	private static float rankBar(Player player) {
		float next = Mastery.rankProgress(player);
		return next < 0.0F ? 1.0F : next;
	}

	private void mastery(GuiGraphicsExtractor graphics, Power power) {
		int x = cx() + 4, y = cy() + 4, w = CANVAS_W - 8;
		Player player = owner(power);
		if (player == null || player.isCreative()) {
			wrapped(graphics, Component.translatable("screen.jaymospowers.mastery.creative"), x, y, w, 6, COLOR_DIM);
			return;
		}
		int unlocked = Mastery.unlocked(player);
		line(graphics, Component.translatable("screen.jaymospowers.mastery.count", unlocked, Mastery.ALL), x, y, w, COLOR_TEXT);
		float progress = Mastery.progress(player);
		y += 11;
		if (progress >= 0.0F) {
			line(graphics, Component.translatable("screen.jaymospowers.mastery_bar", Math.round(progress * 100.0F)), x, y, w, COLOR_DIM);
			bar(graphics, x, y + 10, w, progress, accent(power));
		} else {
			line(graphics, rankLabel(player), x, y, w, accent(power));
			bar(graphics, x, y + 10, w, rankBar(player), accent(power));
		}
		y += 20;
		// each ability in the order they come
		List<KeyGuide.Line> lines = KeyGuide.lines(power, null);
		PowerKey[] order = power.unlockOrder();
		for (int i = 0; i < order.length; i++) {
			PowerKey key = order[i];
			boolean done = Mastery.isUnlocked(player, key);
			well(graphics, x, y);
			graphics.centeredText(this.font, JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage(), x + SLOT / 2, y + 5, done ? COLOR_GOLD : COLOR_DIM);
			Component name = null;
			for (KeyGuide.Line line : lines) {
				if (line.slot().equals(key.id())) {
					name = line.name() != null ? line.name() : line.text();
				}
			}
			line(graphics, name == null ? Component.literal("?") : name, x + 22, y + 1, w - 22, done ? COLOR_TEXT : COLOR_DIM);
			Component state = done ? Component.translatable("screen.jaymospowers.mastery.done")
				: Component.translatable("screen.jaymospowers.mastery.at", Mastery.UNLOCK_AT[Math.min(i + 1, Mastery.UNLOCK_AT.length - 1)]);
			line(graphics, state, x + 22, y + 10, w - 22, done ? COLOR_GOLD : COLOR_DIM);
			y += SLOT + 4;
		}
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			wrapped(graphics, Component.translatable("screen.jaymospowers.mastery.skill_tree"), x, y + 2, w, 2, COLOR_DIM);
		}
	}

	// ---------------------------------------------------------------- powers (creative)

	private int listRows() {
		return (CANVAS_H - 26) / ROW;
	}

	private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Power current = current();
		int rows = listRows();
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.powers.size() - rows));
		for (int i = 0; i < rows && i + this.scroll < this.powers.size(); i++) {
			Power power = this.powers.get(i + this.scroll);
			int x = cx() + 2, y = cy() + 2 + i * ROW;
			boolean shown = power == this.selected;
			if (shown || over(mouseX, mouseY, x, y, CANVAS_W - 4, ROW)) {
				graphics.fill(x, y, x + CANVAS_W - 4, y + ROW, shown ? 0x40FFFFFF : 0x20FFFFFF);
			}
			if (shown) {
				graphics.fill(x, y, x + 2, y + ROW, accent(power));
			}
			graphics.item(power.icon(), x + 3, y + 1);
			line(graphics, power.name(), x + 22, y + 5, CANVAS_W - 30, power == current ? accent(power) : shown ? COLOR_TEXT : COLOR_DIM);
		}
		if (this.chooseButton != null) {
			boolean active = this.selected == current;
			this.chooseButton.setMessage(Component.translatable(active ? "screen.jaymospowers.current" : "screen.jaymospowers.choose"));
			this.chooseButton.active = this.selected != null && !active;
		}
		if (this.clearButton != null) {
			this.clearButton.active = current != null;
		}
	}
}
