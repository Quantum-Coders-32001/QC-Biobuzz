package org.firstinspires.ftc.teamcode.teleop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;

import com.seattlesolvers.solverslib.util.Timing;

import java.util.concurrent.TimeUnit;

@TeleOp(name = "BioBuzz Teleop", group = "TeleOp")
public class BioBuzzTeleop extends OpMode {

    // TODO(tune): these were set for a two-motor launcher. Re-tune against the single "launch"
    //  motor, or the velocity loop may never reach VELOCITY_TOLERANCE and the feeder will
    //  never start.
    private static final double LAUNCHER_TARGET_DEFAULT = 1800;

    // RT / LT adjust the target during a match. The driver is responsible for setting a sane
    // value; these bounds only stop a held trigger from running away.
    private static final double LAUNCHER_VELOCITY_MIN = 0.0;
    private static final double LAUNCHER_VELOCITY_MAX = 3000.0;
    private static final double LAUNCHER_VELOCITY_STEP = 100.0;

    private static final double VELOCITY_TOLERANCE = 75;
    private static final long FEEDER_DURATION_MS = 700;
    private static final double LAUNCHER_KP = 0.002;
    private static final double LAUNCHER_KF = 0.00044;

    private static final double STICK_DEADBAND = 0.05;

    // TODO(measure): the two mechanical stops of the flower hitter. Measure these with the
    // mechanism at each end of its travel against a hard stop. Until then these are guesses.
    private static final double SERVO_HOME = 1;
    private static final double SERVO_FLOWER = 0;

    private DcMotorEx frontLeft, frontRight, backLeft, backRight;
    private DcMotorEx launcher;
    private DcMotorEx intakeMotor, feederMotor;
    private DcMotorEx[] allMotors; // assigned in init() once every motor has been fetched
    private Servo flowerServo;

    private Timing.Timer feedTimer;

    // Launcher is armed at match start. The flag is set here but no power is commanded until loop()
    // runs, so init() still leaves every actuator de-energized (G304 / G403).
    private boolean launcherEnabled = true;
    private double launcherTargetVelocity = LAUNCHER_TARGET_DEFAULT;
    private boolean feederRunning = false;
    private boolean servoAtFlower = false;
    private boolean lastB = false;
    private boolean lastLeftBumper = false;
    private boolean lastRightBumper = false;
    private boolean lastDpadLeft = false;
    private boolean lastDpadRight = false;

    @Override
    public void init() {

        // Drive motors
        frontLeft = hardwareMap.get(DcMotorEx.class, "fl");
        frontRight = hardwareMap.get(DcMotorEx.class, "fr");
        backLeft = hardwareMap.get(DcMotorEx.class, "bl");
        backRight = hardwareMap.get(DcMotorEx.class, "br");

        frontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        backLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        frontRight.setDirection(DcMotorSimple.Direction.FORWARD);
        backRight.setDirection(DcMotorSimple.Direction.FORWARD);

        frontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        frontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        frontLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        frontRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        backLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        backRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        // Launcher motor
        launcher = hardwareMap.get(DcMotorEx.class, "l");

        launcher.setDirection(DcMotorSimple.Direction.FORWARD);
        launcher.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        launcher.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        // Intake and feeder
        intakeMotor = hardwareMap.get(DcMotorEx.class, "i");
        feederMotor = hardwareMap.get(DcMotorEx.class, "f");

        intakeMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        feederMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        intakeMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        feederMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        allMotors = new DcMotorEx[]{
                frontLeft, frontRight, backLeft, backRight,
                launcher, intakeMotor, feederMotor
        };

        // Flower hitter servo. Two fixed stops only; it is never commanded to an intermediate value.
        flowerServo = hardwareMap.get(Servo.class, "s");
        servoAtFlower = false;
        flowerServo.setPosition(SERVO_HOME);

        // Reset actuator state so a previous OpMode's commands cannot survive into this one.
        // setMotorDisable() de-energizes the port and it stays that way until setMotorEnable();
        // start() re-enables every motor.
        for (DcMotorEx motor : allMotors) {
            deenergize(motor);
        }

        feedTimer = new Timing.Timer(FEEDER_DURATION_MS, TimeUnit.MILLISECONDS);

        telemetry.addData("Status", "Ready");
        telemetry.update();
    }

    @Override
    public void start() {
        // Undo init()'s setMotorDisable(). Without this the motors would stay de-energized.
        for (DcMotorEx motor : allMotors) {
            motor.setMotorEnable();
        }
    }

    @Override
    public void loop() {

        // B toggles the launcher. It runs from the start of the match; B is the only way to stop it.
        boolean b = gamepad2.b;
        if (b && !lastB) {
            launcherEnabled = !launcherEnabled;
        }
        lastB = b;

        // D-pad right raises the launcher target, D-pad left lowers it, one step per press.
        boolean dpadRight = gamepad2.dpad_right;
        if (dpadRight && !lastDpadRight) {
            launcherTargetVelocity = clamp(
                    launcherTargetVelocity + LAUNCHER_VELOCITY_STEP,
                    LAUNCHER_VELOCITY_MIN,
                    LAUNCHER_VELOCITY_MAX);
        }
        lastDpadRight = dpadRight;

        boolean dpadLeft = gamepad2.dpad_left;
        if (dpadLeft && !lastDpadLeft) {
            launcherTargetVelocity = clamp(
                    launcherTargetVelocity - LAUNCHER_VELOCITY_STEP,
                    LAUNCHER_VELOCITY_MIN,
                    LAUNCHER_VELOCITY_MAX);
        }
        lastDpadLeft = dpadLeft;

        // Right bumper toggles the flower hitter between its home and flower stops.
        boolean rightBumper = gamepad2.right_bumper;
        if (rightBumper && !lastRightBumper) {
            servoAtFlower = !servoAtFlower;
            flowerServo.setPosition(servoAtFlower ? SERVO_FLOWER : SERVO_HOME);
        }
        lastRightBumper = rightBumper;

        drive();
        runLauncher();
        boolean feederActive = runFeeder();
        runIntake();

        // Telemetry
        telemetry.addData("Launcher", launcherEnabled ? "ENABLED" : "OFF");
        telemetry.addData("Target Velocity", String.format("%.0f", launcherTargetVelocity));
        telemetry.addData("Launcher Velocity", String.format("%.0f", launcher.getVelocity()));
        telemetry.addData("At Speed", atLauncherSpeed() ? "YES" : "NO");
        telemetry.addData("Feeder",
                feederActive ? "FIRING"
                        : (feederRunning ? "WAIT SPEED" : "OFF"));
        telemetry.addData("Flower Servo", servoAtFlower ? "FLOWER" : "HOME");
        telemetry.update();
    }

    /**
     * Robot-centric mecanum drive (CTRL ALT FTC "Drivetrain Control" mixing).
     *
     * <p>Axis convention is the one the page's mixing implies: x = forward, y = strafe right,
     * t = turn clockwise. The page never states this, and the old code fed stick-X into x and
     * stick-Y into y, which swaps forward and strafe against this mixing.
     */
    private void drive() {
        double x = applyDeadband(-gamepad1.left_stick_y);  // forward
        double y = applyDeadband(gamepad1.left_stick_x);   // strafe right
        double t = applyDeadband(gamepad1.right_stick_x);  // turn clockwise

        // The page does not normalize. Without this, one wheel clips at 1.0 while the others
        // don't, which bends the direction of travel on full-stick diagonals / turns.
        double denominator = Math.max(Math.abs(x) + Math.abs(y) + Math.abs(t), 1.0);

        // x, y, theta input mixing
        frontLeft.setPower((x + y + t) / denominator);
        backLeft.setPower((x - y + t) / denominator);
        frontRight.setPower((x - y - t) / denominator);
        backRight.setPower((x + y - t) / denominator);
    }

    private void runLauncher() {
        if (!launcherEnabled) {
            launcher.setPower(0.0);
            return;
        }

        double error = launcherTargetVelocity - launcher.getVelocity();

        double launchPower = clamp(
                LAUNCHER_KP * error + LAUNCHER_KF * launcherTargetVelocity,
                0.0, 1.0);

        launcher.setPower(launchPower);
    }

    private boolean atLauncherSpeed() {
        return Math.abs(launcher.getVelocity() - launcherTargetVelocity) < VELOCITY_TOLERANCE;
    }

    /**
     * Runs one feed cycle, triggered by the left bumper. Returns true only while the feeder is
     * actually turning.
     *
     * <p>A press is ignored while a cycle is already in progress, so retapping the button cannot
     * stack cycles or restart the timer.
     */
    private boolean runFeeder() {
        boolean leftBumper = gamepad2.left_bumper;
        boolean shootPressed = leftBumper && !lastLeftBumper;
        lastLeftBumper = leftBumper;

        boolean feederActive = feederRunning && feedTimer.isTimerOn();

        if (shootPressed && !feederActive) {
            feederRunning = true;
            feedTimer.start();
        }

        if (!feederRunning) {
            feederMotor.setPower(0.0);
            return false;
        }

        if (feederActive && !feedTimer.done()) {
            feederMotor.setPower(1.0);
            return true;
        }

        if (feederActive) {
            // Cycle finished. Cancel before falling through so the next shot starts clean.
            feedTimer.pause();
            feederRunning = false;
        }

        // Requested but blocked on launcher speed. Feeder stays stopped; the driver has to shoot
        // again once atSpeed comes up.
        feederMotor.setPower(0.0);
        return false;
    }

    /** A runs the intake forward, X runs it backward. Nothing else touches the intake. */
    private void runIntake() {
        boolean a = gamepad2.a;
        boolean x = gamepad2.x;

        if (a) {
            intakeMotor.setPower(1.0);
        } else if (x) {
            intakeMotor.setPower(-1.0);
        } else {
            intakeMotor.setPower(0.0);
        }
    }

    private static void deenergize(DcMotorEx motor) {
        motor.setPower(0.0);
        motor.setMotorDisable();
    }

    private static double applyDeadband(double value) {
        if (Math.abs(value) < STICK_DEADBAND) {
            return 0.0;
        }
        double scaled = (value - Math.signum(value) * STICK_DEADBAND) / (1.0 - STICK_DEADBAND);
        return clamp(scaled, -1.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void stop() {
        // init() may have thrown before the motors were fetched; don't stack an NPE on top of it.
        if (allMotors == null) {
            return;
        }
        for (DcMotorEx motor : allMotors) {
            motor.setPower(0);
            // If the OpMode was stopped from INIT, start() never ran. Don't leave ports disabled
            // for whatever OpMode runs next.
            motor.setMotorEnable();
        }
    }
}