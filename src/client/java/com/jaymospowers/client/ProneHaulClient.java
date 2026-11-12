package com.jaymospowers.client;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.ProneHaul;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's prone haul up a block (see {@link ProneHaul} for the rules and the numbers). Lying on the belly
 * against a rise with room on top ({@link ProneHaul#find}), holding Use begins it: from then the haul moves the body
 * (the vanilla travel is skipped, {@code LedgePlayerMixin}) up the face and in across the top along {@link ProneHaul#at},
 * through {@code move()} so the server sees ordinary, collision-free movement. Let go of Use before the end and the grip
 * goes (the server ragdolls you back off the face); keep holding and you end lying on top.
 *
 * <p>While a haul is ready (facing a rise you could go up) or under way, Use does nothing else: no item is used and no
 * block worked, unless the crosshair is on a mob or on something a click is for (a door, a chest...), which keeps the click.
 */
public final class ProneHaulClient {
	private static ProneHaul.@Nullable Spot spot;
	private static int ticks;
	private static int total;
	private static int style;
	/** The server has the haul (its synced copy arrived). */
	private static boolean confirmed;
	/** Lying against a rise that holding Use would haul you up (worked out each tick). */
	private static boolean ready;
	/** Ticks before another haul may begin (after one ends or the grip goes). */
	private static int cooldown;
	/** After a fall, Use must be let go before it hauls again. */
	private static boolean useLatch;

	/** After a haul ends, this long (ticks) before another may begin. */
	static final int COOLDOWN_TICKS = 6;

	private ProneHaulClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(ProneHaulClient::swallowClicks);
		ClientTickEvents.END_CLIENT_TICK.register(ProneHaulClient::tick);
	}

	/** The local player is hauling themselves up a block. */
	public static boolean active() {
		return spot != null;
	}

	/** Use belongs to the haul now (ready to begin one, or one under way): no item use, no block worked. */
	public static boolean wantsUse() {
		Minecraft minecraft = Minecraft.getInstance();
		return active() || ready && minecraft.player != null && !LedgeClient.clickTaken(minecraft, minecraft.player);
	}

	/** The local player's haul, as everyone's copy has it (before the server's arrives too), or null. */
	public static ProneHaul.@Nullable Haul localHaul(LocalPlayer player) {
		ProneHaul.Spot at = spot;
		if (at == null) {
			return null;
		}
		return new ProneHaul.Haul(at.face().get2DDataValue(), player.level().getGameTime() - ticks, style, total, at.from(), at.to(),
			(float) at.wall());
	}

	/** How far through the local haul (0 to 1) at {@code partialTick}. */
	public static float localProgress(float partialTick) {
		return total <= 0 ? 0.0F : Mth.clamp((ticks + partialTick) / total, 0.0F, 1.0F);
	}

	private static void swallowClicks(Minecraft minecraft) {
		if (minecraft.player == null || !wantsUse()) {
			return;
		}
		while (minecraft.options.keyUse.consumeClick()) {
			// (the press is the haul's)
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			end();
			ready = false;
			cooldown = 0;
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		boolean use = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
		if (!use) {
			useLatch = false;
		}
		if (spot == null) {
			ProneHaul.Spot found = cooldown == 0 && player.onGround() && !LedgeClient.active() && !player.isInWater()
				&& CrawlTaps.localRollProgress(player.level().getGameTime(), 0.0F) == null && ProneHaul.canHaul(player)
				? ProneHaul.find(player) : null;
			ready = found != null;
			if (found != null && use && !useLatch && !LedgeClient.clickTaken(minecraft, player)) {
				start(player, found);
			}
			return;
		}
		ready = false;
		ticks++;
		boolean held = player.hasAttached(ProneHaul.HAUL);
		confirmed |= held;
		if (!confirmed && ticks > ProneHaul.CONFIRM_TICKS) {
			// the server didn't take it up: drop it quietly where we are
			end();
			cooldown = COOLDOWN_TICKS;
			return;
		}
		if (confirmed && !held || Carry.isThrown(player) || !player.isAlive() || player.isPassenger() || !Crawl.crawling(player)) {
			// the server ended it (a hit knocked you off, or you were taken out of the crawl)
			fallBack(player);
			return;
		}
		if (!use) {
			letGo(player);
			return;
		}
		if (ticks >= total) {
			// lying on top
			end();
			cooldown = COOLDOWN_TICKS;
			ClientPlayNetworking.send(new ProneHaul.Stop(true));
		}
	}

	private static void start(LocalPlayer player, ProneHaul.Spot found) {
		spot = found;
		style = ProneHaul.style(player);
		total = ProneHaul.ticks(player, style);
		ticks = 0;
		confirmed = false;
		ready = false;
		player.setDeltaMovement(Vec3.ZERO);
		ClientPlayNetworking.send(new ProneHaul.Start(found.pos(), found.face().get2DDataValue(), found.from(), found.to(), (float) found.wall()));
	}

	/** Use let go part way: the grip goes, and the server ragdolls you back off the face. */
	private static void letGo(LocalPlayer player) {
		ClientPlayNetworking.send(new ProneHaul.Stop(false));
		fallBack(player);
	}

	private static void fallBack(LocalPlayer player) {
		ProneHaul.Spot at = spot;
		end();
		cooldown = COOLDOWN_TICKS * 3;
		useLatch = true;
		if (at != null) {
			Direction face = at.face();
			player.setDeltaMovement(-face.getStepX() * ProneHaul.FALL_PUSH, ProneHaul.FALL_LIFT, -face.getStepZ() * ProneHaul.FALL_PUSH);
		}
	}

	/** In place of the player's travel while hauling: the body follows the haul's path, up the face and in. */
	public static void travel(LocalPlayer player) {
		ProneHaul.Spot at = spot;
		if (at == null) {
			return;
		}
		float p = Math.min(1.0F, (ticks + 1.0F) / Math.max(1, total));
		Vec3 target = ProneHaul.at(style, p, at.from(), at.to());
		player.setDeltaMovement(Vec3.ZERO);
		player.move(MoverType.SELF, target.subtract(player.position()));
		if (ticks + 1 >= total) {
			// set down on the top
			player.move(MoverType.SELF, new Vec3(0.0, -0.05, 0.0));
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}

	private static void end() {
		spot = null;
		ticks = 0;
		total = 0;
		confirmed = false;
	}
}
