package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How you live shows. Each dawn your lifestyle score moves by what the last day was: <b>where you slept</b> (your own
 * manor or house +{@link #MANOR}: a bed on land you are lord of, or where a deed makes you a householder; a room at an inn
 * +{@link #INN}: a bed in a standing tavern; any other bed +{@link #BED}; no bed but a stable's floor under you at dawn
 * {@link #STABLE_FLOOR}; the open ground {@link #ROUGH_NIGHT}), <b>what you spent</b> (+1 per {@link #SPEND_STEP} emeralds'
 * worth, up to {@link #SPEND_MOST}), <b>your clothes</b> (a tailor's work within {@link #CLOTHES_DAYS} days
 * {@link #CLOTHES}, else {@link #SHABBY}) and <b>baths</b> (soaked in still water for {@link #BATH_TICKS} ticks within
 * {@link #BATH_DAYS} days, +{@link #CLEAN}, else {@link #GRIMY}). The score eases back toward nothing each dawn.
 * <p>At {@link #WELL_FROM} you live well: a ruler's court opens to you more readily ({@link #courtShift}) and guards
 * hail you less. At {@link #ROUGH_FROM} you live rough: guards stop and search you more ({@link #guardWeight}), and a priest
 * pities you and gives alms once a day ({@link #alms}).
 */
public final class Lifestyle {
	public enum Tier {
		ROUGH, COMMON, WELL
	}

	static final int MANOR = 4, INN = 3, STABLE_FLOOR = -1;
	/** Where a night was spent, as saved: nowhere yet, a bed, an inn's, one's own. */
	static final int NO_BED = 0, A_BED = 1, AN_INN = 2, OWN = 3;
	static final int BED = 2, ROUGH_NIGHT = -3, SPEND_MOST = 3, CLOTHES = 1, SHABBY = -1, CLEAN = 1, GRIMY = -1;
	/** Emeralds' worth spent for one point. */
	static final int SPEND_STEP = 6;
	static final int CLOTHES_DAYS = 8, BATH_DAYS = 3, BATH_TICKS = 100;
	/** The score keeps this much of itself each dawn (percent), and is held within this much either way. */
	static final int KEEP_PERCENT = 90, LIMIT = 40;
	/** The score from which you live well, and from which you live rough. */
	public static final int WELL_FROM = 14, ROUGH_FROM = -14;
	/** A ruler's court: how far the reputation floor for an audience moves for the well-off and the rough. */
	static final int WELL_COURT = -20, ROUGH_COURT = 20;
	/** Alms: emeralds' worth of coin, and bread. */
	static final int ALMS_COIN = 1, ALMS_BREAD = 2, ALMS_HEARTS = 3;

	/** The score, whether you slept in a bed since dawn, what you spent since dawn (milli), the day your last bath and your clothes last till. */
	public record Living(int score, boolean slept, long spent, long bathedTo, long dressedTo, int place) {
		public static final Living NONE = new Living(0, false, 0L, 0L, 0L, NO_BED);

		Living(int score, boolean slept, long spent, long bathedTo, long dressedTo) {
			this(score, slept, spent, bathedTo, dressedTo, slept ? A_BED : NO_BED);
		}

		public static final Codec<Living> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("score").forGetter(Living::score),
			Codec.BOOL.fieldOf("slept").forGetter(Living::slept),
			Codec.LONG.fieldOf("spent").forGetter(Living::spent),
			Codec.LONG.fieldOf("bathed").forGetter(Living::bathedTo),
			Codec.LONG.fieldOf("dressed").forGetter(Living::dressedTo),
			Codec.INT.optionalFieldOf("place", NO_BED).forGetter(Living::place)).apply(i, Living::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Living> STREAM = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Living::score, ByteBufCodecs.BOOL, Living::slept, ByteBufCodecs.VAR_LONG, Living::spent,
			ByteBufCodecs.VAR_LONG, Living::bathedTo, ByteBufCodecs.VAR_LONG, Living::dressedTo, ByteBufCodecs.VAR_INT, Living::place, Living::new);
	}

	public static final AttachmentType<Living> LIVING = AttachmentRegistry.create(JaymosKingdoms.id("lifestyle"),
		builder -> builder.persistent(Living.CODEC).copyOnDeath().syncWith(Living.STREAM, AttachmentSyncPredicate.targetOnly()));
	/** The last day alms were given to a player. */
	private static final Map<UUID, Long> ALMSED = new HashMap<>();
	private static final Map<UUID, Integer> SOAKING = new HashMap<>();
	private static long lastDay = -1L;

	private Lifestyle() {
	}

	static void register() {
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer p && p.isSleepingLongEnough()) {
				int place = bedAt(p, pos);
				change(p, l -> new Living(l.score(), true, l.spent(), l.bathedTo(), l.dressedTo(), Math.max(place, l.place())));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Lifestyle::tick);
	}

	public static Living of(Player player) {
		return player.getAttachedOrElse(LIVING, Living.NONE);
	}

	private static void change(ServerPlayer p, java.util.function.UnaryOperator<Living> op) {
		p.setAttached(LIVING, op.apply(of(p)));
	}

	public static Tier tier(Player player) {
		int s = of(player).score();
		return s >= WELL_FROM ? Tier.WELL : s <= ROUGH_FROM ? Tier.ROUGH : Tier.COMMON;
	}

	/** Coin spent: counted toward the day's spending. */
	public static void spent(ServerPlayer player, long milli) {
		change(player, l -> new Living(l.score(), l.slept(), l.spent() + milli, l.bathedTo(), l.dressedTo(), l.place()));
	}

	/** A tailor made {@code player} an outfit: dressed for {@link #CLOTHES_DAYS} days. */
	static void dressed(ServerPlayer player) {
		long day = player.level().getGameTime() / 24000L;
		change(player, l -> new Living(l.score(), l.slept(), l.spent(), l.bathedTo(), day + CLOTHES_DAYS, l.place()));
	}

	/** How far a ruler's reputation floor for a hearing moves for {@code player}'s manner of living. */
	static int courtShift(Player player) {
		return switch (tier(player)) {
			case WELL -> WELL_COURT;
			case ROUGH -> ROUGH_COURT;
			default -> 0;
		};
	}

	/** Extra weight (or less) on a guard stopping {@code player}: the rough are watched, the well-off waved on. */
	static int guardWeight(Player player) {
		return switch (tier(player)) {
			case ROUGH -> 3;
			case WELL -> -1;
			default -> 0;
		};
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				soak(p, now / 24000L);
			}
		}
		long day = now / 24000L;
		if (lastDay < 0L) {
			lastDay = day;
			return;
		}
		if (day == lastDay) {
			return;
		}
		lastDay = day;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			dawn(p, day);
		}
	}

	/** Still in water, head above it: a bath. */
	private static void soak(ServerPlayer p, long day) {
		boolean still = p.isInWater() && !p.isUnderWater() && p.getDeltaMovement().horizontalDistanceSqr() < 0.002 && p.isAlive();
		if (!still) {
			SOAKING.remove(p.getUUID());
			return;
		}
		int ticks = SOAKING.merge(p.getUUID(), 20, Integer::sum);
		if (ticks >= BATH_TICKS) {
			SOAKING.remove(p.getUUID());
			if (of(p).bathedTo() < day + BATH_DAYS) {
				change(p, l -> new Living(l.score(), l.slept(), l.spent(), day + BATH_DAYS, l.dressedTo(), l.place()));
				p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5F, 1.4F);
				p.sendOverlayMessage(Component.translatable("lifestyle.jaymoskingdoms.bathed"));
			}
		}
	}

	private static void dawn(ServerPlayer p, long day) {
		Living l = of(p);
		int points = !l.slept() ? onStableFloor(p) ? STABLE_FLOOR : ROUGH_NIGHT : switch (l.place()) {
			case OWN -> MANOR;
			case AN_INN -> INN;
			default -> BED;
		};
		points += (int)Math.min(SPEND_MOST, l.spent() / (Money.UNIT * (long)SPEND_STEP));
		points += l.dressedTo() > day ? CLOTHES : SHABBY;
		points += l.bathedTo() > day ? CLEAN : GRIMY;
		int score = Math.max(-LIMIT, Math.min(LIMIT, l.score() * KEEP_PERCENT / 100 + points));
		Tier before = tier(p);
		p.setAttached(LIVING, new Living(score, false, 0L, l.bathedTo(), l.dressedTo()));
		Tier after = tier(p);
		if (after != before) {
			p.sendOverlayMessage(Component.translatable("lifestyle.jaymoskingdoms.now." + after.name().toLowerCase(java.util.Locale.ROOT)));
		}
	}

	/** The realm whose land {@code pos} is (null: nobody's, or not the world realms are in). */
	private static @Nullable Faction landAt(ServerPlayer p, BlockPos pos) {
		MinecraftServer server = p.level().getServer();
		if (p.level() != Factions.level(server)) {
			return null;
		}
		Faction owner = Banners.owner(server, ChunkPos.containing(pos));
		return owner == null || owner.fallen() ? null : owner;
	}

	/** What kind of bed the one at {@code pos} is to {@code p}: their own, an inn's, or only a bed. */
	private static int bedAt(ServerPlayer p, BlockPos pos) {
		Faction land = landAt(p, pos);
		if (land != null && (Wars.isLord(land, p.getUUID()) || ForgedPapers.householder(p, land))) {
			return OWN;
		}
		return p.level() instanceof ServerLevel level && Remedies.tavernAt(level, Vec3.atCenterOf(pos)) ? AN_INN : A_BED;
	}

	/** Whether {@code p} stands in a standing stable (a night on its straw, for want of a bed). */
	private static boolean onStableFloor(ServerPlayer p) {
		Faction land = landAt(p, p.blockPosition());
		if (land == null) {
			return false;
		}
		for (Works.Built b : Stables.stables(p.level().getServer(), land)) {
			if (new AABB(Vec3.atLowerCornerOf(b.min()), Vec3.atLowerCornerOf(b.max()).add(1, 1, 1)).inflate(1.0).contains(p.position())) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code priest} would give {@code player} alms today: they live rough and have not been given any. */
	static boolean alms(Player player, LivingEntity priest) {
		if (!Accusations.priest(priest) || tier(player) != Tier.ROUGH) {
			return false;
		}
		Long last = ALMSED.get(player.getUUID());
		return last == null || last < priest.level().getGameTime() / 24000L;
	}

	/** The priest gives alms: bread and a coin, and a word. */
	static void giveAlms(ServerPlayer player, LivingEntity priest) {
		if (!alms(player, priest) || !(priest.level() instanceof ServerLevel level)) {
			return;
		}
		ALMSED.put(player.getUUID(), level.getGameTime() / 24000L);
		ItemStack bread = new ItemStack(Items.BREAD, ALMS_BREAD);
		if (!player.getInventory().add(bread)) {
			player.spawnAtLocation(level, bread);
		}
		Money.pay(player, Money.worth(ALMS_COIN));
		McaCompat.hearts(priest, player, ALMS_HEARTS);
		Speech.tell(level, priest, player, Component.translatable("lifestyle.jaymoskingdoms.alms." + level.getRandom().nextInt(3)), Speech.GOLD);
		level.playSound(null, priest.getX(), priest.getY(), priest.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}
}
