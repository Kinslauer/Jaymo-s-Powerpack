package com.jaymoskitchen.cooking;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Poisoned sweets (Hunter's Road, patch 7, "T-B6"; Toxony's idea, written clean-room): a <b>toxic cake</b> you can set down
 * (each slice eaten poisons the eater) or <i>throw</i> (a crafted, spent cake: it splatters and poisons all within
 * {@link #SPLASH} blocks of where it lands), and three poisoned chocolates, <b>milk</b>, <b>mint</b> and <b>dark</b>, each with
 * its own sickness. A <b>gift box</b> holds {@link #BOX_COUNT} of one kind; opened, it gives them back. Everything is made with
 * a poison ({@code #jaymoskitchen:sweet_poisons}: a spider eye, a venom gland, Jaymo's Powers' pastes), so nothing is free.
 */
public final class PoisonedSweets {
	/** What a bite of the cake does, and how long (ticks): poison, nausea, hunger. */
	private static final int CAKE_POISON = 20 * 8, CAKE_NAUSEA = 20 * 10, CAKE_HUNGER = 20 * 12;
	/** How far a thrown cake's splatter reaches (blocks), its speed and spread (as a snowball's), the throw sound's volume. */
	static final double SPLASH = 2.5;
	private static final float THROW_SPEED = 1.2F, THROW_SPREAD = 1.0F, THROW_VOLUME = 0.5F;
	/** Chocolates in a gift box. */
	public static final int BOX_COUNT = 3;
	/** Chocolate's nutrition and saturation. */
	private static final int NUTRITION = 3;
	private static final float SATURATION = 0.4F;
	/** The entity event for a splat, and how many bits of cake it throws up. */
	private static final byte SPLAT_EVENT = 3;
	private static final int SPLAT_PARTICLES = 14;

	public static Block TOXIC_CAKE;
	public static Item TOXIC_CAKE_ITEM, MILK_CHOCOLATE, MINT_CHOCOLATE, DARK_CHOCOLATE, GIFT_BOX;
	public static EntityType<ToxicCakeEntity> CAKE_ENTITY;
	/** On a gift box: the id of the chocolate inside. */
	public static DataComponentType<String> GIFT;

	private PoisonedSweets() {
	}

	/** The effects a poisoned sweet gives, by item id (a chocolate's sickness, the cake's slice). */
	private static List<MobEffectInstance> effects(String id) {
		return switch (id) {
			case "milk_chocolate" -> List.of(new MobEffectInstance(MobEffects.POISON, 20 * 6), new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20));
			case "mint_chocolate" -> List.of(new MobEffectInstance(MobEffects.NAUSEA, 20 * 12), new MobEffectInstance(MobEffects.SLOWNESS, 20 * 15));
			case "dark_chocolate" -> List.of(new MobEffectInstance(MobEffects.WITHER, 20 * 5), new MobEffectInstance(MobEffects.HUNGER, 20 * 10));
			default -> List.of(new MobEffectInstance(MobEffects.POISON, CAKE_POISON), new MobEffectInstance(MobEffects.NAUSEA, CAKE_NAUSEA),
				new MobEffectInstance(MobEffects.HUNGER, CAKE_HUNGER));
		};
	}

	/** Gives {@code who} the sickness of the sweet {@code id}. */
	static void dose(LivingEntity who, String id) {
		for (MobEffectInstance e : effects(id)) {
			who.addEffect(new MobEffectInstance(e));
		}
	}

	public static void register() {
		GIFT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("gift"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		ResourceKey<Block> cakeKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("toxic_cake"));
		TOXIC_CAKE = Registry.register(BuiltInRegistries.BLOCK, cakeKey, new ToxicCakeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
			.forceSolidOn().strength(0.5F).sound(SoundType.WOOL).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED).setId(cakeKey)));
		ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("toxic_cake"));
		CAKE_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey, EntityType.Builder.<ToxicCakeEntity>of(ToxicCakeEntity::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(entityKey));
		TOXIC_CAKE_ITEM = item("toxic_cake", p -> new ToxicCakeItem(TOXIC_CAKE, p.useItemDescriptionPrefix().stacksTo(1)));
		((BlockItem) TOXIC_CAKE_ITEM).registerBlocks(Item.BY_BLOCK, TOXIC_CAKE_ITEM);
		MILK_CHOCOLATE = chocolate("milk_chocolate");
		MINT_CHOCOLATE = chocolate("mint_chocolate");
		DARK_CHOCOLATE = chocolate("dark_chocolate");
		GIFT_BOX = item("gift_box", p -> new GiftBoxItem(p.stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item i : List.of(TOXIC_CAKE_ITEM, MILK_CHOCOLATE, MINT_CHOCOLATE, DARK_CHOCOLATE)) {
				output.accept(new ItemStack(i));
			}
			for (Item chocolate : List.of(MILK_CHOCOLATE, MINT_CHOCOLATE, DARK_CHOCOLATE)) {
				ItemStack box = new ItemStack(GIFT_BOX);
				box.set(GIFT, BuiltInRegistries.ITEM.getKey(chocolate).toString());
				output.accept(box);
			}
		});
	}

	private static Item chocolate(String id) {
		return item(id, p -> new Item(p.food(new FoodProperties.Builder().nutrition(NUTRITION).saturationModifier(SATURATION).build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(effects(id))).build())));
	}

	private static Item item(String id, java.util.function.Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** The toxic cake as a block: each slice eaten poisons the one who eats it. */
	public static class ToxicCakeBlock extends CakeBlock {
		public ToxicCakeBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
			if (result != InteractionResult.PASS && !level.isClientSide()) {
				dose(player, "toxic_cake");
			}
			return result;
		}
	}

	/** The toxic cake in the hand: set down on a block, or thrown at the air ({@link ToxicCakeEntity}); a thrown one is spent. */
	public static class ToxicCakeItem extends BlockItem implements ProjectileItem {
		public ToxicCakeItem(Block block, Properties properties) {
			super(block, properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, THROW_VOLUME,
				0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
			if (!level.isClientSide()) {
				ToxicCakeEntity cake = new ToxicCakeEntity(level, player);
				cake.setItem(held);
				cake.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_SPEED, THROW_SPREAD);
				level.addFreshEntity(cake);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
		}

		@Override
		public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
			ToxicCakeEntity cake = new ToxicCakeEntity(level, position.x(), position.y(), position.z());
			cake.setItem(stack);
			return cake;
		}
	}

	/** A toxic cake thrown: it splatters where it lands and poisons everything alive within {@link #SPLASH} blocks. */
	public static class ToxicCakeEntity extends ThrowableItemProjectile {
		public ToxicCakeEntity(EntityType<? extends ToxicCakeEntity> type, Level level) {
			super(type, level);
		}

		public ToxicCakeEntity(Level level, LivingEntity thrower) {
			super(CAKE_ENTITY, thrower, level, TOXIC_CAKE_ITEM.getDefaultInstance());
		}

		public ToxicCakeEntity(Level level, double x, double y, double z) {
			super(CAKE_ENTITY, x, y, z, level, TOXIC_CAKE_ITEM.getDefaultInstance());
		}

		@Override
		protected Item getDefaultItem() {
			return TOXIC_CAKE_ITEM;
		}

		@Override
		public void handleEntityEvent(byte id) {
			if (id == SPLAT_EVENT) {
				ParticleOptions particle = new ItemParticleOption(ParticleTypes.ITEM, getDefaultItem());
				for (int i = 0; i < SPLAT_PARTICLES; ++i) {
					level().addParticle(particle, getX(), getY(), getZ(), (random.nextFloat() * 2.0D - 1.0D) * 0.15F,
						(random.nextFloat() * 2.0D - 1.0D) * 0.15F + 0.1F, (random.nextFloat() * 2.0D - 1.0D) * 0.15F);
				}
			}
		}

		@Override
		protected void onHitEntity(EntityHitResult result) {
			super.onHitEntity(result);
			result.getEntity().hurt(damageSources().thrown(this, getOwner()), 0);
		}

		@Override
		protected void onHit(HitResult result) {
			super.onHit(result);
			if (level() instanceof ServerLevel server && !isRemoved()) {
				for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SPLASH), LivingEntity::isAlive)) {
					if (near.distanceToSqr(this) <= SPLASH * SPLASH) {
						dose(near, "toxic_cake");
					}
				}
				server.broadcastEntityEvent(this, SPLAT_EVENT);
				playSound(SoundEvents.SLIME_BLOCK_HIT, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
				discard();
			}
		}
	}

	/** A gift box: opened (used), it gives back the {@link #BOX_COUNT} chocolates of the kind it holds. */
	public static class GiftBoxItem extends Item {
		public GiftBoxItem(Properties properties) {
			super(properties);
		}

		private static Item inside(ItemStack box) {
			String id = box.get(GIFT);
			Identifier key = id == null ? null : Identifier.tryParse(id);
			return key == null ? MILK_CHOCOLATE : BuiltInRegistries.ITEM.getOptional(key).orElse(MILK_CHOCOLATE);
		}

		@Override
		public Component getName(ItemStack stack) {
			return Component.translatable("item.jaymoskitchen.gift_box.of", new ItemStack(inside(stack)).getHoverName());
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			if (!level.isClientSide()) {
				Item sweet = inside(held);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.8F, 1.0F);
				held.consume(1, player);
				player.getInventory().placeItemBackInInventory(new ItemStack(sweet, BOX_COUNT), net.minecraft.util.Prediction.SERVER_ONLY);
			}
			return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskitchen.gift_box").withStyle(ChatFormatting.GRAY));
			super.appendHoverText(stack, context, display, builder, flag);
		}
	}
}
