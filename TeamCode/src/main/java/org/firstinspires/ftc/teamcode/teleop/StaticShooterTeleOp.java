package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.seattlesolvers.solverslib.command.CommandOpMode;
import com.seattlesolvers.solverslib.command.InstantCommand;
import com.seattlesolvers.solverslib.command.RunCommand;
import com.seattlesolvers.solverslib.command.StartEndCommand;
import com.seattlesolvers.solverslib.gamepad.GamepadEx;
import com.seattlesolvers.solverslib.gamepad.GamepadKeys;

import org.firstinspires.ftc.teamcode.commands.FireCommand;
import org.firstinspires.ftc.teamcode.subsystems.BlockerSubsystem;
import org.firstinspires.ftc.teamcode.subsystems.DriveSubsystem;
import org.firstinspires.ftc.teamcode.subsystems.FlowerSubsystem;
import org.firstinspires.ftc.teamcode.subsystems.IntakeSubsystem;
import org.firstinspires.ftc.teamcode.subsystems.ShooterSubsystem;

import java.util.List;

/**
 * Static-shooter teleop for the first league meet. All controls on gamepad1.
 *   sticks        mecanum, robot-centric (left = move/strafe, right x = turn)
 *   B             toggle flower intake
 *   A (hold)      intake + transfer forward
 *   X (hold)      intake + transfer reverse
 *   Y             toggle launchers (ON at start)
 *   Right bumper  open blocker for BLOCKER_OPEN_MS then close (presses during that window are ignored)
 *
 * UNVERIFIED: not run on a robot.
 */
@TeleOp(name = "Static Shooter TeleOp", group = "TeleOp")
public class StaticShooterTeleOp extends CommandOpMode {
    private List<LynxModule> hubs;

    @Override
    public void initialize() {
        // Manual bulk caching: cleared once per loop in run() so PIDF reads are fresh.
        hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);

        // SubsystemBase auto-registers, so no register() call is needed.
        DriveSubsystem drive = new DriveSubsystem(hardwareMap);
        IntakeSubsystem intake = new IntakeSubsystem(hardwareMap);
        FlowerSubsystem flower = new FlowerSubsystem(hardwareMap);
        ShooterSubsystem shooter = new ShooterSubsystem(hardwareMap);
        BlockerSubsystem blocker = new BlockerSubsystem(hardwareMap);

        // TODO(auto aim): once there is a distance source, call
        // shooter.setDistanceSupplier(() -> distanceToGoalIn);

        GamepadEx driver = new GamepadEx(gamepad1);

        // Raw sticks on purpose: no ambiguity about whether GamepadEx inverts Y.
        drive.setDefaultCommand(new RunCommand(
                () -> drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x),
                drive));

        driver.getGamepadButton(GamepadKeys.Button.B)
                .whenPressed(new InstantCommand(flower::toggle));

        driver.getGamepadButton(GamepadKeys.Button.A)
                .whileHeld(new StartEndCommand(() -> intake.setPower(INTAKE_POWER), intake::stop, intake));
        driver.getGamepadButton(GamepadKeys.Button.X)
                .whileHeld(new StartEndCommand(() -> intake.setPower(-INTAKE_POWER), intake::stop, intake));

        driver.getGamepadButton(GamepadKeys.Button.Y)
                .whenPressed(new InstantCommand(shooter::toggle));

        // The InstantCommand has NO requirements on purpose: if it required the blocker, a second press would
        // interrupt the running FireCommand and restart it instead of being ignored.
        driver.getGamepadButton(GamepadKeys.Button.RIGHT_BUMPER)
                .whenPressed(new InstantCommand(() -> {
                    if (!blocker.isOpen()) schedule(new FireCommand(blocker));
                }));

        schedule(new RunCommand(() -> {
            telemetry.addData("Launchers", shooter.isEnabled() ? "ON" : "OFF");
            telemetry.addData("Aim mode", shooter.isAutoAiming() ? "AUTO (distance)" : "DEFAULT (no distance/table)");
            telemetry.addData("Target vel (tps)", "%.0f", shooter.getTargetVelTps());
            telemetry.addData("Vel R / L (tps)", "%.0f / %.0f", shooter.getVelRTps(), shooter.getVelLTps());
            telemetry.addData("At speed", shooter.atSpeed());
            telemetry.addData("Hood", shooter.isHoodConfigured() ? String.format("%.3f", shooter.getHoodPos()) : "NOT CONFIGURED");
            telemetry.addData("Flower", flower.isDeployed() ? "DEPLOYED" : "STOWED/UNKNOWN");
            telemetry.addData("Blocker", blocker.isOpen() ? "OPEN" : "CLOSED");
            if (LAUNCH_KP == 0 && LAUNCH_KI == 0 && LAUNCH_KD == 0 && LAUNCH_KF == 0) {
                telemetry.addLine("WARN: launcher PIDF gains are all 0 (untuned)");
            }
            if (DEFAULT_LAUNCH_VEL_TPS <= 0) {
                telemetry.addLine("WARN: DEFAULT_LAUNCH_VEL_TPS is 0, launchers will not spin");
            }
            telemetry.update();
        }));
    }

    @Override
    public void run() {
        for (LynxModule hub : hubs) hub.clearBulkCache();
        super.run();
    }
}
