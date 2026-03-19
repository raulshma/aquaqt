import type { PropsWithChildren, ReactNode } from "react";
import {
  ScrollView,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from "react-native";
import { Card, Text, useTheme, type MD3Theme } from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

type DashboardTone =
  | "primary"
  | "secondary"
  | "tertiary"
  | "surface"
  | "surfaceVariant"
  | "error";

type DashboardScrollViewProps = PropsWithChildren<{
  contentContainerStyle?: StyleProp<ViewStyle>;
}>;

type DashboardHeroProps = {
  title: string;
  subtitle: string;
  tone?: DashboardTone;
  chips?: ReactNode;
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
};

type DashboardSectionProps = PropsWithChildren<{
  title?: string;
  description?: string;
  action?: ReactNode;
  style?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
}>;

const resolveTone = (theme: MD3Theme, tone: DashboardTone) => {
  switch (tone) {
    case "secondary":
      return {
        backgroundColor: theme.colors.secondaryContainer,
        textColor: theme.colors.onSecondaryContainer,
      };
    case "tertiary":
      return {
        backgroundColor: theme.colors.tertiaryContainer,
        textColor: theme.colors.onTertiaryContainer,
      };
    case "surface":
      return {
        backgroundColor: theme.colors.surface,
        textColor: theme.colors.onSurface,
      };
    case "surfaceVariant":
      return {
        backgroundColor: theme.colors.surfaceVariant,
        textColor: theme.colors.onSurfaceVariant,
      };
    case "error":
      return {
        backgroundColor: theme.colors.errorContainer,
        textColor: theme.colors.onErrorContainer,
      };
    default:
      return {
        backgroundColor: theme.colors.primaryContainer,
        textColor: theme.colors.onPrimaryContainer,
      };
  }
};

export function DashboardScrollView({
  children,
  contentContainerStyle,
}: DashboardScrollViewProps) {
  const insets = useSafeAreaInsets();

  return (
    <ScrollView
      contentContainerStyle={[
        styles.page,
        { paddingTop: 16 + insets.top },
        contentContainerStyle,
      ]}
      showsVerticalScrollIndicator={false}
    >
      {children}
    </ScrollView>
  );
}

export function DashboardHero({
  title,
  subtitle,
  tone = "primary",
  chips,
  children,
  style,
}: DashboardHeroProps) {
  const theme = useTheme();
  const { backgroundColor, textColor } = resolveTone(theme, tone);

  return (
    <Card
      mode="elevated"
      style={[styles.heroCard, { backgroundColor }, style]}
    >
      <Card.Content style={styles.heroContent}>
        <View style={styles.heroHeader}>
          <Text variant="headlineMedium" style={{ color: textColor }}>
            {title}
          </Text>
          <Text
            variant="bodyMedium"
            style={[styles.heroSubtitle, { color: textColor }]}
          >
            {subtitle}
          </Text>
        </View>
        {chips ? <View style={styles.chipRow}>{chips}</View> : null}
        {children}
      </Card.Content>
    </Card>
  );
}

export function DashboardSection({
  title,
  description,
  action,
  style,
  contentStyle,
  children,
}: DashboardSectionProps) {
  const theme = useTheme();

  return (
    <Card
      mode="elevated"
      style={[
        styles.sectionCard,
        { backgroundColor: theme.colors.surface },
        style,
      ]}
    >
      <Card.Content style={[styles.sectionContent, contentStyle]}>
        {title || description || action ? (
          <View style={styles.sectionHeader}>
            <View style={styles.sectionHeaderText}>
              {title ? <Text variant="titleMedium">{title}</Text> : null}
              {description ? (
                <Text variant="bodySmall" style={styles.sectionDescription}>
                  {description}
                </Text>
              ) : null}
            </View>
            {action}
          </View>
        ) : null}
        {children}
      </Card.Content>
    </Card>
  );
}

const styles = StyleSheet.create({
  page: {
    padding: 16,
    paddingBottom: 132,
    gap: 12,
  },
  heroCard: {
    borderRadius: 24,
    marginVertical: 0,
  },
  heroContent: {
    gap: 12,
  },
  heroHeader: {
    gap: 4,
  },
  heroSubtitle: {
    opacity: 0.82,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  sectionCard: {
    borderRadius: 28,
    marginVertical: 0,
  },
  sectionContent: {
    gap: 14,
  },
  sectionHeader: {
    gap: 10,
  },
  sectionHeaderText: {
    gap: 4,
  },
  sectionDescription: {
    opacity: 0.74,
    lineHeight: 18,
  },
});
