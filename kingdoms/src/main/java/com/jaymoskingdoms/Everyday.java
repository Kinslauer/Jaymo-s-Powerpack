package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Everyday dress in the harness (Andy, 2026-11-25: "most people should opt for harness gear"). The harness is not only plate:
 * its under-layers (a shirt, a tunic or a vest; hose or breeches) and the hood and cloak are what ordinary people wear, so
 * most of a settlement's people wear them: its working folk (farmers, miners, woodcutters, carters, builders, stable hands),
 * and the villagers who are not guards (a guard's kit is {@link GuardKit}'s, a knight's {@link HeraldryWear}'s, a raider's
 * and a bandit's their own).
 * <p>
 * Plain cloth gives no armour, so dressing a crowd changes no fight: it only shows on the body, and on the body's loot
 * screen when it falls (Jaymo's Kitchen's). Plate stays where Lore.md puts it ("Plate belongs to knights and to those who can
 * pay for it"). Who is dressed, and in what, is worked out from the person's own id, so the same person always wears the
 * same, whenever they are looked at.
 */
final class Everyday {
	/** The share of people who dress in the harness at all ("most"). */
	static final float SHARE = 0.8F;
	/** The share of those who add a hood, and a cloak, in mild weather and in the cold. */
	static final float HOOD_SHARE = 0.35F, COLD_HOOD_SHARE = 0.65F, CLOAK_SHARE = 0.25F, COLD_CLOAK_SHARE = 0.5F;
	/** How often (ticks) the villagers round each player are looked at, and how far. */
	static final int SWEEP = 100;
	static final double SWEEP_RANGE = 48.0;
	/** On a villager once their dress has been decided, so a villager someone has stripped isn't dressed again. */
	static final String TAG = "jaymoskingdoms.everyday";
	private static final Set<Climate> HOT = EnumSet.of(Climate.SAVANNA, Climate.DESERT, Climate.BADLANDS, Climate.JUNGLE),
		COLD = EnumSet.of(Climate.TAIGA, Climate.SNOWY);
	private static final List<String> SHIRTS = List.of("linen_shirt", "wool_tunic", "cloth_vest"), HOT_SHIRTS = List.of("linen_shirt", "cloth_vest"),
		COLD_SHIRTS = List.of("wool_tunic", "linen_shirt"), HOSE = List.of("breeches", "linen_hose", "joined_hose"),
		HOT_HOSE = List.of("shorts", "breeches", "linen_hose"), COLD_HOSE = List.of("joined_hose", "breeches", "linen_hose");

	private Everyday() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					sweep(level);
				}
			}
		});
	}

	/** A working person of {@code folk}'s settlement (not a fighter or a ruler) puts on their everyday dress; from {@link HeraldryWear#folk}. */
	static void folk(Folk folk, Climate climate) {
		if (!folk.combatant() && !Rulers.leaderRole(folk.role())) {
			dress(folk, climate);
		}
	}

	/** The villagers round each player who are not yet dressed are, once. */
	private static void sweep(ServerLevel level) {
		List<Villager> seen = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SWEEP_RANGE), Everyday::undecided)) {
				if (!seen.contains(v)) {
					seen.add(v);
				}
			}
		}
		for (Villager v : seen) {
			v.addTag(TAG);
			dress(v, Climate.at(level, v.blockPosition()));
		}
	}

	/** A grown villager no one has dressed or armed: not a guard (a kit's), not already in harness, not a ruler or someone downed. */
	private static boolean undecided(Villager v) {
		return v.isAlive() && !v.isBaby() && !v.entityTags().contains(TAG) && !v.hasAttached(Harness.WORN) && !Downed.isGuard(v) && !Downed.isDowned(v)
			&& !Rulers.is(v) && v.getAttached(com.jaymoskingdoms.dragons.Disguises.DISGUISE) == null;
	}

	/** Puts {@code mob}'s everyday dress on it, if it is one of the {@link #SHARE}: a shirt, hose, and for some a hood and a cloak. */
	private static void dress(Mob mob, Climate climate) {
		long h = mix(mob.getUUID().getLeastSignificantBits() ^ mob.getUUID().getMostSignificantBits());
		if (unit(h, 0) >= SHARE) {
			return;
		}
		boolean cold = COLD.contains(climate), hot = HOT.contains(climate);
		List<ItemStack> on = new ArrayList<>();
		on.add(HeraldryArmour.stack(pick(cold ? COLD_SHIRTS : hot ? HOT_SHIRTS : SHIRTS, h >>> 12)));
		on.add(HeraldryArmour.stack(pick(cold ? COLD_HOSE : hot ? HOT_HOSE : HOSE, h >>> 24)));
		if (unit(h, 36) < (cold ? COLD_HOOD_SHARE : hot ? 0.0F : HOOD_SHARE)) {
			on.add(HeraldryArmour.stack("hood"));
		}
		if (unit(h, 46) < (cold ? COLD_CLOAK_SHARE : hot ? 0.0F : CLOAK_SHARE)) {
			on.add(HeraldryArmour.stack("cloak"));
		}
		Harness.dress(mob, on.toArray(ItemStack[]::new));
	}

	/** Ten bits of {@code h} from {@code shift}, as a number from 0 up to (not including) 1. */
	private static float unit(long h, int shift) {
		return (h >>> shift & 0x3FFL) / 1024.0F;
	}

	private static String pick(List<String> from, long h) {
		return from.get((int) ((h & 0xFFFFL) % from.size()));
	}

	private static long mix(long z) {
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		return z ^ z >>> 31;
	}
}
