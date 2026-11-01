package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.126) Andy (2026-10-21): "Npcs can be criminals this consists of stealing, attacking other npcs, arson, stealing
 * from the player, and upon prompts of good deals (8 cod 3 emeralds) they can scam you and say so with dialogue above
 * their head and the player can confront them about it which can prompt them either giving the emeralds back or trying
 * to run and if the player downs the npc they get their money back. rep has an effect, the higher it is, the less
 * likely to be robbed, scammed, or attacked."
 *
 * <p>Some of a settlement's grown villagers are crooks ({@link #CROOK}, decided once, {@link #CROOK_CHANCE}). Now and
 * then a crook with nothing else to do sets about something ({@link Kind}):
 * <ul>
 * <li><b>stealing</b>: they slip up behind a neighbour and lift something from their pack (or their purse);</li>
 * <li><b>mugging</b>: they rough a lone neighbour up for their purse, or, at night, a player;</li>
 * <li><b>pickpocketing</b> a player: they come up behind you and help themselves to your emeralds (or a bit of a
 * stack); you may feel it ({@link #FEEL_CHANCE}), or only find it gone later;</li>
 * <li><b>arson</b> (at night, once in {@link #ARSON_GAP} per settlement): they set fire to a woodpile, a wall or a
 * haystack inside their own town (players' builds there included) and run; the settlement answers the fire as any other
 * ({@link Firefighting}).</li>
 * </ul>
 * And a crook's bargains ({@link Approaches}) look better than anyone's, and are often scams: you pay, they go off to
 * "fetch your goods", and a moment later they crow about it over their head.
 *
 * <p>A crime someone sees (a neighbour, a guard, a golem, or the victim noticing) has the witness cry out, the guards and
 * golems about set on the crook (where a realm keeps guards, one arrests them instead: {@link Arrests}), the crook
 * run, and makes them a known criminal ({@link #NOTORIETY}, the {@link #CRIMINAL_TAG}, and {@link #SEEN} for the bounty
 * board). Whatever they took from a player is owed back
 * ({@link Owed}): right-click them within {@link #CONFRONT_TICKS} to confront them, and they either hand it back or bolt;
 * down them (or kill them) and it falls out of their pockets, back to you. Hitting or killing a crook who robbed you, or a
 * known criminal, is no crime ({@link #fairGame}).
 *
 * <p>Your reputation with the settlement makes it all rarer: at {@link #REP_SAFE} the odds are {@link #MIN_ODDS} of what
 * they'd be for a stranger; a settlement that dislikes you sees more of it, up to {@link #MAX_ODDS}.
 */
public final class Crooks {
	/** The chance a grown villager of a settlement is a crook (decided once, the first time they're looked at). */
	static final float CROOK_CHANCE = 0.08F;
	/** How often crooks are looked over (ticks); the chance each free one, at each look, sets about something. */
	static final int THINK_TICKS = 100;
	static final float MISCHIEF_CHANCE = 0.05F;
	/** How far from a player villagers are looked over (blocks; only where someone is to see it). */
	static final double LOOK_RANGE = 48.0;
	/** How keen a crook is on each kind of crime: stealing, mugging a neighbour, picking a player's pocket, mugging a player (night), arson (night). */
	static final int STEAL_W = 4, MUG_W = 2, PICK_W = 3, MUG_PLAYER_W = 1, ARSON_W = 1;
	/** A settlement suffers arson at most once in this long (ticks: three days). */
	static final long ARSON_GAP = 72000L;
	/** How far a crook looks for a mark (blocks); how close they must get to lift something, to strike, to set a fire. */
	static final double MARK_RANGE = 20.0, LIFT_RANGE = 1.8, STRIKE_RANGE = 2.2, KINDLE_RANGE = 2.5;
	/** A mugging victim is "alone" with no one else of theirs this close (blocks). */
	static final double ALONE_RANGE = 8.0;
	/** A job is given up after this long (ticks). */
	static final int JOB_TICKS = 20 * 40;
	/** The pace they creep up at, and strike at. */
	static final float CREEP_SPEED = 0.5F, RUSH_SPEED = 0.7F;
	/** A crook is behind a player when the player's look and the line to the crook are at most this far apart (cosine). */
	static final double BEHIND_COS = 0.1;
	/** A mugging: blows, their damage, ticks between them; they stop once the victim is this low. */
	static final int MUG_BLOWS = 3, BLOW_GAP = 16;
	static final float BLOW_DAMAGE = 3.0F, SPARE_HEALTH = 6.0F;
	/** A mugged player's blow lifts their emeralds this often. */
	static final float BLOW_LIFTS = 0.5F;
	/** What a lifted purse holds when the mark carries nothing worth taking (emeralds, from .. to). */
	static final int PURSE_MIN = 1, PURSE_MAX = 3;
	/** From a player: at most this many emeralds; with none, up to half a stack of something else. */
	static final int PICK_EMERALDS = 4;
	/** The chance a player feels the hand in their pocket. */
	static final float FEEL_CHANCE = 0.5F;
	/** An unfelt theft is missed this long after (ticks): at least, plus up to. */
	static final int MISSED_MIN = 20 * 30, MISSED_MORE = 20 * 60;
	/** Witnesses: how far they see a crime; within this they notice whichever way they face; their gaze (cosine). */
	static final double WITNESS_RANGE = 14.0, HEAR_RANGE = 4.0, VIEW_COS = 0.2;
	/** The chance the victim of a theft notices it themselves. */
	static final float VICTIM_NOTICES = 0.35F;
	/** How far guards and golems are called from to chase a crook seen at it (blocks). */
	static final double CHASE_RANGE = 32.0;
	/** Scams: how keen a crook is to offer a "bargain"; the chance one is a scam (before reputation). */
	static final int SCAM_WEIGHT = 3;
	static final float SCAM_CHANCE = 0.7F;
	/** A scammer goes off to "fetch your goods", and crows about it this long after (ticks). */
	static final int GLOAT_TICKS = 20 * 4;
	/** Reputation: at this much the odds of being robbed, scammed or set on are at their lowest; the lowest and highest odds. */
	static final float REP_SAFE = 750.0F, MIN_ODDS = 0.1F, MAX_ODDS = 1.5F;
	/** How long after a theft or a scam you can confront the crook, and they still owe you (ticks: five minutes; then a day). */
	static final long CONFRONT_TICKS = 20L * 60 * 5, OWED_TICKS = 24000L;
	/** Confronting: the chance they give it back (base, with a weapon in hand, per 100 reputation; never under or over). */
	static final float GIVE_BACK = 0.35F, GIVE_BACK_ARMED = 0.25F, GIVE_BACK_REP = 0.04F, GIVE_BACK_MIN = 0.1F, GIVE_BACK_MAX = 0.85F;
	/**
	 * (0.7.449) Andy: "you should be able to confront who you think stole from you in the talk or interact screen.
	 * accusing the wrong person loses you hearts w them". "Accuse them of theft" ({@link #ACCUSE_ANSWER}) in MCA's menu,
	 * for {@link #OWED_TICKS} after you find something gone: the thief is confronted as by a right-click, however long
	 * ago it was; anyone else takes it badly ({@link #WRONG_HEARTS}, and they remember it).
	 */
	public static final String ACCUSE_ANSWER = "jaymoskingdoms_thief";
	static final int WRONG_HEARTS = -8;
	/** On the run: how long (ticks), how far off they make for, their pace. */
	static final int RUN_TICKS = 20 * 45;
	static final double RUN_DISTANCE = 40.0;
	static final float RUN_SPEED = 0.8F;
	/** Downed or killed, what they owe a player goes straight to them if they're this close (else it's dropped). */
	static final double RETURN_RANGE = 24.0;
	/** A bounty's price (emeralds): this much, plus this per notoriety. */
	static final int BOUNTY_BASE = 5, BOUNTY_PER = 4;
	/** Notoriety each crime that's found out adds, by kind (and a scam). */
	static final int NOTE_STEAL = 1, NOTE_MUG = 2, NOTE_PICK = 2, NOTE_ARSON = 4, NOTE_SCAM = 1;
	/** Lines each (lang {@code crook.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;

	/** On a known criminal (seen at a crime): for the bounty board and anything else that reads scoreboard tags. */
	public static final String CRIMINAL_TAG = "jaymoskingdoms_criminal";

	/** Whether this villager is a crook: decided the first time they're looked at (absent: not yet). Saved. */
	public static final AttachmentType<Boolean> CROOK = AttachmentRegistry.create(JaymosKingdoms.id("crook"),
		builder -> builder.persistent(Codec.BOOL));
	/** How many (and how bad) of their crimes have been found out. Saved. */
	public static final AttachmentType<Integer> NOTORIETY = AttachmentRegistry.create(JaymosKingdoms.id("notoriety"),
		builder -> builder.persistent(Codec.INT));
	/** The settlement their crimes were done in (faction id). Saved. */
	public static final AttachmentType<Integer> HOME = AttachmentRegistry.create(JaymosKingdoms.id("crook_home"),
		builder -> builder.persistent(Codec.INT));

	/** Told of every crime of a crook's that's found out, with the faction it was done in and a bounty to match (emeralds). */
	public interface Seen {
		void crime(LivingEntity crook, int faction, int emeralds);
	}

	/** The bounty board's listeners ({@code Bounties}). */
	public static final List<Seen> SEEN = new ArrayList<>();

	private enum Kind {
		STEAL, MUG, PICK, ARSON
	}

	/** A crook at work: on whom (or where), for which settlement, until when; a mugging's blows so far. */
	private static final class Job {
		final Villager crook;
		final Kind kind;
		final int faction;
		final @Nullable LivingEntity mark;
		final @Nullable BlockPos spot;
		final long until;
		int blows;
		long nextBlow;
		boolean spoken;

		Job(Villager crook, Kind kind, int faction, @Nullable LivingEntity mark, @Nullable BlockPos spot, long until) {
			this.crook = crook;
			this.kind = kind;
			this.faction = faction;
			this.mark = mark;
			this.spot = spot;
			this.until = until;
		}
	}

	/** What a crook carries off: whose it was (a player; null for a neighbour's), and till when it's owed. */
	private record Owed(@Nullable UUID owner, ItemStack goods, long until, long confrontBy) {
	}

	/** A crook's pockets, and whether a player has already confronted them. */
	private static final class Pockets {
		final List<Owed> owed = new ArrayList<>();
		final Set<UUID> confronted = new HashSet<>();
	}

	/** A crook running for it: from what, till when. */
	private record Run(Villager crook, Vec3 to, long until) {
	}

	/** A scammer who'll crow about it: to whom, when. */
	private record Gloat(Villager crook, UUID player, int paid, long at) {
	}

	/** A theft a player didn't feel, which they notice later. */
	private record Missed(UUID player, Component what, long at) {
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	private static final Map<UUID, Pockets> POCKETS = new HashMap<>();
	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final List<Gloat> GLOATS = new ArrayList<>();
	private static final List<Missed> MISSED = new ArrayList<>();
	/** (0.7.449) Player → when they last found they'd been robbed or cheated (game time). */
	private static final Map<UUID, Long> ROBBED_AT = new HashMap<>();
	/** (0.7.449) "player/villager": who each player has accused since they were last robbed. */
	private static final Set<String> ACCUSED = new HashSet<>();
	/** Faction → when it last suffered arson (game time). */
	private static final Map<Integer, Long> ARSON = new HashMap<>();

	private Crooks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Crooks::tick);
		// (before Approaches and MCA's own menu: a crook who owes you is confronted, not chatted with)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
				|| !(entity instanceof Villager crook) || !(world instanceof ServerLevel level) || sp.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			return confront(level, sp, crook) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		// a crook at work who's hit gives it up and runs
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof Villager crook && entity.level() instanceof ServerLevel level && JOBS.containsKey(crook.getUUID())) {
				JOBS.remove(crook.getUUID());
				Entity from = source.getEntity();
				run(level, crook, from != null ? from.position() : crook.position());
			}
		});
		// killed: whatever they carried falls out, back to its owners
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager crook && entity.level() instanceof ServerLevel level) {
				empty(level, crook);
				JOBS.remove(crook.getUUID());
				RUNS.remove(crook.getUUID());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			POCKETS.clear();
			RUNS.clear();
			GLOATS.clear();
			MISSED.clear();
			ARSON.clear();
			ROBBED_AT.clear();
			ACCUSED.clear();
		});
	}

	// ---- what others ask -----------------------------------------------------------------------------------------------

	/** {@code entity} is a crook (known or not). Server side. */
	public static boolean isCrook(Entity entity) {
		return entity instanceof Villager v && Boolean.TRUE.equals(v.getAttached(CROOK));
	}

	/** {@code entity} has been found out at a crime. Server side. */
	public static boolean isCriminal(Entity entity) {
		return notoriety(entity) > 0;
	}

	public static int notoriety(Entity entity) {
		Integer n = entity.getAttached(NOTORIETY);
		return n == null ? 0 : n;
	}

	/** The bounty {@code entity}'s crimes are worth (emeralds), or 0 for an honest one. */
	public static int bounty(Entity entity) {
		int n = notoriety(entity);
		return n <= 0 ? 0 : BOUNTY_BASE + BOUNTY_PER * n;
	}

	/** The settlement {@code entity} did their crimes in (faction id), or -1. */
	public static int homeFaction(Entity entity) {
		Integer f = entity.getAttached(HOME);
		return f == null ? -1 : f;
	}

	/** Striking or killing {@code victim} is no crime for {@code attacker}: a known criminal, or a crook who owes them. */
	public static boolean fairGame(LivingEntity victim, @Nullable Entity attacker) {
		if (isCriminal(victim)) {
			return true;
		}
		Pockets p = POCKETS.get(victim.getUUID());
		if (p == null || attacker == null) {
			return false;
		}
		for (Owed o : p.owed) {
			if (attacker.getUUID().equals(o.owner())) {
				return true;
			}
		}
		return false;
	}

	/** (From {@link Approaches}) How keen {@code crook} is to offer {@code player} a "bargain" (0 if not a crook). */
	static int bargainWeight(Mob mob, int rep) {
		return isCrook(mob) ? Math.max(1, Math.round(SCAM_WEIGHT * odds(rep))) : 0;
	}

	/** (From {@link Approaches}) Whether {@code mob}'s bargain with a player of {@code rep} is a scam. */
	static boolean scams(Mob mob, int rep, RandomSource random) {
		return isCrook(mob) && random.nextFloat() < Math.min(1.0F, SCAM_CHANCE * odds(rep));
	}

	/**
	 * (From {@link Approaches}) {@code player} paid {@code crook} {@code paid} emeralds for goods that will never come:
	 * they say they'll fetch them, walk off, and a moment later crow about it.
	 */
	static void scammed(ServerLevel level, Villager crook, ServerPlayer player, int faction, int paid) {
		long now = level.getGameTime();
		owe(crook, player.getUUID(), new ItemStack(Items.EMERALD, paid), now);
		home(crook, faction);
		Speech.tell(level, crook, player, line("fetch", level.getRandom()), Speech.WHITE);
		Vec3 away = crook.position().subtract(player.position()).normalize().scale(10.0).add(crook.position());
		BehaviorUtils.setWalkAndLookTargetMemories(crook, BlockPos.containing(away), CREEP_SPEED, 1);
		GLOATS.add(new Gloat(crook, player.getUUID(), paid, now + GLOAT_TICKS));
	}

	/** The odds of crime against a player with reputation {@code rep}, as a share of a stranger's. */
	static float odds(int rep) {
		return Mth.clamp(1.0F - rep / REP_SAFE, MIN_ODDS, MAX_ODDS);
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (!JOBS.isEmpty()) {
			for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext();) {
				Job job = it.next();
				if (!work(level, job, now)) {
					it.remove();
				}
			}
		}
		if (!RUNS.isEmpty() && now % 10 == 0) {
			runs(level, now);
		}
		if (!GLOATS.isEmpty()) {
			gloats(level, now);
		}
		if (!MISSED.isEmpty()) {
			missed(server, now);
		}
		if (server.getTickCount() % THINK_TICKS == 0) {
			think(level, now);
			if (!POCKETS.isEmpty()) {
				pockets(level, now);
			}
		}
	}

	/** Villagers near players are looked over: a crook or not (once), and a free crook may set about something. */
	private static void think(ServerLevel level, long now) {
		Set<Villager> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			seen.addAll(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(LOOK_RANGE), v -> v.isAlive() && !v.isBaby()));
		}
		RandomSource random = level.getRandom();
		for (Villager v : seen) {
			if (!v.hasAttached(CROOK)) {
				v.setAttached(CROOK, eligible(v) && random.nextFloat() < CROOK_CHANCE);
			}
			if (!Boolean.TRUE.equals(v.getAttached(CROOK)) || !free(v) || random.nextFloat() >= MISCHIEF_CHANCE) {
				continue;
			}
			Faction f = settlement(level, v);
			if (f != null) {
				start(level, v, f, now, random);
			}
		}
	}

	/** Who can be a crook: a grown villager who isn't a guard, an outlaw or a cultist already. */
	private static boolean eligible(Villager v) {
		if (v.isBaby() || Downed.isGuard(v)) {
			return false;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		return !job.equals("outlaw") && !job.equals("cultist");
	}

	/** Free to get up to something: awake, up, not trading, talking, running, panicking, tending or fighting. */
	private static boolean free(Villager v) {
		return !v.isSleeping() && !Downed.isDowned(v) && !Downed.helping(v) && !v.isTrading() && !Approaches.talking(v)
			&& !JOBS.containsKey(v.getUUID()) && !RUNS.containsKey(v.getUUID()) && !Arrests.held(v) && v.getLastHurtByMob() == null
			&& !v.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
	}

	/** The settlement {@code v} lives in (not a barbarian camp, not a fallen one), or null. */
	private static @Nullable Faction settlement(ServerLevel level, Villager v) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.around(server, level, v.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			f = Factions.at(server, level, v.blockPosition());
		}
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	/** {@code crook} picks something to do (if there's anything to do) and sets about it. */
	private static void start(ServerLevel level, Villager crook, Faction f, long now, RandomSource random) {
		boolean night = night(level);
		List<Villager> neighbours = level.getEntitiesOfClass(Villager.class, crook.getBoundingBox().inflate(MARK_RANGE),
			v -> v != crook && v.isAlive() && !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && !isCrook(v));
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, crook.getBoundingBox().inflate(MARK_RANGE),
			p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && !p.isSleeping() && !Downed.isDowned(p));
		// what's on offer, and how keen they are on each (a player's reputation makes them think twice)
		List<Job> options = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		long until = now + JOB_TICKS;
		if (!neighbours.isEmpty()) {
			Villager mark = neighbours.get(random.nextInt(neighbours.size()));
			options.add(new Job(crook, Kind.STEAL, f.id(), mark, null, until));
			weights.add(STEAL_W * 100);
			Villager lone = null;
			for (Villager v : neighbours) {
				if (level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(ALONE_RANGE), o -> o != v && o != crook && o.isAlive()).isEmpty()) {
					lone = v;
					break;
				}
			}
			if (lone != null) {
				options.add(new Job(crook, Kind.MUG, f.id(), lone, null, until));
				weights.add(MUG_W * 100);
			}
		}
		if (!players.isEmpty()) {
			ServerPlayer mark = players.get(random.nextInt(players.size()));
			float odds = odds(Reputation.get(mark, f.id()));
			options.add(new Job(crook, Kind.PICK, f.id(), mark, null, until));
			weights.add(Math.round(PICK_W * 100 * odds));
			if (night) {
				options.add(new Job(crook, Kind.MUG, f.id(), mark, null, until));
				weights.add(Math.round(MUG_PLAYER_W * 100 * odds));
			}
		}
		Long lastFire = ARSON.get(f.id());
		if (night && (lastFire == null || now - lastFire >= ARSON_GAP)) {
			BlockPos spot = kindling(level, crook, f, random);
			if (spot != null) {
				options.add(new Job(crook, Kind.ARSON, f.id(), null, spot, until));
				weights.add(ARSON_W * 100);
			}
		}
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		if (total <= 0) {
			return;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < options.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				Job job = options.get(i);
				JOBS.put(crook.getUUID(), job);
				if (job.kind == Kind.ARSON) {
					ARSON.put(f.id(), now);
				}
				return;
			}
		}
	}

	/** Something that burns, with air above it, inside {@code f}'s own settlement (players' builds there too); or null. */
	private static @Nullable BlockPos kindling(ServerLevel level, Villager crook, Faction f, RandomSource random) {
		BlockPos at = crook.blockPosition();
		for (int tries = 0; tries < 24; tries++) {
			BlockPos pos = at.offset(random.nextInt(25) - 12, random.nextInt(7) - 3, random.nextInt(25) - 12);
			BlockState state = level.getBlockState(pos);
			boolean burns = state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL) || state.is(Blocks.HAY_BLOCK);
			if (!burns || !level.getBlockState(pos.above()).isAir()) {
				continue;
			}
			// (Andy: players' blocks burn too, but only inside the town the crook lives in)
			Faction there = Factions.around(level.getServer(), level, pos, Villages.VILLAGE_RADIUS);
			if (there != null && there.id() == f.id()) {
				return pos;
			}
		}
		return null;
	}

	// ---- at work -------------------------------------------------------------------------------------------------------

	/** One tick of a crook's job; false when it's over. */
	private static boolean work(ServerLevel level, Job job, long now) {
		Villager crook = job.crook;
		if (!crook.isAlive() || crook.isRemoved() || crook.level() != level || now > job.until || Downed.isDowned(crook) || crook.isSleeping()
			|| Approaches.talking(crook)) {
			return false;
		}
		LivingEntity mark = job.mark;
		if (mark != null && (!mark.isAlive() || mark.isRemoved() || mark.level() != level || Downed.isDowned(mark)
			|| mark.distanceTo(crook) > MARK_RANGE * 1.5)) {
			return false;
		}
		Faction f = Factions.get(level.getServer(), job.faction);
		if (f == null) {
			return false;
		}
		return switch (job.kind) {
			case STEAL -> steal(level, job, f, now);
			case PICK -> pick(level, job, f, now);
			case MUG -> mug(level, job, f, now);
			case ARSON -> arson(level, job, f, now);
		};
	}

	/** Slipping up behind a neighbour; close enough, they lift something. */
	private static boolean steal(ServerLevel level, Job job, Faction f, long now) {
		Villager crook = job.crook;
		Villager mark = (Villager)job.mark;
		if (crook.distanceTo(mark) > LIFT_RANGE) {
			if (now % 10 == 0) {
				BehaviorUtils.setWalkAndLookTargetMemories(crook, new EntityTracker(mark, false), CREEP_SPEED, 1);
			}
			return true;
		}
		crook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		ItemStack taken = lift(mark, level.getRandom());
		owe(crook, null, taken, now);
		List<LivingEntity> seen = witnesses(level, crook, mark);
		if (level.getRandom().nextFloat() < VICTIM_NOTICES) {
			seen.add(0, mark);
		}
		if (!seen.isEmpty()) {
			caught(level, crook, f, seen.get(0), "thief", NOTE_STEAL);
		} else {
			stroll(crook);
		}
		return false;
	}

	/** Coming up behind a player; behind them and close, they help themselves. */
	private static boolean pick(ServerLevel level, Job job, Faction f, long now) {
		Villager crook = job.crook;
		ServerPlayer mark = (ServerPlayer)job.mark;
		if (crook.distanceTo(mark) > LIFT_RANGE) {
			if (now % 10 == 0) {
				BehaviorUtils.setWalkAndLookTargetMemories(crook, new EntityTracker(mark, false), CREEP_SPEED, 1);
			}
			return true;
		}
		if (!behind(mark, crook)) {
			// (they're facing this way: wait for them to turn, a little off)
			crook.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return true;
		}
		RandomSource random = level.getRandom();
		ItemStack taken = lift(mark, random);
		if (taken.isEmpty()) {
			// (nothing worth the trouble)
			stroll(crook);
			return false;
		}
		Component what = Component.translatable("message.jaymoskingdoms.crook.goods", taken.getCount(), taken.getHoverName());
		owe(crook, mark.getUUID(), taken, now);
		List<LivingEntity> seen = witnesses(level, crook, mark);
		if (!seen.isEmpty()) {
			// someone else saw: they cry out, and the player knows who it was
			caught(level, crook, f, seen.get(0), "thief", NOTE_PICK);
			mark.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crook.picked_seen", crook.getDisplayName(), what)
				.withStyle(ChatFormatting.RED));
			robbed(mark, now);
		} else if (random.nextFloat() < FEEL_CHANCE) {
			// felt it: they know who, and the crook makes off
			mark.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crook.picked_felt", crook.getDisplayName(), what)
				.withStyle(ChatFormatting.RED));
			robbed(mark, now);
			Speech.say(level, crook, line("caught", random), Speech.YELLOW, Approaches.SPEECH_RANGE);
			run(level, crook, mark.position());
		} else {
			MISSED.add(new Missed(mark.getUUID(), what, now + MISSED_MIN + random.nextInt(MISSED_MORE)));
			stroll(crook);
		}
		return false;
	}

	/** Roughing someone up: they close in and land a few blows, then take the purse and run. */
	private static boolean mug(ServerLevel level, Job job, Faction f, long now) {
		Villager crook = job.crook;
		LivingEntity mark = job.mark;
		RandomSource random = level.getRandom();
		if (!job.spoken && crook.distanceTo(mark) < MARK_RANGE * 0.5) {
			job.spoken = true;
			Speech.say(level, crook, line(mark instanceof Player ? "mug_player" : "mug", random), Speech.RED, Approaches.SPEECH_RANGE);
		}
		if (crook.distanceTo(mark) > STRIKE_RANGE) {
			if (now % 10 == 0) {
				BehaviorUtils.setWalkAndLookTargetMemories(crook, new EntityTracker(mark, false), RUSH_SPEED, 1);
			}
			return true;
		}
		crook.getLookControl().setLookAt(mark, 30.0F, 30.0F);
		if (now < job.nextBlow) {
			return true;
		}
		job.nextBlow = now + BLOW_GAP;
		job.blows++;
		crook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (mark.getHealth() > SPARE_HEALTH) {
			mark.hurtServer(level, level.damageSources().mobAttack(crook), BLOW_DAMAGE);
		}
		if (mark instanceof ServerPlayer player) {
			if (random.nextFloat() < BLOW_LIFTS) {
				int n = Math.min(Purse.count(player, Items.EMERALD), 1 + random.nextInt(PICK_EMERALDS));
				if (n > 0) {
					Purse.take(player, Items.EMERALD, n);
					owe(crook, player.getUUID(), new ItemStack(Items.EMERALD, n), now);
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crook.mugged", crook.getDisplayName(), n)
						.withStyle(ChatFormatting.RED));
				}
			}
		}
		if (job.blows < MUG_BLOWS && mark.getHealth() > SPARE_HEALTH) {
			return true;
		}
		if (!(mark instanceof Player)) {
			owe(crook, null, lift(mark, random), now);
		}
		// a mugging is never unseen: the victim knows who it was
		List<LivingEntity> seen = witnesses(level, crook, mark);
		caught(level, crook, f, seen.isEmpty() || mark instanceof Player ? mark : seen.get(0), "mugged", NOTE_MUG);
		return false;
	}

	/** Making for the woodpile; close enough, they set it alight and run. */
	private static boolean arson(ServerLevel level, Job job, Faction f, long now) {
		Villager crook = job.crook;
		BlockPos spot = job.spot;
		BlockPos fire = spot.above();
		if (crook.distanceToSqr(Vec3.atCenterOf(spot)) > KINDLE_RANGE * KINDLE_RANGE) {
			if (now % 10 == 0) {
				BehaviorUtils.setWalkAndLookTargetMemories(crook, spot, CREEP_SPEED, 1);
			}
			return true;
		}
		if (!level.getBlockState(fire).isAir()) {
			return false;
		}
		crook.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, fire, SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
		List<LivingEntity> seen = witnesses(level, crook, null);
		if (!seen.isEmpty()) {
			caught(level, crook, f, seen.get(0), "arson", NOTE_ARSON);
		} else {
			run(level, crook, Vec3.atCenterOf(fire));
		}
		return false;
	}

	/** Something from {@code mark}'s pack or pockets (a villager's goods, a player's emeralds or half a stack), or a little purse. */
	private static ItemStack lift(LivingEntity mark, RandomSource random) {
		if (mark instanceof ServerPlayer player) {
			int emeralds = Purse.count(player, Items.EMERALD);
			if (emeralds > 0) {
				int n = Math.min(emeralds, 1 + random.nextInt(PICK_EMERALDS));
				Purse.take(player, Items.EMERALD, n);
				return new ItemStack(Items.EMERALD, n);
			}
			List<ItemStack> stacks = new ArrayList<>();
			ItemStack held = player.getMainHandItem();
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty() && stack != held && !stack.has(DataComponents.WEAPON)) {
					stacks.add(stack);
				}
			}
			if (stacks.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack stack = stacks.get(random.nextInt(stacks.size()));
			return stack.split(Math.max(1, stack.getCount() / 2));
		}
		if (mark instanceof Villager v) {
			SimpleContainer bag = v.getInventory();
			List<Integer> full = new ArrayList<>();
			for (int slot = 0; slot < bag.getContainerSize(); slot++) {
				if (!bag.getItem(slot).isEmpty()) {
					full.add(slot);
				}
			}
			if (!full.isEmpty()) {
				ItemStack stack = bag.getItem(full.get(random.nextInt(full.size())));
				ItemStack taken = stack.split(Math.max(1, stack.getCount() / 2));
				bag.setChanged();
				return taken;
			}
		}
		return new ItemStack(Items.EMERALD, PURSE_MIN + random.nextInt(PURSE_MAX - PURSE_MIN + 1));
	}

	/** {@code crook} is behind {@code player}: the player's look and the line to the crook are well apart. */
	private static boolean behind(ServerPlayer player, LivingEntity crook) {
		Vec3 look = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		Vec3 to = crook.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4 || to.lengthSqr() < 1.0E-4) {
			return true;
		}
		return look.normalize().dot(to.normalize()) <= BEHIND_COS;
	}

	/** The settlement's people who see {@code crook} now (not the victim, not fellow crooks), nearest first. */
	private static List<LivingEntity> witnesses(ServerLevel level, Villager crook, @Nullable LivingEntity victim) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, crook.getBoundingBox().inflate(WITNESS_RANGE), LivingEntity::isAlive)) {
			boolean ours = e instanceof Folk || (e instanceof Villager v && !isCrook(v)) || (e instanceof IronGolem g && !g.isPlayerCreated());
			if (!ours || e == crook || e == victim || e.isSleeping() || Downed.isDowned(e) || e.distanceTo(crook) > WITNESS_RANGE
				|| !e.hasLineOfSight(crook)) {
				continue;
			}
			if (e.distanceTo(crook) > HEAR_RANGE) {
				Vec3 look = e.getViewVector(1.0F);
				Vec3 to = crook.getEyePosition().subtract(e.getEyePosition()).normalize();
				if (look.dot(to) < VIEW_COS) {
					continue;
				}
			}
			out.add(e);
		}
		out.sort((a, b) -> Double.compare(a.distanceToSqr(crook), b.distanceToSqr(crook)));
		return out;
	}

	// ---- found out -----------------------------------------------------------------------------------------------------

	/**
	 * {@code crook} was seen at it by {@code witness}: the witness cries out ({@code cry}), they're a known criminal from now
	 * on (the bounty board hears of it), the guards and golems about are set on them, and they run.
	 */
	private static void caught(ServerLevel level, Villager crook, Faction f, LivingEntity witness, String cry, int note) {
		RandomSource random = level.getRandom();
		if (!(witness instanceof Player)) {
			Speech.say(level, witness, line("cry." + cry, random), Speech.RED, Approaches.SPEECH_RANGE);
		}
		known(level, crook, f.id(), note);
		if (Arrests.arrest(level, crook, f, cry)) {
			// (0.7.126) a realm's guards take them alive, for the ruler to judge; they run for it meanwhile
			run(level, crook, witness.position());
			return;
		}
		for (Folk guard : level.getEntitiesOfClass(Folk.class, crook.getBoundingBox().inflate(CHASE_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && g.combatant())) {
			if (guard.getTarget() == null || !guard.getTarget().isAlive()) {
				guard.setTarget(crook);
			}
		}
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, crook.getBoundingBox().inflate(CHASE_RANGE),
			g -> g.isAlive() && !g.isPlayerCreated() && g.getTarget() == null)) {
			golem.setTarget(crook);
		}
		run(level, crook, witness.position());
	}

	/** {@code crook}'s crime in {@code faction} is found out: notoriety, the tag, the bounty board. */
	private static void known(ServerLevel level, Villager crook, int faction, int note) {
		crook.setAttached(NOTORIETY, notoriety(crook) + note);
		crook.addTag(CRIMINAL_TAG);
		home(crook, faction);
		int price = bounty(crook);
		for (Seen s : SEEN) {
			s.crime(crook, faction, price);
		}
	}

	private static void home(Villager crook, int faction) {
		crook.setAttached(HOME, faction);
	}

	/** {@code crook} makes off, away from {@code from}. */
	private static void run(ServerLevel level, Villager crook, Vec3 from) {
		Vec3 away = crook.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(level.getRandom().nextDouble() - 0.5, 0.0, level.getRandom().nextDouble() - 0.5);
		}
		Vec3 to = crook.position().add(away.normalize().scale(RUN_DISTANCE));
		RUNS.put(crook.getUUID(), new Run(crook, to, level.getGameTime() + RUN_TICKS));
		BehaviorUtils.setWalkAndLookTargetMemories(crook, BlockPos.containing(to), RUN_SPEED, 2);
	}

	/** (From {@link Arrests}) {@code crook} makes off, away from {@code from}. */
	static void flee(ServerLevel level, Villager crook, Vec3 from) {
		run(level, crook, from);
	}

	/** (From {@link Arrests}) {@code crook} stops running: they've been taken. */
	static void stopRunning(Villager crook) {
		RUNS.remove(crook.getUUID());
		crook.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	/** Done unseen: they wander off as if nothing happened. */
	private static void stroll(Villager crook) {
		crook.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	private static void runs(ServerLevel level, long now) {
		for (Iterator<Run> it = RUNS.values().iterator(); it.hasNext();) {
			Run r = it.next();
			Villager crook = r.crook();
			if (!crook.isAlive() || crook.isRemoved() || now > r.until() || crook.distanceToSqr(r.to()) < 4.0) {
				it.remove();
				continue;
			}
			if (Downed.isDowned(crook)) {
				// down: they're caught, and nobody goes on beating them
				it.remove();
				calmChasers(level, crook);
				continue;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(crook, BlockPos.containing(r.to()), RUN_SPEED, 2);
		}
	}

	private static void calmChasers(ServerLevel level, Villager crook) {
		for (Folk guard : level.getEntitiesOfClass(Folk.class, crook.getBoundingBox().inflate(CHASE_RANGE), g -> g.getTarget() == crook)) {
			guard.setTarget(null);
		}
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, crook.getBoundingBox().inflate(CHASE_RANGE), g -> g.getTarget() == crook)) {
			golem.setTarget(null);
		}
	}

	private static void gloats(ServerLevel level, long now) {
		for (Iterator<Gloat> it = GLOATS.iterator(); it.hasNext();) {
			Gloat g = it.next();
			if (now < g.at()) {
				continue;
			}
			it.remove();
			ServerPlayer cheated = level.getServer().getPlayerList().getPlayer(g.player());
			if (cheated != null) {
				robbed(cheated, now);
			}
			if (g.crook().isAlive() && !Downed.isDowned(g.crook())) {
				// aloud, over their head: anyone near hears them crow
				Speech.say(level, g.crook(), Component.translatable("crook.jaymoskingdoms.gloat." + level.getRandom().nextInt(LINES), g.paid()),
					Speech.RED, Approaches.SPEECH_RANGE);
				level.playSound(null, g.crook().getX(), g.crook().getY(), g.crook().getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.0F);
			}
		}
	}

	private static void missed(MinecraftServer server, long now) {
		for (Iterator<Missed> it = MISSED.iterator(); it.hasNext();) {
			Missed m = it.next();
			if (now < m.at()) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(m.player());
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crook.missed", m.what()).withStyle(ChatFormatting.GRAY));
				robbed(player, now);
			}
		}
	}

	// ---- what they owe -------------------------------------------------------------------------------------------------

	private static void owe(Villager crook, @Nullable UUID owner, ItemStack goods, long now) {
		if (goods.isEmpty()) {
			return;
		}
		POCKETS.computeIfAbsent(crook.getUUID(), k -> new Pockets()).owed.add(new Owed(owner, goods.copy(), now + OWED_TICKS, now + CONFRONT_TICKS));
	}

	/** Every so often: a crook who's been downed empties their pockets; what's been owed too long is spent. */
	private static void pockets(ServerLevel level, long now) {
		for (Iterator<Map.Entry<UUID, Pockets>> it = POCKETS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pockets> e = it.next();
			Pockets p = e.getValue();
			p.owed.removeIf(o -> now > o.until());
			if (p.owed.isEmpty()) {
				it.remove();
				continue;
			}
			if (level.getEntity(e.getKey()) instanceof Villager crook && Downed.isDowned(crook)) {
				it.remove();
				spill(level, crook, p);
				calmChasers(level, crook);
			}
		}
	}

	/** Killed: their pockets spill. */
	private static void empty(ServerLevel level, Villager crook) {
		Pockets p = POCKETS.remove(crook.getUUID());
		if (p != null) {
			spill(level, crook, p);
		}
	}

	/** What {@code crook} carried goes back: to its owner if they're near, else on the ground. */
	private static void spill(ServerLevel level, Villager crook, Pockets p) {
		for (Owed o : p.owed) {
			ServerPlayer owner = o.owner() == null ? null : level.getServer().getPlayerList().getPlayer(o.owner());
			ItemStack goods = o.goods().copy();
			if (owner != null && owner.level() == level && owner.distanceTo(crook) <= RETURN_RANGE) {
				owner.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crook.recovered",
					Component.translatable("message.jaymoskingdoms.crook.goods", goods.getCount(), goods.getHoverName())).withStyle(ChatFormatting.GREEN));
				give(owner, goods);
			} else {
				level.addFreshEntity(new ItemEntity(level, crook.getX(), crook.getY() + 0.5, crook.getZ(), goods));
			}
		}
	}

	/**
	 * {@code player} right-clicks {@code crook}, who robbed or cheated them a moment ago: they demand it back. The crook
	 * hands it over (more likely with a weapon in your hand, and the better the settlement thinks of you) or bolts. Either
	 * way, they're found out. False if there's nothing to confront them about.
	 */
	private static boolean confront(ServerLevel level, ServerPlayer player, Villager crook) {
		return confront(level, player, crook, false);
	}

	/** (0.7.449) {@code late}: accused from their menu, however long ago it was (while it's still owed). */
	private static boolean confront(ServerLevel level, ServerPlayer player, Villager crook, boolean late) {
		Pockets p = POCKETS.get(crook.getUUID());
		if (p == null || Downed.isDowned(crook) || p.confronted.contains(player.getUUID()) || player.distanceTo(crook) > Approaches.TALK_RANGE) {
			return false;
		}
		long now = level.getGameTime();
		List<Owed> mine = new ArrayList<>();
		for (Owed o : p.owed) {
			if (player.getUUID().equals(o.owner()) && (late || now <= o.confrontBy())) {
				mine.add(o);
			}
		}
		if (mine.isEmpty()) {
			return false;
		}
		p.confronted.add(player.getUUID());
		RandomSource random = level.getRandom();
		JOBS.remove(crook.getUUID());
		Speech.say(level, player, line("demand", random), Speech.WHITE, Approaches.SPEECH_RANGE);
		Faction f = settlement(level, crook);
		int rep = f == null ? 0 : Reputation.get(player, f.id());
		boolean armed = player.getMainHandItem().has(DataComponents.WEAPON);
		float chance = Mth.clamp(GIVE_BACK + (armed ? GIVE_BACK_ARMED : 0.0F) + rep / 100.0F * GIVE_BACK_REP, GIVE_BACK_MIN, GIVE_BACK_MAX);
		if (f != null) {
			known(level, crook, f.id(), NOTE_SCAM);
		}
		if (random.nextFloat() < chance) {
			p.owed.removeAll(mine);
			if (p.owed.isEmpty()) {
				POCKETS.remove(crook.getUUID());
			}
			for (Owed o : mine) {
				give(player, o.goods().copy());
			}
			crook.getLookControl().setLookAt(player, 30.0F, 30.0F);
			Speech.tell(level, crook, player, line("give_back", random), Speech.YELLOW);
			level.playSound(null, crook.getX(), crook.getY(), crook.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
		} else {
			Speech.say(level, crook, line("bolt", random), Speech.RED, Approaches.SPEECH_RANGE);
			run(level, crook, player.position());
		}
		return true;
	}

	// ---- (0.7.449) accusing someone of it ---------------------------------------------------------------------------

	/** {@code player} has found they were robbed or cheated: for a while they can accuse whoever they think did it. */
	private static void robbed(ServerPlayer player, long now) {
		ROBBED_AT.put(player.getUUID(), now);
		String mine = player.getUUID() + "/";
		ACCUSED.removeIf(k -> k.startsWith(mine));
	}

	/** "Accuse them of theft" shows on a grown villager while {@code player} has a theft to lay at someone's door, and hasn't yet accused them. */
	public static boolean canAccuse(ServerPlayer player, Villager villager) {
		Long at = ROBBED_AT.get(player.getUUID());
		if (at == null || player.level().getGameTime() - at > OWED_TICKS) {
			return false;
		}
		Pockets p = POCKETS.get(villager.getUUID());
		return !villager.isBaby() && !Downed.isDowned(villager) && !ACCUSED.contains(player.getUUID() + "/" + villager.getUUID())
			&& (p == null || !p.confronted.contains(player.getUUID()));
	}

	/** {@code player} accuses {@code villager} of robbing them: the thief is confronted; anyone else is hurt by it, and remembers. */
	public static void accuse(ServerPlayer player, Villager villager) {
		ServerLevel level = player.level();
		ACCUSED.add(player.getUUID() + "/" + villager.getUUID());
		if (confront(level, player, villager, true)) {
			return;
		}
		RandomSource random = level.getRandom();
		Speech.say(level, player, line("demand", random), Speech.WHITE, Approaches.SPEECH_RANGE);
		villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.tell(level, villager, player, line("wrongly_accused", random), Speech.RED);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 0.9F);
		McaCompat.hearts(villager, player, WRONG_HEARTS);
		Recollections.remember(villager, player, Recollections.Kind.ACCUSED, Component.empty());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crook.wrongly_accused", villager.getDisplayName())
			.withStyle(ChatFormatting.GRAY));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ---- little things -------------------------------------------------------------------------------------------------

	private static Component line(String key, RandomSource random) {
		return Component.translatable("crook.jaymoskingdoms." + key + "." + random.nextInt(LINES));
	}

	private static boolean night(ServerLevel level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= 12500L && t < 23500L;
	}
}
