package com.jaymoskingdoms;

import com.jaymoskingdoms.Speakeasy.Pit;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.224) Andy (2026-10-26): "You can also bet on fighters in the pit, another feature we should add. Maybe another
 * backroom dug into the speakeasy or in a makeshift ring if the room allows."
 *
 * <p>Every den digs a fight pit off its room ({@link Speakeasy#digPit}: a backroom with a roped ring, or a makeshift
 * ring where there's only room for that). While a player is down there, the den runs bouts: the bookie calls the card
 * (two fighters, each with his hitting power, his guard and his wind, and the odds the house gives on each), takes bets
 * for {@link #BETTING_TICKS} ({@link Bookies}), then rings the bell. The two trade blows bare-knuckled until one can't
 * go on and yields (nobody's killed: it's a show). Winning bets are paid at the odds. The winner stays on as the
 * house champion for the next bout, until {@link #RETIRE_WINS} wins see him retire rich. Each bout warms the city.
 */
final class FightPit {
	/** A den's pit is looked for once a day until it's dug (or found to have no room). */
	static final long RETRY_TICKS = 24000L;
	/** A bout: the card is called this long after the last (ticks), when a player is this near the ring; bets for this long. */
	static final long BOUT_GAP = 1200L, BETTING_TICKS = 600L;
	static final double WATCH_RANGE = 24.0;
	/** The fight: an exchange every this many ticks; each fighter swings with this chance; at most this many exchanges. */
	static final int EXCHANGE_TICKS = 10, MOST_EXCHANGES = 240;
	static final float SWING_CHANCE = 0.6F;
	/** Fighters: power 1 to this, guard (percent of blows turned) up to this, wind (blows taken) from and to. */
	static final int MOST_POWER = 3, MOST_GUARD = 35, LEAST_WIND = 12, MOST_WIND = 24;
	/** A champion's wins add this much wind each; he retires after this many. */
	static final int WIND_PER_WIN = 1, RETIRE_WINS = 5;
	/** How far apart they're squared off, and how close they must be to land a blow. */
	static final double SQUARE_OFF = 1.5, REACH = 1.8;
	/** The odds: the house keeps this share; never shorter or longer than these. Bouts simulated to set them. */
	static final double HOUSE = 0.9, SHORTEST = 1.1, LONGEST = 8.0;
	static final int ODDS_SIMULATIONS = 400;
	/** The most a player may stake on one bout. */
	static final int MOST_STAKE = 32;
	/** The loser is led out, the winner rests, this long after (ticks). */
	static final long AFTER_TICKS = 160L;
	/** Fighters (scoreboard tag). */
	static final String FIGHTER_TAG = "jaymoskingdoms.fighter";

	/** A fighter's measure. */
	record Stats(int power, int guard, int wind, int wins) {
		static Stats roll(RandomSource random) {
			return new Stats(1 + random.nextInt(MOST_POWER), random.nextInt(MOST_GUARD + 1), LEAST_WIND + random.nextInt(MOST_WIND - LEAST_WIND + 1), 0);
		}

		Stats won() {
			return new Stats(this.power, this.guard, this.wind + WIND_PER_WIN, this.wins + 1);
		}
	}

	enum Phase {
		BETTING, FIGHT, AFTER
	}

	/** A bet: who, on which fighter (0 or 1), how much, at what odds. */
	record Bet(UUID player, int fighter, int stake, double odds) {
	}

	static final class Bout {
		final int realm;
		final UUID[] fighters = new UUID[2];
		final Stats[] stats = new Stats[2];
		final double[] odds = new double[2];
		final int[] wind = new int[2];
		final List<Bet> bets = new ArrayList<>();
		Phase phase = Phase.BETTING;
		long at;
		int exchanges, winner = -1;

		Bout(int realm) {
			this.realm = realm;
		}
	}

	/** Each realm's pit (realm id → it), on the Overworld. */
	static final AttachmentType<Map<String, Pit>> PITS = AttachmentRegistry.create(JaymosKingdoms.id("fight_pits"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Pit.CODEC)));

	/** Realm → its bout under way; its champion (and his measure); when the next card may be called; when a dig last failed. */
	private static final Map<Integer, Bout> BOUTS = new HashMap<>();
	private static final Map<Integer, UUID> CHAMPION = new HashMap<>();
	private static final Map<UUID, Stats> MEASURE = new HashMap<>();
	private static final Map<Integer, Long> NEXT = new HashMap<>(), NO_ROOM = new HashMap<>();

	private FightPit() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FightPit::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// bets on a bout cut short go back
			for (Bout bout : BOUTS.values()) {
				refund(server, bout);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BOUTS.clear();
			CHAMPION.clear();
			MEASURE.clear();
			NEXT.clear();
			NO_ROOM.clear();
		});
	}

	static @Nullable Pit pit(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(PITS, Map.of()).get(Integer.toString(realm));
	}

	private static void put(MinecraftServer server, int realm, Pit pit) {
		Map<String, Pit> all = new HashMap<>(Factions.level(server).getAttachedOrElse(PITS, Map.of()));
		all.put(Integer.toString(realm), pit);
		Factions.level(server).setAttached(PITS, Map.copyOf(all));
	}

	/** (From {@link Underworld}'s rounds) {@code f}'s den digs its pit if it hasn't one. */
	static void ensure(ServerLevel level, Faction f, Underworld.Den d) {
		MinecraftServer server = level.getServer();
		if (pit(server, f.id()) != null) {
			return;
		}
		long now = level.getGameTime();
		Long failed = NO_ROOM.get(f.id());
		if (failed != null && now - failed < RETRY_TICKS) {
			return;
		}
		Pit pit = Speakeasy.digPit(level, d.hatch(), d.cell().orElse(null));
		if (pit == null) {
			NO_ROOM.put(f.id(), now);
			return;
		}
		put(server, f.id(), pit);
		JaymosKingdoms.LOGGER.debug("a fight pit dug under {} at {} (makeshift: {})", f.name(), pit.center(), pit.makeshift());
	}

	/** The bout under way (or being bet on) in {@code realm}'s pit. */
	static @Nullable Bout bout(int realm) {
		return BOUTS.get(realm);
	}

	static Component name(ServerLevel level, @Nullable UUID fighter) {
		return fighter != null && level.getEntity(fighter) instanceof Villager v ? v.getName() : Component.literal("?");
	}

	// ---- the clock -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 5 != 0) {
			return;
		}
		for (Map.Entry<String, Pit> e : level.getAttachedOrElse(PITS, Map.of()).entrySet()) {
			int realm = Integer.parseInt(e.getKey());
			Pit pit = e.getValue();
			Faction f = Factions.get(server, realm);
			Bout bout = BOUTS.get(realm);
			if (f == null || f.fallen() || !level.isPositionEntityTicking(pit.center())) {
				if (bout != null) {
					abort(level, bout);
				}
				continue;
			}
			if (bout == null) {
				if (now >= NEXT.getOrDefault(realm, 0L) && watched(level, pit)) {
					call(level, f, pit, now);
				}
				continue;
			}
			run(level, f, pit, bout, now);
		}
	}

	private static boolean watched(ServerLevel level, Pit pit) {
		return !level.getEntitiesOfClass(ServerPlayer.class, new AABB(pit.center()).inflate(WATCH_RANGE, 6.0, WATCH_RANGE),
			p -> !p.isSpectator()).isEmpty();
	}

	/** The card: the champion (if he's still about) against a newcomer, or two newcomers. */
	private static void call(ServerLevel level, Faction f, Pit pit, long now) {
		RandomSource random = level.getRandom();
		Bout bout = new Bout(f.id());
		UUID champ = CHAMPION.get(f.id());
		// strays from before a restart (no measure kept) are sent off
		for (Villager v : level.getEntitiesOfClass(Villager.class, pit.room().inflate(1.0), v -> v.entityTags().contains(FIGHTER_TAG))) {
			if (!v.getUUID().equals(champ) || !MEASURE.containsKey(v.getUUID())) {
				v.discard();
			}
		}
		Vec3 c = Vec3.atBottomCenterOf(pit.center());
		Vec3[] corners = {c.add(-SQUARE_OFF, 0.0, 0.0), c.add(SQUARE_OFF, 0.0, 0.0)};
		for (int i = 0; i < 2; i++) {
			Villager v = i == 0 && champ != null && level.getEntity(champ) instanceof Villager kept && kept.isAlive() ? kept : null;
			if (v == null) {
				v = Underworld.hire(level, corners[i], FIGHTER_TAG);
				if (v == null) {
					abort(level, bout);
					return;
				}
				MEASURE.put(v.getUUID(), Stats.roll(random));
			}
			v.snapTo(corners[i].x, corners[i].y, corners[i].z, i == 0 ? -90.0F : 90.0F, 0.0F);
			bout.fighters[i] = v.getUUID();
			bout.stats[i] = MEASURE.get(v.getUUID());
			bout.wind[i] = bout.stats[i].wind();
		}
		double p = chance(bout.stats[0], bout.stats[1], random);
		bout.odds[0] = odds(p);
		bout.odds[1] = odds(1.0 - p);
		bout.at = now;
		BOUTS.put(f.id(), bout);
		Villager bookie = Bookies.near(level, pit.room().inflate(16.0));
		Component card = Component.translatable("fightpit.jaymoskingdoms.card." + random.nextInt(Underworld.LINES), describe(level, bout, 0), describe(level, bout, 1),
			BETTING_TICKS / 20);
		if (bookie != null) {
			Speech.say(level, bookie, card, Speech.GOLD, WATCH_RANGE);
		}
		level.playSound(null, pit.center(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		Underworld.warm(level.getServer(), f, 1);
	}

	/** A fighter on the card: name, record and odds. */
	static Component describe(ServerLevel level, Bout bout, int i) {
		Stats s = bout.stats[i];
		return Component.translatable("fightpit.jaymoskingdoms.fighter", name(level, bout.fighters[i]), s.power(), s.guard(), s.wind(), s.wins(),
			String.format(java.util.Locale.ROOT, "%.1f", bout.odds[i]));
	}

	private static void run(ServerLevel level, Faction f, Pit pit, Bout bout, long now) {
		Villager[] v = new Villager[2];
		for (int i = 0; i < 2; i++) {
			if (!(level.getEntity(bout.fighters[i]) instanceof Villager found) || !found.isAlive()) {
				if (bout.phase == Phase.AFTER) {
					continue;
				}
				abort(level, bout);
				return;
			}
			v[i] = found;
		}
		switch (bout.phase) {
			case BETTING -> {
				hold(v, pit);
				if (now - bout.at >= BETTING_TICKS) {
					bout.phase = Phase.FIGHT;
					bout.at = now;
					level.playSound(null, pit.center(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 1.0F);
				}
			}
			case FIGHT -> {
				keepInRing(v, pit);
				if ((now - bout.at) % EXCHANGE_TICKS < 5) {
					exchange(level, bout, v);
				}
				if (bout.winner < 0 && bout.exchanges >= MOST_EXCHANGES) {
					// a draw by exhaustion: the one with more wind left takes it
					finish(level, f, bout, v, bout.wind[0] >= bout.wind[1] ? 0 : 1, now);
				}
			}
			case AFTER -> {
				if (now - bout.at >= AFTER_TICKS) {
					int loser = 1 - bout.winner;
					if (v[loser] != null) {
						v[loser].discard();
					}
					Villager w = v[bout.winner];
					if (w != null && MEASURE.get(w.getUUID()) != null && MEASURE.get(w.getUUID()).wins() >= RETIRE_WINS) {
						Speech.say(level, w, Component.translatable("fightpit.jaymoskingdoms.retire"), Speech.GOLD, WATCH_RANGE);
						w.discard();
						CHAMPION.remove(f.id());
					}
					MEASURE.remove(bout.fighters[loser]);
					BOUTS.remove(f.id());
					NEXT.put(f.id(), now + BOUT_GAP);
				}
			}
		}
	}

	/** Before the bell: they stand in their corners. */
	private static void hold(Villager[] v, Pit pit) {
		Vec3 c = Vec3.atBottomCenterOf(pit.center());
		for (int i = 0; i < 2; i++) {
			still(v[i]);
			Vec3 corner = c.add(i == 0 ? -SQUARE_OFF : SQUARE_OFF, 0.0, 0.0);
			if (v[i].position().distanceToSqr(corner) > 1.0) {
				v[i].snapTo(corner.x, corner.y, corner.z, v[i].getYRot(), 0.0F);
			}
			v[i].getLookControl().setLookAt(v[1 - i], 30.0F, 30.0F);
		}
	}

	private static void still(Villager v) {
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.getNavigation().stop();
	}

	/** In the fight: they close on each other, and never leave the ring. */
	private static void keepInRing(Villager[] v, Pit pit) {
		AABB ring = pit.ring();
		for (int i = 0; i < 2; i++) {
			still(v[i]);
			Villager other = v[1 - i];
			v[i].getLookControl().setLookAt(other, 30.0F, 30.0F);
			Vec3 to = other.position().subtract(v[i].position());
			double d = to.horizontalDistance();
			if (d > REACH * 0.8) {
				Vec3 step = new Vec3(to.x / d, 0.0, to.z / d).scale(0.12);
				v[i].setDeltaMovement(step.x, v[i].getDeltaMovement().y, step.z);
			}
			if (!ring.contains(v[i].position())) {
				double x = Math.max(ring.minX + 0.3, Math.min(ring.maxX - 0.3, v[i].getX()));
				double z = Math.max(ring.minZ + 0.3, Math.min(ring.maxZ - 0.3, v[i].getZ()));
				v[i].snapTo(x, ring.minY, z, v[i].getYRot(), 0.0F);
			}
		}
	}

	/** An exchange: each swings (in a random order), the blow turned by the other's guard or landing for power + 0-1. */
	private static void exchange(ServerLevel level, Bout bout, Villager[] v) {
		if (bout.winner >= 0) {
			return;
		}
		bout.exchanges++;
		RandomSource random = level.getRandom();
		int first = random.nextInt(2);
		for (int k = 0; k < 2 && bout.winner < 0; k++) {
			int i = (first + k) % 2, j = 1 - i;
			if (v[i].distanceTo(v[j]) > REACH || random.nextFloat() >= SWING_CHANCE) {
				continue;
			}
			v[i].swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			if (random.nextInt(100) < bout.stats[j].guard()) {
				level.playSound(null, v[j].getX(), v[j].getY(), v[j].getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.7F, 1.2F);
				continue;
			}
			int blow = bout.stats[i].power() + random.nextInt(2);
			bout.wind[j] -= blow;
			Vec3 push = v[j].position().subtract(v[i].position()).normalize().scale(0.25 + 0.1 * blow);
			v[j].push(push.x, 0.1, push.z);
			level.playSound(null, v[j].getX(), v[j].getY(), v[j].getZ(), blow >= 3 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_KNOCKBACK,
				SoundSource.NEUTRAL, 0.9F, 0.9F + random.nextFloat() * 0.2F);
			level.sendParticles(ParticleTypes.CRIT, v[j].getX(), v[j].getY() + 1.4, v[j].getZ(), 4 + blow * 2, 0.2, 0.2, 0.2, 0.1);
			if (random.nextInt(3) == 0) {
				level.playSound(null, v[j].getX(), v[j].getY(), v[j].getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.5F, 0.8F + random.nextFloat() * 0.4F);
			}
			if (bout.wind[j] <= 0) {
				finish(level, Factions.get(level.getServer(), bout.realm), bout, v, i, level.getGameTime());
			}
		}
	}

	/** {@code winner} has it: the loser yields, the bets are paid, the winner is the house's champion. */
	private static void finish(ServerLevel level, @Nullable Faction f, Bout bout, Villager[] v, int winner, long now) {
		bout.winner = winner;
		bout.phase = Phase.AFTER;
		bout.at = now;
		int loser = 1 - winner;
		RandomSource random = level.getRandom();
		Speech.say(level, v[loser], Component.translatable("fightpit.jaymoskingdoms.yield." + random.nextInt(Underworld.LINES)), Speech.WHITE, WATCH_RANGE);
		Speech.say(level, v[winner], Component.translatable("fightpit.jaymoskingdoms.win." + random.nextInt(Underworld.LINES)), Speech.GOLD, WATCH_RANGE);
		level.playSound(null, v[winner].getX(), v[winner].getY(), v[winner].getZ(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.3F);
		level.playSound(null, v[winner].getX(), v[winner].getY(), v[winner].getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		Stats crowned = bout.stats[winner].won();
		MEASURE.put(bout.fighters[winner], crowned);
		if (f != null) {
			CHAMPION.put(f.id(), bout.fighters[winner]);
		}
		MinecraftServer server = level.getServer();
		for (Bet bet : bout.bets) {
			ServerPlayer player = server.getPlayerList().getPlayer(bet.player());
			if (bet.fighter() == winner) {
				int paid = (int)Math.floor(bet.stake() * bet.odds());
				GreatWars.owe(server, bet.player(), paid);
				if (player != null) {
					player.sendSystemMessage(Component.translatable("fightpit.jaymoskingdoms.bet.won", name(level, bout.fighters[winner]), paid)
						.withStyle(net.minecraft.ChatFormatting.GOLD));
				}
			} else if (player != null) {
				player.sendSystemMessage(Component.translatable("fightpit.jaymoskingdoms.bet.lost", name(level, bout.fighters[bet.fighter()]), bet.stake())
					.withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
		bout.bets.clear();
	}

	/** The bout can't go on: the stakes go back, the fighters go home. */
	private static void abort(ServerLevel level, Bout bout) {
		refund(level.getServer(), bout);
		BOUTS.remove(bout.realm);
		NEXT.put(bout.realm, level.getGameTime() + BOUT_GAP);
	}

	private static void refund(MinecraftServer server, Bout bout) {
		for (Bet bet : bout.bets) {
			GreatWars.owe(server, bet.player(), bet.stake());
		}
		bout.bets.clear();
	}

	// ---- odds and bets ---------------------------------------------------------------------------------------------------

	/** The chance the first fighter wins, by fighting it out {@link #ODDS_SIMULATIONS} times. */
	static double chance(Stats a, Stats b, RandomSource random) {
		int wins = 0;
		for (int n = 0; n < ODDS_SIMULATIONS; n++) {
			int wa = a.wind(), wb = b.wind();
			for (int r = 0; r < MOST_EXCHANGES && wa > 0 && wb > 0; r++) {
				boolean aFirst = random.nextBoolean();
				for (int k = 0; k < 2 && wa > 0 && wb > 0; k++) {
					boolean aSwings = aFirst == (k == 0);
					if (random.nextFloat() >= SWING_CHANCE) {
						continue;
					}
					Stats hitter = aSwings ? a : b, taker = aSwings ? b : a;
					if (random.nextInt(100) < taker.guard()) {
						continue;
					}
					int blow = hitter.power() + random.nextInt(2);
					if (aSwings) {
						wb -= blow;
					} else {
						wa -= blow;
					}
				}
			}
			if (wb <= 0 || wa > 0 && wa >= wb) {
				wins++;
			}
		}
		return Math.max(0.02, Math.min(0.98, wins / (double)ODDS_SIMULATIONS));
	}

	/** The house's odds (what a winning stake pays, times) on a chance {@code p}. */
	static double odds(double p) {
		double o = HOUSE / p;
		return Math.round(Math.max(SHORTEST, Math.min(LONGEST, o)) * 10.0) / 10.0;
	}

	/** (From {@link Bookies}) {@code player} stakes {@code stake} (already taken) on {@code fighter}. Returns what's said, and how much was kept. */
	static GreatWars.Taken bet(ServerLevel level, ServerPlayer player, int realm, int fighter, int stake) {
		Bout bout = BOUTS.get(realm);
		if (bout == null || bout.phase != Phase.BETTING) {
			return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.bet.closed"), 0);
		}
		int already = 0;
		for (Bet b : bout.bets) {
			if (b.player().equals(player.getUUID())) {
				if (b.fighter() != fighter) {
					return new GreatWars.Taken(Component.translatable("message.jaymoskingdoms.wager.other_side"), 0);
				}
				already += b.stake();
			}
		}
		int amount = Math.min(stake, MOST_STAKE - already);
		if (amount <= 0) {
			return new GreatWars.Taken(Component.translatable("message.jaymoskingdoms.wager.most", MOST_STAKE), 0);
		}
		bout.bets.add(new Bet(player.getUUID(), fighter, amount, bout.odds[fighter]));
		return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.bet.made", amount, name(level, bout.fighters[fighter]),
			(int)Math.floor(amount * bout.odds[fighter])), amount);
	}

	/** The pit room {@code pos} is in, by realm; -1 if none. */
	static int realmAt(MinecraftServer server, BlockPos pos) {
		for (Map.Entry<String, Pit> e : Factions.level(server).getAttachedOrElse(PITS, Map.of()).entrySet()) {
			if (e.getValue().room().inflate(1.0).contains(Vec3.atCenterOf(pos))) {
				return Integer.parseInt(e.getKey());
			}
		}
		return -1;
	}
}
