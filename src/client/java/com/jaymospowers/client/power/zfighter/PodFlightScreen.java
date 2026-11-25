package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.pod.PodFlight;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (Patch 9, tr_cockpit; it replaces the old timed flight screen for a pod, which {@code ZFighterFlightScreen} still draws for a
 * crossing on ki) The crossing between worlds, seen from the pod's canopy and flown from it: stars, the world left behind and the
 * world ahead hanging as spheres, rock fields, derelict pods, raiders and their bolts, the Maw off to one side with its pull
 * marked, a Patrol post beside the world ahead. All of it is the server's: this screen draws the last {@link PodFlight.State}
 * (smoothed from the one before) and reports the keys.
 * <p>
 * Keys: A and D turn, W and S pitch, Shift boosts, Ctrl or X brakes, Space fires the ki cannon, F works the mining beam on a
 * rock or strips a derelict pod (hold it), G docks at a post, T switches the autopilot (any turn takes the stick back).
 */
public class PodFlightScreen extends Screen {
	// ---- tuning ----
	/** How wide the view is (the focal length as a share of the height), and how near (blocks) a thing is before it is behind the glass. */
	static final float FOCAL = 0.95F;
	static final double NEAR = 1.0;
	/** Dust specks: the size of the cells they are scattered in (blocks), how many cells either way, and how long a speck's streak is at full speed (pixels). */
	static final double DUST_CELL = 70.0;
	static final int DUST_REACH = 3;
	static final float STREAK = 10.0F;
	/** Stars on the sky of the crossing. */
	static final int STARS = 220;
	/** How long (ticks) a line of notice stays up, and a shot's flash. */
	static final int NOTICE_TICKS = 90, SHOT_TICKS = 4;
	/** Where the other worlds hang on the sky (yaw and pitch in degrees, size in pixels), by world index; Home is the last. */
	static final float[][] SKY = {
		{200.0F, 20.0F, 10.0F}, // the moon
		{150.0F, -12.0F, 7.0F}, // Rustfall
		{-120.0F, 10.0F, 8.0F}, // Glacia
		{60.0F, 28.0F, 6.0F}, // Cinder
		{0.0F, 0.0F, 0.0F}, // the Maw (a body of its own, when it is on the way)
		{-60.0F, -20.0F, 9.0F}, // the Verdant World
		{0.0F, 0.0F, 0.0F}, // the future (never)
		{-35.0F, -8.0F, 12.0F}, // the giant
	};
	static final float[] SKY_HOME = {-170.0F, -25.0F, 9.0F};
	/** Where the sun is (yaw, pitch). */
	static final float SUN_YAW = 110.0F, SUN_PITCH = 35.0F;

	private static final Identifier POD = JaymosPowers.id("textures/gui/space_pod_flight.png");
	private static final int POD_W = 48, POD_H = 34;

	private int lastShot = -1, shotFlash, shotAt = -1, lastNotice = -1, noticeAge = NOTICE_TICKS, noticeId, noticeArg;
	private boolean autoWas;
	private int age;
	private int hitFlash;

	public PodFlightScreen() {
		super(Component.translatable("screen.jaymospowers.pod_flight"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		this.age++;
		PodFlight.State state = FlightClient.last;
		if (state == null) {
			return;
		}
		boolean up = InputConstants.isKeyDown(InputConstants.KEY_W) || InputConstants.isKeyDown(InputConstants.KEY_UP);
		boolean down = InputConstants.isKeyDown(InputConstants.KEY_S) || InputConstants.isKeyDown(InputConstants.KEY_DOWN);
		boolean left = InputConstants.isKeyDown(InputConstants.KEY_A) || InputConstants.isKeyDown(InputConstants.KEY_LEFT);
		boolean right = InputConstants.isKeyDown(InputConstants.KEY_D) || InputConstants.isKeyDown(InputConstants.KEY_RIGHT);
		int flags = 0;
		if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)) {
			flags |= PodFlight.BOOST_KEY;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_X)) {
			flags |= PodFlight.BRAKE_KEY;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_SPACE)) {
			flags |= PodFlight.FIRE_KEY;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_F)) {
			flags |= PodFlight.TOOL_KEY;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_G)) {
			flags |= PodFlight.DOCK_KEY;
		}
		boolean auto = InputConstants.isKeyDown(FlightClient.autoCode());
		if (auto && !this.autoWas) {
			flags |= PodFlight.AUTO_KEY;
		}
		this.autoWas = auto;
		FlightClient.send(new PodFlight.Input((right ? 1 : 0) - (left ? 1 : 0), (up ? 1 : 0) - (down ? 1 : 0), flags));
		// what changed since the last tick: a shot, a line to show, a hit
		if (state.shot() != this.lastShot) {
			if (this.lastShot >= 0) {
				this.shotFlash = SHOT_TICKS;
				this.shotAt = state.shotAt();
			}
			this.lastShot = state.shot();
		}
		if (this.shotFlash > 0) {
			this.shotFlash--;
		}
		if (state.notice() != this.lastNotice) {
			this.lastNotice = state.notice();
			if ((state.notice() & 63) != 0) {
				this.noticeId = state.notice() & 63;
				this.noticeArg = state.noticeArg();
				this.noticeAge = 0;
			}
		}
		if (this.noticeAge < NOTICE_TICKS) {
			this.noticeAge++;
		}
		if (this.hitFlash > 0) {
			this.hitFlash--;
		}
		if ((state.flags() & PodFlight.F_HIT) != 0) {
			this.hitFlash = 6;
		}
	}

	// ---------------------------------------------------------------- the view

	/** The camera for this frame. */
	private static final class Cam {
		double x, y, z;
		double fx, fy, fz, rx, ry, rz, ux, uy, uz;
		float focal, cx, cy;
	}

	private @Nullable Cam camera(float a) {
		PodFlight.State s = FlightClient.last;
		if (s == null) {
			return null;
		}
		PodFlight.State p = FlightClient.prev == null ? s : FlightClient.prev;
		float t = Mth.clamp(FlightClient.age + a, 0.0F, 1.0F);
		Cam c = new Cam();
		c.x = Mth.lerp(t, p.x(), s.x());
		c.y = Mth.lerp(t, p.y(), s.y());
		c.z = Mth.lerp(t, p.z(), s.z());
		double yaw = Math.toRadians(Mth.rotLerp(t, p.yaw(), s.yaw())), pitch = Math.toRadians(Mth.lerp(t, p.pitch(), s.pitch()));
		c.fx = Math.sin(yaw) * Math.cos(pitch);
		c.fy = Math.sin(pitch);
		c.fz = Math.cos(yaw) * Math.cos(pitch);
		c.rx = Math.cos(yaw);
		c.ry = 0.0;
		c.rz = -Math.sin(yaw);
		// up = forward x right
		c.ux = c.fy * c.rz - c.fz * c.ry;
		c.uy = c.fz * c.rx - c.fx * c.rz;
		c.uz = c.fx * c.ry - c.fy * c.rx;
		c.focal = this.height * FOCAL;
		c.cx = this.width / 2.0F;
		c.cy = this.height * 0.45F;
		return c;
	}

	/** (screen x, screen y, pixels per block, depth) of the point at offset ({@code dx}, {@code dy}, {@code dz}) from the camera, or null behind it. */
	private static float @Nullable [] project(Cam c, double dx, double dy, double dz) {
		double d = dx * c.fx + dy * c.fy + dz * c.fz;
		if (d < NEAR) {
			return null;
		}
		double sx = (dx * c.rx + dy * c.ry + dz * c.rz) / d, sy = (dx * c.ux + dy * c.uy + dz * c.uz) / d;
		return new float[] {(float)(c.cx + sx * c.focal), (float)(c.cy - sy * c.focal), (float)(c.focal / d), (float)d};
	}

	/** A far thing in the direction (yaw, pitch degrees), on the sky: its screen place, or null behind. */
	private static float @Nullable [] sky(Cam c, float yawDeg, float pitchDeg) {
		double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
		return project(c, Math.sin(yaw) * Math.cos(pitch) * 1000.0, Math.sin(pitch) * 1000.0, Math.cos(yaw) * Math.cos(pitch) * 1000.0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, this.width, this.height, 0xFF02030A);
	}

	/** One thing to draw, with how far it is (the farthest are drawn first). */
	private record Item(float depth, Runnable draw) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		PodFlight.State s = FlightClient.last;
		Cam c = camera(a);
		if (s == null || c == null) {
			return;
		}
		int w = this.width, h = this.height;
		stars(graphics, c, s);
		dust(graphics, c, s);
		float sunX = c.cx + (float)(Math.sin(Math.toRadians(SUN_YAW)) * c.rx + Math.cos(Math.toRadians(SUN_YAW)) * c.rz) * 2000.0F;
		float sunY = c.cy - (float)(Math.sin(Math.toRadians(SUN_PITCH)) * 2000.0);
		List<Item> items = new ArrayList<>();
		// the world left, the world ahead
		world(items, graphics, c, s.from(), 0.0, 0.0, 0.0, PodFlight.WORLD_R, sunX, sunY);
		double ahead = ahead(s);
		world(items, graphics, c, s.to(), 0.0, 0.0, ahead, PodFlight.WORLD_R, sunX, sunY);
		for (int i = 0; i < SKY.length; i++) {
			if (i != s.from() && i != s.to() && SKY[i][2] > 0.0F && ZFighterSpace.charted(s.charts(), i)) {
				far(graphics, c, i, SKY[i]);
			}
		}
		if (s.from() != ZFighterSpace.HOME_INDEX && s.to() != ZFighterSpace.HOME_INDEX) {
			far(graphics, c, ZFighterSpace.HOME_INDEX, SKY_HOME);
		}
		float[] sun = sky(c, SUN_YAW, SUN_PITCH);
		if (sun != null) {
			ZFighterSpaceArt.sun(graphics, sun[0], sun[1], 7.0F);
		}
		PodFlight.Obj target = null;
		for (PodFlight.Obj o : s.objs()) {
			if (o.id() == s.toolTarget()) {
				target = o;
			}
			double dx = o.x() - c.x, dy = o.y() - c.y, dz = o.z() - c.z;
			float[] p = project(c, dx, dy, dz);
			if (p == null) {
				continue;
			}
			PodFlight.Obj obj = o;
			items.add(new Item(p[3], () -> body(graphics, s, obj, p)));
		}
		items.sort(Comparator.comparingDouble((Item i) -> i.depth()).reversed());
		for (Item item : items) {
			item.draw().run();
		}
		beams(graphics, c, s, target);
		if (this.hitFlash > 0) {
			graphics.fill(0, 0, w, h, (this.hitFlash * 12) << 24 | 0xFF2010);
		}
		FlightClient.canopy(graphics, w, h);
		hud(graphics, s);
	}

	/** Where the world ahead is (the way's far end). */
	private static double ahead(PodFlight.State s) {
		// the crossing's length is carried by the progress and where the pod is: ahead is where progress would be 1, plus the arrival ring
		double along = Math.max(1.0E-3, s.progress());
		double start = PodFlight.START;
		double route = (s.z() - start) / along;
		return start + route + PodFlight.ARRIVE_R;
	}

	private void world(List<Item> items, GuiGraphicsExtractor graphics, Cam c, int index, double wx, double wy, double wz, double radius, float lightX, float lightY) {
		float[] p = project(c, wx - c.x, wy - c.y, wz - c.z);
		if (p == null) {
			return;
		}
		float r = Math.min(3000.0F, (float)radius * p[2]);
		items.add(new Item(p[3] + (float)radius, () -> {
			if (r < 1.5F) {
				ZFighterSpaceArt.disc(graphics, p[0], p[1], 1.5F, 0xFFE8F0FF);
			} else {
				ZFighterSpaceArt.world(graphics, index, p[0], p[1], r, lightX, lightY);
			}
		}));
	}

	private void far(GuiGraphicsExtractor graphics, Cam c, int index, float[] at) {
		float[] p = sky(c, at[0], at[1]);
		if (p != null) {
			ZFighterSpaceArt.world(graphics, index, p[0], p[1], at[2], p[0] + 600.0F, p[1] - 400.0F);
		}
	}

	private void stars(GuiGraphicsExtractor graphics, Cam c, PodFlight.State s) {
		for (int k = 0; k < STARS; k++) {
			double yaw = (ZFighterSpaceArt.hash(k, 3, 91) % 3600) / 10.0, pitch = (ZFighterSpaceArt.hash(k, 4, 91) % 1800) / 10.0 - 90.0;
			float[] p = sky(c, (float)yaw, (float)pitch);
			if (p == null) {
				continue;
			}
			int bright = ZFighterSpaceArt.hash(k, 5, 91) % 3;
			int colour = bright == 2 ? 0xFFFFFFFF : bright == 1 ? 0xC0C8D8FF : 0x80A0A8C0;
			graphics.fill(Math.round(p[0]), Math.round(p[1]), Math.round(p[0]) + 1, Math.round(p[1]) + 1, colour);
		}
	}

	/** Dust drifting past, fixed in space: it streams by faster as the pod does, so you feel the speed. */
	private void dust(GuiGraphicsExtractor graphics, Cam c, PodFlight.State s) {
		int ix0 = (int)Math.floor(c.x / DUST_CELL), iy0 = (int)Math.floor(c.y / DUST_CELL), iz0 = (int)Math.floor(c.z / DUST_CELL);
		float streak = Mth.clamp(s.speed() / (float)PodFlight.CRUISE, 0.0F, 3.0F) * STREAK;
		for (int ix = ix0 - DUST_REACH; ix <= ix0 + DUST_REACH; ix++) {
			for (int iy = iy0 - DUST_REACH; iy <= iy0 + DUST_REACH; iy++) {
				for (int iz = iz0 - DUST_REACH; iz <= iz0 + DUST_REACH; iz++) {
					int hh = ZFighterSpaceArt.hash(ix * 31 + iz, iy, 17);
					if (hh % 3 != 0) {
						continue;
					}
					double dx = (ix + (hh >> 3 & 255) / 255.0) * DUST_CELL - c.x, dy = (iy + (hh >> 11 & 255) / 255.0) * DUST_CELL - c.y,
						dz = (iz + (hh >> 19 & 255) / 255.0) * DUST_CELL - c.z;
					float[] p = project(c, dx, dy, dz);
					if (p == null || p[0] < 0 || p[0] > this.width || p[1] < 0 || p[1] > this.height) {
						continue;
					}
					float near = Mth.clamp(p[2] * 12.0F, 0.0F, 1.0F);
					float ex = (p[0] - c.cx) / this.width * streak * near, ey = (p[1] - c.cy) / this.width * streak * near;
					line(graphics, p[0], p[1], p[0] + ex, p[1] + ey, 0x60FFFFFF | (int)(near * 0x90) << 24 & 0xFF000000);
				}
			}
		}
	}

	/** A thing in the crossing. */
	private void body(GuiGraphicsExtractor graphics, PodFlight.State s, PodFlight.Obj o, float[] p) {
		float r = Math.max(1.0F, o.r() * p[2]);
		switch (o.kind()) {
			case PodFlight.ROCK -> {
				boolean mined = o.id() == s.toolTarget();
				ZFighterSpaceArt.disc(graphics, p[0], p[1], r, 0xFF6B6258);
				ZFighterSpaceArt.disc(graphics, p[0] - r * 0.2F, p[1] - r * 0.2F, r * 0.6F, 0xFF8B8276);
				if (mined) {
					ZFighterSpaceArt.ring(graphics, p[0], p[1], r + 3.0F, 0xFFFFD866, 1);
				}
			}
			case PodFlight.WRECK -> {
				pod(graphics, p, o.r() * 2.0F, 0x70000000);
				if (o.f() <= 0.0F) {
					ZFighterSpaceArt.disc(graphics, p[0], p[1], r * 1.2F, 0x50000000);
				} else if (o.id() == s.toolTarget()) {
					ZFighterSpaceArt.ring(graphics, p[0], p[1], r * 1.4F + 3.0F, 0xFF7CFC9A, 1);
				}
			}
			case PodFlight.RAIDER -> {
				pod(graphics, p, o.r() * 2.2F, 0x30FF2020);
				bracket(graphics, p[0], p[1], r * 1.5F + 4.0F, o.f() >= 0.99F ? 0xFFFF7060 : 0xFFFFB040);
			}
			case PodFlight.BOLT -> {
				float rb = Math.max(2.0F, r * 1.5F);
				ZFighterSpaceArt.disc(graphics, p[0], p[1], rb + 2.0F, 0x60FF4020);
				ZFighterSpaceArt.disc(graphics, p[0], p[1], rb, 0xFFFFC060);
			}
			case PodFlight.MAW -> {
				ZFighterSpaceArt.world(graphics, ZFighterSpace.MAW, p[0], p[1], Math.min(r, 2500.0F), p[0] - 600.0F, p[1] - 400.0F);
				// how far its pull reaches, and the band to skim
				float pull = (float)(ZFighterSpace.MAW_PULL_RANGE * 3.0) * p[2], skim = o.r() * 3.0F * p[2];
				if (pull < 3000.0F) {
					ZFighterSpaceArt.ring(graphics, p[0], p[1], pull, 0x40FF8040, 1);
				}
				if (skim < 3000.0F) {
					ZFighterSpaceArt.ring(graphics, p[0], p[1], skim, 0x80FFD866, 1);
				}
			}
			case PodFlight.POST -> {
				ZFighterSpaceArt.disc(graphics, p[0], p[1], r, 0xFFB8C4D8);
				ZFighterSpaceArt.disc(graphics, p[0], p[1], r * 0.55F, 0xFF4A5468);
				graphics.fill(Math.round(p[0] - r * 2.0F), Math.round(p[1]) - 1, Math.round(p[0] + r * 2.0F), Math.round(p[1]) + 1, 0xFFB8C4D8);
				graphics.fill(Math.round(p[0]) - 1, Math.round(p[1] - r * 2.0F), Math.round(p[0]) + 1, Math.round(p[1] + r * 2.0F), 0xFFB8C4D8);
				if ((s.flags() & PodFlight.F_CAN_DOCK) != 0) {
					bracket(graphics, p[0], p[1], r * 2.4F + 4.0F, 0xFF7CFC9A);
				} else if (r < 40.0F) {
					bracket(graphics, p[0], p[1], r * 2.4F + 6.0F, 0x80B8C4D8);
				}
			}
			default -> {
			}
		}
	}

	/** The pod's picture (side on) at a point, as big across as {@code across} blocks, with a tint laid over it. */
	private void pod(GuiGraphicsExtractor graphics, float[] p, float across, int tint) {
		float px = across * p[2];
		if (px < 5.0F) {
			ZFighterSpaceArt.disc(graphics, p[0], p[1], Math.max(1.5F, px * 0.5F), 0xFFC8D0E0);
			return;
		}
		float scale = px / POD_W;
		graphics.pose().pushMatrix();
		graphics.pose().translate(p[0] - px / 2.0F, p[1] - POD_H * scale / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, POD, 0, 0, 0.0F, 0.0F, POD_W, POD_H, POD_W, POD_H);
		graphics.fill(0, 0, POD_W, POD_H, tint);
		graphics.pose().popMatrix();
	}

	private void bracket(GuiGraphicsExtractor graphics, float cx, float cy, float r, int colour) {
		int x0 = Math.round(cx - r), x1 = Math.round(cx + r), y0 = Math.round(cy - r), y1 = Math.round(cy + r);
		int len = Math.max(3, Math.round(r * 0.4F));
		graphics.fill(x0, y0, x0 + len, y0 + 1, colour);
		graphics.fill(x0, y0, x0 + 1, y0 + len, colour);
		graphics.fill(x1 - len, y0, x1, y0 + 1, colour);
		graphics.fill(x1 - 1, y0, x1, y0 + len, colour);
		graphics.fill(x0, y1 - 1, x0 + len, y1, colour);
		graphics.fill(x0, y1 - len, x0 + 1, y1, colour);
		graphics.fill(x1 - len, y1 - 1, x1, y1, colour);
		graphics.fill(x1 - 1, y1 - len, x1, y1, colour);
	}

	/** The cannon's flash and the tool's beam, from the bottom of the glass. */
	private void beams(GuiGraphicsExtractor graphics, Cam c, PodFlight.State s, PodFlight.@Nullable Obj target) {
		float fromX = c.cx, fromY = this.height * 0.78F;
		if (this.shotFlash > 0) {
			float tx = c.cx, ty = c.cy;
			for (PodFlight.Obj o : s.objs()) {
				if (o.id() == this.shotAt) {
					float[] p = project(c, o.x() - c.x, o.y() - c.y, o.z() - c.z);
					if (p != null) {
						tx = p[0];
						ty = p[1];
					}
				}
			}
			int colour = (this.shotFlash * 50 + 40) << 24 | 0xA0D0FF;
			line(graphics, fromX - 14.0F, fromY, tx, ty, colour);
			line(graphics, fromX + 14.0F, fromY, tx, ty, colour);
			if (this.shotAt >= 0) {
				ZFighterSpaceArt.disc(graphics, tx, ty, 4.0F + this.shotFlash, 0xA0FFFFFF);
			}
		}
		if (target != null) {
			float[] p = project(c, target.x() - c.x, target.y() - c.y, target.z() - c.z);
			if (p != null) {
				boolean rock = target.kind() == PodFlight.ROCK;
				int colour = rock ? 0xFFFFD866 : 0xFF7CFC9A;
				line(graphics, fromX, fromY, p[0], p[1], colour);
				float pulse = 2.0F + (float)Math.sin(this.age * 0.6F) * 1.0F;
				ZFighterSpaceArt.disc(graphics, p[0], p[1], pulse, colour);
				int bw = 60;
				UiStyle.bar(graphics, Math.round(c.cx - bw / 2.0F), Math.round(this.height * 0.74F), bw, 4, s.toolProgress(), colour);
			}
		}
	}

	private static void line(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int colour) {
		int steps = Math.max(1, Math.min(400, (int)Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
		for (int i = 0; i <= steps; i++) {
			int x = Math.round(Mth.lerp(i / (float)steps, x0, x1)), y = Math.round(Mth.lerp(i / (float)steps, y0, y1));
			graphics.fill(x, y, x + 1, y + 1, colour);
		}
	}

	// ---------------------------------------------------------------- the gauges

	private void hud(GuiGraphicsExtractor graphics, PodFlight.State s) {
		int w = this.width, h = this.height;
		// the reticle
		int cx = w / 2, cy = Math.round(h * 0.45F);
		graphics.fill(cx - 8, cy, cx - 3, cy + 1, 0xA0FFFFFF);
		graphics.fill(cx + 4, cy, cx + 9, cy + 1, 0xA0FFFFFF);
		graphics.fill(cx, cy - 8, cx + 1, cy - 3, 0xA0FFFFFF);
		graphics.fill(cx, cy + 4, cx + 1, cy + 9, 0xA0FFFFFF);
		// where to
		Component name = Component.translatable("space.jaymospowers.dest." + (s.to() == ZFighterSpace.HOME_INDEX ? "home" : ZFighterSpace.DESTS[s.to()].key()));
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_flight.to", name), w / 2, 12, w - 80, 0xFFFFD866);
		int barW = Math.min(240, w - 120);
		UiStyle.bar(graphics, (w - barW) / 2, 24, barW, 4, s.progress(), 0xFF9FB8FF);
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.pod_flight.eta", Math.max(0, s.eta() / 20)), w / 2, 31, w - 80, UiStyle.DIM);
		boolean auto = (s.flags() & PodFlight.F_AUTO) != 0;
		Component mode = Component.translatable(auto ? "screen.jaymospowers.pod_flight.auto_on" : "screen.jaymospowers.pod_flight.auto_off",
			InputConstants.Type.KEYBOARD.getOrCreate(FlightClient.autoCode()).getDisplayName());
		UiStyle.lineCentred(graphics, this.font, mode, w / 2, 41, w - 80, auto ? 0xFF7CFC9A : UiStyle.DIM);
		if ((s.flags() & PodFlight.F_ALERT) != 0 && this.age / 6 % 2 == 0) {
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.pod_flight.alert"), w / 2, 52, w - 80, FlightClient.WARN);
		}
		if ((s.flags() & PodFlight.F_SKIM) != 0) {
			UiStyle.lineCentred(graphics, this.font, Component.translatable((s.flags() & PodFlight.F_BOOST) != 0 ? "screen.jaymospowers.pod_flight.skim"
				: "screen.jaymospowers.pod_flight.pull"), w / 2, 62, w - 80, 0xFFFFB040);
		}
		if ((s.flags() & PodFlight.F_THROWN) != 0) {
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.pod_flight.thrown"), w / 2, 62, w - 80, 0xFF7CFC9A);
		}
		// the gauges over the sill
		int side = Math.max(5, Math.round(w * 0.04F)) + 8;
		int sill = Math.max(26, Math.round(h * 0.13F));
		int y = h - sill + 12, gw = Math.min(110, w / 4);
		FlightClient.gauge(graphics, this.font, side, y, gw, Component.translatable("screen.jaymospowers.pod_flight.speed"),
			s.speed() / (float)PodFlight.BOOST, (s.flags() & PodFlight.F_BOOST) != 0 ? FlightClient.FUEL : FlightClient.LIT);
		FlightClient.gauge(graphics, this.font, side + gw + 10, y, gw, Component.translatable("screen.jaymospowers.pod_flight.boost"), s.boost(), FlightClient.FUEL);
		int rightX = w - side - gw;
		FlightClient.gauge(graphics, this.font, rightX, y, gw, Component.translatable("screen.jaymospowers.pod_flight.hull"), s.hull(), s.hull() < 0.3F ? FlightClient.WARN : 0xFF7CFC9A);
		if ((s.flags() & PodFlight.F_SHIELD) != 0) {
			FlightClient.gauge(graphics, this.font, rightX - gw - 10, y, gw, Component.translatable("screen.jaymospowers.pod_flight.shield"), s.shield(), 0xFF60C0FF);
		}
		// what the keys do, only for what is fitted
		List<Component> keys = new ArrayList<>();
		keys.add(Component.translatable("screen.jaymospowers.pod_flight.keys_fly"));
		if ((s.flags() & PodFlight.F_CANNON) != 0) {
			keys.add(Component.translatable("screen.jaymospowers.pod_flight.keys_fire"));
		}
		keys.add(Component.translatable((s.flags() & PodFlight.F_BEAM) != 0 ? "screen.jaymospowers.pod_flight.keys_beam" : "screen.jaymospowers.pod_flight.keys_strip"));
		if ((s.flags() & PodFlight.F_CAN_DOCK) != 0) {
			keys.add(Component.translatable("screen.jaymospowers.pod_flight.keys_dock"));
		}
		StringBuilder line = new StringBuilder();
		for (Component key : keys) {
			line.append(line.length() == 0 ? "" : "   ").append(key.getString());
		}
		UiStyle.lineCentred(graphics, this.font, Component.literal(line.toString()), w / 2, h - 12, w - 2 * side, UiStyle.DIM);
		if (s.toolTarget() >= 0 && s.rel() > 0.0F) {
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.pod_flight.rel", String.format("%.1f", s.rel() * 20.0F)), w / 2,
				Math.round(h * 0.70F), w - 80, s.rel() <= 0.7F ? 0xFF7CFC9A : 0xFFFFB040);
		}
		// the line to show
		if (this.noticeAge < NOTICE_TICKS) {
			int fade = this.noticeAge > NOTICE_TICKS - 20 ? 255 * (NOTICE_TICKS - this.noticeAge) / 20 : 255;
			UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.pod_flight.notice." + this.noticeId, this.noticeArg),
				w / 2, Math.round(h * 0.62F), w - 80, Math.max(0, fade) << 24 | 0xFFFFD866 & 0xFFFFFF);
		}
	}
}
