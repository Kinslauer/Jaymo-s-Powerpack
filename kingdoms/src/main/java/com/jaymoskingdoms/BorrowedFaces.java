package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A changeling's borrowed face (Andy, 2026-11-14: the shapeshifters' trick "should also be how sylvan changelings function",
 * and what is copied is always an MCA villager's look). A Sylvan changeling's Mirror Shift, used on an MCA villager, puts that
 * villager's look on the player ({@link McaBridge#wearLook}: skin, face, hair, clothes, build). It holds through a relog, and
 * comes off when they use Mirror Shift with nobody in sight, when they are hurt to {@link #PAIN_SLIP} of their health (as a
 * shapeshifter's does, {@link Doubles}), or when they die.
 */
public final class BorrowedFaces {
	/** Hurt down to this share of their health, the face slips. */
	static final float PAIN_SLIP = 0.5F;

	/** On a player wearing a borrowed face: the MCA look that is their own, to go back to. */
	public static final AttachmentType<CompoundTag> OWN = AttachmentRegistry.create(JaymosKingdoms.id("own_face"),
		b -> b.persistent(CompoundTag.CODEC));

	private BorrowedFaces() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && p.hasAttached(OWN) && p.getHealth() <= p.getMaxHealth() * PAIN_SLIP) {
				drop(p);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				drop(p);
			}
		});
	}

	/** {@code p} takes {@code v}'s face (dropping one already worn). False if {@code v} has no MCA look to take. */
	static boolean wear(ServerPlayer p, Villager v) {
		CompoundTag own = p.getAttached(OWN);
		if (own != null) {
			// (back to their own first, so the look kept is theirs and not the last face's)
			McaCompat.setLook(p, own);
		}
		CompoundTag had = McaCompat.wearLook(p, v);
		if (had == null) {
			return false;
		}
		p.setAttached(OWN, had);
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.face.worn", v.getName()).withStyle(ChatFormatting.GRAY), true);
		return true;
	}

	/** {@code p}'s borrowed face comes off. False if they wore none. */
	static boolean drop(ServerPlayer p) {
		CompoundTag own = p.removeAttached(OWN);
		if (own == null) {
			return false;
		}
		McaCompat.setLook(p, own);
		ServerLevel level = p.level();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.PLAYERS, 0.7F, 1.2F);
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.face.dropped").withStyle(ChatFormatting.GRAY), true);
		return true;
	}
}
