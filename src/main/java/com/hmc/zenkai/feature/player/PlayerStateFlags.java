package com.hmc.zenkai.feature.player;

import net.minecraft.nbt.CompoundTag;

public class PlayerStateFlags {

    private boolean isImmortal  = false;
    private boolean isDivine    = false;
    private boolean isLegendary = false;
    private boolean flyEnabled  = false;
    private boolean chargingKi  = false;
    /** Murió en un mundo hardcore. Yemma no revive a estos: su trato es con las almas que
     *  aún tienen cuerpo al que volver. Solo las esferas del dragón lo deshacen. */
    private boolean hardcoreDeath = false;

    /**
     * El jugador está en BOOST de vuelo -> pose/hitbox horizontal ("acostado").
     * Transitorio (runtime, server-only): NO se guarda en NBT (evita quedar horizontal al
     * reconectar) ni hace falta en el sync (la pose se propaga sola por DATA_POSE).
     */
    private boolean flyBoosting = false;

    /**
     * Tracker transitorio (por lado, NO se guarda ni sincroniza): si la hitbox de boost ya está
     * aplicada. Sirve para llamar refreshDimensions() SOLO en la transición (evita recalcular cada tick).
     */
    private boolean boostSizeApplied = false;

    /**
     * El jugador sostiene Shift + la tecla de cargar ki ("forzar" el 100%). A diferencia de
     * flyBoosting, esto SÍ tiene que ir en save()/load(): SyncPlayerStatsPacket usa exactamente
     * ese round-trip de NBT como transporte de red cada tick (PlayerLifeCycle.syncIfServer, vía
     * ZenkaiTickHandlers) — flyBoosting se libra de necesitar sync porque su efecto visual viaja
     * por la Pose vanilla ya sincronizada aparte, esto no tiene ese atajo y el cliente lo
     * necesita para el temblor del gauge/cámara. Mismo tratamiento que chargingKi (también
     * persistido pese a ser en esencia un estado de input) por el mismo motivo. Lo escribe
     * OverdriveChargePacket, edge-triggered desde el cliente (mismo patrón que FlyBoostPacket).
     */
    private boolean overdriveCharging = false;

    /** Ya rompió el 100% de powerPercent alguna vez (persistente). Determina cuánto tarda el
     *  temblor de romper el límite las siguientes veces (ver OverdriveTuning/KiChargeSystem) —
     *  el mod no vuelve a preguntarle al sistema de logros de Minecraft si ya lo completó en
     *  ningún otro sitio, así que aquí tampoco: es nuestro propio flag. */
    private boolean hasBrokenOverdriveOnce = false;

    /**
     * Completó el ritual de Oozaru al menos una vez (Oozaru -> Super Oozaru -> SSJ4, ver
     * OozaruSystem/PlayerFormAttachment). Comprar super_forms hasta el nivel máximo con TP
     * solo da DERECHO a intentar el ritual (SuperForms.readyForSuperOozaru); la forma en sí
     * no queda utilizable/visible en la rueda hasta completarlo una vez — ver
     * SuperForms.unlocked(SSJ4). Persistente: no hay que repetir el ritual cada partida ni
     * cada respec, solo re-comprar el nivel si se pierde por respec.
     */
    private boolean hasSsj4Ritual = false;

    /**
     * Cola de saiyan: una de las tres condiciones de Oozaru (junto con raza y luna llena, ver
     * OozaruConditions). PENDIENTE: hoy no existe ninguna pieza de GeckoLibArmor de cola
     * todavía, así que el valor por defecto es true (todo saiyan la tiene) para no bloquear el
     * sistema mientras se modela; cuando exista el ítem, algo (equipar/perder la cola) deberá
     * poder ponerlo en false. No se restringe por raza aquí: PlayerStateFlags no conoce razas,
     * ese filtro vive en OozaruConditions.
     */
    private boolean hasTail = true;

    /** Ya recibió el servicio de pesas de Kaiosama (ver KaioEntity.services()). Regalo de una
     *  sola vez: sin este flag, pedirlas repetidas veces sería una granja infinita de
     *  WEIGHTED_STRAPS/WEIGHTED_CAPE gratis. */
    private boolean receivedKaioWeights = false;

    /** El jugador está muerto / en el otro mundo. */
    private boolean inOtherworld = false;
    /** gameTime en que fue enviado al otro mundo (para el contador de Yemma). */
    private long otherworldSince = 0L;

    /** gameTime en que expira el estado "en combate". Ver InCombatState: es un instante
     *  futuro y no un contador para no tener que decrementarlo cada tick. */
    private long inCombatUntil = 0L;

    /** El jugador está "derribado" (acostado, transición previa al otro mundo). */
    private boolean downed = false;
    /** gameTime en que el derribado termina y muere de verdad si no lo curan. */
    private long downedUntil = 0L;

    public boolean isImmortal()  { return isImmortal; }
    public boolean isDivine()    { return isDivine; }
    public boolean isLegendary() { return isLegendary; }
    public boolean isFlyEnabled()  { return flyEnabled; }
    public boolean isChargingKi()  { return chargingKi; }
    public boolean isFlyBoosting() { return flyBoosting; }
    public boolean isBoostSizeApplied() { return boostSizeApplied; }
    public boolean isOverdriveCharging() { return overdriveCharging; }
    public boolean hasBrokenOverdriveOnce() { return hasBrokenOverdriveOnce; }
    public boolean isInOtherworld() { return inOtherworld; }
    public long getOtherworldSince() { return otherworldSince; }
    public boolean isDowned()     { return downed; }
    public long getDownedUntil()  { return downedUntil; }
    public long getInCombatUntil()  { return inCombatUntil; }
    public void setInCombatUntil(long t) { this.inCombatUntil = t; }
    public boolean isHardcoreDeath() { return hardcoreDeath; }
    public void setHardcoreDeath(boolean v) { this.hardcoreDeath = v; }
    public boolean hasTail() { return hasTail; }
    public void setHasTail(boolean v) { this.hasTail = v; }
    public boolean hasSsj4Ritual() { return hasSsj4Ritual; }
    public void setHasSsj4Ritual(boolean v) { this.hasSsj4Ritual = v; }
    public boolean hasReceivedKaioWeights() { return receivedKaioWeights; }
    public void setReceivedKaioWeights(boolean v) { this.receivedKaioWeights = v; }

    public void setImmortal(boolean v)  { this.isImmortal  = v; }
    public void setDivine(boolean v)    { this.isDivine    = v; }
    public void setLegendary(boolean v) { this.isLegendary = v; }
    public void setFlyEnabled(boolean v)  { this.flyEnabled  = v; }
    public void setChargingKi(boolean v)  { this.chargingKi  = v; }
    public void setFlyBoosting(boolean v) { this.flyBoosting = v; }
    public void setBoostSizeApplied(boolean v) { this.boostSizeApplied = v; }
    public void setOverdriveCharging(boolean v) { this.overdriveCharging = v; }
    public void setHasBrokenOverdriveOnce(boolean v) { this.hasBrokenOverdriveOnce = v; }
    public void setInOtherworld(boolean v) { this.inOtherworld = v; }
    public void setOtherworldSince(long t) { this.otherworldSince = t; }
    public void setDowned(boolean v)     { this.downed = v; }
    public void setDownedUntil(long t)   { this.downedUntil = t; }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isImmortal",  isImmortal);
        tag.putBoolean("isDivine",    isDivine);
        tag.putBoolean("isLegendary", isLegendary);
        tag.putBoolean("flyEnabled",  flyEnabled);
        tag.putBoolean("chargingKi",  chargingKi);
        tag.putBoolean("inOtherworld", inOtherworld);
        tag.putLong("otherworldSince", otherworldSince);
        tag.putBoolean("downed", downed);
        tag.putLong("downedUntil", downedUntil);
        tag.putLong("inCombatUntil", inCombatUntil);
        tag.putBoolean("hardcoreDeath", hardcoreDeath);
        tag.putBoolean("hasBrokenOverdriveOnce", hasBrokenOverdriveOnce);
        tag.putBoolean("overdriveCharging", overdriveCharging);
        tag.putBoolean("hasTail", hasTail);
        tag.putBoolean("hasSsj4Ritual", hasSsj4Ritual);
        tag.putBoolean("receivedKaioWeights", receivedKaioWeights);
        return tag;
    }

    public void load(CompoundTag tag) {
        // hasTail: default true. Una partida guardada ANTES de que este campo existiera no
        // tiene la clave -> debe leer true igual (todo saiyan la tenía), no false por omisión
        // como haría getBoolean() con una key ausente.
        this.hasTail = !tag.contains("hasTail") || tag.getBoolean("hasTail");
        this.hasSsj4Ritual = tag.getBoolean("hasSsj4Ritual");
        this.receivedKaioWeights = tag.getBoolean("receivedKaioWeights");
        this.isImmortal  = tag.getBoolean("isImmortal");
        this.isDivine    = tag.getBoolean("isDivine");
        this.isLegendary = tag.getBoolean("isLegendary");
        this.flyEnabled  = tag.getBoolean("flyEnabled");
        this.chargingKi  = tag.getBoolean("chargingKi");
        this.inOtherworld = tag.getBoolean("inOtherworld");
        this.otherworldSince = tag.getLong("otherworldSince");
        this.downed = tag.getBoolean("downed");
        this.downedUntil = tag.getLong("downedUntil");
        this.inCombatUntil = tag.getLong("inCombatUntil");
        this.hardcoreDeath = tag.getBoolean("hardcoreDeath");
        this.hasBrokenOverdriveOnce = tag.getBoolean("hasBrokenOverdriveOnce");
        this.overdriveCharging = tag.getBoolean("overdriveCharging");
    }
}