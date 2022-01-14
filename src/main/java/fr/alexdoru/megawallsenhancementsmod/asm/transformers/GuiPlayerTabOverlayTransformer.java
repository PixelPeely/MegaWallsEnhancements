package fr.alexdoru.megawallsenhancementsmod.asm.transformers;

import fr.alexdoru.megawallsenhancementsmod.asm.ASMLoadingPlugin;
import fr.alexdoru.megawallsenhancementsmod.asm.IMyClassTransformer;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

public class GuiPlayerTabOverlayTransformer implements IMyClassTransformer {

    private static final String PATCHER_CLASS = "club/sk1er/patcher/Patcher.class";

    @Override
    public String getTargetClassName() {
        return "net.minecraft.client.gui.GuiPlayerTabOverlay";
    }

    @Override
    public ClassNode transform(ClassNode classNode) {
        boolean isPatcherLoaded = true;
        try {
            isPatcherLoaded = this.getClass().getClassLoader().getResource(PATCHER_CLASS) != null;
        } catch (Exception ignored) {
        }
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals(ASMLoadingPlugin.isObf ? "a" : "drawScoreboardValues") && methodNode.desc.equals(ASMLoadingPlugin.isObf ? "(Lauk;ILjava/lang/String;IILbdc;)V" : "(Lnet/minecraft/scoreboard/ScoreObjective;ILjava/lang/String;IILnet/minecraft/client/network/NetworkPlayerInfo;)V")) {
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (insnNode.getOpcode() == GETSTATIC
                            && insnNode instanceof FieldInsnNode
                            && ((FieldInsnNode) insnNode).owner.equals(ASMLoadingPlugin.isObf ? "a" : "net/minecraft/util/EnumChatFormatting")
                            && ((FieldInsnNode) insnNode).name.equals(ASMLoadingPlugin.isObf ? "o" : "YELLOW")
                            && ((FieldInsnNode) insnNode).desc.equals(ASMLoadingPlugin.isObf ? "La;" : "Lnet/minecraft/util/EnumChatFormatting;")) {
                        InsnList list = new InsnList();
                        list.add(new VarInsnNode(ILOAD, 7));
                        list.add(new MethodInsnNode(INVOKESTATIC, "fr/alexdoru/megawallsenhancementsmod/asm/hooks/GuiPlayerTabOverlayHook", "getColoredHP", ASMLoadingPlugin.isObf ? "(I)La;" : "(I)Lnet/minecraft/util/EnumChatFormatting;", false));
                        methodNode.instructions.insertBefore(insnNode, list);
                        methodNode.instructions.remove(insnNode);
                        break;
                    }
                }
            }

            if (methodNode.name.equals(ASMLoadingPlugin.isObf ? "a" : "renderPlayerlist") && methodNode.desc.equals(ASMLoadingPlugin.isObf ? "(ILauo;Lauk;)V" : "(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreObjective;)V")) {
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (insnNode.getOpcode() == BIPUSH && insnNode instanceof IntInsnNode && ((IntInsnNode) insnNode).operand == 20) {
                        methodNode.instructions.insertBefore(insnNode, new IntInsnNode(BIPUSH, 25));
                        methodNode.instructions.remove(insnNode);
                    }
                    if (!isPatcherLoaded && insnNode.getOpcode() == BIPUSH && insnNode instanceof IntInsnNode && ((IntInsnNode) insnNode).operand == 80) {
                        methodNode.instructions.insertBefore(insnNode, new IntInsnNode(BIPUSH, 100));
                        methodNode.instructions.remove(insnNode);
                    }
                }
            }
        }
        ASMLoadingPlugin.logger.info("Transformed GuiPlayerTabOverlay");
        return classNode;
    }

}