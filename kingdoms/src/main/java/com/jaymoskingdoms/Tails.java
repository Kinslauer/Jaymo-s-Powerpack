package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.7.10) Jaymo's Powers' Warrior-born Z Fighters have a tail. Townsfolk who see it out take them for a werewolf or
 * some other monster and run ({@link Frights}, crying out as they go), and keep running every time they see it, until
 * the player has made a friend of them: at {@link #EXPLAIN_HEARTS} hearts with an MCA villager the chat menu gains
 * "Explain your tail", and once told, that villager knows them and is never frightened by the tail again. Each
 * villager has to be told on their own. A wrapped tail (sneak+Z) shows nothing, which is how a bond is built at all.
 *
 * <p>Read by entity tag ({@link #TAILED}, {@link #TAIL}, set by Powers), so Kingdoms needs no Powers classes.
 */
public final class Tails {
	/** On a Warrior-born; on one whose tail is out right now. */
	static final String TAILED = "jaymospowers_tailed", TAIL = "jaymospowers_tail";
	/** The MCA hearts a villager needs with you before you can explain. */
	public static final int EXPLAIN_HEARTS = 25;
	/** The hearts telling them earns. */
	static final int EXPLAINED_HEARTS = 5;
	/** The chat menu answer (MCA dialogue {@code main}). */
	public static final String ANSWER = "jaymoskingdoms_tail";

	/** Villagers (by uuid) this player has explained the tail to; kept across deaths. */
	static final AttachmentType<List<String>> EXPLAINED = AttachmentRegistry.create(JaymosKingdoms.id("tail_explained"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private Tails() {
	}

	static boolean tailOut(Entity e) {
		return e.entityTags().contains(TAIL);
	}

	static boolean explained(ServerPlayer player, UUID villager) {
		List<String> told = player.getAttached(EXPLAINED);
		return told != null && told.contains(villager.toString());
	}

	/** Whether {@code v} sees a monster in {@code player}: the tail's out and nobody's explained it to them. */
	static boolean feared(ServerPlayer player, Villager v) {
		return !player.isCreative() && !player.isSpectator() && tailOut(player) && !explained(player, v.getUUID());
	}

	/** Whether "Explain your tail" shows in {@code villager}'s chat menu for {@code player}. */
	public static boolean canExplain(ServerPlayer player, Villager villager) {
		return player.entityTags().contains(TAILED) && !explained(player, villager.getUUID())
			&& McaCompat.heartsOf(villager, player) >= EXPLAIN_HEARTS;
	}

	/** {@code player} explains: {@code villager} understands, says so, and fears the tail no more. */
	public static void explain(ServerPlayer player, Villager villager) {
		if (!canExplain(player, villager)) {
			return;
		}
		List<String> told = new ArrayList<>(player.getAttachedOrElse(EXPLAINED, List.of()));
		told.add(villager.getUUID().toString());
		player.setAttached(EXPLAINED, told);
		McaCompat.hearts(villager, player, EXPLAINED_HEARTS);
		villager.level().playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}
}
