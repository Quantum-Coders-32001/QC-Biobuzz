package org.firstinspires.ftc.teamcode.autos;

import com.pedropathing.api.Paths;
import com.pedropathing.ivy.Command;
import com.pedropathing.ivy.Scheduler;

import static com.pedropathing.ivy.Scheduler.reset;
import static com.pedropathing.ivy.Scheduler.schedule;
import static com.pedropathing.ivy.groups.Groups.parallel;
import static com.pedropathing.ivy.groups.Groups.race;
import static com.pedropathing.ivy.groups.Groups.sequential;
import static com.pedropathing.ivy.pedro.PedroCommands.follow;


import com.pedropathing.api.PoseFactory;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.pedropathing.paths.Path;


import org.firstinspires.ftc.teamcode.pedro.Constants;
import org.firstinspires.ftc.teamcode.subsystems.ivyCommands;

@Autonomous
public class TwopointFiveTipPark extends OpMode {
    private Follower follower; // Add this

    private ivyCommands robot;

    private final PoseFactory poseFactory = PoseFactory.degrees();

    private final Pose start = poseFactory.of(52.121, 8.9273, -90);
    private final Pose goToLine = poseFactory.of(8.8969, 5.4344, 180);
    private final Pose goToLineControl1 = poseFactory.of(25.4168, 13.9624, 0);
    private final Pose moveFromLineStart = poseFactory.of(8.8969, 5.4344, 180);
    private final Pose moveFromLine = poseFactory.of(17.3391, 19.9198, 71);
    private final Pose moveAcrossField = poseFactory.of(48.78, 124.1943, 73.2209);
    private final Pose goToFlowerStart = poseFactory.of(48.78, 124.1943, 99);
    private final Pose goToFlower = poseFactory.of(48.2649, 129.1998, 99);
    private final Pose goTo2ndFlower = poseFactory.of(20.7021, 47.1566, 71.43);
    private final Pose intakeFrom2ndFlowerStart = poseFactory.of(20.7021, 47.1566, 71);
    private final Pose intakeFrom2ndFlower = poseFactory.of(14.3626, 46.909, 180);
    private final Pose shoot2ndFlowerStart = poseFactory.of(14.3626, 46.909, 180);
    private final Pose shoot2ndFlower = poseFactory.of(30.8448, 23.3163, 230);
    private final Pose park = poseFactory.of(7.8398, 100.8798, -73.4799);

    private Path goToLine() {
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

    public Path goTo2ndFlower() {
        return Paths.line(goToFlower, goTo2ndFlower).reverseTangent();
    }

    public Path intakeFrom2ndFlower() {
        return Paths.line(intakeFrom2ndFlowerStart, intakeFrom2ndFlower).linear(intakeFrom2ndFlowerStart, intakeFrom2ndFlower);
    }

    public Path shoot2ndFlower() {
        return Paths.line(shoot2ndFlowerStart, shoot2ndFlower).linear(shoot2ndFlowerStart, shoot2ndFlower);
    }

    public Path park() {
        return Paths.line(shoot2ndFlower, park).reverseTangent();
    }

    @Override
    public void init() {

        Scheduler.reset();
        follower = Constants.create(hardwareMap);
        follower.setPose(start);
        follower.update();
        robot = new ivyCommands(hardwareMap);
    }

    @Override
    public void start() {
        schedule(autoRoutine());
    }

    @Override
    public void loop() {
        follower.update();
        Scheduler.execute();
    }

    private Command autoRoutine() {
        return sequential(
                robot.shoot(1800,2),
                race(follow(follower, goToLine()), robot.intake()),
                follow(follower, moveFromLine()),
                follow(follower, moveAcrossField()),
                follow(follower, goToFlower()),
                follow(follower, goTo2ndFlower()),
                follow(follower, intakeFrom2ndFlower()),
                follow(follower, shoot2ndFlower()),
                follow(follower, park())
        );
    }
}
