package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Echo Bow ({@code jaymospowers:echo_bow}): a bow the Pocket Arsenal now and then pulls out of somewhere else
 * ({@link ArsenalPool}, rarely). Andy: "the echo bow should be a weapon that spawns for arsenal." The idea is Simply
 * Bows' Echo bow (Timefall Development License: no ports, no bundling, so nothing of it is used); this one is our own.
 * <ul>
 * <li>While it is in a hand, two bows of pale light hang at the holder's shoulders ({@link MagicFx} strokes, drawn
 * again every {@link #GHOST_EVERY} ticks, aimed where the holder looks).</li>
 * <li>Every arrow loosed from it is answered: {@link #ECHO_DELAY} ticks later the first ghost bow looses the same shot
 * from its shoulder at whatever the holder is looking at then, and the second {@link #ECHO_DELAY} after that. An echo
 * flies as fast as the shot it answers and hits for {@link #ECHO_DAMAGE} of it; it can't be picked up.</li>
 * <li>An echo is only ever an answer: the ghost bows never loose on their own.</li>
 * </ul>
 */
public final class EchoBow extends BowItem {
	// ---- tuning ----
	/** Ticks between the shot and its first echo, and between the two echoes. */
	public static final int ECHO_DELAY = 5;
	/** An echo's damage, as a share of the shot it answers. */
	public static final double ECHO_DAMAGE = 0.5;
	/** A plain arrow's base damage (vanilla's). */
	public static final double ARROW_BASE = 2.0;
	/** A shot weaker than this (arrow speed, blocks a tick) is not answered: a half-drawn bow makes no echo. */
	public static final double ECHO_MIN_SPEED = 1.5;
	/** How far out the echoes converge on the holder's line of sight (blocks). */
	public static final double ECHO_FOCUS = 32.0;
	/** The ghost bows: how often they're drawn (ticks), how long each drawing lasts, and where they hang from the eyes. */
	public static final int GHOST_EVERY = 6, GHOST_TICKS = 9;
	public static final double GHOST_SIDE = 0.85, GHOST_UP = 0.25, GHOST_BACK = 0.2;
	/** A ghost bow's half height, how far its stave bows forward, and the stroke's width. */
	public static final double GHOST_HALF = 0.55, GHOST_BELLY = 0.28;
	public static final float GHOST_WIDTH = 0.07F;
	public static final int COLOR = 0x9FD8FF;
	public static final int DURABILITY = 384;
	/** How often it turns up in a Reach Through, beside a plain bow's 45 and a netherite sword's 5. */
	public static final int POOL_WEIGHT = 6;

	public static Item ITEM;

	/** An answer waiting to be loosed. */
	private record Echo(ServerPlayer shooter, long when, int side, double speed, double damage, boolean crit) {
	}

	private static final List<Echo> PENDING = new ArrayList<>();

	private EchoBow(Properties properties) {
		super(properties);
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("echo_bow"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new EchoBow(new Item.Properties().setId(key)
			.durability(DURABILITY)
			.enchantable(1)
			.rarity(Rarity.RARE)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.echo_bow.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(ITEM));
		ServerTickEvents.END_SERVER_TICK.register(EchoBow::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle,
		@Nullable LivingEntity target) {
		super.shootProjectile(shooter, projectile, index, power, uncertainty, angle, target);
		double speed = projectile.getDeltaMovement().length();
		// only the first arrow of a shot is answered (Multishot's others are not), and only a real draw
		if (index != 0 || speed < ECHO_MIN_SPEED || !(shooter instanceof ServerPlayer player) || !(projectile instanceof AbstractArrow arrow)) {
			return;
		}
		long now = player.level().getGameTime();
		// an arrow's blow is its base damage times its speed (and Power is added from the bow when it lands), so an echo
		// carries a share of an arrow's base at the same speed
		double damage = ARROW_BASE * ECHO_DAMAGE;
		PENDING.add(new Echo(player, now + ECHO_DELAY, -1, speed, damage, arrow.isCritArrow()));
		PENDING.add(new Echo(player, now + ECHO_DELAY * 2L, 1, speed, damage, arrow.isCritArrow()));
	}

	private static boolean holds(ServerPlayer player) {
		return player.getMainHandItem().is(ITEM) || player.getOffhandItem().is(ITEM);
	}

	/** Where a ghost bow hangs: off the holder's shoulder ({@code side} -1 left, 1 right), a little above the eyes. */
	private static Vec3 shoulder(ServerPlayer player, int side) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		return player.getEyePosition().add(right.scale(GHOST_SIDE * side)).add(0.0, GHOST_UP, 0.0).subtract(flat.scale(GHOST_BACK));
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!PENDING.isEmpty()) {
			Iterator<Echo> it = PENDING.iterator();
			while (it.hasNext()) {
				Echo echo = it.next();
				if (now < echo.when) {
					continue;
				}
				it.remove();
				if (echo.shooter.isAlive() && !echo.shooter.isRemoved() && holds(echo.shooter)) {
					loose(echo);
				}
			}
		}
		if (now % GHOST_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && holds(player)) {
				ghost(player.level(), player, -1, false);
				ghost(player.level(), player, 1, false);
			}
		}
	}

	/** Draws one ghost bow: the stave as a stroke bellied toward the aim, the string straight behind it. */
	private static void ghost(ServerLevel level, ServerPlayer player, int side, boolean loosing) {
		Vec3 at = shoulder(player, side);
		Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(ECHO_FOCUS)).subtract(at).normalize();
		Vec3 top = at.add(0.0, GHOST_HALF, 0.0), bottom = at.subtract(0.0, GHOST_HALF, 0.0);
		float width = loosing ? GHOST_WIDTH * 1.8F : GHOST_WIDTH;
		MagicFx.stroke(level, bottom, top, aim.scale(GHOST_BELLY), width, COLOR, GHOST_TICKS);
		MagicFx.stroke(level, bottom, top, loosing ? null : aim.scale(-0.04), width * 0.5F, COLOR, GHOST_TICKS);
	}

	/** A ghost bow answers: the same shot from its own shoulder, at whatever the holder is looking at now. */
	private static void loose(Echo echo) {
		ServerPlayer player = echo.shooter;
		ServerLevel level = player.level();
		Vec3 from = shoulder(player, echo.side);
		Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(ECHO_FOCUS)).subtract(from).normalize();
		ItemStack weapon = player.getMainHandItem().is(ITEM) ? player.getMainHandItem() : player.getOffhandItem();
		Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), weapon);
		arrow.setOwner(player);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.setBaseDamage(echo.damage);
		arrow.setCritArrow(echo.crit);
		arrow.shoot(aim.x, aim.y, aim.z, (float)echo.speed, 0.0F);
		level.addFreshEntity(arrow);
		ghost(level, player, echo.side, true);
		MagicFx.ring(level, from.add(aim.scale(0.3)), aim, 0.35F, COLOR, 8);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.7F, 1.5F + echo.side * 0.15F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F);
	}
}
