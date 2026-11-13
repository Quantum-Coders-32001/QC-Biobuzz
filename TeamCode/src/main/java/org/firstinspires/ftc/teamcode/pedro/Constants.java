package org.firstinspires.ftc.teamcode.pedro;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Vector2D;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.pedropathing.tuning.autotune.Procedure;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

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




    public static Follower create(HardwareMap h) {
        return new Follower(
                new PinpointLocalizer(h, localizerConfig),
                new Mecanum(h, drivetrainConfig),
                new Foresight(foresightConfig));
    }
    public static MecanumConfig drivetrainConfig = new MecanumConfig(c -> {
        c.frontLeftName.set("fl");
        c.frontRightName.set("fr");
        c.backLeftName.set("bl");
        c.backRightName.set("br");
        c.frontLeftDirection.set(DcMotorSimple.Direction.FORWARD);
        c.frontRightDirection.set(DcMotorSimple.Direction.REVERSE);
        c.backLeftDirection.set(DcMotorSimple.Direction.FORWARD);
        c.backRightDirection.set(DcMotorSimple.Direction.REVERSE);
    });
    public static PinpointConfig localizerConfig = new PinpointConfig(c -> {
        c.name.set("pinpoint");
        c.podType.set(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
        c.xPodOffset.set(-3.655855974813146);
        c.yPodOffset.set(-1.1988410048597442);
        c.xPodDirection.set(GoBildaPinpointDriver.EncoderDirection.REVERSED);
        c.yPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
        c.globalDistanceUnit.set(DistanceUnit.INCH);
        c.offsetUnits.set(DistanceUnit.INCH);
    });
    public static ForesightConfig foresightConfig = new ForesightConfig(
            c -> {
                Controller primaryTranslationalForward = Controller.proportional(0.2672305367225161);
                Controller secondaryTranslationalForward = Controller.proportional(0.09873454278751945);
                Controller primaryTranslationalLateral = Controller.proportional(0.4527826914460759);
                Controller secondaryTranslationalLateral = Controller.proportional(0.16729110591298693);

                c.forwardTranslational.set(Controller.piecewise(secondaryTranslationalForward).put(2.5, primaryTranslationalForward));
                c.strafeTranslational.set(Controller.piecewise(secondaryTranslationalLateral).put(2.5, primaryTranslationalLateral));

                c.coast.set(Controller.proportionalFeedforward(0.011902639472946626));
                c.brake.set(Controller.proportionalFeedforward(0.010117243552004631));

                c.headingFeedback.set(Controller.proportional(3.3650929874437114));
                c.headingBrakeCoefficients.set(Vector2D.cartesian(0.0419283247785947, 0.007267756168951365));

                c.linearBrakeCoefficients.set(Matrix.diag(0.10879718800641208, 0.07241730810841618));
                c.quadraticBrakeCoefficients.set(Matrix.diag(3.3463482069402434E-4, 0.0012614153895064578));

                c.maxAchievableForwardVelocity.set(85.07713656910924);
                c.maxAchievableStrafeVelocity.set(67.98659679265577);
                c.naturalForwardDeceleration.set(30.137914297569612);
                c.naturalStrafeDeceleration.set(69.910061487522);
            }
    );
}
