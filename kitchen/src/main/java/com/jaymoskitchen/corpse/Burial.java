package com.jaymoskitchen.corpse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Laying a body in the ground and taking it out again (Jaymo's Kingdoms' graves reach this by name). {@link #lower} saves the
 * whole corpse (its owner, hotbar, look, wounds and mark) and takes it out of the world; {@link #raise} puts the same body back
 * where it is dug up.
 */
public final class Burial {
	private Burial() {
	}

	/** {@code corpse}'s whole self as data, and it is gone from the world; null if it could not be saved. */
	public static @Nullable CompoundTag lower(CorpseEntity corpse) {
		if (!(corpse.level() instanceof ServerLevel level) || corpse.isRemoved()) {
			return null;
		}
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!corpse.save(output)) {
			return null;
		}
		CompoundTag tag = output.buildResult();
		corpse.discard();
		return tag;
	}

	/** The body {@code data} was lowered from, lying at {@code x}, {@code y}, {@code z} again. */
	public static CorpseEntity raise(ServerLevel level, CompoundTag data, double x, double y, double z) {
		CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, level);
		corpse.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
		corpse.setPos(x, y, z);
		level.addFreshEntity(corpse);
		return corpse;
	}
}
