package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * (0.5.29) One quest: what a villager or a king asks of a player, what it takes, and what it pays. Offered by the giver
 * (kept on them until someone takes it), then carried in the player's log ({@link Quests#LOG}) until it's turned in or
 * given up. Immutable: progress and the like go through the {@code with...} methods.
 *
 * <p>{@code template} names its words in the lang file ({@code quest.jaymoskingdoms.<template>.title|text}).
 */
public record Quest(String id, int kind, String template, Giver giver, Objective objective, Reward reward, long givenAt) {
	/** Kinds: carry a letter, slay so many, bring back something from a ruin, bring so many of an item, slay a royal foe, slay a camp's chief. */
	public static final int DELIVER = 0, SLAY = 1, FETCH = 2, GATHER = 3, CHAMPION = 4, CHIEF = 5, REGICIDE = 6, WARBAND = 7;
	/**
	 * (0.5.47) Horse quests ({@link Stables}): bring a horse you broke in yourself; bring a horse of a breed at least so
	 * good (target: the breed, count: the quality in percent); ride to another town in time (target {@code faction:<id>},
	 * pos its seat, otherName the seconds allowed).
	 */
	public static final int BREAK_IN = 8, STUD = 9, RACE = 10;
	/**
	 * (0.5.56) Beast quests ({@link Sightings}): slay the monster of legend seen near pos (target: its entity id, other: it
	 * once it's come for you, otherName: its name); last a night out near pos where it was seen (spawned: the night's
	 * begun).
	 */
	public static final int HUNT = 11, SURVIVE = 12;
	/**
	 * (0.7.21) A villager's grudge ({@link Grudges}): give their enemy a beating (count: blows), or lure them to where the
	 * giver lies in wait (pos). other: the enemy, otherName: their name.
	 */
	public static final int GRUDGE = 13;
	/**
	 * (0.7.42) Sailing ({@link Navies}): see a merchantman from the giver's harbour safely into another realm's (target
	 * {@code faction:<id>}, pos its mooring, place its name; spawned: she's sailed, other: her).
	 */
	public static final int ESCORT = 14;
	/**
	 * (0.7.31) A settlement struck by a natural disaster asks for its rubble cleared ({@link DisasterRelief}): break
	 * count blocks of stone, earth, sand, drifts or wreckage near pos (its seat). place: the settlement.
	 */
	public static final int RELIEF = 15;

	/** Who asked: their UUID and name, their settlement, whether it's its king, and their trade (a profession's path). */
	public record Giver(UUID uuid, String name, int faction, boolean king, String job) {
		public static final Codec<Giver> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(Giver::uuid),
				Codec.STRING.fieldOf("name").forGetter(Giver::name),
				Codec.INT.fieldOf("faction").forGetter(Giver::faction),
				Codec.BOOL.optionalFieldOf("king", false).forGetter(Giver::king),
				Codec.STRING.optionalFieldOf("job", "").forGetter(Giver::job))
			.apply(i, Giver::new));
	}

	/**
	 * What it takes. {@code target}: an entity type id, {@code #tag} or {@code *monster} (slay); an item id (gather, fetch:
	 * the item it's made of); a structure id (fetch, champion: where); {@code camp:<id>} (chief); {@code leader:<id>} or
	 * empty (deliver: a settlement's ruler, or {@code other}). {@code pos}: where it's to be done. {@code other}: the
	 * letter's addressee, the champion. {@code place}: the name shown for where (a settlement, a ruin). {@code spawned}:
	 * the cache or the champion is out in the world.
	 */
	public record Objective(String target, int count, int progress, Optional<BlockPos> pos, Optional<UUID> other, String otherName,
		String place, boolean spawned) {
		public static final Codec<Objective> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("target").forGetter(Objective::target),
				Codec.INT.optionalFieldOf("count", 1).forGetter(Objective::count),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(Objective::progress),
				BlockPos.CODEC.optionalFieldOf("pos").forGetter(Objective::pos),
				UUIDUtil.CODEC.optionalFieldOf("other").forGetter(Objective::other),
				Codec.STRING.optionalFieldOf("other_name", "").forGetter(Objective::otherName),
				Codec.STRING.optionalFieldOf("place", "").forGetter(Objective::place),
				Codec.BOOL.optionalFieldOf("spawned", false).forGetter(Objective::spawned))
			.apply(i, Objective::new));
	}

	/**
	 * What it pays: emeralds; items as {@code id*count}; an enchanted book as {@code enchantment id@level}; a relic as
	 * {@code passive@base item id} ({@link Relics}); MCA hearts with the giver; reputation with their settlement; standing
	 * (for its members).
	 */
	public record Reward(int emeralds, List<String> items, Optional<String> book, Optional<String> relic, int hearts, int reputation, int standing) {
		public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("emeralds", 0).forGetter(Reward::emeralds),
				Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Reward::items),
				Codec.STRING.optionalFieldOf("book").forGetter(Reward::book),
				Codec.STRING.optionalFieldOf("relic").forGetter(Reward::relic),
				Codec.INT.optionalFieldOf("hearts", 0).forGetter(Reward::hearts),
				Codec.INT.optionalFieldOf("reputation", 0).forGetter(Reward::reputation),
				Codec.INT.optionalFieldOf("standing", 0).forGetter(Reward::standing))
			.apply(i, Reward::new));
	}

	public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Quest::id),
			Codec.INT.fieldOf("kind").forGetter(Quest::kind),
			Codec.STRING.fieldOf("template").forGetter(Quest::template),
			Giver.CODEC.fieldOf("giver").forGetter(Quest::giver),
			Objective.CODEC.fieldOf("objective").forGetter(Quest::objective),
			Reward.CODEC.fieldOf("reward").forGetter(Quest::reward),
			Codec.LONG.optionalFieldOf("given_at", 0L).forGetter(Quest::givenAt))
		.apply(i, Quest::new));

	public Quest withObjective(Objective objective) {
		return new Quest(id, kind, template, giver, objective, reward, givenAt);
	}

	public Quest withProgress(int progress) {
		Objective o = objective;
		return withObjective(new Objective(o.target, o.count, Math.min(progress, o.count), o.pos, o.other, o.otherName, o.place, o.spawned));
	}

	public Quest withSpawned(Optional<UUID> other) {
		Objective o = objective;
		return withObjective(new Objective(o.target, o.count, o.progress, o.pos, other.isPresent() ? other : o.other, o.otherName, o.place, true));
	}

	public Quest withReward(Reward reward) {
		return new Quest(id, kind, template, giver, objective, reward, givenAt);
	}

	public Quest withGivenAt(long at) {
		return new Quest(id, kind, template, giver, objective, reward, at);
	}

	/** Its objective's done by the count kept (slay, deliver, champion, chief); gather and fetch are checked against the pack. */
	public boolean counted() {
		return objective.progress >= objective.count;
	}

	/** A royal quest (from a king). */
	public boolean royal() {
		return giver.king;
	}
}
