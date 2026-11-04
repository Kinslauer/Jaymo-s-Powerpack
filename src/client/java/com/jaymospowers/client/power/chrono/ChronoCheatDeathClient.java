package com.jaymospowers.client.power.chrono;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.ChronoCheatDeath;
import com.jaymospowers.power.chrono.ChronoPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * (0.20.42) Chrono cheating death, on the client ({@link ChronoCheatDeath}): the black screen that asks for R (Andy:
 * "they have to press R, if they dont they still die"), with the time left on it. When a death is rewound, Respawn Recap (MIT, by DialingSpoon; shipped unchanged, see {@code client/mixin/respawnrecap}) plays its memory replay
 * as the rewind. Andy: "limit it to chrono": its replay is never shown for a real death, its death-screen takeover is
 * switched off, and it only keeps its screenshots of your life while you're a Chrono.
 */
public final class ChronoCheatDeathClient {
	/** Respawn Recap is to play its replay for our rewind (it plays it while the player "is dead"). */
	private static boolean recapping;
	/** Its replay for our rewind is running (until it's over and the respawn blink is done). */
	private static boolean recapShown;
	private static int recapTicks;
	/** Never longer than this (a replay that never ended can't hold your screen). */
	private static final int RECAP_MAX_TICKS = 20 * 90;

	private ChronoCheatDeathClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ChronoCheatDeath.Prompt.TYPE, (payload, ctx) -> {
			Screen open = ctx.client().gui.screen();
			if (payload.ticks() <= 0) {
				if (open instanceof PromptScreen) {
					ctx.client().gui.setScreen(null);
				}
			} else if (open instanceof PromptScreen prompt) {
				prompt.left = payload.ticks();
			} else if (!recapping) {
				ctx.client().gui.setScreen(new PromptScreen(payload.seconds(), payload.ticks()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ChronoCheatDeath.Rewound.TYPE, (payload, ctx) -> {
			if (FabricLoader.getInstance().isModLoaded("respawnrecap") && payload.seconds() > 0) {
				recapping = true;
				recapShown = true;
				recapTicks = 0;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if ((recapping || recapShown) && (client.player == null || ++recapTicks > RECAP_MAX_TICKS)) {
				recapping = false;
				recapShown = false;
			}
		});
	}

	private static void rewind(Minecraft minecraft) {
		ClientPlayNetworking.send(new ChronoCheatDeath.Rewind());
		minecraft.gui.setScreen(null);
	}

	/** Black, gold words, R, and the time left. Escape does nothing. */
	static final class PromptScreen extends Screen {
		private static final int GOLD = 0xFFF2C14E;
		private static final int DIM = 0xFF9C8A5A;
		private final int seconds;
		private final int total;
		int left;
		private int age;

		PromptScreen(int seconds, int ticks) {
			super(Component.translatable("screen.jaymospowers.chrono_cheat_death"));
			this.seconds = seconds;
			this.total = Math.max(1, ticks);
			this.left = ticks;
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
			if (this.left > 0) {
				this.left--;
			}
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (this.age > 5 && JaymosPowersClient.keyMapping(PowerKey.ABILITY_1).matches(event)) {
				rewind(this.minecraft);
			}
			return true; // (nothing else gets through)
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			graphics.fill(0, 0, this.width, this.height, 0xFF000000);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			int cx = this.width / 2;
			int cy = this.height / 2;
			float in = Mth.clamp((this.age + a) / 10.0F, 0.0F, 1.0F);
			int alpha = Math.max(4, (int) (in * 255.0F)) << 24;
			graphics.centeredText(this.font, this.title, cx, cy - 30, alpha | (GOLD & 0xFFFFFF));
			graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.chrono_cheat_death.line"), cx, cy - 16, alpha | (DIM & 0xFFFFFF));
			float pulse = 0.65F + 0.35F * Mth.sin((this.age + a) * 0.3F);
			int promptAlpha = Math.max(4, (int) (in * pulse * 255.0F)) << 24;
			Component key = JaymosPowersClient.keyMapping(PowerKey.ABILITY_1).getTranslatedKeyMessage();
			graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.chrono_cheat_death.prompt", key), cx, cy + 6,
				promptAlpha | (GOLD & 0xFFFFFF));
			Component reach = this.seconds >= 60
				? Component.translatable("screen.jaymospowers.chrono_cheat_death.reach", this.seconds / 60, this.seconds % 60)
				: Component.translatable("screen.jaymospowers.chrono_cheat_death.reach_seconds", this.seconds);
			graphics.centeredText(this.font, reach, cx, cy + 20, alpha | (DIM & 0xFFFFFF));
			// the time left, a thin gold line running down
			int barW = 120;
			int filled = (int) (barW * Mth.clamp((this.left - a) / (float) this.total, 0.0F, 1.0F));
			graphics.fill(cx - barW / 2, cy + 36, cx + barW / 2, cy + 37, alpha | 0x3A3220);
			graphics.fill(cx - barW / 2, cy + 36, cx - barW / 2 + filled, cy + 37, alpha | (GOLD & 0xFFFFFF));
		}
	}

	/** Whether Respawn Recap should be playing its replay as our rewind now. */
	public static boolean recapping() {
		return recapping;
	}

	/** Respawn Recap's replay is over (or skipped): the rewind's film is done. */
	public static void recapDone() {
		recapping = false;
	}

	/** Respawn Recap has gone back to idle (its respawn blink begun). */
	public static void recapIdle() {
		recapShown = false;
	}

	/**
	 * Whether Respawn Recap may keep {@code requested} off the screen (its death-screen takeover): only while it plays our
	 * rewind. Every other time screens open as they always do, the death screen too. Called from the patch
	 * {@code RespawnRecapPlugin} puts into its handler.
	 */
	public static boolean recapMayHide(Screen requested) {
		return recapping || recapShown;
	}

	/** Whether Respawn Recap should keep screenshots of your life now: only a Chrono's. */
	public static boolean recordsLife(Minecraft minecraft) {
		return minecraft.player != null && Powers.has(minecraft.player, ChronoPower.INSTANCE);
	}
}
