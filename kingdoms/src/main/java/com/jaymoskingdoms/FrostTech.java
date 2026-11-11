package com.jaymoskingdoms;

import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Andy (2026-11-11): the Frostborn "made all the scouters, rigs, mechs, prosthetics and various other tech you find in
 * the otherwise medieval overworld. The people speak on it. They love having frostborn tech. They are a proud people,
 * and will often have their heritage's inventions unless they are born and raised on the overworld... Frostborn should
 * be shown with their scouters and prosthetics more often."
 * <p>
 * What a grown Frostborn villager wears of it, worked out from who they are (their UUID), so it never changes, needs no
 * saving and reads the same on the server and every client: {@link #RAISED_HERE} of them were born and raised on the
 * Overworld and wear none; the rest wear a scouter ({@link #SCOUTER}), a made arm ({@link #ARM}) or a made leg
 * ({@link #LEG}), and always at least one. It is a look and a thing to talk about ({@link #talk}, {@link #envy}): the
 * scouter reads nothing for them and the limb is as good as their own. The client's {@code FrostTechLayer} draws it.
 * A Frostborn player gets theirs from the life story (raised by the starwrights).
 */
public final class FrostTech {
	/** The share born and raised on the Overworld: none of their people's work on them. */
	static final float RAISED_HERE = 0.35F;
	/** Of the rest: who wears a scouter, who a made arm, who a made leg (each rolled on its own). */
	static final float SCOUTER = 0.7F, ARM = 0.3F, LEG = 0.12F;
	/** When neighbours' talk turns to a Frostborn near them who wears it: how often it is of the work, and the lines there are. */
	static final float ENVY_CHANCE = 0.5F;
	static final int ENVY_LINES = 4;

	/** The bits of {@link #of}: the scouter, and the limb that is Frostborn-made. */
	public static final int SCOUTER_BIT = 1, RIGHT_ARM = 2, LEFT_ARM = 4, RIGHT_LEG = 8, LEFT_LEG = 16;
	public static final int LIMBS = RIGHT_ARM | LEFT_ARM | RIGHT_LEG | LEFT_LEG;

	/** Jaymo's Powers' scouter and iron arm, for the player raised among the starwrights. */
	private static final String SCOUTER_ITEM = "jaymospowers:scouter", ARM_ITEM = "jaymospowers:iron_arm";

	private FrostTech() {
	}

	/** Whether {@code entity} is a grown Frostborn villager (never a player: theirs is real gear). */
	private static boolean frostborn(Entity entity) {
		return !(entity instanceof Player) && entity instanceof LivingEntity living && !living.isBaby()
			&& VillagerHeritage.of(entity) == Heritage.Kind.FROSTBORN;
	}

	/** A number from 0 to 1 that is always the same for this body and this question. */
	private static float roll(UUID id, long salt) {
		long x = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 29) ^ salt * 0x9E3779B97F4A7C15L;
		x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
		x = (x ^ x >>> 27) * 0x94D049BB133111EBL;
		x ^= x >>> 31;
		return (x >>> 40) / (float)(1 << 24);
	}

	/** Whether this Frostborn villager was born and raised on the Overworld (so wears none of it). False for anyone else. */
	public static boolean raisedHere(Entity entity) {
		return frostborn(entity) && roll(entity.getUUID(), 1) < RAISED_HERE;
	}

	/** What {@code entity} wears of their people's work: {@link #SCOUTER_BIT} and the limb bits, 0 for none. */
	public static int of(Entity entity) {
		if (!frostborn(entity)) {
			return 0;
		}
		UUID id = entity.getUUID();
		if (roll(id, 1) < RAISED_HERE) {
			return 0;
		}
		int worn = 0;
		if (roll(id, 2) < SCOUTER) {
			worn |= SCOUTER_BIT;
		}
		if (roll(id, 3) < ARM) {
			worn |= roll(id, 4) < 0.5F ? RIGHT_ARM : LEFT_ARM;
		}
		if (roll(id, 5) < LEG) {
			worn |= roll(id, 6) < 0.5F ? RIGHT_LEG : LEFT_LEG;
		}
		return worn == 0 ? SCOUTER_BIT : worn;
	}

	/**
	 * Which of the blood lines ({@code dialogue.chat.jaymoskingdoms.blood_<this>}) a Frostborn speaks: of the scouter
	 * or the limb they wear (one of the two when they wear both), or the Overworld-raised one's own ({@code frostborn}).
	 */
	static String talk(Entity villager, RandomSource random) {
		int worn = of(villager);
		boolean scouter = (worn & SCOUTER_BIT) != 0, limb = (worn & LIMBS) != 0;
		if (scouter && limb) {
			return random.nextBoolean() ? "frostborn_scouter" : "frostborn_limb";
		}
		return scouter ? "frostborn_scouter" : limb ? "frostborn_limb" : Heritage.Kind.FROSTBORN.key();
	}

	/** A neighbour's line about the Frostborn's work ({@code talk.jaymoskingdoms.neighbours.frost_tech.<n>}), when it comes up. */
	static @org.jspecify.annotations.Nullable String envy(RandomSource random) {
		return random.nextFloat() < ENVY_CHANCE ? "talk.jaymoskingdoms.neighbours.frost_tech." + (1 + random.nextInt(ENVY_LINES)) : null;
	}

	/** The scouter a starwright's child leaves home with (empty without Jaymo's Powers), and the spare arm in their pack. */
	static ItemStack scouter() {
		return item(SCOUTER_ITEM);
	}

	static ItemStack spareArm() {
		return item(ARM_ITEM);
	}

	private static ItemStack item(String id) {
		return BuiltInRegistries.ITEM.get(Identifier.parse(id)).map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
	}
}
