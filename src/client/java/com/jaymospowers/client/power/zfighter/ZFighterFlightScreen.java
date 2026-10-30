package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterPodTrip;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.ZFighterSpaceMissions;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.4, Andy) The pod's flight, shown while the server holds you up past the clouds ({@link ZFighterPodTrip}): deep space, the
 * world you left sliding away behind, the pod (the pod item, drawn big) riding its fire across a field of stars that stream past
 * faster as it gets going and slow again near the end, and the world ahead growing until it fills the right side. Under it, where
 * you are going and how long is left. After a first trip, Space (or a click) skips it. It closes itself when the pod drops
 * into the landing (the pod you ride changes) or the world changes under it, or a little after its time runs out.
 */
public class ZFighterFlightScreen extends Screen {
	/** Ticks after its time the screen gives up waiting for the landing. */
	private static final int GRACE_TICKS = 60;
	/** How many stars stream past, and in how many layers. */
	private static final int STARS = 160, LAYERS = 3;
	/** The share of the flight spent getting up to speed, and slowing down at the end. */
	private static final float RAMP = 0.2F;
	/** (0.18.56) The share of the world's growth (0 to 1) at which a Starlit Lens starts showing the zones. */
	private static final float LENS_FROM = 0.45F;

	private final int to;
	private final int ticks;
	private final boolean skippable;
	private final int from;
	/** (0.18.56) A Starlit Lens on the pod's mast: the world ahead is shown with its mission zones. */
	private final boolean lens;
	private int age;
	private boolean skipped;
	private @Nullable Entity vehicle;
	/** How far the stars have streamed (pixels at the slowest layer). */
	private float travelled;
	private final ItemStack pod = new ItemStack(ZFighterSpace.POD_ITEM);

	public ZFighterFlightScreen(int to, int ticks, boolean skippable, int from, boolean lens) {
		super(Component.translatable("screen.jaymospowers.zfighter_flight"));
		this.lens = lens;
		this.to = to;
		this.ticks = Math.max(1, ticks);
		this.skippable = skippable;
		this.from = from;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterPodTrip.Flight.TYPE, (payload, ctx) -> {
			LocalPlayer player = ctx.client().player;
			int from = ZFighterSpace.HOME_INDEX;
			ZFighterSpace.Dest here = player == null ? null : ZFighterSpace.here(player.level());
			for (int i = 0; i < ZFighterSpace.DESTS.length; i++) {
				if (ZFighterSpace.DESTS[i] == here) {
					from = i;
				}
			}
			// (0.18.52) the world flown to (or from, on the way home) shines brighter in the night sky for a while
			com.jaymospowers.client.power.zfighter.sky.SkyPlanets.glow(payload.to() == ZFighterSpace.HOME_INDEX ? from : payload.to());
			ctx.client().gui.setScreen(new ZFighterFlightScreen(payload.to(), payload.ticks(), payload.skippable(), from, payload.lens()));
		});
	}

	@Override
	protected void init() {
		if (this.vehicle == null && this.minecraft.player != null) {
			this.vehicle = this.minecraft.player.getVehicle();
		}
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
		this.travelled += speed(progress()) * 6.0F;
		LocalPlayer player = this.minecraft.player;
		Entity now = player == null ? null : player.getVehicle();
		boolean landed = this.age > 5 && now != null && now != this.vehicle;
		if (player == null || landed || this.age > this.ticks + GRACE_TICKS) {
			this.onClose();
		}
	}

	private float progress() {
		return Mth.clamp(this.age / (float)this.ticks, 0.0F, 1.0F);
	}

	/** How fast the stars stream at {@code p} of the way (0 to 1): up to speed, cruising, slowing down. */
	private static float speed(float p) {
		if (p < RAMP) {
			return 0.15F + 0.85F * (p / RAMP);
		}
		if (p > 1.0F - RAMP) {
			return 0.15F + 0.85F * ((1.0F - p) / RAMP);
		}
		return 1.0F;
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, this.width, this.height, 0xFF02030A);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int w = this.width, h = this.height;
		float p = Mth.clamp((this.age + a) / this.ticks, 0.0F, 1.0F);
		float fast = speed(p);
		stars(graphics, w, h, fast, a);
		// the world left behind, sliding off to the left and shrinking
		float leave = ease(p / 0.45F);
		float fromR = h * (0.42F - 0.22F * leave);
		float fromX = -fromR * 0.35F - leave * (w * 0.5F + fromR);
		if (fromX + fromR > -2) {
			ZFighterSpaceArt.world(graphics, this.from, fromX, h * 0.55F, fromR, w * 1.5F, -h);
		}
		// the world ahead, growing out of a speck on the right
		float come = ease((p - 0.25F) / 0.75F);
		float toR = 2.0F + come * (h * 0.40F - 2.0F);
		float toX = w * (0.92F - 0.04F * come) + toR * 0.45F * come;
		ZFighterSpaceArt.world(graphics, this.to, toX, h * 0.48F, toR, -w * 0.5F, -h);
		if (this.lens && this.to != ZFighterSpace.HOME_INDEX) {
			zones(graphics, toX, h * 0.48F, toR, come);
		}
		// the pod on its fire, bobbing a little, a touch off the middle
		float bob = (float)Math.sin((this.age + a) * 0.25F) * 2.0F;
		float podX = w * (0.38F + 0.08F * ease(p)), podY = h * 0.5F + bob;
		trail(graphics, podX, podY, fast, a);
		graphics.pose().pushMatrix();
		graphics.pose().translate(podX - 24.0F, podY - 24.0F);
		graphics.pose().scale(3.0F, 3.0F);
		graphics.item(this.pod, 0, 0);
		graphics.pose().popMatrix();
		// where to and how long
		Component name = Component.translatable("space.jaymospowers.dest." + (this.to == ZFighterSpace.HOME_INDEX ? "home" : ZFighterSpace.DESTS[this.to].key()));
		UiStyle.lineCentred(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_flight.to", name), w / 2, 18, w - 20, 0xFFFFD866);
		int barW = Math.min(240, w - 40);
		UiStyle.bar(graphics, (w - barW) / 2, h - 30, barW, 4, p, 0xFF9FB8FF);
		int left = Math.max(0, (this.ticks - this.age + 19) / 20);
		Component under = this.skipped ? Component.translatable("screen.jaymospowers.zfighter_flight.skipping")
			: this.skippable ? Component.translatable("screen.jaymospowers.zfighter_flight.left_skip", left)
			: Component.translatable("screen.jaymospowers.zfighter_flight.left", left);
		UiStyle.lineCentred(graphics, this.font, under, w / 2, h - 22, w - 20, UiStyle.DIM);
	}

	/**
	 * (0.18.56) The Starlit Lens: a pip on the world ahead for each mission zone, a ring about one still waiting, a filled pip for
	 * one dealt with. Pips only, no words; they come up as the world grows.
	 */
	private void zones(GuiGraphicsExtractor graphics, float cx, float cy, float r, float come) {
		LocalPlayer player = this.minecraft.player;
		if (player == null || come < LENS_FROM || r < 6.0F) {
			return;
		}
		int[] zones = ZFighterSpaceMissions.zones(player, this.to);
		float fade = Mth.clamp((come - LENS_FROM) / 0.2F, 0.0F, 1.0F);
		int alpha = Math.round(255 * fade) << 24;
		for (int k = 0; k < zones.length; k++) {
			boolean boss = k == zones.length - 1;
			float angle = (ZFighterSpaceArt.hash(this.to, k, 21) % 360) * Mth.DEG_TO_RAD;
			float reach = boss ? 0.0F : 0.35F + (ZFighterSpaceArt.hash(this.to, k, 22) % 40) / 100.0F;
			int x = Math.round(cx + Mth.cos(angle) * r * reach), y = Math.round(cy + Mth.sin(angle) * r * reach);
			int size = boss ? 3 : 2;
			boolean done = zones[k] != 0;
			int colour = alpha | (done ? 0x7CFC9A : boss ? 0xFFD866 : 0xE8F0FF);
			if (done) {
				graphics.fill(x - size, y - size, x + size + 1, y + size + 1, colour);
			} else {
				int pulse = (this.age / 6 + k) % 4 == 0 ? 1 : 0;
				int s = size + 1 + pulse;
				graphics.fill(x - s, y - s, x + s + 1, y - s + 1, colour);
				graphics.fill(x - s, y + s, x + s + 1, y + s + 1, colour);
				graphics.fill(x - s, y - s, x - s + 1, y + s + 1, colour);
				graphics.fill(x + s, y - s, x + s + 1, y + s + 1, colour);
			}
		}
	}

	/** Stars streaming right to left in layers (nearer layers faster and longer), streaks when going fast. */
	private void stars(GuiGraphicsExtractor graphics, int w, int h, float fast, float a) {
		float moved = this.travelled + fast * 6.0F * a;
		for (int k = 0; k < STARS; k++) {
			int layer = k % LAYERS;
			float depth = 1.0F + layer * 1.6F;
			int sx = ZFighterSpaceArt.hash(k, 11, 5) % Math.max(1, w + 60);
			int sy = ZFighterSpaceArt.hash(k, 12, 5) % Math.max(1, h);
			float x = sx - moved * depth;
			x = ((x % (w + 60)) + (w + 60)) % (w + 60) - 30;
			int length = Math.max(1, Math.round(fast * depth * 5.0F));
			int colour = layer == 2 ? 0xFFFFFFFF : layer == 1 ? 0xC0C8D8FF : 0x80A0A8C0;
			graphics.fill(Math.round(x), sy, Math.round(x) + length, sy + 1, colour);
		}
	}

	/** The pod's fire behind it: flickering flame cells, longer at speed. */
	private void trail(GuiGraphicsExtractor graphics, float x, float y, float fast, float a) {
		int n = 10 + Math.round(fast * 14.0F);
		long tick = this.age;
		for (int k = 0; k < n; k++) {
			float back = 14.0F + k * (2.0F + fast * 1.6F);
			int jitter = ZFighterSpaceArt.hash(k, (int)tick, 9) % 7 - 3;
			int size = Math.max(1, 6 - k / 4);
			int colour = k < 4 ? 0xFFFFF0A0 : k < 10 ? 0xE0FFB040 : k < 18 ? 0xA0FF6020 : 0x50A0A0A0;
			int fx = Math.round(x - back), fy = Math.round(y + 6 + jitter * (k / 6.0F) * 0.6F);
			graphics.fill(fx - size / 2, fy - size / 2, fx + (size + 1) / 2, fy + (size + 1) / 2, colour);
		}
	}

	private void skip() {
		if (this.skippable && !this.skipped) {
			this.skipped = true;
			ClientPlayNetworking.send(new ZFighterSpace.Command(ZFighterSpace.SKIP, 0));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_RETURN) {
			skip();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		skip();
		return true;
	}
}
