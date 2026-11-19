package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A ridden horse: a bonded host riding a horse can let the symbiote reach down into it (the radial menu's Steed chip,
 * while mounted; pick it again to let go). The horse goes glossy black (red or white in the strain's own colour: the
 * client draws it over the horse's own model, {@code WebBehemothClient}), needs no saddle (it is given a saddle of goo
 * for as long as it's held, and only if it had none), leaps like a deer ({@link #JUMP_BONUS} more jump strength), lands
 * from any height, and the symbiote eats for two while it holds it ({@link #DRAIN_MULT} times the hunger). It lets go
 * when asked, when the host has been off it {@link #LINGER_TICKS} (or {@link #MAX_AWAY} blocks away), when it starves
 * or when the horse dies.
 */
public final class WebSymbioteSteed {
	/** More jump strength (a deer's leap: about triple a horse's rise), and the safe fall it gets (blocks). */
	public static final double JUMP_BONUS = 0.5, SAFE_FALL_BONUS = 256.0;
	/** Hunger drains this many times as fast while it holds a horse. */
	public static final int DRAIN_MULT = 2;
	/** Ticks the host may be off it before it lets go, and how far (blocks) it may be. */
	public static final int LINGER_TICKS = 200;
	public static final double MAX_AWAY = 24.0;
	/** Satiety it needs to take a horse at all, and the bond. */
	public static final int MIN_SATIETY = 20;
	public static final int MIN_BOND = WebSymbiote.TENDRILS_BOND;

	private static final Identifier JUMP_ID = JaymosPowers.id("web_steed_jump");
	private static final Identifier FALL_ID = JaymosPowers.id("web_steed_fall");

	/** On the horse: it is held, in this strain (its id; synced to everyone, who see it black). Not saved. */
	public static final AttachmentType<Integer> STEED = AttachmentRegistry.create(JaymosPowers.id("web_steed"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** What a host holds: the horse's id, whether the saddle is ours, and since when the host has been off it (game time, or -1). */
	private record Held(UUID horse, boolean saddled, long offSince) {
	}

	private static final Map<UUID, Held> HELD = new ConcurrentHashMap<>();

	private WebSymbioteSteed() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!HELD.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					tick(player);
				}
			}
		});
	}

	/** The host has a horse in its hold (server). */
	static boolean held(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	/** The player's mount is held by the symbiote (either side). */
	public static boolean riding(net.minecraft.world.entity.player.Player player) {
		return player.getVehicle() != null && player.getVehicle().hasAttached(STEED);
	}

	/** The player is on a horse the symbiote could take, or already has. */
	public static boolean available(net.minecraft.world.entity.player.Player player) {
		return WebSymbiote.isBonded(player) && player.getVehicle() instanceof AbstractHorse;
	}

	/** The radial menu's Steed chip: take the horse under you, or let it go. */
	static void toggle(ServerPlayer player) {
		if (!WebSymbiote.isBonded(player) || !player.isAlive() || player.isSpectator() || WebSymbiote.driving(player)) {
			return;
		}
		if (held(player)) {
			release(player, "steed.out", true);
			return;
		}
		if (!(player.getVehicle() instanceof AbstractHorse horse)) {
			WebSymbiote.whisper(player, "steed.none");
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.bond() < MIN_BOND && !player.isCreative() || state.satiety() < MIN_SATIETY && !player.isCreative() || horse.hasAttached(STEED)) {
			WebSymbiote.whisper(player, "steed.refuse");
			return;
		}
		boolean ours = !horse.isSaddled();
		if (ours) {
			horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
			horse.setDropChance(EquipmentSlot.SADDLE, 0.0F);
		}
		modify(horse, JUMP_ID, Attributes.JUMP_STRENGTH, JUMP_BONUS);
		modify(horse, FALL_ID, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_BONUS);
		horse.setAttached(STEED, state.strain());
		HELD.put(player.getUUID(), new Held(horse.getUUID(), ours, -1L));
		ServerLevel level = player.level();
		level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
		level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 0.8F, 0.7F);
		level.sendParticles(WebSymbiote.dust(1.4F), horse.getX(), horse.getY() + horse.getBbHeight() * 0.5, horse.getZ(), 30,
			horse.getBbWidth() * 0.4, horse.getBbHeight() * 0.4, horse.getBbWidth() * 0.4, 0.0);
		WebSymbiote.whisper(player, "steed.in." + (1 + player.getRandom().nextInt(2)));
	}

	private static void modify(AbstractHorse horse, Identifier id, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount) {
		AttributeInstance instance = horse.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Lets the horse go: plain again, its goo saddle off if it was ours. */
	static void release(ServerPlayer player, String line, boolean sound) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		ServerLevel level = player.level();
		Entity entity = level.getEntity(held.horse());
		if (entity instanceof AbstractHorse horse) {
			horse.removeAttached(STEED);
			AttributeInstance jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
			if (jump != null) {
				jump.removeModifier(JUMP_ID);
			}
			AttributeInstance fall = horse.getAttribute(Attributes.SAFE_FALL_DISTANCE);
			if (fall != null) {
				fall.removeModifier(FALL_ID);
			}
			if (held.saddled()) {
				horse.setItemSlot(EquipmentSlot.SADDLE, ItemStack.EMPTY);
			}
			if (sound) {
				level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.sendParticles(ParticleTypes.SQUID_INK, horse.getX(), horse.getY() + horse.getBbHeight() * 0.5, horse.getZ(), 20, 0.4, 0.5, 0.4, 0.04);
			}
		}
		if (line != null && WebSymbiote.isBonded(player)) {
			WebSymbiote.whisper(player, line);
		}
	}

	/** Every tick, for a bonded host holding a horse (server). */
	static void tick(ServerPlayer player) {
		Held held = HELD.get(player.getUUID());
		if (held == null) {
			return;
		}
		ServerLevel level = player.level();
		Entity entity = level.getEntity(held.horse());
		if (!(entity instanceof AbstractHorse horse) || !horse.isAlive() || !WebSymbiote.isBonded(player) || !player.isAlive()) {
			release(player, null, false);
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.satiety() <= WebSymbiote.STARVING && !player.isCreative()) {
			release(player, "steed.hungry", true);
			return;
		}
		horse.resetFallDistance();
		boolean riding = horse.hasPassenger(player);
		long now = level.getGameTime();
		if (riding) {
			if (held.offSince() >= 0L) {
				HELD.put(player.getUUID(), new Held(held.horse(), held.saddled(), -1L));
			}
		} else if (held.offSince() < 0L) {
			HELD.put(player.getUUID(), new Held(held.horse(), held.saddled(), now));
		} else if (now - held.offSince() > LINGER_TICKS || player.distanceTo(horse) > MAX_AWAY) {
			release(player, "steed.left", true);
			return;
		}
		if (player.tickCount % 8 == 0) {
			level.sendParticles(WebSymbiote.dust(0.8F), horse.getX(), horse.getY() + horse.getBbHeight() * 0.6, horse.getZ(), 2,
				horse.getBbWidth() * 0.4, horse.getBbHeight() * 0.3, horse.getBbWidth() * 0.4, 0.0);
		}
	}

	static void forget(ServerPlayer player) {
		release(player, null, false);
	}
}
