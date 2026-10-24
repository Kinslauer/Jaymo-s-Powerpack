package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.client.compat.AutoHudCrosshair;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.148) The crosshair while you fight. Auto HUD clears the crosshair with the rest of the HUD, and over the
 * shoulder (Shoulder Surfing's view sits 0.9 to the right) the middle of the screen isn't where a swing or a shot goes.
 * So whenever you aim or attack (an attack or use key held, a bow, crossbow, trident or gun in hand, an item being
 * drawn, a power key held, a creature in reach under your aim, or a swing in the last {@link #LINGER} ticks) our own
 * crosshair is drawn where the attack will land: on what you'd hit (vanilla's pick, or the giant's camera-line pick,
 * {@link GiantCamera}), else where a shot from your eyes along your look meets a block. Idle, it's gone again in third
 * person, and first person keeps vanilla's usual one. Auto HUD clears it with the rest of the HUD: while you aim it's
 * revealed through Auto HUD's crosshair ({@link AutoHudCrosshair}), which ours hangs under, and when you stop Auto HUD
 * fades both out as usual. The beam battle's film clears it too.
 */
public final class AimCrosshair {
	/** How long it stays after you last aimed or attacked (ticks). */
	static final int LINGER = 40;
	/** How far a shot's line is followed to find where it lands, when nothing in reach is under the aim (blocks). */
	static final double SHOT_RANGE = 96.0;
	private static final Identifier CROSSHAIR = Identifier.withDefaultNamespace("hud/crosshair");
	private static final Identifier INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier INDICATOR_BACK = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

	/** Ticks left to show it. */
	private static int showing;

	private AimCrosshair() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AimCrosshair::tick);
		// vanilla's (centred) crosshair steps aside whenever ours is up, and in third person it's never shown
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, old -> (graphics, delta) -> {
			if (!takesOver(Minecraft.getInstance())) {
				old.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("aim_crosshair"), AimCrosshair::render);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		if (aiming(minecraft, player)) {
			showing = LINGER;
		} else if (showing > 0) {
			showing--;
		}
		if (showing > 0) {
			AutoHudCrosshair.reveal();
		}
	}

	/** Whether you're aiming or fighting right now. */
	private static boolean aiming(Minecraft minecraft, LocalPlayer player) {
		if (minecraft.gui.screen() != null) {
			return false;
		}
		if (player.isSwinging() || minecraft.options.keyAttack.isDown()) {
			return true;
		}
		boolean eating = player.isUsingItem() && (player.getUseItem().getUseAnimation() == ItemUseAnimation.EAT
			|| player.getUseItem().getUseAnimation() == ItemUseAnimation.DRINK);
		if (player.isUsingItem() && !eating || minecraft.options.keyUse.isDown() && !eating) {
			return true;
		}
		if (ranged(player.getMainHandItem()) || ranged(player.getOffhandItem())) {
			return true;
		}
		for (PowerKey key : PowerKey.values()) {
			if (JaymosPowersClient.keyMapping(key).isDown()) {
				return true;
			}
		}
		if (JaymosPowersClient.ultimateKey().isDown()) {
			return true;
		}
		// something alive in reach under the aim: you can see what a swing would hit before you swing
		return minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living && living.isAlive();
	}

	/** A bow, crossbow, trident or gun: its shot goes far past melee reach. */
	private static boolean ranged(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.TRIDENT) || TaczGuns.isModern(stack));
	}

	/** Whether ours is drawn this frame instead of vanilla's. */
	private static boolean takesOver(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || minecraft.getCameraEntity() != player) {
			return false;
		}
		if (minecraft.options.getCameraType().isFirstPerson()) {
			// a scope, or a gun's sights, bring their own
			return showing > 0 && !player.isScoping() && !TaczGuns.isModern(player.getItemInHand(InteractionHand.MAIN_HAND));
		}
		return true;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || showing <= 0 || BeamCinematic.running() || !takesOver(minecraft)) {
			return;
		}
		int x = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2;
		if (!GiantCamera.active(minecraft) && !minecraft.options.getCameraType().isFirstPerson()) {
			// the giant's pick runs along the camera's line, so its crosshair stays in the middle
			double[] at = project(graphics, minecraft, landing(minecraft, player, delta.getGameTimeDeltaPartialTick(false)));
			if (at == null) {
				return;
			}
			x = (int)Math.round(at[0]);
			y = (int)Math.round(at[1]);
		}
		graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR, x - 7, y - 7, 15, 15);
		if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
			float charge = player.getAttackStrengthScale(0.0F);
			int left = x - 8, top = y - 7 + 16;
			boolean full = charge >= 1.0F && minecraft.crosshairPickEntity instanceof LivingEntity target && target.isAlive()
				&& player.getCurrentItemAttackStrengthDelay() > 5.0F;
			if (full) {
				graphics.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_FULL, left, top, 16, 16);
			} else if (charge < 1.0F) {
				graphics.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_BACK, left, top, 16, 4);
				graphics.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_PROGRESS, 16, 4, 0, 0, left, top, (int)(charge * 17.0F), 4);
			}
		}
	}

	/**
	 * Where an attack would land: on what's picked (in reach, from your eyes, or the giant's camera line), else where a
	 * line from your eyes along your look first meets a block, out to {@link #SHOT_RANGE}.
	 */
	private static Vec3 landing(Minecraft minecraft, LocalPlayer player, float partial) {
		HitResult hit = minecraft.hitResult;
		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			return hit.getLocation();
		}
		Vec3 eyes = player.getEyePosition(partial);
		Vec3 far = eyes.add(player.getViewVector(partial).scale(SHOT_RANGE));
		return player.level().clip(new ClipContext(eyes, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
	}

	/** Where a point in the world falls on the screen ({x, y}), or null if it's behind the camera or off the screen. */
	private static double @Nullable [] project(GuiGraphicsExtractor graphics, Minecraft minecraft, Vec3 world) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 rel = world.subtract(camera.position());
		Vector3fc forward = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
		double z = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		if (z < 0.2) {
			return null;
		}
		double lx = rel.x * left.x() + rel.y * left.y() + rel.z * left.z();
		double uy = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
		float fov = camera.getFov() > 1.0F ? camera.getFov() : minecraft.options.fov().get();
		double scale = graphics.guiHeight() * 0.5 / Math.tan(Math.toRadians(fov) * 0.5);
		double sx = graphics.guiWidth() * 0.5 - lx / z * scale;
		double sy = graphics.guiHeight() * 0.5 - uy / z * scale;
		if (sx < 0 || sy < 0 || sx > graphics.guiWidth() || sy > graphics.guiHeight()) {
			return null;
		}
		return new double[] {sx, sy};
	}
}
