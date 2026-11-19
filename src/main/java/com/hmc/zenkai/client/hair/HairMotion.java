package com.hmc.zenkai.client.hair;

import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.feature.race.hair.strand.StrandGeoWriter;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Movimiento del pelo por mechones (petición del usuario, 2026-10-12). Cada mechón es un hueso con
 * el pivote en su raíz (StrandGeoWriter.STRAND_BONE) y se DOBLA entero, sin tocar la geometría:
 *  · reposo: un balanceo suave, desfasado por mechón;
 *  · movimiento: el "viento" contrario a la marcha (andar, correr, volar, caer) lo dobla, con inercia
 *    y saturación suave (volar o caer no lo exagera);
 *  · cargando ki: aleteo fuerte y pelo algo levantado; en turbo, lo mismo más suave.
 *
 * Doblar, no girar: el giro de cada mechón es d × v (d = su dirección, v = el viento), un eje siempre
 * PERPENDICULAR al mechón, así que su punta se va hacia el viento y nunca se retuerce sobre sí mismo.
 * (Versión anterior: todos giraban sobre el eje X de la cabeza, y los mechones laterales, que
 * apuntan a lo largo de X, se retorcían: "parece que el pelo rotara al caminar".) Ángulos pequeños:
 * la suma de los tres ejes de GeckoLib vale como vector de giro.
 *
 * Idea del comportamiento de DragonMineZ (balanceo, inercia por velocidad, pelo agitado al cargar);
 * implementación propia. HairGeoLayer fija {@link #current} alrededor de su render (patrón
 * TailWaistPose.weight) y GeoLayerArmorModel.setCustomAnimations llama a {@link #apply}.
 */
public final class HairMotion {
    private HairMotion() {}

    /** Jugador cuyo pelo se está pintando AHORA, o null. */
    public static Player current;
    public static float partialTick;

    private static final int MAX_STRANDS = 96;
    /** Reposo (el que gustó al usuario): grados y velocidad. */
    private static final float IDLE_DEG = 1.6f, IDLE_SPEED = 1.7f;
    private static final float CHARGE_DEG = 7f, CHARGE_SPEED = 9f, CHARGE_LIFT_DEG = 10f;
    private static final float TURBO_DEG = 4f, TURBO_SPEED = 6f, TURBO_LIFT_DEG = 5f;
    /** Arrastre: tope en grados y velocidad (bloques/tick) a la que llega a ~76% del tope. */
    private static final float DRAG_MAX = 16f, DRAG_SPEED_REF = 0.4f;
    /** La caída cuenta menos que el avance (el pelo subiendo de golpe se veía raro). */
    private static final float FALL_WEIGHT = 0.45f;
    /** Inercia: fracción del objetivo que se alcanza por segundo (más alto = más rígido). */
    private static final float RESPONSE = 4f;

    /** Viento suavizado por jugador (vector en render, grados) + último tiempo. */
    private static final Map<UUID, float[]> STATE = new WeakHashMap<>();
    /** Dirección (raíz -> mechón) de cada hueso de mechón, por hueso horneado. */
    private static final Map<GeoBone, Vector3f> DIRECTIONS = new WeakHashMap<>();

    public static void apply(GeoModel<?> model) {
        Player p = current;
        if (p == null) return;
        float time = (p.tickCount + partialTick) / 20f;

        // Viento (hacia dónde se dobla el pelo) en el marco del CUERPO, espacio de render de GeckoLib
        // (frente -Z, izquierda del personaje -X): contrario a la marcha.
        Vec3 v = p.getDeltaMovement();
        float yaw = (float) Math.toRadians(Mth.rotLerp(partialTick, p.yBodyRotO, p.yBodyRot));
        float forward = (float) (-v.x * Math.sin(yaw) + v.z * Math.cos(yaw));
        float left = (float) (v.x * Math.cos(yaw) + v.z * Math.sin(yaw));
        float vertical = (float) (v.y + 0.0784); // en el suelo vy ronda -0.078
        Vector3f wind = new Vector3f(left, -vertical * FALL_WEIGHT, forward);
        float speed = wind.length();
        if (speed > 1e-4f) wind.mul(DRAG_MAX * (float) Math.tanh(speed / DRAG_SPEED_REF) / speed);

        boolean charging = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).isChargingKi();
        boolean turbo = !charging && p instanceof AbstractClientPlayer acp && AuraClientState.isTurbo(acp);
        if (charging) wind.y += CHARGE_LIFT_DEG;
        else if (turbo) wind.y += TURBO_LIFT_DEG;

        float[] st = STATE.computeIfAbsent(p.getUUID(), k -> new float[]{0, 0, 0, time});
        float dt = Mth.clamp(time - st[3], 0f, 0.25f);
        float k = 1f - (float) Math.exp(-RESPONSE * dt);
        st[0] += (wind.x - st[0]) * k;
        st[1] += (wind.y - st[1]) * k;
        st[2] += (wind.z - st[2]) * k;
        st[3] = time;
        Vector3f drag = new Vector3f(st[0], st[1], st[2]);

        float amp = charging ? CHARGE_DEG : turbo ? TURBO_DEG : IDLE_DEG;
        float freq = charging ? CHARGE_SPEED : turbo ? TURBO_SPEED : IDLE_SPEED;

        for (int i = 0; i < MAX_STRANDS; i++) {
            var opt = model.getBone(StrandGeoWriter.STRAND_BONE + i);
            if (opt.isEmpty()) {
                if (i == 0 && model.getBone(StrandGeoWriter.STRAND_BONE + 1).isEmpty()) {
                    return; // peinado sin huesos de mechón (geo hecho a mano)
                }
                // Hueco: StrandGeoWriter no crea hueso para un mechón sin cubos (pasa en los pasos
                // de un cambio de forma, con mechones que aún no han crecido). Antes se cortaba
                // aquí y los mechones de detrás del hueco se quedaban quietos.
                continue;
            }
            GeoBone bone = opt.get();
            Vector3f d = DIRECTIONS.computeIfAbsent(bone, HairMotion::direction);
            // Balanceo: un viento propio de cada mechón que gira despacio.
            float phase = i * 1.37f;
            Vector3f sway = new Vector3f(
                    (float) Math.sin(time * freq + phase),
                    0.4f * (float) Math.sin(time * freq * 0.7f + phase * 2.1f),
                    (float) Math.sin(time * freq * 0.8f + phase * 1.7f)).mul(amp);
            Vector3f total = new Vector3f(drag).add(sway);
            // Giro = d × viento (grados): dobla la punta hacia el viento, nunca a lo largo del mechón.
            Vector3f rot = new Vector3f(d).cross(total);
            bone.setRotX((float) Math.toRadians(rot.x));
            bone.setRotY((float) Math.toRadians(rot.y));
            bone.setRotZ((float) Math.toRadians(rot.z));
        }
    }

    /**
     * Dirección media del mechón desde su raíz, en espacio de render: centro de sus vértices con la
     * rotación de cada cubo aplicada (GeckoLib guarda los vértices SIN ella; la aplica al pintar,
     * Z·Y·X alrededor del pivote del cubo, en bloques) menos el pivote del hueso (en px).
     */
    private static Vector3f direction(GeoBone bone) {
        Vector3f pivot = new Vector3f(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()).div(16f);
        Vector3f sum = new Vector3f();
        int n = 0;
        for (GeoCube cube : bone.getCubes()) {
            Vector3f cp = new Vector3f((float) cube.pivot().x, (float) cube.pivot().y, (float) cube.pivot().z);
            Matrix3f r = new Matrix3f().rotateZ((float) cube.rotation().z).rotateY((float) cube.rotation().y)
                    .rotateX((float) cube.rotation().x);
            for (GeoQuad q : cube.quads()) {
                if (q == null) continue;
                for (GeoVertex vtx : q.vertices()) {
                    Vector3f pos = new Vector3f(vtx.position()).sub(cp);
                    r.transform(pos).add(cp);
                    sum.add(pos);
                    n++;
                }
            }
        }
        if (n == 0) return new Vector3f(0, 1, 0);
        Vector3f dir = sum.div(n).sub(pivot);
        return dir.lengthSquared() < 1e-8f ? new Vector3f(0, 1, 0) : dir.normalize();
    }
}
