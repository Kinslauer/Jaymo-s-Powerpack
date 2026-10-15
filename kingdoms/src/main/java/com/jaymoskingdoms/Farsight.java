package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.raid.Raider;

/**
 * (0.7.55) Monsters see farther, after Farsighted Mobs (LGPL-3, remade here: no 26.3 Fabric build). Vanilla skeletons,
 * creepers and spiders notice you at 16 blocks, so standing still kept you safe; now every monster sees at least
 * {@link #SIGHT} blocks, {@link #HARD_BONUS} more on Hard, and up to {@link #LOCAL_BONUS} more where the local
 * difficulty is high (long-settled, moonlit places). A monster that already sees farther (zombies, our own legends)
 * keeps its sight. Illagers and witches keep theirs: {@link Illagers} sets how far a camp's raiders look, and they are
 * the ones that go after a realm's folk.
 */
final class Farsight {
	/** The least a monster sees (blocks; vanilla's 16 for most). */
	static final double SIGHT = 24.0;
	/** Added on Hard. */
	static final double HARD_BONUS = 8.0;
	/** Added at the highest local difficulty, scaled down to nothing at the lowest. */
	static final double LOCAL_BONUS = 4.0;
	private static final Identifier ID = JaymosKingdoms.id("farsight");

	private Farsight() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(Farsight::loaded);
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof Monster mob) || mob instanceof Raider) {
			return;
		}
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range == null) {
			return;
		}
		double sight = SIGHT + (level.getDifficulty() == Difficulty.HARD ? HARD_BONUS : 0.0)
			+ LOCAL_BONUS * level.getCurrentDifficultyAt(mob.blockPosition()).getSpecialMultiplier();
		double extra = sight - range.getBaseValue();
		if (extra > 0.0) {
			range.addOrUpdateTransientModifier(new AttributeModifier(ID, extra, AttributeModifier.Operation.ADD_VALUE));
		} else {
			range.removeModifier(ID);
		}
	}
}
