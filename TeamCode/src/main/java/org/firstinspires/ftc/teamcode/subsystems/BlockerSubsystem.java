package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.seattlesolvers.solverslib.command.SubsystemBase;

/** Servo arm. Closed = blocks balls, open = balls pass (shoots). Closes on the first scheduler loop, not at INIT. */
public class BlockerSubsystem extends SubsystemBase {
    private final Servo blocker;
    private boolean open = false;
    private boolean homed = false;

    public BlockerSubsystem(HardwareMap hw) {
        blocker = hw.get(Servo.class, BLOCKER_NAME);
    }

    @Override
    public void periodic() {
        if (!homed) { // first loop after Play: put the arm in the blocking position
            close();
            homed = true;
        }
    }

    public void open() {
        blocker.setPosition(BLOCKER_OPEN_POS);
        open = true;
    }

    public void close() {
        blocker.setPosition(BLOCKER_CLOSED_POS);
        open = false;
    }

    public boolean isOpen() {
        return open;
    }
}
