package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fighter's way:
 * <ul>
 * <li><b>No foe fells him twice.</b> Whatever kills him (each kind of creature; each player by name) owes him a rematch:
 * until he beats it (kills one of that kind, or that player), it does {@link #FOE_RESIST} less damage to him and takes
 * {@link #FOE_BONUS} more from him; beating it settles the score and the edge is gone. Every new victor also makes him
 * stronger for good (more ki, harder fists; see {@link ZFighterPower#zenkai}).</li>
 * <li><b>Fists only.</b> A weapon (anything with attack damage of its own) won't strike in his hand
 * ({@code ZFighterPlayerMixin}). Every punch that lands builds ki and a combo; every {@link #COMBO_FINISHER}th hit in
 * a row is a finisher that sends the foe flying.</li>
 * <li><b>Super, through hardship.</b> Hardship builds from coming back from the brink in a fight, from defeats, from
 * settling scores with old victors and from felling great beasts. With {@link #SUPER_HARDSHIP} of it and every ability
 * mastered, the next time he's near death in a fight he breaks through: the power ascends and he goes Super on the
 * spot. After that, C goes Super at will.</li>
 * </ul>
 */
final class ZFighterBattle {
	/** Damage a foe that once beat him does to him is cut by this much; his own damage to it grows by this much. */
	static final float FOE_RESIST = 0.5F;
	static final float FOE_BONUS = 0.5F;

	/** Hits within this many ticks of each other keep a combo going. */
	static final int COMBO_WINDOW = 25;
	static final int COMBO_FINISHER = 5;
	static final float FINISHER_DAMAGE = 4.0F;

	// ---- hardship ----
	static final int SUPER_HARDSHIP = 40;
	/** Fallen to a foe he'd never lost to before. */
	static final int DEFEAT_HARDSHIP = 4;
	/** Down at or below this share of his health in a fight... */
	static final float CLUTCH_HEALTH = 0.25F;
	/** ...and still standing this many ticks later. */
	static final int CLUTCH_TICKS = 200;
	static final int CLUTCH_HARDSHIP = 3;
	/** At most one clutch counts in this many ticks. */
	static final int CLUTCH_SPACING = 1200;
	/** Settled a score: killed a foe that had killed him. */
	static final int REMATCH_HARDSHIP = 2;
	/** Felled a Wither, an Ender Dragon, a Warden or an Elder Guardian. */
	static final int BOSS_HARDSHIP = 10;
	/** How recently he must have been hurt by a creature for it to count as a fight (ticks). */
	static final int IN_FIGHT_TICKS = 100;
	/** The breakthrough to Super comes at or below this share of his health, mid-fight. */
	static final float BREAKTHROUGH_HEALTH = 0.3F;
	static final float BREAKTHROUGH_HEAL = 10.0F;

	// ---- Rage: before he has ever gone Super, a beating can send him into a fury ----
	/** At or below this share of his health in a fight, the meter fills... */
	static final float RAGE_HEALTH = 0.25F;
	/** ...over this many ticks (it drains, slower, out of danger). */
	static final int RAGE_FILL = 80;
	static final int RAGE_DRAIN = 400;
	static final int RAGE_TICKS = 300;
	/** After Rage, this long before it can fill again. */
	static final int RAGE_COOLDOWN = 1200;
	/** In Rage: fists this much harder (on top), this much less damage taken, and techniques cost no ki. */
	static final double RAGE_FIST = 0.4;
	static final float RAGE_TOUGHNESS = 0.25F;
	static final int RAGE_HARDSHIP = 2;
	static final int RAGE_COLOR = 0xFF2A1A;

	private ZFighterBattle() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(ZFighterBattle::afterDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer hurt && source.getEntity() instanceof LivingEntity attacker && attacker != hurt
				&& Powers.has(hurt, ZFighterPower.INSTANCE)) {
				ZFighterTechniques.hurtBy(hurt, attacker);
			}
			if (damageTaken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || entity == player
				|| !Powers.has(player, ZFighterPower.INSTANCE)) {
				return;
			}
			ZFighterTraining.landed(player, ZFighterPower.live(player), damageTaken, entity.getMaxHealth());
			if (source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK)) {
				punched(player, entity);
			}
		});
	}

	/** How a foe is remembered: its kind of creature, or the player themselves. */
	static String foeKey(Entity entity) {
		return entity instanceof Player player ? "player:" + player.getUUID() : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	/** Whether {@code foe} beat him and the rematch is still on. */
	static boolean grudge(Player player, Entity foe) {
		return player.getAttachedOrElse(ZFighterPower.GRUDGES, List.of()).contains(foeKey(foe));
	}

	// ---------------------------------------------------------------- fists only

	/** A weapon: anything that would hit harder than a fist (its own attack damage, or a weapon's component). */
	static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.has(DataComponents.WEAPON)) {
			return true;
		}
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code player}'s swing is refused: a Z Fighter with a weapon in hand. Says so (now and then) on the server. */
	static boolean refusesAttack(Player player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || !isWeapon(player.getMainHandItem())) {
			return false;
		}
		if (player instanceof ServerPlayer server) {
			ZFighterPower.Live live = ZFighterPower.live(server);
			long now = server.level().getGameTime();
			if (now - live.lastRefusal > 40) {
				live.lastRefusal = now;
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fists_only").withStyle(ChatFormatting.GOLD));
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- damage

	/**
	 * In a rematch, the foe that beat him hits him for less and takes more from him (every hit, any kind). Trained
	 * Toughness takes its share off everything that hurts him.
	 */
	static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0.0F) {
			return damage;
		}
		Entity attacker = source.getEntity();
		if (victim instanceof ServerPlayer player && Powers.has(player, ZFighterPower.INSTANCE)) {
			damage *= 1.0F - ZFighterTraining.toughness(player);
			damage = ZFighterFusion.toughness(player, damage);
			ZFighterPower.Live live = ZFighterPower.live(player);
			damage = ZFighterGuard.guard(player, live, source, damage);
			if (damage <= 0.0F) {
				return 0.0F;
			}
			if (live.raging()) {
				damage *= 1.0F - RAGE_TOUGHNESS;
			}
			if (live.barrierTicks > 0) {
				if (source.is(DamageTypeTags.IS_PROJECTILE)) {
					return 0.0F;
				}
				if (source.is(DamageTypeTags.IS_EXPLOSION)) {
					damage *= 1.0F - ZFighterTechniques.BARRIER_BLAST_CUT;
				}
			}
			if (attacker != null && attacker != victim && grudge(player, attacker)) {
				damage *= 1.0F - FOE_RESIST;
			}
			return damage;
		}
		if (attacker instanceof ServerPlayer player && attacker != victim && Powers.has(player, ZFighterPower.INSTANCE)) {
			damage = ZFighterGuard.counter(player, source, damage);
			return grudge(player, victim) ? damage * (1.0F + FOE_BONUS) : damage;
		}
		return damage;
	}

	/** A bare-fisted punch landed: ki, the combo, and every fifth hit a finisher. */
	private static void punched(ServerPlayer player, LivingEntity target) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		live.combo = now - live.lastHit <= COMBO_WINDOW ? live.combo + 1 : 1;
		live.lastHit = now;
		live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + ZFighterPower.KI_PER_PUNCH);
		Mastery.used(player);
		if (live.combo % COMBO_FINISHER != 0 || !target.isAlive()) {
			return;
		}
		// the finisher: one heavy blow that sends it flying
		Vec3 look = player.getViewVector(1.0F);
		target.setInvulnerableTime(0);
		target.hurtServer(level, ZFighterKi.source(player), FINISHER_DAMAGE * ZFighterPower.FORM_KI[live.form]);
		ZFighterKi.push(target, look, 1.8, 0.45);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
		com.jaymospowers.core.Shakes.around(level, at, 12.0, 1.0F);
	}

	// ---------------------------------------------------------------- defeat, rematches, great beasts

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player && Powers.has(player, ZFighterPower.INSTANCE)) {
			defeated(player, source);
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player && player != entity && Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterTraining.killed(player, entity.getMaxHealth());
			if (grudge(player, entity)) {
				// the score is settled: the edge over this foe is spent
				List<String> grudges = new ArrayList<>(player.getAttachedOrElse(ZFighterPower.GRUDGES, List.of()));
				grudges.remove(foeKey(entity));
				player.setAttached(ZFighterPower.GRUDGES, grudges);
				ZFighterPower.addHardship(player, REMATCH_HARDSHIP);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rematch", entity.getDisplayName())
					.withStyle(ChatFormatting.GOLD));
			}
			EntityType<?> type = entity.getType();
			if (type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN) {
				ZFighterPower.addHardship(player, BOSS_HARDSHIP);
			}
		}
	}

	/** He fell. Whoever did it will never do it again, and he comes back stronger. */
	private static void defeated(ServerPlayer player, DamageSource source) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		if (live.flying) {
			ZFighterPower.endFlight(player, live);
		}
		ZFighterPower.forget(player);
		LivingEntity victor = victor(player, source);
		if (victor == null) {
			return;
		}
		String key = foeKey(victor);
		List<String> grudges = new ArrayList<>(player.getAttachedOrElse(ZFighterPower.GRUDGES, List.of()));
		if (!grudges.contains(key)) {
			grudges.add(key);
			player.setAttached(ZFighterPower.GRUDGES, grudges);
		}
		List<String> defeats = new ArrayList<>(player.getAttachedOrElse(ZFighterPower.DEFEATS, List.of()));
		if (defeats.contains(key)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_again", victor.getDisplayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			return;
		}
		// a new victor: the growth stays for good
		defeats.add(key);
		player.setAttached(ZFighterPower.DEFEATS, defeats);
		ZFighterPower.addHardship(player, DEFEAT_HARDSHIP);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_never_again", victor.getDisplayName())
			.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	private static @Nullable LivingEntity victor(ServerPlayer player, DamageSource source) {
		if (source.getEntity() instanceof LivingEntity living && living != player) {
			return living;
		}
		LivingEntity credit = player.getKillCredit();
		return credit != null && credit != player ? credit : null;
	}

	// ---------------------------------------------------------------- the brink, and the breakthrough

	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean inFight = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() <= IN_FIGHT_TICKS;
		float share = player.getHealth() / player.getMaxHealth();
		if (inFight && share <= CLUTCH_HEALTH) {
			if (live.clutchFrom < 0) {
				live.clutchFrom = now;
			}
		}
		if (live.clutchFrom >= 0 && now - live.clutchFrom >= CLUTCH_TICKS) {
			live.clutchFrom = -1L;
			if (now - live.lastClutchCredit >= CLUTCH_SPACING && !player.isCreative()) {
				live.lastClutchCredit = now;
				ZFighterPower.addHardship(player, CLUTCH_HARDSHIP);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_clutch").withStyle(ChatFormatting.GOLD));
			}
		}
		rage(player, live, inFight && share <= RAGE_HEALTH);
		if (inFight && share <= BREAKTHROUGH_HEALTH && ZFighterPower.hardship(player) >= SUPER_HARDSHIP && Fate.canAscend(player)) {
			breakthrough(player, live);
		}
	}

	/** Rage fills while he's taking a beating (only before he has ever gone Super), and bursts when full. */
	private static void rage(ServerPlayer player, ZFighterPower.Live live, boolean beaten) {
		if (live.raging()) {
			if (--live.rageTicks == 0) {
				live.rageCooldown = RAGE_COOLDOWN;
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
				ZFighterPower.refresh(player, live);
			} else if (live.rageTicks % 2 == 0) {
				ZFighterPower.aura(player, live, 3);
			}
			return;
		}
		if (live.rageCooldown > 0) {
			live.rageCooldown--;
			return;
		}
		if (Fate.isAscended(player) || player.isCreative()) {
			live.rage = 0.0F;
			return;
		}
		live.rage = beaten ? live.rage + 1.0F / RAGE_FILL : Math.max(0.0F, live.rage - 1.0F / RAGE_DRAIN);
		if (live.rage < 1.0F) {
			return;
		}
		live.rage = 0.0F;
		live.rageTicks = RAGE_TICKS;
		ZFighterPower.addHardship(player, RAGE_HARDSHIP);
		ServerLevel level = player.level();
		Vec3 centre = player.position();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ZFighterPower.SUPER_BURST_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			ZFighterKi.push(near, near.position().subtract(centre), 1.2, 0.3);
		}
		level.sendParticles(new DustParticleOptions(RAGE_COLOR, 2.0F), true, false, centre.x, centre.y + 1.0, centre.z, 50, 1.0, 1.0, 1.0, 0.2);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, centre.x, centre.y + 0.5, centre.z, 3, 0.8, 0.3, 0.8, 0.0);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rage").withStyle(ChatFormatting.RED));
		ZFighterPower.refresh(player, live);
	}

	/** Near death, with hardship enough behind him: the power ascends and he goes Super, healed and blazing. */
	private static void breakthrough(ServerPlayer player, ZFighterPower.Live live) {
		if (!Fate.ascend(player)) {
			return;
		}
		player.heal(BREAKTHROUGH_HEAL);
		live.ki = ZFighterPower.maxKi(player);
		live.clutchFrom = -1L;
		ZFighterPower.transform(player, live, 1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 0.8F);
	}
}
