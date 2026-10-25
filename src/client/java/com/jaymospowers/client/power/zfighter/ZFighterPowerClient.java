package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterState;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import java.util.Locale;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Z Fighter's client half, right of the hotbar in Wind's chip style: the ki bar (gold while Super, with the combo
 * count over it), then R G V B, Z (Flight) once grown into, and C (Super) once ascended. Over the crosshair: the Ki
 * Wave and the Spirit Bomb gathering. G and V show the technique set on them; C shows the Spirit Bomb in Super 3 once
 * learned; Rage shows over the ki bar.
 */
public final class ZFighterPowerClient implements PowerClient {
	/** The screen's Fusion Dance: C this many ticks after Z (Z's own tap has let go by then). */
	static final int FUSION_C_AFTER = 6;
	public static final ZFighterPowerClient INSTANCE = new ZFighterPowerClient();
	private static boolean guardWasUp;
	/** The hold combo: how long the attack key has been held bare-handed, and whether the string is on. */
	private static int attackHeld;
	private static boolean comboOn;
	/** Held this long (ticks) before a click becomes the hold combo: a tap stays an ordinary punch. */
	private static final int COMBO_HOLD_TICKS = 4;

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = ZFighterPower.COLOR;
	private static final int SUPER_COLOR = ZFighterPower.SUPER_COLOR;
	private static final int WAVE_COLOR = 0x8FD3FF;
	private static final int SPIRIT_COLOR = 0x8FD8FF;
	private static final int RAGE_COLOR = 0xFF3A2A;
	/** Ticks a Ki Wave takes to gather in full (mirrors the server's). */
	private static final int WAVE_FULL = 60;

	private ZFighterPowerClient() {
	}

	public static void init() {
		PowerClients.register(ZFighterPower.ID, INSTANCE);
		ZFighterInputClient.init();
		ZFighterBeams.init();
		ZFighterSpirit.init();
		ZFighterOrbs.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.power.zfighter.ZFighterRaids.ROGUE,
			ZFighterRogueRenderer::new);
		ZFighterWishScreen.register();
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterFlightPose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterChargePose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterSpiritPose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterDancePose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterComboPose::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterDiscPose::tick);
		// the Training Post: a Z Fighter's right click opens the drills
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new ZFighterTail.Layer(avatar));
				// (0.15.148) Super 4's red fur, under the mane
				helper.register(new ZFighterPrimalFur.Layer(avatar));
				helper.register(new ZFighterHair.Layer(avatar));
				helper.register(new ZFighterMark.Layer(avatar));
				helper.register(new ZFighterVerdantLook.Layer(avatar));
				helper.register(new ZFighterApe.Layer(avatar));
				// (0.16.7) the faint ki glow round his outline, over everything of him
				helper.register(new ZFighterKiGlow.Layer(avatar));
			} else if (renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) {
				// a Warrior-born villager raging as the wild Great Ape (VillagerApe)
				helper.register(ZFighterApe.mobLayer(renderer));
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || !level.getBlockState(hit.getBlockPos()).is(ZFighterItems.TRAINING_POST)
				|| !Powers.has(player, ZFighterPower.INSTANCE)) {
				return InteractionResult.PASS;
			}
			Minecraft.getInstance().gui.setScreen(new ZFighterDrillScreen());
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		ZFighterFlightClient.tick(minecraft, player);
		ZFighterApeGlide.tick(minecraft, player);
		ApeSmashClient.tick(minecraft, player);
		// the guard: right click held, bare-handed
		boolean guard = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& player.getMainHandItem().isEmpty();
		if (guard != guardWasUp) {
			guardWasUp = guard;
			ClientPlayNetworking.send(new ZFighterNet.Guard(guard));
		}
		// the hold combo: left mouse held, bare-handed, not at a block (that's digging)
		boolean attack = minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& player.getMainHandItem().isEmpty() && !guard && !RaceHoldClient.holding() && !ZFighterLineage.ape(player)
			&& !(ZFighterLineage.of(player) == ZFighterLineage.VERDANT && RaceHoldClient.yielded(player));
		attackHeld = attack ? attackHeld + 1 : 0;
		boolean combo = comboOn ? attack
			: attackHeld >= COMBO_HOLD_TICKS && (minecraft.hitResult == null || minecraft.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK);
		if (combo != comboOn) {
			comboOn = combo;
			ClientPlayNetworking.send(new ZFighterNet.Combo(combo));
		}
	}

	/** Whether the hold combo has the attack key (vanilla's held-click digging stands aside). */
	public static boolean comboHeld() {
		Minecraft minecraft = Minecraft.getInstance();
		return comboOn && minecraft.player != null && Powers.has(minecraft.player, ZFighterPower.INSTANCE);
	}

	/**
	 * (0.16.6) Andy: the powers screen's Mode slot "for z fighters... the way that the fusion dance is activated": it taps
	 * Z, then C a moment later, the fusion dance's keys (a partner facing you answers the same way).
	 */
	@Override
	public boolean screenMode(LocalPlayer player) {
		com.jaymospowers.client.JaymosPowersClient.tap(com.jaymospowers.client.JaymosPowersClient.TAP_MODE);
		com.jaymospowers.client.JaymosPowersClient.tapLater(com.jaymospowers.client.JaymosPowersClient.TAP_ULTIMATE, FUSION_C_AFTER);
		return true;
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		ApeSmashClient.reset();
		if (guardWasUp) {
			guardWasUp = false;
			ClientPlayNetworking.send(new ZFighterNet.Guard(false));
		}
		attackHeld = 0;
		if (comboOn) {
			comboOn = false;
			ClientPlayNetworking.send(new ZFighterNet.Combo(false));
		}
	}

	/** From the avatar renderer's extractRenderState (everyone, not only Z Fighters: the pose eases out). */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		ZFighterFlightPose.extract(entity, state, partialTicks);
		ZFighterTail.extract(entity, state);
		ZFighterHair.extract(entity, state);
		ZFighterPrimalFur.extract(entity, state);
		ZFighterChargePose.extract(entity, state, partialTicks);
		ZFighterSpiritPose.extract(entity, state, partialTicks);
		ZFighterGuardPose.extract(entity, state);
		ZFighterFiringPose.extract(entity, state);
		ZFighterDancePose.extract(entity, state, partialTicks);
		ZFighterComboPose.extract(entity, state, partialTicks);
		ZFighterDiscPose.extract(entity, state, partialTicks);
		ZFighterMark.extract(entity, state);
		ZFighterFusionLook.extract(entity, state);
		ZFighterVerdantLook.extract(entity, state);
		ZFighterApe.extract(entity, state);
		ZFighterKiGlow.extract(entity, state);
		RaceHoldClient.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.ember.EmberKindleClient.extract(entity, state);
		com.jaymospowers.client.power.heritage.HeritageMovesClient.extract(entity, state);
		ZFighterStretchPose.extract(entity, state, partialTicks);
	}

	/** Right after a humanoid model animates. */
	public static void applyModel(Model<?> model, Object state) {
		ZFighterFlightPose.applyModel(model, state);
		ZFighterChargePose.applyModel(model, state);
		ZFighterGuardPose.applyModel(model, state);
		ZFighterFiringPose.applyModel(model, state);
		ZFighterDancePose.applyModel(model, state);
		ZFighterComboPose.applyModel(model, state);
		ZFighterSpiritPose.applyModel(model, state);
		ZFighterDiscPose.applyModel(model, state);
		RaceHoldClient.applyModel(model, state);
		com.jaymospowers.client.power.ember.EmberKindleClient.applyModel(model, state);
		com.jaymospowers.client.power.heritage.HeritageMovesClient.applyModel(model, state);
		ZFighterStretchPose.applyModel(model, state);
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		ZFighterState state = ZFighterPower.state(player);
		wave(graphics, font, player, state, now, time);
		spirit(graphics, font, state, time);
		chips(graphics, font, player, state, now, time);
		// (0.15.148) the Great Ape's rage, and the fight against its fits
		ApeRageClient.hud(graphics, font, player, time);
	}

	/** The Spirit Bomb gathering, over the crosshair: how full it is. */
	private static void spirit(GuiGraphicsExtractor graphics, Font font, ZFighterState state, float time) {
		if (state.bomb() < 0) {
			return;
		}
		float fill = Mth.clamp(state.bomb() / 100.0F, 0.0F, 1.0F);
		int cx = graphics.guiWidth() / 2;
		int width = 80;
		int x0 = cx - width / 2;
		int y = graphics.guiHeight() / 2 - 40;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 5, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(width * fill), y + 4, 0xFF000000 | (fill >= 1.0F ? pulse(0xFFFFFF, SPIRIT_COLOR, time * 0.5F) : SPIRIT_COLOR));
		graphics.centeredText(font, Component.translatable("hud.jaymospowers.zfighter.spirit").copy().withColor(SPIRIT_COLOR), cx, y - 11, 0xFFFFFFFF);
	}

	/** The icon on G or V for the technique set there. */
	private static Item techniqueIcon(int technique) {
		return switch (technique) {
			case ZFighterTraining.KI_DISC -> Items.BOWL;
			case ZFighterTraining.HOMING_VOLLEY -> Items.FIREWORK_STAR;
			case ZFighterTraining.SOLAR_FLASH -> Items.GLOWSTONE;
			case ZFighterTraining.KI_BARRIER -> Items.SHIELD;
			case ZFighterTraining.KI_BOMB -> Items.MAGMA_CREAM;
			case ZFighterTraining.KI_WAVE -> Items.HEART_OF_THE_SEA;
			default -> Items.FIRE_CHARGE;
		};
	}

	private static void wave(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, ZFighterState state, long now, float time) {
		if (state.waveFrom() < 0) {
			return;
		}
		float fill = Mth.clamp((now - state.waveFrom()) / (float)WAVE_FULL, 0.0F, 1.0F);
		// (0.13.27) his own ki colour in base form
		Integer ki = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getAttached(ZFighterPower.KI_COLOR);
		int color = state.has(ZFighterState.SUPER) ? SUPER_COLOR : ki != null ? ki : WAVE_COLOR;
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		int width = 52;
		int x0 = cx - width / 2;
		int y = cy - 28;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(width * fill), y + 3, 0xFF000000 | (fill >= 1.0F ? pulse(0xFFFFFF, color, time * 0.6F) : color));
		// (0.13.31) named for what's on V: the lineage's own wave (Crimson Cannon, Spiral Lance, Frost Needle) or the Ki Bomb
		String name = ZFighterLineage.techniqueKey(player, ZFighterTraining.of(player).charged());
		graphics.centeredText(font, Component.translatable(name).copy().withColor(color), cx, y - 11, 0xFFFFFFFF);
	}

	private static void chips(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, ZFighterState state, long now, float time) {
		boolean flight = ZFighterPower.INSTANCE.slotUnlocked(player, "mode");
		boolean ultimate = Fate.isAscended(player);
		boolean super_ = state.has(ZFighterState.SUPER);
		int count = 5 + (flight ? 1 : 0) + (ultimate ? 1 : 0);
		int total = CHIP * count + CHIP_GAP * (count - 1);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		kiBar(graphics, font, state, x, y - 6, total, super_, time);
		int step = CHIP + CHIP_GAP;
		float charge = state.has(ZFighterState.CHARGING) ? 0.5F + 0.5F * Mth.sin(time * 0.5F) : -1.0F;
		chip(graphics, font, x, y, PowerKey.ABILITY_1, Items.GLOWSTONE_DUST, state, -1, now, charge);
		ZFighterTraining.Data training = ZFighterTraining.of(player);
		chip(graphics, font, x + step, y, PowerKey.ABILITY_2, techniqueIcon(training.quick()), state, ZFighterState.BLAST, now,
			state.has(ZFighterState.BARRIER) ? 1.0F : -1.0F);
		float wave = state.waveFrom() >= 0 ? Mth.clamp((now - state.waveFrom()) / (float)WAVE_FULL, 0.0F, 1.0F) : -1.0F;
		chip(graphics, font, x + step * 2, y, PowerKey.ABILITY_3, techniqueIcon(training.charged()), state, ZFighterState.WAVE, now, wave);
		chip(graphics, font, x + step * 3, y, PowerKey.ABILITY_4, Items.FEATHER, state, ZFighterState.STEP, now, -1.0F);
		int next = 4;
		if (flight) {
			boolean flying = state.has(ZFighterState.FLYING);
			int chipX = x + step * next++;
			special(graphics, font, chipX, y, new ItemStack(Items.PHANTOM_MEMBRANE), 0, 1,
				JaymosPowersClient.modeKey().getTranslatedKeyMessage().getString(), flying ? pulse(0xFFFFFF, CHIP_COLOR, time * 0.4F) : CHIP_COLOR);
			int pose = ZFighterPower.flightPose(player);
			if (pose > 0) {
				// the pose he's holding (scroll with Z held)
				graphics.text(font, Integer.toString(pose), chipX + 2, y + 2, 0xFFFFFFFF);
			}
		}
		boolean limit = state.limit() > 0;
		special(graphics, font, x + step * next++, y, new ItemStack(limit ? Items.BLAZE_POWDER : Items.WRITABLE_BOOK), 0, 1,
			ZFighterInputClient.keyLabel(Minecraft.getInstance()), limit ? pulse(0xFF3A2A, 0xFFB0A0, time * 0.6F) : CHIP_COLOR);
		if (ultimate) {
			// in Super 3 with the Spirit Bomb learned, C raises it: its icon and its cooldown
			boolean spirit = state.form() >= ZFighterTraining.FORMS && training.knows(ZFighterTraining.SPIRIT_BOMB);
			int slot = spirit ? ZFighterState.SPIRIT : ZFighterState.SUPER_SLOT;
			special(graphics, font, x + step * next, y, new ItemStack(spirit ? Items.ENDER_EYE : Items.NETHER_STAR), state.left(slot, now),
				state.length()[slot], JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(),
				spirit ? SPIRIT_COLOR : super_ ? pulse(0xFFFFFF, SUPER_COLOR, time * 0.5F) : SUPER_COLOR);
		}
	}

	/** The ki meter over the chips, with the number and, mid-combo, the hit count. */
	private static void kiBar(GuiGraphicsExtractor graphics, Font font, ZFighterState state, int x, int y, int width, boolean super_, float time) {
		float fill = Mth.clamp(state.ki() / (float)Math.max(1, state.maxKi()), 0.0F, 1.0F);
		int color = super_ ? pulse(SUPER_COLOR, 0xFFF4C0, time * 0.3F) : CHIP_COLOR;
		graphics.fill(x, y, x + width, y + 4, 0x90000000);
		graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * fill), y + 3, 0xFF000000 | color);
		String ki = state.ki() + "/" + state.maxKi();
		graphics.text(font, ki, x + width - font.width(ki), y - 9, 0xFF000000 | color);
		Component label = Component.translatable("hud.jaymospowers.zfighter.ki");
		if (state.form() > 0) {
			label = Component.translatable(ZFighterLineage.formKey(net.minecraft.client.Minecraft.getInstance().player, state.form())).copy().withColor(color);
		}
		if (state.limit() > 0) {
			label = label.copy().append(Component.translatable("hud.jaymospowers.zfighter.limit", state.limit()).copy().withColor(0xFF5A40));
		}
		graphics.text(font, label, x, y - 9, 0xFFFFFFFF);
		if (state.has(ZFighterState.RAGE)) {
			label = Component.translatable("hud.jaymospowers.zfighter.rage").copy().withColor(pulse(RAGE_COLOR, 0xFFB0A0, time * 0.7F));
			graphics.text(font, label, x, y - 18, 0xFFFFFFFF);
		} else if (state.rage() > 0) {
			// the fury building: a thin red line under the ki
			graphics.fill(x + 1, y + 3, x + 1 + Math.round((width - 2) * state.rage() / 100.0F), y + 4, 0xFF000000 | RAGE_COLOR);
		}
		if (state.combo() >= 2 && state.form() == 0 && state.limit() == 0) {
			Component combo = Component.translatable("hud.jaymospowers.zfighter.combo", state.combo());
			graphics.text(font, combo, x + width / 2 - font.width(combo) / 2, y - 9, 0xFFFFE08A);
		}
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, ZFighterState state, int slot,
			long now, float bar) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		if (!JaymosPowersClient.isUnlocked(key)) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		long left = slot < 0 ? 0 : state.left(slot, now);
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (slot >= 0) {
			shade(graphics, font, x, y, left, state.length()[slot]);
		}
		if (bar >= 0.0F) {
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + Math.round((CHIP - 2) * bar), y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Z or C: their own key. */
	private static void special(GuiGraphicsExtractor graphics, Font font, int x, int y, ItemStack icon, long left, int total, String key, int color) {
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | color : 0xA0505050);
		graphics.item(icon, x + 2, y + 2);
		shade(graphics, font, x, y, left, total);
		key = font.plainSubstrByWidth(key, CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	private static void shade(GuiGraphicsExtractor graphics, Font font, int x, int y, long left, int total) {
		if (left <= 0) {
			return;
		}
		int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, total)));
		graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
		String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
		graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
	}

	/** Blend between two 0xRRGGBB colours on a sine wave. */
	private static int pulse(int a, int b, float phase) {
		float t = 0.5F + 0.5F * Mth.sin(phase);
		int r = (int)Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int)Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
