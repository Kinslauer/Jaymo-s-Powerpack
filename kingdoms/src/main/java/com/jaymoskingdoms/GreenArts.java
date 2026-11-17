package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Druid's green arts (Andy's Awakening pick "Green power", 2026-11-17: "Add to druid"), on the Wild key out of a shape
 * ({@link ArtKeys}; in a shape the key is {@link SizeArts}').
 * <ul>
 * <li><b>Greenwarden</b> ({@link #GREEN}): tap at a foe, <b>ivy</b> roots it where it stands; tap at a friend, a <b>vine</b>
 * gives them your health; tap at a block, the vine <b>grapples</b> it and hauls you there; sneak + tap at a foe, the vine
 * <b>drains</b> it into you. And the green is in you: under the open sky by day you mend, and fire hurts you more.</li>
 * <li><b>Bloom and Root</b> ({@link #BLOOM}): sneak + tap with no foe in sight, a <b>patch of sunflowers</b> at your feet heals
 * your side (real sunflowers, where they can stand, that wither with the patch and give nothing if picked); sprint + tap at a friend (or at no one, for yourself), a <b>cocoon of roots</b> closes over them: nothing harms
 * them, they cannot move, and they mend.</li>
 * </ul>
 * The rays (hold, and sneak + hold) are {@link SizeArts#ray}.
 */
public final class GreenArts {
	// ---- tuning ----
	public static final String GREEN = "dru_green", BLOOM = "dru_bloom";
	/** Ivy: reach, how long it roots (ticks), cooldown. */
	static final double IVY_REACH = 16.0;
	static final int IVY_TICKS = 80, IVY_COOLDOWN = 160;
	/** The Slowness level ivy lays on what it roots. */
	static final int IVY_SLOW = 9;
	/** The vines: reach, the distance that snaps one, how long one holds (ticks), cooldown. */
	static final double VINE_REACH = 14.0, VINE_BREAK = 18.0;
	static final int VINE_TICKS = 120, VINE_COOLDOWN = 200;
	/** Giving: health given each second, what it costs the giver, the health the giver is never taken under. Draining: harm and healing each second. */
	static final float GIVE = 2.0F, GIVE_COST = 1.0F, GIVE_FLOOR = 4.0F, DRAIN = 2.0F, DRAIN_HEAL = 1.5F;
	/** Grapple: reach, speed (blocks a tick), the longest haul (ticks), how near counts as there, cooldown. */
	static final double GRAPPLE_REACH = 24.0, GRAPPLE_SPEED = 0.9, GRAPPLE_ARRIVE = 1.6;
	static final int GRAPPLE_TICKS = 40, GRAPPLE_COOLDOWN = 60;
	/** The sunflower patch: its radius, how long it lasts (ticks), what it heals each second (and under the day's sun), cooldown. */
	static final float PATCH_RADIUS = 4.0F, PATCH_HEAL = 1.0F, PATCH_SUN_HEAL = 2.0F;
	static final int PATCH_TICKS = 400, PATCH_COOLDOWN = 900;
	/** The patch's sunflowers are real while it lasts: at most this many are planted, and they wither with it. */
	static final int PATCH_FLOWERS = 12;
	/** The cocoon: reach to an ally, how long it holds (ticks), what it heals each second, cooldown. */
	static final double COCOON_REACH = 12.0;
	static final int COCOON_TICKS = 100, COCOON_COOLDOWN = 1200;
	static final float COCOON_HEAL = 1.0F;
	/** The green in you: health mended in daylight and how often (ticks); how much more fire hurts. */
	static final float DAY_HEAL = 1.0F, FIRE_TAKEN = 1.5F;
	static final int DAY_EVERY = 100;
	static final int LEAF = 0x4E9A2E, SUN = 0xE8C020, BARK = 0x6B4A2A;

	private record Root(LivingEntity target, Vec3 at, long until) {
	}

	private record Vine(ServerPlayer owner, LivingEntity target, boolean give, long until) {
	}

	private record Grapple(Vec3 point, long until) {
	}

	/** A sunflower the patch planted (its lower half), and what was in its two blocks before. */
	private record Flower(BlockPos at, BlockState lower, BlockState upper) {
	}

	private record Patch(ServerLevel level, ServerPlayer owner, Vec3 at, long until, List<Flower> flowers) {
	}

	private static final List<Root> ROOTS = new ArrayList<>();
	private static final List<Vine> VINES = new ArrayList<>();
	private static final Map<UUID, Grapple> GRAPPLES = new HashMap<>();
	private static final List<Patch> PATCHES = new ArrayList<>();
	private static final Map<LivingEntity, Long> COCOONS = new WeakHashMap<>();

	private GreenArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GreenArts::tick);
		// nothing harms the cocooned (but what nothing can stop)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!cocooned(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		// a patch's sunflower is nobody's to pick: struck, it withers and gives nothing
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
			if (!state.is(Blocks.SUNFLOWER) || PATCHES.isEmpty()) {
				return true;
			}
			for (Patch p : PATCHES) {
				for (Iterator<Flower> it = p.flowers.iterator(); it.hasNext();) {
					Flower f = it.next();
					if (p.level == world && (f.at.equals(pos) || f.at.above().equals(pos))) {
						it.remove();
						wither(p.level, f);
						return false;
					}
				}
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Patch p : PATCHES) {
				p.flowers.forEach(f -> wither(p.level, f));
			}
			PATCHES.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ROOTS.clear();
			VINES.clear();
			GRAPPLES.clear();
			PATCHES.clear();
			COCOONS.clear();
		});
	}

	static boolean cocooned(LivingEntity e) {
		Long until = COCOONS.get(e);
		return until != null && e.level().getGameTime() < until;
	}

	/** Fire hurts a Greenwarden more ({@code LivingEntityHurtMixin}). */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		return amount > 0.0F && source.is(DamageTypeTags.IS_FIRE) && victim instanceof Player p && WildShapes.has(p, GREEN) ? amount * FIRE_TAKEN : amount;
	}

	// ---- the key --------------------------------------------------------------------------------------------------------------

	static void key(ServerPlayer player, ArtKeys.Gesture gesture) {
		if (!Classes.is(player, Classes.PlayerClass.DRUID) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		switch (gesture) {
			case TAP -> tap(player, level);
			case SNEAK -> {
				LivingEntity foe = ArtKeys.sight(player, VINE_REACH, e -> !ArtKeys.friend(player, e));
				if (foe != null) {
					if (ClassTrees.check(player, GREEN) && !ArtKeys.cooling(player, "green_vine", VINE_COOLDOWN)) {
						vine(player, level, foe, false);
					}
				} else if (ClassTrees.check(player, BLOOM) && !ArtKeys.cooling(player, "green_patch", PATCH_COOLDOWN)) {
					PATCHES.add(new Patch(level, player, player.position(), level.getGameTime() + PATCH_TICKS, plant(level, player, player.position())));
					Fx.pool(level, player.position().add(0.0, 0.05, 0.0), PATCH_RADIUS, SUN, PATCH_TICKS);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
				}
			}
			case SPRINT -> {
				if (ClassTrees.check(player, BLOOM)) {
					LivingEntity ally = ArtKeys.sight(player, COCOON_REACH, e -> ArtKeys.friend(player, e));
					if (!ArtKeys.cooling(player, "green_cocoon", COCOON_COOLDOWN)) {
						cocoon(level, ally == null ? player : ally);
					}
				}
			}
			case HOLD -> SizeArts.ray(player, level, false);
			case SNEAK_HOLD -> SizeArts.ray(player, level, true);
		}
	}

	/**
	 * Plants the patch's sunflowers about {@code at}, where sunflowers can stand: in the wild or on land the Druid rules, never
	 * on another realm's. Grass and the like they stand in comes back when they wither.
	 */
	private static List<Flower> plant(ServerLevel level, ServerPlayer player, Vec3 at) {
		List<Flower> flowers = new ArrayList<>();
		Faction there = Factions.around(level.getServer(), level, BlockPos.containing(at), 0);
		if (there != null && !Crowns.rules(level.getServer(), there, player.getUUID())) {
			return flowers;
		}
		BlockState sunflower = Blocks.SUNFLOWER.defaultBlockState();
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < PATCH_FLOWERS * 4 && flowers.size() < PATCH_FLOWERS; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, far = Math.sqrt(random.nextDouble()) * PATCH_RADIUS;
			BlockPos column = BlockPos.containing(at.x + Math.cos(angle) * far, at.y, at.z + Math.sin(angle) * far);
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos pos = column.above(dy);
				if (free(level.getBlockState(pos)) && free(level.getBlockState(pos.above())) && sunflower.canSurvive(level, pos)) {
					flowers.add(new Flower(pos, level.getBlockState(pos), level.getBlockState(pos.above())));
					DoublePlantBlock.placeAt(level, sunflower, pos, Block.UPDATE_CLIENTS);
					break;
				}
			}
		}
		return flowers;
	}

	/** Whether a sunflower may take this block's place for a while: air, or grass and the like (never water, or another tall plant). */
	private static boolean free(BlockState state) {
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !(state.getBlock() instanceof DoublePlantBlock);
	}

	/** The sunflower withers: gone without a drop, and what stood there is back. */
	private static void wither(ServerLevel level, Flower flower) {
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
		if (level.getBlockState(flower.at.above()).is(Blocks.SUNFLOWER)) {
			level.setBlock(flower.at.above(), flower.upper, quiet);
		}
		if (level.getBlockState(flower.at).is(Blocks.SUNFLOWER)) {
			level.setBlock(flower.at, flower.lower, quiet);
			level.sendParticles(Fx.miasma(BARK, 0.8F), flower.at.getX() + 0.5, flower.at.getY() + 0.8, flower.at.getZ() + 0.5, 4, 0.2, 0.5, 0.2, 0.0);
		}
	}

	private static void tap(ServerPlayer player, ServerLevel level) {
		if (!ClassTrees.check(player, GREEN)) {
			return;
		}
		LivingEntity seen = ArtKeys.sight(player, IVY_REACH, e -> true);
		if (seen != null && ArtKeys.friend(player, seen)) {
			if (seen.distanceToSqr(player) <= VINE_REACH * VINE_REACH && !ArtKeys.cooling(player, "green_vine", VINE_COOLDOWN)) {
				vine(player, level, seen, true);
			}
			return;
		}
		if (seen != null) {
			if (!ArtKeys.cooling(player, "green_ivy", IVY_COOLDOWN)) {
				// (a boss may tear free at once: the ivy shows, and slows it, but doesn't hold it)
				if (!Legendary.shrugs(seen)) {
					ROOTS.removeIf(r -> r.target == seen);
					ROOTS.add(new Root(seen, seen.position(), level.getGameTime() + IVY_TICKS));
				}
				seen.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, IVY_TICKS, IVY_SLOW), player);
				Fx.sigil(level, seen.position().add(0.0, 0.05, 0.0), seen.getBbWidth() + 0.8F, LEAF, IVY_TICKS);
				level.playSound(null, seen.getX(), seen.getY(), seen.getZ(), SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
			}
			return;
		}
		BlockHitResult hit = ArtKeys.block(player, GRAPPLE_REACH);
		if (hit == null) {
			ArtKeys.refuse(player, "green_nothing");
		} else if (!player.isPassenger() && !ArtKeys.cooling(player, "green_grapple", GRAPPLE_COOLDOWN)) {
			GRAPPLES.put(player.getUUID(), new Grapple(hit.getLocation(), level.getGameTime() + GRAPPLE_TICKS));
			Fx.beam(level, player.position().add(0.0, 1.2, 0.0), hit.getLocation(), 0.05F, LEAF, 14);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 1.0F, 1.3F);
		}
	}

	private static void vine(ServerPlayer player, ServerLevel level, LivingEntity target, boolean give) {
		VINES.removeIf(v -> v.owner == player);
		VINES.add(new Vine(player, target, give, level.getGameTime() + VINE_TICKS));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VINE_PLACE, SoundSource.PLAYERS, 1.0F, give ? 1.4F : 0.6F);
	}

	private static void cocoon(ServerLevel level, LivingEntity ally) {
		COCOONS.put(ally, level.getGameTime() + COCOON_TICKS);
		ally.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, COCOON_TICKS, 9, true, false, true));
		ally.clearFire();
		Fx.orb(level, ally.getBoundingBox().getCenter(), Math.max(ally.getBbWidth(), ally.getBbHeight()) * 0.7F + 0.3F, BARK, COCOON_TICKS);
		level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.MANGROVE_ROOTS_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
				GRAPPLES.remove(player.getUUID());
				continue;
			}
			// the green in you: daylight mends
			if ((player.tickCount + player.getId()) % DAY_EVERY == 0 && player.getHealth() < player.getMaxHealth() && WildShapes.has(player, GREEN)
				&& level.isBrightOutside() && level.canSeeSky(player.blockPosition()) && !level.isRainingAt(player.blockPosition())) {
				player.heal(DAY_HEAL);
				level.sendParticles(Fx.mote(LEAF, 0.8F), player.getX(), player.getY(1.0), player.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
			}
			Grapple g = GRAPPLES.get(player.getUUID());
			if (g != null) {
				Vec3 to = g.point.subtract(player.position().add(0.0, 1.0, 0.0));
				if (to.length() <= GRAPPLE_ARRIVE || level.getGameTime() >= g.until || player.isPassenger() || Druids.shaped(player)) {
					GRAPPLES.remove(player.getUUID());
				} else {
					NightGifts.push(player, to.normalize().scale(GRAPPLE_SPEED));
					player.resetFallDistance();
					if (player.tickCount % 4 == 0) {
						Fx.beam(level, player.position().add(0.0, 1.2, 0.0), g.point, 0.05F, LEAF, 5);
					}
				}
			}
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Root> it = ROOTS.iterator(); it.hasNext();) {
			Root r = it.next();
			if (!r.target.isAlive() || r.target.isRemoved() || now >= r.until) {
				it.remove();
				continue;
			}
			if (r.target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (!(r.target instanceof Player) && r.target.position().subtract(r.at).horizontalDistanceSqr() > 0.09) {
				r.target.setPos(r.at.x, r.target.getY(), r.at.z);
			}
			if (now % 10L == 0L && r.target.level() instanceof ServerLevel level) {
				level.sendParticles(Fx.miasma(LEAF, 1.0F), r.target.getX(), r.target.getY(0.3), r.target.getZ(), 3, r.target.getBbWidth() * 0.4, 0.2, r.target.getBbWidth() * 0.4, 0.0);
			}
		}
		for (Iterator<Vine> it = VINES.iterator(); it.hasNext();) {
			Vine v = it.next();
			if (!(v.owner.level() instanceof ServerLevel level) || !v.owner.isAlive() || !v.target.isAlive() || v.target.level() != level || now >= v.until
				|| v.owner.distanceToSqr(v.target) > VINE_BREAK * VINE_BREAK || Druids.shaped(v.owner)) {
				it.remove();
				continue;
			}
			// a steady beat of its own
			long beat = now + v.owner.getId();
			if (beat % 10L == 0L) {
				Fx.beam(level, v.owner.position().add(0.0, 1.1, 0.0), v.target.getBoundingBox().getCenter(), 0.06F, LEAF, 11);
			}
			if (beat % 20L != 0L) {
				continue;
			}
			if (v.give) {
				if (v.owner.getHealth() - GIVE_COST >= GIVE_FLOOR && v.target.getHealth() < v.target.getMaxHealth()) {
					v.owner.setHealth(v.owner.getHealth() - GIVE_COST);
					v.target.heal(GIVE);
				}
			} else {
				v.target.setInvulnerableTime(0);
				if (v.target.hurtServer(level, level.damageSources().indirectMagic(v.owner, v.owner), DRAIN)) {
					v.owner.heal(DRAIN_HEAL);
				}
			}
		}
		for (Iterator<Patch> it = PATCHES.iterator(); it.hasNext();) {
			Patch p = it.next();
			if (now >= p.until) {
				it.remove();
				p.flowers.forEach(f -> wither(p.level, f));
				if (!p.flowers.isEmpty()) {
					p.level.playSound(null, p.at.x, p.at.y, p.at.z, SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
				}
				continue;
			}
			if (now % 20L == 0L) {
				boolean sun = p.level.isBrightOutside() && p.level.canSeeSky(net.minecraft.core.BlockPos.containing(p.at));
				for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p.at, p.at).inflate(PATCH_RADIUS, 2.0, PATCH_RADIUS),
					e -> e.isAlive() && (e == p.owner || ArtKeys.friend(p.owner, e)) && e.position().subtract(p.at).horizontalDistanceSqr() <= PATCH_RADIUS * PATCH_RADIUS)) {
					e.heal(sun ? PATCH_SUN_HEAL : PATCH_HEAL);
				}
				p.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SUNFLOWER), p.at.x, p.at.y + 0.6, p.at.z, 10, PATCH_RADIUS * 0.5, 0.3, PATCH_RADIUS * 0.5, 0.02);
			}
		}
		if (now % 20L == 0L && !COCOONS.isEmpty()) {
			COCOONS.entrySet().removeIf(e -> !e.getKey().isAlive() || now >= e.getValue());
			for (LivingEntity e : COCOONS.keySet()) {
				e.heal(COCOON_HEAL);
				if (e.level() instanceof ServerLevel level) {
					level.sendParticles(Fx.miasma(BARK, 1.2F), e.getX(), e.getY(0.5), e.getZ(), 4, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.0);
				}
			}
		}
	}
}
