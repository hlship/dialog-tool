import { type KnotData, Category } from "./types";

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
    enable_undo: boolean,
    enable_redo: boolean,
    // Only for new-command:
    new_id?: number,
    // Only appears for GET /api request:
    title?: string,
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

export function category(knot: KnotData): Category {
    if (knot.unblessed == undefined) { return Category.OK; }

    if (knot.response == undefined) { return Category.NEW; }

    return Category.ERROR
}

export function mergeCategory(left: Category, right: Category): Category {
    // TODO: all this is really the max of the two inputs

    if (left == Category.ERROR || right == Category.ERROR) { return Category.ERROR; }

    if (left == Category.NEW || right == Category.NEW) { return Category.NEW; }

    return Category.OK;
}


