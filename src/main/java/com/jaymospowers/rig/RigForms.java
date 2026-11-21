package com.jaymospowers.rig;

import com.jaymospowers.core.HitStop;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.Shakes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import com.jaymospowers.rig.RigForgeBlock;

/**
 * The Rig's arm forms (Frostborn Rig tech: plates slide, lock and fold, nothing grown or alive): each one is a Rig
 * menu entry with a charge cost, a pose, and a sound. One form out at a time; switching folds the last one away first.
 * <ul>
 * <li><b>Arm blade</b> ({@link #AF_BLADE}): the forearm plates slide forward into a blade for close fighting.</li>
 * <li><b>Mace arm smash</b> ({@link #AF_MACE}): the arm swells into a heavy mace and smashes the ground in a shockwave.</li>
 * <li><b>Pile bunker</b> ({@link #AF_PILE}): a spike punches out of the forearm at point blank: huge knockback and a crack.</li>
 * <li><b>Drill breaker</b> ({@link #AF_DRILL}): the arm becomes a drill and tears forward through soft stone.</li>
 * <li><b>Thruster lance</b> ({@link #AF_LANCE}): a lance forms, the thrusters fire, the first foe hit is carried and pinned.</li>
 * <li><b>Energy lash</b> ({@link #AF_LASH}): an energy whip off the wrist: a crack at range, or an overhead slam.</li>
 * <li><b>Weapon mimic</b> ({@link #AF_MIMIC}): copy a studied creature's weapon for 30 seconds.</li>
 * </ul>
 */
public final class RigForms {
	/** Action IDs, continuing RigPicks' range. */
	public static final int AF_BLADE = 29, AF_MACE = 30, AF_PILE = 31, AF_DRILL = 32, AF_LANCE = 33, AF_LASH = 34,
		AF_MIMIC = 35;

	/** Blade: cost, cooldown (ticks), reach (blocks), damage, knockback. */
	public static final int BLADE_COST = 30, BLADE_COOLDOWN = 20;
	public static final double BLADE_REACH = 1.8, BLADE_DAMAGE = 7.0, BLADE_KNOCKBACK = 0.7;
	/** Mace: cost, cooldown, shockwave radius, damage, knockback. */
	public static final int MACE_COST = 60, MACE_COOLDOWN = 40;
	public static final double MACE_RADIUS = 3.5, MACE_DAMAGE = 8.0, MACE_KNOCKBACK = 1.0;
	/** Pile: cost, cooldown, reach, damage, knockback. */
	public static final int PILE_COST = 40, PILE_COOLDOWN = 30;
	public static final double PILE_REACH = 2.2, PILE_DAMAGE = 6.0, PILE_KNOCKBACK = 2.2;
	/** Drill: cost, cooldown, how long the drill stays out (ticks), how fast it advances (b/t), damage per hit. */
	public static final int DRILL_COST = 80, DRILL_COOLDOWN = 60, DRILL_TICKS = 40;
	public static final double DRILL_SPEED = 0.35, DRILL_DAMAGE = 4.0;
	/** Lance: cost, cooldown, charge speed (b/t), damage, pin ticks. */
	public static final int LANCE_COST = 50, LANCE_COOLDOWN = 40;
	public static final double LANCE_SPEED = 1.4, LANCE_DAMAGE = 9.0;
	public static final int LANCE_PIN_TICKS = 60;
	/** Lash: cost, cooldown, reach, damage, pull strength. */
	public static final int LASH_COST = 25, LASH_COOLDOWN = 15;
	public static final double LASH_RANGE = 9.0, LASH_DAMAGE = 4.0, LASH_PULL = 0.6;
	/** Mimic: cost, cooldown (ticks), how long the copy lasts (seconds). */
	public static final int MIMIC_COST = 100, MIMIC_COOLDOWN = 600;
	public static final int MIMIC_SECONDS = 30;

	/** Mind levels that gate the stronger forms. */
	public static final int BLADE_LEVEL = 1, MACE_LEVEL = 2, PILE_LEVEL = 1, DRILL_LEVEL = 3, LANCE_LEVEL = 2,
		LASH_LEVEL = 1, MIMIC_LEVEL = 4;

	private static final class FormState {
		/** Which form is out (-1: none). */
		int form = -1;
		/** Game time the current form ends (drill, mimic), 0: none. */
		int formUntil = 0;
		/** Cooldown timers per form (game time it becomes ready again). */
		int bladeReady, maceReady, pileReady, drillReady, lanceReady, lashReady, mimicReady;
	}

	private static final Map<UUID, FormState> STATES = new HashMap<>();

	private RigForms() {
	}

	/** Called from {@link RigPicks#init}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	/** Whether {@code action} is an arm form. */
	public static boolean is(int action) {
		return action >= AF_BLADE && action <= AF_MIMIC;
	}

	/** The form this player currently has out (-1: none). */
	public static int form(ServerPlayer player) {
		FormState state = STATES.get(player.getUUID());
		return state == null ? -1 : state.form;
	}

	/** A Rig menu pick: an arm form. Folds the last one away first. */
	static void use(ServerPlayer player, int form) {
		if (player.isSpectator()) {
			return;
		}
		if (!Rig.wears(player, Rig.Piece.ARMS)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.needs_arms"));
			return;
		}
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		FormState state = state(player);
		int level = Rig.level(player);
		int cost, cooldown, needsLevel;
		switch (form) {
			case AF_BLADE -> {
				cost = BLADE_COST;
				cooldown = BLADE_COOLDOWN;
				needsLevel = BLADE_LEVEL;
			}
			case AF_MACE -> {
				cost = MACE_COST;
				cooldown = MACE_COOLDOWN;
				needsLevel = MACE_LEVEL;
			}
			case AF_PILE -> {
				cost = PILE_COST;
				cooldown = PILE_COOLDOWN;
				needsLevel = PILE_LEVEL;
			}
			case AF_DRILL -> {
				cost = DRILL_COST;
				cooldown = DRILL_COOLDOWN;
				needsLevel = DRILL_LEVEL;
			}
			case AF_LANCE -> {
				cost = LANCE_COST;
				cooldown = LANCE_COOLDOWN;
				needsLevel = LANCE_LEVEL;
			}
			case AF_LASH -> {
				cost = LASH_COST;
				cooldown = LASH_COOLDOWN;
				needsLevel = LASH_LEVEL;
			}
			case AF_MIMIC -> {
				cost = MIMIC_COST;
				cooldown = MIMIC_COOLDOWN;
				needsLevel = MIMIC_LEVEL;
			}
			default -> {
				return;
			}
		}
		if (level < needsLevel) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.needs_level", needsLevel));
			return;
		}
		long now = player.level().getGameTime();
		int ready = switch (form) {
			case AF_BLADE -> state.bladeReady;
			case AF_MACE -> state.maceReady;
			case AF_PILE -> state.pileReady;
			case AF_DRILL -> state.drillReady;
			case AF_LANCE -> state.lanceReady;
			case AF_LASH -> state.lashReady;
			case AF_MIMIC -> state.mimicReady;
			default -> 0;
		};
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.form_wait", (ready - now + 19) / 20));
			return;
		}
		if (!Rig.spend(player, cost)) {
			RigMind.empty(player);
			return;
		}
		// fold the last form away first
		state.form = -1;
		state.formUntil = 0;
		switch (form) {
			case AF_BLADE -> state.bladeReady = (int) (now + cooldown);
			case AF_MACE -> state.maceReady = (int) (now + cooldown);
			case AF_PILE -> state.pileReady = (int) (now + cooldown);
			case AF_DRILL -> state.drillReady = (int) (now + cooldown);
			case AF_LANCE -> state.lanceReady = (int) (now + cooldown);
			case AF_LASH -> state.lashReady = (int) (now + cooldown);
			case AF_MIMIC -> state.mimicReady = (int) (now + cooldown);
		}
		RigMoves.start(player, RigMoves.ARM_BLADE + (form - AF_BLADE));
		switch (form) {
			case AF_BLADE -> blade(player, state);
			case AF_MACE -> mace(player, state);
			case AF_PILE -> pile(player, state);
			case AF_DRILL -> drill(player, state);
			case AF_LANCE -> lance(player, state);
			case AF_LASH -> lash(player, state);
			case AF_MIMIC -> mimic(player, state);
		}
	}

	private static FormState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new FormState());
	}

	// ---- blade -----------------------------------------------------------------------------------------------------

	/** Wind-up: plates slide forward into a blade; snap: a quick strike along the look. */
	private static void blade(ServerPlayer player, FormState state) {
		state.form = AF_BLADE;
		state.formUntil = (int) (player.level().getGameTime() + 10);
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(0.0, -0.2, 0.0).add(look.scale(0.4));
		Vec3 end = from.add(look.scale(BLADE_REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end,
			player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
						(float) from.distanceToSqr(end));
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			DamageSource source = level.damageSources().playerAttack(player);
			float damage = (float) (BLADE_DAMAGE * Rig.frame(player).shotDamage * RigActives.levelDamage(player));
			if (target.hurtServer(level, source, damage)) {
				target.knockback(BLADE_KNOCKBACK, -look.x, -look.z, source, damage);
				target.syncVelocity = true;
			}
			MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.4F, RigActives.FLAME_HOT);
			HitStop.hit(player, target, 2);
		} else {
			MagicFx.stroke(level, from, end, 0.25F, RigActives.FLAME_COLOR);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 0.8F, 1.4F);
		Shakes.send(player, 0.5F, player.position());
	}

	// ---- mace ------------------------------------------------------------------------------------------------------

	/** Wind-up: the arm swells; snap: smash the ground, a shockwave in a radius. */
	private static void mace(ServerPlayer player, FormState state) {
		state.form = AF_MACE;
		state.formUntil = (int) (player.level().getGameTime() + 10);
		ServerLevel level = player.level();
		Vec3 feet = player.position().add(0.0, -0.3, 0.0);
		MagicFx.ring(level, feet, (float) MACE_RADIUS, RigActives.FLAME_COLOR);
		MagicFx.blast(level, feet.add(0.0, 0.2, 0.0), 1.2F, RigActives.FLAME_HOT);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.7F);
		Shakes.send(player, 1.0F, feet);
		DamageSource source = level.damageSources().playerAttack(player);
		float damage = (float) (MACE_DAMAGE * Rig.frame(player).shotDamage * RigActives.levelDamage(player));
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			new AABB(feet.x - MACE_RADIUS, feet.y - 1.0, feet.z - MACE_RADIUS, feet.x + MACE_RADIUS, feet.y + 2.0, feet.z + MACE_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player))) {
			if (target.hurtServer(level, source, damage)) {
				Vec3 away = target.position().subtract(player.position()).normalize();
				target.knockback(MACE_KNOCKBACK, away.x, away.z, source, damage);
				target.syncVelocity = true;
			}
		}
	}

	// ---- pile ------------------------------------------------------------------------------------------------------

	/** Wind-up: the spike loads; snap: it punches out, knockback and a crack in the block behind. */
	private static void pile(ServerPlayer player, FormState state) {
		state.form = AF_PILE;
		state.formUntil = (int) (player.level().getGameTime() + 8);
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0).add(look.scale(0.5));
		Vec3 end = from.add(look.scale(PILE_REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end,
			player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
						(float) from.distanceToSqr(end));
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			DamageSource source = level.damageSources().playerAttack(player);
			float damage = (float) (PILE_DAMAGE * Rig.frame(player).shotDamage * RigActives.levelDamage(player));
			if (target.hurtServer(level, source, damage)) {
				target.knockback(PILE_KNOCKBACK, -look.x, -look.z, source, damage);
				target.syncVelocity = true;
			}
			MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.5F, RigActives.FLAME_HOT);
			HitStop.hit(player, target, 3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.8F);
		} else {
			BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (block.getType() != HitResult.Type.MISS) {
								level.sendParticles(MagicFx.miasma(0x55585E, 1.0F), block.getLocation().x, block.getLocation().y,
								block.getLocation().z, 6, 0.2, 0.2, 0.2, 0.05);
								level.playSound(null, block.getLocation().x, block.getLocation().y, block.getLocation().z,
									SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 1.2F);
			} else {
				MagicFx.stroke(level, from, end, 0.25F, RigActives.FLAME_COLOR);
			}
		}
		Shakes.send(player, 0.8F, player.position());
	}

	// ---- drill -----------------------------------------------------------------------------------------------------

	/** Held: the arm becomes a drill, tearing forward through soft stone. */
	private static void drill(ServerPlayer player, FormState state) {
		state.form = AF_DRILL;
		state.formUntil = (int) (player.level().getGameTime() + DRILL_TICKS);
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 dir = player.onGround() ? new Vec3(look.x, 0.0, look.z) : look;
		if (dir.lengthSqr() < 1.0E-4) {
			dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		dir = dir.normalize();
		player.setDeltaMovement(dir.scale(DRILL_SPEED).add(0.0, player.onGround() ? 0.05 : 0.0, 0.0));
		player.syncVelocity = true;
		player.resetFallDistance();
		Vec3 feet = player.position();
		MagicFx.ring(level, feet.add(0.0, 0.4, 0.0), dir, 1.0F, RigActives.FLAME_COLOR, 6);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
		Shakes.send(player, 0.4F, feet.subtract(dir));
	}

	/** Every tick while the drill is out: mine soft stone ahead, damage what's in the way. */
	static void drillTick(ServerPlayer player, FormState state) {
		if (state.form != AF_DRILL || player.level().getGameTime() > state.formUntil || !Rig.fullSet(player)) {
			state.form = -1;
			state.formUntil = 0;
			return;
		}
		ServerLevel level = player.level();
		Vec3 motion = player.getDeltaMovement();
		Vec3 dir = motion.lengthSqr() < 1.0E-4 ? player.getLookAngle() : motion.normalize();
		BlockPos ahead = BlockPos.containing(player.position().add(dir.scale(1.2)));
		if (level.getBlockState(ahead).isAir() || !level.getBlockState(ahead).canBeReplaced()) {
			return;
		}
		if (player.getRandom().nextInt(3) == 0) {
			level.destroyBlock(ahead, true, player);
		}
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			player.getBoundingBox().inflate(0.9),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player))) {
			if (target.hurtServer(level, level.damageSources().playerAttack(player), (float) DRILL_DAMAGE)) {
				target.knockback(0.5, dir.x, dir.z, level.damageSources().playerAttack(player), (float) DRILL_DAMAGE);
				target.syncVelocity = true;
			}
		}
	}

	// ---- lance -----------------------------------------------------------------------------------------------------

	/** Hold before the dash: the first foe hit is carried along and pinned at the end. */
	private static void lance(ServerPlayer player, FormState state) {
		state.form = AF_LANCE;
		state.formUntil = (int) (player.level().getGameTime() + 10);
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 dir = player.onGround() ? new Vec3(look.x, 0.0, look.z) : look;
		if (dir.lengthSqr() < 1.0E-4) {
			dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		dir = dir.normalize();
		double speed = LANCE_SPEED * Rig.frame(player).airSpeed;
		player.setDeltaMovement(dir.scale(speed).add(0.0, player.onGround() ? 0.15 : 0.0, 0.0));
		player.syncVelocity = true;
		player.resetFallDistance();
		Vec3 feet = player.position();
		MagicFx.ring(level, feet.add(0.0, 0.6, 0.0), dir, 1.2F, RigActives.FLAME_COLOR, 8);
		MagicFx.stroke(level, feet.add(0.0, 0.9, 0.0), feet.add(0.0, 0.9, 0.0).add(dir.scale(speed * 4.0)), 0.5F, RigForgeBlock.FX_COLOR);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 1.3F);
		Shakes.send(player, 0.7F, feet.subtract(dir));
	}

	/** Every tick while the lance charge is out: pin the first foe hit. */
	static void lanceTick(ServerPlayer player, FormState state) {
		if (state.form != AF_LANCE || player.level().getGameTime() > state.formUntil || !Rig.fullSet(player)) {
			state.form = -1;
			state.formUntil = 0;
			return;
		}
		ServerLevel level = player.level();
		Vec3 motion = player.getDeltaMovement();
		if (motion.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 dir = motion.normalize();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
			player.getBoundingBox().inflate(1.2),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isAlliedTo(player))) {
			DamageSource source = level.damageSources().playerAttack(player);
			if (target.hurtServer(level, source, (float) (LANCE_DAMAGE * Rig.frame(player).shotDamage))) {
				target.knockback(0.0, dir.x, dir.z, source, 1.0F);
				target.syncVelocity = true;
				target.setDeltaMovement(dir.scale(0.4).add(0.0, 0.2, 0.0));
				target.syncVelocity = true;
				MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.5F, RigActives.FLAME_HOT);
				HitStop.hit(player, target, 3);
				break;
			}
		}
	}

	// ---- lash ------------------------------------------------------------------------------------------------------

	/** A quick whip: crack at range, or an overhead slam. Pulls small mobs toward you. */
	private static void lash(ServerPlayer player, FormState state) {
		state.form = AF_LASH;
		state.formUntil = (int) (player.level().getGameTime() + 10);
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(0.0, -0.2, 0.0);
		Vec3 end = from.add(look.scale(LASH_RANGE));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end,
			player.getBoundingBox().expandTowards(end.subtract(from)).inflate(1.2),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
						(float) from.distanceToSqr(end));
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			DamageSource source = level.damageSources().playerAttack(player);
			float damage = (float) (LASH_DAMAGE * Rig.frame(player).shotDamage * RigActives.levelDamage(player));
			if (target.hurtServer(level, source, damage)) {
				Vec3 toPlayer = player.position().subtract(target.position()).normalize();
				target.knockback(LASH_PULL, toPlayer.x, toPlayer.z, source, damage);
				target.syncVelocity = true;
			}
			MagicFx.blast(level, target.getBoundingBox().getCenter(), 0.4F, RigActives.FLAME_HOT);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.7F, 1.6F);
		} else {
			MagicFx.stroke(level, from, end, 0.2F, RigActives.FLAME_COLOR);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.5F, 1.8F);
		}
		Shakes.send(player, 0.4F, player.position());
	}

	// ---- mimic -----------------------------------------------------------------------------------------------------

	/** Scan a studied creature's weapon and copy it for 30 seconds. */
	private static void mimic(ServerPlayer player, FormState state) {
		state.form = AF_MIMIC;
		state.formUntil = (int) (player.level().getGameTime() + MIMIC_SECONDS * 20);
		List<RigStudy.Entry> studied = RigStudy.studied(player);
		if (studied.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.study_none"));
			state.form = -1;
			state.formUntil = 0;
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.mimic_ready", studied.get(0).id()));
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		MagicFx.ring(level, feet.add(0.0, 0.2, 0.0), 2.0F, RigActives.FLAME_COLOR);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
		Shakes.send(player, 0.3F, feet);
	}

	// ---- per tick --------------------------------------------------------------------------------------------------

	/** Every tick: cooldowns, and the drill / lance's ongoing effect. */
	static void tick(ServerPlayer player) {
		FormState state = STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		int now = (int) player.level().getGameTime();
		if (state.formUntil != 0 && now >= state.formUntil) {
			state.form = -1;
			state.formUntil = 0;
		}
		if (state.form == AF_DRILL) {
			drillTick(player, state);
		}
				if (state.form == AF_LANCE) {
					lanceTick(player, state);
						}
					}
					}