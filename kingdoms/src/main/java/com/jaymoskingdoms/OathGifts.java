package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.jspecify.annotations.Nullable;

/**
 * The Paladin's tree (class trees, docs/ClassTrees-Plan.md). {@link Paladins} keeps the starting move (Lay on Hands, its
 * pool growing with the Paladin's level) and asks here for the rest:
 * <ul>
 * <li><b>Deep Wells</b> (3): a bigger pool for the hands and a smite more a day, each rank.</li>
 * <li><b>Smite</b> (3) and the <b>Aura</b> (8): today's, moved into the tree (kept by Paladins who had them).</li>
 * <li><b>Shield of Faith</b> (5): sneak and the key on a friend: a ward, paid from the hands' pool, soaks the next few blows on
 * them for a minute.</li>
 * <li><b>The oath's own</b> (10): Mercy's <i>Spare the Fallen</i>, a foe you bring down instead of killing heals you; Justice's
 * <i>Judgment</i>, a known lawbreaker takes half again from your smites; Honour's <i>Challenge</i>, the key on a foe calls it out:
 * it fights only you, and your blows on it hit harder.</li>
 * <li><b>Divine Sense</b> (12): the key with no harm to mend: the undead and fiends about glow to you through the walls a few
 * seconds.</li>
 * <li><b>Aura of Courage</b> or <b>Aura of Warding</b> (15): friends near you know no fear; or take less from spells.</li>
 * <li><b>Vigil</b> (20): a prayer at a shrine by night fills the smites and the hands, and makes them and the aura stronger
 * until the next sunset.</li>
 * </ul>
 */
public final class OathGifts {
	/** Deep Wells: health a rank added to the hands' pool. */
	static final float WELLS_HANDS = 6.0F;
	/** Shield of Faith: its price from the pool, the hits it soaks, how much of each, and how long it holds (ticks). */
	static final int SHIELD_COST = 4, SHIELD_HITS = 3, SHIELD_TICKS = 20 * 60;
	static final float SHIELD_SOAK = 4.0F;
	/** Spare the Fallen: the health a foe brought down gives back. */
	static final float SPARE_HEAL = 6.0F;
	/** Judgment: a smite on a lawbreaker, this many times over. */
	static final float JUDGMENT = 1.5F;
	/** Challenge: how far it's called (blocks), how long it holds (ticks), and the blows on it. */
	static final double CHALLENGE_REACH = 16.0;
	static final int CHALLENGE_TICKS = 20 * 30;
	static final float CHALLENGE_HIT = 0.25F;
	/** Divine Sense: how far (blocks), how long the glow (ticks), and the wait (ticks). */
	static final double SENSE_RANGE = 32.0;
	static final int SENSE_TICKS = 100, SENSE_WAIT = 20 * 20;
	/** Aura of Warding: the share of a spell a friend near takes. */
	static final float WARDING_TAKEN = 0.7F;
	/** Vigil: how much stronger the smite, the pool and the aura are. */
	static final float VIGIL = 1.5F;

	public static final String WELLS = "pal_wells", SMITE = "pal_smite", SHIELD = "pal_shield", AURA = "pal_aura", OATH = "pal_oath",
		SENSE = "pal_sense", COURAGE = "pal_courage", WARDING = "pal_warding", VIGIL_KEY = "pal_vigil";

	/** Until when (game time) a Vigil holds. Saved, kept through death. */
	static final AttachmentType<Long> VIGIL_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("paladin_vigil"),
		b -> b.persistent(Codec.LONG).copyOnDeath());

	/** A Shield of Faith: its Paladin, the hits left, until when. */
	private record Ward(UUID paladin, int hits, long until) {
	}

	private static final Map<LivingEntity, Ward> WARDS = new HashMap<>();
	/** A Challenge: Paladin → the foe called out, and until when. */
	/** Who was already down when the blow now landing was struck (entity ids; Mercy heals only for the blow that fells). */
	private static final Set<Integer> ALREADY_DOWN = new HashSet<>();

	private record Called(Mob foe, long until) {
	}

	private static final Map<UUID, Called> CALLED = new HashMap<>();
	private static final Map<UUID, Long> SENSED = new HashMap<>();
	/** Foes already spared (so one going down heals once). */
	private static final Map<Integer, Long> SPARED = new HashMap<>();

	private OathGifts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OathGifts::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (Downed.isDowned(entity)) {
				ALREADY_DOWN.add(entity.getId());
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> spare(entity, source));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WARDS.clear();
			CALLED.clear();
			SENSED.clear();
			SPARED.clear();
		});
	}

	static boolean has(Player player, String key) {
		return Paladins.keeping(player) && ClassTrees.has(player, key) > 0;
	}

	/** A Vigil holds on {@code player}. */
	static boolean vigil(Player player) {
		Long until = player.getAttached(VIGIL_UNTIL);
		return until != null && until > player.level().getGameTime() && has(player, VIGIL_KEY);
	}

	/** Deep Wells and a Vigil on the hands' pool ({@code base}, already the class's share and practice). */
	static int hands(Player player, float base) {
		float pool = base + (Classes.is(player, PlayerClass.PALADIN) ? WELLS_HANDS * ClassTrees.power(player, WELLS) : 0.0F);
		return Math.round(vigil(player) ? pool * VIGIL : pool);
	}

	/** Deep Wells: the smites it adds. */
	static int smites(Player player) {
		return Classes.is(player, PlayerClass.PALADIN) ? Math.round(ClassTrees.power(player, WELLS)) : 0;
	}

	/** A smite's extra on {@code target}: Judgment on a lawbreaker, and a Vigil. */
	static float smite(ServerPlayer paladin, LivingEntity target, float extra) {
		if (has(paladin, OATH) && Paladins.oath(paladin).equals("justice") && lawbreaker(target)) {
			extra *= JUDGMENT;
			paladin.level().sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(1.0), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		}
		return vigil(paladin) ? extra * VIGIL : extra;
	}

	/** Judgment: a lawbreaker is one found out at a crime, or a raider. */
	private static boolean lawbreaker(LivingEntity e) {
		return Crooks.isCriminal(e) || e instanceof Raider;
	}

	/** The aura's reach and strength on {@code paladin}: a Vigil makes it greater. */
	static float auraScale(Player paladin) {
		return vigil(paladin) ? VIGIL : 1.0F;
	}

	// ---- the key -------------------------------------------------------------------------------------------------------------

	/** The key on a foe: Honour's Challenge. False when it isn't that. */
	static boolean challenge(ServerPlayer paladin, @Nullable LivingEntity target) {
		if (!(target instanceof Mob foe) || !(foe instanceof Enemy || foe.getTarget() == paladin) || paladin.distanceTo(foe) > CHALLENGE_REACH
			|| !has(paladin, OATH) || !Paladins.oath(paladin).equals("honour")) {
			return false;
		}
		ServerLevel level = paladin.level();
		Called standing = CALLED.get(paladin.getUUID());
		if (standing != null && standing.foe() == foe && standing.until() > level.getGameTime()) {
			// already called out: the key is the Paladin's hands again
			return false;
		}
		CALLED.put(paladin.getUUID(), new Called(foe, level.getGameTime() + CHALLENGE_TICKS));
		foe.setTarget(paladin);
		Fx.ring(level, foe.position().add(0.0, 0.1, 0.0), 1.0F, 0xFFE9A0);
		Fx.beam(level, paladin.getEyePosition().add(0.0, -0.3, 0.0), foe.getBoundingBox().getCenter(), 0.04F, 0xFFE9A0, 6);
		level.playSound(null, paladin.getX(), paladin.getY(), paladin.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
		paladin.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.challenge", foe.getDisplayName()));
		return true;
	}

	/** Sneak and the key on a friend: Shield of Faith. False when it isn't that. */
	static boolean shield(ServerPlayer paladin, @Nullable LivingEntity target) {
		if (target == null || target == paladin || paladin.distanceTo(target) > Paladins.HANDS_REACH || !Restoration.friend(paladin, target)
			|| !has(paladin, SHIELD)) {
			return false;
		}
		if (!Rests.spend(paladin, Rests.HANDS, SHIELD_COST, Paladins.hands(paladin))) {
			paladin.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.hands_spent"));
			return true;
		}
		ServerLevel level = paladin.level();
		WARDS.put(target, new Ward(paladin.getUUID(), SHIELD_HITS, level.getGameTime() + SHIELD_TICKS));
		Fx.orb(level, target.getBoundingBox().getCenter(), target.getBbWidth() + 0.5F, 0xFFE9A0, 20);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
		paladin.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.shield", target.getDisplayName()));
		return true;
	}

	/** The key with no harm to mend: Divine Sense. False when it isn't that. */
	static boolean sense(ServerPlayer paladin) {
		if (!has(paladin, SENSE)) {
			return false;
		}
		ServerLevel level = paladin.level();
		long now = level.getGameTime();
		if (SENSED.getOrDefault(paladin.getUUID(), 0L) > now) {
			return false;
		}
		SENSED.put(paladin.getUUID(), now + SENSE_WAIT);
		int found = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, paladin.getBoundingBox().inflate(SENSE_RANGE),
			e -> e.isAlive() && (Restoration.undead(e) || fiend(e)))) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SENSE_TICKS, 0, false, false, false));
			found++;
		}
		Fx.wave(level, paladin.position().add(0.0, 0.1, 0.0), paladin.getLookAngle().multiply(1.0, 0.0, 1.0), SENSE_RANGE * 0.25, 1.0F, 0xFFE9A0, 12);
		level.playSound(null, paladin.getX(), paladin.getY(), paladin.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.6F);
		paladin.sendOverlayMessage(Component.translatable(found > 0 ? "message.jaymoskingdoms.paladin.sense" : "message.jaymoskingdoms.paladin.sense_none", found));
		return true;
	}

	/** A fiend: a monster of the fires below (one fire can't touch). */
	private static boolean fiend(LivingEntity e) {
		return e instanceof Enemy && e.fireImmune();
	}

	// ---- prayer ----------------------------------------------------------------------------------------------------------------

	/** A prayer at a shrine ({@link Paladins#prayed}): by night, a Vigil. */
	static void prayed(ServerPlayer paladin) {
		ServerLevel level = paladin.level();
		long day = Royals.timeOfDay(level) % 24000L;
		if (!has(paladin, VIGIL_KEY) || day < 12500L || day > 23500L) {
			return;
		}
		// until the next sunset: the rest of tonight, then the coming day to dusk
		long until = level.getGameTime() + (24000L - day) + 12000L;
		paladin.setAttached(VIGIL_UNTIL, until);
		Rests.regain(paladin, Rests.SMITE, Rests.spent(paladin, Rests.SMITE));
		Rests.regain(paladin, Rests.HANDS, Rests.spent(paladin, Rests.HANDS));
		Fx.sigil(level, paladin.position().add(0.0, 0.05, 0.0), 1.6F, 0xFFE9A0, 40);
		level.playSound(null, paladin.getX(), paladin.getY(), paladin.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
		paladin.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.vigil").withStyle(net.minecraft.ChatFormatting.GOLD));
	}

	// ---- the blows -------------------------------------------------------------------------------------------------------------

	/** What the Paladin's tree does to a blow on {@code target}: a Shield of Faith, Aura of Warding, a Challenge. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(target.level() instanceof ServerLevel level)) {
			return amount;
		}
		Ward ward = WARDS.get(target);
		if (ward != null && source.getEntity() != null) {
			if (ward.until < level.getGameTime()) {
				WARDS.remove(target);
			} else {
				float soaked = Math.min(amount, SHIELD_SOAK);
				amount -= soaked;
				if (ward.hits <= 1) {
					WARDS.remove(target);
				} else {
					WARDS.put(target, new Ward(ward.paladin, ward.hits - 1, ward.until));
				}
				Fx.ring(level, target.getBoundingBox().getCenter(), target.getBbWidth() + 0.3F, 0xFFE9A0);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
			}
		}
		if (source.is(DamageTypeTags.WITCH_RESISTANT_TO) && !(target instanceof Enemy) && near(target, WARDING)) {
			amount *= WARDING_TAKEN;
		}
		if (source.getEntity() instanceof ServerPlayer paladin && source.getDirectEntity() == paladin) {
			Called called = CALLED.get(paladin.getUUID());
			if (called != null && called.foe == target && called.until >= level.getGameTime()) {
				amount *= 1.0F + CHALLENGE_HIT;
			}
		}
		return amount;
	}

	/** Aura of Courage: {@code who} stands near a Paladin whose aura drives out fear. */
	static boolean courage(LivingEntity who) {
		// (class trees) and a Beacon's aura, whichever way their crossroads went
		return !(who instanceof Enemy) && (near(who, COURAGE) || near(who, FaithExtras.BEACON));
	}

	/** {@code who} is within the aura of a Paladin with {@code key}. */
	private static boolean near(LivingEntity who, String key) {
		if (!(who.level() instanceof ServerLevel level) || level.players().isEmpty()) {
			return false;
		}
		for (Player p : level.players()) {
			double r = Paladins.AURA_RANGE * auraScale(p);
			if (p.distanceToSqr(who) <= r * r && has(p, AURA) && has(p, key)) {
				return true;
			}
		}
		return false;
	}

	/** Mercy's Spare the Fallen: a foe brought down (not killed) by a Paladin's blow heals them. */
	private static void spare(LivingEntity entity, DamageSource source) {
		// only the blow that brings them down, not one on a foe already lying there
		boolean lying = ALREADY_DOWN.remove(entity.getId());
		if (lying || !(source.getEntity() instanceof ServerPlayer paladin) || entity == paladin || !Downed.isDowned(entity) || SPARED.containsKey(entity.getId())
			|| !has(paladin, OATH) || !Paladins.oath(paladin).equals("mercy")) {
			return;
		}
		SPARED.put(entity.getId(), paladin.level().getGameTime());
		paladin.heal(SPARE_HEAL);
		paladin.level().sendParticles(ParticleTypes.HEART, paladin.getX(), paladin.getY(1.0), paladin.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		paladin.level().playSound(null, paladin.getX(), paladin.getY(), paladin.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	// ---- the tick --------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		// a blow that was refused never reaches {@link #spare}
		ALREADY_DOWN.clear();
		if (!CALLED.isEmpty()) {
			CALLED.entrySet().removeIf(e -> {
				Called c = e.getValue();
				ServerPlayer paladin = server.getPlayerList().getPlayer(e.getKey());
				if (paladin == null || c.until < now || !c.foe.isAlive() || c.foe.level() != paladin.level() || !paladin.isAlive()) {
					return true;
				}
				// it fights only you
				if (c.foe.getTarget() != paladin) {
					c.foe.setTarget(paladin);
				}
				return false;
			});
		}
		if (now % 1200 == 0) {
			WARDS.entrySet().removeIf(e -> e.getValue().until < now || !e.getKey().isAlive());
			SENSED.values().removeIf(t -> t < now);
			SPARED.values().removeIf(t -> t < now - 20 * 600);
		}
	}
}
