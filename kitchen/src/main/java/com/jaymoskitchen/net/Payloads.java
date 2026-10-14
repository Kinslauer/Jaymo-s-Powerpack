package com.jaymoskitchen.net;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Every packet the cooking minigame sends. The server owns the session; the client only presses buttons. */
public final class Payloads {
	private Payloads() {
	}

	/** Server → client: open the kitchen screen for the station at {@code pos}. */
	/** {@code stocked}: bit i set when the station's i-th dish can be made from the pockets and nearby storage. */
	public record OpenKitchen(BlockPos pos, int station, int stocked) implements CustomPacketPayload {
		public static final Type<OpenKitchen> TYPE = new Type<>(JaymosKitchen.id("open_kitchen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenKitchen> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.station);
				buf.writeInt(p.stocked);
			},
			buf -> new OpenKitchen(buf.readBlockPos(), buf.readVarInt(), buf.readInt()));

		@Override
		public Type<OpenKitchen> type() {
			return TYPE;
		}
	}

	/** Client → server: start cooking {@code dish} at the station. */
	public record StartCooking(BlockPos pos, int dish, int station) implements CustomPacketPayload {
		public static final Type<StartCooking> TYPE = new Type<>(JaymosKitchen.id("start_cooking"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StartCooking> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.dish);
				buf.writeVarInt(p.station);
			},
			buf -> new StartCooking(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<StartCooking> type() {
			return TYPE;
		}
	}

	/** Client → server: a step button was pressed. */
	public record PressStep(int step) implements CustomPacketPayload {
		public static final Type<PressStep> TYPE = new Type<>(JaymosKitchen.id("press_step"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PressStep> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.step),
			buf -> new PressStep(buf.readVarInt()));

		@Override
		public Type<PressStep> type() {
			return TYPE;
		}
	}

	/** Client → server: the screen was closed mid-cook. */
	public record Abandon() implements CustomPacketPayload {
		public static final Type<Abandon> TYPE = new Type<>(JaymosKitchen.id("abandon"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Abandon> CODEC = StreamCodec.unit(new Abandon());

		@Override
		public Type<Abandon> type() {
			return TYPE;
		}
	}

	/** Client → server: let go of the corpse being carried: it drops from the arms, or ({@code toss}) is thrown. */
	public record DropCarried(boolean toss) implements CustomPacketPayload {
		public static final Type<DropCarried> TYPE = new Type<>(JaymosKitchen.id("drop_carried"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DropCarried> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.toss), buf -> new DropCarried(buf.readBoolean()));

		@Override
		public Type<DropCarried> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: the state of the player's cooking session.
	 * {@code status} is one of the STATUS_ constants; {@code quality} is only meaningful once finished.
	 */
	public record CookUpdate(int dish, int progress, int mistakes, int status, int quality, int parTicks, int limitTicks)
		implements CustomPacketPayload {
		public static final int STATUS_COOKING = 0;
		public static final int STATUS_FINISHED = 1;
		public static final int STATUS_REJECTED = 2;

		public static final Type<CookUpdate> TYPE = new Type<>(JaymosKitchen.id("cook_update"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CookUpdate> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.dish);
				buf.writeVarInt(p.progress);
				buf.writeVarInt(p.mistakes);
				buf.writeVarInt(p.status);
				buf.writeVarInt(p.quality);
				buf.writeVarInt(p.parTicks);
				buf.writeVarInt(p.limitTicks);
			},
			buf -> new CookUpdate(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<CookUpdate> type() {
			return TYPE;
		}
	}

	/** Client → server: a grill control was used. */
	public record GrillAction(int action) implements CustomPacketPayload {
		public static final int FLIP = 0;
		public static final int SERVE = 1;

		public static final Type<GrillAction> TYPE = new Type<>(JaymosKitchen.id("grill_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GrillAction> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.action),
			buf -> new GrillAction(buf.readVarInt()));

		@Override
		public Type<GrillAction> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: the grill's state. {@code doneA}/{@code doneB} are each side's doneness as of sending;
	 * {@code down} is the side on the fire, which keeps gaining {@code perTick} a tick until the next update.
	 * {@code heat} is the fire's current strength (1.0 = normal), shown as flame height.
	 */
	public record GrillUpdate(int dish, float doneA, float doneB, int down, float perTick, float heat, int status, int quality)
		implements CustomPacketPayload {
		public static final int STATUS_COOKING = CookUpdate.STATUS_COOKING;
		public static final int STATUS_FINISHED = CookUpdate.STATUS_FINISHED;

		public static final Type<GrillUpdate> TYPE = new Type<>(JaymosKitchen.id("grill_update"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GrillUpdate> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.dish);
				buf.writeFloat(p.doneA);
				buf.writeFloat(p.doneB);
				buf.writeVarInt(p.down);
				buf.writeFloat(p.perTick);
				buf.writeFloat(p.heat);
				buf.writeVarInt(p.status);
				buf.writeVarInt(p.quality);
			},
			buf -> new GrillUpdate(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
				buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<GrillUpdate> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: open the prep board on the surface at {@code pos}. {@code recipe} is a {@code Prep.Recipe}
	 * ordinal to go straight to (rolling, when the player clicked with Pmeh in hand), or -1 to choose.
	 */
	public record OpenPrep(BlockPos pos, int recipe, int stocked) implements CustomPacketPayload {
		public static final Type<OpenPrep> TYPE = new Type<>(JaymosKitchen.id("open_prep"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPrep> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.recipe + 1);
				buf.writeInt(p.stocked);
			},
			buf -> new OpenPrep(buf.readBlockPos(), buf.readVarInt() - 1, buf.readInt()));

		@Override
		public Type<OpenPrep> type() {
			return TYPE;
		}
	}

	/** Client → server: take the ingredients for {@code recipe} (a {@code Prep.Recipe} ordinal) and start. */
	public record StartPrep(BlockPos pos, int recipe) implements CustomPacketPayload {
		public static final Type<StartPrep> TYPE = new Type<>(JaymosKitchen.id("start_prep"));
		public static final StreamCodec<RegistryFriendlyByteBuf, StartPrep> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.recipe);
			},
			buf -> new StartPrep(buf.readBlockPos(), buf.readVarInt()));

		@Override
		public Type<StartPrep> type() {
			return TYPE;
		}
	}

	/** Client → server: the joint is rolled (or ruined); how the grinding, sprinkling and rolling went. */
	public record FinishRoll(boolean ground, float evenness, float tears, boolean rolled) implements CustomPacketPayload {
		public static final Type<FinishRoll> TYPE = new Type<>(JaymosKitchen.id("finish_roll"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FinishRoll> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.ground);
				buf.writeFloat(p.evenness);
				buf.writeFloat(p.tears);
				buf.writeBoolean(p.rolled);
			},
			buf -> new FinishRoll(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

		@Override
		public Type<FinishRoll> type() {
			return TYPE;
		}
	}

	/** Client → server: the mix is done; how full each pour got and whether the knead finished in time. */
	public record FinishPrep(float water, float oil, boolean kneaded) implements CustomPacketPayload {
		public static final Type<FinishPrep> TYPE = new Type<>(JaymosKitchen.id("finish_prep"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FinishPrep> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.water);
				buf.writeFloat(p.oil);
				buf.writeBoolean(p.kneaded);
			},
			buf -> new FinishPrep(buf.readFloat(), buf.readFloat(), buf.readBoolean()));

		@Override
		public Type<FinishPrep> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: prep started (with this mix's band offsets: the target bands move every time),
	 * finished (with a {@code Prep.Result} ordinal), or was refused.
	 */
	public record PrepUpdate(int status, int result, float waterShift, float oilShift) implements CustomPacketPayload {
		public static final int STATUS_STARTED = 0;
		public static final int STATUS_FINISHED = 1;
		public static final int STATUS_REJECTED = 2;

		public static final Type<PrepUpdate> TYPE = new Type<>(JaymosKitchen.id("prep_update"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PrepUpdate> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.status);
				buf.writeVarInt(p.result);
				buf.writeFloat(p.waterShift);
				buf.writeFloat(p.oilShift);
			},
			buf -> new PrepUpdate(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat()));

		@Override
		public Type<PrepUpdate> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a client-simulated minigame (smoking, searing) started or finished (with a {@code Quality}
	 * ordinal). The dish says which minigame.
	 */
	public record SimUpdate(int dish, int status, int quality) implements CustomPacketPayload {
		public static final int STATUS_STARTED = 0;
		public static final int STATUS_FINISHED = 1;

		public static final Type<SimUpdate> TYPE = new Type<>(JaymosKitchen.id("sim_update"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SimUpdate> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.dish);
				buf.writeVarInt(p.status);
				buf.writeVarInt(p.quality);
			},
			buf -> new SimUpdate(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<SimUpdate> type() {
			return TYPE;
		}
	}

	/** Client → server: smoking is over; how long it scorched, how long it went cold, and whether it cured. */
	public record SmokeFinish(int scorchTicks, int coldTicks, boolean cured) implements CustomPacketPayload {
		public static final Type<SmokeFinish> TYPE = new Type<>(JaymosKitchen.id("smoke_finish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SmokeFinish> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.scorchTicks);
				buf.writeVarInt(p.coldTicks);
				buf.writeBoolean(p.cured);
			},
			buf -> new SmokeFinish(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<SmokeFinish> type() {
			return TYPE;
		}
	}

	/** Client → server: searing is over; how many rounds were perfect, good, or missed. */
	public record SearFinish(int perfects, int goods, int misses) implements CustomPacketPayload {
		public static final Type<SearFinish> TYPE = new Type<>(JaymosKitchen.id("sear_finish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SearFinish> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.perfects);
				buf.writeVarInt(p.goods);
				buf.writeVarInt(p.misses);
			},
			buf -> new SearFinish(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<SearFinish> type() {
			return TYPE;
		}
	}

	/**
	 * (0.4.32) Server → client: a butcher villager looks over the body you brought ({@code what}) and names his price.
	 * {@code skill}: how many times what you'd get yourself (1 to 3); {@code tier} 0 (clumsy) to 4 (master).
	 */
	public record OpenButcher(int villager, Component name, Component what, float skill, int tier, int price) implements CustomPacketPayload {
		public static final Type<OpenButcher> TYPE = new Type<>(JaymosKitchen.id("open_butcher"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenButcher> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenButcher::villager,
			ComponentSerialization.TRUSTED_STREAM_CODEC, OpenButcher::name,
			ComponentSerialization.TRUSTED_STREAM_CODEC, OpenButcher::what,
			ByteBufCodecs.FLOAT, OpenButcher::skill,
			ByteBufCodecs.VAR_INT, OpenButcher::tier,
			ByteBufCodecs.VAR_INT, OpenButcher::price,
			OpenButcher::new);

		@Override
		public Type<OpenButcher> type() {
			return TYPE;
		}
	}

	/** (0.4.38) Client → server: a chop, roast or stir is over; what the four numbers mean is in Minigames#finish. */
	public record MinigameFinish(int a, int b, int c, int d) implements CustomPacketPayload {
		public static final Type<MinigameFinish> TYPE = new Type<>(JaymosKitchen.id("minigame_finish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MinigameFinish> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.a);
				buf.writeVarInt(p.b);
				buf.writeVarInt(p.c);
				buf.writeVarInt(p.d);
			},
			buf -> new MinigameFinish(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<MinigameFinish> type() {
			return TYPE;
		}
	}

	/** (0.4.32) Client → server: hand the body to the butcher and pay. */
	public record ButcherDeal(int villager) implements CustomPacketPayload {
		public static final Type<ButcherDeal> TYPE = new Type<>(JaymosKitchen.id("butcher_deal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ButcherDeal> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.villager), buf -> new ButcherDeal(buf.readVarInt()));

		@Override
		public Type<ButcherDeal> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenButcher.TYPE, OpenButcher.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ButcherDeal.TYPE, ButcherDeal.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SearFinish.TYPE, SearFinish.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MinigameFinish.TYPE, MinigameFinish.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DropCarried.TYPE, DropCarried.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FinishRoll.TYPE, FinishRoll.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SimUpdate.TYPE, SimUpdate.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SmokeFinish.TYPE, SmokeFinish.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenPrep.TYPE, OpenPrep.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PrepUpdate.TYPE, PrepUpdate.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StartPrep.TYPE, StartPrep.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FinishPrep.TYPE, FinishPrep.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GrillUpdate.TYPE, GrillUpdate.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GrillAction.TYPE, GrillAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenKitchen.TYPE, OpenKitchen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CookUpdate.TYPE, CookUpdate.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StartCooking.TYPE, StartCooking.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PressStep.TYPE, PressStep.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Abandon.TYPE, Abandon.CODEC);
	}
}
