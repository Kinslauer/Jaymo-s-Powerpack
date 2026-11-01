package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.power.zfighter.ZFighterAndroid;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wounds, phase 4 (0.14.4): treating a body, all medieval. Andy (2026-10-20): "I dont want any modern medicine".
 * <ul>
 * <li><b>Using it:</b> use a medicine item ({@link MedicineItems}) in the air for your own body, or on another creature,
 * and the body screen ({@link OpenBody}) opens to pick the part; sneak + use treats the worst part it can help, with no
 * screen. Smelling salts, leeches and poppy (given to another) treat the whole body. Strong spirits (Kitchen's), a
 * honey bottle and a hot blade (cautery) work only with sneak + use: on another, or on yourself while looking down.</li>
 * <li><b>On someone else, hold use</b> (0.15.99, Andy: "you can hold right click with the item on them and it will play
 * and kneeling interaction animation, then apply after a short hold"): held past {@link Downed#TAP_TICKS}, you kneel at
 * their side ({@link Tending}) and treat the worst part (or the whole body) while you hold; letting go stops it. A tap
 * with a bandage, rag, splint or needle opens the body screen instead, to pick the part. Spirits, honey and a hot blade
 * still need sneak (a blade without it is a weapon: a downed enemy's finisher), as before.</li>
 * <li><b>It takes time:</b> each remedy is a channel of {@link Remedy#ticks} with a bar over the hotbar, cancelled by
 * either of you moving more than {@link #MOVE_LIMIT}, by either being hit, by the medicine leaving your hand, or (sneak +
 * use) by letting go of sneak. All on the server: the screen's packet only names the part.</li>
 * <li><b>Cautery:</b> a sword or axe held to a lit campfire, fire or lava is red-hot ({@link #HEATED_KEY}, custom data)
 * for {@link #HEAT_TICKS}; a Fire Aspect blade always is. It stops all bleeding on a part at once (the only thing that
 * closes a stump) and burns it for {@link #CAUTERY_BURN}.</li>
 * </ul>
 */
public final class Treatment {
	/** (0.14.4) A linen bandage heals its part this much (health points) over this long (ticks); a rag half as much. */
	public static final float BANDAGE_HEAL = 3.0F, RAG_HEAL = 1.5F;
	public static final int BANDAGE_TICKS = 200;
	/** (0.14.4) A stitch hurts its part this much; cautery burns it this much. */
	public static final float NEEDLE_HURT = 1.0F, CAUTERY_BURN = 2.0F;
	/**
	 * (0.17.93, Andy: androids "use metal parts to repair instead of bandages") An iron or copper ingot worked into an
	 * android's part: its leak stops, a break is set, and it mends this much (health points) at once.
	 */
	public static final float METAL_HEAL = 4.0F;
	/** (0.14.4) Leeches' cost (health off the body). */
	public static final float LEECH_COST = 1.0F;
	/** (0.14.4) Smelling salts: up with this much health (half a heart in head and body), Speed I this long, then dazed (Nausea) this long. */
	public static final float SALTS_HEALTH = 2.0F;
	public static final int SALTS_SPEED_TICKS = 100, SALTS_DAZE_TICKS = 160;
	/** (0.14.4) Smelling salts on a player who has passed out: the chance they come to (the whole vial goes either way). */
	public static final float ROUSE_CHANCE = 0.4F;
	/** (0.14.4) How long a blade stays red-hot from the fire (ticks). */
	public static final int HEAT_TICKS = 600;
	/** (0.14.4) Looking at least this far down (degrees), sneak + use with spirits, honey or a hot blade is on yourself. */
	public static final float SELF_PITCH = 60.0F;
	/** (0.14.4) Either of you moving this far (blocks) spoils the treatment; how far past reach you may lean. */
	public static final double MOVE_LIMIT = 0.5, REACH_SLACK = 0.5;
	/** (0.14.4) How often your hands are seen at work (ticks), and the bar's length. */
	static final int WORK_TICKS = 15, BAR = 12;
	/** (0.14.4) The custom-data key a red-hot blade carries (the game time it was heated). */
	public static final String HEATED_KEY = "jaymospowers:heated";
	/** (0.18.45) Told of every treatment a player finishes on someone else. Jaymo's Kingdoms adds one (by reflection): a villager remembers who dressed their wounds. */
	public static final List<BiConsumer<ServerPlayer, LivingEntity>> TENDED_LISTENERS = new ArrayList<>();

	/** (0.14.4) What a medicine does, and how long it takes (ticks); {@code part}: to one part (else the whole body). */
	public enum Remedy {
		BANDAGE(40, true), RAG(40, true), SPLINT(60, true), NEEDLE(60, true), CAUTERY(20, true), SPIRITS(20, true), HONEY(20, true),
		SALTS(20, false), LEECHES(40, false), POPPY(40, false),
		/** (0.17.93) Metal parts: an android's only repair (sneak + use, like spirits). */
		METAL(40, true),
		/**
		 * (0.19.24) The herbal remedies (docs/Healing-Plan.md): yarrow powder packed into a cut, a knitbone poultice on a
		 * break, a burn salve, a garlic poultice (cleans like honey, and wards off festering), and a clean rag (washed or
		 * boiled: dresses like a rag but never festers).
		 */
		YARROW(40, true), KNITBONE(60, true), SALVE(40, true), GARLIC(40, true), CLEAN_RAG(40, true);

		public final int ticks;
		public final boolean part;

		Remedy(int ticks, boolean part) {
			this.ticks = ticks;
			this.part = part;
		}

		public String key() {
			return "remedy.jaymospowers." + this.name().toLowerCase(java.util.Locale.ROOT);
		}

		/** One of our own medicine items (the body screen is theirs). */
		boolean screen() {
			return this == BANDAGE || this == RAG || this == SPLINT || this == NEEDLE || this == YARROW || this == KNITBONE
				|| this == SALVE || this == GARLIC || this == CLEAN_RAG;
		}
	}

	/** A part's state on the body screen ({@link OpenBody#states}). */
	public static final int BROKEN = 1, BLEEDING = 2, FESTERING = 4, SPLINTED = 8, BANDAGED = 16, MISSING = 32, HELPS = 64;

	/**
	 * (0.14.4) Server → client: open the body screen on {@code entity} (its name), its {@code parts} (by
	 * {@link BodyPart#ordinal()}) with their {@code fractions} and {@code states} ({@link #BROKEN}...), and {@code plan}
	 * ({@link BodyPlan#ordinal()}). (0.15.18) {@code look}: opened from the inventory to look, not with medicine.
	 */
	public record OpenBody(int entity, String name, int plan, List<Integer> parts, List<Float> fractions, List<Integer> states, boolean look)
		implements CustomPacketPayload {
		public static final Type<OpenBody> TYPE = new Type<>(JaymosPowers.id("wounds_open_body"));
		public static final StreamCodec<ByteBuf, OpenBody> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenBody::entity,
			ByteBufCodecs.STRING_UTF8, OpenBody::name,
			ByteBufCodecs.VAR_INT, OpenBody::plan,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OpenBody::parts,
			ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), OpenBody::fractions,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OpenBody::states,
			ByteBufCodecs.BOOL, OpenBody::look,
			OpenBody::new);

		@Override
		public Type<OpenBody> type() {
			return TYPE;
		}
	}

	/** (0.14.4) Client → server: treat {@code part} (ordinal) of {@code entity} with the medicine in hand. */
	public record Treat(int entity, int part) implements CustomPacketPayload {
		public static final Type<Treat> TYPE = new Type<>(JaymosPowers.id("wounds_treat"));
		public static final StreamCodec<ByteBuf, Treat> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Treat::entity,
			ByteBufCodecs.VAR_INT, Treat::part,
			Treat::new);

		@Override
		public Type<Treat> type() {
			return TYPE;
		}
	}

	/** (0.15.18) Client → server: show me my own body (the inventory's body button). */
	public record LookBody() implements CustomPacketPayload {
		public static final Type<LookBody> TYPE = new Type<>(JaymosPowers.id("wounds_look_body"));
		public static final StreamCodec<ByteBuf, LookBody> CODEC = StreamCodec.unit(new LookBody());

		@Override
		public Type<LookBody> type() {
			return TYPE;
		}
	}

	/** A treatment under way: on whom, which part (null: the whole body), with what, from which hand, from where, how far along. */
	private static final class Session {
		final LivingEntity target;
		@Nullable BodyPart part;
		final Remedy remedy;
		final InteractionHand hand;
		final Vec3 from, targetFrom;
		final boolean sneak;
		/** (0.15.49) How long it takes this healer (Steady Hands, {@link MedicineSkill}). */
		final int need;
		int ticks;
		/**
		 * (0.15.99) Held use on someone else: {@code start}, when use went down, and {@code held}, when it was last seen
		 * held (game times). Not {@code begun} till it's held past a tap; a tap opens the body screen. {@code over}: done (or
		 * nothing to do), kept till use is let go so holding on doesn't start it again.
		 */
		final boolean hold;
		final long start;
		long held;
		boolean begun, over;

		Session(LivingEntity target, @Nullable BodyPart part, Remedy remedy, InteractionHand hand, Vec3 from, boolean sneak, int need,
			boolean hold, long now) {
			this.target = target;
			this.part = part;
			this.remedy = remedy;
			this.hand = hand;
			this.from = from;
			this.targetFrom = target.position();
			this.sneak = sneak;
			this.need = need;
			this.hold = hold;
			this.start = now;
			this.held = now;
			this.begun = !hold;
		}
	}

	/** Player → their treatment. Not saved. */
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** Our use-entity phase: after Downed's (which passes medicine through), before everyone else's. */
	private static final Identifier PHASE = JaymosPowers.id("medicine");

	private Treatment() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenBody.TYPE, OpenBody.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Treat.TYPE, Treat.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Treat.TYPE, (payload, ctx) -> picked(ctx.player(), payload.entity(), payload.part()));
		PayloadTypeRegistry.serverboundPlay().register(LookBody.TYPE, LookBody.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LookBody.TYPE, (payload, ctx) -> look(ctx.player()));
		UseEntityCallback.EVENT.addPhaseOrdering(Downed.FIRST, PHASE);
		UseEntityCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(PHASE, Treatment::onUseEntity);
		UseItemCallback.EVENT.register(Treatment::onUseItem);
		UseBlockCallback.EVENT.register(Treatment::onUseBlock);
		// a blow to either of you spoils it
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (SESSIONS.isEmpty() || damageTaken <= 0.0F) {
				return;
			}
			for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
				Map.Entry<UUID, Session> e = it.next();
				Session s = e.getValue();
				if (s.target == entity || entity instanceof Player player && player.getUUID().equals(e.getKey())) {
					it.remove();
					if (s.over) {
						continue;
					}
					ServerPlayer healer = entity.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(e.getKey()) : null;
					if (healer != null) {
						stopped(healer, s);
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Treatment::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	// ---- what's in hand ---------------------------------------------------------------------------------------------------

	/** (0.14.4) The remedy {@code stack} is, or null: our kit, Kitchen's strong spirits, a honey bottle, or a red-hot blade. */
	public static @Nullable Remedy remedy(ItemStack stack, Level level) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.getItem() instanceof MedicineItems.MedicineItem medicine) {
			return medicine.remedy();
		}
		if (stack.is(MedicineItems.POPPY_SYRUP)) {
			return Remedy.POPPY;
		}
		if (stack.is(Items.HONEY_BOTTLE)) {
			return Remedy.HONEY;
		}
		if (KitchenCompat.isStrongSpirits(stack)) {
			return Remedy.SPIRITS;
		}
		if (stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT)) {
			return Remedy.METAL;
		}
		return hot(stack, level) ? Remedy.CAUTERY : null;
	}

	/**
	 * (0.15.98) Whether {@code stack} is medicine on a plain use (no sneak): our kit with its body screen, or the poppy. Spirits,
	 * honey and a red-hot blade are only medicine with sneak, so a plain hold with them is a finisher's ({@code Finishers}).
	 */
	public static boolean treatsOnPlainUse(ItemStack stack, Level level) {
		Remedy remedy = remedy(stack, level);
		return remedy != null && !(remedy.part && !remedy.screen());
	}

	/** (0.14.4) A blade hot enough to sear: heated in a fire within {@link #HEAT_TICKS}, or with Fire Aspect on it. */
	public static boolean hot(ItemStack stack, Level level) {
		if (!Dismember.blade(stack)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			long heated = data.copyTag().getLongOr(HEATED_KEY, 0L);
			if (heated > 0L && level.getGameTime() - heated < HEAT_TICKS && level.getGameTime() >= heated) {
				return true;
			}
		}
		HolderLookup.RegistryLookup<Enchantment> lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments on = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		return lookup.get(Enchantments.FIRE_ASPECT).map(holder -> on.getLevel(holder) > 0).orElse(false);
	}

	private static void heat(ItemStack stack, long now) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.putLong(HEATED_KEY, now);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void cool(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		CompoundTag tag = data.copyTag();
		if (tag.getLongOr(HEATED_KEY, 0L) == 0L) {
			return;
		}
		tag.putLong(HEATED_KEY, 0L);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	// ---- what a remedy can help ---------------------------------------------------------------------------------------------

	/** (0.17.93) An android's body: mended with metal, and nothing else works on it. */
	static boolean machine(LivingEntity body) {
		return body instanceof Player player && ZFighterAndroid.android(player);
	}

	/** Whether {@code remedy} can do anything for {@code part} of {@code body}. */
	static boolean helps(Remedy remedy, LivingEntity body, BodyPart part) {
		BodyPlan plan = BodyPlan.of(body);
		if (plan == null || plan.index(part) < 0 || machine(body) != (remedy == Remedy.METAL)) {
			return false;
		}
		int bit = Breaks.bit(part);
		boolean missing = Breaks.missing(body, part);
		boolean bleeding = (Bleeding.bleeding(body) & bit) != 0;
		return switch (remedy) {
			case BANDAGE, RAG, CLEAN_RAG -> !missing && (bleeding || Wounds.fraction(body, part) < 0.999F) && !Bleeding.dressed(body, part);
			case YARROW -> bleeding && !missing;
			case KNITBONE -> Herbal.knittable(body, part);
			case SALVE -> Herbal.salvable(body, part);
			case GARLIC -> ((Bleeding.festering(body) | Bleeding.dirty(body) | Bleeding.bleeding(body)) & bit) != 0;
			case SPLINT -> Breaks.splintable(body, part);
			case NEEDLE -> bleeding && !missing;
			case CAUTERY -> bleeding;
			case SPIRITS, HONEY -> ((Bleeding.festering(body) | Bleeding.dirty(body) | Bleeding.bleeding(body)) & bit) != 0;
			case METAL -> !missing && (bleeding || Breaks.splintable(body, part) || Wounds.fraction(body, part) < 0.999F);
			default -> false;
		};
	}

	/** Whether {@code remedy} (a whole-body one) can do anything for {@code body} given by {@code healer}. */
	static boolean helpsBody(Remedy remedy, Player healer, LivingEntity body) {
		if (machine(body)) {
			return false;
		}
		return switch (remedy) {
			case SALTS -> body != healer && Downed.isDowned(body);
			case LEECHES -> BodyPlan.of(body) != null;
			case POPPY -> body != healer && BodyPlan.of(body) != null;
			default -> false;
		};
	}

	/** The worst part {@code remedy} can help: a stump, then festering, then a cut, then the least whole. Null if none. */
	static @Nullable BodyPart worst(Remedy remedy, LivingEntity body) {
		BodyPlan plan = BodyPlan.of(body);
		if (plan == null) {
			return null;
		}
		BodyPart best = null;
		float bestScore = Float.MAX_VALUE;
		int stumps = Bleeding.stumps(body);
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (!helps(remedy, body, part)) {
				continue;
			}
			int bit = Breaks.bit(part);
			float score = Wounds.fraction(body, part);
			if ((stumps & bit) != 0) {
				score -= 30.0F;
			} else if ((Bleeding.festering(body) & bit) != 0) {
				score -= 20.0F;
			} else if ((Bleeding.bleeding(body) & bit) != 0) {
				score -= 10.0F;
			}
			if (score < bestScore) {
				bestScore = score;
				best = part;
			}
		}
		return best;
	}

	/**
	 * (0.17.9) Someone else's hands (Kingdoms' fight-pit attendant, Andy 2026-10-26: "if you accept he does it for you,
	 * bandaging wherever you may be hurt, or splinting"): every hurt part of {@code body} dressed with a rag or a linen
	 * bandage, every break that can be splinted splinted. Returns how many parts were seen to.
	 */
	public static int tendAll(LivingEntity body, boolean rag) {
		BodyPlan plan = BodyPlan.of(body);
		if (plan == null) {
			return 0;
		}
		Remedy dressing = rag ? Remedy.RAG : Remedy.BANDAGE;
		int seen = 0;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (helps(Remedy.SPLINT, body, part) && Breaks.splint(body, part)) {
				seen++;
			}
			if (helps(dressing, body, part)) {
				Bleeding.stop(body, part, false);
				Bleeding.dress(body, part, rag ? RAG_HEAL : BANDAGE_HEAL, BANDAGE_TICKS, rag);
				seen++;
			}
		}
		return seen;
	}

	/** (0.14.4) Downed's revive steps aside: sneak + use with a remedy that can help this downed one (server side). */
	static boolean claimsDowned(Player player, InteractionHand hand, Entity entity) {
		if (!player.isShiftKeyDown() || !(entity instanceof LivingEntity body)) {
			return false;
		}
		Remedy remedy = remedy(player.getItemInHand(hand), player.level());
		if (remedy == null) {
			return false;
		}
		return remedy.part ? worst(remedy, body) != null : helpsBody(remedy, player, body);
	}

	// ---- starting ---------------------------------------------------------------------------------------------------------

	/** A medicine item used in the air ({@link MedicineItems.MedicineItem}): the body screen on yourself, or sneak: your worst part. */
	static void useOnSelf(ServerPlayer player, InteractionHand hand, Remedy remedy) {
		if (Downed.isDowned(player)) {
			return;
		}
		if (!remedy.part) {
			if (!helpsBody(remedy, player, player)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.not_on_self", Component.translatable(remedy.key()))
					.withStyle(ChatFormatting.GRAY));
				return;
			}
			start(player, player, null, remedy, hand, false);
			return;
		}
		if (player.isShiftKeyDown()) {
			startWorst(player, player, remedy, hand, true);
		} else {
			open(player, player, remedy, false);
		}
	}

	private static void startWorst(ServerPlayer player, LivingEntity body, Remedy remedy, InteractionHand hand, boolean sneak) {
		BodyPart part = worst(remedy, body);
		if (part == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.nothing_to_treat", body.getDisplayName(),
				Component.translatable(remedy.key())).withStyle(ChatFormatting.GRAY));
			return;
		}
		start(player, body, part, remedy, hand, sneak);
	}

	private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (Downed.isDowned(player) || player.isSpectator() || !(entity instanceof LivingEntity body) || BodyPlan.of(body) == null) {
			return InteractionResult.PASS;
		}
		Remedy remedy = remedy(player.getItemInHand(hand), world);
		// (0.17.93) an ingot is only medicine to an android (an iron golem's repair, or a trade, goes on as ever)
		if (remedy == null || remedy == Remedy.METAL && !machine(body)) {
			return InteractionResult.PASS;
		}
		boolean sneak = player.isShiftKeyDown();
		// spirits, honey and a blade are only medicine with sneak (else they're drunk, or swung)
		if (!sneak && remedy.part && !remedy.screen()) {
			return InteractionResult.PASS;
		}
		if (world.isClientSide() || !(player instanceof ServerPlayer server)) {
			return InteractionResult.SUCCESS;
		}
		if (Downed.isDowned(body) && sneak && !claimsDowned(player, hand, body)) {
			// (nothing it can do for them: Downed's revive takes it, but it ran first and passed; a revive needs the hold anyway)
			return InteractionResult.PASS;
		}
		long now = world.getGameTime();
		Session old = SESSIONS.get(server.getUUID());
		if (old != null && !old.hold && old.target == body) {
			// (already at a part picked on the body screen)
			return InteractionResult.SUCCESS;
		}
		if (old != null && old.hold && old.target == body && old.hand == hand && old.remedy == remedy) {
			// (0.15.99) still holding use on them (the client repeats it while held)
			old.held = now;
			return InteractionResult.SUCCESS;
		}
		// (0.15.99) held, it's the worst part (or the whole body) with you kneeling at their side; a tap, the body screen
		SESSIONS.put(server.getUUID(), new Session(body, null, remedy, hand, server.position(), sneak, Satchel.shorten(server, MedicineSkill.ticks(server, remedy.ticks)), true, now));
		return InteractionResult.SUCCESS;
	}

	/** (0.15.99) Use let go on someone before it was a hold: the body screen for our kit, else how it's done. */
	private static void tapped(ServerPlayer player, Session s) {
		if (s.remedy.screen()) {
			open(player, s.target, s.remedy, false);
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.hold_to_treat", Component.translatable(s.remedy.key()),
				s.target.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
	}

	/** (0.15.99) A hold on someone, held past a tap: settle what it treats and begin. False if there's nothing it can do. */
	private static boolean begin(ServerPlayer player, Session s) {
		LivingEntity body = s.target;
		if (s.remedy.part) {
			s.part = worst(s.remedy, body);
		}
		if (s.remedy.part ? s.part == null : !helpsBody(s.remedy, player, body)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.nothing_to_treat", body.getDisplayName(),
				Component.translatable(s.remedy.key())).withStyle(ChatFormatting.GRAY));
			return false;
		}
		s.begun = true;
		announce(player, body, s.part, s.remedy);
		return true;
	}

	/** Spirits, honey or a hot blade, sneak + use looking down: on yourself. */
	private static InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
		if (!player.isShiftKeyDown() || player.getXRot() < SELF_PITCH || Downed.isDowned(player)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() instanceof MedicineItems.MedicineItem) {
			// (their own use does it)
			return InteractionResult.PASS;
		}
		Remedy remedy = remedy(stack, world);
		if (remedy != Remedy.SPIRITS && remedy != Remedy.HONEY && remedy != Remedy.CAUTERY && !(remedy == Remedy.METAL && machine(player))) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			startWorst(server, server, remedy, hand, true);
		}
		return InteractionResult.SUCCESS;
	}

	/** A blade held to a lit campfire or a fire (or, sneaking, to the face of a block a fire or lava touches) comes away red-hot. */
	private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		// (the fire itself, or, sneaking, a block a fire burns against: an axe on any other log still strips it)
		if (player.isSpectator() || !Dismember.blade(stack) || !fiery(world, hit.getBlockPos())
			&& !(player.isShiftKeyDown() && fiery(world, hit.getBlockPos().relative(hit.getDirection())))) {
			return InteractionResult.PASS;
		}
		if (world instanceof ServerLevel level) {
			heat(stack, level.getGameTime());
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
			BlockPos pos = hit.getBlockPos();
			level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.01);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.heated", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean fiery(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return CampfireBlock.isLitCampfire(state) || state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA);
	}

	/**
	 * (0.15.18) The inventory's body button: the player's own body screen, to look. Medicine for a part in hand still
	 * marks what it could help, so they can treat from there.
	 */
	private static void look(ServerPlayer player) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		Remedy held = null;
		for (InteractionHand hand : InteractionHand.values()) {
			Remedy remedy = remedy(player.getItemInHand(hand), player.level());
			if (remedy != null && remedy.part) {
				held = remedy;
				break;
			}
		}
		open(player, player, held, true);
	}

	/** The body screen, for {@code body}, with what {@code remedy} (if any) could help marked. */
	private static void open(ServerPlayer player, LivingEntity body, @Nullable Remedy remedy, boolean look) {
		BodyPlan plan = BodyPlan.of(body);
		if (plan == null || !ServerPlayNetworking.canSend(player, OpenBody.TYPE)) {
			if (remedy == null) {
				return;
			}
			// (no screen to show: the worst part, as sneak + use)
			InteractionHand hand = hand(player, remedy);
			if (hand != null) {
				startWorst(player, body, remedy, hand, false);
			}
			return;
		}
		List<Integer> parts = new ArrayList<>();
		List<Float> fractions = new ArrayList<>();
		List<Integer> states = new ArrayList<>();
		int broken = Breaks.broken(body), splinted = Breaks.splinted(body), bleeding = Bleeding.bleeding(body), festering = Bleeding.festering(body);
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			int bit = Breaks.bit(part);
			int state = 0;
			state |= (broken & bit) != 0 ? BROKEN : 0;
			state |= (bleeding & bit) != 0 ? BLEEDING : 0;
			state |= (festering & bit) != 0 ? FESTERING : 0;
			state |= (splinted & bit) != 0 ? SPLINTED : 0;
			state |= Bleeding.dressed(body, part) ? BANDAGED : 0;
			state |= Breaks.missing(body, part) ? MISSING : 0;
			state |= remedy != null && helps(remedy, body, part) ? HELPS : 0;
			parts.add(part.ordinal());
			fractions.add(Wounds.fraction(body, part));
			states.add(state);
		}
		ServerPlayNetworking.send(player, new OpenBody(body.getId(), body.getDisplayName().getString(), plan.ordinal(), parts, fractions, states, look));
	}

	/** The hand holding {@code remedy} (the main hand first). */
	private static @Nullable InteractionHand hand(Player player, Remedy remedy) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (remedy(player.getItemInHand(hand), player.level()) == remedy) {
				return hand;
			}
		}
		return null;
	}

	/** A part picked on the body screen ({@link Treat}): checked again here, then treated. */
	private static void picked(ServerPlayer player, int id, int ordinal) {
		if (ordinal < 0 || ordinal >= BodyPart.values().length || Downed.isDowned(player) || !player.isAlive()) {
			return;
		}
		Entity entity = player.level().getEntity(id);
		if (!(entity instanceof LivingEntity body) || !body.isAlive() || body != player && !player.isWithinEntityInteractionRange(body, REACH_SLACK)) {
			return;
		}
		BodyPart part = BodyPart.values()[ordinal];
		for (InteractionHand hand : InteractionHand.values()) {
			Remedy remedy = remedy(player.getItemInHand(hand), player.level());
			if (remedy != null && remedy.screen()) {
				if (helps(remedy, body, part)) {
					start(player, body, part, remedy, hand, false);
				} else {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.cannot_help", part.displayName(),
						Component.translatable(remedy.key())).withStyle(ChatFormatting.GRAY));
				}
				return;
			}
		}
	}

	private static void start(ServerPlayer player, LivingEntity body, @Nullable BodyPart part, Remedy remedy, InteractionHand hand, boolean sneak) {
		Session old = SESSIONS.get(player.getUUID());
		if (old != null && old.target == body && old.part == part && old.remedy == remedy) {
			// (already at it: use repeats while held)
			return;
		}
		SESSIONS.put(player.getUUID(), new Session(body, part, remedy, hand, player.position(), sneak, Satchel.shorten(player, MedicineSkill.ticks(player, remedy.ticks)), false,
			player.level().getGameTime()));
		announce(player, body, part, remedy);
	}

	private static void announce(ServerPlayer player, LivingEntity body, @Nullable BodyPart part, Remedy remedy) {
		Component what = part == null ? body.getDisplayName() : part.displayName();
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.treating", Component.translatable(remedy.key()), what)
			.withStyle(ChatFormatting.GRAY));
		if (body instanceof ServerPlayer patient && patient != player) {
			patient.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.treated_by", player.getDisplayName(),
				Component.translatable(remedy.key())).withStyle(ChatFormatting.GRAY));
		}
	}

	private static void stopped(ServerPlayer player, Session s) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.treat_stopped").withStyle(ChatFormatting.GRAY));
	}

	// ---- the channel ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		// (a copy: finishing one can hurt someone, and a hurt ends sessions)
		for (Map.Entry<UUID, Session> e : new ArrayList<>(SESSIONS.entrySet())) {
			UUID id = e.getKey();
			Session s = e.getValue();
			if (SESSIONS.get(id) != s) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive() || Downed.isDowned(player) || !(player.level() instanceof ServerLevel level)) {
				SESSIONS.remove(id);
				continue;
			}
			LivingEntity body = s.target;
			if (s.hold) {
				// (0.15.99) held use on someone: let go, it stops (or, a tap, the body screen)
				if (level.getGameTime() - s.held > Downed.REVIVE_GRACE) {
					SESSIONS.remove(id);
					if (s.begun && !s.over) {
						stopped(player, s);
					} else if (s.held - s.start < Downed.TAP_TICKS && body.isAlive() && player.isWithinEntityInteractionRange(body, REACH_SLACK)) {
						tapped(player, s);
					}
					continue;
				}
				if (s.over) {
					continue;
				}
				if (!s.begun) {
					if (s.held - s.start < Downed.TAP_TICKS) {
						continue;
					}
					if (!body.isAlive()) {
						SESSIONS.remove(id);
						continue;
					}
					if (!begin(player, s)) {
						s.over = true;
						continue;
					}
				}
			}
			ItemStack stack = player.getItemInHand(s.hand);
			boolean spoiled = !body.isAlive() || body.isRemoved() || body.level() != level
				|| remedy(stack, level) != s.remedy
				|| player.position().distanceTo(s.from) > MOVE_LIMIT
				|| body != player && (body.position().distanceTo(s.targetFrom) > MOVE_LIMIT || !player.isWithinEntityInteractionRange(body, REACH_SLACK))
				|| s.sneak && !player.isShiftKeyDown()
				|| s.part != null && !helps(s.remedy, body, s.part)
				|| s.part == null && !helpsBody(s.remedy, player, body);
			if (spoiled) {
				SESSIONS.remove(id);
				stopped(player, s);
				continue;
			}
			s.ticks++;
			// (0.15.99) down on one knee at their side
			Tending.keep(player, body);
			if (s.ticks % WORK_TICKS == 0) {
				player.swing(s.hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.5F,
					0.8F + level.getRandom().nextFloat() * 0.4F);
			}
			if (s.ticks < s.need) {
				if (s.ticks % 2 == 0) {
					int filled = Math.round(BAR * s.ticks / (float) s.need);
					String bar = "|".repeat(filled) + ".".repeat(BAR - filled);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.treating_bar", Component.translatable(s.remedy.key()), bar)
						.withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (s.hold) {
				// (held: let go before the next)
				s.over = true;
			} else {
				SESSIONS.remove(id);
			}
			apply(level, player, s, stack);
		}
	}

	/** The treatment done: what the remedy does, and what it costs. */
	private static void apply(ServerLevel level, ServerPlayer player, Session s, ItemStack stack) {
		LivingEntity body = s.target;
		BodyPart part = s.part;
		net.minecraft.world.item.Item used = stack.getItem();
		Component done = Component.translatable("message.jaymospowers.wounds.treated." + s.remedy.name().toLowerCase(java.util.Locale.ROOT),
			part == null ? body.getDisplayName() : part.displayName());
		switch (s.remedy) {
			case BANDAGE, RAG, CLEAN_RAG -> {
				boolean rag = s.remedy != Remedy.BANDAGE;
				Bleeding.stop(body, part, false);
				// (0.15.49) Field Dresser: a better dressing. (0.19.24) a clean rag looks like a rag but is not dirty
				Bleeding.dress(body, part, MedicineSkill.dressing(player, rag ? RAG_HEAL : BANDAGE_HEAL), BANDAGE_TICKS, s.remedy == Remedy.RAG, rag);
				stack.consume(1, player);
			}
			case YARROW -> {
				Bleeding.stop(body, part, false);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.2F);
				stack.consume(1, player);
			}
			case KNITBONE -> {
				Herbal.knit(body, part);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.2F);
				stack.consume(1, player);
			}
			case SALVE -> {
				Herbal.salve(body, part);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
				stack.consume(1, player);
			}
			case GARLIC -> {
				Bleeding.clean(body, part, Herbal.GARLIC_WARD_TICKS);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.7F, 0.8F);
				stack.consume(1, player);
			}
			case SPLINT -> {
				if (Breaks.splint(body, part) && MedicineSkill.bonesetter(player)) {
					// (0.15.49) Bonesetter: set well, the limb starts to mend at once
					Wounds.healPart(body, part, MedicineSkill.BONESETTER_HEAL);
				}
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
				stack.consume(1, player);
			}
			case NEEDLE -> {
				Bleeding.stop(body, part, false);
				Wounds.hurtPart(body, part, MedicineSkill.hurt(player, NEEDLE_HURT), null);
				stack.hurtAndBreak(1, player, s.hand);
			}
			case CAUTERY -> {
				Bleeding.stop(body, part, true);
				Wounds.hurtPart(body, part, MedicineSkill.hurt(player, CAUTERY_BURN), null);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
				level.sendParticles(ParticleTypes.SMOKE, body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(), 10, 0.2, 0.2, 0.2, 0.01);
				// (the flesh takes the heat out of it)
				cool(stack);
				stack.hurtAndBreak(1, player, s.hand);
			}
			case SPIRITS, HONEY -> {
				Bleeding.clean(body, part);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5F, 1.4F);
				stack.consume(1, player);
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
			}
			case SALTS -> {
				Pain.jolt(body);
				if (Downed.passedOut(body) && body instanceof Player) {
					// (a deep swoon: a chance, and the whole vial)
					stack.hurtAndBreak(stack.getMaxDamage() - stack.getDamageValue(), player, s.hand);
					// (0.15.49) Lifesaver: they always come to
					if (!MedicineSkill.lifesaver(player) && level.getRandom().nextFloat() >= ROUSE_CHANCE) {
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.salts_failed", body.getDisplayName())
							.withStyle(ChatFormatting.GRAY));
						return;
					}
				} else {
					stack.hurtAndBreak(1, player, s.hand);
				}
				if (Downed.rouse(level, body, SALTS_HEALTH)) {
					body.addEffect(new MobEffectInstance(MobEffects.SPEED, SALTS_SPEED_TICKS, 0));
					body.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SALTS_DAZE_TICKS, 0));
					MedicineSkill.broughtRound(player, body);
					if (body != player) {
						for (BiConsumer<ServerPlayer, LivingEntity> listener : Downed.BROUGHT_ROUND_LISTENERS) {
							listener.accept(player, body);
						}
					}
				}
			}
			case LEECHES -> {
				Wounds.hurtPart(body, BodyPart.BODY, LEECH_COST, null);
				Pain.leeches(body);
				if (MedicineSkill.bleedBadBlood(player, body)) {
					// (0.15.49) a Plague Doctor's leeches draw out the plague and poison
					level.sendParticles(ParticleTypes.SQUID_INK, body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
					done = Component.translatable("message.jaymospowers.wounds.leeches_bad_blood", body.getDisplayName());
				}
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 1.3F);
				stack.consume(1, player);
			}
			case METAL -> {
				Bleeding.stop(body, part, false);
				Breaks.splint(body, part);
				Wounds.healPart(body, part, METAL_HEAL);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5F, 1.6F);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
				stack.consume(1, player);
			}
			case POPPY -> {
				stack.consume(1, player);
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
				Pain.poppy(level, body);
			}
		}
		player.sendOverlayMessage(done.copy().withStyle(ChatFormatting.GREEN));
		// (0.19.24) the stack in hand ran out: the next of it comes from a healer's satchel
		if (player.getItemInHand(s.hand).isEmpty()) {
			Satchel.refill(player, s.hand, used);
		}
		// (0.15.49) a little Survival experience (Kingdoms)
		MedicineSkill.treated(player);
		if (body != player) {
			for (BiConsumer<ServerPlayer, LivingEntity> listener : TENDED_LISTENERS) {
				listener.accept(player, body);
			}
		}
	}
}
