package org.firstinspires.ftc.teamcode.subsystems;

/**
 * Decides which CELL of a HIVE is currently upward-facing, and whether a shot may be taken.
 *
 * <p>Each CELL carries a 4-tag AprilTag Cluster on its underside, facing down toward the TILES
 * (MANUAL 9.9). Because the cluster is on the underside, the reported roll is the discriminator:
 * a cluster on the upward-facing CELL reads roughly upright (|roll| well under 90 deg), while a
 * cluster on the downward-facing CELL reads rolled over. FIRST's tech tip on AprilTag Clusters
 * uses the same test (|roll| < 90 => right-side up).
 *
 * <p>A HIVE is bi-stable, so exactly one of its two CELLS is up. That invariant lets a single
 * visible cluster identify its partner by elimination, which matters because both clusters are
 * frequently in frame at once (FIRST's own example observes RED SCORING at roll 159.3 deg and
 * RED AUDIENCE at roll -19.7 deg in the same frame).
 *
 * <p>Roll alone cannot tell you which HIVE you are looking at, so the caller must also report
 * which HIVE it is aiming at. That is a deliberate separation: the sensor knows the tag ID, the
 * driver/odometry knows which side of the field the robot is on, and this class refuses to guess.
 *
 * <p>Pure Java, no FTC imports, so it is unit-testable off-robot. A sensor adapter feeds in roll
 * values; this class owns the policy.
 */
public final class HiveCellResolver {
    private HiveCellResolver() {}

    public enum Alliance {
        RED, BLUE
    }

    /** Which half of the HIVE a CELL sits on. SCORING is the side opposite the audience. */
    public enum HiveSide {
        AUDIENCE, SCORING
    }

    /**
     * The four CELL clusters on the field. Tag IDs re-verified by the user on 2026-10-21:
     * BLUE HIVE = 38-45, RED HIVE = 30-37, with the audience/scoring split as encoded here.
     * (The manual's text layer silently drops digits in these lists; the per-CELL split came
     * from the rendered page and should still be double-checked against Figure 9-17 before
     * any vision code ships.)
     */
    public enum Cluster {
        RED_SCORING(Alliance.RED, HiveSide.SCORING, 30, 31, 32, 33),
        RED_AUDIENCE(Alliance.RED, HiveSide.AUDIENCE, 34, 35, 36, 37),
        BLUE_AUDIENCE(Alliance.BLUE, HiveSide.AUDIENCE, 38, 39, 40, 41),
        BLUE_SCORING(Alliance.BLUE, HiveSide.SCORING, 42, 43, 44, 45);

        private final Alliance alliance;
        private final HiveSide side;
        private final int[] tagIds;

        Cluster(Alliance alliance, HiveSide side, int... tagIds) {
            this.alliance = alliance;
            this.side = side;
            this.tagIds = tagIds;
        }

        public Alliance alliance() {
            return alliance;
        }

        public HiveSide side() {
            return side;
        }

        public int[] tagIds() {
            return tagIds.clone();
        }

        /** The other CELL of the same HIVE. */
        public Cluster partner() {
            switch (this) {
                case RED_SCORING:
                    return RED_AUDIENCE;
                case RED_AUDIENCE:
                    return RED_SCORING;
                case BLUE_AUDIENCE:
                    return BLUE_SCORING;
                default:
                    return BLUE_AUDIENCE;
            }
        }

        /** True if both clusters belong to the same HIVE. */
        public boolean sameHive(Cluster other) {
            return other != null && other.alliance == alliance;
        }

        /** @return the owning cluster, or null if the ID is not a BIOBUZZ HIVE tag. */
        public static Cluster fromTagId(int tagId) {
            for (Cluster c : values()) {
                for (int id : c.tagIds) {
                    if (id == tagId) {
                        return c;
                    }
                }
            }
            return null;
        }
    }

    /** Tri-state roll read. The middle band is the deadband, not a bug. */
    public enum Tilt {
        UP, DOWN, AMBIGUOUS
    }

    /** Where the robot is pointed. Roll cannot tell red HIVE from blue HIVE, so the caller must. */
    public enum Targeting {
        /** Nothing usable in view. */
        NONE,
        /** Our own HIVE. */
        OWN_ALLIANCE,
        /** The opposing HIVE. */
        OPPONENT_ALLIANCE
    }

    /** Which CELL we currently believe is up, or UNKNOWN while unsettled. */
    public enum UpCell {
        UNKNOWN,
        RED_SCORING,
        RED_AUDIENCE,
        BLUE_AUDIENCE,
        BLUE_SCORING;

        public Cluster toCluster() {
            return (this == UNKNOWN) ? null : Cluster.valueOf(this.name());
        }

        public static UpCell from(Cluster c) {
            return (c == null) ? UNKNOWN : UpCell.valueOf(c.name());
        }
    }

    /** What the driver should be told, and whether the feeder may run. */
    public static final class ShotDecision {
        public final boolean mayShoot;
        public final Targeting targeting;
        public final UpCell believedUpCell;
        /** Human-readable reason, for telemetry. */
        public final String reason;

        ShotDecision(boolean mayShoot, Targeting targeting, UpCell believedUpCell, String reason) {
            this.mayShoot = mayShoot;
            this.targeting = targeting;
            this.believedUpCell = believedUpCell;
            this.reason = reason;
        }
    }

    /**
     * Roll deadband. |roll| at or below ROLL_UP_DEG means the CELL is up; at or above
     * ROLL_DOWN_DEG means it is down. Between the two we hold the previous belief, because a HIVE
     * mid-TIP sweeps its roll straight through that band and a naive threshold would flip targets
     * on every frame of the transition.
     */
    public static final double ROLL_UP_DEG = 70.0;
    public static final double ROLL_DOWN_DEG = 110.0;

    /** Consecutive agreeing frames required before a new up-cell belief is adopted. */
    public static final int CONFIRM_FRAMES = 3;

    /** Frames of no usable vision tolerated before dropping to UNKNOWN and blocking. */
    public static final int STALE_FRAME_LIMIT = 15;

    private Alliance ourAlliance;

    private UpCell believedUp = UpCell.UNKNOWN;
    private UpCell candidate = UpCell.UNKNOWN;
    private int candidateCount = 0;
    private int framesWithoutVision = 0;

    public HiveCellResolver(Alliance ourAlliance) {
        this.ourAlliance = ourAlliance;
    }

    /** Test seam: start from a known belief. */
    HiveCellResolver(Alliance ourAlliance, UpCell initialBelief) {
        this.ourAlliance = ourAlliance;
        this.believedUp = initialBelief;
    }

    public Alliance ourAlliance() {
        return ourAlliance;
    }

    public UpCell believedUpCell() {
        return believedUp;
    }

    public int framesWithoutVision() {
        return framesWithoutVision;
    }

    /**
     * Feeds one vision frame and returns whether a shot may be taken.
     *
     * @param targetCluster the cluster the sensor is currently locked onto, or null if none
     * @param targetRollDeg roll of that cluster's tag, camera-frame corrected
     * @param targeting which HIVE the robot is aimed at
     */
    public ShotDecision update(Cluster targetCluster, double targetRollDeg, Targeting targeting) {
        if (targeting == Targeting.NONE || targetCluster == null) {
            framesWithoutVision++;
            if (framesWithoutVision > STALE_FRAME_LIMIT) {
                believedUp = UpCell.UNKNOWN;
                candidate = UpCell.UNKNOWN;
                candidateCount = 0;
            }
            return decision(false, targeting, "no vision");
        }

        if (targeting == Targeting.OPPONENT_ALLIANCE) {
            framesWithoutVision = 0;
            return decision(false, targeting, "aimed at opponent HIVE (G417)");
        }

        framesWithoutVision = 0;

        Tilt tilt = classify(targetRollDeg);
        if (tilt == Tilt.AMBIGUOUS) {
            // Mid-band. Hold the existing belief rather than guessing; the HIVE may be tipping.
            return decision(believedUp.toCluster() != null
                    && believedUp.toCluster().alliance() == ourAlliance,
                    targeting, "roll ambiguous, holding");
        }

        // Bi-stability: the observed CELL is either up, or it is down and therefore its partner
        // is up.
        Cluster derived = (tilt == Tilt.UP) ? targetCluster : targetCluster.partner();
        UpCell proposal = UpCell.from(derived);

        if (proposal == believedUp) {
            candidate = UpCell.UNKNOWN;
            candidateCount = 0;
        } else {
            if (proposal == candidate) {
                candidateCount++;
            } else {
                candidate = proposal;
                candidateCount = 1;
            }
            if (candidateCount >= CONFIRM_FRAMES) {
                believedUp = proposal;
                candidate = UpCell.UNKNOWN;
                candidateCount = 0;
            }
        }

        Cluster believed = believedUp.toCluster();
        if (believed == null) {
            return decision(false, targeting, "up-cell unconfirmed");
        }
        if (believed.alliance() != ourAlliance) {
            return decision(false, targeting, "up-cell is " + believed.alliance() + " (G417)");
        }
        return decision(true, targeting, "up " + ourAlliance + " CELL confirmed");
    }

    private ShotDecision decision(boolean mayShoot, Targeting targeting, String reason) {
        return new ShotDecision(mayShoot, targeting, believedUp, reason);
    }

    /** Classifies a roll reading into up / down / deadband. */
    public static Tilt classify(double rollDeg) {
        double a = Math.abs(normalizeDeg(rollDeg));
        if (a <= ROLL_UP_DEG) {
            return Tilt.UP;
        }
        if (a >= ROLL_DOWN_DEG) {
            return Tilt.DOWN;
        }
        return Tilt.AMBIGUOUS;
    }

    /** Wraps degrees into (-180, 180]. */
    public static double normalizeDeg(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        }
        if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }
}