package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): the Frostborn "made all the scouters, rigs, mechs, prosthetics and various other tech you find in
 * the otherwise medieval overworld. The people speak on it. They love having frostborn tech. They are a proud people,
 * and will often have their heritage's inventions unless they are born and raised on the overworld... Frostborn should
 * be shown with their scouters and prosthetics more often." And then: "Any of the villagers can now have these, at
 * different rates and will seek out prosthetics if they lose limbs. They complain about not having a nicer one if its
 * wooden. Galatic patrolmen all need a scouter and are rarely without it."
 * <p>
 * <b>What they start with</b> ({@link #innate}) is worked out from who they are (their UUID), so it never changes, needs
 * no saving and reads the same on the server and every client. A Frostborn not raised on the Overworld
 * ({@link #RAISED_HERE} of them were) wears a scouter ({@link #SCOUTER}), a made arm ({@link #ARM}) or a made leg
 * ({@link #LEG}), and always at least one. Anyone else, an Overworld-raised Frostborn included, only now and then
 * ({@link #OTHER_SCOUTER}, {@link #OTHER_ARM}, {@link #OTHER_LEG}; the star-travelled bloods a little more often,
 * {@link #TRAVELLED}). A galactic patrolman wears a scouter unless he is the rare one without ({@link #PATROL_BARE}).
 * These are a look and a thing to talk about: the scouter reads nothing for them and the limb is as good as their own.
 * <p>
 * <b>What they get after losing a limb</b> ({@link #FITTED}, saved and synced): a villager who has lost an arm or a leg
 * (Jaymo's Powers' dismemberment) waits a while ({@link #SEEK_AFTER}), then walks to the nearest smith ({@link #SMITH_RANGE})
 * and is fitted there; with no smith to reach, or none reached in time ({@link #SEEK_FOR}), they whittle a wooden one
 * themselves. A Frostborn who carries their people's work, or anyone fitted by one, gets a Frostborn-made limb; the
 * rest get iron ({@link #IRON_SHARE}) or wood. One with a wooden limb complains of it ({@link #grumble}). A limb that
 * grows back takes its stand-in off.
 * <p>
 * The client's {@code FrostTechLayer} draws all of it. A Frostborn player gets theirs from the life story (raised by
 * the starwrights).
 */
public final class FrostTech {
	/** The share of Frostborn born and raised on the Overworld: they wear it no more often than anyone else. */
	static final float RAISED_HERE = 0.35F;
	/** Of the other Frostborn: who wears a scouter, who a made arm, who a made leg (each rolled on its own). */
	static final float SCOUTER = 0.7F, ARM = 0.3F, LEG = 0.12F;
	/** Everyone else: bought, traded for or handed down. The bloods that crossed the void wear it this many times as often. */
	static final float OTHER_SCOUTER = 0.05F, OTHER_ARM = 0.03F, OTHER_LEG = 0.015F, TRAVELLED = 2.0F;
	/** The rare galactic patrolman without a scouter. */
	static final float PATROL_BARE = 0.04F;
	/** When neighbours' talk turns to someone near them who wears it: how often it is of the work, and the lines there are. */
	static final float ENVY_CHANCE = 0.5F;
	static final int ENVY_LINES = 4;
	/** How often one with a wooden limb, come over to gossip, grumbles about it instead, and the lines there are. */
	static final float GRUMBLE_CHANCE = 0.35F;
	static final int GRUMBLE_LINES = 4;
	/** After losing a limb: seconds before they go looking, seconds they keep trying to reach a smith, and how far off one may be. */
	static final int SEEK_AFTER = 90, SEEK_FOR = 120;
	static final double SMITH_RANGE = 48.0, FIT_REACH = 3.0;
	static final float SEEK_SPEED = 0.6F;
	/** A smith's limb that isn't Frostborn-made: how often it is iron rather than wood. */
	static final float IRON_SHARE = 0.4F;

	/** The bits of {@link #of}: the scouter, and each limb (Jaymo's Powers' own limb bits). */
	public static final int SCOUTER_BIT = 1, RIGHT_ARM = 2, LEFT_ARM = 4, RIGHT_LEG = 8, LEFT_LEG = 16;
	public static final int LIMBS = RIGHT_ARM | LEFT_ARM | RIGHT_LEG | LEFT_LEG;
	/** What a stand-in limb is made of ({@link #kind}). */
	public static final int NONE = 0, WOOD = 1, IRON = 2, MADE = 3;
	private static final int IRON_SHIFT = 8, MADE_SHIFT = 16;

	/**
	 * The limbs a villager was fitted with after losing them: the wooden ones' bits, the iron ones' shifted by 8, the
	 * Frostborn-made ones' by 16. Saved, and synced to everyone (it is drawn).
	 */
	public static final AttachmentType<Integer> FITTED = AttachmentRegistry.create(JaymosKingdoms.id("fitted_limbs"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Jaymo's Powers' scouter and iron arm, for the player raised among the starwrights. */
	private static final String SCOUTER_ITEM = "jaymospowers:scouter", ARM_ITEM = "jaymospowers:iron_arm";

	/** Who has lost a limb and has no stand-in yet: when they were first seen so (seconds of the server's clock). */
	private static final Map<UUID, Integer> WAITING = new HashMap<>();

	private FrostTech() {
	}

	// ---- who wears what ------------------------------------------------------------------------------------------------

	private static boolean patrolman(Entity entity) {
		return "patrolman".equals(entity.getAttached(ZFighterFolk.PART));
	}

	/** Whether {@code entity} is a grown villager of some blood, or a patrolman (never a player: theirs is real gear). */
	private static boolean wearer(Entity entity) {
		return !(entity instanceof Player) && entity instanceof LivingEntity living && !living.isBaby()
			&& (VillagerHeritage.of(entity) != null || patrolman(entity));
	}

	/** A number from 0 to 1 that is always the same for this body and this question. */
	private static float roll(UUID id, long salt) {
		long x = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 29) ^ salt * 0x9E3779B97F4A7C15L;
		x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
		x = (x ^ x >>> 27) * 0x94D049BB133111EBL;
		x ^= x >>> 31;
		return (x >>> 40) / (float)(1 << 24);
	}

	/** Whether this Frostborn villager was born and raised on the Overworld. False for anyone else. */
	public static boolean raisedHere(Entity entity) {
		return wearer(entity) && VillagerHeritage.of(entity) == Heritage.Kind.FROSTBORN && roll(entity.getUUID(), 1) < RAISED_HERE;
	}

	/** A Frostborn who carries their people's work (not Overworld-raised). */
	private static boolean proud(Entity entity) {
		return wearer(entity) && VillagerHeritage.of(entity) == Heritage.Kind.FROSTBORN && roll(entity.getUUID(), 1) >= RAISED_HERE;
	}

	/** What {@code entity} has always worn: {@link #SCOUTER_BIT} and the made limbs' bits, 0 for none. */
	private static int innate(Entity entity) {
		if (!wearer(entity)) {
			return 0;
		}
		UUID id = entity.getUUID();
		Heritage.Kind blood = VillagerHeritage.of(entity);
		boolean proud = blood == Heritage.Kind.FROSTBORN && roll(id, 1) >= RAISED_HERE;
		float often = blood == Heritage.Kind.WARRIORBORN || blood == Heritage.Kind.EARTHLING ? TRAVELLED : 1.0F;
		int worn = 0;
		if (roll(id, 2) < (proud ? SCOUTER : OTHER_SCOUTER * often)) {
			worn |= SCOUTER_BIT;
		}
		if (roll(id, 3) < (proud ? ARM : OTHER_ARM * often)) {
			worn |= roll(id, 4) < 0.5F ? RIGHT_ARM : LEFT_ARM;
		}
		if (roll(id, 5) < (proud ? LEG : OTHER_LEG * often)) {
			worn |= roll(id, 6) < 0.5F ? RIGHT_LEG : LEFT_LEG;
		}
		if (proud && worn == 0) {
			worn = SCOUTER_BIT;
		}
		if (patrolman(entity)) {
			worn = roll(id, 7) < PATROL_BARE ? worn & ~SCOUTER_BIT : worn | SCOUTER_BIT;
		}
		return worn;
	}

	private static int fitted(Entity entity) {
		Integer mask = entity.getAttached(FITTED);
		return mask == null ? 0 : mask;
	}

	/** What {@code entity} wears: {@link #SCOUTER_BIT} and every limb that is a stand-in of any make, 0 for none. */
	public static int of(Entity entity) {
		int fitted = fitted(entity);
		return innate(entity) | (fitted | fitted >> IRON_SHIFT | fitted >> MADE_SHIFT) & LIMBS;
	}

	/** What the stand-in on {@code limb} (one limb bit) is made of: {@link #WOOD}, {@link #IRON}, {@link #MADE}, or {@link #NONE}. */
	public static int kind(Entity entity, int limb) {
		int fitted = fitted(entity);
		if ((fitted & limb) != 0) {
			return WOOD;
		}
		if ((fitted >> IRON_SHIFT & limb) != 0) {
			return IRON;
		}
		return (fitted >> MADE_SHIFT & limb) != 0 || (innate(entity) & limb) != 0 ? MADE : NONE;
	}

	/** Each limb's make packed two bits a limb (right arm, left arm, right leg, left leg from the low end), for drawing. */
	public static int kinds(Entity entity) {
		return kind(entity, RIGHT_ARM) | kind(entity, LEFT_ARM) << 2 | kind(entity, RIGHT_LEG) << 4 | kind(entity, LEFT_LEG) << 6;
	}

	/** Whether {@code entity} wears Frostborn work: a scouter or a made limb (not a smith's wood or iron). */
	public static boolean wearsWork(Entity entity) {
		if ((innate(entity)) != 0) {
			return true;
		}
		return (fitted(entity) >> MADE_SHIFT & LIMBS) != 0;
	}

	/** Whether {@code entity} has a wooden limb. */
	public static boolean wooden(Entity entity) {
		return (fitted(entity) & LIMBS) != 0;
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	/**
	 * Which of the blood lines ({@code dialogue.chat.jaymoskingdoms.blood_<this>}) a Frostborn speaks: of the scouter
	 * or the made limb they wear (one of the two when they wear both), or the plain ones ({@code frostborn}).
	 */
	static String talk(Entity villager, RandomSource random) {
		boolean scouter = (innate(villager) & SCOUTER_BIT) != 0, limb = false;
		for (int bit : new int[] {RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG}) {
			limb |= kind(villager, bit) == MADE;
		}
		if (scouter && limb) {
			return random.nextBoolean() ? "frostborn_scouter" : "frostborn_limb";
		}
		return scouter ? "frostborn_scouter" : limb ? "frostborn_limb" : Heritage.Kind.FROSTBORN.key();
	}

	/** A neighbour's line about the Frostborn's work ({@code talk.jaymoskingdoms.neighbours.frost_tech.<n>}), when it comes up. */
	static @Nullable String envy(RandomSource random) {
		return random.nextFloat() < ENVY_CHANCE ? "talk.jaymoskingdoms.neighbours.frost_tech." + (1 + random.nextInt(ENVY_LINES)) : null;
	}

	/** What one with a wooden limb says of it when they come over ({@code talk.jaymoskingdoms.wooden_limb.<n>}); null if not this time. */
	static @Nullable String grumble(Entity villager, RandomSource random) {
		return wooden(villager) && random.nextFloat() < GRUMBLE_CHANCE ? "talk.jaymoskingdoms.wooden_limb." + (1 + random.nextInt(GRUMBLE_LINES)) : null;
	}

	// ---- losing a limb, and finding another ----------------------------------------------------------------------------

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
	}

	private static boolean smith(Entity entity) {
		if (!(entity instanceof Villager v) || v.isBaby()) {
			return false;
		}
		var job = v.getVillagerData().profession();
		return job.is(VillagerProfession.ARMORER) || job.is(VillagerProfession.TOOLSMITH) || job.is(VillagerProfession.WEAPONSMITH);
	}

	/**
	 * Once a second for each villager ({@link VillagerHeritage}'s own beat): a stand-in whose limb grew back comes off;
	 * one missing a limb with nothing on it waits, then goes to a smith, and is fitted there (or by their own hand).
	 */
	static void tick(LivingEntity villager, int second) {
		int fitted = fitted(villager);
		int lost = PowersCompat.missingLimbs(villager) & LIMBS;
		if (fitted == 0 && lost == 0) {
			return;
		}
		int grown = (fitted | fitted >> IRON_SHIFT | fitted >> MADE_SHIFT) & LIMBS & ~lost;
		if (grown != 0) {
			fitted &= ~(grown | grown << IRON_SHIFT | grown << MADE_SHIFT);
			store(villager, fitted);
		}
		// (a limb they have always had a made one on is their own under it, so only real losses count)
		int bare = lost & ~(fitted | fitted >> IRON_SHIFT | fitted >> MADE_SHIFT);
		UUID id = villager.getUUID();
		if (bare == 0 || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			WAITING.remove(id);
			return;
		}
		int since = second - WAITING.computeIfAbsent(id, k -> second);
		if (since < SEEK_AFTER) {
			return;
		}
		LivingEntity fitter = smith(villager) ? villager : null;
		if (fitter == null && since < SEEK_AFTER + SEEK_FOR && villager instanceof Villager walker && !walker.isSleeping() && !walker.isPassenger()) {
			Villager nearest = null;
			for (Villager v : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(SMITH_RANGE), e -> e != villager && e.isAlive() && smith(e))) {
				if (nearest == null || v.distanceToSqr(villager) < nearest.distanceToSqr(villager)) {
					nearest = v;
				}
			}
			if (nearest != null) {
				if (nearest.distanceToSqr(villager) > FIT_REACH * FIT_REACH) {
					BehaviorUtils.setWalkAndLookTargetMemories(walker, new EntityTracker(nearest, true), SEEK_SPEED, 2);
					return;
				}
				walker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				fitter = nearest;
			}
		}
		// fitted: by a smith, or (none near, or none reached) a wooden one of their own whittling
		int make = proud(villager) || fitter != null && proud(fitter) ? MADE
			: fitter != null && villager.getRandom().nextFloat() < IRON_SHARE ? IRON : WOOD;
		store(villager, fitted | bare << (make == MADE ? MADE_SHIFT : make == IRON ? IRON_SHIFT : 0));
		WAITING.remove(id);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), make == WOOD ? SoundEvents.WOOD_PLACE : SoundEvents.ANVIL_USE,
			SoundSource.NEUTRAL, 0.7F, make == WOOD ? 0.9F : 1.4F);
	}

	private static void store(LivingEntity villager, int fitted) {
		if (fitted == 0) {
			villager.removeAttached(FITTED);
		} else {
			villager.setAttached(FITTED, fitted);
		}
	}

	// ---- the player raised among the starwrights -----------------------------------------------------------------------

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
