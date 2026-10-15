package com.jaymospowers.client;

import com.jaymoskitchen.client.corpse.CorpseRenderer;
import com.jaymospowers.core.SeveredPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.storage.TagValueInput;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A severed head or limb: the body it came off is drawn (by its own renderer, or a player's model in their skin) with
 * only that part showing (see {@link Dismembered#ONLY}), moved so the part's middle sits on this entity and turned
 * over as it tumbles. Not drawn for the player looking out from it.
 */
public final class SeveredPartRenderer extends EntityRenderer<SeveredPart, SeveredPartRenderer.State> {
	public static final class State extends EntityRenderState {
		@Nullable EntityRenderState body;
		@Nullable EntityRenderer<?, ?> bodyRenderer;
		boolean player;
		boolean hidden;
		float yaw;
		float spin;
		float spinAxis;
		/** Where the part's middle is, from the body's feet, as the body is drawn (blocks). */
		final Vector3f centre = new Vector3f();
		final AvatarRenderState avatar = new AvatarRenderState();
	}

	/** Severed part → the body it's drawn from (client only, never in the world). */
	private static final Map<SeveredPart, Entity> BODIES = new WeakHashMap<>();
	private final PlayerModel wide;
	private final PlayerModel slim;

	public SeveredPartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.2F;
		// (0.11.39) Kitchen's player corpse layers, not the vanilla player ones: Fresh Animations would animate a dead
		// player's lost arm or head as if it were still on them
		this.wide = new PlayerModel(context.bakeLayer(CorpseRenderer.PLAYER_BODY), false);
		this.slim = new PlayerModel(context.bakeLayer(CorpseRenderer.PLAYER_BODY_SLIM), true);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SeveredPart entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.displayFireAnimation = false;
		state.hidden = entity == Minecraft.getInstance().getCameraEntity();
		state.yaw = entity.getYRot();
		state.spin = Mth.lerp(partialTicks, entity.spinO, entity.spin);
		state.spinAxis = entity.spinAxis;
		state.body = null;
		state.bodyRenderer = null;
		int part = entity.part();
		String first = Dismembered.names(part)[0];
		state.player = entity.owner().isPresent() || entity.look().contains("\"minecraft:player\"");
		if (state.player) {
			AvatarRenderState avatar = state.avatar;
			avatar.skin = skin(entity.owner(), entity.look());
			avatar.showCape = false;
			avatar.lightCoords = state.lightCoords;
			avatar.outlineColor = state.outlineColor;
			avatar.walkAnimationPos = 0.0F;
			avatar.walkAnimationSpeed = 0.0F;
			avatar.yRot = 0.0F;
			avatar.xRot = 0.0F;
			avatar.nameTag = null;
			avatar.setData(Dismembered.ONLY, part);
			PlayerModel model = avatar.skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
			// a player is drawn flipped and lifted (see submit): the part's middle, as it ends up
			Vector3f mid = middle(model.root(), first);
			state.centre.set(-mid.x / 16.0F, 1.501F - mid.y / 16.0F, mid.z / 16.0F).mul(0.9375F);
			return;
		}
		Entity body = body(entity);
		if (body == null) {
			return;
		}
		body.setPos(entity.getX(), entity.getY(), entity.getZ());
		body.xo = entity.xo;
		body.yo = entity.yo;
		body.zo = entity.zo;
		body.xOld = entity.xOld;
		body.yOld = entity.yOld;
		body.zOld = entity.zOld;
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
		EntityRenderState bodyState = renderer.createRenderState(body, partialTicks);
		bodyState.nameTag = null;
		bodyState.displayFireAnimation = false;
		bodyState.setData(Dismembered.ONLY, part);
		float scale = 1.0F;
		if (bodyState instanceof LivingEntityRenderState living) {
			living.hasRedOverlay = false;
			living.deathTime = 0.0F;
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
			living.bodyRot = 0.0F;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			scale = living.scale;
		}
		Vector3f mid = renderer instanceof LivingEntityRenderer<?, ?, ?> living ? middle(living.getModel().root(), first) : null;
		if (mid != null) {
			// drawn turned round (facing 0), flipped and lifted: model units to blocks from its feet
			state.centre.set(mid.x / 16.0F, 1.501F - mid.y / 16.0F, -mid.z / 16.0F).mul(scale);
		} else {
			state.centre.set(0.0F, entity.bodyHeight() * 0.85F, 0.0F);
		}
		state.body = bodyState;
		state.bodyRenderer = renderer;
	}

	/** The middle of a part of the model as it rests (model units), from its pivot and its box. */
	private static @Nullable Vector3f middle(ModelPart root, String name) {
		ModelPart part = Dismembered.find(root, name);
		if (part == null) {
			return null;
		}
		var pose = part.getInitialPose();
		Vector3f mid = new Vector3f(pose.x(), pose.y(), pose.z());
		if (!part.isEmpty()) {
			ModelPart.Cube cube = part.getRandomCube(RandomSource.create(0L));
			mid.add((cube.minX + cube.maxX) / 2.0F, (cube.minY + cube.maxY) / 2.0F, (cube.minZ + cube.maxZ) / 2.0F);
		}
		return mid;
	}

	private static @Nullable Entity body(SeveredPart entity) {
		Entity body = BODIES.get(entity);
		if (body != null) {
			return body;
		}
		CompoundTag tag;
		try {
			tag = TagParser.parseCompoundFully(entity.look());
		} catch (Exception e) {
			return null;
		}
		Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
		Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
		if (type.isEmpty()) {
			return null;
		}
		body = type.get().create(entity.level(), EntitySpawnReason.LOAD);
		if (body == null) {
			return null;
		}
		try {
			body.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.level().registryAccess(), tag));
		} catch (RuntimeException e) {
			// drawn as a fresh one of its kind
		}
		// never in the world, so no id of its own (negative: can't clash)
		body.setId(-2000 - entity.getId());
		body.setYRot(0.0F);
		body.yRotO = 0.0F;
		body.setXRot(0.0F);
		if (body instanceof LivingEntity living) {
			living.setYBodyRot(0.0F);
			living.yBodyRotO = 0.0F;
			living.setYHeadRot(0.0F);
			living.yHeadRotO = 0.0F;
		}
		BODIES.put(entity, body);
		return body;
	}

	private static PlayerSkin skin(Optional<UUID> owner, String look) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (owner.isPresent()) {
			PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner.get());
			return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner.get());
		}
		return DefaultPlayerSkin.getDefaultSkin();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		if (state.hidden || !state.player && (state.body == null || state.bodyRenderer == null)) {
			return;
		}
		poseStack.pushPose();
		// the part's middle on this entity's (lifted off the ground a little), turned its way and tumbling
		poseStack.translate(0.0F, 0.17F, 0.0F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		float axis = state.spinAxis * Mth.DEG_TO_RAD;
		poseStack.rotate(new Quaternionf().rotationAxis(state.spin * Mth.DEG_TO_RAD, Mth.cos(axis), 0.0F, Mth.sin(axis)));
		poseStack.translate(-state.centre.x, -state.centre.y, -state.centre.z);
		if (state.player) {
			poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			PlayerModel model = state.avatar.skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
			collector.submitModel(model, state.avatar, poseStack, state.avatar.skin.body().texturePath(), state.lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		} else {
			submitBody(state.bodyRenderer, state.body, poseStack, collector, camera);
		}
		poseStack.popPose();
	}

	@SuppressWarnings("unchecked")
	private static <S extends EntityRenderState> void submitBody(EntityRenderer<?, S> renderer, EntityRenderState state, PoseStack poseStack,
		SubmitNodeCollector collector, CameraRenderState camera) {
		renderer.submit((S) state, poseStack, collector, camera);
	}
}
