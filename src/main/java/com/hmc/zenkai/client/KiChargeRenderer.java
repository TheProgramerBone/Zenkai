package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiBodyRenderer;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiMesh;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiMeshFactory;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiRenderTypes;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiShape;
import com.hmc.zenkai.client.render_and_model_entities.ki.KiVisual;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.TechniquePosition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Bola de ki mientras se carga una técnica, en el punto que diga su TechniquePosition
 * (mano, boca, frente...). La ve el resto, no solo quien carga: los datos llegan por
 * KiChargeStatePacket y el crecimiento se deriva del tick de inicio.
 *
 * EL CUERPO SALE DE {@link KiBodyRenderer}, el mismo que dibuja el proyectil ya disparado.
 * Antes esto era un quad plano orientado a cámara con ki_ball.png y sin banda ni volumen: en
 * tercera persona no se parecía a la bola con núcleo y contorno que sale después, y en primera
 * persona, pegado a la cara y sin nada que le dé profundidad, se leía como una mancha sólida
 * bloqueada en vez de una esfera de energía. Con la misma malla+shader que el proyectil, cargar
 * y disparar son el MISMO cuerpo a distinto tamaño, y una esfera real (a diferencia de un
 * billboard) se lee correctamente desde cualquier ángulo — incluido "un palmo de la cámara".
 *
 * LA MALLA ES CASI SIEMPRE UNA ESFERA DESNUDA (KiMeshFactory.chargeSphere()), aunque la técnica
 * dispare un haz: cargando, la energía todavía no tiene forma — un Kamehameha no es un tubo en
 * la palma, es energía contenida que solo se estira al soltarse. Color, bandas y alfas SÍ salen
 * del KiVisual real de la técnica (ver KiChargeClientState.Charge.type()), para que sea
 * reconociblemente la misma energía que el proyectil que sale después.
 *
 * DISK ES LA ÚNICA EXCEPCIÓN (ver drawBall/DISK_CANT_DEGREES): un disco ya se reconoce como
 * disco desde que se condensa, así que en vez de la esfera genérica usa su malla real desde el
 * primer frame de carga, orientada hacia donde mira el jugador y con el mismo ladeo que el
 * proyectil ya disparado (KiProjectileRenderer) — cargar y disparar quedan como el mismo objeto.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiChargeRenderer {
    private KiChargeRenderer() {}

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Radio en bloques: base + aporte del tamaño de la técnica. */
    private static final float BASE_RADIUS = 0.16f;
    private static final float SIZE_RADIUS = 0.05f;
    /** Al 0% ya se ve algo: una bola que nace de la nada se lee como un parpadeo.
     *  No aplica a EXPLOSION: ver PLAYER_RADIUS. BARRIER SÍ la usa (ver targetRadius) — a su
     *  tamaño de carga (~1 bloque) ya no necesita nacer a tamaño jugador. */
    private static final float START_SCALE = 0.35f;

    /** Suelo de EXPLOSION: nunca por debajo del tamaño de un jugador de pie, NI al empezar a
     *  cargar NI al soltarse (~1.8 bloques de diámetro → radio 0.9). */
    private static final float PLAYER_RADIUS = 0.90f;

    /** Radio de carga de BARRIER: bastante menor que su burbuja real (~2-2.5, ver
     *  KiTechniqueType.projectileSize) a propósito. Antes usaba ese mismo tamaño real (mismo
     *  motivo que EXPLOSION: no sale de la mano, simula el objeto ya formándose), pero a ESE
     *  tamaño la carga ya envolvía al jugador desde el primer instante en primera persona — un
     *  bloque sigue leyéndose como "algo grande condensándose" sin tapar la pantalla. */
    private static final float BARRIER_CHARGE_RADIUS = 1.0f;

    /**
     * Radio final (a carga completa / al soltarse) para el tamaño de técnica elegido, antes del
     * pulso de respiración. Para la mayoría de técnicas es el genérico BASE_RADIUS/SIZE_RADIUS:
     * energía de mano, que no pretende anticipar el tamaño del proyectil final.
     * BARRIER usa su propio radio fijo (BARRIER_CHARGE_RADIUS), independiente del tamaño de la
     * técnica: no sale de una mano, pero tampoco tiene sentido anticipar su tamaño real (~2-2.5),
     * que en primera persona sería tan grande como EXPLOSION.
     * EXPLOSION SÍ apunta al tamaño real: no sale de una mano, ancla al PECHO (ver
     * TechniquePosition) simulando que la esfera que va a detonar ya se está formando encima
     * del cuerpo — así que apunta al mismo radio visual que tendrá el proyectil ya soltado
     * (KiProjectileRenderer escala la misma malla a getBbWidth() × 1.5; radio = diámetro / 2),
     * en vez de al genérico.
     */
    private static float targetRadius(KiChargeClientState.Charge c) {
        if (c.type() == KiTechniqueType.BARRIER) {
            return BARRIER_CHARGE_RADIUS;
        }
        if (c.type() == KiTechniqueType.EXPLOSION) {
            return (float) (c.type().projectileSize(c.size()) * 1.5 * 0.5);
        }
        return BASE_RADIUS + SIZE_RADIUS * c.size();
    }

    /**
     * Radio de la bola de carga a un progreso dado (0 = empieza a cargar, 1 = carga completa).
     * GENÉRICO (incluye BARRIER, que ahora comparte esta rama): crece desde START_SCALE del
     * radio final (un puntito que no se lee como parpadeo) hasta el radio final completo —
     * animación de energía apareciendo/condensándose.
     * EXPLOSION: nunca por debajo de PLAYER_RADIUS, ni al empezar a cargar ni al soltarse.
     * Crece desde ese suelo hasta el radio de la técnica según el tamaño elegido
     * (targetRadius); en tamaño 1 el suelo y el objetivo coinciden, así que se queda constante
     * toda la carga en vez de nacer pequeña como las demás técnicas.
     */
    private static float chargeRadius(KiChargeClientState.Charge c, float progress) {
        float target = targetRadius(c);
        if (c.type() == KiTechniqueType.EXPLOSION) {
            return Mth.lerp(progress, PLAYER_RADIUS, Math.max(PLAYER_RADIUS, target));
        }
        return target * (START_SCALE + (1f - START_SCALE) * progress);
    }

    /** Ajuste SOLO visual y SOLO en primera persona: el modelo que ves ahí no está donde
     *  está tu modelo real. NO toca el spawn del proyectil, que es autoritativo del
     *  servidor y debe ser igual para cualquiera. Toca estos tres a ojo hasta que cuadre. */
    private static final float FP_FORWARD = 0.35f;
    private static final float FP_SIDE    = 0.10f;
    private static final float FP_DOWN    = 0.25f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = mc.level.getGameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        boolean drew = false;
        for (Player p : mc.level.players()) {
            KiChargeClientState.Charge c = KiChargeClientState.of(p);
            if (c == null) continue;

            // Ajuste de opacidad SOLO para la propia bola en primera persona: la de otros
            // jugadores siempre se ve a fuerza completa. El origen y el desvanecido al soltar
            // (más abajo) se siguen calculando igual aunque esto valga 0 — es puramente si se
            // dibuja o no, para que la memoria de PlayerHandTracker/fadeOf no se desincronice.
            KiVisual v = KiVisual.of(c.type());
            boolean selfFirstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            // EXPLOSION: oculta totalmente en primera persona propia, ya desde que se carga (no
            // solo una vez disparada — ver KiProjectileRenderer). Ni atenuada se veía bien: el
            // suelo de tamaño-jugador (chargeRadius) la deja pegada a la cámara desde el primer
            // frame. Pedido explícito, no un dampen más — cero, no un número pequeño.
            float fpOpacity = selfFirstPerson
                    ? (c.type() == KiTechniqueType.EXPLOSION
                            ? 0f : v.firstPersonOpacity(ClientConfig.kiFirstPersonOpacityFrac()))
                    : 1f;

            float progress = KiChargeClientState.progress(c, now);

            // ANCLA AL HUESO cuando hay dato de este frame. Es lo que hace que la esfera nazca
            // en las manos y viaje CON ellas mientras el brazo se coloca: el clip de Kamehameha
            // tarda 18 ticks en llegar a la cadera y con un punto fijo la esfera se quedaría
            // flotando en el pecho ese rato.
            // El respaldo es el offset estático de TechniquePosition, que es lo que había antes
            // y lo que sigue usando el servidor para el spawn del proyectil.
            var anchors = PlayerHandTracker.get(p.getId());
            Vec3 origin;
            if (c.type() == KiTechniqueType.EXPLOSION) {
                // EXPLOSION no sale de una mano ni de ningún hueso: ancla al CENTRO DEL CUERPO,
                // el mismo punto que ya usa la fase disparada (KiProjectileEntity.tickAttached /
                // KiProjectileRenderer.alignToOwner) para que cargar y "mecha pegada al dueño"
                // sean el mismo sitio y no salte al soltar. c.position() (BOTH_HANDS, heredado
                // de TechniqueAnimSet.BARRIER_POSITION vía KiTechnique.position()) NO se usa
                // aquí a propósito — es un valor vestigial que solo describe la pose de la
                // ANIMACIÓN de brazos, nunca tuvo sentido como punto de una esfera que no sale
                // de las manos. getPosition(pt) interpolada, no position() cruda: con la cruda
                // la esfera vibraría al andar (mismo motivo que el respaldo `else` de abajo).
                origin = p.getPosition(pt).add(0, p.getBbHeight() * 0.5, 0);
            } else if (anchors != null) {
                origin = camPos.add(anchors.resolve(c.position()));
            } else if (selfFirstPerson) {
                // Sin dato de hueso Y es la cámara local: caso de PAL filtrando la capa en
                // primera persona (ver PlayerHandTracker.get). Yaw DEL CUERPO, no de la cabeza,
                // para el cálculo completo — el de TechniquePosition.origin incluido. Antes solo el
                // offset de aquí abajo usaba yBodyRot; la BASE seguía saliendo de
                // TechniquePosition.origin(p, feet), que por dentro usa getLookAngle() (con
                // pitch), así que mirar hacia abajo desplazaba la esfera por ESE lado aunque el
                // offset extra no se moviera — de ahí el bug de "la esfera se mueve al mirar
                // abajo". yBodyRot es horizontal puro y sigue al cuerpo (con el lag de giro
                // normal de vanilla, no el de la cabeza), así que pasándolo a los dos sitios la
                // esfera queda por completo indiferente a hacia dónde mires.
                float bodyYaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot);
                double rad = Math.toRadians(bodyYaw);
                Vec3 look = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
                origin = c.position().origin(p, p.getPosition(pt), look);

                // El offset extra (mano-hacia-cámara) solo tiene sentido para posiciones de
                // MANO: MOUTH/FOREHEAD/EYES ya caen a un palmo del ojo por diseño propio de
                // TechniquePosition, así que sumarles el mismo desplazamiento pensado para
                // "brazo estirado a la altura de la cintura" las mandaba a un sitio que no
                // correspondía a ningún hueso real — la causa más visible de mala alineación en
                // primera persona para Masenko/Makankosappo.
                if (isHandAnchored(c.position())) {
                    Vec3 right = new Vec3(-look.z, 0.0, look.x);
                    origin = origin.add(look.scale(FP_FORWARD))
                            .add(right.scale(FP_SIDE))
                            .subtract(0.0, FP_DOWN, 0.0);
                }
            } else {
                // Sin dato de hueso y NO es la cámara local: jugador fuera de pantalla o
                // culling. Posición interpolada, que con p.position() cruda la bola vibra al
                // andar.
                origin = c.position().origin(p, p.getPosition(pt));
            }

            float radius = chargeRadius(c, progress)
                    * (1f + 0.05f * (float) Math.sin((now + pt) * 0.4));

            float r = ((c.rgb() >> 16) & 0xFF) / 255f;
            float g = ((c.rgb() >> 8) & 0xFF) / 255f;
            float b = (c.rgb() & 0xFF) / 255f;

            // El renderer es el único que sabe dónde acabó la esfera (hueso o respaldo), así
            // que es quien tiene que dejarlo apuntado para el desvanecido al soltar.
            KiChargeClientState.rememberDrawn(p.getId(), origin, radius);

            if (fpOpacity > 0f) {
                // DISK: la única técnica que SÍ toma su forma real mientras carga (ver
                // drawBall). Necesita hacia dónde apunta el jugador — durante el vuelo esa
                // orientación sale de la velocidad, pero aquí el proyectil todavía no existe,
                // así que se usa la mirada interpolada (misma fuente que el propio disparo del
                // servidor deriva de getLookAngle).
                Vec3 look = (v.shape() == KiShape.DISK) ? p.getViewVector(pt) : null;
                drawBall(pose, buffers, cam, camPos, origin, v, radius, fpOpacity, r, g, b, now, pt,
                        look, selfFirstPerson);
                drew = true;
            }
        }
        // Esferas apagándose: sitio y tamaño congelados, alfa bajando. Tapan el salto entre
        // donde estaba la esfera (hueso) y donde nace el proyectil (offset del servidor).
        for (Player p : mc.level.players()) {
            var f = KiChargeClientState.fadeOf(p);
            if (f == null) continue;

            float a = KiChargeClientState.fadeAlpha(f, now, pt);
            if (a <= 0f) { KiChargeClientState.dropFade(p.getId()); continue; }

            // El desvanecido de expiración (a) sigue su curso siempre, para que dropFade se
            // dispare en su momento; la opacidad de primera persona solo decide si se pinta.
            KiVisual v = KiVisual.of(f.type());
            boolean selfFirstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            // Misma exclusión de EXPLOSION que arriba: el hueco breve entre carga y proyectil
            // no es excusa para que reaparezca un frame.
            float alpha = (selfFirstPerson && f.type() == KiTechniqueType.EXPLOSION) ? 0f
                    : a * (selfFirstPerson
                            ? v.firstPersonOpacity(ClientConfig.kiFirstPersonOpacityFrac()) : 1f);
            float r = ((f.rgb() >> 16) & 0xFF) / 255f;
            float g = ((f.rgb() >> 8) & 0xFF) / 255f;
            float b = (f.rgb() & 0xFF) / 255f;
            float radius = f.radius() * (0.6f + 0.4f * a);   // encoge mientras se apaga

            if (alpha > 0f) {
                // Sin `look`: el desvanecido al soltar no rastrea hacia dónde mira nadie, así
                // que un disco aquí cae al respaldo de esfera en vez de quedar mal orientado.
                // Es un estado breve (el hueco entre la carga y el proyectil), no se nota.
                drawBall(pose, buffers, cam, camPos, f.origin(), v, radius, alpha, r, g, b, now, pt,
                        null, selfFirstPerson);
                drew = true;
            }
        }
        if (drew) buffers.endBatch();
    }

    /** ¿Esta posición sale de una mano? Decide si el ajuste extra de primera persona
     *  (FP_FORWARD/FP_SIDE/FP_DOWN, calibrado a ojo para "brazo estirado") aplica — a
     *  MOUTH/FOREHEAD/EYES, ya pegadas al ojo por diseño de TechniquePosition, ese mismo
     *  desplazamiento las saca de sitio en vez de alinearlas. */
    private static boolean isHandAnchored(TechniquePosition pos) {
        return switch (pos) {
            case RIGHT_HAND, LEFT_HAND, BOTH_HANDS -> true;
            case MOUTH, FOREHEAD, EYES -> false;
        };
    }

    /** Mismo ladeo que KiProjectileRenderer aplica al disco ya disparado (ver su comentario):
     *  sin él, la cara queda casi exactamente de frente a la mirada y de perfil se lee como una
     *  lámina vertical en vez de un disco tumbado. Público porque TechniqueEditScreen (paquete
     *  distinto) lo reutiliza en la vista previa del editor — un solo número para las dos vistas
     *  de "disco cargando", en vez de otra copia que se pueda desincronizar. */
    public static final float DISK_CANT_DEGREES = 60f;

    /**
     * Cuerpo (esfera real, no billboard, salvo DISK con `look` — ver más abajo) + halo. El
     * cuerpo se lee bien desde cualquier ángulo sin orientarlo a cámara — es geometría 3D, no
     * un sprite plano — así que solo el halo, que sí es un billboard aditivo, necesita
     * encararla.
     */
    private static void drawBall(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                 Camera cam, Vec3 camPos, Vec3 origin, KiVisual v, float radius,
                                 float alphaMul, float r, float g, float b, long now, float pt,
                                 @Nullable Vec3 look, boolean selfFirstPerson) {
        // La malla de KiMeshFactory mide radio 0.5 en espacio propio: escalar por el diámetro
        // deja el radio en pantalla igual a `radius`.
        float size = radius * 2f;

        // DISK es la ÚNICA excepción a "cargando, la energía no tiene forma" (ver
        // KiMeshFactory.chargeSphere): un disco ya se reconoce como disco desde que empieza a
        // condensarse, así que en vez de la esfera desnuda usa su malla real, orientada hacia
        // donde mira el jugador (con `look`) y con el mismo ladeo que el proyectil ya disparado
        // — cargar y disparar quedan como el mismo objeto, solo que uno vuela y el otro no.
        // Sin `look` (el desvanecido al soltar, que no rastrea mirada) cae al respaldo esfera.
        boolean asDisk = v.shape() == KiShape.DISK && look != null;
        KiMesh mesh = asDisk ? KiMeshFactory.get(v) : KiMeshFactory.chargeSphere();

        pose.pushPose();
        pose.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
        if (asDisk) {
            pose.mulPose(Axis.YP.rotationDegrees(
                    (float) (Math.atan2(look.x, look.z) * 180.0 / Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees(
                    (float) (-Math.asin(look.y) * 180.0 / Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees(DISK_CANT_DEGREES));
            pose.mulPose(Axis.ZP.rotationDegrees((now + pt) * 22f));
        }
        KiBodyRenderer.render(buffers, v, mesh, pose, size, r, g, b, alphaMul);
        pose.popPose();

        if (v.haloAlpha() <= 0f) return;
        // El halo NO usa el `alphaMul` del cuerpo: ese lleva el dampen extra de
        // KiVisual.firstPersonOpacity para técnicas que envuelven la cámara (BARRIER/EXPLOSION),
        // pensado para el cuerpo (alfa normal). El halo es SIEMPRE aditivo
        // (KiRenderTypes.ADDITIVE_TRANSPARENCY: suma luz, no se mezcla), así que se rige
        // directamente por el valor crudo del slider "Ki Ball Opacity" — sin ningún extra
        // nuestro encima — para que sea ese único número, ajustable por el jugador, el que
        // decide qué tan marcado se ve, en vez de un dampen fijo en código.
        float haloOpacity = selfFirstPerson ? ClientConfig.kiFirstPersonOpacityFrac() : 1f;
        if (haloOpacity <= 0f) return;
        float pulse = 1f + 0.07f * Mth.sin((now + pt) * 0.22f);
        float half = radius * v.haloScale() * pulse;

        pose.pushPose();
        pose.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
        pose.mulPose(cam.rotation());
        PoseStack.Pose mat = pose.last();
        VertexConsumer vc = buffers.getBuffer(KiRenderTypes.additive(KiRenderTypes.HALO_TEXTURE));
        float a = v.haloAlpha() * haloOpacity;
        vert(vc, mat, -half, -half, 0f, 1f, r, g, b, a);
        vert(vc, mat,  half, -half, 1f, 1f, r, g, b, a);
        vert(vc, mat,  half,  half, 1f, 0f, r, g, b, a);
        vert(vc, mat, -half,  half, 0f, 0f, r, g, b, a);
        pose.popPose();
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, float x, float y,
                             float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, 0f)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(m, 0, 0, 1);
    }
}
