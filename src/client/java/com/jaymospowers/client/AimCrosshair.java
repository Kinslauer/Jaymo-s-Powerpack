package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.armory.Armory;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.client.compat.AutoHudCrosshair;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
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
 * <p>
 * (0.19.43, Andy: "it stays the same size so it obscures what you are aiming at", "should never disappear when you are
 * aiming a weapon", "a smaller crosshair when you have a weapon", "changed dependent on the player") It shrinks with
 * the view's zoom (Ok Zoomer, a drawn bow, a spyglass-like FOV drop: the camera's FOV over your FOV setting, down to
 * {@link #MIN_ZOOM_SCALE}). With a weapon in hand ({@link #armed}) it's always up and it's drawn after Auto HUD's
 * crosshair ({@code AimCrosshairHudMixin}), outside Auto HUD's fade, so it can't be faded away; it never hides because
 * the landing point is off the screen either, it falls back to the middle. Holding a weapon it takes the weapon style
 * (a small cross by default), otherwise the hand style (vanilla's). Each player picks their own with
 * {@code /crosshair weapon|hand <style>} and {@code /crosshair zoom on|off}, kept in
 * {@code config/jaymospowers-client.properties}.
 */
public final class AimCrosshair {
	/** How long it stays after you last aimed or attacked (ticks). */
	static final int LINGER = 40;
	/** How far a shot's line is followed to find where it lands, when nothing in reach is under the aim (blocks). */
	static final double SHOT_RANGE = 96.0;
	/** The smallest the crosshair shrinks to while zoomed in (times its size). */
	static final float MIN_ZOOM_SCALE = 0.35F;
	private static final String KEY_WEAPON = "crosshair.weapon", KEY_HAND = "crosshair.hand", KEY_ZOOM = "crosshair.zoom";
	private static final Identifier CROSSHAIR = Identifier.withDefaultNamespace("hud/crosshair");
	private static final Identifier INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier INDICATOR_BACK = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

	/** Ticks left to show it. */
	private static int showing;

	/** The crosshair looks a player can pick. {@code half} is how far it reaches from its middle (gui pixels). */
	public enum Style {
		VANILLA(7), SMALL(3), DOT(0), RING(4), GAP(6);

		final int half;
		final Identifier texture;

		Style(int half) {
			this.half = half;
			this.texture = JaymosPowers.id("textures/gui/crosshair/" + name().toLowerCase(Locale.ROOT) + ".png");
		}

		static Style parse(String name, Style fallback) {
			for (Style style : values()) {
				if (style.name().equalsIgnoreCase(name)) {
					return style;
				}
			}
			return fallback;
		}

		String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static Style weaponStyle() {
		return Style.parse(ZFighterClientConfig.get(KEY_WEAPON, "small"), Style.SMALL);
	}

	private static Style handStyle() {
		return Style.parse(ZFighterClientConfig.get(KEY_HAND, "vanilla"), Style.VANILLA);
	}

	private static boolean zoomShrinks() {
		return !"off".equals(ZFighterClientConfig.get(KEY_ZOOM, "on"));
	}

	private AimCrosshair() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AimCrosshair::tick);
		// vanilla's (centred) crosshair steps aside whenever ours is up, and in third person it's never shown
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, old -> (graphics, delta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (!takesOver(minecraft)) {
				// vanilla's shrinks with the zoom too, around the middle of the screen
				float scale = zoomShrinks() && minecraft.player != null ? zoomScale(minecraft) : 1.0F;
				if (scale >= 1.0F) {
					old.extractRenderState(graphics, delta);
					return;
				}
				float cx = graphics.guiWidth() / 2 + 0.5F, cy = graphics.guiHeight() / 2 + 0.5F;
				graphics.pose().pushMatrix();
				graphics.pose().translate(cx, cy);
				graphics.pose().scale(scale);
				graphics.pose().translate(-cx, -cy);
				old.extractRenderState(graphics, delta);
				graphics.pose().popMatrix();
			}
		});
		// unarmed it hangs under Auto HUD's crosshair and fades with it; armed, AimCrosshairHudMixin draws it outside the fade
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("aim_crosshair"), (graphics, delta) -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null && !armed(player)) {
				render(graphics, delta);
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> weapon = ClientCommands.literal("weapon");
			LiteralArgumentBuilder<FabricClientCommandSource> hand = ClientCommands.literal("hand");
			for (Style style : Style.values()) {
				weapon.then(ClientCommands.literal(style.id()).executes(c -> pick(c.getSource(), KEY_WEAPON, "weapon", style)));
				hand.then(ClientCommands.literal(style.id()).executes(c -> pick(c.getSource(), KEY_HAND, "hand", style)));
			}
			dispatcher.register(ClientCommands.literal("crosshair")
				.executes(c -> report(c.getSource()))
				.then(weapon)
				.then(hand)
				.then(ClientCommands.literal("zoom")
					.then(ClientCommands.literal("on").executes(c -> zoom(c.getSource(), true)))
					.then(ClientCommands.literal("off").executes(c -> zoom(c.getSource(), false)))));
		});
	}

	private static int report(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable("message.jaymospowers.crosshair.now", weaponStyle().id(), handStyle().id(),
			Component.translatable(zoomShrinks() ? "message.jaymospowers.crosshair.zoom_on" : "message.jaymospowers.crosshair.zoom_off")));
		return Command.SINGLE_SUCCESS;
	}

	private static int pick(FabricClientCommandSource source, String key, String which, Style style) {
		ZFighterClientConfig.set(key, style.id());
		source.sendFeedback(Component.translatable("message.jaymospowers.crosshair.set_" + which, style.id()));
		return Command.SINGLE_SUCCESS;
	}

	private static int zoom(FabricClientCommandSource source, boolean on) {
		ZFighterClientConfig.set(KEY_ZOOM, on ? "on" : "off");
		source.sendFeedback(Component.translatable(on ? "message.jaymospowers.crosshair.set_zoom_on" : "message.jaymospowers.crosshair.set_zoom_off"));
		return Command.SINGLE_SUCCESS;
	}

	/** (0.19.43) Called by AimCrosshairHudMixin just after the crosshair layer, outside Auto HUD's fade: the armed crosshair. */
	public static void renderArmed(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && armed(player)) {
			render(graphics, delta);
		}
	}

	/** A weapon in hand: a blade, axe, spear, mace, one of the Armory's, a fighting tool, or anything that shoots. */
	static boolean armed(LocalPlayer player) {
		return weapon(player.getMainHandItem()) || ranged(player.getOffhandItem());
	}

	private static boolean weapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return ranged(stack) || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)
			|| stack.is(ItemTags.WEAPON_ENCHANTABLE) || stack.is(ItemTags.MACE_ENCHANTABLE) || Armory.isWeapon(stack)
			|| stack.has(DataComponents.WEAPON) && !stack.has(DataComponents.TOOL);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		if (aiming(minecraft, player) || armed(player) && minecraft.gui.screen() == null) {
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
			return showing > 0 && !player.isScoping() && !TaczGuns.isModern(player.getItemInHand(InteractionHand.MAIN_HAND))
				&& (armed(player) || handStyle() != Style.VANILLA);
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
			// off the screen or behind the camera: it stays in the middle rather than going away
			double[] at = project(graphics, minecraft, landing(minecraft, player, delta.getGameTimeDeltaPartialTick(false)));
			if (at != null) {
				x = (int)Math.round(at[0]);
				y = (int)Math.round(at[1]);
			}
		}
		Style style = armed(player) ? weaponStyle() : handStyle();
		float scale = zoomShrinks() ? zoomScale(minecraft) : 1.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 0.5F, y + 0.5F);
		graphics.pose().scale(scale);
		if (style == Style.VANILLA) {
			graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR, -7, -7, 15, 15);
		} else {
			graphics.blit(RenderPipelines.CROSSHAIR, style.texture, -7, -7, 0.0F, 0.0F, 15, 15, 15, 15);
		}
		graphics.pose().popMatrix();
		if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
			float charge = player.getAttackStrengthScale(0.0F);
			// vanilla's sits 9 below the middle; a smaller or zoomed-down crosshair pulls it up to stay close
			int left = x - 8, top = y + Math.min(9, Math.round(style.half * scale) + 3);
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

	/** How much the view is zoomed in: the camera's FOV over the FOV setting (1 unzoomed, smaller zoomed in). */
	private static float zoomScale(Minecraft minecraft) {
		float fov = minecraft.gameRenderer.mainCamera().getFov();
		float base = minecraft.options.fov().get();
		if (fov <= 1.0F || base <= 1.0F) {
			return 1.0F;
		}
		return Mth.clamp(fov / base, MIN_ZOOM_SCALE, 1.0F);
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
