package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * (Better With Mods port, batch 5) Fear of the dark, after Better With Mods' HCGloom (MIT; its idea, our own code).
 * Standing in pitch darkness in the Overworld (no block light and no sky light at the eyes) slowly builds a hidden
 * gloom: first whispers round the player (client), then the screen's edges darken (client), then short Darkness pulses.
 * On a Blood Moon night only, someone at the top of the gloom is hurt a little now and then ("was taken by the dark");
 * on any other night the dark never does real harm. Light drains it fast: a lit place, Night Vision, or a light-giving
 * block (torch, lantern, glowstone...) in either hand. Nightborn, creative and spectator players never gather any.
 *
 * <p>Only the Overworld: the Nether has no sky light at all (it would be gloom everywhere without a torch) and the End's
 * darkness is its own; BWM's default kept it to the Overworld as well. Kingdoms doesn't track an Emberborn's lit hands
 * (only Jaymo's Powers does), so those don't count here. No meter: the gloom is felt, not shown.
 */
public final class Gloom {
	/** How often each player's darkness is looked at (ticks); gloom gathers this much per look in the dark. */
	static final int CHECK_EVERY = 10;
	/** Gloom lost per look in the light (several times quicker than it gathers). */
	static final int DRAIN = 60;
	/** Gloom (ticks in the dark) at which each stage begins: whispers, darkened edges, Darkness pulses; and its cap. */
	static final int WHISPERS_AT = 600, EDGES_AT = 1200, DREAD_AT = 2400, MAX = 3000;
	/** Top stage: a Darkness pulse every this many ticks, lasting this long. */
	static final int PULSE_EVERY = 200, PULSE_TICKS = 50;
	/** Top stage, Blood Moon only: a hurt every this many ticks, this much (magic, through armour). */
	static final int HURT_EVERY = 40;
	static final float HURT = 1.0F;

	/** The dark's own damage ("was taken by the dark"). */
	static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKingdoms.id("gloom"));

	/** A player's gloom (ticks in the dark, less what light has drained). Not saved: a rejoin or respawn starts clear. */
	static final AttachmentType<Integer> GLOOM = AttachmentRegistry.create(JaymosKingdoms.id("gloom"));

	/** Server → player: their gloom stage now (0 none, 1 whispers, 2 edges, 3 dread). */
	public record Sync(int stage) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("gloom"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.stage), buf -> new Sync(buf.readVarInt()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** The client's side: the stage it was last told of. */
	public static volatile int clientStage;

	private Gloom() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Gloom::tick);
		// a respawned or rejoining player starts clear (the gloom isn't kept), so their screen does too
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clear(newPlayer));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clear(handler.player));
	}

	private static void clear(ServerPlayer player) {
		player.removeAttached(GLOOM);
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(0));
		}
	}

	/** The stage for {@code gloom}. */
	public static int stage(int gloom) {
		return gloom >= DREAD_AT ? 3 : gloom >= EDGES_AT ? 2 : gloom >= WHISPERS_AT ? 1 : 0;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		boolean look = now % CHECK_EVERY == 0;
		boolean blood = Moons.now(server) == Moons.Moon.BLOOD || Moons.now(server) == Moons.Moon.SUPER_BLOOD;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int was = player.getAttachedOrElse(GLOOM, 0);
			if (look) {
				int gloom = immune(player) ? 0 : dark(player) ? Math.min(MAX, was + CHECK_EVERY) : Math.max(0, was - DRAIN);
				if (gloom != was) {
					if (gloom == 0) {
						player.removeAttached(GLOOM);
					} else {
						player.setAttached(GLOOM, gloom);
					}
					if (stage(gloom) != stage(was) && ServerPlayNetworking.canSend(player, Sync.TYPE)) {
						ServerPlayNetworking.send(player, new Sync(stage(gloom)));
					}
				}
				was = gloom;
			}
			if (stage(was) < 3) {
				continue;
			}
			if ((now + player.getId()) % PULSE_EVERY == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, PULSE_TICKS, 0, true, false, false));
			}
			if (blood && (now + player.getId()) % HURT_EVERY == 0 && player.level() instanceof ServerLevel level) {
				DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
				player.hurtServer(level, source, HURT);
			}
		}
	}

	/** Never troubled by the dark: Nightborn, creative, spectator, the dead. */
	static boolean immune(ServerPlayer player) {
		return !player.isAlive() || player.isCreative() || player.isSpectator() || Heritage.is(player, Heritage.Kind.NIGHTBORN);
	}

	/** In the Overworld, pitch dark at the eyes, with no light of their own. */
	static boolean dark(ServerPlayer player) {
		Level level = player.level();
		if (level.dimension() != Level.OVERWORLD || player.hasEffect(MobEffects.NIGHT_VISION)) {
			return false;
		}
		// block light and sky light (darkened for the time of day) both nothing
		if (level.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) > 0) {
			return false;
		}
		return !lit(player.getItemInHand(InteractionHand.MAIN_HAND)) && !lit(player.getItemInHand(InteractionHand.OFF_HAND));
	}

	/** A light carried in hand: any block that gives light (a torch, lantern, glowstone, sea lantern...). */
	static boolean lit(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().getLightEmission() > 0;
	}
}
