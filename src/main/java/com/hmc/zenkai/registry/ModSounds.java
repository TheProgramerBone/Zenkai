package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Zenkai.MOD_ID);

    public static final Supplier<SoundEvent> DRAGON_BALL_RADAR_USE = registerSoundEvent("dragon_ball_radar_use");
    public static final Supplier<SoundEvent> DRAGON_BALL_RADAR_SEARCHING = registerSoundEvent("dragon_ball_radar_searching");
    public static final Supplier<SoundEvent> DRAGON_BALL_RADAR_NEAR = registerSoundEvent("dragon_ball_radar_close");
    public static final Supplier<SoundEvent> DRAGON_BALL_USE = registerSoundEvent("dragon_ball_use");

    public static final Supplier<SoundEvent> SENZU_EAT = registerSoundEvent("senzu_eat");
    public static final Supplier<SoundEvent> WISH_GRANTED = registerSoundEvent("wish_granted");
    public static final Supplier<SoundEvent> SPECIALIST = registerSoundEvent("specialist");
    public static final Supplier<SoundEvent> KI_CHARGE = registerSoundEvent("ki_charge");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_1 = registerSoundEvent("ki_attack_charge_1");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_2 = registerSoundEvent("ki_attack_charge_2");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_3 = registerSoundEvent("ki_attack_charge_3");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_4 = registerSoundEvent("ki_attack_charge_4");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_1 = registerSoundEvent("ki_attack_release_1");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_2 = registerSoundEvent("ki_attack_release_2");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_3 = registerSoundEvent("ki_attack_release_3");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_4 = registerSoundEvent("ki_attack_release_4");

    // ── Banco de scouter ─────────────────────────────────────────────────────
    public static final Supplier<SoundEvent> SCOUTER_BENCH_OPEN    = registerSoundEvent("scouter_bench_open");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_INSERT  = registerSoundEvent("scouter_bench_insert");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_REMOVE  = registerSoundEvent("scouter_bench_remove");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_START   = registerSoundEvent("scouter_bench_start");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_WORKING = registerSoundEvent("scouter_bench_working");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_FINISH  = registerSoundEvent("scouter_bench_finish");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_CANCEL  = registerSoundEvent("scouter_bench_cancel");
    public static final Supplier<SoundEvent> SCOUTER_BENCH_FAIL    = registerSoundEvent("scouter_bench_fail");

    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
