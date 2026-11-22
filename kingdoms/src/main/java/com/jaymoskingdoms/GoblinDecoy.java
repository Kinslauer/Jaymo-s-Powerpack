package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The goblins' <b>decoy</b> (survival pick "goblin extras", Andy, 2026-11-22; Lore.md, "Goblin odd-jobs"): a stuffed sack in
 * a goblin's rags. Thrown (use), it lands as a {@link Dummy} (a small scarecrow, hay for a head), and for {@link #LIFE}
 * ticks every goblin and beast near it ({@link #RANGE}) goes for it instead of whoever they were after: monsters, wild
 * wolves and polar bears take it for their target; goblins run to it and tear at it. It takes {@link #TOUGH} damage
 * before it is torn apart. Sold by the wandering goblin ({@link Pedlars}); crafted from a hay bale, leather and string.
 */
public final class GoblinDecoy {
	public static Item ITEM;
	public static EntityType<Sack> SACK;
	public static EntityType<Dummy> DUMMY;

	/** How long a landed decoy holds (ticks), and how much it takes before it is torn apart. */
	static final int LIFE = 600;
	static final float TOUGH = 40.0F;
	/** How far off the things it draws are (blocks), how often they are looked over (ticks). */
	static final double RANGE = 16.0;
	static final int LOOK_EVERY = 10;
	/** How near a goblin must be to tear at it (blocks), and how hard (a blow). */
	static final double TEAR_REACH = 2.0;
	static final float GOBLIN_BLOW = 2.0F;
	/** How fast a goblin runs for it, the throw's speed, spread and wait (ticks). */
	static final float RUN_SPEED = 0.8F, POWER = 1.0F, SPREAD = 1.0F;
	static final int THROW_COOLDOWN = 20;
	/** Its rags and hide (as the goblins', {@link Goblins}). */
	static final int RAG = 0x6B5A3A, HIDE = 0x4E3B26, MOSS = 0x55602E;

	private GoblinDecoy() {
	}

	static void register() {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("goblin_decoy"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new DecoyItem(new Item.Properties().setId(itemKey).stacksTo(8)));
		ResourceKey<EntityType<?>> sackKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("goblin_decoy_sack"));
		SACK = Registry.register(BuiltInRegistries.ENTITY_TYPE, sackKey, EntityType.Builder.<Sack>of(Sack::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(sackKey));
		ResourceKey<EntityType<?>> dummyKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("goblin_decoy_dummy"));
		DUMMY = Registry.register(BuiltInRegistries.ENTITY_TYPE, dummyKey, EntityType.Builder.<Dummy>of(Dummy::new, MobCategory.MISC)
			.noLootTable().noSave().sized(0.5F, 1.975F).clientTrackingRange(10).build(dummyKey));
		FabricDefaultAttributeRegistry.register(DUMMY, ArmorStand.createAttributes().add(Attributes.SCALE, 0.7).add(Attributes.KNOCKBACK_RESISTANCE, 1.0));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(ITEM));
	}

	/** Whether {@code m} goes for a decoy: a monster, a wild wolf or polar bear, a goblin (not the wandering one). */
	static boolean drawn(Mob m) {
		if (!m.isAlive() || m.isNoAi() || Pedlars.party(m)) {
			return false;
		}
		return m instanceof Enemy || m instanceof Wolf wolf && !wolf.isTame() || m instanceof PolarBear || Goblins.isGoblin(m);
	}

	/** The decoy, in the hand: use it to throw it. */
	static final class DecoyItem extends Item {
		DecoyItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 1.3F);
			if (level instanceof ServerLevel server) {
				Projectile.spawnProjectileFromRotation(Sack::new, server, held, player, -10.0F, POWER, SPREAD);
			}
			player.getCooldowns().addCooldown(held, THROW_COOLDOWN);
			held.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}

	/** The sack in the air: where it lands, a {@link Dummy} stands. */
	public static final class Sack extends ThrowableItemProjectile {
		public Sack(EntityType<? extends Sack> type, Level level) {
			super(type, level);
		}

		public Sack(ServerLevel level, LivingEntity owner, ItemStack stack) {
			super(SACK, owner, level, stack);
		}

		@Override
		protected Item getDefaultItem() {
			return ITEM;
		}

		@Override
		protected void onHit(HitResult hit) {
			super.onHit(hit);
			if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
				Vec3 at = hit.getLocation();
				if (hit instanceof BlockHitResult block) {
					// (set down on the face it hit, not inside it)
					at = at.add(block.getDirection().getStepX() * 0.4, block.getDirection().getStepY() < 0 ? -1.0 : 0.0, block.getDirection().getStepZ() * 0.4);
				}
				Dummy dummy = DUMMY.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
				if (dummy != null) {
					dummy.snapTo(at.x, at.y, at.z, this.getYRot(), 0.0F);
					dummy.dress();
					level.addFreshEntity(dummy);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
				}
				this.discard();
			}
		}
	}

	/** The landed decoy: a scarecrow in goblin rags (an armour stand that takes any blow), torn apart when it has taken enough or its time is up. */
	public static final class Dummy extends ArmorStand {
		private float torn;

		public Dummy(EntityType<? extends Dummy> type, Level level) {
			super(type, level);
		}

		/** Hay for a head, a goblin's rags and hide for the rest. */
		void dress() {
			this.setNoBasePlate(true);
			this.setShowArms(true);
			this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.HAY_BLOCK));
			this.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, RAG));
			this.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, HIDE));
			this.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, MOSS));
		}

		private static ItemStack dyed(Item item, int rgb) {
			ItemStack stack = new ItemStack(item);
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			return stack;
		}

		/** Nobody dresses it down. */
		@Override
		public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
			return InteractionResult.PASS;
		}

		/** Any blow tears at it (an armour stand takes only a few kinds). */
		@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
			if (this.isRemoved()) {
				return false;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				this.tearApart(level);
				return true;
			}
			this.torn += damage;
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), this.getX(), this.getY(0.6), this.getZ(), 6,
				0.2, 0.3, 0.2, 0.05);
			this.playSound(SoundEvents.WOOL_HIT, 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
			if (this.torn >= TOUGH) {
				this.tearApart(level);
			}
			return true;
		}

		private void tearApart(ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), this.getX(), this.getY(0.5), this.getZ(), 30,
				0.3, 0.5, 0.3, 0.1);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F);
			this.discard();
		}

		@Override
		public void tick() {
			super.tick();
			if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
				return;
			}
			if (this.tickCount >= LIFE) {
				this.tearApart(level);
			} else if (this.tickCount % LOOK_EVERY == 0) {
				this.lure(level);
			}
		}

		/** Every goblin and beast near goes for it: monsters and beasts take it for their target, goblins run to it and tear at it. */
		private void lure(ServerLevel level) {
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(this.blockPosition()).inflate(RANGE), GoblinDecoy::drawn)) {
				if (m instanceof Villager v) {
					// (a goblin villager's brain: a walk target, as CoinToss's coins)
					v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.blockPosition(), RUN_SPEED, 0));
					if (v.distanceToSqr(this) <= TEAR_REACH * TEAR_REACH) {
						v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						this.hurtServer(level, level.damageSources().mobAttack(v), GOBLIN_BLOW);
					}
				} else if (m.getTarget() != this) {
					m.setTarget(this);
				}
			}
		}
	}
}
