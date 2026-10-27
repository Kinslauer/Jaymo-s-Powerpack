package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.51, Andy's NOEA picks) Patch 4, fusion: the two things that fuse a pair without the dance.
 * <ul>
 * <li>The <b>Bonding Earrings</b> (a rare pair: the {@code bonding} wish gives two, and a beaten tyrant may drop two to
 * whoever hurt him most, {@link #tyrantDrop}). Each fighter wears one: face the other and use yours (right-click); when
 * the one you faced does the same to you within {@link #OFFER_TICKS}, both earrings go on and you are one at once, any
 * lineage, no dance, for {@link #EARRING_TICKS} (30 minutes) with {@link #EARRING_BONUS} on top. The earrings crack when it
 * ends.</li>
 * <li>The <b>Fusion Arm Bands</b> (crafted, two at a time): the same agreement, but for {@link #BAND_TICKS} (3 minutes)
 * and only {@link #BAND_BONUS}. They crack when it ends too.</li>
 * </ul>
 * The one who held his out first steers; the other watches from inside and takes over halfway, as in the dance. Both must
 * be Z Fighters who are free to fuse and not worn from a fusion within {@link ZFighterFusion#COOLDOWN}.
 */
public final class ZFighterFusionItems {
	/** Ticks the pair is fused for. */
	static final int EARRING_TICKS = 36000;
	static final int BAND_TICKS = 3600;
	/** What the fused body gets on top of the two added together (the dance gives {@link ZFighterFusion#FUSION_BONUS}). */
	static final float EARRING_BONUS = 0.25F;
	static final float BAND_BONUS = 0.04F;
	/** How long (ticks) a held-out earring or band waits for the other to answer; how near and how squarely they must face (the look's dot). */
	static final int OFFER_TICKS = 200;
	static final double RANGE = 5.0;
	static final double AIM = 0.8;
	/** The chance a beaten tyrant drops a pair of earrings for whoever hurt him most. */
	static final float TYRANT_DROP_CHANCE = 0.2F;
	/** Ticks the item is on cooldown after being used (no spam). */
	static final int USE_COOLDOWN = 20;

	public static Item BONDING_EARRING;
	public static Item FUSION_ARM_BAND;

	/** A fighter holding his out to another: whom, when, and which kind. */
	private record Offer(UUID to, long at, ZFighterFusion.Kind kind) {
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	private ZFighterFusionItems() {
	}

	static void init() {
		ResourceKey<Item> earringKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("bonding_earring"));
		BONDING_EARRING = Registry.register(BuiltInRegistries.ITEM, earringKey, new Charm(new Item.Properties().setId(earringKey).stacksTo(2)
			.rarity(Rarity.EPIC).fireResistant(), ZFighterFusion.Kind.EARRING));
		ResourceKey<Item> bandKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("fusion_arm_band"));
		FUSION_ARM_BAND = Registry.register(BuiltInRegistries.ITEM, bandKey, new Charm(new Item.Properties().setId(bandKey).stacksTo(2)
			.rarity(Rarity.UNCOMMON), ZFighterFusion.Kind.BAND));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(BONDING_EARRING);
			output.accept(FUSION_ARM_BAND);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> OFFERS.clear());
	}

	/** An earring or a band: use it facing the fighter you would bond with. */
	static final class Charm extends Item {
		private final ZFighterFusion.Kind kind;

		Charm(Properties properties, ZFighterFusion.Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer server) {
				offer(server, hand, kind);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), USE_COOLDOWN);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private static Item item(ZFighterFusion.Kind kind) {
		return kind == ZFighterFusion.Kind.EARRING ? BONDING_EARRING : FUSION_ARM_BAND;
	}

	/** The hand holding this kind of charm (main hand first), or null. */
	private static InteractionHand holding(ServerPlayer player, ZFighterFusion.Kind kind) {
		if (player.getMainHandItem().is(item(kind))) {
			return InteractionHand.MAIN_HAND;
		}
		return player.getOffhandItem().is(item(kind)) ? InteractionHand.OFF_HAND : null;
	}

	/** The Z Fighter {@code player} is facing within {@link #RANGE}, the squarest first. */
	private static ServerPlayer facing(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		ServerPlayer best = null;
		double bestDot = AIM;
		for (ServerPlayer other : player.level().players()) {
			if (other == player || other.distanceTo(player) > RANGE) {
				continue;
			}
			Vec3 toward = other.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
			double dot = look.dot(toward);
			if (dot > bestDot) {
				bestDot = dot;
				best = other;
			}
		}
		return best;
	}

	/** {@code player} uses his earring or band: held out to the fighter he faces, or the answer that fuses them. */
	static void offer(ServerPlayer player, InteractionHand hand, ZFighterFusion.Kind kind) {
		long now = player.level().getGameTime();
		if (!ZFighterFusion.fusable(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_charm_cannot").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (ZFighterFusion.worn(player, now)) {
			return;
		}
		ServerPlayer target = facing(player);
		if (target == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_charm_face").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ZFighterFusion.fusable(target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_charm_them", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Offer back = OFFERS.get(target.getUUID());
		if (back != null && back.to().equals(player.getUUID()) && back.kind() == kind && now - back.at() <= OFFER_TICKS
			&& holding(target, kind) != null) {
			OFFERS.remove(target.getUUID());
			OFFERS.remove(player.getUUID());
			bond(target, player, kind);
			return;
		}
		OFFERS.put(player.getUUID(), new Offer(target.getUUID(), now, kind));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_charm_offered", target.getDisplayName()).withStyle(ChatFormatting.AQUA));
		target.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_charm_asked", player.getDisplayName(), new ItemStack(item(kind)).getHoverName())
			.withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	/** Both agreed: the charms go on (one from each hand) and {@code leader}, who held his out first, steers. */
	private static void bond(ServerPlayer leader, ServerPlayer partner, ZFighterFusion.Kind kind) {
		long now = leader.level().getGameTime();
		if (ZFighterFusion.worn(leader, now) || ZFighterFusion.worn(partner, now)) {
			return;
		}
		for (ServerPlayer wearer : new ServerPlayer[] {leader, partner}) {
			InteractionHand hand = holding(wearer, kind);
			if (hand != null && !wearer.isCreative()) {
				wearer.getItemInHand(hand).shrink(1);
			}
		}
		boolean earrings = kind == ZFighterFusion.Kind.EARRING;
		ZFighterFusion.fuseWith(leader, partner, kind, earrings ? EARRING_TICKS : BAND_TICKS, earrings ? EARRING_BONUS : BAND_BONUS);
	}

	/** The fusion ended: what was worn cracks and is gone. */
	static void crack(ServerPlayer leader, ZFighterFusion.Kind kind) {
		ServerLevel level = leader.level();
		level.sendParticles(ParticleTypes.CRIT, leader.getX(), leader.getY() + 1.5, leader.getZ(), 16, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 0.8F);
		leader.sendSystemMessage(Component.translatable(kind == ZFighterFusion.Kind.EARRING ? "message.jaymospowers.zfighter_earrings_crack"
			: "message.jaymospowers.zfighter_bands_crack").withStyle(ChatFormatting.GRAY));
	}

	/** A beaten tyrant may leave a pair of earrings for whoever hurt him most. */
	static void tyrantDrop(ServerLevel level, ZFighterRogue tyrant) {
		ServerPlayer top = null;
		float most = 0.0F;
		for (Map.Entry<UUID, Float> entry : tyrant.hurtBy.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null && entry.getValue() > most) {
				most = entry.getValue();
				top = player;
			}
		}
		if (top == null || level.getRandom().nextFloat() >= TYRANT_DROP_CHANCE) {
			return;
		}
		ItemStack pair = new ItemStack(BONDING_EARRING, 2);
		if (!top.getInventory().add(pair)) {
			top.drop(pair, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		top.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_tyrant_earrings").withStyle(ChatFormatting.LIGHT_PURPLE));
	}
}
