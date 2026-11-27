package com.jaymoskingdoms.equestrian;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Winged and horned horses (the look is from Mo' Creatures' pegasus and unicorn, GPL-3, see {@code licenses/mocreatures}):
 * not a breed but a gift. A horse whose bond with its owner is full (Icy's Better Horses' bond at 100, through
 * {@link IcysHorses}; without it our own {@link Bonding} trust at {@link Bond#FULL}) can be given a full phial of an
 * alchemist's essence (Jaymo's Alchemy): <b>light</b> raises wings on it, <b>crystal</b> a horn of the same. It costs the
 * phial (and a phial of that much is a long night's work at the crucible), it works once on a horse, and neither kind
 * is ever found wild.
 * <ul>
 * <li><b>Winged:</b> ridden, it flies like the bat horse ({@code MythicMountsClient} steers it: jump climbs, forward
 * flies, otherwise it glides down), but it tires: the wings spend {@link #STAMINA} of stamina as they carry it, and it
 * gets it back on the ground. Winded, it comes down on a slow fall and can't take off again until it has rested to
 * {@link #RESTED}.</li>
 * <li><b>Horned:</b> its horn clears poison and withering from it and its rider every few seconds and mends it a little;
 * a horn is a cure, nothing more.</li>
 * </ul>
 * Their wings and horn are drawn by {@code WonderLayer} on whatever horse it is.
 */
public final class WonderHorses {
	public static final int NONE = 0, WINGED = 1, HORNED = 2;
	/** What each essence it takes is called in Jaymo's Alchemy, and how much of it (a full phial). */
	static final String LIGHT = "light", CRYSTAL = "crystal";
	static final int PHIAL_COST = 10;
	/** Wings: the stamina a horse has, what it loses each tick in the air and gets back each tick on the ground; the rest it needs to rise again. */
	static final float STAMINA = 200.0F, DRAIN = 0.6F, REGAIN = 1.2F, RESTED = 60.0F;
	/** Winded: how long it falls slowly (ticks). */
	static final int SLOW_FALL_TICKS = 120;
	/** The horn: how often it cures (ticks) and what it mends. */
	static final int CURE_EVERY = 80;
	static final float MEND = 1.0F;

	/** Which gift it has been given (0, 1, 2). Saved; synced (drawn on it). */
	public static final AttachmentType<Integer> KIND = AttachmentRegistry.create(JaymosKingdoms.id("wonder_horse"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Each winged horse's stamina and whether it is winded (server memory only: a horse comes back rested). */
	private static final Map<UUID, Float> STAMINAS = new HashMap<>();
	private static final Set<UUID> WINDED = new HashSet<>();

	private WonderHorses() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof AbstractHorse horse) || !(level instanceof ServerLevel server) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			return gift(server, player, hand, horse) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(WonderHorses::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STAMINAS.clear();
			WINDED.clear();
		});
	}

	public static int kind(net.minecraft.world.entity.@Nullable Entity horse) {
		Integer kind = horse == null ? null : horse.getAttached(KIND);
		return kind == null ? NONE : kind;
	}

	public static boolean winged(net.minecraft.world.entity.@Nullable Entity horse) {
		return kind(horse) == WINGED;
	}

	// ---- the gift ----

	/** Whether {@code horse} may be given a gift at all: not a dragon or an elephant, and grown. */
	private static boolean giftable(AbstractHorse horse) {
		return !horse.isBaby() && !(horse instanceof com.jaymoskingdoms.dragons.WindDragon) && !(horse instanceof WarElephant);
	}

	/** Full bond, by Icy's reckoning when it is there and ours when it is not. */
	static boolean fullBond(AbstractHorse horse, Player player) {
		if (Equestrian.ICY) {
			return IcysHorses.bond(horse) >= (int)Bond.FULL && Bonding.owner(horse, player);
		}
		return Bonding.owner(horse, player) && Bonding.trust(horse, player) >= Bond.FULL;
	}

	private static boolean gift(ServerLevel level, Player player, InteractionHand hand, AbstractHorse horse) {
		ItemStack phial = player.getItemInHand(hand);
		int want = essence(phial, LIGHT) >= PHIAL_COST ? WINGED : essence(phial, CRYSTAL) >= PHIAL_COST ? HORNED : NONE;
		if (want == NONE || !giftable(horse) || kind(horse) != NONE) {
			return false;
		}
		if (!horse.isTamed() || !fullBond(horse, player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wonder_horse.bond", horse.getDisplayName()));
			return true;
		}
		horse.setAttached(KIND, want);
		if (!player.isCreative()) {
			Item empty = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("jaymospowers", "phial"));
			phial.shrink(1);
			if (empty != null && empty != net.minecraft.world.item.Items.AIR) {
				ItemStack back = new ItemStack(empty);
				if (phial.isEmpty()) {
					player.setItemInHand(hand, back);
				} else if (!player.getInventory().add(back)) {
					level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY(0.5), player.getZ(), back));
				}
			}
		}
		double x = horse.getX(), y = horse.getY(0.7), z = horse.getZ();
		level.sendParticles(want == WINGED ? ParticleTypes.CLOUD : ParticleTypes.END_ROD, x, y + 0.5, z, 40, 0.8, 0.7, 0.8, 0.05);
		level.playSound(null, horse.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.6F, 0.7F);
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.3F);
		player.sendOverlayMessage(Component.translatable(want == WINGED ? "message.jaymoskingdoms.wonder_horse.winged" : "message.jaymoskingdoms.wonder_horse.horned",
			horse.getDisplayName()));
		return true;
	}

	/**
	 * How much {@code name} (an essence's id) the stack holds, read the way Jaymo's Alchemy keeps it (its essences component,
	 * a map of id to amount) with no reference to its classes; 0 without Alchemy or for anything but a phial of essence.
	 */
	static int essence(ItemStack stack, String name) {
		if (stack.isEmpty() || !"jaymospowers:essence_phial".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
			return 0;
		}
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.fromNamespaceAndPath("jaymospowers", "essences"));
		if (type == null) {
			return 0;
		}
		Object value = stack.get(type);
		if (value == null) {
			return 0;
		}
		@SuppressWarnings("unchecked")
		Codec<Object> codec = (Codec<Object>)type.codec();
		if (codec == null) {
			return 0;
		}
		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
		return json instanceof JsonObject object && object.has(name) ? object.get(name).getAsInt() : 0;
	}

	// ---- the gifts at work ----

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.getVehicle() instanceof AbstractHorse horse)) {
				continue;
			}
			switch (kind(horse)) {
				case WINGED -> fly(player, horse);
				case HORNED -> {
					if (server.getTickCount() % CURE_EVERY == 0) {
						cure(horse);
						cure(player);
					}
				}
				default -> {
				}
			}
		}
		// a winged horse with no rider rests
		for (UUID id : new HashSet<>(STAMINAS.keySet())) {
			boolean ridden = false;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ridden |= player.getVehicle() != null && player.getVehicle().getUUID().equals(id);
			}
			if (!ridden) {
				STAMINAS.remove(id);
				WINDED.remove(id);
			}
		}
	}

	/** The wings carry it while it has the strength; and it tires. */
	private static void fly(ServerPlayer player, AbstractHorse horse) {
		UUID id = horse.getUUID();
		float stamina = STAMINAS.getOrDefault(id, STAMINA);
		boolean winded = WINDED.contains(id);
		if (winded && stamina >= RESTED) {
			WINDED.remove(id);
			winded = false;
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wonder_horse.rested", horse.getDisplayName()));
		}
		if (winded) {
			horse.setNoGravity(false);
			stamina = Math.min(STAMINA, stamina + (horse.onGround() ? REGAIN : 0.0F));
		} else if (horse.onGround()) {
			horse.setNoGravity(true);
			stamina = Math.min(STAMINA, stamina + REGAIN);
		} else {
			horse.setNoGravity(true);
			horse.resetFallDistance();
			player.resetFallDistance();
			float before = stamina;
			stamina = Math.max(0.0F, stamina - DRAIN);
			if (before > STAMINA * 0.25F && stamina <= STAMINA * 0.25F) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wonder_horse.tiring", horse.getDisplayName()));
			}
			if (stamina <= 0.0F) {
				WINDED.add(id);
				horse.setNoGravity(false);
				horse.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, true, false));
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wonder_horse.winded", horse.getDisplayName()));
			}
		}
		STAMINAS.put(id, stamina);
	}

	private static void cure(LivingEntity body) {
		boolean cured = body.removeEffect(MobEffects.POISON) | body.removeEffect(MobEffects.WITHER);
		if (body instanceof AbstractHorse && body.getHealth() < body.getMaxHealth()) {
			body.heal(MEND);
			cured = true;
		}
		if (cured && body.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY(0.8), body.getZ(), 4, 0.4, 0.4, 0.4, 0.0);
		}
	}
}
