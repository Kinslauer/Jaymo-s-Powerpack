package com.jaymoskingdoms.tinker;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The gadgets a Tinkerer builds from what they have seen ({@link Mimic}): each copies one ability in brass, spring and whatever
 * the ability burns. Anyone holding one can work it (Andy, 2026-11-15); only a Tinkerer can study and build them. A gadget that
 * shoots or throws spends what it shoots ({@link Mimic#fuel}): nothing comes out of nothing.
 *
 * <ul>
 * <li><b>Lift rig</b> (Gravity, shulkers): held on a creature in reach, it floats where you point; let go and it falls. Redstone a
 * couple of seconds.</li>
 * <li><b>Gust bellows</b> (Wind, breezes): a cone of wind that throws creatures back; aimed at your feet it throws you up and
 * breaks your fall. A pinch of gunpowder.</li>
 * <li><b>Grapple spinner</b> (Web): a line to the block you look at pulls you to it. Two string, left on the wall.</li>
 * <li><b>Rewind watch</b> (Chrono): back to where you stood three seconds ago, your health then if it was more, the fire out. Gold
 * nuggets for the spring.</li>
 * <li><b>Frost projector</b> (Cryo): held, a cone of cold that slows and freezes, and turns water to ice that melts. A snowball a
 * second.</li>
 * <li><b>Smother pump</b> (Smoke): a creature in reach is wrapped in smoke: blind, slowed, and choking if it breathes. Charcoal or
 * coal.</li>
 * <li><b>Sonic horn</b> (Resonance, wardens): a cone of sound that hurts, knocks back, breaks off what they were doing and shatters
 * panes. An amethyst shard.</li>
 * <li><b>Parry gauntlet</b> (Darktide): held up, a blow met in its first moment is turned aside and its striker staggered. It wears.</li>
 * <li><b>Chain hook</b> (Arsenal): a creature in reach is hooked and yanked to you. An iron nugget for the hook.</li>
 * <li><b>Growth band</b> (Xenoform): half a minute bigger, tougher, stronger and longer-armed, then worn out. Glowstone dust.</li>
 * <li><b>Ki blaster</b> (the Z Fighter): a bolt of stored force at what you aim at. Redstone.</li>
 * <li><b>Blink coil</b> (endermen): you are where you looked, up to twelve blocks off. A chorus fruit.</li>
 * <li><b>Climbing claws</b> (spiders): held, any wall you push into is climbed. They wear.</li>
 * <li><b>Flame spitter</b> (blazes): three small fireballs. Blaze powder.</li>
 * <li><b>Bellows cannon</b> (ghasts): a bursting fireball. A fire charge.</li>
 * <li><b>Beam lens</b> (guardians): held on a creature for two seconds, a beam burns it. Prismarine crystals.</li>
 * <li><b>Jaw rake</b> (evokers): a line of snapping jaws out of the ground. Bones.</li>
 * <li><b>Hissing charge</b> (creepers): thrown, it hisses and bursts. The charge itself is spent.</li>
 * <li><b>Potion sling</b> (witches): throws the splash or lingering potion in your other hand twice as far. The potion is spent.</li>
 * </ul>
 */
public final class MimicGadgets {
	// ---- tuning ----
	static final double LIFT_RANGE = 12.0, LIFT_PULL = 0.35, LIFT_MAX_SPEED = 1.2;
	static final int LIFT_FUEL_EVERY = 40;
	static final double GUST_RANGE = 7.0, GUST_COS = 0.64, GUST_PUSH = 1.4, GUST_LIFT = 0.35, GUST_SELF = 1.15;
	static final float GUST_SELF_PITCH = 50.0F;
	static final double ZIP_RANGE = 24.0, ZIP_SPEED = 2.2;
	static final int REWIND_BACK = 60, REWIND_EVERY = 5;
	static final double FROST_RANGE = 5.0, FROST_COS = 0.8;
	static final int FROST_EVERY = 4, FROST_FUEL_EVERY = 20, FROST_TICKS = 14;
	static final double SMOTHER_RANGE = 10.0;
	static final int SMOTHER_TICKS = 100;
	static final double SONIC_RANGE = 8.0, SONIC_COS = 0.82, SONIC_PUSH = 1.2;
	static final float SONIC_DAMAGE = 4.0F;
	static final int PARRY_TICKS = 6, PARRY_STAGGER = 30, GUARD_PAUSE = 10;
	static final double CHAIN_RANGE = 16.0, CHAIN_SPEED = 1.8;
	static final int GROWTH_TICKS = 20 * 30, GROWTH_TIRED = 200;
	static final double GROWTH_SCALE = 0.4, GROWTH_HEALTH = 6.0, GROWTH_DAMAGE = 2.0, GROWTH_REACH = 1.0;
	static final double KI_RANGE = 24.0;
	static final float KI_DAMAGE = 6.0F;
	static final double BLINK_RANGE = 12.0;
	static final double BEAM_RANGE = 16.0;
	static final int BEAM_CHARGE = 40;
	static final float BEAM_DAMAGE = 6.0F;
	static final int FANGS = 8;
	static final int FUSE = 30;
	static final float BLAST_POWER = 2.2F;
	static final float SLING_SPEED = 1.0F;
	/** A creature this heavy is not lifted or hooked (most health), nor any boss. */
	static final float TOO_HEAVY = 100.0F;
	/** How long a fall is forgiven after the bellows or the grapple throw you (ticks). */
	static final int SAFE_FALL = 120;

	public static final Map<Mimic, Item> ITEMS = new EnumMap<>(Mimic.class);

	private static final Map<UUID, Integer> LIFTING = new HashMap<>();
	private static final Map<UUID, Integer> BEAMING = new HashMap<>();
	private static final Map<UUID, Long> SAFE = new HashMap<>();
	private static final Map<UUID, ArrayDeque<Snap>> HISTORY = new HashMap<>();
	private static final Map<UUID, Long> GROWN = new HashMap<>();
	private static final List<Smother> SMOTHERED = new ArrayList<>();
	private static final List<Fused> FUSED = new ArrayList<>();

	private static final Identifier GROW_SCALE = JaymosKingdoms.id("growth_band_scale"), GROW_HEALTH = JaymosKingdoms.id("growth_band_health"),
		GROW_DAMAGE = JaymosKingdoms.id("growth_band_damage"), GROW_REACH = JaymosKingdoms.id("growth_band_reach");

	private record Snap(ServerLevel level, Vec3 at, float yRot, float xRot, float health) {
	}

	private record Smother(LivingEntity target, long until) {
	}

	private record Fused(ItemEntity charge, long at, @Nullable UUID owner) {
	}

	private MimicGadgets() {
	}

	public static void register() {
		for (Mimic m : Mimic.values()) {
			ITEMS.put(m, item(m));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (Item item : ITEMS.values()) {
				output.accept(item);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(MimicGadgets::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MimicGadgets::allowDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				forget(player);
			}
			SMOTHERED.clear();
			FUSED.clear();
		});
	}

	private static Item item(Mimic m) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(m.gadget));
		Item.Properties p = new Item.Properties().setId(key).rarity(Rarity.UNCOMMON);
		p = m.durability > 0 ? p.durability(m.durability) : m == Mimic.BLAST ? p.stacksTo(8) : p.stacksTo(1);
		Item item = switch (m) {
			case LIFT, FROST, BEAM -> new Held(p, m);
			case PARRY -> new Gauntlet(p, m);
			case CLIMB -> new Gadget(p, m);
			default -> new Tap(p, m);
		};
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Whether {@code stack} is the gadget that copies {@code m}. */
	public static boolean is(ItemStack stack, Mimic m) {
		Item item = ITEMS.get(m);
		return item != null && stack.is(item);
	}

	private static void forget(ServerPlayer player) {
		LIFTING.remove(player.getUUID());
		BEAMING.remove(player.getUUID());
		SAFE.remove(player.getUUID());
		HISTORY.remove(player.getUUID());
		if (GROWN.remove(player.getUUID()) != null) {
			shrink(player, false);
		}
	}

	// ---- the items ----------------------------------------------------------------------------------------------------------

	/** Every gadget: its tooltip says what it copies, what it does and what it spends. */
	static class Gadget extends Item {
		final Mimic mimic;

		Gadget(Properties properties, Mimic mimic) {
			super(properties);
			this.mimic = mimic;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.mimic." + this.mimic.key()).withStyle(ChatFormatting.GRAY));
			if (this.mimic.fuel != null) {
				builder.accept(Component.translatable("tooltip.jaymoskingdoms.mimic.spends", this.mimic.fuel.count(), fuelName(this.mimic.fuel))
					.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	/** A gadget that does its work at once when used. */
	static final class Tap extends Gadget {
		Tap(Properties properties, Mimic mimic) {
			super(properties, mimic);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (this.mimic.fuel != null && !hasFuel(sp, this.mimic.fuel)) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.empty", this.mimic.fuel.count(), fuelName(this.mimic.fuel)));
				return InteractionResult.FAIL;
			}
			boolean done = switch (this.mimic) {
				case GUST -> gust(sp, server);
				case ZIP -> zip(sp, server);
				case REWIND -> rewind(sp, server);
				case SMOTHER -> smother(sp, server);
				case SONIC -> sonic(sp, server);
				case CHAIN -> chain(sp, server);
				case GROWTH -> growth(sp, server);
				case KI -> ki(sp, server);
				case BLINK -> blink(sp, server);
				case FIREBURST -> fireburst(sp, server);
				case FIREBALL -> fireball(sp, server);
				case FANGS -> fangs(sp, server);
				case BLAST -> blast(sp, server, stack);
				case POTION -> sling(sp, server, hand);
				default -> false;
			};
			if (!done) {
				return InteractionResult.FAIL;
			}
			if (this.mimic.fuel != null) {
				spend(sp, this.mimic.fuel);
			}
			if (this.mimic.cooldown > 0 && this.mimic != Mimic.BLAST) {
				sp.getCooldowns().addCooldown(stack, this.mimic.cooldown);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** A gadget held to work: the lift rig, the frost projector, the beam lens. */
	static final class Held extends Gadget {
		Held(Properties properties, Mimic mimic) {
			super(properties, mimic);
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return this.mimic == Mimic.BEAM ? ItemUseAnimation.SPYGLASS : ItemUseAnimation.BOW;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				if (this.mimic.fuel != null && !hasFuel(sp, this.mimic.fuel)) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.empty", this.mimic.fuel.count(), fuelName(this.mimic.fuel)));
					return InteractionResult.FAIL;
				}
				if (this.mimic == Mimic.LIFT || this.mimic == Mimic.BEAM) {
					LivingEntity target = aimEntity(sp, this.mimic == Mimic.LIFT ? LIFT_RANGE : BEAM_RANGE);
					if (target == null) {
						return InteractionResult.FAIL;
					}
					if (this.mimic == Mimic.LIFT && heavy(target)) {
						sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.heavy"));
						return InteractionResult.FAIL;
					}
					(this.mimic == Mimic.LIFT ? LIFTING : BEAMING).put(sp.getUUID(), target.getId());
					if (this.mimic == Mimic.LIFT) {
						spend(sp, this.mimic.fuel);
						server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
					} else {
						server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.8F, 1.3F);
					}
				} else {
					spend(sp, this.mimic.fuel);
				}
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (!(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) {
				return;
			}
			int held = getUseDuration(stack, user) - remaining;
			boolean keep = switch (this.mimic) {
				case LIFT -> liftTick(player, server, held);
				case FROST -> frostTick(player, server, held);
				case BEAM -> beamTick(player, server, held, stack);
				default -> false;
			};
			if (!keep) {
				player.stopUsingItem();
				LIFTING.remove(player.getUUID());
				BEAMING.remove(player.getUUID());
			}
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
			LIFTING.remove(user.getUUID());
			BEAMING.remove(user.getUUID());
			return true;
		}
	}

	/** The parry gauntlet, held up like a shield ({@link #allowDamage} turns the blow). */
	static final class Gauntlet extends Gadget {
		Gauntlet(Properties properties, Mimic mimic) {
			super(properties, mimic);
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BLOCK;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
			// (no fluttering the guard: a parry is a moment, not a wall)
			if (user instanceof Player player) {
				player.getCooldowns().addCooldown(stack, GUARD_PAUSE);
			}
			return true;
		}
	}

	// ---- the work -----------------------------------------------------------------------------------------------------------

	private static boolean liftTick(ServerPlayer player, ServerLevel level, int held) {
		Integer id = LIFTING.get(player.getUUID());
		Entity e = id == null ? null : level.getEntity(id);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > LIFT_RANGE + 4.0) {
			return false;
		}
		if (held > 0 && held % LIFT_FUEL_EVERY == 0) {
			if (!hasFuel(player, Mimic.LIFT.fuel)) {
				return false;
			}
			spend(player, Mimic.LIFT.fuel);
		}
		double hold = Mth.clamp(player.distanceTo(target), 2.5, 8.0);
		Vec3 want = player.getEyePosition().add(player.getViewVector(1.0F).scale(hold));
		Vec3 now = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		Vec3 v = want.subtract(now).scale(LIFT_PULL);
		if (v.length() > LIFT_MAX_SPEED) {
			v = v.normalize().scale(LIFT_MAX_SPEED);
		}
		target.setDeltaMovement(v);
		target.resetFallDistance();
		target.syncVelocity = true;
		if (held % 4 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(), target.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
		}
		return true;
	}

	private static boolean frostTick(ServerPlayer player, ServerLevel level, int held) {
		if (held > 0 && held % FROST_FUEL_EVERY == 0) {
			if (!hasFuel(player, Mimic.FROST.fuel)) {
				return false;
			}
			spend(player, Mimic.FROST.fuel);
		}
		if (held % FROST_EVERY != 0) {
			return true;
		}
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		for (int i = 1; i <= 5; i++) {
			Vec3 p = eye.add(look.scale(i));
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 3, 0.15 * i, 0.15 * i, 0.15 * i, 0.02);
		}
		for (LivingEntity e : cone(player, level, FROST_RANGE, FROST_COS)) {
			e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + FROST_TICKS));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
		}
		// water under the cone turns to ice that melts again; fire goes out
		for (int i = 1; i <= (int)FROST_RANGE; i++) {
			BlockPos at = BlockPos.containing(eye.add(look.scale(i)));
			for (BlockPos pos : List.of(at, at.below())) {
				BlockState state = level.getBlockState(pos);
				if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
					level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
				} else if (state.is(Blocks.FIRE)) {
					level.removeBlock(pos, false);
				}
			}
		}
		if (held % 12 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
		return true;
	}

	private static boolean beamTick(ServerPlayer player, ServerLevel level, int held, ItemStack stack) {
		Integer id = BEAMING.get(player.getUUID());
		Entity e = id == null ? null : level.getEntity(id);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > BEAM_RANGE || !player.hasLineOfSight(target)) {
			return false;
		}
		Vec3 from = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6)).add(0.0, -0.2, 0.0);
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		if (held % 4 == 0) {
			float share = Math.min(1.0F, held / (float)BEAM_CHARGE);
			Fx.beam(level, from, to, 0.04F + 0.08F * share, share < 1.0F ? 0x7FE0D0 : 0xFFF4C0, 4);
		}
		if (held < BEAM_CHARGE) {
			return true;
		}
		if (!hasFuel(player, Mimic.BEAM.fuel)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.empty", Mimic.BEAM.fuel.count(), fuelName(Mimic.BEAM.fuel)));
			return false;
		}
		spend(player, Mimic.BEAM.fuel);
		target.hurtServer(level, level.damageSources().indirectMagic(player, player), BEAM_DAMAGE);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.getCooldowns().addCooldown(stack, Mimic.BEAM.cooldown);
		return false;
	}

	private static boolean gust(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getViewVector(1.0F);
		if (player.getXRot() >= GUST_SELF_PITCH) {
			// aimed at your feet: the wind throws you up, and the fall it gives you is forgiven
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, GUST_SELF, v.z);
			player.syncVelocity = true;
			safeFall(player, level);
			level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY(), player.getZ(), 2, 0.3, 0.0, 0.3, 0.0);
		} else {
			for (LivingEntity e : cone(player, level, GUST_RANGE, GUST_COS)) {
				Vec3 away = e.position().subtract(player.position()).normalize();
				e.push(away.x * GUST_PUSH, GUST_LIFT, away.z * GUST_PUSH);
				e.syncVelocity = true;
			}
			for (int i = 1; i <= 6; i++) {
				Vec3 p = player.getEyePosition().add(look.scale(i));
				level.sendParticles(ParticleTypes.GUST, p.x, p.y, p.z, 1, 0.2 * i, 0.2 * i, 0.2 * i, 0.0);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean zip(ServerPlayer player, ServerLevel level) {
		BlockHitResult hit = aimBlock(player, ZIP_RANGE);
		if (hit == null) {
			return false;
		}
		Vec3 to = hit.getLocation().subtract(player.getEyePosition());
		Vec3 v = to.normalize().scale(Math.min(ZIP_SPEED, to.length() * 0.22)).add(0.0, 0.35, 0.0);
		player.setDeltaMovement(v);
		player.syncVelocity = true;
		safeFall(player, level);
		line(level, player.getEyePosition().add(0.0, -0.3, 0.0), hit.getLocation(), ParticleTypes.WHITE_ASH, 0.5);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	private static boolean rewind(ServerPlayer player, ServerLevel level) {
		ArrayDeque<Snap> history = HISTORY.get(player.getUUID());
		Snap back = history == null ? null : history.peekFirst();
		if (back == null || back.level != level) {
			return false;
		}
		level.sendParticles(ParticleTypes.WAX_OFF, player.getX(), player.getY(1.0), player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
		player.teleportTo(level, back.at.x, back.at.y, back.at.z, java.util.Set.of(), back.yRot, back.xRot, true);
		player.resetFallDistance();
		if (back.health > player.getHealth()) {
			player.setHealth(Math.min(player.getMaxHealth(), back.health));
		}
		player.clearFire();
		history.clear();
		level.sendParticles(ParticleTypes.WAX_OFF, back.at.x, back.at.y + 1.0, back.at.z, 20, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, back.at.x, back.at.y, back.at.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.6F);
		return true;
	}

	private static boolean smother(ServerPlayer player, ServerLevel level) {
		LivingEntity target = aimEntity(player, SMOTHER_RANGE);
		if (target == null) {
			return false;
		}
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SMOTHER_TICKS, 0), player);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SMOTHER_TICKS, 1), player);
		if (target instanceof Mob mob) {
			mob.setTarget(null);
		}
		SMOTHERED.add(new Smother(target, level.getGameTime() + SMOTHER_TICKS));
		line(level, player.getEyePosition(), target.position().add(0.0, target.getBbHeight() / 2.0, 0.0), ParticleTypes.LARGE_SMOKE, 0.6);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	private static boolean sonic(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		for (LivingEntity e : cone(player, level, SONIC_RANGE, SONIC_COS)) {
			e.hurtServer(level, level.damageSources().sonicBoom(player), SONIC_DAMAGE);
			Vec3 away = e.position().subtract(player.position()).normalize();
			e.push(away.x * SONIC_PUSH, 0.2, away.z * SONIC_PUSH);
			e.syncVelocity = true;
			// it breaks off whatever it was doing
			if (e instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			e.stopUsingItem();
		}
		for (double d = 1.5; d <= SONIC_RANGE; d += 1.5) {
			Vec3 p = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		// it shatters panes
		Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize(), up = side.cross(look).normalize();
		for (Vec3 ray : List.of(look, look.add(side.scale(0.25)), look.subtract(side.scale(0.25)), look.add(up.scale(0.25)), look.subtract(up.scale(0.25)))) {
			Vec3 r = ray.normalize();
			for (double d = 0.5; d <= SONIC_RANGE; d += 0.5) {
				BlockPos pos = BlockPos.containing(eye.add(r.scale(d)));
				BlockState state = level.getBlockState(pos);
				if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("glass_pane")) {
					level.destroyBlock(pos, false, player);
				} else if (!state.getCollisionShape(level, pos).isEmpty()) {
					break;
				}
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}

	private static boolean chain(ServerPlayer player, ServerLevel level) {
		LivingEntity target = aimEntity(player, CHAIN_RANGE);
		if (target == null) {
			return false;
		}
		if (heavy(target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.heavy"));
			return false;
		}
		Vec3 to = player.position().subtract(target.position());
		Vec3 v = to.normalize().scale(Math.min(CHAIN_SPEED, to.length() * 0.2)).add(0.0, 0.3, 0.0);
		target.setDeltaMovement(v);
		target.syncVelocity = true;
		line(level, player.getEyePosition().add(0.0, -0.3, 0.0), target.position().add(0.0, target.getBbHeight() / 2.0, 0.0), ParticleTypes.CRIT, 0.4);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static boolean growth(ServerPlayer player, ServerLevel level) {
		if (GROWN.containsKey(player.getUUID())) {
			// worked again: the band lets go early
			GROWN.remove(player.getUUID());
			shrink(player, true);
			return false;
		}
		modifier(player, Attributes.SCALE, GROW_SCALE, GROWTH_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(player, Attributes.MAX_HEALTH, GROW_HEALTH, GROWTH_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.ATTACK_DAMAGE, GROW_DAMAGE, GROWTH_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.ENTITY_INTERACTION_RANGE, GROW_REACH, GROWTH_REACH, AttributeModifier.Operation.ADD_VALUE);
		player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float)GROWTH_HEALTH));
		GROWN.put(player.getUUID(), level.getGameTime() + GROWTH_TICKS);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY(1.0), player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.5F);
		return true;
	}

	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
		double amount, AttributeModifier.Operation op) {
		AttributeInstance a = player.getAttribute(attribute);
		if (a != null) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** The band lets go ({@code tired}: worn out after, as the alien's body leaves you). */
	private static void shrink(ServerPlayer player, boolean tired) {
		for (var pair : List.of(java.util.Map.entry(Attributes.SCALE, GROW_SCALE), java.util.Map.entry(Attributes.MAX_HEALTH, GROW_HEALTH),
			java.util.Map.entry(Attributes.ATTACK_DAMAGE, GROW_DAMAGE), java.util.Map.entry(Attributes.ENTITY_INTERACTION_RANGE, GROW_REACH))) {
			AttributeInstance a = player.getAttribute(pair.getKey());
			if (a != null) {
				a.removeModifier(pair.getValue());
			}
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		if (tired) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GROWTH_TIRED, 1));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, GROWTH_TIRED, 0));
			if (player.level() instanceof ServerLevel level) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			for (ItemStack s : List.of(player.getMainHandItem(), player.getOffhandItem())) {
				if (is(s, Mimic.GROWTH)) {
					player.getCooldowns().addCooldown(s, Mimic.GROWTH.cooldown);
				}
			}
		}
	}

	private static boolean ki(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		BlockHitResult block = aimBlock(player, KI_RANGE);
		Vec3 end = block != null ? block.getLocation() : eye.add(look.scale(KI_RANGE));
		LivingEntity target = aimEntity(player, KI_RANGE);
		if (target != null) {
			end = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), KI_DAMAGE);
			target.push(look.x * 0.6, 0.15, look.z * 0.6);
			target.syncVelocity = true;
		}
		Fx.beam(level, eye.add(look.scale(0.6)).add(0.0, -0.2, 0.0), end, 0.12F, 0xFFE070, 6);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.2, 0.2, 0.2, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.6F);
		return true;
	}

	private static boolean blink(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		BlockHitResult hit = aimBlock(player, BLINK_RANGE);
		Vec3 spot = null;
		if (hit != null) {
			BlockPos base = hit.getBlockPos().relative(hit.getDirection());
			for (int up = 0; up <= 2 && spot == null; up++) {
				spot = fits(player, level, Vec3.atBottomCenterOf(base.above(up)));
			}
		} else {
			Vec3 air = eye.add(look.scale(BLINK_RANGE));
			spot = fits(player, level, new Vec3(air.x, air.y - player.getEyeHeight(), air.z));
		}
		if (spot == null) {
			return false;
		}
		level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(1.0), player.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
		player.teleportTo(spot.x, spot.y, spot.z);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1.0, spot.z, 30, 0.3, 0.6, 0.3, 0.2);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	private static @Nullable Vec3 fits(ServerPlayer player, ServerLevel level, Vec3 at) {
		AABB box = player.getBoundingBox().move(at.subtract(player.position()));
		return level.noCollision(player, box) ? at : null;
	}

	private static boolean fireburst(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		for (int i = 0; i < 3; i++) {
			Vec3 dir = look.add(player.getRandom().triangle(0.0, 0.12), player.getRandom().triangle(0.0, 0.08), player.getRandom().triangle(0.0, 0.12)).normalize();
			SmallFireball ball = new SmallFireball(level, player, dir);
			ball.setPos(eye.x + look.x, eye.y + look.y - 0.1, eye.z + look.z);
			level.addFreshEntity(ball);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean fireball(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		LargeFireball ball = new LargeFireball(level, player, look, 1);
		ball.setPos(eye.x + look.x * 1.5, eye.y + look.y * 1.5 - 0.3, eye.z + look.z * 1.5);
		level.addFreshEntity(ball);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean fangs(ServerPlayer player, ServerLevel level) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double dx = -Mth.sin(yaw), dz = Mth.cos(yaw);
		float facing = (float)Mth.atan2(dz, dx);
		boolean any = false;
		for (int i = 0; i < FANGS; i++) {
			double d = 1.25 * (i + 1);
			double x = player.getX() + dx * d, z = player.getZ() + dz * d;
			Double y = ground(level, x, player.getY(), z);
			if (y != null) {
				level.addFreshEntity(new EvokerFangs(level, x, y, z, facing, i, player));
				any = true;
			}
		}
		if (any) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
		return any;
	}

	/** The top of the ground at (x, z) within two blocks of {@code y}, or null. */
	private static @Nullable Double ground(ServerLevel level, double x, double y, double z) {
		BlockPos start = BlockPos.containing(x, y + 1.0, z);
		for (int i = 0; i <= 4; i++) {
			BlockPos pos = start.below(i);
			BlockPos under = pos.below();
			if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(under).isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) {
				return (double)pos.getY();
			}
		}
		return null;
	}

	private static boolean blast(ServerPlayer player, ServerLevel level, ItemStack stack) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		ItemEntity charge = new ItemEntity(level, eye.x + look.x * 0.5, eye.y - 0.2, eye.z + look.z * 0.5, stack.copyWithCount(1));
		charge.setDeltaMovement(look.scale(0.8).add(0.0, 0.15, 0.0));
		charge.setNeverPickUp();
		charge.setUnlimitedLifetime();
		level.addFreshEntity(charge);
		FUSED.add(new Fused(charge, level.getGameTime() + FUSE, player.getUUID()));
		// the charge itself is what is thrown (the pause first, while the stack is still there to carry it)
		player.getCooldowns().addCooldown(stack, Mimic.BLAST.cooldown);
		stack.consume(1, player);
		level.playSound(null, charge.getX(), charge.getY(), charge.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean sling(ServerPlayer player, ServerLevel level, InteractionHand hand) {
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack potion = player.getItemInHand(other);
		Projectile thrown;
		if (potion.is(Items.SPLASH_POTION)) {
			thrown = Projectile.spawnProjectileFromRotation(ThrownSplashPotion::new, level, potion, player, -10.0F, SLING_SPEED, 1.0F);
		} else if (potion.is(Items.LINGERING_POTION)) {
			thrown = Projectile.spawnProjectileFromRotation(ThrownLingeringPotion::new, level, potion, player, -10.0F, SLING_SPEED, 1.0F);
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.no_potion"));
			return false;
		}
		potion.consume(1, player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
		return thrown != null;
	}

	// ---- the guard, the falls ---------------------------------------------------------------------------------------------

	private static boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			Long until = SAFE.remove(player.getUUID());
			return until == null || until < player.level().getGameTime();
		}
		if (!player.isUsingItem() || !is(player.getUseItem(), Mimic.PARRY) || player.getTicksUsingItem() > PARRY_TICKS
			|| source.getEntity() == null && source.getDirectEntity() == null || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			return true;
		}
		Vec3 from = source.getSourcePosition();
		if (from != null) {
			Vec3 to = from.subtract(player.position()).multiply(1.0, 0.0, 1.0);
			Vec3 look = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4 && look.lengthSqr() > 1.0E-4 && to.normalize().dot(look.normalize()) < 0.2) {
				// a blow from behind is not met
				return true;
			}
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return true;
		}
		// turned aside: the striker reels
		if (source.getEntity() instanceof LivingEntity striker && striker != player && striker.distanceTo(player) < 5.0) {
			Vec3 back = striker.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
			striker.push(back.x * 0.8, 0.2, back.z * 0.8);
			striker.syncVelocity = true;
			striker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PARRY_STAGGER, 2), player);
			striker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PARRY_STAGGER, 1), player);
		}
		ItemStack gauntlet = player.getUseItem();
		InteractionHand hand = player.getUsedItemHand();
		player.stopUsingItem();
		gauntlet.hurtAndBreak(1, player, hand);
		player.getCooldowns().addCooldown(gauntlet, GUARD_PAUSE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(1.0), player.getZ(), 12, 0.4, 0.4, 0.4, 0.3);
		return false;
	}

	private static void safeFall(ServerPlayer player, ServerLevel level) {
		player.resetFallDistance();
		SAFE.put(player.getUUID(), level.getGameTime() + SAFE_FALL);
	}

	// ---- every tick ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			// the rewind watch keeps three seconds of where its carrier has been
			if ((now + player.getId()) % REWIND_EVERY == 0) {
				if (carries(player, Mimic.REWIND) && player.isAlive()) {
					ArrayDeque<Snap> history = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
					history.addLast(new Snap(level, player.position(), player.getYRot(), player.getXRot(), player.getHealth()));
					while (history.size() > REWIND_BACK / REWIND_EVERY) {
						history.removeFirst();
					}
				} else {
					HISTORY.remove(player.getUUID());
				}
			}
			// the claws wear while they climb
			if (now % 20 == 0 && player.onClimbable() && !player.onGround() && player.horizontalCollision && !player.isCreative()) {
				for (InteractionHand hand : InteractionHand.values()) {
					if (is(player.getItemInHand(hand), Mimic.CLIMB)) {
						player.getItemInHand(hand).hurtAndBreak(1, player, hand);
						break;
					}
				}
			}
			Long grown = GROWN.get(player.getUUID());
			if (grown != null && (now >= grown || !player.isAlive())) {
				GROWN.remove(player.getUUID());
				shrink(player, player.isAlive());
			}
			if (LIFTING.containsKey(player.getUUID()) && !(player.isUsingItem() && is(player.getUseItem(), Mimic.LIFT))) {
				LIFTING.remove(player.getUUID());
			}
			if (BEAMING.containsKey(player.getUUID()) && !(player.isUsingItem() && is(player.getUseItem(), Mimic.BEAM))) {
				BEAMING.remove(player.getUUID());
			}
			Long safe = SAFE.get(player.getUUID());
			if (safe != null && now > safe) {
				SAFE.remove(player.getUUID());
			}
		}
		for (Iterator<Smother> it = SMOTHERED.iterator(); it.hasNext(); ) {
			Smother s = it.next();
			LivingEntity t = s.target;
			if (!t.isAlive() || !(t.level() instanceof ServerLevel level) || level.getGameTime() >= s.until) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now % 5 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, t.getX(), t.getY(0.6), t.getZ(), 4, t.getBbWidth() * 0.4, t.getBbHeight() * 0.3, t.getBbWidth() * 0.4, 0.01);
			}
			// a breathing thing chokes in it
			if (now % 20 == 0 && !t.is(EntityTypeTags.UNDEAD) && !t.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER)) {
				t.hurtServer(level, level.damageSources().drown(), 1.0F);
			}
		}
		for (Iterator<Fused> it = FUSED.iterator(); it.hasNext(); ) {
			Fused f = it.next();
			ItemEntity c = f.charge;
			if (c.isRemoved() || !(c.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (level.getGameTime() % 4 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, c.getX(), c.getY() + 0.3, c.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
			}
			if (level.getGameTime() >= f.at) {
				it.remove();
				Entity owner = f.owner == null ? null : level.getEntity(f.owner);
				c.discard();
				level.explode(owner, c.getX(), c.getY() + 0.2, c.getZ(), BLAST_POWER, Level.ExplosionInteraction.MOB);
			}
		}
	}

	// ---- helpers ------------------------------------------------------------------------------------------------------------

	/** Whether {@code player} carries the gadget of {@code m} anywhere on them. */
	static boolean carries(Player player, Mimic m) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (is(inv.getItem(i), m)) {
				return true;
			}
		}
		return false;
	}

	/** The living thing under {@code player}'s aim within {@code range}, not behind a wall, or null. */
	static @Nullable LivingEntity aimEntity(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		HitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e.isPickable() && e != player, eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
	}

	/** The block under {@code player}'s aim within {@code range}, or null. */
	static @Nullable BlockHitResult aimBlock(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(range)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	/** The living things in a cone before {@code player} ({@code cos}: the cosine of its half-angle), in plain sight. */
	static List<LivingEntity> cone(ServerPlayer player, ServerLevel level, double range, double cos) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), x -> x != player && x.isAlive() && !x.isSpectator())) {
			Vec3 to = e.position().add(0.0, e.getBbHeight() / 2.0, 0.0).subtract(eye);
			if (to.length() <= range && to.normalize().dot(look) >= cos && player.hasLineOfSight(e)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Too heavy to lift or hook: a boss, or a creature of {@link #TOO_HEAVY} health or more. */
	static boolean heavy(LivingEntity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || BossBars.boss(e) || e.getMaxHealth() >= TOO_HEAVY;
	}

	private static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double step) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		Vec3 u = d.normalize();
		for (double t = 0.0; t <= len; t += step) {
			Vec3 p = from.add(u.scale(t));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	static Component fuelName(Mimic.Fuel fuel) {
		Component out = null;
		for (Item item : fuel.items()) {
			Component name = new ItemStack(item).getHoverName();
			out = out == null ? name : Component.translatable("tooltip.jaymoskingdoms.mimic.or", out, name);
		}
		return out == null ? Component.empty() : out;
	}

	static boolean hasFuel(Player player, Mimic.@Nullable Fuel fuel) {
		if (fuel == null || player.hasInfiniteMaterials()) {
			return true;
		}
		return count(player, fuel.items()) >= fuel.count();
	}

	/** Takes one use's {@code fuel} from {@code player} (nothing in creative). */
	static void spend(Player player, Mimic.@Nullable Fuel fuel) {
		if (fuel == null || player.hasInfiniteMaterials()) {
			return;
		}
		take(player, fuel.items(), fuel.count());
	}

	static int count(Player player, List<Item> items) {
		int n = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (items.contains(s.getItem())) {
				n += s.getCount();
			}
		}
		return n;
	}

	static void take(Player player, List<Item> items, int n) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (items.contains(s.getItem())) {
				int d = Math.min(n, s.getCount());
				s.shrink(d);
				n -= d;
			}
		}
		inv.setChanged();
	}
}
