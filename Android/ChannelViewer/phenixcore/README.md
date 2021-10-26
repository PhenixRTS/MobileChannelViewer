# Phenix Core library

## Usage

1. Import `phenixcore` module

- Make sure that in your Module:app gradle file you have this line:
```
dependencies {
    implementation project(":phenixcore")
    ...
}
```
- Make sure that in your settings.gradle file you have this line at the top:
```
include ':phenixcore'
```

## Deep link support

1. Extend `DeepLinkActivity` from your launcher (Splash) activity:
```
class SplashActivity : DeepLinkActivity() {
    ...
}
```

2. Override `DeepLinkActivity` abstract functions and variables:

- Add any additional String pairs to the configuration to query in the deep link uri:
```
override val additionalConfiguration: HashMap<String, String>
        get() = hashMapOf()
```

- Return `true` if Channel / Room Express is already initialized once. This will store the configuration in preferences 
  to be applied on next application launch. Channel / Room Express can be initialized only once per process!
```
override fun isAlreadyInitialized(): Boolean = channelExpress.isRoomExpressInitialized()
```

- Handle the received Deep Link statuses: `READY, RELOAD`. `RELOAD` is called when intent has deep link data and
  `isAlreadyInitialized` is set to true:
```
 override fun onDeepLinkQueried(status: DeepLinkStatus) {
    ...
}
 ```

3. Use the `configuration` HashMap from `DeepLinkActivity` to initialize Channel / Room Express.

4. Use the `QUERY_` constants to set / get values from `configuration` map.
