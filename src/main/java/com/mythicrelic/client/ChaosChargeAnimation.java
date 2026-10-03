package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 暗影龙刃的蓄力姿势：第一人称抬起手、越蓄越抖，第三人称摆出「掷矛」的架势。
 *
 * <p>能蓄力的有两种情况：空手，或者手上拿着一件「右键没被占用、本身带攻击力」的武器
 * （判定见 {@code ChaosAbilities.isChargeableWeapon}）。前者没有 {@code UseAnim} 可借，
 * 后者原版也不会给这种动画，所以两边都靠改渲染姿势——持武器时连武器一起动：</p>
 * <ul>
 *   <li><b>第一人称</b>走 {@link RenderHandEvent}。它给的姿势矩阵还在<b>视角空间</b>
 *       ——原点在摄像机、+X 向右、+Y 向上、-Z 向前，1.0 = 1 格（原版就是在这一层
 *       做 {@code applyItemArmTransform} 的）。</li>
 *   <li><b>第三人称</b>走 {@link RenderPlayerEvent.Pre} 改
 *       {@code HumanoidModel.ArmPose}。Forge 的 Pre 是在
 *       {@code PlayerRenderer.setModelProperties} <b>之后</b>发出的
 *       （javap 确认：偏移 2 调 setModelProperties，偏移 8 才 new Pre），
 *       所以在这里改姿势不会被覆盖，也不必自己还原。</li>
 * </ul>
 *
 * <p><b>释放那一下不在这里</b>：它是 {@code ChaosClientHandler} 里的一句
 * {@code player.swing(InteractionHand.MAIN_HAND)}，直接借原版的打击动画
 * （{@code LocalPlayer.swing} 内部自己会发 SwingPacket，别人也看得见）。
 * 蓄力姿势与它是叠加关系——一松手 {@code chargeRatio()} 归零，
 * 我们这套变换就变回恒等，剩下原版的挥击。</p>
 *
 * <p><b>改了平移/旋转就要同步改 {@code ChaosClientHandler.handPosition()}。</b>
 * 那边的粒子是贴着手心画的，它把这里的变换原样复算了一遍；两边不一致，
 * 手和粒子就会分家。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class ChaosChargeAnimation
{
    /** 蓄满时的抖动幅度（格）。乘 ratio²，所以刚起手几乎不抖、快满时抖得明显。 */
    private static final float CHARGE_SHAKE = 0.035F;
    /** 抖动两个轴的频率（弧度/tick），两个数取不同值，看起来才不像规则振动。 */
    private static final float SHAKE_FREQ_X = 1.9F;
    private static final float SHAKE_FREQ_Y = 2.6F;

    private ChaosChargeAnimation() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        // 只动主手。手上空着是空手击，拿着武器就是连武器一起动——蓄力本来就允许两者。
        if (event.getHand() != InteractionHand.MAIN_HAND)
        {
            return;
        }
        float ratio = ChaosClientHandler.chargeRatio();
        if (ratio <= 0.0F)
        {
            return;
        }

        PoseStack pose = event.getPoseStack();

        // 顺序不能反：先平移后旋转，合成出来才是「先转再移」，
        // 和 handPosition() 里那套复算一致。
        pose.translate(0.0F,
                ChaosClientHandler.CHARGE_LIFT * ratio,
                ChaosClientHandler.CHARGE_PULL_BACK * ratio);
        pose.mulPose(Axis.XP.rotationDegrees(ChaosClientHandler.CHARGE_TILT * ratio));

        // 抖动：越接近蓄满抖得越厉害
        float shake = CHARGE_SHAKE * ratio * ratio;
        LocalPlayer player = Minecraft.getInstance().player;
        float phase = (player == null ? 0.0F : player.tickCount) + event.getPartialTick();
        pose.translate(Mth.sin(phase * SHAKE_FREQ_X) * shake,
                Mth.cos(phase * SHAKE_FREQ_Y) * shake,
                0.0F);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event)
    {
        // 蓄力状态只有本地玩家自己知道，别人的模型不动
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || player != Minecraft.getInstance().player)
        {
            return;
        }
        if (ChaosClientHandler.chargeRatio() <= 0.0F)
        {
            return;
        }

        HumanoidModel<?> model = event.getRenderer().getModel();
        // 主手是哪只手就往哪只胳膊上摆姿势（左手玩家皮肤是镜像的）
        if (player.getMainArm() == HumanoidArm.RIGHT)
        {
            model.rightArmPose = HumanoidModel.ArmPose.THROW_SPEAR;
        }
        else
        {
            model.leftArmPose = HumanoidModel.ArmPose.THROW_SPEAR;
        }
    }
}
