package com.hmc.zenkai.client.debug;

import com.hmc.zenkai.event.ZenkaiPalLayers;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Instrumentación TEMPORAL de las capas PAL. Se retira cuando cerremos el bug de
 * "la animación se reproduce una vez y nunca más".
 * QUÉ INTENTA DISTINGUIR
 * ----------------------
 *   a) La máquina de estados no vuelve a PEDIR la animación (flag pegado en ClientZenkaiPalTick).
 *   b) La pide, play() devuelve false  → el controlador la rechaza (asset/registro).
 *   c) La pide, play() devuelve true y no se ve → el controlador quedó inservible tras stop(),
 *      que es la hipótesis principal: el AbstractFadeModifier de salida se queda enganchado.
 * El volcado por reflexión existe para no adivinar la API de PAL: imprime los campos del
 * controlador antes y después de parar, y en el siguiente play. Si una colección de
 * modificadores crece con cada stop y no baja, (c) queda demostrado.
 * ⚠ setAccessible sobre clases de PAL puede fallar según cómo esté modularizado el jar.
 *   Va envuelto en try/catch: si no puede, imprime "<no accesible>" y el resto del log sigue.
 */
public final class ZenkaiAnimDebug {

    private ZenkaiAnimDebug() {}

    /** Interruptor maestro. A false no cuesta nada: todas las entradas salen en la primera línea. */
    public static final boolean ENABLED = false;

    /** Volcado de campos del controlador. Es verboso: solo en stop y en el play siguiente. */
    public static final boolean DUMP_FIELDS = true;

    private static final String P = "[ZK-ANIM] ";

    /** Controladores que ya pasaron por stop(): el próximo play sobre ellos se vuelca entero. */
    private static final Map<Object, Boolean> POISONED = new IdentityHashMap<>();

    /** Última instancia vista por capa (jugador local), para detectar recreación o null. */
    private static final Map<ResourceLocation, Integer> LAST_CTRL = new HashMap<>();

    /** Capas vigiladas. Si ya aplicaste PREVIEW_LAYER, añádela a mano a este array. */
    private static final ResourceLocation[] WATCH = ZenkaiPalLayers.ALL;

    // ── Puntos de entrada desde ZenkaiTransitions ────────────────────────────

    public static void beforePlay(Object controller, ResourceLocation anim) {
        if (!ENABLED || controller == null) return;
        if (POISONED.containsKey(controller)) {
            System.out.println(P + "PLAY tras STOP sobre ctrl=" + id(controller)
                    + " anim=" + path(anim) + "  -- volcado ANTES:");
            dump("pre-play", controller);
        }
    }

    public static void logPlay(Object controller, ResourceLocation anim, int fade, Boolean result) {
        if (!ENABLED) return;
        boolean wasPoisoned = controller != null && POISONED.containsKey(controller);
        System.out.println(P + "PLAY  t=" + gameTime()
                + " ctrl=" + id(controller)
                + " anim=" + path(anim)
                + " fade=" + fade
                + " -> " + (result == null ? "NO LLAMADO (null)" : result)
                + (wasPoisoned ? "   <<< primer PLAY después de un STOP" : ""));
        if (wasPoisoned) {
            dump("post-play", controller);
            POISONED.remove(controller);
        }
    }

    public static void beforeStop(Object controller, int fade) {
        if (!ENABLED) return;
        System.out.println(P + "STOP  t=" + gameTime()
                + " ctrl=" + id(controller) + " fade=" + fade);
        dump("pre-stop", controller);
    }

    public static void afterStop(Object controller) {
        if (!ENABLED || controller == null) return;
        dump("post-stop", controller);
        POISONED.put(controller, Boolean.TRUE);
    }

    // ── Vigilancia de instancias de controlador ──────────────────────────────

    /** Llamar una vez por tick con el jugador local. Solo imprime cuando algo CAMBIA:
     *  una instancia distinta significa que PAL recreó la capa (respawn, dimensión, relog). */
    public static void trackControllers(AbstractClientPlayer p) {
        if (!ENABLED || p == null) return;
        for (ResourceLocation layer : WATCH) {
            Object c = null;
            try {
                c = PlayerAnimationAccess.getPlayerAnimationLayer(p, layer);
            } catch (Throwable t) {
                System.out.println(P + "CTRL layer=" + layer.getPath() + " EXCEPCION " + t);
            }
            int now = (c == null) ? 0 : System.identityHashCode(c);
            Integer prev = LAST_CTRL.put(layer, now);
            if (prev == null || prev != now) {
                System.out.println(P + "CTRL  t=" + gameTime()
                        + " layer=" + layer.getPath()
                        + "  " + hex(prev) + " -> " + hex(now)
                        + (c == null ? "   <<< NULL: la capa no existe para este jugador"
                        : "   (" + c.getClass().getSimpleName() + ")"));
            }
        }
    }

    // ── Máquina de estados (ClientZenkaiPalTick) ─────────────────────────────

    /** Decisiones de la máquina. Sirve para separar "no pedimos" de "pedimos y no pasó nada". */
    public static void state(AbstractClientPlayer p, String what, String detail) {
        if (!ENABLED) return;
        boolean local = p == Minecraft.getInstance().player;
        System.out.println(P + "STATE t=" + gameTime()
                + " " + (local ? "LOCAL" : "remoto:" + p.getId())
                + " " + what + " " + detail);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private static void dump(String tag, Object controller) {
        if (!ENABLED || !DUMP_FIELDS || controller == null) return;
        StringBuilder sb = new StringBuilder(P + "DUMP " + tag
                + " ctrl=" + id(controller)
                + " class=" + controller.getClass().getName());
        for (Class<?> c = controller.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                sb.append("\n    ").append(c.getSimpleName()).append('.').append(f.getName())
                        .append(" = ");
                try {
                    f.setAccessible(true);
                    sb.append(describe(f.get(controller)));
                } catch (Throwable t) {
                    sb.append("<no accesible: ").append(t.getClass().getSimpleName()).append('>');
                }
            }
        }
        System.out.println(sb);
    }

    /** Lo que importa de un campo: el tamaño de las colecciones y el tipo de lo que hay dentro.
     *  Ahí es donde se vería un modificador de fundido que no se retira nunca. */
    private static String describe(Object v) {
        if (v == null) return "null";
        if (v instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder("Collection(size=" + col.size() + ")");
            int i = 0;
            for (Object o : col) {
                if (i++ >= 6) { sb.append(" ..."); break; }
                sb.append("\n        [").append(i - 1).append("] ")
                        .append(o == null ? "null" : o.getClass().getSimpleName() + "@" + id(o));
            }
            return sb.toString();
        }
        if (v instanceof Map<?, ?> m) return "Map(size=" + m.size() + ")";
        if (v.getClass().isArray()) return v.getClass().getSimpleName()
                + "(len=" + java.lang.reflect.Array.getLength(v) + ")";
        String s;
        try { s = String.valueOf(v); } catch (Throwable t) { s = "<toString falló>"; }
        if (s.length() > 140) s = s.substring(0, 140) + "...";
        return v.getClass().getSimpleName() + " " + s;
    }

    private static String id(Object o) {
        return o == null ? "null" : "@" + Integer.toHexString(System.identityHashCode(o));
    }

    private static String hex(Integer i) {
        return (i == null) ? "(nuevo)" : (i == 0 ? "null" : "@" + Integer.toHexString(i));
    }

    private static String path(ResourceLocation rl) {
        return rl == null ? "null" : rl.getPath();
    }

    private static long gameTime() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? -1L : mc.level.getGameTime();
    }
}