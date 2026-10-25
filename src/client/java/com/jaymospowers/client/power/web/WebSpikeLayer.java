package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Spike Burst: long, rigid black spikes shot out of every part of the body at once (head, chest, back, both arms and
 * both legs), each straight out from where it roots, a sea urchin of them, held a moment and then sucked back in. The
 * roar throws the same burst out as it lands. Each body part's spikes ride that part, so they follow the braced pose.
 * Everyone sees them; your own head's aren't drawn from your own eyes.
 * <p>
 * The white strain's Recoil throws out the same burst as short, thick, rounded white nubs ({@link #NUB_LENGTH},
 * {@link #NUB_THICK}); the red strain's are red ({@link WebStrainLook}).
 */
final class WebSpikeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_tendril.png");
	/** Spikes rooted on each body part: head, body, right arm, left arm, right leg, left leg. */
	private static final int[] COUNTS = {6, 14, 5, 5, 4, 4};
	/** Each part's box (model pixels, against its pivot), as the player model has it: min x, y, z, max x, y, z. */
	private static final float[][] BOXES = {
		{-4.0F, -8.0F, -4.0F, 4.0F, 0.0F, 4.0F},
		{-4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 2.0F},
		{-3.0F, -2.0F, -2.0F, 1.0F, 10.0F, 2.0F},
		{-1.0F, -2.0F, -2.0F, 3.0F, 10.0F, 2.0F},
		{-2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F},
		{-2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F},
	};
	/** Where the body's middle sits in each part's own space: spikes lean away from it, so the whole burst is radial. */
	private static final float[][] MIDDLES = {
		{0.0F, 10.0F, 0.0F}, {0.0F, 7.0F, 0.0F}, {5.0F, 5.0F, 0.0F}, {-5.0F, 5.0F, 0.0F}, {1.9F, -5.0F, 0.0F}, {-1.9F, -5.0F, 0.0F},
	};
	/** A spike: tapering sections (thickness, model pixels), each this long; its length is stretched to {@link #REACH}. */
	private static final float[] TAPER = {5.0F, 4.2F, 3.2F, 2.2F, 1.0F};
	private static final float SECTION = 4.0F;
	/** How far a spike reaches at full length (blocks), give or take {@link #REACH_SPREAD}; roughly the burst's radius. */
	private static final float REACH = (float)WebSymbiote.SPIKE_RADIUS, REACH_SPREAD = 0.3F;
	/** How much each spike's direction strays from straight out (the burst is ragged, not a neat ball). */
	private static final float SCATTER = 0.45F;
	/** Shooting out (ticks; each spike a little after the last, up to {@link #STAGGER}), held, and sucked back in from {@link #RETRACT_AT}. */
	private static final float SHOOT_TICKS = 2.0F, STAGGER = 1.5F, RETRACT_AT = 8.0F;
	/** Held out, they thrum this much (thickness). */
	private static final float THRUM = 0.08F;
	/** Recoil's nubs (the white strain): this share of a spike's length, this many times as thick. */
	private static final float NUB_LENGTH = 0.16F, NUB_THICK = 1.7F;

	private final SpikeModel[] models = new SpikeModel[COUNTS.length];

	WebSpikeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		for (int part = 0; part < COUNTS.length; part++) {
			this.models[part] = new SpikeModel(createLayer(part).bakeRoot(), COUNTS[part], part);
		}
	}

	/**
	 * Ticks into the spikes being out, from the pose: a Spike Burst from its start, the roar's from just before it
	 * lands (so they're full length as it roars); null once they're all back in.
	 */
	static @Nullable Float age(@Nullable WebPose pose, float time) {
		if (pose == null) {
			return null;
		}
		float age = switch (pose.kind()) {
			// (they burst once it has gathered itself)
			case WebPose.SPIKES -> time - pose.start() - WebSymbiote.SPIKE_GATHER_TICKS;
			case WebPose.ROAR -> time - pose.start() - WebSymbiote.ROAR_BLAST_AT + SHOOT_TICKS;
			default -> -1.0F;
		};
		return age >= 0.0F && age < WebSymbiote.SPIKE_TICKS ? age : null;
	}

	/** Each part's spikes: rooted at random spots on its surface, pointing out from the body's middle (scattered). */
	private static LayerDefinition createLayer(int part) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(9173L + part * 131L);
		float[] box = BOXES[part];
		float[] middle = MIDDLES[part];
		for (int i = 0; i < COUNTS[part]; i++) {
			// a spot on the box's surface: pick a face, then a point on it
			float x = Mth.lerp(random.nextFloat(), box[0], box[3]);
			float y = Mth.lerp(random.nextFloat(), box[1], box[4]);
			float z = Mth.lerp(random.nextFloat(), box[2], box[5]);
			int face = random.nextInt(6);
			if (part == 1 && face >= 4) {
				// the torso's spikes mostly come out of the chest and back
				face = random.nextInt(4);
			}
			switch (face) {
				case 0 -> z = box[2];
				case 1 -> z = box[5];
				case 2 -> x = box[0];
				case 3 -> x = box[3];
				case 4 -> y = box[1];
				default -> y = box[4];
			}
			float dx = x - middle[0] + (random.nextFloat() - 0.5F) * 2.0F * SCATTER * 8.0F;
			float dy = y - middle[1] + (random.nextFloat() - 0.5F) * 2.0F * SCATTER * 8.0F;
			float dz = z - middle[2] + (random.nextFloat() - 0.5F) * 2.0F * SCATTER * 8.0F;
			// a little out of the face it roots on, so none lies flat along the body
			float out = 3.0F;
			switch (face) {
				case 0 -> dz -= out;
				case 1 -> dz += out;
				case 2 -> dx -= out;
				case 3 -> dx += out;
				case 4 -> dy -= out;
				default -> dy += out;
			}
			float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			// a box along +Z, turned by xRot then yRot, ends up along (cos x sin y, -sin x, cos x cos y)
			float pitch = (float)Math.asin(Mth.clamp(-dy / len, -1.0F, 1.0F));
			float yaw = (float)Mth.atan2(dx, dz);
			PartDefinition parent = root.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(0, 0)
				.addBox(-TAPER[0] / 2.0F, -TAPER[0] / 2.0F, 0.0F, TAPER[0], TAPER[0], SECTION), PartPose.offsetAndRotation(x, y, z, pitch, yaw, 0.0F));
			for (int k = 1; k < TAPER.length; k++) {
				parent = parent.addOrReplaceChild("s" + k, CubeListBuilder.create().texOffs(0, (k % 2) * 8)
					.addBox(-TAPER[k] / 2.0F, -TAPER[k] / 2.0F, 0.0F, TAPER[k], TAPER[k], SECTION), PartPose.offset(0.0F, 0.0F, SECTION - 0.2F));
			}
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.getData(WebSymbioteClient.SPIKES) == null || state.isInvisible) {
			return;
		}
		PlayerModel parent = this.getParentModel();
		WebFeral.applyModel(parent, state);
		WebClimbPose.applyModel(parent, state);
		WebHeldGrabPose.applyModel(parent, state);
		WebExecutionPose.applyModel(parent, state);
		WebSuitGrowth.shift(parent, state);
		ModelPart[] parts = {parent.head, parent.body, parent.rightArm, parent.leftArm, parent.rightLeg, parent.leftLeg};
		boolean ownView = WebSymbioteClient.ownFirstPerson(state);
		for (int part = 0; part < parts.length; part++) {
			if (part == 0 && ownView || !parts[part].visible) {
				continue;
			}
			poseStack.pushPose();
			parts[part].translateAndRotate(poseStack);
			collector.submitModel(this.models[part], state, poseStack, RenderTypes.entityCutout(WebStrainLook.texture(TEXTURE, state)), lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static final class SpikeModel extends Model<AvatarRenderState> {
		private final ModelPart[] spikes;
		/** Each spike's share of the full reach, and how late it fires (ticks). */
		private final float[] reach, delay;

		SpikeModel(ModelPart root, int count, int part) {
			super(root, RenderTypes::entityCutout);
			this.spikes = new ModelPart[count];
			this.reach = new float[count];
			this.delay = new float[count];
			net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(5501L + part * 37L);
			for (int i = 0; i < count; i++) {
				this.spikes[i] = root.getChild("spike" + i);
				this.reach[i] = 1.0F + (random.nextFloat() - 0.5F) * 2.0F * REACH_SPREAD;
				this.delay[i] = random.nextFloat() * STAGGER;
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Float at = state.getData(WebSymbioteClient.SPIKES);
			if (at == null) {
				return;
			}
			// the whole spike, at length 1, is this long (blocks): stretched along itself to reach
			float chain = ((TAPER.length - 1) * (SECTION - 0.2F) + SECTION) / 16.0F;
			// (the white strain's Recoil: short, thick nubs instead)
			boolean nubs = WebStrainLook.of(state) == com.jaymospowers.power.web.WebSymbioteStrain.WHITE;
			float length = nubs ? NUB_LENGTH : 1.0F, girth = nubs ? NUB_THICK : 1.0F;
			for (int i = 0; i < this.spikes.length; i++) {
				float age = at - this.delay[i];
				// out like a shot, held (thrumming), then drawn back in smoothly
				float out = age <= 0.0F ? 0.0F : age < SHOOT_TICKS ? 1.0F - (1.0F - age / SHOOT_TICKS) * (1.0F - age / SHOOT_TICKS)
					: age < RETRACT_AT ? 1.0F : 1.0F - smooth((age - RETRACT_AT) / (WebSymbiote.SPIKE_TICKS - STAGGER - RETRACT_AT));
				ModelPart spike = this.spikes[i];
				spike.visible = out > 0.01F;
				// a touch of overshoot as it lands full length
				float kick = age > SHOOT_TICKS && age < SHOOT_TICKS + 2.0F ? 0.06F * Mth.sin((age - SHOOT_TICKS) / 2.0F * Mth.PI) : 0.0F;
				spike.zScale = Math.max(0.02F, out * (1.0F + kick) * REACH * this.reach[i] / chain * length);
				float thick = (0.6F + 0.4F * out) * (1.0F + THRUM * Mth.sin(state.ageInTicks * 2.7F + i * 1.3F) * (age < RETRACT_AT ? 1.0F : 0.0F));
				spike.xScale = thick * girth;
				spike.yScale = thick * girth;
			}
		}
	}
}
