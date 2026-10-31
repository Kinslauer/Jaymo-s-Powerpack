package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.342) Will: Strength's third and fourth pages ({@link Skills.Perk#DREAD_PRESENCE} on), the fighter's answer to
 * magic. Andy (2026-10-29): "Strength has Combat arts, it needs another tree to combat magic. we can call it Will", with
 * weak mobs trembling in your presence, downed ones unable to rise, more beheadings, the weak killed outright and a roar
 * on one heart; he picked the rest from a list (all but resisting mind effects).
 *
 * <p><b>Weaker</b> ({@link #weaker}), Andy: a foe whose most health is below yours (power level, for a Z Fighter), or
 * any enemy of a world tier below yours, goblins and skeletons being the weak tier. Each kind of foe has its own tier
 * ({@link #kindTier}); Hearth's kinds count as below everyone. Bosses are never weaker.
 *
 * <p><b>Presence:</b> Dread Presence (weaker foes and players near you tremble: slowed, weakened, sometimes buckling),
 * No Getting Up (downed foes near you shiver and can't rise; the holding down is Jaymo's Powers' {@code WillSkill}),
 * Stare Down (sneak and hold your gaze on a weaker foe: it drops its weapon or runs; (0.7.395) a captain or a ruler, and
 * foes by what they are, break their own ways: {@link Staredowns}), Broken Morale (down or kill one and
 * its kind round it lose heart), Name That Carries (a Warrior's name: weaker foes won't start a fight with you).
 *
 * <p><b>Iron mind:</b> Grounded (less harm from spells and powers), Spellbreaker Blow (a full-strength hit on a caster
 * breaks the spell and hushes them: {@link #hushed}), Snap the Thread (risen thralls fall apart under your blows; a
 * necromancer's thralls reel when you hit him), Shrug It Off (sneak and jump to throw off every ill effect), Witch Hunter
 * (harder blows on whoever cast at you, more for felling casters), Will Over Flesh (powers push you less: Powers').
 *
 * <p><b>Killing intent:</b> Grim Hand (grim crits, and every cut more likely to take a head or limb: {@link #severScale}),
 * No Mercy (the weaker die instead of going down, unless you sneak or hit with fists or a club: {@link #noMercy}), Last
 * Look (a beheading sends the weaker who see it running). <b>Last stand:</b> Refuse to Fall (once a day you stay up at
 * one heart instead of going down), Second Breath (under three hearts, Will is stronger: {@link #surging}), Unbroken Roar
 * (on one heart, a roar stuns everything round you; once a full day).
 */
public final class Will {
	/** How often the presence is looked for (ticks), and how often a trembling body shakes (ticks). */
	static final int SCAN_TICKS = 20, TREMBLE_EVERY = 3;
	/** Dread Presence: its reach (blocks: base, and per rank), how long one scan's fear lasts (ticks), a buckle's odds a scan. */
	static final double DREAD_REACH = 4.0, DREAD_REACH_PER_RANK = 2.0;
	static final int DREAD_TICKS = 40;
	static final float BUCKLE_CHANCE = 0.15F;
	static final int BUCKLE_TICKS = 25;
	/** How often a dreaded player hears of it (ticks), and their screen's shake. */
	static final int DREAD_NOTICE_TICKS = 200;
	static final float DREAD_SHAKE = 0.25F;
	/** No Getting Up: downed foes this near you shiver (Powers holds them down within its own, the same, reach). */
	static final double HELD_DOWN_REACH = 8.0;
	/** Stare Down: how far, how true a look (cosine), how long the gaze is held (ticks), the odds a foe with a weapon drops it. */
	static final double STARE_REACH = 10.0, STARE_COS = 0.97;
	static final int STARE_TICKS = 40;
	static final float STARE_DROP_CHANCE = 0.6F;
	/** How long the cowed run (ticks), and how far they run (blocks). */
	static final int FLEE_TICKS = 100, FLEE_DISTANCE = 14;
	static final double FLEE_SPEED = 1.3;
	/** Broken Morale: how far it spreads, how long it lasts (ticks), the odds a weaker one runs. */
	static final double MORALE_REACH = 12.0;
	static final int MORALE_TICKS = 160;
	static final float MORALE_FLEE_CHANCE = 0.5F;
	/** Name That Carries: how far your name keeps weaker foes off, how near is cornered, how long a blow of yours lets them fight back (ticks). */
	static final double NAME_REACH = 24.0, CORNERED = 4.0;
	static final int PROVOKED_TICKS = 200;
	/** Grounded: per rank, this share less harm from spells and powers. */
	static final float GROUNDED_PER_RANK = 0.1F;
	/** A player's blow from further than this is no swing of theirs but a power's (blocks). */
	static final double POWER_REACH = 5.5;
	/** Spellbreaker Blow: how long a caster is hushed (ticks), a player not as long. */
	static final int HUSH_TICKS = 100, HUSH_PLAYER_TICKS = 60;
	/** The tag on a hushed player (Jaymo's Powers keeps their power keys quiet while it's on: {@code WillSkill}). */
	public static final String HUSHED_TAG = "jaymoskingdoms_hushed";
	/** Snap the Thread: the odds a thrall falls apart under a full-strength blow, and how long a necromancer's thralls reel (ticks). */
	static final float SNAP_CHANCE = 0.35F;
	static final int REEL_TICKS = 40;
	/** Shrug It Off: its wait (ticks). */
	static final int SHRUG_COOLDOWN = 3600;
	/** Witch Hunter: per rank, this share more harm to whoever cast at you this recently (ticks); Strength experience for felling a caster. */
	static final float WITCH_HUNTER_PER_RANK = 0.15F;
	static final int CAST_MEMORY = 200;
	static final int XP_CASTER = 6;
	/** Grim Hand: per rank, the odds a full-strength blow is a grim crit; a grim crit's weight; per rank, this much more to every chance to sever. */
	static final float GRIM_CRIT_PER_RANK = 0.07F, GRIM_CRIT = 1.5F, GRIM_SEVER_PER_RANK = 0.4F;
	/** Last Look: how far a beheading is seen (blocks). */
	static final double LAST_LOOK_REACH = 16.0;
	/** Refuse to Fall: how long you stand on one heart (ticks), and how often (ticks: a full day). */
	static final int REFUSE_TICKS = 100;
	static final long DAY = 24000L;
	/** One heart (health). */
	static final float ONE_HEART = 2.0F;
	/** Second Breath: at or under this much health (three hearts); Will's reach and odds this much greater; this much more Grounded. */
	static final float SECOND_BREATH_HEALTH = 6.0F, SURGE = 1.5F, SURGE_GROUNDED = 0.1F, SURGE_CRIT = 0.05F;
	/** Unbroken Roar: its reach, how long foes are stunned, how long the weaker are knocked out (ticks), the shake it sends. */
	static final double ROAR_REACH = 12.0;
	static final int ROAR_STUN_TICKS = 80, ROAR_KNOCKOUT_TICKS = 120;
	static final float ROAR_SHAKE = 1.0F;
	/** Strength experience: a spell endured (at most this often, ticks), a foe felled while you're down to three hearts. */
	static final int XP_ENDURED = 1, XP_LOW_KILL = 3;
	static final long ENDURED_XP_TICKS = 200L;

	/** When Unbroken Roar and Refuse to Fall are next ready (game time). Saved, kept through death. */
	static final AttachmentType<Long> ROAR_READY = AttachmentRegistry.create(JaymosKingdoms.id("will_roar"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());
	static final AttachmentType<Long> REFUSE_READY = AttachmentRegistry.create(JaymosKingdoms.id("will_refuse"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Wildlands' kinds (Hearth's and Borderlands' are below). */
	private static final Set<EntityType<?>> WILDLANDS = Set.of(EntityTypes.ENDERMAN, EntityTypes.BLAZE, EntityTypes.GHAST, EntityTypes.WITHER_SKELETON,
		EntityTypes.PIGLIN_BRUTE, EntityTypes.EVOKER, EntityTypes.ILLUSIONER, EntityTypes.RAVAGER, EntityTypes.BREEZE, EntityTypes.SHULKER,
		EntityTypes.ZOGLIN, EntityTypes.CREAKING);
	private static final Set<EntityType<?>> BORDERLANDS = Set.of(EntityTypes.WITCH, EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.CREEPER,
		EntityTypes.PIGLIN, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.HOGLIN, EntityTypes.GUARDIAN, EntityTypes.MAGMA_CUBE, EntityTypes.PHANTOM,
		EntityTypes.VEX);
	private static final Set<EntityType<?>> HEARTH = Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED, EntityTypes.ZOMBIE_VILLAGER,
		EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.SLIME,
		EntityTypes.SILVERFISH, EntityTypes.ENDERMITE);

	/** The cowed, running: who from, until when. */
	private record Flee(UUID from, long until) {
	}

	private static final Map<Mob, Flee> FLEEING = new WeakHashMap<>();
	/** Trembling bodies, until when. */
	private static final Map<LivingEntity, Long> TREMBLING = new WeakHashMap<>();
	/** The stunned (Unbroken Roar), until when. */
	private static final Map<Mob, Long> STUNNED = new WeakHashMap<>();
	/** Hushed casters (Spellbreaker Blow), until when. Hushed players carry {@link #HUSHED_TAG}. */
	private static final Map<Entity, Long> HUSHED = new WeakHashMap<>();
	private static final Map<UUID, Long> HUSHED_PLAYERS = new HashMap<>();
	/** Player → whom they're staring down, since when. */
	private static final Map<UUID, Object[]> STARING = new HashMap<>();
	/** Player → (who cast at them → when). */
	private static final Map<UUID, Map<UUID, Long>> CAST_AT = new HashMap<>();
	/** Player → when they last struck each foe (Name That Carries: a foe you've struck may fight back). */
	private static final Map<Mob, Map<UUID, Long>> STRUCK = new WeakHashMap<>();
	/** Foes a player hurt this tick: next tick, the downed or dead among them break their side's morale. */
	private static final List<Object[]> FELLED = new ArrayList<>();
	/** Player → game time Shrug It Off is ready; whether they held jump last tick; game time the next spell endured gives experience. */
	private static final Map<UUID, Long> SHRUG_READY = new HashMap<>(), NEXT_ENDURED_XP = new HashMap<>(), DREAD_NOTICED = new HashMap<>();
	private static final Map<UUID, Boolean> JUMPING = new HashMap<>();
	/** Foes struck by a grim crit, and the tick of it (for the severing that follows in the same blow). */
	private static final Map<LivingEntity, Long> GRIM = new WeakHashMap<>();

	private Will() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Will::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Will::afterDamage);
		// before Jaymo's Powers' downing (its last phase): Refuse to Fall keeps you standing
		ServerLivingEntityEvents.ALLOW_DEATH.register(Will::allowDeath);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer sp && !sp.isCreative() && killed instanceof LivingEntity dead) {
				if (caster(dead) || castAt(sp, dead)) {
					Skills.grant(sp, Skills.Pillar.STRENGTH, XP_CASTER);
				}
				if (dead instanceof Enemy && sp.getHealth() <= SECOND_BREATH_HEALTH) {
					Skills.grant(sp, Skills.Pillar.STRENGTH, XP_LOW_KILL);
				}
			}
		});
		// (a hush never outlasts a stop: it isn't kept)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handler.player.removeTag(HUSHED_TAG));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			STARING.remove(id);
			JUMPING.remove(id);
			CAST_AT.remove(id);
			DREAD_NOTICED.remove(id);
			if (HUSHED_PLAYERS.remove(id) != null) {
				handler.player.removeTag(HUSHED_TAG);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLEEING.clear();
			TREMBLING.clear();
			STUNNED.clear();
			HUSHED.clear();
			HUSHED_PLAYERS.clear();
			STARING.clear();
			CAST_AT.clear();
			STRUCK.clear();
			FELLED.clear();
			SHRUG_READY.clear();
			NEXT_ENDURED_XP.clear();
			DREAD_NOTICED.clear();
			JUMPING.clear();
			GRIM.clear();
		});
	}

	static int rank(Player player, Skills.Perk perk) {
		return Skills.rank(player, perk);
	}

	// ---- who is weaker ---------------------------------------------------------------------------------------------

	/**
	 * Whether {@code other} is weaker than {@code player} (for other mods too, by reflection: Jaymo's Powers' No Mercy and
	 * No Getting Up). Andy: "'Weaker' means the foe's max health is below yours (power level for Z Fighters). Weaker should
	 * also apply to all enemies from the world tier below you. goblins and skeletons are weak tier for example." Bosses
	 * never are.
	 */
	public static boolean weaker(Player player, LivingEntity other) {
		if (other == player || !other.isAlive() || BossBars.boss(other)) {
			return false;
		}
		int mine = WorldTiers.of(player).ordinal();
		int theirs = other instanceof Player p ? WorldTiers.of(p).ordinal() : kindTier(other);
		// (Hearth's kinds are the weak tier: below anyone, even in a Hearth world; a player only from a lower tier than yours)
		if (other instanceof Player ? theirs < mine : theirs < Math.max(1, mine)) {
			return true;
		}
		if (zFighter(player) || other instanceof Player p && zFighter(p)) {
			int ours = PowersCompat.powerLevel(player), them = PowersCompat.powerLevel(other);
			if (ours > 0 && them > 0) {
				return them < ours;
			}
		}
		return other.getMaxHealth() < player.getMaxHealth();
	}

	private static boolean zFighter(Player player) {
		return "zfighter".equals(PowersCompat.power(player));
	}

	/**
	 * The world tier a kind of foe belongs to (Hearth 0 to Legend 4): zombies, skeletons, spiders, slimes and goblins are
	 * Hearth's; witches, illagers, creepers, piglins, orcs and camp raiders Borderlands'; endermen, blazes, wither skeletons,
	 * evokers, ravagers, the Hornkin and assassins Wildlands'; the monsters of legend Dread's; bosses Legend's. Anything else
	 * by how much health it was born with.
	 */
	public static int kindTier(LivingEntity e) {
		if (BossBars.boss(e) || e.getType() == EntityTypes.ENDER_DRAGON || e.getType() == EntityTypes.WITHER || e.getType() == EntityTypes.WARDEN) {
			return 4;
		}
		if (e instanceof com.jaymoskingdoms.monsters.LegendMonster || e.getType() == EntityTypes.ELDER_GUARDIAN) {
			return 3;
		}
		if (WILDLANDS.contains(e.getType()) || e instanceof com.jaymoskingdoms.nether.Hornkin || Assassins.is(e)) {
			return 2;
		}
		if (Goblins.isGoblin(e) || HEARTH.contains(e.getType())) {
			return 0;
		}
		if (BORDERLANDS.contains(e.getType()) || Orcs.isOrc(e) || e instanceof Folk folk && folk.style() == FactionType.BARBARIAN
			|| Barbarians.isCampVillager(e) || caster(e)) {
			return 1;
		}
		double born = e.getAttributes().hasAttribute(Attributes.MAX_HEALTH) ? e.getAttributeBaseValue(Attributes.MAX_HEALTH) : e.getMaxHealth();
		return born <= 20.0 ? 0 : born <= 40.0 ? 1 : born <= 80.0 ? 2 : born <= 200.0 ? 3 : 4;
	}

	/** {@code e} is a foe of {@code player}'s for Will's presence: a monster or hostile folk, or something after them. Never a pet, a bystander or a player. */
	static boolean foe(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e instanceof Player) {
			return false;
		}
		if (e instanceof Mob mob && mob.getTarget() == player) {
			return true;
		}
		if (e instanceof OwnableEntity owned && owned.getOwnerReference() != null || Thralls.isThrall(e) && !hostileThrall(player, e)) {
			return false;
		}
		return e instanceof Mob mob && WorldTiers.hostile(mob) || e instanceof Enemy;
	}

	private static boolean hostileThrall(ServerPlayer player, LivingEntity thrall) {
		Thralls.Tag tag = Thralls.tag(thrall);
		return tag != null && !tag.player();
	}

	/** Another player {@code player}'s presence reaches (Andy: Dread Presence and the roar touch players too): never a groupmate. */
	static boolean rival(ServerPlayer player, Entity e) {
		return e instanceof ServerPlayer other && other != player && other.isAlive() && !other.isCreative() && !other.isSpectator()
			&& !Groups.together(player, other);
	}

	/**
	 * Whether {@code e} casts: one of the Kingdoms' casters (necromancers, shamans, blood witches, court wizards), a witch,
	 * an evoker or an illusioner, or a player with a power or attuned to mana.
	 */
	static boolean caster(Entity e) {
		if (e instanceof Player p) {
			return PowersCompat.power(p) != null || Mana.attuned(p);
		}
		if (e instanceof Folk folk) {
			return Sorcery.isCaster(folk.role()) || folk.role() == Orcs.SHAMAN;
		}
		if (e instanceof Worker w && Casters.isOffice(w.role()) || CourtWizards.is(e)) {
			return true;
		}
		EntityType<?> type = e.getType();
		return type == EntityTypes.WITCH || type == EntityTypes.EVOKER || type == EntityTypes.ILLUSIONER;
	}

	/** Whether {@code source} is a spell or a power: magic harm, a power's own harm, a caster's bolt, a power user's blow from afar. */
	static boolean magical(DamageSource source, LivingEntity hurt) {
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.SONIC_BOOM)
			|| source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.WITHER)) {
			return true;
		}
		ResourceKey<DamageType> key = source.typeHolder().unwrapKey().orElse(null);
		if (key != null) {
			String ns = key.identifier().getNamespace(), path = key.identifier().getPath();
			if (ns.equals("jaymospowers") && !path.equals("bleeding") && !path.equals("decapitation") && !path.equals("wounds")) {
				return true;
			}
			if (ns.equals("jaymoskingdoms") && path.equals("gloom")) {
				return true;
			}
		}
		Entity by = source.getEntity();
		if (by == null || !caster(by)) {
			return false;
		}
		// a caster's bolt (not their own fist), or a power user's blow from beyond arm's reach
		return source.getDirectEntity() != by || by instanceof Player && by.distanceTo(hurt) > POWER_REACH;
	}

	/** Second Breath: down to three hearts, Will is stronger. */
	static boolean surging(Player player) {
		return player.getHealth() <= SECOND_BREATH_HEALTH && rank(player, Skills.Perk.SECOND_BREATH) > 0;
	}

	private static double surge(Player player) {
		return surging(player) ? SURGE : 1.0;
	}

	/** Whether {@code player} remembers {@code e} casting at them a moment ago. */
	static boolean castAt(Player player, Entity e) {
		Map<UUID, Long> at = CAST_AT.get(player.getUUID());
		Long when = at == null ? null : at.get(e.getUUID());
		return when != null && player.level().getGameTime() - when <= CAST_MEMORY;
	}

	// ---- blows -------------------------------------------------------------------------------------------------------

	/**
	 * What a blow deals, from {@code amount} (from LivingEntity.hurtServer): a Will player takes less from spells (Grounded);
	 * their own full-strength blow may be a grim crit (Grim Hand), and lands harder on whoever cast at them (Witch Hunter).
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer hurt && amount > 0.0F && magical(source, hurt)) {
			if (source.getEntity() instanceof LivingEntity by && by != hurt) {
				CAST_AT.computeIfAbsent(hurt.getUUID(), id -> new HashMap<>()).put(by.getUUID(), hurt.level().getGameTime());
			}
			int grounded = rank(hurt, Skills.Perk.GROUNDED);
			if (grounded > 0) {
				float off = GROUNDED_PER_RANK * grounded + (surging(hurt) ? SURGE_GROUNDED : 0.0F);
				amount *= Math.max(0.0F, 1.0F - off);
			}
			// (a spell endured teaches Strength's Will)
			long now = hurt.level().getGameTime();
			if (!hurt.isCreative() && NEXT_ENDURED_XP.getOrDefault(hurt.getUUID(), 0L) <= now) {
				NEXT_ENDURED_XP.put(hurt.getUUID(), now + ENDURED_XP_TICKS);
				Skills.grant(hurt, Skills.Pillar.STRENGTH, XP_ENDURED);
			}
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || sp == target || CombatArts.striking() || !(sp.level() instanceof ServerLevel level)) {
			return amount;
		}
		int hunter = rank(sp, Skills.Perk.WITCH_HUNTER);
		if (hunter > 0 && castAt(sp, target)) {
			amount *= 1.0F + WITCH_HUNTER_PER_RANK * hunter;
		}
		int grim = rank(sp, Skills.Perk.GRIM_HAND);
		long now = level.getGameTime();
		if (grim > 0 && source.getDirectEntity() == sp && source.is(DamageTypes.PLAYER_ATTACK) && CombatArts.fullStrength(sp, now)
			&& level.getRandom().nextFloat() < GRIM_CRIT_PER_RANK * grim + (surging(sp) ? SURGE_CRIT : 0.0F)) {
			amount *= GRIM_CRIT;
			GRIM.put(target, now);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 18, 0.3, 0.4, 0.3, 0.35);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
		return amount;
	}

	/**
	 * (for Jaymo's Powers' dismemberment, by reflection) How much more likely {@code attacker}'s cut is to take a part off
	 * {@code target}: Grim Hand's ranks, twice over on a grim crit. 1 for anyone without it.
	 */
	public static float severScale(Player attacker, LivingEntity target) {
		int grim = rank(attacker, Skills.Perk.GRIM_HAND);
		if (grim == 0) {
			return 1.0F;
		}
		float scale = 1.0F + GRIM_SEVER_PER_RANK * grim;
		Long crit = GRIM.get(target);
		return crit != null && crit == attacker.level().getGameTime() ? scale * 2.0F : scale;
	}

	/**
	 * (for Jaymo's Powers' downing, by reflection) No Mercy: whether the blow {@code source} kills {@code victim} outright
	 * instead of downing it. Andy: the weaker die, but "sneaking still downs them instead, so captives and bounties keep
	 * working, or less than lethal weaponry like fists or clubs". Mobs only.
	 */
	public static boolean noMercy(LivingEntity victim, DamageSource source) {
		// (0.7.361) struck by a King's Valor Coating: the weaker die (its own rule for sneaking, fists and clubs)
		if (ValorKings.killed(victim)) {
			return true;
		}
		if (victim instanceof Player || !(source.getEntity() instanceof ServerPlayer sp) || rank(sp, Skills.Perk.NO_MERCY) == 0 || sp.isShiftKeyDown()) {
			return false;
		}
		if (source.getDirectEntity() == sp && gentle(sp.getMainHandItem())) {
			return false;
		}
		return weaker(sp, victim);
	}

	/** Fists, a stick, a club, a cudgel or a baton: weapons that down, not kill. */
	static boolean gentle(ItemStack held) {
		if (held.isEmpty() || held.is(Items.STICK)) {
			return true;
		}
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
		return path.contains("club") || path.contains("cudgel") || path.contains("baton");
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		// Unbroken Roar: down to one heart
		if (target instanceof ServerPlayer hurt && damageTaken > 0.0F && hurt.isAlive() && hurt.getHealth() <= ONE_HEART) {
			roar(level, hurt);
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || sp == target || damageTaken <= 0.0F || CombatArts.striking()) {
			return;
		}
		long now = level.getGameTime();
		if (target instanceof Mob mob) {
			STRUCK.computeIfAbsent(mob, m -> new HashMap<>()).put(sp.getUUID(), now);
		}
		if (rank(sp, Skills.Perk.BROKEN_MORALE) > 0) {
			FELLED.add(new Object[] {sp, target});
		}
		boolean melee = source.getDirectEntity() == sp && source.is(DamageTypes.PLAYER_ATTACK) && CombatArts.fullStrength(sp, now);
		if (!melee) {
			return;
		}
		if (rank(sp, Skills.Perk.SPELLBREAKER) > 0 && caster(target)) {
			spellbreak(level, sp, target);
		}
		if (rank(sp, Skills.Perk.SNAP_THE_THREAD) > 0) {
			if (target instanceof Mob thrall && Thralls.isThrall(thrall) && target.isAlive() && level.getRandom().nextFloat() < SNAP_CHANCE * surge(sp)) {
				Thralls.crumble(level, thrall);
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.snap").withStyle(ChatFormatting.DARK_AQUA));
			} else {
				for (Mob thrall : Thralls.of(target.getUUID())) {
					thrall.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REEL_TICKS, 3), sp);
					thrall.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, REEL_TICKS, 1), sp);
					level.sendParticles(ParticleTypes.SOUL, thrall.getX(), thrall.getY(1.0), thrall.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
				}
			}
		}
	}

	/** Spellbreaker Blow: {@code caster}'s spell breaks, and they can't cast for a while. */
	static void spellbreak(ServerLevel level, ServerPlayer sp, LivingEntity caster) {
		hush(level, caster);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, caster.getX(), caster.getY(0.7), caster.getZ(), 24, 0.3, 0.4, 0.3, 0.3);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.spellbreak").withStyle(ChatFormatting.AQUA));
		// (0.7.361) a feat for Valor
		Valor.spellbroke(sp, caster);
	}

	/** (0.7.361) {@code caster} can't cast for a while (a Spellbreaker Blow's hush; King's Valor's too). */
	static void hush(ServerLevel level, LivingEntity caster) {
		long now = level.getGameTime();
		if (caster instanceof ServerPlayer other) {
			HUSHED_PLAYERS.put(other.getUUID(), now + HUSH_PLAYER_TICKS);
			other.addTag(HUSHED_TAG);
			other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.hushed").withStyle(ChatFormatting.DARK_AQUA));
		} else {
			HUSHED.put(caster, now + HUSH_TICKS);
			int[] timers = caster instanceof Folk folk ? folk.spellTimers() : caster instanceof Enlisted w ? w.spellTimers() : null;
			if (timers != null) {
				for (int i = 0; i < timers.length; i++) {
					timers[i] = Math.max(timers[i], HUSH_TICKS);
				}
			}
		}
	}

	/** (0.7.361) {@code mob} stands dazed for {@code ticks} (Valor's stun, King's Valor's shaken), as the roar's stunned do. */
	static void daze(Mob mob, int ticks) {
		long until = mob.level().getGameTime() + ticks;
		STUNNED.merge(mob, until, Math::max);
		TREMBLING.merge(mob, until, Math::max);
	}

	/** Whether {@code e} was hushed by a Spellbreaker Blow and can't cast yet (Sorcery, Mana and Arcana ask). */
	public static boolean hushed(Entity e) {
		if (e instanceof Player) {
			return e.entityTags().contains(HUSHED_TAG);
		}
		Long until = HUSHED.get(e);
		return until != null && e.level().getGameTime() < until;
	}

	// ---- last stand --------------------------------------------------------------------------------------------------

	/** Refuse to Fall: the blow that would put you down leaves you on one heart, once a day. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level) || rank(sp, Skills.Perk.REFUSE_TO_FALL) == 0
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || Downed.isDowned(sp)) {
			return true;
		}
		long now = level.getGameTime();
		if (sp.getAttachedOrElse(REFUSE_READY, 0L) > now) {
			return true;
		}
		sp.setAttached(REFUSE_READY, now + DAY);
		sp.setHealth(ONE_HEART);
		sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, REFUSE_TICKS, 3), sp);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.5F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, sp.getX(), sp.getY(1.0) + 0.3, sp.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.refuse").withStyle(ChatFormatting.GOLD));
		roar(level, sp);
		// (0.7.361) a feat for Valor, or its waking
		Valor.refused(sp);
		return false;
	}

	/** Unbroken Roar: on one heart, everything round you is stunned and the weaker knocked flat; once a full day. */
	static void roar(ServerLevel level, ServerPlayer sp) {
		if (rank(sp, Skills.Perk.UNBROKEN_ROAR) == 0 || sp.isSpectator() || Downed.isDowned(sp)) {
			return;
		}
		long now = level.getGameTime();
		if (sp.getAttachedOrElse(ROAR_READY, 0L) > now) {
			return;
		}
		sp.setAttached(ROAR_READY, now + DAY);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.2F, 0.8F);
		level.sendParticles(ParticleTypes.SONIC_BOOM, sp.getX(), sp.getY(0.6), sp.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2 * i / 24;
			level.sendParticles(ParticleTypes.POOF, sp.getX() + Math.cos(a) * 2.5, sp.getY(0.3), sp.getZ() + Math.sin(a) * 2.5, 2, 0.1, 0.1, 0.1, 0.08);
		}
		double reach = ROAR_REACH * surge(sp);
		AABB box = sp.getBoundingBox().inflate(reach, 4.0, reach);
		// (0.7.361) a coated Warlord's own stand steady
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> foe(sp, e) || rival(sp, e) && !Valor.steadied(e))) {
			if (e instanceof ServerPlayer other) {
				other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROAR_STUN_TICKS, 4), sp);
				other.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ROAR_STUN_TICKS, 2), sp);
				other.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_STUN_TICKS, 1), sp);
				PowersCompat.shake(other, ROAR_SHAKE);
				other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.roared", sp.getDisplayName()).withStyle(ChatFormatting.RED));
			} else if (e instanceof Mob mob) {
				if (weaker(sp, mob) && PowersCompat.knockOut(level, mob, ROAR_KNOCKOUT_TICKS)) {
					continue;
				}
				STUNNED.put(mob, now + ROAR_STUN_TICKS);
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROAR_STUN_TICKS, 9), sp);
				mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_STUN_TICKS, 1), sp);
			}
			TREMBLING.put(e, now + ROAR_STUN_TICKS);
		}
		PowersCompat.shake(sp, ROAR_SHAKE * 0.5F);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.roar").withStyle(ChatFormatting.GOLD));
	}

	/** (for Jaymo's Powers, by reflection) {@code by} took {@code target}'s head: Last Look sends the weaker who saw it running. */
	public static void beheaded(Player by, LivingEntity target) {
		if (!(by instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level) || rank(sp, Skills.Perk.LAST_LOOK) == 0) {
			return;
		}
		double reach = LAST_LOOK_REACH * surge(sp);
		long now = level.getGameTime();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(reach), m -> m != target && foe(sp, m) && weaker(sp, m)
			&& m.hasLineOfSight(target))) {
			flee(mob, sp, now, FLEE_TICKS);
			level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getY(1.0) + 0.2, mob.getZ(), 6, 0.2, 0.1, 0.2, 0.0);
		}
	}

	// ---- the presence, every tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tickCount = server.getTickCount();
		// foes hurt last tick: the downed or dead break their side's morale
		if (!FELLED.isEmpty()) {
			List<Object[]> felled = new ArrayList<>(FELLED);
			FELLED.clear();
			for (Object[] f : felled) {
				ServerPlayer sp = (ServerPlayer) f[0];
				LivingEntity target = (LivingEntity) f[1];
				if ((!target.isAlive() || Downed.isDowned(target)) && target.level() instanceof ServerLevel level && !target.entityTags().contains(MORALE_TAG)) {
					target.addTag(MORALE_TAG);
					breakMorale(level, sp, target);
				}
			}
		}
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!(sp.level() instanceof ServerLevel level) || !sp.isAlive() || sp.isSpectator() || Downed.isDowned(sp)) {
				STARING.remove(sp.getUUID());
				continue;
			}
			long now = level.getGameTime();
			// (0.7.395) locked in a clash of wills: nothing else of Will's keys
			if (!WillClash.in(sp)) {
				stare(level, sp, now);
				shrug(level, sp, now);
			}
			if (tickCount % SCAN_TICKS == 0) {
				presence(level, sp, now);
			}
		}
		// hushed players speak again
		for (Iterator<Map.Entry<UUID, Long>> it = HUSHED_PLAYERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || p.level().getGameTime() >= e.getValue()) {
				if (p != null) {
					p.removeTag(HUSHED_TAG);
				}
				it.remove();
			}
		}
		// the running run, the stunned stand dazed, the trembling shake
		for (Iterator<Map.Entry<Mob, Flee>> it = FLEEING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Flee> e = it.next();
			Mob mob = e.getKey();
			if (!mob.isAlive() || mob.isRemoved() || mob.level().getGameTime() >= e.getValue().until() || Downed.isDowned(mob)) {
				it.remove();
				continue;
			}
			mob.setTarget(null);
			if (tickCount % 10 == 0 && mob instanceof PathfinderMob pm && mob.level().getPlayerByUUID(e.getValue().from()) instanceof Player from) {
				Vec3 away = DefaultRandomPos.getPosAway(pm, FLEE_DISTANCE, 7, from.position());
				if (away != null) {
					pm.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
				}
			}
		}
		for (Iterator<Map.Entry<Mob, Long>> it = STUNNED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Long> e = it.next();
			Mob mob = e.getKey();
			if (!mob.isAlive() || mob.isRemoved() || mob.level().getGameTime() >= e.getValue()) {
				it.remove();
				continue;
			}
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		}
		if (tickCount % TREMBLE_EVERY == 0) {
			for (Iterator<Map.Entry<LivingEntity, Long>> it = TREMBLING.entrySet().iterator(); it.hasNext();) {
				Map.Entry<LivingEntity, Long> e = it.next();
				LivingEntity body = e.getKey();
				if (!body.isAlive() || body.isRemoved() || body.level().getGameTime() >= e.getValue() || !(body.level() instanceof ServerLevel level)) {
					it.remove();
					continue;
				}
				tremble(level, body);
			}
		}
	}

	/** Marks a foe whose fall has already broken its side's morale (once each). */
	static final String MORALE_TAG = "jaymoskingdoms_morale_broken";

	/** Dread Presence, No Getting Up and Name That Carries, once a scan. */
	private static void presence(ServerLevel level, ServerPlayer sp, long now) {
		int dread = rank(sp, Skills.Perk.DREAD_PRESENCE);
		double surge = surge(sp);
		if (dread > 0) {
			double reach = (DREAD_REACH + DREAD_REACH_PER_RANK * dread) * surge;
			AABB box = sp.getBoundingBox().inflate(reach, 3.0, reach);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> (foe(sp, e) || rival(sp, e) && !Valor.steadied(e)) && !Downed.isDowned(e)
				&& e.distanceToSqr(sp) <= reach * reach && weaker(sp, e))) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DREAD_TICKS, 0), sp);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREAD_TICKS, 0), sp);
				TREMBLING.put(e, now + SCAN_TICKS + 2);
				if (e instanceof ServerPlayer other) {
					PowersCompat.shake(other, DREAD_SHAKE);
					if (DREAD_NOTICED.getOrDefault(other.getUUID(), 0L) <= now) {
						DREAD_NOTICED.put(other.getUUID(), now + DREAD_NOTICE_TICKS);
						other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.dread", sp.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
					}
				} else if (e instanceof Mob mob && level.getRandom().nextFloat() < BUCKLE_CHANCE * surge) {
					// their knees give: a stagger they can't stand up out of
					mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BUCKLE_TICKS, 4), sp);
					mob.getNavigation().stop();
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.HOSTILE, 0.5F, 0.6F);
				}
			}
		}
		if (rank(sp, Skills.Perk.NO_GETTING_UP) > 0) {
			// (Jaymo's Powers holds them down; here they shiver)
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(HELD_DOWN_REACH), e -> !(e instanceof Player)
				&& e.isAlive() && Downed.isDowned(e) && e.distanceToSqr(sp) <= HELD_DOWN_REACH * HELD_DOWN_REACH)) {
				TREMBLING.put(e, now + SCAN_TICKS + 2);
			}
		}
		if (rank(sp, Skills.Perk.NAME_THAT_CARRIES) > 0 && Renown.alignment(Skills.of(sp)) == Skills.Pillar.STRENGTH) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(NAME_REACH), m -> m.getTarget() == sp && weaker(sp, m)
				&& m.distanceTo(sp) > CORNERED && !provoked(m, sp, now))) {
				flee(mob, sp, now, SCAN_TICKS * 2);
			}
		}
	}

	/** Whether {@code player} has struck {@code mob} lately (then it may fight back, name or no name). */
	private static boolean provoked(Mob mob, Player player, long now) {
		Map<UUID, Long> struck = STRUCK.get(mob);
		Long when = struck == null ? null : struck.get(player.getUUID());
		return when != null && now - when <= PROVOKED_TICKS;
	}

	/** Stare Down: sneaking, a weaker foe held in your gaze long enough drops its weapon or runs. */
	private static void stare(ServerLevel level, ServerPlayer sp, long now) {
		if (rank(sp, Skills.Perk.STARE_DOWN) == 0 || !sp.isShiftKeyDown()) {
			STARING.remove(sp.getUUID());
			return;
		}
		Object[] was = STARING.get(sp.getUUID());
		// (0.7.395) another player: they're warned, then the two clash ({@link WillClash})
		ServerPlayer rival = gazedPlayer(level, sp);
		if (rival != null && WillClash.may(sp, rival, now)) {
			if (was == null || was[0] != rival) {
				STARING.put(sp.getUUID(), new Object[] {rival, now});
				WillClash.warn(level, sp, rival);
			} else if (now - (long) was[1] >= WillClash.LOCK_TICKS / surge(sp)) {
				STARING.remove(sp.getUUID());
				WillClash.begin(level, sp, rival, now);
			}
			return;
		}
		Mob seen = gazedAt(level, sp);
		// (0.7.395) the great (a captain, a ruler) weigh your Will against their nerve instead
		if (seen == null || !Staredowns.great(sp, seen) && !weaker(sp, seen) || FLEEING.containsKey(seen)) {
			STARING.remove(sp.getUUID());
			return;
		}
		if (was == null || was[0] != seen) {
			STARING.put(sp.getUUID(), new Object[] {seen, now});
			return;
		}
		long held = now - (long) was[1];
		if (held % 10 == 0) {
			TREMBLING.put(seen, now + 12);
		}
		if (held < Staredowns.ticks(sp, seen) / surge(sp)) {
			return;
		}
		STARING.remove(sp.getUUID());
		if (Staredowns.broke(level, sp, seen, now)) {
			return;
		}
		cowed(level, sp, seen, now);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.stare", seen.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
	}

	/** A foe stared down: it often drops its weapon, and it runs. */
	static void cowed(ServerLevel level, ServerPlayer sp, Mob seen, long now) {
		ItemStack weapon = seen.getMainHandItem();
		if (!weapon.isEmpty() && level.getRandom().nextFloat() < STARE_DROP_CHANCE) {
			seen.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			ItemEntity dropped = new ItemEntity(level, seen.getX(), seen.getY(0.6), seen.getZ(), weapon.copy());
			dropped.setPickUpDelay(20);
			level.addFreshEntity(dropped);
			level.playSound(null, seen.getX(), seen.getY(), seen.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.HOSTILE, 0.4F, 1.4F);
		}
		flee(seen, sp, now, FLEE_TICKS);
	}

	/** (0.7.395) The player {@code sp} is looking straight at, within reach and in sight, or null. */
	private static @Nullable ServerPlayer gazedPlayer(ServerLevel level, ServerPlayer sp) {
		Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
		ServerPlayer best = null;
		double bestCos = STARE_COS;
		for (ServerPlayer other : level.players()) {
			if (other == sp || other.isSpectator()) {
				continue;
			}
			Vec3 to = other.getEyePosition().subtract(eye);
			double d = to.length();
			if (d < 1.0E-3 || d > STARE_REACH) {
				continue;
			}
			double cos = to.scale(1.0 / d).dot(look);
			if (cos > bestCos && sp.hasLineOfSight(other)) {
				bestCos = cos;
				best = other;
			}
		}
		return best;
	}

	/** The foe {@code sp} is looking straight at, within reach and in sight, or null. */
	private static @Nullable Mob gazedAt(ServerLevel level, ServerPlayer sp) {
		Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
		Mob best = null;
		double bestCos = STARE_COS;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(STARE_REACH), m -> (foe(sp, m) || Staredowns.great(sp, m)) && (!Downed.isDowned(m) || Captains.starable(m)))) {
			Vec3 to = mob.getEyePosition().subtract(eye);
			double d = to.length();
			if (d < 1.0E-3 || d > STARE_REACH) {
				continue;
			}
			double cos = to.scale(1.0 / d).dot(look);
			if (cos > bestCos && sp.hasLineOfSight(mob)) {
				bestCos = cos;
				best = mob;
			}
		}
		return best;
	}

	/** Shrug It Off: sneak and jump, and every ill effect on you is thrown off. */
	private static void shrug(ServerLevel level, ServerPlayer sp, long now) {
		boolean jump = sp.getLastClientInput().jump();
		boolean pressed = jump && !JUMPING.getOrDefault(sp.getUUID(), false);
		JUMPING.put(sp.getUUID(), jump);
		if (!pressed || !sp.getLastClientInput().shift() || rank(sp, Skills.Perk.SHRUG_IT_OFF) == 0) {
			return;
		}
		List<Holder<MobEffect>> ill = new ArrayList<>();
		for (MobEffectInstance effect : sp.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				ill.add(effect.getEffect());
			}
		}
		if (ill.isEmpty()) {
			return;
		}
		long ready = SHRUG_READY.getOrDefault(sp.getUUID(), 0L);
		if (ready > now) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combat_art.wait", Component.translatable("skill.jaymoskingdoms.perk.shrug_it_off"),
				(ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		SHRUG_READY.put(sp.getUUID(), now + SHRUG_COOLDOWN);
		for (Holder<MobEffect> effect : ill) {
			sp.removeEffect(effect);
		}
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.sendParticles(ParticleTypes.POOF, sp.getX(), sp.getY(0.5), sp.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will.shrug").withStyle(ChatFormatting.GOLD));
	}

	/** Broken Morale: {@code fallen} went down or died at {@code sp}'s hand; its own kind round it lose heart. */
	private static void breakMorale(ServerLevel level, ServerPlayer sp, LivingEntity fallen) {
		double reach = MORALE_REACH * surge(sp);
		long now = level.getGameTime();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, fallen.getBoundingBox().inflate(reach), m -> m != fallen && m.isAlive() && foe(sp, m)
			&& !Downed.isDowned(m) && kin(m, fallen))) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MORALE_TICKS, 0), sp);
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MORALE_TICKS, 0), sp);
			TREMBLING.put(mob, now + 30);
			if (weaker(sp, mob) && level.getRandom().nextFloat() < MORALE_FLEE_CHANCE) {
				flee(mob, sp, now, FLEE_TICKS);
			}
		}
	}

	/** {@code a} and {@code b} fight on one side: the same kind, or the same faction's folk. */
	private static boolean kin(Mob a, LivingEntity b) {
		if (a instanceof Worker wa && b instanceof Worker wb) {
			return wa.factionId() == wb.factionId();
		}
		return a.getType() == b.getType() || a instanceof Enemy && b instanceof Enemy && kindTier(a) == kindTier(b);
	}

	/** {@code mob} runs from {@code from} for {@code ticks}. */
	static void flee(Mob mob, Player from, long now, int ticks) {
		Flee was = FLEEING.get(mob);
		if (was == null || was.until() < now + ticks) {
			FLEEING.put(mob, new Flee(from.getUUID(), now + ticks));
		}
		mob.setTarget(null);
		TREMBLING.put(mob, Math.max(TREMBLING.getOrDefault(mob, 0L), now + Math.min(ticks, 40)));
	}

	/** A shiver: the head jerks, the body twitches, and a cold sweat. */
	private static void tremble(ServerLevel level, LivingEntity body) {
		RandomSource r = level.getRandom();
		body.setYHeadRot(body.getYHeadRot() + (r.nextFloat() - 0.5F) * 16.0F);
		if (!(body instanceof Player)) {
			body.setYBodyRot(body.yBodyRot + (r.nextFloat() - 0.5F) * 10.0F);
			body.setDeltaMovement(body.getDeltaMovement().add((r.nextDouble() - 0.5) * 0.05, 0.0, (r.nextDouble() - 0.5) * 0.05));
			body.needsSync = true;
		}
		if (r.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.FALLING_WATER, body.getX(), body.getY(0.9), body.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}
	}
}
