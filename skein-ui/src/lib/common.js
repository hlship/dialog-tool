const url = "//localhost:10140/api";

export async function load() {
    let response = await fetch("//localhost:10140/api");
    return await response.json();
}

function applyResult(nodes, result) {
    result.updates.forEach((n) => nodes.update((m) => m.set(n.id, n)));
    // TODO: Deletions
}

export async function processUpdate(nodes, payload) {
    const response = await fetch("//localhost:10140/api", {
        method: "POST",
        cache: "no-cache",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });

    const result = await response.json();

    applyResult(nodes, result);

    return result;
}