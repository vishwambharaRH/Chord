# Chord - A minimalist Chromium browser for MacOS

This project arises from a personal pain point, which is the sheer horror of looking at your Chrome tabs' RAM usage.
I thought it would be better on Brave, but boy was I wrong.

The end goal of this project is to somehow make a browser that ends up performing better than Brave, on the RAM+battery usage level.

## Deliverables
- Reduced RAM usage in all conditions, especially in idle
- Reduced battery usage, by extension
- Smoother and snappier graphics

## How?
- Use some form of native WebKit to scaffold GUI and not build everything directly with Chromium.
I have no idea how to do that (yet)