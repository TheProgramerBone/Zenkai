package com.hmc.db_renewed.network.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Attachment SOLO para datos visuales/cosméticos del jugador.
 * No debe afectar directamente el cálculo de daño, TP, etc.
 *
 * Ejemplos de uso:
 *  - GeckoLib: elegir modelo/anim según raza + formId + hairStyleId.
 *  - Auras: color y estilo del aura.
 *  - Menú de creación de personaje: guardar selección de pelo/ojos/ropa.
 */
public class PlayerVisualAttachment {

    // ========================
    //  COLORES BÁSICOS
    // ========================

    /** Color de pelo en RGB (0xRRGGBB). */
    private int hairColorRgb = 0xCCAA66;

    /** Color de ojos en RGB (0xRRGGBB). */
    private int eyeColorRgb = 0x00FF00;

    /**
     * Color de aura en RGB (0xRRGGBB).
     * Ojo: tienes un auraColorRgb en PlayerStatsAttachment, la idea
     * es ir migrándolo poco a poco a este attachment.
     */
    private int auraColorRgb = 0x33CCFF;

    // ========================
    //  ESTILOS / IDS LÓGICOS
    // ========================

    /**
     * ID lógico del estilo de peinado.
     * Ejemplos: "saiyan_base_1", "saiyan_spiky_2", "namekian_antennae".
     */
    private String hairStyleId = "base";

    /**
     * ID lógico del estilo de aura.
     * Ejemplos: "ki_base", "ssj_flare", "god_flame".
     */
    private String auraStyleId = "base";

    /**
     * ID lógico del outfit/ropa principal.
     * Ejemplos: "gi_orange", "armor_saiyan", "cloak_namekian".
     */
    private String outfitId = "gi_orange";

    /**
     * Forma/transformación actual.
     * Ejemplos: "base", "ssj1", "ssj2", "golden", "majin".
     */
    private String formId = "base";

    /**
     * Si la cola del saiyan (u otra parte) debe renderizarse visible.
     * Esto lo puede leer GeckoLib para mostrar/ocultar el modelo correspondiente.
     */
    private boolean tailVisible = false;

    // ========================
    //  CONSTRUCTOR / ACCESO
    // ========================

    public PlayerVisualAttachment() {}

    /**
     * Acceso estático como en PlayerStatsAttachment.
     * Requiere que registres DataAttachments.PLAYER_VISUAL.
     */
    public static PlayerVisualAttachment get(Player player) {
        return player.getData(DataAttachments.PLAYER_VISUAL.get());
    }

    // ========================
    //  SERIALIZACIÓN NBT
    // ========================

    public @NotNull CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("hairColorRgb", this.hairColorRgb);
        tag.putInt("eyeColorRgb",  this.eyeColorRgb);
        tag.putInt("auraColorRgb", this.auraColorRgb);

        tag.putString("hairStyleId", this.hairStyleId);
        tag.putString("auraStyleId", this.auraStyleId);
        tag.putString("outfitId",    this.outfitId);
        tag.putString("formId",      this.formId);

        tag.putBoolean("tailVisible", this.tailVisible);

        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("hairColorRgb", Tag.TAG_INT)) {
            this.hairColorRgb = tag.getInt("hairColorRgb");
        }
        if (tag.contains("eyeColorRgb", Tag.TAG_INT)) {
            this.eyeColorRgb = tag.getInt("eyeColorRgb");
        }
        if (tag.contains("auraColorRgb", Tag.TAG_INT)) {
            this.auraColorRgb = tag.getInt("auraColorRgb");
        }

        if (tag.contains("hairStyleId", Tag.TAG_STRING)) {
            this.hairStyleId = tag.getString("hairStyleId");
        }
        if (tag.contains("auraStyleId", Tag.TAG_STRING)) {
            this.auraStyleId = tag.getString("auraStyleId");
        }
        if (tag.contains("outfitId", Tag.TAG_STRING)) {
            this.outfitId = tag.getString("outfitId");
        }
        if (tag.contains("formId", Tag.TAG_STRING)) {
            this.formId = tag.getString("formId");
        }

        if (tag.contains("tailVisible", Tag.TAG_BYTE)) {
            this.tailVisible = tag.getBoolean("tailVisible");
        }
    }

    // ========================
    //  GETTERS / SETTERS
    // ========================

    public int getHairColorRgb() {
        return hairColorRgb;
    }

    public void setHairColorRgb(int hairColorRgb) {
        this.hairColorRgb = hairColorRgb;
    }

    public int getEyeColorRgb() {
        return eyeColorRgb;
    }

    public void setEyeColorRgb(int eyeColorRgb) {
        this.eyeColorRgb = eyeColorRgb;
    }

    public int getAuraColorRgb() {
        return auraColorRgb;
    }

    public void setAuraColorRgb(int auraColorRgb) {
        this.auraColorRgb = auraColorRgb;
    }

    public String getHairStyleId() {
        return hairStyleId;
    }

    public void setHairStyleId(String hairStyleId) {
        this.hairStyleId = hairStyleId;
    }

    public String getAuraStyleId() {
        return auraStyleId;
    }

    public void setAuraStyleId(String auraStyleId) {
        this.auraStyleId = auraStyleId;
    }

    public String getOutfitId() {
        return outfitId;
    }

    public void setOutfitId(String outfitId) {
        this.outfitId = outfitId;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public boolean isTailVisible() {
        return tailVisible;
    }

    public void setTailVisible(boolean tailVisible) {
        this.tailVisible = tailVisible;
    }
}
