package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) What the Smithing Anvil can hammer out, and the rules of the hammering game (after Overgeared's anvil
 * minigame, Copyright (c) phuccom000, MIT). Every metal weapon head ({@link WeaponParts}) and every metal tool head
 * ({@link PartType.Kind#HEAD}) has a forging, costing the ingots its cast would. Other parts of the patch add to it
 * through the lists at the bottom.
 */
public final class Forging {
	/** One thing the anvil makes: its id, the (cold) result, its metal, the ingots it takes and the blows it needs. */
	public record Option(String id, ItemStack result, Molten metal, int ingots, int hits) {
	}

	/** Blows a forging takes: a dagger 3, a claymore 8, never more than {@link #MAX_HITS}. */
	public static final int MIN_HITS = 3, MAX_HITS = 10;
	public static final float HITS_PER_INGOT = 1.25F;
	/** Hot ingots one anvil holds. */
	public static final int MAX_INGOTS = 9;

	// --- the bar (0 to 100 across) ---
	/** Half-width of the perfect zone at the first blow, how much it shrinks each blow, and its floor. */
	public static final float PERFECT_HALF = 6.0F, SHRINK = 0.9F, MIN_PERFECT_HALF = 2.0F;
	/** The good zone is this much wider than the perfect one (Overgeared's 3x). */
	public static final float GOOD_SCALE = 3.0F;
	/** Arrow speed (bar per tick) at the first blow, gained each blow, and its top. */
	public static final float START_SPEED = 2.5F, SPEED_UP = 0.4F, MAX_SPEED = 6.0F;
	/** Where zones sit: their middle stays this far from either end. */
	public static final float CENTRE_MARGIN = 25.0F;

	// --- scoring ---
	public static final int MISS = 0, GOOD = 1, PERFECT = 2;
	public static final float GOOD_SCORE = 0.6F;
	/** Average score each grade needs. */
	public static final float PERFECT_GRADE = 0.9F, EXPERT_GRADE = 0.72F, WELL_GRADE = 0.45F;
	/** Chance an all-perfect forging turns out a Masterwork (Overgeared's is 5%; Andy: friends should see one). */
	public static final float MASTER_CHANCE = 0.15F;
	/** Ticks between blows (the hammer's cooldown). */
	public static final int BLOW_TICKS = 8;

	private static @Nullable Map<String, Option> options;

	private Forging() {
	}

	/** How hard a metal is to work: soft gold and copper are kind, steel is not. */
	public static float difficulty(Molten metal) {
		return switch (metal) {
			case GOLD, COPPER -> 0.8F;
			case SILVER, ROSE_GOLD -> 0.9F;
			case AMETHYST_BRONZE, PIG_IRON -> 1.1F;
			case SLIMESTEEL -> 1.25F;
			case STEEL -> 1.3F;
			default -> 1.0F;
		};
	}

	public static int hitsFor(int ingots) {
		return Mth.clamp(Math.round(MIN_HITS + HITS_PER_INGOT * (ingots - 1)), MIN_HITS, MAX_HITS);
	}

	/** Every forging, by id. */
	public static Map<String, Option> all() {
		if (options == null) {
			Map<String, Option> out = new LinkedHashMap<>();
			for (WeaponParts.Kind kind : WeaponParts.loaded()) {
				if (WeaponParts.king(kind)) {
					continue;
				}
				int ingots = Math.max(1, Math.round(kind.mb() / (float) Molten.INGOT));
				for (WeaponParts.Metal metal : WeaponParts.Metal.values()) {
					if (metal.molten != null && kind.in(metal) != null) {
						add(out, new Option("weapon/" + kind.part() + "/" + metal.id, WeaponPartItem.of(kind, metal, 1), metal.molten, ingots,
							hitsFor(ingots)));
					}
				}
			}
			for (PartType part : PartType.values()) {
				if (part.kind != PartType.Kind.HEAD) {
					continue;
				}
				for (TinkerMaterial material : TinkerMaterial.values()) {
					Molten molten = Molten.of(material);
					if (molten != null && material.makes(part.kind)) {
						add(out, new Option("part/" + part.id + "/" + material.id, ToolPartItem.of(part, material, 1), molten, part.cost,
							hitsFor(part.cost)));
					}
				}
			}
			for (Supplier<List<Option>> provider : PROVIDERS) {
				for (Option option : provider.get()) {
					add(out, option);
				}
			}
			options = out;
		}
		return options;
	}

	private static void add(Map<String, Option> out, Option option) {
		out.put(option.id(), option);
	}

	public static @Nullable Option byId(String id) {
		return all().get(id);
	}

	/** What this many ingots of this metal can become, cheapest first. */
	public static List<Option> forMetal(Molten metal, int ingots) {
		List<Option> out = new ArrayList<>();
		for (Option option : all().values()) {
			if (option.metal() == metal && option.ingots() <= ingots) {
				out.add(option);
			}
		}
		out.sort((a, b) -> Integer.compare(a.ingots(), b.ingots()));
		return out;
	}

	// --- the bar, the same on both sides ---

	public static float perfectHalf(int hitsDone, float difficulty, float bonus) {
		return Math.max(MIN_PERFECT_HALF, PERFECT_HALF * (float) Math.pow(SHRINK, hitsDone * difficulty)) + bonus;
	}

	public static float goodHalf(int hitsDone, float difficulty, float bonus) {
		return perfectHalf(hitsDone, difficulty, bonus) * GOOD_SCALE;
	}

	public static float speed(int hitsDone, float difficulty) {
		return Math.min(MAX_SPEED, START_SPEED + SPEED_UP * hitsDone * difficulty);
	}

	/** Where this blow's zones sit on the bar. */
	public static float centre(long seed, int hitsDone) {
		return CENTRE_MARGIN + new Random(seed * 31 + hitsDone).nextFloat() * (100.0F - 2 * CENTRE_MARGIN);
	}

	/** How a blow at {@code arrow} (0 to 100) lands. */
	public static int judge(float arrow, long seed, int hitsDone, float difficulty, float bonus) {
		float off = Math.abs(arrow - centre(seed, hitsDone));
		if (off <= perfectHalf(hitsDone, difficulty, bonus)) {
			return PERFECT;
		}
		return off <= goodHalf(hitsDone, difficulty, bonus) ? GOOD : MISS;
	}

	public static float score(int result) {
		return result == PERFECT ? 1.0F : result == GOOD ? GOOD_SCORE : 0.0F;
	}

	/** The grade a finished forging earns before any cap. */
	public static Grade grade(Smithing.Work work, int hits, Random random) {
		float average = hits <= 0 ? 0.0F : work.score() / hits;
		if (work.perfects() >= hits && random.nextFloat() < MASTER_CHANCE) {
			return Grade.MASTER;
		}
		if (average >= PERFECT_GRADE) {
			return Grade.PERFECT;
		}
		if (average >= EXPERT_GRADE) {
			return Grade.EXPERT;
		}
		return average >= WELL_GRADE ? Grade.WELL : Grade.POOR;
	}

	// --- hooks for the rest of the patch ---

	/** Why this smith can't work this forging on this anvil (a stone anvil, the wrong hammer), or null. */
	public interface Check {
		@Nullable Component refuse(ServerPlayer smith, SmithingAnvilEntity anvil, Option option);
	}

	/** The best grade this smith can reach here (a stone anvil, a blueprint), or null for no cap. */
	public interface Cap {
		@Nullable Grade cap(ServerPlayer smith, SmithingAnvilEntity anvil, Option option);
	}

	/** Something done to a finished head (the maker's mark, a blueprint's use). */
	public interface Finish {
		void finish(ServerPlayer smith, SmithingAnvilEntity anvil, Option option, ItemStack result);
	}

	/** More forgings (armour). */
	public static final List<Supplier<List<Option>>> PROVIDERS = new ArrayList<>();
	public static final List<Check> CHECKS = new ArrayList<>();
	public static final List<Cap> CAPS = new ArrayList<>();
	public static final List<Finish> FINISHES = new ArrayList<>();
	/** Widens the perfect zone (half-width, bar units) for this smith: a Blacksmith's rank. */
	public static final List<ToDoubleFunction<ServerPlayer>> ZONE_BONUS = new ArrayList<>();

	public static float bonus(ServerPlayer smith) {
		double sum = 0;
		for (ToDoubleFunction<ServerPlayer> bonus : ZONE_BONUS) {
			sum += bonus.applyAsDouble(smith);
		}
		return (float) sum;
	}
}
