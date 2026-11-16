package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.client.bb.BbModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The grave ghost's wispy tail (Andy, 2026-11-16: "the ghosts should have wispy tails like wisp"), in place of its legs:
 * one tapering tail of {@link #LINKS} links ({@code tools/gen_ghost_tail.py}) hanging from the waist, curled back a little
 * at every link as Wisp's is (Powers' WispTail), the curl slowly tightening and letting out while a waver runs down it to
 * the tip. The ghost stands over its grave and never moves, so the tail only hangs.
 */
final class GraveGhostTail implements BbMobRenderer.Extra {
	/** How many links (bones {@code Tail 0} to the tip). */
	static final int LINKS = 8;
	/** How far each link bends back from the one above (radians), and how much that breathes, and how fast. */
	static final float CURL = 0.15F, BREATHE = 0.05F, BREATHE_RATE = 0.045F;
	/** The waver: side to side at the tip and a little back and forth (radians), its rate, and how far it is out of step link to link. */
	static final float WAVE = 0.2F, WAVE_RATE = 0.16F, WAVE_STEP = 0.8F;

	private ModelPart[] links;

	@Override
	public void pose(BbModel bb, BbMobRenderer.State state) {
		if (this.links == null) {
			ModelPart[] found = new ModelPart[LINKS];
			for (int i = 0; i < LINKS; i++) {
				found[i] = bb.find("Tail " + i);
				if (found[i] == null) {
					return;
				}
			}
			this.links = found;
		}
		float age = state.ageInTicks;
		float curl = CURL + BREATHE * Mth.sin(age * BREATHE_RATE);
		for (int i = 0; i < LINKS; i++) {
			float along = i / (float)(LINKS - 1);
			float sway = WAVE * (0.3F + 0.7F * along) / (LINKS * 0.25F);
			this.links[i].xRot = curl + Mth.cos(age * WAVE_RATE * 0.7F - i * WAVE_STEP) * sway * 0.5F;
			this.links[i].zRot = Mth.sin(age * WAVE_RATE - i * WAVE_STEP) * sway;
		}
	}
}
