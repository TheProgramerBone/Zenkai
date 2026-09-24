package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cola de dibujado DIFERIDO del VFX de ki (rework 2026-09-24, modelo de dragonminez
 * {@code PlayerEffectQueue}/dbrebirth: los renderers de entidad NO dibujan el ki).
 * <p>
 * ═══ POR QUÉ ═══ Antes cada técnica se dibujaba DENTRO del render de su entidad (o del listener
 * de la carga), mezclada en el {@code BufferSource} compartido con el resto de entidades y en un
 * orden que decidía el motor. El bloom no podía redibujar esa misma geometría, así que se
 * alimentaba de billboards SUSTITUTOS (discos con la textura del halo) que no respetaban ni la
 * forma, ni el culling, ni la oclusión real de la técnica — el origen del disco con anillos al
 * meter la cámara en BARRIER y de las "fuentes fantasma".
 * <p>
 * Ahora cada renderer {@link #submit encola} una tarea con su matriz ya capturada, y
 * {@link KiVfxBloomPipeline} la reproduce DOS veces en un sitio fijo del frame: una en el target
 * del mundo (lo que ve el jugador) y otra en el target de bloom, con {@link #bloomPass()} activo.
 * El bloom sale así de la MISMA malla con el MISMO shader, depth test y culling.
 * <p>
 * ═══ SIN ESTADO ENTRE FRAMES ═══ La cola se vacía al EMPEZAR cada frame (RenderFrameEvent.Pre),
 * no solo al consumirla: un frame en el que el mundo no llegue al stage de dibujado (pantalla de
 * carga, respawn, cambio de dimensión) no puede arrastrar tareas con entidades o matrices viejas
 * al siguiente.
 * <p>
 * La lógica de un frame (partículas, {@code consumeFxTick}, {@code rememberDrawn}) NO va dentro
 * de la tarea: la tarea se ejecuta dos veces por frame.
 * <p>
 * ═══ ORDEN DE ATRÁS HACIA DELANTE (bug real, vídeo 2026-09-24 09-27-24) ═══ Desde que el cuerpo
 * del ki usa mezcla NORMAL y sin escritura de depth, entre dos técnicas que se solapan en pantalla
 * gana la que se dibuja DESPUÉS, no la que está delante — con el aditivo de antes el orden daba
 * igual (sumar es conmutativo) y nunca se notó. La cola salía en el orden en que el motor recorre
 * las entidades: una Genki Dama blanca LEJANA podía pintar encima de una supernova cercana, y
 * cambiaba de golpe en un frame al entrar o salir algo del frustum. Síntoma del usuario: "al
 * volver a hacer spawn de los efectos se bugea" — un {@code clear}+{@code spawnall} crea las
 * entidades con otro id y en otro orden que las cargadas del guardado. {@link #sortBackToFront}
 * aplica el algoritmo del pintor por la distancia de cada técnica a la cámara, el mismo criterio
 * que vanilla usa para su geometría translúcida.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiVfxFrameQueue {
    private KiVfxFrameQueue() {}

    /** Dibujado de una técnica. {@code pose} llega con la matriz capturada en {@link #submit};
     *  {@code buffers} es el BufferSource principal y la tarea debe volcar lo que pida. */
    @FunctionalInterface
    public interface Task {
        void draw(PoseStack pose, MultiBufferSource.BufferSource buffers);
    }

    private record Entry(Matrix4f pose, Matrix3f normal, double distSq, Task task) {}

    private static final List<Entry> QUEUE = new ArrayList<>();
    private static boolean bloomPass = false;

    /** Encola {@code task} con una COPIA de la matriz actual de {@code pose} — el PoseStack del
     *  renderer de entidad se reutiliza en cuanto este vuelve.
     *  @param fromCamera centro de la técnica RELATIVO A LA CÁMARA (mundo − cámara), para
     *                    ordenarla con {@link #sortBackToFront}. */
    public static void submit(PoseStack pose, Vec3 fromCamera, Task task) {
        PoseStack.Pose last = pose.last();
        QUEUE.add(new Entry(new Matrix4f(last.pose()), new Matrix3f(last.normal()),
                fromCamera.lengthSqr(), task));
    }

    /** Lejos primero, cerca al final — ver "ORDEN DE ATRÁS HACIA DELANTE". Una vez por frame,
     *  antes de la primera {@link #replay}; las dos pasadas usan así el MISMO orden. */
    static void sortBackToFront() {
        QUEUE.sort(Comparator.comparingDouble(Entry::distSq).reversed());
    }

    public static boolean isEmpty() { return QUEUE.isEmpty(); }

    /** true solo mientras se reproduce la cola para el target de bloom — lo consulta
     *  {@link KiVfxRenderTypes#setupEnergy} para subir {@code ZenkaiBloomMode}. */
    public static boolean bloomPass() { return bloomPass; }

    /** Reproduce cada tarea encolada y vuelca el lote. No vacía la cola: se llama dos veces. */
    static void replay(boolean forBloom) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        bloomPass = forBloom;
        try {
            for (Entry e : QUEUE) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(e.pose());
                pose.last().normal().set(e.normal());
                e.task().draw(pose, buffers);
                // Volcado POR TAREA: si dos técnicas seguidas usan el mismo RenderType, el
                // BufferSource juntaría su geometría en un solo lote y deshace el orden de atrás
                // hacia delante entre ellas.
                buffers.endBatch();
            }
        } finally {
            bloomPass = false;
        }
    }

    static void clear() { QUEUE.clear(); }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) { QUEUE.clear(); }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { QUEUE.clear(); }
}
