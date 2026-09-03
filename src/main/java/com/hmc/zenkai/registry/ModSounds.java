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
    /** Arranque de una sola vez (cargar ki O empezar a transformar, mismo sonido para las
     *  dos): suena una vez y, al acabar solo, ZenkaiPlayerSounds pasa a KI_CHARGE (el bucle).
     *  Ver el javadoc de esa clase para la máquina de estados completa. */
    public static final Supplier<SoundEvent> KI_CHARGE_START = registerSoundEvent("ki_charge_start");
    /** Bucle de "cargar ki" / transformar. Turbo (TURBO_LOOP) usa el MISMO archivo de audio
     *  pero un evento aparte, porque turbo salta directo al bucle sin pasar por KI_CHARGE_START
     *  — ver ZenkaiPlayerSounds. */
    public static final Supplier<SoundEvent> KI_CHARGE = registerSoundEvent("ki_charge");
    /** Cierre de una sola vez al volver de verdad a base (tap voluntario o forceBase()). */
    public static final Supplier<SoundEvent> DETRANSFORM = registerSoundEvent("detransform");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_1 = registerSoundEvent("ki_attack_charge_1");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_2 = registerSoundEvent("ki_attack_charge_2");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_3 = registerSoundEvent("ki_attack_charge_3");
    public static final Supplier<SoundEvent> KI_ATTACK_CHARGE_4 = registerSoundEvent("ki_attack_charge_4");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_1 = registerSoundEvent("ki_attack_release_1");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_2 = registerSoundEvent("ki_attack_release_2");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_3 = registerSoundEvent("ki_attack_release_3");
    public static final Supplier<SoundEvent> KI_ATTACK_RELEASE_4 = registerSoundEvent("ki_attack_release_4");
    /** Golpe físico normal que CONECTA. UN evento con cuatro variantes en sounds.json: el
     *  motor alterna solo. Cuatro eventos separados podrían desincronizarse en volumen o
     *  subtítulo. Ver CombatFeedback.onAttack. */
    public static final Supplier<SoundEvent> HIT = registerSoundEvent("hit");
    /** Golpe físico normal contra un defensor que está bloqueando (mismo AttackEntityEvent que
     *  HIT, sustituye a HIT en vez de sonar junto a él — ver CombatFeedback.onAttack). Tres
     *  variantes, mismo motivo que HIT. */
    public static final Supplier<SoundEvent> BLOCK = registerSoundEvent("block");
    /** El golpe que derriba ("entra en derribado") o mata de verdad a un jugador — ver
     *  CombatZenkaiHooks.onBodyDepleted/killImmortalOutright. NO suena en el timeout de
     *  DownedSystem: ese desenlace no es un golpe, es un temporizador expirando. */
    public static final Supplier<SoundEvent> KNOCKOUT = registerSoundEvent("knockout");
    /** Impacto de una PhysicalTechnique (Dash Punch/Heavy Blow/Barrage/Kiai) que CONECTA sin que
     *  la víctima esté bloqueando — ver PhysicalCombatServer.impactFx. */
    public static final Supplier<SoundEvent> PHYSICAL_IMPACT = registerSoundEvent("physical_impact");
    /** Mismo impacto que PHYSICAL_IMPACT, pero la víctima está bloqueando. */
    public static final Supplier<SoundEvent> PHYSICAL_IMPACT_BLOCK = registerSoundEvent("physical_impact_block");
    /** Bucle de turbo (R). Distinto del de carga de ki: son dos estados distintos y el
     *  jugador tiene que poder oír cuál está activo sin mirar el HUD. */
    public static final Supplier<SoundEvent> TURBO_LOOP = registerSoundEvent("turbo_loop");
    /** Registrado para la futura Transmisión Instantánea (teletransporte) — todavía SIN
     *  cablear a ningún sitio, esa feature no existe aún. Ver
     *  .claude/pendiente/instant-transmission-pendiente.md. */
    public static final Supplier<SoundEvent> TELEPORT = registerSoundEvent("teleport");

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
