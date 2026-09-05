package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.forms.*;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.skills.DivineForms;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Estado de transformación del jugador. DOS CAPAS INDEPENDIENTES:
 *   - formId: la transformación (SSJ, formas arcosianas...). Datos en datapack (FormDef).
 *   - kaioken: escalón del Kaioken, que se APILA sobre la forma y no se apaga al cambiarla.
 * Una sola tecla, DOS ESCALERAS. El interruptor del kaioken decide cuál sube la tecla de
 * transformar: con él puesto, escalones de kaioken; sin él, la cadena de formas hacia la
 * seleccionada en la rueda. Apagar el interruptor NO baja el escalón activo: solo devuelve
 * la tecla a la escalera de formas.
 * La RUEDA no transforma, solo elige (selectedForm / kaiokenSwitch). Aquí no hay números:
 * los tiempos y porcentajes vienen del datapack (FormDef) o del enum (KaiokenTier); esto es
 * únicamente la máquina de estados del hold y la maestría acumulada.
 *
 * EXCEPCIÓN a "una sola tecla": el ritual de Oozaru (Base -> Oozaru -> Super Oozaru -> SSJ4,
 * ver OozaruSystem) mete un tercer disparador, oozaruForced, que NO es la tecla — lo escribe
 * OozaruSystem cada tick por la condición ambiental (cola + luna llena) y tiene prioridad
 * ABSOLUTA sobre cualquier escalera mientras el jugador esté en Base. Los tramos 2 y 3 sí son
 * la tecla de siempre, con dos gates especiales en resolveNextForm/canAdvance (ver ahí).
 */
public class PlayerFormAttachment {

    /** 0 = off. Sync de progreso cada N ticks, para que la animación se vea "en vivo". */
    private static final int PROGRESS_SYNC_EVERY = 1;

    /** El kaioken no está en el datapack (son cinco números), así que su hold vive aquí. */
    private static final int KAIOKEN_HOLD_TICKS = 30; // 1.5 s por escalón

    /** Radio del grito del kaioken, en bloques. */
    private static final double SHOUT_RANGE = 32.0;
    
    /** Techo acoplado de Potential Unlock, resuelto en servidor y sincronizado. Lo refresca
      *  FormSystem cada tick; el cliente solo lo lee. No se recalcula en cada consumidor
      *  porque drainPerTick no tiene Player y es público para la GUI. */
    private double puCeiling = 0.0;

    public double getPuCeiling() { return puCeiling; }

    /** Servidor: recalcula el techo. Barato (recorre como mucho 5 formas). */
    public void refreshPotentialCeiling(Player p) {
        this.puCeiling = PotentialUnlock.referenceCeiling(p);
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    private boolean transformHeld = false;
    private boolean transforming = false;
    private int holdTicks = 0;
    private int cooldownTicks = 0;

    private ResourceLocation formId = FormIds.BASE;
    private KaiokenTier kaioken = KaiokenTier.OFF;

    /** Forma OBJETIVO elegida en la rueda: ATAJO de un solo uso, no un techo. null = sin
     *  límite, la tecla sube un escalón por vez como antes. Ponlo a FormIds.BASE si quieres
     *  que pasar por la rueda sea obligatorio. Al ALCANZAR la forma elegida se consume sola
     *  (vuelve a null, ver tickFormLadder) para que mantener la tecla siga subiendo la
     *  escalera un escalón a la vez sin tener que volver a la rueda por cada fase. */
    private ResourceLocation selectedForm = null;

    /** Interruptor del Kaioken: decide QUÉ escalera sube la tecla de transformar. */
    private boolean kaiokenSwitch = false;

    /**
     * Volcado cada tick por OozaruSystem (ver esa clase): condición ambiental (raza + cola +
     * luna llena, OozaruConditions) para el PRIMER tramo del ritual de Oozaru. A diferencia de
     * transformHeld (la tecla H), esto NO lo controla el jugador — tiene prioridad ABSOLUTA
     * sobre cualquier otra escalera en serverTick() mientras esté en Base, igual que en el
     * canon ver la luna anula el control voluntario del saiyan. Se sincroniza (save/load, ver
     * abajo) porque currentHoldTarget() lo necesita en el CLIENTE para pintar el color correcto
     * del gui de transformación (mismo motivo que chargingKi/overdriveCharging en
     * PlayerStateFlags — ver el gotcha documentado en CLAUDE.md).
     */
    private boolean oozaruForced = false;

    /** Maestría por forma (clave = formId, 0..100). */
    private final Map<String, Float> formMastery = new HashMap<>();

    /** Fin del strain (gameTime absoluto). 0 = sin fatiga. Ver KaiokenSystem. */
    private long strainUntil = 0L;

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isTransformHeld()      { return transformHeld; }
    public boolean isTransforming()       { return transforming; }
    public int getHoldTicks()             { return holdTicks; }
    public int getCooldownTicks()         { return cooldownTicks; }
    public ResourceLocation getFormId()   { return formId; }
    public ResourceLocation getSelectedForm() { return selectedForm; }
    public boolean isKaiokenSwitch()      { return kaiokenSwitch; }
    public boolean isOozaruForced()       { return oozaruForced; }

    /**
     * Nunca null: es el embudo por el que pasa el mod (TickHandlers, aura, stats).
     * Si el campo se corrompe por cualquier vía, se corta aquí en vez de reventar arriba.
     */
    public KaiokenTier getKaioken() {
        if (kaioken == null) kaioken = KaiokenTier.OFF;
        return kaioken;
    }

    /** true si está en BASE y sin kaioken: nada que mostrar ni que drenar. */
    public boolean isBase() {
        return FormIds.BASE.equals(formId) && !getKaioken().isOn();
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setTransformHeld(boolean held) {
        this.transformHeld = held;
        if (!held) clearProgress(false); // soltar corta el proceso
    }

    public void setFormId(ResourceLocation id) {
        this.formId = (id == null) ? FormIds.BASE : id;
    }

    /** Kaioken y transformación son capas INDEPENDIENTES: cambiar de forma no lo apaga. */
    public void setKaioken(KaiokenTier tier) {
        this.kaioken = (tier == null) ? KaiokenTier.OFF : tier;
    }

    public void setSelectedForm(ResourceLocation id) { this.selectedForm = id; }

    public void setKaiokenSwitch(boolean v) { this.kaiokenSwitch = v; }

    /** Solo lo llama OozaruSystem. Ver el javadoc del campo. */
    public void setOozaruForced(boolean v) { this.oozaruForced = v; }

    // ── Datos de la forma activa (del datapack) ──────────────────────────────

    /** Def de la forma activa, o null en BASE / si el datapack ya no la define. */
    public FormDef activeDef() { return FormRegistry.get(formId); }

    /** Maestría de la forma activa (0..100). */
    public float activeMastery() { return getFormMastery(formId); }

    /** Fracción que la FORMA suma al multiplicador, ya interpolada por maestría. */
    public double formStatPercent() {
        // Potential Unlock no saca su % del JSON: va acoplado al techo del jugador.
        if (FormIds.POTENTIAL_UNLOCK.equals(formId)) {
            return PotentialUnlock.statPercent(puCeiling, activeMastery());
        }
        FormDef d = activeDef();
        return d == null ? 0.0 : d.statPercent(activeMastery());
    }

    /** Fracción que suman FORMA + KAIOKEN. Modelo aditivo: total = 1 + esto. */
    public double totalStatPercent() {
        return formStatPercent() + getKaioken().statPercent();
    }

    /** Ki drenado por tick por la forma activa, ya interpolado por maestría y con el
     *  multiplicador de SPI aplicado (ver FormDef.drainMultiplier). */
    public double formKiDrainPerTick(int spi) {
        FormDef d = activeDef();
        return d == null ? 0.0 : d.effectiveKiDrainPerTick(activeMastery(), spi);
    }

    // ── Máquina de estados del hold ──────────────────────────────────────────

    /**
     * ¿La tecla de transformar tiene algo que hacer AHORA MISMO? ÚNICO sitio que lo decide,
     * para que la animación del cliente y el efecto del servidor no discrepen: al tope del
     * kaioken, o con la forma destino sin comprar, no se arranca nada.
     */
    public boolean canAdvance(Player p, Race race) {
        if (race == null) return false;
        // Con strain no se sube escalón: se consulta desde cliente y servidor, así que el
        // jugador ve que la transformación no arranca en vez de cancelársela a medias.
        if (kaiokenSwitch) {
            return !isStrained(p.level().getGameTime()) && nextKaiokenTier(p) != null;
        }
        if (potentialTarget(p) != null) return true;
        // Último tramo del ritual de Oozaru (ver resolveNextForm): SuperForms.unlocked(SSJ4)
        // da FALSE hasta que el ritual está completo, así que exigirlo aquí lo haría imposible
        // de completar — completar ESTE hold es justo lo que lo marca (completeSsj4Ritual).
        if (FormIds.SUPER_OOZARU.equals(formId)) {
            return FormRegistry.get(FormIds.SSJ4) != null;
        }
        ResourceLocation target = targetForm(p, race);
        if (target == null) return false;

        return FormRegistry.isAllowed(race, target)
                && FormRegistry.get(target) != null
                && SuperForms.unlocked(p, target);
    }

    /**
     * Tick SOLO SERVIDOR.
     * @return dirty si cambió algo y conviene sync inmediato.
     */
    public boolean serverTick(Player p, PlayerStatsAttachment stats, PlayerVisualAttachment visual) {
        if (cooldownTicks > 0) cooldownTicks--;

        // Oozaru tiene prioridad ABSOLUTA sobre cualquier otra escalera y NO depende de
        // transformHeld: no hay tecla que pulsar, la activa OozaruSystem por la condición
        // ambiental (ver oozaruForced). Solo aplica desde Base, y respeta el cooldown propio
        // (forceBase() ya deja uno tras revertir, evitando un flicker si la condición oscila).
        if (oozaruForced && FormIds.BASE.equals(formId) && cooldownTicks <= 0) {
            return tickOozaruLadder();
        }

        if (!transformHeld || cooldownTicks > 0) return clearProgress(false);

        Race race = stats.getRace();
        if (!canAdvance(p, race)) return clearProgress(false);

        if (kaiokenSwitch) return tickKaiokenLadder(p);
        if (SkillToggles.isOn(p, SkillEffects.POTENTIAL_UNLOCK)) return tickPotentialLadder(p);
        return tickFormLadder(p, race);
    }

    /** Primer tramo del ritual de Oozaru: el "hold" lo activa OozaruSystem (oozaruForced), no
     *  la tecla del jugador. Reusa holdTicks/transforming para que el HUD
     *  (TransformGaugeOverlay) pinte la misma barra de progreso que cualquier otra
     *  transformación, sin código de cliente nuevo — ver currentHoldTarget(). */
    private boolean tickOozaruLadder() {
        FormDef def = FormRegistry.get(FormIds.OOZARU);
        int required = (def == null) ? 0 : def.holdTicks();
        if (required <= 0) return clearProgress(false);

        boolean was = transforming;
        if (advanceHold(required)) return progressDirty(was);

        setFormId(FormIds.OOZARU);
        finishHold();
        return true;
    }

    /**
     * Escalera de formas: se va DIRECTO a la forma seleccionada en la rueda. Sin selección se
     * mantiene el comportamiento viejo de subir un escalón. El hold que se exige es el de la
     * forma DESTINO, así que saltar de Base a SSJ4 cuesta el hold de SSJ4.
     */
    private boolean tickFormLadder(Player p, Race race) {
        ResourceLocation target = targetForm(p, race);
        if (target == null) return clearProgress(false);

        FormDef targetDef = FormRegistry.get(target);
        int required = (targetDef == null) ? 0 : targetDef.holdTicks();
        if (required <= 0) return clearProgress(false);

        boolean was = transforming;
        if (advanceHold(required)) return progressDirty(was); // aún cargando

        // Último tramo del ritual de Oozaru: llegar aquí ES lo que desbloquea SSJ4 para
        // siempre (ver resolveNextForm/canAdvance, y completeSsj4Ritual más abajo). Antes de
        // setFormId para que quede claro que es un efecto de ESTE hold completándose.
        if (FormIds.SUPER_OOZARU.equals(formId) && FormIds.SSJ4.equals(target)) {
            completeSsj4Ritual(p);
        }

        setFormId(target);
        // La selección de la rueda es un ATAJO de un solo uso, no un techo permanente: al
        // alcanzarla se consume (selectedForm vuelve a null) para que targetForm() caiga en
        // resolveNextForm() en el siguiente hold. Sin esto, seleccionar SSJ2 y llegar a SSJ2
        // dejaba selectedForm apuntando ahí para siempre — target.equals(formId) hacía que
        // canAdvance() devolviera false en cuanto se llegaba, y mantener la tecla pulsada ya
        // no subía más escalones sin volver a la rueda a elegir SSJ3 a mano.
        if (target.equals(selectedForm)) selectedForm = null;
        finishHold();
        return true;
    }

    /** Escalera de kaioken: mismo gesto, otra capa. No toca la forma. */
    private boolean tickKaiokenLadder(Player p) {
        KaiokenTier next = nextKaiokenTier(p);
        if (next == null) return clearProgress(false); // ya en el tope que permite su nivel

        boolean was = transforming;
        if (advanceHold(KAIOKEN_HOLD_TICKS)) return progressDirty(was);

        setKaioken(next);
        // El grito va AQUÍ, no antes: solo cuando el escalón se aplica de verdad. Anunciarlo
        // en cada tick del hold inundaba la action bar (y con 'next' null reventaba).
        announceKaioken(p, next);
        finishHold();
        return true;
    }

    /**
     * Suma un tick al hold. Devuelve true si ya se completó.
     * Un único sitio para el progreso: las dos escaleras comparten ritmo y sync.
     */
    private boolean advanceHold(int required) {
        transforming = true;
        holdTicks++;
        return holdTicks < required;
    }

    /** ¿Merece sync este tick de carga? Al arrancar siempre (el cliente debe empezar la
     *  animación ya); después, según PROGRESS_SYNC_EVERY. */
    private boolean progressDirty(boolean wasTransforming) {
        if (!wasTransforming) return true;
        return PROGRESS_SYNC_EVERY > 0 && (holdTicks % PROGRESS_SYNC_EVERY == 0);
    }

    /** Cierre común: corta la animación y obliga a soltar para no encadenar escalones. */
    private void finishHold() {
        transforming = false;
        holdTicks = 0;
        transformHeld = false;
        cooldownTicks = 10;
    }

    /** Corta el proceso de hold. Devuelve true si había algo que cortar (hay que sincronizar). */
    private boolean clearProgress(boolean dirty) {
        if (transforming || holdTicks != 0) {
            transforming = false;
            holdTicks = 0;
            return true;
        }
        return dirty;
    }

    // ── Resolución de destino ────────────────────────────────────────────────

    /** Destino de la escalera de formas: la selección de la rueda, o el siguiente escalón.
     *  null si no hay nada que hacer (ya está ahí, o el destino es volver a base). */
    private ResourceLocation targetForm(Player p, Race race) {
        ResourceLocation target = (selectedForm != null)
                ? selectedForm
                : resolveNextForm(p, race, formId);

        if (target == null || target.equals(formId)) return null;
        if (FormIds.BASE.equals(target)) return null; // volver a base es el toque corto
        return target;
    }

    /**
     * Siguiente forma de la cadena para esa raza, o null si no aplica. La cadena la
     * reconstruye FormRegistry a partir de los 'parent' del datapack.
     * ÚNICO gate de compra: sin el nivel de super_forms esa forma no existe para él.
     */
    private static ResourceLocation resolveNextForm(Player p, Race race, ResourceLocation current) {
        if (race == null) return null;

        // Último tramo del ritual de Oozaru: Super Oozaru no declara 'parent' hacia SSJ4 en el
        // datapack (SSJ4 ya es hijo de SSJ3 en la cadena normal, comprada con TP) — este salto
        // es la excepción explícita. Se puede intentar en cuanto se llega a Super Oozaru
        // (SuperForms.readyForSuperOozaru ya exigió el nivel máximo para llegar hasta aquí);
        // completarlo MARCA el ritual (completeSsj4Ritual) — sin él, SuperForms.unlocked(SSJ4)
        // sigue dando false aunque el nivel ya esté comprado.
        if (FormIds.SUPER_OOZARU.equals(current)) {
            return FormRegistry.get(FormIds.SSJ4) != null ? FormIds.SSJ4 : null;
        }

        ResourceLocation next = FormRegistry.nextFrom(
                current == null ? FormIds.BASE : current, race);
        if (next == null || !FormRegistry.isAllowed(race, next)) return null;
        // Oozaru -> Super Oozaru exige tener YA comprado super_forms hasta el nivel MÁXIMO
        // (SuperForms.readyForSuperOozaru): SuperForms.unlocked() por sí solo daría true de
        // oficio (super_oozaru está fuera de la cadena, depthOf <= 0), así que sin este
        // chequeo cualquier saiyan que llegara a Oozaru podría controlar la mutación sin haber
        // entrenado nada. Este MISMO chequeo, aplicado a 'next' == SSJ4 vía
        // SuperForms.unlocked() más abajo, es también lo que bloquea la cadena NORMAL
        // (SSJ3 -sube la tecla-> SSJ4) hasta que el ritual esté hecho, aunque el nivel ya
        // esté pagado: unlocked(SSJ4) exige AMBAS cosas (ver su javadoc).
        if (FormIds.SUPER_OOZARU.equals(next) && !SuperForms.readyForSuperOozaru(p)) return null;
        // FormRegistry.nextFrom solo sabe seguir "el primer hijo" — con hermanas divinas
        // (ssj_blue/ssj_rose) eso siempre sería la misma, sin importar si este jugador es
        // divino. DivineForms.resolveForPlayer cambia 'next' por la hermana que de verdad le
        // corresponde antes de comprobar unlocked(); para cualquier forma sin hermanas, la
        // devuelve tal cual.
        next = DivineForms.resolveForPlayer(p, next);
        return SuperForms.unlocked(p, next) ? next : null;
    }

    /**
     * Marca el ritual de Oozaru como completado (PlayerStatsAttachment.hasSsj4Ritual,
     * persistente). NO otorga ningún nivel de super_forms: el nivel máximo ya se pagó con TP
     * para poder llegar hasta aquí (SuperForms.readyForSuperOozaru) — lo que faltaba era
     * ESTO, la parte que SuperForms.unlocked(SSJ4) exige además del nivel. A partir de aquí
     * SSJ4 queda desbloqueado/visible en la rueda para siempre (ver WheelMenu.forms), incluso
     * si el nivel se pierde y hay que volver a comprarlo por un respec. No hace nada si ya
     * estaba hecho (repetir el ritual no debe reanunciar).
     */
    private static void completeSsj4Ritual(Player p) {
        if (p.level().isClientSide()) return;
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(p);
        if (stats.hasSsj4Ritual()) return;
        stats.setHasSsj4Ritual(true);
        p.displayClientMessage(Component.translatable("message.zenkai.oozaru.ssj4_unlocked")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }

    /** Siguiente escalón de kaioken por encima del actual que permita su nivel. null si tope. */
    private KaiokenTier nextKaiokenTier(Player p) {
        int lvl = SkillEffects.level(p, "kaioken");
        KaiokenTier[] all = KaiokenTier.values();
        for (int i = getKaioken().ordinal() + 1; i < all.length; i++) {
            if (lvl >= all[i].requiredLevel()) return all[i];
        }
        return null;
    }
    
    /**
    * Destino de la escalera de Potential Unlock: solo desde BASE y solo con el interruptor
    * puesto. Exigir BASE es lo que hace que sea "el estado definitivo" y no una capa más
    * encima de SSJ: hay que bajar antes de sacarlo.
    */
    private ResourceLocation potentialTarget(Player p) {
        if (!SkillToggles.isOn(p, SkillEffects.POTENTIAL_UNLOCK)) return null;
        if (!FormIds.BASE.equals(formId)) return null;
        return FormRegistry.get(FormIds.POTENTIAL_UNLOCK) == null
                            ? null : FormIds.POTENTIAL_UNLOCK;
    }

    /** Escalera de Potential Unlock: un solo escalón, del que se baja con el toque corto. */
    private boolean tickPotentialLadder(Player p) {
        ResourceLocation target = potentialTarget(p);
        if (target == null) return clearProgress(false);
        FormDef d = FormRegistry.get(target);
            int required = (d == null) ? 0 : d.holdTicks();
            if (required <= 0) return clearProgress(false);
        boolean was = transforming;
        if (advanceHold(required)) return progressDirty(was);
        setFormId(target);
        finishHold();
        return true;
    }

    /** Helper para CLIENTE/UI: mismo juez que el servidor, así la animación no miente. */
    public static boolean canTransformFrom(Player p, Race race, ResourceLocation current) {
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return fm.canAdvance(p, race);
    }

    /**
     * Ticks de hold que exige el destino, y color de aura de ESE destino (no de la forma
     * actual): para el anillo de progreso del HUD (TransformGaugeOverlay), que así se pinta
     * del color de la forma a la que se está accediendo — rojo si es un escalón de kaioken
     * (mismo AuraColors.KAIOKEN_RGB que tiñe el aura real), o el aura_rgb del datapack de la
     * forma/potencial destino (blanco en potential_unlock, amarillo en los SSJ...). NUNCA se
     * inventa un color aparte: es el mismo dato que ya gobierna el aura de verdad.
     * holdTicks() == 0 si no hay nada en marcha que mostrar.
     */
    public record HoldTarget(int holdTicks, int auraRgb) {
        public boolean isEmpty() { return holdTicks <= 0; }
    }

    /** MISMA rama que serverTick, en el MISMO orden (oozaru > kaioken > potencial > forma): el
     *  cliente no decide nada nuevo, solo repite el juicio del servidor sobre datos ya
     *  sincronizados (formId, oozaruForced, kaiokenSwitch, holdTicks). El tramo oozaru es el
     *  ÚNICO que no pasa por targetForm()/resolveNextForm() (no hay tecla que lo dispare), así
     *  que necesita su propia rama aquí para que el gui pinte el color/tiempo correctos. */
    public HoldTarget currentHoldTarget(Player p, Race race) {
        if (!transforming) return new HoldTarget(0, -1);
        if (oozaruForced && FormIds.BASE.equals(formId)) {
            FormDef d = FormRegistry.get(FormIds.OOZARU);
            return d == null ? new HoldTarget(0, -1) : new HoldTarget(d.holdTicks(), d.auraRgb());
        }
        if (kaiokenSwitch) return new HoldTarget(KAIOKEN_HOLD_TICKS, AuraColors.KAIOKEN_RGB);

        ResourceLocation target = SkillToggles.isOn(p, SkillEffects.POTENTIAL_UNLOCK)
                ? potentialTarget(p) : targetForm(p, race);
        FormDef d = target == null ? null : FormRegistry.get(target);
        return d == null ? new HoldTarget(0, -1) : new HoldTarget(d.holdTicks(), d.auraRgb());
    }

    // ── Anuncios ─────────────────────────────────────────────────────────────

    /**
     * Grito cinemático del Kaioken: lo ve quien lo usa y el que esté a 32 bloques.
     * Action bar y no chat: es un grito, no un registro.
     */
    private static void announceKaioken(Player p, KaiokenTier tier) {
        if (tier == null || !tier.isOn()) return;
        if (p.level().isClientSide()) return;

        String label = tier.label().toUpperCase(Locale.ROOT);
        p.displayClientMessage(
                Component.translatable("message.zenkai.kaioken.shout", label)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);

        Component other = Component.translatable(
                        "message.zenkai.kaioken.shout_other", p.getDisplayName(), label)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);

        double r2 = SHOUT_RANGE * SHOUT_RANGE;
        for (Player q : p.level().players()) {
            if (q != p && q.distanceToSqr(p) <= r2) q.displayClientMessage(other, false);
        }
    }

    // ── Maestría ─────────────────────────────────────────────────────────────

    public float getFormMastery(ResourceLocation form) {
        if (form == null) return 0f;
        // SSJ Blue/Rose son hermanas equivalentes (mismo parent, mismos números — ver
        // feature.skills.DivineForms): con el config activo se leen como una sola pista de
        // maestría, la MAYOR de las dos ya acumuladas. Esto también resuelve solo el caso de
        // "el config acaba de cambiar de false a true con las dos ya divergidas" — no hace
        // falta migrar nada aparte, el propio getter ya las trata como una.
        if (isBlueRoseSibling(form) && ServerConfig.ssjBlueRoseShareMastery()) {
            return Math.max(rawMastery(FormIds.SSJ_BLUE), rawMastery(FormIds.SSJ_ROSE));
        }
        return rawMastery(form);
    }

    private float rawMastery(ResourceLocation form) {
        return formMastery.getOrDefault(form.toString(), 0f);
    }

    private static boolean isBlueRoseSibling(ResourceLocation form) {
        return FormIds.SSJ_BLUE.equals(form) || FormIds.SSJ_ROSE.equals(form);
    }

    /** El ritmo lo marca la propia forma (mastery_gain del datapack). */
    public void addFormMastery(ResourceLocation form, float delta) {
        if (form == null || delta <= 0) return;
        FormDef def = FormRegistry.get(form);
        float scaled = delta * (def == null ? 1f : (float) def.masteryGain());
        if (scaled <= 0) return;

        if (isBlueRoseSibling(form) && ServerConfig.ssjBlueRoseShareMastery()) {
            // Sumar a las DOS a la vez las mantiene iguales desde ya: si el config se apaga
            // más tarde no hace falta ninguna migración, ya estaban sincronizadas.
            addRawMastery(FormIds.SSJ_BLUE, scaled);
            addRawMastery(FormIds.SSJ_ROSE, scaled);
            return;
        }
        addRawMastery(form, scaled);
    }

    private void addRawMastery(ResourceLocation form, float scaled) {
        String k = form.toString();
        formMastery.merge(k, scaled, Float::sum);
        formMastery.computeIfPresent(k, (key, v) -> Math.min(100f, v));
    }

    // ── Reinicios ────────────────────────────────────────────────────────────

    /** Reinicio total del estado. Muerte, respec y cambio de raza. */
    public void resetAll() {
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        cooldownTicks = 0;
        formId = FormIds.BASE;
        kaioken = KaiokenTier.OFF;
        kaiokenSwitch = false;
        selectedForm = null;
        oozaruForced = false;
    }

    /** Vuelta forzada a base (ki agotado, forma inválida). Deja cooldown para no reencadenar. */
    public void forceBase() {
        resetAll();
        cooldownTicks = 10;
    }

    /**
     * Apaga SOLO la capa de kaioken y deja la forma intacta.
     * No aplica strain: castigar el apagado voluntario no tiene sentido — el strain existe
     * para que no reenciendas dos segundos después de caer agotado, y de eso ya se encarga
     * KaiokenSystem cuando el body llega a 1.
     * @return true si había algo que apagar. El que llama lo usa para decidir si además
     *         hay que destransformar.
     */
    public boolean dropKaioken() {
        if (!kaioken.isOn()) return false;
        setKaioken(KaiokenTier.OFF);
        cooldownTicks = 10;
        return true;
    }

    /**
     * Si la forma guardada ya no existe en el datapack o esa raza no puede usarla, vuelve a
     * base. También limpia una selección que dejó de ser válida, que si no se queda apuntando
     * a una forma fantasma y la tecla no responde sin decir por qué.
     * Llamar al entrar al mundo y tras un /reload, no en el load del NBT: ahí el registro
     * todavía no está poblado.
     */
    public boolean validateOrReset(Race race) {
        boolean changed = false;

        if (selectedForm != null && !FormIds.BASE.equals(selectedForm)
                && (FormRegistry.get(selectedForm) == null
                || !FormRegistry.isAllowed(race, selectedForm))) {
            selectedForm = null;
            changed = true;
        }

        if (!FormIds.BASE.equals(formId)
                && (FormRegistry.get(formId) == null || !FormRegistry.isAllowed(race, formId))) {
            forceBase();
            return true;
        }
        return changed;
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("transformHeld", transformHeld);
        tag.putBoolean("transforming", transforming);
        tag.putInt("holdTicks", holdTicks);
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.putString("formId", formId.toString());
        tag.putInt("kaioken", getKaioken().ordinal());
        tag.putBoolean("kaiokenSwitch", kaiokenSwitch);
        tag.putBoolean("oozaruForced", oozaruForced);
        tag.putLong("strainUntil", strainUntil);
        tag.putDouble("puCeiling", puCeiling);
        if (selectedForm != null) tag.putString("selectedForm", selectedForm.toString());

        CompoundTag fm = new CompoundTag();
        for (Map.Entry<String, Float> e : formMastery.entrySet()) fm.putFloat(e.getKey(), e.getValue());
        tag.put("formMastery", fm);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.transformHeld = tag.getBoolean("transformHeld");
        this.transforming = tag.getBoolean("transforming");
        this.holdTicks = tag.getInt("holdTicks");
        this.cooldownTicks = tag.getInt("cooldownTicks");




        ResourceLocation rl = tag.contains("formId")
                ? ResourceLocation.tryParse(tag.getString("formId")) : null;
        this.formId = (rl == null) ? FormIds.BASE : rl;

        this.kaioken = KaiokenTier.byOrdinal(tag.getInt("kaioken"));
        this.kaiokenSwitch = tag.getBoolean("kaiokenSwitch");
        this.oozaruForced = tag.getBoolean("oozaruForced");
        this.selectedForm = tag.contains("selectedForm")
                ? ResourceLocation.tryParse(tag.getString("selectedForm")) : null;
        this.strainUntil = tag.getLong("strainUntil");
        this.puCeiling = tag.getDouble("puCeiling");
        formMastery.clear();
        if (tag.contains("formMastery")) {
            CompoundTag fm = tag.getCompound("formMastery");
            for (String k : fm.getAllKeys()) {
                formMastery.put(k, Math.min(100f, Math.max(0f, fm.getFloat(k))));
            }
        }
        // MIGRACIÓN: la maestría de kaioken era por escalón (zenkai:kaioken/x2, /x20...).
        // Se colapsa al MÁXIMO y no a la media: quien solo entrenó los escalones altos no
        // debe perder progreso por un cambio de modelo que no pidió.
        String general = kaiokenMasteryKey().toString();
        float best = formMastery.getOrDefault(general, 0f);
        var it = formMastery.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().startsWith(general + "/")) {
                best = Math.max(best, e.getValue());
                it.remove();
            }
        }
        if (best > 0f) formMastery.put(general, Math.min(100f, best));
        // La forma guardada puede haber desaparecido del datapack entre partidas. No se valida
        // aquí (el registro aún no está cargado al leer NBT): lo hace validateOrReset en el tick.
    }

    /**
     * Clave sintética de la maestría de Kaioken. NO es una forma real: vive en el mismo mapa
     * formMastery para heredar persistencia NBT, sync y borrado en respec sin tocar save/load
     * ni añadir packets. validateOrReset no toca el mapa, así que es estable.
     * GENERAL, no por escalón: el kaioken se domina como técnica, no escalón a escalón. Lo que
     * sigue diferenciándolos es el RITMO de ganancia (MasteryTicker.kaiokenGainMul): x20 entrena
     * tres veces más rápido que x2, así que farmear en el escalón barato es posible pero malo.
     */
    public static ResourceLocation kaiokenMasteryKey() {
        return ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "kaioken");
    }

    /** Maestría (0..100) del Kaioken, común a cada uno de los escalones. */
    public float getKaiokenMastery() {
        return formMastery.getOrDefault(kaiokenMasteryKey().toString(), 0f);
    }

    public void addKaiokenMastery(float delta) {
        if (delta <= 0) return;
        String k = kaiokenMasteryKey().toString();
        formMastery.merge(k, delta, Float::sum);
        formMastery.computeIfPresent(k, (key, v) -> Math.min(100f, v));
    }

    public long getStrainUntil() { return strainUntil; }

    /** Fatiga tras agotar el kaioken: bloquea reactivarlo y castiga stats mientras dura. */
    public boolean isStrained(long gameTime) { return gameTime < strainUntil; }

    public void setStrain(long until) { this.strainUntil = Math.max(this.strainUntil, until); }

    /** Segundos que faltan (para la UI). */
    public float strainSecondsLeft(long gameTime) {
        return isStrained(gameTime) ? (strainUntil - gameTime) / 20f : 0f;
    }

    /** Fija la maestría de una clave (forma o escalón de kaioken). Para comandos y debug. */
    public void setMastery(ResourceLocation key, float value) {
        if (key == null) return;
        formMastery.put(key.toString(), Math.max(0f, Math.min(100f, value)));
    }

    /** Vista de solo lectura del conjunto de entradas de maestría (formas + kaioken). */
    public Map<String, Float> masteryView() {
        return java.util.Collections.unmodifiableMap(formMastery);
    }

    /** Limpia la fatiga. SOLO muerte y respec. */
    public void clearStrain() { this.strainUntil = 0L; }

    /** Respec: se pierde la maestría además del estado. */
    public void clearProgression() {
        formMastery.clear();
        strainUntil = 0L;
        resetAll();
    }
}