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
 * (0.7.272) Andy (2026-10-27), of a ship's captain in an anime girl's skin from MCA's clothing library: "we're about to
 * go back to a set library of skins, at least for specific characters because these skins have no place in the pack."
 * <p>
 * Our named characters wear only our own outfits (MCA clothing in {@code assets/jaymoskingdoms/skins/clothing}, drawn by
 * {@code tools/gen_uniforms.py} from our folk's and sailors' skins, the head left for the villager's own face and hair):
 * (0.7.291) the court's and the camps' casters in their robes ({@link Casters}, {@link CourtWizards}); a ship's crew by side and role (a pirate captain's red coat, a navy captain's blue one, a merchant's or whaler's master
 * in brown; pirate deckhands, gunners, powder monkeys, bombardiers; plain seamen on the realms' ships), a king's robe or a
 * queen's gown, a lord's surcoat or a lady's gown in their overlord's colour, and a guard's gambeson by the land of their
 * realm's seat. MCA's own pick is never used for them ({@code McaClothingPoolMixin}), and anyone wearing something else
 * (or one of these when it's no longer theirs) is dressed again when they load and whenever their place changes, unless
 * their clothes are locked in MCA's editor. Everyone else is dressed by MCA from its own clothes ({@link Wardrobe}).
 */
public final class Uniforms {
	static final String PREFIX = "jaymoskingdoms:skins/clothing/";
	/** How many of each outfit there are (Andy: "each outfit needs at least 4 variations so they dont all look the same"). */
	static final int VARIANTS = 4;
	static final List<String> PIRATE_CAPTAINS = ids("crew/pirate_captain"), SEA_CAPTAINS = ids("crew/sea_captain"), MASTERS = ids("crew/master"),
		SHIPWRIGHTS = ids("crew/shipwright"), DECKHANDS = ids("crew/deckhand", 6), SEAMEN = ids("crew/seaman", 6), GUNNERS = ids("crew/gunner"),
		POWDER_MONKEYS = ids("crew/powder_monkey"), BOMBARDIERS = ids("crew/bombardier");
	static final List<String> KINGS = ids("royal/king"), QUEENS = ids("royal/queen");
	/** (0.7.291) A necromancer's hooded robes and a blood witch's (a shaman's and a court wizard's go by their side's colour). */
	static final List<String> NECROMANCERS = ids("caster/necromancer"), BLOOD_WITCHES = ids("caster/blood_witch");
	/**
	 * (0.7.292) A goblin village's rags, a slinger's hood and hides, a king's robe and crown; an orc camp's hide kilts and
	 * harness, a ranger's hood, a shaman's bone-strung hides and mask, a warchief's iron (all over a goblin's or an orc's
	 * own green: {@link Greenskins}).
	 */
	static final List<String> GOBLINS = ids("greenskin/goblin"), GOBLIN_SLINGERS = ids("greenskin/goblin_slinger"), GOBLIN_KINGS = ids("greenskin/goblin_king"),
		ORC_GRUNTS = ids("greenskin/orc_grunt"), ORC_RANGERS = ids("greenskin/orc_ranger"), ORC_SHAMANS = ids("greenskin/orc_shaman"),
		ORC_WARCHIEFS = ids("greenskin/orc_warchief");
	/** A guard's gambesons by their realm's land ({@link Climate#skinGroup}: temperate, sunny, cold). */
	static final List<List<String>> GUARDS = List.of(ids("guard/temperate"), ids("guard/sunny"), ids("guard/cold"));

	private Uniforms() {
	}

	/**
	 * (0.7.313) A Z Fighter character's outfits ({@link ZFighterFolk}): the Keeper's temple robe, the Hermit's beach
	 * shirt and straw hat, the Tinkerer's lab coat and goggles, the Judge's black robe, the road's master's travelling
	 * gi, the fortune teller's shawl and scarf, a demon master's dark gi (drawn by {@code tools/gen_zfighter_outfits.py}).
	 */
	static List<String> zfighter(String kind) {
		return ids("zfighter/" + kind);
	}

	private static String id(String path) {
		return PREFIX + path + ".png";
	}

	private static List<String> ids(String path) {
		return ids(path, VARIANTS);
	}

	/** {@code path_0} to {@code path_<n-1>}. */
	private static List<String> ids(String path, int n) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(id(path + "_" + i));
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
		List<String> zfighter = ZFighterFolk.outfits(villager);
		if (zfighter != null) {
			// (0.7.313) one of Powers' Z Fighter characters
			return zfighter;
		}
		if (Crewmen.tagged(villager)) {
			Crew.Side side = side(villager);
			if (role == Pirates.CAPTAIN) {
				return PIRATE_CAPTAINS;
			}
			if (role == Pirates.SEA_CAPTAIN) {
				return side == Crew.Side.NAVY ? SEA_CAPTAINS : MASTERS;
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
				return SHIPWRIGHTS;
			}
			return side == Crew.Side.PIRATE || role == Pirates.DECKHAND ? DECKHANDS : SEAMEN;
		}
		MinecraftServer server = villager.level().getServer();
		Faction f = server == null || !(villager instanceof Worker w) ? null : Factions.get(server, w.factionId());
		boolean female = villager.getGenetics().getGender() == Gender.FEMALE;
		if (Rulers.is(villager)) {
			if (role == Folk.LORD) {
				DyeColor colour = f == null ? DyeColor.RED : f.styleColor(server);
				return ids((female ? "lady/" : "lord/") + colour.getName());
			}
			return female ? QUEENS : KINGS;
		}
		// (0.7.291) the casters in the office (Casters, CourtWizards): robes, a shaman's furs and paint, a wizard's stars
		if (role == Sorcery.NECROMANCER) {
			return NECROMANCERS;
		}
		if (role == Sorcery.BLOOD_WITCH) {
			return BLOOD_WITCHES;
		}
		if (role == Sorcery.SHAMAN || role == Sorcery.WIZARD) {
			DyeColor colour = f == null ? DyeColor.PURPLE : f.styleColor(server);
			return ids("caster/" + (role == Sorcery.SHAMAN ? "shaman/" : "wizard/") + colour.getName());
		}
		// (0.7.296) a cove's captain waiting ashore for his ship, a harbour's shipwright on his pier
		if (role == Pirates.CAPTAIN) {
			return PIRATE_CAPTAINS;
		}
		if (role == Pirates.SHIPWRIGHT) {
			return SHIPWRIGHTS;
		}
		if (Greenskins.isRole(role) || role == Orcs.SHAMAN) {
			return role == Goblins.GOBLIN ? GOBLINS : role == Goblins.SLINGER ? GOBLIN_SLINGERS : role == Goblins.KING ? GOBLIN_KINGS
				: role == Orcs.RANGER ? ORC_RANGERS : role == Orcs.SHAMAN ? ORC_SHAMANS : role == Orcs.WARCHIEF ? ORC_WARCHIEFS : ORC_GRUNTS;
		}
		if (role == Folk.GUARD || villager.isGuard()) {
			int land = f == null || !(villager.level() instanceof ServerLevel level) ? 0
				: Math.floorMod(Climate.at(level, f.seat()).skinGroup(), GUARDS.size());
			return GUARDS.get(land);
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
