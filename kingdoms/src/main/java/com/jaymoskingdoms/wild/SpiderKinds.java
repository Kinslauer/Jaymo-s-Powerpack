package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Loaded;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.17) Spiders take after where they live: Spider Overhaul's ideas (all rights reserved, by Chybx; remade here, none
 * of its code or art) put onto the game's own spiders, which Stormie's Spiders already sends up walls. A spider gets
 * its kind the first time it loads, from its biome (or depth), and keeps it; the client dresses it in its kind's
 * skin (client/mixin/SpiderSkinMixin, art from tools/gen_spider_kinds.py). Plain spiders elsewhere, and cave spiders, are left as they are.
 * <ul>
 * <li><b>Birch</b>: lies in wait without moving, then pounces on whoever comes close.</li>
 * <li><b>Desert</b>: prickly and a little tougher; dying, it bursts its spines into everything round it.</li>
 * <li><b>Swamp</b>: floats still as a lily pad, never drowns, swims fast at its prey, and its bite leaves you
 * <i>bogged</i>: you sink and can't swim up for a while.</li>
 * <li><b>Cavern</b> (deep underground): carries gems on its back (dropped when it dies) and spits web over its prey,
 * webbing it up (medieval picks: Jaymo's Powers' Webbed, a layer of web wrapped round it, {@link PowersCompat#web};
 * without Powers, a cobweb at its feet) until it's cut free or wears off.</li>
 * <li><b>Jungle</b>: quick and agile, pounces, and its bite poisons; dying, it spills a cloud of choking pollen.</li>
 * <li><b>Ice</b>: its icy venom freezes you, slowing you as powder snow does.</li>
 * <li><b>Mother</b> (GTNH patch 6; Andy: "only mother and flying spider"), a wolf spider's way: now and then a temperate
 * spider is a mother, full grown (twice the others' size) and slower, her brood riding on her back. Kill her and the
 * brood scatters off her, {@link #BROOD_LEAST}-{@link #BROOD_MOST} <b>spiderlings</b>: tiny, weak, quick, and they come
 * for whoever killed her. They carry nothing worth taking.</li>
 * <li><b>Flyer</b> (GTNH patch 6), in the woods: a jumping spider's sight and the lifting spiders' silk. It waits up in
 * the canopy or on a ledge, leaps down on prey below on a dragline, and its line hauls the prey up a few blocks off the
 * ground before it lets go: the fall is the bite.</li>
 * <li><b>Netcaster</b> (medieval picks, de-netcaster), deep down and in the spider nests ({@code nest.SilkDeeps}): bigger and
 * long in the leg, it throws a net of silk at prey from range ({@link SilkShots}; it can be stepped out of), then closes in
 * fast on what it caught. Sometimes it leaves a silk gland ({@link #GLAND_CHANCE}).</li>
 * <li><b>Spiny</b> (medieval picks, de-spiny), in the crimson and warped forests of the Nether (spiders spawn there now,
 * {@link #SPINY_WEIGHT}): small, spiked and quick, it runs at you and bursts, wrapping everyone close in web; killed
 * first, it bursts where it falls.</li>
 * </ul>
 * Lore: the silk deeps (Lore.md): deep below the nests the netcasters hunt with thrown silk, and in the crimson and warped
 * woods of the Nether a small spined spider bursts into web.
 */
public final class SpiderKinds {
	public enum Kind {
		PLAIN, BIRCH, DESERT, SWAMP, CAVERN, JUNGLE, ICE,
		/** (GTNH patch 6) A mother, her brood, and the woods' leaping, lifting spider. */
		MOTHER, SPIDERLING, FLYER,
		/** (medieval picks) The deep caves' net-thrower, and the Nether woods' bursting spined spider. */
		NETCASTER, SPINY
	}

	/** Below this height a spider is a cavern spider, whatever the biome. */
	static final int CAVERN_BELOW_Y = 0;
	/** A pouncing kind leaps at a target this far off (blocks), at most once per {@link #POUNCE_COOLDOWN} ticks. */
	static final double POUNCE_MIN = 2.5, POUNCE_MAX = 6.5;
	static final int POUNCE_COOLDOWN = 60;
	/** A cavern spider spits web at a target within this range (blocks) once per {@link #WEB_COOLDOWN} ticks. */
	static final double WEB_RANGE = 10.0;
	static final int WEB_COOLDOWN = 160;
	/** How long a spat web holds (ticks) before it falls apart (a cobweb without Powers). */
	static final int WEB_HOLD = 100;
	/** (medieval picks, de-spiders) How long a mother's bite and a flyer's dragline web their prey up (ticks; Powers' Webbed). */
	static final int MOTHER_WEB = 60, DRAGLINE_WEB = 60;
	/** Bogged (swamp bite): how long (ticks), and how hard it pulls you down in water each tick. */
	static final int BOGGED_TICKS = 120;
	static final double BOGGED_PULL = 0.06;
	/** Ice bite: freeze ticks added (140 is fully frozen), and the slowness that comes with it. */
	static final int ICE_FREEZE = 90, ICE_SLOW_TICKS = 80;
	/** Jungle bite poison (ticks), and the death cloud's radius and poison. */
	static final int JUNGLE_POISON = 100, POLLEN_POISON = 80;
	static final double POLLEN_RADIUS = 3.5;
	/** Desert death spines: radius and damage. */
	static final double SPINE_RADIUS = 3.5;
	static final float SPINE_DAMAGE = 4.0F;
	/** Swamp spiders swim at their prey this fast (blocks a tick). */
	static final double SWIM_SPEED = 0.12;
	/** (GTNH patch 6) A temperate spider is a mother at this chance: health more, speed times (less). */
	static final float MOTHER_CHANCE = 0.08F;
	static final double MOTHER_HEALTH = 10.0, MOTHER_SLOW = -0.25;
	/** Her brood: how many, how hard they scatter off her (blocks a tick), and what they are (health and bite times theirs, speed more). */
	static final int BROOD_LEAST = 3, BROOD_MOST = 6;
	static final double BROOD_SCATTER = 0.35, SPIDERLING_HEALTH = -0.8, SPIDERLING_BITE = -0.6, SPIDERLING_SPEED = 0.25;
	/** (GTNH patch 6) A woodland spider is a flyer at this chance. It leaps at prey at least LEAP_ABOVE below it and at most
	 * LEAP_REACH off sideways (blocks), at most once per FLY_COOLDOWN ticks; a leap that hasn't landed on them in LEAP_TICKS misses. */
	static final float FLYER_CHANCE = 0.25F;
	static final double LEAP_ABOVE = 2.5, LEAP_REACH = 7.0, LEAP_PULL = 0.08, LEAP_UP = 0.2, GRAB_REACH = 1.6;
	static final int FLY_COOLDOWN = 200, LEAP_TICKS = 30;
	/** Its line hauls the prey up this far (blocks) or for this long (ticks), this fast (blocks a tick), then lets go. */
	static final double LIFT_HEIGHT = 4.0, LIFT_SPEED = 0.32;
	static final int LIFT_TICKS = 25;
	/** Perched in a canopy it watches this far below and round it (blocks); idle on the ground it looks for a tree to go up, at this chance a tick. */
	static final double PERCH_WATCH = 8.0;
	static final float PERCH_CHANCE = 1.0F / 300.0F;
	static final int PERCH_SEARCH = 6;

	/** (medieval picks, de-netcaster) Below this height a spider may be a netcaster, at DEEP_NETCASTER; in a spider nest at
	 * NEST_NETCASTER (and a mother at NEST_MOTHER). */
	static final int NETCASTER_BELOW = -24;
	static final float DEEP_NETCASTER = 0.2F, NEST_NETCASTER = 0.18F, NEST_MOTHER = 0.22F;
	/** Its body: health more, speed times (more). */
	static final double NETCASTER_HEALTH = 12.0, NETCASTER_SPEED = 0.1;
	/** Its net: thrown at prey between NET_MIN and NET_RANGE (blocks), NET_SPEED blocks a tick, catching within NET_CATCH,
	 * NET_LAYERS layers holding NET_HOLD ticks; once per NET_COOLDOWN ticks. Then it closes in (Speed CLOSE_IN, CLOSE_IN_TICKS). */
	static final double NET_MIN = 4.0, NET_RANGE = 14.0, NET_SPEED = 0.7, NET_CATCH = 1.3;
	static final int NET_LAYERS = 2, NET_HOLD = 80, NET_COOLDOWN = 160, CLOSE_IN = 1, CLOSE_IN_TICKS = 80;
	/** The chance a netcaster leaves a silk gland. */
	static final float GLAND_CHANCE = 0.35F;
	/** (medieval picks, de-spiny) Spiders in the crimson and warped forests: their weight among the monsters, and how many come. */
	static final int SPINY_WEIGHT = 6, SPINY_MIN = 1, SPINY_MAX = 3;
	/** Its body: health times (less), speed times (more). */
	static final double SPINY_HEALTH = -0.5, SPINY_SPEED = 0.35;
	/** It starts to burst with prey this near (blocks), bursts SPINY_FUSE ticks later unless they get SPINY_ESCAPE away, and
	 * wraps everyone within SPINY_RADIUS in SPINY_LAYERS layers holding SPINY_HOLD ticks. */
	static final double SPINY_REACH = 1.8, SPINY_ESCAPE = 4.5, SPINY_RADIUS = 3.0;
	static final int SPINY_FUSE = 20, SPINY_LAYERS = 2, SPINY_HOLD = 80;

	public static final AttachmentType<Integer> KIND = AttachmentRegistry.create(JaymosKingdoms.id("spider_kind"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Loaded spiders with a kind, and when each may next pounce or spit. */
	private static final Map<Spider, Long> LOADED = Collections.synchronizedMap(new IdentityHashMap<>());
	/** Bogged players and mobs: who, until when. */
	private static final Map<LivingEntity, Long> BOGGED = new IdentityHashMap<>();
	/** Spat cobwebs: where, and when they fall apart. */
	private record SpatWeb(ServerLevel level, BlockPos pos, long until) {
	}

	private static final List<SpatWeb> WEBS = new ArrayList<>();

	/** A flyer's leap on its dragline: from where, at whom, until when it must land, and once it has them, how high it lifts. */
	private static final class Flight {
		final Vec3 anchor;
		final LivingEntity prey;
		long until;
		boolean lifting;
		double fromY;

		Flight(Vec3 anchor, LivingEntity prey, long until) {
			this.anchor = anchor;
			this.prey = prey;
			this.until = until;
		}
	}

	private static final Map<Spider, Flight> FLIGHTS = new IdentityHashMap<>();
	/** (medieval picks) Spiny spiders about to burst (ticks left), and those that have. */
	private static final Map<Spider, Integer> FUSES = new IdentityHashMap<>();
	private static final java.util.Set<Spider> BURST = Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private SpiderKinds() {
	}

	public static Kind of(Entity entity) {
		Integer kind = entity.getAttached(KIND);
		Kind[] kinds = Kind.values();
		return kind == null || kind < 0 || kind >= kinds.length ? Kind.PLAIN : kinds[kind];
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getType() == EntityTypes.SPIDER && entity instanceof Spider spider) {
				if (!spider.hasAttached(KIND)) {
					assign(spider, level);
				}
				if (of(spider) != Kind.PLAIN) {
					LOADED.put(spider, 0L);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Spider spider) {
				LOADED.remove(spider);
				FLIGHTS.remove(spider);
				FUSES.remove(spider);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && source.getDirectEntity() instanceof Spider spider && entity.level() instanceof ServerLevel level) {
				bit(spider, entity, level);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Spider spider && entity.level() instanceof ServerLevel level) {
				died(spider, level);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SpiderKinds::tick);
		SilkShots.register();
		// (medieval picks, de-spiny) spiders in the Nether's crimson and warped woods (each one spiny, by its biome)
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(
			net.fabricmc.fabric.api.biome.v1.BiomeSelectors.includeByKey(Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST),
			net.minecraft.world.entity.MobCategory.MONSTER, EntityTypes.SPIDER, SPINY_WEIGHT, SPINY_MIN, SPINY_MAX);
		// a mother's spiderlings carry nothing worth taking
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof Spider spider && of(spider) == Kind.SPIDERLING) {
				drops.clear();
			}
		});
	}

	private static void assign(Spider spider, ServerLevel level) {
		BlockPos pos = spider.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		Kind kind;
		float roll = spider.getRandom().nextFloat();
		if (biome.is(Biomes.CRIMSON_FOREST) || biome.is(Biomes.WARPED_FOREST)) {
			kind = Kind.SPINY;
		} else if (biome.is(com.jaymoskingdoms.wild.nest.SilkDeeps.SILK_DEEPS)) {
			// (medieval picks) a spider nest's own: netcasters and mothers among the cavern spiders
			kind = roll < NEST_NETCASTER ? Kind.NETCASTER : roll < NEST_NETCASTER + NEST_MOTHER ? Kind.MOTHER
				: pos.getY() < CAVERN_BELOW_Y ? Kind.CAVERN : Kind.PLAIN;
		} else if (pos.getY() < NETCASTER_BELOW && roll < DEEP_NETCASTER) {
			kind = Kind.NETCASTER;
		} else if (pos.getY() < CAVERN_BELOW_Y) {
			kind = Kind.CAVERN;
		} else if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			kind = Kind.BIRCH;
		} else if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			kind = Kind.DESERT;
		} else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			kind = Kind.SWAMP;
		} else if (biome.is(BiomeTags.IS_JUNGLE)) {
			kind = Kind.JUNGLE;
		} else if (biome.value().coldEnoughToSnow(pos, level.getSeaLevel())) {
			kind = Kind.ICE;
		} else if ((biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) && spider.getRandom().nextFloat() < FLYER_CHANCE) {
			kind = Kind.FLYER;
		} else if (biome.is(BiomeTags.IS_OVERWORLD) && spider.getRandom().nextFloat() < MOTHER_CHANCE) {
			kind = Kind.MOTHER;
		} else {
			kind = Kind.PLAIN;
		}
		spider.setAttached(KIND, kind.ordinal());
		switch (kind) {
			case DESERT -> modify(spider, Attributes.MAX_HEALTH, "desert_hide", 4.0, AttributeModifier.Operation.ADD_VALUE);
			case JUNGLE -> modify(spider, Attributes.MOVEMENT_SPEED, "jungle_legs", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case MOTHER -> {
				modify(spider, Attributes.MAX_HEALTH, "mother_body", MOTHER_HEALTH, AttributeModifier.Operation.ADD_VALUE);
				modify(spider, Attributes.MOVEMENT_SPEED, "mother_slow", MOTHER_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case NETCASTER -> {
				modify(spider, Attributes.MAX_HEALTH, "netcaster_body", NETCASTER_HEALTH, AttributeModifier.Operation.ADD_VALUE);
				modify(spider, Attributes.MOVEMENT_SPEED, "netcaster_legs", NETCASTER_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case SPINY -> {
				modify(spider, Attributes.MAX_HEALTH, "spiny_body", SPINY_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				modify(spider, Attributes.MOVEMENT_SPEED, "spiny_legs", SPINY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			default -> {
			}
		}
		// her size (and the flyer's) by kind
		SpiderMinds.size(spider);
		spider.setHealth(spider.getMaxHealth());
	}

	private static void modify(Spider spider, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String name, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = spider.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id(name), amount, operation));
		}
	}

	private static void bit(Spider spider, LivingEntity bitten, ServerLevel level) {
		switch (of(spider)) {
			case SWAMP -> BOGGED.put(bitten, level.getGameTime() + BOGGED_TICKS);
			case JUNGLE -> bitten.addEffect(new MobEffectInstance(MobEffects.POISON, JUNGLE_POISON, 0), spider);
			// (medieval picks) a mother wraps what she bites, for her brood
			case MOTHER -> PowersCompat.web(bitten, MOTHER_WEB, spider);
			case ICE -> {
				bitten.setTicksFrozen(Math.min(bitten.getTicksRequiredToFreeze() + 40, bitten.getTicksFrozen() + ICE_FREEZE));
				bitten.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ICE_SLOW_TICKS, 1), spider);
				level.sendParticles(ParticleTypes.SNOWFLAKE, bitten.getX(), bitten.getY(0.5), bitten.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
			}
			default -> {
			}
		}
	}

	private static void died(Spider spider, ServerLevel level) {
		Vec3 at = spider.position();
		switch (of(spider)) {
			case DESERT -> {
				level.playSound(null, spider.blockPosition(), SoundEvents.THORNS_HIT, SoundSource.HOSTILE, 1.2F, 0.8F);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.4, at.z, 30, 0.6, 0.3, 0.6, 0.6);
				for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, spider.getBoundingBox().inflate(SPINE_RADIUS),
						e -> e != spider && !(e instanceof Spider) && e.isAlive())) {
					near.hurtServer(level, level.damageSources().thorns(spider), SPINE_DAMAGE);
				}
			}
			case JUNGLE -> {
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.5, at.z, 60, 1.4, 0.6, 1.4, 0.0);
				level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, at.x, at.y + 1.2, at.z, 25, 1.2, 0.3, 1.2, 0.0);
				for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, spider.getBoundingBox().inflate(POLLEN_RADIUS),
						e -> e != spider && !(e instanceof Spider) && e.isAlive())) {
					near.addEffect(new MobEffectInstance(MobEffects.POISON, POLLEN_POISON, 0));
				}
			}
			case MOTHER -> brood(spider, level);
			case NETCASTER -> {
				if (spider.getRandom().nextFloat() < GLAND_CHANCE) {
					spider.spawnAtLocation(level, new ItemStack(com.jaymoskingdoms.wild.nest.SpiderNests.SILK_GLAND));
				}
			}
			// killed before it reached them, it bursts where it falls
			case SPINY -> {
				if (!BURST.remove(spider)) {
					burst(spider, level, false);
				}
			}
			case CAVERN -> {
				float roll = spider.getRandom().nextFloat();
				ItemStack gem = roll < 0.03F ? new ItemStack(Items.DIAMOND) : roll < 0.13F ? new ItemStack(Items.EMERALD)
					: roll < 0.45F ? new ItemStack(Items.AMETHYST_SHARD, 1 + spider.getRandom().nextInt(2)) : ItemStack.EMPTY;
				if (!gem.isEmpty()) {
					spider.spawnAtLocation(level, gem);
				}
			}
			default -> {
			}
		}
	}

	private static void tick(MinecraftServer server) {
		List<Spider> spiders;
		synchronized (LOADED) {
			spiders = new ArrayList<>(LOADED.keySet());
		}
		for (Spider spider : spiders) {
			if (!spider.isAlive() || !(spider.level() instanceof ServerLevel level)) {
				LOADED.remove(spider);
				FLIGHTS.remove(spider);
				continue;
			}
			act(spider, level, level.getGameTime());
		}
		// bogged: pulled under while in water
		for (Iterator<Map.Entry<LivingEntity, Long>> it = BOGGED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Long> entry = it.next();
			LivingEntity who = entry.getKey();
			if (!who.isAlive() || who.level().getGameTime() > entry.getValue()) {
				it.remove();
			} else if (who.isInWater()) {
				Vec3 v = who.getDeltaMovement();
				who.setDeltaMovement(v.x, Math.min(v.y, 0.0) - BOGGED_PULL, v.z);
				who.needsSync = true;
			}
		}
		// spat cobwebs fall apart
		for (Iterator<SpatWeb> it = WEBS.iterator(); it.hasNext(); ) {
			SpatWeb web = it.next();
			if (web.level().getGameTime() >= web.until()) {
				if (!Loaded.made(web.level(), web.pos())) {
					continue;
				}
				if (web.level().getBlockState(web.pos()).is(Blocks.COBWEB)) {
					web.level().removeBlock(web.pos(), false);
				}
				it.remove();
			}
		}
	}

	private static void act(Spider spider, ServerLevel level, long now) {
		Kind kind = of(spider);
		LivingEntity target = spider.getTarget();
		long ready = LOADED.getOrDefault(spider, 0L);
		switch (kind) {
			case BIRCH, JUNGLE -> {
				if (kind == Kind.BIRCH && target == null) {
					// lying in wait: no wandering
					spider.getNavigation().stop();
				}
				if (target != null && now >= ready && spider.onGround() && spider.hasLineOfSight(target)) {
					double d = spider.distanceTo(target);
					if (d >= POUNCE_MIN && d <= POUNCE_MAX) {
						Vec3 to = target.position().subtract(spider.position()).normalize();
						spider.setDeltaMovement(to.x * 0.9, 0.45, to.z * 0.9);
						spider.needsSync = true;
						level.playSound(null, spider.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.5F);
						LOADED.put(spider, now + POUNCE_COOLDOWN);
					}
				}
			}
			case SWAMP -> {
				spider.setAirSupply(spider.getMaxAirSupply());
				if (spider.isInWater()) {
					if (target == null) {
						// a lily pad: still on the surface
						spider.getNavigation().stop();
						Vec3 v = spider.getDeltaMovement();
						spider.setDeltaMovement(v.x * 0.5, Math.max(v.y, 0.02), v.z * 0.5);
					} else {
						Vec3 to = target.position().subtract(spider.position()).normalize().scale(SWIM_SPEED);
						spider.setDeltaMovement(spider.getDeltaMovement().add(to.x, to.y * 0.5, to.z));
					}
				}
			}
			case CAVERN -> {
				if (target != null && now >= ready && spider.distanceTo(target) <= WEB_RANGE && spider.hasLineOfSight(target)) {
					spit(spider, target, level);
					LOADED.put(spider, now + WEB_COOLDOWN);
				}
			}
			case FLYER -> fly(spider, target, level, now, ready);
			case NETCASTER -> {
				if (target != null && now >= ready && spider.hasLineOfSight(target)) {
					double d = spider.distanceTo(target);
					if (d >= NET_MIN && d <= NET_RANGE) {
						// at about where they'll be when it gets there
						Vec3 aim = target.position().add(target.getDeltaMovement().scale(d / NET_SPEED * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
						SilkShots.loose(level, spider, spider.getEyePosition(), aim, NET_SPEED, NET_CATCH, NET_LAYERS, NET_HOLD, true);
						LOADED.put(spider, now + NET_COOLDOWN);
						// and then it closes in
						spider.addEffect(new MobEffectInstance(MobEffects.SPEED, CLOSE_IN_TICKS, CLOSE_IN));
					}
				}
			}
			case SPINY -> {
				Integer fuse = FUSES.get(spider);
				if (fuse != null) {
					if (target == null || spider.distanceTo(target) > SPINY_ESCAPE) {
						FUSES.remove(spider);
					} else if (fuse <= 0) {
						FUSES.remove(spider);
						burst(spider, level, true);
					} else {
						FUSES.put(spider, fuse - 1);
						if (fuse % 4 == 0) {
							level.sendParticles(ParticleTypes.ITEM_COBWEB, spider.getX(), spider.getY(0.6), spider.getZ(), 3, 0.2, 0.15, 0.2, 0.0);
						}
					}
				} else if (target != null && spider.distanceTo(target) <= SPINY_REACH) {
					FUSES.put(spider, SPINY_FUSE);
					level.playSound(null, spider.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.0F, 1.9F);
					level.playSound(null, spider.blockPosition(), SoundEvents.COBWEB_STEP, SoundSource.HOSTILE, 1.2F, 0.6F);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * (medieval picks, de-spiny) A spiny spider bursts: everyone near (spiders aside) wrapped in web, and the spider's gone.
	 * {@code alive}: it chose to (and dies of it); otherwise it was killed and bursts as it falls.
	 */
	private static void burst(Spider spider, ServerLevel level, boolean alive) {
		Vec3 at = spider.position().add(0.0, spider.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 60, 1.2, 0.7, 1.2, 0.08);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.6, 0.4, 0.6, 0.4);
		level.playSound(null, spider.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.4F, 0.6F);
		level.playSound(null, spider.blockPosition(), SoundEvents.COBWEB_BREAK, SoundSource.HOSTILE, 1.4F, 0.5F);
		com.jaymoskingdoms.Hearing.noise(level, at, 16.0, null, com.jaymoskingdoms.Hearing.Kind.IMPACT);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, spider.getBoundingBox().inflate(SPINY_RADIUS),
				e -> e != spider && e.isAlive() && !SilkShots.kin(e))) {
			SilkShots.web(level, near, SPINY_LAYERS, SPINY_HOLD, spider);
		}
		if (alive) {
			BURST.add(spider);
			spider.kill(level);
		}
	}

	/** A cobweb at {@code pos} (if it's open) that falls apart after {@code ticks}: how spiders hold prey without Powers. */
	static void cobweb(ServerLevel level, BlockPos pos, int ticks) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
			WEBS.add(new SpatWeb(level, pos, level.getGameTime() + ticks));
		}
	}

	/** The mother's brood scatters off her back as she dies, and comes for whoever killed her. */
	private static void brood(Spider mother, ServerLevel level) {
		int n = BROOD_LEAST + mother.getRandom().nextInt(BROOD_MOST - BROOD_LEAST + 1);
		hatch(level, mother.position().add(0.0, mother.getBbHeight() * 0.6, 0.0), n, mother.getLastHurtByMob());
	}

	/**
	 * {@code n} spiderlings scatter out from {@code at} and come for {@code killer} (if anyone): a mother's brood as she dies,
	 * (medieval picks) an egg sac splitting open on whoever woke it, the brood queen's back.
	 */
	public static void hatch(ServerLevel level, Vec3 at, int n, @Nullable LivingEntity killer) {
		net.minecraft.util.RandomSource random = level.getRandom();
		for (int i = 0; i < n; i++) {
			Spider young = EntityTypes.SPIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (young == null) {
				return;
			}
			young.setAttached(KIND, Kind.SPIDERLING.ordinal());
			modify(young, Attributes.MAX_HEALTH, "spiderling_body", SPIDERLING_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			modify(young, Attributes.ATTACK_DAMAGE, "spiderling_bite", SPIDERLING_BITE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			modify(young, Attributes.MOVEMENT_SPEED, "spiderling_legs", SPIDERLING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			SpiderMinds.size(young);
			young.setHealth(young.getMaxHealth());
			double angle = Math.PI * 2.0 * (i + random.nextDouble() * 0.5) / n;
			young.snapTo(at.x, at.y, at.z, (float) Math.toDegrees(angle), 0.0F);
			young.setDeltaMovement(Math.cos(angle) * BROOD_SCATTER, 0.25, Math.sin(angle) * BROOD_SCATTER);
			level.addFreshEntity(young);
			if (killer != null && killer.isAlive()) {
				young.setTarget(killer);
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FROGSPAWN_HATCH, SoundSource.HOSTILE, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 10, 0.4, 0.2, 0.4, 0.02);
	}

	/**
	 * A flyer: up a tree when idle, watching from the canopy; prey below and near, it leaps down on its dragline, and once on
	 * them the line hauls them up a few blocks, then lets go.
	 */
	private static void fly(Spider spider, @Nullable LivingEntity target, ServerLevel level, long now, long ready) {
		Flight flight = FLIGHTS.get(spider);
		if (flight != null) {
			carry(spider, flight, level, now);
			return;
		}
		boolean perched = level.getBlockState(spider.blockPosition().below()).getBlock() instanceof LeavesBlock;
		if (target == null) {
			if (perched) {
				// lying in wait up there; a jumping spider hunts by sight, day or night
				spider.getNavigation().stop();
				if (spider.tickCount % 10 == 0) {
					Player below = level.getNearestPlayer(spider.getX(), spider.getY() - PERCH_WATCH * 0.5, spider.getZ(), PERCH_WATCH,
						p -> p instanceof Player pl && !pl.isCreative() && !pl.isSpectator() && spider.getY() - pl.getY() >= LEAP_ABOVE && spider.hasLineOfSight(pl));
					if (below != null) {
						spider.setTarget(below);
					}
				}
			} else if (spider.onGround() && spider.getRandom().nextFloat() < PERCH_CHANCE) {
				perch(spider, level);
			}
			return;
		}
		double dx = target.getX() - spider.getX(), dz = target.getZ() - spider.getZ();
		if (now < ready || spider.getY() - target.getY() < LEAP_ABOVE || dx * dx + dz * dz > LEAP_REACH * LEAP_REACH || !spider.hasLineOfSight(target)) {
			return;
		}
		FLIGHTS.put(spider, new Flight(spider.position(), target, now + LEAP_TICKS));
		LOADED.put(spider, now + FLY_COOLDOWN);
		spider.setDeltaMovement(dx * LEAP_PULL, LEAP_UP, dz * LEAP_PULL);
		spider.needsSync = true;
		level.playSound(null, spider.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.7F);
	}

	/** On its way down to them, or hauling them up on its line. */
	private static void carry(Spider spider, Flight flight, ServerLevel level, long now) {
		LivingEntity prey = flight.prey;
		if (!prey.isAlive() || prey.level() != level || now > flight.until) {
			FLIGHTS.remove(spider);
			spider.resetFallDistance();
			return;
		}
		if (spider.tickCount % 3 == 0) {
			silk(level, flight.anchor, spider.position().add(0.0, spider.getBbHeight(), 0.0));
		}
		if (!flight.lifting) {
			if (spider.distanceTo(prey) <= GRAB_REACH) {
				flight.lifting = true;
				flight.fromY = prey.getY();
				flight.until = now + LIFT_TICKS;
				level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.HOSTILE, 1.0F, 0.7F);
				// (medieval picks) its dragline round them as it hauls
				PowersCompat.web(prey, DRAGLINE_WEB, spider);
			}
			return;
		}
		if (prey.getY() - flight.fromY >= LIFT_HEIGHT) {
			// high enough: it lets go, and keeps its own hold on the line
			flight.until = now - 1;
			return;
		}
		Vec3 v = prey.getDeltaMovement();
		prey.setDeltaMovement(v.x * 0.5, LIFT_SPEED, v.z * 0.5);
		prey.needsSync = true;
		spider.snapTo(prey.getX(), prey.getY() + prey.getBbHeight(), prey.getZ());
		spider.setDeltaMovement(Vec3.ZERO);
		spider.resetFallDistance();
	}

	/** Up the trunk of a tree near by, to sit in its canopy. */
	private static void perch(Spider spider, ServerLevel level) {
		BlockPos from = spider.blockPosition();
		for (int tries = 0; tries < 8; tries++) {
			BlockPos column = from.offset(spider.getRandom().nextInt(PERCH_SEARCH * 2 + 1) - PERCH_SEARCH, 0,
				spider.getRandom().nextInt(PERCH_SEARCH * 2 + 1) - PERCH_SEARCH);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
			if (level.getBlockState(top.below()).getBlock() instanceof LeavesBlock && top.getY() - from.getY() >= LEAP_ABOVE + 1) {
				spider.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 1.0);
				return;
			}
		}
	}

	/** Its dragline, from where it leapt to where it is. */
	private static void silk(ServerLevel level, Vec3 from, Vec3 to) {
		ItemParticleOption thread = new ItemParticleOption(ParticleTypes.ITEM, Items.STRING);
		int steps = Math.max(2, Mth.ceil(from.distanceTo(to) * 2.0));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			level.sendParticles(thread, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A cavern spider spits a strand of web; its prey is webbed up (Powers' Webbed), or without Powers held in a cobweb where it stands. */
	private static void spit(Spider spider, LivingEntity target, ServerLevel level) {
		Vec3 from = spider.getEyePosition();
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		int steps = Mth.ceil(from.distanceTo(to) * 3.0);
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, spider.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.0F, 0.6F);
		if (PowersCompat.web(target, WEB_HOLD, spider)) {
			return;
		}
		BlockPos feet = target.blockPosition();
		if (level.getBlockState(feet).isAir()) {
			level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
			WEBS.add(new SpatWeb(level, feet, level.getGameTime() + WEB_HOLD));
		}
	}
}
