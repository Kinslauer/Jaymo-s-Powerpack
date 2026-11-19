package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.Stats;
import com.jaymoskingdoms.client.ui.FlatButton;
import com.jaymoskingdoms.client.ui.PickerLayout;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The heritage screen ({@link Heritage}), the step of character creation between MCA's destiny and the class, in the
 * picker layout ({@link PickerLayout}): you on the left as the heritage pointed at (or picked) would make you, a compact
 * two-column list of every heritage in the middle (icon, name, a one-line tag), and a big info panel on the right: its
 * free points and bonuses as pills, its story, its gift (Intrinsic), the rest of its kit (Strengths, Weaknesses), its
 * look, and the looks it can be born in as Paths. Pressing one picks it; the button under the figure seals it, for life.
 * It can't be shut without picking. An earned heritage not yet woken shows as "???" with a hint of how it's woken, and
 * can't be picked. Each heritage comes in several looks ({@link HeritageVariants}): the button over Choose steps through
 * the picked one's.
 */
final class HeritageScreen extends Screen {
	/** The heritage screen's accent: a deep teal, apart from the class screen's gold. */
	static final int ACCENT = 0xFF4FB8A8;
	/** After Choose, how long the heritage may take to come back before the screen is put up again (ms). */
	static final long CHOOSE_GRACE_MS = 3000L;
	/** The lead line under the story ("Your heritage gives..."): shown this long the first time the screen opens, fading out over the last of it (ms). */
	static final long LEAD_MS = 5000L, LEAD_FADE_MS = 1500L;

	private final boolean forced;
	private Heritage.@Nullable Kind picked;
	/** The picked heritage's look (0: the first). */
	private int pickedVariant;
	private @Nullable Button choose, variant, later;
	private final PickerLayout layout = new PickerLayout();

	/** The server asked for a heritage that can't be put off: the screen comes back whenever it's shut. */
	private static boolean owed;
	private static long quietUntil;
	/** When the lead line first showed (0: not yet); once gone it stays gone, whichever heritage or screen. */
	private static long leadFrom;

	HeritageScreen(boolean forced) {
		super(Component.translatable("gui.jaymoskingdoms.heritage.title"));
		this.forced = forced;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Heritage.Open.TYPE, (payload, context) -> {
			owed |= payload.forced();
			context.client().gui.setScreen(new HeritageScreen(payload.forced()));
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!owed || minecraft.player == null) {
				return;
			}
			if (Heritage.of(minecraft.player) != null) {
				owed = false;
			} else if (minecraft.gui.screen() == null && System.currentTimeMillis() > quietUntil) {
				minecraft.gui.setScreen(new HeritageScreen(true));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> owed = false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.forced;
	}

	/** An earned heritage the player hasn't woken: a "???" with a hint. */
	private static boolean hidden(Heritage.Kind kind) {
		Minecraft minecraft = Minecraft.getInstance();
		return kind.earned && (minecraft.player == null || !Heritage.unlocked(minecraft.player, kind) && Heritage.of(minecraft.player) != kind);
	}

	/** Whether {@code kind} can be picked here: any that isn't hidden. */
	private boolean pickable(Heritage.Kind kind) {
		return !hidden(kind);
	}

	private static Component name(Heritage.Kind kind) {
		return hidden(kind) ? Component.translatable("gui.jaymoskingdoms.heritage.unknown") : Component.translatable("heritage.jaymoskingdoms." + kind.key());
	}

	private static boolean done() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && Heritage.of(minecraft.player) != null;
	}

	private int buttonCount() {
		return this.forced ? 2 : 3;
	}

	@Override
	protected void init() {
		if (done()) {
			onClose();
			return;
		}
		this.layout.fit(this.width, this.height, 0, buttonCount());
		int[] b = this.layout.button(0);
		this.choose = addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.class.choose"), x -> {
			if (this.picked != null) {
				ClientPlayNetworking.send(new Heritage.Choose(this.picked.ordinal(), this.pickedVariant));
				quietUntil = System.currentTimeMillis() + CHOOSE_GRACE_MS;
				onClose();
			}
		}).bounds(b[0], b[1], b[2], b[3]).build());
		// the picked heritage's look, stepped through on each press (right click steps back)
		int[] v = this.layout.button(this.forced ? 1 : 2);
		this.variant = addRenderableWidget(FlatButton.builder(Component.empty(), x -> {
			if (this.picked != null) {
				this.pickedVariant = (this.pickedVariant + 1) % (HeritageVariants.of(this.picked).size() + 1);
			}
		}).bounds(v[0], v[1], v[2], v[3]).build());
		if (!this.forced) {
			int[] l = this.layout.button(1);
			this.later = addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.class.later"), x -> onClose())
				.bounds(l[0], l[1], l[2], l[3]).build());
		}
		this.layout.reveal(Heritage.Kind.playable().length, this.picked == null ? 0 : indexOf(this.picked));
	}

	private static int indexOf(Heritage.Kind kind) {
		Heritage.Kind[] all = Heritage.Kind.playable();
		for (int i = 0; i < all.length; i++) {
			if (all[i] == kind) {
				return i;
			}
		}
		return 0;
	}

	private Heritage.@Nullable Kind rowAt(int mouseX, int mouseY) {
		Heritage.Kind[] all = Heritage.Kind.playable();
		int i = this.layout.rowAt(all.length, mouseX, mouseY);
		return i < 0 ? null : all[i];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Heritage.Kind row = rowAt((int)event.x(), (int)event.y());
		if (row != null && pickable(row)) {
			if (row != this.picked) {
				this.pickedVariant = 0;
			}
			this.picked = row;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		// right click on the look button steps back through the looks
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && this.variant != null && this.variant.visible && this.variant.isMouseOver(event.x(), event.y()) && this.picked != null) {
			int n = HeritageVariants.of(this.picked).size() + 1;
			this.pickedVariant = Math.floorMod(this.pickedVariant - 1, n);
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.layout.scrolled(mouseX, mouseY, scrollY, Heritage.Kind.playable().length)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (done()) {
			onClose();
			return;
		}
		this.layout.fit(this.width, this.height, 0, buttonCount());
		Heritage.Kind[] all = Heritage.Kind.playable();
		Heritage.Kind hover = rowAt(mouseX, mouseY);
		Heritage.Kind kind = hover != null ? hover : this.picked != null ? this.picked : firstPickable();
		boolean hidden = hidden(kind);
		this.layout.header(graphics, this.font, this.title, Component.translatable("gui.jaymoskingdoms.heritage.sub"), Ui.GOLD);

		// the list
		List<PickerLayout.Entry> entries = new ArrayList<>();
		for (Heritage.Kind k : all) {
			boolean locked = !pickable(k);
			entries.add(new PickerLayout.Entry(locked ? ItemStack.EMPTY : new ItemStack(k.icon), null, name(k),
				locked ? Component.translatable("gui.jaymoskingdoms.picker.earned") : PickerLayout.tag("heritage.jaymoskingdoms." + k.key() + ".tag"), rim(k), locked));
		}
		this.layout.list(graphics, this.font, entries, this.picked == null ? -1 : indexOf(this.picked), hover == null ? -1 : indexOf(hover), ACCENT);

		// the figure, as the heritage pointed at (or picked) would make you
		this.layout.backdrop(graphics, hidden ? Ui.HINT : rim(kind), StoryFigure.SCALE);
		int[] box = this.layout.figureBox();
		var player = this.minecraft.player;
		if (player != null && !hidden) {
			int look = kind == this.picked ? this.pickedVariant : 0;
			StoryFigure.drawAs(graphics, player, kind, look, null, box[0], box[1], box[2], box[3]);
		}
		this.layout.caption(graphics, this.font, hidden ? Component.translatable("gui.jaymoskingdoms.heritage.unknown")
			: Component.translatable("gui.jaymoskingdoms.picker.as", name(kind)),
			kind == this.picked && !hidden ? Component.translatable("gui.jaymoskingdoms.picker.look_of", HeritageVariants.title(kind, this.pickedVariant),
				this.pickedVariant + 1, HeritageVariants.of(kind).size() + 1) : null);

		// the info panel
		// (Andy: "all bio built info should be hidden except unlock method") the Bio-built's panel only says how it wakes
		this.layout.info(graphics, this.font, info(kind, hidden || kind == Heritage.Kind.BIOBUILT), kind, mouseX, mouseY);

		if (this.choose != null) {
			int[] b = this.layout.button(0);
			this.choose.setPosition(b[0], b[1]);
			this.choose.setWidth(b[2]);
			this.choose.setMessage(this.picked == null ? Component.translatable("gui.jaymoskingdoms.class.choose")
				: Component.translatable("gui.jaymoskingdoms.picker.become", PickerLayout.withArticle(name(this.picked).getString())));
			this.choose.active = this.picked != null;
		}
		if (this.variant != null) {
			int[] v = this.layout.button(this.forced ? 1 : 2);
			this.variant.setPosition(v[0], v[1]);
			this.variant.setWidth(v[2]);
			this.variant.visible = this.picked != null;
			if (this.picked != null) {
				this.variant.setMessage(Component.translatable("gui.jaymoskingdoms.heritage.variant", HeritageVariants.title(this.picked, this.pickedVariant),
					this.pickedVariant + 1, HeritageVariants.of(this.picked).size() + 1));
			}
		}
		if (this.later != null) {
			int[] l = this.layout.button(1);
			this.later.setPosition(l[0], l[1]);
			this.later.setWidth(l[2]);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The info panel for {@code kind}. */
	private PickerLayout.Info info(Heritage.Kind kind, boolean hidden) {
		List<Component> story = new ArrayList<>();
		List<PickerLayout.Section> sections = new ArrayList<>();
		List<PickerLayout.Pill> pills = new ArrayList<>();
		List<PickerLayout.Path> paths = new ArrayList<>();
		Component tag = hidden ? Component.translatable("gui.jaymoskingdoms.heritage.earned") : PickerLayout.tag("heritage.jaymoskingdoms." + kind.key() + ".tag");
		story.add(Component.translatable(hidden ? "gui.jaymoskingdoms.heritage.earned_desc" : "heritage.jaymoskingdoms." + kind.key() + ".desc"));
		int lead = leadColor();
		if (this.picked == null && lead != 0) {
			story.add(Component.translatable("gui.jaymoskingdoms.heritage.lead").withColor(lead));
		}
		if (hidden) {
			sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.hint"), Ui.DIM,
				List.of(Component.translatable("heritage.jaymoskingdoms." + kind.key() + ".hint"))));
			return new PickerLayout.Info(ItemStack.EMPTY, null, name(kind), tag, Ui.HINT, pills, story, sections, null, paths, -1);
		}
		int looks = HeritageVariants.of(kind).size() + 1;
		HeritageVariants.Variant look = HeritageVariants.at(kind, kind == this.picked ? this.pickedVariant : 0);
		pills.addAll(statPills(kind));
		pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.looks").getString(), String.valueOf(looks), Ui.DIM));

		sections.addAll(kitSections(kind, look));
		// the look: the picked one's own, else every look's name
		List<Component> lookLines = new ArrayList<>();
		if (kind == this.picked) {
			String root = HeritageVariants.langKey(kind, this.pickedVariant);
			lookLines.add(Component.translatable("gui.jaymoskingdoms.heritage.variant_row", HeritageVariants.title(kind, this.pickedVariant),
				Component.translatable(root + ".desc"), Component.translatable(root + ".trait")));
		} else {
			net.minecraft.network.chat.MutableComponent names = Component.empty();
			for (int i = 0; i < looks; i++) {
				if (i > 0) {
					names.append(i == looks - 1 ? Component.translatable("gui.jaymoskingdoms.heritage.variants.and") : Component.literal(", "));
				}
				names.append(HeritageVariants.title(kind, i));
			}
			lookLines.add(Component.translatable("gui.jaymoskingdoms.heritage.variants", looks, names));
		}
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.look"), Ui.TEXT, lookLines));
		// how each of its heritage paths opens (HeritagePaths)
		List<Component> howLines = new ArrayList<>();
		for (com.jaymoskingdoms.HeritagePaths.Path p : com.jaymoskingdoms.HeritagePaths.Path.values()) {
			if (p.blood == kind) {
				howLines.add(Component.translatable("gui.jaymoskingdoms.path.how_row", Component.translatable("path.jaymoskingdoms." + p.key()),
					Component.translatable("path.jaymoskingdoms." + p.key() + ".deeds")));
			}
		}
		if (!howLines.isEmpty()) {
			sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.path.how"), Ui.TEXT, howLines));
		}

		// the paths: the looks it can be born in, and the blood a look leans toward
		for (int i = 0; i < looks; i++) {
			List<ItemStack> icons = new ArrayList<>();
			icons.add(new ItemStack(kind.icon));
			HeritageVariants.Variant v = HeritageVariants.at(kind, i);
			if (v != null && v.hybrid() != null) {
				icons.add(new ItemStack(v.hybrid().icon));
			}
			paths.add(new PickerLayout.Path(icons, HeritageVariants.title(kind, i), -1));
		}
		// and the heritage paths its deeds open: five steps each, lit as they wake (HeritagePaths)
		paths.addAll(heritagePaths(kind));
		return new PickerLayout.Info(new ItemStack(kind.icon), null, name(kind), tag, kind == this.picked ? ACCENT : rim(kind), pills, story, sections,
			Component.translatable("gui.jaymoskingdoms.picker.paths_heritage", name(kind)), paths, -1);
	}

	/** {@code kind}'s heritage paths as picker rows: five step icons (lit as they wake) and the path's name with how far you are on it. */
	static List<PickerLayout.Path> heritagePaths(Heritage.Kind kind) {
		List<PickerLayout.Path> rows = new ArrayList<>();
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		for (com.jaymoskingdoms.HeritagePaths.Path p : com.jaymoskingdoms.HeritagePaths.Path.values()) {
			if (p.blood != kind) {
				continue;
			}
			int steps = player == null ? 0 : com.jaymoskingdoms.HeritagePaths.stepsShown(player, p);
			List<ItemStack> icons = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				icons.add(new ItemStack(i < steps ? p.icon : net.minecraft.world.item.Items.GRAY_DYE));
			}
			Component name = Component.translatable("path.jaymoskingdoms." + p.key());
			rows.add(new PickerLayout.Path(icons, player == null ? name : Component.translatable("gui.jaymoskingdoms.path.row", name,
				com.jaymoskingdoms.HeritagePaths.status(player, p)), -1));
		}
		return rows;
	}

	/** A card row without its leading "Gift: " or "Flaw: " (the section already says which). */
	/** {@code kind}'s free points and stat bonuses, a pill each (also the life story's birthplace page, {@link LifeStoryScreen}). */
	static List<PickerLayout.Pill> statPills(Heritage.Kind kind) {
		List<PickerLayout.Pill> pills = new ArrayList<>();
		if (kind.freePoints > 0) {
			pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.free").getString(), "+" + kind.freePoints * Stats.GIFT_STEP, Ui.GOLD));
		}
		for (Stats.Stat stat : Stats.Stat.values()) {
			int n = kind.bonus(stat);
			if (n > 0) {
				pills.add(new PickerLayout.Pill(Component.translatable("stat.jaymoskingdoms." + stat.key()).getString(), "+" + n * Stats.GIFT_STEP, Ui.GOLD));
			}
		}
		return pills;
	}

	/**
	 * {@code kind}'s whole kit as the panel's Intrinsic, Strengths and Weaknesses, sorted from its card ({@link Heritage.Kind#card}),
	 * so a gift or flaw added to the heritage shows here and on the life story's birthplace page without more work.
	 */
	static List<PickerLayout.Section> kitSections(Heritage.Kind kind, HeritageVariants.@Nullable Variant look) {
		List<PickerLayout.Section> out = new ArrayList<>();
		// the card's rows by what they are: the gift is its own, kit gifts are strengths, flaws and the shunning weaknesses, the rest intrinsic
		List<Component> intrinsic = new ArrayList<>(), strengths = new ArrayList<>(), weaknesses = new ArrayList<>();
		for (Component row : kind.card(look)) {
			String key = row.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
			if (key.endsWith(".free") || key.endsWith(".bonus")) {
				continue;
			}
			int color = rowColor(row);
			// (Andy: "remove all z fighter info from these screens") a Z Fighter's extras are left out, a row with nothing else dropped
			row = withoutZFighter(row);
			if (row == null) {
				continue;
			}
			boolean ownGift = key.equals("heritage.jaymoskingdoms." + kind.key() + ".gift") || key.equals("heritage.jaymoskingdoms." + kind.key() + ".gift_rimewyrm");
			Component text = stripLabel(row);
			if (ownGift) {
				intrinsic.add(text);
			} else if (color == Ui.BAD || key.contains(".shunned.")) {
				weaknesses.add(color == Ui.BAD ? text : row);
			} else if (color == Ui.GOOD) {
				strengths.add(text);
			} else {
				intrinsic.add(text);
			}
		}
		out.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.intrinsic"), Ui.GOLD, intrinsic));
		out.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.strengths"), Ui.GOOD, strengths));
		out.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.weaknesses"), Ui.BAD, weaknesses));
		return out;
	}

	/** {@code row} without its clauses about Z Fighters (cut at its semicolons, outside brackets); null if nothing else is left. */
	private static @Nullable Component withoutZFighter(Component row) {
		String s = row.getString();
		if (!s.contains("Z Fighter")) {
			return row;
		}
		List<String> kept = new ArrayList<>();
		for (String clause : PickerLayout.splitTop(s, "; ", false)) {
			if (!clause.contains("Z Fighter")) {
				kept.add(clause);
			}
		}
		if (kept.isEmpty()) {
			return null;
		}
		return Component.literal(String.join("; ", kept)).withStyle(row.getStyle());
	}

	private static Component stripLabel(Component row) {
		String s = row.getString();
		for (String label : new String[] {"Gift: ", "Flaw: "}) {
			if (s.startsWith(label)) {
				Component out = Component.literal(s.substring(label.length()));
				return row.getStyle().getColor() != null ? ((net.minecraft.network.chat.MutableComponent)out).withStyle(row.getStyle()) : out;
			}
		}
		return row;
	}

	/** The lead line's colour now (fading from the hint grey into the panel), or 0 once its time is up. */
	private static int leadColor() {
		long now = System.currentTimeMillis();
		if (leadFrom == 0L) {
			leadFrom = now;
		}
		long left = LEAD_MS - (now - leadFrom);
		if (left <= 0L) {
			return 0;
		}
		float f = Math.min(1.0F, left / (float)LEAD_FADE_MS);
		int r = Math.round(0x10 + (0x70 - 0x10) * f), g = Math.round(0x10 + (0x70 - 0x10) * f), b = Math.round(0x18 + (0x80 - 0x18) * f);
		return r << 16 | g << 8 | Math.max(1, b);
	}

	/**
	 * A card row's colour by what it is, not where it sits: gifts green, flaws red, an ability gold, the rest plain.
	 */
	private static int rowColor(Component row) {
		if (row.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
			String key = translatable.getKey();
			if (key.endsWith(".flaw") || key.endsWith(".card.flaw")) {
				return Ui.BAD;
			}
			// a gift with its own card key (".gift_rimewyrm") is a gift too
			if (key.endsWith(".gift") || key.endsWith(".card.gift") || key.contains(".gift_")) {
				return Ui.GOOD;
			}
			if (key.contains(".card.move.")) {
				return Ui.GOLD;
			}
		}
		return Ui.TEXT;
	}

	/** What the panel shows before anything's pointed at or picked: the first that can be picked. */
	private Heritage.Kind firstPickable() {
		for (Heritage.Kind kind : Heritage.Kind.playable()) {
			if (pickable(kind)) {
				return kind;
			}
		}
		return Heritage.Kind.playable()[0];
	}

	/** The colour of a heritage's icon rim and figure glow in the list: its element or land. */
	static int rim(Heritage.Kind kind) {
		return switch (kind) {
			case HEARTLANDER -> 0xFF4CAF50;
			case STONEBORN -> 0xFF9A9A9A;
			case TIDEBORN -> 0xFF3A8FD0;
			case SYLVAN -> 0xFF6BBF3A;
			case VERDANT -> 0xFF3E9A55;
			case EMBERBORN -> 0xFFE07A2A;
			case FROSTBORN -> 0xFFA8D8F0;
			case NIGHTBORN -> 0xFF7A5AC0;
			case WARRIORBORN -> 0xFFC0C0C8;
			case EARTHLING -> 0xFFC8B878;
			case STORMBORN -> 0xFF8AB0E8;
			case DAWNBORN -> 0xFFF0D040;
			case ENDBORN -> 0xFFB060D8;
			case DRAGONBLOOD -> 0xFFD04A3A;
			case DEEPBORN -> 0xFF3A6A8A;
			case DIVINE -> 0xFFF0E8B0;
			case BIOBUILT -> 0xFFC87A48;
			case RUBBERFOLK -> 0xFFE888B0;
			case BEASTFOLK -> 0xFFB87A3A;
			case HOLLOW -> 0xFF8A9A8A;
			default -> ACCENT;
		};
	}
}
