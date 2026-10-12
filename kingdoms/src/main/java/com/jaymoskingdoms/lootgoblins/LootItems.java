package com.jaymoskingdoms.lootgoblins;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The minor goblins' treasures. Golden bones, iron eyes and stoneflesh smelt into gold, iron and lapis (recipes in the data
 * pack); a <b>quartzchop</b> is food (filling, but it stings: instant damage) and smelts into quartz; a <b>red bomb</b> is a
 * rocket that scatters redstone as it flies (and boosts an elytra); an <b>emerald crossbow</b> pays an emerald each shot and
 * breaks after one (or smelts into an emerald); a <b>prismarine pearl</b>, thrown, bursts into prismarine instead of
 * teleporting you.
 */
final class LootItems {
	/** A quartzchop: hunger and saturation, and the sting. */
	static final int CHOP_NUTRITION = 10;
	static final float CHOP_SATURATION = 0.2F;
	/** A red bomb drops redstone one tick in this many. */
	static final int BOMB_DUST_ONE_IN = 4;
	/** A prismarine pearl's burst: this many tries, each half the time a shard or a crystal; its cooldown (ticks). */
	static final int PEARL_TRIES = 4, PEARL_COOLDOWN = 20;

	private LootItems() {
	}

	static Item.Properties quartzchop() {
		return new Item.Properties().rarity(Rarity.UNCOMMON).food(
			new FoodProperties.Builder().nutrition(CHOP_NUTRITION).saturationModifier(CHOP_SATURATION).alwaysEdible().build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1))).build());
	}

	static final class RedBomb extends Item {
		RedBomb(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Player player = context.getPlayer();
			if (player != null && player.isFallFlying()) {
				return InteractionResult.PASS;
			}
			if (context.getLevel() instanceof ServerLevel level) {
				ItemStack stack = context.getItemInHand();
				Vec3 at = context.getClickLocation();
				Direction face = context.getClickedFace();
				Projectile.spawnProjectile(new Bomb(level, player, at.x + face.getStepX() * 0.15, at.y + face.getStepY() * 0.15,
					at.z + face.getStepZ() * 0.15), level, stack);
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!player.isFallFlying()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel serverLevel) {
				Projectile.spawnProjectile(new Bomb(level, player), serverLevel, stack);
				stack.consume(1, player);
				player.awardStat(Stats.ITEM_USED.get(this));
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** The red bomb in flight: a plain rocket to the client; the server scatters redstone behind it. */
	private static final class Bomb extends FireworkRocketEntity {
		Bomb(Level level, Entity owner, double x, double y, double z) {
			super(level, owner, x, y, z, new ItemStack(Items.FIREWORK_ROCKET));
		}

		Bomb(Level level, LivingEntity flier) {
			super(level, new ItemStack(Items.FIREWORK_ROCKET), flier);
		}

		@Override
		public void tick() {
			if (this.level() instanceof ServerLevel level && this.random.nextInt(BOMB_DUST_ONE_IN) == 0) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.REDSTONE)));
			}
			super.tick();
		}
	}

	static final class EmeraldCrossbow extends CrossbowItem {
		EmeraldCrossbow(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!level.isClientSide() && CrossbowItem.isCharged(player.getItemInHand(hand))) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD), Prediction.SERVER_ONLY);
			}
			return super.use(level, player, hand);
		}
	}

	static final class PrismarinePearl extends Item {
		PrismarinePearl(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.5F,
				0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
			player.getCooldowns().addCooldown(stack, PEARL_COOLDOWN);
			if (level instanceof ServerLevel serverLevel) {
				Projectile.spawnProjectileFromRotation(Pearl::new, serverLevel, stack, player, 0.0F, 1.5F, 1.0F);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}

	/** A thrown prismarine pearl: an ender pearl to look at, but it bursts into prismarine where it lands. */
	private static final class Pearl extends ThrownEnderpearl {
		Pearl(ServerLevel level, LivingEntity thrower, ItemStack stack) {
			super(level, thrower, stack);
		}

		@Override
		protected void onHit(HitResult hit) {
			if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
				level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 32, 0.3, 1.0, 0.3, 0.5);
				for (int i = 0; i < PEARL_TRIES; i++) {
					if (this.random.nextBoolean()) {
						Item item = this.random.nextBoolean() ? Items.PRISMARINE_SHARD : Items.PRISMARINE_CRYSTALS;
						level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(), this.getZ(), new ItemStack(item)));
					}
				}
				this.discard();
			}
		}
	}
}
