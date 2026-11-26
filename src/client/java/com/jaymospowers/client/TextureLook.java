package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.mojang.brigadier.Command;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Andy: "add one for the new textures we added, so players can go back to vanilla". {@code /texturelook vanilla} switches
 * off every resource pack the pack uses to change how the game's own (and other mods') things look: our built-in
 * {@link #BUILT_IN} packs (the Reason look, 3D items and mushrooms, bushy leaves and grass edges) and the downloaded
 * retexture and model packs in {@link #FILE_PACKS} (Pixel Perfection Fidelity, Medieval Items and the rest).
 * {@code /texturelook reason} switches them all back on, in their usual places, fetching any file that's missing.
 *
 * <p>Left on either way, since they aren't a look over vanilla: our mods' own textures (their new items, blocks and mobs
 * have no vanilla art to fall back on), the 3D guns, Klei_Wright's tundra art (our tundra's own blocks), the fixes packs,
 * the music, the medieval MCA villagers (the clothes the pack's people wear), Tissou's future zombies, Tiered Shields'
 * textures and the Fresh Animations packs (motion, not textures).
 *
 * <p>The choice is kept per player in {@link #FILE}: while it's there, {@link PackResourcePacks} doesn't switch on or
 * fetch any of these packs at launch, so a vanilla look stays vanilla after an update. Players can still switch single
 * packs on or off in Resource Packs.
 */
public final class TextureLook {
	static final String FILE = "jaymospowers-vanilla-look";

	/** Our built-in packs (their path, under our mod id) that change vanilla's look, in their usual pack order. */
	static final List<String> BUILT_IN = List.of("vanilla_style", "held_3d", "living_world");

	/** File name prefixes of {@link PackResourcePacks}' packs that retexture or remodel vanilla or other mods. */
	static final List<String> FILE_PACKS = List.of("Pixel Perfection Fidelity", "MedievalItems", "Framed Glass Panes",
		"RedBricks", "Medieval Tactical Shield", "Re-covered", "Fancy Crops", "§l3D§f Ladders",
		"§aRemodeled-Doors", "FabulousFences", "Armored Legacy", "Canoe boats", "Better Horses");

	private TextureLook() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("texturelook")
				.executes(c -> report(c.getSource()))
				.then(ClientCommands.literal("vanilla").executes(c -> pick(c.getSource(), true)))
				.then(ClientCommands.literal("reason").executes(c -> pick(c.getSource(), false)))));
	}

	/** True while the player picked the vanilla look. */
	static boolean vanilla() {
		return Files.exists(marker());
	}

	/** True for a {@link PackResourcePacks} pack the vanilla look keeps off. */
	static boolean isLookPack(String prefix) {
		return FILE_PACKS.contains(prefix);
	}

	private static Path marker() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static int report(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable(vanilla() ? "message.jaymospowers.texture_look.is_vanilla"
			: "message.jaymospowers.texture_look.is_reason"));
		return Command.SINGLE_SUCCESS;
	}

	private static int pick(FabricClientCommandSource source, boolean toVanilla) {
		Minecraft minecraft = source.getClient();
		// run after the command, so the reload doesn't happen in the middle of chat handling
		minecraft.execute(() -> {
			if (toVanilla) {
				toVanilla(minecraft);
			} else {
				toReason(minecraft);
			}
		});
		source.sendFeedback(Component.translatable(toVanilla ? "message.jaymospowers.texture_look.vanilla"
			: "message.jaymospowers.texture_look.reason"));
		return Command.SINGLE_SUCCESS;
	}

	private static void toVanilla(Minecraft minecraft) {
		try {
			Files.writeString(marker(), "The vanilla look is picked (/texturelook vanilla): the pack's retexture packs stay off. "
				+ "/texturelook reason, or deleting this file, switches them back on.\n");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't write {}", marker(), e);
		}
		if (minecraft.options.resourcePacks.removeIf(TextureLook::isLookId)) {
			minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
			minecraft.options.save();
			minecraft.reloadResourcePacks();
		}
	}

	private static void toReason(Minecraft minecraft) {
		try {
			Files.deleteIfExists(marker());
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't delete {}", marker(), e);
		}
		List<String> selected = minecraft.options.resourcePacks;
		// our built-ins first, above our other built-ins and just under the resource pack files; Pixel Perfection
		// Fidelity then goes in under them again (PackResourcePacks.BASE)
		int at = selected.size();
		for (int i = 0; i < selected.size(); i++) {
			if (selected.get(i).startsWith("file/")) {
				at = i;
				break;
			}
		}
		for (String path : BUILT_IN) {
			String id = builtInId(minecraft, path);
			if (id != null && !selected.contains(id)) {
				selected.add(Math.min(at, selected.size()), id);
				at++;
			}
		}
		// the file packs already in resourcepacks/ go back in their usual places (Pixel Perfection Fidelity under the
		// built-ins, its add-ons just above it), all in one reload; a missing one loses its once-only marker, so the usual
		// launch step fetches it and switches it on
		Path dir = minecraft.getResourcePackDirectory();
		Path config = FabricLoader.getInstance().getConfigDir();
		boolean missing = false;
		for (PackResourcePacks.Pack pack : PackResourcePacks.packs()) {
			if (!isLookPack(pack.prefix())) {
				continue;
			}
			String file = PackResourcePacks.find(dir, pack);
			String id = "file/" + file;
			if (file != null && minecraft.getResourcePackRepository().getPack(id) != null) {
				if (pack.olderFormat() && !minecraft.options.incompatibleResourcePacks.contains(id)) {
					minecraft.options.incompatibleResourcePacks.add(id);
				}
				if (!selected.contains(id)) {
					selected.add(PackResourcePacks.placeAt(selected, pack.place()), id);
				}
				continue;
			}
			missing = true;
			try {
				Files.deleteIfExists(config.resolve(pack.marker()));
			} catch (IOException e) {
				JaymosPowers.LOGGER.warn("Couldn't delete {}", pack.marker(), e);
			}
		}
		minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
		minecraft.options.save();
		minecraft.reloadResourcePacks();
		if (missing) {
			PackResourcePacks.enableOnce(minecraft);
		}
	}

	/** The id the game knows our built-in pack {@code path} by, or null when it isn't loaded. */
	private static String builtInId(Minecraft minecraft, String path) {
		return minecraft.getResourcePackRepository().getAvailableIds().stream()
			.filter(id -> id.contains(JaymosPowers.MOD_ID) && id.endsWith(path)).findFirst().orElse(null);
	}

	private static boolean isLookId(String id) {
		if (id.contains(JaymosPowers.MOD_ID)) {
			return BUILT_IN.stream().anyMatch(id::endsWith);
		}
		return FILE_PACKS.stream().anyMatch(prefix -> id.startsWith("file/" + prefix));
	}
}
