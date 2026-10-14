package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
 * <li><b>No foe fells him twice.</b> Whatever kills him (each kind of creature; each player by name) is remembered for
 * good: from then on it does {@link #FOE_RESIST} less damage to him and takes {@link #FOE_BONUS} more from him. Every new
 * victor also makes him stronger for good (more ki, harder fists; see {@link ZFighterPower#zenkai}).</li>
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
	/** Killed a foe that once killed him. */
	static final int REMATCH_HARDSHIP = 2;
	/** Felled a Wither, an Ender Dragon, a Warden or an Elder Guardian. */
	static final int BOSS_HARDSHIP = 10;
	/** How recently he must have been hurt by a creature for it to count as a fight (ticks). */
	static final int IN_FIGHT_TICKS = 100;
	/** The breakthrough to Super comes at or below this share of his health, mid-fight. */
	static final float BREAKTHROUGH_HEALTH = 0.3F;
	static final float BREAKTHROUGH_HEAL = 10.0F;

	private ZFighterBattle() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(ZFighterBattle::afterDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
				&& source.is(DamageTypes.PLAYER_ATTACK) && entity != player && Powers.has(player, ZFighterPower.INSTANCE)) {
				punched(player, entity);
			}
		});
	}

	/** How a foe is remembered: its kind of creature, or the player themselves. */
	static String foeKey(Entity entity) {
		return entity instanceof Player player ? "player:" + player.getUUID() : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	static boolean beatenBy(Player player, Entity foe) {
		return player.getAttachedOrElse(ZFighterPower.DEFEATS, List.of()).contains(foeKey(foe));
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

	/** A foe that beat him once hits him for less, and takes more from him (every hit, any kind). */
	static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == victim || damage <= 0.0F) {
			return damage;
		}
		if (victim instanceof ServerPlayer player && Powers.has(player, ZFighterPower.INSTANCE) && beatenBy(player, attacker)) {
			return damage * (1.0F - FOE_RESIST);
		}
		if (attacker instanceof ServerPlayer player && Powers.has(player, ZFighterPower.INSTANCE) && beatenBy(player, victim)) {
			return damage * (1.0F + FOE_BONUS);
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
		target.hurtServer(level, ZFighterKi.source(player), FINISHER_DAMAGE * (live.superOn ? ZFighterPower.SUPER_KI_POWER : 1.0F));
		ZFighterKi.push(target, look, 1.8, 0.45);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	// ---------------------------------------------------------------- defeat, rematches, great beasts

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player && Powers.has(player, ZFighterPower.INSTANCE)) {
			defeated(player, source);
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player && player != entity && Powers.has(player, ZFighterPower.INSTANCE)) {
			if (beatenBy(player, entity)) {
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
		List<String> defeats = new ArrayList<>(player.getAttachedOrElse(ZFighterPower.DEFEATS, List.of()));
		if (defeats.contains(key)) {
			return;
		}
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
		if (inFight && share <= BREAKTHROUGH_HEALTH && ZFighterPower.hardship(player) >= SUPER_HARDSHIP && Fate.canAscend(player)) {
			breakthrough(player, live);
		}
	}

	/** Near death, with hardship enough behind him: the power ascends and he goes Super, healed and blazing. */
	private static void breakthrough(ServerPlayer player, ZFighterPower.Live live) {
		if (!Fate.ascend(player)) {
			return;
		}
		player.heal(BREAKTHROUGH_HEAL);
		live.ki = ZFighterPower.maxKi(player);
		live.clutchFrom = -1L;
		ZFighterPower.enterSuper(player, live);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 0.8F);
	}
}
