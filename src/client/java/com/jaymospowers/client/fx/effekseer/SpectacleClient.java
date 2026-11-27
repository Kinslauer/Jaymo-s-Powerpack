package com.jaymospowers.client.fx.effekseer;

import Effekseer.swig.EffekseerBackendCore;
import Effekseer.swig.EffekseerEffectCore;
import Effekseer.swig.EffekseerManagerCore;
import Effekseer.swig.EffekseerTextureType;
import com.google.common.hash.Hashing;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

/** World-only port of AAA Particles' native loader, asset loader and world emitter. */
public final class SpectacleClient {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_EFFECTS = 32;
	private static final int MAX_SPRITES = 8192;
	private static final EffekseerTextureType[] TEXTURE_TYPES = {
		EffekseerTextureType.Color, EffekseerTextureType.Normal, EffekseerTextureType.Distortion
	};
	private static final ArrayList<MagicFx.Spectacle> PENDING = new ArrayList<>();
	private static final Map<Integer, Playing> PLAYING = new HashMap<>();
	private static final Map<String, EffekseerEffectCore> EFFECTS = new HashMap<>();
	private static final float[] VIEW = new float[16];
	private static final float[] PROJECTION = new float[16];

	private static Consumer<MagicFx.Shape> fallback;
	private static ClientLevel level;
	private static EffekseerManagerCore manager;
	private static SpectacleFramebuffer framebuffer;
	private static boolean nativeReady;
	private static boolean failed;
	private static boolean reset;
	private static boolean reload;
	private static int tick;
	private static long lastFrame;
	private static Vec3 origin;

	private record Playing(MagicFx.Shape fallback, int end) {}

	private SpectacleClient() {}

	public static void init(Consumer<MagicFx.Shape> drawFallback) {
		fallback = drawFallback;
		ClientPlayNetworking.registerGlobalReceiver(MagicFx.Spectacle.TYPE, (effect, context) -> {
			changeLevel(context.client().level);
			if (failed || PENDING.size() >= MAX_EFFECTS) {
				fallback.accept(effect.fallback());
			} else {
				PENDING.add(effect);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			changeLevel(client.level);
			if (!client.isPaused()) {
				tick++;
			}
		});
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JaymosPowers.id("effekseer"),
			(ResourceManagerReloadListener) resources -> reload = true);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
	}

	private static void changeLevel(ClientLevel next) {
		if (level != next) {
			level = next;
			PENDING.clear();
			PLAYING.clear();
			reset = true;
		}
	}

	public static void render(CameraRenderState camera, Matrix4f projection, RenderTarget target) {
		if (failed || PENDING.isEmpty() && PLAYING.isEmpty()) {
			return;
		}
		if (!(target.getColorTexture() instanceof GlTexture color) || !(target.getDepthTexture() instanceof GlTexture depth)) {
			useFallback();
			return;
		}
		try (var state = new SpectacleFramebuffer.GlState()) {
			if (manager == null) {
				initNative();
			}
			if (reset || reload) {
				if (reload) {
					PLAYING.values().forEach(playing -> fallback.accept(playing.fallback()));
				}
				manager.StopAllEffects();
				manager.Update(0);
				PLAYING.clear();
				lastFrame = 0;
				reset = false;
				if (reload) {
					clearAssets();
					reload = false;
				}
			}
			if (PLAYING.isEmpty()) {
				origin = camera.pos;
			}
			while (!PENDING.isEmpty()) {
				MagicFx.Spectacle effect = PENDING.getFirst();
				play(effect);
				PENDING.removeFirst();
			}
			if (!PLAYING.isEmpty()) {
				draw(camera, projection, target, color, depth);
			}
		} catch (IOException | RuntimeException | LinkageError ex) {
			LOGGER.warn("Effekseer is unavailable. Using MagicFx for spectacles.", ex);
			failed = true;
			useFallback();
		}
	}

	private static void initNative() throws IOException {
		String arch = System.getProperty("os.arch");
		boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
		String library = switch (Util.getPlatform()) {
			case WINDOWS -> x64 ? "EffekseerNativeForJava.dll" : "";
			case LINUX -> x64 ? "libEffekseerNativeForJava.so" : "";
			case OSX -> x64 ? "libEffekseerNativeForJava-intel.dylib"
				: arch.equals("aarch64") || arch.equals("arm64") ? "libEffekseerNativeForJava.dylib" : "";
			default -> "";
		};
		if (library.isEmpty()) {
			throw new IOException("No Effekseer native for " + Util.getPlatform() + " / " + arch);
		}

		// Natives come directly from this mod's jar, outside the resource pack stack.
		FabricLoader loader = FabricLoader.getInstance();
		Path source = loader.getModContainer("jaymospowers").orElseThrow()
			.findPath("assets/jaymospowers/effekseer/" + library).orElseThrow();
		byte[] nativeBytes = Files.readAllBytes(source);
		Path nativeDir = loader.getGameDir().resolve("natives").resolve("jaymos-effekseer")
			.resolve(Hashing.sha256().hashBytes(nativeBytes).toString());
		Files.createDirectories(nativeDir);
		Path local = nativeDir.resolve(library);
		if (!Files.isRegularFile(local)) {
			Path staging = Files.createTempFile(nativeDir, "effekseer-", ".tmp");
			try {
				Files.write(staging, nativeBytes);
				try {
					Files.move(staging, local);
				} catch (FileAlreadyExistsException concurrentInstall) {
					// Another client finished extracting the same library.
				}
			} finally {
				Files.deleteIfExists(staging);
			}
		}
		if (Files.mismatch(source, local) != -1) {
			throw new IOException("Unexpected contents in " + local);
		}
		System.load(local.toAbsolutePath().toString());
		nativeReady = EffekseerBackendCore.InitializeWithOpenGL();
		if (!nativeReady) {
			throw new IllegalStateException("Effekseer OpenGL initialization failed");
		}
		manager = new EffekseerManagerCore();
		if (!manager.Initialize(MAX_SPRITES, false)) {
			throw new IllegalStateException("Effekseer manager initialization failed");
		}
		framebuffer = new SpectacleFramebuffer();
	}

	private static void play(MagicFx.Spectacle effect) {
		if (PLAYING.size() >= MAX_EFFECTS || !Float.isFinite(effect.scale()) || effect.scale() <= 0.0F) {
			fallback.accept(effect.fallback());
			return;
		}
		if (!EFFECTS.containsKey(effect.id())) {
			try {
				EFFECTS.put(effect.id(), load(effect.id()));
			} catch (IOException | RuntimeException ex) {
				LOGGER.warn("Could not load spectacle {}", effect.id(), ex);
				EFFECTS.put(effect.id(), null);
			}
		}
		EffekseerEffectCore asset = EFFECTS.get(effect.id());
		int handle = asset == null ? -1 : manager.Play(asset);
		if (handle < 0) {
			fallback.accept(effect.fallback());
			return;
		}
		MagicFx.Shape shape = effect.fallback();
		manager.SetEffectPosition(handle, (float)(shape.x() - origin.x), (float)(shape.y() - origin.y), (float)(shape.z() - origin.z));
		manager.SetEffectScale(handle, effect.scale(), effect.scale(), effect.scale());
		PLAYING.put(handle, new Playing(shape, tick + shape.ticks()));
	}

	private static EffekseerEffectCore load(String id) throws IOException {
		String path = "effeks/" + id + ".efkefc";
		EffekseerEffectCore asset = new EffekseerEffectCore();
		boolean loaded = false;
		try {
			byte[] data = read(path);
			if (!asset.Load(data, data.length, 1.0F)) {
				throw new IOException("Unreadable Effekseer effect " + path);
			}
			for (EffekseerTextureType type : TEXTURE_TYPES) {
				loadAssets(path, asset.GetTextureCount(type), i -> asset.GetTexturePath(i, type),
					(bytes, i) -> asset.LoadTexture(bytes, bytes.length, i, type));
			}
			loadAssets(path, asset.GetModelCount(), asset::GetModelPath, (bytes, i) -> asset.LoadModel(bytes, bytes.length, i));
			loadAssets(path, asset.GetCurveCount(), asset::GetCurvePath, (bytes, i) -> asset.LoadCurve(bytes, bytes.length, i));
			loadAssets(path, asset.GetMaterialCount(), asset::GetMaterialPath, (bytes, i) -> asset.LoadMaterial(bytes, bytes.length, i));
			loaded = true;
			return asset;
		} finally {
			if (!loaded) {
				asset.delete();
			}
		}
	}

	private static byte[] read(String path) throws IOException {
		try (var input = Minecraft.getInstance().getResourceManager().open(JaymosPowers.id(path))) {
			return input.readAllBytes();
		}
	}

	private static void loadAssets(String effect, int count, IntFunction<String> dependency, AssetLoader load) throws IOException {
		for (int i = 0; i < count; i++) {
			String path = dependency.apply(i).replace('\\', '/').toLowerCase(Locale.ROOT);
			Path relative = Path.of(effect).getParent().resolve(path).normalize();
			if (!relative.startsWith("effeks")) {
				throw new IOException("Dependency outside effeks/ in " + effect + ": " + path);
			}
			if (!load.load(read(relative.toString().replace('\\', '/')), i)) {
				throw new IOException("Failed to load " + relative);
			}
		}
	}

	@FunctionalInterface
	private interface AssetLoader {
		boolean load(byte[] bytes, int index);
	}

	private static void draw(CameraRenderState camera, Matrix4f projection, RenderTarget target, GlTexture color, GlTexture depth) {
		PLAYING.entrySet().removeIf(entry -> {
			if (tick >= entry.getValue().end()) {
				manager.Stop(entry.getKey());
				return true;
			}
			return !manager.Exists(entry.getKey());
		});
		if (PLAYING.isEmpty()) {
			manager.Update(0);
			lastFrame = 0;
			return;
		}

		long now = System.nanoTime();
		float frames = lastFrame == 0 ? 1.0F : Math.min(3.0F, (now - lastFrame) * 60.0F / 1_000_000_000.0F);
		lastFrame = now;
		Vec3 eye = camera.pos.subtract(origin);
		new Matrix4f(camera.viewRotationMatrix).translate((float)-eye.x, (float)-eye.y, (float)-eye.z).transpose().get(VIEW);
		// RenderPearl uses reversed depth. Effekseer uses near-to-far depth and GL_LEQUAL.
		Matrix4f reverse = new Matrix4f().scaling(1.0F, 1.0F, -1.0F);
		if (RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()) {
			reverse.m32(1.0F);
		}
		reverse.mul(projection).transpose().get(PROJECTION);
		Vector3f facing = camera.orientation.transform(0.0F, 0.0F, -1.0F, new Vector3f());
		manager.SetCameraParameter(facing.x, facing.y, facing.z, (float)eye.x, (float)eye.y, (float)eye.z);
		manager.SetCameraMatrix(VIEW[0], VIEW[1], VIEW[2], VIEW[3], VIEW[4], VIEW[5], VIEW[6], VIEW[7],
			VIEW[8], VIEW[9], VIEW[10], VIEW[11], VIEW[12], VIEW[13], VIEW[14], VIEW[15]);
		manager.SetProjectionMatrix(PROJECTION[0], PROJECTION[1], PROJECTION[2], PROJECTION[3], PROJECTION[4], PROJECTION[5], PROJECTION[6],
			PROJECTION[7], PROJECTION[8], PROJECTION[9], PROJECTION[10], PROJECTION[11], PROJECTION[12], PROJECTION[13], PROJECTION[14], PROJECTION[15]);
		framebuffer.bind(target.width, target.height, color.glId(), depth.glId());
		manager.SetBackground(framebuffer.background(), false);
		try {
			manager.Update(Minecraft.getInstance().isPaused() ? 0.0F : frames);
			manager.DrawBack();
			manager.DrawFront();
		} finally {
			manager.UnsetBackground();
		}
	}

	private static void useFallback() {
		PENDING.forEach(effect -> fallback.accept(effect.fallback()));
		PENDING.clear();
		PLAYING.values().forEach(playing -> fallback.accept(playing.fallback()));
		PLAYING.clear();
		reset = true;
	}

	private static void clearAssets() {
		for (EffekseerEffectCore effect : EFFECTS.values()) {
			if (effect != null) {
				effect.delete();
			}
		}
		EFFECTS.clear();
	}

	private static void close() {
		if (manager != null) {
			manager.delete();
		}
		clearAssets();
		if (framebuffer != null) {
			framebuffer.close();
		}
		if (nativeReady) {
			EffekseerBackendCore.Terminate();
		}
	}
}

