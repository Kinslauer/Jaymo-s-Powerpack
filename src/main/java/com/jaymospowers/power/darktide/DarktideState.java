package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Darktide's attachments (every one of them is declared here, so {@code JaymosPowers.registerAttachments()} loads this
 * one class):
 * <ul>
 * <li>{@link #SAVE}: saved, kept through death, server only: katana parries toward ascension, stored shadows, the
 * real items Tide Arms put away, and until when the corruption is cured;</li>
 * <li>{@link #HUD}: the owner's HUD (Flow, cooldowns, empowerment, Veil, Tide Arms...), synced to the owner only;</li>
 * <li>{@link #GUARD}: when the guard went up (game time), synced to everyone for the pose;</li>
 * <li>{@link #MOVE}: the move being played (strikes, stances, reels), synced to everyone for the pose;</li>
 * <li>{@link #STUN}, {@link #CRIPPLE}: until when a creature is stunned / crippled (crawling), synced to everyone;</li>
 * <li>{@link #VEIL}: the veil is up and hides the name tag (synced to everyone);</li>
 * <li>{@link #CURED}: the corruption is cured for now (synced to everyone, for the name and the HUD);</li>
 * <li>{@link #RISEN}: a risen shadow (Necromancy), synced to everyone so it's drawn dark;</li>
 * <li>{@link #HOLLOW}: (0.11.103) a harvested villager raised as itself, back at its own life: saved, synced to everyone so
 * it's drawn ashen;</li>
 * <li>{@link #SHADE}: a user walking as their own shadow after dying at night (until the next nightfall), synced to everyone so they're drawn dark.</li>
 * </ul>
 */
public final class DarktideState {
	/** Ability slots for cooldowns. */
	public static final int STRIKE = 0, POUNCE = 1, CRESCENT = 2, INK_CROSS = 3, BARRAGE = 4, UNDERTOW = 5, BLACK_TIDE = 6, DASH = 7,
		GEDAN = 8, ARMS = 9, DROWN = 10, RAISE = 11, SLOTS = 12;

	/** Tide Arms: what's conjured. */
	public static final int ARMS_BARE = 0, ARMS_DAGGERS = 1, ARMS_KATANA = 2;

	/**
	 * Saved: katana parries counted toward ascension, stored shadows (entity type ids; a villager's is its whole saved data, so it rises as itself), Tide Arms' stored items and slot,
	 * the overworld clock time the cure lasts until (the next nightfall; 0: corrupted), and the overworld clock time a shade
	 * (died at night and came back as their shadow, {@link DarktideShade}) is flesh again (0: not a shade).
	 */
	public record Save(int parries, List<String> shadows, ItemContainerContents stored, int storedSlot, long curedUntil, long shadeUntil) {
		public static final Save NONE = new Save(0, List.of(), ItemContainerContents.EMPTY, -1, 0L, 0L);
		public static final Codec<Save> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("parries", 0).forGetter(Save::parries),
				Codec.STRING.listOf().optionalFieldOf("shadows", List.of()).forGetter(Save::shadows),
				ItemContainerContents.CODEC.optionalFieldOf("stored", ItemContainerContents.EMPTY).forGetter(Save::stored),
				Codec.INT.optionalFieldOf("stored_slot", -1).forGetter(Save::storedSlot),
				Codec.LONG.optionalFieldOf("cured_until", 0L).forGetter(Save::curedUntil),
				Codec.LONG.optionalFieldOf("shade_until", 0L).forGetter(Save::shadeUntil))
			.apply(i, Save::new));

		Save withParries(int value) {
			return new Save(value, shadows, stored, storedSlot, curedUntil, shadeUntil);
		}

		Save withShadows(List<String> value) {
			return new Save(parries, List.copyOf(value), stored, storedSlot, curedUntil, shadeUntil);
		}

		Save withStored(ItemContainerContents value, int slot) {
			return new Save(parries, shadows, value, slot, curedUntil, shadeUntil);
		}

		Save withCuredUntil(long value) {
			return new Save(parries, shadows, stored, storedSlot, value, shadeUntil);
		}

		Save withShadeUntil(long value) {
			return new Save(parries, shadows, stored, storedSlot, curedUntil, value);
		}
	}

	/** What the owner's HUD shows. Cooldown ends are game times; lengths are for the chips' shading. */
	public record Hud(int flow, long[] until, int[] length, long empoweredUntil, boolean veil, int arms, int shadows, int parries,
			long guardLockedUntil, int risen) {
		public static final Hud NONE = new Hud(0, new long[SLOTS], new int[SLOTS], 0L, false, 0, 0, 0, 0L, 0);
		public static final StreamCodec<ByteBuf, Hud> STREAM_CODEC = StreamCodec.of((buf, hud) -> {
			ByteBufCodecs.VAR_INT.encode(buf, hud.flow);
			for (int i = 0; i < SLOTS; i++) {
				ByteBufCodecs.VAR_LONG.encode(buf, hud.until[i]);
				ByteBufCodecs.VAR_INT.encode(buf, hud.length[i]);
			}
			ByteBufCodecs.VAR_LONG.encode(buf, hud.empoweredUntil);
			ByteBufCodecs.BOOL.encode(buf, hud.veil);
			ByteBufCodecs.VAR_INT.encode(buf, hud.arms);
			ByteBufCodecs.VAR_INT.encode(buf, hud.shadows);
			ByteBufCodecs.VAR_INT.encode(buf, hud.parries);
			ByteBufCodecs.VAR_LONG.encode(buf, hud.guardLockedUntil);
			ByteBufCodecs.VAR_INT.encode(buf, hud.risen);
		}, buf -> {
			int flow = ByteBufCodecs.VAR_INT.decode(buf);
			long[] until = new long[SLOTS];
			int[] length = new int[SLOTS];
			for (int i = 0; i < SLOTS; i++) {
				until[i] = ByteBufCodecs.VAR_LONG.decode(buf);
				length[i] = ByteBufCodecs.VAR_INT.decode(buf);
			}
			return new Hud(flow, until, length, ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.BOOL.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
		});

		public long cooldownLeft(int slot, long now) {
			return Math.max(0L, until[slot] - now);
		}

		boolean same(Hud other) {
			return flow == other.flow && java.util.Arrays.equals(until, other.until) && java.util.Arrays.equals(length, other.length)
				&& empoweredUntil == other.empoweredUntil && veil == other.veil && arms == other.arms && shadows == other.shadows
				&& parries == other.parries && guardLockedUntil == other.guardLockedUntil && risen == other.risen;
		}
	}

	/** A move being played on an entity (see {@link DarktideMove}): which, since when (game time), for how long, and a variant. */
	public record Move(int kind, long start, int duration, int variant) {
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Move::kind,
			ByteBufCodecs.VAR_LONG, Move::start,
			ByteBufCodecs.VAR_INT, Move::duration,
			ByteBufCodecs.VAR_INT, Move::variant,
			Move::new);
	}

	public static final AttachmentType<Save> SAVE = AttachmentRegistry.create(JaymosPowers.id("darktide_save"),
		builder -> builder.persistent(Save.CODEC).copyOnDeath());
	public static final AttachmentType<Hud> HUD = AttachmentRegistry.create(JaymosPowers.id("darktide_hud"),
		builder -> builder.syncWith(Hud.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	public static final AttachmentType<Long> GUARD = AttachmentRegistry.create(JaymosPowers.id("darktide_guard"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("darktide_move"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Long> STUN = AttachmentRegistry.create(JaymosPowers.id("darktide_stun"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Long> CRIPPLE = AttachmentRegistry.create(JaymosPowers.id("darktide_cripple"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Boolean> VEIL = AttachmentRegistry.create(JaymosPowers.id("darktide_veil"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Boolean> CURED = AttachmentRegistry.create(JaymosPowers.id("darktide_cured"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Integer> RISEN = AttachmentRegistry.create(JaymosPowers.id("darktide_risen"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	public static final AttachmentType<Boolean> HOLLOW = AttachmentRegistry.create(JaymosPowers.id("darktide_hollow"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	public static final AttachmentType<Boolean> SHADE = AttachmentRegistry.create(JaymosPowers.id("darktide_shade"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private DarktideState() {
	}

	public static Hud hud(Entity player) {
		Hud hud = player.getAttached(HUD);
		return hud == null ? Hud.NONE : hud;
	}

	public static Save save(Entity player) {
		Save save = player.getAttached(SAVE);
		return save == null ? Save.NONE : save;
	}

	static List<String> mutable(List<String> list) {
		return new ArrayList<>(list);
	}
}
