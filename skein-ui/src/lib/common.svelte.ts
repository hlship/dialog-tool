import { type KnotData, type Category } from "./types";

const url = "//localhost:10140/api";

export type Payload = {
    action: string,
    // Remainder are used depending on the specific action
    id?: number,
    label?: string,
    command?: string,
};

// TODO: start-batch and end-batch are different: return empty body
export type ActionResult = {
    updates: KnotData[],
    removed_ids: number[],
    // knot id to focus on (e.g., scroll to)
    focus: number,
    enable_undo: boolean,
    enable_redo: boolean,
    dirty: boolean,
    // Only for new-command:
    new_id?: number,
    // Only appears for GET /api request:
    title?: string
    // For when an action fails; currently only
    // applies to edit-command action
    error?: string
}

export async function load(): Promise<ActionResult> {
    const response = await fetch("//localhost:10140/api");

    return await response.json();
}

export async function postApi(payload: Payload): Promise<ActionResult> {
    const response = await fetch(url, {
        method: "POST",
        cache: "no-cache",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });

    // The response body is converted to JSON and returned as the result.
    return await response.json();
}

export function mergeCategory(left: Category, right: Category): Category {
    if (left == "error" || right == "error") { return "error"; }

    if (left == "new" || right == "new") { return "new"; }

    return "ok";
}


