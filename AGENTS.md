# AGENTS.md — FTC Team 32001 "Quantum Coders" · BIOBUZZ (2026-27)

Read this whole file before touching code. It is the project's source of truth for the game, the stack,
the conventions and the open questions. Research snapshot: **2026-10-01**, built from the BIOBUZZ
Competition Manual (Sections 9 and 11 as TU02, Section 10 as TU03), Team Updates 00-02 and third-party
material. The manual changes every Thursday, so rule numbers and numbers in general can move. Re-verify anything
that matters before building on it.

Evidence tags used below:
- **[MANUAL]** read directly from the official manual text
- **[TU]** from a Team Update
- **[3P]** third-party source (GitHub sims, guides). Plausible, not authoritative.
- **[INFERRED]** my reasoning from the above. Challenge it.
- **[TODO]** unknown. Must be measured or looked up. Never guess a value.

---

## 0. Rules for the agent

1. **Do not invent.** No made-up APIs, rule numbers, dimensions, field coordinates, AprilTag IDs or hardware
   constants. If it is not here or in docs you actually fetched, write "unknown" and add a `TODO(measure)` Also make sure that you ask the user for this quantity aswell.
2. **Your training data is stale for this project.** BIOBUZZ kicked off in Aug 2026. SolversLib 0.3.6 and
   Pedro Pathing 3 changed APIs (see §3). Do not code from recollection. Read the docs:
   `https://docs.seattlesolvers.com` (append `.md` to any page, index at `/llms.txt`) and the javadocs at
   `https://repo.dairy.foundation/javadoc/releases/org/solverslib/core/latest`.
3. **Be blunt.** The user wants a strict reviewer, not a cheerleader. If a design, constant, plan or the user's own idea
   is weak, say so, say why, and propose the fix. Agree only when the user is actually right. Never pad.
4. **"Done" means demonstrated.** Paste the passing test output, or provide an OpMode with explicit pass/fail
   criteria. If you could not run something (no robot, no SDK), label it **UNVERIFIED**. Do not say it works.
   The user will keep testing until they declare it bulletproof, so do not declare it for them.
5. **Destructive actions need permission.** Never delete, overwrite, move, `rm -rf`, `git reset --hard`,
   force-push, or drop branches without asking first and waiting for a yes. Prefer a new branch over editing in place.
6. **Small, reviewable diffs.** One concern per commit. Do not refactor unrelated code. Keep the build green.
7. **Platform:** the user's machine may be Windows or Linux. Prefer cross-platform commands and say which shell
   you assume.
8. **The robot does not exist yet.** There is no hardware. Every physical constant is a placeholder in one config
   file (§5) and flagged `TODO(measure)`. Prefer work that can be proven off-robot (pure math, simulation, tests).

---

## 1. Team and project state

- Team **32001 Quantum Coders**; GitHub org `Quantum-Coders-32001`. The team has a website and Discord GitHub notifications.
- Language/tooling: Java, Android Studio , Sloth hot-reload, slothboard
  (Dairy Foundation FTC Dashboard fork), FTC Dashboard.
- Previous season (DECODE 2025-26): mecanum, PedroPathing, turret, flywheel shooter, hood regression, Limelight
  fine-aim. Results were mixed (see §7, the old repo "Quantum-Coders-V2"). Do not copy its architecture.
- Coding assistant setup: opencode with NVIDIA Nemotron 3 Ultra. Context is finite and the model can be wrong, so keep
  steps small, read files rather than recalling them, and run things.
- Driver-side facts that affect code: the game has a **30 s AUTO, an 8 s no-movement transition, a 2:00 TELEOP**
  (§2.2) and code revisions do not require robot re-inspection [MANUAL I303.D].

---

## 2. BIOBUZZ game reference

### 2.1 One-paragraph summary
Two alliances of two robots. Robots collect **POLLEN** (small yellow balls) and **NECTAR** (larger, alliance-colored
balls), **LAUNCH** them into their alliance's **HIVE** (a bi-stable seesaw at field center) to cause **HIVE TIPS**,
place them into **FLOWERS** on the walls, and stash them in **GARDENS** in corners. Each tip unlocks more NECTAR.
In the last minute, FLOWER ownership (top-most own-color NECTAR) decides who scores for everything inside it.
Park in your LOADING ZONE at the end. **The launcher is the season.** [MANUAL; framing from 3P]

### 2.2 Match timeline [MANUAL §10.1, §10.4, Table 9-1]
| Phase | Length | Notes |
|---|---|---|
| AUTO | 30 s | No driver input. Select an AUTO OpMode **with the 30 s timer enabled** (G305). |
| Transition | 8 s | **No powered movement of the robot or any mechanism** (G403). Do not press INIT on a TELEOP OpMode if INIT twitches actuators; wait for TELEOP. |
| TELEOP | 2:00 | Field clock counts down 2:00 to 0:00. |
| FLOWER unlock | 1:00 left | NECTAR may enter FLOWERS only in the last 60 s (G410). Humans may enter all remaining NECTAR at ≤60 s (G426). |
| End | 0:00 | Robots must be motionless after TELEOP ends until the Head Referee signals (G404). |

Software implications: run your own match clock from TELEOP start (the robot cannot see the field timer);
zero all actuator outputs for the entire transition and at TELEOP end; make AUTO end cleanly at 30 s.

### 2.3 Field [MANUAL §9]
- 144 in × 144 in, 36 soft foam tiles (24 × 24 × 0.59 in). Illustrations are ±1 in; the 3D CAD is the official geometry.
- Red alliance area is on the **left** from the audience view. Field columns A-C = red side, D-F = blue side (matters for
  AUTO interference, G402).
- ALLIANCE AREA ≈ 97 × 54 in. LOADING ZONE ≈ 23 × 11 in (corner, belongs to the adjacent alliance). GARDEN ≈ 23 × 2 in,
  in the corners, opposite corners for the two alliances.
- 1 HIVE structure at field center (frame holds the red HIVE and the blue HIVE), 4 FLOWERS attached to the perimeter walls.
- **Pedro Pathing uses a 144 × 144 in field**, same size. [INFERRED: mapping of Pedro axes/origin to the manual's red-left /
  audience convention is **TODO**; verify against Pedro docs and the field CAD before hardcoding any pose.]

### 2.4 Scoring elements [MANUAL §9.8, §10.3.1]
- **POLLEN:** ≈ 2.8 in dia, yellow, 40 total. ≈ 0.055 lb [3P].
- **NECTAR:** ≈ 3.6 in dia, 8 red + 8 blue. ≈ 0.091 lb [3P].
- Gopher ResisDent polyethylene balls, **not perfectly spherical, sizes vary**. Design for variation.
- Staging: 4 POLLEN in each of 4 FLOWERS (16); 4 POLLEN in each GARDEN (8); **4 POLLEN preloaded per robot (16)**;
  3 NECTAR in each alliance's upward-facing CELL (6); 5 NECTAR in each ALLIANCE AREA (10).
- A robot must start the match contacting **exactly 4 preloaded POLLEN** (G304).

### 2.5 Point values [MANUAL Table 10-2]
| Achievement | AUTO | TELEOP |
|---|---|---|
| LEAVE (no longer touching the perimeter wall) | 3 | - |
| PARK (partially in your LOADING ZONE) | 5 | 5 |
| HIVE TIP | 20 | 20 |
| POLLEN/NECTAR left in upward-facing CELL at end | - | 2 each |
| Bottom NECTAR Bonus (FLOWER) | - | 5 |
| POLLEN/NECTAR in an **owned** FLOWER | - | 2 each |
| POLLEN/NECTAR in a GARDEN | - | 1 each |

Tips completed before TELEOP count as AUTO. Fouls: MINOR = 5 pts to opponent, MAJOR = 20 pts to opponent.

### 2.6 Ranking points [MANUAL Tables 10-2, 10-3; thresholds are for "all other events"]
- WIN 3, TIE 1.
- **SWARM** 1 RP: combined LEAVE + PARK points ≥ **16**. [INFERRED arithmetic: if both robots LEAVE (6) and both PARK
  (10) that is exactly 16, so reliable AUTO leave + park or TELEOP park on both robots secures it.]
- **POLLINATOR 1** 1 RP: ≥ **4 tips**. **POLLINATOR 2** 1 RP: ≥ **7 tips**.
- Regional and Championship thresholds are TBA in Team Updates.

### 2.7 HIVE [MANUAL §9.6, §10.5.1, G409, G417]
- Frame ≈ 49.46 in wide × 38.95 in deep at the base; pivot axis ≈ **43.95 in** above the tiles.
- Each HIVE has two CELLS ≈ 18.8 in apart; it rotates on the pivot and is **bi-stable**: exactly one CELL faces up.
- CELL opening ≈ **20 in wide × 14 in tall × 12 in deep**. "Bottom of HIVE above TILES" was corrected to 30.6 in [TU01].
- A **TIP** = the HIVE moves from one stable state to the other (down CELL becomes up CELL) and the previously
  free damper contacts the frame. **Launching into the upward-facing CELL is the only legal way to cause a tip**
  (G417). Missing and hitting the outside of the CELL is not treated as strategic, but deliberate attempts are.
- Launching at the downward-facing CELL while the HIVE is moving may disrupt the tip. Pause launching so the
  tip is obvious.
- **Tip threshold is not in the manual** ("enough"). Third-party sims use placeholders (~4 POLLEN on top of 3 NECTAR,
  or ~200 g). **TODO(measure on a real field).**
- When a HIVE tips, its contents spill. They must **hit the floor before any robot collects them** (G409, no catching).
- Raised-CELL mouth height is only in Figures 9-10/9-11 (3P estimates 41-59 in, default 51 in). **TODO(read CAD).**
  A robot must fit in 29 in tall (§2.11), so every HIVE shot is a lob. [3P/INFERRED]
- Starting state: each HIVE tilted with one CELL up. Third-party reading: red's audience-side CELL up, blue's far-side
  CELL up. **TODO(verify Fig 10-2).**
- **Design consequence [INFERRED, important]:** the aim target is *state dependent*. After a tip the other CELL (≈ 18.8 in
  away along the HIVE axis, and at a different height) becomes the target. Your alliance partner can tip it too, and
  there is no robot-to-robot comms, so the turret must not rely on counting only its own tips (§4.4).

### 2.8 FLOWER [MANUAL §9.7, §10.5.2, G410, G418]
- Opening on top ≈ **4 in diameter**, ≈ **21.5 in** above the tiles, with a 1.25 in tall backstop. NECTAR is 3.6 in, so
  clearance is about 0.4 in. Treat it as a precision placement.
- Scoring volume = between the top ring and the middle ring.
- Bottom retrieval opening ≈ 3.55 in tall × 3.57 in deep. **Only POLLEN may be removed, and only from the bottom.**
  Entering is **top only** (G418).
- Lower ring on the floor, ≈ 0.4 in tall, with a ≈ 2.79 in hole that holds a POLLEN.
- **Owner** = the alliance whose top-most own-color NECTAR is in the FLOWER; the owner scores 2 per element inside, no matter
  who placed it. **Bottom NECTAR Bonus** (5) goes to the alliance with the bottom-most own-color NECTAR.
- G410 restricts **NECTAR** before 1:00 remaining (MAJOR FOUL per NECTAR). Whether early POLLEN placement is
  penalized or scores is unclear. **TODO(read G410 and 10.5.2 together, check Q&A).**

### 2.9 GARDEN, LOADING ZONE, LEAVE, PARK [MANUAL §9.3, §10.5.3, §10.5.4]
- GARDEN points: element at least partially in the zone, scored for the garden's alliance regardless of who placed it.
  **GARDENS are unprotected**; either alliance may remove elements.
- LEAVE: robot no longer contacts the perimeter wall (assessed at end of AUTO).
- PARK: robot at least partially in its LOADING ZONE (assessed at end of AUTO and at end of TELEOP).

### 2.10 NECTAR flow [MANUAL §10.1, G426, G427, 3P for details]
- 5 NECTAR per alliance start in the ALLIANCE AREA. **One more may be entered per HIVE TIP** by that alliance, and **all remaining
  at ≤60 s**.
- Humans introduce NECTAR **only via the LOADING ZONE**, by hand, no tools, and it must contact the TILE in the LOADING ZONE
  before contacting a robot or field element (G427). Humans can never score into a HIVE or FLOWER.
- NECTAR that leaves the field goes back to that alliance's drive team.

### 2.11 Robot construction limits [MANUAL V1 §12 + TU]
- **Starting configuration:** fully stationary, within **18 × 18 × 18 in** (R102/R105).
- **R105 expansion:** after the match starts, the robot may expand but must stay within an **18 × 24 × 29 in** sizing volume
  (29 in is vertical from the field surface). It must be **physically** constrained to that without relying on software, must
  stay one assembly, and may not deliberately detach components (G416 enforces in-match).
- **R503:** at most **8 motors and 8 servos total**, across all mechanisms in all configurations (servo limit was cut from 10).
  CR servos count as servos. **Budget this early** (§5).
- **No official weight limit** [MANUAL V1], but weight matters for speed and the field.
- R510 allowed motors list exists (WATTOS Stingray was added in TU00). Check it before buying motors. Servo power/current
  tables in older manuals may not carry over. **TODO(verify in current §12).**

### 2.12 AprilTags [MANUAL §9.9; mostly unextracted]
- 36h11 family, **3.25 in** square tags, in clusters of 4 on a sticker on the **bottom face of each CELL** (facing the tiles,
  bottom edge toward field center). 4 CELLS, so 16 tags.
- Known IDs: 38-41 are the blue CELL on the audience side; red opposite-side starts at 0. The rest were lost in extraction.
  **Do not hardcode IDs; read Figure 9-17.**
- The tags ride on a moving structure, so they are targeting aids, **not static localization landmarks.** [3P: ViDAR marks
  them `localization: false`]. A raised CELL's tag row is reportedly ≈ 9.4 in higher than a lowered one [3P], so height can
  tell you which CELL is up. **TODO(verify).**

### 2.13 Rules that constrain mechanisms and code (titles only; numbers drift between TUs)
- G304 start correctly (own half, touching wall, 4 preloads, not in LOADING ZONE or FLOWER volume, motionless after INIT).
- G305 select/INIT an OpMode. G401 no robot interaction in AUTO. G402 no AUTO interference with the opposing side.
- G403/G404 motionless in transition and after TELEOP.
- G405 do not **deliberately** eject elements from the field (MAJOR per element).
- **G407 control at most 4 elements at once.** 5+ draws scrutiny; 6+ is likely strategic. **Magazine capacity should be ≤ 4
  and software should refuse to intake a 5th.**
- G408 do not control opponent NECTAR. G409 no catching tipped-HIVE spill before it touches something else. G410 NECTAR/FLOWER
  timing. G411 no hoarding or corralling elements to deny the opponent.
- G415 no grabbing, attaching to, or entangling with arena elements. G416 expansion limits. G417 HIVE. G418 FLOWER.
- G419-G421 no damaging, tipping or entangling opponents; 3-second PIN count. G425-G428 humans' reach and NECTAR handling.
- No **launch-zone** restriction surfaced in my research (DECODE had one). **TODO(confirm in current §11).**
- Violation scale: VERBAL WARNING, MINOR (5), MAJOR (20), YELLOW, RED (DQ for the match).

### 2.14 Strategy implications for software [INFERRED]
- A fast, accurate, repeatable **lob launcher + good retargeting** is worth more than anything else; 20 pts per tip.
- Ammunition is limited and shared: 40 POLLEN total on the field. Tips spill the CELL contents to the floor, so elements recirculate.
  Intake and cycle speed beat stockpiling (≤ 4 held).
- The last minute is its own mode: a **timer-driven state machine** that switches from tipping to NECTAR/FLOWER ownership
  to park. Reserve time to reach the LOADING ZONE (park = 5 pts, and feeds SWARM).
- AUTO targets: LEAVE (3) is nearly free; preload shots to tip the HIVE (20) are the main prize; PARK (5) if time allows.

---

## 3. Software stack and conventions

### 3.1 Decisions already made
- **SolversLib 0.3.6 + Pedro Pathing 3.0.x**, command-based. (Ivy was dropped.) Target structure: a `Robot` class, an `AutoBase`
  with `setAlliance()` + `initialize()`, a default drive command, a `RunCommand` for reads, one `SequentialCommandGroup`
  per auto built from `InstantCommand` / `WaitCommand` / `ParallelCommandGroup`, and `*Cmd` command classes.
- Dependencies (per SolversLib docs at 0.3.6; verify against the quickstart):
  - `org.solverslib:core:0.3.6`, `org.solverslib:pedroPathing:0.3.6`, optional `photon:0.3.6`
  - Maven repo: `https://repo.dairy.foundation/releases`
  - **Cannot coexist with FTCLib.** Pedro 3.0.0+ (`com.pedropathing`, hub/telemetry modules) is installed separately.
  - Version map: SolversLib 0.3.6+ ↔ Pedro 3.0.0+; 0.3.3-0.3.5 ↔ Pedro 2.x; 0.3.2 ↔ 1.0.9; 0.3.1 ↔ 1.0.8.
  - Package: `com.seattlesolvers.solverslib.*`. Quickstart: `github.com/FTC-23511/SolversLib-Quickstart`.

### 3.2 SolversLib behaviors that bite (from the docs; verify when in doubt)
- `CommandScheduler` is a singleton. Each `run()`: (1) subsystem `periodic()`, (2) poll triggers, (3) `execute`+`isFinished` on scheduled
  commands, (4) schedule default commands. `initialize()` runs at `schedule()` time.
- Commands: `initialize / execute / end(interrupted) / isFinished` (default false). `addRequirements(...)`. `schedule(false)` = uninterruptible.
- `SubsystemBase` auto-registers. `setDefaultCommand(cmd)`. Keep default commands and `periodic()` consistent with each other.
- Groups: `SequentialCommandGroup`, `ParallelCommandGroup` (ends when all end), `ParallelRaceGroup` (ends when any ends),
  `ParallelDeadlineGroup`. Requirements = union of members; a parallel group cannot hold two commands requiring the same subsystem.
- **A command instance placed in a group cannot be scheduled elsewhere or added to a second group (throws and crashes).**
  Build fresh instances with factory methods. `ScheduleCommand` branches off a group.
- Utility commands: `InstantCommand`, `RunCommand`, `StartEndCommand`, `WaitCommand`, `WaitUntilCommand`, `ConditionalCommand`,
  `RepeatCommand`, `RetryCommand`, `LambdaCommand`, `CallbackCommand`, `Commands`.
- Triggers: `GamepadEx(gamepad1).getGamepadButton(GamepadKeys.Button.X)` then `whenPressed` / `whileHeld` / `whenReleased` /
  `toggleWhenPressed` / `cancelWhenPressed`. Declare once in `initialize()`. `Trigger.and/or/negate` return `Trigger`.
- **`CommandOpMode`:** only `initialize()` is mandatory (create hardware, `schedule(...)`, `register(...)`). The scheduler is reset per
  OpMode, so **never keep static/persistent subsystems.** Override `run()` and call `super.run()`; `preRun()` runs once after Play.
  Prefer `CommandOpMode` over SolversLib's `Robot` class (it shares subsystems across OpModes).
- **Pedro commands (0.3.6):** you must call `follower.update()` every loop yourself (e.g. in `run()` **before** `super.run()` so commands
  see a fresh pose). `FollowPathCommand` takes a `Path` (Pedro 3 removed `PathChain`; chain with `Paths.path(a,b,...)`); the docs page
  still shows `PathChain` and is stale. `HoldPointCommand` finishes when the follower is not busy (a far pose can "finish" while
  still moving). `TurnCommand`/`TurnToCommand` hold position with a new heading.
- Hardware: `MotorEx`, `ServoEx`, `CRServoEx` have power caching (`cachingTolerance` default 0.0001, effectively off; use ≥ 0.01 to
  see an effect). `AnalogAbsoluteEncoder` for Axon. Controllers: `PIDFController`, `SquIDFController`, `CascadeController`,
  `P2PController`. `Timing.Stopwatch/Timer/Rate`. `SolversLib Pose2d.mirror()` for alliance swap.
- Hard-won bug: an **intake default command blocked re-activation** of the intake in autos. Fix was removing the default
  command. Be careful about default commands that hold a subsystem requirement.

### 3.3 Repository layout (proposed)
```
TeamCode/src/main/java/.../
  config/        RobotConstants.java  (ONE place for tunables; see §5)
  field/         FieldGeometry.java   (HIVE/FLOWER/LZ/GARDEN poses, alliance mirroring), MatchClock.java
  math/          PURE Java, NO FTC imports: TurretGeometry, Ballistics, AngleUtil, filters  <- unit-testable
  subsystems/    DriveSubsystem, TurretSubsystem, ShooterSubsystem, IntakeSubsystem, VisionSubsystem, ...
  commands/      *Cmd classes (factory methods return fresh instances)
  opmodes/       auto/, teleop/, tuning/, test/
TeamCode/src/test/java/...   JUnit tests for math/ and field/
```
Keep FTC-SDK-dependent code thin. Put logic in `math/` so it can be proven without a robot.

### 3.4 Coding rules
- **No mutable `public static` state shared across OpModes.** The old repo's leaked static config (a vision offset left at 40 by one
  auto) was a real bug source. Constants are `final` or live in one dashboard-tunable config object that each OpMode
  explicitly reinitializes. Pose handoff AUTO→TELEOP goes through one explicit `MatchState` with a validity flag and reset.
- **Loop discipline:** bulk-read caching on every `LynxModule` (manual mode), clear the cache once per loop, `follower.update()` first,
  then commands. Avoid blocking calls and sleeps. Telemetry via Dashboard; keep driver-station telemetry small.
- **Units:** radians and inches internally (Pedro). Convert degrees only at the edge (Limelight `tx`/`ty` are degrees). Name variables
  with units (`angleRad`, `distIn`). Wrap angles explicitly; a limited-range turret must not blindly use shortest-path error.
- **Alliance:** one flag, set once in init. Mirror geometry in `FieldGeometry`, never with scattered `if (red)` in commands.
- **Match phase safety:** a single `MatchClock`/phase helper enforces §2.2 (zero outputs in the transition and after TELEOP,
  FLOWER logic after 60 s). Do not scatter timers.
- **Every actuator has a software limit and a safe-stop.** CR servos have no stop, so soft limits are mandatory.
- Prefer deterministic, loggable state machines over ad-hoc flags. Log inputs and outputs of each control loop so a failure can
  be replayed.

### 3.5 Vision and localization
- Limelight 3A (pipelines per alliance existed in the old repo), goBILDA Pinpoint odometry computer via Pedro.
- Use odometry as the primary pose; use vision as a **filtered correction**, not a replacement. The HIVE tags are on moving CELLS (§2.12).
- Latency matters: vision is old by the time you use it; do not close a fast loop on a raw single frame.

---

## 4. Turret / launcher design spec

Hardware intent: the turret servos run in **continuous-rotation (CR) mode** (two goBILDA servos), so there is **no built-in
position feedback**. A real output-side encoder is **mandatory**, not a nice-to-have.

### 4.1 Aiming math (pure function, unit-test it)
```
turretField  = robotXY + R(heading) * pivotOffsetRobot            // turret pivot is not at the odometry point
target       = cellMouthPose(allianceFlag, cellUp)                  // §4.4; NOT a fixed corner
virtualTarget= target - robotVelocityField * flightTime(dist)       // iterate 2-3x: dist depends on virtualTarget
bearingField = atan2(vt.y - turret.y, vt.x - turret.x)
turretAngle  = wrap(bearingField - heading - zeroOffset)            // robot-relative
choose angle+2πk inside [minLimit, maxLimit]; clamp at limits; add hysteresis near the wrap/dead zone
dist         = hypot(vt - turret)                                    // ALSO drives flywheel RPM and hood from the same number
```

### 4.2 Control (CR servo + encoder)
`power = kS*sign(e) + kP*e + kD*ė + kV*(-ω_robot)` with a deadband around zero.
- Error = target - measured, **no shortest-path wrap** (cable limits).
- Drive both servos from **one command, one loop** with a per-servo trim. Never run two loops. If the servos are mirrored, set directions
  once and verify with a test; mismatched directions fight through the gear.
- Feed forward the robot's angular velocity (turret must counter-rotate) and optionally the translation term
  `φ̇ = (dx·vy - dy·vx)/d²`.
- **CR zero-drift is a known issue** (the old repo had a "setPower(0) still moves" test). Measure the neutral bias; shift the PWM range
  (e.g. `CRServoImplEx`) or compensate; keep the deadband larger than any residual creep or it will hunt.
- Soft limits inside the hard stops; cut power at limits; use a limit switch or known hard-stop to **zero the encoder on init**
  (an incremental encoder's zero is meaningless otherwise, and a wrong zero also corrupts any vision orientation input).
- Mind backlash: put the encoder on the **turret output**, not the motor side, and keep an absolute encoder to ≤ 1 rev across the range.
- Measure **max slew rate** early. If the turret cannot out-turn the fastest robot rotation, ω feedforward will saturate and no tuning
  will fix lag.

### 4.3 Vision as a correction
- Treat `tx` as an error relative to a **measured constant** `txAtCorrectAim` (camera-to-launcher misalignment), not a "trim" hidden in
  the odometry path.
- Fold it in as a filtered, **integrating** bias with gating (valid tag, correct ID, small |tx|, recent frame). A non-integrating
  `gain*tx` correction removes only `gain/(1+gain)` of the error (the old repo's 0.45 left ~69% of it).
- Deadbands must hold the last correction, not zero it.

### 4.4 Which CELL is up? (state-dependent target) [INFERRED]
Maintain `cellUp ∈ {A, B}` for your alliance's HIVE. Sources, best first: (1) vision (tag cluster visibility/height), (2) a driver
override button, (3) tip counting as a weak fallback. The partner robot can tip the HIVE too. After a tip, **pause launching** until
the new CELL is up and the target is updated (§2.7). Unit-test the retargeting logic.

### 4.5 Tuning order
kS (ramp until the encoder moves) → kV (power vs. velocity) → kP → kD → feedforward on ω → vision gain.
Use Dashboard. Add a hold-test, a step-response test and a rotating-robot tracking test, each with pass criteria.

### 4.6 What to borrow / avoid from the reference repos (RevAmped-Decode-V2, FTC-23849-DECODE)
- Borrow: limit-switch zeroing; a `reached()` command that races the real condition against a predicted move time (so sequences can't hang);
  separate "slew" and "track" PIDF sets; a clean `ShooterMath`-style geometry class with a hood/RPM LUT.
- Avoid: mixing units inside feedforward (servo-ticks vs radians), a loop that claims to iterate but never feeds back, a pivot-offset that
  exists but is commented out, static startup positions for auto→teleop, vision-only aiming with no setpoint, and spamming pipeline
  switches every loop.

---

## 5. Constants registry (all `TODO(measure)` until the robot exists)

Keep **every** value below in `RobotConstants`, with a unit suffix and a comment saying how it was measured and when. Never inline a magic number.

| Constant | Unit | Status |
|---|---|---|
| Turret pivot offset from odometry point (x, y) | in | TODO(measure) |
| Turret zero offset vs. robot forward | rad | TODO(measure) |
| Turret ticks per radian (output-side encoder) | ticks/rad | TODO(measure) |
| Turret min/max angle (soft limits) | rad | TODO(measure) |
| CR servo neutral bias, kS, kV, kP, kD | - | TODO(tune) |
| Camera pose relative to turret/robot, `txAtCorrectAim` | in, rad | TODO(measure) |
| Launcher exit point relative to turret pivot | in | TODO(measure) |
| Flight-time model `t(dist)`, RPM/hood LUT | s, rpm | TODO(tune) |
| HIVE CELL mouth poses (per alliance, per CELL state) in Pedro coordinates | in | TODO(CAD + Pedro frame) |
| FLOWER, LOADING ZONE, GARDEN poses in Pedro coordinates | in | TODO(CAD + Pedro frame) |
| HIVE tip threshold (elements per tip) | count | TODO(measure on real HIVE) |
| Actuator budget: ≤ 8 motors, ≤ 8 servos | count | TODO(plan; turret CR uses 2 servos) |

---

## 6. Testing and verification protocol

1. **L0:** project compiles (`./gradlew :TeamCode:assembleDebug`; confirm the task names in this repo).
2. **L1 pure-math unit tests** (`./gradlew :TeamCode:testDebugUnitTest`; add JUnit as `testImplementation` if missing): angle wrap, limit
   selection, hysteresis, pivot-offset transform, virtual-target convergence, alliance mirror symmetry
   (blue result = mirrored red result), retargeting on CELL state, no discontinuities across a sweep of poses.
3. **L2 simulation:** a plant model of the turret with deadband, backlash, latency and CR zero-drift; run the controller against it,
   including a rotating robot, and assert tracking error bounds.
4. **L3 on-robot test OpModes** with printed PASS/FAIL (encoder zeroing, direction check for each servo, hold, step, tracking, slew rate,
   limit cutoff, transition/TELEOP-end zero-output). Log everything to Dashboard.
5. **L4 match rehearsal:** run a full 30 s AUTO + 8 s transition + 2:00 TELEOP script, both alliances, from each legal start,
   repeated. Record shot-by-shot results.
6. **"Bulletproof" is the user's call.** Report numbers (trials, success rate, worst-case error, conditions: battery level, alliance,
   start position) and name what you did *not* test. Pass criteria are proposed by you and approved by the user; do not invent them silently.

---

## 7. Lessons from the old repo ("Quantum-Coders-V2", DECODE) — do not repeat

- Turret ran **positional servos open-loop**; the encoder only fed telemetry and an `isAimed()` check; the PIDF object was created and never used.
- Per-zone trims of **-28° / -36.5°** and per-shot auto offsets (about -22° to -26.5°), plus vision offsets of 15-40°, hid a wrong geometry
  model. A real model needs ≈ 0° trim.
- The turret aimed at the **field corner** (144,144) / (0,144) while the shot-speed solver used a different goal point (≈ (132,138) /
  (133,133)). Two goal definitions in one system produced position-dependent error.
- `setAngleOffsetDegrees` wrote the same value to both the vision offset and the close trim, overwriting calibration and double-applying.
- The two turret servos were configured with **different directions in different files** (center-hold vs. calibrate vs. aiming).
- Public-static config mutated by OpModes leaked between OpModes.
- Encoder zero was set by a runtime button press and never persisted; harmless for positional servos, **fatal for CR mode**.
- CR zero-drift was already observed once; plan for it.

---

## 8. Open questions / verify list (resolve before relying on them)

1. Tip threshold and CELL mouth height (§2.7); read the CAD, measure a real HIVE.
2. AprilTag IDs per CELL (Fig 9-17) and whether the up/down CELL height difference is usable (§2.12).
3. Pedro coordinate frame ↔ manual's field orientation; all field poses (§2.3, §5).
4. G410 scope for POLLEN; any launch-zone rule; current G-rule numbers (they drift).
5. Allowed motors/servos and power limits in the current §12 (R503, R510).
6. Starting state of each HIVE (which CELL is up) per Fig 10-2.
7. Regional / Championship RP thresholds (TBA in Team Updates).
8. Whether SolversLib 0.3.6 / Pedro 3 are still the right pins after the next releases; check changelogs before upgrading.

---

## 9. References

- Game and season hub: `ftc.game` · Manual: `ftc.game/manual` · HTML: `ftc.game/cm-html` · Scoring calculator: `ftc.game/calculator`
- Team Updates: `https://ftc-resources.firstinspires.org/ftc/game/tu-latest` (every Thursday) · Q&A opened Sep 28, 2026
- SolversLib docs `https://docs.seattlesolvers.com` (+`.md`, `/llms.txt`) · Quickstart `github.com/FTC-23511/SolversLib-Quickstart`
- Third-party BIOBUZZ tools (unverified, useful for ideas): `Lazer2077/BIOBUZZ_Simulator`, `patricio-jijon/biobuzz-shooter-bench`
- Reference code (DECODE era): `RevAmped-Decode-V2`, `FTC-23849-DECODE`
