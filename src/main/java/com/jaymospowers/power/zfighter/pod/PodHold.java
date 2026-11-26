package com.jaymospowers.power.zfighter.pod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * The pod's cargo hold (the {@link PodModules.Kind#HOLD} module): a chest's screen over the pod's own container, one row of nine
 * slots, two or three with a better class ({@link PodClass#holdRows}). Opened from the pod's bay; what is in it travels with the
 * pod on every trip ({@link PodEntity.Extras}).
 */
public final class PodHold {
	private PodHold() {
	}

	/** Opens the hold of {@code pod} for {@code player}. */
	static void open(ServerPlayer player, PodEntity pod) {
		int rows = PodClass.of(pod).holdRows;
		MenuType<ChestMenu> type = switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			default -> MenuType.GENERIC_9x3;
		};
		player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new ChestMenu(type, id, inventory, pod.holdContainer(), rows),
			Component.translatable("container.jaymospowers.pod_hold")));
	}
}
