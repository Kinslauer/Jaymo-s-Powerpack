package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.ZFighterSpaceMissions;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * (0.17.62, Andy's NOEA picks) The space pod's star map, opened by right-clicking a pod: Home, the Pale Moon, Rustfall, Glacia, Cinder,
 * the Maw, the Verdant World and the future, one button each with a line about the place or how its mission stands, and, on a planet
 * with a mission, a box to begin or give up the mission and the titles earned. Escape closes it.
 */
public class ZFighterStarMapScreen extends Screen {
	private static final int ROW = 20;
	private static final int PANEL_W = 330;
	private static final int BUTTON_W = 96;
	private static final int ACCENT = 0xFF9FB8FF;
	private static final int GOLD = 0xFFFFD866;

	private final List<Button> buttons = new ArrayList<>();
	private Button begin;
	private Button abandon;

	public ZFighterStarMapScreen() {
		super(Component.translatable("screen.jaymospowers.zfighter_space"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterSpace.Open.TYPE, (payload, ctx) -> ctx.client().gui.setScreen(new ZFighterStarMapScreen()));
	}

	private int rows() {
		return ZFighterSpace.DESTS.length + 1;
	}

	private int panelH() {
		return UiStyle.HEADER + rows() * ROW + 70;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int boxY() {
		return top() + UiStyle.HEADER + rows() * ROW + 6;
	}

	@Override
	protected void init() {
		this.buttons.clear();
		for (int i = 0; i < rows(); i++) {
			int index = i == 0 ? ZFighterSpace.HOME_INDEX : i - 1;
			Component label = Component.translatable("space.jaymospowers.dest." + (index == ZFighterSpace.HOME_INDEX ? "home" : ZFighterSpace.DESTS[index].key()));
			this.buttons.add(addRenderableWidget(Button.builder(label, b -> {
				ClientPlayNetworking.send(new ZFighterSpace.Command(ZFighterSpace.GO, index));
				this.onClose();
			}).bounds(left() + 6, top() + UiStyle.HEADER + i * ROW, BUTTON_W, ROW - 2).build()));
		}
		this.begin = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_space.begin"),
			b -> ClientPlayNetworking.send(new ZFighterSpace.Command(ZFighterSpace.BEGIN_MISSION, 0)))
			.bounds(left() + PANEL_W - 2 * 76 - 12, boxY() + 4, 70, 16).build());
		this.abandon = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_space.abandon"),
			b -> ClientPlayNetworking.send(new ZFighterSpace.Command(ZFighterSpace.ABANDON_MISSION, 0)))
			.bounds(left() + PANEL_W - 76 - 6, boxY() + 4, 70, 16).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Component status(int index, ZFighterSpaceMissions.Data data) {
		int planet = ZFighterSpaceMissions.planetAt(index);
		if (planet < 0) {
			return Component.translatable("space.jaymospowers." + ZFighterSpace.DESTS[index].key() + ".blurb");
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
		return Component.translatable("space.jaymospowers." + ZFighterSpace.DESTS[index].key() + ".blurb");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		int x = left();
		int y = top();
		UiStyle.panel(graphics, x, y, PANEL_W, panelH(), ACCENT);
		UiStyle.header(graphics, this.font, x, y, PANEL_W, null, this.title, Component.translatable("screen.jaymospowers.zfighter_space.sub"), ACCENT, 0);
		ZFighterSpaceMissions.Data data = ZFighterSpaceMissions.of(player);
		ZFighterSpace.Dest here = ZFighterSpace.here(player.level());
		boolean inFuture = here != null && here.level() == ZFighterSpace.DESTS[ZFighterSpace.FUTURE].level();
		for (int i = 0; i < this.buttons.size(); i++) {
			int index = i == 0 ? ZFighterSpace.HOME_INDEX : i - 1;
			Button button = this.buttons.get(i);
			boolean there = index != ZFighterSpace.HOME_INDEX && here == ZFighterSpace.DESTS[index];
			button.active = index == ZFighterSpace.HOME_INDEX || (!there && !inFuture);
			int ry = y + UiStyle.HEADER + i * ROW;
			Component text = index == ZFighterSpace.HOME_INDEX ? Component.translatable("space.jaymospowers.home.blurb") : status(index, data);
			UiStyle.line(graphics, this.font, text, x + BUTTON_W + 14, ry + 5, PANEL_W - BUTTON_W - 22, there ? GOLD : UiStyle.DIM);
		}
		int by = boxY();
		graphics.fill(x + 6, by - 3, x + PANEL_W - 6, by - 2, UiStyle.ROW);
		int planet = ZFighterSpaceMissions.planetOf(player.level());
		boolean onMission = planet >= 0;
		int value = onMission ? data.of(planet) : 0;
		boolean running = ZFighterSpaceMissions.mode(value) != 0;
		this.begin.visible = onMission && !running && !data.settled(planet);
		this.abandon.visible = onMission && running;
		if (onMission) {
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.mission", Component.translatable("space.jaymospowers.dest."
				+ ZFighterSpaceMissions.key(planet))), x + 8, by + 3, PANEL_W - 170, GOLD);
			Component what = data.settled(planet) ? Component.translatable("screen.jaymospowers.zfighter_space.settled")
				: running ? status(ZFighterSpaceMissions.destOf(planet), data) : Component.translatable("screen.jaymospowers.zfighter_space.idle");
			wrapped(graphics, what, x + 8, by + 16, PANEL_W - 16, 2, UiStyle.TEXT);
		} else {
			wrapped(graphics, Component.translatable("screen.jaymospowers.zfighter_space.no_mission"), x + 8, by + 3, PANEL_W - 16, 2, UiStyle.DIM);
		}
		List<Component> titles = ZFighterSpaceMissions.titles(player);
		if (!titles.isEmpty()) {
			StringBuilder joined = new StringBuilder();
			for (Component title : titles) {
				joined.append(joined.length() == 0 ? "" : ", ").append(title.getString());
			}
			UiStyle.line(graphics, this.font, Component.translatable("screen.jaymospowers.zfighter_space.titles", joined.toString()), x + 8, by + 40, PANEL_W - 16, GOLD);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int lines, int color) {
		UiStyle.wrapped(graphics, this.font, text, x, y, width, lines, 9, color, true);
	}
}
