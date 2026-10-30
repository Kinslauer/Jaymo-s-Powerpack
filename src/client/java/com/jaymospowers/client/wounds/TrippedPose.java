package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.wounds.Tripped;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.71) A body tripped by a bolas ({@link Tripped}), drawn: the cord whips round its shins and cinches, the legs snap
 * together, it teeters with its arms flung out, then pitches forward onto its face, arms thrown out to catch itself, with
 * a little bounce as it lands. Down, it kicks its bound legs together and paws at the ground, head up. When the trip's
 * over it pushes itself back up and the cord drops off (Kingdoms drops the bolas there). The cord and its two stone
 * weights are drawn on the legs ({@link Layer}). Mobs only (no player is ever tripped); illagers, whose model isn't a
 * HumanoidModel, are posed through {@link PoseBlend#parts}.
 */
public final class TrippedPose {
	/** How far it teeters forward (share of the fall) as the cord cinches, and how hard it sways (degrees). */
	static final float TEETER = 0.12F, TEETER_SWAY = 4.0F;
	/** The bounce as it lands: how far past flat (share of the fall), how fast it rings and dies (per tick). */
	static final float BOUNCE = 0.06F, BOUNCE_RING = 1.2F, BOUNCE_DECAY = 0.35F;
	/** How far the body is raised off the ground face down (blocks), so it doesn't sink in. */
	static final float FACE_DOWN_LIFT = 0.3F;
	/** Kicking the bound legs: every this many ticks (give or take), how long a fit lasts, how fast and how high (radians). */
	static final float KICK_EVERY = 34.0F, KICK_TICKS = 18.0F, KICK_RATE = 0.9F, KICK_LIFT = 0.75F;
	/** Pawing at the ground: how fast (radians a tick) and how far (radians). */
	static final float PAW_RATE = 0.45F, PAW = 0.35F;
	/** The cord as it's thrown on: how many turns it spins round the legs, and how far out it starts (times its cinched size). */
	static final float WRAP_TURNS = 2.5F, WRAP_FLARE = 3.0F;
	/** Where the legs meet in the right leg's space (pixels): the cord turns about here. */
	static final float LEGS_MID_X = 1.9F;

	/** This frame's trip: {@code age} ticks in (with the partial tick), its {@code ticks} on the ground, the fall's {@code tip} (0 standing, 1 flat, a little over in the bounce). */
	record Pose(float age, int ticks, float tip, int id) {
	}

	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:tripped");
	static final Identifier CORD = JaymosPowers.id("textures/entity/wounds/bolas_cord.png");

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("tripped");

	private TrippedPose() {
	}

	/** Whether this body is tripped (its limbs are this pose's). */
	public static boolean tripped(Object state) {
		return state instanceof LivingEntityRenderState living && living.getData(KEY) != null;
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register(layer(renderer)));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Tripped.Trip trip = entity instanceof Player ? null : entity.getAttached(Tripped.TRIP);
		if (trip == null) {
			state.setData(KEY, null);
			return;
		}
		float age = Math.max(0.0F, entity.level().getGameTime() + partialTicks - trip.start());
		state.setData(KEY, new Pose(age, trip.ticks(), tip(age, trip.ticks()), entity.getId()));
	}

	/**
	 * The fall, {@code age} ticks in: a teeter as the cord cinches, then dropping faster and faster, a bounce as it lands;
	 * flat until the trip's over, then pushed back up.
	 */
	static float tip(float age, int ticks) {
		float wrap = Tripped.WRAP_TICKS, fall = Tripped.FALL_TICKS;
		if (age < wrap) {
			return TEETER * PoseBlend.smooth(age / wrap);
		}
		if (age < wrap + fall) {
			float d = (age - wrap) / fall;
			return TEETER + (1.0F - TEETER) * d * d;
		}
		if (age < ticks) {
			float after = age - wrap - fall;
			return 1.0F + BOUNCE * (float)Math.exp(-after * BOUNCE_DECAY) * Mth.sin(after * BOUNCE_RING);
		}
		return 1.0F - PoseBlend.smooth((age - ticks) / Tripped.GET_UP_TICKS);
	}

	/** After the body's been turned to face its way: tip it forward about its feet, onto its face. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || state.deathTime > 0.0F) {
			return;
		}
		float tip = pose.tip();
		float flat = Mth.clamp(tip, 0.0F, 1.0F);
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		if (pose.age() < Tripped.WRAP_TICKS) {
			// teetering, side to side, as the legs are pulled together
			poseStack.rotate(Axis.ZP.rotationDegrees(TEETER_SWAY * Mth.sin(pose.age() * 1.6F) * (1.0F - pose.age() / Tripped.WRAP_TICKS)));
		}
		poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * tip));
		poseStack.translate(0.0F, -height * 0.555F * flat * flat, FACE_DOWN_LIFT * flat * flat);
	}

	/** Right after a model was posed for this state (the body, and each armour piece). */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || living.deathTime > 0.0F) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		if (!(model instanceof HumanoidModel<?> m)) {
			ModelPart[] parts = PoseBlend.parts(model);
			if (parts != null) {
				PoseBlend.Frame f = new PoseBlend.Frame(parts);
				pose(f, living, pose);
				f.put(parts, 1.0F);
			}
			return;
		}
		PoseBlend.Track track = BLEND.track(pose.id(), true);
		if (track == null) {
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(m);
		pose(f, living, pose);
		// (snappy while the cord cinches and it falls, so the flail isn't smoothed away)
		track.apply(m, f, pose.age() < Tripped.WRAP_TICKS + Tripped.FALL_TICKS ? 3.0F : 1.2F);
	}

	/** The frame: standing bound and flailing, falling with arms thrown out, down and kicking, or pushing up. */
	private static void pose(PoseBlend.Frame f, LivingEntityRenderState state, Pose pose) {
		float age = pose.age();
		float wrap = Tripped.WRAP_TICKS, falls = wrap + Tripped.FALL_TICKS;
		int ticks = pose.ticks();
		float a = state.ageInTicks + (pose.id() & 15);
		if (age < wrap) {
			// the cord cinches: legs pulled together, arms windmilling for balance, a look down at the feet
			float mill = Mth.sin(a * 1.4F);
			f.set(PoseBlend.RIGHT_ARM, -1.4F + 0.9F * mill, 0.0F, 1.1F);
			f.set(PoseBlend.LEFT_ARM, -1.4F - 0.9F * mill, 0.0F, -1.1F);
			bound(f, 0.0F);
			f.set(PoseBlend.HEAD, 0.6F, 0.0F, 0.0F);
			return;
		}
		if (age < falls || age >= ticks) {
			// pitching forward (or pushing back up): the arms out ahead to take it, the head up out of the dirt
			float up = age >= ticks ? PoseBlend.smooth((age - ticks) / Tripped.GET_UP_TICKS) : 0.0F;
			f.set(PoseBlend.RIGHT_ARM, Mth.lerp(up, -2.6F, -1.3F), -0.1F, 0.35F);
			f.set(PoseBlend.LEFT_ARM, Mth.lerp(up, -2.6F, -1.3F), 0.1F, -0.35F);
			bound(f, up > 0.5F ? 0.0F : 0.15F);
			f.set(PoseBlend.HEAD, -0.9F, 0.0F, 0.0F);
			f.scale(1.0F - up * up);
			return;
		}
		// down, bound: pawing at the ground, now and then a fit of kicking the tied legs together, the body squirming
		float paw = Mth.sin(a * PAW_RATE);
		f.set(PoseBlend.RIGHT_ARM, -2.55F + PAW * paw, -0.1F, 0.4F);
		f.set(PoseBlend.LEFT_ARM, -2.55F - PAW * paw, 0.1F, -0.4F);
		float down = age - falls;
		float every = KICK_EVERY + (pose.id() & 7) * 2.0F;
		float fit = down < KICK_TICKS ? PoseBlend.bump(down, 0.0F, 3.0F, KICK_TICKS - 5.0F, KICK_TICKS)
			: PoseBlend.bump((down + (pose.id() * 13 & 31)) % every, 0.0F, 3.0F, KICK_TICKS - 5.0F, KICK_TICKS);
		float kick = (0.5F + 0.5F * Mth.sin(a * KICK_RATE)) * fit;
		bound(f, KICK_LIFT * kick);
		f.set(PoseBlend.BODY, 0.0F, 0.12F * Mth.sin(a * KICK_RATE * 0.5F) * fit, 0.0F);
		f.set(PoseBlend.HEAD, -1.0F + 0.15F * fit, 0.25F * Mth.sin(a * 0.2F), 0.0F);
	}

	/** Both legs straight and pressed together, raised back off the ground by {@code lift} (radians) as one. */
	private static void bound(PoseBlend.Frame f, float lift) {
		f.set(PoseBlend.RIGHT_LEG, lift, 0.0F, 0.0F);
		f.set(PoseBlend.LEFT_LEG, lift, 0.0F, 0.0F);
	}

	// ---- the cord on the legs ----------------------------------------------------------------------------------------------

	private static @Nullable ModelPart cord;

	/**
	 * In the right leg's space (pixels: the leg from the hip at y 0 down to 12, the left leg alongside at +x): two turns
	 * of cord round both shins and a knot at the front, the two stone weights hanging from it.
	 */
	private static ModelPart cord() {
		if (cord == null) {
			CubeListBuilder cubes = CubeListBuilder.create();
			// the turns, standing just off both legs (x -2 to 5.8), one below the knee and one at the ankle
			cubes.texOffs(0, 0).addBox(-2.4F, 6.6F, -2.4F, 8.6F, 0.8F, 4.8F);
			cubes.texOffs(0, 6).addBox(-2.4F, 9.8F, -2.4F, 8.6F, 0.8F, 4.8F);
			// the knot between the legs, at the front, and the cord hanging from it to the weights
			cubes.texOffs(0, 12).addBox(1.3F, 9.4F, -3.0F, 1.2F, 1.4F, 0.8F);
			cubes.texOffs(4, 12).addBox(0.2F, 10.8F, -2.9F, 0.5F, 1.6F, 0.5F);
			cubes.texOffs(4, 12).addBox(3.1F, 10.8F, -2.9F, 0.5F, 1.2F, 0.5F);
			// the weights: two round stones
			cubes.texOffs(8, 12).addBox(-0.4F, 12.2F, -3.4F, 1.6F, 1.6F, 1.6F);
			cubes.texOffs(8, 12).addBox(2.5F, 11.8F, -3.4F, 1.6F, 1.6F, 1.6F);
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("cord", cubes, PartPose.ZERO);
			cord = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("cord");
		}
		return cord;
	}

	/** On every living renderer; draws only on a tripped body built like a person (a right leg it can find). */
	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S living, float yRot, float xRot) {
			Pose pose = living.getData(KEY);
			if (pose == null || living.isInvisible || living.deathTime > 0.0F || pose.age() >= pose.ticks()) {
				// (as it gets up the cord's off: Kingdoms drops the bolas at its feet)
				return;
			}
			EntityModel<S> model = this.getParentModel();
			ModelPart leg;
			if (model instanceof HumanoidModel<?> h) {
				leg = h.rightLeg;
			} else {
				ModelPart[] parts = PoseBlend.parts(model);
				if (parts == null) {
					return;
				}
				leg = parts[4];
			}
			if (!leg.visible || leg.skipDraw) {
				return;
			}
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			leg.translateAndRotate(poseStack);
			float w = PoseBlend.smooth(pose.age() / Tripped.WRAP_TICKS);
			if (w < 1.0F) {
				// thrown on: spinning round the legs and drawing in tight
				float mid = LEGS_MID_X / 16.0F, flare = 1.0F + (WRAP_FLARE - 1.0F) * (1.0F - w);
				poseStack.translate(mid, 0.0F, 0.0F);
				poseStack.rotate(Axis.YP.rotationDegrees(360.0F * WRAP_TURNS * (1.0F - w)));
				poseStack.scale(flare, 1.0F, flare);
				poseStack.translate(-mid, 0.0F, 0.0F);
			}
			collector.submitModelPart(cord(), poseStack, RenderTypes.entityCutout(CORD), lightCoords, LivingEntityRenderer.getOverlayCoords(living, 0.0F), null);
			poseStack.popPose();
		}
	}
}
