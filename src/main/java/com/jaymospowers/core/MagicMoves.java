package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Walking spells' hold on a body (fifth edition's water walk, spider climb and jump): Jaymo's Kingdoms' spells ask for
 * them here by reflection, as they ask for a trip. Each is a time (game time) until which it lasts, kept on the body and
 * synced, so the client's own movement obeys it too (the mixins read it on both sides):
 * <ul>
 * <li><b>Water walk</b>: still water holds you up like ground ({@code ChronoWaterRunMixin}); sneak and you sink.</li>
 * <li><b>Spider climb</b>: any wall beside you counts as a ladder ({@code SkitterClimbMixin}).</li>
 * <li><b>Long jump</b>: a jump goes farther and higher ({@code SpellJumpMixin}).</li>
 * </ul>
 */
public final class MagicMoves {
	/** Until when each lasts (game time): water walk, spider climb, long jump. */
	public record Moves(long water, long climb, long jump) {
		public static final Moves NONE = new Moves(0L, 0L, 0L);
		static final Codec<Moves> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("water", 0L).forGetter(Moves::water),
			Codec.LONG.optionalFieldOf("climb", 0L).forGetter(Moves::climb),
			Codec.LONG.optionalFieldOf("jump", 0L).forGetter(Moves::jump)).apply(i, Moves::new));
		static final StreamCodec<io.netty.buffer.ByteBuf, Moves> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Moves::water,
			ByteBufCodecs.VAR_LONG, Moves::climb, ByteBufCodecs.VAR_LONG, Moves::jump, Moves::new);
	}

	/** A long jump adds this much to a jump's reach along the way you face (blocks a tick), and this share to its height. */
	public static final double JUMP_FORWARD = 0.42, JUMP_HIGHER = 1.3;

	/** On a body, synced to everyone near it and saved (the times are game time). */
	public static final AttachmentType<Moves> MOVES = AttachmentRegistry.create(JaymosPowers.id("magic_moves"),
		builder -> builder.persistent(Moves.CODEC).syncWith(Moves.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private MagicMoves() {
	}

	/** {@code kind} ({@code water}, {@code climb} or {@code jump}) lasts {@code ticks} more on {@code who}; false for an unknown kind. */
	public static boolean grant(LivingEntity who, String kind, int ticks) {
		long until = who.level().getGameTime() + ticks;
		Moves m = who.getAttachedOrElse(MOVES, Moves.NONE);
		Moves next = switch (kind) {
			case "water" -> new Moves(until, m.climb(), m.jump());
			case "climb" -> new Moves(m.water(), until, m.jump());
			case "jump" -> new Moves(m.water(), m.climb(), until);
			default -> null;
		};
		if (next == null) {
			return false;
		}
		who.setAttached(MOVES, next);
		return true;
	}

	/** Ends {@code kind} on {@code who}; a null kind ends all three. */
	public static void clear(LivingEntity who, @Nullable String kind) {
		Moves m = who.getAttachedOrElse(MOVES, Moves.NONE);
		Moves next = kind == null ? Moves.NONE : switch (kind) {
			case "water" -> new Moves(0L, m.climb(), m.jump());
			case "climb" -> new Moves(m.water(), 0L, m.jump());
			case "jump" -> new Moves(m.water(), m.climb(), 0L);
			default -> m;
		};
		if (!next.equals(m)) {
			who.setAttached(MOVES, next);
		}
	}

	/** Whether {@code who} walks on water just now. */
	public static boolean waterWalk(LivingEntity who) {
		return who.getAttachedOrElse(MOVES, Moves.NONE).water() > who.level().getGameTime();
	}

	/** Whether {@code who} climbs walls just now. */
	public static boolean climbing(LivingEntity who) {
		return who.getAttachedOrElse(MOVES, Moves.NONE).climb() > who.level().getGameTime();
	}

	/** Whether {@code who} jumps far just now. */
	public static boolean longJump(LivingEntity who) {
		return who.getAttachedOrElse(MOVES, Moves.NONE).jump() > who.level().getGameTime();
	}
}
