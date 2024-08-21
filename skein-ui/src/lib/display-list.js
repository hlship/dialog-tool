import { derived } from "svelte/store";

export function displayList(knots, selected) {
    return derived([knots, selected], ([$knots, $selected]) => {
        let nodeIds = [];
        let id = 0;

        while(true) {
            let knot = $knots.get(id);

            if (knot == undefined) { break; }

            nodeIds.push(id);

            id = $selected.get(id);
        }

        return nodeIds;
    });
};