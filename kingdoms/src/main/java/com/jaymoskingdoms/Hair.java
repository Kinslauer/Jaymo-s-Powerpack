package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BedBlock;
import org.jspecify.annotations.Nullable;

/**
 * A lock of hair: what locate creature and scrying are tuned to. Snip it with shears, or comb it with a brush, from a player or
 * villager who cannot stop you (held fast, bound, downed or asleep), or take what a villager left in their own bed (a brush
 * drawn over it). The lock remembers whose it is. A person gives only so many: one a day.
 */
public final class Hair {
	static final String OWNER = "jaymoskingdoms_hair_of", NAME = "jaymoskingdoms_hair_name";
	/** How near a bed a villager's own must be to be theirs (blocks), and the days between two locks from one head. */
	static final double BED_SEARCH = 32.0;
	static final long DAY = 24000L;

	public static Item LOCK;
	private static final Map<UUID, Long> TAKEN = new HashMap<>();

	private Hair() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("lock_of_hair"));
		LOCK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.STRING, LOCK));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TAKEN.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p && entity instanceof LivingEntity target && comb(p.getItemInHand(hand))
				&& (target instanceof Villager || target instanceof ServerPlayer) && still(target)) {
				return snip(server, p, hand, target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer p && p.getItemInHand(hand).is(Items.BRUSH)
				&& server.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock) {
				return fromBed(server, p, hand, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean comb(ItemStack stack) {
		return stack.is(Items.SHEARS) || stack.is(Items.BRUSH);
	}

	/** Whether {@code who} cannot stop a hand: held, bound, downed, or asleep. */
	static boolean still(LivingEntity who) {
		return HeldFast.locked(who) || Captives.bound(who) || Downed.isDowned(who) || who.isSleeping() || who instanceof ServerPlayer p && p.isSleeping();
	}

	/** A lock of {@code owner}'s hair (the name shown is theirs). */
	public static ItemStack lockOf(UUID owner, String name) {
		ItemStack stack = new ItemStack(LOCK);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putString(OWNER, owner.toString());
			tag.putString(NAME, name);
		});
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.lock_of_hair.of", name).withStyle(s -> s.withItalic(false)));
		return stack;
	}

	/** Whose hair {@code stack} is, or null. */
	public static @Nullable UUID ownerOf(ItemStack stack) {
		if (!stack.is(LOCK)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		Optional<String> id = data == null ? Optional.empty() : data.copyTag().getString(OWNER);
		try {
			return id.map(UUID::fromString).orElse(null);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A lock of hair {@code p} carries (hand first), or empty. */
	public static ItemStack carried(ServerPlayer p) {
		for (ItemStack s : java.util.List.of(p.getMainHandItem(), p.getOffhandItem())) {
			if (ownerOf(s) != null) {
				return s;
			}
		}
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (ownerOf(p.getInventory().getItem(i)) != null) {
				return p.getInventory().getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean snip(ServerLevel level, ServerPlayer taker, InteractionHand hand, LivingEntity target) {
		long now = level.getGameTime();
		if (TAKEN.getOrDefault(target.getUUID(), -DAY) + DAY > now) {
			return false;
		}
		TAKEN.put(target.getUUID(), now);
		give(level, taker, lockOf(target.getUUID(), target.getName().getString()), target.getEyePosition());
		taker.getItemInHand(hand).hurtAndBreak(1, taker, hand);
		if (target instanceof Villager v) {
			Recollections.remember(v, taker, Recollections.Kind.STRUCK, Component.empty());
		}
		return true;
	}

	private static boolean fromBed(ServerLevel level, ServerPlayer taker, InteractionHand hand, BlockPos bed) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bed).inflate(BED_SEARCH), Villager::isAlive)) {
			var home = v.getBrain().getMemory(MemoryModuleType.HOME);
			if (home.isPresent() && home.get().dimension() == level.dimension() && home.get().pos().distManhattan(bed) <= 1) {
				long now = level.getGameTime();
				if (TAKEN.getOrDefault(v.getUUID(), -DAY) + DAY > now) {
					return false;
				}
				TAKEN.put(v.getUUID(), now);
				give(level, taker, lockOf(v.getUUID(), v.getName().getString()), Vec3Util.centre(bed));
				taker.getItemInHand(hand).hurtAndBreak(1, taker, hand);
				return true;
			}
		}
		return false;
	}

	private static void give(ServerLevel level, ServerPlayer taker, ItemStack lock, net.minecraft.world.phys.Vec3 at) {
		if (!taker.getInventory().add(lock)) {
			taker.drop(lock, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.5F, 1.6F);
		level.sendParticles(Fx.mote(0xC9A06A, 0.5F), at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.01);
	}

	/** Small helper for a block's centre. */
	private static final class Vec3Util {
		static net.minecraft.world.phys.Vec3 centre(BlockPos pos) {
			return net.minecraft.world.phys.Vec3.atCenterOf(pos);
		}
	}

	/** Used by scrying and locate: the entity {@code owner} names, loaded anywhere {@code level}'s server knows. */
	static @Nullable Entity find(ServerLevel level, UUID owner) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
		if (p != null) {
			return p;
		}
		for (ServerLevel l : level.getServer().getAllLevels()) {
			Entity e = l.getEntity(owner);
			if (e != null) {
				return e;
			}
		}
		return null;
	}
}
