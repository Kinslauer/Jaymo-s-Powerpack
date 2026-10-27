package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Journeys;
import com.jaymoskingdoms.Wagon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.266) The road screen a player's ride to another settlement goes by on ({@link Journeys}): our own take on
 * Stagecoach's travel screen (nothing of it copied). Drawn by hand in the pack's flat, blocky style: the sky at the hour
 * they set off (sun, moon, stars, clouds), far hills and near ones with trees, the road with its ruts, a fence going by
 * in front, and their ride in the middle of it all: a rickshaw with its puller walking between the shafts, or a carriage
 * in its realm's colours behind a horse with its driver up. The land changes on the way from the look of where they
 * set off to the look of where they're going (grass, snow, sand, cherry blossom). A sign at the top names the place and
 * the time left; "Turn back" gets them down where they started, their fare back. It fades in from black and out to
 * black, and the world fades back in once they're there.
 */
final class TravelScreen extends Screen {
	/** How fast the road goes by (screen pixels a tick, in the scene's own pixel), on foot and behind a horse. */
	static final float RICKSHAW_PACE = 1.4F, CARRIAGE_PACE = 2.4F;
	/** Fading in and out (ticks), and the world fading back in after (ticks). */
	static final int FADE_TICKS = 10, CURTAIN_TICKS = 20;
	/** How tall the screen is in the scene's own pixels (so it's as blocky on any screen); the ride is drawn this many times bigger. */
	static final int SCENE_PIXELS = 120, RIDE_SCALE = 2;

	private static final int SKY_DAY = 0x6FA8FF, SKY_DAY_LOW = 0xC4E4FF, SKY_NIGHT = 0x0B1028, SKY_NIGHT_LOW = 0x26305A, SKY_DUSK = 0xF2A65A;
	private static final int SUN = 0xFFF2A6, MOON = 0xE8ECF4, CLOUD = 0xFFFFFF;
	/** Per land ({@link Journeys#GRASS}, SNOW, SAND, BLOSSOM): far hills, near hills, the ground, leaves, trunks, the road. */
	private static final int[][] LAND = {
		{0x6F9E78, 0x4F8A3A, 0x5E9C3F, 0x2F6B2A, 0x6B4A2B, 0x8B6B44},
		{0xB8C8D8, 0xDCE5EE, 0xEEF3F7, 0x2E4D3A, 0x4E3A26, 0xA89A8A},
		{0xD7B98A, 0xD9C08A, 0xE3CF9C, 0x5C8A3A, 0x5C8A3A, 0xC9A66B},
		{0x8FB08A, 0x6FA35A, 0x76AD52, 0xF2A7C8, 0x5A3A2E, 0x8B6B44}};
	private static final int FAR = 0, NEAR = 1, GROUND = 2, LEAF = 3, TRUNK = 4, ROAD = 5;
	private static final int WOOD = 0x7A5A36, WOOD_DARK = 0x4A3420, TYRE = 0x2C2420, SKIN = 0xC8946A, HAIR = 0x4A2E1A, SHIRT = 0x3AA0A8,
		TROUSERS = 0x3B3F9C, PULLER = 0x9A7B55, PULLER_LEGS = 0x4B3A2A, STRAW = 0xD8C27A, HORSE = 0x7A4E2C, MANE = 0x3A2416, LEATHER = 0x5A3A22,
		GLASS = 0x2A3040, ROOF = 0x3A2A1E, SIGN_TEXT = 0xFFF5E6C8, SIGN_DIM = 0xFFC8B89A;

	/** The trip being shown, or null. */
	private static Journeys.@Nullable Start trip;
	/** Ticks since it came up. */
	private static int elapsed;
	/** Ticks left of the world fading back in afterwards. */
	private static int curtain;
	private static boolean turning;

	private @Nullable Button turnBack;
	/** The scene's own pixel (screen pixels), and the ride's. */
	private int u = 1, ru = 2;
	private float pace;

	private TravelScreen() {
		super(Component.translatable("gui.jaymoskingdoms.journey.title"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Journeys.Start.TYPE, (payload, context) -> {
			trip = payload;
			elapsed = 0;
			curtain = 0;
			turning = false;
			context.client().gui.setScreen(new TravelScreen());
		});
		ClientPlayNetworking.registerGlobalReceiver(Journeys.End.TYPE, (payload, context) -> {
			boolean shown = trip != null;
			trip = null;
			if (context.client().gui.screen() instanceof TravelScreen) {
				context.client().gui.setScreen(null);
			}
			curtain = shown ? payload.arrived() ? CURTAIN_TICKS : CURTAIN_TICKS / 2 : 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (curtain > 0) {
				curtain--;
			}
			if (trip == null || minecraft.player == null) {
				return;
			}
			elapsed++;
			sounds(minecraft, trip, elapsed);
			// (up again if anything took its place while they're still on the road)
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new TravelScreen());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			trip = null;
			curtain = 0;
		});
		HudElementRegistry.addLast(JaymosKingdoms.id("journey_curtain"), TravelScreen::drawCurtain);
	}

	/** Hooves or footsteps on the way, and the creak of the wheels now and then. */
	private static void sounds(Minecraft minecraft, Journeys.Start trip, int t) {
		if (t < FADE_TICKS / 2 || t > trip.ticks()) {
			return;
		}
		float pitch = 0.9F + minecraft.level.getRandom().nextFloat() * 0.2F;
		int land = t < trip.ticks() / 2 ? trip.from() : trip.to();
		if (trip.kind() == Wagon.RICKSHAW) {
			if (t % 7 == 0) {
				SoundEvent step = land == Journeys.SNOW ? SoundEvents.SNOW_STEP : land == Journeys.SAND ? SoundEvents.SAND_STEP : SoundEvents.GRAVEL_STEP;
				play(minecraft, step, pitch, 0.3F);
			}
		} else if (t % 8 == 0 || t % 8 == 3) {
			play(minecraft, t % 8 == 0 ? SoundEvents.HORSE_GALLOP : SoundEvents.HORSE_STEP, pitch, 0.3F);
		}
		if (t % 37 == 0) {
			play(minecraft, SoundEvents.WOOD_STEP, 0.55F + pitch * 0.1F, 0.25F);
		}
	}

	private static void play(Minecraft minecraft, SoundEvent sound, float pitch, float volume) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	/** Black over the world while it fades back in, once they're there. */
	private static void drawCurtain(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (curtain > 0 && trip == null) {
			float a = Mth.clamp((curtain - delta.getGameTimeDeltaPartialTick(false)) / CURTAIN_TICKS, 0.0F, 1.0F);
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int)(a * 255.0F) << 24);
		}
	}

	@Override
	protected void init() {
		this.turnBack = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.journey.turn_back"), b -> {
			if (!turning) {
				turning = true;
				b.active = false;
				ClientPlayNetworking.send(new Journeys.TurnBack());
			}
		}).bounds(this.width - 92, this.height - 26, 84, 18).build());
		this.turnBack.active = !turning;
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Journeys.Start start = trip;
		if (start == null) {
			graphics.fill(0, 0, this.width, this.height, 0xFF000000);
			return;
		}
		float t = elapsed + a;
		float p = Mth.clamp(t / Math.max(1, start.ticks()), 0.0F, 1.0F);
		this.u = Math.max(1, Math.round(this.height / (float)SCENE_PIXELS));
		this.ru = this.u * RIDE_SCALE;
		this.pace = start.kind() == Wagon.RICKSHAW ? RICKSHAW_PACE : CARRIAGE_PACE;
		float s = t * this.pace * this.u;
		float hour = (start.time() + t) % 24000.0F;
		float sun = Mth.cos((hour - 6000.0F) / 24000.0F * Mth.TWO_PI);
		float day = smooth(-0.25F, 0.25F, sun);
		float dusk = Mth.clamp(1.0F - Math.abs(sun) / 0.35F, 0.0F, 1.0F);
		float light = Mth.lerp(day, 0.38F, 1.0F);
		float k = smooth(0.35F, 0.65F, p);
		int horizon = (int)(this.height * 0.66F), groundTop = (int)(this.height * 0.78F);

		sky(graphics, hour, sun, day, dusk, s);
		hills(graphics, s * 0.15F, horizon, this.height * 0.12F, 0.013F, mix(LAND[start.from()][FAR], LAND[start.to()][FAR], k, light * 0.92F), groundTop,
			start.to() == Journeys.SNOW ? k : start.from() == Journeys.SNOW ? 1.0F - k : 0.0F, light);
		float nearShift = s * 0.4F;
		int nearColor = mix(LAND[start.from()][NEAR], LAND[start.to()][NEAR], k, light);
		hills(graphics, nearShift, (int)(this.height * 0.75F), this.height * 0.07F, 0.021F, nearColor, groundTop, 0.0F, light);
		trees(graphics, start, nearShift, light);
		graphics.fill(0, groundTop, this.width, this.height, argb(mix(LAND[start.from()][GROUND], LAND[start.to()][GROUND], k, light)));
		road(graphics, start, s, k, light);
		ride(graphics, start, t, s, k, light);
		fence(graphics, s * 1.5F, light);
		sign(graphics, start, t, p);

		// fading in from black and out to it again
		float fade = t < FADE_TICKS ? 1.0F - t / FADE_TICKS : t > start.ticks() - FADE_TICKS ? (t - (start.ticks() - FADE_TICKS)) / FADE_TICKS : 0.0F;
		fade = Mth.clamp(fade, 0.0F, 1.0F);
		if (fade > 0.0F) {
			graphics.fill(0, 0, this.width, this.height, (int)(fade * 255.0F) << 24);
		}
		if (t >= start.ticks()) {
			graphics.centeredText(this.font, Component.translatable("gui.jaymoskingdoms.journey.arriving"), this.width / 2, this.height / 2 - 4, SIGN_DIM);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	// ---- the scene ----------------------------------------------------------------------------------------------------

	private void sky(GuiGraphicsExtractor graphics, float hour, float sun, float day, float dusk, float s) {
		int horizon = (int)(this.height * 0.78F);
		int top = lerpColor(SKY_NIGHT, SKY_DAY, day);
		int low = lerpColor(lerpColor(SKY_NIGHT_LOW, SKY_DAY_LOW, day), SKY_DUSK, dusk * 0.7F);
		graphics.fillGradient(0, 0, this.width, horizon, argb(top), argb(low));
		// stars, by night
		if (day < 0.5F) {
			int alpha = (int)((1.0F - day * 2.0F) * 220.0F);
			for (int i = 0; i < 60; i++) {
				int x = (int)(hash(i * 3 + 1) * this.width), y = (int)(hash(i * 7 + 2) * this.height * 0.55F);
				int size = hash(i * 11 + 5) > 0.85F ? 2 : 1;
				graphics.fill(x, y, x + size, y + size, alpha << 24 | 0xFFFFFF);
			}
		}
		// the sun, and the moon across the sky from it
		float angle = (hour - 6000.0F) / 24000.0F * Mth.TWO_PI;
		body(graphics, angle, sun, 7 * this.u, SUN);
		body(graphics, angle + Mth.PI, -sun, 5 * this.u, MOON);
		// clouds drifting by
		int cloudAlpha = (int)Mth.lerp(day, 60.0F, 210.0F);
		for (int i = 0; i < 6; i++) {
			int w = (int)((18 + hash(i * 13) * 26) * this.u), h = 3 * this.u;
			float span = this.width + w * 2.0F;
			int x = (int)(((hash(i * 17) * span - s * (0.04F + hash(i * 5) * 0.04F)) % span + span) % span) - w;
			int y = (int)((0.08F + hash(i * 19) * 0.3F) * this.height);
			int c = cloudAlpha << 24 | CLOUD;
			graphics.fill(x, y, x + w, y + h, c);
			graphics.fill(x + w / 5, y - h, x + w * 3 / 5, y, c);
		}
	}

	/** The sun or moon at {@code angle} round the sky (above the horizon when {@code height} > 0). */
	private void body(GuiGraphicsExtractor graphics, float angle, float height, int size, int color) {
		if (height < -0.1F) {
			return;
		}
		int horizon = (int)(this.height * 0.7F);
		int x = (int)(this.width / 2.0F + Mth.sin(angle) * this.width * 0.4F) - size / 2;
		int y = (int)(horizon - height * horizon * 0.85F) - size / 2;
		graphics.fill(x - this.u, y - this.u, x + size + this.u, y + size + this.u, 0x40000000 | color);
		graphics.fill(x, y, x + size, y + size, argb(color));
	}

	/** A range of hills, {@code shift} along, rising {@code amp} above {@code base}, filled down to {@code bottom}; {@code snow} white on the tops. */
	private void hills(GuiGraphicsExtractor graphics, float shift, int base, float amp, float freq, int color, int bottom, float snow, float light) {
		int step = 2 * this.u;
		int cap = snow > 0.05F ? (int)(snow * 255.0F) << 24 | shade(0xF4F8FC, light) : 0;
		for (int x = 0; x < this.width; x += step) {
			int y = hill(x + shift, base, amp, freq);
			graphics.fill(x, y, x + step, bottom, argb(color));
			if (cap != 0) {
				graphics.fill(x, y, x + step, y + 2 * this.u, cap);
			}
		}
	}

	private int hill(float hx, int base, float amp, float freq) {
		hx /= this.u;
		float h = Mth.sin(hx * freq) * 0.5F + Mth.sin(hx * freq * 2.4F + 1.7F) * 0.3F + Mth.sin(hx * freq * 0.5F + 4.0F) * 0.2F;
		return (int)(base - amp * (0.55F + 0.45F * h));
	}

	/** Trees along the near hills: oaks, spruces, cacti or cherries, by the land each one stands in. */
	private void trees(GuiGraphicsExtractor graphics, Journeys.Start start, float shift, float light) {
		float spacing = 34.0F * this.u;
		int first = (int)Math.floor(shift / spacing) - 1, last = (int)Math.ceil((shift + this.width) / spacing) + 1;
		float whole = start.ticks() * this.pace * this.u * 0.4F;
		int base = (int)(this.height * 0.75F);
		for (int i = first; i <= last; i++) {
			if (hash(i * 31 + 7) < 0.3F) {
				continue;
			}
			float wx = i * spacing + hash(i * 37) * spacing * 0.6F;
			int x = (int)(wx - shift);
			int y = hill(wx, base, this.height * 0.07F, 0.021F) + this.u;
			float at = smooth(0.35F, 0.65F, Mth.clamp(wx / Math.max(1.0F, whole), 0.0F, 1.0F));
			int land = hash(i * 41 + 3) < at ? start.to() : start.from();
			tree(graphics, land, x, y, 0.8F + hash(i * 43) * 0.5F, light);
		}
	}

	private void tree(GuiGraphicsExtractor graphics, int land, int x, int y, float size, float light) {
		int u = this.u;
		int leaf = argb(shade(LAND[land][LEAF], light)), trunk = argb(shade(LAND[land][TRUNK], light));
		int h = (int)(10 * size);
		switch (land) {
			case Journeys.SNOW -> {
				// a spruce: tiers narrowing up, snow on them
				graphics.fill(x - u / 2, y - 3 * u, x + u, y, trunk);
				int snow = argb(shade(0xF4F8FC, light));
				for (int tier = 0; tier < 4; tier++) {
					int half = (int)((4 - tier) * 1.4F * size) * u, ty = y - (3 + tier * 3) * u - (int)(tier * size);
					graphics.fill(x - half, ty - 3 * u, x + half + u, ty, leaf);
					graphics.fill(x - half, ty - 3 * u, x + half + u, ty - 2 * u, snow);
				}
			}
			case Journeys.SAND -> {
				// a cactus with its arms up
				graphics.fill(x, y - h * u, x + 2 * u, y, leaf);
				graphics.fill(x - 2 * u, y - h / 2 * u, x, y - (h / 2 - 1) * u, leaf);
				graphics.fill(x - 2 * u, y - (h - 3) * u, x - u, y - h / 2 * u, leaf);
				graphics.fill(x + 2 * u, y - (h / 2 + 2) * u, x + 4 * u, y - (h / 2 + 1) * u, leaf);
				graphics.fill(x + 3 * u, y - (h - 2) * u, x + 4 * u, y - (h / 2 + 2) * u, leaf);
			}
			default -> {
				// an oak, or a cherry in bloom: a trunk and a blocky crown
				graphics.fill(x, y - h / 2 * u, x + 2 * u, y, trunk);
				int w = (int)(5 * size) * u;
				graphics.fill(x - w + u, y - h * u, x + w + u, y - (h / 2 - 1) * u, leaf);
				graphics.fill(x - w / 2 + u, y - (h + 2) * u, x + w / 2 + u, y - h * u, leaf);
				graphics.fill(x - w + u, y - (h / 2 - 1) * u, x + w + u, y - (h / 2 - 2) * u, argb(shade(darker(LAND[land][LEAF]), light)));
			}
		}
	}

	/** The road: edges, ruts going by, a tuft or a stone beside it. */
	private void road(GuiGraphicsExtractor graphics, Journeys.Start start, float s, float k, float light) {
		int top = (int)(this.height * 0.83F), bottom = (int)(this.height * 0.91F);
		int road = mix(LAND[start.from()][ROAD], LAND[start.to()][ROAD], k, light);
		graphics.fill(0, top, this.width, bottom, argb(road));
		graphics.fill(0, top, this.width, top + this.u, argb(darker(road)));
		graphics.fill(0, bottom - this.u, this.width, bottom, argb(darker(road)));
		float spacing = 22.0F * this.u;
		int first = (int)Math.floor(s / spacing) - 1, last = (int)Math.ceil((s + this.width) / spacing) + 1;
		int rut = argb(darker(road)), tuft = argb(darker(mix(LAND[start.from()][GROUND], LAND[start.to()][GROUND], k, light)));
		for (int i = first; i <= last; i++) {
			int x = (int)(i * spacing - s);
			int len = (int)((4 + hash(i * 3) * 8) * this.u);
			graphics.fill(x, top + 3 * this.u, x + len, top + 4 * this.u, rut);
			int x2 = (int)(x + hash(i * 5 + 1) * spacing);
			graphics.fill(x2, bottom - 4 * this.u, x2 + len / 2, bottom - 3 * this.u, rut);
			if (hash(i * 7 + 2) > 0.5F) {
				int tx = (int)(x + hash(i * 9) * spacing);
				graphics.fill(tx, top - 2 * this.u, tx + this.u, top, tuft);
				graphics.fill(tx + this.u, top - 3 * this.u, tx + 2 * this.u, top, tuft);
			}
		}
	}

	/** A fence going by in front, faster than everything. */
	private void fence(GuiGraphicsExtractor graphics, float shift, float light) {
		int top = (int)(this.height * 0.93F);
		int wood = argb(shade(WOOD, light)), dark = argb(shade(WOOD_DARK, light));
		graphics.fill(0, top + this.u, this.width, top + 2 * this.u, wood);
		graphics.fill(0, top + 4 * this.u, this.width, top + 5 * this.u, wood);
		float spacing = 30.0F * this.u;
		int first = (int)Math.floor(shift / spacing) - 1, last = (int)Math.ceil((shift + this.width) / spacing) + 1;
		for (int i = first; i <= last; i++) {
			int x = (int)(i * spacing - shift);
			graphics.fill(x, top - this.u, x + 2 * this.u, this.height, wood);
			graphics.fill(x + 2 * this.u, top - this.u, x + 3 * this.u, this.height, dark);
		}
	}

	// ---- the ride -----------------------------------------------------------------------------------------------------

	private int ox, oy;

	/** One block of the ride, in its own pixels from where it stands (x forward, y up). */
	private void px(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(this.ox + x * this.ru, this.oy - (y + h) * this.ru, this.ox + (x + w) * this.ru, this.oy - y * this.ru, color);
	}

	private void ride(GuiGraphicsExtractor graphics, Journeys.Start start, float t, float s, float k, float light) {
		this.ox = (int)(this.width * 0.4F);
		this.oy = (int)(this.height * 0.885F);
		boolean rickshaw = start.kind() == Wagon.RICKSHAW;
		float bob = Mth.sin(t * (rickshaw ? 0.9F : 1.3F)) * 0.6F;
		int dust = shade(start.to() == Journeys.SNOW && k > 0.5F ? 0xFFFFFF : lighter(mix(LAND[start.from()][ROAD], LAND[start.to()][ROAD], k, 1.0F)), light);
		// a little dust thrown up behind
		for (int i = 0; i < 6; i++) {
			float age = (t * 1.2F + i * 5.0F) % 30.0F;
			int alpha = (int)((1.0F - age / 30.0F) * 120.0F);
			px(graphics, (rickshaw ? -6 : -12) - (int)(age * 0.7F), (int)(age * 0.12F), 1 + (int)(age / 12.0F), 1 + (int)(age / 15.0F), alpha << 24 | dust & 0xFFFFFF);
		}
		float turned = s / this.ru;
		if (rickshaw) {
			rickshaw(graphics, start, t, turned, bob, light);
		} else {
			carriage(graphics, start, t, turned, bob, light);
		}
	}

	private void rickshaw(GuiGraphicsExtractor graphics, Journeys.Start start, float t, float turned, float bob, float light) {
		int wood = argb(shade(WOOD, light)), dark = argb(shade(WOOD_DARK, light)), hood = argb(shade(start.color(), light));
		int lift = Math.round(bob);
		// the puller, walking between the shafts
		float stride = Mth.sin(t * 0.55F) * 1.6F;
		int legs = argb(shade(PULLER_LEGS, light));
		px(graphics, 17 + Math.round(stride), 0, 1, 5, legs);
		px(graphics, 17 - Math.round(stride), 0, 1, 5, legs);
		this.oy -= lift * this.ru / 2;
		px(graphics, 16, 5, 3, 5, argb(shade(PULLER, light)));
		px(graphics, 17, 10, 3, 3, argb(shade(SKIN, light)));
		px(graphics, 16, 13, 6, 1, argb(shade(STRAW, light)));
		px(graphics, 17, 14, 3, 1, argb(shade(STRAW, light)));
		px(graphics, 15, 8, 3, 1, argb(shade(SKIN, light)));
		// the shafts, the seat, the hood in its colours
		px(graphics, 2, 8, 14, 1, wood);
		px(graphics, -6, 8, 9, 1, dark);
		px(graphics, -6, 9, 1, 8, dark);
		px(graphics, -7, 16, 8, 2, hood);
		px(graphics, -6, 18, 5, 1, hood);
		px(graphics, -5, 9, 1, 6, hood);
		// the rider, sat back with their legs out
		px(graphics, -4, 9, 3, 4, argb(shade(SHIRT, light)));
		px(graphics, -1, 9, 4, 2, argb(shade(TROUSERS, light)));
		px(graphics, 2, 6, 1, 3, argb(shade(TROUSERS, light)));
		px(graphics, -4, 13, 3, 3, argb(shade(SKIN, light)));
		px(graphics, -4, 15, 3, 1, argb(shade(HAIR, light)));
		this.oy += lift * this.ru / 2;
		wheel(graphics, -2, 4, turned, argb(shade(TYRE, light)), wood);
	}

	private void carriage(GuiGraphicsExtractor graphics, Journeys.Start start, float t, float turned, float bob, float light) {
		int body = argb(shade(start.color(), light)), trim = argb(shade(darker(start.color()), light)), wood = argb(shade(WOOD, light));
		int horse = argb(shade(HORSE, light)), mane = argb(shade(MANE, light)), leather = argb(shade(LEATHER, light));
		// the horse at a gallop
		float gait = t * 0.7F;
		int[] legX = {12, 14, 20, 22};
		for (int i = 0; i < 4; i++) {
			float swing = Mth.sin(gait + (i < 2 ? 0.0F : Mth.PI * 0.6F) + i % 2 * 0.8F) * 1.4F;
			px(graphics, legX[i] + Math.round(swing), 0, 1, 5, i % 2 == 0 ? mane : horse);
			px(graphics, legX[i] + Math.round(swing * 0.5F), 4, 1, 4, horse);
		}
		int lift = Math.round(Mth.sin(gait * 2.0F) * 0.6F);
		this.oy -= lift * this.ru;
		px(graphics, 11, 8, 12, 5, horse);
		px(graphics, 21, 12, 3, 5, horse);
		px(graphics, 22, 16, 5, 3, horse);
		px(graphics, 26, 16, 1, 1, mane);
		px(graphics, 20, 13, 2, 5, mane);
		px(graphics, 9, 9, 2, 4, mane);
		// its driver up in the saddle
		px(graphics, 15, 13, 3, 5, trim);
		px(graphics, 15, 18, 3, 3, argb(shade(SKIN, light)));
		px(graphics, 14, 21, 5, 1, argb(shade(ROOF, light)));
		px(graphics, 15, 22, 3, 1, argb(shade(ROOF, light)));
		this.oy += lift * this.ru;
		// the traces, the carriage in its realm's colours, the rider at the window
		int sway = Math.round(bob);
		px(graphics, 4, 9, 8, 1, leather);
		this.oy -= sway * this.ru / 2;
		px(graphics, -11, 4, 15, 9, body);
		px(graphics, -11, 4, 15, 1, trim);
		px(graphics, -11, 12, 15, 1, trim);
		px(graphics, -12, 13, 17, 2, argb(shade(ROOF, light)));
		px(graphics, -7, 7, 6, 4, argb(shade(GLASS, light)));
		px(graphics, -6, 7, 3, 3, argb(shade(SKIN, light)));
		px(graphics, -6, 10, 3, 1, argb(shade(HAIR, light)));
		px(graphics, 1, 6, 1, 6, trim);
		if (start.kind() == Wagon.COACH) {
			// a coach's luggage up top
			px(graphics, -10, 15, 7, 2, wood);
			px(graphics, -2, 15, 4, 3, argb(shade(LEATHER, light)));
		}
		this.oy += sway * this.ru / 2;
		int tyre = argb(shade(TYRE, light));
		wheel(graphics, -8, 4, turned, tyre, wood);
		wheel(graphics, 1, 3, turned, tyre, wood);
	}

	/** A spoked wheel, its middle at {@code cx}, {@code r} across, turned as far as it's rolled. */
	private void wheel(GuiGraphicsExtractor graphics, int cx, int r, float rolled, int tyre, int spoke) {
		for (int dy = -r; dy <= r; dy++) {
			int outer = (int)Math.round(Math.sqrt(r * r - dy * dy));
			int inner = Math.abs(dy) >= r - 1 ? -1 : (int)Math.round(Math.sqrt((r - 1) * (r - 1) - dy * dy));
			if (inner < 0) {
				px(graphics, cx - outer, r + dy, outer * 2 + 1, 1, tyre);
			} else {
				px(graphics, cx - outer, r + dy, outer - inner, 1, tyre);
				px(graphics, cx + inner + 1, r + dy, outer - inner, 1, tyre);
			}
		}
		float angle = -rolled / r;
		for (int i = 0; i < 4; i++) {
			float a = angle + i * Mth.PI / 4.0F;
			for (int d = -(r - 1); d <= r - 1; d++) {
				px(graphics, cx + Math.round(Mth.cos(a) * d), r + Math.round(Mth.sin(a) * d), 1, 1, spoke);
			}
		}
		px(graphics, cx, r, 1, 1, tyre);
	}

	// ---- the sign -----------------------------------------------------------------------------------------------------

	/** A wooden board at the top: where to, how far, how long; and how far along under it. */
	private void sign(GuiGraphicsExtractor graphics, Journeys.Start start, float t, float p) {
		Component to = Component.translatable("gui.jaymoskingdoms.journey.to", start.place());
		int left = Math.max(0, (int)Math.ceil((start.ticks() - t) / 20.0F));
		Component how = Component.translatable("gui.jaymoskingdoms.journey.left", start.blocks(), left);
		int w = Math.max(this.font.width(to), this.font.width(how)) + 24, h = 30;
		int x0 = (this.width - w) / 2, y0 = 10;
		graphics.fill(x0 + 10, y0 + h, x0 + 13, y0 + h + 8, 0xFF000000 | WOOD_DARK);
		graphics.fill(x0 + w - 13, y0 + h, x0 + w - 10, y0 + h + 8, 0xFF000000 | WOOD_DARK);
		graphics.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF000000 | WOOD_DARK);
		graphics.fill(x0, y0, x0 + w, y0 + h, 0xFF000000 | WOOD);
		graphics.fill(x0, y0 + h / 2, x0 + w, y0 + h / 2 + 1, 0x30000000);
		graphics.centeredText(this.font, to, this.width / 2, y0 + 5, SIGN_TEXT);
		graphics.centeredText(this.font, how, this.width / 2, y0 + 18, SIGN_DIM);
		int bx = x0 + 8, bw = w - 16, by = y0 + h + 12;
		Ui.bar(graphics, bx, by, bw, 3, p, Ui.GOLD);
		int at = bx + Math.round(bw * p);
		graphics.fill(at - 2, by - 2, at + 2, by + 5, 0xFF000000 | WOOD_DARK);
		graphics.fill(bx + bw - 1, by - 6, bx + bw, by + 3, 0xFF000000 | WOOD_DARK);
		graphics.fill(bx + bw, by - 6, bx + bw + 5, by - 3, Ui.GOLD);
	}

	// ---- colour -------------------------------------------------------------------------------------------------------

	private static float smooth(float from, float to, float x) {
		float f = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
		return f * f * (3.0F - 2.0F * f);
	}

	/** Between two lands' colours, {@code k} of the way, dimmed by {@code light}. */
	private static int mix(int a, int b, float k, float light) {
		return shade(lerpColor(a, b, k), light);
	}

	private static int lerpColor(int a, int b, float k) {
		int r = (int)Mth.lerp(k, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int)Mth.lerp(k, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int)Mth.lerp(k, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}

	/** Dimmed toward night: a little blue kept in it. */
	private static int shade(int c, float light) {
		int r = (int)((c >> 16 & 0xFF) * light), g = (int)((c >> 8 & 0xFF) * light), b = (int)((c & 0xFF) * Math.min(1.0F, light * 1.15F));
		return r << 16 | g << 8 | b;
	}

	private static int darker(int c) {
		return shade(c, 0.78F);
	}

	private static int lighter(int c) {
		return lerpColor(c, 0xFFFFFF, 0.3F);
	}

	private static int argb(int rgb) {
		return 0xFF000000 | rgb;
	}

	/** A steady 0..1 for {@code i}, the same every frame. */
	private static float hash(int i) {
		int h = i * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFFFF) / (float)0x1000000;
	}
}
