package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.util.BalanceUtil;
import com.hmc.zenkai.util.MathUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

public class PlayerRaceStats {

    private Race  race  = Race.HUMAN;
    private Style style = Style.MARTIAL_ARTIST;

    private boolean raceChosen  = false;
    private boolean styleChosen = false;

    private int tp = 0;
    private final EnumMap<ZenkaiAttributes, Integer> attributes = new EnumMap<>(ZenkaiAttributes.class);
    private final EnumMap<ZenkaiAttributes, Integer> invested   = new EnumMap<>(ZenkaiAttributes.class);

    public PlayerRaceStats() {
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            attributes.put(a, 0);
            invested.put(a, 0);
        }
        applyRaceBaseAttributes();
    }

    // ── Raza y Estilo ─────────────────────────────────────────────────────────
    public Race  getRace()  { return race; }
    public Style getStyle() { return style; }

    public boolean isRaceChosen()  { return raceChosen; }
    public boolean isStyleChosen() { return styleChosen; }

    public void setRaceChosen(boolean v)  { this.raceChosen  = v; }
    public void setStyleChosen(boolean v) { this.styleChosen = v; }

    public void setRace(Race r) {
        this.race = r;
        applyRaceBaseAttributes();
    }

    public void setStyle(Style s) {
        this.style = s;
    }

    // ── Atributos base ────────────────────────────────────────────────────────
    public void applyRaceBaseAttributes() {
        // Indexado por ZenkaiAttributes.ordinal(): STR, CON, DEX, WIL, SPI, MND.
        // El array de config venía documentado como [STR, DEX, CON, ...] pero se leía como
        // [STR, CON, DEX, ...], así que CON y DEX salían cambiadas en las cinco razas.
        int[] base = RaceStatTable.baseAttributes(this.race);
        BalanceUtil.setBase(attributes,
                base[ZenkaiAttributes.STRENGTH.ordinal()],
                base[ZenkaiAttributes.CONSTITUTION.ordinal()],
                base[ZenkaiAttributes.DEXTERITY.ordinal()],
                base[ZenkaiAttributes.WILLPOWER.ordinal()],
                base[ZenkaiAttributes.SPIRIT.ordinal()],
                base[ZenkaiAttributes.MIND.ordinal()]);
        capAll();
    }

    private void capAll() {
        int cap = CommonConfig.globalAttributeCap();
        for (Map.Entry<ZenkaiAttributes, Integer> e : attributes.entrySet()) {
            e.setValue(Math.min(e.getValue(), cap));
        }
    }

    public int getAttribute(ZenkaiAttributes a) { return attributes.getOrDefault(a, 0); }

    public void setAttribute(ZenkaiAttributes a, int v) {
        attributes.put(a, MathUtil.clamp(v, 0, CommonConfig.globalAttributeCap()));
    }

    // ── TP ───────────────────────────────────────────────────────────────────
    public int  getTP() { return tp; }
    public void addTP(int amount) { this.tp = Math.max(0, this.tp + amount); }

    public boolean spendTP(ZenkaiAttributes attr, int points) {
        if (points <= 0) return false;
        double coeff    = CommonConfig.tpCoefficient();
        int    totalInv = invested.values().stream().mapToInt(Integer::intValue).sum();
        int    cap      = CommonConfig.globalAttributeCap();
        int    cur      = attributes.get(attr);
        int    add      = Math.min(points, cap - cur);
        if (add <= 0) return false;

        int totalCost = closedCost(totalInv, add, coeff);
        if (tp < totalCost) return false;

        attributes.put(attr, cur + add);
        invested.compute(attr, (k, v) -> v + add);
        tp -= totalCost;
        return true;
    }

    public int previewTpCost(ZenkaiAttributes attr, int points) {
        if (points <= 0) return 0;
        double coeff    = CommonConfig.tpCoefficient();
        int    totalInv = invested.values().stream().mapToInt(Integer::intValue).sum();
        int    cap      = CommonConfig.globalAttributeCap();
        int    cur      = attributes.get(attr);
        int    add      = Math.min(points, cap - cur);
        if (add <= 0) return 0;
        return closedCost(totalInv, add, coeff);
    }

    /** Coste total en O(1): add*(1 + coef*(inv + (add-1)/2)), UN solo redondeo.
     *  (El bucle anterior era O(n) por compra — inviable comprando miles — y con coefs
     *  pequeños inflaba ~+1 por punto por el ceil por término. Cambio de balance Fase 4.) */
    private static int closedCost(int inv, int add, double coeff) {
        double total = add * (1.0 + coeff * (inv + (add - 1) / 2.0));
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(total));
    }

    public void respec() {
        int refund = invested.values().stream().mapToInt(i -> i).sum();
        tp += refund;
        invested.replaceAll((k, v) -> 0);
        applyRaceBaseAttributes();
    }

    // ── Recalc — devuelve los máximos para que PlayerResourcePools los aplique ──
    public record RecalcResult(int bodyMax, int staminaMax, int energyMax,
                               double speed, double flySpeed) {}

    public RecalcResult recalcAll() {
        int con = attributes.get(ZenkaiAttributes.CONSTITUTION);
        int spi = attributes.get(ZenkaiAttributes.SPIRIT);

        // RaceStatTable reparte por raza/estilo; las *Scale de config siguen siendo el mando
        // global de time-to-kill (y las comparten los mobs vía EntityStats).
        int bodyMax    = (int) Math.max(1, Math.round(
                10 + con * RaceStatTable.health(race, style)  * CommonConfig.bodyScale()));
        int staminaMax = (int) Math.max(1, Math.round(
                90 + con * RaceStatTable.stamina(race, style) * CommonConfig.staminaScale()));
        int energyMax  = (int) Math.max(1, Math.round(
                90 + spi * RaceStatTable.kiReserves(race, style) * CommonConfig.energyScale()));

        // speed/flySpeed ya no salen de DEX: los gobiernan las habilidades Run y Fly.
        return new RecalcResult(bodyMax, staminaMax, energyMax, 0.0, 0.0);
    }

    // ── Stats de combate ─────────────────────────────────────────────────────
    public double computeMeleeFinal() {
        return attributes.get(ZenkaiAttributes.STRENGTH) * RaceStatTable.melee(race, style);
    }
    public double computeDefenseFinal() {
        return attributes.get(ZenkaiAttributes.DEXTERITY) * RaceStatTable.defense(race, style);
    }
    public double computeKiPowerFinal() {
        return attributes.get(ZenkaiAttributes.WILLPOWER) * RaceStatTable.kiDamage(race, style);
    }
    public double computeKiPoolFinal() {
        return attributes.get(ZenkaiAttributes.SPIRIT) * RaceStatTable.kiReserves(race, style);
    }

    public double computeSpiritMeleeFinal() {
        return attributes.get(ZenkaiAttributes.SPIRIT) * RaceStatTable.melee(race, style);
    }

    /** Mejor atributo ofensivo en escala de melee. El coeficiente es SIEMPRE el de melee, no
     *  el de ki_damage: si cada atributo trajera su propio coeficiente estaríamos comparando
     *  escalas distintas y el "mejor" dependería de la tabla, no del jugador. */
    public double computeBestMeleeFinal() {
        int best = Math.max(attributes.get(ZenkaiAttributes.STRENGTH),
                Math.max(attributes.get(ZenkaiAttributes.WILLPOWER),
                        attributes.get(ZenkaiAttributes.SPIRIT)));
        return best * RaceStatTable.melee(race, style);
    }


    // computeSpeedFinal / computeFlyFinal: ya no se usan para velocidad. Si algo más los
    // llama, que devuelvan defensa o se eliminen.
    public double computeSpeedFinal() {
        return BalanceUtil.computeStat(attributes.get(ZenkaiAttributes.DEXTERITY),  race, style, ZenkaiAttributes.DEXTERITY);
    }
    public double computeFlyFinal() {
        return BalanceUtil.computeStat(attributes.get(ZenkaiAttributes.DEXTERITY),  race, style, ZenkaiAttributes.DEXTERITY);
    }



    /** CON efectiva (lineal, sin el offset del pool). La usa el Power Level. */
    public double computeConFinal() {
        return BalanceUtil.computeStat(attributes.get(ZenkaiAttributes.CONSTITUTION), race, style, ZenkaiAttributes.CONSTITUTION);
    }

    public double getMeleeBonus() {
        return attributes.get(ZenkaiAttributes.STRENGTH);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("race",         race.name());
        tag.putString("style",        style.name());
        tag.putBoolean("raceChosen",  raceChosen);
        tag.putBoolean("styleChosen", styleChosen);
        tag.putInt("tp", tp);

        CompoundTag attrs = new CompoundTag();
        for (var e : attributes.entrySet()) attrs.putInt(e.getKey().name(), e.getValue());
        tag.put("attributes", attrs);

        CompoundTag inv = new CompoundTag();
        for (var e : invested.entrySet()) inv.putInt(e.getKey().name(), e.getValue());
        tag.put("invested", inv);

        return tag;
    }

    public void load(CompoundTag tag) {
        try {
            this.race  = Race.valueOf(tag.getString("race"));
            this.style = Style.valueOf(tag.getString("style"));
        } catch (Exception ignored) {}

        this.raceChosen  = tag.getBoolean("raceChosen");
        this.styleChosen = tag.getBoolean("styleChosen");
        this.tp          = tag.getInt("tp");

        CompoundTag attrs = tag.getCompound("attributes");
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) attributes.put(a, attrs.getInt(a.name()));

        CompoundTag inv = tag.getCompound("invested");
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) invested.put(a, inv.getInt(a.name()));
    }
}