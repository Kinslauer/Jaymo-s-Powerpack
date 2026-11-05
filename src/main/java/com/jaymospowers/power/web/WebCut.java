package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Anyone can punch a web line to break it: a swing line, a Yank, a hitch, a webline. The client finds the line under
 * the crosshair (anywhere along it, the shooter's end or the anchor) and says which; the server checks it's real and
 * in reach, and cuts it. Each line takes one to four punches (rolled per line) before it gives.
 */
public final class WebCut {
	public static final byte SWING = 0, HITCH = 1, BRIDGE = 2;
	/** How far from the eyes a line can be punched (blocks), with a little slack for lag. */
	public static final double REACH = 4.5;
	/**
	 * Each line takes {@code MIN_HITS} to {@code MAX_HITS} punches to break (rolled per line, the first time it's hit).
	 * A punched line remembers its hits for {@code MEMORY_TICKS} after the last one.
	 */
	public static final int MIN_HITS = 1, MAX_HITS = 4, MEMORY_TICKS = 200;

	/** A line that has been punched but held: which (kind, whose, about where), how many hits it takes, has taken. */
	private static final class Frayed {
		final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
		final byte kind;
		final int entity;
		Vec3 at;
		final int needed;
		int hits;
		long last;

		Frayed(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, byte kind, int entity, Vec3 at, int needed) {
			this.dimension = dimension;
			this.kind = kind;
			this.entity = entity;
			this.at = at;
			this.needed = needed;
		}
	}

	private static final java.util.List<Frayed> FRAYED = new java.util.ArrayList<>();

	/** Client → server: punch the line {@code kind} (whose player or hitched creature is {@code entity}) between {@code a} and {@code b}. */
	public record Cut(byte kind, int entity, Vec3 a, Vec3 b) implements CustomPacketPayload {
		public static final Type<Cut> TYPE = new Type<>(JaymosPowers.id("web_cut"));
		public static final StreamCodec<ByteBuf, Cut> CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Cut::kind, ByteBufCodecs.VAR_INT, Cut::entity, Vec3.STREAM_CODEC, Cut::a, Vec3.STREAM_CODEC, Cut::b, Cut::new);

		@Override
		public Type<Cut> type() {
			return TYPE;
		}
	}

	private WebCut() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Cut.TYPE, Cut.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cut.TYPE, (payload, ctx) -> cut(ctx.player(), payload));
	}

	private static void cut(ServerPlayer player, Cut cut) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		if (distanceToSegment(eye, cut.a(), cut.b()) > REACH + 1.5) {
			return;
		}
		// (0.19.71) fire in the fist (a torch, a flint, a fire charge, a blaze rod, lava, a hot or Fire Aspect blade, or
		// you alight) goes through at once: a plain web burns, a symbiote's cord recoils
		boolean fiery = fiery(player);
		Vec3 hit = closestPoint(eye, cut.a(), cut.b());
		if (fiery) {
			if (dark(level, cut)) {
				WebHeft.recoilFx(level, hit);
			} else {
				WebHeft.burnFx(level, hit);
			}
		}
		// it may hold for a punch or three first
		if (!fiery && !giveWay(level, cut)) {
			Vec3 at = closestPoint(eye, cut.a(), cut.b());
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.8F, 0.6F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.02);
			return;
		}
		// it parts where it was hit: two broken halves
		boolean done = switch (cut.kind()) {
			case SWING -> level.getEntity(cut.entity()) instanceof ServerPlayer owner && WebPower.cutLine(owner, cut.b(), hit);
			case HITCH -> {
				Entity tied = level.getEntity(cut.entity());
				yield tied != null && WebHitches.cut(level, tied, cut.b(), hit);
			}
			case BRIDGE -> WebBridges.cut(level, cut.a(), cut.b());
			default -> false;
		};
		if (done) {
			Vec3 at = hit;
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.05);
		}
	}

	/** What's in the punching hand (or on the puncher) sets a web line alight. */
	static boolean fiery(ServerPlayer player) {
		net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
		return player.isOnFire() || stack.is(net.minecraft.world.item.Items.TORCH) || stack.is(net.minecraft.world.item.Items.SOUL_TORCH)
			|| stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) || stack.is(net.minecraft.world.item.Items.FIRE_CHARGE)
			|| stack.is(net.minecraft.world.item.Items.BLAZE_ROD) || stack.is(net.minecraft.world.item.Items.LAVA_BUCKET)
			|| com.jaymospowers.core.wounds.Treatment.hot(stack, player.level()) || fireAspect(player, stack);
	}

	private static boolean fireAspect(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
		net.minecraft.world.item.enchantment.ItemEnchantments on = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
			net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
		return player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
			.get(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT).map(holder -> on.getLevel(holder) > 0).orElse(false);
	}

	/** The punched line is a symbiote's living cord (it recoils from fire rather than burning). */
	private static boolean dark(ServerLevel level, Cut cut) {
		return switch (cut.kind()) {
			case SWING -> level.getEntity(cut.entity()) instanceof ServerPlayer owner && WebSymbiote.suitShown(owner);
			case HITCH -> {
				Entity tied = level.getEntity(cut.entity());
				java.util.List<WebHitches.Line> lines = tied == null ? null : tied.getAttached(WebPower.HITCH);
				yield lines != null && lines.stream().filter(l -> l.anchor().distanceTo(cut.b()) < 2.5).anyMatch(WebHitches.Line::dark);
			}
			case BRIDGE -> {
				java.util.List<WebBridges.Span> spans = level.getAttached(WebBridges.SPANS);
				yield spans != null && spans.stream().anyMatch(sp -> sp.style() != WebBridgeBlock.WEB
					&& (sp.from().distanceTo(cut.a()) < 1.0 && sp.to().distanceTo(cut.b()) < 1.0 || sp.from().distanceTo(cut.b()) < 1.0 && sp.to().distanceTo(cut.a()) < 1.0));
			}
			default -> false;
		};
	}

	/** Counts a punch on the line: true once it has taken as many as it can (and then it's forgotten). */
	private static boolean giveWay(ServerLevel level, Cut cut) {
		long now = level.getGameTime();
		FRAYED.removeIf(f -> now - f.last > MEMORY_TICKS);
		Frayed line = null;
		for (Frayed f : FRAYED) {
			if (f.dimension == level.dimension() && f.kind == cut.kind() && f.entity == cut.entity() && f.at.distanceTo(cut.b()) < 2.5) {
				line = f;
				break;
			}
		}
		if (line == null) {
			line = new Frayed(level.dimension(), cut.kind(), cut.entity(), cut.b(), MIN_HITS + level.getRandom().nextInt(MAX_HITS - MIN_HITS + 1));
			FRAYED.add(line);
		}
		line.at = cut.b();
		line.last = now;
		if (++line.hits < line.needed) {
			return false;
		}
		FRAYED.remove(line);
		return true;
	}

	/** The point on segment a-b nearest to {@code p}. */
	public static Vec3 closestPoint(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		if (length < 1.0E-8) {
			return a;
		}
		double t = Math.clamp(p.subtract(a).dot(ab) / length, 0.0, 1.0);
		return a.add(ab.scale(t));
	}

	static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		return p.distanceTo(closestPoint(p, a, b));
	}
}
