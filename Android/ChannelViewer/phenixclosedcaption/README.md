# Phenix Closed Captions

Support library providing Closed Captions integration using PhenixSdk under-the-hood.

## Usage

1. Import `phenixclosedcaption` module

- Make sure that in your Module:app gradle file you have this line:
```
dependencies {
    implementation project(":phenixclosedcaption")
    ...
}
```
- Make sure that in your settings.gradle file you have this line at the top:
```
include ':phenixclosedcaption'
```

2. Add `PhenixClosedCaptionView` to your activity XML:

- Position the view over the SurfaceView you wish to draw subtitles onto:
```
<com.phenixrts.suite.phenixclosedcaption.PhenixClosedCaptionView
    android:id="@+id/closed_caption_view"
    android:layout_width="0dp"
    android:layout_height="0dp"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintBottom_toBottomOf="@id/video_surface_view" />
```

3. Initialize the `PhenixClosedCaptionView` by calling `subscribe` function:

```
val roomService: RoomService = ...  // Previously obtained and mandatory. Used to subscribe to chat messages.
val mimeTypes: List<String> = ... // Previously obtained (e.g.: application/Phenix-CC). If not provided - defaults are initialized.
val configuration: ClosedCaptionConfiguration = ... // The default configuration to use. If not provided - defaults are initialized.
closed_caption_view.subscribe(roomService, mimeTypes, configuration)
```

4. Use `updateConfiguration(..)` or `defaultConfiguration` variable to update default values on the fly:

- Updates a single value keeping others in-tact:
```
closed_caption_view.defaultCOnfiguration.wordWrap = true
```
- Updates all values with the ones specified or defaults otherwise:
```
closed_caption_view.updateConfiguration(ClosedCaptionConfiguration(wordWrap = false))
```
