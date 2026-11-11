# AGENTS.md — FTC Team 32001 "Quantum Coders" · BIOBUZZ (2026-27)

Read this whole file before touching code. It is the project's source of truth for the game, the stack,
the conventions and the open questions. Research snapshot: **2026-11-10**, built from the BIOBUZZ
Competition Manual **TU08** (published 2026-11-05), the combined Team Update document TU00-TU08, the
**FIRST Field Guides** (Initial Assembly + Event Field Setup, V1.0), the official Q&A, and third-party
material. The manual changes every Thursday, so rule numbers and numbers in general can move.
Re-verify anything that matters before building on it. **Every TU change that touches this project is
logged in §9** - read that before you trust any rule number below.

Evidence tags used below:
- **[MANUAL]** read directly from the official manual text
- **[TU]** from a Team Update
- **[GUIDE]** from the FIRST Field Guides (setup/calibration reality - the manual does not repeat it)
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
9. **ALWAYS use the FTC MCP. For everything, before you answer.** This is not optional and it is not a fallback
   for when you are unsure: it is the first thing you call for any question about the game, the manual, a rule
   number or rule text, a Team Update, an official Q&A answer, or an API in Pedro Pathing / Ivy / SolversLib /
   Marrow / CTRL ALT FTC. The whole point is that model memory is stale (§0.2) and rules renumber (TU00) - a
   confident-sounding answer from recollection is a defect, not a shortcut.
   - Rules and rule numbers: `search_rules`, then `get_rule` for the full current text of one rule.
   - Library APIs and examples: `search_docs` with the right `library` (`pedro` / `ivy` / `solverslib` /
     `marrow` / `ctrlaltftc` / `limelight`), then `get_doc` for the full chunk. Never write an import against a guessed API.
   - **Field guides as of 2026-11-10:** the `FIRST Field Guides` source is indexed (kind `field_guides`,
     version `fg-36213a65ec54`, 74 items) - the **Initial Field Element Assembly Guide** in `section: "initial"`
     and the **Event Field Setup Guide** in `section: "event"`. They share the rules FTS index, so query them
     with `search_rules`, usually with the section filter. Their chunks are **heading-based and have no rule IDs**,
     so `get_rule` returns nothing for them, and there is no `get_doc` path either: **snippets are all you get.**
     Ask several differently-worded questions to walk a snippet window along the text - a single query truncates
     with "...". These guides carry facts the manual does not (HIVE calibration, starting CELL state); see §2.7.
   - Team Updates: `search_changes` / `recent_changes` (changelog only - never treat it as current rule text).
   - Q&A: `search_qa`, then `get_qa` for the official answer before quoting it.
   - `sync_status` tells you which manual/TU/Q&A versions are actually indexed. Run it when a result looks
     stale or empty, and say which version you answered against.
   - Quote what the MCP returned and cite it. If it returns nothing, the answer is "not found in the index",
     followed by what you would need to look up - **never** a substitute from memory, and never an invented
     rule number, dimension, tag ID or API. Say "I could not verify this" out loud.
   - Scope, honestly: the MCP indexes FTC rules, TUs, Q&A and third-party library docs. It does not contain this
     repository, our code, or gradle/dependency versions. Those still require reading the actual file - e.g.
     the dashboard coordinates were verified by reading `build.dependencies.gradle` and the published artifact,
     not by asking the MCP. "Use the MCP for everything" means for every external FTC fact, not as a replacement
     for reading code.

---

## 1. Team and project state

- Team **32001 Quantum Coders**; GitHub org `Quantum-Coders-32001`. The team has a website and Discord GitHub notifications.
- Language/tooling: Java, Android Studio , Sloth hot-reload, slothboard
  (Dairy Foundation FTC Dashboard fork), FTC Dashboard.
- Previous season (DECODE 2025-26): mecanum, PedroPathing, turret, flywheel shooter, hood regression, Limelight
  fine-aim. Results were mixed (see §7, the old repo "Quantum-Coders-V2"). Do not copy its architecture.
- Coding assistant setup: opencode with NVIDIA Nemotron 3 Ultra. Context is finite and the model can be wrong, so keep
  steps small, read files rather than recalling them, and run things.
- **Current build (2026-11-10).** Static-shooter mecanum robot, **no turret**. Two match teleops -
  `AutoAimRedTeleOp` / `AutoAimBlueTeleOp` - which rotate the chassis to aim at the raised CELL while the driver
  keeps translation, and a base `StaticShooterTeleOp` that owns the intake/transfer/shooter/gate directly. Two
  tuners: `PidfTuner` (flywheel speed loop) and `ShotMapTuner` (fills the distance -> velocity/hood regression).
  **Nothing hardware-side is verified**: no robot, no field, zero PIDF gains, `NaN` hood limits, empty shot arrays.
  §3.1 is the design rationale; §5 is what is and is not filled in.
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

**Field timer and audio cues** [MANUAL Table 9-1, checked at TU08]. The FIELD timer counts down from **2:30** at MATCH start,
so "1:00 on the timer" means 60 s left in TELEOP. Cues, in order: MATCH start "Cavalry Charge" · AUTO ends
**buzzer x3 at 2:00** · transition 0:08→0:01 "Drivers, pick up your controllers, 3-2-1" · TELEOP begins **3 bells
at 2:00** · **FLOWER ownership unlocked 1:00 "chime"** (this cell was `[TBD]` until TU04 filled it in) · final 20 s
train whistle · 0:00 three-second buzzer · MATCH stopped foghorn. Audio cues are a courtesy, not official markers;
the visual FIELD timer is the authority. Nothing here replaces a software `MatchClock`.

Software implications: run your own match clock from TELEOP start (the robot cannot see the field timer);
zero all actuator outputs for the entire transition and at TELEOP end; make AUTO end cleanly at 30 s. Triple
penalty risk on stray motion: G403 (transition), G404 (after TELEOP), and 10.5 Scoring says achievements scored
before the MATCH, during the transition, or after 0:00 "may be subject to penalties".

### 2.3 Field [MANUAL §9]
- 144 in × 144 in, 36 soft foam tiles (24 × 24 × 0.59 in). Illustrations are ±1 in; the 3D CAD is the official geometry.
- Red alliance area is on the **left** from the audience view. Field columns A-C = red side, D-F = blue side (matters for
  AUTO interference, G402).
- ALLIANCE AREA ≈ 97 × 54 in. LOADING ZONE ≈ 23 × 11 in (corner, belongs to the adjacent alliance). GARDEN ≈ 23 × 2 in,
  in the corners, opposite corners for the two alliances.
- 1 HIVE structure at field center (frame holds the red HIVE and the blue HIVE), 4 FLOWERS attached to the perimeter walls.
- **Pedro frame [PEDRO DOCS - `Pedro-Pathing/Docs`, `public/docs/fieldcoordinates.png`, read 2026-11-02].**
  Pedro's BIOBUZZ field diagram labels the four corners **(0,0)**, **(141.5,0)**, **(0,141.5)**, **(141.5,141.5)**
  and puts the HIVE structure centre at **(70.75, 70.75)**. So the box Pedro works in is **141.5 x 141.5 in, not 144** -
  consistent with the 144 in tile grid minus a ~1.25 in perimeter wall on each side
  [INFERRED: 144 - 2 x 1.25 = 141.5. Confirm in the CAD before hardcoding].
  **+x is to the right, +y is up**; heading is CCW with **0 deg = +x, 90 deg = +y, 180 deg = -x, 270 deg = -y**.
  In that diagram the **red HIVE sits left of centre (low x)** and the **blue HIVE right of centre (high x)**, so
  **red side = x < 70.75, blue side = x > 70.75**. This is the assumption every pose in `autos/` is currently built on.
- **Measured off that diagram, not dimensioned** [INFERRED, TODO(CAD)]: the red LOADING ZONE is a rectangle against the
  low-x wall at roughly **x 3-13, y 96-119** (the right shape for 11 in deep x 23 in long), and a thin red GARDEN strip
  sits in the low-x / low-y corner. Treat these as "roughly here", not as constants.
- **Still unverified:** which corner of the Pedro box corresponds to the manual's audience direction, and therefore
  whether red is x < 70.75 under *every* field setup. §8 item 3. **TODO.**
- Do not confuse the two frames: the manual's TILE columns A-F are a *setup* grid; Pedro is a continuous 141.5 in box.
  Nothing maps them automatically.

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
- **Tip threshold: the manual never says, but the Event Field Setup Guide does.** [GUIDE: Event Field Setup
  Guide V1.0, §12 "Hive Calibration", read via MCP 2026-11-10]. Every HIVE is calibrated to tip at
  **two** element combinations (§12.3 table): **`[8] POLLEN + [0] NECTAR`** and **`[3] NECTAR + [3] POLLEN`**.
  §12.2 confirms the mass ordering: "`[3] Pollen + [3] Nectar` has less mass than `[8] Pollen`". Using the [3P]
  masses in §2.4 (~0.055 lb per POLLEN, ~0.091 lb per NECTAR) that is **≈ 0.44 lb ≈ 200 g** - which is where the
  third-party ~200 g placeholder came from, now with a primary source. §12.4 also requires that a calibrated HIVE
  does **not** tip at `[3] NECTAR + [2] POLLEN`.
  **Caveat, and it matters:** this is a **field-staff calibration spec, not a rule**. A HIVE outside its
  tolerance is an ARENA FAULT (Q&A #46, answered 2026-11-10: "a HIVE that is not within the specifications in
  the Field Acceptance Checklist may fall under criteria for an ARENA FAULT"). Use 8 POLLEN / 3+3 as the design
  assumption for gearing and shot counting, not as a guarantee. `TODO(measure on a real HIVE)` still stands for
  the actual number on the field you practise on.
- When a HIVE tips, its contents spill. They must **hit the floor before any robot collects them** (G409, no catching).
- Raised-CELL mouth height is only in Figures 9-10/9-11 (3P estimates 41-59 in, default 51 in). **TODO(read CAD).**
  A robot must fit in 29 in tall (§2.11), so every HIVE shot is a lob. [3P/INFERRED]
- **Starting state - RESOLVED 2026-11-10** [GUIDE: Event Field Setup Guide V1.0, §11.1 "Hive Setup"]:
  > "At the start of each match, the Hives should be tipped with the Cell of the Red Hive on the 'Audience' side
  > of the field tilted up, and the Cell of the Blue Hive on the 'Scoring' side of the field tilted up, as shown.
  > Each upward tilted Cell has [3] Nectar in it."

  Cross-referenced with the tag map in §2.12, the raised CELL's tags at kickoff are therefore
  **red 34-37** (audience side) and **blue 42-45** (scoring/opposite side); after one tip they become
  **red 30-33** and **blue 38-41**. [MANUAL 9.9] + [GUIDE §11.1].
  This is a **field-setup** instruction, so a mis-set HIVE is a setup error, not something to design around -
  `HiveAim.selectRaisedCell`'s "upper cluster wins" read is the runtime check that agrees with it.
  The manual's own wording [MANUAL 10.3.1] is only the mnemonic "the CELL which 'points at' a FLOWER should be
  the one tilted down"; the per-alliance answer above comes from the field guide, not the manual.
  Also [MANUAL 10.3.1]: NECTAR in the starting CELL is placed against the CELL back wall, on the side nearest that
  alliance's ALLIANCE AREA - relevant if you plan a preload shot that relies on where the 3 NECTAR sit.
  **Still TODO:** the CELL mouths' field coordinates (§8 item 1) - no indexed text has them.
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
- **R503 [MANUAL, confirmed TU00, unchanged at TU08]:** at most **8 motors and 8 servos total**, across all mechanisms in all
  configurations (the servo cap was cut from 10 in TU00). CR servos count as servos. "All configurations" means the
  sum across every interchangeable mechanism you bring to an event - not per configuration. **Budget this early** (§5).
- **R504.C [MANUAL]:** servos may be modified **as the manufacturer specifies**, explicitly including "soft limits or
  modification for continuous rotation". **The CR-servo turret in §4 is legal.** R504.A also permits trimming the
  mounting bracket and output shaft/interface to fit; R504.B permits trimming leads and adding connectors/splices.
  Nothing else on an actuator may be modified.
- **No weight limit** [MANUAL R104: "There is no explicit weight limit"], but weight matters for speed and the field.
- **R501 Table 12-1** is the allowed-motor list (older drafts of this file called it "R510" - that number was wrong).
  WATTOS Stingray (WDM12) is on it, added in TU00. **R502 Table 12-2** is the servo table: rotational servo
  **≤ 8 W and ≤ 4 A at 6 V**; linear servo **≤ 1 A at 6 V**. Check a candidate servo against the Inspection Quick
  Reference before buying; if it is not pre-approved you must supply manufacturer specs.
- **Power gotcha [MANUAL R502]:** the REV Control Hub and Expansion Hub feed servos **5 V**. To get 6 V you need a
  goBILDA Servo Power Injector, REV Servo Power Module, Studica Servo Power Block or REV Servo Hub. A servo rated
  6-8.4 V may misbehave at 5 V. This decides your turret servo wiring, not just its firmware.

### 2.12 AprilTags [MANUAL §9.9, tag IDs confirmed; positions still unextracted]
- 36h11 family, **3.25 in** square tags, in clusters of 4 on a sticker on the **bottom face of each CELL** (facing the tiles,
  bottom edge toward field center). 4 CELLS, so 16 tags.
- **Known IDs [MANUAL 9.9, verified against TU07 on 2026-11-02, unchanged at TU08].** The manual lists them literally:
  **30, 31, 32, 33** = red CELL on the side of the FIELD **opposite the audience**;
  **34, 35, 36, 37** = red CELL on the **audience** side;
  **38, 39, 40, 41** = blue CELL on the **audience** side;
  **42, 43, 44, 45** = blue CELL on the side **opposite the audience**.
  So RED HIVE = 30-37, BLUE HIVE = 38-45, and the audience/opposite split is now manual text rather than a
  third-party guess. `HiveCellResolver` encodes this mapping. The 2026-10-21 "user-confirmed" version happened to
  be correct; the tag is now [MANUAL].
- **Which cluster is raised at kickoff - RESOLVED 2026-11-10.** Event Field Setup Guide V1.0 §11.1 puts the
  **red audience-side CELL up** and the **blue scoring-side CELL up** at the start of every match. Combined with
  the tag map above:

  | | up at kickoff | up after one tip |
  |---|---|---|
  | RED | **34-37** | 30-33 |
  | BLUE | **42-45** | 38-41 |

  So `HiveAim.selectRaisedCell`'s "highest tag row wins" read has a known starting expectation to be checked
  against during a match, not just an unverifiable guess. Field-setup error is possible (a mis-tipped HIVE), so
  the runtime read still owns the decision; the table is what it *should* return.
- The tags ride on a moving structure, so they are targeting aids, **not static localization landmarks.** [3P: ViDAR marks
  them `localization: false`]. A raised CELL's tag row is reportedly ≈ 9.4 in higher than a lowered one [3P], so height can
  tell you which CELL is up. **TODO(verify).**
- **This is also the test for §8 item 3** [INFERRED]: at kickoff the red 34-37 cluster and the blue 42-45 cluster are
  the high ones. Aim the camera at each HIVE, read which physical side of the field the high cluster is on, and that
  fixes the audience direction in Pedro's frame without waiting for the CAD.

### 2.13 Rules that constrain mechanisms and code
Rule numbers in this section were renumbered in TU00 (the G407/G412 merge into G406 moved things) — the numbers
below are the **TU08** text. Re-verify after any TU if you are quoting a number to a referee.

- **G304 [rewritten in TU07]** start correctly, eight conditions: (A) fully contained on your own alliance side —
  columns A-C red, D-F blue; (B) not attached to, entangled with or suspended from any field element; (C) touching
  the perimeter wall; (D) not contacting or inside a FLOWER scoring volume; (E) not in the LOADING ZONE; (F) inside
  the STARTING CONFIGURATION per R102/R103; (G) in contact with **exactly 4** POLLEN preloads, fully contained on
  your own side; (H) fully motionless after OpMode initialization.
- G305 select/INIT an OpMode; an AUTO OpMode must be selected **with the 30 s timer enabled**.
- G401 no drive-team interaction during AUTO. Only three exceptions (TU06 restated them): press start within a
  momentary margin, press stop, or act for safety.
- **G402 [rewritten in TU06] — no AUTO interference with the opposing alliance.** **Violation: MAJOR FOUL per MATCH,
  plus a YELLOW CARD if STRATEGIC.** Columns A-C are red, D-F are blue; each alliance has priority over the field and
  elements on its own side; driving into the opponent's side during AUTO "is a risky gameplay strategy that may be
  seen as STRATEGIC". Examples: controlling opponent-side elements, contacting an opponent on their side. Deflections
  that happen after bouncing off something else are likely fine. **This is the rule that punishes a long AUTO
  traverse — check every AUTO path against it before running.**
- G403/G404 powered motion of the robot **or any mechanism** is forbidden for the whole 8 s transition and after
  TELEOP ends until the Head Referee signals.
- G405 do not **deliberately** eject an element from the field, directly or by bouncing it off a field element or a
  robot. MAJOR FOUL per element. **TU08 added the safe harbour:** elements that leave the FIELD *during scoring
  attempts*, *as the result of a ROBOT blocking another ROBOT's scoring attempt*, or *as the result of
  ROBOT-to-ROBOT interactions* are **not** deliberate ejections and are **not** violations. That covers a deflected
  shot that sails out of bounds, which the old text left ambiguous.
- **R612 [TU08] — critical power paths must stay unmodified.** A CUSTOM CIRCUIT must not directly alter the power or
  control path between the battery and main switch, main switch and a power regulating device, any two power
  regulating devices, or a power regulating device and an actuator. **TU08 added:** any *dynamic* or
  *continuous-rotation* electrical interface along those paths (linear sliding contacts, **slip rings**) must be an
  **unmodified COTS item** - custom or fabricated ones are prohibited. Relevant to any turret or rotating mechanism
  that passes power across a rotating joint: a home-made slip ring is now explicitly illegal, a purchased one is
  fine. The §4 CR-servo turret does **not** cross a rotating electrical joint (the servos and the encoder both
  mount on the moving part and the leads flex), so it is unaffected - but a slip-ring upgrade path is closed
  unless it is COTS.
- **G407 — control at most 4 elements at once.** Violation: VERBAL WARNING; MAJOR FOUL + YELLOW CARD per MATCH if
  STRATEGIC. 5+ at once is scrutinised; a momentary 5 that you immediately reverse is explicitly fine, 6+ while
  moving to a scoring location is the textbook STRATEGIC example. **TU05 added: REPEATED 5+ is likely STRATEGIC
  even if you have a "keep 4, discard the rest" sorter.** Guards so elements cannot pile on top of the robot are
  called out as good practice. **Capacity <= 4, software refuses a 5th.**
- **G408 — do not control opponent NECTAR.** VERBAL WARNING; YELLOW CARD per MATCH if STRATEGIC. **TU05 added:**
  REPEATED control of opponent NECTAR is likely STRATEGIC **even if your sorter automatically spits it back out**.
  "It falls out by itself" is not a defence. Your intake must be able to tell colours apart, or you must accept the risk.
- **G409 — no catching tipped-HIVE spill.** A robot may not catch or deflect an element released by a tipped HIVE
  until it has touched something else. VERBAL WARNING; YELLOW CARD per MATCH if STRATEGIC. Intent: spill hits the
  TILE floor first. Parking under a HIVE waiting for a tip, or having a wide-open top that scoops falling elements,
  are the named STRATEGIC examples. Guards over the top of the robot make it obvious you are not catching.
- **G410 — no NECTAR into the FLOWER scoring volume until the last 60 s.** **MAJOR FOUL per NECTAR.** Per 10.5.2,
  early elements still *score* but are penalised, so this is purely a time gate, not a legality gate.
- G411 no STRATEGIC denial of opponent access to elements. MAJOR FOUL + YELLOW CARD per MATCH. Using your own
  elements is fine; a GARDEN is not a protected zone.
- **G412 — robots must be under control.** Violation (TU07 wording): **DISABLED + VERBAL WARNING**; DISABLED +
  YELLOW CARD per MATCH if it happens again during the event. Flailing outside the field or hitting field staff
  gets you switched off mid-match.
- **G415** prohibits specific interactions with ARENA elements — but **scoring elements are excepted**, so it is not
  a ball-handling rule. G416 enforces R105's expansion limits in-match.
- **G417 — HIVE.** Launching into the **upward-facing** CELL is the only legal way to cause a TIP. Explicitly *not*
  strategic: ramming the frame at speed, ramming it repeatedly, launching at the CELL's external faces, impeding an
  **opponent's** tip, and — importantly for us — **a miss that hits the outside of the CELL while genuinely aiming
  at the mouth**. Named strategic: directing shots at an opponent's HIVE to stop their tip.
- **G418 — FLOWER.** Enter elements only through the top; remove **only POLLEN, only through the bottom**. VERBAL
  WARNING; MAJOR FOUL + YELLOW CARD if STRATEGIC. Contacting the FLOWER while attempting a legal action, or knocking
  a scored POLLEN out while scoring another, is fine.
- **G419 / G420 — do not damage, tip or entangle opponents.** G419: VERBAL WARNING, MAJOR FOUL + YELLOW CARD per
  instance if STRATEGIC, **RED CARD if STRATEGIC and the opponent cannot drive**. TU07 added the gory examples:
  disconnecting internal wires, disconnecting the battery, or powering off an opponent through a well-protected
  switch are all RED CARD territory. G420 covers attach/tip/entangle. They are **mutually exclusive** — only the most
  punitive penalty applies.
- **G421 — 3-count on PINS.** Pinning is preventing an opponent's movement by contact, direct or transitive. Count
  stops once the robots separate by >= 2 ft for > 3 s, or either robot moves 2 ft from the pin point for > 3 s, or
  the pinner gets pinned. MAJOR FOUL per instance **plus an additional MAJOR FOUL every 3 s uncorrected**.
- **G426 / G427 / G428 are human rules.** G426: drive teams may enter NECTAR only once per own HIVE tip, or all
  remaining at <= 60 s — **MINOR FOUL per NECTAR**. G427: NECTAR goes in through the LOADING ZONE and must contact
  the TILE there before touching a robot or field element. G428: humans may not remove elements from the field.
  G423/G424/G425 cover controllers-off-limits, drive coaches not touching elements, and reach.
- **No launch-zone restriction exists in TU08 §11.4 (G401-G428).** DECODE's launch-zone rule did not carry over.
  [MANUAL, checked against TU08 on 2026-11-10] That means you may launch from anywhere on the field — but see G402 for AUTO.
- **"STRATEGIC" is a defined term [TU00]:** actions done for competitive advantage, including deliberate or reckless
  acts with foreseeable consequences. Accidental or unforeseeable acts can never be STRATEGIC. Most penalties
  escalate only when a referee judges STRATEGIC.
- Violation scale: VERBAL WARNING, MINOR FOUL (5 to opponent), MAJOR FOUL (20 to opponent), YELLOW CARD, RED CARD (DQ).
  Duration terms: MOMENTARY < ~3 s, CONTINUOUS > ~10 s, REPEATED = more than once in a match.

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
- **SolversLib 0.3.6 + Pedro Pathing 3.0.x + Ivy 1.1.1.** Ivy was dropped, then **brought back on 2026-11-02** - autos
  are written against **Pedro Ivy** (`com.pedropathing.ivy.*`), not SolversLib's command classes. Ivy is Pedro's own
  recommended control-flow layer for pathing and ships with built-in Pedro integration, so one scheduler owns both the
  drive and the mechanisms. Consequence: **everything in §3.2 about `CommandScheduler`, `SequentialCommandGroup`,
  `InstantCommand`, `GamepadEx` and `CommandOpMode` is SolversLib lore that does not apply to the Ivy autos.** §3.2b
  is the Ivy equivalent. Target structure: one `LinearOpMode` per auto, an `AutoBase` with `setAlliance()` +
  `initialize()`, and per-auto `sequential(...)` compositions of Ivy commands.
- **2026-11-02: the TELEOP is deliberately one file.** `teleop/StaticShooterTeleOp` extends plain `OpMode` and owns its
  hardware directly (no subsystems, no commands, no `GamepadEx`). It uses plain `OpMode`, not `LinearOpMode`, because
  `LinearOpMode.stop()` is final while `OpMode.stop()` is the overridable end-of-run hook (verified in RobotCore 12.0.0
  bytecode: `internalRunOpMode()` calls `stop()` after the last loop, and telemetry is updated after every user callback).
  All `subsystems/` and `commands/` classes were deleted on 2026-11-02. The flywheel motor setup and control law
  live as public statics on the teleop (`configureFlywheelMotor`, `flywheelPower`) and are called by
  `PidfTuner`, so bench tuning and match behaviour are one implementation and cannot drift.
- **2026-11-09: every bench tuning OpMode lives in `tuners/`.** `PidfTuner`: one target speed,
  live `kP/kI/kD/kF` from a dashboard `@Config` class, error on screen. It calls
  `StaticShooterTeleOp.flywheelPower`, so a gain tuned on the bench is the gain that flies. A `@Config` static class
  is the sanctioned exception to §3.4: it is only ever read by the tuner, never by the match teleop.
- **2026-11-10: second tuner, `tuners/ShotMapTuner`.** Collects `(distance, achieved velocity, hood position)`
  samples and prints the `SHOT_DISTANCES_IN` / `SHOT_VELOCITIES_TPS` / `SHOT_HOOD_POS` arrays to paste into
  `RobotConstants`. It records the **achieved** velocity, not the setpoint, and it **refuses** a sample when the wheel
  is not at speed, when the target changed <800 ms ago, or when the Limelight range is stale (>250 ms) or outside
  20-200 in. That gate is the point of the tool: a mid-spin-up shot leaves the barrel at a speed the fit cannot name,
  and a stale-but-finite range silently poisons the dataset. **Run `PidfTuner` first** - with `LAUNCH_K*` all 0 the
  wheel never reaches target and every record is refused.
- **2026-11-10: distance -> (velocity, hood) is a least-squares regression, not a lookup table.** This replaced
  `ShotTable`, which is deleted.
  - `subsystems/ShotRegression.java` - polynomial least squares, **centred** (`u = x - mean(x)`) because distances
    live in 30-90 in and raw `x^4` in the normal equations loses precision; the coefficients are converted back
    so `predictRaw` is in real inches. It refuses to go quadratic below 4 points (3 points fit a parabola exactly
    and prove nothing), clamps `predict` to the measured band and reports `isExtrapolating`, and returns `null`
    rather than throwing.
  - `subsystems/ShotMap.java` - the two fits the teleop queries, `velocityTps(d)` / `hoodPos(d)`. Velocity and hood
    are fitted **independently** (user's call, and it is the right one: they are different physical relations).
    Empty or mismatched arrays produce an inert map that answers `NaN`, so a half-filled constant cannot take the
    OpMode down at INIT - `new ShotTable(...)` used to throw `IllegalArgumentException` there.
  - `ShotMapTuner` fits at record time so you can see the law and its residuals before pasting.
  - Degree is `SHOT_FIT_MAX_DEGREE = 2`. If the samples come back scattered, drop it to 1 - a line that admits it is
    a line beats a parabola that hides the scatter.
- **2026-11-10: firing lives in `subsystems/ShotSequence.java`, one implementation.** Gate servo + both transfer
  motors, driven by one `update(trigger)` call. The match teleop and `ShotMapTuner` both call it, so a sample is
  taken through the identical gate timing and transfer power as a real shot. Same rule as `flywheelPower`: a tuner
  that reimplements the mechanism measures a mechanism that does not exist.
- **2026-11-10: auto-aim is chassis rotation, not a turret.** `teleop/AutoAimTeleOpBase` (with
  `AutoAimRedTeleOp` / `AutoAimBlueTeleOp` subclasses pinning Limelight pipeline 0 / 1) holds the right bumper to
  rotate the drivetrain toward the raised CELL while the driver keeps translation. Pure proportional turn law +
  deadband + slew limit, **no D term** - the old repo's D-term hunting on a slow loop is why. Range comes from
  `getTargetPoseCameraSpace()` rotated by the mount pitch (`CAMERA_PITCH_UP_DEG`), not from raw `z`. The raised
  CELL is the upper tag cluster by `ty`, which survives a partner-caused tip with no comms (§4.4).
- **Kept on purpose, not dead:** `subsystems/HiveCellResolver.java` (the alternative roll-based up-CELL read - not
  wired in, but it is the comparison for `HiveAim.selectRaisedCell`), `subsystems/PinpointUtil.java` (unused
  `recalibrate()` / `resetPosAndIMU()` - the obvious next thing needed), and `pedro/procedures/*` (Pedro's
  `TunerScanner` auto-discovers `Procedure` subclasses, so they are not orphaned code even though nothing in this
  repo references them).
- Dependencies - **this list is what `build.dependencies.gradle` actually declares** (read 2026-11-02; treat that file
  as the authority, not this list):
  - `org.solverslib:core:0.3.6` and `org.solverslib:pedroPathing:0.3.6`
  - **`com.pedropathing.ivy:pedro:1.1.1`** - Ivy, the command / control-flow layer
  - `com.pedropathing:revhub:3.0.2`, `com.pedropathing:tuning:1.0.3`
  - **`com.acmerobotics.slothboard:dashboard:0.3.2+0.6.0`** - FTC Dashboard, Slothboard fork (added 2026-11-09).
    Version format is `<Sloth>+<Dashboard>`; it resolves from `repo.dairy.foundation` and pulls
    `dev.frozenmilk.sinister:Sloth:0.3.2` as a runtime dependency. Packages are the upstream ones
    (`com.acmerobotics.dashboard.FtcDashboard`, `...config.Config`, `...telemetry.MultipleTelemetry`), verified
    against the published AAR. Do **not** also add upstream `com.acmerobotics.dashboard:dashboard` - same packages,
    duplicate classes.
  - **Sloth hot-reload (added 2026-11-09, fixed 2026-11-09):**
    - Root `build.gradle`: `buildscript { repositories { maven { url "https://repo.dairy.foundation/releases" } }; dependencies { classpath "dev.frozenmilk:Load:0.3.2" } }`
    - `TeamCode/build.gradle`: `apply plugin: 'dev.frozenmilk.sinister.sloth.load'`
    - `build.dependencies.gradle`: `implementation 'dev.frozenmilk.sinister:Sloth:0.3.2'` (compile-time — required by the Load plugin; Slothboard only pulls it at runtime)
    - Android Studio run configs: `deploySloth` and `removeSlothRemote` (in a `Sloth` folder in the run config dropdown).
    - To deploy: run `deploySloth` (or the run config). To wipe old Sloth-loaded code: run `removeSlothRemote`.
    - **Without the Load plugin and the Sloth run configs, Slothboard's `/dash` will not load.** Verified: the build compiles and the tasks exist; the `deploySloth` task fails only at the `adb` step because no robot is connected.
  - Maven repos: `https://repo.dairy.foundation/releases`, `mavenCentral()`, `google()`
  - **Cannot coexist with FTCLib.** Pedro 3.0.0+ (`com.pedropathing`) is installed separately from SolversLib.
  - Version map: SolversLib 0.3.6+ ↔ Pedro 3.0.0+; 0.3.3-0.3.5 ↔ Pedro 2.x; 0.3.2 ↔ 1.0.9; 0.3.1 ↔ 1.0.8.
  - Packages: `com.seattlesolvers.solverslib.*`, `com.pedropathing.*`, `com.pedropathing.ivy.*`.
    SolversLib quickstart `github.com/FTC-23511/SolversLib-Quickstart`; Ivy docs `pedropathing.com/docs/ivy`.

### 3.2 SolversLib behaviors that bite (applies only where you actually use SolversLib, not in the Ivy autos)
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

### 3.2b Ivy behaviors that bite (Pedro Ivy 1.1.1 - this is what the autos use)
Docs: `https://pedropathing.com/docs/ivy` - pages are Installation, What are commands?, Requirements and Priorities,
Command Builder, Command Compositions, Utilities and Decorators, Class API, Scheduling and OpMode use,
Pedro Commands, Example Repos. Read the page before using an Ivy API you have not used here.

- **`Scheduler` is a static class, not an object.** You never construct it: `Scheduler.schedule(cmd)`,
  `Scheduler.execute()`, `Scheduler.cancel(cmd)`, `Scheduler.reset()`, `Scheduler.isRunning(cmd)`,
  `Scheduler.isScheduled(cmd)`. Static imports exist (`import static com.pedropathing.ivy.Scheduler.schedule;`).
- **The scheduler does not run itself.** Call `Scheduler.execute()` **exactly once per loop iteration**. One call =
  one cycle: executes every running command, checks completion, starts queued commands whose requirements freed up,
  resumes suspended ones.
- **Call `Scheduler.reset()` at the top of `runOpMode()`.** Because the scheduler is static, commands survive between
  OpModes. This is the Ivy equivalent of §3.4's "no shared mutable static state" and it is mandatory, not optional.
- **Commands run in PARALLEL by default.** Scheduling two commands does not chain them; ordering is explicit via
  `sequential(...)`. This is the most likely source of "why did my auto do that" bugs.
- **Building commands** - either the builder
  `Command.build().setExecute(...).setDone(...).setEnd(endCondition -> ...).requiring(...)`, or implement the
  `Command` interface directly. `requiring(...)` declares a requirement; the scheduler uses requirements to resolve
  conflicts and queue commands. The exact rules are on the **Requirements and Priorities** page - read it before
  designing two commands that touch the same mechanism. [page title/behaviour from the docs index; exact semantics
  not transcribed here]
- **Compositions:** `sequential(...)` and friends from `com.pedropathing.ivy.groups.Groups`. Utility/decorator
  builders (`instant(...)`, `waitMs(...)`) come from `com.pedropathing.ivy.commands.Commands`.
- **Pedro commands** live in `com.pedropathing.ivy.pedro.PedroCommands`: `follow(follower, path)` is the
  path-following command (usage confirmed in this repo's autos). You still call `follower.update()` yourself every
  loop, before `Scheduler.execute()`.
- **Ivy is not SolversLib.** `SequentialCommandGroup`, `InstantCommand`, `CommandScheduler`, `SubsystemBase`,
  `GamepadEx` and `CommandOpMode` are all SolversLib types and are **not** what the Ivy autos use. Do not mix them
  in one OpMode.
- **Example repos:** `pedropathing.com/docs/ivy/example-repos`. Read one before inventing structure.

### 3.3 Repository layout (actual, as of 2026-11-10)
```
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/
  config/     RobotConstants.java         (ONE place for tunables; see §5)
  subsystems/ HiveAim, ShotRegression, ShotMap, ShotSequence, ivyCommands   (NOT FTC subsystems - plain helpers)
              HiveCellResolver, PinpointUtil                                (kept, not wired in - see §3.1)
  teleop/     StaticShooterTeleOp (base)  AutoAimTeleOpBase + Red/Blue subclasses
  tuners/     PidfTuner, ShotMapTuner
  autos/      Ivy autos (TwoTipPark, TwopointFiveTipPark)
  pedro/      Constants, Tuning, procedures/* (TunerScanner auto-discovers these)
TeamCode/src/test/java/org/firstinspires/ftc/teamcode/subsystems/   JUnit, no FTC imports
```
`subsystems/` is a **namesake, not a command framework**: the classes there are plain Java helpers with no
`SubsystemBase`, no requirements and no scheduler. `ShotRegression` and `ShotMap` import nothing from the FTC SDK at
all, which is why they can be unit-tested (§6 L1). Keep new logic in that shape.
**History worth knowing:** at `HEAD` the same helpers lived in `field/` (`HiveCellResolver`) and `math/`
(`LinearFit`, `ShotTable`). Those two packages were dissolved into `subsystems/` on 2026-11-10 and `LinearFit` /
`ShotTable` were then deleted outright (§3.1). Do not recreate `field/` or `math/` - "pure math" is a property of
a class, not a directory.

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
- **Pipelines switch by index only** (`limelight.pipelineSwitch(0)`); the names in the Limelight UI are cosmetic and
  are not what the API takes. Red teleop uses **index 0**, blue uses **index 1**.
- **Limelight API actually used here** (verified against the SDK, not recalled): `Limelight3A`
  (`setPollRateHz`, `start`, `pipelineSwitch`, `getLatestResult`, `isConnected`), `LLResult.getFiducialResults()`,
  `FiducialResult.getFiducialId()/getTargetXDegrees()/getTargetYDegrees()/getTargetPoseCameraSpace()`, and
  `Pose3D.getPosition()` returning `Position{x, y, z, unit}` (call `.toUnit(DistanceUnit.INCH)`).
- **Camera space:** X+ right, Y+ down, Z+ **out of the lens**. To turn that into a horizontal range with the camera
  pitched up by `p`, rotate about the camera X axis: `forward = y*sin(p) + z*cos(p)`, then `dist = hypot(x, forward)`.
  Using raw `z` instead is wrong by a factor of `cos(p)` and gets worse as the camera pitches up.
- **A constant range-scale error cancels.** If `CAMERA_PITCH_UP_DEG` is wrong, the same wrong factor multiplies the
  range at **fit** time and at **shoot** time, so the fitted `v(d)` and the queried `v(d)` are consistent and the
  shots still land. **Consequence: a high R² is not evidence the model is right** - it is evidence the data is
  smooth. The cancellation only breaks if the camera geometry changes between tuning and a match (bent mount,
  re-aimed camera), so lock the camera down and re-run the tuner after touching it.
- Use odometry as the primary pose; use vision as a **filtered correction**, not a replacement. The HIVE tags are on moving CELLS (§2.12).
  They must **never** be fed to a localizer as landmarks.
- Latency matters: vision is old by the time you use it; do not close a fast loop on a raw single frame.

---

## 4. Turret / launcher design spec

> **STATUS 2026-11-10 - this section is partly design, not code.** The **turret does not exist in the current
> build.** Auto-aim is done by **rotating the chassis** (`teleop/AutoAimTeleOpBase`), so §4.1-§4.2's turret geometry
> and CR-servo loop are a *future* option, kept because it is the design if a turret is ever added. What **is**
> implemented: the state-dependent target choice (§4.4), the distance -> (velocity, hood) law (§3.1 / §5), and the
> single-implementation firing sequence (`ShotSequence`). Read §3.1 before believing anything below describes code.

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

### 4.4 Which CELL is up? (state-dependent target)
**Implemented** in `subsystems/HiveAim.selectRaisedCell`: take the **median `ty`** of the tags in each cluster and
pick the **higher** one (smaller `ty`, since Limelight `ty` grows downward). That needs no tip counting, no comms and
no driver input, and a partner-caused tip just changes which cluster is higher. **Starting expectation**
[Event Field Setup Guide V1.0 §11.1, 2026-11-10]: red **34-37** up / blue **42-45** up, flipping to **30-33** /
**38-41** after one tip. Use that table to sanity-check the read at INIT, not as the input.

Design notes that still hold:
- Sources, best first: (1) vision (tag cluster height), (2) a driver override button, (3) tip counting as a weak
  fallback. The partner robot can tip the HIVE too, which is exactly why (3) is the fallback and not the source.
- After a tip, **pause launching** until the new CELL is up (§2.7) - the target moves ~18.8 in and changes height.
- A median, not a mean: one badly-detected tag in a 4-tag row should not drag the decision.
- **TODO:** the up/down tag-row height difference is third-party-sourced (~9.4 in) and unverified (§2.12). The
  *ordering* is what the code relies on, not the magnitude, so a wrong magnitude does not break it.

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

## 5. Constants registry (all `TODO(measure)` / `TODO(tune)` until the robot exists)

Keep **every** value below in `RobotConstants`, with a unit suffix and a comment saying how it was measured and when. Never inline a magic number.

**Inert values are load-bearing.** `RobotConstants` uses `NaN` for "not configured", `0` for "don't run", and
`{}` for "no data", and the code checks for those before touching hardware. That is why an unfilled hood constant
cannot command a servo at INIT. Preserve the convention: a new constant starts inert, and the reader must handle
"inert" as a normal case rather than an error.

### 5.1 Implemented in code today

| Constant | Unit | Status |
|---|---|---|
| `LAUNCH_KP/KI/KD/KF` | - | **all 0.0, TODO(tune)** with `PidfTuner`. `kF` multiplies the *setpoint* in SolversLib, so it absorbs `kS`; there is no separate `kS` slot. Keep `kI`/`kD` at 0 - an I term turns shot-load dips into windup. |
| `LAUNCH_VEL_TOLERANCE_TPS` | tps | 50.0, telemetry/"at speed" gate only. TODO(tune) |
| `DEFAULT_LAUNCH_VEL_TPS` | tps | 0.0 = launchers stay off when there is no distance. TODO(measure) |
| `SHOT_DISTANCES_IN` / `SHOT_VELOCITIES_TPS` / `SHOT_HOOD_POS` | in / tps / servo pos | **`{}` - no data.** Filled by `ShotMapTuner`, same length and order. §3.1 |
| `SHOT_FIT_MAX_DEGREE` | - | 2. Drop to 1 if the samples are scattered. Do not raise it to hide scatter. |
| `GATE_REST_POS` / `GATE_FIRE_POS` | servo pos | **0.75 / 0.55**, user-confirmed 2026-11-10. Rest holds the balls, fire releases them. |
| `HOOD_MIN_POS` / `HOOD_MAX_POS` / `HOOD_DEFAULT_POS` | servo pos | **`NaN` - hood is never commanded in a match.** The tuner still moves it, so the hood half of the regression is invisible until these are measured. **TODO(measure) - this is a blocker, not a nicety.** |
| `CAMERA_PITCH_UP_DEG` | deg | **40.0**, derived from the user's "130 deg from the bottom corner" (130 - 90). TODO(measure): if that 130 was from the *front* face instead, every range is wrong. See §3.5 - a constant error cancels for the fit, but it breaks the mm-level mental model. |
| `CAMERA_YAW_OFFSET_DEG`, `CAMERA_FORWARD_OFFSET_IN` | deg, in | 0.0, TODO(measure) |
| `AIM_KP` | power/deg | 0.02 (50 deg error = full power). TODO(tune) |
| `AIM_DEADBAND_DEG` | deg | 1.0. TODO(tune) |
| `AIM_MAX_TURN` | power | 0.8 - never a full-power spin. TODO(tune) |
| `AIM_SLEW_PER_SEC` | power/s | 2.5, loop-rate independent. TODO(tune) |
| `AIM_TARGET_TIMEOUT_MS` | ms | 150 - hold the last frame after the tags vanish, so a latency spike does not snap the chassis back to manual mid-shot. TODO(tune) |
| `AIM_CLUSTER_GAP_DEG` | deg | 3.0 `ty` gap that means "two different CELLs in frame". TODO(tune with real frames) |
| `PIPELINE_RED` / `PIPELINE_BLUE` | index | 0 / 1. Pipelines switch by index only. |
| `RED_TAG_IDS` / `BLUE_TAG_IDS` | ids | 30-37 / 38-45. A **filter**, so an opponent's tags can never drive our aim. |
| `LEFT_SIDE_REVERSED` | bool | true, copied from the old teleop. TODO(verify on robot) |
| Pedro field bounding box (min/max x and y) | in | **141.5 x 141.5**, origin at a corner, +x right / +y up [PEDRO DOCS]. TODO(confirm against CAD) |
| Alliance split line, Pedro x (red low-x / blue high-x) | in | **70.75** [PEDRO DOCS: HIVE structure centre]. TODO(confirm it is the alliance boundary and not just the HIVE centre) |
| HIVE CELL mouth poses (per alliance, per CELL state) in Pedro coordinates | in | TODO(CAD). No indexed text has them - not the manual, not either field guide (§8 item 1). |
| FLOWER, LOADING ZONE, GARDEN poses in Pedro coordinates | in | TODO(CAD). Off the Pedro diagram: red LZ roughly x 3-13, y 96-119. |
| Robot chassis footprint (for pose/wall clearance checks) | in | TODO(measure). Every pose in `autos/` currently assumes a robot much smaller than 18 x 18. |
| HIVE tip threshold (elements per tip) | count | **8 POLLEN, or 3 NECTAR + 3 POLLEN** (≈200 g) per Event Field Setup Guide §12.3 - a build spec, not a rule (§2.7). `TODO(measure on your practice HIVE)` |
| Actuator budget: ≤ 8 motors, ≤ 8 servos | count | TODO(plan). Current build: 4 drive + 1 shooter + `lt` + `rt` + `roll` = 8 motors, **at the cap**; gate + 2 flower + hood = 4 of 8 servos. No room for a turret without dropping something. |
| Ball path | names | **`lt` + `rt` and nothing else.** A drives both at `TRANSFER_POWER = 1.0`, X at -1.0. The shot sequence overrides both while firing. |
| `roll` (the team's "notshooter") | motor / power | Hardware name **`roll`**, constant **`ROLL_POWER = 0.4`** for the whole match, commanded after Play and zeroed in `stop()` for G404. It is **not** the intake and **not** the transfer - never pulsed, reversed, or part of the shot sequence. Corrected 2026-11-10 after this file and the teleop wrongly treated it as the intake. Optional in the teleop, so a missing name degrades to telemetry instead of a dead OpMode. |

### 5.2 Turret design only - not implemented

These belong to §4, which describes a turret that does not exist in the current build. Do not look for them in
`RobotConstants`; they are listed so the design stays complete if a turret is added.

| Constant | Unit | Status |
|---|---|---|
| Turret pivot offset from odometry point (x, y) | in | TODO(measure) |
| Turret zero offset vs. robot forward | rad | TODO(measure) |
| Turret ticks per radian (output-side encoder) | ticks/rad | TODO(measure) |
| Turret min/max angle (soft limits) | rad | TODO(measure) |
| CR servo neutral bias, kS, kV, kP, kD | - | TODO(tune) |
| Camera pose relative to turret/robot, `txAtCorrectAim` | in, rad | TODO(measure) |
| Launcher exit point relative to turret pivot | in | TODO(measure) |
| Flight-time model `t(dist)` | s | TODO(tune) |

---

## 6. Testing and verification protocol

1. **L0:** project compiles. This repo's tasks are
   `./gradlew :TeamCode:assembleDebug :TeamCode:testDebugUnitTest --console=plain`. Green as of 2026-11-10.
2. **L1 pure-math unit tests** - `TeamCode/src/test/java/.../subsystems/ShotRegressionTest.java`, **18 tests, all
   passing as of 2026-11-10**, covering the regression and `ShotMap`:
   - exact line and exact quadratic recovered, incl. between-sample checks after the centring round-trip;
   - degree rules: n=1 constant, n=2 and n=3 line, n>=4 quadratic, `degree 0` honoured;
   - `predict` clamps to the measured band, non-finite query returns `NaN` (not a clamp), `isExtrapolating` reports;
   - bad input returns `null`, not an exception: null/empty/mismatched arrays, non-finite samples, all-identical x;
   - `ShotMap`: empty arrays are inert, mismatched arrays degrade instead of throwing, velocity and hood fit
     independently, a missing hood fit leaves velocity usable;
   - a sqrt-shaped velocity curve is tracked by a quadratic inside the band.
   **Read the actual XML, do not trust "BUILD SUCCESSFUL":** Gradle reports success when no tests ran.
   `TeamCode/build/test-results/testDebugUnitTest/*.xml` has `tests/failures/errors` per class.
   Still missing here: angle wrap, limit selection, hysteresis, `HiveAim` transform and `selectRaisedCell`
   retargeting. Those are `HiveAim`'s job and it is **not** unit-tested yet.
3. **L2 simulation:** a plant model of the mechanism with deadband, backlash, latency and (if a turret is ever fitted)
   CR zero-drift; run the controller against it, including a rotating robot, and assert tracking error bounds.
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

1. **CELL mouth field coordinates and height** (§2.7) — no indexed text has them. Event Field Setup Guide §9.1
   installs the HIVE by removing the four centre TILES and aligning the frame foot-bar holes onto elevator bolts,
   and §12.1 defers the controlled dimensions to the **Field Acceptance Checklist**, which both field guides still
   list as *"coming soon"*. **TODO(read the 3D CAD, or tape-measure a real HIVE).** The **tip threshold is no
   longer open** - Event Field Setup Guide §12.3 calibrates every HIVE to tip at `[8] POLLEN` and at
   `[3] NECTAR + [3] POLLEN` (≈200 g); see §2.7.
2. ~~AprilTag IDs per CELL (Fig 9-17)~~ — **RESOLVED 2026-11-02 against MANUAL 9.9**: red 30-37 (30-33 opposite
   audience, 34-37 audience side), blue 38-45 (38-41 audience side, 42-45 opposite audience). Still open: whether
   the up/down CELL height difference is usable (§2.12).
3. ~~Pedro coordinate frame~~ — **frame RESOLVED 2026-11-02** from Pedro's `fieldcoordinates.png`: a 141.5 x 141.5
   box, origin at a corner, +x right, +y up, heading CCW (0 = +x, 90 = +y), HIVE structure centre (70.75, 70.75),
   red HIVE at low x and blue HIVE at high x. **Still open:** which corner is the audience direction, and every
   concrete field pose (§2.3, §5).
4. ~~G410 scope for POLLEN; any launch-zone rule; current G-rule numbers~~ — **RESOLVED 2026-11-02**:
   G410 is **NECTAR only** ("ROBOTS may not enter NECTAR into the FLOWER scoring volume"); POLLEN is not time-gated.
   There is **no launch-zone rule** in TU07 or TU08. Rule numbers are current as of TU08 and are listed in §2.13.
5. ~~Allowed motors/servos and power limits~~ — **RESOLVED 2026-11-02**: R501 + R502 tables, R503 = 8/8 (§2.11).
   Still open: which physical servo you actually pick, given the 5 V vs 6 V hub issue.
6. ~~Starting state of each HIVE (which CELL is up)~~ — **RESOLVED 2026-11-10** by the Event Field Setup Guide
   V1.0 §11.1: **red audience-side CELL up, blue scoring-side CELL up**, each holding `[3] NECTAR`. In tags:
   red **34-37** up / 30-33 down, blue **42-45** up / 38-41 down (§2.7, §2.12).
7. Regional / Championship RP thresholds (TBA in Team Updates). **Still TBA at TU08** — Table 10-3 lists TBA for
   both *FIRST* Championship and Regional Championships; only "all other events" has numbers (16 / 4 tips / 7 tips).
8. Whether SolversLib 0.3.6 / Pedro 3 are still the right pins after the next releases; check changelogs before upgrading.

---

## 9. Team Update log (TU00 → TU08)

Source: the **combined** Team Update document `https://ftc-resources.firstinspires.org/ftc/game/tu-combined` and the
manual at `https://ftc-resources.firstinspires.org/ftc/game/cm-html`. TU08 was published **2026-11-05** and was read
via the FTC MCP on **2026-11-10** (`sync_status` / `recent_changes`: TU08 is the indexed manual and TU version; the
MCP reports sections 11 and 12 at TU08, section 9 still TU06). At the source both are PDFs; `cm-html` renders the
whole manual to text and is the fastest way to check a rule number. TUs publish every Thursday until two weeks
before *FIRST* Championship, and a TU published after an event's driver's meeting does not apply to that event
[MANUAL 1.7.3].

### 9.1 What each update changed, and whether it hits us

| TU | Date | Changes | Hits us? |
|---|---|---|---|
| **TU00** | 2026-09-12 | New "spirit of the rule" manual philosophy; **STRATEGIC** added as a defined term; **Competition Integrity Contract** + **G202** added; **G406** merges the old G407 (don't damage elements) and G412 (don't damage the field) — this is why G-numbers moved; Section 13 renumbered; T301 (2 people may talk to the Head Ref) and T701 (2 students in alliance selection); **R503 servo cap 10 → 8**; **R501** gains WATTOS Stingray; **R801** extended to pneumatic actuators, high-speed blowers, vacuums; **R101**, **R201**, **E510** added; old gamepad-restriction rule (was R903) removed, so **R903 is now "OPERATOR CONSOLE physical requirements"**. | **Yes** |
| **TU01** | 2026-09-17 | Figure 9-10 "Bottom of HIVE above TILES" corrected to **30.6 in (77.70 cm)**; G204 example fixed G426 → G427; 10.6.1 duplicate text removed. | **Yes** — HIVE geometry |
| **TU02** | 2026-09-24 | Table 10-6 violation examples expanded (REPEATED, STRATEGIC, per-element, 3-second-count escalation); G420 tip/entangle examples; G423/G424/G425 penalty attribution; G426 NECTAR timing. | **Yes** — G426, G420 |
| **TU03** | 2026-10-01 | 10.3.4 preload wording ("held in or on", or "placed on the TILES"); **G304.G "in contact with exactly 4 POLLEN pre-loads"**; outreach definitions. | **Yes** — G304 |
| **TU04** | 2026-10-08 | Escalation Guidelines posted; judging feedback form + interview question bank links; **Table 9-1: the 1:00 "chime" for FLOWER ownership unlocked replaced the `[TBD]`**. | **Yes** — drivers, match clock |
| **TU05** | 2026-10-15 | 10.8.4 FIELD reset; **G407 note: REPEATED 5+ elements is likely STRATEGIC even with a "keep 4, discard the rest" sorter**; **G408 note: same for opponent NECTAR**; R403 team numbers must be legible at 12 ft — **LED-display numbers explicitly prohibited**. | **Yes** — intake + sorter |
| **TU06** | 2026-10-22 | Digital Animation Award (WPI); **Inspection Checklist v26-27.2**; 9.1 field checklist links; **G401** exceptions restated; **G402 rewritten** — MAJOR FOUL per MATCH, YELLOW CARD if STRATEGIC, columns A-C red / D-F blue, examples spelled out. | **Yes** — AUTO paths |
| **TU07** | 2026-10-29 | **G304 rewritten into conditions A-H**; **G412** violation now "DISABLED and VERBAL WARNING / DISABLED and YELLOW CARD per MATCH if subsequent"; **G419** examples E-G added (disconnect wires, disconnect battery, power off via switch → RED CARD). | **Yes** — pre-match checklist |
| **TU08** | 2026-11-05 | **G405** safe harbour added: elements leaving the FIELD *during scoring attempts*, *because a ROBOT blocked another ROBOT's scoring attempt*, or *because of ROBOT-to-ROBOT interactions* are **not** deliberate ejections and are **not** violations. **R612** extended: dynamic / continuous-rotation electrical interfaces (linear sliding contacts, **slip rings**) on a critical power path must be **unmodified COTS**; custom ones are prohibited. **T402** wording: a ROBOT may only be re-enabled after a REFEREE declared it DISABLED under stated conditions. | **Yes** — G405 relaxes the shot-out-of-bounds fear; R612 constrains any rotating-power-joint mechanism |

### 9.2 Facts this file previously had wrong, or merely guessed

| Was | Now |
|---|---|
| "R510 allowed motors list" | **R501**, Table 12-1. There is no R510 motor rule. |
| AprilTag per-CELL split tagged "from the research snapshot", alliance grouping "user-confirmed" | Both are **manual text** (9.9). The guess happened to be right. |
| "No launch-zone restriction surfaced in my research. TODO" | Confirmed: **no launch-zone rule in TU07**. |
| "G410 scope for POLLEN unclear. TODO" | **G410 is NECTAR-only.** POLLEN into a FLOWER is not time-gated. |
| "Starting state: red audience-side CELL up, blue far-side up. TODO(verify Fig 10-2)" | Manual gives only the mnemonic; the per-alliance state is **Event Field Setup Guide V1.0 §11.1** [2026-11-10]: red audience-side up, blue scoring-side up. The third-party guess was right. §2.7, §2.12. |
| "Tip threshold is not in the manual, use the ~200 g third-party placeholder" | Third-party placeholder, **primary source found** [2026-11-10]: Event Field Setup Guide §12.3 - every HIVE is calibrated to tip at `[8] POLLEN` and at `[3] NECTAR + [3] POLLEN` (≈0.44 lb ≈200 g). Calibration spec, not a rule. §2.7. |
| "TU07 is the latest team update" | **TU08** published 2026-11-05 and is the indexed version. §9.1. |
| G-rule titles only | Full violation text and escalations now in §2.13. |
| "Pedro Pathing uses a 144 × 144 in field, same size." | **141.5 × 141.5**, origin at a corner, +x right / +y up, heading CCW, HIVE centre (70.75, 70.75). Not TU-sourced - from Pedro's field diagram. §2.3. |
| "(Ivy was dropped.)" | **Ivy is back.** `com.pedropathing.ivy:pedro:1.1.1` is in `build.dependencies.gradle` and the autos are written in it. Not TU-sourced. §3.1, §3.2b. |

### 9.3 Game facts the TUs moved (already reflected above)

- HIVE "bottom above TILES" = **30.6 in** (TU01).
- AprilTags **30-45**, audience/opposite split is manual text, not a third-party guess.
- The **1:00 audio cue is a "chime"** (TU04) — useful for drivers; not a substitute for a software match clock.
- **R503 = 8 motors / 8 servos** (TU00), and **R504.C explicitly permits CR-servo modification** — the CR turret is legal.
- **G402 is now a MAJOR FOUL per MATCH** with STRATEGIC escalation (TU06). That is the single most consequential
  change for this project, because AUTO paths that cross to the other side of the field are exactly what it targets.

### 9.4 Still open after TU08

1. ~~HIVE tip threshold~~ — **RESOLVED 2026-11-10** by the Event Field Setup Guide V1.0 §12.3: every HIVE is
   calibrated to tip at `[8] POLLEN` **and** at `[3] NECTAR + [3] POLLEN` (≈0.44 lb ≈200 g), and must **not** tip
   at `[3] NECTAR + [2] POLLEN`. A HIVE outside that tolerance is an ARENA FAULT (Q&A #46), so it is a build spec,
   not a rule. `TODO(measure on your practice HIVE)` - a practice field is not guaranteed to be in calibration.
2. **Raised-CELL mouth height** — only in Figures 9-10/9-11, which are images, and the Field Acceptance Checklist
   that would hold the controlled dimensions is still *"coming soon"* in both field guides. `TODO(read CAD)`.
3. ~~Which CELL starts up per alliance~~ — **RESOLVED 2026-11-10**: Event Field Setup Guide V1.0 §11.1 - red
   audience-side CELL up, blue scoring-side CELL up, `[3] NECTAR` in each. Tags: red 34-37, blue 42-45. §2.7, §2.12.
4. **Regional / Championship RP thresholds** — still TBA in Table 10-3 as of TU08.
5. Whether the up/down CELL tag-row height difference is usable for state detection. `TODO`.
6. Pedro frame ↔ field orientation mapping. `TODO`.
7. **CELL mouth field coordinates** — not in the manual and not in either field guide. `TODO(CAD or tape measure)`,
   see §8 item 1.

### 9.5 How to refresh this section

1. Run the MCP `sync_status` first - it names the indexed manual/TU/Q&A versions without fetching anything.
2. `recent_changes` with `since: "<current TU>"` lists every rule a TU touched, with a one-line excerpt.
   `search_changes` finds a rule's TU history. **Neither is current rule text** - follow up with `get_rule`.
3. Fetch `https://ftc-resources.firstinspires.org/ftc/game` - it states the current TU version and date.
4. The TU PDFs are not text-fetchable by the tooling that wrote this file. Extract locally: download
   `tu-combined`, then run a PDF text extractor over it.
5. Fetch `https://ftc-resources.firstinspires.org/ftc/game/cm-html` for the consolidated manual as text.
6. **The field guides move separately from the TUs** and carry facts the manual does not (HIVE starting state,
   tip calibration). Query them through `search_rules` with the section filter - see §0.9. They have no rule IDs,
   so `get_rule` will not find them.
7. Update §9.1, then every inline claim in §2 it invalidates, then §9.4. Do not leave a stale `[MANUAL]` tag behind.

---

## 10. References

- Game and season hub: `ftc.game` · Manual: `ftc.game/manual` · HTML: `ftc.game/cm-html` · Scoring calculator: `ftc.game/calculator`
- Team Updates: `https://ftc-resources.firstinspires.org/ftc/game/tu-latest` (latest, PDF) ·
  `https://ftc-resources.firstinspires.org/ftc/game/tu-combined` (**combined TU00 → latest** - use this one) ·
  season page with live version strings `https://ftc-resources.firstinspires.org/ftc/game` ·
  Q&A opened Sep 28, 2026
- SolversLib docs `https://docs.seattlesolvers.com` (+`.md`, `/llms.txt`) · Quickstart `github.com/FTC-23511/SolversLib-Quickstart`
- Third-party BIOBUZZ tools (unverified, useful for ideas): `Lazer2077/BIOBUZZ_Simulator`, `patricio-jijon/biobuzz-shooter-bench`
- Reference code (DECODE era): `RevAmped-Decode-V2`, `FTC-23849-DECODE`
