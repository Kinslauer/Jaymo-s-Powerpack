package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClassForks;
import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.client.ui.FlatButton;
import com.jaymoskingdoms.client.ui.PickerLayout;
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
 * The class screen ({@link Classes}) in the picker layout ({@link PickerLayout}): you on the left in the gear of the class
 * pointed at (or picked), a compact list of every class (its signature gear, name and a one-line tag), and a big info
 * panel: what it's good with (Intrinsic, Strengths), what it costs to change the pick later (Specialization) and, as Paths,
 * the options it picks (a Mage's first spell, a Sorcerer's element, a Paladin's oath...; press one to pick it) and the
 * crossroads its tree grows into. Pressing a class picks it; the button under the figure seals it. Opened by the server the
 * first time a player comes into a world (then it can't be shut without picking) and when a Writ of Rebirth is read.
 * A player who already holds their class and opens it again ({@code switching}) can change what they picked with it, for
 * silver and after a wait ({@link Classes#RESPEC_SILVER}); a creative player switches class freely.
 */
final class ClassScreen extends Screen {
	/** What each class grows into at its crossroads: the tree nodes ({@code classtree.jaymoskingdoms.<id>}). */
	private static final java.util.Map<PlayerClass, List<String>> FORKS = java.util.Map.of(
		PlayerClass.THIEF, List.of(ClassForks.ASSASSIN, ClassForks.ACROBAT),
		PlayerClass.MAGE, List.of(ClassForks.EVOKER, ClassForks.WARDER),
		PlayerClass.RANGER, List.of(ClassForks.HUNTER, ClassForks.BEASTS));

	private final boolean forced;
	/** The class pressed, and (for a class with options) the option pressed under it. */
	private @Nullable PlayerClass picked;
	private int option = -1;
	private @Nullable Button choose, none, later;
	private final PickerLayout layout = new PickerLayout();
	/** Each class's gear, made once for the screen. */
	private final java.util.Map<String, List<ItemStack>> kits = new java.util.HashMap<>();

	/** The class a master is teaching as a second class (the screen shows that one only), or null. */
	private final @Nullable PlayerClass teaching;
	private final boolean switching;

	ClassScreen(boolean forced) {
		this(forced, 0, false);
	}

	ClassScreen(boolean forced, int second) {
		this(forced, second, false);
	}

	/**
	 * {@code switching}: a class tab on the H menu: open with a class already chosen. A creative player's Choose swaps it, the
	 * screen staying up; anyone else's can only change the option picked with the class they hold.
	 */
	ClassScreen(boolean forced, boolean switching) {
		this(forced, 0, switching);
	}

	private ClassScreen(boolean forced, int second, boolean switching) {
		super(Component.translatable(second > 0 ? "gui.jaymoskingdoms.class.title_second" : "gui.jaymoskingdoms.class.title"));
		this.forced = forced;
		this.switching = switching;
		this.teaching = second > 0 && second <= PlayerClass.values().length ? PlayerClass.values()[second - 1] : null;
		this.picked = this.teaching;
		Minecraft minecraft = Minecraft.getInstance();
		if (switching && minecraft.player != null && !minecraft.player.isCreative() && Classes.classOf(minecraft.player) != null) {
			this.picked = Classes.classOf(minecraft.player);
			this.option = this.picked.options.indexOf(Classes.of(minecraft.player).option());
		}
	}

	/** Whether this is a player changing what they picked with the class they hold. */
	private boolean respec() {
		Minecraft minecraft = Minecraft.getInstance();
		return this.switching && minecraft.player != null && !minecraft.player.isCreative() && Classes.classOf(minecraft.player) != null;
	}

	/** The panel's top-left corner, for the H menu's tabs. */
	int[] corner() {
		return new int[] {this.layout.figX, MenuCompat.STRIP};
	}

	/** Whether the screen is done: a class chosen (not while a master teaches a second, nor while a creative player is switching). */
	private boolean done(Minecraft minecraft) {
		return this.teaching == null && !this.switching && minecraft.player != null && Classes.decided(minecraft.player);
	}

	/** After Choose, how long the class may take to come back before the screen is put up again (ms). */
	static final long CHOOSE_GRACE_MS = 3000L;
	/** The server asked for a class that can't be put off: the screen comes back whenever it's shut. */
	private static boolean owed;
	private static long quietUntil;

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Classes.Open.TYPE, (payload, context) -> {
			owed |= payload.forced();
			context.client().gui.setScreen(new ClassScreen(payload.forced(), payload.second()));
		});
		// nothing else (another mod's screen, a pause) puts character creation away
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!owed || minecraft.player == null) {
				return;
			}
			if (Classes.decided(minecraft.player)) {
				owed = false;
			} else if (minecraft.gui.screen() == null && System.currentTimeMillis() > quietUntil) {
				minecraft.gui.setScreen(new ClassScreen(true, 0));
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

	private boolean noneButton() {
		return this.teaching == null && !this.switching;
	}

	private int buttonCount() {
		return 1 + (this.forced ? 0 : 1) + (noneButton() ? 1 : 0);
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		if (done(minecraft)) {
			onClose();
			return;
		}
		this.layout.fit(this.width, this.height, MenuCompat.STRIP, buttonCount());
		int[] b = this.layout.button(0);
		this.choose = addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.class.choose"), x -> {
			if (this.picked != null) {
				ClientPlayNetworking.send(new Classes.Choose(this.picked.ordinal(), this.option, this.teaching != null));
				quietUntil = System.currentTimeMillis() + CHOOSE_GRACE_MS;
				if (!this.switching) {
					onClose();
				}
			}
		}).bounds(b[0], b[1], b[2], b[3]).build());
		int at = 1;
		if (noneButton()) {
			int[] n = this.layout.button(at++);
			// start with no class yet, and learn one later from a teacher
			this.none = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.class.none"), x -> {
				ClientPlayNetworking.send(new Classes.Choose(Classes.NONE_CHOICE, -1, false));
				quietUntil = System.currentTimeMillis() + CHOOSE_GRACE_MS;
				onClose();
			}).bounds(n[0], n[1], n[2], n[3]).build();
			this.none.setTooltip(Ui.tipOf(Component.translatable("gui.jaymoskingdoms.class.none.tip")));
			addRenderableWidget(this.none);
		}
		if (!this.forced) {
			int[] l = this.layout.button(at);
			this.later = addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.class.later"), x -> onClose())
				.bounds(l[0], l[1], l[2], l[3]).build());
		}
		this.layout.reveal(PlayerClass.values().length, this.picked == null ? 0 : this.picked.ordinal());
	}

	private @Nullable PlayerClass rowAt(int mouseX, int mouseY) {
		PlayerClass[] all = PlayerClass.values();
		int i = this.layout.rowAt(all.length, mouseX, mouseY);
		return i < 0 ? null : all[i];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int)event.x(), my = (int)event.y();
		PlayerClass row = rowAt(mx, my);
		if (row != null) {
			// a second class taught by a master, or the class you hold while you change its pick, can't be swapped for another
			if (this.teaching != null && row != this.teaching || respec() && row != this.picked) {
				return true;
			}
			if (row != this.picked) {
				this.picked = row;
				this.option = -1;
			}
			click();
			return true;
		}
		int path = this.layout.pathAt(mx, my);
		if (path >= 0 && this.picked != null && path < this.picked.options.size()) {
			this.option = path;
			click();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.layout.scrolled(mouseX, mouseY, scrollY, PlayerClass.values().length)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private List<ItemStack> kit(PlayerClass cls, String option) {
		return this.kits.computeIfAbsent(cls.key() + "/" + option, k -> Classes.gear(cls, option));
	}

	private List<ItemStack> kit(PlayerClass cls) {
		return kit(cls, cls.options.isEmpty() ? "" : cls.options.getFirst());
	}

	private ItemStack icon(PlayerClass cls) {
		List<ItemStack> kit = kit(cls);
		return kit.isEmpty() ? ItemStack.EMPTY : kit.getFirst();
	}

	private static int colour(PlayerClass cls) {
		return 0xFF000000 | ClassTrees.colour(cls);
	}

	/** Ticks until the pick may be changed again, per this client's synced copy. */
	private long respecWait() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null || minecraft.level == null ? 0L : Classes.respecWait(minecraft.player, minecraft.level.getGameTime());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Minecraft minecraft = Minecraft.getInstance();
		// a first-class Cleric serves the patron their story raised them to; a second class picks freely
		if (this.picked != null && this.teaching == null && minecraft.player != null) {
			String raised = Classes.clericPatron(minecraft.player, this.picked);
			if (raised != null) {
				this.option = this.picked.options.indexOf(raised);
			}
		}
		if (done(minecraft)) {
			onClose();
			return;
		}
		this.layout.fit(this.width, this.height, MenuCompat.STRIP, buttonCount());
		PlayerClass[] all = PlayerClass.values();
		PlayerClass hover = rowAt(mouseX, mouseY);
		PlayerClass cls = hover != null ? hover : this.picked != null ? this.picked : all[0];
		this.layout.header(graphics, this.font, this.title, Component.translatable("gui.jaymoskingdoms.class.sub"), Ui.GOLD);

		List<PickerLayout.Entry> entries = new ArrayList<>();
		for (PlayerClass c : all) {
			entries.add(new PickerLayout.Entry(icon(c), null, Component.translatable("class.jaymoskingdoms." + c.key()),
				PickerLayout.tag("class.jaymoskingdoms." + c.key() + ".tag"), colour(c), false));
		}
		this.layout.list(graphics, this.font, entries, this.picked == null ? -1 : this.picked.ordinal(), hover == null ? -1 : hover.ordinal(), Ui.GOLD);

		// the figure, in the gear of the class (and the option) shown
		String shownOption = cls == this.picked && this.option >= 0 && this.option < cls.options.size() ? cls.options.get(this.option) : cls.options.isEmpty() ? "" : cls.options.getFirst();
		this.layout.backdrop(graphics, colour(cls), StoryFigure.SCALE);
		int[] box = this.layout.figureBox();
		if (minecraft.player != null) {
			StoryFigure.drawAs(graphics, minecraft.player, null, 0, StoryFigure.classScene(kit(cls, shownOption)), box[0], box[1], box[2], box[3]);
		}
		this.layout.caption(graphics, this.font, Component.translatable("gui.jaymoskingdoms.picker.as", Component.translatable("class.jaymoskingdoms." + cls.key())), null);

		this.layout.info(graphics, this.font, info(cls, shownOption), cls.key() + "/" + (cls == this.picked), mouseX, mouseY);

		if (this.choose != null) {
			int[] b = this.layout.button(0);
			this.choose.setPosition(b[0], b[1]);
			this.choose.setWidth(b[2]);
			boolean respec = respec();
			this.choose.setMessage(this.picked == null ? Component.translatable("gui.jaymoskingdoms.class.choose")
				: respec ? Component.translatable("gui.jaymoskingdoms.picker.change", this.option >= 0
					? Component.translatable("class.jaymoskingdoms.option." + this.picked.options.get(this.option)) : Component.translatable("class.jaymoskingdoms." + this.picked.key()))
				: Component.translatable("gui.jaymoskingdoms.picker.become", PickerLayout.withArticle(Component.translatable("class.jaymoskingdoms." + this.picked.key()).getString())));
			this.choose.active = this.picked != null && (this.picked.options.isEmpty() || this.option >= 0);
			if (respec) {
				Classes.Chosen mine = Classes.of(minecraft.player);
				this.choose.active = this.option >= 0 && Classes.respecable(this.picked) && !this.picked.options.get(this.option).equals(mine.option()) && respecWait() == 0L;
			}
		}
		int at = 1;
		if (this.none != null) {
			int[] n = this.layout.button(at++);
			this.none.setPosition(n[0], n[1]);
			this.none.setWidth(n[2]);
		}
		if (this.later != null) {
			int[] l = this.layout.button(at);
			this.later.setPosition(l[0], l[1]);
			this.later.setWidth(l[2]);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** The info panel for {@code cls}, its option {@code shown} under the figure. */
	private PickerLayout.Info info(PlayerClass cls, String shown) {
		boolean isPicked = cls == this.picked;
		List<Component> story = new ArrayList<>();
		story.add(Component.translatable("class.jaymoskingdoms." + cls.key() + ".desc"));
		if (this.picked == null) {
			story.add(Component.translatable("gui.jaymoskingdoms.class.lead").withColor(Ui.HINT));
		}
		List<PickerLayout.Pill> pills = new ArrayList<>();
		if (!cls.options.isEmpty()) {
			pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.options").getString(), String.valueOf(cls.options.size()), Ui.GOLD));
		}
		pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.picker.second").getString(), Math.round(Classes.SECOND_SHARE * 100) + "%", Ui.DIM));
		if (isPicked) {
			pills.add(new PickerLayout.Pill(Component.translatable("gui.jaymoskingdoms.class.picked").getString(), "", Ui.GOOD));
		}

		List<PickerLayout.Section> sections = new ArrayList<>();
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.intrinsic"), Ui.GOLD, ClassPerks.card(cls)));
		List<ItemStack> kit = kit(cls);
		net.minecraft.network.chat.MutableComponent goodWith = Component.empty();
		for (ItemStack stack : kit) {
			if (!stack.isEmpty()) {
				if (!goodWith.getString().isEmpty()) {
					goodWith.append(", ");
				}
				goodWith.append(stack.getHoverName());
			}
		}
		sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.class.gear"), Ui.GOOD, List.of(goodWith)));
		if (!cls.options.isEmpty()) {
			List<Component> spec = new ArrayList<>();
			if (Classes.respecable(cls)) {
				spec.add(Component.translatable("gui.jaymoskingdoms.picker.spec.cost", Classes.RESPEC_SILVER, Classes.RESPEC_WAIT_TICKS / 1200L));
				long wait = respec() && isPicked ? respecWait() : 0L;
				if (wait > 0L) {
					spec.add(Component.translatable("gui.jaymoskingdoms.picker.spec.wait", (wait + 1199L) / 1200L).withColor(Ui.BAD));
				}
			} else {
				spec.add(Component.translatable("gui.jaymoskingdoms.picker.spec.patron"));
			}
			sections.add(new PickerLayout.Section(Component.translatable("gui.jaymoskingdoms.picker.spec"), Ui.GOLD, spec));
		}
		// the option under the figure, in full
		if (isPicked && !shown.isEmpty()) {
			sections.add(new PickerLayout.Section(Component.translatable("class.jaymoskingdoms.option." + shown), Ui.TEXT,
				List.of(Component.translatable("class.jaymoskingdoms.option." + shown + ".desc"))));
		}

		// the paths: the options it picks (pressable on the picked class), then the crossroads its tree grows into
		List<PickerLayout.Path> paths = new ArrayList<>();
		for (int i = 0; i < cls.options.size(); i++) {
			List<ItemStack> icons = new ArrayList<>();
			for (ItemStack stack : kit(cls, cls.options.get(i))) {
				if (!stack.isEmpty() && icons.size() < 3) {
					icons.add(stack);
				}
			}
			paths.add(new PickerLayout.Path(icons, Component.translatable("class.jaymoskingdoms.option." + cls.options.get(i)), isPicked ? i : -1));
		}
		for (String fork : FORKS.getOrDefault(cls, List.of())) {
			paths.add(new PickerLayout.Path(List.of(icon(cls)), Component.translatable("classtree.jaymoskingdoms." + fork), -1));
		}
		Component note = cls.options.isEmpty() ? Component.translatable("gui.jaymoskingdoms.picker.paths_class", Component.translatable("class.jaymoskingdoms." + cls.key()))
			: Component.translatable(isPicked ? "gui.jaymoskingdoms.picker.paths_pick" : "gui.jaymoskingdoms.picker.paths_press");
		return new PickerLayout.Info(icon(cls), null, Component.translatable("class.jaymoskingdoms." + cls.key()), PickerLayout.tag("class.jaymoskingdoms." + cls.key() + ".tag"),
			colour(cls), pills, story, sections, note, paths, isPicked ? this.option : -1);
	}
}
