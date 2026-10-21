package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import com.jaymoskingdoms.dragons.DragonBreath;
import com.jaymoskingdoms.dragons.Dragons;
import com.jaymoskingdoms.dragons.NotusDragon;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.135) The dragons on the client ({@link Dragons}): Notus's renderer; flying a ridden dragon (the rider's client
 * moves it, as with the bat horse's flight in {@code MythicMountsClient}, with the free-rein camera); the breath key; and
 * the HUD (bottom right, right of the hotbar, in Equestrian's place and style): the dragon's stamina while you ride it,
 * and its trust while you're winning it over (only you see it).
 * <p>
 * Flight: Space takes off from the ground and climbs; W flies where you look (the camera under free rein) and keeps its
 * momentum; let go and it glides, sinking slowly; S brakes to a hover; Shift dives (in the air it doesn't dismount,
 * {@code DragonDiveInputMixin}; on the ground it does). An exhausted dragon can only glide down.
 * <p>
 * The breath key is R by default, the same as Jaymo's Powers' first ability key: it's polled (not clicked) so it works
 * whichever mapping the game hands the key to; with a power, R in the saddle also fires the ability (rebind one of
 * them to keep them apart).
 */
public final class DragonsClient {
	/** Climb per tick holding Space, the fastest climb; the hop that takes it off the ground. */
	static final double LIFT = 0.07, MAX_CLIMB = 0.55, TAKE_OFF = 0.75;
	/** Thrust per tick holding W (along the look), the top speed, the top speed diving. */
	static final double THRUST = 0.06, CRUISE = 1.0, MAX_DIVE_SPEED = 1.6;
	/** Gliding: how fast it sinks and the most it sinks; how much way it loses a tick without W. */
	static final double SINK = 0.02, MAX_SINK = 0.3, GLIDE_DRAG = 0.992;
	/** S: the brake (share of speed kept a tick) and the hover's damping of any rise or fall. */
	static final double BRAKE = 0.88, HOVER_DAMP = 0.8;
	/** Shift: how hard it dives and the fastest fall. */
	static final double DIVE = 0.09, MAX_DIVE = 1.4;
	/** Exhausted, it can't keep height: it sinks at least this fast. */
	static final double EXHAUSTED_SINK = 0.15;
	/**
	 * Vanilla's air drag on a mount's motion each tick (horizontal, vertical): the flight undoes it, so the dragon keeps
	 * the momentum it's given.
	 */
	static final double AIR_DRAG = 0.91, AIR_DRAG_Y = 0.98;
	/** The trust meter shows for a dragon this near. */
	static final double TRUST_RANGE = 24.0;
	static final int BAR_WIDTH = 64, CHIP = 20;
	static final int STAMINA_COLOR = 0xFF9A3A, LOW_COLOR = 0xFFD45A, EXHAUSTED_COLOR = 0xE04040, TRUST_COLOR = 0xFFC86A;

	private static @Nullable KeyMapping breathKey;

	private DragonsClient() {
	}

	public static void register() {
		EntityRenderers.register(Dragons.NOTUS_DRAGON, NotusDragonRenderer::new);
		breathKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.dragon_breath", InputConstants.KEY_R,
			KeyMapping.Category.register(JaymosKingdoms.id("dragons"))));
		ClientTickEvents.END_CLIENT_TICK.register(DragonsClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("dragon"), DragonsClient::hud);
	}

	/** The dragon {@code player} rides and steers. */
	public static @Nullable NotusDragon steered(@Nullable Player player) {
		return player != null && player.getVehicle() instanceof NotusDragon dragon && dragon.getControllingPassenger() == player ? dragon : null;
	}

	private static boolean breathHeld(Minecraft minecraft) {
		if (breathKey == null || minecraft.gui.screen() != null) {
			return false;
		}
		if (breathKey.isDown()) {
			return true;
		}
		// (polled: the key may be shared with another mod's mapping, which then gets its presses)
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(breathKey);
		return key.getType() == InputConstants.Type.KEYBOARD && key.getValue() != InputConstants.UNKNOWN.getValue()
			&& InputConstants.isKeyDown(key.getValue());
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		NotusDragon dragon = steered(player);
		if (dragon == null) {
			return;
		}
		float yaw = FreeReinClient.on() ? FreeReinClient.yaw() : player.getYRot();
		float pitch = FreeReinClient.on() ? FreeReinClient.pitch() : player.getXRot();
		if (dragon.isTamed() && breathHeld(minecraft) && player.tickCount % 2 == 0 && ClientPlayNetworking.canSend(Dragons.Breathe.TYPE)) {
			ClientPlayNetworking.send(new Dragons.Breathe(yaw, pitch));
		}
		if (dragon.isNoGravity()) {
			fly(minecraft, dragon, yaw, pitch);
		}
	}

	private static void fly(Minecraft minecraft, NotusDragon dragon, float yaw, float pitch) {
		boolean tired = dragon.exhausted();
		boolean up = minecraft.options.keyJump.isDown() && !tired;
		boolean forward = minecraft.options.keyUp.isDown();
		boolean brake = minecraft.options.keyDown.isDown();
		boolean dive = minecraft.options.keyShift.isDown();
		Vec3 m = dragon.getDeltaMovement();
		if (dragon.onGround()) {
			if (up) {
				dragon.setDeltaMovement(m.x, TAKE_OFF, m.z);
			}
			return;
		}
		// (undo the drag the mount's own movement put on it this tick: momentum)
		double x = m.x / AIR_DRAG, y = m.y / AIR_DRAG_Y, z = m.z / AIR_DRAG;
		if (forward && !tired) {
			Vec3 look = DragonBreath.aim(yaw, pitch);
			x += look.x * THRUST;
			y += look.y * THRUST;
			z += look.z * THRUST;
		} else {
			x *= GLIDE_DRAG;
			z *= GLIDE_DRAG;
		}
		if (up) {
			y = Math.min(y + LIFT, MAX_CLIMB);
		}
		if (brake) {
			x *= BRAKE;
			z *= BRAKE;
			y *= HOVER_DAMP;
		} else if (dive) {
			y = Math.max(y - DIVE, -MAX_DIVE);
		} else if (!up && !forward) {
			y = Math.max(y - SINK, -MAX_SINK);
		}
		if (tired) {
			y = Math.min(y, -EXHAUSTED_SINK);
		}
		double speed = Math.sqrt(x * x + y * y + z * z), top = dive ? MAX_DIVE_SPEED : CRUISE;
		if (speed > top) {
			x *= top / speed;
			y *= top / speed;
			z *= top / speed;
		}
		dragon.setDeltaMovement(x, y, z);
	}

	// ---- the HUD -------------------------------------------------------------------------------------------------------

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.options.hideGui) {
			return;
		}
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		if (player.getVehicle() instanceof NotusDragon riding) {
			float share = riding.stamina() / NotusDragon.STAMINA;
			int color = riding.exhausted() ? pulse(EXHAUSTED_COLOR, 0xFFFFFF, time * 0.5F) : share < NotusDragon.RECOVER / NotusDragon.STAMINA ? LOW_COLOR
				: STAMINA_COLOR;
			MutableComponent label = riding.getDisplayName().copy().withColor(0xB0B0B0);
			if (riding.exhausted()) {
				label.append(Component.literal("  ")).append(Component.translatable("hud.jaymoskingdoms.dragon_exhausted").withColor(EXHAUSTED_COLOR));
			}
			bar(graphics, minecraft.font, share, color, label, NotusDragon.RECOVER / NotusDragon.STAMINA);
			return;
		}
		NotusDragon wooed = null;
		for (NotusDragon dragon : player.level().getEntitiesOfClass(NotusDragon.class, player.getBoundingBox().inflate(TRUST_RANGE),
			d -> !d.isTamed() && d.tamerId() == player.getId() && d.trust() > 0.0F)) {
			wooed = dragon;
		}
		if (wooed != null) {
			float share = wooed.trust() / NotusDragon.TRUST_FULL;
			Component label = Component.translatable("hud.jaymoskingdoms.dragon_trust", wooed.getDisplayName()).withColor(0xB0B0B0);
			bar(graphics, minecraft.font, share, pulse(TRUST_COLOR, 0xFFFFFF, time * 0.15F), label, -1.0F);
		}
	}

	/** A bar in Equestrian's place (right of the hotbar, over the powers' chips), with its label over it. */
	private static void bar(GuiGraphicsExtractor graphics, Font font, float share, int color, Component label, float mark) {
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int cx = width / 2;
		int x0 = cx + 91 + 30;
		int y = height - CHIP - 1 - 6;
		if (x0 + BAR_WIDTH > width - 2) {
			x0 = cx + 91 - BAR_WIDTH;
			y = height - 22 - 30 - CHIP - 6;
		}
		share = Mth.clamp(share, 0.0F, 1.0F);
		graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 3, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(BAR_WIDTH * share), y + 2, 0xFF000000 | color);
		if (mark > 0.0F) {
			int at = x0 + Math.round(BAR_WIDTH * mark);
			graphics.fill(at, y - 1, at + 1, y + 3, 0xFF606060);
		}
		graphics.text(font, label, x0 + BAR_WIDTH - font.width(label), y - 11, 0xE0FFFFFF);
	}

	private static int pulse(int a, int b, float t) {
		float k = (Mth.sin(t) + 1.0F) * 0.5F;
		int r = (int)Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int)Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int)Mth.lerp(k, a & 255, b & 255);
		return r << 16 | g << 8 | bl;
	}
}
