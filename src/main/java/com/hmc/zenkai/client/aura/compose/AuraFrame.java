package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.feature.aura.AuraProfile;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Contexto de UN jugador en UN frame que el compositor ({@link AuraComposer}) rellena y los
 * componentes leen. Uno por jugador, reutilizado entre frames (vive en su {@code AuraRuntime}): sin
 * asignaciones por frame.
 * <p>
 * Los componentes lo leen en {@code update} (simulación, fuera de la cola) y lo capturan en
 * {@code submit}. Como el siguiente relleno ocurre en el frame siguiente — después de que la cola
 * de este ya se reprodujo —, una tarea puede leerlo sin copiarlo.
 */
public final class AuraFrame {
    public AbstractClientPlayer player;
    /** Posición interpolada de los pies. */
    public double x, y, z;
    /** Posición de la cámara. */
    public double camX, camY, camZ;
    /** Ejes de pantalla de la cámara en mundo (para billboards). */
    public float leftX, leftY, leftZ, upX, upY, upZ;
    public float seconds;
    /** Segundos reales desde el frame anterior (acotado). */
    public float dt;

    /** true = el servidor/cliente dicen que hay aura ahora (carga, transformación, turbo). */
    public boolean active;
    /** Rampa de encendido suavizada, 0..1. */
    public float growth;
    public @Nullable AuraProfile profile;
    /** Poder relativo a la base (AuraFlameParams.powerFactor) y escala de tamaño por poder. */
    public float powerF, powerScale;

    public double distSq;
    /** Nivel de detalle de la malla ({@link AuraBodyMesh#LOD_NEAR}...). */
    public int lod;
    public boolean selfFirstPerson;
    /** La cámara está DENTRO del volumen del cuerpo en 3ª persona (cámara pegada por una pared o el
     *  suelo, o aura muy grande): se dibuja como desde dentro, ver AuraComposer.cameraInside. */
    public boolean cameraInside;
    public float fpOpacity;
    /** false en primera persona propia o con el bloom del aura apagado. */
    public boolean bloomAllowed;

    // ── Color (rgb 0..1), resuelto por AuraColors y preparado por el compositor ──
    public final float[] inner = new float[3];
    public final float[] outer = new float[3];
    /** Capa caliente del tinte exterior (KiVfxColors.derivedHot): chispas, ascuas. */
    public final float[] hot = new float[3];
    public boolean twoTone;

    // ── Estados dinámicos (AuraDynamics) ──
    public final AuraDynamics dynamics = new AuraDynamics();

    // ── Cuerpo ──
    /** Pose real (o sintética) del cuerpo como cápsulas, en MUNDO. */
    public final AuraAnchor.Pose anchor = new AuraAnchor.Pose();
    /** true si {@link #anchor} es la pose capturada de verdad este frame. */
    public boolean anchorCaptured;
    /** Giro del cuerpo interpolado (grados) y escala de cuerpo (atributo minecraft:generic.scale). */
    public float bodyYaw, bodyScale = 1f;
    /** Parámetros finales del cuerpo exterior de este frame (familia + estilo + estados); null si
     *  el cuerpo no se dibuja. Los componentes que añaden capas (shell) parten de aquí. */
    public AuraBodyParams body;
    /** Programa del cuerpo exterior de este frame. */
    public AuraBodyKind bodyKind = AuraBodyKind.FLAME;
    /** Colores ya preparados del cuerpo (centro y borde de la capa exterior e interior). */
    public float[] outerCenter = new float[3], innerCenter = new float[3], innerEdge = new float[3];
    /** Multiplicador de tamaño de la secuencia de transformación (1 = sin efecto). */
    public float burstScale = 1f;
    /** Multiplicador de alfa de la secuencia de transformación (1 = sin efecto). */
    public float burstAlpha = 1f;
    /** Alfa del cuerpo principal para las auras SIEMPRE ENCENDIDAS (Ultra Instinct: el contorno se
     *  ve en tercera persona sin cargar y cargar solo lo intensifica). 1 en el resto. */
    public float idleBody = 1f;
    /** Alfa de las capas secundarias (cáscaras, borde) de esas mismas auras: 0 en reposo, 1 al
     *  cargar. 1 en el resto. */
    public float extras = 1f;

    /** Radio aproximado de la cáscara exterior a la altura del pecho (bloques), para anclar rayos
     *  y wisps a su superficie. */
    public float shellRadius() {
        if (body == null) return 0.7f * powerScale;
        return body.get(AuraBodyParams.Key.RADIUS) * bodyScale * burstScale;
    }

    /** Altura de la cáscara sobre los pies (bloques). */
    public float shellTop() {
        if (body == null) return 2.2f * powerScale;
        return (body.get(AuraBodyParams.Key.CENTER_Y) + body.get(AuraBodyParams.Key.HALF_HEIGHT)) * bodyScale * burstScale;
    }
}
