# Competitive landscape

Why this file exists: Lumi's pitch is that a phone you already own can do what a category of £100-700
wearables does, without the cloud and without a subscription. That is a positioning claim, and it has to
survive a judge who owns one of these devices. Read this before writing demo copy.

Prices and plan details are approximate and were accurate to the best of our knowledge in mid-2026. This
market moves monthly — verify before quoting a number on stage.

## The category: AI capture wearables

A cluster of products appeared in 2024-2025 built on one idea: wear a microphone, record your day, and
let a cloud model turn it into notes and action items.

| Product | Hardware | Roughly | Where inference happens | Ongoing cost |
|---|---|---|---|---|
| **Limitless Pendant** (was Rewind) | Clip-on pendant | ~$199 | Cloud | Subscription for meaningful use |
| **Plaud Note** | Card-shaped recorder, magnetic to the phone | ~$159 | Cloud (GPT-class models) | Monthly minute quota, paid tiers above it |
| **Plaud NotePin** | Wearable pin | ~$169 | Cloud | Same quota model |
| **Bee** | Wristband / clip | ~$50 | Cloud | ~$12/mo. Acquired by Amazon, 2025 |
| **Omi** (was Friend, Based Hardware) | Open-source wearable | ~$89 | Cloud, with local options | Varies; developer ecosystem |
| **Friend** (Avi Schiffmann) | Necklace | ~$99 | Cloud | Companion-focused rather than productivity |

Two adjacent categories worth knowing:

- **Standalone AI devices.** The **Rabbit R1** (~$199) and the **Humane Ai Pin** (~$699 plus ~$24/mo)
  tried to replace the phone rather than clip onto it. The Ai Pin was discontinued in early 2025 and its
  devices lost their cloud service — a useful cautionary tale about hardware whose intelligence lives on
  someone else's server.
- **Display-first glasses.** **Even Realities G2** (~$599) and Meta's display glasses put a HUD in your
  field of view for notes, translation, and teleprompting. Different job, same dependency: the glasses
  are a screen and a mic, and the thinking happens elsewhere.

## The shared architecture, and the shared weakness

Strip the industrial design away and every product above is the same system:

```
microphone → radio → someone else's datacentre → model → summary → your phone
```

The device is a sensor. The intelligence is a subscription. That produces five structural consequences,
and they are what Lumi is positioned against:

1. **Your conversations are uploaded.** Not as a policy choice that could be changed — as the
   architecture. There is no model on a $50 wristband. Removing the upload removes the product.
2. **You pay monthly, forever.** Cloud inference has a per-minute cost, so the vendor has to meter you.
   The quota is not greed; it is arithmetic.
3. **It stops working when the service does.** The Ai Pin demonstrated this literally.
4. **It captures but cannot act.** This is the one that matters most and gets discussed least. Every
   device in that table can tell you that you agreed to send a document by Friday. None of them can set
   the reminder, find the document, silence your phone when you get to the office, or read the
   notification that says the deadline moved. They live *beside* your digital life and narrate it.
5. **It is another thing to own.** Charge it, remember it, keep it paired, replace it when the strap
   breaks or the company is acquired.

## What Lumi does differently

Lumi runs **Gemma 4 E2B** on the phone. 2.6GB of weights, resident in memory, no network required for
inference. That single fact reverses all five consequences.

| Their architecture | Lumi |
|---|---|
| Audio leaves the device for a datacentre | Inference is on-device. Measured: model resident in 14-21s on GPU, 8-12s on CPU, on a mid-range Exynos 1380 |
| Subscription, metered by the minute | No server, so no per-minute cost, so nothing to meter |
| Dies with the vendor's service | Weights are on your storage. It works if we disappear |
| Records and summarises | **Acts.** Reminders that fire with the app closed, device actions, file retrieval, routines |
| A second device to own and charge | The phone in your pocket. Nothing to buy, nothing to lose |

**The differentiator is not "on-device transcription".** Several competitors will ship that eventually,
and a dedicated device with its own battery has real advantages at it. The differentiator is that Lumi
lives on the device that *is* your digital life, so understanding and acting are the same system.

A pendant can tell you that you agreed to send the tax document. Lumi can find the tax document.

That is the demo, in one sentence: **capture, understanding, and action in one place, with none of it
leaving the phone.**

## Where the network is used, and why that is still honest

Lumi has exactly two features that touch the network, both opt-in and both user-initiated:

- **DuckDuckGo search**, only when the user asks for a search.
- **Gmail fetch over MCP**, read-only, one-way.

Neither sends anything to an inference service. The network *fetches*; it never *infers*. A fetched
email is summarised by Gemma on the phone — the body does not go anywhere else.

The owner ruled on Aug 16 that airplane-mode purity is no longer an acceptance criterion, and this is
why: **provable** beats **absent**. Two visible, opt-in, audited network features are a stronger privacy
claim than an app with no network code, because the audit log lets a user check rather than trust. Every
network request writes an audit event before it is made, and there is one settings control that turns all
of it off.

## Where Lumi is genuinely worse

State these before a judge does. Knowing the weaknesses is what makes the strengths credible.

- **Always-on capture.** A dedicated wearable with its own battery can listen all day. Lumi is
  session-based and push-to-talk, and that is a deliberate choice — an always-on wake word means battery
  drain, false triggers, and a background service that fails in front of an audience. It is honest to
  call this a limitation and to say the wake word is future work rather than claim it works.
- **Battery.** Their recording runs on their battery. Lumi's call mode runs on the phone's, and
  continuous ASR is the most demanding thing it does. Phase 6 measures a 20-minute run and writes the
  number down rather than hoping.
- **Microphone placement.** A pendant on your chest hears a room better than a phone in a pocket. For
  call mode the phone goes on the table, which is exactly how a Plaud Note is used anyway.
- **Raw model capability.** A frontier cloud model beats a 2B on-device model on hard reasoning. Lumi's
  answer is task fit — routing, classification, extraction, and summarisation are what a small local
  model is good at, and those are the tasks. Where the tradeoff bites, it bites on open-ended reasoning,
  which is not the product.
- **Convenience.** You take your phone out. A pin is already on your lapel.

## One line for the pitch

> The AI wearable market sells you a microphone and rents you the intelligence. Lumi puts the
> intelligence on the phone you already own — and because it lives there, it can act, not just listen.
