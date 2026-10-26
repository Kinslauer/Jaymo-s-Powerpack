package com.jaymospowers.ground;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.12) Andy: "add the ability for players to throw rocks not just the npcs". Right click the air with a loose rock
 * ({@link GroundRocks}) in either hand and you throw it, the way Kingdoms' poor guards and barbarians do: a snowball
 * wearing the rock, that hits for {@link #DAMAGE} (Kingdoms' {@code Javelins.ROCK_DAMAGE}). Right clicking a block still
 * sets the rock down, and a rock with flint in the other hand still strikes fire. The rock lies where it lands
 * {@link #KEEP_CHANCE} of the time, to be picked up again; the rest break.
 */
public final class ThrownRocks {
	/** A rock hits for this much. */
	static final float DAMAGE = 3.0F;
	/** How hard it's thrown, and how wide it may stray. */
	static final float SPEED = 1.5F, SPREAD = 1.0F;
	/** Ticks between throws. */
	static final int COOLDOWN = 10;
	/** How often a rock lies where it lands rather than breaking. */
	static final float KEEP_CHANCE = 0.5F;
	/** A rock still flying after this long is no longer watched. */
	static final int LIFE = 200;

	private static final class Rock {
		final Snowball ball;
		final ItemStack stone;
		final boolean creative;
		int age;

		Rock(Snowball ball, ItemStack stone, boolean creative) {
			this.ball = ball;
			this.stone = stone;
			this.creative = creative;
		}
	}

	private static final List<Rock> FLYING = new ArrayList<>();
	/** While a rock's blow is being dealt again (so it isn't again and again). */
	private static boolean redealing;

	private ThrownRocks() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(ThrownRocks::use);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThrownRocks::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(ThrownRocks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLYING.clear());
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator() || !FirstTools.rock(stack) || FirstTools.striking(player) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		if (level instanceof ServerLevel server) {
			ItemStack stone = stack.copyWithCount(1);
			Snowball ball = Projectile.spawnProjectileFromRotation((l, owner, s) -> new Snowball(l, owner, s), server, stone, player, 0.0F, SPEED,
				SPREAD);
			FLYING.add(new Rock(ball, stone, player.getAbilities().instabuild));
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F,
				0.5F + server.getRandom().nextFloat() * 0.2F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean thrown(Entity entity) {
		for (Rock rock : FLYING) {
			if (rock.ball == entity) {
				return true;
			}
		}
		return false;
	}

	/** A snowball does no harm: a rock's blow is dealt again with its weight. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (redealing || amount >= DAMAGE || !(source.getDirectEntity() instanceof Snowball ball) || !thrown(ball)
			|| !(victim.level() instanceof ServerLevel level)) {
			return true;
		}
		redealing = true;
		try {
			victim.hurtServer(level, source, DAMAGE);
		} finally {
			redealing = false;
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (FLYING.isEmpty()) {
			return;
		}
		FLYING.removeIf(rock -> {
			if (rock.ball.isRemoved()) {
				// it hit something (not merely unloaded with its chunk): it may lie there
				if (rock.ball.getRemovalReason() == Entity.RemovalReason.DISCARDED && !rock.creative && rock.ball.level() instanceof ServerLevel level
					&& level.getRandom().nextFloat() < KEEP_CHANCE) {
					Vec3 at = rock.ball.position();
					ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, rock.stone.copy());
					item.setDefaultPickUpDelay();
					level.addFreshEntity(item);
				}
				return true;
			}
			return ++rock.age > LIFE;
		});
	}
}
