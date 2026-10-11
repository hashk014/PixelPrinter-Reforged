import fr.hash014.pixelprinter.HttpSource;
import fr.hash014.pixelprinter.Images;
import fr.hash014.pixelprinter.Skins;
import java.io.File;
import java.util.Collections;

/** Optional integration probe against Mojang's public profile/texture endpoints. */
public final class SkinProbe {
  public static void main(String[] args) throws Exception {
    Skins.Skin skin = Skins.fetch(args.length == 0 ? "Notch" : args[0],
        new HttpSource(new File("."), 16777216, 10000, Collections.emptyList()),
        new Images(16777216, 33554432, 128));
    if (skin.image.getWidth() != 64 || (skin.image.getHeight() != 32 && skin.image.getHeight() != 64))
      throw new AssertionError("Unexpected skin dimensions");
    System.out.println("MOJANG SKIN PASS " + skin.image.getWidth() + "x" + skin.image.getHeight()
        + " slim=" + skin.slim + " cape=" + (skin.cape != null));
  }
}
