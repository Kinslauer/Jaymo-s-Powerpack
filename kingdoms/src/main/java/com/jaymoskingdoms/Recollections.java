package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.109) What each of a settlement's people remembers of each player: the gift you gave them, the trades, the hug
 * they welcomed (or didn't), the lie you told them, the theft they saw, the day you saved their life or brought their
 * brother home from captivity. Held by the villager (or folk) themselves, under the player (a masked stranger under
 * the costume's stand-in, {@link Identities#standIn}), and kept for as long as it matters to them: small things for a
 * few weeks, a life saved or a kinsman murdered for good.
 * <p>
 * It works without any AI: a villager who remembers something they haven't yet said comes over and says it
 * ({@link Approaches}: a greeting about it, or gossip about another player), brings it up when you chat with them
 * (MCA), and one who bears you enough ill will won't chat, joke or be hugged at all ({@link #refuses}). Friends pass
 * the weightier things on to each other when they meet ({@link Neighbours}), so word of what you did gets round. With
 * MCA's villager chat AI switched on, all of it goes into the villager's prompt too ({@link ChatPrompts}).
 */
public final class Recollections {
	/**
	 * What happened. Each has a weight (how they feel about it, per time), the days they keep it (0: for good), whether
	 * they'd bring it up to you, whether it's worth passing on, and how it reads in the chat AI's prompt (%1$s the
	 * player, %2$s the detail, %3$s the count) and, passed on, as told by the one it happened to (%3$s them).
	 */
	enum Kind {
		GIFT(2, 40, true, false, "%1$s gave you a gift: %2$s.", ""),
		TRADE(1, 20, true, false, "%1$s traded with you (%3$s trades that day).", ""),
		FED(3, 45, true, true, "%1$s gave you food when you went hungry.", "%1$s gave %3$s food when they went hungry."),
		KIND(1, 20, true, false, "%1$s spoke kindly to you.", ""),
		STOOD(2, 60, true, false, "%1$s swore to stand with your realm in its war against %2$s.", ""),
		PLAYED(2, 30, true, false, "%1$s played with you.", ""),
		CHATTED(1, 20, false, false, "%1$s %2$s, and you enjoyed it.", ""),
		HUGGED(2, 30, true, false, "%1$s hugged you.", ""),
		KISSED(3, 60, true, true, "%1$s kissed you.", "%1$s kissed %3$s."),
		REBUFFED(-1, 15, true, false, "%1$s tried to %2$s, and you didn't welcome it.", ""),
		RUDE(-2, 30, true, false, "%1$s was rude to you.", ""),
		LIED(-3, 45, true, true, "%1$s lied to your face.", "%1$s lied to %3$s's face."),
		THREATENED(-4, 60, true, true, "%1$s threatened you.", "%1$s threatened %3$s."),
		CONFESSED(1, 30, true, false, "%1$s owned up to a wrong and paid the fine.", ""),
		BRIBED(1, 30, true, false, "%1$s slipped you a bribe to look the other way.", ""),
		BEAT_YOU(1, 30, true, false, "%1$s beat you in a bout.", ""),
		LOST_TO_YOU(0, 30, true, false, "You beat %1$s in a bout.", ""),
		STRUCK(-3, 30, true, true, "%1$s struck you.", "%1$s struck %3$s."),
		/** (0.7.121) {@link Retaliation}. */
		PAID_OFF(1, 30, true, false, "%1$s struck you, then paid you to let the fight go.", ""),
		CRIME(-3, 60, true, true, "You saw %1$s commit %2$s.", "%3$s saw %1$s commit %2$s."),
		ROBBED(-4, 60, true, true, "%1$s tried to pick your pocket.", "%1$s tried to pick %3$s's pocket."),
		KILLED_KIN(-12, 0, true, true, "%1$s killed %2$s, who was family to you.", "%1$s killed %2$s, family of %3$s."),
		SAVED(5, 0, true, true, "%1$s saved your life.", "%1$s saved %3$s's life."),
		/** (0.7.212) {@link Restoration}. */
		MENDED(2, 40, true, true, "%1$s healed your wounds with magic.", "%1$s healed %3$s's wounds with magic."),
		FREED(4, 0, true, true, "%1$s cut you free from your captors.", "%1$s cut %3$s free from captors."),
		HOME(6, 0, true, true, "%1$s brought you safely home after you were taken captive.", "%1$s brought %3$s safely home from captivity."),
		RETURNED_KIN(5, 0, true, true, "%1$s brought %2$s, who is family to you, safely home from captivity.",
			"%1$s brought %2$s, family of %3$s, home from captivity."),
		SAW_DEED(2, 30, true, false, "You saw %1$s %2$s.", ""),
		QUEST_DONE(3, 90, true, true, "%1$s did the task you asked of them: %2$s.", "%1$s did a task %3$s asked of them: %2$s."),
		QUEST_FAILED(-2, 45, true, false, "%1$s gave up the task you asked of them: %2$s.", ""),
		LETTER(1, 30, true, false, "%1$s brought you a letter from %2$s.", ""),
		TALKED(0, 30, false, false, "%1$s said to you: \"%2$s\"", ""),
		// (0.7.254) what they did to or for you, and how you took it up with them after ({@link SharedHistory})
		REVIVED_YOU(1, 60, true, false, "You knelt by %1$s when they lay downed and got them back on their feet.", ""),
		LED_YOU(0, 30, false, false, "You walked %1$s to your ruler for an audience.", ""),
		SHOVED_YOU(0, 30, false, false, "You shoved %1$s away (%3$s times that day).", ""),
		SEARCHED_YOU(0, 30, false, false, "You searched %1$s's pack.", ""),
		HALTED_YOU(0, 30, false, false, "You stopped %1$s in the street and asked their business.", ""),
		THANKED(1, 45, false, false, "%1$s thanked you for %2$s.", ""),
		APOLOGISED(1, 45, false, false, "%1$s said sorry for %2$s, and you forgave them.", ""),
		SPURNED(0, 45, false, false, "%1$s said sorry for %2$s, and you would not have it.", ""),
		AIRED(0, 30, false, false, "%1$s brought up %2$s with you, and you had it out.", ""),
		ASKED_AFTER(1, 30, false, false, "%1$s asked after you, after %2$s.", ""),
		// (0.7.380) what a player did for them while they lay downed or under attack ({@link Rescues})
		CARRIED(4, 0, true, true, "You lay downed and %1$s picked you up and carried you out of harm's way.",
			"%1$s carried %3$s out of harm's way when they lay downed."),
		GOT_UP(4, 90, true, true, "You lay downed and %1$s knelt by you and got you back on your feet.", "%1$s got %3$s back on their feet when they lay downed."),
		TENDED(2, 40, true, false, "%1$s dressed your wounds with their own hands.", ""),
		SHIELDED(3, 60, true, true, "%1$s drew %2$s off you when it came for you.", "%1$s drew %2$s off %3$s when it came for them."),
		THREW(-1, 20, true, false, "%1$s picked you up while you lay downed, then threw you down.", ""),
		// (0.7.424) held down on the execution hold and questioned ({@link Questioning}), and let go after
		QUESTIONED(-3, 60, true, true, "%1$s held you down with a weapon at you and made you talk.", "%1$s held %3$s down and made them talk."),
		SPARED(3, 60, true, true, "%1$s had you at their mercy and let you live.", "%1$s had %3$s at their mercy and let them live.");

		final int weight, days;
		final boolean remark, tellable;
		final String prompt, heardPrompt;

		Kind(int weight, int days, boolean remark, boolean tellable, String prompt, String heardPrompt) {
			this.weight = weight;
			this.days = days;
			this.remark = remark;
			this.tellable = tellable;
			this.prompt = prompt;
			this.heardPrompt = heardPrompt;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Kind parse(String key) {
			for (Kind k : values()) {
				if (k.key().equals(key)) {
					return k;
				}
			}
			return null;
		}
	}

	/** Lines a villager has for bringing up each kind of memory: their own, heard from someone, about another player. */
	static final int REMARKS = 3, HEARD_REMARKS = 2, ABOUT_REMARKS = 2;
	/** Memories one keeps of one player, and players one keeps memories of (the least weighty, and the longest unseen, go first). */
	static final int PER_PLAYER = 12, PLAYERS = 24;
	/** What the AI chat keeps of what you said to them: this many lines, each cut to this long. */
	static final int TALKED_KEPT = 3, TALKED_LENGTH = 100;
	/** A memory counts at most this many times over (ten trades in a day don't outweigh a theft). */
	static final int COUNT_CAP = 3;
	/** Trades in a day before they'd call you a regular. */
	static final int REGULAR_TRADES = 4;
	/** How they lean towards you (summed weights): too sour to chat or be hugged; sour enough not to come over friendly; warm. */
	static final int REFUSE = -10, COLD = -5, WARM = 6;
	/** The chance a gossip talks of what they know of another player rather than anything else. */
	static final float GOSSIP_CHANCE = 0.35F;
	/** The chance, when two friends meet, that one passes on something weighty they remember (each way). */
	static final float SHARE_CHANCE = 0.35F;
	/** (0.7.380) How long what last happened between you stays what they talk of first (ticks: ten minutes). */
	static final long FRESH_TICKS = 12000L;
	/** The chance, in an MCA chat, that they bring up something they remember and haven't yet said. */
	public static final float CHAT_REMARK_CHANCE = 0.5F;
	/** Ways a villager has of refusing to chat with someone they bear too much ill will. */
	public static final int REFUSALS = 3;
	/** How far off the family of someone killed, or brought home, is told of it (they live round there). */
	static final double KIN_RANGE = 64.0;
	/** The least weight (either way) that's worth passing on, or worth talking of to another player. */
	static final int TELL_WEIGHT = 3;

	/**
	 * One memory: what, its detail (an item, a task, a name), who told them (empty: they were there), the day, how many times,
	 * said yet, and (0.7.380) the game time it last happened (0: before that was kept), so the latest dealing can be told apart.
	 */
	public record Recollection(String kind, Component detail, String from, long day, int count, boolean told, long at) {
		public static final Codec<Recollection> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("kind").forGetter(Recollection::kind),
				ComponentSerialization.CODEC.optionalFieldOf("detail", Component.empty()).forGetter(Recollection::detail),
				Codec.STRING.optionalFieldOf("from", "").forGetter(Recollection::from),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(Recollection::day),
				Codec.INT.optionalFieldOf("count", 1).forGetter(Recollection::count),
				Codec.BOOL.optionalFieldOf("told", false).forGetter(Recollection::told),
				Codec.LONG.optionalFieldOf("at", 0L).forGetter(Recollection::at))
			.apply(i, Recollection::new));

		@Nullable Kind type() {
			return Kind.parse(kind);
		}

		boolean heard() {
			return !from.isEmpty();
		}

		Recollection withTold() {
			return new Recollection(kind, detail, from, day, count, true, at);
		}

		/** How much it sways them, counted over and halved for hearsay. */
		int sway() {
			Kind k = type();
			if (k == null) {
				return 0;
			}
			int w = k.weight * Math.min(count, COUNT_CAP);
			return heard() ? w / 2 : w;
		}
	}

	/** One player as they remember them: the name they knew them by, the last day they had to do with them, and what happened. */
	public record Person(String name, long last, List<Recollection> memories) {
		public static final Codec<Person> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("name", "").forGetter(Person::name),
				Codec.LONG.optionalFieldOf("last", 0L).forGetter(Person::last),
				Recollection.CODEC.listOf().optionalFieldOf("memories", List.of()).forGetter(Person::memories))
			.apply(i, Person::new));
	}

	/** On a villager (or folk): player (or stand-in) → what they remember of them. Saved. */
	static final AttachmentType<Map<UUID, Person>> MEMORIES = AttachmentRegistry.create(JaymosKingdoms.id("recollections"),
		builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Person.CODEC)));

	private Recollections() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!blocked && damage > 0.0F && source.getEntity() instanceof ServerPlayer player && rememberer(entity) && entity.isAlive()
				&& !com.jaymoskingdoms.forms.Duels.dueling(player, entity)) {
				remember(entity, player, Kind.STRUCK, Component.empty());
			}
		});
	}

	// ---- who remembers, and under what --------------------------------------------------------------------------------

	/** A settlement's people remember: villagers, and its folk (guards, kings, workers). */
	static boolean rememberer(LivingEntity e) {
		return e instanceof Villager || e instanceof Folk;
	}

	/** The id {@code who} knows {@code player} by: theirs, or the costume's while they're a masked stranger to them. */
	static UUID key(ServerPlayer player, LivingEntity who) {
		String mask = Identities.maskedAs(player);
		return mask != null && Identities.hiddenFrom(player, who) ? Identities.standIn(mask) : player.getUUID();
	}

	/** The name {@code who} knows {@code player} by. */
	public static Component nameTo(ServerPlayer player, LivingEntity who) {
		return Identities.hiddenFrom(player, who) ? Identities.shownName(player) : Identities.trueName(player);
	}

	private static long today(LivingEntity e) {
		return Deeds.today(e.level().getServer());
	}

	// ---- remembering ----------------------------------------------------------------------------------------------------

	/** {@code who} remembers {@code player} doing {@code kind} ({@code detail}: what, if it matters). */
	static void remember(LivingEntity who, ServerPlayer player, Kind kind, Component detail) {
		remember(who, key(player, who), nameTo(player, who).getString(), kind, detail, "");
	}

	private static void remember(LivingEntity who, UUID id, String name, Kind kind, Component detail, String from) {
		if (!rememberer(who) || who.level().isClientSide() || who.level().getServer() == null) {
			return;
		}
		long day = today(who);
		long now = who.level().getGameTime();
		Map<UUID, Person> all = new HashMap<>(who.getAttachedOrElse(MEMORIES, Map.of()));
		Person had = all.get(id);
		List<Recollection> list = new ArrayList<>(had == null ? List.of() : had.memories());
		list.removeIf(r -> forgotten(r, day));
		String detailText = detail.getString();
		boolean merged = false;
		for (int i = 0; i < list.size(); i++) {
			Recollection r = list.get(i);
			if (r.kind().equals(kind.key()) && r.day() == day && r.from().equals(from) && (kind == Kind.TRADE || kind == Kind.STRUCK
				|| r.detail().getString().equals(detailText))) {
				// (the same again that day: it counts the more, and is news again)
				list.set(i, new Recollection(r.kind(), detail, from, day, r.count() + 1, false, now));
				merged = true;
				break;
			}
		}
		if (!merged) {
			if (kind == Kind.TALKED) {
				// (only the last few things said to them)
				List<Recollection> said = list.stream().filter(r -> r.kind().equals(kind.key())).toList();
				for (int i = 0; i <= said.size() - TALKED_KEPT; i++) {
					list.remove(said.get(i));
				}
			}
			list.add(new Recollection(kind.key(), detail, from, day, 1, false, now));
		}
		while (list.size() > PER_PLAYER) {
			// (the least weighty, the oldest first; what's kept for good is kept)
			list.remove(list.stream().min(Comparator.comparingLong(r -> keep(r, day))).orElseThrow());
		}
		all.put(id, new Person(name.isEmpty() && had != null ? had.name() : name, day, List.copyOf(list)));
		while (all.size() > PLAYERS) {
			UUID oldest = all.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().last())).orElseThrow().getKey();
			all.remove(oldest);
		}
		who.setAttached(MEMORIES, Map.copyOf(all));
	}

	/** How much a memory is worth keeping, for the one to forget when there are too many. */
	private static long keep(Recollection r, long today) {
		Kind k = r.type();
		if (k == null) {
			return Long.MIN_VALUE;
		}
		return (k.days == 0 ? 1000L : 0L) + Math.abs(r.sway()) * 10L - (today - r.day());
	}

	private static boolean forgotten(Recollection r, long today) {
		Kind k = r.type();
		return k == null || k.days > 0 && today - r.day() > k.days;
	}

	/** What {@code who} remembers of {@code player} still (oldest first). */
	static List<Recollection> of(LivingEntity who, ServerPlayer player) {
		Person p = who.getAttachedOrElse(MEMORIES, Map.of()).get(key(player, who));
		if (p == null) {
			return List.of();
		}
		long day = today(who);
		return p.memories().stream().filter(r -> !forgotten(r, day)).toList();
	}

	/** How {@code who} leans towards {@code player}, all they remember summed (0: nothing, or nothing either way). */
	static int leaning(LivingEntity who, ServerPlayer player) {
		int sum = 0;
		for (Recollection r : of(who, player)) {
			sum += r.sway();
		}
		return sum;
	}

	/** {@code who} bears {@code player} too much ill will to chat, joke, be told a story or be hugged. */
	public static boolean refuses(LivingEntity who, ServerPlayer player) {
		return leaning(who, player) <= REFUSE;
	}

	/** (0.7.254) {@code who} lets go of {@code r} about {@code player} (an apology taken: {@link SharedHistory}). */
	static void forget(LivingEntity who, ServerPlayer player, Recollection r) {
		Map<UUID, Person> all = new HashMap<>(who.getAttachedOrElse(MEMORIES, Map.of()));
		UUID id = key(player, who);
		Person p = all.get(id);
		if (p == null) {
			return;
		}
		List<Recollection> list = new ArrayList<>(p.memories());
		if (list.remove(r)) {
			all.put(id, new Person(p.name(), p.last(), List.copyOf(list)));
			who.setAttached(MEMORIES, Map.copyOf(all));
		}
	}

	private static void markTold(LivingEntity who, ServerPlayer player, Recollection told) {
		Map<UUID, Person> all = new HashMap<>(who.getAttachedOrElse(MEMORIES, Map.of()));
		UUID id = key(player, who);
		Person p = all.get(id);
		if (p == null) {
			return;
		}
		List<Recollection> list = new ArrayList<>(p.memories());
		int i = list.indexOf(told);
		if (i >= 0) {
			list.set(i, told.withTold());
			all.put(id, new Person(p.name(), p.last(), List.copyOf(list)));
			who.setAttached(MEMORIES, Map.copyOf(all));
		}
	}

	// ---- saying it (no AI) ----------------------------------------------------------------------------------------------

	/** {@code r} is something they'd bring up, and haven't yet. */
	private static boolean sayable(Recollection r) {
		Kind k = r.type();
		return k != null && !r.told() && k.remark && (!r.heard() || k.tellable) && (k != Kind.TRADE || r.count() >= REGULAR_TRADES);
	}

	/** (0.7.380) {@code r} happened in the last {@link #FRESH_TICKS}. */
	private static boolean fresh(LivingEntity who, Recollection r) {
		return r.at() > 0L && who.level().getGameTime() - r.at() <= FRESH_TICKS;
	}

	/**
	 * (0.7.380) What {@code who} last had to do with {@code player} themselves (not hearsay), if it was in the last
	 * {@link #FRESH_TICKS}; or null. Andy: "the talk options should always relate to the most recent interaction."
	 */
	static @Nullable Recollection latest(LivingEntity who, ServerPlayer player) {
		Recollection best = null;
		for (Recollection r : of(who, player)) {
			if (!r.heard() && fresh(who, r) && (best == null || r.at() >= best.at())) {
				best = r;
			}
		}
		return best;
	}

	/**
	 * The memory of {@code player} {@code who} would bring up next: (0.7.380) what just happened between them, if it's worth
	 * a word and still unsaid; else the weightiest not yet said, the latest of those; or null.
	 */
	static @Nullable Recollection unsaid(LivingEntity who, ServerPlayer player) {
		Recollection last = latest(who, player);
		if (last != null && sayable(last)) {
			return last;
		}
		Recollection best = null;
		for (Recollection r : of(who, player)) {
			if (!sayable(r)) {
				continue;
			}
			if (best == null || Math.abs(r.sway()) > Math.abs(best.sway()) || Math.abs(r.sway()) == Math.abs(best.sway()) && r.day() >= best.day()) {
				best = r;
			}
		}
		return best;
	}

	/**
	 * (0.7.380) MCA's menu opening on {@code who}: what just happened between you, said and now told, if it was the last thing
	 * between you, is worth a word and hasn't been said; or null for MCA's own greeting.
	 */
	public static @Nullable Component opening(LivingEntity who, ServerPlayer player, RandomSource random) {
		Recollection last = latest(who, player);
		if (last == null || !sayable(last)) {
			return null;
		}
		markTold(who, player, last);
		return line(last, nameTo(player, who), random);
	}

	/** {@code who} has something about {@code player} they've not yet said. */
	static boolean hasRemark(LivingEntity who, ServerPlayer player) {
		return unsaid(who, player) != null;
	}

	/** What {@code who} says to {@code player} about the next thing they remember, now said; or null if nothing. */
	public static @Nullable Component remark(LivingEntity who, ServerPlayer player, RandomSource random) {
		Recollection r = unsaid(who, player);
		if (r == null) {
			return null;
		}
		markTold(who, player, r);
		return line(r, nameTo(player, who), random);
	}

	private static Component line(Recollection r, Component name, RandomSource random) {
		if (r.heard()) {
			return Component.translatable("recall.jaymoskingdoms.heard." + r.kind() + "." + random.nextInt(HEARD_REMARKS), name, r.detail(), r.from());
		}
		return Component.translatable("recall.jaymoskingdoms." + r.kind() + "." + random.nextInt(REMARKS), name, r.detail(), r.count());
	}

	/** (Approaches) A gossip's talk of something they themselves saw another player do; or null. */
	static @Nullable Component gossip(LivingEntity who, ServerPlayer listener, RandomSource random) {
		if (random.nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		UUID self = key(listener, who);
		long day = today(who);
		List<Component> said = new ArrayList<>();
		for (Map.Entry<UUID, Person> e : who.getAttachedOrElse(MEMORIES, Map.of()).entrySet()) {
			if (e.getKey().equals(self) || e.getValue().name().isEmpty()) {
				continue;
			}
			for (Recollection r : e.getValue().memories()) {
				Kind k = r.type();
				if (k != null && k.tellable && !r.heard() && !forgotten(r, day) && Math.abs(k.weight) >= TELL_WEIGHT) {
					said.add(Component.translatable("recall.jaymoskingdoms.about." + r.kind() + "." + random.nextInt(ABOUT_REMARKS),
						e.getValue().name(), r.detail()));
				}
			}
		}
		return said.isEmpty() ? null : said.get(random.nextInt(said.size()));
	}

	/** (Neighbours) Two friends met: {@code teller} may pass on to {@code listener} one weighty thing they saw a player do. */
	static void share(Villager teller, Villager listener, RandomSource random) {
		if (random.nextFloat() >= SHARE_CHANCE) {
			return;
		}
		long day = today(teller);
		List<Map.Entry<UUID, Recollection>> worth = new ArrayList<>();
		Map<UUID, Person> theirs = listener.getAttachedOrElse(MEMORIES, Map.of());
		for (Map.Entry<UUID, Person> e : teller.getAttachedOrElse(MEMORIES, Map.of()).entrySet()) {
			for (Recollection r : e.getValue().memories()) {
				Kind k = r.type();
				if (k == null || !k.tellable || r.heard() || forgotten(r, day) || Math.abs(k.weight) < TELL_WEIGHT) {
					continue;
				}
				Person known = theirs.get(e.getKey());
				String detail = r.detail().getString();
				if (known != null && known.memories().stream().anyMatch(m -> m.kind().equals(r.kind()) && m.detail().getString().equals(detail))) {
					// (they know of it already)
					continue;
				}
				worth.add(Map.entry(e.getKey(), r));
			}
		}
		if (worth.isEmpty()) {
			return;
		}
		Map.Entry<UUID, Recollection> told = worth.get(random.nextInt(worth.size()));
		Person about = teller.getAttachedOrElse(MEMORIES, Map.of()).get(told.getKey());
		Kind kind = told.getValue().type();
		if (about != null && kind != null) {
			remember(listener, told.getKey(), about.name(), kind, told.getValue().detail(), teller.getName().getString());
		}
	}

	// ---- the hooks ----------------------------------------------------------------------------------------------------

	/** (Approaches) {@code who} gave {@code player} the reply {@code key} (talk.jaymoskingdoms.<topic>[.n].reply.<outcome>[.temper]): what they make of it. */
	static void replied(LivingEntity who, @Nullable ServerPlayer player, String key, @Nullable String enemy) {
		if (player == null) {
			return;
		}
		String[] parts = key.split("\\.");
		if (parts.length < 3) {
			return;
		}
		String topic = parts[2];
		String outcome = "";
		for (int i = 3; i < parts.length - 1; i++) {
			if (parts[i].equals("reply")) {
				outcome = parts[i + 1];
				break;
			}
		}
		if (parts.length == 4 && parts[3].equals("ignored")) {
			outcome = "ignored";
		}
		Kind kind = switch (topic + "/" + outcome) {
			case "accuse/confess" -> Kind.CONFESSED;
			case "accuse/liar" -> Kind.LIED;
			case "accuse/threatened" -> Kind.THREATENED;
			case "question/warm", "loyalty/praise" -> Kind.KIND;
			case "halt/rude", "question/rude", "bout/insult", "loyalty/ill", "halt/ignored", "accuse/ignored", "unmask/refuse" -> Kind.RUDE;
			case "hungry/fed" -> Kind.FED;
			case "bargain/sold" -> Kind.TRADE;
			case "war/stand" -> Kind.STOOD;
			case "search/bribe" -> key.endsWith(".tyrant") || key.endsWith(".reveler") || key.endsWith(".plain") ? Kind.BRIBED : Kind.RUDE;
			case "bout/won" -> Kind.LOST_TO_YOU;
			case "bout/lost" -> Kind.BEAT_YOU;
			default -> topic.equals("child") && parts.length == 5 && parts[4].equals("reply") ? Kind.PLAYED : null;
		};
		if (kind != null) {
			remember(who, player, kind, kind == Kind.STOOD && enemy != null ? Component.literal(enemy) : Component.empty());
		}
		// (0.7.254) and what they did to you, for when you're introduced or clear the air ({@link SharedHistory})
		Kind rough = switch (topic + "/" + outcome) {
			case "search/clean", "search/found", "unmask/search_clean", "unmask/search_found" -> Kind.SEARCHED_YOU;
			default -> topic.equals("halt") && !outcome.isEmpty() ? Kind.HALTED_YOU : null;
		};
		if (rough != null) {
			remember(who, player, rough, Component.empty());
		}
	}

	/** (MCA) {@code player}'s chat, joke, story, hug, flirt or kiss went down well ({@code hearts} up) or badly with {@code who}. */
	public static void socialised(LivingEntity who, ServerPlayer player, String act, int hearts) {
		if (hearts == 0) {
			return;
		}
		if (hearts < 0) {
			remember(who, player, Kind.REBUFFED, Component.translatable("recall.jaymoskingdoms.act." + act));
		} else if (act.equals("hug")) {
			remember(who, player, Kind.HUGGED, Component.empty());
		} else if (act.equals("kiss")) {
			remember(who, player, Kind.KISSED, Component.empty());
		} else {
			remember(who, player, Kind.CHATTED, Component.translatable("recall.jaymoskingdoms.did." + act));
		}
	}

	/** (MCA) {@code player} gave {@code who} {@code gift}, and it was {@code liked} (hearts up). */
	public static void gifted(LivingEntity who, ServerPlayer player, ItemStack gift, boolean liked) {
		if (liked && !gift.isEmpty()) {
			remember(who, player, Kind.GIFT, gift.getHoverName());
		} else if (!gift.isEmpty()) {
			remember(who, player, Kind.REBUFFED, Component.translatable("recall.jaymoskingdoms.act.gift"));
		}
	}

	/** (trades) {@code player} traded with {@code v}: a customer (often enough, a regular). */
	public static void trade(Villager v, ServerPlayer player) {
		remember(v, player, Kind.TRADE, Component.empty());
	}

	/** (Theft) {@code seen} saw {@code player} commit a crime ({@code cry}: rummage, theft, arson, murder...). */
	static void crime(ServerPlayer player, List<LivingEntity> seen, String cry) {
		long day = Deeds.today(player.level().getServer());
		for (LivingEntity w : seen) {
			if (cry.equals("pickpocket") && of(w, player).stream().anyMatch(r -> r.kind().equals(Kind.ROBBED.key()) && r.day() == day)) {
				// (the mark remembers it as their own pocket picked)
				continue;
			}
			if (rememberer(w)) {
				remember(w, player, Kind.CRIME, Component.translatable("recall.jaymoskingdoms.crime." + cry));
			}
		}
	}

	/** (Theft) {@code player} was seen killing {@code victim}: their family round about won't forget it. */
	static void murdered(ServerLevel level, ServerPlayer player, LivingEntity victim) {
		if (!(victim instanceof Villager dead)) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, victim.getBoundingBox().inflate(KIN_RANGE), v -> v.isAlive() && v != victim)) {
			if (McaCompat.kin(v, dead) != McaCompat.STRANGERS) {
				remember(v, player, Kind.KILLED_KIN, dead.getName().copy());
			}
		}
	}

	/** (Captives) {@code player} brought {@code captive} home: they, and their family round about, remember it always. */
	static void broughtHome(ServerLevel level, ServerPlayer player, Villager captive) {
		remember(captive, player, Kind.HOME, Component.empty());
		for (Villager v : level.getEntitiesOfClass(Villager.class, captive.getBoundingBox().inflate(KIN_RANGE), v -> v.isAlive() && v != captive)) {
			if (McaCompat.kin(v, captive) != McaCompat.STRANGERS) {
				remember(v, player, Kind.RETURNED_KIN, captive.getName().copy());
			}
		}
	}

	/** (MCA chat AI) {@code player} said {@code msg} to {@code who}: they'll remember the gist. */
	public static void talked(LivingEntity who, ServerPlayer player, String msg) {
		String said = msg.strip();
		if (said.isEmpty()) {
			return;
		}
		if (said.length() > TALKED_LENGTH) {
			said = said.substring(0, TALKED_LENGTH - 3) + "...";
		}
		remember(who, player, Kind.TALKED, Component.literal(said));
	}

	// ---- for the chat AI --------------------------------------------------------------------------------------------------

	/** What {@code who} remembers of {@code player}, as lines for the chat AI's prompt (empty: nothing). */
	static List<String> promptLines(LivingEntity who, ServerPlayer player) {
		long day = today(who);
		String name = nameTo(player, who).getString();
		List<String> out = new ArrayList<>();
		for (Recollection r : of(who, player)) {
			Kind k = r.type();
			if (k == null) {
				continue;
			}
			String when = ago(day - r.day());
			String detail = r.detail().getString();
			String what = r.heard() && !k.heardPrompt.isEmpty() ? r.from() + " told you: " + String.format(Locale.ROOT, k.heardPrompt, name, detail, r.from())
				: r.heard() ? r.from() + " told you: " + String.format(Locale.ROOT, k.prompt, name, detail, r.count())
				: String.format(Locale.ROOT, k.prompt, name, detail, r.count());
			out.add("(" + when + ") " + what + (!r.heard() && r.count() > 1 && k != Kind.TRADE ? " (" + r.count() + " times)" : ""));
		}
		return out;
	}

	private static String ago(long days) {
		return days <= 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
	}

	// ---- the test command ------------------------------------------------------------------------------------------------

	/** {@code /kingdom memories} (ops): what the villager or folk you're looking at (or the nearest) remembers of you. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("memories").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(Recollections::show);
	}

	private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		LivingEntity who = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8.0), Recollections::rememberer).stream()
			.min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
		if (who == null) {
			c.getSource().sendFailure(Component.translatable("recall.jaymoskingdoms.command.nobody"));
			return 0;
		}
		List<String> lines = promptLines(who, player);
		c.getSource().sendSuccess(() -> Component.translatable("recall.jaymoskingdoms.command.head", who.getDisplayName(), lines.size(), leaning(who, player))
			.withStyle(ChatFormatting.GOLD), false);
		for (String line : lines) {
			c.getSource().sendSuccess(() -> Component.literal(" - " + line).withStyle(ChatFormatting.GRAY), false);
		}
		return lines.size();
	}
}
