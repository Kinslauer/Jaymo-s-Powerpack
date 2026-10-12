package com.jaymospowers.tinkering;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The traits that act on the world: extra damage on a hit, the sledge hammer's wide swing, magnets, severed heads. */
public final class TraitEffects {
	/** Piercing: damage per level that ignores armour, on top of the hit. */
	static final float PIERCE_DAMAGE = 1.0F;
	/** Smite: extra damage per level against the undead. */
	static final float SMITE_DAMAGE = 2.5F;
	/** Severing: chance per level that a kill drops its head. */
	static final float SEVERING_CHANCE = 0.1F;
	/** Magnetic: how long the pull lasts after a block or kill (ticks), its reach (blocks, plus 1 a level) and strength. */
	static final int MAGNET_TICKS = 40;
	static final double MAGNET_RANGE = 3.0, MAGNET_PULL = 0.08;
	/** Wide swing: a block next to the one you broke goes too if it's at most this much harder. */
	static final float AREA_HARDNESS_SLACK = 1.5F;

	/** Players with a magnet on: until when, and how far it reaches. */
	private static final Map<UUID, long[]> MAGNETS = new HashMap<>();
	/** The face each player last started breaking, so the wide swing knows which way the 3x3 lies. */
	private static final Map<UUID, Direction> FACES = new HashMap<>();
	/** Set while the wide swing breaks its extra blocks, so they don't swing again. */
	private static boolean sweeping;

	private TraitEffects() {
	}

	static void init() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			FACES.put(player.getUUID(), direction);
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!sweeping && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
				wideSwing(serverLevel, serverPlayer, pos, state);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof LivingEntity killer && entity.level() instanceof ServerLevel level) {
				ItemStack held = killer.getMainHandItem();
				ToolStats.Stats stats = ToolStats.isBroken(held) ? null : ToolStats.stats(held);
				if (stats == null) {
					return;
				}
				if (stats.level(Trait.SEVERING) > 0 && level.getRandom().nextFloat() < stats.level(Trait.SEVERING) * SEVERING_CHANCE) {
					ItemStack head = head(entity);
					if (!head.isEmpty()) {
						entity.spawnAtLocation(level, head);
					}
				}
				magnet(killer, stats);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (MAGNETS.isEmpty()) {
				return;
			}
			MAGNETS.entrySet().removeIf(entry -> {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null || player.level().getGameTime() > entry.getValue()[0]) {
					return true;
				}
				pull(player, entry.getValue()[1]);
				return false;
			});
		});
	}

	/** After a hit lands: Piercing and Smite add their damage. */
	static void afterHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!(attacker.level() instanceof ServerLevel level) || ToolStats.isBroken(stack) || !target.isAlive()) {
			return;
		}
		ToolStats.Stats stats = ToolStats.stats(stack);
		if (stats == null) {
			return;
		}
		int smite = stats.level(Trait.SMITE);
		if (smite > 0 && target.is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, attacker instanceof Player player ? level.damageSources().playerAttack(player)
				: level.damageSources().mobAttack(attacker), smite * SMITE_DAMAGE);
		}
		int pierce = stats.level(Trait.PIERCE);
		if (pierce > 0 && target.isAlive()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().indirectMagic(attacker, attacker), pierce * PIERCE_DAMAGE);
		}
	}

	/** After a block is mined: Magnetic starts pulling. */
	static void afterMine(ItemStack stack, Level level, LivingEntity owner) {
		if (!level.isClientSide() && !ToolStats.isBroken(stack)) {
			ToolStats.Stats stats = ToolStats.stats(stack);
			if (stats != null) {
				magnet(owner, stats);
			}
		}
	}

	private static void magnet(LivingEntity owner, ToolStats.Stats stats) {
		int magnetic = stats.level(Trait.MAGNETIC);
		if (magnetic > 0 && owner instanceof ServerPlayer player) {
			MAGNETS.put(player.getUUID(), new long[] {player.level().getGameTime() + MAGNET_TICKS, magnetic});
		}
	}

	private static void pull(ServerPlayer player, long level) {
		double range = MAGNET_RANGE + level;
		Vec3 to = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(range))) {
			Vec3 pull = to.subtract(item.position());
			if (pull.lengthSqr() > 0.25) {
				item.setDeltaMovement(item.getDeltaMovement().add(pull.normalize().scale(MAGNET_PULL * level)));
				item.syncVelocity = true;
			}
		}
	}

	/** The sledge hammer breaks the 3x3 face around the block it broke (sneak to break just one). */
	private static void wideSwing(ServerLevel level, ServerPlayer player, BlockPos center, BlockState centerState) {
		ItemStack held = player.getMainHandItem();
		ToolStats.Stats stats = ToolStats.isBroken(held) ? null : ToolStats.stats(held);
		if (stats == null || stats.level(Trait.AREA) <= 0 || player.isShiftKeyDown()) {
			return;
		}
		Direction face = FACES.getOrDefault(player.getUUID(), Direction.UP);
		float hardness = centerState.getDestroySpeed(level, center);
		sweeping = true;
		try {
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					if (a == 0 && b == 0) {
						continue;
					}
					BlockPos pos = switch (face.getAxis()) {
						case Y -> center.offset(a, 0, b);
						case X -> center.offset(0, a, b);
						case Z -> center.offset(a, b, 0);
					};
					BlockState state = level.getBlockState(pos);
					float h = state.getDestroySpeed(level, pos);
					if (state.isAir() || h < 0 || h > hardness + AREA_HARDNESS_SLACK || !held.isCorrectToolForDrops(state)
						|| ToolStats.isBroken(held)) {
						continue;
					}
					player.gameMode.destroyBlock(pos);
				}
			}
		} finally {
			sweeping = false;
		}
	}

	private static ItemStack head(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		Item head = type == EntityTypes.ZOMBIE ? Items.ZOMBIE_HEAD : type == EntityTypes.SKELETON ? Items.SKELETON_SKULL
			: type == EntityTypes.WITHER_SKELETON ? Items.WITHER_SKELETON_SKULL : type == EntityTypes.CREEPER ? Items.CREEPER_HEAD
			: type == EntityTypes.PIGLIN ? Items.PIGLIN_HEAD : null;
		if (head != null) {
			return new ItemStack(head);
		}
		if (entity instanceof Player player) {
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
			return stack;
		}
		return ItemStack.EMPTY;
	}
}
