# Phenix Deep Link parser

Support library providing Deep Link querying and configuration storing.

## Usage

1. Import `phenixdeeplink` module

- Make sure that in your Module:app gradle file you have this line:
```
dependencies {
    implementation project(":phenixdeeplink")
    ...
}
```
- Make sure that in your settings.gradle file you have this line at the top:
```
include ':phenixdeeplink'
```

2. Extend `DeepLinkActivity` from your launcher (Splash) activity:
```
class SplashActivity : DeepLinkActivity() {
    ...
}
```

3. Override `DeepLinkActivity` abstract functions and variables:

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

4. Use the `configuration` HashMap from `DeepLinkActivity` to initialize Channel / Room Express.

5. Use the `QUERY_` constants to set / get values from `configuration` map.
