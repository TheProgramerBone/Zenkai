package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.core.network.feature.KiAttackType;
import com.hmc.db_renewed.core.network.feature.ki.KiAttackDefinition;
import com.hmc.db_renewed.util.MathUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PlayerKiAttacks {

    private final Map<String, KiAttackDefinition> attacks = new HashMap<>();
    private String selectedId = "";

    public PlayerKiAttacks() {
        // Ataque básico de semilla
        addOrUpdate(new KiAttackDefinition(
                "basic_blast", "Basic Blast", KiAttackType.BLAST,
                4.0,     // basePower
                1.0,     // speed (blocks/tick)
                20,      // cooldownTicks
                20,      // chargeTimeTicks
                1,       // density
                0x33CCFF // color
        ));
        selectedId = "basic_blast";
    }

    // ── Acceso ───────────────────────────────────────────────────────────────
    public KiAttackDefinition getAttack(String id) {
        return attacks.get(id);
    }

    public KiAttackDefinition getSelected() {
        return attacks.get(selectedId);
    }

    public String getSelectedId() { return selectedId; }

    public Map<String, KiAttackDefinition> getReadonly() {
        return Collections.unmodifiableMap(attacks);
    }

    // ── Mutación ─────────────────────────────────────────────────────────────
    public void addOrUpdate(KiAttackDefinition def) {
        if (def == null || def.id() == null || def.id().isEmpty()) return;
        attacks.put(def.id(), def);
        if (selectedId == null || selectedId.isEmpty()) {
            selectedId = def.id();
        }
    }

    public void setSelectedId(String id) {
        if (id != null && attacks.containsKey(id)) {
            this.selectedId = id;
        }
    }

    public void setColor(String id, int rgb) {
        var old = attacks.get(id);
        if (old == null) return;
        attacks.put(id, new KiAttackDefinition(
                old.id(), old.displayName(), old.type(),
                old.basePower(), old.speed(), old.cooldownTicks(),
                old.chargeTimeTicks(), old.density(), rgb
        ));
    }

    // ── Cálculo de coste y daño ───────────────────────────────────────────────
    /**
     * Coste de Ki para disparar un ataque.
     * @param chargeRatio 0.0 = sin cargar, 1.0 = 100%, 2.0 = overcharge
     * @param kiPowerFinal valor ya calculado desde PlayerRaceStats
     */
    public int computeCost(KiAttackDefinition def, double chargeRatio, double kiPowerFinal) {
        if (def == null) return 1;
        double r = Math.max(0.0, Math.min(2.0, chargeRatio));

        double base     = 3.0 + def.basePower() * 0.3;
        double byPower  = Math.sqrt(Math.max(0.0, kiPowerFinal)) * 0.5;
        double byCharge = 0.5 + 0.75 * r;

        return MathUtil.clamp((int) Math.round(base + byPower * byCharge), 1, 80);
    }

    /**
     * Daño final de un ataque.
     * @param chargeRatio 0.0–2.0
     * @param kiPowerFinal valor ya calculado desde PlayerRaceStats
     */
    public float computeDamage(KiAttackDefinition def, double chargeRatio, double kiPowerFinal) {
        if (def == null) return 0f;
        double r     = Math.max(0.0, Math.min(2.0, chargeRatio));
        double scale = 1.0 + kiPowerFinal / 200.0;
        double dmg   = def.basePower() * (0.5 + 1.5 * r) * scale;
        return (float) Math.max(0.5, dmg);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();
        for (var def : attacks.values()) {
            CompoundTag d = new CompoundTag();
            d.putString("id",         def.id());
            d.putString("display",    def.displayName());
            d.putString("type",       def.type().name());
            d.putDouble("basePower",  def.basePower());
            d.putDouble("speed",      def.speed());
            d.putInt("cooldown",      def.cooldownTicks());
            d.putInt("chargeTime",    def.chargeTimeTicks());
            d.putInt("density",       def.density());
            d.putInt("rgb",           def.rgbColor());
            list.add(d);
        }
        tag.put("attacks", list);
        tag.putString("selectedId", selectedId);
        return tag;
    }

    public void load(CompoundTag tag) {
        attacks.clear();
        if (tag.contains("attacks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("attacks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag d = list.getCompound(i);
                attacks.put(d.getString("id"), new KiAttackDefinition(
                        d.getString("id"),
                        d.getString("display"),
                        safeType(d.getString("type")),
                        d.getDouble("basePower"),
                        d.getDouble("speed"),
                        d.getInt("cooldown"),
                        d.getInt("chargeTime"),
                        d.getInt("density"),
                        d.getInt("rgb")
                ));
            }
        }
        this.selectedId = tag.getString("selectedId");
    }

    private static KiAttackType safeType(String name) {
        try { return KiAttackType.valueOf(name); }
        catch (Exception e) { return KiAttackType.BLAST; }
    }
}