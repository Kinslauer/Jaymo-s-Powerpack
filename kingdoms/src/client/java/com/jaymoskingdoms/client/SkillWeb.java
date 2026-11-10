package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Skills;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * (0.7.514) Where every perk sits on the skill web: one big circle with you at its middle, split in three
 * (Strength on top, Survival lower right, Stealth lower left). Each of a pillar's old pages is a branch running outward
 * from it; a perk's ring is how deep it sits along its requirements, so a pick's place shows what it leads to. The bridge
 * perks sit on the borders between two pillars. Positions are in web units, from the middle; the screen scales them.
 */
final class SkillWeb {
	/** The first ring, the gap between rings, how far out the pillar crests and the branch gates sit (web units). */
	static final double R0 = 80.0, STEP = 28.0, CREST_R = 30.0, GATE_R = 56.0;
	/** How far round the branch's wedge each column of three is set (a share of the wedge), and Meditation's. */
	static final double COLUMN_SPREAD = 0.30, MEDITATION_SPREAD = -0.42;
	/** Will's second page (Killing Intent) starts this many rings out, past Will's own label. */
	static final int KILLING_INTENT_RING = 5;
	/** (0.7.516) The class-skill and Weapon Forms branches start this many rings out, where their narrow wedges have room. */
	static final int SKILL_BRANCH_RING = 3;
	/** The first bridge on a border sits on this ring, each next one this much further out. */
	static final double BRIDGE_RING = 2.0, BRIDGE_RING_STEP = 1.6;
	/** How far past a branch's last ring its name sits (rings). */
	static final double LABEL_RING = 1.5;
	/** The most rings drawn. */
	static final int RINGS = 10;

	/**
	 * Each pillar's wedge (degrees, clockwise from the right, y down): Strength on top, Survival lower right, Stealth lower
	 * left. The Scholar's pillar has none: it is learned in the Astronomer's Journal ({@link Skills.Pillar#web()}).
	 */
	static double from(Skills.Pillar pillar) {
		return switch (pillar) {
			case STRENGTH -> -155.0;
			case SURVIVAL -> -25.0;
			case STEALTH -> 90.0;
			case SCHOLAR -> throw new IllegalArgumentException("the Scholar pillar is not on the web");
		};
	}

	static double to(Skills.Pillar pillar) {
		return switch (pillar) {
			case STRENGTH -> -25.0;
			case SURVIVAL -> 90.0;
			case STEALTH -> 205.0;
			case SCHOLAR -> throw new IllegalArgumentException("the Scholar pillar is not on the web");
		};
	}

	/** The borders between pillars, as the angle each sits at. */
	static double border(Skills.Pillar a, Skills.Pillar b) {
		if (to(a) == from(b) || to(a) - 360.0 == from(b)) {
			return from(b);
		}
		return from(a);
	}

	/** A branch: a pillar's page, in order round its wedge. */
	enum Branch {
		// (0.7.516) the RPG Series' skills and the Weapon Forms (Learnables) sit in the middle of each wedge, clear of the bridges
		WILL(Skills.Pillar.STRENGTH), COMBAT_ARTS(Skills.Pillar.STRENGTH), WARFARE(Skills.Pillar.STRENGTH), WEAPON_FORMS(Skills.Pillar.STRENGTH),
		MIGHT(Skills.Pillar.STRENGTH),
		FORAGING(Skills.Pillar.SURVIVAL), ARCHERY(Skills.Pillar.SURVIVAL), SPELLCRAFT(Skills.Pillar.SURVIVAL), MEDICINE(Skills.Pillar.SURVIVAL),
		FREERUNNING(Skills.Pillar.STEALTH), ROGUES_ARTS(Skills.Pillar.STEALTH), KNIFE_ARTS(Skills.Pillar.STEALTH), THIEVERY(Skills.Pillar.STEALTH),
		SHADOWS(Skills.Pillar.STEALTH);

		final Skills.Pillar pillar;

		Branch(Skills.Pillar pillar) {
			this.pillar = pillar;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** (0.7.516) The branches that start {@link #SKILL_BRANCH_RING} rings out. */
	static final java.util.Set<Branch> LATE_BRANCHES = java.util.EnumSet.of(Branch.WARFARE, Branch.WEAPON_FORMS, Branch.ARCHERY,
		Branch.SPELLCRAFT, Branch.ROGUES_ARTS, Branch.KNIFE_ARTS);

	/** A point on the web (web units). */
	record Spot(double x, double y) {
	}

	/** A branch label: its name, where it sits. */
	record Label(String key, Skills.Pillar pillar, Spot at) {
	}

	static final Map<Skills.Perk, Spot> SPOTS = new EnumMap<>(Skills.Perk.class);
	static final Map<Skills.Perk, Branch> BRANCHES = new EnumMap<>(Skills.Perk.class);
	static final Map<Branch, Spot> GATES = new EnumMap<>(Branch.class);
	static final Map<Skills.Pillar, Spot> CRESTS = new EnumMap<>(Skills.Pillar.class);
	static final List<Label> LABELS = new ArrayList<>();
	private static final Map<Skills.Perk, Integer> DEPTH = new EnumMap<>(Skills.Perk.class);

	private SkillWeb() {
	}

	private static int page(Skills.Perk perk) {
		return perk.tier / Skills.Perk.PAGE_TIERS;
	}

	/** The branch a (non-bridge) perk grows on. */
	static Branch branch(Skills.Perk perk) {
		int page = page(perk);
		return switch (perk.pillar) {
			case STRENGTH -> page >= 5 ? Branch.WEAPON_FORMS : page == 4 ? Branch.WARFARE : page >= 2 ? Branch.WILL
				: page == 1 || perk == Skills.Perk.WEAPON_FORMS ? Branch.COMBAT_ARTS : Branch.MIGHT;
			case SURVIVAL -> page >= 3 ? Branch.SPELLCRAFT : page == 2 ? Branch.ARCHERY : page == 1 ? Branch.MEDICINE : Branch.FORAGING;
			case STEALTH -> page >= 4 ? Branch.KNIFE_ARTS : page == 3 ? Branch.ROGUES_ARTS : page == 1 ? Branch.FREERUNNING
				: page == 2 ? Branch.SHADOWS : Branch.THIEVERY;
			case SCHOLAR -> throw new IllegalArgumentException("the Scholar pillar is not on the web");
		};
	}

	/** The perks {@code perk} grows from on its own page (the same branch), Weapon Forms leading the Combat Arts. */
	private static List<Skills.Perk> parents(Skills.Perk perk) {
		List<Skills.Perk> out = new ArrayList<>();
		for (Skills.Perk need : perk.requires) {
			// (0.7.516) Spellcraft runs over two pages as one branch
			boolean samePage = page(need) == page(perk) || branch(perk) == Branch.SPELLCRAFT && branch(need) == Branch.SPELLCRAFT;
			if (!need.bridge() && samePage && (need == Skills.Perk.WEAPON_FORMS) == (perk == Skills.Perk.WEAPON_FORMS)) {
				out.add(need);
			}
		}
		if (out.isEmpty() && perk.requires.contains(Skills.Perk.WEAPON_FORMS)) {
			out.add(Skills.Perk.WEAPON_FORMS);
		}
		return out;
	}

	/** The ring {@code perk} sits on: one past the deepest perk it grows from. */
	static int depth(Skills.Perk perk) {
		Integer known = DEPTH.get(perk);
		if (known != null) {
			return known;
		}
		List<Skills.Perk> parents = parents(perk);
		int d = 0;
		for (Skills.Perk p : parents) {
			d = Math.max(d, depth(p) + 1);
		}
		if (parents.isEmpty() && perk.pillar == Skills.Pillar.STRENGTH && page(perk) == 3) {
			d = KILLING_INTENT_RING;
		}
		if (parents.isEmpty() && LATE_BRANCHES.contains(branch(perk))) {
			d = SKILL_BRANCH_RING;
		}
		DEPTH.put(perk, d);
		return d;
	}

	private static Spot polar(double degrees, double r) {
		double a = Math.toRadians(degrees);
		return new Spot(r * Math.cos(a), r * Math.sin(a));
	}

	static {
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			if (!pillar.web()) {
				continue;
			}
			CRESTS.put(pillar, polar((from(pillar) + to(pillar)) / 2.0, CREST_R));
			List<Branch> branches = new ArrayList<>();
			for (Branch b : Branch.values()) {
				if (b.pillar == pillar) {
					branches.add(b);
				}
			}
			double wedge = (to(pillar) - from(pillar)) / branches.size();
			for (int i = 0; i < branches.size(); i++) {
				Branch b = branches.get(i);
				double mid = from(pillar) + wedge * (i + 0.5);
				GATES.put(b, polar(mid, GATE_R));
				int deepest = 0, deepestWill = 0;
				for (Skills.Perk perk : Skills.Perk.values()) {
					if (perk.bridge() || !perk.pillar.web() || branch(perk) != b) {
						continue;
					}
					BRANCHES.put(perk, b);
					int d = depth(perk);
					deepest = Math.max(deepest, d);
					if (b == Branch.WILL && page(perk) == 2) {
						deepestWill = Math.max(deepestWill, d);
					}
					double off = perk == Skills.Perk.MEDITATION ? wedge * MEDITATION_SPREAD : (perk.column - 1) * wedge * COLUMN_SPREAD;
					SPOTS.put(perk, polar(mid + off, R0 + d * STEP));
				}
				if (b == Branch.WILL) {
					LABELS.add(new Label(Branch.WILL.key(), pillar, polar(mid, R0 + (deepestWill + LABEL_RING) * STEP)));
					LABELS.add(new Label("killing_intent", pillar, polar(mid, R0 + (deepest + LABEL_RING) * STEP)));
				} else {
					LABELS.add(new Label(b.key(), pillar, polar(mid, R0 + (deepest + LABEL_RING) * STEP)));
				}
			}
		}
		// the bridges: on their border, each next one further out
		Map<Double, Integer> onBorder = new java.util.HashMap<>();
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (!perk.bridge() || perk.other == null) {
				continue;
			}
			double angle = border(perk.pillar, perk.other);
			int n = onBorder.merge(angle, 1, Integer::sum) - 1;
			SPOTS.put(perk, polar(angle, R0 + (BRIDGE_RING + BRIDGE_RING_STEP * n) * STEP));
		}
	}

	/** The web's bounds (web units): its perks and branch names, {min x, min y, max x, max y}. */
	static double[] bounds() {
		double[] b = {0.0, 0.0, 0.0, 0.0};
		List<Spot> all = new ArrayList<>(SPOTS.values());
		LABELS.forEach(l -> all.add(l.at()));
		for (Spot s : all) {
			b[0] = Math.min(b[0], s.x());
			b[1] = Math.min(b[1], s.y());
			b[2] = Math.max(b[2], s.x());
			b[3] = Math.max(b[3], s.y());
		}
		return b;
	}

	/** The perks that need {@code perk} (whose requirements name it). */
	static List<Skills.Perk> leadsTo(Skills.Perk perk) {
		List<Skills.Perk> out = new ArrayList<>();
		for (Skills.Perk p : Skills.Perk.values()) {
			if (p.requires.contains(perk)) {
				out.add(p);
			}
		}
		return out;
	}
}
