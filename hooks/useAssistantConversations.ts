import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { AssistantConversation } from "@/types/assistant";
import { nowId } from "@/utils/assistant-constants";
import {
  initPersistence,
  loadPersistedAssistantState,
  savePersistedAssistantState,
} from "@/services/persistence";

export function createConversation(): AssistantConversation {
  const ts = new Date().toISOString();
  return {
    id: nowId("conv"),
    title: "New Chat",
    pinned: false,
    messages: [
      {
        id: nowId("msg"),
        role: "assistant",
        content:
          "Hi! I can help you operate Aquapt. Type or dictate what you'd like to do and I'll detect actions for your approval.",
        createdAt: ts,
      },
    ],
    detectedActions: [],
    warnings: [],
    createdAt: ts,
    updatedAt: ts,
  };
}

interface UseAssistantConversationsReturn {
  conversations: AssistantConversation[];
  activeConversationId: string;
  activeConversation: AssistantConversation | undefined;
  isHydrated: boolean;
  createNewConversation: () => void;
  switchConversation: (id: string) => void;
  deleteConversation: (id: string) => void;
  toggleConversationPin: (id: string) => void;
  renameConversation: (id: string, title: string) => void;
  updateConversation: (
    convId: string,
    updater: (c: AssistantConversation) => AssistantConversation,
  ) => void;
}

export function useAssistantConversations(): UseAssistantConversationsReturn {
  const [conversations, setConversations] = useState<AssistantConversation[]>(
    () => [createConversation()],
  );
  const [activeConversationId, setActiveConversationId] = useState(() =>
    conversations[0]?.id ?? "",
  );
  const [isHydrated, setIsHydrated] = useState(false);
  const persistTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const activeConversation = useMemo(
    () =>
      conversations.find((c) => c.id === activeConversationId) ??
      conversations[0],
    [conversations, activeConversationId],
  );

  useEffect(() => {
    let isMounted = true;

    const hydrate = async () => {
      try {
        await initPersistence();
        const state = await loadPersistedAssistantState();

        if (!isMounted || !state) return;

        if (state.conversations.length === 0) return;

        setConversations(state.conversations);

        const hasActive = state.conversations.some(
          (c) => c.id === state.activeConversationId,
        );
        setActiveConversationId(
          hasActive
            ? state.activeConversationId
            : state.conversations[0].id,
        );
      } catch (error) {
        console.warn("Conversation hydration failed", error);
      } finally {
        if (isMounted) setIsHydrated(true);
      }
    };

    void hydrate();
    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    if (!isHydrated) return;

    if (persistTimeoutRef.current) {
      clearTimeout(persistTimeoutRef.current);
    }

    const activeId =
      conversations.find((c) => c.id === activeConversationId)?.id ??
      conversations[0]?.id;

    persistTimeoutRef.current = setTimeout(() => {
      if (!activeId) return;
      void savePersistedAssistantState({
        conversations,
        activeConversationId: activeId,
        updatedAt: new Date().toISOString(),
      });
    }, 250);

    return () => {
      if (persistTimeoutRef.current) {
        clearTimeout(persistTimeoutRef.current);
        persistTimeoutRef.current = null;
      }
    };
  }, [activeConversationId, conversations, isHydrated]);

  const createNewConversation = useCallback(() => {
    const conv = createConversation();
    setConversations((prev) => [conv, ...prev]);
    setActiveConversationId(conv.id);
  }, []);

  const switchConversation = useCallback((id: string) => {
    setActiveConversationId(id);
  }, []);

  const deleteConversation = useCallback(
    (id: string) => {
      setConversations((prev) => {
        const next = prev.filter((c) => c.id !== id);
        if (next.length === 0) {
          const fresh = createConversation();
          next.push(fresh);
        }
        if (id === activeConversationId) {
          setActiveConversationId(next[0].id);
        }
        return next;
      });
    },
    [activeConversationId],
  );

  const toggleConversationPin = useCallback((id: string) => {
    setConversations((prev) =>
      prev.map((c) =>
        c.id === id
          ? { ...c, pinned: !c.pinned, updatedAt: new Date().toISOString() }
          : c,
      ),
    );
  }, []);

  const renameConversation = useCallback((id: string, title: string) => {
    const nextTitle = title.trim();
    if (!nextTitle) return;

    setConversations((prev) =>
      prev.map((c) =>
        c.id === id
          ? { ...c, title: nextTitle, updatedAt: new Date().toISOString() }
          : c,
      ),
    );
  }, []);

  const updateConversation = useCallback(
    (
      convId: string,
      updater: (c: AssistantConversation) => AssistantConversation,
    ) => {
      setConversations((prev) =>
        prev.map((c) => (c.id === convId ? updater(c) : c)),
      );
    },
    [],
  );

  return {
    conversations,
    activeConversationId,
    activeConversation,
    isHydrated,
    createNewConversation,
    switchConversation,
    deleteConversation,
    toggleConversationPin,
    renameConversation,
    updateConversation,
  };
}
