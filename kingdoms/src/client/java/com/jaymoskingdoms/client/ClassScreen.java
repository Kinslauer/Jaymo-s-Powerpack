package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0) The class screen ({@link Classes}). (0.7.38) Restyled like the creative H screen: every class down the left
 * (its signature gear and name), and on the right the one you point at or picked: what it's good with
 * ({@link ClassPerks#card}) as striped rows, the gear it's good with ({@link Classes#gear}), a Mage's or a Sorcerer's
 * first spell or element to pick, and a box at the bottom with the class (or the hovered spell) in full. Pressing a
 * class picks it; Choose seals it. Opened by the server the first time a player comes into a world (then it can't be
 * shut without picking) and when a Writ of Rebirth is read.
 */
final class ClassScreen extends Screen {
	static final int MAX_W = 420, MAX_H = 266, LIST_W = 118, LIST_ROW = 20, HEADER = 34, STAT_ROW = 13, CHIP_H = 14, FOOTER = 24, DETAIL_MIN_LINES = 4;

	private final boolean forced;
	/** The class pressed, and (for a Mage or a Sorcerer) the option pressed under it. */
	private @Nullable PlayerClass picked;
	private int option = -1;
	private @Nullable Button choose;
	/** Each class's gear, made once for the screen. */
	private final java.util.Map<PlayerClass, List<ItemStack>> kits = new java.util.EnumMap<>(PlayerClass.class);

	ClassScreen(boolean forced) {
		super(Component.translatable("gui.jaymoskingdoms.class.title"));
		this.forced = forced;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Classes.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new ClassScreen(payload.forced())));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.forced;
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int panelH() {
		return Math.min(MAX_H, this.height - 8);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int paneX() {
		return left() + LIST_W;
	}

	private int paneW() {
		return panelW() - LIST_W;
	}

	private int rowY(int index) {
		return top() + HEADER + index * LIST_ROW;
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Classes.classOf(minecraft.player) != null) {
			onClose();
			return;
		}
		int y = top() + panelH() - FOOTER + 4;
		int right = paneX() + paneW() - 6;
		if (!this.forced) {
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.later"), b -> onClose()).bounds(right - 56, y, 56, 16).build());
			right -= 60;
		}
		this.choose = addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.choose"), b -> {
			if (this.picked != null) {
				ClientPlayNetworking.send(new Classes.Choose(this.picked.ordinal(), this.option));
				onClose();
			}
		}).bounds(right - 110, y, 110, 16).build());
	}

	private @Nullable PlayerClass rowAt(int mouseX, int mouseY) {
		PlayerClass[] all = PlayerClass.values();
		for (int i = 0; i < all.length; i++) {
			int ry = rowY(i);
			if (ry + LIST_ROW > top() + panelH() - 4) {
				break;
			}
			if (Ui.over(mouseX, mouseY, left() + 4, ry, LIST_W - 8, LIST_ROW - 2)) {
				return all[i];
			}
		}
		return null;
	}

	/** The class the right-hand pane shows: the one pointed at, else the one picked, else the first. */
	private PlayerClass viewed(int mouseX, int mouseY) {
		PlayerClass hover = rowAt(mouseX, mouseY);
		return hover != null ? hover : this.picked != null ? this.picked : PlayerClass.values()[0];
	}

	/** Where the option chips sit, as {x, y, w} each (only for the picked class, when it has options). */
	private List<int[]> chips() {
		List<int[]> out = new ArrayList<>();
		if (this.picked == null || this.picked.options.isEmpty()) {
			return out;
		}
		int x0 = paneX() + 9, x = x0, y = optionsY() + 12, right = paneX() + paneW() - 9;
		for (String option : this.picked.options) {
			int w = this.font.width(Component.translatable("class.jaymoskingdoms.option." + option)) + 12;
			if (x + w > right && x > x0) {
				x = x0;
				y += CHIP_H + 3;
			}
			out.add(new int[] {x, y, w});
			x += w + 4;
		}
		return out;
	}

	/** Where the stat rows end and the gear row and options begin. */
	private int gearY(PlayerClass cls) {
		return top() + HEADER + ClassPerks.card(cls).size() * STAT_ROW + 4;
	}

	private int optionsY() {
		return this.picked == null ? 0 : gearY(this.picked) + 24;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int)event.x(), my = (int)event.y();
		PlayerClass row = rowAt(mx, my);
		if (row != null) {
			if (row != this.picked) {
				this.picked = row;
				this.option = -1;
			}
			click();
			return true;
		}
		List<int[]> chips = chips();
		for (int i = 0; i < chips.size(); i++) {
			int[] c = chips.get(i);
			if (Ui.over(mx, my, c[0], c[1], c[2], CHIP_H)) {
				this.option = i;
				click();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Classes.classOf(minecraft.player) != null) {
			onClose();
			return;
		}
		int x = left(), y = top(), w = panelW(), h = panelH();
		Ui.panel(graphics, x, y, w, h, Ui.GOLD);
		list(graphics, mouseX, mouseY);
		PlayerClass cls = viewed(mouseX, mouseY);
		int px = paneX(), pw = paneW();

		// the header: its signature gear, its name, and whether it's yours yet
		List<ItemStack> kit = kit(cls);
		if (!kit.isEmpty() && !kit.getFirst().isEmpty()) {
			graphics.item(kit.getFirst(), px + 8, y + 8);
		}
		Ui.line(graphics, this.font, Component.translatable("class.jaymoskingdoms." + cls.key()), px + 28, y + 7, pw - 36, cls == this.picked ? Ui.GOLD : Ui.TEXT);
		Component sub = cls == this.picked ? Component.translatable("gui.jaymoskingdoms.class.picked")
			: Component.translatable("gui.jaymoskingdoms.class.press");
		Ui.line(graphics, this.font, sub, px + 28, y + 18, pw - 36, Ui.DIM);
		Ui.rule(graphics, px + 6, px + pw - 6, y + HEADER - 4);

		// what it's good with, a row each
		List<Component> stats = ClassPerks.card(cls);
		for (int i = 0; i < stats.size(); i++) {
			int ry = y + HEADER + i * STAT_ROW;
			Ui.row(graphics, px + 6, ry, px + pw - 6, ry + STAT_ROW - 1, i, false);
			Ui.line(graphics, this.font, stats.get(i), px + 10, ry + 2, pw - 20, Ui.TEXT);
		}
		// the gear it's good with, as a row of icons
		int gy = gearY(cls);
		Component goodWith = Component.translatable("gui.jaymoskingdoms.class.gear");
		graphics.text(this.font, goodWith, px + 9, gy + 5, Ui.DIM);
		int gx = px + 9 + this.font.width(goodWith) + 6;
		ItemStack hoveredStack = ItemStack.EMPTY;
		for (ItemStack stack : kit) {
			if (stack.isEmpty() || gx + 18 > px + pw - 6) {
				continue;
			}
			Ui.well(graphics, gx - 1, gy, 18, 18);
			graphics.item(stack, gx, gy + 1);
			if (Ui.over(mouseX, mouseY, gx, gy + 1, 16, 16)) {
				hoveredStack = stack;
			}
			gx += 19;
		}

		// the first spell or element, for the class picked
		String hoveredOption = null;
		int detailTop = gy + 24;
		if (cls == this.picked && !cls.options.isEmpty()) {
			int oy = optionsY();
			graphics.text(this.font, Component.translatable("gui.jaymoskingdoms.class.pick",
				Component.translatable("gui.jaymoskingdoms.class.pick." + cls.key())), px + 9, oy, Ui.DIM);
			List<int[]> chips = chips();
			for (int i = 0; i < chips.size(); i++) {
				int[] c = chips.get(i);
				boolean hover = Ui.over(mouseX, mouseY, c[0], c[1], c[2], CHIP_H);
				boolean chosen = i == this.option;
				graphics.fill(c[0], c[1], c[0] + c[2], c[1] + CHIP_H, hover ? Ui.ROW_HOVER : chosen ? Ui.ROW_PICKED : Ui.CHIP);
				graphics.fill(c[0], c[1] + CHIP_H - 1, c[0] + c[2], c[1] + CHIP_H, chosen ? Ui.GOLD : Ui.ROW);
				graphics.centeredText(this.font, Component.translatable("class.jaymoskingdoms.option." + cls.options.get(i)), c[0] + c[2] / 2, c[1] + 3,
					chosen ? Ui.GOLD : Ui.TEXT);
				if (hover) {
					hoveredOption = cls.options.get(i);
				}
				detailTop = Math.max(detailTop, c[1] + CHIP_H + 4);
			}
		}

		// the detail box: the class (or the hovered spell) in full
		int footer = y + h - FOOTER;
		int lines = Math.max(DETAIL_MIN_LINES, (footer - detailTop - 6) / Ui.LINE);
		List<Component> text = new ArrayList<>();
		Component name;
		if (hoveredOption != null) {
			name = Component.translatable("class.jaymoskingdoms.option." + hoveredOption);
			text.add(Component.translatable("class.jaymoskingdoms.option." + hoveredOption + ".desc"));
		} else {
			name = Component.translatable("class.jaymoskingdoms." + cls.key());
			text.add(Component.translatable("class.jaymoskingdoms." + cls.key() + ".desc"));
			if (this.picked == null) {
				text.add(Component.translatable("gui.jaymoskingdoms.class.lead").withColor(Ui.HINT));
			}
		}
		Ui.detail(graphics, this.font, px, Math.min(detailTop, footer - DETAIL_MIN_LINES * Ui.LINE - 8), pw, lines, name, Ui.GOLD, text);

		if (this.choose != null) {
			this.choose.setMessage(this.picked == null ? Component.translatable("gui.jaymoskingdoms.class.choose")
				: Component.translatable("gui.jaymoskingdoms.class.confirm", Component.translatable("class.jaymoskingdoms." + this.picked.key())));
			this.choose.active = this.picked != null && (this.picked.options.isEmpty() || this.option >= 0);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!hoveredStack.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hoveredStack, mouseX, mouseY);
		}
	}

	private List<ItemStack> kit(PlayerClass cls) {
		return this.kits.computeIfAbsent(cls, c -> Classes.gear(c, c.options.isEmpty() ? "" : c.options.getFirst()));
	}

	/** Every class down the left, the picked one marked. */
	private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left(), y = top();
		Ui.line(graphics, this.font, this.title, x + 8, y + 8, LIST_W - 12, Ui.TEXT);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.class.sub"), x + 8, y + 19, LIST_W - 12, Ui.DIM);
		graphics.fill(x + LIST_W - 1, y + 4, x + LIST_W, y + panelH() - 4, Ui.ROW);
		PlayerClass[] all = PlayerClass.values();
		PlayerClass hover = rowAt(mouseX, mouseY);
		for (int i = 0; i < all.length; i++) {
			PlayerClass cls = all[i];
			int ry = rowY(i);
			if (ry + LIST_ROW > y + panelH() - 4) {
				break;
			}
			boolean picked = cls == this.picked;
			graphics.fill(x + 4, ry, x + LIST_W - 4, ry + LIST_ROW - 2,
				picked ? Ui.ROW_PICKED : cls == hover ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW_ALT : 0x0CFFFFFF);
			if (picked) {
				graphics.fill(x + 4, ry, x + 6, ry + LIST_ROW - 2, Ui.GOLD);
			}
			List<ItemStack> kit = kit(cls);
			if (!kit.isEmpty() && !kit.getFirst().isEmpty()) {
				graphics.item(kit.getFirst(), x + 8, ry + 1);
			}
			Ui.line(graphics, this.font, Component.translatable("class.jaymoskingdoms." + cls.key()), x + 27, ry + 5, LIST_W - 34,
				picked ? Ui.GOLD : cls == hover ? Ui.TEXT : Ui.DIM);
		}
	}
}
