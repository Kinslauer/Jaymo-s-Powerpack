package com.jaymoskingdoms.client.inhabitants.fts;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.geckolib.constant.dataticket.DataTicket;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.disasters.CalamitiesClient;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import com.jaymoskingdoms.inhabitants.fts.Bulldrogioth;
import com.jaymoskingdoms.inhabitants.fts.NetherBeasts;
import com.jaymoskingdoms.inhabitants.fts.Nehemoth;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The client's side of the beasts out of the demons' country (From the Shadows Reborn, AGPL v3; see
 * licenses/from-the-shadows/CREDITS-nether-beasts.md): their renderers, with the eyes glowing in the dark, and the
 * camera shake of a roar, a landing and a charged beam, read straight from the beast's synced act (no packet), and fed
 * to the rattle Kingdoms already has for earthquakes ({@link CalamitiesClient#quake}).
 */
public final class NetherBeastsClient {
	static final float NEHEMOTH_SCALE = 1.2F, NEHEMOTH_SHADOW = 1.5F;
	/** Camera shake in degrees at the beast's feet, thinning to nothing at this many blocks: a roar, a landing, a beam. */
	static final float ROAR_SHAKE = 1.6F, LAND_SHAKE = 1.4F, BEAM_SHAKE = 0.5F;
	static final double ROAR_RANGE = 50.0, LAND_RANGE = 15.0, BEAM_RANGE = 30.0;

	static final float BULLDROGIOTH_SCALE = 1.7F, BULLDROGIOTH_SHADOW = 1.7F, SLAM_SHAKE = 0.8F;
	static final double SLAM_RANGE = 12.0;
	static final Identifier[] SHELLS = {JaymosKingdoms.id("textures/entity/bulldrogioth/crimson.png"),
		JaymosKingdoms.id("textures/entity/bulldrogioth/warped.png"), JaymosKingdoms.id("textures/entity/bulldrogioth/basalt.png")};
	static final DataTicket<Integer> SHELL = DataTicket.create("jaymoskingdoms_bulldrogioth_shell", Integer.class);

	static final DataTicket<Integer> VARIANT = DataTicket.create("jaymoskingdoms_nehemoth_variant", Integer.class);
	static final Identifier FIRE = JaymosKingdoms.id("textures/entity/nehemoth/fire.png");
	static final Identifier SOUL = JaymosKingdoms.id("textures/entity/nehemoth/soul.png");

	private NetherBeastsClient() {
	}

	public static void register() {
		EntityRenderers.register(NetherBeasts.NEHEMOTH, NehemothRenderer::new);
		EntityRenderers.register(NetherBeasts.BULLDROGIOTH, BulldrogiothRenderer::new);
		EntityRenderers.register(NetherBeasts.CORAL_THORN, ThrownItemRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(NetherBeastsClient::shake);
	}

	/** The fire kind or the soul kind, with the eyes (each texture's {@code _glowmask}) glowing. */
	static final class NehemothRenderer extends InhabitantRenderer<Nehemoth> {
		NehemothRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<Nehemoth>(JaymosKingdoms.id("nehemoth")) {
				@Override
				public Identifier getTextureResource(GeoRenderState state) {
					return state.getOrDefaultGeckolibData(VARIANT, 0) == 1 ? SOUL : FIRE;
				}
			}, NEHEMOTH_SCALE, NEHEMOTH_SHADOW, "head");
			this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void addRenderData(Nehemoth nehemoth, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(nehemoth, related, state, partialTick);
			state.addGeckolibData(VARIANT, nehemoth.variant());
		}
	}

	/** Its shell in the colour of where it was born, its eyes glowing, and no tipping over when it dies (its death is animated). */
	static final class BulldrogiothRenderer extends InhabitantRenderer<Bulldrogioth> {
		BulldrogiothRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<Bulldrogioth>(JaymosKingdoms.id("bulldrogioth")) {
				@Override
				public Identifier getTextureResource(GeoRenderState state) {
					return SHELLS[Mth.clamp(state.getOrDefaultGeckolibData(SHELL, 0), 0, SHELLS.length - 1)];
				}
			}, BULLDROGIOTH_SCALE, BULLDROGIOTH_SHADOW, "head");
			this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void addRenderData(Bulldrogioth bulldrogioth, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(bulldrogioth, related, state, partialTick);
			state.addGeckolibData(SHELL, bulldrogioth.variant());
		}

		@Override
		protected float getDeathMaxRotation(GeoRenderState state) {
			return 0.0F;
		}
	}

	private static void shake(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		float strongest = 0.0F;
		for (Nehemoth n : minecraft.level.getEntitiesOfClass(Nehemoth.class, minecraft.player.getBoundingBox().inflate(ROAR_RANGE))) {
			double d = n.distanceTo(minecraft.player);
			int age = n.actAge();
			float shake = 0.0F;
			switch (n.act()) {
				case ROAR -> shake = age >= 8 && age < 38 ? ROAR_SHAKE * fall(d, ROAR_RANGE) : 0.0F;
				case LAND -> shake = age < 10 ? LAND_SHAKE * fall(d, LAND_RANGE) : 0.0F;
				case BEAM -> shake = age >= 12 && age < 34 ? BEAM_SHAKE * fall(d, BEAM_RANGE) : 0.0F;
				default -> {
				}
			}
			strongest = Math.max(strongest, shake);
		}
		for (Bulldrogioth b : minecraft.level.getEntitiesOfClass(Bulldrogioth.class, minecraft.player.getBoundingBox().inflate(SLAM_RANGE))) {
			if (b.act() == Bulldrogioth.Act.SLAM && b.actAge() >= 32 && b.actAge() < 40) {
				strongest = Math.max(strongest, SLAM_SHAKE * fall(b.distanceTo(minecraft.player), SLAM_RANGE));
			}
		}
		if (strongest > 0.0F) {
			CalamitiesClient.quake = Math.max(CalamitiesClient.quake, strongest);
		}
	}

	private static float fall(double distance, double range) {
		return (float)Mth.clamp(1.0 - distance / range, 0.0, 1.0);
	}
}
