package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0) The class screen ({@link Classes}): the eight classes in two columns, each with what it's good with in its
 * tooltip. A Mage or a Sorcerer gets a second page to pick their first spell or their element. Opened by the server the
 * first time a player comes into a world (then it can't be shut without picking) and when a Writ of Rebirth is read.
 */
final class ClassScreen extends Screen {
	static final int PANEL_W = 300, PANEL_H = 230, PAD = 8, BUTTON_W = 132, BUTTON_H = 20, ROW_H = 24;
	static final int COLOR_PANEL = SkillScreen.COLOR_PANEL, COLOR_EDGE = SkillScreen.COLOR_EDGE, COLOR_TEXT = SkillScreen.COLOR_TEXT,
		COLOR_DIM = SkillScreen.COLOR_DIM, COLOR_GOLD = SkillScreen.COLOR_GOLD;

	private final boolean forced;
	/** The class picked, when its options are being chosen (the second page). */
	private @Nullable PlayerClass picked;
	private int option = -1;

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

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	@Override
	protected void init() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Classes.classOf(minecraft.player) != null) {
			onClose();
			return;
		}
		int x0 = this.width / 2 - BUTTON_W - 4, x1 = this.width / 2 + 4, y = top() + 46;
		if (this.picked == null) {
			PlayerClass[] all = PlayerClass.values();
			for (int i = 0; i < all.length; i++) {
				PlayerClass cls = all[i];
				Button button = Button.builder(Component.translatable("class.jaymoskingdoms." + cls.key()), b -> {
					if (cls.options.isEmpty()) {
						choose(cls, -1);
					} else {
						this.picked = cls;
						this.option = -1;
						rebuildWidgets();
					}
				}).bounds(i % 2 == 0 ? x0 : x1, y + (i / 2) * ROW_H, BUTTON_W, BUTTON_H).build();
				button.setTooltip(Tooltip.create(describe("class.jaymoskingdoms." + cls.key())));
				addRenderableWidget(button);
			}
			if (!this.forced) {
				addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.later"), b -> onClose())
					.bounds(this.width / 2 - 40, top() + PANEL_H - 26, 80, BUTTON_H).build());
			}
			return;
		}
		PlayerClass cls = this.picked;
		for (int i = 0; i < cls.options.size(); i++) {
			int index = i;
			String key = "class.jaymoskingdoms.option." + cls.options.get(i);
			MutableComponent name = Component.translatable(key);
			Button button = Button.builder(index == this.option ? name.withStyle(ChatFormatting.GOLD) : name, b -> {
				this.option = index;
				rebuildWidgets();
			}).bounds(i % 2 == 0 ? x0 : x1, y + (i / 2) * ROW_H, BUTTON_W, BUTTON_H).build();
			button.setTooltip(Tooltip.create(describe(key)));
			addRenderableWidget(button);
		}
		Button confirm = Button.builder(Component.translatable("gui.jaymoskingdoms.class.confirm", Component.translatable("class.jaymoskingdoms." + cls.key())),
			b -> choose(cls, this.option)).bounds(this.width / 2 + 4, top() + PANEL_H - 26, BUTTON_W, BUTTON_H).build();
		confirm.active = this.option >= 0;
		addRenderableWidget(confirm);
		addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.class.back"), b -> {
			this.picked = null;
			this.option = -1;
			rebuildWidgets();
		}).bounds(this.width / 2 - BUTTON_W - 4, top() + PANEL_H - 26, BUTTON_W, BUTTON_H).build());
	}

	private static Component describe(String key) {
		return Component.translatable(key).withStyle(ChatFormatting.GOLD).append("\n").append(Component.translatable(key + ".desc").withStyle(ChatFormatting.WHITE));
	}

	private void choose(PlayerClass cls, int option) {
		ClientPlayNetworking.send(new Classes.Choose(cls.ordinal(), option));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && Classes.classOf(minecraft.player) != null) {
			onClose();
			return;
		}
		int x = left(), y = top();
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_PANEL);
		SkillScreen.edge(graphics, x, y, PANEL_W, PANEL_H, COLOR_EDGE);
		graphics.centeredText(this.font, this.title, this.width / 2, y + 10, COLOR_GOLD);
		Component lead = this.picked == null ? Component.translatable("gui.jaymoskingdoms.class.lead")
			: Component.translatable("gui.jaymoskingdoms.class.pick", Component.translatable("gui.jaymoskingdoms.class.pick." + this.picked.key()));
		int cy = y + 24;
		for (FormattedCharSequence line : this.font.split(lead, PANEL_W - PAD * 2)) {
			graphics.centeredText(this.font, line, this.width / 2, cy, COLOR_DIM);
			cy += 10;
		}
		if (this.picked != null && this.option >= 0) {
			String key = "class.jaymoskingdoms.option." + this.picked.options.get(this.option);
			cy = y + PANEL_H - 60;
			for (FormattedCharSequence line : this.font.split(Component.translatable(key + ".desc"), PANEL_W - PAD * 4)) {
				graphics.centeredText(this.font, line, this.width / 2, cy, COLOR_TEXT);
				cy += 10;
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
