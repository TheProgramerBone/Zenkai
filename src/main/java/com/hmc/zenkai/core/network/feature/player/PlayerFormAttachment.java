package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.forms.FormDef;
import com.hmc.zenkai.core.network.feature.forms.FormIds;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.forms.KaiokenTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Estado de transformación del jugador. DOS CAPAS INDEPENDIENTES:
 *   - formId: la transformación (SSJ, formas arcosianas...). Datos en datapack (FormDef).
 *   - kaioken: escalón del Kaioken, que se APILA sobre la forma y no se apaga al cambiarla.
 * Los datos de cada forma vienen del datapack, así que aquí no hay números: solo la máquina
 * de estados del hold y la maestría acumulada.
 */
public class PlayerFormAttachment {

    private boolean transformHeld = false;
    private boolean transforming = false;
    private int holdTicks = 0;
    private ResourceLocation formId = FormIds.BASE;
    private int cooldownTicks = 0;
    private KaiokenTier kaioken = KaiokenTier.OFF;

    // (Opcional) habilita sync de progreso cada N ticks (si quieres barra/anim más "en vivo")
    private static final int PROGRESS_SYNC_EVERY = 0; // 0 = off, ej: 5 = cada 5 ticks

    // ---------------- Getters ----------------
    public boolean isTransformHeld() { return transformHeld; }
    public boolean isTransforming() { return transforming; }
    public int getHoldTicks() { return holdTicks; }
    public ResourceLocation getFormId() { return formId; }
    public int getCooldownTicks() { return cooldownTicks; }
    public KaiokenTier getKaioken() { return kaioken; }

    /** true si está en BASE y sin kaioken: nada que mostrar ni que drenar. */
    public boolean isBase() { return FormIds.BASE.equals(formId) && !kaioken.isOn(); }

    // ---------------- Setters ----------------
    public void setTransformHeld(boolean held) {
        this.transformHeld = held;
        if (!held) {
            // si suelta, se corta el proceso
            if (this.transforming || this.holdTicks != 0) {
                this.transforming = false;
                this.holdTicks = 0;
            }
        }
    }

    public void setFormId(ResourceLocation id) {
        this.formId = (id == null) ? FormIds.BASE : id;
    }

    /** Kaioken y transformación son capas INDEPENDIENTES: cambiar de forma no lo apaga. */
    public void setKaioken(KaiokenTier tier) { this.kaioken = (tier == null) ? KaiokenTier.OFF : tier; }

    /** Reinicio total: vuelve a base y apaga el kaioken. Muerte, respec y cambio de raza. */
    public void resetAll() {
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        formId = FormIds.BASE;
        cooldownTicks = 0;
        kaioken = KaiokenTier.OFF;
    }

    // ── Datos de la forma activa (del datapack) ─────────────────────────────

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
        return formStatPercent() + kaioken.statPercent();
    }

    /** Ki drenado por tick por la forma activa, ya interpolado por maestría. */
    public double formKiDrainPerTick() {
        FormDef d = activeDef();
        return d == null ? 0.0 : d.kiDrainPerTick(activeMastery());
    }

    /**
     * Tick SOLO SERVIDOR.
     * Reglas:
     * - Si NO hay transformación configurada para esa raza (o no hay next desde el estado actual),
     *   NO se marca transforming, NO progresa hold, NO hay anim/FOV si el cliente depende de eso.
     *
     * @return dirty si cambió algo importante y conviene sync inmediato.
     */
    public boolean serverTick(Player p, PlayerStatsAttachment stats, PlayerVisualAttachment visual) {
        boolean dirty = false;

        // cooldown
        if (cooldownTicks > 0) cooldownTicks--;

        // Gate: si no está holdeando o en cooldown, limpiar proceso
        if (!transformHeld || cooldownTicks > 0) {
            return clearProgress(dirty);
        }

        // Resolver target según raza + forma actual
        Race race = stats.getRace();
        ResourceLocation target = resolveNextForm(race, formId);

        // Si no hay transformación configurada -> NO hacemos nada (y NO mantenemos anim)
        if (target == null) return clearProgress(dirty);

        // Hold requerido: el de la FORMA DESTINO (del datapack)
        FormDef targetDef = FormRegistry.get(target);
        int required = (targetDef == null) ? 0 : targetDef.holdTicks();

        // Forma destino mal configurada o sin JSON -> no transformar
        if (required <= 0) return clearProgress(dirty);

        // =========================
        // Progreso de hold
        // =========================
        boolean wasTransforming = transforming;

        transforming = true;
        holdTicks++;

        if (!wasTransforming) {
            dirty = true;
        }

        // (Opcional) sync de progreso cada N ticks (si quieres UI/barra/anim "en vivo")
        if (PROGRESS_SYNC_EVERY > 0 && (holdTicks % PROGRESS_SYNC_EVERY == 0)) {
            dirty = true;
        }

        // =========================
        // Completa
        // =========================
        if (holdTicks >= required) {
            setFormId(target);
            dirty = true;

            // Reset proceso: cortar anim aunque siga holdeando teclas
            transforming = false;
            holdTicks = 0;

            // Obligar a soltar para evitar encadenar
            transformHeld = false;

            // Cooldown pequeño anti spam
            cooldownTicks = 10;
        }

        return dirty;
    }

    /** Corta el proceso de hold. Devuelve el dirty acumulado. */
    private boolean clearProgress(boolean dirty) {
        if (transforming || holdTicks != 0) {
            transforming = false;
            holdTicks = 0;
            return true;
        }
        return dirty;
    }

    /**
     * Devuelve la siguiente forma para esta raza/forma actual, o null si no aplica.
     * La cadena la reconstruye FormRegistry a partir de los 'parent' del datapack.
     */
    private static ResourceLocation resolveNextForm(Race race, ResourceLocation current) {
        if (race == null) return null;
        ResourceLocation next = FormRegistry.nextFrom(
                current == null ? FormIds.BASE : current, race);
        return (next != null && FormRegistry.isAllowed(race, next)) ? next : null;
    }

    /**
     * Helper útil (CLIENTE/UI): saber si existe transform posible desde el estado actual.
     */
    public static boolean canTransformFrom(Race race, ResourceLocation current) {
        return resolveNextForm(race, current) != null;
    }

    // ── Maestría por forma (clave = formId, 0..100%) ─────────────────────────
    private final java.util.Map<String, Float> formMastery = new java.util.HashMap<>();

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

    /** Respec: se pierden las formas aprendidas y la maestría, y se vuelve a base. */
    public void clearProgression() {
        formMastery.clear();
        resetAll();
    }

    // ---------------- NBT ----------------
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("transformHeld", transformHeld);
        tag.putBoolean("transforming", transforming);
        tag.putInt("holdTicks", holdTicks);
        tag.putString("formId", formId.toString());
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.putInt("kaioken", kaioken.ordinal());
        CompoundTag fm = new CompoundTag();
        for (var e : formMastery.entrySet()) fm.putFloat(e.getKey(), e.getValue());
        tag.put("formMastery", fm);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.transformHeld = tag.getBoolean("transformHeld");
        this.transforming = tag.getBoolean("transforming");
        this.holdTicks = tag.getInt("holdTicks");

        if (tag.contains("formId")) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.getString("formId"));
            this.formId = (rl == null) ? FormIds.BASE : rl;
        } else {
            this.formId = FormIds.BASE;
        }

        this.cooldownTicks = tag.getInt("cooldownTicks");
        this.kaioken = KaiokenTier.byOrdinal(tag.getInt("kaioken"));

        formMastery.clear();
        if (tag.contains("formMastery")) {
            CompoundTag fm = tag.getCompound("formMastery");
            for (String k : fm.getAllKeys()) formMastery.put(k, Math.min(100f, Math.max(0f, fm.getFloat(k))));
        }
        // La forma guardada puede haber desaparecido del datapack entre partidas. No se valida
        // aquí (el registro aún no está cargado al leer NBT): lo hace validateOrReset en el tick.
    }

    /**
     * Si la forma guardada ya no existe en el datapack o esa raza no puede usarla, vuelve a
     * base. Llamar al entrar al mundo y tras un /reload, no en el load del NBT: ahí el
     * registro todavía no está poblado.
     */
    public boolean validateOrReset(Race race) {
        if (FormIds.BASE.equals(formId)) return false;
        if (FormRegistry.get(formId) != null && FormRegistry.isAllowed(race, formId)) return false;
        forceBase();
        return true;
    }

    public void forceBase() {
        setFormId(FormIds.BASE);
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        cooldownTicks = 10;
        kaioken = KaiokenTier.OFF;
    }
}