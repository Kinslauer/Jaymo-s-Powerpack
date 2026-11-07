package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.jaymoskingdoms.dragons.IgnivorusDragon;
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
 * <p>
 * (0.7.612, docs/SaintsDragons-Animation-Spec.md) More of the wyvern's own set: a blow plays its flinch, dying plays its
 * death (the renderer's sideways flip is off for it), sitting a while it falls asleep, and stood idle it roars now and
 * then. Ignivorus's moves play theirs: the fireball volley its fire (hovering, its hover fire), the magma pillars' stamp
 * its slam, the bite its bite and the tail sweep its sweep, each faded in and out over its move and the idle under it.
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

	/** (0.7.612) Stood idle, it roars once in this many ticks; once this far settled into its sit it curls up asleep. */
	static final float ROAR_EVERY = 520.0F, SLEEP_AFTER = 0.9F;

	/** Whether its moves are Ignivorus's ({@link IgnivorusDragon#action}); Eurus and Aeolus have none. */
	private final boolean ignivorus;

	KintsugiDragonModel() {
		this(false);
	}

	KintsugiDragonModel(boolean ignivorus) {
		this(BbModel.load(JaymosKingdoms.MOD_ID, "kintsugi"), ignivorus);
	}

	private KintsugiDragonModel(BbModel bb, boolean ignivorus) {
		super(bb.root);
		this.bb = bb;
		this.ignivorus = ignivorus;
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

		// (0.7.612) dying: its own death, played through as it falls (and nothing else)
		float dead = Mth.clamp(s.deathTime / 20.0F, 0.0F, 1.0F);
		if (dead > 0.0F) {
			this.bb.play("wyvern_death", dead * this.bb.length("wyvern_death"), 1.0F);
			return;
		}

		// (0.7.612) its moves (Ignivorus): each faded in over its first tenth and out over its last fifth, played through
		// at the pace of its move; the idle gives way under it
		float move = 0.0F;
		if (this.ignivorus && s.action != IgnivorusDragon.NONE) {
			float x = IgnivorusDragon.actionShare(s.action, s.actionAge);
			move = Math.min(Mth.clamp(x / 0.1F, 0.0F, 1.0F), Mth.clamp((1.0F - x) / 0.2F, 0.0F, 1.0F));
			move = move * move * (3.0F - 2.0F * move);
			String anim = switch (s.action) {
				case IgnivorusDragon.VOLLEY_MOVE -> fly > 0.5F ? "wyvern_hover_fire" : "wyvern_fire";
				case IgnivorusDragon.STAMP_MOVE -> "wyvern_slam";
				case IgnivorusDragon.BITE_MOVE -> "wyvern_bite";
				default -> "wyvern_tail_sweep_left";
			};
			this.bb.play(anim, x * this.bb.length(anim), move);
		}
		// a blow: its flinch, quick
		float hurt = s.hurt * (1.0F - move);
		if (hurt > 0.0F) {
			this.bb.play("wyvern_flinch", (1.0F - s.hurt) * this.bb.length("wyvern_flinch"), hurt);
		}
		// settled into its sit it curls up asleep; stood idle it roars now and then
		float sleep = sit * Mth.clamp((s.sit - SLEEP_AFTER) / (1.0F - SLEEP_AFTER), 0.0F, 1.0F);
		float idle = stand * (1.0F - Math.min(s.walkAnimationSpeed * 4.0F, 1.0F)) * (1.0F - move) * (1.0F - breath);
		float roarAt = (t + 160.0F) % ROAR_EVERY;
		float roarLen = this.bb.length("wyvern_roar") * 20.0F;
		float roar = roarAt < roarLen ? idle * Math.min(Mth.clamp(roarAt / 10.0F, 0.0F, 1.0F), Mth.clamp((roarLen - roarAt) / 14.0F, 0.0F, 1.0F)) : 0.0F;

		// on the ground: its idle (settled low on folded legs to sit, asleep after a while), knocked down crashed, hovering to land
		this.loop("wyvern_idle", t, (stand + sit * (1.0F - sleep)) * (1.0F - move) * (1.0F - roar));
		if (roar > 0.0F) {
			this.bb.play("wyvern_roar", roarAt / 20.0F, roar);
		}
		this.loop("wyvern_sleep", t, sleep * (1.0F - move));
		this.top.y += sit * (1.0F - sleep) * SIT_DROP;
		this.legL.xRot -= sit * (1.0F - sleep) * SIT_FOLD * Mth.DEG_TO_RAD;
		this.legR.xRot -= sit * (1.0F - sleep) * SIT_FOLD * Mth.DEG_TO_RAD;
		if (crash > 0.0F) {
			this.bb.play("wyvern_knockdown", CRASH_AT, crash);
		}
		this.loop("wyvern_hover_move", t, land * (1.0F - move));

		// walking: the legs swing over the idle
		float step = s.walkAnimationPos / STRIDE * Mth.TWO_PI;
		float swing = Mth.sin(step) * STRIDE_SWING * Mth.DEG_TO_RAD * walk;
		this.legL.xRot += swing;
		this.legR.xRot -= swing;

		// in the air: its wingbeat in time with its stroke (held open gliding), pitched with its climb
		if (fly > 0.0F) {
			float length = this.bb.length("wyvern_fly_move");
			float phase = (s.flap / Mth.TWO_PI % 1.0F + 1.0F) % 1.0F;
			this.bb.play("wyvern_fly_move", phase * length, fly * (1.0F - glide) * (1.0F - move));
			this.bb.play("wyvern_fly_move", GLIDE_AT, glide * (1.0F - move));
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
