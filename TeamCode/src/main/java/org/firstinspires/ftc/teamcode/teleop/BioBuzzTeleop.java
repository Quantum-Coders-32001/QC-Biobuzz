
package org.firstinspires.ftc.teamcode.teleop;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;

import java.util.List;

@TeleOp(name = "BioBuzz Teleop", group = "TeleOp")
public class BioBuzzTeleop extends OpMode {

    private static final double LAUNCHER_TARGET = 1500;
    private static final double VELOCITY_TOLERANCE = 75;
    private static final long FEEDER_DURATION_MS = 700;
    private static final double LAUNCHER_KP = 0.002;
    private static final double LAUNCHER_KF = 0.00044;

    private DcMotorEx frontLeft, frontRight, backLeft, backRight;
    private DcMotorEx launcherRight, launcherLeft;
    private DcMotorEx intakeMotor, feederMotor;
    private Servo aimServo;
    private Limelight3A limelight;

    private boolean feederRunning = false;
    private long feederStartTime = 0;
    private boolean limelightMode = false;
    private boolean lastX = false;
    private boolean lastA = false;

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

        // Launcher motors
        launcherRight = hardwareMap.get(DcMotorEx.class, "launch");
        launcherLeft = hardwareMap.get(DcMotorEx.class, "launch1");

        launcherRight.setDirection(DcMotorSimple.Direction.FORWARD);
        launcherLeft.setDirection(DcMotorSimple.Direction.REVERSE);

        launcherRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        launcherLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        launcherRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        launcherLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        // Intake and feeder
        intakeMotor = hardwareMap.get(DcMotorEx.class, "intake");
        feederMotor = hardwareMap.get(DcMotorEx.class, "feeder");

        intakeMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        feederMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        intakeMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        feederMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        // Aiming servo
        aimServo = hardwareMap.get(Servo.class, "aimServo");

        // Limelight
        limelight = hardwareMap.get(Limelight3A.class, "limelight");
        limelight.pipelineSwitch(0);
        limelight.start();

        telemetry.addData("Status", "Ready");
        telemetry.update();
    }

    @Override
    public void loop() {

        // Drive controls
        double dy = -gamepad1.left_stick_y;
        double dx = gamepad1.left_stick_x;
        double dt = gamepad1.right_stick_x;

        if (!limelightMode) {
            frontLeft.setPower(dy + dx + dt);
            frontRight.setPower(dy - dx - dt);
            backLeft.setPower(dy - dx + dt);
            backRight.setPower(dy + dx - dt);
        }

        // X button toggles Limelight mode
        boolean x = gamepad1.x;

        if (x && !lastX) {
            limelightMode = !limelightMode;

            if (!limelightMode) {
                frontLeft.setPower(0);
                frontRight.setPower(0);
                backLeft.setPower(0);
                backRight.setPower(0);
            }
        }

        lastX = x;

        // Limelight aiming mode
        if (limelightMode) {

            aimServo.setPosition(1.0);

            LLResult result = limelight.getLatestResult();

            if (result != null) {

                List<LLResultTypes.FiducialResult> tags =
                        result.getFiducialResults();

                if (tags != null && tags.size() >= 2) {

                    // FIX: Use getTargetXDegrees()
                    double tx1 = tags.get(0).getTargetXDegrees();
                    double tx2 = tags.get(1).getTargetXDegrees();

                    double midTx = (tx1 + tx2) / 2.0;

                    double turnPower = Math.max(
                            -0.5,
                            Math.min(0.5, midTx * 0.02)
                    );

                    frontLeft.setPower(turnPower);
                    frontRight.setPower(-turnPower);
                    backLeft.setPower(turnPower);
                    backRight.setPower(-turnPower);

                    telemetry.addData(
                            "tx1", String.format("%.2f", tx1));
                    telemetry.addData(
                            "tx2", String.format("%.2f", tx2));
                    telemetry.addData(
                            "midTx", String.format("%.2f", midTx));

                } else if (tags != null && tags.size() == 1) {

                    // FIX: Use getTargetXDegrees()
                    double tx = tags.get(0).getTargetXDegrees();

                    double turnPower = Math.max(
                            -0.5,
                            Math.min(0.5, tx * 0.02)
                    );

                    frontLeft.setPower(turnPower);
                    frontRight.setPower(-turnPower);
                    backLeft.setPower(turnPower);
                    backRight.setPower(-turnPower);

                    telemetry.addData("tags", "1 visible");

                } else {

                    frontLeft.setPower(0);
                    frontRight.setPower(0);
                    backLeft.setPower(0);
                    backRight.setPower(0);

                    telemetry.addData("tags", "none visible");
                }
            }
        }

        // Launcher control
        double velocity = launcherRight.getVelocity();

        double error = LAUNCHER_TARGET - velocity;

        double launchPower = Math.max(
                0,
                Math.min(
                        1,
                        LAUNCHER_KP * error
                                + LAUNCHER_KF * LAUNCHER_TARGET
                )
        );

        launcherRight.setPower(launchPower);
        launcherLeft.setPower(launchPower);

        boolean atSpeed =
                Math.abs(velocity - LAUNCHER_TARGET)
                        < VELOCITY_TOLERANCE;

        // Intake controls
        if (gamepad1.right_bumper) {
            intakeMotor.setPower(1.0);
        } else if (gamepad1.left_bumper) {
            intakeMotor.setPower(-1.0);
        } else if (!feederRunning) {
            intakeMotor.setPower(0.0);
        }

        // A button starts the feeder sequence
        boolean a = gamepad1.a;

        if (a && !lastA) {
            feederRunning = true;
            feederStartTime = 0;
        }

        lastA = a;

        // Wait until the launcher reaches the target speed
        if (feederRunning && atSpeed && feederStartTime == 0) {
            feederStartTime = System.currentTimeMillis();
        }

        // Stop feeding after the set duration
        if (feederRunning
                && feederStartTime != 0
                && System.currentTimeMillis() - feederStartTime
                >= FEEDER_DURATION_MS) {

            feederRunning = false;
            feederStartTime = 0;
        }

        boolean feederActive =
                feederRunning && feederStartTime != 0;

        if (feederActive) {
            intakeMotor.setPower(1.0);
            feederMotor.setPower(1.0);
        } else {
            feederMotor.setPower(0.0);
        }

        // Telemetry
        telemetry.addData(
                "Limelight Mode", limelightMode ? "ON" : "OFF");
        telemetry.addData(
                "Launcher Velocity", String.format("%.0f", velocity));
        telemetry.addData(
                "At Speed", atSpeed ? "YES" : "NO");
        telemetry.addData(
                "Feeder",
                feederActive ? "FIRING"
                        : (feederRunning ? "WAIT SPEED" : "OFF"));

        telemetry.update();
    }

    @Override
    public void stop() {

        frontLeft.setPower(0);
        frontRight.setPower(0);
        backLeft.setPower(0);
        backRight.setPower(0);

        launcherRight.setPower(0);
        launcherLeft.setPower(0);

        intakeMotor.setPower(0);
        feederMotor.setPower(0);

        limelight.stop();
    }
}
