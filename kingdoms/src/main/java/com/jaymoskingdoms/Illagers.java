package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The illagers of an illager camp: real vanilla pillagers (crossbows), vindicators (axes), evokers (in grown camps) and a
 * ravager, and the camp's <b>warlord</b> (a vindicator mini-boss with a diamond axe and the ominous banner on his head,
 * a white boss bar, and a rally when he weakens). Each carries a {@link #TAG} naming its camp and its part in it, so the
 * camp systems ({@link Camps}, {@link IllagerCamps}, {@link Raids}) can count, send and scatter them.
 *
 * <p>Vanilla gives them their hatred of players, villagers and golems, and their alerts (illagers only call other
 * illagers). This class adds the rest: they hunt every faction's folk (kingdom folk, villagers' guards, barbarians) on
 * sight, keep to their camp, never despawn, and never join a vanilla raid. Kingdom folk and barbarians already attack
 * them on sight (they're monsters).
 */
public final class Illagers {
	/** Their parts in a camp (also their roster keys in the camp's {@link Faction.Holdings}). */
	public static final int PILLAGER = 20, VINDICATOR = 21, EVOKER = 22, WARLORD = 23, RAVAGER = 24;
	/** How far they spot folk to hunt. */
	static final double SIGHT = 28.0;
	/** The warlord: health, his blows (on top of his axe), armour, how hard he is to shove. */
	static final double WARLORD_HEALTH = 200.0, WARLORD_DAMAGE = 6.0, WARLORD_ARMOR = 8.0, WARLORD_TOUGHNESS = 2.0, WARLORD_KNOCKBACK_RESIST = 0.6;
	/** He keeps this near his seat (his boss bar's range is {@link BossBars#RANGE}). */
	static final int WARLORD_HOME_RADIUS = 8;
	/** His rally: at this share of his health, for this long (ticks); illagers this near him are called to his foe. */
	static final float RALLY_AT = 0.4F;
	static final int RALLY_TICKS = 20 * 60;
	static final double RALLY_RANGE = 48.0;
	/** How often they look for folk to hunt (ticks), and how often a stray is sent back home (ticks). */
	static final int THINK_TICKS = 20, HOME_TICKS = 100;
	/** A load carried home from a raid drops for sure (a drop chance over 1 is kept whole). */
	static final float LOAD_DROP = 2.0F;

	/** What an illager is to its camp: the camp's faction id, its part, whether it's on a raid, whether (the warlord) it has rallied. */
	public record Tag(int camp, int role, boolean raid, boolean rallied) {
		public static final Codec<Tag> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("camp").forGetter(Tag::camp),
				Codec.INT.fieldOf("role").forGetter(Tag::role),
				Codec.BOOL.optionalFieldOf("raid", false).forGetter(Tag::raid),
				Codec.BOOL.optionalFieldOf("rallied", false).forGetter(Tag::rallied))
			.apply(i, Tag::new));
	}

	/** On every camp illager (saved with it). */
	static final AttachmentType<Tag> TAG = AttachmentRegistry.create(JaymosKingdoms.id("illager"), builder -> builder.persistent(Tag.CODEC));

	/** The camp illagers loaded now. */
	private static final Set<Mob> LOADED = new HashSet<>();
	/** Warlords' boss bars (entity UUID → bar). */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** Illagers just loaded: next tick, those with no camp to belong to (or a second warlord) are dismissed. */
	private static final List<Mob> DISMISS = new ArrayList<>();

	private Illagers() {
	}

	/** Their roster names and the events (call once at init, from {@link Camps#register}). */
	static void init() {
		Folk.defineRole(new Folk.Role(PILLAGER, "pillager", true, false, null, (folk, faction) -> {
		}));
		Folk.defineRole(new Folk.Role(VINDICATOR, "vindicator", true, false, null, (folk, faction) -> {
		}));
		Folk.defineRole(new Folk.Role(EVOKER, "evoker", true, false, null, (folk, faction) -> {
		}));
		Folk.defineRole(new Folk.Role(RAVAGER, "ravager", true, false, null, (folk, faction) -> {
		}));
		Folk.defineRole(new Folk.Role(WARLORD, "warlord", true, true, BossEvent.BossBarColor.WHITE, (folk, faction) -> {
		}));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> unloaded(entity));
		ServerTickEvents.END_SERVER_TICK.register(Illagers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	// ---- who they are ------------------------------------------------------------------------------------------------

	static @Nullable Tag tag(Entity entity) {
		return entity.getAttached(TAG);
	}

	/** It's one of a camp's illagers. */
	static boolean isCampIllager(Entity entity) {
		return entity instanceof Mob && tag(entity) != null;
	}

	static boolean isFighter(int role) {
		return role == PILLAGER || role == VINDICATOR;
	}

	/** The camp's fighting illagers on its roster (pillagers and vindicators). */
	static int fighters(Faction camp) {
		return camp.holdings().count(PILLAGER) + camp.holdings().count(VINDICATOR);
	}

	private static EntityType<? extends Mob> type(int role) {
		return switch (role) {
			case VINDICATOR, WARLORD -> EntityTypes.VINDICATOR;
			case EVOKER -> EntityTypes.EVOKER;
			case RAVAGER -> EntityTypes.RAVAGER;
			default -> EntityTypes.PILLAGER;
		};
	}

	// ---- spawning ----------------------------------------------------------------------------------------------------

	/**
	 * One of {@code camp}'s illagers at {@code at}, keeping {@code radius} of {@code home}: equipped as vanilla does, never
	 * despawning, never joining a vanilla raid. Counted on the camp's roster unless it's the warlord or on a raid.
	 */
	static @Nullable Mob spawn(ServerLevel level, Faction camp, int role, BlockPos at, BlockPos home, int radius, boolean raid) {
		Mob mob = type(role).create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
			raider.setPatrolLeader(false);
		}
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.setHomeTo(home, radius);
		mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 4.0);
		if (role == WARLORD) {
			outfitWarlord(level, mob, camp);
		}
		mob.setAttached(TAG, new Tag(camp.id(), role, raid, false));
		level.addFreshEntity(mob);
		if (!raid && role != WARLORD) {
			Factions.update(level.getServer(), camp.id(), f -> f.withHoldings(f.holdings().adjust(role, 1)));
		}
		return mob;
	}

	/** The warlord: a big vindicator with a diamond axe, the ominous banner on his head (it drops when he dies), a boss bar. */
	private static void outfitWarlord(ServerLevel level, Mob mob, Faction camp) {
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(WARLORD_HEALTH);
		mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WARLORD_DAMAGE);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(WARLORD_ARMOR);
		mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(WARLORD_TOUGHNESS);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(WARLORD_KNOCKBACK_RESIST);
		mob.setHealth(mob.getMaxHealth());
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		mob.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
		mob.setDropChance(EquipmentSlot.HEAD, LOAD_DROP);
		mob.setCustomName(Component.translatable("entity.jaymoskingdoms.warlord_of", camp.name()));
	}

	/** Seats a new warlord at {@code spot} and makes him the camp's leader. Returns the camp as stored. */
	static Faction crown(ServerLevel level, Faction camp, BlockPos spot) {
		Mob warlord = spawn(level, camp, WARLORD, spot, spot, WARLORD_HOME_RADIUS, false);
		return Factions.update(level.getServer(), camp.id(), f -> f.withLeader(warlord == null ? java.util.Optional.empty() : java.util.Optional.of(warlord.getUUID())));
	}

	// ---- carrying (for raids) ------------------------------------------------------------------------------------------

	/** What it carries home from a raid (shown in its off hand), or empty. */
	static ItemStack carried(Mob mob) {
		return mob.getOffhandItem();
	}

	static void setCarried(Mob mob, ItemStack load) {
		mob.setItemSlot(EquipmentSlot.OFFHAND, load);
		mob.setDropChance(EquipmentSlot.OFFHAND, load.isEmpty() ? 0.0F : LOAD_DROP);
	}

	static ItemStack takeCarried(Mob mob) {
		ItemStack load = mob.getOffhandItem();
		setCarried(mob, ItemStack.EMPTY);
		return load;
	}

	// ---- events ------------------------------------------------------------------------------------------------------

	private static void loaded(Entity entity) {
		if (!(entity instanceof Mob mob) || mob.level().isClientSide()) {
			return;
		}
		Tag tag = tag(mob);
		if (tag == null) {
			return;
		}
		LOADED.add(mob);
		// (looked at next tick: a new warlord is made leader just after he's added)
		DISMISS.add(mob);
	}

	/** Its camp is gone (cleared while it was away), or it's a warlord who isn't his camp's leader (a second one): it slinks off. */
	private static boolean unwanted(MinecraftServer server, Mob mob, Tag tag) {
		Faction camp = Factions.get(server, tag.camp());
		return camp == null || camp.fallen() || (tag.role() == WARLORD && !camp.leader().map(mob.getUUID()::equals).orElse(false));
	}

	private static void unloaded(Entity entity) {
		if (entity instanceof Mob mob) {
			LOADED.remove(mob);
			ServerBossEvent bar = BARS.remove(mob.getUUID());
			if (bar != null) {
				bar.removeAllPlayers();
			}
		}
	}

	/** One of a camp's illagers died: the warlord's death clears the camp; a raider's is the raid's; anyone else leaves a gap in the roster. */
	static void died(ServerLevel level, LivingEntity entity, DamageSource source) {
		Tag tag = tag(entity);
		if (tag == null) {
			return;
		}
		ServerBossEvent bar = BARS.remove(entity.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		if (tag.role() == WARLORD) {
			Camps.chiefSlain(level, entity, tag.camp(), source);
		} else if (tag.raid()) {
			Raids.raiderDied(level, entity, tag.camp(), source, false);
		} else {
			Factions.update(level.getServer(), tag.camp(), f -> f.withHoldings(f.holdings().adjust(tag.role(), -1)));
		}
	}

	/** {@code camp} is cleared: its illagers this near its fire scatter (dropping whatever they carry). */
	static void scatter(ServerLevel level, int camp, BlockPos center, double range, @Nullable Entity spare) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(range), m -> m != spare && m.isAlive()
			&& tag(m) != null && tag(m).camp() == camp)) {
			dismiss(level, mob);
		}
	}

	private static void dismiss(ServerLevel level, Mob mob) {
		ItemStack load = takeCarried(mob);
		if (!load.isEmpty()) {
			mob.spawnAtLocation(level, load);
		}
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		mob.discard();
	}

	// ---- their minds ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (!DISMISS.isEmpty()) {
			List<Mob> gone = List.copyOf(DISMISS);
			DISMISS.clear();
			for (Mob mob : gone) {
				Tag tag = tag(mob);
				if (mob.isAlive() && tag != null && !tag.raid() && unwanted(server, mob, tag) && mob.level() instanceof ServerLevel here) {
					dismiss(here, mob);
				}
			}
		}
		long now = level.getGameTime();
		if (now % 5 == 0) {
			for (Mob mob : List.copyOf(LOADED)) {
				Tag tag = tag(mob);
				if (tag != null && tag.role() == WARLORD && mob.isAlive() && mob.level() == level) {
					bar(level, mob);
				}
			}
		}
		if (now % THINK_TICKS != 0) {
			return;
		}
		for (Mob mob : List.copyOf(LOADED)) {
			if (mob.isRemoved() || !mob.isAlive()) {
				LOADED.remove(mob);
				continue;
			}
			Tag tag = tag(mob);
			if (tag != null && mob.level() instanceof ServerLevel here) {
				think(here, mob, tag, now);
			}
		}
	}

	/** Hunts folk it can see; goes home when it has strayed with nothing to fight; the warlord rallies when he weakens. */
	private static void think(ServerLevel level, Mob mob, Tag tag, long now) {
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
			Folk prey = level.getEntitiesOfClass(Folk.class, mob.getBoundingBox().inflate(range, range / 2.0, range),
					f -> f.isAlive() && !f.isInvisible() && mob.getSensing().hasLineOfSight(f)).stream()
				.min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
			if (prey != null) {
				mob.setTarget(prey);
				target = prey;
			}
		}
		if (target == null && !tag.raid() && mob.hasHome() && !mob.isWithinHome() && mob.getNavigation().isDone() && now % HOME_TICKS == 0) {
			BlockPos home = mob.getHomePosition();
			mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
		}
		if (tag.role() == WARLORD && !tag.rallied() && mob.getHealth() <= mob.getMaxHealth() * RALLY_AT) {
			rally(level, mob, tag);
		}
	}

	/** The warlord's second wind: strength, resistance and speed, a horn, and every illager of his camp near him sent at his foe. */
	private static void rally(ServerLevel level, Mob warlord, Tag tag) {
		warlord.setAttached(TAG, new Tag(tag.camp(), tag.role(), tag.raid(), true));
		warlord.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 1));
		warlord.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_TICKS, 1));
		warlord.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_TICKS, 0));
		LivingEntity foe = warlord.getTarget();
		if (foe != null) {
			for (Mob other : level.getEntitiesOfClass(Mob.class, warlord.getBoundingBox().inflate(RALLY_RANGE),
				m -> m != warlord && m.isAlive() && tag(m) != null && tag(m).camp() == tag.camp())) {
				other.setTarget(foe);
			}
		}
		level.playSound(null, warlord.getX(), warlord.getY(), warlord.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.9F);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, warlord.getBoundingBox().inflate(32.0))) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warlord_rallies", warlord.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** The warlord's boss bar: his health, shown to the players who've hit him while they stay near (0.5.19). */
	private static void bar(ServerLevel level, Mob warlord) {
		ServerBossEvent bar = BARS.computeIfAbsent(warlord.getUUID(), id -> new ServerBossEvent(id, warlord.getDisplayName(),
			BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10));
		bar.setProgress(warlord.getHealth() / warlord.getMaxHealth());
		BossBars.show(level, warlord, bar);
	}

	/** A camp's warlord (he gets a boss bar). */
	static boolean warlord(Entity entity) {
		Tag tag = tag(entity);
		return tag != null && tag.role() == WARLORD;
	}

	static void clear() {
		LOADED.clear();
		DISMISS.clear();
		for (ServerBossEvent bar : BARS.values()) {
			bar.removeAllPlayers();
		}
		BARS.clear();
	}

	// ---- for the camp --------------------------------------------------------------------------------------------------

	/** {@code camp}'s illagers loaded now, by role (raiders left out). */
	static Map<Integer, Integer> census(int camp) {
		Map<Integer, Integer> out = new HashMap<>();
		for (Mob mob : LOADED) {
			Tag tag = tag(mob);
			if (tag != null && tag.camp() == camp && !tag.raid() && mob.isAlive() && tag.role() != WARLORD) {
				out.merge(tag.role(), 1, Integer::sum);
			}
		}
		return out;
	}

	static Set<Integer> rosterRoles() {
		return Set.of(PILLAGER, VINDICATOR, EVOKER, RAVAGER);
	}
}
