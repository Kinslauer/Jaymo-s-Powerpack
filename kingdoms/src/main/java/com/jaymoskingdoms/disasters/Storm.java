package com.jaymoskingdoms.disasters;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * (0.6.9) A storm cell: a patch of dark sky {@link #radius} blocks across that drifts with the wind. Rain and thunder
 * fall only under it ({@link Storms#cover}). A supercell can put down a {@link Tornado}. Held by the server
 * ({@link Storms}) and mirrored to every client in the Overworld ({@link StormNet}), which moves it on by its velocity
 * between updates.
 */
public final class Storm {
	/** A rain cell (rain, no thunder), a thunderstorm, a supercell (thunder, and it can spawn a tornado). */
	public static final int RAIN = 0, THUNDER = 1, SUPERCELL = 2;

	public final int id;
	public final int kind;
	public double x, z, vx, vz;
	public float radius;
	public int age, life;
	/** Whether this supercell has already put down its tornado. Server only. */
	boolean spawned;

	public Storm(int id, int kind, double x, double z, double vx, double vz, float radius, int age, int life) {
		this.id = id;
		this.kind = kind;
		this.x = x;
		this.z = z;
		this.vx = vx;
		this.vz = vz;
		this.radius = radius;
		this.age = age;
		this.life = life;
	}

	/** How built up it is, 0..1: it gathers over its first minute and breaks up over its last. */
	public float strength() {
		float in = Math.min(1.0F, age / (float)Storms.GATHER_TICKS);
		float out = Math.min(1.0F, Math.max(0, life - age) / (float)Storms.GATHER_TICKS);
		return Math.max(0.0F, Math.min(in, out));
	}

	/** How much of it lies over (x, z), 0..1: full inside, fading out over {@link Storms#EDGE} blocks past its edge. */
	public float over(double px, double pz) {
		double d = Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z));
		float f = d <= radius ? 1.0F : (float)Math.max(0.0, 1.0 - (d - radius) / Storms.EDGE);
		return f * strength();
	}

	/** One tick of drift. */
	public void move() {
		x += vx;
		z += vz;
		age++;
	}

	void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeVarInt(kind);
		buf.writeDouble(x);
		buf.writeDouble(z);
		buf.writeFloat((float)vx);
		buf.writeFloat((float)vz);
		buf.writeFloat(radius);
		buf.writeVarInt(age);
		buf.writeVarInt(life);
	}

	static Storm read(RegistryFriendlyByteBuf buf) {
		return new Storm(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readVarInt(), buf.readVarInt());
	}
}
