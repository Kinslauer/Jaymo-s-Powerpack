package com.jaymospowers.power.darktide;

import com.jaymospowers.core.Fate;
import com.jaymospowers.power.arsenal.Summoned;
import com.jaymospowers.power.web.KitchenCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * C and Z. Ascended: sneak + C <b>Tide Arms</b> (bare hands → twin daggers → katana of dark water; your real items are
 * stored and come back). While corrupted, Z on a Jaymo's Kitchen corpse takes its shadow; ascended, <b>Necromancy</b>: tap C
 * to raise one, which follows and defends you until the corruption lifts. Corrupted, not ascended: C is <b>Drown</b>, a
 * sphere of water around a target.
 */
final class DarktideTide {
	// ---- Tide Arms ----
	static final int ARMS_SWAP_COOLDOWN = 40;
	/** The {@link Summoned} batch that marks Tide Arms' conjured blades (they can't be dropped, stored or traded). */
	static final int TIDE_BATCH = 0x7D1DE;
	// ---- Necromancy ----
	static final double HARVEST_REACH = 5.0;
	static final int HARVEST_TICKS = 30;
	static final int MAX_SHADOWS = 5, MAX_RISEN = 3;
	static final double RISE_REACH = 10.0;
	static final double RISEN_FOLLOW = 5.0, RISEN_TELEPORT = 20.0, RISEN_LEASH = 48.0;
	static final double RISEN_SPEED = 1.25;
	/** How long a risen shadow keeps after what you fought (ticks). */
	static final int ENEMY_MEMORY = 200;
	static final int RAISE_COOLDOWN = 20;
	// ---- Drown ----
	static final double DROWN_REACH = 24.0, DROWN_RADIUS = 6.0;
	static final int DROWN_TICKS = 80, DROWN_COOLDOWN = 900, DROWN_EVERY = 10;
	static final float DROWN_DAMAGE = 1.0F;

	private record Zone(ServerPlayer owner, ServerLevel level, Vec3 center, long until) {
	}

	private record Enemy(int id, long until) {
	}

	private static final List<Zone> ZONES = new ArrayList<>();
	/** Risen shadow → its master. */
	private static final Map<UUID, UUID> RISEN_OWNER = new HashMap<>();
	private static final List<Mob> RISEN = new ArrayList<>();
	private static final Map<UUID, Enemy> ENEMIES = new HashMap<>();

	private DarktideTide() {
	}

	// ---- keys ---------------------------------------------------------------------------------------------------------

	/**
	 * C. Ascended: sneaking, Tide Arms; a tap raises a shadow while corrupted (cured, the shadows are dormant).
	 * Not ascended: Drown while corrupted.
	 */
	static void ultimateKey(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		boolean ascended = Fate.isAscended(player);
		boolean corrupted = DarktidePower.corrupted(player);
		if (ascended && player.isShiftKeyDown()) {
			cycleArms(player);
		} else if (!ascended) {
			if (corrupted) {
				drownKey(player, true);
			}
		} else if (!corrupted) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.shadows_dormant").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		} else if (DarktideState.save(player).shadows().isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_shadows").withStyle(ChatFormatting.GRAY));
		} else {
			raise(player);
		}
	}

	/** Z while corrupted (ascended or not): hold on a corpse to take its shadow. */
	static void modeKey(ServerPlayer player, boolean down) {
		DarktideLive live = DarktidePower.live(player);
		live.modeDown = down;
		if (!down) {
			if (live.harvesting >= 0) {
				live.harvesting = -1;
				DarktideMove.clear(player);
			}
			return;
		}
		Entity corpse = aimedCorpse(player);
		if (corpse == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_corpse").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (DarktideState.save(player).shadows().size() >= MAX_SHADOWS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.shadows_full").withStyle(ChatFormatting.GRAY));
			return;
		}
		live.harvesting = corpse.getId();
		live.harvestStart = player.level().getGameTime();
		DarktideMove.play(player, DarktideMove.CAST, HARVEST_TICKS, 1);
	}

	/** Drown (C, corrupted and not ascended). */
	static void drownKey(ServerPlayer player, boolean down) {
		if (!down || !DarktidePower.ready(player, DarktideState.DROWN)) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity target = DarktideMoves.pick(player, DROWN_REACH);
		Vec3 center;
		if (target != null) {
			center = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		} else {
			Vec3 eye = player.getEyePosition();
			BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(DROWN_REACH)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_target").withStyle(ChatFormatting.GRAY));
				return;
			}
			center = hit.getLocation().add(0.0, 1.0, 0.0);
		}
		ZONES.add(new Zone(player, level, center, level.getGameTime() + DROWN_TICKS));
		DarktideMove.play(player, DarktideMove.CAST, DarktideMove.CAST_TICKS, 2);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8F, 0.6F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.5F, 0.4F);
		DarktidePower.cooldown(player, DarktideState.DROWN, DROWN_COOLDOWN);
	}

	// ---- Tide Arms ----------------------------------------------------------------------------------------------------

	private static boolean conjured(ItemStack stack, ServerPlayer player) {
		Summoned s = Summoned.of(stack);
		return s != null && s.batch() == TIDE_BATCH && s.ownedBy(player.getUUID());
	}

	private static ItemStack mark(ItemStack stack, ServerPlayer player) {
		stack.set(Summoned.TYPE, new Summoned(player.getUUID(), Long.MAX_VALUE, TIDE_BATCH));
		return stack;
	}

	static void cycleArms(ServerPlayer player) {
		if (!DarktideWeapons.loaded()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_weaponmod").withStyle(ChatFormatting.GRAY));
			return;
		}
		DarktideLive live = DarktidePower.live(player);
		ServerLevel level = player.level();
		if (level.getGameTime() < live.armsSwapAt || !DarktidePower.ready(player, DarktideState.ARMS)) {
			return;
		}
		int next = (live.arms + 1) % 3;
		Inventory inventory = player.getInventory();
		if (live.arms == DarktideState.ARMS_BARE) {
			// put the real things away
			ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
			ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND).copy();
			player.setAttached(DarktideState.SAVE, DarktideState.save(player).withStored(ItemContainerContents.fromItems(List.of(main, off)),
				inventory.getSelectedSlot()));
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		} else {
			removeConjured(player);
		}
		switch (next) {
			case DarktideState.ARMS_DAGGERS -> {
				player.setItemInHand(InteractionHand.MAIN_HAND, mark(DarktideWeapons.conjure("knife"), player));
				player.setItemInHand(InteractionHand.OFF_HAND, mark(DarktideWeapons.conjure("knife"), player));
			}
			case DarktideState.ARMS_KATANA -> {
				player.setItemInHand(InteractionHand.MAIN_HAND, mark(DarktideWeapons.conjure("katana"), player));
				restoreOffhand(player);
			}
			default -> restoreStored(player);
		}
		live.arms = next;
		live.armsSwapAt = level.getGameTime() + ARMS_SWAP_COOLDOWN;
		DarktidePower.cooldown(player, DarktideState.ARMS, ARMS_SWAP_COOLDOWN);
		DarktideMove.play(player, DarktideMove.CAST, DarktideMove.CAST_TICKS, 3);
		Vec3 hand = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.SPLASH, hand.x, hand.y, hand.z, 24, 0.35, 0.3, 0.35, 0.15);
		level.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 12, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.9F, 0.6F);
	}

	private static void removeConjured(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (conjured(inventory.getItem(slot), player)) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (conjured(player.containerMenu.getCarried(), player)) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
	}

	private static boolean holdsConjured(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (conjured(inventory.getItem(slot), player)) {
				return true;
			}
		}
		return conjured(player.containerMenu.getCarried(), player);
	}

	private static NonNullList<ItemStack> stored(ServerPlayer player) {
		NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
		DarktideState.save(player).stored().copyInto(items);
		return items;
	}

	private static void restoreOffhand(ServerPlayer player) {
		NonNullList<ItemStack> items = stored(player);
		if (!items.get(1).isEmpty()) {
			give(player, items.get(1), InteractionHand.OFF_HAND, -1);
			player.setAttached(DarktideState.SAVE, DarktideState.save(player).withStored(ItemContainerContents.fromItems(List.of(items.get(0), ItemStack.EMPTY)),
				DarktideState.save(player).storedSlot()));
		}
	}

	private static void restoreStored(ServerPlayer player) {
		NonNullList<ItemStack> items = stored(player);
		DarktideState.Save save = DarktideState.save(player);
		give(player, items.get(0), InteractionHand.MAIN_HAND, save.storedSlot());
		give(player, items.get(1), InteractionHand.OFF_HAND, -1);
		player.setAttached(DarktideState.SAVE, save.withStored(ItemContainerContents.EMPTY, -1));
	}

	/** Back where it was if that's free, else anywhere in the inventory, else on the ground at your feet. */
	private static void give(ServerPlayer player, ItemStack stack, InteractionHand hand, int slot) {
		if (stack.isEmpty()) {
			return;
		}
		Inventory inventory = player.getInventory();
		if (hand == InteractionHand.OFF_HAND && player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
			player.setItemInHand(InteractionHand.OFF_HAND, stack);
			return;
		}
		if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
			inventory.setItem(slot, stack);
			return;
		}
		if (!inventory.add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Tide Arms ends: the conjured blades go, the real things come back. */
	static void revertArms(ServerPlayer player, boolean effects) {
		DarktideLive live = DarktidePower.liveIfAny(player);
		boolean stored = DarktideState.save(player).storedSlot() >= 0 || !DarktideState.save(player).stored().equals(ItemContainerContents.EMPTY);
		if ((live == null || live.arms == DarktideState.ARMS_BARE) && !stored && !holdsConjured(player)) {
			return;
		}
		removeConjured(player);
		restoreStored(player);
		if (live != null) {
			live.arms = DarktideState.ARMS_BARE;
		}
		if (effects) {
			player.level().sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
		}
	}

	// ---- Necromancy ---------------------------------------------------------------------------------------------------

	private static @Nullable Entity aimedCorpse(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Entity best = null;
		double bestDot = 0.93;
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(HARVEST_REACH))) {
			if (KitchenCompat.freeCorpseOf(entity) == null) {
				continue;
			}
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			if (to.length() > HARVEST_REACH + 1.0) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = entity;
			}
		}
		return best;
	}

	private static void tickHarvest(ServerPlayer player, DarktideLive live) {
		ServerLevel level = player.level();
		Entity corpse = level.getEntity(live.harvesting);
		EntityType<?> type = corpse == null ? null : KitchenCompat.freeCorpseOf(corpse);
		if (type == null || !live.modeDown || corpse.distanceTo(player) > HARVEST_REACH + 1.5 || !DarktidePower.corrupted(player)) {
			live.harvesting = -1;
			DarktideMove.clear(player);
			return;
		}
		long t = level.getGameTime() - live.harvestStart;
		// soul sand streaming from the corpse into the outstretched hand
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.7)).add(0.0, -0.35, 0.0);
		Vec3 from = corpse.getBoundingBox().getCenter();
		Vec3 flow = hand.subtract(from);
		var random = level.getRandom();
		for (int i = 0; i < 4; i++) {
			Vec3 p = from.add((random.nextDouble() - 0.5) * corpse.getBbWidth(), (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * corpse.getBbWidth());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SOUL_SAND.defaultBlockState()), p.x, p.y, p.z, 0,
				flow.x, flow.y + 0.2, flow.z, 0.12);
			level.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 0, flow.x, flow.y, flow.z, 0.08);
		}
		if (t % 10 == 0) {
			level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_SAND_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
		if (t < HARVEST_TICKS) {
			return;
		}
		// the corpse crumbles; its shadow is yours
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SOUL_SAND.defaultBlockState()), from.x, from.y, from.z, 40,
			corpse.getBbWidth() / 2.0, 0.3, corpse.getBbWidth() / 2.0, 0.1);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		corpse.discard();
		List<String> shadows = DarktideState.mutable(DarktideState.save(player).shadows());
		shadows.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		player.setAttached(DarktideState.SAVE, DarktideState.save(player).withShadows(shadows));
		live.harvesting = -1;
		DarktideMove.clear(player);
	}

	private static void raise(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		ServerLevel level = player.level();
		if (!DarktidePower.ready(player, DarktideState.RAISE)) {
			return;
		}
		live.risen.removeIf(id -> !(level.getEntity(id) instanceof Mob mob) || !mob.isAlive());
		if (live.risen.size() >= MAX_RISEN) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.risen_full").withStyle(ChatFormatting.GRAY));
			return;
		}
		List<String> shadows = DarktideState.mutable(DarktideState.save(player).shadows());
		String id = shadows.removeLast();
		player.setAttached(DarktideState.SAVE, DarktideState.save(player).withShadows(shadows));
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(id));
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(RISE_REACH)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		BlockPos at = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(eye.add(player.getLookAngle().scale(3.0))) : hit.getBlockPos().relative(hit.getDirection());
		Entity spawned = type == null ? null : type.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
		if (!(spawned instanceof Mob mob)) {
			if (spawned != null) {
				spawned.discard();
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.shadow_lost").withStyle(ChatFormatting.GRAY));
			return;
		}
		mob.setAttached(DarktideState.RISEN, player.getId());
		RISEN_OWNER.put(mob.getUUID(), player.getUUID());
		RISEN.add(mob);
		live.risen.add(mob.getId());
		mob.setTarget(null);
		mob.clearFire();
		level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.3, mob.getZ(), 40, 0.4, 0.2, 0.4, 0.05);
		level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SOUL_SAND.defaultBlockState()), mob.getX(), mob.getY(), mob.getZ(),
			30, 0.4, 0.1, 0.4, 0.1);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.6F, 1.4F);
		DarktideMove.play(player, DarktideMove.CAST, DarktideMove.CAST_TICKS, 4);
		DarktidePower.cooldown(player, DarktideState.RAISE, RAISE_COOLDOWN);
	}

	/** Whether {@code entity} is one of {@code player}'s risen shadows. */
	static boolean risenOf(Entity entity, ServerPlayer player) {
		UUID owner = RISEN_OWNER.get(entity.getUUID());
		return owner != null && owner.equals(player.getUUID());
	}

	/** What the risen go for: what hurt you, or what you hit. */
	static void enemy(ServerPlayer player, LivingEntity entity) {
		if (risenOf(entity, player) || !entity.isAlive()) {
			return;
		}
		ENEMIES.put(player.getUUID(), new Enemy(entity.getId(), player.level().getGameTime() + ENEMY_MEMORY));
	}

	private static void melt(Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(), 30, 0.3, 0.5, 0.3, 0.04);
			level.sendParticles(new DustParticleOptions(0x050510, 1.6F), mob.getX(), mob.getY() + 0.5, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.0);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
		}
		RISEN_OWNER.remove(mob.getUUID());
		mob.discard();
	}

	static void meltAll(ServerPlayer player) {
		for (Iterator<Mob> it = RISEN.iterator(); it.hasNext(); ) {
			Mob mob = it.next();
			if (risenOf(mob, player)) {
				it.remove();
				melt(mob);
			}
		}
		DarktideLive live = DarktidePower.liveIfAny(player);
		if (live != null) {
			live.risen.clear();
		}
	}

	/** A risen shadow never dies into loot or a corpse: it melts. */
	static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof Mob mob && RISEN_OWNER.containsKey(mob.getUUID())) {
			RISEN.remove(mob);
			melt(mob);
			return false;
		}
		return true;
	}

	private static void tickRisen(MinecraftServer server) {
		for (Iterator<Mob> it = RISEN.iterator(); it.hasNext(); ) {
			Mob mob = it.next();
			UUID ownerId = RISEN_OWNER.get(mob.getUUID());
			ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
			if (mob.isRemoved() || !mob.isAlive()) {
				it.remove();
				RISEN_OWNER.remove(mob.getUUID());
				continue;
			}
			// they melt when the corruption lifts (sunrise, for a cured master), and without their master near
			if (owner == null || owner.level() != mob.level() || !DarktidePower.has(owner) || !DarktidePower.corrupted(owner)
				|| mob.distanceTo(owner) > RISEN_LEASH) {
				it.remove();
				melt(mob);
				continue;
			}
			mob.clearFire();
			Enemy enemy = ENEMIES.get(owner.getUUID());
			LivingEntity foe = null;
			if (enemy != null && mob.level().getGameTime() < enemy.until && mob.level().getEntity(enemy.id) instanceof LivingEntity target
				&& target.isAlive() && target != mob && !risenOf(target, owner)) {
				foe = target;
			}
			if (mob.getTarget() != foe) {
				mob.setTarget(foe);
			}
			if (foe == null) {
				double d = mob.distanceTo(owner);
				if (d > RISEN_TELEPORT) {
					mob.snapTo(owner.getX(), owner.getY(), owner.getZ());
					mob.getNavigation().stop();
				} else if (d > RISEN_FOLLOW && mob.tickCount % 10 == 0) {
					mob.getNavigation().moveTo(owner, RISEN_SPEED);
				}
			}
			if (mob.tickCount % 8 == 0 && mob.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
			}
		}
	}

	// ---- ticking ------------------------------------------------------------------------------------------------------

	static void tick(ServerPlayer player, DarktideLive live) {
		if (live.harvesting >= 0) {
			tickHarvest(player, live);
		}
		DarktideState.Save save = DarktideState.save(player);
		if (live.arms != DarktideState.ARMS_BARE && !holdsConjured(player)) {
			// the blades were lost somehow (dropped, dissolved): the real things come back
			revertArms(player, true);
		} else if (live.arms == DarktideState.ARMS_BARE && save.storedSlot() >= 0) {
			// stored items left from before a restart
			revertArms(player, false);
		} else if (live.arms != DarktideState.ARMS_BARE && !Fate.isAscended(player)) {
			revertArms(player, true);
		}
		if (player.tickCount % 20 == 0) {
			live.risen.removeIf(id -> !(player.level().getEntity(id) instanceof Mob mob) || !mob.isAlive());
		}
	}

	static void tickWorld(MinecraftServer server) {
		if (!RISEN.isEmpty()) {
			tickRisen(server);
		}
		if (ZONES.isEmpty()) {
			return;
		}
		for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
			Zone zone = it.next();
			ServerLevel level = zone.level;
			long now = level.getGameTime();
			if (now >= zone.until || zone.owner.isRemoved()) {
				it.remove();
				level.sendParticles(ParticleTypes.SPLASH, zone.center.x, zone.center.y - DROWN_RADIUS * 0.5, zone.center.z, 80, DROWN_RADIUS * 0.5, 0.3,
					DROWN_RADIUS * 0.5, 0.3);
				continue;
			}
			var random = level.getRandom();
			for (int i = 0; i < 14; i++) {
				// a ball of dark water: bubbles through it, a shimmering skin
				Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
				Vec3 skin = zone.center.add(dir.scale(DROWN_RADIUS));
				level.sendParticles(ParticleTypes.FALLING_WATER, skin.x, skin.y, skin.z, 1, 0.0, 0.0, 0.0, 0.0);
				Vec3 inside = zone.center.add(dir.scale(random.nextDouble() * DROWN_RADIUS));
				level.sendParticles(ParticleTypes.BUBBLE_POP, inside.x, inside.y, inside.z, 1, 0.0, 0.0, 0.0, 0.0);
				if (i % 3 == 0) {
					level.sendParticles(new DustParticleOptions(0x0C2450, 1.5F), inside.x, inside.y, inside.z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			}
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(zone.center, zone.center).inflate(DROWN_RADIUS + 1.0),
					e -> e.isAlive() && !e.isSpectator() && e != zone.owner && !risenOf(e, zone.owner))) {
				Vec3 off = victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0).subtract(zone.center);
				double d = off.length();
				if (d > DROWN_RADIUS + 0.5) {
					continue;
				}
				// can't swim out: pulled back from the skin, and held low in the water
				if (d > DROWN_RADIUS - 1.2) {
					Vec3 back = off.normalize().scale(-0.25);
					victim.setDeltaMovement(back.x, Math.min(back.y, 0.02), back.z);
					victim.syncVelocity = true;
				} else {
					victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.6, 0.6, 0.6));
				}
				victim.setAirSupply(Math.max(-19, victim.getAirSupply() - 6));
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2), zone.owner);
				if (now % DROWN_EVERY == 0) {
					victim.setInvulnerableTime(0);
					victim.hurtServer(level, level.damageSources().drown(), DROWN_DAMAGE);
				}
			}
		}
	}

	static void clear() {
		ZONES.clear();
		for (Mob mob : RISEN) {
			mob.discard();
		}
		RISEN.clear();
		RISEN_OWNER.clear();
		ENEMIES.clear();
	}
}
