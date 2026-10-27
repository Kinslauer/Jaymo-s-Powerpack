package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.50, Andy's NOEA picks) The god form ({@link ZFighterForms#GOD}, form 6): red hair, a calm aura, near Primal
 * Super's strength at a third of the ki. Lifted at the Lookout by five ({@link ZFighterGodRitual}); once the ring has
 * done it, it is his for good (kept through death) and taken like the other forms: C from the top of his line (Super 3,
 * Primal Super or Legend 3), 100 ki (free from half mastery), sneak+C back down. In it C still raises the Spirit Bomb.
 * Its mastery (0-10000 like the other forms') grows with blows and time spent in it and opens silver instinct at
 * {@link ZFighterInstinct#MASTERY}. No Limit Break on it.
 */
public final class ZFighterGod {
	/** The god form is his. Saved, kept through death, synced to him. */
	public static final AttachmentType<Boolean> UNLOCKED = AttachmentRegistry.create(JaymosPowers.id("zfighter_god_unlocked"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	/** Mastery of the god form, 0-{@link ZFighterTraining#MASTERY_MAX}. Saved, kept through death, synced to him. */
	public static final AttachmentType<Integer> MASTERY = AttachmentRegistry.create(JaymosPowers.id("zfighter_god_mastery"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** The calm of it: a few motes of red-gold light drifting up every this many ticks. */
	static final int GLOW_MOTES = 3;

	private ZFighterGod() {
	}

	public static boolean unlocked(Player player) {
		return Boolean.TRUE.equals(player.getAttached(UNLOCKED));
	}

	public static int mastery(Player player) {
		return player.getAttachedOrElse(MASTERY, 0);
	}

	static void unlock(ServerPlayer player) {
		player.setAttached(UNLOCKED, true);
	}

	static void lock(ServerPlayer player) {
		player.removeAttached(UNLOCKED);
	}

	static void setMastery(ServerPlayer player, int value) {
		player.setAttached(MASTERY, Math.clamp(value, 0, ZFighterTraining.MASTERY_MAX));
	}

	/** Mastery from a blow or time in the form (already scaled by his training rate). */
	static void master(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return;
		}
		int before = mastery(player);
		int after = Math.min(ZFighterTraining.MASTERY_MAX, before + amount);
		if (after == before) {
			return;
		}
		player.setAttached(MASTERY, after);
		for (int threshold : new int[] {ZFighterTraining.NEXT_FORM_MASTERY, ZFighterTraining.FREE_ENTRY_MASTERY, ZFighterTraining.MASTERY_MAX}) {
			if (before < threshold && after >= threshold) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_mastery_" + threshold / 100,
					Component.translatable(ZFighterLineage.formKey(player, ZFighterForms.GOD))).withStyle(ChatFormatting.GOLD));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
			}
		}
		if (before < ZFighterInstinct.MASTERY && after >= ZFighterInstinct.MASTERY) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_instinct_ready").withStyle(ChatFormatting.AQUA));
		}
	}

	/** C at the top of his line with the god form unlocked: into it, for {@code ZFighterPower.FORM_ENTER_KI} unless it is half mastered. */
	static void enter(ServerPlayer player, ZFighterPower.Live live) {
		boolean free = player.isCreative() || mastery(player) >= ZFighterTraining.FREE_ENTRY_MASTERY;
		int cost = ZFighterPower.FORM_ENTER_KI[ZFighterForms.GOD];
		if (!free && live.ki < cost) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!free) {
			live.ki -= cost;
		}
		ZFighterPower.transform(player, live, ZFighterForms.GOD);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_god_enter").withStyle(ChatFormatting.RED));
	}

	/** Standing in the god form: a few calm motes of red-gold rising off him, the quiet of a form that doesn't need to roar. */
	static void glow(ServerPlayer player) {
		ServerLevel level = player.level();
		if (player.isInvisible() || player.isSpectator()) {
			return;
		}
		level.sendParticles(new DustParticleOptions(ZFighterForms.color(ZFighterForms.GOD), 0.9F), true, false, player.getX(), player.getY() + 0.8,
			player.getZ(), GLOW_MOTES, 0.45, 0.8, 0.45, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.7, 0.4, 0.01);
	}
}
