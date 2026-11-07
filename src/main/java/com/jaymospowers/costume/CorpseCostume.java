package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.KitchenCompat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.49) Andy (2026-11-01): "dying should remove your costume ... your corpse will still have the costume on and it can
 * be removed after death to identify you as well".
 * <ul>
 * <li>A player who dies comes back without their costume: it stays on their body (Jaymo's Kitchen's corpse,
 * {@link Costume#CORPSE_WORN}), drawn on it, mask and all ({@code client/costume/CorpseCostumeClient}). Without a body
 * (no Kitchen, a body that leaves nothing, a spectator) the costume falls where they died instead.</li>
 * <li>Use the body with an empty hand ({@link #strip}): the costume comes off into your hands, and with it off the dead
 * face shows. You are told whose body it is, and if the costume hid the face, Jaymo's Kingdoms hears of it: the
 * settlement whose people see it (and you) learn who wore that costume.</li>
 * <li>The body's tags carry what Kingdoms needs, read by name ({@link #OWNER}, {@link #MASK}, {@link #MASK_NAME}): its
 * guards walk up and strip a costumed body lying in their town themselves.</li>
 * <li>If the body goes (burned, risen, crumbled) the costume falls where it lay.</li>
 * </ul>
 * {@link #unmask} is Kingdoms' way in for its guards to unmask a downed player ({@link Costume#unmaskBy}).
 */
public final class CorpseCostume {
	/** Tags on a costumed body for Kingdoms: whose it was; the identity the costume hid (only if it hid the face); its name. */
	public static final String OWNER = "jaymospowers_corpse_of:", MASK = "jaymospowers_corpse_mask:", MASK_NAME = "jaymospowers_corpse_mask_name:";
	/** How long after a death its body may turn up to take the costume (ticks); then it drops where they died. */
	static final int BODY_WAIT = 40;
	private static final Identifier CORPSE = Identifier.fromNamespaceAndPath("jaymoskitchen", "corpse");
	private static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");

	/** A costume taken off someone who just died, waiting for their body. */
	private record Pending(CostumeDesign design, boolean unmasked, ResourceKey<Level> dimension, Vec3 at, long since) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private CorpseCostume() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				died(player);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!PENDING.isEmpty() && isCorpse(entity)) {
				UUID owner = KitchenCompat.corpseOwner(entity);
				Pending pending = owner == null ? null : PENDING.remove(owner);
				if (pending != null) {
					dress(entity, pending);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			// the body is gone for good (burned, risen, crumbled): the costume falls where it lay
			if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy() && entity.hasAttached(Costume.CORPSE_WORN)) {
				CostumeDesign design = entity.getAttached(Costume.CORPSE_WORN);
				if (design != null) {
					dropAt(level, entity.position(), design);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CorpseCostume::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
				|| !entity.hasAttached(Costume.CORPSE_WORN)) {
				return InteractionResult.PASS;
			}
			if (!world.isClientSide()) {
				strip(entity, player);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** {@code player} has just died: their costume comes off them, to go on their body (next tick, when it appears). */
	private static void died(ServerPlayer player) {
		CostumeDesign worn = Costume.worn(player);
		if (worn == null) {
			return;
		}
		boolean unmasked = Costume.unmasked(player);
		player.removeAttached(Costume.WORN);
		player.removeAttached(Costume.UNMASKED);
		SecretIdentity.update(player);
		PENDING.put(player.getUUID(), new Pending(worn, unmasked, player.level().dimension(), player.position(), player.level().getGameTime()));
	}

	/** Bodies that never turned up: the costume falls where they died. */
	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Pending p = it.next().getValue();
			ServerLevel level = server.getLevel(p.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			long age = level.getGameTime() - p.since();
			if (age >= BODY_WAIT || age < 0) {
				it.remove();
				dropAt(level, p.at(), p.design());
			}
		}
	}

	private static boolean isCorpse(Entity entity) {
		return CORPSE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}

	/** The body takes the costume its owner died in, and the tags Kingdoms reads. */
	private static void dress(Entity corpse, Pending p) {
		corpse.setAttached(Costume.CORPSE_WORN, p.design());
		if (p.unmasked()) {
			corpse.setAttached(Costume.CORPSE_UNMASKED, true);
		}
		UUID owner = KitchenCompat.corpseOwner(corpse);
		if (owner != null) {
			corpse.addTag(OWNER + owner);
		}
		if (p.design().mask().hidesFace && !p.unmasked()) {
			corpse.addTag(MASK + SecretIdentity.key(p.design()));
			if (!p.design().name().isEmpty()) {
				corpse.addTag(MASK_NAME + p.design().name());
			}
		}
	}

	/** The costume {@code corpse} wears, or null (either side). */
	public static @Nullable CostumeDesign worn(Entity corpse) {
		return corpse.getAttached(Costume.CORPSE_WORN);
	}

	/**
	 * The costume is pulled off {@code corpse} (server) by {@code by}: a player gets it in hand (or it drops), anyone else
	 * (a guard) leaves it lying by the body. The dead face shows; whoever took it is told whose body it is, and Kingdoms
	 * settles who else learns who wore the costume. False if it wore none. Kingdoms calls this by name for its guards.
	 */
	public static boolean strip(Entity corpse, @Nullable LivingEntity by) {
		CostumeDesign design = worn(corpse);
		if (design == null || !(corpse.level() instanceof ServerLevel level)) {
			return false;
		}
		String key = null;
		UUID owner = null;
		for (String tag : new ArrayList<>(corpse.entityTags())) {
			if (tag.startsWith(MASK)) {
				key = tag.substring(MASK.length());
			} else if (tag.startsWith(OWNER)) {
				try {
					owner = UUID.fromString(tag.substring(OWNER.length()));
				} catch (IllegalArgumentException e) {
					owner = null;
				}
			}
			if (tag.startsWith(MASK) || tag.startsWith(OWNER) || tag.startsWith(MASK_NAME)) {
				corpse.removeTag(tag);
			}
		}
		corpse.removeAttached(Costume.CORPSE_WORN);
		corpse.removeAttached(Costume.CORPSE_UNMASKED);
		ItemStack item = CostumeItem.of(design);
		if (by instanceof Player player) {
			player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
		} else {
			dropAt(level, corpse.position(), design);
		}
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (by instanceof ServerPlayer player) {
			String name = KitchenCompat.corpseOwnerName(corpse);
			ServerPlayer living = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			Component who = living != null && key == null ? living.getDisplayName() : living != null ? living.getName() : Component.literal(name);
			player.sendOverlayMessage(Component.translatable(key != null ? "message.jaymospowers.costume.corpse_unmasked"
				: "message.jaymospowers.costume.corpse_stripped", SecretIdentity.shownName(design), who));
		}
		if (key != null && owner != null && KINGDOMS) {
			kingdomsUnmasked(level, corpse, owner, key, by);
		}
		return true;
	}

	/** Kingdoms' guards: take {@code player}'s face covering off (downed in their town). Called by name. */
	public static boolean unmask(ServerPlayer player) {
		return Costume.unmaskBy(player);
	}

	private static void dropAt(ServerLevel level, Vec3 at, CostumeDesign design) {
		ItemEntity drop = new ItemEntity(level, at.x, at.y + 0.3, at.z, CostumeItem.of(design));
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}

	private static java.lang.reflect.@Nullable Method unmaskedHook;
	private static boolean lookedForHook;

	/** Jaymo's Kingdoms' {@code Identities.corpseUnmasked}: who learns whose face was under the mask. */
	private static void kingdomsUnmasked(ServerLevel level, Entity corpse, UUID owner, String key, @Nullable LivingEntity by) {
		if (!lookedForHook) {
			lookedForHook = true;
			try {
				unmaskedHook = Class.forName("com.jaymoskingdoms.Identities").getMethod("corpseUnmasked", ServerLevel.class, Entity.class, UUID.class,
					String.class, LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Jaymo's Kingdoms has no corpse unmasking (older Kingdoms?)", e);
			}
		}
		if (unmaskedHook != null) {
			try {
				unmaskedHook.invoke(null, level, corpse, owner, key, by);
			} catch (ReflectiveOperationException | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Kingdoms' corpse unmasking failed", e);
			}
		}
	}
}
