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
import net.minecraft.world.item.ItemStack;

/** Every packet the cooking minigame sends. The server owns the session; the client only presses buttons. */
public final class Payloads {
	private Payloads() {
	}

	/** Server → client: open the kitchen screen for the station at {@code pos}. */
	/** {@code stocked}: bit i set when the station's i-th dish can be made from the pockets and nearby storage (a BitSet: any number of dishes). */
	public record OpenKitchen(BlockPos pos, int station, java.util.BitSet stocked) implements CustomPacketPayload {
		public static final Type<OpenKitchen> TYPE = new Type<>(JaymosKitchen.id("open_kitchen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenKitchen> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.station);
				buf.writeBitSet(p.stocked);
			},
			buf -> new OpenKitchen(buf.readBlockPos(), buf.readVarInt(), buf.readBitSet()));

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
		/** The dish went on at the block, done by hand: close the screen. */
		public static final int STATUS_HANDS = 3;

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
		/** It went on at the board, done by hand: close the screen. */
		public static final int STATUS_HANDS = 3;

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
	 * {@code skill}: how many times what you'd get yourself (1 to 3); {@code tier} 0 (clumsy) to 4 (master). (0.5.56) {@code price}
	 * is made by the server, in the coin of the land ("6 Realm Silver"), and {@code coin} is one of that coin to draw beside it;
	 * {@code cost} is the price in milli and {@code have} is that cost when the server's charge rule would take it from what you
	 * carry (foreign coin at the going rate), else 0 (a purse is hidden from the client).
	 */
	public record OpenButcher(int villager, Component name, Component what, float skill, int tier, Component price, ItemStack coin, long cost,
		long have) implements CustomPacketPayload {
		public static final Type<OpenButcher> TYPE = new Type<>(JaymosKitchen.id("open_butcher"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenButcher> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenButcher::villager,
			ComponentSerialization.TRUSTED_STREAM_CODEC, OpenButcher::name,
			ComponentSerialization.TRUSTED_STREAM_CODEC, OpenButcher::what,
			ByteBufCodecs.FLOAT, OpenButcher::skill,
			ByteBufCodecs.VAR_INT, OpenButcher::tier,
			ComponentSerialization.TRUSTED_STREAM_CODEC, OpenButcher::price,
			ItemStack.STREAM_CODEC, OpenButcher::coin,
			ByteBufCodecs.VAR_LONG, OpenButcher::cost,
			ByteBufCodecs.VAR_LONG, OpenButcher::have,
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

	/** (0.5.6) Server → client: how many days the calendar runs ahead of the world's day count (see {@code Seasons}). */
	public record SeasonSync(int shift) implements CustomPacketPayload {
		public static final Type<SeasonSync> TYPE = new Type<>(JaymosKitchen.id("season_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SeasonSync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.shift),
			buf -> new SeasonSync(buf.readVarInt()));

		@Override
		public Type<SeasonSync> type() {
			return TYPE;
		}
	}

	/** (0.5.25) Client → server: sneaking with an empty hand at water out of reach of a block, or up at the rain: a sip. */
	public record Sip() implements CustomPacketPayload {
		public static final Type<Sip> TYPE = new Type<>(JaymosKitchen.id("sip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sip> CODEC = StreamCodec.unit(new Sip());

		@Override
		public Type<Sip> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: someone ({@code by}, an entity id, or -1) tore a chunk off the spit at {@code pos}; the roast shakes
	 * and they're drawn reaching in and ripping it away.
	 */
	public record SpitRip(BlockPos pos, int by) implements CustomPacketPayload {
		public static final Type<SpitRip> TYPE = new Type<>(JaymosKitchen.id("spit_rip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SpitRip> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.by);
			},
			buf -> new SpitRip(buf.readBlockPos(), buf.readVarInt()));

		@Override
		public Type<SpitRip> type() {
			return TYPE;
		}
	}

	/** Server → client: a parasite cramp just hit you (ParasiteEffect): the sickly flash at the edges of the screen. */
	public record Cramp() implements CustomPacketPayload {
		public static final Type<Cramp> TYPE = new Type<>(JaymosKitchen.id("parasite_cramp"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cramp> CODEC = StreamCodec.unit(new Cramp());

		@Override
		public Type<Cramp> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: open the screen of the hearth or icebox at {@code pos}: its fuel (of {@code most}), whether it is the
	 * cold one and lit, how much air it fills, and whether the player carries something it burns.
	 */
	public record OpenHearth(BlockPos pos, int fuel, int most, boolean cold, boolean lit, int cells, boolean canFeed) implements CustomPacketPayload {
		public static final Type<OpenHearth> TYPE = new Type<>(JaymosKitchen.id("open_hearth"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenHearth> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.fuel);
				buf.writeVarInt(p.most);
				buf.writeBoolean(p.cold);
				buf.writeBoolean(p.lit);
				buf.writeVarInt(p.cells);
				buf.writeBoolean(p.canFeed);
			},
			buf -> new OpenHearth(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
				buf.readBoolean()));

		@Override
		public Type<OpenHearth> type() {
			return TYPE;
		}
	}

	/** Client → server: put one thing from the pockets that the hearth or icebox at {@code pos} burns into it. */
	public record FeedHearth(BlockPos pos) implements CustomPacketPayload {
		public static final Type<FeedHearth> TYPE = new Type<>(JaymosKitchen.id("feed_hearth"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FeedHearth> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBlockPos(p.pos),
			buf -> new FeedHearth(buf.readBlockPos()));

		@Override
		public Type<FeedHearth> type() {
			return TYPE;
		}
	}

	/**
	 * Server to client: what is on the station at {@code pos}, and how its dish is going. {@code phase} is one of the PHASE_
	 * constants; {@code kind} a {@code Pick.Kind} ordinal; {@code pick} of {@code picks} the dish chosen from those the
	 * placed things make ({@code dish} its ordinal, -1 for none); {@code quality} a Quality ordinal once it is judged
	 * (else -1). {@code ints} and {@code floats} are the readout of the running kind (see each Run's fill).
	 */
	public record HandsSync(BlockPos pos, int phase, int kind, int dish, int pick, int picks, int quality, int cooks,
		java.util.List<ItemStack> placed, int[] ints, float[] floats) implements CustomPacketPayload {
		public static final int PHASE_GONE = 0, PHASE_STAGING = 1, PHASE_RUNNING = 2, PHASE_JUDGED = 3;

		public static final Type<HandsSync> TYPE = new Type<>(JaymosKitchen.id("hands_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HandsSync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.phase);
				buf.writeVarInt(p.kind);
				buf.writeVarInt(p.dish + 1);
				buf.writeVarInt(p.pick);
				buf.writeVarInt(p.picks);
				buf.writeVarInt(p.quality + 1);
				buf.writeVarInt(p.cooks);
				buf.writeVarInt(p.placed.size());
				for (ItemStack stack : p.placed) {
					ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
				}
				buf.writeVarInt(p.ints.length);
				for (int value : p.ints) {
					buf.writeVarInt(value);
				}
				buf.writeVarInt(p.floats.length);
				for (float value : p.floats) {
					buf.writeFloat(value);
				}
			},
			buf -> {
				BlockPos pos = buf.readBlockPos();
				int phase = buf.readVarInt();
				int kind = buf.readVarInt();
				int dish = buf.readVarInt() - 1;
				int pick = buf.readVarInt();
				int picks = buf.readVarInt();
				int quality = buf.readVarInt() - 1;
				int cooks = buf.readVarInt();
				java.util.List<ItemStack> placed = new java.util.ArrayList<>();
				for (int i = buf.readVarInt(); i > 0; i--) {
					placed.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
				}
				int[] ints = new int[buf.readVarInt()];
				for (int i = 0; i < ints.length; i++) {
					ints[i] = buf.readVarInt();
				}
				float[] floats = new float[buf.readVarInt()];
				for (int i = 0; i < floats.length; i++) {
					floats[i] = buf.readFloat();
				}
				return new HandsSync(pos, phase, kind, dish, pick, picks, quality, cooks, placed, ints, floats);
			});

		@Override
		public Type<HandsSync> type() {
			return TYPE;
		}
	}

	/** Client to server: a cook's input at the station at {@code pos} (what {@code what} means is in {@code Run#input}). */
	public record HandsInput(BlockPos pos, int what, float value) implements CustomPacketPayload {
		public static final Type<HandsInput> TYPE = new Type<>(JaymosKitchen.id("hands_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HandsInput> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.what);
				buf.writeFloat(p.value);
			},
			buf -> new HandsInput(buf.readBlockPos(), buf.readVarInt(), buf.readFloat()));

		@Override
		public Type<HandsInput> type() {
			return TYPE;
		}
	}

	/** Client to server: sneak and scroll over a station: choose the next ({@code delta} 1) or previous (-1) dish it could make. */
	public record HandsPick(BlockPos pos, int delta) implements CustomPacketPayload {
		public static final Type<HandsPick> TYPE = new Type<>(JaymosKitchen.id("hands_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HandsPick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBlockPos(p.pos);
				buf.writeVarInt(p.delta);
			},
			buf -> new HandsPick(buf.readBlockPos(), buf.readVarInt()));

		@Override
		public Type<HandsPick> type() {
			return TYPE;
		}
	}

	/** Server to client: whether cooking is done by hand at the stations (the game rule), so the client knows what its clicks mean. */
	public record HandsMode(boolean on) implements CustomPacketPayload {
		public static final Type<HandsMode> TYPE = new Type<>(JaymosKitchen.id("hands_mode"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HandsMode> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.on), buf -> new HandsMode(buf.readBoolean()));

		@Override
		public Type<HandsMode> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HandsSync.TYPE, HandsSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HandsInput.TYPE, HandsInput.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HandsPick.TYPE, HandsPick.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HandsMode.TYPE, HandsMode.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Sip.TYPE, Sip.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenHearth.TYPE, OpenHearth.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FeedHearth.TYPE, FeedHearth.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SeasonSync.TYPE, SeasonSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpitRip.TYPE, SpitRip.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Cramp.TYPE, Cramp.CODEC);
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
