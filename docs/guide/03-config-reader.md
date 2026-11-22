# 03 — ConfigReader (settings without editing code)

File: `automation-core/.../core/config/ConfigReader.java`

## Problem it solves
Locally you want a visible Chrome with 3 threads; Jenkins wants headless with 5 threads and maybe another
URL. We never want to edit code or commit a changed file for that.

## How it decides a value (highest wins)
1. `-Dthreads=5` on the Maven command line (Jenkins parameters end up here)
2. Environment variable `THREADS` (key upper-cased, `.` → `_`, so `browser.rotation` → `BROWSER_ROTATION`)
3. `config.properties` on the classpath (the defaults committed in git)

## API
| Method | Use |
|---|---|
| `get(key)` | Required value; throws a message that tells you the three ways to set it |
| `get(key, default)`, `getInt`, `getDouble`, `getBoolean` | Optional value with default |
| `getList(key)` | `chrome, firefox` → `["chrome","firefox"]` (Stream API: split → trim → filter) |
| `find(key)` | `Optional<String>` for custom handling |

## Design notes
- The environment map is passed into the constructor, so tests can fake env vars (`Map.of("HEADLESS","true")`).
- Returning `Optional` avoids `null` checks everywhere.
