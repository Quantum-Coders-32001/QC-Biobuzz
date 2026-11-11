package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.PIPELINE_RED;
import static org.firstinspires.ftc.teamcode.config.RobotConstants.RED_TAG_IDS;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Red-alliance match teleop: the static shooter plus chassis auto-aim on the red HIVE CELL.
 *
 * <p>Uses Limelight pipeline {@code PIPELINE_RED} (index {@value
 * org.firstinspires.ftc.teamcode.config.RobotConstants#PIPELINE_RED}) and only ever aims at red
 * AprilTags 30-37. All behaviour lives in {@link AutoAimTeleOpBase}; this class is the alliance
 * switch, on purpose, so red and blue cannot drift apart.
 */
@TeleOp(name = "Red Auto-Aim TeleOp", group = "TeleOp")
public class AutoAimRedTeleOp extends AutoAimTeleOpBase {
    @Override
    protected String allianceLabel() {
        return "RED";
    }

    @Override
    protected int pipelineIndex() {
        return PIPELINE_RED;
    }

    @Override
    protected int[] allianceTagIds() {
        return RED_TAG_IDS;
    }
}
