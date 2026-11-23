package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.StatSynergy;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.stats.TpCurve;
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
    /**
     * Bono de las armaduras imbuidas que lleva puestas (Fase 5a de la 0.7.0). Capa de ATRIBUTOS, antes de StatSynergy y
     * RaceStatTable: la forma multiplica siempre después sin tocar nada. Va APARTE de {@code attributes} a propósito:
     * el bono NO recorta los puntos propios (un jugador al tope con +50 de armadura queda en tope + 50) y no cuenta para
     * los costes de TP, el respec ni los requisitos de MND, que siguen leyendo getAttribute (los puntos comprados).
     * Lo escribe ArmorAttributeBonus en el servidor; viaja al cliente dentro de save() para la pantalla de Stats.
     */
    private final EnumMap<ZenkaiAttributes, Integer> armorBonus = new EnumMap<>(ZenkaiAttributes.class);

    /**
     * TP REALMENTE gastado en atributos. Es lo que devuelve el respec.
     * Antes el respec devolvía `sum(invested)`, o sea el NÚMERO DE PUNTOS. Con la curva
     * cuadrática de closedCost eso significaba que un jugador que había pagado 999 TP por 500
     * puntos recuperaba 500: se le confiscaba la mitad de su progreso sin avisarle en ninguna
     * parte. En escenarios de compra punto a punto la pérdida llegaba al 50 %.
     * Es un double y no un int por el botón de devolución: el reembolso de UN punto es una
     * fracción del gasto, y redondeando a entero en cada pulsación se perdía hasta 1 TP por
     * clic (con 50.000 puntos, 50.000 TP evaporados). El acarreo vive en refundCarry.
     */
    private double tpSpent = 0.0;

    /** Fracción de TP pendiente de entregar por la devolución. Nunca se persiste redondeada. */
    private double refundCarry = 0.0;
    /**
     * Curva vigente ANTES de que el coste pasara a ser 1 + n. Solo la usa la migración de
     * tpSpent, y por eso son literales y no ServerConfig: reconstruyen lo que un save viejo
     * pagó de verdad, y ese número no cambia porque hoy la config diga otra cosa. Evaluar la
     * curva actual sobre puntos comprados con la antigua inflaba el reembolso del respec
     * varios órdenes de magnitud.
     */
    private static final double LEGACY_BASE  = 5.0;
    private static final double LEGACY_COEFF = 0.005;

    public PlayerRaceStats() {
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            attributes.put(a, 0);
            invested.put(a, 0);
            armorBonus.put(a, 0);
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
        int[] base = RaceStatTable.baseAttributes(this.race);
        BalanceUtil.setBase(attributes,
                base[ZenkaiAttributes.STRENGTH.ordinal()],
                base[ZenkaiAttributes.CONSTITUTION.ordinal()],
                base[ZenkaiAttributes.DEXTERITY.ordinal()],
                base[ZenkaiAttributes.WILLPOWER.ordinal()],
                base[ZenkaiAttributes.SPIRIT.ordinal()],
                base[ZenkaiAttributes.MIND.ordinal()]);
        // Los puntos invertidos se suman a la base de la raza (antes cambiar de raza los dejaba contados en `invested`
        // pero fuera del atributo). Con respec() ya están a 0, y la reencarnación los trae como puntos conservados.
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            attributes.merge(a, invested.getOrDefault(a, 0), Integer::sum);
        }
        capAll();
    }

    /**
     * Reencarnación: la nueva vida arranca con estos puntos ya invertidos. NO se anota TP gastado ({@code tpSpent} queda a 0):
     * son un regalo del alma, no se pueden devolver por TP con el respec. Sí cuentan en {@link #totalInvested()}, o sea en la
     * curva de coste de los siguientes.
     */
    public void carryOverInvested(java.util.Map<ZenkaiAttributes, Integer> points) {
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            invested.put(a, Math.max(0, points.getOrDefault(a, 0)));
        }
        applyRaceBaseAttributes();
    }

    private void capAll() {
        int cap = ServerConfig.globalAttributeCap();
        for (Map.Entry<ZenkaiAttributes, Integer> e : attributes.entrySet()) {
            e.setValue(Math.min(e.getValue(), cap));
        }
    }

    public int getAttribute(ZenkaiAttributes a) { return attributes.getOrDefault(a, 0); }

    /** Bono de armadura en este atributo (0 sin armaduras imbuidas). */
    public int getArmorBonus(ZenkaiAttributes a) { return armorBonus.getOrDefault(a, 0); }

    /** Puntos propios + armadura. Es lo que alimenta StatSynergy, los pools y el drenaje de formas/Kaioken. */
    public int getAttributeWithArmor(ZenkaiAttributes a) { return getAttribute(a) + getArmorBonus(a); }

    /**
     * Sustituye el bono de armadura entero. Devuelve true si cambió algo (quien llama recalcula pools y sincroniza solo
     * entonces). El tope del bono lo aplica ArmorAttributeBonus, no aquí: así un gametest puede poner cualquier valor.
     */
    public boolean setArmorBonus(Map<ZenkaiAttributes, Integer> bonus) {
        boolean changed = false;
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            int v = Math.max(0, bonus.getOrDefault(a, 0));
            if (armorBonus.getOrDefault(a, 0) != v) { armorBonus.put(a, v); changed = true; }
        }
        return changed;
    }

    public void setAttribute(ZenkaiAttributes a, int v) {
        attributes.put(a, MathUtil.clamp(v, 0, ServerConfig.globalAttributeCap()));
    }

    // ── TP ───────────────────────────────────────────────────────────────────
    public int  getTP() { return tp; }
    public void addTP(int amount) { this.tp = Math.max(0, this.tp + amount); }

    /** TP gastado en atributos (redondeado hacia abajo). Lo enseña la pantalla de stats. */
    public int getTpSpent() { return (int) Math.floor(tpSpent); }

    /** Puntos invertidos en total. Es el índice de la curva de coste. */
    public int totalInvested() {
        return invested.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int investedIn(ZenkaiAttributes a) { return invested.getOrDefault(a, 0); }

    public boolean spendTP(ZenkaiAttributes attr, int points) {
        if (points <= 0) return false;
        int totalInv = totalInvested();
        int cap      = ServerConfig.globalAttributeCap();
        int cur      = attributes.get(attr);
        int add      = Math.min(points, cap - cur);
        if (add <= 0) return false;

        int totalCost = TpCurve.cost(totalInv, add);
        if (tp < totalCost) return false;

        attributes.put(attr, cur + add);
        invested.compute(attr, (k, v) -> v + add);
        tp -= totalCost;
        tpSpent += totalCost;
        return true;
    }

    public int previewTpCost(ZenkaiAttributes attr, int points) {
        if (points <= 0) return 0;
        int cap = ServerConfig.globalAttributeCap();
        int cur = attributes.get(attr);
        int add = Math.min(points, cap - cur);
        if (add <= 0) return 0;
        return TpCurve.cost(totalInvested(), add);
    }

    /**
     * Devuelve UN punto invertido y reembolsa la parte del gasto que le corresponde.
     * NO se calcula como "el coste marginal del punto N" con closedCost: closedCost redondea
     * hacia arriba el bloque entero, así que comprar 100 puntos de golpe cuesta 101 TP pero
     * devolverlos de uno en uno pagaría ~200. Eso es TP infinito con dos clics, y la
     * simulación lo confirmó en tres de los cuatro patrones de compra probados.
     * El reparto correcto es PROPORCIONAL a la curva teórica: el último punto se lleva la
     * fracción 1 − coste(N−1)/coste(N) de lo que el jugador tiene gastado. Devolviendo cada uno de
     * los puntos uno a uno se reconstruye el gasto exacto (±1 TP por acarreo), venga de compras
     * de una en una o de bloques de diez mil.
     * @return TP devuelto, o -1 si no había nada que devolver en ese atributo.
     */
    public int refundPoint(ZenkaiAttributes attr) {
        int inv = invested.getOrDefault(attr, 0);
        if (inv <= 0) return -1;

        int n = totalInvested();
        if (n <= 0) return -1;

        double after = TpCurve.theoretical(n - 1);
        double now   = TpCurve.theoretical(n);
        double frac  = (now > 0.0) ? 1.0 - (after / now) : 1.0;

        double refund = tpSpent * frac;
        tpSpent = Math.max(0.0, tpSpent - refund);

        refundCarry += refund;
        int whole = (int) refundCarry;
        refundCarry -= whole;

        attributes.compute(attr, (k, v) -> Math.max(0, v - 1));
        invested.put(attr, inv - 1);
        tp += whole;
        return whole;
    }

    /**
     * Devuelve hasta {@code points} puntos de golpe. Devuelve el TP total reembolsado.
     * Es un bucle sobre refundPoint y no una fórmula cerrada porque cada punto sale de una
     * posición distinta de la curva: no hay un "precio del bloque" que calcular de una vez, del
     * mismo modo que comprar cien de golpe no cuesta cien veces el primero. El acarreo decimal
     * vive en refundPoint, así que encadenar llamadas no pierde fracciones por el camino.
     * Se para en cuanto el atributo se queda sin puntos invertidos: pedir cien teniendo cuarenta
     * devuelve cuarenta, no falla. Quien llama decide si eso le vale (al servidor le vale: el
     * cliente puede tener el número desfasado un tick).
     */
    public int refundPoints(ZenkaiAttributes attr, int points) {
        int total = 0;
        for (int i = 0; i < points; i++) {
            int given = refundPoint(attr);
            if (given < 0) break;
            total += given;
        }
        return total;
    }

    /** ¿Se puede devolver un punto de este atributo? Lo consulta la pantalla para el botón −. */
    public boolean canRefund(ZenkaiAttributes attr) {
        return invested.getOrDefault(attr, 0) > 0;
    }



    /** Respec completo: devuelve el TP gastado en atributos y restaura las bases. */
    public void respec() {
        tp += (int) Math.floor(tpSpent + refundCarry);
        tpSpent = 0.0;
        refundCarry = 0.0;
        invested.replaceAll((k, v) -> 0);
        applyRaceBaseAttributes();
    }

    // ── Recalc — devuelve los máximos para que PlayerResourcePools los aplique ──
    public record RecalcResult(int bodyMax, int staminaMax, int energyMax,
                               double speed, double flySpeed) {}

    /**
     * Máximos de los tres pools para una combinación arbitraria. Estático y sin estado a
     * propósito: lo llaman tanto el recalc del jugador como la pantalla de creación, y si
     * cada uno llevara su copia de las fórmulas se separarían al primer rebalanceo.
     */
    public static RecalcResult pools(Race race, Style style, int con, int spi) {
        int bodyMax    = (int) Math.max(1, Math.round(
                10 + con * RaceStatTable.health(race, style)  * ServerConfig.bodyScale()));
        int staminaMax = (int) Math.max(1, Math.round(
                90 + con * RaceStatTable.stamina(race, style) * ServerConfig.staminaScale()));
        int energyMax  = (int) Math.max(1, Math.round(
                90 + spi * RaceStatTable.kiReserves(race, style) * ServerConfig.energyScale()));
        return new RecalcResult(bodyMax, staminaMax, energyMax, 0.0, 0.0);
    }

    public RecalcResult recalcAll() {
        // speed/flySpeed ya no salen de DEX: los gobiernan las habilidades Run y Fly.
        return pools(race, style,
                getAttributeWithArmor(ZenkaiAttributes.CONSTITUTION),
                getAttributeWithArmor(ZenkaiAttributes.SPIRIT));
    }

    // ── Stats de combate ─────────────────────────────────────────────────────
    // Las contribuciones cruzadas viven en StatSynergy, no aquí: el editor de técnicas, la
    // pantalla de creación de personaje y el scouter necesitan las MISMAS fórmulas, y con la
    // aritmética repetida en cada sitio se separaban al primer ajuste.

    public double computeMeleeFinal() {
        return StatSynergy.melee(
                getAttributeWithArmor(ZenkaiAttributes.STRENGTH),
                getAttributeWithArmor(ZenkaiAttributes.WILLPOWER),
                RaceStatTable.melee(race, style));
    }

    public double computeDefenseFinal() {
        return StatSynergy.defense(
                getAttributeWithArmor(ZenkaiAttributes.DEXTERITY),
                getAttributeWithArmor(ZenkaiAttributes.CONSTITUTION),
                RaceStatTable.defense(race, style));
    }

    public double computeKiPowerFinal() {
        return StatSynergy.kiPower(
                getAttributeWithArmor(ZenkaiAttributes.WILLPOWER),
                getAttributeWithArmor(ZenkaiAttributes.SPIRIT),
                RaceStatTable.kiDamage(race, style));
    }

    public double computeKiPoolFinal() {
        return StatSynergy.kiPool(
                getAttributeWithArmor(ZenkaiAttributes.SPIRIT),
                RaceStatTable.kiReserves(race, style));
    }

    public double computeSpiritMeleeFinal() {
        return getAttributeWithArmor(ZenkaiAttributes.SPIRIT) * RaceStatTable.melee(race, style);
    }

    /** Mejor atributo ofensivo en escala de melee. El coeficiente es SIEMPRE el de melee, no
     *  el de ki_damage: si cada atributo trajera su propio coeficiente estaríamos comparando
     *  escalas distintas y el "mejor" dependería de la tabla, no del jugador. */
    public double computeBestMeleeFinal() {
        int best = Math.max(getAttributeWithArmor(ZenkaiAttributes.STRENGTH),
                Math.max(getAttributeWithArmor(ZenkaiAttributes.WILLPOWER),
                        getAttributeWithArmor(ZenkaiAttributes.SPIRIT)));
        return best * RaceStatTable.melee(race, style);
    }

    /** CON efectiva (lineal, sin el offset del pool). La usa el Power Level. */
    public double computeConFinal() {
        return BalanceUtil.computeStat(getAttributeWithArmor(ZenkaiAttributes.CONSTITUTION),
                race, style, ZenkaiAttributes.CONSTITUTION);
    }

    public double getMeleeBonus() {
        return getAttributeWithArmor(ZenkaiAttributes.STRENGTH);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("race",         race.name());
        tag.putString("style",        style.name());
        tag.putBoolean("raceChosen",  raceChosen);
        tag.putBoolean("styleChosen", styleChosen);
        tag.putInt("tp", tp);
        tag.putDouble("tpSpent", tpSpent);
        tag.putDouble("refundCarry", refundCarry);

        CompoundTag attrs = new CompoundTag();
        for (var e : attributes.entrySet()) attrs.putInt(e.getKey().name(), e.getValue());
        tag.put("attributes", attrs);

        CompoundTag inv = new CompoundTag();
        for (var e : invested.entrySet()) inv.putInt(e.getKey().name(), e.getValue());
        tag.put("invested", inv);

        // El bono de armadura se recalcula en el servidor cada tick (ArmorAttributeBonus); se guarda solo para que el
        // sync completo lo lleve al cliente. Un valor viejo en disco se corrige en el primer tick tras cargar.
        CompoundTag arm = new CompoundTag();
        for (var e : armorBonus.entrySet()) if (e.getValue() > 0) arm.putInt(e.getKey().name(), e.getValue());
        tag.put("armorBonus", arm);

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

        CompoundTag arm = tag.getCompound("armorBonus");
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) armorBonus.put(a, Math.max(0, arm.getInt(a.name())));

        this.refundCarry = tag.getDouble("refundCarry");

        if (tag.contains("tpSpent")) {
            this.tpSpent = tag.getDouble("tpSpent");
        } else {
        // MIGRACIÓN de saves anteriores a tpSpent: se reconstruye el gasto suponiendo que
        // se compró de una vez, CON LA CURVA VIEJA. Es una ESTIMACIÓN A LA BAJA — el
        // redondeo hacia arriba de cada compra real siempre suma por encima de la curva
        // continua — así que nadie sale ganando TP al actualizar, que es el único error
        // inaceptable aquí. En los patrones simulados el defecto va del 0 % (compra en
        // bloque) al 50 % (compra punto a punto durante la partida entera).
        int n = totalInvested();
        this.tpSpent = n <= 0 ? 0.0 : n * (LEGACY_BASE + LEGACY_COEFF * (n - 1) / 2.0);
    }
    }
}