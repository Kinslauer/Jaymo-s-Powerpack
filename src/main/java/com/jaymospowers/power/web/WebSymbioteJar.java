package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bells and jars: a bell rung close (within {@link #BELL_NEAR} blocks) leaves a host's symbiote stunned and slack for
 * {@link #STUN_TICKS} (its powers don't answer, it stops flinching from the noise), if the host holds a glass bottle and
 * hasn't bonded deep (under {@link #DEEP_BOND}). Use the bottle in that time and the symbiote is poured off into a
 * <b>jarred symbiote</b> that keeps its strain, hunger and bond (less {@link #BOND_LOSS}). It does not like this: it says
 * why as it goes, its trust in the host falls by {@link #TRUST_LOSS}, and the jar remembers whose hand held the glass.
 * Use the jar on yourself, free of a symbiote, and it goes back in with the bond it had left; in a hand that isn't the
 * one that held the glass, it says so, and trusts that one less still.
 */
public final class WebSymbioteJar {
	/** A bell this close is heard as the jar's bell; the bond a host may have at most (exclusive) to be poured off. */
	public static final double BELL_NEAR = 6.0;
	public static final int DEEP_BOND = 70;
	/** How long it is stunned, and what a pour costs: trust, and bond kept in the jar. */
	public static final int STUN_TICKS = 300, TRUST_LOSS = 25, BOND_LOSS = 12;
	/** The jar let back into a hand that isn't the first one's: more trust lost. */
	public static final int TRUST_LOSS_STRANGER = 10;
	/** Weakness (ticks) is on the host while the symbiote is slack. */
	private static final int SLACK_AMPLIFIER = 0;
	/** Lines of each kind (jar.pour.1..N, jar.back.keeper.1..N, jar.back.stranger.1..N). */
	private static final int LINES = 3;

	/** What the jar holds: the symbiote's strain, bond and satiety, and the hand that held the glass (a player's id). */
	public record Jarred(int strain, int bond, int satiety, String keeper) {
		public static final Codec<Jarred> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("strain").forGetter(Jarred::strain),
				Codec.INT.fieldOf("bond").forGetter(Jarred::bond),
				Codec.INT.fieldOf("satiety").forGetter(Jarred::satiety),
				Codec.STRING.fieldOf("keeper").forGetter(Jarred::keeper))
			.apply(i, Jarred::new));
		public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Jarred> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Jarred::strain,
			ByteBufCodecs.VAR_INT, Jarred::bond,
			ByteBufCodecs.VAR_INT, Jarred::satiety,
			ByteBufCodecs.STRING_UTF8, Jarred::keeper,
			Jarred::new);
	}

	public static DataComponentType<Jarred> JARRED;
	public static Item ITEM;

	/** Each stunned host: game time it comes round. */
	private static final Map<UUID, Long> STUNNED = new ConcurrentHashMap<>();

	private WebSymbioteJar() {
	}

	static void register() {
		JARRED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("jarred_symbiote"),
			DataComponentType.<Jarred>builder().persistent(Jarred.CODEC).networkSynchronized(Jarred.STREAM_CODEC).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("jarred_symbiote"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new JarItem(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(
			jar(WebSymbioteStrain.BLACK.id(), WebSymbiote.BOND_MAX / 2, WebSymbiote.SATIETY_MAX, "")));
		// a glass bottle, used on a stunned host
		UseItemCallback.EVENT.register((player, level, hand) -> pour(player, level, hand));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!STUNNED.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					tick(player);
				}
			}
		});
	}

	static void forget(ServerPlayer player) {
		STUNNED.remove(player.getUUID());
	}

	/** A jar holding a symbiote. */
	public static ItemStack jar(int strain, int bond, int satiety, String keeper) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(JARRED, new Jarred(strain, bond, satiety, keeper));
		return stack;
	}

	// ---- the bell ----------------------------------------------------------------------------------------------------

	/** The host's symbiote is slack from a bell (its powers refuse, and it isn't flinching from the noise). */
	public static boolean stunned(Player player) {
		Long until = STUNNED.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	private static boolean holdsBottle(Player player) {
		return player.getMainHandItem().is(Items.GLASS_BOTTLE) || player.getOffhandItem().is(Items.GLASS_BOTTLE);
	}

	/** A bell rang at {@code pos} (from {@link WebSymbiote#onGameEvent}, before the noise is dealt out). */
	static void onBell(ServerLevel level, Vec3 pos) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (!WebSymbiote.isBonded(player) || !player.isAlive() || player.isSpectator() || player.position().distanceToSqr(pos) > BELL_NEAR * BELL_NEAR
				|| !holdsBottle(player) || WebSymbiote.state(player).bond() >= DEEP_BOND || stunned(player)) {
				continue;
			}
			STUNNED.put(player.getUUID(), now + STUN_TICKS);
			WebSymbiote.whisper(player, "jar.stunned");
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, SLACK_AMPLIFIER, false, false));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
			com.jaymospowers.core.Shakes.send(player, 0.6F, null);
		}
	}

	/** Every tick, for a bonded host: slack, it drips; when it comes round it isn't pleased. */
	static void tick(ServerPlayer player) {
		Long until = STUNNED.get(player.getUUID());
		if (until == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!WebSymbiote.isBonded(player) || !player.isAlive()) {
			STUNNED.remove(player.getUUID());
			return;
		}
		if (now >= until) {
			STUNNED.remove(player.getUUID());
			WebSymbiote.whisper(player, "jar.recover");
			return;
		}
		if (player.tickCount % 5 == 0) {
			level.sendParticles(WebSymbiote.dust(0.9F), player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
	}

	// ---- the bottle --------------------------------------------------------------------------------------------------

	/** A glass bottle used on yourself while the symbiote is slack: it is poured off into the glass. */
	private static InteractionResult pour(Player player, Level level, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.GLASS_BOTTLE) || !(player instanceof ServerPlayer host) || !stunned(host) || !WebSymbiote.isBonded(host)) {
			return InteractionResult.PASS;
		}
		WebSymbioteState state = WebSymbiote.state(host);
		// why it hates this, said as it goes (before the bond is gone, so it speaks in its own voice)
		WebSymbiote.whisper(host, "jar.pour." + (1 + host.getRandom().nextInt(LINES)));
		WebSymbioteMind.addTrust(host, -TRUST_LOSS);
		STUNNED.remove(host.getUUID());
		host.removeEffect(MobEffects.WEAKNESS);
		ItemStack jar = jar(state.strain(), Math.max(0, state.bond() - BOND_LOSS), state.satiety(), host.getUUID().toString());
		// it comes out of the host and into the glass (no blob is left crawling about)
		WebSymbioteBlob blob = WebSymbiote.withdraw(host);
		blob.discard();
		held.shrink(1);
		if (!host.getInventory().add(jar)) {
			host.drop(jar, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		ServerLevel serverLevel = host.level();
		serverLevel.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 0.6F);
		serverLevel.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.8F, 1.7F);
		serverLevel.sendParticles(ParticleTypes.SQUID_INK, host.getX(), host.getY() + 1.1, host.getZ(), 30, 0.3, 0.5, 0.3, 0.05);
		return InteractionResult.SUCCESS;
	}

	/** The jar used on yourself: the symbiote goes back in, if you have none. */
	private static InteractionResult letIn(ServerPlayer player, ItemStack stack) {
		Jarred jarred = stack.get(JARRED);
		if (jarred == null || WebSymbiote.isBonded(player)) {
			return InteractionResult.PASS;
		}
		boolean keeper = jarred.keeper().equals(player.getUUID().toString());
		WebSymbiote.bond(player, player.position(), WebSymbioteStrain.of(jarred.strain()));
		// the bond it had in the glass (less what the bell and the jar cost), no settling in and no fight for the suit
		WebSymbioteState now = WebSymbiote.state(player);
		WebSymbiote.set(player, new WebSymbioteState(true, Math.max(jarred.satiety(), WebSymbiote.SATIETY_START), 0, 0, WebSymbioteState.SUIT,
			now.hints(), Mth.clamp(jarred.bond(), 0, WebSymbiote.BOND_MAX), jarred.strain()));
		WebSymbioteBond.clear(player);
		if (!keeper) {
			WebSymbioteMind.addTrust(player, -TRUST_LOSS_STRANGER);
		}
		WebSymbiote.whisper(player, (keeper ? "jar.back.keeper." : "jar.back.stranger.") + (1 + player.getRandom().nextInt(LINES)));
		stack.shrink(1);
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		if (!player.getInventory().add(bottle)) {
			player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 0.6F);
		return InteractionResult.SUCCESS;
	}

	/** The jarred symbiote: use it to let it back in. */
	public static final class JarItem extends Item {
		JarItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer server) {
				return letIn(server, player.getItemInHand(hand));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			Jarred jarred = stack.get(JARRED);
			if (jarred != null) {
				builder.accept(Component.translatable("item.jaymospowers.jarred_symbiote.kind." + WebSymbioteStrain.of(jarred.strain()).name().toLowerCase(Locale.ROOT))
					.withStyle(ChatFormatting.GRAY));
			}
			builder.accept(Component.translatable("item.jaymospowers.jarred_symbiote.hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
