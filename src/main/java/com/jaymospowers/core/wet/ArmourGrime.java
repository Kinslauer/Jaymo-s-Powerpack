package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
import net.minecraft.world.entity.npc.Npc;
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
 * <p>Dust takes the colour of the ground it comes off ({@link Grime#tint}): red off badlands sand, dark brown off a forest
 * floor, grey off gravel, soot off the Nether's rock, pale off end stone. Bare earth throws a little dust anywhere when it's
 * dry, not only in dry country, so a walk through the woods leaves its mark too.
 * <p>Bare clothes and skin get dirty too (Andy: "bare clothes should get dirty too"): every player and villager carries a
 * {@link #BODY} grime of its own, kicked up as its armour's is (less, as the armour takes the worst of it) and washed by
 * the same water, rain and scrubs. The client paints it over the skin, legs first, under any armour worn.
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
	/** Dry bare earth outside dry country (a forest floor, a path, a field) and the Nether's and the End's dusty rock. */
	static final float EARTH_DUST_GAIN = 1.0F, ROCK_DUST_GAIN = 1.5F;
	/** How far dust's colour goes from the plain dust tan toward the ground's own colour. */
	static final float GROUND_TINT = 0.5F;
	/** The plain dust tan, for blending the ground's colour into. */
	static final int DUST_TAN = 0xB59E78;
	/** Running doubles it; lying or crawling on the ground gets this many times as much, all over. */
	static final float SPRINT = 2.0F, LYING = 2.5F;
	/** How much of a check's dirt reaches each piece, feet to head. */
	static final float[] REACH = {1.0F, 0.7F, 0.4F, 0.2F};
	/** Washed off per check: under water, wading (boots and greaves), in the rain, and one scrub by hand. */
	static final int UNDER_WATER_WASH = 30, WADING_WASH = 15, RAIN_WASH = 3, SCRUB = 35;
	/**
	 * (0.21.28) Dragged a step ({@link #dragged}): through water, this much washed off; over the ground, this many times a
	 * walking check's dirt, shared over boots, greaves, chest and helmet like this.
	 */
	static final int DRAG_WASH = 12;
	static final float DRAG_DIRT = 0.6F;
	static final float[] DRAG_REACH = {0.7F, 1.0F, 1.0F, 0.4F};
	/** A wet sponge scrubs once every this many ticks. */
	static final int SPONGE_COOLDOWN = 20;
	/** The armour slots, feet to head (matching {@link #REACH}). */
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

	/**
	 * How dirty a piece is ({@code 0..MAX}), how much of it is mud rather than dust ({@code 0..MAX}), and the dust's colour
	 * (RGB, from the ground it came off; 0 for the plain dust tan).
	 */
	public record Grime(int amount, int mud, int tint) {
		public static final Codec<Grime> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("amount").forGetter(Grime::amount),
			Codec.INT.optionalFieldOf("mud", 0).forGetter(Grime::mud),
			Codec.INT.optionalFieldOf("tint", 0).forGetter(Grime::tint)).apply(i, Grime::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Grime> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Grime::amount,
			ByteBufCodecs.VAR_INT, Grime::mud, ByteBufCodecs.INT, Grime::tint, Grime::new);

		public Grime(int amount, int mud) {
			this(amount, mud, 0);
		}

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
	/** The grime on a body's own clothes and skin: synced to everyone near it, and saved, so it stays until washed. */
	public static final AttachmentType<Grime> BODY = AttachmentRegistry.create(JaymosPowers.id("body_grime"),
		builder -> builder.persistent(Grime.CODEC).syncWith(Grime.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** How much of a check's dirt reaches the body's own clothes (the feet and legs get it; armour takes the worst). */
	static final float BODY_REACH = 0.6F;

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
		if (body instanceof Player || body instanceof Npc) {
			// bare clothes get dirty too
			return true;
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
			// wading: the water washes boots and greaves (and the legs under them), and kicks nothing up
			wash(body, EquipmentSlot.FEET, WADING_WASH);
			wash(body, EquipmentSlot.LEGS, WADING_WASH);
			washBody(body, WADING_WASH);
			return;
		}
		boolean rained = body.isInWaterOrRain();
		float gain = 0.0F;
		boolean mud = false;
		int tint = 0;
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
			} else if (!rained && isRockDust(ground)) {
				gain = ROCK_DUST_GAIN;
			} else if (!rained && isEarth(ground) && !ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.MYCELIUM)
				&& !ground.is(Blocks.MOSS_BLOCK)) {
				// a dry forest floor, a path or a field, anywhere: a little dust
				gain = EARTH_DUST_GAIN;
			}
			if (gain > 0.0F && !mud) {
				tint = groundTint(level, under, ground);
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
				soil(body, SLOTS[i], dirt, mud, tint);
			}
		}
		if (body instanceof Player || body instanceof Npc) {
			float dirt = gain * (lying ? LYING : BODY_REACH);
			if (rained && dirt < 1.0F) {
				washBody(body, RAIN_WASH);
			} else {
				soilBody(body, dirt, mud, tint);
			}
		}
	}

	/** How dirty {@code body}'s own clothes and skin are, or null if they're clean. */
	public static @Nullable Grime body(LivingEntity body) {
		return body.getAttached(BODY);
	}

	/** Dirties {@code body}'s own clothes by {@code gain} (fractions carry over by chance). */
	static void soilBody(LivingEntity body, float gain, boolean mud, int tint) {
		if (gain <= 0.0F) {
			return;
		}
		Grime was = body(body);
		Grime now = soiled(was, gain, mud, tint, body.getRandom());
		if (now != null && !now.equals(was)) {
			body.setAttached(BODY, now);
		}
	}

	/** Washes {@code amount} off {@code body}'s own clothes; true if they had any on them. */
	static boolean washBody(LivingEntity body, int amount) {
		Grime was = body(body);
		if (was == null) {
			return false;
		}
		int left = was.amount() - amount;
		if (left <= 0) {
			body.removeAttached(BODY);
		} else {
			body.setAttached(BODY, new Grime(left, was.mud(), was.tint()));
		}
		return true;
	}

	/** Dusty rock that walking scuffs up: the Nether's (soot and ash) and the End's. */
	private static boolean isRockDust(BlockState state) {
		return state.is(Blocks.NETHERRACK) || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.BLACKSTONE)
			|| state.is(Blocks.SOUL_SAND) || state.is(Blocks.END_STONE) || state.is(BlockTags.NYLIUM);
	}

	/** The colour dust off {@code ground} takes: its map colour, part way from the plain dust tan. */
	static int groundTint(Level level, BlockPos pos, BlockState ground) {
		int rgb = ground.getMapColor(level, pos).col;
		if (rgb == 0) {
			return 0;
		}
		int r = mixChannel(DUST_TAN >> 16 & 0xFF, rgb >> 16 & 0xFF), g = mixChannel(DUST_TAN >> 8 & 0xFF, rgb >> 8 & 0xFF),
			b = mixChannel(DUST_TAN & 0xFF, rgb & 0xFF);
		return r << 16 | g << 8 | b;
	}

	private static int mixChannel(int from, int to) {
		return Mth.clamp(Math.round(from + (to - from) * GROUND_TINT), 0, 255);
	}

	/**
	 * (0.21.28) Andy: "dragging should bloody dirty and clean the armor they wear respectively". {@code body} was just
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
			soil(body, SLOTS[i], dirt.gain() * DRAG_DIRT * DRAG_REACH[i], dirt.mud(), dirt.tint());
		}
		if (body instanceof Player || body instanceof Npc) {
			soilBody(body, dirt.gain() * DRAG_DIRT, dirt.mud(), dirt.tint());
		}
	}

	/** (0.21.28) What a body dragged over the ground here is scraped with: so much dirt a step, muddy or dusty, and the dust's colour. */
	public record Dirt(float gain, boolean mud, int tint) {
	}

	/** (0.21.28) The dirt a body dragged a step over the ground under {@code body} picks up (walking's, before {@link #DRAG_DIRT}), or null. */
	public static @Nullable Dirt dragDirt(ServerLevel level, net.minecraft.world.entity.Entity body) {
		BlockPos under = body.getOnPos();
		BlockState ground = level.getBlockState(under);
		if (ground.isAir()) {
			ground = level.getBlockState(under.below());
		}
		boolean rained = body.isInWaterOrRain();
		Holder<Biome> biome = level.getBiome(under);
		if (isMud(ground)) {
			return new Dirt(MUD_GAIN, true, 0);
		}
		if (isEarth(ground) && (rained || level.isRainingAt(body.blockPosition()) || biome.is(ConventionalBiomeTags.IS_SWAMP))) {
			return new Dirt(WET_GROUND_GAIN, true, 0);
		}
		if (isEarth(ground) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.SUSPICIOUS_GRAVEL) || isRockDust(ground)) {
			// dragged, even dry earth grinds in
			return new Dirt(DUST_GAIN, false, groundTint(level, under, ground));
		}
		return null;
	}

	/**
	 * (0.21.28) A corpse's armour (Jaymo's Kitchen keeps it on the body), piece {@code index} (0 boots ... 3 helmet), dragged
	 * {@code steps} steps over {@code dirt}: scraped dirty as a living body's would be.
	 */
	public static void dragArmour(ItemStack stack, int index, Dirt dirt, int steps, net.minecraft.util.RandomSource random) {
		soilStack(stack, dirt.gain() * DRAG_DIRT * DRAG_REACH[Mth.clamp(index, 0, DRAG_REACH.length - 1)] * steps, dirt.mud(), dirt.tint(), random);
	}

	/** (0.21.28) The same, through water: {@code steps} steps' washing. */
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
	private static void soil(LivingEntity body, EquipmentSlot slot, float gain, boolean mud, int tint) {
		soilStack(body.getItemBySlot(slot), gain, mud, tint, body.getRandom());
	}

	/** Dirties {@code stack} by {@code gain} (fractions carry over by chance); dust takes on {@code tint} (0: plain tan). */
	static void soilStack(ItemStack stack, float gain, boolean mud, int tint, net.minecraft.util.RandomSource random) {
		if (stack.isEmpty() || gain <= 0.0F) {
			return;
		}
		Grime was = grime(stack);
		Grime now = soiled(was, gain, mud, tint, random);
		if (now != null && !now.equals(was)) {
			stack.set(GRIME, now);
		}
	}

	/** {@code was} dirtied by {@code gain} (fractions carry over by chance), or null for no change. */
	private static @Nullable Grime soiled(@Nullable Grime was, float gain, boolean mud, int tint, net.minecraft.util.RandomSource random) {
		int add = Mth.floor(gain) + (random.nextFloat() < gain - Mth.floor(gain) ? 1 : 0);
		if (add <= 0) {
			return null;
		}
		int amount = was == null ? 0 : was.amount();
		int muddy = was == null ? 0 : was.mud();
		int wasTint = was == null ? 0 : was.tint();
		if (amount >= MAX && (muddy >= MAX) == mud && (mud || wasTint == tint)) {
			return null;
		}
		int next = Math.min(MAX, amount + add);
		// the new dirt's share of the whole decides how muddy it now is, and the dust's colour
		float share = add / (float) (amount + add);
		int mudPart = Math.round((muddy * (float) amount + (mud ? MAX : 0) * (float) add) / (amount + add));
		int nextTint = mud ? wasTint : blendTint(wasTint, tint, amount == 0 ? 1.0F : Math.max(share, 0.15F));
		return new Grime(next, Mth.clamp(mudPart, 0, MAX), nextTint);
	}

	/** {@code from} toward {@code to} by {@code t} (0 is the plain tan). */
	private static int blendTint(int from, int to, float t) {
		int a = from == 0 ? DUST_TAN : from, b = to == 0 ? DUST_TAN : to;
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return from == 0 && to == 0 ? 0 : Math.max(1, r << 16 | g << 8 | bl);
	}

	/** Washes {@code amount} off the piece in {@code slot}; true if it had any on it. */
	static boolean wash(LivingEntity body, EquipmentSlot slot, int amount) {
		return clean(body.getItemBySlot(slot), amount);
	}

	static boolean washAll(LivingEntity body, int amount) {
		boolean any = com.jaymospowers.core.blood.Bloodied.cleanCoat(body, amount);
		any |= washBody(body, amount);
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
			stack.set(GRIME, new Grime(left, was.mud(), was.tint()));
		}
		return true;
	}

	static boolean anyDirt(LivingEntity body) {
		if (com.jaymospowers.core.blood.Bloodied.coat(body) != null || body(body) != null) {
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
			// scrub what you're wearing, a level of water each time; (Hardrock picks) and yourself (Smell)
			if (!anyDirt(player) && Smell.stink(player) <= 0.0F) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				washAll(player, SCRUB);
				Smell.scrub(player);
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
