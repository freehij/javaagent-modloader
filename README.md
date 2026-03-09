# Java agent mod loader
[![latest](https://img.shields.io/badge/Download-latest-green)](https://github.com/freehij/javaagent-modloader/releases/latest) [![issues](https://img.shields.io/badge/github-issues-orange?logo=github)](https://github.com/freehij/javaagent-modloader/issues)  
A simple minimal dependency java-agent based mod loader for java programs (mainly for minecraft).
# How to use
Download the loader and add this to your java arguments for the game instance you want to use the loader with:  
`-javaagent:<path to loader jar>`

Alternatively if you use prism launcher you can go to `Edit instance -> Version -> Add agents` and select the downloaded jar.

# Version range (Minecraft: JE)
Supports all unobfuscated versions of the game (25w45a_unobfuscated-1.21.11_unobfuscated and all later releases)  
**Full list of supported versions with download links can be found [here](https://github.com/freehij/resources/blob/main/versions.json).**

# Development
**An example mod is avaliable [here](https://github.com/freehij/example-mod).**

The mod development process is pretty similar to fabric so it should be pretty straight forward.  
However this loader is much more simplier than fabric so it may lack many crucial features, feel free to add them yourself!
