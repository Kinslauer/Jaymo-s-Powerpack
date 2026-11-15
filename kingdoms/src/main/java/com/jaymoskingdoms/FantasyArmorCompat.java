package com.jaymoskingdoms;

import com.jaymoskingdoms.barrow.Revenant;
import com.jaymoskingdoms.barrow.ZombieKnight;
import com.jaymoskingdoms.remnants.Monoliths;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Fantasy Armor (mod id {@value #MOD}, kenddie's Medieval Series; all rights reserved, nothing of it copied here) puts its
 * sets on the world's people where it's loaded, by registry id, so there's no dependency. Only on monsters, the undead
 * and kings: its pieces are made over netherite, the crown's tier (Andy, 2026-10-29: kings wear netherite, nobody below
 * them may), so royal tiers still hold. The risen dead now and then come up in a whole dark set at the higher world tiers
 * ({@link MonsterGear}); a king in gilded attire wears a gilded set instead of his trimmed netherite ({@link Regalia}).
 * The Grave Sentinel's helm is never put on anyone (Andy, 2026-11-15: "the grave sentinel helmet should be removed").
 * <p>
 * The armourers' orders in Lore.md ("What the armourers make") wear Fantasy Armor's sets (Andy chose its models over
 * new art, 2026-11-15), each order the set below. So far:
 * <ul>
 * <li><b>Barrow wardens' mail</b> ({@link #BARROW}): it falls from the barrow's dead ({@link #BARROW_DROP}), and whoever
 * wears all of it walks in the barrows as the knights' own ({@link Revenant#keeper}).</li>
 * <li><b>Giant-slayers' harness</b> ({@link #GIANT_SLAYER}): when a Giant falls, a plate of it lies where it fell for
 * each who was there ({@link #GIANT_WITNESS} blocks).</li>
 * <li><b>Folded lamellar</b> ({@link #FOLDED}): some of the blossom lords' guards wear it ({@link Outfits#ronin}).</li>
 * </ul>
 */
public final class FantasyArmorCompat {
	public static final String MOD = "fantasy_armor";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/**
	 * The orders' sets (Lore.md, "What the armourers make"): the barrow wardens', the dawnwatch's, the headsmen's, the
	 * Giant-slayers', the sky folk elders' robes, the well-wardens', the demons' ashen plate, the Warden's hunters', the
	 * blossom guards' folded lamellar, the pit champions' and the paired harness sworn back to back.
	 */
	public static final String BARROW = "grave_sentinel", DAWNWATCH = "dawncrest", HEADSMAN = "golden_execution", GIANT_SLAYER = "dragonslayer",
		SKY_ROBES = "wind_worshipper", WELL_WARDEN = "fog_guard", ASHEN = "dark_lord", HUNTER = "evening_ghost", FOLDED = "ronin",
		PIT = "dead_gladiator", TWINNED = "twinned";
	/** The sets the risen dead come up in, and the one a king wears (none of them another order's). */
	static final List<String> UNDEAD_SETS = List.of("dark_cover", BARROW);
	static final List<String> KING_SETS = List.of("gilded_hunt");
	/** The chance a barrow's dead drop a piece of the wardens' mail: a household knight, and the revenant his lord. */
	static final float[] BARROW_DROP = {0.08F, 0.6F};
	/** How near a falling Giant one has to be to have been there for its fall (blocks). */
	static final double GIANT_WITNESS = 48.0;
	/** The share of the blossom guards in folded lamellar (the rest in the Armory's ronin harness). */
	static final float FOLDED_CHANCE = 0.35F;
	/** A set whose helm nobody wears (its slot is left as it was). */
	static final String NO_HELM = "grave_sentinel";
	private static final String[] PIECES = {"_helmet", "_chestplate", "_leggings", "_boots"};
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private FantasyArmorCompat() {
	}

	static void register() {
		if (!LOADED) {
			return;
		}
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			RandomSource random = entity.getRandom();
			float barrow = entity instanceof Revenant ? BARROW_DROP[1] : entity instanceof ZombieKnight ? BARROW_DROP[0] : 0.0F;
			if (barrow > 0.0F && random.nextFloat() < barrow) {
				// (its helm is never handed out: the body, the legs or the boots)
				piece(BARROW, 1 + random.nextInt(3)).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
			}
			if (Monoliths.giant(entity)) {
				for (Player player : level.players()) {
					if (!player.isSpectator() && player.distanceToSqr(entity) < GIANT_WITNESS * GIANT_WITNESS) {
						piece(GIANT_SLAYER, random.nextInt(4)).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
					}
				}
			}
		});
	}

	/** {@code e} has on every piece of {@code set} anyone wears (the barrow wardens' mail: all but the helm). */
	public static boolean whole(LivingEntity e, String set) {
		if (!LOADED) {
			return false;
		}
		for (int i = 0; i < SLOTS.length; i++) {
			Optional<Item> piece = piece(set, i);
			if (piece.isPresent() && !e.getItemBySlot(SLOTS[i]).is(piece.get())) {
				return false;
			}
		}
		return true;
	}

	/** {@code e} in every piece of {@code set}; false (nothing changed) where the mod or the set isn't there. */
	static boolean dress(LivingEntity e, String set) {
		boolean any = false;
		for (int i = 0; i < SLOTS.length; i++) {
			Optional<Item> piece = piece(set, i);
			if (piece.isPresent()) {
				e.setItemSlot(SLOTS[i], new ItemStack(piece.get()));
				any = true;
			}
		}
		return any;
	}

	/** One of {@code sets}, at random, or null where the mod isn't loaded. */
	static String pick(List<String> sets, RandomSource random) {
		return LOADED ? sets.get(random.nextInt(sets.size())) : null;
	}

	/** {@code set}'s piece for slot {@code i} (0 the helm to 3 the boots), if it's there and anyone may wear it. */
	static Optional<Item> piece(String set, int i) {
		if (set == null || i == 0 && set.equals(NO_HELM)) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(MOD, set + PIECES[i]));
	}
}
