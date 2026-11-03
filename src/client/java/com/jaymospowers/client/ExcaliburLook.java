package com.jaymospowers.client;

import com.mojang.brigadier.Command;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.72) The Excalibur look, optional and off by default (Andy: "make excalibur and all it's additions from here
 * optional, and off by default. but keep it in the pack"). Three packs make it up: Excalibur itself and its Fresh
 * Animations patch (PackResourcePacks.OPTIONAL, fetched into resourcepacks/ but never switched on by us), and our
 * built-in excalibur_style pack (our textures graded toward Excalibur, calmer leaves). {@code /excalibur on} switches
 * all three on in the right order: Excalibur at the bottom, the patch just above Fresh Animations, ours on top.
 * {@code /excalibur off} takes all three off. Recrafted joins them when the player has it ({@link #RECRAFTED}). They can also be picked by hand in Resource Packs.
 *
 * <p>0.19.65 switched Excalibur and its patch on for everyone; on the first launch with this version they're switched
 * off once (marker {@link #OFF_MARKER}). After that, players' own choice is never touched.
 */
final class ExcaliburLook {
	private static final String OFF_MARKER = "jaymospowers-excalibur-off-v1";
	private static final String OURS = "excalibur_style";
	/**
	 * (0.19.72) Recrafted (by MR_CHOO) is a paid early-access pack with no licence to redistribute, so it's never shipped
	 * or fetched. A player who has it in their own resourcepacks/ gets it switched on with the look, just above
	 * Excalibur (so it draws over Excalibur, and Fresh Animations and its patch still draw over both). It's made for
	 * 1.21.1-1.21.8, so it's accepted as an older-format pack.
	 */
	private static final String RECRAFTED = "Recrafted";

	private ExcaliburLook() {
	}

	static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("excalibur")
				.executes(c -> report(c.getSource()))
				.then(ClientCommands.literal("on").executes(c -> toggle(c.getSource(), true)))
				.then(ClientCommands.literal("off").executes(c -> toggle(c.getSource(), false)))));
	}

	/** At startup: the one-time switch-off, then fetch the two files in the background so /excalibur on works later. */
	static void start(Minecraft minecraft) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(OFF_MARKER);
		if (!Files.exists(marker)) {
			if (switchAll(minecraft, false)) {
				apply(minecraft);
			}
			try {
				Files.writeString(marker, "Jaymo's Powers switched the Excalibur look off once (it's optional now). /excalibur on brings it back.\n");
			} catch (IOException e) {
				com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
			}
		}
		Path dir = minecraft.getResourcePackDirectory();
		for (PackResourcePacks.Pack pack : PackResourcePacks.OPTIONAL) {
			if (PackResourcePacks.find(dir, pack) == null) {
				CompletableFuture.supplyAsync(() -> PackResourcePacks.download(dir, pack), Util.nonCriticalIoPool()).thenAcceptAsync(got -> {
					if (got != null) {
						minecraft.getResourcePackRepository().reload();
					}
				}, minecraft);
			}
		}
	}

	private static int report(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable(isOn(source.getClient())
			? "message.jaymospowers.excalibur.is_on" : "message.jaymospowers.excalibur.is_off"));
		return Command.SINGLE_SUCCESS;
	}

	private static int toggle(FabricClientCommandSource source, boolean on) {
		Minecraft minecraft = source.getClient();
		if (on && missing(minecraft) != null) {
			source.sendFeedback(Component.translatable("message.jaymospowers.excalibur.missing", missing(minecraft)));
			return 0;
		}
		if (switchAll(minecraft, on)) {
			apply(minecraft);
		}
		source.sendFeedback(Component.translatable(on ? "message.jaymospowers.excalibur.on" : "message.jaymospowers.excalibur.off"));
		return Command.SINGLE_SUCCESS;
	}

	private static boolean isOn(Minecraft minecraft) {
		return minecraft.options.resourcePacks.stream().anyMatch(id -> isExcalibur(id, PackResourcePacks.OPTIONAL.get(0)));
	}

	/** The first of Excalibur's files not in resourcepacks/ yet (still downloading, or offline), or null. */
	private static @Nullable String missing(Minecraft minecraft) {
		for (PackResourcePacks.Pack pack : PackResourcePacks.OPTIONAL) {
			if (PackResourcePacks.find(minecraft.getResourcePackDirectory(), pack) == null) {
				return pack.file();
			}
		}
		return null;
	}

	private static boolean isExcalibur(String id, PackResourcePacks.Pack pack) {
		return id.startsWith("file/" + pack.prefix());
	}

	private static boolean isRecrafted(String id) {
		return id.startsWith("file/" + RECRAFTED);
	}

	private static boolean isOurs(String id) {
		return id.contains(com.jaymospowers.JaymosPowers.MOD_ID) && id.endsWith(OURS);
	}

	/** Takes all three out of the selected list, and with {@code on} puts them back in order. True when it changed. */
	private static boolean switchAll(Minecraft minecraft, boolean on) {
		List<String> selected = minecraft.options.resourcePacks;
		List<String> before = List.copyOf(selected);
		selected.removeIf(id -> isOurs(id) || isRecrafted(id) || PackResourcePacks.OPTIONAL.stream().anyMatch(pack -> isExcalibur(id, pack)));
		minecraft.options.incompatibleResourcePacks.removeIf(id -> PackResourcePacks.OPTIONAL.stream().anyMatch(pack -> isExcalibur(id, pack)));
		if (on) {
			minecraft.getResourcePackRepository().reload();
			for (PackResourcePacks.Pack pack : PackResourcePacks.OPTIONAL) {
				String file = PackResourcePacks.find(minecraft.getResourcePackDirectory(), pack);
				if (file != null && minecraft.getResourcePackRepository().getPack("file/" + file) != null) {
					selected.add(PackResourcePacks.placeAt(selected, pack.place()), "file/" + file);
				}
			}
			minecraft.getResourcePackRepository().getAvailableIds().stream().filter(ExcaliburLook::isRecrafted).findFirst()
				.ifPresent(id -> {
					PackResourcePacks.Pack excalibur = PackResourcePacks.OPTIONAL.get(0);
					int at = selected.size();
					for (int i = 0; i < selected.size(); i++) {
						if (isExcalibur(selected.get(i), excalibur)) {
							at = i + 1;
							break;
						}
					}
					selected.add(at, id);
					if (!minecraft.options.incompatibleResourcePacks.contains(id)) {
						minecraft.options.incompatibleResourcePacks.add(id);
					}
				});
			minecraft.getResourcePackRepository().getAvailableIds().stream().filter(ExcaliburLook::isOurs).findFirst()
				.ifPresent(selected::add);
		}
		return !before.equals(selected);
	}

	private static void apply(Minecraft minecraft) {
		minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
		minecraft.options.save();
		minecraft.reloadResourcePacks();
	}
}
