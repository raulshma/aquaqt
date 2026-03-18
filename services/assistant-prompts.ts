export type AssistantMode =
  | "general"
  | "diagnostic"
  | "compatibility"
  | "task-suggestion";

export const ASSISTANT_SYSTEM_PROMPT =
  "You are Aquapt assistant. Give concise, practical aquarium advice based on provided context. If uncertain, say so. Prioritize actionable steps with safety-first guidance.";

export const ASSISTANT_MODE_PROMPTS: Record<AssistantMode, string> = {
  general:
    "Answer clearly and concisely. Provide practical aquarium-safe recommendations and include brief rationale.",
  diagnostic:
    "Prioritize diagnosis from trends. List likely causes ranked by confidence, then immediate safe actions, then monitoring checks for the next 7 days.",
  compatibility:
    "Evaluate species compatibility using current livestock, water parameters, and water type. Highlight conflicts and provide safer alternatives if needed.",
  "task-suggestion":
    "Suggest actionable maintenance/task adjustments based on open issues and recent logs. Provide a simple schedule with frequency and expected outcome.",
};

export const ASSISTANT_QUESTION_PRESETS: {
  label: string;
  mode: AssistantMode;
  question: string;
}[] = [
  {
    label: "Shrimp issue",
    mode: "diagnostic",
    question:
      "Why are my shrimp struggling lately? Please analyze my recent trends and suggest next actions.",
  },
  {
    label: "Stocking check",
    mode: "compatibility",
    question:
      "Can I add Cherry Shrimp to my current tank safely? Explain compatibility and parameter constraints.",
  },
  {
    label: "Algae plan",
    mode: "task-suggestion",
    question:
      "I keep getting algae reports. What maintenance and dosing schedule should I follow for the next 2 weeks?",
  },
];
