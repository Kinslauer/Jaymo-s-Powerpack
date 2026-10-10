package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Relic weapons: one-of-a-kind arms the pocket dimension let slip, each with its own gift, each turning up where it
 * belongs ({@link #THEMES}). They're relics like any other ({@link ArsenalRelics#RELIC}): an Arsenal user who picks one
 * up claims it into the Pocket Stash, and Reach brings it out first. Anyone can swing one.
 * <ul>
 * <li><b>Soul Saber</b> (the Nether): drinks {@link #LIFE_STEAL} of the harm it does; right click looses a slash of
 * soul fire down your sight line.</li>
 * <li><b>Grand Shield</b> (mansions, outposts, strongholds): raised, it sends projectiles back where they came from.</li>
 * <li><b>Voidreaver</b> (End cities): blinds what it cuts; right click steps you through the void to behind your target.</li>
 * <li><b>Echo Cleaver</b> (ancient cities): right click lets out a warden's sonic boom along your sight.</li>
 * <li><b>Tidebreaker</b> (shipwrecks, buried treasure, ocean ruins): cuts harder in water or rain; right click hurls you
 * forward in a spinning riptide.</li>
 * <li><b>Sunfang</b> (desert and jungle temples): sets what it cuts alight; right click blasts a cone of scouring sand.</li>
 * </ul>
 */
public final class RelicWeapons {
	public enum Kind { SOUL_SABER, GRAND_SHIELD, VOIDREAVER, ECHO_CLEAVER, TIDEBREAKER, SUNFANG }

	/** Soul Saber: share of the damage dealt that heals you; the slash's reach, width, damage, cooldown. */
	static final float LIFE_STEAL = 0.3F;
	static final double SLASH_RANGE = 14.0, SLASH_WIDTH = 1.3;
	static final float SLASH_DAMAGE = 6.0F;
	static final int SLASH_COOLDOWN = 40;
	/** Grand Shield: how close a projectile has to come while it's raised to be sent back, and how much faster. */
	static final double REFLECT_RANGE = 3.5, REFLECT_SPEEDUP = 1.2;
	/** Voidreaver: blindness on a cut; the blink's reach and cooldown. */
	static final int REAVER_BLIND_TICKS = 40;
	static final double BLINK_RANGE = 16.0;
	static final int BLINK_COOLDOWN = 80;
	/** Echo Cleaver: the boom's reach, width, damage, cooldown. */
	static final double BOOM_RANGE = 15.0, BOOM_WIDTH = 1.5;
	static final float BOOM_DAMAGE = 8.0F;
	static final int BOOM_COOLDOWN = 120;
	/** Tidebreaker: the extra damage in water or rain; the riptide's length, damage and cooldown. */
	static final float TIDE_BONUS = 3.0F;
	static final int RIPTIDE_TICKS = 20, RIPTIDE_COOLDOWN = 60;
	static final float RIPTIDE_DAMAGE = 6.0F;
	static final double RIPTIDE_SPEED = 1.6;
	/** Sunfang: seconds alight on a cut; the sand blast's reach, cone, damage, cooldown. */
	static final int SUN_FIRE_SECONDS = 4;
	static final double SAND_RANGE = 6.0, SAND_CONE = 0.6;
	static final float SAND_DAMAGE = 3.0F;
	static final int SAND_COOLDOWN = 80;

	static final Map<Kind, Item> ITEMS = new java.util.EnumMap<>(Kind.class);

	/** Where each turns up. Chests not listed here that hold relics give any of them. */
	static final Map<ResourceKey<LootTable>, Kind> THEMES = Map.ofEntries(
		Map.entry(BuiltInLootTables.BASTION_TREASURE, Kind.SOUL_SABER), Map.entry(BuiltInLootTables.BASTION_OTHER, Kind.SOUL_SABER),
		Map.entry(BuiltInLootTables.BASTION_BRIDGE, Kind.SOUL_SABER), Map.entry(BuiltInLootTables.BASTION_HOGLIN_STABLE, Kind.SOUL_SABER),
		Map.entry(BuiltInLootTables.NETHER_BRIDGE, Kind.SOUL_SABER), Map.entry(BuiltInLootTables.RUINED_PORTAL, Kind.SOUL_SABER),
		Map.entry(BuiltInLootTables.WOODLAND_MANSION, Kind.GRAND_SHIELD), Map.entry(BuiltInLootTables.PILLAGER_OUTPOST, Kind.GRAND_SHIELD),
		Map.entry(BuiltInLootTables.STRONGHOLD_CORRIDOR, Kind.GRAND_SHIELD),
		Map.entry(BuiltInLootTables.END_CITY_TREASURE, Kind.VOIDREAVER),
		Map.entry(BuiltInLootTables.ANCIENT_CITY, Kind.ECHO_CLEAVER),
		Map.entry(BuiltInLootTables.SHIPWRECK_TREASURE, Kind.TIDEBREAKER), Map.entry(BuiltInLootTables.BURIED_TREASURE, Kind.TIDEBREAKER),
		Map.entry(BuiltInLootTables.UNDERWATER_RUIN_BIG, Kind.TIDEBREAKER),
		Map.entry(BuiltInLootTables.DESERT_PYRAMID, Kind.SUNFANG), Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, Kind.SUNFANG));

	private RelicWeapons() {
	}

	static void register() {
		item(Kind.SOUL_SABER, "soul_saber", new Item.Properties().sword(ToolMaterial.NETHERITE, 3.5F, -2.2F).fireResistant());
		item(Kind.GRAND_SHIELD, "grand_shield", new Item.Properties()
			.durability(1200)
			.repairable(ItemTags.IRON_TOOL_MATERIALS)
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(0.15F, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(120.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK));
		item(Kind.VOIDREAVER, "voidreaver", new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F));
		item(Kind.ECHO_CLEAVER, "echo_cleaver", new Item.Properties().sword(ToolMaterial.NETHERITE, 6.0F, -3.0F));
		item(Kind.TIDEBREAKER, "tidebreaker", new Item.Properties().sword(ToolMaterial.DIAMOND, 4.0F, -2.4F));
		item(Kind.SUNFANG, "sunfang", new Item.Properties().sword(ToolMaterial.DIAMOND, 3.5F, -2.2F));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> ITEMS.values().forEach(output::accept));

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			Kind kind = kind(attacker.getMainHandItem());
			if (kind == null) {
				return;
			}
			switch (kind) {
				case SOUL_SABER -> {
					attacker.heal(damageTaken * LIFE_STEAL);
					level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
				}
				case VOIDREAVER -> entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, REAVER_BLIND_TICKS, 0), attacker);
				case TIDEBREAKER -> {
					if (attacker.isInWaterOrRain() && entity.isAlive()) {
						entity.setInvulnerableTime(0);
						entity.hurtServer(level, level.damageSources().magic(), TIDE_BONUS);
						level.sendParticles(ParticleTypes.SPLASH, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
					}
				}
				case SUNFANG -> entity.igniteForSeconds(SUN_FIRE_SECONDS);
				default -> {
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isBlocking() && kind(player.getUseItem()) == Kind.GRAND_SHIELD) {
					reflect(player);
				}
			}
		});
	}

	private static void item(Kind kind, String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		properties.setId(key).rarity(Rarity.EPIC).component(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.jaymospowers." + name + ".lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
			Component.translatable("item.jaymospowers." + name + ".lore2").withStyle(ChatFormatting.DARK_PURPLE))));
		Item item = kind == Kind.GRAND_SHIELD ? new Shield(properties) : new Relic(properties, kind);
		ITEMS.put(kind, Registry.register(BuiltInRegistries.ITEM, key, item));
	}

	/** Which relic weapon this is, if any. */
	static @Nullable Kind kind(ItemStack stack) {
		if (stack.getItem() instanceof Relic relic) {
			return relic.kind;
		}
		return stack.getItem() instanceof Shield ? Kind.GRAND_SHIELD : null;
	}

	/** A relic weapon for a chest of {@code table} (its own kind there, any kind elsewhere). */
	static ItemStack make(@Nullable ResourceKey<LootTable> table, RandomSource random) {
		Kind kind = table == null ? null : THEMES.get(table);
		if (kind == null) {
			kind = Kind.values()[random.nextInt(Kind.values().length)];
		}
		return new ItemStack(ITEMS.get(kind));
	}

	/** The Grand Shield raised: anything flying at you within reach turns back on whoever loosed it. */
	private static void reflect(ServerPlayer player) {
		ServerLevel level = player.level();
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(REFLECT_RANGE),
			p -> p.getOwner() != player && p.isAlive())) {
			Vec3 v = projectile.getDeltaMovement();
			Vec3 toPlayer = player.getBoundingBox().getCenter().subtract(projectile.position());
			if (v.lengthSqr() < 1.0E-4 || v.dot(toPlayer) <= 0.0) {
				continue;
			}
			Entity shooter = projectile.getOwner();
			Vec3 back = shooter != null ? shooter.getEyePosition().subtract(projectile.position()).normalize().scale(v.length() * REFLECT_SPEEDUP)
				: v.scale(-REFLECT_SPEEDUP);
			projectile.setDeltaMovement(back);
			projectile.setOwner(player);
			projectile.syncVelocity = true;
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
		}
	}

	/** Every living thing near the line from {@code from} along {@code dir} for {@code range} (not {@code self}). */
	private static List<LivingEntity> alongLine(ServerLevel level, LivingEntity self, Vec3 from, Vec3 dir, double range, double width) {
		Vec3 to = from.add(dir.scale(range));
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width), e -> {
			if (e == self || !e.isAlive() || e.isSpectator()) {
				return false;
			}
			Vec3 c = e.getBoundingBox().getCenter();
			double t = net.minecraft.util.Mth.clamp(c.subtract(from).dot(dir), 0.0, range);
			return c.distanceTo(from.add(dir.scale(t))) <= width + e.getBbWidth() * 0.5;
		});
	}

	/** A relic sword: its gift on right click. */
	public static final class Relic extends Item {
		final Kind kind;

		Relic(Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			int cooldown = switch (this.kind) {
				case SOUL_SABER -> slash(server, serverPlayer);
				case VOIDREAVER -> blink(server, serverPlayer);
				case ECHO_CLEAVER -> boom(server, serverPlayer);
				case TIDEBREAKER -> riptide(server, serverPlayer, stack);
				case SUNFANG -> sand(server, serverPlayer);
				default -> 0;
			};
			if (cooldown <= 0) {
				return InteractionResult.PASS;
			}
			player.getCooldowns().addCooldown(stack, cooldown);
			player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			return InteractionResult.SUCCESS;
		}

		private static int slash(ServerLevel level, ServerPlayer player) {
			Vec3 eye = player.getEyePosition();
			Vec3 dir = player.getLookAngle();
			for (LivingEntity target : alongLine(level, player, eye, dir, SLASH_RANGE, SLASH_WIDTH)) {
				target.hurtServer(level, level.damageSources().playerAttack(player), SLASH_DAMAGE);
				target.igniteForSeconds(2);
			}
			for (double d = 1.0; d <= SLASH_RANGE; d += 0.5) {
				Vec3 at = eye.add(dir.scale(d));
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 2, 0.25, 0.05, 0.25, 0.01);
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, eye.x + dir.x * 2.0, eye.y + dir.y * 2.0, eye.z + dir.z * 2.0, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
			return SLASH_COOLDOWN;
		}

		private static int blink(ServerLevel level, ServerPlayer player) {
			Entity target = com.jaymospowers.power.web.WebPower.entityInSight(player, BLINK_RANGE);
			if (!(target instanceof LivingEntity)) {
				return 0;
			}
			Vec3 facing = target.getLookAngle().horizontal();
			facing = facing.lengthSqr() < 1.0E-4 ? target.position().subtract(player.position()).horizontal().normalize() : facing.normalize();
			Vec3 behind = target.position().subtract(facing.scale(target.getBbWidth() * 0.5 + 1.0));
			if (!level.noCollision(player, player.getBoundingBox().move(behind.subtract(player.position())))) {
				behind = target.position().add(0.0, target.getBbHeight() + 0.1, 0.0);
			}
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
			float yaw = (float)(net.minecraft.util.Mth.atan2(facing.z, facing.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
			player.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), yaw, player.getXRot(), false);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, behind.x, behind.y + 1.0, behind.z, 30, 0.3, 0.6, 0.3, 0.1);
			level.playSound(null, behind.x, behind.y, behind.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
			return BLINK_COOLDOWN;
		}

		private static int boom(ServerLevel level, ServerPlayer player) {
			Vec3 eye = player.getEyePosition();
			Vec3 dir = player.getLookAngle();
			for (LivingEntity target : alongLine(level, player, eye, dir, BOOM_RANGE, BOOM_WIDTH)) {
				target.hurtServer(level, level.damageSources().sonicBoom(player), BOOM_DAMAGE);
				Vec3 push = dir.scale(1.2).add(0.0, 0.3, 0.0);
				target.push(push.x, push.y, push.z);
				target.syncVelocity = true;
			}
			for (double d = 1.5; d <= BOOM_RANGE; d += 1.0) {
				Vec3 at = eye.add(dir.scale(d));
				level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 1.0F);
			return BOOM_COOLDOWN;
		}

		private static int riptide(ServerLevel level, ServerPlayer player, ItemStack stack) {
			float strength = player.isInWaterOrRain() ? 1.0F : 0.7F;
			Vec3 v = player.getLookAngle().scale(RIPTIDE_SPEED * strength).add(0.0, 0.2, 0.0);
			player.startAutoSpinAttack(RIPTIDE_TICKS, RIPTIDE_DAMAGE * strength, stack);
			player.setDeltaMovement(v);
			player.syncVelocity = true;
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(v));
			level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.2);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.0F);
			return RIPTIDE_COOLDOWN;
		}

		private static int sand(ServerLevel level, ServerPlayer player) {
			Vec3 eye = player.getEyePosition();
			Vec3 dir = player.getLookAngle();
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SAND_RANGE),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
				Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
				if (to.length() > SAND_RANGE || to.normalize().dot(dir) < SAND_CONE) {
					continue;
				}
				target.hurtServer(level, level.damageSources().playerAttack(player), SAND_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), player);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), player);
			}
			net.minecraft.core.particles.BlockParticleOption sandDust = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
				net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
			for (double d = 1.0; d <= SAND_RANGE; d += 0.5) {
				Vec3 at = eye.add(dir.scale(d));
				level.sendParticles(sandDust, at.x, at.y, at.z, 6, d * 0.15, d * 0.1, d * 0.15, 0.0);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HUSK_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.6F);
			return SAND_COOLDOWN;
		}
	}

	/** The Grand Shield: a shield (its gift is in {@link #reflect}). */
	public static final class Shield extends ShieldItem {
		Shield(Properties properties) {
			super(properties);
		}
	}
}
