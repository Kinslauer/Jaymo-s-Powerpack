package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Rig's finishes: a painted layer over the plates (client RigSuit draws it, on every frame and under any paint), each
 * earned by a deed done in the suit and then offered at the Rig Forge's screen for good:
 * <ul>
 * <li>{@link #DEEPWALKER}: kill a Warden wearing the whole set. Veins of light across the plates.</li>
 * <li>{@link #SKYBREAKER}: crack the air in full flight at {@link #SKY_HEIGHT} or higher. Swept bands of pale sky.</li>
 * <li>{@link #LAST_STAND}: have the parked suit come for you on its own ({@link RigCall}'s failsafe). Soot and embers.</li>
 * </ul>
 * What is earned is kept on the player ({@link #EARNED}, through death); the finish picked is on each piece
 * ({@link Rig#FINISH}). Looks only: a finish changes nothing the suit does.
 */
public final class RigFinish {
	public static final int NONE = 0, DEEPWALKER = 1, SKYBREAKER = 2, LAST_STAND = 3, COUNT = 4;
	/** Their names, for lang keys and textures. */
	public static final String[] NAMES = {"none", "deepwalker", "skybreaker", "last_stand"};
	/** Skybreaker: the height the air has to be cracked at or over. */
	static final int SKY_HEIGHT = 192;
	/** The Forge has to be this near to put a finish on (blocks). */
	static final double FORGE_REACH = 8.0;

	/** The finishes earned, a bit each. */
	public static final AttachmentType<Integer> EARNED = AttachmentRegistry.create(JaymosPowers.id("rig_finishes"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private RigFinish() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Warden && source.getEntity() instanceof ServerPlayer player && Rig.fullSet(player)) {
				earn(player, DEEPWALKER);
			}
		});
	}

	/** Whether this player may wear this finish (everything in creative). */
	public static boolean has(Player player, int finish) {
		return finish == NONE || finish > NONE && finish < COUNT && (player.isCreative() || (player.getAttachedOrElse(EARNED, 0) & 1 << finish) != 0);
	}

	/** The finish on this piece. */
	public static int of(ItemStack stack) {
		Integer finish = stack.get(Rig.FINISH);
		return finish == null || finish <= NONE || finish >= COUNT ? NONE : finish;
	}

	static void earn(ServerPlayer player, int finish) {
		int earned = player.getAttachedOrElse(EARNED, 0);
		if ((earned & 1 << finish) != 0) {
			return;
		}
		player.setAttached(EARNED, earned | 1 << finish);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.rig.finish_earned",
			Component.translatable("gui.jaymospowers.rig_finish." + NAMES[finish])).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	/** The Forge screen's pick: the finish goes on (or, for {@link #NONE}, comes off) every Rig piece worn. */
	static void set(ServerPlayer player, BlockPos forge, int finish) {
		if (!(player.level().getBlockEntity(forge) instanceof RigForgeEntity) || !forge.closerToCenterThan(player.position(), FORGE_REACH)
			|| !has(player, finish)) {
			return;
		}
		int pieces = 0;
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			if (piece.item() == null || !worn.is(piece.item())) {
				continue;
			}
			if (finish == NONE) {
				worn.remove(Rig.FINISH);
			} else {
				worn.set(Rig.FINISH, finish);
			}
			pieces++;
		}
		if (pieces == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.finish_needs_suit"));
			return;
		}
		player.level().playSound(null, forge, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
	}
}
