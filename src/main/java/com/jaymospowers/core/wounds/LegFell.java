package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.power.web.KitchenCompat;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Felled by a blow to the legs: crippled, not downed. Andy (2026-11-24): "downed and crippled should be two different
 * things, a suffienct blow, to the legs forces you into a ragdoll then prone, but you can roll to recover. downed is what
 * happens after losing your hearts".
 * <ul>
 * <li>A blow ({@link Downed#attack}: something struck it, not a fall, fire or bleeding) that lands on a leg or foot and
 * takes {@link #BLOW_SHARE} of the most health, or breaks that leg, takes the legs out from under a body on its feet: a
 * player's one time in five ({@link #PLAYER_CHANCE}), an NPC's half the time (its sweep, {@link Tripped#NPC_SWEEP_CHANCE}).</li>
 * <li>A player goes limp into Kitchen's ragdoll, flung a little away from the blow, and once the body comes to rest lies
 * prone ({@link Crawl#CRAWLING}): no standing up by Jump or the crawl key. A roll (a double tap of Left or Right, or the
 * Dodge key) gets them back on their feet as it ends. Two broken legs still keep them crawling ({@link Breaks#crawlOnly}).
 * Without Kitchen they go straight to prone.</li>
 * <li>A mob built like a person goes down flat on its back ({@link Tripped#sweep}) and gets up by itself.</li>
 * </ul>
 * Their hearts aren't touched: the downed state is still what comes of losing them ({@link Downed}).
 */
public final class LegFell {
	/** A blow to a leg or foot taking at least this share of the most health fells (a player's 7 of 20). */
	public static final float BLOW_SHARE = 0.35F;
	/**
	 * Such a blow fells a player only this often. Andy (2026-11-28): "Players are knocked on the ground far too easily.
	 * lower that chance by 80% and require targeted hits to the legs, knockback or explosions". NPCs roll through their sweep ({@link Tripped#NPC_SWEEP_CHANCE}): Andy then: "players 1 in 5, npcs 1 in 2".
	 */
	public static final float PLAYER_CHANCE = 0.2F;
	/** The fling away from the blow (blocks a tick), and its lift. */
	public static final double FLING = 0.18, LIFT = 0.12;
	/** Ticks after the fall before the body may be found at rest (Kitchen's client takes a moment to go limp). */
	public static final int LIMP_GRACE_TICKS = 6;
	/** A mob's time flat on its back (ticks). */
	public static final int MOB_TICKS = 50;

	/** A fall: when it happened, and when the roll that gets them up ends (0 until they roll). */
	public record Fell(long since, long upAt) {
		public static final StreamCodec<ByteBuf, Fell> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Fell::since,
			ByteBufCodecs.VAR_LONG, Fell::upAt,
			Fell::new);
	}

	/** A felled player, until they've rolled up. Synced to the player (their keys), not saved: a reload stands them up. */
	public static final AttachmentType<Fell> FELLED = AttachmentRegistry.create(JaymosPowers.id("leg_felled"),
		builder -> builder.syncWith(Fell.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final Set<ServerPlayer> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

	private LegFell() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LegFell::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRACKED.clear());
	}

	/** Whether {@code player} is down from a leg blow and hasn't rolled back up yet (both sides). */
	public static boolean felled(Player player) {
		return player.hasAttached(FELLED);
	}

	/**
	 * A hit on {@code victim} landed on {@code landed}, taking {@code loss} of its health ({@code broke}: it broke that
	 * leg). Called from {@link Wounds}' after-damage, for a hit that wasn't blocked.
	 */
	static void struck(ServerLevel level, LivingEntity victim, DamageSource source, @Nullable BodyPart landed, float loss, float max, boolean broke) {
		if (!leg(landed) || !Downed.attack(source) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
			|| !victim.isAlive() || Downed.isDowned(victim) || Carry.isCarried(victim) || victim.isPassenger()
			|| !broke && loss < BLOW_SHARE * max) {
			return;
		}
		if (victim instanceof ServerPlayer player) {
			if (player.getRandom().nextFloat() < PLAYER_CHANCE) {
				fell(level, player, source);
			}
		} else if (Tripped.sweepable(victim)) {
			Tripped.sweep(victim, MOB_TICKS);
		}
	}

	private static boolean leg(@Nullable BodyPart part) {
		return part == BodyPart.LEFT_LEG || part == BodyPart.RIGHT_LEG || part == BodyPart.LEFT_FOOT || part == BodyPart.RIGHT_FOOT;
	}

	private static void fell(ServerLevel level, ServerPlayer player, DamageSource source) {
		if (felled(player) || player.isCreative() || player.isSpectator() || !Crawl.canCrawl(player)) {
			return;
		}
		player.setAttached(FELLED, new Fell(level.getGameTime(), 0L));
		TRACKED.add(player);
		player.setSprinting(false);
		Vec3 from = source.getSourcePosition();
		double dx = from == null ? 0.0 : player.getX() - from.x, dz = from == null ? 0.0 : player.getZ() - from.z;
		double flat = Math.hypot(dx, dz);
		if (flat < 1.0E-4) {
			KitchenCompat.knockLimp(player, 0.0, LIFT, 0.0);
		} else {
			KitchenCompat.knockLimp(player, dx / flat * FLING, LIFT, dz / flat * FLING);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.leg_fell.roll").withStyle(ChatFormatting.GRAY));
	}

	/** {@code player} began a roll (Crawl) that ends at {@code end}: if felled, they come up on their feet as it does. */
	public static void rolled(ServerPlayer player, long end) {
		Fell fell = player.getAttached(FELLED);
		if (fell != null && fell.upAt() == 0L) {
			player.setAttached(FELLED, new Fell(fell.since(), end));
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<ServerPlayer> it = TRACKED.iterator(); it.hasNext(); ) {
			ServerPlayer player = it.next();
			Fell fell = player.getAttached(FELLED);
			if (fell == null || player.isRemoved() || !player.isAlive() || Downed.isDowned(player) || Carry.isCarried(player)) {
				// gone, or something else has them now (the downed state holds its own crawl)
				it.remove();
				player.removeAttached(FELLED);
				continue;
			}
			if (!Crawl.canCrawl(player)) {
				// in water, riding, flying: nothing to lie on
				it.remove();
				player.removeAttached(FELLED);
				player.removeAttached(Crawl.CRAWLING);
				continue;
			}
			long now = player.level().getGameTime();
			if (fell.upAt() > 0L) {
				if (now >= fell.upAt()) {
					// the roll carries them up onto their feet (unless both legs are broken)
					it.remove();
					player.removeAttached(FELLED);
					if (!Breaks.crawlOnly(player)) {
						player.removeAttached(Crawl.SUPINE);
						player.removeAttached(Crawl.CRAWLING);
					}
				}
				continue;
			}
			if (!player.hasAttached(Crawl.CRAWLING) && now - fell.since() >= LIMP_GRACE_TICKS && !KitchenCompat.isLimp(player)) {
				// the ragdoll has come to rest: prone where it lies
				player.setAttached(Crawl.CRAWLING, true);
			}
		}
	}
}
