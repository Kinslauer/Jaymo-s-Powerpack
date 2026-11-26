package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.LowEndMode;
import com.jaymospowers.client.power.gravity.mixin.LensPostChainAccessor;
import com.jaymospowers.client.power.gravity.mixin.LensPostPassAccessor;
import com.jaymospowers.power.chrono.ChronoSkip;
import com.jaymospowers.power.chrono.ChronoSpeed;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * Time skip on the runner's own side ({@link ChronoSkip}): the skip key (default numpad {@code 0}) asks for it; while it lasts the
 * runner is pushed forward at speed for its first {@link #PUSH_TICKS} ticks (movement is the client's to decide, as the wall
 * run's is) and the screen warps: the {@code chrono_warp} post effect pulls the picture in toward the middle and streaks it
 * outward, rings running across it, easing in and out. Its strength and clock are written into the effect's settings block each
 * frame, the way the black hole's lensing does it ({@code LensClient}); if that goes wrong on a machine the warp is switched
 * off for the session. Not shown in low-end mode.
 */
public final class ChronoSkipClient {
	/** The post effect and its settings block. */
	static final Identifier EFFECT = JaymosPowers.id("chrono_warp");
	private static final String BLOCK = "WarpConfig";
	/** The push forward: how many ticks, how fast (blocks a tick). */
	static final int PUSH_TICKS = 14;
	static final double PUSH_SPEED = 1.0;
	/** The warp comes in over this share of the skip and goes out over this share. */
	static final float EASE_IN = 0.12F, EASE_OUT = 0.35F;

	private static KeyMapping skipKey;
	/** Ticks into the skip on this screen (-1: none), the warp's strength this frame, its clock. */
	private static int age = -1;
	private static float strength, time;
	private static @Nullable GpuBuffer block;
	private static boolean broken;

	private ChronoSkipClient() {
	}

	public static void init() {
		skipKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.speed_skip", InputConstants.KEY_NUMPAD0, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(ChronoSkipClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			age = -1;
			strength = 0.0F;
		});
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (skipKey == null || player == null) {
			age = -1;
			strength = 0.0F;
			return;
		}
		boolean free = minecraft.gui.screen() == null && ChronoSpeed.overclocked(player) && player.isAlive() && !player.isSpectator();
		while (skipKey.consumeClick()) {
			if (free && !player.hasAttached(ChronoSkip.SKIPPING) && ClientPlayNetworking.canSend(ChronoSkip.Press.TYPE)) {
				ClientPlayNetworking.send(new ChronoSkip.Press());
			}
		}
		if (!player.hasAttached(ChronoSkip.SKIPPING)) {
			age = -1;
			strength = Math.max(0.0F, strength - 0.2F);
			return;
		}
		age++;
		float share = Mth.clamp(age / (float)ChronoSkip.TICKS, 0.0F, 1.0F);
		strength = Mth.clamp(Math.min(share / EASE_IN, (1.0F - share) / EASE_OUT), 0.0F, 1.0F);
		time = age;
		if (age < PUSH_TICKS && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()) {
			Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
			Vec3 now = player.getDeltaMovement();
			player.setDeltaMovement(look.x * PUSH_SPEED, now.y, look.z * PUSH_SPEED);
		}
	}

	/** The warp this frame (ChronoSlowGameRendererMixin), or null. */
	public static @Nullable Identifier effect() {
		return strength > 0.01F && !broken && !LowEndMode.on() ? EFFECT : null;
	}

	/** Just before the frame's post effects are drawn: this frame's strength and clock go into the effect's settings block. */
	public static void write(List<PostChain> applied) {
		if (broken || strength <= 0.0F) {
			return;
		}
		try {
			for (PostChain chain : applied) {
				if (!EFFECT.equals(chain.id())) {
					continue;
				}
				for (PostPass pass : ((LensPostChainAccessor) chain).jaymospowers$passes()) {
					Map<String, GpuBuffer> uniforms = ((LensPostPassAccessor) pass).jaymospowers$customUniforms();
					GpuBuffer had = uniforms.get(BLOCK);
					if (had == null) {
						continue;
					}
					if (had != block || had.isClosed()) {
						// the effect was (re)loaded: its block is made once and never written again, so ours takes its place
						block = RenderSystem.getDevice().createBuffer(() -> "Time skip settings", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, had.size());
						uniforms.put(BLOCK, block);
						had.close();
					}
					try (MemoryStack stack = MemoryStack.stackPush()) {
						ByteBuffer bytes = Std140Builder.onStack(stack, (int) block.size()).putFloat(strength).putFloat(time).get();
						RenderSystem.getDevice().createCommandEncoder().writeToBuffer(block.slice(0, bytes.remaining()), bytes);
					}
				}
			}
		} catch (Throwable e) {
			broken = true;
			JaymosPowers.LOGGER.warn("The time skip's screen warp is off for this session", e);
		}
	}
}
