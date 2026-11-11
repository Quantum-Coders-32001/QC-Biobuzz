package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.BLUE_TAG_IDS;
import static org.firstinspires.ftc.teamcode.config.RobotConstants.PIPELINE_BLUE;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/**
 * Blue-alliance match teleop: the static shooter plus chassis auto-aim on the blue HIVE CELL.
 *
 * <p>Uses Limelight pipeline {@code PIPELINE_BLUE} (index {@value
 * org.firstinspires.ftc.teamcode.config.RobotConstants#PIPELINE_BLUE}) and only ever aims at blue
 * AprilTags 38-45. All behaviour lives in {@link AutoAimTeleOpBase}; this class is the alliance
 * switch, on purpose, so red and blue cannot drift apart.
 */
@TeleOp(name = "Blue Auto-Aim TeleOp", group = "TeleOp")
public class AutoAimBlueTeleOp extends AutoAimTeleOpBase {
    @Override
    protected String allianceLabel() {
        return "BLUE";
    }

    @Override
    protected int pipelineIndex() {
        return PIPELINE_BLUE;
    }

    @Override
    protected int[] allianceTagIds() {
        return BLUE_TAG_IDS;
    }
}
