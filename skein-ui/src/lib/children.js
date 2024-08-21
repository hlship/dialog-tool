import { derived } from "svelte/store";

// Pulled this into its own file; inside a component the canonical naming ($node) causes a problem; confuses
// things. Perhaps this will be better in Svelte 5?

export function deriveChildren(knotCommands, knot) {
    return derived([knotCommands], ([$knotCommands]) => {
        let children = [];

        for (const childId of knot.children || []) {
            const command = $knotCommands.get(childId);
            children.push({ id: childId, label: command });
        }

        return children;
    });
};
