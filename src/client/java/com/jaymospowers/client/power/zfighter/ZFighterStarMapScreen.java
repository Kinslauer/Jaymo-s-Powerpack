package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.ZFighterSpaceMissions;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.62, Andy's NOEA picks) The space pod's star map, opened by right-clicking a pod. (0.18.4, Andy) Now a solar system: the sun
 * in the middle, Cinder, Home (with the Pale Moon going round it), Rustfall, Glacia and the Verdant World slowly turning on their
 * orbits, and the Maw burning off in the corner. Click a world for its card: everything about it (the whole text, never cut), its
 * gravity, how its mission stands, the fuel the trip burns against what the pod holds, and Launch; on a world with a mission, the
 * card of the world you are on also begins or gives up the mission. The future is not on the map, and a pod in the future shows
 * the map dark. The whole panel shrinks to fit small screens. Escape closes it.
 */
public class ZFighterStarMapScreen extends Screen {
	private static final int MAP = 200;
	private static final int CARD_W = 172;
	private static final int PANEL_W = 6 + MAP + 8 + CARD_W + 6;
	private static final int PANEL_H = UiStyle.HEADER + MAP + 8;
	private static final int ACCENT = 0xFF9FB8FF;
	private static final int GOLD = 0xFFFFD866;
	private static final int RED = 0xFFFF7060;
	private static final int BUTTON_H = 16;
	/** Each world's orbit (pixels from the sun), size, how fast it goes round (turns a minute) and where it starts (turns). */
	private static final float[][] ORBITS = {
		// index: radius, size, speed, start
		{0, 3, 4.0F, 0.1F}, // the moon, round Home
		{64, 6, 0.30F, 0.55F}, // Rustfall
		{80, 6, 0.22F, 0.85F}, // Glacia
		{26, 5, 0.65F, 0.30F}, // Cinder
		{0, 8, 0, 0}, // the Maw, still, in the corner
		{94, 7, 0.16F, 0.15F}, // the Verdant World
	};
	private static final float[] HOME_ORBIT = {46, 7, 0.42F, 0.70F};
	private static final float MOON_ORBIT = 11.0F;
	/** The map's order of worlds: Home first, then the destinations (the future left off). */
	private static final int[] BODIES = {ZFighterSpace.HOME_INDEX, ZFighterSpace.CINDER, ZFighterSpace.MOON, ZFighterSpace.RUSTFALL, ZFighterSpace.GLACIA,
		ZFighterSpace.VERDANT, ZFighterSpace.MAW};

	private final int fuel;
	/** (0.18.14) The grade of the fuel in the tank (0: empty). */
	private final int grade;
	private int picked;
	private float scale = 1.0F;
	private int ox, oy;

	public ZFighterStarMapScreen(int fuel, int grade) {
		super(Component.translatable("screen.jaymospowers.zfighter_space"));
		this.fuel = fuel;
		this.grade = grade;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSpace.Open.TYPE,
			(payload, ctx) -> ctx.client().gui.setScreen(new ZFighterStarMapScreen(payload.fuel(), payload.grade())));
	}

	@Override
	protected void init() {
		this.scale = Math.min(1.0F, Math.min((this.width - 8) / (float)PANEL_W, (this.height - 8) / (float)PANEL_H));
		this.ox = Math.round((this.width - PANEL_W * this.scale) / 2.0F);
		this.oy = Math.round((this.height - PANEL_H * this.scale) / 2.0F);
		LocalPlayer player = this.minecraft.player;
		ZFighterSpace.Dest here = player == null ? null : ZFighterSpace.here(player.level());
		this.picked = ZFighterSpace.MOON;
		if (here != null) {
			for (int i = 0; i < ZFighterSpace.DESTS.length; i++) {
				if (ZFighterSpace.DESTS[i] == here && i != ZFighterSpace.FUTURE) {
					this.picked = i;
				}
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- where things are (in panel pixels)

	private float time() {
		return (System.currentTimeMillis() % 3_600_000L) / 60_000.0F;
	}

	private float[] at(int index) {
		float cx = 6 + MAP / 2.0F, cy = UiStyle.HEADER + MAP / 2.0F;
		if (index == ZFighterSpace.MAW) {
			return new float[] {cx + 78, cy - 76, ORBITS[ZFighterSpace.MAW][1]};
		}
		float[] home = place(HOME_ORBIT, cx, cy);
		if (index == ZFighterSpace.HOME_INDEX) {
			return home;
		}
		if (index == ZFighterSpace.MOON) {
			double a = Math.PI * 2.0 * (ORBITS[ZFighterSpace.MOON][3] + time() * ORBITS[ZFighterSpace.MOON][2]);
			return new float[] {home[0] + (float)Math.cos(a) * MOON_ORBIT, home[1] + (float)Math.sin(a) * MOON_ORBIT, ORBITS[ZFighterSpace.MOON][1]};
		}
		return place(ORBITS[index], cx, cy);
	}

	private float[] place(float[] orbit, float cx, float cy) {
		double a = Math.PI * 2.0 * (orbit[3] + time() * orbit[2]);
		return new float[] {cx + (float)Math.cos(a) * orbit[0], cy + (float)Math.sin(a) * orbit[0], orbit[1]};
	}

	private int bodyAt(double mx, double my) {
		int best = Integer.MIN_VALUE;
		double bestD = Double.MAX_VALUE;
		for (int index : BODIES) {
			float[] p = at(index);
			double dx = mx - p[0], dy = my - p[1];
			double d = dx * dx + dy * dy;
			double reach = p[2] + 5.0;
			if (d < reach * reach && d < bestD) {
				bestD = d;
				best = index;
			}
		}
		return best;
	}

	private int cardX() {
		return 6 + MAP + 8;
	}

	/** The card's action buttons: launch, and begin or give up a mission. */
	private int[] launchBox() {
		return new int[] {cardX(), PANEL_H - 8 - BUTTON_H, CARD_W, BUTTON_H};
	}

	private int[] missionBox() {
		return new int[] {cardX(), PANEL_H - 8 - BUTTON_H * 2 - 15, CARD_W, BUTTON_H};
	}

	// ---------------------------------------------------------------- what the card says

	private boolean inFuture(LocalPlayer player) {
		ZFighterSpace.Dest here = ZFighterSpace.here(player.level());
		return here != null && here.level() == ZFighterSpace.DESTS[ZFighterSpace.FUTURE].level();
	}

	private boolean isHere(LocalPlayer player, int index) {
		ZFighterSpace.Dest here = ZFighterSpace.here(player.level());
		if (index == ZFighterSpace.HOME_INDEX) {
			return here == null;
		}
		return here == ZFighterSpace.DESTS[index];
	}

	private String key(int index) {
		return index == ZFighterSpace.HOME_INDEX ? "home" : ZFighterSpace.DESTS[index].key();
	}

	/** Why the pod can't fly to {@code index} now, or null if it can. */
	private @Nullable Component blocked(LocalPlayer player, int index) {
		if (inFuture(player)) {
			return Component.translatable("screen.jaymospowers.zfighter_space.dead");
		}
		if (index != ZFighterSpace.HOME_INDEX && isHere(player, index)) {
			return Component.translatable("screen.jaymospowers.zfighter_space.you_are_here");
		}
		int cost = cost(player, index);
		if (this.fuel < cost) {
			return Component.translatable("screen.jaymospowers.zfighter_space.short", cost - this.fuel);
		}
		if (tooCoarse(player, index)) {
			return Component.translatable("screen.jaymospowers.zfighter_space.grade_short", PodFuel.roman(PodFuel.needed(index)));
		}
		return null;
	}

	/** (0.18.14) Whether the tank's fuel is too poor a grade to reach {@code index} (a creative pilot needs no grade). */
	private boolean tooCoarse(LocalPlayer player, int index) {
		return !player.isCreative() && this.grade < PodFuel.needed(index);
	}

	private int cost(LocalPlayer player, int index) {
		return player.isCreative() ? 0 : ZFighterSpace.cost(index, player.level().dimension());
	}

	private Component status(int index, ZFighterSpaceMissions.Data data) {
		int planet = ZFighterSpaceMissions.planetAt(index);
		if (planet < 0) {
			return Component.empty();
		}
		if (data.freedPlanet(planet)) {
			return ZFighterSpaceMissions.title(planet, ZFighterSpaceMissions.FREE);
		}
		if (data.conqueredPlanet(planet)) {
			return ZFighterSpaceMissions.title(planet, ZFighterSpaceMissions.CONQUER);
		}
		int value = data.of(planet);
		if (ZFighterSpaceMissions.mode(value) != 0) {
			boolean free = ZFighterSpaceMissions.mode(value) == ZFighterSpaceMissions.FREE;
			if (ZFighterSpaceMissions.phase(value) == 0) {
				return Component.translatable(free ? "screen.jaymospowers.zfighter_space.captains" : "screen.jaymospowers.zfighter_space.champions",
					ZFighterSpaceMissions.count(value), ZFighterSpaceMissions.CAPTAINS);
			}
			return Component.translatable(free ? "screen.jaymospowers.zfighter_space.boss" : "screen.jaymospowers.zfighter_space.flag");
		}
		return Component.translatable("screen.jaymospowers.zfighter_space.mission_open");
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		double mx = (mouseX - this.ox) / this.scale, my = (mouseY - this.oy) / this.scale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.ox, this.oy);
		graphics.pose().scale(this.scale, this.scale);
		boolean dark = inFuture(player);
		UiStyle.panel(graphics, 0, 0, PANEL_W, PANEL_H, ACCENT);
		UiStyle.header(graphics, this.font, 0, 0, PANEL_W, null, this.title,
			Component.translatable(dark ? "screen.jaymospowers.zfighter_space.dead" : "screen.jaymospowers.zfighter_space.sub"), ACCENT, PANEL_W - 110);
		fuelGauge(graphics);
		map(graphics, player, mx, my, dark);
		card(graphics, player, mx, my, dark);
		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void fuelGauge(GuiGraphicsExtractor graphics) {
		int right = PANEL_W - 8;
		UiStyle.lineRight(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.fuel_grade", this.fuel, ZFighterSpace.MAX_FUEL,
			PodFuel.roman(this.grade)), right, 7, 96, GOLD);
		UiStyle.bar(graphics, right - 96, 19, 96, 5, this.fuel / (float)ZFighterSpace.MAX_FUEL, 0xFFFF9A30);
	}

	private void map(GuiGraphicsExtractor graphics, LocalPlayer player, double mx, double my, boolean dark) {
		int x0 = 6, y0 = UiStyle.HEADER;
		graphics.fill(x0, y0, x0 + MAP, y0 + MAP, 0xFF05060E);
		ZFighterSpaceArt.stars(graphics, x0, y0, MAP, MAP, 90, 4242);
		float cx = x0 + MAP / 2.0F, cy = y0 + MAP / 2.0F;
		if (dark) {
			// a lifetime on: the sun gone cold, nothing on the orbits
			ZFighterSpaceArt.disc(graphics, cx, cy, 8, 0xFF3A1810);
			return;
		}
		graphics.enableScissor(x0, y0, x0 + MAP, y0 + MAP);
		for (float[] orbit : new float[][] {ORBITS[ZFighterSpace.CINDER], HOME_ORBIT, ORBITS[ZFighterSpace.RUSTFALL], ORBITS[ZFighterSpace.GLACIA],
			ORBITS[ZFighterSpace.VERDANT]}) {
			ZFighterSpaceArt.orbit(graphics, cx, cy, orbit[0], 0x40A0B0FF);
		}
		ZFighterSpaceArt.sun(graphics, cx, cy, 9);
		int hover = bodyAt(mx, my);
		for (int index : BODIES) {
			float[] p = at(index);
			ZFighterSpaceArt.world(graphics, index, p[0], p[1], p[2], cx, cy);
			if (index != ZFighterSpace.HOME_INDEX && !isHere(player, index) && tooCoarse(player, index)) {
				// (0.18.14) the tank's fuel can't reach it: greyed over
				ZFighterSpaceArt.disc(graphics, p[0], p[1], p[2], 0xB0101018);
			}
			if (index == this.picked) {
				ZFighterSpaceArt.ring(graphics, p[0], p[1], p[2] + 3.0F, GOLD, 1);
			} else if (index == hover) {
				ZFighterSpaceArt.ring(graphics, p[0], p[1], p[2] + 3.0F, 0xA0FFFFFF, 1);
			}
			if (isHere(player, index)) {
				// you are here: a small marker over the world
				int tx = Math.round(p[0]), ty = Math.round(p[1] - p[2] - 7);
				graphics.fill(tx - 2, ty, tx + 3, ty + 1, 0xFF7CFF7C);
				graphics.fill(tx - 1, ty + 1, tx + 2, ty + 2, 0xFF7CFF7C);
				graphics.fill(tx, ty + 2, tx + 1, ty + 3, 0xFF7CFF7C);
			}
		}
		graphics.disableScissor();
		if (hover != Integer.MIN_VALUE) {
			float[] p = at(hover);
			UiStyle.lineCentred(graphics, this.font, Component.translatable("space.jaymospowers.dest." + key(hover)), Math.round(p[0]),
				Math.round(p[1] + p[2] + 4), 90, UiStyle.TEXT);
		}
	}

	private void card(GuiGraphicsExtractor graphics, LocalPlayer player, double mx, double my, boolean dark) {
		int x = cardX(), y = UiStyle.HEADER, w = CARD_W;
		int index = this.picked;
		boolean here = isHere(player, index);
		ZFighterSpaceMissions.Data data = ZFighterSpaceMissions.of(player);
		// the world, big
		graphics.fill(x, y, x + w, y + 46, 0xFF05060E);
		ZFighterSpaceArt.stars(graphics, x, y, w, 46, 22, 77 + index);
		ZFighterSpaceArt.world(graphics, index, x + 26, y + 23, 18, x - 20, y);
		UiStyle.line(graphics, this.font, Component.translatable("space.jaymospowers.dest." + key(index)), x + 50, y + 8, w - 54, here ? GOLD : ACCENT);
		UiStyle.line(graphics, this.font, Component.translatable("space.jaymospowers." + key(index) + ".gravity"), x + 50, y + 20, w - 54, UiStyle.DIM);
		if (here) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.you_are_here"), x + 50, y + 32, w - 54, 0xFF7CFF7C);
		} else if (!dark) {
			// (0.18.14) the grade of fuel the trip needs
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.needs_grade", PodFuel.roman(PodFuel.needed(index))), x + 50,
				y + 32, w - 54, tooCoarse(player, index) ? RED : UiStyle.DIM);
		}
		int ty = y + 52;
		ty += 2 + 9 * UiStyle.wrapped(graphics, this.font, Component.translatable("space.jaymospowers." + key(index) + ".about"), x + 2, ty, w - 8, 7, 9,
			UiStyle.TEXT, true);
		int planet = index == ZFighterSpace.HOME_INDEX ? -1 : ZFighterSpaceMissions.planetAt(index);
		if (planet >= 0) {
			UiStyle.line(graphics, this.font, status(index, data), x + 2, ty, w - 6, GOLD);
			ty += 11;
		}
		List<Component> titles = ZFighterSpaceMissions.titles(player);
		if (!titles.isEmpty() && index == ZFighterSpace.HOME_INDEX) {
			StringBuilder joined = new StringBuilder();
			for (Component title : titles) {
				joined.append(joined.length() == 0 ? "" : ", ").append(title.getString());
			}
			UiStyle.wrapped(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.titles", joined.toString()), x + 2, ty, w - 8, 2, 9,
				GOLD, true);
		}
		// the mission here: begin or give up
		int[] mbox = missionBox();
		if (here && planet >= 0 && !data.settled(planet)) {
			boolean running = ZFighterSpaceMissions.mode(data.of(planet)) != 0;
			button(graphics, mbox, Component.translatable(running ? "screen.jaymospowers.zfighter_space.abandon" : "screen.jaymospowers.zfighter_space.begin"),
				true, UiStyle.over((int)mx, (int)my, mbox[0], mbox[1], mbox[2], mbox[3]));
		}
		// the fuel and the launch
		int[] box = launchBox();
		Component why = blocked(player, index);
		int cost = cost(player, index);
		UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.burns", cost, this.fuel), x + 2, box[1] - 11, w - 6,
			this.fuel >= cost ? UiStyle.DIM : RED);
		if (why == null) {
			button(graphics, box, Component.translatable("screen.jaymospowers.zfighter_space.launch", Component.translatable("space.jaymospowers.dest." + key(index))),
				true, UiStyle.over((int)mx, (int)my, box[0], box[1], box[2], box[3]));
		} else {
			button(graphics, box, why, false, false);
		}
	}

	private void button(GuiGraphicsExtractor graphics, int[] box, Component label, boolean active, boolean hover) {
		int colour = !active ? 0x50202028 : hover ? 0xFF4A5A9A : 0xFF33406E;
		graphics.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], colour);
		graphics.fill(box[0], box[1], box[0] + box[2], box[1] + 1, active ? ACCENT : 0x40FFFFFF);
		UiStyle.lineCentred(graphics, this.font, label, box[0] + box[2] / 2, box[1] + 4, box[2] - 6, active ? UiStyle.TEXT : UiStyle.HINT);
	}

	// ---------------------------------------------------------------- clicks

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		LocalPlayer player = this.minecraft.player;
		if (player == null || event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = (event.x() - this.ox) / this.scale, my = (event.y() - this.oy) / this.scale;
		int hit = inFuture(player) ? Integer.MIN_VALUE : bodyAt(mx, my);
		if (hit != Integer.MIN_VALUE) {
			this.picked = hit;
			click(1.4F);
			return true;
		}
		int[] box = launchBox();
		if (UiStyle.over((int)mx, (int)my, box[0], box[1], box[2], box[3]) && blocked(player, this.picked) == null) {
			click(0.8F);
			ClientPlayNetworking.send(new ZFighterSpace.Command(ZFighterSpace.GO, this.picked));
			this.onClose();
			return true;
		}
		int[] mbox = missionBox();
		int planet = this.picked == ZFighterSpace.HOME_INDEX ? -1 : ZFighterSpaceMissions.planetAt(this.picked);
		if (UiStyle.over((int)mx, (int)my, mbox[0], mbox[1], mbox[2], mbox[3]) && planet >= 0 && isHere(player, this.picked)) {
			ZFighterSpaceMissions.Data data = ZFighterSpaceMissions.of(player);
			if (!data.settled(planet)) {
				boolean running = ZFighterSpaceMissions.mode(data.of(planet)) != 0;
				click(1.0F);
				ClientPlayNetworking.send(new ZFighterSpace.Command(running ? ZFighterSpace.ABANDON_MISSION : ZFighterSpace.BEGIN_MISSION, 0));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void click(float pitch) {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}
}
