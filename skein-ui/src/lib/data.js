import { derived } from "svelte/store";

export function withChildren(knotsStore) {
    return derived(knotsStore, $knots => {
        let result = new Map();

         for (const [id, knot] of $knots) {
            let knotCopy = {...knot};

/*             let children = [];

            for (const childId of  (knot.node.children || []) {
                const childNode = $knots.get(childId).node;

                const child = {id: childId, label: childNode.command};

                children.push(child);
            }
*/
            knotCopy.children = [];

            result.set(id, knotCopy);
        } 

        return result;
    });
}