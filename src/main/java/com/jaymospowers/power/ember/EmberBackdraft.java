package com.jaymospowers.power.ember;

import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Backdraft (Andy's Awakening pick: Ember; "Pyre does this as well"). For {@link #DRAW_TICKS} ticks the air is pulled out of
 * {@link #RADIUS} blocks round the caster: every fire in it goes out (fire blocks, lit campfires, burning creatures) and foes are
 * dragged in toward them. Then it all comes back at once: one blast round the caster, bigger and harder for every fire it
 * swallowed, and the fires it put out light again where they were. An Emberborn calls it by sneaking as they start Kindle
 * (left mouse, bare-handed); Pyre with sneak + G.
 */
public final class EmberBackdraft {
	// ---- tuning ----
	/** The draw: how long (ticks), how far (blocks), the pull on a foe (blocks a tick, a tick). */
	static final int DRAW_TICKS = 40;
	static final double RADIUS = 12.0, PULL = 0.12;
	/** The blast: radius and damage before anything is swallowed, what each fire swallowed adds, and the most of each. */
	static final double BLAST_RADIUS = 4.0, PER_FIRE_RADIUS = 0.5, MAX_RADIUS = 10.0;
	static final float BLAST_DAMAGE = 6.0F, PER_FIRE_DAMAGE = 0.75F, MAX_DAMAGE = 20.0F;
	static final int BURN = 5, COOLDOWN = 400;
	/** Fire blocks looked at in the draw each tick (so a forest fire is swallowed over the two seconds, not in one tick). */
	static final int BLOCKS_PER_TICK = 600;
	static final int COLOUR = 0xFF8A1E, AIR = 0xCFC7BC;

	private static final class Draft {
		final ServerPlayer owner;
		final long until;
		int swallowed;
		int scan;
		/** The fires put out, to light again in the blast. */
		final Map<BlockPos, BlockState> doused = new HashMap<>();

		Draft(ServerPlayer owner, long until) {
			this.owner = owner;
			this.until = until;
		}
	}

	private static final Map<UUID, Draft> DRAFTS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private EmberBackdraft() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EmberBackdraft::tick);
	}

	/** Starts the draw round {@code player}: true if it began. */
	public static boolean start(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (DRAFTS.containsKey(player.getUUID()) || READY.getOrDefault(player.getUUID(), 0L) > now && !player.isCreative()) {
			return false;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		DRAFTS.put(player.getUUID(), new Draft(player, now + DRAW_TICKS));
		EmberHeat.cast(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 2.0F, 0.5F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		Iterator<Draft> it = DRAFTS.values().iterator();
		while (it.hasNext()) {
			Draft d = it.next();
			ServerPlayer p = d.owner;
			if (!p.isAlive() || p.isRemoved()) {
				it.remove();
				continue;
			}
			ServerLevel level = p.level();
			Vec3 at = p.position().add(0.0, 1.0, 0.0);
			if (level.getGameTime() >= d.until) {
				it.remove();
				blast(p, level, at, d.swallowed);
				relight(level, d);
				continue;
			}
			draw(p, level, at, d);
		}
	}

	private static void draw(ServerPlayer p, ServerLevel level, Vec3 at, Draft d) {
		AABB box = new AABB(at, at).inflate(AbilityPower.size(p, RADIUS));
		for (Entity e : level.getEntities(p, box, e -> e.isAlive() && e.position().distanceToSqr(at) <= AbilityPower.size(p, RADIUS) * AbilityPower.size(p, RADIUS))) {
			if (e.isOnFire()) {
				e.clearFire();
				d.swallowed++;
				level.sendParticles(MagicFx.miasma(AIR, 1.0F), e.getX(), e.getY(0.5), e.getZ(), 4, 0.2, 0.3, 0.2, 0.01);
			}
			if (FireKit.foe(p, e) && e instanceof LivingEntity living) {
				Vec3 in = at.subtract(e.position());
				double dist = in.length();
				if (dist > 1.5) {
					living.setDeltaMovement(living.getDeltaMovement().add(in.scale(PULL / dist)));
					living.syncVelocity = true;
				}
			}
		}
		// the fire blocks, a slice of the sphere a tick
		int r = (int)Math.ceil(AbilityPower.size(p, RADIUS)), side = 2 * r + 1, total = side * side * side;
		BlockPos centre = BlockPos.containing(at);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int n = 0; n < BLOCKS_PER_TICK && d.scan < total; n++, d.scan++) {
			int i = d.scan;
			pos.set(centre.getX() - r + i % side, centre.getY() - r + (i / side) % side, centre.getZ() - r + i / (side * side));
			if (pos.distSqr(centre) > AbilityPower.size(p, RADIUS) * AbilityPower.size(p, RADIUS)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				d.doused.put(pos.immutable(), state);
				d.swallowed++;
			} else if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
				level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
				d.doused.put(pos.immutable(), state);
				d.swallowed++;
			}
		}
		// the air streams in from every side
		for (int i = 0; i < 6; i++) {
			Vec3 from = at.add(new Vec3(level.getRandom().nextGaussian(), level.getRandom().nextGaussian() * 0.5, level.getRandom().nextGaussian()).normalize().scale(AbilityPower.size(p, RADIUS) * 0.8));
			Vec3 v = at.subtract(from).scale(0.08);
			level.sendParticles(MagicFx.miasma(AIR, 1.4F), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
		}
	}

	/** Every fire the draw put out comes back where it was (if its place is still free, or the campfire still there). */
	private static void relight(ServerLevel level, Draft d) {
		for (Map.Entry<BlockPos, BlockState> e : d.doused.entrySet()) {
			BlockState now = level.getBlockState(e.getKey());
			BlockState was = e.getValue();
			if (was.is(BlockTags.FIRE) ? now.isAir() : now.getBlock() == was.getBlock()) {
				level.setBlock(e.getKey(), was.is(BlockTags.FIRE) ? was : now.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
			}
		}
	}

	private static void blast(ServerPlayer p, ServerLevel level, Vec3 at, int swallowed) {
		double radius = Math.min(AbilityPower.size(p, MAX_RADIUS), AbilityPower.size(p, BLAST_RADIUS) + swallowed * AbilityPower.size(p, PER_FIRE_RADIUS));
		// (FireKit.hurt adds Heat's bonus)
		float damage = Math.min(MAX_DAMAGE, BLAST_DAMAGE + swallowed * PER_FIRE_DAMAGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> FireKit.foe(p, e) && e.position().distanceToSqr(at) <= radius * radius)) {
			FireKit.hurt(p, e, damage);
			Flames.touch(level, e, BURN, p);
			Vec3 out = e.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
			out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.9);
			e.setDeltaMovement(e.getDeltaMovement().add(out.x, 0.4, out.z));
			e.syncVelocity = true;
		}
		MagicFx.blast(level, at, (float)radius, COLOUR);
		MagicFx.ring(level, p.position().add(0.0, 0.1, 0.0), (float)radius, COLOUR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0F, 0.5F);
		com.jaymospowers.core.Shakes.heavy(p, at, radius * 2.0, (float)Math.min(1.5, 0.5 + radius / 10.0));
	}
}
