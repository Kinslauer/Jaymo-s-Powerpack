package com.jaymoskingdoms.disasters;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * (0.7.31) One of the land's own weather disasters the clients need to see: a sandstorm or a blizzard (a wide patch of
 * blowing sand or snow drifting with the wind), or a hail shower (riding under its thunderstorm). Held by the server
 * ({@link Calamities}) and mirrored to clients, which thicken the fog and blow sand, snow or hail past the camera.
 * Floods and wildfires are real water and fire, so they aren't sent. (Stage 3) An earthquake too, which the clients feel
 * as a shaking screen and a rumble ({@link Earthquakes}).
 */
public final class Calamity {
	public static final int SANDSTORM = 0, BLIZZARD = 1, HAIL = 2, QUAKE = 3;
	public static final String[] NAMES = {"sandstorm", "blizzard", "hail", "quake"};

	public final int id;
	public final int kind;
	public double x, z, vx, vz;
	public float radius;
	public int age, life;
	/** A hail shower's thunderstorm (its id), which it rides under; -1 for the rest. */
	public final int storm;

	public Calamity(int id, int kind, double x, double z, double vx, double vz, float radius, int age, int life, int storm) {
		this.id = id;
		this.kind = kind;
		this.x = x;
		this.z = z;
		this.vx = vx;
		this.vz = vz;
		this.radius = radius;
		this.age = age;
		this.life = life;
		this.storm = storm;
	}

	/** How built up it is, 0..1: it gathers over its first {@link Calamities#GATHER_TICKS} and dies down over its last. */
	public float strength() {
		float gather = kind == QUAKE ? Earthquakes.GATHER : Calamities.GATHER_TICKS;
		float in = Math.min(1.0F, age / gather);
		float out = Math.min(1.0F, Math.max(0, life - age) / gather);
		return Math.max(0.0F, Math.min(in, out));
	}

	/** How much of it lies over (x, z), 0..1: full inside, fading out over its outer fifth. */
	public float over(double px, double pz) {
		double d = Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z));
		float edge = radius * 0.2F;
		float f = d <= radius - edge ? 1.0F : (float)Math.max(0.0, (radius - d) / edge);
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
		buf.writeVarInt(storm);
	}

	static Calamity read(RegistryFriendlyByteBuf buf) {
		return new Calamity(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}
}
