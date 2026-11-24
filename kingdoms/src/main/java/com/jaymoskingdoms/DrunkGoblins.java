package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Drunk goblins</b> (survival pick "goblin extras", Andy, 2026-11-22; Lore.md, "Goblin odd-jobs"): a goblin village that
 * has its tavern ({@link GoblinBuildings#tavern}, a brewing stand at the bar) now and then has a few of its goblins at the
 * mushroom ale: for {@link #DRUNK_MIN} to {@link #DRUNK_MIN} + {@link #DRUNK_MORE} ticks they're slow (Slowness II), stagger
 * about the village's ring when they've no foe, hiccup, and pick scuffles with whoever stands near, a shove and a hurt
 * grunt that never leaves a goblin below {@link #SPARE_BELOW} health and never turns anyone on anyone. (A drunk goblin
 * is still a goblin: a foe near him gets the same fight, only slower.) The ale itself ({@link #ALE}) is craftable (a
 * water bottle's worth: a bottle, both mushrooms and wheat); a player who drinks it staggers too.
 */
public final class DrunkGoblins {
	public static Item ALE;

	/** How likely a village with its tavern and nobody drunk has a round (each time it is looked over), how many drink, how long (ticks). */
	static final float ROUND_CHANCE = 0.12F;
	static final int MOST_DRUNK = 3, DRUNK_MIN = 1200, DRUNK_MORE = 1200;
	/** How often the drunk are looked over (ticks), and how often one staggers off (ticks), and how far (blocks). */
	static final int LOOK_EVERY = 10, STAGGER_EVERY = 40, STAGGER_FAR = 6;
	static final float STAGGER_PACE = 0.45F;
	/** A drunk goblin's Slowness (amplifier). */
	static final int SLOW = 1;
	/** A scuffle: a goblin this near (blocks) is picked at, one time in {@link #SCUFFLE_ONE_IN}, shoving {@link #SHOVE}; never when he'd be left below {@link #SPARE_BELOW}. */
	static final double SCUFFLE_REACH = 2.5;
	static final int SCUFFLE_ONE_IN = 3;
	static final float SHOVE = 1.0F, SPARE_BELOW = 6.0F;
	/** The ale: how long a player staggers (ticks). */
	static final int ALE_NAUSEA = 300, ALE_SLOW = 600;
	static final int SAY_COLOUR = 0xC9B458, LINES = 4;

	/** A drunk goblin: his level, and when he sobers up (game time). */
	private record Drunk(ServerLevel level, long until) {
	}

	private static final Map<UUID, Drunk> DRUNK = new HashMap<>();

	private DrunkGoblins() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("mushroom_ale"));
		ALE = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)
			.usingConvertsTo(Items.GLASS_BOTTLE).food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).alwaysEdible().build(),
				Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.NAUSEA, ALE_NAUSEA, 0),
					new MobEffectInstance(MobEffects.SLOWNESS, ALE_SLOW, 0)))).build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(ALE));
		ServerTickEvents.END_SERVER_TICK.register(DrunkGoblins::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DRUNK.clear());
	}

	/** The common goblins of {@code village} at home ({@link Goblins#GOBLIN} folk or villagers, not a pedlar's party). */
	static List<PathfinderMob> goblins(ServerLevel level, Faction village, Camps.CampData data) {
		AABB box = new AABB(data.center()).inflate(GoblinBuildings.HOME_RADIUS + 4);
		return level.getEntitiesOfClass(PathfinderMob.class, box, m -> m.isAlive() && m instanceof Worker w && w.role() == Goblins.GOBLIN
			&& w.factionId() == village.id() && !Pedlars.party(m));
	}

	/** Whether {@code village} has its tavern standing: the brewing stand at its bar. */
	private static boolean tavern(ServerLevel level, Camps.CampData data) {
		BlockPos stand = GoblinBuildings.TAVERN.placer(data.center(), data.gate()).at(2, 1, 5);
		return Loaded.made(level, stand) && level.getBlockState(stand).is(Blocks.BREWING_STAND);
	}

	/**
	 * (From {@link GoblinVillages#tick}, each time a village is looked over) its engineer appointed ({@link GoblinRobot}), and a
	 * round at its tavern if nobody is drunk already.
	 */
	static void look(ServerLevel level, Faction village, Camps.CampData data) {
		List<PathfinderMob> goblins = goblins(level, village, data);
		if (goblins.isEmpty()) {
			return;
		}
		GoblinRobot.appoint(level, goblins);
		if (level.getRandom().nextFloat() >= ROUND_CHANCE || goblins.stream().anyMatch(g -> DRUNK.containsKey(g.getUUID())) || !tavern(level, data)) {
			return;
		}
		List<PathfinderMob> pool = new ArrayList<>(goblins);
		int round = 1 + level.getRandom().nextInt(MOST_DRUNK);
		for (int i = 0; i < round && !pool.isEmpty(); i++) {
			PathfinderMob g = pool.remove(level.getRandom().nextInt(pool.size()));
			DRUNK.put(g.getUUID(), new Drunk(level, level.getGameTime() + DRUNK_MIN + level.getRandom().nextInt(DRUNK_MORE)));
		}
		JaymosKingdoms.LOGGER.debug("{}'s goblins are at the ale", village.name());
	}

	private static void tick(MinecraftServer server) {
		if (DRUNK.isEmpty() || server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Drunk>> it = DRUNK.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Drunk> e = it.next();
			ServerLevel level = e.getValue().level();
			if (!(level.getEntity(e.getKey()) instanceof PathfinderMob goblin) || !goblin.isAlive() || level.getGameTime() >= e.getValue().until()) {
				it.remove();
				continue;
			}
			goblin.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LOOK_EVERY * 4, SLOW, true, false));
			if (goblin.tickCount % STAGGER_EVERY < LOOK_EVERY) {
				stagger(level, goblin);
			}
			if (goblin.getRandom().nextInt(SCUFFLE_ONE_IN) == 0) {
				scuffle(level, goblin);
			}
		}
	}

	/** He lurches off a few steps (when he has no foe), hiccups, and now and then says something. */
	private static void stagger(ServerLevel level, PathfinderMob goblin) {
		level.sendParticles(ParticleTypes.NOTE, goblin.getX(), goblin.getY(1.2), goblin.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		goblin.playSound(SoundEvents.VILLAGER_AMBIENT, 0.8F, 0.6F);
		if (goblin.getRandom().nextInt(4) == 0) {
			Speech.say(level, goblin, Dialects.tr(goblin, "talk.jaymoskingdoms.drunk." + goblin.getRandom().nextInt(LINES)), SAY_COLOUR, 12.0);
		}
		if (Rulers.target(goblin) != null || McaCompat.busy(goblin)) {
			return;
		}
		Vec3 to = LandRandomPos.getPos(goblin, STAGGER_FAR, 3);
		if (to == null) {
			return;
		}
		if (goblin instanceof Villager v) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(to), STAGGER_PACE, 1));
		} else {
			goblin.getNavigation().moveTo(to.x, to.y, to.z, STAGGER_PACE);
		}
	}

	/** A shove at a goblin nearby: a hurt grunt (no blame, so no fight, and never to the last few hearts). */
	private static void scuffle(ServerLevel level, PathfinderMob goblin) {
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, goblin.getBoundingBox().inflate(SCUFFLE_REACH),
			o -> o != goblin && Goblins.isGoblin(o) && o.getHealth() > SPARE_BELOW && !Pedlars.party(o))) {
			Vec3 away = other.position().subtract(goblin.position()).multiply(1.0, 0.0, 1.0);
			Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.4);
			goblin.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			other.hurtServer(level, level.damageSources().generic(), SHOVE);
			other.push(new Vec3(push.x, 0.2, push.z));
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, goblin.getX(), goblin.getY(1.4), goblin.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			level.playSound(null, goblin.getX(), goblin.getY(), goblin.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.6F, 0.8F);
			return;
		}
	}
}
