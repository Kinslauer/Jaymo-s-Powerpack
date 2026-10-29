package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.aether.WindAether;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.62, Andy's NOEA picks) The afterlife: a Z Fighter who dies wakes in the Other World with a halo over his head, instead of
 * waking at his bed. The Other World is a site in the pack's own Aether ({@code jaymospowers:aether}, the dimension Wind rises into),
 * a sky island of holystone far from anywhere Wind arrives ({@link #SITE_X}, {@link #SITE_Z}), not a dimension of its own.
 * Everything vanilla and the pack do about a death still happens first (the drops or the kept inventory per the gamerule, Kitchen's
 * corpse, the downed state, the wounds): the death is let through untouched, and one tick after the normal respawn he is moved here
 * ({@link #arrive}). Only a player with the Z Fighter power, only a real death (a sparring bout, a tournament, Last Stand and the
 * rest cancel theirs before this sees it), not one already at the site ({@link #inOtherWorld}), and only while
 * {@code afterlife=true} in the server properties file ({@link ZFighterRealism}).
 * <p>
 * Whoever reaches the site alive (a Wind user rising into the Aether, say) may visit: no halo, no stay timer, nobody sends him
 * back; the master, the tournament and the training bonus are open to him as to anyone.
 * <p>
 * What is there, all built by hand once ({@link #build}, no structure files, on a round platform of Aether blocks at height
 * {@link #FLOOR}, with the air above it cleared), the NPCs put back by themselves whenever a player comes near ({@link #ensureNpcs}):
 * <ul>
 * <li><b>The judge's hall</b> at the arrival point: the Judge, who says what he thinks of you.</li>
 * <li><b>The road</b>, {@link #ROAD_LENGTH} blocks of quartz south of the hall, to a pavilion where the
 * <b>master of the road</b> waits (sneak and right-click him bare-handed: one lesson a stay, as a good master's, and a bonus of
 * {@link #MASTER_TP} TP); training at the site pays {@link #TRAINING_BONUS} times over ({@link #trainingBonus}).</li>
 * <li><b>The tournament of the dead</b>, a ring east of the pavilion: ring its bell for {@link #ROUNDS} bouts against spirit
 * fighters, one after the other, each harder and each paying TP. Win all of them and the fortune teller sends you back free.</li>
 * <li><b>The fortune teller</b>, west of the pavilion, who sends you back to where you woke in the world: free after the
 * tournament, for {@link #PRICE_EMERALDS} emeralds in your hand or {@link #PRICE_TP} TP (sneak and right-click), or by the
 * Eternal Dragon's wish ({@link ZFighterWishes#CALL_BACK}). Nobody is stuck: after {@link #STAY_TICKS} the Other World lets go.</li>
 * </ul>
 * (0.17.108, Andy: "you respawn in the afterlife, you can only leave with one of those ways") The dead are held: a death at the
 * site wakes him there again (the same stay, the same way home), and leaving any other way (falling off the island, a pearl,
 * a power, a command) brings him straight back to the hall ({@link #hold}). Only the fortune teller, the wish and the
 * {@link #STAY_TICKS} wait let him go. A living visitor is free to come and go as before.
 */
public final class ZFighterAfterlife {
	// ---- tuning ----
	/** A death's move is dropped if the respawn comes later than this (ticks), so a revived player is not carried off by an old death. */
	static final long PENDING_TICKS = 24000L;
	/** (0.17.108) One of the dead this far below the floor (blocks) has fallen off, and is taken back to the hall. */
	static final int FALL_LIMIT = 24;
	/** How long he stays at most (ticks) before the Other World lets him go. */
	static final long STAY_TICKS = 36000L;
	/**
	 * The site: the hall's middle in the Aether (blocks). Far from the lowland islands Wind arrives on (it arrives at the x and z it
	 * rose from, and nobody rises from out here).
	 */
	public static final int SITE_X = 20000;
	public static final int SITE_Z = 20000;
	/** The standing height of the site's floor (blocks of the platform are at {@code FLOOR - 1}), above the lowland islands and the broken islets. */
	static final int FLOOR = 200;
	/** The platform is an ellipse of these half-widths round this spot, in site coordinates (relative to {@link #SITE_X}, {@link #SITE_Z}). */
	static final int PLATFORM_CX = 5;
	static final int PLATFORM_CZ = 34;
	static final int PLATFORM_HX = 54;
	static final int PLATFORM_HZ = 46;
	/** How far the platform hangs below its top at the middle, and how high the air is cleared over it. */
	static final int PLATFORM_DEPTH = 6;
	static final int CLEAR_HEIGHT = 12;
	/** Within this many blocks of the platform's middle in the Aether, you are at the Other World. */
	public static final double SITE_RADIUS = 64.0;
	/** The road: south of the hall, this long; the pavilion, ring and tent are at its end. */
	static final int ROAD_LENGTH = 40;
	static final int ROAD_HALF_WIDTH = 2;
	static final int PAVILION_Z = ROAD_LENGTH + 12;
	static final int RING_X = 32;
	static final int RING_RADIUS = 12;
	static final int TENT_X = -26;
	/** A lesson's bonus TP, and how many times over training pays in the Other World. */
	static final int MASTER_TP = 150;
	public static final float TRAINING_BONUS = 1.5F;
	/** The tournament: bouts, the TP of each (times the round), the spirit fighters' health and the damage each round adds. */
	static final int ROUNDS = 3;
	static final int ROUND_TP = 60;
	static final double SPIRIT_HEALTH = 60.0;
	static final double SPIRIT_HEALTH_PER_ROUND = 30.0;
	static final double SPIRIT_DAMAGE = 3.0;
	static final double SPIRIT_DAMAGE_PER_ROUND = 1.5;
	/** The gauntlet is called off if the fighter wanders this far from the ring (blocks). */
	static final double RING_LEASH = 48.0;
	/** The fortune teller's price. */
	static final int PRICE_EMERALDS = 24;
	static final int PRICE_TP = 400;
	/** The effects of waking up again. */
	static final int WAKE_REGEN = 200;
	static final int WAKE_RESISTANCE = 200;
	/** An NPC is put back when a player comes this near the place it belongs (blocks). */
	static final double NPC_NEAR = 64.0;
	static final String JUDGE_TAG = "jaymospowers_judge";
	static final String MASTER_TAG = "jaymospowers_afterlife_master";
	static final String TELLER_TAG = "jaymospowers_fortune_teller";
	static final String SPIRIT_TAG = "jaymospowers_spirit_fighter";

	/** Where he woke in the world before he died (the dimension and the spot), and where the fortune teller sends him. */
	public record Home(ResourceKey<Level> dimension, BlockPos pos) {
		public static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(Home::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Home::pos)
		).apply(i, Home::new));
	}

	/**
	 * The game time he arrived, while he is among the dead. Synced to everyone (the halo) and saved; (0.17.108) kept through a
	 * death there, as are the three below, so dying again among the dead doesn't free him.
	 */
	public static final AttachmentType<Long> STAY = AttachmentRegistry.create(JaymosPowers.id("zfighter_afterlife_stay"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath().syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** Where he is sent back to. Saved. */
	public static final AttachmentType<Home> HOME = AttachmentRegistry.create(JaymosPowers.id("zfighter_afterlife_home"),
		builder -> builder.persistent(Home.CODEC).copyOnDeath());
	/** Whether the road's master has taught him this stay, and whether the tournament was won this stay. Saved. */
	public static final AttachmentType<Boolean> TAUGHT = AttachmentRegistry.create(JaymosPowers.id("zfighter_afterlife_taught"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	public static final AttachmentType<Boolean> CHAMPION = AttachmentRegistry.create(JaymosPowers.id("zfighter_afterlife_champion"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** On the Aether's level: the Other World's site has been built. Saved. */
	public static final AttachmentType<Boolean> BUILT = AttachmentRegistry.create(JaymosPowers.id("zfighter_afterlife_built"),
		builder -> builder.persistent(Codec.BOOL));

	/** A death waiting for its respawn (game time of the death), and a move waiting for next tick (the tick to do it on). */
	private static final Map<UUID, Long> PENDING = new HashMap<>();
	private static final Map<UUID, Long> ARRIVALS = new HashMap<>();

	/** A tournament in progress: the round, the spirit fighter in the ring, and whether he fell. */
	private static final class Gauntlet {
		int round = 1;
		UUID foe;
		boolean down;
	}

	private static final Map<UUID, Gauntlet> GAUNTLETS = new HashMap<>();

	private ZFighterAfterlife() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				// (0.17.108) and one of the dead dying again wakes among the dead again
				if (ZFighterRealism.afterlife() && !player.isSpectator() && (among(player) || !inOtherWorld(player)
					&& Powers.has(player, ZFighterPower.INSTANCE))) {
					PENDING.put(player.getUUID(), player.level().getGameTime());
				}
				endGauntlet(player);
			} else if (entity.entityTags().contains(SPIRIT_TAG)) {
				for (Gauntlet gauntlet : GAUNTLETS.values()) {
					if (entity.getUUID().equals(gauntlet.foe)) {
						gauntlet.down = true;
					}
				}
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Long died = PENDING.remove(newPlayer.getUUID());
			if (!alive && died != null && newPlayer.level().getGameTime() - died <= PENDING_TICKS) {
				ARRIVALS.put(newPlayer.getUUID(), newPlayer.level().getGameTime() + 1L);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			ARRIVALS.clear();
			GAUNTLETS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(ZFighterAfterlife::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			boolean judge = entity.entityTags().contains(JUDGE_TAG);
			boolean master = entity.entityTags().contains(MASTER_TAG);
			boolean teller = entity.entityTags().contains(TELLER_TAG);
			if (!judge && !master && !teller || ZFighterNpcs.viaMca(entity)) {
				// ((0.17.146) as MCA villagers, their menus lead to their talk: talk)
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				if (judge) {
					judge(server, entity);
				} else if (master) {
					teach(server, entity);
				} else {
					tell(server, entity);
				}
			}
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !inSite(level, hit.getBlockPos().getX(), hit.getBlockPos().getZ())
				|| !level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				ring(server, hit.getBlockPos());
			}
			return InteractionResult.SUCCESS;
		});
	}

	// ---------------------------------------------------------------- the dying

	/** Training at the Other World pays this many times over (1 anywhere else). */
	public static float trainingBonus(ServerPlayer player) {
		return inOtherWorld(player) ? TRAINING_BONUS : 1.0F;
	}

	/** Whether (x, z) in this level is within {@link #SITE_RADIUS} of the site, in the Aether. */
	public static boolean inSite(Level level, double x, double z) {
		if (level.dimension() != WindAether.AETHER) {
			return false;
		}
		double dx = x - (SITE_X + PLATFORM_CX);
		double dz = z - (SITE_Z + PLATFORM_CZ);
		return dx * dx + dz * dz <= SITE_RADIUS * SITE_RADIUS;
	}

	/** Whether this entity is at the Other World: within {@link #SITE_RADIUS} of the site in the Aether. */
	public static boolean inOtherWorld(Entity entity) {
		return inSite(entity.level(), entity.getX(), entity.getZ());
	}

	public static boolean among(net.minecraft.world.entity.player.Player player) {
		return player.hasAttached(STAY);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!ARRIVALS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = ARRIVALS.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Long> entry = it.next();
				if (entry.getValue() > now) {
					continue;
				}
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null && player.isAlive() && !inOtherWorld(player)) {
					arrive(server, player);
				}
			}
		}
		if (now % 20 != 0) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			Long since = player.getAttached(STAY);
			if (since == null) {
				continue;
			}
			if (now - since >= STAY_TICKS) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_let_go").withStyle(ChatFormatting.AQUA));
				returnHome(player);
			} else if (player.isAlive() && !ARRIVALS.containsKey(player.getUUID()) && (!inOtherWorld(player) || player.getY() < FLOOR - FALL_LIMIT)) {
				hold(server, player);
			}
		}
		ServerLevel world = server.getLevel(WindAether.AETHER);
		if (world == null) {
			return;
		}
		boolean visited = false;
		for (ServerPlayer player : world.players()) {
			if (inOtherWorld(player)) {
				visited = true;
				break;
			}
		}
		if (visited && Boolean.TRUE.equals(world.getAttached(BUILT))) {
			ensureNpcs(world);
		}
		if (!GAUNTLETS.isEmpty()) {
			gauntlets(server, world);
		}
	}

	/**
	 * (0.17.108) One of the dead got away some other way than the three (fell off the island, a pearl, a power, a command): the
	 * Other World takes him straight back to the hall.
	 */
	private static void hold(MinecraftServer server, ServerPlayer player) {
		ServerLevel world = server.getLevel(WindAether.AETHER);
		if (world == null) {
			return;
		}
		endGauntlet(player);
		double ax = SITE_X + 0.5;
		double az = SITE_Z + 5.5;
		player.teleportTo(world, ax, FLOOR, az, Set.of(), 180.0F, 0.0F, true);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		world.sendParticles(ParticleTypes.END_ROD, ax, FLOOR + 1.0, az, 20, 0.4, 0.8, 0.4, 0.05);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_held").withStyle(ChatFormatting.AQUA));
	}

	/** He wakes among the dead: the site is built if it has not been, he is moved there with his halo. */
	private static void arrive(MinecraftServer server, ServerPlayer player) {
		ServerLevel world = server.getLevel(WindAether.AETHER);
		if (world == null) {
			return;
		}
		if (!Boolean.TRUE.equals(world.getAttached(BUILT))) {
			build(world);
			world.setAttached(BUILT, true);
		}
		// (0.17.108) dying again among the dead keeps the stay he had (its clock, his way home, what he won)
		if (!among(player)) {
			player.setAttached(HOME, new Home(player.level().dimension(), player.blockPosition()));
			player.setAttached(STAY, player.level().getGameTime());
			player.removeAttached(TAUGHT);
			player.removeAttached(CHAMPION);
		}
		double ax = SITE_X + 0.5;
		double az = SITE_Z + 5.5;
		player.teleportTo(world, ax, FLOOR, az, Set.of(), 180.0F, 0.0F, true);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		world.sendParticles(ParticleTypes.END_ROD, ax, FLOOR + 1.0, az, 40, 0.4, 0.8, 0.4, 0.05);
		world.playSound(null, ax, FLOOR, az, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_wake").withStyle(ChatFormatting.AQUA));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_halo").withStyle(ChatFormatting.AQUA));
	}

	/** Back among the living: to where he woke in the world, whole, the halo gone. */
	static void returnHome(ServerPlayer player) {
		endGauntlet(player);
		MinecraftServer server = player.level().getServer();
		Home home = player.getAttached(HOME);
		ServerLevel to = home == null ? null : server.getLevel(home.dimension());
		BlockPos at;
		if (to == null || inSite(to, home.pos().getX(), home.pos().getZ())) {
			to = server.overworld();
			at = home != null && home.dimension() == to.dimension() ? home.pos() : to.getRespawnData().pos();
		} else {
			at = home.pos();
		}
		to.getChunk(at);
		player.removeAttached(STAY);
		player.removeAttached(TAUGHT);
		player.removeAttached(CHAMPION);
		player.teleportTo(to, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, WAKE_REGEN, 1));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, WAKE_RESISTANCE, 1));
		to.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.05);
		to.playSound(null, at, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_back").withStyle(ChatFormatting.GREEN));
	}

	/** The Eternal Dragon's wish ({@link ZFighterWishes#CALL_BACK}): everyone among the dead is called back. False if none was. */
	static boolean callBack(ServerPlayer wisher) {
		boolean any = false;
		for (ServerPlayer player : new ArrayList<>(wisher.level().getServer().getPlayerList().getPlayers())) {
			if (among(player)) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_called", wisher.getDisplayName())
					.withStyle(ChatFormatting.GREEN));
				returnHome(player);
				any = true;
			}
		}
		if (!any) {
			wisher.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_no_dead").withStyle(ChatFormatting.GRAY));
		}
		return any;
	}

	// ---------------------------------------------------------------- the people

	private static void judge(ServerPlayer player, net.minecraft.world.entity.Entity judge) {
		int line = 1 + player.getRandom().nextInt(3);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_judge_" + line, judge.getDisplayName())
			.withStyle(ChatFormatting.AQUA));
	}

	/** The road's master: one lesson a stay (a good master's), and a bonus of TP. */
	private static void teach(ServerPlayer player, net.minecraft.world.entity.Entity master) {
		teach(player, master, false);
	}

	/** (0.17.146) {@code asked}: asked for on his talk screen (no sneaking up to him). */
	private static void teach(ServerPlayer player, net.minecraft.world.entity.Entity master, boolean asked) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_master_no_spark", master.getDisplayName())
				.withStyle(ChatFormatting.AQUA));
			return;
		}
		if (!asked && (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_master_hint", master.getDisplayName())
				.withStyle(ChatFormatting.AQUA));
			return;
		}
		if (Boolean.TRUE.equals(player.getAttached(TAUGHT)) && !player.isCreative()) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_master_again", master.getDisplayName())
				.withStyle(ChatFormatting.AQUA));
			return;
		}
		player.setAttached(TAUGHT, true);
		Component lesson = ZFighterMasters.lesson(player, false);
		ZFighterTraining.grant(player, ZFighterMasters.LESSON_TP + MASTER_TP);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_master_teaches", master.getDisplayName(), lesson,
			ZFighterMasters.LESSON_TP + MASTER_TP).withStyle(ChatFormatting.AQUA));
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** The fortune teller: free after the tournament, or sneak and right-click to pay (emeralds in hand first, else TP). */
	private static void tell(ServerPlayer player, net.minecraft.world.entity.Entity teller) {
		if (!among(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_teller_alive", teller.getDisplayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (Boolean.TRUE.equals(player.getAttached(CHAMPION))) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_teller_champion", teller.getDisplayName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			returnHome(player);
			return;
		}
		if (!player.isShiftKeyDown()) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_teller_offer", teller.getDisplayName(),
				PRICE_EMERALDS, PRICE_TP).withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (held.is(Items.EMERALD) && held.getCount() >= PRICE_EMERALDS) {
			held.shrink(PRICE_EMERALDS);
		} else {
			ZFighterTraining.Data data = ZFighterTraining.of(player);
			if (data.tp() < PRICE_TP) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_teller_poor", teller.getDisplayName(),
					PRICE_EMERALDS, PRICE_TP).withStyle(ChatFormatting.LIGHT_PURPLE));
				return;
			}
			player.setAttached(ZFighterTraining.DATA, data.withTp(data.tp() - PRICE_TP));
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_teller_paid", teller.getDisplayName())
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		returnHome(player);
	}

	// ---------------------------------------------------------------- talking (0.17.146)

	/** A character of the Other World's talk (MCA's "Talk about work"): the Judge, the road's master, the fortune teller. */
	static void talk(ServerPlayer player, LivingEntity npc, String tag) {
		Runnable back = () -> talk(player, npc, tag);
		switch (tag) {
			case JUDGE_TAG -> ZFighterNpcs.ask(player, npc, Component.translatable("talk.jaymospowers.judge.greet"), List.of(
				ZFighterNpcs.Option.of("talk.jaymospowers.judge.ask", true, () -> ZFighterNpcs.reply(player, npc, () -> judge(player, npc), back)),
				ZFighterNpcs.Option.of("talk.jaymospowers.judge.ring", true, () -> ZFighterNpcs.ask(player, npc,
					Component.translatable(Boolean.TRUE.equals(player.getAttached(CHAMPION)) ? "talk.jaymospowers.judge.ring_won" : "talk.jaymospowers.judge.ring_how", ROUNDS),
					List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back))))));
			case MASTER_TAG -> {
				boolean taught = Boolean.TRUE.equals(player.getAttached(TAUGHT)) && !player.isCreative();
				ZFighterNpcs.ask(player, npc, Component.translatable(taught ? "talk.jaymospowers.road_master.greet_taught" : "talk.jaymospowers.road_master.greet"),
					List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.lesson", !taught && Powers.has(player, ZFighterPower.INSTANCE),
						() -> ZFighterNpcs.reply(player, npc, () -> teach(player, npc, true), back))));
			}
			default -> {
				if (!among(player) || Boolean.TRUE.equals(player.getAttached(CHAMPION))) {
					ZFighterNpcs.ask(player, npc, Component.translatable(among(player) ? "talk.jaymospowers.teller.champion" : "talk.jaymospowers.teller.alive"),
						among(player) ? List.of(ZFighterNpcs.Option.of("talk.jaymospowers.teller.go", true, () -> ZFighterNpcs.reply(player, npc, () -> tell(player, npc), back)))
							: List.of());
					return;
				}
				boolean emeralds = count(player, Items.EMERALD) >= PRICE_EMERALDS;
				boolean tp = ZFighterTraining.of(player).tp() >= PRICE_TP;
				ZFighterNpcs.ask(player, npc, Component.translatable("talk.jaymospowers.teller.greet", PRICE_EMERALDS, PRICE_TP), List.of(
					ZFighterNpcs.Option.of("talk.jaymospowers.teller.pay_emeralds", emeralds, () -> ZFighterNpcs.reply(player, npc, () -> pay(player, npc, true), back), PRICE_EMERALDS),
					ZFighterNpcs.Option.of("talk.jaymospowers.teller.pay_tp", tp, () -> ZFighterNpcs.reply(player, npc, () -> pay(player, npc, false), back), PRICE_TP)));
			}
		}
	}

	/** (0.17.146) Paid from the talk screen: {@link #PRICE_EMERALDS} emeralds from anywhere in the pack, or {@link #PRICE_TP} TP. */
	private static void pay(ServerPlayer player, LivingEntity teller, boolean emeralds) {
		if (!among(player)) {
			return;
		}
		if (emeralds) {
			if (count(player, Items.EMERALD) < PRICE_EMERALDS) {
				return;
			}
			int left = PRICE_EMERALDS;
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(Items.EMERALD)) {
					int take = Math.min(left, stack.getCount());
					stack.shrink(take);
					left -= take;
				}
			}
		} else {
			ZFighterTraining.Data data = ZFighterTraining.of(player);
			if (data.tp() < PRICE_TP) {
				return;
			}
			player.setAttached(ZFighterTraining.DATA, data.withTp(data.tp() - PRICE_TP));
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_teller_paid", teller.getDisplayName())
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		returnHome(player);
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the tournament of the dead

	private static BlockPos ringCentre() {
		return new BlockPos(SITE_X + RING_X, FLOOR - 1, SITE_Z + PAVILION_Z);
	}

	private static void ring(ServerPlayer player, BlockPos bell) {
		ServerLevel level = player.level();
		level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.8F);
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		if (GAUNTLETS.containsKey(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_ring_busy").withStyle(ChatFormatting.AQUA));
			return;
		}
		if (Boolean.TRUE.equals(player.getAttached(CHAMPION))) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_ring_won").withStyle(ChatFormatting.AQUA));
			return;
		}
		Gauntlet gauntlet = new Gauntlet();
		GAUNTLETS.put(player.getUUID(), gauntlet);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_ring_start", ROUNDS).withStyle(ChatFormatting.AQUA));
		spawnSpirit(level, player, gauntlet);
	}

	private static void spawnSpirit(ServerLevel level, ServerPlayer player, Gauntlet gauntlet) {
		Vindicator spirit = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
		if (spirit == null) {
			endGauntlet(player);
			return;
		}
		double angle = level.getRandom().nextDouble() * Mth.TWO_PI;
		BlockPos c = ringCentre();
		spirit.snapTo(c.getX() + 0.5 + Math.cos(angle) * 8.0, FLOOR, c.getZ() + 0.5 + Math.sin(angle) * 8.0, level.getRandom().nextFloat() * 360.0F, 0.0F);
		spirit.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SPIRIT_HEALTH + SPIRIT_HEALTH_PER_ROUND * (gauntlet.round - 1));
		spirit.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SPIRIT_DAMAGE + SPIRIT_DAMAGE_PER_ROUND * (gauntlet.round - 1));
		spirit.setHealth(spirit.getMaxHealth());
		spirit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		spirit.setCustomName(Component.translatable("entity.jaymospowers.spirit_fighter", gauntlet.round));
		spirit.setCustomNameVisible(true);
		spirit.setGlowingTag(true);
		spirit.addTag(SPIRIT_TAG);
		spirit.setPersistenceRequired();
		spirit.setTarget(player);
		gauntlet.foe = spirit.getUUID();
		gauntlet.down = false;
		level.addFreshEntity(spirit);
		level.sendParticles(ParticleTypes.SOUL, spirit.getX(), spirit.getY() + 1.0, spirit.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
	}

	/** Each second: a spirit fighter down brings the next, the last brings the title; a fighter who left or fell calls it off. */
	private static void gauntlets(MinecraftServer server, ServerLevel world) {
		for (Iterator<Map.Entry<UUID, Gauntlet>> it = GAUNTLETS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Gauntlet> entry = it.next();
			Gauntlet gauntlet = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != world || !inOtherWorld(player)
				|| player.distanceToSqr(Vec3.atCenterOf(ringCentre())) > RING_LEASH * RING_LEASH) {
				discardFoe(world, gauntlet);
				it.remove();
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_ring_off").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (!gauntlet.down) {
				if (!(world.getEntity(gauntlet.foe) instanceof LivingEntity foe) || !foe.isAlive()) {
					// gone without falling (a reload): the bout starts over
					spawnSpirit(world, player, gauntlet);
				}
				continue;
			}
			ZFighterTraining.grant(player, ROUND_TP * gauntlet.round);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_ring_won_round", gauntlet.round, ROUND_TP * gauntlet.round)
				.withStyle(ChatFormatting.AQUA));
			if (gauntlet.round >= ROUNDS) {
				player.setAttached(CHAMPION, true);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_afterlife_ring_champion").withStyle(ChatFormatting.GOLD));
				world.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
				it.remove();
				continue;
			}
			gauntlet.round++;
			spawnSpirit(world, player, gauntlet);
		}
	}

	private static void discardFoe(ServerLevel world, Gauntlet gauntlet) {
		if (gauntlet.foe != null && world.getEntity(gauntlet.foe) instanceof Mob foe) {
			foe.discard();
		}
	}

	private static void endGauntlet(ServerPlayer player) {
		Gauntlet gauntlet = GAUNTLETS.remove(player.getUUID());
		if (gauntlet != null && player.level().getServer().getLevel(WindAether.AETHER) instanceof ServerLevel world) {
			discardFoe(world, gauntlet);
		}
	}

	// ---------------------------------------------------------------- the NPCs

	private record Npc(String tag, String name, Vec3 at) {
	}

	private static List<Npc> npcs() {
		return List.of(
			new Npc(JUDGE_TAG, "entity.jaymospowers.afterlife_judge", new Vec3(SITE_X + 0.5, FLOOR, SITE_Z - 3.5)),
			new Npc(MASTER_TAG, "entity.jaymospowers.afterlife_master", new Vec3(SITE_X + 0.5, FLOOR, SITE_Z + PAVILION_Z - 3.5)),
			new Npc(TELLER_TAG, "entity.jaymospowers.fortune_teller", new Vec3(SITE_X + TENT_X + 0.5, FLOOR, SITE_Z + PAVILION_Z + 0.5)));
	}

	/** Puts back any NPC missing from where a player is near (they never leave and cannot be hurt, but a chunk can lose them). */
	private static void ensureNpcs(ServerLevel world) {
		for (Npc npc : npcs()) {
			BlockPos spot = BlockPos.containing(npc.at());
			boolean near = false;
			for (ServerPlayer player : world.players()) {
				if (player.distanceToSqr(npc.at()) <= NPC_NEAR * NPC_NEAR) {
					near = true;
					break;
				}
			}
			if (!near || !world.hasChunkAt(spot)) {
				continue;
			}
			AABB around = new AABB(spot).inflate(6.0, 4.0, 6.0);
			if (world.getEntitiesOfClass(LivingEntity.class, around, e -> e.entityTags().contains(npc.tag())).isEmpty()) {
				ZFighterSites.spawnNpc(world, npc.at(), npc.tag(), npc.name(), new MerchantOffers());
			}
		}
	}

	// ---------------------------------------------------------------- building

	/** Sets a block at (x, y, z) in site coordinates (x and z from the site's origin; y is the world's). */
	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(SITE_X + x, y, SITE_Z + z), state, Block.UPDATE_CLIENTS);
	}

	/**
	 * The platform in the sky: an ellipse of grass over dirt over holystone that hangs deeper at its middle (so it reads as a small
	 * island from below), with the air over it cleared of any islet that drifts there. Built before the rest, which overwrites its top.
	 */
	private static void platform(ServerLevel level) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState grass = AetherBlocks.AETHER_GRASS_BLOCK.defaultBlockState();
		BlockState dirt = AetherBlocks.AETHER_DIRT.defaultBlockState();
		BlockState stone = AetherBlocks.HOLYSTONE.defaultBlockState();
		for (int x = PLATFORM_CX - PLATFORM_HX; x <= PLATFORM_CX + PLATFORM_HX; x++) {
			for (int z = PLATFORM_CZ - PLATFORM_HZ; z <= PLATFORM_CZ + PLATFORM_HZ; z++) {
				double u = Math.pow((x - PLATFORM_CX) / (double)PLATFORM_HX, 2.0) + Math.pow((z - PLATFORM_CZ) / (double)PLATFORM_HZ, 2.0);
				if (u > 1.0) {
					continue;
				}
				int depth = 1 + (int)Math.round((PLATFORM_DEPTH - 1) * (1.0 - Math.sqrt(u)));
				for (int up = 0; up < CLEAR_HEIGHT; up++) {
					BlockPos at = new BlockPos(SITE_X + x, FLOOR + up, SITE_Z + z);
					if (!level.getBlockState(at).isAir()) {
						level.setBlock(at, air, Block.UPDATE_CLIENTS);
					}
				}
				set(level, x, FLOOR - 1, z, grass);
				for (int down = 2; down <= depth + 1; down++) {
					set(level, x, FLOOR - down, z, down == 2 ? dirt : stone);
				}
			}
		}
	}

	/** The platform, the hall, the road, the pavilion, the ring and the tent: built once, the first time anyone wakes here. */
	private static void build(ServerLevel level) {
		platform(level);
		BlockState quartz = Blocks.QUARTZ_BRICKS.defaultBlockState();
		BlockState smooth = Blocks.SMOOTH_QUARTZ.defaultBlockState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
		BlockState slab = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
		// the judge's hall: a smooth floor, a ring of pillars and a flat roof, open to the south
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				set(level, x, FLOOR - 1, z, (x + z) % 2 == 0 ? smooth : quartz);
			}
		}
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				boolean wall = z == -6 || Math.abs(x) == 6 && z < 5;
				boolean column = (Math.abs(x) == 6 || z == -6) && (x % 3 == 0 || z % 3 == 0);
				for (int up = 0; up < 5; up++) {
					set(level, x, FLOOR + up, z, wall && up < 4 && !column ? Blocks.GLASS.defaultBlockState()
						: column ? pillar : Blocks.AIR.defaultBlockState());
				}
				set(level, x, FLOOR + 5, z, slab);
			}
		}
		set(level, 0, FLOOR, -5, chiseled);
		set(level, 0, FLOOR + 1, -5, Blocks.SOUL_LANTERN.defaultBlockState());
		set(level, -2, FLOOR, -3, chiseled);
		set(level, 2, FLOOR, -3, chiseled);
		// the road
		for (int z = 8; z <= PAVILION_Z; z++) {
			for (int x = -ROAD_HALF_WIDTH; x <= ROAD_HALF_WIDTH; x++) {
				set(level, x, FLOOR - 1, z, z % 8 == 0 ? chiseled : quartz);
			}
			if (z % 10 == 0) {
				for (int side : new int[] {-ROAD_HALF_WIDTH - 1, ROAD_HALF_WIDTH + 1}) {
					set(level, side, FLOOR - 1, z, chiseled);
					set(level, side, FLOOR, z, pillar);
					set(level, side, FLOOR + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
				}
			}
		}
		// the pavilion: a round platform with four pillars and a roof
		disc(level, 0, PAVILION_Z, 9, smooth);
		for (int[] corner : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			for (int up = 0; up < 5; up++) {
				set(level, corner[0], FLOOR + up, PAVILION_Z + corner[1], pillar);
			}
			set(level, corner[0], FLOOR + 5, PAVILION_Z + corner[1], chiseled);
		}
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				set(level, x, FLOOR + 5, PAVILION_Z + z, slab);
			}
		}
		set(level, 0, FLOOR + 4, PAVILION_Z, Blocks.SOUL_LANTERN.defaultBlockState());
		// the tournament ring: a wide floor with a raised rim and the bell on a plinth
		disc(level, RING_X, PAVILION_Z, RING_RADIUS, smooth);
		for (int x = -RING_RADIUS; x <= RING_RADIUS; x++) {
			for (int z = -RING_RADIUS; z <= RING_RADIUS; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > RING_RADIUS - 1.0 && d <= RING_RADIUS + 0.5) {
					set(level, RING_X + x, FLOOR - 1, PAVILION_Z + z, chiseled);
				}
			}
		}
		set(level, RING_X, FLOOR, PAVILION_Z, chiseled);
		set(level, RING_X, FLOOR + 1, PAVILION_Z, Blocks.BELL.defaultBlockState());
		for (int x = 9; x <= RING_X - RING_RADIUS; x++) {
			for (int z = -1; z <= 1; z++) {
				set(level, x, FLOOR - 1, PAVILION_Z + z, quartz);
			}
		}
		// the fortune teller's tent: a purple floor, four posts, a purpur roof
		disc(level, TENT_X, PAVILION_Z, 5, Blocks.PURPUR_BLOCK.defaultBlockState());
		for (int[] corner : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			for (int up = 0; up < 4; up++) {
				set(level, TENT_X + corner[0], FLOOR + up, PAVILION_Z + corner[1], Blocks.PURPUR_PILLAR.defaultBlockState());
			}
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				set(level, TENT_X + x, FLOOR + 4, PAVILION_Z + z, Blocks.PURPUR_SLAB.defaultBlockState());
			}
		}
		set(level, TENT_X, FLOOR + 3, PAVILION_Z, Blocks.SOUL_LANTERN.defaultBlockState());
		set(level, TENT_X + 2, FLOOR, PAVILION_Z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
		for (int x = TENT_X + 5; x <= -9; x++) {
			for (int z = -1; z <= 1; z++) {
				set(level, x, FLOOR - 1, PAVILION_Z + z, quartz);
			}
		}
	}

	private static void disc(ServerLevel level, int cx, int cz, int radius, BlockState floor) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (x * x + z * z <= radius * radius + 1) {
					set(level, cx + x, FLOOR - 1, cz + z, floor);
				}
			}
		}
	}
}
