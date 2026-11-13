package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Magic laid on the ground (fifth edition's web, grease, fog cloud, darkness, silence, spike growth, alarm and tiny hut):
 * a place that lasts a while and does something to whoever is in it. This class keeps the list, ticks it, ends what ran
 * out, and answers the questions others ask (is this body in a bubble of silence; what lies at this spot, for a dispel).
 * What each kind does is {@link BattleSpells} (the fight's) and {@link CampSpells} (the camp's); a caster who keeps one up
 * holds it ({@link Concentration}), and when they lose it the place goes with it.
 */
public final class Fields {
	/** What a field is. */
	public enum Kind {
		WEB(0xE8E8E8, true), GREASE(0x6B5A2B, false), FOG(0xD8E2E8, true), DARKNESS(0x0B0A12, true), SILENCE(0x7C6FA0, true),
		THORNS(0x4C7A34, false), WARD_LINE(0x9FD8FF, false), DOME(0xC8D6E8, true), TRUTH(0xF2D98A, false), MOONBEAM(0xCFE0FF, false);

		public final int colour;
		/** A sphere or dome (it has height); the rest lie on the ground. */
		public final boolean round;

		Kind(int colour, boolean round) {
			this.colour = colour;
			this.round = round;
		}
	}

	/** At most this many fields of one owner stand at once; the oldest ends when another is laid. */
	static final int MOST_PER_OWNER = 4;
	/** A ground field reaches this far up and down from where it lies (blocks). */
	static final double BAND_UP = 3.0, BAND_DOWN = 1.5;
	/** How often (ticks) the look of each is drawn again. */
	static final int DRAW_EVERY = 10;
	/** The flat sheets and circles on the ground are laid again this often (ticks), and last a little longer. */
	static final int SHEET_EVERY = 40;

	/** One place of magic. */
	public static final class Field {
		public final Kind kind;
		public final ServerLevel level;
		public final Vec3 at;
		public final double radius;
		public final long made, until;
		public final UUID owner;
		/** The way a ward line runs (a unit vector along the ground); zero for the rest. */
		public final Vec3 axis;
		/** How far above the ground its centre sits (a sphere hangs over its spot). */
		public final double lift;
		/** Where each body was last seen (a ward line watches who crosses it, thorns who moves). */
		final Map<UUID, Vec3> last = new HashMap<>();
		/** When each body was last acted on (game time), so a field does not pounce on the same body every tick. */
		final Map<UUID, Long> acted = new HashMap<>();
		long lastChime;
		boolean ended;

		Field(Kind kind, ServerLevel level, Vec3 at, double radius, double lift, long until, UUID owner, Vec3 axis) {
			this.kind = kind;
			this.level = level;
			this.at = at.add(0.0, lift, 0.0);
			this.lift = lift;
			this.radius = radius;
			this.made = level.getGameTime();
			this.until = until;
			this.owner = owner;
			this.axis = axis;
		}

		/** Whether {@code p} is in it. */
		public boolean contains(Vec3 p) {
			double dy = p.y - this.at.y;
			if (this.kind.round) {
				return p.distanceToSqr(this.at) <= this.radius * this.radius;
			}
			if (this.kind == Kind.WARD_LINE) {
				return false;
			}
			double dx = p.x - this.at.x, dz = p.z - this.at.z;
			return dx * dx + dz * dz <= this.radius * this.radius && dy >= -BAND_DOWN && dy <= BAND_UP;
		}

		/** Whether the walk from {@code a} to {@code b} crosses a ward line, level with it. */
		public boolean crossedBy(Vec3 a, Vec3 b) {
			if (this.kind != Kind.WARD_LINE || Math.abs(b.y - this.at.y) > BAND_UP + 1.0) {
				return false;
			}
			// the line's own frame: along it (u) and across it (v)
			double au = (a.x - this.at.x) * this.axis.x + (a.z - this.at.z) * this.axis.z;
			double av = (a.x - this.at.x) * -this.axis.z + (a.z - this.at.z) * this.axis.x;
			double bu = (b.x - this.at.x) * this.axis.x + (b.z - this.at.z) * this.axis.z;
			double bv = (b.x - this.at.x) * -this.axis.z + (b.z - this.at.z) * this.axis.x;
			if (av * bv > 0.0 || av == bv) {
				return false;
			}
			double u = au + (bu - au) * (av / (av - bv));
			return Math.abs(u) <= this.radius;
		}

		/** The ender (a held spell's, or a dispel's) ends it now. */
		public void end() {
			this.ended = true;
		}

		public boolean alive() {
			return !this.ended;
		}
	}

	private static final List<Field> ALL = new ArrayList<>();

	private Fields() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Fields::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ALL.clear());
		// a ward line or a patch of thorns can be noticed by someone who is not its owner
		Notice.add((player, into) -> {
			for (Field f : ALL) {
				if (f.level != player.level() || f.owner.equals(player.getUUID()) || f.kind != Kind.WARD_LINE && f.kind != Kind.THORNS) {
					continue;
				}
				if (f.at.distanceToSqr(player.position()) <= NOTICE_RANGE * NOTICE_RANGE) {
					into.add(new Notice.Spot(f.owner.getMostSignificantBits() ^ f.made * 31L + f.kind.ordinal(), f.at.add(0.0, 0.3, 0.0),
						SoundEvents.AMETHYST_BLOCK_RESONATE));
				}
			}
		});
	}

	/** How near (blocks) a player may notice a ward line or a patch of thorns that is not theirs. */
	static final double NOTICE_RANGE = 8.0;

	/** Lays a field on the ground at {@code ground}, its centre {@code lift} above it; the owner's oldest ends if they have too many. */
	public static Field add(Kind kind, ServerLevel level, Vec3 ground, double radius, double lift, int ticks, UUID owner, @Nullable Vec3 axis) {
		int mine = 0;
		for (Field f : ALL) {
			if (f.owner.equals(owner) && f.alive()) {
				mine++;
			}
		}
		if (mine >= MOST_PER_OWNER) {
			for (Field f : ALL) {
				if (f.owner.equals(owner) && f.alive()) {
					f.end();
					break;
				}
			}
		}
		Field field = new Field(kind, level, ground, radius, lift, level.getGameTime() + ticks, owner, axis == null ? Vec3.ZERO : axis);
		ALL.add(field);
		return field;
	}

	/** Every field that is up. */
	public static List<Field> all() {
		return ALL;
	}

	/** The fields of {@code kind} {@code p} stands in. */
	public static List<Field> at(ServerLevel level, Vec3 p, @Nullable Kind kind) {
		List<Field> out = new ArrayList<>();
		for (Field f : ALL) {
			if (f.level == level && f.alive() && (kind == null || f.kind == kind) && f.contains(p)) {
				out.add(f);
			}
		}
		return out;
	}

	/** Whether {@code who} stands in a bubble of silence. */
	public static boolean silent(LivingEntity who) {
		if (ALL.isEmpty() || !(who.level() instanceof ServerLevel level)) {
			return false;
		}
		for (Field f : ALL) {
			if (f.kind == Kind.SILENCE && f.level == level && f.alive() && f.contains(who.getEyePosition())) {
				return true;
			}
		}
		return false;
	}

	/** Whether any field of {@code kind} owned by {@code owner} is up. */
	public static boolean has(UUID owner, Kind kind) {
		for (Field f : ALL) {
			if (f.owner.equals(owner) && f.kind == kind && f.alive()) {
				return true;
			}
		}
		return false;
	}

	/** Ends the fields of {@code kind} that {@code owner} laid (a held spell let go). */
	public static void endOf(UUID owner, Kind kind) {
		for (Field f : ALL) {
			if (f.owner.equals(owner) && f.kind == kind) {
				f.end();
			}
		}
	}

	/** Ends every field whose place holds {@code p} or lies within {@code reach} of it; how many ended. */
	public static int dispel(ServerLevel level, Vec3 p, double reach) {
		int n = 0;
		for (Field f : ALL) {
			if (f.level != level || !f.alive()) {
				continue;
			}
			boolean hit = f.kind == Kind.WARD_LINE ? distanceToLine(f, p) <= reach : f.contains(p) || f.at.distanceTo(p) <= f.radius + reach * 0.5;
			if (hit) {
				f.end();
				Fx.burst(level, f.at.x, f.at.y + 0.5, f.at.z, f.kind.colour, 1.2F, 14, f.radius * 0.5, 0.5, f.radius * 0.5);
				n++;
			}
		}
		return n;
	}

	/** The field of any kind nearest {@code p} within {@code reach} (blocks), or null. */
	public static @Nullable Field nearest(ServerLevel level, Vec3 p, double reach) {
		Field best = null;
		double bestD = Double.MAX_VALUE;
		for (Field f : ALL) {
			if (f.level != level || !f.alive()) {
				continue;
			}
			double d = f.kind == Kind.WARD_LINE ? distanceToLine(f, p) : Math.max(0.0, f.at.distanceTo(p) - f.radius);
			if (d <= reach && d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	private static double distanceToLine(Field f, Vec3 p) {
		double u = Math.clamp((p.x - f.at.x) * f.axis.x + (p.z - f.at.z) * f.axis.z, -f.radius, f.radius);
		double cx = f.at.x + f.axis.x * u, cz = f.at.z + f.axis.z * u;
		return Math.sqrt((p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz) + (p.y - f.at.y) * (p.y - f.at.y) * 0.25);
	}

	private static void tick(MinecraftServer server) {
		if (ALL.isEmpty()) {
			return;
		}
		for (Iterator<Field> it = ALL.iterator(); it.hasNext();) {
			Field f = it.next();
			long now = f.level.getGameTime();
			if (f.ended || now >= f.until) {
				it.remove();
				finish(f, now >= f.until && !f.ended);
				continue;
			}
			if (now % DRAW_EVERY == 0L) {
				draw(f, (now - f.made) % SHEET_EVERY == 0L);
			}
			switch (f.kind) {
				case WEB, GREASE, FOG, DARKNESS, SILENCE, THORNS -> BattleSpells.tick(f, now);
				case WARD_LINE, DOME -> CampSpells.tick(f, now);
				case MOONBEAM -> Moonbeam.tick(f, now);
				case TRUTH -> {
				}
			}
		}
	}

	/** A field's end: a puff, a sound; fire left it burning off or it simply ran out. */
	private static void finish(Field f, boolean ranOut) {
		f.level.playSound(null, f.at.x, f.at.y, f.at.z, ranOut ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6F, 1.5F);
		Fx.burst(f.level, f.at.x, f.at.y + 0.4, f.at.z, f.kind.colour, 1.0F, 10, Math.min(f.radius * 0.5, 2.0), 0.4, Math.min(f.radius * 0.5, 2.0));
	}

	/** What a field looks like, drawn again every {@link #DRAW_EVERY} ticks ({@code sheets}: the ground's sheets and circles too). */
	private static void draw(Field f, boolean sheets) {
		ServerLevel level = f.level;
		int c = f.kind.colour;
		float r = (float) f.radius;
		Vec3 floor = f.at.add(0.0, -f.lift + 0.04, 0.0);
		int sheet = SHEET_EVERY + 4;
		switch (f.kind) {
			case WEB -> {
				if (sheets) {
					Fx.pool(level, floor, r, c, sheet);
				}
				level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, net.minecraft.world.item.Items.COBWEB),
					f.at.x, f.at.y, f.at.z, 6, f.radius * 0.5, 0.8, f.radius * 0.5, 0.0);
				Fx.orb(level, f.at, r * 0.8F, c, DRAW_EVERY + 4);
			}
			case GREASE -> {
				if (sheets) {
					Fx.pool(level, floor, r, c, sheet);
				}
			}
			case FOG -> {
				Fx.orb(level, f.at, r, c, DRAW_EVERY + 4);
				level.sendParticles(Fx.miasma(c, 2.4F), f.at.x, f.at.y, f.at.z, 14, f.radius * 0.6, f.radius * 0.5, f.radius * 0.6, 0.0);
			}
			case DARKNESS -> {
				Fx.orb(level, f.at, r, c, DRAW_EVERY + 4);
				level.sendParticles(Fx.miasma(c, 3.0F), f.at.x, f.at.y, f.at.z, 24, f.radius * 0.7, f.radius * 0.6, f.radius * 0.7, 0.0);
			}
			case SILENCE -> {
				if (sheets) {
					Fx.sigil(level, floor, r, c, sheet);
				}
				Fx.orb(level, f.at, r, c, DRAW_EVERY + 4);
			}
			case THORNS -> {
				if (sheets) {
					Fx.pool(level, floor, r, c, sheet);
				}
				level.sendParticles(Fx.mote(c, 0.7F), f.at.x, f.at.y + 0.3, f.at.z, 4, f.radius * 0.6, 0.15, f.radius * 0.6, 0.0);
			}
			case WARD_LINE -> {
				for (int i = -4; i <= 4; i++) {
					double u = f.radius * i / 4.0;
					level.sendParticles(Fx.rune(c, 0.7F), f.at.x + f.axis.x * u, f.at.y + 0.3, f.at.z + f.axis.z * u, 1, 0.05, 0.1, 0.05, 0.0);
				}
			}
			case TRUTH -> {
				if (sheets) {
					Fx.sigil(level, floor, r, c, sheet);
				}
				level.sendParticles(Fx.mote(c, 0.6F), f.at.x, f.at.y + 0.2, f.at.z, 3, f.radius * 0.6, 0.1, f.radius * 0.6, 0.0);
			}
			case MOONBEAM -> {
				if (sheets) {
					Fx.pool(level, floor, r, c, sheet);
				}
				Fx.beam(level, floor, floor.add(0.0, 8.0, 0.0), r * 0.9F, c, DRAW_EVERY + 4);
			}
			case DOME -> {
				Fx.orb(level, f.at, r, c, DRAW_EVERY + 4);
				if (sheets) {
					Fx.sigil(level, floor, r, c, sheet);
				}
			}
		}
	}

	/** The block {@code p} stands over. */
	static BlockPos ground(Vec3 p) {
		return BlockPos.containing(p.x, p.y - 0.1, p.z);
	}

	/** Whether an entity is a body a field can act on: alive, not a spectator. */
	static boolean body(Entity e) {
		return e instanceof LivingEntity l && l.isAlive() && !l.isSpectator();
	}
}
