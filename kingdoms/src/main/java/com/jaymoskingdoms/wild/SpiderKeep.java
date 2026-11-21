package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "when you get this respawn the spiders shouldnt keep attacking you, they also shouldnt attack you if you are
 * webbed up, as they are saving you for the queen". Spiders leave their catch be, as captors leave a captive keeping their
 * place ({@code CaptiveConduct}):
 * <ul>
 * <li><b>Webbed up:</b> anyone wrapped in web (Jaymo's Powers' Webbed, any layer) isn't a spider's to bite.</li>
 * <li><b>Kept:</b> one who woke in a spiders' lair ({@link SpiderLairs#take}) is left be there, webbed or not, until they
 * get {@link #LAIR_REACH} out of it (they've got away) or {@link #KEPT_TICKS} pass.</li>
 * <li><b>Roused:</b> striking a spider (a blow or a shot; not a squash underfoot) sets them on you for {@link #ROUSED_TICKS},
 * webbed or kept, and a lair's keeping ends there.</li>
 * </ul>
 * A spider can't be set on one it spares ({@code SpiderKeepMixin}), and any already on them lets go. The brood queen isn't a
 * spider here: they keep you for her.
 */
public final class SpiderKeep {
	// ---- tuning ----
	/** A lair keeps the one it took this long at most (ticks): a day. */
	static final long KEPT_TICKS = 24000L;
	/** Kept only this near (blocks) where they woke; past it they've got out, and it's over. */
	static final double LAIR_REACH = 32.0;
	/** Striking a spider sets them on you this long (ticks). */
	static final long ROUSED_TICKS = 1200L;
	/** Every so many ticks, spiders this near (blocks) one they spare let go of them. */
	static final int CALM_EVERY = 10;
	static final double CALM_RANGE = 24.0;

	/** One kept by a lair: where they woke, and the game time it ends. */
	record Kept(GlobalPos lair, long until) {
		static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(GlobalPos.CODEC.fieldOf("lair").forGetter(Kept::lair),
			Codec.LONG.fieldOf("until").forGetter(Kept::until)).apply(i, Kept::new));
	}

	/** On one a lair took: where, and till when. Saved. */
	static final AttachmentType<Kept> KEPT = AttachmentRegistry.create(JaymosKingdoms.id("spider_kept"), b -> b.persistent(Kept.CODEC));
	/** On one who struck a spider: the game time the spiders' anger ends. Saved. */
	static final AttachmentType<Long> ROUSED = AttachmentRegistry.create(JaymosKingdoms.id("spider_roused"), b -> b.persistent(Codec.LONG));

	private SpiderKeep() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (entity instanceof Spider && player instanceof ServerPlayer sp) {
				rouse(sp);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Spider && source.getDirectEntity() instanceof Projectile && source.getEntity() instanceof ServerPlayer sp) {
				rouse(sp);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SpiderKeep::tick);
	}

	/** {@code player} wakes in a spiders' lair at {@code lair}: kept there, whatever they did before. */
	static void keep(ServerPlayer player, GlobalPos lair) {
		player.removeAttached(ROUSED);
		player.setAttached(KEPT, new Kept(lair, player.level().getGameTime() + KEPT_TICKS));
	}

	/** Whether {@code mob}, a spider, leaves {@code target} be: webbed up, or kept by a lair, and not roused. */
	public static boolean spares(Mob mob, @Nullable LivingEntity target) {
		return mob instanceof Spider && target instanceof ServerPlayer player && !player.isSpectator() && !roused(player)
			&& (PowersCompat.webbed(player) || kept(player));
	}

	private static boolean roused(ServerPlayer player) {
		Long until = player.getAttached(ROUSED);
		return until != null && player.level().getGameTime() < until;
	}

	private static boolean kept(ServerPlayer player) {
		Kept kept = player.getAttached(KEPT);
		return kept != null && player.level().getGameTime() < kept.until() && player.level().dimension() == kept.lair().dimension()
			&& kept.lair().pos().closerToCenterThan(player.position(), LAIR_REACH);
	}

	private static void rouse(ServerPlayer player) {
		player.setAttached(ROUSED, player.level().getGameTime() + ROUSED_TICKS);
		player.removeAttached(KEPT);
	}

	/** Each tick: a spent anger or a keeping they've got out of is cleared; spiders on one they spare let go. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			Long until = player.getAttached(ROUSED);
			if (until != null && now >= until) {
				player.removeAttached(ROUSED);
			}
			if (player.hasAttached(KEPT) && !kept(player)) {
				player.removeAttached(KEPT);
			}
			if (now % CALM_EVERY != 0L || !player.isAlive()) {
				continue;
			}
			for (Spider spider : player.level().getEntitiesOfClass(Spider.class, player.getBoundingBox().inflate(CALM_RANGE),
				s -> s.isAlive() && (s.getTarget() == player || s.getLastHurtByMob() == player))) {
				if (!spares(spider, player)) {
					break;
				}
				if (spider.getTarget() == player) {
					spider.setTarget(null);
					spider.getNavigation().stop();
				}
				if (spider.getLastHurtByMob() == player) {
					spider.setLastHurtByMob(null);
				}
			}
		}
	}
}
