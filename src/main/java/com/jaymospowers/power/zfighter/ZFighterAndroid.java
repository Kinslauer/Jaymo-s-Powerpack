package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.chrono.future.FutureMachines;
import com.jaymospowers.power.chrono.future.FutureMech;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.50, Andy's NOEA picks) The Android upgrade: a mad scientist rebuilds a Z Fighter who has broken through to Super.
 * He sets up shop now and then near such a fighter ({@link #SPAWN_EVERY}: a wandering tinkerer, tagged {@link #TAG},
 * who leaves after {@link #LIFETIME} ticks; right-click him). Four steps, kept in {@link #STAGE}:
 * <ol>
 * <li>he asks for <b>parts</b> ({@link #PARTS}: iron, redstone blocks, diamonds, lightning rods, copper);</li>
 * <li>he wheels out a <b>test unit</b> (a glowing android mech from the future, {@link #TEST_HEALTH} health, {@link #TEST_DAMAGE} a blow,
 * set on you): beat it before {@link #TEST_LIFE} ticks run out and without going further than {@link #TEST_LEASH} from
 * him, or he builds another (free);</li>
 * <li>the <b>operation</b> ({@link #OPERATION_TICKS}): you lie held in place, sparks, a few hearts of pain, and he must
 * not be left;</li>
 * <li>you are rebuilt.</li>
 * </ol>
 * After it, for good (0.17.93, Andy: "infinite ki and no aura, no other debuff"): his ki never runs out (every cost is
 * free and the bar stays full: techniques, flight, guarding, forms), he shows no aura, and two android forms open past
 * the top of his ladder ({@link ZFighterBranchForms#OVERDRIVE} at once, {@link ZFighterBranchForms#REACTOR}
 * after time in it). Saved, kept through death. {@link #undo} takes it all back (a wish, later).
 */
public final class ZFighterAndroid {
	/** Quest steps: 0 not begun, 1 asked for parts, 2 test fight, 3 operation, {@link #DONE} rebuilt. */
	static final int PARTS_STAGE = 1;
	static final int TEST_STAGE = 2;
	static final int OPERATION_STAGE = 3;
	public static final int DONE = 4;

	/** An android's ki costs (times): nothing, his ki is endless. */
	static final float COST = 0.0F;

	/** The parts: what he asks for, and how many. */
	static final Item[] PARTS = {Items.IRON_INGOT, Items.REDSTONE_BLOCK, Items.DIAMOND, Items.LIGHTNING_ROD.asList().get(0), Items.COPPER_INGOT};
	static final int[] PARTS_COUNT = {32, 8, 6, 2, 16};

	/** The scientist: checks (ticks), the odds (1 in this) per fighter per check, how long he stays, where he sets up (blocks away). */
	static final int SPAWN_EVERY = 400;
	static final int SPAWN_ODDS = 4;
	static final long LIFETIME = 36000L;
	static final double SPAWN_MIN = 20.0, SPAWN_MAX = 36.0;
	/** No second scientist within this far (blocks) of a fighter. */
	static final double ALONE = 160.0;
	/** The test unit: health, damage a blow, how long it runs (ticks), and how far from the scientist you may go (blocks). */
	static final double TEST_HEALTH = 100.0;
	static final double TEST_DAMAGE = 8.0;
	/** (0.17.135) The test mech's plating: lighter than a wild one's, since it also fires its arm guns. */
	static final double TEST_ARMOR = 4.0;
	static final int TEST_LIFE = 4800;
	static final double TEST_LEASH = 64.0;
	/** The operation: ticks, hearts of pain (half-hearts total), how near he must stay (blocks), health he needs to begin. */
	static final int OPERATION_TICKS = 200;
	static final float OPERATION_PAIN = 6.0F;
	static final double OPERATION_NEAR = 6.0;
	static final float OPERATION_HEALTH = 12.0F;
	static final int COLOR = 0x40E0F0;

	public static final String TAG = "jaymospowers_scientist";
	static final String UNTIL_TAG = "jaymospowers_scientist_until_";
	static final String TEST_TAG = "jaymospowers_test_unit";

	/** How far along the questline he is; {@link #DONE} is an android. Everyone sees it (the look). Saved, kept through death. */
	public static final AttachmentType<Integer> STAGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_android_stage"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_android_speed");

	/** An operation under way. */
	private static final class Operation {
		final UUID scientist;
		int ticks;
		float pain;

		Operation(UUID scientist) {
			this.scientist = scientist;
		}
	}

	private static final Map<UUID, Operation> OPERATIONS = new HashMap<>();
	/** Test unit → the fighter it is set on, and the fighter → his unit. */
	private static final Map<UUID, UUID> TEST_OWNERS = new HashMap<>();
	private static final Map<UUID, UUID> TEST_UNITS = new HashMap<>();
	private static final Map<UUID, Long> TEST_STARTS = new HashMap<>();

	private ZFighterAndroid() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterAndroid::tickServer);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!entity.entityTags().contains(TAG)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
				talk(server, entity);
			}
			// (the trade window never opens: he doesn't sell)
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			UUID owner = TEST_OWNERS.get(entity.getUUID());
			if (owner != null && entity.level() instanceof ServerLevel level && level.getServer().getPlayerList().getPlayer(owner) != null) {
				passed(level.getServer().getPlayerList().getPlayer(owner), entity.getUUID());
			}
		});
		// (0.17.93) he doesn't let you die on his table: whatever would kill you mid-operation leaves you at the edge, and he
		// brings you round when he's done (rebuilt)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && OPERATIONS.containsKey(player.getUUID())) {
				player.setHealth(1.0F);
				return false;
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OPERATIONS.clear();
			TEST_OWNERS.clear();
			TEST_UNITS.clear();
			TEST_STARTS.clear();
		});
	}

	// ---------------------------------------------------------------- reading

	public static int stage(Player player) {
		return player.getAttachedOrElse(STAGE, 0);
	}

	/** He has been rebuilt (and still has the power). */
	public static boolean android(Player player) {
		return stage(player) >= DONE && ZFighterBranchForms.fighter(player);
	}

	static float costFactor(ServerPlayer player) {
		return android(player) ? COST : 1.0F;
	}

	static void setStage(ServerPlayer player, int stage) {
		if (stage <= 0) {
			player.removeAttached(STAGE);
		} else {
			player.setAttached(STAGE, stage);
		}
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	/**
	 * Undoes the upgrade: back to flesh (a wish for it later; nothing calls this yet). The android forms close and he steps
	 * out of one.
	 */
	public static void undo(ServerPlayer player) {
		player.removeAttached(STAGE);
		OPERATIONS.remove(player.getUUID());
		int branch = ZFighterBranchForms.of(player);
		if (branch == ZFighterBranchForms.OVERDRIVE || branch == ZFighterBranchForms.REACTOR) {
			player.removeAttached(ZFighterBranchForms.BRANCH);
		}
		int open = player.getAttachedOrElse(ZFighterBranchForms.OPEN, 0) & ~(1 << ZFighterBranchForms.OVERDRIVE - ZFighterBranchForms.FIRST
			| 1 << ZFighterBranchForms.REACTOR - ZFighterBranchForms.FIRST);
		player.setAttached(ZFighterBranchForms.OPEN, open);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_android_undone").withStyle(ChatFormatting.GRAY));
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	static void clear(ServerPlayer player) {
		player.removeAttached(STAGE);
		OPERATIONS.remove(player.getUUID());
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** An android's body runs a touch lighter: refreshed with the rest of his attributes. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, android(player) ? 0.02 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Every tick of the power: an android's ki is always full, and (0.17.93) it never needs to eat. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (android(player)) {
			live.ki = ZFighterPower.maxKi(player);
			player.getFoodData().setFoodLevel(20);
		}
	}

	// ---------------------------------------------------------------- the scientist

	private static void tickServer(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % SPAWN_EVERY == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator() && eligible(player) && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
					&& player.getRandom().nextInt(SPAWN_ODDS) == 0) {
					summon(player);
				}
			}
		}
		if (now % 200 == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity entity : level.getAllEntities()) {
					if (entity.entityTags().contains(TAG) && expired(entity, now) && !operating(entity)) {
						level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0, entity.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
						entity.discard();
					}
				}
			}
		}
		operations(server);
		if (now % 20 == 0) {
			tests(server, now);
		}
	}

	/** A Z Fighter past the breakthrough who is not yet rebuilt: the scientist comes looking for him. */
	private static boolean eligible(ServerPlayer player) {
		return ZFighterBranchForms.fighter(player) && Fate.isAscended(player) && stage(player) < DONE;
	}

	private static boolean expired(Entity entity, long now) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(UNTIL_TAG)) {
				try {
					return now > Long.parseLong(tag.substring(UNTIL_TAG.length()));
				} catch (NumberFormatException e) {
					return true;
				}
			}
		}
		return true;
	}

	private static boolean operating(Entity scientist) {
		for (Operation operation : OPERATIONS.values()) {
			if (operation.scientist.equals(scientist.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** Sets a scientist up near {@code player}, unless one is already about. False if there was no room. */
	static boolean summon(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!level.getEntitiesOfClass(WanderingTrader.class, player.getBoundingBox().inflate(ALONE), e -> e.entityTags().contains(TAG)).isEmpty()) {
			return false;
		}
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0.0, player.getZ() + Math.sin(angle) * distance);
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				continue;
			}
			WanderingTrader npc = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
			if (npc == null) {
				return false;
			}
			npc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			npc.setNoAi(true);
			npc.setPermanentlyInvulnerable(true);
			npc.setPersistenceRequired();
			npc.setDespawnDelay(0);
			npc.addTag(TAG);
			npc.addTag(UNTIL_TAG + (level.getGameTime() + LIFETIME));
			npc.setCustomName(Component.translatable("entity.jaymospowers.scientist"));
			npc.setCustomNameVisible(true);
			npc.getOffers().clear();
			level.addFreshEntity(npc);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, npc.getX(), npc.getY() + 1.2, npc.getZ(), 20, 0.4, 0.8, 0.4, 0.2);
			level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0F, 1.8F);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_here", npc.getBlockX(), npc.getBlockZ())
				.withStyle(ChatFormatting.AQUA));
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- talking

	private static void talk(ServerPlayer player, Entity scientist) {
		Component name = scientist.getDisplayName();
		if (!ZFighterBranchForms.fighter(player)) {
			say(player, name, "message.jaymospowers.zfighter_scientist_no_spark");
			return;
		}
		if (OPERATIONS.containsKey(player.getUUID())) {
			return;
		}
		switch (stage(player)) {
			case 0 -> {
				if (!Fate.isAscended(player)) {
					say(player, name, "message.jaymospowers.zfighter_scientist_not_ready");
					return;
				}
				player.setAttached(STAGE, PARTS_STAGE);
				say(player, name, "message.jaymospowers.zfighter_scientist_offer");
				player.sendSystemMessage(partsList(player).withStyle(ChatFormatting.AQUA));
			}
			case PARTS_STAGE -> {
				if (!hasParts(player)) {
					say(player, name, "message.jaymospowers.zfighter_scientist_parts_missing");
					player.sendSystemMessage(partsList(player).withStyle(ChatFormatting.AQUA));
					return;
				}
				takeParts(player);
				player.setAttached(STAGE, TEST_STAGE);
				say(player, name, "message.jaymospowers.zfighter_scientist_parts_ok");
				startTest(player, scientist);
			}
			case TEST_STAGE -> {
				if (TEST_UNITS.containsKey(player.getUUID())) {
					say(player, name, "message.jaymospowers.zfighter_scientist_test_running");
				} else {
					say(player, name, "message.jaymospowers.zfighter_scientist_test_again");
					startTest(player, scientist);
				}
			}
			case OPERATION_STAGE -> operate(player, scientist);
			default -> say(player, name, "message.jaymospowers.zfighter_scientist_done");
		}
	}

	private static void say(ServerPlayer player, Component name, String key) {
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_says", name, Component.translatable(key))
			.withStyle(ChatFormatting.AQUA));
	}

	/** "32 Iron Ingot, 8 Block of Redstone ...", marked with what is still missing. */
	private static MutableComponent partsList(ServerPlayer player) {
		MutableComponent list = Component.empty();
		for (int i = 0; i < PARTS.length; i++) {
			int have = count(player, PARTS[i]);
			if (i > 0) {
				list.append(Component.literal(", "));
			}
			list.append(Component.literal(Math.min(have, PARTS_COUNT[i]) + "/" + PARTS_COUNT[i] + " ")).append(new ItemStack(PARTS[i]).getHoverName());
		}
		return list;
	}

	private static int count(ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean hasParts(ServerPlayer player) {
		if (player.isCreative()) {
			return true;
		}
		for (int i = 0; i < PARTS.length; i++) {
			if (count(player, PARTS[i]) < PARTS_COUNT[i]) {
				return false;
			}
		}
		return true;
	}

	private static void takeParts(ServerPlayer player) {
		if (player.isCreative()) {
			return;
		}
		for (int part = 0; part < PARTS.length; part++) {
			int left = PARTS_COUNT[part];
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(PARTS[part])) {
					int take = Math.min(left, stack.getCount());
					stack.shrink(take);
					left -= take;
				}
			}
		}
	}

	// ---------------------------------------------------------------- the test fight

	private static void startTest(ServerPlayer player, Entity scientist) {
		ServerLevel level = player.level();
		// (0.17.135) an android mech from the future, not an iron golem
		FutureMech unit = FutureMachines.ANDROID_MECH.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (unit == null) {
			return;
		}
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 side = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			BlockPos.containing(player.getX() + side.x * 7.0, 0.0, player.getZ() + side.z * 7.0));
		unit.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		var health = unit.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(TEST_HEALTH);
		}
		var armor = unit.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.setBaseValue(TEST_ARMOR);
		}
		var damage = unit.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(TEST_DAMAGE);
		}
		unit.setHealth((float)TEST_HEALTH);
		unit.setCustomName(Component.translatable("entity.jaymospowers.test_unit"));
		unit.setCustomNameVisible(true);
		unit.setGlowingTag(true);
		unit.setPersistenceRequired();
		unit.addTag(TEST_TAG);
		unit.setTarget(player);
		level.addFreshEntity(unit);
		TEST_OWNERS.put(unit.getUUID(), player.getUUID());
		TEST_UNITS.put(player.getUUID(), unit.getUUID());
		TEST_STARTS.put(player.getUUID(), level.getGameTime());
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, unit.getX(), unit.getY() + 1.5, unit.getZ(), 30, 0.5, 1.0, 0.5, 0.3);
		level.playSound(null, unit.getX(), unit.getY(), unit.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.5F, 0.6F);
		level.playSound(null, unit.getX(), unit.getY(), unit.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2F, 1.4F);
	}

	/** Once a second: keep each unit on its fighter, and end the test when it is won, lost or abandoned. */
	private static void tests(MinecraftServer server, long now) {
		for (Iterator<Map.Entry<UUID, UUID>> it = TEST_UNITS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, UUID> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			Entity unit = null;
			if (owner != null) {
				unit = owner.level().getEntity(entry.getValue());
			}
			boolean over = owner == null || unit == null || !unit.isAlive() || !owner.isAlive()
				|| now - TEST_STARTS.getOrDefault(entry.getKey(), now) > TEST_LIFE || unit.distanceToSqr(owner) > TEST_LEASH * TEST_LEASH;
			if (over) {
				if (unit != null && unit.isAlive()) {
					unit.discard();
				}
				TEST_OWNERS.remove(entry.getValue());
				TEST_STARTS.remove(entry.getKey());
				it.remove();
				if (owner != null && stage(owner) == TEST_STAGE) {
					owner.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_test_failed").withStyle(ChatFormatting.AQUA));
				}
				continue;
			}
			if (unit instanceof Mob mob && mob.getTarget() != owner) {
				mob.setTarget(owner);
			}
			if (unit != null && com.jaymospowers.core.wounds.Downed.isDowned(unit)) {
				// (a downed unit is a beaten one)
				passed(owner, unit.getUUID());
				unit.discard();
				it.remove();
			}
		}
	}

	private static void passed(ServerPlayer player, UUID unit) {
		TEST_OWNERS.remove(unit);
		TEST_UNITS.remove(player.getUUID());
		TEST_STARTS.remove(player.getUUID());
		if (stage(player) != TEST_STAGE) {
			return;
		}
		player.setAttached(STAGE, OPERATION_STAGE);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_test_passed").withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ---------------------------------------------------------------- the operation

	private static void operate(ServerPlayer player, Entity scientist) {
		if (player.getHealth() < OPERATION_HEALTH && !player.isCreative()) {
			say(player, scientist.getDisplayName(), "message.jaymospowers.zfighter_scientist_too_weak");
			return;
		}
		say(player, scientist.getDisplayName(), "message.jaymospowers.zfighter_scientist_operating");
		OPERATIONS.put(player.getUUID(), new Operation(scientist.getUUID()));
	}

	private static void operations(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Operation>> it = OPERATIONS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Operation> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Operation operation = entry.getValue();
			Entity scientist = player == null ? null : player.level().getEntity(operation.scientist);
			if (player == null || !player.isAlive() || scientist == null || !scientist.isAlive() || scientist.distanceToSqr(player) > OPERATION_NEAR * OPERATION_NEAR) {
				it.remove();
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_scientist_interrupted").withStyle(ChatFormatting.AQUA));
				}
				continue;
			}
			ServerLevel level = player.level();
			operation.ticks++;
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 10, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 5, 128, false, false));
			if (operation.ticks % 2 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.8, 0.4, 0.25);
			}
			if (operation.ticks % 20 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.5F);
				float pain = Math.min(OPERATION_PAIN / (OPERATION_TICKS / 20), Math.max(0.0F, player.getHealth() - 2.0F));
				if (pain > 0.0F && !player.isCreative()) {
					player.hurtServer(level, level.damageSources().magic(), pain);
				}
			}
			if (operation.ticks >= OPERATION_TICKS) {
				it.remove();
				rebuilt(player);
			}
		}
	}

	/** The operation is done: he is an android. */
	private static void rebuilt(ServerPlayer player) {
		ServerLevel level = player.level();
		player.setAttached(STAGE, DONE);
		ZFighterPower.Live live = ZFighterPower.live(player);
		// (0.17.93) he brings you round: up off the table if you went down, whole, and none of the operation's weight left on you
		if (Downed.isDowned(player)) {
			Downed.undo(level, player);
		}
		player.removeEffect(MobEffects.SLOWNESS);
		player.removeEffect(MobEffects.JUMP_BOOST);
		player.setHealth(player.getMaxHealth());
		live.ki = ZFighterPower.maxKi(player);
		ZFighterBranchForms.grant(player, ZFighterBranchForms.OVERDRIVE);
		ZFighterPower.refresh(player, live);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.sendParticles(ParticleTypes.END_ROD, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_android_done").withStyle(ChatFormatting.AQUA));
	}
}
