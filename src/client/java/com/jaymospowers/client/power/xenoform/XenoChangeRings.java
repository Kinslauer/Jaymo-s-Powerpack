package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.BandStyle;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;

/**
 * The change, as everyone sees it on every alien (Andy: "the flash should stay as well, and this should be the transition
 * for all xenos"). The moment a player's form changes (into an alien, from one alien to another, or back to themselves) two
 * rings of light in their band's glow open at the waist; one sweeps up over the head and the other down to the feet over
 * {@link #TICKS}, narrowing as they go. The band's flash ({@link BandFlashHud}) still goes off as before; the rings are what
 * is left to see as it fades.
 * <p>
 * Each client notices the change itself from the synced {@link XenoformState#FORM}, so nothing is sent for it.
 */
public final class XenoChangeRings {
	// ---- tuning ----
	/** How long the two rings take to run from the waist to the head and the feet. */
	static final int TICKS = 12;
	/** Motes round each ring a tick, and each mote's size. */
	static final int MOTES = 14;
	static final float MOTE_SIZE = 0.9F;
	/** A ring's radius as a share of the body's width: at the waist, and where it ends. */
	static final float RADIUS_WAIST = 1.1F, RADIUS_END = 0.6F;
	/** How far past the head and under the feet the rings run (blocks). */
	static final double OVERRUN = 0.15;

	private static final class Seen {
		String form;
		int left;
		float spin;
	}

	private static final Map<Integer, Seen> SEEN = new HashMap<>();

	private XenoChangeRings() {
	}

	/** Every client tick: notices changes and runs the rings on for everyone in view. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			SEEN.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			String form = player.getAttached(XenoformState.FORM);
			Seen seen = SEEN.get(player.getId());
			if (seen == null) {
				// first sight of them: whatever they are already is no change
				seen = new Seen();
				seen.form = form;
				SEEN.put(player.getId(), seen);
				continue;
			}
			if (!Objects.equals(seen.form, form)) {
				seen.form = form;
				seen.left = TICKS;
				seen.spin = player.getRandom().nextFloat() * Mth.TWO_PI;
			}
			if (seen.left > 0) {
				if (!player.isInvisible()) {
					rings(level, player, 1.0F - seen.left / (float)TICKS, seen.spin);
				}
				seen.left--;
			}
		}
		for (Iterator<Integer> it = SEEN.keySet().iterator(); it.hasNext();) {
			if (!(level.getEntity(it.next()) instanceof AbstractClientPlayer)) {
				it.remove();
			}
		}
	}

	/** One tick's two rings, {@code t} of the way (0 to 1) from the waist to the ends. */
	private static void rings(ClientLevel level, AbstractClientPlayer player, float t, float spin) {
		ParticleOptions mote = MagicFx.mote(BandStyle.glow(player), MOTE_SIZE);
		double height = player.getBbHeight(), waist = player.getY() + height * 0.5;
		double radius = player.getBbWidth() * Mth.lerp(t, RADIUS_WAIST, RADIUS_END);
		double reach = (height * 0.5 + OVERRUN) * t;
		for (int side = -1; side <= 1; side += 2) {
			double y = waist + reach * side;
			for (int i = 0; i < MOTES; i++) {
				// the two rings turn opposite ways as they travel
				float angle = spin + (i / (float)MOTES + t * 0.5F * side) * Mth.TWO_PI;
				double dx = Mth.cos(angle), dz = Mth.sin(angle);
				level.addParticle(mote, player.getX() + dx * radius, y, player.getZ() + dz * radius, dx * 0.01, side * 0.02, dz * 0.01);
			}
		}
	}
}
