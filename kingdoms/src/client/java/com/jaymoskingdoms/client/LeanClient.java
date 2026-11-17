package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Lean;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The client's half of leaning ({@link Lean}): the two keys (Q and E, only while sneaking: without Shift Q still drops and E
 * still opens the inventory), what the server is told, and how far each player has leaned just now, eased so the body and the
 * view swing out and back. The camera ({@code CameraLeanMixin}) and every player's model ({@code AvatarLeanMixin}) read it.
 * <p>
 * The local player's lean follows the keys at once; everyone else's follows the attachment the server syncs.
 */
public final class LeanClient {
	/** How fast the lean comes and goes (a share of the full lean a tick): a quarter second to full. */
	static final float STEP = 0.2F;
	/** The camera stops this far short of a wall it leans into (blocks). */
	public static final double CAMERA_GAP = 0.25;

	private static KeyMapping left;
	private static KeyMapping right;
	/** The lean each player has (entity id: before and now, -1 full left to 1 full right). */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();
	/** The lean a player's render state was extracted with. */
	private static final Map<EntityRenderState, Float> STATES = new WeakHashMap<>();
	/** What the server was last told. */
	private static int sent;

	private LeanClient() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("lean"));
		left = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.lean_left", InputConstants.KEY_Q, category));
		right = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.lean_right", InputConstants.KEY_E, category));
		ClientTickEvents.START_CLIENT_TICK.register(LeanClient::claimKeys);
		ClientTickEvents.END_CLIENT_TICK.register(LeanClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AMOUNTS.clear();
			STATES.clear();
			sent = 0;
		});
	}

	/** Sneaking, a lean key press is the lean's: the drop and the inventory don't hear it (before the game reads its keys). */
	private static void claimKeys(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || !player.isShiftKeyDown()) {
			return;
		}
		boolean used = left.isDown() || right.isDown();
		while (left.consumeClick()) {
			used = true;
		}
		while (right.consumeClick()) {
			used = true;
		}
		if (used) {
			while (minecraft.options.keyDrop.consumeClick()) {
				// (swallowed)
			}
			while (minecraft.options.keyInventory.consumeClick()) {
				// (swallowed)
			}
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer me = minecraft.player;
		if (me == null || minecraft.level == null) {
			return;
		}
		int want = 0;
		if (minecraft.gui.screen() == null && Lean.can(me)) {
			want = (right.isDown() ? 1 : 0) - (left.isDown() ? 1 : 0);
		}
		if (want != sent && ClientPlayNetworking.canSend(Lean.Want.TYPE)) {
			ClientPlayNetworking.send(new Lean.Want(want));
			sent = want;
		}
		for (Player player : minecraft.level.players()) {
			float[] a = AMOUNTS.computeIfAbsent(player.getId(), id -> new float[2]);
			a[0] = a[1];
			a[1] += Mth.clamp((player == me ? want : Lean.dir(player)) - a[1], -STEP, STEP);
		}
		if (minecraft.level.getGameTime() % 200L == 0L) {
			AMOUNTS.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
		}
	}

	/** How far {@code entity} has leaned: -1 full left to 1 full right, 0 upright. */
	public static float amount(Entity entity, float partialTicks) {
		float[] a = AMOUNTS.get(entity.getId());
		return a == null ? 0.0F : Mth.lerp(partialTicks, a[0], a[1]);
	}

	/** Remembers a player's lean for their render state. */
	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		float amount = amount(entity, partialTicks);
		if (amount == 0.0F) {
			STATES.remove(state);
		} else {
			STATES.put(state, amount);
		}
	}

	/** Tilts a leaning player's model out about its feet (called as the model is set up, in the body's own frame). */
	public static void rotate(EntityRenderState state, PoseStack poseStack) {
		Float amount = STATES.get(state);
		if (amount != null) {
			poseStack.rotateDegrees(Axis.ZP, -amount * Lean.TILT);
		}
	}
}
