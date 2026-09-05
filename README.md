# DryRun

**You know what to say. Then you soften it.**

A rehearsal app for hard conversations — the counterpart pushes back, your
hedging is underlined as you type, and the silence is real. Kotlin
Multiplatform, Android and iOS.

[dryrun site](https://tushartechs.github.io/dryrun/) ·
[privacy](https://tushartechs.github.io/dryrun/privacy.html)

---

## What it does

You pick the conversation you're dreading — or write your own — and have it
badly first, against someone who answers in character and doesn't let you off
the hook.

Two things make it different from a chatbot with a prompt:

**Hedging is marked as you type.** *Just*, *maybe*, *sorry to bother you*,
*I might be wrong but* — underlined in the moment you write them, with a
running count of the phrase you lean on most. Marked, never blocked. The
detector is deterministic and runs entirely on the device, so it never waits
on the network and never sends anything to find a hedge.

**The silence is real.** When you finally make a specific point, the
counterpart says nothing for three full seconds. Most people fill it and undo
the thing they just did well. Whether you sat through it is measured on the
device and passed to the scorer as fact, not left to the model's opinion.

Afterwards, four criteria — named the specific thing, said what it cost, left
them room to answer, held the point under pushback — each quoted back verbatim
from your own transcript. Run it again and the app shows you the line that
changed, side by side.

## Privacy

The conversation is about a real colleague, so it stays on the phone. No
account, no name, no email, and no share button anywhere in the app, on
purpose. Messages are sent only to generate a reply and are never stored
server-side. [The full policy](PRIVACY.md) says exactly what that means.

## How it's built

| | |
|---|---|
| App | Kotlin Multiplatform + Compose Multiplatform — every screen shared |
| API | Cloudflare Worker (TypeScript) |
| Storage | Device-local key-value; nothing synced |
| Billing | RevenueCat, failing open to a free app when no store key is present |

Layout:

    shared/     Kotlin shared by both platforms, including all UI
    androidApp/ Android entry point
    iosApp/     iOS entry point (Xcode project)
    worker/     Cloudflare Worker: roleplay, scoring, safety, rate limiting
    docs/       The site and privacy policy, served by GitHub Pages

### Running it

    ./gradlew :androidApp:assembleDebug     # Android
    ./gradlew :shared:testAndroidHostTest   # shared tests
    cd worker && npm test                   # worker tests

For iOS, open `iosApp/` in Xcode and run.

## Notes from building it

The hedge detector is deterministic and offline, which means it can be tested
properly. The daily drill's content is checked against it by two invariants —
every softened line must contain a hedge, every straight answer must contain
none. Those two assertions caught an entire missing category: pre-emptive
self-deprecation. *I hope this isn't a stupid question.* *I'm probably being
dense.* *I hate to ask.* The thing the app is fundamentally about, and the
detector was blind to it.

Other things that only showed up by running the app rather than the tests: the
before/after bars drew the earlier run in the same colour as the empty track,
so every criterion read as 0 → 2 and contradicted the headline beside it; and
a run that failed to score set a line of red text at the top of a transcript
that had long since scrolled away, so tapping Done appeared to do nothing.

## Licence

Source-available, not open source — published so anyone can check what the app
does with what they type. Read it, quote it with credit; don't ship it. See
[LICENSE](LICENSE).
