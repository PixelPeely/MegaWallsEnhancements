package fr.alexdoru.megawallsenhancementsmod.features;

import fr.alexdoru.megawallsenhancementsmod.asm.hooks.NetHandlerPlayClientHook;
import fr.alexdoru.megawallsenhancementsmod.chat.ChatUtil;
import fr.alexdoru.megawallsenhancementsmod.config.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class PHXAutoHeal {
    public static final Minecraft mc = Minecraft.getMinecraft();

    private int cooldown = 0;
    private int timer = -1;
    private EntityPlayer target = null;

    private double manhattanDistance(Vec3 a, Vec3 b) {
        return Math.abs(Math.abs(a.xCoord - b.xCoord) + Math.abs(a.yCoord - b.yCoord) + Math.abs(a.zCoord - b.zCoord));
    }

    private int getItemByNameContents(String content) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        for (int i = 0; i <= 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName()).contains(content))
                return i;
        }
        return -1;
    }

    private boolean readyToHeal() {
        return ConfigHandler.phxAutoHealLevel != 0
                && mc.thePlayer.experienceLevel == 100
                && cooldown == 0
                && SquadHandler.getSquad().size() > 1
                && mc.thePlayer.getDisplayName().getFormattedText().contains("[PHX]")
                && getItemByNameContents("Sword") != -1;
    }

    private void healPlayer(EntityPlayer target, int slotWithSword) {
        EntityPlayerSP player = mc.thePlayer;
        float initialYaw = player.rotationYawHead;
        float initialPitch = player.cameraPitch;
        int initialSlot = player.inventory.currentItem;
        boolean shouldRequestSlotChange = slotWithSword != initialSlot;

        //Calculate look direction
        double d0 = player.posX - target.posX;
        double d1 = player.posY - target.posY;
        double d2 = player.posZ - target.posZ;
        double d3 = (double) MathHelper.sqrt_double(d0 * d0 + d2 * d2);
        float f = (float) (90.0F - MathHelper.atan2(-d2, d0) * 180.0D / Math.PI);
        float f1 = (float) (MathHelper.atan2(d1, d3) * 180.0D / Math.PI);

        if (ConfigHandler.phxAutoHealLevel == 1) {//Apply look and slot switch client-side
            player.setPositionAndRotation(player.posX, player.posY, player.posZ, MathHelper.wrapAngleTo180_float(f), MathHelper.wrapAngleTo180_float(f1));
            if (shouldRequestSlotChange)
                player.inventory.currentItem = slotWithSword;
        }
        else if (shouldRequestSlotChange) {//Tell server to look and switch slot
                player.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slotWithSword));
            player.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(MathHelper.wrapAngleTo180_float(f), MathHelper.wrapAngleTo180_float(f1), player.onGround));
        }

        if (timer == 0) {//Activate
            cooldown = 10;
            player.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(null));
            if (player.getItemInUseDuration() == 0) //Only release if not already using another item (such as a potion)
                player.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            if (ConfigHandler.phxAutoHealLevel == 2) {//Snap back
                player.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(initialYaw, initialPitch, player.onGround));
                if (shouldRequestSlotChange)
                    player.sendQueue.addToSendQueue(new C09PacketHeldItemChange(initialSlot));
            }
            ChatUtil.addChatMessage(EnumChatFormatting.AQUA + "Phoenix Auto-Heal " + EnumChatFormatting.GRAY + "healed " + EnumChatFormatting.GREEN + target.getName() + EnumChatFormatting.GRAY + "!");
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || !readyToHeal()) {
            if (cooldown > 0)
                cooldown--;
            return;
        }
        if (timer > -1) {
            if (target != null)
                healPlayer(target, getItemByNameContents("Sword"));
            else
                timer = 0;
            timer--;
            return;
        }
        List<EntityPlayer> players = new ArrayList<>();
        for (String squadMember : SquadHandler.getSquad().keySet()) {
            NetworkPlayerInfo info = NetHandlerPlayClientHook.getPlayerInfo(squadMember);
            if (info != null) {
                final EntityPlayer player = mc.theWorld.getPlayerEntityByUUID(info.getGameProfile().getId());
                if (player != null)
                    players.add(player);
            }
        }

        for (EntityPlayer player : players) {
            if (player.getHealth() > 8
                || player == mc.thePlayer
                || player.getDisplayName().getFormattedText().contains("[PHX]")
                || manhattanDistance(mc.thePlayer.getPositionVector(), player.getPositionVector()) > 14)
                continue;
            timer = ConfigHandler.phxAutoHealLevel == 1 ? ConfigHandler.phxAutoHealTime : 0;
            target = player;
        }
    }
}
