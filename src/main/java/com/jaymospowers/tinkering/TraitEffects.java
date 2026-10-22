package com.jaymospowers.tinkering;

import com.jaymospowers.armory.Armory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	/** Searing: extra damage per level against anything that isn't fireproof. */
	static final float SEARING_DAMAGE = 1.5F;
	/** Tempered (steel): damage per level that goes through armour, against anything wearing some. */
	static final float TEMPERED_ARMOR_PIERCE = 1.0F;
	/** Tasty: hunger at or under which it can be nibbled, chance per level a use, food and saturation, durability it costs. */
	static final int TASTY_HUNGER = 16, TASTY_FOOD = 2, TASTY_COST = 5;
	static final float TASTY_CHANCE = 0.05F, TASTY_SATURATION = 0.3F;
	/** Tool xp for each block mined and each hit landed. */
	static final int XP_PER_BLOCK = 1, XP_PER_HIT = 1;
	/** Wide swing: a block next to the one you broke goes too if it's at most this much harder. */
	static final float AREA_HARDNESS_SLACK = 1.5F;
	/** Charge Attack (minotaur axe): extra damage a level when you hit while sprinting. */
	static final float CHARGE_ATTACK_DAMAGE = 3.0F;
	/** War Charge (war pick): each block mined stacks a charge (up to this many, lost after this many ticks idle); the next hit
	 * spends them all, this much extra damage each. */
	static final int WAR_CHARGE_MAX = 5, WAR_CHARGE_TICKS = 100;
	static final float WAR_CHARGE_DAMAGE = 1.5F;

	/** Players with a magnet on: until when, and how far it reaches. */
	private static final Map<UUID, long[]> MAGNETS = new HashMap<>();
	/** The face each player last started breaking, so the wide swing knows which way the 3x3 lies. */
	private static final Map<UUID, Direction> FACES = new HashMap<>();
	/** Set while the wide swing breaks its extra blocks, so they don't swing again. */
	private static boolean sweeping;
	/** Each war pick holder's charges, and the game time they run out. */
	private static final Map<UUID, long[]> WAR_CHARGES = new HashMap<>();

	private TraitEffects() {
	}

	static void init() {
		// (0.8.76) Armory weapons level up from hits too
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
				&& Armory.isWeapon(player.getMainHandItem())) {
				gainXp(player.getMainHandItem(), player, XP_PER_HIT);
			}
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			FACES.put(player.getUUID(), direction);
			return InteractionResult.PASS;
		});
		// (0.15.x) Harvest: a grown crop broken with the kama or scythe is picked and replanted instead (sneak to break it)
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel && !serverPlayer.isShiftKeyDown()
				&& ToolActions.matureCrop(state)) {
				ItemStack held = serverPlayer.getMainHandItem();
				ToolStats.Stats stats = ToolStats.isBroken(held) ? null : ToolStats.stats(held);
				if (stats != null && stats.level(Trait.HARVEST) > 0 && ToolActions.harvest(serverLevel, serverPlayer, held, pos)) {
					if (!sweeping) {
						areaBreak(serverLevel, serverPlayer, pos, state);
					}
					return false;
				}
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!sweeping && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
				areaBreak(serverLevel, serverPlayer, pos, state);
			}
		});
		ToolActions.init();
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

	/** After a hit lands: Piercing, Smite, Searing and Tempered add their damage. */
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
		int searing = stats.level(Trait.SEARING);
		if (searing > 0 && target.isAlive() && !target.fireImmune()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, attacker instanceof Player player ? level.damageSources().playerAttack(player)
				: level.damageSources().mobAttack(attacker), searing * SEARING_DAMAGE);
		}
		int pierce = stats.level(Trait.PIERCE);
		if (pierce > 0 && target.isAlive()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().indirectMagic(attacker, attacker), pierce * PIERCE_DAMAGE);
		}
		int tempered = stats.level(Trait.TEMPERED);
		if (tempered > 0 && target.isAlive() && target.getArmorValue() > 0) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().indirectMagic(attacker, attacker), tempered * TEMPERED_ARMOR_PIERCE);
		}
		int charge = stats.level(Trait.CHARGE_ATTACK);
		if (charge > 0 && attacker.isSprinting() && target.isAlive()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, attacker instanceof Player player ? level.damageSources().playerAttack(player)
				: level.damageSources().mobAttack(attacker), charge * CHARGE_ATTACK_DAMAGE);
		}
		if (stats.level(Trait.WAR_CHARGE) > 0 && target.isAlive()) {
			long[] charges = WAR_CHARGES.remove(attacker.getUUID());
			if (charges != null && charges[1] >= level.getGameTime() && charges[0] > 0) {
				target.setInvulnerableTime(0);
				target.hurtServer(level, attacker instanceof Player player ? level.damageSources().playerAttack(player)
					: level.damageSources().mobAttack(attacker), charges[0] * stats.level(Trait.WAR_CHARGE) * WAR_CHARGE_DAMAGE);
			}
		}
		ToolActions.afterHit(stats, target, attacker);
		tasty(stack, attacker, stats);
		gainXp(stack, attacker, XP_PER_HIT);
	}

	/** After a block is mined: Magnetic starts pulling. */
	static void afterMine(ItemStack stack, Level level, LivingEntity owner) {
		if (!level.isClientSide() && !ToolStats.isBroken(stack)) {
			ToolStats.Stats stats = ToolStats.stats(stack);
			if (stats != null) {
				magnet(owner, stats);
				tasty(stack, owner, stats);
				if (stats.level(Trait.WAR_CHARGE) > 0) {
					long[] charges = WAR_CHARGES.computeIfAbsent(owner.getUUID(), id -> new long[2]);
					charges[0] = charges[1] < level.getGameTime() ? 1 : Math.min(WAR_CHARGE_MAX, charges[0] + 1);
					charges[1] = level.getGameTime() + WAR_CHARGE_TICKS;
				}
				gainXp(stack, owner, XP_PER_BLOCK);
			}
		}
	}

	/** Tasty: a hungry player sometimes takes a bite of the tool, at the cost of some durability. */
	private static void tasty(ItemStack stack, LivingEntity owner, ToolStats.Stats stats) {
		int tasty = stats.level(Trait.TASTY);
		if (tasty > 0 && owner instanceof ServerPlayer player && player.getFoodData().getFoodLevel() <= TASTY_HUNGER
			&& player.getRandom().nextFloat() < tasty * TASTY_CHANCE) {
			player.getFoodData().eat(TASTY_FOOD, TASTY_SATURATION);
			stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + TASTY_COST));
			ToolStats.apply(stack);
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, 1.0F);
		}
	}

	/** Use earns a tool xp; each level it reaches gives it another modifier slot. */
	private static void gainXp(ItemStack stack, LivingEntity owner, int amount) {
		if (!(owner instanceof ServerPlayer player) || !Modifier.takes(stack)) {
			return;
		}
		int before = Modifier.xp(stack);
		int level = Modifier.toolLevel(before);
		stack.set(Tinkering.TOOL_XP, before + amount);
		if (Modifier.toolLevel(before + amount) > level) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.tinkering.level_up", stack.getHoverName(), level + 1)
				.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
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

	/**
	 * The tool's area: after a block is broken the tool takes the blocks of its area too ({@link ToolType#aoe}; the sledge
	 * hammer's Wide Swing is the 3x3 face). They're broken as the player would (drops, wear, xp), and only the ones the tool
	 * is right for. Sneak to break just one.
	 */
	private static void areaBreak(ServerLevel level, ServerPlayer player, BlockPos center, BlockState centerState) {
		ItemStack held = player.getMainHandItem();
		ToolStats.Stats stats = ToolStats.isBroken(held) ? null : ToolStats.stats(held);
		if (stats == null || player.isShiftKeyDown() || !(held.getItem() instanceof TinkerToolItem tool)) {
			return;
		}
		// a tool with no area of its own but Wide Swing breaks the 3x3 face (the sledge hammer), as it always has
		boolean legacy = tool.type.aoe.kind() == ToolType.Aoe.Kind.NONE;
		ToolType.Aoe aoe = legacy ? ToolType.Aoe.FACE_3X3 : tool.type.aoe;
		if (legacy && stats.level(Trait.AREA) <= 0) {
			return;
		}
		Direction face = FACES.getOrDefault(player.getUUID(), Direction.UP);
		float hardness = centerState.getDestroySpeed(level, center);
		boolean plants = stats.level(Trait.HARVEST) > 0;
		ToolAreas.Area area = ToolAreas.blocks(level, player, aoe, center, centerState, face);
		boolean whole = area.whole();
		sweeping = true;
		try {
			for (BlockPos pos : area.blocks()) {
				BlockState state = level.getBlockState(pos);
				if (state.isAir() || ToolStats.isBroken(held)) {
					continue;
				}
				float h = state.getDestroySpeed(level, pos);
				boolean fits;
				if (legacy) {
					fits = h >= 0 && h <= hardness + AREA_HARDNESS_SLACK && held.isCorrectToolForDrops(state);
				} else if (whole) {
					fits = h >= 0 && held.isCorrectToolForDrops(state);
				} else {
					// the area takes what the tool is made for, never a chest or a half-grown crop
					fits = h >= 0 && h <= hardness + AREA_HARDNESS_SLACK && !state.hasBlockEntity() && held.isCorrectToolForDrops(state)
						&& (tool.type.mines.stream().anyMatch(state::is) || plants && h == 0.0F)
						&& (!plants || !ToolActions.crop(state) || ToolActions.matureCrop(state));
				}
				if (fits) {
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
