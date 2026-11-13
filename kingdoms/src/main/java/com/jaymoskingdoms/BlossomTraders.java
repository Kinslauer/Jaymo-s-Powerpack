package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Blossom merchantmen (docs/BlossomLands-Plan.md, patch 2; Lore: "the ships of strange build that come in now and then
 * with tea, rice wine, lacquer, paper and a steel no smith here can make"). Now and then a trader from over the
 * eastern sea lies off a realm's harbour for a day: dark timbers and red sails, her captain's hold open to anyone who
 * comes aboard and talks work with him ({@link SeaTrade}). Then she sails east and is gone once nobody is near to see
 * her go (follow her, {@link Helmsmen}, and she is the way there).
 * <p>
 * <b>Passage.</b> Aboard her while she lies there, her captain has "Buy Passage" on the Interact page: for
 * {@link #PASSAGE_COST} she sails at once and carries you the whole crossing, to lie off Mohknee's shore. She waits
 * there a day and will carry you home again for the same.
 * <p>
 * She is a merchantman of {@link Corsairs} like any other ({@link Crew#flag} marks her), under the colours of the
 * realm whose harbour she calls at, so its navy and its foes treat her as that realm's guest. Her crew wear the
 * harbour's dress for now: the blossom land's own comes with its folk.
 */
public final class BlossomTraders {
	/** {@link Crew#flag}: lying to trade (or homeward with nobody aboard), and carrying passengers. */
	static final String TRADING = "blossom", PASSAGE = "blossom_passage";
	/** Looked to this often (ticks); a harbour is called at no sooner than this after the last; the chance each look. */
	static final int EVERY = 200;
	static final long GAP = 24000L * 3L, LIES = 24000L;
	static final float CHANCE = 0.02F;
	/** She's gone once under way with no player this near (blocks). */
	static final double SEEN = 128.0;
	/** What the crossing costs a passenger (emeralds' worth). */
	static final int PASSAGE_COST = 20;
	/** Her captain talks passage from this near. */
	static final double NEAR = 10.0;
	public static final String ACTION = "passage";
	static final String[] NAMES = {"Plum Wind", "Ninth Lantern", "Crane's Wake", "Morning Petal", "Red Gate", "Quiet Tide", "Paper Moon", "Long Road Home"};

	/** Harbour (realm) → when a trader last called there. Not saved: the first after a restart may come early. */
	private static final Map<Integer, Long> CALLED = new HashMap<>();
	/** Player → the captain they're talking passage with. */
	private static final Map<UUID, Integer> ASKED = new HashMap<>();

	private BlossomTraders() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BlossomTraders::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ASKED.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CALLED.clear();
			ASKED.clear();
		});
	}

	static boolean is(@Nullable Crew crew) {
		return crew != null && crew.flag.startsWith(TRADING);
	}

	/** Where she makes for over the sea: a point inside Mohknee's western shore (she lies off it when she raises it). */
	private static BlossomPoint far() {
		return new BlossomPoint(Mohknee.CENTRE_X - Mohknee.LAND + Mohknee.WOBBLE, Mohknee.CENTRE_Z);
	}

	private record BlossomPoint(int x, int z) {
		BlockPos at(int y) {
			return new BlockPos(this.x, y, this.z);
		}
	}

	// ---- coming and going ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 20 == 0) {
			for (Ship ship : Ships.in(level)) {
				if (is(ship.crew)) {
					watch(level, ship, ship.crew, now);
				}
			}
		}
		if (now % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			Navies.Harbour h = f.type().isRealm() && !f.fallen() ? Navies.harbour(server, f.id()) : null;
			if (h == null || !level.isPositionEntityTicking(h.mooring()) || Mohknee.distance(h.mooring().getX(), h.mooring().getZ()) < Mohknee.edge()
				|| level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			Long last = CALLED.get(f.id());
			if (last != null && now - last < GAP && now >= last || here(level, h.mooring())) {
				continue;
			}
			call(level, f, h, now);
		}
	}

	/** Whether a blossom trader already belongs to the harbour whose mooring is {@code mooring}. */
	private static boolean here(ServerLevel level, BlockPos mooring) {
		for (Ship ship : Ships.in(level)) {
			if (is(ship.crew) && ship.crew.home.equals(mooring)) {
				return true;
			}
		}
		return false;
	}

	/** A blossom trader comes to lie off {@code f}'s harbour for a day. */
	static @Nullable Ship call(ServerLevel level, Faction f, Navies.Harbour h, long now) {
		RandomSource random = level.getRandom();
		Crew crew = new Crew(Crew.Side.MERCHANT, f.id(), "the " + NAMES[random.nextInt(NAMES.length)], h.mooring());
		crew.flag = TRADING;
		crew.bound = far().at(h.mooring().getY());
		Fleet.Look look = new Fleet.Look(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG, Fleet.block("red_wool"), null);
		BlockPos water = Fleet.openSea(level, h.mooring(), 40, 8, random);
		Ship ship = water == null ? null : Corsairs.launch(level, random.nextBoolean() ? Fleet.Rig.CARGO_SHIP : Fleet.Rig.TRADER, look, water,
			net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random), crew, f,
			new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, Navies.MERCHANT_HANDS, 0));
		if (ship == null) {
			return null;
		}
		CALLED.put(f.id(), now);
		SeaTrade.lie(ship, now + LIES);
		JaymosKingdoms.LOGGER.info("The blossom trader {} lies off {}", crew.name, f.name());
		return ship;
	}

	/** {@code /kingdom blossomtrader}: one comes to the realm harbour nearest whoever asked. */
	static int callHere(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {
		net.minecraft.commands.CommandSourceStack source = context.getSource();
		ServerLevel level = Factions.level(source.getServer());
		Faction best = null;
		Navies.Harbour bestH = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(source.getServer())) {
			Navies.Harbour h = Navies.harbour(source.getServer(), f.id());
			double d = h == null ? Double.MAX_VALUE : h.mooring().distToCenterSqr(source.getPosition());
			if (d < bestD) {
				best = f;
				bestH = h;
				bestD = d;
			}
		}
		Ship ship = best == null || !level.isPositionEntityTicking(bestH.mooring()) ? null : call(level, best, bestH, level.getGameTime());
		if (ship == null) {
			source.sendFailure(Component.translatable("command.jaymoskingdoms.blossomtrader.none"));
			return 0;
		}
		Ship launched = ship;
		String realm = best.name();
		source.sendSuccess(() -> Component.translatable("command.jaymoskingdoms.blossomtrader.done", launched.crew.name, realm,
			launched.blockPosition().getX(), launched.blockPosition().getZ()), true);
		return 1;
	}

	/** One of them, each second: gone when she sails unseen; lying off Mohknee when she raises it with passengers. */
	private static void watch(ServerLevel level, Ship ship, Crew crew, long now) {
		if (ship.foundering() || SeaTrade.hoveTo(level, ship)) {
			return;
		}
		boolean seen = level.getNearestPlayer(ship, SEEN) != null;
		if (!seen) {
			// under way with nobody to see her go: she's over the horizon
			Navies.stow(ship);
			return;
		}
		boolean outward = crew.bound != null && !crew.bound.equals(crew.home);
		if (outward && Mohknee.shoreAhead(level, ship.getX(), ship.getZ())) {
			// the far shore: she lies off it a day, and her next course is home
			crew.flag = TRADING;
			crew.bound = crew.home;
			SeaTrade.lie(ship, now + LIES);
			say(level, ship, crew, "landfall");
		}
	}

	/** She's back at the harbour she trades at (carrying someone home): she lies there again, then east once more. */
	static void arrived(ServerLevel level, Ship ship, Crew crew) {
		crew.flag = TRADING;
		crew.bound = far().at(crew.home.getY());
		SeaTrade.lie(ship, level.getGameTime() + LIES);
		say(level, ship, crew, "home");
	}

	private static @Nullable LivingEntity captain(Ship ship, Crew crew) {
		for (LivingEntity hand : ship.crewAboard()) {
			if (hand.getUUID().equals(crew.captain)) {
				return hand;
			}
		}
		return null;
	}

	private static void say(ServerLevel level, Ship ship, Crew crew, String line) {
		LivingEntity captain = captain(ship, crew);
		if (captain != null) {
			Speech.say(level, captain, Component.translatable("talk.jaymoskingdoms.blossom_trader." + line), Speech.WHITE, 24.0);
		}
	}

	// ---- passage ------------------------------------------------------------------------------------------------------------

	/** "Buy Passage" shows on {@code v}'s Interact page: a blossom trader's captain, lying to, with {@code player} aboard her. */
	static boolean offers(ServerPlayer player, LivingEntity v) {
		return v.getVehicle() instanceof Ship ship && is(ship.crew) && v.getUUID().equals(ship.crew.captain) && TRADING.equals(ship.crew.flag)
			&& !ship.foundering() && Ships.aboard(player) == ship && v.distanceTo(player) <= NEAR;
	}

	/** {@code player} pressed "Buy Passage": where she's bound, and his price. */
	static void talk(ServerPlayer player, LivingEntity v) {
		if (!offers(player, v) || !(v.getVehicle() instanceof Ship ship)) {
			return;
		}
		ServerLevel level = player.level();
		boolean home = ship.crew.bound != null && ship.crew.bound.equals(ship.crew.home);
		Component words = Component.translatable(home ? "talk.jaymoskingdoms.blossom_trader.ask.home" : "talk.jaymoskingdoms.blossom_trader.ask", PASSAGE_COST);
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			Speech.tell(level, v, player, words, Speech.WHITE);
			return;
		}
		ASKED.put(player.getUUID(), v.getId());
		ServerPlayNetworking.send(player, new TalkNet.Ask(v.getId(), v.getDisplayName(), words,
			List.of(Component.translatable("talk.jaymoskingdoms.blossom_trader.option.pay", PASSAGE_COST), Component.translatable("talk.jaymoskingdoms.work.close")),
			List.of(Money.canPay(player, Money.worth(PASSAGE_COST)), true)));
	}

	/** An answer to a blossom captain's price; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Integer asked = ASKED.get(player.getUUID());
		if (asked == null || asked != answer.entityId()) {
			return false;
		}
		ASKED.remove(player.getUUID());
		ServerLevel level = player.level();
		if (answer.option() != 0 || !(level.getEntity(answer.entityId()) instanceof LivingEntity v) || !offers(player, v)
			|| !(v.getVehicle() instanceof Ship ship) || !Money.charge(player, Money.worth(PASSAGE_COST))) {
			return true;
		}
		boolean home = ship.crew.bound != null && ship.crew.bound.equals(ship.crew.home);
		ship.crew.flag = PASSAGE;
		SeaTrade.sail(ship);
		Introductions.learn(player, v);
		Speech.tell(level, v, player, Component.translatable(home ? "talk.jaymoskingdoms.blossom_trader.paid.home" : "talk.jaymoskingdoms.blossom_trader.paid"), Speech.WHITE);
		return true;
	}
}
