package com.jaymoskitchen.client.hands;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.cooking.hands.HandsOn;
import com.jaymoskitchen.cooking.hands.Pick;
import com.jaymoskitchen.net.Payloads;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * The client side of cooking by hand: what the stations round here hold and how their dishes are going (the server
 * tells us, see {@link Payloads.HandsSync}), the readout of the one you're at (and, in the world, the searing bar over
 * any pan near, see {@link SearBar}), and the inputs a click can't carry (the
 * angle you point round a pot, whether use is held), sent while you work a running dish. Sneak and scroll over a station
 * picks between the dishes it could make.
 */
public final class HandsClient {
	/** A station not heard from for this many ticks is forgotten. */
	static final int STALE_TICKS = 100;
	/** How far (blocks) the readout looks for a station when none is aimed at. */
	static final double NEAR = 6.0;

	record View(Payloads.HandsSync sync, int seen) {
	}

	static final Map<BlockPos, View> VIEWS = new HashMap<>();
	static int clock;
	/** The attack key's press has already been sent as a chop (or a knead); cleared when the key comes up. */
	private static boolean pressed;

	private HandsClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.HandsMode.TYPE, (payload, ctx) -> HandsOn.clientOn = payload.on());
		ClientPlayNetworking.registerGlobalReceiver(Payloads.HandsSync.TYPE, (payload, ctx) -> {
			if (payload.phase() == Payloads.HandsSync.PHASE_GONE) {
				VIEWS.remove(payload.pos());
			} else {
				VIEWS.put(payload.pos(), new View(payload, clock));
			}
		});
		HandsOn.clientRuns = pos -> {
			View view = VIEWS.get(pos);
			return view != null && view.sync().phase() == Payloads.HandsSync.PHASE_RUNNING;
		};
		HandsOn.clientSteps = pos -> {
			View view = VIEWS.get(stationOf(Minecraft.getInstance(), pos));
			return view != null && view.sync().phase() == Payloads.HandsSync.PHASE_RUNNING && view.sync().kind() == Pick.Kind.SEQUENCE.ordinal();
		};
		HandsOn.clientPress = () -> {
			boolean fresh = !pressed;
			pressed = true;
			return fresh;
		};
		ClientTickEvents.END_CLIENT_TICK.register(HandsClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKitchen.id("hands"), HandsHud::draw);
		LevelRenderEvents.COLLECT_SUBMITS.register(SearBar::render);
	}

	private static void tick(Minecraft minecraft) {
		clock++;
		if (!minecraft.options.keyAttack.isDown()) {
			pressed = false;
		}
		Iterator<View> it = VIEWS.values().iterator();
		while (it.hasNext()) {
			if (clock - it.next().seen() > STALE_TICKS) {
				it.remove();
			}
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || !HandsOn.clientOn) {
			return;
		}
		HandsInputs.tick(minecraft, player);
	}

	/** The station block a crosshair at a block is on: a spit is the fire below it. */
	static BlockPos stationOf(Minecraft minecraft, BlockPos hit) {
		if (minecraft.level != null && minecraft.level.getBlockState(hit).is(com.jaymoskitchen.cooking.spit.SpitBlock.SPIT)) {
			return hit.below();
		}
		return hit;
	}

	/** The station being aimed at, if the server has told us about it. */
	static @Nullable View aimed(Minecraft minecraft) {
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return VIEWS.get(stationOf(minecraft, hit.getBlockPos()));
		}
		return null;
	}

	/** The station to show: the one aimed at, else the nearest within reach. */
	static @Nullable View current(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return null;
		}
		View aimed = aimed(minecraft);
		if (aimed != null) {
			return aimed;
		}
		View best = null;
		double bestDistance = NEAR * NEAR;
		for (View view : VIEWS.values()) {
			BlockPos pos = view.sync().pos();
			double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = view;
			}
		}
		return best;
	}

	/** Sneak and scroll over a station that holds things: choose the next dish it could make. True if the scroll was used. */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !HandsOn.clientOn || !player.isShiftKeyDown() || amount == 0.0 || minecraft.gui.screen() != null) {
			return false;
		}
		View view = aimed(minecraft);
		if (view == null || view.sync().phase() != Payloads.HandsSync.PHASE_STAGING || view.sync().picks() < 1) {
			return false;
		}
		ClientPlayNetworking.send(new Payloads.HandsPick(view.sync().pos(), amount > 0 ? -1 : 1));
		return true;
	}
}
