package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import java.util.ArrayList;
import java.util.List;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.270) Andy (2026-10-27), of a ship's captain in an anime girl's skin from MCA's clothing library: "we're about to
 * go back to a set library of skins, at least for specific characters because these skins have no place in the pack."
 * <p>
 * Our named characters wear only our own outfits (MCA clothing in {@code assets/jaymoskingdoms/skins/clothing}, drawn by
 * {@code tools/gen_uniforms.py} from our folk's and sailors' skins, the head left for the villager's own face and hair):
 * a ship's crew by side and role (a pirate captain's red coat, a navy captain's blue one, a merchant's or whaler's master
 * in brown; pirate deckhands, gunners, powder monkeys, bombardiers; plain seamen on the realms' ships), a king's robe or a
 * queen's gown, a lord's surcoat or a lady's gown in their overlord's colour, and a guard's gambeson by the land of their
 * realm's seat. MCA's own pick is never used for them ({@code McaClothingPoolMixin}), and anyone wearing something else
 * (or one of these when it's no longer theirs) is dressed again when they load and whenever their place changes, unless
 * their clothes are locked in MCA's editor. Everyone else is dressed by MCA from its own clothes ({@link Wardrobe}).
 */
public final class Uniforms {
	static final String PREFIX = "jaymoskingdoms:skins/clothing/";
	static final String PIRATE_CAPTAIN = id("crew/pirate_captain"), SEA_CAPTAIN = id("crew/sea_captain"), MASTER = id("crew/master"),
		SHIPWRIGHT = id("crew/shipwright");
	static final List<String> DECKHANDS = ids("crew/deckhand_", 4), SEAMEN = ids("crew/seaman_", 4), GUNNERS = ids("crew/gunner_", 2),
		POWDER_MONKEYS = ids("crew/powder_monkey_", 2), BOMBARDIERS = ids("crew/bombardier_", 2);
	static final List<String> KINGS = List.of(id("royal/king_crimson"), id("royal/king_blue")),
		QUEENS = List.of(id("royal/queen_crimson"), id("royal/queen_purple"));
	/** A guard's gambeson by their realm's land ({@link Climate#skinGroup}: temperate, sunny, cold). */
	static final List<String> GUARDS = List.of(id("guard/temperate"), id("guard/sunny"), id("guard/cold"));

	private Uniforms() {
	}

	private static String id(String path) {
		return PREFIX + path + ".png";
	}

	private static List<String> ids(String path, int n) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(id(path + i));
		}
		return List.copyOf(out);
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof VillagerEntityMCA v) {
				fit(v);
			}
		});
	}

	/** {@code clothes} are one of ours. */
	static boolean ours(@Nullable String clothes) {
		return clothes != null && clothes.startsWith(PREFIX);
	}

	/** {@code villager} wears a ruler's robe or gown of ours. */
	static boolean royal(VillagerEntityMCA villager) {
		String c = villager.getClothes();
		return ours(c) && (c.contains("/royal/") || c.contains("/lord/") || c.contains("/lady/"));
	}

	/** What {@code villager} may wear, or nothing for anyone who isn't one of our named characters (MCA dresses them). */
	public static List<String> outfits(VillagerEntityMCA villager) {
		if (villager.isBaby()) {
			return List.of();
		}
		int role = villager instanceof Worker w ? w.role() : -1;
		if (Crewmen.tagged(villager)) {
			Crew.Side side = side(villager);
			if (role == Pirates.CAPTAIN) {
				return List.of(PIRATE_CAPTAIN);
			}
			if (role == Pirates.SEA_CAPTAIN) {
				return List.of(side == Crew.Side.NAVY ? SEA_CAPTAIN : MASTER);
			}
			if (role == Pirates.GUNNER) {
				return GUNNERS;
			}
			if (role == Pirates.POWDER_MONKEY) {
				return POWDER_MONKEYS;
			}
			if (role == Pirates.BOMBARDIER) {
				return BOMBARDIERS;
			}
			if (role == Pirates.SHIPWRIGHT) {
				return List.of(SHIPWRIGHT);
			}
			return side == Crew.Side.PIRATE || role == Pirates.DECKHAND ? DECKHANDS : SEAMEN;
		}
		MinecraftServer server = villager.level().getServer();
		Faction f = server == null || !(villager instanceof Worker w) ? null : Factions.get(server, w.factionId());
		boolean female = villager.getGenetics().getGender() == Gender.FEMALE;
		if (Rulers.is(villager)) {
			if (role == Folk.LORD) {
				DyeColor colour = f == null ? DyeColor.RED : f.styleColor(server);
				return List.of(id((female ? "lady/" : "lord/") + colour.getName()));
			}
			return female ? QUEENS : KINGS;
		}
		if (role == Folk.GUARD || villager.isGuard()) {
			int land = f == null || !(villager.level() instanceof ServerLevel level) ? 0
				: Math.floorMod(Climate.at(level, f.seat()).skinGroup(), GUARDS.size());
			return List.of(GUARDS.get(land));
		}
		return List.of();
	}

	/** The crew's side from their tag ({@link Crewmen#sideTag}), or pirates. */
	private static Crew.Side side(VillagerEntityMCA villager) {
		for (Crew.Side side : Crew.Side.values()) {
			if (villager.entityTags().contains(Crewmen.sideTag(side))) {
				return side;
			}
		}
		return Crew.Side.PIRATE;
	}

	/** {@link #outfits} as MCA's pool, or null for MCA's own pick. */
	public static @Nullable WeightedPool<String> pool(VillagerEntityMCA villager) {
		List<String> outfits = outfits(villager);
		if (outfits.isEmpty()) {
			return null;
		}
		WeightedPool.Mutable<String> pool = new WeightedPool.Mutable<>(outfits.get(0));
		for (String o : outfits) {
			pool.add(o, 1.0F);
		}
		return pool;
	}

	/**
	 * {@code villager} (their clothes not locked) is put in one of their outfits if they aren't in one, or out of ours if
	 * they're no longer one of our characters (MCA dresses them for their job). True if they were dressed.
	 */
	static boolean fit(VillagerEntityMCA villager) {
		if (villager.level().isClientSide() || villager.isClothingLocked()) {
			return false;
		}
		List<String> outfits = outfits(villager);
		String clothes = villager.getClothes();
		if (outfits.isEmpty()) {
			if (!ours(clothes)) {
				return false;
			}
			villager.forceRandomizeClothes();
			return true;
		}
		if (outfits.contains(clothes)) {
			return false;
		}
		villager.setClothes(outfits.get(villager.getRandom().nextInt(outfits.size())));
		return true;
	}
}
