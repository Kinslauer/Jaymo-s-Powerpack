package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Tendril stilts (Legends patch 4, {@code sy_stilts}): double-tap Jump with the tendrils out and you rise up on two
 * tendrils from your back and stride on them; hold Jump to go higher, Sneak to come down (Sneak once you are down puts
 * them away). The body really is taller (a Scale modifier, with a longer step and no jumping while up), so doors, ceilings
 * and reach all follow; it will not rise where the head would not fit. {@link #LEVEL} is how high, in thousandths of the
 * full height, synced to everyone, who see the legs hang and the tendrils plant (client {@code WebTendrilWalk},
 * {@code WebSuitPoses}). Standing on them wears the symbiote a little. Ends on death, downed, disconnect, a change of
 * world, power loss, the tendrils going in, or flight.
 */
public final class WebStilts {
	/** How high you are, in thousandths of the full rise (none for off). */
	public static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(JaymosPowers.id("web_stilts"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** The full rise, as a share of your height added (1.0 doubles it), the first rest height, how fast it climbs / settles / lowers (per tick). */
	public static final float FULL = 0.9F, REST = 0.45F, CLIMB = 0.02F, SETTLE = 0.03F, LOWER = 0.04F;
	/** The longer step at full height (blocks), the longer fall you can take, and the satiety it costs every this many ticks. */
	static final double STEP_BONUS = 1.4, SAFE_FALL_BONUS = 2.5;
	static final int TIRE_TICKS = 100;

	private static final Identifier SCALE_ID = JaymosPowers.id("web_stilts_scale");
	private static final Identifier STEP_ID = JaymosPowers.id("web_stilts_step");
	private static final Identifier JUMP_ID = JaymosPowers.id("web_stilts_jump");
	private static final Identifier FALL_ID = JaymosPowers.id("web_stilts_fall");

	/** What the client holds: {@code toggle} (the double tap), Jump held, Sneak held. */
	public record Input(boolean toggle, boolean up, boolean down) implements CustomPacketPayload {
		public static final Type<Input> TYPE = new Type<>(JaymosPowers.id("web_stilts_input"));
		public static final StreamCodec<ByteBuf, Input> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Input::toggle,
			ByteBufCodecs.BOOL, Input::up, ByteBufCodecs.BOOL, Input::down, Input::new);

		@Override
		public Type<Input> type() {
			return TYPE;
		}
	}

	private static final class Live {
		float height;
		boolean up, down;
		boolean downReleased;
		long since;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** The last game time each web slinger was seen on the ground: the double tap's first tap (the jump) must have left it just now. */
	private static final Map<UUID, Long> GROUNDED = new HashMap<>();
	static final int GROUND_GRACE = 14;

	private WebStilts() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Input.TYPE, (payload, ctx) -> input(ctx.player(), payload));
	}

	/** Whether the stilts are up under this player (either side). */
	public static boolean up(Player player) {
		Integer level = player.getAttached(LEVEL);
		return level != null && level > 0;
	}

	/** How high (0-1) the stilts are (either side). */
	public static float height(Player player) {
		Integer level = player.getAttached(LEVEL);
		return level == null ? 0.0F : level / 1000.0F;
	}

	static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !Downed.isDowned(player) && Powers.has(player, WebPower.INSTANCE)
			&& !Powers.silenced(player) && WebSymbiote.isBonded(player) && WebSymbiote.tendrilKeys(player) && !WebSymbiote.bulked(player)
			&& !WebSymbiote.driving(player) && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()
			&& !WebHeldGrab.holding(player) && !WebLatch.latched(player);
	}

	private static void input(ServerPlayer player, Input in) {
		Live live = LIVE.get(player.getUUID());
		if (live != null) {
			live.up = in.up();
			live.down = in.down();
			if (!in.down()) {
				live.downReleased = true;
			}
		}
		if (!in.toggle()) {
			return;
		}
		if (live != null) {
			stop(player);
		} else if (able(player) && player.level().getGameTime() - GROUNDED.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) <= GROUND_GRACE
			&& !WebGrind.active(player)) {
			Live fresh = new Live();
			fresh.since = player.level().getGameTime();
			fresh.up = in.up();
			LIVE.put(player.getUUID(), fresh);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.9F, 0.5F);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		if (player.onGround()) {
			GROUNDED.put(player.getUUID(), player.level().getGameTime());
		}
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		if (!able(player) || WebGrind.active(player)) {
			stop(player);
			return;
		}
		long now = player.level().getGameTime();
		float want = live.height;
		if (live.up) {
			want = live.height + CLIMB;
		} else if (live.down) {
			want = live.height - LOWER;
		} else if (live.height < REST && now - live.since < 60) {
			want = Math.min(REST, live.height + SETTLE);
		}
		if (live.down && live.height <= 0.02F && live.downReleased && now - live.since > 10) {
			// sneaking at the ground puts them away
			stop(player);
			return;
		}
		want = Mth.clamp(want, 0.0F, 1.0F);
		if (want > live.height && !room(player, want)) {
			// no room overhead: stay as high as it fits
			want = live.height;
		}
		live.height = want;
		apply(player, want);
		int level = Math.round(want * 1000.0F);
		if (level <= 0) {
			level = 1;
		}
		if (!Integer.valueOf(level).equals(player.getAttached(LEVEL))) {
			player.setAttached(LEVEL, level);
		}
		if (player.tickCount % TIRE_TICKS == 0 && !player.isCreative()) {
			WebSymbioteState state = WebSymbiote.state(player);
			if (state.satiety() > 0) {
				WebSymbiote.set(player, state.withSatiety(state.satiety() - 1));
			} else {
				stop(player);
			}
		}
	}

	/** Whether the head would fit at {@code height}. */
	private static boolean room(ServerPlayer player, float height) {
		double scale = 1.0 + FULL * height;
		double now = player.getScale();
		double extra = player.getBbHeight() / now * (scale - now);
		return player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, extra + 0.1, 0.0));
	}

	private static void apply(ServerPlayer player, float height) {
		modifier(player, Attributes.SCALE, SCALE_ID, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, FULL * height);
		modifier(player, Attributes.STEP_HEIGHT, STEP_ID, AttributeModifier.Operation.ADD_VALUE, STEP_BONUS * height);
		modifier(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, AttributeModifier.Operation.ADD_VALUE, SAFE_FALL_BONUS * height);
		// up on tendrils there is no jump: Jump is the rise
		modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, height > 0.0F ? -1.0 : 0.0);
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, AttributeModifier.Operation operation, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier existing = instance.getModifier(id);
		if (amount == 0.0) {
			if (existing != null) {
				instance.removeModifier(id);
			}
		} else if (existing == null || existing.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Down they come: the modifiers go, the tendrils withdraw. */
	static void stop(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		apply(player, 0.0F);
		player.removeAttached(LEVEL);
	}

	static void forget(ServerPlayer player) {
		GROUNDED.remove(player.getUUID());
		stop(player);
	}

	static void clearAll() {
		LIVE.clear();
		GROUNDED.clear();
	}
}
