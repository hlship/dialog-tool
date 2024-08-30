import { derived } from "svelte/store";
import { traif, addTraif } from "./common.js";


export function deriveKnotTraif(knots) {
    return derived(knots, $knots => {

        let leafIds = [];
        let result = new Map();

        for (const [id, knot] of $knots) {
            const t = traif(knot);
            result.set(id, t);

            if (knot.children.length == 0) {
                leafIds.push(id);
            }
        }

        // Next, we accumulate traif from leaves up to root

        for (const leafId of leafIds) {
            let t = result.get(leafId);

            if (t != "ok") {
                let id = leafId;
                while (true) {
                    let parentId = $knots.get(id).parent_id;

                    if (parentId == undefined) { break; }

                    t = addTraif(t, result.get(parentId));

                    result.set(parentId, t);

                    id = parentId;

                }
            }
        }

        return result;
    });
}

export function deriveChildren(knotCommands, traif, knot) {
    return derived([knotCommands, traif], ([$knotCommands, $traif]) => {
        let children = [];

        for (const childId of knot.children || []) {
            const command = $knotCommands.get(childId);
            // console.debug(childId, $traif.get(childId), command);
            children.push({ id: childId, 
                traif: $traif.get(childId),
                label: command });
        }

        return children;
    });
};

// derives the list of of knots ids to display, starting with 0, and working forward from each node's selected child id.
export function deriveDisplayIds(knots, selected) {
    return derived([knots, selected], ([$knots, $selected]) => {
        let nodeIds = [];
        let id = 0;

        while (true) {
            let knot = $knots.get(id);

            if (knot == undefined) { break; }

            nodeIds.push(id);

            id = $selected.get(id);
        }

        return nodeIds;
    });
};

// Using knots map, derived total number of knots
export function deriveKnotTotals(knots) {
    return derived(knots, $knots => {


        let unblessed = 0; // Unblessed content for new node (no conflicting response)
        let error = 0; // Conflict between response and unblessed

        for (const [_, knot] of $knots) {
             
            if (knot.unblessed) {
                if (knot.response) {
                    error += 1;
                } else {
                    unblessed += 1;
                }
            }
        }

        return {
            ok: $knots.size - unblessed - error,
            unblessed, error
        };
    });
}

// Using knots map, derives a list of label tuples [String, long]
// in sorted order.
export function deriveLabels(knots) {
    return derived(knots, $knots => {
        let result = [];

        for (const [id, knot] of $knots) {
            if (knot.label) {
                result.push({label: knot.label, id});
            }
        }

        result.sort((a, b) => {
            if (a.label == b.label) { return 0; }

            if (a.label == "START") { return -1};

            if (a.label < b.label) { return -1; }

            return 1;
        })


        return result;
    });
}

