package com.jaymoskitchen.client.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.climate.BodyHeat;
import com.jaymoskitchen.climate.Climate;
import com.jaymoskitchen.climate.Thirst;
import com.jaymoskitchen.climate.WaterskinItem;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;

/**
 * (0.5.25) Thirst and body heat on the screen. The thirst row sits in vanilla's place for such things, above the hunger
 * bar (above Jaymo's Powers' feathers when they're there, the air bubbles when they show and a mount's hearts), ten
 * drops draining as hunger does, murky while dirty water sits badly (hunger effect). Body heat is a little thermometer
 * between the hearts and the hunger bar, with an arrow while it's moving; the frost on the screen is the game's own
 * freezing, and overheating reddens the edges. Sneaking with an empty hand at water out of a block's reach (deep
 * water) or up at the rain asks the server for a sip. Art: {@code tools/gen_climate.py}.
 */
public final class ClimateHud {
	private static final Identifier FULL = JaymosKitchen.id("hud/thirst_full");
	private static final Identifier HALF = JaymosKitchen.id("hud/thirst_half");
	private static final Identifier EMPTY = JaymosKitchen.id("hud/thirst_empty");
	private static final Identifier MURKY_FULL = JaymosKitchen.id("hud/thirst_murky_full");
	private static final Identifier MURKY_HALF = JaymosKitchen.id("hud/thirst_murky_half");
	/** Ticks between sips asked for while use is held. */
	static final int SIP_EVERY = 10;
	/** The thermometer's tube (px) and how far the arrow needs the body and air apart to show. */
	static final int TUBE = 14;
	static final float TREND = 4.0F;
	/** Overheating's red edges: bands and how deep each is. */
	static final int BANDS = 10, BAND = 4;

	private static int sipWait;

	private ClimateHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, JaymosKitchen.id("thirst"), ClimateHud::thirst);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKitchen.id("body_heat"), ClimateHud::heat);
		ClientTickEvents.END_CLIENT_TICK.register(ClimateHud::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(Climate.WATERSKIN)) {
				WaterskinItem.Water water = WaterskinItem.water(stack);
				lines.add(water.sips() <= 0 ? Component.translatable("tooltip.jaymoskitchen.waterskin.empty").withStyle(ChatFormatting.GRAY)
					: Component.translatable(water.clean() ? "tooltip.jaymoskitchen.waterskin.clean" : "tooltip.jaymoskitchen.waterskin.dirty",
						water.sips(), WaterskinItem.SIPS).withStyle(water.clean() ? ChatFormatting.AQUA : ChatFormatting.DARK_GREEN));
			} else if (stack.is(Climate.CLEAN_WATER)) {
				lines.add(Component.translatable("tooltip.jaymoskitchen.clean_water").withStyle(ChatFormatting.AQUA));
			}
		});
	}

	private static void tick(Minecraft minecraft) {
		if (sipWait > 0) {
			sipWait--;
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || sipWait > 0 || !minecraft.options.keyUse.isDown()
			|| !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			return;
		}
		// a block in reach is the block use's business (the server sips there too); this is for open water and rain
		if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
			return;
		}
		HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true);
		boolean water = hit.getType() == HitResult.Type.BLOCK
			&& player.level().getFluidState(((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos()).is(FluidTags.WATER);
		boolean rain = player.getXRot() < -60.0F && player.level().isRainingAt(player.blockPosition().above());
		if (water || rain) {
			ClientPlayNetworking.send(new Payloads.Sip());
			sipWait = SIP_EVERY;
		}
	}

	private static boolean shown(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && !player.isCreative() && !player.isSpectator();
	}

	private static void thirst(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!shown(minecraft)) {
			return;
		}
		LocalPlayer player = minecraft.player;
		int right = graphics.guiWidth() / 2 + 91;
		int y = graphics.guiHeight() - 49;
		if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			// Jaymo's Powers' feathers have the row above the hunger bar
			y -= 10;
		}
		if (player.getVehicle() instanceof LivingEntity mount) {
			int hearts = Mth.ceil(mount.getMaxHealth() / 2.0F);
			y -= (Math.max(1, Mth.ceil(Math.min(hearts, 30) / 10.0F)) - 1) * 10;
		}
		if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) {
			y -= 10;
		}
		int water = Thirst.water(player);
		boolean murky = player.hasEffect(MobEffects.HUNGER);
		for (int i = 0; i < Thirst.MAX / 2; i++) {
			int x = right - i * 8 - 9;
			int at = y;
			if (water <= Thirst.PARCHED) {
				// parched: the row trembles, as an empty hunger bar does
				at += (player.tickCount * 3 + i * 7) % 3 - 1;
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, x, at, 9, 9);
			if (i * 2 + 1 < water) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, murky ? MURKY_FULL : FULL, x, at, 9, 9);
			} else if (i * 2 + 1 == water) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, murky ? MURKY_HALF : HALF, x, at, 9, 9);
			}
		}
	}

	private static void heat(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!shown(minecraft)) {
			return;
		}
		BodyHeat.Heat heat = BodyHeat.heat(minecraft.player);
		float body = heat.body();
		if (body >= BodyHeat.OVERHEATING) {
			edges(graphics, Mth.clamp((body - BodyHeat.OVERHEATING) / (BodyHeat.MAX - BodyHeat.OVERHEATING), 0.0F, 1.0F));
		}
		thermometer(graphics, body, heat.ambient());
	}

	/** Red-orange edges creeping in as the heat climbs. */
	private static void edges(GuiGraphicsExtractor graphics, float strength) {
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		for (int i = 0; i < BANDS; i++) {
			int alpha = Math.round(strength * 70.0F * (BANDS - i) / BANDS);
			if (alpha <= 0) {
				continue;
			}
			int colour = ARGB.color(alpha, 220, 70, 20);
			int a = i * BAND, b = a + BAND;
			graphics.fill(a, a, w - a, b, colour);
			graphics.fill(a, h - b, w - a, h - a, colour);
			graphics.fill(a, b, b, h - b, colour);
			graphics.fill(w - b, b, w - a, h - b, colour);
		}
	}

	/** Between the hearts and the hunger bar: a tube that fills from blue to red, and which way it's going. */
	private static void thermometer(GuiGraphicsExtractor graphics, float body, float ambient) {
		int cx = graphics.guiWidth() / 2;
		int bottom = graphics.guiHeight() - 30;
		int top = bottom - 5 - TUBE;
		int colour = colour(body);
		// glass
		graphics.fill(cx - 2, top - 1, cx + 2, bottom - 4, 0xFF1A1A1A);
		graphics.fill(cx - 3, bottom - 6, cx + 3, bottom, 0xFF1A1A1A);
		graphics.fill(cx - 1, top, cx + 1, bottom - 4, 0xFF505050);
		// what's in it
		int filled = Math.round(TUBE * Mth.clamp((body - BodyHeat.MIN) / (BodyHeat.MAX - BodyHeat.MIN), 0.0F, 1.0F));
		graphics.fill(cx - 1, bottom - 5 - filled, cx + 1, bottom - 4, colour);
		graphics.fill(cx - 2, bottom - 5, cx + 2, bottom - 1, colour);
		// marks where trouble starts
		int comfort = bottom - 5 - TUBE / 2;
		graphics.fill(cx + 2, comfort, cx + 3, comfort + 1, 0xA0FFFFFF);
		if (ambient > body + TREND) {
			arrow(graphics, cx + 4, top + 2, true, colour(Math.max(body, 0.0F) + 30.0F));
		} else if (ambient < body - TREND) {
			arrow(graphics, cx + 4, top + 2, false, colour(Math.min(body, 0.0F) - 30.0F));
		}
	}

	private static void arrow(GuiGraphicsExtractor graphics, int x, int y, boolean up, int colour) {
		for (int row = 0; row < 3; row++) {
			int width = up ? row : 2 - row;
			graphics.fill(x + 2 - width, y + row, x + 3 + width, y + row + 1, colour);
		}
	}

	/** Deep blue frozen, pale comfortable, red with heatstroke. */
	static int colour(float body) {
		float t = Mth.clamp(body / BodyHeat.MAX, -1.0F, 1.0F);
		if (t < 0.0F) {
			return ARGB.srgbLerp(-t, 0xFFE8E8E8, 0xFF2E6CFF);
		}
		return ARGB.srgbLerp(t, 0xFFE8E8E8, 0xFFFF3A1A);
	}
}
