package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The camp spells (fifth edition's alarm, tiny hut, create food and purify food, Andy's pick H6): spells that make camping
 * safe and fed.
 * <ul>
 * <li><b>Ward line</b> (alarm): a line across the ground, twelve blocks long, that chimes when anything but its owner and
 * their friends crosses it, and wakes the sleepers near. A camp with a ward line or a hut up counts as watched
 * ({@link NightWatch}): no night visitors come for its sleepers.</li>
 * <li><b>Hut</b> (tiny hut): a dome of force for one night. Arrows and bolts from outside turn back at its shell; hostile
 * beasts cannot come in; and inside, the weather does not reach the body (Kitchen's body heat is held neutral).</li>
 * <li><b>Create food</b>: plain bread conjured into the pot, cauldron or chest you look at.</li>
 * <li><b>Purify food</b>: the food you carry and the food lying near is made safe: poison washed out, spoiled food made
 * plain bread again (Kitchen's freshness restored), a poisonous potato or a pufferfish made a potato or a cod.</li>
 * </ul>
 */
public final class CampSpells {
	/** The ward line: half its length (blocks), how long it stands (ticks). */
	static final double LINE_HALF = 6.0;
	static final int LINE_TICKS = 20 * 60 * 10;
	/** The ward line chimes at most this often (ticks), wakes sleepers this near (blocks), and tells its owner this far off (blocks). */
	static final int CHIME_GAP = 40;
	static final double WAKES = 32.0, TELLS_OWNER = 120.0;
	/** The hut: its radius (blocks) and how long it stands (ticks: a night). */
	static final double HUT_RADIUS = 4.5;
	static final int HUT_TICKS = 12000;
	/** How hard the dome shoves a beast out (blocks a tick). */
	static final double SHOVE = 0.45;
	/** A camp counts as watched by a ward or a hut this near its sleepers (blocks). */
	static final double WATCHES = 16.0;
	/** How far the caster can reach a pot (blocks). */
	static final double POT_REACH = 6.0;
	/** Create food: loaves made, and the most a scroll or staff adds for power. */
	static final int LOAVES = 3, LOAVES_PER_POWER = 2;
	/** Purify food: how far round the caster lying food is made safe (blocks). */
	static final double PURIFIES = 4.0;

	private CampSpells() {
	}

	static boolean ours(String spell) {
		return switch (spell) {
			case "ward_line", "hut", "create_food", "purify_food" -> true;
			default -> false;
		};
	}

	static boolean cast(String spell, ServerLevel level, Player caster, float power) {
		return switch (spell) {
			case "ward_line" -> wardLine(level, caster, power);
			case "hut" -> hut(level, caster, power);
			case "create_food" -> createFood(level, caster, power);
			case "purify_food" -> purifyFood(level, caster, power);
			default -> false;
		};
	}

	// ---- the ward line ---------------------------------------------------------------------------------------------------------

	private static boolean wardLine(ServerLevel level, Player caster, float power) {
		Vec3 look = caster.getLookAngle();
		Vec3 along = new Vec3(-look.z, 0.0, look.x);
		along = along.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : along.normalize();
		// laid a couple of paces in front of them, across the way they face
		Vec3 centre = caster.position().add(look.x, 0.0, look.z).add(look.multiply(1.0, 0.0, 1.0).normalize().scale(1.5));
		Fields.add(Fields.Kind.WARD_LINE, level, centre, LINE_HALF, 0.0, Math.round(LINE_TICKS * Math.max(0.5F, power)), caster.getUUID(), along);
		for (int i = -6; i <= 6; i++) {
			Vec3 p = centre.add(along.scale(i));
			Fx.burst(level, p.x, p.y + 0.2, p.z, Fields.Kind.WARD_LINE.colour, 0.8F, 3, 0.1, 0.1, 0.1);
		}
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8F, 1.6F);
		return true;
	}

	/** Whether {@code e} is the owner's friend: the owner, an ally, a party mate. */
	static boolean friend(@Nullable LivingEntity owner, Entity e) {
		if (owner == null) {
			return false;
		}
		return e == owner || owner.isAlliedTo(e) || e instanceof ServerPlayer p && owner instanceof ServerPlayer o && Parties.allied(o, p);
	}

	/** One tick of a ward line or a hut. */
	static void tick(Fields.Field f, long now) {
		ServerLevel level = f.level;
		LivingEntity owner = level.getEntity(f.owner) instanceof LivingEntity l ? l : null;
		if (f.kind == Fields.Kind.WARD_LINE) {
			if (now % 2L == 0L) {
				ward(f, now, owner);
			}
		} else if (now % 2L == 0L) {
			dome(f, owner);
		}
	}

	private static void ward(Fields.Field f, long now, @Nullable LivingEntity owner) {
		ServerLevel level = f.level;
		AABB box = new AABB(f.at, f.at).inflate(f.radius + 2.0, 4.0, f.radius + 2.0);
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, Fields::body);
		for (LivingEntity e : near) {
			Vec3 here = e.position();
			Vec3 was = f.last.put(e.getUUID(), here);
			if (was == null || friend(owner, e) || !f.crossedBy(was, here) || e.getBbHeight() < 0.4F) {
				continue;
			}
			if (now - f.lastChime >= CHIME_GAP) {
				f.lastChime = now;
				chime(f, here, owner);
			}
		}
		if (f.last.size() > 64) {
			f.last.keySet().removeIf(id -> near.stream().noneMatch(e -> e.getUUID().equals(id)));
		}
	}

	private static void chime(Fields.Field f, Vec3 at, @Nullable LivingEntity owner) {
		ServerLevel level = f.level;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.5F, 1.5F);
		Fx.ring(level, at, 1.5F, Fields.Kind.WARD_LINE.colour);
		for (ServerPlayer p : level.players()) {
			if (p.isSleeping() && p.distanceToSqr(f.at) <= WAKES * WAKES && (friend(owner, p) || p.getUUID().equals(f.owner))) {
				p.stopSleepInBed(true, true);
			}
		}
		if (owner instanceof ServerPlayer o && o.distanceToSqr(at) <= TELLS_OWNER * TELLS_OWNER) {
			level.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.7F);
		}
	}

	/** Whether a ward line or a hut stands within {@link #WATCHES} of {@code pos}: the camp there is watched. */
	static boolean watching(ServerLevel level, Vec3 pos) {
		for (Fields.Field f : Fields.all()) {
			if (f.level == level && f.alive() && (f.kind == Fields.Kind.WARD_LINE || f.kind == Fields.Kind.DOME)
				&& f.at.distanceToSqr(pos) <= (WATCHES + f.radius) * (WATCHES + f.radius)) {
				return true;
			}
		}
		return false;
	}

	// ---- the hut ---------------------------------------------------------------------------------------------------------------

	private static boolean hut(ServerLevel level, Player caster, float power) {
		Fields.add(Fields.Kind.DOME, level, caster.position(), HUT_RADIUS, 0.0, Math.round(HUT_TICKS * Math.max(0.5F, power)), caster.getUUID(), null);
		Fx.blast(level, caster.position().add(0.0, 1.0, 0.0), (float) HUT_RADIUS, Fields.Kind.DOME.colour);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	/** Whether {@code e} is sheltered by a hut: inside a dome's shell. */
	public static boolean domed(Entity e) {
		return e.level() instanceof ServerLevel level && !Fields.at(level, e.position().add(0.0, 0.8, 0.0), Fields.Kind.DOME).isEmpty();
	}

	private static void dome(Fields.Field f, @Nullable LivingEntity owner) {
		ServerLevel level = f.level;
		Vec3 c = f.at;
		AABB box = new AABB(c, c).inflate(f.radius + 4.0);
		// arrows and bolts from outside turn back at the shell
		for (Projectile shot : level.getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
			Entity from = shot.getOwner();
			if (from != null && (friend(owner, from) || from.position().distanceToSqr(c) <= f.radius * f.radius)) {
				continue;
			}
			Vec3 off = shot.position().subtract(c);
			Vec3 v = shot.getDeltaMovement();
			double d = off.length();
			if (d < 1.0E-3 || d > f.radius + v.length() || v.dot(off) >= 0.0) {
				continue;
			}
			Vec3 out = off.normalize();
			shot.setPos(c.add(out.scale(f.radius + 0.2)));
			shot.setDeltaMovement(out.scale(Math.max(0.3, v.length() * 0.3)));
			shot.syncVelocity = true;
			level.sendParticles(Fx.CRACKLE, shot.getX(), shot.getY(), shot.getZ(), 5, 0.1, 0.1, 0.1, 0.1);
			level.playSound(null, shot.getX(), shot.getY(), shot.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
		}
		// hostile beasts, and anything set on someone inside, cannot come in
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && !friend(owner, m) && !Legendary.boss(m))) {
			double d = mob.position().add(0.0, 0.8, 0.0).distanceTo(c);
			if (d > f.radius) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			boolean menace = mob instanceof Enemy || target != null && target.position().distanceToSqr(c) <= f.radius * f.radius;
			if (!menace || Thralls.isThrall(mob) && owner != null && !Thralls.refuses(owner, mob)) {
				continue;
			}
			Vec3 out = mob.position().subtract(c).multiply(1.0, 0.0, 1.0);
			out = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
			mob.setDeltaMovement(out.x * SHOVE, 0.15, out.z * SHOVE);
			mob.syncVelocity = true;
			mob.getNavigation().stop();
			if (target != null && target.position().distanceToSqr(c) <= f.radius * f.radius) {
				mob.setTarget(null);
			}
		}
	}

	// ---- create food, purify food ----------------------------------------------------------------------------------------------

	private static boolean createFood(ServerLevel level, Player caster, float power) {
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(caster.getEyePosition(),
			caster.getEyePosition().add(caster.getLookAngle().scale(POT_REACH)), net.minecraft.world.level.ClipContext.Block.OUTLINE,
			net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		BlockEntity be = level.getBlockEntity(pos);
		boolean pot = id.getPath().equals("cooking_pot") || state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON)
			|| state.is(Blocks.POWDER_SNOW_CAULDRON);
		int loaves = Math.round(LOAVES + LOAVES_PER_POWER * power);
		ItemStack food = new ItemStack(Items.BREAD, loaves);
		if (be instanceof Container container && !pot) {
			food = put(container, food);
			be.setChanged();
			if (food.getCount() == loaves) {
				return false;
			}
		}
		if (!food.isEmpty()) {
			if (!pot && !(be instanceof Container)) {
				return false;
			}
			// a pot holds no stores: the loaves fill it to the brim and spill out on top
			Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.7, 0.0);
			ItemEntity drop = new ItemEntity(level, top.x, top.y, top.z, food);
			drop.setDeltaMovement(0.0, 0.1, 0.0);
			level.addFreshEntity(drop);
		}
		Vec3 at = Vec3.atCenterOf(pos).add(0.0, 0.6, 0.0);
		level.sendParticles(Fx.mote(0xF0D890, 1.0F), at.x, at.y, at.z, 12, 0.25, 0.2, 0.25, 0.02);
		Fx.ring(level, at.add(0.0, -0.4, 0.0), 1.2F, 0xF0D890);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.3F);
		return true;
	}

	/** Puts as much of {@code stack} in {@code c} as it takes; what is left. */
	private static ItemStack put(Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack in = c.getItem(i);
			if (in.isEmpty()) {
				c.setItem(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
			} else if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
				int add = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(add);
				stack.shrink(add);
			}
		}
		return stack;
	}

	private static boolean purifyFood(ServerLevel level, Player caster, float power) {
		int cleaned = 0;
		Inventory inv = caster.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			ItemStack made = purified(level, stack);
			if (made != stack) {
				inv.setItem(i, made);
				cleaned++;
			} else if (touched) {
				cleaned++;
			}
			touched = false;
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, caster.getBoundingBox().inflate(PURIFIES), ItemEntity::isAlive)) {
			ItemStack made = purified(level, item.getItem());
			if (made != item.getItem()) {
				item.setItem(made);
				cleaned++;
			} else if (touched) {
				cleaned++;
			}
			touched = false;
		}
		if (cleaned == 0) {
			return false;
		}
		Vec3 at = caster.position().add(0.0, 1.0, 0.0);
		level.sendParticles(Fx.LIGHT, at.x, at.y, at.z, 16, 0.5, 0.5, 0.5, 0.03);
		Fx.ring(level, caster.position(), 2.0F, 0xFFF6E0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	/** Set when {@link #purified} cleaned a stack in place (its components changed) rather than swapping it. */
	private static boolean touched;

	/**
	 * {@code stack} made safe: poison washed out and Kitchen's freshness restored in place (and {@link #touched} set), or a
	 * different stack that stands for it (spoiled food as bread, a poisonous potato as a potato); the same stack untouched
	 * if it needed nothing.
	 */
	static ItemStack purified(ServerLevel level, ItemStack stack) {
		touched = false;
		if (stack.isEmpty()) {
			return stack;
		}
		Item swap = null;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (stack.is(Items.POISONOUS_POTATO)) {
			swap = Items.POTATO;
		} else if (stack.is(Items.PUFFERFISH)) {
			swap = Items.COD;
		} else if (id.getNamespace().equals("jaymoskitchen") && id.getPath().equals("spoiled_food")) {
			swap = Items.BREAD;
		}
		if (swap != null) {
			return new ItemStack(swap, stack.getCount());
		}
		if (stack.get(Poisons.KIND) != null && stack.get(Poisons.COAT) == null) {
			stack.remove(Poisons.KIND);
			touched = true;
		}
		if (stack.has(DataComponents.FOOD) && freshen(level, stack)) {
			touched = true;
		}
		return stack;
	}

	private static @Nullable DataComponentType<Long> madeType;
	private static java.lang.reflect.@Nullable Method today;
	private static boolean lookedForFreshness;

	/** Kitchen's freshness (the day the food was made) set back to today; false if the stack has none, or without Kitchen. */
	@SuppressWarnings("unchecked")
	private static boolean freshen(ServerLevel level, ItemStack stack) {
		if (!lookedForFreshness) {
			lookedForFreshness = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
				try {
					Class<?> fresh = Class.forName("com.jaymoskitchen.fresh.Freshness");
					madeType = (DataComponentType<Long>) fresh.getField("MADE").get(null);
					today = fresh.getMethod("today", net.minecraft.world.level.Level.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.debug("Jaymo's Kitchen has no food freshness to restore (older Kitchen?)", e);
				}
			}
		}
		if (madeType == null || today == null) {
			return false;
		}
		Long made = stack.get(madeType);
		try {
			long now = (long) today.invoke(null, level);
			if (made == null || made >= now) {
				return false;
			}
			stack.set(madeType, now);
			return true;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}
}
