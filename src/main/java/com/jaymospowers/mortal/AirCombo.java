package com.jaymospowers.mortal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.armory.Lash;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.jump.PowerJump;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.smoke.SmokePower;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteBond;
import com.jaymospowers.power.web.WebTendrils;
import com.jaymospowers.power.web.WebUptilt;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.zfighter.ZFighterPower;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Launcher, juggle and slam, for those who can follow a foe into the air: the powers that fly (Wind, Z Fighter, Smoke,
 * Xenoform), Web, Darktide and Gravity, and anyone a symbiote rides ({@link #can}). All three ride on a plain melee blow:
 * <ul>
 * <li><b>Launcher:</b> sneaking, on the ground, a blow at something on the ground throws it {@link #LAUNCH} up and you
 * after it ({@link #LAUNCH_WAIT} ticks between launches). Nothing too big ({@link #BIGGEST} blocks tall) or that
 * cannot be shoved. Learned in Jaymo's Kingdoms' skill tree (Might's Air Combo, {@link com.jaymospowers.core.TreeLocks#AIR_COMBO}),
 * or by a web slinger with the Agility {@link WebUptilt#AGILITY_POINTS} asks;
 * the juggle and slam only follow a launch (or a pull-up, below).</li>
 * <li><b>Juggle:</b> each further blow on it before it lands bumps it back up, up to {@link #JUGGLES} times, and holds
 * you up with it.</li>
 * <li><b>Slam:</b> a sneaking blow on it in the air drives it down; where it lands it takes {@link #SLAM_DAMAGE} and
 * {@link #SLAM_PER_JUGGLE} more for each juggle, and whatever else stands within {@link #SLAM_WIDE} takes
 * {@link #SLAM_SPLASH}, except your friends and the villagers ({@link #spared}).</li>
 * </ul>
 * Andy (2026-11-27): "make them ragdoll, react, and anyone with a double jump can reset the combo in midair by jumping
 * near the enemy, like a DMC enemy step, allowing for higher skilled, flashy combos. web and the symbiotes should be able
 * to pull enemies up from the ground while he is midair to start combos or extend in that way."
 * <ul>
 * <li><b>Ragdoll:</b> a mob launched, juggled, slammed or stepped off goes limp in Jaymo's Kitchen's ragdoll and is thrown
 * as the body it is ({@link KitchenCompat#throwLimp}): no mind while it flies, it turns over in the air, it lands where it
 * falls and gets up when it lies still. One that can't go limp (a boss, just up again, no Kitchen) is stunned for
 * {@link #STUN_TICKS} instead, so it doesn't fight on in the air. Players are thrown as before.</li>
 * <li><b>Enemy step:</b> anyone with a second jump in the air ({@link #hasAirJump}: a power's, the Draught of Mettle's,
 * the gravitite set's, a cloud in a bottle's, a Sorcerer's) presses Jump in mid-air within {@link #STEP_REACH} of a foe
 * in a combo: they kick off it (the client's hop, {@code EnemyStepClient}) and that combo's juggles and time start again;
 * the foe is kicked down {@link #STEP_DIP}. The second jump itself is left as it was. Once per blow: a foe can't be
 * stepped off again until it's been hit (or pulled up) again.</li>
 * <li><b>Pull-up:</b> Web's Yank, or a symbiote's tendril grab, made in mid-air on a foe the combo could take
 * ({@link #pullUp}): it's hauled up to just in front of you and is in your combo (a fresh one, or the one it was in, its
 * time started again), ready to be hit.</li>
 * </ul>
 */
public final class AirCombo {
	static final double LAUNCH = 0.95, FOLLOW = 0.9, BUMP = 0.5, HOVER = 0.35, DRIVE = -1.8, BIGGEST = 3.5;
	static final int LAUNCH_WAIT = 120, AIRBORNE = 60, JUGGLES = 5, SLAM_TICKS = 60;
	static final float SLAM_DAMAGE = 6.0F, SLAM_PER_JUGGLE = 1.0F, SLAM_SPLASH = 3.0F;
	static final double SLAM_WIDE = 2.5;
	/** Ticks on the ground after it went up before a combo counts it landed (it's still leaving the ground). */
	static final int GROUND_GRACE = 5;
	/** A mob that can't go limp: stunned this long by each blow of the combo (ticks). */
	static final int STUN_TICKS = 20;
	/**
	 * The enemy step: how near the foe must be (blocks, box to box), and the slack the server allows past that (the two
	 * see positions a tick apart). The hop's lift and its push the way you steer are the client's ({@code EnemyStepClient}).
	 */
	public static final double STEP_REACH = 1.5, STEP_SLACK = 1.0;
	public static final double STEP_LIFT = 0.6, STEP_PUSH = 0.15;
	/** The kick the foe takes from the step: down this fast (blocks a tick). */
	static final double STEP_DIP = -0.25;
	/**
	 * A pull-up: where it's hauled to (this far ahead of you, this high against your feet), how fast it comes (blocks a
	 * tick), and the fewest and most ticks the haul takes.
	 */
	static final double HAUL_AHEAD = 1.6, HAUL_RISE = 0.2, HAUL_SPEED = 1.3;
	static final int HAUL_MIN_TICKS = 5, HAUL_MAX_TICKS = 14;
	/** A body's fall and its drag along the ground and up, each tick in the air (vanilla's, for living things). */
	static final double GRAVITY = 0.08, AIR_DRAG = 0.91;

	/** {@code since}: the game time it went up (a launch or a pull-up). */
	private record Up(UUID by, long since, long until, int juggles) {
	}

	private record Down(UUID by, long until, float damage) {
	}

	private static final Map<LivingEntity, Up> UP = new HashMap<>();
	private static final Map<LivingEntity, Down> DOWN = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	/** On a foe in someone's combo that can be stepped off now (synced to everyone, whose clients look for it). */
	public static final AttachmentType<Boolean> STEPPABLE = AttachmentRegistry.create(JaymosPowers.id("air_combo_steppable"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client to server: "I kicked off this foe" (entity id). */
	public record Step(int entityId) implements CustomPacketPayload {
		public static final Type<Step> TYPE = new Type<>(JaymosPowers.id("air_combo_step"));
		public static final StreamCodec<ByteBuf, Step> CODEC = ByteBufCodecs.VAR_INT.map(Step::new, Step::entityId);

		@Override
		public Type<Step> type() {
			return TYPE;
		}
	}

	private AirCombo() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Step.TYPE, Step.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Step.TYPE, (payload, context) -> step(context.player(), payload.entityId()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!FallingAttacks.landing && !Lash.noAirCombo && taken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer player && entity != player
				&& entity.isAlive() && entity.level() instanceof ServerLevel level) {
				blow(level, player, entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(AirCombo::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			UP.clear();
			DOWN.clear();
			READY.clear();
		});
	}

	/** Whether {@code target} is in the air after a launch by {@code by} that is still going (a web slinger can zip onto it, {@code WebSurf}). */
	public static boolean launchedBy(LivingEntity target, Player by) {
		Up up = UP.get(target);
		return up != null && up.by().equals(by.getUUID()) && !target.onGround() && target.isAlive();
	}

	/** Whether {@code player}'s power lets them fight in the air (or a symbiote, awake in them, does). */
	public static boolean can(Player player) {
		Power power = Powers.of(player);
		return power == WindPower.INSTANCE || power == ZFighterPower.INSTANCE || power == SmokePower.INSTANCE || power == XenoformPower.INSTANCE
			|| power == WebPower.INSTANCE || power == DarktidePower.INSTANCE || power == GravityPower.INSTANCE
			|| WebSymbiote.isBonded(player) && !WebSymbioteBond.dormant(player);
	}

	/**
	 * Whether {@code player} has a second jump in the air of any kind (either side): a power's or the Draught of Mettle's
	 * ({@link PowerJump}), the gravitite set's, a cloud in a bottle's, a Kingdoms Sorcerer's.
	 */
	public static boolean hasAirJump(Player player) {
		return PowerJump.kindOf(player) != PowerJump.NONE || com.jaymospowers.alchemy.picks.MettleDraught.active(player)
			|| com.jaymospowers.aether.gear.ArmorAbilities.gravitite(player)
			|| com.jaymospowers.baubles.artifacts.ArtifactBaubles.CLOUD_IN_A_BOTTLE != null
				&& com.jaymospowers.baubles.artifacts.ArtifactEffects.wears(player, com.jaymospowers.baubles.artifacts.ArtifactBaubles.CLOUD_IN_A_BOTTLE)
			|| Boolean.TRUE.equals(com.jaymospowers.compat.KingdomsCompat.call("SorcererJump", "has", new Class<?>[] {Player.class}, false, player));
	}

	/** Whether {@code target} is something the combo can throw about at all. */
	private static boolean takes(LivingEntity target) {
		return target.isAlive() && !target.isSpectator() && target.getBbHeight() <= BIGGEST
			&& target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0;
	}

	private static void blow(ServerLevel level, ServerPlayer player, LivingEntity target) {
		long now = level.getGameTime();
		Up up = UP.get(target);
		if (up != null && up.by().equals(player.getUUID()) && !target.onGround()) {
			// hit out of a tendril's grip: it's let go into the blow
			WebTendrils.letGoOf(player, target);
			if (player.isShiftKeyDown()) {
				// the slam
				UP.remove(target);
				target.removeAttached(STEPPABLE);
				Vec3 look = player.getLookAngle();
				throwBody(target, look.x * 0.4, DRIVE, look.z * 0.4);
				DOWN.put(target, new Down(player.getUUID(), now + SLAM_TICKS, SLAM_DAMAGE + SLAM_PER_JUGGLE * up.juggles()));
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.3F, 0.5F);
			} else if (up.juggles() < JUGGLES) {
				UP.put(target, new Up(up.by(), up.since(), now + AIRBORNE, up.juggles() + 1));
				target.setAttached(STEPPABLE, true);
				// a web slinger's blow uses the webs where there is something to stick to, and throws it higher
				if (WebUptilt.juggle(level, player, target, up.juggles() + 1)) {
					// its throws go through throwLimp; one that can't go limp doesn't fight on in the air
					if (target instanceof Mob && !KitchenCompat.limpMob(target)) {
						DarktideStatus.stun(target, STUN_TICKS);
					}
				} else {
					throwBody(target, 0.0, BUMP, 0.0);
					fling(player, new Vec3(player.getDeltaMovement().x, HOVER, player.getDeltaMovement().z));
				}
				player.resetFallDistance();
			}
			return;
		}
		if (player.isShiftKeyDown() && player.onGround() && target.onGround()) {
			launch(level, player, target, now);
		}
	}

	/**
	 * Andy (2026-11-28), of Combo Clash: "i like everything about except the air combos ... along with or enemy step for
	 * air combat". Its uppercut (jump held as a string's fourth blow lands) is our launcher now, from {@code
	 * ComboClashCompat}: off the ground with {@code player} after it, the juggle, slam and enemy step to follow. For
	 * those who can't fight in the air (or haven't learned it) it stays a plain fourth blow.
	 */
	public static void finisherLaunch(ServerPlayer player, LivingEntity target) {
		if (player.onGround() && target != player && player.level() instanceof ServerLevel level && !UP.containsKey(target)) {
			launch(level, player, target, level.getGameTime());
		}
	}

	/** The launcher, if {@code player} can and has it ready: {@code target} thrown up and {@code player} after it. */
	private static boolean launch(ServerLevel level, ServerPlayer player, LivingEntity target, long now) {
		if (!can(player) || now < READY.getOrDefault(player.getUUID(), 0L) || !takes(target)
			|| !(WebUptilt.unlocked(player) || com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.AIR_COMBO))) {
			return false;
		}
		// a Combo Clash string under way gives way to it
		com.jaymospowers.compat.ComboClashCompat.giveWay(player);
		READY.put(player.getUUID(), now + LAUNCH_WAIT);
		UP.put(target, new Up(player.getUUID(), now, now + AIRBORNE, 0));
		target.setAttached(STEPPABLE, true);
		throwBody(target, 0.0, LAUNCH, 0.0);
		fling(player, new Vec3(player.getDeltaMovement().x, FOLLOW, player.getDeltaMovement().z));
		level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1, target.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 1.2F);
		return true;
	}

	/**
	 * Web's Yank or a symbiote's tendril, from the air, on {@code target}: if {@code player} is off the ground, can fight in
	 * the air and has learned the Air Combo, and the combo can take it, it's in their combo now (its juggles kept if it
	 * was already theirs, its time started again). {@code haul}: it's pulled up to just in front of them (a tendril
	 * hauls what it grabs itself). Whether it's in the combo.
	 */
	public static boolean pullUp(ServerPlayer player, LivingEntity target, boolean haul) {
		if (player.onGround() || player.isInWater() || player.isPassenger() || target == player || !takes(target) || target.isInWater()
			|| !can(player) || !WebUptilt.unlocked(player) && !com.jaymospowers.core.TreeLocks.has(player, com.jaymospowers.core.TreeLocks.AIR_COMBO)
			|| !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		int ticks = 0;
		// a player's movement is their own client's: one yanked comes in on the web's own pull (WebPower's nudge)
		if (haul && !(target instanceof Player)) {
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
			Vec3 to = player.position().add(flat.scale(HAUL_AHEAD)).add(0.0, HAUL_RISE, 0.0);
			Vec3 d = to.subtract(target.position());
			ticks = Mth.clamp(Mth.ceil(d.length() / HAUL_SPEED), HAUL_MIN_TICKS, HAUL_MAX_TICKS);
			// across: what the air's drag leaves of a speed over that many ticks; up: what the fall takes back on the way
			double carry = (1.0 - Math.pow(AIR_DRAG, ticks)) / (1.0 - AIR_DRAG);
			double vy = d.y / ticks + GRAVITY * ticks * 0.5;
			if (target instanceof Mob mob && KitchenCompat.limpMob(mob) && KitchenCompat.throwLimp(mob, d.x / ticks, vy, d.z / ticks)) {
				// a body already limp is thrown as the body (no drag along the ground in the air)
			} else {
				fling(target, new Vec3(d.x / carry, vy, d.z / carry));
				if (target instanceof Mob) {
					DarktideStatus.stun(target, ticks + STUN_TICKS);
				}
			}
			level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1, target.getZ(), 6, 0.3, 0.05, 0.3, 0.03);
		}
		com.jaymospowers.compat.ComboClashCompat.giveWay(player);
		Up up = UP.get(target);
		int juggles = up != null && up.by().equals(player.getUUID()) ? up.juggles() : 0;
		UP.put(target, new Up(player.getUUID(), now + ticks, now + ticks + AIRBORNE, juggles));
		DOWN.remove(target);
		target.setAttached(STEPPABLE, true);
		return true;
	}

	/** The enemy step, as the client made it: checked, then the combo starts again and the foe is kicked down. */
	private static void step(ServerPlayer player, int id) {
		if (player.onGround() || player.isInWater() || player.isSpectator() || player.isPassenger() || !hasAirJump(player)
			|| !(player.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof LivingEntity foe)
			|| !Boolean.TRUE.equals(foe.getAttached(STEPPABLE))) {
			return;
		}
		Up up = UP.get(foe);
		if (up == null || !player.getBoundingBox().inflate(STEP_REACH + STEP_SLACK).intersects(foe.getBoundingBox())) {
			return;
		}
		long now = level.getGameTime();
		com.jaymospowers.compat.ComboClashCompat.giveWay(player);
		UP.put(foe, new Up(up.by(), up.since(), now + AIRBORNE, 0));
		foe.removeAttached(STEPPABLE);
		player.resetFallDistance();
		Vec3 v = foe.getDeltaMovement();
		throwBody(foe, v.x, STEP_DIP, v.z);
		Vec3 top = new Vec3(foe.getX(), foe.getBoundingBox().maxY, foe.getZ());
		level.sendParticles(ParticleTypes.CLOUD, top.x, top.y, top.z, 6, 0.25, 0.05, 0.25, 0.03);
		level.sendParticles(ParticleTypes.CRIT, top.x, top.y, top.z, 5, 0.25, 0.05, 0.25, 0.1);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1.0F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 1.5F);
	}

	/**
	 * A combo's throw of a mob as its ragdoll (limp, or knocked limp now: {@link KitchenCompat#throwLimp}), for the web
	 * moves' juggle ({@code WebUptilt}) as well. False when it can't go limp (or isn't a mob): the caller throws it.
	 */
	public static boolean throwLimp(LivingEntity target, Vec3 v) {
		return target instanceof Mob mob && KitchenCompat.throwLimp(mob, v.x, v.y, v.z);
	}

	/**
	 * Throws {@code target} at this speed: a mob limp, as its ragdoll ({@link KitchenCompat#throwLimp}); one that can't go
	 * limp is flung and stunned for {@link #STUN_TICKS}; a player is flung.
	 */
	private static void throwBody(LivingEntity target, double vx, double vy, double vz) {
		if (target instanceof Mob mob && KitchenCompat.throwLimp(mob, vx, vy, vz)) {
			return;
		}
		fling(target, new Vec3(vx, vy, vz));
		if (target instanceof Mob) {
			DarktideStatus.stun(target, STUN_TICKS);
		}
	}

	private static void fling(Entity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		entity.needsSync = true;
		entity.syncVelocity = true;
	}

	/**
	 * Left out of the slam's splash: {@code by}'s own pets, anyone on their side (a team, a Kingdoms party), and villagers,
	 * unless it's set on {@code by} right now (a guard, a dog or a villager turned on you is a foe).
	 */
	static boolean spared(@Nullable ServerPlayer by, LivingEntity near) {
		if (by != null && near instanceof Mob mob && mob.getTarget() == by) {
			return false;
		}
		if (near instanceof AbstractVillager) {
			return true;
		}
		if (by == null) {
			return false;
		}
		return near instanceof OwnableEntity pet && pet.getOwner() == by || by.isAlliedTo(near)
			|| near instanceof ServerPlayer other && com.jaymospowers.core.TeamTime.allied(by, other);
	}

	private static void tick(MinecraftServer server) {
		if (UP.isEmpty() && DOWN.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<LivingEntity, Up>> it = UP.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Up> e = it.next();
			LivingEntity target = e.getKey();
			Up up = e.getValue();
			long now = target.level().getGameTime();
			ServerPlayer by = server.getPlayerList().getPlayer(up.by());
			// held up by its owner's tendril: the combo waits for it
			boolean held = by != null && WebTendrils.holds(by, target);
			if (!target.isAlive() || target.isRemoved() || !held && (now > up.until() || target.onGround() && now > up.since() + GROUND_GRACE)) {
				target.removeAttached(STEPPABLE);
				it.remove();
			}
		}
		for (Iterator<Map.Entry<LivingEntity, Down>> it = DOWN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Down> e = it.next();
			LivingEntity target = e.getKey();
			if (!target.isAlive() || !(target.level() instanceof ServerLevel level) || level.getGameTime() > e.getValue().until()) {
				it.remove();
				continue;
			}
			if (!target.onGround() && !target.isInWater()) {
				continue;
			}
			it.remove();
			ServerPlayer by = server.getPlayerList().getPlayer(e.getValue().by());
			var source = by != null ? by.damageSources().playerAttack(by) : level.damageSources().generic();
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, e.getValue().damage());
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SLAM_WIDE, 1.0, SLAM_WIDE),
				n -> n != target && n != by && n.isAlive() && !n.isSpectator() && !spared(by, n))) {
				near.hurtServer(level, source, SLAM_SPLASH);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.2, target.getZ(), 2, 0.4, 0.0, 0.4, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
		}
	}
}
