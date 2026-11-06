package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.63) Andy: armour "gets dirty when you walk in the desert or swampy muddy areas, and washes in the rain or by hand
 * or in bodies of water". Each worn armour piece carries its own {@link Grime} (how dirty, and how much of that is mud
 * rather than dust), so it stays on the piece when it's taken off, traded or dropped. The client paints dusty tan or mud
 * brown splotches into the piece's texture and its inventory icon, more as it gets dirtier ({@code ArmourGrimeTextures}).
 * <ul>
 * <li><b>Getting dirty</b> (only while moving, or lying on the ground): sand and dry ground in deserts, badlands and other
 * sandy or dry places throw up dust; mud, clay and wet ground (in the rain or in a swamp) splash mud. Boots get it first,
 * then greaves, chest, helmet ({@link #REACH}); lying or crawling on the ground gets it all over.</li>
 * <li><b>Washing</b>: water washes whatever is in it (wading cleans boots and greaves, swimming under washes all of it,
 * quickly); rain washes everything slowly; and by hand: sneak + use an empty hand on a water cauldron to scrub your
 * armour (a level of water each scrub), sneak + use a wet sponge anywhere, or use a dirty piece on a water cauldron.</li>
 * </ul>
 * Everyone wearing armour near a player is kept, not only players, so guards and villagers come back muddy too.
 * <p>(0.20.41) The washes by hand take blood off too ({@code Bloodied}): a scrub or a sponge washes your own coat as well,
 * and dunking any bloody item (a blade too) in a cauldron rinses it.
 */
public final class ArmourGrime {
	/** How dirty a piece can get (a whole number, so the component only changes in coarse steps and isn't sent every tick). */
	public static final int MAX = 100;
	/** How often bodies are checked (ticks), and how far from a player (blocks). */
	static final int CHECK_TICKS = 40;
	static final double RANGE = 48.0;
	/** How far a body must have moved (blocks, sideways) since the last check to kick anything up. */
	static final double MOVED = 1.5;
	/** Dirt gained per check while moving: on dusty ground, on mud, and on wet ground (dirt, grass in the rain or a swamp). */
	static final float DUST_GAIN = 2.0F, MUD_GAIN = 5.0F, WET_GROUND_GAIN = 2.5F;
	/** Running doubles it; lying or crawling on the ground gets this many times as much, all over. */
	static final float SPRINT = 2.0F, LYING = 2.5F;
	/** How much of a check's dirt reaches each piece, feet to head. */
	static final float[] REACH = {1.0F, 0.6F, 0.25F, 0.08F};
	/** Washed off per check: under water, wading (boots and greaves), in the rain, and one scrub by hand. */
	static final int UNDER_WATER_WASH = 30, WADING_WASH = 15, RAIN_WASH = 3, SCRUB = 35;
	/**
	 * (0.21.26) Dragged a step ({@link #dragged}): through water, this much washed off; over the ground, this many times a
	 * walking check's dirt, shared over boots, greaves, chest and helmet like this.
	 */
	static final int DRAG_WASH = 12;
	static final float DRAG_DIRT = 0.6F;
	static final float[] DRAG_REACH = {0.7F, 1.0F, 1.0F, 0.4F};
	/** A wet sponge scrubs once every this many ticks. */
	static final int SPONGE_COOLDOWN = 20;
	/** The armour slots, feet to head (matching {@link #REACH}). */
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

	/** How dirty a piece is ({@code 0..MAX}) and how much of it is mud rather than dust ({@code 0..MAX}). */
	public record Grime(int amount, int mud) {
		public static final Codec<Grime> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("amount").forGetter(Grime::amount),
			Codec.INT.optionalFieldOf("mud", 0).forGetter(Grime::mud)).apply(i, Grime::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Grime> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Grime::amount,
			ByteBufCodecs.VAR_INT, Grime::mud, Grime::new);

		/** 0 (clean) to 1 (filthy). */
		public float level() {
			return Mth.clamp(this.amount / (float) MAX, 0.0F, 1.0F);
		}

		/** 0 (all dust) to 1 (all mud). */
		public float muddiness() {
			return Mth.clamp(this.mud / (float) MAX, 0.0F, 1.0F);
		}
	}

	public static DataComponentType<Grime> GRIME;

	/** Where each body was at the last check, and when each player last scrubbed with a sponge. */
	private static final Map<LivingEntity, Vec3> LAST = new WeakHashMap<>();
	private static final Map<Player, Long> SPONGED = new WeakHashMap<>();

	private ArmourGrime() {
	}

	public static void init() {
		GRIME = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("grime"),
			DataComponentType.<Grime>builder().persistent(Grime.CODEC).networkSynchronized(Grime.STREAM_CODEC).build());
		ServerTickEvents.END_SERVER_TICK.register(ArmourGrime::tick);
		UseBlockCallback.EVENT.register(ArmourGrime::onUseBlock);
		UseItemCallback.EVENT.register(ArmourGrime::onUseItem);
	}

	/** How dirty {@code stack} is, or null if it's clean. */
	public static @Nullable Grime grime(ItemStack stack) {
		return GRIME == null || stack.isEmpty() ? null : stack.get(GRIME);
	}

	// ---- every check --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<LivingEntity> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) {
					continue;
				}
				for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(RANGE),
					ArmourGrime::wearsArmour)) {
					if (seen.add(body)) {
						weather(level, body);
					}
				}
			}
		}
	}

	private static boolean wearsArmour(LivingEntity body) {
		if (!body.isAlive()) {
			return false;
		}
		for (EquipmentSlot slot : SLOTS) {
			if (!body.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** One check of one body: washes what water and rain reach, then dirties what the ground kicks up. */
	private static void weather(ServerLevel level, LivingEntity body) {
		Vec3 now = body.position();
		Vec3 last = LAST.put(body, now);
		double moved = last == null ? 0.0 : Math.hypot(now.x - last.x, now.z - last.z);
		if (body.isUnderWater()) {
			washAll(body, UNDER_WATER_WASH);
			return;
		}
		if (body.isInWater()) {
			// wading: the water washes boots and greaves, and kicks nothing up
			wash(body, EquipmentSlot.FEET, WADING_WASH);
			wash(body, EquipmentSlot.LEGS, WADING_WASH);
			return;
		}
		boolean rained = body.isInWaterOrRain();
		float gain = 0.0F;
		boolean mud = false;
		boolean lying = body.getPose() == Pose.SWIMMING || body.getPose() == Pose.SLEEPING || body.getPose() == Pose.DYING
			|| body.entityTags().contains("jaymospowers_downed");
		if (body.onGround() && (moved >= MOVED || lying)) {
			BlockPos under = body.getOnPos();
			BlockState ground = level.getBlockState(under);
			Holder<Biome> biome = level.getBiome(under);
			if (isMud(ground)) {
				gain = MUD_GAIN;
				mud = true;
			} else if (isEarth(ground) && (rained || level.isRainingAt(body.blockPosition()) || biome.is(ConventionalBiomeTags.IS_SWAMP))) {
				gain = WET_GROUND_GAIN;
				mud = true;
			} else if (!rained && (ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.SUSPICIOUS_GRAVEL)
				|| ground.is(Blocks.RED_SANDSTONE) || ground.is(Blocks.SANDSTONE) || isEarth(ground) && (biome.is(ConventionalBiomeTags.IS_DESERT)
					|| biome.is(ConventionalBiomeTags.IS_BADLANDS) || biome.is(ConventionalBiomeTags.IS_SANDY) || biome.is(ConventionalBiomeTags.IS_DRY)))) {
				gain = DUST_GAIN;
			}
			if (body.isSprinting()) {
				gain *= SPRINT;
			}
		}
		for (int i = 0; i < SLOTS.length; i++) {
			float dirt = gain * (lying ? LYING : REACH[i]);
			if (rained && dirt < 1.0F) {
				// the rain washes what the ground isn't splashing faster than it washes
				wash(body, SLOTS[i], RAIN_WASH);
			} else {
				soil(body, SLOTS[i], dirt, mud);
			}
		}
	}

	/**
	 * (0.21.26) Andy: "dragging should bloody dirty and clean the armor they wear respectively". {@code body} was just
	 * dragged (or crawled) a step: through water it's washed ({@link #DRAG_WASH}), over dirt, mud or sand everything it
	 * wears is scraped through it, {@link #DRAG_DIRT} times walking's dust or mud a step (the chest and legs most).
	 */
	public static void dragged(ServerLevel level, LivingEntity body) {
		if (body.isInWater()) {
			washAll(body, DRAG_WASH);
			return;
		}
		Dirt dirt = dragDirt(level, body);
		if (dirt == null) {
			return;
		}
		for (int i = 0; i < SLOTS.length; i++) {
			soil(body, SLOTS[i], dirt.gain() * DRAG_DIRT * DRAG_REACH[i], dirt.mud());
		}
	}

	/** (0.21.26) What a body dragged over the ground here is scraped with: so much dirt a step, muddy or dusty. */
	public record Dirt(float gain, boolean mud) {
	}

	/** (0.21.26) The dirt a body dragged a step over the ground under {@code body} picks up (walking's, before {@link #DRAG_DIRT}), or null. */
	public static @Nullable Dirt dragDirt(ServerLevel level, net.minecraft.world.entity.Entity body) {
		BlockPos under = body.getOnPos();
		BlockState ground = level.getBlockState(under);
		if (ground.isAir()) {
			ground = level.getBlockState(under.below());
		}
		boolean rained = body.isInWaterOrRain();
		Holder<Biome> biome = level.getBiome(under);
		if (isMud(ground)) {
			return new Dirt(MUD_GAIN, true);
		}
		if (isEarth(ground) && (rained || level.isRainingAt(body.blockPosition()) || biome.is(ConventionalBiomeTags.IS_SWAMP))) {
			return new Dirt(WET_GROUND_GAIN, true);
		}
		if (isEarth(ground) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.SUSPICIOUS_GRAVEL)) {
			// dragged, even dry earth grinds in
			return new Dirt(DUST_GAIN, false);
		}
		return null;
	}

	/**
	 * (0.21.26) A corpse's armour (Jaymo's Kitchen keeps it on the body), piece {@code index} (0 boots ... 3 helmet), dragged
	 * {@code steps} steps over {@code dirt}: scraped dirty as a living body's would be.
	 */
	public static void dragArmour(ItemStack stack, int index, Dirt dirt, int steps, net.minecraft.util.RandomSource random) {
		soilStack(stack, dirt.gain() * DRAG_DIRT * DRAG_REACH[Mth.clamp(index, 0, DRAG_REACH.length - 1)] * steps, dirt.mud(), random);
	}

	/** (0.21.26) The same, through water: {@code steps} steps' washing. */
	public static void washArmour(ItemStack stack, int steps) {
		clean(stack, DRAG_WASH * steps);
	}

	private static boolean isMud(BlockState state) {
		if (state.is(Blocks.MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.CLAY) || state.is(Blocks.SOUL_SOIL)) {
			return true;
		}
		// other mods' mud (and wet, muddy ground), but not dried mud building blocks
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.contains("mud") && !path.contains("brick") && !path.contains("packed");
	}

	/** Bare earth that turns to mud when wet and to dust when dry. */
	private static boolean isEarth(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND);
	}

	/** Dirties the piece in {@code slot} by {@code gain} (fractions carry over by chance). */
	private static void soil(LivingEntity body, EquipmentSlot slot, float gain, boolean mud) {
		soilStack(body.getItemBySlot(slot), gain, mud, body.getRandom());
	}

	/** Dirties {@code stack} by {@code gain} (fractions carry over by chance). */
	static void soilStack(ItemStack stack, float gain, boolean mud, net.minecraft.util.RandomSource random) {
		if (stack.isEmpty() || gain <= 0.0F) {
			return;
		}
		int add = Mth.floor(gain) + (random.nextFloat() < gain - Mth.floor(gain) ? 1 : 0);
		if (add <= 0) {
			return;
		}
		Grime was = grime(stack);
		int amount = was == null ? 0 : was.amount();
		int muddy = was == null ? 0 : was.mud();
		if (amount >= MAX && (muddy >= MAX) == mud) {
			return;
		}
		int next = Math.min(MAX, amount + add);
		// the new dirt's share of the whole decides how muddy it now is
		int mudPart = Math.round((muddy * (float) amount + (mud ? MAX : 0) * (float) add) / (amount + add));
		stack.set(GRIME, new Grime(next, Mth.clamp(mudPart, 0, MAX)));
	}

	/** Washes {@code amount} off the piece in {@code slot}; true if it had any on it. */
	static boolean wash(LivingEntity body, EquipmentSlot slot, int amount) {
		return clean(body.getItemBySlot(slot), amount);
	}

	static boolean washAll(LivingEntity body, int amount) {
		boolean any = com.jaymospowers.core.blood.Bloodied.cleanCoat(body, amount);
		for (EquipmentSlot slot : SLOTS) {
			any |= wash(body, slot, amount);
		}
		return any;
	}

	/** Washes {@code amount} off {@code stack}; true if it had any on it. */
	public static boolean clean(ItemStack stack, int amount) {
		boolean bloody = com.jaymospowers.core.blood.Bloodied.clean(stack, amount);
		Grime was = grime(stack);
		if (was == null) {
			return bloody;
		}
		int left = was.amount() - amount;
		if (left <= 0) {
			stack.remove(GRIME);
		} else {
			stack.set(GRIME, new Grime(left, was.mud()));
		}
		return true;
	}

	static boolean anyDirt(LivingEntity body) {
		if (com.jaymospowers.core.blood.Bloodied.coat(body) != null) {
			return true;
		}
		for (EquipmentSlot slot : SLOTS) {
			if (grime(body.getItemBySlot(slot)) != null || com.jaymospowers.core.blood.Bloodied.blood(body.getItemBySlot(slot)) != null) {
				return true;
			}
		}
		return false;
	}

	// ---- by hand --------------------------------------------------------------------------------------------------------

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.WATER_CAULDRON) || !state.hasProperty(LayeredCauldronBlock.LEVEL)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
			// scrub what you're wearing, a level of water each time
			if (!anyDirt(player)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				washAll(player, SCRUB);
				LayeredCauldronBlock.lowerFillLevel(state, level, pos);
				splash(server, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), player.getBbWidth());
			}
			return InteractionResult.SUCCESS;
		}
		if (grime(held) != null || com.jaymospowers.core.blood.Bloodied.blood(held) != null) {
			// dunk the dirty piece itself: it comes out clean (no water used, like rinsing dye)
			if (level instanceof ServerLevel server) {
				clean(held, MAX);
				Vec3 at = Vec3.atCenterOf(pos);
				splash(server, at.add(0.0, 0.4, 0.0), 0.5F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (player.isSpectator() || !held.is(Items.WET_SPONGE) || !player.isShiftKeyDown() || !anyDirt(player)) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		Long last = SPONGED.get(player);
		if (last != null && now - last < SPONGE_COOLDOWN) {
			return InteractionResult.FAIL;
		}
		SPONGED.put(player, now);
		if (level instanceof ServerLevel server) {
			washAll(player, SCRUB);
			splash(server, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), player.getBbWidth());
		}
		return InteractionResult.SUCCESS;
	}

	private static void splash(ServerLevel level, Vec3 at, float width) {
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 14, width * 0.5, 0.4, width * 0.5, 0.1);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), at.x, at.y, at.z, 4, width * 0.4, 0.3,
			width * 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.4F, 1.1F + level.getRandom().nextFloat() * 0.2F);
	}
}
