package com.jaymospowers.client.tinkering;

import com.jaymospowers.armory.Armory;
import com.jaymospowers.tinkering.Modifier;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.MeltingRecipes;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** The Part Builder's, Tool Station's, Tool Forge's and Crafting Station's screens, and what's left lying on them. */
public final class TinkeringClient {
	private TinkeringClient() {
	}

	public static void init() {
		MenuScreens.register(Tinkering.PART_BUILDER_MENU, PartBuilderScreen::new);
		MenuScreens.register(Tinkering.TOOL_STATION_MENU, ToolStationScreen::new);
		MenuScreens.register(com.jaymospowers.tinkering.table.Tables.TOOL_FORGE_MENU, ToolStationScreen::new);
		MenuScreens.register(com.jaymospowers.tinkering.table.Tables.CRAFTING_STATION_MENU, CraftingStationScreen::new);
		BlockEntityRenderers.register(Tinkering.STATION_ENTITY, StationRenderer::new);
		MenuScreens.register(Tinkering.SMELTERY_MENU, SmelteryScreen::new);
		BlockEntityRenderers.register(Tinkering.SMELTERY_ENTITY, SmelteryRenderer::new);
		BlockEntityRenderers.register(Tinkering.CASTING_ENTITY, CastingRenderer::new);
		BlockEntityRenderers.register(Tinkering.FAUCET_ENTITY, FaucetRenderer::new);
		BlockEntityRenderers.register(com.jaymospowers.tinkering.kiln.Kiln.KILN_LOAD_ENTITY, KilnLoadRenderer::new);
		// (0.13) what melts by recipe, data file or forged weapon, as the server worked it out
		ClientPlayNetworking.registerGlobalReceiver(MeltingRecipes.Sync.TYPE, (payload, context) -> Melting.setExtra(payload.items()));
		// (0.8.76) Armory weapons level up and take modifiers like tinker tools
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!Armory.isWeapon(stack)) {
				return;
			}
			int xp = Modifier.xp(stack), level = Modifier.toolLevel(xp);
			int at = Math.min(1, lines.size());
			lines.add(at, Component.translatable("tooltip.jaymospowers.tinkering.slots", Modifier.slots(stack) - Modifier.used(stack),
				Modifier.slots(stack)).withStyle(ChatFormatting.GRAY));
			lines.add(at, (level >= Modifier.MAX_LEVEL ? Component.translatable("tooltip.jaymospowers.tinkering.level_max", level)
				: Component.translatable("tooltip.jaymospowers.tinkering.level", level, xp, Modifier.xpFor(level + 1)))
				.withStyle(ChatFormatting.YELLOW));
		});
		Tinkering.traitDetails = () -> Minecraft.getInstance().hasShiftDown();
		com.jaymospowers.client.tinkering.smithing.SmithingClient.init();
	}
}
