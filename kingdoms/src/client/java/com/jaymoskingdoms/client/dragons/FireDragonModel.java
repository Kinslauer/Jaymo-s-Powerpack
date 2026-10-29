package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * (0.7.321) Notus and Boreas: nyxaris' Fire Dragon (Blockbench Workshop, CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt), Notus in its own hide and Boreas in an icy one ({@code tools/gen_fire_dragons.py}).
 * The model comes posed for flight with one animation, its wingbeat; the rest is posed here from the render state.
 * <p>
 * In the air it beats its wings in time with the dragon's own stroke (barely, gliding fast), pitched with its climb,
 * jaw wide to breathe. On the ground it levels out on all fours, its wings folded back over its spine, and walks with
 * its legs; landing it flares its wings; crashed it lies low with them spread; sitting it settles back on its haunches.
 */
final class FireDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** On the ground: the body levelled, the neck raised, the head tipped down (degrees). */
	static final float GROUND_BODY = 17.0F, GROUND_NECK = -22.0F, GROUND_HEAD = 15.0F;
	/** Folded wings: the arm swung back and down (degrees, added to its spread), the outer wing folded back on it. */
	static final float FOLD_PITCH = 27.5F, FOLD_SWEEP = 70.0F, FOLD_DROOP = -30.0F, FOLD_TIP = 100.0F;
	/** Standing, the forelegs reach down and back (degrees). */
	static final float FORELEG = -20.0F;
	/** The walk: how far each leg swings (degrees), and one stride in walk distance. */
	static final float STRIDE_SWING = 28.0F, STRIDE = Mth.TWO_PI / 0.6662F;
	/** How far the model is raised so it stands on its feet, and pushed forward over its middle (pixels). */
	static final float LIFT = 19.0F, FORWARD = 10.0F;
	/** How low it lies crashed and sitting (pixels). */
	static final float CRASH_DROP = 12.0F, SIT_DROP = 7.0F;
	/** The wingbeat's moment with the wings held level (seconds), for gliding. */
	static final float GLIDE_AT = 0.3F;

	private final BbModel bb;
	private final ModelPart top, body, neck, head, jaw, wingL, wingR, tipL, tipR, hipL, hipR, armL, armR;
	private final ModelPart[] hindLeg, foreLeg, tail;

	FireDragonModel() {
		this(BbModel.load(JaymosKingdoms.MOD_ID, "fire_dragon"));
	}

	private FireDragonModel(BbModel bb) {
		super(bb.root);
		this.bb = bb;
		this.top = bb.part("root");
		this.body = bb.part("body");
		this.neck = bb.part("cuello1");
		this.head = bb.part("HEAD");
		this.jaw = bb.part("mandibula");
		this.wingL = bb.part("leftAla");
		this.wingR = bb.part("righAla");
		this.tipL = bb.part("ala1");
		this.tipR = bb.part("ala3");
		this.hipL = bb.part("leftPata");
		this.hipR = bb.part("rightPata");
		this.armL = bb.part("mano_r");
		this.armR = bb.part("mano_r2");
		this.hindLeg = parts(bb, "leftPata", "pata1", "pata2", "pata3", "dedos", "rightPata", "pata4", "pata5", "pata6", "dedos4");
		this.foreLeg = parts(bb, "mano_r", "mano1", "mano2", "dedos3", "dedoese", "mano_r2", "mano3", "mano4", "dedos2", "dedoese2");
		this.tail = parts(bb, "cola0", "cola1", "cola2", "cola3", "cola4");
	}

	private static ModelPart[] parts(BbModel bb, String... names) {
		ModelPart[] out = new ModelPart[names.length];
		for (int i = 0; i < names.length; i++) {
			out[i] = bb.part(names[i]);
		}
		return out;
	}

	/** Sets {@code part}'s pitch (degrees, absolute) on the ground, its flying pose kept in the air. */
	private static void ground(ModelPart part, float xDeg, float yDeg, float zDeg, float onGround) {
		part.xRot = Mth.lerp(onGround, part.xRot, xDeg * Mth.DEG_TO_RAD);
		part.yRot = Mth.lerp(onGround, part.yRot, yDeg * Mth.DEG_TO_RAD);
		part.zRot = Mth.lerp(onGround, part.zRot, zDeg * Mth.DEG_TO_RAD);
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float fly = s.fly, crash = s.crash * (1.0F - fly), sit = s.sit * (1.0F - crash) * (1.0F - fly);
		float land = s.land * (1.0F - fly), glide = s.glide * fly, breath = s.breath;
		float onGround = 1.0F - fly;
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * onGround * (1.0F - crash);
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -40.0F, 60.0F) * Mth.DEG_TO_RAD;

		// standing it's lifted onto its feet; it lies lower crashed or sitting
		this.top.y -= LIFT * onGround - crash * CRASH_DROP - sit * SIT_DROP;
		this.top.z -= FORWARD;

		// on the ground: level, the neck up, the legs under it, the wings folded back over its spine (flared to land)
		ground(this.body, GROUND_BODY - sit * 18.0F, 0.0F, 0.0F, onGround);
		ground(this.neck, GROUND_NECK + crash * 30.0F, 0.0F, 0.0F, onGround);
		ground(this.head, GROUND_HEAD, 0.0F, 0.0F, onGround);
		for (ModelPart leg : this.hindLeg) {
			ground(leg, 0.0F, 0.0F, 0.0F, onGround);
		}
		for (ModelPart leg : this.foreLeg) {
			ground(leg, 0.0F, 0.0F, 0.0F, onGround);
		}
		float fold = onGround * (1.0F - land) * (1.0F - crash * 0.6F);
		this.wingL.xRot += FOLD_PITCH * Mth.DEG_TO_RAD * fold;
		this.wingL.yRot += FOLD_SWEEP * Mth.DEG_TO_RAD * fold;
		this.wingL.zRot += FOLD_DROOP * Mth.DEG_TO_RAD * fold;
		this.wingR.xRot += FOLD_PITCH * Mth.DEG_TO_RAD * fold;
		this.wingR.yRot -= FOLD_SWEEP * Mth.DEG_TO_RAD * fold;
		this.wingR.zRot -= FOLD_DROOP * Mth.DEG_TO_RAD * fold;
		this.tipL.yRot += FOLD_TIP * Mth.DEG_TO_RAD * fold;
		this.tipR.yRot -= FOLD_TIP * Mth.DEG_TO_RAD * fold;
		// crashed, the wings lie spread low on the ground
		this.wingL.zRot -= 0.5F * crash;
		this.wingR.zRot += 0.5F * crash;
		// at rest the folded wings breathe a little
		this.wingL.zRot += Mth.sin(t * 0.1F) * 0.02F * fold;
		this.wingR.zRot -= Mth.sin(t * 0.1F) * 0.02F * fold;

		// walking: hind and fore legs swing in opposite pairs; sitting, the hind legs fold forward under it
		float step = s.walkAnimationPos / STRIDE * Mth.TWO_PI;
		float swing = Mth.sin(step) * STRIDE_SWING * Mth.DEG_TO_RAD * walk;
		this.hipL.xRot += swing + sit * 0.9F;
		this.hipR.xRot += -swing + sit * 0.9F;
		this.armL.xRot += FORELEG * Mth.DEG_TO_RAD * onGround - swing;
		this.armR.xRot += FORELEG * Mth.DEG_TO_RAD * onGround + swing;
		this.body.zRot += Mth.sin(step * 0.5F) * 0.03F * walk;

		// in the air: its wingbeat, in time with its stroke (a glide holds the wings level), pitched with its climb
		if (fly > 0.0F) {
			float length = this.bb.length("fly");
			float phase = (s.flap / Mth.TWO_PI % 1.0F + 1.0F) % 1.0F;
			this.bb.play("fly", phase * length, fly * (1.0F - glide));
			this.bb.play("fly", GLIDE_AT, glide);
			this.body.xRot += s.pitch * fly;
		}

		// the tail sways, more as it walks; it streams out straight in flight
		for (int i = 0; i < this.tail.length; i++) {
			this.tail[i].yRot += Mth.sin(t * 0.07F - i * 0.6F) * Mth.lerp(fly, 0.05F + walk * 0.08F, 0.03F);
			this.tail[i].xRot *= 1.0F - glide * 0.5F;
		}

		// the head looks about; the jaw drops to breathe
		this.neck.yRot += headYaw * 0.4F;
		this.head.yRot += headYaw * 0.4F;
		this.head.xRot += headPitch * 0.5F * onGround;
		this.jaw.xRot += breath * 0.7F + Mth.sin(t * 0.6F) * 0.05F * breath;
	}
}
