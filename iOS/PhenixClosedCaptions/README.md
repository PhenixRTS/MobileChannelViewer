# Phenix Closed Captions

Support library providing Closed Captions integration using PhenixSdk under-the-hood.

## Requirements
* iOS 12.0+
* Xcode 11+
* Swift 5.1+

## 1. Installation

### CocoaPods (using Development Pods)

[CocoaPods](https://cocoapods.org) is a dependency manager for Cocoa projects. For usage and installation instructions, visit their website.
To integrate Phenix Closed Captions into your Xcode project using CocoaPods:

1. Copy `PhenixClosedCaptions` directory inside the ROOT directory of your iOS project.

2. Modify your `Podfile`:

```ruby
source 'https://cdn.cocoapods.org/'
source 'git@github.com:PhenixRTS/CocoaPodsSpecs.git' # Phenix private repository

target 'your app name'
  use_frameworks!
  pod 'PhenixClosedCaptions', :path => './PhenixClosedCaptions' # Closed Captions development pod
```

### Manually

If you prefer not to use [CocoaPods](https://cocoapods.org), you can integrate Phenix Closed Captions into your project manually.

1. Open the `PhenixClosedCaptions` folder, and drag the `Source` folder into the Project Navigator of your application's Xcode project.

> Check the "Copy items if needed" checkbox and under the "Add to targets" check the applications destination target as well.

2. Delete `Source/Supporting Files` folder and its content.

3. Make sure that your application target has embeded `PhenixSdk` framework because the `PhenixClosedCaptions` depends on it.

## 2. Usage

1. _(only when using CocoaPods)_ Import `PhenixClosedCaptions` framework

```
import PhenixClosedCaptions
```

2. Create a `PhenixClosedCaptionsService` instance by providing to it `PhenixRoomService` instance. Keep a strong reference to the  `PhenixClosedCaptionsService` instance:

```
let roomService: PhenixRoomService = ...  // previously obtained
let closedCaptionsService = PhenixClosedCaptionsService(roomService: roomService)
```

3. Create a `PhenixClosedCaptionsView` instance. It will contain all the Closed Captions in it. Add this view to the view hierarchy and pass it to the `PhenixClosedCaptionsService` instance:

```
let closedCaptionsView = PhenixClosedCaptionsView()
view.addSubview(closedCaptionsView)

// TODO: Set `closedCaptionsView` size...

closedCaptionsService.setContainerView(closedCaptionsView)
```

## 3. Advanced Usage

1. If you want to provide a custom functionality for Closed Captions, you can conform to the `PhenixClosedCaptionsServiceDelegate` protocol, implement required method and provide your own logic, for example:

```
extension ViewController: PhenixClosedCaptionsServiceDelegate {
    func closedCaptionsService(_ service: PhenixClosedCaptionsService, didReceive message: PhenixClosedCaptionsMessage) {
        DispatchQueue.main.async { [weak self] in
            self?.label.text = message.textUpdates.first?.caption
        }
    }
}
```

2. Set the delegate for the `PhenixClosedCaptionsService` instance to receive `PhenixClosedCaptionsServiceDelegate` updates:

```
closedCaptionsService.delegate = self
```

3. If you do not want to rely on the provided *out-of-box* user interface for the Closed Captions, you can disable automatic user interface updates by setting `PhenixClosedCaptionsService` instance container view to `nil`:

```
closedCaptionsService.setContainerView(nil)
```

4. If you want to dynamically switch the Closed Captions service on/off, you can do that by changing `PhenixClosedCaptionsService.isEnabled` parameter:

```
closedCaptionsService.isEnabled = true
```

## 4. Customization

It is possible to provide your own user interface properties to customize the default look of the Closed Captions.

1. Change provided configuration properties on `PhenixClosedCaptionsView` instance:

```
closedCaptionsView.configuration.anchorPointOnTextWindow = CGPoint(x: 0.0, y: 0.0)
closedCaptionsView.configuration.positionOfTextWindow = CGPoint(x: 1.0, y: 1.0)
closedCaptionsView.configuration.widthInCharacters = 32
closedCaptionsView.configuration.heightInTextLines = 1
closedCaptionsView.configuration.textBackgroundColor = UIColor.black
...
```

2. Provide your own customized property configuration by creating `PhenixClosedCaptionsConfiguration` instance and setting required properties to it and then provide that configuration to the `PhenixClosedCaptionsView` instance:

```
let customConfiguration = PhenixClosedCaptionsConfiguration(...)
closedCaptionsView.configuration = customConfiguration
```

3. Modify  `PhenixClosedCaptionsConfiguration.default` configuration and provide that to the `PhenixClosedCaptionsView` instance:

```
let modifiedConfiguration = PhenixClosedCaptionsConfiguration.default
modifiedConfiguration.textBackgroundAlpha = 0.5
closedCaptionsView.configuration = modifiedConfiguration
```
