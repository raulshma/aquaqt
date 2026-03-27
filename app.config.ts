import type { ExpoConfig } from "expo/config";

const appVariant =
  process.env.APP_VARIANT === "development" ? "development" : "production";
const isDevelopment = appVariant === "development";

const androidPackage = isDevelopment
  ? "com.keepaside.aquapt.dev"
  : "com.keepaside.aquapt";
const appName = isDevelopment ? "AquaPT Dev" : "aquapt";

const icon = isDevelopment
  ? "./assets/images/android-icon-foreground.png"
  : "./assets/images/logo.png";

const splashImage = isDevelopment
  ? "./assets/images/logo.png"
  : "./assets/images/splash.png";

const config: ExpoConfig = {
  name: appName,
  slug: "aquapt",
  version: "0.1.3",
  orientation: "portrait",
  icon,
  scheme: "aquapt",
  userInterfaceStyle: "automatic",
  ios: {
    supportsTablet: true,
    infoPlist: {
      UIBackgroundModes: ["fetch"],
      NSSpeechRecognitionUsageDescription:
        "AquaPT needs speech recognition to convert your voice to text.",
      NSMicrophoneUsageDescription:
        "AquaPT needs access to your microphone for voice dictation.",
    },
  },
  android: {
    adaptiveIcon: isDevelopment
      ? {
          backgroundColor: "#14202C",
          foregroundImage: "./assets/images/logo.png",
          monochromeImage: "./assets/images/android-icon-monochrome.png",
        }
      : {
          backgroundColor: "#0A111A",
          foregroundImage: "./assets/images/android-icon-foreground.png",
          backgroundImage: "./assets/images/android-icon-background.png",
          monochromeImage: "./assets/images/android-icon-monochrome.png",
        },
    predictiveBackGestureEnabled: false,
    package: androidPackage,
    permissions: ["android.permission.RECORD_AUDIO"],
  },
  web: {
    output: "static",
    favicon: "./assets/images/logo.png",
  },
  plugins: [
    "expo-router",
    [
      "expo-splash-screen",
      {
        image: splashImage,
        imageWidth: isDevelopment ? 180 : 200,
        resizeMode: "contain",
        backgroundColor: isDevelopment ? "#14202C" : "#0A111A",
        dark: {
          backgroundColor: "#000000",
        },
      },
    ],
    [
      "expo-dev-client",
      {
        launchMode: "most-recent",
      },
    ],
    [
      "expo-notifications",
      {
        defaultChannel: "reminders",
      },
    ],
    [
      "expo-speech-recognition",
      {
        microphonePermission:
          "AquaPT needs access to your microphone for voice dictation.",
        speechRecognitionPermission:
          "AquaPT needs speech recognition to convert your voice to text.",
      },
    ],
    "expo-secure-store",
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
  extra: {
    router: {},
    eas: {
      projectId: "be90a9f3-0127-41ef-bd50-58a00fee31e6",
    },
  },
};

export default config;
