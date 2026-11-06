package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * (0.7.641) Many stakes, like arrows: every skewer that bites lodges (up to {@link #MAX}), each at its own spot on the body
 * ({@link Stake#seed}, drawn by {@code StakeLayer}), and each one more deepens the slow ({@link #slowAmplifier}: one is
 * Slowness V, two VI, three or more pin them; from two on they're weakened too). They come out one at a time.
 * <p>
 * (0.7.640) A wooden skewer that bites a vampire ({@link Vampires#staked}) stays in it: the stake leaves the hand that drove
 * it and the vampire is slowed to a crawl (Slowness V) until it comes out. A vampire player pulls it out by holding sneak
 * for {@link #PULL_TICKS}; a vampire mob tears it out after {@link #MOB_PULL_TICKS}; anyone with an empty hand can pull it
 * out by using the staked (it goes to their hand); one stake each time. Out, it drops at the vampire's feet (or into the puller's hand); on
 * death it drops with the body. Kept on the vampire ({@link #STAKE}) across saves. One stake at a time.
 * (0.7.641) Synced to everyone near, who see it sticking out of the chest ({@code StakeLayer}).
 */
public final class Stakes {
	/** One stake in a vampire: the skewer, and the seed that picks where on the body it went in. */
	public record Stake(ItemStack stack, int seed) {
		static final Codec<Stake> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(Stake::stack),
			Codec.INT.fieldOf("seed").forGetter(Stake::seed)
		).apply(i, Stake::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Stake> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Stake::stack, ByteBufCodecs.INT, Stake::seed, Stake::new);
	}

	/** The stakes in a vampire, oldest first (synced to everyone near, who see them: {@code StakeLayer}). */
	public static final AttachmentType<List<Stake>> STAKE = AttachmentRegistry.create(JaymosKingdoms.id("vampire_stakes"),
		builder -> builder.persistent(Stake.CODEC.listOf()).syncWith(Stake.STREAM_CODEC.apply(ByteBufCodecs.list()), (target, viewer) -> true));
	/** At most this many in one vampire. */
	static final int MAX = 8;
	/** Slowness refreshed every check, for this long. */
	static final int SLOW_TICKS = 30, CHECK_EVERY = 5;
	/** A player holds sneak this long (ticks) to pull it out; a mob tears it out after this long. */
	static final int PULL_TICKS = 60, MOB_PULL_TICKS = 400;

	/** Staked vampires loaded now, and how long each has worked at it (ticks: a player's sneak held, a mob's time in). */
	private static final Map<UUID, LivingEntity> STAKED = new HashMap<>();
	private static final Map<UUID, Integer> WORK = new HashMap<>();

	private Stakes() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
			if (taken > 0.0F && entity.isAlive() && !blocked && Vampires.isVampire(entity) && count(entity) < MAX
				&& source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
				&& isStake(attacker.getMainHandItem())) {
				ItemStack held = attacker.getMainHandItem();
				ItemStack stake = held.copyWithCount(1);
				if (!(attacker instanceof Player p && p.getAbilities().instabuild)) {
					held.shrink(1);
				}
				lodge(entity, stake);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && living.hasAttached(STAKE)) {
				STAKED.put(living.getUUID(), living);
			}
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.player.hasAttached(STAKE)) {
				STAKED.put(handler.player.getUUID(), handler.player);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			STAKED.remove(entity.getUUID());
			WORK.remove(entity.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			while (staked(entity)) {
				pull(entity, null);
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof LivingEntity staked)
				|| !staked(staked) || !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			pull(staked, player);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Stakes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STAKED.clear();
			WORK.clear();
		});
	}

	static boolean isStake(ItemStack stack) {
		return !stack.isEmpty() && Vampires.STAKE_ITEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}

	public static boolean staked(LivingEntity e) {
		return count(e) > 0;
	}

	static int count(LivingEntity e) {
		List<Stake> stakes = e.getAttached(STAKE);
		return stakes == null ? 0 : stakes.size();
	}

	/** Slowness's amplifier for so many stakes: V for one, VI for two, VII (pinned) from three. */
	static int slowAmplifier(int count) {
		return Math.min(3 + count, 6);
	}

	private static void slow(LivingEntity vampire, int count) {
		vampire.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, slowAmplifier(count), false, false, true));
		if (count >= 2) {
			vampire.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SLOW_TICKS, Math.min(count - 2, 3), false, false, true));
		}
	}

	private static void lodge(LivingEntity vampire, ItemStack stake) {
		List<Stake> stakes = new ArrayList<>(vampire.getAttachedOrElse(STAKE, List.of()));
		stakes.add(new Stake(stake, vampire.getRandom().nextInt()));
		vampire.setAttached(STAKE, List.copyOf(stakes));
		STAKED.put(vampire.getUUID(), vampire);
		WORK.remove(vampire.getUUID());
		slow(vampire, stakes.size());
		if (vampire.level() instanceof ServerLevel level) {
			level.playSound(null, vampire.getX(), vampire.getY(), vampire.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.9F, 0.7F);
		}
	}

	/** The newest stake comes out: into {@code puller}'s hand, or dropped at the vampire's feet. */
	private static void pull(LivingEntity vampire, Player puller) {
		List<Stake> stakes = new ArrayList<>(vampire.getAttachedOrElse(STAKE, List.of()));
		ItemStack stake = stakes.isEmpty() ? ItemStack.EMPTY : stakes.removeLast().stack();
		WORK.remove(vampire.getUUID());
		vampire.removeEffect(MobEffects.SLOWNESS);
		vampire.removeEffect(MobEffects.WEAKNESS);
		if (stakes.isEmpty()) {
			vampire.removeAttached(STAKE);
			STAKED.remove(vampire.getUUID());
		} else {
			vampire.setAttached(STAKE, List.copyOf(stakes));
			slow(vampire, stakes.size());
		}
		if (!(vampire.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(new DustParticleOptions(0x5A0A0A, 1.0F), vampire.getX(), vampire.getY(0.65), vampire.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, vampire.getX(), vampire.getY(), vampire.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (stake.isEmpty()) {
			return;
		}
		if (puller != null && puller.getMainHandItem().isEmpty()) {
			puller.setItemInHand(InteractionHand.MAIN_HAND, stake);
			return;
		}
		ItemEntity drop = new ItemEntity(level, vampire.getX(), vampire.getY(0.5), vampire.getZ(), stake);
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}

	private static void tick(MinecraftServer server) {
		if (STAKED.isEmpty() || server.overworld().getGameTime() % CHECK_EVERY != 0) {
			return;
		}
		for (LivingEntity e : new ArrayList<>(STAKED.values())) {
			if (e.isRemoved() || !e.isAlive()) {
				STAKED.remove(e.getUUID());
				WORK.remove(e.getUUID());
				continue;
			}
			if (!staked(e)) {
				STAKED.remove(e.getUUID());
				continue;
			}
			// cured, or never truly one: they slide out
			if (!Vampires.isVampire(e)) {
				while (staked(e)) {
					pull(e, null);
				}
				continue;
			}
			slow(e, count(e));
			int work = WORK.getOrDefault(e.getUUID(), 0);
			if (e instanceof ServerPlayer player) {
				work = player.isShiftKeyDown() ? work + CHECK_EVERY : 0;
				if (work > 0 && e.level() instanceof ServerLevel level && work % 20 == 0) {
					level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 0.6F);
				}
				if (work >= PULL_TICKS) {
					pull(e, null);
					continue;
				}
			} else if (e instanceof PathfinderMob) {
				work += CHECK_EVERY;
				if (work >= MOB_PULL_TICKS) {
					pull(e, null);
					continue;
				}
			}
			WORK.put(e.getUUID(), work);
			if (e.level() instanceof ServerLevel level && e.getRandom().nextInt(4) == 0) {
				level.sendParticles(new DustParticleOptions(0x5A0A0A, 0.8F), e.getX(), e.getY(0.6), e.getZ(), 1, 0.1, 0.05, 0.1, 0.0);
			}
		}
	}
}
