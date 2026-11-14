package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.forms.Lasso;
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
 * Where a held lasso's rope leaves its coil, taken from the item as it's drawn (the way Jaymo's Powers finds a flail's
 * handle, its LashSockets): while a player's main-hand lasso is submitted (third person: ItemInHandLayer, your own
 * first person: the hands renderer), the item model's own transform carries the top of the coil ({@link #COIL_TIP},
 * in the model's 0-1 space) into the world. Kept per player as an offset from where they're drawn, so the rope stays
 * on the coil however the arm, the body and the camera move. Each capture is stamped with the frame it was drawn in;
 * your own first-person view takes whichever was drawn last, the vanilla hand's or (with First-person Model) your
 * drawn body's.
 */
public final class LassoSockets {
	/** The top of the coil's lead, in its model's own space (tools/gen_chain_weapons.py). */
	static final Vector3f COIL_TIP = new Vector3f(0.5F, 11.5F / 16.0F, 8.1F / 16.0F);
	/** The hands are drawn as if the view were this wide (Camera's base HUD fov), whatever the world's fov is. */
	private static final float HAND_FOV = 70.0F;
	/** An offset further than this from the body is someone drawn elsewhere (a menu's preview): not kept. */
	private static final double MAX_OFFSET = 3.0;
	/** A tip not drawn for this many frames is stale (the hand isn't being drawn any more). */
	private static final int STALE_FRAMES = 3;

	private record Holder(int id, boolean firstPerson, double x, double y, double z) {
	}

	private record Tip(Vec3 offset, long frame) {
	}

	private static final Map<Integer, Tip> THIRD = new HashMap<>();
	private static @Nullable Tip first;
	/** Counts the level's frames (each time its entities are submitted). */
	private static long frame;
	private static @Nullable Holder holder;
	/** Set while the level's entities are being submitted (not a menu's preview of a player). */
	private static boolean inLevel;

	private LassoSockets() {
	}

	public static void level(boolean on) {
		inLevel = on;
		if (on) {
			frame++;
		}
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
		return stack.is(Lasso.ITEM) ? new Holder(id, firstPerson, x, y, z) : null;
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
		Vector3f tip = pose.transformPosition(COIL_TIP, new Vector3f());
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
				first = new Tip(camera.position().add(world.x(), world.y(), world.z()).subtract(self.getPosition(partialTick)), frame);
			}
			return;
		}
		Vec3 offset = at.add(camera.position()).subtract(held.x(), held.y(), held.z());
		if (offset.lengthSqr() < MAX_OFFSET * MAX_OFFSET) {
			THIRD.put(held.id(), new Tip(offset, frame));
		}
	}

	/**
	 * The coil's top as last drawn, from the player's position (null: not drawn lately). Your own, in first person: the
	 * vanilla hand's or your drawn body's, whichever was drawn last (the vanilla hand when both are).
	 */
	static @Nullable Vec3 offset(Entity player, boolean firstPerson) {
		Tip body = fresh(THIRD.get(player.getId()));
		if (!firstPerson) {
			return body == null ? null : body.offset();
		}
		Tip hand = fresh(first);
		Tip tip = hand == null ? body : body == null || hand.frame() >= body.frame() ? hand : body;
		return tip == null ? null : tip.offset();
	}

	private static @Nullable Tip fresh(@Nullable Tip tip) {
		return tip != null && frame - tip.frame() <= STALE_FRAMES ? tip : null;
	}

	static void clear() {
		THIRD.clear();
		first = null;
	}
}
