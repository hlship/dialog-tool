# jpackage Distribution

## Goal

Distribute `dgt` as a native installer on macOS (Intel + ARM), Windows, and Linux,
bundling a JRE so users don't need to install Java.

## What jpackage Does

jpackage is a JDK-bundled tool that produces native installers:

| Platform | Formats |
|---|---|
| macOS | `.dmg`, `.pkg` |
| Windows | `.msi`, `.exe` |
| Linux | `.deb`, `.rpm` |

The installer bundles a trimmed JRE (via jlink) alongside the uberjar. Users
install normally and never think about the JVM.

## Why This Is Viable

- No code changes required — jpackage works directly from the existing uberjar
- No GraalVM native-image complications (pty4j JNI, Lucene reflection, brotli4j
  native libs all work fine on the JVM)
- jpackage has been production-ready since JDK 16; targeting JDK 24 is fine
- JDK 24 adds WiX v4/v5 support on Windows (auto-selects newest installed version)

## What Changes

### Remove
- `release/templates/dgt` — jpackage generates its own native launcher
- `release/templates/dgt.cmd` — same

### Update `build.clj`
Add a jpackage invocation after the uberjar step:

```
jpackage
  --name dgt
  --app-version <tag>
  --input out/build/
  --main-jar dialog-tool-<tag>.jar
  --main-class dialog_tool.main
  --java-options "--enable-native-access=ALL-UNNAMED"
  --type dmg|pkg|exe|msi|deb|rpm   (platform-specific)
  --dest out/packages/
```

The `--enable-native-access=ALL-UNNAMED` flag (currently in the `dgt` launcher
template) moves here.

### Update `release.clj` / `bb.edn`
- Replace zip-of-jar logic with per-platform installer artifact upload
- Update Homebrew formula: switch from `formula` (jar + script) to a `cask` (dmg/pkg)
- Update Scoop manifest: reference the `.msi` or `.exe` instead of the zip

### Add GitHub Actions CI
Cross-compilation is not supported — each platform's installer must be built on
that platform. A matrix workflow is needed:

```yaml
strategy:
  matrix:
    include:
      - os: macos-13          # Intel
        type: dmg
      - os: macos-latest      # ARM
        type: dmg
      - os: ubuntu-latest
        type: deb
      - os: windows-latest
        type: msi
```

Each job:
1. Checks out repo
2. Sets up JDK 24
3. Builds uberjar (`clojure -T:build uber`)
4. Runs jpackage for the platform
5. Uploads installer as a workflow artifact

A final job collects all four artifacts and publishes the GitHub Release (replacing
the current manual `bb release` flow).

## Package Size

Expect ~80–120MB per installer (uberjar + trimmed JRE + Lucene/pty4j/brotli4j).
Users download once; no ongoing JVM management.

## Gotchas

### macOS: Signing and Notarization (Medium effort, real cost)
Without signing, modern macOS reports downloaded installers as "damaged/unopenable"
via Gatekeeper. Options:
- Apple Developer account — $99/year
- Secrets needed in GitHub: certificate (p12), certificate password, Apple ID, app-specific password
- GitHub Actions exist for sign + notarize + staple
- Without this, users must `xattr -c` the installer from the command line — not viable for non-developers

### Windows: SmartScreen (Optional)
Without an EV code signing certificate (~$300/year), Windows SmartScreen shows
an "unknown publisher" warning on first run. Dismissible but unfriendly. Optional
depending on target audience.

### Linux: No Signing Concern
`.deb` and `.rpm` packages don't trigger the same friction. No certificate needed
for basic distribution.

## Effort Estimate

| Task | Effort |
|---|---|
| `build.clj` jpackage step | Low |
| Move JVM flags (`--enable-native-access`) | Trivial |
| Remove old launcher templates | Trivial |
| GitHub Actions matrix workflow | Low–Medium |
| Update Homebrew formula (cask) | Low |
| Update Scoop manifest | Low |
| macOS signing + notarization | Medium |
| Windows signing (optional) | Medium |

**Total (without signing): ~2–3 days**
**Total (with macOS signing): ~3–4 days**
