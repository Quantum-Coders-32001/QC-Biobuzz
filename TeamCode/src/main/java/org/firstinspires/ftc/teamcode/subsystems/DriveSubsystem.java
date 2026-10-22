package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.seattlesolvers.solverslib.command.SubsystemBase;

public class DriveSubsystem extends SubsystemBase {
    private final DcMotorEx lf, lr, rf, rr;

    public DriveSubsystem(HardwareMap hw) {
        lf = hw.get(DcMotorEx.class, LF_NAME);
        lr = hw.get(DcMotorEx.class, LR_NAME);
        rf = hw.get(DcMotorEx.class, RF_NAME);
        rr = hw.get(DcMotorEx.class, RR_NAME);

        DcMotorSimple.Direction left = LEFT_SIDE_REVERSED ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD;
        DcMotorSimple.Direction right = LEFT_SIDE_REVERSED ? DcMotorSimple.Direction.FORWARD : DcMotorSimple.Direction.REVERSE;
        lf.setDirection(left);
        lr.setDirection(left);
        rf.setDirection(right);
        rr.setDirection(right);

        for (DcMotorEx m : new DcMotorEx[]{lf, lr, rf, rr}) {
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }
    }

    /** Robot-centric. forward: +ahead, strafe: +right, turn: +clockwise. Nothing is powered until this is called. */
    public void drive(double forward, double strafe, double turn) {
        double lfP = forward + strafe + turn;
        double lrP = forward - strafe + turn;
        double rfP = forward - strafe - turn;
        double rrP = forward + strafe - turn;

        double max = Math.max(1.0, Math.max(Math.max(Math.abs(lfP), Math.abs(lrP)),
                Math.max(Math.abs(rfP), Math.abs(rrP))));

        lf.setPower(lfP / max);
        lr.setPower(lrP / max);
        rf.setPower(rfP / max);
        rr.setPower(rrP / max);
    }

    public void stop() {
        drive(0, 0, 0);
    }
}
