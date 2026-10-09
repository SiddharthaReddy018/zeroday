package liveness.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * State → message mapping for the liveness UI (§9).
 * <p>
 * EN/FR/AR minimum; AR requires RTL. Keys match the
 * LivenessState enum names. Challenge actions have separate keys.
 * <p>
 * Status is never indicated by colour alone.
 * No model internals are shown to users.
 */
public final class UiMessages {

    private UiMessages() {}

    // --- English (default) ---
    private static final Map<String, String> EN;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        // State messages (§9)
        m.put("IDLE", "Ready to begin face verification");
        m.put("DEVICE_CHECK", "Connecting to camera...");
        m.put("POSITIONING", "Position your face in the oval");
        m.put("PASSIVE", "Checking face liveness...");
        m.put("CHALLENGE_PROMPT", "Please follow the instruction");
        m.put("CHALLENGE_WAIT", "Please continue");
        m.put("FINAL_CAPTURE", "Capturing final image...");
        m.put("VERIFYING_FINAL", "Verifying...");
        m.put("SUCCESS", "Good, face captured successfully");
        m.put("RETRY_AVAILABLE", "We could not verify face liveness. Please try again.");
        m.put("BLOCKED_ATTACK", "Face verification could not be completed. Please try again.");
        m.put("FAILED_MAX_RETRIES", "Verification could not be completed.");
        m.put("DEVICE_ERROR", "Camera not available. Please check the connection and try again.");

        // Challenge actions
        m.put("CHALLENGE.BLINK", "Please blink");
        m.put("CHALLENGE.SMILE", "Please smile");
        m.put("CHALLENGE.TURN_LEFT", "Please turn your head to the left");
        m.put("CHALLENGE.TURN_RIGHT", "Please turn your head to the right");
        m.put("CHALLENGE.LOOK_LEFT", "Please look left");
        m.put("CHALLENGE.LOOK_RIGHT", "Please look right");
        m.put("CHALLENGE.LOOK_UP", "Please look up");
        m.put("CHALLENGE.LOOK_DOWN", "Please look down");

        // Guidance messages (S4)
        m.put("GUIDE.FACE_TOO_CLOSE", "Move your face further from the camera");
        m.put("GUIDE.FACE_TOO_FAR", "Move your face closer to the camera");
        m.put("GUIDE.FACE_NOT_CENTERED", "Center your face in the oval");
        m.put("GUIDE.POSE_YAW", "Please face the camera directly");
        m.put("GUIDE.POSE_PITCH", "Please look straight at the camera");
        m.put("GUIDE.BLUR", "Please hold still");
        m.put("GUIDE.BRIGHTNESS_LOW", "Move to a brighter area");
        m.put("GUIDE.BRIGHTNESS_HIGH", "Move away from direct light");
        m.put("GUIDE.NO_FACE", "No face detected — please look at the camera");
        m.put("GUIDE.MULTIPLE_FACES", "Only one person should be visible");
        m.put("GUIDE.OPERATOR_ADJUST", "Operator: please adjust the camera position");

        // Retry
        m.put("RETRY.REMAINING", "attempts remaining");

        // Recovery (FAILED_MAX_RETRIES)
        m.put("RECOVERY.CONTACT_SUPERVISOR", "Please contact the supervisor for assistance.");
        m.put("RECOVERY.TRY_LATER", "Please try again later.");

        EN = Collections.unmodifiableMap(m);
    }

    // --- French ---
    private static final Map<String, String> FR;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("IDLE", "Prêt à commencer la vérification du visage");
        m.put("DEVICE_CHECK", "Connexion à la caméra...");
        m.put("POSITIONING", "Placez votre visage dans l'ovale");
        m.put("PASSIVE", "Vérification de la vivacité du visage...");
        m.put("CHALLENGE_PROMPT", "Veuillez suivre l'instruction");
        m.put("CHALLENGE_WAIT", "Veuillez continuer");
        m.put("FINAL_CAPTURE", "Capture de l'image finale...");
        m.put("VERIFYING_FINAL", "Vérification...");
        m.put("SUCCESS", "Bien, le visage a été capturé avec succès");
        m.put("RETRY_AVAILABLE", "Nous n'avons pas pu vérifier la vivacité du visage. Veuillez réessayer.");
        m.put("BLOCKED_ATTACK", "La vérification du visage n'a pas pu être effectuée. Veuillez réessayer.");
        m.put("FAILED_MAX_RETRIES", "La vérification n'a pas pu être effectuée.");
        m.put("DEVICE_ERROR", "Caméra non disponible. Veuillez vérifier la connexion et réessayer.");

        m.put("CHALLENGE.BLINK", "Veuillez cligner des yeux");
        m.put("CHALLENGE.SMILE", "Veuillez sourire");
        m.put("CHALLENGE.TURN_LEFT", "Veuillez tourner la tête vers la gauche");
        m.put("CHALLENGE.TURN_RIGHT", "Veuillez tourner la tête vers la droite");
        m.put("CHALLENGE.LOOK_LEFT", "Veuillez regarder à gauche");
        m.put("CHALLENGE.LOOK_RIGHT", "Veuillez regarder à droite");
        m.put("CHALLENGE.LOOK_UP", "Veuillez regarder en haut");
        m.put("CHALLENGE.LOOK_DOWN", "Veuillez regarder en bas");

        m.put("GUIDE.FACE_TOO_CLOSE", "Éloignez votre visage de la caméra");
        m.put("GUIDE.FACE_TOO_FAR", "Rapprochez votre visage de la caméra");
        m.put("GUIDE.FACE_NOT_CENTERED", "Centrez votre visage dans l'ovale");
        m.put("GUIDE.POSE_YAW", "Veuillez faire face à la caméra directement");
        m.put("GUIDE.POSE_PITCH", "Veuillez regarder droit dans la caméra");
        m.put("GUIDE.BLUR", "Veuillez rester immobile");
        m.put("GUIDE.BRIGHTNESS_LOW", "Déplacez-vous vers un endroit plus lumineux");
        m.put("GUIDE.BRIGHTNESS_HIGH", "Éloignez-vous de la lumière directe");
        m.put("GUIDE.NO_FACE", "Aucun visage détecté — veuillez regarder la caméra");
        m.put("GUIDE.MULTIPLE_FACES", "Une seule personne doit être visible");
        m.put("GUIDE.OPERATOR_ADJUST", "Opérateur: veuillez ajuster la position de la caméra");

        m.put("RETRY.REMAINING", "tentatives restantes");
        m.put("RECOVERY.CONTACT_SUPERVISOR", "Veuillez contacter le superviseur pour obtenir de l'aide.");
        m.put("RECOVERY.TRY_LATER", "Veuillez réessayer plus tard.");

        FR = Collections.unmodifiableMap(m);
    }

    // --- Arabic (RTL) ---
    private static final Map<String, String> AR;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("IDLE", "\u062C\u0627\u0647\u0632 \u0644\u0628\u062F\u0621 \u0627\u0644\u062A\u062D\u0642\u0642 \u0645\u0646 \u0627\u0644\u0648\u062C\u0647");
        m.put("DEVICE_CHECK", "\u062C\u0627\u0631\u064A \u0627\u0644\u0627\u062A\u0635\u0627\u0644 \u0628\u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627...");
        m.put("POSITIONING", "\u0636\u0639 \u0648\u062C\u0647\u0643 \u0641\u064A \u0627\u0644\u0634\u0643\u0644 \u0627\u0644\u0628\u064A\u0636\u0627\u0648\u064A");
        m.put("PASSIVE", "\u062C\u0627\u0631\u064A \u0627\u0644\u062A\u062D\u0642\u0642 \u0645\u0646 \u062D\u064A\u0648\u064A\u0629 \u0627\u0644\u0648\u062C\u0647...");
        m.put("SUCCESS", "\u062C\u064A\u062F\u060C \u062A\u0645 \u0627\u0644\u062A\u0642\u0627\u0637 \u0627\u0644\u0648\u062C\u0647 \u0628\u0646\u062C\u0627\u062D");
        m.put("RETRY_AVAILABLE", "\u0644\u0645 \u0646\u062A\u0645\u0643\u0646 \u0645\u0646 \u0627\u0644\u062A\u062D\u0642\u0642 \u0645\u0646 \u062D\u064A\u0648\u064A\u0629 \u0627\u0644\u0648\u062C\u0647. \u064A\u0631\u062C\u0649 \u0627\u0644\u0645\u062D\u0627\u0648\u0644\u0629 \u0645\u0631\u0629 \u0623\u062E\u0631\u0649.");
        m.put("BLOCKED_ATTACK", "\u062A\u0639\u0630\u0631 \u0625\u0643\u0645\u0627\u0644 \u0627\u0644\u062A\u062D\u0642\u0642 \u0645\u0646 \u0627\u0644\u0648\u062C\u0647. \u064A\u0631\u062C\u0649 \u0627\u0644\u0645\u062D\u0627\u0648\u0644\u0629 \u0645\u0631\u0629 \u0623\u062E\u0631\u0649.");
        m.put("FAILED_MAX_RETRIES", "\u062A\u0639\u0630\u0631 \u0625\u0643\u0645\u0627\u0644 \u0627\u0644\u062A\u062D\u0642\u0642.");
        m.put("DEVICE_ERROR", "\u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627 \u063A\u064A\u0631 \u0645\u062A\u0627\u062D\u0629. \u064A\u0631\u062C\u0649 \u0627\u0644\u062A\u062D\u0642\u0642 \u0645\u0646 \u0627\u0644\u0627\u062A\u0635\u0627\u0644 \u0648\u0627\u0644\u0645\u062D\u0627\u0648\u0644\u0629 \u0645\u0631\u0629 \u0623\u062E\u0631\u0649.");

        m.put("CHALLENGE.BLINK", "\u064A\u0631\u062C\u0649 \u0627\u0644\u0631\u0645\u0634");
        m.put("CHALLENGE.SMILE", "\u064A\u0631\u062C\u0649 \u0627\u0644\u0627\u0628\u062A\u0633\u0627\u0645");
        m.put("CHALLENGE.TURN_LEFT", "\u064A\u0631\u062C\u0649 \u0625\u062F\u0627\u0631\u0629 \u0631\u0623\u0633\u0643 \u0625\u0644\u0649 \u0627\u0644\u064A\u0633\u0627\u0631");
        m.put("CHALLENGE.TURN_RIGHT", "\u064A\u0631\u062C\u0649 \u0625\u062F\u0627\u0631\u0629 \u0631\u0623\u0633\u0643 \u0625\u0644\u0649 \u0627\u0644\u064A\u0645\u064A\u0646");

        m.put("GUIDE.FACE_TOO_CLOSE", "\u0627\u0628\u0639\u062F \u0648\u062C\u0647\u0643 \u0639\u0646 \u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627");
        m.put("GUIDE.FACE_TOO_FAR", "\u0642\u0631\u0628 \u0648\u062C\u0647\u0643 \u0645\u0646 \u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627");
        m.put("GUIDE.NO_FACE", "\u0644\u0645 \u064A\u062A\u0645 \u0627\u0644\u0643\u0634\u0641 \u0639\u0646 \u0648\u062C\u0647 \u2014 \u064A\u0631\u062C\u0649 \u0627\u0644\u0646\u0638\u0631 \u0625\u0644\u0649 \u0627\u0644\u0643\u0627\u0645\u064A\u0631\u0627");

        m.put("RETRY.REMAINING", "\u0645\u062D\u0627\u0648\u0644\u0627\u062A \u0645\u062A\u0628\u0642\u064A\u0629");
        m.put("RECOVERY.CONTACT_SUPERVISOR", "\u064A\u0631\u062C\u0649 \u0627\u0644\u0627\u062A\u0635\u0627\u0644 \u0628\u0627\u0644\u0645\u0634\u0631\u0641 \u0644\u0644\u062D\u0635\u0648\u0644 \u0639\u0644\u0649 \u0627\u0644\u0645\u0633\u0627\u0639\u062F\u0629.");

        AR = Collections.unmodifiableMap(m);
    }

    // --- Supported locales ---
    private static final Map<String, Map<String, String>> LOCALES;

    static {
        Map<String, Map<String, String>> lm = new LinkedHashMap<>();
        lm.put("en", EN);
        lm.put("fr", FR);
        lm.put("ar", AR);
        LOCALES = Collections.unmodifiableMap(lm);
    }

    /**
     * Gets the localised message for a key.
     *
     * @param locale the locale code ("en", "fr", "ar")
     * @param key    the message key (state name or dotted key)
     * @return the localised message, or the key itself if not found
     */
    public static String get(String locale, String key) {
        Map<String, String> messages = LOCALES.getOrDefault(locale, EN);
        return messages.getOrDefault(key, EN.getOrDefault(key, key));
    }

    /**
     * Gets the English message for a key.
     */
    public static String get(String key) {
        return get("en", key);
    }

    /**
     * Gets the challenge prompt message for an action.
     */
    public static String challengeMessage(String locale, String action) {
        return get(locale, "CHALLENGE." + action);
    }

    /**
     * Gets the retry message with remaining attempts.
     */
    public static String retryMessage(String locale, int remaining) {
        return get(locale, "RETRY_AVAILABLE") + " " + remaining + " " + get(locale, "RETRY.REMAINING");
    }

    /**
     * Returns true if the locale uses RTL layout.
     */
    public static boolean isRtl(String locale) {
        return "ar".equals(locale);
    }

    /**
     * Returns all supported locale codes.
     */
    public static Iterable<String> supportedLocales() {
        return LOCALES.keySet();
    }
}
