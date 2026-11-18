package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Cleric's tree (class trees, docs/ClassTrees-Plan.md). {@link Clerics} keeps the starting move (Turn the Undead, the
 * dead it unmakes growing with the Cleric's level) and asks here for the rest:
 * <ul>
 * <li><b>Devout</b> (3): a turning more a day, each rank.</li>
 * <li><b>Bless the Band</b> (3): today's blessing, moved into the tree (kept by Clerics who had it).</li>
 * <li><b>Sanctify</b> (5): sneak and the key with the symbol spends a turning: the ground round you is holy for a minute; the
 * dead won't set foot in it and friends in it heal slowly.</li>
 * <li><b>Last Rites</b> (8): the key with the symbol, looking at a body, reads the rites over it: it never rises (no Unburied
 * gets up from it) and its village thinks better of you. A village's own priest (vanilla's or MCA's cleric) reads them over
 * its dead too.</li>
 * <li><b>Healer</b> or <b>War Priest</b> (10): healing prayers mend more and reach further; or your blows carry wrath (more
 * harm, twice on the dead) and may stun the dead.</li>
 * <li><b>Patron's Gift</b> (15): one gift by your patron.</li>
 * <li><b>Destroy Undead</b> (20): the symbol unmakes far stronger dead, and the great dead take a heavy blow from it.</li>
 * </ul>
 */
public final class Litany {
	/** Sanctify: how wide (blocks), how long (ticks), the heal and how often (ticks). */
	static final double SANCTIFY_RADIUS = 5.0;
	static final int SANCTIFY_TICKS = 20 * 60, SANCTIFY_EVERY = 40;
	static final float SANCTIFY_HEAL = 1.0F;
	/** Last Rites: how near a body must be, the good name won, the tag a rited body carries; a priest's reach and pace. */
	static final double RITES_REACH = 4.0, PRIEST_SEEK = 32.0, PRIEST_READ = 2.0;
	static final int RITES_REP = 2, PRIEST_EVERY = 100;
	/** Last Rites: the dead's kin this near warm to the one who read them, by this many hearts. */
	static final double RITES_KIN_REACH = 32.0;
	static final int RITES_KIN_HEARTS = 10;
	public static final String RITES_TAG = "jaymoskingdoms_rited";
	/** Healer: healing prayers this many times as strong, and reaching this much further. */
	static final float HEALER = 1.3F;
	static final double HEALER_REACH = 1.5;
	/** War Priest: the wrath on a blow (health; twice on the dead) and the chance a blow on the dead stuns it (ticks). */
	static final float WRATH = 2.0F, WRATH_STUN = 0.2F;
	static final int WRATH_STUN_TICKS = 40;
	/** Destroy Undead: the dead the symbol unmakes, this many times as strong; a great one's share of its health, once a raising. */
	static final float DESTROY = 3.0F, DESTROY_BOSS = 0.2F;
	/** The Lantern Warden's Hush: the dead forget a Cleric this long after they were last struck by them (ticks). */
	static final int HUSH_FORGIVE = 200;

	public static final String DEVOUT = "cle_devout", BLESS = "cle_bless", SANCTIFY = "cle_sanctify", RITES = "cle_rites",
		HEALER_KEY = "cle_healer", WAR_PRIEST = "cle_war_priest", PATRON = "cle_patron", DESTROY_KEY = "cle_destroy";

	/** A sanctified circle. */
	private record Ground(ServerLevel level, Vec3 at, long until, UUID cleric) {
	}

	private static final List<Ground> GROUNDS = new ArrayList<>();
	/** The great dead a raising has already struck (cleric → ids). */
	private static final Map<UUID, Set<Integer>> DESTROYED = new HashMap<>();
	/** When each Cleric last struck one of the dead (the Hush). */
	private static final Map<UUID, Long> STRUCK_DEAD = new HashMap<>();
	/** A priest on the way to read the rites: villager → body. */
	private static final Map<Villager, Entity> READING = new HashMap<>();

	private Litany() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Litany::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GROUNDS.clear();
			DESTROYED.clear();
			STRUCK_DEAD.clear();
			READING.clear();
		});
	}

	static boolean has(Player player, String key) {
		return Classes.is(player, PlayerClass.CLERIC) && ClassTrees.has(player, key) > 0;
	}

	/** Devout: the turnings it adds. */
	static int devout(Player player) {
		return Classes.is(player, PlayerClass.CLERIC) ? Math.round(ClassTrees.power(player, DEVOUT)) : 0;
	}

	/** Destroy Undead: how many times as strong the dead the symbol unmakes may be. */
	static float destroy(Player player) {
		return has(player, DESTROY_KEY) ? DESTROY : 1.0F;
	}

	/** Healer: healing prayers' strength and reach. */
	public static float healer(Player player) {
		return has(player, HEALER_KEY) ? HEALER : 1.0F;
	}

	public static double healerReach(Player player) {
		return has(player, HEALER_KEY) ? HEALER_REACH : 1.0;
	}

	// ---- the key --------------------------------------------------------------------------------------------------------------

	/** Sneak and the key with the symbol: Sanctify. */
	static void sanctify(ServerPlayer cleric) {
		if (!ClassTrees.check(cleric, SANCTIFY)) {
			return;
		}
		if (!Rests.spend(cleric, Rests.CHANNEL, 1, Clerics.channels(cleric))) {
			cleric.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.silent"));
			return;
		}
		ServerLevel level = cleric.level();
		Vec3 at = cleric.position();
		GROUNDS.add(new Ground(level, at, level.getGameTime() + SANCTIFY_TICKS, cleric.getUUID()));
		Fx.pool(level, at.add(0.0, 0.05, 0.0), (float) SANCTIFY_RADIUS, colour(cleric), SANCTIFY_TICKS);
		level.playSound(null, cleric.getX(), cleric.getY(), cleric.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.2F);
		cleric.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.sanctified"));
	}

	/** The key with the symbol, looking at a body: Last Rites. False when there's no body before them. */
	static boolean rites(ServerPlayer cleric) {
		if (!has(cleric, RITES) || !KitchenCompat.corpsesExist()) {
			return false;
		}
		ServerLevel level = cleric.level();
		Vec3 eye = cleric.getEyePosition();
		Vec3 end = eye.add(cleric.getLookAngle().scale(RITES_REACH));
		for (Entity body : level.getEntities((Entity) null, cleric.getBoundingBox().inflate(RITES_REACH), KitchenCompat::isCorpse)) {
			if (body.entityTags().contains(RITES_TAG) || !mourned(body) || body.getBoundingBox().inflate(0.3).clip(eye, end).isEmpty()) {
				continue;
			}
			read(level, body);
			// the dead's kin nearby think better of the one who read over them
			Bearers.who(level, body).id().ifPresent(dead -> {
				for (Villager kin : level.getEntitiesOfClass(Villager.class, body.getBoundingBox().inflate(RITES_KIN_REACH),
					v -> v.isAlive() && McaCompat.kinOf(v, dead) != McaCompat.STRANGERS)) {
					McaCompat.hearts(kin, cleric, RITES_KIN_HEARTS);
				}
			});
			ClassTrees.give(cleric, PlayerClass.CLERIC, ClassTrees.XP_RITE);
			Faction home = Factions.at(level.getServer(), level, body.blockPosition());
			if (home != null) {
				Reputation.change(cleric, home, RITES_REP);
			}
			cleric.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.rites"));
			return true;
		}
		return false;
	}

	/** A body rites are read over: a person's or a monster's (what can rise), or a player's; not a beast's carcass. */
	private static boolean mourned(Entity body) {
		if (KitchenCompat.isPlayerCorpse(body)) {
			return true;
		}
		net.minecraft.world.entity.EntityType<?> was = KitchenCompat.sourceType(body);
		return was != null && (was.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
			|| was.getCategory() == net.minecraft.world.entity.MobCategory.MISC);
	}

	/** The rites are read over {@code body}: it will never rise. */
	private static void read(ServerLevel level, Entity body) {
		body.addTag(RITES_TAG);
		Fx.sigil(level, body.position().add(0.0, 0.05, 0.0), 1.0F, 0xFFE9A0, 40);
		level.sendParticles(ParticleTypes.END_ROD, body.getX(), body.getY() + 0.3, body.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.3F);
	}

	private static int colour(ServerPlayer cleric) {
		Patron patron = Patrons.of(cleric);
		return patron == null ? 0xFFE9A0 : patron.colour & 0xFFFFFF;
	}

	// ---- the symbol -----------------------------------------------------------------------------------------------------------

	/** A raising begins: the great dead it has struck are forgotten. */
	static void raised(ServerPlayer cleric) {
		DESTROYED.remove(cleric.getUUID());
	}

	/** Destroy Undead: a great one in the symbol's cone takes a heavy blow, once a raising. */
	static void great(ServerPlayer cleric, ServerLevel level, Mob mob) {
		if (!has(cleric, DESTROY_KEY) || !DESTROYED.computeIfAbsent(cleric.getUUID(), k -> new HashSet<>()).add(mob.getId())) {
			return;
		}
		mob.hurtServer(level, level.damageSources().indirectMagic(cleric, cleric), mob.getMaxHealth() * DESTROY_BOSS);
		Fx.blast(level, mob.getBoundingBox().getCenter(), 1.6F, colour(cleric));
	}

	// ---- the blessing's gifts ---------------------------------------------------------------------------------------------------

	/** Patron's Gift on one of the band blessed: what each patron gives with it. */
	static void blessed(ServerPlayer cleric, LivingEntity who, int ticks) {
		if (!has(cleric, PATRON)) {
			return;
		}
		Patron patron = Patrons.of(cleric);
		if (patron == null) {
			return;
		}
		switch (patron) {
			// home and healing: mended as they're blessed
			case HEARTHKEEPER -> who.heal(6.0F);
			// smithing: what they hold mended a little
			case ANVIL_FATHER -> {
				for (ItemStack held : List.of(who.getMainHandItem(), who.getOffhandItem())) {
					if (held.isDamageableItem() && held.isDamaged()) {
						held.setDamageValue(Math.max(0, held.getDamageValue() - held.getMaxDamage() / 10));
					}
				}
			}
			// the sea
			case DEEP_MOTHER -> who.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, ticks, 0, true, true, true), cleric);
			// the wilds
			case GREEN_HUNTSMAN -> who.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0, true, true, true), cleric);
			// kings and war
			case CROWNED_SUN -> who.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0, true, true, true), cleric);
			// fire
			case ASH_WHISPERER -> who.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0, true, true, true), cleric);
			// the plain word: what afflicts them is lifted
			case TRUTH -> {
				for (MobEffectInstance e : List.copyOf(who.getActiveEffects())) {
					if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
						who.removeEffect(e.getEffect());
					}
				}
			}
			// the Lantern Warden's is the Hush (the dead forget you), not a blessing
			default -> {
			}
		}
	}

	/** The Lantern Warden's Hush: {@code cleric}, symbol in hand, is not one the dead go after unless struck by them lately. */
	private static boolean hushed(ServerPlayer cleric, long now) {
		return has(cleric, PATRON) && Patrons.of(cleric) == Patron.LANTERN_WARDEN && Clerics.isSymbol(cleric.getMainHandItem()) | Clerics.isSymbol(cleric.getOffhandItem())
			&& STRUCK_DEAD.getOrDefault(cleric.getUUID(), Long.MIN_VALUE / 2) + HUSH_FORGIVE < now;
	}

	// ---- the blows ------------------------------------------------------------------------------------------------------------

	/** What the Cleric's tree does to a blow: a War Priest's wrath. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer cleric) || source.getDirectEntity() != cleric || target == cleric
			|| !Classes.is(cleric, PlayerClass.CLERIC)) {
			return amount;
		}
		boolean dead = Restoration.undead(target);
		if (dead) {
			STRUCK_DEAD.put(cleric.getUUID(), cleric.level().getGameTime());
		}
		if (!has(cleric, WAR_PRIEST)) {
			return amount;
		}
		ServerLevel level = cleric.level();
		level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
		if (dead && cleric.getRandom().nextFloat() < WRATH_STUN) {
			HeldFast.afflict(target, WRATH_STUN_TICKS);
		}
		return amount + (dead ? WRATH * 2.0F : WRATH);
	}

	// ---- the tick -------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!GROUNDS.isEmpty() && server.getTickCount() % 5 == 0) {
			GROUNDS.removeIf(g -> g.until < now);
			for (Ground g : GROUNDS) {
				ground(server, g, now);
			}
		}
		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer cleric : server.getPlayerList().getPlayers()) {
				if (!hushed(cleric, now)) {
					continue;
				}
				for (Mob mob : cleric.level().getEntitiesOfClass(Mob.class, cleric.getBoundingBox().inflate(32.0),
					m -> m.getTarget() == cleric && Restoration.undead(m))) {
					mob.setTarget(null);
				}
			}
		}
		if (!READING.isEmpty() || server.getTickCount() % PRIEST_EVERY == 0) {
			priests(server, now);
		}
	}

	/** A sanctified circle at work: the dead put out of it, friends in it mended. */
	private static void ground(MinecraftServer server, Ground g, long now) {
		AABB box = new AABB(g.at, g.at).inflate(SANCTIFY_RADIUS + 1.0, 3.0, SANCTIFY_RADIUS + 1.0);
		ServerPlayer cleric = server.getPlayerList().getPlayer(g.cleric);
		for (LivingEntity e : g.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			Vec3 off = e.position().subtract(g.at).multiply(1.0, 0.0, 1.0);
			if (off.lengthSqr() > (SANCTIFY_RADIUS + 0.5) * (SANCTIFY_RADIUS + 0.5)) {
				continue;
			}
			if (e instanceof Mob mob && Restoration.undead(mob)) {
				// the dead won't set foot on holy ground
				Vec3 away = off.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : off.normalize();
				mob.setDeltaMovement(away.scale(0.5).add(0.0, 0.1, 0.0));
				mob.syncVelocity = true;
				mob.getNavigation().stop();
				continue;
			}
			if (now % SANCTIFY_EVERY < 5 && e.getHealth() < e.getMaxHealth() && (e instanceof Player
				|| cleric != null && (e instanceof TamableAnimal pet && pet.isOwnedBy(cleric) || e instanceof Villager || Followers.takesOrders(e, cleric)))) {
				e.heal(SANCTIFY_HEAL);
				g.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY(0.8), e.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}

	/**
	 * A village's own priest reads the rites over its dead: every few seconds, near each player, a grown cleric (vanilla's or
	 * MCA's) within {@link #PRIEST_SEEK} of a body lying unread in a settlement walks to it and reads them.
	 */
	private static void priests(MinecraftServer server, long now) {
		if (!KitchenCompat.corpsesExist()) {
			return;
		}
		READING.entrySet().removeIf(e -> {
			Villager priest = e.getKey();
			Entity body = e.getValue();
			if (!priest.isAlive() || body.isRemoved() || body.entityTags().contains(RITES_TAG) || priest.level() != body.level()
				|| priest.distanceTo(body) > PRIEST_SEEK * 1.5) {
				return true;
			}
			if (priest.distanceTo(body) <= PRIEST_READ) {
				priest.getNavigation().stop();
				priest.getLookControl().setLookAt(body);
				read((ServerLevel) priest.level(), body);
				return true;
			}
			if (now % 20 == 0) {
				priest.getNavigation().moveTo(body.getX(), body.getY(), body.getZ(), 0.6);
			}
			return false;
		});
		if (server.getTickCount() % PRIEST_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			for (Entity body : KitchenCompat.fallenNear(level, player.position(), PRIEST_SEEK)) {
				if (READING.containsValue(body) || Factions.at(server, level, body.blockPosition()) == null) {
					continue;
				}
				Villager priest = nearestPriest(level, body);
				if (priest != null && !READING.containsKey(priest)) {
					READING.put(priest, body);
				}
			}
		}
	}

	private static @Nullable Villager nearestPriest(ServerLevel level, Entity body) {
		Villager best = null;
		double bestD = PRIEST_SEEK * PRIEST_SEEK;
		for (Villager v : level.getEntitiesOfClass(Villager.class, body.getBoundingBox().inflate(PRIEST_SEEK), Accusations::priest)) {
			double d = v.distanceToSqr(body);
			if (d < bestD && !Fear.frightened(v) && !Downed.isDowned(v)) {
				best = v;
				bestD = d;
			}
		}
		return best;
	}
}
