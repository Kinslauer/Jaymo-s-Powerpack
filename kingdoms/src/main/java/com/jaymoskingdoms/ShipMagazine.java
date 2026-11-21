package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Sailing;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.ShotKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-19), on a sea ship's guns being loaded for nothing: "yes" (make them pay from their hold, as an airship's
 * do). A sea ship sailed by her own crew ({@link Corsairs}) carries a magazine ({@link Crew#magazine}): her powder, and
 * so much of each kind of shot ({@link ShotKind}). Every round her gunners load spends a powder and a shot; she loads the
 * kind her gunner wants if she has it, else round, heavy, chain or grape, whichever she has (never a shell he didn't ask
 * for); with no powder, or no shot at all, her guns stay empty.
 * <ul>
 * <li><b>At launch</b> (and a ship saved before she had one, the first time she loads) she's stocked by who she is
 * ({@link #stock}): a warship most, a pirate a little less, a merchantman, whaler or smack a few rounds to drive off
 * pirates. Explosive shells are dear (three TNT apiece), so few of them are carried.</li>
 * <li><b>In home waters</b> ({@link #HOME_WATERS} of her home), with no ship to fight, every {@link #RESUPPLY} ticks she's
 * topped up to her stock again: a realm's ship out of its treasury at {@link #price}, a shell only while the realm is rich
 * ({@link Cannon#SHELL_COST}, as its wall guns); a pirate out of her cove's storage, powder and shot as they lie there,
 * and a shell made up from three TNT (or the powder of three, {@link #SHELL_POWDER}) and a round shot, if the cove can
 * spare it.</li>
 * </ul>
 */
final class ShipMagazine {
	/** Her powder's key in the magazine (each shot's is its kind's name). */
	static final String POWDER = "powder";
	/** How near her home counts as home waters (blocks), and how often she's topped up there (ticks). */
	static final double HOME_WATERS = 80.0;
	static final int RESUPPLY = 1200;
	/** What a realm pays its treasury for a powder, a round, heavy, chain or grape shot (a shell is {@link Cannon#SHELL_COST}). */
	static final long POWDER_COST = 2L, ROUND_COST = 2L, HEAVY_COST = 5L, CHAIN_COST = 4L, GRAPE_COST = 3L;
	/** The gunpowder a pirate cove spends on a shell when it has no TNT (three kegs' worth), and what it keeps back besides. */
	static final int SHELL_POWDER = 15, POWDER_RESERVE = 8;
	/** The order a gun falls back through when the shot her gunner wants is spent. */
	private static final ShotKind[] FALLBACK = {ShotKind.ROUND, ShotKind.HEAVY, ShotKind.CHAIN, ShotKind.GRAPE};

	private ShipMagazine() {
	}

	/** What a ship of {@code side} carries when full: powder, then round, heavy, shell, chain and grape. */
	static Map<String, Integer> stock(Crew.Side side) {
		int[] n = switch (side) {
			case NAVY -> new int[] {36, 18, 7, 2, 5, 5};
			case PIRATE -> new int[] {28, 14, 5, 1, 4, 4};
			default -> new int[] {8, 6, 0, 0, 0, 2};
		};
		Map<String, Integer> out = new HashMap<>();
		out.put(POWDER, n[0]);
		out.put(ShotKind.ROUND.getSerializedName(), n[1]);
		out.put(ShotKind.HEAVY.getSerializedName(), n[2]);
		out.put(ShotKind.SHELL.getSerializedName(), n[3]);
		out.put(ShotKind.CHAIN.getSerializedName(), n[4]);
		out.put(ShotKind.GRAPE.getSerializedName(), n[5]);
		return out;
	}

	/** Her magazine, stocked as new if she has none yet. */
	static Map<String, Integer> of(Crew crew) {
		if (crew.magazine == null) {
			crew.magazine = stock(crew.side);
		}
		return crew.magazine;
	}

	/**
	 * A gun's charge from her magazine: a powder and a shot of {@code kind} (or another she has, {@link #FALLBACK}) spent.
	 * The kind loaded, or null if she's out of powder or shot (nothing is spent then).
	 */
	static @Nullable ShotKind charge(Crew crew, ShotKind kind) {
		Map<String, Integer> m = of(crew);
		if (m.getOrDefault(POWDER, 0) <= 0) {
			return null;
		}
		ShotKind loaded = m.getOrDefault(kind.getSerializedName(), 0) > 0 ? kind : null;
		for (int i = 0; loaded == null && i < FALLBACK.length; i++) {
			if (m.getOrDefault(FALLBACK[i].getSerializedName(), 0) > 0) {
				loaded = FALLBACK[i];
			}
		}
		if (loaded == null) {
			return null;
		}
		m.merge(POWDER, -1, Integer::sum);
		m.merge(loaded.getSerializedName(), -1, Integer::sum);
		return loaded;
	}

	/** From {@link Corsairs}' gunnery: in home waters with no foe, now and then, her magazine made up again. */
	static void resupply(ServerLevel level, Ship ship, Crew crew, @Nullable Ship foe, long now) {
		if (foe != null || (now + ship.getId()) % RESUPPLY != 0
			|| ship.blockPosition().distSqr(crew.home) > HOME_WATERS * HOME_WATERS) {
			return;
		}
		Faction f = Factions.get(level.getServer(), crew.faction);
		if (f == null || f.fallen()) {
			return;
		}
		Map<String, Integer> m = of(crew);
		Map<String, Integer> full = stock(crew.side);
		if (crew.side == Crew.Side.PIRATE) {
			fromCove(level, f, m, full);
		} else {
			fromTreasury(level, f, m, full);
		}
	}

	/** A realm's ship topped up out of its treasury, as far as it can pay. */
	private static void fromTreasury(ServerLevel level, Faction f, Map<String, Integer> m, Map<String, Integer> full) {
		long paid = 0L;
		long have = f.treasury();
		for (Map.Entry<String, Integer> e : full.entrySet()) {
			String key = e.getKey();
			int want = e.getValue() - m.getOrDefault(key, 0);
			if (want <= 0) {
				continue;
			}
			boolean shell = key.equals(ShotKind.SHELL.getSerializedName());
			long each = price(key);
			int n = 0;
			while (n < want && have - paid >= each && (!shell || Muskets.afford(f, Muskets.ARM_TREASURY + paid + each))) {
				paid += each;
				n++;
			}
			if (n > 0) {
				m.merge(key, n, Integer::sum);
			}
		}
		if (paid > 0L) {
			long bill = paid;
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - bill)));
		}
	}

	/** What a realm pays for one of {@code key}. */
	static long price(String key) {
		if (key.equals(POWDER)) {
			return POWDER_COST;
		}
		for (ShotKind k : ShotKind.values()) {
			if (k.getSerializedName().equals(key)) {
				return switch (k) {
					case ROUND -> ROUND_COST;
					case HEAVY -> HEAVY_COST;
					case SHELL -> Cannon.SHELL_COST;
					case CHAIN -> CHAIN_COST;
					case GRAPE -> GRAPE_COST;
				};
			}
		}
		return Long.MAX_VALUE;
	}

	/** A pirate topped up out of her cove's storage: powder and shot as they lie there, shells made up if it can spare them. */
	private static void fromCove(ServerLevel level, Faction cove, Map<String, Integer> m, Map<String, Integer> full) {
		if (!Storage.loaded(level, cove)) {
			return;
		}
		take(level, cove, m, POWDER, Items.GUNPOWDER, full);
		for (ShotKind k : ShotKind.values()) {
			take(level, cove, m, k.getSerializedName(), k.item(), full);
		}
		// a shell made up for each she lacks: three TNT (or the powder of three) and a round shot
		String shell = ShotKind.SHELL.getSerializedName();
		for (int i = full.get(shell) - m.getOrDefault(shell, 0); i > 0; i--) {
			if (Storage.count(level, cove, Sailing.ROUND_SHOT) <= 0) {
				return;
			}
			if (Storage.count(level, cove, Items.TNT) >= 3) {
				Storage.take(level, cove, Items.TNT, 3);
			} else if (Storage.count(level, cove, Items.GUNPOWDER) >= SHELL_POWDER + POWDER_RESERVE) {
				Storage.take(level, cove, Items.GUNPOWDER, SHELL_POWDER);
			} else {
				return;
			}
			Storage.take(level, cove, Sailing.ROUND_SHOT, 1);
			m.merge(shell, 1, Integer::sum);
		}
	}

	private static void take(ServerLevel level, Faction cove, Map<String, Integer> m, String key, Item item, Map<String, Integer> full) {
		int want = full.getOrDefault(key, 0) - m.getOrDefault(key, 0);
		if (want <= 0) {
			return;
		}
		int got = Storage.take(level, cove, item, want).getCount();
		if (got > 0) {
			m.merge(key, got, Integer::sum);
		}
	}
}
