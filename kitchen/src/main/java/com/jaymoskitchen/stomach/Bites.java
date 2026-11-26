package com.jaymoskitchen.stomach;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * Finer hunger (Granular Hunger's idea, CC0, by tetro48; our own code): the hunger bar goes down a third of a point at a
 * time instead of a whole point ({@link #PER_POINT} bites to a point, 60 to the bar), at the same pace as before over
 * all: a bite for every {@link #EXHAUSTION} of exhaustion, a third of a point of saturation first while there is any.
 * The bar itself still counts whole points (so AppleSkin, Thirst, sprinting and the stomach read it as ever); the bites
 * gone from the next point are kept beside it ({@code mixin/GranularFoodDataMixin}) and drawn as a shank part eaten
 * away ({@code client/stomach/BitesHud}). A bitten point means you can eat again, and food eaten onto a nearly full bar
 * fills the bitten point first, so nothing is wasted. Health still comes only from the stomach ({@link Stomach}).
 */
public final class Bites {
	/** Bites to a point of hunger. */
	public static final int PER_POINT = 3;
	/** Exhaustion a bite takes (vanilla takes 4 for a whole point), and saturation it uses up while there is any. */
	public static final float EXHAUSTION = 4.0F / PER_POINT, SATURATION = 1.0F / PER_POINT;
	/** The full bar, in points. */
	public static final int FULL = 20;

	/** Mixed into FoodData: the bites gone from the next point (0 to 2). */
	public interface Holder {
		int jaymoskitchen$bites();

		void jaymoskitchen$setBites(int bites);
	}

	/** On a player: the bites gone, for their own hunger bar (synced to them only; the server keeps it on the FoodData). */
	public static final AttachmentType<Integer> BITTEN = AttachmentRegistry.create(JaymosKitchen.id("hunger_bites"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private Bites() {
	}

	/** Loads the attachment (from {@code JaymosKitchen}). */
	public static void register() {
	}

	/** The bites gone from {@code player}'s next point (0 to 2); client (the synced copy) and server. */
	public static int of(Player player) {
		return player.getAttachedOrElse(BITTEN, 0);
	}

	/** Server: tells the player's client when the bites changed. */
	public static void sync(Player player, int bites) {
		if (of(player) != bites) {
			if (bites == 0) {
				player.removeAttached(BITTEN);
			} else {
				player.setAttached(BITTEN, bites);
			}
		}
	}
}
