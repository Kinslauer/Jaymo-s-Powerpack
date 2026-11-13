package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The bodies a familiar wears: an owl and a raven (the parrot's frame), a cat, a rat (the rabbit's frame) and a frog, each in its
 * own coat. They are spirit animals: they have no wants of their own (every errand comes from {@link Familiars}), they never
 * despawn, and a death leaves ash. Each kind is its own entity type so it can be drawn in its own colours.
 */
public final class FamiliarBodies {
	/** A familiar's health, and the pace it is driven at. */
	static final double HEALTH = 8.0;
	public static final List<String> KINDS = List.of("owl", "cat", "rat", "raven", "frog");

	public static EntityType<Owl> OWL;
	public static EntityType<Mouser> CAT;
	public static EntityType<Rat> RAT;
	public static EntityType<Raven> RAVEN;
	public static EntityType<Toad> FROG;

	private FamiliarBodies() {
	}

	private static <T extends net.minecraft.world.entity.Mob> EntityType<T> make(String name, EntityType.EntityFactory<T> factory, float w, float h,
		AttributeSupplier.Builder attributes) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("familiar_" + name));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(factory, MobCategory.MISC).sized(w, h).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(type, attributes.add(Attributes.MAX_HEALTH, HEALTH));
		return type;
	}

	static void register() {
		OWL = make("owl", Owl::new, 0.5F, 0.9F, Parrot.createAttributes());
		CAT = make("cat", Mouser::new, 0.6F, 0.7F, Cat.createAttributes());
		RAT = make("rat", Rat::new, 0.4F, 0.5F, Rabbit.createAttributes());
		RAVEN = make("raven", Raven::new, 0.5F, 0.9F, Parrot.createAttributes());
		FROG = make("frog", Toad::new, 0.5F, 0.5F, Frog.createAttributes());
	}

	/** The type of the body for {@code kind}. */
	static EntityType<? extends net.minecraft.world.entity.Mob> type(String kind) {
		return switch (kind) {
			case "cat" -> CAT;
			case "rat" -> RAT;
			case "raven" -> RAVEN;
			case "frog" -> FROG;
			default -> OWL;
		};
	}

	/** The kind a body is, or "" for any other entity. */
	static String kindOf(net.minecraft.world.entity.Entity e) {
		EntityType<?> t = e.getType();
		return t == OWL ? "owl" : t == CAT ? "cat" : t == RAT ? "rat" : t == RAVEN ? "raven" : t == FROG ? "frog" : "";
	}

	public static final class Owl extends Parrot {
		public Owl(EntityType<? extends Owl> type, Level level) {
			super(type, level);
		}

		@Override
		protected void registerGoals() {
			this.goalSelector.addGoal(0, new FloatGoal(this));
		}

		@Override
		public InteractionResult mobInteract(Player player, InteractionHand hand) {
			return Familiars.interact(this, player, hand);
		}

		@Override
		public void die(DamageSource source) {
			Familiars.died(this);
			super.die(source);
		}

		@Override
		public boolean removeWhenFarAway(double distance) {
			return false;
		}

		@Override
		public boolean canSitOnShoulder() {
			return false;
		}
	}

	public static final class Raven extends Parrot {
		public Raven(EntityType<? extends Raven> type, Level level) {
			super(type, level);
		}

		@Override
		protected void registerGoals() {
			this.goalSelector.addGoal(0, new FloatGoal(this));
		}

		@Override
		public InteractionResult mobInteract(Player player, InteractionHand hand) {
			return Familiars.interact(this, player, hand);
		}

		@Override
		public void die(DamageSource source) {
			Familiars.died(this);
			super.die(source);
		}

		@Override
		public boolean removeWhenFarAway(double distance) {
			return false;
		}

		@Override
		public boolean canSitOnShoulder() {
			return false;
		}
	}

	public static final class Mouser extends Cat {
		public Mouser(EntityType<? extends Mouser> type, Level level) {
			super(type, level);
		}

		@Override
		protected void registerGoals() {
			this.goalSelector.addGoal(0, new FloatGoal(this));
		}

		@Override
		public InteractionResult mobInteract(Player player, InteractionHand hand) {
			return Familiars.interact(this, player, hand);
		}

		@Override
		public void die(DamageSource source) {
			Familiars.died(this);
			super.die(source);
		}

		@Override
		public boolean removeWhenFarAway(double distance) {
			return false;
		}
	}

	public static final class Rat extends Rabbit {
		public Rat(EntityType<? extends Rat> type, Level level) {
			super(type, level);
		}

		@Override
		protected void registerGoals() {
			this.goalSelector.addGoal(0, new FloatGoal(this));
		}

		@Override
		public InteractionResult mobInteract(Player player, InteractionHand hand) {
			return Familiars.interact(this, player, hand);
		}

		@Override
		public void die(DamageSource source) {
			Familiars.died(this);
			super.die(source);
		}

		@Override
		public boolean removeWhenFarAway(double distance) {
			return false;
		}
	}

	public static final class Toad extends Frog {
		public Toad(EntityType<? extends Toad> type, Level level) {
			super(type, level);
		}

		@Override
		protected void registerGoals() {
			this.goalSelector.addGoal(0, new FloatGoal(this));
		}

		/** The frog's own brain is left idle: a familiar goes where it is sent. */
		@Override
		protected void customServerAiStep(ServerLevel level) {
		}

		@Override
		public InteractionResult mobInteract(Player player, InteractionHand hand) {
			return Familiars.interact(this, player, hand);
		}

		@Override
		public void die(DamageSource source) {
			Familiars.died(this);
			super.die(source);
		}

		@Override
		public boolean removeWhenFarAway(double distance) {
			return false;
		}
	}
}
