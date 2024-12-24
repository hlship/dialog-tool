import { type KnotData, type Category } from "./types";
import { mergeCategory } from "./common.svelte";

export function deriveId2TreeCategory(knots: Map<number, KnotData>) {
    let result = new Map<number, Category>();

    // ids of knots whose own category is not OK; used
    // to propogate those categories up towards root.
    let failedIds = Array<number>();

    for (const [id, knot] of knots) {
        const c = knot.category;

        if (c != "ok") {
            result.set(id, c);
            failedIds.push(id);
        }
    }

    // Next, we accumulate category from non-OK knots up to root
    // Lots of redundant work here, but efficiency is not necessary.

    for (const failedId of failedIds) {
        let c = result.get(failedId) || "ok";

        if (c != "ok") {
            let id = failedId;
            while (true) {
                const parentId = knots.get(id)?.parent_id;

                if (parentId == undefined) { break; }

                c = mergeCategory(c, result.get(parentId) ||"ok");

                result.set(parentId, c);

                id = parentId;
            }
        }
    }

    return result;
}


// derives the list of of knots ids to display, starting with 0, and working forward from each node's selected child id.
export function deriveDisplayIds(knots: Map<number, KnotData>) {
    
    let nodeIds = Array<number>();
    let id = 0;

    while (id != -1) {
        let knot = knots.get(id);

        if (knot == undefined) { break; }

        nodeIds.push(id);

        id = knot.selected || -1;
    }

    return nodeIds;
};

// Using knots map, derived total number of knots
export function deriveKnotTotals(knots: Map<number, KnotData>): Record<Category, number> {

    let result = { ok: 0, new: 0, error: 0 }

    for (const [_, knot] of knots) {
        result[knot.category] += 1
    }

    return result;
}

type KnotLabel = {
    label: string,
    id: number
}

// Using knots map, derives a list of KnotLabels
// in sorted order.
export function deriveLabels(knots: Map<number, KnotData>): KnotLabel[] {
    let result = new Array<KnotLabel>();

    for (const [id, knot] of knots) {
        if (knot.label) {
            result.push({ label: knot.label, id });
        }
    }

    result.sort((a, b) => {
        if (a.label == b.label) { return 0; }

        if (a.label == "START") { return -1 };

        if (a.label < b.label) { return -1; }

        return 1;
    })

    return result;
}

