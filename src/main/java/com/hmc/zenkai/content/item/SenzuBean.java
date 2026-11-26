package com.hmc.zenkai.content.item;

import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class SenzuBean extends Item {
    public SenzuBean(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        // instantáneo
        return 1;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(itemstack);
    }

    /** Cooldown de una senzu, COMPARTIDO con la bolsa de senzu (decisión del usuario 2026-11-26): alternar no cura el doble. */
    public static final int COOLDOWN_TICKS = 20 * 7;

    public static void startCooldown(Player player) {
        player.getCooldowns().addCooldown(com.hmc.zenkai.registry.ModItems.SENZU_BEAN.get(), COOLDOWN_TICKS);
        player.getCooldowns().addCooldown(com.hmc.zenkai.registry.ModItems.SENZU_BAG.get(), COOLDOWN_TICKS);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        if (entity instanceof Player player) {
            eat(player, level);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    /**
     * Efecto de comerse una senzu (cooldown, sonido, body/stamina/ki al máximo, hambre llena). Lo usan la senzu suelta y la
     * bolsa ({@link SenzuBagItem}); quien llama gasta la senzu.
     */
    public static void eat(Player player, Level level) {
        startCooldown(player);
        if (level.isClientSide) return;
        // Sonido custom
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SENZU_EAT.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);

        // === Integración con sistema de stats (body / stamina / ki) ===
        PlayerStatsAttachment att = player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int bodyMissing = att.getBodyMax() - att.getBody();
        if (bodyMissing > 0) att.addBody(bodyMissing);
        int stamMissing = att.getStaminaMax() - att.getStamina();
        if (stamMissing > 0) att.addStamina(stamMissing);
        int kiMissing = att.getEnergyMax() - att.getEnergy();
        if (kiMissing > 0) att.addEnergy(kiMissing);
        PlayerLifeCycle.syncIfServer(player);

        // === Ajuste vanilla ===
        // Sin raza no hay pool que curar: la senzu es un objeto de curación normal y
        // la vida vanilla es lo único que puede rellenar. CON raza, la vida la escribe
        // el espejo dentro de sync() y tocarla aquí sería el segundo escritor.
        if (!att.isRaceChosen()) {
            player.setHealth(player.getMaxHealth());
        }
        // Hambre y saturación como en DBC (lleno total)
        player.getFoodData().eat(20, 1.0F);
        // Efectos por partículas/cross-compat
        player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0));
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 255));
    }

    /**
     * Click derecho con la senzu sobre OTRO jugador: si está derribado (o le falta body) lo cura al
     * máximo. Es la vía "que alguien lo cure" antes de que expire el temporizador de derribado.
     */
    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player user,
                                                           @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (user.level().isClientSide || !healOther(user, target)) return InteractionResult.PASS;
        if (!user.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /**
     * Cura a otro jugador derribado o herido (senzu suelta y bolsa). Devuelve si lo curó; quien llama gasta la senzu.
     * Solo servidor.
     */
    public static boolean healOther(Player user, LivingEntity target) {
        if (!(target instanceof Player targetPlayer)) return false;

        // Un jugador derribado no puede curar/revivir a nadie más — no es distinto de cualquier
        // otra acción bloqueada por ActionRules mientras isDowned() (bloquear, físicas,
        // transformar...), solo que interactLivingEntity es un Item.use crudo que no pasa por
        // ese embudo, así que hace falta el chequeo aquí a mano.
        PlayerStatsAttachment userAtt = user.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (userAtt.flags().isDowned()) return false;

        PlayerStatsAttachment att = targetPlayer.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        boolean needsHelp = att.flags().isDowned() || att.getBody() < att.getBodyMax();
        if (!needsHelp) return false;

        // Rescate a tiempo si estaba derribado (antes de curar: el evento lee el derribo y su ventana).
        if (user instanceof net.minecraft.server.level.ServerPlayer rescuer
                && targetPlayer instanceof net.minecraft.server.level.ServerPlayer rescued) {
            com.hmc.zenkai.api.event.PlayerRescuedEvent.fire(rescuer, rescued,
                    com.hmc.zenkai.api.event.PlayerRescuedEvent.Method.SENZU);
        }
        int bodyMissing = att.getBodyMax() - att.getBody();
        if (bodyMissing > 0) att.addBody(bodyMissing);
        att.addStamina(att.getStaminaMax());
        att.addEnergy(att.getEnergyMax());
        if (!att.isRaceChosen()) targetPlayer.setHealth(targetPlayer.getMaxHealth());
        PlayerLifeCycle.syncIfServer(targetPlayer);

        startCooldown(user);
        user.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                ModSounds.SENZU_EAT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BLOCK;
    }
}