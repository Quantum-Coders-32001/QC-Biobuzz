package org.firstinspires.ftc.teamcode.commands;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.BLOCKER_OPEN_MS;

import com.qualcomm.robotcore.util.ElapsedTime;
import com.seattlesolvers.solverslib.command.CommandBase;

import org.firstinspires.ftc.teamcode.subsystems.BlockerSubsystem;

/** Opens the blocker, waits BLOCKER_OPEN_MS (non-blocking), then closes it. Always closes on end/interrupt. */
public class FireCommand extends CommandBase {
    private final BlockerSubsystem blocker;
    private final ElapsedTime timer = new ElapsedTime();

    public FireCommand(BlockerSubsystem blocker) {
        this.blocker = blocker;
        addRequirements(blocker);
    }

    @Override
    public void initialize() {
        blocker.open();
        timer.reset();
    }

    @Override
    public boolean isFinished() {
        return timer.milliseconds() >= BLOCKER_OPEN_MS;
    }

    @Override
    public void end(boolean interrupted) {
        blocker.close();
    }
}
