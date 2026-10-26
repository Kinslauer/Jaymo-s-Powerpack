package com.jaymospowers.alchemy.codex;

import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Research;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The apothecary's first tools: the Scrying Lens ({@link LensItem}, {@link Lens}) that reads what things are made of,
 * and the Apothecary's Codex ({@link CodexItem}, the client's {@code CodexScreen}) where the insight it earns is spent
 * on research. This class registers both, the basic research entries, and handles unlocking.
 */
public final class Codex {
	public static Item LENS, CODEX;

	/**
	 * The local player, set by the client (null on a server and off the render thread): item names and tooltips that
	 * depend on what the viewer has learned ask this, since the item classes can't see client code.
	 */
	public static volatile Supplier<@Nullable Player> VIEWER = () -> null;

	private Codex() {
	}

	public static void init() {
		LENS = Alchemy.item("scrying_lens", p -> new LensItem(p.stacksTo(1)));
		CODEX = Alchemy.item("apothecary_codex", p -> new CodexItem(p.stacksTo(1)));
		CodexNet.init();

		// the basics: the first is free and learned on the first scan; the others are bought with insight
		Research.register(new Research.Entry(Lens.BASICS, Research.Category.BASICS, () -> CODEX, EssenceList.EMPTY, List.of(), 0, 0, 1));
		Research.register(new Research.Entry("essences", Research.Category.BASICS, () -> Alchemy.ESSENCE_PHIAL,
			EssenceList.of(Essence.EARTH, 2, Essence.WATER, 2), List.of(Lens.BASICS), 0, 1, 0));
		Research.register(new Research.Entry("scrying", Research.Category.BASICS, () -> LENS,
			EssenceList.of(Essence.FIRE, 2, Essence.AIR, 2), List.of(Lens.BASICS), 0, 1, 2));
	}

	/** Whether the viewer (the local player, on the client) has seen this essence; true when there is no viewer. */
	public static boolean seenByViewer(Essence essence) {
		Player viewer = VIEWER.get();
		return viewer == null || Knowledge.hasSeen(viewer, essence);
	}

	/** Tells a player they learned an entry. */
	static void learned(ServerPlayer player, Research.Entry entry) {
		player.sendSystemMessage(Component.translatable("message.jaymospowers.codex.learned", Component.translatable(entry.key()))
			.withStyle(net.minecraft.ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	/** The book asked to unlock {@code id}: checks the player carries a Codex, then {@link Knowledge#unlock}. */
	static void unlock(ServerPlayer player, String id) {
		Research.Entry entry = Research.get(id);
		if (entry == null || !player.isCreative() && !player.getInventory().contains(stack -> stack.is(CODEX))) {
			return;
		}
		if (Knowledge.of(player).research().contains(id)) {
			return;
		}
		if (Knowledge.unlock(player, entry)) {
			learned(player, entry);
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.codex.lacking"));
		}
	}
}
