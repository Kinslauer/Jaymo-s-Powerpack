package com.jaymospowers.client.sound;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crafting and workstation sounds (Andy's ImmersiveFX pick, built as our own): each station sounds as you open it (a
 * crafting table's knock, an anvil's ring, a loom's clack, a stonecutter's whirr, a smithing table's tink, a furnace's
 * hatch and roar, a grindstone's scrape, a cartography table's paper, a brewing stand's glass, an enchanting table's pages),
 * the work sounds as you take what you made (by what it's made of at a crafting table, by the station elsewhere), and a
 * furnace sizzles each time an item is done while you watch it. Our own stations get theirs: the Tinkers tables and the
 * smeltery by their screens, and the ones you work in the world by using them: the crucible and alembics, the smith's
 * anvils and forges, the lapidary wheel, and Kitchen's pots, pans, stoves, boards, presses and grindstones.
 * <p>All heard on this side alone, at your own position, through {@link ImmersiveSounds#play}.
 */
public final class Workstations {
	static final float OPEN_VOLUME = 0.45F, CRAFT_VOLUME = 0.4F, SMELTED_VOLUME = 0.35F, USE_VOLUME = 0.4F;
	/** A furnace's progress falling from over this to under that between ticks is an item done. */
	static final float DONE_FROM = 0.75F, DONE_TO = 0.25F;
	/** A station worked in the world sounds at most once in this many ticks. */
	static final int USE_COOLDOWN = 12;

	private static final Map<Screen, Boolean> OPENED = new WeakHashMap<>();
	private static final Map<Screen, Float> PROGRESS = new WeakHashMap<>();
	private static long lastUse = -USE_COOLDOWN;
	private static @Nullable BlockPos lastUsePos;

	private Workstations() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			String station = station(screen, container.getMenu());
			if (station != null && OPENED.put(screen, Boolean.TRUE) == null) {
				here("station." + station, OPEN_VOLUME);
			}
			if (container.getMenu() instanceof AbstractFurnaceMenu) {
				ScreenEvents.afterTick(screen).register(Workstations::furnaceTick);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				used(level, hit.getBlockPos(), player.getItemInHand(hand).getItem());
			}
			return InteractionResult.PASS;
		});
	}

	/** Which station a container screen is, or null for one that isn't a workstation (a chest, your inventory). */
	static @Nullable String station(Screen screen, AbstractContainerMenu menu) {
		String cls = screen.getClass().getName();
		if (cls.startsWith("com.jaymospowers.client.tinkering.")) {
			return cls.endsWith("SmelteryScreen") ? "smeltery" : "tinker";
		}
		if (cls.startsWith("com.jaymoskitchen.")) {
			return "kitchen";
		}
		if (menu instanceof CraftingMenu) {
			return "crafting";
		}
		if (menu instanceof AnvilMenu) {
			return "anvil";
		}
		if (menu instanceof SmithingMenu) {
			return "smithing";
		}
		if (menu instanceof LoomMenu) {
			return "loom";
		}
		if (menu instanceof StonecutterMenu) {
			return "stonecutter";
		}
		if (menu instanceof AbstractFurnaceMenu) {
			return "furnace";
		}
		if (menu instanceof GrindstoneMenu) {
			return "grindstone";
		}
		if (menu instanceof CartographyTableMenu) {
			return "cartography";
		}
		if (menu instanceof BrewingStandMenu) {
			return "brewing";
		}
		if (menu instanceof EnchantmentMenu) {
			return "enchanting";
		}
		return null;
	}

	/** (AbstractContainerScreen mixin) A slot was clicked: taking a made thing from a result slot is the work being done. */
	public static void clicked(AbstractContainerScreen<?> screen, @Nullable Slot slot, ContainerInput input) {
		if (slot == null || !slot.hasItem() || input == ContainerInput.CLONE || input == ContainerInput.QUICK_CRAFT
			|| input == ContainerInput.PICKUP_ALL) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (slot instanceof FurnaceResultSlot) {
			here("craft.smelted", SMELTED_VOLUME);
			return;
		}
		if (!result(menu, slot)) {
			return;
		}
		String station = menu instanceof InventoryMenu ? "crafting" : station(screen, menu);
		String craft = switch (station == null ? "" : station) {
			case "anvil", "smithing" -> "metal";
			case "loom", "cartography" -> "cloth";
			case "stonecutter", "grindstone" -> "stone";
			case "tinker", "smeltery" -> screen.getClass().getName().endsWith("FletchingScreen") ? "wood" : "tinker";
			case "kitchen" -> "food";
			case "crafting" -> switch (SoundKinds.stuff(slot.getItem())) {
				case METAL, COIN -> "metal";
				case STONE, GLASS, GEM -> "stone";
				case SOFT, PAPER -> "cloth";
				default -> "wood";
			};
			default -> null;
		};
		if (craft != null) {
			here("craft." + craft, CRAFT_VOLUME);
		}
	}

	/** A slot that holds what a station made (its result slot), not one of its inputs or your inventory. */
	private static boolean result(AbstractContainerMenu menu, Slot slot) {
		if (slot instanceof ResultSlot || slot.container instanceof ResultContainer) {
			return true;
		}
		if (menu instanceof StonecutterMenu) {
			return slot.index == StonecutterMenu.RESULT_SLOT;
		}
		if (menu instanceof LoomMenu) {
			return slot.index == 3;
		}
		if (menu instanceof GrindstoneMenu) {
			return slot.index == 2;
		}
		return false;
	}

	private static void furnaceTick(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container) || !(container.getMenu() instanceof AbstractFurnaceMenu furnace)) {
			return;
		}
		float now = furnace.getBurnProgress();
		Float last = PROGRESS.put(screen, now);
		if (last != null && last > DONE_FROM && now < DONE_TO) {
			here("craft.smelted", SMELTED_VOLUME);
		}
	}

	/** A station you work in the world (it has no screen of its own) sounds as you use it. */
	private static void used(Level level, BlockPos pos, net.minecraft.world.item.Item held) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
		String sound = worked(id, BuiltInRegistries.ITEM.getKey(held).getPath());
		long now = ImmersiveSounds.tick();
		if (sound == null || (pos.equals(lastUsePos) && now - lastUse < USE_COOLDOWN)) {
			return;
		}
		lastUse = now;
		lastUsePos = pos.immutable();
		if (level instanceof ClientLevel client) {
			ImmersiveSounds.play(client, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, USE_VOLUME,
				0.95F + client.getRandom().nextFloat() * 0.1F);
		}
	}

	/** The sound one of the in-world stations makes as it's used (null for any other block). */
	static @Nullable String worked(Identifier block, String held) {
		String path = block.getPath();
		if (block.getNamespace().equals(JaymosPowers.MOD_ID)) {
			if (path.contains("crucible") || path.contains("alembic")) {
				return "station.crucible";
			}
			if (path.endsWith("_anvil") || path.endsWith("_forge") && !path.equals("tool_forge")) {
				return "station.anvil";
			}
			if (path.equals("forge_hearth")) {
				return "station.furnace";
			}
			if (path.equals("lapidary_wheel")) {
				return "station.grindstone";
			}
			return null;
		}
		if (block.getNamespace().equals("jaymoskitchen")) {
			if (path.contains("cutting_board") || path.contains("butcher_block") || path.contains("chopping_block") || path.equals("chopper")) {
				return held.contains("knife") || held.contains("cleaver") || held.contains("axe") ? "craft.food" : "station.kitchen";
			}
			if (path.contains("grindstone") || path.contains("press") || path.equals("potters_wheel")) {
				return "station.grindstone";
			}
			if (path.equals("cooking_pot") || path.equals("frying_pan") || path.equals("stove") || path.equals("hearth") || path.equals("spit")
				|| path.equals("icebox") || path.equals("still") || path.equals("cask") || path.equals("drying_rack")) {
				return "station.kitchen";
			}
		}
		return null;
	}

	private static void here(String sound, float volume) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && mc.level != null) {
			ImmersiveSounds.play(mc.level, player.getX(), player.getEyeY() - 0.4, player.getZ(), sound, SoundSource.PLAYERS, volume,
				0.95F + mc.level.getRandom().nextFloat() * 0.1F);
		}
	}
}
