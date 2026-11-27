package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Old-tongued knights (Andy, 2026-11-27: "a companion you can encounter who strictly speaks like this", after the
 * Spyder-Knight of the cartoon Ultimate Spider-Man; no name from the show is used). Now and then ({@link #CHANCE}) a
 * fallen knight who turns up on a road for hire ({@link Drifters}, {@link Veterans#KNIGHT}) still speaks as the old
 * courts spoke: every word he says, whoever wrote it, comes out all thee and thou ({@link Dialects#olden},
 * {@link OldenTalk}), and he goes by Sir (or Dame) and his name. Hired, he is a companion like any other, and he alone
 * says a few of the knightly quips ({@link Quips#GATED}) to whoever pays him, besides some of his own.
 * <p>
 * {@code /kingdom oldknight} (game masters, {@link Drifters}) brings one to where you stand, for trying him out. No MCA
 * class is named here, so {@link #is} is safe to ask without MCA.
 */
public final class OldKnights {
	/** The share of a road's fallen knights who are old-tongued. */
	static final float CHANCE = 0.35F;

	/** On an old-tongued knight. Saved. */
	public static final AttachmentType<Boolean> OLD = AttachmentRegistry.create(JaymosKingdoms.id("old_tongued"),
		builder -> builder.persistent(Codec.BOOL));

	private OldKnights() {
	}

	/** Loads the class, so {@link #OLD} is made before any world is read. */
	static void register() {
	}

	/** {@code e} is an old-tongued knight. */
	public static boolean is(@Nullable Entity e) {
		return e != null && e.hasAttached(OLD);
	}

	/** Makes {@code v} (a fallen knight) old-tongued, and gives him his Sir (or her Dame). */
	static void make(LivingEntity v, boolean female) {
		v.setAttached(OLD, Boolean.TRUE);
		String name = v.getName().getString();
		if (!name.startsWith("Sir ") && !name.startsWith("Dame ")) {
			McaCompat.rename(v, (female ? "Dame " : "Sir ") + name);
		}
	}
}
