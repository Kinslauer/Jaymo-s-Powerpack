package com.jaymoskingdoms;

import com.jaymoskingdoms.equestrian.Bonding;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "reincarnating didnt seem to forget my bonded horses". When a life is over for good ({@link LifeStory#reincarnateAnew})
 * the animals of the old life forget it, as the people do ({@link Recollections#bornAnew}): a tamed wolf, cat or parrot (any
 * tamed animal: Jaymo's Powers' dinosaurs, the aerbunny, our beavers and sporelings too) goes wild again; a horse or any other
 * mount of that kind (Icy's Better Horses' and ours, the war elephant, the great goat, a wind dragon) is no one's and untamed,
 * and whatever trust it had in the old life goes ({@link Bonding#forgetLife}); the parrots on their shoulders get down, and
 * anything on their lead is let go. The new life meets every one of them as a stranger.
 * <p>
 * The animals loaded now are seen to at once. Those away are seen to when they next load: when each life ended is kept on the
 * Overworld ({@link #ENDED}), and each tamable animal keeps when it was last seen to ({@link #SEEN}), so it forgets only the
 * lives that ended since.
 */
public final class ForgottenLives {
	/** On the Overworld: player (account) → the game time their last life ended. Saved. */
	static final AttachmentType<Map<UUID, Long>> ENDED = AttachmentRegistry.create(JaymosKingdoms.id("lives_ended"),
		builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)));

	/** On a tamable animal or a horse: the game time it was last seen to against {@link #ENDED}. Saved. */
	static final AttachmentType<Long> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("lives_seen"),
		builder -> builder.persistent(Codec.LONG));

	private ForgottenLives() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(ForgottenLives::loaded);
	}

	/** An animal that can be someone's: a tamed animal of any kind, or a horse-like mount. */
	private static boolean bonds(Entity e) {
		return e instanceof TamableAnimal || e instanceof AbstractHorse;
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (!bonds(entity)) {
			return;
		}
		ServerLevel over = level.getServer().overworld();
		if (over == null) {
			return;
		}
		Map<UUID, Long> ended = over.getAttachedOrElse(ENDED, Map.of());
		if (ended.isEmpty()) {
			return;
		}
		long seen = entity.getAttachedOrElse(SEEN, Long.MIN_VALUE);
		for (Map.Entry<UUID, Long> e : ended.entrySet()) {
			if (e.getValue() > seen) {
				forget(entity, e.getKey(), null);
			}
		}
		entity.setAttached(SEEN, over.getGameTime());
	}

	/**
	 * (From {@link LifeStory#reincarnateAnew}) {@code player}'s life is over: every animal about forgets it now, the rest as they
	 * load, and the roster and Icy's tracker let go of the horses they had ({@link Bonding#forgetLifeEverywhere}).
	 */
	static void bornAnew(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel over = server.overworld();
		UUID id = player.getUUID();
		long now = over.getGameTime();
		Map<UUID, Long> ended = new HashMap<>(over.getAttachedOrElse(ENDED, Map.of()));
		ended.put(id, now);
		over.setAttached(ENDED, Map.copyOf(ended));
		// (a parrot let down is loaded after the time above, unseen, so it forgets them as it lands)
		((com.jaymoskingdoms.mixin.PlayerShoulderInvoker)player).jaymoskingdoms$removeEntitiesOnShoulder();
		int n = 0;
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> all = new ArrayList<>();
			level.getAllEntities().forEach(all::add);
			for (Entity e : all) {
				if (e == player || e.isRemoved()) {
					continue;
				}
				if (forget(e, id, player)) {
					n++;
				}
				if (bonds(e)) {
					e.setAttached(SEEN, now);
				}
			}
		}
		Bonding.forgetLifeEverywhere(server, id);
		JaymosKingdoms.LOGGER.info("{}'s life ended: {} animals about forgot them (those away forget them as they load)",
			player.getScoreboardName(), n);
	}

	/**
	 * {@code e} forgets the life of {@code id} ({@code living}: the player themselves, when about, for their lead). True if it
	 * knew them.
	 */
	private static boolean forget(Entity e, UUID id, @Nullable ServerPlayer living) {
		boolean knew = false;
		if (e instanceof AbstractHorse horse) {
			knew = Bonding.forgetLife(horse, id);
		} else if (e instanceof TamableAnimal pet) {
			EntityReference<LivingEntity> owner = pet.getOwnerReference();
			if (owner != null && owner.getUUID().equals(id)) {
				pet.setOrderedToSit(false);
				pet.setInSittingPose(false);
				pet.setOwnerReference(null);
				pet.setTame(false, true);
				knew = true;
			}
		}
		if (living != null && e instanceof Leashable leashed && leashed.getLeashHolder() == living) {
			leashed.removeLeash();
			knew = true;
		}
		return knew;
	}
}
