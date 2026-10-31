package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.client.ragdoll.ClientRagdolls;
import com.jaymoskitchen.client.ragdoll.RagdollPose;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
		/** (0.4.39) Ragdoll physics (see Flop): drawn tipped to the ground, bounced up, and still swinging into place. */
		float anchorX, anchorY, anchorZ;
		final Quaternionf tilt = new Quaternionf();
		final Quaternionf turn = new Quaternionf();
		final Vector3f move = new Vector3f();
		final float[] lift = new float[1];
		/** A body sprawled flat (see Ragdoll), whose limbs lie on the ground. */
		boolean ragdolled;
		/** How far the body's own renderer turns it over as it dies (spiders roll right onto their backs). */
		float flip;
		/** (0.5.1) Drawn by physics: where the ragdoll's points put the body this frame (null when carried, slung, in a cart). */
		@Nullable RagdollPose pose;
		/** (0.5.23) Nailed to a wall by an arrow: drawn upright facing out from it ({@link #yaw}), slumped forward. */
		boolean pinned;
	}

	/** (0.5.23) How far a pinned body slumps forward off its wall (degrees). */
	private static final float PIN_SLUMP = 8.0F;

	/**
	 * (0.4.42) A dead player's body: the player model, but baked from layers of its own. Entity Model Features finds
	 * models by layer, so Fresh Animations: Player Extension's animations ran on the vanilla player layers as the body was
	 * drawn, after its ragdoll pose (see Ragdoll, Flop), and stood its limbs back up. These layers have none.
	 */
	public static final ModelLayerLocation PLAYER_BODY = new ModelLayerLocation(JaymosKitchen.id("player_corpse"), "main");
	public static final ModelLayerLocation PLAYER_BODY_SLIM = new ModelLayerLocation(JaymosKitchen.id("player_corpse"), "slim");

	private final PlayerModel wide;
	private final PlayerModel slim;

	public CorpseRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.wide = new PlayerModel(context.bakeLayer(PLAYER_BODY), false);
		this.slim = new PlayerModel(context.bakeLayer(PLAYER_BODY_SLIM), true);
	}

	/** The player model, as vanilla builds it (LayerDefinitions), for {@link #PLAYER_BODY}. */
	public static LayerDefinition playerBody(boolean slim) {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64);
	}

	@Override
	protected boolean shouldShowName(CorpseEntity corpse, double distanceToCameraSq) {
		// the body (a player's skin, say) is enough to tell who it was
		return false;
	}

	/**
	 * (0.4.45) Falling, the body turns over well outside the corpse's small box, so it isn't cut off at the screen's
	 * edge by that box (see CorpseEntity#shouldRenderAtSqrDistance for distance).
	 */
	@Override
	protected boolean affectedByCulling(CorpseEntity corpse) {
		ClientRagdolls.Track track = ClientRagdolls.get(corpse.getId());
		if (track != null && corpse.carrier() == null) {
			// (0.5.1) a ragdoll, moving or sprawled at rest, reaches well outside the corpse's small box
			return false;
		}
		return !(corpse.isTumbling() || corpse.falling()) && super.affectedByCulling(corpse);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CorpseEntity corpse, State state, float partialTicks) {
		super.extractRenderState(corpse, state, partialTicks);
		// the corpse's own spot: the body is drawn placed from here (see place), and swings about it (see Flop)
		double baseX = state.x, baseY = state.y, baseZ = state.z;
		state.ragdolled = false;
		state.flip = 90.0F;
		// it burns from the inside (see CorpseEntity#smoulder), not in a sheet of fire
		state.displayFireAnimation = false;
		state.body = null;
		state.bodyRenderer = null;
		state.player = false;
		state.crumble = corpse.crumble(partialTicks);
		state.age = corpse.tickCount + partialTicks;
		float yaw = corpse.getYRot();
		Entity carrier = corpse.carrier();
		state.pose = null;
		state.pinned = carrier == null && corpse.pinned() >= 0;
		if (state.pinned) {
			extractPinned(corpse, state, corpse.pinned(), partialTicks);
			return;
		}
		if (carrier == null && !corpse.isPassenger() && corpse.slungOn() == null) {
			ClientRagdolls.Track track = ClientRagdolls.get(corpse.getId());
			if (track != null) {
				extractPhysical(corpse, state, track, partialTicks);
				return;
			}
		}
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
		Entity horse = carrier == null ? corpse.slungOn() : null;
		if (horse != null) {
			// (0.4.49) slung over a horse: limp across its back as if carried, swinging with its stride, turned with it
			state.carried = true;
			state.tumbling = false;
			yaw = horse instanceof net.minecraft.world.entity.LivingEntity living
				? Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot) : horse.getYRot(partialTicks);
			state.carryYaw = yaw;
		}
		// (0.4.44) a dead player is drawn as the villager MCA draws them as (their genes, hair and clothes), lying like any
		// other villager's body; without one, in their player skin
		Entity look = corpse.isPlayerCorpse() ? McaLooks.of(corpse.owner()) : null;
		if (corpse.isPlayerCorpse() && look == null) {
			extractPlayer(corpse, state, carrier, yaw, partialTicks);
			state.avatar.setData(Severed.KEY, Severed.of(corpse.missing()));
			physics(corpse, state, state.avatar, baseX, baseY, baseZ, partialTicks);
			return;
		}
		Entity body = look != null ? look : corpse.clientBody(yaw);
		if (body == null) {
			return;
		}
		if (look != null) {
			corpse.placeBody(look, yaw);
		}
		state.bodyHeight = body.getBbHeight();
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
			corpse.placeBody(body, state.tall ? yaw : yaw + 90.0F);
		}
		if (!state.carried && (kindOfRenderer instanceof SpiderRenderer<?> || kindOfRenderer instanceof SilverfishRenderer
			|| kindOfRenderer instanceof EndermiteRenderer)) {
			// these die flipping onto their backs, pivoting at the feet, which buries the body: lift it out
			state.y += body.getBbHeight();
			state.flip = 180.0F;
		} else if (!state.carried) {
			// the rest keel over sideways from the feet (toward yaw's (cos, sin)): shift back so the body's middle,
			// not its feet, sits over the corpse (the middle of the butcher block)
			float half = body.getBbHeight() / 2.0F;
			float radians = yaw * Mth.DEG_TO_RAD;
			state.x -= Mth.cos(radians) * half;
			state.z -= Mth.sin(radians) * half;
		}
		EntityRenderState bodyState = lookState(corpse, body, look, renderer, partialTicks);
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
				state.ragdolled = true;
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
		if (look != null) {
			// MCA's villager for the player isn't in the world: lit where the corpse lies, not where it was made
			bodyState.lightCoords = state.lightCoords;
		}
		// no name over a body (a zombie player's, say)
		bodyState.nameTag = null;
		bodyState.setData(Severed.KEY, Severed.of(corpse.missing()));
		ClosedEyes.mark(bodyState);
		bodyState.setData(com.jaymoskitchen.client.archery.StuckArrowsLayer.KEY, arrows(corpse));
		state.body = bodyState;
		state.bodyRenderer = renderer;
		physics(corpse, state, bodyState, baseX, baseY, baseZ, partialTicks);
	}

	/** (0.5.23) A body nailed to a wall: standing as it hangs there, facing out at {@code yaw}, no physics. */
	private void extractPinned(CorpseEntity corpse, State state, float yaw, float partialTicks) {
		state.carried = false;
		state.tumbling = false;
		state.sprawled = false;
		state.ragdolled = false;
		state.tall = true;
		state.player = false;
		state.body = null;
		state.bodyRenderer = null;
		state.yaw = yaw;
		state.anchorX = state.anchorY = state.anchorZ = 0.0F;
		state.tilt.identity();
		state.turn.identity();
		state.move.set(0.0F, 0.0F, 0.0F);
		state.lift[0] = 0.0F;
		Entity look = corpse.isPlayerCorpse() ? McaLooks.of(corpse.owner()) : null;
		if (corpse.isPlayerCorpse() && look == null) {
			extractPlayer(corpse, state, null, yaw, partialTicks);
			AvatarRenderState avatar = state.avatar;
			avatar.setData(Ragdoll.KEY, null);
			avatar.setData(Limp.KEY, null);
			avatar.setData(Flop.KEY, null);
			avatar.setData(RagdollPose.KEY, null);
			avatar.setData(Pinned.KEY, Pinned.HANGING);
			avatar.setData(Severed.KEY, Severed.of(corpse.missing()));
			return;
		}
		Entity body = look != null ? look : corpse.clientBody(yaw);
		if (body == null) {
			return;
		}
		if (look != null) {
			corpse.placeBody(look, yaw);
		}
		state.bodyHeight = body.getBbHeight();
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
		EntityRenderState bodyState = lookState(corpse, body, look, renderer, partialTicks);
		if (bodyState instanceof LivingEntityRenderState living) {
			living.hasRedOverlay = false;
			living.deathTime = 0.0F;
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
			living.xRot = 0.0F;
			living.yRot = 0.0F;
		}
		bodyState.displayFireAnimation = false;
		if (look != null) {
			bodyState.lightCoords = state.lightCoords;
		}
		bodyState.nameTag = null;
		bodyState.setData(Severed.KEY, Severed.of(corpse.missing()));
		bodyState.setData(Pinned.KEY, Pinned.HANGING);
		ClosedEyes.mark(bodyState);
		bodyState.setData(com.jaymoskitchen.client.archery.StuckArrowsLayer.KEY, arrows(corpse));
		state.body = bodyState;
		state.bodyRenderer = renderer;
	}

	/**
	 * (0.5.37) {@code body}'s render state; a dead player's look ({@code look}, MCA's villager for them) wears the tags
	 * they died with while it's taken ({@link CorpseEntity#lookTags}: a Bio-built's form, so whoever draws that look draws
	 * the form they died in, Andy: "it should always look the way it did when the player died"), and then sheds them,
	 * since MCA draws the living player from the same villager.
	 */
	private static EntityRenderState lookState(CorpseEntity corpse, Entity body, @Nullable Entity look, EntityRenderer<? super Entity, ?> renderer,
		float partialTicks) {
		List<String> tags = look != null && body == look ? corpse.lookTags() : List.of();
		List<String> added = new ArrayList<>();
		for (String tag : tags) {
			if (body.addTag(tag)) {
				added.add(tag);
			}
		}
		try {
			return renderer.createRenderState(body, partialTicks);
		} finally {
			for (String tag : added) {
				body.removeTag(tag);
			}
		}
	}

	/**
	 * (0.5.1) A body lying free: drawn where its ragdoll's points are this frame. The model is drawn standing at the
	 * corpse's own spot, then moved onto the ragdoll by {@link RagdollPose#body}; the limbs follow the physics.
	 */
	private void extractPhysical(CorpseEntity corpse, State state, ClientRagdolls.Track track, float partialTicks) {
		state.carried = false;
		state.tumbling = false;
		state.sprawled = false;
		state.ragdolled = false;
		state.tall = false;
		state.player = false;
		state.body = null;
		state.bodyRenderer = null;
		state.anchorX = state.anchorY = state.anchorZ = 0.0F;
		state.tilt.identity();
		state.turn.identity();
		state.move.set(0.0F, 0.0F, 0.0F);
		state.lift[0] = 0.0F;
		// (0.5.16) a dead animal settles onto its side, not propped belly down on its legs
		RagdollPose pose = RagdollPose.of(track.rig, track.points(partialTicks), state.x, state.y, state.z,
			track.side(partialTicks), track.sideUp(), false);
		state.pose = pose;
		Entity look = corpse.isPlayerCorpse() ? McaLooks.of(corpse.owner()) : null;
		if (corpse.isPlayerCorpse() && look == null) {
			extractPlayer(corpse, state, null, 0.0F, partialTicks);
			AvatarRenderState avatar = state.avatar;
			avatar.setData(Ragdoll.KEY, null);
			avatar.setData(Flop.KEY, null);
			avatar.setData(RagdollPose.KEY, pose);
			avatar.setData(Severed.KEY, Severed.of(corpse.missing()));
			return;
		}
		Entity body = look != null ? look : corpse.clientBody(0.0F);
		if (body == null) {
			return;
		}
		if (look != null) {
			corpse.placeBody(look, 0.0F);
		}
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
		if (renderer instanceof LivingEntityRenderer<?, ?, ?> living && living.getModel().root().hasChild("right_middle_front_leg")) {
			// (0.5.19) a dead spider rolls onto its back, legs curled up
			pose = RagdollPose.of(track.rig, track.points(partialTicks), state.x, state.y, state.z, track.side(partialTicks),
				track.sideUp(), true);
			state.pose = pose;
		}
		EntityRenderState bodyState = lookState(corpse, body, look, renderer, partialTicks);
		if (bodyState instanceof LivingEntityRenderState living) {
			// standing, facing front, nothing flinching: the physics does all the posing
			living.hasRedOverlay = false;
			living.deathTime = 0.0F;
			living.bodyRot = 0.0F;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
		}
		bodyState.displayFireAnimation = false;
		if (look != null) {
			bodyState.lightCoords = state.lightCoords;
		}
		bodyState.nameTag = null;
		bodyState.setData(Severed.KEY, Severed.of(corpse.missing()));
		ClosedEyes.mark(bodyState);
		bodyState.setData(com.jaymoskitchen.client.archery.StuckArrowsLayer.KEY, arrows(corpse));
		// (0.5.40) whatever the ragdoll doesn't drive (a vulture's wings and neck, a modded mob's legs) hangs loose
		pose.loosen(com.jaymoskitchen.client.ragdoll.LooseParts.motion(corpse), state.age, !corpse.isTumbling(),
			bodyState instanceof LivingEntityRenderState living ? living.scale : 1.0F);
		bodyState.setData(RagdollPose.KEY, pose);
		state.body = bodyState;
		state.bodyRenderer = renderer;
	}

	/**
	 * (0.4.39) Runs the body's ragdoll physics (see Flop) and hands this frame's result to the drawing: where the body
	 * is placed now (relative to the corpse's spot) is what it swings from the next time it changes pose.
	 */
	private void physics(CorpseEntity corpse, State state, EntityRenderState bodyState, double baseX, double baseY, double baseZ,
		float partialTicks) {
		Flop flop = Flop.of(corpse);
		Flop.Layout layout = state.tumbling ? Flop.Layout.TUMBLING : state.carried ? Flop.Layout.CARRIED : Flop.Layout.LYING;
		boolean onGround = !state.carried && (state.player || state.ragdolled);
		flop.update(corpse, layout, onGround, state.faceUp, state.bodyHeight);
		state.anchorX = (float) (baseX - state.x);
		state.anchorY = (float) (baseY - state.y);
		state.anchorZ = (float) (baseZ - state.z);
		Matrix4f placement = placement(state);
		if (layout == Flop.Layout.LYING) {
			// just died: it falls from where the dying mob was last drawn, keeled over on its side at the corpse's spot
			flop.fallFrom(new Matrix4f().rotateY((180.0F - corpse.getYRot()) * Mth.DEG_TO_RAD).rotateZ(state.flip * Mth.DEG_TO_RAD));
		}
		flop.placed(placement, baseX, baseY, baseZ, partialTicks, state.tilt, state.move, state.turn, state.lift);
		bodyState.setData(Flop.KEY, new Flop.Frame(flop, partialTicks));
		// (0.5.40) and whatever its limp pose doesn't hang (a vulture's wings and neck, a modded mob's legs) hangs loose,
		// placed as the body's own renderer would stand it (it turns it half round itself)
		bodyState.setData(com.jaymoskitchen.client.ragdoll.LooseParts.KEY, new com.jaymoskitchen.client.ragdoll.LooseParts.Frame(
			com.jaymoskitchen.client.ragdoll.LooseParts.motion(corpse), new Matrix4f(placement).rotateY(-Mth.PI), state.age,
			layout == Flop.Layout.LYING, bodyState instanceof LivingEntityRenderState living ? living.scale : 1.0F));
	}

	/** Where the body is placed (its feet, facing as drawn), relative to the corpse's own spot. */
	private static Matrix4f placement(State state) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(-state.anchorX, -state.anchorY, -state.anchorZ);
		place(state, poseStack);
		Matrix4f placement = new Matrix4f(poseStack.last().pose());
		if (!state.player && state.body instanceof LivingEntityRenderState living) {
			// the body's own renderer then turns it to face its way, and over as it dies
			placement.rotateY((180.0F - living.bodyRot) * Mth.DEG_TO_RAD);
			if (living.deathTime > 0.0F) {
				float fall = Math.min(1.0F, Mth.sqrt((living.deathTime - 1.0F) / 20.0F * 1.6F));
				placement.rotateZ(fall * state.flip * Mth.DEG_TO_RAD);
			}
		}
		return placement;
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
		avatar.setData(Pinned.KEY, null);
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
		if (state.body == null && !state.player) {
			return;
		}
		poseStack.pushPose();
		// ragdoll physics (see Flop), about the corpse's own spot: bounced up, tipped to lie along the ground under it,
		// and swinging from where it was drawn before into where it's placed now
		poseStack.translate(state.anchorX, state.anchorY + state.lift[0], state.anchorZ);
		poseStack.rotate(state.tilt);
		poseStack.translate(state.move.x(), state.move.y(), state.move.z());
		poseStack.rotate(state.turn);
		poseStack.translate(-state.anchorX, -state.anchorY, -state.anchorZ);
		if (state.crumble > 0.0F && !state.player) {
			// falling apart: shuddering harder as it goes, slumping flatter and sinking into the ground
			float c = state.crumble;
			float shake = 0.04F * c;
			poseStack.translate(Mth.sin(state.age * 2.7F) * shake, -0.35F * c * c, Mth.cos(state.age * 3.1F) * shake);
			poseStack.scale(1.0F + 0.15F * c, 1.0F - 0.6F * c * c, 1.0F + 0.15F * c);
		}
		if (state.pose != null) {
			// (0.5.1) onto the ragdoll
			poseStack.mulPose(state.pose.body);
		} else {
			place(state, poseStack);
		}
		if (state.player) {
			if (state.pose != null) {
				// as the player renderer turns a standing model (bodyRot 0)
				poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
			}
			// as players are drawn
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			PlayerModel model = state.avatar.skin.model() == PlayerModelType.SLIM ? slim : wide;
			collector.submitModel(model, state.avatar, poseStack, state.avatar.skin.body().texturePath(), state.lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		} else if (state.bodyRenderer != null) {
			submitBody(state.bodyRenderer, state.body, poseStack, collector, camera);
		}
		poseStack.popPose();
	}

	/**
	 * Places the body (drawn from its feet, as its renderer draws it) as it lies: fallen over, sprawled flat, across
	 * someone's arms, or turning over as it falls from them.
	 */
	private static void place(State state, PoseStack poseStack) {
		if (state.pinned) {
			// upright against the wall, slumped a little forward about its own left-right axis (pivoting at the feet)
			float yaw = state.yaw * Mth.DEG_TO_RAD;
			poseStack.rotate(new Quaternionf().rotationAxis(-PIN_SLUMP * Mth.DEG_TO_RAD, -Mth.cos(yaw), 0.0F, -Mth.sin(yaw)));
			if (state.player) {
				poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yaw));
			}
			return;
		}
		if (state.tumbling) {
			// falling: it turns over about its middle, end over end and side to side, as fast as it's spinning
			float yaw = state.carryYaw * Mth.DEG_TO_RAD;
			poseStack.translate(0.0F, 0.3F, 0.0F);
			// end over end about the body's own crosswise axis, then rolling about its length
			poseStack.rotate(new Quaternionf().rotationAxis(state.tumblePitch * Mth.DEG_TO_RAD, Mth.cos(yaw), 0.0F, Mth.sin(yaw)));
			poseStack.rotate(new Quaternionf().rotationAxis(state.tumbleRoll * Mth.DEG_TO_RAD, -Mth.sin(yaw), 0.0F, Mth.cos(yaw)));
			poseStack.translate(0.0F, -0.3F, 0.0F);
		}
		if (state.player) {
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
		} else if (state.carried) {
			layAcrossArms(state, poseStack);
		} else if (state.sprawled) {
			// tipped flat about its side-to-side axis, face up or down, its middle over the corpse
			float radians = state.yaw * Mth.DEG_TO_RAD;
			poseStack.translate(0.0F, 2.0F / 16.0F, 0.0F);
			poseStack.rotate(new Quaternionf().rotationAxis(state.faceUp ? -Mth.HALF_PI : Mth.HALF_PI, Mth.cos(radians), 0.0F,
				Mth.sin(radians)));
			poseStack.translate(0.0F, -state.bodyHeight / 2.0F, 0.0F);
		}
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

	/** (0.5.23) How many arrows to draw in the body and where (null for none). */
	private static int @Nullable [] arrows(CorpseEntity corpse) {
		int total = com.jaymoskitchen.archery.StuckArrows.total(corpse.arrows());
		return total > 0 ? new int[] {total, corpse.getId()} : null;
	}
}
