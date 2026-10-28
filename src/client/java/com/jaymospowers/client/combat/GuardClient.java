package com.jaymospowers.client.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.LockOnClient;
import com.jaymospowers.combat.Guard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.67) The guard on this side (see {@link Guard}): the timing bar under the crosshair while a guard is up (white
 * the perfect parry, gold the parry, grey the plain block; a notch runs along it), its flash when a parry lands, and the
 * lock-on lunge: locked onto a foe a little past your reach, a swing with a weapon steps you in so the blow connects
 * (Epic Fight's attack step, our own).
 */
public final class GuardClient {
	/** The bar: half its width and its height (pixels), and how far under the crosshair. */
	static final int BAR_HALF = 13, BAR_HEIGHT = 2, BAR_DROP = 9;
	/** The bar stays this many ticks past the parry window, then goes. */
	static final int BAR_LINGER = 6;
	/** A landed parry's flash (ticks). */
	static final int FLASH_TICKS = 10;
	static final int PERFECT_COLOR = 0xFFFFFFFF, PARRY_COLOR = 0xFFFFC940, BLOCK_COLOR = 0xFF7A7A7A, NOTCH_COLOR = 0xFFE03A2A;
	/** The lunge: a foe at most this far past your reach (blocks), this close inside it still counts as out. */
	static final double LUNGE_RANGE = 3.0, LUNGE_SLACK = 0.4;
	/** Its speed (blocks a tick): this much per block of gap plus one, between these. */
	static final double LUNGE_PER_BLOCK = 0.3, LUNGE_MIN = 0.35, LUNGE_MAX = 0.9;
	static final int LUNGE_COOLDOWN = 12;

	private static boolean attackWasDown;
	private static int lungeWait;
	private static int flash;
	private static boolean flashPerfect;

	private GuardClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Guard.Parried.TYPE, (payload, context) -> {
			flash = FLASH_TICKS;
			flashPerfect = payload.perfect();
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("guard_timing"), GuardClient::bar);
	}

	private static void tick(Minecraft minecraft) {
		if (flash > 0) {
			flash--;
		}
		if (lungeWait > 0) {
			lungeWait--;
		}
		LocalPlayer player = minecraft.player;
		boolean down = minecraft.options.keyAttack.isDown();
		boolean pressed = down && !attackWasDown;
		attackWasDown = down;
		if (pressed && player != null && minecraft.gui.screen() == null && lungeWait <= 0) {
			lunge(player);
		}
	}

	private static void lunge(LocalPlayer player) {
		LivingEntity target = LockOnClient.locked();
		if (target == null || !player.onGround() || player.isUsingItem() || player.isShiftKeyDown() || player.isPassenger()
			|| !player.getMainHandItem().has(DataComponents.WEAPON)) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		double gap = Math.sqrt(target.getBoundingBox().distanceToSqr(eye));
		double reach = player.entityInteractionRange();
		if (gap < reach - LUNGE_SLACK || gap > reach + LUNGE_RANGE) {
			return;
		}
		Vec3 to = target.position().subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return;
		}
		double speed = Mth.clamp((gap - reach + 1.0) * LUNGE_PER_BLOCK, LUNGE_MIN, LUNGE_MAX);
		Vec3 dir = flat.normalize();
		player.setDeltaMovement(dir.x * speed, player.getDeltaMovement().y, dir.z * speed);
		lungeWait = LUNGE_COOLDOWN;
	}

	private static void bar(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int cx = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2 + BAR_DROP;
		if (flash > 0) {
			int alpha = Math.round(255.0F * flash / FLASH_TICKS);
			int color = (alpha << 24) | ((flashPerfect ? PERFECT_COLOR : PARRY_COLOR) & 0xFFFFFF);
			int grow = FLASH_TICKS - flash;
			graphics.fill(cx - BAR_HALF - grow, y - 1, cx + BAR_HALF + grow + 1, y + BAR_HEIGHT + 1, color);
			return;
		}
		if (!Guard.raised(player)) {
			return;
		}
		boolean locked = LockOnClient.locked() != null;
		int perfect = Guard.PERFECT_TICKS + (locked ? Guard.LOCK_PERFECT_BONUS : 0);
		int parry = Guard.PARRY_TICKS + (locked ? Guard.LOCK_PARRY_BONUS : 0);
		float held = player.getTicksUsingItem() + delta.getGameTimeDeltaPartialTick(false);
		if (held > parry + BAR_LINGER) {
			return;
		}
		int span = parry + BAR_LINGER;
		int left = cx - BAR_HALF, width = BAR_HALF * 2;
		int perfectX = left + width * perfect / span, parryX = left + width * parry / span;
		graphics.fill(left - 1, y - 1, left + width + 1, y + BAR_HEIGHT + 1, 0x80000000);
		graphics.fill(left, y, perfectX, y + BAR_HEIGHT, PERFECT_COLOR);
		graphics.fill(perfectX, y, parryX, y + BAR_HEIGHT, PARRY_COLOR);
		graphics.fill(parryX, y, left + width, y + BAR_HEIGHT, BLOCK_COLOR);
		int notch = left + Math.round(width * Math.min(held, span) / span);
		graphics.fill(notch, y - 2, notch + 1, y + BAR_HEIGHT + 2, NOTCH_COLOR);
	}
}
