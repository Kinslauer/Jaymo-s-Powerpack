package com.jaymospowers.client;

import com.jaymospowers.client.armory.LashRenderer;
import com.jaymospowers.client.wounds.DownedPose;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;

/**
 * (0.16.16) Touch'n Motion (GPL-3.0, shipped unchanged, never compiled against) lays its own motion over the player's
 * body as Entity Model Features animates it: the stance, the foot grounding, the reaches. It hands that pose on only to
 * the armour EMF copies the body's pose onto, and never to models drawn over the body any other way. Andy (2026-10-25):
 * "iron armor leggings are not animated on my character", and the same for Kingdoms' paladin legs and costumes; all of
 * them move again without Touch'n Motion. So every humanoid model drawn on a player (armour, Armory pieces, costumes,
 * MCA's layers) takes the body's pose, just before the model is drawn. Only the pose: what's shown, hidden or scaled
 * stays the model's own.
 * <p>
 * (0.17.40) The pose is our own copy of the body, taken this frame as the renderer turns to its layers ({@link #keep}),
 * no longer Touch'n Motion's {@code Drawn} copy. Andy (2026-10-27): his villager's arms and legs never moved in F5. That
 * copy is only kept from the player model EMF calls the main one, the one the game drew. MCA swaps the renderer's model
 * for its own animated body and never draws it (its skin, clothes and hair layers are what show), so the copy was the
 * pose of the plain body from the moment before MCA's look arrived, and it was stamped on MCA's layers every frame
 * after.
 * <p>
 * Not while one of our poses is on (EMF and Touch'n Motion are paused for those, and our poses are laid on each model
 * themselves), nor on a downed body, a whip hand, or your own body in first person, which Touch'n Motion leaves alone.
 * <p>
 * (0.19.28) Nor on a player changed into an alien: the alien's body (and its Ultimate spikes) has its own build and
 * gait, and stamping the player's part positions on it pulled it apart. Andy (2026-11-01): Duplex's "legs are
 * disconnected", the golem's body and arms lifted to a player's shoulders over its short legs.
 */
public final class TnmFollow {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("touch_n_motion");
	private static final int PARTS = 6;
	/** The renderer's own body model for this player: Touch'n Motion's pose is already on it, with our tweaks after. */
	private static final RenderStateDataKey<Model<?>> BODY = RenderStateDataKey.create(() -> "jaymospowers:tnm_body");
	/** The body's pose this frame: x, y, z, xRot, yRot, zRot for head, body, arms and legs. */
	private static final RenderStateDataKey<float[]> POSE = RenderStateDataKey.create(() -> "jaymospowers:tnm_pose");

	private TnmFollow() {
	}

	private static ModelPart[] parts(HumanoidModel<?> model) {
		return new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
	}

	/** From LivingEntityRenderer.submit, as it turns to the layers: which model is the body, and the pose it holds. */
	public static void keep(LivingEntityRenderState state, Model<?> model) {
		if (!LOADED || !(state instanceof AvatarRenderState)) {
			return;
		}
		state.setData(BODY, model);
		if (!(model instanceof HumanoidModel<?> humanoid)) {
			state.setData(POSE, null);
			return;
		}
		ModelPart[] parts = parts(humanoid);
		float[] pose = new float[PARTS * 6];
		for (int i = 0; i < PARTS; i++) {
			ModelPart part = parts[i];
			pose[i * 6] = part.x;
			pose[i * 6 + 1] = part.y;
			pose[i * 6 + 2] = part.z;
			pose[i * 6 + 3] = part.xRot;
			pose[i * 6 + 4] = part.yRot;
			pose[i * 6 + 5] = part.zRot;
		}
		state.setData(POSE, pose);
	}

	/** From ModelFeatureRenderer.prepareModel, after every pose and just before the model is drawn. */
	public static void beforeDraw(Model<?> model, Object state) {
		if (!LOADED || !(model instanceof HumanoidModel<?> humanoid) || !(state instanceof AvatarRenderState avatar) || avatar.deathTime > 0.0F
			|| model == avatar.getData(BODY) || PoseTakeover.posed(avatar) || DownedPose.down(avatar)
			|| avatar.getData(com.jaymospowers.client.power.xenoform.XenoformClient.FORM) != null
			|| com.jaymospowers.client.wounds.TrippedPose.tripped(avatar)) {
			return;
		}
		float[] pose = avatar.getData(POSE);
		if (pose == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.level.getEntity(avatar.id) instanceof Player player)) {
			return;
		}
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
			|| LashRenderer.arm(player, minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)) != null) {
			return;
		}
		ModelPart[] parts = parts(humanoid);
		for (int i = 0; i < PARTS; i++) {
			ModelPart part = parts[i];
			part.x = pose[i * 6];
			part.y = pose[i * 6 + 1];
			part.z = pose[i * 6 + 2];
			part.xRot = pose[i * 6 + 3];
			part.yRot = pose[i * 6 + 4];
			part.zRot = pose[i * 6 + 5];
		}
	}
}
