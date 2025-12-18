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

    // ====== Colores básicos (RGB) ======
    private int hairColorRgb = 0xF4D03F;  // rubio tipo SSJ por defecto
    private int eyeColorRgb  = 0x2E86C1;  // azul
    private int auraColorRgb = 0x33CCFF;  // mismo default que usas en stats

    // ====== IDs de estilos/modelos ======
    // Puedes mapear estos IDs a assets/animaciones concretas
    private String hairStyleId = "base";      // p.ej. "base", "saiyan_spiky_1", "saiyan_long"
    private String auraStyleId = "none";      // p.ej. "none", "basic", "flame", "god"
    private String outfitId    = "gi_default"; // futura ropa

    // ====== Forma / transformación ======
    // 0 = base, 1 = primera forma, 2 = SSJ, etc.
    private int formStage = 0;

    public PlayerVisualAttachment() {
    }

    // ---------- Acceso estático (igual que PlayerStatsAttachment) ----------
    public static PlayerVisualAttachment get(@NotNull Player player) {
        return player.getData(DataAttachments.PLAYER_VISUAL.get());
    }

    // ---------- Getters / setters simples ----------

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
        if (hairStyleId != null && !hairStyleId.isEmpty()) {
            this.hairStyleId = hairStyleId;
        }
    }

    public String getAuraStyleId() {
        return auraStyleId;
    }

    public void setAuraStyleId(String auraStyleId) {
        if (auraStyleId != null && !auraStyleId.isEmpty()) {
            this.auraStyleId = auraStyleId;
        }
    }

    public String getOutfitId() {
        return outfitId;
    }

    public void setOutfitId(String outfitId) {
        if (outfitId != null && !outfitId.isEmpty()) {
            this.outfitId = outfitId;
        }
    }

    public int getFormStage() {
        return formStage;
    }

    public void setFormStage(int formStage) {
        this.formStage = Math.max(0, formStage);
    }

    // ---------- Serialización NBT (para AttachmentType.serializable) ----------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("hairColor", hairColorRgb);
        tag.putInt("eyeColor", eyeColorRgb);
        tag.putInt("auraColor", auraColorRgb);

        tag.putString("hairStyleId", hairStyleId);
        tag.putString("auraStyleId", auraStyleId);
        tag.putString("outfitId", outfitId);

        tag.putInt("formStage", formStage);

        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("hairColor")) {
            hairColorRgb = tag.getInt("hairColor");
        }
        if (tag.contains("eyeColor")) {
            eyeColorRgb = tag.getInt("eyeColor");
        }
        if (tag.contains("auraColor")) {
            auraColorRgb = tag.getInt("auraColor");
        }

        if (tag.contains("hairStyleId")) {
            hairStyleId = tag.getString("hairStyleId");
        }
        if (tag.contains("auraStyleId")) {
            auraStyleId = tag.getString("auraStyleId");
        }
        if (tag.contains("outfitId")) {
            outfitId = tag.getString("outfitId");
        }

        formStage = tag.getInt("formStage");
        if (formStage < 0) formStage = 0;
    }
}