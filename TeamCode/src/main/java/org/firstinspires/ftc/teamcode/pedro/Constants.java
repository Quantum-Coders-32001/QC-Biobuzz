package org.firstinspires.ftc.teamcode.pedro;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.follower.Follower;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * Pedro Pathing 3 entry point. Fills the quickstart stub (which returned null).
 *
 * <p>Verified against revhub 3.0.2 / core 3.0.2 (javap + vendored tuning procedures):
 * Follower(Localizer, Drivetrain, Algorithm); Foresight implements Algorithm;
 * Mecanum(HardwareMap, MecanumConfig); PinpointLocalizer(HardwareMap, PinpointConfig).
 *
 * <p>NO-GUESSING RULE: ConfigVars left unset fall back to Pedro's built-in defaults.
 * Only values we actually know (hardware names, the same direction logic as the teleop)
 * are set. Everything unmeasured is TODO and marked below:
 *
 * <ul>
 *   <li>Drivetrain motor names and Pinpoint name: verified against the Driver Station config
 *       by the user on 2026-10-21 (pinpoint = vendored PinpointTuner default)</li>
 *   <li>Pod type, pod directions and pod offsets: TODO(needed before A: run PinpointTuner)</li>
 *   <li>ALL Foresight gains (translational kP piecewise, coast/brake kV, heading kP,
 *       linear/quadratic brake coefficients, max achievable velocities): default to Pedro
 *       values here; TODO(needed before B: run ForesightTuner, then paste its generated
 *       values over the empty lambda)</li>
 *   <li>manualBrakeMode / powerThreshold: unset (Pedro defaults)</li>
 * </ul>
 */
public class Constants {

    // rgb indicator colors

    public final double red = 0.279;
    public final double orange = 0.317;
    public final double yellow = 0.36;
    public final double green = 0.5;
    public final double blue = 0.61;
    public final double indigo = 0.67;
    public final double violet = 0.7;
    public final double white = 1;




    /** TODO(verify): Pinpoint hardware name. Matches the vendored PinpointTuner default. */
    public static final String PINPOINT_NAME = "pinpoint";

    /**
     * Drivetrain config: same names/direction policy as StaticShooterTeleOp.
     * NOTE: Pedro's Mecanum sets its own directions on the same DcMotorEx objects, so once a
     * follower is actually driving the wheels, the teleop's setPower must not run concurrently
     * with follower.manual/follow (one drive path per loop).
     */
    public static MecanumConfig mecanumConfig() {
        DcMotorSimple.Direction left =
                LEFT_SIDE_REVERSED ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD;
        DcMotorSimple.Direction right =
                LEFT_SIDE_REVERSED ? DcMotorSimple.Direction.FORWARD : DcMotorSimple.Direction.REVERSE;

        return new MecanumConfig(c -> {
            // Names verified against the Driver Station config by the user on 2026-10-21.
            c.frontLeftName.set(LF_NAME);
            c.backLeftName.set(LR_NAME);
            c.frontRightName.set(RF_NAME);
            c.backRightName.set(RR_NAME);
            c.frontLeftDirection.set(left);
            c.backLeftDirection.set(left);
            c.frontRightDirection.set(right);
            c.backRightDirection.set(right);
            // manualBrakeMode, powerThreshold: Pedro defaults (unset) TODO(verify in testing)
        });
    }

    /** Localizer config: all physical values TODO(measure via PinpointTuner). */
    public static PinpointConfig pinpointConfig() {
        return new PinpointConfig(c -> {
            c.name.set(PINPOINT_NAME);
            // podType, ticksPerUnit: Pedro defaults TODO(run PinpointTuner, paste its output);
            // xPodDirection, yPodDirection: Pedro defaults TODO(verify per PinpointTuner);
            // xPodOffset, yPodOffset: Pedro defaults TODO(identify per PinpointTuner);
            c.globalDistanceUnit.set(org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.INCH);
            c.offsetUnits.set(org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.INCH);
        });
    }

    /** Foresight gains: Pedro defaults TODO(run ForesightTuner, paste its generated block). */
    public static ForesightConfig foresightConfig() {
        return new ForesightConfig(c -> {
        });
    }

    /**
     * Follower(Localizer, Drivetrain, Algorithm) — argument order verified against the actual
     * 3.0.2 bytecode and the vendored Tests procedure.
     */
    public static Follower create(HardwareMap h) {
        return new Follower(
                new PinpointLocalizer(h, pinpointConfig()),
                new Mecanum(h, mecanumConfig()),
                new Foresight(foresightConfig())
        );
    }
}
