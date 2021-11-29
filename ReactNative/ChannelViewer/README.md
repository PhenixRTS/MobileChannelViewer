# ReactNativeViewer
Simple ReactNative Phenix channel viewer, based on Phenix WebSDK

# Channel Viewer Example Application
This application shows how to subscribe to a channel using the `Phenix Channel Express API`.

This example application demonstrates how to:
1. Select the capabilities to subscribe with
2. Subscribe with an edge token and channel alias
3. Wait for the stream to start
4. Handle potential subscription errors

For more details and additional features, please refer to our `Channel Express API` documentation.

## How to Install
1. cd ReactNative/ChannelViewer
2. `npm install`
### This project was started on node v.12.18.2, npm 6.14.5

## iOS:
1. cd ReactNative/ChannelViewer/ios
2. `pod install`
3. Open Xcode project (definitely provide a path to the project main file which needs to be launched)
4. Navigate to Project Settings - Signing & Capabilities
5. Select your development team
### This project was started on Xcode 12.0, COCOAPODS 1.10.2.

## How to Run iOS
1. cd ReactNative/ChannelViewer:
2. `npm run start`
3. `npm run ios` or `react-native run-ios`

## Android
In Android Manifest, if it doesn't exist, add:
`<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`
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
* [Mobile Examples](https://github.com/PhenixRTS/MobileExamples)
* [Web Examples](https://github.com/PhenixRTS/WebExamples)

### Documentation
* [Channel Viewer Tutorial](https://phenixrts.com/docs/web/react-native/#web-sdk-react-native-example)
* [Phenix Channel Express API](https://phenixrts.com/docs/web/#channel-express)
* [React Native Support](https://phenixrts.com/docs/web/#react-native-support)
* [Phenix Platform Documentation](http://phenixrts.com/docs/)
