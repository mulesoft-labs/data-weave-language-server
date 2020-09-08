# DataWeave LSP

This project contains the first basic implementation of a DataWeave LSP

## Build Instructions

1. Build data-weave-lang-server

```bash
./gradlew data-weave-lang-server:assembleShadowDist

```

2. Build the extension

```bash
./gradlew data-weave-client-vscode:packageExtension

``` 
This is going to generate the vsix on the `data-weave-client-vscode` directory that you can later install it in your vscode.

3. Release a version

In order to release just create a tag with v<next_version> example if we want to release 0.0.4
we just need to create v0.0.4 and push the tag. The CI should take care of the rest