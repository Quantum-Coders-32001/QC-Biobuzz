package org.firstinspires.ftc.teamcode.config;

/**
 * Static-shooter robot (first league meet). Every config name, position and tunable lives here.
 *
 * Rule from AGENTS.md: never guess a value. Anything unknown is marked TODO(measure) / TODO(tune)
 * and is set to an INERT value (0.0, an empty table, or NaN). Code treats NaN as "not configured"
 * and does not command that mechanism, so an unfilled constant cannot move hardware.
 */
public final class RobotConstants {
    private RobotConstants() {}

    // ===================== DRIVE (mecanum, robot-centric) =====================
    public static final String LF_NAME = "lf";
    public static final String LR_NAME = "lr";
    // Names verified against the Driver Station config by the user on 2026-10-21
    // (ff is the rear-right motor).
    public static final String RF_NAME = "fr";
    public static final String RR_NAME = "ff";
    // TODO(verify on robot): copied from BioBuzzTeleop (left side reversed).
    public static final boolean LEFT_SIDE_REVERSED = true;

    // ===================== INTAKE + TRANSFER (one mechanism, two motors) =====================
    public static final String INTAKE_R_NAME = "r"; // reversed in code
    public static final String INTAKE_L_NAME = "l";
    public static final double INTAKE_POWER = 1.0;

    // ===================== FLOWER INTAKE (two servos) =====================
    public static final String FLOWER_L_NAME = "flwrl";
    public static final String FLOWER_R_NAME = "flwrr"; // reversed in code
    public static final double FLOWER_DEPLOY_POS = 1.0;
    public static final double FLOWER_STOW_POS = Double.NaN; // TODO(measure): second toggle position

    // ===================== LAUNCHERS (two motors, SolversLib PIDF, ticks/sec) =====================
    public static final String LAUNCH_R_NAME = "launchr"; // reversed in code
    public static final String LAUNCH_L_NAME = "launchl";
    // TODO(tune): Jeffrey will supply these. While all are 0 the launchers output 0 power.
    public static final double LAUNCH_KP = 0.0;
    public static final double LAUNCH_KI = 0.0;
    public static final double LAUNCH_KD = 0.0;
    public static final double LAUNCH_KF = 0.0;
    public static final double LAUNCH_VEL_TOLERANCE_TPS = 50.0; // telemetry "at speed" only. TODO(tune)
    // Used whenever no distance is available. 0 = launchers stay off. TODO(measure)
    public static final double DEFAULT_LAUNCH_VEL_TPS = 0.0;

    // ===================== HOOD =====================
    public static final String HOOD_NAME = "hood";
    // TODO(measure): real mechanical limits and default. NaN = hood is never commanded.
    public static final double HOOD_MIN_POS = Double.NaN;
    public static final double HOOD_MAX_POS = Double.NaN;
    public static final double HOOD_DEFAULT_POS = Double.NaN;

    // ===================== AUTO VELOCITY / HOOD LOOKUP (distance -> launcher + hood) =====================
    // TODO(tune): fill all three arrays with the same length >= 2 and strictly increasing distances.
    // Empty = auto-aim disabled (default velocity/hood are used).
    public static final double[] SHOT_DISTANCES_IN = {};
    public static final double[] SHOT_VELOCITIES_TPS = {};
    public static final double[] SHOT_HOOD_POS = {};

    // ===================== BLOCKER (closed = blocking, open = balls pass = shoots) =====================
    public static final String BLOCKER_NAME = "blocker";
    public static final double BLOCKER_CLOSED_POS = 0.0;
    public static final double BLOCKER_OPEN_POS = 0.5;
    public static final long BLOCKER_OPEN_MS = 1000;
}
