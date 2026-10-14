package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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
 * holding it in a cobweb until it's cut free or wears off.</li>
 * <li><b>Jungle</b>: quick and agile, pounces, and its bite poisons; dying, it spills a cloud of choking pollen.</li>
 * <li><b>Ice</b>: its icy venom freezes you, slowing you as powder snow does.</li>
 * </ul>
 */
public final class SpiderKinds {
	public enum Kind {
		PLAIN, BIRCH, DESERT, SWAMP, CAVERN, JUNGLE, ICE
	}

	/** Below this height a spider is a cavern spider, whatever the biome. */
	static final int CAVERN_BELOW_Y = 0;
	/** A pouncing kind leaps at a target this far off (blocks), at most once per {@link #POUNCE_COOLDOWN} ticks. */
	static final double POUNCE_MIN = 2.5, POUNCE_MAX = 6.5;
	static final int POUNCE_COOLDOWN = 60;
	/** A cavern spider spits web at a target within this range (blocks) once per {@link #WEB_COOLDOWN} ticks. */
	static final double WEB_RANGE = 10.0;
	static final int WEB_COOLDOWN = 160;
	/** How long a spat cobweb holds (ticks) before it falls apart. */
	static final int WEB_HOLD = 100;
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
	}

	private static void assign(Spider spider, ServerLevel level) {
		BlockPos pos = spider.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		Kind kind;
		if (pos.getY() < CAVERN_BELOW_Y) {
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
		} else {
			kind = Kind.PLAIN;
		}
		spider.setAttached(KIND, kind.ordinal());
		switch (kind) {
			case DESERT -> modify(spider, Attributes.MAX_HEALTH, "desert_hide", 4.0, AttributeModifier.Operation.ADD_VALUE);
			case JUNGLE -> modify(spider, Attributes.MOVEMENT_SPEED, "jungle_legs", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			default -> {
			}
		}
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
			default -> {
			}
		}
	}

	/** A cavern spider spits a strand of web; its prey is held in a cobweb where it stands. */
	private static void spit(Spider spider, LivingEntity target, ServerLevel level) {
		Vec3 from = spider.getEyePosition();
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		int steps = Mth.ceil(from.distanceTo(to) * 3.0);
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, spider.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.0F, 0.6F);
		BlockPos feet = target.blockPosition();
		if (level.getBlockState(feet).isAir()) {
			level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
			WEBS.add(new SpatWeb(level, feet, level.getGameTime() + WEB_HOLD));
		}
	}
}
