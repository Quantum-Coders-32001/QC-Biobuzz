package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.seattlesolvers.solverslib.command.SubsystemBase;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.teamcode.math.ShotTable;

import java.util.function.DoubleSupplier;

/**
 * Two flywheels (SolversLib PIDF, one controller per motor) + adjustable hood.
 *
 * Aim source: if a finite distance (inches) is supplied AND the shot table is filled in, velocity and hood come
 * from the table. Otherwise the DEFAULT velocity/hood constants are used. Nothing here runs before Play
 * (periodic only runs when the scheduler runs).
 */
public class ShooterSubsystem extends SubsystemBase {
    private final DcMotorEx launchR, launchL;
    private final Servo hood;
    private final ShotTable velocityTable, hoodTable; // null when the table is not filled in
    private final boolean hoodConfigured;

    private PIDFController pidR, pidL;
    private DoubleSupplier distanceSupplierIn = () -> Double.NaN; // TODO: wire a real distance source

    private boolean enabled = true; // ON by default at the start of teleop
    private boolean autoAiming = false;
    private double targetVelTps = 0.0;
    private double hoodPos = Double.NaN;

    public ShooterSubsystem(HardwareMap hw) {
        launchR = hw.get(DcMotorEx.class, LAUNCH_R_NAME);
        launchL = hw.get(DcMotorEx.class, LAUNCH_L_NAME);
        launchR.setDirection(DcMotorSimple.Direction.REVERSE);

        for (DcMotorEx m : new DcMotorEx[]{launchR, launchL}) {
            m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER); // PIDF is done in software
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        }

        hood = hw.get(Servo.class, HOOD_NAME);
        hoodConfigured = !Double.isNaN(HOOD_MIN_POS) && !Double.isNaN(HOOD_MAX_POS)
                && !Double.isNaN(HOOD_DEFAULT_POS) && HOOD_MIN_POS <= HOOD_MAX_POS;

        boolean tableReady = SHOT_DISTANCES_IN.length >= 2
                && SHOT_VELOCITIES_TPS.length == SHOT_DISTANCES_IN.length
                && SHOT_HOOD_POS.length == SHOT_DISTANCES_IN.length;
        velocityTable = tableReady ? new ShotTable(SHOT_DISTANCES_IN, SHOT_VELOCITIES_TPS) : null;
        hoodTable = tableReady ? new ShotTable(SHOT_DISTANCES_IN, SHOT_HOOD_POS) : null;

        resetControllers();
    }

    private void resetControllers() {
        pidR = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
        pidL = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
    }

    public void setDistanceSupplier(DoubleSupplier supplierIn) {
        this.distanceSupplierIn = supplierIn;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean on) {
        if (on && !enabled) resetControllers(); // fresh integrator on every spin-up
        enabled = on;
    }

    @Override
    public void periodic() {
        double distIn = distanceSupplierIn.getAsDouble();
        autoAiming = velocityTable != null && Double.isFinite(distIn);

        targetVelTps = autoAiming ? velocityTable.get(distIn) : DEFAULT_LAUNCH_VEL_TPS;

        if (hoodConfigured) {
            double pos = autoAiming ? hoodTable.get(distIn) : HOOD_DEFAULT_POS;
            hoodPos = Math.max(HOOD_MIN_POS, Math.min(HOOD_MAX_POS, pos));
            hood.setPosition(hoodPos);
        }

        if (enabled && targetVelTps > 0) {
            // Output clamped to [0,1]: flywheels are never driven backwards to brake.
            launchR.setPower(clamp01(pidR.calculate(launchR.getVelocity(), targetVelTps)));
            launchL.setPower(clamp01(pidL.calculate(launchL.getVelocity(), targetVelTps)));
        } else {
            launchR.setPower(0);
            launchL.setPower(0);
        }
    }

    /**
     * Direct zero command for the OpMode-end hook (StaticShooterTeleOp.end(); the scheduler no
     * longer runs periodic() at that point). SolversLib's CommandOpMode does NOT zero actuators
     * automatically, so this is the G403/G404 safe-stop path. Servos are intentionally NOT
     * re-commanded here: no new output means the robot is motionless, which G404 requires
     * after TELEOP.
     */
    public void zeroOutputs() {
        launchR.setPower(0);
        launchL.setPower(0);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }

    // ---- telemetry ----
    public boolean isEnabled() { return enabled; }
    public boolean isAutoAiming() { return autoAiming; }
    public boolean isHoodConfigured() { return hoodConfigured; }
    public double getTargetVelTps() { return targetVelTps; }
    public double getVelRTps() { return launchR.getVelocity(); }
    public double getVelLTps() { return launchL.getVelocity(); }
    public double getHoodPos() { return hoodPos; }

    public boolean atSpeed() {
        return enabled && targetVelTps > 0
                && Math.abs(getVelRTps() - targetVelTps) <= LAUNCH_VEL_TOLERANCE_TPS
                && Math.abs(getVelLTps() - targetVelTps) <= LAUNCH_VEL_TOLERANCE_TPS;
    }
}
