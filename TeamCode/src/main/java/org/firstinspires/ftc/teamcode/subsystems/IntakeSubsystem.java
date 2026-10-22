package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.seattlesolvers.solverslib.command.SubsystemBase;

/** Intake + transfer: one mechanism, two motors. */
public class IntakeSubsystem extends SubsystemBase {
    private final DcMotorEx motorR, motorL;

    public IntakeSubsystem(HardwareMap hw) {
        motorR = hw.get(DcMotorEx.class, INTAKE_R_NAME);
        motorL = hw.get(DcMotorEx.class, INTAKE_L_NAME);
        motorR.setDirection(DcMotorSimple.Direction.REVERSE);
        motorR.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        motorL.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }

    public void setPower(double power) {
        motorR.setPower(power);
        motorL.setPower(power);
    }

    public void stop() {
        setPower(0);
    }
}
