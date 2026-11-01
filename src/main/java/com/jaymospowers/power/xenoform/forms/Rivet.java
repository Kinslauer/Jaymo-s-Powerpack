package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.95, Xeno-Band patch 3) Rivet, the living metal (scanned from a clockwork golem): small, quick, made of liquid
 * metal, so falls only splash it. Its trick is becoming machines.
 * <ul>
 * <li>R, Meld: flow into the block you look at (within {@link #MELD_REACH}) if it's one of the seven shells below, and
 * take its shape: R G V B become that shell's moves. Sneak + R pours you back out. (The block itself is left as it
 * was: Rivet copies it, it doesn't take it.)</li>
 * <li>G, Lash: a whip of liquid metal ({@link #LASH_REACH} blocks) that hurts and drags the creature to you.</li>
 * <li>V, Pour: a quick flowing dash, nothing landing on you while you pour.</li>
 * <li>B, Ride a machine: climb into the minecart, iron or clockwork golem, or android mech you look at and drive it
 * (forward goes where you look). Inside it's faster, stronger and lit with your green; it fights what you hit. B again
 * climbs out.</li>
 * </ul>
 * The shells ({@link #SHELLS}): furnace (fire breath, fire shot, Smelt, smoke), piston (leap, Brace, Shove, Rebound),
 * anvil (high leap, Drop, eye beam, Iron Skin), stonecutter (saw, Burrow, Climb, Spin), smithing table (Brace, Temper,
 * Hammer, Plate), dispenser (arrows, a potion, fire charges, a water spray) and note block (stun pulse, Lure, Chord, Bass
 * Drop). Arrows, potions, fire charges and fuel come from your inventory, since hands are empty while changed.
 */
public final class Rivet extends XenoForm {
	// ---- tuning ----
	public static final double MELD_REACH = 5.0;
	public static final int MELD_COOLDOWN = 20;
	public static final double LASH_REACH = 7.0, LASH_PULL = 0.9;
	public static final float LASH_DAMAGE = 5.0F;
	public static final int LASH_COOLDOWN = 40;
	public static final double POUR_SPEED = 1.4;
	public static final int POUR_COOLDOWN = 40, POUR_GUARD = 10;
	/** Riding: reach to climb in, how often the driven machine is steered (ticks), how far ahead it's sent, its boosts. */
	public static final double RIDE_REACH = 4.0, DRIVE_AHEAD = 3.0, DRIVE_SPEED = 1.3, CART_PUSH = 0.06, CART_MAX = 1.4;
	public static final int RIDE_COOLDOWN = 20, MACHINE_EFFECT_TICKS = 40;
	// furnace
	public static final double BREATH_REACH = 5.0;
	public static final float BREATH_DAMAGE = 3.0F, BREATH_FIRE_SECONDS = 4.0F;
	public static final int BREATH_COOLDOWN = 40, SHOT_COOLDOWN = 30, SMELT_COOLDOWN = 100, SMOKE_COOLDOWN = 200;
	/** Smelt: at most this many items a go; each coal or charcoal cooks this many (a coal block nine times as many). */
	public static final int SMELT_MAX = 16, SMELTS_PER_COAL = 8;
	public static final float SMOKE_RADIUS = 3.5F;
	public static final int SMOKE_TICKS = 140;
	// piston
	public static final double LEAP_OUT = 1.4, LEAP_UP = 0.7, SHOVE_RADIUS = 4.0, SHOVE_OUT = 1.8, SHOVE_UP = 0.4;
	public static final float SHOVE_DAMAGE = 4.0F;
	public static final int LEAP_COOLDOWN = 40, SHOVE_COOLDOWN = 40, REBOUND_COOLDOWN = 20;
	/** Rebound: a landing faster than this (blocks a tick) springs you back up, at this share of the speed. */
	public static final double REBOUND_MIN = 0.5, REBOUND_SHARE = 0.85, REBOUND_MAX = 1.3;
	// anvil
	public static final double HIGH_LEAP = 1.5, DROP_SPEED = 2.5, DROP_RADIUS = 3.0, BEAM_REACH = 12.0;
	public static final float DROP_DAMAGE = 4.0F, DROP_PER_BLOCK = 1.0F, DROP_MAX = 20.0F, BEAM_DAMAGE = 6.0F;
	public static final int HIGH_LEAP_COOLDOWN = 60, DROP_COOLDOWN = 40, BEAM_COOLDOWN = 60, SKIN_COOLDOWN = 400, SKIN_TICKS = 100;
	// stonecutter
	public static final double SAW_REACH = 16.0, BURROW_DISTANCE = 6.0, SPIN_RADIUS = 2.5;
	public static final float SAW_DAMAGE = 6.0F, SPIN_DAMAGE = 5.0F;
	public static final int SAW_COOLDOWN = 30, BURROW_COOLDOWN = 80, SPIN_COOLDOWN = 60;
	// smithing table
	public static final double TEMPER_RADIUS = 2.0, HAMMER_REACH = 3.5;
	public static final float HAMMER_DAMAGE = 8.0F;
	public static final int TEMPER_COOLDOWN = 600, HAMMER_COOLDOWN = 40, PLATE_COOLDOWN = 600, PLATE_TICKS = 600;
	// dispenser
	public static final int VOLLEY = 3, VOLLEY_COOLDOWN = 30, POTION_COOLDOWN = 40, CHARGES = 3, CHARGE_COOLDOWN = 40;
	public static final double SPRAY_REACH = 4.0;
	// note block
	public static final double STUN_RADIUS = 6.0, LURE_RADIUS = 16.0, LURE_REACH = 24.0, CHORD_RADIUS = 12.0, BASS_RADIUS = 5.0;
	public static final float STUN_DAMAGE = 2.0F, BASS_DAMAGE = 4.0F;
	public static final int STUN_COOLDOWN = 120, STUN_TICKS = 60, LURE_COOLDOWN = 200, CHORD_COOLDOWN = 200, BASS_COOLDOWN = 80;
	/** The band's theme, played by Chord: harp notes (semitones up from F#3), one every {@link #CHORD_STEP} ticks. */
	static final int[] THEME = {6, 10, 13, 18, 13, 10, 18, 22};
	static final int CHORD_STEP = 4;

	/** The seven shells, by the blocks that give them. */
	public static final List<String> SHELLS = List.of("furnace", "piston", "anvil", "stonecutter", "smithing", "dispenser", "note");
	private static final Map<Block, String> SHELL_OF = Map.ofEntries(
		Map.entry(Blocks.FURNACE, "furnace"), Map.entry(Blocks.BLAST_FURNACE, "furnace"), Map.entry(Blocks.SMOKER, "furnace"),
		Map.entry(Blocks.PISTON, "piston"), Map.entry(Blocks.STICKY_PISTON, "piston"),
		Map.entry(Blocks.ANVIL, "anvil"), Map.entry(Blocks.CHIPPED_ANVIL, "anvil"), Map.entry(Blocks.DAMAGED_ANVIL, "anvil"),
		Map.entry(Blocks.STONECUTTER, "stonecutter"), Map.entry(Blocks.SMITHING_TABLE, "smithing"),
		Map.entry(Blocks.DISPENSER, "dispenser"), Map.entry(Blocks.DROPPER, "dispenser"), Map.entry(Blocks.NOTE_BLOCK, "note"));
	private static final Identifier CLOCKWORK_GOLEM = Identifier.fromNamespaceAndPath("jaymoskingdoms", "clockwork_golem");

	public Rivet() {
		super("rivet", 0.6F, 20.0F, 4.0, 0.15, 0.2, 0.0, Items.COPPER_INGOT, 0x9FB4C0);
	}

	/** The block each shell is drawn as (for everyone's screen). */
	public static BlockState look(String shell) {
		return switch (shell) {
			case "furnace" -> Blocks.FURNACE.defaultBlockState();
			case "piston" -> Blocks.PISTON.defaultBlockState();
			case "anvil" -> Blocks.ANVIL.defaultBlockState();
			case "stonecutter" -> Blocks.STONECUTTER.defaultBlockState();
			case "smithing" -> Blocks.SMITHING_TABLE.defaultBlockState();
			case "dispenser" -> Blocks.DISPENSER.defaultBlockState();
			default -> Blocks.NOTE_BLOCK.defaultBlockState();
		};
	}

	@Override
	public boolean scans(Entity creature) {
		return CLOCKWORK_GOLEM.equals(BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()));
	}

	/** Liquid metal: a fall only splashes it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(net.minecraft.tags.DamageTypeTags.IS_FALL);
	}

	/** (0.18.96) The Ultimate (Ironclad): only the band's own stronger body, no move of its own. */
	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 0.6, 0.0), 40, 0.5, 0.2);
		sound(player, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
		return true;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		setShell(player, live, null);
		if (player.getVehicle() != null && driven(player) != null) {
			player.stopRiding();
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (live.shell == null) {
			if (down) {
				base(player, live, key);
			}
			return;
		}
		if (key == PowerKey.ABILITY_1 && down && player.isShiftKeyDown()) {
			pourOut(player, live);
			return;
		}
		switch (live.shell) {
			case "furnace" -> { if (down) furnace(player, live, key); }
			case "piston" -> piston(player, live, key, down);
			case "anvil" -> { if (down) anvil(player, live, key); }
			case "stonecutter" -> stonecutter(player, live, key, down);
			case "smithing" -> smithing(player, live, key, down);
			case "dispenser" -> dispenser(player, live, key, down);
			default -> { if (down) note(player, live, key); }
		}
	}

	private void base(ServerPlayer player, XenoformPower.Live live, PowerKey key) {
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				BlockPos at = aimedBlock(player, MELD_REACH);
				String shell = at == null ? null : SHELL_OF.get(level.getBlockState(at).getBlock());
				if (shell == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.no_shell").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				setShell(player, live, shell);
				Vec3 c = Vec3.atCenterOf(at);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(at)), player.getX(), player.getY() + 0.4, player.getZ(), 30, 0.3, 0.3, 0.3, 0.1);
				line(level, player.position().add(0.0, 0.4, 0.0), c, ParticleTypes.ELECTRIC_SPARK, 8);
				sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 0.8F, 1.4F);
				sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.8F);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.shell." + shell).withStyle(ChatFormatting.GREEN));
				cooldown(player, live, R, MELD_COOLDOWN);
			}
			case ABILITY_2 -> {
				LivingEntity target = ready(player, live, G) ? aimed(player, LASH_REACH, e -> true) : null;
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), LASH_DAMAGE);
					Vec3 pull = player.position().subtract(target.position()).normalize().scale(LASH_PULL);
					target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.3, pull.z));
					target.syncVelocity = true;
					line(level, player.getEyePosition().subtract(0.0, 0.3, 0.0), target.getBoundingBox().getCenter(), ParticleTypes.CRIT, 12);
					sound(player, SoundEvents.CHAIN_PLACE, 1.0F, 1.6F);
					cooldown(player, live, G, LASH_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					Vec3 look = player.getLookAngle();
					player.setDeltaMovement(look.x * POUR_SPEED, Math.max(0.1, look.y * POUR_SPEED * 0.5), look.z * POUR_SPEED);
					player.syncVelocity = true;
					player.setInvulnerableTime(Math.max(player.getInvulnerableTime(), POUR_GUARD));
					burst(level, ParticleTypes.FALLING_DRIPSTONE_WATER, player.position().add(0.0, 0.3, 0.0), 12, 0.3, 0.05);
					sound(player, SoundEvents.BUCKET_EMPTY, 0.8F, 1.6F);
					cooldown(player, live, V, POUR_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (!ready(player, live, B)) {
					return;
				}
				if (driven(player) != null) {
					player.stopRiding();
					sound(player, SoundEvents.BUCKET_EMPTY, 0.8F, 1.2F);
					cooldown(player, live, B, RIDE_COOLDOWN);
					return;
				}
				LivingEntity machine = aimed(player, RIDE_REACH, Rivet::machine);
				Entity ride = machine;
				if (ride == null) {
					ride = aimedCart(player);
				}
				if (ride == null || !player.startRiding(ride, true, true)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.no_machine").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ride.getX(), ride.getY() + ride.getBbHeight() * 0.5, ride.getZ(), 30, 0.5, 0.6, 0.5, 0.2);
				sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 1.0F, 0.9F);
				cooldown(player, live, B, RIDE_COOLDOWN);
			}
			default -> {
			}
		}
	}

	/** A machine Rivet can climb into and drive (a minecart is found separately). */
	private static boolean machine(Entity e) {
		return e instanceof IronGolem || e instanceof com.jaymospowers.power.chrono.future.FutureMech
			|| CLOCKWORK_GOLEM.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
	}

	private static @Nullable Entity aimedCart(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RIDE_REACH));
		var box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(RIDE_REACH)).inflate(1.0);
		var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
			e -> e instanceof AbstractMinecart && e.getPassengers().isEmpty(), 0.3F);
		return hit == null ? null : hit.getEntity();
	}

	/** The machine Rivet is driving, or null. */
	private static @Nullable Entity driven(ServerPlayer player) {
		Entity vehicle = player.getVehicle();
		return vehicle instanceof AbstractMinecart || vehicle != null && machine(vehicle) ? vehicle : null;
	}

	private static void setShell(ServerPlayer player, XenoformPower.Live live, @Nullable String shell) {
		live.shell = shell;
		live.brace = live.spray = live.rebound = live.anvilDrop = false;
		live.climb = false;
		live.song = 0;
		if (shell == null) {
			player.removeAttached(XenoformState.SHELL);
		} else {
			player.setAttached(XenoformState.SHELL, shell);
		}
		live.hudDirty = true;
	}

	private static void pourOut(ServerPlayer player, XenoformPower.Live live) {
		setShell(player, live, null);
		burst(player.level(), ParticleTypes.FALLING_DRIPSTONE_WATER, player.position().add(0.0, 0.5, 0.0), 20, 0.4, 0.05);
		sound(player, SoundEvents.BUCKET_EMPTY_LAVA, 0.8F, 1.6F);
		cooldown(player, live, R, MELD_COOLDOWN);
	}

	// ---------------------------------------------------------------- shells

	private void furnace(ServerPlayer player, XenoformPower.Live live, PowerKey key) {
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				Vec3 look = player.getLookAngle();
				for (LivingEntity e : around(player, BREATH_REACH)) {
					Vec3 to = e.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
					if (to.dot(look) > 0.6) {
						e.hurtServer(level, player.damageSources().playerAttack(player), BREATH_DAMAGE);
						e.igniteForSeconds(BREATH_FIRE_SECONDS);
					}
				}
				Vec3 eye = player.getEyePosition().subtract(0.0, 0.3, 0.0);
				for (int i = 1; i <= 10; i++) {
					Vec3 p = eye.add(look.scale(i * BREATH_REACH / 10.0));
					level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.1 * i / 3.0, 0.1 * i / 3.0, 0.1 * i / 3.0, 0.01);
				}
				sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
				cooldown(player, live, R, BREATH_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					Vec3 look = player.getLookAngle();
					SmallFireball bolt = new SmallFireball(level, player, look.scale(1.0));
					bolt.setPos(player.getX() + look.x * 0.6, player.getEyeY() - 0.2, player.getZ() + look.z * 0.6);
					level.addFreshEntity(bolt);
					sound(player, SoundEvents.FIRECHARGE_USE, 1.0F, 1.0F);
					cooldown(player, live, G, SHOT_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && smelt(player)) {
					cooldown(player, live, V, SMELT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					Vec3 at = player.position().add(player.getLookAngle().horizontal().scale(1.5));
					AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
					cloud.setOwner(player);
					cloud.setRadius(SMOKE_RADIUS);
					cloud.setDuration(SMOKE_TICKS);
					cloud.setRadiusPerTick(0.0F);
					cloud.setCustomParticle(ParticleTypes.LARGE_SMOKE);
					cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
					level.addFreshEntity(cloud);
					burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.add(0.0, 0.5, 0.0), 20, 1.0, 0.02);
					sound(player, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
					cooldown(player, live, B, SMOKE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Smelt: the first stack in the inventory that a furnace can cook, up to {@link #SMELT_MAX}, burning carried coal. */
	private static boolean smelt(ServerPlayer player) {
		ServerLevel level = player.level();
		Inventory inv = player.getInventory();
		int fuel = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			fuel += s.is(Items.COAL) || s.is(Items.CHARCOAL) ? s.getCount() * SMELTS_PER_COAL : s.is(Items.COAL_BLOCK) ? s.getCount() * SMELTS_PER_COAL * 9 : 0;
		}
		if (fuel <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.no_fuel").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			SingleRecipeInput input = new SingleRecipeInput(s);
			var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
			if (recipe.isEmpty()) {
				continue;
			}
			ItemStack one = recipe.get().value().assemble(input);
			if (one.isEmpty()) {
				continue;
			}
			int count = Math.min(Math.min(s.getCount(), SMELT_MAX), fuel);
			s.shrink(count);
			burnFuel(inv, Mth.ceilDiv(count, SMELTS_PER_COAL));
			for (int n = 0; n < count; n++) {
				inv.placeItemBackInInventory(one.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.5, player.getZ(), 15, 0.3, 0.3, 0.3, 0.02);
			sound(player, SoundEvents.FURNACE_FIRE_CRACKLE, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.smelted", count, one.getHoverName()).withStyle(ChatFormatting.GOLD));
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.nothing_to_smelt").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		return false;
	}

	/** Takes {@code coal} coal (or charcoal, or coal blocks for nine at once) out of the inventory. */
	private static void burnFuel(Inventory inv, int coal) {
		for (int i = 0; i < inv.getContainerSize() && coal > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) {
				int take = Math.min(coal, s.getCount());
				s.shrink(take);
				coal -= take;
			}
		}
		for (int i = 0; i < inv.getContainerSize() && coal > 0; i++) {
			ItemStack s = inv.getItem(i);
			while (s.is(Items.COAL_BLOCK) && !s.isEmpty() && coal > 0) {
				s.shrink(1);
				coal -= 9;
			}
		}
	}

	private void piston(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		ServerLevel level = player.level();
		if (key == PowerKey.ABILITY_2) {
			live.brace = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && player.onGround()) {
					Vec3 out = player.getLookAngle().horizontal().normalize().scale(LEAP_OUT);
					player.setDeltaMovement(out.x, LEAP_UP, out.z);
					player.syncVelocity = true;
					sound(player, SoundEvents.PISTON_EXTEND, 1.0F, 1.0F);
					cooldown(player, live, R, LEAP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					shoveAhead(player, SHOVE_RADIUS, SHOVE_DAMAGE, 0.3);
					sound(player, SoundEvents.PISTON_EXTEND, 1.2F, 0.7F);
					cooldown(player, live, V, SHOVE_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.rebound = !live.rebound;
					sound(player, live.rebound ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.PISTON_CONTRACT, 1.0F, 1.2F);
					player.sendOverlayMessage(Component.translatable(live.rebound ? "message.jaymospowers.xenoform.rivet.rebound_on"
						: "message.jaymospowers.xenoform.rivet.rebound_off").withStyle(ChatFormatting.GREEN));
					cooldown(player, live, B, REBOUND_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private void anvil(ServerPlayer player, XenoformPower.Live live, PowerKey key) {
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && player.onGround()) {
					player.setDeltaMovement(player.getDeltaMovement().x, HIGH_LEAP, player.getDeltaMovement().z);
					player.syncVelocity = true;
					sound(player, SoundEvents.ANVIL_PLACE, 0.6F, 1.6F);
					cooldown(player, live, R, HIGH_LEAP_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && !player.onGround() && !live.anvilDrop) {
					live.anvilDrop = true;
					live.dropFrom = player.getY();
					player.setDeltaMovement(0.0, -DROP_SPEED, 0.0);
					player.syncVelocity = true;
					sound(player, SoundEvents.ANVIL_FALL, 1.0F, 0.8F);
					cooldown(player, live, G, DROP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				LivingEntity target = ready(player, live, V) ? aimed(player, BEAM_REACH, e -> true) : null;
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), BEAM_DAMAGE);
					line(level, player.getEyePosition(), target.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 16);
					sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8F, 2.0F);
					cooldown(player, live, V, BEAM_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SKIN_TICKS, 1, false, true));
					sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.7F);
					cooldown(player, live, B, SKIN_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private void stonecutter(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		ServerLevel level = player.level();
		if (key == PowerKey.ABILITY_3) {
			live.climb = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				LivingEntity target = ready(player, live, R) ? aimed(player, SAW_REACH, e -> true) : null;
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), SAW_DAMAGE);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1));
					line(level, player.getEyePosition(), target.getBoundingBox().getCenter(), ParticleTypes.CRIT, 16);
					sound(player, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.0F, 1.4F);
					cooldown(player, live, R, SAW_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && burrow(player)) {
					cooldown(player, live, G, BURROW_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					for (LivingEntity e : around(player, SPIN_RADIUS)) {
						e.hurtServer(level, player.damageSources().playerAttack(player), SPIN_DAMAGE);
					}
					ring(level, ParticleTypes.SWEEP_ATTACK, player.position().add(0.0, 0.5, 0.0), SPIN_RADIUS * 0.7, 8);
					sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
					cooldown(player, live, B, SPIN_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Burrow: under the ground and up again {@link #BURROW_DISTANCE} ahead, where there's room to stand. */
	private static boolean burrow(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 dir = player.getLookAngle().horizontal();
		if (dir.lengthSqr() < 1.0E-4) {
			return false;
		}
		dir = dir.normalize();
		for (double d = BURROW_DISTANCE; d >= 2.0; d -= 1.0) {
			Vec3 to = player.position().add(dir.scale(d));
			BlockPos feet = BlockPos.containing(to);
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos at = feet.above(dy);
				if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
					&& level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
					BlockState under = level.getBlockState(player.blockPosition().below());
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY(), player.getZ(), 25, 0.3, 0.1, 0.3, 0.1);
					player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
					BlockState ground = level.getBlockState(at.below());
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 25, 0.3, 0.1, 0.3, 0.1);
					level.playSound(null, at, SoundEvents.ROOTED_DIRT_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
					return true;
				}
			}
		}
		return false;
	}

	private void smithing(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		ServerLevel level = player.level();
		if (key == PowerKey.ABILITY_1) {
			live.brace = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				int mended = 0;
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(TEMPER_RADIUS))) {
					ItemStack s = item.getItem();
					if (s.isDamageableItem() && s.isDamaged()) {
						s.setDamageValue(0);
						mended++;
						level.sendParticles(ParticleTypes.WAX_OFF, item.getX(), item.getY() + 0.3, item.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
					}
				}
				if (mended == 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.nothing_to_temper").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				sound(player, SoundEvents.SMITHING_TABLE_USE, 1.0F, 1.0F);
				cooldown(player, live, G, TEMPER_COOLDOWN);
			}
			case ABILITY_3 -> {
				LivingEntity target = ready(player, live, V) ? aimed(player, HAMMER_REACH, e -> true) : null;
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), HAMMER_DAMAGE);
					shove(target, player.position(), 1.0, 0.3);
					level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
					sound(player, SoundEvents.ANVIL_USE, 1.0F, 0.9F);
					cooldown(player, live, V, HAMMER_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, PLATE_TICKS, 1, false, true));
					sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.8F);
					cooldown(player, live, B, PLATE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private void dispenser(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		ServerLevel level = player.level();
		if (key == PowerKey.ABILITY_4) {
			live.spray = down;
			return;
		}
		if (!down) {
			return;
		}
		Inventory inv = player.getInventory();
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				int shot = 0;
				for (int i = 0; i < inv.getContainerSize() && shot < VOLLEY; i++) {
					ItemStack s = inv.getItem(i);
					while (s.is(ItemTags.ARROWS) && !s.isEmpty() && shot < VOLLEY) {
						ItemStack one = s.split(1);
						Arrow arrow = new Arrow(level, player, one.is(Items.TIPPED_ARROW) || one.is(Items.SPECTRAL_ARROW) ? one : new ItemStack(Items.ARROW), null);
						arrow.pickup = AbstractArrow.Pickup.ALLOWED;
						arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + (shot - 1) * 4.0F, 0.0F, 3.0F, 1.0F);
						level.addFreshEntity(arrow);
						shot++;
					}
				}
				if (shot == 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.no_arrows").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				sound(player, SoundEvents.DISPENSER_LAUNCH, 1.0F, 1.0F);
				cooldown(player, live, R, VOLLEY_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack s = inv.getItem(i);
					if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) {
						ItemStack one = s.split(1);
						var potion = one.is(Items.SPLASH_POTION) ? new ThrownSplashPotion(level, player, one) : new ThrownLingeringPotion(level, player, one);
						potion.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.6F, 1.0F);
						level.addFreshEntity(potion);
						sound(player, SoundEvents.DISPENSER_LAUNCH, 1.0F, 1.3F);
						cooldown(player, live, G, POTION_COOLDOWN);
						return;
					}
				}
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.no_potion").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				int shot = 0;
				Vec3 look = player.getLookAngle();
				for (int i = 0; i < inv.getContainerSize() && shot < CHARGES; i++) {
					ItemStack s = inv.getItem(i);
					while (s.is(Items.FIRE_CHARGE) && !s.isEmpty() && shot < CHARGES) {
						s.shrink(1);
						Vec3 dir = look.add((player.getRandom().nextDouble() - 0.5) * 0.15, (player.getRandom().nextDouble() - 0.5) * 0.1, (player.getRandom().nextDouble() - 0.5) * 0.15);
						SmallFireball bolt = new SmallFireball(level, player, dir);
						bolt.setPos(player.getX() + look.x * 0.6, player.getEyeY() - 0.2, player.getZ() + look.z * 0.6);
						level.addFreshEntity(bolt);
						shot++;
					}
				}
				if (shot == 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.no_charges").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				sound(player, SoundEvents.FIRECHARGE_USE, 1.0F, 0.9F);
				cooldown(player, live, V, CHARGE_COOLDOWN);
			}
			default -> {
			}
		}
	}

	private void note(ServerPlayer player, XenoformPower.Live live, PowerKey key) {
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					for (LivingEntity e : around(player, STUN_RADIUS)) {
						e.hurtServer(level, player.damageSources().playerAttack(player), STUN_DAMAGE);
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, 3));
						e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, STUN_TICKS, 0));
					}
					ring(level, ParticleTypes.NOTE, player.position().add(0.0, 0.5, 0.0), STUN_RADIUS * 0.6, 16);
					sound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 0.5F);
					cooldown(player, live, R, STUN_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				BlockPos at = aimedBlock(player, LURE_REACH);
				if (at == null) {
					return;
				}
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LURE_RADIUS), m -> m.isAlive() && !spared(player, m))) {
					mob.getNavigation().moveTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 1.0);
				}
				level.sendParticles(ParticleTypes.NOTE, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 10, 0.5, 0.3, 0.5, 1.0);
				level.playSound(null, at, SoundEvents.NOTE_BLOCK_CHIME.value(), net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.2F);
				cooldown(player, live, G, LURE_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					live.song = 1;
					for (ServerPlayer friend : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(CHORD_RADIUS))) {
						friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
					}
					cooldown(player, live, V, CHORD_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					for (LivingEntity e : around(player, BASS_RADIUS)) {
						e.hurtServer(level, player.damageSources().playerAttack(player), BASS_DAMAGE);
						shove(e, player.position(), 1.4, 0.5);
					}
					ring(level, ParticleTypes.SONIC_BOOM, player.position().add(0.0, 0.5, 0.0), BASS_RADIUS * 0.5, 6);
					sound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 2.0F, 0.5F);
					cooldown(player, live, B, BASS_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- every tick

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 motion = player.getDeltaMovement();
		// Brace (piston, smithing table): hold fast against everything
		if (live.brace) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 5, 2, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 2, false, false));
			if (player.tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		}
		// Rebound (piston): a hard landing springs you up again
		if (live.rebound && player.onGround() && live.fallSpeed < -REBOUND_MIN && !player.isShiftKeyDown()) {
			player.setDeltaMovement(motion.x, Math.min(REBOUND_MAX, -live.fallSpeed * REBOUND_SHARE), motion.z);
			player.syncVelocity = true;
			sound(player, SoundEvents.SLIME_BLOCK_FALL, 1.0F, 1.2F);
		}
		// Drop (anvil): the slam on landing, harder the farther it fell
		if (live.anvilDrop && (player.onGround() || player.isInWater())) {
			live.anvilDrop = false;
			float hit = Math.min(DROP_MAX, DROP_DAMAGE + (float)Math.max(0.0, live.dropFrom - player.getY()) * DROP_PER_BLOCK);
			for (LivingEntity e : around(player, DROP_RADIUS)) {
				e.hurtServer(level, player.damageSources().playerAttack(player), hit);
				shove(e, player.position(), 0.6, 0.4);
			}
			BlockState ground = level.getBlockState(player.blockPosition().below());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY(), player.getZ(), 40, DROP_RADIUS * 0.4, 0.1, DROP_RADIUS * 0.4, 0.2);
			sound(player, SoundEvents.ANVIL_LAND, 1.4F, 0.7F);
			com.jaymospowers.core.Shakes.heavy(player, player.position(), 8.0, 0.4F);
		}
		live.fallSpeed = player.onGround() ? 0.0 : motion.y;
		// Climb (stonecutter)
		if (live.climb && player.horizontalCollision && !player.onGround() && !player.isInWater()) {
			player.setDeltaMovement(motion.x, Gremlin.CLIMB_SPEED, motion.z);
			player.syncVelocity = true;
			player.resetFallDistance();
		}
		// the water spray (dispenser): fires out, creatures pushed back
		if (live.spray) {
			spray(player);
		}
		// Chord (note block): the band's theme, a note at a time
		if (live.song > 0) {
			int step = (live.song - 1) / CHORD_STEP;
			if ((live.song - 1) % CHORD_STEP == 0 && step < THEME.length) {
				float pitch = (float)Math.pow(2.0, (THEME[step] - 12) / 12.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_HARP.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, pitch);
				level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.3, 0.2, 0.3, THEME[step] / 24.0);
			}
			live.song = step >= THEME.length ? 0 : live.song + 1;
		}
		drive(player, live);
	}

	/** The machine Rivet drives: steered toward where it looks while forward is held, faster and stronger, lit green. */
	private static void drive(ServerPlayer player, XenoformPower.Live live) {
		Entity machine = driven(player);
		if (machine == null) {
			return;
		}
		ServerLevel level = player.level();
		Input input = player.getLastClientInput();
		if (machine instanceof AbstractMinecart cart) {
			Vec3 m = cart.getDeltaMovement();
			if (input.forward() && m.horizontalDistanceSqr() > 1.0E-4 && m.horizontalDistance() < CART_MAX) {
				cart.setDeltaMovement(m.add(m.horizontal().normalize().scale(CART_PUSH)));
			}
		} else if (machine instanceof Mob mob) {
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MACHINE_EFFECT_TICKS, 1, false, false));
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MACHINE_EFFECT_TICKS, 1, false, false));
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			mob.setYRot(player.getYRot());
			mob.yHeadRot = player.getYRot();
			if (input.forward()) {
				Vec3 ahead = mob.position().add(player.getLookAngle().horizontal().normalize().scale(DRIVE_AHEAD));
				mob.getNavigation().moveTo(ahead.x, ahead.y, ahead.z, DRIVE_SPEED);
			} else if (mob.getTarget() == null) {
				mob.getNavigation().stop();
			}
		}
		if (player.tickCount % 4 == 0) {
			level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | XenoformPower.COLOR),
				machine.getX(), machine.getY() + machine.getBbHeight() * 0.5, machine.getZ(), 3, machine.getBbWidth() * 0.4, machine.getBbHeight() * 0.4,
				machine.getBbWidth() * 0.4, 0.0);
		}
	}

	/** Whatever Rivet hurts, the machine it drives goes for too. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		if (driven(player) instanceof Mob mob && target != mob) {
			mob.setTarget(target);
		}
	}

	private static void spray(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (int i = 1; i <= 8; i++) {
			Vec3 p = eye.add(look.scale(i * SPRAY_REACH / 8.0));
			level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0.1);
			BlockPos at = BlockPos.containing(p);
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				if (level.getBlockState(q).is(BlockTags.FIRE)) {
					level.removeBlock(q, false);
					level.playSound(null, q, SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.5F);
				}
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(look.scale(SPRAY_REACH)).inflate(1.0),
			e -> e != player && e.isAlive())) {
			e.clearFire();
			if (!spared(player, e)) {
				e.setDeltaMovement(e.getDeltaMovement().add(look.scale(0.08)));
				e.syncVelocity = true;
			}
		}
		player.clearFire();
		if (player.tickCount % 6 == 0) {
			sound(player, SoundEvents.WEATHER_RAIN, 0.6F, 1.6F);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static void shoveAhead(ServerPlayer player, double radius, float damage, double dot) {
		Vec3 look = player.getLookAngle();
		for (LivingEntity e : around(player, radius)) {
			Vec3 to = e.position().subtract(player.position()).normalize();
			if (to.dot(look) > dot) {
				e.hurtServer(player.level(), player.damageSources().playerAttack(player), damage);
				shove(e, player.position(), SHOVE_OUT, SHOVE_UP);
			}
		}
	}

	private static @Nullable BlockPos aimedBlock(ServerPlayer player, double reach) {
		HitResult hit = player.pick(reach, 0.0F, false);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
	}

	private static void line(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.core.particles.ParticleOptions particle, int points) {
		Vec3 step = to.subtract(from).scale(1.0 / points);
		for (int i = 1; i <= points; i++) {
			Vec3 p = from.add(step.scale(i));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static final class Mth {
		static int ceilDiv(int a, int b) {
			return (a + b - 1) / b;
		}
	}
}
