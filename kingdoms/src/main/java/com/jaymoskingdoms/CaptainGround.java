package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.618) Rivals patch 3, <b>captains hold ground</b> (ideas from Shadow of War, a commercial game: our own code and
 * lines). Each camp's highest-ranked captain claims the land around it, {@link #BASE} blocks out plus {@link #PER_RANK} for
 * each of his ranks. On it:
 * <ul>
 * <li><b>Tolls:</b> a player crossing it on foot, once a day for each claim, pays his men {@link #TOLL} (thousandths of an
 * emerald's worth) or, short of it, is marked for it (a tribe's grudge rises). Sneak through and they don't see you. The
 * tribe's ruler, or a player whose sworn captain rules it, passes free.</li>
 * <li><b>Robbed caravans:</b> a caravan not his own tribe's that passes through loses {@link #ROB_SHARE} of every cart.</li>
 * <li><b>Freed:</b> kill or turn him (or every captain there) and the claim is gone; the next one up takes it if he's there.</li>
 * </ul>
 * The claim is marked on your journal's map with the captain once you know of him ({@link Captains#journal}).
 */
public final class CaptainGround {
	// ================================================================================================================
	// Tuning
	/** How far a claim runs (blocks) from the camp's centre, and how much more per rank; the camp itself (no toll in it). */
	static final int BASE = 80, PER_RANK = 16, CAMP = 40;
	/** The toll, in thousandths of an emerald's worth; a tribe's grudge for one who can't pay it. */
	static final long TOLL = 2000L;
	static final int UNPAID_GRUDGE = 4;
	/** What a caravan crossing loses from each cart. */
	static final float ROB_SHARE = 0.3F;
	// ================================================================================================================

	/** A claim: the captain, his camp's centre and how far it runs. */
	record Claim(Captain captain, BlockPos center, int radius) {
		boolean holds(BlockPos at) {
			double dx = at.getX() - center.getX(), dz = at.getZ() - center.getZ();
			return dx * dx + dz * dz <= (double)radius * radius;
		}
	}

	/** Who paid (or dodged) a claim's toll today: "camp/player" → day. Caravans already robbed: "camp/caravan". In memory. */
	private static final Map<String, Long> TOLLED = new HashMap<>();
	private static final Set<String> ROBBED = new HashSet<>();

	private CaptainGround() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TOLLED.clear();
			ROBBED.clear();
		});
	}

	/** Every claim standing now: one a camp, its highest-ranked captain's (unsworn, at home). */
	static List<Claim> claims(MinecraftServer server) {
		Map<Integer, Captain> best = new HashMap<>();
		for (Captain c : Captains.all(server).values()) {
			if (c.fallen() || c.post() < 0 || SwornCaptains.sworn(server, c.id()) || CaptainSchemes.away(server, c.id())) {
				continue;
			}
			Captain had = best.get(c.post());
			if (had == null || c.rank() > had.rank()) {
				best.put(c.post(), c);
			}
		}
		List<Claim> out = new ArrayList<>();
		for (Captain c : best.values()) {
			Faction camp = Factions.get(server, c.post());
			Camps.CampData data = camp == null || camp.fallen() ? null : Camps.data(server, c.post());
			if (data != null && data.clearedAt() < 0) {
				out.add(new Claim(c, data.center(), radius(c)));
			}
		}
		return out;
	}

	static int radius(Captain c) {
		return BASE + PER_RANK * c.rank();
	}

	/** {@code c}'s claim's reach, or 0 if he holds none (for the journal). */
	static int held(MinecraftServer server, Captain c) {
		for (Claim k : claims(server)) {
			if (k.captain().id().equals(c.id())) {
				return k.radius();
			}
		}
		return 0;
	}

	/** (From {@link Captains#tick}) tolls taken, caravans robbed. */
	static void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		List<Claim> claims = claims(server);
		if (claims.isEmpty()) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
				continue;
			}
			for (Claim k : claims) {
				if (k.holds(p.blockPosition()) && !near(k.center(), p.blockPosition(), CAMP)) {
					toll(level, k, p, day);
				}
			}
		}
		for (Caravans.Caravan cv : Caravans.all(server)) {
			for (Claim k : claims) {
				if (k.holds(cv.at()) && !k.captain().tribe().equals(Rivals.of(server, cv.home())) && ROBBED.add(k.captain().post() + "/" + cv.id())) {
					rob(server, k, cv);
				}
			}
		}
	}

	private static boolean near(BlockPos a, BlockPos b, int r) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz <= (double)r * r;
	}

	private static void toll(ServerLevel level, Claim k, ServerPlayer p, long day) {
		Captain c = k.captain();
		String key = c.post() + "/" + p.getStringUUID();
		Long last = TOLLED.get(key);
		if (last != null && last >= day) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (SwornCaptains.rules(server, c.tribe(), p.getUUID()) || p.isShiftKeyDown()) {
			// (theirs to pass; or slipped by unseen, this time)
			if (!p.isShiftKeyDown()) {
				TOLLED.put(key, day);
			}
			return;
		}
		TOLLED.put(key, day);
		Faction camp = Factions.get(server, c.post());
		if (Money.charge(p, camp, TOLL)) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7F, 1.4F);
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captain.toll.paid", c.name()).withStyle(ChatFormatting.GOLD));
		} else {
			if (Rivals.tribe(c.tribe())) {
				Tribes.add(server, p.getUUID(), c.tribe(), UNPAID_GRUDGE);
			}
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captain.toll.unpaid", c.name()).withStyle(ChatFormatting.RED));
		}
	}

	private static void rob(MinecraftServer server, Claim k, Caravans.Caravan cv) {
		List<Map<String, Integer>> carts = new ArrayList<>();
		boolean took = false;
		for (Map<String, Integer> cart : cv.carts()) {
			Map<String, Integer> left = new HashMap<>();
			for (Map.Entry<String, Integer> e : cart.entrySet()) {
				int lost = (int)Math.floor(e.getValue() * ROB_SHARE);
				took |= lost > 0;
				if (e.getValue() - lost > 0) {
					left.put(e.getKey(), e.getValue() - lost);
				}
			}
			carts.add(Map.copyOf(left));
		}
		if (!took) {
			return;
		}
		Caravans.put(server, cv.carts(carts));
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.robbed", k.captain().name(), Rivals.name(k.captain().tribe()),
			Component.literal(Caravans.nameOf(server, cv.home()))));
	}

	/** The claim at {@code at}, or null. */
	static @Nullable Claim at(MinecraftServer server, BlockPos at) {
		for (Claim k : claims(server)) {
			if (k.holds(at)) {
				return k;
			}
		}
		return null;
	}
}
