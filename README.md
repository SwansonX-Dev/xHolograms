# xHolograms

Modern Paper holograms built on native `TextDisplay` entities.

## Features

- Persistent YAML holograms
- MiniMessage formatting
- Animated lines with `||` frame syntax
- Built-in placeholders: `{online}`, `{max_players}`, `{world}`, `{time}`, `{tps}`
- Precise line editing and movement commands
- Configurable display settings for line height, brightness, billboard mode, alignment, background, shadow, and view range

## Commands

- `/holo create <id> [text]`
- `/holo addline <id> <text>`
- `/holo setline <id> <line> <text>`
- `/holo insertline <id> <line> <text>`
- `/holo removeline <id> <line>`
- `/holo movehere <id>`
- `/holo delete <id>`
- `/holo list`
- `/holo near`
- `/holo info <id>`
- `/holo reload`

Use `||` inside a line to animate between frames:

```text
<gold>Welcome||<yellow>Welcome||<white>Welcome
```

## Build

```bash
./gradlew build
```

The release jar is created at `build/libs/xHolograms-0.1.0.jar`.
