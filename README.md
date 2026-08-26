# Latch

**Make your phone useful again.**

Latch is a simple Android app that uses physical NFC tags to change how much access you have to your phone.

When your phone is **unlatched**, everything works normally. When it is **latched**, everything is blocked by default except the apps you have deliberately chosen to let through.

Instead of asking you to maintain a growing list of distractions, Latch flips the logic: **you decide what deserves access to your attention.**

## Why Latch?

Most app blockers still leave the decision on the phone. You can disable them, extend a timer, change a setting or simply decide that this time is an exception.

Latch moves that decision into the physical world.

Tap a physical NFC tag to latch your phone. If you want full access again, you have to physically use an authorised Latch to unlatch it.

That small amount of friction is the point.

Latch is not designed to make your phone unusable. It is designed to make it useful on your terms.

Calls, messages, maps, music, banking, authentication, WhatsApp or anything else you genuinely need can still get through. Endless feeds, shopping apps, browsers, games or whatever tends to swallow your attention stay outside unless you explicitly allow them in.

## Modes

Latch is built around custom **Modes**.

A Mode defines what your phone should be allowed to do in a particular context. You might create:

- **Bedtime** — Phone, Messages, WhatsApp, Clock and Spotify
- **Focus** — Phone, Messages, Teams, Outlook and Authenticator
- **Family Time** — Phone, Messages, WhatsApp and Camera
- **Out & About** — Phone, Messages, Maps, Wallet, Camera and Music

These are only examples. Modes are completely user-defined.

Every Mode follows the same simple rule:

> If you did not choose to let an app through, it stays blocked while that Mode is active.

## Physical Latches

NFC tags become physical **Latches** that can activate or release Modes.

A single Latch can act as a simple toggle, or you can deliberately separate the place where a Mode starts from the place where it can end.

For example:

```text
Bedtime

Allowed through:
Phone
Messages
WhatsApp
Clock
Spotify

Latch with: Bedroom
Unlatch with: Kitchen
Maximum latch time: 8 hours
```

Tapping the Bedroom Latch activates Bedtime. Tapping it again does not release it. To fully unlatch the phone, you need to physically go to the Kitchen Latch.

For something like Focus, the same Desk Latch could both activate and release the Mode.

This means Latch can create as much or as little physical friction as the situation needs.

## Safety without an escape button

Latch deliberately avoids a convenient emergency-unlock button.

Every Mode has a maximum latch time. If the required NFC tag is lost, damaged or unavailable, the phone automatically returns to its normal unlatched state when that maximum time is reached.

The maximum is a safety limit, not a productivity timer. Latch is meant to stay active until you physically unlatch it, while making sure you can never permanently lock yourself out.

## What Latch is not

Latch is intentionally small and focused.

There are no schedules, streaks, productivity scores, focus charts or easy bypass buttons planned for the core experience.

The normal interaction should remain:

```text
Tap a Latch
→ phone becomes Latched

Tap an authorised Latch
→ phone becomes Unlatched
```

No menus. No repeated decisions. No negotiation with yourself five minutes later.

## Project status

Latch is currently in early development.

The project began as a fork of [Lock](https://github.com/NathanLenias/lock-app), an excellent open-source NFC app blocker by Nathan Lenias. Lock provides the working Android foundation for NFC handling, accessibility-based app restriction, local persistence and session management.

Latch is being redesigned around a different access model and product philosophy:

- everything is blocked by default while Latched
- Modes define what is allowed through
- NFC devices can have separate latch and unlatch roles
- each Mode has a maximum automatic release time
- the physical interaction is the centre of the experience

The current codebase still contains inherited Lock features and terminology while this refactor is underway.

## Privacy

Latch is intended to remain local-first and private by design.

No account should be required, and the core functionality does not need internet access. Mode configuration, NFC tags and session state remain on the device.

## How it works

Latch uses Android's Accessibility Service to detect when an app opens while the phone is Latched.

If the app is not permitted by the active Mode, Latch returns the user to the home screen and provides simple visual feedback.

NFC tags provide the physical latch/unlatch interaction. During pairing, Latch writes routing information to compatible NFC tags so Android can hand scans directly to the app even when it is not already open.

No root access, VPN or device-admin tricks are required.

## Tech stack

- Kotlin
- Jetpack Compose
- Room
- DataStore
- Kotlin Coroutines and Flow
- Android NFC APIs
- Android Accessibility Service

## Building from source

Latch currently retains the original Lock project structure while the refactor is underway.

```bash
git clone https://github.com/TomColey/Latch.git
cd Latch
./gradlew assembleDebug
```

NFC features require a physical Android device with NFC hardware.

The inherited project currently uses the Satoshi typeface. These font files are not redistributed in the repository and must be added locally to `app/src/main/res/font/` when building the current codebase.

## Credits

Latch is built from the open-source [Lock](https://github.com/NathanLenias/lock-app) project by Nathan Lenias and is used under the terms of the MIT License.

The original project solved much of the difficult Android work that makes this idea possible. Latch is taking that foundation in a different direction, centred on selective access rather than maintaining a blocklist.

## License

This project is licensed under the [MIT License](LICENSE).