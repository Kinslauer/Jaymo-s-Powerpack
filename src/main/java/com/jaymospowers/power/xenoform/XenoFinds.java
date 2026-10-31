package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureMachines;
import com.jaymospowers.power.chrono.future.FutureMech;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.121, Xeno-Band patch 6) Earning aliens, beyond scanning:
 * <ul>
 * <li><b>Star tablets</b> ({@link #TABLET}): found in fallen stars, fishing treasure and the remnants' chests, assembled by
 * Gremlin, and dropped by band hunters. Hold one when you slam the band and the band reads it: a random alien it doesn't
 * hold yet (never the hidden ones) is learned, and the tablet crumbles.</li>
 * <li><b>Alien-only crafting</b>, sneak + use as the alien: Gremlin at a crafting table turns {@link #TABLET_CIRCUITS}
 * circuits and an amethyst shard into a tablet; Bulwark at a grindstone grinds one raw ore (or ore block) of iron, copper
 * or gold into two dusts, which smelt to ingots; Pyre, holding something that smelts, smelts up to {@link #PYRE_BATCH} of
 * it in its hands. Each has a JEI page ({@code client/jei/XenoJei}).</li>
 * <li><b>Star wrecks</b>: one fallen star in {@link #WRECK_ONE_IN} comes down a wreck, its core ({@link StarWreckBlock})
 * sending out probes ({@link StarProbe}) while a band wearer is near. Probes drop circuits; the core gives a wreck core
 * (kept for the band's reforging) and more circuits.</li>
 * <li><b>Band hunters</b>: on some nights an android mech comes for a band wearer. It drops a tablet.</li>
 * </ul>
 * The fifth way, a corrupted band's glitch teaching an alien, is in {@link XenoformPower#glitch}.
 */
public final class XenoFinds {
	// ---- tuning ----
	/** Tablets: from a mined fallen star, a fishing treasure catch, a remnant chest. */
	public static final float STAR_TABLET = 0.25F, FISHING_TABLET = 0.15F, REMNANT_TABLET = 0.2F;
	/** Gremlin's tablet: this many circuits and one amethyst shard. */
	public static final int TABLET_CIRCUITS = 4;
	/** Bulwark grinds one ore into this many dusts. */
	public static final int DUSTS_PER_ORE = 2;
	/** Pyre smelts up to this many at once, then waits this long (ticks). */
	public static final int PYRE_BATCH = 8, PYRE_COOLDOWN = 20;
	/** One star in this many is a wreck. */
	public static final int WRECK_ONE_IN = 10;
	/** Band hunters: every this often (ticks) at night each band wearer may be hunted, this likely, at most once a night. */
	public static final int HUNT_CHECK = 1200;
	public static final float HUNT_CHANCE = 0.12F;
	public static final long HUNT_GAP = 12000L;
	/** They come from this far off, and never while another is out for the same wearer within this far. */
	public static final double HUNT_FROM_MIN = 28.0, HUNT_FROM_MAX = 44.0, HUNT_BUSY = 128.0;
	public static final String HUNTER_TAG = "jaymospowers.band_hunter";

	public static Item TABLET, CIRCUIT, WRECK_CORE, IRON_DUST, COPPER_DUST, GOLD_DUST;
	public static EntityType<StarProbe> PROBE;

	private static final Map<UUID, Long> LAST_HUNT = new HashMap<>();
	private static final Map<UUID, Long> PYRE_NEXT = new HashMap<>();

	private XenoFinds() {
	}

	/** An item with a line of grey under its name. */
	public static class TipItem extends Item {
		private final String tip;

		public TipItem(Properties properties, String tip) {
			super(properties);
			this.tip = tip;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
			lines.accept(Component.translatable(this.tip).withStyle(ChatFormatting.GRAY));
		}
	}

	static void init() {
		TABLET = item("star_tablet", Rarity.UNCOMMON);
		CIRCUIT = item("probe_circuit", Rarity.COMMON);
		WRECK_CORE = item("wreck_core", Rarity.RARE);
		IRON_DUST = item("iron_dust", Rarity.COMMON);
		COPPER_DUST = item("copper_dust", Rarity.COMMON);
		GOLD_DUST = item("gold_dust", Rarity.COMMON);
		StarWreckBlock.init();
		StarReaders.init();
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("star_probe"));
		PROBE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<StarProbe>of(StarProbe::new, MobCategory.MONSTER)
			.sized(0.4F, 0.8F).eyeHeight(0.5F).clientTrackingRange(8).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(PROBE, StarProbe.createAttributes());
		loot();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer server) || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			String form = server.getAttached(XenoformState.FORM);
			var state = level.getBlockState(hit.getBlockPos());
			if ("gremlin".equals(form) && state.is(Blocks.CRAFTING_TABLE)) {
				return assemble(server) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if ("bulwark".equals(form) && state.is(Blocks.GRINDSTONE)) {
				return grind(server, server.getMainHandItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer server) || !player.isShiftKeyDown()
				|| !"pyre".equals(server.getAttached(XenoformState.FORM))) {
				return InteractionResult.PASS;
			}
			return smelt(server, server.getMainHandItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(XenoFinds::hunt);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(HUNTER_TAG) && entity.level() instanceof ServerLevel level) {
				entity.spawnAtLocation(level, new ItemStack(TABLET));
				entity.spawnAtLocation(level, new ItemStack(CIRCUIT, 2 + level.getRandom().nextInt(3)));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_HUNT.clear();
			PYRE_NEXT.clear();
		});
	}

	private static Item item(String id, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new TipItem(new Item.Properties().setId(key).rarity(rarity), "item.jaymospowers." + id + ".tooltip"));
	}

	// ---------------------------------------------------------------- tablets

	/** Tablets in fishing treasure and the remnants' chests. */
	private static void loot() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey();
			if (key.isEmpty()) {
				return;
			}
			var id = key.get().identifier();
			RandomSource random = context.getRandom();
			if (id.getNamespace().equals("minecraft") && id.getPath().equals("gameplay/fishing/treasure") && random.nextFloat() < FISHING_TABLET
				|| id.getNamespace().equals("ancient_remnants") && id.getPath().startsWith("chests/") && random.nextFloat() < REMNANT_TABLET) {
				drops.add(new ItemStack(TABLET));
			}
		});
	}

	/** A fallen star mined: now and then a tablet among what was inside. */
	static void fromStar(ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextFloat() < STAR_TABLET) {
			net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(TABLET));
		}
	}

	/**
	 * The band is slammed: a tablet in either hand is read first. A random alien the band doesn't hold yet (never a hidden
	 * one) is learned and the tablet crumbles; with nothing left to learn it stays whole. True: a tablet was read.
	 */
	static boolean readTablet(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty() || !stack.is(TABLET)) {
				continue;
			}
			List<XenoForm> unread = new ArrayList<>();
			XenoformState.Band band = XenoformState.band(player);
			for (XenoForm form : XenoForms.all()) {
				if (!form.hidden() && !band.has(form.id)) {
					unread.add(form);
				}
			}
			ServerLevel level = player.level();
			if (unread.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.tablet_nothing").withStyle(ChatFormatting.GRAY));
				return false;
			}
			stack.shrink(1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 1.0F, 1.2F);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
			XenoformPower.unlock(player, unread.get(player.getRandom().nextInt(unread.size())));
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- alien-only crafting

	/** Gremlin at a crafting table: circuits and an amethyst shard put together into a tablet. */
	private static boolean assemble(ServerPlayer player) {
		var inventory = player.getInventory();
		if (inventory.countItem(CIRCUIT) < TABLET_CIRCUITS || inventory.countItem(Items.AMETHYST_SHARD) < 1) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.craft_gremlin_need", TABLET_CIRCUITS).withStyle(ChatFormatting.GRAY));
			return true;
		}
		take(player, CIRCUIT, TABLET_CIRCUITS);
		take(player, Items.AMETHYST_SHARD, 1);
		give(player, new ItemStack(TABLET));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
		return true;
	}

	/** What one ore of {@code stack} grinds into (Bulwark at a grindstone), or null. */
	public static @Nullable Item dustOf(ItemStack stack) {
		if (stack.is(Items.RAW_IRON) || stack.is(ItemTags.IRON_ORES)) {
			return IRON_DUST;
		}
		if (stack.is(Items.RAW_COPPER) || stack.is(ItemTags.COPPER_ORES)) {
			return COPPER_DUST;
		}
		if (stack.is(Items.RAW_GOLD) || stack.is(ItemTags.GOLD_ORES) && !stack.is(Items.NETHER_GOLD_ORE)) {
			return GOLD_DUST;
		}
		return null;
	}

	/** Bulwark at a grindstone: one ore in its hand ground into two dusts. */
	private static boolean grind(ServerPlayer player, ItemStack held) {
		Item dust = dustOf(held);
		if (dust == null) {
			return false;
		}
		held.shrink(1);
		give(player, new ItemStack(dust, DUSTS_PER_ORE));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
		return true;
	}

	/** Pyre, sneaking with something that smelts: up to {@link #PYRE_BATCH} of it smelted in its hands. */
	private static boolean smelt(ServerPlayer player, ItemStack held) {
		if (held.isEmpty()) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < PYRE_NEXT.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		SingleRecipeInput input = new SingleRecipeInput(held);
		var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
		if (recipe.isEmpty()) {
			return false;
		}
		ItemStack one = recipe.get().value().assemble(input);
		if (one.isEmpty()) {
			return false;
		}
		int count = Math.min(PYRE_BATCH, held.getCount());
		held.shrink(count);
		give(player, one.copyWithCount(one.getCount() * count));
		PYRE_NEXT.put(player.getUUID(), now + PYRE_COOLDOWN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
		return true;
	}

	private static void take(Player player, Item item, int count) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int n = Math.min(count, stack.getCount());
				stack.shrink(n);
				count -= n;
			}
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	// ---------------------------------------------------------------- band hunters

	/** Now and then, at night, an android mech comes for a band wearer. */
	private static void hunt(MinecraftServer server) {
		if (server.getTickCount() % HUNT_CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (!XenoformPower.has(player) || player.isCreative() || player.isSpectator() || level.dimension() != Level.OVERWORLD || level.isBrightOutside()) {
				continue;
			}
			long now = level.getGameTime();
			if (now - LAST_HUNT.getOrDefault(player.getUUID(), -HUNT_GAP) < HUNT_GAP || player.getRandom().nextFloat() >= HUNT_CHANCE) {
				continue;
			}
			if (!level.getEntitiesOfClass(FutureMech.class, player.getBoundingBox().inflate(HUNT_BUSY), e -> e.entityTags().contains(HUNTER_TAG)).isEmpty()) {
				continue;
			}
			RandomSource random = player.getRandom();
			double angle = random.nextDouble() * Math.PI * 2.0, d = HUNT_FROM_MIN + random.nextDouble() * (HUNT_FROM_MAX - HUNT_FROM_MIN);
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(player.getX() + Math.cos(angle) * d, 0.0, player.getZ() + Math.sin(angle) * d));
			if (!level.isLoaded(at) || !level.getFluidState(at.below()).isEmpty()) {
				continue;
			}
			FutureMech mech = FutureMachines.ANDROID_MECH.create(level, EntitySpawnReason.EVENT);
			if (mech == null) {
				continue;
			}
			mech.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			mech.addTag(HUNTER_TAG);
			mech.setPersistenceRequired();
			mech.setTarget(player);
			level.addFreshEntity(mech);
			LAST_HUNT.put(player.getUUID(), now);
			level.playSound(null, at, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.5F);
		}
	}

	/** (For the wreck) a probe sent out at {@code target}. */
	static void sendProbe(ServerLevel level, Vec3 from, @Nullable Player target) {
		StarProbe probe = PROBE.create(level, EntitySpawnReason.EVENT);
		if (probe == null) {
			return;
		}
		probe.snapTo(from.x, from.y, from.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		probe.setBoundOrigin(BlockPos.containing(from));
		if (target != null) {
			probe.setTarget(target);
		}
		level.addFreshEntity(probe);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8F, 2.0F);
	}

	/** A probe: a small flying machine that rams band wearers, drops a circuit or two. Built on the vex's flight. */
	public static final class StarProbe extends Vex {
		public StarProbe(EntityType<? extends Vex> type, Level level) {
			super(type, level);
			this.xpReward = 3;
		}

		public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
			return Vex.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 8.0)
				.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 3.0);
		}

		@Override
		protected void populateDefaultEquipmentSlots(RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
			// no sword: it rams
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			this.spawnAtLocation(level, new ItemStack(CIRCUIT, 1 + this.random.nextInt(2)));
		}

		@Override
		protected net.minecraft.sounds.SoundEvent getAmbientSound() {
			return SoundEvents.BEACON_AMBIENT;
		}

		@Override
		protected net.minecraft.sounds.SoundEvent getDeathSound() {
			return SoundEvents.COPPER_BULB_TURN_OFF;
		}

		@Override
		protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
			return SoundEvents.COPPER_HIT;
		}
	}

	/** For the JEI pages: everything Bulwark grinds. */
	public static List<ItemStack> grindable() {
		List<ItemStack> all = new ArrayList<>();
		for (Item item : List.of(Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE, Items.RAW_COPPER, Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE,
			Items.RAW_GOLD, Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE)) {
			all.add(new ItemStack(item));
		}
		return all;
	}
}
