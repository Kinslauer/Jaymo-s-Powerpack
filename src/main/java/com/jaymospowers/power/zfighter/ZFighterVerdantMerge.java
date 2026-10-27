package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.56, Andy's NOEA picks) Verdant merging: a Verdant Z Fighter joins with a Verdant ally for a permanent share of
 * what they have grown. Sneak+Z (on the ground, facing the partner within {@link #RANGE} blocks) begins a
 * {@link #CHANNEL_TICKS}-tick joining, broken if he moves, is hurt or the partner leaves. Who can be merged with:
 * <ul>
 * <li>a <b>Verdant player</b> (a Z Fighter): if they are on their feet, they must answer with sneak+Z facing him within
 * {@link #CONSENT_TICKS} ticks, then both stand still for the joining; if they are downed, no answer is needed and
 * they are brought round with {@link #RAISE_HEALTH} health. Both keep going, each given a {@link #PLAYER_SHARE} share of the
 * other's fists, health and ki;</li>
 * <li>a <b>Verdant villager</b>: it is consumed (it gives itself to him) for a small flat share ({@link #VILLAGER_FISTS},
 * {@link #VILLAGER_HEALTH}, {@link #VILLAGER_KI});</li>
 * <li>a <b>Verdant master</b> (a good-hearted pupil with {@link ZFighterMasterFusion#LESSONS_NEEDED} lessons): consumed for a
 * larger share ({@link #MASTER_FISTS}, {@link #MASTER_HEALTH}, {@link #MASTER_KI}); the masters' upkeep sends a new one.</li>
 * </ul>
 * Once per partner, ever (the partners' ids are kept); the totals never pass {@link #FISTS_CAP} fists, {@link #HEALTH_CAP}
 * health and {@link #KI_CAP} ki. Kept through death.
 */
public final class ZFighterVerdantMerge {
	static final double RANGE = 5.0;
	static final int CHANNEL_TICKS = 60;
	static final int CONSENT_TICKS = 200;
	/** A joining breaks if he strays this far (blocks) or the partner is this far (squared check uses the square). */
	static final double STRAY = 0.6;
	static final float RAISE_HEALTH = 8.0F;
	/** Between two players: the share of the partner's fists bonus, health above 20 (plus a flat point), and max ki. */
	static final double PLAYER_SHARE = 0.1;
	static final double PLAYER_HEALTH_SHARE = 0.2;
	static final double VILLAGER_FISTS = 0.4, VILLAGER_HEALTH = 2.0;
	static final int VILLAGER_KI = 10;
	static final double MASTER_FISTS = 1.0, MASTER_HEALTH = 4.0;
	static final int MASTER_KI = 25;
	static final double FISTS_CAP = 4.0, HEALTH_CAP = 16.0;
	static final int KI_CAP = 120;
	static final int COLOR = 0x6BFF8A;

	private static final Identifier FISTS_ID = JaymosPowers.id("zfighter_merge_fists");
	private static final Identifier HEALTH_ID = JaymosPowers.id("zfighter_merge_health");

	/** What he has taken in by merging, and with whom (ids; a villager's too). */
	public record Merged(double fists, double health, int ki, List<String> partners) {
		public static final Merged NONE = new Merged(0.0, 0.0, 0, List.of());
		public static final Codec<Merged> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("fists").forGetter(Merged::fists),
			Codec.DOUBLE.fieldOf("health").forGetter(Merged::health),
			Codec.INT.fieldOf("ki").forGetter(Merged::ki),
			Codec.STRING.listOf().fieldOf("partners").forGetter(Merged::partners)).apply(i, Merged::new));
	}

	/** Saved, kept through death. */
	public static final AttachmentType<Merged> MERGED = AttachmentRegistry.create(JaymosPowers.id("zfighter_merged"),
		builder -> builder.persistent(Merged.CODEC).copyOnDeath());

	/** A joining under way: with whom, when it began, and where he stood. */
	private record Channel(UUID partner, long since, Vec3 at, float health) {
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

	private ZFighterVerdantMerge() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterVerdantMerge::tick);
	}

	static void clear() {
		CHANNELS.clear();
	}

	static void forget(ServerPlayer player) {
		CHANNELS.remove(player.getUUID());
	}

	public static Merged of(Player player) {
		return player.getAttachedOrElse(MERGED, Merged.NONE);
	}

	/** The ki his merges add to his maximum. */
	public static int ki(Player player) {
		return of(player).ki();
	}

	/** Every second, per Z Fighter: the fists and health he has taken in. */
	static void attributes(ServerPlayer player) {
		Merged merged = of(player);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FISTS_ID, merged.fists(), AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, merged.health(), AttributeModifier.Operation.ADD_VALUE);
	}

	// ---------------------------------------------------------------- the key

	/** Sneak+Z: begin (or call off) a joining. Verdant Z Fighters only. */
	static void press(ServerPlayer player) {
		if (ZFighterLineage.of(player) != ZFighterLineage.VERDANT || !Powers.has(player, ZFighterPower.INSTANCE) || Powers.silenced(player)
			|| ZFighterLineage.ape(player) || Downed.isDowned(player)) {
			return;
		}
		UUID id = player.getUUID();
		if (CHANNELS.remove(id) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_stopped").withStyle(ChatFormatting.GRAY));
			return;
		}
		LivingEntity partner = find(player);
		if (partner == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_none").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (of(player).partners().contains(partner.getUUID().toString())) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_done", partner.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (partner.entityTags().contains(ZFighterMasters.TAG) && !(ZFighterGodRitual.goodHearted(player) && !ZFighterKingdoms.rogue(player)
			&& ZFighterGodRitual.lessons(player) >= ZFighterMasterFusion.LESSONS_NEEDED)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_master_no", partner.getDisplayName()).withStyle(ChatFormatting.AQUA));
			return;
		}
		long now = player.level().getGameTime();
		CHANNELS.put(id, new Channel(partner.getUUID(), now, player.position(), player.getHealth()));
		if (partner instanceof ServerPlayer other && !Downed.isDowned(other)) {
			Channel back = CHANNELS.get(other.getUUID());
			boolean answered = back != null && back.partner().equals(id);
			if (!answered) {
				other.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_offer", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_waiting", other.getDisplayName()).withStyle(ChatFormatting.GREEN));
				return;
			}
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_begin", partner.getDisplayName()).withStyle(ChatFormatting.GREEN));
	}

	/** The eligible partner nearest the middle of where he looks, within range. */
	private static LivingEntity find(ServerPlayer player) {
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestDot = 0.8;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e -> eligible(player, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(player.getEyePosition());
			double dot = to.lengthSqr() < 1.0E-4 ? 1.0 : to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = e;
			}
		}
		return best;
	}

	/** Whether {@code e} could be merged with at all (a Verdant Z Fighter player, villager or master, not himself). */
	private static boolean eligible(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || e.distanceToSqr(player) > RANGE * RANGE) {
			return false;
		}
		if (e instanceof ServerPlayer other) {
			return Powers.has(other, ZFighterPower.INSTANCE) && ZFighterLineage.of(other) == ZFighterLineage.VERDANT && !ZFighterLineage.ape(other);
		}
		if (e instanceof Player || e.isBaby() || ZFighterLineage.race(e) != ZFighterLineage.VERDANT) {
			return false;
		}
		return e instanceof Villager || e.entityTags().contains(ZFighterMasters.TAG);
	}

	// ---------------------------------------------------------------- the joining

	private static void tick(MinecraftServer server) {
		if (CHANNELS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Channel> entry : new ArrayList<>(CHANNELS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Channel channel = entry.getValue();
			if (CHANNELS.get(entry.getKey()) != channel) {
				// (finished or called off earlier this tick)
				continue;
			}
			if (player == null) {
				CHANNELS.remove(entry.getKey());
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			Entity found = level.getEntity(channel.partner());
			if (!player.isAlive() || Downed.isDowned(player) || player.position().distanceToSqr(channel.at()) > STRAY * STRAY
				|| player.getHealth() < channel.health() || !(found instanceof LivingEntity partner) || !eligible(player, partner)) {
				CHANNELS.remove(entry.getKey());
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_broken").withStyle(ChatFormatting.GRAY));
				continue;
			}
			boolean downed = partner instanceof ServerPlayer other && Downed.isDowned(other);
			if (partner instanceof ServerPlayer other && !downed) {
				Channel back = CHANNELS.get(other.getUUID());
				if (back == null || !back.partner().equals(player.getUUID())) {
					// the offer lapses unanswered
					if (now - channel.since() > CONSENT_TICKS) {
						CHANNELS.remove(entry.getKey());
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_refused", other.getDisplayName()).withStyle(ChatFormatting.GRAY));
					}
					continue;
				}
				if (now - Math.max(channel.since(), back.since()) < CHANNEL_TICKS) {
					swirl(level, player, partner, now);
					continue;
				}
				// both stood through it: the lower id does the joining, once for both
				if (player.getUUID().compareTo(other.getUUID()) < 0) {
					CHANNELS.remove(other.getUUID());
					CHANNELS.remove(entry.getKey());
					joinPlayers(player, other);
				}
				continue;
			}
			if (now - channel.since() < CHANNEL_TICKS) {
				swirl(level, player, partner, now);
				continue;
			}
			CHANNELS.remove(entry.getKey());
			if (partner instanceof ServerPlayer other) {
				Downed.rouse(level, other, RAISE_HEALTH);
				joinPlayers(player, other);
			} else {
				absorb(player, partner);
			}
		}
	}

	private static void swirl(ServerLevel level, ServerPlayer player, LivingEntity partner, long now) {
		if (now % 3 != 0) {
			return;
		}
		Vec3 a = player.position().add(0.0, 1.0, 0.0), b = partner.position().add(0.0, 1.0, 0.0);
		Vec3 mid = a.add(b).scale(0.5);
		level.sendParticles(new DustParticleOptions(COLOR, 1.2F), true, false, mid.x, mid.y, mid.z, 5, 0.4, 0.5, 0.4, 0.02);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, false, mid.x, mid.y, mid.z, 2, 0.5, 0.5, 0.5, 0.0);
	}

	/** Two players, each given a share of the other's. Each is recorded as the other's partner, so it is never done twice. */
	private static void joinPlayers(ServerPlayer a, ServerPlayer b) {
		double fistsA = PLAYER_SHARE * ZFighterPower.fistBonus(b), fistsB = PLAYER_SHARE * ZFighterPower.fistBonus(a);
		double healthA = 1.0 + PLAYER_HEALTH_SHARE * Math.max(0.0, b.getMaxHealth() - 20.0), healthB = 1.0 + PLAYER_HEALTH_SHARE * Math.max(0.0, a.getMaxHealth() - 20.0);
		int kiA = (int)(PLAYER_SHARE * ZFighterPower.maxKi(b)), kiB = (int)(PLAYER_SHARE * ZFighterPower.maxKi(a));
		give(a, b.getUUID(), fistsA, healthA, kiA);
		give(b, a.getUUID(), fistsB, healthB, kiB);
		burst(a, b);
	}

	/** A villager or master, consumed. */
	private static void absorb(ServerPlayer player, LivingEntity partner) {
		boolean master = partner.entityTags().contains(ZFighterMasters.TAG);
		give(player, partner.getUUID(), master ? MASTER_FISTS : VILLAGER_FISTS, master ? MASTER_HEALTH : VILLAGER_HEALTH, master ? MASTER_KI : VILLAGER_KI);
		burst(player, partner);
		partner.discard();
	}

	private static void give(ServerPlayer player, UUID partner, double fists, double health, int ki) {
		Merged was = of(player);
		List<String> partners = new ArrayList<>(was.partners());
		partners.add(partner.toString());
		double newHealth = Math.min(HEALTH_CAP, was.health() + health);
		player.setAttached(MERGED, new Merged(Math.min(FISTS_CAP, was.fists() + fists), newHealth, Math.min(KI_CAP, was.ki() + ki), partners));
		attributes(player);
		player.heal((float)(newHealth - was.health()));
		ZFighterPower.refresh(player, ZFighterPower.live(player));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_merge_joined").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
	}

	private static void burst(Entity a, Entity b) {
		ServerLevel level = (ServerLevel)a.level();
		Vec3 mid = a.position().add(b.position()).scale(0.5).add(0.0, 1.0, 0.0);
		level.sendParticles(new DustParticleOptions(COLOR, 2.0F), true, false, mid.x, mid.y, mid.z, 50, 0.7, 0.9, 0.7, 0.2);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, false, mid.x, mid.y, mid.z, 20, 0.7, 0.9, 0.7, 0.0);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.3F);
		if (a instanceof ServerPlayer player) {
			Shakes.send(player, 0.5F);
		}
		if (b instanceof ServerPlayer player) {
			Shakes.send(player, 0.5F);
		}
	}
}
