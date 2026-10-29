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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/**
 * The Omens and their Worky drones, in the future only. A squad is sent out rarely: with the hordes' check
 * ({@link FutureMachines#check}, every 2 s for each player in the future) a squad turns up about once in
 * {@link #SQUAD_CHANCE} checks (some twelve minutes) while no Omen is within {@link #SQUAD_SPACE} blocks of the player,
 * 30 to 50 blocks out: one mech, now and then two or three (all the same build, red or blue), each with
 * {@link #DRONES_MIN} to {@link #DRONES_MAX} drones. Drones only exist in a squad (and leave when their mech is gone).
 * There are no spawn eggs, as with the future's other mobs; {@code /summon} does it.
 */
public final class FutureOmens {
	public static EntityType<FutureOmen> OMEN_RED;
	public static EntityType<FutureOmen> OMEN_BLUE;
	public static EntityType<FutureWorky> WORKY;

	/** One check in this many sends a squad, while no Omen is within this far of the player. */
	static final int SQUAD_CHANCE = 360;
	static final double SQUAD_SPACE = 160.0;
	/** A squad has one mech; this chance of a second, and (given a second) this chance of a third. */
	static final float SECOND_CHANCE = 0.35F, THIRD_CHANCE = 0.4F;
	/** Each mech brings this many drones. */
	static final int DRONES_MIN = 1, DRONES_MAX = 2;
	/** The mechs of a squad stand this far apart. */
	static final int GAP = 5;

	private FutureOmens() {
	}

	static void init() {
		OMEN_RED = register("omen_red", EntityType.Builder.<FutureOmen>of(FutureOmen::new, MobCategory.CREATURE)
			.sized(2.2F, 4.6F).eyeHeight(4.1F).clientTrackingRange(12).noLootTable());
		OMEN_BLUE = register("omen_blue", EntityType.Builder.<FutureOmen>of(FutureOmen::new, MobCategory.CREATURE)
			.sized(2.2F, 4.6F).eyeHeight(4.1F).clientTrackingRange(12).noLootTable());
		WORKY = register("worky_drone", EntityType.Builder.<FutureWorky>of(FutureWorky::new, MobCategory.CREATURE)
			.sized(0.8F, 1.3F).eyeHeight(0.7F).clientTrackingRange(10).noLootTable());
		FabricDefaultAttributeRegistry.register(OMEN_RED, FutureOmen.createAttributes());
		FabricDefaultAttributeRegistry.register(OMEN_BLUE, FutureOmen.createAttributes());
		FabricDefaultAttributeRegistry.register(WORKY, FutureWorky.createAttributes());
	}

	private static <T extends Mob> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** With the hordes' check, for a player in the future. */
	static void check(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		if (random.nextInt(SQUAD_CHANCE) != 0
			|| !level.getEntitiesOfClass(FutureOmen.class, player.getBoundingBox().inflate(SQUAD_SPACE), FutureOmen::isAlive).isEmpty()) {
			return;
		}
		BlockPos at = FutureMachines.spot(level, player, random);
		if (at == null) {
			return;
		}
		squad(level, at, random);
	}

	/** A squad of one to three mechs (one build) at {@code at}, each with its drones. Returns how many mechs stood. */
	static int squad(ServerLevel level, BlockPos at, RandomSource random) {
		EntityType<FutureOmen> build = random.nextBoolean() ? OMEN_RED : OMEN_BLUE;
		int mechs = 1;
		if (random.nextFloat() < SECOND_CHANCE) {
			mechs = random.nextFloat() < THIRD_CHANCE ? 3 : 2;
		}
		int stood = 0;
		for (int i = 0; i < mechs; i++) {
			FutureOmen mech = build.create(level, EntitySpawnReason.EVENT);
			if (mech == null || !FutureMachines.place(level, mech, at.offset(i * GAP + random.nextInt(3) - 1, 0, random.nextInt(5) - 2))) {
				continue;
			}
			level.addFreshEntity(mech);
			stood++;
			int drones = DRONES_MIN + random.nextInt(DRONES_MAX - DRONES_MIN + 1);
			for (int d = 0; d < drones; d++) {
				FutureWorky drone = WORKY.create(level, EntitySpawnReason.EVENT);
				if (drone != null) {
					drone.snapTo(mech.getX() + random.nextInt(5) - 2, mech.getY() + 4.5 + random.nextDouble(), mech.getZ() + random.nextInt(5) - 2,
						random.nextFloat() * 360.0F, 0.0F);
					drone.follow(mech);
					level.addFreshEntity(drone);
				}
			}
		}
		return stood;
	}
}
