package com.jaymospowers.power.chrono.future;

/**
 * The future's four cars (Patch 8: "exactly four cars", ported from the Cars mod's hatchback, pickup, muscle car and roadster
 * by {@code tools/car_port.py}, MIT, Pokato; our names). The numbers are in blocks and ticks: {@link #top} the fastest it
 * goes on the flat (blocks a tick, 1.0 is 72 km/h), {@link #accel} how hard it picks up, the axles and the wheels' radius
 * where its arches are, and {@link #width}/{@link #height} the box it takes up.
 */
public enum CarKind {
	/** The little hatchback: handy, slow to get going, forgiving. */
	RUNABOUT("future_runabout", "runabout", 0.85, 0.034, 2.0F, 1.3F, 0.50F, -0.57F, 0.42F),
	/** The pickup: the slowest, the best on a slope, the most room. */
	HAULER("future_hauler", "hauler", 0.65, 0.026, 2.4F, 1.7F, 0.71F, -1.05F, 0.45F),
	/** The muscle car: the quickest to pick up and the worst to hold. */
	BRUISER("future_bruiser", "bruiser", 1.05, 0.042, 2.2F, 1.2F, 0.59F, -0.58F, 0.46F),
	/** The roadster: the fastest, and light. */
	DASHER("future_dasher", "dasher", 1.2, 0.045, 2.0F, 1.1F, 0.50F, -0.53F, 0.42F);

	public final String id, mesh;
	public final double top, accel;
	public final float width, height;
	/** Where the front and rear axles are along the car (blocks from its middle) and the wheels' radius. */
	public final float front, rear, wheel;

	CarKind(String id, String mesh, double top, double accel, float width, float height, float front, float rear, float wheel) {
		this.id = id;
		this.mesh = mesh;
		this.top = top;
		this.accel = accel;
		this.width = width;
		this.height = height;
		this.front = front;
		this.rear = rear;
		this.wheel = wheel;
	}
}
