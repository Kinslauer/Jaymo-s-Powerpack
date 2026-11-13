package com.jaymoskingdoms;

import com.jaymoskingdoms.champions.MoveKit;
import com.jaymoskingdoms.dragons.WindDragon;
import com.jaymoskingdoms.films.BossIntros;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of a boss fight (docs/BossRise-Plan.md, patch 2; the ideas are Bosses'Rise's, an All Rights Reserved mod:
 * none of its code, art or names). Two beats for every great foe that has an entrance ({@link BossIntros#kind}):
 * <ul>
 * <li><b>The turn.</b> The first time it is brought to {@link #TURN_AT} of its health the fight stops for
 * {@link #TURN_HOLD} ticks: it can't be hurt, it roars, and everyone near who can see it gets a short film of it
 * ({@link BossIntros#play}). Then it comes back changed. A foe with a fight of its own ({@link #ownFight}: the patrons'
 * champions, the dragons, kings and chiefs, the grizzly, the Ogre King, the Wither and the Ender Dragon) only gets the film. Every other one is
 * quicker and hits harder from then on ({@link #QUICKER}, {@link #HARDER}) and has one move it didn't have before,
 * the <b>shove</b>: a red glint, a wind-up of {@link #SHOVE_WINDUP} ticks, then everyone within reach is struck and
 * thrown back. Roll or back away when it glints.</li>
 * <li><b>The fall.</b> A great foe killed by a player doesn't tip over at once. It stops, bows, shudders for
 * {@link #DEATH_TICKS} ticks while those near are shown it, and then falls to the blow that killed it. The dragons keep
 * their own deaths, and so do vanilla's two.</li>
 * </ul>
 * Nothing is written on screen for either. A turn is once per foe (the {@link #TURNED} tag, saved).
 */
public final class BossTurns {
	/** The share of its health at which a great foe turns. */
	static final float TURN_AT = 0.5F;
	/** The turn: how long the fight stops, how long its film runs and when in it the foe roars (ticks). */
	static final int TURN_HOLD = 50, TURN_FILM = 50, TURN_ROAR = 14;
	/** How far away (blocks) a player is shown a turn or a fall. */
	static final double WITNESS = 32.0;
	/** After the turn: its added speed and the added weight of its blows (shares). */
	static final double QUICKER = 0.15, HARDER = 0.2;
	/** The shove: how often (ticks), its wind-up, its reach past the foe's own width, its push and lift, its share of a plain blow. */
	static final int SHOVE_EVERY = 240, SHOVE_FIRST = 100, SHOVE_WINDUP = 24;
	static final double SHOVE_REACH = 4.5, SHOVE_PUSH = 1.3, SHOVE_LIFT = 0.45;
	static final float SHOVE_SHARE = 0.6F;
	/** The fall: how long it stands dying, and how long its film runs (ticks). */
	static final int DEATH_TICKS = 50, DEATH_FILM = 75;
	/** How far the head bows as it dies (degrees). */
	static final float BOW_PITCH = 50.0F;

	/** On a great foe that has turned. */
	static final String TURNED = "jaymoskingdoms.turned";
	private static final Identifier QUICKER_ID = JaymosKingdoms.id("turned_quicker"), HARDER_ID = JaymosKingdoms.id("turned_harder");
	private static final Identifier LATE = JaymosKingdoms.id("boss_fall");

	private static final class Turn {
		long holdUntil, nextShove, shoveAt;
	}

	private record Dying(long until, DamageSource source) {
	}

	private static final Map<Mob, Turn> TURNS = new WeakHashMap<>();
	private static final Map<Mob, Dying> DYING = new WeakHashMap<>();
	/** Those whose fall has been played: the next death is the real one. */
	private static final Map<Mob, Boolean> FALLEN = new WeakHashMap<>();

	private BossTurns() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BossTurns::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BossTurns::allowDamage);
		// (after everything that may yet save it: the fall is only for a death that is really happening)
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(LATE, BossTurns::allowDeath);
		ServerTickEvents.END_SERVER_TICK.register(BossTurns::tick);
	}

	/** A great foe in a fight: it has an entrance, and it is hostile or after someone. */
	static boolean applies(Entity entity) {
		return entity instanceof Mob mob && mob.isAlive() && BossIntros.kind(entity) != null && (entity instanceof Enemy || mob.getTarget() != null);
	}

	/** Foes whose fights already change as they go: they get the turn's film and nothing else. */
	static boolean ownFight(Entity entity) {
		return entity instanceof PatronChampion || entity instanceof WindDragon || entity instanceof Folk || entity instanceof EnderDragon
			|| entity instanceof WitherBoss || entity instanceof com.jaymoskingdoms.wild.Grizzly || OgreHolds.king(entity);
	}

	/** Foes that keep the death they have. */
	static boolean ownDeath(Entity entity) {
		return entity instanceof WindDragon || entity instanceof EnderDragon || entity instanceof WitherBoss;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !applies(mob) || mob.getHealth() <= 0.0F) {
			return;
		}
		if (mob.entityTags().contains(TURNED)) {
			// (turned before this world was last loaded: its shove picks up again)
			if (!ownFight(mob) && !TURNS.containsKey(mob)) {
				Turn turn = new Turn();
				turn.nextShove = level.getGameTime() + SHOVE_FIRST;
				TURNS.put(mob, turn);
			}
			return;
		}
		if (mob.getHealth() <= mob.getMaxHealth() * TURN_AT && !DYING.containsKey(mob)) {
			turn(level, mob);
		}
	}

	private static void turn(ServerLevel level, Mob mob) {
		long now = level.getGameTime();
		mob.addTag(TURNED);
		Turn turn = new Turn();
		turn.holdUntil = now + TURN_HOLD;
		turn.nextShove = now + TURN_HOLD + SHOVE_FIRST;
		TURNS.put(mob, turn);
		mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TURN_HOLD, 9, false, false));
		mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TURN_HOLD, 9, false, false));
		mob.getNavigation().stop();
		if (!ownFight(mob)) {
			strengthen(mob, Attributes.MOVEMENT_SPEED, QUICKER_ID, QUICKER);
			strengthen(mob, Attributes.ATTACK_DAMAGE, HARDER_ID, HARDER);
		}
		Vec3 chest = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
		double spread = mob.getBbWidth() * 0.5;
		level.sendParticles(ParticleTypes.LARGE_SMOKE, chest.x, chest.y, chest.z, 24, spread, 0.5, spread, 0.04);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, chest.x, chest.y + mob.getBbHeight() * 0.4, chest.z, 4, spread, 0.2, spread, 0.0);
		boolean filmed = false;
		for (ServerPlayer player : witnesses(level, mob)) {
			if (player.onGround() && !Downed.isDowned(player)) {
				filmed |= BossIntros.play(player, mob, BossIntros.Play.TURN, TURN_FILM, TURN_ROAR);
			}
		}
		if (!filmed) {
			// nobody could be shown it: it is still heard
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
		}
	}

	private static void strengthen(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double share) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(id)) {
			instance.addPermanentModifier(new AttributeModifier(id, share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Players near enough and with a clear sight of {@code mob}. */
	private static java.util.List<ServerPlayer> witnesses(ServerLevel level, Mob mob) {
		java.util.List<ServerPlayer> seen = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(mob) <= WITNESS * WITNESS && player.hasLineOfSight(mob)) {
				seen.add(player);
			}
		}
		return seen;
	}

	/** Nothing hurts a great foe while it turns, or while it stands dying. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof Mob mob) || TURNS.isEmpty() && DYING.isEmpty()) {
			return true;
		}
		if (DYING.containsKey(mob)) {
			return false;
		}
		Turn turn = TURNS.get(mob);
		return turn == null || mob.level().getGameTime() >= turn.holdUntil;
	}

	/** The fall: a great foe a player has killed stands dying first ({@link #DEATH_TICKS}), then dies to that same blow. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || FALLEN.containsKey(mob)) {
			return true;
		}
		if (DYING.containsKey(mob)) {
			return false;
		}
		if (BossIntros.kind(mob) == null || ownDeath(mob) || !(source.getEntity() instanceof ServerPlayer) || mob.isPassenger() || mob.isVehicle()) {
			return true;
		}
		long now = level.getGameTime();
		mob.setHealth(1.0F);
		DYING.put(mob, new Dying(now + DEATH_TICKS, source));
		TURNS.remove(mob);
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DEATH_TICKS + 10, 9, false, false));
		mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEATH_TICKS + 10, 9, false, false));
		Vec3 chest = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.CRIT, chest.x, chest.y, chest.z, 30, 0.5, 0.5, 0.5, 0.3);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
		PowersCompat.impactFrame(level, chest);
		for (ServerPlayer player : witnesses(level, mob)) {
			BossIntros.play(player, mob, BossIntros.Play.DEATH, DEATH_FILM, -1);
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (!DYING.isEmpty()) {
			for (Map.Entry<Mob, Dying> entry : new ArrayList<>(DYING.entrySet())) {
				dying(entry.getKey(), entry.getValue());
			}
		}
		if (!TURNS.isEmpty()) {
			for (Map.Entry<Mob, Turn> entry : new ArrayList<>(TURNS.entrySet())) {
				turned(entry.getKey(), entry.getValue());
			}
		}
	}

	private static void dying(Mob mob, Dying dying) {
		if (mob == null || mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			DYING.remove(mob);
			return;
		}
		long now = level.getGameTime();
		if (now >= dying.until()) {
			DYING.remove(mob);
			FALLEN.put(mob, Boolean.TRUE);
			mob.hurtServer(level, dying.source(), Float.MAX_VALUE);
			if (mob.isAlive()) {
				mob.kill(level);
			}
			return;
		}
		// it stands where it was struck, head sinking, and shudders
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
		float sunk = 1.0F - (dying.until() - now) / (float) DEATH_TICKS;
		mob.setXRot(BOW_PITCH * sunk);
		if (now % 6 == 0) {
			Vec3 chest = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
			double spread = mob.getBbWidth() * 0.4;
			level.sendParticles(ParticleTypes.SMOKE, chest.x, chest.y, chest.z, 6, spread, 0.4, spread, 0.01);
			mob.setYBodyRot(mob.yBodyRot + (mob.getRandom().nextFloat() - 0.5F) * 8.0F);
		}
	}

	private static void turned(Mob mob, Turn turn) {
		if (mob == null || mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			TURNS.remove(mob);
			return;
		}
		long now = level.getGameTime();
		if (now < turn.holdUntil) {
			mob.getNavigation().stop();
			return;
		}
		if (ownFight(mob)) {
			TURNS.remove(mob);
			return;
		}
		if (mob.isNoAi() || BossPosture.kneeling(mob) || Downed.isDowned(mob)) {
			turn.shoveAt = 0;
			return;
		}
		double reach = mob.getBbWidth() * 0.5 + SHOVE_REACH;
		if (turn.shoveAt > 0) {
			mob.getNavigation().stop();
			if (now >= turn.shoveAt) {
				turn.shoveAt = 0;
				turn.nextShove = now + SHOVE_EVERY;
				shove(level, mob, reach);
			}
			return;
		}
		LivingEntity target = mob.getTarget();
		if (now >= turn.nextShove && target != null && target.isAlive() && mob.onGround() && mob.distanceTo(target) <= reach) {
			turn.shoveAt = now + SHOVE_WINDUP;
			MobCombat.tell(level, mob, true);
			PowersCompat.tellIncoming(mob, target, SHOVE_WINDUP, true);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2F, 0.8F);
		}
	}

	/** The shove lands: everyone it is fighting within {@code reach} is struck and thrown back. */
	private static void shove(ServerLevel level, Mob mob, double reach) {
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		float blow = (float) ((damage == null ? 6.0 : damage.getValue()) * SHOVE_SHARE);
		Vec3 feet = mob.position();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach), e -> e != mob && e.isAlive())) {
			boolean foe = near == mob.getTarget() || near instanceof Mob other && other.getTarget() == mob
				|| near instanceof Player player && !player.isCreative() && !player.isSpectator() && mob instanceof Enemy;
			if (!foe || near.distanceTo(mob) > reach + near.getBbWidth() * 0.5) {
				continue;
			}
			mob.addTag(MobCombat.UNPARRYABLE_TAG);
			try {
				near.hurtServer(level, level.damageSources().mobAttack(mob), blow);
			} finally {
				mob.removeTag(MobCombat.UNPARRYABLE_TAG);
			}
			MoveKit.push(near, feet, SHOVE_PUSH, SHOVE_LIFT);
			near.syncVelocity = true;
		}
		int puffs = Math.max(16, (int) (reach * 8));
		for (int i = 0; i < puffs; i++) {
			double angle = Math.PI * 2.0 * i / puffs;
			level.sendParticles(ParticleTypes.POOF, feet.x + Math.cos(angle) * reach * 0.6, feet.y + 0.15, feet.z + Math.sin(angle) * reach * 0.6,
				1, 0.1, 0.02, 0.1, 0.06);
		}
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9F, 0.6F);
		PowersCompat.impactFrame(level, feet.add(0.0, mob.getBbHeight() * 0.5, 0.0));
	}
}
