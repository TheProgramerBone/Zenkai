package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.client.aura.AuraTiltController;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.client.aura.compose.component.AuraComponent;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentConfig;
import com.hmc.zenkai.client.aura.flame.AuraFlameParams;
import com.hmc.zenkai.client.aura.flame.AuraFlameColors;
import com.hmc.zenkai.client.aura.flame.AuraFlameTuning;
import com.hmc.zenkai.client.aura.flame.AuraSystemDispatcher;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.aura.AuraFormula;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.aura.AuraModifier;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.feature.aura.AuraSignatureRegistry;
import com.hmc.zenkai.feature.aura.AuraState;
import com.hmc.zenkai.feature.aura.RaceSignature;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ORQUESTADOR del aura compuesta: un frame, cada jugador.
 * <pre>
 * flags sincronizados ──► AuraFrame (estado + AuraDynamics + pose del cuerpo) ──► AuraStyle
 *   (aura_type → familia) ──► AuraBodyParams (llama calibrada por el perfil → moldeado de familia →
 *   estilo → estados → estallido de transformación) ──► cuerpo exterior (+ interior) en SU programa
 *   ──► componentes (motes, rayos, wisps, cáscaras, contorno) ──► onda de transformación
 * </pre>
 * El dibujado entero va DIFERIDO por {@code KiVfxFrameQueue.submitAura}: el bloom sale de la misma
 * geometría, ordenada de atrás hacia delante con las técnicas de ki. Con shaderpack la cola se
 * reproduce en AFTER_LEVEL sin bloom (KiVfxBloomPipeline): una sola ruta, sin aura duplicada.
 * <p>
 * El servidor decide si hay aura (AuraClientState.isAuraActive, igual que el legacy); esto solo
 * decide cómo se ve. El PODER sigue saliendo únicamente de AuraFormula (tamaño, alfa, núcleo): la
 * familia y el estilo moldean la forma multiplicando esos números, nunca los sustituyen.
 * <p>
 * DISTANCIA: el cuerpo se dibuja hasta {@link #MAX_DIST} bloques con la malla del nivel de
 * detalle que toque (a lo lejos sigue leyéndose la familia por su silueta); los componentes se
 * recortan solos (sin rayos ni wisps en LOD lejano, la mitad a media distancia) y el bloom se
 * apaga en LOD lejano.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraComposer {

    private AuraComposer() {}

    private static final double MAX_DIST = 64.0;
    /** Separación lateral del aura fantasma de {@code /zenkaiaura compare} (bloques). */
    private static final float COMPARE_OFFSET = 2.5f;
    /** Aura siempre encendida (Ultra Instinct): alfa del contorno en reposo (1 = el de cargar) y
     *  segundos que tarda en intensificarse al cargar / en volver a reposo al soltar. */
    private static final float IDLE_BODY_ALPHA = 0.5f;
    private static final float IDLE_CHARGE_IN = 0.5f;
    private static final float IDLE_CHARGE_OUT = 1.2f;

    /** Estado persistente de un jugador (o del fantasma de comparación) entre frames. */
    private static final class Runtime {
        final AuraFrame frame = new AuraFrame();
        final TransformFx transform = new TransformFx();
        final FlightTrail trail = new FlightTrail();
        float ramp;
        /** 0..1: cuánto se está cargando, suavizado. Solo lo usan las auras siempre encendidas. */
        float charge;
        long lastNanos;
        @Nullable AuraProfile lastProfile;
        @Nullable AuraStyle style;
        @Nullable ResourceLocation lastFormId;
        boolean seenForm;
        /** Forma cuyo cambio (retrasado por ClientFormDelay) no debe disparar otra transformación: la escena ya arrancó con el aviso. */
        @Nullable ResourceLocation skipChangeTo;
        final List<AuraComponent> components = new ArrayList<>();

        boolean componentsAlive() {
            for (AuraComponent c : components) if (c.alive()) return true;
            return transform.running() || trail.alive();
        }

        void rebuild(AuraStyle s) {
            style = s;
            components.clear();
            for (AuraComponentConfig cfg : s.effectiveComponents()) components.add(cfg.create());
        }
    }

    private static final Map<Integer, Runtime> RUNTIMES = new HashMap<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static @Nullable Runtime ghost;

    /** ¿Tiene este jugador un aura compuesta viva (encendida o apagándose)? Lo usa la captura de pose. */
    public static boolean hasRuntime(int id) { return RUNTIMES.containsKey(id); }

    /**
     * Respuesta de suelo del aura compuesta de un jugador (campo "ground" del estilo o de su familia),
     * para AuraChargeGroundFx. null si el sistema compuesto no está activo o el jugador no tiene aura.
     */
    public static @Nullable String groundModeOf(int id) {
        if (!AuraSystemDispatcher.useComposed()) return null;
        Runtime rt = RUNTIMES.get(id);
        if (rt == null || rt.style == null) return null;
        return rt.style.ground() != null ? rt.style.ground() : rt.style.family().ground();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        boolean composed = mc.level != null && AuraSystemDispatcher.useComposed();
        if (mc.level == null || (!composed && RUNTIMES.isEmpty() && ghost == null)) {
            RUNTIMES.clear();
            ghost = null;
            return;
        }

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float seconds = (float) ((com.hmc.zenkai.client.aura.AuraClock.ticks() + pt) / 20.0);
        com.hmc.zenkai.client.aura.AuraClock.bindShaderTime(pt); // los shaders de cuerpo leen GameTime: que usen el mismo reloj
        // Prueba de movimiento A SALTOS (AuraDebug.lookFps): el mismo reloj cuantizado que los shaders de
        // cuerpo, para que agujas, rayos y vaivén salten a la vez. Las partículas simuladas (dt) no.
        if (AuraDebug.lookFps > 0f) seconds = (float) Math.floor(seconds * AuraDebug.lookFps) / AuraDebug.lookFps;
        long now = System.nanoTime();
        PoseStack pose = e.getPoseStack();
        boolean hud = AuraDebug.hud;
        if (hud) AuraDebug.LINES.clear();

        SEEN.clear();
        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p)) continue;
            int id = p.getId();
            SEEN.add(id);
            Runtime rt = RUNTIMES.get(id);
            boolean self = p == mc.player;
            boolean charging = composed && AuraClientState.isAuraActive(p);
            // Ultra Instinct: el contorno está SIEMPRE encendido y cargar ki solo lo intensifica.
            // En primera persona propia no se dibuja nunca (es FLOW), así que ahí no hay nada que
            // mantener vivo.
            boolean alwaysOn = composed && alwaysOnAura(p)
                    && !(self && mc.options.getCameraType().isFirstPerson());
            // Un aviso de primera transformación vigente mantiene el aura viva: el despertar de Rage Forms es automático (nadie carga)
            // y sin runtime previo el cambio de forma no se detectaba, así que no habría ni estallido.
            boolean active = charging || alwaysOn || com.hmc.zenkai.feature.rage.LongTransformState.pending(p.getUUID());
            if (rt == null) {
                if (!active) continue;
                rt = new Runtime();
                RUNTIMES.put(id, rt);
            }
            String type = self && AuraDebug.typeOverride != null ? AuraDebug.typeOverride : formAuraType(p);
            if (!process(rt, p, self, false, type, active, alwaysOn, charging, pt, seconds, now, camPos, left, up, pose, mc)) {
                RUNTIMES.remove(id);
                continue;
            }
            if (hud && self) describe(rt, type);
        }
        RUNTIMES.keySet().retainAll(SEEN);
        AuraAnchor.retain(SEEN);

        // Aura FANTASMA de comparación: la misma pose y el mismo poder que el aura propia, con otro
        // aura_type, a tu derecha. Siempre encendida mientras dure la comparación.
        if (composed && AuraDebug.compareType != null && mc.player != null) {
            if (ghost == null) ghost = new Runtime();
            process(ghost, mc.player, true, true, AuraDebug.compareType, true, false, true, pt, seconds, now, camPos, left, up, pose, mc);
        } else {
            ghost = null;
        }
        com.hmc.zenkai.client.aura.AuraClock.restoreShaderTime(pt);
    }

    /** Formas cuyo contorno se ve siempre, sin cargar (Ultra Instinct y su signo). */
    private static boolean alwaysOnAura(AbstractClientPlayer p) {
        return OverlayMetersSystem.isUiForm(p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
    }

    /**
     * @param active    hay algo que dibujar (carga/transformación/turbo, o aura siempre encendida).
     * @param alwaysOn  aura siempre encendida: el cuerpo se ve en reposo, atenuado, y las capas
     *                  secundarias solo aparecen al cargar.
     * @param charging  carga/transformación/turbo de verdad (lo que antes era "active" a secas).
     * @return false si el runtime ya no tiene nada que dibujar y se puede soltar.
     */
    private static boolean process(Runtime rt, AbstractClientPlayer p, boolean self, boolean isGhost, String auraType,
                                   boolean active, boolean alwaysOn, boolean charging, float pt, float seconds,
                                   long now, Vec3 camPos, Vector3f left, Vector3f up, PoseStack pose, Minecraft mc) {
        // Una secuencia de transformación en curso mantiene el aura ENCENDIDA aunque nadie esté cargando: el despertar de Rage Forms es
        // automático (sin hold) y sin esto el estallido no tenía cuerpo al que hincharse; solo se veía el polvo del suelo.
        if (!isGhost && rt.transform.running()) active = true;
        // Rampa en tiempo REAL (el render corre por frame, no por tick).
        float dt = rt.lastNanos == 0L ? 0f : Math.min(0.1f, (now - rt.lastNanos) / 1.0e9f);
        rt.lastNanos = now;
        float fadeIn = Math.max(0.01f, AuraFlameTuning.get(AuraFlameTuning.Param.FADE_IN));
        float fadeOut = Math.max(0.01f, AuraFlameTuning.get(AuraFlameTuning.Param.FADE_OUT));
        rt.ramp = Mth.clamp(rt.ramp + (active ? dt / fadeIn : -dt / fadeOut), 0f, 1f);
        if (rt.ramp <= 0f && !active && !rt.componentsAlive()) return false;

        AuraFrame f = rt.frame;
        boolean overridden = isGhost || (self && AuraDebug.typeOverride != null);
        // f.active = carga de verdad: en un aura siempre encendida los componentes (motas, wisps,
        // estela...) solo nacen mientras se carga, y en reposo queda únicamente el contorno.
        fill(f, p, pt, seconds, dt, camPos, left, up, charging, rt.ramp, mc, self, isGhost,
                overridden ? auraType : null);
        if (alwaysOn) {
            rt.charge = Mth.clamp(rt.charge + (charging ? dt / IDLE_CHARGE_IN : -dt / IDLE_CHARGE_OUT), 0f, 1f);
            float k = rt.charge * rt.charge * (3f - 2f * rt.charge);
            f.idleBody = Mth.lerp(k, IDLE_BODY_ALPHA, 1f);
            f.extras = k;
        } else {
            rt.charge = 0f;
            f.idleBody = 1f;
            f.extras = 1f;
        }

        // ── Estilo ──
        AuraStyle style = AuraStyleManager.get(auraType);
        if (self && !isGhost && AuraDebug.familyOverride != null) {
            AuraFamily fam = AuraFamilies.get(AuraDebug.familyOverride);
            if (fam != null) style = AuraStyle.bare(auraType, fam);
        }
        // equals y no identidad: un estilo desnudo se crea de nuevo cada frame, y recrear los
        // componentes cada frame borraría sus motas vivas.
        if (!style.equals(rt.style)) rt.rebuild(style);

        // ── Perfil (PODER) ── Encendida: el vivo; apagándose: el último visible.
        AuraProfile profile = active ? profileFor(p, auraType, overridden) : null;
        if (profile != null && profile.isVisible()) rt.lastProfile = profile;
        else profile = rt.lastProfile;
        f.profile = profile;
        f.powerF = AuraFlameParams.powerFactor(profile, seconds);
        f.powerScale = AuraFlameParams.sizeScale(f.powerF);

        // ── Transformación ── Detecta el cambio de forma (sincronizado) o la petición de debug.
        detectTransform(rt, f, p, self, isGhost, style);
        rt.transform.update(f);

        // ── Cuerpo ──
        f.body = null;
        boolean hidden = f.selfFirstPerson && f.fpOpacity <= 0f;
        boolean inRange = f.distSq <= MAX_DIST * MAX_DIST;
        if (profile != null && rt.ramp > 0.002f && !hidden && inRange) {
            buildBody(f, style, seconds, p.getId());
            if (layerOn(self, isGhost, "body")) submitBody(f, style, pose);
        }

        // ── Componentes: simulan siempre (lo vivo termina su vida aunque el aura se apague),
        // dibujan si el aura propia no está oculta en primera persona.
        for (AuraComponent c : rt.components) {
            c.update(f);
            if (!hidden && inRange && layerOn(self, isGhost, c.type())) c.submit(f, pose);
        }
        if (!hidden && inRange) rt.transform.submit(f, pose);
        // Estela de vuelo (no para el fantasma de comparación: no vuela).
        if (!isGhost) {
            rt.trail.update(f, style.effectiveTrail());
            if (inRange && layerOn(self, false, "trail")) rt.trail.submit(f, pose);
        }
        return true;
    }

    /** Corta la secuencia de transformación en curso de esa entidad (carga cancelada antes de completarse). */
    public static void cancelTransform(int entityId) {
        Runtime rt = RUNTIMES.get(entityId);
        if (rt == null) return;
        rt.transform.cancel();
        rt.skipChangeTo = null;
    }

    private static boolean layerOn(boolean self, boolean ghost, String layer) {
        return !(self || ghost) || AuraDebug.layerOn(layer);
    }

    private static void detectTransform(Runtime rt, AuraFrame f, AbstractClientPlayer p, boolean self, boolean isGhost,
                                        AuraStyle style) {
        boolean fire = false;
        boolean fromNotice = false;
        // /zenkaiaura transform: el aura propia y, si la hay, la fantasma a la vez (para comparar).
        if (self && AuraDebug.burstRequested) fire = true;
        if (!isGhost) {
            PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
            ResourceLocation id = form.getFormId();
            // Solo al ENTRAR en una forma (no al volver a base) y nunca la primera vez que se ve al
            // jugador: un jugador que aparece ya transformado no "explota" al entrar en pantalla.
            if (rt.seenForm && !Objects.equals(id, rt.lastFormId) && form.activeDef() != null) {
                // El cambio que el cliente retrasó hasta el estallido de una escena que YA arrancó con el aviso no vuelve a disparar.
                if (id.equals(rt.skipChangeTo)) rt.skipChangeTo = null;
                else fire = true;
            }
            // Un aviso de secuencia larga vigente arranca la escena YA, sin esperar al cambio de forma: el cliente lo retrasa hasta el
            // estallido (ClientFormDelay) para que la forma transicione conforme avanza la secuencia. También cubre que el runtime nazca
            // con el aviso y la forma en el mismo tick.
            if (com.hmc.zenkai.feature.rage.LongTransformState.pending(p.getUUID())) { fire = true; fromNotice = true; }
            rt.lastFormId = id;
            rt.seenForm = true;
        }
        if (!fire) return;
        String t = style.transform() != null ? style.transform() : style.family().transform();
        // Primera vez de esa forma para este jugador: el servidor lo avisó con LongTransformPacket. El aviso se gasta SIEMPRE (aunque
        // la preferencia lo apague) para que no quede armado para una transformación posterior.
        var pendingInfo = isGhost ? null : com.hmc.zenkai.feature.rage.LongTransformState.consumeInfo(p.getUUID());
        LongTransformMode mode = com.hmc.zenkai.config.ClientConfig.longTransformMode();
        boolean longSeq = pendingInfo != null && mode != LongTransformMode.OFF;
        if (fromNotice && pendingInfo != null && pendingInfo.form() != null
                && !pendingInfo.form().equals(p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId())) {
            rt.skipChangeTo = pendingInfo.form();   // la forma aún no ha cambiado: su cambio (en el estallido) no es otra transformación
        }        rt.transform.trigger(TransformFx.Type.of(t), f, rt.components, longSeq);
        // La escena que rodea al aura (pose, sonido, viñeta, título) arranca en el mismo instante que el estallido.
        if (longSeq) com.hmc.zenkai.client.rage.AwakeningScene.start(p, self, pendingInfo, f.outer, mode);
        // La cámara solo la toca la secuencia larga PROPIA y solo con la preferencia completa.
        if (longSeq && self && mode == LongTransformMode.FULL) TransformCameraFx.start();
    }

    private static void fill(AuraFrame f, AbstractClientPlayer p, float pt, float seconds, float dt, Vec3 camPos,
                             Vector3f left, Vector3f up, boolean active, float ramp, Minecraft mc, boolean self,
                             boolean isGhost, @Nullable String colorsOfType) {
        f.player = p;
        Vec3 at = p.getPosition(pt);
        f.bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        double offX = 0.0, offZ = 0.0;
        if (isGhost) {
            // A la derecha del cuerpo (con yaw 0 el jugador mira a +Z y su derecha es -X).
            float yaw = f.bodyYaw * Mth.DEG_TO_RAD;
            offX = -Mth.cos(yaw) * COMPARE_OFFSET;
            offZ = -Mth.sin(yaw) * COMPARE_OFFSET;
        }
        f.x = at.x + offX;
        f.y = at.y;
        f.z = at.z + offZ;
        f.camX = camPos.x;
        f.camY = camPos.y;
        f.camZ = camPos.z;
        f.leftX = left.x();
        f.leftY = left.y();
        f.leftZ = left.z();
        f.upX = up.x();
        f.upY = up.y();
        f.upZ = up.z();
        f.seconds = seconds;
        f.dt = dt;
        f.active = active;
        f.growth = ramp * ramp * (3f - 2f * ramp);
        f.distSq = camPos.distanceToSqr(f.x, f.y, f.z);
        f.lod = AuraBodyMesh.lodFor(f.distSq);
        f.selfFirstPerson = !isGhost && p == mc.player && mc.options.getCameraType().isFirstPerson();
        f.fpOpacity = f.selfFirstPerson ? ClientConfig.auraFirstPersonOpacityFrac() : 1f;
        // En primera persona la cámara está dentro del aura: el bloom sería un velo a pantalla
        // completa. De lejos el bloom no aporta y cuesta: se apaga en LOD lejano.
        f.bloomAllowed = !f.selfFirstPerson && ClientConfig.auraBloomEnabled() && f.lod != AuraBodyMesh.LOD_FAR;
        f.dynamics.read(p, self);

        AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
        f.bodyScale = scaleAttr == null ? 1f : (float) scaleAttr.getValue();

        f.anchorCaptured = AuraAnchor.resolve(p, at.x, at.y, at.z, f.bodyYaw, f.anchor);
        if (isGhost) {
            for (int i = 0; i < AuraAnchor.SEGMENTS; i++) {
                f.anchor.ax[i] += offX; f.anchor.bx[i] += offX;
                f.anchor.az[i] += offZ; f.anchor.bz[i] += offZ;
            }
        }

        int innerRgb, outerRgb;
        FormDef sample = colorsOfType == null ? null : sampleFormOf(colorsOfType);
        if (sample != null) {
            // aura_type forzado (debug, fantasma): los colores de una forma que lo usa, para que
            // "/zenkaiaura type ssj" se vea dorada y no del color base del jugador. AuraColors
            // sigue mandando en el aura real.
            innerRgb = sample.auraRgb();
            f.twoTone = sample.auraOuterRgb() >= 0;
            outerRgb = f.twoTone ? sample.auraOuterRgb() : innerRgb;
        } else {
            AuraColors.Layers layers = AuraColors.resolveLayers(p);
            f.twoTone = layers.hasOuter();
            innerRgb = layers.inner();
            outerRgb = layers.hasOuter() ? layers.outer() : layers.inner();
        }
        copy(AuraFlameColors.saturated(outerRgb), f.outer);
        copy(AuraFlameColors.saturated(innerRgb), f.inner);
        copy(KiVfxColors.derivedHot(f.outer[0], f.outer[1], f.outer[2]), f.hot);
    }

    private static void copy(float[] src, float[] dst) {
        dst[0] = src[0];
        dst[1] = src[1];
        dst[2] = src[2];
    }

    /** Una forma cargada con ese aura_type, o null. Prefiere la Saiyan (la referencia canónica de
     *  casi cada aura: "god" debe verse como SSG y no como human_god). */
    private static @Nullable FormDef sampleFormOf(String auraType) {
        FormDef any = null;
        for (FormDef d : FormDef.all()) {
            if (!auraType.equals(d.auraType())) continue;
            if (d.races().contains(com.hmc.zenkai.feature.Race.SAIYAN)) return d;
            if (any == null) any = d;
        }
        return any;
    }

    private static String formAuraType(AbstractClientPlayer p) {
        // Con un cambio retrasado en curso (secuencia larga) el aura ya es la de la forma de DESTINO (auraDef).
        FormDef def = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).auraDef();
        return def == null ? "default" : def.auraType();
    }

    /** Perfil de poder; con el aura_type forzado (debug, fantasma), con la firma de ESE tipo en vez
     *  de la de la forma (la raza sigue siendo la del jugador). */
    private static AuraProfile profileFor(AbstractClientPlayer p, String auraType, boolean overridden) {
        float turbo = AuraClientState.isTurbo(p) ? 1f : 0f;
        if (!overridden) return AuraManager.profileOf(p, turbo);
        AuraState st = AuraManager.stateOf(p, turbo);
        if (!st.isVisible()) return AuraProfile.OFF;
        AuraModifier mods = RaceSignature.of(PlayerStatsAttachment.get(p).raceStats().getRace())
                .plus(AuraSignatureRegistry.of(auraType));
        return AuraFormula.profile(st, mods);
    }

    /** Parámetros finales del cuerpo exterior y colores del cuerpo, en el frame. */
    private static void buildBody(AuraFrame f, AuraStyle style, float seconds, int seed) {
        AuraFlameParams base = AuraFlameParams.from(f.profile, seconds);
        AuraBodyParams outer = AuraBodyParams.from(base, f.powerScale);
        style.shape(outer);
        AuraDebug.BODY_OVERRIDES.forEach(outer::set);
        f.dynamics.applyToBody(outer, seconds, seed);
        if (f.burstScale != 1f) {
            for (Key k : Key.values()) if (k.dimensional) outer.mul(k, f.burstScale);
        }
        f.body = outer;
        f.bodyKind = style.family().body();
        f.cameraInside = !f.selfFirstPerson && cameraInside(f, outer);
        // Dentro del aura en 3ª persona = la misma situación que la 1ª persona: misma opacidad
        // (aura.first_person_opacity); a plena opacidad era un arco brillante cruzando la pantalla.
        if (f.cameraInside) f.fpOpacity = ClientConfig.auraFirstPersonOpacityFrac();

        float coreWhite = outer.get(Key.CORE_WHITE);
        copy(AuraFlameColors.heatedCenter(f.outer, coreWhite), f.outerCenter);
        // Un solo tono con capa interior (divina, oscura): el interior es el MISMO tinte calentado,
        // no un segundo color inventado.
        if (f.twoTone) copy(f.inner, f.innerEdge);
        else copy(f.outerCenter, f.innerEdge);
        copy(AuraFlameColors.heatedCenter(f.innerEdge, coreWhite), f.innerCenter);
    }

    /**
     * ¿Cámara dentro del cuerpo en 3ª persona? (2026-10-26, barrido de cámara del usuario: "el aura se
     * hace más pequeña, se malforma según la cámara"). Al chocar con el suelo o una pared la cámara de
     * 3ª persona se acerca y acaba DENTRO de la cáscara; el fsh oculta las caras traseras (las únicas
     * que se ven desde dentro) y el aura quedaba en arcos sueltos. Mismo volumen que dibuja el vsh de
     * la llama: elipsoide abajo, lágrima o elipsoide arriba; con un margen del 10%.
     */
    private static boolean cameraInside(AuraFrame f, AuraBodyParams b) {
        if (f.bodyKind == AuraBodyKind.FLOW) return false;
        // Las medidas del cuerpo ya llevan el estallido (burstScale); la escala del jugador va en la pose.
        double s = f.bodyScale;
        double dx = (f.camX - f.x) / s, dz = (f.camZ - f.z) / s, dy = (f.camY - f.y) / s - b.get(Key.CENTER_Y);
        double d = Math.sqrt(dx * dx + dz * dz);
        double r = b.get(Key.RADIUS) * 1.1;
        double w;
        if (dy < 0) {
            double y = -dy / Math.max(b.get(Key.LOWER_HALF), 0.001);
            if (y >= 1) return false;
            w = Math.sqrt(1 - y * y);
        } else {
            double y = dy / Math.max(b.get(Key.HALF_HEIGHT), 0.001);
            if (y >= 1) return false;
            double ell = Math.sqrt(1 - y * y);
            double tear = Math.pow(1 - y, 1.1) * (1 + 1.2 * y);
            w = Mth.lerp(b.get(Key.TEARDROP), ell, tear);
        }
        return d < r * w;
    }

    private static void submitBody(AuraFrame f, AuraStyle style, PoseStack pose) {
        submitBodyLayer(f, pose, f.bodyKind, f.body, f.outerCenter.clone(), f.outer.clone(), f.idleBody, 1f);
        AuraInnerSpec inner = style.effectiveInner();
        if (inner.draws(f.twoTone)) {
            AuraBodyKind innerKind = inner.kind() != null ? inner.kind() : f.bodyKind;
            // Encima del exterior (distancia un pelo menor; la cola dibuja de lejos a cerca): la
            // convención de aura_2/aura_8, ver AuraFlameColors.
            submitBodyLayer(f, pose, innerKind, inner.innerOf(f.body), f.innerCenter.clone(),
                    f.innerEdge.clone(), 1f, 0.9995f);
        }
    }

    /**
     * Encola UNA capa de cuerpo (la exterior, la interior, o la de un componente: cáscara, contorno).
     * Se encarga de la transformación (traslación al jugador + inclinación de vuelo + giro de cuerpo
     * + escala) o, para FLOW, de pasar las cápsulas de la pose real relativas a la cámara.
     *
     * @param alphaMul  factor extra de opacidad de esta capa.
     * @param distScale factor de la distancia de orden (&lt; 1 = encima del cuerpo, &gt; 1 = detrás).
     */
    public static void submitBodyLayer(AuraFrame f, PoseStack pose, AuraBodyKind kind, AuraBodyParams params,
                                       float[] center, float[] edge, float alphaMul, float distScale) {
        // El contorno de silueta (FLOW: Ultra Instinct y toda forma que use ese sistema, incluido el
        // aro) JAMÁS se dibuja en primera persona propia, sea cual sea la opción de % de aura
        // (pedido explícito del usuario, 2026-10-07): queda pegado a los brazos y a la cámara.
        if (kind == AuraBodyKind.FLOW && f.selfFirstPerson) return;
        float alpha = Mth.clamp(f.growth * f.fpOpacity * params.get(Key.OPACITY) * f.burstAlpha * alphaMul, 0f, 1f);
        if (alpha <= 0.002f) return;
        boolean noBloom = !f.bloomAllowed;
        int lod = f.lod;
        float growth = f.growth;
        Vec3 fromCam = new Vec3((f.x - f.camX) * distScale, (f.y - f.camY) * distScale, (f.z - f.camZ) * distScale);
        if (kind == AuraBodyKind.FLOW) {
            float[] segs = new float[AuraAnchor.SEGMENTS * AuraAnchor.SEG_FLOATS];
            AuraAnchor.Pose ap = f.anchor;
            for (int i = 0; i < AuraAnchor.SEGMENTS; i++) {
                int o = i * AuraAnchor.SEG_FLOATS;
                boolean hideHead = f.selfFirstPerson && i == AuraAnchor.HEAD;
                segs[o] = (float) (ap.ax[i] - f.camX);
                segs[o + 1] = (float) (ap.ay[i] - f.camY);
                segs[o + 2] = (float) (ap.az[i] - f.camZ);
                // En primera persona la caja de la cabeza rodea la cámara: fuera.
                segs[o + 3] = hideHead ? 0f : ap.r[i];
                segs[o + 4] = (float) (ap.bx[i] - f.camX);
                segs[o + 5] = (float) (ap.by[i] - f.camY);
                segs[o + 6] = (float) (ap.bz[i] - f.camZ);
                segs[o + 7] = hideHead ? 0f : ap.rz[i];
                segs[o + 8] = ap.lx[i];
                segs[o + 9] = ap.ly[i];
                segs[o + 10] = ap.lz[i];
            }
            // Pre-pasada de profundidad salvo en primera persona propia (con la cámara dentro de una
            // cápsula, sus caras traseras taparían cualquier cosa que se dibuje después).
            // El "camY" que recibe el shader es ahora la altura de la cámara SOBRE LOS PIES del jugador
            // (no la de mundo): el ruido del flujo se muestrea con la altura sobre los pies, así que no
            // se desplaza al volar. Con la de mundo, volar rápido barría el ruido por el contorno y
            // Ultra Instinct oscilaba mucho (2026-10-06).
            submitLayer(pose, fromCam, kind, params, growth, center, edge, alpha, lod, noBloom, segs,
                    (float) (f.camY - f.y), !f.selfFirstPerson, false);
            return;
        }
        pose.pushPose();
        pose.translate(f.x - f.camX, f.y - f.camY, f.z - f.camZ);
        // Misma inclinación de vuelo que la estela y el cuerpo (el legacy la avanza este frame).
        AuraTiltController.applyCurrent(pose, f.player.getId());
        pose.mulPose(Axis.YP.rotationDegrees(180f - f.bodyYaw));
        pose.scale(f.bodyScale, f.bodyScale, f.bodyScale);
        submitLayer(pose, fromCam, kind, params, growth, center, edge, alpha, lod, noBloom, null, (float) f.camY, false,
                f.selfFirstPerson || f.cameraInside);
        pose.popPose();
    }

    /**
     * Una capa de cuerpo. Sin estado dentro de la tarea (se reproduce dos veces por frame).
     * @param depthPrepass solo FLOW: antes del color, la misma geometría escribe SOLO profundidad.
     *                     Así de la unión de cápsulas se ve únicamente la superficie más cercana:
     *                     sin ella, el borde de cada cápsula se veía a través de las demás y el
     *                     contorno parecía una radiografía de piezas (capturas del 2026-09-30).
     */
    private static void submitLayer(PoseStack pose, Vec3 fromCam, AuraBodyKind kind, AuraBodyParams params,
                                    float growth, float[] center, float[] edge, float alpha, int lod,
                                    boolean noBloom, float @Nullable [] segs, float camY, boolean depthPrepass,
                                    boolean inside) {
        KiVfxFrameQueue.submitAura(pose, fromCam, (ps, buf) -> {
            boolean bloomPass = KiVfxFrameQueue.bloomPass();
            if (bloomPass && noBloom) return;
            ShaderInstance sh = AuraComposeShaders.shader(kind);
            if (sh == null) return;
            RenderType type = AuraComposeShaders.bodyType(kind);
            type.setupRenderState();
            AuraBodyUniforms.apply(sh, ps.last().pose(), params, growth, center, edge, bloomPass, segs, camY);
            AuraBodyUniforms.setInside(sh, inside);
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            if (kind == AuraBodyKind.FLOW) {
                if (depthPrepass) {
                    AuraBodyUniforms.setDepthPass(sh, params, true);
                    RenderSystem.colorMask(false, false, false, false);
                    RenderSystem.depthMask(true);
                    AuraBodyMesh.drawCapsules(sh);
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(false);
                    AuraBodyUniforms.setDepthPass(sh, params, false);
                }
                AuraBodyMesh.drawCapsules(sh);
            } else {
                AuraBodyMesh.draw(lod, sh);
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            type.clearRenderState();
        });
    }

    // ── Vista previa de la GUI ──────────────────────────────────────────────

    /**
     * Dibuja AHORA (sin cola: en la GUI no hay pasada de mundo ni bloom) el cuerpo del aura
     * compuesta sobre el personaje de la vista previa (StyleSelectionScreen), con el estilo de su
     * forma: cuerpo exterior, capa interior y cáscaras secundarias. Agujas, motas, rayos y estelas
     * viven en el mundo y no salen aquí. Proyección ortográfica: los shaders lo saben por
     * ZenkaiOrtho (si no, el borde fresnel sale torcido).
     *
     * @param pose   la del personaje en la GUI, en sus pies.
     * @param profile perfil FIJO de la vista previa (ver AuraPreviewRenderer: comparar tintes).
     */
    public static void drawPreview(PoseStack pose, AbstractClientPlayer p, AuraProfile profile, int innerRgb,
                                   int outerRgb, boolean twoTone, float seconds) {
        // /zenkaiaura type también manda aquí: se puede ver cualquier estilo en la propia pantalla.
        AuraStyle style = AuraStyleManager.get(AuraDebug.typeOverride != null ? AuraDebug.typeOverride : formAuraType(p));
        float powerF = AuraFlameParams.powerFactor(profile, seconds);
        AuraBodyParams outer = AuraBodyParams.from(AuraFlameParams.from(profile, seconds), AuraFlameParams.sizeScale(powerF));
        style.shape(outer);
        AuraBodyKind kind = style.family().body();

        float[] outerEdge = AuraFlameColors.saturated(twoTone ? outerRgb : innerRgb);
        float[] innerTint = AuraFlameColors.saturated(innerRgb);
        float coreWhite = outer.get(Key.CORE_WHITE);
        float[] outerCenter = AuraFlameColors.heatedCenter(outerEdge, coreWhite);
        float[] innerEdge = twoTone ? innerTint : outerCenter;
        float[] innerCenter = AuraFlameColors.heatedCenter(innerEdge, coreWhite);
        float[] segs = AuraAnchor.standingLocal();

        // Detrás primero: cáscaras secundarias, cuerpo, interior encima.
        for (AuraComponentConfig cfg : style.effectiveComponents()) {
            if (cfg instanceof com.hmc.zenkai.client.aura.compose.component.ShellComponent.Config sc) {
                AuraBodyParams sp = com.hmc.zenkai.client.aura.compose.component.ShellComponent.paramsOf(sc, outer, seconds);
                float[] edge = AuraFlameColors.saturated(twoTone ? outerRgb : innerRgb);
                drawNow(pose, com.hmc.zenkai.client.aura.compose.component.ShellComponent.kindOf(sc, kind), sp,
                        AuraFlameColors.heatedCenter(edge, coreWhite), edge, segs);
            }
        }
        drawNow(pose, kind, outer, outerCenter, outerEdge, segs);
        AuraInnerSpec inner = style.effectiveInner();
        if (inner.draws(twoTone)) {
            drawNow(pose, inner.kind() != null ? inner.kind() : kind, inner.innerOf(outer), innerCenter, innerEdge, segs);
        }
    }

    private static void drawNow(PoseStack pose, AuraBodyKind kind, AuraBodyParams params, float[] center,
                                float[] edge, float[] segs) {
        ShaderInstance sh = AuraComposeShaders.shader(kind);
        if (sh == null) return;
        RenderType type = AuraComposeShaders.bodyType(kind);
        type.setupRenderState();
        AuraBodyUniforms.apply(sh, pose.last().pose(), params, 1f, center, edge, false,
                kind == AuraBodyKind.FLOW ? segs : null, 0f, true);
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(params.get(Key.OPACITY), 0f, 1f));
        if (kind == AuraBodyKind.FLOW) AuraBodyMesh.drawCapsules(sh);
        else AuraBodyMesh.draw(AuraBodyMesh.LOD_NEAR, sh);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        type.clearRenderState();
    }

    private static void describe(Runtime rt, String auraType) {
        AuraFrame f = rt.frame;
        AuraStyle style = rt.style;
        if (style == null) return;
        List<String> l = AuraDebug.LINES;
        l.add("[Aura] sistema: " + AuraSystemDispatcher.effectiveMode() + (AuraSystemDispatcher.useComposed() ? " (compuesta)" : ""));
        l.add("aura_type: " + auraType + (AuraDebug.typeOverride != null ? " (forzado)" : "")
                + "  estilo: " + (AuraStyleManager.loadedTypes().contains(style.auraType()) ? "JSON" : "familia desnuda")
                + (AuraDebug.compareType != null ? "  comparando con: " + AuraDebug.compareType : ""));
        l.add("familia: " + style.family().id() + (AuraDebug.familyOverride != null ? " (forzada)" : "")
                + "  cuerpo: " + style.family().body() + "  interior: " + (style.effectiveInner().draws(f.twoTone) ? "sí" : "no")
                + "  dos tonos: " + (f.twoTone ? "sí" : "no") + "  pose: " + (f.anchorCaptured ? "real" : "sintética"));
        StringBuilder comps = new StringBuilder("capas:");
        comps.append(AuraDebug.layerOn("body") ? " body" : " (body)");
        for (AuraComponent c : rt.components) {
            boolean on = AuraDebug.layerOn(c.type());
            comps.append(' ').append(on ? c.type() : "(" + c.type() + ")").append(c.alive() ? "*" : "");
        }
        l.add(comps.toString());
        StringBuilder st = new StringBuilder("estados:");
        for (AuraDynamics.State s : AuraDynamics.State.values()) {
            if (f.dynamics.is(s)) st.append(' ').append(s.id()).append(AuraDebug.FORCED.containsKey(s) ? "!" : "");
        }
        l.add(st.toString());
        l.add(String.format(Locale.ROOT, "ki=%.0f%%  poder=%.2f  escala=%.2f  LOD=%d  bloom=%s  estallido=%.2f",
                f.dynamics.kiFrac * 100f, f.powerF, f.powerScale, f.lod, f.bloomAllowed, f.burstScale));
        l.add("suelo: " + (style.ground() != null ? style.ground() : style.family().ground())
                + "  transformación: " + (style.transform() != null ? style.transform() : style.family().transform()));
    }

    /** Consumida por {@code /zenkaiaura transform}; se limpia al final del frame. */
    @SubscribeEvent
    public static void onFrameEnd(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) AuraDebug.burstRequested = false;
    }
}
