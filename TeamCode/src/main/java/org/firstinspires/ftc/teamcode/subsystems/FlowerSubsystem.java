package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.seattlesolvers.solverslib.command.SubsystemBase;

/**
 * Flower intake. Does NOT move at init (G304/G403), so the first toggle always goes to DEPLOY.
 * If FLOWER_STOW_POS is still NaN, the second press is ignored (nothing is guessed).
 */
public class FlowerSubsystem extends SubsystemBase {
    private final Servo left, right;
    private boolean deployed = false;

    public FlowerSubsystem(HardwareMap hw) {
        left = hw.get(Servo.class, FLOWER_L_NAME);
        right = hw.get(Servo.class, FLOWER_R_NAME);
        right.setDirection(Servo.Direction.REVERSE);
    }

    public void toggle() {
        if (deployed) {
            if (Double.isNaN(FLOWER_STOW_POS)) return; // stow position not measured yet
            deployed = false;
            set(FLOWER_STOW_POS);
        } else {
            deployed = true;
            set(FLOWER_DEPLOY_POS);
        }
    }

    private void set(double pos) {
        left.setPosition(pos);
        right.setPosition(pos);
    }

    public boolean isDeployed() {
        return deployed;
    }
}
