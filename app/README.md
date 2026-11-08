# app

Mobile App code

## Running test
There are two type of tests: unit tests and android tests.
Unit tests can be run natively on PC, while android test requires emulator or connected mobile device.
To run unit test:
```sh
.\gradlew.bat test
```
To run android test on PC:
```sh
adb devices # to check emulator name, requres set up emulator
$env:ANDROID_SERIAL = "emulator-5554" # emulator id from previous command
.\gradlew.bat connectedDebugAndroidTest
```
