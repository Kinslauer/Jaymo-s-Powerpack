package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The giant rigs ({@link FutureRig}), in the future only. With the hordes' check ({@link FutureMachines#check}, every 2 s for
 * each player there) a rig turns up about once in {@link #CHANCE} checks, but only where the dead are gathered
 * ({@link #HORDE_MIN} of them or more within {@link FutureMachines#NEAR}) and no rig is within {@link #SPACE}: they are sent to
 * fight the hordes, not to wander. Which maker's, Frostborn or the Tinkerer's, is a coin; the
 * {@link FutureMachines#SPAWN_MIN}-{@link FutureMachines#SPAWN_MAX} block spawn spot is the machines'. There are no spawn eggs, as
 * with the future's other mobs; {@code /summon} does it.
 */
public final class FutureRigs {
	public static EntityType<FutureRig> FROSTBORN;
	public static EntityType<FutureRig> TINKERER;

	/** One check in this many sends a rig, while the dead are thick near the player. */
	static final int CHANCE = 300;
	/** How many of the dead make a horde worth a rig, and how far a rig keeps from another (blocks). */
	static final int HORDE_MIN = 8;
	static final double SPACE = 160.0;

	private FutureRigs() {
	}

	static void init() {
		FROSTBORN = register("frostborn_siege_rig");
		TINKERER = register("tinkerer_colossus");
		FabricDefaultAttributeRegistry.register(FROSTBORN, FutureRig.createRigAttributes());
		FabricDefaultAttributeRegistry.register(TINKERER, FutureRig.createRigAttributes());
	}

	private static EntityType<FutureRig> register(String name) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<FutureRig>of(FutureRig::new, MobCategory.MONSTER)
			.sized(4.2F, 7.8F).eyeHeight(6.8F).clientTrackingRange(16).noLootTable().build(key));
	}

	/** With the hordes' check, for a player in the future. */
	static void check(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		if (random.nextInt(CHANCE) != 0
			|| level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(FutureMachines.NEAR), Zombie::isAlive).size() < HORDE_MIN
			|| !level.getEntitiesOfClass(FutureRig.class, player.getBoundingBox().inflate(SPACE), FutureRig::isAlive).isEmpty()) {
			return;
		}
		BlockPos at = FutureMachines.spot(level, player, random);
		FutureRig rig = at == null ? null : (random.nextBoolean() ? FROSTBORN : TINKERER).create(level, EntitySpawnReason.EVENT);
		if (rig != null && FutureMachines.place(level, rig, at)) {
			level.addFreshEntity(rig);
		}
	}
}
