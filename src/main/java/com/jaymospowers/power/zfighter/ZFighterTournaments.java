package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tournaments (needs Jaymo's Kingdoms): every {@link #TOURNEY_DAYS} days or so, by day, a kingdom someone is near
 * holds one. A ring of light marks the ground beside its seat; anyone who steps into it while the sign-up lasts
 * ({@link #SIGNUP_TICKS}) is entered. The bracket is filled up with the kingdom's champions ({@link ZFighterRogue}),
 * stronger each round, and fought out one bout at a time in the ring. A bout ends when a fighter is beaten down (he's
 * left on half a heart, never killed), steps or is knocked out of the ring, or time runs out (the one less hurt wins).
 * Only the two in the ring can hurt each other. Every bout won pays; the winner of the whole tournament far more.
 * <p>
 * (0.17.60, Andy's NOEA picks) A tournament is one of two kinds, picked when it opens ({@link #ROYALE_CHANCE} of them are the
 * second): the bracket above, or a last-one-standing fight ({@link ZFighterBattleRoyale}) with the same sign-up and ring
 * but everyone in the ring at once. Both keep a board ({@link #board}) that the bracket screen draws
 * ({@link ZFighterBracketNet}), shown to each fighter as he is entered and to anyone who asks ({@code /zfighter bracket}).
 */
final class ZFighterTournaments {
	static final long TOURNEY_DAYS = 3L;
	static final long TOURNEY_SPREAD = 24000L;
	static final double NEAR = 160.0;
	static final int SIGNUP_TICKS = 3600;
	static final int BOUT_TICKS = 2400;
	static final int BETWEEN = 100;
	static final double RADIUS = 7.0;
	static final double RING_OUT = RADIUS + 1.5;
	static final double RING_HIGH = 12.0;
	static final double RING_LOW = 4.0;
	/** (0.19.1) How far a kingdom's kept ring may lie from its seat and still be its ring (blocks). */
	static final int SITE_KEEP = 64;
	/** (0.19.1) When no kingdom near anyone has proper ground for a ring, how long until the next try (ticks). */
	static final long NO_SITE_RETRY = 6000L;
	static final int MAX_ENTRANTS = 8;
	static final int MIN_BRACKET = 4;
	/** Champions get this much more health and punch for each round past the first. */
	static final double ROUND_HEALTH = 0.5;
	static final double ROUND_DAMAGE = 0.3;
	static final int BOUT_TP = 100;
	static final int BOUT_EMERALDS = 4;
	static final int WIN_TP = 750;
	static final int WIN_EMERALDS = 24;
	static final long DAY = 24000L;
	static final String[] CHAMPIONS = {"krell", "toma", "shin", "bora", "yarro", "pashi", "namu", "gula"};
	static final int RING_COLOR = 0xFFE070;
	/** (0.17.60) The share of tournaments that open as a last-one-standing fight. */
	static final float ROYALE_CHANCE = 0.3F;

	/** When the next tournament may be held (overworld game time). */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosPowers.id("zfighter_next_tournament"),
		builder -> builder.persistent(Codec.LONG));
	/** (0.19.1) Each kingdom's ring: the middle of the floor laid for it (by name), used again while it stands. */
	public static final AttachmentType<Map<String, BlockPos>> SITES = AttachmentRegistry.create(JaymosPowers.id("zfighter_tournament_sites"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, BlockPos.CODEC)));

	/** A place in the bracket: a player, or one of the kingdom's champions. */
	private record Entry(@Nullable UUID player, Component name) {
		boolean champion() {
			return player == null;
		}
	}

	private static final class Bout {
		final Entry a;
		final Entry b;
		/** Each side's fighter in the world (the player, or the champion who stands for that entry). */
		UUID aId;
		UUID bId;
		final long until;

		Bout(Entry a, Entry b, long until) {
			this.a = a;
			this.b = b;
			this.until = until;
		}
	}

	/** (0.17.60) One place on the board: a bout of the bracket, its fighters once they are known, its result, whether it is on. */
	private static final class Row {
		@Nullable Entry a;
		@Nullable Entry b;
		int winner;
		boolean live;
	}

	private static final class Tourney {
		final ServerLevel level;
		final String kingdom;
		final BlockPos center;
		long signupEnd;
		final List<Entry> entrants = new ArrayList<>();
		List<Entry> bracket = List.of();
		final List<Entry> winners = new ArrayList<>();
		int round;
		int pair;
		@Nullable Bout bout;
		long nextAt;
		/** (0.17.60) A last-one-standing fight, not a bracket. */
		boolean royale;
		/** (0.17.60) The board: every round's bouts (later rounds fill as winners go through), and the bout being fought. */
		final List<List<Row>> rounds = new ArrayList<>();
		@Nullable Row row;
		@Nullable Entry champion;

		Tourney(ServerLevel level, String kingdom, BlockPos center, long signupEnd) {
			this.level = level;
			this.kingdom = kingdom;
			this.center = center;
			this.signupEnd = signupEnd;
		}

		boolean signingUp() {
			return bracket.isEmpty();
		}
	}

	private static @Nullable Tourney current;
	/** (0.17.60) The last tournament's board, kept to look at after it ends (until the next one opens). */
	private static ZFighterBracketNet.Board lastBoard = ZFighterBracketNet.Board.NONE;

	private ZFighterTournaments() {
	}

	static void init() {
		// (0.17.60) the board's packet, the last-one-standing fight, realistic mode, and the commands
		ZFighterBracketNet.register();
		ZFighterBattleRoyale.init();
		ZFighterRealism.init();
		ZFighterTourneyCommands.init();
		ServerTickEvents.END_SERVER_TICK.register(ZFighterTournaments::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (current != null && current.bout != null) {
				discardChampions(current, current.bout);
			}
			current = null;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Bout bout = current == null ? null : current.bout;
			if (bout == null || !in(bout, entity.getUUID())) {
				return true;
			}
			// in the ring only your foe can hurt you (falls and fire too)
			Entity attacker = source.getEntity();
			return attacker == null || attacker.getUUID().equals(foeOf(bout, entity.getUUID()));
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Tourney t = current;
			if (t == null || t.bout == null || !in(t.bout, entity.getUUID())) {
				return true;
			}
			entity.setHealth(1.0F);
			finish(t, t.bout, !entity.getUUID().equals(t.bout.aId), "beaten");
			return false;
		});
	}

	/** Ends sign-up now (the debug command). False if no tournament is signing up. */
	static boolean closeSignUp() {
		Tourney t = current;
		if (t == null || !t.signingUp()) {
			return false;
		}
		t.signupEnd = t.level.getGameTime();
		return true;
	}

	/** Whether this champion has a bout to fight (a champion without one goes). */
	static boolean fighting(UUID id) {
		return current != null && current.bout != null && in(current.bout, id) || ZFighterBattleRoyale.fighting(id);
	}

	private static boolean in(Bout bout, UUID id) {
		return id.equals(bout.aId) || id.equals(bout.bId);
	}

	private static UUID foeOf(Bout bout, UUID id) {
		return id.equals(bout.aId) ? bout.bId : bout.aId;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		Tourney t = current;
		if (t != null) {
			run(t, now);
			return;
		}
		if (now % 600 != 0 || !ZFighterKingdoms.present() || ZFighterBattleRoyale.active()) {
			return;
		}
		Long next = overworld.getAttached(NEXT);
		if (next == null) {
			overworld.setAttached(NEXT, now + TOURNEY_DAYS * DAY + overworld.getRandom().nextInt((int)TOURNEY_SPREAD));
			return;
		}
		if (now < next || overworld.isDarkOutside()) {
			return;
		}
		List<ZFighterKingdoms.Settlement> near = new ArrayList<>();
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(server)) {
			if ("KINGDOM".equals(s.type()) && overworld.getNearestPlayer(s.seat().getX(), s.seat().getY(), s.seat().getZ(), NEAR, false) != null) {
				near.add(s);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		RandomSource random = overworld.getRandom();
		// (0.19.1) a kingdom with no proper ground for a ring holds none; the next one near someone is tried
		Collections.shuffle(near, new java.util.Random(random.nextLong()));
		for (ZFighterKingdoms.Settlement s : near) {
			if (open(overworld, s.seat(), s.name())) {
				overworld.setAttached(NEXT, now + TOURNEY_DAYS * DAY + random.nextInt((int)TOURNEY_SPREAD));
				return;
			}
		}
		overworld.setAttached(NEXT, now + NO_SITE_RETRY);
	}

	/**
	 * A tournament opens beside {@code seat}: the ring is drawn and sign-up begins. False if one is already on, or
	 * (0.19.1) if there's no proper ground for a ring near the seat: then none is held at all.
	 */
	static boolean open(ServerLevel level, BlockPos seat, String kingdom) {
		return open(level, seat, kingdom, level.getRandom().nextFloat() < ROYALE_CHANCE);
	}

	/** (0.17.60) The same, as a bracket or ({@code royale}) as a last-one-standing fight. */
	static boolean open(ServerLevel level, BlockPos seat, String kingdom, boolean royale) {
		if (current != null || ZFighterBattleRoyale.active()) {
			return false;
		}
		// (0.11.71) a floor laid on the flattest plain ground near the seat (ZFighterArena). (0.19.1) It used to fall back
		// to any spot at all (a hillside, a roof, the river) when there was none; now there's no tournament then.
		BlockPos center = site(level, seat, kingdom);
		if (center == null) {
			return false;
		}
		current = new Tourney(level, kingdom, center, level.getGameTime() + SIGNUP_TICKS);
		current.royale = royale;
		lastBoard = ZFighterBracketNet.Board.NONE;
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(royale ? "message.jaymospowers.zfighter_royale_open"
			: "message.jaymospowers.zfighter_tournament_open", kingdom, center.getX(), center.getZ(), SIGNUP_TICKS / 1200).withStyle(ChatFormatting.GOLD), false);
		level.playSound(null, center, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 8.0F, 1.2F);
		return true;
	}

	/**
	 * (0.19.1) Where {@code kingdom}'s ring is: the floor laid for it before, if it still stands near the seat (tidied),
	 * else a new one on proper ground (kept for next time), else null. Returns the spot fighters stand on at its middle.
	 */
	private static @Nullable BlockPos site(ServerLevel level, BlockPos seat, String kingdom) {
		Map<String, BlockPos> sites = level.getAttachedOrElse(SITES, Map.of());
		BlockPos kept = sites.get(kingdom);
		if (kept != null && kept.distSqr(seat) <= (double)SITE_KEEP * SITE_KEEP && ZFighterArena.reuse(level, kept)) {
			return kept.above();
		}
		BlockPos center = ZFighterArena.build(level, seat);
		if (center == null) {
			return null;
		}
		Map<String, BlockPos> next = new HashMap<>(sites);
		next.put(kingdom, center.below());
		level.setAttached(SITES, next);
		return center;
	}

	private static void run(Tourney t, long now) {
		ServerLevel level = t.level;
		if (now % 10 == 0) {
			ring(t);
		}
		if (t.signingUp()) {
			signUp(t);
			if (now >= t.signupEnd) {
				draw(t, now);
			}
			return;
		}
		if (t.bout != null) {
			watch(t, t.bout, now);
			return;
		}
		if (now < t.nextAt) {
			return;
		}
		if (t.pair * 2 >= t.bracket.size()) {
			// the round is over: its winners go on
			t.bracket = new ArrayList<>(t.winners);
			t.winners.clear();
			t.round++;
			t.pair = 0;
			if (t.bracket.size() == 1) {
				crown(t, t.bracket.get(0));
				return;
			}
		}
		Entry a = t.bracket.get(t.pair * 2);
		Entry b = t.bracket.get(t.pair * 2 + 1);
		t.row = t.round < t.rounds.size() ? t.rounds.get(t.round).get(t.pair) : null;
		t.pair++;
		if (a.champion() && b.champion()) {
			// two champions: fought out of sight, only the result is told
			Entry won = level.getRandom().nextBoolean() ? a : b;
			t.winners.add(won);
			decided(t, won == a);
			say(t, Component.translatable("message.jaymospowers.zfighter_tournament_aside", won.name(), (won == a ? b : a).name()), ChatFormatting.GRAY);
			t.nextAt = now + 20;
			return;
		}
		begin(t, a, b, now);
	}

	/** Anyone in the ring is entered, once. */
	private static void signUp(Tourney t) {
		for (ServerPlayer player : t.level.players()) {
			if (player.isSpectator() || !player.isAlive() || !inRing(t, player.position()) || t.entrants.size() >= MAX_ENTRANTS
				|| t.entrants.stream().anyMatch(e -> player.getUUID().equals(e.player()))) {
				continue;
			}
			t.entrants.add(new Entry(player.getUUID(), player.getDisplayName()));
			say(t, Component.translatable("message.jaymospowers.zfighter_tournament_entered", player.getDisplayName(), t.kingdom), ChatFormatting.GOLD);
			// (0.17.60) the board comes up for the one who signed, and is updated for the rest
			push(t);
			ZFighterBracketNet.open(player, snapshot(t));
			t.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
		}
	}

	/** Sign-up is over: the bracket is drawn, champions filling the empty places. */
	private static void draw(Tourney t, long now) {
		if (t.entrants.isEmpty()) {
			say(t, Component.translatable("message.jaymospowers.zfighter_tournament_off", t.kingdom), ChatFormatting.GRAY);
			current = null;
			return;
		}
		if (t.royale) {
			// (0.17.60) no bracket: everyone in the ring at once, the last one standing wins
			List<UUID> players = new ArrayList<>();
			for (Entry entry : t.entrants) {
				players.add(entry.player());
			}
			current = null;
			ZFighterBattleRoyale.start(t.level, t.kingdom, t.center, players);
			return;
		}
		int size = MIN_BRACKET;
		while (size < t.entrants.size()) {
			size *= 2;
		}
		List<Entry> bracket = new ArrayList<>(t.entrants);
		List<String> names = new ArrayList<>(List.of(CHAMPIONS));
		Collections.shuffle(names, new java.util.Random(t.level.getRandom().nextLong()));
		for (int i = 0; bracket.size() < size; i++) {
			bracket.add(new Entry(null, Component.translatable("entity.jaymospowers.rogue_fighter.champion." + names.get(i % names.size()))));
		}
		Collections.shuffle(bracket, new java.util.Random(t.level.getRandom().nextLong()));
		t.bracket = bracket;
		t.round = 0;
		t.pair = 0;
		t.nextAt = now + BETWEEN;
		// (0.17.60) the board: round 0's bouts known, the later rounds empty until their winners come through
		for (int pairs = size / 2; pairs >= 1; pairs /= 2) {
			List<Row> row = new ArrayList<>();
			for (int i = 0; i < pairs; i++) {
				row.add(new Row());
			}
			t.rounds.add(row);
		}
		for (int i = 0; i < t.rounds.get(0).size(); i++) {
			t.rounds.get(0).get(i).a = bracket.get(i * 2);
			t.rounds.get(0).get(i).b = bracket.get(i * 2 + 1);
		}
		say(t, Component.translatable("message.jaymospowers.zfighter_tournament_begins", t.kingdom, t.entrants.size(), size), ChatFormatting.GOLD);
		push(t);
		for (Entry entry : t.entrants) {
			ServerPlayer player = t.level.getServer().getPlayerList().getPlayer(entry.player());
			if (player != null) {
				ZFighterBracketNet.open(player, snapshot(t));
			}
		}
	}

	/** Two step into the ring. */
	private static void begin(Tourney t, Entry a, Entry b, long now) {
		Bout bout = new Bout(a, b, now + BOUT_TICKS);
		LivingEntity fa = place(t, a, -1.0);
		LivingEntity fb = place(t, b, 1.0);
		if (fa == null || fb == null) {
			// someone isn't here: the other goes through
			if (fa != null && a.champion()) {
				fa.discard();
			}
			if (fb != null && b.champion()) {
				fb.discard();
			}
			Entry won = fa != null ? a : b;
			t.winners.add(won);
			decided(t, won == a);
			say(t, Component.translatable("message.jaymospowers.zfighter_tournament_walkover", won.name()), ChatFormatting.GRAY);
			t.nextAt = now + 20;
			return;
		}
		bout.aId = fa.getUUID();
		bout.bId = fb.getUUID();
		t.bout = bout;
		if (t.row != null) {
			t.row.live = true;
		}
		push(t);
		if (fa instanceof ZFighterRogue champion) {
			champion.setTarget(fb);
		}
		if (fb instanceof ZFighterRogue champion) {
			champion.setTarget(fa);
		}
		say(t, Component.translatable("message.jaymospowers.zfighter_tournament_bout", a.name(), b.name(), t.round + 1), ChatFormatting.YELLOW);
		t.level.playSound(null, t.center, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0F, 1.0F);
	}

	/** Puts a fighter at one side of the ring (a champion steps in fresh; a player who isn't near forfeits). */
	private static @Nullable LivingEntity place(Tourney t, Entry entry, double side) {
		ServerLevel level = t.level;
		Vec3 spot = Vec3.atBottomCenterOf(t.center).add(side * (RADIUS - 2.0), 0.0, 0.0);
		float yaw = side < 0 ? -90.0F : 90.0F;
		if (entry.champion()) {
			ZFighterRogue champion = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
			if (champion == null) {
				return null;
			}
			champion.become(ZFighterRogue.CHAMPION, entry.name());
			double health = ZFighterRogue.CHAMPION_HEALTH * (1.0 + ROUND_HEALTH * t.round);
			champion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
			champion.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ZFighterRogue.CHAMPION_DAMAGE * (1.0 + ROUND_DAMAGE * t.round));
			champion.setHealth((float)health);
			champion.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
			level.addFreshEntity(champion);
			level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 1.0, spot.z, 12, 0.3, 0.6, 0.3, 0.05);
			return champion;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.player());
		if (player == null || !player.isAlive() || player.level() != level || player.isSpectator()
			|| player.distanceToSqr(Vec3.atCenterOf(t.center)) > NEAR * NEAR) {
			return null;
		}
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, false);
		player.setHealth(player.getMaxHealth());
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_tournament_fight").withStyle(ChatFormatting.GOLD));
		return player;
	}

	/** Every tick of a bout: who has left, been knocked out of the ring, or outlasted the clock. */
	private static void watch(Tourney t, Bout bout, long now) {
		LivingEntity a = fighter(t, bout.aId);
		LivingEntity b = fighter(t, bout.bId);
		if (a == null || out(t, a)) {
			finish(t, bout, false, a == null ? "gone" : "ring_out");
			return;
		}
		if (b == null || out(t, b)) {
			finish(t, bout, true, b == null ? "gone" : "ring_out");
			return;
		}
		if (a instanceof ZFighterRogue champion && champion.getTarget() != b) {
			champion.setTarget(b);
		}
		if (b instanceof ZFighterRogue champion && champion.getTarget() != a) {
			champion.setTarget(a);
		}
		if (now >= bout.until) {
			finish(t, bout, a.getHealth() / a.getMaxHealth() >= b.getHealth() / b.getMaxHealth(), "time");
		}
	}

	private static @Nullable LivingEntity fighter(Tourney t, UUID id) {
		ServerPlayer player = t.level.getServer().getPlayerList().getPlayer(id);
		if (player != null) {
			return player.isAlive() && player.level() == t.level && !player.isSpectator() ? player : null;
		}
		return t.level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private static boolean out(Tourney t, LivingEntity fighter) {
		Vec3 c = Vec3.atBottomCenterOf(t.center);
		double dx = fighter.getX() - c.x, dz = fighter.getZ() - c.z;
		return dx * dx + dz * dz > RING_OUT * RING_OUT || fighter.getY() > c.y + RING_HIGH || fighter.getY() < c.y - RING_LOW;
	}

	/** The bout is decided: the winner goes on, both are healed, the champions leave the ring. */
	private static void finish(Tourney t, Bout bout, boolean aWon, String how) {
		t.bout = null;
		t.nextAt = t.level.getGameTime() + BETWEEN;
		Entry won = aWon ? bout.a : bout.b;
		Entry lost = aWon ? bout.b : bout.a;
		t.winners.add(won);
		decided(t, aWon);
		for (UUID id : List.of(bout.aId, bout.bId)) {
			ServerPlayer player = t.level.getServer().getPlayerList().getPlayer(id);
			if (player != null && player.isAlive()) {
				player.setHealth(player.getMaxHealth());
			}
		}
		discardChampions(t, bout);
		say(t, Component.translatable("message.jaymospowers.zfighter_tournament_won_" + how, won.name(), lost.name()), ChatFormatting.YELLOW);
		t.level.playSound(null, t.center, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 2.0F, 0.8F);
		ServerPlayer winner = won.player() == null ? null : t.level.getServer().getPlayerList().getPlayer(won.player());
		if (winner != null) {
			pay(winner, BOUT_TP, BOUT_EMERALDS);
		}
	}

	private static void discardChampions(Tourney t, Bout bout) {
		for (UUID id : List.of(bout.aId, bout.bId)) {
			if (t.level.getEntity(id) instanceof ZFighterRogue champion) {
				t.level.sendParticles(ParticleTypes.CLOUD, champion.getX(), champion.getY() + 1.0, champion.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
				champion.discard();
			}
		}
	}

	/** The last one standing. */
	private static void crown(Tourney t, Entry won) {
		current = null;
		t.champion = won;
		lastBoard = snapshot(t);
		push(t, lastBoard);
		t.level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_tournament_champion",
			won.name(), t.kingdom).withStyle(ChatFormatting.GOLD), false);
		t.level.playSound(null, t.center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 4.0F, 1.0F);
		ServerPlayer winner = won.player() == null ? null : t.level.getServer().getPlayerList().getPlayer(won.player());
		if (winner != null) {
			pay(winner, WIN_TP, WIN_EMERALDS);
			t.level.sendParticles(ParticleTypes.FIREWORK, winner.getX(), winner.getY() + 1.5, winner.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
		}
	}

	/** (0.17.60) The bout in {@code t.row} is decided; its winner goes into the next round's row, and the board is sent round. */
	private static void decided(Tourney t, boolean aWon) {
		Row row = t.row;
		t.row = null;
		if (row == null) {
			return;
		}
		row.live = false;
		row.winner = aWon ? ZFighterBracketNet.FIRST : ZFighterBracketNet.SECOND;
		Entry won = aWon ? row.a : row.b;
		int index = t.pair - 1;
		if (won != null && t.round + 1 < t.rounds.size()) {
			Row next = t.rounds.get(t.round + 1).get(index / 2);
			if (index % 2 == 0) {
				next.a = won;
			} else {
				next.b = won;
			}
		}
		push(t);
	}

	private static String wire(@Nullable Entry entry) {
		return entry == null ? "" : entry.champion() ? ZFighterBracketNet.name(entry.name()) : entry.name().getString();
	}

	/** (0.17.60) The board as it stands: the bracket's rounds, who is up, which bout is on, how long sign-up has left. */
	private static ZFighterBracketNet.Board snapshot(Tourney t) {
		List<ZFighterBracketNet.Match> matches = new ArrayList<>();
		for (int round = 0; round < t.rounds.size(); round++) {
			for (Row row : t.rounds.get(round)) {
				matches.add(new ZFighterBracketNet.Match(round, wire(row.a), wire(row.b), row.a != null && !row.a.champion(),
					row.b != null && !row.b.champion(), row.winner, row.live));
			}
		}
		List<ZFighterBracketNet.Fighter> fighters = new ArrayList<>();
		if (t.signingUp()) {
			for (Entry entry : t.entrants) {
				fighters.add(new ZFighterBracketNet.Fighter(wire(entry), true, 0, "", ""));
			}
		}
		int phase = t.champion != null ? ZFighterBracketNet.OVER : t.signingUp() ? ZFighterBracketNet.SIGN_UP : ZFighterBracketNet.FIGHTING;
		int ticks = t.signingUp() ? (int)Math.max(0L, t.signupEnd - t.level.getGameTime()) : 0;
		return new ZFighterBracketNet.Board(true, t.royale, phase, t.kingdom, Math.min(t.round, Math.max(0, t.rounds.size() - 1)), t.rounds.size(),
			ticks, t.champion == null ? "" : wire(t.champion), matches, fighters);
	}

	/** (0.17.60) Sends the board to everyone near the ring (an open screen redraws; nothing opens). */
	private static void push(Tourney t) {
		push(t, snapshot(t));
	}

	private static void push(Tourney t, ZFighterBracketNet.Board board) {
		for (ServerPlayer player : t.level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(t.center)) <= NEAR * NEAR) {
				ZFighterBracketNet.update(player, board);
			}
		}
	}

	/** (0.17.60) The board of the tournament that is on (either kind), or of the last one to end. */
	static ZFighterBracketNet.Board board() {
		if (ZFighterBattleRoyale.active()) {
			return ZFighterBattleRoyale.board();
		}
		Tourney t = current;
		return t != null ? snapshot(t) : lastBoard;
	}

	/** (0.17.60) A last-one-standing fight that has ended leaves its final board here. */
	static void remember(ZFighterBracketNet.Board board) {
		lastBoard = board;
	}

	/** A Z Fighter is paid in TP; anyone else in emeralds (a Z Fighter gets the emeralds too). */
	static void pay(ServerPlayer player, int tp, int emeralds) {
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterTraining.grant(player, tp);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_tournament_prize_tp", tp, emeralds).withStyle(ChatFormatting.GOLD));
		} else {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_tournament_prize", emeralds).withStyle(ChatFormatting.GOLD));
		}
		ItemStack stack = new ItemStack(Items.EMERALD, emeralds);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	private static boolean inRing(Tourney t, Vec3 pos) {
		Vec3 c = Vec3.atBottomCenterOf(t.center);
		double dx = pos.x - c.x, dz = pos.z - c.z;
		return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(pos.y - c.y) <= RING_LOW;
	}

	/** The ring: a circle of gold motes on the ground, brighter while sign-up is open. */
	private static void ring(Tourney t) {
		Vec3 c = Vec3.atBottomCenterOf(t.center);
		DustParticleOptions dust = new DustParticleOptions(RING_COLOR, 1.6F);
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2.0 / 48.0;
			t.level.sendParticles(dust, true, false, c.x + Math.cos(a) * RADIUS, c.y + 0.15, c.z + Math.sin(a) * RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (t.signingUp()) {
			t.level.sendParticles(ParticleTypes.END_ROD, true, false, c.x, c.y + 0.5, c.z, 6, 0.2, 2.0, 0.2, 0.02);
		}
	}

	/** Told to everyone within sight of the ring. */
	private static void say(Tourney t, Component message, ChatFormatting style) {
		for (ServerPlayer player : t.level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(t.center)) <= NEAR * NEAR) {
				player.sendSystemMessage(message.copy().withStyle(style));
			}
		}
	}
}
