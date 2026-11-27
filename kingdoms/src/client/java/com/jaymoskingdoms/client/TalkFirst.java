package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Introductions;
import com.jaymoskingdoms.Questioning;
import com.jaymoskingdoms.Replies;
import com.jaymoskingdoms.ZFighterFolk;
import com.jaymoskingdoms.client.mixin.mca.McaButtonExAccessor;
import com.jaymoskingdoms.client.mixin.mca.McaInteractScreenAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.entity.CommonSpeechManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.InteractionDialogueInitMessage;
import net.conczin.mca.network.c2s.InteractionDialogueMessage;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-25): "I want all interactions accessible through talk so removal of both the talk and interaction button
 * and leaving the wheel, and the buttons that remain are gift and trade, even family tree should be accessed through
 * talk". MCA's villager screen opens straight into the talk: the villager speaks and the talk wheel
 * ({@code McaDialogueBoxMixin}) comes up with their answers, and the button column holds only Gift and Trade.
 *
 * <p>Everything MCA's column offered is a spoke on the wheel instead. Each MCA page (main, Interact, Work, Professions,
 * Locations, Ask the Way) is still laid out by MCA and by our page mixins as before, so every rule about who sees which
 * button (strangers, tailors, family, farmhands, a villager's own offers, Z Fighter characters) still decides; this only
 * hides the laid-out buttons and shows the ones that are there as spokes, pressed exactly as the button was. On the
 * talk's first ring: Family Tree, and "Ask of them..." (MCA's Interact page with the main page's Inventory, Work, Quests
 * and the tailor's work); a page's spokes end with Back, which returns to the ring it came from.
 *
 * <p>Kept from before: one you haven't been introduced to gets only Introduce yourself on the wheel (with Gift and Trade);
 * the talk starts once they've told you who they are. The Judge, the Keeper and the masters get only their own talk, no
 * Gift. Held down for questioning, only the questions.
 *
 * <p>Andy (2026-11-27): "there should always be an option to address what they say here. either the first on the tree
 * or a button". First on the talk's first ring: a reply to the line they opened with ({@link Replies}).
 */
public final class TalkFirst {
	/** A spoke that isn't one of MCA's answers: this prefix, then the MCA button it presses (or one of ours below). */
	public static final String LOCAL = "jk:";
	/** Our own spokes: back to the last ring, and the stranger's introduction. */
	static final String BACK = LOCAL + "back", INTRODUCE = LOCAL + "introduce";
	/** MCA's buttons that are never spokes: the column's two, the talk itself, MCA's back arrow (Back stands in). */
	static final String TALK = "gui.button.talk", INTERACT = "gui.button.interact", TRADE = "gui.button.trade", GIFT = "gui.button.gift",
		BACK_ARROW = "gui.button.backarrow", FAMILY = "gui.button.familyTree";
	/** The main page's buttons that go on "Ask of them..." with the Interact page's, not on the first ring. */
	static final Set<String> TO_ASK = Set.of("gui.button.inventory", "gui.button.work", "gui.button.quests", "gui.button.tailor",
		"gui.button.costume", "gui.button.costume_table");
	static final String MAIN = "main", ASK = "interact";

	/** The screen this is for (one villager screen is open at a time). */
	private static @Nullable InteractScreen screen;
	/** The laid-out page's buttons that are spokes now (hidden from the column), and the main page's that go on Ask. */
	private static final List<AbstractWidget> page = new ArrayList<>();
	private static final Map<String, Spoke> askExtras = new LinkedHashMap<>();
	/** The rings walked through, for Back. */
	private static final Deque<String> trail = new ArrayDeque<>();
	/** The talk has been asked for (it starts once, when the screen first lays out for one you know). */
	private static boolean talkAsked;
	/** What the villager last said in the talk box: the reply spoke answers it ({@link Replies}). */
	private static @Nullable Component said;

	/** One of the main page's buttons kept for the Ask ring: MCA's button and its label. */
	private record Spoke(MCAButton button, Component label) {
	}

	private TalkFirst() {
	}

	/** Whether {@code answer} is one of ours rather than one of MCA's dialogue answers. */
	public static boolean local(@Nullable String answer) {
		return answer != null && answer.startsWith(LOCAL);
	}

	private static void forScreen(InteractScreen s) {
		if (screen != s) {
			screen = s;
			page.clear();
			askExtras.clear();
			trail.clear();
			talkAsked = false;
			said = null;
		}
	}

	/** The Judge, the Keeper, a master: one of Powers' Z Fighter characters, with their own trimmed talk. */
	static boolean special(Entity v) {
		return v.getAttached(ZFighterFolk.PART) != null;
	}

	/** One you haven't been introduced to (and not one of those, who are never shown the stranger's page). */
	static boolean stranger(Entity v) {
		return !Introductions.knownHere(v) && !special(v);
	}

	/**
	 * MCA (and our page mixins) laid out {@code layout}: its buttons become spokes and leave the column, except Trade on
	 * the main page; the main page gets Gift beside it ({@code add}, unless it's one of the special characters); and the
	 * talk starts the first time the main page is laid out for one you know.
	 */
	public static void laidOut(InteractScreen s, String layout, List<? extends GuiEventListener> children, java.util.function.Consumer<AbstractWidget> addGift) {
		forScreen(s);
		page.clear();
		boolean main = MAIN.equals(layout);
		if (main) {
			askExtras.clear();
			trail.clear();
		}
		AbstractWidget talk = null, trade = null;
		for (GuiEventListener l : children) {
			if (!(l instanceof McaButtonExAccessor ex) || !(l instanceof AbstractWidget w)) {
				continue;
			}
			MCAButton b = ex.jaymoskingdoms$apiButton();
			String id = b.identifier();
			if (main && id.equals(TALK)) {
				talk = w;
			}
			if (main && id.equals(TRADE)) {
				trade = w;
				continue;
			}
			if (w.visible && !id.equals(BACK_ARROW) && !id.equals(TALK) && !id.equals(GIFT)) {
				if (main && TO_ASK.contains(id)) {
					if (w.active) {
						askExtras.put(id, new Spoke(b, w.getMessage()));
					}
				} else {
					page.add(w);
				}
			}
			w.visible = false;
		}
		Entity v = ((McaInteractScreenAccessor) s).jaymoskingdoms$villager().asEntity();
		if (main && talk != null && !special(v)) {
			AbstractWidget anchor = talk;
			addGift.accept(net.minecraft.client.gui.components.Button.builder(Component.translatable(GIFT), b -> press(s,
					new MCAButton(GIFT, "", 0, 0, 0, 0, anchor.getWidth(), anchor.getHeight(), false, false, "", false, false)))
				.bounds(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight()).build());
			if (trade != null && trade.visible) {
				trade.setY(anchor.getY() + anchor.getHeight() + 1);
			}
		}
		if (main && !talkAsked && !stranger(v) && v instanceof VillagerEntityMCA) {
			talkAsked = true;
			Network.sendToServer(new InteractionDialogueInitMessage(v.getUUID()));
		}
	}

	/**
	 * The spokes for the wheel now: on the main page our own first (Family Tree, Ask of them...) and then MCA's answers to
	 * {@code question} (null while there's no talk yet); on any other page its buttons and Back. Empty: no wheel.
	 */
	public static List<String> spokes(InteractScreen s, @Nullable String question, @Nullable List<String> answers) {
		forScreen(s);
		McaInteractScreenAccessor interact = (McaInteractScreenAccessor) s;
		List<String> out = new ArrayList<>();
		if (interact.jaymoskingdoms$giftMode()) {
			return out;
		}
		Entity v = interact.jaymoskingdoms$villager().asEntity();
		page.removeIf(w -> !s.children().contains(w));
		if (!MAIN.equals(s.getActiveScreen())) {
			for (AbstractWidget w : page) {
				if (w.active) {
					out.add(LOCAL + ((McaButtonExAccessor) w).jaymoskingdoms$apiButton().identifier());
				}
			}
			if (ASK.equals(s.getActiveScreen())) {
				for (String id : askExtras.keySet()) {
					out.add(LOCAL + id);
				}
			}
			out.add(BACK);
			return out;
		}
		if (stranger(v)) {
			// (Andy: "no options but gift trade or introduce")
			out.add(INTRODUCE);
			return out;
		}
		if (talkAsked && question == null) {
			// their first words aren't in yet
			return out;
		}
		boolean questioned = answers != null && !answers.isEmpty() && Questioning.ANSWERS.containsAll(answers);
		if ((question == null || MAIN.equals(question)) && !questioned) {
			for (AbstractWidget w : page) {
				if (w.active) {
					out.add(LOCAL + ((McaButtonExAccessor) w).jaymoskingdoms$apiButton().identifier());
				}
			}
		}
		if (answers != null) {
			if (MAIN.equals(question) && !questioned && !special(v)) {
				// (Andy 2026-11-27) "there should always be an option to address what they say": first on the wheel
				out.addFirst(Replies.answer(topic()));
			}
			out.addAll(answers);
		}
		return out;
	}

	/** The villager said {@code text} in the talk box (MCA's question line, or one of ours): the reply spoke answers it. */
	public static void said(InteractScreen s, Component text) {
		forScreen(s);
		said = text;
	}

	/** The topic of what they last said ({@link Replies#topicOf}), from the key MCA picked for it and its words. */
	static String topic() {
		Component line = said;
		if (line == null) {
			return Replies.GENERAL;
		}
		if (line != topicOf || topic == null) {
			TranslatableContents tc = translatable(line, 0);
			topic = Replies.topicOf(tc == null ? null : key(tc), line.getString());
			topicOf = line;
		}
		return topic;
	}

	/** The last line's topic, worked out once (the wheel asks every frame). */
	private static @Nullable Component topicOf;
	private static @Nullable String topic;

	/** The first translated part of {@code c} (the line itself, or its parts and arguments). */
	private static @Nullable TranslatableContents translatable(Component c, int depth) {
		if (c.getContents() instanceof TranslatableContents tc) {
			return tc;
		}
		if (depth > 4) {
			return null;
		}
		for (Component part : c.getSiblings()) {
			TranslatableContents tc = translatable(part, depth + 1);
			if (tc != null) {
				return tc;
			}
		}
		return null;
	}

	/**
	 * The line's key: the one MCA's pooled lines resolved it to (e.g. {@code guard.dialogue.main.night/4}, which tells the
	 * creeper warning from the other night lines), when that is a pick of this line's own key; else the key it was sent
	 * with, less MCA's {@code #G..}/{@code #P..} speaker parts.
	 */
	private static String key(TranslatableContents tc) {
		String sent = tc.getKey();
		int hash = sent.lastIndexOf('#');
		String base = hash < 0 ? sent : sent.substring(sent.indexOf('.', hash) + 1);
		String resolved = CommonSpeechManager.INSTANCE.translations.get(tc);
		if (resolved != null && resolved.replaceAll("/\\d+$", "").endsWith(base)) {
			return resolved;
		}
		return base;
	}

	/** A spoke's label. */
	public static Component label(String spoke) {
		if (spoke.equals(BACK)) {
			return Component.translatable("gui.jaymoskingdoms.talk.back");
		}
		if (spoke.equals(INTRODUCE)) {
			return Component.translatable("gui.jaymoskingdoms.introduce");
		}
		String id = spoke.substring(LOCAL.length());
		if (id.equals(INTERACT)) {
			return Component.translatable("gui.jaymoskingdoms.talk.ask");
		}
		Spoke extra = askExtras.get(id);
		if (extra != null) {
			return extra.label();
		}
		for (AbstractWidget w : page) {
			if (((McaButtonExAccessor) w).jaymoskingdoms$apiButton().identifier().equals(id)) {
				return w.getMessage();
			}
		}
		return Component.translatable(id);
	}

	/** A spoke picked: Back, the introduction, or the MCA button it stands for, pressed as that button was. */
	public static void pick(InteractScreen s, String spoke) {
		forScreen(s);
		Entity v = ((McaInteractScreenAccessor) s).jaymoskingdoms$villager().asEntity();
		if (spoke.equals(BACK)) {
			s.setLayout(trail.isEmpty() ? MAIN : trail.pop());
			return;
		}
		if (spoke.equals(INTRODUCE)) {
			Network.sendToServer(new InteractionDialogueMessage(v.getUUID(), MAIN, Introductions.INTRODUCE));
			return;
		}
		String id = spoke.substring(LOCAL.length());
		Spoke extra = askExtras.get(id);
		MCAButton button = extra != null ? extra.button() : null;
		for (AbstractWidget w : page) {
			MCAButton b = ((McaButtonExAccessor) w).jaymoskingdoms$apiButton();
			if (button == null && b.identifier().equals(id)) {
				button = b;
			}
		}
		if (button == null) {
			return;
		}
		String before = s.getActiveScreen();
		press(s, button);
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() == s && !before.equals(s.getActiveScreen())) {
			trail.push(before);
		}
	}

	private static void press(InteractScreen s, MCAButton button) {
		((McaInteractScreenAccessor) s).jaymoskingdoms$press(button);
	}
}
