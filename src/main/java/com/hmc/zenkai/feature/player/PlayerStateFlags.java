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
     * El jugador empuja el vuelo (adelante pulsado o crucero activo). Bit cliente -> servidor por
     * FlyThrustPacket. Transitorio como flyBoosting: nada de NBT ni sync.
     */
    private boolean flyThrust = false;
    /** Rampa de vuelo 0..1 (ver FlightSystem). Solo servidor, transitoria. */
    private double flyThrottle = 0.0;

    /**
     * Tracker transitorio (por lado, NO se guarda ni sincroniza): si la hitbox de boost ya está
     * aplicada. Sirve para llamar refreshDimensions() SOLO en la transición (evita recalcular cada tick).
     */
    private boolean boostSizeApplied = false;

    /**
     * Cuenta atrás (ticks, transitoria, NO se guarda) del aterrizaje de emergencia: cuando el
     * jugador pierde la capacidad de volar a media altura (sobrecarga, respec de la skill Fly),
     * FlightSystem.tick() no corta mayfly/flying en seco — mantiene el vuelo unos ticks más con
     * velocidad recortada y descenso forzado hasta tocar el suelo o agotar esta cuenta. Ver
     * FlightSystem para el detalle; 0 = no está en aterrizaje de emergencia.
     */
    private int emergencyLandingTicks = 0;

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
     * OozaruSystem/PlayerFormAttachment). Super Oozaru se controla con SSJ1
     * (FormAccess.readyForSuperOozaru) y el salto a SSJ4 pide SSJ3 (readyForSsj4Ritual); SSJ4
     * no queda utilizable/visible en la rueda ni en el árbol hasta completarlo una vez — ver
     * FormAccess.unlocked(SSJ4). Persistente: no hay que repetir el ritual cada partida ni
     * cada respec, solo re-comprar el nivel si se pierde por respec.
     */
    private boolean hasSsj4Ritual = false;

    /**
     * Llegó a Súper Oozaru al menos una vez. Lo REVELA en el árbol de transformaciones
     * (SkillTreeNodes.revealed): se puede controlar desde SSJ1, pero el usuario quiere que el
     * nodo no aparezca hasta que el jugador lo consiga (2026-10-21). Persistente, viaja al
     * cliente por este mismo save()/load().
     */
    private boolean reachedSuperOozaru = false;

    /**
     * Beast desbloqueado por furia: una mascota propia o un miembro de la party murió cerca del
     * jugador teniendo él la maestría de Mystic requerida (ver RageSystem). Persistente, igual que
     * hasSsj4Ritual, y viaja al cliente por este mismo save()/load() para que la rueda lo sepa.
     */
    private boolean beastFury = false;
    /** Rage Forms: gameTime del servidor del último despertar o progreso por un aliado (-1 = nunca). Ver RageSystem. */
    private long rageAllyEventTick = -1L;
    /** Rage Forms: gameTime del último despertar o progreso por un hecho MENOR (mascota, aliado derribado); enfriamiento corto aparte. */
    private long rageMinorEventTick = -1L;
    /** Rage Forms: ids de los últimos hechos recompensados (anillo), contra recompensar dos veces lo mismo. */
    private final java.util.ArrayDeque<String> rageEventIds = new java.util.ArrayDeque<>();
    public static final int RAGE_EVENT_IDS_CAP = 64;
    /** Formas cuya secuencia larga (primera transformación) ya se vio. Del PERSONAJE: la reencarnación lo reinicia. */
    private final java.util.LinkedHashSet<String> longCinematicSeen = new java.util.LinkedHashSet<>();
    /** false SOLO en partidas anteriores a Rage Forms (la clave no existe): RageMigration las pone al día al entrar. */
    private boolean rageMigrated = true;

    /**
     * Audiencia divina concedida por un maestro (Tori-Bot hoy; ver GodKiAudiencePacket): la
     * cadena de God Ki del árbol pasa de SELLADA a HABILITADA y se puede afrontar la prueba. No
     * da God Ki por sí sola. Persistente, viaja al cliente por este mismo save()/load().
     */
    private boolean godKiAudience = false;

    /**
     * Ya se hizo la tirada de «nacer Legendario» de ESTE personaje (ver LegendaryBirth). Una por vida: la reencarnación crea
     * un attachment nuevo y por eso vuelve a false. Sin él, volver a elegir raza o estilo repetiría la tirada.
     */
    private boolean legendaryRolled = false;
    /**
     * El deseo de potencial ya le dio al Namekiano el indicio del poder que hay por encima del Súper Namekiano: su nodo oculto
     * (Naranja) pasa a verse en el árbol. Persistente; viaja al cliente por este mismo save()/load().
     */
    private boolean namekOrangeHinted = false;

    /**
     * Cola de saiyan: una de las tres condiciones de Oozaru (junto con raza y luna llena, ver
     * OozaruConditions). PENDIENTE: hoy no existe ninguna pieza de GeckoLibArmor de cola
     * todavía, así que el valor por defecto es true (cada saiyan la tiene) para no bloquear el
     * sistema mientras se modela; cuando exista el ítem, algo (equipar/perder la cola) deberá
     * poder ponerlo en false. No se restringe por raza aquí: PlayerStateFlags no conoce razas,
     * ese filtro vive en OozaruConditions.
     */
    private boolean hasTail = true;

    /** Power Points del Arcosiano (ver ArcosianPowerSystem): tanque que se llena en reposo. Va en
     *  save()/load() porque ese round-trip de NBT es también el transporte de red (ver
     *  overdriveCharging). */
    private double arcosianPp = 0.0;
    /** Absorción del Majin (ver MajinAbsorption): pilas de masa vivas, gameTime en que caducan y absorciones
     *  totales (parte permanente). El enfriamiento es el de la técnica física. Van en save()/load(), que es también
     *  el transporte de red: el cliente calcula el mismo bono de stats que el servidor. */
    private int absorbMass = 0;
    private long absorbMassUntil = 0L;
    private int absorbedTotal = 0;
    /** Poder robado a lo absorbido con «Majin Devorador» (fracción aditiva de stats); caduca con la masa. */
    private double absorbStolen = 0.0;
    public double getAbsorbStolen() { return absorbStolen; }
    public void setAbsorbStolen(double v) { this.absorbStolen = Math.max(0.0, v); }
    public int getAbsorbMass() { return absorbMass; }
    public void setAbsorbMass(int v) { this.absorbMass = Math.max(0, v); }
    public long getAbsorbMassUntil() { return absorbMassUntil; }
    public void setAbsorbMassUntil(long v) { this.absorbMassUntil = v; }
    public int getAbsorbedTotal() { return absorbedTotal; }
    public void setAbsorbedTotal(int v) { this.absorbedTotal = Math.max(0, v); }

    /** Barra de calor de Ultra Instinct, 0-100 (ver OverlayMetersSystem). */
    private double uiHeat = 0.0;
    /** Barra de daño acumulado de Ultra Ego, 0-100. */
    private double ueCharge = 0.0;

    /** Ya recibió el servicio de pesas de Kaiosama (ver KaioEntity.services()). Regalo de una
     *  sola vez: sin este flag, pedirlas repetidas veces sería una granja infinita de
     *  WEIGHTED_STRAPS/WEIGHTED_CAPE gratis. */
    private boolean receivedKaioWeights = false;

    /** El jugador está muerto / en el otro mundo. */
    private boolean inOtherworld = false;
    /** gameTime en que fue enviado al otro mundo (para el contador de Yemma). */
    private long otherworldSince = 0L;
    /**
     * Visita a la Tierra por Uranai Baba: ticks que le quedan (0 = sin visita). Se guarda como ticks restantes y no como
     * instante de fin porque el reloj solo corre con el jugador conectado. Durante la visita {@code inOtherworld} sigue
     * puesto (sigue muerto: halo, Esferas, Yemma al volver); este campo es lo que dice que está en la Tierra.
     */
    private long earthVisitTicksLeft = 0L;
    /**
     * Espera hasta poder pedir otra visita a Baba (0 = libre). Empieza al TERMINAR una visita y, como el reloj de la visita,
     * solo avanza con el jugador conectado. Sin ella se encadenarían visitas sin coste. Se borra al revivir y con
     * {@code /zenkai reset visit}.
     */
    private long earthVisitCooldownTicks = 0L;

    /** gameTime en que expira el estado "en combate". Ver InCombatState: es un instante
     *  futuro y no un contador para no tener que decrementarlo cada tick. */
    private long inCombatUntil = 0L;
    /** Game time hasta el que dura el cooldown de la cámara de contención de ki (ver
     *  KiContainmentChamberBlockEntity). Es POR JUGADOR y no por máquina: si fuera de la cámara,
     *  bastaría cambiar de cámara para saltárselo. Viaja en save()/load() porque la HUD del dueño
     *  enseña los segundos que faltan (en esta clase «no persistir» y «no sincronizar» son lo mismo). */
    private long kiChamberCooldownUntil = 0L;

    /** gameTime hasta el que el jugador está EN el ritual de God Ki (candidato o participante): el
     *  cliente oculta el gauge de carga de ki (el texto del ritual se solapaba con él). Instante futuro,
     *  como inCombatUntil: caduca solo, sin tener que apagarlo. Viaja en save()/load() (transporte de sync). */
    private long godKiRitualUntil = 0L;

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
    public boolean isFlyThrust() { return flyThrust; }
    public double getFlyThrottle() { return flyThrottle; }
    public boolean isBoostSizeApplied() { return boostSizeApplied; }
    public int  getEmergencyLandingTicks() { return emergencyLandingTicks; }
    public boolean isOverdriveCharging() { return overdriveCharging; }
    public boolean hasBrokenOverdriveOnce() { return hasBrokenOverdriveOnce; }
    public boolean isInOtherworld() { return inOtherworld; }
    public long getOtherworldSince() { return otherworldSince; }
    public boolean isDowned()     { return downed; }
    public long getDownedUntil()  { return downedUntil; }
    public long getGodKiRitualUntil() { return godKiRitualUntil; }
    public void setGodKiRitualUntil(long t) { this.godKiRitualUntil = t; }
    public long getInCombatUntil()  { return inCombatUntil; }
    public void setInCombatUntil(long t) { this.inCombatUntil = t; }
    public long getKiChamberCooldownUntil() { return kiChamberCooldownUntil; }
    public void setKiChamberCooldownUntil(long t) { this.kiChamberCooldownUntil = Math.max(0L, t); }
    public boolean isHardcoreDeath() { return hardcoreDeath; }
    public void setHardcoreDeath(boolean v) { this.hardcoreDeath = v; }
    public boolean hasTail() { return hasTail; }
    public void setHasTail(boolean v) { this.hasTail = v; }
    public double getArcosianPp() { return arcosianPp; }
    public void setArcosianPp(double v) { this.arcosianPp = Math.max(0.0, v); }
    public double getUiHeat() { return uiHeat; }
    public void setUiHeat(double v) { this.uiHeat = Math.max(0.0, Math.min(100.0, v)); }
    public double getUeCharge() { return ueCharge; }
    public void setUeCharge(double v) { this.ueCharge = Math.max(0.0, Math.min(100.0, v)); }
    public boolean hasSsj4Ritual() { return hasSsj4Ritual; }
    public void setHasSsj4Ritual(boolean v) { this.hasSsj4Ritual = v; }
    public boolean hasReachedSuperOozaru() { return reachedSuperOozaru; }
    public void setReachedSuperOozaru(boolean v) { this.reachedSuperOozaru = v; }
    public boolean hasBeastFury() { return beastFury; }
    public void setBeastFury(boolean v) { this.beastFury = v; }

    public boolean hasSeenLongCinematic(String form) { return longCinematicSeen.contains(form); }
    /** @return true si es la primera vez (no estaba) */
    public boolean markLongCinematicSeen(String form) { return longCinematicSeen.add(form); }
    /** Solo para los arneses de capturas: vuelve a dejar la forma como «no vista» para repetir su primera secuencia larga. */
    public void forgetLongCinematicSeen(String form) { longCinematicSeen.remove(form); }
    /** Vuelve a armar la secuencia larga de cada forma (comando de pruebas {@code /zenkai rage scene reset}). */
    public void clearLongCinematicSeen() { longCinematicSeen.clear(); }
    public java.util.Set<String> longCinematicSeen() { return java.util.Collections.unmodifiableSet(longCinematicSeen); }
    public boolean isRageMigrated() { return rageMigrated; }
    public void setRageMigrated(boolean v) { this.rageMigrated = v; }

    public long getRageAllyEventTick() { return rageAllyEventTick; }
    public void setRageAllyEventTick(long t) { this.rageAllyEventTick = t; }
    public long getRageMinorEventTick() { return rageMinorEventTick; }
    public void setRageMinorEventTick(long t) { this.rageMinorEventTick = t; }
    public boolean hasRageEvent(String id) { return rageEventIds.contains(id); }
    /** Anota un hecho ya recompensado; el anillo descarta el más antiguo al pasar de RAGE_EVENT_IDS_CAP. */
    public void addRageEvent(String id) {
        if (rageEventIds.contains(id)) return;
        rageEventIds.addLast(id);
        while (rageEventIds.size() > RAGE_EVENT_IDS_CAP) rageEventIds.removeFirst();
    }
    public boolean hasGodKiAudience() { return godKiAudience; }
    public boolean isLegendaryRolled() { return legendaryRolled; }
    public void setLegendaryRolled(boolean v) { this.legendaryRolled = v; }
    public boolean isNamekOrangeHinted() { return namekOrangeHinted; }
    public void setNamekOrangeHinted(boolean v) { this.namekOrangeHinted = v; }
    public void setGodKiAudience(boolean v) { this.godKiAudience = v; }
    public boolean hasReceivedKaioWeights() { return receivedKaioWeights; }
    public void setReceivedKaioWeights(boolean v) { this.receivedKaioWeights = v; }

    public void setImmortal(boolean v)  { this.isImmortal  = v; }
    public void setDivine(boolean v)    { this.isDivine    = v; }
    public void setLegendary(boolean v) { this.isLegendary = v; }
    public void setFlyEnabled(boolean v)  { this.flyEnabled  = v; }
    public void setChargingKi(boolean v)  { this.chargingKi  = v; }
    public void setFlyBoosting(boolean v) { this.flyBoosting = v; }
    public void setFlyThrust(boolean v) { this.flyThrust = v; }
    public void setFlyThrottle(double v) { this.flyThrottle = Math.max(0.0, Math.min(1.0, v)); }
    public void setBoostSizeApplied(boolean v) { this.boostSizeApplied = v; }
    public void setEmergencyLandingTicks(int v) { this.emergencyLandingTicks = v; }
    public void setOverdriveCharging(boolean v) { this.overdriveCharging = v; }
    public void setHasBrokenOverdriveOnce(boolean v) { this.hasBrokenOverdriveOnce = v; }
    public void setInOtherworld(boolean v) { this.inOtherworld = v; }
    public void setOtherworldSince(long t) { this.otherworldSince = t; }
    public long getEarthVisitTicksLeft() { return earthVisitTicksLeft; }
    public void setEarthVisitTicksLeft(long t) { this.earthVisitTicksLeft = Math.max(0L, t); }
    public long getEarthVisitCooldownTicks() { return earthVisitCooldownTicks; }
    public void setEarthVisitCooldownTicks(long t) { this.earthVisitCooldownTicks = Math.max(0L, t); }
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
        tag.putLong("earthVisitTicksLeft", earthVisitTicksLeft);
        tag.putLong("earthVisitCooldownTicks", earthVisitCooldownTicks);
        tag.putBoolean("downed", downed);
        tag.putLong("downedUntil", downedUntil);
        tag.putLong("inCombatUntil", inCombatUntil);
        tag.putLong("kiChamberCooldownUntil", kiChamberCooldownUntil);
        tag.putLong("godKiRitualUntil", godKiRitualUntil);
        tag.putBoolean("hardcoreDeath", hardcoreDeath);
        tag.putBoolean("hasBrokenOverdriveOnce", hasBrokenOverdriveOnce);
        tag.putBoolean("overdriveCharging", overdriveCharging);
        tag.putBoolean("hasTail", hasTail);
        tag.putBoolean("hasSsj4Ritual", hasSsj4Ritual);
        tag.putBoolean("reachedSuperOozaru", reachedSuperOozaru);
        tag.putBoolean("beastFury", beastFury);
        tag.putLong("rageAllyEventTick", rageAllyEventTick);
        tag.putLong("rageMinorEventTick", rageMinorEventTick);
        tag.putBoolean("rageMigrated", rageMigrated);
        net.minecraft.nbt.ListTag seen = new net.minecraft.nbt.ListTag();
        for (String f : longCinematicSeen) seen.add(net.minecraft.nbt.StringTag.valueOf(f));
        tag.put("longCinematicSeen", seen);
        net.minecraft.nbt.ListTag rage = new net.minecraft.nbt.ListTag();
        for (String id : rageEventIds) rage.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put("rageEventIds", rage);
        tag.putBoolean("godKiAudience", godKiAudience);
        tag.putBoolean("legendaryRolled", legendaryRolled);
        tag.putBoolean("namekOrangeHinted", namekOrangeHinted);
        tag.putDouble("arcosianPp", arcosianPp);
        tag.putInt("absorbMass", absorbMass);
        tag.putLong("absorbMassUntil", absorbMassUntil);
        tag.putInt("absorbedTotal", absorbedTotal);
        tag.putDouble("absorbStolen", absorbStolen);
        tag.putDouble("uiHeat", uiHeat);
        tag.putDouble("ueCharge", ueCharge);
        tag.putBoolean("receivedKaioWeights", receivedKaioWeights);
        return tag;
    }

    public void load(CompoundTag tag) {
        // hasTail: default true. Una partida guardada ANTES de que este campo existiera no
        // tiene la clave -> debe leer true igual (cada saiyan la tenía), no false por omisión
        // como haría getBoolean() con una key ausente.
        this.hasTail = !tag.contains("hasTail") || tag.getBoolean("hasTail");
        this.hasSsj4Ritual = tag.getBoolean("hasSsj4Ritual");
        this.reachedSuperOozaru = tag.getBoolean("reachedSuperOozaru");
        this.beastFury = tag.getBoolean("beastFury");
        this.rageAllyEventTick = tag.contains("rageAllyEventTick") ? tag.getLong("rageAllyEventTick") : -1L;
        this.rageMinorEventTick = tag.contains("rageMinorEventTick") ? tag.getLong("rageMinorEventTick") : -1L;
        // Sin la clave = partida anterior a Rage Forms: pendiente de migrar (ver RageMigration).
        this.rageMigrated = tag.getBoolean("rageMigrated");
        longCinematicSeen.clear();
        net.minecraft.nbt.ListTag seen = tag.getList("longCinematicSeen", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < seen.size(); i++) longCinematicSeen.add(seen.getString(i));
        rageEventIds.clear();
        net.minecraft.nbt.ListTag rage = tag.getList("rageEventIds", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < rage.size(); i++) rageEventIds.add(rage.getString(i));
        this.godKiAudience = tag.getBoolean("godKiAudience");
        // Sin la clave = partida anterior a la 0.7.0: PlayerStatsAttachment.load decide (un personaje ya creado no tira).
        this.legendaryRolled = tag.getBoolean("legendaryRolled");
        this.namekOrangeHinted = tag.getBoolean("namekOrangeHinted");
        this.arcosianPp = tag.getDouble("arcosianPp");
        this.absorbMass = tag.getInt("absorbMass");
        this.absorbMassUntil = tag.getLong("absorbMassUntil");
        this.absorbedTotal = tag.getInt("absorbedTotal");
        this.absorbStolen = tag.getDouble("absorbStolen");
        this.uiHeat = tag.getDouble("uiHeat");
        this.ueCharge = tag.getDouble("ueCharge");
        this.receivedKaioWeights = tag.getBoolean("receivedKaioWeights");
        this.isImmortal  = tag.getBoolean("isImmortal");
        this.isDivine    = tag.getBoolean("isDivine");
        this.isLegendary = tag.getBoolean("isLegendary");
        this.flyEnabled  = tag.getBoolean("flyEnabled");
        this.chargingKi  = tag.getBoolean("chargingKi");
        this.inOtherworld = tag.getBoolean("inOtherworld");
        this.otherworldSince = tag.getLong("otherworldSince");
        this.earthVisitTicksLeft = tag.getLong("earthVisitTicksLeft");
        this.earthVisitCooldownTicks = tag.getLong("earthVisitCooldownTicks");
        this.downed = tag.getBoolean("downed");
        this.downedUntil = tag.getLong("downedUntil");
        this.inCombatUntil = tag.getLong("inCombatUntil");
        this.kiChamberCooldownUntil = tag.getLong("kiChamberCooldownUntil");
        this.godKiRitualUntil = tag.getLong("godKiRitualUntil");
        this.hardcoreDeath = tag.getBoolean("hardcoreDeath");
        this.hasBrokenOverdriveOnce = tag.getBoolean("hasBrokenOverdriveOnce");
        this.overdriveCharging = tag.getBoolean("overdriveCharging");
    }
}