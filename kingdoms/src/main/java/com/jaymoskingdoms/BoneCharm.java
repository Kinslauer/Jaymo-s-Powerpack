package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The bone charm (Reference Mods patch 7, after Mo' Creatures' amulets): a charm of carved bone that a beast-tamer makes. A
 * beast of yours, one that follows you and answers to you, can be put to rest in it, and comes out where you call it.
 * <ul>
 * <li><b>Who makes it.</b> The Green Huntsman's rangers ({@link ForestRangers}): the only beast-tamers there are. Bring one a
 * bone and {@link #PRICE} emeralds and ask: it is carved while you wait. No villager profession of the game or of MCA is a
 * beast-tamer, so the rangers (who already teach their calling and judge a poacher) are it.</li>
 * <li><b>Putting a beast to rest.</b> Use the empty charm on a beast that is yours: a tame cat or dog, a tamed horse or
 * other mount, a cub you raised ({@link BigCats}), a liger. It is asleep within the bone, whole as it was (hurt, named,
 * saddled), and keeps nothing of the world about it: it cannot be harmed there, and does not grow or hunger.</li>
 * <li><b>Calling it.</b> Use the full charm: the beast steps out where you are looking (within {@link #REACH} blocks),
 * and the charm is empty again.</li>
 * </ul>
 * Where the charm is not enough: a beast that is not yours (a wild one, another's) will not go into it. Sympathy: a bonded
 * beast comes when its master calls; stewardship: the care that bonded it (food, a bed, a hand) is what the charm keeps.
 */
public final class BoneCharm {
	/** Emeralds the beast-tamer asks for the carving. */
	public static final int PRICE = 3;
	/** How far the call reaches (blocks); how long the full charm rests before it can be used again (ticks). */
	static final double REACH = 24.0;
	static final int REST = 40;

	public static Item EMPTY, FULL;

	private BoneCharm() {
	}

	public static void register() {
		EMPTY = item("bone_charm", new Item.Properties().stacksTo(1));
		FULL = item("bone_charm_bound", new Item.Properties().stacksTo(1));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(EMPTY));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).is(EMPTY)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof Mob beast) || !yours(player, beast)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				rest(server, player, hand, beast);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, name.endsWith("bound") ? new Bound(properties.setId(key)) : new Item(properties.setId(key)));
	}

	/** A bone and the price in emeralds, from {@code player}'s pack: given if it can be (the ranger's carving). */
	static boolean carve(Player player) {
		if (!player.getMainHandItem().is(Items.BONE) || count(player, Items.EMERALD) < PRICE) {
			return false;
		}
		player.getMainHandItem().shrink(1);
		int owed = PRICE;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (owed > 0 && s.is(Items.EMERALD)) {
				int take = Math.min(owed, s.getCount());
				s.shrink(take);
				owed -= take;
			}
		}
		ItemStack charm = new ItemStack(EMPTY);
		if (!player.getInventory().add(charm)) {
			player.drop(charm, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return true;
	}

	/** Whether {@code player} could be asked to carve a charm: a bone in hand and the price in the pack. */
	static boolean canCarve(Player player) {
		return player.getMainHandItem().is(Items.BONE) && count(player, Items.EMERALD) >= PRICE;
	}

	private static int count(Player player, Item item) {
		int n = 0;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Whether {@code beast} is {@code player}'s: tamed to them, or raised by them. */
	private static boolean yours(Player player, Mob beast) {
		if (beast instanceof OwnableEntity pet && pet.getOwnerReference() != null && pet.getOwnerReference().getUUID().equals(player.getUUID())) {
			return true;
		}
		return player.getUUID().equals(BigCats.keeperOf(beast));
	}

	/** The beast goes into the charm: its whole self saved in it, the beast gone from the world. */
	private static void rest(ServerLevel level, Player player, InteractionHand hand, Mob beast) {
		if (beast.isPassenger() || beast.isVehicle()) {
			return;
		}
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!beast.saveAsPassenger(out)) {
			return;
		}
		CompoundTag body = out.buildResult();
		ItemStack full = new ItemStack(FULL);
		CompoundTag tag = new CompoundTag();
		tag.put("beast", body);
		full.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		full.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.bone_charm_bound.named", beast.getDisplayName()));
		level.sendParticles(ParticleTypes.POOF, beast.getX(), beast.getY() + 0.5, beast.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, beast.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 0.8F);
		beast.discard();
		player.setItemInHand(hand, full);
	}

	/** The charm with a beast in it: use it and the beast comes out where you look. */
	private static final class Bound extends Item {
		Bound(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			CompoundTag body = data == null ? null : data.copyTag().getCompound("beast").orElse(null);
			if (body == null || player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.FAIL;
			}
			HitResult hit = player.pick(REACH, 0.0F, false);
			Vec3 at = hit.getType() == HitResult.Type.MISS ? player.position().add(player.getLookAngle().multiply(2.0, 0.0, 2.0)) : hit.getLocation();
			Entity beast = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), body), server, EntitySpawnReason.LOAD, e -> {
				e.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
				return e;
			});
			if (beast == null || !server.addFreshEntity(beast)) {
				return InteractionResult.FAIL;
			}
			server.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.02);
			server.playSound(null, beast.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.3F);
			ItemStack empty = new ItemStack(EMPTY);
			player.setItemInHand(hand, empty);
			player.getCooldowns().addCooldown(empty, REST);
			return InteractionResult.SUCCESS_SERVER;
		}
	}
}
