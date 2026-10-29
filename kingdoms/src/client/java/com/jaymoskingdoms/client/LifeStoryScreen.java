package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.LifeStory;
import com.jaymoskingdoms.McaCompat;
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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
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
 * (0.7.356) Andy: "this screen should scroll from left to right ... a larger image of the icon beside a larger image of
 * your character in their scenario, built out with all their customizations. I also want this screen to come after the
 * Creator". The road (where you start) is a carousel: one start at a time, its picture large beside you acting it out
 * on a backdrop of that place, stepped through with the arrows, the wheel, the keys or the strip of starts under it.
 * With MCA's creator still to come, the story stops at its last page short of the road; the creator follows, and then
 * this screen opens again on the road alone, with you drawn as the creator made you.
 */
final class LifeStoryScreen extends Screen {
	static final int MAX_W = 440, MAX_H = 300, TELL_W = 168, LIST_ROW = 19, MIN_ROW = 12, HEADER = 24, FOOTER = 24;
	/** The story's accent: an old parchment brown, apart from the heritage teal and the class gold. */
	static final int ACCENT = 0xFFC89A5A;
	/** After "Live this life", how long the story may take to come back before the screen is put up again (ms). */
	static final long LIVE_GRACE_MS = 3000L;
	/** (0.7.304) The first page's rows, by MCA's names (their lang keys): 0 male, 1 female. */
	static final String[] GENDERS = {LifeStory.MALE, LifeStory.FEMALE};
	/** Pixels the story page moves for one notch of the wheel. */
	static final int SCROLL_STEP = 12;
	/** (0.7.269) The pause between two lines the narrator reads, and how long a line is given to start (ms). */
	static final long READ_GAP_MS = 350L, READ_START_MS = 500L;
	/**
	 * (0.7.360) Andy: "'as a child you were....' should be all it says. If players spend more than 10s on this screen then
	 * it plays, 'what kind of child were you?'". A question is read by its title alone; its line underneath only once
	 * the page has stood this long (ms) unanswered. The road's page is only ever its title.
	 */
	static final long SUB_DELAY_MS = 10_000L;
	/** (0.7.275) The skip-the-story ask: its box's size. */
	static final int ASK_W = 240, ASK_H = 96;
	/** (0.7.282) The figure of you at the foot of the left side ({@link StoryFigure}): its most height, and the least room it's drawn in. */
	static final int FIGURE_H = 96, FIGURE_MIN_H = 48;
	/**
	 * (0.7.356) The road's carousel: the arrows' width, the strip of starts under the stage (its height and a cell's
	 * width), the big picture's well and scale, the text column's share of the stage, and how long a turn slides (ms)
	 * and how far (pixels).
	 */
	static final int ARROW_W = 16, STRIP_H = 24, CELL_W = 22, ICON_WELL = 64, ICON_SCALE = 3;
	static final float TEXT_SHARE = 0.44F;
	static final long SLIDE_MS = 220L;
	static final int SLIDE_PX = 36;

	/** One page: a question, the realm to name for a question's answer, or the story read back. */
	private record Step(@Nullable Question question, boolean realm, boolean gender) {
		static final Step SUMMARY = new Step(null, false, false);
		/** (0.7.304) Andy: "The first screen should be choosing your gender". */
		static final Step GENDER = new Step(null, false, true);

		boolean summary() {
			return this.question == null && !this.gender;
		}
	}

	/**
	 * The answers so far: an index per question (-1: none yet), the realms named, the look and the option, the heritage
	 * and class picked over the story's (ordinals, -1: the story's), and the page.
	 */
	private static final class Draft {
		final int[] answers = new int[Question.values().length];
		int raisedRealm = -1, roadRealm = -1, look = -1, option = -1, heritage = -1, cls = -1, step;
		/** (0.7.304) 0 male, 1 female, -1 not picked yet. */
		int gender = -1;

		Draft() {
			Arrays.fill(this.answers, -1);
		}
	}

	private final boolean forced;
	private @Nullable Button back, next, look, heritageButton, classButton, later, skip, skipYes, skipNo, prev, after;
	/** (0.7.356) The carousel's last turn: when, and which way (-1 back, 1 on). */
	private long slidAt;
	private int slideDir;
	private int scroll, contentH, viewH;
	/** (0.7.275) Whether "On second thought..." is asking if they'd skip the story. */
	private boolean asking;

	/** The story owed (the screen comes back whenever it's shut), the realms there are to name, and the answers so far. */
	private static boolean owed;
	/** (0.7.356) MCA's creator comes after this screen: the story stops short of the road, told after the creator. */
	private static boolean creatorAhead;
	private static long quietUntil;
	private static List<LifeStory.Realm> realms = List.of();
	private static Draft draft = new Draft();
	/** (0.7.251) The page the narrator last began, so it reads each page once; the line being read and those still to read. */
	private static @Nullable String narrated;
	/** (0.7.352) The page under the skip-the-story ask, so shutting the ask doesn't read that page again. */
	private static @Nullable String underAsk;
	private static @Nullable SimpleSoundInstance reading;
	private static final ArrayDeque<String> toRead = new ArrayDeque<>();
	/** (0.7.269) When the line being read began, and when the next may (ms). */
	private static long readFrom, readNext;
	/** (0.7.360) When the page being read first showed (ms), and whether its line underneath has been read. */
	private static long pageSince;
	private static boolean subRead;

	LifeStoryScreen(boolean forced) {
		super(Component.translatable("gui.jaymoskingdoms.story.title"));
		this.forced = forced;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LifeStory.Open.TYPE, (payload, context) -> {
			owed |= payload.forced();
			realms = payload.realms();
			creatorAhead = payload.creatorAhead();
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
			creatorAhead = false;
			draft = new Draft();
			narrated = null;
			toRead.clear();
		});
	}

	/** Whether the story is still owed (the screen comes back whenever it's shut). */
	static boolean owed() {
		return owed;
	}

	/** (0.7.304) The gender picked on the first page: 0 male, 1 female, -1 none yet (the story figure follows it). */
	static int gender() {
		return draft.gender;
	}

	/**
	 * Whether the story is told: it came back from the server. (0.7.356) Told up to the road with MCA's creator to come,
	 * this screen is done till the creator is; after it, only once the road is told too.
	 */
	private static boolean done() {
		Minecraft minecraft = Minecraft.getInstance();
		List<String> story = minecraft.player == null ? null : LifeStory.of(minecraft.player);
		return story != null && (creatorAhead || !LifeStory.roadOwed(story));
	}

	/** (0.7.356) Whether only the road is left to tell (the rest was told before MCA's creator). */
	private static boolean roadOnly() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && LifeStory.roadOwed(LifeStory.of(minecraft.player));
	}

	@Override
	public void removed() {
		// (0.7.286) the stand-in figure is no longer MCA's editor figure
		StoryFigure.closed();
		// (0.7.360) Andy: "if the narrator is still speaking when you move on, it will double up the voice. make it cut off
		// anytime the player advances." Leaving the story cuts the reading off too
		hush();
		super.removed();
	}

	/** (0.7.360) The narrator stops at once, and forgets what it was about to read. */
	static void hush() {
		if (reading != null) {
			Minecraft.getInstance().getSoundManager().stop(reading);
			reading = null;
		}
		toRead.clear();
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
		// (0.7.356) after MCA's creator: the road, and the realm it names
		if (roadOnly()) {
			out.add(new Step(Question.ROAD, false, false));
			Answer a = picked(Question.ROAD);
			if (a != null && a.realm()) {
				out.add(new Step(Question.ROAD, true, false));
			}
			return out;
		}
		// (0.7.304) who you are comes first: MCA's body is a man's or a woman's
		if (McaCompat.LOADED) {
			out.add(Step.GENDER);
		}
		for (Question q : Question.values()) {
			// (0.7.356) the road waits for MCA's creator when it's still to come
			if (!q.asked() || q == Question.ROAD && creatorAhead) {
				continue;
			}
			out.add(new Step(q, false, false));
			Answer a = picked(q);
			if (a != null && a.realm()) {
				out.add(new Step(q, true, false));
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
		// (0.7.356) on the road alone, the rest is the story told before MCA's creator
		Minecraft minecraft = Minecraft.getInstance();
		if (i < 0 && q != Question.ROAD && minecraft.player != null && roadOnly()) {
			return LifeStory.answer(minecraft.player, q);
		}
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

	/**
	 * Whether an answer can be picked: one that names a realm only while there's a realm to name; one offered here;
	 * (0.7.349) one that's only for a heritage (the Verdant World's start) only for those born to it.
	 */
	private static boolean open(Answer a) {
		return a.offered() && (!a.realm() || !realms.isEmpty()) && (a.onlyFor() == null || bornAs() == a.onlyFor());
	}

	/** (0.7.349) The road's hold on the heritage: the start that makes you one (the afterlife's), or is only for one (the Verdant World's). */
	private static boolean roadFixesHeritage() {
		Answer road = picked(Question.ROAD);
		return road != null && (road.makes() != null || road.onlyFor() != null);
	}

	/** Whether the page is answered, so Next can go on. */
	private static boolean answered(Step s) {
		if (s.gender) {
			return draft.gender >= 0;
		}
		if (s.question == null) {
			// (0.7.349) a road that's no longer open (the birthplace changed under it) has to be picked again
			Answer road = picked(Question.ROAD);
			if (road != null && !open(road)) {
				return false;
			}
			Classes.PlayerClass cls = youthClass();
			return cls == null || cls.options.isEmpty() || draft.option >= 0;
		}
		if (s.realm) {
			return realmOf(s.question) >= 0;
		}
		// (0.7.356) the carousel shows every road, open or not: only an open one goes on
		Answer a = picked(s.question);
		return a != null && (s.question != Question.ROAD || open(a));
	}

	/** The heritage the story makes (or the one picked over it), and its look (the childhood's leaning until one is picked). */
	private static Heritage.@Nullable Kind heritage() {
		Answer road = picked(Question.ROAD);
		if (road != null && road.makes() != null) {
			return road.makes();
		}
		if (draft.heritage >= 0 && draft.heritage < Heritage.Kind.values().length) {
			return Heritage.Kind.values()[draft.heritage];
		}
		return storyHeritage();
	}

	/** (0.7.356) Your heritage as the story told before MCA's creator made it (null before then). */
	private static Heritage.@Nullable Kind told() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && roadOnly() ? Heritage.of(minecraft.player) : null;
	}

	private static Heritage.@Nullable Kind storyHeritage() {
		Answer road = picked(Question.ROAD);
		return road != null && road.makes() != null ? road.makes() : told() != null ? told() : bornAs();
	}

	/** (0.7.349) The heritage the birthplace gives, whatever the road makes of it. */
	private static Heritage.@Nullable Kind bornAs() {
		// (0.7.356) after MCA's creator, the heritage already yours (the server checks the Verdant World's road against it)
		if (told() != null) {
			return told();
		}
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
		// (0.7.349) the afterlife's road makes you Divine-born, and the Verdant World's is the Verdant-born's alone
		if (story == null || now == null || minecraft.player == null || roadFixesHeritage()) {
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
		return s.gender ? GENDERS.length : s.question == null ? 0 : s.realm ? realms.size() : s.question.answers().size();
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
		// (0.7.356) the road's carousel: a turn back and a turn on, either side of the stage
		int arrowY = top() + HEADER + (panelH() - HEADER - FOOTER - STRIP_H - 4) / 2 - 10;
		this.prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left() + 4, arrowY, ARROW_W, 20).build());
		this.after = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left() + panelW() - 4 - ARROW_W, arrowY, ARROW_W, 20).build());
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
			int pick = !q.asked() || open.isEmpty() || q == Question.ROAD && creatorAhead ? -1 : open.get(random.nextInt(open.size()));
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
	 * "the realm". (0.7.352) While {@code asking}, it reads the skip-the-story ask instead; saying no goes back to the page
	 * quietly.
	 */
	private static void narrate(Step s, boolean asking) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		String page = s.summary() ? "story" + Arrays.toString(draft.answers) : String.join(".", lines(s));
		if (asking) {
			if (!"skip".equals(narrated)) {
				underAsk = narrated;
			}
			page = "skip";
		} else if ("skip".equals(narrated) && page.equals(underAsk)) {
			if (reading != null) {
				minecraft.getSoundManager().stop(reading);
				reading = null;
			}
			toRead.clear();
			narrated = page;
		}
		if (!page.equals(narrated)) {
			narrated = page;
			pageSince = now;
			subRead = false;
			// (0.7.360) whatever was still being read stops: this page's lines only, never two at once
			hush();
			StoryHoldScreen.hush(minecraft);
			toRead.addAll(asking ? List.of("gui.jaymoskingdoms.story.skip.ask", "gui.jaymoskingdoms.story.skip.desc") : lines(s));
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
		// (0.7.360) a question left unanswered a while: its line underneath, once
		if (!asking && !subRead && toRead.isEmpty() && !s.gender && !s.realm && s.question != null && s.question != Question.ROAD
			&& picked(s.question) == null && now - pageSince >= SUB_DELAY_MS) {
			subRead = true;
			toRead.add("story.jaymoskingdoms." + s.question.key() + ".sub");
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
		if (s.gender) {
			// (0.7.360) Andy: "should just say, 'Who are you' no other voice line, on this screen"
			return List.of("story.jaymoskingdoms.gender.title");
		}
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
		hush();
		// (0.7.356) after MCA's creator: the road's last page sets out
		if (roadOnly()) {
			if (draft.step < steps().size() - 1) {
				draft.step++;
				this.scroll = 0;
				return;
			}
			Answer road = picked(Question.ROAD);
			ClientPlayNetworking.send(new LifeStory.Road(draft.answers[Question.ROAD.ordinal()], road != null && road.realm() ? draft.roadRealm : -1, lookIndex()));
			quietUntil = System.currentTimeMillis() + LIVE_GRACE_MS;
			onClose();
			return;
		}
		if (!s.summary()) {
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
			road != null && road.realm() ? draft.roadRealm : -1, lookIndex(), draft.option, draft.heritage, draft.cls, draft.gender));
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
			} else if (s.summary() && chipAt(mx, my) >= 0) {
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
		if (carousel(s)) {
			int cell = cellAt(mx, my);
			if (cell >= 0) {
				int cur = draft.answers[Question.ROAD.ordinal()];
				if (cell != cur) {
					pickRoad(cell, cell < cur ? -1 : 1);
				}
				return true;
			}
		} else if (s.gender) {
			int row = rowAt(s, mx, my);
			if (row >= 0) {
				draft.gender = row;
				click();
				return true;
			}
		} else if (s.question != null) {
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
						// (0.7.349) a road that fixes the heritage drops one picked over the story's; a new birthplace drops a road it shuts
						if (s.question == Question.ROAD && (a.makes() != null || a.onlyFor() != null)) {
							draft.heritage = -1;
							draft.look = -1;
						}
						Answer road = picked(Question.ROAD);
						if (s.question == Question.BORN && road != null && !open(road)) {
							draft.answers[Question.ROAD.ordinal()] = -1;
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
		// (0.7.356) the wheel turns the carousel
		if (carousel(step()) && scrollY != 0) {
			turn(scrollY > 0 ? -1 : 1);
			return true;
		}
		if (this.contentH > this.viewH && mouseX >= paneX()) {
			this.scroll = Math.clamp(this.scroll - (int)Math.round(scrollY * SCROLL_STEP), 0, this.contentH - this.viewH);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// (0.7.356) left and right (or A and D) turn the carousel
		if (!this.asking && carousel(step())) {
			int key = event.key();
			if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_A) {
				turn(-1);
				return true;
			}
			if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_D) {
				turn(1);
				return true;
			}
		}
		return super.keyPressed(event);
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
		narrate(s, this.asking);
		int x = left(), y = top(), w = panelW(), h = panelH();
		Ui.panel(graphics, x, y, w, h, ACCENT);
		boolean wheel = carousel(s);
		if (wheel) {
			carouselPage(graphics, mouseX, mouseY);
		} else {
			page(graphics, s, mouseX, mouseY);
		}

		if (this.back != null) {
			this.back.setX(wheel ? x + 6 : paneX() + 6);
		}
		if (this.skip != null) {
			this.skip.setX(wheel ? x + 70 : x + 6);
		}
		for (Button b : new Button[] {this.prev, this.after}) {
			if (b != null) {
				b.visible = wheel && !this.asking;
			}
		}

		if (this.next != null) {
			// (0.7.356) with MCA's creator to come the story's page leads on to it; after it, the road's last page sets out
			boolean last = draft.step >= steps().size() - 1;
			String label = roadOnly() ? last ? "gui.jaymoskingdoms.story.live" : "gui.jaymoskingdoms.story.next"
				: s.summary() ? creatorAhead ? "gui.jaymoskingdoms.story.creator" : "gui.jaymoskingdoms.story.live" : "gui.jaymoskingdoms.story.next";
			this.next.setMessage(Component.translatable(label));
			this.next.active = answered(s);
		}
		if (this.back != null) {
			this.back.active = draft.step > 0;
		}
		if (this.look != null) {
			Heritage.Kind kind = heritage();
			this.look.visible = s.summary() && kind != null;
			if (kind != null) {
				this.look.setMessage(Component.translatable("gui.jaymoskingdoms.heritage.variant", HeritageVariants.title(kind, lookIndex()),
					lookIndex() + 1, HeritageVariants.of(kind).size() + 1));
			}
		}
		Heritage.Kind kind = heritage();
		Classes.PlayerClass cls = youthClass();
		if (this.heritageButton != null) {
			this.heritageButton.visible = s.summary() && kind != null;
			if (kind != null) {
				this.heritageButton.setMessage(Component.translatable("gui.jaymoskingdoms.story.heritage", Component.translatable("heritage.jaymoskingdoms." + kind.key()))
					.withColor(draft.heritage >= 0 ? ACCENT : Ui.TEXT));
			}
		}
		if (this.classButton != null) {
			this.classButton.visible = s.summary() && cls != null;
			if (cls != null) {
				this.classButton.setMessage(Component.translatable("gui.jaymoskingdoms.story.class", Component.translatable("class.jaymoskingdoms." + cls.key()))
					.withColor(draft.cls >= 0 ? ACCENT : Ui.TEXT));
			}
		}
		if (this.skip != null) {
			this.skip.visible = s.question != null && !roadOnly();
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

	/** A page as it always was: the answers (or the story) down the right, the one pointed at told on the left. */
	private void page(GuiGraphicsExtractor graphics, Step s, int mouseX, int mouseY) {
		int x = left(), y = top(), h = panelH();
		graphics.fill(x + TELL_W - 1, y + 4, x + TELL_W, y + h - 4, Ui.ROW);
		// the header over the right: the question (or the realm's, or the story's); (0.7.269) the line under it is only heard
		int px = paneX(), pw = paneW();
		Ui.line(graphics, this.font, heading(s), px + 8, y + 7, pw - 16, Ui.TEXT);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);

		int viewTop = y + HEADER, viewBottom = y + h - FOOTER;
		this.viewH = viewBottom - viewTop;
		graphics.enableScissor(px + 1, viewTop, px + pw - 1, viewBottom);
		if (!s.summary()) {
			rows(graphics, s, mouseX, mouseY);
		} else {
			summary(graphics, mouseX, mouseY);
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, px + pw - 4, viewTop, this.viewH, this.viewH, this.contentH, this.scroll);

		tell(graphics, s, mouseX, mouseY);
	}

	// ---- the road's carousel (0.7.356) -------------------------------------------------------------------------------

	/**
	 * (0.7.356) Each start's backdrop behind the figure: the sky's top and foot, and the ground (ARGB), by road key. A
	 * start without one stands on a dusk road.
	 */
	static final java.util.Map<String, int[]> BACKDROPS = java.util.Map.ofEntries(
		java.util.Map.entry("city", new int[] {0xFF3A2A4A, 0xFFC08050, 0xFF6A5A4A}),
		java.util.Map.entry("banished", new int[] {0xFF2A2A34, 0xFF6A5A5A, 0xFF4A3A2A}),
		java.util.Map.entry("cave", new int[] {0xFF08080C, 0xFF20242C, 0xFF3A3A40}),
		java.util.Map.entry("shipwreck", new int[] {0xFF4A6A8A, 0xFF8AB0C8, 0xFFC8B880}),
		java.util.Map.entry("sworn", new int[] {0xFF3A4A6A, 0xFF8A9AB0, 0xFF6A6A6A}),
		java.util.Map.entry("village", new int[] {0xFF5A8AC8, 0xFFB0D0E8, 0xFF5A8A3A}),
		java.util.Map.entry("monolith", new int[] {0xFF2A1A4A, 0xFF7A5AA0, 0xFF5A5A62}),
		java.util.Map.entry("wild", new int[] {0xFF4A7AA8, 0xFFA8C8D8, 0xFF3A6A2A}),
		java.util.Map.entry("skyfall", new int[] {0xFF1A2A6A, 0xFF8AA8E8, 0xFF7A5A3A}),
		java.util.Map.entry("timelost", new int[] {0xFF0A1A2A, 0xFF2A7A8A, 0xFF3A4A52}),
		java.util.Map.entry("burned", new int[] {0xFF2A0A0A, 0xFFC04A1A, 0xFF2A2420}),
		java.util.Map.entry("snowed", new int[] {0xFF8A9AB0, 0xFFE0E8F0, 0xFFF4F8FC}),
		java.util.Map.entry("captive", new int[] {0xFF2A1A12, 0xFF8A5A3A, 0xFF4A3A2A}),
		java.util.Map.entry("whalers", new int[] {0xFF2A3A4A, 0xFF7A8A9A, 0xFF1A3A5A}),
		java.util.Map.entry("unarmed", new int[] {0xFF2A2A2A, 0xFF6A6A6A, 0xFF4A4A4A}),
		java.util.Map.entry("moon", new int[] {0xFF000004, 0xFF10101A, 0xFFB8B8C0}),
		java.util.Map.entry("verdant", new int[] {0xFF2A6A2A, 0xFF9AD07A, 0xFF4A8A2A}),
		java.util.Map.entry("rustfall", new int[] {0xFF3A1A0A, 0xFFA05A2A, 0xFF5A3A2A}),
		java.util.Map.entry("maw", new int[] {0xFF000000, 0xFF2A0A3A, 0xFF1A1A1A}),
		java.util.Map.entry("demon", new int[] {0xFF1A0000, 0xFF8A1A1A, 0xFF3A1010}),
		java.util.Map.entry("afterlife", new int[] {0xFFB0C8E8, 0xFFF8F0D0, 0xFFF0F0F0}),
		java.util.Map.entry("fate", new int[] {0xFF0A0A2A, 0xFF4A2A7A, 0xFF2A2A3A}));
	static final int[] DUSK = {0xFF2A2A4A, 0xFF8A6A6A, 0xFF4A4A3A};

	/** (0.7.356) Whether page {@code s} is the road's carousel (not the realm it names). */
	private static boolean carousel(Step s) {
		return s.question == Question.ROAD && !s.realm;
	}

	/** (0.7.356) Turns the carousel one start back ({@code -1}) or on ({@code 1}), wrapping. */
	private void turn(int dir) {
		int n = Question.ROAD.answers().size(), cur = draft.answers[Question.ROAD.ordinal()];
		pickRoad(cur < 0 ? (dir > 0 ? 0 : n - 1) : Math.floorMod(cur + dir, n), dir);
	}

	/** (0.7.356) Shows (and picks, if it's open) road {@code i}, sliding in from {@code dir}'s side. */
	private void pickRoad(int i, int dir) {
		Answer a = Question.ROAD.answers().get(i);
		draft.answers[Question.ROAD.ordinal()] = i;
		// (0.7.349) a road that fixes the heritage drops one picked over the story's
		if (a.makes() != null || a.onlyFor() != null) {
			draft.heritage = -1;
			draft.look = -1;
		}
		this.slidAt = System.currentTimeMillis();
		this.slideDir = dir;
		click();
	}

	/** (0.7.356) The strip's first cell's left edge: centred when it fits, else scrolled to keep the shown start in the middle. */
	private int stripStart() {
		int n = Question.ROAD.answers().size(), view = panelW() - 16, total = n * CELL_W;
		if (total <= view) {
			return left() + 8 + (view - total) / 2;
		}
		int cur = Math.max(0, draft.answers[Question.ROAD.ordinal()]);
		return left() + 8 - Math.clamp(cur * CELL_W + CELL_W / 2 - view / 2, 0, total - view);
	}

	private int stripY() {
		return top() + panelH() - FOOTER - STRIP_H;
	}

	/** (0.7.356) The strip's cell under the mouse, or -1. */
	private int cellAt(int mx, int my) {
		int y = stripY();
		if (my < y || my >= y + STRIP_H - 4 || mx < left() + 8 || mx >= left() + panelW() - 8) {
			return -1;
		}
		int i = Math.floorDiv(mx - stripStart(), CELL_W);
		return i >= 0 && i < Question.ROAD.answers().size() ? i : -1;
	}

	/**
	 * (0.7.356) The road, one start at a time: its picture large on the left over its name, its line of your past and
	 * what it gives; you on the right acting it out on a backdrop of the place; the strip of every start under them.
	 */
	private void carouselPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Answer> roads = Question.ROAD.answers();
		int ri = Question.ROAD.ordinal();
		// (the first open start is shown when none is picked yet)
		if (draft.answers[ri] < 0 || draft.answers[ri] >= roads.size()) {
			for (int i = 0; i < roads.size(); i++) {
				if (open(roads.get(i))) {
					draft.answers[ri] = i;
					break;
				}
			}
		}
		int cur = Math.max(0, draft.answers[ri]);
		Answer a = roads.get(cur);
		int x = left(), y = top(), w = panelW();
		Ui.line(graphics, this.font, Component.translatable("story.jaymoskingdoms.road.title"), x + 8, y + 7, w - 70, Ui.TEXT);
		Ui.lineRight(graphics, this.font, Component.literal((cur + 1) + " / " + roads.size()), x + w - 8, y + 7, 60, Ui.DIM);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);

		int sx0 = x + ARROW_W + 8, sx1 = x + w - ARROW_W - 8, sy0 = y + HEADER, sy1 = stripY() - 4;
		int textW = (int)((sx1 - sx0) * TEXT_SHARE), split = sx0 + textW;
		float t = Math.min(1F, (System.currentTimeMillis() - this.slidAt) / (float)SLIDE_MS);
		int off = Math.round(this.slideDir * (1F - (1F - (1F - t) * (1F - t) * (1F - t))) * SLIDE_PX);

		// the place: its sky and ground, framed
		int bx0 = split + 6, bx1 = sx1, ground = sy1 - (sy1 - sy0) / 5;
		int[] colors = BACKDROPS.getOrDefault(a.key(), DUSK);
		Ui.well(graphics, bx0 - 1, sy0 - 1, bx1 - bx0 + 2, sy1 - sy0 + 2);
		graphics.fillGradient(bx0, sy0, bx1, ground, colors[0], colors[1]);
		graphics.fill(bx0, ground, bx1, sy1, colors[2]);
		graphics.fill(bx0, ground, bx1, ground + 1, 0x40000000);
		graphics.fillGradient(bx0, ground + 1, bx1, sy1, 0x00000000, 0x60000000);

		graphics.enableScissor(sx0, sy0, sx1, sy1);
		// you, acting the start out on the ground of the place
		figure(graphics, new Step(Question.ROAD, false, false), -1, -1, bx0 + off, sy0 + 4, bx1 + off, ground + (sy1 - ground) / 2);

		// its picture, large
		int ix = sx0 + off, wx = ix + (textW - ICON_WELL) / 2, wy = sy0 + 4;
		Ui.well(graphics, wx, wy, ICON_WELL, ICON_WELL);
		int size = 16 * ICON_SCALE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(wx + (ICON_WELL - size) / 2F, wy + (ICON_WELL - size) / 2F);
		graphics.pose().scale(ICON_SCALE, ICON_SCALE);
		graphics.item(new ItemStack(a.icon()), 0, 0);
		graphics.pose().popMatrix();
		boolean open = open(a);
		if (!open) {
			graphics.fill(wx + 1, wy + 1, wx + ICON_WELL - 1, wy + ICON_WELL - 1, 0x90000000);
		}

		// its name, its line of your past, and what it gives
		Component name = Component.translatable("story.jaymoskingdoms.road." + a.key());
		int ny = wy + ICON_WELL + 6;
		if (this.font.width(name) <= textW - 8) {
			graphics.centeredText(this.font, name, ix + textW / 2, ny, open ? ACCENT : Ui.HINT);
		} else {
			Ui.line(graphics, this.font, name, ix + 4, ny, textW - 8, open ? ACCENT : Ui.HINT);
		}
		List<Component> lines = new ArrayList<>();
		// (0.7.360) Andy: "For every option it self, shorten the white text to a sentence or two at most": each option shows
		// its short line; the whole tale is kept for the story on the last page
		lines.add(Component.translatable("story.jaymoskingdoms.road." + a.key() + ".short", realmName(Question.ROAD)));
		if (!open) {
			lines.add(reason(a).withColor(Ui.BAD));
		}
		graphics.disableScissor();
		block(graphics, lines, gives(Question.ROAD, a, false), sx0 - 9 + off, ny + 14, textW + 10, sy1);

		// the strip: every start, the shown one marked, those not open dimmed
		int stripY = stripY(), x0 = stripStart();
		int hover = cellAt(mouseX, mouseY);
		graphics.enableScissor(x + 8, stripY, x + w - 8, stripY + STRIP_H - 4);
		for (int i = 0; i < roads.size(); i++) {
			int cx = x0 + i * CELL_W;
			boolean shown = i == cur, can = open(roads.get(i));
			graphics.fill(cx + 1, stripY, cx + CELL_W - 1, stripY + STRIP_H - 4, shown ? Ui.ROW_PICKED : i == hover ? Ui.ROW_HOVER : Ui.ROW_ALT);
			if (shown) {
				graphics.fill(cx + 1, stripY + STRIP_H - 6, cx + CELL_W - 1, stripY + STRIP_H - 4, ACCENT);
			}
			graphics.item(new ItemStack(roads.get(i).icon()), cx + (CELL_W - 16) / 2, stripY + 2);
			if (!can) {
				graphics.fill(cx + 1, stripY, cx + CELL_W - 1, stripY + STRIP_H - 4, 0xA0101018);
			}
		}
		graphics.disableScissor();
	}

	/** (0.7.356) Why an answer can't be picked here. */
	private static net.minecraft.network.chat.MutableComponent reason(Answer a) {
		return !a.offered() ? Component.translatable("gui.jaymoskingdoms.story.needs_powers")
			: a.onlyFor() != null && bornAs() != a.onlyFor()
				? Component.translatable("gui.jaymoskingdoms.story.only_for", Component.translatable("heritage.jaymoskingdoms." + a.onlyFor().key()))
				: Component.translatable("gui.jaymoskingdoms.story.no_realms");
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
		if (s.gender) {
			return Component.translatable("story.jaymoskingdoms.gender.title");
		}
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
			if (s.gender) {
				picked = draft.gender == i;
				label = Component.translatable("story.jaymoskingdoms.gender." + GENDERS[i]);
			} else if (s.realm) {
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
		if (this.chipsTop < 0 || cls == null || cls.options.isEmpty() || !step().summary()) {
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
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && roadOnly()) {
			return Classes.classOf(minecraft.player);
		}
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
		if (a.start() != null && a.makes() != null && !story) {
			out.add(Component.translatable("gui.jaymoskingdoms.story.gives.heritage", Component.translatable("heritage.jaymoskingdoms." + a.makes().key())));
		}
		if (a.start() == LifeStory.Start.RUSTFALL) {
			out.add(Component.translatable("gui.jaymoskingdoms.story.gives.stat", Component.translatable("stat.jaymoskingdoms." + com.jaymoskingdoms.Stats.Stat.MIGHT.key())));
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
		Component name;
		ItemStack icon = ItemStack.EMPTY;
		List<Component> lines = new ArrayList<>();
		List<Component> good = new ArrayList<>();
		if (s.gender) {
			int row = rowAt(s, mouseX, mouseY);
			int g = row >= 0 ? row : draft.gender;
			name = Component.translatable(g < 0 ? "story.jaymoskingdoms.gender.title" : "story.jaymoskingdoms.gender." + GENDERS[g]);
			// (0.7.352) its grey line is only heard now, like the questions'
		} else if (s.question == null) {
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
				// (0.7.360) the option's short line; its whole tale is told on the last page
				lines.add(Component.translatable("story.jaymoskingdoms." + s.question.key() + "." + a.key() + ".short", realmName(s.question)));
				good.addAll(gives(s.question, a, false));
				if (!open(a)) {
					lines.add(reason(a).withColor(Ui.BAD));
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
		int bottom = y + panelH() - FOOTER - 2 - (s.summary() ? 40 : 0);
		// (0.7.282) the figure under the text, as much of it as there's room for
		int figureH = Math.min(FIGURE_H, bottom - ty - 3 * Ui.LINE);
		if (figureH >= FIGURE_MIN_H) {
			bottom -= figureH;
			figure(graphics, s, mouseX, mouseY, x + 8, bottom + 2, x + w - 8, bottom + figureH);
		}
		block(graphics, lines, good, x, ty, w, bottom);
	}

	/**
	 * The lines of a tale (each in its own colour, white if none) and, under a rule, what it gives (green), in the
	 * column {@code x}..{@code x + w} from {@code ty} down to {@code bottom}.
	 */
	private void block(GuiGraphicsExtractor graphics, List<Component> lines, List<Component> good, int x, int ty, int w, int bottom) {
		int textW = w - 18;
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
		if (s.gender) {
			// (0.7.304) the one pointed at, before the story gives you any blood
			int row = rowAt(s, mouseX, mouseY);
			StoryFigure.draw(graphics, player, null, 0, "", ItemStack.EMPTY, x0, y0, x1, y1, row >= 0 ? row : draft.gender);
			return;
		}
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
		// (0.7.356) after MCA's creator it's you, as the creator made you (lent only the heritage a road makes you)
		if (roadOnly()) {
			Answer road = picked(Question.ROAD);
			StoryFigure.drawYou(graphics, player, road != null ? road.makes() : null, look, scene, icon, x0, y0, x1, y1);
			return;
		}
		StoryFigure.draw(graphics, player, kind, look, scene, icon, x0, y0, x1, y1, draft.gender);
	}

	private static String keyOf(@Nullable Answer a) {
		return a == null ? "raised" : a.key().toLowerCase(Locale.ROOT);
	}
}
