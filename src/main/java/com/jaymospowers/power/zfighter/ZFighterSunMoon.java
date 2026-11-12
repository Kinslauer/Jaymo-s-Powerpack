package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;

/**
 * Strength from the sky, two lineages' traits (the lore ties the sun to the Verdant, "fed by the sun", and the moon to the
 * Warrior-born, whose great ape wakes under the full moon; the Emberborn are fire and frost, and the Dawnborn and
 * Nightborn, who already carry the day and night in Jaymo's Kingdoms, are not Z Fighter lineages):
 * <ul>
 * <li><b>Verdant</b>: his fists hit {@link #SUN_BONUS} harder in direct sunlight (by day, open sky, no rain), and
 * {@link #NIGHT_PENALTY} softer at night.</li>
 * <li><b>Warrior-born</b>: {@link #MOON_BONUS} harder under the night sky (open sky; {@link #FULL_MOON_BONUS} more under a
 * full moon), and no weaker for the day.</li>
 * <li><b>Drinking the sky</b>: sneak, standing still, face tipped straight up under open sky for {@link #DRINK_TICKS}
 * (a Verdant by day, a Warrior-born by night, not as the ape): strength for {@link #BOOST_TICKS}, a Verdant mends
 * ({@link #REGEN_TICKS}) and a Warrior-born is quick, and a share of his ki comes back. Again after {@link #COOLDOWN}.</li>
 * </ul>
 */
final class ZFighterSunMoon {
	// ---- tuning ----
	/** The passive: a share of his fists' damage. */
	static final double SUN_BONUS = 0.12, NIGHT_PENALTY = -0.08, MOON_BONUS = 0.10, FULL_MOON_BONUS = 0.05;
	/** The passive is read off the sky this often (ticks). */
	static final int READ_EVERY = 20;
	/** Facing straight up: at least this far (degrees, pitch is negative looking up). */
	static final float LOOK_UP = 75.0F;
	/** Held still sneaking this long to drink (ticks), the boost's length, a Verdant's mending, the cooldown. */
	static final int DRINK_TICKS = 60, BOOST_TICKS = 600, REGEN_TICKS = 200, COOLDOWN = 2400;
	/** A share of his ceiling in ki that comes back. */
	static final float KI_SHARE = 0.25F;
	static final int SUN_COLOR = 0xFFE27A, MOON_COLOR = 0xBFD4FF;
	/** Moved less than this a tick (blocks) is standing still. */
	static final double STILL = 0.02;

	private static final Identifier BONUS_ID = JaymosPowers.id("zfighter_sky_strength");

	private static final Map<UUID, Long> DRINKING = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, Vec3> LAST = new HashMap<>();

	private ZFighterSunMoon() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DRINKING.clear();
			READY.clear();
			LAST.clear();
		});
	}

	static void forget(ServerPlayer player) {
		DRINKING.remove(player.getUUID());
		READY.remove(player.getUUID());
		LAST.remove(player.getUUID());
	}

	/** Direct sunlight: the overworld by day, open sky overhead, not raining. */
	static boolean sunlit(ServerPlayer player) {
		ServerLevel level = player.level();
		return level.dimension() == Level.OVERWORLD && level.isBrightOutside() && !level.isRaining()
			&& level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/** Under the night sky: the overworld in the dark, open sky overhead. */
	static boolean moonlit(ServerPlayer player) {
		ServerLevel level = player.level();
		return level.dimension() == Level.OVERWORLD && level.isDarkOutside() && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	private static boolean fullMoon(ServerLevel level) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON;
	}

	/** Every tick, per Z Fighter, from {@link ZFighterRaces#tick}. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, int max) {
		int lineage = ZFighterLineage.of(player);
		if (lineage != ZFighterLineage.VERDANT && lineage != ZFighterLineage.WARRIOR) {
			if (player.tickCount % READ_EVERY == 0) {
				set(player, 0.0);
			}
			return;
		}
		ServerLevel level = player.level();
		if (player.tickCount % READ_EVERY == 0) {
			double bonus = 0.0;
			if (lineage == ZFighterLineage.VERDANT) {
				bonus = sunlit(player) ? SUN_BONUS : level.dimension() == Level.OVERWORLD && level.isDarkOutside() ? NIGHT_PENALTY : 0.0;
			} else if (moonlit(player)) {
				bonus = MOON_BONUS + (fullMoon(level) ? FULL_MOON_BONUS : 0.0);
			}
			set(player, bonus);
		}
		drink(player, live, max, lineage == ZFighterLineage.VERDANT);
	}

	private static void set(ServerPlayer player, double bonus) {
		AttributeInstance fists = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (fists == null) {
			return;
		}
		AttributeModifier now = fists.getModifier(BONUS_ID);
		if (bonus == 0.0) {
			if (now != null) {
				fists.removeModifier(BONUS_ID);
			}
		} else if (now == null || now.amount() != bonus) {
			fists.addOrUpdateTransientModifier(new AttributeModifier(BONUS_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** The gesture: sneaking, still, face up to the right sky. */
	private static void drink(ServerPlayer player, ZFighterPower.Live live, int max, boolean sun) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		Vec3 last = LAST.put(id, player.position());
		boolean still = last != null && last.subtract(player.position()).multiply(1.0, 0.0, 1.0).length() < STILL;
		boolean able = player.isShiftKeyDown() && still && player.onGround() && player.getXRot() <= -LOOK_UP && !ZFighterLineage.ape(player)
			&& (sun ? sunlit(player) : moonlit(player));
		if (!able) {
			DRINKING.remove(id);
			return;
		}
		long now = level.getGameTime();
		if (now < READY.getOrDefault(id, 0L)) {
			return;
		}
		Long since = DRINKING.get(id);
		int color = sun ? SUN_COLOR : MOON_COLOR;
		if (since == null) {
			DRINKING.put(id, now);
			MagicFx.orb(level, player.getEyePosition().add(0.0, 0.6, 0.0), 1.2F, color, DRINK_TICKS);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, sun ? 1.4F : 0.8F);
			return;
		}
		long held = now - since;
		if (held % 4 == 0) {
			// light running down out of the sky into his face
			Vec3 face = player.getEyePosition();
			level.sendParticles(MagicFx.mote(color, 1.2F), face.x, face.y + 2.5, face.z, 2, 0.5, 1.2, 0.5, 0.0);
			level.sendParticles(MagicFx.mote(color, 0.8F), face.x, face.y + 0.4, face.z, 2, 0.2, 0.2, 0.2, 0.0);
		}
		if (held < DRINK_TICKS) {
			return;
		}
		DRINKING.remove(id);
		READY.put(id, now + COOLDOWN);
		boolean full = !sun && fullMoon(level);
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BOOST_TICKS, full ? 1 : 0, false, true));
		player.addEffect(sun ? new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0, false, true)
			: new MobEffectInstance(MobEffects.SPEED, BOOST_TICKS, 0, false, true));
		live.ki = Math.min(max, live.ki + max * KI_SHARE);
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		MagicFx.blast(level, at, 2.0F, color);
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 3.0F, color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, sun ? 1.5F : 0.9F);
		player.sendOverlayMessage(Component.translatable(sun ? "message.jaymospowers.zfighter_brawl.sun_drunk" : "message.jaymospowers.zfighter_brawl.moon_drunk")
			.withStyle(ChatFormatting.YELLOW));
	}
}
