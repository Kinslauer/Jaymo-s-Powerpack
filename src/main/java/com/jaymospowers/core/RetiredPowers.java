package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The Reactor Heart, the Blinker and the Lawgiver came in as powers of their own with the Awakening picks; Andy never meant
 * them as such (2026-11-18). The heart now beats in the Legendary Z Fighter (its heat and moves, in the legendary forms),
 * the blinks are the Void Arsenal's (each through a rift), and the rulebook is how a Paladin's oath and a Warlock's pact are
 * written (Jaymo's Kingdoms). A player who still holds one of the three when they join is moved over, once:
 * <ul>
 * <li>a Reactor Heart wakes again as a Z Fighter;</li>
 * <li>a Blinker wakes again with the Pocket Arsenal;</li>
 * <li>a Lawgiver wakes again to a power fate picks.</li>
 * </ul>
 * Either way it's a fresh awakening (its first ability, the rest with mastery). One only called by one of them (not yet
 * awake) loses the calling, and an affinity or an inherited power naming one is cleared.
 */
public final class RetiredPowers {
	/** The retired powers' ids. */
	static final Set<Identifier> RETIRED = Set.of(JaymosPowers.id("reactor"), JaymosPowers.id("blinker"), JaymosPowers.id("rules"));

	/** The Lawgiver's old rulebook. Still registered so old saves load; dropped when its holder is moved over. */
	public static final AttachmentType<List<Integer>> OLD_RULEBOOK = AttachmentRegistry.create(JaymosPowers.id("rules_written"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

	private RetiredPowers() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> move(handler.player)));
	}

	private static boolean retired(@Nullable Identifier id) {
		return id != null && RETIRED.contains(id);
	}

	/** A holder of a retired power is moved over (once: afterwards they hold another). */
	static void move(ServerPlayer player) {
		player.removeAttached(OLD_RULEBOOK);
		player.removeAttached(Callings.BLINKS);
		if (retired(player.getAttached(Callings.CALLING))) {
			player.removeAttached(Callings.CALLING);
		}
		if (retired(player.getAttached(Affinity.AFFINITY))) {
			player.removeAttached(Affinity.AFFINITY);
		}
		if (retired(player.getAttached(Affinity.INHERITED))) {
			player.removeAttached(Affinity.INHERITED);
		}
		Identifier held = player.getAttached(Powers.POWER);
		if (!retired(held)) {
			return;
		}
		String path = held.getPath();
		// the old power's id isn't registered any more: off it comes, then the new awakening
		player.removeAttached(Powers.POWER);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.retired." + path).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		switch (path) {
			case "reactor" -> Powers.awakenAs(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE);
			case "blinker" -> Powers.awakenAs(player, com.jaymospowers.power.arsenal.ArsenalPower.INSTANCE);
			default -> Powers.awaken(player);
		}
		JaymosPowers.LOGGER.info("Moved {} off the retired power {}", player.getPlainTextName(), held);
	}
}
