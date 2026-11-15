package com.hmc.zenkai.client.face;

import com.hmc.zenkai.client.aura.AuraClock;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Expresiones de la cara, calculadas en el CLIENTE desde la entidad, sin red (fases 1-3: parpadeo, daño, cansancio,
 * sueño, muerte y carga de ki). Cómo funciona: {@code .claude/docs/customizacion-cara.md}, «Expresiones y variants». El único punto que las
 * consume es {@code FaceOverlayGeoLayer}, que pide {@link #current} y luego {@link #layersFor} por categoría.
 *
 * OJOS y BOCA tienen su propia expresión (así el parpadeo no apaga la boca de dolor ni al revés):
 * <ul>
 *   <li>OJOS (la de arriba manda): DEAD &gt; SLEEP &gt; HURT &gt; BLINK &gt; TIRED &gt; NEUTRAL.</li>
 *   <li>BOCA: HURT &gt; CHARGING &gt; TIRED &gt; NEUTRAL (dormir y morir no tocan la boca).</li>
 * </ul>
 * QUÉ SE PINTA. Cada rasgo puede traer su propia variante ({@code "variants": {"blink": [...]}} en su JSON, ver
 * {@link FacePartDef#variant}); sin ella se usa un rasgo genérico del catálogo: ojos BLINK/HURT/SLEEP/DEAD → ojo
 * cerrado ({@code zenkai:eyes/closed}) más las cejas del elegido; ojos TIRED → {@code zenkai:eyes/sleepy}; boca HURT →
 * {@code zenkai:mouth/yell}, CHARGING (transformándose) → {@code zenkai:mouth/yell}, TIRED → {@code zenkai:mouth/frown}.
 * Las claves de variante son {@code blink}, {@code hurt}, {@code tired}, {@code charge}.
 *
 * PARPADEO: el reloj es el del cliente ({@link AuraClock}), el momento sale de un hash del id de la entidad y del
 * ciclo (100 ticks = 5 s; el parpadeo cae en un punto distinto de cada ciclo y cierra 4 ticks), así que dos jugadores
 * no parpadean a la vez.
 */
public final class FaceExpression {
    private FaceExpression() {}

    public enum Expression { NEUTRAL, TIRED, CHARGING, BLINK, HURT, SLEEP, DEAD }

    /** Expresión de los ojos y de la boca en este fotograma. */
    public record State(Expression eyes, Expression mouth) {
        public static final State NEUTRAL = new State(Expression.NEUTRAL, Expression.NEUTRAL);
    }

    private static final int CYCLE = 100, BLINK_TICKS = 4, MIN_START = 20, SPREAD = 70;
    /** Por debajo de esta fracción de estamina la cara se ve cansada (solo hay dato fiable del jugador local). */
    private static final float TIRED_BELOW = 0.15f;

    private static final ResourceLocation EYES_CLOSED = ResourceLocation.fromNamespaceAndPath("zenkai", "eyes/closed");
    private static final ResourceLocation EYES_SLEEPY = ResourceLocation.fromNamespaceAndPath("zenkai", "eyes/sleepy");
    private static final ResourceLocation MOUTH_YELL = ResourceLocation.fromNamespaceAndPath("zenkai", "mouth/yell");
    private static final ResourceLocation MOUTH_FROWN = ResourceLocation.fromNamespaceAndPath("zenkai", "mouth/frown");

    public static State current(Player p, float partialTick) {
        boolean dead = p.isDeadOrDying(), sleep = p.isSleeping(), hurt = pain(p);
        boolean tired = tired(p);
        // Forma hacia la que se transforma (carga o escena larga): el Ultra Instinto no grita (transform_face, ver FormDef).
        com.hmc.zenkai.feature.forms.FormDef target = transformTarget(p);
        boolean calm = target != null && target.calmTransformFace();
        boolean eyesShut = target != null && target.closesEyesWhileTransforming();
        Expression eyes = dead ? Expression.DEAD : sleep ? Expression.SLEEP : hurt ? Expression.HURT
                : eyesShut || blinking(p, partialTick) ? Expression.BLINK : tired ? Expression.TIRED : Expression.NEUTRAL;
        Expression mouth = (dead || sleep) ? Expression.NEUTRAL : hurt ? Expression.HURT
                : charging(p) && !calm ? Expression.CHARGING : tired ? Expression.TIRED : Expression.NEUTRAL;
        return new State(eyes, mouth);
    }

    /**
     * ¿Cierra los ojos por transformarse hacia una forma serena (UI Señal)? FaceOverlayGeoLayer lo respeta aunque la mezcla de colores
     * del cambio de forma congele el resto de expresiones de los ojos: sin esto la escena los dejaba abiertos (capturas 2026-11-13).
     */
    public static boolean eyesShutForTransform(Player p) {
        com.hmc.zenkai.feature.forms.FormDef target = transformTarget(p);
        return target != null && target.closesEyesWhileTransforming() && !p.isDeadOrDying();
    }

    /**
     * Forma de destino de la transformación en curso: la de la carga ({@code getHoldTargetForm}) o, si no hay carga, la de la
     * escena larga que aún dura (la del Oozaru llega sin carga y la escena sigue tras el cambio). null = no se está transformando.
     */
    private static com.hmc.zenkai.feature.forms.FormDef transformTarget(Player p) {
        var att = p.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get());
        ResourceLocation id = att.getHoldTargetForm();
        if (id == null) id = com.hmc.zenkai.client.rage.AwakeningScene.activeForm(p.getId());
        return com.hmc.zenkai.feature.forms.FormDef.get(id);
    }

    /** Cuántos ticks se queda la cara de dolor tras un golpe fuerte. */
    private static final int PAIN_TICKS = 14;

    /** Por jugador: {vida del último muestreo, tick de cliente hasta el que dura el dolor, último tick muestreado}. */
    private static final java.util.Map<Integer, float[]> PAIN = new java.util.HashMap<>();

    /**
     * Dolor SOLO si UN golpe quita al menos {@code face.pain_threshold_percent} de la vida máxima (15 % por defecto):
     * con 1 de daño sobre 20 de vida no hay cara de dolor. El cliente no ve el daño de un golpe, solo la vida que se
     * sincroniza, así que se mide la CAÍDA de vida entre dos ticks consecutivos del cliente (curar no cuenta). Se muestrea
     * una vez por tick de cliente aunque se pida varias veces por fotograma.
     */
    private static boolean pain(Player p) {
        long now = AuraClock.ticks();
        float health = p.getHealth(), max = Math.max(1f, p.getMaxHealth());
        float[] s = PAIN.get(p.getId());
        if (s == null) {
            PAIN.put(p.getId(), new float[]{health, 0f, now});
            return false;
        }
        if ((long) s[2] != now) {
            float drop = s[0] - health;
            float threshold = com.hmc.zenkai.config.ClientConfig.facePainThresholdPercent() / 100f;
            if (drop > 0f && drop >= max * threshold) s[1] = now + PAIN_TICKS;
            s[0] = health;
            s[2] = now;
        }
        return now < (long) s[1];
    }

    /** Vacía el estado por jugador (salir del mundo). */
    public static void clear() { PAIN.clear(); }

    /**
     * La «cara de carga» ahora es el GRITO de la transformación (boca abierta): la de cargar ki con boca decidida no
     * convenció (2026-11-01). Cargar ki sin transformarse ya no cambia la cara.
     */
    private static boolean charging(Player p) {
        return p.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()).isTransforming();
    }

    private static boolean blinking(Player p, float partialTick) {
        // Desfase por jugador para que no coincidan los ciclos de dos jugadores con ids cercanos.
        long t = AuraClock.ticks() + (p.getId() * 37L & 0x7F);
        long cycle = Math.floorDiv(t, (long) CYCLE);
        float within = (int) Math.floorMod(t, (long) CYCLE) + partialTick;
        int start = MIN_START + Math.floorMod(hash(p.getId(), cycle), SPREAD);
        return within >= start && within < start + BLINK_TICKS;
    }

    private static boolean tired(Player p) {
        PlayerStatsAttachment st = PlayerStatsAttachment.get(p);
        int max = st.getStaminaMax();
        return max > 0 && st.getStamina() < max * TIRED_BELOW;
    }

    /**
     * Capas con que se pinta {@code part} (de la categoría dada) bajo esta expresión. Las normales de la raza si la
     * expresión no cambia esa categoría o si el rasgo ya ES lo que la expresión pondría (ojo ya cerrado, etc.).
     */
    public static List<FaceLayer> layersFor(State state, FaceCategory category, FacePartDef part, Race race) {
        List<FaceLayer> normal = part.layersFor(race);
        if (category == FaceCategory.EYES) {
            return switch (state.eyes()) {
                case TIRED -> eyes(part, race, "tired", EYES_SLEEPY, normal);
                case BLINK, HURT, SLEEP, DEAD ->
                        eyes(part, race, state.eyes() == Expression.HURT ? "hurt" : "blink", EYES_CLOSED, normal);
                case CHARGING -> eyes(part, race, "charge", null, normal);
                default -> normal;
            };
        }
        if (category == FaceCategory.MOUTH) {
            return switch (state.mouth()) {
                case HURT -> mouth(part, race, "hurt", MOUTH_YELL, normal);
                case CHARGING -> mouth(part, race, "charge", MOUTH_YELL, normal);
                case TIRED -> mouth(part, race, "tired", MOUTH_FROWN, normal);
                default -> normal;
            };
        }
        return normal;
    }

    private static List<FaceLayer> eyes(FacePartDef part, Race race, String variant, ResourceLocation fallback,
                                        List<FaceLayer> normal) {
        if (part.id().equals(fallback) || part.id().equals(EYES_CLOSED)) return normal;
        List<FaceLayer> own = part.variant(variant);
        // Daño sin variante propia: el mismo párpado cerrado del parpadeo.
        if (own == null && "hurt".equals(variant)) own = part.variant("blink");
        boolean keepsOwn = "tired".equals(variant) || "charge".equals(variant);
        List<FaceLayer> out = new ArrayList<>();
        // El trazo de ceja del ojo elegido no baja con el párpado (en el cansancio los ojos conservan sus cejas
        // porque el rasgo sleepy trae las suyas).
        if (!keepsOwn) for (FaceLayer l : normal) if (l.eyebrow()) out.add(l);
        if (own != null) {
            out.addAll(own);
            return out;
        }
        if (fallback == null) return normal;
        FacePartDef generic = FaceCatalog.get(fallback);
        if (generic == null) return normal;
        out.clear();
        if (!keepsOwn) for (FaceLayer l : normal) if (l.eyebrow()) out.add(l);
        out.addAll(generic.layersFor(race));
        return out;
    }

    private static List<FaceLayer> mouth(FacePartDef part, Race race, String variant, ResourceLocation fallback,
                                         List<FaceLayer> normal) {
        if (part.id().equals(fallback)) return normal;
        List<FaceLayer> own = part.variant(variant);
        if (own != null) return own;
        FacePartDef generic = FaceCatalog.get(fallback);
        return generic == null ? normal : generic.layersFor(race);
    }

    private static int hash(int id, long cycle) {
        long h = id * 0x9E3779B97F4A7C15L + cycle * 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 31);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 29);
        return (int) (h & 0x7FFFFFFF);
    }
}
