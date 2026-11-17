package com.jaymospowers.power.reactor;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The Reactor Heart's moves (see {@link ReactorPower}). Every number is a tuning constant here. */
final class ReactorMoves {
	// ---- tuning ----
	private static final String TABLE = "reactor";
	private static final int CONE = 0, DASH = 1, BEAM = 2, CRACK = 3, TOSS = 4;
	/** Cone Burst: reach (blocks), how wide (cosine of the half angle), harm, push, least charge, wait. */
	static final double CONE_REACH = 6.0, CONE_WIDTH = 0.6, CONE_PUSH = 1.0;
	static final float CONE_HARM = 5.0F, CONE_LEAST = 8.0F;
	static final int CONE_COOLDOWN = 30;
	/** Blast Dash and Recoil: the throw, the blast's reach and harm, least charge, wait. */
	static final double DASH_SPEED = 1.5, DASH_RADIUS = 2.0;
	static final float DASH_HARM = 3.0F, DASH_LEAST = 10.0F;
	static final int DASH_COOLDOWN = 40;
	/** Shell Beam: reach, the burst's reach and harm, least charge, wait. */
	static final double BEAM_REACH = 48.0, BEAM_RADIUS = 2.5;
	static final float BEAM_HARM = 9.0F, BEAM_LEAST = 15.0F;
	static final int BEAM_COOLDOWN = 80;
	/** Charged Throw: the throw's speed, the burst's reach and harm, least charge, wait. */
	static final float TOSS_SPEED = 1.6F, TOSS_HARM = 7.0F, TOSS_LEAST = 10.0F;
	static final double TOSS_RADIUS = 2.5;
	static final int TOSS_COOLDOWN = 30;
	/** The drill: how far, ticks a block, charge a block. */
	static final double DRILL_REACH = 5.0;
	static final int DRILL_EVERY = 5;
	static final float DRILL_COST = 1.0F;
	/** Ground Crack: how far it runs, harm, the pop, least charge, wait. */
	static final int CRACK_LENGTH = 8;
	static final float CRACK_HARM = 6.0F, CRACK_LEAST = 12.0F;
	static final double CRACK_POP = 0.6;
	static final int CRACK_COOLDOWN = 60;

	private static final Set<UUID> DRILLING = new HashSet<>();

	private record Thrown(ServerPlayer owner, Snowball shot, float strength) {
	}

	private static final List<Thrown> THROWN = new ArrayList<>();
	private static final Map<Snowball, Vec3> LAST = new HashMap<>();

	private ReactorMoves() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ReactorMoves::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DRILLING.clear();
			THROWN.clear();
			LAST.clear();
		});
	}

	static void forget(ServerPlayer player) {
		DRILLING.remove(player.getUUID());
	}

	private static boolean ready(ServerPlayer player, int slot) {
		return PickKit.ready(TABLE, player, slot) || player.getAbilities().instabuild;
	}

	private static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		return look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
	}

	/** R: a punched wave of air and heat in a cone ahead ({@code starter}: half as strong, a little charge). */
	static void cone(ServerPlayer player, boolean starter) {
		if (!ready(player, CONE)) {
			return;
		}
		float spent = ReactorPower.spend(player, starter ? CONE_LEAST * 0.5F : CONE_LEAST);
		if (spent <= 0.0F) {
			return;
		}
		PickKit.spend(TABLE, player, CONE, CONE_COOLDOWN);
		float f = ReactorPower.strength(spent) * (starter ? 0.5F : 1.0F);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(CONE_REACH), e -> PickKit.foe(player, e))) {
			Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
			double dist = d.length();
			if (dist > CONE_REACH || dist < 1.0E-3 || d.scale(1.0 / dist).dot(look) < CONE_WIDTH) {
				continue;
			}
			e.hurtServer(level, player.damageSources().explosion(player, player), CONE_HARM * f);
			e.setDeltaMovement(e.getDeltaMovement().add(look.scale(CONE_PUSH * f)).add(0.0, 0.2, 0.0));
			e.syncVelocity = true;
		}
		MagicFx.wave(level, eye.add(look.scale(0.6)), look, CONE_REACH, (float)(CONE_REACH * 0.8), ReactorPower.GLOW, 8);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** G: blasts off along the look, the blast behind hurting what stood there. */
	static void dash(ServerPlayer player) {
		if (!ready(player, DASH)) {
			return;
		}
		float spent = ReactorPower.spend(player, DASH_LEAST);
		if (spent <= 0.0F) {
			return;
		}
		PickKit.spend(TABLE, player, DASH, DASH_COOLDOWN);
		float f = ReactorPower.strength(spent);
		Vec3 look = player.getLookAngle();
		ReactorPower.blast(player, player.position().add(0.0, 0.5, 0.0), DASH_RADIUS, DASH_HARM * f, 0.6);
		player.setDeltaMovement(look.scale(DASH_SPEED * Math.min(2.0, f)).add(0.0, 0.2, 0.0));
		player.syncVelocity = true;
		player.resetFallDistance();
	}

	/** Sneak + G: blasts what's in front and is thrown backwards by it. */
	static void recoil(ServerPlayer player) {
		if (!ready(player, DASH)) {
			return;
		}
		float spent = ReactorPower.spend(player, DASH_LEAST);
		if (spent <= 0.0F) {
			return;
		}
		PickKit.spend(TABLE, player, DASH, DASH_COOLDOWN);
		float f = ReactorPower.strength(spent);
		Vec3 look = player.getLookAngle();
		ReactorPower.blast(player, player.getEyePosition().add(look.scale(1.8)), DASH_RADIUS * 1.2, DASH_HARM * 1.5F * f, 1.0);
		player.setDeltaMovement(look.scale(-DASH_SPEED * Math.min(2.0, f)).add(0.0, 0.3, 0.0));
		player.syncVelocity = true;
		player.resetFallDistance();
	}

	/** V: the shell of light down the look, bursting at what it hits. */
	static void beam(ServerPlayer player) {
		if (!ready(player, BEAM)) {
			return;
		}
		float spent = ReactorPower.spend(player, BEAM_LEAST);
		if (spent <= 0.0F) {
			return;
		}
		PickKit.spend(TABLE, player, BEAM, BEAM_COOLDOWN);
		float f = ReactorPower.strength(spent);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(BEAM_REACH));
		BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> PickKit.foe(player, e), 0.3F);
		if (hit != null) {
			end = hit.getLocation();
		}
		MagicFx.beam(level, eye.add(0.0, -0.2, 0.0), end, 0.25F, ReactorPower.GLOW, 8);
		MagicFx.beam(level, eye.add(0.0, -0.2, 0.0), end, 0.1F, ReactorPower.CORE, 8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
		ReactorPower.blast(player, end, BEAM_RADIUS, BEAM_HARM * f, 0.8);
	}

	/** Sneak + V: the held item, charged, thrown to burst where it lands. */
	static void toss(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.nothing_held").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ready(player, TOSS)) {
			return;
		}
		float spent = ReactorPower.spend(player, TOSS_LEAST);
		if (spent <= 0.0F) {
			return;
		}
		PickKit.spend(TABLE, player, TOSS, TOSS_COOLDOWN);
		ServerLevel level = player.level();
		ItemStack one = held.copyWithCount(1);
		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}
		Snowball shot = new Snowball(level, player, one);
		shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, TOSS_SPEED, 0.5F);
		level.addFreshEntity(shot);
		THROWN.add(new Thrown(player, shot, ReactorPower.strength(spent)));
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** B held (or let go): the drill turns on or off. */
	static void drill(ServerPlayer player, boolean on) {
		if (on) {
			DRILLING.add(player.getUUID());
		} else {
			DRILLING.remove(player.getUUID());
		}
	}

	/** Sneak + B: a slam that splits the ground ahead. */
	static void crack(ServerPlayer player) {
		if (!player.onGround() || !ready(player, CRACK)) {
			return;
		}
		float spent = ReactorPower.spend(player, CRACK_LEAST);
		if (spent <= 0.0F) {
			return;
		}
		PickKit.spend(TABLE, player, CRACK, CRACK_COOLDOWN);
		float f = ReactorPower.strength(spent);
		ServerLevel level = player.level();
		Vec3 look = flatLook(player);
		Set<LivingEntity> hit = new HashSet<>();
		for (int i = 1; i <= CRACK_LENGTH; i++) {
			Vec3 at = player.position().add(look.scale(i));
			BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
			com.jaymospowers.core.GroundCracks.crack(level, under, level.getRandom(), 2);
			level.sendParticles(MagicFx.mote(ReactorPower.GLOW, 0.8F), at.x, at.y + 0.1, at.z, 3, 0.3, 0.05, 0.3, 0.02);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.0, 1.5, 1.0), e -> PickKit.foe(player, e))) {
				if (hit.add(e)) {
					e.hurtServer(level, player.damageSources().explosion(player, player), CRACK_HARM * f);
					e.setDeltaMovement(e.getDeltaMovement().add(0.0, CRACK_POP * Math.min(2.0, f), 0.0));
					e.syncVelocity = true;
				}
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
		com.jaymospowers.core.Shakes.around(level, player.position(), 16.0, 1.0F);
	}

	/** Per player: the drill bites. */
	static void tick(ServerPlayer player) {
		if (!DRILLING.contains(player.getUUID()) || player.tickCount % DRILL_EVERY != 0) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(DRILL_REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!ground(state)) {
			if (player.tickCount % 20 == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.drill_no").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return;
		}
		if (ReactorPower.live(player).charge + 1.0E-3F < DRILL_COST && !player.getAbilities().instabuild) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.reactor.low", Math.round(DRILL_COST)).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.getAbilities().instabuild) {
			ReactorPower.live(player).charge -= DRILL_COST;
		}
		net.minecraft.world.level.block.Block.dropResources(state, level, pos, null, player, new ItemStack(Items.IRON_PICKAXE));
		level.removeBlock(pos, false);
		Vec3 c = Vec3.atCenterOf(pos);
		MagicFx.beam(level, eye.add(0.0, -0.3, 0.0), c, 0.08F, ReactorPower.GLOW, 4);
		level.sendParticles(MagicFx.spark(ReactorPower.CORE, 0.6F), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, c.x, c.y, c.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.3F);
	}

	/** What the drill takes: the ground as it lies (stone, deepslate, netherrack, dirt, sand, gravel, clay); never an ore, never anything made. */
	static boolean ground(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
			|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.END_STONE);
	}

	/** Thrown items: each bursts where it lands. */
	private static void serverTick(MinecraftServer server) {
		if (THROWN.isEmpty()) {
			return;
		}
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext();) {
			Thrown t = it.next();
			if (!t.shot().isRemoved()) {
				LAST.put(t.shot(), t.shot().position());
				if (t.shot().tickCount > 200) {
					t.shot().discard();
				}
				continue;
			}
			Vec3 at = LAST.remove(t.shot());
			it.remove();
			if (at != null && !t.owner().isRemoved() && t.owner().level() == t.shot().level()) {
				ReactorPower.blast(t.owner(), at, TOSS_RADIUS, TOSS_HARM * t.strength(), 0.8);
			}
		}
	}
}
