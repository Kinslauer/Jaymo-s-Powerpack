package com.jaymospowers.client.power.chrono;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.chrono.ChronoCheatDeath;
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
 * (0.20.42) Chrono cheating death, on the client ({@link ChronoCheatDeath}): the black screen that asks for R, and the
 * rewind itself shown as Respawn Recap's memory replay when that mod is installed (MIT, by DialingSpoon; shipped unchanged,
 * see {@code client/mixin/respawnrecap}). Without it the screen simply lifts.
 */
public final class ChronoCheatDeathClient {
	/** Respawn Recap is to play its replay for our rewind (it plays it while the player "is dead"). */
	private static boolean recapping;
	private static int recapTicks;
	/** Never longer than this (a replay that never ended can't hold your screen). */
	private static final int RECAP_MAX_TICKS = 20 * 90;

	private ChronoCheatDeathClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ChronoCheatDeath.Prompt.TYPE, (payload, ctx) -> {
			if (!(ctx.client().gui.screen() instanceof PromptScreen) && !recapping) {
				ctx.client().gui.setScreen(new PromptScreen(payload.seconds()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (recapping && (client.player == null || ++recapTicks > RECAP_MAX_TICKS)) {
				recapping = false;
			}
		});
	}

	/** Whether Respawn Recap should be playing its replay as our rewind now. */
	public static boolean recapping() {
		return recapping;
	}

	/** Respawn Recap's replay is over (or skipped): the rewind's film is done. */
	public static void recapDone() {
		recapping = false;
	}

	private static void rewind(Minecraft minecraft) {
		ClientPlayNetworking.send(new ChronoCheatDeath.Rewind());
		minecraft.gui.setScreen(null);
		if (FabricLoader.getInstance().isModLoaded("respawnrecap")) {
			recapping = true;
			recapTicks = 0;
		}
	}

	/** Black, gold words, and R. Escape does nothing: there is no other way out. */
	static final class PromptScreen extends Screen {
		private static final int GOLD = 0xFFF2C14E;
		private static final int DIM = 0xFF9C8A5A;
		private final int seconds;
		private int age;

		PromptScreen(int seconds) {
			super(Component.translatable("screen.jaymospowers.chrono_cheat_death"));
			this.seconds = seconds;
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
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (this.age > 10 && JaymosPowersClient.keyMapping(PowerKey.ABILITY_1).matches(event)) {
				rewind(this.minecraft);
				return true;
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
			// fades in over a second, then the prompt breathes
			float in = Mth.clamp((this.age + a) / 20.0F, 0.0F, 1.0F);
			int alpha = (int) (in * 255.0F) << 24;
			graphics.centeredText(this.font, this.title, cx, cy - 30, alpha | (GOLD & 0xFFFFFF));
			graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.chrono_cheat_death.line"), cx, cy - 16, alpha | (DIM & 0xFFFFFF));
			if (this.age > 10) {
				float pulse = 0.65F + 0.35F * Mth.sin((this.age + a) * 0.15F);
				int promptAlpha = (int) (in * pulse * 255.0F) << 24;
				Component key = JaymosPowersClient.keyMapping(PowerKey.ABILITY_1).getTranslatedKeyMessage();
				graphics.centeredText(this.font, Component.translatable("screen.jaymospowers.chrono_cheat_death.prompt", key), cx, cy + 6,
					promptAlpha | (GOLD & 0xFFFFFF));
				Component reach = this.seconds >= 60
					? Component.translatable("screen.jaymospowers.chrono_cheat_death.reach", this.seconds / 60, this.seconds % 60)
					: Component.translatable("screen.jaymospowers.chrono_cheat_death.reach_seconds", this.seconds);
				graphics.centeredText(this.font, reach, cx, cy + 20, alpha | (DIM & 0xFFFFFF));
			}
		}
	}
}
