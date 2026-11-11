package org.firstinspires.ftc.teamcode.autos;

import static com.pedropathing.api.Paths.*;
import com.pedropathing.api.Paths;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.Scheduler;
import static com.pedropathing.ivy.Scheduler.schedule;
import static com.pedropathing.ivy.commands.Commands.*;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static com.pedropathing.ivy.pedro.PedroCommands.follow;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.teamcode.pedro.Constants;

@Autonomous(name = "TwoTipPark", group = "Autonomous")
public class TwoTipPark extends LinearOpMode {

    private Follower follower;

    private final PoseFactory poseFactory = PoseFactory.degrees();

    private final Pose start = poseFactory.of(52.121, 8.9273, -90);
    private final Pose goToLine = poseFactory.of(8.8969, 5.4344, 180);
    private final Pose goToLineControl1 = poseFactory.of(25.4168, 13.9624, 0);
    private final Pose moveFromLineStart = poseFactory.of(8.8969, 5.4344, 180);
    private final Pose moveFromLine = poseFactory.of(17.3391, 19.9198, 71);
    private final Pose moveAcrossField = poseFactory.of(48.78, 124.1943, 73.2209);
    private final Pose goToFlowerStart = poseFactory.of(48.78, 124.1943, 99);
    private final Pose goToFlower = poseFactory.of(48.2649, 129.1998, 99);
    private final Pose park = poseFactory.of(10.1165, 99.0629, 38.3085);

    // Autonomous routine
    public Command autoRoutine() {
        return sequential(
                follow(follower, goToLine()),
                follow(follower, moveFromLine()),
                follow(follower, moveAcrossField()),
                follow(follower, goToFlower()),
                follow(follower, park())
        );
    }

    @Override
    public void runOpMode() {
        Scheduler.reset();
        follower = Constants.create(hardwareMap);
        follower.setPose(start);
        follower.update();

        waitForStart();
        schedule(autoRoutine());

        while (opModeIsActive()) {
            follower.update();
            Scheduler.execute();

            telemetry.addData("x", follower.pose().x());
            telemetry.addData("y", follower.pose().y());
            telemetry.addData("heading", follower.pose().heading());

            if (follower.currentPath() != null) {
                telemetry.addData("Current path distance remaining", follower.distanceToEndpoint());
                telemetry.addData("Path number", follower.pathIndex());
            }

            telemetry.update();
        }
    }

    public Path goToLine() {
        return Paths.curve(start, goToLineControl1, goToLine).linear(start, goToLine);
    }

    public Path moveFromLine() {
        return Paths.line(moveFromLineStart, moveFromLine).linear(moveFromLineStart, moveFromLine);
    }

    public Path moveAcrossField() {
        return Paths.line(moveFromLine, moveAcrossField).tangent();
    }

    public Path goToFlower() {
        return Paths.line(goToFlowerStart, goToFlower).linear(goToFlowerStart, goToFlower);
    }

    public Path park() {
        return Paths.line(goToFlower, park).reverseTangent();
    }
}
