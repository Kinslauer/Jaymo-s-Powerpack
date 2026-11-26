package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The midnight bargain: the Warlock's pact sealed as a story scene ({@link StoryScenes}, the first of them), after Andy's
 * Endless Lantern Corps pick (2026-11-26: the ring's acquisition as the way our story missions flow; and "a voice recognition
 * integration that we could use for warlock spellcasting. that would help differentiate it from paladins").
 * <p>
 * The lore has it that a Warlock "strikes a bargain at a shrine at midnight with whatever answers, and is not told the price".
 * A Warlock's pact is still sworn with the class ({@link Warlocks#begin}), but it isn't sealed until then:
 * <ol>
 * <li><b>The hint:</b> at dusk the seal, held, whispers over its holder's head ({@link #HINT_FROM}), once a day.</li>
 * <li><b>The scene:</b> a Warlock who hasn't made the bargain, using any shrine between {@link #MIDNIGHT_FROM} and
 * {@link #MIDNIGHT_TO}, meets whatever answers: the candles gutter and something gathers over the stone
 * ({@code story_scenes/midnight_bargain.json}). It asks the one question ("will you pay what is asked, when it is asked?");
 * a no ends it for the night, a yes brings them to their knees to say the words back.</li>
 * <li><b>The seal:</b> once said, the bargain is made ({@link #MADE}) and paid for as every pact is, later.</li>
 * </ol>
 * <b>Spoken spells:</b> after the bargain, while the seal is in a hand, the Warlock's game is told the spells they know
 * ({@link Words}); saying one's name aloud (the client's {@code SpokenSpells}, with the microphone switched on) casts it
 * straight away, whatever the seal is turned to ({@link Spoken}), on the same slots and debts as a tap. The name is heard
 * over their head by everyone near. A gag or a bubble of silence stops it ({@link SpellParts#canSpeak}). A Paladin's oath
 * is kept in silence; a Warlock's magic is said out loud.
 */
public final class MidnightBargain {
	/** The hour the shrine answers (day time: 18000 is midnight), and dusk, when the seal whispers. */
	static final long MIDNIGHT_FROM = 17000L, MIDNIGHT_TO = 19500L, HINT_FROM = 12500L, HINT_TO = 13500L;
	/** How near others hear a spell said aloud (blocks); the shortest gap between two spoken casts (ticks). */
	static final double HEARD_RANGE = 16.0;
	static final int SPOKEN_GAP = 10;
	/** The seal's colour, for the scene's dark and the spoken words. */
	static final int COLOR = 0x8A4FD0;
	/** The scene's file. */
	static final String SCENE = "midnight_bargain";

	/** Whether this player has made the bargain (kept through death). */
	static final AttachmentType<Boolean> MADE = AttachmentRegistry.create(JaymosKingdoms.id("midnight_bargain"), b -> b.persistent(Codec.BOOL).copyOnDeath());

	/** Server → client: the spells this Warlock may say aloud now (empty: the seal isn't in hand, stop listening). */
	public record Words(List<String> spells) implements CustomPacketPayload {
		public static final Type<Words> TYPE = new Type<>(JaymosKingdoms.id("spoken_words"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Words> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Words::spells, Words::new);

		@Override
		public Type<Words> type() {
			return TYPE;
		}
	}

	/** Client → server: the Warlock said {@code spell}'s name aloud. */
	public record Spoken(String spell) implements CustomPacketPayload {
		public static final Type<Spoken> TYPE = new Type<>(JaymosKingdoms.id("spoken_spell"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spoken> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Spoken::spell, Spoken::new);

		@Override
		public Type<Spoken> type() {
			return TYPE;
		}
	}

	/** What each player's game was last told it may hear; the day each was last whispered to; the last spoken cast. */
	private static final Map<UUID, List<String>> TOLD = new HashMap<>();
	private static final Map<UUID, Long> HINTED = new HashMap<>(), SPOKE_AT = new HashMap<>();

	private MidnightBargain() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Words.TYPE, Words.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Spoken.TYPE, Spoken.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spoken.TYPE, (payload, context) -> spoken(context.player(), payload.spell()));
		ServerTickEvents.END_SERVER_TICK.register(MidnightBargain::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TOLD.remove(handler.player.getUUID());
			SPOKE_AT.remove(handler.player.getUUID());
		});
		StoryScenes.outcome("midnight.dark", MidnightBargain::dark);
		StoryScenes.outcome("midnight.kneel", (player, playing) -> kneel(player, playing, true));
		StoryScenes.outcome("midnight.rise", (player, playing) -> kneel(player, playing, false));
		StoryScenes.outcome("midnight.sealed", MidnightBargain::sealed);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("scene").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("forget").executes(c -> forget(c.getSource())))
				.then(Commands.argument("id", StringArgumentType.word()).executes(c -> test(c.getSource(), StringArgumentType.getString(c, "id")))))));
	}

	/** Whether {@code player} has made the bargain. */
	public static boolean made(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(MADE));
	}

	/** (from {@link Shrine}) A shrine used at midnight by a Warlock who hasn't made the bargain: the scene begins. */
	static boolean atShrine(ServerPlayer player, BlockPos pos) {
		if (!Classes.is(player, PlayerClass.WARLOCK) || made(player) || StoryScenes.playing(player) || !midnight((ServerLevel) player.level())) {
			return false;
		}
		return StoryScenes.play(player, SCENE, Vec3.atBottomCenterOf(pos).add(0.0, 1.0, 0.0));
	}

	private static boolean midnight(ServerLevel level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= MIDNIGHT_FROM && t <= MIDNIGHT_TO;
	}

	/** The candles gutter and something gathers over the stone. */
	private static List<Component> dark(ServerPlayer player, StoryScenes.Playing playing) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = playing.anchor();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.25F, 0.5F);
		level.sendParticles(Fx.miasma(0x1A0F24, 1.6F), at.x, at.y + 0.8, at.z, 40, 0.35, 0.9, 0.35, 0.01);
		level.sendParticles(Fx.mote(COLOR, 0.6F), at.x, at.y + 1.2, at.z, 14, 0.4, 0.6, 0.4, 0.01);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 6, 0, false, false));
		return List.of();
	}

	/** Down on their knees for the words, or back up. */
	private static List<Component> kneel(ServerPlayer player, StoryScenes.Playing playing, boolean down) {
		if (down) {
			player.setAttached(CaptiveIdle.DOING, CaptiveIdle.KNEEL);
			Fx.sigil((ServerLevel) player.level(), player.position().add(0.0, 0.05, 0.0), 1.2F, COLOR, 20 * 30);
		} else {
			player.removeAttached(CaptiveIdle.DOING);
		}
		return List.of();
	}

	/** The words were said: the bargain is made. */
	private static List<Component> sealed(ServerPlayer player, StoryScenes.Playing playing) {
		player.removeAttached(CaptiveIdle.DOING);
		player.setAttached(MADE, true);
		ClassTrees.give(player, PlayerClass.WARLOCK, ClassTrees.XP_DEBT * 3);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = playing.anchor();
		Fx.blast(level, at.add(0.0, 0.4, 0.0), 1.4F, COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		return List.of(Component.translatable("story.jaymoskingdoms.midnight_bargain.gift.made").withStyle(ChatFormatting.LIGHT_PURPLE),
			Component.translatable("story.jaymoskingdoms.midnight_bargain.gift.spoken"));
	}

	// ---- spoken spells ------------------------------------------------------------------------------------------------

	private static boolean sealInHand(ServerPlayer player) {
		return player.getMainHandItem().is(Warlocks.SEAL) || player.getOffhandItem().is(Warlocks.SEAL);
	}

	/** The spells {@code player} may say aloud (theirs as a first or second Warlock), in the order they know them. */
	private static List<String> sayable(ServerPlayer player) {
		Set<String> out = new LinkedHashSet<>();
		if (Classes.classOf(player) == PlayerClass.WARLOCK) {
			out.addAll(Classes.of(player).spells());
		}
		if (Classes.second(player) == PlayerClass.WARLOCK) {
			out.addAll(Classes.secondOf(player).spells());
		}
		return List.copyOf(out);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// a scene left while kneeling for the words: back up (no player kneels this way otherwise)
			if (player.hasAttached(CaptiveIdle.DOING) && !StoryScenes.playing(player)) {
				player.removeAttached(CaptiveIdle.DOING);
			}
			boolean warlock = Classes.is(player, PlayerClass.WARLOCK);
			boolean holding = warlock && sealInHand(player);
			// the words the game may listen for: only with the seal in hand, after the bargain
			List<String> now = holding && made(player) && !StoryScenes.playing(player) ? sayable(player) : List.of();
			List<String> was = TOLD.getOrDefault(player.getUUID(), List.of());
			if (!now.equals(was) && ServerPlayNetworking.canSend(player, Words.TYPE)) {
				ServerPlayNetworking.send(player, new Words(now));
				TOLD.put(player.getUUID(), now);
			}
			// the hint: the seal whispers at dusk, once a day, until the bargain is made
			if (holding && !made(player) && server.getTickCount() % 40 == 0) {
				ServerLevel level = (ServerLevel) player.level();
				long time = level.getOverworldClockTime();
				long t = Math.floorMod(time, 24000L), day = Math.floorDiv(time, 24000L);
				if (t >= HINT_FROM && t <= HINT_TO && !Long.valueOf(day).equals(HINTED.get(player.getUUID()))) {
					HINTED.put(player.getUUID(), day);
					Speech.privately(player, player, Component.translatable("story.jaymoskingdoms.midnight_bargain.hint"), COLOR);
				}
			}
		}
	}

	/** {@code spell} said aloud: cast straight away if it's theirs and the seal is in hand. */
	private static void spoken(ServerPlayer player, String spell) {
		if (!Classes.is(player, PlayerClass.WARLOCK) || !made(player) || StoryScenes.playing(player) || !sayable(player).contains(spell)) {
			return;
		}
		ItemStack seal = player.getMainHandItem().is(Warlocks.SEAL) ? player.getMainHandItem() : player.getOffhandItem();
		if (!seal.is(Warlocks.SEAL) || !SpellParts.canSpeak(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = SPOKE_AT.get(player.getUUID());
		if (last != null && now - last < SPOKEN_GAP) {
			return;
		}
		SPOKE_AT.put(player.getUUID(), now);
		ServerLevel level = (ServerLevel) player.level();
		Speech.say(level, player, Spellcraft.spellName(spell).copy().withStyle(ChatFormatting.ITALIC), COLOR, HEARD_RANGE);
		player.swing(player.getMainHandItem() == seal ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Spellcraft.castFocus(player, level, seal, spell);
	}

	// ---- testing ------------------------------------------------------------------------------------------------------

	/** {@code /kingdom scene <id>}: plays a scene here, framed two blocks ahead (any hour; a bargain still counts). */
	private static int test(CommandSourceStack source, String id) {
		ServerPlayer player = source.getPlayer();
		if (player == null || !StoryScenes.exists(id)) {
			source.sendFailure(Component.translatable("commands.jaymoskingdoms.scene.none", id));
			return 0;
		}
		Vec3 ahead = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0)).add(0.0, 1.0, 0.0);
		return StoryScenes.play(player, id, ahead) ? 1 : 0;
	}

	/** {@code /kingdom scene forget}: the bargain unmade, to be met again. */
	private static int forget(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		player.removeAttached(MADE);
		source.sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.scene.forgot"), false);
		return 1;
	}
}
