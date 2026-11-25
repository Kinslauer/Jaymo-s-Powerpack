package com.jaymospowers.core.reader;

import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.blood.BloodParticles;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * The best guess at what a creature bleeds when no blood tag, no op's override and no heritage says: from the vanilla class
 * it extends (a zombie's rotten blood, a skeleton's bones, a slime's goo, an iron golem's sparks, a spider's bug blood, an
 * enderman's ender blood), from its body tag (an arthropod is a bug, a metal body strikes sparks) or from words in its id
 * ({@link #WORDS}). {@link BladeBlood} asks it for everything, and Kitchen's bodies get it through
 * {@code BladeBlood.bodyBlood}, the bridge Kitchen already reads by name.
 */
public final class BloodGuess {
	/** Nothing guessed: the caller's own answer (red) stands. */
	public static final int NO_SAY = BladeBlood.NO_SAY;
	/** Words in an entity id, and what they say: a colour, or no blood. */
	private static final String[] ROTTEN_WORDS = {"zombie", "ghoul", "mummy", "corpse", "undead", "revenant"},
		BONE_WORDS = {"skeleton", "skull", "bones", "ghost", "spectre", "specter", "wraith", "poltergeist"},
		GOO_WORDS = {"slime", "ooze", "jelly", "blob"},
		METAL_WORDS = {"golem", "robot", "mech", "drone", "automaton"},
		BUG_WORDS = {"spider", "beetle", "ant", "scarab", "mantis", "scorpion", "insect", "cricket", "wasp", "roach"},
		ENDER_WORDS = {"ender", "void"},
		MAGMA_WORDS = {"magma", "lava", "ember"};
	private static final TagKey<EntityType<?>> ARTHROPOD = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
		com.jaymospowers.JaymosPowers.id("body/arthropod"));

	private BloodGuess() {
	}

	/** Whether a tag already says what this kind bleeds (any blood tag, or the bloodless tag). */
	public static boolean tagged(EntityType<?> type) {
		var holder = type.builtInRegistryHolder();
		return holder.is(BladeBlood.BLOODLESS) || holder.is(BladeBlood.ENDER_BLOOD) || holder.is(BladeBlood.DARK_BLOOD)
			|| holder.is(BladeBlood.SLIME_BLOOD) || holder.is(BladeBlood.CREEPER_BLOOD) || holder.is(BladeBlood.BUG_BLOOD)
			|| holder.is(BladeBlood.MAGMA_BLOOD) || holder.is(BladeBlood.ROTTEN_BLOOD);
	}

	/**
	 * What {@code body} bleeds by its class, then its tags and its id: an RGB colour, {@link BladeBlood#NO_BLOOD}, or
	 * {@link #NO_SAY}. A body a tag already speaks for is left to the tag.
	 */
	public static int guess(LivingEntity body) {
		if (tagged(body.getType())) {
			return NO_SAY;
		}
		if (body instanceof MagmaCube) {
			return BloodParticles.MAGMA;
		}
		if (body instanceof Zombie) {
			return BladeBlood.ROTTEN;
		}
		if (body instanceof AbstractSkeleton || body instanceof Vex || body instanceof IronGolem) {
			return BladeBlood.NO_BLOOD;
		}
		if (body instanceof Slime) {
			return BladeBlood.SLIME;
		}
		if (body instanceof Spider) {
			return BladeBlood.BUG;
		}
		if (body instanceof Enderman) {
			return BladeBlood.ENDER;
		}
		return guess(body.getType());
	}

	/** {@link #guess(LivingEntity)} from what a type's tags and id say alone. */
	public static int guess(EntityType<?> type) {
		if (tagged(type)) {
			return NO_SAY;
		}
		var holder = type.builtInRegistryHolder();
		if (holder.is(BladeBlood.METAL_BODIES)) {
			return BladeBlood.NO_BLOOD;
		}
		if (holder.is(ARTHROPOD)) {
			return BladeBlood.BUG;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().toLowerCase(Locale.ROOT);
		if (has(path, MAGMA_WORDS)) {
			return BloodParticles.MAGMA;
		}
		if (has(path, ROTTEN_WORDS)) {
			return BladeBlood.ROTTEN;
		}
		if (has(path, BONE_WORDS) || has(path, METAL_WORDS)) {
			return BladeBlood.NO_BLOOD;
		}
		if (has(path, GOO_WORDS)) {
			return BladeBlood.SLIME;
		}
		if (has(path, BUG_WORDS)) {
			return BladeBlood.BUG;
		}
		return has(path, ENDER_WORDS) ? BladeBlood.ENDER : NO_SAY;
	}

	/** Whether a word of {@code words} is in {@code path} as a whole word or a word's start (so "ant" isn't in "giant"). */
	private static boolean has(String path, String[] words) {
		for (String segment : path.split("[_./-]")) {
			for (String word : words) {
				if (segment.equals(word) || segment.startsWith(word) && word.length() > 4) {
					return true;
				}
			}
		}
		return false;
	}
}
