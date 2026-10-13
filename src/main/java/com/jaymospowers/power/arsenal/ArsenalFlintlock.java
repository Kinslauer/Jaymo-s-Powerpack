package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Armory's flintlocks (ascended and corrupted): a pair of single-shot pistols out of the rift. Reach sometimes
 * brings the pair ({@link #PAIR_CHANCE}; always the first time). Pinned (Sneak + Reach holding one), they're the only
 * thing the rift gives you, a pair every Reach. Fire one (right click), drop it spent, and a fresh loaded one is
 * already in your hand: no Reach, no wait.
 */
public final class ArsenalFlintlock {
	/** The shot: reach, damage (the void adds its share), how far it throws. */
	static final double RANGE = 48.0;
	static final float DAMAGE = 9.0F;
	static final double KNOCKBACK = 0.5;
	/** Chance a Reach in the Void Armory brings the pair (unpinned). */
	static final float PAIR_CHANCE = 0.35F;

	public static Item FLINTLOCK;
	/** {@code jaymospowers:spent}: this flintlock has been fired. */
	public static DataComponentType<Boolean> SPENT;
	/** The Void Armory has found its flintlocks (the first Reach there brings them). Saved. */
	static final AttachmentType<Boolean> FOUND = AttachmentRegistry.create(JaymosPowers.id("arsenal_flintlocks_found"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private ArsenalFlintlock() {
	}

	static void register() {
		SPENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("spent"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("flintlock"));
		FLINTLOCK = Registry.register(BuiltInRegistries.ITEM, key, new Flintlock(new Item.Properties().setId(key).stacksTo(1)
			.rarity(Rarity.EPIC)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.flintlock.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))))));
	}

	/** In the Void Armory: ascended and corrupted. */
	static boolean armory(Player player) {
		return Fate.isAscended(player) && Fate.isCorrupted(player);
	}

	/**
	 * What a Reach in the Void Armory brings, if it's the flintlocks: the pair when they're pinned, the first time,
	 * or by chance. Null for the usual pull.
	 */
	static List<ItemStack> pairFor(ServerPlayer player, RandomSource random) {
		if (!armory(player)) {
			return null;
		}
		boolean pinned = ArsenalArmory.pinned(player) == FLINTLOCK;
		boolean first = player.getAttached(FOUND) == null;
		if (!pinned && !first && (ArsenalArmory.pinned(player) != null || random.nextFloat() >= PAIR_CHANCE)) {
			return null;
		}
		player.setAttached(FOUND, true);
		List<ItemStack> pair = new ArrayList<>();
		pair.add(new ItemStack(FLINTLOCK));
		pair.add(new ItemStack(FLINTLOCK));
		return pair;
	}

	/**
	 * A summoned flintlock hit the ground: spent, it's swapped for a loaded one straight into its owner's hand (it
	 * dissolves either way). Returns true if it did.
	 */
	public static boolean reloadOnDrop(ServerLevel level, ItemStack stack, Summoned summoned, Vec3 at) {
		if (!stack.is(FLINTLOCK) || !Boolean.TRUE.equals(stack.get(SPENT))) {
			return false;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(summoned.owner());
		if (owner == null || !armory(owner) || !owner.isAlive()) {
			return false;
		}
		ItemStack fresh = new ItemStack(FLINTLOCK);
		fresh.set(Summoned.TYPE, summoned);
		if (owner.getMainHandItem().isEmpty()) {
			owner.setItemInHand(InteractionHand.MAIN_HAND, fresh);
		} else if (owner.getOffhandItem().isEmpty()) {
			owner.setItemInHand(InteractionHand.OFF_HAND, fresh);
		} else if (!owner.getInventory().add(fresh)) {
			return false;
		}
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.2, at.z, 6, 0.1, 0.05, 0.1, 0.01);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.8F);
		return true;
	}

	/**
	 * The player is dropping (Q) what's in their main hand: if it's a summoned flintlock and the off hand holds the
	 * other of the pair, that goes down with it, so the pair is dropped together (a spent one comes back loaded, see
	 * {@link #reloadOnDrop}).
	 */
	public static void dropPartner(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		if (!main.is(FLINTLOCK) || !off.is(FLINTLOCK) || !Summoned.is(main) || !Summoned.is(off)) {
			return;
		}
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.drop(off, true, net.minecraft.util.Prediction.PREDICTED);
	}

	/** A single-shot pistol. */
	public static final class Flintlock extends Item {
		Flintlock(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (Boolean.TRUE.equals(stack.get(SPENT))) {
				// spent: drop it for another
				level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
				return InteractionResult.FAIL;
			}
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			stack.set(SPENT, true);
			fire(server, player);
			player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			return InteractionResult.SUCCESS;
		}

		private static void fire(ServerLevel level, Player player) {
			Vec3 eye = player.getEyePosition();
			Vec3 dir = player.getLookAngle();
			HitResult block = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			double reach = block.getType() == HitResult.Type.MISS ? RANGE : block.getLocation().distanceTo(eye);
			LivingEntity hit = null;
			double best = reach;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(dir.scale(reach))).inflate(1.0),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
				var clip = e.getBoundingBox().inflate(0.2).clip(eye, eye.add(dir.scale(reach)));
				if (clip.isPresent() && clip.get().distanceTo(eye) < best) {
					best = clip.get().distanceTo(eye);
					hit = e;
				}
			}
			float damage = DAMAGE * (Fate.isCorrupted(player) ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F);
			if (hit != null) {
				hit.hurtServer(level, level.damageSources().playerAttack(player), damage);
				hit.push(dir.x * KNOCKBACK, 0.15, dir.z * KNOCKBACK);
				hit.syncVelocity = true;
				level.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY() + hit.getBbHeight() * 0.6, hit.getZ(), 12, 0.2, 0.2, 0.2, 0.3);
			} else if (block.getType() != HitResult.Type.MISS) {
				Vec3 at = block.getLocation();
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.02);
			}
			// the trail and the muzzle's flash and smoke
			for (double d = 1.0; d < best; d += 1.5) {
				Vec3 at = eye.add(dir.scale(d));
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			Vec3 muzzle = eye.add(dir.scale(0.9)).add(0.0, -0.2, 0.0);
			level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE0A0), muzzle.x, muzzle.y, muzzle.z,
				1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.03);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.05);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.9F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
	}
}
