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
    private static final double LAUNCHER_TARGET = 1500;
    private static final double VELOCITY_TOLERANCE = 75;
    private static final long FEEDER_DURATION_MS = 700;
    private static final double LAUNCHER_KP = 0.002;
    private static final double LAUNCHER_KF = 0.00044;

    private static final double STICK_DEADBAND = 0.05;

    // TODO(measure): the two mechanical stops of the flower hitter. Measure these with the
    // mechanism at each end of its travel against a hard stop. Until then these are guesses.
    private static final double SERVO_HOME = 0.0;
    private static final double SERVO_FLOWER = 1.0;

    private DcMotorEx frontLeft, frontRight, backLeft, backRight;
    private DcMotorEx launcher;
    private DcMotorEx intakeMotor, feederMotor;
    private Servo flowerServo;

    private Timing.Timer feedTimer;

    // Launcher is armed at match start. The flag is set here but no power is commanded until loop()
    // runs, so init() still leaves every actuator de-energized (G304 / G403).
    private boolean launcherEnabled = true;
    private boolean feederRunning = false;
    private boolean servoAtFlower = false;
    private boolean lastY = false;
    private boolean lastLeftBumper = false;
    private boolean lastRightBumper = false;

    @Override
    public void init() {

        // Drive motors
        frontLeft = hardwareMap.get(DcMotorEx.class, "frontLeft");
        frontRight = hardwareMap.get(DcMotorEx.class, "frontRight");
        backLeft = hardwareMap.get(DcMotorEx.class, "backLeft");
        backRight = hardwareMap.get(DcMotorEx.class, "backRight");

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
        launcher = hardwareMap.get(DcMotorEx.class, "launch");

        launcher.setDirection(DcMotorSimple.Direction.FORWARD);
        launcher.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        launcher.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        // Intake and feeder
        intakeMotor = hardwareMap.get(DcMotorEx.class, "intake");
        feederMotor = hardwareMap.get(DcMotorEx.class, "feeder");

        intakeMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        feederMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        intakeMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        feederMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        // Flower hitter servo. Two fixed stops only; it is never commanded to an intermediate value.
        flowerServo = hardwareMap.get(Servo.class, "flowerServo");
        servoAtFlower = false;
        flowerServo.setPosition(SERVO_HOME);

        // Reset actuator state so a previous OpMode's commands cannot survive into this one.
        // NOTE: DcMotor.stopAndReset() was removed in SDK 12; setPower(0) + setMotorDisable()
        // is the replacement.
        deenergize(frontLeft);
        deenergize(frontRight);
        deenergize(backLeft);
        deenergize(backRight);
        deenergize(launcher);
        deenergize(intakeMotor);
        deenergize(feederMotor);

        feedTimer = new Timing.Timer(FEEDER_DURATION_MS, TimeUnit.MILLISECONDS);

        telemetry.addData("Status", "Ready");
        telemetry.update();
    }

    @Override
    public void loop() {

        // Y is the emergency launcher kill: the launcher runs from the start of the match, and Y is the
        // only way to stop it.
        boolean y = gamepad2.y;
        if (y && !lastY) {
            launcherEnabled = !launcherEnabled;
        }
        lastY = y;

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
        telemetry.addData("Launcher Velocity", String.format("%.0f", launcher.getVelocity()));
        telemetry.addData("At Speed", atLauncherSpeed() ? "YES" : "NO");
        telemetry.addData("Feeder",
                feederActive ? "FIRING"
                        : (feederRunning ? "WAIT SPEED" : "OFF"));
        telemetry.addData("Flower Servo", servoAtFlower ? "FLOWER" : "HOME");
        telemetry.update();
    }

    private void drive() {
        double dy = applyDeadband(-gamepad1.left_stick_y);
        double dx = applyDeadband(gamepad1.left_stick_x);
        double dt = applyDeadband(gamepad1.right_stick_x);

        frontLeft.setPower(dy + dx + dt);
        frontRight.setPower(dy - dx - dt);
        backLeft.setPower(dy - dx + dt);
        backRight.setPower(dy + dx - dt);
    }

    private void runLauncher() {
        if (!launcherEnabled) {
            launcher.setPower(0.0);
            return;
        }

        double error = LAUNCHER_TARGET - launcher.getVelocity();

        double launchPower = clamp(
                LAUNCHER_KP * error + LAUNCHER_KF * LAUNCHER_TARGET,
                0.0, 1.0);

        launcher.setPower(launchPower);
    }

    private boolean atLauncherSpeed() {
        return Math.abs(launcher.getVelocity() - LAUNCHER_TARGET) < VELOCITY_TOLERANCE;
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
        frontLeft.setPower(0);
        frontRight.setPower(0);
        backLeft.setPower(0);
        backRight.setPower(0);

        launcher.setPower(0);

        intakeMotor.setPower(0);
        feederMotor.setPower(0);
    }
}