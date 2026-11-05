package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.20.102) Andy (2026-11-05, a new Emberborn on "Alone in the wild"): "i got all these achievements but did not do
 * them yet". Homesteads' five workstation advancements (Nice Tan!, A bouquet for you!, Honey, I placed a thing, Got
 * wood?, Your prospects are looking up) give their placed_block trigger an old {@code "block"} field that 26.3 no
 * longer reads, so each has no condition at all and the first block a player places earns all five. An always-on pack
 * ({@code resourcepacks/homesteads_fixes}) gives them 26.3's {@code location} match on the workstation itself; Homesteads'
 * own jar is untouched. Players who already got them from the bug have them taken back once (they come back when the
 * workstation is really placed).
 */
public final class HomesteadsCompat {
	static final String MOD_ID = "homesteads";
	/** The five advancements the bug gave away. */
	static final String[] PLACED = {"place_tanning_rack", "place_flower_workstation", "place_bee_keeper_work_station",
		"place_wood_work_log", "place_prospect_bench"};
	/** The player has been looked at for the bug's advancements (taken back if they had them). */
	public static final AttachmentType<Boolean> CHECKED = AttachmentRegistry.create(JaymosPowers.id("homesteads_checked"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private HomesteadsCompat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ResourceLoader.registerBuiltinPack(JaymosPowers.id("homesteads_fixes"),
			FabricLoader.getInstance().getModContainer(JaymosPowers.MOD_ID).orElseThrow(),
			Component.literal("Jaymo's Powers: Homesteads advancements"), PackActivationType.ALWAYS_ENABLED);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> takeBack(handler.getPlayer()));
	}

	/** Once per player: the bug's advancements are taken back. */
	static void takeBack(ServerPlayer player) {
		if (player.getAttachedOrElse(CHECKED, false)) {
			return;
		}
		player.setAttached(CHECKED, true);
		for (String name : PLACED) {
			AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(MOD_ID, name));
			if (holder == null) {
				continue;
			}
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			List<String> done = new ArrayList<>();
			for (String criterion : progress.getCompletedCriteria()) {
				done.add(criterion);
			}
			for (String criterion : done) {
				player.getAdvancements().revoke(holder, criterion);
			}
		}
	}
}
