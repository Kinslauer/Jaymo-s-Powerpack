package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.17.54) The Great Ape's steps, on the client. Andy: the apes too should "move with a slow sway, keeping their move
 * speed but removing that fast swing". Vanilla's walk cycle runs with the ground covered as if the ape were a player's
 * size, so its giant legs flailed; here each ape's stride runs one step every {@code ZFighterLineage#APE_STRIDE} blocks
 * (scaled to its size), and its model plays vanilla's own walk on that slower stride ({@link #stride}). The server's
 * footfalls ({@link ZFighterNet.ApeStep}) keep it in step (the left foot down on the even steps, the right on the odd) and
 * start each footfall's shockwave: a ring of dust and puffs running out over the ground to its reach.
 */
public final class ZFighterApeSteps {
	/** Before its first footfall arrives: how far a full-grown ape walks a step (blocks). */
	static final float DEFAULT_STEP = 5.0F;
	/** How long the ring takes to run out to its reach (ticks), and how far apart its dust is (blocks). */
	static final int RING_TICKS = 5;
	static final double RING_GAP = 1.0;
	/** How far up or down the ring follows the ground (blocks). */
	static final int CLIMB = 3;

	private static final class Stride {
		float phase, step = DEFAULT_STEP;
		Vec3 last;
	}

	private record Ring(double x, double y, double z, double reach, int[] age) {
	}

	private static final Map<Entity, Stride> STRIDES = new WeakHashMap<>();
	private static final List<Ring> RINGS = new ArrayList<>();

	private ZFighterApeSteps() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.ApeStep.TYPE, (step, context) -> {
			ClientLevel level = context.client().level;
			Entity entity = level == null ? null : level.getEntity(step.entity());
			if (entity != null) {
				Stride stride = STRIDES.computeIfAbsent(entity, e -> new Stride());
				stride.step = Math.max(0.5F, step.step());
				// the nearest step of that foot
				int n = Math.round(stride.phase);
				if (((n & 1) == 0) != step.left()) {
					n += stride.phase > n ? 1 : -1;
				}
				stride.phase = n;
			}
			RINGS.add(new Ring(step.x(), step.y(), step.z(), step.reach(), new int[1]));
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				RINGS.clear();
				STRIDES.clear();
				return;
			}
			drawRings(client.level);
		});
	}

	/**
	 * How far through its stride {@code entity} is this frame, in steps, run on by the ground it has covered since the last
	 * frame (a teleport isn't counted as steps).
	 */
	static float stride(Entity entity, float partialTicks) {
		Stride stride = STRIDES.computeIfAbsent(entity, e -> new Stride());
		Vec3 at = entity.getPosition(partialTicks);
		if (stride.last != null && entity.onGround()) {
			double moved = Math.sqrt(Mth.square(at.x - stride.last.x) + Mth.square(at.z - stride.last.z));
			stride.phase += (float)Math.min(moved / stride.step, 0.5);
		}
		stride.last = at;
		return stride.phase;
	}

	private static void drawRings(ClientLevel level) {
		for (Iterator<Ring> it = RINGS.iterator(); it.hasNext();) {
			Ring ring = it.next();
			int age = ++ring.age()[0];
			double r = ring.reach() * Math.min(age, RING_TICKS) / RING_TICKS;
			int points = Math.max(10, (int)(r * Mth.TWO_PI / RING_GAP));
			double turn = level.getRandom().nextDouble();
			for (int i = 0; i < points; i++) {
				double angle = (i + turn) * Mth.TWO_PI / points, cx = Math.cos(angle), cz = Math.sin(angle);
				double x = ring.x() + cx * r, z = ring.z() + cz * r, y = top(level, x, ring.y(), z);
				if (Double.isNaN(y)) {
					continue;
				}
				BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
				if (!ground.isAir()) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y + 0.1, z, cx * 0.3, 0.25, cz * 0.3);
				}
				if ((i & 1) == 0) {
					level.addParticle(ParticleTypes.POOF, x, y + 0.15, z, cx * 0.12, 0.03, cz * 0.12);
				}
			}
			if (age >= RING_TICKS) {
				it.remove();
			}
		}
	}

	/** The top of the ground at {@code (x, z)} within {@link #CLIMB} of {@code y}, or NaN. */
	private static double top(ClientLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, y + CLIMB, z).mutable();
		for (int i = 0; i <= CLIMB * 2; i++, pos.move(0, -1, 0)) {
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.max(Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}
}
