package com.jaymospowers.dino.rig;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DinoSpec {
   public final String name;
   public boolean quadruped;
   public float stride = 2.0F;
   public float hipHeight = 1.2F;
   public float lieHeight = 0.3F;
   public float sitDrop = 0.6F;
   public float thighSwing = 0.55F;
   public float shinFlex = 0.75F;
   public float ankleFlex = 0.55F;
   public float footFlex = 0.5F;
   public float armSwing = 0.12F;
   public float forearmFlex = 0.2F;
   public float bob = 0.05F;
   public float sway = 0.05F;
   public float runLean = 0.1F;
   public float tailIdle = 0.05F;
   public float tailWalk = 0.09F;
   public float jawMax = 0.65F;
   public float sitThigh = -0.75F;
   public float sitShin = 1.55F;
   public float sitAnkle = -1.45F;
   public float sitFoot = 0.65F;
   public float sitArm = -1.15F;
   public float sitForearm = 1.9F;
   public float sitHand = -0.75F;
   public float restNeck = 0.25F;
   public float lieNeck = 0.3F;
   public float lieHead = 0.12F;
   public float lieTail = 0.07F;
   public float headLift = 1.0F;
   public float bipedSitArm = 0.6F;
   public float rear = 0.0F;
   public float biteTime = 0.6F;
   public float biteHit = 0.3F;
   public float roarTime = 2.4F;
   public float specialTime = 1.2F;
   public float specialHit = 0.6F;
   public float eatTime = 1.4F;
   public int attackStyle = 0;
   public int specialStyle = 0;
   private static final Map<String, DinoSpec> CACHE = new ConcurrentHashMap<>();

   private DinoSpec(String name) {
      this.name = name;
   }

   public static DinoSpec of(String name) {
      return CACHE.computeIfAbsent(name, DinoSpec::load);
   }

   private static DinoSpec load(String name) {
      DinoSpec s = new DinoSpec(name);
      String path = "assets/jaymospowers/dino/" + name + ".spec";
      List<String> lines = null;

      try {
         try (InputStream in = DinoSpec.class.getResourceAsStream("/" + path)) {
            if (in != null) {
               lines = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n"));
            }
         }
      } catch (IOException e) {
         throw new IllegalStateException("cannot read " + path, e);
      }

      if (lines == null) {
         return s;
      }

      for (String raw : lines) {
         String line = raw.contains("#") ? raw.substring(0, raw.indexOf(35)) : raw;
         int eq = line.indexOf(61);
         if (eq >= 0) {
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            try {
               Field f = DinoSpec.class.getField(key);
               if (f.getType() == float.class) {
                  f.setFloat(s, Float.parseFloat(value));
               } else if (f.getType() == int.class) {
                  f.setInt(s, Integer.parseInt(value));
               } else if (f.getType() == boolean.class) {
                  f.setBoolean(s, Boolean.parseBoolean(value));
               }
            } catch (ReflectiveOperationException | NumberFormatException e) {
               throw new IllegalStateException(path + ": bad line '" + raw.trim() + "'", e);
            }
         }
      }

      return s;
   }
}
