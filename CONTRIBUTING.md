# Contributing to Cottage Kitchen

Thanks for your interest in the game. This file covers the setup, code layout, and tooling you need to make a change. Player-facing information stays in [README.md](README.md).

The project is licensed under the [GNU General Public License v3.0](LICENSE); please keep new source and assets compatible with that license.

## Getting set up

You need JDK 17 or newer (CI uses 21) and the Android SDK with API 36 installed. Android Studio is optional.

```bash
git clone https://github.com/russelldickenson/cottage-kitchen.git
cd cottage-kitchen
export ANDROID_HOME="$HOME/Library/Android/sdk"   # point this at your SDK
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/cottage_kitchen.apk
```

On Windows use `gradlew.bat` instead of `./gradlew`. You can also open the project in Android Studio and run the `app` configuration. The app is portrait-only and needs Android 7.0 (API 24) or newer.

## How the project is organised

The game is one `Activity` with a custom `View` drawing every screen on a `Canvas`. There are no XML layouts, no AndroidX, and no third-party dependencies. Art and sound effects are bundled locally, progress is stored in `SharedPreferences`, and the app has no network permission.

Everything gameplay-related lives in `app/src/main/java/com/zigzag/cottagekitchen/`:

- `GameView.java` — game state, touch input, and the match/clear state machine.
- `Board.java`, `Rules.java`, `Tile.java` — pure match-3 logic with no Android types, shared with the simulator.
- `Level.java`, `Recipes.java`, `Art.java` — levels, dishes, ingredient ids, and drawable ids.
- `Renderer.java`, `CookRenderer.java`, `Gfx.java`, `Effects.java` — screens, animations, and drawing helpers.
- `Progress.java` — saved stars, best scores, unlocks, and settings.
- `Sfx.java` — sound effects and haptics; `MainActivity.java` — the activity shell.

Assets live in `app/src/main/res/drawable-nodpi/` (ingredient tiles, dishes, wallpapers), `app/src/main/res/raw/` (WAV effects), and `app/src/main/res/mipmap-*` (launcher icons). There are no automated unit tests; the simulator below is the de facto gameplay test harness.

Keep new code dependency-free and follow the surrounding Java style.

## Levels and balance

There are 14 recipes and 49 levels, one ingredient per level. From level 16 some orders ask for an earlier ingredient as well, and from level 20 some include crates. The first five-in-a-row or L/T clear in a level refunds one move; `Rules.bigMatch()` defines what counts as a big match.

Move budgets in `Level.MOVES` were produced by the headless simulator in `tools/sim/`, which compiles the same `Board`, `Rules`, and `Level` code as the game. Use it when you change goals, mechanics, or balance:

```bash
./tools/sim/run.sh 100        # win rates for expert, casual, and random bots
./tools/sim/run.sh 200 tune   # suggested move budgets for every level
```

`run.sh` compiles the pure logic with `javac`, so it also catches breakage in those classes. Changing a goal type or adding a tile kind means updating both the game and `tools/sim/R.java`.

## Regenerating art and sound

The generators in `tools/` are small Go programs. Run them from the repository root unless noted:

- `go run ./tools/dishes` — turns the artwork in `tools/dishes/src/` into dish and crop PNGs.
- `go run ./tools/cutsprites` — cuts board sprites out of the sheets in `tools/sprites/` into `drawable-nodpi/`.
- `go run ./tools/crate` — draws the crate and cracked-crate tiles.
- `(cd tools/sounds && go run . ../../app/src/main/res/raw)` — synthesises the WAV effects.
- `(cd tools/icon && go run . ../../app/src/main)` — rebuilds launcher icons. It expects the cottage source illustration at `_aa_cottage_with_hat.jpeg` in the repository root, which is not checked in; supply the artwork before running it.

The README showcase in `assets/readme/` has its own generator, `assets/readme/source/build_dishes.py`, for `dishes.png`. The gameplay screenshot is a device capture, not a generated asset.

## Release builds and signing

[`build.sh`](build.sh) builds the release APK, verifies the signature with `apksigner`, and copies the result to the repository root. Signing credentials come from the environment and must never be committed; `*.jks` and `*.keystore` are gitignored.

```bash
export ANDROID_KEYSTORE_PATH="$HOME/.android-signing/android-shared.p12"
export ANDROID_KEYSTORE_PASSWORD="$( ... )"   # read it from your password manager
./build.sh
```

The key alias defaults to `cottagekitchen` in [`gradle.properties`](gradle.properties); override it with `ANDROID_KEY_ALIAS` if needed. The APK is written to `app/build/outputs/apk/release/cottage_kitchen.apk` and copied to `./cottage_kitchen.apk`. `./gradlew assembleRelease` with the same environment variables produces the same APK without the copy and verification steps. Pushing a `v*` tag runs the [release workflow](.github/workflows/android-release.yml), which uploads a signed APK as an Actions artifact.

## Before opening a pull request

- Run `./gradlew assembleDebug` and `./tools/sim/run.sh 100`.
- If you changed balance, re-run `tune` and explain the new budgets.
- Keep changes focused, and describe what you verified in the pull request.
