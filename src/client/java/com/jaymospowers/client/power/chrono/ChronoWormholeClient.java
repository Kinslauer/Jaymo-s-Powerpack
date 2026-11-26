package com.jaymospowers.client.power.chrono;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.chrono.ChronoMomentum;
import com.jaymospowers.power.chrono.ChronoSpeed;
import com.jaymospowers.power.chrono.ChronoWormhole;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The wormhole on this side ({@link ChronoWormhole}): the holes the server has torn open near you, drawn by
 * {@link ChronoWormholeArt#drawHole} tearing open and closing; the catch key (default numpad {@code 1}) that drags the foe ahead
 * when you run in top gear; and the mark, which the server needs to know about so a wormhole can put you there
 * ({@link #marked}, called by {@code ChronoSpeedKeys}).
 */
public final class ChronoWormholeClient {
	/** Ticks a hole takes to tear open and to close. */
	static final float OPEN_TICKS = 8.0F;

	private record Hole(Vec3 at, Vec3 dir, long from, long until) {
	}

	private static final Int2ObjectMap<Hole> HOLES = new Int2ObjectOpenHashMap<>();
	private static KeyMapping catchKey;

	private ChronoWormholeClient() {
	}

	public static void init() {
		catchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.speed_catch", InputConstants.KEY_NUMPAD1, JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(ChronoWormhole.Open.TYPE, (payload, context) -> context.client().execute(() -> {
			ClientLevel level = context.client().level;
			if (level == null || payload.life() <= 0) {
				HOLES.remove(payload.owner());
			} else {
				HOLES.put(payload.owner(), new Hole(payload.at(), payload.dir(), level.getGameTime(), level.getGameTime() + payload.life()));
			}
		}));
		LevelRenderEvents.COLLECT_SUBMITS.register(ChronoWormholeClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(ChronoWormholeClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HOLES.clear());
		ChronoWormholeArt.init();
	}

	/** You marked a place (where you stand): the server is told, so a wormhole can put you there. */
	static void marked(BlockPos pos) {
		if (ClientPlayNetworking.canSend(ChronoWormhole.Mark.TYPE)) {
			ClientPlayNetworking.send(new ChronoWormhole.Mark(true, pos));
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (catchKey == null) {
			return;
		}
		boolean free = player != null && minecraft.gui.screen() == null && ChronoSpeed.overclocked(player) && player.isAlive() && !player.isSpectator();
		while (catchKey.consumeClick()) {
			// only in top gear at speed: the blur
			if (free && player.getAttached(ChronoMomentum.BLUR) != null && ClientPlayNetworking.canSend(ChronoWormhole.Catch.TYPE)) {
				ClientPlayNetworking.send(new ChronoWormhole.Catch());
			}
		}
		if (HOLES.isEmpty() || player == null) {
			return;
		}
		long now = player.level().getGameTime();
		HOLES.values().removeIf(hole -> now > hole.until() + 2);
	}

	private static void render(LevelRenderContext context) {
		if (HOLES.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float now = minecraft.level.getGameTime() + partial;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		for (Hole hole : HOLES.values()) {
			float open = Mth.clamp(Math.min((now - hole.from()) / OPEN_TICKS, (hole.until() - now) / OPEN_TICKS), 0.0F, 1.0F);
			ChronoWormholeArt.drawHole(collector, poseStack, hole.at().subtract(camera), hole.dir(), now, open);
		}
	}
}
