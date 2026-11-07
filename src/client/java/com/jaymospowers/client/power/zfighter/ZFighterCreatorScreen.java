package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.zfighter.ZFighterCreator;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSlots;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * (0.18.94, Andy's NOEA pick "Technique creator") The Create screen, opened from the training screen: pick quick (G) or
 * charged (V), name it, pick a shape, a colour and a strength, and make it for TP. Under that, the techniques you've
 * made, to put on their key, on the wheel, or forget. The server checks and does it all ({@link ZFighterCreator}).
 */
public class ZFighterCreatorScreen extends Screen {
	private static final int MAX_W = 340;
	private static final int H = 236;
	private static final int ROW = 14;
	private static final int SWATCH = 14;
	private static final int COLOR_PANEL = 0xD0101018;
	private static final int COLOR_ROW = 0x30FFFFFF;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DIM = 0xFFA8A8B8;
	private static final int COLOR_ACCENT = 0xFF000000 | ZFighterPower.COLOR;
	private static final int COLOR_GOLD = 0xFF000000 | ZFighterPower.SUPER_COLOR;

	/** What's being made (kept between openings). */
	private static boolean charged;
	private static String name = "";
	private static int shape;
	private static int color;
	private static int power = 1;

	private final Screen previous;
	private EditBox nameBox;
	private Button kindButton;
	private Button shapeButton;
	private Button createButton;
	private final List<Button> swatches = new ArrayList<>();
	private final List<Button> useButtons = new ArrayList<>();
	private final List<Button> wheelButtons = new ArrayList<>();
	private final List<Button> forgetButtons = new ArrayList<>();

	public ZFighterCreatorScreen(Screen previous) {
		super(Component.translatable("screen.jaymospowers.zfighter_creator.title"));
		this.previous = previous;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	/** The left edge of the controls, right of their labels. */
	private int fieldX() {
		return left() + 70;
	}

	private int listY() {
		return top() + 146;
	}

	@Override
	protected void init() {
		swatches.clear();
		useButtons.clear();
		wheelButtons.clear();
		forgetButtons.clear();
		int x = left();
		int y = top();
		int w = panelW();
		int fx = fieldX();
		int fw = Math.min(160, x + w - 8 - fx);
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_story.back"), b -> onClose())
			.bounds(x + 8, y + 7, 44, 14).build());
		kindButton = addRenderableWidget(Button.builder(Component.empty(), b -> charged = !charged).bounds(fx, y + 26, fw, 14).build());
		nameBox = addRenderableWidget(new EditBox(this.font, fx, y + 44, fw, 14, Component.translatable("screen.jaymospowers.zfighter_creator.name")));
		nameBox.setMaxLength(ZFighterCreator.NAME_MAX);
		nameBox.setValue(name);
		nameBox.setHint(Component.translatable("screen.jaymospowers.zfighter_creator.name_hint"));
		nameBox.setResponder(s -> name = s);
		shapeButton = addRenderableWidget(Button.builder(Component.empty(), b -> shape = (shape + 1) % ZFighterCreator.SHAPES)
			.bounds(fx, y + 62, fw, 14).build());
		for (int i = 0; i < ZFighterCreator.COLORS.length; i++) {
			int pick = i;
			swatches.add(addRenderableWidget(Button.builder(Component.empty(), b -> color = pick)
				.bounds(fx + i * (SWATCH + 2), y + 98, SWATCH, SWATCH).build()));
		}
		addRenderableWidget(Button.builder(Component.literal("-"), b -> power = Math.max(1, power - 1)).bounds(fx, y + 116, 14, 14).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> power = Math.min(ZFighterCreator.POWER_MAX, power + 1))
			.bounds(fx + 54, y + 116, 14, 14).build());
		createButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_creator.create"), b -> {
			ClientPlayNetworking.send(new ZFighterCreator.Make(charged, name, shape, ZFighterCreator.COLORS[color], power));
			name = "";
			nameBox.setValue("");
		}).bounds(x + w - 62, y + 116, 54, 14).build());
		for (int i = 0; i < 2 * ZFighterCreator.EACH; i++) {
			int ry = listY() + 12 + i * ROW;
			int index = index(i);
			useButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter.equip"),
				b -> send(ZFighterNet.EQUIP_TECHNIQUE, index)).bounds(x + w - 128, ry - 2, 54, 12).build()));
			wheelButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> send(ZFighterNet.WHEEL_SLOT, index))
				.bounds(x + w - 72, ry - 2, 14, 12).build()));
			forgetButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.zfighter_creator.forget"),
				b -> ClientPlayNetworking.send(new ZFighterCreator.Forget(index))).bounds(x + w - 56, ry - 2, 48, 12).build()));
		}
	}

	/** The made technique's place shown on list row {@code row}: the three quick ones, then the three charged. */
	private static int index(int row) {
		return row < ZFighterCreator.EACH ? ZFighterCreator.QUICK_FIRST + row : ZFighterCreator.CHARGED_FIRST + row - ZFighterCreator.EACH;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.previous);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static void send(int action, int index) {
		ClientPlayNetworking.send(new ZFighterNet.Train(action, index));
	}

	private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
		UiStyle.line(graphics, this.font, text, x, y, width, color);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}
		int x = left();
		int y = top();
		int w = panelW();
		int fx = fieldX();
		graphics.fill(x, y, x + w, y + H, COLOR_PANEL);
		graphics.fill(x, y, x + w, y + 2, COLOR_ACCENT);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 6, COLOR_ACCENT);
		graphics.text(this.font, Component.translatable("screen.jaymospowers.zfighter.tp", ZFighterTraining.of(player).tp()), x + w - 90, y + 10, COLOR_TEXT);

		// what's being made
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.kind"), x + 8, y + 29, fx - x - 12, COLOR_DIM);
		kindButton.setMessage(Component.translatable(charged ? "screen.jaymospowers.zfighter_creator.charged" : "screen.jaymospowers.zfighter_creator.quick"));
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.name"), x + 8, y + 47, fx - x - 12, COLOR_DIM);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.shape"), x + 8, y + 65, fx - x - 12, COLOR_DIM);
		String shapeKey = "screen.jaymospowers.zfighter_creator.shape."
			+ (charged ? ZFighterCreator.CHARGED_SHAPE_KEYS : ZFighterCreator.QUICK_SHAPE_KEYS)[shape];
		shapeButton.setMessage(Component.translatable(shapeKey));
		UiStyle.wrapped(graphics, this.font, Component.translatable(shapeKey + ".desc"), fx, y + 79, x + w - 8 - fx, 2, 9, COLOR_DIM, true);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.color"), x + 8, y + 101, fx - x - 12, COLOR_DIM);
		for (int i = 0; i < swatches.size(); i++) {
			Button swatch = swatches.get(i);
			swatch.setMessage(Component.literal(i == color ? "◆" : "■").withColor(0xFF000000 | ZFighterCreator.COLORS[i]));
		}
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.power"), x + 8, y + 119, fx - x - 12, COLOR_DIM);
		graphics.centeredText(this.font, Component.literal(power + "/" + ZFighterCreator.POWER_MAX), fx + 34, y + 119, COLOR_GOLD);
		String blocker = ZFighterCreator.blocker(player, charged, name, power);
		createButton.active = blocker == null;
		Component cost = Component.translatable("screen.jaymospowers.zfighter.tp_cost", player.isCreative() ? 0 : ZFighterCreator.cost(power));
		line(graphics, blocker == null ? cost : Component.translatable(blocker), fx + 74, y + 119, x + w - 66 - fx - 78, blocker == null ? COLOR_TEXT : COLOR_DIM);

		// what's been made
		int ly = listY();
		graphics.fill(x + 6, ly - 4, x + w - 6, ly - 3, COLOR_ROW);
		line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.made"), x + 8, ly, w - 16, COLOR_GOLD);
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		List<Integer> wheel = ZFighterSlots.of(player);
		for (int i = 0; i < 2 * ZFighterCreator.EACH; i++) {
			int index = index(i);
			ZFighterCreator.Made made = ZFighterCreator.made(player, index);
			int ry = ly + 12 + i * ROW;
			Button use = useButtons.get(i);
			Button onWheel = wheelButtons.get(i);
			Button forget = forgetButtons.get(i);
			graphics.fill(x + 6, ry - 3, x + w - 6, ry + 10, i % 2 == 0 ? COLOR_ROW : 0x18FFFFFF);
			graphics.text(this.font, i < ZFighterCreator.EACH ? "G" : "V", x + 10, ry, COLOR_ACCENT);
			if (made == null) {
				line(graphics, Component.translatable("screen.jaymospowers.zfighter_creator.empty"), x + 22, ry, w - 40, COLOR_DIM);
				use.visible = onWheel.visible = forget.visible = false;
				continue;
			}
			line(graphics, ZFighterCreator.name(made), x + 22, ry, 96, COLOR_TEXT);
			line(graphics, Component.translatable(made.shapeKey()).append(" " + made.power()), x + 122, ry, w - 128 - 122 - 4, COLOR_DIM);
			boolean equipped = made.charged() ? data.charged() == index : data.quick() == index;
			use.visible = true;
			use.active = !equipped;
			use.setMessage(Component.translatable(equipped ? "screen.jaymospowers.zfighter.on_key" : "screen.jaymospowers.zfighter.equip",
				made.charged() ? "V" : "G"));
			boolean on = wheel.contains(index);
			onWheel.visible = true;
			onWheel.setMessage(Component.literal(on ? "-" : "+").withColor(on ? COLOR_GOLD : 0xFFFFFFFF));
			forget.visible = true;
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
