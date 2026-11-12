package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import com.jaymoskingdoms.money.Money;

/**
 * A night on the town: at a tavern, or a speakeasy's fence or dicer, <b>Make a Night of It</b> spends coin ({@link #NIGHTS}:
 * a round, a night, a grand night) and the night happens in the world. Each night draws outcomes, and every one is real:
 * <ul>
 * <li>a <b>new friend</b>: a villager nearby who now likes you ({@link Recollections}, MCA hearts);</li>
 * <li>a <b>treasure rumour</b>: a real structure, told with distance and heading ({@link Rumours});</li>
 * <li>a <b>debt to a crook</b>: a real crook is named, and collects in two days from your purse, with a beating if you
 * cannot pay (see {@link #collect});</li>
 * <li>a <b>rival</b>: a villager who now holds a grudge;</li>
 * <li>a <b>black eye</b>: your Presence is lower for {@link #EYE_DAYS} days ({@link Presence#skill});</li>
 * <li>a <b>jail cell</b>: a night in the realm's real cells ({@link Busts#lockUp});</li>
 * <li>a <b>betrothal</b>: a villager you pledged to, engaged to you in MCA's terms, or an engagement of our own without MCA.</li>
 * </ul>
 * You black out, and on waking (a few seconds on, or on leaving a bed) a screen recalls the night in your own first-person
 * words ({@link Recall}).
 */
public final class Carousing {
	/** The three nights: cost (in emeralds' worth) and how many things happen. */
	static final int[] COST = {2, 6, 18}, EVENTS = {1, 2, 3};
	/** A speakeasy's crowd is rougher: its debts and cells are likelier by this much. */
	static final float SPEAKEASY_ROUGH = 1.6F;
	/** How long you are out (ticks) before the recall screen. */
	static final int BLACKOUT_TICKS = 100;
	/** A black eye lasts this many days and takes this many Presence points off. */
	static final int EYE_DAYS = 2, EYE_PENALTY = Stats.STEP;
	/** A night in the cells (ticks). */
	static final long CELL_TICKS = 2400L;
	/** A debt, in emeralds' worth (least, spread), and when it is collected (days), and the interest on a miss. */
	static final int DEBT_MIN = 5, DEBT_SPREAD = 10, DEBT_DAYS = 2;
	static final float DEBT_INTEREST = 1.5F;
	/** The beating a debtor takes who cannot pay a second time (half hearts). */
	static final float BEATING = 6.0F;
	/** How far a night's folk are found (blocks). */
	static final double FOLK_RANGE = 40.0, CROOK_RANGE = 72.0;
	/** What the night does to a villager's hearts. */
	static final int FRIEND_HEARTS = 20, RIVAL_HEARTS = -30, BETROTHED_HEARTS = 30;
	/** How often debts are checked (ticks). */
	static final int DEBT_CHECK = 200;

	enum Outcome {
		FRIEND(4.0F), RUMOUR(3.0F), EYE(1.2F), DEBT(1.5F), RIVAL(1.5F), JAIL(0.8F), BETROTHED(0.35F);

		final float weight;

		Outcome(float weight) {
			this.weight = weight;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** A debt: who is owed, how much (milli), the day it falls due and how many times it was missed. */
	public record Debt(String creditor, UUID crook, long owed, long due, int missed) {
		public static final Codec<Debt> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("creditor").forGetter(Debt::creditor),
			net.minecraft.core.UUIDUtil.CODEC.fieldOf("crook").forGetter(Debt::crook),
			Codec.LONG.fieldOf("owed").forGetter(Debt::owed),
			Codec.LONG.fieldOf("due").forGetter(Debt::due),
			Codec.INT.fieldOf("missed").forGetter(Debt::missed)).apply(i, Debt::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Debt> STREAM = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Debt::creditor, net.minecraft.core.UUIDUtil.STREAM_CODEC, Debt::crook, ByteBufCodecs.VAR_LONG, Debt::owed,
			ByteBufCodecs.VAR_LONG, Debt::due, ByteBufCodecs.VAR_INT, Debt::missed, Debt::new);
	}

	/** The debt you carry, if any. Saved, synced to you. */
	public static final AttachmentType<Debt> DEBT = AttachmentRegistry.create(JaymosKingdoms.id("night_debt"),
		builder -> builder.persistent(Debt.CODEC).copyOnDeath().syncWith(Debt.STREAM, AttachmentSyncPredicate.targetOnly()));
	/** The day a black eye heals. */
	public static final AttachmentType<Long> EYE = AttachmentRegistry.create(JaymosKingdoms.id("night_eye"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.targetOnly()));
	/** Who you are betrothed to (a name), when the night made it so. */
	public static final AttachmentType<String> BETROTHED = AttachmentRegistry.create(JaymosKingdoms.id("night_betrothed"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8.cast(), AttachmentSyncPredicate.targetOnly()));
	/** Names of those the nights made your rivals. */
	public static final AttachmentType<List<String>> RIVALS = AttachmentRegistry.create(JaymosKingdoms.id("night_rivals"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).cast(), AttachmentSyncPredicate.targetOnly()));

	/** Server → player: the night, recalled in the first person. */
	public record Recall(List<Component> lines) implements CustomPacketPayload {
		public static final Type<Recall> TYPE = new Type<>(JaymosKingdoms.id("night_recall"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Recall> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Recall::lines, Recall::new);

		@Override
		public Type<Recall> type() {
			return TYPE;
		}
	}

	private record Waiting(long at, List<Component> lines) {
	}

	private static final Map<UUID, Waiting> WAITING = new HashMap<>();

	private Carousing() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Recall.TYPE, Recall.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Carousing::tick);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Waiting w = WAITING.remove(player.getUUID());
				if (w != null) {
					show(player, w.lines());
				}
			}
		});
	}

	/** 0: no place for a night out; 1: a tavern; 2: a speakeasy. */
	static int venue(ServerLevel level, LivingEntity v) {
		if (!(v instanceof Villager villager) || villager.isBaby() || !villager.isAlive()) {
			return 0;
		}
		if (villager.entityTags().contains(Underworld.FENCE_TAG) || villager.entityTags().contains(Underworld.DICER_TAG)) {
			return 2;
		}
		return Remedies.inTavern(level, villager) ? 1 : 0;
	}

	static boolean offers(ServerPlayer player, LivingEntity v) {
		return player.level() instanceof ServerLevel level && venue(level, v) > 0 && !WAITING.containsKey(player.getUUID()) && !Busts.jailed(player);
	}

	/** The menu of nights. */
	static void ask(ServerPlayer player, LivingEntity host) {
		if (!offers(player, host)) {
			return;
		}
		List<Menus.Choice> choices = new ArrayList<>();
		for (int n = 0; n < COST.length; n++) {
			int night = n;
			boolean can = Money.canPay(player, Money.worth(COST[n]));
			choices.add(Menus.Choice.of(Component.translatable("talk.jaymoskingdoms.night." + n, COST[n]), can, p -> spend(p, host, night)));
		}
		Menus.ask(player, host, Component.translatable("talk.jaymoskingdoms.night.ask"), choices);
	}

	private static void spend(ServerPlayer player, LivingEntity host, int night) {
		if (!offers(player, host) || !(player.level() instanceof ServerLevel level) || !Money.charge(player, Money.worth(COST[night]))) {
			return;
		}
		int venue = venue(level, host);
		if (venue == 2) {
			// the speakeasy lets in those who drink with it: they read the Underworld's signs
			ChalkMarks.initiate(player);
		}
		level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (!KitchenCompat.getDrunk(player, 3 + night * 3)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 400, 0));
		}
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLACKOUT_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BLACKOUT_TICKS + 100, 2));
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("recall.jaymoskingdoms.night.open." + venue + "." + level.getRandom().nextInt(2), host.getDisplayName()));
		List<Outcome> drawn = new ArrayList<>();
		for (int i = 0; i < EVENTS[night] && drawn.size() < Outcome.values().length; i++) {
			Outcome o = draw(level, player, host, venue, night, drawn);
			if (o == null) {
				break;
			}
			drawn.add(o);
			Component line = apply(level, player, host, o, venue);
			if (line != null) {
				lines.add(line);
			}
		}
		if (drawn.isEmpty() || lines.size() == 1) {
			lines.add(Component.translatable("recall.jaymoskingdoms.night.nothing"));
		}
		lines.add(Component.translatable("recall.jaymoskingdoms.night.close"));
		Sparks.act(player, Sparks.Act.CAROUSED, true);
		WAITING.put(player.getUUID(), new Waiting(level.getGameTime() + BLACKOUT_TICKS, lines));
	}

	private static @Nullable Outcome draw(ServerLevel level, ServerPlayer player, LivingEntity host, int venue, int night, List<Outcome> drawn) {
		float presence = 1.0F + Presence.skill(player) * 0.01F;
		float total = 0.0F;
		Map<Outcome, Float> weights = new java.util.EnumMap<>(Outcome.class);
		for (Outcome o : Outcome.values()) {
			if (drawn.contains(o) || !possible(level, player, host, o, night)) {
				continue;
			}
			float w = o.weight;
			switch (o) {
				case FRIEND -> w *= presence;
				case EYE, RIVAL -> w /= presence;
				case DEBT, JAIL -> w *= venue == 2 ? SPEAKEASY_ROUGH : 1.0F;
				case RUMOUR -> w *= venue == 2 ? 1.4F : 1.0F;
				default -> {
				}
			}
			weights.put(o, w);
			total += w;
		}
		if (total <= 0.0F) {
			return null;
		}
		float roll = level.getRandom().nextFloat() * total;
		for (Map.Entry<Outcome, Float> e : weights.entrySet()) {
			roll -= e.getValue();
			if (roll <= 0.0F) {
				return e.getKey();
			}
		}
		return weights.keySet().iterator().next();
	}

	private static boolean possible(ServerLevel level, ServerPlayer player, LivingEntity host, Outcome o, int night) {
		return switch (o) {
			case FRIEND -> pick(level, player, host, true) != null;
			case RUMOUR -> Rumours.place(level, player.blockPosition()) != null;
			case EYE -> player.getAttachedOrElse(EYE, 0L) < level.getGameTime() / 24000L;
			case DEBT -> night >= 1 && !player.hasAttached(DEBT) && crook(level, host) != null;
			case RIVAL -> night >= 1 && pick(level, player, host, false) != null;
			case JAIL -> night >= 1 && !Busts.jailed(player) && realm(level, host) != null;
			case BETROTHED -> night >= 2 && player.getAttachedOrElse(BETROTHED, "").isEmpty() && pick(level, player, host, true) != null;
		};
	}

	/** A villager nearby, grown, who is not the host, a crook or a ruler. {@code kind}: one who is not yet a friend. */
	private static @Nullable Villager pick(ServerLevel level, ServerPlayer player, LivingEntity host, boolean kind) {
		List<Villager> near = new ArrayList<>(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(FOLK_RANGE),
			v -> v.isAlive() && !v.isBaby() && v != host && !Rulers.is(v) && !Crooks.isCrook(v) && !Downed.isGuard(v)
				&& (!kind || McaCompat.heartsOf(v, player) < 50 && !Recollections.refuses(v, player))));
		if (near.isEmpty()) {
			return null;
		}
		Collections.shuffle(near, new java.util.Random(level.getRandom().nextLong()));
		return near.getFirst();
	}

	private static @Nullable Villager crook(ServerLevel level, LivingEntity host) {
		AABB box = host.getBoundingBox().inflate(CROOK_RANGE);
		List<Villager> crooks = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && (Crooks.isCrook(v) || v.entityTags().contains(Underworld.FENCE_TAG)));
		return crooks.isEmpty() ? null : crooks.getFirst();
	}

	private static @Nullable Faction realm(ServerLevel level, LivingEntity host) {
		Faction f = Theft.lawAt(level, host.blockPosition());
		return f != null && f.type().isRealm() ? f : null;
	}

	private static @Nullable Component apply(ServerLevel level, ServerPlayer player, LivingEntity host, Outcome o, int venue) {
		int v = level.getRandom().nextInt(2);
		String key = "recall.jaymoskingdoms.night." + o.key() + "." + v;
		switch (o) {
			case FRIEND -> {
				Villager friend = pick(level, player, host, true);
				if (friend == null) {
					return null;
				}
				McaCompat.hearts(friend, player, FRIEND_HEARTS);
				Recollections.remember(friend, player, Recollections.Kind.PLAYED, Component.translatable("recall.jaymoskingdoms.night.drank"));
				Recollections.remember(friend, player, Recollections.Kind.KIND, Component.translatable("recall.jaymoskingdoms.night.drank"));
				return Component.translatable(key, friend.getDisplayName());
			}
			case RUMOUR -> {
				BlockPos at = Rumours.place(level, player.blockPosition());
				if (at == null) {
					return null;
				}
				return Component.translatable(key, Rumours.words(player.position(), at));
			}
			case EYE -> {
				player.setAttached(EYE, level.getGameTime() / 24000L + EYE_DAYS);
				return Component.translatable(key);
			}
			case DEBT -> {
				Villager crook = crook(level, host);
				if (crook == null) {
					return null;
				}
				long owed = Money.worth(DEBT_MIN + level.getRandom().nextInt(DEBT_SPREAD + 1));
				player.setAttached(DEBT, new Debt(crook.getName().getString(), crook.getUUID(), owed, level.getGameTime() / 24000L + DEBT_DAYS, 0));
				return Component.translatable(key, crook.getDisplayName(), Money.text(player, owed), DEBT_DAYS);
			}
			case RIVAL -> {
				Villager rival = pick(level, player, host, false);
				if (rival == null) {
					return null;
				}
				McaCompat.hearts(rival, player, RIVAL_HEARTS);
				Recollections.remember(rival, player, Recollections.Kind.RUDE, Component.translatable("recall.jaymoskingdoms.night.brawl"));
				Recollections.remember(rival, player, Recollections.Kind.STRUCK, Component.translatable("recall.jaymoskingdoms.night.brawl"));
				List<String> rivals = new ArrayList<>(player.getAttachedOrElse(RIVALS, List.of()));
				rivals.add(rival.getName().getString());
				player.setAttached(RIVALS, List.copyOf(rivals));
				return Component.translatable(key, rival.getDisplayName());
			}
			case JAIL -> {
				Faction f = realm(level, host);
				if (f == null) {
					return null;
				}
				Busts.lockUp(level, player, f, level.getGameTime() + CELL_TICKS, Post.nameOf(player));
				return Component.translatable(key, f.name());
			}
			case BETROTHED -> {
				Villager partner = pick(level, player, host, true);
				if (partner == null) {
					return null;
				}
				McaCompat.hearts(partner, player, BETROTHED_HEARTS);
				Recollections.remember(partner, player, Recollections.Kind.KISSED, Component.translatable("recall.jaymoskingdoms.night.pledge"));
				boolean mca = McaCompat.engage(partner, player);
				player.setAttached(BETROTHED, partner.getName().getString());
				return Component.translatable(key + (mca ? "" : ".plain"), partner.getDisplayName());
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!WAITING.isEmpty()) {
			WAITING.entrySet().removeIf(e -> {
				if (now < e.getValue().at()) {
					return false;
				}
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					show(p, e.getValue().lines());
				}
				return p != null;
			});
		}
		if (now % DEBT_CHECK == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Debt d = p.getAttached(DEBT);
				if (d != null && p.level().getGameTime() / 24000L >= d.due()) {
					collect(p, d);
				}
			}
		}
	}

	private static void show(ServerPlayer player, List<Component> lines) {
		if (ServerPlayNetworking.canSend(player, Recall.TYPE)) {
			ServerPlayNetworking.send(player, new Recall(lines));
		}
	}

	/** The crook's debt falls due: taken from the purse; a shortfall carries interest once, and the second miss is a beating. */
	static void collect(ServerPlayer player, Debt d) {
		ServerLevel level = player.level();
		long paid = Money.chargeUpTo(player, d.owed());
		LivingEntity crook = level.getEntity(d.crook()) instanceof LivingEntity c && c.isAlive() && c.distanceTo(player) < 48.0F ? c : null;
		if (paid >= d.owed()) {
			player.removeAttached(DEBT);
			say(level, crook, player, "debt.paid", d.creditor());
			return;
		}
		long left = d.owed() - paid;
		if (d.missed() >= 1) {
			player.removeAttached(DEBT);
			player.hurtServer(level, level.damageSources().generic(), BEATING);
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
			List<String> rivals = new ArrayList<>(player.getAttachedOrElse(RIVALS, List.of()));
			rivals.add(d.creditor());
			player.setAttached(RIVALS, List.copyOf(rivals));
			say(level, crook, player, "debt.beaten", d.creditor());
			return;
		}
		player.setAttached(DEBT, new Debt(d.creditor(), d.crook(), Math.round(left * DEBT_INTEREST), level.getGameTime() / 24000L + 1, d.missed() + 1));
		say(level, crook, player, "debt.short", d.creditor());
	}

	private static void say(ServerLevel level, @Nullable LivingEntity crook, ServerPlayer player, String key, String name) {
		Component words = Component.translatable("night.jaymoskingdoms." + key + "." + level.getRandom().nextInt(2), name);
		if (crook != null) {
			Speech.tell(level, crook, player, words, Speech.RED);
		} else {
			player.sendOverlayMessage(words);
		}
	}

	/** The Presence a black eye takes off {@code player} now. */
	public static int eyePenalty(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(EYE, 0L) > player.level().getGameTime() / 24000L ? EYE_PENALTY : 0;
	}
}
