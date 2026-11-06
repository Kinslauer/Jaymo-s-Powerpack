package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
 * (0.7.641) A vampire player works one out with Sneak + scroll up ({@link #tug}), a held meter, never by holding sneak;
 * staked mobs are seen struggling with theirs the whole time, players while they tug ({@link #STRUGGLE}).
 * <p>
 * (0.7.640) A wooden skewer that bites a vampire ({@link Vampires#staked}) stays in it: the stake leaves the hand that drove
 * it and the vampire is slowed to a crawl (Slowness V) until it comes out. A vampire player pulls it out (0.7.641: by tugs);
 * a vampire mob tears it out after {@link #MOB_PULL_TICKS}; anyone with an empty hand can pull it
 * out by using the staked (it goes to their hand); one stake each time. Out, it drops at the vampire's feet (or into the puller's hand); on
 * death it drops with the body. Kept on the vampire ({@link #STAKE}) across saves. One stake at a time.
 * (0.7.641) Synced to everyone near, who see it sticking out of the chest ({@code StakeLayer}).
 */
public final class Stakes {
	/** One stake in a vampire: the skewer, and the seed that picks where on the body it went in. */
	public record Stake(ItemStack stack, int seed, int tugs) {
		static final Codec<Stake> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(Stake::stack),
			Codec.INT.fieldOf("seed").forGetter(Stake::seed),
			Codec.INT.optionalFieldOf("tugs", 0).forGetter(Stake::tugs)
		).apply(i, Stake::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Stake> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Stake::stack, ByteBufCodecs.INT, Stake::seed, ByteBufCodecs.VAR_INT, Stake::tugs, Stake::new);
	}

	/**
	 * (0.7.641) A vampire player's tug at their newest stake: Sneak + scroll up ({@code StakeStruggle} on the client). Each
	 * one fills the meter a notch, and it stays filled when they stop (as the chop meter does) until the stake comes out.
	 */
	public record Tug() implements CustomPacketPayload {
		public static final Type<Tug> TYPE = new Type<>(JaymosKingdoms.id("stake_tug"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tug> CODEC = StreamCodec.unit(new Tug());

		@Override
		public Type<Tug> type() {
			return TYPE;
		}
	}

	/** (0.7.641) When (game time) a vampire last tugged at a stake: everyone near sees them struggle ({@code StakeStruggle}). */
	public static final AttachmentType<Long> STRUGGLE = AttachmentRegistry.create(JaymosKingdoms.id("stake_struggle"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), (target, viewer) -> true));
	/** Tugs it takes to pull one stake out; at most one tug counts every this many ticks; the struggle shows this long after one. */
	public static final int TUGS = 14, TUG_EVERY = 2, STRUGGLE_TICKS = 12;

	/** The stakes in a vampire, oldest first (synced to everyone near, who see them: {@code StakeLayer}). */
	public static final AttachmentType<List<Stake>> STAKE = AttachmentRegistry.create(JaymosKingdoms.id("vampire_stakes"),
		builder -> builder.persistent(Stake.CODEC.listOf()).syncWith(Stake.STREAM_CODEC.apply(ByteBufCodecs.list()), (target, viewer) -> true));
	/** At most this many in one vampire. */
	static final int MAX = 8;
	/** Slowness refreshed every check, for this long. */
	static final int SLOW_TICKS = 30, CHECK_EVERY = 5;
	/** A mob tears one out after this long (ticks). */
	static final int MOB_PULL_TICKS = 400;

	/** Staked vampires loaded now, and how long each mob has worked at its newest (ticks). */
	private static final Map<UUID, LivingEntity> STAKED = new HashMap<>();
	private static final Map<UUID, Integer> WORK = new HashMap<>();

	private Stakes() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Tug.TYPE, Tug.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Tug.TYPE, (payload, context) -> tug(context.player()));
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
		stakes.add(new Stake(stake, vampire.getRandom().nextInt(), 0));
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

	/** (0.7.641) {@code player} tugs at their newest stake: a notch on the meter, and out at {@link #TUGS}. */
	private static void tug(ServerPlayer player) {
		List<Stake> stakes = player.getAttached(STAKE);
		if (stakes == null || stakes.isEmpty() || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long last = player.getAttached(STRUGGLE);
		if (last != null && now - last < TUG_EVERY && now >= last) {
			return;
		}
		player.setAttached(STRUGGLE, now);
		List<Stake> next = new ArrayList<>(stakes);
		Stake newest = next.getLast();
		int tugs = newest.tugs() + 1;
		if (tugs >= TUGS) {
			showTugs(player, TUGS);
			pull(player, null);
			return;
		}
		next.set(next.size() - 1, new Stake(newest.stack(), newest.seed(), tugs));
		player.setAttached(STAKE, List.copyOf(next));
		showTugs(player, tugs);
		if (tugs % 3 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 0.6F);
			level.sendParticles(new DustParticleOptions(0x5A0A0A, 0.8F), player.getX(), player.getY(0.6), player.getZ(), 3, 0.15, 0.1, 0.15, 0.0);
		}
	}

	/** (0.7.641) The pull meter on the action bar, drawn like the chop meter ({@code Trees.showChops}): it holds where it is. */
	private static void showTugs(ServerPlayer player, int tugs) {
		int marks = TUGS;
		Component bar = Component.literal("\u25A0".repeat(tugs)).withStyle(tugs >= marks ? ChatFormatting.RED : ChatFormatting.DARK_RED)
			.append(Component.literal("\u25A1".repeat(marks - tugs)).withStyle(ChatFormatting.DARK_GRAY));
		player.sendOverlayMessage(bar);
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
			if (e instanceof PathfinderMob) {
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
