package com.jaymoskingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The ways a tale ({@link Tales}) reads the world it's told in (docs/StoryQuests-Plan.md, section 7): the hour and the
 * moon, who the player is (blood, patron, standing, dress, purse), days that run out, a word that may or may not sway
 * its listener, naming who did it, a night's watch, what a beast is weak to, what a companion makes of a choice, a
 * letter in a tongue the player can't read, a lie that comes out, and what an ending brings weeks later.
 */
final class TaleWays {
	// ================================================================================================================
	// Tuning
	/** Armour this heavy is "armoured"; a worn piece this far gone is rags. */
	static final int ARMOURED = 10;
	static final float RAGGED_WEAR = 0.8F;
	/** What counts as fine dress, and as rags whatever its wear (data/jaymoskingdoms/tags/item/fine_dress.json, ragged_dress.json). */
	static final TagKey<Item> FINE = TagKey.create(Registries.ITEM, JaymosKingdoms.id("fine_dress"));
	static final TagKey<Item> RAGS = TagKey.create(Registries.ITEM, JaymosKingdoms.id("ragged_dress"));
	/**
	 * A try's odds (in a hundred): where they start, what each step of a tale's {@code odds} is worth, what each thing
	 * that helps or hurts is worth, and their bounds. Hearts, standing and what the listener remembers each sway them by
	 * one for every so many, up to {@link #SWAY_MOST}.
	 */
	static final int ODDS = 50, ODDS_STEP = 15, HELP = 15, ODDS_LEAST = 5, ODDS_MOST = 95;
	static final int HEARTS_PER = 5, STANDING_PER = 20, LEAN_TIMES = 3, SWAY_MOST = 15;
	/** A threat from someone in armour, with blood on them, or known to have cut down one who yielded. */
	static final int THREAT_ARMOUR = 10, THREAT_BLOOD = 5, THREAT_DEED = 10;
	/** How each temper takes a threat: the timid give way, the brave and the hot-headed dig in. */
	static final int THREAT_TIMID = 25, THREAT_GIVES = 5, THREAT_BRAVE = -25, THREAT_HOTHEAD = -15, THREAT_PROUD = -10;
	/** How each temper takes a bribe: the greedy take it, the dutiful and the devout are put out by it. */
	static final int BRIBE_TAKES = 30, BRIBE_MAYBE = 5, BRIBE_DUTIFUL = -15, BRIBE_DEVOUT = -20;
	/** How each temper takes being talked round. */
	static final int TALK_KIND = 15, TALK_CHEERFUL = 10, TALK_GLOOMY = -5, TALK_PROUD = -10, TALK_SUSPICIOUS = -15;
	/** The odds (in a hundred) at or above which a try is said in words as likely, fair, or long: below the last, poor. */
	static final int WORDS_LIKELY = 75, WORDS_FAIR = 50, WORDS_LONG = 25;
	/** What the greedy and the proud ask of a bribe, as a share of its price. */
	static final double BRIBE_GREEDY = 1.5, BRIBE_PROUD = 2.0;
	/** What agreeing or not with a companion's belief moves their bond by. */
	static final int VOICE_BOND = 3;
	/** What a priest or a librarian takes to read something aloud (coins); how often they talk of it after. */
	static final int READ_COINS = 1;
	static final float READER_TALKS = 0.5F;
	/** What each thing known of a beast leaves of its health and its blow, and the least they can be brought to. */
	static final double WEAK_SHARE = 0.7, WEAK_LEAST = 0.35;
	/** A foe this near water that runs (blocks) is by the water, for what's {@code weak_to} it. */
	static final int WATER_NEAR = 2;
	/** A watch is kept this near its place (blocks), a line every so many seconds. */
	static final int VIGIL_RADIUS = 12, VIGIL_GAP = 10;
	/** How many times a watch's radius away walking off counts as leaving it. */
	static final double VIGIL_LEFT = 3.0;
	/** A tale's person is met once a player has stood this near; one whose hours are over goes once nobody is this near. */
	static final double MEET_RANGE = 8.0, HOURS_GONE = 14.0;
	/** Someone who moves to a town does so when the player is next this near its seat; what's owed later is dropped after this many days unpaid. */
	static final int SETTLE_NEAR = 96, LATER_KEPT = 60;
	/** The days an ending's later things come after, and a lie's, where a tale leaves them out. */
	static final int LATER_DAYS = 10, LIE_DAYS = 7;
	/** What of a later thing waits apart until it can be done: a letter, a grave, someone moving away. */
	static final List<String> WAITS = List.of("letter", "grave", "leaves");
	/** Lines a companion has for each belief before a choice, and for agreeing and not after it. */
	static final int VOICE_LINES = 2;

	static final String BY = "~by:", MET = "~met:", TRIED = "~tried:";
	static final String KEY_UNREAD = "jaymoskingdoms_tale_unread";
	/** How far round the tavern's counter a window sill is looked for (blocks, across), and the run var of a candle still to be lit. */
	static final int CANDLE_REACH = 7;
	static final String CANDLE = "~candle";
	/**
	 * Following someone: how near is too near (blocks; half that crouched), how far is losing them, how many seconds
	 * out of reach before they're lost, and how near their journey's end counts as arrived.
	 */
	static final int TAIL_NEAR = 6, TAIL_FAR = 28, TAIL_LOST = 12, TAIL_ARRIVE = TaleCast.KEEP_WITHIN + 1;
	/** Seconds after they set off before standing close counts as being seen (whoever is beside them as they go isn't followed yet). */
	static final int TAIL_GRACE = 5;
	/** Crouched, someone can come this much of {@code near} closer before they're seen. */
	static final double TAIL_CROUCHED = 0.5;
	/**
	 * A field to cut: how far it's sown round its place (blocks each way), how near the player comes before it stands
	 * there, how far from its place a cut still counts, and the most the ground may rise or fall across it.
	 */
	static final int REAP_REACH = 4, REAP_NEAR = 40, REAP_RADIUS = 10, REAP_SLOPE = 3;
	/** The run var of a field already sown (so it's sown once). */
	static final String SOWN = "~sown:";
	/** Set in a run's scratch as its step is left undone (its days ran out, a watch was walked out on, a job failed). */
	static final String UNDONE = "undone";
	private static final Pattern COMPARE = Pattern.compile("^(reputation|hearts|coins|since:[a-z0-9_]+)(>=|<)(-?\\d+)$");

	/**
	 * Something an ending (or a lie) brings later: on {@code day}, in the settlement {@code faction}, {@code what} (a
	 * letter, town talk, a scene, someone who moves there, a mark, a lie found out), its words already filled in.
	 */
	record Later(long day, int faction, String what) {
		static final Codec<Later> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("day").forGetter(Later::day),
				Codec.INT.optionalFieldOf("faction", -1).forGetter(Later::faction),
				Codec.STRING.fieldOf("what").forGetter(Later::what))
			.apply(i, Later::new));
	}

	private TaleWays() {
	}

	// ---- conditions ------------------------------------------------------------------------------------------------------

	/** Whether {@code c} (one of this class's conditions, without its {@code !}) holds; null if it isn't one of them. */
	static @Nullable Boolean holds(ServerPlayer player, Tales.@Nullable Run run, String c) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Matcher m = COMPARE.matcher(c);
		if (m.matches()) {
			long n = Long.parseLong(m.group(3));
			Long have = amount(level, player, run, m.group(1), n);
			return have != null && (m.group(2).equals(">=") ? have >= n : have < n);
		}
		if (c.startsWith("exists:")) {
			// (a thing another of the pack's mods makes: a path that needs it is only offered where it can be made)
			Identifier id = Identifier.tryParse(c.substring(7));
			return id != null && BuiltInRegistries.ITEM.containsKey(id);
		}
		ServerLevel world = Factions.level(level.getServer());
		if (c.equals("night")) {
			return Approaches.night(world);
		}
		if (c.equals("day")) {
			return !Approaches.night(world);
		}
		if (c.equals("moon:full")) {
			return Lycans.fullMoon(world, world.getOverworldClockTime());
		}
		if (c.equals("moon:new")) {
			return world.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.NEW_MOON;
		}
		if (c.equals("moon:blood")) {
			return Moons.blood(level.getServer());
		}
		if (c.equals("cursed")) {
			// (the blood or the moon has them: a vampire, or one who carries the wolf)
			return Vampires.isVampire(player) || Lycans.cursed(player) || MoonCurse.cursed(player);
		}
		if (c.equals("human")) {
			return Faiths.human(Heritage.of(player));
		}
		if (c.startsWith("heritage:")) {
			Heritage.Kind kind = Heritage.of(player);
			return kind != null && kind.name().equalsIgnoreCase(c.substring(9));
		}
		if (c.startsWith("patron:")) {
			Patron patron = Patrons.of(player);
			return c.substring(7).equals("none") ? patron == null : patron != null && patron.key.equals(c.substring(7));
		}
		if (c.startsWith("dress:")) {
			return dress(player).contains(c.substring(6));
		}
		if (c.startsWith("reads:")) {
			Languages.Tongue tongue = tongue(c.substring(6));
			return tongue != null && Languages.speaks(player, tongue);
		}
		if (c.startsWith("late:")) {
			String due = run == null ? null : run.vars.get(BY + c.substring(5));
			return due != null && Tales.day(level) > Tales.parse(due, Integer.MAX_VALUE);
		}
		if (c.startsWith("met:")) {
			return run != null && run.vars.containsKey(MET + c.substring(4));
		}
		if (c.equals("companion")) {
			return !Bonds.companions(player, Convictions.WITNESS).isEmpty();
		}
		if (c.startsWith("companion:")) {
			Convictions.Belief belief = Convictions.Belief.byKey(c.substring(10));
			for (Mob m2 : Bonds.companions(player, Convictions.WITNESS)) {
				if (belief != null && Convictions.of(m2).contains(belief)) {
					return true;
				}
			}
			return false;
		}
		return null;
	}

	/** How much {@code player} has of what a comparison weighs; null: nothing to weigh (the tale it counts from never ended). */
	private static @Nullable Long amount(ServerLevel level, ServerPlayer player, Tales.@Nullable Run run, String what, long asked) {
		Faction f = run == null || run.faction < 0 ? null : Factions.get(level.getServer(), run.faction);
		if (what.equals("coins")) {
			return Money.canPay(player, f, Money.worth(asked)) ? asked : asked - 1;
		}
		if (what.equals("reputation")) {
			return f == null ? 0L : (long)Reputation.get(player, f.id());
		}
		if (what.equals("hearts")) {
			LivingEntity who = run == null ? null : speaker(level, run);
			return who instanceof Villager v ? (long)McaCompat.heartsOf(v, player) : 0L;
		}
		// since:<tale>: days since it ended
		Tales.State s = player.getAttached(Tales.STATE);
		if (s != null) {
			for (Tales.Told told : s.told) {
				if (told.tale().equals(what.substring(6))) {
					return Tales.day(level) - told.day();
				}
			}
		}
		return null;
	}

	/** Whoever {@code run}'s open step is with (its {@code who}, when that's one plain name), or else its asker. */
	private static @Nullable LivingEntity speaker(ServerLevel level, Tales.Run run) {
		Tale tale = Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		String who = step == null ? "giver" : Tale.string(step, "who", "giver");
		return Tales.entity(level, who.equals("giver") ? run.giverId() : Tales.uuid(run.cast.get(who)));
	}

	static Languages.@Nullable Tongue tongue(String id) {
		for (Languages.Tongue t : Languages.Tongue.values()) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	/**
	 * How {@code player} is dressed, as anyone would see it: {@code armoured} (heavy armour), {@code bloodied} (blood on
	 * them), {@code fine} (something of {@link #FINE} on, and no rags), {@code ragged} (in {@link #RAGS}, or in a piece worn
	 * nearly through), and {@code plain} when neither fine, ragged nor armoured.
	 */
	static Set<String> dress(ServerPlayer player) {
		Set<String> out = new HashSet<>();
		boolean fine = false, ragged = false;
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack worn = player.getItemBySlot(slot);
			fine |= worn.is(FINE);
			ragged |= worn.is(RAGS) || worn.isDamageableItem() && worn.getDamageValue() >= worn.getMaxDamage() * RAGGED_WEAR;
		}
		if (player.getArmorValue() >= ARMOURED) {
			out.add("armoured");
		}
		if (PowersCompat.bloodSeen(player) >= 0) {
			out.add("bloodied");
		}
		if (ragged) {
			out.add("ragged");
		} else if (fine) {
			out.add("fine");
		}
		if (!ragged && !fine && !out.contains("armoured")) {
			out.add("plain");
		}
		return out;
	}

	// ---- days that run out --------------------------------------------------------------------------------------------

	/** {@code run}'s step is begun: the clocks it starts are started (once), and its own days are counted from today. */
	static void entered(ServerLevel level, Tales.Run run, JsonObject step) {
		long today = Tales.day(level);
		// (the same for everyone sharing the run, whoever's turn it is to count)
		RandomSource random = RandomSource.create(run.instance.hashCode() * 31L + run.step.hashCode());
		for (Map.Entry<String, JsonElement> e : Tale.object(step, "clock").entrySet()) {
			run.vars.putIfAbsent(BY + e.getKey(), Long.toString(today + Tale.between(e.getValue(), 1, random)));
		}
		String candle = Tale.flag(step, "candle") ? "black" : Tale.string(step, "candle", "");
		if (!candle.isEmpty() && !candle(level, run, candle)) {
			run.vars.put(CANDLE, candle);
		}
		JsonElement within = step.get("within");
		if (within != null && !named(within)) {
			run.scratch.put("by", Long.toString(today + Tale.between(within, 1, random)));
		}
	}

	/**
	 * A black candle is set burning in a window of the tavern of {@code run}'s town (the guild's mark that someone has
	 * been paid for): on a sill beside glass if one is near the counter, else on the counter. False if the tavern isn't
	 * loaded yet (it's tried again while the tale runs). {@code colour} is "black", or "white" for the candle that
	 * answers it (the paper bought back). One of a colour is enough for a window. A tale begun outside any town (a
	 * book opened on the road) lights the nearest town's.
	 */
	static boolean candle(ServerLevel level, Tales.Run run, String colour) {
		Faction f = run.faction < 0 ? null : Factions.get(level.getServer(), run.faction);
		if (f == null) {
			f = Tales.nearest(level.getServer(), BlockPos.of(run.home), null, false);
		}
		if (f == null) {
			return true;
		}
		BlockPos tavern = tavern(level, f);
		BlockPos at = tavern != null ? tavern : f.seat();
		if (!level.isLoaded(at)) {
			return false;
		}
		Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(colour + "_candle")).orElse(Blocks.CANDLE);
		BlockPos spot = null;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-CANDLE_REACH, -1, -CANDLE_REACH), at.offset(CANDLE_REACH, 3, CANDLE_REACH))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(block)) {
				return true;
			}
			if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
				continue;
			}
			boolean window = false;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockState beside = level.getBlockState(p.relative(d));
				window |= beside.is(ConventionalBlockTags.GLASS_PANES) || beside.is(ConventionalBlockTags.GLASS_BLOCKS);
			}
			if (window && (spot == null || p.distSqr(at) < spot.distSqr(at))) {
				spot = p.immutable();
			}
		}
		if (spot == null) {
			spot = at.above();
			if (!level.getBlockState(spot).isAir()) {
				// (nowhere to stand it: the town still knows, by its marks and its talk)
				return true;
			}
		}
		level.setBlockAndUpdate(spot, block.defaultBlockState().setValue(AbstractCandleBlock.LIT, true));
		return true;
	}

	private static boolean named(JsonElement within) {
		return within.isJsonPrimitive() && within.getAsJsonPrimitive().isString();
	}

	/** The last day {@code run}'s step may be done on (its {@code within}: so many days, or a clock by name), or -1. */
	static long due(Tales.Run run, JsonObject step) {
		JsonElement within = step.get("within");
		if (within == null) {
			return -1L;
		}
		String due = named(within) ? run.vars.get(BY + within.getAsString()) : run.scratch.get("by");
		return due == null ? -1L : Tales.parse(due, -1);
	}

	/** Whether {@code run}'s step has run out of days (and says what happens then: {@code late}). */
	static boolean late(ServerLevel level, Tales.Run run, JsonObject step) {
		long due = due(run, step);
		return step.has("late") && due >= 0 && Tales.day(level) > due;
	}

	/** So many days left, in words. */
	static Component leftWords(long left) {
		return left <= 0 ? Component.translatable("tale.jaymoskingdoms.left.0") : left == 1 ? Component.translatable("tale.jaymoskingdoms.left.1")
			: Component.translatable("tale.jaymoskingdoms.left.n", left);
	}

	/** How long the clock {@code name} of {@code run} has left, in words (for {@code {left:name}}). */
	static Component leftOn(ServerLevel level, Tales.Run run, String name, boolean said) {
		String due = run.vars.get(BY + name);
		if (due == null) {
			// (a clock not started yet: said before the step that sets its days)
			return Component.translatable("tale.jaymoskingdoms.in.soon");
		}
		long left = Tales.parse(due, 0) - Tales.day(level);
		// ({in:<clock>} is how someone says it in a sentence: today, tomorrow, in 3 days)
		return !said ? leftWords(left) : left <= 0 ? Component.translatable("tale.jaymoskingdoms.in.0")
			: left == 1 ? Component.translatable("tale.jaymoskingdoms.in.1") : Component.translatable("tale.jaymoskingdoms.in.n", left);
	}

	/**
	 * What follows a step's goal in the log: when it can be done ({@code when}), and how long is left, as its asker
	 * would say it ({@code until}) with the days counted after. Null if the step has neither.
	 */
	static @Nullable Component after(ServerLevel level, ServerPlayer player, Tales.Run run, JsonObject step) {
		MutableComponent out = Component.empty();
		String when = Tale.string(step, "when", "");
		if (!when.isEmpty()) {
			out.append(Component.translatable("tale.jaymoskingdoms.when." + when.replace(':', '_')));
		}
		long due = due(run, step);
		if (due >= 0) {
			Component left = leftWords(due - Tales.day(level));
			MutableComponent until = Tales.words(player, run, step, "until");
			out.append(until == null ? Component.translatable("tale.jaymoskingdoms.left.plain", left) : Component.translatable("tale.jaymoskingdoms.left", until, left));
		}
		return when.isEmpty() && due < 0 ? null : out;
	}

	/** Whether {@code run}'s step can be done at this hour (its {@code when}). */
	static boolean now(ServerPlayer player, Tales.Run run, JsonObject step) {
		String when = Tale.string(step, "when", "");
		// (a moon is a night: the day before it doesn't count)
		return when.isEmpty() || Tales.holds(player, run, when) && (!when.contains("moon:") || Tales.holds(player, run, "night"));
	}

	/** {@code who} has nothing to say at this hour: their own words for it ({@code not_now}), or the usual. */
	static void notNow(ServerLevel level, ServerPlayer player, Tales.Run run, JsonObject step, LivingEntity who) {
		MutableComponent own = Tales.words(player, run, step, "not_now");
		Speech.tell(level, who, player, own != null ? own
			: Component.translatable("tale.jaymoskingdoms.not_now." + Tale.string(step, "when", "").replace(':', '_')), Speech.WHITE);
	}

	/** The tavern of {@code f}, where its counter is; or null if it has none. */
	static @Nullable BlockPos tavern(ServerLevel level, Faction f) {
		for (Meals.Eatery e : Meals.eateries(level, f)) {
			if (e.tavern()) {
				return e.counter();
			}
		}
		return null;
	}

	// ---- a word that may or may not sway them ----------------------------------------------------------------------------

	private static int clamp(int v, int most) {
		return Math.clamp(v, -most, most);
	}

	/**
	 * The odds (in a hundred) that {@code who} is swayed by the try {@code t} ({@code how}: persuade, threaten or bribe):
	 * from their temperament, their hearts for the player, the player's standing with their town, what they remember of
	 * the player, how the player looks (a threat), and whatever the tale says {@code helps} or {@code hurts}.
	 */
	static int odds(ServerPlayer player, Tales.Run run, JsonObject t, LivingEntity who) {
		String how = Tale.string(t, "how", "persuade");
		boolean dice = how.equals("dice");
		// (the dice have no temper and no memory: only what the tale says loads them)
		Temperaments.Temperament temper = dice ? null : Temperaments.of(who);
		if (how.equals("bribe") && Boolean.FALSE.equals(Temperaments.takesBribe(who))) {
			return ODDS_LEAST;
		}
		int odds = ODDS + Tale.number(t, "odds", 0) * ODDS_STEP;
		if (temper != null) {
			odds += switch (how) {
				case "threaten" -> switch (temper) {
					case TIMID -> THREAT_TIMID;
					case GLOOMY, GREEDY -> THREAT_GIVES;
					case BRAVE -> THREAT_BRAVE;
					case HOTHEADED -> THREAT_HOTHEAD;
					case PROUD, SPITEFUL -> THREAT_PROUD;
					default -> 0;
				};
				case "bribe" -> switch (temper) {
					case GREEDY -> BRIBE_TAKES;
					case SPITEFUL, GLOOMY -> BRIBE_MAYBE;
					case DUTIFUL -> BRIBE_DUTIFUL;
					case DEVOUT -> BRIBE_DEVOUT;
					default -> 0;
				};
				default -> switch (temper) {
					case KINDHEARTED -> TALK_KIND;
					case CHEERFUL -> TALK_CHEERFUL;
					case GLOOMY -> TALK_GLOOMY;
					case PROUD, SPITEFUL -> TALK_PROUD;
					case SUSPICIOUS -> TALK_SUSPICIOUS;
					default -> 0;
				};
			};
		}
		if (dice) {
			// (nothing about the two of them sways a throw)
		} else if (how.equals("threaten")) {
			Set<String> dress = dress(player);
			odds += (dress.contains("armoured") ? THREAT_ARMOUR : 0) + (dress.contains("bloodied") ? THREAT_BLOOD : 0)
				+ (run.vars.containsKey("~cut_down") || cutDown(who, player) ? THREAT_DEED : 0);
		} else {
			odds += who instanceof Villager v ? clamp(McaCompat.heartsOf(v, player) / HEARTS_PER, SWAY_MOST) : 0;
			odds += run.faction < 0 ? 0 : clamp(Reputation.get(player, run.faction) / STANDING_PER, SWAY_MOST);
			odds += clamp(Recollections.leaning(who, player) * LEAN_TIMES, SWAY_MOST);
		}
		for (String cond : Tale.strings(t.get("helps"))) {
			odds += Tales.holds(player, run, cond) ? HELP : 0;
		}
		for (String cond : Tale.strings(t.get("hurts"))) {
			odds -= Tales.holds(player, run, cond) ? HELP : 0;
		}
		return Math.clamp(odds, ODDS_LEAST, ODDS_MOST);
	}

	/** The odds in words, as they're shown beside the reply. */
	static Component oddsWords(int odds) {
		return Component.translatable("tale.jaymoskingdoms.odds." + (odds >= WORDS_LIKELY ? 3 : odds >= WORDS_FAIR ? 2 : odds >= WORDS_LONG ? 1 : 0))
			.withStyle(ChatFormatting.GRAY);
	}

	/** Whether {@code who} saw, or has heard, that {@code player} killed someone who had yielded (in this tale or any other). */
	private static boolean cutDown(LivingEntity who, ServerPlayer player) {
		for (Recollections.Recollection r : Recollections.of(who, player)) {
			if (r.type() == Recollections.Kind.CUT_DOWN) {
				return true;
			}
		}
		return false;
	}

	/** How a try is likely to go, in words, as it's shown beside the reply (a throw of the dice only says it's one). */
	static Component chance(ServerPlayer player, Tales.Run run, JsonObject t, LivingEntity who) {
		return Tale.string(t, "how", "persuade").equals("dice")
			? Component.translatable("tale.jaymoskingdoms.odds.dice").withStyle(ChatFormatting.GRAY) : oddsWords(odds(player, run, t, who));
	}

	/**
	 * What {@code who} asks for the bribe {@code t} (coins): its price, and more from the greedy and the proud. For a
	 * throw of the dice, the stake.
	 */
	static int price(JsonObject t, LivingEntity who) {
		int coins = Math.max(1, Tale.number(t, "coins", 1));
		if (Tale.string(t, "how", "persuade").equals("dice")) {
			return coins;
		}
		return (int)Math.ceil(coins * (Temperaments.is(who, Temperaments.Temperament.GREEDY) ? BRIBE_GREEDY
			: Temperaments.is(who, Temperaments.Temperament.PROUD) ? BRIBE_PROUD : 1.0));
	}

	/** Whether {@code player} can make the try {@code t} at all (a bribe takes the coin; a stake has to be on the table). */
	static boolean canTry(ServerPlayer player, Tales.Run run, JsonObject t, LivingEntity who) {
		String how = Tale.string(t, "how", "persuade");
		return !how.equals("bribe") && !how.equals("dice") || Money.canPay(player, Tales.faction(player, run), Money.worth(price(t, who)));
	}

	/**
	 * {@code player} makes the try {@code t} on {@code who}: the lot is drawn against {@link #odds}. A bribe taken is paid
	 * (one refused is kept), a stake at dice is won or lost, and they remember being bought or threatened. True if it worked.
	 */
	static boolean attempt(ServerPlayer player, Tales.Run run, JsonObject t, LivingEntity who) {
		String how = Tale.string(t, "how", "persuade");
		boolean won = player.getRandom().nextInt(100) < odds(player, run, t, who);
		if (how.equals("dice")) {
			// (the stake changes hands one way or the other)
			if (won) {
				Money.pay(player, Tales.faction(player, run), Money.worth(price(t, who)));
			} else {
				Money.charge(player, Tales.faction(player, run), Money.worth(price(t, who)));
			}
		} else if (how.equals("bribe") && won) {
			Money.charge(player, Tales.faction(player, run), Money.worth(price(t, who)));
			Recollections.remember(who, player, Recollections.Kind.BRIBED, Component.empty());
		} else if (how.equals("threaten")) {
			Recollections.remember(who, player, Recollections.Kind.THREATENED, Component.empty());
		}
		if (!won && player.level() instanceof ServerLevel level) {
			MutableComponent refuse = Tales.words(player, run, t, "refuse");
			Speech.tell(level, who, player, refuse != null ? refuse : Component.translatable("tale.jaymoskingdoms.refuse." + how + "." + level.getRandom().nextInt(2)), Speech.RED);
		}
		return won;
	}

	// ---- what a companion makes of it -------------------------------------------------------------------------------------

	private static Set<Convictions.Belief> beliefs(List<String> keys) {
		Set<Convictions.Belief> out = new HashSet<>();
		for (String key : keys) {
			Convictions.Belief b = Convictions.Belief.byKey(key);
			if (b != null) {
				out.add(b);
			}
		}
		return out;
	}

	/**
	 * A choice is before {@code player}: one companion near who believes in something it turns on ({@code for},
	 * {@code irks} or {@code against} on one of the replies offered, or a belief the step has {@code voices} for) says so, in the tale's words for that belief
	 * ({@code voices}) or their own. Once for each step.
	 */
	static void voiceBefore(ServerLevel level, ServerPlayer player, Tales.Run run, JsonObject step) {
		if (run.scratch.putIfAbsent("voiced", "1") != null) {
			return;
		}
		// (what the replies this player is offered stand for, irk or go against; and what the tale wrote them a line for)
		JsonObject own = Tale.object(step, "voices");
		List<String> keys = new ArrayList<>(own.keySet());
		List<JsonObject> replies = Tale.objects(step.get("replies"));
		for (int n = 0; n < replies.size(); n++) {
			JsonObject reply = replies.get(n);
			String cond = Tale.string(reply, "if", "");
			if (!cond.isEmpty() && !Tales.holds(player, run, cond) || reply.has("try") && run.vars.containsKey(tried(run, n))) {
				continue;
			}
			keys.addAll(Tale.strings(reply.get("for")));
			keys.addAll(Tale.strings(reply.get("irks")));
			keys.addAll(Tale.strings(reply.get("against")));
		}
		Set<Convictions.Belief> turns = beliefs(keys);
		if (turns.isEmpty()) {
			return;
		}
		for (Mob m : Bonds.companions(player, Convictions.WITNESS)) {
			if (m.getUUID().equals(run.giverId())) {
				// (the one whose tale it is doesn't speak up about their own choice)
				continue;
			}
			for (Convictions.Belief b : Convictions.of(m)) {
				if (turns.contains(b)) {
					MutableComponent line = Tales.words(player, run, own, b.key);
					Speech.tell(level, m, player, line != null ? line
						: Component.translatable("tale.jaymoskingdoms.voice." + b.key + "." + level.getRandom().nextInt(VOICE_LINES)), Speech.WHITE);
					return;
				}
			}
		}
	}

	/**
	 * {@code player} chose {@code reply}: each companion near who believes in what it stood {@code for} is a little
	 * closer for it, and each who believes in what it {@code irks} a little further, and says so. One whose bond that
	 * empties leaves; nobody else does.
	 */
	static void voiceAfter(ServerLevel level, ServerPlayer player, JsonObject reply) {
		Set<Convictions.Belief> stood = beliefs(Tale.strings(reply.get("for"))), irked = beliefs(Tale.strings(reply.get("irks")));
		if (stood.isEmpty() && irked.isEmpty()) {
			return;
		}
		boolean said = false;
		for (Mob m : Bonds.companions(player, Convictions.WITNESS)) {
			Set<Convictions.Belief> holds = Convictions.of(m);
			boolean differs = holds.stream().anyMatch(irked::contains);
			if (!differs && holds.stream().noneMatch(stood::contains)) {
				continue;
			}
			Bonds.add(m, player.getUUID(), differs ? -VOICE_BOND : VOICE_BOND);
			if (!said) {
				// (one of them speaks for the rest)
				said = true;
				Speech.tell(level, m, player, Component.translatable("tale.jaymoskingdoms.voice." + (differs ? "differ" : "agree") + "." + level.getRandom().nextInt(VOICE_LINES)),
					differs ? Speech.RED : Speech.GOLD);
			}
			if (differs && Bonds.of(m, player.getUUID()) <= 0) {
				Convictions.leave(level, m, player, holds.stream().filter(irked::contains).findFirst().orElseThrow().key);
			}
		}
	}

	// ---- naming who did it ---------------------------------------------------------------------------------------------------

	/**
	 * {@code who} asks {@code player} who did it ({@code "do": "name"}): everyone the step suspects whom the player has
	 * met can be named, or nobody yet. Naming one goes on by whether there's proof enough ({@link #named}).
	 */
	static void name(ServerLevel level, ServerPlayer player, Tales.Run run, JsonObject step, LivingEntity who) {
		if (!now(player, run, step)) {
			notNow(level, player, run, step, who);
			return;
		}
		String at = run.step;
		List<Menus.Choice> choices = new ArrayList<>();
		for (JsonObject suspect : Tale.objects(step.get("suspects"))) {
			String role = Tale.string(suspect, "who", "");
			String cond = Tale.string(suspect, "if", "");
			if (!run.vars.containsKey(MET + role) || !cond.isEmpty() && !Tales.holds(player, run, cond)) {
				continue;
			}
			choices.add(Menus.Choice.of(Tales.text(player, run, Tale.string(suspect, "say", "{" + role + "}")), p -> {
				if (Tales.open(p, run, at)) {
					named(p, run, suspect, who);
				}
			}));
		}
		JsonObject none = Tale.object(step, "none");
		MutableComponent notYet = Tales.words(player, run, none, "say");
		choices.add(Menus.Choice.of(notYet != null ? notYet : Component.translatable("tale.jaymoskingdoms.name.not_yet"), p -> {
			if (none.has("then") && Tales.open(p, run, at)) {
				Tales.go(p, run, none.get("then"), who);
			}
		}));
		MutableComponent say = Tales.words(player, run, step, "say");
		TaleCast.hold(who, player);
		Menus.ask(player, who, say != null ? say : Component.translatable("tale.jaymoskingdoms.name.ask"), choices);
	}

	/** {@code player} named {@code suspect}: believed if enough of its {@code proof} holds ({@code need}, one if unsaid), doubted if not. */
	private static void named(ServerPlayer player, Tales.Run run, JsonObject suspect, LivingEntity who) {
		int have = 0;
		for (String proof : Tale.strings(suspect.get("proof"))) {
			have += Tales.holds(player, run, proof) ? 1 : 0;
		}
		boolean believed = !suspect.has("doubted") || have >= Tale.number(suspect, "need", 1);
		String role = Tale.string(suspect, "who", "");
		String mark = Tale.string(suspect, "mark", "");
		MutableComponent line = Tales.words(player, run, suspect, "journal");
		Tales.each(player, run, r -> {
			r.vars.put("named", role);
			if (line != null) {
				Tales.note(r, line.getString());
			}
		});
		if (!mark.isEmpty()) {
			for (ServerPlayer p : Tales.sharers(player, run)) {
				Tales.state(p).marks.add(mark);
				learnt(p, run, mark);
			}
		}
		Tales.go(player, run, believed ? suspect.get("then") : suspect.get("doubted"), who);
	}

	// ---- a watch kept ----------------------------------------------------------------------------------------------------------

	/**
	 * A watch ({@code "do": "vigil"}): while {@code player} keeps near its place at its hour, its {@code lines} come one
	 * at a time (said by one of the tale's people, or seen and heard: the tracker reads it, and it's written down), and when the
	 * last has, the tale goes on. Walking well away once it's begun goes the step's {@code left} way, if it has one.
	 */
	static void vigil(ServerLevel level, ServerPlayer player, Tales.Run run, Tale tale, JsonObject step) {
		if (!now(player, run, step)) {
			return;
		}
		Predicate<String> facts = Tales.facts(player, run);
		BlockPos at = Tales.place(level, run, tale, Tale.pickString(step, "at", facts, ""));
		double dx = at.getX() + 0.5 - player.getX(), dz = at.getZ() + 0.5 - player.getZ();
		double far = Math.sqrt(dx * dx + dz * dz);
		int radius = Tale.number(step, "radius", VIGIL_RADIUS);
		int n = Tales.parse(run.scratch.getOrDefault("line", "0"), 0);
		if (far > radius) {
			if (n > 0 && step.has("left") && far > radius * VIGIL_LEFT) {
				Tales.each(player, run, r -> r.scratch.put(UNDONE, "1"));
				Tales.go(player, run, step.get("left"), null);
			}
			return;
		}
		int waited = Tales.parse(run.scratch.getOrDefault("waited", "0"), 0) + 1;
		if (waited < Tale.number(step, "gap", VIGIL_GAP) && n > 0) {
			run.scratch.put("waited", Integer.toString(waited));
			return;
		}
		List<JsonObject> lines = Tale.objects(Tale.pick(step.get("lines"), facts));
		if (n >= lines.size()) {
			Tales.go(player, run, step.get("then"), null);
			return;
		}
		JsonObject line = lines.get(n);
		String cond = Tale.string(line, "if", "");
		run.scratch.put("line", Integer.toString(n + 1));
		run.scratch.put("waited", "0");
		if (!cond.isEmpty() && !facts.test(cond)) {
			return;
		}
		MutableComponent words = Tales.words(player, run, line, "say");
		String role = Tale.string(line, "who", "");
		LivingEntity who = role.isEmpty() ? null : Tales.entity(level, role.equals("giver") ? run.giverId() : Tales.uuid(run.cast.get(role)));
		if (words != null && who != null && !TaleCast.away(who)) {
			Speech.tell(level, who, player, words, Speech.WHITE);
		} else if (words != null && role.isEmpty()) {
			// (what's seen and heard is the player's own to write: the tracker reads it, and the journal keeps it, for
			// everyone keeping the watch; a line a person would say, with that person not there, is let go)
			String seen = words.getString();
			for (ServerPlayer p : Tales.sharers(player, run)) {
				Tales.Run theirs = p == player ? run : Tales.run(p, run.instance);
				if (theirs != null) {
					theirs.scratch.put("seen", seen);
					if (!theirs.notes.contains(seen)) {
						Tales.note(theirs, seen);
					}
					Quests.sync(p, true);
				}
			}
		}
		Identifier sound = Identifier.tryParse(Tale.string(line, "sound", ""));
		SoundEvent heard = sound == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(sound).orElse(null);
		if (heard != null) {
			level.playSound(null, at, heard, SoundSource.AMBIENT, 1.0F, 1.0F);
		}
		String mark = Tale.string(line, "mark", "");
		if (!mark.isEmpty()) {
			for (ServerPlayer p : Tales.sharers(player, run)) {
				Tales.state(p).marks.add(mark);
				learnt(p, run, mark);
			}
		}
	}

	// ---- a field to cut -----------------------------------------------------------------------------------------------------------

	/**
	 * A field to cut ({@code "do": "reap"}): the first time {@code player} comes near its place a small ripe field
	 * stands there (ploughed out of the ground, a pool in the middle to keep it), and the step is done when
	 * {@code count} ripe plants have been cut in it ({@link #cut}).
	 */
	static void reap(ServerLevel level, ServerPlayer player, Tales.Run run, Tale tale, JsonObject step) {
		BlockPos at = Tales.ground(level, run, tale, Tale.pickString(step, "at", Tales.facts(player, run), ""));
		if (at == null || !now(player, run, step)) {
			return;
		}
		double dx = at.getX() + 0.5 - player.getX(), dz = at.getZ() + 0.5 - player.getZ();
		if (dx * dx + dz * dz > REAP_NEAR * REAP_NEAR || !level.isLoaded(at)) {
			return;
		}
		String sown = SOWN + run.step;
		if (!run.vars.containsKey(sown)) {
			Tales.each(player, run, r -> r.vars.put(sown, "1"));
			Identifier id = Identifier.tryParse(Tale.string(step, "crop", "minecraft:wheat"));
			CropBlock crop = id != null && BuiltInRegistries.BLOCK.getValue(id) instanceof CropBlock c ? c : (CropBlock) Blocks.WHEAT;
			for (int x = -REAP_REACH; x <= REAP_REACH; x++) {
				for (int z = -REAP_REACH; z <= REAP_REACH; z++) {
					BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(x, 0, z));
					BlockPos soil = top.below();
					BlockState ground = level.getBlockState(soil);
					if (Math.abs(top.getY() - at.getY()) > REAP_SLOPE || !soil(ground)
						|| !level.getBlockState(top).canBeReplaced()) {
						continue;
					}
					if (x == 0 && z == 0) {
						level.setBlockAndUpdate(soil, Blocks.WATER.defaultBlockState());
						continue;
					}
					level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					level.setBlockAndUpdate(top, crop.getStateForAge(crop.getMaxAge()));
				}
			}
		}
		// (the same count, and the same default of one, as the step's quest in the log)
		if (Tales.parse(run.scratch.getOrDefault("cut", "0"), 0) >= Tale.pickNumber(step, "count", Tales.facts(player, run), 1)) {
			Tales.go(player, run, step.get("then"), null);
		}
	}

	/** Ground a tale's field can be ploughed out of: earth, and failing that sand, gravel or bare stone, so there is always a crop to cut. */
	private static boolean soil(BlockState ground) {
		return ground.is(BlockTags.DIRT) || ground.is(Blocks.FARMLAND) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL)
			|| ground.is(BlockTags.BASE_STONE_OVERWORLD);
	}

	/** {@code player} broke {@code state} at {@code pos}: a ripe plant cut in the field of a {@code reap} step of theirs is counted. */
	static void cut(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
			return;
		}
		for (Tales.Run run : Tales.state(player).runs) {
			Tale tale = Tale.get(run.tale);
			JsonObject step = tale == null ? null : tale.step(run.step);
			if (step == null || !Tale.string(step, "do", "").equals("reap")) {
				continue;
			}
			BlockPos at = Tales.place(level, run, tale, Tale.pickString(step, "at", Tales.facts(player, run), ""));
			if (Math.abs(pos.getX() - at.getX()) > REAP_RADIUS || Math.abs(pos.getZ() - at.getZ()) > REAP_RADIUS) {
				continue;
			}
			// (the party cuts the one field: what any of them cuts counts for all)
			int cut = Tales.parse(run.scratch.getOrDefault("cut", "0"), 0) + 1;
			for (ServerPlayer p : Tales.sharers(player, run)) {
				Tales.Run theirs = p == player ? run : Tales.run(p, run.instance);
				if (theirs == null) {
					continue;
				}
				theirs.scratch.put("cut", Integer.toString(cut));
				Quest q = Quests.quest(p, theirs.quest());
				if (q != null) {
					Quest.Objective o = q.objective();
					Quests.put(p, q.withObjective(new Quest.Objective(o.target(), o.count(), Math.min(cut, o.count()), o.pos(), o.other(), o.otherName(), o.place(), o.spawned())));
					Quests.sync(p, true);
				}
			}
		}
	}

	// ---- following someone -----------------------------------------------------------------------------------------------------

	/**
	 * Following someone ({@code "do": "tail"}): at its hour, once {@code player} is near enough to follow, the step's
	 * {@code who} sets off on foot for its {@code to} place. Coming nearer than {@code near} blocks where they can see
	 * (half that, crouched) goes the step's {@code seen} way; falling further back than {@code far} for
	 * {@link #TAIL_LOST} seconds goes its {@code lost} way. Still with them when they arrive, the tale goes on.
	 */
	static void tail(ServerLevel level, ServerPlayer player, Tales.Run run, Tale tale, JsonObject step) {
		if (!now(player, run, step)) {
			return;
		}
		Predicate<String> facts = Tales.facts(player, run);
		String role = Tale.pickString(step, "who", facts, "");
		BlockPos to = Tales.ground(level, run, tale, Tale.pickString(step, "to", facts, ""));
		if (!(Tales.entity(level, Tales.uuid(run.cast.get(role))) instanceof Mob mob) || to == null) {
			return;
		}
		int near = Tale.number(step, "near", TAIL_NEAR), far = Tale.number(step, "far", TAIL_FAR);
		double gap = Math.sqrt(mob.distanceToSqr(player));
		// (the one nearest them of everyone following: they're lost only when the whole party has lost them)
		double nearest = gap;
		for (ServerPlayer p : Tales.sharers(player, run)) {
			nearest = Math.min(nearest, Math.sqrt(mob.distanceToSqr(p)));
		}
		if (!run.scratch.containsKey("going")) {
			if (nearest > far) {
				// (they don't set off until there's someone to follow them)
				return;
			}
			String set = Long.toString(level.getGameTime());
			Tales.each(player, run, r -> r.scratch.put("going", set));
			TaleCast.send(mob, to);
			Tales.each(player, run, r -> r.places.put("@" + role, to.asLong()));
		}
		boolean started = level.getGameTime() - Tales.parse(run.scratch.get("going"), 0) > TAIL_GRACE * 20L;
		if (started && gap < (player.isCrouching() ? near * TAIL_CROUCHED : near) && mob.hasLineOfSight(player)) {
			Tales.each(player, run, r -> r.scratch.put(UNDONE, "1"));
			Tales.go(player, run, step.get("seen"), mob);
			return;
		}
		if (nearest > far) {
			int lost = Tales.parse(run.scratch.getOrDefault("lost", "0"), 0) + 1;
			if (lost >= TAIL_LOST) {
				Tales.each(player, run, r -> r.scratch.put(UNDONE, "1"));
				Tales.go(player, run, step.get("lost"), null);
				return;
			}
			run.scratch.put("lost", Integer.toString(lost));
		} else {
			run.scratch.remove("lost");
		}
		double dx = to.getX() + 0.5 - mob.getX(), dz = to.getZ() + 0.5 - mob.getZ();
		if (dx * dx + dz * dz <= TAIL_ARRIVE * TAIL_ARRIVE) {
			Tales.go(player, run, step.get("then"), null);
		}
	}

	// ---- what a beast is weak to ------------------------------------------------------------------------------------------------

	private static boolean carries(ServerPlayer player, String spec) {
		Identifier id = Identifier.tryParse(spec.startsWith("#") ? spec.substring(1) : spec);
		if (id == null) {
			return false;
		}
		TagKey<Item> tag = spec.startsWith("#") ? TagKey.create(Registries.ITEM, id) : null;
		Item item = tag != null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && (tag != null ? s.is(tag) : item != null && s.is(item))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The fight of {@code run}'s foe step begins: for each thing the step says the foe is {@code weak_to} that
	 * {@code player} has learned ({@code know}) and carries ({@code item}, where it asks one), the foe has less health
	 * and a lighter blow, the journal says why, and the bestiary gets its note.
	 */
	static void prepared(ServerPlayer player, Tales.Run run, JsonObject step, Mob foe) {
		double share = 1.0;
		Tale tale = Tale.get(run.tale);
		for (JsonObject w : Tale.objects(step.get("weak_to"))) {
			String know = Tale.string(w, "know", ""), item = Tale.string(w, "item", "");
			if (!know.isEmpty() && !Tales.holds(player, run, know)) {
				continue;
			}
			if (!know.isEmpty() && tale != null) {
				// (known, so it's in the bestiary whether or not it was brought: it may have been missed as it was learned)
				for (ServerPlayer p : Tales.sharers(player, run)) {
					teach(p, run, tale, step, w);
				}
			}
			if (!item.isEmpty() && !carries(player, item) || Tale.flag(w, "water") && !byWater(foe)) {
				continue;
			}
			share *= Tale.decimal(w, "share", WEAK_SHARE);
			MutableComponent why = Tales.words(player, run, w, "journal");
			if (why != null) {
				Tales.each(player, run, r -> {
					if (!r.notes.contains(why.getString())) {
						Tales.note(r, why.getString());
					}
				});
			}
			if (know.isEmpty() && tale != null) {
				// (nothing to learn it from first: what was carried into the fight taught it)
				for (ServerPlayer p : Tales.sharers(player, run)) {
					teach(p, run, tale, step, w);
				}
			}
		}
		if (share >= 1.0) {
			return;
		}
		share = Math.max(WEAK_LEAST, share);
		for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
			AttributeInstance instance = foe.getAttribute(attribute);
			if (instance != null) {
				instance.setBaseValue(instance.getBaseValue() * share);
			}
		}
		foe.setHealth(Math.min(foe.getHealth(), foe.getMaxHealth()));
	}

	/** Whether {@code foe} stands in water, or within {@link #WATER_NEAR} blocks of water that runs. */
	static boolean byWater(Mob foe) {
		if (foe.isInWater()) {
			return true;
		}
		for (BlockPos p : BlockPos.betweenClosed(foe.blockPosition().offset(-WATER_NEAR, -1, -WATER_NEAR), foe.blockPosition().offset(WATER_NEAR, 1, WATER_NEAR))) {
			FluidState fluid = foe.level().getFluidState(p);
			if (fluid.is(FluidTags.WATER) && !fluid.isSource()) {
				return true;
			}
		}
		return false;
	}

	/** {@code w} (one of {@code step}'s {@code weak_to}) goes in {@code player}'s bestiary, on the page of the kind of foe it's about. */
	private static void teach(ServerPlayer player, Tales.Run run, Tale tale, JsonObject step, JsonObject w) {
		MutableComponent note = Tales.words(player, run, w, "bestiary");
		if (note != null) {
			Bestiary.learned(player, kindOf(run, tale, step), note.getString());
		}
	}

	/**
	 * The bestiary page a foe step's notes go on: its own {@code bestiary_kind} (blood-drinkers, wolves), else the kind of
	 * creature its foe is, else the foe's name.
	 */
	static String kindOf(Tales.Run run, Tale tale, JsonObject step) {
		String own = Tale.string(step, "bestiary_kind", "");
		if (!own.isEmpty()) {
			return own;
		}
		String role = Tale.string(step, "who", "");
		JsonObject body = role.isEmpty() ? null : tale.role(role);
		String kind = body == null ? "" : Tale.string(body, "body", "");
		Identifier id = kind.contains(":") ? Identifier.tryParse(kind) : null;
		EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		if (type != null) {
			return type.getDescription().getString();
		}
		String name = run.names.getOrDefault(role, "");
		return name.isEmpty() ? role : name;
	}

	/**
	 * {@code mark} has just been set for {@code player}: whatever a beast of one of their tales is weak to that it
	 * teaches ({@code weak_to} entries whose {@code know} it is) goes in their bestiary now. Knowing is enough; carrying
	 * the thing matters only in the fight.
	 */
	static void learnt(ServerPlayer player, String mark) {
		if (mark.isEmpty()) {
			return;
		}
		Tales.State s = Tales.state(player);
		for (Tales.Run run : new ArrayList<>(s.runs)) {
			learnt(player, run, mark);
		}
	}

	/** {@link #learnt(ServerPlayer, String)}, for one run. */
	static void learnt(ServerPlayer player, Tales.Run run, String mark) {
		Tale tale = Tale.get(run.tale);
		if (tale == null || mark.isEmpty()) {
			return;
		}
		for (JsonElement e : Tale.object(tale.json, "steps").asMap().values()) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject step = e.getAsJsonObject();
			for (JsonObject w : Tale.objects(step.get("weak_to"))) {
				String know = Tale.string(w, "know", "");
				if (names(know, mark) && Tales.holds(player, run, know)) {
					teach(player, run, tale, step, w);
				}
			}
		}
	}

	/** Whether the condition {@code know} turns on {@code mark}. */
	private static boolean names(String know, String mark) {
		for (String part : know.split("&")) {
			if (part.trim().equals("mark:" + mark)) {
				return true;
			}
		}
		return false;
	}

	// ---- words in a tongue you can't read -------------------------------------------------------------------------------------

	/**
	 * {@code lines}, as {@code player} can read what {@code spec} (a tale's letter, clue or thing) wrote: themselves, or
	 * gibberish if it's in a {@code tongue} they don't speak. What reading it does ({@code mark}, {@code told}) is kept in
	 * {@code tag} with the true words until it's read ({@link #read}).
	 */
	static List<Component> written(ServerPlayer player, Tales.Run run, JsonObject spec, List<Component> lines, CompoundTag tag) {
		Languages.Tongue tongue = tongue(Tale.string(spec, "tongue", ""));
		String mark = Tale.string(spec, "mark", "");
		boolean veiled = tongue != null && !Languages.speaks(player, tongue);
		if (!veiled && mark.isEmpty()) {
			return lines;
		}
		JsonObject kept = new JsonObject();
		kept.addProperty("mark", mark);
		MutableComponent told = Tales.words(player, run, spec, "told");
		if (told != null) {
			kept.addProperty("told", told.getString());
		}
		if (!veiled) {
			tag.putString(KEY_UNREAD, kept.toString());
			return lines;
		}
		kept.addProperty("tongue", tongue.id());
		JsonArray truth = new JsonArray();
		List<Component> out = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			truth.add(lines.get(i).getString());
			out.add(Languages.garble(tongue, run.instance.hashCode() * 31L + lines.get(i).getString().hashCode()));
		}
		kept.add("lines", truth);
		tag.putString(KEY_UNREAD, kept.toString());
		return out;
	}

	private static @Nullable JsonObject unread(ItemStack stack) {
		String kept = Tales.tagged(stack, KEY_UNREAD);
		if (kept.isEmpty()) {
			return null;
		}
		try {
			return JsonParser.parseString(kept).getAsJsonObject();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * {@code player} looks at {@code stack}: if a tale wrote it and they can read it (it's in a tongue they speak, or one
	 * they've learned since), its true words show and what reading it does is done. False if they still can't.
	 */
	static boolean read(ServerPlayer player, ItemStack stack) {
		JsonObject kept = unread(stack);
		if (kept == null) {
			return true;
		}
		Languages.Tongue tongue = tongue(Tale.string(kept, "tongue", ""));
		if (tongue != null && !Languages.speaks(player, tongue)) {
			return false;
		}
		reveal(player, stack, kept, "");
		return true;
	}

	/** {@code stack}'s true words are shown, its mark set ({@code how}: a suffix for how it was read), and it's a plain thing from now on. */
	private static void reveal(ServerPlayer player, ItemStack stack, JsonObject kept, String how) {
		List<Component> lines = new ArrayList<>();
		for (String line : Tale.strings(kept.get("lines"))) {
			lines.add(Component.literal(line));
		}
		Letters.Letter letter = Letters.of(stack);
		if (!lines.isEmpty() && letter != null) {
			stack.set(Letters.CONTENTS, new Letters.Letter(letter.to(), letter.from(), lines, letter.title()));
		} else if (!lines.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(lines.stream()
				.map(l -> (Component)l.copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))).toList()));
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.remove(KEY_UNREAD);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		String mark = Tale.string(kept, "mark", "");
		if (!mark.isEmpty()) {
			Tales.state(player).marks.add(mark);
			if (!how.isEmpty()) {
				Tales.state(player).marks.add(mark + how);
			}
			learnt(player, mark);
			if (!how.isEmpty()) {
				learnt(player, mark + how);
			}
		}
		player.containerMenu.broadcastChanges();
	}

	/**
	 * {@code player} holds out something they can't read to {@code who}: a priest or a librarian reads it to them for a
	 * coin. They know what it says from then on, and now and then their town hears of it ({@code told}). True if
	 * {@code who} is one who reads and the thing is one that wants reading (the offer's made).
	 */
	static boolean reader(ServerPlayer player, LivingEntity who, ItemStack held) {
		JsonObject kept = unread(held);
		if (kept == null || !kept.has("tongue") || !(who instanceof Villager v) || v.isBaby() || TaleCast.cast(who) != null
			|| !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		if (!job.equals("cleric") && !job.equals("librarian")) {
			return false;
		}
		Faction f = Quests.settlement(level, v);
		long cost = Money.worth(READ_COINS);
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.reader.pay", Money.text(f, cost)), Money.canPay(player, f, cost), p -> {
			ItemStack now = p.getMainHandItem();
			JsonObject still = unread(now);
			if (still == null || !still.has("tongue") || !v.isAlive() || !Money.charge(p, f, cost)) {
				return;
			}
			reveal(p, now, still, ":aloud");
			Speech.tell(level, v, p, Component.translatable("tale.jaymoskingdoms.reader.read." + level.getRandom().nextInt(2)), Speech.WHITE);
			level.playSound(null, v.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 0.9F);
			String told = Tale.string(still, "told", "");
			if (!told.isEmpty() && f != null && level.getRandom().nextFloat() < READER_TALKS) {
				Overheard.townTalk(p, f.id(), List.of(Component.literal(told)), false);
				String mark = Tale.string(still, "mark", "");
				if (!mark.isEmpty()) {
					Tales.state(p).marks.add(mark + ":told");
				}
			}
		}));
		choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.reader.no"), p -> {
		}));
		TaleCast.hold(who, player);
		Menus.ask(player, who, Component.translatable("tale.jaymoskingdoms.reader.offer"), choices);
		return true;
	}

	// ---- what comes later ---------------------------------------------------------------------------------------------------------

	private static JsonArray filled(ServerPlayer player, Tales.Run run, @Nullable JsonElement lines) {
		JsonArray out = new JsonArray();
		for (String line : Tale.strings(Tale.pick(lines, Tales.facts(player, run)))) {
			out.add(Tales.text(player, run, line).getString());
		}
		return out;
	}

	/**
	 * {@code run} ended with {@code ending}: what it brings {@code later} (each: {@code days}, and any of a
	 * {@code letter}, {@code town} talk, a {@code scene} between two of the town, someone who {@code settles} there, a
	 * {@code mark}) is put by for its day, its words filled in now while the tale still knows its names.
	 */
	static void schedule(ServerLevel level, ServerPlayer player, Tales.Run run, Tale tale, JsonObject ending) {
		for (JsonObject l : Tale.objects(ending.get("later"))) {
			String cond = Tale.string(l, "if", "");
			if (!cond.isEmpty() && !Tales.holds(player, run, cond)) {
				continue;
			}
			JsonObject what = new JsonObject();
			JsonObject letter = Tale.object(l, "letter");
			if (!letter.isEmpty()) {
				JsonObject sent = letter.deepCopy();
				sent.addProperty("from", Tales.text(player, run, Tale.string(letter, "from", "")).getString());
				if (letter.has("to")) {
					sent.addProperty("to", Tales.text(player, run, Tale.string(letter, "to", "")).getString());
				}
				sent.add("lines", filled(player, run, letter.get("lines")));
				MutableComponent told = Tales.words(player, run, letter, "told");
				if (told != null) {
					sent.addProperty("told", told.getString());
				}
				what.add("letter", sent);
			}
			if (l.has("town")) {
				what.add("town", filled(player, run, l.get("town")));
			}
			JsonArray scene = new JsonArray();
			for (JsonElement line : l.get("scene") instanceof JsonArray a ? a : new JsonArray()) {
				if (line instanceof JsonArray pair && pair.size() == 2) {
					scene.add(pair.get(0).getAsString() + "|" + Tales.text(player, run, pair.get(1).getAsString()).getString());
				}
			}
			if (!scene.isEmpty()) {
				what.add("scene", scene);
			}
			String settles = Tale.string(l, "settles", "");
			JsonObject body = settles.isEmpty() || run.vars.containsKey(Tales.DEAD + settles) ? null : tale.role(settles);
			if (body != null) {
				JsonObject who = new JsonObject();
				who.addProperty("name", run.names.getOrDefault(settles, ""));
				who.add("body", body);
				who.addProperty("as", Tale.string(l, "as", ""));
				what.add("settles", who);
			}
			if (l.has("grave")) {
				// (the words on the board at its head)
				what.add("grave", filled(player, run, l.get("grave")));
			}
			UUID gone = Tale.string(l, "leaves", "").equals("giver") ? run.giverId() : null;
			if (gone != null) {
				what.addProperty("leaves", gone.toString());
			}
			if (l.has("mark")) {
				what.addProperty("mark", Tale.string(l, "mark", ""));
			}
			if (!what.isEmpty() && l.has("still")) {
				// (asked again on its day, when the tale itself is long over: marks, standing, what else has ended)
				what.addProperty("still", Tale.string(l, "still", ""));
			}
			if (!what.isEmpty()) {
				Tales.state(player).later.add(new Later(Tales.day(level) + Tale.between(l.get("days"), LATER_DAYS, level.getRandom()), run.faction, what.toString()));
			}
		}
	}

	/**
	 * {@code player} told a lie ({@code lie} on the reply they chose: its {@code name}, the {@code days} until it can come
	 * out, {@code unless} something has covered it by then, and what it costs: {@code town} talk, {@code reputation},
	 * {@code hearts} with the one lied to, and who {@code returns} to the liar's door). It's marked {@code lie:<name>} now
	 * and {@code caught:<name>} if it comes out.
	 */
	static void lied(ServerLevel level, ServerPlayer player, Tales.Run run, JsonObject lie, LivingEntity who) {
		String name = Tale.string(lie, "name", "");
		if (name.isEmpty()) {
			return;
		}
		JsonObject kept = new JsonObject();
		kept.addProperty("name", name);
		kept.addProperty("unless", Tale.string(lie, "unless", ""));
		kept.add("town", filled(player, run, lie.get("town")));
		kept.addProperty("reputation", Tale.number(lie, "reputation", 0));
		kept.addProperty("hearts", Tale.number(lie, "hearts", 0));
		// (the one it was told to: one of the tale's own people is gone by then, and only the town's folk remember)
		kept.addProperty("to", who.getUUID().toString());
		// (who it brings to the liar's door when it's out)
		JsonObject back = Tale.object(lie, "returns");
		Tale tale = Tale.get(run.tale);
		JsonObject body = tale == null ? null : tale.role(Tale.string(back, "who", ""));
		if (body != null) {
			JsonObject comes = new JsonObject();
			comes.addProperty("name", run.names.getOrDefault(Tale.string(back, "who", ""), ""));
			comes.addProperty("body", body.toString());
			comes.addProperty("as", Tale.string(back, "as", "warning"));
			MutableComponent say = Tales.words(player, run, back, "say");
			comes.addProperty("say", say == null ? "" : say.getString());
			comes.add("gift", back.has("gift") ? back.get("gift") : new JsonArray());
			kept.add("returns", comes);
		}
		JsonObject what = new JsonObject();
		what.add("lie", kept);
		// (one lie, found out on the one day, for everyone who stood by while it was told)
		long out = Tales.day(level) + Tale.between(lie.get("days"), LIE_DAYS, level.getRandom());
		for (ServerPlayer p : Tales.sharers(player, run)) {
			Tales.State s = Tales.state(p);
			s.marks.add("lie:" + name);
			s.later.add(new Later(out, run.faction, what.toString()));
		}
	}

	private static List<Component> literal(@Nullable JsonElement lines) {
		List<Component> out = new ArrayList<>();
		for (String line : Tale.strings(lines)) {
			out.add(Component.literal(line));
		}
		return out;
	}

	/**
	 * What's come due of what {@code player}'s tales left for later. What can be done at once is (talk, scenes, marks, a
	 * lie found out, someone moving in); what waits on something (a letter on a runner, a grave on open ground near a
	 * town, someone moving away on nobody watching, hearts owed by someone not about) is kept apart, each until it's
	 * done or too old. One thing that needs a runner at a time.
	 */
	static void later(ServerLevel level, ServerPlayer player, Tales.State s, long today) {
		for (Later l : new ArrayList<>(s.later)) {
			if (today < l.day()) {
				continue;
			}
			JsonObject what;
			try {
				what = JsonParser.parseString(l.what()).getAsJsonObject();
			} catch (RuntimeException e) {
				s.later.remove(l);
				continue;
			}
			if (today > l.day() + LATER_KEPT) {
				s.later.remove(l);
				continue;
			}
			Faction f = l.faction() < 0 ? null : Factions.get(level.getServer(), l.faction());
			String rest = Tale.string(what, "rest", "");
			if (!rest.isEmpty()) {
				if (waiting(level, player, s, l, what, f, rest, today)) {
					return;
				}
				continue;
			}
			if (f == null && (what.has("town") || what.has("scene") || what.has("settles") || what.has("grave"))) {
				// (a tale begun where no town was, or whose town is gone since: the town nearest the player has it, or it waits)
				f = Tales.nearest(level.getServer(), player.blockPosition(), null, false);
				if (f == null) {
					continue;
				}
			}
			JsonObject settles = Tale.object(what, "settles");
			if (!settles.isEmpty() && (player.level() != level || !player.blockPosition().closerThan(f.seat(), SETTLE_NEAR)
				|| !level.isPositionEntityTicking(f.seat()))) {
				continue;
			}
			s.later.remove(l);
			if (what.has("still") && !Tales.holds(player, null, Tale.string(what, "still", ""))) {
				continue;
			}
			int town = f == null ? l.faction() : f.id();
			JsonObject lie = Tale.object(what, "lie");
			if (!lie.isEmpty()) {
				caught(level, player, s, f, lie, l.day(), town);
			}
			if (what.has("mark")) {
				String mark = Tale.string(what, "mark", "");
				s.marks.add(mark);
				learnt(player, mark);
			}
			if (f != null) {
				Overheard.townTalk(player, f.id(), literal(what.get("town")), false);
				Overheard.owe(player, f.id(), Tale.strings(what.get("scene")));
			}
			if (!settles.isEmpty()) {
				TaleCast.settle(level, Tale.object(settles, "body"), Tale.string(settles, "name", ""), f.seat(), Tale.string(settles, "as", "").equals("guard"));
			}
			for (String part : WAITS) {
				if (what.has(part)) {
					JsonObject wait = new JsonObject();
					wait.addProperty("rest", part);
					wait.add(part, what.get(part));
					s.later.add(new Later(l.day(), town, wait.toString()));
				}
			}
		}
	}

	/**
	 * One thing of {@code l} still waiting ({@code rest}: a letter, a grave, someone moving away, hearts owed): done if it
	 * can be now (and taken off), else left for another try. True if a runner was sent.
	 */
	private static boolean waiting(ServerLevel level, ServerPlayer player, Tales.State s, Later l, JsonObject what, @Nullable Faction f, String rest,
		long today) {
		if (Tale.number(what, "next", 0) > today) {
			return false;
		}
		MinecraftServer server = level.getServer();
		ServerLevel world = Factions.level(server);
		switch (rest) {
			case "letter" -> {
				if (!TaleCast.canVisit(level, player)) {
					return false;
				}
				JsonObject letter = Tale.object(what, "letter");
				// (a runner who can't set out, or doesn't find them, comes again another day; it's off the list once it's in their hand)
				Later again = again(s, l, what, today);
				Tales.Run blank = new Tales.Run("later:" + l.day(), "", "", "", "", "", l.faction(), player.blockPosition().asLong(), -1L, false,
					Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
				CompoundTag tag = new CompoundTag();
				List<Component> lines = written(player, blank, letter, literal(letter.get("lines")), tag);
				String to = Tale.string(letter, "to", "");
				ItemStack stack = Letters.sealed(new Letters.Letter(to.isEmpty() ? Post.nameOf(player).getString() : to, Component.literal(Tale.string(letter, "from", "")), lines, Optional.empty()));
				if (!tag.isEmpty()) {
					stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				}
				UUID whose = player.getUUID();
				TaleCast.sendLetter(level, player, stack, () -> {
					ServerPlayer now = server.getPlayerList().getPlayer(whose);
					if (now != null) {
						Tales.state(now).later.remove(again);
					}
				});
				return true;
			}
			case "grave" -> {
				Faction town = f != null ? f : Tales.nearest(server, player.blockPosition(), null, false);
				if (town == null || !world.isLoaded(town.seat())) {
					// (dug while somebody is near enough the town for it to be there)
					return false;
				}
				if (TaleAfter.grave(world, town, Tale.strings(what.get("grave")))) {
					s.later.remove(l);
				} else {
					again(s, l, what, today);
				}
			}
			case "leaves" -> {
				UUID id = Tales.uuid(Tale.string(what, "leaves", ""));
				if (id == null || TaleAfter.leave(world, id)) {
					s.later.remove(l);
				}
			}
			case "owed" -> {
				// (the one lied to wasn't about when it came out: they hear of it when they're next about)
				LivingEntity to = Tales.entity(world, Tales.uuid(Tale.string(what, "owed", "")));
				if (to != null) {
					s.later.remove(l);
					Recollections.remember(to, player, Recollections.Kind.LIED, Component.empty());
					int hearts = Tale.number(what, "hearts", 0);
					if (hearts != 0) {
						McaCompat.hearts(to, player, -Math.abs(hearts));
					}
				}
			}
			default -> s.later.remove(l);
		}
		return false;
	}

	/** {@code l} again, tried no sooner than tomorrow (in place of {@code l} on the list). */
	private static Later again(Tales.State s, Later l, JsonObject what, long today) {
		JsonObject next = what.deepCopy();
		next.addProperty("next", today + 1);
		Later again = new Later(l.day(), l.faction(), next.toString());
		s.later.remove(l);
		s.later.add(again);
		return again;
	}

	/** A lie's day has come: unless something covered it, it's out. The one lied to and their town think the less of the liar. */
	private static void caught(ServerLevel level, ServerPlayer player, Tales.State s, @Nullable Faction f, JsonObject lie, long day, int town) {
		String unless = Tale.string(lie, "unless", "");
		if (!unless.isEmpty() && Tales.holds(player, null, unless)) {
			return;
		}
		s.marks.add("caught:" + Tale.string(lie, "name", ""));
		JsonObject comes = Tale.object(lie, "returns");
		if (!comes.isEmpty()) {
			String as = Tale.string(comes, "as", "warning");
			JsonObject body;
			try {
				body = JsonParser.parseString(Tale.string(comes, "body", "{}")).getAsJsonObject();
			} catch (RuntimeException e) {
				body = new JsonObject();
			}
			boolean fighter = Tale.string(body, "body", "villager").equals("fighter");
			s.returns.add(new Tales.Return(Tale.string(comes, "name", ""), Tale.string(comes, "body", "{}"), Tales.day(level),
				fighter ? as : as.equals("revenge") ? "warning" : as.equals("recruit") ? "thanks" : as, Tale.string(comes, "say", ""), Tale.strings(comes.get("gift")),
				f == null ? -1 : f.id()));
		}
		LivingEntity to = Tales.entity(Factions.level(level.getServer()), Tales.uuid(Tale.string(lie, "to", "")));
		if (to != null) {
			Recollections.remember(to, player, Recollections.Kind.LIED, Component.empty());
			if (Tale.number(lie, "hearts", 0) != 0) {
				McaCompat.hearts(to, player, -Math.abs(Tale.number(lie, "hearts", 0)));
			}
		} else if (!Tale.string(lie, "to", "").isEmpty()) {
			// (not about just now: it's owed them, for when they are)
			JsonObject owed = new JsonObject();
			owed.addProperty("rest", "owed");
			owed.addProperty("owed", Tale.string(lie, "to", ""));
			owed.addProperty("hearts", Tale.number(lie, "hearts", 0));
			s.later.add(new Later(day, town, owed.toString()));
		}
		if (f != null) {
			if (Tale.number(lie, "reputation", 0) != 0) {
				Reputation.change(player, f, -Math.abs(Tale.number(lie, "reputation", 0)));
			}
			Overheard.townTalk(player, f.id(), literal(lie.get("town")), true);
		}
	}

	/** A try's place in a run's bookkeeping: the step, and which of its replies. */
	static String tried(Tales.Run run, int reply) {
		return TRIED + run.step + "#" + reply;
	}
}
