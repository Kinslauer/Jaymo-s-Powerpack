package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.467) The strong strike every spirit has. Andy: "each spirit should have a strong strike attack that whips the fist
 * straight forward within your view, and when one of these punches clashes with another you get a barrage style clash". The
 * spirit's fist shoots straight out along your aim ({@link Poses#PUNCH}: the arm stretches the whole way, as a far hand
 * does) and hits the first thing in line within its reach, each spirit in its own way ({@link #DAMAGE}...): Grimsby a quick
 * hard jab, Varro a slow heavy blow that throws, Ambrose even, Thessaly a long light flick, the Quiet One by its size this
 * summon. A punch into the air still goes out (and costs its mana).
 * <p>
 * Two punches meeting: a punch at another keeper whose spirit is out lands {@link #TRAVEL} ticks late; if they punch back at
 * you within {@link SpiritBarrage#MEET_WINDOW} ticks, the fists meet and neither lands, and a barrage clash begins
 * ({@link SpiritBarrage}). A folk keeper (court wizard, hermit) punched with its spirit out answers in kind one time in two
 * ({@link #FOLK_ANSWER}).
 */
public final class SpiritPunch implements Move {
	public static final SpiritPunch MOVE = new SpiritPunch();

	/** Per kind (Grimsby, Varro, Ambrose, Thessaly, the Quiet One): the blow's hurt, its cooldown (ticks), reach (blocks), knockback. */
	static final float[] DAMAGE = {6.5F, 9.0F, 6.0F, 4.5F, 6.0F};
	static final int[] COOLDOWN = {16, 26, 18, 10, 16};
	static final double[] REACH = {7.0, 6.0, 7.0, 10.0, 7.0};
	static final double[] KNOCK = {0.9, 1.6, 1.0, 0.6, 1.0};
	/** Mana a punch costs; the screen shake on a hit. */
	static final float COST = 5.0F, SHAKE = 0.25F;
	/** Ticks a punch at a keeper with their spirit out takes to land (theirs may meet it first). */
	static final int TRAVEL = 3;
	/** The chance a folk keeper answers a punch with its own (a barrage), and how well it keeps the beat (0-1, by its level). */
	static final float FOLK_ANSWER = 0.5F, FOLK_SKILL = 0.3F, FOLK_SKILL_PER_LEVEL = 0.03F, FOLK_SKILL_MAX = 0.85F;

	/** A punch on its way: who threw it, at whom, when it was thrown, when it lands, and how hard. */
	private record Pending(UUID from, LivingEntity target, long thrown, long lands, float damage, double knock, Vec3 look) {
	}

	/** Punches thrown at keepers, by thrower: the last one each. */
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private SpiritPunch() {
	}

	@Override
	public String id() {
		return "punch";
	}

	@Override
	public float cost() {
		return COST;
	}

	@Override
	public int cooldown() {
		return COOLDOWN[0];
	}

	@Override
	public int level() {
		return 1;
	}

	@Override
	public int pose() {
		return Poses.PUNCH;
	}

	private static int kind(SpiritState state) {
		return Math.clamp(state.kind(), 0, DAMAGE.length - 1);
	}

	/** The Quiet One's size this summon (1 for the others). */
	private static float size(SpiritState state) {
		return state.kind() == Spirits.QUIET ? Math.max(0.5F, state.scale() / 100.0F) : 1.0F;
	}

	@Override
	public boolean cast(ServerPlayer player, SpiritState state, int charge) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		int kind = kind(state);
		float size = size(state);
		double reach = REACH[kind] * Math.min(1.5F, size);
		CastHints.setCooldown(player, COOLDOWN[kind]);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(reach));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult found = ProjectileUtil.getEntityHitResult(level, player, eye, end, box,
			e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && !HandMoves.ally(player, l), 0.3F);
		LivingEntity target = found != null && found.getEntity() instanceof LivingEntity l ? l : null;
		Vec3 at = target != null ? found.getLocation() : end;
		CastHints.setReach(player, Math.max(1.0, at.distanceTo(eye)));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F,
			1.5F - 0.1F * kind);
		if (target == null) {
			return true;
		}
		float damage = DAMAGE[kind] * Spirits.damageScale(state) * size;
		double knock = KNOCK[kind] * Math.min(1.5F, size);
		long now = level.getGameTime();
		if (Spirits.out(target) && !SpiritBarrage.busy(target) && !SpiritClash.inClash(player)) {
			if (target instanceof ServerPlayer other) {
				// theirs at you, still on its way or just landed: the fists meet
				Pending theirs = PENDING.get(other.getUUID());
				if (theirs != null && theirs.target() == player && now - theirs.thrown() <= SpiritBarrage.MEET_WINDOW
					&& SpiritBarrage.meet(other, player)) {
					PENDING.remove(other.getUUID());
					return true;
				}
				PENDING.put(player.getUUID(), new Pending(player.getUUID(), other, now, now + TRAVEL, damage, knock, look));
				return true;
			}
			if (target instanceof Folk folk && folk.getRandom().nextFloat() < FOLK_ANSWER) {
				SpiritState theirs = Spirits.state(folk);
				float skill = Math.min(FOLK_SKILL_MAX, FOLK_SKILL + FOLK_SKILL_PER_LEVEL * (theirs == null ? 1 : theirs.level()));
				if (SpiritBarrage.start(player, folk, skill, null)) {
					return true;
				}
			}
		}
		land(level, player, target, damage, knock, look);
		return true;
	}

	/** The blow lands on {@code target}: the hurt, the throw along {@code look}, the burst, the shake. */
	private static void land(ServerLevel level, ServerPlayer player, LivingEntity target, float damage, double knock, Vec3 look) {
		if (!target.isAlive()) {
			return;
		}
		if (SpiritFoes.strike(level, player, target, damage)) {
			target.knockback(knock, -look.x, -look.z, SpiritFoes.source(level, player), 0.0F);
			target.syncVelocity = true;
		}
		Vec3 hit = target.getBoundingBox().getCenter();
		Mana.sendSeen(level, Mana.MOTE, hit.x, hit.y, hit.z, 12, 0.3, 0.3, 0.3, 0.12);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, hit.x, hit.y, hit.z, 6, 0.2, 0.2, 0.2, 0.3);
		level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
		PowersCompat.shake(player, SHAKE);
		if (target instanceof ServerPlayer struck) {
			PowersCompat.shake(struck, SHAKE * 2.0F);
		}
	}

	/** Every tick ({@link SpiritBarrage}'s): the punches thrown at keepers that are due land, unless a barrage took them. */
	static void tick(long now) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<UUID> due = new ArrayList<>();
		for (Map.Entry<UUID, Pending> e : PENDING.entrySet()) {
			if (now >= e.getValue().lands()) {
				due.add(e.getKey());
			}
		}
		for (UUID id : due) {
			Pending p = PENDING.get(id);
			if (now - p.thrown() > SpiritBarrage.MEET_WINDOW) {
				PENDING.remove(id);
			}
			if (now != p.lands()) {
				continue;
			}
			LivingEntity target = p.target();
			if (!(target.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof ServerPlayer player)) {
				continue;
			}
			if (!SpiritBarrage.busy(player) && !SpiritBarrage.busy(target) && player.distanceTo(target) <= REACH[3] + 2.0) {
				land(level, player, target, p.damage(), p.knock(), p.look());
			}
		}
	}

	/** {@code id} left the game. */
	static void forget(UUID id) {
		PENDING.remove(id);
	}

	static void clear() {
		PENDING.clear();
	}
}
