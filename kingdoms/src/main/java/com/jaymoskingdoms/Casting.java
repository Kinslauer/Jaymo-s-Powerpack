package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * How a spell is thrown, by anyone. Andy: "they shouldnt just punch to cast, use whatever makes sense in terms of hand
 * motioning", and "The magic casters need to cast sigils". A cast is one of seven gestures, the same seven the lore
 * films' mages use (their moves of the same names in {@code kingdoms/tools/humanoid_moves.py}), and each draws its sigil
 * where the hands are working, in the spell's colour, with a few marks pulled in to it:
 * <ul>
 * <li>{@link Gesture#PUSH}: the open palm pushed out at what it is for (a bolt, a beam, a hex), the sigil standing before it.</li>
 * <li>{@link Gesture#RAISE}: a hand lifted to the sky (calling something down or up: a storm, a chant, a renewal), the sigil over it.</li>
 * <li>{@link Gesture#GROUND}: bowed, the hand pressed down at the earth (the dead raised, spikes, a ring), the sigil on the ground.</li>
 * <li>{@link Gesture#WARD}: both palms out and apart (a ward, a repulse, a sanctuary), a wide sigil before them.</li>
 * <li>{@link Gesture#GIVE}: a hand held out low, palm up (mending someone, a gift of life), a small sigil lying on it.</li>
 * <li>{@link Gesture#DRAW}: the hand clawed and pulled back in to the chest (a drain, a reaping, an absorbing), the sigil at the feet.</li>
 * <li>{@link Gesture#SWEEP}: the arm swept across the body (a wave, a cone, a page's working let fly), the sigil before it.</li>
 * </ul>
 * Everyone who can see the caster is told ({@link Cast}); the client's {@code CastPose} moves the arms.
 */
public final class Casting {
	public enum Gesture {
		PUSH, RAISE, GROUND, WARD, GIVE, DRAW, SWEEP,
		/** (Class trees) not a spell: a Marksman's Speed Load, the gun spun round in the hand. */
		SPIN
	}

	/** How long the arms are held in a gesture (ticks; the client eases in and out of it within this). */
	public static final int TICKS = 16;
	/** The sigils: how long they stay, how far before the caster the standing ones are drawn, and the marks pulled in to each. */
	static final int SIGIL_TICKS = 14, GROUND_TICKS = 22, MARKS = 6;
	static final float REACH = 0.95F, PUSH_SIZE = 0.45F, WARD_SIZE = 0.9F, SWEEP_SIZE = 0.6F, RAISE_SIZE = 0.6F, GIVE_SIZE = 0.3F, GROUND_SIZE = 1.4F,
		DRAW_SIZE = 0.8F;
	/** The sigil's number among Powers' shapes ({@code MagicFx.SIGIL}). */
	private static final int SIGIL = 4;

	/** Server to client: {@code entity} makes {@code gesture} now, with the off hand if {@code off}. */
	public record Cast(int entity, int gesture, boolean off) implements CustomPacketPayload {
		public static final Type<Cast> TYPE = new Type<>(JaymosKingdoms.id("cast_gesture"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				buf.writeByte(p.gesture);
				buf.writeBoolean(p.off);
			},
			buf -> new Cast(buf.readVarInt(), buf.readByte(), buf.readBoolean()));

		@Override
		public Type<Cast> type() {
			return TYPE;
		}
	}

	private Casting() {
	}

	/** The gesture a spell (by its id) is thrown with: pushed at a foe unless it lies on the ground, rises, wards, heals or takes. */
	public static Gesture of(String spell) {
		return switch (spell) {
			case "raising", "sanctuary", "blight", "speak_with_dead" -> Gesture.GROUND;
			case "draining", "deaths_door", "blood_grip" -> Gesture.DRAW;
			case "warding" -> Gesture.WARD;
			case "mending" -> Gesture.GIVE;
			case "restoration", "frenzy", "whispers" -> Gesture.RAISE;
			case "hemorrhage" -> Gesture.SWEEP;
			default -> Gesture.PUSH;
		};
	}

	/** A spell's colour: its element's ({@link Absorption#colour}), blood's red, else the sigil's own pale violet. */
	public static int colour(String spell) {
		String element = Tomes.SPELL_ELEMENT.get(spell);
		if ("blood".equals(element) || ForbiddenArts.blood(spell)) {
			return BLOOD;
		}
		return element == null ? GLYPH : Absorption.colour(element);
	}

	/** Blood's red, and the colour of a working of no element. */
	static final int BLOOD = 0x9A0C0C, GLYPH = 0xE4DCFF;

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Cast.TYPE, Cast.CODEC);
	}

	/** {@code caster} throws a spell of {@code color} (0xRRGGBB) with {@code gesture}, the main hand leading. */
	public static void gesture(LivingEntity caster, Gesture gesture, int color) {
		gesture(caster, gesture, color, false);
	}

	public static void gesture(LivingEntity caster, Gesture gesture, int color, boolean off) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return;
		}
		Cast packet = new Cast(caster.getId(), gesture.ordinal(), off);
		List<ServerPlayer> to = new ArrayList<>(PlayerLookup.tracking(caster));
		if (caster instanceof ServerPlayer self) {
			to.add(self);
		}
		for (ServerPlayer p : to) {
			if (ServerPlayNetworking.canSend(p, Cast.TYPE)) {
				ServerPlayNetworking.send(p, packet);
			}
		}
		if (gesture == Gesture.SPIN) {
			// a gun spun in the hand draws no sigil
			return;
		}
		Vec3 look = caster.getLookAngle(), feet = caster.position(), chest = feet.add(0.0, caster.getBbHeight() * 0.72, 0.0);
		Vec3 flat = new Vec3(look.x, 0.0, look.z).lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, caster.getYRot()) : new Vec3(look.x, 0.0, look.z).normalize();
		Vec3 at;
		Vec3 facing = null;
		float size;
		int ticks = SIGIL_TICKS;
		switch (gesture) {
			case PUSH -> {
				at = chest.add(look.scale(REACH));
				facing = look;
				size = PUSH_SIZE;
			}
			case WARD -> {
				at = chest.add(flat.scale(REACH));
				facing = flat;
				size = WARD_SIZE;
			}
			case SWEEP -> {
				at = chest.add(flat.scale(REACH));
				facing = flat;
				size = SWEEP_SIZE;
			}
			case RAISE -> {
				at = feet.add(0.0, caster.getBbHeight() + 0.75, 0.0);
				facing = new Vec3(0.0, 1.0, 0.0);
				size = RAISE_SIZE;
			}
			case GIVE -> {
				at = feet.add(flat.scale(0.7)).add(0.0, caster.getBbHeight() * 0.6, 0.0);
				facing = new Vec3(0.0, 1.0, 0.0);
				size = GIVE_SIZE;
			}
			case DRAW -> {
				at = feet;
				size = DRAW_SIZE;
			}
			default -> {
				at = feet;
				size = GROUND_SIZE;
				ticks = GROUND_TICKS;
			}
		}
		Fx.shape(level, SIGIL, at, facing == null ? Vec3.ZERO : facing, size, color, ticks);
		// its marks, pulled in to where the hands work
		Vec3 heart = facing == null ? chest : at;
		for (int i = 0; i < MARKS; i++) {
			// (a mark sent with a way starts that far off and is drawn in to its point)
			double turn = i * Math.PI * 2.0 / MARKS, out = size + 0.5;
			level.sendParticles(Fx.rune(color, 0.9F), heart.x, heart.y, heart.z, 0, Math.cos(turn) * out, (caster.getRandom().nextDouble() - 0.3) * 0.8,
				Math.sin(turn) * out, 1.0);
		}
	}
}
