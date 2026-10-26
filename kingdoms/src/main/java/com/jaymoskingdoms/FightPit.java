package com.jaymoskingdoms;

import com.jaymoskingdoms.Speakeasy.Pit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.224) Andy (2026-10-26): "You can also bet on fighters in the pit, another feature we should add. Maybe another
 * backroom dug into the speakeasy or in a makeshift ring if the room allows." Then: "Sure add that [players stepping into
 * the ring], also make the villager winners/champions bring that up in conversation."
 *
 * <p>Every den digs a fight pit off its room ({@link Speakeasy#digPit}: a backroom with a roped ring, or a makeshift
 * ring where there's only room for that). While a player is down there, the den runs bouts: the bookie calls the card
 * (two fighters, each with his hitting power, his guard and his wind, and the odds the house gives on each), takes bets
 * for {@link #BETTING_TICKS} ({@link Bookies}), then rings the bell. The two trade blows bare-knuckled until one can't go
 * on and yields (nobody's hurt for real: it's a show). Winning bets are paid at the odds. The winner stays on as the
 * house champion for the next bout, until {@link #RETIRE_WINS} wins see him retire rich; the beaten go back up to town.
 *
 * <p><b>Fighting yourself.</b> Ask the bookie for a place on the card ({@link #enter}, {@link #ENTRY} emeralds): you
 * take the newcomer's place (or a card is called for you), and at the bell you're put in your corner. Punch with an
 * empty hand (anything in it is a foul, and the bout's lost); sneak to cover up and turn half his blows; step out of the
 * ring and you've thrown it. Win and the purse is yours ({@link #PURSE} and more for each win the beaten had), and
 * he remembers it.
 *
 * <p><b>Their story.</b> Every fighter keeps his record ({@link #RECORD}, on the villager): his measure, wins and losses,
 * whether he's the champion now or retired as one. Those who've won tell you about it when you chat ({@link #tell}),
 * and MCA's chat AI knows it ({@link #prompt}).
 */
public final class FightPit {
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
	/**
	 * A player in the ring: the entry (emeralds), their wind, how they're measured for the odds (power, guard); a full
	 * punch lands for {@link #PLAYER_POWER} + 0-1 (a half-charged one for less); covering up (sneaking) turns this share
	 * of blows (percent). The purse: this much, and this much more for each win the beaten had.
	 */
	static final int ENTRY = 3, PLAYER_WIND = 20, PLAYER_POWER = 2, PLAYER_GUARD = 25, COVER_GUARD = 50, PURSE = 8, PURSE_PER_WIN = 4;
	/** A punch charged less than this counts for nothing. */
	static final float WEAK_PUNCH = 0.3F;
	/** Talk: a fighter with a win to his name brings it up in a chat this often. */
	static final float TALK_CHANCE = 0.4F;
	static final int TALK_LINES = 3;
	/** Fighters (scoreboard tag, while they're on the den's books). */
	static final String FIGHTER_TAG = "jaymoskingdoms.fighter";

	/** A fighter's measure. */
	record Stats(int power, int guard, int wind, int wins) {
		static Stats roll(RandomSource random) {
			return new Stats(1 + random.nextInt(MOST_POWER), random.nextInt(MOST_GUARD + 1), LEAST_WIND + random.nextInt(MOST_WIND - LEAST_WIND + 1), 0);
		}
	}

	/** A fighter's record, kept on him: where he fought, his measure, wins and losses, and whether he's champion now or retired as one. */
	public record Record(String pit, int power, int guard, int wind, int wins, int losses, boolean champion, boolean retired) {
		static final Codec<Record> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("pit").forGetter(Record::pit),
			Codec.INT.fieldOf("power").forGetter(Record::power),
			Codec.INT.fieldOf("guard").forGetter(Record::guard),
			Codec.INT.fieldOf("wind").forGetter(Record::wind),
			Codec.INT.optionalFieldOf("wins", 0).forGetter(Record::wins),
			Codec.INT.optionalFieldOf("losses", 0).forGetter(Record::losses),
			Codec.BOOL.optionalFieldOf("champion", false).forGetter(Record::champion),
			Codec.BOOL.optionalFieldOf("retired", false).forGetter(Record::retired)).apply(i, Record::new));

		Stats stats() {
			return new Stats(this.power, this.guard, this.wind, this.wins);
		}

		Record won() {
			return new Record(this.pit, this.power, this.guard, this.wind + WIND_PER_WIN, this.wins + 1, this.losses, true, this.retired);
		}

		Record lost() {
			return new Record(this.pit, this.power, this.guard, this.wind, this.wins, this.losses + 1, false, this.retired);
		}

		Record retire() {
			return new Record(this.pit, this.power, this.guard, this.wind, this.wins, this.losses, false, true);
		}
	}

	public static final AttachmentType<Record> RECORD = AttachmentRegistry.create(JaymosKingdoms.id("pit_record"),
		builder -> builder.persistent(Record.CODEC));

	enum Phase {
		BETTING, FIGHT, AFTER
	}

	/** A bet: who, on which fighter (0 or 1), how much, at what odds. */
	record Bet(UUID player, int fighter, int stake, double odds) {
	}

	static final class Bout {
		final int realm;
		final UUID[] fighters = new UUID[2];
		final boolean[] player = new boolean[2];
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

		/** Which corner {@code id} fights in, or -1. */
		int corner(UUID id) {
			return id.equals(this.fighters[0]) ? 0 : id.equals(this.fighters[1]) ? 1 : -1;
		}
	}

	/** Each realm's pit (realm id → it), on the Overworld. */
	static final AttachmentType<Map<String, Pit>> PITS = AttachmentRegistry.create(JaymosKingdoms.id("fight_pits"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Pit.CODEC)));

	/** Realm → its bout under way; when the next card may be called; when a dig last failed. */
	private static final Map<Integer, Bout> BOUTS = new HashMap<>();
	private static final Map<Integer, Long> NEXT = new HashMap<>(), NO_ROOM = new HashMap<>();

	private FightPit() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FightPit::tick);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof Villager v) || !v.entityTags().contains(FIGHTER_TAG)) {
				return InteractionResult.PASS;
			}
			// a fighter is only ever hit in the ring, by the one he's fighting, and only as a punch
			punch(sp, v);
			return InteractionResult.FAIL;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// bets on a bout cut short go back
			for (Bout bout : BOUTS.values()) {
				refund(server, bout);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BOUTS.clear();
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

	/** {@code fighter} is on a card now (talking to him waits until it's over). */
	static boolean onCard(UUID fighter) {
		for (Bout bout : BOUTS.values()) {
			if (bout.corner(fighter) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** The bout under way (or being bet on) in {@code realm}'s pit. */
	static @Nullable Bout bout(int realm) {
		return BOUTS.get(realm);
	}

	static Component name(ServerLevel level, @Nullable UUID fighter) {
		if (fighter == null) {
			return Component.literal("?");
		}
		if (level.getServer().getPlayerList().getPlayer(fighter) instanceof ServerPlayer p) {
			return Identities.shownName(p);
		}
		return level.getEntity(fighter) instanceof LivingEntity e ? e.getName() : Component.literal("?");
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
					call(level, f, pit, null, now);
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

	private static Vec3 corner(Pit pit, int i) {
		return Vec3.atBottomCenterOf(pit.center()).add(i == 0 ? -SQUARE_OFF : SQUARE_OFF, 0.0, 0.0);
	}

	/** The den's champion, standing in its pit (one whose record says so), or null. */
	private static @Nullable Villager champion(ServerLevel level, Pit pit) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, pit.room().inflate(1.0), v -> v.isAlive() && v.entityTags().contains(FIGHTER_TAG))) {
			Record r = v.getAttached(RECORD);
			if (r != null && r.champion()) {
				return v;
			}
		}
		return null;
	}

	/** A newcomer for the card. */
	private static @Nullable Villager newcomer(ServerLevel level, Faction f, Vec3 at) {
		Villager v = Underworld.hire(level, at, FIGHTER_TAG);
		if (v != null) {
			Stats s = Stats.roll(level.getRandom());
			v.setAttached(RECORD, new Record(f.name(), s.power(), s.guard(), s.wind(), 0, 0, false, false));
		}
		return v;
	}

	/**
	 * The card: the champion (if he's about) against a newcomer, or two newcomers; {@code challenger} (a player who asked
	 * the bookie) in the newcomer's corner.
	 */
	private static @Nullable Bout call(ServerLevel level, Faction f, Pit pit, @Nullable ServerPlayer challenger, long now) {
		RandomSource random = level.getRandom();
		Bout bout = new Bout(f.id());
		Villager champ = champion(level, pit);
		// anyone else on the books standing about (a stray from before a restart) goes back up to town
		for (Villager v : level.getEntitiesOfClass(Villager.class, pit.room().inflate(1.0), v -> v.entityTags().contains(FIGHTER_TAG))) {
			if (v != champ) {
				leave(level, f, v);
			}
		}
		for (int i = 0; i < 2; i++) {
			Vec3 at = corner(pit, i);
			if (i == 1 && challenger != null) {
				bout.fighters[1] = challenger.getUUID();
				bout.player[1] = true;
				bout.stats[1] = new Stats(PLAYER_POWER, PLAYER_GUARD, PLAYER_WIND, 0);
				bout.wind[1] = PLAYER_WIND;
				continue;
			}
			Villager v = i == 0 && champ != null ? champ : newcomer(level, f, at);
			if (v == null) {
				abort(level, bout);
				return null;
			}
			v.snapTo(at.x, at.y, at.z, i == 0 ? -90.0F : 90.0F, 0.0F);
			Record r = v.getAttached(RECORD);
			bout.fighters[i] = v.getUUID();
			bout.stats[i] = r == null ? Stats.roll(random) : r.stats();
			bout.wind[i] = bout.stats[i].wind();
		}
		price(bout, random);
		bout.at = now;
		BOUTS.put(f.id(), bout);
		announce(level, pit, bout);
		level.playSound(null, pit.center(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		Underworld.warm(level.getServer(), f, 1);
		return bout;
	}

	private static void price(Bout bout, RandomSource random) {
		double p = chance(bout.stats[0], bout.stats[1], random);
		bout.odds[0] = odds(p);
		bout.odds[1] = odds(1.0 - p);
	}

	private static void announce(ServerLevel level, Pit pit, Bout bout) {
		Villager bookie = Bookies.near(level, pit.room().inflate(16.0));
		if (bookie != null) {
			Component card = Component.translatable("fightpit.jaymoskingdoms.card." + level.getRandom().nextInt(Underworld.LINES), describe(level, bout, 0),
				describe(level, bout, 1), Math.max(1L, (BETTING_TICKS - (level.getGameTime() - bout.at)) / 20));
			Speech.say(level, bookie, card, Speech.GOLD, WATCH_RANGE);
		}
	}

	/** A fighter on the card: name, record and odds. */
	static Component describe(ServerLevel level, Bout bout, int i) {
		Stats s = bout.stats[i];
		return Component.translatable("fightpit.jaymoskingdoms.fighter", name(level, bout.fighters[i]), s.power(), s.guard(), s.wind(), s.wins(),
			String.format(java.util.Locale.ROOT, "%.1f", bout.odds[i]));
	}

	private static void run(ServerLevel level, Faction f, Pit pit, Bout bout, long now) {
		LivingEntity[] v = new LivingEntity[2];
		for (int i = 0; i < 2; i++) {
			LivingEntity found = bout.player[i] ? level.getServer().getPlayerList().getPlayer(bout.fighters[i])
				: level.getEntity(bout.fighters[i]) instanceof Villager villager ? villager : null;
			if (found == null || !found.isAlive() || found.level() != level) {
				if (bout.phase == Phase.AFTER) {
					continue;
				}
				if (bout.phase == Phase.FIGHT) {
					// gone from the fight: the other has it
					finish(level, f, bout, v, 1 - i, now, "fled");
					return;
				}
				abort(level, bout);
				return;
			}
			v[i] = found;
		}
		switch (bout.phase) {
			case BETTING -> {
				hold(v, pit, bout);
				if (now - bout.at >= BETTING_TICKS) {
					bout.phase = Phase.FIGHT;
					bout.at = now;
					for (int i = 0; i < 2; i++) {
						if (bout.player[i] && v[i] instanceof ServerPlayer p) {
							Vec3 at = corner(pit, i);
							p.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), i == 0 ? -90.0F : 90.0F, 0.0F, false);
							p.sendOverlayMessage(Component.translatable("fightpit.jaymoskingdoms.you.bell").withStyle(ChatFormatting.GOLD));
						}
					}
					level.playSound(null, pit.center(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 1.0F);
				}
			}
			case FIGHT -> {
				for (int i = 0; i < 2; i++) {
					if (bout.player[i] && v[i] instanceof ServerPlayer p) {
						if (!p.getMainHandItem().isEmpty()) {
							finish(level, f, bout, v, 1 - i, now, "foul");
							return;
						}
						if (!pit.ring().inflate(0.6).contains(p.position())) {
							finish(level, f, bout, v, 1 - i, now, "out");
							return;
						}
						p.sendOverlayMessage(Component.translatable("fightpit.jaymoskingdoms.you.wind", Math.max(0, bout.wind[i]), PLAYER_WIND,
							Math.max(0, bout.wind[1 - i])).withStyle(ChatFormatting.GOLD));
					}
				}
				keepInRing(v, pit, bout);
				if ((now - bout.at) % EXCHANGE_TICKS < 5) {
					exchange(level, f, bout, v, now);
				}
				if (bout.winner < 0 && bout.exchanges >= MOST_EXCHANGES) {
					// a draw by exhaustion: the one with more wind left takes it
					finish(level, f, bout, v, bout.wind[0] >= bout.wind[1] ? 0 : 1, now, "points");
				}
			}
			case AFTER -> {
				if (now - bout.at >= AFTER_TICKS) {
					int loser = 1 - bout.winner;
					if (!bout.player[loser] && v[loser] instanceof Villager beaten) {
						leave(level, f, beaten);
					}
					if (!bout.player[bout.winner] && v[bout.winner] instanceof Villager w) {
						Record r = w.getAttached(RECORD);
						if (r != null && r.wins() >= RETIRE_WINS) {
							Speech.say(level, w, Component.translatable("fightpit.jaymoskingdoms.retire"), Speech.GOLD, WATCH_RANGE);
							w.setAttached(RECORD, r.retire());
							leave(level, f, w);
						}
					}
					BOUTS.remove(f.id());
					NEXT.put(f.id(), now + BOUT_GAP);
				}
			}
		}
	}

	/** Off the den's books: back up to town to live there, his record kept. */
	private static void leave(ServerLevel level, Faction f, Villager v) {
		v.removeTag(FIGHTER_TAG);
		Record r = v.getAttached(RECORD);
		if (r != null && r.champion()) {
			v.setAttached(RECORD, new Record(r.pit(), r.power(), r.guard(), r.wind(), r.wins(), r.losses(), false, r.retired()));
		}
		if (r == null || r.wins() == 0 && r.losses() == 0) {
			// (one who never fought was only ever hired for the card)
			v.discard();
			return;
		}
		Vec3 out = Speakeasy.release(level, f);
		v.snapTo(out.x, out.y, out.z, v.getYRot(), 0.0F);
	}

	/** Before the bell: the villagers stand in their corners. */
	private static void hold(LivingEntity[] v, Pit pit, Bout bout) {
		for (int i = 0; i < 2; i++) {
			if (bout.player[i] || !(v[i] instanceof Villager villager)) {
				continue;
			}
			still(villager);
			Vec3 corner = corner(pit, i);
			if (villager.position().distanceToSqr(corner) > 1.0) {
				villager.snapTo(corner.x, corner.y, corner.z, villager.getYRot(), 0.0F);
			}
			villager.getLookControl().setLookAt(v[1 - i], 30.0F, 30.0F);
		}
	}

	private static void still(Villager v) {
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.getNavigation().stop();
	}

	/** In the fight: the villagers close on their man, and never leave the ring. */
	private static void keepInRing(LivingEntity[] v, Pit pit, Bout bout) {
		AABB ring = pit.ring();
		for (int i = 0; i < 2; i++) {
			if (bout.player[i] || !(v[i] instanceof Villager villager)) {
				continue;
			}
			still(villager);
			LivingEntity other = v[1 - i];
			villager.getLookControl().setLookAt(other, 30.0F, 30.0F);
			Vec3 to = other.position().subtract(villager.position());
			double d = to.horizontalDistance();
			if (d > REACH * 0.8) {
				Vec3 step = new Vec3(to.x / d, 0.0, to.z / d).scale(0.12);
				villager.setDeltaMovement(step.x, villager.getDeltaMovement().y, step.z);
			}
			if (!ring.contains(villager.position())) {
				double x = Math.max(ring.minX + 0.3, Math.min(ring.maxX - 0.3, villager.getX()));
				double z = Math.max(ring.minZ + 0.3, Math.min(ring.maxZ - 0.3, villager.getZ()));
				villager.snapTo(x, ring.minY, z, villager.getYRot(), 0.0F);
			}
		}
	}

	/** An exchange: each villager swings (in a random order), the blow turned by the other's guard or landing for power + 0-1. */
	private static void exchange(ServerLevel level, Faction f, Bout bout, LivingEntity[] v, long now) {
		if (bout.winner >= 0) {
			return;
		}
		bout.exchanges++;
		RandomSource random = level.getRandom();
		int first = random.nextInt(2);
		for (int k = 0; k < 2 && bout.winner < 0; k++) {
			int i = (first + k) % 2, j = 1 - i;
			if (bout.player[i] || v[i].distanceTo(v[j]) > REACH || random.nextFloat() >= SWING_CHANCE) {
				continue;
			}
			v[i].swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			int guard = bout.player[j] && v[j].isShiftKeyDown() ? COVER_GUARD : bout.stats[j].guard();
			if (random.nextInt(100) < guard) {
				level.playSound(null, v[j].getX(), v[j].getY(), v[j].getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.7F, 1.2F);
				continue;
			}
			land(level, f, bout, v, i, bout.stats[i].power() + random.nextInt(2), now);
		}
	}

	/** {@code i}'s blow of {@code blow} lands on the other. */
	private static void land(ServerLevel level, @Nullable Faction f, Bout bout, LivingEntity[] v, int i, int blow, long now) {
		int j = 1 - i;
		RandomSource random = level.getRandom();
		bout.wind[j] -= blow;
		Vec3 push = v[j].position().subtract(v[i].position()).normalize().scale(0.25 + 0.1 * blow);
		v[j].push(push.x, 0.1, push.z);
		v[j].needsSync = true;
		level.playSound(null, v[j].getX(), v[j].getY(), v[j].getZ(), blow >= 3 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_KNOCKBACK,
			SoundSource.NEUTRAL, 0.9F, 0.9F + random.nextFloat() * 0.2F);
		level.sendParticles(ParticleTypes.CRIT, v[j].getX(), v[j].getY() + 1.4, v[j].getZ(), 4 + blow * 2, 0.2, 0.2, 0.2, 0.1);
		if (random.nextInt(3) == 0) {
			level.playSound(null, v[j].getX(), v[j].getY(), v[j].getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.5F, 0.8F + random.nextFloat() * 0.4F);
		}
		if (bout.wind[j] <= 0) {
			finish(level, f, bout, v, i, now, "down");
		}
	}

	/** A player in the ring throws a punch at {@code target}. */
	private static void punch(ServerPlayer player, Villager target) {
		ServerLevel level = player.level();
		float charge = player.getAttackStrengthScale(0.5F);
		player.resetAttackStrengthTicker();
		Faction f = null;
		for (Map.Entry<Integer, Bout> e : BOUTS.entrySet()) {
			Bout bout = e.getValue();
			int mine = bout.corner(player.getUUID()), theirs = bout.corner(target.getUUID());
			if (bout.phase != Phase.FIGHT || mine < 0 || theirs < 0 || mine == theirs || bout.winner >= 0) {
				continue;
			}
			f = Factions.get(level.getServer(), e.getKey());
			if (charge < WEAK_PUNCH || player.distanceTo(target) > REACH + 1.2) {
				return;
			}
			if (level.getRandom().nextInt(100) < bout.stats[theirs].guard()) {
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.7F, 1.2F);
				return;
			}
			int blow = Math.max(1, Math.round((PLAYER_POWER + level.getRandom().nextInt(2)) * charge));
			LivingEntity[] v = new LivingEntity[2];
			v[mine] = player;
			v[theirs] = target;
			land(level, f, bout, v, mine, blow, level.getGameTime());
			return;
		}
	}

	/** {@code winner} has it ({@code how}: down, points, foul, out, fled): the loser yields, the bets are paid, the winner's the champion. */
	private static void finish(ServerLevel level, @Nullable Faction f, Bout bout, LivingEntity[] v, int winner, long now, String how) {
		bout.winner = winner;
		bout.phase = Phase.AFTER;
		bout.at = now;
		int loser = 1 - winner;
		RandomSource random = level.getRandom();
		if (v[loser] instanceof Villager beaten) {
			Speech.say(level, beaten, Component.translatable("fightpit.jaymoskingdoms.yield." + random.nextInt(Underworld.LINES)), Speech.WHITE, WATCH_RANGE);
			Record r = beaten.getAttached(RECORD);
			if (r != null) {
				beaten.setAttached(RECORD, r.lost());
			}
		}
		if (v[winner] instanceof Villager w) {
			Speech.say(level, w, Component.translatable("fightpit.jaymoskingdoms.win." + random.nextInt(Underworld.LINES)), Speech.GOLD, WATCH_RANGE);
			Record r = w.getAttached(RECORD);
			if (r != null) {
				w.setAttached(RECORD, r.won());
			}
		}
		LivingEntity at = v[winner] != null ? v[winner] : v[loser];
		if (at != null) {
			level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.3F);
			level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		MinecraftServer server = level.getServer();
		// a player in it: the purse, or the loss; and the one they fought remembers
		for (int i = 0; i < 2; i++) {
			if (!bout.player[i] || !(v[i] instanceof ServerPlayer p)) {
				continue;
			}
			LivingEntity foe = v[1 - i];
			if (i == winner) {
				int beatenWins = bout.stats[1 - i].wins();
				int purse = PURSE + PURSE_PER_WIN * beatenWins;
				GreatWars.owe(server, p.getUUID(), purse);
				p.sendSystemMessage(Component.translatable("fightpit.jaymoskingdoms.you.won", name(level, bout.fighters[1 - i]), purse).withStyle(ChatFormatting.GOLD));
				if (foe != null) {
					Recollections.remember(foe, p, Recollections.Kind.BEAT_YOU, Component.empty());
				}
			} else {
				p.sendSystemMessage(Component.translatable("fightpit.jaymoskingdoms.you.lost." + how, name(level, bout.fighters[1 - i])).withStyle(ChatFormatting.RED));
				if (foe != null) {
					Recollections.remember(foe, p, Recollections.Kind.LOST_TO_YOU, Component.empty());
				}
			}
		}
		for (Bet bet : bout.bets) {
			ServerPlayer player = server.getPlayerList().getPlayer(bet.player());
			if (bet.fighter() == winner) {
				int paid = (int)Math.floor(bet.stake() * bet.odds());
				GreatWars.owe(server, bet.player(), paid);
				if (player != null) {
					player.sendSystemMessage(Component.translatable("fightpit.jaymoskingdoms.bet.won", name(level, bout.fighters[winner]), paid)
						.withStyle(ChatFormatting.GOLD));
				}
			} else if (player != null) {
				player.sendSystemMessage(Component.translatable("fightpit.jaymoskingdoms.bet.lost", name(level, bout.fighters[bet.fighter()]), bet.stake())
					.withStyle(ChatFormatting.GRAY));
			}
		}
		bout.bets.clear();
	}

	/** The bout can't go on: the stakes (and a player's entry) go back. */
	private static void abort(ServerLevel level, Bout bout) {
		refund(level.getServer(), bout);
		BOUTS.remove(bout.realm);
		NEXT.put(bout.realm, level.getGameTime() + BOUT_GAP);
	}

	private static void refund(MinecraftServer server, Bout bout) {
		for (Bet bet : bout.bets) {
			GreatWars.owe(server, bet.player(), bet.stake());
		}
		for (int i = 0; i < 2; i++) {
			if (bout.player[i] && bout.phase == Phase.BETTING) {
				GreatWars.owe(server, bout.fighters[i], ENTRY);
			}
		}
		bout.bets.clear();
	}

	// ---- a player on the card ------------------------------------------------------------------------------------------

	/**
	 * (From {@link Bookies}) {@code player} asks for a place on {@code realm}'s card, {@link #ENTRY} paid (already taken):
	 * the newcomer's corner of the card being bet on, or a card called now. Returns what's said, and what was kept.
	 */
	static GreatWars.Taken enter(ServerLevel level, ServerPlayer player, Faction f) {
		Pit pit = pit(level.getServer(), f.id());
		if (pit == null) {
			return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.enter.no_pit"), 0);
		}
		Bout bout = BOUTS.get(f.id());
		if (bout != null && bout.corner(player.getUUID()) >= 0) {
			return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.enter.already"), 0);
		}
		if (bout == null) {
			bout = call(level, f, pit, player, level.getGameTime());
			return bout == null ? new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.enter.later"), 0)
				: new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.enter.called", name(level, bout.fighters[0])), ENTRY);
		}
		if (bout.phase != Phase.BETTING || bout.player[0] || bout.player[1]) {
			return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.enter.later"), 0);
		}
		// they take the newcomer's corner; his backers get their stakes back
		int corner = 1;
		if (level.getEntity(bout.fighters[corner]) instanceof Villager stood) {
			leave(level, f, stood);
		}
		List<Bet> kept = new ArrayList<>();
		for (Bet bet : bout.bets) {
			if (bet.fighter() == corner) {
				GreatWars.owe(level.getServer(), bet.player(), bet.stake());
			} else {
				kept.add(bet);
			}
		}
		bout.bets.clear();
		bout.bets.addAll(kept);
		bout.fighters[corner] = player.getUUID();
		bout.player[corner] = true;
		bout.stats[corner] = new Stats(PLAYER_POWER, PLAYER_GUARD, PLAYER_WIND, 0);
		bout.wind[corner] = PLAYER_WIND;
		price(bout, level.getRandom());
		announce(level, pit, bout);
		return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.enter.called", name(level, bout.fighters[0])), ENTRY);
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
		int mine = bout.corner(player.getUUID());
		if (mine >= 0 && mine != fighter) {
			return new GreatWars.Taken(Component.translatable("fightpit.jaymoskingdoms.bet.against_self"), 0);
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

	// ---- their story ---------------------------------------------------------------------------------------------------

	/** (A chat, from {@code McaDialogueMixin}) One with a win to his name brings it up; null if not this time. */
	public static @Nullable Component tell(Villager v, ServerPlayer player) {
		Record r = v.getAttached(RECORD);
		if (r == null || r.wins() == 0 || v.getRandom().nextFloat() >= TALK_CHANCE) {
			return null;
		}
		String state = r.champion() ? "champion" : r.retired() ? "retired" : "winner";
		return Component.translatable("fightpit.jaymoskingdoms.talk." + state + "." + v.getRandom().nextInt(TALK_LINES), r.wins(), r.losses(), r.pit(),
			Identities.shownName(player));
	}

	/** (For MCA's chat AI, from {@link ChatPrompts}) His record in the pit, in a line; empty if he has none. */
	static String prompt(Villager v) {
		Record r = v.getAttached(RECORD);
		if (r == null || r.wins() == 0 && r.losses() == 0) {
			return "";
		}
		String state = r.champion() ? " You are the pit's reigning champion and proud of it."
			: r.retired() ? " You retired from the pit as its champion, rich on your winnings, and love to tell of it."
			: r.wins() > 0 ? " You're proud of your wins and bring them up." : "";
		return "You fight bare-knuckle in the fight pit under " + r.pit() + "'s speakeasy: " + r.wins() + " wins and " + r.losses() + " losses." + state;
	}
}
