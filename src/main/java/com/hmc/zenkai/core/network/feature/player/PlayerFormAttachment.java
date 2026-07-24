package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.forms.FormDef;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.forms.KaiokenTier;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.skills.SkillEffects;
import com.hmc.zenkai.core.skills.SuperForms;
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
 */
public class PlayerFormAttachment {

    /** 0 = off. Sync de progreso cada N ticks, para que la animación se vea "en vivo". */
    private static final int PROGRESS_SYNC_EVERY = 1;

    /** El kaioken no está en el datapack (son cinco números), así que su hold vive aquí. */
    private static final int KAIOKEN_HOLD_TICKS = 30; // 1.5 s por escalón

    /** Radio del grito del kaioken, en bloques. */
    private static final double SHOUT_RANGE = 32.0;

    // ── Estado ───────────────────────────────────────────────────────────────

    private boolean transformHeld = false;
    private boolean transforming = false;
    private int holdTicks = 0;
    private int cooldownTicks = 0;

    private ResourceLocation formId = FormIds.BASE;
    private KaiokenTier kaioken = KaiokenTier.OFF;

    /** Forma OBJETIVO elegida en la rueda. null = sin límite: la tecla sube un escalón por
     *  vez como antes. Ponlo a FormIds.BASE si quieres que pasar por la rueda sea obligatorio. */
    private ResourceLocation selectedForm = null;

    /** Interruptor del Kaioken: decide QUÉ escalera sube la tecla de transformar. */
    private boolean kaiokenSwitch = false;

    /** Maestría por forma (clave = formId, 0..100). */
    private final Map<String, Float> formMastery = new HashMap<>();

    // ── Getters ──────────────────────────────────────────────────────────────

    public boolean isTransformHeld()      { return transformHeld; }
    public boolean isTransforming()       { return transforming; }
    public int getHoldTicks()             { return holdTicks; }
    public int getCooldownTicks()         { return cooldownTicks; }
    public ResourceLocation getFormId()   { return formId; }
    public ResourceLocation getSelectedForm() { return selectedForm; }
    public boolean isKaiokenSwitch()      { return kaiokenSwitch; }

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

    // ── Datos de la forma activa (del datapack) ──────────────────────────────

    /** Def de la forma activa, o null en BASE / si el datapack ya no la define. */
    public FormDef activeDef() { return FormRegistry.get(formId); }

    /** Maestría de la forma activa (0..100). */
    public float activeMastery() { return getFormMastery(formId); }

    /** Fracción que la FORMA suma al multiplicador, ya interpolada por maestría. */
    public double formStatPercent() {
        FormDef d = activeDef();
        return d == null ? 0.0 : d.statPercent(activeMastery());
    }

    /** Fracción que suman FORMA + KAIOKEN. Modelo aditivo: total = 1 + esto. */
    public double totalStatPercent() {
        return formStatPercent() + getKaioken().statPercent();
    }

    /** Ki drenado por tick por la forma activa, ya interpolado por maestría. */
    public double formKiDrainPerTick() {
        FormDef d = activeDef();
        return d == null ? 0.0 : d.kiDrainPerTick(activeMastery());
    }

    // ── Máquina de estados del hold ──────────────────────────────────────────

    /**
     * ¿La tecla de transformar tiene algo que hacer AHORA MISMO? ÚNICO sitio que lo decide,
     * para que la animación del cliente y el efecto del servidor no discrepen: al tope del
     * kaioken, o con la forma destino sin comprar, no se arranca nada.
     */
    public boolean canAdvance(Player p, Race race) {
        if (race == null) return false;

        if (kaiokenSwitch) return nextKaiokenTier(p) != null;

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
        if (!transformHeld || cooldownTicks > 0) return clearProgress(false);

        Race race = stats.getRace();
        if (!canAdvance(p, race)) return clearProgress(false);

        return kaiokenSwitch ? tickKaiokenLadder(p) : tickFormLadder(p, race);
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

        setFormId(target);
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
        ResourceLocation next = FormRegistry.nextFrom(
                current == null ? FormIds.BASE : current, race);
        if (next == null || !FormRegistry.isAllowed(race, next)) return null;
        return SuperForms.unlocked(p, next) ? next : null;
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

    /** Helper para CLIENTE/UI: mismo juez que el servidor, así la animación no miente. */
    public static boolean canTransformFrom(Player p, Race race, ResourceLocation current) {
        PlayerFormAttachment fm = p.getData(DataAttachments.PLAYER_FORM.get());
        return fm.canAdvance(p, race);
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
            if (q != p && q.distanceToSqr(p) <= r2) q.displayClientMessage(other, true);
        }
    }

    // ── Maestría ─────────────────────────────────────────────────────────────

    public float getFormMastery(ResourceLocation form) {
        return form == null ? 0f : formMastery.getOrDefault(form.toString(), 0f);
    }

    /** El ritmo lo marca la propia forma (mastery_gain del datapack). */
    public void addFormMastery(ResourceLocation form, float delta) {
        if (form == null || delta <= 0) return;
        FormDef def = FormRegistry.get(form);
        float scaled = delta * (def == null ? 1f : (float) def.masteryGain());
        if (scaled <= 0) return;

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
    }

    /** Respec: se pierde la maestría además del estado. */
    public void clearProgression() {
        formMastery.clear();
        resetAll();
    }

    /** Vuelta forzada a base (ki agotado, forma inválida). Deja cooldown para no reencadenar. */
    public void forceBase() {
        resetAll();
        cooldownTicks = 10;
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
        this.selectedForm = tag.contains("selectedForm")
                ? ResourceLocation.tryParse(tag.getString("selectedForm")) : null;

        formMastery.clear();
        if (tag.contains("formMastery")) {
            CompoundTag fm = tag.getCompound("formMastery");
            for (String k : fm.getAllKeys()) {
                formMastery.put(k, Math.min(100f, Math.max(0f, fm.getFloat(k))));
            }
        }
        // La forma guardada puede haber desaparecido del datapack entre partidas. No se valida
        // aquí (el registro aún no está cargado al leer NBT): lo hace validateOrReset en el tick.
    }
}