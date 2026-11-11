package com.jaymospowers.core.fx;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "Magic FX need an overhaul, i dont like the recolored firework or redstone particles. Every effect
 * should have a type, (beam, blast, miasma, etc) and go to the same asset for each one, like the beacon for beams, and
 * they can vary in size intensity etc but that keeps it consistent and more like a magical system".
 * <p>
 * Every magic or ki effect is one of these types, and each type is drawn by one asset, whoever casts it. Only its
 * colour, size and how long it lasts change. Ki and mana are the same well (the lore), so a Z Fighter's wave and a
 * court wizard's bolt are the same beam.
 * <ul>
 * <li>{@link #beam}: a line of power from one point to another. The beacon's beam: a bright core in a wider glow.</li>
 * <li>{@link #blast}: a burst from a point. A shell of light swelling out and thinning away round a white-hot heart.</li>
 * <li>{@link #ring}: a shockwave. A flat band of light running out over the ground (or across any plane).</li>
 * <li>{@link #orb}: power held in one place. A ball of light, see-through at the rim.</li>
 * <li>{@link #sigil}: a working laid on the ground. A turning circle of marks.</li>
 * <li>{@link #mote}: loose power in the air. A small bright glint that drifts, twinkles and goes out. Takes the place
 * of every recoloured redstone dust.</li>
 * <li>{@link #spark}: power crackling. A jagged fleck that flickers and is gone. Takes the place of the electric spark.</li>
 * <li>{@link #rune}: a working being written. One of our marks, drawn in to a point. Takes the place of the enchanting
 * table's letters.</li>
 * <li>{@link #miasma}: dark or heavy power. A soft rolling puff, lit by the world, that thins away. Takes the place of
 * every dark dust (ink, shadow, blight, gas).</li>
 * </ul>
 * The shapes (beam, blast, ring, orb, sigil) go out as one {@link Shape} packet each and are drawn by
 * {@code client/fx/MagicFxClient}; motes, miasma, sparks and runes are particles ({@link Wisp}), so they drop in wherever a dust did.
 * Kingdoms reaches all of this through its own {@code Fx} class (it never compiles against this mod).
 */
public final class MagicFx {
	/** The shapes a {@link Shape} packet can carry. */
	public static final int BEAM = 0, BLAST = 1, RING = 2, ORB = 3, SIGIL = 4;
	/** Players further than this (blocks) aren't sent a shape. */
	static final double RANGE = 96.0;
	/** How long a blast and a ring last (ticks) when the caller doesn't say. */
	public static final int BLAST_TICKS = 12, RING_TICKS = 14;
	/** A colour darker than this (0-1, the brightest of its three channels) is miasma when the caller doesn't choose. */
	static final float DARK = 0.36F;
	/** The blast that stands where a bare flash of light was (blocks). */
	public static final float FLASH_RADIUS = 1.6F;
	/** The most motes a blast throws out with it. */
	public static final int BLAST_MOTES = 24;

	/** The particles a {@link Wisp} can be. */
	public static final int KIND_MOTE = 0, KIND_MIASMA = 1, KIND_SPARK = 2, KIND_RUNE = 3;

	/**
	 * One of the particles (a mote, a puff of miasma, a spark, a rune): {@code color} 0xRRGGBB, {@code size} on redstone
	 * dust's scale (1 = a dust's size), so the old numbers carry over.
	 */
	public record Wisp(int color, float size, int kind) implements ParticleOptions {
		static MapCodec<Wisp> codec(int kind) {
			return RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Wisp::color),
				Codec.FLOAT.optionalFieldOf("size", 1.0F).forGetter(Wisp::size)).apply(i, (color, size) -> new Wisp(color, size, kind)));
		}

		static StreamCodec<RegistryFriendlyByteBuf, Wisp> stream(int kind) {
			return StreamCodec.composite(ByteBufCodecs.INT, Wisp::color, ByteBufCodecs.FLOAT, Wisp::size, (color, size) -> new Wisp(color, size, kind));
		}

		@Override
		public ParticleType<?> getType() {
			return switch (kind) {
				case KIND_MIASMA -> MIASMA;
				case KIND_SPARK -> SPARK;
				case KIND_RUNE -> RUNE;
				default -> MOTE;
			};
		}
	}

	public static final ParticleType<Wisp> MOTE = register("magic_mote", KIND_MOTE);
	public static final ParticleType<Wisp> MIASMA = register("magic_miasma", KIND_MIASMA);
	public static final ParticleType<Wisp> SPARK = register("magic_spark", KIND_SPARK);
	public static final ParticleType<Wisp> RUNE = register("magic_rune", KIND_RUNE);

	private static ParticleType<Wisp> register(String name, int kind) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id(name),
			FabricParticleTypes.complex(false, Wisp.codec(kind), Wisp.stream(kind)));
	}

	/** The colours of power whose caller gave none: plain light, a crackle, the void's violet, a rune, an ember, a glow. */
	public static final int LIGHT_COLOR = 0xFFF6E0, CRACKLE_COLOR = 0xFFF7B0, VOID_COLOR = 0xB26BFF, RUNE_COLOR = 0xE4DCFF, EMBER_COLOR = 0xFFA040,
		GLOW_COLOR = 0x60F0D0, HEX_COLOR = 0x8A3FC8;
	/**
	 * The stock particles, for where a vanilla one stood: {@link #LIGHT} a white mote (the end rod's glint),
	 * {@link #CRACKLE} a spark (the electric spark), {@link #VOID} a violet mote (the portal's specks), {@link #GLYPH}
	 * a rune (the enchanting table's letters), {@link #EMBER} an orange mote and {@link #PALE} a white one (the wax
	 * sparkles), {@link #GLOW} a sea-green mote (the glow squid's), {@link #HEX} a witch's violet mote, {@link #MURK}
	 * a violet miasma (dragon's breath).
	 */
	public static final ParticleOptions LIGHT = new Wisp(LIGHT_COLOR, 1.0F, KIND_MOTE), CRACKLE = new Wisp(CRACKLE_COLOR, 1.0F, KIND_SPARK),
		VOID = new Wisp(VOID_COLOR, 0.8F, KIND_MOTE), GLYPH = new Wisp(RUNE_COLOR, 1.0F, KIND_RUNE), EMBER = new Wisp(EMBER_COLOR, 0.8F, KIND_MOTE),
		PALE = new Wisp(0xFFFFFF, 0.8F, KIND_MOTE), GLOW = new Wisp(GLOW_COLOR, 0.9F, KIND_MOTE), HEX = new Wisp(HEX_COLOR, 0.9F, KIND_MOTE),
		MURK = new Wisp(0xC040E0, 1.6F, KIND_MIASMA);

	/**
	 * Server to client: one shape. {@code kind} is {@link #BEAM} and the rest; ({@code x}, {@code y}, {@code z}) where
	 * it starts or sits; ({@code dx}, {@code dy}, {@code dz}) a beam's reach to its far end, or the way a ring or a sigil
	 * faces (zero: up); {@code size} a beam's radius, or the radius the others reach; {@code color} 0xRRGGBB;
	 * {@code ticks} how long it lasts.
	 */
	public record Shape(int kind, double x, double y, double z, float dx, float dy, float dz, float size, int color, int ticks)
		implements CustomPacketPayload {
		public static final Type<Shape> TYPE = new Type<>(JaymosPowers.id("magic_fx_shape"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shape> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.kind);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.dx);
				buf.writeFloat(p.dy);
				buf.writeFloat(p.dz);
				buf.writeFloat(p.size);
				buf.writeInt(p.color);
				buf.writeVarInt(p.ticks);
			},
			buf -> new Shape(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readInt(), buf.readVarInt()));

		@Override
		public Type<Shape> type() {
			return TYPE;
		}
	}

	private MagicFx() {
	}

	/** Loads the class (the particle types) and registers the packet. */
	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Shape.TYPE, Shape.CODEC);
	}

	/** A mote: loose power in the air. Drops in wherever a redstone dust of this colour and size was. */
	public static ParticleOptions mote(int color, float size) {
		return new Wisp(color & 0xFFFFFF, size, KIND_MOTE);
	}

	/** A puff of miasma: dark or heavy power. Drops in wherever a dark dust of this colour and size was. */
	public static ParticleOptions miasma(int color, float size) {
		return new Wisp(color & 0xFFFFFF, size, KIND_MIASMA);
	}

	/** A spark: power crackling (lightning, static, a charge). A jagged fleck that flickers and is gone. */
	public static ParticleOptions spark(int color, float size) {
		return new Wisp(color & 0xFFFFFF, size, KIND_SPARK);
	}

	/**
	 * A rune: a working being written. One of our marks, drifting. Sent with no count and a direction, it starts that
	 * far from the point and is drawn in to it, as the enchanting table's letters are.
	 */
	public static ParticleOptions rune(int color, float size) {
		return new Wisp(color & 0xFFFFFF, size, KIND_RUNE);
	}

	/** A mote, or miasma if the colour is dark (light can't be black). For colours only known when the game runs. */
	public static ParticleOptions wisp(int color, float size) {
		return dark(color) ? miasma(color, size) : mote(color, size);
	}

	/** Whether {@code color} (0xRRGGBB) is too dark to be a light. */
	public static boolean dark(int color) {
		int max = Math.max(color >> 16 & 0xFF, Math.max(color >> 8 & 0xFF, color & 0xFF));
		return max / 255.0F < DARK;
	}

	/** A beam from {@code from} to {@code to}, {@code radius} thick, lasting {@code ticks}. */
	public static void beam(ServerLevel level, Vec3 from, Vec3 to, float radius, int color, int ticks) {
		Vec3 d = to.subtract(from);
		send(level, from, new Shape(BEAM, from.x, from.y, from.z, (float)d.x, (float)d.y, (float)d.z, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	/** A blast at {@code at}, swelling out to {@code radius}. */
	public static void blast(ServerLevel level, Vec3 at, float radius, int color) {
		blast(level, at, radius, color, BLAST_TICKS);
	}

	public static void blast(ServerLevel level, Vec3 at, float radius, int color, int ticks) {
		send(level, at, new Shape(BLAST, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	public static void blast(ServerLevel level, double x, double y, double z, float radius, int color) {
		blast(level, new Vec3(x, y, z), radius, color, BLAST_TICKS);
	}

	/** A shockwave over the ground at {@code at}, running out to {@code radius}. */
	public static void ring(ServerLevel level, Vec3 at, float radius, int color) {
		ring(level, at, null, radius, color, RING_TICKS);
	}

	/** A shockwave across the plane that faces {@code normal} (null: flat on the ground). */
	public static void ring(ServerLevel level, Vec3 at, @Nullable Vec3 normal, float radius, int color, int ticks) {
		Vec3 n = normal == null || normal.lengthSqr() < 1.0E-6 ? Vec3.ZERO : normal.normalize();
		send(level, at, new Shape(RING, at.x, at.y, at.z, (float)n.x, (float)n.y, (float)n.z, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	/** A ball of power held at {@code at} for {@code ticks}. Send it again to keep it. */
	public static void orb(ServerLevel level, Vec3 at, float radius, int color, int ticks) {
		send(level, at, new Shape(ORB, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	/** A circle of marks on the ground at {@code at}, {@code radius} across, for {@code ticks}. */
	public static void sigil(ServerLevel level, Vec3 at, float radius, int color, int ticks) {
		send(level, at, new Shape(SIGIL, at.x, at.y, at.z, 0.0F, 0.0F, 0.0F, radius, color & 0xFFFFFF, Math.max(1, ticks)));
	}

	/**
	 * What a one-off cloud of {@code count} dusts spread over ({@code dx}, {@code dy}, {@code dz}) was: a blast that
	 * size, with motes thrown out of it.
	 */
	public static void burst(ServerLevel level, double x, double y, double z, int color, float size, int count, double dx, double dy, double dz) {
		float radius = (float)Math.max(0.4, Math.max(dx, Math.max(dy, dz)) * 2.0);
		blast(level, new Vec3(x, y, z), radius, color, BLAST_TICKS);
		level.sendParticles(wisp(color, size), x, y, z, Mth.clamp(count / 3, 2, BLAST_MOTES), dx, dy, dz, 0.02);
	}

	/** How thick (blocks) and how long (ticks) the beam is that stands where a row of particles drew a line. */
	public static final float LINE_RADIUS = 0.06F;
	public static final int LINE_TICKS = 7;

	/**
	 * What a row of {@code particle} from {@code from} to {@code to} was. If it is one of our lights (a mote, a spark,
	 * a rune) the line is a beam of its colour; anything else (miasma, flame, notes, another mod's) stays a row of
	 * {@code points} particles.
	 */
	public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, int points) {
		if (particle instanceof Wisp wisp && wisp.kind() != KIND_MIASMA) {
			beam(level, from, to, LINE_RADIUS * Math.max(0.6F, wisp.size()), wisp.color(), LINE_TICKS);
			return;
		}
		Vec3 step = to.subtract(from).scale(1.0 / Math.max(1, points));
		for (int i = 1; i <= points; i++) {
			Vec3 p = from.add(step.scale(i));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/**
	 * What a circle of {@code particle} on the ground round {@code at} was: a shockwave ring of its colour if it is one
	 * of ours (miasma too: murk rolls out as well as light does), else the circle of {@code points} particles.
	 */
	public static void circle(ServerLevel level, ParticleOptions particle, Vec3 at, double radius, int points) {
		if (particle instanceof Wisp wisp) {
			ring(level, at.add(0.0, 0.1, 0.0), (float)radius, wisp.color());
			return;
		}
		for (int i = 0; i < points; i++) {
			double a = i / (double)points * Math.PI * 2.0;
			level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.2, at.z + Math.sin(a) * radius, 1, 0.1, 0.05, 0.1, 0.0);
		}
	}

	private static void send(ServerLevel level, Vec3 at, Shape shape) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) <= RANGE * RANGE) {
				ServerPlayNetworking.send(player, shape);
			}
		}
	}
}
