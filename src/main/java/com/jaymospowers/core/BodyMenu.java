package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Fainting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-20, hogtying with the lasso): "there needs to be a context menu on right click for lifting, pickpocketing,
 * reviving, untying. I had way too many prompts come up just now". A tap of use (standing, let go at once) on a body, someone
 * downed, hogtied or out cold in a faint, opens a small menu by the crosshair ({@code client.BodyMenuScreen}) of only what can
 * be done to them now: revive, untie or cut free, drag, lift, pick their pockets, take their weapons, pull out a blade, shake
 * them awake. A hold of use is still the drag, and sneak + a hold still the revive; only the tap changed (it used to take
 * their weapons, and a plain hold on someone tied untied them, so the binds' bars came up over each other).
 * <p>
 * What's chosen is done from there with no button held ({@link Task}): a timed one (a revive, untying, a pocket) fills its own
 * bar as its hold would, and stops, the bar kept where it got to, when you step away ({@link #STEP_OFF}), they're out of reach,
 * or you open the menu again. Each option is an {@link Option}: when it's offered, and one tick of doing it. Powers adds its
 * own; Jaymo's Kingdoms adds its own through {@link #SHARE} in Fabric Loader's object share (it doesn't depend on this mod):
 * an {@code Object[]} of {id, order, offered, step}, the two last {@code BiPredicate<ServerPlayer, LivingEntity>}. An
 * option's label is the lang key {@code body_menu.<namespace>.<path>} with the body's name.
 * <p>
 * Only a click nobody else took opens it ({@link #PHASE}, after every other mod's use and before Downed's catch-all): medicine,
 * a limb to fit, a gag, a lasso's haul or a carry's drop keep their own clicks.
 */
public final class BodyMenu {
	// ===== the knobs ================================================================================================
	/** A press let go within this many client ticks is a tap (the client's {@code BodyMenuClient} times it). */
	public static final int TAP_TICKS = 5;
	/** The server's own record of an untaken click on a body is good this long (ticks) for a tap to open the menu. */
	static final int REACHED_TICKS = 20;
	/** Walk this far (blocks) from where you chose and what you're doing stops; and the body must stay this much past reach. */
	static final double STEP_OFF = 1.25, REACH_SLACK = 1.0;
	// ================================================================================================================

	/** Where Kingdoms (and anyone else) adds its options: an {@code Object[]} of {id, order, offered, step}. */
	public static final String SHARE = "jaymos:body_menu";
	/** After every other mod's use on an entity, before Downed's catch-all (which swallows the rest). */
	static final Identifier PHASE = JaymosPowers.id("body_menu");

	/**
	 * One thing to do to a body. {@code offered}: whether it's on the menu for this player and body now. {@code step}: one
	 * tick of doing it, true while it goes on (a timed one), false once done or given up (an instant one does it and says false).
	 */
	public record Option(String id, int order, BiPredicate<ServerPlayer, LivingEntity> offered, BiPredicate<ServerPlayer, LivingEntity> step) {
		Component label(LivingEntity body) {
			Identifier key = Identifier.parse(id);
			return Component.translatable("body_menu." + key.getNamespace() + "." + key.getPath(), body.getDisplayName());
		}
	}

	/** Client → server: use was tapped on {@code entity}. */
	public record Tap(int entity) implements CustomPacketPayload {
		public static final Type<Tap> TYPE = new Type<>(JaymosPowers.id("body_menu_tap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tap> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Tap::entity, Tap::new);

		@Override
		public Type<Tap> type() {
			return TYPE;
		}
	}

	/** One row of the menu as sent: the option's id and its label. */
	public record Row(String id, Component label) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Row::id,
			ComponentSerialization.STREAM_CODEC, Row::label, Row::new);
	}

	/** Server → client: the menu for {@code entity}, titled with its name. */
	public record Menu(int entity, Component title, List<Row> rows) implements CustomPacketPayload {
		public static final Type<Menu> TYPE = new Type<>(JaymosPowers.id("body_menu"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Menu> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Menu::entity,
			ComponentSerialization.STREAM_CODEC, Menu::title, Row.CODEC.apply(ByteBufCodecs.list(16)), Menu::rows, Menu::new);

		@Override
		public Type<Menu> type() {
			return TYPE;
		}
	}

	/** Client → server: {@code id} chosen from the menu on {@code entity}. */
	public record Choose(int entity, String id) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosPowers.id("body_menu_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Choose::entity,
			ByteBufCodecs.STRING_UTF8, Choose::id, Choose::new);

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	/** Something chosen, being done: to whom, which option, and where the doer stood. */
	private record Task(LivingEntity body, Option option, Vec3 at) {
	}

	private static final List<Option> OPTIONS = new ArrayList<>();
	/** Player → what they're doing from the menu. */
	private static final Map<UUID, Task> TASKS = new HashMap<>();
	/** Player → the body their last click nobody else took landed on, and when (game time). */
	private record Reached(int entity, long at) {
	}

	private static final Map<UUID, Reached> REACHED = new HashMap<>();

	/** The client's side ({@code BodyMenuClient}): told of each untaken click on a body, on the client. */
	public static @Nullable Consumer<Entity> clientPress;

	private BodyMenu() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Tap.TYPE, Tap.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Menu.TYPE, Menu.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Tap.TYPE, (payload, ctx) -> tapped(ctx.player(), payload.entity()));
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, ctx) -> chosen(ctx.player(), payload.entity(), payload.id()));
		UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		UseEntityCallback.EVENT.addPhaseOrdering(PHASE, Downed.lastPhase());
		UseEntityCallback.EVENT.register(PHASE, BodyMenu::onUse);
		ServerTickEvents.END_SERVER_TICK.register(BodyMenu::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TASKS.clear();
			REACHED.clear();
		});
		FabricLoaderShare.put();
		// Powers' own: bring them round, drag them, lift them, shake a faint awake, pull out a blade stuck in them
		add("jaymospowers:revive", 10, (player, body) -> Downed.isDowned(body), Downed::reviveChosen);
		add("jaymospowers:shake", 12, (player, body) -> body instanceof ServerPlayer lying && Fainting.out(lying),
			(player, body) -> {
				Fainting.shakeAwake(player, (ServerPlayer) body);
				return false;
			});
		add("jaymospowers:drag", 30, Carry::offersDrag, (player, body) -> {
			if (!Carry.liftDowned(player, body)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.cant_take_hold", body.getDisplayName())
					.withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			return false;
		});
		add("jaymospowers:lift", 40, Carry::offersLift, (player, body) -> {
			Carry.liftChosen(player, body);
			return false;
		});
		add("jaymospowers:pull_blade", 70, (player, body) -> player.getMainHandItem().isEmpty() && !com.jaymospowers.armory.Lodged.stuck(body).isEmpty(),
			(player, body) -> {
				com.jaymospowers.armory.Lodged.pullOnTap(player.level(), player, body);
				return false;
			});
	}

	/** Adds an option to every body's menu. */
	public static void add(String id, int order, BiPredicate<ServerPlayer, LivingEntity> offered, BiPredicate<ServerPlayer, LivingEntity> step) {
		OPTIONS.add(new Option(id, order, offered, step));
		OPTIONS.sort(Comparator.comparingInt(Option::order));
	}

	/** (kept apart so the object share's raw types stay out of the rest) */
	private static final class FabricLoaderShare {
		@SuppressWarnings("unchecked")
		static void put() {
			net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().put(SHARE, (Consumer<Object[]>) o -> add((String) o[0], (Integer) o[1],
				(BiPredicate<ServerPlayer, LivingEntity>) o[2], (BiPredicate<ServerPlayer, LivingEntity>) o[3]));
		}
	}

	/** A body the menu is for, both sides: alive and downed, tied hand and foot, or a player out cold in a faint. */
	public static boolean body(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
			&& (Downed.isDowned(living) || Carry.bound(living) || living instanceof Player player && Fainting.out(player));
	}

	/** A use on an entity nobody else took. On a body, standing, it's swallowed and noted: a tap of it opens the menu. */
	private static InteractionResult onUse(Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator() || entity == player || !body(entity)
			|| Downed.isDowned(player) || Carry.isCarried(player)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			if (clientPress != null) {
				clientPress.accept(entity);
			}
		} else {
			REACHED.put(player.getUUID(), new Reached(entity.getId(), level.getGameTime()));
		}
		return InteractionResult.SUCCESS;
	}

	/** What {@code player} can do to {@code body} now. */
	static List<Option> offered(ServerPlayer player, LivingEntity body) {
		List<Option> list = new ArrayList<>();
		for (Option option : OPTIONS) {
			try {
				if (option.offered().test(player, body)) {
					list.add(option);
				}
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.warn("Body menu option {} failed to say if it's offered", option.id(), e);
			}
		}
		return list;
	}

	private static @Nullable LivingEntity within(ServerPlayer player, int entityId) {
		if (!(player.level().getEntity(entityId) instanceof LivingEntity body) || !body(body) || !player.isAlive() || Downed.isDowned(player)
			|| !player.isWithinEntityInteractionRange(body, REACH_SLACK)) {
			return null;
		}
		return body;
	}

	/** A tap: if the click that came with it was one nobody else took, the menu opens on what can be done. */
	private static void tapped(ServerPlayer player, int entityId) {
		Reached reached = REACHED.remove(player.getUUID());
		LivingEntity body = within(player, entityId);
		if (body == null || reached == null || reached.entity() != entityId || player.level().getGameTime() - reached.at() > REACHED_TICKS) {
			return;
		}
		// (a new menu stops what the last one began)
		TASKS.remove(player.getUUID());
		List<Option> options = offered(player, body);
		if (options.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.body_menu.nothing", body.getDisplayName())
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			return;
		}
		List<Row> rows = new ArrayList<>();
		for (Option option : options) {
			rows.add(new Row(option.id(), option.label(body)));
		}
		ServerPlayNetworking.send(player, new Menu(entityId, body.getDisplayName(), rows));
	}

	/** {@code id} chosen on {@code entityId}: begun, if it's still on offer. */
	private static void chosen(ServerPlayer player, int entityId, String id) {
		LivingEntity body = within(player, entityId);
		if (body == null) {
			return;
		}
		for (Option option : offered(player, body)) {
			if (option.id().equals(id)) {
				Task task = new Task(body, option, player.position());
				TASKS.remove(player.getUUID());
				if (step(player, task)) {
					TASKS.put(player.getUUID(), task);
				}
				return;
			}
		}
	}

	private static boolean step(ServerPlayer player, Task task) {
		try {
			return task.option().step().test(player, task.body());
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Body menu option {} failed", task.option().id(), e);
			return false;
		}
	}

	private static void tick(MinecraftServer server) {
		if (!REACHED.isEmpty() && server.getTickCount() % 100 == 0) {
			REACHED.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey()) == null);
		}
		if (TASKS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Task>> it = TASKS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Task task = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			LivingEntity body = task.body();
			if (player == null || !(player.level() instanceof ServerLevel level) || !player.isAlive() || Downed.isDowned(player) || body.isRemoved()
				|| !body.isAlive() || body.level() != level || player.position().distanceTo(task.at()) > STEP_OFF
				|| !player.isWithinEntityInteractionRange(body, REACH_SLACK) || !step(player, task)) {
				// stepped away, out of reach, or done: whatever hold it fed lets go by itself (and keeps its bar)
				it.remove();
			}
		}
	}

	/** Whether {@code player} is doing something to {@code body} from the menu now. */
	public static boolean busyWith(Player player, Entity body) {
		Task task = TASKS.get(player.getUUID());
		return task != null && task.body() == body;
	}
}
