package com.jaymospowers.client;

import com.jaymospowers.client.armory.LashRenderer;
import com.jaymospowers.client.wounds.DownedPose;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.16.16) Touch'n Motion (GPL-3.0, shipped unchanged, never compiled against) lays its own motion over the player's
 * body as Entity Model Features animates it: the stance, the foot grounding, the reaches. It hands that pose on only to
 * the armour EMF copies the body's pose onto, and never to models drawn over the body any other way. Andy (2026-10-25):
 * "iron armor leggings are not animated on my character", and the same for Kingdoms' paladin legs and costumes; all of
 * them move again without Touch'n Motion. So every humanoid model drawn on a player (armour, Armory pieces, costumes,
 * MCA's layers) takes the body's pose as Touch'n Motion last left it, through its own copy (its {@code Drawn.put}),
 * just before the model is drawn. Only the pose: what's shown, hidden or scaled stays the model's own.
 * <p>
 * Not while one of our poses is on (EMF and Touch'n Motion are paused for those, and our poses are laid on each model
 * themselves), nor on a downed body, a whip hand, or your own body in first person, which Touch'n Motion leaves alone.
 */
public final class TnmFollow {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("touch_n_motion");
	private static final int PARTS = 6;
	/** The renderer's own body model for this player: Touch'n Motion's pose is already on it, with our tweaks after. */
	private static final RenderStateDataKey<Model<?>> BODY = RenderStateDataKey.create(() -> "jaymospowers:tnm_body");
	private static MethodHandle put;
	private static boolean looked;

	private TnmFollow() {
	}

	/** From LivingEntityRenderer.submit: which model is the body itself. */
	public static void body(LivingEntityRenderState state, Model<?> model) {
		if (LOADED && state instanceof AvatarRenderState) {
			state.setData(BODY, model);
		}
	}

	/** From ModelFeatureRenderer.prepareModel, after every pose and just before the model is drawn. */
	public static void beforeDraw(Model<?> model, Object state) {
		if (!LOADED || !(model instanceof HumanoidModel<?> humanoid) || !(state instanceof AvatarRenderState avatar) || avatar.deathTime > 0.0F
			|| model == avatar.getData(BODY) || PoseTakeover.posed(avatar) || DownedPose.down(avatar)) {
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
		MethodHandle handle = handle();
		if (handle == null) {
			return;
		}
		ModelPart[] parts = {humanoid.head, humanoid.body, humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg};
		boolean[] visible = new boolean[PARTS];
		boolean[] skipDraw = new boolean[PARTS];
		float[] scales = new float[PARTS * 3];
		for (int i = 0; i < PARTS; i++) {
			visible[i] = parts[i].visible;
			skipDraw[i] = parts[i].skipDraw;
			scales[i * 3] = parts[i].xScale;
			scales[i * 3 + 1] = parts[i].yScale;
			scales[i * 3 + 2] = parts[i].zScale;
		}
		try {
			handle.invoke(((Entity)player).getUUID(), humanoid);
		} catch (Throwable e) {
			put = null;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't hand Touch'n Motion's pose to the armour and costumes", e);
		}
		for (int i = 0; i < PARTS; i++) {
			parts[i].visible = visible[i];
			parts[i].skipDraw = skipDraw[i];
			parts[i].xScale = scales[i * 3];
			parts[i].yScale = scales[i * 3 + 1];
			parts[i].zScale = scales[i * 3 + 2];
		}
	}

	/** Touch'n Motion's {@code TouchNMotionHook.Drawn.put(UUID, HumanoidModel)}: the body's last pose onto a model. */
	private static MethodHandle handle() {
		if (!looked) {
			looked = true;
			try {
				Method method = Class.forName("strm.touchnmotion.TouchNMotionHook$Drawn").getDeclaredMethod("put", UUID.class, HumanoidModel.class);
				method.setAccessible(true);
				put = MethodHandles.lookup().unreflect(method);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				com.jaymospowers.JaymosPowers.LOGGER.warn("Touch'n Motion has no pose to hand to the armour and costumes; they keep the body's plain pose", e);
			}
		}
		return put;
	}
}
