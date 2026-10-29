package com.jaymoskingdoms.client;

import com.jaymoskingdoms.sailing.HelmBlock;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.ShipNet;
import com.jaymoskingdoms.sailing.Ships;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.7) The client's part in sailing: the deck carries you (your own game moves you, so it carries you here), the wheel
 * turns you with the ship, the ship heels in its turns, and right-clicking its helm or a chest aboard uses it.
 */
public final class ShipClient {
	/** Heel per degree of turn a tick, and how quickly it eases in. */
	static final float HEEL_PER_TURN = 2.2F, HEEL_EASE = 0.08F, MOST_HEEL = 9.0F;
	/** (0.7.18) Ticks between broadsides you can call for. */
	static final int BROADSIDE_COOLDOWN = 20;
	private static int useCooldown;

	private ShipClient() {
	}

	static void init() {
		Ship.clientTick = ShipClient::tick;
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (useCooldown > 0) {
				useCooldown--;
			}
			if (client.level != null) {
				remeshGone(client.level);
			}
		});
	}

	static void tick(Ship ship) {
		float turned = Mth.wrapDegrees(ship.getYRot() - ship.yRotO);
		ship.heelO = ship.heel;
		ship.heel += (Mth.clamp(turned * HEEL_PER_TURN, -MOST_HEEL, MOST_HEEL) - ship.heel) * HEEL_EASE;
		remesh(ship);
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

	// ---- (0.7.306) the sea kept out of her hold ----

	/** Each ship's hold as it was last meshed: where she lay, her hull, whether she was foundering, and the tick. */
	private static final Map<Ship, Meshed> MESHED = new HashMap<>();
	/** How far a ship moves (blocks) or turns (degrees) before the sections she lies in are meshed again, and at most how often (ticks). */
	static final double REMESH_MOVE = 1.0, REMESH_TURN = 6.0;
	static final int REMESH_TICKS = 10;

	private record Meshed(Vec3 at, float yaw, Hull hull, boolean foundering, int tick, AABB box) {
	}

	/**
	 * The sea isn't drawn in her dry hold (FluidRendererDryHoldMixin), so as she moves the sections she lay in and lies
	 * in are meshed again: the sea back where she was, none where she is now. Only the waterline's band of sections, where
	 * her hold meets the sea; and not every tick, which would be a lot of meshing for a big ship under way.
	 */
	private static void remesh(Ship ship) {
		if (!(ship.level() instanceof ClientLevel level)) {
			return;
		}
		Hull h = ship.hull();
		if (h == null || h.masked.length == 0) {
			return;
		}
		Meshed last = MESHED.get(ship);
		Vec3 at = ship.position();
		float yaw = ship.getYRot();
		boolean foundering = ship.foundering();
		if (last != null && (ship.tickCount - last.tick() < REMESH_TICKS
			|| last.hull() == h && last.foundering() == foundering && at.distanceToSqr(last.at()) < REMESH_MOVE * REMESH_MOVE
				&& Math.abs(Mth.wrapDegrees(yaw - last.yaw())) < REMESH_TURN)) {
			return;
		}
		AABB box = waterlineBox(ship, h);
		if (last != null) {
			box = box.minmax(last.box());
		}
		MESHED.put(ship, new Meshed(at, yaw, h, foundering, ship.tickCount, waterlineBox(ship, h)));
		dirty(level, box);
	}

	/** The sea back in the holds of ships that are gone (laid up, sunk, out of sight). */
	private static void remeshGone(ClientLevel level) {
		if (MESHED.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<Ship, Meshed>> it = MESHED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Ship, Meshed> e = it.next();
			if (e.getKey().isRemoved() || e.getKey().level() != level) {
				dirty(level, e.getValue().box());
				it.remove();
			}
		}
	}

	/** Where her hold meets the sea: her footprint, from her keel up to a block over the waterline. */
	private static AABB waterlineBox(Ship ship, Hull h) {
		AABB b = ship.getBoundingBox();
		return new AABB(b.minX, ship.getY() - 1.0, b.minZ, b.maxX, ship.getY() + h.draft + 2.0, b.maxZ);
	}

	private static void dirty(ClientLevel level, AABB box) {
		level.setSectionRangeDirty(SectionPos.blockToSectionCoord(Mth.floor(box.minX)), SectionPos.blockToSectionCoord(Mth.floor(box.minY)),
			SectionPos.blockToSectionCoord(Mth.floor(box.minZ)), SectionPos.blockToSectionCoord(Mth.floor(box.maxX)),
			SectionPos.blockToSectionCoord(Mth.floor(box.maxY)), SectionPos.blockToSectionCoord(Mth.floor(box.maxZ)));
	}

	/** (0.7.18) At the helm, a left click fires a broadside to the side you look. True if it did (the click's used). */
	/**
	 * (0.7.246) Whether EMF should leave this entity's model alone (EmfBladePause): one of a ship's crew at their station,
	 * who stands there (HumanoidHelmStanceMixin) rather than taking any riding pose an EMF pack has for them.
	 */
	public static boolean crewAboard(Object entity) {
		return entity instanceof net.minecraft.world.entity.LivingEntity living && !(living instanceof net.minecraft.world.entity.player.Player)
			&& living.getVehicle() instanceof Ship;
	}

	public static boolean broadside(Minecraft mc) {
		LocalPlayer you = mc.player;
		if (you == null || !(you.getVehicle() instanceof Ship ship)) {
			return false;
		}
		if (useCooldown <= 0) {
			ClientPlayNetworking.send(new ShipNet.Broadside(ship.getId()));
			useCooldown = BROADSIDE_COOLDOWN;
		}
		return true;
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
				if (!(state.getBlock() instanceof HelmBlock) && !(state.getBlock() instanceof EntityBlock)
					&& !(state.getBlock() instanceof com.jaymoskingdoms.sailing.ShipCannonBlock)) {
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
