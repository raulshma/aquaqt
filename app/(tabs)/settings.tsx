import { useState } from "react";
import { ScrollView, StyleSheet } from "react-native";
import { Button, Card, Text, TextInput } from "react-native-paper";

import { useAquapt } from "@/context/aquapt-context";

export default function SettingsScreen() {
  const { settings, saveApiKey } = useAquapt();
  const [apiKey, setApiKey] = useState(settings.openRouterApiKey);
  const [savedAt, setSavedAt] = useState<string | null>(null);

  const handleSave = () => {
    saveApiKey(apiKey);
    setSavedAt(new Date().toLocaleString());
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text variant="headlineMedium">Settings</Text>
      <Text variant="bodyMedium" style={styles.subtitle}>
        Configure your BYOK AI assistant and app preferences.
      </Text>

      <Card mode="contained">
        <Card.Title
          title="OpenRouter API Key (BYOK)"
          subtitle="Stored in app state for this MVP"
        />
        <Card.Content>
          <TextInput
            mode="outlined"
            label="sk-or-v1-..."
            secureTextEntry
            autoCapitalize="none"
            autoCorrect={false}
            value={apiKey}
            onChangeText={setApiKey}
          />
          <Button
            mode="contained"
            onPress={handleSave}
            style={styles.saveButton}
          >
            Save key
          </Button>
          {savedAt ? (
            <Text variant="bodySmall" style={styles.savedAt}>
              Saved: {savedAt}
            </Text>
          ) : null}
        </Card.Content>
      </Card>

      <Card mode="outlined" style={styles.noteCard}>
        <Card.Title title="Roadmap status" />
        <Card.Content>
          <Text variant="bodyMedium">
            Local persistent storage (SQLite), chart analytics, and contextual
            AI chat are the next build steps.
          </Text>
        </Card.Content>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    gap: 12,
  },
  subtitle: {
    opacity: 0.75,
    marginBottom: 6,
  },
  saveButton: {
    marginTop: 12,
    alignSelf: "flex-start",
  },
  savedAt: {
    marginTop: 8,
    opacity: 0.75,
  },
  noteCard: {
    marginTop: 2,
  },
});
