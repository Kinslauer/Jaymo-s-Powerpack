package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.jaymoskingdoms.Turning;
import com.jaymoskingdoms.client.ui.FlatButton;
import com.jaymoskingdoms.client.ui.PickerLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The patrons screen (docs/Patrons-Plan.md section 8) in the picker layout ({@link PickerLayout}): the eight patrons in the
 * list (the ones you have not met dim), and for the one picked a big info panel: its domain, what it takes as an offering, the
 * deeds it likes, who it is cool to and, for your own patron, your favour, its four blessings (the Paths) and the boon. Names,
 * offerings, favour and mechanics only: no lore outright. Opened from a shrine (sneak and use), the skill screen (K) and the
 * boon menu. Reads only the synced state and the item tags.
 */
final class PatronScreen extends Screen {
	private static final int OFFERING_ICONS = 8;

	private final @Nullable Screen parent;
	private @Nullable Patron picked;
	private final PickerLayout layout = new PickerLayout();

	PatronScreen(@Nullable Screen parent, @Nullable Patron picked) {
		super(Component.translatable("gui.jaymoskingdoms.patrons.title"));
		this.parent = parent;
		this.picked = picked;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private Patrons.State state() {
		return this.minecraft.player == null ? Patrons.State.EMPTY : Patrons.state(this.minecraft.player);
	}

	private boolean known(Patron p) {
		Patrons.State s = state();
		return s.known().contains(p.key) || p.key.equals(s.patron());
	}

	@Override
	protected void init() {
		if (this.picked == null) {
			Patron mine = state().patronOf();
			this.picked = mine != null ? mine : Patron.values()[0];
		}
		this.layout.fit(this.width, this.height, 0, 1);
		int[] b = this.layout.button(0);
		addRenderableWidget(FlatButton.builder(Component.translatable("gui.done"), x -> onClose()).bounds(b[0], b[1], b[2], b[3]).build());
		this.layout.reveal(Patron.values().length, this.picked.ordinal());
	}

	private @Nullable Patron rowAt(int mouseX, int mouseY) {
		Patron[] all = Patron.values();
		int i = this.layout.rowAt(all.length, mouseX, mouseY);
		return i < 0 ? null : all[i];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Patron row = rowAt((int)event.x(), (int)event.y());
		if (row != null) {
			this.picked = row;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.layout.scrolled(x, y, scrollY, Patron.values().length);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.layout.fit(this.width, this.height, 0, 1);
		Patrons.State s = state();
		Patron mine = s.patronOf();
		Patron[] all = Patron.values();
		Patron hover = rowAt(mouseX, mouseY);
		Patron p = hover != null ? hover : this.picked != null ? this.picked : all[0];
		int accent = mine != null ? mine.colour : Ui.GOLD;
		int standing = standingColour(s, mine);
		this.layout.header(graphics, this.font, this.title, standingLine(s, mine), accent, standing);

		List<PickerLayout.Entry> entries = new ArrayList<>();
		for (Patron q : all) {
			boolean known = known(q);
			entries.add(new PickerLayout.Entry(offeringIcon(q), null, known ? q.named() : Component.translatable("patron.jaymoskingdoms.unknown"),
				known ? PickerLayout.tag("patron.jaymoskingdoms." + q.key + ".tag") : null, q == mine ? Ui.GOLD : q.colour, !known));
		}
		this.layout.list(graphics, this.font, entries, this.picked == null ? -1 : this.picked.ordinal(), hover == null ? -1 : hover.ordinal(), accent);

		// you, under the glow of the patron shown
		boolean known = known(p);
		this.layout.backdrop(graphics, known ? p.colour : Ui.HINT, StoryFigure.SCALE);
		int[] box = this.layout.figureBox();
		if (this.minecraft.player != null) {
			StoryFigure.drawAs(graphics, this.minecraft.player, null, 0, null, box[0], box[1], box[2], box[3]);
		}
		this.layout.caption(graphics, this.font, mine == null ? Component.translatable("gui.jaymoskingdoms.patrons.unsworn_short")
			: Component.translatable("gui.jaymoskingdoms.patrons.sworn_short", mine.named()), known && p != mine ? p.named() : null);

		this.layout.info(graphics, this.font, info(p, s, mine, known), p.key + "/" + known, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private static ItemStack offeringIcon(Patron p) {
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(p.offerings)) {
			return new ItemStack(holder.value());
		}
		return ItemStack.EMPTY;
	}

	/** Your standing's colour. */
	private int standingColour(Patrons.State s, @Nullable Patron mine) {
		int color = Ui.DIM;
		if (mine != null) {
			color = mine.colour;
			Patron to = Patron.byKey(s.streak().patron());
			if (to != null && to != mine && s.streak().days() > 0 && s.streak().lastDay() >= today() - 1) {
				color = Ui.BAD;
			}
		}
		return color;
	}

	/** Your standing, under the title. */
	private Component standingLine(Patrons.State s, @Nullable Patron mine) {
		if (mine != null) {
			Patron to = Patron.byKey(s.streak().patron());
			// praying towards another patron: your own will mark you
			if (to != null && to != mine && s.streak().days() > 0 && s.streak().lastDay() >= today() - 1) {
				return Component.translatable("gui.jaymoskingdoms.patrons.turning", to.named(), s.streak().days(), Patrons.SWEAR_DAYS);
			}
			return Component.translatable("gui.jaymoskingdoms.patrons.sworn", mine.named(), s.swornDay() + 1, Component.translatable("patron.jaymoskingdoms.tier." + s.tier()));
		}
		if (s.streak().days() > 0) {
			Patron streaking = Patron.byKey(s.streak().patron());
			return Component.translatable("gui.jaymoskingdoms.patrons.streak", streaking == null ? "" : streaking.named(), s.streak().days(), Patrons.SWEAR_DAYS);
		}
		return Component.translatable("gui.jaymoskingdoms.patrons.unsworn");
	}

	/** The clock's shrine day on this client, or 0 with no world. */
	private long today() {
		return this.minecraft.level == null ? 0L : Patrons.day(this.minecraft.level.getOverworldClockTime());
	}

	private static boolean isTrial(Patrons.Mark m) {
		return Turning.TRIAL.equals(m.kind()) || Turning.TRIAL_EASED_KIND.equals(m.kind());
	}

	/** The most days left of any live trial. */
	private long daysLeftMost(Patrons.State s) {
		long today = today(), most = 0L;
		for (Patrons.Mark m : s.marks()) {
			if (isTrial(m) && m.day() > today) {
				most = Math.max(most, m.day() - today);
			}
		}
		return most;
	}

	private PickerLayout.Info info(Patron p, Patrons.State s, @Nullable Patron mine, boolean known) {
		List<Component> story = new ArrayList<>();
		List<PickerLayout.Section> sections = new ArrayList<>();
		List<PickerLayout.Pill> pills = new ArrayList<>();
		List<PickerLayout.Path> paths = new ArrayList<>();
		if (!known) {
			story.add(Component.translatable("gui.jaymoskingdoms.patrons.unknown_hint"));
			return new PickerLayout.Info(ItemStack.EMPTY, null, Component.translatable("patron.jaymoskingdoms.unknown"), null, Ui.HINT, pills, story, sections, null, paths, -1);
		}
		story.add(Component.translatable("patron.jaymoskingdoms." + p.key + ".domain"));
		if (p == mine) {
			pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.favour").getString(), s.favour() + "/" + Patrons.FAVOUR_MAX, p.colour));
			pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.tier").getString(), String.valueOf(s.tier()), Ui.GOLD));
		}
		long longest = daysLeftMost(s);
		if (longest > 0L) {
			pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.trials").getString(), longest + "d", Ui.BAD));
		}

		// offerings: what it takes
		List<Component> offerings = new ArrayList<>();
		net.minecraft.network.chat.MutableComponent names = Component.empty();
		int n = 0;
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(p.offerings)) {
			if (n++ >= OFFERING_ICONS) {
				break;
			}
			if (n > 1) {
				names.append(", ");
			}
			names.append(new ItemStack(holder.value()).getHoverName());
		}
		offerings.add(names);
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.patrons.offerings"), Ui.GOLD, offerings));
		List<Component> deeds = new ArrayList<>();
		for (int i = 1; i <= 2; i++) {
			deeds.add(Component.literal("- ").append(Component.translatable("patron.jaymoskingdoms." + p.key + ".deed." + i)));
		}
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.patrons.deeds"), Ui.GOOD, deeds));
		List<Component> cool = new ArrayList<>();
		cool.add(Component.translatable("gui.jaymoskingdoms.patrons.rivals", rivals(p)));
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.cool"), Ui.BAD, cool));
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.patrons.marks"), Ui.BAD, marks(p, s)));
		if (mine != null && p != mine) {
			sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.turning"), Ui.BAD,
				List.of(Component.translatable("gui.jaymoskingdoms.patrons.turn_warning", p.named(), mine.named()).withColor(Ui.HINT))));
		}

		// the four blessings and the boon: the paths a patron's favour grows into
		List<Component> blessings = new ArrayList<>();
		for (int tier = 1; tier <= 4; tier++) {
			boolean lit = p == mine && s.tier() >= tier;
			blessings.add(Component.translatable("blessing.jaymoskingdoms." + p.key + "." + tier + ".desc").withColor(lit ? p.colour & 0x00FFFFFF : Ui.HINT & 0x00FFFFFF));
			List<ItemStack> icons = new ArrayList<>();
			int skip = 0;
			for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(p.offerings)) {
				if (skip++ == tier - 1) {
					icons.add(new ItemStack(holder.value()));
					break;
				}
			}
			paths.add(new PickerLayout.Path(icons.isEmpty() ? List.of(offeringIcon(p)) : icons, Component.translatable("blessing.jaymoskingdoms." + p.key + "." + tier), -1));
		}
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.blessings"), p.colour, blessings));
		if (p == mine) {
			List<Component> own = new ArrayList<>();
			own.add(Component.translatable("gui.jaymoskingdoms.patrons.boon").append(": ").append(BoonScreen.stateLine(s, this.minecraft)).withColor(s.tier() >= 4 ? Ui.GOOD & 0x00FFFFFF : Ui.HINT & 0x00FFFFFF));
			own.add(Component.translatable("gui.jaymoskingdoms.patrons.decay", Patrons.GRACE_DAYS).withColor(Ui.HINT & 0x00FFFFFF));
			sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.boon"), Ui.GOLD, own));
		}
		return new PickerLayout.Info(offeringIcon(p), null, p.named(), PickerLayout.tag("patron.jaymoskingdoms." + p.key + ".tag"), p.colour, pills, story, sections,
			Component.translatable("gui.jaymoskingdoms.picker.paths_patron"), paths, -1);
	}

	/** Your scars and trials of {@code p}, when you have any (as lines). */
	private List<Component> marks(Patron p, Patrons.State s) {
		List<Component> out = new ArrayList<>();
		if (s.marks().isEmpty() || this.minecraft.player == null) {
			return out;
		}
		int scars = Turning.scars(this.minecraft.player, p);
		long today = today(), left = 0L;
		boolean eased = false, any = false;
		for (Patrons.Mark m : s.marks()) {
			if (isTrial(m) && m.day() > today && m.patron().equals(p.key)) {
				any = true;
				if (m.day() - today > left) {
					left = m.day() - today;
					eased = Turning.TRIAL_EASED_KIND.equals(m.kind());
				}
			}
		}
		if (scars <= 0 && !any) {
			return out;
		}
		if (scars > 0) {
			out.add(Component.translatable("gui.jaymoskingdoms.patrons.scarred", scars));
			out.add(Component.translatable("gui.jaymoskingdoms.patrons.scar." + p.key).withColor(Ui.DIM & 0x00FFFFFF));
		}
		if (any) {
			out.add(Component.translatable(eased ? "gui.jaymoskingdoms.patrons.trial.eased" : "gui.jaymoskingdoms.patrons.trial", left));
			out.add(Component.translatable("gui.jaymoskingdoms.patrons.trial." + p.key).withColor(Ui.DIM & 0x00FFFFFF));
			out.add(Component.translatable("gui.jaymoskingdoms.patrons.trial.shunned", p.named()).withColor(Ui.DIM & 0x00FFFFFF));
			if (!eased) {
				out.add(Component.translatable("gui.jaymoskingdoms.patrons.trial.rite_hint").withColor(Ui.HINT & 0x00FFFFFF));
			}
		}
		return out;
	}

	private static Component rivals(Patron p) {
		if (p == Patron.TRUTH) {
			return Component.translatable("gui.jaymoskingdoms.patrons.rivals.seven");
		}
		Component out = Patron.TRUTH.named();
		if (p.rival != null) {
			out = Component.empty().append(p.rival.named()).append(", ").append(out);
		}
		if (p == Patron.LANTERN_WARDEN) {
			out = Component.empty().append(Component.translatable("gui.jaymoskingdoms.patrons.rivals.dark")).append(", ").append(out);
		}
		return out;
	}
}
