import { derived } from "svelte/store";

export function deriveChildren(knotCommands, knot) {
    return derived(knotCommands, $knotCommands => {
        let children = [];

        for (const childId of knot.children || []) {
            const command = $knotCommands.get(childId);
            children.push({ id: childId, label: command });
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
