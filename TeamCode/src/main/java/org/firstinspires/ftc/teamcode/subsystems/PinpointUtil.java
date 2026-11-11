package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.HardwareMap;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * PINPOINT UTIL
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Helpers for the goBILDA Pinpoint IMU based on the official user guide.
 *
 * The IMU calibrates its zero-offset on power-up, but if the robot is
 * vibrating (e.g. a flywheel spinning, someone bumping the robot) at that
 * moment, it captures a bad zero — which causes constant heading drift
 * even when stationary. Recalibrate in init() while the robot is still.
 *
 * recalibrate(): just recalibrates the IMU, leaves position alone.
 *   Good for teleop where you want to keep the auto's ending position.
 *
 * resetPosAndIMU(): recalibrates IMU AND zeroes position. Docs say this
 *   is recommended for the FIRST opmode of a match (autonomous). For your
 *   code it's effectively the same as recalibrate() because Pedro's
 *   follower.setStartingPose() overwrites the position right after.
 *
 * Both calls take ~250ms and require the robot to be stationary.
 * ═══════════════════════════════════════════════════════════════════════
 */
public class PinpointUtil {

    public static String PINPOINT_NAME = "pinpoint";

    /**
     * Recalibrates the Pinpoint IMU zero-offset.
     * Robot MUST be stationary. ~250ms blocking call.
     * Returns true on success, false if the device couldn't be found.
     */
    public static boolean recalibrate(HardwareMap hardwareMap) {
        try {
            GoBildaPinpointDriver pinpoint =
                    hardwareMap.get(GoBildaPinpointDriver.class, PINPOINT_NAME);
            pinpoint.recalibrateIMU();
            sleep(300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resets position to (0,0,0) AND recalibrates the IMU.
     * The docs recommend this for the first opmode of a match (auto).
     * Note: if your follower.setStartingPose() runs after this, the
     * position reset gets overwritten — that's fine, only the IMU
     * recalibration matters in that case.
     *
     * Robot MUST be stationary. ~250ms blocking call.
     */
    public static boolean resetPosAndIMU(HardwareMap hardwareMap) {
        try {
            GoBildaPinpointDriver pinpoint =
                    hardwareMap.get(GoBildaPinpointDriver.class, PINPOINT_NAME);
            pinpoint.resetPosAndIMU();
            sleep(300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) { }
    }
}