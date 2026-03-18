import { Platform } from "react-native";

export interface DictationSession {
  stop: () => Promise<{ transcript: string; error?: string }>;
  cancel: () => void;
}

/* ── Web Speech API fallback types ─────────────────────────────── */

interface BrowserSpeechRecognitionEvent {
  results: ArrayLike<{
    isFinal: boolean;
    0: {
      transcript: string;
    };
  }>;
}

type BrowserRecognitionInstance = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((event: BrowserSpeechRecognitionEvent) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};

type BrowserSpeechRecognitionConstructor = new () => BrowserRecognitionInstance;

const getWebSpeechConstructor =
  (): BrowserSpeechRecognitionConstructor | null => {
    if (Platform.OS !== "web") {
      return null;
    }

    const target = globalThis as {
      SpeechRecognition?: BrowserSpeechRecognitionConstructor;
      webkitSpeechRecognition?: BrowserSpeechRecognitionConstructor;
    };

    return target.SpeechRecognition ?? target.webkitSpeechRecognition ?? null;
  };

/* ── Public API ────────────────────────────────────────────────── */

/**
 * Returns true if dictation is supported on the current platform.
 * On native (iOS/Android) expo-speech-recognition is always available.
 * On web, checks for the Web Speech API.
 */
export function isDictationSupported(): boolean {
  if (Platform.OS === "web") {
    return getWebSpeechConstructor() !== null;
  }
  // Native: expo-speech-recognition is a native module, always available
  return true;
}

/**
 * Start a press-and-hold dictation session.
 * - On native: uses expo-speech-recognition with event listeners
 * - On web: uses the Web Speech API
 *
 * Call `session.stop()` when the user releases the button to get the final transcript.
 * Call `session.cancel()` to abort without returning a result.
 */
export async function startPressHoldDictation(
  onPartialTranscript?: (value: string) => void,
): Promise<DictationSession> {
  if (Platform.OS === "web") {
    return startWebDictation(onPartialTranscript);
  }
  return startNativeDictation(onPartialTranscript);
}

/* ── Native implementation (expo-speech-recognition) ───────────── */

async function startNativeDictation(
  onPartialTranscript?: (value: string) => void,
): Promise<DictationSession> {
  // Dynamic import to avoid bundling native module on web
  const { ExpoSpeechRecognitionModule } = await import(
    "expo-speech-recognition"
  );

  // Request permissions first
  const permissionResult =
    await ExpoSpeechRecognitionModule.requestPermissionsAsync();
  if (!permissionResult.granted) {
    return {
      stop: async () => ({
        transcript: "",
        error:
          "Microphone/speech recognition permission not granted. Please enable it in settings.",
      }),
      cancel: () => {
        // no-op
      },
    };
  }

  let finalTranscript = "";
  let latestTranscript = "";
  let hasEnded = false;
  let endResolve: (() => void) | null = null;
  let recognitionError: string | undefined;

  // Set up event listeners
  const resultListener = ExpoSpeechRecognitionModule.addListener(
    "result",
    (event: {
      results: { transcript: string }[];
      isFinal: boolean;
    }) => {
      const text = event.results[0]?.transcript ?? "";
      if (event.isFinal) {
        finalTranscript = text;
        latestTranscript = text;
      } else {
        latestTranscript = text;
      }
      onPartialTranscript?.(latestTranscript);
    },
  );

  const errorListener = ExpoSpeechRecognitionModule.addListener(
    "error",
    (event: { error: string; message: string }) => {
      // "no-speech" is not a real error — it just means the user didn't say anything
      if (event.error !== "no-speech") {
        recognitionError = event.message || event.error;
      }
    },
  );

  const endListener = ExpoSpeechRecognitionModule.addListener("end", () => {
    hasEnded = true;
    endResolve?.();
  });

  // Start recognition
  ExpoSpeechRecognitionModule.start({
    lang: "en-US",
    interimResults: true,
    continuous: true,
    addsPunctuation: true,
    iosTaskHint: "dictation" as const,
  });

  const cleanup = () => {
    resultListener.remove();
    errorListener.remove();
    endListener.remove();
  };

  return {
    stop: async () => {
      ExpoSpeechRecognitionModule.stop();

      // Wait for the "end" event if it hasn't fired yet
      if (!hasEnded) {
        await new Promise<void>((resolve) => {
          endResolve = resolve;
          // Safety timeout to prevent hanging
          setTimeout(() => resolve(), 3000);
        });
      }

      cleanup();

      const transcript = (finalTranscript || latestTranscript).trim();
      return {
        transcript,
        error: recognitionError,
      };
    },
    cancel: () => {
      ExpoSpeechRecognitionModule.abort();
      cleanup();
    },
  };
}

/* ── Web implementation (Web Speech API) ───────────────────────── */

async function startWebDictation(
  onPartialTranscript?: (value: string) => void,
): Promise<DictationSession> {
  const SpeechCtor = getWebSpeechConstructor();

  if (!SpeechCtor) {
    return {
      stop: async () => ({
        transcript: "",
        error:
          "Voice dictation is not available on this browser. You can still type your message.",
      }),
      cancel: () => {
        // no-op
      },
    };
  }

  const recognition = new SpeechCtor();
  recognition.continuous = true;
  recognition.interimResults = true;
  recognition.lang = "en-US";

  let finalTranscript = "";
  let interimTranscript = "";

  recognition.onresult = (event) => {
    interimTranscript = "";

    for (let i = 0; i < event.results.length; i += 1) {
      const result = event.results[i];
      const text = result?.[0]?.transcript ?? "";

      if (result?.isFinal) {
        finalTranscript += `${text} `;
      } else {
        interimTranscript += `${text} `;
      }
    }

    onPartialTranscript?.(`${finalTranscript}${interimTranscript}`.trim());
  };

  recognition.onerror = () => {
    onPartialTranscript?.("");
  };

  recognition.start();

  return {
    stop: async () => {
      await new Promise<void>((resolve) => {
        recognition.onend = () => {
          resolve();
        };

        recognition.stop();
      });

      const transcript = `${finalTranscript}${interimTranscript}`.trim();
      return {
        transcript,
      };
    },
    cancel: () => {
      recognition.abort();
    },
  };
}
