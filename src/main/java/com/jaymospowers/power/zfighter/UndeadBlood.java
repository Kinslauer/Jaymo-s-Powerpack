package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.Level;

/**
 * The Warrior-born dead. Andy (2026-11-18): "there should be rare warrior born zombies with the same decrepit rotting
 * tails, that have a chance to turn during the moons." A few of the Overworld's old dead (zombies, husks and zombie
 * villagers, {@link #WARRIOR_CHANCE}) rise Warrior-born, rolled once when each is first in the world; and an MCA villager
 * who was Warrior-born stays it when it turns (the game copies its tags onto the zombie). In death the tail isn't kept
 * wrapped any more: it hangs, rotted and mangled the way the future's dead's are (the client's {@code FutureDeadLooks}),
 * or is a stump on one whose tail was cut.
 *
 * <p>The tags are Kingdoms' own ({@link VillagerApe#WARRIOR}, {@link VillagerApe#TAIL_WRAPPED} for a tail still on it,
 * {@link VillagerApe#TAIL_CUT}), so a dead one with its tail turns into the wild Great Ape under the moon as a living
 * villager does ({@link VillagerApe}), and its tail can be cut off the same way. Tags aren't seen by clients, so what its
 * tail is goes to them on {@link #TAIL}.
 */
public final class UndeadBlood {
	/** The share of the Overworld's old dead that rise Warrior-born. */
	static final float WARRIOR_CHANCE = 0.03F;
	/** Of those, the share with a tail still on them (the rest have only a stump, and never turn). */
	static final float TAIL_CHANCE = 0.75F;

	/** Kingdoms' heritage tags all start with this. */
	static final String HERITAGE_TAG = "jaymoskingdoms_heritage_";

	/** Whether it has been rolled for. Saved. */
	static final AttachmentType<Boolean> ROLLED = AttachmentRegistry.create(JaymosPowers.id("undead_blood_rolled"),
		builder -> builder.persistent(Codec.BOOL));
	/** On a Warrior-born dead: true while its tail is on it, false for a stump. Saved, and seen by everyone. */
	public static final AttachmentType<Boolean> TAIL = AttachmentRegistry.create(JaymosPowers.id("undead_tail"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private UndeadBlood() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register(UndeadBlood::loaded);
	}

	/** The old dead it can be: a zombie, a husk, a zombie villager (MCA's too); not drowned, and none of our own zombies. */
	static boolean kind(Entity entity) {
		return entity.getType() == EntityTypes.ZOMBIE || entity.getType() == EntityTypes.HUSK || entity instanceof ZombieVillager;
	}

	/** One of the Warrior-born dead. */
	static boolean dead(Entity entity) {
		return entity instanceof Zombie && kind(entity) && entity.entityTags().contains(VillagerApe.WARRIOR);
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (!kind(entity)) {
			return;
		}
		if (!Boolean.TRUE.equals(entity.getAttached(ROLLED))) {
			entity.setAttached(ROLLED, true);
			// one that turned already has its blood (an MCA villager's, through its tags); only the Overworld's old dead roll
			if (level.dimension() == Level.OVERWORLD && !blooded(entity.entityTags()) && entity.getRandom().nextFloat() < WARRIOR_CHANCE) {
				entity.addTag(VillagerApe.WARRIOR);
				entity.addTag(entity.getRandom().nextFloat() < TAIL_CHANCE ? VillagerApe.TAIL_WRAPPED : VillagerApe.TAIL_CUT);
			}
		}
		mark(entity);
	}

	private static boolean blooded(Set<String> tags) {
		for (String tag : tags) {
			if (tag.startsWith(HERITAGE_TAG)) {
				return true;
			}
		}
		return false;
	}

	/** Tells everyone what its tail is (on it, or a stump), when it's one of the Warrior-born dead. */
	static void mark(Entity entity) {
		if (!dead(entity)) {
			return;
		}
		Boolean tail = !entity.entityTags().contains(VillagerApe.TAIL_CUT);
		if (!tail.equals(entity.getAttached(TAIL))) {
			entity.setAttached(TAIL, tail);
		}
	}
}
