package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.109, Scan C) Tusk, the war boar: broad and heavy, bristly dark-brown hide, a ridge of stiff mane down its
 * back, a flat snout and two curved tusks. 34 health, fists of 6, a little slow, hard to knock about. Scanned from a
 * hoglin, Naturalist's boar or rhino, Kingdoms' wild boar, or the Aether's burrukai, kirrid or taegore
 * ({@code XenoDna}).
 * <ul>
 * <li>R, Charge: head down, a run where you face; the first creature met takes a blow and is tossed. Sneak + R is the
 * Rampage instead: a longer run on all fours that does not stop; standing trees come down as felled trunks (Kingdoms'
 * falling trees if it is loaded, else the logs just break) and every creature in the path is gored and tossed aside.</li>
 * <li>G, Gore: the tusks into the creature in reach: a cut that bleeds, and a shove.</li>
 * <li>V, Root Out: dig the ground at your feet (or where you look) for roots and truffles: food, and sometimes seeds
 * or bone meal.</li>
 * <li>B, Thick Hide: for {@link #HIDE_TICKS} ticks nothing knocks you back, and blows land softer.</li>
 * </ul>
 */
public final class Tusk extends XenoForm {
	// ---- tuning ----
	public static final double CHARGE_SPEED = 0.7, CHARGE_HIT = 1.0, CHARGE_TOSS = 0.9, CHARGE_OUT = 1.4;
	public static final int CHARGE_TICKS = 25, CHARGE_COOLDOWN = 100;
	public static final float CHARGE_DAMAGE = 7.0F;
	/** Rampage (Sneak + R): ticks, speed, the gore's damage and its toss aside / up, the reach it gores at, ticks of wall that end it, cooldown. */
	public static final int RAMPAGE_TICKS = 50, RAMPAGE_WALL_TICKS = 4, RAMPAGE_COOLDOWN = 220;
	public static final double RAMPAGE_SPEED = 0.8, RAMPAGE_TOSS_SIDE = 1.1, RAMPAGE_TOSS_AHEAD = 0.4, RAMPAGE_TOSS_UP = 0.55, RAMPAGE_REACH = 0.7;
	public static final float RAMPAGE_DAMAGE = 6.0F;
	public static final double GORE_REACH = 3.0;
	public static final float GORE_DAMAGE = 6.0F;
	public static final int GORE_COOLDOWN = 50;
	public static final double ROOT_REACH = 4.0;
	public static final int ROOT_COOLDOWN = 200;
	/** Chance a dig also turns up a seed or bone meal. */
	public static final float ROOT_EXTRA = 0.4F;
	public static final int HIDE_TICKS = 200, HIDE_COOLDOWN = 500;

	/** A rampage under way: the creatures already gored, and ticks it has been against a wall. */
	private static final class Rampage {
		final it.unimi.dsi.fastutil.ints.IntSet gored = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
		int wall;
	}

	private static final java.util.Map<java.util.UUID, Rampage> RAMPAGES = new java.util.HashMap<>();

	private static final Identifier HIDE_ID = com.jaymospowers.JaymosPowers.id("xenoform_tusk_hide");
	private static final Item[] FOOD = {Items.POTATO, Items.CARROT, Items.BEETROOT, Items.BROWN_MUSHROOM, Items.SWEET_BERRIES};
	private static final Item[] EXTRA = {Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.BONE_MEAL};

	public Tusk() {
		super("tusk", 1.15F, 34.0F, 6.0, -0.03, 0.6, 0.4, Items.LEATHER, 0x6B4A32);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Hoglin;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.rush = 0;
		RAMPAGES.remove(player.getUUID());
		live.hideUntil = 0L;
		hide(player, false);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					if (player.isShiftKeyDown()) {
						// Sneak + R: the rampage
						live.rush = RAMPAGE_TICKS;
						RAMPAGES.put(player.getUUID(), new Rampage());
						sound(player, SoundEvents.HOGLIN_ANGRY, 1.6F, 0.6F);
						sound(player, SoundEvents.RAVAGER_ROAR, 0.8F, 1.3F);
						cooldown(player, live, R, RAMPAGE_COOLDOWN);
						return;
					}
					live.rush = CHARGE_TICKS;
					RAMPAGES.remove(player.getUUID());
					sound(player, SoundEvents.HOGLIN_ANGRY, 1.2F, 0.8F);
					cooldown(player, live, R, CHARGE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, GORE_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), GORE_DAMAGE);
				Bleeding.open(target, BodyPart.BODY, true);
				shove(target, player.position(), 0.8, 0.3);
				live.hurt = true;
				sound(player, SoundEvents.HOGLIN_ATTACK, 1.0F, 0.9F);
				cooldown(player, live, G, GORE_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && root(player, level)) {
					cooldown(player, live, V, ROOT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.hideUntil = level.getGameTime() + HIDE_TICKS;
					hide(player, true);
					player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, HIDE_TICKS, 0, false, true));
					sound(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2F, 0.6F);
					cooldown(player, live, B, HIDE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Thick Hide on or off: nothing knocks it back. */
	private static void hide(ServerPlayer player, boolean on) {
		AttributeInstance resist = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (resist == null) {
			return;
		}
		if (on) {
			resist.addOrUpdateTransientModifier(new AttributeModifier(HIDE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
		} else {
			resist.removeModifier(HIDE_ID);
		}
	}

	/** Roots out the ground at its feet, or where it looks (within reach): food, and sometimes a seed or bone meal. */
	private static boolean root(ServerPlayer player, ServerLevel level) {
		BlockPos at = player.blockPosition().below();
		if (!diggable(level.getBlockState(at))) {
			at = aimBlock(player, ROOT_REACH);
		}
		if (at == null || !diggable(level.getBlockState(at)) || !player.mayBuild() || !level.mayInteract(player, at)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.tusk.no_ground").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockState state = level.getBlockState(at);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.3, 0.1, 0.3, 0.1);
		if (!state.is(Blocks.MUD)) {
			level.setBlockAndUpdate(at, Blocks.COARSE_DIRT.defaultBlockState());
		}
		var random = player.getRandom();
		drop(level, at, new ItemStack(FOOD[random.nextInt(FOOD.length)], 1 + random.nextInt(2)));
		if (random.nextFloat() < ROOT_EXTRA) {
			drop(level, at, new ItemStack(EXTRA[random.nextInt(EXTRA.length)]));
		}
		sound(player, SoundEvents.SNIFFER_DIGGING, 1.0F, 1.0F);
		return true;
	}

	private static boolean diggable(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT)
			|| state.is(Blocks.MUD);
	}

	private static void drop(ServerLevel level, BlockPos at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, stack, 0.0, 0.25, 0.0);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.hideUntil != 0L && level.getGameTime() >= live.hideUntil) {
			live.hideUntil = 0L;
			hide(player, false);
		}
		if (live.rush > 0) {
			live.rush--;
			Vec3 ahead = player.getLookAngle().horizontal();
			ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : ahead.normalize();
			Rampage rampage = RAMPAGES.get(player.getUUID());
			double pace = rampage != null ? RAMPAGE_SPEED : CHARGE_SPEED;
			player.setDeltaMovement(ahead.x * pace, player.getDeltaMovement().y, ahead.z * pace);
			player.syncVelocity = true;
			if (rampage != null) {
				rampage(player, live, level, rampage, ahead);
				return;
			}
			if (player.tickCount % 3 == 0) {
				BlockState under = level.getBlockState(player.blockPosition().below());
				if (!under.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.3, 0.05,
						0.3, 0.05);
				}
			}
			for (LivingEntity e : around(player, CHARGE_HIT + 1.5)) {
				if (e.getBoundingBox().inflate(CHARGE_HIT * 0.5).intersects(player.getBoundingBox())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), CHARGE_DAMAGE);
					shove(e, player.position(), CHARGE_OUT, CHARGE_TOSS);
					live.hurt = true;
					live.rush = 0;
					sound(player, SoundEvents.HOGLIN_ATTACK, 1.4F, 0.7F);
					com.jaymospowers.core.Shakes.heavy(player, player.position(), 8.0, 0.4F);
					break;
				}
			}
			if (player.horizontalCollision) {
				live.rush = 0;
			}
		}
	}

	/**
	 * One tick of the rampage (after the run's motion): the trees ahead come down, every creature in the path is gored once
	 * and tossed aside, and it only stops when it has been against a wall (not a trunk it is felling) for a few ticks.
	 */
	private static void rampage(ServerPlayer player, XenoformPower.Live live, ServerLevel level, Rampage rampage, Vec3 ahead) {
		// the trees: the shared trample brings a standing trunk down for a sprinter
		player.setSprinting(true);
		boolean felled = com.jaymospowers.core.GiantTrample.ram(player);
		if (felled) {
			sound(player, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.5F, 0.6F);
			com.jaymospowers.core.Shakes.heavy(player, player.position(), 12.0, 0.3F);
		}
		if (player.tickCount % 2 == 0) {
			BlockState under = level.getBlockState(player.blockPosition().below());
			if (!under.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.4, 0.05, 0.4, 0.1);
			}
		}
		Vec3 side = new Vec3(-ahead.z, 0.0, ahead.x);
		net.minecraft.world.phys.AABB zone = player.getBoundingBox().expandTowards(ahead.scale(RAMPAGE_REACH)).inflate(0.4, 0.0, 0.4);
		for (LivingEntity e : around(player, CHARGE_HIT + 2.0)) {
			if (!e.getBoundingBox().intersects(zone) || !rampage.gored.add(e.getId())) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), RAMPAGE_DAMAGE);
			Bleeding.open(e, BodyPart.BODY, true);
			double lean = Math.signum(e.position().subtract(player.position()).dot(side));
			if (lean == 0.0) {
				lean = player.getRandom().nextBoolean() ? 1.0 : -1.0;
			}
			e.setDeltaMovement(ahead.x * RAMPAGE_TOSS_AHEAD + side.x * lean * RAMPAGE_TOSS_SIDE, RAMPAGE_TOSS_UP,
				ahead.z * RAMPAGE_TOSS_AHEAD + side.z * lean * RAMPAGE_TOSS_SIDE);
			e.syncVelocity = true;
			live.hurt = true;
			sound(player, SoundEvents.HOGLIN_ATTACK, 1.4F, 0.7F);
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.4, 0.4, 0.4, 0.25);
			com.jaymospowers.core.Shakes.heavy(player, player.position(), 8.0, 0.35F);
		}
		if (player.horizontalCollision && !felled) {
			if (++rampage.wall >= RAMPAGE_WALL_TICKS) {
				live.rush = 0;
			}
		} else {
			rampage.wall = 0;
		}
		if (live.rush <= 0) {
			RAMPAGES.remove(player.getUUID());
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CAMPFIRE_COSY_SMOKE, player.position().add(0.0, 1.0, 0.0), 20, 0.8, 0.02);
		sound(player, SoundEvents.HOGLIN_ANGRY, 2.0F, 0.6F);
		return true;
	}
}
