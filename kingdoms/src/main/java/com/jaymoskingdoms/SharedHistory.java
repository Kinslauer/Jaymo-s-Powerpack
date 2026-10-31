package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.254) Andy: "add context based talk options with villagers, for example i was revived by a villager and would
 * have liked to get up and thank them, or if a guard i havent met before's first interaction is a shove or searching
 * me, the introduction might be awkward". MCA's menu offers what your history with this villager calls for, from what
 * they remember ({@link Recollections}), and each shows only while there's something to take up:
 * <ul>
 * <li>"Thank them" ({@link #THANK}): they got you up when you were downed, or walked you to the ruler. One who got you up
 * stays at your side a moment ({@link #LINGER_TICKS}) so you can. Warm, gruff or greedy as they are; late thanks are
 * noticed. It needs no shared tongue.</li>
 * <li>"Apologise" ({@link #APOLOGISE}): for the latest wrong you did them (struck, rude, lied, threatened, a crime they
 * saw, their pocket, a hug they didn't want, a task given up). Taken ({@link #forgives}), the wrong is let go; spurned,
 * it stands, and there's no asking twice for the same one. A killed kinsman is never forgiven this way.</li>
 * <li>"Clear the air" ({@link #AIR}): they shoved you, searched your pack or stopped you in the street. Soft ones are
 * sorry, dutiful ones say it was the job, hard ones would do it again.</li>
 * <li>"Ask how they are" ({@link #ASK_AFTER}): you saved them, freed them, brought them home or healed them. Once each;
 * the first time, they may press a small thanks on you.</li>
 * <li>Introductions ({@link #introduction}): asked their name, one whose first dealing with you was one of these says so
 * (the guard who shoved you, the stranger who hit them, the one they bandaged in the mud).</li>
 * </ul>
 */
public final class SharedHistory {
	/** The MCA {@code main} menu answers (our dialogue data). */
	public static final String THANK = "jaymoskingdoms_thank", APOLOGISE = "jaymoskingdoms_apologise", AIR = "jaymoskingdoms_clear_air",
		ASK_AFTER = "jaymoskingdoms_ask_after";

	/** How long one who got you up stays by you to be thanked (ticks), and how far you may go before they give up. */
	static final int LINGER_TICKS = 20 * 12;
	static final double LINGER_RANGE = 10.0;
	/** Hearts won: thanking them, an apology taken, a sorry guard, asking after them. */
	static final int THANK_HEARTS = 2, APOLOGY_HEARTS = 1, AIR_HEARTS = 2, ASK_HEARTS = 1;
	/** Thanks this many days after count as late. */
	static final int LATE_DAYS = 2;
	/** An apology taken: the odds by the wrong's weight (1 to 4+), what a soft or hard temperament adds, what a heart adds, and the bounds. */
	static final float[] FORGIVE_BY_WEIGHT = {0.9F, 0.75F, 0.55F, 0.4F};
	static final float FORGIVE_SOFT = 0.2F, FORGIVE_HARD = -0.25F, FORGIVE_PER_HEART = 0.005F, FORGIVE_GRUDGE = -0.2F, FORGIVE_LEAST = 0.05F,
		FORGIVE_MOST = 0.95F;
	/** Asked after the first time: the chance they press something on you. */
	static final float KEEPSAKE_CHANCE = 0.6F;
	/** Lines per tone. */
	static final int LINES = 2;

	/** What they did for you, to thank them for. */
	static final Set<Recollections.Kind> FAVOURS = Set.of(Recollections.Kind.REVIVED_YOU, Recollections.Kind.LED_YOU);
	/** What you did to them, to apologise for. */
	static final Set<Recollections.Kind> WRONGS = Set.of(Recollections.Kind.STRUCK, Recollections.Kind.RUDE, Recollections.Kind.LIED,
		Recollections.Kind.THREATENED, Recollections.Kind.CRIME, Recollections.Kind.ROBBED, Recollections.Kind.KILLED_KIN, Recollections.Kind.REBUFFED,
		Recollections.Kind.QUEST_FAILED, Recollections.Kind.THREW,
		// (0.7.424)
		Recollections.Kind.QUESTIONED);
	/** What they did to you, to have out with them. */
	static final Set<Recollections.Kind> ROUGH = Set.of(Recollections.Kind.SHOVED_YOU, Recollections.Kind.SEARCHED_YOU, Recollections.Kind.HALTED_YOU);
	/** What you did for them, to ask after. */
	static final Set<Recollections.Kind> HELPED = Set.of(Recollections.Kind.SAVED, Recollections.Kind.FREED, Recollections.Kind.HOME,
		Recollections.Kind.MENDED, Recollections.Kind.CARRIED, Recollections.Kind.GOT_UP, Recollections.Kind.TENDED, Recollections.Kind.SHIELDED,
		Recollections.Kind.SPARED);
	/** A first dealing that colours an introduction. */
	static final Set<Recollections.Kind> FIRST = Set.of(Recollections.Kind.SHOVED_YOU, Recollections.Kind.SEARCHED_YOU, Recollections.Kind.HALTED_YOU,
		Recollections.Kind.REVIVED_YOU, Recollections.Kind.LED_YOU, Recollections.Kind.STRUCK, Recollections.Kind.RUDE, Recollections.Kind.CRIME,
		Recollections.Kind.ROBBED, Recollections.Kind.SAVED, Recollections.Kind.FREED, Recollections.Kind.HOME, Recollections.Kind.MENDED,
		Recollections.Kind.CARRIED, Recollections.Kind.GOT_UP, Recollections.Kind.TENDED, Recollections.Kind.SHIELDED);

	private record Linger(UUID player, long until) {
	}

	/** One who just got a player up → whom they wait on, and until when (game time). */
	private static final Map<Villager, Linger> LINGERING = new WeakHashMap<>();

	private SharedHistory() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SharedHistory::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LINGERING.clear());
	}

	// ---- the hooks ------------------------------------------------------------------------------------------------------

	/** (Downed) {@code helper} got {@code player} back on their feet: remembered, and they stay a moment to be thanked. */
	static void revived(Villager helper, ServerPlayer player) {
		Recollections.remember(helper, player, Recollections.Kind.REVIVED_YOU, Component.empty());
		LINGERING.put(helper, new Linger(player.getUUID(), helper.level().getGameTime() + LINGER_TICKS));
	}

	private static void tick(MinecraftServer server) {
		if (LINGERING.isEmpty()) {
			return;
		}
		LINGERING.entrySet().removeIf(e -> {
			Villager v = e.getKey();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getValue().player());
			if (p == null || !v.isAlive() || v.level() != p.level() || v.level().getGameTime() > e.getValue().until() || Downed.isDowned(v)
				|| v.getTarget() != null || v.distanceToSqr(p) > LINGER_RANGE * LINGER_RANGE || !canThank(p, v)) {
				return true;
			}
			// (they stand and watch you find your feet)
			v.getNavigation().stop();
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getLookControl().setLookAt(p, 30.0F, 30.0F);
			return false;
		});
	}

	// ---- what the menu offers ----------------------------------------------------------------------------------------------

	/** The memory's detail as a key ("for.<kind>" for ours), to match an answer to what it answered. */
	private static String detailKey(Component detail) {
		return detail.getContents() instanceof TranslatableContents t ? t.getKey() : detail.getString();
	}

	private static Component about(Recollections.Kind kind) {
		return Component.translatable("history.jaymoskingdoms.for." + kind.key());
	}

	/** {@code r} hasn't been answered yet with any of {@code answers} (one made the same day or after, about it). */
	private static boolean open(List<Recollections.Recollection> all, Recollections.Recollection r, Set<Recollections.Kind> answers) {
		Recollections.Kind k = r.type();
		if (k == null) {
			return false;
		}
		String key = "history.jaymoskingdoms.for." + k.key();
		for (Recollections.Recollection a : all) {
			Recollections.Kind ak = a.type();
			if (ak != null && answers.contains(ak) && a.day() >= r.day() && detailKey(a.detail()).equals(key)) {
				return false;
			}
		}
		return true;
	}

	/** (0.7.380) {@code a} happened no earlier than {@code b}: by game time where both have one, else by day. */
	private static boolean later(Recollections.Recollection a, Recollections.Recollection b) {
		return a.at() > 0L && b.at() > 0L ? a.at() >= b.at() : a.day() >= b.day();
	}

	/** The latest of {@code kinds} {@code who} remembers of {@code player} themselves that's still to be answered with {@code answers}. */
	private static Recollections.@Nullable Recollection latest(LivingEntity who, ServerPlayer player, Set<Recollections.Kind> kinds,
		Set<Recollections.Kind> answers) {
		List<Recollections.Recollection> all = Recollections.of(who, player);
		Recollections.Recollection best = null;
		for (Recollections.Recollection r : all) {
			Recollections.Kind k = r.type();
			if (k != null && kinds.contains(k) && !r.heard() && (best == null || later(r, best)) && open(all, r, answers)) {
				best = r;
			}
		}
		return best;
	}

	private static Recollections.@Nullable Recollection favour(LivingEntity who, ServerPlayer player) {
		return latest(who, player, FAVOURS, Set.of(Recollections.Kind.THANKED));
	}

	private static Recollections.@Nullable Recollection wrong(LivingEntity who, ServerPlayer player) {
		return latest(who, player, WRONGS, Set.of(Recollections.Kind.APOLOGISED, Recollections.Kind.SPURNED));
	}

	private static Recollections.@Nullable Recollection rough(LivingEntity who, ServerPlayer player) {
		return latest(who, player, ROUGH, Set.of(Recollections.Kind.AIRED));
	}

	private static Recollections.@Nullable Recollection helped(LivingEntity who, ServerPlayer player) {
		return latest(who, player, HELPED, Set.of(Recollections.Kind.ASKED_AFTER));
	}

	/**
	 * (0.7.380) Andy: "the talk options should always relate to the most recent interaction." Of "Thank them",
	 * "Apologise", "Clear the air" and "Ask how they are", the one MCA's menu shows: whichever answers the latest thing
	 * still to be taken up between you (null: none). The others wait until that's been seen to.
	 */
	public static @Nullable String current(ServerPlayer player, LivingEntity who) {
		String answer = null;
		Recollections.Recollection best = null;
		Recollections.Recollection[] open = {favour(who, player), wrong(who, player), rough(who, player), helped(who, player)};
		String[] answers = {THANK, APOLOGISE, AIR, ASK_AFTER};
		for (int i = 0; i < open.length; i++) {
			if (open[i] != null && (best == null || later(open[i], best))) {
				best = open[i];
				answer = answers[i];
			}
		}
		return answer;
	}

	/** "Thank them" shows: they did something for you you've not thanked them for. */
	public static boolean canThank(ServerPlayer player, LivingEntity who) {
		return favour(who, player) != null;
	}

	/** "Apologise" shows: you wronged them, and haven't said sorry for it. */
	public static boolean canApologise(ServerPlayer player, LivingEntity who) {
		return wrong(who, player) != null;
	}

	/** "Clear the air" shows: they were rough with you, and it hasn't been had out. */
	public static boolean canAir(ServerPlayer player, LivingEntity who) {
		return rough(who, player) != null;
	}

	/** "Ask how they are" shows: you did them a great good, and haven't asked after them since. */
	public static boolean canAskAfter(ServerPlayer player, LivingEntity who) {
		return helped(who, player) != null;
	}

	// ---- how they take it ----------------------------------------------------------------------------------------------

	/** Soft (sorry, warm), plain (dutiful), hard (curt), or greedy. */
	enum Tone {
		SOFT, PLAIN, HARD, GREEDY;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static Tone tone(LivingEntity who) {
		Temperaments.Temperament t = Temperaments.of(who);
		if (t == null) {
			return Tone.PLAIN;
		}
		return switch (t) {
			case KINDHEARTED, CHEERFUL, DEVOUT, TIMID -> Tone.SOFT;
			case PROUD, HOTHEADED, SPITEFUL -> Tone.HARD;
			case GREEDY -> Tone.GREEDY;
			default -> Tone.PLAIN;
		};
	}

	/** Greedy ones speak as hard ones where they've no lines of their own. */
	static Tone toneNoGreed(LivingEntity who) {
		Tone t = tone(who);
		return t == Tone.GREEDY ? Tone.HARD : t;
	}

	private static Component line(String key, RandomSource random, Object... args) {
		return Component.translatable(key + "." + random.nextInt(LINES), args);
	}

	private static void cheer(LivingEntity who) {
		if (who.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, who.getX(), who.getY() + who.getBbHeight() + 0.3, who.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
		}
	}

	/** {@code player} thanks {@code who}: what they say. */
	public static Component thank(ServerPlayer player, LivingEntity who) {
		Recollections.Recollection r = favour(who, player);
		Recollections.Kind k = r == null ? null : r.type();
		RandomSource random = who.getRandom();
		Component name = Recollections.nameTo(player, who);
		if (k == null) {
			return line("history.jaymoskingdoms.thank.nothing", random, name);
		}
		Recollections.remember(who, player, Recollections.Kind.THANKED, about(k));
		McaCompat.hearts(who, player, THANK_HEARTS);
		cheer(who);
		long late = Deeds.today(player.level().getServer()) - r.day();
		Tone t = tone(who);
		if (late >= LATE_DAYS) {
			return line("history.jaymoskingdoms.thank.late." + (t == Tone.SOFT || t == Tone.PLAIN ? "soft" : "hard"), random, name);
		}
		return line("history.jaymoskingdoms.thank." + k.key() + "." + t.key(), random, name);
	}

	/** Whether {@code who} takes {@code player}'s apology for {@code r}. */
	static boolean forgives(ServerPlayer player, LivingEntity who, Recollections.Recollection r) {
		Recollections.Kind k = r.type();
		if (k == null || k == Recollections.Kind.KILLED_KIN) {
			return false;
		}
		float chance = FORGIVE_BY_WEIGHT[Math.clamp(Math.abs(k.weight) - 1, 0, FORGIVE_BY_WEIGHT.length - 1)];
		Tone t = tone(who);
		chance += t == Tone.SOFT ? FORGIVE_SOFT : t == Tone.HARD ? FORGIVE_HARD : 0.0F;
		if (who instanceof Villager v) {
			chance += McaCompat.heartsOf(v, player) * FORGIVE_PER_HEART;
		}
		if (Recollections.refuses(who, player)) {
			chance += FORGIVE_GRUDGE;
		}
		return who.getRandom().nextFloat() < Math.clamp(chance, FORGIVE_LEAST, FORGIVE_MOST);
	}

	/** {@code player} says sorry to {@code who} for the latest wrong they remember: taken (and let go) or spurned. */
	public static Component apologise(ServerPlayer player, LivingEntity who) {
		Recollections.Recollection r = wrong(who, player);
		Recollections.Kind k = r == null ? null : r.type();
		RandomSource random = who.getRandom();
		Component name = Recollections.nameTo(player, who);
		if (k == null) {
			return line("history.jaymoskingdoms.apology.nothing", random, name);
		}
		Component what = about(k);
		if (k == Recollections.Kind.KILLED_KIN) {
			Recollections.remember(who, player, Recollections.Kind.SPURNED, what);
			return line("history.jaymoskingdoms.apology.kin", random, name, r.detail());
		}
		if (forgives(player, who, r)) {
			Recollections.forget(who, player, r);
			Recollections.remember(who, player, Recollections.Kind.APOLOGISED, what);
			McaCompat.hearts(who, player, APOLOGY_HEARTS);
			cheer(who);
			return line("history.jaymoskingdoms.apology.taken." + toneNoGreed(who).key(), random, name, what);
		}
		Recollections.remember(who, player, Recollections.Kind.SPURNED, what);
		return line("history.jaymoskingdoms.apology.spurned." + toneNoGreed(who).key(), random, name, what);
	}

	/** {@code player} brings up the shove, the search or the stop with {@code who}: sorry, the job, or no regrets. */
	public static Component air(ServerPlayer player, LivingEntity who) {
		Recollections.Recollection r = rough(who, player);
		Recollections.Kind k = r == null ? null : r.type();
		RandomSource random = who.getRandom();
		Component name = Recollections.nameTo(player, who);
		if (k == null) {
			return line("history.jaymoskingdoms.air.nothing", random, name);
		}
		Recollections.remember(who, player, Recollections.Kind.AIRED, about(k));
		Tone t = toneNoGreed(who);
		if (t == Tone.SOFT) {
			McaCompat.hearts(who, player, AIR_HEARTS);
			cheer(who);
		}
		return line("history.jaymoskingdoms.air." + k.key() + "." + t.key(), random, name);
	}

	/** {@code player} asks after {@code who}, whom they did a great good: how they are, and the first time maybe a keepsake. */
	public static Component askAfter(ServerPlayer player, LivingEntity who) {
		Recollections.Recollection r = helped(who, player);
		Recollections.Kind k = r == null ? null : r.type();
		RandomSource random = who.getRandom();
		Component name = Recollections.nameTo(player, who);
		if (k == null) {
			return line("history.jaymoskingdoms.ask_after.nothing", random, name);
		}
		boolean first = Recollections.of(who, player).stream().noneMatch(m -> m.type() == Recollections.Kind.ASKED_AFTER);
		Recollections.remember(who, player, Recollections.Kind.ASKED_AFTER, about(k));
		McaCompat.hearts(who, player, ASK_HEARTS);
		cheer(who);
		if (first && random.nextFloat() < KEEPSAKE_CHANCE) {
			ItemStack gift = switch (random.nextInt(3)) {
				case 0 -> new ItemStack(Items.EMERALD, 1 + random.nextInt(2));
				case 1 -> new ItemStack(Items.BREAD, 3);
				default -> new ItemStack(Items.COOKED_MUTTON, 2);
			};
			Component what = Component.literal(gift.getCount() + " ").append(gift.getHoverName());
			if (!player.getInventory().add(gift) && !gift.isEmpty()) {
				player.drop(gift, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			return line("history.jaymoskingdoms.ask_after.keepsake", random, name, what);
		}
		return line("history.jaymoskingdoms.ask_after." + k.key(), random, name);
	}

	/**
	 * (Introductions) Asked their name, one whose first dealing with {@code player} was a shove, a search, a stop, a
	 * blow, a rescue or the like says so; or null for a plain introduction. {@code name}: their own.
	 */
	static @Nullable Component introduction(ServerPlayer player, LivingEntity who, Component name) {
		List<Recollections.Recollection> all = Recollections.of(who, player);
		Recollections.Recollection first = all.stream().filter(r -> !r.heard()).findFirst().orElse(null);
		Recollections.Kind k = first == null ? null : first.type();
		if (k == null || !FIRST.contains(k)) {
			return null;
		}
		String key = "history.jaymoskingdoms.intro." + k.key();
		if (ROUGH.contains(k)) {
			key += "." + toneNoGreed(who).key();
		}
		return line(key, who.getRandom(), name, Recollections.nameTo(player, who));
	}
}
