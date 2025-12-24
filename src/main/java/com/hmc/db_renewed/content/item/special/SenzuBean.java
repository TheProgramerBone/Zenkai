package com.hmc.db_renewed.content.item.special;

import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.content.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        if (entity instanceof Player player) {
            // cooldown de 7 segundos
            player.getCooldowns().addCooldown(this, 20 * 7);

            if (!level.isClientSide) {
                // Sonido custom
                level.playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        ModSounds.SENZU_EAT.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F
                );

                // === Integración con sistema de stats (body / stamina / ki) ===
                PlayerStatsAttachment att = player.getData(DataAttachments.PLAYER_STATS.get());

                // Curar body al máximo
                int bodyMissing = att.getBodyMax() - att.getBody();
                if (bodyMissing > 0) {
                    att.addBody(bodyMissing);
                }

                // Rellenar stamina al máximo
                int stamMissing = att.getStaminaMax() - att.getStamina();
                if (stamMissing > 0) {
                    att.addStamina(stamMissing);
                }

                // Rellenar ki al máximo
                int kiMissing = att.getEnergyMax() - att.getEnergy();
                if (kiMissing > 0) {
                    att.addEnergy(kiMissing);
                }

                // Opcional: también sincronizar al cliente
                PlayerLifeCycle.syncIfServer(player);

                // === Ajuste vanilla para que sea consistente
                // Vida vanilla al máximo (por si acaso)
                player.setHealth(player.getMaxHealth());
                // Hambre y saturación como en DBC (lleno total)
                player.getFoodData().eat(20, 1.0F);

                // Puedes mantener los efectos si quieres partículas/cross-compat
                player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 255));
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BLOCK;
    }
}