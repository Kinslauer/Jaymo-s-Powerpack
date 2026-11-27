package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Choices;
import com.jaymoskingdoms.MagicMenu;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Magic wheel (the Magic key, or the Magic page of the H menu), in the symbiote wheel's look. The first ring is the kinds of
 * magic the caster has (everyday spells, rituals, a Sorcerer's kept spells, the choices some spells take); click one and its
 * spells fan round the ring. Point at a spell to read it in full in the info box beside the wheel (as tall as its text needs, so
 * nothing scrolls), click to cast. The middle, right click or Backspace goes back to the kinds. Whisper opens a line to type in;
 * Enter sends. A changed choice keeps the wheel open on the Choices ring (the server sends the screen back).
 */
final class MagicScreen extends Screen {
	private static final int RADIUS = 72, TILE = 26, DEAD_ZONE = 26, PER_PAGE = 12, INFO_MAX_W = 260, INFO_MIN_W = 150, GAP = 14;
	private static final int ACCENT = 0xFFB08CFF;

	/** The kinds, in ring order. */
	private enum Kind {
		EVERYDAY(Items.TORCH), RITUALS(Items.WRITABLE_BOOK), SORCERY(Items.BLAZE_ROD), CHOICES(Items.COMPASS);

		final Item icon;

		Kind(Item icon) {
			this.icon = icon;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** One spell on the ring: its name, what it does, what it sends, its icon. */
	private record Entry(Component name, Component desc, String pick, ItemStack icon) {
	}

	/** The ring last left open, so a changed choice (the server reopens the screen) comes back to it. */
	private static @Nullable Kind lastKind;

	private final List<String> cantrips, rituals, choices, spells;
	private final List<Kind> kinds = new ArrayList<>();
	/** The open ring: null shows the kinds. */
	private @Nullable Kind kind;
	private int page, selected = -1;
	private @Nullable EditBox whisper;

	MagicScreen(List<String> cantrips, List<String> rituals, List<String> choices, List<String> spells) {
		super(Component.translatable("gui.jaymoskingdoms.magic.title"));
		this.cantrips = cantrips;
		this.rituals = rituals;
		this.choices = choices;
		this.spells = spells;
		this.kinds.add(Kind.EVERYDAY);
		if (!rituals.isEmpty()) {
			this.kinds.add(Kind.RITUALS);
		}
		if (!spells.isEmpty()) {
			this.kinds.add(Kind.SORCERY);
		}
		if (!choices.isEmpty()) {
			this.kinds.add(Kind.CHOICES);
		}
		// one kind only: straight to its spells
		this.kind = lastKind != null && this.kinds.contains(lastKind) ? lastKind : this.kinds.size() == 1 ? Kind.EVERYDAY : null;
		lastKind = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// no blur: the world stays in view, as on the other wheels
	}

	// ---------------------------------------------------------------- what the rings hold

	private List<Entry> entries(Kind k) {
		List<Entry> out = new ArrayList<>();
		switch (k) {
			case EVERYDAY -> {
				for (String id : this.cantrips) {
					out.add(new Entry(Component.translatable("magic.jaymoskingdoms.cantrip." + id), Component.translatable("magic.jaymoskingdoms.cantrip." + id + ".desc"),
						id, new ItemStack(cantripIcon(id))));
				}
			}
			case RITUALS -> {
				for (String id : this.rituals) {
					out.add(new Entry(Component.translatable("class.jaymoskingdoms.option." + id), Component.translatable("class.jaymoskingdoms.option." + id + ".desc"),
						id, new ItemStack(spellIcon(id))));
				}
			}
			case SORCERY -> {
				for (String id : this.spells) {
					out.add(new Entry(Component.translatable("class.jaymoskingdoms.option." + id), Component.translatable("class.jaymoskingdoms.option." + id + ".desc"),
						"cast." + id, new ItemStack(spellIcon(id))));
				}
			}
			case CHOICES -> {
				for (String choice : this.choices) {
					int eq = choice.indexOf('=');
					String key = choice.substring(0, eq), option = choice.substring(eq + 1);
					Component name = Component.translatable("magic.jaymoskingdoms.choice", Component.translatable("magic.jaymoskingdoms.choice." + key),
						Component.translatable("magic.jaymoskingdoms.choice." + key + "." + option));
					out.add(new Entry(name, choiceDesc(key), "choose." + key, new ItemStack(choiceIcon(key, option))));
				}
			}
		}
		return out;
	}

	/** What a choice is for: the spell it sets, and the options a click steps through. */
	private static Component choiceDesc(String key) {
		for (Choices.Choice c : Choices.ALL) {
			if (c.key().equals(key)) {
				MutableComponent options = Component.empty();
				for (int i = 0; i < c.options().size(); i++) {
					if (i > 0) {
						options.append(", ");
					}
					options.append(Component.translatable("magic.jaymoskingdoms.choice." + key + "." + c.options().get(i)));
				}
				return Component.translatable("gui.jaymoskingdoms.magic.choice_desc", Component.translatable("class.jaymoskingdoms.option." + c.spell() + ".desc"), options);
			}
		}
		return Component.translatable("magic.jaymoskingdoms.choice." + key + ".desc");
	}

	private static Item cantripIcon(String id) {
		return switch (id) {
			case "light" -> Items.TORCH;
			case "mend" -> Items.ANVIL;
			case "clean" -> Items.WATER_BUCKET;
			case "warm" -> Items.CAMPFIRE;
			case "chill" -> Items.SNOWBALL;
			case "whisper" -> Items.ECHO_SHARD;
			case "weather" -> Items.SUNFLOWER;
			default -> Items.PAPER;
		};
	}

	private static Item spellIcon(String id) {
		return switch (id) {
			case "dispel" -> Items.MILK_BUCKET;
			case "ward_line" -> Items.STRING;
			case "find_magic" -> Items.AMETHYST_SHARD;
			case "identify" -> Items.SPYGLASS;
			case "speak_animals" -> Items.WHEAT;
			case "control_weather" -> Items.WIND_CHARGE;
			case "find_familiar" -> Items.FEATHER;
			case "call_familiar" -> Items.BONE_MEAL;
			case "keeping" -> Items.ROTTEN_FLESH;
			case "glass_shards", "glass_spires" -> Items.GLASS_PANE;
			case "glass_skin" -> Items.TINTED_GLASS;
			case "molten_glass" -> Items.MAGMA_CREAM;
			case "fire_ring", "fire_pillars", "fire_lance" -> Items.FIRE_CHARGE;
			case "black_flame" -> Items.SOUL_TORCH;
			case "storm_cloak" -> Items.TRIDENT;
			default -> Items.ENCHANTED_BOOK;
		};
	}

	private static Item choiceIcon(String key, String option) {
		return switch (key) {
			case "command" -> Items.GOAT_HORN;
			case "suggestion" -> Items.BELL;
			case "weather" -> option.equals("clear") ? Items.SUNFLOWER : option.equals("rain") ? Items.WATER_BUCKET : Items.TRIDENT;
			case "familiar" -> switch (option) {
				case "owl", "raven" -> Items.FEATHER;
				case "cat" -> Items.COD;
				case "rat" -> Items.WHEAT_SEEDS;
				case "frog" -> Items.SLIME_BALL;
				default -> Items.LEAD;
			};
			case "send" -> Items.ENDER_EYE;
			default -> Items.COMPASS;
		};
	}

	/** The tiles round the ring now: the kinds, or this page of the open kind's spells. */
	private int count() {
		if (this.kind == null) {
			return this.kinds.size();
		}
		List<Entry> all = entries(this.kind);
		return Math.max(0, Math.min(PER_PAGE, all.size() - this.page * PER_PAGE));
	}

	private @Nullable Entry entry(int i) {
		if (this.kind == null || i < 0) {
			return null;
		}
		List<Entry> all = entries(this.kind);
		int at = this.page * PER_PAGE + i;
		return at < all.size() ? all.get(at) : null;
	}

	private int pages() {
		return this.kind == null ? 1 : Math.max(1, (entries(this.kind).size() + PER_PAGE - 1) / PER_PAGE);
	}

	// ---------------------------------------------------------------- layout

	/** Where the wheel's middle sits: left of the info box when the screen is wide enough, else centred with the box under it. */
	private boolean side() {
		return this.width >= 2 * (RADIUS + TILE) + GAP + INFO_MIN_W + 24;
	}

	private int outer() {
		return RADIUS + TILE / 2 + 6;
	}

	private int infoW() {
		return side() ? Math.min(INFO_MAX_W, this.width - 2 * outer() - GAP - 24) : Math.min(INFO_MAX_W, this.width - 16);
	}

	private int cx() {
		return side() ? (this.width - 2 * outer() - GAP - infoW()) / 2 + outer() : this.width / 2;
	}

	private int cy() {
		int top = 4 + MenuCompat.STRIP + outer();
		return side() ? Math.max(top, this.height / 2) : Math.max(top, Math.min(this.height / 2 - 30, this.height - outer() - 70));
	}

	private int infoX() {
		return side() ? cx() + outer() + GAP : this.width / 2 - infoW() / 2;
	}

	// ---------------------------------------------------------------- input

	@Override
	protected void init() {
		String typed = this.whisper != null ? this.whisper.getValue() : "";
		boolean open = this.whisper != null && this.whisper.visible;
		this.whisper = new EditBox(this.font, infoX(), 0, infoW(), 16, Component.translatable("gui.jaymoskingdoms.magic.whisper_hint"));
		this.whisper.setMaxLength(MagicMenu.WHISPER_MAX);
		this.whisper.setHint(Component.translatable("gui.jaymoskingdoms.magic.whisper_hint"));
		this.whisper.setValue(typed);
		this.whisper.visible = open;
		addRenderableWidget(this.whisper);
		if (open) {
			setFocused(this.whisper);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.whisper != null && this.whisper.visible && this.whisper.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			back();
			return true;
		}
		if (this.selected < 0) {
			// the middle: back to the kinds
			int dx = (int)event.x() - cx(), dy = (int)event.y() - cy();
			if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
				back();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (this.kind == null) {
			this.kind = this.kinds.get(this.selected);
			this.page = 0;
			this.selected = -1;
			click();
			return true;
		}
		Entry e = entry(this.selected);
		if (e != null) {
			if (e.pick().equals("whisper")) {
				this.whisper.visible = true;
				setFocused(this.whisper);
				click();
			} else {
				send(e.pick(), "");
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.whisper != null && this.whisper.visible && getFocused() == this.whisper) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				send("whisper", this.whisper.getValue());
				return true;
			}
			return super.keyPressed(event);
		}
		if (event.key() == InputConstants.KEY_BACKSPACE && this.kind != null && this.kinds.size() > 1) {
			back();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		this.page = Math.floorMod(this.page + (dy < 0 ? 1 : -1), pages());
		return true;
	}

	private void back() {
		if (this.whisper != null && this.whisper.visible) {
			this.whisper.visible = false;
			setFocused(null);
		} else if (this.kind != null && this.kinds.size() > 1) {
			this.kind = null;
			this.page = 0;
		} else {
			return;
		}
		click();
	}

	private void send(String id, String text) {
		ClientPlayNetworking.send(new MagicMenu.Pick(id, text));
		// a changed choice comes back as a fresh screen on the same ring; anything else is cast and the wheel goes
		if (id.startsWith("choose.") && !id.equals("choose.send")) {
			lastKind = this.kind;
		} else {
			onClose();
		}
	}

	private void click() {
		LocalPlayer player = this.minecraft.player;
		if (player != null) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25F, 1.2F, false);
		}
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		int n = count(), cx = cx(), cy = cy(), dx = mouseX - cx, dy = mouseY - cy;
		double step = 360.0 / Math.max(1, n);
		int before = this.selected;
		boolean onBox = this.whisper != null && this.whisper.visible && this.whisper.isMouseOver(mouseX, mouseY);
		if (n == 0 || onBox || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE || dx * dx + dy * dy > (outer() + 8) * (outer() + 8)) {
			this.selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			this.selected = Math.floorMod((int)Math.round(angle / step), n);
		}
		if (this.selected != before && this.selected >= 0) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.6F + this.selected * 0.04F, false);
		}
		Ui.wheel(graphics, cx, cy, RADIUS, TILE);
		if (this.selected >= 0) {
			Ui.wheelPointer(graphics, cx, cy, RADIUS, TILE, Math.atan2(dy, dx), ACCENT);
		}
		for (int i = 0; i < n; i++) {
			double t = Math.toRadians(i * step - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2, y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean picked = i == this.selected;
			Ui.wheelTile(graphics, x, y, TILE, i, picked, picked ? ACCENT : 0x60FFFFFF);
			ItemStack icon = this.kind == null ? new ItemStack(this.kinds.get(i).icon) : entry(i).icon();
			graphics.item(icon, x + (TILE - 16) / 2, y + (TILE - 16) / 2);
		}
		// the middle: the open ring's name, and the way back
		int inner = 2 * Ui.wheelInner(RADIUS, TILE) - 8;
		Component middle = this.kind == null ? this.title : Component.translatable("gui.jaymoskingdoms.magic.tab." + this.kind.key());
		Ui.lineCentred(graphics, this.font, middle, cx, cy - (this.kind != null && this.kinds.size() > 1 ? 9 : 4), inner, Ui.GOLD);
		if (this.kind != null && this.kinds.size() > 1) {
			Ui.lineCentred(graphics, this.font, Component.translatable("gui.jaymoskingdoms.magic.back"), cx, cy + 2, inner, Ui.HINT);
		}
		if (pages() > 1) {
			Ui.lineCentred(graphics, this.font, Component.literal((this.page + 1) + " / " + pages()), cx, cy + 13, inner, Ui.HINT);
		}
		info(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The box beside (or under) the wheel: the pointed-at spell (or kind) in full, as tall as its text needs. */
	private void info(GuiGraphicsExtractor graphics) {
		Component name, sub, body;
		if (this.kind == null) {
			Kind k = this.selected >= 0 ? this.kinds.get(this.selected) : null;
			name = k == null ? this.title : Component.translatable("gui.jaymoskingdoms.magic.tab." + k.key());
			sub = k == null ? null : Component.translatable("gui.jaymoskingdoms.magic.sub." + k.key());
			body = Component.translatable(k == null ? "gui.jaymoskingdoms.magic.hint.kinds" : "gui.jaymoskingdoms.magic.hint.open");
		} else {
			Entry e = entry(this.selected);
			if (e == null && this.whisper != null && this.whisper.visible) {
				e = entries(Kind.EVERYDAY).stream().filter(x -> x.pick().equals("whisper")).findFirst().orElse(null);
			}
			name = e == null ? Component.translatable("gui.jaymoskingdoms.magic.tab." + this.kind.key()) : e.name();
			sub = Component.translatable("gui.jaymoskingdoms.magic.sub." + this.kind.key());
			body = e == null ? Component.translatable(count() == 0 ? "gui.jaymoskingdoms.magic.none" : "gui.jaymoskingdoms.magic.hint.spells") : e.desc();
		}
		int w = infoW(), x = infoX(), textW = w - 18;
		List<FormattedCharSequence> rows = this.font.split(body, textW);
		boolean typing = this.whisper != null && this.whisper.visible;
		int h = 8 + 11 + (sub != null ? 10 : 0) + 4 + rows.size() * Ui.LINE + 6 + (typing ? 22 : 0);
		int y = side() ? cy() - h / 2 : cy() + outer() + 8;
		y = Math.max(4 + MenuCompat.STRIP, Math.min(y, this.height - 4 - h));
		Ui.panel(graphics, x, y, w, h, ACCENT);
		int ty = y + 8;
		Ui.line(graphics, this.font, name, x + 9, ty, textW, ACCENT);
		ty += 11;
		if (sub != null) {
			Ui.line(graphics, this.font, sub, x + 9, ty, textW, Ui.DIM);
			ty += 10;
		}
		Ui.rule(graphics, x + 6, x + w - 6, ty + 1);
		ty += 5;
		for (FormattedCharSequence row : rows) {
			graphics.text(this.font, row, x + 9, ty, this.kind == null || entry(this.selected) == null && !typing ? Ui.HINT : Ui.TEXT);
			ty += Ui.LINE;
		}
		if (typing) {
			this.whisper.setX(x + 6);
			this.whisper.setY(ty + 4);
			this.whisper.setWidth(w - 12);
		}
	}
}
