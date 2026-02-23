# Live Wallpaper Technology Documentation

## Overview

The Breezy Weather live wallpaper is a custom Android `WallpaperService` implementation that renders animated weather scenes using Android's Canvas API. It supports multiple weather conditions including clear sky, cloudy, thunder, rain, snow, hail, fog, haze, and wind.

## Architecture

### Core Components

```
MaterialLiveWallpaperService (WallpaperService)
    └── WeatherEngine (Engine)
        ├── MaterialPainterView (View)
        │   └── WeatherAnimationImplementor (abstract)
        │       ├── SunImplementor
        │       ├── MeteorShowerImplementor
        │       ├── CloudImplementor
        │       ├── RainImplementor
        │       ├── SnowImplementor
        │       ├── HailImplementor
        │       ├── WindImplementor
        │       └── (thunder handled within RainImplementor/CloudImplementor)
        ├── DelayRotateController (RotateController)
        ├── IntervalComputer
        └── SensorEventListener (Gravity sensor)
```

### Engine Flow

1. **onCreate**: Initializes HandlerThread, SensorManager, and SurfaceHolder
2. **onVisibilityChanged**: 
   - Starts/stops animation loop
   - Registers gravity sensor
   - Enables/disables orientation detection
   - Loads weather data from config
3. **onDraw callback** (via AsyncHelper.intervalRunOnUI):
   - Updates rotation based on device orientation
   - Calls `updateData()` on implementor
   - Calls `draw()` on implementor
   - Posts canvas back to Surface

## Resolution

### Adaptive Rendering

The wallpaper uses adaptive resolution controlled by `mAdaptiveSize`:

```kotlin
// In MaterialLiveWallpaperService.kt
mAdaptiveSize[0] = (mSizes[0] * resolution).toInt()
mAdaptiveSize[1] = (mSizes[1] * resolution).toInt()
```

Where `resolution` is a configurable float (default: `1.0f`) stored in SharedPreferences.

### How It Works

1. **Full Canvas Size** (`mSizes`): The actual Surface dimensions from the display
2. **Adaptive Size** (`mAdaptiveSize`): Scaled rendering size based on config
3. **Drawing**: Background is drawn to full canvas, but weather elements are rendered to adaptive size with translation

```kotlin
canvas.withTranslation(
    (mSizes[0] - mAdaptiveSize[0]) / 2f,
    (mSizes[1] - mAdaptiveSize[1]) / 2f
) {
    mImplementor!!.draw(...)
}
```

This centers the scaled rendering, effectively cropping/zooming the scene.

### Configuration Options

| Setting | Key | Default | Range | Description |
|---------|-----|---------|-------|-------------|
| Resolution | `KEY_RESOLUTION` | 1.0 | 0.25 - 2.0 | Scale factor for rendering size |
| FPS | `KEY_DRAW_INTERVAL` | 60 | 15-60 (fps) or >60 (ms) | Frame rate control |
| Animations | `KEY_ANIMATIONS_ENABLED` | false | boolean | Enable/disable animations |

### Frame Rate Control

```kotlin
// In MaterialLiveWallpaperService.kt
private fun setDrawInterval() {
    val drawInterval = configManager.drawInterval
    val intervalMs = if (drawInterval <= 60) {
        // Interpret as fps
        (1000.0 / drawInterval.coerceIn(1, 60)).toLong()
    } else {
        // Interpret as ms
        drawInterval.toLong()
    }
    mIntervalController = AsyncHelper.intervalRunOnUI(...)
}
```

**FPS Modes**:
- `15` fps → `66.67 ms` interval
- `30` fps → `33.33 ms` interval  
- `60` fps → `16.67 ms` interval

**Direct ms Mode**:
- `1000` → `1000 ms` interval (very slow, may cause stutter)
- Values >60 are treated as milliseconds

### FPS Calculation

The `IntervalComputer` measures actual time between frames:

```kotlin
class IntervalComputer {
    var interval = 0.0  // milliseconds
        private set
    
    fun invalidate() {
        mCurrentTime = System.currentTimeMillis()
        interval = (if (mLastTime == -1L) 0 else mCurrentTime - mLastTime).toDouble()
        mLastTime = mCurrentTime
    }
}
```

This provides **actual** frame-to-frame delta time for smooth animation regardless of target FPS setting.

## Mathematical Models

### 1D Trigonometric Motion

All weather particle movement uses sine-based physics for periodic motion.

#### Cloud Movement

```kotlin
centerX = mInitCX + sin(rotation2D * π / 180.0) * 0.40 * radius * moveFactor
centerY = mInitCY - sin(rotation3D * π / 180.0) * 0.50 * radius * moveFactor
```

**Parameters**:
- `rotation2D`: Device tilt around vertical axis (0-180°)
- `rotation3D`: Device tilt around horizontal axis (0-180°)
- `moveFactor`: Per-cloud speed variation (1.3-2.0x based on size)
- `radius`: Cloud radius controls wind sensitivity

#### Rain/Snow/Wind Movement

```kotlin
y += speed * interval * (scale^1.5 ± 5 * sin(Δrotation3D * π/180) * cos(θ))
x -= speed * interval * 5 * sin(Δrotation3D * π/180) * sin(θ)
```

Where θ is a fixed angle (8° for rain, 16° for wind, 60° for meteors).

### Cloud Scaling (Pulsing)

Clouds dynamically scale using half-period sine:

```kotlin
radius = if (progress < 0.5 * duration) {
    initRadius * (1 + (scaleRatio - 1) * progress / 0.5 / duration)
} else {
    initRadius * (scaleRatio - (scaleRatio - 1) * (progress - 0.5 * duration) / 0.5 / duration)
}
```

This creates a smooth expansion/collision effect over the cloud's duration cycle.

### Star Twinkling

Stars use a triangular wave for opacity:

```kotlin
alpha = if (progress < 0.5 * duration) {
    progress / 0.5 / duration
} else {
    1 - (progress - 0.5 * duration) / 0.5 / duration
}
```

### Thunder Flash

A 4-stage ramp/decay pattern:

```kotlin
alpha = if (progress < 0.25 * duration) {
    progress / 0.25 / duration           // Ramp up (25%)
} else if (progress < 0.5 * duration) {
    1 - (progress - 0.25 * duration) / 0.25 / duration   // Ramp down (25%)
} else if (progress < 0.75 * duration) {
    (progress - 0.5 * duration) / 0.25 / duration   // Ramp up (25%)
} else {
    1 - (progress - 0.75 * duration) / 0.25 / duration   // Ramp down (25%)
}
```

### Gravity Sensor Physics

The sensor data calculates effective gravity vector:

```kotlin
g2D = sqrt(aX² + aY²)           // 2D gravity magnitude
g3D = sqrt(aX² + aY² + aZ²)     // 3D gravity magnitude

cos2D = aY / g2D                 // Horizontal angle
cos3D = g2D / g3D               // Vertical angle

rotation2D = degrees(acos(cos2D)) * sign(aX)
rotation3D = degrees(acos(cos3D)) * sign(aZ)
```

Device orientation adjustments subtract 90° for landscape modes and 180° for bottom-up.

## Particle Systems

### Cloud Implementor

| Type | Count | Colors | Features |
|------|-------|--------|----------|
| TYPE_CLOUD | 6 | 2 tone sets | Day/night colors |
| TYPE_CLOUDY | 6 | 2 tone sets | Same as CLOUD |
| TYPE_THUNDER | 6 | 2 tone sets | + Thunder effect |
| TYPE_FOG | 9 | 3 tone sets | Fog banks |
| TYPE_HAZE | 9 | 3 tone sets | Haze layers |

### Rain Implementor

| Type | Rain Count | Features |
|------|------------|----------|
| TYPE_RAIN | 75 | 3-color gradient |
| TYPE_THUNDERSTORM | 75 | + Thunder effect |
| TYPE_SLEET | 45 | 3-color gradient |

### Snow Implementor

- **Count**: 50 particles
- **Speed**: 1.5x base velocity
- **Size**: Random 0.005-0.012 × canvasSize

### Hail Implementor

- **Count**: 51 particles
- **Speed**: `min(width,height) / 150`
- **Rotation**: 360° at `360/500` rad/ms

### Wind Implementor

- **Count**: 160 particles
- **Speed**: `canvasSize / (1000 * (0.5 + random())) * 6`

### Meteor Shower Implementor

- **Meteors**: 10 (when animate=true)
- **Stars**: 70
- **Meteor Speed**: `viewWidth / 200`
- **Revive Time**: 5-25 seconds random

## Sensor System

### Gravity Sensor

- **Type**: `Sensor.TYPE_GRAVITY`
- **Rate**: `SENSOR_DELAY_FASTEST` (lowest latency)
- **Purpose**: Device orientation detection

### Orientation Listener

- **Type**: `OrientationEventListener`
- **Purpose**: Detect device rotation (TOP/LEFT/BOTTOM/RIGHT)

## Configuration Manager

### SharedPreferences File: `live_wallpaper_config`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `weather_kind` | String | "auto" | User-selected weather |
| `day_night_type` | String | "auto" | Day/night override |
| `animations_enabled` | Boolean | false | Enable animation loop |
| `draw_interval` | Int | 60 | FPS (15-60) or ms (>60) |
| `resolution` | Float | 1.0f | Scaling factor |
| `sensors_enabled` | Boolean | true | Gravity sensor |

## Optimization Opportunities

### 1. Canvas Draw Optimization

**Current Issue**: `MaterialPainterView.onDraw()` calls `super.onDraw()` first, which fills the entire canvas with the background color. This is wasteful since we immediately redraw everything.

**Potential Fix**: Use `TextureView` or `SurfaceView` instead of `View` to avoid automatic canvas clearing:

```kotlin
// Current (inefficient):
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)  // Fills canvas with background
    // ... draw everything ...
}

// Optimized approach:
// Use SurfaceView and only draw what changed
```

### 2. Animation Update Skipping

**Current Issue**: When `animate=false`, the wallpaper still calls `updateData()` and `draw()` on every frame, just with `interval=0`.

**Potential Fix**: Skip full animation updates when animations disabled:

```kotlin
if (!mAnimate) {
    if (hasDrawn) return  // Already drew, no need to redraw
    hasDrawn = true
}
```

### 3. Particle Count Scaling

**Current Issue**: Particle counts are fixed regardless of device performance or screen size.

**Potential Fix**: Scale particle count based on device profile:

```kotlin
val particleScale = when {
    devicePerformance == LOW -> 0.5f
    devicePerformance == HIGH -> 1.5f
    else -> 1.0f
}
val rainCount = (RAIN_COUNT * particleScale).toInt()
```

### 4. Canvas Matrix Optimization

**Current Issue**: Repeated `canvas.rotate()` calls create new matrix operations each frame.

**Potential Fix**: Calculate final matrix once per frame and apply:

```kotlin
// Current: Multiple rotate calls
// Optimized: Pre-compute rotation matrix
val rotationMatrix = Matrix().apply {
    postRotate(finalRotation, pivotX, pivotY)
}
canvas.setMatrix(rotationMatrix)
```

### 5. Memory Allocation Reduction

**Current Issue**: Random number generation and array creation on every frame initialization.

**Potential Fix**: Use object pooling for temporary objects:

```kotlin
// Reuse Random instances per class, not per frame
private val mRandom = Random()

// Pre-allocate arrays instead of creating new ones
private val tempRect = RectF()
```

### 6. Floating Point Precision

**Current Issue**: Using `Double.pow()` for simple power operations.

**Potential Fix**: Use multiplication for small integer powers:

```kotlin
// Current: scale.toDouble().pow(1.5)
// Faster: scale * sqrt(scale)  OR scale * scale for power of 2
```

### 7. Trigonometric Optimization

**Current Issue**: Repeated `sin()` and `cos()` calls with the same angles.

**Potential Fix**: Cache computed values when same rotation is used:

```kotlin
val sinRot2D = sin(rotation2D * π / 180.0)
val cosRot2D = cos(rotation2D * π / 180.0)
val sinRot3D = sin(rotation3D * π / 180.0)
val cosRot3D = cos(rotation3D * π / 180.0)
```

### 8. Background Image Optimization

**Current Issue**: Background drawable is recreated on weather change.

**Potential Fix**: Cache background drawables for each weather type:

```kotlin
// Cache in WeatherImplementorFactory
private val backgroundCache = mutableMapOf<Pair<Int, Boolean>, Drawable>()
```

### 9. Star/Meteor Spacing Optimization

**Current Issue**: Random placement can cause overlap and visual artifacts.

**Potential Fix**: Use Poisson disk sampling or regular grid with random offsets:

```kotlin
// Instead of pure random:
val x = random.nextInt(width)
val y = random.nextInt(height)

// Use jittered grid:
val gridSize = canvasSize / sqrt(starCount)
val x = (gridCol * gridSize + random.nextFloat() * gridSize).toInt()
```

### 10. Thunder Effect Optimization

**Current Issue**: Thunder has complex alpha calculations on every frame update.

**Potential Fix**: Use a Lookup Table (LUT) for the thunder flash pattern:

```kotlin
// Pre-computed flash pattern (simplified)
private val THUNDER_LUT = FloatArray(100) {
    val t = it / 100f
    when {
        t < 0.25f -> t / 0.25f
        t < 0.5f -> 1 - (t - 0.25f) / 0.25f
        t < 0.75f -> (t - 0.5f) / 0.25f
        else -> 1 - (t - 0.75f) / 0.25f
    }
}
```

## Potential GPU Acceleration

### Shader-Based Rendering

**Concept**: Move particle calculations to GPU shaders

**Benefits**:
- 1000s of particles with minimal CPU overhead
- Hardware-accelerated rotation/scaling
- Parallel updates

**Implementation Sketch**:

```kotlin
// Vertex shader for particle positions
#version 300 es
uniform float uTime;
uniform vec2 uGravity;
in vec2 aPosition;
in float aSize;
out float vAlpha;

void main() {
    float speed = aSize * 0.01;
    vec2 pos = aPosition + uGravity * speed * uTime;
    gl_Position = vec4(pos, 0.0, 1.0);
    vAlpha = sin(uTime * 2.0);  // Twinkle effect
}
```

**Challenges**:
- Android OpenGL ES 2.0/3.0 compatibility
- More complex codebase
- Potential battery drain if not optimized

## Current Limitations

1. **No GPU Acceleration**: All rendering done on CPU
2. **Fixed Particle Counts**: No dynamic scaling based on device
3. **Redundant Canvas Operations**: Background fill on every frame
4. **No Frame Skipping**: Always updates even when not visible
5. **Memory Leaks**: HandlerThread created on every engine creation (not cleaned up properly in all paths)

## Future Enhancements

1. **Parallax Depth Layers**: Multiple background layers at different depths
2. **Dynamic Weather Changes**: Transition between weather types
3. **Particle Interaction**: Raindrops hitting clouds, etc.
4. **Dynamic Lighting**: Sun position based on time of day
5. **Weather Pattern History**: Show real weather patterns from last 24h
