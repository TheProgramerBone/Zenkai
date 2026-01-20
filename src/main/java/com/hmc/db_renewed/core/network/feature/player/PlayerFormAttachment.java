package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.forms.FormDefinition;
import com.hmc.db_renewed.core.network.feature.forms.FormIds;
import com.hmc.db_renewed.core.network.feature.forms.FormRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class PlayerFormAttachment {

    private boolean transformHeld = false;
    private boolean transforming = false;
    private int holdTicks = 0;
    private ResourceLocation formId = FormIds.BASE;
    private int cooldownTicks = 0;

    // (Opcional) habilita sync de progreso cada N ticks (si quieres barra/anim más “en vivo”)
    private static final int PROGRESS_SYNC_EVERY = 0; // 0 = off, ej: 5 = cada 5 ticks

    // ---------------- Getters ----------------
    public boolean isTransformHeld() { return transformHeld; }
    public boolean isTransforming() { return transforming; }
    public int getHoldTicks() { return holdTicks; }
    public ResourceLocation getFormId() { return formId; }
    public int getCooldownTicks() { return cooldownTicks; }

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

    public void resetAll() {
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        formId = FormIds.BASE;
        cooldownTicks = 0;
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

        // Asegura que el registry exista (por si no se llamó en commonSetup)
        FormRegistry.bootstrap();

        // cooldown
        if (cooldownTicks > 0) cooldownTicks--;

        // Gate: si no está holdeando o en cooldown, limpiar proceso
        if (!transformHeld || cooldownTicks > 0) {
            if (transforming || holdTicks != 0) {
                transforming = false;
                holdTicks = 0;
                dirty = true;
            }
            return dirty;
        }

        // Resolver target según raza + forma actual
        Race race = stats.getRace();
        ResourceLocation target = resolveNextForm(race, formId);

        // Si no hay transformación configurada -> NO hacemos nada (y NO mantenemos anim)
        if (target == null) {
            if (transforming || holdTicks != 0) {
                transforming = false;
                holdTicks = 0;
                dirty = true;
            }
            return dirty;
        }

        // Hold requerido: el de la FORMA DESTINO
        FormDefinition targetDef = FormRegistry.get(target);
        int required = (targetDef == null) ? 0 : targetDef.holdTicksRequired();

        if (required <= 0) {
            // Forma destino mal configurada -> no transformar
            if (transforming || holdTicks != 0) {
                transforming = false;
                holdTicks = 0;
                dirty = true;
            }
            return dirty;
        }

        // Progreso de hold
        transforming = true;
        holdTicks++;

        if (PROGRESS_SYNC_EVERY > 0 && (holdTicks % PROGRESS_SYNC_EVERY == 0)) {
            dirty = true;
        }

        // Completa
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

    /**
     * Devuelve la siguiente forma para esta raza/forma actual, o null si no aplica.
     * REGLAS:
     * - Si current == BASE => usa firstFormFor(race)
     * - Si current != BASE => usa def(current).nextFormId()
     * - Siempre valida allowedRaces del destino
     */
    private static ResourceLocation resolveNextForm(Race race, ResourceLocation current) {
        if (race == null) return null;
        if (current == null) current = FormIds.BASE;

        // BASE -> first form por raza
        if (FormIds.BASE.equals(current)) {
            ResourceLocation first = FormRegistry.firstFormFor(race);
            if (first == null) return null;
            return FormRegistry.isAllowed(race, first) ? first : null;
        }

        // Non-base -> next form por cadena
        FormDefinition curDef = FormRegistry.get(current);
        ResourceLocation next = (curDef == null) ? null : curDef.nextFormId();
        if (next == null) return null;

        return FormRegistry.isAllowed(race, next) ? next : null;
    }

    /**
     * Helper útil (CLIENTE/UI): saber si existe transform posible desde el estado actual.
     */
    public static boolean canTransformFrom(Race race, ResourceLocation current) {
        FormRegistry.bootstrap();
        return resolveNextForm(race, current) != null;
    }

    // ---------------- NBT ----------------
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("transformHeld", transformHeld);
        tag.putBoolean("transforming", transforming);
        tag.putInt("holdTicks", holdTicks);
        tag.putString("formId", formId.toString());
        tag.putInt("cooldownTicks", cooldownTicks);
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

        // Safety: si quedó en una forma no permitida (cambio de configs), vuelve a base
        // (Opcional, pero recomendado)
        // Race no está en NBT aquí; se valida en runtime al transformarse.
    }

    public void forceBase() {
        setFormId(FormIds.BASE);
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        cooldownTicks = 10;
    }
}
