package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * (0.7.329) Eurus and Aeolus: eanncrod's Kintsugi wyvern (Blockbench Workshop, CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt), Aeolus grey-white with his gold seams, Eurus storm-dark with his glowing cyan
 * ({@code tools/gen_kintsugi_dragons.py}). A wyvern: it stands on its hind legs with its wings for forelegs.
 * <p>
 * Its own animations do most of the work: on the ground it breathes in its idle and walks with its legs swung over it;
 * in the air it beats its wings in its flight loop in time with the dragon's stroke (held open, gliding), pitched with
 * its climb; landing it hovers; sitting it settles low on bent legs; crashed it lies knocked down. The jaw drops to breathe.
 */
final class KintsugiDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How far the model is lowered so its feet stand on the ground in its idle (pixels). */
	static final float GROUND_DROP = 25.0F;
	/** The walk: how far each leg swings (degrees), and one stride in walk distance. */
	static final float STRIDE_SWING = 30.0F, STRIDE = Mth.TWO_PI / 0.6662F;
	/** The moment of its wingbeat held for a glide, and of its knockdown held crashed (seconds). */
	static final float GLIDE_AT = 0.0F, CRASH_AT = 2.5F;
	/** Sitting: how low it settles (pixels) and how far its legs fold (degrees). */
	static final float SIT_DROP = 14.0F, SIT_FOLD = 50.0F;

	private final BbModel bb;
	private final ModelPart top, neck, head, jaw, legL, legR, tail;

	KintsugiDragonModel() {
		this(BbModel.load(JaymosKingdoms.MOD_ID, "kintsugi"));
	}

	private KintsugiDragonModel(BbModel bb) {
		super(bb.root);
		this.bb = bb;
		this.top = bb.part("root");
		this.neck = bb.part("neck");
		this.head = bb.part("head");
		this.jaw = bb.part("jaw");
		this.legL = bb.part("leftLeg");
		this.legR = bb.part("rightLeg");
		this.tail = bb.part("tail");
	}

	/** {@code anim} looping by the clock, {@code weight} of it. */
	private void loop(String anim, float ticks, float weight) {
		if (weight > 0.0F) {
			float length = Math.max(this.bb.length(anim), 0.05F);
			this.bb.play(anim, ticks / 20.0F % length, weight);
		}
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float fly = s.fly, crash = s.crash * (1.0F - fly), sit = s.sit * (1.0F - crash) * (1.0F - fly);
		float land = s.land * (1.0F - fly), glide = s.glide * fly, breath = s.breath;
		float onGround = 1.0F - fly;
		float stand = onGround * (1.0F - sit) * (1.0F - crash) * (1.0F - land);
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * stand;
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -40.0F, 60.0F) * Mth.DEG_TO_RAD;

		this.top.y += GROUND_DROP;

		// on the ground: its idle (settled low on folded legs to sit), knocked down crashed, hovering to land
		this.loop("wyvern_idle", t, stand + sit);
		this.top.y += sit * SIT_DROP;
		this.legL.xRot -= sit * SIT_FOLD * Mth.DEG_TO_RAD;
		this.legR.xRot -= sit * SIT_FOLD * Mth.DEG_TO_RAD;
		if (crash > 0.0F) {
			this.bb.play("wyvern_knockdown", CRASH_AT, crash);
		}
		this.loop("wyvern_hover_move", t, land);

		// walking: the legs swing over the idle
		float step = s.walkAnimationPos / STRIDE * Mth.TWO_PI;
		float swing = Mth.sin(step) * STRIDE_SWING * Mth.DEG_TO_RAD * walk;
		this.legL.xRot += swing;
		this.legR.xRot -= swing;

		// in the air: its wingbeat in time with its stroke (held open gliding), pitched with its climb
		if (fly > 0.0F) {
			float length = this.bb.length("wyvern_fly_move");
			float phase = (s.flap / Mth.TWO_PI % 1.0F + 1.0F) % 1.0F;
			this.bb.play("wyvern_fly_move", phase * length, fly * (1.0F - glide));
			this.bb.play("wyvern_fly_move", GLIDE_AT, glide);
			this.top.xRot += s.pitch * fly;
		}

		// the tail sways; the head looks about; the jaw drops to breathe
		this.tail.yRot += Mth.sin(t * 0.07F) * (0.05F + walk * 0.08F);
		this.neck.yRot += headYaw * 0.4F;
		this.head.yRot += headYaw * 0.4F;
		this.head.xRot += headPitch * 0.5F * onGround;
		this.jaw.xRot += breath * 0.7F + Mth.sin(t * 0.6F) * 0.05F * breath;
	}
}
