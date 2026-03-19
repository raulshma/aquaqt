import type { MD3Theme } from "react-native-paper";

export type CardToneRole =
  | "primaryContainer"
  | "secondaryContainer"
  | "tertiaryContainer"
  | "surface"
  | "surfaceVariant"
  | "errorContainer";

const toneTextMap: Record<CardToneRole, keyof MD3Theme["colors"]> = {
  primaryContainer: "onPrimaryContainer",
  secondaryContainer: "onSecondaryContainer",
  tertiaryContainer: "onTertiaryContainer",
  surface: "onSurface",
  surfaceVariant: "onSurfaceVariant",
  errorContainer: "onErrorContainer",
};

export function getCardTone(theme: MD3Theme, tone: CardToneRole) {
  return {
    backgroundColor: theme.colors[tone] as string,
    textColor: theme.colors[toneTextMap[tone]] as string,
  };
}

export function getCardTextColorForBackground(
  theme: MD3Theme,
  backgroundColor: string,
) {
  const knownTones: CardToneRole[] = [
    "primaryContainer",
    "secondaryContainer",
    "tertiaryContainer",
    "surface",
    "surfaceVariant",
    "errorContainer",
  ];

  const matchedTone = knownTones.find(
    (tone) =>
      theme.colors[tone].toLowerCase() === backgroundColor.toLowerCase(),
  );

  if (!matchedTone) {
    return theme.colors.onSurface as string;
  }

  return theme.colors[toneTextMap[matchedTone]] as string;
}
