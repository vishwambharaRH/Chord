# Chord - A self-hosted music analytics page

It is exactly what the title intends it to be. 

I'm working on the schemas and structure necessary for this. 

## The plan
To create a personalized music analytics page, much like Spotistats, last.fm and other such analytics aggregators.

## Why?
I like listening to music. A lot. But over the last few months I've been trying to actually collate what I've listened to and how much of it, over the multiple streaming services I have: 
- Spotify (for its overall versatility)
- Apple Music (for sound quality; I know Spotify has lossless too now but Apple Music better)
- YouTube Music (for large concert videos, and things we don't get on the other music apps)
- Bandcamp (TBD in the future)

## Exactly what?
- Extract currently playing music on music services, count playing numbers, present analytics in an appealing format.
- Utilize past analytics from listening services (JSONs, .html, txts, etc.) for a complete profile.
- Recommendation engine (in the future, not now)

## How?
- Extract music playing using dedicated APIs, call webapp scrobbler for YT Music.
- Save analytics to either SQLite or NoSQL DB (private, unhosted).
- Set GitHub actions to push stats on something (README perhaps, or even my portfolio website)

## Some possible difficulties
Spotify and Apple Music offer dedicated APIs to access what the user is playing at that point in time. YouTube Music does not. Hence some complexity.