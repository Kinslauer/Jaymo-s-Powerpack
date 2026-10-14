package com.jaymoskingdoms.sailing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.6) The ships under sail on each side, and what their blocks mean to everything else: each block a box to stand
 * on or walk into, wherever the ship has carried it ({@link #collisions}), and who is aboard one ({@link #aboard}).
 */
public final class Ships {
	private static final List<Ship> CLIENT = new CopyOnWriteArrayList<>(), SERVER = new CopyOnWriteArrayList<>();

	private Ships() {
	}

	static void add(Ship ship) {
		List<Ship> side = ship.level().isClientSide() ? CLIENT : SERVER;
		if (!side.contains(ship)) {
			side.add(ship);
		}
	}

	static void remove(Ship ship) {
		CLIENT.remove(ship);
		SERVER.remove(ship);
	}

	public static List<Ship> in(Level level) {
		List<Ship> out = new ArrayList<>();
		for (Ship s : level.isClientSide() ? CLIENT : SERVER) {
			if (s.level() == level && !s.isRemoved()) {
				out.add(s);
			}
		}
		return out;
	}

	/** The ship {@code entity} is aboard (null: none). */
	public static Ship aboard(Entity entity) {
		if (entity instanceof Ship) {
			return null;
		}
		if (entity.getVehicle() instanceof Ship ship) {
			return ship;
		}
		for (Ship s : in(entity.level())) {
			if (s.getBoundingBox().inflate(1.0).contains(entity.position()) && s.aboard(s.toLocal(entity.position()), 0.5)) {
				return s;
			}
		}
		return null;
	}

	/** Whether a world block lies inside a ship, so the sea it sails over isn't in its hold (or its timbers). */
	public static boolean dry(Level level, net.minecraft.core.BlockPos pos) {
		Vec3 mid = Vec3.atCenterOf(pos);
		for (Ship s : in(level)) {
			if (!s.getBoundingBox().contains(mid)) {
				continue;
			}
			Hull h = s.hull();
			Vec3 l = s.toLocal(mid);
			if (h != null && h.inside(Mth.floor(l.x + 0.5), Mth.floor(l.y), Mth.floor(l.z + 0.5))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Every ship's blocks inside {@code area}, as {@code entity} runs into them: each one an upright box at its block's
	 * middle as the ship now lies, as high as the block's own collision (so a slab is half a step).
	 */
	public static List<VoxelShape> collisions(Entity entity, AABB area, List<VoxelShape> others) {
		if (entity instanceof Ship || entity.noPhysics) {
			return others;
		}
		List<VoxelShape> out = null;
		for (Ship s : in(entity.level())) {
			if (entity.getVehicle() == s || !s.getBoundingBox().inflate(0.5).intersects(area)) {
				continue;
			}
			Hull h = s.hull();
			if (h == null) {
				continue;
			}
			// the area's corners in the ship's frame give the cells to look at
			double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
			for (int c = 0; c < 4; c++) {
				Vec3 l = s.toLocal(new Vec3((c & 1) == 0 ? area.minX : area.maxX, area.minY, (c & 2) == 0 ? area.minZ : area.maxZ));
				x0 = Math.min(x0, l.x);
				x1 = Math.max(x1, l.x);
				z0 = Math.min(z0, l.z);
				z1 = Math.max(z1, l.z);
			}
			int cx0 = Math.max(h.minX, Mth.floor(x0 - 0.5)), cx1 = Math.min(h.maxX, Mth.ceil(x1 + 0.5));
			int cz0 = Math.max(h.minZ, Mth.floor(z0 - 0.5)), cz1 = Math.min(h.maxZ, Mth.ceil(z1 + 0.5));
			int cy0 = Math.max(h.minY, Mth.floor(area.minY - s.getY()) - 1), cy1 = Math.min(h.maxY, Mth.floor(area.maxY - s.getY()));
			for (int x = cx0; x <= cx1; x++) {
				for (int y = cy0; y <= cy1; y++) {
					for (int z = cz0; z <= cz1; z++) {
						int i = h.at(x, y, z);
						if (i < 0 || h.lo[i] > h.hi[i]) {
							continue;
						}
						Vec3 mid = s.toWorld(x, y, z);
						AABB box = new AABB(mid.x - 0.5, mid.y + h.lo[i], mid.z - 0.5, mid.x + 0.5, mid.y + h.hi[i], mid.z + 0.5);
						if (box.intersects(area)) {
							if (out == null) {
								out = new ArrayList<>(others);
							}
							out.add(Shapes.create(box));
						}
					}
				}
			}
		}
		return out == null ? others : out;
	}
}
