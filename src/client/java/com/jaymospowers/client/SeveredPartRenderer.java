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
	/** How far each limb's parents move it (model units), worked out once per model part. */
	private static final java.util.Map<ModelPart, Vector3f> HUNG_FROM = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
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
		// (0.11.79) a player's part comes off the villager MCA draws them as, when there is one (as their corpse does)
		Entity look = state.player ? com.jaymoskitchen.client.corpse.McaLooks.of(entity.owner()) : null;
		if (state.player && look == null) {
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
		state.player = false;
		Entity body = look != null ? look : body(entity);
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
		if (look != null) {
			// MCA's villager for the player isn't in the world: lit where the part lies
			bodyState.lightCoords = state.lightCoords;
		}
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
		// the first of the part's names this model has (a creature's arm isn't called "right_arm")
		ModelPart root = renderer instanceof LivingEntityRenderer<?, ?, ?> living ? living.getModel().root() : null;
		ModelPart own = root != null ? Dismembered.limb(root, part) : null;
		Vector3f mid = own != null ? middle(root, own) : null;
		Float geoHeight = mid == null ? geoHeight(renderer, bodyState, part) : null;
		if (mid != null) {
			// drawn turned round (facing 0), flipped and lifted: model units to blocks from its feet
			state.centre.set(mid.x / 16.0F, 1.501F - mid.y / 16.0F, -mid.z / 16.0F).mul(scale);
		} else if (geoHeight != null) {
			// a GeckoLib mob's bone (pivots measured up from its feet): at that height, on its middle line
			state.centre.set(0.0F, geoHeight / 16.0F * scale, 0.0F);
		} else {
			state.centre.set(0.0F, entity.bodyHeight() * 0.85F, 0.0F);
		}
		state.body = bodyState;
		state.bodyRenderer = renderer;
	}

	/**
	 * How high up a GeckoLib mob's (an Imp's) bone for {@code part} sits as it rests (model units from its feet): halfway
	 * from the bone's pivot (a shoulder, a hip, the neck) to its first child's (an elbow, a knee), or a head's pivot a few
	 * units up. Null for any other renderer, or a model without that bone.
	 */
	private static @Nullable Float geoHeight(EntityRenderer<?, ?> renderer, EntityRenderState bodyState, int part) {
		if (!(renderer instanceof com.geckolib.renderer.GeoEntityRenderer<?, ?> geo) || !((Object)bodyState instanceof com.geckolib.renderer.base.GeoRenderState geoState)) {
			return null;
		}
		try {
			com.geckolib.cache.model.BakedGeoModel model = geo.getGeoModel().getBakedModel(geo.getGeoModel().getModelResource(geoState));
			for (String name : Dismembered.names(part)) {
				var found = model.getBone(name);
				if (found.isPresent()) {
					com.geckolib.cache.model.GeoBone bone = found.get();
					if (part == com.jaymospowers.core.Dismember.HEAD) {
						return bone.pivotY() + 4.0F;
					}
					com.geckolib.cache.model.GeoBone[] children = bone.children();
					return children.length > 0 ? (bone.pivotY() + children[0].pivotY()) / 2.0F : bone.pivotY() - 3.0F;
				}
			}
		} catch (RuntimeException e) {
			// a model that isn't loaded: the middle of the body
		}
		return null;
	}

	/** The middle of a part of the model as it rests (model units), from its pivot and its box. */
	private static @Nullable Vector3f middle(ModelPart root, String name) {
		ModelPart part = Dismembered.find(root, name);
		return part == null ? null : middle(part);
	}

	/**
	 * The middle of {@code part} of the model at {@code root} as it rests (model units): its own middle, moved by the pivots
	 * of the parts it hangs from (a creature's arm hangs off its body; the game's limbs hang off the root).
	 */
	private static Vector3f middle(ModelPart root, ModelPart part) {
		Vector3f mid = middle(part);
		Vector3f up = HUNG_FROM.computeIfAbsent(part, p -> {
			Vector3f sum = new Vector3f();
			for (ModelPart above : root.getAllParts()) {
				if (above != p && above.getAllParts().contains(p)) {
					var pose = above.getInitialPose();
					sum.add(pose.x(), pose.y(), pose.z());
				}
			}
			return sum;
		});
		return mid.add(up);
	}

	/** The middle of {@code part} as it rests (model units), from its pivot and its box. */
	private static Vector3f middle(ModelPart part) {
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
