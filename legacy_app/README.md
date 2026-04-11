# AquaPT

AquaPT is a comprehensive mobile application for aquarium enthusiasts to manage their tanks, track livestock, monitor water parameters, schedule maintenance tasks, and get AI-powered care assistance.

## Features

### Tank Management
- Multi-tank support (freshwater, marine, brackish)
- Track tank specifications: volume, dimensions, setup date, investment cost
- Quick logging of water parameters, memos, issues, and dosing

### Water Parameter Tracking
- Log parameters: NH3, NO2, NO3, pH, temperature, GH, KH, salinity, calcium, alkalinity
- Visual charts showing parameter trends over time
- Automated safety alerts when parameters are outside optimal ranges

### Livestock Management
- Track fish, shrimp, snails, corals, plants, and other livestock
- Record species, quantity, purchase price, and photos
- Monitor health status (active, ill, deceased)
- Feeding notes and scheduled feeding tasks
- Transfer livestock between tanks and record offspring

### Task Scheduling
- Create maintenance tasks for equipment
- Automated feeding schedules for livestock
- Task reminders and completion tracking

### Issue Tracking
- Log and monitor tank issues
- Track issue status (open, monitoring, resolved)
- Resolution notes and history

### Assets & Consumables
- Track equipment (filters, heaters, lights, CO2 systems)
- Inventory management for consumables
- Low stock alerts and reorder reminders

### AI Assistant
- Voice-enabled AI assistant for aquarium care advice
- Memory of past conversations
- Can perform actions like logging parameters, creating tasks, and adding livestock

## Tech Stack

- **Framework**: Expo (React Native)
- **UI Library**: React Native Paper (Material Design 3)
- **Database**: SQLite with op-sqlite
- **AI**: OpenRouter API for LLM-powered assistant
- **Voice**: expo-speech-recognition
- **Charts**: react-native-gifted-charts

## Getting Started

### Prerequisites

- Node.js 18+
- Bun (for package management)
- Expo CLI
- For development: Android Studio or Xcode

### Installation

```bash
# Install dependencies
bun install

# Start development server
bun expo start

# Run on Android
bun expo run:android

# Run on iOS
bun expo run:ios
```

### Building APK

```bash
# Generate Android project
bun expo prebuild --platform android

# Build release APK
cd android && ./gradlew assembleRelease
```

## Project Structure

```
aquapt/
├── app/                    # Expo Router pages
│   └── (tabs)/            # Tab-based navigation
│       ├── index.tsx      # Dashboard/home
│       ├── assistant.tsx  # AI assistant
│       ├── tasks.tsx      # Task management
│       ├── timeline.tsx   # Event timeline
│       └── settings.tsx   # App settings
├── components/           # Reusable UI components
├── services/             # Business logic
│   ├── assistant-ai.ts   # OpenRouter API
│   ├── assistant-orchestrator.ts
│   ├── assistant-executor.ts
│   ├── assistant-memory.ts
│   ├── persistence.ts    # SQLite operations
│   ├── scheduling.ts     # Task scheduling
│   ├── water-alerts.ts   # Parameter alerts
│   └── voice.ts          # Voice recognition
├── types/                 # TypeScript types
└── assets/               # Images and icons
```

## Configuration

### AI Assistant Setup

1. Get an API key from [OpenRouter](https://openrouter.ai/)
2. Enter the API key in the Settings tab
3. Select your preferred AI model

### Notifications

Configure reminder notifications in Settings to get notified about scheduled tasks.

## License

Private - All rights reserved
