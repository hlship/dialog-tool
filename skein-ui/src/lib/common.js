const url = "//localhost:10140/api";

export async function load() {
    const response = await fetch("//localhost:10140/api");

    return await response.json();
}

export async function postApi(payload) {
    const response = await fetch("//localhost:10140/api", {
        method: "POST",
        cache: "no-cache",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });

    // The response body is converted to JSON and returned as the result.
    return await response.json();

}

export function updateStoreMap(store, f) {
    store.update((m) => {
        f(m);
        return m;
    });
}

export function traif(knot) {
    if (knot.unblessed == undefined) { return "ok"; }

    if (knot.response == undefined) { return "new"; }

    return "error";
}

export function addTraif(left, right) {
    if (left == "error" || right == "error") { return "error"; }

    if (left == "new" || right == "new") { return "new"; }

    return "ok";
}

export function selectChild(selectedStore, parentId, childId) {
    updateStoreMap(selectedStore, (_selected) => {
        _selected.set(parentId, childId);
    });
}
