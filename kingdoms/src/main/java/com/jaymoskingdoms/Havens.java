package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.221) Andy (2026-10-26): "you will often see villages of solely non humans because of this fact, as the ones who
 * lived among the humans got tired of the ridicule. raising a sort of rivalry, between human and nonhumans across the
 * entire world."
 * <p>
 * A haven is a settlement of non-humans only ({@link Prejudice#degree}): of one blood (the one its land suits: Tideborn
 * by water, Stoneborn in the hills, Sylvan in the woods, else any of the four) or a mix of all four. Every one born
 * there is of it ({@link #lean}, from {@link VillagerHeritage#roll}), and so are its own folk ({@link #folk}). Whether a
 * settlement is a haven is drawn once, when it's first looked at, and kept ({@link #HAVENS}). Settlements that were
 * already there when this came in are never havens (their folk are who they are); new ones are, this often:
 * {@link #VILLAGE_SHARE} of villages, {@link #REALM_SHARE} of realms (never a purist's, never a barbarian camp).
 * Folk who first load within {@link #SETTLE_TICKS} of a haven being drawn take its blood, so a village found with its
 * people already standing is wholly of it too.
 * <p>
 * The rivalry is {@link Prejudice}'s: a haven's people mind humans hard, and non-humans anywhere mind them a little.
 */
public final class Havens {
	/** How often a new settlement is a haven: a village, and a kingdom or fiefdom. */
	static final float VILLAGE_SHARE = 0.25F, REALM_SHARE = 0.1F;
	/** A haven of all four bloods mixed this often (the rest are of one). */
	static final float MIXED_SHARE = 0.35F;
	/** A haven's land, round its seat. */
	static final double LAND_RANGE = 96.0;
	/** Folk first loading this soon after a haven is drawn take its blood (ticks: two days). */
	static final long SETTLE_TICKS = 48000L;
	/** A mixed haven's bloods, by weight: Tideborn, Stoneborn, Warrior-born, Sylvan. */
	static final int MIX_TIDE = 3, MIX_STONE = 3, MIX_WARRIOR = 2, MIX_SYLVAN = 3;

	static final String MIXED = "mixed", NONE = "", MIGRATED = "_migrated";
	/** Faction id → its haven ("" none, "mixed", or a heritage key), "|" the game time it was drawn. On the Overworld. */
	static final AttachmentType<Map<String, String>> HAVENS = AttachmentRegistry.create(JaymosKingdoms.id("havens"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	private Havens() {
	}

	static void register() {
		// the settlements already standing in an old world stay as they are
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = Factions.level(server);
			Map<String, String> map = level.getAttachedOrElse(HAVENS, Map.of());
			if (map.containsKey(MIGRATED)) {
				return;
			}
			Map<String, String> next = new HashMap<>(map);
			for (Faction f : Factions.all(server)) {
				next.putIfAbsent(Integer.toString(f.id()), NONE + "|0");
			}
			next.put(MIGRATED, "1");
			level.setAttached(HAVENS, Map.copyOf(next));
		});
	}

	/** {@code f}'s haven: "" none, {@link #MIXED}, or a heritage key (drawn and kept the first time it's asked). */
	static String of(MinecraftServer server, @Nullable Faction f) {
		if (f == null) {
			return NONE;
		}
		String entry = entry(server, f);
		return entry.substring(0, entry.indexOf('|'));
	}

	private static String entry(MinecraftServer server, Faction f) {
		ServerLevel level = Factions.level(server);
		Map<String, String> map = level.getAttachedOrElse(HAVENS, Map.of());
		String key = Integer.toString(f.id());
		String entry = map.get(key);
		if (entry != null && entry.indexOf('|') >= 0) {
			return entry;
		}
		entry = draw(level, f) + "|" + level.getGameTime();
		Map<String, String> next = new HashMap<>(map);
		next.put(key, entry);
		level.setAttached(HAVENS, Map.copyOf(next));
		return entry;
	}

	/** Whether {@code f} is a haven, and of what (from who it is, so the draw is always the same). */
	private static String draw(ServerLevel level, Faction f) {
		if (f.fallen() || f.type() == FactionType.BARBARIAN || Tempers.of(f) == Tempers.Temper.PURIST) {
			return NONE;
		}
		long mix = (f.seat().asLong() * 0x9E3779B97F4A7C15L) ^ ((long)f.id() << 21);
		mix ^= mix >>> 29;
		float share = f.type().isRealm() ? REALM_SHARE : VILLAGE_SHARE;
		if (Math.floorMod(mix, 1000L) >= (long)(share * 1000)) {
			return NONE;
		}
		mix = Long.rotateLeft(mix, 17) * 0xBF58476D1CE4E5B9L;
		if (Math.floorMod(mix >>> 7, 1000L) < (long)(MIXED_SHARE * 1000)) {
			return MIXED;
		}
		Holder<Biome> biome = level.getBiome(f.seat());
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
			return Heritage.Kind.TIDEBORN.key();
		}
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_BADLANDS)) {
			return Heritage.Kind.STONEBORN.key();
		}
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
			return Heritage.Kind.SYLVAN.key();
		}
		Heritage.Kind[] four = {Heritage.Kind.TIDEBORN, Heritage.Kind.STONEBORN, Heritage.Kind.WARRIORBORN, Heritage.Kind.SYLVAN};
		return four[(int)Math.floorMod(mix >>> 23, 4L)].key();
	}

	/** {@code f} is a haven. */
	static boolean is(MinecraftServer server, @Nullable Faction f) {
		return !of(server, f).isEmpty();
	}

	/** The haven whose land {@code pos} is on, or null. */
	static @Nullable Faction at(ServerLevel level, BlockPos pos) {
		Faction f = Factions.around(level.getServer(), level, pos, LAND_RANGE);
		return is(level.getServer(), f) ? f : null;
	}

	/** One born at {@code pos}: on a haven's land, of its blood (null: the usual roll). */
	static Heritage.@Nullable Kind lean(ServerLevel level, BlockPos pos, RandomSource random) {
		return blood(level.getServer(), at(level, pos), random);
	}

	/** A folk set up for faction {@code id}: of the haven's blood if it's one (null: the usual roll). */
	static Heritage.@Nullable Kind folk(MinecraftServer server, int id, RandomSource random) {
		return blood(server, Factions.get(server, id), random);
	}

	/** A blood for one of haven {@code f}'s people, or null if it isn't one. */
	private static Heritage.@Nullable Kind blood(MinecraftServer server, @Nullable Faction f, RandomSource random) {
		String haven = of(server, f);
		if (haven.isEmpty()) {
			return null;
		}
		if (!MIXED.equals(haven)) {
			return Heritage.Kind.byKey(haven);
		}
		int pick = random.nextInt(MIX_TIDE + MIX_STONE + MIX_WARRIOR + MIX_SYLVAN);
		return (pick -= MIX_TIDE) < 0 ? Heritage.Kind.TIDEBORN : (pick -= MIX_STONE) < 0 ? Heritage.Kind.STONEBORN
			: pick - MIX_WARRIOR < 0 ? Heritage.Kind.WARRIORBORN : Heritage.Kind.SYLVAN;
	}

	/** {@code kind} belongs in haven {@code f}. */
	static boolean fits(MinecraftServer server, @Nullable Faction f, Heritage.@Nullable Kind kind) {
		String haven = of(server, f);
		return haven.isEmpty() || (MIXED.equals(haven) ? Prejudice.degree(kind) > 0 : kind != null && haven.equals(kind.key()));
	}

	/**
	 * A villager (already of a blood) first loads: in a haven drawn only lately, one not of its blood takes it (the village
	 * was found with its people standing). Null leaves them be.
	 */
	static Heritage.@Nullable Kind settle(ServerLevel level, LivingEntity villager, Heritage.@Nullable Kind kind) {
		Faction f = villager instanceof Folk folk ? Factions.get(level.getServer(), folk.factionId()) : at(level, villager.blockPosition());
		if (f == null || fits(level.getServer(), f, kind)) {
			return null;
		}
		String entry = entry(level.getServer(), f);
		long drawn = Long.parseLong(entry.substring(entry.indexOf('|') + 1));
		if (level.getGameTime() - drawn > SETTLE_TICKS) {
			return null;
		}
		return blood(level.getServer(), f, villager.getRandom());
	}

	/** The haven {@code who} lives in, or null. */
	static @Nullable Faction home(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level)) {
			return null;
		}
		if (who instanceof Folk folk) {
			Faction f = Factions.get(level.getServer(), folk.factionId());
			return is(level.getServer(), f) ? f : null;
		}
		return at(level, who.blockPosition());
	}
}
