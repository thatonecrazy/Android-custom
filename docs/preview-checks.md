# What each Customize setting does to its preview

Made by `PreviewsTest` (run `./gradlew :app:testDebugUnitTest`), which opens each Customize page, changes every setting the way its control does, and checks the preview: that the picture changes (how many pixels changed), that a short label appears for 2 seconds and then goes, or that the edge glow plays around the preview. A few settings can't be run on the test phone; those rows say why.

## Home screen

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Columns | changes the picture | 19535 | yes |
| Rows | changes the picture | 33302 | yes |
| Page dots | changes the picture | 14255 | yes |
| Page dots, with only one page | label "once you have a second page" for 2 s | 59671 | yes |
| Show the dock | changes the picture | 49692 | yes |
| Apps in the dock (5 to 7) | changes the picture | 6919 | yes |
| Dock color | changes the picture | 13582 | yes |
| Corner roundness | changes the picture | 281 | yes |
| Darken wallpaper | changes the picture | 130756 | yes |
| Hide the status bar | changes the picture | 12556 | yes |
| Status bar icons, while it's hidden | label "Hidden for now" for 2 s, showing that look meanwhile | 106158 | yes |
| Status bar icons | changes the picture | 270 | yes |
| Show notification dots | changes the picture | 156 | yes |
| Glow when you unlock | glow plays around the preview once | 16055 | yes |
| Glow color | glow plays around the preview once | 16430 | yes |
| Glow when the sound mode changes | glow plays around the preview once | 9617 | yes |
| Try it | glow plays around the preview once | 25552 | yes |
| Swipe up | label "Opens quick settings" for 2 s | 36091 | yes |
| Swipe down | label "ready to search" for 2 s | 52056 | yes |
| Double-tap | label "Locks the screen" for 2 s | 31451 | yes |
| App to open | label "Opens Maps" for 2 s | 26187 | yes |
| Weather: units | label "°F" for 2 s | 41456 | yes |
| Weather: city | label "in Lisbon" for 2 s | 64307 | yes |
| Wallpaper (a wallpaper set from Veneer is drawn small) | changes the picture | 131050 | yes |

## App list

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Columns | changes the picture | 29757 | yes |
| Show app names | changes the picture | 17285 | yes |
| Background | changes the picture | 128771 | yes |
| Text color | changes the picture | 8025 | yes |

## Icons

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Shape | changes the picture | 16211 | yes |
| Roundness | changes the picture | 5183 | yes |
| Style: Extra vivid | changes the picture | 49491 | yes |
| Style: Themed | changes the picture | 97354 | yes |
| Style: Tinted | changes the picture | 71550 | yes |
| Style: Color symbols | changes the picture | 71873 | yes |
| Style: Pastel | changes the picture | 97181 | yes |
| Style: Grayscale | changes the picture | 84459 | yes |
| Symbol color | changes the picture | 5025 | yes |
| Background color | changes the picture | 92406 | yes |
| Gradient background | changes the picture | 80542 | yes |
| Second color | changes the picture | 75600 | yes |
| Background (how solid) | changes the picture | 92203 | yes |
| Finish: Glass | changes the picture | 101713 | yes |
| Glass color | changes the picture | 67421 | yes |
| See-through | changes the picture | 67529 | yes |
| Shine | changes the picture | 27190 | yes |
| Depth | changes the picture | 75729 | yes |
| Shadow | changes the picture | 93124 | yes |
| Outline thickness | changes the picture | 20091 | yes |
| Outline color | changes the picture | 20633 | yes |
| Icon size (54 to 72 dp) | changes the picture | 396144 | yes |
| Icon size (72 to 80 dp, the largest) | changes the picture | 433872 | yes |
| Symbol size | changes the picture | 10225 | yes |
| Color tint strength | changes the picture | 69794 | yes |
| Color tint color | changes the picture | 106890 | yes |
| Show notification dots | changes the picture | 1690 | yes |
| Dot color, while dots are off | label "how they look when on" for 2 s, showing that look meanwhile | 63567 | yes |
| Dot color | changes the picture | 1114 | yes |
| Show the number | changes the picture | 2092 | yes |
| Give old-style icons a background | changes the picture | 17563 | yes |
| Old-style icons: background color | changes the picture | 10426 | yes |
| Reset icon settings | changes the picture | 446448 | yes |
| Icon pack | not run here: the test phone has no icon packs. Picking one redraws every icon in the preview, as on the home screen | - | yes |

## Colors

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Accent color | changes the picture | 1028 | yes |
| App names and widget text | changes the picture | 4338 | yes |
| Shadow behind text | changes the picture | 4926 | yes |
| Symbol color, while icons aren't Themed | label "for the Themed style" for 2 s, showing that look meanwhile | 124137 | yes |
| Icon background, while icons aren't Themed | label "for the Themed style" for 2 s, showing that look meanwhile | 133161 | yes |
| Behind old-style icons | changes the picture | 8791 | yes |
| Use the Themed style | changes the picture | 94035 | yes |
| Symbol color | changes the picture | 4750 | yes |
| Icon background | changes the picture | 89322 | yes |
| Dock | changes the picture | 19058 | yes |
| App list background | changes the picture | 85092 | yes |
| App list text | changes the picture | 14751 | yes |
| Behind old-style icons, while their backgrounds are off | label "Give old-style icons a background" for 2 s, showing that look meanwhile | 103544 | yes |
| Accent color, with no page dots and an own dot color | label "menus, highlights" for 2 s | 64317 | yes |
| Take colors from my wallpaper | not run here: the test phone can't report wallpaper colors. It sets the accent, dock and icon colors, each checked above | - | yes |

## Fonts

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Font: System condensed | changes the picture | 9329 | yes |
| Font: System light | changes the picture | 8653 | yes |
| Font: System thin | changes the picture | 6028 | yes |
| Font: System medium | changes the picture | 11537 | yes |
| Font: Small caps | changes the picture | 12562 | yes |
| Font: Serif | changes the picture | 12230 | yes |
| Font: Monospace | changes the picture | 10804 | yes |
| Font: Cursive | changes the picture | 396144 | yes |
| Font: Inter | changes the picture | 11344 | yes |
| Font: Outfit | changes the picture | 396930 | yes |
| Font: Space Grotesk | changes the picture | 397716 | yes |
| Font: Quicksand | changes the picture | 398502 | yes |
| Font: Comfortaa | changes the picture | 397716 | yes |
| Font: Playfair Display | changes the picture | 395358 | yes |
| Font: Lora | changes the picture | 399288 | yes |
| Font: JetBrains Mono | changes the picture | 12912 | yes |
| Font: Orbitron | changes the picture | 398502 | yes |
| Font: Bebas Neue | changes the picture | 397716 | yes |
| Font: Caveat | changes the picture | 396930 | yes |
| Font: Pacifico | changes the picture | 397716 | yes |
| Font: Press Start 2P | changes the picture | 407148 | yes |
| Show names on the home screen | changes the picture | 393000 | yes |
| Show names in the app list | changes the picture | 27741 | yes |
| Text size | changes the picture | 393000 | yes |
| Text color | changes the picture | 8951 | yes |
| Bold | changes the picture | 2627 | yes |
| Shadow | changes the picture | 9195 | yes |

## Wallpaper

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Gradient: style | changes the picture | 297553 | yes |
| Gradient: another style | changes the picture | 169275 | yes |
| Gradient: add a third color | changes the picture | 207380 | yes |
| Set as wallpaper | the preview shows the new wallpaper (a copy is kept for the small previews) | - | yes |
| Add pictures (shuffle) | label "2 new pictures" for 2 s | 72808 | yes |
| Shuffle | label "A new picture each time you lock" for 2 s | 62625 | yes |
| How often | label "every hour" for 2 s | 40431 | yes |
| Where | label "home screen only" for 2 s | 47575 | yes |

## Island

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Show the island | changes the picture | 15803 | yes |
| Charging | changes the picture | 43240 | yes |
| Music | changes the picture | 4283 | yes |
| Lyrics | changes the picture | 193055 | yes |
| Notifications | changes the picture | 197184 | yes |
| Alarms | changes the picture | 7536 | yes |
| Resting pill | changes the picture | 19503 | yes |
| Pop-ups stay for | label "Stay open for 7 seconds" for 2 s | 4052 | yes |
| Music or video? | label "Video: leaves the island as it is" for 2 s | 3711 | yes |
| Quick action tiles: add | changes the picture | 294576 | yes |
| Quick action tiles: remove | changes the picture | 664240 | yes |
| Quick action tiles: reorder | changes the picture | 50846 | yes |
| Quick actions drop-down | changes the picture | 522728 | yes |
| Background | changes the picture | 337828 | yes |
| Text and icons | changes the picture | 23738 | yes |
| Highlight | changes the picture | 522728 | yes |
| Glowing outline | changes the picture | 1595 | yes |
| Width | changes the picture | 8262 | yes |
| Height | changes the picture | 9597 | yes |
| From the top | changes the picture | 34280 | yes |
| Left or right | changes the picture | 21946 | yes |

## Your apps → one app

| Setting | How it shows | Pixels changed | Shows |
|---|---|---|---|
| Name | changes the picture | 679 | yes |
| Use a picture | changes the picture | 21581 | yes |
| Shape | changes the picture | 3505 | yes |
| Style | changes the picture | 17039 | yes |
| Finish | changes the picture | 10473 | yes |
| Icon background | changes the picture | 13356 | yes |
| Symbol color | changes the picture | 1092 | yes |
| Name color on the home screen | changes the picture | 431 | yes |
| Match the other apps again | changes the picture | 21905 | yes |
| Hide from the app list | a "Hidden from the app list" tag under it | - | yes |
| An icon from a pack | not run here: the test phone has no icon packs. A pack icon replaces the icon at the top like a picture does | - | yes |
