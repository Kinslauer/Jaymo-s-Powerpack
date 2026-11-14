package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * A changeling's borrowed face (Andy, 2026-11-14: the shapeshifters' trick "should also be how sylvan changelings function",
 * and what is copied is always an MCA villager's look). A Sylvan changeling's Mirror Shift, used on an MCA villager, puts that
 * villager's look on the player ({@link McaBridge#wearLook}: skin, face, hair, clothes, build). It holds through a relog, and
 * comes off when they use Mirror Shift with nobody in sight, when they are hurt to {@link #PAIN_SLIP} of their health (as a
 * shapeshifter's does, {@link Doubles}), or when they die.
 *
 * <p><b>Found out</b> (Andy, 2026-11-14: "If you are found out and you lied or hurt the person you changed into the villagers
 * will attack, and be suspicious as anyone could be a changeling or shifter now"). A face that comes off where a villager can
 * see it ({@link #SEEN}) is a face found out. If the wearer wronged the one they wore (struck them, before or after taking
 * the face, or lied to a villager who asked them something only that one would know, {@link Doubts}), whoever saw it
 * reacts as to a shapeshifter ({@link ShapeshifterAlarm}): the guards and the brave attack, the rest run and cry out.
 * Otherwise they only cry out. Either way the settlement is left in doubt ({@link Doubts#raise}).
 */
public final class BorrowedFaces {
	/** Hurt down to this share of their health, the face slips. */
	static final float PAIN_SLIP = 0.5F;

	/** A villager this close (blocks), with a clear sight of it, sees a face come off. */
	static final double SEEN = 24.0;

	/** On a player wearing a borrowed face: whose it is, and whether the wearer has wronged them (struck them, or lied as them). */
	public static final AttachmentType<UUID> FACE_OF = AttachmentRegistry.create(JaymosKingdoms.id("face_of"), b -> b.persistent(UUIDUtil.CODEC));
	public static final AttachmentType<Boolean> WRONGED = AttachmentRegistry.create(JaymosKingdoms.id("face_wronged"),
		b -> b.persistent(com.mojang.serialization.Codec.BOOL));
	/** Whom each player has struck since the server started (player, then villagers). */
	private static final Map<UUID, Set<UUID>> STRUCK = new HashMap<>();

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
			if (entity instanceof Villager v && source.getEntity() instanceof ServerPlayer p) {
				STRUCK.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(v.getUUID());
				if (v.getUUID().equals(p.getAttached(FACE_OF))) {
					p.setAttached(WRONGED, true);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				// (dead, nobody is left to be found out)
				shed(p);
			}
		});
		Doubts.register();
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
		p.setAttached(FACE_OF, v.getUUID());
		if (STRUCK.getOrDefault(p.getUUID(), Set.of()).contains(v.getUUID())) {
			p.setAttached(WRONGED, true);
		} else {
			p.removeAttached(WRONGED);
		}
		if (McaCompat.knows(v, p)) {
			p.setAttached(Doubts.KNEW, true);
		} else {
			p.removeAttached(Doubts.KNEW);
		}
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.face.worn", v.getName()).withStyle(ChatFormatting.GRAY), true);
		return true;
	}

	/** Whose face {@code p} wears, or null. */
	static @Nullable UUID faceOf(ServerPlayer p) {
		return p.hasAttached(OWN) ? p.getAttached(FACE_OF) : null;
	}

	/** {@code p}, wearing a face, has lied as the one they wear. */
	static void lied(ServerPlayer p) {
		if (p.hasAttached(OWN)) {
			p.setAttached(WRONGED, true);
		}
	}

	/** {@code p} is back in their own look, with nothing said or seen. False if they wore no face. */
	private static boolean shed(ServerPlayer p) {
		CompoundTag own = p.removeAttached(OWN);
		p.removeAttached(FACE_OF);
		p.removeAttached(Doubts.KNEW);
		if (own == null) {
			p.removeAttached(WRONGED);
			return false;
		}
		McaCompat.setLook(p, own);
		return true;
	}

	/** {@code p}'s borrowed face comes off, and any villager who sees it has found them out. False if they wore none. */
	static boolean drop(ServerPlayer p) {
		UUID wore = p.getAttached(FACE_OF);
		Component woreName = p.level().getEntity(wore == null ? p.getUUID() : wore) instanceof Villager v ? v.getName() : p.getName();
		boolean wronged = Boolean.TRUE.equals(p.removeAttached(WRONGED));
		if (!shed(p)) {
			return false;
		}
		ServerLevel level = p.level();
		Villager witness = null;
		for (Villager w : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(SEEN),
			w -> w.isAlive() && !w.isBaby() && !w.isSleeping() && !Downed.isDowned(w) && !Doubles.isCopy(w) && w.hasLineOfSight(p))) {
			witness = w;
			break;
		}
		if (witness != null) {
			Doubts.raise(level, p.blockPosition());
			if (wronged && !p.isCreative()) {
				ShapeshifterAlarm.seen(level, p, woreName, null, wore);
			} else {
				Speech.say(level, witness, Component.translatable("talk.jaymoskingdoms.shapeshifter.cry." + level.getRandom().nextInt(ShapeshifterAlarm.CRY_LINES),
					woreName), Speech.RED, SEEN);
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.PLAYERS, 0.7F, 1.2F);
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.face.dropped").withStyle(ChatFormatting.GRAY), true);
		return true;
	}
}
