package top.skyeyefast.mchjong.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Selects a quantity form while retaining native, lazily translated component arguments. */
public final class CountedText {
    private CountedText() {}

    public static MutableComponent of(String key, int countArgument, Object... arguments) {
        Number count = (Number) arguments[countArgument];
        return Component.translatable(key + (Math.abs(count.doubleValue()) == 1 ? ".one" : ".other"), arguments);
    }
}
