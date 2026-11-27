package com.jaymoskitchen.corpse;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The harness a dead mob wore (Jaymo's Kingdoms: padding, mail, hose, a hood and the rest, one stack per place, in the
 * order of its places): saved with the mob, so it is in the body's look. The loot screen reads it from there and writes it
 * back there, so the look stays the one place it is kept (a body that gets up as a zombie, a reload and a second person
 * looking all see what is left). Kitchen never compiles against Kingdoms, so it is found by the id Kingdoms registered its
 * attachment under.
 * <p>
 * Where: the look is the mob's own saved data ({@code Corpses#look}, from {@code saveWithoutId}), and Fabric's data
 * attachments write a persistent attachment into it as {@code fabric:attachments} (a compound, from
 * {@code AttachmentSerializingImpl.serializeAttachmentData}, checked in the fabric-data-attachment-api-v1 jar) holding one
 * entry per attachment, keyed by its id. Kingdoms' harness is that entry, a list of {@code ItemStack.OPTIONAL_CODEC} stacks.
 */
final class CorpseHarness {
	static final String KEY = "jaymoskingdoms:harness";
	/** Fabric's key for the mob's persistent attachments in its saved data. */
	static final String ATTACHMENTS = "fabric:attachments";

	private CorpseHarness() {
	}

	/** The look's attachments compound if it holds a harness list, or null. */
	private static @Nullable CompoundTag holder(CompoundTag look) {
		return look.get(ATTACHMENTS) instanceof CompoundTag attachments && attachments.get(KEY) instanceof ListTag ? attachments : null;
	}

	/** How many places the look's harness has (0 if it is bare or it has none); this many slots are shown on the screen. */
	static int places(CompoundTag look) {
		CompoundTag holder = holder(look);
		if (holder == null || !(holder.get(KEY) instanceof ListTag list) || !anyPiece(list)) {
			return 0;
		}
		return Math.min(list.size(), CorpseEntity.EXTRA_SLOTS);
	}

	/** Whether the look still has a piece of harness on it. */
	static boolean present(CompoundTag look) {
		return places(look) > 0;
	}

	private static boolean anyPiece(ListTag list) {
		for (Tag entry : list) {
			if (entry instanceof CompoundTag piece && !piece.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The pieces in the look's harness, by place (empty stacks for bare places); none if it has none. */
	static ItemStack[] read(CompoundTag look, RegistryOps<Tag> ops) {
		CompoundTag holder = holder(look);
		if (holder == null || !(holder.get(KEY) instanceof ListTag list) || !anyPiece(list)) {
			return new ItemStack[0];
		}
		List<ItemStack> pieces = ItemStack.OPTIONAL_CODEC.listOf().parse(ops, list).result().orElse(List.of());
		ItemStack[] out = new ItemStack[Math.min(pieces.size(), CorpseEntity.EXTRA_SLOTS)];
		for (int i = 0; i < out.length; i++) {
			out[i] = pieces.get(i);
		}
		return out;
	}

	/**
	 * Puts {@code pieces} back as the look's harness (the key is dropped once nothing is left, as Kingdoms drops it from a
	 * bare wearer). Nothing happens if the look has no harness to write to.
	 */
	static void write(CompoundTag look, ItemStack[] pieces, RegistryOps<Tag> ops) {
		CompoundTag holder = holder(look);
		if (holder == null) {
			return;
		}
		boolean bare = true;
		for (ItemStack piece : pieces) {
			bare &= piece.isEmpty();
		}
		if (bare) {
			holder.remove(KEY);
			if (holder.isEmpty()) {
				look.remove(ATTACHMENTS);
			}
			return;
		}
		Tag saved = ItemStack.OPTIONAL_CODEC.listOf().encodeStart(ops, List.of(pieces)).result().orElse(null);
		if (saved != null) {
			holder.put(KEY, saved);
		}
	}
}
