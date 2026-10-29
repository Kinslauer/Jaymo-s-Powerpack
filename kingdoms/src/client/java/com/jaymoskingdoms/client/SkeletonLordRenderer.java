package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.SkeletonLord;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * (0.7.351) The {@link SkeletonLord}, drawn as the Blockbench Workshop's "Skeleton Lord" (CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt) with its own animations: asleep, its statue pose under amethyst; waking, its laugh
 * (its jaw working); awake, the crystals gone, idle, walking and running by his pace, and his knockback spell.
 */
public final class SkeletonLordRenderer extends MobRenderer<SkeletonLord, SkeletonLordRenderer.State, SkeletonLordRenderer.Model> {
	/** The model stands 5 blocks to its crown (8 to the staff's tip): drawn to his 2.9-block height. */
	static final float SCALE = 0.55F;
	/** The model's crystal bones, shown only while he sleeps. */
	static final String[] CRYSTALS = {"head_crystals", "crystal_brow", "crystal_hand", "crystal_fingerbase", "crystal_fingertips", "crystal_thumbbase",
		"crystal_thumbtip", "crystal_arm", "crystal_chest", "crystal_waist", "crystal_leg", "crystalized_pauldron", "R_crystalized_pauldron"};

	public static final class State extends LivingEntityRenderState {
		boolean dormant;
		float sinceWoke;
		float sinceCast;
	}

	public SkeletonLordRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(BbModel.load(JaymosKingdoms.MOD_ID, "skeleton_lord")), 0.9F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SkeletonLord lord, State state, float partialTicks) {
		super.extractRenderState(lord, state, partialTicks);
		state.dormant = lord.dormant();
		state.sinceWoke = lord.sinceWoke() + partialTicks;
		state.sinceCast = lord.sinceCast() + partialTicks;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.model.bb.texture;
	}

	static final class Model extends EntityModel<State> {
		/** A stride of the walk (blocks walked, as the walk animation counts them) for one run of its animation. */
		static final float STRIDE = Mth.TWO_PI / 0.6662F;
		/** The spell's animation runs this long (ticks). */
		static final float CAST_TICKS = 19.0F;

		final BbModel bb;
		private final List<ModelPart> crystals = new ArrayList<>();

		Model(BbModel bb) {
			super(bb.root);
			this.bb = bb;
			for (String name : CRYSTALS) {
				ModelPart part = bb.find(name);
				if (part != null) {
					this.crystals.add(part);
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			for (ModelPart crystal : this.crystals) {
				crystal.visible = state.dormant;
			}
			if (state.dormant) {
				this.bb.play("statue", 0.0F, 1.0F);
				return;
			}
			if (state.sinceWoke < SkeletonLord.WAKE_TICKS) {
				// the laugh: its pose eased in and out, the jaw working
				float t = state.sinceWoke / SkeletonLord.WAKE_TICKS;
				float weight = Mth.clamp(Math.min(t * 5.0F, (1.0F - t) * 5.0F), 0.0F, 1.0F);
				this.bb.play("idle", state.ageInTicks / 20.0F, 1.0F - weight);
				this.bb.play("NYEHEHEHE", 0.0F, weight);
				ModelPart jaw = this.bb.find("jaw");
				if (jaw != null) {
					jaw.xRot += Math.abs(Mth.sin(state.sinceWoke * 0.9F)) * 0.35F * weight;
				}
				return;
			}
			float pace = Mth.clamp(state.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
			float run = Mth.clamp((state.walkAnimationSpeed - 0.5F) * 2.0F, 0.0F, 1.0F);
			this.bb.play("idle", state.ageInTicks / 20.0F, 1.0F - pace);
			this.bb.play("walk", state.walkAnimationPos / STRIDE * this.bb.length("walk"), pace * (1.0F - run));
			this.bb.play("run", state.walkAnimationPos / STRIDE * this.bb.length("run"), pace * run);
			if (state.sinceCast < CAST_TICKS) {
				this.bb.play("spell_knockback", state.sinceCast / 20.0F, 1.0F);
			}
			ModelPart head = this.bb.find("head");
			if (head != null) {
				head.yRot += state.yRot * Mth.DEG_TO_RAD;
				head.xRot += state.xRot * Mth.DEG_TO_RAD;
			}
		}
	}
}
