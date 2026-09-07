# Hikari

An original, privacy-first AniList discovery and tracking client for Android.

## Current foundation

This initial Compose implementation establishes the visual direction and an adaptive navigation shell:

- Five primary destinations: Home, Discover, Library, Calendar, and Profile.
- A content-first home experience with continue tracking, airing, and trending sections.
- A responsive navigation pattern: floating bottom navigation on compact windows and a navigation rail on expanded windows.
- Theme tokens for **System**, **AMOLED**, and **Aurora**, plus independent **Blur**, **Liquid**, and **Off** navigation treatments.
- Sample-only local content. No streaming, downloading, or piracy-related features are included.

## Next steps

The UI shell intentionally keeps account and network concerns out of composables. AniList OAuth, Apollo GraphQL, Room caching, and secure token storage should be added behind repository interfaces before connecting this UI to live data.
