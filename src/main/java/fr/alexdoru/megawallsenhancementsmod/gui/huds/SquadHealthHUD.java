package fr.alexdoru.megawallsenhancementsmod.gui.huds;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.mojang.authlib.GameProfile;
import fr.alexdoru.megawallsenhancementsmod.asm.accessors.NetworkPlayerInfoAccessor;
import fr.alexdoru.megawallsenhancementsmod.asm.hooks.GuiPlayerTabOverlayHook;
import fr.alexdoru.megawallsenhancementsmod.asm.hooks.NetHandlerPlayClientHook;
import fr.alexdoru.megawallsenhancementsmod.config.ConfigHandler;
import fr.alexdoru.megawallsenhancementsmod.features.SquadHandler;
import fr.alexdoru.megawallsenhancementsmod.scoreboard.ScoreboardTracker;
import fr.alexdoru.megawallsenhancementsmod.utils.NameUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldSettings;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class SquadHealthHUD extends AbstractRenderer {

    public static SquadHealthHUD instance;
    private static final Ordering<NetworkPlayerInfo> ordering = Ordering.from(new SquadHealthHUD.PlayerComparator());

    public SquadHealthHUD() {
        super(ConfigHandler.squadHUDPosition);
        instance = this;
    }

    private int getDistance(EntityPlayer p1, EntityPlayer p2) {
        return (int)(Math.abs(p1.posX - p2.posX) + /*Math.abs(p1.posY - p2.posY)*/ + Math.abs(p1.posZ - p2.posZ));
    }

    private void drawArrow(int x, int y, float direction) {
        int xDisplacement = (int)(Math.cos(direction) * 4);
        int yDisplacement = (int)(Math.sin(direction) * 4);
        drawLine(x - xDisplacement, y - yDisplacement, x + xDisplacement, y + yDisplacement, 0xFFFF0000);
        Gui.drawRect(x + xDisplacement, y + yDisplacement, x + xDisplacement + 1, y + yDisplacement - 1, 0xFFFFFFFF);
    }

    private void drawLine(int x, int y, int dx, int dy, int color) {
        if (x == dx) Gui.drawRect(x, Math.max(y, dy), x+1, Math.min(y, dy), color);
        float step = ((float)(dy-y))/(dx-x);
        int lastY = y;
        Gui.drawRect(x, y, x+1, y-1, color);
        for (int ix = x; dx<x? ix >= dx : ix <= dx; ix += dx<x?-1:1) {
            int currentY = (int)((ix-x)*step)+y;
            Gui.drawRect(ix, Math.max(lastY, currentY), ix+1, Math.min(lastY, currentY)-1, color);
            lastY = currentY;
        }
    }

    @Override
    public void render(ScaledResolution resolution) {
        final Scoreboard scoreboard = mc.theWorld.getScoreboard();
        final ScoreObjective scoreobjective = scoreboard.getObjectiveInDisplaySlot(0);
        if (mc.isIntegratedServerRunning() && scoreobjective == null) {
            return;
        }
        final List<NetworkPlayerInfo> list = new ArrayList<>();
        for (final String squadmateName : SquadHandler.getSquad().keySet()) {
            final NetworkPlayerInfo networkPlayerInfo = NetHandlerPlayClientHook.getPlayerInfo(squadmateName);
            if (networkPlayerInfo != null) {
                list.add(networkPlayerInfo);
            }
        }
        if (list.size() <= 1) {
            return;
        }
        final List<NetworkPlayerInfo> playerlistToRender = ordering.sortedCopy(list);
        int maxNameWidth = 0;
        int maxScoreWidth = 0;
        int maxFinalWidth = 0;
        int maxDirectionWidth = 0;
        int maxYOffsetWidth = 0;
        for (final NetworkPlayerInfo networkplayerinfo : playerlistToRender) {
            EntityPlayer player = mc.theWorld.getPlayerEntityByUUID(networkplayerinfo.getGameProfile().getId());
            maxNameWidth = Math.max(maxNameWidth, mc.fontRendererObj.getStringWidth(this.getPlayerName(networkplayerinfo)));
            if (scoreobjective != null && scoreobjective.getRenderType() != IScoreObjectiveCriteria.EnumRenderType.HEARTS) {
                maxScoreWidth = Math.max(maxScoreWidth, mc.fontRendererObj.getStringWidth(" " + scoreboard.getValueFromObjective(networkplayerinfo.getGameProfile().getName(), scoreobjective).getScorePoints()));
            }
            if (ScoreboardTracker.isInMwGame) {
                final int playerFinalkills = ((NetworkPlayerInfoAccessor) networkplayerinfo).getPlayerFinalkills();
                if (playerFinalkills != 0) {
                    maxFinalWidth = Math.max(maxFinalWidth, mc.fontRendererObj.getStringWidth(" " + playerFinalkills));
                }
            }
            if (player != mc.thePlayer && player != null) {
                final int playerDistance = Math.round(getDistance(player, mc.thePlayer));
                maxDirectionWidth = Math.max(maxDirectionWidth, mc.fontRendererObj.getStringWidth(" ( " + playerDistance));
                final int yOffset = (int)(player.posY - mc.thePlayer.posY);
                maxYOffsetWidth = Math.max(maxYOffsetWidth, mc.fontRendererObj.getStringWidth(yOffset + " )"));
            }
        }
        GlStateManager.pushMatrix();
        {
            final boolean flag = mc.isIntegratedServerRunning() || mc.getNetHandler().getNetworkManager().getIsencrypted();
            final int maxLineWidth = (flag ? 9 : 0) + maxNameWidth + maxFinalWidth + maxScoreWidth + maxDirectionWidth + maxYOffsetWidth + 9;
            final int listSize = playerlistToRender.size();
            final int hudWidth = maxLineWidth + 2;
            final int hudHight = listSize * 9 + 1;
            this.guiPosition.updateAdjustedAbsolutePosition(resolution, hudWidth, hudHight);
            final int hudXpos = this.guiPosition.getAbsoluteRenderX();
            final int hudYpos = this.guiPosition.getAbsoluteRenderY();
            Gui.drawRect(hudXpos, hudYpos, hudXpos + hudWidth, hudYpos + hudHight, Integer.MIN_VALUE);
            for (int i = 0; i < listSize; i++) {
                int xDrawingPos = hudXpos + 1;
                final int yDrawingPos = hudYpos + 1 + i * 9;
                Gui.drawRect(xDrawingPos, yDrawingPos, hudXpos + maxLineWidth + 1, yDrawingPos + 8, 0x20FFFFFF);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                final NetworkPlayerInfo networkplayerinfo = playerlistToRender.get(i);
                final GameProfile gameprofile = networkplayerinfo.getGameProfile();
                if (flag) {
                    final EntityPlayer entityplayer = mc.theWorld.getPlayerEntityByUUID(gameprofile.getId());
                    mc.getTextureManager().bindTexture(networkplayerinfo.getLocationSkin());
                    Gui.drawScaledCustomSizeModalRect(xDrawingPos, yDrawingPos, 8, 8, 8, 8, 8, 8, 64.0F, 64.0F);
                    if (entityplayer == null || entityplayer.isWearing(EnumPlayerModelParts.HAT)) {
                        Gui.drawScaledCustomSizeModalRect(xDrawingPos, yDrawingPos, 40, 8, 8, 8, 8, 8, 64.0F, 64.0F);
                    }
                    xDrawingPos += 9;
                }
                mc.fontRendererObj.drawStringWithShadow(this.getPlayerName(networkplayerinfo), (float) xDrawingPos, (float) yDrawingPos, 0xFFFFFF);
                final int xStartFinalDrawingPos = xDrawingPos + maxNameWidth + 1;
                final int xStartScoreDrawingPos = xStartFinalDrawingPos + maxFinalWidth;
                final int xStartDirectionDrawingPos = xStartScoreDrawingPos + maxScoreWidth;
                final int xStartYOffsetDrawingPos = xStartDirectionDrawingPos + maxDirectionWidth;
                if (maxScoreWidth + maxFinalWidth > 5) {
                    if (scoreobjective != null && networkplayerinfo.getGameType() != WorldSettings.GameType.SPECTATOR && scoreobjective.getRenderType() != IScoreObjectiveCriteria.EnumRenderType.HEARTS) {
                        final int scorePoints = scoreobjective.getScoreboard().getValueFromObjective(gameprofile.getName(), scoreobjective).getScorePoints();
                        final String scoreString = GuiPlayerTabOverlayHook.getColoredHP(scorePoints) + " " + scorePoints;
                        mc.fontRendererObj.drawStringWithShadow(scoreString, xStartScoreDrawingPos, yDrawingPos, 0xFFFFFF);
                    }
                    if (ScoreboardTracker.isInMwGame) {
                        final int playersFinals = ((NetworkPlayerInfoAccessor) networkplayerinfo).getPlayerFinalkills();
                        if (playersFinals != 0) {
                            final String finalsString = EnumChatFormatting.GOLD + " " + playersFinals;
                            mc.fontRendererObj.drawStringWithShadow(finalsString, xStartFinalDrawingPos, yDrawingPos, 0xFFFFFF);
                        }
                    }
                }
                EntityPlayer player = mc.theWorld.getPlayerEntityByUUID(networkplayerinfo.getGameProfile().getId());

                if (player != mc.thePlayer && player != null && !ScoreboardTracker.isPreGameLobby) {
                    final int distance = getDistance(mc.thePlayer, player);
                    final String directionString = " (" + EnumChatFormatting.LIGHT_PURPLE + distance;
                    final String yOffsetString = EnumChatFormatting.GRAY.toString() + Math.round(player.posY - mc.thePlayer.posY) + ")";
                    mc.fontRendererObj.drawStringWithShadow(directionString, xStartDirectionDrawingPos, yDrawingPos, 0xFFFFFF);
                    mc.fontRendererObj.drawStringWithShadow(yOffsetString, xStartYOffsetDrawingPos + mc.fontRendererObj.getStringWidth("  "), yDrawingPos, 0xFFFFFF);
                    drawArrow(xStartYOffsetDrawingPos + mc.fontRendererObj.getCharWidth(' ') / 2, yDrawingPos + 9 / 2, (float) (Math.atan2(mc.thePlayer.posZ - player.posZ, mc.thePlayer.posX - player.posX) - Math.toRadians(mc.thePlayer.rotationYawHead)));
                }
            }
        }
        GlStateManager.popMatrix();
    }

    private static final Pattern squadSuffixPattern = Pattern.compile("^" + EnumChatFormatting.GOLD + "\\[" + EnumChatFormatting.DARK_GREEN + "S" + EnumChatFormatting.GOLD + "\\] ");
    private String getPlayerName(NetworkPlayerInfo networkPlayerInfoIn) {
        final String formattedName = NameUtil.getFormattedName(networkPlayerInfoIn);
        return squadSuffixPattern.matcher(formattedName).replaceFirst("");
    }

    @Override
    public void renderDummy() {
        GlStateManager.pushMatrix();
        {
            final int hudXpos = this.guiPosition.getAbsoluteRenderX();
            final int hudYpos = this.guiPosition.getAbsoluteRenderY();
            final int listSize = 4;
            final int maxNameWidth = mc.fontRendererObj.getStringWidth(mc.thePlayer.getName());
            final int maxScoreWidth = mc.fontRendererObj.getStringWidth(" 00");
            final int maxFinalWidth = mc.fontRendererObj.getStringWidth(" 0");
            final String direction1 = " (" + EnumChatFormatting.LIGHT_PURPLE + "13 ";
            final String direction2 = " " + EnumChatFormatting.GRAY + " -3" + EnumChatFormatting.RESET + ")";
            final int direction1Width = mc.fontRendererObj.getStringWidth(direction1);
            final int direction2Width = mc.fontRendererObj.getStringWidth(direction2);
            final int maxLineWidth = maxNameWidth + maxFinalWidth + maxScoreWidth + direction1Width + direction2Width;
            Gui.drawRect(hudXpos, hudYpos, hudXpos + maxLineWidth + 2, hudYpos + listSize * 9 + 1, Integer.MIN_VALUE);
            for (int i = 0; i < listSize; i++) {
                int xDrawingPos = hudXpos + 1;
                final int yDrawingPos = hudYpos + 1 + i * 9;
                Gui.drawRect(xDrawingPos, yDrawingPos, hudXpos + maxLineWidth + 1, yDrawingPos + 8, 0x20FFFFFF);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                mc.getTextureManager().bindTexture(DefaultPlayerSkin.getDefaultSkinLegacy());
                Gui.drawScaledCustomSizeModalRect(xDrawingPos, yDrawingPos, 8, 8, 8, 8, 8, 8, 64.0F, 64.0F);
                Gui.drawScaledCustomSizeModalRect(xDrawingPos, yDrawingPos, 40, 8, 8, 8, 8, 8, 64.0F, 64.0F);
                xDrawingPos += 9;
                final String formattedName = EnumChatFormatting.GREEN + mc.thePlayer.getName();
                mc.fontRendererObj.drawStringWithShadow(formattedName, (float) xDrawingPos, (float) yDrawingPos, -1);
                final int xStartFinalDrawingPos = xDrawingPos + maxNameWidth + 1;
                final int xStartScoreDrawingPos = xStartFinalDrawingPos + maxFinalWidth;
                final int xStartDirection1DrawingPos = xStartScoreDrawingPos + direction1Width;
                final int xStartDirection2DrawingPos = xStartDirection1DrawingPos + direction2Width;
                if (maxScoreWidth + maxFinalWidth > 5) {
                    final int scorePoints = 12 + (i * 18 + 22) % 8;
                    final String scoreString = GuiPlayerTabOverlayHook.getColoredHP(scorePoints) + " " + scorePoints;
                    mc.fontRendererObj.drawStringWithShadow(scoreString, xStartScoreDrawingPos, yDrawingPos, 0xFFFFFF);
                    final String finalsString = EnumChatFormatting.GOLD + " " + (3 + (i * 28 + 15) % 5);
                    mc.fontRendererObj.drawStringWithShadow(finalsString, xStartFinalDrawingPos, yDrawingPos, 0xFFFFFF);
                    mc.fontRendererObj.drawStringWithShadow(direction1 + direction2, xStartDirection1DrawingPos, yDrawingPos, 0xFFFFFF);
                    drawArrow(xStartDirection2DrawingPos + mc.fontRendererObj.getCharWidth(' ') / 2, yDrawingPos + 9/2, (float)(mc.theWorld.getWorldTime() * Math.PI * 0.01) + i);
                    //drawArrow(80, 80, (float)(Math.atan2(mc.thePlayer.posZ - 0, mc.thePlayer.posX - 0) - Math.toRadians(mc.thePlayer.rotationYawHead)));
                }
            }
        }
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isEnabled(long currentTimeMillis) {
        return ConfigHandler.showSquadHUD && SquadHandler.getSquad().size() > 1;
    }

    private static class PlayerComparator implements Comparator<NetworkPlayerInfo> {
        private PlayerComparator() {}
        @Override
        public int compare(NetworkPlayerInfo p_compare_1_, NetworkPlayerInfo p_compare_2_) {
            final ScorePlayerTeam scoreplayerteam = p_compare_1_.getPlayerTeam();
            final ScorePlayerTeam scoreplayerteam1 = p_compare_2_.getPlayerTeam();
            return ComparisonChain.start()
                    .compareTrueFirst(p_compare_1_.getGameType() != WorldSettings.GameType.SPECTATOR, p_compare_2_.getGameType() != WorldSettings.GameType.SPECTATOR)
                    .compare(scoreplayerteam != null ? scoreplayerteam.getRegisteredName() : "", scoreplayerteam1 != null ? scoreplayerteam1.getRegisteredName() : "")
                    .compare(p_compare_1_.getGameProfile().getName(), p_compare_2_.getGameProfile().getName())
                    .result();
        }
    }

}
