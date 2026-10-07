# Meeting-room door panels — market scan (Oct 2026)

## Headline findings

1. **Microsoft Graph only — never EWS.** Exchange Web Services starts shutting down in Exchange Online on **1 Oct 2026** and is fully removed **1 Apr 2027** ([Petri](https://petri.com/microsoft-exchange-web-services-2026/)). MeetEasier (the best-known OSS room display) is EWS-based — reference only.
2. **A custom Android tablet app cannot be a "Teams Rooms panel".** Microsoft's panel app runs only on certified hardware (Logitech Tap Scheduler, Crestron TSS-770, Yealink, Poly, Neat…) with a Teams Shared Device licence (~$8/device/mo). We compete in the third-party category: Meetingroom365, Joan, Skedda, Roomzilla.
3. **Price point to beat:** ~$8–10 per room per month + $150–1,000 hardware.

## Comparison

| Product                      | Hardware                        | Price (public)                                              | M365           | Notable                                                              | Weakness                                           |
| ---------------------------- | ------------------------------- | ----------------------------------------------------------- | -------------- | -------------------------------------------------------------------- | -------------------------------------------------- |
| Microsoft Teams Rooms panels | Certified panels only           | ~$800–1,000 + $8/device/mo (or Teams Rooms Pro $40/room/mo) | Native         | Book/extend/release, check-in + auto-release, LED, occupancy sensors | Hardware lock-in; check-in button bugs reported    |
| Joan (Visionect)             | E-ink 6"/13", or app on tablets | ~€49/mo + €9.99/device/mo                                   | Graph/Google   | Battery months, no cabling                                           | Grayscale, ~750 ms refresh; costly for small sites |
| Robin                        | iPad/Android                    | ~$15k/yr enterprise                                         | Graph          | Abandoned-meeting protection, wayfinding, analytics                  | Expensive; lost bookings after missed check-in     |
| Skedda                       | Any browser tablet (PWA)        | Quote                                                       | Graph          | Live status display                                                  | Display is an add-on; no LED/native                |
| Meetingroom365               | Native Android/iOS/Fire         | **$9/display/mo** or $99/yr                                 | Graph/Google   | Cheap, branding, mass deploy, ProDVX LED                             | Generic UI — the main "cheap tablet app" rival     |
| GoBright                     | ProDVX panels w/ LED, NFC       | ~£177/room/yr                                               | Graph + add-in | LED, NFC check-in, PoE                                               | Wants certified hardware                           |
| Evoko Naso                   | Dedicated LED/NFC panel         | ~$875 hw + ~$10/room/mo                                     | Graph/Google   | Ambient light, offline-tolerant                                      | Pricey hardware                                    |
| Eptura Teem / Condeco        | iPad/Android                    | Quote                                                       | Graph          | Check-in, desks/visitors                                             | Enterprise sales; dated UX                         |
| YAROOMS                      | Tablet app                      | $200–500/mo base + display add-on                           | Graph          | Rooms/desks/visitors                                                 | Display cost hidden                                |
| Roomzilla                    | Tablet app                      | $10–20/room/mo                                              | Graph/Google   | Display included                                                     | Small vendor                                       |
| ROOMZ                        | E-ink + PIR sensor              | ~£649 incl. 1 yr                                            | Graph          | Sensor auto-release                                                  | E-ink limits                                       |
| Room Display 6 / X           | Any tablet                      | $129/yr                                                     | Graph/Google   | Map view                                                             | Refresh/sync complaints                            |

## Table stakes (must ship)

- Colour status at a glance (green free / red busy / amber starting soon), current + next meeting, day timeline
- **Book now** (15/30/60 min or until next meeting)
- **Extend** and **end early / release**
- **Check-in** window with **auto-release of no-shows** (declines the meeting, organiser notified)
- Two-way sync with Outlook/Teams via the room mailbox, seconds-to-a-minute latency
- Kiosk lock, auto-start on boot, remote config
- Privacy: show or mask subject/organiser; honour private meetings

## Differentiators (pick later)

- LED status bar (needs panel hardware with an LED API — ProDVX, Qbic, IAdea)
- Occupancy-sensor auto-release; NFC/QR check-in
- **Attendee list on the door** — few products do it by default (privacy), so configurable display is a differentiator
- Teams join QR code for the current meeting
- Arabic / RTL UI — rare in this market
- Utilisation analytics (no-shows, booked vs used); offline cache with stale-data banner

## Anti-features (avoid)

- Per-device licence on top of per-room licence; hardware lock-in
- Unreliable check-in that never releases the room, or releases it too aggressively
- Sync lag / display not refreshing with no visible indication
- Showing private meeting subjects at the door

## Open source

| Project                                                           | Stack                    | M365                  | Notes                                                                                                      |
| ----------------------------------------------------------------- | ------------------------ | --------------------- | ---------------------------------------------------------------------------------------------------------- |
| [magweter/spacepad](https://github.com/magweter/spacepad)         | PHP + tablet app, Docker | Graph, Google, CalDAV | Active (Sept 2026); book/check-in/release. Dual licence. **Closest match — study its UX and Graph flows.** |
| [probits-as/MeetEasier](https://github.com/probits-as/MeetEasier) | React/Node               | Graph                 | MeetEasier ported to Graph                                                                                 |
| [danxfisher/MeetEasier](https://github.com/danxfisher/MeetEasier) | React/Node               | EWS (dead)            | Display only                                                                                               |
| [Anyesh/roombelt](https://github.com/Anyesh/roombelt)             | JS                       | Google/O365           | Ex-SaaS fork                                                                                               |

## Sources

Teams panels: https://learn.microsoft.com/microsoftteams/devices/teams-panels · https://learn.microsoft.com/en-gb/microsoftTeams/devices/teams-panels-certified-hardware · https://learn.microsoft.com/en-gb/microsoftTeams/devices/check-in-and-room-release · https://www.meetingroom365.com/blog/teams-rooms-pro-license-cost/
Vendors: https://meetingroom365.com/pricing · https://www.getapp.com/communication-software/a/joan/ · https://www.softwareadvice.com/cafm/robin-powered-profile/ · https://www.skedda.com/integrations/tablet-displays · https://prodvx.com/blog/gobright-certifies-prodvx-displays · https://shop.biamp.com/products/evoko-naso · https://www.capterra.com/p/138848/Roomzilla/ · https://www.capterra.com/p/204120/Room-Display-X/reviews/
EWS retirement: https://petri.com/microsoft-exchange-web-services-2026/
Gaps: no public per-room prices for Skedda, Teem/Eptura, YAROOMS displays, Appspace.
