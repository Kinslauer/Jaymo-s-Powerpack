package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebBehemoth;
import com.jaymospowers.power.web.WebInhabit;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A humanoid the symbiote has taken fights in the strain's own body (Andy, 2026-11-24: "when the symbiotes affect non web
 * humanoids they will use these forms for combat"): a villager, a zombie, a skeleton, an illager, a witch or a piglin it
 * rides (WebInhabit), or a risen body it has made a behemoth (WebBehemoth), is no longer drawn as itself under a coat of goo
 * but as the symbiote's body: the black (and the white) strain's big body with its head ({@link WebHulk},
 * {@link WebBigHead}), the red strain's slender one ({@link WebSlender}). The body is posed by the same code as a web
 * slinger's, off a player-shaped render state made for the mob each frame ({@link #extract}: its walk, its look, its swing,
 * its strain, the big body up), so it walks, breathes, snarls and flicks its tongue the same way. The mob's own model is
 * hidden under it; its back tendrils are still WebBehemothClient's.
 */
public final class WebHostBody {
	/** On a taken humanoid's render state: the player-shaped state its body is drawn from. */
	public static final RenderStateDataKey<AvatarRenderState> BODY = RenderStateDataKey.create(() -> "jaymospowers:web_host_body");
	/** Each taken humanoid's own body state (kept, not made anew each frame). */
	private static final Map<Entity, AvatarRenderState> STATES = new WeakHashMap<>();

	private static WebHulk.Model hulk;
	private static WebBigHead.HeadModel head;
	private static WebSlender.Model slender;

	private WebHostBody() {
	}

	/** A humanoid the symbiote can ride (WebInhabit#takeable's two-legged ones). */
	static boolean humanoid(Entity entity) {
		return entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
			|| entity instanceof net.minecraft.world.entity.monster.zombie.Zombie || entity instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
			|| entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager || entity instanceof net.minecraft.world.entity.monster.Witch
			|| entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin;
	}

	/**
	 * From LivingEntityRenderer.extractRenderState (after the game's own): a taken humanoid gets its body's state and its own
	 * model is hidden (the game draws an invisible entity's model only for those who can see through it, and nobody can).
	 */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		boolean taken = entity.isAlive() && (entity.hasAttached(WebBehemoth.HOST) || WebInhabit.isHost(entity) && humanoid(entity));
		if (!taken || state instanceof AvatarRenderState) {
			STATES.remove(entity);
			state.setData(BODY, null);
			return;
		}
		AvatarRenderState body = STATES.computeIfAbsent(entity, e -> new AvatarRenderState());
		body.id = entity.getId();
		body.entityType = state.entityType;
		body.x = state.x;
		body.y = state.y;
		body.z = state.z;
		body.ageInTicks = state.ageInTicks;
		body.lightCoords = state.lightCoords;
		body.outlineColor = state.outlineColor;
		body.isInvisible = false;
		body.isDiscrete = state.isDiscrete;
		body.boundingBoxWidth = state.boundingBoxWidth;
		body.boundingBoxHeight = state.boundingBoxHeight;
		body.bodyRot = state.bodyRot;
		body.yRot = state.yRot;
		body.xRot = state.xRot;
		body.deathTime = state.deathTime;
		body.walkAnimationPos = state.walkAnimationPos;
		body.walkAnimationSpeed = state.walkAnimationSpeed;
		body.scale = state.scale;
		body.ageScale = state.ageScale;
		body.isInWater = state.isInWater;
		body.hasRedOverlay = state.hasRedOverlay;
		body.pose = state.pose;
		if (state instanceof ArmedEntityRenderState armed) {
			body.mainArm = armed.mainArm;
			body.rightArmPose = armed.rightArmPose;
			body.leftArmPose = armed.leftArmPose;
			body.swingAnimation = armed.swingAnimation;
		}
		if (state instanceof HumanoidRenderState humanoid) {
			body.isCrouching = humanoid.isCrouching;
			body.swimAmount = humanoid.swimAmount;
			body.isPassenger = humanoid.isPassenger;
		}
		// its strain, and the big body up (worn alone, its tendrils WebBehemothClient's)
		body.setData(WebStrainLook.STRAIN, state.getData(WebStrainLook.STRAIN));
		body.setData(WebSymbioteClient.TENDRILS_IN, Boolean.TRUE);
		state.setData(BODY, body);
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
	}

	/** Draws a taken humanoid's body (from WebBehemothClient's layer, in the mob's model space). */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState body) {
		if (hulk == null) {
			hulk = new WebHulk.Model();
			head = new WebBigHead.HeadModel();
			slender = new WebSlender.Model();
			WebSuitGrowth.register(hulk, WebSuitGrowth.Role.HULK);
			WebSuitGrowth.register(slender, WebSuitGrowth.Role.SLENDER);
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(body, 0.0F);
		if (WebSuitGrowth.slender(body)) {
			collector.order(1).submitModel(slender, body, poseStack, RenderTypes.entityCutout(WebSlender.TEXTURE), lightCoords, overlay, body.outlineColor);
			collector.order(2).submitModel(slender, body, poseStack, RenderTypes.eyes(WebSlender.GLOW), LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, body.outlineColor);
			return;
		}
		collector.order(1).submitModel(hulk, body, poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebHulk.TEXTURE, body)), lightCoords, overlay,
			body.outlineColor);
		// the head goes on the big body's neck: pose it now (it's posed again when it's drawn)
		hulk.setupAnim(body);
		WebSuitGrowth.applyModel(hulk, body);
		WebBigHead.submit(poseStack, collector, lightCoords, body, hulk, head);
	}
}
