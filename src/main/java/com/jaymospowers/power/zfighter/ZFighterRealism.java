package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fractures;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.gifted.Gifted;
import com.jaymospowers.core.wounds.Downed;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.60, Andy's NOEA picks) Realistic mode: a server setting that makes a Z Fighter's fights harsher. It is the line
 * {@code realistic_mode=true} in {@code config/jaymospowers-server.properties} (written, off, the first time the server
 * starts; read again at every server start; {@code /zfighter realism on|off} flips it for the running server and saves it).
 * Off, nothing here does anything. On, in any fight a Z Fighter is part of (he hits or is hit: a player, or a gifted villager
 * with the power, or one of the tyrants and champions):
 * <ul>
 * <li>a ki attack does {@link #KI_DAMAGE} times its damage and any other blow {@link #BLOW_DAMAGE} times, to both sides
 * (applied after the fists' cap and the toughness, so the hardest hitters hit hardest of all);</li>
 * <li>a Z Fighter's ki comes back at {@link #KI_REGEN} of its rate, and flight costs {@link #FLIGHT_COST} times the ki;</li>
 * <li>for {@link #FIGHT_TICKS} after a blow either way, natural healing is cut to {@link #HEALTH_REGEN} of itself (a heal of
 * more than {@link #HEAL_STEP} at once, a golden apple or a potion, is left alone);</li>
 * <li>a blow that does {@link #BREAK_MIN} or more may break a limb of the one it lands on: {@link #BREAK_BASE} plus
 * {@link #BREAK_PER_DAMAGE} per point over, no more than {@link #BREAK_MAX} (the wounds' own limb damage rises with the damage
 * itself, and a harder blow on a failing limb takes it off more often).</li>
 * </ul>
 */
public final class ZFighterRealism {
	static final String FILE = "jaymospowers-server.properties";
	static final String SETTING = "realistic_mode";
	/** (0.17.62) {@code afterlife=true} (the default): a Z Fighter who dies wakes in the Other World ({@link ZFighterAfterlife}). */
	static final String AFTERLIFE_SETTING = "afterlife";

	/** What a ki attack and any other blow do (multiplied). */
	static final float KI_DAMAGE = 2.0F;
	static final float BLOW_DAMAGE = 1.6F;
	/** The share of a Z Fighter's ki regeneration that is left, and the multiple that flight costs. */
	static final float KI_REGEN = 0.5F;
	static final float FLIGHT_COST = 1.75F;
	/** How long after a blow either way a Z Fighter counts as in a fight (ticks), the share of natural healing left, and the biggest heal that counts as natural. */
	static final int FIGHT_TICKS = 200;
	static final float HEALTH_REGEN = 0.4F;
	static final float HEAL_STEP = 1.5F;
	/** A blow of at least this damage may break a limb: this chance, plus this per point over, at most {@code BREAK_MAX}. */
	static final float BREAK_MIN = 3.0F;
	static final float BREAK_BASE = 0.08F;
	static final float BREAK_PER_DAMAGE = 0.02F;
	static final float BREAK_MAX = 0.4F;

	private static boolean realistic;
	private static boolean afterlife = true;
	private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

	private ZFighterRealism() {
	}

	static void init() {
		load();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_HEALTH.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!realistic) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				healing(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (realistic && !blocked && damageTaken >= BREAK_MIN && source.getEntity() instanceof LivingEntity attacker && attacker != entity
				&& fight(entity, source) && entity.isAlive()) {
				injure(entity, attacker, damageTaken);
			}
		});
	}

	/** Whether realistic mode is on. */
	public static boolean on() {
		return realistic;
	}

	/** (0.17.62) Whether a Z Fighter's death takes him to the Other World ({@code afterlife} in the server properties; on by default). */
	public static boolean afterlife() {
		return afterlife;
	}

	/** Turns it on or off for the running server and saves the setting. */
	static void set(boolean on) {
		realistic = on;
		save();
	}

	/** The multiple a Z Fighter's ki regeneration is taken by (1 when off). */
	public static float kiRegen() {
		return realistic ? KI_REGEN : 1.0F;
	}

	/** The multiple a Z Fighter's flight ki is taken by (1 when off). */
	public static float flightCost() {
		return realistic ? FLIGHT_COST : 1.0F;
	}

	/** Part of {@link ZFighterBattle#modifyDamage}: a fight's blows and ki, harder when realistic mode is on. */
	static float damage(LivingEntity victim, DamageSource source, float damage) {
		if (!realistic || damage <= 0.0F || !fight(victim, source)) {
			return damage;
		}
		return damage * (source.is(ZFighterKi.KI) ? KI_DAMAGE : BLOW_DAMAGE);
	}

	/** Whether this hit is part of a Z Fighter's fight: he hits, or is hit (by any blow of a living thing's, not a fall or fire). */
	static boolean fight(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == victim) {
			return false;
		}
		return zFighter(victim) || zFighter(attacker);
	}

	private static boolean zFighter(Entity entity) {
		if (entity instanceof Player player) {
			return Powers.has(player, ZFighterPower.INSTANCE);
		}
		return entity instanceof ZFighterRogue || "zfighter".equals(Gifted.power(entity));
	}

	/** A hard blow may break a limb of the one it lands on. */
	private static void injure(LivingEntity victim, LivingEntity attacker, float damage) {
		if (victim instanceof Player player && (player.isCreative() || player.isSpectator()) || !Dismember.canLoseParts(victim) || Downed.isDowned(victim)) {
			return;
		}
		float chance = Math.min(BREAK_MAX, BREAK_BASE + BREAK_PER_DAMAGE * (damage - BREAK_MIN));
		if (victim.getRandom().nextFloat() < chance) {
			Fractures.breakBone(victim, Dismember.aimedPart(attacker, victim), attacker);
		}
	}

	/** A Z Fighter hurt lately heals only part of what natural regeneration gives. */
	private static void healing(ServerPlayer player) {
		float health = player.getHealth();
		Float last = LAST_HEALTH.put(player.getUUID(), health);
		if (last == null || health <= last || health - last > HEAL_STEP || !Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		int now = player.tickCount;
		boolean fighting = now - player.getLastHurtByMobTimestamp() < FIGHT_TICKS || now - player.getLastHurtMobTimestamp() < FIGHT_TICKS;
		if (fighting) {
			float kept = last + (health - last) * HEALTH_REGEN;
			player.setHealth(kept);
			LAST_HEALTH.put(player.getUUID(), kept);
		}
	}

	// ---------------------------------------------------------------- the setting

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static void load() {
		Path file = file();
		try {
			if (Files.exists(file)) {
				Properties properties = new Properties();
				try (Reader reader = Files.newBufferedReader(file)) {
					properties.load(reader);
				}
				realistic = "true".equalsIgnoreCase(properties.getProperty(SETTING, "false").trim());
				afterlife = !"false".equalsIgnoreCase(properties.getProperty(AFTERLIFE_SETTING, "true").trim());
				if (properties.getProperty(AFTERLIFE_SETTING) == null) {
					save();
				}
			} else {
				realistic = false;
				afterlife = true;
				save();
			}
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't read {}", file, e);
		}
	}

	private static void save() {
		Path file = file();
		Properties properties = new Properties();
		properties.setProperty(SETTING, Boolean.toString(realistic));
		properties.setProperty(AFTERLIFE_SETTING, Boolean.toString(afterlife));
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				properties.store(writer, "Jaymo's Powers, server settings. realistic_mode=true makes Z Fighter fights harsher: ki x"
					+ KI_DAMAGE + " and blows x" + BLOW_DAMAGE + " damage to both sides, ki regen x" + KI_REGEN + ", flight ki x" + FLIGHT_COST
					+ ", natural healing x" + HEALTH_REGEN + " in a fight, and hard blows may break limbs (docs/Abilities.md, Realistic mode). "
					+ "afterlife=false stops a Z Fighter who dies from waking in the Other World (default true).");
			}
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't write {}", file, e);
		}
	}
}
