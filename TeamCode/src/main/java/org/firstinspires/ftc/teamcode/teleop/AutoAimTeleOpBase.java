package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.hardware.Gamepad;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.teamcode.subsystems.HiveAim;

import java.util.ArrayList;
import java.util.List;

/**
 * Chassis auto-aim on the HIVE CELL, shared by the red and blue match teleops.
 *
 * <p>Controls:
 * <ul>
 *   <li>gamepad1 left stick - translate (always the driver's)</li>
 *   <li>gamepad1 right stick - manual turn</li>
 *   <li>gamepad1 RIGHT BUMPER (hold) - auto-aim. Releases straight back to the right stick.</li>
 *   <li>gamepad2 - every mechanism: A/X transfer (lt/rt), B flower, Y shooter, RIGHT BUMPER = shoot
 *       (gate 0.55 + both transfers at 1, restored to 0.75 on release)</li>
 * </ul>
 *
 * <p>What "auto-aim" means here: the chassis is the turret. The drive mix's turn component is
 * replaced by a P loop on the median tx of the raised CELL's centre tag pair, so the CELL is driven
 * to the camera crosshair; the driver keeps full translational control. The same frame also yields
 * a range (camera-space 3D solve, rotated by the measured mount pitch) which is written to
 * {@code distanceToGoalIn}, so the existing shot table picks the flywheel velocity and hood.
 *
 * <p>Why the upper pair: the pipeline contains the centre pair of BOTH CELLs (4 IDs). The raised
 * CELL is physically higher, so it is the upper cluster in the image. That is how the aim survives
 * a tip caused by the partner robot, with no robot-to-robot comms and no tip counter
 * (AGENTS.md 4.4). See {@link HiveAim#selectRaisedCell}.
 *
 * <p>Known limits, stated rather than hidden:
 * <ul>
 *   <li>Zeroing tx zeroes the true azimuth even with the camera pitched up, but the loop gain varies
 *       with elevation, so {@link #AIM_KP} is a compromise. Tune at realistic range.</li>
 *   <li>Range is only as good as {@link #CAMERA_PITCH_UP_DEG}; it is currently a guess from a
 *       spoken angle, not a measurement.</li>
 *   <li>The tag cluster is on the CELL's bottom face, which is not the CELL mouth. Expect a small
 *       constant aim offset, TODO(measure).</li>
 *   <li>Manuals 9.9: tags face down. A level camera sees them at a grazing angle; detection range
 *       will be shorter than a forward-facing-target intuition suggests.</li>
 * </ul>
 *
 * UNVERIFIED on hardware.
 */
public abstract class AutoAimTeleOpBase extends StaticShooterTeleOp {

    // ---- vision ----
    private Limelight3A limelight;
    private boolean visionReady;
    private int pipelineSeen = -1;
    private HiveAim.Selection selection;
    private long lastTargetNanos;

    // ---- aim loop ----
    private boolean aiming;
    private double lastTurn;
    private long lastAimLoopNanos;

    /** "RED" / "BLUE", telemetry only. */
    protected abstract String allianceLabel();

    /** Limelight pipeline INDEX (names in the web UI are cosmetic). */
    protected abstract int pipelineIndex();

    /** Fiducial IDs this alliance may aim at; anything else is ignored. */
    protected abstract int[] allianceTagIds();

    @Override
    public void init() {
        super.init();
        try {
            limelight = hardwareMap.get(Limelight3A.class, LIMELIGHT_NAME);
            limelight.setPollRateHz(100);
            limelight.pipelineSwitch(pipelineIndex());
            limelight.start();
            visionReady = true;
        } catch (RuntimeException e) {
            // Camera missing or misnamed: keep the robot drivable and say so on telemetry rather
            // than throwing at INIT and leaving the team with a dead OpMode.
            limelight = null;
            visionReady = false;
        }
    }

    @Override
    protected Gamepad mechanismPad() {
        return gamepad2;
    }

    @Override
    protected void beforeLoop() {
        updateVision();
    }

    // ------------------------------------------------------------------ vision
    private void updateVision() {
        if (!visionReady) return;

        LLResult result = limelight.getLatestResult();
        if (result == null) return;
        pipelineSeen = result.getPipelineIndex();
        if (!result.isValid()) return; // no target this frame; keep the last one until it times out

        List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();
        if (fiducials == null || fiducials.isEmpty()) return;

        List<HiveAim.Sample> samples = new ArrayList<>(fiducials.size());
        int[] allowed = allianceTagIds();
        for (LLResultTypes.FiducialResult fiducial : fiducials) {
            int id = fiducial.getFiducialId();
            if (!isAllianceTag(id, allowed)) continue;
            samples.add(new HiveAim.Sample(
                    id,
                    fiducial.getTargetXDegrees(),
                    fiducial.getTargetYDegrees(),
                    rangeIn(fiducial)));
        }
        if (samples.isEmpty()) return;

        HiveAim.Selection selected = HiveAim.selectRaisedCell(samples, AIM_CLUSTER_GAP_DEG);
        if (selected == null) return;

        selection = selected;
        lastTargetNanos = System.nanoTime();
        // Only overwrite on a usable solve: losing the 3D pose for a frame must not drop the
        // flywheel back to DEFAULT_LAUNCH_VEL_TPS (0) in the middle of a match.
        if (Double.isFinite(selected.distIn)) {
            distanceToGoalIn = selected.distIn + CAMERA_FORWARD_OFFSET_IN;
        }
    }

    /**
     * Horizontal camera-to-tag distance in inches from Limelight's per-tag solve.
     * Uses the 3D pose rather than a ty/tag-height triangle, because the tag height is not
     * measured either and this needs one constant (mount pitch) instead of two.
     */
    private static double rangeIn(LLResultTypes.FiducialResult fiducial) {
        try {
            Pose3D pose = fiducial.getTargetPoseCameraSpace();
            if (pose == null) return Double.NaN;
            Position p = pose.getPosition().toUnit(DistanceUnit.INCH);
            return HiveAim.horizontalDistanceIn(p.x, p.y, p.z, CAMERA_PITCH_UP_DEG);
        } catch (RuntimeException e) {
            // A tag can be decoded (tx/ty valid) while the 3D solve is not. Bearing is still usable.
            return Double.NaN;
        }
    }

    private static boolean isAllianceTag(int id, int[] allowed) {
        for (int a : allowed) {
            if (a == id) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ aiming
    @Override
    protected double driverTurn() {
        long now = System.nanoTime();
        double dtSec = (lastAimLoopNanos == 0) ? 0.02 : (now - lastAimLoopNanos) / 1e9;
        lastAimLoopNanos = now;

        aiming = gamepad1.right_bumper && hasFreshTarget(now);

        if (!aiming) {
            lastTurn = 0.0; // re-engage through the slew limit, never a step
            return super.driverTurn();
        }

        double errDeg = selection.txDeg + CAMERA_YAW_OFFSET_DEG;
        double targetTurn = HiveAim.aimTurnPower(errDeg, AIM_KP, AIM_DEADBAND_DEG, AIM_MAX_TURN);
        double maxStep = AIM_SLEW_PER_SEC * Math.max(0.0, Math.min(dtSec, 0.1));
        lastTurn = HiveAim.slewToward(targetTurn, lastTurn, maxStep);
        return lastTurn;
    }

    private boolean hasFreshTarget(long now) {
        return visionReady
                && selection != null
                && (now - lastTargetNanos) <= AIM_TARGET_TIMEOUT_MS * 1_000_000L;
    }

    // --------------------------------------------------------------- telemetry
    @Override
    protected void extraTelemetry() {
        telemetry.addData("Alliance / pipeline", allianceLabel() + " / " + pipelineIndex()
                + ((visionReady && pipelineSeen >= 0 && pipelineSeen != pipelineIndex())
                ? "  WARN: LL on " + pipelineSeen : ""));

        if (!visionReady) {
            telemetry.addData("Limelight", "NOT CONFIGURED (" + LIMELIGHT_NAME + ")");
        } else if (!limelight.isConnected()) {
            telemetry.addData("Limelight", "NO LINK");
        } else {
            telemetry.addData("Limelight", "OK");
        }

        if (!visionReady) {
            telemetry.addData("Aim", "UNAVAILABLE");
        } else if (aiming) {
            telemetry.addData("Aim", "AIMING");
        } else if (gamepad1.right_bumper) {
            telemetry.addData("Aim", "HOLDING - no target");
        } else {
            telemetry.addData("Aim", "manual");
        }

        if (selection != null) {
            telemetry.addData("Tags used", selection.count + " of " + (selection.twoClusters ? "2 CELLs" : "1 CELL")
                    + " " + ids(selection.ids));
            telemetry.addData("tx / ty (deg)", "%.2f / %.2f", selection.txDeg, selection.tyDeg);
        }
        telemetry.addData("Target dist (in)", Double.isFinite(distanceToGoalIn)
                ? String.format("%.1f", distanceToGoalIn) + (hasFreshTarget(System.nanoTime()) ? "" : "  STALE")
                : "NONE");
    }

    private static String ids(int[] ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        return sb.append(']').toString();
    }

    // -------------------------------------------------------------- safe stop
    @Override
    public void stop() {
        super.stop();
        if (limelight != null) limelight.stop();
    }
}
