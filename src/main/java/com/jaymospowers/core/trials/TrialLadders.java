package com.jaymospowers.core.trials;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.trials.PowerTrials.Kind;
import com.jaymospowers.core.trials.PowerTrials.Trial;
import java.util.List;
import java.util.Map;

/**
 * Every power's own trial ladder ({@link PowerTrials}): sixteen trials each, easier first. A line is one trial:
 * {@code Trial.feat(key, goal)} is a feat of the power's own, which its code reports with
 * {@code PowerTrials.count(player, key, 1)} (so many in all) or {@code PowerTrials.reach(player, key, value)} (so many in
 * one go); a third number is the ability it waits for (0 the starter, 1 to 3 in unlock order). {@code Trial.shared} is
 * one every power can have (at most four on a ladder). The first, fourth, seventh and tenth trials are the four
 * abilities' own. A trial's name is lang {@code trial.jaymospowers.<key>} and the thought it comes with
 * {@code trial.jaymospowers.<key>.thought} (lang part {@code trials.json}).
 * <p>A trial is saved by its place on the ladder: add new ones by replacing, not by reordering.
 */
public final class TrialLadders {
	private static final Map<String, List<Trial>> LADDERS = Map.ofEntries(
		// ---- Gravity ----
		Map.entry("gravity", List.of(
			Trial.feat("gravity.lift", 15, 0),
			Trial.feat("gravity.hurl", 10, 0),
			Trial.shared(Kind.HIGH, 1),
			Trial.feat("gravity.weightless", 120, 1),
			Trial.feat("gravity.push", 10, 0),
			Trial.feat("gravity.planet", 10, 2),
			Trial.feat("gravity.well_many", 3, 2),
			Trial.feat("gravity.board", 60, 1),
			Trial.feat("gravity.weight_kills", 10),
			Trial.feat("gravity.crush", 25, 3),
			Trial.feat("gravity.wave", 15, 3),
			Trial.feat("gravity.inversion", 5, 3),
			Trial.feat("gravity.rocks", 20),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.END, 1),
			Trial.feat("gravity.overturn", 3, 3))),
		// ---- Chrono ----
		Map.entry("chrono", List.of(
			Trial.feat("chrono.rewind", 60, 0),
			Trial.shared(Kind.TRAVEL, 3000),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("chrono.water", 30, 1),
			Trial.feat("chrono.flurry", 40, 1),
			Trial.feat("chrono.phase", 5, 1),
			Trial.feat("chrono.bloom_heal", 100, 2),
			Trial.feat("chrono.gear", 5, 1),
			Trial.feat("chrono.slow_time", 5, 1),
			Trial.feat("chrono.stasis_many", 4, 3),
			Trial.feat("chrono.ring", 3, 1),
			Trial.feat("chrono.decoys", 5, 1),
			Trial.feat("chrono.wind_day", 20),
			Trial.feat("chrono.wall_run", 10, 1),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.feat("chrono.cheat_death", 1, 0))),
		// ---- Web Slinger ----
		Map.entry("web", List.of(
			Trial.feat("web.swing_far", 100, 0),
			Trial.feat("web.trick", 15, 0),
			Trial.shared(Kind.HIGH, 1),
			Trial.feat("web.whip", 5, 1),
			Trial.feat("web.wallrun", 10),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("web.yank", 15, 2),
			Trial.feat("web.hitch", 5, 2),
			Trial.feat("web.hang", 60),
			Trial.feat("web.shot", 20, 3),
			Trial.feat("web.bridge", 5, 2),
			Trial.feat("web.parkour", 20),
			Trial.feat("web.string_up", 5, 2),
			Trial.shared(Kind.NETHER, 1),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.feat("web.swing_marathon", 500, 0))),
		// ---- Wind Control ----
		Map.entry("wind", List.of(
			Trial.feat("wind.flight", 120, 0),
			Trial.feat("wind.ram", 10, 0),
			Trial.shared(Kind.HIGH, 1),
			Trial.feat("wind.barrier_push", 15, 1),
			Trial.feat("wind.douse", 20),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("wind.zephyr", 15, 2),
			Trial.feat("wind.deflect", 10, 1),
			Trial.feat("wind.whistle", 20),
			Trial.feat("wind.gust", 20, 3),
			Trial.feat("wind.tailwind", 10, 3),
			Trial.feat("wind.cyclone", 30),
			Trial.feat("wind.dive", 30),
			Trial.feat("wind.sonic", 30, 0),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.END, 1))),
		// ---- Cryokinesis ----
		Map.entry("cryo", List.of(
			Trial.feat("cryo.slide", 60, 0),
			Trial.feat("cryo.slide_water", 20, 0),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("cryo.breath", 30, 1),
			Trial.feat("cryo.freeze_water", 30, 1),
			Trial.feat("cryo.wall", 10),
			Trial.feat("cryo.shards", 25, 2),
			Trial.shared(Kind.HIGH, 1),
			Trial.feat("cryo.shards_far", 12, 2),
			Trial.feat("cryo.flash_freeze", 10, 3),
			Trial.feat("cryo.shatter", 5, 3),
			Trial.feat("cryo.freeze_big", 40, 3),
			Trial.shared(Kind.NETHER, 1),
			Trial.feat("cryo.wall_nether", 1),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.feat("cryo.blizzard_hold", 60, 3))),
		// ---- Smoke ----
		Map.entry("smoke", List.of(
			Trial.feat("smoke.vapour", 60, 0),
			Trial.feat("smoke.soft_landing", 5, 0),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("smoke.screen", 60, 1),
			Trial.feat("smoke.pass", 10, 0),
			Trial.feat("smoke.jet", 60),
			Trial.feat("smoke.bomb", 20, 2),
			Trial.shared(Kind.DEEP, 1),
			Trial.feat("smoke.step", 10),
			Trial.feat("smoke.smother", 60, 3),
			Trial.feat("smoke.bomb_many", 4, 2),
			Trial.feat("smoke.sight", 60),
			Trial.feat("smoke.choke", 15, 3),
			Trial.shared(Kind.NETHER, 1),
			Trial.feat("smoke.whole", 30, 0),
			Trial.shared(Kind.KILL_BIG, 1))),
		// ---- Resonance ----
		Map.entry("resonance", List.of(
			Trial.feat("resonance.echo", 40, 0),
			Trial.feat("resonance.lure", 10),
			Trial.shared(Kind.DEEP, 1),
			Trial.feat("resonance.pulse", 25, 1),
			Trial.feat("resonance.pulse_many", 3, 1),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("resonance.shatter_block", 30, 2),
			Trial.feat("resonance.echo_deep", 5, 0),
			Trial.feat("resonance.strip", 15, 2),
			Trial.feat("resonance.hush_many", 4, 3),
			Trial.feat("resonance.shatter_hit", 10, 2),
			Trial.feat("resonance.echo_many", 6, 0),
			Trial.feat("resonance.quiet", 60, 3),
			Trial.feat("resonance.pulse_far", 7, 1),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.END, 1))),
		// ---- Darktide ----
		Map.entry("darktide", List.of(
			Trial.feat("darktide.strike", 20, 0),
			Trial.feat("darktide.parry", 10),
			Trial.shared(Kind.DEEP, 1),
			Trial.feat("darktide.undertow", 15, 1),
			Trial.feat("darktide.shadow", 10),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("darktide.crescent", 20, 2),
			Trial.feat("darktide.riposte", 5),
			Trial.feat("darktide.surf", 60),
			Trial.feat("darktide.tide", 20, 3),
			Trial.feat("darktide.cripple", 10),
			Trial.feat("darktide.geyser", 10),
			Trial.feat("darktide.pit", 10),
			Trial.feat("darktide.reflect", 5),
			Trial.shared(Kind.NETHER, 1),
			Trial.shared(Kind.KILL_BIG, 1))),
		// ---- Pocket Arsenal ----
		Map.entry("arsenal", List.of(
			Trial.feat("arsenal.reach", 15, 0),
			Trial.feat("arsenal.ride", 30),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("arsenal.return", 10, 1),
			Trial.feat("arsenal.rift", 5),
			Trial.feat("arsenal.portal", 10),
			Trial.feat("arsenal.barrage", 25, 2),
			Trial.shared(Kind.NETHER, 1),
			Trial.feat("arsenal.storm", 30, 2),
			Trial.feat("arsenal.chains", 10, 3),
			Trial.feat("arsenal.cannons", 20),
			Trial.feat("arsenal.wing", 30, 2),
			Trial.feat("arsenal.chain_yank", 5, 3),
			Trial.feat("arsenal.barrage_far", 20, 2),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.END, 1))),
		// ---- Blinker ----
		Map.entry("blinker", List.of(
			Trial.feat("blinker.blink", 200, 0),
			Trial.feat("blinker.air", 10, 0),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("blinker.strike", 15, 1),
			Trial.feat("blinker.untouched", 10),
			Trial.feat("blinker.strike_far", 15, 1),
			Trial.feat("blinker.vanish", 15, 2),
			Trial.shared(Kind.END, 1),
			Trial.feat("blinker.vanish_many", 3, 2),
			Trial.feat("blinker.drop", 10, 3),
			Trial.feat("blinker.strike_air", 3, 1),
			Trial.feat("blinker.blink_long", 11, 0),
			Trial.feat("blinker.drop_big", 30, 3),
			Trial.feat("blinker.end_blink", 10, 0),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.TRAVEL, 3000))),
		// ---- Reactor Heart ----
		Map.entry("reactor", List.of(
			Trial.feat("reactor.cone", 20, 0),
			Trial.feat("reactor.land", 20),
			Trial.shared(Kind.HURT, 30),
			Trial.feat("reactor.dash", 10, 1),
			Trial.feat("reactor.overload", 5),
			Trial.shared(Kind.TRAVEL, 3000),
			Trial.feat("reactor.beam", 15, 2),
			Trial.feat("reactor.recoil", 10, 1),
			Trial.feat("reactor.crack", 10, 3),
			Trial.feat("reactor.drill", 100, 3),
			Trial.feat("reactor.toss", 10, 2),
			Trial.feat("reactor.beam_far", 40, 2),
			Trial.feat("reactor.overload_many", 4),
			Trial.feat("reactor.full", 80),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.NETHER, 1))),
		// ---- Lawgiver ----
		Map.entry("rules", List.of(
			Trial.feat("rules.enact", 15, 0),
			Trial.shared(Kind.HURT, 30),
			Trial.shared(Kind.NETHER, 1),
			Trial.feat("rules.book", 5, 1),
			Trial.feat("rules.foes", 10, 1),
			Trial.feat("rules.target", 10, 1),
			Trial.feat("rules.pick", 15, 2),
			Trial.feat("rules.allies", 5, 1),
			Trial.feat("rules.many", 5, 1),
			Trial.feat("rules.repeal", 5, 3),
			Trial.feat("rules.long", 60, 1),
			Trial.feat("rules.strength", 4, 1),
			Trial.feat("rules.mute", 3, 1),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.shared(Kind.END, 1),
			Trial.feat("rules.at_once", 3, 1))),
		// ---- Xenoform ----
		Map.entry("xenoform", List.of(
			Trial.feat("xenoform.move_r", 20, 0),
			Trial.feat("xenoform.slam", 10),
			Trial.feat("xenoform.time", 300),
			Trial.feat("xenoform.move_g", 20, 1),
			Trial.feat("xenoform.blows", 40),
			Trial.feat("xenoform.soak", 40),
			Trial.feat("xenoform.move_v", 15, 2),
			Trial.feat("xenoform.forms", 4),
			Trial.feat("xenoform.scan", 2),
			Trial.feat("xenoform.move_b", 15, 3),
			Trial.feat("xenoform.dry", 3),
			Trial.feat("xenoform.skill", 2),
			Trial.feat("xenoform.quick", 5),
			Trial.feat("xenoform.wild", 3),
			Trial.shared(Kind.KILL_BIG, 1),
			Trial.feat("xenoform.reforged", 1))),
		// ---- Z Fighter ----
		Map.entry("zfighter", List.of(
			Trial.feat("zfighter.charge", 60, 0),
			Trial.feat("zfighter.punch", 50),
			Trial.feat("zfighter.eat", 40),
			Trial.feat("zfighter.blast_hit", 20, 1),
			Trial.feat("zfighter.full", 5, 0),
			Trial.feat("zfighter.parry", 5),
			Trial.feat("zfighter.step_at", 10, 2),
			Trial.feat("zfighter.finisher", 10),
			Trial.feat("zfighter.flight", 120, 1),
			Trial.feat("zfighter.wave_hit", 15, 3),
			Trial.feat("zfighter.slam", 10),
			Trial.shared(Kind.HIGH, 1),
			Trial.feat("zfighter.shadowbox", 120),
			Trial.feat("zfighter.blast_far", 30, 1),
			Trial.feat("zfighter.wave_many", 3, 3),
			Trial.shared(Kind.KILL_BIG, 1))));

	private TrialLadders() {
	}

	/** {@code power}'s ladder: its own, or the shared one for a power that has none here. */
	public static List<Trial> of(Power power) {
		return power.id().getNamespace().equals(JaymosPowers.MOD_ID) ? LADDERS.getOrDefault(power.id().getPath(), PowerTrials.SHARED) : PowerTrials.SHARED;
	}
}
