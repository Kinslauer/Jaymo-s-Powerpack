package com.jaymospowers.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy's GT New Horizons picks (docs/GTNH-Plan.md, patch 1): what mends and what can't be mended.
 * <ul>
 * <li>Overslime ({@link TinkerTool#tick}) and the Mending enchantment ({@code mixin/ArmourWearMendingMixin}) do nothing
 * until the holder has Kingdoms' Knowledge {@link #SELF_MEND_KNOWLEDGE} (the Skeleton Lord slain). Without Kingdoms
 * there is no Knowledge to earn, so they mend as they always did.</li>
 * <li>Wood and flint tools can't be mended ({@link #unmendable}): not at the Tool Station, the anvil
 * ({@code mixin/UnmendableAnvilMixin}) or by putting two together in the grid ({@code mixin/UnmendableGridRepairMixin}).</li>
 * </ul>
 */
public final class Mending {
	/** The Knowledge level a holder needs before Overslime and Mending work. */
	public static final int SELF_MEND_KNOWLEDGE = 3;
	/** Plain items that are wood or flint tools (vanilla's wooden tools, the Stone Age weapons): a data tag. */
	public static final TagKey<Item> UNMENDABLE = TagKey.create(Registries.ITEM, JaymosPowers.id("unmendable"));

	private Mending() {
	}

	/** Whether gear on {@code holder} may mend itself (Overslime, Mending). */
	public static boolean selfMends(@Nullable Entity holder) {
		return holder instanceof Player player && (player.hasInfiniteMaterials() || KingdomsCompat.knowledge(player) >= SELF_MEND_KNOWLEDGE);
	}

	/** A wood or flint tool: in {@link #UNMENDABLE}, or a Tinkering tool whose head is wood or flint. */
	public static boolean unmendable(ItemStack stack) {
		if (stack.is(UNMENDABLE)) {
			return true;
		}
		if (!(stack.getItem() instanceof TinkerTool)) {
			return false;
		}
		List<TinkerMaterial> materials = ToolStats.materials(stack);
		if (materials.isEmpty()) {
			return false;
		}
		TinkerMaterial head = materials.getFirst();
		return head == TinkerMaterial.WOOD || head == TinkerMaterial.WOOD_CRIMSON || head == TinkerMaterial.WOOD_WARPED
			|| head == TinkerMaterial.FLINT;
	}
}
