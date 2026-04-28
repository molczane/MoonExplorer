This version of the idea is much sharper:

> **Moon Explorer**: a mobile-only KMP/CMP app for Android + iOS that renders a realistic, lit 3D Moon, lets you rotate/zoom it with gestures, adjust Sun/light direction, and later adds a Koog-powered guide that can answer questions and navigate to lunar sites.

I think this is a strong app idea.

## Product shape

The app should probably not feel like a “space encyclopedia.” It should feel like a **tactile lunar globe**.

The core experience:

1. Open app.
    
2. See the Moon in 3D.
    
3. Drag to rotate.
    
4. Pinch to zoom.
    
5. Change Sun position.
    
6. Watch shadows move across craters.
    
7. Tap famous sites.
    
8. Later: ask the agent, “Show me Apollo 11,” and the Moon rotates/zooms there.
    

That is a very good scope.

## Technical direction

For Android + iOS only, I would split it like this:

```text
Shared KMP module
├─ Moon domain model
├─ crater / landing site data
├─ lat/lon math
├─ camera state
├─ Sun/light state
├─ search/navigation logic
├─ Koog agent integration later
└─ shared view models

Android app
├─ Compose UI
└─ 3D renderer view

iOS app
├─ Compose Multiplatform UI, or SwiftUI shell
└─ 3D renderer view
```

Compose Multiplatform is great for the app shell, panels, controls, lists, settings, and agent chat UI. Compose itself is not the thing I would rely on for serious 3D. I would embed a real 3D renderer inside the Compose screen.

## Renderer choice

The most serious candidate is **Filament**. It is a physically based real-time rendering engine that supports Android and iOS, and it is designed to be efficient on Android. ([GitHub](https://github.com/google/filament?utm_source=chatgpt.com "GitHub - google/filament: Filament is a physically based rendering ..."))

That matters because your app is all about:

- spherical mesh,
    
- realistic material,
    
- directional sunlight,
    
- normal maps,
    
- shadows / terminator line,
    
- smooth gestures,
    
- mobile performance.
    

Another candidate is **SceneView**, which is built around 3D/AR UI and uses Filament on Android; its current public positioning also includes iOS through SwiftUI/RealityKit rather than a single identical renderer everywhere. ([GitHub](https://github.com/SceneView/sceneview-android?utm_source=chatgpt.com "GitHub - sceneview/sceneview: 3D & AR SDK for Android (Jetpack Compose ..."))

My practical recommendation:

### Best serious path

Use:

- **Compose Multiplatform** for UI,
    
- **Filament** for the Moon renderer,
    
- platform-specific renderer host on Android/iOS,
    
- shared KMP code for state and logic.
    

### Best faster prototype path

Use:

- **SceneView on Android first**,
    
- validate the 3D Moon experience,
    
- then decide whether iOS should use Filament directly, RealityKit, or SceneView’s iOS path.
    

For a polished Android+iOS app, I would avoid assuming “pure Compose 3D” will be enough.

## The Moon rendering model

You do not need a complex Moon mesh at first.

For v1:

```text
UV sphere mesh
+ Moon albedo/color texture
+ Moon normal map
+ directional light = Sun
+ black/dark space background
+ camera orbit controls
```

This already gives a convincing Moon.

Later:

```text
UV sphere
+ displacement/elevation map
+ higher-res texture tiles
+ crater labels
+ landing-site markers
+ search navigation
```

I would not start with true geometric crater displacement. A normal map will give you the visual feeling of relief at much lower complexity.

## Sun placement tool

This is one of the best parts of the idea. It makes the app feel more “physical.”

You could expose Sun control in three layers.

### Simple mode

A circular control:

```text
Light Direction
[ 2D sun joystick / compass ]
```

User drags a small Sun icon around a circle. The Moon lighting updates live.

### Scientific-ish mode

Controls:

```text
Sun longitude
Sun latitude
Phase angle
Reset to front-lit
Reset to crescent
Reset to Apollo-style lighting
```

### Visual mode

Show a tiny mini-orbit diagram:

```text
Sun → Moon → Camera
```

The user drags the Sun around the Moon. This could be very satisfying.

For v1, I’d use a simple **Sun direction joystick** plus presets:

- Full Moon
    
- Half Moon
    
- Crescent
    
- Backlit rim
    
- Apollo landing-site lighting
    

## Gestures

Mobile interaction should be very direct:

|Gesture|Action|
|---|---|
|One-finger drag|Rotate Moon|
|Pinch|Zoom|
|Two-finger drag|Pan camera / optional|
|Double tap|Reset view|
|Tap marker|Open site card|
|Long press|Drop temporary pin / show coordinates|

I would avoid free camera movement in v1. Use an **orbit camera** looking at the Moon center.

## MVP definition

A really good MVP would be:

### Moon Explorer v1

- 3D Moon sphere
    
- realistic Moon texture
    
- directional sunlight
    
- drag rotation
    
- pinch zoom
    
- Sun direction control
    
- 10–20 named sites
    
- tap site → info sheet
    
- search site by name
    
- “fly to site” animation
    

No agent yet. No terrain displacement yet. No web search yet.

This is already a complete app.

## v1 named sites

Start with a curated small list:

- Apollo 11 landing site
    
- Apollo 12 landing site
    
- Apollo 15 landing site
    
- Apollo 17 landing site
    
- Tycho crater
    
- Copernicus crater
    
- Aristarchus crater
    
- Plato crater
    
- Clavius crater
    
- Shackleton crater
    
- Mare Tranquillitatis
    
- Mare Imbrium
    
- Mare Serenitatis
    
- Oceanus Procellarum
    
- Lunar south pole
    
- Lunar north pole
    

This gives enough content to make the Moon explorable without needing a huge database.

## “Fly to site” is important

This should be a core feature, not a later extra.

When user selects “Apollo 11,” the app should:

1. rotate Moon so the site faces the camera,
    
2. zoom in,
    
3. show marker,
    
4. open info card.
    

Later, the Koog agent can call the exact same function:

```kotlin
moonNavigator.flyTo(
    latitude = 0.674,
    longitude = 23.473,
    zoom = 3.5
)
```

That means you can design the app to be “agent-ready” without building the agent yet.

## Koog later

Koog is a good fit for the future agent layer because it is JetBrains’ Kotlin-based framework for building AI agents, and the docs describe support across JVM, Android, iOS, JavaScript, and WasmJS via Kotlin Multiplatform. ([Koog](https://docs.koog.ai/?utm_source=chatgpt.com "Overview - Koog"))

But I would keep Koog out of the initial app. Design the command surface now:

```kotlin
interface MoonExplorerActions {
    fun flyToLocation(id: MoonLocationId)
    fun setSunDirection(direction: Vec3)
    fun setZoom(level: Float)
    fun highlightLocation(id: MoonLocationId)
    fun compareLocations(first: MoonLocationId, second: MoonLocationId)
}
```

Then later Koog gets tools like:

```text
searchMoonLocations(query)
flyToMoonLocation(locationId)
setLightingPreset(preset)
explainCurrentView()
searchWebForMoonTopic(query)
```

That future agent could answer:

> “Where did Apollo 11 land?”

Then it:

1. answers briefly,
    
2. rotates the Moon to Mare Tranquillitatis,
    
3. zooms to Apollo 11,
    
4. highlights the site.
    

That is a killer feature.

## Complexity assessment

I’d rate the complexity like this:

|Feature|Difficulty|
|---|--:|
|Textured rotating Moon|Medium|
|Pinch zoom / drag rotation|Medium|
|Directional Sun lighting|Medium|
|Sun placement UI|Medium|
|Site markers|Medium-high|
|Fly-to-site camera animation|Medium-high|
|Accurate crater terrain|High|
|Huge texture streaming|High|
|Koog guide with tool calls|High|
|Web-searching agent|High|
|Fully scientific Moon ephemeris|Very high|

The good news: your desired v1 lives mostly in the **medium** range.

## Biggest risks

The main risks are not the idea itself. They are technical integration risks:

1. **3D renderer integration with CMP on iOS**
    
2. **keeping Android and iOS renderer behavior consistent**
    
3. **asset size**
    
4. **gesture feel**
    
5. **lat/lon marker positioning**
    
6. **lighting looking good on a plain sphere**
    

To reduce risk, the first prototype should answer only this:

> Can I render a nice Moon, rotate it, zoom it, and move the Sun?

Everything else comes after.

## Suggested app structure

```text
Moon Explorer

Main screen
├─ 3D Moon viewport
├─ Sun control button
├─ Search button
├─ Location sheet
└─ Settings

Location sheet
├─ Name
├─ Type: crater / mare / landing site / pole
├─ Coordinates
├─ Short description
├─ Fly to
└─ Favorite

Sun panel
├─ Light direction joystick
├─ Intensity slider
├─ Presets
│  ├─ Full Moon
│  ├─ Half Moon
│  ├─ Crescent
│  └─ Dramatic craters
└─ Reset
```

## Name ideas

- **Moon Explorer**
    
- **LunaScope**
    
- **MoonScope**
    
- **Lunar Atlas**
    
- **Luna Atlas**
    
- **Selene**
    
- **MoonGlobe**
    
- **Lunar Surface**
    
- **LunaView**
    
- **Sol & Selene**
    

My favorites:

1. **LunaScope**
    
2. **Moon Explorer**
    
3. **Lunar Atlas**
    

## Recommended build order

### Phase 0: Spike

One screen only:

- render sphere,
    
- apply Moon texture,
    
- rotate,
    
- zoom,
    
- move light.
    

This validates the whole project.

### Phase 1: App MVP

- CMP app shell,
    
- site database,
    
- search,
    
- markers,
    
- fly-to-site,
    
- Sun panel.
    

### Phase 2: Polish

- better texture,
    
- normal map,
    
- cinematic camera motion,
    
- labels that fade in/out,
    
- favorites,
    
- onboarding.
    

### Phase 3: Koog guide

- chat panel,
    
- local app tools,
    
- answer from bundled Moon data,
    
- later web search,
    
- “show me that place” commands.
    

## My verdict

This is a very feasible and attractive app.

The best version is not:

> “A scientific lunar GIS tool.”

It is:

> “A beautiful interactive Moon globe with realistic lighting, curated places, and eventually an AI guide.”

That is focused, buildable, demo-friendly, and perfect for agent-assisted development.