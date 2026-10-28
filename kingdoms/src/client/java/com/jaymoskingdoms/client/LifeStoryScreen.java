package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.LifeStory;
import com.jaymoskingdoms.LifeStory.Answer;
import com.jaymoskingdoms.LifeStory.Question;
import com.jaymoskingdoms.Voices;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.216) The life story ({@link LifeStory}): a newcomer's first screen after MCA's look and name, in place of the
 * heritage and class screens. One question a page, its answers down the right; the left tells the answer pointed at
 * or picked (its picture, the line of your past it writes, and what it gives). An answer that names a realm (a noble
 * family, a banishment, an oath) is followed by a page of the world's realms. The last page reads the whole story
 * back, with the heritage's look to step through and a Mage's first spell or a Sorcerer's element to pick, and "Live
 * this life" seals it. It can't be shut; the answers so far are kept if something else closes it. (0.7.275) "On second
 * thought..." asks whether to skip the story: yes picks every answer at random and turns to the story page, where it
 * can still be changed.
 */
final class LifeStoryScreen extends Screen {
	static final int MAX_W = 440, MAX_H = 300, TELL_W = 168, LIST_ROW = 19, MIN_ROW = 12, HEADER = 24, FOOTER = 24;
	/** The story's accent: an old parchment brown, apart from the heritage teal and the class gold. */
	static final int ACCENT = 0xFFC89A5A;
	/** After "Live this life", how long the story may take to come back before the screen is put up again (ms). */
	static final long LIVE_GRACE_MS = 3000L;
	/** Pixels the story page moves for one notch of the wheel. */
	static final int SCROLL_STEP = 12;
	/** (0.7.269) The pause between two lines the narrator reads, and how long a line is given to start (ms). */
	static final long READ_GAP_MS = 350L, READ_START_MS = 500L;
	/** (0.7.275) The skip-the-story ask: its box's size. */
	static final int ASK_W = 240, ASK_H = 96;
	/** (0.7.282) The figure of you at the foot of the left side ({@link StoryFigure}): its most height, and the least room it's drawn in. */
	static final int FIGURE_H = 96, FIGURE_MIN_H = 48;

	/** One page: a question, the realm to name for a question's answer, or the story read back. */
	private record Step(@Nullable Question question, boolean realm) {
		static final Step SUMMARY = new Step(null, false);
	}

	/**
	 * The answers so far: an index per question (-1: none yet), the realms named, the look and the option, the heritage
	 * and class picked over the story's (ordinals, -1: the story's), and the page.
	 */
	private static final class Draft {
		final int[] answers = new int[Question.values().length];
		int raisedRealm = -1, roadRealm = -1, look = -1, option = -1, heritage = -1, cls = -1, step;

		Draft() {
			Arrays.fill(this.answers, -1);
		}
	}

	private final boolean forced;
	private @Nullable Button back, next, look, heritageButton, classButton, later, skip, skipYes, skipNo;
	private int scroll, contentH, viewH;
	/** (0.7.275) Whether "On second thought..." is asking if they'd skip the story. */
	private boolean asking;

	/** The story owed (the screen comes back whenever it's shut), the realms there are to name, and the answers so far. */
	private static boolean owed;
	private static long quietUntil;
	private static List<LifeStory.Realm> realms = List.of();
	private static Draft draft = new Draft();
	/** (0.7.251) The page the narrator last began, so it reads each page once; the line being read and those still to read. */
	private static @Nullable String narrated;
	private static @Nullable SimpleSoundInstance reading;
	private static final ArrayDeque<String> toRead = new ArrayDeque<>();
	/** (0.7.269) When the line being read began, and when the next may (ms). */
	private static long readFrom, readNext;

	LifeStoryScreen(boolean forced) {
		super(Component.translatable("gui.jaymoskingdoms.story.title"));
		this.forced = forced;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LifeStory.Open.TYPE, (payload, context) -> {
			owed |= payload.forced();
			realms = payload.realms();
			StoryCurtain.raise();
			context.client().gui.setScreen(new LifeStoryScreen(payload.forced()));
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!owed || minecraft.player == null) {
				return;
			}
			if (done()) {
				owed = false;
				draft = new Draft();
			} else if (minecraft.gui.screen() == null && System.currentTimeMillis() > quietUntil) {
				minecraft.gui.setScreen(new LifeStoryScreen(true));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			owed = false;
			draft = new Draft();
			narrated = null;
			toRead.clear();
		});
	}

	/** Whether the story is still owed (the screen comes back whenever it's shut). */
	static boolean owed() {
		return owed;
	}

	/** Whether the story is told: it came back from the server. */
	private static boolean done() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && LifeStory.of(minecraft.player) != null;
	}

	@Override
	public void removed() {
		// (0.7.286) the stand-in figure is no longer MCA's editor figure
		StoryFigure.closed();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.forced;
	}

	// ---- the pages ---------------------------------------------------------------------------------------------------

	/** The pages as the answers so far make them: each question asked, a realm page after an answer that names one, then the story. */
	private static List<Step> steps() {
		List<Step> out = new ArrayList<>();
		for (Question q : Question.values()) {
			if (!q.asked()) {
				continue;
			}
			out.add(new Step(q, false));
			Answer a = picked(q);
			if (a != null && a.realm()) {
				out.add(new Step(q, true));
			}
		}
		out.add(Step.SUMMARY);
		return out;
	}

	private static Step step() {
		List<Step> all = steps();
		draft.step = Math.clamp(draft.step, 0, all.size() - 1);
		return all.get(draft.step);
	}

	private static @Nullable Answer picked(Question q) {
		int i = draft.answers[q.ordinal()];
		return i >= 0 && i < q.answers().size() ? q.answers().get(i) : null;
	}

	/** The realm named for {@code q}'s answer (-1: none). */
	private static int realmOf(Question q) {
		return q == Question.RAISED ? draft.raisedRealm : draft.roadRealm;
	}

	private static void setRealm(Question q, int id) {
		if (q == Question.RAISED) {
			draft.raisedRealm = id;
		} else {
			draft.roadRealm = id;
		}
	}

	/** Whether an answer can be picked: one that names a realm only while there's a realm to name; one offered here. */
	private static boolean open(Answer a) {
		return a.offered() && (!a.realm() || !realms.isEmpty());
	}

	/** Whether the page is answered, so Next can go on. */
	private static boolean answered(Step s) {
		if (s.question == null) {
			Classes.PlayerClass cls = youthClass();
			return cls == null || cls.options.isEmpty() || draft.option >= 0;
		}
		return s.realm ? realmOf(s.question) >= 0 : picked(s.question) != null;
	}

	/** The heritage the story makes (or the one picked over it), and its look (the childhood's leaning until one is picked). */
	private static Heritage.@Nullable Kind heritage() {
		if (draft.heritage >= 0 && draft.heritage < Heritage.Kind.values().length) {
			return Heritage.Kind.values()[draft.heritage];
		}
		return storyHeritage();
	}

	private static Heritage.@Nullable Kind storyHeritage() {
		Answer born = picked(Question.BORN);
		return born == null ? null : born.heritage();
	}

	/**
	 * The next heritage open to them after the one shown (wrapping), or (0.7.282, a right click) the one before; back at
	 * the story's, the override is dropped.
	 */
	private static void cycleHeritage(int step) {
		Heritage.Kind story = storyHeritage(), now = heritage();
		Minecraft minecraft = Minecraft.getInstance();
		if (story == null || now == null || minecraft.player == null) {
			return;
		}
		Heritage.Kind[] kinds = Heritage.Kind.values();
		for (int i = 1; i <= kinds.length; i++) {
			Heritage.Kind next = kinds[Math.floorMod(now.ordinal() + i * step, kinds.length)];
			if (Heritage.unlocked(minecraft.player, next)) {
				draft.heritage = next == story ? -1 : next.ordinal();
				draft.look = -1;
				return;
			}
		}
	}

	/** The next class after the one shown (wrapping), or the one before; back at the story's, the override is dropped. */
	private static void cycleClass(int step) {
		Answer youth = picked(Question.YOUTH);
		Classes.PlayerClass story = youth == null ? null : youth.cls(), now = youthClass();
		if (story == null || now == null) {
			return;
		}
		Classes.PlayerClass[] classes = Classes.PlayerClass.values();
		Classes.PlayerClass next = classes[Math.floorMod(now.ordinal() + step, classes.length)];
		draft.cls = next == story ? -1 : next.ordinal();
		draft.option = -1;
	}

	/** The heritage's next look after the one shown (wrapping), or the one before. */
	private static void cycleLook(int step) {
		Heritage.Kind kind = heritage();
		if (kind != null) {
			draft.look = Math.floorMod(lookIndex() + step, HeritageVariants.of(kind).size() + 1);
		}
	}

	private static int lookIndex() {
		Heritage.Kind kind = heritage();
		if (kind == null) {
			return 0;
		}
		return draft.look >= 0 ? draft.look : LifeStory.lookFor(kind, picked(Question.CHILDHOOD));
	}

	// ---- layout ------------------------------------------------------------------------------------------------------

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return Math.min(MAX_H, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int paneX() {
		return left() + TELL_W;
	}

	private int paneW() {
		return panelW() - TELL_W;
	}

	/** The rows the page lists: a question's answers, or the realms. */
	private int rowCount(Step s) {
		return s.question == null ? 0 : s.realm ? realms.size() : s.question.answers().size();
	}

	private int rowH(Step s) {
		int fit = (panelH() - HEADER - FOOTER - 4) / Math.max(1, rowCount(s));
		return Math.clamp(fit, MIN_ROW, LIST_ROW);
	}

	private int rowY(Step s, int index) {
		return top() + HEADER + index * rowH(s) - this.scroll;
	}

	private int rowAt(Step s, int mouseX, int mouseY) {
		int viewTop = top() + HEADER, viewBottom = top() + panelH() - FOOTER;
		if (mouseY < viewTop || mouseY >= viewBottom) {
			return -1;
		}
		int rh = rowH(s);
		for (int i = 0; i < rowCount(s); i++) {
			if (Ui.over(mouseX, mouseY, paneX() + 6, rowY(s, i), paneW() - 14, rh - 2)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void init() {
		if (done()) {
			onClose();
			return;
		}
		int y = top() + panelH() - FOOTER + 4;
		int right = paneX() + paneW() - 6;
		this.next = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.story.next"), b -> forward()).bounds(right - 110, y, 110, 16).build());
		this.back = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.story.back"), b -> {
			draft.step = Math.max(0, draft.step - 1);
			this.scroll = 0;
		}).bounds(paneX() + 6, y, 60, 16).build());
		// on the story page: the heritage's look, stepped through on each press
		this.look = addRenderableWidget(Button.builder(Component.empty(), b -> cycleLook(1)).bounds(left() + 6, y, TELL_W - 12, 16).build());
		// (Andy: "allow a player override AFTER the result") the heritage and class, stepped past the story's on each press
		this.heritageButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleHeritage(1)).bounds(left() + 6, y - 40, TELL_W - 12, 16).build());
		this.classButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleClass(1)).bounds(left() + 6, y - 20, TELL_W - 12, 16).build());
		if (!this.forced) {
			this.later = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.later"), b -> onClose()).bounds(paneX() + 70, y, 50, 16).build());
		}
		// (0.7.275) Andy: "a button that says 'on second thought...' then asks if you would like to skip the story"; on the
		// question pages it sits where the story page's look button does
		this.skip = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.story.skip"), b -> this.asking = true)
			.bounds(left() + 6, y, TELL_W - 12, 16).build());
		int ax = (this.width - ASK_W) / 2, ay = top() + (panelH() - ASK_H) / 2;
		this.skipYes = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.story.skip.yes"), b -> {
			randomize();
			this.asking = false;
			this.scroll = 0;
		}).bounds(ax + 10, ay + ASK_H - 24, ASK_W / 2 - 14, 16).build());
		this.skipNo = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.story.skip.no"), b -> this.asking = false)
			.bounds(ax + ASK_W / 2 + 4, ay + ASK_H - 24, ASK_W / 2 - 14, 16).build());
	}

	/**
	 * (0.7.275) Skip the story: every question answered at random (only answers open here, a realm drawn for one that
	 * names one), a Mage's spell or a Sorcerer's element drawn too, nothing picked over the story, then the story page,
	 * where Back and the heritage and class buttons can still change any of it.
	 */
	private static void randomize() {
		java.util.Random random = new java.util.Random();
		for (Question q : Question.values()) {
			List<Integer> open = new ArrayList<>();
			for (int i = 0; i < q.answers().size(); i++) {
				if (open(q.answers().get(i))) {
					open.add(i);
				}
			}
			int pick = !q.asked() || open.isEmpty() ? -1 : open.get(random.nextInt(open.size()));
			draft.answers[q.ordinal()] = pick;
			if (pick >= 0 && q.answers().get(pick).realm()) {
				setRealm(q, realms.get(random.nextInt(realms.size())).id());
			}
		}
		draft.heritage = -1;
		draft.cls = -1;
		draft.look = -1;
		Classes.PlayerClass cls = youthClass();
		draft.option = cls == null || cls.options.isEmpty() ? -1 : random.nextInt(cls.options.size());
		draft.step = steps().size() - 1;
	}

	/**
	 * (0.7.251) The narrator reads a page when it first shows ({@link Voices}): a question's title and the line under it,
	 * a realm page's ask, and (0.7.269) on the story page the whole tale, line by line with {@link #READ_GAP_MS} between,
	 * then the closing line. Turning the page cuts the reading short. The realm a tale names isn't read: the clip says
	 * "the realm".
	 */
	private static void narrate(Step s) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		String page = s.question == null ? "story" + Arrays.toString(draft.answers) : String.join(".", lines(s));
		if (!page.equals(narrated)) {
			narrated = page;
			if (reading != null) {
				minecraft.getSoundManager().stop(reading);
				reading = null;
			}
			toRead.clear();
			toRead.addAll(lines(s));
			readNext = 0L;
		}
		if (reading != null) {
			// (a sound only counts as playing once the engine has picked it up, so give it a moment first)
			if (now - readFrom < READ_START_MS || minecraft.getSoundManager().isActive(reading)) {
				return;
			}
			reading = null;
			readNext = now + READ_GAP_MS;
		}
		if (now < readNext) {
			return;
		}
		while (!toRead.isEmpty()) {
			Holder<SoundEvent> voice = Voices.of(toRead.poll());
			if (voice != null) {
				reading = SimpleSoundInstance.forUI(voice.value(), 1.0F, 1.0F);
				readFrom = now;
				minecraft.getSoundManager().play(reading);
				return;
			}
		}
	}

	/** The lines the narrator reads on page {@code s}, as lang keys. */
	private static List<String> lines(Step s) {
		if (s.question == null) {
			List<String> out = new ArrayList<>();
			for (Question q : Question.values()) {
				Answer a = q.asked() ? picked(q) : null;
				if (a != null) {
					out.add("story.jaymoskingdoms." + q.key() + "." + a.key() + ".tale");
				}
			}
			out.add(Voices.STORY_END);
			return out;
		}
		if (s.realm) {
			Answer a = picked(s.question);
			return List.of("story.jaymoskingdoms.realm." + (a == null ? "raised" : a.key()));
		}
		return List.of("story.jaymoskingdoms." + s.question.key() + ".title");
	}

	/** Next: on to the next page, or on the last, live this life. */
	private void forward() {
		Step s = step();
		if (!answered(s)) {
			return;
		}
		if (s.question != null) {
			draft.step++;
			this.scroll = 0;
			return;
		}
		List<Integer> answers = new ArrayList<>();
		for (Question q : Question.values()) {
			answers.add(q.asked() ? draft.answers[q.ordinal()] : -1);
		}
		Answer raised = picked(Question.RAISED), road = picked(Question.ROAD);
		ClientPlayNetworking.send(new LifeStory.Live(List.copyOf(answers), raised != null && raised.realm() ? draft.raisedRealm : -1,
			road != null && road.realm() ? draft.roadRealm : -1, lookIndex(), draft.option, draft.heritage, draft.cls));
		quietUntil = System.currentTimeMillis() + LIVE_GRACE_MS;
		onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.asking) {
			// (only the ask's buttons answer while it's up)
			return super.mouseClicked(event, doubleClick);
		}
		Step s = step();
		int mx = (int)event.x(), my = (int)event.y();
		// (0.7.282) Andy: "right clicking should take you back on any of these options": the heritage, class and look
		// buttons step back, and the element or spell chips too
		if (event.button() == 1) {
			Runnable back = null;
			if (over(this.heritageButton, mx, my)) {
				back = () -> cycleHeritage(-1);
			} else if (over(this.classButton, mx, my)) {
				back = () -> cycleClass(-1);
			} else if (over(this.look, mx, my)) {
				back = () -> cycleLook(-1);
			} else if (s.question == null && chipAt(mx, my) >= 0) {
				Classes.PlayerClass cls = youthClass();
				int n = cls == null ? 0 : cls.options.size();
				back = () -> draft.option = n == 0 ? -1 : Math.floorMod((draft.option < 0 ? 0 : draft.option) - 1, n);
			}
			if (back != null) {
				back.run();
				click();
				return true;
			}
		}
		if (s.question != null) {
			int row = rowAt(s, mx, my);
			if (row >= 0) {
				if (s.realm) {
					setRealm(s.question, realms.get(row).id());
				} else {
					Answer a = s.question.answers().get(row);
					if (!open(a)) {
						return true;
					}
					if (draft.answers[s.question.ordinal()] != row) {
						draft.answers[s.question.ordinal()] = row;
						// a new answer drops what was picked over it
						if (s.question == Question.BORN) {
							draft.heritage = -1;
						}
						if (s.question == Question.YOUTH) {
							draft.cls = -1;
						}
						// a new birthplace starts its look over; a new youth its option
						if (s.question == Question.BORN || s.question == Question.CHILDHOOD) {
							draft.look = -1;
						}
						if (s.question == Question.YOUTH) {
							draft.option = -1;
						}
					}
				}
				click();
				return true;
			}
		} else {
			int chip = chipAt(mx, my);
			if (chip >= 0) {
				draft.option = chip;
				click();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.asking) {
			return true;
		}
		if (this.contentH > this.viewH && mouseX >= paneX()) {
			this.scroll = Math.clamp(this.scroll - (int)Math.round(scrollY * SCROLL_STEP), 0, this.contentH - this.viewH);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private static boolean over(@Nullable Button b, int mx, int my) {
		return b != null && b.visible && b.active && b.isMouseOver(mx, my);
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	// ---- drawing -----------------------------------------------------------------------------------------------------

	/** (0.7.238) Andy: black behind the story, never the world they're about to land in. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		StoryCurtain.background(graphics, this);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (done()) {
			onClose();
			return;
		}
		Step s = step();
		narrate(s);
		int x = left(), y = top(), w = panelW(), h = panelH();
		Ui.panel(graphics, x, y, w, h, ACCENT);
		graphics.fill(x + TELL_W - 1, y + 4, x + TELL_W, y + h - 4, Ui.ROW);

		// the header over the right: the question (or the realm's, or the story's); (0.7.269) the line under it is only heard
		int px = paneX(), pw = paneW();
		Ui.line(graphics, this.font, heading(s), px + 8, y + 7, pw - 16, Ui.TEXT);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);

		int viewTop = y + HEADER, viewBottom = y + h - FOOTER;
		this.viewH = viewBottom - viewTop;
		graphics.enableScissor(px + 1, viewTop, px + pw - 1, viewBottom);
		if (s.question != null) {
			rows(graphics, s, mouseX, mouseY);
		} else {
			summary(graphics, mouseX, mouseY);
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, px + pw - 4, viewTop, this.viewH, this.viewH, this.contentH, this.scroll);

		tell(graphics, s, mouseX, mouseY);

		if (this.next != null) {
			this.next.setMessage(Component.translatable(s.question == null ? "gui.jaymoskingdoms.story.live" : "gui.jaymoskingdoms.story.next"));
			this.next.active = answered(s);
		}
		if (this.back != null) {
			this.back.active = draft.step > 0;
		}
		if (this.look != null) {
			Heritage.Kind kind = heritage();
			this.look.visible = s.question == null && kind != null;
			if (kind != null) {
				this.look.setMessage(Component.translatable("gui.jaymoskingdoms.heritage.variant", HeritageVariants.title(kind, lookIndex()),
					lookIndex() + 1, HeritageVariants.of(kind).size() + 1));
			}
		}
		Heritage.Kind kind = heritage();
		Classes.PlayerClass cls = youthClass();
		if (this.heritageButton != null) {
			this.heritageButton.visible = s.question == null && kind != null;
			if (kind != null) {
				this.heritageButton.setMessage(Component.translatable("gui.jaymoskingdoms.story.heritage", Component.translatable("heritage.jaymoskingdoms." + kind.key()))
					.withColor(draft.heritage >= 0 ? ACCENT : Ui.TEXT));
			}
		}
		if (this.classButton != null) {
			this.classButton.visible = s.question == null && cls != null;
			if (cls != null) {
				this.classButton.setMessage(Component.translatable("gui.jaymoskingdoms.story.class", Component.translatable("class.jaymoskingdoms." + cls.key()))
					.withColor(draft.cls >= 0 ? ACCENT : Ui.TEXT));
			}
		}
		if (this.skip != null) {
			this.skip.visible = s.question != null;
		}
		if (this.asking) {
			ask(graphics);
			for (Button b : new Button[] {this.next, this.back, this.look, this.heritageButton, this.classButton, this.later, this.skip}) {
				if (b != null) {
					b.visible = false;
				}
			}
		} else {
			// (0.7.282) the footer comes back once the ask is answered: without this, Next and Back stayed hidden
			// after a skip and the story could never be lived
			for (Button b : new Button[] {this.next, this.back, this.later}) {
				if (b != null) {
					b.visible = true;
				}
			}
		}
		if (this.skipYes != null && this.skipNo != null) {
			this.skipYes.visible = this.asking;
			this.skipNo.visible = this.asking;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	/** (0.7.275) The skip-the-story ask, over the dimmed page: the question, what skipping does, and the yes and no under it. */
	private void ask(GuiGraphicsExtractor graphics) {
		graphics.fill(left(), top(), left() + panelW(), top() + panelH(), 0xC0000000);
		int x = (this.width - ASK_W) / 2, y = top() + (panelH() - ASK_H) / 2;
		Ui.panel(graphics, x, y, ASK_W, ASK_H, ACCENT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.story.skip.ask"), x + 10, y + 8, ASK_W - 20, ACCENT);
		int ty = y + 24;
		for (FormattedCharSequence line : this.font.split(Component.translatable("gui.jaymoskingdoms.story.skip.desc"), ASK_W - 20)) {
			if (ty + Ui.LINE > y + ASK_H - 26) {
				break;
			}
			graphics.text(this.font, line, x + 10, ty, Ui.TEXT);
			ty += Ui.LINE;
		}
	}

	private Component heading(Step s) {
		if (s.question == null) {
			return Component.translatable("gui.jaymoskingdoms.story.summary");
		}
		return s.realm ? Component.translatable("story.jaymoskingdoms.realm.title") : Component.translatable("story.jaymoskingdoms." + s.question.key() + ".title");
	}

	/** A question's answers (or the realms) down the right, the picked one marked. */
	private void rows(GuiGraphicsExtractor graphics, Step s, int mouseX, int mouseY) {
		int n = rowCount(s), rh = rowH(s);
		this.contentH = n * rh;
		this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.contentH - this.viewH));
		int hover = rowAt(s, mouseX, mouseY);
		int x0 = paneX() + 6, x1 = paneX() + paneW() - 8;
		boolean icons = rh >= 17;
		for (int i = 0; i < n; i++) {
			int ry = rowY(s, i);
			boolean picked, open = true;
			Component label;
			ItemStack icon = ItemStack.EMPTY;
			if (s.realm) {
				LifeStory.Realm realm = realms.get(i);
				picked = realm.id() == realmOf(s.question);
				label = Component.literal(realm.name());
			} else {
				Answer a = s.question.answers().get(i);
				picked = draft.answers[s.question.ordinal()] == i;
				open = open(a);
				label = Component.translatable("story.jaymoskingdoms." + s.question.key() + "." + a.key());
				icon = new ItemStack(a.icon());
			}
			graphics.fill(x0, ry, x1, ry + rh - 2, picked ? Ui.ROW_PICKED : i == hover && open ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			if (picked) {
				graphics.fill(x0, ry, x0 + 2, ry + rh - 2, ACCENT);
			}
			int tx = x0 + 6;
			if (icons && !icon.isEmpty()) {
				graphics.item(icon, x0 + 4, ry + 1);
				tx = x0 + 23;
			}
			Ui.line(graphics, this.font, label, tx, ry + (rh - 2 - 8) / 2 + 1, x1 - tx - 4, picked ? ACCENT : !open ? Ui.HINT : i == hover ? Ui.TEXT : Ui.DIM);
		}
	}

	/** The story page: the whole tale, then what it makes of you, then (a Mage's, a Sorcerer's) the option chips. */
	private void summary(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int px = paneX(), pw = paneW(), textW = pw - 22;
		int y = top() + HEADER + 4 - this.scroll;
		int startY = y;
		List<FormattedCharSequence> tale = this.font.split(tale(), textW);
		for (FormattedCharSequence line : tale) {
			graphics.text(this.font, line, px + 10, y, Ui.TEXT);
			y += Ui.LINE;
		}
		y += 6;
		Ui.rule(graphics, px + 6, px + pw - 6, y - 3);
		int i = 0;
		for (Component row : results()) {
			List<FormattedCharSequence> split = this.font.split(row, textW);
			int rh = split.size() * Ui.LINE + 4;
			Ui.row(graphics, px + 6, y, px + pw - 8, y + rh - 1, i++, false);
			for (int l = 0; l < split.size(); l++) {
				graphics.text(this.font, split.get(l), px + 10, y + 2 + l * Ui.LINE, Ui.GOOD);
			}
			y += rh;
		}
		Classes.PlayerClass cls = youthClass();
		if (cls != null && !cls.options.isEmpty()) {
			y += 4;
			graphics.text(this.font, Component.translatable(cls == Classes.PlayerClass.MAGE ? "gui.jaymoskingdoms.story.spell" : "gui.jaymoskingdoms.story.element"),
				px + 10, y, draft.option < 0 ? ACCENT : Ui.DIM);
			y += 12;
			this.chipsTop = y + this.scroll;
			int hover = chipAt(mouseX, mouseY);
			List<int[]> chips = chips(cls, y + this.scroll);
			for (int c = 0; c < chips.size(); c++) {
				int[] chip = chips.get(c);
				int cy = chip[1] - this.scroll;
				boolean chosen = c == draft.option;
				graphics.fill(chip[0], cy, chip[0] + chip[2], cy + 14, chosen ? Ui.ROW_PICKED : c == hover ? Ui.ROW_HOVER : Ui.CHIP);
				if (chosen) {
					graphics.fill(chip[0], cy + 12, chip[0] + chip[2], cy + 14, ACCENT);
				}
				graphics.centeredText(this.font, Component.translatable("class.jaymoskingdoms.option." + cls.options.get(c)), chip[0] + chip[2] / 2, cy + 3,
					chosen ? ACCENT : Ui.TEXT);
				y = Math.max(y, cy + 18);
			}
		} else {
			this.chipsTop = -1;
		}
		this.contentH = y + this.scroll - startY + 4;
		this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.contentH - this.viewH));
	}

	/** Where the option chips start (in unscrolled screen space, -1: none), as the story page last laid them out. */
	private int chipsTop = -1;

	/** The option chips laid out from {@code top} (unscrolled), as {x, y, w} each, wrapping. */
	private List<int[]> chips(Classes.PlayerClass cls, int top) {
		List<int[]> out = new ArrayList<>();
		int x0 = paneX() + 10, x = x0, y = top, right = paneX() + paneW() - 10;
		for (String option : cls.options) {
			int w = this.font.width(Component.translatable("class.jaymoskingdoms.option." + option)) + 12;
			if (x + w > right && x > x0) {
				x = x0;
				y += 17;
			}
			out.add(new int[] {x, y, w});
			x += w + 4;
		}
		return out;
	}

	private int chipAt(int mouseX, int mouseY) {
		Classes.PlayerClass cls = youthClass();
		if (this.chipsTop < 0 || cls == null || cls.options.isEmpty() || step().question != null) {
			return -1;
		}
		if (mouseY < top() + HEADER || mouseY >= top() + panelH() - FOOTER) {
			return -1;
		}
		List<int[]> chips = chips(cls, this.chipsTop);
		for (int c = 0; c < chips.size(); c++) {
			int[] chip = chips.get(c);
			if (Ui.over(mouseX, mouseY, chip[0], chip[1] - this.scroll, chip[2], 14)) {
				return c;
			}
		}
		return -1;
	}

	/** The class the story makes, or the one picked over it. */
	private static Classes.@Nullable PlayerClass youthClass() {
		if (draft.cls >= 0 && draft.cls < Classes.PlayerClass.values().length) {
			return Classes.PlayerClass.values()[draft.cls];
		}
		Answer youth = picked(Question.YOUTH);
		return youth == null ? null : youth.cls();
	}

	/** The whole story as one tale: each answer's line, in order. */
	private static Component tale() {
		var out = Component.empty();
		for (Question q : Question.values()) {
			Answer a = q.asked() ? picked(q) : null;
			if (a == null) {
				continue;
			}
			if (!out.getSiblings().isEmpty()) {
				out.append(" ");
			}
			out.append(Component.translatable("story.jaymoskingdoms." + q.key() + "." + a.key() + ".tale", realmName(q)));
		}
		return out;
	}

	/** The realm named for {@code q}'s answer, by name ("" with none). */
	private static String realmName(Question q) {
		int id = realmOf(q);
		for (LifeStory.Realm r : realms) {
			if (r.id() == id) {
				return r.name();
			}
		}
		return "";
	}

	/** What the story makes of you, a row each. */
	private static List<Component> results() {
		List<Component> out = new ArrayList<>();
		for (Question q : Question.values()) {
			Answer a = q.asked() ? picked(q) : null;
			if (a != null) {
				out.addAll(gives(q, a, true));
			}
		}
		return out;
	}

	/**
	 * What answer {@code a} to {@code q} gives, a line each. {@code story}: on the story page, where the childhood's power
	 * lean only shows if fate was left to choose.
	 */
	private static List<Component> gives(Question q, Answer a, boolean story) {
		List<Component> out = new ArrayList<>();
		if (a.heritage() != null) {
			Heritage.Kind kind = story && heritage() != null ? heritage() : a.heritage();
			out.add(changed(Component.translatable("gui.jaymoskingdoms.story.gives.heritage", Component.translatable("heritage.jaymoskingdoms." + kind.key())),
				story && draft.heritage >= 0));
		}
		if (a.stat() != null) {
			out.add(Component.translatable("gui.jaymoskingdoms.story.gives.stat", Component.translatable("stat.jaymoskingdoms." + a.stat().key())));
		}
		if (a.freePoint()) {
			out.add(Component.translatable("gui.jaymoskingdoms.story.gives.free"));
		}
		if (a.realm() && q == Question.RAISED) {
			out.add(Component.translatable("gui.jaymoskingdoms.story.gives.noble"));
		}
		if (a.key().equals("merchants")) {
			out.add(Component.translatable("gui.jaymoskingdoms.story.gives.purse"));
		}
		if (a.cls() != null) {
			Classes.PlayerClass cls = story && youthClass() != null ? youthClass() : a.cls();
			out.add(changed(Component.translatable("gui.jaymoskingdoms.story.gives.class", Component.translatable("class.jaymoskingdoms." + cls.key())),
				story && draft.cls >= 0));
		}
		if (q == Question.CHILDHOOD) {
			Heritage.Kind kind = heritage();
			int look = kind == null ? 0 : LifeStory.lookFor(kind, a);
			if (!story && kind != null && look > 0) {
				out.add(Component.translatable("gui.jaymoskingdoms.story.gives.look", HeritageVariants.title(kind, look)));
			}
			// (0.7.228, Andy: "remove this text from every entry") no "leans to <power>" line
		}
		if (q == Question.TURN) {
			Answer road = picked(Question.ROAD);
			if (story && road != null && road.start() == LifeStory.Start.TIMELOST) {
				// (lost in time wakes Chrono at once, whatever this was)
				out.add(Component.translatable("gui.jaymoskingdoms.story.gives.timelost", power("chrono")));
			}
			// (0.7.249) Andy: "this screen should not show the class you will get": what wakes is a surprise
		}
		if (a.start() != null) {
			out.add(Component.translatable("story.jaymoskingdoms.road." + a.key() + ".wake"));
			Answer turn = picked(Question.TURN);
			String echo = LifeStory.echo(a.start());
			if (LifeStory.powers() && !echo.isEmpty() && turn != null && echo.equals(turn.power())) {
				out.add(Component.translatable("gui.jaymoskingdoms.story.gives.echo"));
			}
		}
		return out;
	}

	/** {@code line}, marked as picked over the story's when {@code changed}. */
	private static Component changed(net.minecraft.network.chat.MutableComponent line, boolean changed) {
		return changed ? line.append(" ").append(Component.translatable("gui.jaymoskingdoms.story.override")) : line;
	}

	private static Component power(String id) {
		return Component.translatable("power.jaymospowers." + id);
	}

	/** The left: the answer pointed at or picked, its line of your past and what it gives; on the story page, your heritage's look. */
	private void tell(GuiGraphicsExtractor graphics, Step s, int mouseX, int mouseY) {
		int x = left(), y = top(), w = TELL_W;
		int textW = w - 18;
		Component name;
		ItemStack icon = ItemStack.EMPTY;
		List<Component> lines = new ArrayList<>();
		List<Component> good = new ArrayList<>();
		if (s.question == null) {
			Heritage.Kind kind = heritage();
			if (kind == null) {
				return;
			}
			int look = lookIndex();
			icon = new ItemStack(kind.icon);
			name = Component.translatable("heritage.jaymoskingdoms." + kind.key());
			String root = HeritageVariants.langKey(kind, look);
			lines.add(Component.translatable("gui.jaymoskingdoms.story.look", HeritageVariants.title(kind, look)).withColor(ACCENT));
			lines.add(Component.translatable(root + ".desc"));
			good.add(Component.translatable(root + ".trait"));
		} else if (s.realm) {
			int row = rowAt(s, mouseX, mouseY);
			int id = row >= 0 ? realms.get(row).id() : realmOf(s.question);
			String realm = "";
			for (LifeStory.Realm r : realms) {
				if (r.id() == id) {
					realm = r.name();
				}
			}
			name = realm.isEmpty() ? Component.translatable("story.jaymoskingdoms.realm.title") : Component.literal(realm);
			lines.add(Component.translatable("story.jaymoskingdoms.realm." + keyOf(picked(s.question)) + ".desc"));
		} else {
			int row = rowAt(s, mouseX, mouseY);
			Answer a = row >= 0 ? s.question.answers().get(row) : picked(s.question);
			if (a == null) {
				name = Component.translatable("story.jaymoskingdoms." + s.question.key() + ".title");
				lines.add(Component.translatable("gui.jaymoskingdoms.story.lead").withColor(Ui.DIM));
			} else {
				icon = new ItemStack(a.icon());
				name = Component.translatable("story.jaymoskingdoms." + s.question.key() + "." + a.key());
				lines.add(Component.translatable("story.jaymoskingdoms." + s.question.key() + "." + a.key() + ".tale", realmName(s.question)));
				good.addAll(gives(s.question, a, false));
				if (!open(a)) {
					lines.add(Component.translatable(a.offered() ? "gui.jaymoskingdoms.story.no_realms" : "gui.jaymoskingdoms.story.needs_powers").withColor(Ui.BAD));
				}
			}
		}
		int ty = y + 8;
		Ui.line(graphics, this.font, this.title, x + 8, ty, w - 16, Ui.DIM);
		ty += 14;
		if (!icon.isEmpty()) {
			Ui.well(graphics, x + 8, ty, 20, 20);
			graphics.item(icon, x + 10, ty + 2);
		}
		Ui.line(graphics, this.font, name, x + (icon.isEmpty() ? 9 : 33), ty + 6, w - 42, ACCENT);
		ty += 28;
		// (on the story page, above the heritage and class buttons)
		int bottom = y + panelH() - FOOTER - 2 - (s.question == null ? 40 : 0);
		// (0.7.282) the figure under the text, as much of it as there's room for
		int figureH = Math.min(FIGURE_H, bottom - ty - 3 * Ui.LINE);
		if (figureH >= FIGURE_MIN_H) {
			bottom -= figureH;
			figure(graphics, s, mouseX, mouseY, x + 8, bottom + 2, x + w - 8, bottom + figureH);
		}
		// (UI text pass) laid out first; a tale longer than the room scrolls slowly through it and back
		List<FormattedCharSequence> seqs = new ArrayList<>();
		List<Integer> at = new ArrayList<>(), colors = new ArrayList<>();
		int oy = 0, ruleAt = -1;
		for (Component line : lines) {
			int color = line.getStyle().getColor() != null ? 0xFF000000 | line.getStyle().getColor().getValue() : Ui.TEXT;
			for (FormattedCharSequence seq : this.font.split(line, textW)) {
				seqs.add(seq);
				at.add(oy);
				colors.add(color);
				oy += Ui.LINE;
			}
			oy += 3;
		}
		if (!good.isEmpty()) {
			oy += 2;
			ruleAt = oy - 3;
		}
		for (Component line : good) {
			for (FormattedCharSequence seq : this.font.split(line, textW)) {
				seqs.add(seq);
				at.add(oy);
				colors.add(Ui.GOOD);
				oy += Ui.LINE;
			}
		}
		int view = Math.max(0, bottom - ty), over = Math.max(0, oy - view);
		int shift = over > 0 ? Ui.slide(over, Ui.SCROLL_MS_PER_LINE / Ui.LINE, Ui.SCROLL_PAUSE_MS) : 0;
		if (over > 0) {
			graphics.enableScissor(x, ty - 1, x + w, bottom);
		}
		if (ruleAt >= 0) {
			Ui.rule(graphics, x + 6, x + w - 6, ty + ruleAt - shift);
		}
		for (int i = 0; i < seqs.size(); i++) {
			int ly = ty + at.get(i) - shift;
			if (ly > ty - Ui.LINE && ly < bottom) {
				graphics.text(this.font, seqs.get(i), x + 9, ly, colors.get(i));
			}
		}
		if (over > 0) {
			graphics.disableScissor();
			Ui.scrollbar(graphics, x + w - 6, ty, view - 1, view, oy, shift);
		}
	}

	/**
	 * (0.7.282) Andy: "an animated simulation of the character in every story frame, like in bannerlord ... helps the
	 * user see the effect of every race on their character". You, drawn as the page would make you: the heritage and
	 * look of the birthplace or childhood pointed at (or picked), acting out the answer in its gear ({@link StoryFigure}).
	 * On the story page it follows the heritage, look and class buttons, in the class's gear.
	 */
	private void figure(GuiGraphicsExtractor graphics, Step s, int mouseX, int mouseY, int x0, int y0, int x1, int y1) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		Heritage.Kind kind = heritage();
		int look = lookIndex();
		String scene = "";
		ItemStack icon = ItemStack.EMPTY;
		if (s.question != null) {
			int row = s.realm ? -1 : rowAt(s, mouseX, mouseY);
			Answer a = row >= 0 ? s.question.answers().get(row) : picked(s.question);
			if (a != null) {
				scene = s.question.key() + "." + a.key();
				icon = new ItemStack(a.icon());
				if (s.question == Question.BORN && a.heritage() != null) {
					kind = a.heritage();
					look = LifeStory.lookFor(kind, picked(Question.CHILDHOOD));
				} else if (s.question == Question.CHILDHOOD && kind != null && draft.look < 0) {
					look = LifeStory.lookFor(kind, a);
				}
			}
		} else {
			// (the class's scene, the one picked over the story's too)
			Classes.PlayerClass cls = youthClass();
			for (Answer a : Question.YOUTH.answers()) {
				if (a.cls() == cls) {
					scene = Question.YOUTH.key() + "." + a.key();
					icon = new ItemStack(a.icon());
					break;
				}
			}
		}
		StoryFigure.draw(graphics, player, kind, look, scene, icon, x0, y0, x1, y1);
	}

	private static String keyOf(@Nullable Answer a) {
		return a == null ? "raised" : a.key().toLowerCase(Locale.ROOT);
	}
}
