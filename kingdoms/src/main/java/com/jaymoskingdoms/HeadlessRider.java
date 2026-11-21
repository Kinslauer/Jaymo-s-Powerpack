package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import com.jaymoskingdoms.disasters.Mist;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The headless rider (Andy's pick ld_rider): an undead knight on an undead horse (a skeleton horse, which wears the pack's own
 * build), out on the roads that pass a graveyard while the morning mist lies ({@link Mist}). He was beheaded on that road and
 * buried without his head; he chases whoever is out, and takes the head of anyone downed in his reach (a player is only ever
 * beheaded downed). A boss bar shows while he rides. He rides off when the mist thins.
 * <p>
 * Kill him and he drops his own head ({@link #HEAD}). Carry it by day to the grave he rode from (a headstone of the road's
 * graveyard) and use it there: he lies down for good, for that road, and his people leave a reward. Leaving him costs a night's
 * care, nothing more.
 */
final class HeadlessRider {
	// ---- tuning ----
	/** The ride is looked at this often (ticks). */
	static final int EVERY = 20;
	/** The mist must be this thick for him to ride out, and thinner than {@link #LEAVES} sends him back. */
	static final float OUT_MIST = 0.3F, LEAVES = 0.12F;
	/** Each look while the mist lies, the chance he rides out (once a night). */
	static final float RIDE_CHANCE = 0.01F;
	/** He appears on a road this far from the player, nearest and farthest (blocks); the road must pass a grave within this far. */
	static final int SPAWN_MIN = 48, SPAWN_MAX = 96, GRAVE_NEAR = 64;
	/** Health, how far he sees, and how near a downed player must be for his blade to take the head (blocks). */
	static final double HEALTH = 60.0, SIGHT = 48.0, REACH = 2.6;
	/** The boss bar shows to players this near (blocks). */
	static final double BAR_RANGE = 80.0;
	/** A head laid at its grave: how near (blocks), and the reward. */
	static final int GRAVE_REACH = 6, REWARD_EMERALDS = 12, REWARD_XP = 60;

	static Item HEAD;
	private static final String TAG = "jaymoskingdoms_rider", ROAD = "jaymoskingdoms_rider_road:", GRAVE = "jaymoskingdoms_rider_grave:";
	/** The roads he has been laid to rest on. */
	private static final AttachmentType<List<String>> RESTED = AttachmentRegistry.create(JaymosKingdoms.id("rider_rested"),
		b -> b.persistent(Codec.STRING.listOf()));

	private static UUID rider;
	private static UUID mount;
	private static long nightOf = Long.MIN_VALUE;
	private static final ServerBossEvent BAR = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.jaymoskingdoms.headless_rider"),
		BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

	private HeadlessRider() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("riders_head"));
		HEAD = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		ServerTickEvents.END_SERVER_TICK.register(HeadlessRider::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			rider = null;
			mount = null;
			BAR.removeAllPlayers();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level && entity.getUUID().equals(rider)) {
				dropHead(level, entity);
				end(level, false);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || !held.is(HEAD) || !server.isBrightOutside()) {
				return InteractionResult.PASS;
			}
			CustomData data = held.get(DataComponents.CUSTOM_DATA);
			if (data == null) {
				return InteractionResult.PASS;
			}
			CompoundTag tag = data.copyTag();
			BlockPos grave = BlockPos.of(tag.getLongOr("grave", 0L));
			if (hit.getBlockPos().distSqr(grave) > (double)GRAVE_REACH * GRAVE_REACH) {
				return InteractionResult.PASS;
			}
			layToRest(server, sp, held, tag.getStringOr("road", ""));
			return InteractionResult.SUCCESS;
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (rider != null) {
			Entity knight = level.getEntity(rider);
			if (knight == null || !knight.isAlive()) {
				end(level, false);
			} else if (Mist.hour(level) < LEAVES) {
				end(level, true);
			} else {
				follow(level, knight);
			}
			return;
		}
		float mist = Mist.hour(level);
		long night = level.getOverworldClockTime() / 24000L;
		if (mist >= OUT_MIST && night != nightOf && level.getRandom().nextFloat() < RIDE_CHANCE) {
			for (ServerPlayer player : level.players()) {
				if (ride(level, player)) {
					nightOf = night;
					break;
				}
			}
		}
	}

	/** The bar for those near him, and the blade for anyone downed in his reach. */
	private static void follow(ServerLevel level, Entity knight) {
		if (knight instanceof net.minecraft.world.entity.LivingEntity living) {
			BAR.setProgress(Math.max(0.0F, Math.min(1.0F, living.getHealth() / living.getMaxHealth())));
		}
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(knight) <= BAR_RANGE * BAR_RANGE) {
				BAR.addPlayer(player);
				if (Downed.isDowned(player) && player.distanceToSqr(knight) <= REACH * REACH && knight instanceof net.minecraft.world.entity.LivingEntity living) {
					PowersCompat.beheadPinned(player, living, knight.position(), knight.getYRot());
				}
			} else {
				BAR.removePlayer(player);
			}
		}
	}

	/** He rides out on a road near {@code player} that passes a graveyard and has not been laid to rest. */
	private static boolean ride(ServerLevel level, ServerPlayer player) {
		List<BlockPos> graves = new ArrayList<>();
		for (Bearers.Grave g : level.getAttachedOrElse(Bearers.GRAVES, List.<Bearers.Grave>of())) {
			graves.add(BlockPos.of(g.head()));
		}
		if (graves.isEmpty()) {
			return false;
		}
		List<String> rested = level.getAttachedOrElse(RESTED, List.<String>of());
		for (Roads.Road road : Roads.network(level.getServer()).roads()) {
			if (rested.contains(road.key())) {
				continue;
			}
			for (int i = 0; i < road.points().size(); i += 6) {
				BlockPos p = BlockPos.of(road.points().get(i));
				double away = Math.hypot(p.getX() - player.getX(), p.getZ() - player.getZ());
				if (away < SPAWN_MIN || away > SPAWN_MAX || !Loaded.made(level, p)) {
					continue;
				}
				BlockPos grave = null;
				for (BlockPos g : graves) {
					if (Math.hypot(p.getX() - g.getX(), p.getZ() - g.getZ()) <= GRAVE_NEAR) {
						grave = g;
						break;
					}
				}
				if (grave != null && mount(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p), road.key(), grave)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean mount(ServerLevel level, BlockPos at, String road, BlockPos grave) {
		Zombie knight = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.EVENT);
		if (knight == null || horse == null) {
			return false;
		}
		horse.setTamed(true);
		horse.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		knight.snapTo(horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), 0.0F);
		knight.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		knight.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT);
		knight.setHealth((float)HEALTH);
		knight.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		knight.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		knight.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		knight.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		knight.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		horse.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		knight.addTag(TAG);
		knight.addTag(ROAD + road);
		knight.addTag(GRAVE + grave.asLong());
		horse.addTag(TAG);
		PowersCompat.bornWithout(knight, 1);
		level.addFreshEntity(horse);
		level.addFreshEntity(knight);
		knight.startRiding(horse, true, false);
		rider = knight.getUUID();
		mount = horse.getUUID();
		BAR.setProgress(1.0F);
		level.playSound(null, at, SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
		return true;
	}

	/** The ride is over: he rides off into the mist (both gone), or he is dead. */
	private static void end(ServerLevel level, boolean away) {
		if (away) {
			for (UUID id : new UUID[] {rider, mount}) {
				Entity e = id == null ? null : level.getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
		}
		rider = null;
		mount = null;
		BAR.removeAllPlayers();
	}

	private static void dropHead(ServerLevel level, Entity knight) {
		String road = "";
		long grave = 0L;
		for (String tag : knight.entityTags()) {
			if (tag.startsWith(ROAD)) {
				road = tag.substring(ROAD.length());
			} else if (tag.startsWith(GRAVE)) {
				grave = Long.parseLong(tag.substring(GRAVE.length()));
			}
		}
		ItemStack head = new ItemStack(HEAD);
		CompoundTag tag = new CompoundTag();
		tag.putString("road", road);
		tag.putLong("grave", grave);
		head.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		head.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.riders_head"));
		net.minecraft.world.level.block.Block.popResource(level, knight.blockPosition(), head);
	}

	private static void layToRest(ServerLevel level, ServerPlayer player, ItemStack head, String road) {
		head.shrink(1);
		List<String> rested = new ArrayList<>(level.getAttachedOrElse(RESTED, List.<String>of()));
		if (!rested.contains(road)) {
			rested.add(road);
			level.setAttached(RESTED, List.copyOf(rested));
		}
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, REWARD_EMERALDS), Prediction.SERVER_ONLY);
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.DIAMOND), Prediction.SERVER_ONLY);
		player.giveExperiencePoints(REWARD_XP);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.rests").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
	}
}
