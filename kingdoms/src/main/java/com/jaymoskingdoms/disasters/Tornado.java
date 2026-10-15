package com.jaymoskingdoms.disasters;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * (0.6.12) A tornado: a dust devil, a tornado or a monster. It wanders under its storm (a dust devil has none),
 * forms over {@link Tornadoes#FORM_TICKS} and ropes out over its last ones. Held by the server ({@link Tornadoes}),
 * mirrored to clients ({@link StormNet}), which draw its funnel.
 */
public final class Tornado {
	public static final int DUST_DEVIL = 0, TORNADO = 1, MONSTER = 2;
	public static final String[] NAMES = {"dust_devil", "tornado", "monster"};

	public final int id;
	/** (0.7.34) Not final: a Smoke cloudshaper can unwind it a rank at a time ({@link SkyCalm}). */
	public int size;
	/** Its storm's id (-1 for a dust devil, which has none). */
	public final int storm;
	public double x, z, vx, vz;
	/** The ground under it. */
	public double y;
	public int age, life;
	/** A Wind user's shove (blocks per tick), wearing off. Server only. */
	double shoveX, shoveZ;
	/** (0.7.34) Seconds a Smoke cloudshaper has spent unwinding it ({@link SkyCalm}). Server only. */
	float unwound;
	/** (0.7.35) Whether a Smoke user with both fates has already finished it off (it's roping out). Server only. */
	boolean killed;
	/** A seed for its wander, so each one weaves its own way. */
	final float seed;

	public Tornado(int id, int size, int storm, double x, double y, double z, double vx, double vz, int age, int life, float seed) {
		this.id = id;
		this.size = size;
		this.storm = storm;
		this.x = x;
		this.y = y;
		this.z = z;
		this.vx = vx;
		this.vz = vz;
		this.age = age;
		this.life = life;
		this.seed = seed;
	}

	/** How formed it is, 0..1 (a thin rope as it touches down and as it dies). */
	public float formed() {
		float in = Math.min(1.0F, age / (float)Tornadoes.FORM_TICKS);
		float out = Math.min(1.0F, Math.max(0, life - age) / (float)Tornadoes.FORM_TICKS);
		return Mth.clamp(Math.min(in, out), 0.0F, 1.0F);
	}

	/** The radius of its core: inside, things are lifted. */
	public float core() {
		return Tornadoes.CORE[size] * (0.35F + 0.65F * formed());
	}

	/** How far its winds pull things in. */
	public float pull() {
		return Tornadoes.PULL[size] * (0.5F + 0.5F * formed());
	}

	/** How tall its funnel stands. */
	public float height() {
		return Tornadoes.HEIGHT[size];
	}

	/** The funnel's radius {@code h} blocks above the ground (narrow at the foot, wide where it meets the cloud). */
	public float radiusAt(float h) {
		float t = Mth.clamp(h / height(), 0.0F, 1.0F);
		return core() * (0.45F + 2.2F * t * t);
	}

	void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeVarInt(size);
		buf.writeVarInt(storm);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeFloat((float)vx);
		buf.writeFloat((float)vz);
		buf.writeVarInt(age);
		buf.writeVarInt(life);
		buf.writeFloat(seed);
	}

	static Tornado read(RegistryFriendlyByteBuf buf) {
		return new Tornado(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
			buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
	}
}
