# Hikari

An original, privacy-first AniList discovery and tracking client for Android.

## Current foundation

The app establishes the visual direction and an adaptive navigation shell:

- Five primary destinations: Home, Discover, Library, Calendar, and Profile.
- A content-first home experience with continue tracking, airing, and trending sections.
- A responsive navigation pattern: floating bottom navigation on compact windows and a navigation rail on expanded windows.
- Theme tokens for **System**, **AMOLED**, and **Aurora**, plus independent **Blur**, **Liquid**, and **Off** navigation treatments.
- AniList GraphQL transport, encrypted access-token storage, OAuth callback handling, and a Room-backed media cache.

## Required local configuration

Create an AniList OAuth application with the redirect URL `hikari://oauth`, then add its client ID to `~/.gradle/gradle.properties`:

```properties
ANILIST_CLIENT_ID=your_client_id
```

The current network layer targets AniList's public GraphQL endpoint. No streaming, downloading, or piracy-related features are included.
