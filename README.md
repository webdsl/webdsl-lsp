# WebDSL LSP Server

Quick developer instructions.

## Dependencies:
- Kotlin - yes
- Java - version 21 (not higher, not lower)
- gradle - will be downloaded by the wrapper
- everything else - fetched by the gradle script

## Developing WebDSL LSP server
### Running a development version
Use `./gradlew run` to build and start the LSP server.
By default, it will start on a random free port.
If you want it to start on a specific port instead, e.g. 1337, pass the port number as a commandline argument: `./gradlew run --args 1337`.

### Building a release JAR
`./gradlew shadowJar`, not much to add here.

### Running the JAR standalone
This will most likely be managed by the VS Code plugin.
However, if you ever need to run WebDSL LSP server without `./gradlew run` and not through the VS Code plugin, keep in mind that webdslc migh require a larger stack size - consider adding `-Xss8m` as a JVM argument.

### Managing webdslc
The WebDSL LSP server requires the WebDSL compiler JAR.
The build script will automatically fetch the newest version from WebDSL's release page (see the `downloadAndExtractWebdsl` task).
If you need to rebuild WebDSL LSP server with a new version of the compiler, run `./gradlew cleanWebdsl` before running a build task.

### Spotless
Keep the code clean.
Make sure `./gradlew spotlessCheck` doesn't fail before pushing to remote.
Most spotless issues can be automatically fixed with `./gradlew spotlessApply`.
