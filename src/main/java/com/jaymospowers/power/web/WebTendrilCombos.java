package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Debris;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tendril combos: attack (left click) while Living Armor's tendrils hold things and the attack becomes a combo.
 * <ul>
 * <li>A creature in each tendril: a <b>clap</b>, both slammed into each other in front of you, then dropped to bounce apart.</li>
 * <li>Debris in one, and a creature in the other or under your crosshair (within {@link #CRUSH_REACH}): a <b>crush</b>,
 * the debris brought down on it and spent.</li>
 * <li>Debris in both: a <b>shatter</b>, the two smashed together and burst into a cone of debris ahead.</li>
 * </ul>
 * Each is heavy: hauled out (until {@link #REAR_TICKS}), slammed in (impact at {@link #HIT_TICKS}), then a slow reset (to
 * {@link #TICKS}) before the next. The server runs it and syncs it in {@link WebTendrils#COMBO}; everyone sees the held
 * things swung (steered in {@link WebTendrils#tick}) and the tendrils perform it. Soft debris hits for no more than
 * {@link BlockCluster#SOFT_HIT_DAMAGE}, as every soft block does. You're never hurt by your own combo.
 */
public final class WebTendrilCombos {
	public static final int CLAP = 0, CRUSH = 1, SHATTER = 2;
	/** Timing (ticks from the click): hauled out until {@code REAR_TICKS}, slammed in to the impact at {@code HIT_TICKS}, reset until {@code TICKS}. */
	public static final int REAR_TICKS = 12, HIT_TICKS = 18, TICKS = 50;
	/** How far your crosshair target may be for a crush (blocks, plus its half width), and the slack the server allows. */
	public static final double CRUSH_REACH = 6.0;
	static final double REACH_SLACK = 2.0, FRONT_DOT = 0.3;
	/** How hard the held things are steered through a combo (share of the gap a tick) and their top speed (blocks/tick). */
	static final double STEER = 0.8, SPEED = 2.8;

	/** Clap: they meet this far ahead and below the eyes, hauled out this far to each side (and up, and back) first. */
	static final double CLAP_FORWARD = 2.3, CLAP_DROP = 0.5, CLAP_WIDE = 2.6, CLAP_REAR_UP = 0.6, CLAP_REAR_BACK = 0.4;
	static final float CLAP_DAMAGE = 12.0F;
	/** Then they bounce apart: this hard outward, this much on ahead, and this much up. */
	static final double CLAP_BOUNCE = 0.75, CLAP_BOUNCE_FORWARD = 0.2, CLAP_LIFT = 0.4;

	/** Crush: the debris reared this high above your head (and this far back), then over the target this high, then down onto it. */
	static final double CRUSH_REAR_UP = 2.8, CRUSH_REAR_BACK = 0.3, CRUSH_OVER = 2.4;
	/** A held creature is pinned low in front for it: this far ahead, this far below the eyes. */
	static final double CRUSH_PIN_FORWARD = 2.2, CRUSH_PIN_DROP = 1.3;
	static final float CRUSH_DAMAGE = 15.0F;
	/** Driven down (if airborne) and shoved a little away; staggered (Slowness) a moment. */
	static final double CRUSH_DOWN = -0.9, CRUSH_PUSH = 0.25, CRUSH_LIFT = 0.1;
	static final int CRUSH_STAGGER_TICKS = 30, CRUSH_STAGGER_LEVEL = 1;

	/** Shatter: they meet this far ahead (and up), hauled out this far to each side and up first. */
	static final double SHATTER_FORWARD = 2.4, SHATTER_UP = 0.1, SHATTER_WIDE = 2.2, SHATTER_REAR_UP = 1.4;
	/** The burst: a cone this long and this wide (half-angle, degrees) from where they met, along your look. */
	public static final double SHATTER_RANGE = 6.0, SHATTER_HALF_ANGLE = 30.0;
	/** Damage at the near end (falling off by {@code SHATTER_FALLOFF} at the far end), and the knock along the cone. */
	static final float SHATTER_DAMAGE = 10.0F, SHATTER_FALLOFF = 0.4F;
	static final double SHATTER_KNOCK = 0.9, SHATTER_LIFT = 0.35;
	/** Puffs of debris sprayed through the cone. */
	static final int SHATTER_SPRAY = 36;

	/** Damage scales with the debris' size: × ({@code SIZE_BASE} + {@code SIZE_PER_PIECE} a block), clamped. */
	static final float SIZE_BASE = 0.6F, SIZE_PER_PIECE = 0.1F, SIZE_MIN = 0.7F, SIZE_MAX = 1.5F, SHATTER_SIZE_MAX = 1.8F;
	/** A spent falling block drops as an item this often (as a thrown chunk's blocks do). */
	static final float SPENT_DROP_CHANCE = 0.3F;
	/** Screen shake: yours at the impact, and a struck player's. */
	static final float SHAKE = 1.8F, SHATTER_SHAKE = 2.2F, TARGET_SHAKE = 2.5F;

	/** Client → server: the attack key while the tendrils hold things ({@code target}: the creature under the crosshair, or -1). */
	public record Attack(int target) implements CustomPacketPayload {
		public static final Type<Attack> TYPE = new Type<>(JaymosPowers.id("web_tendril_combo_attack"));
		public static final StreamCodec<ByteBuf, Attack> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Attack::target, Attack::new);

		@Override
		public Type<Attack> type() {
			return TYPE;
		}
	}

	private WebTendrilCombos() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Attack.TYPE, Attack.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Attack.TYPE, (payload, ctx) -> attack(ctx.player(), payload.target()));
	}

	/** Debris: a torn-out chunk or a falling block. */
	public static boolean debris(@Nullable Entity entity) {
		return entity instanceof BlockCluster || entity instanceof FallingBlockEntity;
	}

	private static boolean creature(@Nullable Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive();
	}

	public static boolean active(Player player) {
		return player.getAttached(WebTendrils.COMBO) != null;
	}

	/**
	 * Which combo an attack makes now (-1: none), from what the tendrils hold and the creature under the crosshair (may
	 * be null). Works on both sides.
	 */
	public static int kind(Player player, @Nullable Entity crosshair) {
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		if (grips == null) {
			return -1;
		}
		Entity main = grips.right() < 0 ? null : player.level().getEntity(grips.right());
		Entity off = grips.left() < 0 ? null : player.level().getEntity(grips.left());
		if (debris(main) && debris(off)) {
			return SHATTER;
		}
		if (creature(main) && creature(off)) {
			return CLAP;
		}
		if (debris(main) && creature(off) || debris(off) && creature(main) || (debris(main) || debris(off)) && creature(crosshair)) {
			return CRUSH;
		}
		return -1;
	}

	/** The creature under the crosshair within {@link #CRUSH_REACH} (not held, not you), seen past held things. Both sides. */
	public static @Nullable LivingEntity crosshairTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(CRUSH_REACH));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
		AABB box = player.getBoundingBox().expandTowards(look.scale(CRUSH_REACH)).inflate(1.0);
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player && e.getId() != grips.right() && e.getId() != grips.left(),
			eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** The attack key with the tendrils full: a combo if one fits, else the plain swing of a held chunk. */
	private static void attack(ServerPlayer player, int targetId) {
		if (!WebSymbiote.armored(player) || !player.isAlive() || player.isSpectator() || active(player)) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity crosshair = null;
		if (targetId >= 0) {
			crosshair = level.getEntity(targetId) instanceof LivingEntity living && validTarget(player, living) ? living : crosshairTarget(player);
		}
		int kind = kind(player, crosshair);
		if (kind < 0) {
			WebTendrils.smash(player, WebNet.MAIN);
			return;
		}
		if (!WebTendrils.reached(player, WebNet.MAIN) || !WebTendrils.reached(player, WebNet.OFF)) {
			return;
		}
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		Entity main = grips.right() < 0 ? null : level.getEntity(grips.right());
		Entity off = grips.left() < 0 ? null : level.getEntity(grips.left());
		Entity first, second;
		if (kind == CRUSH) {
			first = debris(main) ? main : off;
			Entity other = first == main ? off : main;
			second = creature(other) ? other : crosshair;
		} else {
			first = main;
			second = off;
		}
		if (first == null || second == null) {
			return;
		}
		long now = level.getGameTime();
		player.setAttached(WebTendrils.COMBO, new WebTendrils.Combo(kind, now, first.getId(), second.getId()));
		// a heavy strike swinging the same debris gives way to it
		WebSymbiote.Strike strike = player.getAttached(WebSymbiote.STRIKE);
		if (strike != null && strike.heavy()) {
			player.removeAttached(WebSymbiote.STRIKE);
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		// the weight of it, hauled out
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.2F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.4F);
	}

	private static boolean validTarget(ServerPlayer player, LivingEntity target) {
		if (target == player || !target.isAlive() || target.isSpectator() || target.level() != player.level()) {
			return false;
		}
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		double d = to.length();
		return d <= CRUSH_REACH + target.getBbWidth() * 0.5 + REACH_SLACK && (d < 1.0E-3 || to.scale(1.0 / d).dot(player.getLookAngle()) >= FRONT_DOT);
	}

	/** Your look, flattened (never zero). */
	private static Vec3 ahead(Player player) {
		Vec3 dir = player.getLookAngle().horizontal();
		return dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : dir.normalize();
	}

	/** Eases 0-1. */
	private static double smooth(double x) {
		x = Mth.clamp(x, 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}

	/** Where a combo puts what tendril {@code hand} holds (null: not in a combo, or done with it). */
	static @Nullable Vec3 point(ServerPlayer player, Entity held, int hand, long now) {
		WebTendrils.Combo combo = player.getAttached(WebTendrils.COMBO);
		if (combo == null || held.getId() != combo.first() && held.getId() != combo.second()) {
			return null;
		}
		long t = now - combo.start();
		if (t >= HIT_TICKS) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 dir = ahead(player);
		Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
		double rear = smooth(t / (double)REAR_TICKS);
		double k = t < REAR_TICKS ? 0.0 : (t - REAR_TICKS + 1) / (double)(HIT_TICKS - REAR_TICKS);
		k = Mth.clamp(k, 0.0, 1.0);
		double sign = hand == WebNet.OFF ? -1.0 : 1.0;
		Vec3 here = held.getBoundingBox().getCenter();
		switch (combo.kind()) {
			case CLAP -> {
				Vec3 meet = eye.add(dir.scale(CLAP_FORWARD)).subtract(0.0, CLAP_DROP, 0.0);
				Vec3 wide = meet.add(right.scale(sign * CLAP_WIDE)).add(0.0, CLAP_REAR_UP, 0.0).subtract(dir.scale(CLAP_REAR_BACK));
				Vec3 contact = meet.add(right.scale(sign * (held.getBbWidth() * 0.5 + 0.05)));
				// hauled out wide, then slammed together, faster and faster
				return t < REAR_TICKS ? here.lerp(wide, rear) : wide.lerp(contact, k * k);
			}
			case SHATTER -> {
				Vec3 meet = eye.add(dir.scale(SHATTER_FORWARD)).add(0.0, SHATTER_UP, 0.0);
				Vec3 wide = meet.add(right.scale(sign * SHATTER_WIDE)).add(0.0, SHATTER_REAR_UP, 0.0);
				Vec3 contact = meet.add(right.scale(sign * (held.getBbWidth() * 0.5)));
				return t < REAR_TICKS ? here.lerp(wide, rear) : wide.lerp(contact, k * k);
			}
			default -> {
				if (held.getId() == combo.second()) {
					// the held one is pinned low in front, to take it
					return eye.add(dir.scale(CRUSH_PIN_FORWARD + held.getBbWidth() * 0.5)).subtract(0.0, CRUSH_PIN_DROP, 0.0);
				}
				Entity victim = player.level().getEntity(combo.second());
				Vec3 up = eye.add(0.0, CRUSH_REAR_UP, 0.0).subtract(dir.scale(CRUSH_REAR_BACK));
				if (victim == null) {
					return up;
				}
				AABB box = victim.getBoundingBox();
				Vec3 top = new Vec3((box.minX + box.maxX) * 0.5, box.maxY + held.getBbHeight() * 0.5, (box.minZ + box.maxZ) * 0.5);
				Vec3 over = top.add(0.0, CRUSH_OVER, 0.0);
				if (t < REAR_TICKS) {
					return here.lerp(up, rear);
				}
				// over the top, then straight down onto it
				return k < 0.5 ? up.lerp(over, k * 2.0) : over.lerp(top, (k - 0.5) * 2.0);
			}
		}
	}

	/** Every tick, per web slinger (from {@link WebTendrils#tick}): the impact, and the end of the reset. */
	static void tick(ServerPlayer player) {
		WebTendrils.Combo combo = player.getAttached(WebTendrils.COMBO);
		if (combo == null) {
			return;
		}
		long t = player.level().getGameTime() - combo.start();
		if (!player.isAlive() || t < 0 || t >= TICKS) {
			player.removeAttached(WebTendrils.COMBO);
			return;
		}
		if (t == REAR_TICKS) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.45F);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
		if (t == HIT_TICKS) {
			switch (combo.kind()) {
				case CLAP -> clap(player, combo);
				case CRUSH -> crush(player, combo);
				default -> shatter(player, combo);
			}
		}
	}

	/** Which tendril holds this (-1: none). */
	private static int handOf(Player player, Entity entity) {
		WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
		return grips.right() == entity.getId() ? WebNet.MAIN : grips.left() == entity.getId() ? WebNet.OFF : -1;
	}

	private static void letGo(ServerPlayer player, Entity entity) {
		int hand = handOf(player, entity);
		if (hand >= 0) {
			WebTendrils.release(player, hand, false);
		}
	}

	private static void knock(Entity entity, Vec3 v) {
		entity.setDeltaMovement(v);
		entity.needsSync = true;
		entity.syncVelocity = true;
		if (entity instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(v));
			ServerPlayNetworking.send(other, new WebNet.Shake(TARGET_SHAKE));
		}
	}

	/** Two creatures slammed into each other: both hurt, dropped, and bounced apart. */
	private static void clap(ServerPlayer player, WebTendrils.Combo combo) {
		ServerLevel level = player.level();
		Entity a = level.getEntity(combo.first());
		Entity b = level.getEntity(combo.second());
		boolean ready = a instanceof LivingEntity && b instanceof LivingEntity && handOf(player, a) >= 0 && handOf(player, b) >= 0;
		if (!ready) {
			// one slipped free: the other's just let go
			for (Entity e : new Entity[] {a, b}) {
				if (e != null) {
					letGo(player, e);
				}
			}
			return;
		}
		Vec3 meet = a.getBoundingBox().getCenter().lerp(b.getBoundingBox().getCenter(), 0.5);
		Vec3 dir = ahead(player);
		Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
		for (Entity e : new Entity[] {a, b}) {
			LivingEntity living = (LivingEntity)e;
			double sign = handOf(player, e) == WebNet.OFF ? -1.0 : 1.0;
			letGo(player, e);
			living.hurtServer(level, level.damageSources().playerAttack(player), CLAP_DAMAGE);
			Vec3 out = living.getBoundingBox().getCenter().subtract(meet).horizontal();
			out = out.lengthSqr() < 1.0E-4 ? right.scale(sign) : out.normalize();
			knock(living, out.scale(CLAP_BOUNCE).add(dir.scale(CLAP_BOUNCE_FORWARD)).add(0.0, CLAP_LIFT, 0.0));
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, meet.x, meet.y, meet.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, meet.x, meet.y, meet.z, 24, 0.4, 0.4, 0.4, 0.3);
		level.sendParticles(WebSymbiote.dust(1.6F), meet.x, meet.y, meet.z, 20, 0.5, 0.5, 0.5, 0.0);
		groundDust(level, meet);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.3F, 0.5F);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3F, 0.5F);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.7F);
		ServerPlayNetworking.send(player, new WebNet.Shake(SHAKE));
	}

	/** Debris brought down on a creature: it's hurt (and driven down), and the debris collapses. */
	private static void crush(ServerPlayer player, WebTendrils.Combo combo) {
		ServerLevel level = player.level();
		Entity debris = level.getEntity(combo.first());
		Entity victim = level.getEntity(combo.second());
		if (!debris(debris) || handOf(player, debris) < 0) {
			if (victim != null && handOf(player, victim) >= 0) {
				letGo(player, victim);
			}
			return;
		}
		letGo(player, debris);
		boolean inReach = victim instanceof LivingEntity living && living.isAlive()
			&& (handOf(player, living) >= 0 || validTarget(player, living));
		if (!inReach) {
			// it got away: the debris comes down where it is
			spend(level, debris, debris.getBoundingBox().getCenter());
			if (victim != null && handOf(player, victim) >= 0) {
				letGo(player, victim);
			}
			ServerPlayNetworking.send(player, new WebNet.Shake(SHAKE * 0.5F));
			return;
		}
		LivingEntity living = (LivingEntity)victim;
		letGo(player, living);
		int pieces = debris instanceof BlockCluster chunk ? chunk.pieces().size() : 1;
		float damage = soft(debris) ? BlockCluster.SOFT_HIT_DAMAGE
			: CRUSH_DAMAGE * Mth.clamp(SIZE_BASE + SIZE_PER_PIECE * pieces, SIZE_MIN, SIZE_MAX);
		living.hurtServer(level, level.damageSources().playerAttack(player), damage);
		Vec3 away = living.position().subtract(player.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? ahead(player) : away.normalize();
		knock(living, away.scale(CRUSH_PUSH).add(0.0, living.onGround() ? CRUSH_LIFT : CRUSH_DOWN, 0.0));
		if (!soft(debris)) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRUSH_STAGGER_TICKS, CRUSH_STAGGER_LEVEL), player);
		}
		AABB box = living.getBoundingBox();
		Vec3 top = new Vec3((box.minX + box.maxX) * 0.5, box.maxY, (box.minZ + box.maxZ) * 0.5);
		spend(level, debris, top);
		groundDust(level, living.position());
		level.sendParticles(ParticleTypes.CRIT, top.x, top.y, top.z, 16, 0.4, 0.3, 0.4, 0.2);
		level.sendParticles(ParticleTypes.EXPLOSION, top.x, top.y, top.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.5F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.5F);
		ServerPlayNetworking.send(player, new WebNet.Shake(SHAKE));
	}

	/** Two clumps smashed together: they burst into a cone of debris ahead, hurting everything in it. */
	private static void shatter(ServerPlayer player, WebTendrils.Combo combo) {
		ServerLevel level = player.level();
		Entity a = level.getEntity(combo.first());
		Entity b = level.getEntity(combo.second());
		List<Entity> clumps = new ArrayList<>();
		for (Entity e : new Entity[] {a, b}) {
			if (debris(e) && handOf(player, e) >= 0) {
				clumps.add(e);
			} else if (e != null && handOf(player, e) >= 0) {
				letGo(player, e);
			}
		}
		if (clumps.isEmpty()) {
			return;
		}
		Vec3 meet = clumps.size() == 2 ? clumps.get(0).getBoundingBox().getCenter().lerp(clumps.get(1).getBoundingBox().getCenter(), 0.5)
			: clumps.get(0).getBoundingBox().getCenter();
		int pieces = 0, hard = 0;
		List<BlockState> states = new ArrayList<>();
		for (Entity e : clumps) {
			if (e instanceof BlockCluster chunk) {
				for (BlockCluster.Piece piece : chunk.pieces()) {
					pieces++;
					hard += BlockCluster.soft(piece.blockState()) ? 0 : 1;
					states.add(piece.blockState());
				}
			} else if (e instanceof FallingBlockEntity falling) {
				pieces++;
				hard += BlockCluster.soft(falling.getBlockState()) ? 0 : 1;
				states.add(falling.getBlockState());
			}
		}
		for (Entity e : clumps) {
			letGo(player, e);
			spend(level, e, meet);
		}
		if (clumps.size() < 2 || pieces == 0) {
			// only one left to smash: it just breaks
			ServerPlayNetworking.send(player, new WebNet.Shake(SHAKE * 0.5F));
			return;
		}
		// soft blocks hit for no more than any soft block; a mix by its share of hard ones
		float hardShare = hard / (float)pieces;
		float full = SHATTER_DAMAGE * Mth.clamp(SIZE_BASE + SIZE_PER_PIECE * pieces, SIZE_MIN, SHATTER_SIZE_MAX);
		Vec3 look = player.getLookAngle();
		double cos = Math.cos(Math.toRadians(SHATTER_HALF_ANGLE));
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(meet, meet).inflate(SHATTER_RANGE + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 center = living.getBoundingBox().getCenter();
			Vec3 to = center.subtract(meet);
			double d = to.length();
			if (d > SHATTER_RANGE + living.getBbWidth() * 0.5 || d > 1.0 && to.scale(1.0 / d).dot(look) < cos) {
				continue;
			}
			if (level.clip(new ClipContext(meet, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
				continue;
			}
			float near = 1.0F - SHATTER_FALLOFF * (float)Mth.clamp(d / SHATTER_RANGE, 0.0, 1.0);
			float damage = Math.max(BlockCluster.SOFT_HIT_DAMAGE, full * near * hardShare);
			living.hurtServer(level, level.damageSources().playerAttack(player), damage);
			Vec3 out = to.horizontal();
			out = out.lengthSqr() < 1.0E-4 ? look.horizontal() : out;
			out = out.lengthSqr() < 1.0E-4 ? ahead(player) : out.normalize();
			knock(living, living.getDeltaMovement().add(out.scale(SHATTER_KNOCK * (0.4 + 0.6 * hardShare))).add(0.0, SHATTER_LIFT, 0.0));
		}
		// the spray: puffs of their blocks scattered through the cone
		Vec3 dir = look.lengthSqr() < 1.0E-4 ? ahead(player) : look.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		double tan = Math.tan(Math.toRadians(SHATTER_HALF_ANGLE));
		for (int i = 0; i < SHATTER_SPRAY; i++) {
			double along = 0.6 + level.getRandom().nextDouble() * (SHATTER_RANGE - 0.6);
			double ang = level.getRandom().nextDouble() * Mth.TWO_PI;
			double r = Math.sqrt(level.getRandom().nextDouble()) * along * tan;
			Vec3 at = meet.add(dir.scale(along)).add(side.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
			BlockState state = states.get(level.getRandom().nextInt(states.size()));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.15);
			if (i % 3 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), at.x, at.y, at.z, 2, 0.3, 0.2, 0.3, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, meet.x, meet.y, meet.z, 1, 0.0, 0.0, 0.0, 0.0);
		groundDust(level, meet.add(dir.scale(SHATTER_RANGE * 0.5)));
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6F, 0.45F);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.45F);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
		ServerPlayNetworking.send(player, new WebNet.Shake(SHATTER_SHAKE));
	}

	private static boolean soft(Entity debris) {
		return debris instanceof BlockCluster chunk ? chunk.allSoft()
			: debris instanceof FallingBlockEntity falling && BlockCluster.soft(falling.getBlockState());
	}

	/** Used debris collapses at {@code at}: dust from every block, falling grit, and (by the usual chance) some drop. */
	private static void spend(ServerLevel level, Entity debris, Vec3 at) {
		if (debris instanceof BlockCluster chunk) {
			chunk.setPos(at.subtract(0.0, 0.7, 0.0));
			chunk.setDeltaMovement(Vec3.ZERO);
			for (BlockCluster.Piece piece : chunk.pieces()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, piece.blockState()), at.x, at.y, at.z, 4, 0.6, 0.4, 0.6, 0.0);
			}
			chunk.shatter(level);
		} else if (debris instanceof FallingBlockEntity falling) {
			BlockState state = falling.getBlockState();
			Debris.puff(level, BlockPos.containing(at), state, 1.6F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), at.x, at.y, at.z, 6, 0.6, 0.4, 0.6, 0.0);
			if (level.getRandom().nextFloat() < SPENT_DROP_CHANCE && !state.isAir()) {
				ItemStack drop = new ItemStack(state.getBlock());
				if (!drop.isEmpty()) {
					level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, drop));
				}
			}
			level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.5F, 0.6F);
			falling.discard();
		}
	}

	/** Dust kicked up from the ground under {@code at} (within a few blocks down). */
	private static void groundDust(ServerLevel level, Vec3 at) {
		BlockHitResult ground = level.clip(new ClipContext(at, at.subtract(0.0, 4.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (ground.getType() == HitResult.Type.BLOCK) {
			Debris.puff(level, ground.getBlockPos(), level.getBlockState(ground.getBlockPos()), 1.3F);
		}
	}
}
