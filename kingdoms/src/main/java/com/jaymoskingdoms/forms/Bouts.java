package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Festivals;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Reputation;
import com.jaymoskingdoms.Rulers;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.forms.Duels.Outcome;
import com.jaymoskingdoms.forms.Schools.School;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.385) Festival bouts (Weapon Forms, patch 5). On a realm's festival day ({@link Festivals#keeping}) one of its schooled
 * guards, the <b>bout master</b> (chosen among the students within {@link #RANGE} blocks of the seat, the same one all day
 * while they stand), offers entry on their MCA talk menu: <b>"Enter the festival bout"</b> ({@link #ANSWER}). Once per
 * player per realm per festival day.
 * <p>
 * The bout is a short bracket of up to {@link #ROUNDS} honour duels ({@link Duels}, unranked: no day spent, no standing or
 * reputation per duel) against students of the realm, the last against the bout master. {@link #BREAK_TICKS} pass between
 * rounds, with a little tending. Lose, draw or call off a round and you are out. Each round won pays {@link #ROUND_SILVER}
 * silver coins and {@link #ROUND_STANDING} standing in your school; the final also pays {@link #FINAL_SILVER} silver coins, a golden
 * apple, {@link #FINAL_STANDING} standing and {@link #FINAL_REP} reputation with the realm.
 * <p>
 * Andy (2026-11-12): "there needs to be a screen so the fight starts when the user is ready, everytime these happen right
 * now the user is caught off guard". The bracket has its own screen ({@link BoutNet}, {@code BoutBracketScreen}): you, the
 * fighters in order with the rounds you've won, and before every round <b>Ready</b> or <b>Withdraw</b>; no round starts
 * until you say you're ready (the screen comes back every {@link #ASK_AGAIN} ticks if you shut it). The Tidings still
 * keep the lines.
 * <p>
 * And <b>tavern bouts</b> (the plan's "taverns and festivals hold school bouts"): of an evening ({@link #EVENING_FROM} to
 * {@link #EVENING_TO}) in a realm with a standing tavern, a student near it is the tavern's bout master, once a day, for
 * smaller stakes ({@link #TAVERN_FINAL_SILVER}, {@link #TAVERN_STANDING}, {@link #TAVERN_REP}; no golden apple).
 */
public final class Bouts {
	// ---- tuning ----
	/** The most rounds, the fewest (the least the realm needs to have: this many students beside the bout master, minus one). */
	static final int ROUNDS = 3, MIN_ROUNDS = 2;
	/** How far (blocks) from the seat students may be called to the bracket, and how far from it you may be before the bout is called off. */
	static final double RANGE = 40.0, LEAVE = 56.0;
	/** The pause between rounds (ticks), and the regeneration (ticks) the break gives. */
	static final int BREAK_TICKS = 100, TEND_TICKS = 80;
	/** A bracket not finished this long after it was drawn (ticks) is called off between rounds. */
	static final long GIVE_UP = 12000L;
	/** Silver coins for each round won, and for the final on top; the final's standing, reputation; each round's standing. */
	static final int ROUND_SILVER = 2, FINAL_SILVER = 6, FINAL_STANDING = 4, FINAL_REP = 12, ROUND_STANDING = 1;
	/** The MCA {@code main} menu answer (our dialogue data): "Enter the festival bout". */
	public static final String ANSWER = "jaymoskingdoms_bout";
	/** The bracket screen comes back this often (ticks) while a round waits on you. */
	static final int ASK_AGAIN = 600;
	/** A tavern's bouts: the hours (day time) they run, how far from the tavern its students are called, and its stakes. */
	static final long EVENING_FROM = 12000L, EVENING_TO = 18000L;
	static final double TAVERN_RANGE = 24.0;
	static final int TAVERN_FINAL_SILVER = 3, TAVERN_STANDING = 2, TAVERN_REP = 4;
	static final int LINES = 2;

	private static final class Run {
		final UUID player;
		final int realm;
		final List<UUID> foes;
		int round;
		long nextRound;
		boolean fighting;
		final long started;
		final net.minecraft.core.BlockPos centre;
		final boolean tavern;
		final List<Component> names;
		/** The player said they're ready for the next round; when the screen last asked. */
		boolean ready;
		long asked = Long.MIN_VALUE / 2;

		Run(UUID player, int realm, List<UUID> foes, long nextRound, net.minecraft.core.BlockPos centre, boolean tavern, List<Component> names) {
			this.centre = centre;
			this.tavern = tavern;
			this.names = names;
			this.started = nextRound;
			this.player = player;
			this.realm = realm;
			this.foes = foes;
			this.nextRound = nextRound;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	/** realm → {day, master's UUID most and least bits} the bout master chosen. */
	private static final Map<Integer, Master> MASTERS = new HashMap<>(), TAVERN_MASTERS = new HashMap<>();

	private record Master(long day, UUID who) {
	}

	private Bouts() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Bouts::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNS.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUNS.clear();
			MASTERS.clear();
			TAVERN_MASTERS.clear();
		});
		BoutNet.register();
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(BoutNet.Ready.TYPE,
			(payload, context) -> answered(context.player(), payload.ready()));
	}

	/** Whether {@code player} is in a bracket just now. */
	static boolean entered(UUID player) {
		return RUNS.containsKey(player);
	}

	// ---- the offer ---------------------------------------------------------------------------------------------------

	private static String key(Faction f, boolean tavern) {
		return (tavern ? "tavern_bout:" : "bout:") + f.id();
	}

	/** The realm's bout master today: a student near its seat, kept for the day while they stand. Null if its festival isn't on. */
	static @Nullable PathfinderMob masterOf(ServerLevel level, Faction f) {
		if (!Festivals.keeping(level, f)) {
			return null;
		}
		long day = level.getGameTime() / Duels.DAY_TICKS;
		Master chosen = MASTERS.get(f.id());
		if (chosen != null && chosen.day == day && level.getEntity(chosen.who) instanceof PathfinderMob m && m.isAlive() && !Downed.isDowned(m)) {
			return m;
		}
		return choose(MASTERS, f, day, students(level, f.seat(), RANGE));
	}

	/** Its tavern's bout master this evening: a student near a standing tavern, kept while they stand. Null off the hours, or without one. */
	static @Nullable PathfinderMob tavernMasterOf(ServerLevel level, Faction f) {
		long time = Math.floorMod(level.getOverworldClockTime(), Duels.DAY_TICKS);
		net.minecraft.core.BlockPos tavern = time < EVENING_FROM || time >= EVENING_TO || Festivals.keeping(level, f) ? null
			: com.jaymoskingdoms.Meals.tavern(level, f);
		if (tavern == null) {
			return null;
		}
		long day = level.getGameTime() / Duels.DAY_TICKS;
		Master chosen = TAVERN_MASTERS.get(f.id());
		if (chosen != null && chosen.day == day && level.getEntity(chosen.who) instanceof PathfinderMob m && m.isAlive() && !Downed.isDowned(m)
			&& m.blockPosition().closerThan(tavern, TAVERN_RANGE)) {
			return m;
		}
		return choose(TAVERN_MASTERS, f, day, students(level, tavern, TAVERN_RANGE));
	}

	private static @Nullable PathfinderMob choose(Map<Integer, Master> masters, Faction f, long day, List<PathfinderMob> students) {
		PathfinderMob best = null;
		for (PathfinderMob m : students) {
			if (best == null || m.getUUID().compareTo(best.getUUID()) < 0) {
				best = m;
			}
		}
		if (best != null) {
			masters.put(f.id(), new Master(day, best.getUUID()));
		}
		return best;
	}

	/** The realm's students near {@code centre}, free to fight. */
	private static List<PathfinderMob> students(ServerLevel level, net.minecraft.core.BlockPos centre, double range) {
		List<PathfinderMob> out = new ArrayList<>();
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(centre).inflate(range),
			m -> m.getTarget() == null && Duels.studentOf(m) != null && !Schools.isMaster(m) && !Rulers.is(m))) {
			out.add(m);
		}
		return out;
	}

	/** Whether "Enter the festival bout" shows on {@code who}: today's bout master of a realm keeping its festival. */
	public static boolean canOffer(ServerPlayer player, net.minecraft.world.entity.LivingEntity who) {
		if (!(who instanceof PathfinderMob mob) || !(mob.level() instanceof ServerLevel level) || Duels.studentOf(mob) == null) {
			return false;
		}
		Faction f = SchooledGuards.realmOf(level, mob);
		return f != null && f.type().isRealm() && (masterOf(level, f) == mob || tavernMasterOf(level, f) == mob);
	}

	/** "Enter the festival bout" ({@link #ANSWER}): the bout master draws the bracket, or says why not. */
	public static void offer(ServerPlayer player, net.minecraft.world.entity.LivingEntity who) {
		if (!canOffer(player, who) || !(who instanceof PathfinderMob master) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Faction f = SchooledGuards.realmOf(level, master);
		School school = SchooledGuards.schoolOf(level, master);
		if (f == null || school == null || Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			return;
		}
		if (RUNS.containsKey(player.getUUID())) {
			say(level, master, player, "bout.jaymoskingdoms.under_way");
			return;
		}
		boolean tavern = masterOf(level, f) != master;
		net.minecraft.core.BlockPos centre = tavern ? com.jaymoskingdoms.Meals.tavern(level, f) : f.seat();
		if (centre == null) {
			return;
		}
		if (Duels.markedToday(level, player, key(f, tavern))) {
			say(level, master, player, "bout.jaymoskingdoms.tomorrow");
			return;
		}
		if (Duels.engaged(player) || Duels.busy(player.getUUID()) || player.getHealth() < player.getMaxHealth() * Duels.START_HEALTH || Downed.isDowned(player)
			|| master.getHealth() < master.getMaxHealth() * Duels.START_HEALTH) {
			say(level, master, player, "bout.jaymoskingdoms.not_now");
			return;
		}
		List<PathfinderMob> pool = students(level, centre, tavern ? TAVERN_RANGE : RANGE);
		pool.remove(master);
		pool.removeIf(m -> m.getHealth() < m.getMaxHealth() * Duels.START_HEALTH);
		pool.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));
		int ordinary = Math.min(ROUNDS - 1, pool.size());
		if (ordinary < MIN_ROUNDS - 1) {
			say(level, master, player, "bout.jaymoskingdoms.too_few");
			return;
		}
		List<UUID> foes = new ArrayList<>();
		List<Component> names = new ArrayList<>();
		for (int i = 0; i < ordinary; i++) {
			foes.add(pool.get(i).getUUID());
			names.add(pool.get(i).getDisplayName());
		}
		foes.add(master.getUUID());
		names.add(master.getDisplayName());
		Duels.markDay(level, player, key(f, tavern));
		Run run = new Run(player.getUUID(), f.id(), foes, level.getGameTime() + BREAK_TICKS, centre, tavern, List.copyOf(names));
		RUNS.put(player.getUUID(), run);
		say(level, master, player, "bout.jaymoskingdoms.start");
		Component list = Component.empty();
		for (int i = 0; i < names.size(); i++) {
			list = list.copy().append(i == 0 ? Component.empty() : Component.literal(", ")).append(names.get(i));
		}
		Post.aside(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.bout.bracket", f.name(), foes.size(), list));
		ask(level, player, f, run);
	}

	/** The bracket screen, asking whether they're ready for the next round. */
	private static void ask(ServerLevel level, ServerPlayer player, Faction f, Run run) {
		run.asked = level.getGameTime();
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, BoutNet.Bracket.TYPE)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new BoutNet.Bracket(Component.literal(f.name()), player.getDisplayName(), run.names, run.round,
				true, run.tavern));
		} else {
			// (no screen to say it on: they're as ready as they'll be)
			run.ready = true;
		}
	}

	/** Their answer on the bracket screen. */
	private static void answered(ServerPlayer player, boolean ready) {
		Run run = RUNS.get(player.getUUID());
		if (run == null || run.fighting) {
			return;
		}
		if (ready) {
			run.ready = true;
		} else {
			out(player.level(), player, run, "tidings.jaymoskingdoms.bout.withdrew");
		}
	}

	// ---- the bracket -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (Run run : new ArrayList<>(RUNS.values())) {
			if (run.fighting) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(run.player);
			if (player == null) {
				RUNS.remove(run.player);
				continue;
			}
			ServerLevel level = player.level();
			Faction f = com.jaymoskingdoms.Factions.get(server, run.realm);
			if (f == null || f.fallen() || !player.isAlive() || Downed.isDowned(player)
				|| player.blockPosition().distSqr(run.centre) > LEAVE * LEAVE || level.getGameTime() - run.started > GIVE_UP) {
				out(level, player, run, "tidings.jaymoskingdoms.bout.walked");
				continue;
			}
			if (level.getGameTime() < run.nextRound) {
				continue;
			}
			if (!run.ready) {
				// (Andy) no round starts until they say they're ready
				if (level.getGameTime() - run.asked >= ASK_AGAIN) {
					ask(level, player, f, run);
				}
				continue;
			}
			launch(level, player, f, run);
		}
	}

	private static void launch(ServerLevel level, ServerPlayer player, Faction f, Run run) {
		PathfinderMob foe = level.getEntity(run.foes.get(run.round)) instanceof PathfinderMob m && m.isAlive() ? m : null;
		School school = foe == null ? null : SchooledGuards.schoolOf(level, foe);
		if (foe == null || school == null) {
			// their fighter isn't there to meet: out of the bracket, no blame
			out(level, player, run, "tidings.jaymoskingdoms.bout.no_show");
			return;
		}
		if (Downed.isDowned(foe) || foe.getHealth() < foe.getMaxHealth() * Duels.START_HEALTH || foe.getTarget() != null) {
			// they need a moment
			run.nextRound = level.getGameTime() + BREAK_TICKS;
			return;
		}
		if (player.getHealth() < player.getMaxHealth() * Duels.START_HEALTH) {
			// give them the break to mend
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TEND_TICKS, 1, true, false));
			run.nextRound = level.getGameTime() + BREAK_TICKS;
			return;
		}
		final int round = run.round;
		run.fighting = true;
		run.ready = false;
		boolean started = Duels.begin(level, player, foe, school, false, outcome -> result(level, player, f, run, round, outcome));
		if (!started) {
			run.fighting = false;
			run.nextRound = level.getGameTime() + BREAK_TICKS;
			return;
		}
		boolean last = round == run.foes.size() - 1;
		Post.aside(player.getUUID(), Component.translatable(last ? "tidings.jaymoskingdoms.bout.final" : "tidings.jaymoskingdoms.bout.round",
			round + 1, run.foes.size(), foe.getDisplayName()));
	}

	/** A round's duel ended. */
	private static void result(ServerLevel level, ServerPlayer player, Faction f, Run run, int round, Outcome outcome) {
		run.fighting = false;
		if (outcome != Outcome.WON) {
			out(level, player, run, outcome == Outcome.LOST ? "tidings.jaymoskingdoms.bout.lost" : "tidings.jaymoskingdoms.bout.out");
			return;
		}
		School mine = Duels.schoolFor(player, school(level, run));
		Money.pay(player, Money.worth(ROUND_SILVER));
		boolean joined = Schools.joined(player).contains(mine);
		if (joined) {
			Schools.addStanding(player, mine, ROUND_STANDING);
		}
		run.round = round + 1;
		if (run.round < run.foes.size()) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TEND_TICKS, 1, true, false));
			run.nextRound = level.getGameTime() + BREAK_TICKS;
			Post.aside(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.bout.advance", round + 1, run.foes.size()));
			ask(level, player, f, run);
			return;
		}
		RUNS.remove(run.player);
		Money.pay(player, Money.worth(run.tavern ? TAVERN_FINAL_SILVER : FINAL_SILVER));
		if (!run.tavern) {
			give(player, new ItemStack(Items.GOLDEN_APPLE));
		}
		if (joined) {
			Schools.addStanding(player, mine, run.tavern ? TAVERN_STANDING : FINAL_STANDING);
		}
		Reputation.change(player, f, run.tavern ? TAVERN_REP : FINAL_REP);
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, BoutNet.Bracket.TYPE)) {
			// the bracket, all won
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new BoutNet.Bracket(Component.literal(f.name()), player.getDisplayName(), run.names,
				run.foes.size(), false, run.tavern));
		}
		level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.6, 0.6, 0.6, 0.15);
		Post.aside(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.bout.won", f.name(), Rivals.name(mine)));
		if (level.getEntity(run.foes.get(run.foes.size() - 1)) instanceof PathfinderMob master) {
			say(level, master, player, "bout.jaymoskingdoms.won");
		}
	}

	/** The school the realm's final fighter stands for (for a player with none of their own). */
	private static School school(ServerLevel level, Run run) {
		for (int i = run.foes.size() - 1; i >= 0; i--) {
			if (level.getEntity(run.foes.get(i)) instanceof net.minecraft.world.entity.LivingEntity l) {
				School s = SchooledGuards.schoolOf(level, l);
				if (s != null) {
					return s;
				}
			}
		}
		return School.BLADE;
	}

	/** The bout is over for {@code player}: out of the bracket, with {@code tidings}. */
	private static void out(ServerLevel level, ServerPlayer player, Run run, String tidings) {
		RUNS.remove(run.player);
		Post.aside(player.getUUID(), Component.translatable(tidings));
		if (level.getEntity(run.foes.get(Math.min(run.round, run.foes.size() - 1))) instanceof PathfinderMob foe && foe.isAlive()) {
			say(level, foe, player, "bout.jaymoskingdoms.out");
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static void say(ServerLevel level, PathfinderMob who, ServerPlayer player, String key) {
		Speech.tell(level, who, player, Component.translatable(key + "." + level.getRandom().nextInt(LINES), player.getDisplayName()), Speech.GOLD);
	}
}
