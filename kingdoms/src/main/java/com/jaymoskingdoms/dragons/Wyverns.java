package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Wyverns to raise and ride (see {@link WyvernDragon}), and where they come from.
 * <p>
 * <b>The nest</b> ({@code jaymoskingdoms:wyvern_nest}): a bowl of hay, bones and sticks on a ledge of the highest crags of
 * the harshest mountains (jagged, frozen and stony peaks, at least {@link #NEST_HEIGHT} up), far from anyone: the structure
 * set keeps {@link #KEEP_FROM_VILLAGES} chunks from villages, and nothing lives that high. It sits over a vent of magma that
 * keeps the clutch warm, under hay; in the middle a hollow holds the eggs (a barrel, {@code chests/wyvern_nest}), and the
 * mother keeps the crag. She hunts the herds of the high pastures and a hunter who comes for her eggs has her to answer to.
 * <p>
 * <b>The egg</b> ({@link #EGG}): taking one is the whole of the rest of the work. Carried, it takes the warmth of a fire (a
 * lit campfire, furnace, lava, magma, or the Nether's own heat) within {@link #WARM_RANGE} blocks, and grows ripe after
 * {@link #HATCH_TICKS} ticks of it; away from warmth it cools off again twice as fast as it warmed. Used on a block, a ripe
 * egg hatches a hatchling tamed to whoever hatched it. An egg left cold never spoils: it only has to be kept warm again.
 */
public final class Wyverns {
	// ---- tuning ----
	/** A ripe egg has been warm this long (ticks: five minutes); it warms and cools this much a check, a check every this many ticks. */
	static final int HATCH_TICKS = 6000, WARM_GAIN = 20, COOL_LOSS = 40, CHECK_EVERY = 20;
	/** How near a fire must be to warm an egg (blocks, each way, and up and down). */
	static final int WARM_RANGE = 3, WARM_DEPTH = 2;
	/** A nest is only built on ground this high, with this much open sky; the structure set keeps this many chunks from villages. */
	static final int NEST_HEIGHT = 118, KEEP_FROM_VILLAGES = 10;
	/** The nest's bowl: half its width (blocks), and the hollow's. */
	static final int BOWL = 5, HOLLOW = 3;

	public static EntityType<WyvernDragon> WYVERN;
	public static Item EGG, WYVERN_SPAWN_EGG;
	public static StructureType<NestStructure> NEST;
	public static StructurePieceType NEST_PIECE;
	static final ResourceKey<LootTable> NEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/wyvern_nest"));

	private Wyverns() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("wyvern_dragon"));
		WYVERN = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(WyvernDragon::new, MobCategory.CREATURE).sized(2.0F, 1.7F).clientTrackingRange(16).build(key));
		FabricDefaultAttributeRegistry.register(WYVERN, WyvernDragon.createAttributes());
		ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("wyvern_egg"));
		EGG = Registry.register(BuiltInRegistries.ITEM, eggKey, new EggItem(new Item.Properties().setId(eggKey).stacksTo(1).rarity(Rarity.RARE)));
		ResourceKey<Item> spawnKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("wyvern_dragon_spawn_egg"));
		WYVERN_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, spawnKey, new SpawnEggItem(new Item.Properties().setId(spawnKey).spawnEgg(WYVERN)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(EGG);
			output.accept(WYVERN_SPAWN_EGG);
		});
		NEST = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("wyvern_nest"), () -> NestStructure.CODEC);
		NEST_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosKingdoms.id("wyvern_nest"), (StructurePieceType.ContextlessType)NestPiece::new);
	}

	// ---- the egg ----

	/** How long (ticks) the egg has been warm. */
	static int warmth(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr("warmth", 0);
	}

	/** A fire, a vent or the Nether's heat about {@code at}. */
	static boolean warm(ServerLevel level, BlockPos at) {
		if (level.dimension() == Level.NETHER) {
			return true;
		}
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-WARM_RANGE, -WARM_DEPTH, -WARM_RANGE), at.offset(WARM_RANGE, WARM_DEPTH, WARM_RANGE))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)
				|| (s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER))
				&& s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
				&& s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
				return true;
			}
		}
		return false;
	}

	private static final class EggItem extends Item {
		EggItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, @Nullable EquipmentSlot slot) {
			if (!(holder instanceof Player) || level.getGameTime() % CHECK_EVERY != 0) {
				return;
			}
			int had = warmth(stack);
			int now = warm(level, holder.blockPosition()) ? Math.min(HATCH_TICKS, had + WARM_GAIN) : Math.max(0, had - COOL_LOSS);
			if (now != had) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("warmth", now));
			}
		}

		@Override
		public boolean isBarVisible(ItemStack stack) {
			return warmth(stack) > 0;
		}

		@Override
		public int getBarWidth(ItemStack stack) {
			return Math.round(13.0F * warmth(stack) / HATCH_TICKS);
		}

		@Override
		public int getBarColor(ItemStack stack) {
			return warmth(stack) >= HATCH_TICKS ? 0xFFB02E : 0xC8643C;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
			int w = warmth(stack);
			lines.accept(Component.translatable(w >= HATCH_TICKS ? "item.jaymoskingdoms.wyvern_egg.ripe" : w > 0 ? "item.jaymoskingdoms.wyvern_egg.warming" : "item.jaymoskingdoms.wyvern_egg.cold")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = context.getItemInHand();
			int w = warmth(stack);
			if (w < HATCH_TICKS && !player.isCreative()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wyvern_egg.not_ripe", Mth.floor(100.0F * w / HATCH_TICKS)));
				return InteractionResult.FAIL;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			WyvernDragon wyvern = WYVERN.create(server, EntitySpawnReason.SPAWN_ITEM_USE);
			if (wyvern == null) {
				return InteractionResult.FAIL;
			}
			wyvern.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
			wyvern.finalizeSpawn(server, server.getCurrentDifficultyAt(at), EntitySpawnReason.SPAWN_ITEM_USE, null);
			// (finalizeSpawn makes it grown, so the hatchling's age comes after)
			wyvern.setBaby(true);
			wyvern.tameWithName(player);
			wyvern.setPersistenceRequired();
			if (!server.noCollision(wyvern)) {
				wyvern.discard();
				return InteractionResult.FAIL;
			}
			server.addFreshEntity(wyvern);
			server.playSound(null, at, SoundEvents.TURTLE_EGG_CRACK, SoundSource.NEUTRAL, 1.4F, 0.7F);
			server.playSound(null, at, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 0.5F, 1.9F);
			server.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.05);
			server.sendParticles(ParticleTypes.HEART, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.0);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dragon_egg.hatched", wyvern.getDisplayName()));
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---- the nest ----

	public static final class NestStructure extends Structure {
		public static final MapCodec<NestStructure> CODEC = simpleCodec(NestStructure::new);

		public NestStructure(StructureSettings settings) {
			super(settings);
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
			int ground = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			// only the high crags
			if (ground < NEST_HEIGHT) {
				return Optional.empty();
			}
			BlockPos at = new BlockPos(x, ground, z);
			return Optional.of(new GenerationStub(at, builder -> builder.addPiece(new NestPiece(at))));
		}

		@Override
		public StructureType<?> type() {
			return NEST;
		}
	}

	public static final class NestPiece extends StructurePiece {
		private final BlockPos at;

		NestPiece(BlockPos at) {
			super(NEST_PIECE, 0, new BoundingBox(at.getX() - BOWL - 1, at.getY() - 6, at.getZ() - BOWL - 1, at.getX() + BOWL + 1, at.getY() + 6, at.getZ() + BOWL + 1));
			this.at = at;
		}

		NestPiece(CompoundTag tag) {
			super(NEST_PIECE, tag);
			this.at = BlockPos.of(tag.getLongOr("at", 0L));
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putLong("at", this.at.asLong());
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource chunkRandom, BoundingBox box,
				ChunkPos chunk, BlockPos reference) {
			RandomSource random = RandomSource.create(this.at.asLong() * 17L + 3L);
			int x0 = this.at.getX(), y0 = this.at.getY(), z0 = this.at.getZ();
			for (int dx = -BOWL; dx <= BOWL; dx++) {
				for (int dz = -BOWL; dz <= BOWL; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > BOWL) {
						continue;
					}
					// the ledge: stone under the bowl down to whatever is there, and the open air above it
					for (int y = y0 - 1; y >= y0 - 5; y--) {
						BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
						if (!box.isInside(p) || level.getBlockState(p).isSolid() && y < y0 - 1) {
							break;
						}
						this.put(level, box, p, d < 2.0 && y == y0 - 1 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState());
					}
					for (int y = 1; y <= 4; y++) {
						this.put(level, box, new BlockPos(x0 + dx, y0 + y, z0 + dz), Blocks.AIR.defaultBlockState());
					}
					BlockState top = d > HOLLOW + 0.5 ? (random.nextInt(3) == 0 ? Blocks.BONE_BLOCK : Blocks.HAY_BLOCK).defaultBlockState()
						: Blocks.HAY_BLOCK.defaultBlockState();
					this.put(level, box, new BlockPos(x0 + dx, y0, z0 + dz), d < 1.5 ? Blocks.HAY_BLOCK.defaultBlockState() : top);
					// sticks and bones over it
					if (d > 1.5 && d < BOWL - 0.5 && random.nextInt(4) == 0) {
						this.put(level, box, new BlockPos(x0 + dx, y0 + 1, z0 + dz), (random.nextBoolean() ? Blocks.BONE_BLOCK : Blocks.OAK_FENCE).defaultBlockState());
					}
				}
			}
			// the clutch: a barrel in the middle of the hollow, under a bed of hay
			BlockPos clutch = new BlockPos(x0, y0, z0);
			this.createChest(level, box, random, clutch, NEST_LOOT, Blocks.BARREL.defaultBlockState());
			// the mother
			if (box.isInside(clutch.above(2))) {
				WyvernDragon mother = WYVERN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (mother != null) {
					mother.snapTo(x0 + 0.5, y0 + 1.0, z0 + 0.5, random.nextFloat() * 360.0F, 0.0F);
					mother.finalizeSpawn(level, level.getCurrentDifficultyAt(clutch), EntitySpawnReason.STRUCTURE, null);
					mother.setPersistenceRequired();
					level.addFreshEntity(mother);
				}
			}
		}

		private void put(WorldGenLevel level, BoundingBox box, BlockPos p, BlockState state) {
			if (box.isInside(p)) {
				level.setBlock(p, state, 2);
			}
		}
	}
}
