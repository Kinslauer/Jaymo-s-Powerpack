package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.corpse.CorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.client.renderer.entity.EndermiteRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SilverfishRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Draws a corpse as the mob it was: the mob is rebuilt from the corpse's saved look and drawn by its own
 * renderer, posed at the very end of its death animation (keeled over), without the red damage flash.
 */
public class CorpseRenderer extends EntityRenderer<CorpseEntity, CorpseRenderer.State> {
	public static class State extends EntityRenderState {
		@Nullable EntityRenderState body;
		@Nullable EntityRenderer<?, ?> bodyRenderer;
		/** Being carried: drawn lying on its back across the carrier's arms instead of fallen over. */
		boolean carried;
		/** The carrier's facing, and the body's size, for the carry pose. */
		float carryYaw;
		float bodyHeight;
		boolean tall;
		/** A player's corpse: drawn here as a player model in their skin. */
		boolean player;
		/** A humanoid mob lying sprawled flat (see Ragdoll), rather than keeled over. */
		boolean sprawled;
		/** Falling from someone's arms: how far it has turned over, end over end and side to side (degrees). */
		boolean tumbling;
		float tumblePitch;
		float tumbleRoll;
		float yaw;
		boolean faceUp;
		/** A heavy (iron) corpse falling apart: 0 whole, 1 gone. It shakes, slumps and sinks as it goes. */
		float crumble;
		float age;
		final AvatarRenderState avatar = new AvatarRenderState();
	}

	private final PlayerModel wide;
	private final PlayerModel slim;

	public CorpseRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.wide = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
		this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	@Override
	protected boolean shouldShowName(CorpseEntity corpse, double distanceToCameraSq) {
		// the body (a player's skin, say) is enough to tell who it was
		return false;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CorpseEntity corpse, State state, float partialTicks) {
		super.extractRenderState(corpse, state, partialTicks);
		// it burns from the inside (see CorpseEntity#smoulder), not in a sheet of fire
		state.displayFireAnimation = false;
		state.body = null;
		state.bodyRenderer = null;
		state.player = false;
		state.crumble = corpse.crumble(partialTicks);
		state.age = corpse.tickCount + partialTicks;
		float strength = corpse.jolt(partialTicks);
		// a blow or rummaging: its limbs flop (see Jolt), the body stays put
		Jolt jolt = strength > 0.0F ? new Jolt(strength, corpse.joltTime(partialTicks), corpse.joltSide()) : null;
		float yaw = corpse.getYRot();
		Entity carrier = corpse.carrier();
		// dropped and still falling: drawn limp as if carried, turning over as it goes, until it lands
		boolean tumbling = carrier == null && corpse.isTumbling() && !corpse.isPassenger();
		state.carried = carrier != null || tumbling;
		state.tumbling = tumbling;
		state.tumblePitch = tumbling ? corpse.tumblePitch(partialTicks) : 0.0F;
		state.tumbleRoll = tumbling ? corpse.tumbleRoll(partialTicks) : 0.0F;
		if (tumbling) {
			// a carried body faces across the carrier (yaw + 90): the same, from the way it was held
			yaw = corpse.getYRot() - 90.0F;
			state.carryYaw = yaw;
		}
		if (carrier != null) {
			// carried: drawn from the carrier's smooth (per-frame) position and facing, not the per-tick one
			yaw = carrier.getYRot(partialTicks);
			Vec3 at = CorpseEntity.carryPosition(carrier, yaw,
				Mth.lerp(partialTicks, carrier.xo, carrier.getX()),
				Mth.lerp(partialTicks, carrier.yo, carrier.getY()),
				Mth.lerp(partialTicks, carrier.zo, carrier.getZ()), corpse.getBbWidth());
			state.x = at.x;
			state.y = at.y;
			state.z = at.z;
			state.carryYaw = yaw;
		}
		if (corpse.isPlayerCorpse()) {
			extractPlayer(corpse, state, carrier, yaw, partialTicks);
			state.avatar.setData(Jolt.KEY, jolt);
			state.avatar.setData(Severed.KEY, Severed.of(corpse.missing()));
			return;
		}
		Entity body = corpse.clientBody(yaw);
		if (body == null) {
			return;
		}
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
		Object kindOfRenderer = renderer;
		if (state.carried) {
			state.bodyHeight = body.getBbHeight();
			// four-legged animals go belly down across the arms, whatever their size; humanoids (and other tall bodies)
			// on their backs
			boolean fourLegged = kindOfRenderer instanceof LivingEntityRenderer<?, ?, ?> living
				&& living.getModel().root().hasChild("right_front_leg");
			boolean humanoid = kindOfRenderer instanceof LivingEntityRenderer<?, ?, ?> living
				&& living.getModel().root().hasChild("right_arm");
			state.tall = !fourLegged && (humanoid || body.getBbHeight() > body.getBbWidth() * 1.1F);
			// tall bodies face away before being laid back; long bodies lie across the arms
			corpse.clientBody(state.tall ? yaw : yaw + 90.0F);
		}
		if (!state.carried && (kindOfRenderer instanceof SpiderRenderer<?> || kindOfRenderer instanceof SilverfishRenderer
			|| kindOfRenderer instanceof EndermiteRenderer)) {
			// these die flipping onto their backs, pivoting at the feet, which buries the body: lift it out
			state.y += body.getBbHeight();
		} else if (!state.carried) {
			// the rest keel over sideways from the feet (toward yaw's (cos, sin)): shift back so the body's middle,
			// not its feet, sits over the corpse (the middle of the butcher block)
			float half = body.getBbHeight() / 2.0F;
			float radians = yaw * Mth.DEG_TO_RAD;
			state.x -= Mth.cos(radians) * half;
			state.z -= Mth.sin(radians) * half;
		}
		EntityRenderState bodyState = renderer.createRenderState(body, partialTicks);
		state.sprawled = false;
		if (bodyState instanceof LivingEntityRenderState living) {
			living.hasRedOverlay = false;
			// fallen over on the ground; carried, it's posed by hand instead (see submit)
			living.deathTime = state.carried ? 0.0F : Math.max(living.deathTime, 20.0F);
			if (!state.carried && kindOfRenderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer
				&& livingRenderer.getModel() instanceof HumanoidModel<?>) {
				// humanoids (zombies, piglins, skeletons...) sprawl flat like a player's body, not keeled over
				Ragdoll ragdoll = Ragdoll.of(corpse.poseSeed());
				living.setData(Ragdoll.KEY, ragdoll);
				living.deathTime = 0.0F;
				living.walkAnimationPos = 0.0F;
				living.walkAnimationSpeed = 0.0F;
				state.sprawled = true;
				state.faceUp = ragdoll.faceUp();
				state.bodyHeight = body.getBbHeight();
				state.yaw = yaw;
				// undo the keel-over offset: it lies centred on the corpse
				float half = body.getBbHeight() / 2.0F;
				float radians = yaw * Mth.DEG_TO_RAD;
				state.x += Mth.cos(radians) * half;
				state.z += Mth.sin(radians) * half;
			}
			if (!state.carried && body instanceof net.minecraft.world.entity.animal.fish.AbstractFish) {
				// a fish already flops onto its side out of water (its renderer does it); the death roll on top of that
				// would sink it into the ground
				living.deathTime = 0.0F;
				living.walkAnimationSpeed = 0.0F;
				living.ageInTicks = corpse.poseSeed() % 100;
				unshift(state, body, yaw);
			} else if (!state.carried && body instanceof net.minecraft.world.entity.animal.squid.Squid) {
				// a squid slumps flat on its side, tentacles splayed along the ground, rather than keeling over from its
				// tentacle tips (which buries it)
				living.deathTime = 0.0F;
				state.sprawled = true;
				state.faceUp = (corpse.poseSeed() & 1) == 0;
				state.bodyHeight = body.getBbHeight();
				state.yaw = yaw;
				unshift(state, body, yaw);
			}
			if (state.carried) {
				// limp: limbs hanging free, swinging with the carrier's movement (see Limp)
				living.setData(Limp.KEY, new Limp(corpse.swingBack(partialTicks), corpse.swingLeft(partialTicks)));
				living.walkAnimationPos = 0.0F;
				living.walkAnimationSpeed = 0.0F;
				living.xRot = 0.0F;
				living.yRot = 0.0F;
			}
		}
		bodyState.displayFireAnimation = false;
		// no name over a body (a zombie player's, say)
		bodyState.nameTag = null;
		bodyState.setData(Jolt.KEY, jolt);
		bodyState.setData(Severed.KEY, Severed.of(corpse.missing()));
		state.body = bodyState;
		state.bodyRenderer = renderer;
	}

	/** Undoes the keel-over offset: the body lies centred on the corpse. */
	private static void unshift(State state, Entity body, float yaw) {
		float half = body.getBbHeight() / 2.0F;
		float radians = yaw * Mth.DEG_TO_RAD;
		state.x += Mth.cos(radians) * half;
		state.z += Mth.sin(radians) * half;
	}

	/** A dead player: their skin, sprawled where they fell (see {@link Ragdoll}), or hanging limp when carried. */
	private static void extractPlayer(CorpseEntity corpse, State state, @Nullable Entity carrier, float yaw, float partialTicks) {
		state.player = true;
		state.yaw = yaw;
		state.bodyHeight = 1.8F;
		state.tall = true;
		AvatarRenderState avatar = state.avatar;
		avatar.skin = Skins.of(corpse.owner());
		avatar.showCape = false;
		avatar.lightCoords = state.lightCoords;
		avatar.outlineColor = state.outlineColor;
		avatar.walkAnimationPos = 0.0F;
		avatar.walkAnimationSpeed = 0.0F;
		avatar.yRot = 0.0F;
		avatar.xRot = 0.0F;
		avatar.setData(Ragdoll.KEY, null);
		avatar.setData(Limp.KEY, null);
		avatar.nameTag = null;
		if (state.carried) {
			avatar.setData(Limp.KEY, new Limp(corpse.swingBack(partialTicks), corpse.swingLeft(partialTicks)));
		} else {
			Ragdoll ragdoll = Ragdoll.of(corpse.poseSeed());
			avatar.setData(Ragdoll.KEY, ragdoll);
			state.faceUp = ragdoll.faceUp();
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		if (state.tumbling) {
			// falling: it turns over about its middle, end over end and side to side, as fast as it's spinning
			poseStack.pushPose();
			float yaw = state.carryYaw * Mth.DEG_TO_RAD;
			poseStack.translate(0.0F, 0.3F, 0.0F);
			// end over end about the body's own crosswise axis, then rolling about its length
			poseStack.rotate(new org.joml.Quaternionf().rotationAxis(state.tumblePitch * Mth.DEG_TO_RAD, Mth.cos(yaw), 0.0F, Mth.sin(yaw)));
			poseStack.rotate(new org.joml.Quaternionf().rotationAxis(state.tumbleRoll * Mth.DEG_TO_RAD, -Mth.sin(yaw), 0.0F, Mth.cos(yaw)));
			poseStack.translate(0.0F, -0.3F, 0.0F);
			submitWhole(state, poseStack, collector, camera);
			poseStack.popPose();
			return;
		}
		submitWhole(state, poseStack, collector, camera);
	}

	private void submitWhole(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.crumble > 0.0F && !state.player) {
			// falling apart: shuddering harder as it goes, slumping flatter and sinking into the ground
			float c = state.crumble;
			float shake = 0.04F * c;
			poseStack.pushPose();
			poseStack.translate(Mth.sin(state.age * 2.7F) * shake, -0.35F * c * c, Mth.cos(state.age * 3.1F) * shake);
			poseStack.scale(1.0F + 0.15F * c, 1.0F - 0.6F * c * c, 1.0F + 0.15F * c);
			state.crumble = 0.0F;
			submitWhole(state, poseStack, collector, camera);
			state.crumble = c;
			poseStack.popPose();
			return;
		}
		if (state.player) {
			submitPlayer(state, poseStack, collector);
			return;
		}
		if (state.body != null && state.bodyRenderer != null) {
			poseStack.pushPose();
			if (state.carried) {
				layAcrossArms(state, poseStack);
			} else if (state.sprawled) {
				// tipped flat about its side-to-side axis, face up or down, its middle over the corpse
				float radians = state.yaw * Mth.DEG_TO_RAD;
				poseStack.translate(0.0F, 2.0F / 16.0F, 0.0F);
				poseStack.rotate(new Quaternionf().rotationAxis(state.faceUp ? -Mth.HALF_PI : Mth.HALF_PI, Mth.cos(radians), 0.0F,
					Mth.sin(radians)));
				poseStack.translate(0.0F, -state.bodyHeight / 2.0F, 0.0F);
			}
			submitBody(state.bodyRenderer, state.body, poseStack, collector, camera);
			poseStack.popPose();
		}
	}

	private void submitPlayer(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		poseStack.pushPose();
		if (state.carried) {
			layAcrossArms(state, poseStack);
			poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yaw));
		} else {
			// flat on the ground, face up or down, its middle over the corpse
			poseStack.translate(0.0F, 2.0F / 16.0F, 0.0F);
			poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yaw));
			poseStack.rotate(Axis.XP.rotationDegrees(state.faceUp ? 90.0F : -90.0F));
			poseStack.translate(0.0F, -0.9F, 0.0F);
		}
		// as players are drawn
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		PlayerModel model = state.avatar.skin.model() == PlayerModelType.SLIM ? slim : wide;
		collector.submitModel(model, state.avatar, poseStack, state.avatar.skin.body().texturePath(), state.lightCoords,
			OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}

	/**
	 * Carried: on its back across the arms. Rotations are in world space about the carry point (the body's own
	 * renderer then applies its facing). A tall body faces away from the carrier, is tipped back so its face
	 * points up (head toward the carrier), then swung round so it lies across the arms, and centred. A long body
	 * already lies across the arms (it faces sideways): it's just let down so its belly rests on them.
	 */
	private static void layAcrossArms(State state, PoseStack poseStack) {
		float yaw = state.carryYaw * Mth.DEG_TO_RAD;
		// the carrier's right, as a direction in the world
		float rightX = -Mth.cos(yaw);
		float rightZ = -Mth.sin(yaw);
		float h = state.bodyHeight;
		if (state.tall) {
			poseStack.translate(-rightX * h / 2.0F, 0.0F, -rightZ * h / 2.0F);
			poseStack.rotate(new Quaternionf().rotationY(Mth.HALF_PI));
			poseStack.rotate(new Quaternionf().rotationAxis(-Mth.HALF_PI, -rightX, 0.0F, -rightZ));
		} else {
			// belly down across the arms, its legs dangling below them
			poseStack.translate(0.0F, -h * 0.55F, 0.0F);
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends EntityRenderState> void submitBody(EntityRenderer<?, S> renderer, EntityRenderState state,
		PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		renderer.submit((S) state, poseStack, collector, camera);
	}
}
