package com.jaymospowers.dino;

import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.FossilSkeleton;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.entity.TranqDart;
import com.jaymospowers.dino.entity.VenomSpit;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class DinoEntities {
	private static final Map<Species, EntityType<Dinosaur>> DINOSAURS = new EnumMap<>(Species.class);
	public static EntityType<TranqDart> TRANQ_DART;
	public static EntityType<VenomSpit> VENOM_SPIT;
	public static EntityType<FossilSkeleton> FOSSIL_SKELETON;

	private DinoEntities() {
	}

	public static EntityType<Dinosaur> dinosaur(Species species) {
		return DINOSAURS.get(species);
	}

	public static Species speciesOf(EntityType<?> type) {
		for (Map.Entry<Species, EntityType<Dinosaur>> e : DINOSAURS.entrySet()) {
			if (e.getValue() == type) {
				return e.getKey();
			}
		}
		return Species.VELOCIRAPTOR;
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Dino.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		for (Species s : Species.values()) {
			EntityType<Dinosaur> type = register(s.id, EntityType.Builder.of(Dinosaur::new, MobCategory.CREATURE)
				.sized(s.width, s.height).eyeHeight(s.eyeHeight).clientTrackingRange(s.big() ? 12 : 10));
			DINOSAURS.put(s, type);
			FabricDefaultAttributeRegistry.register(type, Dinosaur.createAttributes(s));
		}
		TRANQ_DART = register("tranq_dart", EntityType.Builder.<TranqDart>of(TranqDart::new, MobCategory.MISC)
			.sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
		VENOM_SPIT = register("dino_venom_spit", EntityType.Builder.<VenomSpit>of(VenomSpit::new, MobCategory.MISC)
			.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
		FOSSIL_SKELETON = register("fossil_skeleton", EntityType.Builder.<FossilSkeleton>of(FossilSkeleton::new, MobCategory.MISC)
			.sized(1.0F, 2.0F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
	}
}
