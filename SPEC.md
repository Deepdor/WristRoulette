# Wrist Roulette — Product & Technical Specification

**Document:** `SPEC.md`  
**Version:** 0.1  
**Status:** Working specification  
**Platform:** Standalone Wear OS app  
**Primary target:** Samsung Galaxy Watch Ultra-class round display

---

## 1. Purpose

Wrist Roulette is a standalone Wear OS application with two primary modes:

1. **RNG mode** — a compact roulette/dice-style random number generator presented as a spinning wheel.
2. **Roulette / Play mode** — an interactive roulette simulation operated physically using the watch IMU and the circular touchscreen.

The defining interaction in Play mode is that the user operates the roulette rather than merely pressing a “spin” button:

- the user draws the watch arm back;
- the watch confirms that the gesture is armed with haptic feedback;
- the user swings the arm forward to impart spin to the roulette wheel;
- the user then drags a finger around the edge of the circular display to throw the ball;
- the simulation continues without further user input until a pocket/result is determined.

The application may include a simple betting game using **local, fictitious credits only**.

---

## 2. Product Principles

- The application is **standalone** and does not require a phone companion, server, account, cloud service, or network connection during normal use.
- Play mode should favor **physical interactions that resemble the real-world action** instead of substituting buttons where watch hardware can provide the interaction.
- Game credits have **no connection to real money or currency**.
- The initial implementation should use simple native/programmatic graphics.
- Rendering, physics, game state, and input logic must remain sufficiently separated that programmatic graphics can later be replaced with image/vector assets without changing the simulation or game rules.
- The physical roulette mode should derive its outcome from the simulated wheel/ball state rather than selecting a result first and playing an animation toward it.
- RNG mode may choose the result first; its wheel animation is presentation.

---

## 3. Top-Level Navigation

The application navigation is a rooted tree.

The root is always the **Main Screen**.

```text
MAIN
├── OPTIONS
├── RNG
├── BET RECORD
└── ROULETTE
    └── BET
        └── PLAY
            └── RESULT
```

### 3.1 Back behavior

The Android/Wear OS Back action moves one level toward the root.

Examples:

- RNG → Main
- Options → Main
- Bet Record → Main
- Bet → Main
- Play → Bet
- Result → Play or Bet, depending on the final result-flow implementation
- Main → normal Android/Wear OS exit/background behavior

If Back is used during an active RNG animation, wheel spin, ball throw, or simulation, the transient interaction is cancelled before returning to the parent screen.

The app should not leave sensor processing or physics simulation running after navigating away from the relevant screen.

---

## 4. Main Screen

The Main Screen is divided into four large touch regions shaped to suit the round display.

```text
            OPTIONS
         ─────────────
        /             \
       /               \
      |   RNG     PLAY  |
      |   RED     BLACK |
       \               /
        \             /
         ─────────────
          BET RECORD
```

### 4.1 Regions

- **Large red region:** RNG
- **Large black region:** Roulette / Play
- **Small upper region:** Options
- **Small lower region:** Bet Record

The exact geometry may be adjusted for usability on the target watch, but the four-region concept should remain.

No scrolling should be required on the Main Screen.

---

## 5. Visual Design

### 5.1 Initial visual style

The initial application should remain deliberately plain and use standard Kotlin/Compose/Canvas drawing, similar to the current prototype.

The implementation should avoid coupling game logic to any specific visual asset.

For example:

```text
wheelAngle     = simulation state
wheelVelocity  = simulation state
ballAngle      = simulation state

Canvas / bitmap / vector asset = renderer only
```

Future visual polish may replace programmatic wheel graphics with raster or vector assets while preserving the same logical wheel geometry and state.

### 5.2 European roulette geometry

The roulette wheel uses a single-zero European layout with 37 sectors.

Current wheel order:

```text
0,
32, 15, 19, 4, 21, 2, 25, 17, 34,
6, 27, 13, 36, 11, 30, 8, 23, 10,
5, 24, 16, 33, 1, 20, 14, 31, 9,
22, 18, 29, 7, 28, 12, 35, 3, 26
```

The implementation must preserve one authoritative wheel-order definition rather than duplicating wheel ordering separately in rendering, physics, and result logic.

---

## 6. RNG Mode

RNG mode is a utility/randomizer mode rather than physical roulette simulation.

### 6.1 Presentation

- The wheel is displayed using the configured rotor geometry: 37 sectors for European, D12, and D6; 21 sectors for D20.
- No roulette ball is used.
- A **fixed downward-pointing arrow at the top of the display** identifies the selected sector.
- The presentation should resemble a small lottery drum: the wheel rotates beneath a stationary selector.

```text
             ▼
        ┌─────────┐
        │ rotating│
        │  wheel  │
        └─────────┘
```

### 6.2 RNG spans

The selectable RNG spans are:

- European Roulette
- D20
- D12
- D6

European Roulette, D12, and D6 retain the **37-sector** visual wheel.

D20 uses a bespoke **21-sector** rotor containing zero and one sector for each value from 1 through 20.

The green zero sector remains present in all RNG modes.

For D12 and D6, values repeat around the 36 non-zero sectors of the existing roulette geometry.

The dice layouts are:

- **D20:** Bespoke roulette rotor with 0 and 1–20
- **D12:** 0 and 1–12 repeating to fill the 36 non-zero sectors
- **D6:** 0 and 1–6 repeating to fill the 36 non-zero sectors

The zero sector is retained as a roulette-style visual feature, but it is not an eligible logical die result. Logical dice results use the conventional ranges D20 = 1–20, D12 = 1–12, and D6 = 1–6.


### 6.3 RNG fairness

RNG mode must select the logical result uniformly from the configured span first.

Dice results are generated with an unbiased bounded random draw over the conventional range 1 through N. Zero is not a logical dice outcome.

The animation then rotates the wheel so that a sector carrying the selected value stops beneath the fixed arrow.

Therefore:

```text
uniform logical RNG result
        ↓
select matching visual sector
        ↓
animate wheel to that sector
```

Repeated labels on the D12 and D6 rotors must not determine probability. The logical result is selected uniformly from 1 through N first, and the animation then selects a matching visual sector.

### 6.4 RNG roll duration

The duration of the RNG roll animation is configurable in Options.

Exact control presentation and min/max duration are tunable.

### 6.5 RNG roll initiation

RNG mode opens with a stationary wheel. Tapping the wheel or its central region initiates a roll.

Further taps are ignored while a roll animation is active. After the result is displayed, the user may tap again for another independent roll.

---

## 7. Roulette / Play Mode

Selecting Roulette from the Main Screen first opens the **Bet Screen**, then proceeds to the physical roulette simulation.

```text
MAIN
  ↓
BET
  ↓
DRAW BACK
  ↓
ARMED / HAPTIC
  ↓
SWING
  ↓
WHEEL SPIN
  ↓
THROW BALL
  ↓
SIMULATION
  ↓
RESULT
  ↓
BET SETTLEMENT
```

---

## 8. Gesture-Controlled Wheel Spin

### 8.1 Sensor source

The watch gyroscope is the primary sensor for wheel-spin gesture detection.

Accelerometer data may be added later if it improves rejection of false gestures, but is not required by the current proven concept.

### 8.2 Gesture state machine

The current proven interaction uses a simple state machine:

```text
WAITING / DRAW BACK
        ↓
valid backswing detected
        ↓
ARMED / SWING!
        ↓
opposite-direction forward swing
        ↓
LAUNCH
        ↓
short cooldown
        ↓
WAITING / next state
```

The backswing establishes:

- dominant gyro axis;
- direction/sign of rotation;
- arming time.

A valid forward swing must occur on the stored dominant axis and reverse the sign of the backswing.

The recognition should not require a fixed left/right wrist setting.

### 8.3 Current prototype thresholds

Current/provisional values:

- backswing arming threshold: approximately 4 rad/s
- forward-swing launch threshold: approximately 5 rad/s
- armed timeout: approximately 1.2 s
- post-launch cooldown: approximately 0.7 s

These are tuning values, not fixed product requirements.

Empirical user testing has shown approximately:

- 5–10 rad/s: weak swing
- 10–15 rad/s: fast/normal strong swing
- 15–20 rad/s: strong swing
- >20 rad/s: unusually forceful movement
- approximately 25 rad/s: practical observed ceiling during very hard movement

Normal gameplay should be well controlled within approximately **5–20 rad/s**.

The game should not reward increasingly extreme arm motion above the practical strong-spin range. High input values should be compressed or clamped.

### 8.4 Haptic arming cue

When the backswing is accepted and the state transitions to ARMED/SWING:

- the watch produces a short haptic vibration;
- the user should be able to feel the confirmation without looking at the screen;
- the duration is configurable in Options.

A brief gyro guard period may be used after the haptic to prevent mechanical vibration from contaminating gesture detection.

The current concept has been physically proven on the target watch.

---

## 9. Wheel Physics

The original prototype used a fixed Compose tween. Play mode now uses a frame-driven physics/state representation based on angular velocity.

Minimum state:

```text
wheelAngle
wheelAngularVelocity
```

A forward swing maps to an initial wheel angular velocity:

```text
arm gesture strength
        ↓
initial wheel angular velocity
```

The gesture must **not** directly determine the final pocket/result.

The wheel then advances over time:

```text
wheelAngle += wheelAngularVelocity × dt
wheelAngularVelocity decays according to friction
```

A simple exponential or tuned friction model is acceptable.

### 9.1 Current physics tuning

Milestone 5 uses the following provisional model:

- accepted gesture strength is clamped to 5–20 rad/s;
- that range maps to approximately 540–1440 degrees/second of initial wheel velocity;
- the sign of the forward swing determines wheel direction;
- exponential friction decays angular velocity each frame;
- the wheel stops below approximately 6 degrees/second;
- unusually long frame gaps are clamped so backgrounding cannot jump the simulation forward.

These remain physical-device tuning values rather than fixed product constants.

### 9.2 Spin duration

The wheel must remain spinning long enough for the user to comfortably perform the subsequent ball-throw gesture. The original tween duration was too short.

Target duration is tunable, but a normal spin should provide several seconds of usable wheel rotation after launch.

Milestone 6 makes wheel friction phase-aware. Before a valid ball throw, the wheel uses lighter exponential friction (currently approximately 0.35/second) and retains a useful coasting floor (currently approximately 240 degrees/second). Once the ball is launched, normal wheel decay (currently approximately 0.62/second) resumes and the existing stop threshold applies. These values remain subject to physical-device tuning.

---

## 10. Ball Throw

### 10.1 Play-mode ball

Unlike RNG mode, Play mode uses a visible roulette ball.

The ball is inserted only after the wheel has been spun.

### 10.2 Touch gesture

The user throws the ball by dragging a finger around the **outer edge of the circular watch display**.

The left-side quadrant is reserved for Wear OS left-to-right Back navigation. The available ball-launch zone is therefore shown as a white 270-degree arc rather than a complete circle, and a throw must start within that arc. Once the gesture has begun in the valid zone, minor inward or lateral drift does not invalidate it. This restriction affects initial touch input only; once launched, the simulated ball may travel around the full 360-degree outer track.

The gesture provides:

- direction;
- angular launch speed.

Finger position relative to the screen center is converted into an angle:

```text
theta = atan2(y - centerY, x - centerX)
```

The angular motion of the touch gesture is used to estimate launch angular velocity.

The implementation should use a short sample window rather than only two instantaneous points, to avoid unrealistic launch velocities caused by touch-coordinate noise.

### 10.2.1 Current throw tuning

Ball launch estimates touch velocity with a linear fit across the most recent 180 ms. Following the Wear OS 7 compatibility pass, a valid gesture may use two or more samples spanning at least 24 ms, provided it travels at least 0.05 radians. Touch speeds below approximately 0.6 rad/s are treated as noise, speeds above approximately 15 rad/s are clamped, and the accepted speed is scaled by 2.4 for the initial ball velocity. The throw must begin inside the permitted outer zone, but brief inward drift after the initial contact no longer invalidates the whole gesture. Rejected attempts display a temporary diagnostic reason and sample count on the watch. These remain physical-device tuning values rather than fixed product constants.

### 10.3 Same/opposite direction

The user is allowed to throw the ball in either direction.

The app should not artificially prevent a ball throw in the same direction as the wheel.

---

## 11. Ball Physics

Minimum Play-mode ball state:

```text
ballAngle
ballAngularVelocity
ballRadius
ballRadialVelocity   // when inward motion begins
```

### 11.1 Outer track

Immediately after release, the ball initially travels around the outer track.

Its angular velocity decays faster than the wheel's angular velocity.

The ball begins at approximately 44% of the wheel diameter while applying outer-track angular friction of approximately 0.85/second. Milestone 7 begins the inward drop when ball speed falls to approximately 360 degrees/second rather than stopping on the outer track.

### 11.2 Drop

When ball speed falls below a tunable threshold, the ball begins moving inward toward the rotor/pockets.

The inward transition should be continuous rather than teleporting directly from the outer track to a pocket.

Current Milestone 7 tuning applies approximately 1.10/second angular friction during the drop. Radial velocity begins at approximately -0.035 radius-fraction/second, accelerates inward, and is limited to approximately -0.09 radius-fraction/second.

### 11.3 Deflectors / chaos

Later implementation may model diamonds/deflectors as radial interaction zones.

Crossing these zones may perturb:

- ball angular velocity;
- radial velocity;
- trajectory.

Small deterministic/random perturbations may be used to create plausible chaotic behavior without requiring a full 3D rigid-body simulation.

Milestone 7 uses four radial deflector bands during the drop. Each crossing applies a small seeded angular nudge, angular-velocity kick, and radial-speed variation. A fresh seed is generated for each throw, while the same state and seed remain repeatable for testing.

### 11.4 Pocket capture

Once the ball reaches the pocket region, the winning pocket is calculated from the ball's angle relative to the wheel:

```text
relativeAngle = ballAngle - wheelAngle
```

The relative angle maps into one of the 37 European roulette sectors.

The displayed visual pocket and logical result must agree.

Capture currently occurs at approximately 36% of the wheel diameter. The ball snaps to the captured sector center and remains locked to that sector as the rotor finishes slowing. The wheel displays the authoritative sector labels, and the result overlay uses the same `EUROPEAN_WHEEL_ORDER` entry. Bet settlement remains deferred to Milestone 8.

---

## 12. Betting

### 12.1 Starting bank

A new session begins with:

**100 credits**

Credits are fictional and have no monetary value.

### 12.2 Bet placement gesture

Betting should be optimized for a small round watch display.

When interacting with a bet target:

- **tap:** add 1 credit
- **hold for at least 1 second:** add 5 credits
- **hold for at least 3 seconds:** add 20 credits
- if fewer credits remain than the requested amount, add the remaining available credits

A single press/hold interaction should resolve to the highest threshold reached once, rather than cumulatively applying all lower thresholds.

Example:

```text
0.2 s press  → +1
1.4 s press  → +5
3.2 s press  → +20
```

The total amount staked must never exceed the available bank.

During bet placement, stakes are reserved in an in-memory bet slip. The amount still available to stake is `bank - total stake`. The persisted bank is not changed until a completed roulette result can be settled.

The Bet screen includes a **BET 0** control that clears all current stakes and restores the full bank as available to stake. It preserves the currently selected straight number. Back navigation remains available through the normal Android/Wear OS Back action.

### 12.3 Bet-screen number selection

Straight-number selection should use the circular display edge as a rotary interaction rather than arrow buttons.

Running a finger around the edge changes the selected number.

The edge selector must not begin over the bottom PLAY/BET 0 navigation row. A selection that begins elsewhere on the edge may continue through the bottom sector, preserving access to every number while ensuring navigation taps are not consumed by the selector.

The selected number should be clearly visible.

The initial interface supports one active straight-number target. Changing the selected number moves that target, including its current stake, to the newly selected number.

### 12.4 Initial bet types

The initial v1 set is:

- Red / Black
- Odd / Even
- Low / High
- Straight number

Additional roulette bet types may be added later.

### 12.5 Bet settlement

The initial supported bets use standard European roulette payouts:

- Red / Black, Odd / Even, and Low / High pay 1:1. A winning stake is returned together with an equal amount of profit.
- A winning straight-number bet pays 35:1. The stake is returned together with 35 times the stake as profit.
- Zero loses Red / Black, Odd / Even, and Low / High bets. A straight bet on zero wins normally.
- Multiple active bets are settled independently against the same result.

The persisted bank is updated once for each captured result:

```text
new bank = previous bank - total stake + total winning returns
```

A completed spin counts as a win in Bet Record only when its combined net credit change is positive. Biggest Win records the largest positive net credit change from one completed spin, rather than gross returns from an individual bet.

---

## 13. Session Model

A session begins with a bank of 100 credits.

The session continues through completed roulette spins.

The session ends when the bank reaches zero.

The session may also be manually reset from Options.

A manual reset:

- restores the bank to 100 credits;
- resets the active session state;
- begins a new session.

Transient wheel/ball state is not part of session persistence.

---

## 14. Bet Record

The Bet Record screen is accessed from the lower small sector of the Main Screen.

At minimum, the application records/displays:

- **Wins**
- **Biggest Win**
- **Number of spins in a session**
- **Highest Bank**

The application must also track the current session's bank and spin count internally.

Bet Record displays both the current session spin count and the highest completed-session spin count.

A session becomes completed when its bank reaches zero or when the user manually resets it. At that point its final spin count is eligible for Highest Session Spins. An in-progress session remains visible through Current Session Spins but does not update the completed-session record yet.

RNG utility rolls are not betting events and should not automatically be treated as roulette betting records.

### 14.1 Reset semantics

Options includes a control to **Reset Bank / Session**.

Resetting the bank resets the current session.

Reset Bank / Session preserves lifetime record statistics. No independent lifetime-record reset is included in the initial implementation.

---

## 15. Options

The Options screen is accessed from the small upper sector of the Main Screen.

Initial settings:

### 15.1 Buzz duration

Controls the duration/strength presentation of the haptic cue emitted when the backswing is accepted.

An Off setting may be considered.

### 15.2 RNG roll length

Controls the duration of the RNG wheel animation.

The UI may use a small number of discrete settings or a suitable watch-native adjustment control.

### 15.3 RNG span

Selectable values:

- European Roulette
- D20
- D12
- D6

### 15.4 Reset bank/session

A dedicated control resets:

```text
bank → 100 credits
current session → new session
```

Because this is destructive to current-session progress, it should require confirmation.

Settings take effect locally and should persist across normal app restarts.

No separate Save button is required unless later UI constraints make one useful.

---

## 16. Persistence

The app should use local persistence only.

Persistent data should include, as applicable:

- options/settings;
- current bank;
- current session statistics;
- retained betting records/statistics.

Transient state should generally not survive process death:

- active gyro gesture;
- active wheel velocity;
- active ball trajectory;
- mid-animation RNG roll.

If the app is interrupted in the middle of Play, it may return to a safe pre-spin or bet state rather than attempting to reconstruct mid-spin physics.

---

## 17. Sensor Lifecycle

Sensors should be active only when needed.

Examples:

- Main: gyro inactive
- Options: gyro inactive
- RNG: gyro inactive
- Bet Record: gyro inactive
- Bet Screen: gyro inactive
- Play / DRAW BACK + ARMED: gyro active
- after successful wheel launch: gyro may be disabled unless still needed
- ball throw: touchscreen gesture tracking active
- post-result: gyro inactive

The app should not leave continuous IMU sampling active simply because `RouletteScreen` exists in the process.

---

## 18. Current Proven Prototype State

The following has already been implemented and tested on a physical Galaxy Watch:

- Android Studio → wireless ADB → physical watch deployment
- standalone Wear OS application
- Compose/Canvas wheel rendering
- 37-sector European roulette geometry
- basic wheel animation
- live gyroscope readout
- empirical swing testing
- dominant-axis backswing detection
- sign-reversal forward-swing detection
- armed timeout
- post-launch cooldown
- haptic cue when entering ARMED/SWING state
- arm gesture successfully launches the wheel on the physical watch
- manual SPIN control exists as a temporary diagnostic/fallback
- Play-mode rotor animation now uses angular velocity and friction rather than a predetermined tween target

Existing proven hardware interaction should be preserved during refactoring unless a milestone explicitly changes it.

---

## 19. Suggested Internal State Model

The exact Kotlin structure is implementation-dependent, but the product state should conceptually separate top-level navigation from Play-mode phase.

Example:

```text
AppScreen
├── MAIN
├── OPTIONS
├── RNG
├── BET_RECORD
└── ROULETTE
```

Play state:

```text
PlayState
├── BETTING
├── DRAW_BACK
├── ARMED
├── THROW_BALL
├── SIMULATING
└── RESULT
```

The app should avoid making a single screen/composable responsible for navigation, persistence, sensor acquisition, physics, betting, and rendering simultaneously.

---

## 20. Implementation Milestones

### Milestone 0 — Current prototype

Status: **complete / proven**

Acceptance:

- app installs and runs on physical watch;
- gyro gesture can arm and spin the wheel;
- haptic cue is felt on arming.

### Milestone 1 — App shell and navigation

Implement:

- four-sector Main Screen;
- Main → Options;
- Main → RNG;
- Main → Bet Record;
- Main → Roulette/Bet;
- Back behavior returning toward Main;
- preserve existing roulette prototype behind the Play flow.

Acceptance:

- launch always starts at Main;
- each region is reliably tappable on the physical watch;
- Back returns to the correct parent;
- leaving Play stops any active sensor/simulation work.

### Milestone 2 — Options and persistence

Implement:

- buzz duration;
- RNG roll length;
- RNG span;
- reset bank/session;
- local persistence.

Acceptance:

- settings survive app restart;
- reset restores bank to 100 and starts a new session;
- haptic duration setting changes the physical cue.

### Milestone 3 — RNG mode

Implement:

- fixed downward arrow;
- configured rotor geometry (37 sectors for European/D12/D6 and 21 sectors for D20);
- European / D20 / D12 / D6 label mapping;
- uniform logical RNG;
- configured roll duration;
- animation stops on a sector carrying the selected value.

Acceptance:

- no roulette ball appears in RNG mode;
- Dn outcomes are uniform over 1..N independent of visible label repetition;
- final displayed sector agrees with the generated result.

### Milestone 4 — Betting/session shell

Implement:

- 100-credit starting bank;
- bet screen;
- tap/hold stake increments;
- straight-number edge selection;
- session counter;
- Bet Record data model.

Acceptance:

- tap adds 1;
- ≥1 s hold adds 5;
- ≥3 s hold adds 20;
- remainder behavior works when bank is lower than requested stake;
- total stake cannot exceed bank.

### Milestone 5 — Wheel physics

Replace fixed tween launch with:

- wheel angle;
- wheel angular velocity;
- gesture-strength mapping;
- friction/deceleration;
- sufficiently long spin to permit ball insertion.

Acceptance:

- stronger physical swing produces a generally stronger/longer wheel spin;
- >20 rad/s input is compressed/clamped rather than strongly rewarded;
- final rotor position is not preselected;
- wheel remains active long enough for comfortable ball throwing.

### Milestone 6 — Ball insertion

Implement:

- screen-edge drag detection;
- angular touch tracking;
- direction detection;
- launch velocity from a short motion sample window;
- outer-track ball rendering.

Acceptance:

- clockwise and counterclockwise throws work;
- ball speed visibly changes with finger gesture speed;
- wheel and ball move independently.

### Milestone 7 — Ball drop and result

Implement:

- ball friction;
- inward drop;
- simple deflector/chaos behavior;
- relative-angle pocket capture;
- result display.

Acceptance:

- visual landing pocket and logical result agree;
- Play-mode result arises from simulated wheel/ball state;
- repeated similar gestures do not trivially guarantee identical outcomes.

### Milestone 8 — Bet settlement and records

Implement:

- payout rules for supported bet types;
- wins;
- biggest win;
- session spin count;
- highest bank;
- result settlement.

Acceptance:

- bank updates correctly after each completed spin;
- session ends at zero credits;
- records update consistently.

### Milestone 9 — Visual polish / assets

Optional after behavior is stable:

- replace programmatic rotor/bowl graphics with polished assets;
- retain the existing simulation/state interfaces;
- add improved haptics/sound if desired.

Acceptance:

- visual replacement does not change physics, results, RNG fairness, or game state behavior.

---

## 21. Non-Goals for Initial Development

The following are outside the initial scope unless explicitly added later:

- phone companion application;
- cloud backend;
- online accounts;
- multiplayer;
- network dependency;
- real-money betting;
- payment integration;
- casino-certified RNG or simulation;
- full 3D rigid-body roulette physics;
- complete casino-table bet topology;
- elaborate asset-heavy graphics before core interaction is stable;
- analytics/telemetry backend.

---

## 22. Open Decisions / Tunable Parameters

The following are intentionally left open:

- exact Main Screen sector geometry;
- exact RNG animation easing;
- exact haptic duration range;
- final arm arming/launch thresholds;
- wheel friction constants and target spin duration;
- ball launch scaling;
- ball drop threshold;
- deflector perturbation model;
- payout table;
- exact result-screen Back behavior;
- final visual asset strategy.

These should be resolved by focused implementation/testing rather than guessed in advance.

---

## 23. Source of Truth

This document defines the intended product behavior.

Implementation agents should treat `SPEC.md` as the product-level source of truth and should not silently invent additional features or alter established behavior.

Agent-specific working instructions should live separately in files such as:

- `AGENTS.md`
- `CLAUDE.md`

Those files should reference this specification rather than duplicate it.
