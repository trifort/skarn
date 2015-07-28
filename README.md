# Skarn [![Circle CI](https://circleci.com/gh/trifort/skarn.svg?style=svg)](https://circleci.com/gh/trifort/skarn)

Push notification server build on Akka Actor with Scala

## Quick start with Docker

Pull a docker image.
```sh
docker pull trifort/skarn
```

Create *service.json* and fill your service information.

```json
{
  "services": [
    {
      "name": "service1",
      "auth-token": "ARBITRARY_TOKEN",
      "apns": {
        "cert-path": "/path/to/certificate.p12",
        "password": "password"
      },
      "gcm": {
        "api-key": "ApiKey"
      }
    }
  ]
}
```

Note that you need to put your APNS certificate with p12 format at the specified path.


Run a container.

```sh
docker run -v /path/to/service.json:/conf/service.json -v /path/to/certificate.p12:/path/to/certificate.p12 -e CONFIG_PATH=/conf/service.json -p 80:8080 -it trifort/skarn
```

If you see a log message such as "Bound to /0.0.0.0:8080", it is ready to request.

```
curl --header "X-AUTH-TOKEN: ARBITRARY_TOKEN" \
     --header Content-Type:"application/json" \
     http://YOUR_SKARN_IP \
     -d "{  \"notifications\": [    {      \"deviceTokens\": [\"b59430...0ca3a\"],      \"platform\": 1,      \"body\": \"Hello iOS\"    },    {      \"deviceTokens\": [\"APA91bGw...Aps-jSkBUC\"],      \"platform\": 2,      \"title\": \"Hello Android\"    }  ]"
```


## Build

```sh
sbt
> skarn/stage
```

This command generates executables in *api/target/universal/stage*.

In addition, you can package in any other format with following commands.

+ skarn/universal:packageBin - Generates a universal zip file
+ skarn/universal:packageZipTarball - Generates a universal tgz file
+ skarn/debian:packageBin - Generates a deb
+ skarn/docker:publishLocal - Builds a Docker image using the local Doapi/cker server
+ skarn/rpm:packageBin - Generates an rpm
+ skarn/universal::packageOsxDmg - Generates a DMG file with the saapi/me contents as the unskarnrsal zip/tgz.
+ skarn/windows:packageBin - Generates an MSI

For more information, see [sbt-native-packager document](http://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/my-first-project.html).

## Configuration

You must define APNS and GCM information of your services in JSON (or HOCON) format.

example: service.json
```json
{
  "services": [
    {
      "name": "service1",
      "auth-token": "TOKEN_IN_HEADER",
      "apns": {
        "cert-path": "/path/to/certificate.p12",
        "password": "password"
      },
      "gcm": {
        "api-key": "ApiKey"
      }
    },
    {
      "name": "service2",
      "auth-token": "TOKEN_IN_HEADER",
      "apns": {
        "cert-path": "/path/to/certificate.p12",
        "password": "password"
      },
      "gcm": {
        "api-key": "ApiKey"
      }
    }
  ]
}
```

## Run

```sh
CONFIG_PATH=service.json  ./api/target/universal/stage/bin/api
```


## API

POST /v1/push

example

```json
{
  "notifications": [
    {
      "deviceTokens": ["b59430...0ca3a"],
      "platform": 1,
      "badge": 2,
      "sound": "default",
      "body": "Hello iOS"
    },
    {
      "deviceTokens": ["APA91bGw...Aps-jSkBUC"],
      "platform": 2,
      "title": "Hello Android",
      "data": [{"key": "my-data", "value": "my-value"}],
      "collapseKey": "Updates Available",
      "delayWhileIdle": true
    }
  ]
}
```

HEADER
+ Content-Type - application/json
+ X-AUTH-TOKEN - Your token defined in the config file specified by *CONFIG_PATH*

JSON body

| Parameter    | Usage                   | Description |
|:-------------|:------------------------|:------------|
| notifications| Notification <br> Array | Array of Notification object (see below) |


Notification

| Parameter    | Usage                   | Description |
|:-------------|:------------------------|:------------|
| deviceTokens | String <br> Array       | array of device tokens or registration IDs |
| platform     | Int                     | APNS: 1, GCM: 2 |
| title        | String                  | notification title. Required for Android and optional for iOS. Note that in APNS, only iOS 8.2 and higher and Apple Watch support this field.  |
| body         | Optional <br> String    | notification body |
| badge        | Optional <br> Int       | badge count. APNS only. |
| sound        | Optional <br> String    | sound to be played. APNS only. |
| data         | Optional <br> Object <br> Array| GCM data payload. Format should be array of object contains "key" and "value" fields. This array is reduced into single object. For example, [{"key": "my-data", "value": "my-value"}] turns into {"my-data": "my-value"} |
| collapseKey  | Optional <br> String    | a key for collapsing notifications. GCM only. |
| delayWhileIdle| Optional <br> Boolean  | if notification should be wait or not until the device becomes active. GCM only. |



## Contributors

+ Yusuke Yasuda ([@TanUkkii](https://github.com/TanUkkii007))
+ Hajime Noguchi
+ Shuhei Hayashibara ([@shufo](https://github.com/shufo))

## Licence

Licensed under the MIT License.
