# Play Console listing — DryRun

Copy-paste ready. Character counts checked against Play's limits.

---

## App name (30 max)

```
DryRun: Hard Conversations
```
25 characters.

## Short description (80 max)

```
Rehearse the conversation you're dreading. Private, and only on your phone.
```
74 characters.

## Full description (4000 max)

```
You already know what you need to say. You've said it in the shower, in the
car, in your head at 2am. Then you get in the room and it comes out as "I just
wanted to check in, no worries if not."

DryRun is where you say it badly first.

Pick the conversation you're dreading — or write your own. The other person
answers in character: defensive, tearful, charming, or simply quiet. They push
back on vague openers. They don't let you off the hook.

WHAT MAKES IT DIFFERENT

Your hedging is underlined as you type. "Just", "maybe", "sorry to bother
you", "I might be wrong but" — marked in the moment you write them, with a
running count. Not blocked. Marked. So you can see the shape of your own
flinching before you send it.

And when you finally make a specific point, the other person says nothing.
Three full seconds of silence. Most people fill it and undo everything they
just said. Sitting in it is the whole skill.

AFTERWARDS

Four things, scored on what you actually said, quoted back to you word for
word:
- Named the specific thing
- Said what it cost
- Left them room to answer
- Held the point under pushback

Run it again and DryRun shows you the line that changed — your hedged version
and your straight one, side by side. That comparison is the point of the app.

TODAY'S LINE

One softened sentence a day, rewritten straight. Sixty seconds, entirely
offline. No streak to break and no notification chasing you about it.

BUILT FOR CONVERSATIONS YOU ACTUALLY HAVE

Delivering feedback. Setting a boundary. Saying no. Keep as many
conversations as you need — each one keeps its own runs and its own progress.

PRIVATE BY DESIGN

What you type is about a real colleague, so it stays on your phone. No
account. No name, no email. Transcripts and scores never leave your device,
and there is no share button anywhere in the app — on purpose. Messages are
sent to generate a reply and are never stored.

One reminder the evening before your conversation, if you want it. No daily
pestering.
```
~1,850 characters.

---

## Data safety form

**Does your app collect or share any of the required user data types? -> Yes**

Two types, and the reasoning for each. Read these against the code before you
submit; this is a legal declaration, not a form to be pasted through.

### 1. App activity -> Other user-generated content

| Field | Answer |
|---|---|
| Collected | **Yes** |
| Shared | **Yes** |
| Processed ephemerally | **Yes** |
| Required or optional | Required |
| Purpose | App functionality |

What it is: the counterpart description, the situation, and every message
typed during a rehearsal. It is sent to our Worker and forwarded to the model
provider to generate a reply.

"Collected" because it leaves the device. "Shared" because a third-party model
provider receives it. "Ephemeral" because the Worker writes none of it down --
it exists for the life of the request and is never logged or stored. Declaring
it shared *and* ephemeral is the honest combination: pretending it never leaves
would be false, and pretending we keep it would also be false.

### 2. Device or other IDs

| Field | Answer |
|---|---|
| Collected | **Yes** |
| Shared | No |
| Processed ephemerally | No |
| Required or optional | Required |
| Purpose | App functionality, and Fraud prevention / security / compliance |

What it is: `deviceId`, a random UUID generated on the device, exchanged for a
token and held server-side for 180 days, plus a request counter kept against
that token and the caller's IP for 48 hours.

An earlier draft of this file said No here, on the grounds that a random
install id is not an advertising or hardware identifier. That was wrong. Play's
own definition lists "Firebase installation ID" as an example, and this is
functionally the same thing: random, per-install, persistent, stored
server-side. It goes to no third party, so shared is No.

### The rest

Everything else is **not collected**: no name, email, phone, address, contacts,
photos, location, financial info, health data, calendar, or files. Transcripts,
scores and run history never leave the device at all.

- **Encrypted in transit?** Yes, HTTPS throughout.
- **Can users request deletion?** Yes -- "Delete everything" in the app wipes
  local data immediately, and the privacy policy carries a contact address for
  the server-side token. It expires by itself within 180 days regardless.

## Content rating questionnaire

- Category: **Reference, News, or Educational** (or Productivity)
- Violence, sexual content, profanity, drugs, gambling: **No** to all
- User-generated content shared between users: **No** — nothing a user writes
  is ever visible to another user
- Expected rating: **Everyone / PEGI 3**

## Package name

`com.techtush.dryrun` — matches the Play Console app entry and the publisher
convention set by com.techtush.machinecharades. Permanent once published.

## App access declaration — this one changes later

**Today: "No — no part of my app is restricted."**

True only because `ANDROID_API_KEY` in `StoreKeys.android.kt` is blank. With no
key, billing reports unavailable, `plusActive` computes to true, the two-run
cap never engages and the paywall is unreachable. A reviewer sees the whole app
with no credentials.

**⚠️ The moment RevenueCat is wired up, this declaration becomes false.**

The two-run cap activates and the paywall appears, which is an "access tier"
under Play's second bullet. When you upload the first build carrying a real
key, change App access to **Yes** in the same session, *before* sending for
review — not afterwards. A live paywall sitting under a "nothing is restricted"
declaration is a misrepresentation, not a paperwork slip.

"Yes" needs reviewer instructions, because there is no login to hand over.
Either works:

- **License testing** (Setup → License testing): add the reviewer account, then
  write "The first two rehearsals are free. A subscription is required beyond
  that. This account has license testing enabled, so it can be purchased at no
  cost."
- **Promo code** (Monetize → Promotion codes): generate one and paste it in.

Recommendation: leave billing out of the closed test entirely. Testers get the
app free and unlimited, which is what you want them reacting to, and the
declaration cannot drift out of date while you are not looking.

## Other required fields

- **Category**: Productivity (alternative: Education)
- **Tags**: productivity, self-improvement, communication
- **Privacy policy URL**: required — host PRIVACY.md and paste the URL
- **Countries**: include the **United States**. An India-only release is
  rejected at intake.
- **Contact email**: shown publicly on the listing

## Graphics checklist

| Asset | Size | Status |
|---|---|---|
| App icon | 512x512 PNG | Ready: `androidApp/play-icon-512.png` |
| Feature graphic | 1024x500 PNG | **Needed** |
| Phone screenshots | min 2, max 8 | **Needed** — see below |

Screenshots worth taking, in this order — they tell the story without copy:
1. The rehearsal mid-sentence, hedges underlined in amber
2. The silence beat — "They're not saying anything"
3. The feedback screen with the four criteria and quotes
4. The before/after card — "You hedged 7 fewer times"
5. Today's line
