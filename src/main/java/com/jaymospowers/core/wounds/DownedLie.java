package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Carry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Which way someone downed lies, worked out the same on both sides: the client draws it ({@code DownedPose}), and the
 * server hands it to the body they leave when they die there. Andy (2026-11-12): "downed enemies should react to the
 * executions, i just watched jung wild slam a few zombies and they popped up after, instead of being beaten into the
 * ground". A downed mob still stands as far as the game knows (only its drawing is laid down), so Jaymo's Kitchen began
 * its corpse upright and let it topple: the body sprang to its feet on the killing blow. Now whoever dies downed is
 * marked ({@link #markBody}) and the corpse starts lying where and how they lay.
 */
public final class DownedLie {
	/** Of crawling people, the share that shuffle along on their backs (the rest crawl face down); of the lying, the share curled on their side. */
	public static final float SHUFFLE_SHARE = 0.3F, CURL_SHARE = 0.5F;
	/** Jaymo's Kitchen's {@code Corpses.LYING_TAG}: then {@code u} or {@code d} (face up or down), a dot, and the way the head points (whole degrees). */
	static final String LYING_TAG = "jaymoskitchen.lying.";

	private DownedLie() {
	}

	/** A fixed value (0-1) for this body, the same every time it goes down. */
	public static float pick(int id) {
		int h = id * 0x9E3779B1;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65536.0F;
	}

	/** A person curled on their side: which side they rolled to (1 or -1). */
	public static float curlSide(int id) {
		return pick(id * 31) < 0.5F ? 1.0F : -1.0F;
	}

	/**
	 * Marks a downed mob about to die so its corpse starts as it lies: a crawler face down with its head ahead, one on its
	 * back with its head behind where it stood, one curled with its head out to the side it rolled to; anything not built
	 * like a person along the way it faced. One tripped by a bolas and finished there lies face down. Not players (their own crawl is a lying pose already), nor anyone carried.
	 */
	static void markBody(LivingEntity entity) {
		if (entity instanceof Player || Carry.isCarried(entity)) {
			return;
		}
		boolean tripped = !Downed.isDowned(entity) && Tripped.tripped(entity);
		if (!Downed.isDowned(entity) && !tripped) {
			return;
		}
		float yaw = entity.yBodyRot;
		boolean faceUp = true;
		if (tripped) {
			// a bolas round its shins: it went down on its face
			faceUp = false;
		} else if (BodyPlan.of(entity) == BodyPlan.HUMANOID) {
			int id = entity.getId();
			if (Malfunction.is(entity)) {
				// on its back, or sat slumped (it goes over backward)
				yaw += 180.0F;
			} else if (Downed.crawls(entity)) {
				if (pick(id) < SHUFFLE_SHARE) {
					yaw += 180.0F;
				} else {
					faceUp = false;
				}
			} else if (pick(id) < CURL_SHARE) {
				yaw -= curlSide(id) * 90.0F;
			} else {
				yaw += 180.0F;
			}
		}
		clearBody(entity);
		entity.addTag(LYING_TAG + (faceUp ? "u." : "d.") + Math.round(net.minecraft.util.Mth.wrapDegrees(yaw)));
	}

	/** Takes the mark off again (the death was turned away, so it may yet die on its feet). */
	static void clearBody(LivingEntity entity) {
		for (String tag : java.util.List.copyOf(entity.entityTags())) {
			if (tag.startsWith(LYING_TAG)) {
				entity.removeTag(tag);
			}
		}
	}
}
