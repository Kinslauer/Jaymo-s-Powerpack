package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * The Monk's tree (class trees, docs/ClassTrees-Plan.md). {@link Monks} keeps the starting move (Catch an Arrow, whose
 * window widens with the Monk's level) and asks here for the rest:
 * <ul>
 * <li><b>Deep Breath</b> (3): one more ki a rank.</li>
 * <li><b>Stunning Blow</b> (3): today's stunning blow, moved into the tree (kept by Monks who had it).</li>
 * <li><b>Deflect</b> (3): hands free, an arrow you don't catch glances off for half; hold sneak as you catch one and it goes
 * straight back at whoever loosed it.</li>
 * <li><b>Flurry</b> (5): sneak and the key with no caught arrow to throw readies it; the next fist blow spends a ki and two
 * quick blows follow it.</li>
 * <li><b>Step of the Wind</b> (8): the key in the air spends a ki on a long leap or a dash along your look, and falling beside a
 * wall you slide down it unharmed (today's wall slide, kept by Monks who had it).</li>
 * <li><b>Open Hand</b> or <b>Iron Body</b> (10): the flurry's blows shove the foe away and the last knocks it off its feet;
 * or a blow that would take you below half health spends a ki and hardens you, half harm for a few seconds.</li>
 * <li><b>Still Mind</b> (15): fear and charms fall off you, and meditating brings ki back.</li>
 * <li><b>Quivering Palm</b> or <b>Empty Body</b> (20): a stunning blow sets the palm for a ki more, and the key, later, ends
 * it: the foe falls (a boss or a player takes a heavy blow instead); or sneak and the key in the air spends two ki to go
 * unseen for a few seconds, every blow slipping past you.</li>
 * </ul>
 */
public final class KiArts {
	/** Deflect: an arrow that isn't caught does this share. */
	static final float DEFLECT_TAKEN = 0.5F;
	/** Flurry: how long it waits readied (ticks), the follow-up blows' share of the first, their spacing (ticks) and reach. */
	static final int FLURRY_READY = 100, FLURRY_GAP = 4;
	static final float FLURRY_SHARE = 0.5F;
	static final double FLURRY_REACH = 4.0;
	/** Step of the Wind: the dash along the look and the lift with it. */
	static final double WIND_DASH = 1.4, WIND_LIFT = 0.35;
	/** Open Hand: the shove of each blow and the last one's knock-down (ticks). */
	static final float OPEN_SHOVE = 0.7F;
	static final int OPEN_DOWN = 30;
	/** Iron Body: how long it holds (ticks) and the share of harm taken. */
	static final int IRON_TICKS = 100;
	static final float IRON_TAKEN = 0.5F;
	/** Still Mind: a ki back every this many ticks of meditation. */
	static final int STILL_KI_TICKS = 200;
	/** Quivering Palm: the ki it costs over the stun, how long it can be ended (ticks), how far, and a boss's share of its health. */
	static final int PALM_KI = 1, PALM_TICKS = 20 * 120;
	static final double PALM_RANGE = 48.0;
	static final float PALM_BOSS = 0.3F;
	/** Empty Body: its ki and how long it lasts (ticks). */
	static final int EMPTY_KI = 2, EMPTY_TICKS = 100;

	public static final String BREATH = "monk_breath", STUN = "monk_stun", DEFLECT = "monk_deflect", FLURRY = "monk_flurry",
		WIND = "monk_wind", OPEN_HAND = "monk_open_hand", IRON_BODY = "monk_iron_body", STILL = "monk_still",
		QUIVERING = "monk_quivering", EMPTY = "monk_empty";

	private static final Map<UUID, Long> FLURRY_READIED = new HashMap<>(), IRON = new HashMap<>(), EMPTIED = new HashMap<>();
	/** The flurry's follow-up blows to come. */
	private record Follow(UUID monk, LivingEntity target, long at, float damage, boolean last) {
	}

	private static final List<Follow> FOLLOWS = new ArrayList<>();
	/** A set palm: on what, until when. */
	private record Palm(LivingEntity target, long until) {
	}

	private static final Map<UUID, Palm> PALMS = new HashMap<>();
	/** A follow-up blow landing now (so it doesn't start another flurry). */
	private static boolean following;

	private KiArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(KiArts::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Empty Body: blows slip past
			if (entity instanceof ServerPlayer monk && source.getEntity() != null && source.getEntity() != monk
				&& EMPTIED.getOrDefault(monk.getUUID(), 0L) > monk.level().getGameTime()) {
				monk.level().playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 1.6F);
				return false;
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLURRY_READIED.clear();
			IRON.clear();
			EMPTIED.clear();
			FOLLOWS.clear();
			PALMS.clear();
		});
	}

	static boolean has(Player player, String key) {
		return Classes.is(player, PlayerClass.MONK) && ClassTrees.has(player, key) > 0;
	}

	/** Deep Breath: the ki it adds. */
	static int breath(Player player) {
		return Classes.is(player, PlayerClass.MONK) ? Math.round(ClassTrees.power(player, BREATH)) : 0;
	}

	/** Step of the Wind: a Monk who slides down walls (and, sliding, comes to no harm). */
	static boolean wallSlide(Player player) {
		return has(player, WIND);
	}

	// ---- the key ---------------------------------------------------------------------------------------------------------------

	/** The key in the air: Step of the Wind. False when it isn't that. */
	static boolean leap(ServerPlayer monk) {
		if (monk.onGround() || monk.isInWater() || monk.isPassenger() || monk.getAbilities().flying || !has(monk, WIND)) {
			return false;
		}
		if (!Rests.spend(monk, Rests.KI, 1, Monks.ki(monk))) {
			monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.spent"));
			return true;
		}
		Vec3 look = monk.getLookAngle();
		monk.setDeltaMovement(look.scale(WIND_DASH).add(0.0, WIND_LIFT, 0.0));
		monk.syncVelocity = true;
		monk.fallDistance = 0.0F;
		ServerLevel level = monk.level();
		Fx.wave(level, monk.position().add(0.0, 0.2, 0.0), look.multiply(-1.0, 0.0, -1.0), 1.5, 0.6F, 0xE8F4FF, 8);
		level.playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.3F);
		return true;
	}

	/** Sneak and the key in the air: Empty Body. False when it isn't that. */
	static boolean emptyBody(ServerPlayer monk) {
		if (monk.onGround() || monk.isInWater() || !has(monk, EMPTY)) {
			return false;
		}
		if (!Rests.spend(monk, Rests.KI, EMPTY_KI, Monks.ki(monk))) {
			monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.spent"));
			return true;
		}
		ServerLevel level = monk.level();
		EMPTIED.put(monk.getUUID(), level.getGameTime() + EMPTY_TICKS);
		monk.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EMPTY_TICKS, 0, false, false, true));
		for (Mob mob : level.getEntitiesOfClass(Mob.class, monk.getBoundingBox().inflate(32.0), m -> m.getTarget() == monk)) {
			mob.setTarget(null);
		}
		Fx.orb(level, monk.position().add(0.0, 1.0, 0.0), 1.0F, 0xC8D8F0, 12);
		level.playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	/** Sneak and the key with nothing caught to throw: Flurry readied. */
	static void readyFlurry(ServerPlayer monk) {
		if (!Classes.is(monk, PlayerClass.MONK) || !ClassTrees.check(monk, FLURRY) || !Monks.fists(monk.getMainHandItem())) {
			return;
		}
		if (Rests.left(monk, Rests.KI, Monks.ki(monk)) <= 0) {
			monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.spent"));
			return;
		}
		ServerLevel level = monk.level();
		FLURRY_READIED.put(monk.getUUID(), level.getGameTime() + FLURRY_READY);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, monk.getX(), monk.getY(1.0), monk.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
		level.playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.9F);
		monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.flurry", Rests.left(monk, Rests.KI, Monks.ki(monk))));
	}

	/** The key with a palm set: it ends. False when no palm is set. */
	static boolean endPalm(ServerPlayer monk) {
		Palm palm = PALMS.remove(monk.getUUID());
		ServerLevel level = monk.level();
		if (palm == null || palm.until < level.getGameTime() || !palm.target.isAlive() || palm.target.level() != level
			|| palm.target.distanceTo(monk) > PALM_RANGE) {
			return false;
		}
		LivingEntity target = palm.target;
		boolean great = Legendary.boss(target) || target instanceof Player;
		float damage = great ? target.getMaxHealth() * PALM_BOSS : target.getHealth() + target.getAbsorptionAmount() + 100.0F;
		target.setInvulnerableTime(0);
		target.hurtServer(level, monk.damageSources().indirectMagic(monk, monk), damage);
		Fx.ring(level, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 1.2F, 0xE8F4FF);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.4F);
		monk.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return true;
	}

	// ---- the blows -------------------------------------------------------------------------------------------------------------

	/** A fist blow landed ({@link Monks#struck}): a readied flurry follows it. */
	static void struck(ServerPlayer monk, LivingEntity target, float amount) {
		if (following) {
			return;
		}
		Long until = FLURRY_READIED.get(monk.getUUID());
		long now = monk.level().getGameTime();
		if (until == null || until < now) {
			return;
		}
		FLURRY_READIED.remove(monk.getUUID());
		if (!Rests.spend(monk, Rests.KI, 1, Monks.ki(monk))) {
			monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.spent"));
			return;
		}
		FOLLOWS.add(new Follow(monk.getUUID(), target, now + FLURRY_GAP, amount * FLURRY_SHARE, false));
		FOLLOWS.add(new Follow(monk.getUUID(), target, now + FLURRY_GAP * 2L, amount * FLURRY_SHARE, true));
	}

	/** A stunning blow landed on {@code target}: with Quivering Palm, the palm is set (a ki more). */
	static void stunned(ServerPlayer monk, LivingEntity target) {
		if (!has(monk, QUIVERING) || !Rests.spend(monk, Rests.KI, PALM_KI, Monks.ki(monk))) {
			return;
		}
		PALMS.put(monk.getUUID(), new Palm(target, monk.level().getGameTime() + PALM_TICKS));
		monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.palm"));
		monk.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.6F, 2.0F);
	}

	/** What the Monk's tree does to a blow on {@code target}: Deflect and Iron Body on a Monk struck. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof ServerPlayer monk) || !Classes.is(monk, PlayerClass.MONK) || source.getEntity() == monk) {
			return amount;
		}
		ServerLevel level = monk.level();
		if (source.getDirectEntity() instanceof AbstractArrow && has(monk, DEFLECT) && Monks.fists(monk.getMainHandItem())) {
			amount *= DEFLECT_TAKEN;
			level.sendParticles(ParticleTypes.CRIT, monk.getX(), monk.getY(1.0), monk.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.5F, 1.7F);
		}
		if (has(monk, IRON_BODY) && source.getEntity() != null) {
			long now = level.getGameTime();
			boolean on = IRON.getOrDefault(monk.getUUID(), 0L) > now;
			if (!on && monk.getHealth() - amount < monk.getMaxHealth() * 0.5F && Rests.spend(monk, Rests.KI, 1, Monks.ki(monk))) {
				IRON.put(monk.getUUID(), now + IRON_TICKS);
				on = true;
				Fx.orb(level, monk.position().add(0.0, 1.0, 0.0), 1.1F, 0xB8B8C8, 20);
				level.playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5F, 1.6F);
			}
			if (on) {
				amount *= IRON_TAKEN;
			}
		}
		return amount;
	}

	// ---- the tick --------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!FOLLOWS.isEmpty()) {
			List<Follow> due = new ArrayList<>();
			FOLLOWS.removeIf(f -> {
				if (f.at > now) {
					return false;
				}
				due.add(f);
				return true;
			});
			for (Follow f : due) {
				follow(server, f);
			}
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer monk : server.getPlayerList().getPlayers()) {
			if (!has(monk, STILL)) {
				continue;
			}
			// Still Mind: fear and charms fall away; meditation brings ki back
			if (Fear.frightened(monk)) {
				Fear.calm(monk);
			}
			if (Charm.charmed(monk)) {
				Charm.free(monk);
			}
			if (PowersCompat.meditating(monk) && monk.tickCount % STILL_KI_TICKS < 20 && Rests.spent(monk, Rests.KI) > 0) {
				Rests.regain(monk, Rests.KI, 1);
				monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.breath", Rests.left(monk, Rests.KI, Monks.ki(monk))));
			}
		}
		if (now % 1200 == 0) {
			IRON.values().removeIf(t -> t < now);
			EMPTIED.values().removeIf(t -> t < now);
			PALMS.values().removeIf(p -> p.until < now || !p.target.isAlive());
		}
	}

	/** One of a flurry's quick blows. */
	private static void follow(MinecraftServer server, Follow f) {
		ServerPlayer monk = server.getPlayerList().getPlayer(f.monk);
		LivingEntity target = f.target;
		if (monk == null || !monk.isAlive() || !target.isAlive() || target.level() != monk.level() || monk.distanceTo(target) > FLURRY_REACH
			|| !Monks.fists(monk.getMainHandItem())) {
			return;
		}
		ServerLevel level = monk.level();
		monk.swing(f.last ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		target.setInvulnerableTime(0);
		DamageSource source = monk.damageSources().playerAttack(monk);
		following = true;
		try {
			target.hurtServer(level, source, f.damage);
		} finally {
			following = false;
		}
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 6, 0.2, 0.3, 0.2, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.3F);
		if (has(monk, OPEN_HAND)) {
			// Open Hand: shoved away, and the last blow knocks it off its feet
			target.knockback(OPEN_SHOVE, monk.getX() - target.getX(), monk.getZ() - target.getZ(), source, f.damage);
			target.syncVelocity = true;
			if (f.last) {
				HeldFast.afflict(target, OPEN_DOWN);
			}
		}
	}
}
