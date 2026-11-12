package com.jaymospowers.gear.belt;

import com.jaymospowers.armory.ThrowingWeaponItem;
import com.jaymospowers.armory.ThrownWeapon;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.gear.GearPicks;
import com.jaymospowers.gear.GroundFx;
import com.jaymospowers.gear.ThrownGear;
import com.jaymospowers.gear.arrow.HeadFx;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The belt's throwables. Each is a plain item you can also throw from the hand (use), lobbed as a {@link ThrownGear} that does
 * its work where it lands ({@link #land}); the belt ({@link ThrowerBeltItem}) holds them and throws the picked one. Existing
 * throwing weapons ({@link ThrowingWeaponItem}: daggers, kunai, stars, hatchets) are thrown from the belt too, and bleed.
 */
public final class Throwables {
	// ---- tuning ----
	/** How fast a lob leaves the hand (blocks/tick), and the wait before the next throw (ticks). */
	public static final float LOB_SPEED = 1.3F;
	public static final int THROW_COOLDOWN = 8;
	/** A knife thrown from the belt flies at this speed. */
	public static final float KNIFE_SPEED = 2.0F;
	/** Sneezing powder: the cloud's reach (blocks). */
	public static final double POWDER_RADIUS = 3.0;
	/** Acid: reach (blocks), the share of an armour piece's durability it eats, the harm it does, and how long its pool shows (ticks). */
	public static final double ACID_RADIUS = 2.5;
	public static final int ACID_WEAR_PERCENT = 22, ACID_POOL_TICKS = 60;
	public static final float ACID_HARM = 2.0F;
	/** Bleeding dart: the harm it does. */
	public static final float DART_HARM = 1.0F;
	/** Caltrops scattered by one throw. */
	public static final int CALTROPS_SCATTERED = 6;
	/** Entity tag of a knife thrown from the belt (it opens a cut). */
	public static final String KNIFE_TAG = "jaymospowers.belt_knife";

	public static Item SMOKE_POT, FLASH_POWDER, SNEEZING_POWDER, BLEEDING_DART, ACID_VIAL, CHOKING_VIAL, CALTROPS, BEAR_TRAP;

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Throwables() {
	}

	/** An item that throws from the hand and bursts where it lands. */
	public static final class ThrowableItem extends Item {
		private final String id;

		ThrowableItem(Item.Properties properties, String id) {
			super(properties);
			this.id = id;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel server && player instanceof ServerPlayer thrower) {
				lob(server, thrower, stack.consumeAndReturn(1, player));
			}
			player.getCooldowns().addCooldown(stack, THROW_COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.throwable." + this.id).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.throwable.belt").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public static void register() {
		SMOKE_POT = item("smoke_pot", 16);
		FLASH_POWDER = item("flash_powder", 16);
		SNEEZING_POWDER = item("sneezing_powder", 16);
		BLEEDING_DART = item("bleeding_dart", 16);
		ACID_VIAL = item("acid_vial", 8);
		CHOKING_VIAL = item("choking_vial", 8);
		CALTROPS = item("caltrops", 16);
		BEAR_TRAP = item("bear_trap", 4);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && !blocked && source.getDirectEntity() != null && source.getDirectEntity().entityTags().contains(KNIFE_TAG)) {
				Bleeding.open(victim, BodyPart.BODY, false);
			}
		});
	}

	private static Item item(String id, int stack) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, com.jaymospowers.JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new ThrowableItem(new Item.Properties().setId(key).stacksTo(stack), id));
	}

	/** Whether the belt may hold this: one of ours, or a throwing weapon. */
	public static boolean isThrowable(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof ThrowableItem || stack.getItem() instanceof ThrowingWeaponItem);
	}

	/** Throws one {@code thrown} from {@code player}: a knife-like weapon flies as itself, anything of ours is lobbed. */
	public static void lob(ServerLevel level, ServerPlayer player, ItemStack thrown) {
		if (thrown.getItem() instanceof ThrowingWeaponItem) {
			ThrownWeapon weapon = Projectile.spawnProjectileFromRotation((l, owner, stack) -> new ThrownWeapon(l, owner, stack), level, thrown, player,
				0.0F, KNIFE_SPEED, 1.0F);
			weapon.addTag(KNIFE_TAG);
			if (player.hasInfiniteMaterials()) {
				weapon.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			}
			level.playSound(null, weapon, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
			return;
		}
		ThrownGear gear = new ThrownGear(level, player, thrown);
		Vec3 eye = player.getEyePosition();
		gear.setPos(eye.x, eye.y - 0.2, eye.z);
		Vec3 look = player.getLookAngle();
		gear.shoot(look.x, look.y + 0.1, look.z, LOB_SPEED, 1.0F);
		level.addFreshEntity(gear);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.9F);
	}

	/**
	 * A lobbed item reached {@code hit}: it does its work. False when it should carry on (a dart that struck a wall is simply lost,
	 * which is also a yes; false is only for an item with nothing to do).
	 */
	public static boolean land(ServerLevel level, ItemStack item, @Nullable Entity owner, HitResult hit) {
		Vec3 at = hit.getLocation();
		Item kind = item.getItem();
		if (kind == SMOKE_POT) {
			GroundFx.cloud(level, at, owner, GroundFx.Kind.STING, GroundFx.POT_RADIUS, GroundFx.POT_TICKS);
		} else if (kind == FLASH_POWDER) {
			HeadFx.flash(level, at, owner);
		} else if (kind == SNEEZING_POWDER) {
			powder(level, at, owner);
		} else if (kind == BLEEDING_DART) {
			if (hit instanceof EntityHitResult struck && struck.getEntity() instanceof LivingEntity victim) {
				victim.hurtServer(level, level.damageSources().generic(), DART_HARM);
				Bleeding.open(victim, BodyPart.BODY, false);
			}
		} else if (kind == ACID_VIAL) {
			acid(level, at);
		} else if (kind == CHOKING_VIAL) {
			GroundFx.cloud(level, at, owner, GroundFx.Kind.CHOKE, GroundFx.VIAL_RADIUS, GroundFx.VIAL_TICKS);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
		} else if (kind == CALTROPS) {
			GroundFx.caltrops(level, at, owner, CALTROPS_SCATTERED);
		} else if (kind == BEAR_TRAP) {
			GroundFx.bearTrap(level, at, owner);
		} else {
			return false;
		}
		return true;
	}

	private static void powder(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.blast(level, at, 1.6F, 0xE8E0C0);
		level.sendParticles(MagicFx.miasma(0xE8E0C0, 2.2F), at.x, at.y + 0.5, at.z, 10, POWDER_RADIUS * 0.4, 0.4, POWDER_RADIUS * 0.4, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.9F, 1.4F);
		AABB box = new AABB(at, at).inflate(POWDER_RADIUS);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			if (victim.position().distanceTo(at) <= POWDER_RADIUS + 0.5) {
				GroundFx.sneeze(level, victim);
			}
		}
	}

	private static void acid(ServerLevel level, Vec3 at) {
		MagicFx.pool(level, at, (float) ACID_RADIUS, 0x8AD040, ACID_POOL_TICKS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
		AABB box = new AABB(at, at).inflate(ACID_RADIUS);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
			if (victim.position().distanceTo(at) > ACID_RADIUS + 0.4) {
				continue;
			}
			for (EquipmentSlot slot : ARMOUR) {
				ItemStack worn = victim.getItemBySlot(slot);
				if (worn.isDamageableItem()) {
					int wear = Math.max(1, worn.getMaxDamage() * ACID_WEAR_PERCENT / 100);
					// it eats the plate, never the last of it
					worn.setDamageValue(Math.min(worn.getMaxDamage() - 1, worn.getDamageValue() + wear));
				}
			}
			victim.hurtServer(level, level.damageSources().magic(), ACID_HARM);
		}
	}

	/** A thrown shield that lost its flight: back to its thrower, or onto the ground. */
	public static void giveBack(ThrownGear gear) {
		if (gear.level() instanceof ServerLevel level) {
			ItemStack stack = gear.getItem().copy();
			if (gear.getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.getInventory().add(stack)) {
				level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
			} else if (!stack.isEmpty()) {
				gear.spawnAtLocation(level, stack);
			}
		}
		gear.discard();
	}
}
