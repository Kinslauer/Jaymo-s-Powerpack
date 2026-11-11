package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A lore film on tape: one performance of it, captured in the game as it was staged ({@code /kingdomfilm record}) and
 * played back the same every time after (Andy: "captured from within the engine, then replayed. Not drawn up every
 * time"). The tape holds the cast, the set, where every actor stood and faced each tick, and everything that happened
 * (a move started, a weapon taken, a head off, a sound, a particle, lightning, something sent flying, a tether thrown, a
 * flame lit, grown or put out).
 * All of it is in the stage's own measure (blocks ahead of and beside where the viewer stood, and up from the ground
 * there), so it plays back on any ground.
 * <p>
 * The tape shipped with the mod is {@code assets/jaymoskingdoms/films/<film>.tape}; one recorded on this game
 * ({@code <game folder>/jaymoskingdoms/films/<film>.tape}) is played instead when there is one.
 */
final class FilmTape {
	static final int MAGIC = 0x4A4B4654, VERSION = 1;
	/** What can happen in a tick ({@link Event#type}). */
	static final byte ACT = 0, REST = 1, ITEM = 2, MAIM = 3, HURT = 4, HIDE = 5, SOUND = 6, PARTICLE = 7, BOLT = 8, FLASH = 9, GIB = 10, UNGIB = 11,
		TETHER = 12, GAIT = 13, PLACE = 14, IGNITE = 15, FLAME = 16, QUENCH = 17, CLOAK = 18, CHARGE = 19;

	/**
	 * How finely a tape keeps things (steps to a block, or to a degree): where actors stand, which way they face, where a
	 * particle starts, and how fast it goes (blocks a tick).
	 */
	static final float PLACE_STEPS = 256.0F, TURN_STEPS = 32767.0F / 180.0F, PARTICLE_STEPS = 64.0F, SPEED_STEPS = 4096.0F;

	/**
	 * One of the cast: {@code kind} 0 a film actor (its skin, body, an orc's features, the cloak), 1 a draugr drawn alive (with
	 * an axe or a sword), 2 an imp (its skin "imp" or "zombified_imp"); its part and what it's made of; what's in its hands; and {f, s, up, the way it faces, its size}.
	 */
	record Cast(int kind, String skin, int body, int orc, boolean cloak, int role, int flesh, boolean axe, String main, String off, float[] at) {
	}

	/** A box of the set: a block, {anchor f, anchor s, f, s, up from the anchor's ground, width, height, depth}, and whether it's lit. */
	record Box(String block, float[] at, boolean lit) {
	}

	/** Something that happened: its kind, two whole numbers, some numbers, a name. (A particle: its number on the tape, then {f, s, up, and its speed the same way}.) */
	record Event(byte type, int a, int b, float[] v, String s) {
	}

	/** One tick: who moved ({actor, f, s, up, yaw} each), and what happened. */
	static final class Frame {
		final List<float[]> moves = new ArrayList<>();
		final List<Event> events = new ArrayList<>();
	}

	final List<Cast> cast = new ArrayList<>();
	final List<Box> set = new ArrayList<>();
	final List<String> particles = new ArrayList<>();
	final Map<String, Integer> named = new HashMap<>();
	final List<Frame> frames = new ArrayList<>();
	private final Map<String, Integer> particleIndex = new HashMap<>();

	/** The number {@code particle} (as text) goes by on this tape. */
	int particle(String particle) {
		return this.particleIndex.computeIfAbsent(particle, p -> {
			this.particles.add(p);
			return this.particles.size() - 1;
		});
	}

	Frame frame(int tick) {
		while (this.frames.size() <= tick) {
			this.frames.add(new Frame());
		}
		return this.frames.get(tick);
	}

	static Path local(String film) {
		return FabricLoader.getInstance().getGameDir().resolve("jaymoskingdoms").resolve("films").resolve(film + ".tape");
	}

	/** The tape of {@code film}: this game's own recording if it has one, else the one shipped; null if there's neither (or it can't be read). */
	static @Nullable FilmTape find(String film) {
		Path local = local(film);
		try {
			if (Files.isRegularFile(local)) {
				try (InputStream in = Files.newInputStream(local)) {
					return read(in);
				}
			}
			Identifier id = JaymosKingdoms.id("films/" + film + ".tape");
			var resource = Minecraft.getInstance().getResourceManager().getResource(id);
			if (resource.isPresent()) {
				try (InputStream in = resource.get().open()) {
					return read(in);
				}
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("lore film {}: its tape can't be read, staging it afresh", film, e);
		}
		return null;
	}

	void save(String film) throws IOException {
		Path path = local(film);
		Files.createDirectories(path.getParent());
		try (OutputStream out = Files.newOutputStream(path)) {
			this.write(out);
		}
	}

	private static int pack(float value, float steps) {
		return Math.clamp(Math.round(value * steps), Short.MIN_VALUE, Short.MAX_VALUE);
	}

	private static void floats(DataOutputStream out, float[] v) throws IOException {
		out.writeByte(v.length);
		for (float f : v) {
			out.writeFloat(f);
		}
	}

	private static float[] floats(DataInputStream in) throws IOException {
		float[] v = new float[in.readUnsignedByte()];
		for (int i = 0; i < v.length; i++) {
			v[i] = in.readFloat();
		}
		return v;
	}

	void write(OutputStream raw) throws IOException {
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
			out.writeInt(MAGIC);
			out.writeInt(VERSION);
			out.writeInt(this.cast.size());
			for (Cast c : this.cast) {
				out.writeByte(c.kind);
				out.writeUTF(c.skin);
				out.writeByte(c.body);
				out.writeByte(c.orc);
				out.writeBoolean(c.cloak);
				out.writeByte(c.role);
				out.writeByte(c.flesh);
				out.writeBoolean(c.axe);
				out.writeUTF(c.main);
				out.writeUTF(c.off);
				floats(out, c.at);
			}
			out.writeInt(this.set.size());
			for (Box b : this.set) {
				out.writeUTF(b.block);
				floats(out, b.at);
				out.writeBoolean(b.lit);
			}
			out.writeInt(this.particles.size());
			for (String p : this.particles) {
				out.writeUTF(p);
			}
			out.writeInt(this.named.size());
			for (Map.Entry<String, Integer> e : this.named.entrySet()) {
				out.writeUTF(e.getKey());
				out.writeInt(e.getValue());
			}
			out.writeInt(this.frames.size());
			for (Frame f : this.frames) {
				out.writeShort(f.moves.size());
				for (float[] m : f.moves) {
					out.writeShort((int)m[0]);
					for (int i = 1; i < 4; i++) {
						out.writeShort(pack(m[i], PLACE_STEPS));
					}
					out.writeShort(Math.round(net.minecraft.util.Mth.wrapDegrees(m[4]) * TURN_STEPS));
				}
				out.writeShort(f.events.size());
				for (Event e : f.events) {
					out.writeByte(e.type);
					if (e.type == PARTICLE) {
						// (most of a tape is these: kept small)
						out.writeShort(e.a);
						for (int i = 0; i < 6; i++) {
							out.writeShort(pack(e.v[i], i < 3 ? PARTICLE_STEPS : SPEED_STEPS));
						}
						continue;
					}
					out.writeInt(e.a);
					out.writeInt(e.b);
					floats(out, e.v);
					out.writeUTF(e.s);
				}
			}
		}
	}

	static FilmTape read(InputStream raw) throws IOException {
		FilmTape tape = new FilmTape();
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
			if (in.readInt() != MAGIC || in.readInt() != VERSION) {
				throw new IOException("not a lore film tape of this version");
			}
			for (int i = in.readInt(); i > 0; i--) {
				tape.cast.add(new Cast(in.readByte(), in.readUTF(), in.readByte(), in.readByte(), in.readBoolean(), in.readByte(), in.readByte(),
					in.readBoolean(), in.readUTF(), in.readUTF(), floats(in)));
			}
			for (int i = in.readInt(); i > 0; i--) {
				tape.set.add(new Box(in.readUTF(), floats(in), in.readBoolean()));
			}
			for (int i = in.readInt(); i > 0; i--) {
				tape.particle(in.readUTF());
			}
			for (int i = in.readInt(); i > 0; i--) {
				tape.named.put(in.readUTF(), in.readInt());
			}
			for (int i = in.readInt(); i > 0; i--) {
				Frame f = new Frame();
				for (int n = in.readUnsignedShort(); n > 0; n--) {
					f.moves.add(new float[] {in.readShort(), in.readShort() / PLACE_STEPS, in.readShort() / PLACE_STEPS, in.readShort() / PLACE_STEPS,
						in.readShort() / TURN_STEPS});
				}
				for (int n = in.readUnsignedShort(); n > 0; n--) {
					byte type = in.readByte();
					if (type == PARTICLE) {
						int which = in.readUnsignedShort();
						float[] v = new float[6];
						for (int j = 0; j < 6; j++) {
							v[j] = in.readShort() / (j < 3 ? PARTICLE_STEPS : SPEED_STEPS);
						}
						f.events.add(new Event(type, which, 0, v, ""));
						continue;
					}
					f.events.add(new Event(type, in.readInt(), in.readInt(), floats(in), in.readUTF()));
				}
				tape.frames.add(f);
			}
		}
		return tape;
	}
}
