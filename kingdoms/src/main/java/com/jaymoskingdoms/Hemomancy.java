package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.231) Blood magic, the ninth Sorcerer element ({@link ForbiddenArts}). It's paid in blood, not mana: first from
 * the blood it has drunk ({@link #pool}), then from the caster's own health; casting too deep opens a cut on them (Jaymo's
 * Powers' wounds). It feeds on the bleeding and bursts their cuts.
 * <ul>
 * <li><b>The Blood Sorcerer:</b> {@code J} imbues their blade (four health), and every blow it lands opens a cut; a burst
 * bursts every cut on it at once. <b>Drink the bleeding:</b> anything bleeding near them feeds their pool. Sneak and
 * {@code J}: at the bleeding, <b>Blood Grip</b> (dragged to you, a mob's weapon torn from its hand, a player weakened);
 * at the living, a blood bolt that opens a cut; at nothing, <b>Hemorrhage</b> (every cut round you bursts). Bleeding
 * hurts them half as much ({@link ClassPerks}).</li>
 * <li><b>Spells</b> for scrolls and staffs ({@link Arcana#cast}; a staff casts them for blood too): <b>Hemorrhage</b>,
 * <b>Blood Grip</b>, and <b>Frenzy</b> (a cut on you for strength a while, and every blow you land heals you a little).</li>
 * <li><b>Vampires</b> ({@link VampirePlayers}) cast it cheaper and harder, but every cast stokes their blood fever.</li>
 * <li>A camp's <b>blood witch</b> ({@link Sorcery#BLOOD_WITCH}) casts the same: bolts that cut, grips, hemorrhages, and
 * she drinks the bleeding round her to heal.</li>
 * </ul>
 */
public final class Hemomancy {
	/** What each costs in health: the Sorcerer's imbue and bolt; Hemorrhage, Blood Grip, Frenzy (the Sorcerer's keys and the spells). */
	static final Map<String, Float> COST = Map.of("imbue", 4.0F, "bolt", 2.0F, "hemorrhage", 4.0F, "blood_grip", 3.0F, "frenzy", 6.0F);
	/** Never casts you below this much health. */
	static final float MIN_LEFT = 2.0F;
	/** Below this share of health, a cast opens a cut on you. */
	static final float CUT_BELOW = 0.5F;
	/** The pool: the most it holds; what each bleeding cut near feeds it a second (and the most a second); how near. */
	static final float POOL_MAX = 20.0F, DRINK_PER_CUT = 0.25F, DRINK_MOST = 2.0F;
	static final double DRINK_RANGE = 8.0;
	/** A vampire's costs and power. */
	static final float VAMPIRE_COST = 0.75F, VAMPIRE_POWER = 1.5F, VAMPIRE_FEVER = 3.0F;
	/** Hemorrhage: how far, how many bleed ticks each cut bursts for. */
	static final double HEMORRHAGE_RANGE = 8.0;
	static final float HEMORRHAGE_TIMES = 4.0F;
	/** A blood burst on a blade's blow or a bolt: how many bleed ticks it bursts the target's cuts for. */
	static final float BURST_TIMES = 3.0F;
	/** Blood Grip: how hard it drags, its weakness and slow on a player (ticks). */
	static final double GRIP_PULL = 1.1;
	static final int GRIP_WEAKEN = 80;
	/** Frenzy: how long (ticks), the heal per blow. */
	static final int FRENZY_TICKS = 20 * 30;
	static final float FRENZY_HEAL = 1.0F;
	/** The blood witch: her bolt, how often she drinks (ticks) and what each bleeding foe near gives her, her spells' cooldowns. */
	static final float WITCH_BOLT = 4.0F, WITCH_DRINK = 1.0F;
	static final int WITCH_DRINK_EVERY = 40, WITCH_HEMORRHAGE_COOLDOWN = 20 * 12, WITCH_GRIP_COOLDOWN = 20 * 10;

	static final int CRIMSON = 0x8A0F14, DARK = 0x4A0508;
	static final DustParticleOptions BLOOD_MOTE = new DustParticleOptions(CRIMSON, 1.0F), DARK_MOTE = new DustParticleOptions(DARK, 1.2F);

	/** The blood a Sorcerer has drunk, by player. */
	private static final Map<UUID, Float> POOL = new HashMap<>();
	/** Frenzied until (game time), by player. */
	private static final Map<UUID, Long> FRENZY = new HashMap<>();

	private Hemomancy() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Hemomancy::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POOL.clear();
			FRENZY.clear();
		});
		// Frenzy: your blows heal you
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player && damageTaken > 0.0F
				&& !blocked && frenzied(player)) {
				player.heal(FRENZY_HEAL);
			}
		});
	}

	// ---- who and what it costs -----------------------------------------------------------------------------------------

	/** A Blood Sorcerer. */
	static boolean bloodMage(Player player) {
		return Classes.is(player, PlayerClass.SORCERER) && "blood".equals(Classes.of(player).option());
	}

	static float pool(Player player) {
		return POOL.getOrDefault(player.getUUID(), 0.0F);
	}

	static float cost(String what) {
		return COST.getOrDefault(what, 3.0F);
	}

	/** How hard {@code player}'s blood magic hits: a vampire's harder. */
	static float power(Player player) {
		return VampirePlayers.vampire(player) ? VAMPIRE_POWER : 1.0F;
	}

	/**
	 * Pays {@code cost} health for a blood spell: the pool first, then the body. False (and a word on the screen) if it
	 * would leave them under {@link #MIN_LEFT}. Casting deep opens a cut; a vampire's fever climbs.
	 */
	static boolean pay(ServerPlayer player, float cost) {
		if (player.isCreative()) {
			return true;
		}
		boolean vampire = VampirePlayers.vampire(player);
		if (vampire) {
			cost *= VAMPIRE_COST;
		}
		float pool = pool(player);
		float fromPool = Math.min(pool, cost);
		float rest = cost - fromPool;
		if (rest > 0.0F && player.getHealth() - rest < MIN_LEFT) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.too_little").withStyle(ChatFormatting.DARK_RED));
			return false;
		}
		POOL.put(player.getUUID(), pool - fromPool);
		if (rest > 0.0F) {
			player.setHealth(player.getHealth() - rest);
			if (player.getHealth() < player.getMaxHealth() * CUT_BELOW && PowersCompat.cut(player)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.too_deep").withStyle(ChatFormatting.DARK_RED));
			}
		}
		if (vampire) {
			BloodFever.stoke(player, VAMPIRE_FEVER);
		}
		return true;
	}

	/** Bleeding now (Jaymo's Powers' cuts). */
	static boolean bleeding(LivingEntity e) {
		return PowersCompat.cuts(e) > 0;
	}

	// ---- the blade, the bolt and the keys -------------------------------------------------------------------------------

	/** A blood blade's blow: a cut. */
	static void bladeBlow(LivingEntity target) {
		PowersCompat.cut(target);
	}

	/** A blood burst on {@code target} (a blade's, or a bolt's): a cut, and every cut on it bursts. */
	static void burst(ServerLevel level, LivingEntity by, LivingEntity target) {
		PowersCompat.cut(target);
		PowersCompat.hemorrhage(target, by, BURST_TIMES * (by instanceof Player p ? power(p) : 1.0F));
		splash(level, target, 10);
	}

	/** Sneak and {@code J}: grip the bleeding, Hemorrhage at nothing; false for a bolt ({@link Spellcraft} throws it). */
	static boolean key(ServerPlayer player, ServerLevel level) {
		LivingEntity target = Arcana.sighted(player);
		if (target != null && !bleeding(target)) {
			return false;
		}
		net.minecraft.world.item.ItemStack key = Arcana.BONE_STAFF.getDefaultInstance();
		if (player.getCooldowns().isOnCooldown(key)) {
			return true;
		}
		boolean cast = target != null ? grip(level, player, target, power(player), true) : hemorrhage(level, player, power(player), true);
		if (cast) {
			// (a scroll's or a staff's cast is counted where it's read or cast, Spellcraft.afterCast)
			ForbiddenArts.cast(player, level, true, 0);
			player.getCooldowns().addCooldown(key, Spellcraft.BOLT_COOLDOWN);
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
		return true;
	}

	// ---- the spells ----------------------------------------------------------------------------------------------------

	/** Hemorrhage: every bleeding thing round you (not you) has its cuts burst. {@code pay}: the caster pays here (a key or a staff's own cost aside). */
	static boolean hemorrhage(ServerLevel level, Player player, float power, boolean pay) {
		List<LivingEntity> bleeding = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(HEMORRHAGE_RANGE),
			e -> e != player && e.isAlive() && !Thralls.refuses(player, e) && bleeding(e));
		if (bleeding.isEmpty()) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.none_bleeding"));
			}
			return false;
		}
		if (pay && player instanceof ServerPlayer sp && !pay(sp, cost("hemorrhage"))) {
			return false;
		}
		for (LivingEntity e : bleeding) {
			Sorcery.beam(level, player, e.getBoundingBox().getCenter(), BLOOD_MOTE);
			PowersCompat.hemorrhage(e, player, HEMORRHAGE_TIMES * power);
			splash(level, e, 14);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** Blood Grip: who you look at (bleeding or not, from a scroll) is dragged to you; a mob drops its weapon, a player is weakened. */
	static boolean grip(ServerLevel level, Player player, LivingEntity target, float power, boolean pay) {
		if (pay && player instanceof ServerPlayer sp && !pay(sp, cost("blood_grip"))) {
			return false;
		}
		Sorcery.beam(level, player, target.getBoundingBox().getCenter(), DARK_MOTE);
		Vec3 to = player.position().subtract(target.position());
		Vec3 pull = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : to.normalize().scale(GRIP_PULL * power);
		target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.35, pull.z));
		target.syncVelocity = true;
		if (target instanceof Player) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (GRIP_WEAKEN * power), 0), player);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (GRIP_WEAKEN * power), 1), player);
		} else if (target instanceof Mob mob) {
			ItemStack held = mob.getMainHandItem();
			if (!held.isEmpty()) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				mob.spawnAtLocation(level, held);
			}
		}
		splash(level, target, 8);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.2F);
		return true;
	}

	/** Blood Grip off a scroll or a staff: at who you look at. */
	static boolean gripSpell(ServerLevel level, Player player, float power) {
		LivingEntity target = Arcana.sighted(player);
		return target != null && grip(level, player, target, power, false);
	}

	/** Frenzy: a cut on you for strength a while, and your blows heal you. */
	static boolean frenzy(ServerLevel level, Player player, float power) {
		if (!(player instanceof ServerPlayer sp)) {
			return false;
		}
		int ticks = (int) (FRENZY_TICKS * power);
		PowersCompat.cut(sp);
		sp.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0), sp);
		FRENZY.put(sp.getUUID(), level.getGameTime() + ticks);
		splash(level, sp, 16);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6F, 1.3F);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.frenzy").withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	static boolean frenzied(Player player) {
		Long until = FRENZY.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	static void splash(ServerLevel level, LivingEntity at, int count) {
		level.sendParticles(BLOOD_MOTE, at.getX(), at.getY(0.6), at.getZ(), count, 0.3, 0.4, 0.3, 0.05);
		level.sendParticles(PowersCompat.blood(CRIMSON, 1.0F), at.getX(), at.getY(0.5), at.getZ(), count / 2, 0.25, 0.3, 0.25, 0.1);
	}

	// ---- the blood witch -----------------------------------------------------------------------------------------------

	/** Her spell at {@code target} ({@link Sorcery#cast}): a hemorrhage when foes near bleed, a grip, else a bolt that cuts. */
	static void witchCast(ServerLevel level, Sorcery.Caster caster, LivingEntity target, int[] timers) {
		// ((0.7.291) one of our folk, or a villager in the office: Casters)
		LivingEntity witch = caster.mob();
		Mana.ripple(witch, Mana.HARM);
		if (timers[Sorcery.T_SPIKES] <= 0 && bleeding(target)) {
			timers[Sorcery.T_SPIKES] = WITCH_HEMORRHAGE_COOLDOWN;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, witch.getBoundingBox().inflate(HEMORRHAGE_RANGE),
				e -> caster.foe(e) && bleeding(e))) {
				Sorcery.beam(level, witch, e.getBoundingBox().getCenter(), BLOOD_MOTE);
				PowersCompat.hemorrhage(e, witch, HEMORRHAGE_TIMES);
				splash(level, e, 12);
			}
			level.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4F, 0.8F);
			return;
		}
		if (timers[Sorcery.T_HEX] <= 0 && witch.distanceTo(target) > Sorcery.CLOSE) {
			timers[Sorcery.T_HEX] = WITCH_GRIP_COOLDOWN;
			Sorcery.beam(level, witch, target.getBoundingBox().getCenter(), DARK_MOTE);
			Vec3 pull = witch.position().subtract(target.position()).normalize().scale(GRIP_PULL);
			target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.35, pull.z));
			target.syncVelocity = true;
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, GRIP_WEAKEN, 0), witch);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2F, 1.2F);
			return;
		}
		Sorcery.beam(level, witch, target.getBoundingBox().getCenter(), BLOOD_MOTE);
		if (target.hurtServer(level, level.damageSources().indirectMagic(witch, witch), WITCH_BOLT)) {
			PowersCompat.cut(target);
			splash(level, target, 6);
		}
		level.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 0.5F);
	}

	/** Her own tick ({@link Sorcery#tick}): she drinks from the bleeding foes round her. */
	static void witchTick(ServerLevel level, Sorcery.Caster caster) {
		LivingEntity witch = caster.mob();
		if (witch.tickCount % WITCH_DRINK_EVERY != 0 || witch.getHealth() >= witch.getMaxHealth()) {
			return;
		}
		int drank = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, witch.getBoundingBox().inflate(DRINK_RANGE),
			e -> caster.foe(e) && bleeding(e))) {
			Sorcery.beam(level, e, witch.getBoundingBox().getCenter(), BLOOD_MOTE);
			drank++;
		}
		if (drank > 0) {
			witch.heal(WITCH_DRINK * drank);
		}
	}

	// ---- every tick: the Blood Sorcerers drink -------------------------------------------------------------------------

	private static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		FRENZY.values().removeIf(until -> until < Factions.level(server).getGameTime());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!bloodMage(player) || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			float pool = pool(player);
			if (pool >= POOL_MAX) {
				continue;
			}
			float drank = 0.0F;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DRINK_RANGE), e -> e != player && e.isAlive())) {
				int cuts = PowersCompat.cuts(e);
				if (cuts > 0) {
					drank += cuts * DRINK_PER_CUT;
					Vec3 from = e.getBoundingBox().getCenter();
					Vec3 mid = from.add(player.getEyePosition().subtract(from).scale(0.5));
					level.sendParticles(BLOOD_MOTE, mid.x, mid.y, mid.z, 2, 0.15, 0.15, 0.15, 0.0);
				}
			}
			if (drank > 0.0F) {
				POOL.put(player.getUUID(), Math.min(POOL_MAX, pool + Math.min(DRINK_MOST, drank)));
			}
		}
	}
}
