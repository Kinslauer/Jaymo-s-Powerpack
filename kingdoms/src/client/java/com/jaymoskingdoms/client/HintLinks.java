package com.jaymoskingdoms.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-23): "when you are given a tooltip hint like the screen shot pressing H or K should take you to exactly what
 * was mentioned. instead of just to that screen." A hint line that names a place (the H menu's Feats and Paths or Rewards,
 * a perk in the K skill tree) is remembered for {@link #FRESH_MS}; the next press of its key opens that page on the thing
 * named: the class whose path is ready, the perk not learned yet. Read off the action-bar line itself
 * ({@code HintLinkOverlayMixin}), so it works with Jaymo's Powers' tip cards or without them.
 */
public final class HintLinks {
	// ---- tuning ----
	/** How long after a hint (ms) its key still goes where it said. */
	static final long FRESH_MS = 60_000L;

	/** The page a hint names. */
	enum Page {
		/** The K skill tree. */
		SKILLS,
		/** The H menu's Feats and Paths. */
		GROWTH,
		/** The H menu's Rewards. */
		REWARDS
	}

	/**
	 * Where a hint pointed: its page, and on it a name key ({@code skill.jaymoskingdoms.perk.*} or
	 * {@code classtree.jaymoskingdoms.*}), {@link #PATH_PERK}, {@link #FEATS} or {@link #PATHS}; null for the page itself.
	 */
	record Link(Page page, @Nullable String target, long at) {
	}

	/** Targets that aren't a name key. */
	static final String PATH_PERK = "path_perk", FEATS = "feats", PATHS = "paths";

	private static @Nullable Link pending;
	/** A link H is opening its page for (the page takes it as it opens). */
	private static @Nullable Link opening;

	private HintLinks() {
	}

	/** An action-bar line came: if it is a hint that names a place, remember where. */
	public static void heard(Component message) {
		TranslatableContents said = said(message);
		if (said == null) {
			return;
		}
		Link link = switch (said.getKey()) {
			case "message.jaymoskingdoms.tree_lock.locked", "message.jaymoskingdoms.tree_lock.rank" -> {
				Object[] args = said.getArgs();
				TranslatableContents perk = args.length > 0 && args[0] instanceof Component c ? said(c) : null;
				yield perk == null ? null : link(Page.SKILLS, perk.getKey());
			}
			case "message.jaymoskingdoms.tree_lock.path" -> link(Page.SKILLS, PATH_PERK);
			case "message.jaymoskingdoms.combat_art.none" -> link(Page.SKILLS, "skill.jaymoskingdoms.perk.charge");
			case "message.jaymospowers.meditate.locked" -> link(Page.SKILLS, "skill.jaymoskingdoms.perk.meditation");
			case "message.jaymoskingdoms.path.owed" -> link(Page.GROWTH, PATHS);
			case "message.jaymoskingdoms.feat.owed" -> link(Page.GROWTH, FEATS);
			case "message.jaymoskingdoms.rewards.held" -> link(Page.REWARDS, null);
			default -> null;
		};
		if (link != null) {
			pending = link;
		}
	}

	private static Link link(Page page, @Nullable String target) {
		return new Link(page, target, Util.getMillis());
	}

	/** The translatable inside {@code message}: itself, or the one line inside an empty wrapper. */
	private static @Nullable TranslatableContents said(Component message) {
		if (message.getContents() instanceof TranslatableContents said) {
			return said;
		}
		List<Component> siblings = message.getSiblings();
		return message.getContents() == PlainTextContents.EMPTY && siblings.size() == 1 ? said(siblings.getFirst()) : null;
	}

	private static boolean fresh(@Nullable Link link) {
		return link != null && Util.getMillis() - link.at() <= FRESH_MS;
	}

	/**
	 * For Jaymo's Powers' H (by reflection, {@code MenuTabs.landing}): the menu tab a fresh hint names ({@code growth},
	 * {@code rewards}), or null for the page you had open last. The page takes the link as it opens ({@link #take}).
	 */
	static @Nullable String landing() {
		Link link = pending;
		if (!fresh(link) || link.page() == Page.SKILLS) {
			return null;
		}
		pending = null;
		// (Rewards has nothing to pick: only Feats and Paths takes the link)
		opening = link.page() == Page.GROWTH ? link : null;
		return link.page() == Page.GROWTH ? "growth" : "rewards";
	}

	/** The fresh link to {@code page}, taken (each is used once), or null. */
	static @Nullable Link take(Page page) {
		Link link = fresh(opening) && opening.page() == page ? opening : fresh(pending) && pending.page() == page ? pending : null;
		if (link == opening) {
			opening = null;
		}
		if (link == pending) {
			pending = null;
		}
		return link;
	}
}
