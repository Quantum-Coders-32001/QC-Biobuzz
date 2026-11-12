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

    // ===================== BALL PATH: TRANSFER = lt + rt =====================
    // lt and rt ARE the ball path - intake and transfer in one pair. They are the only motors that
    // move a ball, so A drives both at +TRANSFER_POWER and X drives both at -TRANSFER_POWER.
    // User 2026-11-10, correcting an earlier mistake in this file.
    public static final String TRANSFER_L_NAME = "lt";
    public static final String TRANSFER_R_NAME = "rt";
    public static final double TRANSFER_POWER = 1.0;

    // ===================== CONSTANT-SPEED ROLLER ("roll") =====================
    // The motor the team calls "notshooter". It is NOT the intake and NOT the transfer, it runs at
    // a fixed 0.4 for as long as the robot is enabled, and it is never pulsed, reversed, or driven
    // by the shot sequence. User 2026-11-10, emphatically: do not mess with it.
    // Commanded after Play, never at INIT (nothing may move between INIT and Play, G304.H/G403).
    // Optional in the teleop: if the name is not in the Driver Station config the robot still
    // drives and telemetry says "NOT IN CONFIG" instead of the OpMode dying at INIT.
    public static final String ROLL_NAME = "roll";
    public static final double ROLL_POWER = 0.4;

    // ===================== FLOWER INTAKE (two servos, may not be fitted) =====================
    public static final String FLOWER_L_NAME = "flwrl";
    public static final String FLOWER_R_NAME = "flwrr"; // reversed in code
    public static final double FLOWER_DEPLOY_POS = 1.0;
    public static final double FLOWER_STOW_POS = Double.NaN; // TODO(measure): second toggle position

    // ===================== LAUNCHER (ONE flywheel motor, SolversLib PIDF, ticks/sec) =====================
    // One motor: the Driver Station config has "shooter" (see ivyCommands.java), not a left/right
    // pair. TODO(verify): motor direction.
    public static final String SHOOTER_NAME = "shooter";
    // TODO(tune): Jeffrey will supply these. While all are 0 the launchers output 0 power.
    // Bench tuning order (AGENTS.md L3, see tuners/):
    //   1) LAUNCH_KF = kV from PidfTuner (set target, dial kF until steady state hits it).
    //      SolversLib's kF multiplies the SETPOINT (verified), so power ≈ kF*targetTps + kP*err.
    //      There is no additive-kS slot in PIDFController: kF absorbs it.
    //   2) LAUNCH_KP from PidfTuner (raise until ~5% overshoot, back off).
    //      kI/kD stay 0 for a flywheel speed loop: both inspo repos (Panheya, CNapSys) run
    //      feedforward + P only, and an I term turns shot-load dips into windup.
    public static final double LAUNCH_KP = 0.0;
    public static final double LAUNCH_KI = 0.0;
    public static final double LAUNCH_KD = 0.0;
    public static final double LAUNCH_KF = 0.0;
    public static final double LAUNCH_VEL_TOLERANCE_TPS = 50.0; // telemetry "at speed" only. TODO(tune)
    // Used whenever no distance is available. 0 = launchers stay off. TODO(measure)
    public static final double DEFAULT_LAUNCH_VEL_TPS = 0.0;

    // ===================== FLYWHEEL BANG-BANG (velocity hold, used by the Ivy autos) =====================
    // Copied from teleop/ShooterTeleOp.G on 2026-11-11, where they were tuned by eye against a bench
    // wheel. This is now the ONLY copy: ShooterTeleOp.G initialises its dashboard fields from these,
    // so the auto and the match teleop cannot drift to two different wheel speeds (AGENTS.md §7 -
    // two configs for one mechanism is how the old repo's turret ended up fighting itself).
    //
    // The law (subsystems/ivyCommands#bangBangPower) is a relay:
    //     power = velocity < target - deadband ? bangPower : 0
    // It needs the flywheel in RUN_WITHOUT_ENCODER. In RUN_USING_ENCODER, setPower() is a velocity
    // SETPOINT for the hub's own PID and quietly fights this loop instead of obeying it.
    public static final double BANG_TARGET_TPS = 1470.0; // TODO(tune): settled-on value, not measured
    public static final double BANG_DEADBAND_TPS = 7.2;  // hysteresis width, ~0.5% of target. TODO(tune)
    public static final double BANG_POWER = 1.0;

    // An auto has no button to hold, so it waits for the wheel - but only this long. Without a cap, a
    // jammed or unplugged flywheel hangs the entire auto instead of failing visibly. TODO(measure):
    // time a cold spin-up from 0 to BANG_TARGET_TPS at full power.
    public static final double BANG_SPINUP_TIMEOUT_MS = 2500.0;

    // ===================== HOOD =====================
    public static final String HOOD_NAME = "hood";
    // TODO(measure): real mechanical limits and default. NaN = hood is never commanded.
    public static final double HOOD_MIN_POS = Double.NaN;
    public static final double HOOD_MAX_POS = Double.NaN;
    public static final double HOOD_DEFAULT_POS = Double.NaN;

    // ===================== AUTO VELOCITY / HOOD REGRESSION (distance -> launcher + hood) =====================
    // These are REGRESSION SAMPLES, not a lookup table. At INIT the teleop fits a polynomial
    // through them (subsystems/ShotMap + ShotRegression) and shoots from the fitted law, so the
    // values between samples are smooth instead of a straight line between two measured points.
    //
    // HOW TO FILL THEM: run tuners/ShotMapTuner on a real field, fire at several distances, press
    // LB on each scored shot, then paste the three arrays it prints. Same length, same order.
    // TODO(tune): empty = auto velocity/hood disabled, DEFAULT_LAUNCH_VEL_TPS is used.
    public static final double[] SHOT_DISTANCES_IN = {};
    public static final double[] SHOT_VELOCITIES_TPS = {};
    public static final double[] SHOT_HOOD_POS = {};
    // Polynomial degree for the fits. 2 = quadratic, but only once a law has 4+ points; a 3-point
    // parabola fits its own points exactly and says nothing. 1 = force a line (safer with a small,
    // noisy dataset). AGENTS.md §0.1: do not raise this to hide scatter - fix the data instead.
    public static final int SHOT_FIT_MAX_DEGREE = 2;

    // ===================== GATE (what the old teleop called "blocker") =====================
    // Hardware name is "gate" (ivyCommands.java). 0.75 rests/holds the balls, 0.55 fires.
    // Confirmed by the user 2026-11-10: gate -> 0.55 while shooting, back to 0.75 after.
    public static final String GATE_NAME = "gate";
    public static final double GATE_REST_POS = 0.75;
    public static final double GATE_FIRE_POS = 0.55;

    // ===================== LIMELIGHT =====================
    public static final String LIMELIGHT_NAME = "limelight"; // TODO(verify): DS config name
    // Limelight pipelines are switched by INDEX (0-9); the names in the web UI are cosmetic.
    // User decision 2026-11-10.
    public static final int PIPELINE_RED = 0;
    public static final int PIPELINE_BLUE = 1;

    // AprilTags per alliance, manual 9.9 (MCP index serves 9.9 at version TU06; AGENTS.md 2.12
    // records the same IDs through TU07):
    //   red  30-33 = red CELL opposite the audience, 34-37 = red CELL audience side
    //   blue 38-41 = blue CELL audience side,      42-45 = blue CELL opposite the audience
    // This is a FILTER, not an assumption about which two tags are the centre pair. The pipelines
    // already track only each CELL's centre pair; this guards against an opponent's tags (or a
    // mis-numbered pipeline) ever being used to aim our own robot.
    public static final int[] RED_TAG_IDS = {30, 31, 32, 33, 34, 35, 36, 37};
    public static final int[] BLUE_TAG_IDS = {38, 39, 40, 41, 42, 43, 44, 45};

    // ===================== AUTO AIM (chassis rotates, driver keeps translating) =====================
    // Camera mount. User 2026-11-10: tilted up "around 130 from bottom corner of ll".
    // If the 130 deg is measured from the Limelight's bottom face, the optical axis is
    // 130 - 90 = 40 deg above horizontal. TODO(measure): if it was measured from the front face
    // instead, this number is wrong and every range is wrong with it.
    public static final double CAMERA_PITCH_UP_DEG = 40.0;
    // Camera yaw relative to robot forward; positive = camera points right of forward. TODO(measure)
    public static final double CAMERA_YAW_OFFSET_DEG = 0.0;
    // How far the camera sits AHEAD of the launcher exit, along robot forward. TODO(measure)
    public static final double CAMERA_FORWARD_OFFSET_IN = 0.0;

    // A ty gap at least this big means "two different CELLs are in frame". Tags on one CELL are
    // nearly co-planar, so this separates the pair-of-pairs. TODO(tune with real frames)
    public static final double AIM_CLUSTER_GAP_DEG = 3.0;

    // Turn loop: P only, deadband, slew limited. TODO(tune)
    public static final double AIM_KP = 0.02;            // power per degree; 50 deg error = full power
    public static final double AIM_DEADBAND_DEG = 1.0;   // ~2 in lateral at 10 ft
    public static final double AIM_MAX_TURN = 0.8;       // never full-power spin
    public static final double AIM_SLEW_PER_SEC = 2.5;   // power per second, loop-rate independent

    // Keep aiming on the last frame for this long after the tags vanish (a spike in latency must
    // not snap the chassis back to manual mid-shot). TODO(tune)
    public static final long AIM_TARGET_TIMEOUT_MS = 150;

    // Rgb indihcator

    public static final double red = 0.279;

    public static final double orange = 0.317;

    public static final double yellow = 0.36;

    public static final double green = 0.5;

    public static final double blue = 0.61;

    public static final double indigo = 0.67;

    public static final double violet = 0.7;

    public static final double white = 1;
}
