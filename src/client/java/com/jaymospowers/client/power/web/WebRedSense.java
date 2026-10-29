package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebSymbioteBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * (0.17.150) The red strain's sense, like a sculk sensor's: living things near you show as red outlines through walls, but
 * only as they betray themselves, by the beat of their heart (a lub-dub that flashes bright, slower for the big and
 * quicker for the small) or by moving (a steady dim red; sneaking hides it). Drawn only on your own screen, with the
 * outline the spider-sense uses; nobody else sees anything. Each close beat also presses a little dread in on you (the
 * suit hungers for them), no more often than {@code DREAD_GAP}.
 */
public final class WebRedSense {
	/** How far it reaches (blocks). */
	public static final double RANGE = 20.0;
	/** Things this close make the screen darken at their beat, no more often than this many ticks. */
	private static final double DREAD_RANGE = 7.0;
	private static final int DREAD_GAP = 160, DREAD_TICKS = 24;
	/** A beat: ticks between them for a normal-sized thing, and how long the lub and the dub each flash. */
	private static final int BEAT_BASE = 22, BEAT_FLASH = 3, DUB_AT = 6;
	/** Movement that shows (blocks a tick, sideways). */
	private static final double MOVE_MIN = 0.03;
	/** The colours (ARGB): a beat's flash and a mover's glow. */
	private static final int BEAT_COLOR = 0xFFFF2A2A, MOVE_COLOR = 0xFF7A1212;

	private static long lastDread;

	private WebRedSense() {
	}

	/** The outline colour for {@code entity} on our screen, or 0. */
	public static int outline(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || entity == self || !(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !living.isAlive()
			|| living.isInvisible() || entity.distanceToSqr(self) > RANGE * RANGE || !WebSymbioteBody.senses(self)) {
			return 0;
		}
		// the big beat slowly, the small quickly
		float size = Math.max(0.3F, Math.min(3.0F, living.getBbWidth() * living.getBbHeight()));
		int period = Math.round(BEAT_BASE * (0.6F + 0.4F * size));
		long clock = living.level().getGameTime() + entity.getId() * 7L;
		int phase = (int)(clock % period);
		boolean beat = phase < BEAT_FLASH || phase >= DUB_AT && phase < DUB_AT + BEAT_FLASH - 1;
		if (beat) {
			// (a heart can't be sneaked)
			if (phase == 0 && entity.distanceToSqr(self) < DREAD_RANGE * DREAD_RANGE) {
				dread(self.level().getGameTime());
			}
			return BEAT_COLOR;
		}
		double sideways = living.getDeltaMovement().horizontalDistanceSqr();
		if (sideways > MOVE_MIN * MOVE_MIN && !living.isShiftKeyDown()) {
			return MOVE_COLOR;
		}
		return 0;
	}

	private static void dread(long now) {
		if (now - lastDread < DREAD_GAP && now >= lastDread) {
			return;
		}
		lastDread = now;
		WebSymbioteDread.onPacket(new WebNet.Dread(DREAD_TICKS, false, false, 0.0F));
	}

	static void reset() {
		lastDread = 0L;
	}
}
