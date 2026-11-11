package org.firstinspires.ftc.teamcode.tuners;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.MultipleTelemetry;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.teamcode.teleop.StaticShooterTeleOp;

/**
 * The simple PIDF tuner. Spin the flywheel at one speed, edit the gains live, watch the error.
 *
 * <p>Open the dashboard Configure tab, find {@link G}, set {@code targetTps}, then turn
 * {@code kF} until the wheels sit near the target at steady state, and {@code kP} until they get
 * there fast without overshooting. {@code kI}/{@code kD} stay 0 for a flywheel speed loop.
 *
 * <p>Controls: A (hold) = lt + rt forward at 1.0, X (hold) = lt + rt reverse at 1.0,
 * dpad up/down = target +/-50 tps, B = stop the flywheel. Gains and {@code targetTps} are on the
 * dashboard.
 *
 * <p>The ball path is {@code lt} + {@code rt}; the {@code roll} motor (the team's "notshooter") runs
 * at a constant 0.4 and is never a button here either. Do not wire it into A/X.
 *
 * <p>The control law is the match teleop's own ({@link StaticShooterTeleOp#flywheelPower}), so a
 * gain that looks good here is the gain that flies.
 *
 * <p>UNVERIFIED: no robot yet (AGENTS.md ยง0.8). This is a bench tool - strap the flywheels.
 */
@TeleOp(name = "PIDF Tuner", group = "tuners")
public class PidfTuner extends LinearOpMode {

    /** Live gains. Dashboard-editable; only this tuner reads them, never the match teleop. */
    @Config
    public static class G {
        public static double kP = LAUNCH_KP;
        public static double kI = LAUNCH_KI;
        public static double kD = LAUNCH_KD;
        public static double kF = LAUNCH_KF;
        /** Setpoint in ticks/sec. 0 = wheels stopped, which is why it is the default. */
        public static double targetTps = DEFAULT_LAUNCH_VEL_TPS;
    }

    private static final double TARGET_STEP_TPS = 50.0;

    private DcMotorEx shooter;
    private DcMotorEx notshooter;
    private DcMotor transferL, transferR; // "lt" / "rt" - the ball path, driven by A/X

    private PIDFController pidf;
    private double usedP, usedI, usedD, usedF;

    @Override
    public void runOpMode() {
        telemetry = new MultipleTelemetry(telemetry, FtcDashboard.getInstance().getTelemetry());

        shooter = hardwareMap.get(DcMotorEx.class, "fly");
        shooter.setDirection(DcMotorSimple.Direction.REVERSE);
        notshooter = hardwareMap.get(DcMotorEx.class, "roll");
        transferL = hardwareMap.get(DcMotor.class, "lt");
        transferR = hardwareMap.get(DcMotor.class, "rt");
        StaticShooterTeleOp.configureFlywheelMotor(shooter, false); // same as the match
        rebuildPids();
        notshooter.setPower(0.4);
        transferL.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        transferR.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        transferR.setDirection(DcMotorSimple.Direction.REVERSE);
        telemetry.addLine("A = lt/rt forward 1.0, X = reverse 1.0, "
                + "dpad up/down = target +/-50 tpse, B = stop flywheel.");
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            stopAll();
            return;
        }

        boolean lastUp = false, lastDown = false;
        while (!isStopRequested()) {
            boolean up = gamepad1.dpad_up, down = gamepad1.dpad_down;
            if (up && !lastUp) G.targetTps += TARGET_STEP_TPS;
            if (down && !lastDown) G.targetTps = Math.max(0, G.targetTps - TARGET_STEP_TPS);
            lastUp = up;
            lastDown = down;

            if (gainsChanged()) rebuildPids();

            updateTransfer();

            double target = gamepad1.b ? 0.0 : G.targetTps;
            if (target > 0) {
                shooter.setPower(StaticShooterTeleOp.flywheelPower(pidf, shooter.getVelocity(), target));
            } else {
                stopWheels();
            }

            pushTelemetry(target);
            sleep(20);
        }
        stopAll();
    }

    /** A / X drive {@code lt} and {@code rt} and nothing else, exactly like the match teleop. */
    private void updateTransfer() {
        double power = gamepad1.a ? TRANSFER_POWER
                : gamepad1.x ? -TRANSFER_POWER
                : 0.0;
        transferL.setPower(-power);
        transferR.setPower(-power);
    }

    private void pushTelemetry(double target) {
        double v = shooter.getVelocity();
        telemetry.addData("target tps", "%.0f", target);
        telemetry.addData("vel", v);                 // graph vel against target tps
        telemetry.addData("err", target - v);
        telemetry.addData("kP kI kD kF", "%.6f %.6f %.6f %.6f", G.kP, G.kI, G.kD, G.kF);
        telemetry.addData("Transfer (lt/rt)", gamepad1.a ? "IN 1.0"
                : gamepad1.x ? "REV -1.0" : "0");
        telemetry.addData("Roller", String.format("%.2f", ROLL_POWER));
        if (G.targetTps <= 0) {
            telemetry.addLine("targetTps is 0 - set it on the dashboard or press dpad up.");
        }
        telemetry.update();
    }

    /** A gain change must start from a clean controller: the old integral is not valid for new gains. */
    private boolean gainsChanged() {
        return G.kP != usedP || G.kI != usedI || G.kD != usedD || G.kF != usedF;
    }

    private void rebuildPids() {
        usedP = G.kP;
        usedI = G.kI;
        usedD = G.kD;
        usedF = G.kF;
        pidf = new PIDFController(usedP, usedI, usedD, usedF);
    }

    private void stopWheels() {
        if (shooter != null) shooter.setPower(0);
    }

    /** End of run: every motor this OpMode touched, including the ball path and the roller. */
    private void stopAll() {
        stopWheels();
        if (transferL != null) { transferL.setPower(0); transferR.setPower(0); }
        if (notshooter != null) notshooter.setPower(0);
    }
}