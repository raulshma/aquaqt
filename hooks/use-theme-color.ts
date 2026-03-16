/**
 * Learn more about light and dark modes:
 * https://docs.expo.dev/guides/color-schemes/
 */

import { Colors } from "@/constants/theme";
import { useColorScheme } from "@/hooks/use-color-scheme";
import { useTheme } from "react-native-paper";

const paperColorMap = {
  text: "onBackground",
  background: "background",
  tint: "primary",
  icon: "onSurfaceVariant",
  tabIconDefault: "onSurfaceVariant",
  tabIconSelected: "primary",
} as const;

export function useThemeColor(
  props: { light?: string; dark?: string },
  colorName: keyof typeof Colors.light & keyof typeof Colors.dark,
) {
  const theme = useColorScheme() ?? "light";
  const paperTheme = useTheme();
  const colorFromProps = props[theme];
  const paperColorKey = paperColorMap[colorName];

  if (colorFromProps) {
    return colorFromProps;
  }

  return paperTheme.colors[paperColorKey] ?? Colors[theme][colorName];
}
