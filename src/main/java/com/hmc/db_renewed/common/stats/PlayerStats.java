package com.hmc.db_renewed.common.stats;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PlayerStats {

    private static final Map<ServerPlayer, PlayerStats> STATS_MAP = new HashMap<>();

    private int strength;
    private int dexterity;
    private int constitution;
    private int willpower;
    private int mind;
    private int spirit;

    private int currentKi; // Se deriva de spirit

    public PlayerStats(int strength, int dexterity, int constitution, int willpower, int mind, int spirit) {
        this.strength = strength;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.willpower = willpower;
        this.mind = mind;
        this.spirit = spirit;
        this.currentKi = calculateMaxKi(); // Inicializa al máximo
    }

    public static PlayerStats get(ServerPlayer player) {
        return STATS_MAP.computeIfAbsent(player, p -> new PlayerStats(10, 10, 10, 10, 10, 10));
    }

    public boolean setStat(String name, int value) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "strength" -> strength = value;
            case "dexterity" -> dexterity = value;
            case "constitution" -> constitution = value;
            case "willpower" -> willpower = value;
            case "mind" -> mind = value;
            case "spirit" -> {
                spirit = value;
                currentKi = Math.min(currentKi, calculateMaxKi()); // Ajustar si excedía
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public Integer getStat(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "strength" -> strength;
            case "dexterity" -> dexterity;
            case "constitution" -> constitution;
            case "willpower" -> willpower;
            case "mind" -> mind;
            case "spirit" -> spirit;
            default -> null;
        };
    }

    public int calculateMaxKi() {
        return spirit * 100; // Ajusta esta fórmula si es necesario
    }

    public int getCurrentKi() {
        return currentKi;
    }

    public void setCurrentKi(int value) {
        this.currentKi = Math.max(0, Math.min(value, calculateMaxKi())); // Entre 0 y máximo
    }

    public void modifyCurrentKi(int delta) {
        setCurrentKi(currentKi + delta);
    }

    public int getStrength()     { return strength; }
    public int getDexterity()    { return dexterity; }
    public int getConstitution() { return constitution; }
    public int getWillpower()    { return willpower; }
    public int getMind()         { return mind; }
    public int getSpirit()       { return spirit; }
}