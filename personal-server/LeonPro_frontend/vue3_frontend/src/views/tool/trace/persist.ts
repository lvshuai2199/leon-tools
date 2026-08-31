import type { PluginKind } from "./parse";

export const TRACE_STORAGE_KEY = "leon-trace-workspace-v1";

export interface TraceWorkspace {
  plugin: PluginKind;
  xmlText: string;
  scriptText: string;
  treeText: string;
  xmlName: string;
  scriptName: string;
  treeName: string;
  showLine: boolean;
  showPoints: boolean;
  showMarkers: boolean;
  showTreePoses: boolean;
  showArrows: boolean;
}

export function emptyWorkspace(): TraceWorkspace {
  return {
    plugin: "auto",
    xmlText: "",
    scriptText: "",
    treeText: "",
    xmlName: "",
    scriptName: "",
    treeName: "",
    showLine: true,
    showPoints: false,
    showMarkers: true,
    showTreePoses: false,
    showArrows: false,
  };
}

export function loadWorkspace(): TraceWorkspace | null {
  try {
    const raw = localStorage.getItem(TRACE_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<TraceWorkspace>;
    return { ...emptyWorkspace(), ...parsed };
  } catch {
    return null;
  }
}

export function saveWorkspace(ws: TraceWorkspace) {
  localStorage.setItem(TRACE_STORAGE_KEY, JSON.stringify(ws));
}

export function clearWorkspace() {
  localStorage.removeItem(TRACE_STORAGE_KEY);
}

export function hasWorkspaceContent(ws: TraceWorkspace) {
  return Boolean(ws.xmlText || ws.scriptText || ws.treeText);
}
