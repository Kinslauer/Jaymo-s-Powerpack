package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Senses;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.383) The knife family's forms ({@code light_blades}); a Thief does each of them better.
 * <ul>
 * <li>Marked Quarry: mark the foe you are looking at (within {@value #MARK_RANGE} blocks, through walls): its outline shows to
 * you alone, through walls, for {@value #MARK_TICKS} ticks ({@link Mark}, drawn by the client). Press again within that time to
 * dash behind it and stab; the stab is half again as hard on a foe that is not after you, and a Thief's is
 * {@value #THIEF_BONUS} times as hard.
 * <li>Cast-off Cloak: a straw figure in your look is left where you stood, and hostile mobs after you swing at it for
 * {@value #CLOAK_TICKS} ticks while you slip away, quicker; then it vanishes. A Thief's lasts longer.
 * <li>Tangle Cord: throws a pair of {@link Bolas} from your pack that wraps the first foe's legs.
 * </ul>
 */
public final class KnifeForms {
	static final Form MARKED_QUARRY = new Form("marked_quarry", Family.KNIFE, Kind.ART, 30, 4, 1.5F, 6);
	static final Form CAST_OFF_CLOAK = new Form("cast_off_cloak", Family.KNIFE, Kind.ART, 300, 2, 1.0F, 6);
	static final Form TANGLE_CORD = new Form("tangle_cord", Family.KNIFE, Kind.ART, 100, 3, 1.0F, 6);

	/** The tag of the straw figure, so a leftover one is swept away and nobody practises on it. */
	static final String DECOY_TAG = "jaymoskingdoms_straw_decoy";

	/** Marked Quarry: how far the mark reaches (blocks) and how true the look must be (cosine), how long it lasts (ticks), the wait after a dash before a new mark (ticks). */
	static final double MARK_RANGE = 24.0, MARK_FACING = 0.97;
	static final int MARK_TICKS = 100, MARK_LOCKOUT = 100;
	/** The dash: the farthest it goes (blocks), how far behind the foe you land, what the stab deals (times your attack), the bonus on an unaware foe, a Thief's bonus. */
	static final double DASH_MAX = 14.0, BEHIND = 1.3;
	static final float STAB_DAMAGE = 1.3F, UNAWARE_BONUS = 1.5F, THIEF_BONUS = 1.3F;
	/** Cast-off Cloak: how long mobs are lured (ticks; a Thief's longer), how far, how often they are told (ticks), the haste you slip away with (ticks, level). */
	static final int CLOAK_TICKS = 60, CLOAK_THIEF_TICKS = 100, LURE_EVERY = 5, SLIP_TICKS = 50, SLIP_LEVEL = 1;
	static final double LURE_RANGE = 16.0;
	/** Tangle Cord: how fast the bolas fly (blocks a tick); a Thief's tangle is this many ranks better. */
	static final float CORD_SPEED = 1.5F;
	static final int THIEF_CORD_RANKS = 1;

	/** Server → player: outline {@code entity} for {@code ticks} (0 to stop). Drawn only for them. */
	public record Mark(int entity, int ticks) implements CustomPacketPayload {
		public static final Type<Mark> TYPE = new Type<>(JaymosKingdoms.id("form_mark"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Mark> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				buf.writeVarInt(p.ticks);
			}, buf -> new Mark(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Mark> type() {
			return TYPE;
		}
	}

	private record Marked(UUID foe, int entityId, long until) {
	}

	private record Decoy(ServerLevel level, UUID figure, long until) {
	}

	private static final Map<UUID, Marked> MARKS = new HashMap<>();
	private static final Map<UUID, Long> LOCKOUT = new HashMap<>();
	private static final Map<UUID, Decoy> DECOYS = new HashMap<>();

	/** The mannequin's skin, which only a mannequin's own code may set: read through a subclass that's never made. */
	private abstract static class Profile extends Mannequin {
		private Profile(Level level) {
			super(level);
		}

		static net.minecraft.network.syncher.EntityDataAccessor<ResolvableProfile> key() {
			return DATA_PROFILE;
		}
	}

	private KnifeForms() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(MARKED_QUARRY.id(), MARKED_QUARRY);
		forms.put(CAST_OFF_CLOAK.id(), CAST_OFF_CLOAK);
		forms.put(TANGLE_CORD.id(), TANGLE_CORD);
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Mark.TYPE, Mark.CODEC);
		Bolas.register();
		ServerTickEvents.END_SERVER_TICK.register(KnifeForms::tick);
		// the straw figure takes no harm, so mobs keep swinging at it for its whole time
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Mannequin && entity.entityTags().contains(DECOY_TAG)));
		// a figure left over from a restart has no owner to follow: it goes
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(DECOY_TAG) && !isFigure(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MARKS.remove(handler.player.getUUID());
			LOCKOUT.remove(handler.player.getUUID());
			Decoy decoy = DECOYS.remove(handler.player.getUUID());
			if (decoy != null) {
				vanish(decoy);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Decoy decoy : DECOYS.values()) {
				vanish(decoy);
			}
			DECOYS.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MARKS.clear();
			LOCKOUT.clear();
			DECOYS.clear();
		});
	}

	static void perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (form == MARKED_QUARRY) {
			quarry(level, player, form, rank);
		} else if (form == CAST_OFF_CLOAK) {
			cloak(level, player);
		} else if (form == TANGLE_CORD) {
			cord(level, player, rank);
		}
	}

	private static boolean thief(ServerPlayer player) {
		return Classes.is(player, Classes.PlayerClass.THIEF);
	}

	// ---- Marked Quarry -------------------------------------------------------------------------------------------------

	private static void quarry(ServerLevel level, ServerPlayer player, Form form, int rank) {
		long now = level.getGameTime();
		Marked marked = MARKS.get(player.getUUID());
		if (marked != null && marked.until() > now && level.getEntity(marked.foe()) instanceof LivingEntity foe && foe.isAlive()) {
			dash(level, player, foe, marked, rank);
			return;
		}
		MARKS.remove(player.getUUID());
		if (LOCKOUT.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		LivingEntity quarry = lookedAt(level, player);
		if (quarry == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.marked_quarry.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		MARKS.put(player.getUUID(), new Marked(quarry.getUUID(), quarry.getId(), now + MARK_TICKS));
		if (ServerPlayNetworking.canSend(player, Mark.TYPE)) {
			ServerPlayNetworking.send(player, new Mark(quarry.getId(), MARK_TICKS));
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.marked_quarry.marked", quarry.getDisplayName()).withStyle(ChatFormatting.GOLD));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.5F, 1.9F);
	}

	/** The living thing most nearly dead ahead of the crosshair within {@link #MARK_RANGE}, walls or no. */
	private static @Nullable LivingEntity lookedAt(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = MARK_FACING;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MARK_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof Player) && !(e instanceof Mannequin)
				&& (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(player.getEyePosition());
			double d = to.length();
			if (d < 1.0E-3 || d > MARK_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot) {
				best = e;
				bestDot = dot;
			}
		}
		return best;
	}

	/** The second press: behind the quarry in a moment, and the stab. */
	private static void dash(ServerLevel level, ServerPlayer player, LivingEntity foe, Marked marked, int rank) {
		if (player.distanceTo(foe) > DASH_MAX) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.marked_quarry.far").withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 face = Vec3.directionFromRotation(0.0F, foe.getYRot());
		Vec3 side = new Vec3(-face.z, 0.0, face.x);
		Vec3 start = player.position();
		Vec3 dest = null;
		// straight behind, then a little to either side, then close in
		Vec3[] tries = {
			foe.position().subtract(face.scale(BEHIND)), foe.position().subtract(face.scale(BEHIND)).add(side.scale(0.9)),
			foe.position().subtract(face.scale(BEHIND)).subtract(side.scale(0.9)), foe.position().subtract(face.scale(0.8))};
		for (Vec3 candidate : tries) {
			if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))) {
				dest = candidate;
				break;
			}
		}
		if (dest == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.marked_quarry.blocked").withStyle(ChatFormatting.GRAY));
			return;
		}
		MARKS.remove(player.getUUID());
		LOCKOUT.put(player.getUUID(), level.getGameTime() + MARK_LOCKOUT);
		if (ServerPlayNetworking.canSend(player, Mark.TYPE)) {
			ServerPlayNetworking.send(player, new Mark(marked.entityId(), 0));
		}
		for (double d = 0.0; d < start.distanceTo(dest); d += 0.8) {
			Vec3 at = start.add(dest.subtract(start).normalize().scale(d));
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.5, at.z, 2, 0.15, 0.3, 0.15, 0.01);
		}
		float yaw = Mth.wrapDegrees((float)(Mth.atan2(foe.getZ() - dest.z, foe.getX() - dest.x) * 57.2957763671875) - 90.0F);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, 0.0F, false);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.7F);
		float amount = Forms.damage(player, STAB_DAMAGE, rank);
		if (Senses.unaware(foe, player)) {
			amount *= UNAWARE_BONUS;
			level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.6), foe.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		}
		if (thief(player)) {
			amount *= THIEF_BONUS;
		}
		CombatArts.strike(level, player, foe, amount);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY(0.5), foe.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		Forms.landed(player, MARKED_QUARRY, List.of(foe));
	}

	// ---- Cast-off Cloak ------------------------------------------------------------------------------------------------

	private static void cloak(ServerLevel level, ServerPlayer player) {
		Decoy old = DECOYS.remove(player.getUUID());
		if (old != null) {
			vanish(old);
		}
		Mannequin figure = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (figure != null) {
			// a straw figure in your look
			figure.getEntityData().set(Profile.key(), ResolvableProfile.createResolved(player.getGameProfile()));
			figure.addTag(DECOY_TAG);
			// (not set invulnerable: mobs never take an invulnerable thing for an enemy; its hurt is cancelled instead)
			figure.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
			figure.setYHeadRot(player.getYHeadRot());
			figure.setYBodyRot(player.yBodyRot);
			int ticks = thief(player) ? CLOAK_THIEF_TICKS : CLOAK_TICKS;
			// (known before it is added: the load sweep would otherwise take it for a leftover)
			Decoy decoy = new Decoy(level, figure.getUUID(), level.getGameTime() + ticks);
			DECOYS.put(player.getUUID(), decoy);
			if (level.addFreshEntity(figure)) {
				lure(player, level, figure);
			} else {
				DECOYS.remove(player.getUUID());
			}
		}
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(0.5), player.getZ(), 14, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, SLIP_TICKS, SLIP_LEVEL, false, false));
	}

	/** Every hostile mob after {@code owner} (or loose and near) turns on the figure. */
	private static void lure(ServerPlayer owner, ServerLevel level, LivingEntity figure) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, figure.getBoundingBox().inflate(LURE_RANGE),
			m -> m.isAlive() && (m.getTarget() == owner || m instanceof Enemy && m.getTarget() == null && m.distanceToSqr(figure) < 100.0))) {
			mob.setTarget(figure);
		}
	}

	private static boolean isFigure(UUID id) {
		for (Decoy decoy : DECOYS.values()) {
			if (decoy.figure().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** The figure goes in a puff of straw. */
	private static void vanish(Decoy decoy) {
		Entity figure = decoy.level().getEntity(decoy.figure());
		if (figure != null) {
			decoy.level().sendParticles(ParticleTypes.POOF, figure.getX(), figure.getY(1.0), figure.getZ(), 12, 0.3, 0.6, 0.3, 0.03);
			figure.discard();
		}
	}

	// ---- Tangle Cord ---------------------------------------------------------------------------------------------------

	private static void cord(ServerLevel level, ServerPlayer player, int rank) {
		ItemStack stack = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Bolas.ITEM)) {
				stack = player.getInventory().getItem(i);
				break;
			}
		}
		if (stack.isEmpty() && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.tangle_cord.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		Bolas.throwFrom(level, player, player.getLookAngle().scale(CORD_SPEED).add(0.0, Bolas.THROW_LOFT, 0.0), rank + (thief(player) ? THIEF_CORD_RANKS : 0), true);
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (MARKS.isEmpty() && DECOYS.isEmpty() && LOCKOUT.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Marked>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Marked> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Marked marked = entry.getValue();
			if (player == null || !player.isAlive() || player.level().getGameTime() >= marked.until()
				|| !(player.level().getEntity(marked.foe()) instanceof LivingEntity foe) || !foe.isAlive()) {
				it.remove();
				if (player != null && ServerPlayNetworking.canSend(player, Mark.TYPE)) {
					ServerPlayNetworking.send(player, new Mark(marked.entityId(), 0));
				}
			}
		}
		long clock = server.overworld().getGameTime();
		LOCKOUT.values().removeIf(until -> clock >= until);
		for (Iterator<Map.Entry<UUID, Decoy>> it = DECOYS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Decoy> entry = it.next();
			Decoy decoy = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			Entity figure = decoy.level().getEntity(decoy.figure());
			if (!(figure instanceof Mannequin mannequin) || !mannequin.isAlive()) {
				it.remove();
				continue;
			}
			long now = decoy.level().getGameTime();
			if (owner == null || !owner.isAlive() || owner.level() != decoy.level() || now >= decoy.until()) {
				// it fades out on its own
				vanish(decoy);
				it.remove();
				continue;
			}
			if (now % LURE_EVERY == 0) {
				lure(owner, decoy.level(), mannequin);
			}
		}
	}
}
