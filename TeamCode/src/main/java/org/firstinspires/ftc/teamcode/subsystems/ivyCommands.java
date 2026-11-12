package org.firstinspires.ftc.teamcode.subsystems;

import static com.pedropathing.ivy.commands.Commands.infinite;
import static com.pedropathing.ivy.commands.Commands.instant;
import static com.pedropathing.ivy.commands.Commands.waitMs;
import static com.pedropathing.ivy.commands.Commands.waitUntil;
import static com.pedropathing.ivy.groups.Groups.race;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.pedropathing.ivy.Command;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.teamcode.teleop.StaticShooterTeleOp;

public class ivyCommands {

    private final DcMotorEx shooter;
    private final DcMotor lefttransfer;
    private final DcMotor righttransfer;
    private final DcMotor notshooter;
    private final Servo gate;
    private final Servo rflwr;
    private final Servo lflwr;

    public ivyCommands(HardwareMap hw) {
        shooter = hw.get(DcMotorEx.class, SHOOTER_NAME);
        lefttransfer = hw.get(DcMotor.class, TRANSFER_L_NAME);
        righttransfer = hw.get(DcMotor.class, TRANSFER_R_NAME);
        lefttransfer.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        righttransfer.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        lefttransfer.setDirection(DcMotorSimple.Direction.FORWARD);
        righttransfer.setDirection(DcMotorSimple.Direction.REVERSE);
        notshooter = hw.get(DcMotor.class, "notshooter");

        // Same setup as the match teleop: RUN_WITHOUT_ENCODER, because bang-bang runs here in software.
        // Check getVelocity() reads POSITIVE when the wheel spins the shooting way, or the loop below
        // stays "below target" forever and the wheel just runs flat out.
        StaticShooterTeleOp.configureFlywheelMotor(shooter, true);

        gate = hw.get(Servo.class, "block");
        rflwr = hw.get(Servo.class, "rflwr");
        lflwr = hw.get(Servo.class, "lflwr");
    }

    // Bang-bang: full power while the wheel is below target, off once it's there.
    private double bangBang(double velocity) {
        return shooter.getVelocity() < velocity - BANG_DEADBAND_TPS ? BANG_POWER : 0;
    }

    private boolean atSpeed(double velocity) {
        return shooter.getVelocity() >= velocity - BANG_DEADBAND_TPS;
    }

    /** Holds the wheel at speed with bang-bang. NEVER finishes - only use it inside race(). */
    public Command spinUp(double velocity) {
        return infinite(() -> shooter.setPower(bangBang(velocity)))
                .setEnd(endCondition -> shooter.setPower(0));
    }

    public Command shoot(double velocity) {
        return shoot(velocity, 1000);
    }

    public Command shoot(double velocity, double time) {
        return sequential(
                // 1. spin up until the wheel is really at speed, or give up after the timeout
                race(spinUp(velocity),
                        waitUntil(() -> atSpeed(velocity)),
                        waitMs(BANG_SPINUP_TIMEOUT_MS)),

                // 2. feed while bang-bang holds the wheel there
                race(spinUp(velocity),
                        sequential(
                                instant(() -> {
                                    notshooter.setPower(ROLL_POWER);
                                    gate.setPosition(GATE_FIRE_POS);
                                    lefttransfer.setPower(TRANSFER_POWER);
                                    righttransfer.setPower(TRANSFER_POWER);
                                }),

                                waitMs(time),

                                instant(() -> {
                                    gate.setPosition(GATE_REST_POS);
                                    lefttransfer.setPower(0);
                                    righttransfer.setPower(0);
                                })
                        ))
        );
    }

    public Command intake() {
        return intake(1000.0);
    }

    public Command intake(double time) {
        return sequential(
                instant(() -> {
                    lefttransfer.setPower(1);
                    righttransfer.setPower(1);
                }),

                waitMs(time),

                instant(() -> {
                    lefttransfer.setPower(0);
                    righttransfer.setPower(0);
                })
        );
    }

    public Command flower(String status, double durationMs) {
        return sequential(

                instant(() -> {
                    rflwr.setPosition(0);
                    lflwr.setPosition(0);
                }),

                waitMs(durationMs),

                instant(() -> {
                    rflwr.setPosition(1);
                    lflwr.setPosition(1);
                })
        );
    }

    public Command off() {
        return sequential(
                instant(() -> {
                    lefttransfer.setPower(0);
                    righttransfer.setPower(0);
                    notshooter.setPower(0);
                    shooter.setVelocity(0);
                })
        );
    }
}
