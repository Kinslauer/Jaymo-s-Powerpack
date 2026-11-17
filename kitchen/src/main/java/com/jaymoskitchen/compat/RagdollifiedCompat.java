package com.jaymoskitchen.compat;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.Loaded;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.ragdoll.RagdollNet;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Ragdollified (Raiiiden, all rights reserved; downloaded by id, never copied) and Ragdollified Reactions. When they're in
 * the pack their ragdolls do the falling: a dying body is played out by Ragdollified (this mod's corpse waits, undrawn,
 * and lies down where it comes to rest, see {@link CorpseEntity#settleAt}), and Reactions ragdolls the living (our
 * crash, blast, cart and hard-landing limp, and the big-hit mob limp, stand down). Our own ragdoll code is the fallback
 * when they're absent. Called by reflection only, so Kitchen runs the same without them.
 */
public final class RagdollifiedCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("ragdollified");
	private static final boolean REACTIONS = FabricLoader.getInstance().isModLoaded("ragdollifiedreactions");

	/** Ticks a corpse waits for a ragdoll to report it's at rest before it is drawn where it is. */
	public static final int WAIT_TICKS = 600;
	/** The furthest (blocks) a body may have settled from where its corpse is: a flung one, not a made-up spot. */
	private static final double SETTLE_REACH = 64.0;
	/** The furthest (blocks) a reporting player may be from the corpse. */
	private static final double REPORT_REACH = 128.0;
	/** How far (blocks) a mapped pose's middle may be from the spot reported with it. */
	private static final double POSE_NEAR = 2.0;

	private RagdollifiedCompat() {
	}

	public static boolean loaded() {
		return LOADED;
	}

	public static boolean reactionsLoaded() {
		return REACTIONS;
	}

	public static void register() {
		if (!LOADED) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(RagdollNet.Settled.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!(player.level().getEntity(payload.corpse()) instanceof CorpseEntity corpse) || corpse.origin() < 0
				|| player.distanceToSqr(corpse) > REPORT_REACH * REPORT_REACH) {
				return;
			}
			double x = payload.x(), y = payload.y(), z = payload.z();
			if (!Double.isFinite(x + y + z + payload.yaw()) || corpse.distanceToSqr(x, y, z) > SETTLE_REACH * SETTLE_REACH
				|| !Loaded.made(player.level(), BlockPos.containing(x, y, z))) {
				return;
			}
			RagdollSnapshot pose = payload.pose();
			corpse.settleAt(x, y, z, payload.yaw(), pose != null && believable(pose, corpse.ragdollRig(), x, y, z) ? pose : null);
		});
		try {
			registerSeveredParts();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			JaymosKitchen.LOGGER.warn("Ragdollified is loaded but its severed-part hook didn't take: {}", e.toString());
		}
	}

	/**
	 * Whether a pose a client mapped from Ragdollified's ragdoll is for this corpse's rig, lies where the ragdoll was
	 * reported, and holds together: every point within the body's reach of its middle, every stick near its rest length.
	 */
	private static boolean believable(RagdollSnapshot pose, RagdollRig rig, double x, double y, double z) {
		if (!pose.fits(rig) || !Double.isFinite(pose.x() + pose.y() + pose.z())
			|| Math.abs(pose.x() - x) > POSE_NEAR || Math.abs(pose.z() - z) > POSE_NEAR || Math.abs(pose.y() - y) > POSE_NEAR) {
			return false;
		}
		double reach = rig.height * 1.5 + 1.0;
		for (int i = 0; i < rig.count; i++) {
			double dx = pose.pointX(i) - pose.x(), dy = pose.pointY(i) - pose.y(), dz = pose.pointZ(i) - pose.z();
			if (dx * dx + dy * dy + dz * dz > reach * reach) {
				return false;
			}
		}
		for (int k = 0; k < rig.stickA.length; k++) {
			int a = rig.stickA[k], b = rig.stickB[k];
			double dx = pose.pointX(a) - pose.pointX(b), dy = pose.pointY(a) - pose.pointY(b), dz = pose.pointZ(a) - pose.pointZ(b);
			if (Math.abs(Math.sqrt(dx * dx + dy * dy + dz * dz) - rig.stickLength[k]) > rig.stickLength[k] * 0.15 + 0.05) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A body that lost a limb (Powers' Dismember tags it, see {@link Corpses#missingParts}) falls as a Ragdollified ragdoll
	 * without that limb.
	 */
	private static void registerSeveredParts() throws ReflectiveOperationException {
		Class<?> provider = Class.forName("com.raiiiden.ragdollified.api.SeveredPartProvider");
		Class<?> serverApi = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedServerApi");
		Class<?> partClass = Class.forName("com.raiiiden.ragdollified.RagdollPart");
		Method bit = partClass.getMethod("bit");
		// in the order of Corpses.missingParts' bits: head, right arm, left arm, right leg, left leg
		String[] names = {"HEAD", "RIGHT_ARM", "LEFT_ARM", "RIGHT_LEG", "LEFT_LEG"};
		int[] bits = new int[names.length];
		for (int i = 0; i < names.length; i++) {
			Object part = partClass.getField(names[i]).get(null);
			bits[i] = (Integer) bit.invoke(part);
		}
		Object proxy = Proxy.newProxyInstance(RagdollifiedCompat.class.getClassLoader(), new Class<?>[] {provider},
			(self, method, args) -> {
				switch (method.getName()) {
					case "severedPartMask" -> {
						int missing = Corpses.missingParts((LivingEntity) args[0]);
						int mask = 0;
						for (int i = 0; i < bits.length; i++) {
							if ((missing >> i & 1) != 0) {
								mask |= bits[i];
							}
						}
						return mask;
					}
					case "hashCode" -> {
						return System.identityHashCode(self);
					}
					case "equals" -> {
						return self == args[0];
					}
					default -> {
						return self.getClass().getName();
					}
				}
			});
		serverApi.getMethod("addSeveredPartProvider", provider).invoke(null, proxy);
	}
}
