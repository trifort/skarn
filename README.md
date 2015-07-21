# Skarn

Push notification server build on Akka Actor with Scala

## Quick start with Docker
Comming soon.

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
        "cert-path": "/path/to/certificate",
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
        "cert-path": "/path/to/certificate",
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
      "message": "Hello iOS"
    },
    {
      "deviceTokens": ["APA91bGw...Aps-jSkBUC"],
      "platform": 2,
      "message": "Hello Android",
      "extend": [{"key": "my-data", "value": "my-value"}],
      "collapseKey": "outline",
      "delayWhileIdle": true
    }
  ]
}
```

HEADER
+ Content-Type - application/json
+ X-AUTH-TOKEN - Your token defined in the config file specified by *CONFIG_PATH*

JSON

| Parameter    | Usage                   | Description |
|:-------------|:------------------------|:------------|
| notifications| Notification <br> Array |             |


Notification

| Parameter    | Usage                   | Description |
|:-------------|:------------------------|:------------|
| deviceTokens | String <br> Array       |             |
| platform     | Int                     |             |
| message      | String                  |             |
| badge        | Optional <br> Int       |             |
| sound        | Optional <br> String    |             |
| extend       | Optional <br> Object <br> Array|             |
| collapseKey  | Optional <br> String    |             |
| delayWhileIdle| Optional <br> Boolean  |             |



## Contributers

+ Yusuke Yasuda ([@TanUkkii](https://github.com/TanUkkii007))
+ Hajime Noguchi
+ Shuhei Hayashibara ([@shufo](https://github.com/shufo))

## Licence

Licensed under the MIT License.
