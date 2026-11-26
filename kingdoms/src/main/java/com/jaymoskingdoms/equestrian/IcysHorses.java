package com.jaymoskingdoms.equestrian;

import icy.betterhorses.net.HorseBreed;
import icy.betterhorses.net.IHorseData;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Mule;
import org.jspecify.annotations.Nullable;

/**
 * Icy's Better Horses (Bare Minimum License; in the pack, shipped unchanged) owns ordinary horses: breeds and coats, gaits,
 * bond, tack, revival, the whistle and wheel. This is how the rest of Kingdoms (stables, quests, cavalry, trade routes,
 * couriers, emigrants, races, gatherers) still talks to such a horse: who owns it, how bonded it is, what we'd call its
 * breed and stats. Loaded only when {@link Equestrian#ICY}, since it names Icy's classes.
 */
final class IcysHorses {
	private IcysHorses() {
	}

	/** Icy's breed for each of ours (the war elephant, mammoth, donkey and mule have none). */
	static String icyBreed(Breed breed) {
		return switch (breed) {
			case DESTRIER -> "andalusian";
			case COURSER -> "thoroughbred";
			case PALFREY -> "morgan";
			case DRAFT -> "percheron";
			case STEPPE_PONY -> "icelandic";
			case DESERT_HORSE -> "arabian";
			// the nightmare is a black Friesian, the bat horse a wild Mustang: their legend stays ours (MythicMounts)
			case NIGHTMARE -> "friesian";
			case BAT_HORSE -> "mustang";
			default -> "morgan";
		};
	}

	/** A new horse of Icy's breed standing for {@code breed}, or null if Icy's isn't there. */
	static @Nullable AbstractHorse create(ServerLevel level, Breed breed, EntitySpawnReason reason) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath("icys-better-horses",
			icyBreed(breed) + "_horse")).orElse(null);
		return type != null && type.create(level, reason) instanceof AbstractHorse horse ? horse : null;
	}

	/** Which of our breeds Icy's breed of {@code horse} corresponds to (for prices and tables). */
	static Breed ours(AbstractHorse horse) {
		HorseBreed b = data(horse).bh_getBreed();
		if (b == null) {
			return horse instanceof Donkey ? Breed.DONKEY : horse instanceof Mule ? Breed.MULE : Breed.PALFREY;
		}
		return switch (b) {
			case THOROUGHBRED, QUARTER -> Breed.COURSER;
			case ARABIAN -> Breed.DESERT_HORSE;
			case FRIESIAN, ANDALUSIAN, MUSTANG -> Breed.DESTRIER;
			case PERCHERON, CLYDESDALE, SHIRE, BELGIAN -> Breed.DRAFT;
			case ICELANDIC, HAFLINGER -> Breed.STEPPE_PONY;
			case DONKEY_SPECIES -> Breed.DONKEY;
			case MULE_SPECIES -> Breed.MULE;
			default -> Breed.PALFREY;
		};
	}

	/** Its stats as Icy's horse stands: speed and jump from its attributes (Icy's clamps them to its class), the rest middling. */
	static Equine read(AbstractHorse horse) {
		float speed = (float)Math.max(0.0, Math.min(1.0, (horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.1125) / 0.225));
		float jump = (float)Math.max(0.0, Math.min(1.0, (horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH) - 0.4) / 0.6));
		return new Equine(ours(horse), 0, speed, 0.5F, jump, 0.5F, 0.5F);
	}

	static IHorseData data(AbstractHorse horse) {
		return (IHorseData)horse;
	}

	/** Whether {@code id} is its owner by Icy's reckoning (vanilla's owner is checked by the caller too). */
	static boolean owner(AbstractHorse horse, UUID id) {
		return id.equals(data(horse).bh_getOwner());
	}

	/** Its bond with its owner, 0 to 100 (Icy's; what a later patch reads for a fully bonded horse). */
	static int bond(AbstractHorse horse) {
		return data(horse).bh_getBond();
	}

	/** Icy's draft class: the horses a coach is hitched to. */
	static boolean draft(AbstractHorse horse) {
		HorseBreed b = data(horse).bh_getBreed();
		return b == HorseBreed.PERCHERON || b == HorseBreed.CLYDESDALE || b == HorseBreed.SHIRE || b == HorseBreed.BELGIAN;
	}

	/**
	 * A horse that carries our old breed data under Icy's rule: our size is undone (Icy's clamps health, speed and jump to its own
	 * classes when it converts a horse, but never touches scale) and the coach tag follows Icy's breed.
	 */
	static void release(AbstractHorse horse) {
		AttributeInstance scale = horse.getAttribute(Attributes.SCALE);
		if (scale != null && horse.hasAttached(Equestrian.EQUINE) && scale.getBaseValue() != 1.0) {
			scale.setBaseValue(1.0);
			horse.refreshDimensions();
		}
		if (draft(horse)) {
			horse.addTag(Equestrian.DRAFT_TAG);
		} else {
			horse.removeTag(Equestrian.DRAFT_TAG);
		}
	}
}
