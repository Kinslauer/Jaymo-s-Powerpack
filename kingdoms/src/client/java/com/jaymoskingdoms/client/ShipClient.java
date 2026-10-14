package com.jaymoskingdoms.client;

import com.jaymoskingdoms.sailing.HelmBlock;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.ShipNet;
import com.jaymoskingdoms.sailing.Ships;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.5) The client's part in sailing: the deck carries you (your own game moves you, so it carries you here), the wheel
 * turns you with the ship, the ship heels in its turns, and right-clicking its helm or a chest aboard uses it.
 */
public final class ShipClient {
	/** Heel per degree of turn a tick, and how quickly it eases in. */
	static final float HEEL_PER_TURN = 2.2F, HEEL_EASE = 0.08F, MOST_HEEL = 9.0F;
	private static int useCooldown;

	private ShipClient() {
	}

	static void init() {
		Ship.clientTick = ShipClient::tick;
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (useCooldown > 0) {
				useCooldown--;
			}
		});
	}

	static void tick(Ship ship) {
		float turned = Mth.wrapDegrees(ship.getYRot() - ship.yRotO);
		ship.heelO = ship.heel;
		ship.heel += (Mth.clamp(turned * HEEL_PER_TURN, -MOST_HEEL, MOST_HEEL) - ship.heel) * HEEL_EASE;
		LocalPlayer you = Minecraft.getInstance().player;
		if (you == null || you.level() != ship.level()) {
			return;
		}
		if (you.getVehicle() == ship) {
			you.setYRot(you.getYRot() + turned);
			you.setYHeadRot(you.getYHeadRot() + turned);
			return;
		}
		if (you.isPassenger() || you.isSpectator()) {
			return;
		}
		Vec3 before = new Vec3(ship.xo, ship.yo, ship.zo);
		Vec3 local = Ship.toLocal(you.getX(), you.getY(), you.getZ(), before, ship.yRotO);
		if (!ship.aboard(local, 0.3) || before.equals(ship.position()) && turned == 0.0F) {
			return;
		}
		Vec3 now = Ship.toWorld(local.x, local.y, local.z, ship.position(), ship.getYRot());
		you.setPos(now);
		you.setYRot(you.getYRot() + turned);
		you.setYHeadRot(you.getYHeadRot() + turned);
	}

	/** Uses the ship's helm or chest in your sights, if it's nearer than whatever block you're looking at. */
	public static boolean use(Minecraft mc) {
		LocalPlayer you = mc.player;
		if (you == null || mc.level == null || useCooldown > 0 || you.isPassenger()) {
			return false;
		}
		Vec3 eye = you.getEyePosition();
		Vec3 look = you.getViewVector(1.0F);
		double reach = you.blockInteractionRange();
		HitResult hit = mc.hitResult;
		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			reach = Math.min(reach, hit.getLocation().distanceTo(eye));
		}
		for (Ship ship : Ships.in(mc.level)) {
			Hull h = ship.hull();
				if (h == null || !ship.getBoundingBox().inflate(reach).contains(eye)) {
				continue;
			}
			for (double t = 0.0; t <= reach; t += 0.05) {
				Vec3 l = ship.toLocal(eye.add(look.scale(t)));
				int x = Mth.floor(l.x + 0.5), y = Mth.floor(l.y), z = Mth.floor(l.z + 0.5);
				int i = h.at(x, y, z);
				if (i < 0) {
					continue;
				}
				// inside the block's own shape (a fence post, not its whole cell)
				BlockState state = h.pieces.get(i).blockState();
				double inX = l.x - x + 0.5, inY = l.y - y, inZ = l.z - z + 0.5;
				boolean in = false;
				for (AABB box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
					if (box.inflate(0.01).contains(inX, inY, inZ)) {
						in = true;
						break;
					}
				}
				if (!in) {
					continue;
				}
						if (!(state.getBlock() instanceof HelmBlock) && !(state.getBlock() instanceof EntityBlock)) {
					return false;
				}
				ClientPlayNetworking.send(new ShipNet.Use(ship.getId(), x, y, z));
				you.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
				useCooldown = 4;
				return true;
			}
		}
		return false;
	}
}
