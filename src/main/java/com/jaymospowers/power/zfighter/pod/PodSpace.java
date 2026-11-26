package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.power.zfighter.ZFighterRaiders;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crossing between two worlds, flown by hand (Patch 9, track B: tr_cockpit, tr_map, tr_sling, tr_raiders, tr_wrecks,
 * tr_rocks, tr_patrol). Not a dimension: a flight space the server simulates for one trip, with the world left at its start
 * ({@code 0, 0, 0}) and the world ahead at {@code 0, 0, }{@link #ahead}. The pod cruises, boosts, brakes and turns on what the
 * pilot sends ({@link PodFlight.Input}); with the autopilot on it flies the straight way itself, steering round rocks and the
 * Maw, and any touch of the stick takes it back.
 * <p>
 * What is out there, made when the trip begins and nowhere else (so it never grows over a long flight):
 * <ul>
 * <li><b>Rock fields</b> hold the ores of a world they broke from; the MINING_BEAM module reaches {@link #BEAM_RANGE} blocks.</li>
 * <li>A <b>derelict pod</b> drifts across the way now and then: slow beside it and hold the tool key to strip it (a keel, plates,
 * a tank). Keels are found, never made.</li>
 * <li><b>Raider pods</b> of the warrior-born ({@link ZFighterRaiders}) catch you in the dark: the KI_CANNON module shoots them, the
 * SHIELD module takes their bolts, and boost outruns them.</li>
 * <li>The <b>Maw</b> hangs off to one side of a long way (when its chart is read). Skim its pull on boost and it throws you on: a
 * fuel saved. Too close and it takes you.</li>
 * <li>A <b>Patrol post</b> hangs above a few worlds: slow beside it and dock.</li>
 * </ul>
 * Everything here is the server's; the client draws the {@link PodFlight.State} sent each tick. Loot goes to the pilot's pack
 * ({@link PodLoot}).
 */
public final class PodSpace {
	// ---- tuning: the pod ----
	/** Turn rates (degrees a tick) at full stick, and the autopilot's; the steepest the nose may point. */
	static final double TURN = 3.0, PITCH_TURN = 2.2, AUTO_TURN = 4.0, PITCH_MAX = 80.0;
	/** How fast the pod gains or loses speed (blocks a tick, a tick), and the speed a brake settles at (share of cruise). */
	static final double ACCEL = 0.05, BRAKE_SHARE = 0.15;
	/** The pod's size for what it bumps into, and how far from the way's line it may wander. */
	static final double SHIP_R = 4.0, LANE = 260.0;
	/** How long a full boost lasts, and how long it takes to come back (ticks). */
	static final int BOOST_DRAIN = 200, BOOST_REFILL = 400;
	/** The hull a pod has: a base, and more for each class (C to S); the shield a SHIELD module holds, its wait after a hit and its mend (a tick). */
	static final double HULL_BASE = 16.0, HULL_PER_CLASS = 4.0, SHIELD_MAX = 10.0, SHIELD_DELAY = 80.0, SHIELD_MEND = 0.06;
	// ---- tuning: rocks ----
	/** Rock fields on a way (one more on a long way), rocks in each, how big they are, and how wide a field spreads and sits off the line. */
	static final int FIELDS = 1, ROCKS_MIN = 8, ROCKS_MAX = 14;
	static final double ROCK_R_MIN = 3.0, ROCK_R_MAX = 9.0, FIELD_SPREAD = 130.0, FIELD_OFF = 70.0;
	/** The mining beam: how far it reaches (blocks, from the rock's face), how wide a cone it needs the rock in (cosine), and the rock's strength per radius. */
	public static final double BEAM_RANGE = 24.0;
	static final double BEAM_CONE = 0.55, ROCK_HP = 10.0;
	/** What a rock gives: at least this many ores, and one more for each this many blocks of its radius. */
	static final int ORE_MIN = 2;
	static final double ORE_PER_R = 4.0;
	// ---- tuning: derelict pods ----
	/** The odds a way has a derelict pod, where it crosses (share of the way), how fast it drifts, how near you strip it, how slowly beside it, how long it takes. */
	static final float WRECK_CHANCE = 0.55F;
	static final double WRECK_FROM = 0.30, WRECK_TO = 0.75, WRECK_DRIFT = 0.4, STRIP_RANGE = 16.0, MATCH_SPEED = 0.7, WRECK_R = 7.0;
	static final int STRIP_TICKS = 70;
	// ---- tuning: raiders ----
	/** Where on the way a band may catch you (share), how far behind they begin, how fast they fly, how tough, how far they shoot. */
	static final double RAID_FROM = 0.30, RAID_TO = 0.60, RAID_BEHIND = 420.0, RAIDER_SPEED = 2.3, RAIDER_R = 6.0, FIRE_RANGE = 300.0, STANDOFF = 100.0;
	static final double RAIDER_HP = 5.0, CHIEF_HP = 9.0;
	/** A bolt's speed, how near it must pass to hit, what it does, how long it lives, and the wait between a raider's shots (a base and a spread). */
	static final double BOLT_SPEED = 3.2, BOLT_HIT = 6.0, BOLT_DAMAGE = 3.0;
	static final int BOLT_LIFE = 220, FIRE_BASE = 45, FIRE_SPREAD = 35;
	/** Raiders left this far behind for this long give up the chase (blocks, ticks). */
	static final double LOSE_RANGE = 560.0;
	static final int LOSE_TICKS = 120;
	/** The ki cannon: damage a shot, the wait between shots, its reach, and how near the line of the nose a raider must be (blocks, growing with range). */
	static final double CANNON_DAMAGE = 1.0, CANNON_RANGE = 320.0, CANNON_AIM = 8.0, CANNON_AIM_PER_BLOCK = 0.025;
	static final int CANNON_WAIT = 7;
	// ---- tuning: the Maw ----
	/** One block of the Maw's own world is this many blocks here: its pull and tide ranges (ZFighterSpace.MAW_PULL_RANGE and MAW_TIDE_RANGE) are scaled by it. */
	static final double MAW_SCALE = 3.0;
	/** How far off the way's line the Maw hangs, how long a way must be (leagues) to pass it, and how much its pull is stronger here than in its own world. */
	static final double MAW_SIDE = 300.0, MAW_AT = 0.55, PULL_GAIN = 4.0, PULL_DRAG = 0.96;
	static final int MAW_MIN_LEAGUES = 3;
	/** The skimming band reaches this many times the tide's range; inside this many times the tide's range it takes you. */
	static final double SKIM_FACTOR = 3.0, CAPTURE_FACTOR = 1.15;
	/** Boosted ticks in the band that make a throw, the speed the throw holds, for how long, and the fuel a throw saves. */
	static final int SKIM_MIN = 12, THROW_TICKS = 140;
	public static final int SLING_SAVES = 1;
	static final double THROW_SPEED = 4.4;
	// ---- tuning: posts ----
	/** How near (blocks, from its edge) and how slowly a pod docks at a Patrol post, and how big a post is. */
	static final double DOCK_RANGE = 45.0, DOCK_SPEED = 1.2, POST_R = 16.0;
	/** Worlds with a Patrol post hanging above them. */
	public static final int[] POST_WORLDS = {ZFighterSpace.MOON, ZFighterSpace.RUSTFALL, ZFighterSpace.GLACIA};
	/** How far out things are drawn (blocks): further ones are left out of what is sent. */
	static final double VIEW = 900.0;

	/** What a tick of the crossing came to. */
	public enum Result {
		FLYING, ARRIVED, DOCKED, CRASHED
	}

	/** The lines the view shows ({@code State.notice}'s low six bits). */
	public static final int N_RAIDERS = 1, N_LOST = 2, N_BEATEN = 3, N_STRIPPED = 4, N_MINED = 5, N_THROWN = 6, N_CAUGHT = 7, N_SHIELD_DOWN = 8,
		N_NO_CANNON = 9, N_NO_BEAM = 10, N_MATCH = 11, N_NOTHING = 12, N_AUTO_ON = 13, N_AUTO_OFF = 14, N_CAN_DOCK = 15, N_WRECK = 16, N_ROCKS = 17,
		N_MAW = 18, N_HULL_LOW = 20;

	/** One body out there. */
	private static final class Body {
		final int id;
		final int kind;
		double x, y, z, vx, vy, vz;
		double r;
		double hp, maxHp;
		/** A rock's ore world, a raider's shot wait, a wreck's strip progress. */
		int ore, wait, progress;
		int life;
		boolean chief, done;

		Body(int id, int kind, double x, double y, double z, double r) {
			this.id = id;
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
			this.r = r;
		}

		Vec3 pos() {
			return new Vec3(x, y, z);
		}
	}

	private final RandomSource random;
	public final int from, to, leagues;
	private final double route, ahead;
	private final boolean cannon, shieldFitted, beam, towing;
	private final double speedScale;
	private final int read;
	private final int heightClass;
	private final List<Body> bodies = new ArrayList<>();
	private int nextId = 1;
	// the pod
	private double px, py, pz, yaw, pitch, speed, hull, hullMax, shield, boost = 1.0;
	private double pullX, pullY, pullZ;
	private boolean auto;
	private int sinceHit = 1000, throwTicks, skimTicks, shot, shotAt = -1, toolTarget = -1, toolProgress, toolMax = 1, noticeSeq, notice, noticeArg;
	private int tick, coolCannon, nextHint;
	private int flags;
	private boolean thrown, saved, raidSpawned, raidBeaten, raidLost;
	private double raidAt;
	private int band, farBehind;
	private double bodyRel;
	private @Nullable Body maw, post;

	/**
	 * A crossing from world {@code from} to {@code to} (indices; {@link ZFighterSpace#HOME_INDEX} for home), for {@code leagues}
	 * of way. {@code read} is the pilot's read charts.
	 */
	public PodSpace(ServerPlayer player, PodEntity pod, int from, int to, int leagues, boolean auto, int read, boolean towing, boolean hollow, float flightShare) {
		this.random = player.getRandom();
		this.from = from;
		this.to = to;
		this.leagues = leagues;
		this.route = PodFlight.distance(leagues, hollow);
		this.ahead = PodFlight.START + this.route + PodFlight.ARRIVE_R;
		this.cannon = PodModules.has(pod, PodModules.Kind.KI_CANNON);
		this.shieldFitted = PodModules.has(pod, PodModules.Kind.SHIELD);
		this.beam = PodModules.has(pod, PodModules.Kind.MINING_BEAM);
		this.towing = towing;
		this.heightClass = PodClass.of(pod).ordinal();
		this.speedScale = PodFlight.classSpeed(flightShare) * (towing ? PodTow.SLOW : 1.0);
		this.read = read;
		this.auto = auto;
		this.px = 0.0;
		this.py = 0.0;
		this.pz = PodFlight.START;
		this.hullMax = HULL_BASE + HULL_PER_CLASS * this.heightClass;
		this.hull = this.hullMax;
		this.shield = this.shieldFitted ? SHIELD_MAX : 0.0;
		this.speed = PodFlight.CRUISE * 0.4;
		fill(player);
	}

	/** Whether the throw has saved a fuel that has not yet been paid back to the pod. */
	public boolean takeSaving() {
		if (this.thrown && !this.saved) {
			this.saved = true;
			return true;
		}
		return false;
	}

	/** How far along the way the pod is, 0 to 1. */
	public double progress() {
		return Mth.clamp((this.pz - PodFlight.START) / this.route, 0.0, 1.0);
	}

	private double heading(int axis) {
		double yawRad = Math.toRadians(this.yaw), pitchRad = Math.toRadians(this.pitch);
		return switch (axis) {
			case 0 -> Math.sin(yawRad) * Math.cos(pitchRad);
			case 1 -> Math.sin(pitchRad);
			default -> Math.cos(yawRad) * Math.cos(pitchRad);
		};
	}

	private Vec3 forward() {
		return new Vec3(heading(0), heading(1), heading(2));
	}

	private Vec3 shipPos() {
		return new Vec3(this.px, this.py, this.pz);
	}

	// ---------------------------------------------------------------- what is out there

	private Body add(int kind, double x, double y, double z, double r) {
		Body body = new Body(this.nextId++, kind, x, y, z, r);
		this.bodies.add(body);
		return body;
	}

	private void fill(ServerPlayer player) {
		int fields = FIELDS + (this.leagues >= MAW_MIN_LEAGUES ? 1 : 0) + (this.random.nextInt(3) == 0 ? 1 : 0);
		for (int f = 0; f < fields; f++) {
			double share = 0.15 + 0.7 * (f + this.random.nextDouble() * 0.6) / fields;
			double cx = (this.random.nextDouble() * 2.0 - 1.0) * FIELD_OFF;
			double cy = (this.random.nextDouble() * 2.0 - 1.0) * FIELD_OFF * 0.6;
			double cz = PodFlight.START + this.route * share;
			int ore = this.random.nextBoolean() ? this.from : this.to;
			int count = ROCKS_MIN + this.random.nextInt(ROCKS_MAX - ROCKS_MIN + 1);
			for (int i = 0; i < count; i++) {
				double r = ROCK_R_MIN + this.random.nextDouble() * (ROCK_R_MAX - ROCK_R_MIN);
				Body rock = add(PodFlight.ROCK, cx + gauss() * FIELD_SPREAD * 0.5, cy + gauss() * FIELD_SPREAD * 0.35, cz + gauss() * FIELD_SPREAD * 0.7, r);
				rock.ore = ore;
				rock.maxHp = rock.hp = r * ROCK_HP;
			}
		}
		if (this.random.nextFloat() < WRECK_CHANCE) {
			double share = WRECK_FROM + this.random.nextDouble() * (WRECK_TO - WRECK_FROM);
			double side = this.random.nextBoolean() ? 1.0 : -1.0;
			Body wreck = add(PodFlight.WRECK, side * (90.0 + this.random.nextDouble() * 50.0), (this.random.nextDouble() - 0.5) * 60.0,
				PodFlight.START + this.route * share, WRECK_R);
			wreck.vx = -side * WRECK_DRIFT;
			wreck.vy = (this.random.nextDouble() - 0.5) * 0.1;
			wreck.maxHp = wreck.hp = 1.0;
		}
		this.band = ZFighterRaiders.spaceBand(player, this.random);
		this.raidAt = RAID_FROM + this.random.nextDouble() * (RAID_TO - RAID_FROM);
		if (this.leagues >= MAW_MIN_LEAGUES && this.from != ZFighterSpace.MAW && this.to != ZFighterSpace.MAW && ZFighterSpace.charted(this.read, ZFighterSpace.MAW)) {
			double tide = ZFighterSpace.MAW_TIDE_RANGE * MAW_SCALE;
			this.maw = add(PodFlight.MAW, MAW_SIDE, 0.0, PodFlight.START + this.route * MAW_AT, tide);
		}
		for (int world : POST_WORLDS) {
			if (world == this.to && ZFighterSpace.charted(this.read, world)) {
				double r = PodFlight.WORLD_R;
				this.post = add(PodFlight.POST, -r * 1.1, r * 0.3, this.ahead - r * 2.1, POST_R);
			}
		}
	}

	private double gauss() {
		return Mth.clamp(this.random.nextGaussian(), -2.0, 2.0) * 0.5;
	}

	// ---------------------------------------------------------------- one tick

	/** One tick of the crossing, for {@code player} flying {@code pod}; what it came to. */
	public Result tick(ServerPlayer player, PodEntity pod, @Nullable ServerPlayer guest) {
		this.tick++;
		PodFlight.Input in = PodFlight.input(player);
		if (PodFlight.toggled(player)) {
			this.auto = !this.auto;
			notice(this.auto ? N_AUTO_ON : N_AUTO_OFF, 0);
		}
		if (this.auto && (in.yaw() != 0 || in.pitch() != 0)) {
			this.auto = false;
			notice(N_AUTO_OFF, 0);
		}
		this.flags = 0;
		steer(in);
		double boostable = this.boost > 0.02 ? 1.0 : 0.0;
		boolean boosting = (in.has(PodFlight.BOOST_KEY) || this.auto && raidersNear()) && boostable > 0.0;
		boolean braking = in.has(PodFlight.BRAKE_KEY) && !this.auto;
		double cruise = PodFlight.CRUISE * this.speedScale;
		double top = boosting ? PodFlight.BOOST * this.speedScale : cruise;
		if (braking) {
			top = cruise * BRAKE_SHARE;
		}
		if (this.auto) {
			double left = (this.ahead - PodFlight.ARRIVE_R - this.pz) / this.route;
			if (left < PodFlight.AUTO_SLOW_SHARE) {
				top = Math.min(top, cruise * Mth.lerp(Math.max(0.0, left) / PodFlight.AUTO_SLOW_SHARE, PodFlight.AUTO_SLOW_FLOOR, 1.0));
			}
		}
		if (this.throwTicks > 0) {
			this.throwTicks--;
			top = Math.max(top, THROW_SPEED);
			this.flags |= PodFlight.F_THROWN;
		}
		this.speed += Mth.clamp(top - this.speed, -ACCEL * 2.0, ACCEL);
		if (boosting && this.speed > cruise * 1.05) {
			this.boost = Math.max(0.0, this.boost - 1.0 / BOOST_DRAIN);
			this.flags |= PodFlight.F_BOOST;
		} else {
			this.boost = Math.min(1.0, this.boost + 1.0 / BOOST_REFILL);
		}
		// the Maw pulls (and may throw or take)
		Result maw = mawPull(boosting);
		if (maw != Result.FLYING) {
			return maw;
		}
		double vx = heading(0) * this.speed + this.pullX, vy = heading(1) * this.speed + this.pullY, vz = heading(2) * this.speed + this.pullZ;
		this.px += vx;
		this.py += vy;
		this.pz += vz;
		lane();
		// bodies
		bodiesMove(vx, vy, vz);
		this.bodyRel = 0.0;
		Result hit = collide();
		if (hit != Result.FLYING) {
			return hit;
		}
		tools(player, in, vx, vy, vz);
		cannon(player, in);
		raiders(player);
		this.shield = Math.min(this.shieldFitted ? SHIELD_MAX : 0.0, this.shield + (this.sinceHit++ > SHIELD_DELAY ? SHIELD_MEND : 0.0));
		if (this.hull <= 0.0) {
			return Result.CRASHED;
		}
		// where we are
		double toWorld = Math.sqrt(this.px * this.px + this.py * this.py + (this.pz - this.ahead) * (this.pz - this.ahead));
		if (toWorld <= PodFlight.ARRIVE_R) {
			return Result.ARRIVED;
		}
		Body near = this.post;
		if (near != null) {
			double d = near.pos().distanceTo(shipPos()) - near.r;
			if (d <= DOCK_RANGE) {
				this.flags |= PodFlight.F_CAN_DOCK;
				if (this.speed <= DOCK_SPEED && in.has(PodFlight.DOCK_KEY)) {
					return Result.DOCKED;
				}
				if (this.tick >= this.nextHint) {
					notice(N_CAN_DOCK, 0);
					this.nextHint = this.tick + 160;
				}
			}
		}
		if (this.hull < this.hullMax * 0.3 && this.tick % 200 == 0) {
			notice(N_HULL_LOW, 0);
		}
		send(player, guest, in);
		return Result.FLYING;
	}

	/** The pod after the world's glare, as the client is told, to {@code player} and any passenger. */
	private void send(ServerPlayer player, @Nullable ServerPlayer guest, PodFlight.Input in) {
		if (this.auto) {
			this.flags |= PodFlight.F_AUTO;
		}
		if (this.shieldFitted) {
			this.flags |= PodFlight.F_SHIELD;
		}
		if (this.cannon) {
			this.flags |= PodFlight.F_CANNON;
		}
		if (this.beam) {
			this.flags |= PodFlight.F_BEAM;
		}
		if (this.towing) {
			this.flags |= PodFlight.F_TOW;
		}
		if (!raidersAlive().isEmpty()) {
			this.flags |= PodFlight.F_ALERT;
		}
		if (this.sinceHit < 6) {
			this.flags |= PodFlight.F_HIT;
		}
		List<PodFlight.Obj> objs = new ArrayList<>();
		Vec3 ship = shipPos();
		for (Body b : this.bodies) {
			if (b.done && b.kind != PodFlight.WRECK) {
				continue;
			}
			if (b.pos().distanceTo(ship) > VIEW && b.kind != PodFlight.MAW && b.kind != PodFlight.POST) {
				continue;
			}
			float f = b.maxHp <= 0.0 ? 1.0F : (float)Mth.clamp(b.hp / b.maxHp, 0.0, 1.0);
			if (b.kind == PodFlight.WRECK) {
				f = b.done ? 0.0F : (float)b.progress / STRIP_TICKS;
			}
			objs.add(new PodFlight.Obj(b.id, b.kind, (float)b.x, (float)b.y, (float)b.z, (float)b.r, f));
		}
		double left = Math.max(0.0, this.ahead - PodFlight.ARRIVE_R - this.pz);
		int eta = (int)Math.round(left / Math.max(0.3, PodFlight.CRUISE * this.speedScale));
		PodFlight.State state = new PodFlight.State(PodFlight.PHASE_SPACE, this.flags, (float)this.speed, (float)progress(), eta, (float)(this.hull / this.hullMax),
			(float)(this.shieldFitted ? this.shield / SHIELD_MAX : 0.0), (float)this.boost, (float)this.px, (float)this.py, (float)this.pz, (float)this.yaw,
			(float)this.pitch, this.from, this.to, this.read, this.noticeSeq << 6 | this.notice, this.noticeArg, this.shot, this.shotAt, this.toolTarget,
			this.toolMax <= 0 ? 0.0F : (float)this.toolProgress / this.toolMax, (float)this.bodyRel, objs);
		ServerPlayNetworking.send(player, state);
		if (guest != null) {
			ServerPlayNetworking.send(guest, state);
		}
	}

	private void notice(int id, int arg) {
		this.notice = id;
		this.noticeArg = arg;
		this.noticeSeq = (this.noticeSeq + 1) & 0xFFFF;
	}

	// ---------------------------------------------------------------- steering

	private void steer(PodFlight.Input in) {
		if (this.auto) {
			Vec3 want = autoHeading();
			double wantYaw = Math.toDegrees(Math.atan2(want.x, want.z));
			double wantPitch = Math.toDegrees(Math.asin(Mth.clamp(want.y, -1.0, 1.0)));
			this.yaw += Mth.clamp(Mth.wrapDegrees(wantYaw - this.yaw), -AUTO_TURN, AUTO_TURN);
			this.pitch += Mth.clamp(wantPitch - this.pitch, -AUTO_TURN, AUTO_TURN);
		} else {
			this.yaw = Mth.wrapDegrees(this.yaw + in.yaw() * TURN);
			this.pitch += in.pitch() * PITCH_TURN;
		}
		this.pitch = Mth.clamp(this.pitch, -PITCH_MAX, PITCH_MAX);
	}

	/** The autopilot's heading: for the world ahead, bent round rocks in the way and out of the Maw's pull. */
	private Vec3 autoHeading() {
		Vec3 ship = shipPos();
		Vec3 go = new Vec3(0.0, 0.0, this.ahead).subtract(ship).normalize();
		Vec3 f = forward();
		for (Body b : this.bodies) {
			if (b.done) {
				continue;
			}
			double clear = b.kind == PodFlight.ROCK ? b.r + 16.0 : b.kind == PodFlight.WRECK ? b.r + 20.0
				: b.kind == PodFlight.MAW ? ZFighterSpace.MAW_PULL_RANGE * MAW_SCALE * 1.1 : 0.0;
			if (clear <= 0.0) {
				continue;
			}
			Vec3 rel = b.pos().subtract(ship);
			double along = rel.dot(f);
			double reach = b.kind == PodFlight.MAW ? clear : 120.0;
			if (along <= -clear * 0.2 || along > reach + clear) {
				continue;
			}
			Vec3 side = rel.subtract(f.scale(along));
			double apart = side.length();
			if (apart >= clear) {
				continue;
			}
			Vec3 away = apart < 0.01 ? new Vec3(0.0, 1.0, 0.0) : side.scale(-1.0 / apart);
			go = go.add(away.scale((1.0 - apart / clear) * 1.8)).normalize();
		}
		return go;
	}

	/** Keeps the pod in the lane of the way, and the way's two ends. */
	private void lane() {
		double off = Math.sqrt(this.px * this.px + this.py * this.py);
		if (off > LANE) {
			double k = LANE / off;
			this.px *= k;
			this.py *= k;
		}
		this.pz = Mth.clamp(this.pz, PodFlight.START * 0.5, this.ahead + 150.0);
	}

	private boolean raidersNear() {
		for (Body b : raidersAlive()) {
			if (b.pos().distanceTo(shipPos()) < FIRE_RANGE) {
				return !this.cannon;
			}
		}
		return false;
	}

	private List<Body> raidersAlive() {
		List<Body> alive = new ArrayList<>();
		for (Body b : this.bodies) {
			if (b.kind == PodFlight.RAIDER && !b.done) {
				alive.add(b);
			}
		}
		return alive;
	}

	// ---------------------------------------------------------------- the Maw

	/** Its pull on the pod; a throw or a capture. */
	private Result mawPull(boolean boosting) {
		Body m = this.maw;
		if (m == null) {
			return Result.FLYING;
		}
		Vec3 to = m.pos().subtract(shipPos());
		double distance = to.length();
		double range = ZFighterSpace.MAW_PULL_RANGE * MAW_SCALE;
		double tide = ZFighterSpace.MAW_TIDE_RANGE * MAW_SCALE;
		this.pullX *= PULL_DRAG;
		this.pullY *= PULL_DRAG;
		this.pullZ *= PULL_DRAG;
		if (distance < 1.0 || distance > range) {
			if (this.skimTicks >= SKIM_MIN && distance > tide * SKIM_FACTOR && !this.thrown) {
				// out the far side of the band, boosted: the Maw throws the pod on
				this.thrown = true;
				this.throwTicks = THROW_TICKS;
				notice(N_THROWN, SLING_SAVES);
			}
			this.skimTicks = 0;
			return Result.FLYING;
		}
		if (this.notice != N_MAW && this.tick % 100 == 1) {
			notice(N_MAW, 0);
		}
		if (distance < tide * CAPTURE_FACTOR) {
			return Result.CRASHED;
		}
		double share = 1.0 - distance / range;
		double pull = (ZFighterSpace.MAW_PULL_FAR + (ZFighterSpace.MAW_PULL_NEAR - ZFighterSpace.MAW_PULL_FAR) * share * share) * PULL_GAIN;
		Vec3 pulling = to.scale(pull / distance);
		this.pullX += pulling.x;
		this.pullY += pulling.y;
		this.pullZ += pulling.z;
		this.flags |= PodFlight.F_SKIM;
		if (distance <= tide * SKIM_FACTOR) {
			if (boosting && this.speed > PodFlight.CRUISE * this.speedScale * 1.05) {
				this.skimTicks++;
			} else {
				this.skimTicks = Math.max(0, this.skimTicks - 2);
			}
		}
		return Result.FLYING;
	}

	/** Whether the pod was taken by the Maw (the last tick's result was a crash with the Maw close). */
	public boolean caught() {
		Body m = this.maw;
		return m != null && m.pos().distanceTo(shipPos()) < ZFighterSpace.MAW_TIDE_RANGE * MAW_SCALE * CAPTURE_FACTOR + 1.0;
	}

	// ---------------------------------------------------------------- bodies moving, and hitting

	private void bodiesMove(double vx, double vy, double vz) {
		Iterator<Body> it = this.bodies.iterator();
		while (it.hasNext()) {
			Body b = it.next();
			if (b.kind == PodFlight.WRECK) {
				b.x += b.vx;
				b.y += b.vy;
				b.z += b.vz;
			} else if (b.kind == PodFlight.BOLT) {
				b.x += b.vx;
				b.y += b.vy;
				b.z += b.vz;
				if (--b.life <= 0) {
					it.remove();
				}
			}
		}
	}

	/** Damage to the pod: the shield first. */
	private void damage(double amount) {
		this.sinceHit = 0;
		if (this.shield > 0.0) {
			double soaked = Math.min(this.shield, amount);
			this.shield -= soaked;
			amount -= soaked;
			if (this.shield <= 0.0) {
				notice(N_SHIELD_DOWN, 0);
			}
		}
		this.hull -= amount;
	}

	/** Rocks and bolts that touch the pod. */
	private Result collide() {
		Vec3 ship = shipPos();
		Iterator<Body> it = this.bodies.iterator();
		while (it.hasNext()) {
			Body b = it.next();
			if (b.done) {
				continue;
			}
			double d = b.pos().distanceTo(ship);
			if (b.kind == PodFlight.ROCK && d < b.r + SHIP_R) {
				damage(Math.min(8.0, 1.0 + b.r * 0.35 + this.speed * 0.8));
				this.speed *= 0.4;
				b.done = true;
			} else if (b.kind == PodFlight.BOLT && d < BOLT_HIT + SHIP_R) {
				damage(BOLT_DAMAGE);
				it.remove();
			} else if (b.kind == PodFlight.WRECK && d < b.r + SHIP_R * 0.5) {
				// nudged off: a wreck is not run into twice
				Vec3 away = ship.subtract(b.pos()).normalize();
				this.px += away.x * 2.0;
				this.py += away.y * 2.0;
				this.pz += away.z * 2.0;
				this.speed *= 0.6;
			}
		}
		return this.hull <= 0.0 ? Result.CRASHED : Result.FLYING;
	}

	// ---------------------------------------------------------------- the tools: the mining beam, stripping a derelict

	private void tools(ServerPlayer player, PodFlight.Input in, double vx, double vy, double vz) {
		this.toolTarget = -1;
		Vec3 ship = shipPos();
		Vec3 f = forward();
		Body rock = null;
		double best = Double.MAX_VALUE;
		Body wreck = null;
		double wreckBest = Double.MAX_VALUE;
		for (Body b : this.bodies) {
			if (b.done) {
				continue;
			}
			Vec3 rel = b.pos().subtract(ship);
			double d = rel.length();
			if (b.kind == PodFlight.ROCK && d - b.r <= BEAM_RANGE && d > 0.1 && rel.scale(1.0 / d).dot(f) >= BEAM_CONE && d < best) {
				best = d;
				rock = b;
			} else if (b.kind == PodFlight.WRECK && d - b.r <= STRIP_RANGE && d < wreckBest) {
				wreckBest = d;
				wreck = b;
			}
		}
		if (rock != null && this.tick >= this.nextHint && !in.has(PodFlight.TOOL_KEY)) {
			notice(this.beam ? N_ROCKS : N_NO_BEAM, 0);
			this.nextHint = this.tick + 400;
		}
		if (wreck != null) {
			double rel = Math.sqrt(sq(vx - wreck.vx) + sq(vy - wreck.vy) + sq(vz - wreck.vz));
			this.bodyRel = rel;
			if (this.tick >= this.nextHint && !in.has(PodFlight.TOOL_KEY)) {
				notice(N_WRECK, 0);
				this.nextHint = this.tick + 300;
			}
		}
		if (!in.has(PodFlight.TOOL_KEY)) {
			if (wreck != null && wreck.progress > 0) {
				wreck.progress = Math.max(0, wreck.progress - 2);
			}
			return;
		}
		if (rock != null && this.beam) {
			this.toolTarget = rock.id;
			this.toolMax = (int)Math.ceil(rock.maxHp);
			rock.hp -= 1.0;
			this.toolProgress = (int)Math.round(rock.maxHp - rock.hp);
			if (rock.hp <= 0.0) {
				rock.done = true;
				int count = ORE_MIN + (int)Math.floor(rock.r / ORE_PER_R) + this.random.nextInt(2);
				PodLoot.ore(player, rock.ore, count, this.random);
				notice(N_MINED, count);
				this.toolTarget = -1;
				this.toolProgress = 0;
			}
			return;
		}
		if (rock != null && !this.beam && wreck == null) {
			if (this.tick >= this.nextHint) {
				notice(N_NO_BEAM, 0);
				this.nextHint = this.tick + 160;
			}
			return;
		}
		if (wreck != null) {
			this.toolTarget = wreck.id;
			this.toolMax = STRIP_TICKS;
			if (this.bodyRel > MATCH_SPEED) {
				wreck.progress = Math.max(0, wreck.progress - 3);
				this.toolProgress = wreck.progress;
				if (this.tick >= this.nextHint) {
					notice(N_MATCH, 0);
					this.nextHint = this.tick + 100;
				}
				return;
			}
			wreck.progress++;
			this.toolProgress = wreck.progress;
			if (wreck.progress >= STRIP_TICKS) {
				wreck.done = true;
				PodLoot.strip(player, this.random);
				notice(N_STRIPPED, 0);
				this.toolTarget = -1;
				this.toolProgress = 0;
			}
			return;
		}
		if (this.tick >= this.nextHint) {
			notice(N_NOTHING, 0);
			this.nextHint = this.tick + 100;
		}
	}

	private static double sq(double v) {
		return v * v;
	}

	// ---------------------------------------------------------------- the ki cannon

	private void cannon(ServerPlayer player, PodFlight.Input in) {
		if (this.coolCannon > 0) {
			this.coolCannon--;
		}
		if (!in.has(PodFlight.FIRE_KEY) || this.coolCannon > 0) {
			return;
		}
		if (!this.cannon) {
			if (this.tick >= this.nextHint) {
				notice(N_NO_CANNON, 0);
				this.nextHint = this.tick + 160;
			}
			return;
		}
		this.coolCannon = CANNON_WAIT;
		Vec3 ship = shipPos();
		Vec3 f = forward();
		Body hit = null;
		double best = Double.MAX_VALUE;
		for (Body b : this.bodies) {
			if (b.kind != PodFlight.RAIDER || b.done) {
				continue;
			}
			Vec3 rel = b.pos().subtract(ship);
			double along = rel.dot(f);
			if (along < 5.0 || along > CANNON_RANGE) {
				continue;
			}
			double perp = rel.subtract(f.scale(along)).length();
			if (perp <= CANNON_AIM + b.r + along * CANNON_AIM_PER_BLOCK && along < best) {
				best = along;
				hit = b;
			}
		}
		this.shot++;
		this.shotAt = hit == null ? -1 : hit.id;
		if (hit != null) {
			hit.hp -= CANNON_DAMAGE * (1.0 + 0.1 * this.heightClass);
			if (hit.hp <= 0.0) {
				hit.done = true;
				PodLoot.raider(player, hit.chief);
			}
		}
	}

	// ---------------------------------------------------------------- the raiders

	private void raiders(ServerPlayer player) {
		Vec3 ship = shipPos();
		if (!this.raidSpawned && this.band > 0 && progress() >= this.raidAt) {
			this.raidSpawned = true;
			Vec3 f = forward();
			for (int i = 0; i < this.band; i++) {
				Vec3 at = ship.subtract(f.scale(RAID_BEHIND)).add((this.random.nextDouble() - 0.5) * 160.0, (this.random.nextDouble() - 0.5) * 100.0,
					(this.random.nextDouble() - 0.5) * 80.0);
				Body raider = add(PodFlight.RAIDER, at.x, at.y, at.z, RAIDER_R);
				raider.chief = i == 0;
				raider.maxHp = raider.hp = raider.chief ? CHIEF_HP : RAIDER_HP;
				raider.wait = FIRE_BASE + this.random.nextInt(FIRE_SPREAD);
			}
			notice(N_RAIDERS, this.band);
		}
		List<Body> alive = raidersAlive();
		if (alive.isEmpty()) {
			if (this.raidSpawned && !this.raidBeaten && !this.raidLost) {
				this.raidBeaten = true;
				notice(N_BEATEN, 0);
			}
			return;
		}
		boolean allFar = true;
		for (Body b : alive) {
			Vec3 rel = ship.subtract(b.pos());
			double d = rel.length();
			Vec3 out = d < 0.01 ? new Vec3(0.0, 0.0, -1.0) : rel.scale(-1.0 / d);
			// hold a standoff, circling a little; the chase is as fast as their pod goes
			Vec3 tangent = new Vec3(-out.z, 0.0, out.x).scale(0.8);
			Vec3 want = d > STANDOFF ? rel.scale(1.0 / d) : rel.scale(-0.2 / Math.max(d, 1.0)).add(tangent);
			if (d <= STANDOFF) {
				want = tangent.add(rel.scale(1.0 / Math.max(d, 1.0)).scale(Math.min(1.0, (d - STANDOFF * 0.6) / STANDOFF)));
			}
			double pace = d > STANDOFF ? RAIDER_SPEED : RAIDER_SPEED * 0.5;
			Vec3 step = want.lengthSqr() < 1.0E-6 ? Vec3.ZERO : want.normalize().scale(pace);
			b.x += step.x;
			b.y += step.y;
			b.z += step.z;
			if (--b.wait <= 0 && d < FIRE_RANGE) {
				b.wait = FIRE_BASE + this.random.nextInt(FIRE_SPREAD);
				double lead = d / BOLT_SPEED;
				Vec3 vel = new Vec3(heading(0), heading(1), heading(2)).scale(this.speed);
				Vec3 aim = ship.add(vel.scale(lead)).add((this.random.nextDouble() - 0.5) * 14.0, (this.random.nextDouble() - 0.5) * 14.0,
					(this.random.nextDouble() - 0.5) * 14.0).subtract(b.pos());
				Vec3 dir = aim.normalize().scale(BOLT_SPEED);
				Body bolt = add(PodFlight.BOLT, b.x, b.y, b.z, 1.5);
				bolt.vx = dir.x;
				bolt.vy = dir.y;
				bolt.vz = dir.z;
				bolt.life = BOLT_LIFE;
			}
			if (d < LOSE_RANGE) {
				allFar = false;
			}
		}
		this.farBehind = allFar ? this.farBehind + 1 : 0;
		if (this.farBehind >= LOSE_TICKS) {
			for (Body b : alive) {
				b.done = true;
			}
			this.raidLost = true;
			notice(N_LOST, 0);
		}
	}

	/** The state the client is sent when the crossing is over, so its view closes. */
	public static PodFlight.State end() {
		return new PodFlight.State(PodFlight.PHASE_END, 0, 0.0F, 1.0F, 0, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1, -1, 0, 0, 0, 0, -1, -1, 0.0F, 0.0F,
			List.of());
	}
}
