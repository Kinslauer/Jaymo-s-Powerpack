package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * The view from inside a great helm (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its black helmet puts a
 * filter over the view and plays slow breathing). Ours: wearing a helmet tagged {@code jaymospowers:great_helms}, in
 * first person, the world is seen through the visor's slit (dark steel above and below it, soft at the edges, drawn
 * under the hotbar and the rest of the HUD), and you hear your own breath, muffled, in and out: slow at rest, quicker
 * while you sprint or are hurt. {@code /helmview on|off} turns it off for good (the client's own setting). The Rig's
 * faceplate has its own view and is left alone.
 */
public final class GreatHelmView {
	public static final TagKey<Item> GREAT_HELMS = TagKey.create(Registries.ITEM, JaymosPowers.id("great_helms"));
	/** The steel above and below the slit, as shares of the screen's height, and how far each fades into the view. */
	static final float TOP = 0.17F, BOTTOM = 0.21F, FADE = 0.07F;
	/** The steel's colour (opaque), and the shade it fades to. */
	static final int STEEL = 0xFF0B0B0D, CLEAR = 0x000B0B0D;
	/** A breath every this many ticks at rest, and while sprinting or under half health; how loud. */
	static final int BREATH_REST = 76, BREATH_HARD = 34;
	static final float BREATH_VOLUME = 0.35F;
	private static final String KEY = "helmView";
	private static final SoundEvent BREATH = SoundEvent.createVariableRangeEvent(JaymosPowers.id("helm.breath"));

	private static int untilBreath;

	private GreatHelmView() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("great_helm_view"), (graphics, delta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null && active(minecraft, minecraft.player)) {
				render(graphics);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || minecraft.isPaused() || !active(minecraft, player)) {
				untilBreath = 20;
				return;
			}
			if (--untilBreath <= 0) {
				boolean hard = player.isSprinting() || player.getHealth() < player.getMaxHealth() * 0.5F;
				untilBreath = hard ? BREATH_HARD : BREATH_REST;
				player.level().playLocalSound(player, BREATH, SoundSource.PLAYERS, BREATH_VOLUME, hard ? 1.25F : 1.0F);
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("helmview")
				.executes(c -> say(c.getSource()))
				.then(ClientCommands.literal("on").executes(c -> set(c.getSource(), true)))
				.then(ClientCommands.literal("off").executes(c -> set(c.getSource(), false)))));
	}

	static boolean on() {
		return !"off".equals(ZFighterClientConfig.get(KEY, "on"));
	}

	/** Whether the slit is over this player's view now. */
	static boolean active(Minecraft minecraft, LocalPlayer player) {
		return on() && minecraft.options.getCameraType().isFirstPerson() && !player.isSpectator()
			&& player.getItemBySlot(EquipmentSlot.HEAD).is(GREAT_HELMS);
	}

	private static void render(GuiGraphicsExtractor g) {
		int w = g.guiWidth(), h = g.guiHeight();
		int top = Math.round(h * TOP), bottom = Math.round(h * BOTTOM), fade = Math.round(h * FADE);
		g.fill(0, 0, w, top, STEEL);
		g.fillGradient(0, top, w, top + fade, STEEL, CLEAR);
		g.fillGradient(0, h - bottom - fade, w, h - bottom, CLEAR, STEEL);
		g.fill(0, h - bottom, w, h, STEEL);
	}

	private static int say(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable(on() ? "message.jaymospowers.helm_view.is_on" : "message.jaymospowers.helm_view.is_off"));
		return Command.SINGLE_SUCCESS;
	}

	private static int set(FabricClientCommandSource source, boolean value) {
		ZFighterClientConfig.set(KEY, value ? "on" : "off");
		return say(source);
	}
}
