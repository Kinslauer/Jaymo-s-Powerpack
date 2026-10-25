package com.jaymospowers.client.armory;

import com.jaymospowers.armory.Lash;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.8) Where a held flail's chain and a held whip's lash leave the handle, taken from the item as it's drawn (the
 * Whip mod's idea, MIT): while a player's main-hand flail or whip is submitted (third person: ItemInHandLayer, your own
 * first person: the hands renderer), the item model's own transform carries the handle's tip ({@link #FLAIL_TIP},
 * {@link #WHIP_TIP}, in the model's 0-1 space) into the world. Kept per player as an offset from where they're drawn, so
 * the chain stays on the handle however the arm, the body and the camera move. Andy: "the flail chain is completley
 * disconnected from the stick and it looks jarring".
 */
public final class LashSockets {
	/** The handle's tip on its sprite: Balkon's ball-less flail (flail-thrown.png, top right) and our whip's handle. */
	static final Vector3f FLAIL_TIP = new Vector3f(13.2F / 16.0F, 13.4F / 16.0F, 0.5F), WHIP_TIP = new Vector3f(9.0F / 16.0F, 9.6F / 16.0F, 0.5F);
	/** The hands are drawn as if the view were this wide (Camera's base HUD fov), whatever the world's fov is. */
	private static final float HAND_FOV = 70.0F;
	/** An offset further than this from the body is someone drawn elsewhere (a menu's preview): not kept. */
	private static final double MAX_OFFSET = 3.0;

	private record Holder(int id, boolean flail, boolean firstPerson, double x, double y, double z) {
	}

	private static final Map<Integer, Vec3> THIRD = new HashMap<>();
	private static @Nullable Vec3 first;
	private static @Nullable Holder holder;
	/** Set while the level's entities are being submitted (not a menu's preview of a player). */
	private static boolean inLevel;

	private LashSockets() {
	}

	public static void level(boolean on) {
		inLevel = on;
	}

	/** A player's main-hand item is about to be submitted in third person, drawn at {@code x y z}. */
	public static void thirdPerson(int id, ItemStack stack, double x, double y, double z) {
		holder = inLevel ? holder(id, stack, false, x, y, z) : null;
	}

	/** Your own main-hand item is about to be submitted in first person. */
	public static void firstPerson(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		holder = minecraft.player == null ? null : holder(minecraft.player.getId(), stack, true, 0.0, 0.0, 0.0);
	}

	private static @Nullable Holder holder(int id, ItemStack stack, boolean firstPerson, double x, double y, double z) {
		boolean flail = Lash.isFlail(stack);
		return flail || Lash.isWhip(stack) ? new Holder(id, flail, firstPerson, x, y, z) : null;
	}

	public static void done() {
		holder = null;
	}

	/** The item's model transform is set: carry the tip through it (the pose stack is camera-relative, world axes). */
	public static void capture(Matrix4fc pose) {
		Holder held = holder;
		if (held == null) {
			return;
		}
		holder = null;
		Vector3f tip = pose.transformPosition(held.flail() ? FLAIL_TIP : WHIP_TIP, new Vector3f());
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 at = new Vec3(tip.x(), tip.y(), tip.z());
		if (held.firstPerson()) {
			// the hands have their own, narrower lens: the same spot on screen, at the same depth, in the world's
			Quaternionf rotation = camera.rotation();
			Vector3f view = new Quaternionf(rotation).conjugate().transform(tip, new Vector3f());
			float widen = (float)(Math.tan(Math.toRadians(camera.getFov() * 0.5)) / Math.tan(Math.toRadians(HAND_FOV * 0.5)));
			view.mul(widen, widen, 1.0F);
			Vector3f world = rotation.transform(view, new Vector3f());
			Entity self = minecraft.player;
			if (self != null) {
				float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				first = camera.position().add(world.x(), world.y(), world.z()).subtract(self.getPosition(partialTick));
			}
			return;
		}
		Vec3 offset = at.add(camera.position()).subtract(held.x(), held.y(), held.z());
		if (offset.lengthSqr() < MAX_OFFSET * MAX_OFFSET) {
			THIRD.put(held.id(), offset);
		}
	}

	/** The handle's tip as last drawn, from the player's position (null: not drawn yet). */
	static @Nullable Vec3 offset(Entity player, boolean firstPerson) {
		return firstPerson ? first : THIRD.get(player.getId());
	}

	static void forget(int id) {
		THIRD.remove(id);
	}

	static void clear() {
		THIRD.clear();
		first = null;
	}
}
