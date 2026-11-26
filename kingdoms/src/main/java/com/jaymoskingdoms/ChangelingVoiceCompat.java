package com.jaymoskingdoms;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Keeps the optional voice API out of the changeling's ordinary game code. */
final class ChangelingVoiceCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("voicechat");

	private ChangelingVoiceCompat() {
	}

	static boolean lure(Villager copy, ServerPlayer target) {
		return LOADED && ChangelingVoices.lure(copy, target);
	}
}
