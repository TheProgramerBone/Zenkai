package com.hmc.db_renewed.sound;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, DragonBlockRenewed.MOD_ID);

    public static final Supplier<SoundEvent> DRAGON_BALL_RADAR_USE = registerSoundEvent("dragon_ball_radar_use");
    public static final Supplier<SoundEvent> DRAGON_BALL_RADAR_SEARCHING = registerSoundEvent("dragon_ball_radar_searching");
    public static final Supplier<SoundEvent> DRAGON_BALL_RADAR_NEAR = registerSoundEvent("dragon_ball_radar_close");
    public static final Supplier<SoundEvent> DRAGON_BALL_USE = registerSoundEvent("dragon_ball_use");
    public static final Supplier<SoundEvent> SENZU_EAT = registerSoundEvent("senzu_eat");


    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
