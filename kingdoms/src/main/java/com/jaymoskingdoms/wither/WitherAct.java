package com.jaymoskingdoms.wither;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.jspecify.annotations.Nullable;

/**
 * What the Wither is doing in its fight right now, told to everyone who can see it, so its own look (the client's
 * WitherRenderer) plays the move's animation from when it began: the beam's tell and shot, the charge's tell and rush,
 * the climb before the skull rain, throwing blue skulls, a skull thrown at a house, and the daze after a blocked charge.
 * Nothing at all while it only circles or hangs in the air (the renderer works those out from how it moves).
 */
public final class WitherAct {
	/** The move's animation, by name, and the game time it began. */
	public record Act(String name, long start) {
		static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Act::name,
			ByteBufCodecs.VAR_LONG, Act::start, Act::new);
	}

	public static final AttachmentType<Act> ACT = AttachmentRegistry.create(JaymosKingdoms.id("wither_act"),
		builder -> builder.syncWith(Act.CODEC, AttachmentSyncPredicate.all()));

	public static final String RAVAGE = "ravage", BEAM_TELL = "beam_tell", BEAM_FIRE = "beam_fire", CHARGE_TELL = "charge_tell",
		CHARGE_RUSH = "charge_rush", CLIMB = "climb", THROW = "throw_blues", STUN = "stun";

	private WitherAct() {
	}

	/** Its move is now {@code name} (or nothing), begun at {@code now}; the same move carries on from when it began. */
	static void set(WitherBoss wither, @Nullable String name, long now) {
		Act was = wither.getAttached(ACT);
		if (name == null) {
			if (was != null) {
				wither.removeAttached(ACT);
			}
			return;
		}
		if (was == null || !was.name().equals(name)) {
			wither.setAttached(ACT, new Act(name, now));
		}
	}

	/** The move begins again from the start (one skull thrown after another). */
	static void restart(WitherBoss wither, String name, long now) {
		wither.setAttached(ACT, new Act(name, now));
	}

	/** What it's doing, for the client (null for nothing). */
	public static @Nullable Act of(WitherBoss wither) {
		return wither.getAttached(ACT);
	}
}
