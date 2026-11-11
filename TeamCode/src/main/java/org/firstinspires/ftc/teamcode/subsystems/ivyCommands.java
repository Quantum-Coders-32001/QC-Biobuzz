package org.firstinspires.ftc.teamcode.subsystems;

import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.commands.Commands.waitUntil;

import com.pedropathing.ivy.Command;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

public class ivyCommands {
    private final DcMotorEx shooter;
    private final DcMotor lefttransfer;
    private final DcMotor righttransfer;
    private final DcMotor notshooter;
    private final Servo gate;


    public ivyCommands(HardwareMap hw) {
        shooter = hw.get(DcMotorEx.class, "shooter");
        lefttransfer = hw.get(DcMotor.class, "lt");
        righttransfer = hw.get(DcMotor.class, "rt");
        notshooter = hw.get(DcMotor.class, "notshooter");
        shooter.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        gate = hw.get(Servo.class, "gate");
    }

    public Command spinUp(double velocity) {
        return instant(() -> shooter.setVelocity(velocity));
    }

    public Command shoot() {
        return Command.build()
        .setStart(() -> {
            gate.setPosition(0.55);
        })
        .setExecute(() -> {
            lefttransfer.setPower(1);
            righttransfer.setPower(1);
        })
        .setEnd(endCondition -> {
            gate.setPosition(0.75);
            lefttransfer.setPower(0);
            righttransfer.setPower(0);
        });

    }

   /* public static Command shoot() {
        return Command.build()
                .setStart(() -> shooter.feed())
                .setDone(() -> shooter.isEmpty())   // real sensor/state, not a guess
                .setEnd(interrupted -> shooter.stopFeed())
                .requiring(shooter);
    }

    /*public static Command shoot {
        return Command.build()
                .setExecute(() -> flywheel.setTargetPower(0.85))
                .setDone(flywheel::atTargetVelocity)
                .setEnd((i) -> flywheel.setTargetPower(0))
                .build();
    }

     /* public static Command feedIntake(IntakeSubsystem intake, int count) {
        return Command.build()
                .setExecute(() -> intake.feed())
                .setDone(() -> intake.getBallsFed() >= count)
                .setEnd((i) -> intake.stop())
                .requiring(intake)
                .build();
    }

    /* public static Command waitForFlywheel(FlywheelSubsystem flywheel) {
        return waitUntil(flywheel::atTargetVelocity);  // Ivy built-in
    }

    // one-shot actions
    public static Command setFlywheelPower(FlywheelSubsystem flywheel, double power) {
        return instant(() -> flywheel.setTargetPower(power));
    }
    */


}
