import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollWorld;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks Jaymo's Kitchen's ragdoll physics on its own, without the game: bodies dropped, flung and laid over edges in
 * a little world of boxes. Run from the repository root (JDK 21+):
 *   javac -d build/ragdoll_sim kitchen/src/main/java/com/jaymoskitchen/ragdoll/Ragdoll{Rig,Body,World}.java tools/ragdoll_sim/RagdollSimCheck.java
 *   java -cp build/ragdoll_sim RagdollSimCheck
 */
public final class RagdollSimCheck {
	static int failures;

	record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
	}

	static final class World implements RagdollWorld {
		final List<Box> boxes = new ArrayList<>();
		double waterBelow = Double.NEGATIVE_INFINITY;
		double[] mover;

		World add(double a, double b, double c, double d, double e, double f) {
			boxes.add(new Box(a, b, c, d, e, f));
			return this;
		}

		@Override
		public void solids(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Boxes out) {
			for (Box b : boxes) {
				if (b.maxX > minX && b.minX < maxX && b.maxY > minY && b.minY < maxY && b.maxZ > minZ && b.minZ < maxZ) {
					out.add(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
				}
			}
		}

		@Override
		public void movers(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Boxes out) {
			if (mover != null) {
				out.add(mover[0], mover[1], mover[2], mover[3], mover[4], mover[5], mover[6], mover[7], mover[8]);
			}
		}

		@Override
		public int fluid(double x, double y, double z) {
			return y < waterBelow ? WATER : AIR;
		}

		/** How deep a point of radius r is inside any box (0 if clear). */
		double depth(double x, double y, double z, double r) {
			double worst = 0;
			for (Box b : boxes) {
				double cx = Math.max(b.minX, Math.min(x, b.maxX)), cy = Math.max(b.minY, Math.min(y, b.maxY)),
					cz = Math.max(b.minZ, Math.min(z, b.maxZ));
				double dx = x - cx, dy = y - cy, dz = z - cz;
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				boolean inside = x > b.minX && x < b.maxX && y > b.minY && y < b.maxY && z > b.minZ && z < b.maxZ;
				worst = Math.max(worst, inside ? r + 0.5 : r - d);
			}
			return worst;
		}
	}

	static World ground() {
		return new World().add(-50, 63, -50, 50, 64, 50);
	}

	static void check(String name, boolean ok, String detail) {
		System.out.println((ok ? "  ok   " : "  FAIL ") + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
		if (!ok) {
			failures++;
		}
	}

	static int run(RagdollBody body, World world, int ticks) {
		for (int t = 0; t < ticks; t++) {
			body.step(world);
			if (!body.sane()) {
				return -1;
			}
			if (body.asleep()) {
				return t;
			}
		}
		return ticks;
	}

	static double worstDepth(RagdollBody b, World w) {
		double worst = 0;
		for (int i = 0; i < b.rig.count; i++) {
			worst = Math.max(worst, w.depth(b.x[i], b.y[i], b.z[i], b.rig.radius[i]));
		}
		return worst;
	}

	static double worstStretch(RagdollBody b) {
		double worst = 0;
		RagdollRig r = b.rig;
		for (int s = 0; s < r.stickA.length; s++) {
			int i = r.stickA[s], j = r.stickB[s];
			double dx = b.x[i] - b.x[j], dy = b.y[i] - b.y[j], dz = b.z[i] - b.z[j];
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			worst = Math.max(worst, Math.abs(d - r.stickLength[s]) / Math.max(r.stickLength[s], 0.1));
		}
		return worst;
	}

	static String f(double v) {
		return String.format("%.3f", v);
	}

	public static void main(String[] args) {
		RagdollRig player = RagdollRig.of(RagdollRig.Shape.HUMANOID, 0.6F, 1.8F);
		RagdollRig cow = RagdollRig.of(RagdollRig.Shape.QUADRUPED, 0.9F, 1.4F);
		RagdollRig chicken = RagdollRig.of(RagdollRig.Shape.BOX, 0.4F, 0.7F);
		RagdollRig giant = RagdollRig.of(RagdollRig.Shape.HUMANOID, 3.6F, 12.0F);

		System.out.println("Standing body collapses where it stood");
		{
			World w = ground();
			RagdollBody b = RagdollBody.standing(player, 0.5, 64, 0.5, 30);
			b.shove(0.05, 0, 0.02);
			int t = run(b, w, 400);
			float[] fr = b.frame();
			check("settles asleep", t >= 0 && t < 400, "ticks " + t);
			check("lies down (torso level)", Math.abs(fr[4]) < 0.35, "up.y " + f(fr[4]));
			check("nothing in the ground", worstDepth(b, w) < 0.03, "depth " + f(worstDepth(b, w)));
			check("bones keep length", worstStretch(b) < 0.05, "stretch " + f(worstStretch(b)));
			double[] m = b.middle();
			check("stays near where it stood", Math.hypot(m[0] - 0.5, m[2] - 0.5) < 1.6, "moved " + f(Math.hypot(m[0] - 0.5, m[2] - 0.5)));
		}

		System.out.println("Thrown hard against a wall");
		{
			World w = ground().add(3, 64, -5, 4, 70, 5);
			RagdollBody b = RagdollBody.standing(player, 0, 64, 0, -90);
			b.shove(2.0, 0.3, 0);
			double furthest = -9;
			for (int t = 0; t < 300 && b.sane(); t++) {
				b.step(w);
				for (int i = 0; i < player.count; i++) {
					furthest = Math.max(furthest, b.x[i]);
				}
			}
			check("never passes through the wall", furthest < 3.0, "furthest x " + f(furthest));
			check("no blow-up", b.sane(), "");
			check("bones keep length", worstStretch(b) < 0.05, "stretch " + f(worstStretch(b)));
		}

		System.out.println("Dropped from 20 blocks");
		{
			World w = ground();
			RagdollBody b = RagdollBody.lying(player, 0, 84, 0, 0, true, 7);
			int t = run(b, w, 600);
			check("lands and settles", t >= 0 && t < 600, "ticks " + t);
			check("lands on top, not through", b.bottom() > 63.9, "bottom " + f(b.bottom()));
			check("nothing in the ground", worstDepth(b, w) < 0.03, "depth " + f(worstDepth(b, w)));
		}

		System.out.println("Laid half over a ledge");
		{
			World w = new World().add(-50, 60, -50, 50, 61, 50).add(-50, 61, -50, 0, 64, 50);
			RagdollBody b = RagdollBody.lying(player, 0.1, 64, 0, -90, true, 3);
			int t = run(b, w, 600);
			check("settles", t >= 0 && t < 600, "ticks " + t);
			check("slides off or drapes over the edge", b.bottom() < 63.5, "bottom " + f(b.bottom()));
			check("nothing in a block", worstDepth(b, w) < 0.04, "depth " + f(worstDepth(b, w)));
		}

		System.out.println("Down a staircase");
		{
			World w = new World().add(-50, 55, -50, 50, 56, 50);
			for (int s = 0; s < 8; s++) {
				w.add(s, 56, -5, s + 1, 64 - s, 5);
			}
			RagdollBody b = RagdollBody.standing(player, 0.5, 64, 0, -90);
			b.shove(0.25, 0, 0);
			run(b, w, 800);
			check("tumbles down some stairs", b.bottom() < 63.5, "bottom " + f(b.bottom()));
			check("nothing in a block", worstDepth(b, w) < 0.05, "depth " + f(worstDepth(b, w)));
			check("bones keep length", worstStretch(b) < 0.05, "stretch " + f(worstStretch(b)));
		}

		System.out.println("Four-legged and small bodies");
		for (RagdollRig rig : new RagdollRig[] {cow, chicken}) {
			World w = ground();
			RagdollBody b = RagdollBody.standing(rig, 0, 64.5, 0, 0);
			b.shove(0.08, 0.1, 0.0);
			int t = run(b, w, 600);
			check(rig.shape + " settles", t >= 0 && t < 600, "ticks " + t);
			check(rig.shape + " nothing in the ground", worstDepth(b, w) < 0.03, "depth " + f(worstDepth(b, w)));
			check(rig.shape + " bones keep length", worstStretch(b) < 0.05, "stretch " + f(worstStretch(b)));
		}

		System.out.println("A giant falls");
		{
			World w = ground();
			RagdollBody b = RagdollBody.standing(giant, 0, 64, 0, 0);
			b.shove(0.0, 0, 0.05);
			int t = run(b, w, 800);
			float[] fr = b.frame();
			check("settles", t >= 0 && t < 800, "ticks " + t);
			check("lies down", Math.abs(fr[4]) < 0.35, "up.y " + f(fr[4]));
			check("nothing in the ground", worstDepth(b, w) < 0.05, "depth " + f(worstDepth(b, w)));
		}

		System.out.println("Floats in water");
		{
			World w = ground();
			w.waterBelow = 70;
			RagdollBody b = RagdollBody.lying(player, 0, 64, 0, 0, true, 1);
			for (int t = 0; t < 200; t++) {
				b.step(w);
			}
			check("rises toward the surface", b.middle()[1] > 67, "middle y " + f(b.middle()[1]));
		}

		System.out.println("Kicked by a walker");
		{
			World w = ground();
			RagdollBody b = RagdollBody.lying(player, 0, 64, 0, 0, true, 5);
			run(b, w, 300);
			double[] before = b.middle();
			w.mover = new double[] {-0.3, 64, -0.3, 0.3, 65.8, 0.3, 0.2, 0, 0};
			for (int t = 0; t < 8; t++) {
				b.step(w);
			}
			w.mover = null;
			run(b, w, 300);
			double moved = Math.hypot(b.middle()[0] - before[0], b.middle()[2] - before[2]);
			check("moves when kicked", moved > 0.02, "moved " + f(moved));
			check("settles again", b.asleep(), "");
		}

		System.out.println("Asleep wakes when the ground goes");
		{
			World w = ground().add(-5, 64, -5, 5, 65, 5);
			RagdollBody b = RagdollBody.lying(player, 0, 65, 0, 0, true, 9);
			run(b, w, 300);
			double was = b.bottom();
			w.boxes.remove(1);
			for (int t = 0; t < 200; t++) {
				b.step(w);
			}
			check("falls to the new ground", b.bottom() < was - 0.5, "bottom " + f(was) + " -> " + f(b.bottom()));
		}

		System.out.println(failures == 0 ? "All good." : failures + " failed.");
		System.exit(failures == 0 ? 0 : 1);
	}
}
