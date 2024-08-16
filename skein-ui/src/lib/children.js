import { derived } from "svelte/store";

// Pulled this into its own file; inside a component the canonical naming ($node) causes a problem; confuses
// things. Perhaps this will be better in Svelte 5?

export function deriveChildren(childNames, node) {
    return derived([childNames, node], ([$childNames, $node]) => {
        let children = [];

        for (const childId of $node.children || []) {
            const command = $childNames.get(childId);
            children.push({ id: childId, label: command });
        }

        return children;
    });
};
