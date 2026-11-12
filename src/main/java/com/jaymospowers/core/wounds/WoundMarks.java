package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fractures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy's Cruor picks (2026-11-12, ideas only: Cruor is All Rights Reserved): a hit leaves a mark that says what made it,
 * where it landed. A blade leaves a cut, claws and teeth a raking of three, an arrow or a bolt a puncture, a blunt blow, a
 * fist, a fall or a slam a bruise, a blast a peppering of small cuts over several parts. Each mark sits on the part the
 * hit was routed to ({@link Wounds}) and on the side it came from, is synced to everyone and drawn into the skin by the
 * client's {@code WoundMarksLayer}, fades as it ages ({@link Kind#life}), and goes once the part has mended whole.
 * <p>
 * A fresh cut or puncture drips a few drops for a while ({@link #DRIP_TICKS}), more seldom as it closes; the deep cuts
 * that really bleed are {@link Bleeding}'s and already run on their own.
 */
public final class WoundMarks {
	/** The kinds of mark, and how long each lasts at most (ticks). */
	public enum Kind {
		CUT(12000), CLAW(12000), STAB(12000), BRUISE(7200), BLAST(9600);

		public final int life;

		Kind(int life) {
			this.life = life;
		}
	}

	/** Which side of a part a mark is on: the front, the body's right, its back, its left. */
	public static final int FRONT = 0, RIGHT = 1, BACK = 2, LEFT = 3;
	/** A body keeps this many marks at most (the oldest go first). */
	public static final int MOST = 10;
	/** A hit must take at least this much (health points) to mark. */
	public static final float MARK_FROM = 1.0F;
	/** A blast marks this many parts at most. */
	public static final int BLAST_PARTS = 3;
	/** A fresh cut drips for this long (ticks): at first about every {@link #DRIP_EVERY_FIRST} ticks, by the end every {@link #DRIP_EVERY_LAST}. */
	public static final int DRIP_TICKS = 600, DRIP_EVERY_FIRST = 30, DRIP_EVERY_LAST = 140;
	/** Marks on a part that has mended this whole (share) go, once they're this old (ticks). */
	public static final float MENDED = 0.995F;
	public static final int MENDED_AGE = 600;
	/** How often the server tidies marks (ticks). */
	static final int TIDY_EVERY = 20;

	/**
	 * One mark. {@code shape} packs, from the bottom: the part (its {@link BodyPart#ordinal()}, 4 bits), the kind (3), the
	 * side (2), where on that side across and down (8 and 8, 0 to 255) and its angle (8, 0 to 255 for a half turn), and 16
	 * bits of seed for its own small shape. {@code laid} is the game time it was made.
	 */
	public record Mark(long shape, long laid) {
		public static final StreamCodec<io.netty.buffer.ByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Mark::shape, ByteBufCodecs.VAR_LONG, Mark::laid, Mark::new);

		static Mark of(BodyPart part, Kind kind, int side, int across, int down, int angle, int seed, long laid) {
			long s = part.ordinal() & 0xF | (long) (kind.ordinal() & 0x7) << 4 | (long) (side & 0x3) << 7 | (long) (across & 0xFF) << 9
				| (long) (down & 0xFF) << 17 | (long) (angle & 0xFF) << 25 | (long) (seed & 0xFFFF) << 33;
			return new Mark(s, laid);
		}

		public BodyPart part() {
			int i = (int) (this.shape & 0xF);
			BodyPart[] all = BodyPart.values();
			return i < all.length ? all[i] : BodyPart.BODY;
		}

		public Kind kind() {
			int i = (int) (this.shape >> 4 & 0x7);
			Kind[] all = Kind.values();
			return i < all.length ? all[i] : Kind.BRUISE;
		}

		public int side() {
			return (int) (this.shape >> 7 & 0x3);
		}

		/** Across and down the side, 0 to 1. */
		public float across() {
			return (this.shape >> 9 & 0xFF) / 255.0F;
		}

		public float down() {
			return (this.shape >> 17 & 0xFF) / 255.0F;
		}

		/** Its angle (radians, 0 to pi). */
		public float angle() {
			return (this.shape >> 25 & 0xFF) / 255.0F * Mth.PI;
		}

		public int seed() {
			return (int) (this.shape >> 33 & 0xFFFF);
		}

		/** How far through its life it is at {@code now} (0 fresh, 1 gone). */
		public float age(long now) {
			return Mth.clamp((now - this.laid) / (float) this.kind().life, 0.0F, 1.0F);
		}
	}

	/** On every marked body, synced to everyone near it. Not saved: marks are gone after a restart, as blood coats are. */
	public static final AttachmentType<List<Mark>> MARKS = AttachmentRegistry.create(JaymosPowers.id("wound_marks"),
		builder -> builder.syncWith(Mark.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

	/** The bodies carrying marks (to tidy and drip). */
	private static final Map<LivingEntity, Boolean> MARKED = Collections.synchronizedMap(new WeakHashMap<>());
	/** When each marked body next drips. */
	private static final Map<LivingEntity, Long> NEXT_DRIP = new WeakHashMap<>();

	private WoundMarks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WoundMarks::tick);
	}

	public static List<Mark> marks(Entity body) {
		List<Mark> marks = body.getAttached(MARKS);
		return marks == null ? List.of() : marks;
	}

	/** From {@link Wounds}: {@code victim} took {@code loss} from {@code source}, routed to {@code landed}. */
	static void onHit(LivingEntity victim, BodyPlan plan, DamageSource source, @Nullable BodyPart landed, float loss) {
		if (loss < MARK_FROM || !(victim.level() instanceof ServerLevel level) || BladeBlood.color(victim) == BladeBlood.NO_BLOOD) {
			return;
		}
		Kind kind = kind(source);
		if (kind == null) {
			return;
		}
		RandomSource random = victim.getRandom();
		long now = level.getGameTime();
		int side = side(victim, source.getSourcePosition(), random);
		List<Mark> marks = new ArrayList<>(marks(victim));
		if (kind == Kind.BLAST) {
			List<BodyPart> parts = new ArrayList<>();
			for (int i = 0; i < plan.size(); i++) {
				parts.add(plan.part(i));
			}
			Collections.shuffle(parts, new java.util.Random(random.nextLong()));
			for (int i = 0; i < Math.min(BLAST_PARTS, parts.size()); i++) {
				marks.add(make(parts.get(i), kind, side, random, now));
			}
		} else {
			BodyPart part = landed != null && plan.index(landed) >= 0 ? landed : BodyPart.BODY;
			marks.add(make(part, kind, side, random, now));
		}
		while (marks.size() > MOST) {
			marks.remove(0);
		}
		victim.setAttached(MARKS, List.copyOf(marks));
		MARKED.put(victim, Boolean.TRUE);
	}

	private static Mark make(BodyPart part, Kind kind, int side, RandomSource random, long now) {
		// (kept off the very edges of the side, so the mark isn't cut in half by a seam)
		int across = 40 + random.nextInt(176), down = 30 + random.nextInt(196);
		return Mark.of(part, kind, side, across, down, random.nextInt(256), random.nextInt(65536), now);
	}

	/** What a hit from {@code source} marks, or null for one that leaves none (fire, magic, drowning, hunger). */
	static @Nullable Kind kind(DamageSource source) {
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return Kind.BLAST;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return Kind.STAB;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.FALLING_BLOCK)
			|| source.is(DamageTypes.FALLING_ANVIL)) {
			return Kind.BRUISE;
		}
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.STALAGMITE)) {
			return Kind.STAB;
		}
		Entity by = source.getEntity();
		if (!(by instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
			return null;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && !weapon.isEmpty()) {
			if (Dismember.blade(weapon)) {
				return Kind.CUT;
			}
			return Kind.BRUISE;
		}
		// bare: a person's fists bruise; a beast's claws and teeth rake
		return attacker instanceof Player || Fractures.blunt(attacker.getMainHandItem()) || humanoid(attacker) ? Kind.BRUISE : Kind.CLAW;
	}

	private static boolean humanoid(LivingEntity body) {
		return BodyPlan.of(body) == BodyPlan.HUMANOID;
	}

	/** Which side of {@code victim} a hit from {@code from} lands on. */
	static int side(LivingEntity victim, @Nullable Vec3 from, RandomSource random) {
		if (from == null) {
			return random.nextInt(4);
		}
		double dx = from.x - victim.getX(), dz = from.z - victim.getZ();
		if (dx * dx + dz * dz < 1.0E-4) {
			return random.nextInt(4);
		}
		// the way the hit came from, against the way the body faces
		float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float rel = Mth.wrapDegrees(toward - victim.yBodyRot);
		if (Math.abs(rel) <= 45.0F) {
			return FRONT;
		}
		if (Math.abs(rel) >= 135.0F) {
			return BACK;
		}
		return rel > 0.0F ? LEFT : RIGHT;
	}

	private static void tick(MinecraftServer server) {
		if (MARKED.isEmpty() || server.getTickCount() % 2 != 0) {
			return;
		}
		boolean tidy = server.getTickCount() % TIDY_EVERY == 0;
		List<LivingEntity> bodies;
		synchronized (MARKED) {
			bodies = new ArrayList<>(MARKED.keySet());
		}
		for (LivingEntity body : bodies) {
			if (body.isRemoved() || !(body.level() instanceof ServerLevel level)) {
				MARKED.remove(body);
				continue;
			}
			List<Mark> marks = marks(body);
			if (marks.isEmpty()) {
				MARKED.remove(body);
				continue;
			}
			long now = level.getGameTime();
			if (tidy) {
				List<Mark> kept = new ArrayList<>(marks.size());
				for (Mark mark : marks) {
					boolean old = now - mark.laid() >= mark.kind().life;
					boolean mended = now - mark.laid() >= MENDED_AGE && body.isAlive() && Wounds.plan(body) != null
						&& Wounds.plan(body).index(mark.part()) >= 0 && Wounds.fraction(body, mark.part()) >= MENDED;
					if (!old && !mended) {
						kept.add(mark);
					}
				}
				if (kept.size() != marks.size()) {
					if (kept.isEmpty()) {
						body.removeAttached(MARKS);
						MARKED.remove(body);
						continue;
					}
					body.setAttached(MARKS, List.copyOf(kept));
					marks = kept;
				}
			}
			if (body.isAlive() && !Downed.isDowned(body)) {
				drip(level, body, marks, now);
			}
		}
	}

	/** The freshest open cut or puncture not already bleeding properly drips, more seldom as it closes; a bandaged part never does. */
	private static void drip(ServerLevel level, LivingEntity body, List<Mark> marks, long now) {
		Long next = NEXT_DRIP.get(body);
		if (next != null && now < next) {
			return;
		}
		Mark fresh = null;
		for (Mark mark : marks) {
			Kind kind = mark.kind();
			if ((kind == Kind.CUT || kind == Kind.CLAW || kind == Kind.STAB) && now - mark.laid() < DRIP_TICKS
				&& ((Bleeding.bleeding(body) | Bleeding.bandaged(body) | Bleeding.wrapped(body)) & Breaks.bit(mark.part())) == 0
				&& (fresh == null || mark.laid() > fresh.laid())) {
				fresh = mark;
			}
		}
		if (fresh == null) {
			NEXT_DRIP.remove(body);
			return;
		}
		float t = (now - fresh.laid()) / (float) DRIP_TICKS;
		int every = Math.round(Mth.lerp(t, DRIP_EVERY_FIRST, DRIP_EVERY_LAST));
		NEXT_DRIP.put(body, now + every + body.getRandom().nextInt(Math.max(1, every / 2)));
		if (next == null) {
			// (the hit itself already sprayed: the first drip waits its turn)
			return;
		}
		double y = switch (fresh.part()) {
			case HEAD -> 0.85;
			case BODY, LEFT_ARM, RIGHT_ARM, WINGS -> 0.6;
			case LEFT_FOOT, RIGHT_FOOT -> 0.08;
			default -> 0.3;
		};
		BladeBlood.drip(level, body, y, false);
	}
}
