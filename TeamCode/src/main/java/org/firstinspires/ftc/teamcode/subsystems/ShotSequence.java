package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Servo;

/**
 * The shoot sequence: gate to the fire position and both transfer motors at full power while the
 * trigger is held; gate back to rest and transfers released when it comes up.
 *
 * <p>Exists as its own class for one reason: {@code ShotMapTuner} records the shots that the
 * distance -> velocity regression is fitted from, and a shot fired through different gate timing or
 * a different transfer power is <b>a different shot</b>. If the tuner reimplemented these four lines,
 * the fitted numbers would describe a mechanism the match never runs. One implementation, called by
 * both (same reasoning as the AGENTS.md §7 note about two direction configs for one servo).
 *
 * <p>Deliberately does NOT wait for the flywheel to be at speed. Firing while the shooter is still
 * spinning up dribbles the ball out instead of launching it - that is the driver's cue to hold the
 * button until telemetry says "At speed". Gating on speed inside the sequence would hide the
 * problem instead of showing it.
 *
 * <p>Call {@link #update} exactly once per loop, after whatever sets the transfer motors from the
 * A/X buttons: the fire state overrides them.
 */
public final class ShotSequence {

    private final Servo gate;
    private final DcMotor transferL;
    private final DcMotor transferR;
    private final double restPos;
    private final double firePos;

    private boolean firing;
    private boolean homed;

    public ShotSequence(Servo gate, DcMotor transferL, DcMotor transferR,
                        double restPos, double firePos) {
        this.gate = gate;
        this.transferL = transferL;
        this.transferR = transferR;
        this.restPos = restPos;
        this.firePos = firePos;
    }

    /**
     * @param fire the trigger state this loop
     * @return the latched fire state after this update
     */
    public boolean update(boolean fire) {
        // The gate is first driven on the first loop, never at INIT: no mechanism may move between
        // INIT and Play (G304.G/H, G403).
        if (!homed) {
            gate.setPosition(restPos);
            firing = false;
            homed = true;
        }
        if (fire != firing) {
            gate.setPosition(fire ? firePos : restPos);
            firing = fire;
        }
        if (fire) {
            transferL.setPower(1.0);
            transferR.setPower(1.0);
        }
        return firing;
    }

    /** The latched state, for telemetry. */
    public boolean isFiring() {
        return firing;
    }
}
