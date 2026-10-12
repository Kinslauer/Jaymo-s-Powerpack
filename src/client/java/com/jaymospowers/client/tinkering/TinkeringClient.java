package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** The Part Builder's and Tool Station's screens, and what's left lying on them. */
public final class TinkeringClient {
	private TinkeringClient() {
	}

	public static void init() {
		MenuScreens.register(Tinkering.PART_BUILDER_MENU, PartBuilderScreen::new);
		MenuScreens.register(Tinkering.TOOL_STATION_MENU, ToolStationScreen::new);
		BlockEntityRenderers.register(Tinkering.STATION_ENTITY, StationRenderer::new);
		MenuScreens.register(Tinkering.SMELTERY_MENU, SmelteryScreen::new);
		BlockEntityRenderers.register(Tinkering.SMELTERY_ENTITY, SmelteryRenderer::new);
		BlockEntityRenderers.register(Tinkering.CASTING_ENTITY, CastingRenderer::new);
		BlockEntityRenderers.register(Tinkering.FAUCET_ENTITY, FaucetRenderer::new);
		Tinkering.traitDetails = () -> Minecraft.getInstance().hasShiftDown();
	}
}
