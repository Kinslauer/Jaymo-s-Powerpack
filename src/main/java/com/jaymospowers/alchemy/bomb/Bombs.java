package com.jaymospowers.alchemy.bomb;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.hunter.Refills;
import com.jaymospowers.alchemy.oil.Acid;
import com.jaymospowers.alchemy.oil.OilKind;
import com.jaymospowers.alchemy.oil.OilSlickBlock;
import com.jaymospowers.alchemy.oil.Oils;
import com.jaymospowers.alchemy.toxic.Harms;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The eight bombs (Hunter's Road, patch 7, "D5"), and every other flask that is thrown ({@link OilKind}, from an oil sash: "T-C3").
 * A bomb is crafted, thrown ({@link Bomb}, after Kingdoms' {@code GasBottle}: a flask that breaks where it lands) and spent: the
 * item is used up, and a <b>spent casing</b> comes back to the pack ({@link Refills#spentCasing}) that strong spirit fills again.
 * <ul>
 * <li><b>Fire burst</b>: a blast of flame; it breaks what the {@link #BURST_BREAKS} tag lists (nests), where nothing else breaks.</li>
 * <li><b>Stunning flash</b>: monsters stop where they stand ({@link DarktideStatus#stun}); a player who sees it is blinded.</li>
 * <li><b>Fire spread</b>: fire laid across the ground, which takes what burns.</li>
 * <li><b>Poison cloud</b>: a cloud that poisons all who breathe in it.</li>
 * <li><b>Volatile gas</b>: a cloud that does nothing until flame touches it, and then blows ({@link #Gas}).</li>
 * <li><b>Freezing blast</b>: ices the water, freezes foes, and fliers fall.</li>
 * <li><b>Magic snuffer</b>: nothing teleports, casts or uses a portal in it, and powers fail ({@link BombStatus}).</li>
 * <li><b>Healing-stopper dust</b>: nothing in it heals, turns or stays invisible ({@link BombStatus}).</li>
 * </ul>
 * A thrown oil breaks into what its kind does ({@link #splash}); every oil but a splash, smoke and acid also spills a slick
 * ({@link OilSlickBlock}) where it lands on the ground.
 */
public final class Bombs {
	// ---- tuning ----
	/** How hard a flask is thrown (a snowball is 1.5), and how far it strays. */
	public static final float POWER = 1.2F, SPREAD = 1.0F;
	/** Fire burst: radius (blocks), the hurt at the middle, seconds alight. */
	public static final double BURST_RADIUS = 3.5;
	public static final float BURST_HURT = 10.0F, BURST_BURN = 6.0F;
	/** Stunning flash: how far it dazzles (blocks), ticks a monster is held, ticks a player is blind. */
	public static final double FLASH_RADIUS = 9.0;
	public static final int FLASH_STUN = 80, FLASH_BLIND = 100;
	/** Fire spread: radius (blocks), the share of free ground that catches, seconds creatures burn. */
	public static final int SPREAD_RADIUS = 4;
	public static final float SPREAD_SHARE = 0.55F, SPREAD_BURN = 5.0F;
	/** Poison cloud: radius, ticks it hangs, ticks of poison and its level. */
	public static final float CLOUD_RADIUS = 3.5F;
	public static final int CLOUD_TICKS = 20 * 12, CLOUD_POISON = 100, CLOUD_POISON_LEVEL = 1;
	/** Volatile gas: radius (blocks), ticks it hangs, and the force of the blast. */
	public static final double GAS_RADIUS = 3.5;
	public static final int GAS_TICKS = 20 * 20;
	public static final float GAS_BLAST = 3.5F;
	/** Freezing blast: radius, slowness ticks, ticks a flier is held down, the hurt of the cold. */
	public static final int FREEZE_RADIUS = 4, FREEZE_SLOW = 100, GROUNDED_TICKS = 100;
	public static final float FREEZE_HURT = 4.0F;
	/** Magic snuffer: radius (blocks). */
	public static final int SNUFF_RADIUS = 6;
	/** Healing-stopper dust: radius (blocks). */
	public static final double DUST_RADIUS = 5.0;
	/** Blocks a fire burst breaks, whatever they are (a data tag: nests join it). */
	public static final TagKey<Block> BURST_BREAKS = TagKey.create(Registries.BLOCK, JaymosPowers.id("burst_breaks"));

	/** The eight bombs. {@code color} is the colour of the flash and the cloud. */
	public enum Kind {
		FIRE_BURST("fire_burst", 0xF08030), STUN_FLASH("stun_flash", 0xF4F0D0), FIRE_SPREAD("fire_spread", 0xE05818),
		POISON_CLOUD("poison_cloud", 0x6FA03A), VOLATILE_GAS("volatile_gas", 0xB8C060), FREEZE_BLAST("freeze_blast", 0x9AD8F0),
		SNUFFER("snuffer", 0x8A7AB0), STOPPER_DUST("stopper_dust", 0xB09878);

		public final String id;
		public final int color;
		/** Set when the item is registered. */
		public Item item;

		Kind(String id, int color) {
			this.id = id;
			this.color = color;
		}

		public String itemId() {
			return "bomb_" + this.id;
		}
	}

	public static EntityType<Bomb> TYPE;

	/** A cloud of volatile gas hanging in the air. */
	private record Gas(ServerLevel level, Vec3 at, long until, @Nullable UUID owner) {
	}

	private static final List<Gas> GASES = new ArrayList<>();
	/** Fliers held down by the cold: the game time each is free again. */
	private static final Map<Entity, Long> GROUNDED = new IdentityHashMap<>();

	private Bombs() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("bomb"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Bomb>of(Bomb::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(key));
		for (Kind kind : Kind.values()) {
			kind.item = Alchemy.item(kind.itemId(), p -> new BombItem(p.stacksTo(16), kind));
		}
		BombStatus.init();
		ServerTickEvents.END_SERVER_TICK.register(Bombs::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			GASES.clear();
			GROUNDED.clear();
		});
	}

	/** Which bomb {@code stack} is, or null. */
	public static @Nullable Kind bombOf(ItemStack stack) {
		for (Kind k : Kind.values()) {
			if (k.item != null && stack.is(k.item)) {
				return k;
			}
		}
		return null;
	}

	// ---- throwing -----------------------------------------------------------------------------------------------------

	/** Throws {@code stack} (a bomb, or a flask of oil) from {@code thrower}'s hand. The caller spends what was held. */
	public static void throwFrom(ServerLevel level, LivingEntity thrower, ItemStack stack) {
		Projectile.spawnProjectileFromRotation(Bomb::new, level, stack, thrower, -10.0F, POWER, SPREAD);
	}

	/** A bomb item: use it to throw it; the casing comes back. */
	public static final class BombItem extends Item {
		public final Kind kind;

		public BombItem(Item.Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.7F);
			if (level instanceof ServerLevel server) {
				throwFrom(server, player, held.copyWithCount(1));
				ItemStack casing = Refills.spentCasing(held);
				held.consume(1, player);
				if (!player.hasInfiniteMaterials()) {
					player.getInventory().placeItemBackInInventory(casing, Prediction.SERVER_ONLY);
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
			java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
			builder.accept(net.minecraft.network.chat.Component.translatable("tooltip.jaymospowers.bomb." + this.kind.id)
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			super.appendHoverText(stack, context, display, builder, flag);
		}
	}

	// ---- landing ------------------------------------------------------------------------------------------------------

	/** A thrown {@code stack} breaks at {@code at}; {@code ground} is the free block it landed in. */
	public static void land(ServerLevel level, Vec3 at, ItemStack stack, @Nullable Entity owner, BlockPos ground) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
		Kind bomb = bombOf(stack);
		if (bomb != null) {
			burst(level, at, bomb, owner, ground);
			return;
		}
		OilKind oil = OilKind.of(stack);
		if (oil != null) {
			splash(level, at, oil, Oils.tier(stack), owner, ground);
		}
	}

	private static void burst(ServerLevel level, Vec3 at, Kind kind, @Nullable Entity owner, BlockPos ground) {
		switch (kind) {
			case FIRE_BURST -> fireBurst(level, at, owner);
			case STUN_FLASH -> stunFlash(level, at, owner);
			case FIRE_SPREAD -> fireSpread(level, at, ground);
			case POISON_CLOUD -> poisonCloud(level, at, owner);
			case VOLATILE_GAS -> {
				GASES.add(new Gas(level, at, level.getGameTime() + GAS_TICKS, owner == null ? null : owner.getUUID()));
				MagicFx.blast(level, at.x, at.y, at.z, 1.2F, kind.color);
			}
			case FREEZE_BLAST -> freezeBlast(level, at, owner);
			case SNUFFER -> {
				BombStatus.snuff(level, BlockPos.containing(at), SNUFF_RADIUS);
				MagicFx.blast(level, at.x, at.y, at.z, SNUFF_RADIUS * 0.6F, kind.color);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 1.4F);
			}
			case STOPPER_DUST -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(DUST_RADIUS))) {
					if (e.position().distanceToSqr(at) <= DUST_RADIUS * DUST_RADIUS) {
						BombStatus.stop(e, BombStatus.UNHEAL_TICKS);
					}
				}
				level.sendParticles(MagicFx.miasma(kind.color, 1.6F), at.x, at.y + 0.5, at.z, 40, DUST_RADIUS * 0.4, 0.6, DUST_RADIUS * 0.4, 0.01);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
			}
		}
	}

	private static void fireBurst(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.blast(level, at.x, at.y, at.z, (float) BURST_RADIUS, Kind.FIRE_BURST.color);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 40, 1.0, 0.6, 1.0, 0.12);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
		// fire damage (the in-fire type, {@code #minecraft:is_fire}), so a vampire takes its fire weakness from the blast as well as from the burning
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(net.minecraft.world.damagesource.DamageTypes.IN_FIRE), null, owner);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS))) {
			double d = Math.sqrt(e.position().distanceToSqr(at));
			if (d <= BURST_RADIUS) {
				e.hurtServer(level, source, (float) (BURST_HURT * (1.0 - 0.5 * d / BURST_RADIUS)));
				e.igniteForSeconds(BURST_BURN);
			}
		}
		BlockPos centre = BlockPos.containing(at);
		int r = (int) Math.ceil(BURST_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (pos.distSqr(centre) <= BURST_RADIUS * BURST_RADIUS && level.getBlockState(pos).is(BURST_BREAKS)) {
				level.destroyBlock(pos, true, owner);
			}
		}
	}

	private static void stunFlash(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.blast(level, at.x, at.y, at.z, 3.0F, Kind.STUN_FLASH.color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5F, 1.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(FLASH_RADIUS))) {
			if (e.position().distanceToSqr(at) > FLASH_RADIUS * FLASH_RADIUS) {
				continue;
			}
			if (e instanceof ServerPlayer player) {
				Vec3 toFlash = at.subtract(player.getEyePosition()).normalize();
				if (player.getLookAngle().dot(toFlash) > -0.2) {
					player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLASH_BLIND, 0));
				}
			} else if (e instanceof Mob mob && (mob instanceof Enemy || mob.getTarget() != null)) {
				mob.setTarget(null);
				mob.getNavigation().stop();
				DarktideStatus.stun(mob, FLASH_STUN);
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FLASH_STUN, 5));
			}
		}
	}

	private static void fireSpread(ServerLevel level, Vec3 at, BlockPos ground) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.7F);
		MagicFx.blast(level, at.x, at.y, at.z, 2.0F, Kind.FIRE_SPREAD.color);
		for (BlockPos column : BlockPos.betweenClosed(ground.offset(-SPREAD_RADIUS, -2, -SPREAD_RADIUS), ground.offset(SPREAD_RADIUS, 2, SPREAD_RADIUS))) {
			if (column.distSqr(ground) > SPREAD_RADIUS * SPREAD_RADIUS || level.getRandom().nextFloat() > SPREAD_SHARE) {
				continue;
			}
			BlockState here = level.getBlockState(column);
			if (here.isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, column) || here.is(OilSlickBlock.SLICK)) {
				level.setBlockAndUpdate(column, Blocks.FIRE.defaultBlockState());
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground).inflate(SPREAD_RADIUS))) {
			e.igniteForSeconds(SPREAD_BURN);
		}
	}

	private static void poisonCloud(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		if (owner instanceof LivingEntity living) {
			cloud.setOwner(living);
		}
		cloud.setRadius(CLOUD_RADIUS);
		cloud.setDuration(CLOUD_TICKS);
		cloud.setRadiusPerTick(-CLOUD_RADIUS / CLOUD_TICKS);
		cloud.setWaitTime(2);
		cloud.setCustomParticle(MagicFx.miasma(Kind.POISON_CLOUD.color, 1.4F));
		cloud.addEffect(new MobEffectInstance(MobEffects.POISON, CLOUD_POISON, CLOUD_POISON_LEVEL));
		level.addFreshEntity(cloud);
	}

	private static void freezeBlast(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.blast(level, at.x, at.y, at.z, FREEZE_RADIUS, Kind.FREEZE_BLAST.color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.4, at.z, 50, FREEZE_RADIUS * 0.4, 0.6, FREEZE_RADIUS * 0.4, 0.05);
		BlockPos centre = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-FREEZE_RADIUS, -FREEZE_RADIUS, -FREEZE_RADIUS),
			centre.offset(FREEZE_RADIUS, FREEZE_RADIUS, FREEZE_RADIUS))) {
			if (pos.distSqr(centre) > FREEZE_RADIUS * FREEZE_RADIUS) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE)) {
				level.removeBlock(pos, false);
			} else if (state.is(Blocks.WATER) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).getFluidState().isEmpty()) {
				level.setBlockAndUpdate(pos.immutable(), Blocks.ICE.defaultBlockState());
			}
		}
		DamageSource cold = level.damageSources().freeze();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(FREEZE_RADIUS))) {
			if (e.position().distanceToSqr(at) > (double) FREEZE_RADIUS * FREEZE_RADIUS) {
				continue;
			}
			e.setTicksFrozen(e.getTicksRequiredToFreeze() + 80);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_SLOW, 3));
			if (!(e instanceof Player) || e != owner) {
				e.hurtServer(level, cold, FREEZE_HURT);
			}
			if (flies(e)) {
				e.setNoGravity(false);
				GROUNDED.put(e, level.getGameTime() + GROUNDED_TICKS);
			}
			if (e instanceof ServerPlayer player && player.isFallFlying()) {
				player.stopFallFlying();
			}
		}
	}

	/** Whether {@code e} keeps itself in the air: a flying mob (bats, phantoms, vexes, blazes, ghasts, allays, bees) or a gliding player. */
	private static boolean flies(LivingEntity e) {
		return e instanceof net.minecraft.world.entity.ambient.Bat
			|| e instanceof net.minecraft.world.entity.monster.Vex || e instanceof net.minecraft.world.entity.monster.Phantom
			|| e instanceof net.minecraft.world.entity.monster.Ghast || e instanceof net.minecraft.world.entity.monster.Blaze
			|| e instanceof Mob mob && mob.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
	}

	// ---- thrown oils --------------------------------------------------------------------------------------------------

	/**
	 * A thrown flask of {@code oil} at grade {@code tier} breaks at {@code at}. Smoke blinds, poison and the haze hang as a cloud,
	 * toxin and the mark go straight into what is near, acid eats ({@link Acid}), the purifying fire burns, the two splashes heal
	 * and ward the friends near it. Any oil but those lays a slick where it fell.
	 */
	public static void splash(ServerLevel level, Vec3 at, OilKind oil, int tier, @Nullable Entity owner, BlockPos ground) {
		double r = 2.0 + tier;
		AABB box = new AABB(at, at).inflate(r);
		switch (oil) {
			case POISON, HAZE, SMOKE -> {
				AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
				if (owner instanceof LivingEntity living) {
					cloud.setOwner(living);
				}
				cloud.setRadius((float) r * 0.8F);
				cloud.setDuration(20 * (4 + 2 * tier));
				cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
				cloud.setWaitTime(2);
				cloud.setCustomParticle(MagicFx.miasma(oil.color, 1.2F));
				int ticks = oil == OilKind.POISON ? Oils.POISON_TICKS[tier - 1] : oil == OilKind.HAZE ? Oils.HAZE_TICKS[tier - 1] : Oils.SMOKE_TICKS[tier - 1];
				switch (oil) {
					case POISON -> cloud.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, tier >= 3 ? 1 : 0));
					case HAZE -> {
						cloud.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, tier >= 2 ? 1 : 0));
						cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
					}
					default -> cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
				}
				level.addFreshEntity(cloud);
			}
			case MARK, TOXIN, FIRE -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
					if (e.position().distanceToSqr(at) <= r * r && e != owner) {
						Oils.bite(e, oil, tier, owner instanceof ServerPlayer p ? p : null);
					}
				}
				if (oil == OilKind.FIRE) {
					MagicFx.blast(level, at.x, at.y, at.z, (float) r * 0.7F, oil.color);
					level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 25, r * 0.3, 0.3, r * 0.3, 0.05);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
					flareSlicks(level, ground, (int) Math.ceil(r));
				}
			}
			case ACID -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
					if (e.position().distanceToSqr(at) <= r * r) {
						Oils.bite(e, oil, tier, null);
					}
				}
				Acid.splash(level, ground, tier, owner);
			}
			case FIRE_WARD, HEALING -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
					if (e.position().distanceToSqr(at) > r * r || e instanceof Enemy) {
						continue;
					}
					if (oil == OilKind.HEALING) {
						e.heal(4.0F * tier);
					} else {
						e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * tier, 0));
						e.clearFire();
					}
				}
				level.sendParticles(oil == OilKind.HEALING ? ParticleTypes.HEART : ParticleTypes.FLAME, at.x, at.y + 0.6, at.z, 10, r * 0.3, 0.4, r * 0.3, 0.02);
			}
			default -> {
			}
		}
		if (oil != OilKind.FIRE_WARD && oil != OilKind.HEALING && oil != OilKind.SMOKE && oil != OilKind.ACID) {
			OilSlickBlock.spill(level, ground, 1);
		}
	}

	/** The purifying fire lights every slick within {@code radius} of {@code ground}. */
	private static void flareSlicks(ServerLevel level, BlockPos ground, int radius) {
		for (BlockPos p : BlockPos.betweenClosed(ground.offset(-radius, -1, -radius), ground.offset(radius, 1, radius))) {
			if (level.getBlockState(p).is(OilSlickBlock.SLICK)) {
				OilSlickBlock.flare(level, p.immutable());
			}
		}
	}

	// ---- the passing of time ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!GROUNDED.isEmpty()) {
			long now = server.overworld().getGameTime();
			GROUNDED.entrySet().removeIf(en -> {
				Entity e = en.getKey();
				if (!e.isAlive() || now >= en.getValue()) {
					return true;
				}
				if (!e.onGround()) {
					e.setNoGravity(false);
					Vec3 v = e.getDeltaMovement();
					e.setDeltaMovement(v.x * 0.5, Math.min(v.y, -0.45), v.z * 0.5);
				}
				return false;
			});
		}
		if (GASES.isEmpty()) {
			return;
		}
		for (Gas gas : List.copyOf(GASES)) {
			long now = gas.level().getGameTime();
			if (now >= gas.until()) {
				GASES.remove(gas);
				continue;
			}
			if (now % 10 == 0) {
				gas.level().sendParticles(MagicFx.miasma(Kind.VOLATILE_GAS.color, 1.5F), gas.at().x, gas.at().y + 0.4, gas.at().z, 6,
					GAS_RADIUS * 0.35, 0.5, GAS_RADIUS * 0.35, 0.0);
			}
			if (now % 5 == 0 && flameIn(gas)) {
				GASES.remove(gas);
				Entity owner = gas.owner() == null ? null : gas.level().getEntity(gas.owner());
				gas.level().explode(owner, gas.at().x, gas.at().y, gas.at().z, GAS_BLAST, true, Level.ExplosionInteraction.TNT);
			}
		}
	}

	/** Whether flame touches the gas: fire, lava, a torch, a lit campfire, furnace or candle in it, or a burning creature or flaming arrow. */
	private static boolean flameIn(Gas gas) {
		ServerLevel level = gas.level();
		BlockPos centre = BlockPos.containing(gas.at());
		int r = (int) Math.ceil(GAS_RADIUS);
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (p.distSqr(centre) <= GAS_RADIUS * GAS_RADIUS && OilSlickBlock.isFlame(level.getBlockState(p))) {
				return true;
			}
		}
		for (Entity e : level.getEntities((Entity) null, new AABB(gas.at(), gas.at()).inflate(GAS_RADIUS), x -> x.isOnFire() || x instanceof net.minecraft.world.entity.LightningBolt)) {
			if (e.position().distanceToSqr(gas.at()) <= GAS_RADIUS * GAS_RADIUS) {
				return true;
			}
		}
		return false;
	}
}
