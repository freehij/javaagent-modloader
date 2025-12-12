# Explanation
The problem appears when pairing this loader with fabric or forge.

Here is an example of code from [modmenu](https://github.com/freehij/modmenu/blob/main/io/github/freehij/injections/TitleScreenInjection.java) that may be cause an issue:
```java
@EditClass("net/minecraft/client/gui/screens/TitleScreen")
public class TitleScreenInjection {
    @Inject(method = "init")
    public static void init(InjectionHelper helper) {
        TitleScreen ts = (TitleScreen) helper.getSelf();
        Reflector tsR = helper.getReflector();
        tsR.invokeRaw("addRenderableWidget",
                new Class<?>[] { GuiEventListener.class },
                Button.builder(Component.literal("Mods"),
                        button -> Minecraft.getInstance().setScreen(new ModMenuScreen()))
                        .bounds(ts.width / 2 - 32, 2, 64, 20)
                        .build());
    }
}
```
And here is the full stack trace:
```
[20:34:37] [Render thread/ERROR]: Unreported exception thrown!
java.lang.ClassCastException: class net.minecraft.client.gui.screens.TitleScreen cannot be cast to class net.minecraft.client.gui.screens.TitleScreen (net.minecraft.client.gui.screens.TitleScreen is in unnamed module of loader 'knot' @35cabb2a; net.minecraft.client.gui.screens.TitleScreen is in unnamed module of loader 'app')
	at io.github.freehij.injections.TitleScreenInjection.init(TitleScreenInjection.java:18)
	at knot//net.minecraft.client.gui.screens.TitleScreen.init(TitleScreen.java)
	at knot//net.minecraft.client.gui.screens.Screen.init(Screen.java:326)
	at knot//net.minecraft.client.Minecraft.setScreen(Minecraft.java:1237)
	at knot//net.minecraft.client.Minecraft.lambda$buildInitialScreens$8(Minecraft.java:794)
	at knot//net.minecraft.client.Minecraft.onGameLoadFinished(Minecraft.java:776)
	at knot//net.minecraft.client.Minecraft.onResourceLoadFinished(Minecraft.java:765)
	at knot//net.minecraft.client.Minecraft.lambda$new$5(Minecraft.java:730)
	at knot//net.minecraft.util.Util.ifElse(Util.java:674)
	at knot//net.minecraft.client.Minecraft.lambda$new$6(Minecraft.java:725)
	at knot//net.minecraft.client.gui.screens.LoadingOverlay.tick(LoadingOverlay.java:141)
	at knot//net.minecraft.client.Minecraft.tick(Minecraft.java:1916)
	at knot//net.minecraft.client.Minecraft.runTick(Minecraft.java:1354)
	at knot//net.minecraft.client.Minecraft.run(Minecraft.java:966)
	at knot//net.minecraft.client.main.Main.main(Main.java:248)
	at net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider.launch(MinecraftGameProvider.java:514)
	at net.fabricmc.loader.impl.launch.knot.Knot.launch(Knot.java:72)
	at net.fabricmc.loader.impl.launch.knot.KnotClient.main(KnotClient.java:23)
	at org.prismlauncher.launcher.impl.StandardLauncher.launch(StandardLauncher.java:105)
	at org.prismlauncher.EntryPoint.listen(EntryPoint.java:129)
	at org.prismlauncher.EntryPoint.main(EntryPoint.java:70)
```
As far as I can tell it happens because we are loading TitleScreen 2 times: 1st one is when we initialize the handler, 2nd is when knot is loading all game classes.

And so this can easily be confirmed by enabling logs:
<img width="811" height="182" alt="image" src="https://github.com/user-attachments/assets/9476b67d-0426-441c-bb8f-5f68be868de5" />
<img width="811" height="182" alt="image" src="https://github.com/user-attachments/assets/b2ad9288-a074-4e63-b80f-bbea3ff7c814" />

At first glance I thought that it happens due to te reference in handler it self, but when I asked chatgpt to make me a injection processor without class loading I can almost 100% surely tell that it's not the case and that I'm fully stuck here as I have to clue why does this code loads TitleScreen multiple times even when I remove anyting that leads to class loading.

Notice that removing direct reference from helper itself didn't help either, now I just have a different problem, it can't find addRenderableWidget due to the fact that super class of TitleScreen is loaded by knot, not system class loader, because well, for some reason it still loads TitleScreen.

That's it.