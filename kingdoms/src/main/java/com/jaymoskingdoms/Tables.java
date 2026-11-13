package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Dice and cards at a table, for coin, with other players or against a villager. The state is the server's alone
 * ({@link Table}); every seated player is sent their own view of it ({@link State}: other players' hidden cards stay
 * hidden) after every change, and sends back only what they want to do ({@link Act}).
 * <p>Where: a tavern table (right-click the plate on a table's post, {@link #tavernAt}) or the speakeasy's dice-thrower
 * ({@link Underworld}). Up to {@link #MAX_SEATS} players sit. A villager standing near the table is the house when there is
 * no one else to play, or as another hand at the table; he cheats if he is a crook.
 * <p><b>Dice:</b> everyone throws two dice, the highest total takes the pot. <b>Cards:</b> twenty-one: draw to get near 21
 * without going over, the highest total that has not gone over takes the pot; the house draws to 17.
 * <p><b>Cheating</b> ({@link #cheatChance}): arm it before a throw or a draw. With good Finesse you load the dice (the best
 * of three throws) or palm the best card in the deck; with poor Finesse you fumble and are caught outright. Either way
 * every other hand at the table may catch you ({@link #catchChance}, by their Wits). The caught forfeit their stake, are put
 * out of the game and are remembered. The house's cheating is caught the same way, by the players' Wits.
 */
public final class Tables {
	public enum Game {
		DICE, CARDS
	}

	/** Phases of a table. */
	public static final int IDLE = 0, PLAY = 1, RESULT = 2;
	/** A seat's status in a state packet. */
	public static final int WAITING = 0, TURN = 1, STOOD = 2, BUST = 3, WON = 4, LOST = 5, CAUGHT = 6, PUSH = 7;
	/** The stakes on offer (emeralds' worth). */
	public static final int[] STAKES = {1, 2, 5, 10, 25};
	public static final int MAX_SEATS = 4;
	/** The actions a player sends. */
	public static final int LEAVE = 0, STAKE = 1, GAME = 2, DEAL = 3, HIT = 4, STAND = 5, CHEAT = 6;

	/** A turn at cards lasts this long; a result is shown this long before the table is open for another hand (ticks). */
	static final int TURN_TICKS = 600, RESULT_TICKS = 160;
	/** Players wander off from a table past this many blocks. */
	static final double SEAT_RANGE = 7.0;
	/** Cheating: the chance with no Finesse and each point's addition; being caught: base and each point of Wits. */
	static final float CHEAT_BASE = 0.3F, CHEAT_PER_FINESSE = 0.006F, CATCH_BASE = 0.08F, CATCH_PER_WITS = 0.0075F;
	/** A crooked house cheats this often, and an honest one never. */
	static final float HOUSE_CHEAT = 0.25F;
	/** The house stands on this total at cards. */
	static final int HOUSE_STANDS = 17;
	/** What a caught cheater's reputation with the house costs (hearts). */
	static final int CAUGHT_HEARTS = -8;

	/** One hand's holder: a player or the house. */
	static final class Seat {
		@Nullable UUID player;
		String name;
		final List<Integer> cards = new ArrayList<>();
		int status = WAITING;
		boolean armed, ante;

		Seat(@Nullable UUID player, String name) {
			this.player = player;
			this.name = name;
		}
	}

	/** A table in play. */
	static final class Table {
		final long key;
		final ServerLevel level;
		final BlockPos pos;
		Game game = Game.DICE;
		int phase = IDLE, stake = 0, pot, turn;
		long deadline;
		final List<Seat> seats = new ArrayList<>();
		@Nullable UUID houseId;
		@Nullable Seat house;
		boolean houseIn;
		List<Integer> deck = new ArrayList<>();
		Component note = Component.empty();

		Table(ServerLevel level, BlockPos pos) {
			this.level = level;
			this.pos = pos;
			this.key = pos.asLong();
		}
	}

	/** One hand on the table as a player sees it: cards (-1 hidden), the total (-1 unknown), and how it stands. */
	public record Entry(String name, boolean you, boolean house, List<Integer> cards, int total, int status, boolean armed) {
	}

	/** Server → player: the table. {@code closed}: they are out of the game. */
	public record State(boolean closed, int game, int phase, int stake, int pot, boolean canDeal, boolean yourTurn, Component note,
		List<Entry> entries) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(JaymosKingdoms.id("table_state"));
		public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.closed);
				buf.writeVarInt(p.game);
				buf.writeVarInt(p.phase);
				buf.writeVarInt(p.stake);
				buf.writeVarInt(p.pot);
				buf.writeBoolean(p.canDeal);
				buf.writeBoolean(p.yourTurn);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.note);
				buf.writeVarInt(p.entries.size());
				for (Entry e : p.entries) {
					buf.writeUtf(e.name);
					buf.writeBoolean(e.you);
					buf.writeBoolean(e.house);
					buf.writeVarInt(e.cards.size());
					for (int c : e.cards) {
						buf.writeVarInt(c + 1);
					}
					buf.writeVarInt(e.total + 1);
					buf.writeVarInt(e.status);
					buf.writeBoolean(e.armed);
				}
			},
			buf -> {
				boolean closed = buf.readBoolean();
				int game = buf.readVarInt(), phase = buf.readVarInt(), stake = buf.readVarInt(), pot = buf.readVarInt();
				boolean canDeal = buf.readBoolean(), yourTurn = buf.readBoolean();
				Component note = ComponentSerialization.STREAM_CODEC.decode(buf);
				int n = buf.readVarInt();
				List<Entry> entries = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					String name = buf.readUtf();
					boolean you = buf.readBoolean(), house = buf.readBoolean();
					int cn = buf.readVarInt();
					List<Integer> cards = new ArrayList<>();
					for (int c = 0; c < cn; c++) {
						cards.add(buf.readVarInt() - 1);
					}
					entries.add(new Entry(name, you, house, cards, buf.readVarInt() - 1, buf.readVarInt(), buf.readBoolean()));
				}
				return new State(closed, game, phase, stake, pot, canDeal, yourTurn, note, entries);
			});

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	/** Player → server: something done at the table. */
	public record Act(int action, int arg) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("table_act"));
		public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.arg + 8);
			},
			buf -> new Act(buf.readVarInt(), buf.readVarInt() - 8));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	private static final Map<Long, Table> TABLES = new HashMap<>();
	private static final Map<UUID, Table> SEATED = new HashMap<>();

	private Tables() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> act(context.player(), payload.action(), payload.arg()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.player, false));
		ServerTickEvents.END_SERVER_TICK.register(Tables::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
				|| !(level instanceof ServerLevel sl) || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			BlockPos at = hit.getBlockPos();
			if (!isTable(sl, at)) {
				return InteractionResult.PASS;
			}
			sit(sl, sp, at, null);
			return InteractionResult.SUCCESS;
		});
	}

	/** A tavern table: the plate on a post, in a standing tavern. */
	static boolean isTable(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.is(BlockTags.WOODEN_PRESSURE_PLATES) && level.getBlockState(at.below()).is(BlockTags.FENCES)
			&& Remedies.tavernAt(level, net.minecraft.world.phys.Vec3.atCenterOf(at));
	}

	/** {@code player} sits at the dice-thrower of a speakeasy. */
	static void sitAtDicer(ServerLevel level, ServerPlayer player, Villager dicer) {
		ChalkMarks.initiate(player);
		sit(level, player, dicer.blockPosition(), dicer);
	}

	private static void sit(ServerLevel level, ServerPlayer player, BlockPos at, @Nullable Villager forcedHouse) {
		Table seated = SEATED.get(player.getUUID());
		if (seated != null) {
			if (seated.key == at.asLong()) {
				send(seated, player, false);
				return;
			}
			leave(player, true);
		}
		Table t = TABLES.computeIfAbsent(at.asLong(), k -> new Table(level, at));
		if (t.seats.size() >= MAX_SEATS) {
			player.sendOverlayMessage(Component.translatable("table.jaymoskingdoms.full"));
			return;
		}
		if (forcedHouse != null) {
			t.houseId = forcedHouse.getUUID();
		}
		Seat seat = new Seat(player.getUUID(), Post.nameOf(player).getString());
		t.seats.add(seat);
		SEATED.put(player.getUUID(), t);
		findHouse(t);
		t.note = Component.translatable("table.jaymoskingdoms.sit");
		level.playSound(null, at, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.2F);
		broadcast(t);
	}

	/** The villager who plays the house: the one named already (the dice-thrower) or the nearest grown villager. */
	private static void findHouse(Table t) {
		if (t.houseId != null && t.level.getEntity(t.houseId) instanceof Villager v && v.isAlive()) {
			t.house = house(t, v);
			return;
		}
		Villager best = null;
		for (Villager v : t.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(t.pos).inflate(5.0), v -> v.isAlive() && !v.isBaby()
			&& !Downed.isDowned(v) && !Rulers.is(v))) {
			if (best == null || v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(t.pos)) < best.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(t.pos))) {
				best = v;
			}
		}
		t.houseId = best == null ? null : best.getUUID();
		t.house = best == null ? null : house(t, best);
	}

	private static Seat house(Table t, Villager v) {
		Seat s = t.house != null && t.house.name.equals(v.getName().getString()) ? t.house : new Seat(null, v.getName().getString());
		return s;
	}

	private static @Nullable Villager houseVillager(Table t) {
		return t.houseId != null && t.level.getEntity(t.houseId) instanceof Villager v && v.isAlive() ? v : null;
	}

	private static boolean crooked(@Nullable Villager v) {
		return v != null && (Crooks.isCrook(v) || v.entityTags().contains(Underworld.DICER_TAG));
	}

	// ---- actions ----------------------------------------------------------------------------------------------------

	private static void act(ServerPlayer player, int action, int arg) {
		Table t = SEATED.get(player.getUUID());
		if (t == null) {
			return;
		}
		Seat seat = seatOf(t, player);
		if (seat == null) {
			return;
		}
		switch (action) {
			case LEAVE -> leave(player, true);
			case STAKE -> {
				if (t.phase == IDLE) {
					t.stake = Math.max(0, Math.min(STAKES.length - 1, t.stake + Integer.signum(arg)));
					broadcast(t);
				}
			}
			case GAME -> {
				if (t.phase == IDLE) {
					t.game = Game.values()[Math.floorMod(arg, Game.values().length)];
					broadcast(t);
				}
			}
			case DEAL -> deal(t);
			case HIT -> {
				if (t.phase == PLAY && t.game == Game.CARDS && myTurn(t, seat)) {
					draw(t, seat);
					advance(t);
				}
			}
			case STAND -> {
				if (t.phase == PLAY && t.game == Game.CARDS && myTurn(t, seat)) {
					seat.status = STOOD;
					advance(t);
				}
			}
			case CHEAT -> {
				if (t.phase != RESULT && seat.status != CAUGHT) {
					seat.armed = !seat.armed;
					broadcast(t);
				}
			}
			default -> {
			}
		}
	}

	private static @Nullable Seat seatOf(Table t, ServerPlayer p) {
		for (Seat s : t.seats) {
			if (p.getUUID().equals(s.player)) {
				return s;
			}
		}
		return null;
	}

	private static boolean myTurn(Table t, Seat s) {
		return t.turn >= 0 && t.turn < t.seats.size() && t.seats.get(t.turn) == s && s.status == TURN;
	}

	/** {@code player} leaves their table (their stake in a hand under way stays in the pot). */
	static void leave(ServerPlayer player, boolean tell) {
		Table t = SEATED.remove(player.getUUID());
		if (t == null) {
			return;
		}
		Seat seat = seatOf(t, player);
		boolean wasTurn = seat != null && t.phase == PLAY && myTurn(t, seat);
		if (seat != null) {
			t.seats.remove(seat);
		}
		if (tell && ServerPlayNetworking.canSend(player, State.TYPE)) {
			ServerPlayNetworking.send(player, new State(true, 0, 0, 0, 0, false, false, Component.empty(), List.of()));
		}
		if (t.seats.isEmpty()) {
			TABLES.remove(t.key);
			return;
		}
		if (t.phase == PLAY) {
			if (wasTurn) {
				t.turn--;
				advance(t);
			} else if (t.game == Game.CARDS) {
				t.turn = Math.min(t.turn, t.seats.size() - 1);
			}
			if (t.seats.stream().noneMatch(s -> s.status == TURN || s.status == WAITING) && t.phase == PLAY) {
				showdown(t);
			}
		}
		broadcast(t);
	}

	private static void deal(Table t) {
		if (t.phase != IDLE || t.seats.isEmpty() || t.seats.size() == 1 && t.house == null) {
			return;
		}
		int stake = STAKES[t.stake];
		t.pot = 0;
		t.houseIn = t.house != null && (t.seats.size() == 1 || t.houseId != null);
		List<Seat> paid = new ArrayList<>();
		for (Seat s : new ArrayList<>(t.seats)) {
			ServerPlayer p = t.level.getServer().getPlayerList().getPlayer(s.player);
			if (p != null && Money.charge(p, Money.worth(stake))) {
				s.ante = true;
				t.pot += stake;
				paid.add(s);
			} else {
				if (p != null) {
					p.sendOverlayMessage(Component.translatable("table.jaymoskingdoms.cannot_cover"));
				}
				t.seats.remove(s);
				SEATED.remove(s.player);
				if (p != null && ServerPlayNetworking.canSend(p, State.TYPE)) {
					ServerPlayNetworking.send(p, new State(true, 0, 0, 0, 0, false, false, Component.empty(), List.of()));
				}
			}
		}
		if (paid.isEmpty()) {
			TABLES.remove(t.key);
			return;
		}
		if (paid.size() == 1 && !t.houseIn) {
			// nobody to play against: the stake comes back
			Money.pay(playerOf(t, paid.getFirst()), Money.worth(stake));
			paid.getFirst().ante = false;
			t.pot = 0;
			broadcast(t);
			return;
		}
		if (t.houseIn && t.house != null) {
			t.pot += stake;
		}
		for (Seat s : t.seats) {
			s.cards.clear();
			s.status = WAITING;
		}
		if (t.house != null) {
			t.house.cards.clear();
			t.house.status = WAITING;
		}
		t.phase = PLAY;
		t.turn = -1;
		t.level.playSound(null, t.pos, SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 1.6F);
		if (t.game == Game.DICE) {
			roll(t);
		} else {
			t.deck = freshDeck(t.level);
			for (Seat s : t.seats) {
				s.cards.add(t.deck.removeLast());
				s.cards.add(t.deck.removeLast());
			}
			if (t.houseIn && t.house != null) {
				t.house.cards.add(t.deck.removeLast());
				t.house.cards.add(t.deck.removeLast());
			}
			t.turn = -1;
			t.note = Component.translatable("table.jaymoskingdoms.dealt");
			advance(t);
		}
		broadcast(t);
	}

	private static List<Integer> freshDeck(ServerLevel level) {
		List<Integer> deck = new ArrayList<>();
		for (int i = 0; i < 52; i++) {
			deck.add(i);
		}
		Collections.shuffle(deck, new java.util.Random(level.getRandom().nextLong()));
		return deck;
	}

	private static ServerPlayer playerOf(Table t, Seat s) {
		return t.level.getServer().getPlayerList().getPlayer(s.player);
	}

	// ---- cheating ---------------------------------------------------------------------------------------------------

	/** The chance an armed player's trick goes unfumbled: loaded dice or a palmed card. */
	static float cheatChance(ServerPlayer p) {
		return Math.min(0.9F, CHEAT_BASE + CHEAT_PER_FINESSE * Stats.value(p, Stats.Stat.FINESSE));
	}

	/** The chance one hand at the table, with {@code wits}, sees a cheat. */
	static float catchChance(int wits) {
		return Math.min(0.9F, CATCH_BASE + CATCH_PER_WITS * wits);
	}

	/** Whether any other hand at the table than {@code cheater} sees through the trick. */
	private static boolean seen(Table t, @Nullable Seat cheater) {
		for (Seat s : t.seats) {
			if (s == cheater || s.status == CAUGHT) {
				continue;
			}
			ServerPlayer p = playerOf(t, s);
			int wits = p == null ? 0 : Stats.value(p, Stats.Stat.WITS);
			if (t.level.getRandom().nextFloat() < catchChance(wits)) {
				return true;
			}
		}
		if (cheater != t.house && t.houseIn && t.house != null) {
			Villager v = houseVillager(t);
			if (v != null && t.level.getRandom().nextFloat() < catchChance(ForgedPapers.wits(v))) {
				return true;
			}
		}
		return false;
	}

	/** The cheater is caught: the stake is forfeit, they are out, and the house remembers. */
	private static void caught(Table t, Seat cheater) {
		cheater.status = CAUGHT;
		cheater.armed = false;
		ServerPlayer p = playerOf(t, cheater);
		if (p != null) {
			Villager v = houseVillager(t);
			if (v != null) {
				McaCompat.hearts(v, p, CAUGHT_HEARTS);
				Recollections.remember(v, p, Recollections.Kind.LIED, Component.translatable("table.jaymoskingdoms.cheated"));
				Speech.tell(t.level, v, p, Component.translatable("table.jaymoskingdoms.caught." + t.level.getRandom().nextInt(2)), Speech.RED);
			}
			Sparks.act(p, Sparks.Act.CHEATED, true);
			p.sendOverlayMessage(Component.translatable("table.jaymoskingdoms.you_caught"));
		}
		t.note = Component.translatable("table.jaymoskingdoms.caught_note", cheater.name);
	}

	// ---- dice -------------------------------------------------------------------------------------------------------

	private static int throwTwo(Table t) {
		return 1 + t.level.getRandom().nextInt(6);
	}

	private static void roll(Table t) {
		List<Seat> hands = new ArrayList<>(t.seats);
		if (t.houseIn && t.house != null) {
			hands.add(t.house);
		}
		Villager hv = houseVillager(t);
		boolean houseCheats = t.houseIn && t.house != null && crooked(hv) && t.level.getRandom().nextFloat() < HOUSE_CHEAT;
		for (Seat s : hands) {
			s.cards.clear();
			int a = throwTwo(t), b = throwTwo(t);
			boolean cheats = s.armed || s == t.house && houseCheats;
			if (cheats) {
				ServerPlayer p = s.player == null ? null : playerOf(t, s);
				boolean clean = p == null || t.level.getRandom().nextFloat() < cheatChance(p);
				if (clean) {
					int a2 = throwTwo(t), b2 = throwTwo(t), a3 = throwTwo(t), b3 = throwTwo(t);
					int best = a + b;
					if (a2 + b2 > best) {
						a = a2;
						b = b2;
						best = a + b;
					}
					if (a3 + b3 > best) {
						a = a3;
						b = b3;
					}
				}
				s.armed = false;
				s.cards.add(a);
				s.cards.add(b);
				if (!clean || seen(t, s)) {
					caught(t, s);
				} else if (s == t.house) {
					// (a crook's cheating seen by no one)
					t.note = Component.translatable("table.jaymoskingdoms.rolled");
				}
				if (p != null) {
					Sparks.act(p, Sparks.Act.CHEATED, true);
				}
				continue;
			}
			s.cards.add(a);
			s.cards.add(b);
		}
		showdown(t);
	}

	// ---- cards ------------------------------------------------------------------------------------------------------

	/** The best total of {@code cards} at twenty-one (aces count 1 or 11). */
	public static int total(List<Integer> cards) {
		int sum = 0, aces = 0;
		for (int c : cards) {
			int r = c % 13;
			if (r == 0) {
				aces++;
				sum += 1;
			} else {
				sum += Math.min(10, r + 1);
			}
		}
		while (aces > 0 && sum + 10 <= 21) {
			sum += 10;
			aces--;
		}
		return sum;
	}

	private static void draw(Table t, Seat seat) {
		boolean armed = seat.armed;
		int card;
		if (armed) {
			ServerPlayer p = playerOf(t, seat);
			boolean clean = p == null || t.level.getRandom().nextFloat() < cheatChance(p);
			seat.armed = false;
			card = clean ? bestCard(t, seat) : t.deck.removeLast();
			if (clean && t.deck.contains(card)) {
				t.deck.remove((Integer)card);
			}
			seat.cards.add(card);
			if (!clean || seen(t, seat)) {
				caught(t, seat);
				return;
			}
			if (p != null) {
				Sparks.act(p, Sparks.Act.CHEATED, true);
			}
		} else {
			seat.cards.add(t.deck.removeLast());
		}
		if (total(seat.cards) > 21) {
			seat.status = BUST;
		} else if (total(seat.cards) == 21) {
			seat.status = STOOD;
		}
	}

	/** The card in the deck that takes {@code seat} nearest to 21 without going over (the top card if none helps). */
	private static int bestCard(Table t, Seat seat) {
		int best = t.deck.getLast(), bestTotal = -1;
		for (int c : t.deck) {
			List<Integer> with = new ArrayList<>(seat.cards);
			with.add(c);
			int total = total(with);
			if (total <= 21 && total > bestTotal) {
				best = c;
				bestTotal = total;
			}
		}
		return best;
	}

	/** Moves play on to the next seat that still has a turn, then the house, then the showdown. */
	private static void advance(Table t) {
		if (t.phase != PLAY) {
			return;
		}
		int next = t.turn + 1;
		while (next < t.seats.size()) {
			Seat s = t.seats.get(next);
			if (s.status == WAITING || s.status == TURN) {
				if (total(s.cards) >= 21) {
					s.status = total(s.cards) > 21 ? BUST : STOOD;
					next++;
					continue;
				}
				s.status = TURN;
				t.turn = next;
				t.deadline = t.level.getGameTime() + TURN_TICKS;
				t.note = Component.translatable("table.jaymoskingdoms.turn", s.name);
				broadcast(t);
				return;
			}
			next++;
		}
		// the house plays out
		if (t.houseIn && t.house != null) {
			Villager hv = houseVillager(t);
			boolean cheats = crooked(hv) && t.level.getRandom().nextFloat() < HOUSE_CHEAT;
			while (total(t.house.cards) < HOUSE_STANDS && !t.deck.isEmpty()) {
				if (cheats) {
					t.house.cards.add(bestCard(t, t.house));
					t.deck.remove((Integer)t.house.cards.getLast());
				} else {
					t.house.cards.add(t.deck.removeLast());
				}
			}
			if (cheats && seen(t, t.house)) {
				caught(t, t.house);
			}
			t.house.status = total(t.house.cards) > 21 ? BUST : STOOD;
		}
		showdown(t);
	}

	// ---- the showdown -----------------------------------------------------------------------------------------------

	/** The value of a hand at the showdown: -1 if out of it. */
	private static int score(Table t, Seat s) {
		if (s.status == CAUGHT || s.status == BUST || s.cards.isEmpty()) {
			return -1;
		}
		return t.game == Game.DICE ? s.cards.get(0) + s.cards.get(1) : total(s.cards);
	}

	private static void showdown(Table t) {
		List<Seat> hands = new ArrayList<>(t.seats);
		if (t.houseIn && t.house != null) {
			hands.add(t.house);
		}
		int best = -1;
		for (Seat s : hands) {
			best = Math.max(best, score(t, s));
		}
		List<Seat> winners = new ArrayList<>();
		for (Seat s : hands) {
			if (best >= 0 && score(t, s) == best) {
				winners.add(s);
			}
		}
		if (best < 0) {
			// everyone busted or was caught: stakes go back to those still in
			for (Seat s : t.seats) {
				if (s.ante && s.status != CAUGHT) {
					Money.pay(playerOf(t, s), Money.worth(STAKES[t.stake]));
					s.status = PUSH;
				}
			}
			t.note = Component.translatable("table.jaymoskingdoms.nobody");
		} else {
			int share = t.pot / winners.size();
			for (Seat s : hands) {
				if (winners.contains(s)) {
					s.status = winners.size() > 1 ? PUSH : WON;
					ServerPlayer p = s.player == null ? null : playerOf(t, s);
					if (p != null) {
						Money.pay(p, Money.worth(share));
						if (share > STAKES[t.stake] * 2) {
							Sparks.act(p, Sparks.Act.WON_BIG, false);
						}
					}
				} else if (s.status != CAUGHT) {
					s.status = LOST;
				}
			}
			t.note = Component.translatable(winners.size() > 1 ? "table.jaymoskingdoms.tie" : "table.jaymoskingdoms.winner", winners.getFirst().name, share);
		}
		t.phase = RESULT;
		if (t.houseId != null && houseVillager(t) != null && houseVillager(t).entityTags().contains(Underworld.DICER_TAG)) {
			Underworld.played(t.level, t.pos);
		}
		t.deadline = t.level.getGameTime() + RESULT_TICKS;
		t.level.playSound(null, t.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.3F);
		broadcast(t);
	}

	// ---- time -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (TABLES.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Table t : new ArrayList<>(TABLES.values())) {
			if (now % 20 == 0) {
				for (Seat s : new ArrayList<>(t.seats)) {
					ServerPlayer p = playerOf(t, s);
					if (p == null || !p.isAlive() || p.level() != t.level || p.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(t.pos)) > SEAT_RANGE * SEAT_RANGE) {
						if (p != null) {
							leave(p, true);
						} else {
							SEATED.remove(s.player);
							t.seats.remove(s);
						}
					}
				}
				if (t.seats.isEmpty()) {
					TABLES.remove(t.key);
					continue;
				}
				Villager hv = houseVillager(t);
				if (hv == null || hv.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(t.pos)) > 100.0) {
					if (t.phase == IDLE) {
						t.houseId = null;
						findHouse(t);
						t.house = t.houseId == null ? null : t.house;
					}
				}
			}
			if (t.phase == PLAY && t.game == Game.CARDS && now >= t.deadline && t.turn >= 0 && t.turn < t.seats.size()) {
				t.seats.get(t.turn).status = STOOD;
				advance(t);
			} else if (t.phase == RESULT && now >= t.deadline) {
				// those caught cheating are put out of the game
				for (Seat s : new ArrayList<>(t.seats)) {
					ServerPlayer out = s.status == CAUGHT ? playerOf(t, s) : null;
					if (out != null) {
						leave(out, true);
					}
				}
				if (t.seats.isEmpty()) {
					continue;
				}
				t.phase = IDLE;
				t.pot = 0;
				for (Seat s : new ArrayList<>(t.seats)) {
					s.cards.clear();
					s.ante = false;
					s.status = WAITING;
				}
				if (t.house != null) {
					t.house.cards.clear();
					t.house.status = WAITING;
				}
				t.note = Component.translatable("table.jaymoskingdoms.again");
				broadcast(t);
			}
		}
	}

	// ---- telling the players ----------------------------------------------------------------------------------------

	private static void broadcast(Table t) {
		for (Seat s : t.seats) {
			ServerPlayer p = playerOf(t, s);
			if (p != null) {
				send(t, p, false);
			}
		}
	}

	private static void send(Table t, ServerPlayer p, boolean closed) {
		if (!ServerPlayNetworking.canSend(p, State.TYPE)) {
			return;
		}
		Seat mine = seatOf(t, p);
		List<Entry> entries = new ArrayList<>();
		boolean reveal = t.phase == RESULT;
		for (Seat s : t.seats) {
			entries.add(entry(t, s, s == mine, false, reveal));
		}
		if (t.house != null && (t.houseIn || t.phase == IDLE)) {
			entries.add(entry(t, t.house, false, true, reveal || t.phase == IDLE));
		}
		boolean canDeal = t.phase == IDLE && (t.seats.size() > 1 || t.house != null);
		boolean yourTurn = mine != null && t.phase == PLAY && t.game == Game.CARDS && myTurn(t, mine);
		ServerPlayNetworking.send(p, new State(closed, t.game.ordinal(), t.phase, t.stake, t.pot, canDeal, yourTurn, t.note, entries));
	}

	private static Entry entry(Table t, Seat s, boolean you, boolean house, boolean reveal) {
		List<Integer> cards = new ArrayList<>();
		for (int i = 0; i < s.cards.size(); i++) {
			// (in play, another hand's cards are face down but for the house's second card at twenty-one, and one's own)
			boolean shown = you || reveal || t.game == Game.CARDS && i > 0 && (house || s.status == STOOD || s.status == BUST);
			cards.add(shown ? s.cards.get(i) : -1);
		}
		int total = -1;
		if (you || reveal) {
			total = t.game == Game.DICE ? (s.cards.size() == 2 ? s.cards.get(0) + s.cards.get(1) : -1) : total(s.cards);
		}
		return new Entry(s.name, you, house, cards, total, s.status, you && s.armed);
	}
}
