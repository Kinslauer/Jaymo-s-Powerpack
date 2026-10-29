package com.jaymospowers.tome;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;

/**
 * (Andy, 2026-10-29: "make the starter tome a new type of weapon") The spell tome: its look is a Blockbench Workshop
 * model from Andy's batch (tools/blockbench/spell_tome.py), its code is ours. Controls:
 * <ul>
 * <li><b>Left click, tap</b>: a quick bolt of the current element ({@link TomeBolt}), with a short wait before the next.</li>
 * <li><b>Left click, hold</b>: charges (a bar on the action bar, which {@code HeldInput} treats like any hold: no swinging);
 * let go to fire a bolt as strong as the charge.</li>
 * <li><b>Sneak + right click</b>: the next element: fire, frost, lightning, arcane.</li>
 * <li><b>Right click</b>: with nothing stored, a short channel stores a spell of the current element, as strong as a full
 * charge; with one stored, it's cast at once.</li>
 * </ul>
 * Vanilla sends nothing to the server for a left click on air, so the client ({@code TomeClient}) sends the press and the
 * release as {@link Attack} and the server times the hold.
 */
public final class Tome {
	// ---- tuning ----
	/** Held this many ticks or less on release, a click is a tap; the charge then runs from here to {@link #FULL_TICKS}. */
	public static final int TAP_TICKS = 6, FULL_TICKS = 30;
	/** The quickest next tap (ticks), the wait after a charged bolt, and after switching element. */
	public static final int QUICK_COOLDOWN = 10, CHARGED_COOLDOWN = 14, CYCLE_COOLDOWN = 6;
	/** Storing a spell takes this long (ticks) with the right button held. */
	public static final int CHANNEL_TICKS = 30;
	/** Durability: the whole tome, and what a quick bolt, a charged bolt, storing and a stored cast take. */
	public static final int DURABILITY = 320, QUICK_WEAR = 1, CHARGED_WEAR = 2, STORE_WEAR = 2, RELEASE_WEAR = 1;
	/** A charge bar's length (HeldInput reads 8 or more as a hold), and a hold nobody lets go of is dropped after this. */
	public static final int BAR_LENGTH = 12, MAX_HOLD_TICKS = 1200;
	/** A charge never starts a new bolt below this share of the full power (the quick bolt is power 0). */
	public static final float CHARGE_FLOOR = 0.2F;

	/** The element the tome is set to (an id, see {@link TomeElement}); the stored spell's element, when it has one. */
	public static DataComponentType<String> ELEMENT, STORED;
	public static Item SPELL_TOME;
	public static EntityType<TomeBolt> BOLT;

	/** Who holds the left button on a tome, and since when (server ticks). */
	private static final Map<UUID, Long> HOLDS = new HashMap<>();
	/** When each caster's next tap bolt may fly (server ticks). */
	private static final Map<UUID, Long> READY = new HashMap<>();

	/** Client → server: the left button on a tome. {@code action}: 1 pressed, 0 let go (fire), 2 dropped (item swapped, screen opened). */
	public record Attack(int action) implements CustomPacketPayload {
		public static final Type<Attack> TYPE = new Type<>(JaymosPowers.id("tome_attack"));
		public static final StreamCodec<ByteBuf, Attack> CODEC = ByteBufCodecs.VAR_INT.map(Attack::new, Attack::action);

		@Override
		public Type<Attack> type() {
			return TYPE;
		}
	}

	private Tome() {
	}

	public static void init() {
		ELEMENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("tome_element"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		STORED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("tome_stored"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("spell_tome"));
		SPELL_TOME = Registry.register(BuiltInRegistries.ITEM, itemKey, new TomeItem(new Item.Properties().setId(itemKey)
			.durability(DURABILITY).rarity(Rarity.UNCOMMON).enchantable(15).repairable(Items.AMETHYST_SHARD).component(ELEMENT, TomeElement.FIRE.id)));
		ResourceKey<EntityType<?>> boltKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("tome_bolt"));
		BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE, boltKey, EntityType.Builder.<TomeBolt>of(TomeBolt::new, MobCategory.MISC)
			.noLootTable().noSummon().sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build(boltKey));

		// next to the Armory's weapons, and in the vanilla combat tab
		ResourceKey<CreativeModeTab> armory = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("armory"));
		CreativeModeTabEvents.modifyOutputEvent(armory).register(output -> output.accept(SPELL_TOME));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(SPELL_TOME));

		PayloadTypeRegistry.serverboundPlay().register(Attack.TYPE, Attack.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Attack.TYPE, (payload, context) -> onAttack(context.player(), payload.action()));
		ServerTickEvents.END_SERVER_TICK.register(Tome::tick);
	}

	/** Whether {@code player} holds a tome in their main hand. */
	public static boolean holdsTome(Player player) {
		return player.getMainHandItem().getItem() instanceof TomeItem;
	}

	/** About where the book is in the caster's hand, for the particles. */
	static Vec3 handPoint(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize().scale(player.getMainArm() == HumanoidArm.RIGHT ? -1.0 : 1.0);
		return player.getEyePosition().add(look.scale(0.7)).add(right.scale(0.3)).add(0.0, -0.35, 0.0);
	}

	// ---- left click ----

	private static void onAttack(ServerPlayer player, int action) {
		UUID id = player.getUUID();
		long now = player.level().getServer().getTickCount();
		if (action == 1) {
			if (holdsTome(player) && !player.isUsingItem() && !player.isSpectator() && player.isAlive()) {
				HOLDS.put(id, now);
			}
			return;
		}
		Long since = HOLDS.remove(id);
		if (since == null) {
			return;
		}
		if (action != 0 || !holdsTome(player) || !player.isAlive()) {
			player.sendOverlayMessage(Component.empty());
			return;
		}
		release(player, (int) Math.min(now - since, MAX_HOLD_TICKS), now);
	}

	/** A hold of {@code held} ticks ends: a tap is a quick bolt (if it's ready), anything longer a bolt as strong as the charge. */
	private static void release(ServerPlayer player, int held, long now) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		TomeElement element = TomeItem.element(stack);
		float power;
		int wear;
		if (held <= TAP_TICKS) {
			if (now < READY.getOrDefault(player.getUUID(), 0L)) {
				return;
			}
			power = 0.0F;
			wear = QUICK_WEAR;
			READY.put(player.getUUID(), now + QUICK_COOLDOWN);
		} else {
			power = CHARGE_FLOOR + (1.0F - CHARGE_FLOOR) * charge(held);
			wear = CHARGED_WEAR;
			READY.put(player.getUUID(), now + CHARGED_COOLDOWN);
			player.sendOverlayMessage(Component.empty());
		}
		TomeBolt.cast(level, player, element, power);
		stack.hurtAndBreak(wear, player, InteractionHand.MAIN_HAND);
		player.swing(InteractionHand.MAIN_HAND, stack.getAttackAnimation(), true);
	}

	/** How charged a hold of {@code held} ticks is, 0 to 1. */
	static float charge(int held) {
		return Mth.clamp((held - TAP_TICKS) / (float) (FULL_TICKS - TAP_TICKS), 0.0F, 1.0F);
	}

	/** The charge bars and cues of every hold. */
	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (HOLDS.isEmpty()) {
			if (READY.size() > 64) {
				READY.values().removeIf(t -> t < now);
			}
			return;
		}
		for (Iterator<Map.Entry<UUID, Long>> it = HOLDS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int held = (int) (now - entry.getValue());
			if (player == null || !player.isAlive() || !holdsTome(player) || player.isUsingItem() || held > MAX_HOLD_TICKS) {
				it.remove();
				if (player != null) {
					player.sendOverlayMessage(Component.empty());
				}
				continue;
			}
			if (held >= TAP_TICKS && player.level() instanceof ServerLevel level) {
				chargeCue(level, player, TomeItem.element(player.getMainHandItem()), held);
			}
		}
	}

	/** The bar, and motes and a rising chime as the charge builds. */
	private static void chargeCue(ServerLevel level, ServerPlayer player, TomeElement element, int held) {
		float charge = charge(held);
		player.sendOverlayMessage(bar(charge, element));
		Vec3 hand = handPoint(player);
		if (held % 2 == 0) {
			level.sendParticles(element.trail, hand.x, hand.y, hand.z, 1 + (int) (3 * charge), 0.12, 0.12, 0.12, 0.02);
		}
		int step = (int) (charge * 3.0F);
		int lastStep = (int) (charge(held - 1) * 3.0F);
		if (step != lastStep || held == FULL_TICKS) {
			boolean full = held >= FULL_TICKS && charge(held - 1) < 1.0F;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				full ? 1.0F : 0.6F, 0.8F + 0.4F * charge);
			if (full) {
				level.sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 8, 0.2, 0.2, 0.2, 0.05);
			}
		}
	}

	/** One tick of the right-click channel that stores a spell: the bar fills, motes gather. */
	static void channelTick(ServerLevel level, Player player, TomeElement element, float progress) {
		player.sendOverlayMessage(bar(progress, element));
		Vec3 hand = handPoint(player);
		level.sendParticles(element.trail, hand.x, hand.y, hand.z, 1 + (int) (2 * progress), 0.15, 0.15, 0.15, 0.02);
		if (player.tickCount % 7 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F,
				0.8F + 0.6F * progress);
		}
	}

	/** {@code "|"} filled, {@code "."} to go: the shape every hold's charge bar has, which {@code HeldInput} recognises. */
	private static Component bar(float fraction, TomeElement element) {
		int filled = Mth.clamp(Math.round(fraction * BAR_LENGTH), 0, BAR_LENGTH);
		return Component.literal("|".repeat(filled) + ".".repeat(BAR_LENGTH - filled)).withColor(element.color);
	}
}
