package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>Five trials, then the master</b>: an old vigilante who outlived his rivals tests the young. He leaves five trial
 * chests across the land (set out round the world spawn the first time a player nears each: {@link #SITES}), each a
 * different trial, opened by using it: a <b>timed run</b> to a marker, a <b>target range</b> of five targets to hit with
 * arrows, a <b>wave of foes</b> to fell, a <b>fall to live through</b> from a platform high above, and a <b>quiet theft</b>
 * from a sleeping guard's strongbox. Each player's finished trials are saved ({@link #DONE}). Whoever has finished all
 * five finds the master waiting by the last chest: a masked old fighter with a boss bar, who duels them himself. Beat
 * him and he leaves a relic and Strength experience. Nothing on screen says who he is or why.
 */
public final class VigilanteTrials {
	/** How many chests, and how far from the world spawn they are set (blocks), and how near a player must be to set one out. */
	static final int COUNT = 5;
	static final double SPAWN_FROM_NEAR = 350.0, SPAWN_FROM_FAR = 700.0, SET_OUT_RANGE = 80.0;
	/** The timed run: how far the marker is (blocks), its time (ticks) and the reach that counts as there. */
	static final double RUN_DISTANCE = 44.0, RUN_REACH = 2.5;
	static final int RUN_TICKS = 700;
	/** The target range: targets, the ring they stand on (blocks), time (ticks), and the arrows given if you carry too few. */
	static final int TARGETS = 5, RANGE_TICKS = 1800, RANGE_ARROWS = 12;
	static final double TARGET_NEAR = 7.0, TARGET_FAR = 12.0;
	/** The wave: how many foes, how far from the chest they come (blocks), its time (ticks), how far you may stray (blocks). */
	static final int WAVE_TICKS = 3000;
	static final double WAVE_RING = 8.0, WAVE_STRAY = 40.0;
	/** The fall: the platform's height above the chest (blocks), the drop that counts, the time (ticks) and health needed to begin. */
	static final int FALL_HEIGHT = 20, FALL_TICKS = 1200;
	static final double FALL_COUNTS = 12.0;
	static final float FALL_HEALTH = 20.0F;
	/** The theft: where the camp is (blocks from the chest), its time (ticks), the guard's hearing for a sprint, a step and a creep (blocks), and how long noise wakes him (ticks). */
	static final double CAMP_FROM = 14.0, HEAR_SPRINT = 9.0, HEAR_WALK = 4.5, HEAR_SNEAK = 2.0;
	static final int THEFT_TICKS = 2400, WAKES_AFTER = 24;
	/** The master: his health, his blow, his armour and his speed over a man's; the reward's Strength experience; how far from his chest the duel may roam. */
	static final double MASTER_HEALTH = 160.0, MASTER_DAMAGE = 9.0, MASTER_ARMOUR = 12.0, MASTER_SPEED = 0.25;
	static final int REWARD_XP = 600;
	static final double MASTER_LEASH = 64.0;

	public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, COUNT - 1);
	/** Each player's finished trials: bit {@code i} for trial {@code i}. Saved. */
	static final AttachmentType<Integer> DONE = AttachmentRegistry.create(JaymosKingdoms.id("trials_done"), b -> b.persistent(Codec.INT));
	/** Whether this player has beaten the master. Saved. */
	static final AttachmentType<Boolean> BEATEN = AttachmentRegistry.create(JaymosKingdoms.id("trials_beaten"), b -> b.persistent(Codec.BOOL));
	/** On the Overworld: where the chests go, as "x;z;kind;placed". Saved. */
	static final AttachmentType<List<String>> SITES = AttachmentRegistry.create(JaymosKingdoms.id("trials_sites"),
		b -> b.persistent(Codec.STRING.listOf()));

	/** Whether this player has been handed the range's bow and arrows. Saved. */
	static final AttachmentType<Boolean> RANGE_GIVEN = AttachmentRegistry.create(JaymosKingdoms.id("trials_range_given"), b -> b.persistent(Codec.BOOL));
	/** Put on every foe, guard and master a trial spawns, so any left in the world from before is gone on load. */
	static final String TAG = "jaymoskingdoms_trial";

	public static Block CHEST;
	public static Item CHEST_ITEM;

	/** One trial under way: whose, which, where its chest is, and what it has set out. */
	private static final class Run {
		final UUID who;
		final int kind;
		final BlockPos chest;
		final long end;
		final List<BlockPos> blocks = new ArrayList<>();
		final List<UUID> foes = new ArrayList<>();
		@Nullable BlockPos goal;
		@Nullable UUID guard;
		@Nullable BlockPos strongbox;
		int platformY, noise;
		boolean lost;
		double fell;

		Run(UUID who, int kind, BlockPos chest, long end) {
			this.who = who;
			this.kind = kind;
			this.chest = chest;
			this.end = end;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	/** The masters out now, by the player they fight, and their bars. */
	private static final Map<UUID, UUID> MASTERS = new HashMap<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private VigilanteTrials() {
	}

	/** Whether {@code id} is a foe, guard or master of a trial under way. */
	private static boolean live(UUID id) {
		return MASTERS.containsValue(id) || RUNS.values().stream().anyMatch(r -> r.foes.contains(id) || id.equals(r.guard));
	}

	/** A trial chest: its kind in its blockstate. */
	private static final class TrialChest extends Block {
		TrialChest(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(this.stateDefinition.any().setValue(KIND, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(KIND);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				open(server, sp, pos, state.getValue(KIND));
			}
			return InteractionResult.SUCCESS;
		}
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("trial_chest"));
		CHEST = Registry.register(BuiltInRegistries.BLOCK, key, new TrialChest(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).sound(SoundType.IRON).strength(-1.0F, 3600000.0F).noLootTable()));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("trial_chest"));
		CHEST_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(CHEST, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.OP_BLOCKS).register(output -> output.accept(CHEST_ITEM));
		ServerTickEvents.END_SERVER_TICK.register(VigilanteTrials::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
				Run run = RUNS.get(sp.getUUID());
				if (run != null && run.kind == 4 && run.strongbox != null && run.strongbox.equals(hit.getBlockPos())) {
					finish(sp, run, true);
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			for (Map.Entry<UUID, UUID> e : MASTERS.entrySet()) {
				if (e.getValue().equals(entity.getUUID()) && entity.level() instanceof ServerLevel level) {
					won(level, entity, e.getKey());
					break;
				}
			}
		});
		// (a trial under way when the server stops is taken down, so nothing of it is left in the world)
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel level = Factions.level(server);
			for (Run run : RUNS.values()) {
				clean(level, run, server.getPlayerList().getPlayer(run.who));
			}
			for (UUID id : MASTERS.values()) {
				Entity master = level.getEntity(id);
				if (master != null) {
					master.discard();
				}
			}
		});
		// (a trial's foe, guard or master left in the world from before is gone)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !live(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RUNS.clear();
			MASTERS.clear();
			for (ServerBossEvent bar : BARS.values()) {
				bar.removeAllPlayers();
			}
			BARS.clear();
		});
	}

	// ---- setting the chests out --------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 40 == 0) {
			setOut(level);
		}
		if (RUNS.isEmpty() && MASTERS.isEmpty()) {
			return;
		}
		for (Iterator<Run> it = RUNS.values().iterator(); it.hasNext();) {
			Run run = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(run.who);
			if (player == null || !player.isAlive() || player.level() != level || now > run.end || run.lost) {
				clean(level, run, player);
				if (player != null) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.failed"));
				}
				it.remove();
				continue;
			}
			if (step(level, player, run, now)) {
				it.remove();
				clean(level, run, player);
				pass(level, player, run);
			}
		}
		masters(server, level);
	}

	/** The chests' sites are fixed the first time anyone is about, then each is set out when a player comes near. */
	private static void setOut(ServerLevel level) {
		List<String> sites = new ArrayList<>(level.getAttachedOrElse(SITES, List.of()));
		if (sites.isEmpty()) {
			if (level.players().isEmpty()) {
				return;
			}
			RandomSource random = level.getRandom();
			BlockPos spawn = level.getRespawnData().pos();
			double turn = random.nextDouble() * Math.PI * 2.0;
			for (int i = 0; i < COUNT; i++) {
				double angle = turn + i * Math.PI * 2.0 / COUNT + (random.nextDouble() - 0.5) * 0.5;
				double r = SPAWN_FROM_NEAR + random.nextDouble() * (SPAWN_FROM_FAR - SPAWN_FROM_NEAR);
				sites.add((spawn.getX() + (int)(Math.cos(angle) * r)) + ";" + (spawn.getZ() + (int)(Math.sin(angle) * r)) + ";" + i + ";0");
			}
			level.setAttached(SITES, List.copyOf(sites));
			return;
		}
		boolean changed = false;
		for (int i = 0; i < sites.size(); i++) {
			String[] f = sites.get(i).split(";");
			if (f.length < 4 || f[3].equals("1")) {
				continue;
			}
			int x = Integer.parseInt(f[0]), z = Integer.parseInt(f[1]);
			for (ServerPlayer p : level.players()) {
				double dx = p.getX() - x, dz = p.getZ() - z;
				if (dx * dx + dz * dz < SET_OUT_RANGE * SET_OUT_RANGE && level.hasChunkAt(new BlockPos(x, 64, z))) {
					set(level, x, z, Integer.parseInt(f[2]));
					sites.set(i, f[0] + ";" + f[1] + ";" + f[2] + ";1");
					changed = true;
					break;
				}
			}
		}
		if (changed) {
			level.setAttached(SITES, List.copyOf(sites));
		}
	}

	/** A chest of kind {@code kind} on a small mossy footing at the surface of {@code x}, {@code z}. */
	private static void set(ServerLevel level, int x, int z, int kind) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 64, z));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos under = top.offset(dx, -1, dz);
				level.setBlock(under, (dx + dz) % 2 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), 3);
			}
		}
		level.setBlock(top, CHEST.defaultBlockState().setValue(KIND, kind), 3);
		JaymosKingdoms.LOGGER.debug("trial chest {} set out at {}", kind, top);
	}

	// ---- opening a chest ---------------------------------------------------------------------------------------------

	private static void open(ServerLevel level, ServerPlayer player, BlockPos pos, int kind) {
		if (RUNS.containsKey(player.getUUID())) {
			return;
		}
		int done = player.getAttachedOrElse(DONE, 0);
		if (done == (1 << COUNT) - 1) {
			if (!player.getAttachedOrElse(BEATEN, false) && !MASTERS.containsKey(player.getUUID())) {
				master(level, player, pos);
			}
			return;
		}
		if ((done & (1 << kind)) != 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.done"));
			return;
		}
		Run run = new Run(player.getUUID(), kind, pos, level.getGameTime() + ticks(kind));
		if (!begin(level, player, run)) {
			clean(level, run, player);
			return;
		}
		RUNS.put(player.getUUID(), run);
		level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	private static int ticks(int kind) {
		return switch (kind) {
			case 0 -> RUN_TICKS;
			case 1 -> RANGE_TICKS;
			case 2 -> WAVE_TICKS;
			case 3 -> FALL_TICKS;
			default -> THEFT_TICKS;
		};
	}

	/** Sets out trial {@code run}; false (with a word to the player) if it can't begin. */
	private static boolean begin(ServerLevel level, ServerPlayer player, Run run) {
		RandomSource random = level.getRandom();
		BlockPos c = run.chest;
		switch (run.kind) {
			case 0 -> {
				for (int i = 0; i < 8 && run.goal == null; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0;
					BlockPos column = c.offset((int)(Math.cos(angle) * RUN_DISTANCE), 0, (int)(Math.sin(angle) * RUN_DISTANCE));
					if (level.hasChunkAt(column)) {
						BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
						if (level.getFluidState(at.below()).isEmpty()) {
							run.goal = at;
						}
					}
				}
				if (run.goal == null) {
					return false;
				}
			}
			case 1 -> {
				for (int i = 0; i < TARGETS; i++) {
					double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2.0 / TARGETS, r = TARGET_NEAR + random.nextDouble() * (TARGET_FAR - TARGET_NEAR);
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset((int)(Math.cos(angle) * r), 0, (int)(Math.sin(angle) * r)));
					at = at.above();
					if (level.getBlockState(at).isAir()) {
						level.setBlock(at, Blocks.TARGET.defaultBlockState(), 3);
						run.blocks.add(at);
					}
				}
				if (run.blocks.size() < TARGETS) {
					return false;
				}
				if (!player.getAttachedOrElse(RANGE_GIVEN, false)) {
					player.setAttached(RANGE_GIVEN, true);
					int arrows = 0;
					boolean bow = false;
					for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
						arrows += s.is(Items.ARROW) ? s.getCount() : 0;
						bow |= s.is(Items.BOW);
					}
					if (!bow) {
						give(player, new ItemStack(Items.BOW));
					}
					if (arrows < RANGE_ARROWS) {
						give(player, new ItemStack(Items.ARROW, RANGE_ARROWS - arrows));
					}
				}
			}
			case 2 -> {
				List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SKELETON,
					EntityTypes.SKELETON, EntityTypes.VINDICATOR);
				for (EntityType<? extends Mob> type : kinds) {
					double angle = random.nextDouble() * Math.PI * 2.0;
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset((int)(Math.cos(angle) * WAVE_RING), 0, (int)(Math.sin(angle) * WAVE_RING)));
					Mob mob = type.spawn(level, at, EntitySpawnReason.EVENT);
					if (mob != null) {
						mob.setPersistenceRequired();
						mob.addTag(TAG);
						mob.setTarget(player);
						run.foes.add(mob.getUUID());
					}
				}
				if (run.foes.isEmpty()) {
					return false;
				}
			}
			case 3 -> {
				if (player.getHealth() < FALL_HEALTH || player.getAbilities().flying) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.unready"));
					return false;
				}
				run.platformY = Math.min(level.getMaxY() - 3, c.getY() + FALL_HEIGHT);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						BlockPos at = new BlockPos(c.getX() + dx, run.platformY, c.getZ() + dz);
						if (level.getBlockState(at).isAir()) {
							level.setBlock(at, Blocks.STONE_BRICKS.defaultBlockState(), 3);
							run.blocks.add(at);
						}
					}
				}
				player.teleportTo(level, c.getX() + 0.5, run.platformY + 1.0, c.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, false);
				player.fallDistance = 0.0;
			}
			default -> {
				if (!camp(level, run)) {
					return false;
				}
			}
		}
		return true;
	}

	/** A red bed (coloured blocks have no Blocks fields). */
	private static BlockState bed() {
		return BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.parse("minecraft:red_bed")).orElse(Blocks.OAK_PLANKS).defaultBlockState();
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	/** The guard's camp: a bed with the guard asleep in it and a strongbox a few paces off, out of sight of the chest. */
	private static boolean camp(ServerLevel level, Run run) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 8; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			BlockPos column = run.chest.offset((int)(Math.cos(angle) * CAMP_FROM), 0, (int)(Math.sin(angle) * CAMP_FROM));
			if (!level.hasChunkAt(column)) {
				continue;
			}
			BlockPos foot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			Direction way = Direction.from2DDataValue(random.nextInt(4));
			BlockPos head = foot.relative(way), box = foot.relative(way.getClockWise(), 4);
			if (!level.getFluidState(foot.below()).isEmpty() || !level.getBlockState(foot).isAir() || !level.getBlockState(head).isAir() || !level.getBlockState(box).isAir()) {
				continue;
			}
			level.setBlock(foot, bed().setValue(BedBlock.FACING, way).setValue(BedBlock.PART, BedPart.FOOT), 3);
			level.setBlock(head, bed().setValue(BedBlock.FACING, way).setValue(BedBlock.PART, BedPart.HEAD), 3);
			level.setBlock(box, Blocks.CHEST.defaultBlockState(), 3);
			run.blocks.add(foot);
			run.blocks.add(head);
			run.blocks.add(box);
			run.strongbox = box;
			Villager guard = EntityTypes.VILLAGER.spawn(level, head, EntitySpawnReason.EVENT);
			if (guard == null) {
				return false;
			}
			guard.setCustomName(Component.translatable("entity.jaymoskingdoms.trial_guard"));
			guard.addTag(TAG);
			guard.setNoAi(true);
			guard.setPersistenceRequired();
			guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			guard.startSleeping(head);
			run.guard = guard.getUUID();
			return true;
		}
		return false;
	}

	// ---- each trial's turn -------------------------------------------------------------------------------------------

	/** One tick of {@code run}; true once it is won. */
	private static boolean step(ServerLevel level, ServerPlayer player, Run run, long now) {
		long left = Math.max(0L, run.end - now);
		if (now % 20 == 0 && run.kind != 2) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.time", left / 20));
		}
		switch (run.kind) {
			case 0 -> {
				BlockPos g = run.goal;
				if (now % 5 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, g.getX() + 0.5, g.getY() + 1.0, g.getZ() + 0.5, 6, 0.2, 2.0, 0.2, 0.01);
				}
				double dx = player.getX() - (g.getX() + 0.5), dz = player.getZ() - (g.getZ() + 0.5);
				return dx * dx + dz * dz <= RUN_REACH * RUN_REACH && Math.abs(player.getY() - g.getY()) <= 3.0;
			}
			case 1 -> {
				AABB near = new AABB(run.chest).inflate(TARGET_FAR + 20.0);
				for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, near, a -> a.getOwner() == player)) {
					AABB reach = arrow.getBoundingBox().inflate(0.35);
					for (Iterator<BlockPos> it = run.blocks.iterator(); it.hasNext();) {
						BlockPos t = it.next();
						if (reach.intersects(new AABB(t))) {
							it.remove();
							level.sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
							level.playSound(null, t, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 1.0F + 0.15F * (TARGETS - run.blocks.size()));
							level.setBlock(t, Blocks.AIR.defaultBlockState(), 3);
						}
					}
				}
				return run.blocks.isEmpty();
			}
			case 2 -> {
				run.foes.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
				if (player.distanceToSqr(Vec3.atCenterOf(run.chest)) > WAVE_STRAY * WAVE_STRAY) {
					run.foes.forEach(id -> {
						Entity e = level.getEntity(id);
						if (e != null) {
							e.discard();
						}
					});
					return false;
				}
				if (now % 20 == 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.foes", run.foes.size()));
				}
				return run.foes.isEmpty();
			}
			case 3 -> {
				run.fell = Math.max(run.fell, player.fallDistance);
				return player.onGround() && run.fell >= FALL_COUNTS && player.getY() < run.platformY - 4;
			}
			default -> {
				if (!(level.getEntity(run.guard) instanceof Villager guard) || !guard.isAlive()) {
					return false;
				}
				if (guard.getHealth() < guard.getMaxHealth() || !guard.isSleeping() && !guard.isNoAi()) {
					wake(guard, player, run);
					return false;
				}
				double d2 = player.distanceToSqr(guard), ear = player.isSprinting() ? HEAR_SPRINT : player.isCrouching() ? HEAR_SNEAK : HEAR_WALK;
				boolean still = player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
				run.noise = d2 < ear * ear && !still ? run.noise + 1 : Math.max(0, run.noise - 1);
				if (run.noise >= WAKES_AFTER) {
					wake(guard, player, run);
					return false;
				}
				return false;
			}
		}
	}

	/** The guard wakes: the trial is lost. */
	private static void wake(Villager guard, ServerPlayer player, Run run) {
		run.lost = true;
		guard.stopSleeping();
		guard.setNoAi(false);
		guard.level().playSound(null, guard.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.woke"));
	}

	/** Takes down everything {@code run} set out (and, for a fall, brings the player back to the ground). */
	private static void clean(ServerLevel level, Run run, @Nullable ServerPlayer player) {
		if (run.kind == 3 && player != null && player.isAlive() && player.getY() > run.chest.getY() + 6) {
			player.teleportTo(level, run.chest.getX() + 0.5, run.chest.getY() + 1.0, run.chest.getZ() + 2.5, java.util.Set.of(), player.getYRot(), 0.0F, false);
			player.fallDistance = 0.0;
		}
		for (BlockPos p : run.blocks) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.TARGET) || s.is(Blocks.STONE_BRICKS) || s.getBlock() instanceof BedBlock || s.is(Blocks.CHEST)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		for (UUID id : run.foes) {
			Entity e = level.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		if (run.guard != null && level.getEntity(run.guard) != null) {
			level.getEntity(run.guard).discard();
		}
	}

	private static void finish(ServerPlayer player, Run run, boolean won) {
		ServerLevel level = (ServerLevel)player.level();
		RUNS.remove(player.getUUID());
		clean(level, run, player);
		if (won) {
			pass(level, player, run);
		}
	}

	/** {@code player} has won trial {@code run.kind}. */
	private static void pass(ServerLevel level, ServerPlayer player, Run run) {
		int done = player.getAttachedOrElse(DONE, 0) | (1 << run.kind);
		player.setAttached(DONE, done);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
		int n = Integer.bitCount(done);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vigil.won", n, COUNT).withStyle(ChatFormatting.GOLD));
		if (n == COUNT) {
			master(level, player, run.chest);
		}
	}

	// ---- the master --------------------------------------------------------------------------------------------------

	/** The master steps out beside {@code chest} to meet {@code player}. */
	private static void master(ServerLevel level, ServerPlayer player, BlockPos chest) {
		Vec3 at = Vec3.atBottomCenterOf(chest.offset(3, 0, 0));
		BlockPos stand = Construction.standable(level, BlockPos.containing(at), 4);
		Vec3 where = stand == null ? at.add(0.0, 1.0, 0.0) : Vec3.atBottomCenterOf(stand);
		Mob master = McaCompat.spawnGuard(level, where);
		if (master == null) {
			master = EntityTypes.VINDICATOR.spawn(level, BlockPos.containing(where), EntitySpawnReason.EVENT);
		}
		if (master == null) {
			return;
		}
		master.setCustomName(Component.translatable("entity.jaymoskingdoms.trials_master"));
		master.setCustomNameVisible(true);
		master.setPersistenceRequired();
		master.addTag(TAG);
		boost(master, Attributes.MAX_HEALTH, MASTER_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		boost(master, Attributes.ATTACK_DAMAGE, MASTER_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		boost(master, Attributes.ARMOR, MASTER_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
		boost(master, Attributes.MOVEMENT_SPEED, MASTER_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		master.setHealth(master.getMaxHealth());
		ItemStack mask = new ItemStack(Items.LEATHER_HELMET);
		mask.set(DataComponents.DYED_COLOR, new DyedItemColor(0x101010));
		master.setItemSlot(EquipmentSlot.HEAD, mask);
		master.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		master.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			master.setDropChance(slot, 0.0F);
		}
		master.setTarget(player);
		MASTERS.put(player.getUUID(), master.getUUID());
		ServerBossEvent bar = new ServerBossEvent(master.getUUID(), master.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
		BARS.put(master.getUUID(), bar);
		bar.addPlayer(player);
		level.playSound(null, master.blockPosition(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	private static void boost(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double by, AttributeModifier.Operation op) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addPermanentModifier(new AttributeModifier(JaymosKingdoms.id("trials_master_" + attribute.getRegisteredName().replace(':', '_')), by, op));
		}
	}

	/** Keeps each master after his duellist, and his bar true; one whose duellist is gone leaves. */
	private static void masters(MinecraftServer server, ServerLevel level) {
		for (Iterator<Map.Entry<UUID, UUID>> it = MASTERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, UUID> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			ServerBossEvent bar = BARS.get(e.getValue());
			Entity found = level.getEntity(e.getValue());
			if (!(found instanceof Mob master) || !master.isAlive()) {
				dropBar(e.getValue());
				it.remove();
				continue;
			}
			if (player == null || !player.isAlive() || player.level() != level || player.distanceToSqr(master) > MASTER_LEASH * MASTER_LEASH) {
				master.discard();
				dropBar(e.getValue());
				it.remove();
				continue;
			}
			if (bar != null) {
				bar.setProgress(master.getHealth() / master.getMaxHealth());
			}
			if (master.getTarget() != player) {
				master.setTarget(player);
			}
			if (master.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) != player) {
				master.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
			}
		}
	}

	private static void dropBar(UUID master) {
		ServerBossEvent bar = BARS.remove(master);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** The master is beaten: he leaves a relic and Strength experience to {@code player}, and goes. */
	private static void won(ServerLevel level, LivingEntity master, UUID who) {
		MASTERS.remove(who);
		dropBar(master.getUUID());
		if (level.getServer().getPlayerList().getPlayer(who) instanceof ServerPlayer player) {
			player.setAttached(BEATEN, true);
			ItemStack relic = Relics.found(level, level.getRandom());
			if (relic.isEmpty()) {
				relic = new ItemStack(Items.DIAMOND, 8);
			}
			master.spawnAtLocation(level, relic);
			Skills.grant(player, Skills.Pillar.STRENGTH, REWARD_XP);
			level.playSound(null, master.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}
}
