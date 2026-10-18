package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.focus.FocusId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;

/**
 * Thaumcraft 4R rebuilt the focus action API between 20740 and 20744: {@code FocusActivation} and
 * {@code FocusCastAnimation} are gone, a spec now carries an input, a set of target kinds and a cast mode, and the
 * context reports a {@code FocusInteraction} instead of an activation. This bridge keeps one Isorropia build working
 * against both generations, so servers and packs can stay on whichever 4R build they already run.
 *
 * <p>Everything that moved is reached reflectively: a class compiled against one generation would otherwise fail to
 * link on the other. The shared surface (the handler signature, {@code FocusActionContext} accessors, the focus id and
 * research types) is untouched by the refactor and is used directly.</p>
 */
public final class FocusCompat {
    /** The moment of a cast, in the vocabulary both generations share. */
    public enum Phase {
        /** The button went down. */
        PRESS,
        /** One tick of held use. */
        TICK,
        /** Held use ended. */
        STOP
    }

    /** What the press was aimed at. The names match the newer {@code FocusTargetKind}. */
    public enum Aim {
        AIR, BLOCK, ENTITY
    }

    /** How long the action runs. */
    public enum Mode {
        INSTANT, CHANNEL
    }

    private static final String INTERACTION = "dev.tc4port.thaumcraft.api.focus.action.FocusInteraction";
    private static final String ACTIVATION = "dev.tc4port.thaumcraft.api.focus.action.FocusActivation";
    private static final String CAST_ANIMATION = "dev.tc4port.thaumcraft.api.focus.action.FocusCastAnimation";
    private static final String INPUT = "dev.tc4port.thaumcraft.api.focus.action.FocusInput";
    private static final String TARGET_KIND = "dev.tc4port.thaumcraft.api.focus.action.FocusTargetKind";
    private static final String CAST_MODE = "dev.tc4port.thaumcraft.api.focus.action.FocusCastMode";

    /** 20744 replaced the activation with an interaction; its presence is the generation marker. */
    private static final boolean MODERN = present(INTERACTION);

    private static final Method INTERACTION_OF_CONTEXT = method(FocusActionContext.class, "interaction");
    private static final Method ACTIVATION_OF_CONTEXT = method(FocusActionContext.class, "activation");
    private static final Method GESTURE_OF_INTERACTION = method(classOrNull(INTERACTION), "gesture");
    private static final Method CHANNEL = method(classOrNull(CAST_MODE), "channel", int.class);

    private FocusCompat() {
    }

    /** Whether the running 4R speaks the 20744 focus action vocabulary. */
    public static boolean modern() {
        return MODERN;
    }

    /** Which moment of the cast the wand is reporting. */
    public static Phase phase(FocusActionContext context) {
        if (MODERN) {
            Object gesture = invoke(GESTURE_OF_INTERACTION, invoke(INTERACTION_OF_CONTEXT, context));
            return switch (((Enum<?>) gesture).name()) {
                case "PRESS" -> Phase.PRESS;
                case "HOLD" -> Phase.TICK;
                default -> Phase.STOP;
            };
        }
        return switch (((Enum<?>) invoke(ACTIVATION_OF_CONTEXT, context)).name()) {
            case "USING_TICK" -> Phase.TICK;
            case "STOP_USING" -> Phase.STOP;
            // USE_AIR, USE_BLOCK and USE_ENTITY are all the press.
            default -> Phase.PRESS;
        };
    }

    /**
     * Builds an action spec in whichever shape the running 4R understands.
     *
     * @param release whether the action wants the end of held use; the newer channel mode always reports it, the older
     *                one only delivers an activation that was declared
     */
    public static FocusActionSpec spec(ResourceLocation actionId, FocusId focus, Aim aim, Mode mode, boolean release,
            ResearchRequirement research, int range, int priority) {
        FocusActionTarget target = new FocusActionTarget.Focus(focus);
        try {
            Class<?> specClass = Class.forName(FocusActionSpec.class.getName());
            if (MODERN) {
                Class<?> inputClass = Class.forName(INPUT);
                Class<?> kindClass = Class.forName(TARGET_KIND);
                Class<?> modeClass = Class.forName(CAST_MODE);
                Object input = constant(inputClass, "SECONDARY");
                Object kind = constant(kindClass, aim.name());
                Object castMode = mode == Mode.INSTANT
                        ? modeClass.getField("INSTANT").get(null)
                        : CHANNEL.invoke(null, 1);
                Constructor<?> constructor = specClass.getConstructor(ResourceLocation.class, FocusActionTarget.class,
                        inputClass, Set.class, modeClass, ResearchRequirement.class, int.class, int.class,
                        ActionPresentationAudience.class);
                return (FocusActionSpec) constructor.newInstance(actionId, target, input, Set.of(kind), castMode,
                        research, range, priority, ActionPresentationAudience.PREDICTED);
            }
            Class<?> activationClass = Class.forName(ACTIVATION);
            EnumSet<?> activations = EnumSet.noneOf(activationClass.asSubclass(Enum.class));
            addActivation(activations, activationClass, "USE_" + aim.name());
            if (mode == Mode.CHANNEL) {
                addActivation(activations, activationClass, "USING_TICK");
            }
            if (release) {
                addActivation(activations, activationClass, "STOP_USING");
            }
            Object animation = constant(Class.forName(CAST_ANIMATION),
                    mode == Mode.INSTANT ? "INSTANT" : "CONTINUOUS");
            Constructor<?> constructor = specClass.getConstructor(ResourceLocation.class, FocusActionTarget.class,
                    Set.class, ResearchRequirement.class, int.class, int.class, Class.forName(CAST_ANIMATION),
                    ActionPresentationAudience.class);
            return (FocusActionSpec) constructor.newInstance(actionId, target, activations, research, range, priority,
                    animation, ActionPresentationAudience.PREDICTED);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Thaumcraft 4R focus action API is neither the 20740 nor the 20744 shape",
                    exception);
        }
    }

    private static void addActivation(EnumSet<?> activations, Class<?> activationClass, String name)
            throws ReflectiveOperationException {
        @SuppressWarnings({"unchecked", "rawtypes"})
        EnumSet raw = activations;
        //noinspection unchecked
        raw.add(constant(activationClass, name));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, FocusCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className, false, FocusCompat.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Object invoke(Method method, Object owner) {
        try {
            return method.invoke(owner);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Thaumcraft 4R focus action API call failed", exception);
        }
    }
}
