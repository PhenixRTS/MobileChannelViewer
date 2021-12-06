# MobileChannelViewer

Simple ReactNative Phenix channel viewer, based on Phenix WebSDK

# Channel Viewer Example Application
This application shows how to subscribe to a channel view using the `Phenix Channel Express API`.

This example application demonstrates how to:
1. Enter AuthToken
2. Join and view channel

For more details and additional features, please refer to our `Channel Express API` documentation.

## How to Install
Required:
* node: 12.18.2
* npm: 6.14.5

1. cd ReactNative/ChannelViewer
2. `npm install`

## iOS:
1. cd ReactNative/ChannelViewer/ios
2. `pod install`
3. Open ChannelViewer.xcworkspace with Xcode
4. Navigate to Project Settings - Signing & Capabilities
5. Select your development team
### This project was started on Xcode 13.1, COCOAPODS 1.10.2.

## How to Run iOS
1. cd ReactNative/ChannelViewer:
2. `npm run start`
3. `npm run ios` or `react-native run-ios`

## Android
In Android Manifest, if it doesn't exist, add:
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

In some cases in gradle.properties add:
`android.enableDexingArtifactTransform.desugaring=false`

1. Start Android Studio
2. Select ChannelViewer project
3. Check android/local.properties
4. Check /android/app/debug.keystore, if it doesn't exist add or generate

## How to Run Android
1. cd ReactNative/ChannelViewer
2. `npm run start`
3. `npm run android` or `react-native run-android`

## See Also
### Related Examples
* [Mobile Channel Publisher](https://github.com/PhenixRTS/MobileChannelPublisher)
* [Web Examples](https://github.com/PhenixRTS/WebExamples)

### Documentation
* [Channel Viewer Tutorial](https://phenixrts.com/docs/web/react-native/#web-sdk-react-native-example)
* [Phenix Channel Express API](https://phenixrts.com/docs/web/#channel-express)
* [React Native Support](https://phenixrts.com/docs/web/#react-native-support)
* [Phenix Platform Documentation](https://phenixrts.com/docs/)
