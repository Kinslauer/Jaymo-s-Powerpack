package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * (0.17.46, Andy's NOEA picks) The eight technique slots, shown on the technique wheel (Andy: "Hold Y for a technique
 * wheel"): holding Y opens a ring of the eight, and pointing at one puts it on its key (G for the quick ones, sneak+V
 * for the charged ones). A learned technique takes the first free slot by itself; the training screen's Techniques
 * page adds or takes one off. The Spirit Bomb (C) and Instant Teleport (B) have keys of their own and never sit here.
 * Before this there were no slots, so a fighter who has none saved gets his known techniques in learning order.
 */
public final class ZFighterSlots {
	public static final int SLOTS = 8;
	/** An empty slot. */
	public static final int EMPTY = -1;

	public static final AttachmentType<List<Integer>> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_slots"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	private ZFighterSlots() {
	}

	/** Whether technique {@code index} can sit on the wheel. */
	public static boolean fits(int index) {
		return index >= 0 && index < ZFighterTraining.TECHNIQUES && index != ZFighterTraining.SPIRIT_BOMB && index != ZFighterTraining.TELEPORT;
	}

	/** His eight slots ({@link #EMPTY} where none), saved or, if none are, his known techniques in order. Both sides. */
	public static List<Integer> of(Player player) {
		List<Integer> saved = player.getAttached(DATA);
		List<Integer> slots = new ArrayList<>(SLOTS);
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		if (saved != null) {
			for (int i = 0; i < SLOTS; i++) {
				int index = i < saved.size() ? saved.get(i) : EMPTY;
				slots.add(fits(index) && data.knows(index) && !slots.contains(index) ? index : EMPTY);
			}
			return slots;
		}
		for (int index = 0; index < ZFighterTraining.TECHNIQUES && slots.size() < SLOTS; index++) {
			if (fits(index) && data.knows(index)) {
				slots.add(index);
			}
		}
		while (slots.size() < SLOTS) {
			slots.add(EMPTY);
		}
		return slots;
	}

	/** Server: a freshly learned technique takes the first free slot, if there is one. */
	static void add(ServerPlayer player, int index) {
		List<Integer> slots = of(player);
		int free = slots.indexOf(EMPTY);
		if (fits(index) && !slots.contains(index) && free >= 0) {
			slots.set(free, index);
		}
		player.setAttached(DATA, List.copyOf(slots));
	}

	/** Server: the training screen's wheel button: takes a technique off the wheel, or puts it in the first free slot. */
	static void toggle(ServerPlayer player, int index) {
		if (!fits(index) || !ZFighterTraining.of(player).knows(index)) {
			return;
		}
		List<Integer> slots = of(player);
		int at = slots.indexOf(index);
		if (at >= 0) {
			slots.set(at, EMPTY);
		} else {
			int free = slots.indexOf(EMPTY);
			if (free < 0) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wheel.full").withStyle(ChatFormatting.GRAY));
				return;
			}
			slots.set(free, index);
		}
		player.setAttached(DATA, List.copyOf(slots));
	}
}
