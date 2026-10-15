package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.corpse.ZombiePlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A zombie player: the dead player's own skin (slim or wide arms, as theirs were), gone grey-blue, with their
 * clothes in tatters over it, shambling with its arms out like any zombie. After the Zombie Players mod.
 *
 * <p>Its render state is deliberately not a player's: the game sends any player-type render state to its own player
 * renderer, which drew these as plain, untouched players.
 */
public class ZombiePlayerRenderer extends LivingEntityRenderer<ZombiePlayer, ZombiePlayerRenderer.State, ZombiePlayerModel> {
	private static final Identifier TATTERS = JaymosKitchen.id("textures/entity/zombie_player_tatters.png");
	/** Multiplied over the skin: dead grey-blue. */
	private static final int DEAD_SKIN = 0xFF93A8AE;

	public static class State extends HumanoidRenderState {
		PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
		/** (0.4.44) The dead player's MCA villager, drawn by MCA's renderer instead of the skin (see McaLooks). */
		@Nullable EntityRenderState look;
		@Nullable EntityRenderer<?, ?> lookRenderer;
	}

	/** (0.4.44) On a zombie player's MCA villager: its arms held out like a zombie's (see ZombiePlayerModel#armsOut). */
	public static final RenderStateDataKey<Boolean> ARMS_OUT = RenderStateDataKey.create(() -> "jaymoskitchen:zombie_arms_out");

	private final ZombiePlayerModel wide;
	private final ZombiePlayerModel slim;

	public ZombiePlayerRenderer(EntityRendererProvider.Context context) {
		// (0.4.42) the player corpse's layers, not the vanilla player ones: Fresh Animations would walk it like a player,
		// arms down, over its zombie arms (see CorpseRenderer#PLAYER_BODY)
		super(context, new ZombiePlayerModel(context.bakeLayer(CorpseRenderer.PLAYER_BODY)), 0.5F);
		this.wide = this.model;
		this.slim = new ZombiePlayerModel(context.bakeLayer(CorpseRenderer.PLAYER_BODY_SLIM));
		addLayer(new ItemInHandLayer<>(this));
		addLayer(new Tatters(this, new ZombiePlayerModel(context.bakeLayer(ZombiePlayerModel.TATTERS)),
			new ZombiePlayerModel(context.bakeLayer(ZombiePlayerModel.TATTERS_SLIM))));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ZombiePlayer zombie, State state, float partialTicks) {
		super.extractRenderState(zombie, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(zombie, state, partialTicks, this.itemModelResolver);
		state.skin = Skins.of(zombie.owner(), zombie.ownerName());
		extractLook(zombie, state, partialTicks);
	}

	/**
	 * (0.4.44) With MCA everyone is a villager, so the zombie is the villager MCA draws the dead player as (genes, hair,
	 * clothes), gone green-grey with the infection, walking and holding its arms out as this zombie does.
	 */
	private void extractLook(ZombiePlayer zombie, State state, float partialTicks) {
		state.look = null;
		state.lookRenderer = null;
		Entity villager = McaLooks.of(zombie.owner());
		if (!(villager instanceof LivingEntity living)) {
			return;
		}
		living.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
		living.xo = zombie.xo;
		living.yo = zombie.yo;
		living.zo = zombie.zo;
		living.xOld = zombie.xOld;
		living.yOld = zombie.yOld;
		living.zOld = zombie.zOld;
		living.setYRot(zombie.getYRot());
		living.yRotO = zombie.yRotO;
		living.setXRot(zombie.getXRot());
		living.xRotO = zombie.xRotO;
		living.setYBodyRot(zombie.yBodyRot);
		living.yBodyRotO = zombie.yBodyRotO;
		living.setYHeadRot(zombie.getYHeadRot());
		living.yHeadRotO = zombie.yHeadRotO;
		living.deathTime = 0;
		living.hurtTime = 0;
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(living);
		EntityRenderState look = McaLooks.infected(living, () -> renderer.createRenderState(living, partialTicks));
		if (!(look instanceof HumanoidRenderState humanoid)) {
			return;
		}
		// moving, turning, hurt, dying, lit and named as the zombie is, holding what it holds
		HumanoidMobRenderer.extractHumanoidRenderState(zombie, humanoid, partialTicks, this.itemModelResolver);
		humanoid.x = state.x;
		humanoid.y = state.y;
		humanoid.z = state.z;
		humanoid.bodyRot = state.bodyRot;
		humanoid.yRot = state.yRot;
		humanoid.xRot = state.xRot;
		humanoid.walkAnimationPos = state.walkAnimationPos;
		humanoid.walkAnimationSpeed = state.walkAnimationSpeed;
		humanoid.ageInTicks = state.ageInTicks;
		humanoid.deathTime = state.deathTime;
		humanoid.hasRedOverlay = state.hasRedOverlay;
		humanoid.isInvisible = state.isInvisible;
		humanoid.isInvisibleToPlayer = state.isInvisibleToPlayer;
		humanoid.lightCoords = state.lightCoords;
		humanoid.outlineColor = state.outlineColor;
		humanoid.displayFireAnimation = state.displayFireAnimation;
		humanoid.nameTag = state.nameTag;
		humanoid.nameTagAttachment = state.nameTagAttachment;
		humanoid.setData(CrawlPose.KEY, state.getData(CrawlPose.KEY));
		humanoid.setData(ARMS_OUT, true);
		state.look = humanoid;
		state.lookRenderer = renderer;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.look != null && state.lookRenderer != null) {
			submitLook(state.lookRenderer, state.look, poseStack, collector, camera);
			return;
		}
		this.model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
		super.submit(state, poseStack, collector, camera);
	}

	@SuppressWarnings("unchecked")
	private static <S extends EntityRenderState> void submitLook(EntityRenderer<?, S> renderer, EntityRenderState state,
		PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		renderer.submit((S) state, poseStack, collector, camera);
	}

	/** (0.4.44) Holds a zombie player's MCA villager's arms out (called as each of its models is posed). */
	public static void armsOut(net.minecraft.client.model.Model<?> model, Object state) {
		if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid && state instanceof HumanoidRenderState living
			&& Boolean.TRUE.equals(living.getData(ARMS_OUT))) {
			ZombiePlayerModel.armsOut(humanoid.rightArm, humanoid.leftArm, living.swingAnimation, living.ageInTicks);
		}
	}

	@Override
	protected boolean shouldShowName(ZombiePlayer zombie, double distanceToCameraSq) {
		// as any mob: only a name-tagged one, looked at (the living-entity default shows every one, as for players)
		return super.shouldShowName(zombie, distanceToCameraSq)
			&& (zombie.shouldShowName() || zombie.hasCustomName() && zombie == this.entityRenderDispatcher.crosshairPickEntity);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected int getModelTint(State state) {
		return DEAD_SKIN;
	}

	/** Torn clothes and grey flesh showing through, drawn just over the skin. */
	private static final class Tatters extends RenderLayer<State, ZombiePlayerModel> {
		private final ZombiePlayerModel wide;
		private final ZombiePlayerModel slim;

		Tatters(RenderLayerParent<State, ZombiePlayerModel> renderer, ZombiePlayerModel wide, ZombiePlayerModel slim) {
			super(renderer);
			this.wide = wide;
			this.slim = slim;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			ZombiePlayerModel model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TATTERS), lightCoords,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, null, state.outlineColor);
		}
	}
}
